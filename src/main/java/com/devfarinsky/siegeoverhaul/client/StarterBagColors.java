package com.devfarinsky.siegeoverhaul.client;
import com.devfarinsky.siegeoverhaul.SiegeOverhaul;
import com.devfarinsky.siegeoverhaul.items.ModItems;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterColorHandlersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
@Mod.EventBusSubscriber(modid=SiegeOverhaul.MOD_ID,bus=Mod.EventBusSubscriber.Bus.MOD,value=Dist.CLIENT)
public final class StarterBagColors {
    @SubscribeEvent public static void colors(RegisterColorHandlersEvent.Item event) {
        event.register((stack,tint)->0x69C4D1,ModItems.SETTLEMENT_BAG.get());
        event.register((stack,tint)->0xE8BF72,ModItems.SURVIVAL_BAG.get());
    }
}
