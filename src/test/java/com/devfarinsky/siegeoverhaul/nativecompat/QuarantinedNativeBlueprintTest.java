package com.devfarinsky.siegeoverhaul.nativecompat;

import com.devfarinsky.siegeoverhaul.MinecraftTestSupport;
import com.devfarinsky.siegeoverhaul.ModConstants;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class QuarantinedNativeBlueprintTest extends MinecraftTestSupport {
    private CompoundTag saved() {
        CompoundTag saved = new CompoundTag(), recipe = new CompoundTag(), unknown = new CompoundTag();
        saved.putUUID("playerUUID", UUID.randomUUID()); saved.putUUID("SiegeNativeBuilder", UUID.randomUUID());
        saved.putBoolean("SiegeNativeSealed", true); saved.putLong("SiegeNativeOrigin", 42);
        saved.putString("playerName", "Owner"); saved.putInt("width", 80); saved.putInt("height", 6); saved.putInt("depth", 80);
        unknown.putLongArray("foreign:data", new long[]{Long.MIN_VALUE, 17, Long.MAX_VALUE});
        recipe.put("unrecognizedCapability", unknown); saved.put("structureNBT", recipe);
        return saved;
    }
    @Test void safeNativeReadPreservesAuthenticatedIdentityButNeverContainsTheRejectedRecipe() {
        var saved = saved(); var rejected = new QuarantinedNativeBlueprint(saved); var safe = rejected.safeNativeInput();
        assertEquals(saved.getUUID("playerUUID"), safe.getUUID("playerUUID"));
        assertEquals(saved.getUUID("SiegeNativeBuilder"), rejected.reservedBuilder());
        assertTrue(safe.getCompound("structureNBT").isEmpty());
        assertEquals(1, safe.getInt("width")); assertFalse(safe.getBoolean("teamAccess"));
        CompoundTag output = new CompoundTag(); rejected.save(output);
        assertEquals(saved, output);
    }
    @Test void rejectedRawRecipeAndSealSurviveSaveWithoutMutableAliasesOrNormalization() {
        var saved = saved(); var original = saved.copy(); var rejected = new QuarantinedNativeBlueprint(saved);
        saved.getCompound("structureNBT").remove("unrecognizedCapability");
        CompoundTag first = new CompoundTag(); rejected.save(first); assertEquals(original, first);
        first.getCompound("structureNBT").putString("changed", "caller mutation");
        CompoundTag second = new CompoundTag(); rejected.save(second); assertEquals(original, second);
        var restored = new QuarantinedNativeBlueprint(second);
        CompoundTag third = new CompoundTag(); restored.save(third); assertEquals(original, third);
    }
    @Test void conflictingGuardOrPlayerJobReceiptCannotAuthenticateTheSavedOwner() {
        for (boolean guard : new boolean[]{false, true}) {
            var saved = saved(); var persistent = new CompoundTag();
            if (guard) {
                var receipt = new CompoundTag(); receipt.putUUID("Owner", UUID.randomUUID());
                receipt.putUUID("Builder", saved.getUUID("SiegeNativeBuilder")); persistent.put("SiegeProtectedConstructionV1", receipt);
            } else persistent.putUUID(ModConstants.Tags.PLAYER_FORTIFICATION_OWNER, UUID.randomUUID());
            saved.put("ForgeData", persistent); var rejected = new QuarantinedNativeBlueprint(saved);
            assertFalse(rejected.hasVerifiedIdentity()); assertNull(rejected.reservedBuilder());
            assertThrows(IllegalStateException.class, rejected::safeNativeInput);
            var output = new CompoundTag(); rejected.save(output);
            assertEquals(saved.getUUID("playerUUID"), output.getUUID("playerUUID"));
            assertEquals(saved.getCompound("structureNBT"), output.getCompound("structureNBT"));
            assertEquals(saved.get("ForgeData"), output.get("ForgeData"));
            assertFalse(new QuarantinedNativeBlueprint(output).hasVerifiedIdentity());
        }
    }
    @Test void malformedForgeDataSurvivesRepeatedEntitySaveOrderingWithoutAcquiringAuthority() {
        for (net.minecraft.nbt.Tag malformed : java.util.List.of(
                net.minecraft.nbt.StringTag.valueOf("unrecognized original metadata"),
                net.minecraft.nbt.IntTag.valueOf(17), new net.minecraft.nbt.ListTag())) {
            var saved = saved(); saved.put("ForgeData", malformed.copy());
            for (int reload = 0; reload < 3; reload++) {
                var rejected = new QuarantinedNativeBlueprint(saved);
                assertFalse(rejected.hasVerifiedIdentity()); assertNull(rejected.reservedBuilder());
                assertThrows(IllegalStateException.class, rejected::safeNativeInput);
                // Forge Entity.save writes current persistent data before this callback.
                var output = new CompoundTag(); var live = new CompoundTag();
                live.putString("SiegeConstructionPause", "Paused: identity requires review");
                output.put("ForgeData", live); rejected.save(output);
                assertEquals(malformed, output.get("ForgeData"));
                assertEquals(saved.getCompound("structureNBT"), output.getCompound("structureNBT"));
                saved = output;
            }
        }
    }
    @Test void verifiedIdentityKeepsLivePersistentDataIncludingCancellationChanges() {
        var saved = saved(); var persistent = new CompoundTag();
        persistent.putUUID(ModConstants.Tags.PLAYER_FORTIFICATION_OWNER, saved.getUUID("playerUUID"));
        saved.put("ForgeData", persistent); var rejected = new QuarantinedNativeBlueprint(saved);
        assertTrue(rejected.hasVerifiedIdentity());
        var output = new CompoundTag(); var live = persistent.copy(); live.putBoolean("Cancelled", true);
        output.put("ForgeData", live); rejected.save(output);
        assertEquals(live, output.getCompound("ForgeData"));
    }
    @Test void missingOwnerStillSavesOriginalEvidenceWithoutCallingNativePutUuidWithNull() {
        var saved = saved(); saved.remove("playerUUID"); var rejected = new QuarantinedNativeBlueprint(saved);
        assertFalse(rejected.hasVerifiedIdentity());
        var output = new CompoundTag(); assertDoesNotThrow(() -> rejected.save(output)); assertEquals(saved, output);
    }
}
