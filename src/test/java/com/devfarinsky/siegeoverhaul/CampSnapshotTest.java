package com.devfarinsky.siegeoverhaul;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CampSnapshotTest {
    @Test
    void repeatedPlacementPreservesFirstBlockAndInventorySnapshot() {
        RaidSavedData.RaidState raid = new RaidSavedData.RaidState("team:test", "home", 0);
        CompoundTag original = new CompoundTag();
        original.putString("Name", "minecraft:chest");
        CompoundTag inventory = new CompoundTag();
        inventory.putString("CustomName", "Supplies");
        original.put("BlockEntity", inventory);
        raid.recordCampBlock(42L, "minecraft:oak_planks", original);
        inventory.putString("CustomName", "Mutated caller data");

        raid.recordCampBlock(42L, "minecraft:cobblestone", new CompoundTag());
        CompoundTag record = raid.campBlocks.get(42L);
        assertEquals("minecraft:cobblestone", record.getString("Placed"));
        assertEquals("minecraft:chest", record.getCompound("Original").getString("Name"));
        assertEquals("Supplies", record.getCompound("Original").getCompound("BlockEntity").getString("CustomName"));
    }

    @Test
    void originallyEmptySpaceStaysEmptyAfterRepeatedCampPlacement() {
        RaidSavedData.RaidState raid = new RaidSavedData.RaidState("team:test", "home", 0);
        raid.recordCampBlock(1L, "minecraft:oak_planks", null);
        CompoundTag replacement = new CompoundTag();
        replacement.putString("Name", "minecraft:oak_planks");
        raid.recordCampBlock(1L, "minecraft:cobblestone", replacement);
        assertTrue(raid.campBlocks.get(1L).getCompound("Original").isEmpty());
    }
}
