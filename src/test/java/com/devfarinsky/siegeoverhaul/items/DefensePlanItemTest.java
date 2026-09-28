package com.devfarinsky.siegeoverhaul.items;

import com.devfarinsky.siegeoverhaul.MinecraftTestSupport;
import com.devfarinsky.siegeoverhaul.core.DefenseBlueprint;
import com.devfarinsky.siegeoverhaul.core.DefenseStructures;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DefensePlanItemTest extends MinecraftTestSupport {
    @Test void failedPlacementKeepsThePlan() throws Exception { placement(false, false, 1); }
    @Test void successfulPlacementConsumesExactlyOnePlan() throws Exception { placement(true, false, 0); }
    @Test void creativePlacementKeepsThePlan() throws Exception { placement(true, true, 1); }

    private void placement(boolean success, boolean creative, int remaining) throws Exception {
        var item = mock(DefensePlanItem.class, CALLS_REAL_METHODS);
        var field = DefensePlanItem.class.getDeclaredField("kind"); field.setAccessible(true);
        field.set(item, DefenseBlueprint.Kind.WATCHTOWER);
        var context = mock(UseOnContext.class); var player = mock(ServerPlayer.class);
        var inventory = Player.class.getDeclaredField("inventoryMenu"); inventory.setAccessible(true);
        inventory.set(player, mock(InventoryMenu.class));
        var stack = new ItemStack(Items.PAPER);
        when(context.getPlayer()).thenReturn(player); when(context.getClickedFace()).thenReturn(Direction.UP);
        when(context.getClickedPos()).thenReturn(BlockPos.ZERO); when(context.getItemInHand()).thenReturn(stack);
        when(player.getDirection()).thenReturn(Direction.EAST); when(player.isCreative()).thenReturn(creative);
        try (var structures = mockStatic(DefenseStructures.class)) {
            structures.when(() -> DefenseStructures.commission(player, BlockPos.ZERO.above(), Direction.EAST,
                    DefenseBlueprint.Kind.WATCHTOWER)).thenReturn(success);
            assertEquals(success ? InteractionResult.CONSUME : InteractionResult.FAIL, item.useOn(context));
            assertEquals(remaining, stack.getCount());
        }
    }
}
