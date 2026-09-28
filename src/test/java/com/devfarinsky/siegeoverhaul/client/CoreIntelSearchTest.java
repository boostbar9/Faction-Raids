package com.devfarinsky.siegeoverhaul.client;

import org.junit.jupiter.api.Test;
import java.util.Locale;
import static org.junit.jupiter.api.Assertions.*;

class CoreIntelSearchTest {
    @Test void findsWordsAcrossTitleAndInstructionsIgnoringCaseAndWhitespace() {
        assertTrue(CoreIntelSearch.matches("  BUILDER \t supplies ", "Hire a Builder", "Bring supplies"));
        assertFalse(CoreIntelSearch.matches("builder supplies", "Hire a Builder", "No materials"));
    }
    @Test void clearingTheFilterRestoresEveryEntry() {
        assertTrue(CoreIntelSearch.matches("", "Watchtower"));
        assertTrue(CoreIntelSearch.matches(" \n ", ""));
    }
    @Test void filteringDoesNotDependOnThePlayersLocale() {
        Locale original = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            assertTrue(CoreIntelSearch.matches("INTEL", "Intel archive"));
        } finally { Locale.setDefault(original); }
    }
}
