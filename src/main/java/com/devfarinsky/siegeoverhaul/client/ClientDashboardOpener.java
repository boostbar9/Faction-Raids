package com.devfarinsky.siegeoverhaul.client;

import com.devfarinsky.siegeoverhaul.RaidEvents;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class ClientDashboardOpener {
    public static void open(RaidEvents.DashboardSnapshot snapshot) {
        if (Minecraft.getInstance().screen instanceof SiegeCommandScreen current) {
            current.updateSnapshot(snapshot);
        } else Minecraft.getInstance().setScreen(new SiegeCommandScreen(snapshot));
    }

    private ClientDashboardOpener() {}
}
