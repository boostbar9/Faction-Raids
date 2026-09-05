package com.devfarinsky.factionraids;

import com.devfarinsky.factionraids.items.ModItems;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@Mod(FactionRaids.MOD_ID)
public final class FactionRaids {
    public static final String MOD_ID = "factionraids";

    public FactionRaids() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, RaidConfig.SPEC);
        ModItems.register(modBus);
        RaidNetwork.init();
        MinecraftForge.EVENT_BUS.register(RaidEvents.class);
    }
}
