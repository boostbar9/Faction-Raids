package com.devfarinsky.siegeoverhaul.items;

import com.devfarinsky.siegeoverhaul.SiegeOverhaul;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * Registers a dedicated Siege Overhaul creative-mode tab.
 *
 * <p>The tab is a vanilla creative tab (not a HUD tab) so it lives inside
 * the standard Minecraft creative inventory next to every other mod's tab.
 * Contents are grouped in three visual bands:
 *
 * <ol>
 *   <li><b>Items</b>: our own registered items (guidebook, siege core,
 *       starter bags) plus the Warlord's Codex proxy.</li>
 *   <li><b>Mobs</b>: spawn eggs for every unit type used by the siege
 *       system. We do not register our own entity types (raiders are
 *       vanilla plus Recruits), so we surface the vanilla eggs and any
 *       Recruits eggs that are available at runtime. Missing eggs are
 *       skipped silently.</li>
 *   <li><b>Faction banners</b>: one pre-built banner per faction, ready
 *       to place. Uses {@link FactionBanners#itemStackFor(FactionBanners.FactionId)}
 *       so the banner carries the correct pattern data.</li>
 * </ol>
 */
public final class ModTabs {

    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, SiegeOverhaul.MOD_ID);

    public static final RegistryObject<CreativeModeTab> SIEGE_TAB = TABS.register(
            "siege_overhaul",
            () -> CreativeModeTab.builder()
                    .title(Component.literal("Siege Overhaul"))
                    // Icon: siege core (our headline block). Falls back to a
                    // guidebook if the core hasn't registered yet at icon time.
                    .icon(() -> {
                        if (ModItems.SIEGE_CORE.isPresent()) {
                            return new ItemStack(ModItems.SIEGE_CORE.get());
                        }
                        return new ItemStack(ModItems.GUIDEBOOK.get());
                    })
                    .displayItems((params, output) -> {
                        // ---- 1. Our own items ----
                        output.accept(ModItems.GUIDEBOOK.get());
                        output.accept(ModItems.SIEGE_CORE.get());
                        output.accept(ModItems.SETTLEMENT_BAG.get());
                        output.accept(ModItems.SURVIVAL_BAG.get());
                        output.accept(ModItems.CATAPULT_CREW_KIT.get());
                        output.accept(ModItems.BALLISTA_CREW_KIT.get());

                        // ---- 2. Spawn eggs for vanilla raider units ----
                        // The mob types the raid system spawns as enemy waves,
                        // for testing defense loadouts.
                        output.accept(Items.PILLAGER_SPAWN_EGG);
                        output.accept(Items.VINDICATOR_SPAWN_EGG);
                        output.accept(Items.EVOKER_SPAWN_EGG);
                        output.accept(Items.WITCH_SPAWN_EGG);
                        output.accept(Items.RAVAGER_SPAWN_EGG);

                        // ---- 3. Our own recruit + hero spawn eggs ----
                        // Each egg spawns a fully-outfitted unit in the same
                        // gear it would arrive in through a Siege Core hire.
                        // Recruits (0-3) first, then heroes ordered by rarity.
                        for (RegistryObject<net.minecraft.world.item.Item> egg : ModItems.UNIT_EGGS) {
                            if (egg.isPresent()) output.accept(egg.get());
                        }

                        // ---- 4. Faction banner loadout ----
                        // One pre-built banner per faction, in canon order.
                        for (FactionBanners.FactionId faction : FactionBanners.FactionId.values()) {
                            ItemStack banner = FactionBanners.itemStackFor(faction);
                            if (banner != null && !banner.isEmpty()) {
                                output.accept(banner);
                            }
                        }
                    })
                    .build());

    private ModTabs() {}

    public static void register(IEventBus modBus) {
        TABS.register(modBus);
    }

    /**
     * Look up a Recruits mod entity by short id and, if it exists, add its
     * spawn egg (or a fallback egg) to the tab. Silently no-ops when
     * Recruits is not installed.
     */
    private static void acceptRecruitEgg(CreativeModeTab.Output output, String id) {
        try {
            ResourceLocation eggId = new ResourceLocation("recruits", id + "_spawn_egg");
            if (ForgeRegistries.ITEMS.containsKey(eggId)) {
                var egg = ForgeRegistries.ITEMS.getValue(eggId);
                if (egg != null) {
                    output.accept(new ItemStack(egg));
                    return;
                }
            }
            // No dedicated egg registered; skip. We deliberately do not
            // manufacture a fallback egg here because vanilla spawn eggs
            // spawn vanilla entities, not Recruits ones.
        } catch (Exception ignored) {
            // Registry lookup failures are non-fatal for a creative tab.
        }
    }

    /**
     * Runtime probe for whether the tab's Recruits section will have any
     * entries. Kept for future use by /siegeoverhaul diagnostics; not called
     * from the tab build path itself.
     */
    @SuppressWarnings("unused")
    public static boolean hasRecruitsEggs() {
        String[] ids = {"recruit_shieldman", "bowman", "crossbowman", "assassin",
                "siege_engineer", "patrol_leader", "captain"};
        for (String id : ids) {
            if (ForgeRegistries.ITEMS.containsKey(new ResourceLocation("recruits", id + "_spawn_egg"))) {
                return true;
            }
        }
        return false;
    }

    /** For static reference from callers that need the entity-type namespace. */
    @SuppressWarnings("unused")
    public static EntityType<?> recruitEntityType(String id) {
        try {
            var rl = new ResourceLocation("recruits", id);
            if (ForgeRegistries.ENTITY_TYPES.containsKey(rl)) {
                return ForgeRegistries.ENTITY_TYPES.getValue(rl);
            }
        } catch (Exception ignored) {}
        return null;
    }
}
