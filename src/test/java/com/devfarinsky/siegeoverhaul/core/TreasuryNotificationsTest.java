package com.devfarinsky.siegeoverhaul.core;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;

class TreasuryNotificationsTest {
    @Test void depositsHaveSignedGreenAmountAndGoldTreasury() {
        verify(25, "+25 emeralds deposited to Treasury", ChatFormatting.GREEN);
    }

    @Test void withdrawalsAndSpendingHaveSignedRedAmountAndGoldTreasury() {
        verify(-400, "-400 emeralds removed from Treasury", ChatFormatting.RED);
    }

    private void verify(long delta, String expected, ChatFormatting color) {
        Component message = TreasuryNotifications.message(delta);
        assertEquals(expected, message.getString());
        var colors = new ArrayList<TextColor>();
        message.visit((style, text) -> {
            if (!text.isEmpty()) colors.add(style.getColor());
            return Optional.empty();
        }, net.minecraft.network.chat.Style.EMPTY);
        assertEquals(TextColor.fromLegacyFormat(color), colors.get(0));
        assertEquals(TextColor.fromLegacyFormat(ChatFormatting.GOLD), colors.get(colors.size() - 1));
    }
}
