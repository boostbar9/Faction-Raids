package com.devfarinsky.siegeoverhaul.core;

import com.devfarinsky.siegeoverhaul.*;
import com.devfarinsky.siegeoverhaul.compat.EnemyHiringProtection;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.effect.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.*;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraftforge.event.entity.living.*;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Four readable specializations. Native AI, finite equipment, visible effects and bounded cooldowns. */
@Mod.EventBusSubscriber(modid=SiegeOverhaul.MOD_ID)
public final class HeroTraits {
    private HeroTraits() {}
    /** Signature-ability description shown on hero cards. Keep concise (fits card). */
    public static String description(int role) {
        return switch(role) {
            case 10 -> "Ironoath: every third melee hit heals 1 heart";
            case 11 -> "Stonehand: on block, allies within 4 blocks take 25% less damage";
            case 12 -> "Stormbow: every fourth arrow chains lightning to two nearby foes";
            case 13 -> "Frostbinder: a bolt slows up to three enemies for 3s";
            case 14 -> "Bloodthorn: at full health, first hit grants absorption";
            case 15 -> "Dawnwarden: shields an ally below 30% health for 5s";
            case 16 -> "Emberstep: melee hits ignite the target for 4s";
            case 17 -> "Hollowveil: every third arrow marks target for +30% damage";
            case 18 -> "Warbell: on block, a shockwave staggers nearby enemies";
            case 19 -> "Verdant: arrows root the target for 2s";
            case 20 -> "Grimwatch: bolts leave a lingering piercing trail";
            case 21 -> "Wildsong: kills grant +20% attack speed for 6s";
            case 22 -> "Voidweaver mage: casts a void nova every 15s";
            case 23 -> "Starweaver mage: every hit summons a homing star";
            case 24 -> "Ashenheart mage: fireball attacks explode on impact";
            case 25 -> "Ironclad: Resistance II aura to nearby allies";
            case 26 -> "Skyrender: arrows split into three tracers on hit";
            case 27 -> "Solmyra the Radiant: sunlight burns all foes within 8 blocks every 20s";
            case 28 -> "Nightcaller: summons two shadow wolves for 30s (45s)";
            case 29 -> "Chronos: slows all enemies within 10 blocks for 4s (30s)";
            default -> "";
        };
    }
    /** Convenience: is this hero a mage (staff-wielding, spell-focused)? */
    private static boolean mage(int role) { return role == 22 || role == 23 || role == 24 || role >= 27; }
    /**
     * Per-hero armor trim. Material/pattern colors match the rarity and class
     * so every hero reads visually distinct even at a glance.
     */
    public static ItemStack armor(int role,Item item) {
        ItemStack stack=new ItemStack(item);CompoundTag trim=new CompoundTag();
        trim.putString("material","minecraft:"+trimMaterial(role));
        trim.putString("pattern","minecraft:"+trimPattern(role));
        stack.getOrCreateTag().put("Trim",trim);
        stack.enchant(Enchantments.UNBREAKING, role >= 22 ? 3 : 2);
        return stack;
    }
    private static String trimMaterial(int role) {
        // Common: rusty; Uncommon: bright metal; Rare: gems; Epic: exotic; Legendary: netherite/amethyst.
        return switch(role) {
            case 10 -> "iron"; case 11 -> "copper";
            case 12 -> "emerald"; case 13 -> "lapis"; case 14 -> "redstone"; case 15 -> "gold";
            case 16 -> "redstone"; case 17 -> "amethyst"; case 18 -> "iron"; case 19 -> "emerald"; case 20 -> "lapis"; case 21 -> "copper";
            case 22 -> "amethyst"; case 23 -> "diamond"; case 24 -> "redstone"; case 25 -> "netherite"; case 26 -> "emerald";
            case 27 -> "gold"; case 28 -> "netherite"; case 29 -> "amethyst";
            default -> "iron";
        };
    }
    private static String trimPattern(int role) {
        return switch(role) {
            case 10 -> "sentry"; case 11 -> "ward";
            case 12 -> "wild"; case 13 -> "snout"; case 14 -> "rib"; case 15 -> "ward";
            case 16 -> "tide"; case 17 -> "eye"; case 18 -> "dune"; case 19 -> "coast"; case 20 -> "vex"; case 21 -> "spire";
            case 22 -> "spire"; case 23 -> "eye"; case 24 -> "tide"; case 25 -> "ward"; case 26 -> "wild";
            case 27 -> "silence"; case 28 -> "vex"; case 29 -> "raiser";
            default -> "sentry";
        };
    }
    /** RGB shell tint for Epic Knights compat, used when armor supports colored leather layers. */
    private static int shellColor(int role) {
        return switch(role) {
            case 10 -> 0x8b7355; case 11 -> 0x7d7d7d;
            case 12 -> 0x4a7bc4; case 13 -> 0x87ceeb; case 14 -> 0x963f3f; case 15 -> 0xf2c96b;
            case 16 -> 0xff6633; case 17 -> 0x2a1a4d; case 18 -> 0xc4b087; case 19 -> 0x3a6b2f; case 20 -> 0x2f3a5e; case 21 -> 0xa04030;
            case 22 -> 0x8a2be2; case 23 -> 0xe6d78a; case 24 -> 0xe64d1f; case 25 -> 0x1a1a1a; case 26 -> 0x7fe0d0;
            case 27 -> 0xffd966; case 28 -> 0x0f0d1a; case 29 -> 0x9370db;
            default -> 0x808080;
        };
    }
    /** Weapon name per hero. Adds flavor and reads at a glance in the tooltip. */
    private static String weaponName(int role) {
        return switch(role) {
            case 10 -> "Ironoath"; case 11 -> "Stonebreaker";
            case 12 -> "Thornsong"; case 13 -> "Stormbolt"; case 14 -> "Cinderfang"; case 15 -> "Oathkeeper";
            case 16 -> "Emberedge"; case 17 -> "Hollowshaft"; case 18 -> "Warbell"; case 19 -> "Verdantbow"; case 20 -> "Grimlance"; case 21 -> "Wildfang";
            case 22 -> "Voidcaller Staff"; case 23 -> "Starweaver Staff"; case 24 -> "Ashenheart Staff"; case 25 -> "Ironclad Bulwark"; case 26 -> "Skyrender";
            case 27 -> "Sunspire Staff"; case 28 -> "Nightcaller Staff"; case 29 -> "Chronoscepter";
            default -> "Hero weapon";
        };
    }
    /** Text color per rarity. Legendary is bright gold, Epic is arcane, etc. */
    private static ChatFormatting nameColor(int tier) {
        return switch(tier) {
            case 0 -> ChatFormatting.WHITE;
            case 1 -> ChatFormatting.GREEN;
            case 2 -> ChatFormatting.AQUA;
            case 3 -> ChatFormatting.LIGHT_PURPLE;
            case 4 -> ChatFormatting.GOLD;
            default -> ChatFormatting.WHITE;
        };
    }
    public static void equip(Mob mob,int role,SimpleContainer inventory) {
        int base = CoreHiring.heroBase(role);
        int tier = CoreHiring.heroTier(role);
        Item[] armor={Items.DIAMOND_HELMET,Items.DIAMOND_CHESTPLATE,Items.DIAMOND_LEGGINGS,Items.DIAMOND_BOOTS};
        // Legendaries get netherite; Epics diamond; Rares diamond; Uncommons diamond; Commons iron.
        if (tier == 4) armor = new Item[]{Items.NETHERITE_HELMET, Items.NETHERITE_CHESTPLATE, Items.NETHERITE_LEGGINGS, Items.NETHERITE_BOOTS};
        else if (tier == 0) armor = new Item[]{Items.IRON_HELMET, Items.IRON_CHESTPLATE, Items.IRON_LEGGINGS, Items.IRON_BOOTS};
        EquipmentSlot[] slots={EquipmentSlot.HEAD,EquipmentSlot.CHEST,EquipmentSlot.LEGS,EquipmentSlot.FEET};
        int shell = shellColor(role);
        for(int i=0;i<4;i++){
            ItemStack stack=armor(role,armor[i]);
            // Epic Knights compat protection scales with rarity: 4 base + 1 per tier.
            stack=com.devfarinsky.siegeoverhaul.compat.EpicKnightsCompatibility.armor(stack, 4 + tier, slots[i], shell);
            inventory.setItem(i,stack);mob.setItemSlot(slots[i],stack);
        }
        // Mages carry a blaze rod for the visible staff look; ranged carry bow/crossbow; melee sword.
        Item weaponItem;
        if (mage(role)) weaponItem = Items.BLAZE_ROD;
        else if (base == 2) weaponItem = Items.BOW;
        else if (base == 3) weaponItem = Items.CROSSBOW;
        else weaponItem = Items.DIAMOND_SWORD;
        ItemStack weapon = new ItemStack(weaponItem);
        if (base == 2 && !mage(role)) weapon.enchant(Enchantments.POWER_ARROWS, 2 + Math.max(0, tier - 1));
        else if (base == 3 && !mage(role)) { weapon.enchant(Enchantments.QUICK_CHARGE, 2); weapon.enchant(Enchantments.PIERCING, 1 + Math.max(0, tier - 1)); }
        else if (!mage(role)) weapon.enchant(Enchantments.SHARPNESS, 2 + Math.max(0, tier - 1));
        weapon.enchant(Enchantments.UNBREAKING, mage(role) ? 3 : 2);
        weapon.setHoverName(Component.literal(weaponName(role)).withStyle(nameColor(tier)));
        inventory.setItem(5, weapon); mob.setItemSlot(EquipmentSlot.MAINHAND, weapon);
        // Shield in offhand for shield-based heroes (base==1) that aren't mages.
        if (base == 1 && !mage(role)) {
            ItemStack shield = new ItemStack(Items.SHIELD); CompoundTag be = new CompoundTag();
            be.putInt("Base", tier >= 3 ? DyeColor.PURPLE.getId() : (tier == 2 ? DyeColor.LIGHT_BLUE.getId() : DyeColor.BLUE.getId()));
            var patterns = new net.minecraft.nbt.ListTag(); CompoundTag pattern = new CompoundTag();
            pattern.putString("Pattern", "bo"); pattern.putInt("Color", DyeColor.YELLOW.getId()); patterns.add(pattern); be.put("Patterns", patterns);
            shield.getOrCreateTag().put("BlockEntityTag", be); shield.enchant(Enchantments.UNBREAKING, 2);
            inventory.setItem(4, shield); mob.setItemSlot(EquipmentSlot.OFFHAND, shield);
        }
        mob.getPersistentData().putInt("SiegeHeroRole",role);
        mob.setCustomName(Component.literal(CoreHiring.NAMES[role]).withStyle(nameColor(tier)));
    }
    static boolean ready(long now,long next) { return next<=now || next>now+1200; }
    private static int role(Mob mob) {
        var tag=mob.getPersistentData();
        if(!tag.getBoolean("SiegeHiredHero") || EnemyHiringProtection.enemy(mob))return -1;
        if(!tag.contains("SiegeHeroRole")) {
            var id=net.minecraftforge.registries.ForgeRegistries.ENTITY_TYPES.getKey(mob.getType());
            if(id==null)return -1;
            int role=switch(id.getPath()){case "recruit"->10;case "recruit_shieldman"->11;case "bowman"->12;case "crossbowman"->13;default->-1;};
            tag.putInt("SiegeHeroRole",role);
        }
        return tag.getInt("SiegeHeroRole");
    }
    private static boolean hostile(Mob hero,LivingEntity target) {
        return target.isAlive() && EnemyHiringProtection.enemy(target) && !hero.isAlliedTo(target) && hero.hasLineOfSight(target);
    }
    static boolean charged(CompoundTag tag,String key,int interval) {
        int hits=Math.min(interval,Math.max(0,tag.getInt(key))+1);
        tag.putInt(key,hits);return hits>=interval;
    }
    private static void sparkle(ServerLevel level,LivingEntity target,net.minecraft.core.particles.SimpleParticleType particle) {
        level.sendParticles(particle,target.getX(),target.getY()+1,target.getZ(),16,.4,.6,.4,.02);
    }
    private static void burst(ServerLevel level, double x, double y, double z, net.minecraft.core.particles.SimpleParticleType particle, int count, double spread, double speed) {
        level.sendParticles(particle, x, y, z, count, spread, spread, spread, speed);
    }
    /** Same-owner friendly living entities in a radius, excluding the hero itself. */
    private static java.util.List<LivingEntity> allies(ServerLevel level, Mob hero, double radius) {
        var owner = RecruitsBridge.ownerUuid(hero);
        if (owner.isEmpty()) return java.util.List.of();
        return level.getEntitiesOfClass(LivingEntity.class, hero.getBoundingBox().inflate(radius), other -> other != hero && other.isAlive()
                && !EnemyHiringProtection.enemy(other) && hero.hasLineOfSight(other)
                && (other.getUUID().equals(owner.get()) || RecruitsBridge.ownerUuid(other).equals(owner)));
    }
    /** Hostile living entities in a radius the hero can see. */
    private static java.util.List<LivingEntity> enemies(ServerLevel level, Mob hero, double radius) {
        return level.getEntitiesOfClass(LivingEntity.class, hero.getBoundingBox().inflate(radius),
                other -> hostile(hero, other));
    }
    @SubscribeEvent
    public static void tick(LivingEvent.LivingTickEvent event) {
        if(!(event.getEntity() instanceof Mob mob) || !(mob.level() instanceof ServerLevel level)
                || mob.tickCount%20!=0 || !mob.isAlive() || mob.isNoAi()) return;
        int r = role(mob); if (r < 0) return;
        long now = level.getGameTime();
        var tag = mob.getPersistentData();
        // Dawnwarden (15): shield the most-hurt ally in a 6-block bubble every 30s.
        if (r == 15 && ready(now, tag.getLong("SiegeHeroNext"))) {
            var candidates = allies(level, mob, 6).stream()
                    .filter(o -> o.getHealth() <= o.getMaxHealth() * .3F && !o.hasEffect(MobEffects.ABSORPTION)
                            && ready(now, o.getPersistentData().getLong("SiegeLastLight"))).toList();
            if (!candidates.isEmpty()) {
                var ally = candidates.stream().min(java.util.Comparator.comparingDouble(o -> o.getHealth() / o.getMaxHealth())).get();
                ally.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 100, 1));
                ally.getPersistentData().putLong("SiegeLastLight", now + 600);
                tag.putLong("SiegeHeroNext", now + 600);
                sparkle(level, ally, net.minecraft.core.particles.ParticleTypes.TOTEM_OF_UNDYING);
                level.playSound(null, ally.blockPosition(), net.minecraft.sounds.SoundEvents.AMETHYST_BLOCK_CHIME, net.minecraft.sounds.SoundSource.NEUTRAL, 0.7F, 1.2F);
            }
            return;
        }
        // Ironclad (25): passive Resistance II aura to nearby allies. Refreshed every second.
        if (r == 25) {
            for (var a : allies(level, mob, 6)) a.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 60, 1, false, true, true));
            if (mob.tickCount % 40 == 0) burst(level, mob.getX(), mob.getY() + 1, mob.getZ(), net.minecraft.core.particles.ParticleTypes.ENCHANT, 8, 1.5, 0.02);
            return;
        }
        // Voidweaver (22): void nova every 15s (300 ticks). 6-block AoE, 5 damage, purple particle ring.
        if (r == 22 && mob.getTarget() != null && ready(now, tag.getLong("SiegeHeroNext"))) {
            tag.putLong("SiegeHeroNext", now + 300);
            burst(level, mob.getX(), mob.getY() + 1, mob.getZ(), net.minecraft.core.particles.ParticleTypes.PORTAL, 80, 3.0, 0.4);
            burst(level, mob.getX(), mob.getY() + 1, mob.getZ(), net.minecraft.core.particles.ParticleTypes.REVERSE_PORTAL, 40, 2.0, 0.2);
            level.playSound(null, mob.blockPosition(), net.minecraft.sounds.SoundEvents.ENDERMAN_TELEPORT, net.minecraft.sounds.SoundSource.HOSTILE, 1.0F, 0.6F);
            for (var enemy : enemies(level, mob, 6)) enemy.hurt(level.damageSources().indirectMagic(mob, mob), 5);
            return;
        }
        // Solmyra Radiant (27): sunlight column on all enemies within 8 blocks every 20s.
        if (r == 27 && mob.getTarget() != null && ready(now, tag.getLong("SiegeHeroNext"))) {
            tag.putLong("SiegeHeroNext", now + 400);
            var foes = enemies(level, mob, 8);
            if (foes.isEmpty()) return;
            for (var enemy : foes) {
                for (int y = 0; y < 6; y++) burst(level, enemy.getX(), enemy.getY() + y, enemy.getZ(), net.minecraft.core.particles.ParticleTypes.END_ROD, 6, 0.15, 0.02);
                enemy.hurt(level.damageSources().indirectMagic(mob, mob), 6);
                enemy.setSecondsOnFire(4);
            }
            level.playSound(null, mob.blockPosition(), net.minecraft.sounds.SoundEvents.AMETHYST_BLOCK_CHIME, net.minecraft.sounds.SoundSource.HOSTILE, 1.2F, 1.6F);
            return;
        }
        // Nightcaller (28): summon two wolf allies for 30s, 45s cooldown. Wolves are tamed to hero owner via NBT tag; despawn timer via SiegeShadowDespawn.
        if (r == 28 && mob.getTarget() != null && ready(now, tag.getLong("SiegeHeroNext"))) {
            tag.putLong("SiegeHeroNext", now + 900);
            for (int i = 0; i < 2; i++) {
                var wolf = net.minecraft.world.entity.EntityType.WOLF.create(level);
                if (wolf == null) continue;
                double angle = (i * Math.PI); wolf.setPos(mob.getX() + Math.cos(angle) * 1.5, mob.getY(), mob.getZ() + Math.sin(angle) * 1.5);
                wolf.getPersistentData().putLong("SiegeShadowDespawn", now + 600);
                wolf.getPersistentData().putBoolean("SiegeHiredHero", true);
                wolf.setCustomName(Component.literal("Shadow Wolf").withStyle(ChatFormatting.DARK_PURPLE));
                if (mob.getTarget() != null) wolf.setTarget(mob.getTarget());
                level.addFreshEntity(wolf);
                burst(level, wolf.getX(), wolf.getY() + 0.5, wolf.getZ(), net.minecraft.core.particles.ParticleTypes.SOUL, 30, 0.5, 0.05);
            }
            level.playSound(null, mob.blockPosition(), net.minecraft.sounds.SoundEvents.WOLF_HOWL, net.minecraft.sounds.SoundSource.HOSTILE, 1.5F, 0.5F);
            return;
        }
        // Shadow wolf cleanup: entities with SiegeShadowDespawn past their deadline vanish in a soul burst.
        if (mob.getPersistentData().contains("SiegeShadowDespawn") && mob.getPersistentData().getLong("SiegeShadowDespawn") <= now) {
            burst(level, mob.getX(), mob.getY() + 0.5, mob.getZ(), net.minecraft.core.particles.ParticleTypes.SOUL, 20, 0.4, 0.05);
            mob.discard();
            return;
        }
        // Chronos (29): every 30s, all enemies within 10 blocks slowed 90% for 4s.
        if (r == 29 && mob.getTarget() != null && ready(now, tag.getLong("SiegeHeroNext"))) {
            tag.putLong("SiegeHeroNext", now + 600);
            for (var enemy : enemies(level, mob, 10)) {
                enemy.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 80, 4));
                burst(level, enemy.getX(), enemy.getY() + 1, enemy.getZ(), net.minecraft.core.particles.ParticleTypes.PORTAL, 20, 0.6, 0.05);
            }
            level.playSound(null, mob.blockPosition(), net.minecraft.sounds.SoundEvents.ELDER_GUARDIAN_CURSE, net.minecraft.sounds.SoundSource.HOSTILE, 1.0F, 1.5F);
        }
    }
    @SubscribeEvent
    public static void hit(LivingDamageEvent event) {
        if(event.getAmount()<=0 || !(event.getSource().getEntity() instanceof Mob mob)
                || !(mob.level() instanceof ServerLevel level) || !mob.isAlive() || mob.isNoAi()
                || !hostile(mob,event.getEntity()))return;
        int r = role(mob); long now = level.getGameTime(); var tag = mob.getPersistentData();
        boolean melee = event.getSource().getDirectEntity() == mob;
        var target = event.getEntity();
        // === MELEE HEROES ===
        if (melee) {
            // 10 Ironoath / 14 Bloodthorn: every 3rd hit heals 1 heart. Bloodthorn also grants absorption at full HP.
            if ((r == 10 || r == 14) && ready(now, tag.getLong("SiegeHeroNext"))) {
                if (charged(tag, "SiegeHeroHits", 3)) {
                    tag.putInt("SiegeHeroHits", 0); tag.putLong("SiegeHeroNext", now + 40);
                    if (mob.getHealth() < mob.getMaxHealth()) mob.heal(2);
                    else if (r == 14 && !mob.hasEffect(MobEffects.ABSORPTION)) mob.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 100, 0));
                    sparkle(level, mob, net.minecraft.core.particles.ParticleTypes.HEART);
                }
            }
            // 16 Emberstep: melee hits ignite target for 4s (with cooldown so it doesn't reapply every tick).
            if (r == 16 && ready(now, tag.getLong("SiegeHeroNext"))) {
                target.setSecondsOnFire(4); tag.putLong("SiegeHeroNext", now + 20);
                burst(level, target.getX(), target.getY() + 1, target.getZ(), net.minecraft.core.particles.ParticleTypes.FLAME, 20, 0.4, 0.06);
            }
            // 21 Wildsong: kills grant +20% attack speed for 6s (rider effect at kill time).
            if (r == 21 && target.getHealth() - event.getAmount() <= 0) {
                mob.addEffect(new MobEffectInstance(net.minecraft.world.effect.MobEffects.MOVEMENT_SPEED, 120, 1, false, true, true));
                burst(level, mob.getX(), mob.getY() + 1, mob.getZ(), net.minecraft.core.particles.ParticleTypes.CRIT, 20, 0.3, 0.1);
            }
            // 23 Starweaver (mage): every hit summons a homing star to a nearby second enemy for 4 magic damage.
            if (r == 23 && ready(now, tag.getLong("SiegeHeroNext"))) {
                tag.putLong("SiegeHeroNext", now + 20);
                var second = enemies(level, mob, 6).stream().filter(e -> e != target).findFirst().orElse(null);
                if (second != null) {
                    second.hurt(level.damageSources().indirectMagic(mob, mob), 4);
                    // Draw a trail of end_rod particles from hero to the second enemy.
                    double sx = mob.getX(), sy = mob.getY() + 1, sz = mob.getZ();
                    double dx = second.getX() - sx, dy = second.getY() + 1 - sy, dz = second.getZ() - sz;
                    for (int i = 0; i < 20; i++) {
                        double t = i / 20.0;
                        burst(level, sx + dx * t, sy + dy * t, sz + dz * t, net.minecraft.core.particles.ParticleTypes.END_ROD, 1, 0.02, 0.0);
                    }
                }
            }
            // 24 Ashenheart (mage): melee hit triggers a small non-block-damaging explosion at the target.
            if (r == 24 && ready(now, tag.getLong("SiegeHeroNext"))) {
                tag.putLong("SiegeHeroNext", now + 60);
                var foes = enemies(level, mob, 3);
                for (var enemy : foes) enemy.hurt(level.damageSources().indirectMagic(mob, mob), 4);
                burst(level, target.getX(), target.getY() + 1, target.getZ(), net.minecraft.core.particles.ParticleTypes.FLAME, 60, 1.5, 0.3);
                burst(level, target.getX(), target.getY() + 1, target.getZ(), net.minecraft.core.particles.ParticleTypes.LARGE_SMOKE, 20, 1.0, 0.1);
                level.playSound(null, target.blockPosition(), net.minecraft.sounds.SoundEvents.GENERIC_EXPLODE, net.minecraft.sounds.SoundSource.HOSTILE, 0.7F, 1.4F);
            }
            return;
        }
        // === RANGED HEROES (projectile hits) ===
        if (!(event.getSource().getDirectEntity() instanceof Projectile projectile) || projectile.getOwner() != mob) return;
        // 12 Stormbow: every 4th arrow chains lightning to two nearby foes for 4 magic damage.
        if (r == 12) {
            if (!charged(tag, "SiegeHeroHits", 4)) { sparkle(level, mob, net.minecraft.core.particles.ParticleTypes.ELECTRIC_SPARK); return; }
            if (!ready(now, tag.getLong("SiegeHeroNext"))) return;
            tag.putInt("SiegeHeroHits", 0); tag.putLong("SiegeHeroNext", now + 60);
            var primary = target;
            var foes = level.getEntitiesOfClass(LivingEntity.class, primary.getBoundingBox().inflate(6),
                    e -> e != primary && hostile(mob, e) && primary.hasLineOfSight(e));
            foes.sort(java.util.Comparator.comparingDouble(primary::distanceToSqr));
            for (var enemy : foes.stream().limit(2).toList()) {
                if (enemy.hurt(level.damageSources().indirectMagic(mob, mob), 4)) sparkle(level, enemy, net.minecraft.core.particles.ParticleTypes.ELECTRIC_SPARK);
            }
            sparkle(level, primary, net.minecraft.core.particles.ParticleTypes.ELECTRIC_SPARK);
            return;
        }
        // 13 Frostbinder: bolt slows up to 3 enemies for 3s.
        if (r == 13 && ready(now, tag.getLong("SiegeHeroNext"))) {
            tag.putLong("SiegeHeroNext", now + 200);
            var primary = target;
            var foes = level.getEntitiesOfClass(LivingEntity.class, primary.getBoundingBox().inflate(3),
                    e -> hostile(mob, e) && primary.hasLineOfSight(e));
            foes.sort(java.util.Comparator.comparingDouble(primary::distanceToSqr));
            for (var enemy : foes.stream().limit(3).toList()) {
                if (!ready(now, enemy.getPersistentData().getLong("SiegeFrostNext"))) continue;
                enemy.getPersistentData().putLong("SiegeFrostNext", now + 100);
                enemy.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 1));
                sparkle(level, enemy, net.minecraft.core.particles.ParticleTypes.SNOWFLAKE);
            }
            return;
        }
        // 17 Hollowveil: every 3rd arrow marks target for +30% damage (Glowing + damage rider next hit).
        if (r == 17 && charged(tag, "SiegeHeroHits", 3)) {
            tag.putInt("SiegeHeroHits", 0);
            target.addEffect(new MobEffectInstance(MobEffects.GLOWING, 100, 0, false, true, true));
            target.getPersistentData().putLong("SiegeHollowMark", now + 100);
            sparkle(level, target, net.minecraft.core.particles.ParticleTypes.END_ROD);
            return;
        }
        // 19 Verdant: arrows root target for 2s (Slowness IV = effectively rooted).
        if (r == 19 && ready(now, tag.getLong("SiegeHeroNext"))) {
            tag.putLong("SiegeHeroNext", now + 60);
            target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 4, false, true, true));
            burst(level, target.getX(), target.getY(), target.getZ(), net.minecraft.core.particles.ParticleTypes.COMPOSTER, 30, 0.5, 0.02);
            return;
        }
        // 20 Grimwatch: bolts leave a lingering trail that damages any enemy stepping into it (short + cheap).
        if (r == 20) {
            double px = projectile.getX(), py = projectile.getY(), pz = projectile.getZ();
            burst(level, px, py, pz, net.minecraft.core.particles.ParticleTypes.CRIT, 8, 0.2, 0.05);
            for (var enemy : level.getEntitiesOfClass(LivingEntity.class, projectile.getBoundingBox().inflate(2), e -> hostile(mob, e))) {
                enemy.hurt(level.damageSources().indirectMagic(mob, mob), 2);
            }
            return;
        }
        // 26 Skyrender: on hit, arrow splits into 3 tracer arrows dealing 3 damage each to nearest foes.
        if (r == 26 && ready(now, tag.getLong("SiegeHeroNext"))) {
            tag.putLong("SiegeHeroNext", now + 30);
            var primary = target;
            var foes = level.getEntitiesOfClass(LivingEntity.class, primary.getBoundingBox().inflate(5),
                    e -> e != primary && hostile(mob, e));
            foes.sort(java.util.Comparator.comparingDouble(primary::distanceToSqr));
            for (var enemy : foes.stream().limit(3).toList()) {
                enemy.hurt(level.damageSources().indirectMagic(mob, mob), 3);
                sparkle(level, enemy, net.minecraft.core.particles.ParticleTypes.CRIT);
            }
        }
    }
    /** Bonus damage rider: consume Hollowveil mark for +30% damage on next hit. */
    @SubscribeEvent
    public static void markedDamage(LivingHurtEvent event) {
        if (!(event.getEntity().level() instanceof ServerLevel level)) return;
        var target = event.getEntity();
        var pd = target.getPersistentData();
        if (!pd.contains("SiegeHollowMark") || pd.getLong("SiegeHollowMark") <= level.getGameTime()) return;
        pd.remove("SiegeHollowMark");
        event.setAmount(event.getAmount() * 1.30F);
        sparkle(level, target, net.minecraft.core.particles.ParticleTypes.END_ROD);
    }
    /**
     * Warbell (18): on any successful shield block, emit a 4-block shockwave that
     * knocks back and staggers enemies (Slowness III for 1.5s).
     */
    @SubscribeEvent
    public static void shieldBlock(net.minecraftforge.event.entity.living.LivingAttackEvent event) {
        if (!(event.getEntity() instanceof Mob mob) || !(mob.level() instanceof ServerLevel level)) return;
        int r = role(mob); if (r < 0) return;
        long now = level.getGameTime();
        var tag = mob.getPersistentData();
        // Warbell (18): passive shockwave on block. Uses cooldown to avoid spam.
        if (r == 18 && mob.isBlocking() && ready(now, tag.getLong("SiegeHeroNext"))) {
            tag.putLong("SiegeHeroNext", now + 40);
            burst(level, mob.getX(), mob.getY() + 1, mob.getZ(), net.minecraft.core.particles.ParticleTypes.EXPLOSION, 6, 1.0, 0.05);
            for (var enemy : enemies(level, mob, 4)) {
                enemy.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 30, 2));
                enemy.knockback(0.8, mob.getX() - enemy.getX(), mob.getZ() - enemy.getZ());
            }
            level.playSound(null, mob.blockPosition(), net.minecraft.sounds.SoundEvents.ANVIL_LAND, net.minecraft.sounds.SoundSource.HOSTILE, 0.5F, 1.6F);
            return;
        }
        // Stonehand (11): on block, allies within 4 blocks get Resistance I for 3s (short throttle).
        if (r == 11 && mob.isBlocking() && ready(now, tag.getLong("SiegeHeroNext"))) {
            tag.putLong("SiegeHeroNext", now + 40);
            for (var a : allies(level, mob, 4)) a.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 60, 0, false, true, true));
            burst(level, mob.getX(), mob.getY() + 1, mob.getZ(), net.minecraft.core.particles.ParticleTypes.CLOUD, 10, 0.6, 0.02);
        }
    }
}
