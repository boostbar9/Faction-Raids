# Mod Research: Villager Recruits & Villager Workers

Deep read of the two mods our raid system is built on. Everything here is grounded in the actual 1.20.1 source at:

- Recruits: https://github.com/talhanation/recruits — `com.talhanation.recruits.*`
- Workers: https://github.com/talhanation/workers — `com.talhanation.workers.*`

The goal of this document is to answer, for each part of Devin's raid vision, **"what does the mod already expose that we can hook into?"** so we don't rebuild systems that already exist.

---

## 1. Villager Recruits — key findings

### 1.1 Entity hierarchy (this is our raider chassis)

```
AbstractInventoryEntity
 └─ AbstractOrderAbleEntity          (447 lines — the order/command state machine)
     └─ AbstractRecruitEntity        (2,383 lines — the full soldier)
         ├─ RecruitEntity            (basic melee)
         ├─ RecruitShieldmanEntity
         ├─ BowmanEntity
         ├─ CrossBowmanEntity
         ├─ HorsemanEntity
         ├─ NomadEntity
         ├─ ScoutEntity
         ├─ AssassinEntity
         └─ SiegeEngineerEntity      (already exists — man siege weapons)

AbstractChunkLoaderEntity
 └─ AbstractLeaderEntity             (874 lines — patrols, waypoints, army command)
     ├─ CaptainEntity                (ship captains)
     ├─ CommanderEntity              (land army leader)
     ├─ MessengerEntity
     └─ AssassinLeaderEntity
```

**Implication for raids:** We do NOT need custom raider entity classes. We spawn **regular `RecruitEntity`/`BowmanEntity`/etc.**, assign them to an enemy faction/team, and their existing AI does everything. The `CommanderEntity` is a ready-made raid-party leader.

### 1.2 The order/command state machine (this is our formation & attack system)

`AbstractRecruitEntity` exposes a huge public state API. The setters that matter for raids:

| Setter | What it does |
|---|---|
| `setOwnerUUID(Optional<UUID>)` | Assigns a "commander" — raiders own each other via their leader UUID |
| `setGroupUUID(UUID)` | Puts the recruit in a `RecruitsGroup` (this is what formations attach to) |
| `setAggroState(int)` | 0=Passive, 1=Neutral, 2=Aggressive, 3=Raid (attacks any living) |
| `setFollowState(int)` | 0=Wander, 1=Follow, 2=Hold, 3=BackToPos, etc. |
| `setShouldHoldPos(boolean)` + `setHoldPos(Vec3)` | Anchor to a defensive position |
| `setShouldMovePos(boolean)` + `setMovePos(BlockPos)` | Send to attack position |
| `setShouldProtect(bool)` + `setProtectUUID(UUID)` | Bodyguard a specific unit (great for protecting our raid Commander) |
| `setShouldRanged(bool)` / `setShouldBlock(bool)` | Fire-at-will / Shield up |
| `setTarget(LivingEntity)` | Direct combat target |
| `setMoral(float)` | Group morale — falls below threshold → routs |

**Implication for raids:** Our siege lifecycle just flips these flags. To send a raid party to a chest we `setShouldMovePos(true) + setMovePos(chestPos) + setAggroState(2)`. No new AI goals needed.

### 1.3 Groups & formations (this is what makes raiders feel like an army, not a mob)

`RecruitsGroup` (`world/RecruitsGroup.java`, 306 lines) is a first-class persisted object:

- `List<UUID> members` — the roster
- `int aggroState` / `int followState` — group-level stance broadcast to members
- `BlockPos upkeep` — supply point
- `int groupMorale` / `int groupHealth` — aggregate stats
- `UUID leaderUUID` — who's in charge
- `boolean allowRanged` / `boolean allowRest`
- Full NBT save/load (`toNBT()`, `fromNBT()`)

Formations are a separate axis (from the `rts-map-command-recruits` addon + base changelog): **Line, Column, Square, Hollow Square, Circle, Hollow Circle, Triangle, V-Form, March, No Formation**. These are applied to a group. In-game, formation keybinds are:

