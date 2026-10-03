package com.devfarinsky.siegeoverhaul.client;

/** Presentation only: these ordinals are never network action IDs. */
enum BuildingSection {
    PERIMETER("Auto perimeter", "Perimeter"),
    STRUCTURES("Place structure", "Structures"),
    CONSTRUCTION("Construction", "Construction");

    final String label, compactLabel;
    BuildingSection(String label, String compactLabel) {
        this.label = label;
        this.compactLabel = compactLabel;
    }
}
