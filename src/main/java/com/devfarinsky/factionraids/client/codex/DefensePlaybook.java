package com.devfarinsky.factionraids.client.codex;

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
            new Tip("Stronghold",
                    "Your respawn point IS the target",
                    "Any bed or respawn anchor you sleep at becomes your stronghold. If you move your bed mid-raid the objective does not follow \u2014 finish the current siege first, then relocate. Use /factionraids home refresh after sleeping to update it explicitly."),
            new Tip("Walls",
                    "Stone walls beat wooden ones",
                    "Ravagers cannot break stone or fences \u2014 they only trample crops and leaves. Your real wall problem is the breach path the raid carves toward the objective. Deepslate/obsidian at the likely breach corridor is worth 10 walls anywhere else. Do not use planks \u2014 fire arrows will burn them down mid-siege."),
            new Tip("Height",
                    "Elevation wins fights",
                    "Give yourself a 3-4 block platform above ground with a ladder or trapdoor drop. Bowmen and crossbowmen cannot elevate their aim reliably; you will out-DPS them from a rooftop with a crossbow of your own."),
            new Tip("Recruits",
                    "Post Recruits INSIDE the stronghold",
                    "Assassins skip your wall to rush the objective. Station at least two Recruits (a shieldman + a bowman is ideal) inside the ring of your stronghold, not just on the perimeter. They intercept infiltrators the wall cannot stop."),
            new Tip("Gates",
                    "Iron doors > wooden doors",
                    "Wooden doors get chopped down by raider axes in seconds \u2014 physical breaching goes for doors first. Iron doors require a pickaxe and slow raiders massively. Put your bed one door deeper than you think you need; assassins push straight for the objective."),
            new Tip("Naval",
                    "Coastal bases need shore denial",
                    "If the enemy war camp forms over water they will arrive by boat. A wall two blocks tall in shallow water where boats beach turns their landing into a killing ground. Alternatively, dig the shore into a 4-block cliff \u2014 raiders can\u2019t climb it while dismounting."),
            new Tip("Waves",
                    "There is no break between waves",
                    "Waves flow back-to-back once the siege begins \u2014 there is no configurable rest window. Prep before the countdown ends: golden apples on your Recruits, arrows quivered, torches placed. Once wave 1 starts you are fighting until the Faction Commander drops or your bed does."),
            new Tip("Commander",
                    "Kill the Commander with no breach for bonus loot",
                    "The Faction Commander appears on the final wave with a boss bar. Killing the commander ends the raid instantly. New in v2.28.0: ending the siege with your perimeter never breached grants a bonus emerald payout on top of the standard reward \u2014 defense that never bends pays more than defense that recovers."),
            new Tip("Rewards",
                    "Check reward eligibility BEFORE the siege",
                    "The Overview tab's DEFENSE FORECAST card (shown when no siege is active) lists 'Reward eligible: yes/no'. During the live siege that indicator is not visible \u2014 by then it's locked in. Check the forecast before triggering /factionraids start."),
            new Tip("Emergency",
                    "/factionraids stop is for admins only",
                    "There is no player-facing panic button. If you are losing and it is unwinnable, the honest move is to die at your bed and respawn \u2014 the raid completes as a defender defeat and rolls over to your next scheduled siege. Losing is fine.")
    );

    private DefensePlaybook() {}

    public record Tip(String tag, String title, String body) {}
}
