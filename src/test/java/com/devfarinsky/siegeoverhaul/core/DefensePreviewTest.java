package com.devfarinsky.siegeoverhaul.core;

import com.devfarinsky.siegeoverhaul.MinecraftTestSupport;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class DefensePreviewTest extends MinecraftTestSupport {
    private final UUID owner = UUID.randomUUID();
    private final BlockPos origin = new BlockPos(-15, 64, 22);
    private ItemStack preview() {
        var stack = new ItemStack(Items.PAPER);
        DefensePreview.set(stack, DefenseBlueprint.Kind.WATCHTOWER, origin, Direction.WEST, Level.OVERWORLD.location(), owner, 100, null);
        return stack;
    }
    @Test void survivesInventorySyncWithExactAnchorAndRotation() {
        var stack = ItemStack.of(preview().save(new net.minecraft.nbt.CompoundTag()));
        var selection = DefensePreview.read(stack, DefenseBlueprint.Kind.WATCHTOWER, Level.OVERWORLD.location(), owner, 110);
        assertNotNull(selection); assertEquals(origin, selection.origin()); assertEquals(Direction.WEST, selection.facing());
        assertTrue(selection.ready());
    }
    @Test void anotherOwnerOrDimensionCannotReuseTheConfirmation() {
        assertNull(DefensePreview.read(preview(), DefenseBlueprint.Kind.WATCHTOWER, Level.OVERWORLD.location(), UUID.randomUUID(), 110));
        assertNull(DefensePreview.read(preview(), DefenseBlueprint.Kind.WATCHTOWER, Level.NETHER.location(), owner, 110));
    }
    @Test void expiredOrFutureDatedSelectionsCannotConfirm() {
        assertNull(DefensePreview.read(preview(), DefenseBlueprint.Kind.WATCHTOWER, Level.OVERWORLD.location(), owner, 99));
        assertNull(DefensePreview.read(preview(), DefenseBlueprint.Kind.WATCHTOWER, Level.OVERWORLD.location(), owner, 2501));
    }
    @Test void confirmationRequiresTheSameAnchorAndASeparateDeliberateUse() {
        var selection = DefensePreview.read(preview(), DefenseBlueprint.Kind.WATCHTOWER, Level.OVERWORLD.location(), owner, 105);
        assertFalse(selection.canConfirm(origin, 105));
        assertFalse(selection.canConfirm(origin.east(), 120));
        assertTrue(selection.canConfirm(origin, 110));
    }
    @Test void rotationResetsConfirmationDelayAndCancellationPreservesOtherItemData() {
        var stack = preview(); stack.getOrCreateTag().putString("OtherMod", "kept");
        DefensePreview.set(stack, DefenseBlueprint.Kind.WATCHTOWER, origin, Direction.NORTH, Level.OVERWORLD.location(), owner, 120, "Blocked");
        var selection = DefensePreview.read(stack, DefenseBlueprint.Kind.WATCHTOWER, Level.OVERWORLD.location(), owner, 120);
        assertFalse(selection.canConfirm(origin, 120)); assertFalse(selection.ready());
        DefensePreview.clear(stack);
        assertNull(DefensePreview.read(stack, DefenseBlueprint.Kind.WATCHTOWER, Level.OVERWORLD.location(), owner, 140));
        assertEquals("kept", stack.getTag().getString("OtherMod"));
    }
    @Test void readinessRefreshDoesNotMoveOrExtendTheSelection() {
        var stack = preview(); DefensePreview.updateProblem(stack, "Need Treasury emeralds");
        var selection = DefensePreview.read(stack, DefenseBlueprint.Kind.WATCHTOWER, Level.OVERWORLD.location(), owner, 110);
        assertEquals(100, selection.created()); assertEquals(origin, selection.origin()); assertFalse(selection.ready());
        DefensePreview.updateProblem(stack, null);
        assertTrue(DefensePreview.read(stack, DefenseBlueprint.Kind.WATCHTOWER, Level.OVERWORLD.location(), owner, 110).ready());
    }
    @Test void onlyChangedWallAndCornerPreVersionPreviewsRequireFreshReview() {
        for (var kind : DefenseBlueprint.Kind.values()) {
            var stack = new ItemStack(Items.PAPER);
            DefensePreview.set(stack, kind, origin, Direction.WEST, Level.OVERWORLD.location(), owner, 100, null);
            stack.getTag().getCompound(DefensePreview.TAG).remove("Geometry"); // Actual legacy tag shape.
            var selection = DefensePreview.read(stack, kind, Level.OVERWORLD.location(), owner, 120);
            if (kind == DefenseBlueprint.Kind.WALL || kind == DefenseBlueprint.Kind.CORNER) assertNull(selection);
            else assertNotNull(selection, kind.label);
            DefensePreview.updateProblem(stack, null);
            if (kind == DefenseBlueprint.Kind.WALL || kind == DefenseBlueprint.Kind.CORNER)
                assertNull(DefensePreview.read(stack, kind, Level.OVERWORLD.location(), owner, 120));
            DefensePreview.set(stack, kind, origin, Direction.WEST, Level.OVERWORLD.location(), owner, 120, null);
            var fresh = DefensePreview.read(stack, kind, Level.OVERWORLD.location(), owner, 120);
            assertNotNull(fresh); assertFalse(fresh.canConfirm(origin, 120)); assertTrue(fresh.canConfirm(origin, 130));
        }
    }
    @Test void staleWrongKindAndMalformedGeometryIdentityFailClosed() {
        var stack = new ItemStack(Items.PAPER);
        DefensePreview.set(stack, DefenseBlueprint.Kind.WALL, origin, Direction.WEST, Level.OVERWORLD.location(), owner, 100, null);
        assertNull(DefensePreview.read(stack, DefenseBlueprint.Kind.CORNER, Level.OVERWORLD.location(), owner, 120));
        stack.getTag().getCompound(DefensePreview.TAG).putString("Geometry", "WALL:1");
        assertNull(DefensePreview.read(stack, DefenseBlueprint.Kind.WALL, Level.OVERWORLD.location(), owner, 120));
        stack.getTag().getCompound(DefensePreview.TAG).putInt("Geometry", 2);
        assertNull(DefensePreview.read(stack, DefenseBlueprint.Kind.WALL, Level.OVERWORLD.location(), owner, 120));
    }
    @Test void corruptFacingFailsClosed() {
        var stack = preview(); stack.getTag().getCompound(DefensePreview.TAG).putInt("Facing", 10);
        assertNull(DefensePreview.read(stack, DefenseBlueprint.Kind.WATCHTOWER, Level.OVERWORLD.location(), owner, 110));
        assertThrows(IllegalArgumentException.class, () -> DefensePreview.set(stack, DefenseBlueprint.Kind.WATCHTOWER, origin, Direction.UP,
                Level.OVERWORLD.location(), owner, 100, null));
    }
}
