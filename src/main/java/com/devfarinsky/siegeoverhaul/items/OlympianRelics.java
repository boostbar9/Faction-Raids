package com.devfarinsky.siegeoverhaul.items;

import net.minecraft.world.item.Item;

/** Lazy registry resolution; loot generation runs after Forge's item registration. */
final class OlympianRelics {
    enum Kind { FORGE, WATCH, CLEANSE }
    private OlympianRelics() {}
    static Item item(Kind kind) {
        return switch (kind) {
            case FORGE -> ModItems.FORGE_EMBER.get();
            case WATCH -> ModItems.OWL_SEAL.get();
            case CLEANSE -> ModItems.SUN_LAUREL.get();
        };
    }
}
