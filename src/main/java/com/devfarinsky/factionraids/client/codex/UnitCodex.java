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
 * <p>v2.28.0 rewrite: every entry was audited against the actual role
 * assignment code in {@code RaidEvents.assignRole()} and the spawn
 * distribution in {@code RaidEvents.chooseRecruitType()}. Fictional
 * behaviors (assassin "skips defenders", engineer "builds engines on the
 * field", ravager "breaks unreinforced blocks", illusioner "casts
 * blindness") were removed; the remaining copy describes only what the
 * mod actually does today. The Captain aura is a real v2.28.0 mechanic
 * (see {@code RaidEvents.applyCaptainAuraTick}).
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
                    "Elite melee that buffs nearby raiders.",
                    "Health: high  \u2022  Damage: high  \u2022  Range: melee",
                    "Every second, raiders within 8 blocks of a captain gain a short Strength I pulse. The aura ends the moment the captain dies.",
                    "Kill captains first. Their aura is your biggest damage swing per kill \u2014 crossbow bolts from cover work well because captains push forward.",
                    "Iron gear, emeralds, occasional enchanted sword.",
                    "Wave 2+"),
            new Entry("assassin", "Assassin",
                    "Fast melee breacher with light armor.",
                    "Health: low  \u2022  Damage: high  \u2022  Range: melee",
                    "Tagged as a breacher \u2014 pushes hard toward the objective and swings at gates and doors on the way.",
                    "Focus early with ranged fire before they close. Assassins hit hard but fold fast if you catch them in the open.",
                    "Occasional emeralds, rare enchanted weapon.",
                    "Wave 3+"),
            new Entry("siege_engineer", "Siege Engineer",
                    "Breacher class from the Recruits mod. Ranged threat.",
                    "Health: medium  \u2022  Damage: medium  \u2022  Range: melee + tools",
                    "Tagged as a breacher \u2014 pushes gates and doors alongside dedicated breachers.",
                    "Priority target behind the front line \u2014 taking one out slows the physical breach.",
                    "Iron ingots, occasional TNT, redstone.",
                    "Wave 3+"),
            new Entry("patrol_leader", "Patrol Leader",
                    "Mid-tier officer. Tagged as a captain in-mod.",
                    "Health: high  \u2022  Damage: high  \u2022  Range: melee",
                    "Shares the captain role tag \u2014 also emits the Captain aura. Two patrol leaders in one push is a real damage spike.",
                    "Treat like any captain: kill first, prevent the buff from stacking with a nearby captain.",
                    "Iron gear, emeralds.",
                    "Wave 3+"),
            new Entry("ravager", "Ravager Beast",
                    "Vanilla ravager. Charges the front line. One per final wave.",
                    "Health: very high  \u2022  Damage: very high  \u2022  Range: melee + charge",
                    "Vanilla ravager behavior: tramples crops, breaks leaves, and stunlocks defenders it charges into. It does NOT break stone or fences \u2014 it just walks through the wave gate the raid opens.",
                    "Kite in circles \u2014 ravagers turn slowly. Use elevation; a two-block ledge is enough to farm them safely.",
                    "Saddle (occasional), emeralds, hide.",
                    "Final wave"),
            new Entry("illusioner", "Illusioner",
                    "Vanilla illusioner. Spawns visual duplicates.",
                    "Health: low  \u2022  Damage: medium  \u2022  Range: 8 blocks",
                    "Vanilla illusioner behavior: fires arrows and periodically spawns four visual clones. The clones do not deal damage; the real one is the one that takes damage.",
                    "Rush in melee \u2014 clones disappear on hit, the real illusioner is the one that bleeds. Tagged as a warcaster in the role labels.",
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
