package com.devfarinsky.siegeoverhaul.items;

import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;

/** Colour, particle and sound identity for each Olympian patron's magic. Common code: no client classes. */
public enum MagicTheme {
    ZEUS(0xFFF6A0, 0x7FD7FF, ParticleTypes.ELECTRIC_SPARK, ParticleTypes.END_ROD,
            SoundEvents.AMETHYST_BLOCK_RESONATE, SoundEvents.TRIDENT_THUNDER),
    POSEIDON(0x2EC4FF, 0x0A4F8F, ParticleTypes.BUBBLE_POP, ParticleTypes.NAUTILUS,
            SoundEvents.CONDUIT_ACTIVATE, SoundEvents.TRIDENT_RIPTIDE_1),
    ARES(0xC0182B, 0x3A0A0A, ParticleTypes.CRIMSON_SPORE, ParticleTypes.CRIT,
            SoundEvents.PLAYER_ATTACK_STRONG, SoundEvents.BLAZE_HURT),
    ATHENA(0xE8E8F0, 0x8FA3C8, ParticleTypes.ENCHANT, ParticleTypes.GLOW,
            SoundEvents.BEACON_POWER_SELECT, SoundEvents.AMETHYST_BLOCK_CHIME),
    ARTEMIS(0xD9E4FF, 0x9B7BFF, ParticleTypes.END_ROD, ParticleTypes.GLOW,
            SoundEvents.AMETHYST_CLUSTER_BREAK, SoundEvents.AMETHYST_BLOCK_CHIME),
    APOLLO(0xFFD54A, 0xFF7A1A, ParticleTypes.SMALL_FLAME, ParticleTypes.END_ROD,
            SoundEvents.BLAZE_SHOOT, SoundEvents.BEACON_ACTIVATE),
    HEPHAESTUS(0xFF8A00, 0x5A1E00, ParticleTypes.FLAME, ParticleTypes.LAVA,
            SoundEvents.FIRECHARGE_USE, SoundEvents.ANVIL_LAND),
    DEMETER(0x8BD450, 0x3F7A1E, ParticleTypes.HAPPY_VILLAGER, ParticleTypes.SPORE_BLOSSOM_AIR,
            SoundEvents.BONE_MEAL_USE, SoundEvents.AZALEA_LEAVES_PLACE),
    FROST(0xE0F7FF, 0x6FB7FF, ParticleTypes.SNOWFLAKE, ParticleTypes.END_ROD,
            SoundEvents.POWDER_SNOW_STEP, SoundEvents.GLASS_BREAK);

    /** RGB colours the effect dust fades between. */
    public final int from, to;
    public final ParticleOptions core, sparkle;
    public final SoundEvent cast, impact;

    MagicTheme(int from, int to, ParticleOptions core, ParticleOptions sparkle, SoundEvent cast, SoundEvent impact) {
        this.from = from; this.to = to; this.core = core; this.sparkle = sparkle; this.cast = cast; this.impact = impact;
    }

    /** Theme of an Olympian-skinned weapon, or null for ordinary items. */
    public static MagicTheme of(ItemStack stack) {
        return switch (OlympianWeaponSkins.patron(stack)) {
            case "zeus" -> ZEUS; case "poseidon" -> POSEIDON; case "ares" -> ARES; case "athena" -> ATHENA;
            case "artemis" -> ARTEMIS; case "apollo" -> APOLLO; case "hephaestus" -> HEPHAESTUS; case "demeter" -> DEMETER;
            default -> null;
        };
    }

    /** Theme of a spell or hero cast role (22 lightning, 27 fire, 29 water, 30 ice). */
    public static MagicTheme ofRole(int role) {
        return switch (role) { case 22 -> ZEUS; case 29 -> POSEIDON; case 30 -> FROST; default -> HEPHAESTUS; };
    }

    public static MagicTheme byId(int id) {
        var values = values();
        return id >= 0 && id < values.length ? values[id] : null;
    }
}
