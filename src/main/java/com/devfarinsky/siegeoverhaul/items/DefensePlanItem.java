package com.devfarinsky.siegeoverhaul.items;

import com.devfarinsky.siegeoverhaul.core.DefenseBlueprint;
import com.devfarinsky.siegeoverhaul.core.DefenseStructures;
import com.devfarinsky.siegeoverhaul.core.DefensePreview;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import java.util.List;

/** Free placement plan; the Treasury commission is paid only on a successful native handoff. */
public final class DefensePlanItem extends Item {
    private final DefenseBlueprint.Kind kind;
    public DefensePlanItem(DefenseBlueprint.Kind kind) {
        super(new Properties().stacksTo(1));
        this.kind = kind;
    }
    public DefenseBlueprint.Kind kind() { return kind; }
    @Override public InteractionResult useOn(UseOnContext context) {
        if (!(context.getPlayer() instanceof ServerPlayer player))
            return context.getLevel().isClientSide ? InteractionResult.SUCCESS : InteractionResult.FAIL;
        ItemStack stack = context.getItemInHand();
        var selection = selection(stack, player);
        if (player.isShiftKeyDown() && selection != null) {
            preview(stack, player, selection.origin(), selection.facing().getClockWise());
        } else {
            if (context.getClickedFace() != Direction.UP) {
                player.displayClientMessage(Component.literal("Use the top of a ground block to set the anchor."), true);
                return InteractionResult.FAIL;
            }
            BlockPos origin = context.getClickedPos().above();
            long now = player.level().getGameTime();
            if (selection != null && selection.canConfirm(origin, now)) {
                if (!DefenseStructures.commission(player, origin, selection.facing(), kind)) return InteractionResult.FAIL;
                DefensePreview.clear(stack);
                if (!player.isCreative()) stack.shrink(1);
            } else if (selection == null || !selection.origin().equals(origin)) {
                preview(stack, player, origin, player.getDirection());
            }
        }
        player.inventoryMenu.broadcastChanges();
        return InteractionResult.CONSUME;
    }

    @Override public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player instanceof ServerPlayer server) {
            var selection = selection(stack, server);
            if (selection != null && player.isShiftKeyDown())
                preview(stack, server, selection.origin(), selection.facing().getClockWise());
            else {
                DefensePreview.clear(stack);
                player.displayClientMessage(Component.literal("Preview cancelled. Use the plan on ground to choose a site."), true);
            }
            server.inventoryMenu.broadcastChanges();
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    private DefensePreview.Selection selection(ItemStack stack, Player player) {
        var result = DefensePreview.read(stack, player.level().dimension().location(), player.getUUID(), player.level().getGameTime());
        return result != null && player.distanceToSqr(result.origin().getX() + .5, result.origin().getY(), result.origin().getZ() + .5)
                <= DefensePreview.RANGE * DefensePreview.RANGE ? result : null;
    }
    private void preview(ItemStack stack, ServerPlayer player, BlockPos origin, Direction facing) {
        String problem = DefenseStructures.prepare(player, origin, facing, kind).problem();
        DefensePreview.set(stack, origin, facing, player.level().dimension().location(), player.getUUID(),
                player.level().getGameTime(), problem);
        player.displayClientMessage(Component.literal("Preview only: " + kind.price + "e + "
                + DefenseBlueprint.create(kind, origin, facing).materials()), true);
    }
    @Override public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
        if (!(entity instanceof ServerPlayer player) || level.getGameTime() % 20 != 0
                || (!selected && player.getOffhandItem() != stack)) return;
        var selection = selection(stack, player);
        if (selection == null) { DefensePreview.clear(stack); return; }
        DefensePreview.updateProblem(stack, DefenseStructures.prepare(player, selection.origin(), selection.facing(), kind).problem());
    }
    @Override public void appendHoverText(ItemStack stack, Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.literal(kind.description).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.literal(kind.dimensions()).withStyle(ChatFormatting.GOLD));
        tooltip.add(Component.literal("Use ground to preview; use the same anchor again to confirm.").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.literal("Sneak-use rotates. Use in air cancels. Builds away from the anchor.").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.literal("Requires your idle builder within 16 blocks and Workers storage.").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.literal(kind.price + " Treasury emeralds on placement + "
                + DefenseBlueprint.create(kind, BlockPos.ZERO, Direction.SOUTH).materials()).withStyle(ChatFormatting.YELLOW));
    }
}
