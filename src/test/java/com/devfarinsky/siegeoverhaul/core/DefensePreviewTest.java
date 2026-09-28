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
        DefensePreview.set(stack, origin, Direction.WEST, Level.OVERWORLD.location(), owner, 100, null);
        return stack;
    }
    @Test void survivesInventorySyncWithExactAnchorAndRotation() {
        var stack = ItemStack.of(preview().save(new net.minecraft.nbt.CompoundTag()));
        var selection = DefensePreview.read(stack, Level.OVERWORLD.location(), owner, 110);
        assertNotNull(selection); assertEquals(origin, selection.origin()); assertEquals(Direction.WEST, selection.facing());
        assertTrue(selection.ready());
    }
    @Test void anotherOwnerOrDimensionCannotReuseTheConfirmation() {
        assertNull(DefensePreview.read(preview(), Level.OVERWORLD.location(), UUID.randomUUID(), 110));
        assertNull(DefensePreview.read(preview(), Level.NETHER.location(), owner, 110));
    }
    @Test void expiredOrFutureDatedSelectionsCannotConfirm() {
        assertNull(DefensePreview.read(preview(), Level.OVERWORLD.location(), owner, 99));
        assertNull(DefensePreview.read(preview(), Level.OVERWORLD.location(), owner, 2501));
    }
    @Test void confirmationRequiresTheSameAnchorAndASeparateDeliberateUse() {
        var selection = DefensePreview.read(preview(), Level.OVERWORLD.location(), owner, 105);
        assertFalse(selection.canConfirm(origin, 105));
        assertFalse(selection.canConfirm(origin.east(), 120));
        assertTrue(selection.canConfirm(origin, 110));
    }
    @Test void rotationResetsConfirmationDelayAndCancellationPreservesOtherItemData() {
        var stack = preview(); stack.getOrCreateTag().putString("OtherMod", "kept");
        DefensePreview.set(stack, origin, Direction.NORTH, Level.OVERWORLD.location(), owner, 120, "Blocked");
        var selection = DefensePreview.read(stack, Level.OVERWORLD.location(), owner, 120);
        assertFalse(selection.canConfirm(origin, 120)); assertFalse(selection.ready());
        DefensePreview.clear(stack);
        assertNull(DefensePreview.read(stack, Level.OVERWORLD.location(), owner, 140));
        assertEquals("kept", stack.getTag().getString("OtherMod"));
    }
    @Test void readinessRefreshDoesNotMoveOrExtendTheSelection() {
        var stack = preview(); DefensePreview.updateProblem(stack, "Need Treasury emeralds");
        var selection = DefensePreview.read(stack, Level.OVERWORLD.location(), owner, 110);
        assertEquals(100, selection.created()); assertEquals(origin, selection.origin()); assertFalse(selection.ready());
        DefensePreview.updateProblem(stack, null);
        assertTrue(DefensePreview.read(stack, Level.OVERWORLD.location(), owner, 110).ready());
    }
    @Test void corruptFacingFailsClosed() {
        var stack = preview(); stack.getTag().getCompound(DefensePreview.TAG).putInt("Facing", 10);
        assertNull(DefensePreview.read(stack, Level.OVERWORLD.location(), owner, 110));
        assertThrows(IllegalArgumentException.class, () -> DefensePreview.set(stack, origin, Direction.UP,
                Level.OVERWORLD.location(), owner, 100, null));
    }
}
