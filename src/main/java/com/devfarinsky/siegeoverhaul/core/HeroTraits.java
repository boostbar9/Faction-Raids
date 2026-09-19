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
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.*;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraftforge.event.entity.living.*;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.UUID;

/** Shared player and enemy hero specializations. Native AI, finite equipment, visible effects and bounded cooldowns. */
@Mod.EventBusSubscriber(modid=SiegeOverhaul.MOD_ID)
public final class HeroTraits {
    private HeroTraits() {}
    private static final UUID WILDSONG_ATTACK_SPEED_ID = UUID.fromString("3f68d978-7160-42e3-a81e-5476f362f969");
    private static final String WILDSONG_ATTACK_SPEED_TAG = "SiegeWildsongAttackSpeedUntil";
    private static final String OLYMPIAN_IDENTITY_TAG = "SiegeOlympianHeroIdentity";
    private static final int OLYMPIAN_IDENTITY_SCHEMA = 2;
    /** Signature-ability description shown on hero cards. Keep concise (fits card). */
    public static String description(int role) {
        return switch(role) {
            case 10 -> "Ares' Blade: every third melee hit heals 1 heart";
            case 11 -> "Athena's Aegis: blocked hits ward nearby allies";
            case 12 -> "Poseidon's Storm: every fourth arrow chains lightning";
            case 13 -> "Artemis' Moonfrost: a bolt slows up to three foes";
            case 14 -> "Ares' Fury: a full-health strike grants absorption";
            case 15 -> "Hephaestus' Ward: shields a badly wounded ally";
            case 16 -> "Hephaestus' Flame: melee hits ignite the target";
            case 17 -> "Poseidon's Fog: every third arrow marks its target";
            case 18 -> "Ares' Warbell: blocked hits unleash a shockwave";
            case 19 -> "Artemis' Roots: arrows root the target for 2s";
            case 20 -> "Hephaestus' Sunforge: bolts burst through an impact zone";
            case 21 -> "Athena's Tempo: kills grant +20% attack speed for 6s";
            case 22 -> "Poseidon's Tempest: casts a tidal nova every 15s";
            case 23 -> "Athena's Oracle: melee hits arc into a nearby foe";
            case 24 -> "Forgefire: melee hits trigger a flame burst (3s)";
            case 25 -> "Aegis Bulwark: Resistance II aura to nearby allies";
            case 26 -> "Artemis' Skyhunt: arrows split into three tracers";
            case 27 -> "Ares' Inferno: battle fury burns nearby foes";
            case 28 -> "Artemis' Hounds: summons two shadow wolves";
            case 29 -> "Poseidon's Undertow: heavily slows nearby foes";
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
            case 12 -> "lapis"; case 13 -> "emerald"; case 14 -> "redstone"; case 15 -> "copper";
            case 16 -> "redstone"; case 17 -> "lapis"; case 18 -> "iron"; case 19 -> "emerald"; case 20 -> "copper"; case 21 -> "diamond";
            case 22 -> "diamond"; case 23 -> "gold"; case 24 -> "redstone"; case 25 -> "netherite"; case 26 -> "emerald";
            case 27 -> "redstone"; case 28 -> "netherite"; case 29 -> "lapis";
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
            case 10 -> 0x8b302b; case 11 -> 0xe8e1c7;
            case 12 -> 0x256aa1; case 13 -> 0xb8c4bf; case 14 -> 0xa53a32; case 15 -> 0xb76e3b;
            case 16 -> 0xe85d2a; case 17 -> 0x315b7a; case 18 -> 0x8e3b32; case 19 -> 0x476b3a; case 20 -> 0xc46b32; case 21 -> 0xd7c98b;
            case 22 -> 0x2d79a8; case 23 -> 0xe6d78a; case 24 -> 0xd94a22; case 25 -> 0xd8e6ef; case 26 -> 0x7aa38b;
            case 27 -> 0x7f211d; case 28 -> 0x343c36; case 29 -> 0x1d557c;
            default -> 0x808080;
        };
    }
    /** Weapon name per hero. Adds flavor and reads at a glance in the tooltip. */
    private static String weaponName(int role) {
        return switch(role) {
            case 10 -> "Spear of Ares"; case 11 -> "Aegis Edge";
            case 12 -> "Stormbow of Poseidon"; case 13 -> "Moonfrost"; case 14 -> "Phobos Fang"; case 15 -> "Forgeward";
            case 16 -> "Forgefire"; case 17 -> "Fogpiercer"; case 18 -> "Warbell"; case 19 -> "Laurel Bow"; case 20 -> "Sunforge"; case 21 -> "Strategist's Blade";
            case 22 -> "Tempest Scepter"; case 23 -> "Oracle's Staff"; case 24 -> "Volcanic Staff"; case 25 -> "Aegis Bulwark"; case 26 -> "Orion's Bow";
            case 27 -> "Brand of Ares"; case 28 -> "Moon-Hound Crook"; case 29 -> "Undertow Staff";
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
    /** Rename untouched legacy defaults while preserving player-applied custom names. */
    static void ensureOlympianIdentity(Mob mob,int role) {
        if(!CoreHiring.isHero(role))return;
        CompoundTag tag=mob.getPersistentData();
        if(tag.getInt(OLYMPIAN_IDENTITY_TAG)>=OLYMPIAN_IDENTITY_SCHEMA)return;
        Component current=mob.getCustomName();
        String legacy=CoreHiring.legacyHeroName(role);
        String previous=CoreHiring.previousOlympianHeroName(role);
        String currentText=current==null?"":current.getString();
        boolean generatedEnemyLabel=("Enemy Hero · "+legacy).equals(currentText)
                || ("Enemy Hero · "+previous).equals(currentText);
        if(current==null || legacy.equals(currentText) || previous.equals(currentText) || generatedEnemyLabel) {
            String replacement=generatedEnemyLabel?"Enemy Hero · "+CoreHiring.NAMES[role]:CoreHiring.NAMES[role];
            ChatFormatting color=generatedEnemyLabel?ChatFormatting.LIGHT_PURPLE:nameColor(CoreHiring.heroTier(role));
            mob.setCustomName(Component.literal(replacement).withStyle(color));
        }
        tag.putInt(OLYMPIAN_IDENTITY_TAG,OLYMPIAN_IDENTITY_SCHEMA);
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
        ensureOlympianIdentity(mob,role);
    }
    static boolean ready(long now,long next) { return next<=now || next>now+1200; }
    private static int role(Mob mob) {
        var tag=mob.getPersistentData();
        if (EnemyHeroes.active(mob)) return tag.getInt("SiegeHeroRole");
        if(!tag.getBoolean("SiegeHiredHero") || EnemyHiringProtection.enemy(mob))return -1;
        if(!tag.contains("SiegeHeroRole")) {
            var id=net.minecraftforge.registries.ForgeRegistries.ENTITY_TYPES.getKey(mob.getType());
            if(id==null)return -1;
            int role=switch(id.getPath()){case "recruit"->10;case "recruit_shieldman"->11;case "bowman"->12;case "crossbowman"->13;default->-1;};
            tag.putInt("SiegeHeroRole",role);
        }
        int role = tag.getInt("SiegeHeroRole");
        return CoreHiring.isHero(role) ? role : -1;
    }
    static boolean hostile(Mob hero,LivingEntity target) {
        return target != hero && target.isAlive() && !hero.isAlliedTo(target) && hero.hasLineOfSight(target)
                && (EnemyHeroes.active(hero) ? EnemyHeroes.defender(hero, target) : EnemyHiringProtection.enemy(target));
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
    static void applyWildsongAttackSpeed(Mob mob, long now) {
        var attribute = mob.getAttribute(Attributes.ATTACK_SPEED);
        if (attribute == null) return;
        if (attribute.getModifier(WILDSONG_ATTACK_SPEED_ID) == null) {
            attribute.addTransientModifier(new AttributeModifier(WILDSONG_ATTACK_SPEED_ID, "SiegeWildsongAttackSpeed", 0.20, AttributeModifier.Operation.MULTIPLY_TOTAL));
        }
        mob.getPersistentData().putLong(WILDSONG_ATTACK_SPEED_TAG, now + 120);
    }
    static void refreshWildsongAttackSpeed(ServerLevel level, Mob mob, long now) {
        var attribute = mob.getAttribute(Attributes.ATTACK_SPEED);
        if (attribute == null) return;
        var tag = mob.getPersistentData();
        long until = tag.getLong(WILDSONG_ATTACK_SPEED_TAG);
        if (until > now && until <= now + 120) {
            if (attribute.getModifier(WILDSONG_ATTACK_SPEED_ID) == null) {
                attribute.addTransientModifier(new AttributeModifier(WILDSONG_ATTACK_SPEED_ID, "SiegeWildsongAttackSpeed", 0.20, AttributeModifier.Operation.MULTIPLY_TOTAL));
            }
            if (mob.tickCount % 20 == 0) burst(level, mob.getX(), mob.getY() + 1, mob.getZ(), net.minecraft.core.particles.ParticleTypes.HAPPY_VILLAGER, 6, 0.4, 0.02);
            return;
        }
        if (attribute.getModifier(WILDSONG_ATTACK_SPEED_ID) != null) attribute.removeModifier(WILDSONG_ATTACK_SPEED_ID);
        if (tag.contains(WILDSONG_ATTACK_SPEED_TAG)) tag.remove(WILDSONG_ATTACK_SPEED_TAG);
    }
    /** Same-owner friendly living entities in a radius, excluding the hero itself. */
    private static java.util.List<LivingEntity> allies(ServerLevel level, Mob hero, double radius) {
        if (EnemyHeroes.active(hero)) return level.getEntitiesOfClass(LivingEntity.class, hero.getBoundingBox().inflate(radius),
                other -> other != hero && other.isAlive() && hero.hasLineOfSight(other) && EnemyHeroes.ally(hero, other));
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
        if(!(event.getEntity() instanceof Mob mob) || !(mob.level() instanceof ServerLevel level)) return;
        if (mob.getPersistentData().contains(WILDSONG_ATTACK_SPEED_TAG))
            refreshWildsongAttackSpeed(level, mob, level.getGameTime());
        if (mob.tickCount % 20 != 0) return;
        // Summons are not recruit heroes. Process their saved deadline before hero/AI gates,
        // including legacy wolves incorrectly stamped as hired heroes in 4.19/4.20.
        if (mob instanceof net.minecraft.world.entity.animal.Wolf
                && mob.getPersistentData().contains("SiegeShadowDespawn", net.minecraft.nbt.Tag.TAG_LONG)) {
            var summonTag = mob.getPersistentData();
            summonTag.remove("SiegeHiredHero");
            summonTag.remove("SiegeHeroRole");
            if (summonTag.getLong("SiegeShadowDespawn") <= level.getGameTime()) {
                burst(level, mob.getX(), mob.getY() + 0.5, mob.getZ(), net.minecraft.core.particles.ParticleTypes.SOUL, 20, 0.4, 0.05);
                mob.discard();
            }
            return;
        }
        if (!mob.isAlive() || mob.isNoAi()) return;
        int r = role(mob); if (r < 0) return;
        ensureOlympianIdentity(mob,r);
        long now = level.getGameTime();
        var tag = mob.getPersistentData();
        // Apollo's Dawn (15): shield the most-hurt ally in a 6-block bubble every 30s.
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
        // Athena's Bulwark (25): passive Resistance II aura to nearby allies. Refreshed every second.
        if (r == 25) {
            for (var a : allies(level, mob, 6)) a.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 60, 1, false, true, true));
            if (mob.tickCount % 40 == 0) burst(level, mob.getX(), mob.getY() + 1, mob.getZ(), net.minecraft.core.particles.ParticleTypes.ENCHANT, 8, 1.5, 0.02);
            return;
        }
        // Athena's Judgment (22): arcane nova every 15s (300 ticks). 6-block AoE, 5 damage.
        if (r == 22 && mob.getTarget() != null && ready(now, tag.getLong("SiegeHeroNext"))) {
            tag.putLong("SiegeHeroNext", now + 300);
            burst(level, mob.getX(), mob.getY() + 1, mob.getZ(), net.minecraft.core.particles.ParticleTypes.PORTAL, 80, 3.0, 0.4);
            burst(level, mob.getX(), mob.getY() + 1, mob.getZ(), net.minecraft.core.particles.ParticleTypes.REVERSE_PORTAL, 40, 2.0, 0.2);
            level.playSound(null, mob.blockPosition(), net.minecraft.sounds.SoundEvents.ENDERMAN_TELEPORT, net.minecraft.sounds.SoundSource.HOSTILE, 1.0F, 0.6F);
            for (var enemy : enemies(level, mob, 6)) enemy.hurt(level.damageSources().indirectMagic(mob, mob), 5);
            return;
        }
        // Apollo's Chosen (27): sunlight column on all enemies within 8 blocks every 20s.
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
        // Artemis' Hounds (28): summon two wolves for 30s, 45s cooldown; saved deadline identifies summons.
        if (r == 28 && mob.getTarget() != null && ready(now, tag.getLong("SiegeHeroNext"))) {
            tag.putLong("SiegeHeroNext", now + 900);
            for (int i = 0; i < 2; i++) {
                var wolf = net.minecraft.world.entity.EntityType.WOLF.create(level);
                if (wolf == null) continue;
                double angle = (i * Math.PI); wolf.setPos(mob.getX() + Math.cos(angle) * 1.5, mob.getY(), mob.getZ() + Math.sin(angle) * 1.5);
                wolf.getPersistentData().putLong("SiegeShadowDespawn", now + 600);
                wolf.setCustomName(Component.literal("Shadow Wolf").withStyle(ChatFormatting.DARK_PURPLE));
                if (mob.getTarget() != null) wolf.setTarget(mob.getTarget());
                if (!EnemyHeroes.prepareShadow(level, mob, wolf)) { wolf.discard(); continue; }
                if (!level.addFreshEntity(wolf)) { wolf.discard(); continue; }
                burst(level, wolf.getX(), wolf.getY() + 0.5, wolf.getZ(), net.minecraft.core.particles.ParticleTypes.SOUL, 30, 0.5, 0.05);
            }
            level.playSound(null, mob.blockPosition(), net.minecraft.sounds.SoundEvents.WOLF_HOWL, net.minecraft.sounds.SoundSource.HOSTILE, 1.5F, 0.5F);
            return;
        }
        // Hermes' Hourglass (29): every 30s, all enemies within 10 blocks slowed 90% for 4s.
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
            // 10 Ironoath: every 3rd hit heals 1 heart.
            if (r == 10 && ready(now, tag.getLong("SiegeHeroNext"))) {
                if (charged(tag, "SiegeHeroHits", 3)) {
                    tag.putInt("SiegeHeroHits", 0); tag.putLong("SiegeHeroNext", now + 40);
                    if (mob.getHealth() < mob.getMaxHealth()) mob.heal(2);
                    sparkle(level, mob, net.minecraft.core.particles.ParticleTypes.HEART);
                }
            }
            // 14 Ares' Fury: at full health, next melee hit grants absorption.
            if (r == 14 && ready(now, tag.getLong("SiegeHeroNext"))
                    && mob.getHealth() >= mob.getMaxHealth() && !mob.hasEffect(MobEffects.ABSORPTION)) {
                mob.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 100, 0, false, true, true));
                tag.putLong("SiegeHeroNext", now + 80);
                burst(level, mob.getX(), mob.getY() + 1, mob.getZ(), net.minecraft.core.particles.ParticleTypes.CRIMSON_SPORE, 20, 0.4, 0.02);
                level.playSound(null, mob.blockPosition(), net.minecraft.sounds.SoundEvents.BEACON_ACTIVATE, net.minecraft.sounds.SoundSource.HOSTILE, 0.6F, 1.2F);
            }
            // 16 Emberstep: melee hits ignite target for 4s (with cooldown so it doesn't reapply every tick).
            if (r == 16 && ready(now, tag.getLong("SiegeHeroNext"))) {
                target.setSecondsOnFire(4); tag.putLong("SiegeHeroNext", now + 20);
                burst(level, target.getX(), target.getY() + 1, target.getZ(), net.minecraft.core.particles.ParticleTypes.FLAME, 20, 0.4, 0.06);
            }
            // 23 Starweaver (mage): every hit summons a homing star to a nearby second enemy for 4 magic damage.
            if (r == 23 && ready(now, tag.getLong("SiegeHeroNext"))) {
                var starTarget = enemies(level, mob, 6).stream().filter(e -> e != target).findFirst().orElse(null);
                if (starTarget == null) return;
                tag.putLong("SiegeHeroNext", now + 20);
                starTarget.hurt(level.damageSources().indirectMagic(mob, mob), 4);
                // Draw a trail of end_rod particles from hero to the star target.
                double sx = mob.getX(), sy = mob.getY() + 1, sz = mob.getZ();
                double dx = starTarget.getX() - sx, dy = starTarget.getY() + 1 - sy, dz = starTarget.getZ() - sz;
                for (int i = 0; i < 20; i++) {
                    double t = i / 20.0;
                    burst(level, sx + dx * t, sy + dy * t, sz + dz * t, net.minecraft.core.particles.ParticleTypes.END_ROD, 1, 0.02, 0.0);
                }
                burst(level, starTarget.getX(), starTarget.getY() + 1, starTarget.getZ(), net.minecraft.core.particles.ParticleTypes.GLOW, 8, 0.2, 0.02);
                level.playSound(null, starTarget.blockPosition(), net.minecraft.sounds.SoundEvents.AMETHYST_CLUSTER_HIT, net.minecraft.sounds.SoundSource.HOSTILE, 0.6F, 1.5F);
            }
            // Hephaestus' Flame uses the native melee/staff attack; no projectile is created by that AI.
            if (r == 24 && ready(now, tag.getLong("SiegeHeroNext"))) {
                tag.putLong("SiegeHeroNext", now + 60);
                var foes = level.getEntitiesOfClass(LivingEntity.class, target.getBoundingBox().inflate(3), e -> hostile(mob, e));
                for (var enemy : foes) enemy.hurt(level.damageSources().indirectMagic(mob, mob), 4);
                burst(level, target.getX(), target.getY() + 1, target.getZ(), net.minecraft.core.particles.ParticleTypes.FLAME, 60, 1.5, 0.3);
                burst(level, target.getX(), target.getY() + 1, target.getZ(), net.minecraft.core.particles.ParticleTypes.LARGE_SMOKE, 24, 1.0, 0.1);
                burst(level, target.getX(), target.getY() + 1, target.getZ(), net.minecraft.core.particles.ParticleTypes.LAVA, 10, 0.7, 0.05);
                level.playSound(null, target.blockPosition(), net.minecraft.sounds.SoundEvents.GENERIC_EXPLODE, net.minecraft.sounds.SoundSource.HOSTILE, 0.7F, 1.4F);
                return;
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
                if (enemy.hurt(level.damageSources().indirectMagic(mob, mob), 4)) {
                    sparkle(level, enemy, net.minecraft.core.particles.ParticleTypes.ELECTRIC_SPARK);
                    double sx = primary.getX(), sy = primary.getY() + 1, sz = primary.getZ();
                    double dx = enemy.getX() - sx, dy = enemy.getY() + 1 - sy, dz = enemy.getZ() - sz;
                    for (int i = 0; i < 10; i++) {
                        double t = i / 10.0;
                        burst(level, sx + dx * t, sy + dy * t, sz + dz * t, net.minecraft.core.particles.ParticleTypes.ELECTRIC_SPARK, 1, 0.02, 0.0);
                    }
                }
            }
            sparkle(level, primary, net.minecraft.core.particles.ParticleTypes.ELECTRIC_SPARK);
            level.playSound(null, primary.blockPosition(), net.minecraft.sounds.SoundEvents.LIGHTNING_BOLT_IMPACT, net.minecraft.sounds.SoundSource.HOSTILE, 0.6F, 1.3F);
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
        // 20 Grimwatch: bolts burst into a short piercing impact zone.
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
    @SubscribeEvent
    public static void heroKill(LivingDeathEvent event) {
        if (!(event.getSource().getEntity() instanceof Mob mob) || !(mob.level() instanceof ServerLevel level)
                || !mob.isAlive() || mob.isNoAi() || role(mob) != 21) return;
        var target = event.getEntity();
        // Death events run after lethal damage; the target is no longer alive.
        boolean enemy = EnemyHeroes.active(mob)
                ? EnemyHeroes.defenderIdentity(mob, target) : EnemyHiringProtection.enemy(target);
        if (!enemy || mob.isAlliedTo(target)) return;
        applyWildsongAttackSpeed(mob, level.getGameTime());
        burst(level, mob.getX(), mob.getY() + 1, mob.getZ(), net.minecraft.core.particles.ParticleTypes.HAPPY_VILLAGER, 20, 0.3, 0.1);
    }
    /** Bonus damage rider: consume Hollowveil mark for +30% damage on next hit. */
    @SubscribeEvent
    public static void markedDamage(LivingHurtEvent event) {
        if (!(event.getEntity().level() instanceof ServerLevel level)) return;
        var target = event.getEntity();
        var pd = target.getPersistentData();
        if (pd.contains("SiegeStoneguardUntil")) {
            long until = pd.getLong("SiegeStoneguardUntil");
            if (until > level.getGameTime()) {
                event.setAmount(event.getAmount() * 0.75F);
                if (target.tickCount % 5 == 0) burst(level, target.getX(), target.getY() + 1, target.getZ(), net.minecraft.core.particles.ParticleTypes.CLOUD, 4, 0.2, 0.01);
            } else pd.remove("SiegeStoneguardUntil");
        }
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
    public static void shieldBlock(net.minecraftforge.event.entity.living.ShieldBlockEvent event) {
        if (!(event.getEntity() instanceof Mob mob) || !(mob.level() instanceof ServerLevel level)) return;
        if (event.isCanceled() || event.getBlockedDamage() <= 0 || !mob.isAlive() || mob.isNoAi()) return;
        int r = role(mob); if (r < 0) return;
        long now = level.getGameTime();
        var tag = mob.getPersistentData();
        // Warbell (18): passive shockwave on block. Uses cooldown to avoid spam.
        if (r == 18 && ready(now, tag.getLong("SiegeHeroNext"))) {
            tag.putLong("SiegeHeroNext", now + 40);
            burst(level, mob.getX(), mob.getY() + 1, mob.getZ(), net.minecraft.core.particles.ParticleTypes.EXPLOSION, 6, 1.0, 0.05);
            for (var enemy : enemies(level, mob, 4)) {
                enemy.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 30, 2));
                enemy.knockback(0.8, mob.getX() - enemy.getX(), mob.getZ() - enemy.getZ());
            }
            level.playSound(null, mob.blockPosition(), net.minecraft.sounds.SoundEvents.ANVIL_LAND, net.minecraft.sounds.SoundSource.HOSTILE, 0.5F, 1.6F);
            return;
        }
        // Stonehand (11): on block, allies within 4 blocks get 25% damage reduction for 3s.
        if (r == 11 && ready(now, tag.getLong("SiegeHeroNext"))) {
            tag.putLong("SiegeHeroNext", now + 40);
            for (var a : allies(level, mob, 4)) a.getPersistentData().putLong("SiegeStoneguardUntil", now + 60);
            burst(level, mob.getX(), mob.getY() + 1, mob.getZ(), net.minecraft.core.particles.ParticleTypes.CLOUD, 10, 0.6, 0.02);
            level.playSound(null, mob.blockPosition(), net.minecraft.sounds.SoundEvents.SHIELD_BLOCK, net.minecraft.sounds.SoundSource.HOSTILE, 0.8F, 1.1F);
        }
    }
}
