# Olympian utility relics

These are registered consumable items with server-side actions, not renamed vanilla ingredients. Both loot boxes bought through the Core and wave-drop boxes use the same reward factory.

| Relic | Action | Limits |
| --- | --- | --- |
| Hephaestus' Forge Ember | Repairs damaged gear held in the other hand; otherwise repairs the equipped armor piece with the greatest fraction of durability missing. | 25% of maximum durability, capped at 400 points; never exceeds full durability. Item identity, enchants and other NBT are preserved. |
| Athena's Owl Seal | Applies a ten-second glowing outline to nearby siege enemies, visible to tracking players through walls. | Nearest eight eligible living siege units within 16 blocks; excludes players, allies, unmarked mobs and spectators. No chunk loading or terrain changes. |
| Apollo's Sun Laurel | Removes poison, wither, blindness, weakness and slowness from the user. | Preserves beneficial effects and other statuses, including Bad Omen. |

Each successful use consumes one relic outside Creative and starts a ten-second per-kind cooldown. Cooldown ticks are saved on the player as well as sent to the client. Empty searches or other ineffective uses spend nothing and receive a one-second retry delay. Activation uses native particle/sound packets; there is no passive tick loop or custom renderer. Longer and permanent glowing effects are preserved without spending a charge. The seal's normal glowing effect is public to tracking clients, not private reconnaissance.

Each sealed box contains equipment, provisions and a relic stack. Common/Uncommon supply one charge, Rare/Epic two. Remaining stacks are sampled without replacement across the remaining supply categories. Total reward stacks remain 3–7 and prices stay 16/48/96 emeralds. Healing, speed and fire-protection potions, repair XP and ammunition remain ordinary supplies. New supplies have no alternate sneak-use powers. Tidehooks, Harvest hoes and Deep Breath draughts no longer appear in rewards or the Creative catalog. Infinity-compatible ordinary arrows replace spectral arrows when that category rolls alongside an Infinity bow. Existing opened items are not rewritten.

All three models are original cuboid geometry using vanilla material references. Run `python tools/generate_olympian_relic_models.py` to reproduce the JSON and [model sheet](images/olympian-relic-models.svg). Colors in the sheet are approximate; it is not an in-game screenshot.

Tests exercise real item action methods, repair preservation, failed use, both hands, cooldowns, Creative/spectator/client guards, cleanse filtering, target filtering/caps, generation, save/load and Creative inclusion. Plain JUnit has no Forge registration event: a scoped test fixture substitutes three distinct vanilla registry identities only for loot/catalog construction. It does not replace production registration or action logic. No interactive companion-mod playtest or GL rendering check is claimed.
