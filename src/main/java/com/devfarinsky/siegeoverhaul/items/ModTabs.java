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

/** Dedicated creative catalog for Siege Overhaul items, Olympian equipment and unit eggs. */
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
                    .displayItems((params, output) -> CreativeCatalog.entries(
                            ModItems.ITEMS.getEntries().stream().map(RegistryObject::get).toList()).forEach(output::accept))
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
