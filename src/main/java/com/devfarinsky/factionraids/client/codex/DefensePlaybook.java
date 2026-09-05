package com.devfarinsky.factionraids.client.codex;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.List;

/**
 * Curated defense-strategy tips displayed in the Warlord's Codex.
 * Purely static; the book renders these as tip cards. The content is written
 * as concrete, actionable advice \u2014 no generic "build walls" filler.
 */
@OnlyIn(Dist.CLIENT)
public final class DefensePlaybook {

    public static final List<Tip> TIPS = List.of(
            new Tip("Stronghold",
                    "Your respawn point IS the target",
                    "Any bed or respawn anchor you sleep at becomes your stronghold. If you move your bed mid-raid the objective does not follow \u2014 finish the current siege first, then relocate. Use /factionraids home refresh after sleeping to update it explicitly."),
            new Tip("Walls",
                    "Two-block thick walls block ravagers",
                    "Ravagers charge through fences and single-block walls. A two-block cobblestone or deepslate wall stops them cold; a wall with a lava moat kills the wave for you. Do not use planks \u2014 fire arrows will burn them down mid-siege."),
            new Tip("Height",
                    "Elevation wins fights",
                    "Give yourself a 3-4 block platform above ground with a ladder or trapdoor drop. Bowmen and crossbowmen cannot elevate their aim reliably; you will out-DPS them from a rooftop with a crossbow of your own."),
            new Tip("Recruits",
                    "Post Recruits INSIDE the stronghold",
                    "Assassins skip your wall to rush the objective. Station at least two Recruits (a shieldman + a bowman is ideal) inside the ring of your stronghold, not just on the perimeter. They intercept infiltrators the wall cannot stop."),
            new Tip("Gates",
                    "Iron doors > wooden doors",
                    "Wooden doors get chopped down by axes in seconds. Iron doors require a pickaxe and slow raiders massively. If you must use wood, put a stone-brick pressure plate one block behind it \u2014 anything that steps on it triggers your obsidian trap."),
            new Tip("Naval",
                    "Coastal bases need shore denial",
                    "If the enemy war camp forms over water they will arrive by boat. A wall two blocks tall in shallow water where boats beach turns their landing into a killing ground. Alternatively, dig the shore into a 4-block cliff \u2014 raiders can\u2019t climb it while dismounting."),
            new Tip("Waves",
                    "Rest waves are for repairs, not looting",
                    "The 30 seconds between waves is for patching walls, replacing burned torches, healing your Recruits with golden apples, and re-nocking arrows. Do not use it to sort chests \u2014 the next wave will not wait."),
            new Tip("Commander",
                    "Focus the boss to end the siege",
                    "On the final wave the Faction Commander appears with a boss bar. Killing the commander ends the raid instantly, even if 15 raiders are still fighting. Focus every arrow you have on the commander \u2014 the rest are cleanup."),
            new Tip("Rewards",
                    "Practice sieges do not pay",
                    "/factionraids start triggers your stronghold's next siege early. It is fully rewarded. What is NOT rewarded is anything the config marks as a training run \u2014 check the Overview tab; it will say \"reward eligible\" if this siege will pay out."),
            new Tip("Emergency",
                    "/factionraids stop is for admins only",
                    "There is no player-facing panic button. If you are losing and it is unwinnable, the honest move is to die at your bed and respawn \u2014 the raid completes as a defender defeat and rolls over to your next scheduled siege. Losing is fine.")
    );

    private DefensePlaybook() {}

    public record Tip(String tag, String title, String body) {}
}
