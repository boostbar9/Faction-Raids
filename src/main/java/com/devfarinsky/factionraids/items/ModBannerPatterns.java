package com.devfarinsky.factionraids.items;

import com.devfarinsky.factionraids.FactionRaids;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BannerPattern;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

/**
 * Custom banner patterns for the five raiding factions. Each pattern is a
 * white-on-transparent 16x16 mask under
 * {@code assets/factionraids/textures/entity/banner/&lt;id&gt;.png}
 * (and a matching {@code entity/shield/&lt;id&gt;.png}) which vanilla Minecraft
 * tints with the banner's dye color at runtime.
 *
 * <p>Patterns are registered to the vanilla {@code minecraft:banner_pattern}
 * registry via a {@link DeferredRegister}. Each {@code BannerPattern} carries
 * a "hashname" — the short string that appears in banner NBT — which is what
 * lets a stacked pattern serialize and render correctly.</p>
 *
 * <p>Modded patterns render in-game only if the client has Faction Raids
 * installed. Vanilla clients connecting to a modded server see a plain
 * base-color banner where the sigil would be. This is expected and
 * acceptable: without the mod installed there are no raids either.</p>
 */
public final class ModBannerPatterns {

    public static final DeferredRegister<BannerPattern> BANNER_PATTERNS =
            DeferredRegister.create(Registries.BANNER_PATTERN, FactionRaids.MOD_ID);

    // Hashnames are what appears in banner ItemStack NBT. Keep them short and
    // stable — renaming later would break any player-crafted banners.
    public static final RegistryObject<BannerPattern> BLACKBAY_WAVE =
            BANNER_PATTERNS.register("blackbay_wave",
                    () -> new BannerPattern("fr_bbw"));

    public static final RegistryObject<BannerPattern> HOLLOWFANG_TUSK =
            BANNER_PATTERNS.register("hollowfang_tusk",
                    () -> new BannerPattern("fr_hft"));

    public static final RegistryObject<BannerPattern> EMBERCHANT_FLAME =
            BANNER_PATTERNS.register("emberchant_flame",
                    () -> new BannerPattern("fr_ecf"));

    public static final RegistryObject<BannerPattern> CROWNFALL_CROWN =
            BANNER_PATTERNS.register("crownfall_crown",
                    () -> new BannerPattern("fr_cfc"));

    public static final RegistryObject<BannerPattern> WILDS_CLUBS =
            BANNER_PATTERNS.register("wilds_clubs",
                    () -> new BannerPattern("fr_wcl"));

    private ModBannerPatterns() {}

    public static void register(IEventBus modBus) {
        BANNER_PATTERNS.register(modBus);
    }
}