- `KP1`=Circle, `KP2`=H-Circle, `KP3`=Square, `KP4`=H-Square, `KP5`=Line, `KP6`=Triangle, `KP7`=V-Form, `KP8`=March, `KP9`=None

**Implication for raids:** Every raiding party is a `RecruitsGroup`. Formation is chosen per-party by role:

| Party role | Formation | Why |
|---|---|---|
| Scout wave | March / No Formation | Fast movement to probe defenses |
| Bowman line | Line | Volley fire from range |
| Shieldman vanguard | Square / Testudo | Absorb defender arrows while advancing |
| Cavalry flanker | V-Form | Punch through weak flanks |
| Siege engineer detail | H-Circle | Screens the engineer while they operate |

### 1.4 Factions, claims, sieges — the killer finding

The mod already ships a **complete faction/claim/siege system** (v1.14 "Claim and Siege Update"). The relevant classes:

```
world/RecruitsClaim.java              — a single claimed chunk group + banner + owner
world/RecruitsClaimManager.java       — the runtime index
world/RecruitsClaimSaveData.java      — persisted per-world data
world/RecruitsFaction.java            — faction identity (name, banner, color, members)
world/RecruitsFactionManager.java
world/RecruitsDiplomacyManager.java   — ally/neutral/enemy relations
world/RecruitsTreatyManager.java      — formal treaties
world/RecruitsGroupsManager.java      — group registry
world/RecruitsPatrolSpawn.java        — spawns wandering NPC groups
world/RecruitsHireTrade.java          — trade/economy hooks
```

And — **critically** — a real event bus:

```java
// events/SiegeEvent.java
SiegeEvent.Start    // @Cancelable — fires before a siege begins
SiegeEvent.Tick     // @Cancelable — every 100 ticks; exposes attackerCount,
                    //                defenderCount, mutable damage per tick
SiegeEvent.End      // siege failed (attackers wiped)
SiegeEvent.Success  // attackers captured the claim
```

There are matching events for `ClaimEvent`, `FactionEvent`, `DiplomacyEvent`, `RecruitEvent`.

**This changes our entire architecture.** We should not run a parallel siege system. Our raid IS a Recruits siege:

- **We are the attackers.** We spawn an NPC faction, assign it recruits, mark the player's faction as hostile, and start a siege against the player's claim.
- **Recruits handles win/lose.** The "10 troops for 10 minutes" mechanic is already there. We layer narrative and objectives on top.
- **We subscribe to `SiegeEvent.Start/Tick/End/Success`** to drive our announcements, boss bar, block restoration, and loot resolution.
- **`SiegeEvent.Tick` is our per-second heartbeat** — no more `RaidEvents` polling.

Rules of engagement are ready-made: alliance rules (Ally/Neutral/Hostile) already control targeting. We just mark our raider faction as Hostile to the defender.

### 1.5 NPC army & attack controllers (this is our raid-party AI)

```
util/NPCArmy.java                                       — a coordinated group of units
entities/ai/controller/IAttackController.java           — pluggable attack behavior
entities/ai/controller/PatrolLeaderAttackController.java
entities/ai/controller/CaptainPrepareShipAttackController.java
entities/ai/controller/SmallShipsController.java
entities/ai/controller/siegeengineer/                   — siege-weapon attack behavior
```

`AbstractLeaderEntity` already owns:
- `NPCArmy army` (our units) + `NPCArmy enemyArmy` (spotted enemies)
- `IAttackController attackController` (swappable)
- Waypoint stacks (`WAYPOINTS`, `WAYPOINT_ITEMS`, `WAYPOINT_WAIT_SECONDS`)
- Patrol state machine (`State.IDLE/PATROL/RETREAT/RESUPPLY/...`)
- `handleUpkeepState()`, `handleResupply()`, `resetPatrolling()`

**Implication for raids:** We can write custom `IAttackController` implementations for each phase:

