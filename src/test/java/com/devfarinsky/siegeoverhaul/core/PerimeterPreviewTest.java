package com.devfarinsky.siegeoverhaul.core;

import com.devfarinsky.siegeoverhaul.MinecraftTestSupport;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class PerimeterPreviewTest extends MinecraftTestSupport {
    private final UUID owner = UUID.randomUUID();
    private final BlockPos core = new BlockPos(8, 64, 8);
    private Map<Long, String> wall() {
        return PerimeterBlueprint.create(Set.of(new ChunkPos(0, 0)), (x,z) -> PerimeterBlueprint.Surface.ready(64),
                PerimeterBlueprint.Palette.COBBLESTONE).blocks();
    }
    private ItemStack preview() {
        ItemStack stack = new ItemStack(Items.PAPER); var cells = wall();
        PerimeterPreview.set(stack, owner, Level.OVERWORLD.location(), core, 1, 100,
                PerimeterPreview.fingerprint(cells, core, 1, "claim"), null, "352 cobblestone, 220 oak planks", cells);
        return stack;
    }
    @Test void exactWorldCellsSurviveMergedPrismsAndInventoryRoundTrip() {
        ItemStack stack = ItemStack.of(preview().save(new CompoundTag()));
        var selection = PerimeterPreview.read(stack, owner, Level.OVERWORLD.location(), 120);
        assertNotNull(selection); assertTrue(selection.ready()); assertEquals(core, selection.core());
        Set<Long> reconstructed = new HashSet<>(); int volume = 0;
        for (var box : selection.boxes()) {
            for (BlockPos pos : BlockPos.betweenClosed(box.min(), box.max())) {
                assertTrue(reconstructed.add(pos.asLong()), "Preview prisms must not overlap"); volume++;
            }
        }
        assertEquals(wall().keySet(), reconstructed); assertEquals(572, volume);
        assertTrue(selection.boxes().size() < 100, "A simple ring must not render a box per voxel");
    }
    @Test void canonicalFingerprintIsIndependentOfIterationOrderAndTracksMaterialGeometryAndIdentity() {
        Map<Long,String> cells = wall(); var reversed = new LinkedHashMap<Long,String>();
        var keys = new ArrayList<>(cells.keySet()); Collections.reverse(keys); keys.forEach(k -> reversed.put(k,cells.get(k)));
        String hash = PerimeterPreview.fingerprint(cells,core,1,"claim");
        assertEquals(hash,PerimeterPreview.fingerprint(reversed,core,1,"claim"));
        assertNotEquals(hash,PerimeterPreview.fingerprint(cells,core,2,"claim"));
        assertNotEquals(hash,PerimeterPreview.fingerprint(cells,core.east(),1,"claim"));
        assertNotEquals(hash,PerimeterPreview.fingerprint(cells,core,1,"other faction"));
        reversed.remove(keys.get(0)); assertNotEquals(hash,PerimeterPreview.fingerprint(reversed,core,1,"claim"));
    }
    @Test void ownershipDimensionAgeAndConfirmationDelayAreEnforced() {
        ItemStack stack = preview();
        assertNull(PerimeterPreview.read(stack,UUID.randomUUID(),Level.OVERWORLD.location(),110));
        assertNull(PerimeterPreview.read(stack,owner,Level.NETHER.location(),110));
        assertNull(PerimeterPreview.read(stack,owner,Level.OVERWORLD.location(),99));
        assertNull(PerimeterPreview.read(stack,owner,Level.OVERWORLD.location(),2501));
        var selection = PerimeterPreview.read(stack,owner,Level.OVERWORLD.location(),105);
        assertFalse(selection.canConfirm(105)); assertTrue(selection.canConfirm(110));
        stack.getTag().getCompound(PerimeterPreview.TAG).putLong("Created",Long.MIN_VALUE);
        assertNull(PerimeterPreview.read(stack,owner,Level.OVERWORLD.location(),110));
    }
    @Test void corruptPaletteHashBoxesAndOversizedVolumeFailClosed() {
        ItemStack stack=preview(); var tag=stack.getTag().getCompound(PerimeterPreview.TAG);
        tag.putInt("Material",3); assertNull(PerimeterPreview.read(stack,owner,Level.OVERWORLD.location(),110));
        stack=preview();tag=stack.getTag().getCompound(PerimeterPreview.TAG);tag.putString("Fingerprint","not-a-digest");
        assertNull(PerimeterPreview.read(stack,owner,Level.OVERWORLD.location(),110));
        stack=preview();tag=stack.getTag().getCompound(PerimeterPreview.TAG);tag.putLongArray("Boxes",new long[]{1,2});
        assertNull(PerimeterPreview.read(stack,owner,Level.OVERWORLD.location(),110));
        stack=preview();tag=stack.getTag().getCompound(PerimeterPreview.TAG);
        tag.putLongArray("Boxes",new long[]{BlockPos.ZERO.asLong(),new BlockPos(100,100,100).asLong(),0});
        assertNull(PerimeterPreview.read(stack,owner,Level.OVERWORLD.location(),110));
    }
    @Test void blockedPreviewAndCancellationNeverBecomeAReadySelection() {
        ItemStack stack=new ItemStack(Items.PAPER);stack.getOrCreateTag().putString("Unrelated","preserved");
        PerimeterPreview.set(stack,owner,Level.OVERWORLD.location(),core,0,100,"","Protected obstruction","",Map.of());
        var selection=PerimeterPreview.read(stack,owner,Level.OVERWORLD.location(),110);
        assertNotNull(selection);assertFalse(selection.ready());assertEquals("Protected obstruction",selection.problem());
        PerimeterPreview.clear(stack);assertNull(PerimeterPreview.read(stack,owner,Level.OVERWORLD.location(),110));
        assertEquals("preserved",stack.getTag().getString("Unrelated"));
    }
    @Test void negativeCoordinatesMixedMaterialsAndDisjointCellsAreNotFilledIn() {
        Map<Long,String> cells = Map.of(new BlockPos(-7,64,-9).asLong(),"minecraft:cobblestone",
                new BlockPos(-7,65,-9).asLong(),"minecraft:oak_planks",new BlockPos(-5,64,-9).asLong(),"minecraft:dirt");
        var boxes=PerimeterPreview.boxes(cells);assertEquals(3,boxes.size());
        for(var box:boxes)assertEquals(box.min(),box.max());
        assertEquals(Set.of(0,1,2),boxes.stream().map(PerimeterPreview.Box::material).collect(java.util.stream.Collectors.toSet()));
    }
}
