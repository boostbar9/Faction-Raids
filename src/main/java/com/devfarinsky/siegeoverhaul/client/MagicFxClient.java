package com.devfarinsky.siegeoverhaul.client;

import com.devfarinsky.siegeoverhaul.*;
import com.devfarinsky.siegeoverhaul.items.MagicFx;
import com.devfarinsky.siegeoverhaul.items.MagicTheme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.DustColorTransitionOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Vector3f;

import java.util.*;

/**
 * Client expansion of patron magic: shockwave rings, helices, bursts, beams and rune
 * circles from server packets, plus purely local projectile trails, held-weapon auras
 * and charge spirals. Everything shares one per-tick particle budget and follows the
 * player's Hero visual settings (particle level, distance, reduced flashes).
 */
@Mod.EventBusSubscriber(modid = SiegeOverhaul.MOD_ID, value = Dist.CLIENT)
public final class MagicFxClient {
    private static final int MAX_EFFECTS = 64, MAX_TRAILS = 96, BUDGET = 192, AURA_HOLDERS = 24;
    private static final List<Effect> EFFECTS = new ArrayList<>();
    private static final Map<Entity, MagicTheme> TRAILS = new WeakHashMap<>();
    private static final Map<MagicTheme, DustColorTransitionOptions[]> DUST = new EnumMap<>(MagicTheme.class);
    private static ClientLevel world;
    private static int budget;

    private record Effect(int shape, MagicTheme theme, Vec3 at, Vec3 arg, int ticks, int entity, int[] age) {}

    private MagicFxClient() {}

    private static void world() {
        var current = Minecraft.getInstance().level;
        if (current != world) { EFFECTS.clear(); TRAILS.clear(); world = current; }
    }

    public static void accept(RaidNetwork.MagicFxPacket packet) {
        world();
        var theme = MagicTheme.byId(packet.theme());
        if (world == null || theme == null || level() == 0) return;
        Vec3 at = new Vec3(packet.x(), packet.y(), packet.z());
        if (!inRange(at)) return;
        if (EFFECTS.size() >= MAX_EFFECTS) EFFECTS.remove(0);
        EFFECTS.add(new Effect(packet.shape(), theme, at, new Vec3(packet.a(), packet.b(), packet.c()),
                packet.ticks(), packet.entity(), new int[1]));
    }