- `ApproachAttackController` — march to camp site, ignore contact
- `CampBuildAttackController` — protect the workers building camp; skirmish only
- `ProbeAttackController` — small aggressive detachment testing defenses
- `BreachAttackController` — focus on breachable walls; call in shieldmen
- `LootAttackController` — path to marked chest, engage looters
- `HoldClaimAttackController` — occupy the claim to trigger `SiegeEvent.Success`

Each swaps into `CommanderEntity.attackController = new X(...)` at the right phase.

### 1.6 Async pathfinding

`pathfinding/AsyncPathfinder.java` + `AsyncGroundPathNavigation.java` + `AsyncWaterBoundPathNavigation.java`. Recruits already do off-thread pathfinding. **Any raider we spawn inherits this** — we don't need to worry about pathfinding cost even with 30+ units in a siege.

### 1.7 SmallShips compat (naval approach)

`compat/smallships/SmallShips.java` (522 lines) — a full wrapper: sail state, steering, cannon fire (`shootCannonsSmallShip`), repair, rotation-toward-position. `CaptainPrepareShipAttackController` already handles "sail to target, disembark, attack". Cannon fire uses `canShootCannons(vehicle)` + `shootCannonsSmallShip(driver, boat, target, leftSide)`.

**Implication for raids:** If target claim borders water, spawn a `CaptainEntity` with a boat, assign it `CaptainPrepareShipAttackController`, load recruits, sail to shore, disembark, hand off to land phase.

### 1.8 Siege weapons

`compat/siegeweapons/` — `Ballista.java`, `Catapult.java`, `SiegeWeapon.java`. Combined with the already-existing `SiegeEngineerEntity` and the `siegeengineer/` attack controller package, this means **siege weapons are already fully wired**. Our raid just needs to spawn a `SiegeEngineerEntity` with a `Catapult` for the breach phase.

### 1.9 Public event bus surface

```
events/ClaimEvent.java       — claim placed/removed/changed
events/DiplomacyEvent.java   — ally/neutral/enemy flip
events/FactionEvent.java     — faction created/dissolved/etc.
events/RecruitEvent.java     — recruit hired/killed/promoted
events/SiegeEvent.java       — Start/Tick/End/Success
events/RecruitsOnWriteSpawnEggEvent.java
```

Every one of these is `@SubscribeEvent`-able on `MinecraftForge.EVENT_BUS`. This is our primary integration point — we're not doing reflection or mixins.

---

## 2. Villager Workers — key findings

### 2.1 Worker hierarchy

```
AbstractInventoryEntity
 └─ AbstractWorkerEntity              (631 lines)
     ├─ LumberjackEntity              (164 lines — chops trees)
     ├─ BuilderEntity                 (160 lines — builds from blueprints)
     ├─ MinerEntity
     ├─ FarmerEntity
     ├─ AnimalFarmerEntity
     ├─ FishermanEntity
     ├─ CookEntity
     ├─ CourierEntity                 (356 lines — hauls between chests)
     └─ MerchantEntity
```

### 2.2 Work-area system (this is our camp blueprint framework)

`entities/workarea/`:

```
AbstractWorkAreaEntity.java      — base for all named work zones
BuildArea.java                   — a blueprint zone (this is what BuilderEntity consumes)
LumberArea.java                  — designated tree-cutting zone
MiningArea.java, CropArea.java, KitchenArea.java, FishingArea.java,
AnimalPenArea.java, MarketArea.java, StorageArea.java, HomeArea.java
IPermissionArea.java             — access-control interface
```

**Implication for raids:** The camp build phase becomes:

1. Spawn a `BuildArea` at the chosen campsite loaded with our tent/wall/tower NBT blueprints.
2. Spawn a `LumberArea` surrounding it — trees inside are timber for the camp.
3. Spawn a `StorageArea` (chest) at the camp center for material staging.
4. Spawn 1–2 `LumberjackEntity` (owned by the raider faction) to fell trees.
5. Spawn 1–2 `BuilderEntity` to construct from the blueprint.

The workers do the entire construction themselves. We don't script placement — the mod does. When the `BuildArea` reports complete, we advance to the next siege phase.

