# Battlefield boxes and legacy blessed supplies

As of 4.50.4, newly opened boxes and Creative catalog entries use ordinary food, potion and ammunition actions. They no longer carry alternate sneak-use powers. This avoids spending valuable food or long-duration potions on weaker short buffs. Tidehook fishing rods, Harvest hoes and Deep Breath underwater draughts are retired from new rewards and the catalog; combat tridents remain available at Rare and Epic tiers. Previously saved items are not rewritten or removed.

The table below documents legacy blessed supplies that already have saved powers. Hold sneak and use those items in air to activate. Their tooltips retain the costs, effects and durations. Normal use, eating, shooting, crafting and placement retain their vanilla behavior. Crafted outputs and placed blocks do not carry these item powers forward.

All supply powers share a ten-second server-side cooldown saved on the player. A power spends only the stated quantity, and only when at least one effect applies. Stronger active effects are preserved; equal effects with enough time remaining are not refreshed. Creative can demonstrate powers without spending supplies. There is no passive inventory scan. Activation sends a small enchantment-particle burst and chime.

| Supply | Cost | Sneak-use power |
| --- | --- | --- |
| Moonlit / Sunbow Arrows | 8 | Night Vision I, 60s |
| Athena's Lessons | 1 | Haste II, 30s |
| Forge Stock | 4 ingots or 1 iron block | Resistance I, 15s |
| Rampart Stone | 8 | Absorption I, 30s |
| Healing Draught | 1 | Regeneration II, 4s |
| Road Draught | 1 | Speed II and Jump Boost I, 10s |
| Snaring Arrows | 4 | Night Vision I, 30s; Speed I, 15s |
| Hera's Tribute | 1 | Luck II, 120s; affects eligible fishing treasure rolls, not ordinary mob drops |
| Furnace Draught | 1 | Fire Resistance I, 60s; Resistance I, 8s |
| Deep Breath | 1 | Water Breathing I and Dolphin's Grace I, 30s |
| Work Platforms | 4 | Slow Falling I, 30s |
| Wall Ladders | 4 | Jump Boost II, 30s; Slow Falling I, 10s |
| Golden Harvest | 1 | Regeneration I, 5s |
| Orchard of the Hesperides | 1 | Resistance I and Speed I, 20s |
| Ambrosia | 1 | Strength I, 30s; Regeneration II, 10s |

Using a draught as a blessing spends it outright; normal drinking still provides its normal potion effects and bottle. Food used as a blessing is not eaten and does not refill hunger. Existing Forge Ember, Owl Seal and Sun Laurel actions remain separate.

Tracked active siege raiders killed by the defending faction can drop one box: 2% Common, 0.5% Uncommon, 97.5% none. Kills by defending recruits count through the same faction check as combat bounties. Manual raids use the existing reward-eligibility setting. Untracked eggs, ordinary mobs, scouts, idle camp staff, Creative/spectator kills and environmental deaths do not qualify. The opportunity is consumed once in LivingDropsEvent, including canceled events; doMobLoot is respected. Looting does not multiply this chance. Wave-clear rewards are separate and prices remain 16/48/96.

Validation covers advertised effect/cost matching, save/load identity, cooldowns across supply kinds, vanilla/invalid-tag exclusion, effect cancellation, stronger effects, Creative/client guards, exact roll distribution and duplicate/canceled/disabled drops. Full Forge build required before release. No interactive Minecraft playtest is claimed.

## Optional Curios integration

API inspected against TheIllusiveC4/Curios commit `a91da6ba2396f6ed9042c8a47a471ca5efc97da8` (1.20.x / 5.14.1+1.20.1). Compile-only API and test-only API use that exact release; no Curios classes are packaged. `ICurioItem#getAttributeModifiers(SlotContext, UUID, ItemStack)` and `CuriosApi.registerCurio` are used from a compatibility class reached only after a `ModList.isLoaded("curios")` check during common setup. Item classes themselves have no Curios references.

The three registered utility relics use native charm tags and one merged player charm slot (`SET`, size 1, no replacement). Forge Ember adds 1 toughness; Owl Seal adds 2 armor; Sun Laurel adds 2 maximum health (one heart). Slot UUIDs allow native Curios to apply/remove modifiers; cosmetic slots return none. Additional slots configured by modpacks can stack modifiers normally. Equip through the Curios GUI: right-click auto-equipping is disabled to preserve normal item use. No periodic inventory scans or auto-consumption. Native Curios controls synchronization and drop policy. No custom on-body accessory renderer is provided; the existing item model appears in its slot.

References: [API source](https://github.com/TheIllusiveC4/Curios/tree/a91da6ba2396f6ed9042c8a47a471ca5efc97da8), [optional registration](https://docs.illusivesoulworks.com/1.20.x/curios/items/curio-creation), [slot merging](https://docs.illusivesoulworks.com/1.20.x/curios/slots/slot-register).
