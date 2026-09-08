package com.devfarinsky.siegeoverhaul.client.codex;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.List;

/**
 * Curated defense-strategy tips displayed in the Warlord's Codex.
 * Purely static; the book renders these as tip cards. The content is written
 * as concrete, actionable advice \u2014 no generic "build walls" filler.
 *
 * <p>v2.28.0 audit: the ravager, wooden-door trap, wave-rest, and
 * reward-eligibility tips were rewritten to remove claims the mod does not
 * back with code (there is no obsidian trap system, no configurable rest
 * between waves, ravagers do not break walls, and the reward-eligible
 * indicator only shows on the pre-siege forecast card).
 */
@OnlyIn(Dist.CLIENT)
public final class DefensePlaybook {

    public static final List<Tip> TIPS = List.of(
            new Tip("Stronghold", "Defend your Siege Core",
                    "Place one core in your faction's Recruits claim in the Overworld. Enemies must outnumber you and your recruits within 10 horizontal and 3 vertical blocks for 120 seconds by default. Ties pause progress. There is no perimeter timer."),
            new Tip("Recruitment", "Check the core's hire offers",
                    "Right-click the core for three shared faction hires. Each slot rolls 80% recruit, 10% shieldman and 10% archer. Stock refreshes every 15 minutes of server runtime. Native hiring prices, currency and unit limits apply."),
            new Tip("Preparation", "Watch the enemy foothold grow",
                    "By default, the enemy spends four minutes establishing camp, four building a timber palisade, and four gathering its first wave. The horn marks the assault. Builders need daytime to work; unfinished structures do not prevent an assault."),
            new Tip("Counterattack", "Disrupt the muster",
                    "The first wave gathers at camp before marching. Attack the assembling troops to weaken it. Once that wave is active, the campfire can stop its remaining reinforcements and the banner can force it to retreat."),
            new Tip("Defense", "Keep soldiers near the core",
                    "An enemy outside your wall is a threat; an enemy occupying the core is a losing siege. Keep a reserve inside the occupation ring and watch the pressure indicator."),
            new Tip("Naval", "Watch the landing beach",
                    "Later squads may arrive by warship when the coast supports a landing. Raiders disembark to join the ground assault. Do not leave your shoreline undefended."),
            new Tip("Siege engines", "Defeat the operator",
                    "Native Recruits engineers operate supplied ballistae and catapults after preparation. A defeated operator is not replaced. Reach the gun crew or destroy the engine."),
            new Tip("Recovery", "Protect the faction's claim",
                    "A captured core transfers its whole Recruits claim to the Raiders. Outnumber the enemy at your core for 120 seconds to reclaim the territory. Your recruits count even while you are offline. Timers and radius are configurable.")
    );

    private DefensePlaybook() {}

    public record Tip(String tag, String title, String body) {}
}
