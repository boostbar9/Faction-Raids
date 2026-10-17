package com.devfarinsky.siegeoverhaul.chat;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BossbarLabelTest {
    @Test void shortLabelsKeepEveryChipInOrder() {
        assertEquals("Ship-Wolves · Breach · stronghold 47m · 12 deployed",
                ChatStyle.bossbarLabel("Ship-Wolves", "Breach", "stronghold 47m", null, "", "12 deployed"));
    }
    @Test void longLabelsDropFlavourThenTrailingChipsButKeepThePhaseAndMainChip() {
        String label = ChatStyle.bossbarLabel("The Thrice-Crowned Host of the Wine-Dark Sea", "Assault",
                "stronghold 42% holding", "Wave 3/5", "Trident Charge", "18 deployed + 6 reinforcing");
        assertTrue(label.length() <= ChatStyle.BOSSBAR_MAX, label);
        assertTrue(label.startsWith("Assault · stronghold 42% holding"), label);
        assertFalse(label.contains("Thrice-Crowned"));
        String huge = ChatStyle.bossbarLabel(null, "Assault", "x".repeat(200));
        assertEquals(ChatStyle.BOSSBAR_MAX, huge.length());
        assertTrue(huge.endsWith("…"));
    }
}
