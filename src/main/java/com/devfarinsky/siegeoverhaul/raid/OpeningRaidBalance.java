package com.devfarinsky.siegeoverhaul.raid;

import com.devfarinsky.siegeoverhaul.ModConstants;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

import java.util.UUID;

/** An opening ramp keyed to the absolute wave, never the repeating chapter. */
public final class OpeningRaidBalance {
    private static final UUID HEALTH_MODIFIER = UUID.fromString("29a11d79-5a22-44e5-a48b-64d7a148b13b");
    private static final String APPLIED = "SiegeOpeningBalanceApplied";
    private static final String DAMAGE = "SiegeOpeningDamageScale";

    private OpeningRaidBalance() {}

    public static boolean heroesAllowed(int wave, boolean enabled) {
        return !enabled || wave >= 4;
    }

    /** Applied after role, hero and configured stat bonuses; persisted across reloads. */
    public static void apply(Mob mob, int wave, boolean enabled) {
        if (!enabled || wave < 1 || wave >= 5 || mob.getPersistentData().getBoolean(APPLIED)) return;
        double healthScale = switch (wave) {
            case 1 -> .60;
            case 2 -> .70;
            case 3 -> .80;
            default -> .90;
        };
        double damageScale = switch (wave) {
            case 1 -> .60;
            case 2 -> .70;
            case 3 -> .80;
            default -> .90;
        };
        var health = mob.getAttribute(Attributes.MAX_HEALTH);
        if (health != null && health.getModifier(HEALTH_MODIFIER) == null) {
            health.addPermanentModifier(new AttributeModifier(HEALTH_MODIFIER, "Opening siege health",
                    healthScale - 1.0, AttributeModifier.Operation.MULTIPLY_TOTAL));
            mob.setHealth(mob.getMaxHealth());
        }
        // Scale actual damage, including arrows and weapon bonuses, instead of only
        // ATTACK_DAMAGE (which does not control every ranged unit's projectiles).
        mob.getPersistentData().putDouble(DAMAGE, damageScale);
        mob.getPersistentData().putBoolean(APPLIED, true);
    }

    public static float outgoingDamage(Entity attacker, float amount) {
        if (!(attacker instanceof Mob)
                || attacker.getPersistentData().getString(ModConstants.Tags.RAID_TEAM).isBlank()) return amount;
        double scale = attacker.getPersistentData().getDouble(DAMAGE);
        return scale > 0 && scale < 1 ? (float) (amount * scale) : amount;
    }
}
