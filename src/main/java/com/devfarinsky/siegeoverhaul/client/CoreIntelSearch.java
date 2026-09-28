package com.devfarinsky.siegeoverhaul.client;

import java.util.Locale;

/** Local archive filtering; no network calls or hidden game state. */
final class CoreIntelSearch {
    private CoreIntelSearch() {}

    static boolean matches(String query, String... fields) {
        String normalized = query.strip().toLowerCase(Locale.ROOT);
        if (normalized.isEmpty()) return true;
        String haystack = String.join(" ", fields).toLowerCase(Locale.ROOT);
        for (String word : normalized.split("\\s+")) {
            if (!haystack.contains(word)) return false;
        }
        return true;
    }
}
