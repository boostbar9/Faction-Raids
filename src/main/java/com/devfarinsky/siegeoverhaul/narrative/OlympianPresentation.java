package com.devfarinsky.siegeoverhaul.narrative;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import java.util.List;
import java.util.Map;

/** Shared patron cues for champions and active camp installations. No world mutation or ambient loop. */
public final class OlympianPresentation {
    private OlympianPresentation() {}
    public record Signature(SimpleParticleType particle, SoundEvent sound, List<String> installations) {
        public Signature { installations = List.copyOf(installations); }
    }
    private static final Signature GENERIC = new Signature(ParticleTypes.ENCHANT,
            SoundEvents.AMETHYST_BLOCK_CHIME, List.of("Camp Stores", "Camp Armoury", "Command Post"));
    private static final Map<String, Signature> SIGNATURES = Map.of(
            "blackbay_reavers", new Signature(ParticleTypes.SPLASH, SoundEvents.TRIDENT_RIPTIDE_3,
                    List.of("Poseidon's Spring", "Trident Arsenal", "Tide Council")),
            "hollowfang_clan", new Signature(ParticleTypes.CRIMSON_SPORE, SoundEvents.RAVAGER_ROAR,
                    List.of("Vanguard Mess", "Arsenal of Ares", "Hall of the War Drum")),
            "emberchant_zealots", new Signature(ParticleTypes.WAX_ON, SoundEvents.ANVIL_USE,
                    List.of("Hearth of Hephaestus", "Divine Forge", "Master Smith's Hall")),
            "crownfall_exiles", new Signature(ParticleTypes.ENCHANT, SoundEvents.SHIELD_BLOCK,
                    List.of("Olive Court", "Aegis Arsenal", "Strategion of Athena")),
            "wilds_marauders", new Signature(ParticleTypes.END_ROD, SoundEvents.AMETHYST_BLOCK_CHIME,
                    List.of("Hunters' Lodge", "Silver Bowyer", "Moonwatch of Artemis")));

    public static Signature forFaction(String faction) {
        return faction == null ? GENERIC : SIGNATURES.getOrDefault(faction, GENERIC);
    }
}
