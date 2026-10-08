package com.devfarinsky.siegeoverhaul.client.codex;

import com.devfarinsky.siegeoverhaul.core.TerritoryFortification;
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
            // === Setup ===
            new Tip("Setup","Claim land, place a Siege Core",
                    "You need the Recruits mod. Create or join a faction. In the Overworld, the faction leader opens the claim map (M by default), right-clicks a chunk within four chunks of them and picks Claim Area for the first 5x5 claim. Claim Chunk only extends an existing claim. Carry the claim cost in your inventory and stay more than three chunks away from other factions' claims. Place a single Siege Core anywhere inside your claim. Every raid that spawns for your faction will march on that core."),
            new Tip("Setup","Open the Command Center",
                    "Right-click the Siege Core to open the Command Center. That's the tabbed UI for hiring, banking, checking your roster, improving territory and planning defenses, and reading intel. Press Escape or click the X in the corner to close."),
            // === Economy ===
            new Tip("Bank","Your Purse vs the Bank",
                    "Your Purse (top right) is the emeralds in your pocket. The Treasury tab holds your faction funds. Deposit emeralds there before shopping. All Command Center purchases use only the Treasury; emeralds in your purse are never spent automatically."),
            new Tip("Bank","Deposit early, deposit often",
                    "The bank earns daily interest in game time. Treasury shows the rate, next payout and countdown. Deposit spare emeralds to fund hires, upgrades and construction with your faction."),
            // === Hiring ===
            new Tip("Hire","Two offers, one worker, one hero",
                    "The Army tab always shows two recruit offers, one worker, and one featured hero. Stock is shared across your whole faction and rotates every 15 minutes, so coordinate purchases with your team."),
            new Tip("Hire","Read the card before you buy",
                    "Each hire card shows the role, a short description, its kit (armor and weapon), and the cost. Hover for extra flavor. Recruits fight, workers gather, heroes have unique abilities."),
            new Tip("Hire","Olympian champions",
                    "Twenty Olympian champions rotate through the featured slot, from Ares' front-line fighters to Athena's guardians and Artemis' hunters. Each keeps a distinct signature ability and rarity."),
            // === Defense ===
            new Tip("Defend","Hold the core ring",
                    "During a raid, the enemy tries to enter the ring around your Siege Core. If they outnumber your defenders inside it long enough, they capture the claim. Equal numbers pause capture. A defending majority reverses it."),
            new Tip("Defend","Break the door, not the wall",
                    "Raiders now physically swing at doors and gates. Solid walls make them go around. Force them through a narrow choke you control, or a killbox with archers overhead."),
            new Tip("Defend","Formations and roles",
                    "Shieldmen anchor the front. Archers and crossbowmen work best on elevated firing positions behind them. Heroes carry the line. Position them before the wave hits, not during it."),
            new Tip("Defend","Read the Objective HUD",
                    "The action bar at the top of your screen during a raid tells you what the raiders are doing right now: scouting, breaching, capturing, or retreating. If it says Breaching, they're at a door or gate. If it says Capturing, get bodies into the core ring."),
            // === Territory ===
            new Tip("Territory","Permanent faction upgrades",
                    "The Territory tab offers four permanent faction decrees. Read their effects before buying; Active means your faction already owns that upgrade. Perimeter planning and all placeable construction plans are in Building."),
            new Tip("Builders","Preview a defense before paying",
                    "Collect a free plan from Building > Place structure. Use it on ground to preview, sneak-use to rotate, and use the same anchor again to confirm. Use in air to cancel. Payment happens only when an owned idle builder accepts the commission."),
            new Tip("Builders","Supply the construction site",
                    "Stock the exact materials shown by your plan in your owned Workers storage area with Builders enabled. The whole worksite must remain within storage reach. Native Workers handles tools, materials and work hours. Building > Construction reports your loaded jobs within 128 blocks."),
            new Tip("Builders","Review the automatic perimeter",
                    "Building > Auto perimeter gives you a free Perimeter Plan. Hold it to review the template-style wall inside your claim. Use to confirm or sneak-use to cancel. The flat " + TerritoryFortification.PRICE + "-emerald faction Treasury commission covers the whole perimeter and is separate from supplied blocks. Changed, blocked, steep or oversized plans need a fresh review before payment."),
            new Tip("Builders","Protected construction and owner controls",
                    "New protected jobs require Workers 2.0.3 and Recruits 1.15.2. Builders clear only unchanged small plants; solid obstructions, inventories and reactive surroundings stay protected. Jobs pause when the owner is offline or the site changes. Inspect the native shovel to change projection or cancel the job. Cancel leaves placed blocks intact and does not refund supplies or the commission."),
            // === Intel ===
            new Tip("Intel","Know your enemy",
                    "Search within each Intel section to find units, enemy lore or instructions. The unit archive lists raiders: their tag, stats, behavior, counter, and drops. Read it before the first wave so you're not surprised by sappers or siege engineers."),
            new Tip("Intel","Faction lore matters",
                    "Each Olympian host fights differently: Poseidon's Tide lands from the water, Ares presses the breach, Hephaestus fields siege craft, Athena coordinates captains, and Artemis hunts from range."),
            // === Recapture and counterattack ===
            new Tip("Recapture","Take your territory back",
                    "If a raid captures your claim, the core still stands. Rally your faction, get more bodies into the ring than the occupiers, and hold the majority until recapture completes. The claim flips back."),
            new Tip("Counterattack","Hit the war camp",
                    "Between waves the raiders build a fortified camp with a gate. Scouts appear first, then the main force. Strike the camp during the pre-siege phase to disrupt siege engineers and delay the wave, but leave a reserve on the core."),
            // === Endgame ===
            new Tip("Endgame","Endless campaigns",
                    "Waves scale over time. Faction economy, hero rotation, and rewards persist across sessions. The goal isn't to finish the mod, it's to see how long your faction lasts."),
            new Tip("Tools","Creative tab shortcut",
                    "In creative mode there's a Siege Overhaul tab in the inventory with every mod item, vanilla raider spawn eggs, every Recruits egg that's available, and pre-built faction banners. Useful for testing and for building showcase forts.")
    );

    private DefensePlaybook() {}

    public record Tip(String tag, String title, String body) {}
}
