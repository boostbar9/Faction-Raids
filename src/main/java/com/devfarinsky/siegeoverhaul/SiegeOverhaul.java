package com.devfarinsky.siegeoverhaul;

import com.devfarinsky.siegeoverhaul.items.ModBannerPatterns;
import com.devfarinsky.siegeoverhaul.items.ModItems;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

/**
 * The Siege Overhaul (formerly Faction Raids) mod entry point.
 *
 * <p>Formerly the {@code FactionRaids} class in the
 * {@code com.devfarinsky.factionraids} package. Renamed in v3.0.0. The
 * legacy mod id {@code "factionraids"} is preserved in
 * {@link ModConstants#LEGACY_MOD_ID} and used only by the migration
 * reader to locate legacy save/config files during upgrade.
 */
@Mod(SiegeOverhaul.MOD_ID)
public final class SiegeOverhaul {

    /** New canonical mod id as of v3.0.0. */
    public static final String MOD_ID = "siegeoverhaul";

    public SiegeOverhaul() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, RaidConfig.SPEC);
        com.devfarinsky.siegeoverhaul.core.CoreBlocks.BLOCKS.register(modBus);
        com.devfarinsky.siegeoverhaul.core.CoreMenus.MENUS.register(modBus);
        ModItems.register(modBus);
        ModBannerPatterns.register(modBus);
        com.devfarinsky.siegeoverhaul.items.ModTabs.register(modBus);
        RaidNetwork.init();
        MinecraftForge.EVENT_BUS.register(RaidEvents.class);
        // v2.30.0: install the Bridge Sieges listener. Bootstrap is deferred
        // to ServerStartedEvent inside the bridge itself so Recruits'
        // class-loading is complete before we probe for SiegeEvent.Start.
        com.devfarinsky.siegeoverhaul.compat.RecruitsSiegeBridge.init();
    }
}
