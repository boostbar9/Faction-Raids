package com.devfarinsky.siegeoverhaul.items;

import com.devfarinsky.siegeoverhaul.MinecraftTestSupport;
import com.devfarinsky.siegeoverhaul.core.DefenseBlueprint;
import com.devfarinsky.siegeoverhaul.core.DefenseStructures;
import com.devfarinsky.siegeoverhaul.core.DefensePreview;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class DefensePlanItemTest extends MinecraftTestSupport {
    @Test void failedPlacementKeepsThePlan() throws Exception { confirmation(false, false, 1); }
    @Test void successfulPlacementConsumesExactlyOnePlan() throws Exception { confirmation(true, false, 0); }
    @Test void creativePlacementKeepsThePlan() throws Exception { confirmation(true, true, 1); }

    private void confirmation(boolean success, boolean creative, int remaining) throws Exception {
        try (Fixture f = new Fixture()) {
            f.arm(); when(f.player.isCreative()).thenReturn(creative);
            f.structures.when(() -> DefenseStructures.commission(f.player, BlockPos.ZERO.above(), Direction.EAST,
                    DefenseBlueprint.Kind.WATCHTOWER)).thenReturn(success);
            assertEquals(success ? InteractionResult.CONSUME : InteractionResult.FAIL, f.item.useOn(f.context));
            assertEquals(remaining, f.stack.getCount());
            if (success) assertNull(f.selection());
            else assertNotNull(f.selection());
        }
    }
    @Test void firstUsePreviewsWithoutCommissioningOrConsuming() throws Exception {
        try (Fixture f = new Fixture()) {
            assertEquals(InteractionResult.CONSUME, f.item.useOn(f.context));
            assertEquals(BlockPos.ZERO.above(), f.selection().origin());
            assertEquals(1, f.stack.getCount()); f.noCommission();
        }
    }
    @Test void repeatedClickDuringPreviewDelayCannotCharge() throws Exception {
        try (Fixture f = new Fixture()) {
            f.item.useOn(f.context); f.item.useOn(f.context);
            assertEquals(1, f.stack.getCount()); f.noCommission();
        }
    }
    @Test void sneakUseRotatesAroundTheOriginalAnchorWithoutCharging() throws Exception {
        try (Fixture f = new Fixture()) {
            f.arm(); when(f.player.isShiftKeyDown()).thenReturn(true);
            when(f.context.getClickedPos()).thenReturn(new BlockPos(5, 0, 0));
            f.item.useOn(f.context);
            assertEquals(Direction.SOUTH, f.selection().facing());
            assertEquals(BlockPos.ZERO.above(), f.selection().origin());
            assertEquals(20, f.selection().created()); f.noCommission();
        }
    }
    @Test void useInAirCancelsWithoutConsumingThePlan() throws Exception {
        try (Fixture f = new Fixture()) {
            f.arm(); f.item.use(f.level, f.player, InteractionHand.MAIN_HAND);
            assertNull(f.selection()); assertEquals(1, f.stack.getCount()); f.noCommission();
        }
    }
    @Test void choosingAnotherAnchorMovesPreviewWithoutCommissioning() throws Exception {
        try (Fixture f = new Fixture()) {
            f.arm(); when(f.context.getClickedPos()).thenReturn(new BlockPos(2, 0, 0));
            f.item.useOn(f.context);
            assertEquals(new BlockPos(2, 1, 0), f.selection().origin()); f.noCommission();
        }
    }
    private static class Fixture implements AutoCloseable {
        final DefensePlanItem item = mock(DefensePlanItem.class, CALLS_REAL_METHODS);
        final UseOnContext context = mock(UseOnContext.class);
        final ServerPlayer player = mock(ServerPlayer.class);
        final ServerLevel level = mock(ServerLevel.class);
        final UUID owner = UUID.randomUUID();
        final ItemStack stack = new ItemStack(Items.PAPER);
        final MockedStatic<DefenseStructures> structures = mockStatic(DefenseStructures.class);
        Fixture() throws Exception {
            var field = DefensePlanItem.class.getDeclaredField("kind"); field.setAccessible(true);
            field.set(item, DefenseBlueprint.Kind.WATCHTOWER);
            var inventory = Player.class.getDeclaredField("inventoryMenu"); inventory.setAccessible(true);
            inventory.set(player, mock(InventoryMenu.class));
            when(player.level()).thenReturn(level); when(player.serverLevel()).thenReturn(level);
            when(player.getUUID()).thenReturn(owner); when(level.dimension()).thenReturn(Level.OVERWORLD);
            when(level.getGameTime()).thenReturn(20L);
            when(context.getPlayer()).thenReturn(player); when(context.getClickedFace()).thenReturn(Direction.UP);
            when(context.getClickedPos()).thenReturn(BlockPos.ZERO); when(context.getItemInHand()).thenReturn(stack);
            when(player.getItemInHand(InteractionHand.MAIN_HAND)).thenReturn(stack);
            when(player.getDirection()).thenReturn(Direction.EAST);
            structures.when(() -> DefenseStructures.prepare(any(), any(), any(), any()))
                    .thenReturn(new DefenseStructures.Preparation(null, null, null));
        }
        void arm() { DefensePreview.set(stack, BlockPos.ZERO.above(), Direction.EAST, Level.OVERWORLD.location(), owner, 0, null); }
        DefensePreview.Selection selection() { return DefensePreview.read(stack, Level.OVERWORLD.location(), owner, 20); }
        void noCommission() { structures.verify(() -> DefenseStructures.commission(any(), any(), any(), any()), never()); }
        public void close() { structures.close(); }
    }
}
