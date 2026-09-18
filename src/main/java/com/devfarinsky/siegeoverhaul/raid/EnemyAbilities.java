package com.devfarinsky.siegeoverhaul.raid;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;

import java.util.UUID;

/**
 * v4.35.0: role-specific ability kits for raiders.
 *
 * <p>Every raider is normalized into one of a small set of tactical roles by
 * {@code RaidEvents.assignSiegeRole}. Historically only two roles (captain
 * and breacher) received distinct passive treatment, and the rest fought
 * with identical stats. That made siege combat feel homogenous no matter
 * which faction wave you drew.
 *
 * <p>This class gives every role a unique signature:
 * <ul>
 *   <li><b>Breacher</b> — durable frontliner. Extra max HP, fire resistance,
 *       and permanent Speed I (already applied by RaidEvents).</li>
 *   <li><b>Captain</b> — squad backbone. Resistance I self-buff and pulses
 *       Strength I to nearby friendly raiders (aura still lives in
 *       RaidEvents#tickCaptainAura since it existed before v4.35.0).</li>
 *   <li><b>Warcaster</b> — glass cannon caster. Higher max HP and regen
 *       kicks in below half HP so witches and evokers get a proper
 *       "last stand" without being brittle.</li>
 *   <li><b>Flanker</b> — assassin. Speed II, Jump Boost I, and a one-time
 *       Invisibility burst when first taken below 50% HP so they can peel
 *       out and reengage.</li>
 *   <li><b>Cavalry</b> — mounted trooper. Speed I on foot and heavier
 *       damage output; jump boost helps them clear low walls.</li>
 *   <li><b>Scout</b> — recon. Speed II and Night Vision so they actually
 *       act like scouts, plus damage boost so a caught scout can hit back
 *       rather than being a free kill.</li>
 *   <li><b>Marksman</b> — bow/crossbow raider. Attack damage bonus (which
 *       propagates to arrows via base damage) and Speed I to reposition.</li>
 * </ul>
 *
 * <p>Passive effects use very long durations with hidden particles so the
 * mob model doesn't get covered in swirling effect particles. Attribute
 * changes go on the base value once at spawn and never repeat. Both are
 * idempotent per raider via a persistent-data flag.
 *
 * <p>Commander abilities remain owned by {@link CommanderTraits}. Hero
 * abilities remain owned by {@code HeroTraits}. This class covers the
 * common raider roster only.
 */
public final class EnemyAbilities {
    private EnemyAbilities() {}

    private static final String APPLIED_TAG = "SiegeOverhaulAbilitiesApplied";
    private static final String LAST_STAND_TAG = "SiegeOverhaulFlankerCloakUsed";
    private static final int LONG = 20 * 60 * 60; // 60 minutes, refreshed by tick loop as needed

    /**
     * Apply the passive ability kit for a raider's normalized role. Safe to
     * call more than once — the persistent-data flag short-circuits repeat
     * applications so a save/load or reconciliation pass won't re-buff a
     * raider that already got their kit.
     *
     * @param raider the freshly spawned raider mob
     * @param role   normalized tactical role from {@code assignSiegeRole}
     */
    public static void apply(Mob raider, String role) {
        if (raider == null || role == null || role.isEmpty()) return;
        if (raider.getPersistentData().getBoolean(APPLIED_TAG)) return;
        try {
            switch (role) {
                case "breacher" -> equipBreacher(raider);
                case "warcaster" -> equipWarcaster(raider);
                case "flanker" -> equipFlanker(raider);
                case "cavalry" -> equipCavalry(raider);
                case "scout" -> equipScout(raider);
                case "marksman" -> equipMarksman(raider);
                // captain is intentionally handled by existing RaidEvents code
                // (Resistance I + tickCaptainAura). Commander + hero owned elsewhere.
                default -> { /* no-op; captain, commander, hero */ }
            }
            raider.getPersistentData().putBoolean(APPLIED_TAG, true);
        } catch (Throwable ignored) {
            // Never let an ability configuration failure abort raider spawn.
        }
    }