    /** Patron arrows and spell bolts get a local trail; no packet is needed. */
    @SubscribeEvent
    public static void join(EntityJoinLevelEvent event) {
        if (!(event.getLevel() instanceof ClientLevel)) return;
        Entity entity = event.getEntity();
        MagicTheme theme = null;
        if (entity instanceof com.devfarinsky.siegeoverhaul.spells.OlympianBolt bolt) theme = MagicTheme.ofRole(bolt.kind());
        else if (entity instanceof AbstractArrow arrow && arrow.getOwner() instanceof LivingEntity owner) theme = held(owner);
        if (theme != null) {
            if (TRAILS.size() >= MAX_TRAILS) TRAILS.keySet().removeIf(e -> e.isRemoved());
            if (TRAILS.size() < MAX_TRAILS) TRAILS.put(entity, theme);
        }
    }

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        world();
        var mc = Minecraft.getInstance();
        if (world == null || mc.isPaused() || mc.player == null) return;
        int level = level();
        if (level == 0) { EFFECTS.clear(); return; }
        budget = level == 1 ? BUDGET / 3 : BUDGET;
        long time = world.getGameTime();
        for (Iterator<Effect> it = EFFECTS.iterator(); it.hasNext(); ) {
            var effect = it.next();
            int age = effect.age()[0]++;
            if (age >= effect.ticks()) { it.remove(); continue; }
            play(effect, age);
        }
        trails(time);
        if (time % 4 == 0) auras(mc, time);
        charges(mc, time);
    }

    private static void play(Effect e, int age) {
        switch (e.shape()) {
            case MagicFx.BURST -> burst(e.at(), e.theme(), (float) e.arg().x);
            case MagicFx.RING -> ring(e.at(), e.theme(), (float) e.arg().x, age, e.ticks());
            case MagicFx.HELIX -> helix(follow(e), e.theme(), (float) e.arg().x, (float) Math.max(.4, e.arg().y), age, e.ticks());
            case MagicFx.BEAM -> beam(e.at(), e.arg(), e.theme());
            case MagicFx.CIRCLE -> circle(follow(e), e.theme(), (float) e.arg().x, age);
            default -> { }
        }
    }

    private static Vec3 follow(Effect e) {
        Entity entity = e.entity() >= 0 ? world.getEntity(e.entity()) : null;
        return entity != null && entity.isAlive() ? entity.position() : e.at();
    }

    // ---- shapes -------------------------------------------------------------

    private static void burst(Vec3 at, MagicTheme theme, float power) {
        int points = Mth.clamp(Math.round(22 * power), 6, 48);
        var random = world.random;
        for (int i = 0; i < points; i++) {
            // Even spherical spread with outward velocity, so the burst reads as a bloom.
            double u = random.nextDouble() * 2 - 1, phi = random.nextDouble() * Math.PI * 2, r = Math.sqrt(1 - u * u);
            double dx = r * Math.cos(phi), dy = u, dz = r * Math.sin(phi), speed = .12 + .1 * power;
            emit(dust(theme, i), at.x, at.y, at.z, dx * speed, dy * speed, dz * speed);
        }
        for (int i = 0; i < points / 3; i++)
            emit(theme.sparkle, at.x, at.y, at.z, (random.nextDouble() - .5) * .3, random.nextDouble() * .25, (random.nextDouble() - .5) * .3);
        if (!HeroVisualConfig.REDUCED_FLASHES.get() && power >= 1) emit(ParticleTypes.FLASH, at.x, at.y, at.z, 0, 0, 0);
    }

    private static void ring(Vec3 at, MagicTheme theme, float radius, int age, int ticks) {
        float progress = (age + 1F) / ticks, current = .3F + (radius - .3F) * easeOut(progress);
        int points = Mth.clamp(Math.round(current * 10), 10, 36);
        double turn = age * .15;
        for (int i = 0; i < points; i++) {
            double angle = turn + i * Math.PI * 2 / points;
            double x = at.x + Math.cos(angle) * current, z = at.z + Math.sin(angle) * current;
            emit(dust(theme, i + age), x, at.y + .12, z, Math.cos(angle) * .03, .01, Math.sin(angle) * .03);
        }
        if (age % 2 == 0) for (int i = 0; i < 3; i++) {
            double angle = world.random.nextDouble() * Math.PI * 2;
            emit(theme.core, at.x + Math.cos(angle) * current, at.y + .2, at.z + Math.sin(angle) * current, 0, .04, 0);
        }
    }

    private static void helix(Vec3 base, MagicTheme theme, float height, float width, int age, int ticks) {
        float progress = (age + 1F) / ticks;
        double radius = .45 + width * .5;
        for (int strand = 0; strand < 2; strand++) for (int step = 0; step < 3; step++) {
            double t = progress - step * .02;
            if (t < 0) continue;
            double angle = t * Math.PI * 6 + strand * Math.PI;
            emit(dust(theme, strand), base.x + Math.cos(angle) * radius, base.y + t * height, base.z + Math.sin(angle) * radius, 0, .01, 0);
        }
        if (age % 3 == 0) emit(theme.sparkle, base.x, base.y + progress * height, base.z, 0, .05, 0);
    }

    private static void beam(Vec3 from, Vec3 to, MagicTheme theme) {
        Vec3 delta = to.subtract(from);
        int points = Mth.clamp((int) (delta.length() / .35), 2, 72);
        for (int i = 0; i <= points; i++) {
            Vec3 p = from.add(delta.scale((double) i / points));
            emit(dust(theme, i), p.x, p.y, p.z, 0, 0, 0);
            if (i % 5 == 0) emit(theme.sparkle, p.x, p.y, p.z, 0, .02, 0);
        }
    }

    private static void circle(Vec3 feet, MagicTheme theme, float radius, int age) {
        double turn = age * .12;
        for (int i = 0; i < 12; i++) {
            double angle = turn + i * Math.PI * 2 / 12;
            emit(dust(theme, i), feet.x + Math.cos(angle) * radius, feet.y + .06, feet.z + Math.sin(angle) * radius, 0, 0, 0);
        }
        // Inner counter-rotating rune points.
        for (int i = 0; i < 4; i++) {
            double angle = -turn * 1.6 + i * Math.PI / 2;
            emit(theme.core, feet.x + Math.cos(angle) * radius * .55, feet.y + .08, feet.z + Math.sin(angle) * radius * .55, 0, .015, 0);
        }
    }

    private static void trails(long time) {
        for (Iterator<Map.Entry<Entity, MagicTheme>> it = TRAILS.entrySet().iterator(); it.hasNext(); ) {
            var entry = it.next(); Entity projectile = entry.getKey();
            if (projectile.isRemoved()) { it.remove(); continue; }
            Vec3 motion = projectile.getDeltaMovement();
            if (motion.lengthSqr() < .01 || !inRange(projectile.position())) continue; // stuck in a block or far away
            var theme = entry.getValue(); Vec3 pos = projectile.position();
            emit(dust(theme, (int) time), pos.x, pos.y, pos.z, 0, 0, 0);
            // Two strands spiralling around the flight axis.
            Vec3 axis = motion.normalize(), side = axis.cross(new Vec3(0, 1, 0));
            if (side.lengthSqr() < 1e-4) side = new Vec3(1, 0, 0);
            side = side.normalize(); Vec3 up = side.cross(axis);
            double angle = time * .9;
            for (int strand = 0; strand < 2; strand++) {
                double a = angle + strand * Math.PI;
                Vec3 offset = side.scale(Math.cos(a) * .18).add(up.scale(Math.sin(a) * .18));
                emit(dust(theme, strand), pos.x + offset.x, pos.y + offset.y, pos.z + offset.z, 0, 0, 0);
            }
            if (time % 2 == 0) emit(theme.sparkle, pos.x, pos.y, pos.z, 0, 0, 0);
        }
    }

    private static void auras(Minecraft mc, long time) {
        Vec3 camera = mc.gameRenderer.getMainCamera().getPosition();
        int holders = 0;
        for (var living : world.getEntitiesOfClass(LivingEntity.class, new AABB(camera, camera).inflate(24))) {
            if (holders >= AURA_HOLDERS || budget <= 0) break;
            if (living.isInvisible() || !living.isAlive()) continue;
            var theme = held(living);
            if (theme == null) continue;
            holders++;
            Vec3 hand = hand(living, living.getMainArm());
            emit(dust(theme, (int) time), hand.x, hand.y, hand.z, 0, .01, 0);
            if (time % 12 == 0) emit(theme.sparkle, hand.x, hand.y, hand.z, 0, .02, 0);
        }
    }

    private static void charges(Minecraft mc, long time) {
        Vec3 camera = mc.gameRenderer.getMainCamera().getPosition();
        for (var living : world.getEntitiesOfClass(LivingEntity.class, new AABB(camera, camera).inflate(32))) {
            if (budget <= 0) return;
            if (!living.isUsingItem()) continue;
            ItemStack using = living.getUseItem();
            var theme = using.getItem() instanceof com.devfarinsky.siegeoverhaul.items.OlympianSpellBookItem book
                    ? MagicTheme.ofRole(book.role()) : MagicTheme.of(using);
            if (theme == null) continue;
            float progress = using.getItem() instanceof BowItem ? Math.min(1F, living.getTicksUsingItem() / 20F)
                    : using.getItem() instanceof CrossbowItem ? Math.min(1F, living.getTicksUsingItem() / (float) Math.max(1, CrossbowItem.getChargeDuration(using)))
                    : Math.min(1F, living.getTicksUsingItem() / 20F);
            Vec3 hand = hand(living, living.getUsedItemHand() == net.minecraft.world.InteractionHand.MAIN_HAND
                    ? living.getMainArm() : living.getMainArm().getOpposite());
            // Inward spiral that tightens as the draw or spell charges.
            double radius = .9 * (1 - progress) + .12;
            for (int i = 0; i < 3; i++) {
                double angle = time * .7 + i * Math.PI * 2 / 3;
                emit(dust(theme, i), hand.x + Math.cos(angle) * radius, hand.y + Math.sin(angle * .5) * .15,
                        hand.z + Math.sin(angle) * radius, -Math.cos(angle) * .02, 0, -Math.sin(angle) * .02);
            }
            if (progress >= 1 && time % 3 == 0) emit(theme.sparkle, hand.x, hand.y, hand.z, 0, .03, 0);
        }
    }

    // ---- helpers ------------------------------------------------------------

    private static MagicTheme held(LivingEntity living) {
        var theme = MagicTheme.of(living.getMainHandItem());
        return theme != null ? theme : MagicTheme.of(living.getOffhandItem());
    }

    private static Vec3 hand(LivingEntity living, HumanoidArm arm) {
        double yaw = Math.toRadians(living.yBodyRot);
        double side = arm == HumanoidArm.RIGHT ? -.38 : .38;
        double x = -Math.sin(yaw) * .3 + Math.cos(yaw) * side, z = Math.cos(yaw) * .3 + Math.sin(yaw) * side;
        return living.position().add(x, living.getBbHeight() * .55, z);
    }

    private static DustColorTransitionOptions dust(MagicTheme theme, int variant) {
        var options = DUST.computeIfAbsent(theme, t -> new DustColorTransitionOptions[]{
                new DustColorTransitionOptions(rgb(t.from), rgb(t.to), 1.1F),
                new DustColorTransitionOptions(rgb(t.to), rgb(t.from), .8F)});
        return options[Math.floorMod(variant, options.length)];
    }

    private static Vector3f rgb(int color) {
        return new Vector3f((color >> 16 & 255) / 255F, (color >> 8 & 255) / 255F, (color & 255) / 255F);
    }

    private static void emit(ParticleOptions particle, double x, double y, double z, double dx, double dy, double dz) {
        if (budget <= 0) return;
        budget--;
        world.addParticle(particle, x, y, z, dx, dy, dz);
    }

    private static boolean inRange(Vec3 at) {
        var camera = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        double range = HeroVisualConfig.DISTANCE.get();
        return camera.distanceToSqr(at) <= range * range;
    }

    private static int level() { return HeroVisualConfig.PARTICLES.get(); }

    private static float easeOut(float t) {
        float clamped = Math.max(0F, Math.min(1F, t));
        return 1 - (1 - clamped) * (1 - clamped);
    }
}
