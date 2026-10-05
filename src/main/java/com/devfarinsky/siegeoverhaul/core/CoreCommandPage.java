package com.devfarinsky.siegeoverhaul.core;

/** Client navigation metadata only; ordinals are never purchase or packet IDs. */
public enum CoreCommandPage {
    ARMY("Army", "Army & heroes", "Recruit defenders, workers and heroes from shared faction offers"),
    LOOT("Loot", "Loot & blessings", "Open mystery rewards or buy a five-minute personal blessing"),
    TREASURY("Treasury", "Faction Treasury", "Deposit emeralds, manage shared funds and view recent activity"),
    TERRITORY("Territory", "Territory upgrades", "Buy permanent upgrades for your faction"),
    DEFENSES("Building", "Building", "Plan a perimeter, place a structure and follow your builders"),
    CIVILIANS("Civilians", "Civilians", "Manage residents, review native care details and track collected taxes"),
    INTEL("Intel", "Intel", "Find units, enemy lore and practical defense advice");

    private final String label, title, description;
    CoreCommandPage(String label, String title, String description) {
        this.label = label;
        this.title = title;
        this.description = description;
    }
    public String label() { return label; }
    public String title() { return title; }
    public String description() { return description; }
}