    private static void equipBreacher(Mob raider) {
        // Breachers are already speed-buffed by RaidEvents; here we add
        // durability so they survive long enough to actually reach a wall.
        bumpMaxHealth(raider, 1.20D);
        raider.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, LONG, 0, false, false));
    }

    private static void equipWarcaster(Mob raider) {
        // Casters are frail by vanilla defaults; witches especially. Give
        // them 40% more HP so the caster archetype survives long enough to
        // matter. Regeneration is applied lazily by tickLastStand when the
        // caster drops below half HP so it feels like a wounded-mage push.
        bumpMaxHealth(raider, 1.40D);
        raider.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, LONG, 0, false, false));
    }

    private static void equipFlanker(Mob raider) {
        // Assassins should hit fast and hard. Speed II + jump boost so they
        // can vault a fence or peel around a shield line; +25% attack so a
        // successful flanking hit actually punishes.
        raider.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, LONG, 1, false, false));
        raider.addEffect(new MobEffectInstance(MobEffects.JUMP, LONG, 0, false, false));
        bumpAttackDamage(raider, 1.25D);
    }

    private static void equipCavalry(Mob raider) {
        // Mounted raiders push a lane. Speed I on foot (dismounts happen
        // often when horses die), fall-damage-free jumps for terrain, and
        // a 15% damage bump.
        raider.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, LONG, 0, false, false));
        raider.addEffect(new MobEffectInstance(MobEffects.JUMP, LONG, 0, false, false));
        bumpAttackDamage(raider, 1.15D);
    }

    private static void equipScout(Mob raider) {
        // Scouts need to actually move like scouts. Speed II + night vision
        // so a night raid still has a functioning recon layer. Small damage
        // bump so a scout caught by a defender isn't a free kill.
        raider.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, LONG, 1, false, false));
        raider.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, LONG, 0, false, false));
        bumpAttackDamage(raider, 1.10D);
    }

    private static void equipMarksman(Mob raider) {
        // Ranged raiders. +25% attack damage flows through to bow/crossbow
        // shots via the base attribute (recruits use attack damage as their
        // ranged base too). Speed I for repositioning between volleys.
        raider.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, LONG, 0, false, false));
        bumpAttackDamage(raider, 1.25D);
    }

    /**
     * v4.35.0 tick hook. Handles per-tick abilities that can't be baked in
     * at spawn: the warcaster's low-HP regen last stand, and flanker cloak
     * hooks fired by {@link #onRoleRaiderHurt}. Called once per raid tick
     * next to {@code tickCaptainAura} in the RaidEvents loop.
     *
     * <p>Cheap: does one persistent-data read per raider and only touches
     * the ones flagged with a role we care about.
     */
    public static void tick(ServerLevel level, Iterable<UUID> raiders) {
        for (UUID id : raiders) {
            Entity e = level.getEntity(id);
            if (!(e instanceof Mob mob) || !mob.isAlive()) continue;
            String role = mob.getPersistentData().getString(
                    com.devfarinsky.siegeoverhaul.ModConstants.Tags.RAID_ROLE);
            if ("warcaster".equals(role)) tickLastStand(mob);
        }
    }

    /**
     * Regen ticks in for wounded warcasters. Cheap: only fires when the
     * caster is below half HP, and Regeneration II lasts 40 ticks so we
     * only re-apply about every second.
     */
    private static void tickLastStand(Mob caster) {
        if (caster.getHealth() > caster.getMaxHealth() * 0.5F) return;
        caster.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 40, 1, false, false));
    }

    /**
     * v4.35.0: one-shot cloak proc for flankers. When a flanker first drops
     * below 50% HP they blink out for 3 seconds so they can reposition.
     * Uses a persistent flag so the cloak fires at most once per raider.
     * Called from {@code RaidEvents.onRaiderHurt_ShoutToAllies} which
     * already has the victim mob in hand.
     */
    public static void onRaiderHurt(Mob victim) {
        if (victim == null || !victim.isAlive()) return;
        String role = victim.getPersistentData().getString(
                com.devfarinsky.siegeoverhaul.ModConstants.Tags.RAID_ROLE);
        if (!"flanker".equals(role)) return;
        if (victim.getPersistentData().getBoolean(LAST_STAND_TAG)) return;
        if (victim.getHealth() > victim.getMaxHealth() * 0.5F) return;
        victim.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, 60, 0, false, false));
        victim.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 60, 2, false, false));
        victim.getPersistentData().putBoolean(LAST_STAND_TAG, true);
    }

    private static void bumpMaxHealth(Mob mob, double multiplier) {
        AttributeInstance hp = mob.getAttribute(Attributes.MAX_HEALTH);
        if (hp == null) return;
        hp.setBaseValue(hp.getBaseValue() * multiplier);
        mob.setHealth(mob.getMaxHealth());
    }

    private static void bumpAttackDamage(Mob mob, double multiplier) {
        AttributeInstance dmg = mob.getAttribute(Attributes.ATTACK_DAMAGE);
        if (dmg == null) return;
        dmg.setBaseValue(dmg.getBaseValue() * multiplier);
    }
}
