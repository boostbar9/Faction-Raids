package com.devfarinsky.siegeoverhaul.items;

import com.devfarinsky.siegeoverhaul.SiegeOverhaul;
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
 * from {@link SiegeOverhaul} at construct-time; also subscribes to the
 * creative-tab build event so the guidebook shows up under Tools & Utilities.
 */
@Mod.EventBusSubscriber(modid = SiegeOverhaul.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class ModItems {

    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, SiegeOverhaul.MOD_ID);

    public static final RegistryObject<Item> GUIDEBOOK = ITEMS.register("guidebook",
            () -> new GuidebookItem(new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON)));

    public static final RegistryObject<Item> SIEGE_CORE = ITEMS.register("siege_core", com.devfarinsky.siegeoverhaul.core.CoreBlocks.CoreItem::new);

    public static final RegistryObject<Item> SETTLEMENT_BAG=ITEMS.register("settlement_bag",()->new StarterBagItem(true));
    public static final RegistryObject<Item> SURVIVAL_BAG=ITEMS.register("survival_bag",()->new StarterBagItem(false));
    public static final RegistryObject<Item> CATAPULT_CREW_KIT = ITEMS.register(
            "catapult_crew_deployment_kit",
            () -> new CrewDeploymentItem(0,
                    new Item.Properties().stacksTo(1).rarity(Rarity.RARE)));
    public static final RegistryObject<Item> BALLISTA_CREW_KIT = ITEMS.register(
            "ballista_crew_deployment_kit",
            () -> new CrewDeploymentItem(1,
                    new Item.Properties().stacksTo(1).rarity(Rarity.RARE)));

    // v4.33.0: rarity-tiered loot boxes handed out for wave clears. Four
    // separate registrations rather than NBT-tagged variants so each tier
    // gets its own model, texture, and vanilla-rarity name color for free.
    public static final RegistryObject<Item> LOOT_BOX_COMMON = ITEMS.register(
            "loot_box_common", () -> new LootBoxItem(LootBoxItem.Tier.COMMON));
    public static final RegistryObject<Item> LOOT_BOX_UNCOMMON = ITEMS.register(
            "loot_box_uncommon", () -> new LootBoxItem(LootBoxItem.Tier.UNCOMMON));
    public static final RegistryObject<Item> LOOT_BOX_RARE = ITEMS.register(
            "loot_box_rare", () -> new LootBoxItem(LootBoxItem.Tier.RARE));
    public static final RegistryObject<Item> LOOT_BOX_EPIC = ITEMS.register(
            "loot_box_epic", () -> new LootBoxItem(LootBoxItem.Tier.EPIC));

    /** Resolves a tier to its registered item for wave-drop code paths. */
    public static RegistryObject<Item> lootBox(LootBoxItem.Tier tier) {
        return switch (tier) {
            case COMMON -> LOOT_BOX_COMMON;
            case UNCOMMON -> LOOT_BOX_UNCOMMON;
            case RARE -> LOOT_BOX_RARE;
            case EPIC -> LOOT_BOX_EPIC;
        };
    }

    private ModItems() {}

    public static void register(IEventBus modBus) {
        ITEMS.register(modBus);
    }

    @SubscribeEvent
    public static void addCreativeTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) {
            // v4.13.0: guidebook removed from the creative tab. The item is
            // still registered so existing books in old worlds continue to work,
            // but new copies come from /siegeoverhaul book only.
            event.accept(SIEGE_CORE.get());
            event.accept(SETTLEMENT_BAG.get());event.accept(SURVIVAL_BAG.get());
            event.accept(LOOT_BOX_COMMON.get());
            event.accept(LOOT_BOX_UNCOMMON.get());
            event.accept(LOOT_BOX_RARE.get());
            event.accept(LOOT_BOX_EPIC.get());
        }
    }
}
