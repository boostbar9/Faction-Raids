package com.devfarinsky.factionraids.items;

import com.devfarinsky.factionraids.RaidNetwork;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * Right-clickable guidebook that opens the Warlord's Codex \u2014 the tabbed
 * client-side siege command screen ({@code SiegeCommandScreen}) introduced
 * in v2.11.0. This item is purely a friendly entry point so players don't
 * need to type {@code /factionraids menu} to reach it.
 *
 * <p>v2.28.0: the old chest-style {@code RaidDashboardMenu} was deleted;
 * this javadoc previously referenced it in error.
 */
public class GuidebookItem extends Item {

    public GuidebookItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack held = player.getItemInHand(hand);
        if (!level.isClientSide && player instanceof ServerPlayer sp) {
            // Fire a small feedback sound so the click feels responsive even
            // on higher-latency setups where the menu takes a moment to open.
            level.playSound(null, sp.blockPosition(), SoundEvents.BOOK_PAGE_TURN,
                    SoundSource.PLAYERS, 0.9F, 1.0F);
            RaidNetwork.openDashboard(sp);
        }
        return InteractionResultHolder.sidedSuccess(held, level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.factionraids.guidebook.tooltip.line1"));
        tooltip.add(Component.translatable("item.factionraids.guidebook.tooltip.line2"));
    }
}
