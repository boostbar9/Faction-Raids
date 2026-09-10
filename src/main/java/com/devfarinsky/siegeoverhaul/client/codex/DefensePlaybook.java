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
            // === Setup ===
            new Tip("Setup","Claim land, place a Siege Core",
                    "You need the Recruits mod. Create or join a faction and claim at least one chunk in the Overworld. Place a single Siege Core anywhere inside your claim. Every raid that spawns for your faction will march on that core."),
            new Tip("Setup","Open the Command Center",
                    "Right-click the Siege Core to open the Command Center. That's the tabbed UI for hiring, banking, checking your roster, viewing territory, and reading intel. Press Escape or click the X in the corner to close."),
            // === Economy ===
            new Tip("Bank","Your Purse vs the Bank",
                    "Your Purse (top right) is the emeralds in your pocket. The Bank card (Bank tab) is your faction treasury. Deposits go to the bank so any member can spend them. Hire costs pull from the bank first, then from your purse."),
            new Tip("Bank","Deposit early, deposit often",
                    "The bank earns interest at the start of every siege wave, so emeralds sitting in the bank grow while emeralds in your pocket don't. Drop your loot in as soon as you get back to base."),
            // === Hiring ===
            new Tip("Hire","Two offers, one worker, one hero",
                    "The Army tab always shows two recruit offers, one worker, and one featured hero. Stock is shared across your whole faction and rotates every 15 minutes, so coordinate purchases with your team."),
            new Tip("Hire","Read the card before you buy",
                    "Each hire card shows the role, a short description, its kit (armor and weapon), and the cost. Hover for extra flavor. Recruits fight, workers gather, heroes have unique abilities."),
            new Tip("Hire","Heroes only spawn on Sundays",
                    "Four heroes rotate through the featured slot: Kael Bloodthorn, Branna Dawnwarden, Sylva Stormbow, and Orin Frostbinder. They cost more but bring passive auras that boost your recruits."),
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
            new Tip("Territory","See your claims",
                    "The Territory tab shows a live top-down map of your surroundings with faction claim overlays. Zoom with the scroll wheel. This is your best planning tool for placing forward camps or blocking bridges."),
            new Tip("Territory","Waypoints and shortcuts",
                    "Press M in the world for the full territory map with waypoint markers on claim centers. Handy for teleporting attention across a large front without alt-tabbing to a map mod."),
            // === Intel ===
            new Tip("Intel","Know your enemy",
                    "The Intel tab lists every raider unit: their tag, stats, behavior, counter, and drops. Read it before the first wave so you're not surprised by sappers or siege engineers."),
            new Tip("Intel","Faction lore matters",
                    "Each attacking faction has its own theme: Blackbay Reavers strike from the water, Hollowfang Clan brute-forces, Emberchant Zealots burn, Crownfall Exiles fight for lost titles, Wilds Marauders swarm. Their unit mix reflects that."),
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