### 2.3 The chest/looting behavior (this IS our chest-raid mechanic)

`entities/ai/AbstractChestGoal.java` is the base for all chest interaction. Key methods:

```java
public boolean moveToPosition(BlockPos pos)   // pathfinds worker to chest
public void interactChest(Container c, bool open)  // physically opens the chest
public int getAmountOfItem(Item item)              // inventory scan
public boolean isContainerFull(Container c)
```

Two concrete goals extend it:
- `DepositItemsToStorage.java` — worker walks up, opens chest, deposits
- `GetNeededItemsFromStorage.java` — worker walks up, opens chest, takes what it needs

Plus `RecruitStorageUpkeepGoal.java` — **this is a direct precedent for Recruits pulling items out of chests**, exactly the pattern our chest-looting raiders use.

**Implication for raids:** Our `LootObjective` is not a new system. It's:

1. Scan claim for `ChestBlockEntity` positions.
2. Filter by "chest contains emerald OR any item matching `#factionraids:lootable`" (this is our answer to the user's Q2).
3. Assign each looter recruit a `TargetChestGoal` (our thin adaptation of `GetNeededItemsFromStorage`) with the chest pos and the loot filter tag.
4. On success: transfer matching items into the recruit's inventory; if the recruit dies inside the claim, defender loots the body.
5. On raid end: any looter that escaped with items counts as loot yield for the raider faction (feeds the outcome ledger).

### 2.4 Blueprint system (`BuildBlockParse`, `NeededItem`, `StructureManager`)

- `BuildBlockParse` — resolves each block in a blueprint to its material cost (grass → dirt; oak_stairs → oak_planks-as-material). Caches results.
- `NeededItem` — tracks what materials the builder still needs.
- `StructureManager` — loads NBT structure files as blueprints.
- `BuilderWorkGoal` (source lines confirmed) — has **PREFETCH_MATERIAL_BUDGET = 4** and full break/free/place stacks. Real code, not a stub.

**Implication for raids:** We author 4–6 small `.nbt` structure files under `data/factionraids/structures/camp/` — tent, watchtower, palisade wall segment, campfire, gate, banner. `WarCamp.java` picks a subset per siege based on faction size, and the Workers-side Builder assembles them.

### 2.5 CourierEntity (bonus — supply lines)

`CourierEntity` (356 lines) hauls items between named storage areas on `CourierRoute`s. In late-game raids, we can spawn a courier that ferries stolen emeralds from the front-line looters back to the camp's central chest. Visible supply line = alive world.

---

## 3. Existing compat bridge (our template)

`recruits/src/main/java/com/talhanation/recruits/compat/workers/IVillagerWorker.java`:

```java
public interface IVillagerWorker {
   ItemStack getCustomProfessionItem();
   default ItemStack getCustomProfessionItem2() { return null; }
   Screen getSpecialScreen(AbstractRecruitEntity recruit, Player player);
   void openSpecialGUI(ServerPlayer player);
   boolean hasOnlyScreen();
}
```

This is the pattern talhanation uses to let workers integrate with recruits. Our own compat classes (`RecruitsBridge.java`, `OptionalCompatBridge.java`) already exist in Faction-Raids — we extend them to expose:

- `RecruitsBridge.setGroup(entity, groupUuid)`, `.setAggro(entity, state)`, `.setMovePos(entity, pos)`, `.setProtect(entity, targetUuid)` — thin wrappers over the setters in §1.2 so we never touch AbstractRecruitEntity fields directly.
- `RecruitsBridge.newRaiderGroup(name, factionUuid) → UUID` — creates a `RecruitsGroup` for a raid party.
- `RecruitsBridge.spawnRaider(type, level, pos, groupUuid)` — spawns and configures one raider.
- `RecruitsBridge.startSiegeAgainstClaim(claim, attackerFaction)` — kicks off the native Recruits siege that our system rides on top of.
- `WorkersBridge.spawnLumberjack(level, homePos)`, `.spawnBuilder(level, homePos, blueprint)`, `.setBuildArea(builder, area)` — mirrors above for camp construction.
- `SmallShipsBridge.spawnTroopTransport(level, waterPos, captain, cargo)` — naval approach.

Every one of these is a compile-time optional dependency: if the mod isn't installed, the bridge no-ops and that feature silently disables.

---

## 4. Mapping the vision to concrete integration

Grid of "vision element → what already exists → what we still have to write":

| Vision element | Already exists (theirs) | What we write |
|---|---|---|
| Enemy raiders with real AI | `RecruitEntity` family + `AbstractRecruitEntity` full stack | Nothing — we spawn theirs |
| Faction with a banner + hostility | `RecruitsFaction` + `RecruitsDiplomacyManager` | `RaiderFactionRegistry` — a small pool of NPC factions with names, banners, casus belli |
| Trigger the raid | `RecruitsFactionManager.declareWar()` + native siege start | `RaidScheduler` — picks target, timing, faction |
| Formations | Group formation system + `RecruitsGroup` | `Formations.assign(group, Style)` — thin façade choosing formation per party role |
| Approach march | `AbstractLeaderEntity` waypoints + `IAttackController` | `ApproachAttackController` — 1 class |
| Camp construction | `BuilderEntity` + `BuildArea` + `LumberjackEntity` + `LumberArea` + `.nbt` blueprints | 4-6 tiny `.nbt` files + `WarCampBuilder` that picks blueprint set and spawns workers |
| Progressive attack waves | `NPCArmy` + `IAttackController` swap + siege ticks | `SiegeLifecycle` state machine: Approach → Camp → Probe → Assault → Breach → Loot → Hold |
| Breachable blocks + restore-on-end | `SiegeEvent.End/Success` hook for restore | `BreachSystem` (mostly already in RaidEvents) + `#factionraids:breachable_soft` / `#factionraids:breachable_reinforced` block tags |
| Bed destruction = lose spawn point | Vanilla bed break already does this | Nothing mechanical. Add an announcement: "The raiders shattered your bed. You've lost your foothold here." Do NOT weaken defenders or award capture progress (per Devin's answer). |
| Chest looting | `AbstractChestGoal` + `interactChest` + `GetNeededItemsFromStorage` pattern | `LootObjective` + `TargetChestGoal` (thin adaptation) + `#factionraids:lootable` item tag with emeralds as always-on default |
| Claim capture win | `RecruitsClaim` + native "10 troops for 10 min" + `SiegeEvent.Success` | Nothing — we subscribe to `SiegeEvent.Success` for our announcement/reward hook |
| Naval approach | `CaptainPrepareShipAttackController` + `SmallShips` compat + `CaptainEntity` | `NavalApproachPlanner` — checks if target is coastal, if yes spawns transport(s) |
| Siege weapons | `SiegeEngineerEntity` + `SiegeWeapon`/`Ballista`/`Catapult` + `siegeengineer/` controller | `SiegeEngineerSpawner` — spawns 1-2 during Breach phase against reinforced walls |
| Casus belli (reason for raid) | Nothing | `CasusBelliRegistry` — random pool now (grudge, resource claim, expansion, retaliation). Ledger-driven later. |
| Outcome ledger | Nothing | `RaidLedger` — persisted per-world data: who raided whom, when, outcome, loot taken. Enables ledger-driven casus belli in the ambient-faction phase. |
| Ambient factions (future) | `RecruitsFaction`, `RecruitsPatrolSpawn`, `RecruitsDiplomacyManager`, "Living Villages" addon precedent | `world/AmbientFactionAI` — decides which factions raid which each in-game month; feeds `RaidScheduler` |

**Bottom line: ~80% of what Devin wants already exists in Recruits+Workers.** Our job is to be a **choreographer**: subscribe to their events, spawn their entities into their groups with their controllers, and add the narrative/objective/ledger layer.

---

## 5. Recommended build order (driven by Devin's answer)

Devin's siege sequencing:
> "They build a camp, they attack with strategy depending on the situation… where possible they arrive by boats… they use formations and they try to get into your base and you have to have good defenses to stop them."

That gives a clear PR order — each PR is a real playable slice:

1. **PR: Native siege integration.** Delete our parallel siege driver; subscribe to `SiegeEvent.Start/Tick/End/Success`. Boss bar and block restoration now driven by Recruits events. **Player-visible:** raids feel more solid (accurate attacker/defender counts, proper claim ownership transfer).

2. **PR: Casus belli + raider faction pool.** `CasusBelliRegistry` (random pool), `RaiderFactionRegistry` (3–5 pre-authored factions with banners and names), announcement system. **Player-visible:** each raid arrives with a reason and a named enemy — "The Broken Anvil clan demands tribute."

3. **PR: Camp construction phase.** `WarCampBuilder` spawns `LumberArea` + `BuildArea` + Lumberjacks + Builders + 4–6 `.nbt` blueprints. `SiegeLifecycle` starts here. **Player-visible:** raiders arrive, actually chop your treeline and build a visible camp before attacking. Massive vibe shift.

4. **PR: Naval approach.** `NavalApproachPlanner` for coastal claims. **Player-visible:** shoreline claims get boats. Coastal raids feel very different from land raids.

5. **PR: Formations & progressive waves.** `Formations` façade + `SiegeLifecycle` phase controllers (`ProbeAttackController`, `AssaultAttackController`). **Player-visible:** waves feel tactical — scouts first, then a bowman line, then a shieldman push.

6. **PR: Breach system polished.** Tag-driven allowlists (`#factionraids:breachable_soft`, `#factionraids:breachable_reinforced`), `SiegeEngineerSpawner` for reinforced blocks. Restore-on-end already exists. **Player-visible:** defenses matter — soft walls fall to grunts, reinforced walls need engineers with catapults.

7. **PR: Chest looting objective.** `LootObjective` + `TargetChestGoal` + `#factionraids:lootable` tag + emerald default. Raider bodies drop looted items. **Player-visible:** raiders physically walk to chests and steal from them. Losing raiders = recovering your emeralds.

8. **PR: Outcome ledger + ledger-driven casus belli.** `RaidLedger` persisted data + `CasusBelli.fromHistory()` branch. **Player-visible:** raids feel intentional — "The Broken Anvil returns to avenge their loss last week."

9. **PR: Ambient factions.** `world/AmbientFactionAI` — factions raid *each other*, sometimes you. `RaidScheduler` learns to pick attacker AND defender. **Player-visible:** world feels alive; you see raids happening between AI factions on the horizon.

Each PR should ship as a self-contained slice on top of the refactor foundation. No PR touches more than its own subsystem plus the small edits to `SiegeLifecycle` to add its phase.

---

## 6. Open questions / calls I need to make explicit

1. **Group ownership of raiders.** Recruits' `setOwnerUUID` expects a player UUID. For NPC-owned raiders we'll set the owner to the raider faction's `CommanderEntity` UUID and confirm Recruits' targeting/friendly-fire logic treats that correctly. May need a small mixin or a fake-player UUID. Will verify in PR 2.

2. **`RecruitsFactionManager` public API surface.** I've mapped the world data classes but haven't traced every `declareWar`/`setRelation` signature. Will do that in PR 1 before writing `RaiderFactionRegistry`.

3. **`.nbt` blueprint authoring.** I'll build camp structures in a creative world, save via `/structure save`, and drop the files under `data/factionraids/structures/camp/`. Small — one afternoon.

4. **Behavior when Recruits or Workers is not installed.** The compat bridges already no-op cleanly. Some phases (camp construction) require Workers; if Workers is absent, we fall back to a "raiders arrive already camped" prefab spawn. Recruits is a hard dep — without it there's nothing to raid with.

5. **Player-owned Recruits defending.** The player's own Recruits already defend against hostile-faction attackers via native diplomacy. We just need to make sure our raider faction is marked Hostile to the player's faction and Recruits' existing defense AI does the rest.

