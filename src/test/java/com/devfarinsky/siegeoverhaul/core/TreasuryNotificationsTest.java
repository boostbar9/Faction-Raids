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
        verify(25, "[Treasury] +25 emeralds deposited", ChatFormatting.GREEN);
    }

    @Test void withdrawalsAndSpendingHaveSignedRedAmountAndGoldTreasury() {
        verify(-400, "[Treasury] -400 emeralds removed", ChatFormatting.RED);
    }

    @Test void largeAmountsUseGroupingAndSingleEmeraldUsesSingular() {
        verify(1250, "[Treasury] +1,250 emeralds deposited", ChatFormatting.GREEN);
        verify(-1250, "[Treasury] -1,250 emeralds removed", ChatFormatting.RED);
        verify(1, "[Treasury] +1 emerald deposited", ChatFormatting.GREEN);
        verify(-1, "[Treasury] -1 emerald removed", ChatFormatting.RED);
    }

    private void verify(long delta, String expected, ChatFormatting color) {
        Component message = TreasuryNotifications.message(delta);
        assertEquals(expected, message.getString());
        var colors = new ArrayList<TextColor>();
        message.visit((style, text) -> {
            if (!text.isEmpty()) colors.add(style.getColor());
            return Optional.empty();
        }, net.minecraft.network.chat.Style.EMPTY);
        assertEquals(TextColor.fromLegacyFormat(ChatFormatting.GOLD), colors.get(0));
        assertEquals(TextColor.fromLegacyFormat(color), colors.get(colors.size() - 1));
    }
}
