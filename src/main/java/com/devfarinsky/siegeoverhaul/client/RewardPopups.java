package com.devfarinsky.siegeoverhaul.client;

import com.devfarinsky.siegeoverhaul.RaidNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * The "+N emeralds" popup beside the crosshair for kills, streaks, jackpots and camp spoils.
 * Rewards arriving while it is still up add to the shown total and restart its timer.
 */
public final class RewardPopups {
    static final long SHOW_MILLIS = 2600L, POP_MILLIS = 180L;
    private static final ItemStack EMERALD = new ItemStack(Items.EMERALD);
    private static int kind, total, streak, multiplier = 1;
    private static long shownAt = -SHOW_MILLIS;
    private RewardPopups() {}

    public static void accept(RaidNetwork.RewardPopup popup) {
        long now = System.currentTimeMillis();
        boolean live = now - shownAt < SHOW_MILLIS;
        int previousMultiplier = live ? multiplier : 1;
        total = live && popup.kind() != RaidNetwork.RewardPopup.SPOILS && kind != RaidNetwork.RewardPopup.SPOILS
                ? total + popup.emeralds() : popup.emeralds();
        // A jackpot keeps its banner for the rest of the popup even if ordinary kills follow it.
        kind = live && kind == RaidNetwork.RewardPopup.JACKPOT && popup.kind() == RaidNetwork.RewardPopup.KILL ? kind : popup.kind();
        streak = popup.streak();
        multiplier = popup.multiplier();
        shownAt = now;
        var sounds = Minecraft.getInstance().getSoundManager();
        if (popup.kind() == RaidNetwork.RewardPopup.KILL || popup.kind() == RaidNetwork.RewardPopup.JACKPOT) {
            sounds.play(SimpleSoundInstance.forUI(SoundEvents.EXPERIENCE_ORB_PICKUP, 0.85F + 0.15F * (multiplier - 1), 0.6F));
            if (multiplier > previousMultiplier)
                sounds.play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_PLING.value(), 0.9F + 0.2F * multiplier, 0.8F));
        } else {
            sounds.play(SimpleSoundInstance.forUI(SoundEvents.PLAYER_LEVELUP, 1.3F, 0.5F));
        }
    }

    /** Opacity for an age in milliseconds: full until the last half second, then fading out. */
    static float alpha(long age) {
        if (age < 0 || age >= SHOW_MILLIS) return 0F;
        return Mth.clamp((SHOW_MILLIS - age) / 500F, 0F, 1F);
    }

    /** A short overshoot when a reward lands, settling back to normal size. */
    static float pop(long age) {
        if (age < 0 || age >= POP_MILLIS) return 1F;
        return 1F + 0.35F * (1F - age / (float) POP_MILLIS);
    }

    public static void draw(GuiGraphics graphics) {
        Minecraft mc = Minecraft.getInstance();
        long age = System.currentTimeMillis() - shownAt;
        float alpha = alpha(age);
        if (alpha <= 0F || HudVisibility.hidden(mc)) return;
        int a = Math.max(4, (int) (alpha * 255)) << 24;
        String headline = switch (kind) {
            case RaidNetwork.RewardPopup.JACKPOT -> "JACKPOT +" + total;
            case RaidNetwork.RewardPopup.SPOILS -> "SPOILS +" + total;
            default -> "+" + total;
        };
        int color = kind == RaidNetwork.RewardPopup.JACKPOT ? 0xFFD54A : kind == RaidNetwork.RewardPopup.SPOILS ? 0x6FE3FF : 0x5CF07A;
        int x = mc.getWindow().getGuiScaledWidth() / 2 + 14, y = mc.getWindow().getGuiScaledHeight() / 2 - 22;
        float scale = pop(age) * (kind == RaidNetwork.RewardPopup.JACKPOT ? 1.4F : 1.15F);
        var pose = graphics.pose();
        pose.pushPose();
        pose.translate(x, y, 0);
        pose.scale(scale, scale, 1F);
        if (alpha > 0.35F) graphics.renderItem(EMERALD, 0, -4);
        graphics.drawString(mc.font, headline, 18, 0, a | color, true);
        pose.popPose();
        if (kind != RaidNetwork.RewardPopup.SPOILS && streak >= 2) {
            String line = (multiplier > 1 ? "STREAK x" + multiplier + "  " : "STREAK  ") + streak + " kills";
            graphics.drawString(mc.font, line, x + 18, y + Math.round(14 * scale), a | 0xFFB347, true);
        } else if (kind == RaidNetwork.RewardPopup.SPOILS) {
            graphics.drawString(mc.font, "Enemy camp looted", x + 18, y + Math.round(14 * scale), a | 0xBFEFFF, true);
        }
    }
}
