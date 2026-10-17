package com.devfarinsky.siegeoverhaul.items;

import com.devfarinsky.siegeoverhaul.RaidNetwork;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * Server entry point for patron magic visuals. Each call sends one small packet to
 * nearby players; the client expands it into rings, helices and bursts within its
 * own particle budget, instead of the server sending one packet per particle.
 */
public final class MagicFx {
    /** Shape ids carried by {@link RaidNetwork.MagicFxPacket}. */
    public static final int BURST = 0, RING = 1, HELIX = 2, BEAM = 3, CIRCLE = 4;
    public static final double RANGE = 48;
    /** Per-entity spacing so multishot, rapid fire or many hits cannot flood clients. */
    static final long MIN_GAP_TICKS = 4;
    static final String LAST = "SiegeMagicFxTick";
    private MagicFx() {}

    public static void burst(ServerLevel level, Vec3 at, MagicTheme theme, float power) {
        send(level, BURST, theme, at, new Vec3(power, 0, 0), 1, -1);
    }

    /** An expanding shockwave ring on the ground plane. */
    public static void ring(ServerLevel level, Vec3 at, MagicTheme theme, float radius, int ticks) {
        send(level, RING, theme, at, new Vec3(radius, 0, 0), ticks, -1);
    }

    /** A rising double helix that follows {@code around}. */
    public static void helix(ServerLevel level, Entity around, MagicTheme theme, float height, int ticks) {
        if (around == null) return;
        send(level, HELIX, theme, around.position(), new Vec3(height, around.getBbWidth(), 0), ticks, around.getId());
    }

    /** A dotted line of dust between two points, such as a lightning strike or a reveal. */
    public static void beam(ServerLevel level, Vec3 from, Vec3 to, MagicTheme theme) {
        send(level, BEAM, theme, from, to, 1, -1);
    }

    /** A turning rune circle at the caster's feet while a spell charges. */
    public static void castCircle(ServerLevel level, Entity caster, MagicTheme theme, int ticks) {
        if (caster == null) return;
        send(level, CIRCLE, theme, caster.position(), new Vec3(1.1, 0, 0), ticks, caster.getId());
    }

    /** Layered cast or impact sound with a little pitch variation. */
    public static void cue(ServerLevel level, Vec3 at, MagicTheme theme, boolean impact) {
        if (level == null || at == null || theme == null) return;
        float pitch = 0.9F + java.util.concurrent.ThreadLocalRandom.current().nextFloat() * 0.25F;
        level.playSound(null, at.x, at.y, at.z, impact ? theme.impact : theme.cast, SoundSource.PLAYERS,
                impact && theme == MagicTheme.ZEUS ? 0.3F : 0.6F, pitch);
        level.playSound(null, at.x, at.y, at.z, net.minecraft.sounds.SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS,
                0.35F, pitch * 1.3F);
    }

    /** True at most once per {@link #MIN_GAP_TICKS} for this entity; records the use. */
    public static boolean allow(Entity source) {
        if (source == null || source.level() == null || source.getPersistentData() == null) return true;
        long now = source.level().getGameTime(), last = source.getPersistentData().getLong(LAST);
        if (source.getPersistentData().contains(LAST) && last <= now && now - last < MIN_GAP_TICKS) return false;
        source.getPersistentData().putLong(LAST, now);
        return true;
    }

    private static void send(ServerLevel level, int shape, MagicTheme theme, Vec3 at, Vec3 arg, int ticks, int entity) {
        if (level == null || level.getServer() == null || theme == null || at == null
                || !Double.isFinite(at.x) || !Double.isFinite(at.y) || !Double.isFinite(at.z)) return;
        RaidNetwork.sendMagicFx(level, new RaidNetwork.MagicFxPacket(shape, theme.ordinal(),
                (float) at.x, (float) at.y, (float) at.z, (float) arg.x, (float) arg.y, (float) arg.z,
                Math.max(1, Math.min(200, ticks)), entity));
    }
}
