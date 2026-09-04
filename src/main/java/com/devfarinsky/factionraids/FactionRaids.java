package com.devfarinsky.factionraids;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;

@Mod(FactionRaids.MOD_ID)
public final class FactionRaids {
    public static final String MOD_ID = "factionraids";

    public FactionRaids() {
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, RaidConfig.SPEC);
        MinecraftForge.EVENT_BUS.register(RaidEvents.class);
    }
}
