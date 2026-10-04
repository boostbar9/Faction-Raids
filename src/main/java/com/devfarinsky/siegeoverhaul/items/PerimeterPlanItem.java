package com.devfarinsky.siegeoverhaul.items;

import com.devfarinsky.siegeoverhaul.core.PerimeterConstruction;
import com.devfarinsky.siegeoverhaul.core.PerimeterPreview;
import com.devfarinsky.siegeoverhaul.core.TerritoryFortification;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import java.util.List;

/** Free, owner-bound review item. Using it requests a fully revalidated server commission. */
public final class PerimeterPlanItem extends Item {
    public PerimeterPlanItem() { super(new Properties().stacksTo(1)); }
    @Override public InteractionResult useOn(UseOnContext context) {
        if (context.getPlayer() instanceof ServerPlayer player) act(player, context.getItemInHand());
        return InteractionResult.sidedSuccess(context.getLevel().isClientSide);
    }
    @Override public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player instanceof ServerPlayer server) act(server, stack);
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }
    private static void act(ServerPlayer player, ItemStack stack) {
        if (player.isShiftKeyDown()) {
            PerimeterPreview.clear(stack); player.inventoryMenu.broadcastChanges();
            player.displayClientMessage(Component.literal("Perimeter review cancelled. Reopen Building at your core to choose a new plan."), true);
        } else PerimeterConstruction.confirm(player, stack);
    }
    @Override public void appendHoverText(ItemStack stack, Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.literal("Hold to inspect the exact template-style perimeter.").withStyle(ChatFormatting.AQUA));
        tooltip.add(Component.literal("Use to confirm; sneak-use cancels. Expires after 2 minutes.").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.literal(TerritoryFortification.PRICE + " faction Treasury emeralds for the whole perimeter + supplied blocks.").withStyle(ChatFormatting.GOLD));
        tooltip.add(Component.literal("The server checks the whole claim, builder, storage and footprint again.").withStyle(ChatFormatting.GRAY));
    }
}
