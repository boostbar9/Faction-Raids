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

    // v4.34.0: dedicated spawn eggs for every hireable combat unit.
    // Four recruit tiers (role 0-3) and twenty heroes (role 10-29). Each
    // egg spawns a native Recruits entity and applies our own personality
    // or hero-prep so the unit shows up in the same gear it would if hired
    // through a Siege Core. Names below follow CoreHiring.NAMES exactly so
    // adding/renaming heroes there is the only place that needs a change.
    public static final RegistryObject<Item> EGG_RECRUIT       = ITEMS.register("spawn_egg_recruit",       () -> new UnitSpawnEggItem(0,  Rarity.COMMON));
    public static final RegistryObject<Item> EGG_SHIELDMAN     = ITEMS.register("spawn_egg_shieldman",     () -> new UnitSpawnEggItem(1,  Rarity.COMMON));
    public static final RegistryObject<Item> EGG_BOWMAN        = ITEMS.register("spawn_egg_bowman",        () -> new UnitSpawnEggItem(2,  Rarity.COMMON));
    public static final RegistryObject<Item> EGG_CROSSBOWMAN   = ITEMS.register("spawn_egg_crossbowman",   () -> new UnitSpawnEggItem(3,  Rarity.COMMON));

    // Heroes 10-11 (Common)
    public static final RegistryObject<Item> EGG_GARRICK       = ITEMS.register("spawn_egg_garrick",       () -> new UnitSpawnEggItem(10, Rarity.COMMON));
    public static final RegistryObject<Item> EGG_MIRA          = ITEMS.register("spawn_egg_mira",          () -> new UnitSpawnEggItem(11, Rarity.COMMON));
    // Heroes 12-15 (Uncommon)
    public static final RegistryObject<Item> EGG_SYLVA         = ITEMS.register("spawn_egg_sylva",         () -> new UnitSpawnEggItem(12, Rarity.UNCOMMON));
    public static final RegistryObject<Item> EGG_ORIN          = ITEMS.register("spawn_egg_orin",          () -> new UnitSpawnEggItem(13, Rarity.UNCOMMON));
    public static final RegistryObject<Item> EGG_KAEL          = ITEMS.register("spawn_egg_kael",          () -> new UnitSpawnEggItem(14, Rarity.UNCOMMON));
    public static final RegistryObject<Item> EGG_BRANNA        = ITEMS.register("spawn_egg_branna",        () -> new UnitSpawnEggItem(15, Rarity.UNCOMMON));
    // Heroes 16-21 (Rare)
    public static final RegistryObject<Item> EGG_VEX           = ITEMS.register("spawn_egg_vex",           () -> new UnitSpawnEggItem(16, Rarity.RARE));
    public static final RegistryObject<Item> EGG_NYX           = ITEMS.register("spawn_egg_nyx",           () -> new UnitSpawnEggItem(17, Rarity.RARE));
    public static final RegistryObject<Item> EGG_RORIC         = ITEMS.register("spawn_egg_roric",         () -> new UnitSpawnEggItem(18, Rarity.RARE));
    public static final RegistryObject<Item> EGG_ELOWEN        = ITEMS.register("spawn_egg_elowen",        () -> new UnitSpawnEggItem(19, Rarity.RARE));
    public static final RegistryObject<Item> EGG_THANE         = ITEMS.register("spawn_egg_thane",         () -> new UnitSpawnEggItem(20, Rarity.RARE));
    public static final RegistryObject<Item> EGG_ZARA          = ITEMS.register("spawn_egg_zara",          () -> new UnitSpawnEggItem(21, Rarity.RARE));
    // Heroes 22-26 (Epic)
    public static final RegistryObject<Item> EGG_ARCANIS       = ITEMS.register("spawn_egg_arcanis",       () -> new UnitSpawnEggItem(22, Rarity.EPIC));
    public static final RegistryObject<Item> EGG_LYRIA         = ITEMS.register("spawn_egg_lyria",         () -> new UnitSpawnEggItem(23, Rarity.EPIC));
    public static final RegistryObject<Item> EGG_PYRA          = ITEMS.register("spawn_egg_pyra",          () -> new UnitSpawnEggItem(24, Rarity.EPIC));
    public static final RegistryObject<Item> EGG_SABLE         = ITEMS.register("spawn_egg_sable",         () -> new UnitSpawnEggItem(25, Rarity.EPIC));
    public static final RegistryObject<Item> EGG_TALON         = ITEMS.register("spawn_egg_talon",         () -> new UnitSpawnEggItem(26, Rarity.EPIC));
    // Heroes 27-29 (Legendary)
    public static final RegistryObject<Item> EGG_SOLMYRA       = ITEMS.register("spawn_egg_solmyra",       () -> new UnitSpawnEggItem(27, Rarity.EPIC));
    public static final RegistryObject<Item> EGG_UMBROS        = ITEMS.register("spawn_egg_umbros",        () -> new UnitSpawnEggItem(28, Rarity.EPIC));
    public static final RegistryObject<Item> EGG_CHRONOS       = ITEMS.register("spawn_egg_chronos",       () -> new UnitSpawnEggItem(29, Rarity.EPIC));

    /** Every registered unit spawn egg in role order, for creative-tab layout. */
    public static final RegistryObject<Item>[] UNIT_EGGS = new RegistryObject[] {
        EGG_RECRUIT, EGG_SHIELDMAN, EGG_BOWMAN, EGG_CROSSBOWMAN,
        EGG_GARRICK, EGG_MIRA,
        EGG_SYLVA, EGG_ORIN, EGG_KAEL, EGG_BRANNA,
        EGG_VEX, EGG_NYX, EGG_RORIC, EGG_ELOWEN, EGG_THANE, EGG_ZARA,
        EGG_ARCANIS, EGG_LYRIA, EGG_PYRA, EGG_SABLE, EGG_TALON,
        EGG_SOLMYRA, EGG_UMBROS, EGG_CHRONOS
    };

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
