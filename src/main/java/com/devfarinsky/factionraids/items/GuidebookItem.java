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
 * Right-clickable guidebook that opens the Faction Raids dashboard for the
 * player. The dashboard is the existing 9x6 chest-style menu implemented in
 * {@link com.devfarinsky.factionraids.RaidDashboardMenu} \u2014 this item is
 * purely a client-friendly entry point so players don't need to type the
 * {@code /factionraids} command to reach it.
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
