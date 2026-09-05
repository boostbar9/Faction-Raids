package com.devfarinsky.factionraids.items;

import com.devfarinsky.factionraids.FactionRaids;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * All Faction Raids items live here. Registered against the mod event bus
 * from {@link FactionRaids} at construct-time; also subscribes to the
 * creative-tab build event so the guidebook shows up under Tools & Utilities.
 */
@Mod.EventBusSubscriber(modid = FactionRaids.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class ModItems {

    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, FactionRaids.MOD_ID);

    public static final RegistryObject<Item> GUIDEBOOK = ITEMS.register("guidebook",
            () -> new GuidebookItem(new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON)));

    private ModItems() {}

    public static void register(IEventBus modBus) {
        ITEMS.register(modBus);
    }

    @SubscribeEvent
    public static void addCreativeTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) {
            event.accept(GUIDEBOOK.get());
        }
    }
}
