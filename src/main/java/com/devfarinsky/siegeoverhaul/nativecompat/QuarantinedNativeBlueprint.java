package com.devfarinsky.siegeoverhaul.nativecompat;

import com.devfarinsky.siegeoverhaul.ModConstants;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

import java.util.List;
import java.util.UUID;

/** Server-only rejected native fields. Never return its recipe to a synced accessor or native queue. */
final class QuarantinedNativeBlueprint {
    private static final String OWNER = "playerUUID", BUILDER = "SiegeNativeBuilder", GUARD = "SiegeProtectedConstructionV1";
    private static final List<String> NATIVE_FIELDS = List.of(OWNER, "playerName", "isDone", "isBeingWorkedOn",
            "width", "height", "depth", "facing", "teamStringID", "time", "teamAccess", "structureNBT",
            "freeArea", "freeAreaDone", "alwaysShowProjection", "SiegeNativeOrigin", BUILDER, "SiegeNativeSealed");
    private final CompoundTag original = new CompoundTag();
    private final UUID owner, builder;
    private final Tag rejectedIdentityForgeData;

    QuarantinedNativeBlueprint(CompoundTag saved) {
        for (String key : NATIVE_FIELDS) if (saved.contains(key)) original.put(key, saved.get(key).copy());
        boolean verified = consistentIdentity(saved);
        // Entity.load ignores malformed ForgeData, while Entity.save writes a fresh
        // live compound before addAdditionalSaveData. Preserve rejection evidence
        // so save/reload cannot turn an unauthenticated identity into a valid one.
        rejectedIdentityForgeData = !verified && saved.contains("ForgeData")
                ? saved.get("ForgeData").copy() : null;
        owner = verified ? saved.getUUID(OWNER) : null;
        builder = verified ? saved.getUUID(BUILDER) : null;
    }

    static boolean consistentIdentity(CompoundTag saved) {
        if (!saved.hasUUID(OWNER) || !saved.hasUUID(BUILDER)) return false;
        UUID owner = saved.getUUID(OWNER), builder = saved.getUUID(BUILDER);
        if (owner.equals(new UUID(0, 0)) || builder.equals(new UUID(0, 0))) return false;
        if (saved.contains("ForgeData") && !saved.contains("ForgeData", Tag.TAG_COMPOUND)) return false;
        CompoundTag persistent = saved.getCompound("ForgeData");
        if (!matchesIfPresent(persistent, ModConstants.Tags.PLAYER_FORTIFICATION_OWNER, owner)
                || !matchesIfPresent(persistent, ModConstants.Tags.PLAYER_FORTIFICATION_BUILDER, builder)) return false;
        if (persistent.contains(GUARD)) {
            if (!persistent.contains(GUARD, Tag.TAG_COMPOUND)) return false;
            CompoundTag receipt = persistent.getCompound(GUARD);
            if (!receipt.hasUUID("Owner") || !receipt.hasUUID("Builder")
                    || !owner.equals(receipt.getUUID("Owner")) || !builder.equals(receipt.getUUID("Builder"))) return false;
        }
        return true;
    }

    private static boolean matchesIfPresent(CompoundTag tag, String key, UUID expected) {
        return !tag.contains(key) || tag.hasUUID(key) && expected.equals(tag.getUUID(key));
    }

    boolean hasVerifiedIdentity() { return owner != null && builder != null; }
    UUID reservedBuilder() { return builder; }

    /** Safe presentation only. The original saved recipe and fields remain untouched in original. */
    CompoundTag safeNativeInput() {
        if (!hasVerifiedIdentity()) throw new IllegalStateException("Conflicting rejected-area identity");
        CompoundTag safe = new CompoundTag();
        safe.putUUID(OWNER, owner);
        String name = original.getString("playerName");
        safe.putString("playerName", name.length() <= 64 ? name : "");
        safe.putInt("width", 1); safe.putInt("height", 1); safe.putInt("depth", 1);
        safe.putInt("facing", Direction.SOUTH.get3DDataValue());
        safe.putBoolean("teamAccess", false); safe.put("structureNBT", new CompoundTag());
        return safe;
    }

    /** Bypass native save's unconditional putUUID when a malformed identity cannot be authenticated. */
    void save(CompoundTag target) {
        if (rejectedIdentityForgeData != null) target.put("ForgeData", rejectedIdentityForgeData.copy());
        for (String key : NATIVE_FIELDS) {
            target.remove(key);
            if (original.contains(key)) target.put(key, original.get(key).copy());
        }
    }
}
