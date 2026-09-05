package com.devfarinsky.factionraids.client.codex;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.List;

/**
 * Client-side bestiary for the Warlord's Codex. Static data — never
 * synced from the server because the roster is baked into the mod. If a
 * server disables specific units via config the codex still lists them; the
 * lore doesn't change based on config.
 *
 * <p>All content here is authored to be helpful in a real fight — the player
 * should be able to read one entry mid-siege and immediately know what to do
 * against the incoming unit. Keep prose tight; every line has to earn its slot.
 */
@OnlyIn(Dist.CLIENT)
public final class UnitCodex {

    public static final List<Entry> ENTRIES = List.of(
            new Entry("shieldman", "Recruit Shieldman",
                    "Bulk melee. Front line of every wave.",
                    "Health: high  \u2022  Damage: medium  \u2022  Range: melee",
                    "Blocks arrows with a raised shield. Slow to reposition.",
                    "Flank with your own Recruits or hit them from above. Crossbow bolts and knockback punch through shields.",
                    "Iron scraps, occasional emeralds. Rare shield drop.",
                    "Wave 1+"),
            new Entry("bowman", "Bowman",
                    "Light ranged. Common from wave 1 onward.",
                    "Health: low  \u2022  Damage: medium  \u2022  Range: 16 blocks",
                    "Kites at range, retreats when melee closes.",
                    "Cover shields until you close the gap. Bowmen die fast to a shieldman rush.",
                    "Arrows, occasional emeralds. Rare bow drop.",
                    "Wave 1+"),
            new Entry("crossbowman", "Crossbowman",
                    "Heavy ranged. Introduced wave 2.",
                    "Health: low  \u2022  Damage: high  \u2022  Range: 12 blocks",
                    "Slow reload but hits hard. Ignores partial cover.",
                    "Break line of sight during reload. Do not stand still in the open.",
                    "Crossbow bolts, occasional emeralds. Rare crossbow drop.",
                    "Wave 2+"),
            new Entry("captain", "Recruit Captain",
                    "Elite melee with a squad-buff aura.",
                    "Health: high  \u2022  Damage: high  \u2022  Range: melee",
                    "Nearby raiders hit slightly harder and take slightly less damage.",
                    "Kill captains first when you see them. Their aura evaporates on death.",
                    "Iron gear, emeralds, occasional enchanted sword.",
                    "Wave 2+"),
            new Entry("assassin", "Assassin",
                    "Fast infiltrator that ignores your walls.",
                    "Health: low  \u2022  Damage: high  \u2022  Range: melee",
                    "Sprints toward the objective; skips defenders where possible.",
                    "Post a defender inside your stronghold, not just on the wall. Assassins are what kill unguarded respawn anchors.",
                    "Occasional emeralds, rare enchanted weapon.",
                    "Wave 3+"),
            new Entry("siege_engineer", "Siege Engineer",
                    "Builds and repairs siege engines mid-fight.",
                    "Health: medium  \u2022  Damage: low  \u2022  Range: melee",
                    "Turns raw wood into siege engines. Priority target if you want to prevent breach.",
                    "Snipe from a distance. Do not let one reach a stalled siege engine.",
                    "Iron ingots, occasional TNT, redstone.",
                    "Wave 3+"),
            new Entry("patrol_leader", "Patrol Leader",
                    "Mid-tier officer. Coordinates flanks.",
                    "Health: high  \u2022  Damage: high  \u2022  Range: melee",
                    "Redirects nearby squadmates to gaps in your defense.",
                    "Treat like a mini-captain. Kill early to fragment the wave.",
                    "Iron gear, emeralds.",
                    "Wave 3+"),
            new Entry("ravager", "Ravager Beast",
                    "Wall-breaker. One per final wave.",
                    "Health: very high  \u2022  Damage: very high  \u2022  Range: melee + charge",
                    "Charges through fences and breaks unreinforced blocks.",
                    "Kite in circles \u2014 ravagers turn slowly. Use elevation, drop distance is fatal.",
                    "Saddle (occasional), emeralds, hide.",
                    "Final wave"),
            new Entry("illusioner", "Illusioner",
                    "Spawns duplicates that block your shots.",
                    "Health: low  \u2022  Damage: medium  \u2022  Range: 8 blocks",
                    "Blindness spell wastes your arrows on illusions.",
                    "Rush them in melee \u2014 duplicates die to a single hit and the real illusioner is the one that bleeds.",
                    "Emeralds, occasional totem, rare enchanted book.",
                    "Wave 4+"),
            new Entry("commander", "Faction Commander",
                    "Boss unit. One per final wave, boss-bar tracked.",
                    "Health: very high  \u2022  Damage: very high  \u2022  Range: melee",
                    "Coordinates the final assault. Kill = raid ends.",
                    "Focus fire once the boss bar appears. Everything else in the final wave dies when the commander does.",
                    "Guaranteed victory treasury payout, enchanted commander weapon.",
                    "Final wave")
    );

    private UnitCodex() {}

    public record Entry(
            String id,
            String name,
            String tagline,
            String stats,
            String behavior,
            String counter,
            String drops,
            String availability) {}
}
