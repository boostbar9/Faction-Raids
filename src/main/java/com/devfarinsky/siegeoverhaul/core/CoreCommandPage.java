package com.devfarinsky.siegeoverhaul.core;

/** Client navigation metadata only; ordinals are never purchase or packet IDs. */
public enum CoreCommandPage {
    ARMY("Army", "War Council", "Recruit defenders, specialists and legendary heroes"),
    LOOT("Loot", "Olympian Reliquary", "Unseal divine spoils and prepare battlefield blessings"),
    TREASURY("Treasury", "Faction Treasury", "Manage shared wealth, rewards and faction activity"),
    TERRITORY("Territory", "Kingdom Development", "Commission permanent upgrades and perimeter works"),
    DEFENSES("Defenses", "Defense Works", "Collect a plan, choose a site and commission your builder"),
    CIVILIANS("Civilians", "Your Settlement", "House your people, trade and grow the faction Treasury"),
    INTEL("Intel", "Warlord Intelligence", "Study units, enemy hosts and defensive doctrine");

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
