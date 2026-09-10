package com.devfarinsky.siegeoverhaul.client;

import com.devfarinsky.siegeoverhaul.RaidConfig;
import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.fml.ModLoadingContext;

/**
 * v4.18.0 client-only bridge that registers the mod's config screen with
 * Forge. Isolated in its own class so the entrypoint can guard construction
 * with DistExecutor without loading client-only symbols on a dedicated
 * server. Called from {@code SiegeOverhaul} via a method reference.
 */
public final class ConfigScreenBridge {
    private ConfigScreenBridge() {}

    public static void register() {
        ModLoadingContext.get().registerExtensionPoint(
                ConfigScreenHandler.ConfigScreenFactory.class,
                () -> new ConfigScreenHandler.ConfigScreenFactory(
                        (mc, parent) -> new SiegeOverhaulConfigScreen(parent, RaidConfig.SPEC)));
    }
}
