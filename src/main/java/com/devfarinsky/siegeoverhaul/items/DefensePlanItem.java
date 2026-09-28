package com.devfarinsky.siegeoverhaul.items;

import com.devfarinsky.siegeoverhaul.core.DefenseBlueprint;
import com.devfarinsky.siegeoverhaul.core.DefenseStructures;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
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
    @Override public InteractionResult useOn(UseOnContext context) {
        if (context.getClickedFace() != Direction.UP) return InteractionResult.FAIL;
        if (!(context.getPlayer() instanceof ServerPlayer player))
            return context.getLevel().isClientSide ? InteractionResult.SUCCESS : InteractionResult.FAIL;
        if (!DefenseStructures.commission(player, context.getClickedPos().above(), player.getDirection(), kind))
            return InteractionResult.FAIL;
        if (!player.isCreative()) context.getItemInHand().shrink(1);
        player.inventoryMenu.broadcastChanges();
        return InteractionResult.CONSUME;
    }
    @Override public void appendHoverText(ItemStack stack, Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.literal(kind.description).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.literal(kind.dimensions()).withStyle(ChatFormatting.GOLD));
        tooltip.add(Component.literal("Use on ground: near-center anchor, builds away from you.").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.literal("Requires your idle builder within 16 blocks and Workers storage.").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.literal(kind.price + " Treasury emeralds on placement + "
                + DefenseBlueprint.create(kind, BlockPos.ZERO, Direction.SOUTH).materials()).withStyle(ChatFormatting.YELLOW));
    }
}
