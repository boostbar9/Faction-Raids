Warning: truncated output (original token count: 91527)
Total output lines: 6207

package com.devfarinsky.siegeoverhaul;

import com.devfarinsky.siegeoverhaul.core.EndlessSiege;
import com.devfarinsky.siegeoverhaul.core.FactionBank;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.monster.Vex;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.raid.Raider;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Team;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.EntityMountEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraft.world.level.pathfinder.BlockPathTypes;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingExperienceDropEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.registries.ForgeRegistries;

import com.devfarinsky.siegeoverhaul.command.RaidCommands;
import com.devfarinsky.siegeoverhaul.raid.RaidBossBars;

import java.util.*;

public final class RaidEvents {
    // Shared constants live in ModConstants / raid.RaidTags; the local aliases below
    // keep the huge body of this class readable while the incremental split continues.
    private static final Component MESSAGE_PREFIX = ModConstants.MESSAGE_PREFIX;
    private static final String RAID_TEAM_TAG = ModConstants.Tags.RAID_TEAM;
    private static final String RAID_ROLE_TAG = ModConstants.Tags.RAID_ROLE;

    /**
     * Per-raid wave composition (progressive picker + formation choice).
     * Populated by queueWave and consulted by createAttackerForWave and the
     * FormationDirector tick. Cleared in finishRaid.
     */
    private static final Map<String, com.devfarinsky.siegeoverhaul.waves.WaveComposition> ACTIVE_COMPOSITIONS = new HashMap<>();
    private static int tickCounter;

    // v2.23.0 Press-the-Attack: per-raider stuck tracker. Keyed by raider UUID.
    // Value carries the last observed distance-to-objective, the tick that
    // distance was recorded, and how many escalations we have applied. Entries
    // for dead raiders age out because redirectRaiders skips missing entities
    // and finishRaid clears state. Concurrent map because multiple worlds may
    // tick raids in parallel on some server setups.
    static final java.util.Map<UUID, StuckEntry> STUCK_TRACKER =
            new java.util.concurrent.ConcurrentHashMap<>();

    static final class PathingTelemetry {
        int fallbackSearches;
        int fallbackCacheHits;
        int stuckEscalationsL1;
        int stuckEscalationsL2;
        int stuckEscalationsL3;
        int breachCandidatesScanned;
        int breachCandidatesRejectedNoStand;
        int finalApproachBoostTicks;
    }

    static final java.util.Map<String, PathingTelemetry> PATHING_TELEMETRY =
            new java.util.concurrent.ConcurrentHashMap<>();

    private static PathingTelemetry pathingTelemetry(String teamKey) {
        return PATHING_TELEMETRY.computeIfAbsent(teamKey, ignored -> new PathingTelemetry());
    }

    static final class StuckEntry {
        double lastDistSq;
        long lastProgressGameTime;
        long nextFallbackSearchGameTime;
        long fallbackCacheUntilGameTime;
        Vec3 cachedFallbackTarget;
        Vec3 cachedFallbackObjective;
        // 0 = fresh, 1 = jump+burst, 2 = wide-aggro + cone fallback,
        // 3 = short teleport forward toward objective (v4.17.0).
        int escalationLevel;

        StuckEntry(double distSq, long gameTime) {
            this.lastDistSq = distSq;
            this.lastProgressGameTime = gameTime;
            this.nextFallbackSearchGameTime = gameTime;
            this.fallbackCacheUntilGameTime = gameTime;
            this.cachedFallbackTarget = null;
            this.escalationLevel = 0;
        }
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        RaidCommands.register(event.getDispatcher());
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !RaidConfig.ENABLED.get()) return;
        if (++tickCounter < 20) return;
        tickCounter = 0;
        tick(event.getServer());
    }

    @SubscribeEvent(priority = net.minecraftforge.eventbus.api.EventPriority.LOWEST)
    public static void onCampWorkerTick(net.minecraftforge.event.entity.living.LivingEvent.LivingTickEvent event) {
        if (!(event.getEntity() instanceof Mob mob) || !(mob.level() instanceof ServerLevel level)) return;
        String team = mob.getPersistentData().getString(ModConstants.Tags.CAMP_WORKER_TEAM);
        if (team.isBlank()) return;
        RaidSavedData.RaidState raid = RaidSavedData.get(level.getServer()).raids.get(team);
        if (raid != null && com.devfarinsky.siegeoverhaul.camp.NativeCampConstruction.active(raid)
                && ((RaidConfig.PAUSE_WHEN_FACTION_OFFLINE.get() && onlineMembers(level.getServer(), team).isEmpty())
                || !com.devfarinsky.siegeoverhaul.camp.NativeCampConstruction.safeToTick(level, raid))) event.setCanceled(true);
        if(raid!=null && !event.isCanceled())com.devfarinsky.siegeoverhaul.camp.BuilderSupport.tick(level,mob,raid);
    }

    @SubscribeEvent
    public static void onEntityJoin(EntityJoinLevelEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        com.devfarinsky.siegeoverhaul.raid.RaidCavalry.join(event,level);
        if(event.isCanceled())return;
        Entity joining = event.getEntity();
        if (joining instanceof net.minecraft.world.entity.projectile.Projectile projectile
                && projectile.getOwner() instanceof Mob owner
                && !owner.getPersistentData().getString(com.devfarinsky.siegeoverhaul.siege.SiegeDeployment.TEAM_TAG).isBlank()) {
            ResourceLocation kind = ForgeRegistries.ENTITY_TYPES.getKey(joining.getType());
            if (kind != null && kind.toString().equals("siegeweapons:catapult_projectile")) {
                try {
                    // Native cobble explosions otherwise destroy arbitrary blocks outside the restoration ledger.
                    joining.getClass().getMethod("setAreaDamage", double.class).invoke(joining, 0.0D);
                } catch (ReflectiveOperationException | RuntimeException ex) {
                    joining.discard(); event.setCanceled(true); return;
                }
            }
        }
        String engineTeam = joining.getPersistentData().getString(com.devfarinsky.siegeoverhaul.siege.SiegeDeployment.TEAM_TAG);
        if (!(joining instanceof Mob) && !engineTeam.isBlank() && event.loadedFromDisk()
                && RaidConfig.CLEANUP_SURVIVING_ENGINES.get()
                && !RaidSavedData.get(level.getServer()).raids.containsKey(engineTeam)) {
            joining.discard(); event.setCanceled(true); return;
        }
        String areaTeam = event.getEntity().getPersistentData().getString(ModConstants.Tags.CAMP_AREA_TEAM);
        if (!areaTeam.isBlank()) {
            if (event.loadedFromDisk() && !com.devfarinsky.siegeoverhaul.camp.NativeCampConstruction.reloadArea(
                    level, event.getEntity(), RaidSavedData.get(level.getServer()).raids.get(areaTeam))) {
                event.getEntity().discard();
                event.setCanceled(true);
            }
            return;
        }
        if (!(event.getEntity() instanceof Mob mob)) return;
        String guardTeam = mob.getPersistentData().getString(com.devfarinsky.siegeoverhaul.camp.CampGuards.TEAM_TAG);
        if (!guardTeam.isBlank()) {
            RaidSavedData.RaidState guardRaid = RaidSavedData.get(level.getServer()).raids.get(guardTeam);
            if (event.loadedFromDisk() && (guardRaid == null || !guardRaid.campGuards.contains(mob.getUUID()))) {
                mob.discard(); event.setCanceled(true);
            }
            return;
        }
        String campTeam = mob.getPersistentData().getString(ModConstants.Tags.CAMP_WORKER_TEAM);
        if (!campTeam.isBlank()) {
            if (event.loadedFromDisk()) {
                RaidSavedData.RaidState campRaid = RaidSavedData.get(level.getServer()).raids.get(campTeam);
                if (campRaid == null || !campRaid.campWorkers.contains(mob.getUUID())) {
                    mob.discard();
                    event.setCanceled(true);
                } else {
                    RecruitsBridge.assignToRaidersFaction(mob);
                    if (com.devfarinsky.siegeoverhaul.camp.NativeCampConstruction.active(campRaid)) {
                        try {
                            com.devfarinsky.siegeoverhaul.compat.WorkersBridge.enableNative(mob,
                                    campRaid.nativeCamp.getUUID(ModConstants.Tags.CAMP_OWNER), false);
                        } catch (ReflectiveOperationException | RuntimeException ex) {
                            mob.discard();
                            event.setCanceled(true);
                        }
                    } else if (!com.devfarinsky.siegeoverhaul.compat.WorkersBridge.parkBuilder(mob)) {
                        mob.discard(); event.setCanceled(true);
                    }
                }
            }
            return; // Camp crew never count as wave enemies or receive soldier AI.
        }
        if (mob instanceof Vex vex && !mob.getPersistentData().contains(RAID_TEAM_TAG)) {
            Mob owner = vex.getOwner();
            if (owner != null && owner.getPersistentData().contains(RAID_TEAM_TAG)) {
                mob.getPersistentData().putString(RAID_TEAM_TAG,
                        owner.getPersistentData().getString(RAID_TEAM_TAG));
                mob.setPersistenceRequired();
            }
        }
        String teamKey = mob.getPersistentData().getString(RAID_TEAM_TAG);
        if (teamKey.isBlank()) return;
        // v2.26.0 scouts carry RAID_TEAM_TAG for friendly-fire logic but are
        // not part of any active raid. Skip raid bookkeeping so we do not
        // discard them here and do not count them as wave spawns/kills.
        if (mob.getPersistentData().getBoolean(ModConstants.Tags.SCOUT)) return;
        RaidSavedData data = RaidSavedData.get(level.getServer());
        RaidSavedData.RaidState state = data.raids.get(teamKey);
        if (state == null) {
            // Clean up an invasion mob that was unloaded when its raid ended or was stopped.
            mob.discard();
            return;
        }
        if (com.devfarinsky.siegeoverhaul.camp.CampSabotage.discardRetreated(mob, state)) {
            event.setCanceled(true);
            return;
        }
        if (event.loadedFromDisk()) {
            // Old saves can contain an unmarked native hold order from before marching ownership was tracked.
            mob.getPersistentData().putBoolean(ModConstants.Tags.FORMATION_MARCH, true);
            com.devfarinsky.siegeoverhaul.formations.RecruitsFormationBridge.release(mob);
        }
        state.raiders.add(mob.getUUID());
        if (RaidConfig.PAUSE_WHEN_FACTION_OFFLINE.get() &&
                onlineMembers(level.getServer(), teamKey).isEmpty()) mob.setNoAi(true);
        // v2.25.0: attach raider-side AI upgrades once per raider. These
        // are cheap no-ops when the config keys are off, so we always
        // attach and let the goals themselves gate on the config value.
        attachRaiderAI(mob);
        data.setDirty();
    }

    /**
     * v2.25.0 raider AI hookup. Called from onEntityJoin after the raider's
     * RAID_TEAM_TAG has been confirmed set. Safe to call multiple times per
     * mob because the parkour goal is added at a unique priority slot and
     * setPathfindingMalus is idempotent.
     */
    private static void attachRaiderAI(Mob mob) {
        com.devfarinsky.siegeoverhaul.raid.RaidMarchDiscipline.install(mob);
        mob.getPersistentData().remove(com.devfarinsky.siegeoverhaul.siege.CommanderWallStrikeGoal.CHARGING);
        mob.goalSelector.addGoal(0,new com.devfarinsky.siegeoverhaul.siege.CommanderWallStrikeGoal(mob));
        // Parkour: leap short obstacles. Only meaningful for PathfinderMobs
        // because the goal drives horizontal-nudge + vertical impulse. Non
        // PathfinderMob raiders (e.g. vex) fall through unchanged.
        if (mob instanceof PathfinderMob) {
            // Priority 2 keeps parkour just below vanilla melee/attack goals
            // (0-1) so it never overrides an in-progress attack, but above
            // wander/look goals (5+) so it fires when the raider is idled
            // by an obstacle.
            mob.goalSelector.addGoal(2, new RaiderParkourGoal(mob));
        }
        // Hazard avoidance: raise pathfinding cost for lethal blocks so
        // vanilla path search routes around them. -1 malus means "never
        // step on"; positive values are additive cost. We use +8 (high
        // but not infinite) so a raider forced through fire will still
        // take it, but any alternative route is preferred.
        if (RaidConfig.AVOID_HAZARDS.get()) {
            mob.setPathfindingMalus(BlockPathTypes.DAMAGE_FIRE, 16.0f);
            mob.setPathfindingMalus(BlockPathTypes.DANGER_FIRE, 16.0f);
            mob.setPathfindingMalus(BlockPathTypes.LAVA, -1.0f);
            mob.setPathfindingMalus(BlockPathTypes.DAMAGE_OTHER, 16.0f);
            mob.setPathfindingMalus(BlockPathTypes.DANGER_OTHER, 16.0f);
        }
        // Hole avoidance: strongly penalise routes that go through mid-air
        // (OPEN) or over cliff-edge fall damage. Raiders were routinely
        // walking into 3-block+ pits on their way to the core because vanilla
        // GroundPathNavigation treats any drop the mob can survive as free.
        // Setting a positive malus on these path types makes the search
        // strongly prefer walking around the pit unless the detour is very
        // long, without hard-blocking (in case the only route is down).
        if (mob instanceof PathfinderMob) {
            // Very high malus on cliffs and mid-air makes the planner refuse
            // to route through ravines and cave openings unless there is no
            // other way through. Values > 20 effectively veto the route in
            // vanilla A*, but still allow a fallback when everything else
            // is worse (e.g. underwater sieges).
            mob.setPathfindingMalus(BlockPathTypes.DAMAGE_CAUTIOUS, 24.0f);
            mob.setPathfindingMalus(BlockPathTypes.WATER_BORDER, 12.0f);
            // Non-blocking, per-tick hole and cave escape watcher.
            mob.goalSelector.addGoal(1,
                    new com.devfarinsky.siegeoverhaul.siege.RaiderHoleAvoidGoal((PathfinderMob) mob));
        }
    }

    /**
     * v2.25.0 shout-to-allies. When a raider is hurt by a defender, alert
     * every allied raider (same RAID_TEAM_TAG) within shoutRadius blocks
     * and give them the attacker as a target if they don't already have
     * one. Ignores line of sight so defenders in cover can't hide from
     * the whole wave. Fires once per hurt event; the natural game rate
     * limits ally-alert cascades to reasonable frequencies.
     */
    @SubscribeEvent
    public static void onRaiderHurt_ShoutToAllies(LivingHurtEvent event) {
        if (!(event.getEntity() instanceof Mob victim)) return;
        String team = victim.getPersistentData().getString(RAID_TEAM_TAG);
        if (team.isBlank()) return;
        // v2.26.0 scouts hurt -> flee, never alert. A scout that summoned a
        // whole faction on hit would defeat the intel-hunt fantasy.
        if (victim.getPersistentData().getBoolean(ModConstants.Tags.SCOUT)) {
            com.devfarinsky.siegeoverhaul.scout.ScoutManager.onScoutHurt(victim);
            return;
        }
        // v4.35.0: flanker cloak. Fires at most once per raider when first
        // brought below half HP so assassins can reposition instead of
        // dying in a shield line. No-ops for every other role.
        com.devfarinsky.siegeoverhaul.raid.EnemyAbilities.onRaiderHurt(victim);
        if (!RaidConfig.SHOUT_TO_ALLIES.get()) return;
        Entity attacker = event.getSource().getEntity();
        if (!(attacker instanceof LivingEntity livingAttacker)) return;
        // Never alert against another raider (friendly fire from vex/etc).
        if (attacker.getPersistentData().getString(RAID_TEAM_TAG).equals(team)) return;
        if (!(victim.level() instanceof ServerLevel level)) return;
        // v3.2.0: record ally-defender damage contribution. When a player who
        // is NOT a member of the raided faction hits a raider, credit their
        // damage to that raid so the victory-payout step can share emeralds
        // with them proportional to their contribution.
        if (attacker instanceof ServerPlayer attackingPlayer) {
            RaidSavedData data = RaidSavedData.get(level.getServer());
            RaidSavedData.RaidState state = data.raids.get(team);
            if (state != null) {
                RaidSavedData.Anchor victimAnchor = data.anchors.get(team);
                boolean isMember = victimAnchor != null && victimAnchor.members().contains(attackingPlayer.getUUID());
                if (!isMember) {
                    state.allyDefenderDamage.merge(attackingPlayer.getUUID(), event.getAmount(), Float::sum);
                }
            }
        }
        int radius = RaidConfig.SHOUT_RADIUS.get();
        for (Mob ally : level.getEntitiesOfClass(Mob.class,
                victim.getBoundingBox().inflate(radius),
                m -> m != victim
                        && team.equals(m.getPersistentData().getString(RAID_TEAM_TAG))
                        && m.isAlive()
                        && m.getTarget() == null
                        // Do not recruit scouts into ally shouting either;
                        // they stay in observe/flee mode.
                        && !m.getPersistentData().getBoolean(ModConstants.Tags.SCOUT))) {
            ally.setTarget(livingAttacker);
        }
    }

    @SubscribeEvent
    public static void onEntityMount(EntityMountEvent event) {
        if (!event.isMounting() || !(event.getEntityMounting() instanceof ServerPlayer player)) return;
        Entity vehicle = event.getEntityBeingMounted();
        if (!OptionalCompatBridge.isSmallShip(vehicle) && !OptionalCompatBridge.isSiegeWeapon(vehicle)) return;
        RaidSavedData data = RaidSavedData.get(player.getServer());
        OptionalCompatBridge.rememberCrewedAsset(vehicle, player.getUUID(), factionKeyForPlayer(data, player));
    }

    @SubscribeEvent
    public static void onLivingAttack(LivingAttackEvent event) {
        Entity attacker = event.getSource().getEntity();
        if (!(attacker instanceof Mob mob)) return;
        if (!mob.getPersistentData().contains(RAID_TEAM_TAG)) return;
        if (event.getEntity().getPersistentData().getString(RAID_TEAM_TAG)
                .equals(mob.getPersistentData().getString(RAID_TEAM_TAG))) {
            event.setCanceled(true);
            mob.setTarget(null);
            return;
        }
        boolean protectedVanillaCivilian = RaidConfig.PROTECT_VILLAGERS.get() &&
                (event.getEntity() instanceof AbstractVillager || event.getEntity() instanceof IronGolem);
        // v3.3.0: extend civilian protection to modded villagers/NPCs
        // (MCA, Alex's Mobs, etc.) via config allowlist. Cheap string
        // match on the entity type id.
        boolean protectedModdedCivilian = false;
        if (!protectedVanillaCivilian) {
            java.util.List<? extends String> allowlist = RaidConfig.CIVILIAN_MOB_ALLOWLIST.get();
            if (allowlist != null && !allowlist.isEmpty()) {
                ResourceLocation typeKey = ForgeRegistries.ENTITY_TYPES.getKey(event.getEntity().getType());
                if (typeKey != null) {
                    String id = typeKey.toString();
                    for (String entry : allowlist) {
                        if (id.equals(entry)) { protectedModdedCivilian = true; break; }
                    }
                }
            }
        }
        String defendedFaction = mob.getPersistentData().getString(RAID_TEAM_TAG);
        RaidSavedData.Anchor anchor = event.getEntity().level() instanceof ServerLevel level ?
                RaidSavedData.get(level.getServer()).anchors.get(defendedFaction) : null;
        boolean protectedWorker = RaidConfig.PROTECT_WORKERS.get() && anchor != null &&
                OptionalCompatBridge.workerBelongsToFaction(event.getEntity(), defendedFaction,
                        anchor.members());
        if (protectedVanillaCivilian || protectedModdedCivilian || protectedWorker) {
            event.setCanceled(true);
            mob.setTarget(null);
        }
    }

    @SubscribeEvent
    public static void onLivingExperienceDrop(LivingExperienceDropEvent event) {
        // v3.3.0: scale XP for raiders that carry the per-spawn multiplier
        // tag. Non-raiders and unmarked raiders (multiplier == 1.0) are
        // untouched, so this composes cleanly with mob-XP mods that also
        // hook this event.
        double mult = event.getEntity().getPersistentData().getDouble("SiegeOverhaulXpMult");
        if (mult > 0.0D && Math.abs(mult - 1.0D) > 1e-6) {
            int scaled = (int) Math.round(event.getDroppedExperience() * mult);
            event.setDroppedExperience(Math.max(0, scaled));
        }
    }

    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        if (!(event.getEntity().level() instanceof ServerLevel level)) return;
        String victimTeamKey = event.getEntity().getPersistentData().getString(RAID_TEAM_TAG);

        // Effort-bonus hook: if a raider killed a defender, reward the raid.
        // Fires before the raider-death bookkeeping below because a raider
        // dying to another raider (rare) should not credit itself.
        if (victimTeamKey.isBlank() && RaidConfig.ENABLE_EFFORT_BONUS.get()) {
            var killer = event.getSource().getEntity();
            if (killer != null) {
                String killerTeam = killer.getPersistentData().getString(RAID_TEAM_TAG);
                if (!killerTeam.isBlank()) {
                    RaidSavedData data0 = RaidSavedData.get(level.getServer());
                    RaidSavedData.Anchor anchor0 = data0.anchors.get(killerTeam);
                    if (anchor0 != null && isDefenderVictim(event.getEntity(), anchor0)) {
                        com.devfarinsky.siegeoverhaul.effort.RaidEffortTracker
                                .onDefenderKilled(killerTeam);
                    }
                }
            }
        }

        // v4.32.0: track defender-player deaths per active raid so the
        // Untouchable advancement (win with zero deaths) can check state
        // in finishRaid. We only count faction-member ServerPlayer deaths
        // during an active raid on THAT faction; mob deaths and idle-world
        // deaths don't feed this counter. Wrapped in try to keep a subsystem
        // problem from breaking the death event for other listeners.
        if (event.getEntity() instanceof ServerPlayer defenderPlayer) {
            try {
                RaidSavedData data0 = RaidSavedData.get(level.getServer());
                String defenderKey = com.devfarinsky.siegeoverhaul.core.SiegeCore.key(defenderPlayer);
                if (defenderKey != null && !defenderKey.isBlank()) {
                    RaidSavedData.RaidState activeRaid = data0.raids.get(defenderKey);
                    if (activeRaid != null) {
                        activeRaid.defenderDeaths++;
                        data0.setDirty();
                    }
                }
            } catch (Throwable t) {
                FactionLogger.LOG.debug("[SiegeOverhaul] defender death tally skipped: {}", t.toString());
            }
        }

        if (victimTeamKey.isBlank()) return;
        RaidSavedData data = RaidSavedData.get(level.getServer());
        // v2.26.0 scout death: drop the intel letter and record the removal
        // in the scout mission bookkeeping. Scouts are never in a raid so we
        // return before the raid-state handling below.
        if (event.getEntity() instanceof Mob scoutVictim
                && scoutVictim.getPersistentData().getBoolean(ModConstants.Tags.SCOUT)) {
            RaidSavedData.Anchor scoutAnchor = data.anchors.get(victimTeamKey);
            boolean defeatedByFaction = isFactionDefender(event.getSource().getEntity(), scoutAnchor);
            com.devfarinsky.siegeoverhaul.scout.ScoutManager.onScoutKilled(
                    level.getServer(), data, scoutVictim, defeatedByFaction);
            return;
        }
        RaidSavedData.RaidState state = data.raids.get(victimTeamKey);
        if (state == null || !state.raiders.remove(event.getEntity().getUUID())) return;
        state.missingTicks.remove(event.getEntity().getUUID());
        state.totalDefeated++;
        boolean isCommander = event.getEntity().getUUID().equals(state.commanderUuid) && !state.commanderDefeated;
        RaidSavedData.Anchor anchor = data.anchors.get(victimTeamKey);
        if (isCommander) {
            if (anchor != null) markCommanderDefeated(level.getServer(), anchor, state);
        }
        // v4.32.0: fire advancement triggers for the killer. Only fires if
        // the killer is a real player (not a hired recruit or wolf) so a
        // defender who lets the auto-army do all the work does not
        // accidentally earn kill-based advancements. isFactionDefender
        // above already ran; we reuse the entity ref here.
        if (event.getSource().getEntity() instanceof ServerPlayer killerPlayer) {
            com.devfarinsky.siegeoverhaul.advancements.SiegeTriggers.RAIDER_KILLED.trigger(killerPlayer);
            if (isCommander) {
                String factionId = state.narrative != null && state.narrative.factionId != null
                        ? state.narrative.factionId : "";
                com.devfarinsky.siegeoverhaul.advancements.SiegeTriggers.COMMANDER_KILLED
                        .trigger(killerPlayer, factionId);
            }
        }
        // v4.27.0 combat bounties: pay the treasury for each raider the
        // faction kills. Manual raids are excluded when reward farming is
        // disabled so the config toggle matches wave payouts. Commander pays
        // a larger lump-sum on top of the per-raider tick. v4.27.1 tightens
        // this with lower defaults and a per-raid bounty cap enforced via
        // state.campaign so a single mega-raid can't dump thousands of
        // emeralds into the bank.
        if (state.rewardEligible && isFactionDefender(event.getSource().getEntity(), anchor)) {
            CompoundTag core = data.siegeCores.get(victimTeamKey);
            if (core != null) {
                int raiderBounty = RaidConfig.RAIDER_BOUNTY_EMERALDS.get();
                if (raiderBounty > 0) payBountyCapped(core, state, raiderBounty);
                if (isCommander) {
                    int cmdBounty = RaidConfig.COMMANDER_BOUNTY_EMERALDS.get();
                    if (cmdBounty > 0) payBountyCapped(core, state, cmdBounty);
                }
            }
        }
        data.setDirty();
    }

    /**
     * v4.27.1: pay a bounty into the treasury but honor the per-raid cap
     * stored on state.campaign so a single mega-raid can't inflate the bank.
     * A cap of 0 disables the cap. Bookkeeping key is a plain int so it
     * fits alongside the existing PaidWave / Deposited fields already on
     * the campaign NBT.
     */
    private static void payBountyCapped(CompoundTag core, RaidSavedData.RaidState state, int amount) {
        int cap = RaidConfig.MAX_BOUNTY_EMERALDS_PER_RAID.get();
        int already = state.campaign.getInt("BountyPaid");
        int payable = amount;
        if (cap > 0) {
            int remaining = Math.max(0, cap - already);
            payable = Math.min(payable, remaining);
        }
        if (payable <= 0) return;
        long paid = FactionBank.deposit(core, payable);
        if (paid > 0) state.campaign.putInt("BountyPaid",
                (int) Math.min(Integer.MAX_VALUE, (long) already + paid));
    }

    /**
     * Returns true if {@code victim} counts as a defender for the raid
     * targeting {@code anchor}: an online team member, or an allied recruit
     * owned by a team member.
     */
    private static boolean isDefenderVictim(net.minecraft.world.entity.LivingEntity victim,
                                             RaidSavedData.Anchor anchor) {
        if (victim instanceof ServerPlayer sp) {
            return anchor.members().contains(sp.getUUID());
        }
        if (victim instanceof Mob mob) {
            return RecruitsBridge.belongsTo(mob, anchor.teamKey(), anchor.members());
        }
        return false;
    }

    /**
     * A bounty is earned only when the damaging entity is a member of the
     * defending faction or one of its owned Recruits. Environmental deaths,
     * unrelated players and unrelated mobs still advance normal raid death
     * bookkeeping, but cannot mint treasury rewards.
     */
    static boolean isFactionDefender(Entity entity, RaidSavedData.Anchor anchor) {
        return anchor != null && entity instanceof LivingEntity living
                && isDefenderVictim(living, anchor);
    }

    /**
     * Give each player the Faction Raids guidebook once on first login.
     * The gift is idempotent — marked with a player-NBT flag so it never
     * duplicates on subsequent logins, dimension changes, or /kill respawns.
     */
    @SubscribeEvent
    public static void onPlayerLoggedIn(net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent event) {
        if(event.getEntity() instanceof ServerPlayer player) {
            player.getPersistentData().remove("SiegeVoteReminder");
            EndlessSiege.remind(player,RaidSavedData.get(player.server).raids.get(com.devfarinsky.siegeoverhaul.core.SiegeCore.key(player)));
        }
        if (!(event.getEntity() instanceof ServerPlayer sp)) return;
        com.devfarinsky.siegeoverhaul.items.StarterBagItem.giveOnce(sp);
        // v3.2.0: notify player of any spoils queued while they were offline.
        // v4.31.0: log the failure instead of silently swallowing so a
        // corrupted pendingSpoils entry surfaces in the server log rather
        // than manifesting only as "my spoils vanished."
        try {
            RaidSavedData data = RaidSavedData.get(sp.server);
            java.util.List<RaidSavedData.UnclaimedSpoils> queued = data.pendingSpoils.get(sp.getUUID());
            if (queued != null && !queued.isEmpty()) {
                int count = queued.size();
                sp.sendSystemMessage(Component.literal("You have " + count + " unclaimed siege reward" +
                        (count == 1 ? "" : "s") + ". Run ")
                        .withStyle(ChatFormatting.GOLD)
                        .append(Component.literal("/siegeoverhaul claim").withStyle(ChatFormatting.AQUA))
                        .append(Component.literal(" to collect.").withStyle(ChatFormatting.GOLD)));
            }
        } catch (Exception e) {
            com.devfarinsky.siegeoverhaul.FactionLogger.LOG.warn(
                    "[{}] pending spoils lookup failed for {} on login: {}",
                    SiegeOverhaul.MOD_ID, sp.getGameProfile().getName(), e.toString());
        }
    }

    /**
     * v3.2.0 — re-give the Warlord's Codex on respawn if the player died
     * with it in their inventory and keepInventory is off. Idempotent: the
     * check confirms they no longer have one before granting.
     */
    @SubscribeEvent
    public static void onPlayerRespawn(net.minecraftforge.event.entity.player.PlayerEvent.PlayerRespawnEvent event) {
        if (!RaidConfig.SPAWN_GUIDEBOOK_ON_JOIN.get()) return;
        if (event.isEndConquered()) return; // returning from End portal, not a death respawn
        if (!(event.getEntity() instanceof ServerPlayer sp)) return;
        net.minecraft.world.item.Item book = com.devfarinsky.siegeoverhaul.items.ModItems.GUIDEBOOK.get();
        // Only re-grant if they've been given one before AND no longer have it.
        net.minecraft.nbt.CompoundTag persistent = sp.getPersistentData()
                .getCompound(net.minecraft.world.entity.player.Player.PERSISTED_NBT_TAG);
        if (!persistent.getBoolean("FactionRaidsGuidebookGiven")) return;
        if (playerHasGuidebook(sp, book)) return;
        net.minecraft.world.item.ItemStack stack = new net.minecraft.world.item.ItemStack(book);
        if (!sp.getInventory().add(stack)) sp.drop(stack, false);
        sp.sendSystemMessage(Component.literal("Your Warlord's Codex has been returned.")
                .withStyle(ChatFormatting.GOLD));
    }

    /**
     * Ensure the shared "Raiders" faction exists as soon as the server is
     * fully started. Idempotent — subsequent restarts skip the create path
     * once the scoreboard team and Recruits faction manager entry exist.
     */
    /**
     * v3.0.0 rebrand file-copy migration. Runs before any SavedData
     * read or config load so RaidSavedData.get sees the new-name file
     * naturally. Idempotent via data/siegeoverhaul.migrated marker.
     */
    @SubscribeEvent
    public static void onServerAboutToStart(net.minecraftforge.event.server.ServerAboutToStartEvent event) {
        com.devfarinsky.siegeoverhaul.rebrand.RebrandMigration.run(event.getServer());
    }

    @SubscribeEvent
    public static void onServerStarted(net.minecraftforge.event.server.ServerStartedEvent event) {
        RecruitsBridge.ensureRaidersFaction(event.getServer());
        // v2.15.0: register the role-colored glow teams so raiders can
        // join them at spawn without a per-spawn registry check.
        RaiderLabels.onServerStarted(event.getServer());
        // v3.0.0: after RaiderLabels registered the new-name teams,
        // move any legacy fraid_role_* members into sieov_role_* and
        // delete the legacy team objects.
        com.devfarinsky.siegeoverhaul.rebrand.RebrandMigration.runScoreboard(event.getServer());
        // v2.36.0: rebrand pre-work. Emits the one-time deprecation banner
        // and inventories every legacy persistence surface on this server
        // so v3.0.0's migration reader has ground-truth audit data to work
        // from. Read-only -- mutates nothing.
        com.devfarinsky.siegeoverhaul.rebrand.RebrandProbe.run(event.getServer());
        // v2.28.0: startup audit log. Emits the exact list of player-facing
        // commands the server has just registered, plus the set of codex ids
        // the raid system can generate. Server owners can eyeball this list
        // against the wiki/README to catch drift; the Codex is baked into the
        // client, so a mismatch means an update needs to be shipped.
        FactionLogger.LOG.info("[SiegeOverhaul] Registered player commands: {}",
                "/siegeoverhaul [menu|anchor set|anchor claim|anchor remove|home automatic|" +
                        "home refresh|territory add|territory remove|territory list|member add|" +
                        "member remove|member list|start|status|help|debug*|stop*|admin list*|" +
                        "admin stop*|admin remove*|admin repair*] (* = op-only)");
        FactionLogger.LOG.info("[SiegeOverhaul] Codex ids the raid system can emit: {}",
                "[shieldman, bowman, crossbowman, captain, assassin, siege_engineer, patrol_leader, " +
                        "ravager, illusioner, commander]");
        FactionLogger.LOG.info("[SiegeOverhaul] Optional-mod bridges: Recruits={} Workers={} SmallShips={} SiegeWeapons={}",
                com.devfarinsky.siegeoverhaul.compat.RecruitsClaimsBridge.available(),
                OptionalCompatBridge.isLoaded(OptionalCompatBridge.WORKERS),
                OptionalCompatBridge.isLoaded(OptionalCompatBridge.SMALL_SHIPS),
                OptionalCompatBridge.isLoaded(OptionalCompatBridge.SIEGE_WEAPONS));
    }


    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        PATHING_TELEMETRY.clear();
        STUCK_TRACKER.clear();
        RaidBossBars.shutdown();
        com.devfarinsky.siegeoverhaul.raid.CommanderBossBar.shutdown();
        com.devfarinsky.siegeoverhaul.raid.ClaimWaypoints.shutdown();
        // v2.36.0: reset the probe latch so an integrated-server restart
        // within the same JVM (single-player world reload) re-emits the
        // banner.
        com.devfarinsky.siegeoverhaul.rebrand.RebrandProbe.reset();
    }

    /**
     * v2.13.0: strategic camp-block break handler.
     *
     * <p>When a player breaks a block whose position matches any active
     * raid's {@code campfirePos}, {@code bannerPos}, or {@code barrelPos},
     * apply the corresponding effect:
     * <ul>
     *   <li>Campfire → reinforcements cancelled (pendingWaveSpawns = 0,
     *       ticksToNextSquad = MAX), the war effort loses its heart.</li>
     *   <li>Banner → morale broken; all currently-deployed raiders are
     *       flagged as escaping and the wave advances early.</li>
     *   <li>Barrel → drops a bonus stack of emeralds at the barrel
     *       position (configurable).</li>
     * </ul>
     * The event itself is not cancelled — breaking still succeeds and the
     * block's normal drops still apply. We just react to the break.
     */
    @SubscribeEvent(priority = net.minecraftforge.eventbus.api.EventPriority.LOWEST)
    public static void onCampBlockBroken(net.minecraftforge.event.level.BlockEvent.BreakEvent event) {
        if (event.isCanceled() || !RaidConfig.CAMP_DESTRUCTIBLE_STRUCTURES.get()) return;
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        RaidSavedData data = RaidSavedData.get(level.getServer());
        BlockPos pos = event.getPos();
        for (RaidSavedData.RaidState state : data.raids.values()) {
            if (state.wave <= 0) continue;
            RaidSavedData.Anchor anchor = data.anchors.get(state.teamKey);
            RaidSavedData.DefensePoint point = anchor == null ? null : anchor.point(state.defensePointName);
            if (point == null || !point.dimension().equals(level.dimension().location())) continue;
            if (pos.equals(state.campfirePos)) {
                handleCampfireBroken(level, state);
                data.setDirty();
                return;
            }
            if (pos.equals(state.bannerPos)) {
                handleBannerBroken(level, state);
                data.setDirty();
                return;
            }
            if (pos.equals(state.barrelPos)) {
                handleBarrelBroken(level, state);
                data.setDirty();
                return;
            }
        }
    }

    /**
     * Reinforcement heart. Cancels pending squads for the current wave and
     * suppresses future squads by pushing the next-squad timer far out.
     * Existing deployed raiders keep fighting — defenders now just have to
     * grind them down instead of holding an infinite tap.
     */
    private static void handleCampfireBroken(ServerLevel level, RaidSavedData.RaidState state) {
        state.pendingWaveSpawns = 0;
        state.ticksToNextSquad = Integer.MAX_VALUE;
        // Null the tracked pos so we don't fire again if the block state
        // change bubbles up multiple events.
        state.campfirePos = null;
        announce(level.getServer(), state.teamKey, Component.literal(
                "Campfire destroyed: remaining reinforcements for this wave are stopped. Later waves still attack.")
                .withStyle(ChatFormatting.GREEN), true);
    }

    /**
     * Command banner. Broken morale ends the current wave: the wave-cleared
     * bookkeeping path in the main tick loop already handles the "raiders
     * defeated → next wave" transition, so we drop remaining raiders here
     * and let the tick loop clean up naturally.
     */
    private static void handleBannerBroken(ServerLevel level, RaidSavedData.RaidState state) {
        if (state.coreCaptured) {
            state.bannerPos = null;
            announce(level.getServer(),state.teamKey,Component.literal("Enemy banner destroyed. Reclaim the territory by holding your core.").withStyle(ChatFormatting.GREEN),false);
            return;
        }
        com.devfarinsky.siegeoverhaul.camp.CampSabotage.retreatWave(level, state);
        announce(level.getServer(), state.teamKey, Component.literal(
                "Banner destroyed: the current wave retreats. Later waves still attack.")
                .withStyle(ChatFormatting.GREEN), true);
    }

    /**
     * Supply barrel. Drops a bonus stack of emeralds at the barrel position
     * so defenders who fight up the hill get concrete loot back.
     */
    private static void handleBarrelBroken(ServerLevel level, RaidSavedData.RaidState state) {
        int count = RaidConfig.CAMP_BONUS_LOOT_EMERALDS.get();
        if (count > 0) {
            BlockPos drop = state.barrelPos;
            ItemStack emeralds = new ItemStack(Items.EMERALD, count);
            net.minecraft.world.entity.item.ItemEntity entity =
                    new net.minecraft.world.entity.item.ItemEntity(level,
                            drop.getX() + 0.5D, drop.getY() + 0.5D, drop.getZ() + 0.5D, emeralds);
            entity.setDefaultPickUpDelay();
            level.addFreshEntity(entity);
        }
        state.barrelPos = null;
        announce(level.getServer(), state.teamKey, Component.literal(
                "The war camp supply barrel spills its cargo — emeralds scatter across the ground.")
                .withStyle(ChatFormatting.GOLD), false);
    }

    // ---------------------------------------------------------------------
    // Command delegators.
    //
    // The Brigadier tree lives in command.RaidCommands. These package-visible
    // wrappers keep every command entry point in one obvious block and let the
    // handler bodies below stay unchanged until they are extracted into their
    // own handler classes in a later pass.
    // ---------------------------------------------------------------------
    public static int openDashboardCmd(CommandSourceStack s) { return openDashboard(s); }
    public static int setAnchorCmd(CommandSourceStack s) { return setAnchor(s); }
    public static int claimLegacyAnchorCmd(CommandSourceStack s) { return claimLegacyAnchor(s); }
    public static int removeAnchorCmd(CommandSourceStack s) { return removeAnchor(s); }
    public static int enableAutomaticHomeCmd(CommandSourceStack s) { return enableAutomaticHome(s); }
    public static int refreshAutomaticHomeCmd(CommandSourceStack s) { return refreshAutomaticHome(s); }
    public static int addDefensePointCmd(CommandSourceStack s, String n) { return addDefensePoint(s, n); }
    public static int removeDefensePointCmd(CommandSourceStack s, String n) { return removeDefensePoint(s, n); }
    public static int listDefensePointsCmd(CommandSourceStack s) { return listDefensePoints(s); }
    public static int addMemberCmd(CommandSourceStack s, ServerPlayer p) { return addMember(s, p); }
    public static int removeMemberCmd(CommandSourceStack s, ServerPlayer p) { return removeMember(s, p); }
    public static int listMembersCmd(CommandSourceStack s) { return listMembers(s); }
    public static int startOwnRaidCmd(CommandSourceStack s, String p) { return startOwnRaid(s, p); }
    public static int stopOwnRaidCmd(CommandSourceStack s) { return stopOwnRaid(s); }
    public static int statusCmd(CommandSourceStack s) { return status(s); }
    public static int debugCmd(CommandSourceStack s) { return debug(s); }
    public static int helpCmd(CommandSourceStack s) { return help(s); }
    public static int bookCmd(CommandSourceStack s) { return giveGuidebook(s); }
    public static int claimSpoilsCmd(CommandSourceStack s) { return claimSpoils(s); }
    public static int notifyOnCmd(CommandSourceStack s) { return setRaidNotify(s, true); }
    public static int notifyOffCmd(CommandSourceStack s) { return setRaidNotify(s, false); }
    public static int compatDiagCmd(CommandSourceStack s) {
        // v3.3.0: single-line status for the three claim providers plus the
        // difficulty and civilian-allowlist settings, so users can verify
        // their config is being read correctly.
        String claims = com.devfarinsky.siegeoverhaul.compat.ClaimBridge.diagnosticStatus();
        String scaling = String.format("HP x%.2f | DMG x%.2f | XP x%.2f",
                RaidConfig.RAIDER_HEALTH_MULTIPLIER.get(),
                RaidConfig.RAIDER_DAMAGE_MULTIPLIER.get(),
                RaidConfig.RAIDER_XP_MULTIPLIER.get());
        int allowlist = RaidConfig.CIVILIAN_MOB_ALLOWLIST.get().size();
        s.sendSuccess(() -> Component.literal("[SiegeOverhaul] Claim providers: " + claims
                + " | Scaling: " + scaling
                + " | Civilian allowlist entries: " + allowlist).withStyle(ChatFormatting.GRAY), false);
        return 1;
    }
    public static int adminListCmd(CommandSourceStack s) { return adminList(s); }
    public static int adminStopCmd(CommandSourceStack s, String k) { return adminStop(s, k); }
    public static int adminRemoveCmd(CommandSourceStack s, String k) { return adminRemove(s, k); }
    public static int adminRepairCmd(CommandSourceStack s, String k) { return adminRepair(s, k); }
    public static int adminSkipScoutCmd(CommandSourceStack s, String k) { return adminSkipScout(s, k); }

    private static int setAnchor(CommandSourceStack source) {
        try {
            ServerPlayer player = source.getPlayerOrException();
            RaidSavedData data = RaidSavedData.get(source.getServer());
            String key = factionKeyForPlayer(data, player);
            String display = teamDisplay(player);
            RaidSavedData.Anchor existing = data.anchors.get(key);
            if (data.raids.containsKey(key)) {
                source.sendFailure(Component.literal("Stop the active invasion before moving this faction's anchor."));
                return 0;
            }
            if (existing != null && !canManage(player, existing)) {
                source.sendFailure(Component.literal("Only the anchor owner or an operator can move this faction's anchor."));
                return 0;
            }
            long next = source.getServer().overworld().getGameTime() + randomCooldownTicks(source.getServer().overworld().random);
            RaidSavedData.DefensePoint home = new RaidSavedData.DefensePoint(RaidSavedData.HOME_POINT,
                    player.level().dimension().location(), player.blockPosition());
            RaidSavedData.Anchor updated;
            if (existing == null) {
                Set<UUID> members = seedRoster(source.getServer(), player);
                Map<String, RaidSavedData.DefensePoint> points = new LinkedHashMap<>();
                points.put(RaidSavedData.HOME_POINT, home);
                updated = new RaidSavedData.Anchor(key, display, player.getUUID(), members,
                        true, false, points, next);
            } else {
                updated = existing.withIdentity(key, display).withPoint(home)
                        .withAutomaticHome(false).withNextRaid(next);
            }
            data.anchors.put(key, updated);
            data.setDirty();
            source.sendSuccess(() -> Component.literal("Faction raid anchor set at " + formatPos(player.blockPosition()) +
                    ". Enemy invasions will target your faction players here.").withStyle(ChatFormatting.GREEN), false);
            return 1;
        } catch (Exception e) {
            source.sendFailure(Component.literal("Only a player can set a faction raid anchor."));
            return 0;
        }
    }

    private static int enableAutomaticHome(CommandSourceStack source) {
        return refreshAutomaticHome(source);
    }

    private static int refreshAutomaticHome(CommandSourceStack source) {
        try {
            ServerPlayer player = source.getPlayerOrException();
            var point = com.devfarinsky.siegeoverhaul.core.SiegeCore.point(source.getServer(), teamKey(player));
            if (point == null) { source.sendFailure(Component.literal("Place a Siege Core in your faction's Recruits claim first.")); return 0; }
            source.sendSuccess(() -> Component.literal("Siege Core at " + formatPos(point.pos()) + ". Beds do not move this objective."), false);
            return 1;
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException ex) { source.sendFailure(Component.literal("Only players can inspect their core.")); return 0; }
    }

    private static int claimLegacyAnchor(CommandSourceStack source) {
        try {
            ServerPlayer player = source.getPlayerOrException();
            RaidSavedData data = RaidSavedData.get(source.getServer());
            String key = factionKeyForPlayer(data, player);
            RaidSavedData.Anchor anchor = data.anchors.get(key);
            if (anchor == null) {
                source.sendFailure(Component.literal("Your faction does not have an anchor."));
                return 0;
            }
            if (!RaidSavedData.UNKNOWN_OWNER.equals(anchor.ownerUuid())) {
                source.sendFailure(Component.literal("This anchor already has an owner."));
                return 0;
            }
            data.anchors.put(key, anchor.withOwner(player.getUUID()));
            data.setDirty();
            source.sendSuccess(() -> Component.literal("You now manage this legacy faction anchor.")
                    .withStyle(ChatFormatting.GREEN), false);
            return 1;
        } catch (Exception e) {
            source.sendFailure(Component.literal("Only a player can claim a legacy anchor."));
            return 0;
        }
    }

    private static int removeAnchor(CommandSourceStack source) {
        try {
            ServerPlayer player = source.getPlayerOrException();
            RaidSavedData data = RaidSavedData.get(source.getServer());
            String key = factionKeyForPlayer(data, player);
            RaidSavedData.Anchor anchor = data.anchors.get(key);
            if (anchor == null) {
                source.sendFailure(Component.literal("Your faction does not have a raid anchor."));
                return 0;
            }
            if (!canManage(player, anchor)) {
                source.sendFailure(Component.literal("Only the anchor owner or an operator can remove it."));
                return 0;
            }
            if (data.raids.containsKey(key)) {
                source.sendFailure(Component.literal("Stop the active invasion before removing this faction's anchor."));
                return 0;
            }
            data.anchors.remove(key);
            data.setDirty();
            source.sendSuccess(() -> Component.literal("Faction raid anchor removed.").withStyle(ChatFormatting.YELLOW), false);
            return 1;
        } catch (Exception e) {
            source.sendFailure(Component.literal("Only a player can remove a faction raid anchor."));
            return 0;
        }
    }

    private static int addDefensePoint(CommandSourceStack source, String suppliedName) {
        try {
            ServerPlayer player = source.getPlayerOrException();
            RaidSavedData data = RaidSavedData.get(source.getServer());
            String key = factionKeyForPlayer(data, player);
            RaidSavedData.Anchor anchor = data.anchors.get(key);
            if (anchor == null) {
                source.sendFailure(Component.literal("Set your home anchor first with /siegeoverhaul anchor set"));
                return 0;
            }
            if (!canManage(player, anchor)) {
                source.sendFailure(Component.literal("Only the anchor owner or an operator can manage defense points."));
                return 0;
            }
            if (data.raids.containsKey(key)) {
                source.sendFailure(Component.literal("Stop the active invasion before changing defense points."));
                return 0;
            }
            String name = normalizePointName(suppliedName);
            if (name == null || RaidSavedData.HOME_POINT.equals(name)) {
                source.sendFailure(Component.literal("Use a 1–24 character name other than 'home'."));
                return 0;
            }
            if (!anchor.defensePoints().containsKey(name) &&
                    anchor.defensePoints().size() >= RaidConfig.MAX_DEFENSE_POINTS.get()) {
                source.sendFailure(Component.literal("This faction has reached its defense-point limit."));
                return 0;
            }
            RaidSavedData.DefensePoint point = new RaidSavedData.DefensePoint(name,
                    player.level().dimension().location(), player.blockPosition());
            data.anchors.put(key, anchor.withPoint(point));
            data.setDirty();
            source.sendSuccess(() -> Component.literal("Defense point '" + name + "' saved at " +
                    formatPos(point.pos()) + ".").withStyle(ChatFormatting.GREEN), false);
            return 1;
        } catch (Exception e) {
            source.sendFailure(Component.literal("Only a player can add a defense point."));
            return 0;
        }
    }

    private static int removeDefensePoint(CommandSourceStack source, String suppliedName) {
        try {
            ServerPlayer player = source.getPlayerOrException();
            RaidSavedData data = RaidSavedData.get(source.getServer());
            String key = factionKeyForPlayer(data, player);
            RaidSavedData.Anchor anchor = data.anchors.get(key);
            if (anchor == null) {
                source.sendFailure(Component.literal("Your faction does not have an anchor."));
                return 0;
            }
            if (!canManage(player, anchor)) {
                source.sendFailure(Component.literal("Only the anchor owner or an operator can manage defense points."));
                return 0;
            }
            if (data.raids.containsKey(key)) {
                source.sendFailure(Component.literal("Stop the active invasion before changing defense points."));
                return 0;
            }
            String name = normalizePointName(suppliedName);
            if (RaidSavedData.HOME_POINT.equals(name)) {
                source.sendFailure(Component.literal("The home point is moved with /siegeoverhaul anchor set, not removed."));
                return 0;
            }
            if (name == null || !anchor.defensePoints().containsKey(name)) {
                source.sendFailure(Component.literal("No defense point named '" + suppliedName + "'."));
                return 0;
            }
            data.anchors.put(key, anchor.withoutPoint(name));
            data.setDirty();
            source.sendSuccess(() -> Component.literal("Defense point '" + name + "' removed.")
                    .withStyle(ChatFormatting.YELLOW), false);
            return 1;
        } catch (Exception e) {
            source.sendFailure(Component.literal("Only a player can remove a defense point."));
            return 0;
        }
    }

    private static int listDefensePoints(CommandSourceStack source) {
        try {
            ServerPlayer player = source.getPlayerOrException();
            RaidSavedData data = RaidSavedData.get(source.getServer());
            RaidSavedData.Anchor anchor = data.anchors.get(factionKeyForPlayer(data, player));
            if (anchor == null) {
                source.sendFailure(Component.literal("Your faction does not have an anchor."));
                return 0;
            }
            source.sendSuccess(() -> Component.literal("Defense points for " + anchor.teamDisplay() + ":")
                    .withStyle(ChatFormatting.AQUA), false);
            anchor.defensePoints().values().forEach(point -> source.sendSuccess(() -> Component.literal(
                    "• " + point.name() + " — " + point.dimension() + " at " + formatPos(point.pos())), false));
            return anchor.defensePoints().size();
        } catch (Exception e) {
            source.sendFailure(Component.literal("Only a player can list defense points."));
            return 0;
        }
    }

    private static int addMember(CommandSourceStack source, ServerPlayer target) {
        try {
            ServerPlayer player = source.getPlayerOrException();
            RaidSavedData data = RaidSavedData.get(source.getServer());
            String key = factionKeyForPlayer(data, player);
            RaidSavedData.Anchor anchor = data.anchors.get(key);
            if (anchor == null) {
                source.sendFailure(Component.literal("Set your faction anchor before creating a roster."));
                return 0;
            }
            if (!canManage(player, anchor)) {
                source.sendFailure(Component.literal("Only the anchor owner or an operator can manage the roster."));
                return 0;
            }
            String otherKey = associatedAnchorKeyForPlayer(data, target.getUUID());
            if (otherKey != null && !otherKey.equals(key)) {
                source.sendFailure(Component.literal(target.getGameProfile().getName() +
                        " already belongs to another Faction Raids roster."));
                return 0;
            }
            Set<UUID> members = anchor.internalRoster() ? new LinkedHashSet<>(anchor.members()) :
                    seedLegacyRoster(source.getServer(), anchor, player);
            if (!members.contains(target.getUUID()) && members.size() >= RaidConfig.MAX_ROSTER_MEMBERS.get()) {
                source.sendFailure(Component.literal("This faction has reached its roster limit."));
                return 0;
            }
            members.add(target.getUUID());
            data.anchors.put(key, anchor.withRoster(members, true));
            data.setDirty();
            source.sendSuccess(() -> Component.literal(target.getGameProfile().getName() +
                    " added to the Faction Raids roster.").withStyle(ChatFormatting.GREEN), false);
            target.sendSystemMessage(Component.literal("You joined " + anchor.teamDisplay() +
                    "'s Faction Raids roster.").withStyle(ChatFormatting.AQUA));
            return 1;
        } catch (Exception e) {
            source.sendFailure(Component.literal("A player must manage the faction roster."));
            return 0;
        }
    }

    private static int removeMember(CommandSourceStack source, ServerPlayer target) {
        try {
            ServerPlayer player = source.getPlayerOrException();
            RaidSavedData data = RaidSavedData.get(source.getServer());
            String key = factionKeyForPlayer(data, player);
            RaidSavedData.Anchor anchor = data.anchors.get(key);
            if (anchor == null || !anchor.internalRoster()) {
                source.sendFailure(Component.literal("Your faction is not using an internal roster yet."));
                return 0;
            }
            if (!canManage(player, anchor)) {
                source.sendFailure(Component.literal("Only the anchor owner or an operator can manage the roster."));
                return 0;
            }
            if (target.getUUID().equals(anchor.ownerUuid())) {
                source.sendFailure(Component.literal("The anchor owner cannot be removed from the roster."));
                return 0;
            }
            Set<UUID> members = new LinkedHashSet<>(anchor.members());
            if (!members.remove(target.getUUID())) {
                source.sendFailure(Component.literal(target.getGameProfile().getName() + " is not on this roster."));
                return 0;
            }
            data.anchors.put(key, anchor.withRoster(members, true));
            data.setDirty();
            source.sendSuccess(() -> Component.literal(target.getGameProfile().getName() +
                    " removed from the Faction Raids roster.").withStyle(ChatFormatting.YELLOW), false);
            return 1;
        } catch (Exception e) {
            source.sendFailure(Component.literal("A player must manage the faction roster."));
            return 0;
        }
    }

    private static int listMembers(CommandSourceStack source) {
        try {
            ServerPlayer player = source.getPlayerOrException();
            RaidSavedData data = RaidSavedData.get(source.getServer());
            String key = factionKeyForPlayer(data, player);
            RaidSavedData.Anchor anchor = data.anchors.get(key);
            if (anchor == null) {
                source.sendFailure(Component.literal("Your faction does not have an anchor."));
                return 0;
            }
            List<ServerPlayer> online = onlineMembers(source.getServer(), key);
            source.sendSuccess(() -> Component.literal(anchor.internalRoster() ?
                    "Internal roster: " + anchor.members().size() + " members, " + online.size() + " online." :
                    "Legacy scoreboard roster: " + online.size() + " members currently online.")
                    .withStyle(ChatFormatting.AQUA), false);
            if (anchor.internalRoster()) {
                anchor.members().forEach(id -> source.sendSuccess(() -> Component.literal("• " +
                        playerName(source.getServer(), id) + (id.equals(anchor.ownerUuid()) ? " [owner]" : "") +
                        (source.getServer().getPlayerList().getPlayer(id) != null ? " [online]" : "")), false));
            } else {
                online.forEach(member -> source.sendSuccess(() -> Component.literal("• " +
                        member.getGameProfile().getName() + " [online]"), false));
            }
            return anchor.internalRoster() ? anchor.members().size() : online.size();
        } catch (Exception e) {
            source.sendFailure(Component.literal("Only a player can list faction members."));
            return 0;
        }
    }

    private static int startOwnRaid(CommandSourceStack source, String suppliedPoint) {
        try {
            ServerPlayer player = source.getPlayerOrException();
            RaidSavedData data = RaidSavedData.get(source.getServer());
            String key = factionKeyForPlayer(data, player);
            RaidSavedData.Anchor anchor = data.anchors.get(key);
            if (anchor == null) {
                syncAutomaticHome(source.getServer(), data, player, true);
                key = teamKey(player);
                anchor = data.anchors.get(key);
                if (anchor == null) {
                    source.sendFailure(Component.literal("Your automatic stronghold could not be created."));
                    return 0;
                }
            }
            if (!canManage(player, anchor)) {
                source.sendFailure(Component.literal("Only the anchor owner or an operator can manually start an invasion."));
                return 0;
            }
            if (data.raids.containsKey(key)) {
                source.sendFailure(Component.literal("Your faction already has an active invasion."));
                return 0;
            }
            RaidSavedData.DefensePoint point;
            if (suppliedPoint != null) {
                String pointName = normalizePointName(suppliedPoint);
                point = pointName == null ? null : anchor.defensePoints().get(pointName);
                if (point == null) {
                    source.sendFailure(Component.literal("Unknown defense point. Use /siegeoverhaul territory list"));
                    return 0;
                }
            } else {
                point = anchor.automaticHome() ? respawnPoint(source.getServer(), player) :
                        closestDefensePoint(source.getServer(), anchor, player);
            }
            if (point == null) {
                source.sendFailure(Component.literal("Place a Siege Core in your faction's Recruits claim before starting an invasion."));
                return 0;
            }
            if (!hasDefenderNear(source.getServer(), point, onlineMembers(source.getServer(), key))) {
                source.sendFailure(Component.literal("Stand near the selected defense point before starting the invasion."));
                return 0;
            }
            if (!beginRaid(source.getServer(), data, anchor, point,
                    RaidConfig.MANUAL_RAIDS_GRANT_REWARDS.get())) {
                source.sendFailure(Component.literal("The server has reached its configured concurrent raid limit."));
                return 0;
            }
            return 1;
        } catch (Exception e) {
            source.sendFailure(Component.literal("Only a player can start their faction invasion."));
            return 0;
        }
    }

    private static int stopOwnRaid(CommandSourceStack source) {
        try {
            ServerPlayer player = source.getPlayerOrException();
            RaidSavedData data = RaidSavedData.get(source.getServer());
            String key = factionKeyForPlayer(data, player);
            if (!data.raids.containsKey(key)) {
                source.sendFailure(Component.literal("Your faction has no active invasion."));
                return 0;
            }
            finishRaid(source.getServer(), data, key, false, false,
                    "The invasion was stopped by an administrator.");
            return 1;
        } catch (Exception e) {
            source.sendFailure(Component.literal("Only a player can stop their faction invasion."));
            return 0;
        }
    }

    private static int status(CommandSourceStack source) {
        try {
            ServerPlayer player = source.getPlayerOrException();
            RaidSavedData data = RaidSavedData.get(source.getServer());
            String key = factionKeyForPlayer(data, player);
            RaidSavedData.Anchor anchor = data.anchors.get(key);
            if (anchor == null) {
                source.sendSuccess(() -> MESSAGE_PREFIX.copy().append(Component.literal("No stronghold registered")
                        .withStyle(ChatFormatting.YELLOW)), false);
                source.sendSuccess(() -> Component.literal("Place a Siege Core in your faction's Recruits claim."), false);
                return 1;
            }
            RaidSavedData.RaidState state = data.raids.get(key);
            source.sendSuccess(() -> MESSAGE_PREFIX.copy().append(Component.literal(anchor.teamDisplay())
                    .withStyle(ChatFormatting.GOLD)), false);
            // v4.28.8: treasury balance + next interest payout (game-time).
            var core = data.siegeCores.get(key);
            if (core != null) {
                long balance = FactionBank.balance(core);
                int rateBp = RaidConfig.BANK_INTEREST_BASIS_POINTS.get();
                long dailyInterest = balance * rateBp / 10000L;
                long ticks = FactionBank.ticksUntilInterest(core, source.getServer().overworld().getGameTime());
                String countdown = formatInterestCountdown(ticks);
                source.sendSuccess(() -> Component.literal("Treasury: ").withStyle(ChatFormatting.GRAY)
                        .append(Component.literal(String.format(java.util.Locale.ROOT,
                                "%,d emeralds • +%,d interest in %s (in-game time)",
                                balance, dailyInterest, countdown))
                                .withStyle(ChatFormatting.GREEN)), false);
            }
            if (state != null) {
                RaidSavedData.DefensePoint point = anchor.point(state.defensePointName);
                ServerLevel raidLevel = getLevel(source.getServer(), point);
                int occupation = state.captureTicks * 100 /
                        Math.max(1, RaidConfig.CAPTURE_TIME_SECONDS.get() * 20);
                int breach = breachPercent(state);
                source.sendSuccess(() -> Component.literal("Phase: ").withStyle(ChatFormatting.GRAY)
                        .append(Component.literal(state.wave == 0 ? "War camp forming" :
                                (!state.breached && RaidConfig.ENABLE_BREACH_PHASE.get() ?
                                        "Perimeter breach " + breach + "% — wave " + state.wave + (EndlessSiege.active(state) ? " (endless)" : "/" + RaidConfig.WAVES.get()) :
                                        waveTitle(EndlessSiege.active(state) ? EndlessSiege.chapterWave(state.wave) : state.wave) + " — wave " + state.wave + (EndlessSiege.active(state) ? " (endless)" : "/" + RaidConfig.WAVES.get())))
                                .withStyle(ChatFormatting.RED)), false);
                source.sendSuccess(() -> Component.literal("Enemy force: ").withStyle(ChatFormatting.GRAY)
                        .append(Component.literal(state.raiders.size() + " deployed • " +
                                state.pendingWaveSpawns + " reinforcing • " + state.totalDefeated + " defeated")
                                .withStyle(ChatFormatting.YELLOW)), false);
                source.sendSuccess(() -> Component.literal("War camp: ").withStyle(ChatFormatting.GRAY)
                        .append(Component.literal(state.campPos == null ? "No safe camp site was available" :
                                formatPos(state.campPos)).withStyle(state.campPos == null ?
                                ChatFormatting.YELLOW : ChatFormatting.RED)), false);
                source.sendSuccess(() -> Component.literal("Physical breach: ").withStyle(ChatFormatting.GRAY)
                        .append(Component.literal(state.currentBreachBlock == null ?
                                state.breachedBlocks.size() + " defense block(s) awaiting repair" :
                                formatPos(state.currentBreachBlock) + " • " + gateBreachPercent(state) + "% • " +
                                        state.breachedBlocks.size() + " awaiting repair")
                                .withStyle(state.currentBreachBlock == null ? ChatFormatting.GREEN :
                                        ChatFormatting.RED)), false);
                source.sendSuccess(() -> Component.literal("Objective: ").withStyle(ChatFormatting.GRAY)
                        .append(Component.literal("'" + point.name() + "' at " + formatPos(point.pos()) +
                                (state.breached || !RaidConfig.ENABLE_BREACH_PHASE.get() ?
                                        " • occupation " + occupation + "%" : " • marked breach point " +
                                        (raidLevel == null ? "unavailable" :
                                                formatVec(invasionBreachObjective(raidLevel, point, state)))))
                                .withStyle(occupation >= 75 ? ChatFormatting.RED : ChatFormatting.AQUA)), false);
            } else {
                long now = source.getServer().overworld().getGameTime();
                long seconds = Math.max(0L, (anchor.nextRaidGameTime() - now) / 20L);
                source.sendSuccess(() -> Component.literal("Stronghold: ").withStyle(ChatFormatting.GRAY)
                        .append(Component.literal((anchor.automaticHome() ? "automatic" : "manual") + " at " +
                                formatPos(anchor.primaryPoint().pos())).withStyle(ChatFormatting.AQUA)), false);
                source.sendSuccess(() -> Component.literal("Defenses: ").withStyle(ChatFormatting.GRAY)
                        .append(Component.literal(anchor.defensePoints().size() + " registered location(s) • " +
                                anchor.members().size() + " saved member(s)").withStyle(ChatFormatting.WHITE)), false);
                source.sendSuccess(() -> Component.literal("Next siege eligible: ").withStyle(ChatFormatting.GRAY)
                        .append(Component.literal(formatTime(seconds)).withStyle(ChatFormatting.GREEN)), false);
            }
            return 1;
        } catch (Exception e) {
            source.sendFailure(Component.literal("Only a player can inspect faction raid status."));
            return 0;
        }
    }

    private static int adminList(CommandSourceStack source) {
        RaidSavedData data = RaidSavedData.get(source.getServer());
        source.sendSuccess(() -> Component.literal("Faction Raids anchors: " + data.anchors.size() +
                ", active invasions: " + data.raids.size()).withStyle(ChatFormatting.AQUA), false);
        data.anchors.values().stream().sorted(Comparator.comparing(RaidSavedData.Anchor::teamKey)).forEach(anchor ->
                source.sendSuccess(() -> Component.literal(anchor.teamKey() + " — " + anchor.teamDisplay() + " — " +
                        anchor.defensePoints().size() + " point(s), " + anchor.members().size() + " saved member(s)" +
                        (data.raids.containsKey(anchor.teamKey()) ? " [ACTIVE]" : "")), false));
        return data.anchors.size();
    }

    private static int help(CommandSourceStack source) {
        source.sendSuccess(() -> MESSAGE_PREFIX.copy().append(Component.literal("Player commands")
                .withStyle(ChatFormatting.GOLD)), false);
        source.sendSuccess(() -> Component.literal("/siegeoverhaul status").withStyle(ChatFormatting.AQUA)
                .append(Component.literal(" — stronghold and siege status")), false);
        source.sendSuccess(() -> Component.literal("/siegeoverhaul start").withStyle(ChatFormatting.AQUA)
                .append(Component.literal(" — begin a controlled siege test")), false);
        source.sendSuccess(() -> Component.literal("/siegeoverhaul home refresh").withStyle(ChatFormatting.AQUA)
                .append(Component.literal(" — check your faction Siege Core")), false);
        source.sendSuccess(() -> Component.literal("/siegeoverhaul territory list").withStyle(ChatFormatting.AQUA)
                .append(Component.literal(" — list every defended location")), false);
        source.sendSuccess(() -> Component.literal("/siegeoverhaul debug").withStyle(ChatFormatting.AQUA)
                .append(Component.literal(" — show faction integration and performance details")), false);
        source.sendSuccess(() -> Component.literal("/siegeoverhaul book").withStyle(ChatFormatting.AQUA)
                .append(Component.literal(" — get a Warlord's Codex if you lost yours")), false);
        source.sendSuccess(() -> Component.literal("/siegeoverhaul claim").withStyle(ChatFormatting.AQUA)
                .append(Component.literal(" — claim rewards from sieges you missed")), false);
        source.sendSuccess(() -> Component.literal("/siegeoverhaul notify on|off").withStyle(ChatFormatting.AQUA)
                .append(Component.literal(" — hear about other factions' sieges on this server")), false);
        return 1;
    }

    /**
     * v3.2.0 — /siegeoverhaul book. Rate-limited helper that gives the
     * caller a Warlord's Codex if they don't already have one in their
     * inventory or ender chest. Fixes the losing-the-book problem on
     * multiplayer servers where non-op players had no recovery path.
     */
    private static final java.util.Map<java.util.UUID, Long> BOOK_COOLDOWN = new java.util.concurrent.ConcurrentHashMap<>();
    private static final long BOOK_COOLDOWN_TICKS = 1200L; // 60 seconds

    private static int giveGuidebook(CommandSourceStack source) {
        try {
            ServerPlayer player = source.getPlayerOrException();
            net.minecraft.world.item.Item book = com.devfarinsky.siegeoverhaul.items.ModItems.GUIDEBOOK.get();
            if (playerHasGuidebook(player, book)) {
                source.sendFailure(Component.literal("You already have a Warlord's Codex."));
                return 0;
            }
            long now = source.getServer().overworld().getGameTime();
            Long last = BOOK_COOLDOWN.get(player.getUUID());
            if (last != null && now - last < BOOK_COOLDOWN_TICKS) {
                long remain = Math.max(1L, (BOOK_COOLDOWN_TICKS - (now - last)) / 20L);
                source.sendFailure(Component.literal("Wait " + remain + "s before requesting another."));
                return 0;
            }
            net.minecraft.world.item.ItemStack stack = new net.minecraft.world.item.ItemStack(book);
            if (!player.getInventory().add(stack)) player.drop(stack, false);
            BOOK_COOLDOWN.put(player.getUUID(), now);
            source.sendSuccess(() -> Component.literal("A Warlord's Codex materializes in your inventory.")
                    .withStyle(ChatFormatting.GREEN), false);
            return 1;
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) {
            source.sendFailure(Component.literal("Only a player can request a Codex."));
            return 0;
        }
    }

    private static boolean playerHasGuidebook(ServerPlayer player, net.minecraft.world.item.Item book) {
        for (net.minecraft.world.item.ItemStack s : player.getInventory().items) {
            if (!s.isEmpty() && s.getItem() == book) return true;
        }
        for (net.minecraft.world.item.ItemStack s : player.getInventory().offhand) {
            if (!s.isEmpty() && s.getItem() == book) return true;
        }
        for (int i = 0; i < player.getEnderChestInventory().getContainerSize(); i++) {
            net.minecraft.world.item.ItemStack s = player.getEnderChestInventory().getItem(i);
            if (!s.isEmpty() && s.getItem() == book) return true;
        }
        return false;
    }

    /**
     * v3.2.0 — /siegeoverhaul claim. Grants any spoils queued for the caller
     * while they were offline during a victorious siege.
     */
    private static int claimSpoils(CommandSourceStack source) {
        try {
            ServerPlayer player = source.getPlayerOrException();
            RaidSavedData data = RaidSavedData.get(source.getServer());
            java.util.List<RaidSavedData.UnclaimedSpoils> queued = data.pendingSpoils.remove(player.getUUID());
            if (queued == null || queued.isEmpty()) {
                source.sendFailure(Component.literal("No unclaimed spoils."));
                return 0;
            }
            int totalEmeralds = 0;
            int totalXp = 0;
            int lootRolls = 0;
            int trophies = 0;
            for (RaidSavedData.UnclaimedSpoils spoils : queued) {
                if (spoils.emeralds() > 0) { giveEmeralds(player, spoils.emeralds()); totalEmeralds += spoils.emeralds(); }
                if (spoils.experience() > 0) { player.giveExperiencePoints(spoils.experience()); totalXp += spoils.experience(); }
                if (spoils.lootRoll()) { giveVictoryLoot(source.getServer(), player); lootRolls++; }
                if (spoils.factionId() != null && !spoils.factionId().isEmpty()) {
                    com.devfarinsky.siegeoverhaul.items.FactionBanners.FactionId trophy =
                            com.devfarinsky.siegeoverhaul.items.FactionBanners.FactionId.byIdOrDefault(spoils.factionId());
                    net.minecraft.world.item.ItemStack banner =
                            com.devfarinsky.siegeoverhaul.items.FactionBanners.itemStackFor(trophy);
                    net.minecraftforge.items.ItemHandlerHelper.giveItemToPlayer(player, banner);
                    trophies++;
                }
            }
            data.setDirty();
            int emeraldsFinal = totalEmeralds;
            int xpFinal = totalXp;
            int lootFinal = lootRolls;
            int trophyFinal = trophies;
            int sieges = queued.size();
            source.sendSuccess(() -> Component.literal("Claimed spoils from " + sieges + " siege" +
                    (sieges == 1 ? "" : "s") + ": " + emeraldsFinal + " emeralds, " +
                    xpFinal + " XP, " + lootFinal + " loot roll" + (lootFinal == 1 ? "" : "s") +
                    ", " + trophyFinal + " trophy banner" + (trophyFinal == 1 ? "" : "s") + ".")
                    .withStyle(ChatFormatting.GREEN), false);
            return sieges;
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) {
            source.sendFailure(Component.literal("Only a player can claim spoils."));
            return 0;
        }
    }

    /**
     * v3.2.0 — /siegeoverhaul notify on|off. Sets whether the caller sees
     * server-wide chat notifications when other factions' sieges begin.
     */
    private static int setRaidNotify(CommandSourceStack source, boolean enabled) {
        try {
            ServerPlayer player = source.getPlayerOrException();
            RaidSavedData data = RaidSavedData.get(source.getServer());
            if (enabled) data.raidNotifyOptOut.remove(player.getUUID());
            else data.raidNotifyOptOut.add(player.getUUID());
            data.setDirty();
            source.sendSuccess(() -> Component.literal(enabled ?
                    "Server-wide raid alerts: ON." : "Server-wide raid alerts: OFF.")
                    .withStyle(ChatFormatting.GREEN), false);
            return 1;
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) {
            source.sendFailure(Component.literal("Only a player can change notification preferences."));
            return 0;
        }
    }

    private static int debug(CommandSourceStack source) {
        try {
            ServerPlayer player = source.getPlayerOrException();
            RaidSavedData data = RaidSavedData.get(source.getServer());
            String key = factionKeyForPlayer(data, player);
            RaidSavedData.Anchor anchor = data.anchors.get(key);
            if (anchor == null) {
                source.sendFailure(Component.literal("No Faction Raids anchor is associated with you."));
                return 0;
            }
            List<ServerPlayer> online = onlineMembers(source.getServer(), key);
            double tps = approximateTps(source.getServer());
            source.sendSuccess(() -> Component.literal("Faction Raids diagnostic for " + anchor.teamDisplay())
                    .withStyle(ChatFormatting.AQUA), false);
            source.sendSuccess(() -> Component.literal("Key: " + key + " | owner: " +
                    playerName(source.getServer(), anchor.ownerUuid())), false);
            source.sendSuccess(() -> Component.literal("Roster: " + (anchor.internalRoster() ? "internal" : "scoreboard fallback") +
                    " | saved: " + anchor.members().size() + " | online: " + online.stream()
                    .map(p -> p.getGameProfile().getName()).toList()), false);
            source.sendSuccess(() -> Component.literal("Villager Recruits integration: " +
                    RecruitsBridge.diagnosticStatus()), false);
            source.sendSuccess(() -> Component.literal("Optional integrations: " +
                    OptionalCompatBridge.diagnosticStatus()), false);
            source.sendSuccess(() -> Component.literal(String.format(Locale.ROOT,
                    "Defense points: %d | approximate TPS: %.1f | global tracked raiders: %d/%d",
                    anchor.defensePoints().size(), tps, globalTrackedCount(data), RaidConfig.MAX_GLOBAL_RAIDERS.get())), false);
            RaidSavedData.RaidState state = data.raids.get(key);
            if (state == null) {
                source.sendSuccess(() -> Component.literal("Raid state: inactive"), false);
            } else {
                long loaded = state.raiders.stream().filter(id -> {
                    RaidSavedData.DefensePoint point = anchor.point(state.defensePointName);
                    ServerLevel level = getLevel(source.getServer(), point);
                    return level != null && level.getEntity(id) != null;
                }).count();
                source.sendSuccess(() -> Component.literal("Raid state: wave " + state.wave + (EndlessSiege.active(state) ? " (endless)" : "/" + RaidConfig.WAVES.get()) +
                        " at '" + state.defensePointName + "' | tracked: " + state.raiders.size() +
                        " | queued: " + state.pendingWaveSpawns + " | squads: " + state.squadsSpawned +
                        " | loaded: " + loaded + " | missing grace: " + state.missingTicks.size() +
                        " | deployed/defeated/lost: " + state.totalSpawned + "/" +
                        state.totalDefeated + "/" + state.totalEscaped +
                        " | breach: " + (state.breached ? "open" : breachPercent(state) + "%") +
                        " | occupation: " + (state.captureTicks / 20) + "s" +
                        (state.commanderUuid != null ? " | commander: " +
                                (state.commanderDefeated ? "defeated" : "active") : "")), false);
                PathingTelemetry telemetry = PATHING_TELEMETRY.get(key);
                if (telemetry != null) {
                    source.sendSuccess(() -> Component.literal("Pathing telemetry: fallback search/cache " +
                            telemetry.fallbackSearches + "/" + telemetry.fallbackCacheHits +
                            " | stuck L1/L2/L3 " + telemetry.stuckEscalationsL1 + "/" +
                            telemetry.stuckEscalationsL2 + "/" + telemetry.stuckEscalationsL3 +
                            " | breach scan/reject " + telemetry.breachCandidatesScanned + "/" +
                            telemetry.breachCandidatesRejectedNoStand +
                            " | final-approach boost ticks " + telemetry.finalApproachBoostTicks), false);
                }
            }
            return 1;
        } catch (Exception e) {
            source.sendFailure(Component.literal("Only a player can inspect their faction diagnostic."));
            return 0;
        }
    }

    private static int adminStop(CommandSourceStack source, String suppliedKey) {
        RaidSavedData data = RaidSavedData.get(source.getServer());
        String key = normalizeTeamKey(data, suppliedKey);
        if (!data.raids.containsKey(key)) {
            source.sendFailure(Component.literal("No active invasion found for " + suppliedKey));
            return 0;
        }
        finishRaid(source.getServer(), data, key, false, false,
                "The invasion was stopped by an administrator.");
        return 1;
    }

    private static int adminRemove(CommandSourceStack source, String suppliedKey) {
        RaidSavedData data = RaidSavedData.get(source.getServer());
        String key = normalizeTeamKey(data, suppliedKey);
        if (data.raids.containsKey(key)) {
            source.sendFailure(Component.literal("Stop the active invasion before removing this anchor."));
            return 0;
        }
        if (data.anchors.remove(key) == null) {
            source.sendFailure(Component.literal("No anchor found for " + suppliedKey));
            return 0;
        }
        data.setDirty();
        source.sendSuccess(() -> Component.literal("Removed faction raid anchor " + key), true);
        return 1;
    }

    private static int adminRepair(CommandSourceStack source, String suppliedKey) {
        RaidSavedData data = RaidSavedData.get(source.getServer());
        String key = normalizeTeamKey(data, suppliedKey);
        RaidSavedData.Anchor anchor = data.anchors.get(key);
        RaidSavedData.RaidState state = data.raids.get(key);
        if (anchor == null || state == null) {
            source.sendFailure(Component.literal("No active invasion found for " + suppliedKey));
            return 0;
        }
        RaidSavedData.DefensePoint point = anchor.point(state.defensePointName);
        ServerLevel level = getLevel(source.getServer(), point);
        if (level == null) {
            source.sendFailure(Component.literal("The invasion dimension is not available."));
            return 0;
        }
        state.reconcileTicks = 0;
        reconcileTaggedMobs(level, point, state);
        updateTrackedMobs(level, state);
        for (UUID id : state.raiders) if (level.getEntity(id) instanceof Mob mob)
            com.devfarinsky.siegeoverhaul.items.FactionUniforms.apply(mob,state.factionId,mob.getPersistentData().getString(RAID_ROLE_TAG));
        data.setDirty();
        source.sendSuccess(() -> Component.literal("Reconciled invasion " + key + ": " +
                state.raiders.size() + " enemies tracked.").withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    /**
     * v4.35.0: unstick a raid that is stuck in the war-camp scouting phase.
     * Marks the raid as {@code campSearchAbandoned}, releases any pending
     * search ticket, and kicks the preparation timer so waves can begin
     * without a fortified camp. Use when island/coastal terrain or dense
     * player claims prevent any camp candidate from succeeding.
     */
    private static int adminSkipScout(CommandSourceStack source, String suppliedKey) {
        RaidSavedData data = RaidSavedData.get(source.getServer());
        String key = normalizeTeamKey(data, suppliedKey);
        RaidSavedData.Anchor anchor = data.anchors.get(key);
        RaidSavedData.RaidState state = data.raids.get(key);
        if (anchor == null || state == null) {
            source.sendFailure(Component.literal("No active invasion found for " + suppliedKey));
            return 0;
        }
        if (state.campPos != null) {
            source.sendFailure(Component.literal("Invasion " + key + " already has a camp; nothing to skip."));
            return 0;
        }
        RaidSavedData.DefensePoint point = anchor.point(state.defensePointName);
        ServerLevel level = getLevel(source.getServer(), point);
        if (state.campSearchPos != null && level != null) {
            com.devfarinsky.siegeoverhaul.camp.CampLoading.release(level, state.campSearchPos);
        }
        state.campSearchPos = null;
        state.campSearchTicks = 0;
        state.campSearchAbandoned = true;
        state.campBuildAttempted = true;
        state.preparationTotalTicks = RaidConfig.PREPARATION_MINUTES.get() * 1200;
        state.preparationTicks = state.preparationTotalTicks;
        state.ticksToNextWave = state.preparationTicks;
        data.setDirty();
        source.sendSuccess(() -> Component.literal("Invasion " + key +
                " scouting abandoned. Waves will spawn without a camp; preparation timer reset.")
                .withStyle(ChatFormatting.GOLD), true);
        return 1;
    }

    private static void tick(MinecraftServer server) {
        RaidSavedData data = RaidSavedData.get(server);
        if (RaidConfig.AUTOMATIC_PLAYER_HOMES.get()) {
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                syncAutomaticHome(server, data, player, false);
            }
        }
        com.devfarinsky.siegeoverhaul.core.CoreOccupation.tick(server, data);
        // v4.28.8: interest is now measured in game ticks so single-player
        // pausing doesn't rack up phantom interest. The tick-level settle
        // uses the base rate; the territory-provisioning buff is applied by
        // the two-arg settle(data,core) helper on HUD open and wave clears.
        long bankNow = server.overworld().getGameTime();
        int bankRate = RaidConfig.BANK_INTEREST_BASIS_POINTS.get();
        for (var core : data.siegeCores.values()) if (FactionBank.settle(core, bankNow, bankRate)) data.setDirty();
        // Core ownership follows the placing faction, not an individual changing teams.
        long now = server.overworld().getGameTime();
        if (now / 20 % 5 == 0) com.devfarinsky.siegeoverhaul.compat.CampClaims.cleanOrphans(server.overworld(), data);

        if (RaidConfig.AUTOMATIC_RAIDS.get() && data.raids.size() < RaidConfig.MAX_CONCURRENT_RAIDS.get()) {
            for (RaidSavedData.Anchor anchor : new ArrayList<>(data.anchors.values())) {
                if (data.raids.size() >= RaidConfig.MAX_CONCURRENT_RAIDS.get()) break;
                if (data.raids.containsKey(anchor.teamKey()) || now < anchor.nextRaidGameTime()) {
                    // Anchor is in cooldown: consider scheduling a scout mission.
                    // maybeSchedule is idempotent and cheap when already scheduled.
                    com.devfarinsky.siegeoverhaul.scout.ScoutManager.maybeSchedule(server, data, anchor);
                    continue;
                }
                List<ServerPlayer> members = onlineMembers(server, anchor.teamKey());
                if (members.isEmpty()) continue;
                RaidSavedData.DefensePoint point = selectAutomaticPoint(server, anchor, members);
                if (point == null) continue;
                beginRaid(server, data, anchor, point, true);
            }
        }
        // v2.26.0: tick pending scout missions independently of raid processing.
        // Runs even when AUTOMATIC_RAIDS is disabled so admins can /siegeoverhaul
        // start manually while scouts remain a background flavor system.
        com.devfarinsky.siegeoverhaul.scout.ScoutManager.tick(server, data);

        for (String teamKey : new ArrayList<>(data.raids.keySet())) processRaid(server, data, teamKey);
        data.setDirty();
    }

    /**
     * v2.30.0 Bridge Sieges (Path A) public entry point.
     *
     * <p>Called by {@code RecruitsSiegeBridge} when Recruits fires
     * {@code SiegeEvent.Start} on a claim whose owner faction id matches a
     * Faction Raids anchor's teamKey. Spawns a raid using the anchor's own
     * defense point closest to the claim center (falls back to any eligible
     * point). Idempotent: returns {@code false} without side effects when</p>
     * <ul>
     *   <li>No anchor exists for the given team key (Recruits faction not
     *       registered as a Faction Raids team).</li>
     *   <li>The team already has an active raid (dedupe).</li>
     *   <li>The server has reached its configured concurrent raid limit.</li>
     *   <li>The team has no online members near any defense point (guards
     *       against ambient sieges on empty claims triggering pointless raids).</li>
     * </ul>
     *
     * <p>Called from an off-tick Forge event handler, so all mutation goes
     * through the same {@code data.setDirty()}-guarded pipeline as the
     * regular tick trigger. Runs synchronously on the server thread because
     * Recruits fires SiegeEvent.Start on the server thread.</p>
     *
     * @param level        the world where the siege was declared
     * @param teamKey      Recruits claim owner faction string id (matches anchor teamKey)
     * @param claimCenter  the claim's center chunk (used to pick the closest defense point)
     * @return {@code true} when a raid was actually scheduled
     */
    public static boolean tryTriggerRaidForTeam(net.minecraft.server.level.ServerLevel level,
                                                String teamKey,
                                                net.minecraft.world.level.ChunkPos claimCenter) {
        if (level == null || teamKey == null || teamKey.isBlank()) return false;
        MinecraftServer server = level.getServer();
        if (server == null) return false;
        RaidSavedData data = RaidSavedData.get(server);
        teamKey = normalizeTeamKey(data, teamKey);
        // Dedupe: FR raid already in flight for this team.
        if (data.raids.containsKey(teamKey)) return false;
        RaidSavedData.Anchor anchor = data.anchors.get(teamKey);
        // No matching FR anchor for this Recruits faction — nothing to raid.
        if (anchor == null) return false;
        // Online-members gate. An ambient siege on an empty claim shouldn't
        // spawn a raid nobody will play.
        List<ServerPlayer> members = onlineMembers(server, teamKey);
        if (members.isEmpty()) return false;
        // Pick the anchor defense point closest to the claim center. This
        // usually places the raid right where the Recruits army already is.
        RaidSavedData.DefensePoint point = pickPointNearClaimCenter(anchor, claimCenter);
        if (point == null) {
            // Fall back to normal auto-selection when the geometry pick fails.
            point = selectAutomaticPoint(server, anchor, members);
        }
        if (point == null) return false;
        boolean ok = beginRaid(server, data, anchor, point,
                RaidConfig.MANUAL_RAIDS_GRANT_REWARDS.get());
        if (ok) data.setDirty();
        return ok;
    }

    /**
     * Find the anchor's defense point whose block-position chunk is nearest
     * to {@code claimCenter}. Returns null when the anchor has no defense
     * points at all.
     */
    private static RaidSavedData.DefensePoint pickPointNearClaimCenter(RaidSavedData.Anchor anchor,
                                                                        net.minecraft.wor…41527 tokens truncated…NE_FALLBACK_RADIUS.get();
        int vertical = RaidConfig.CONE_FALLBACK_VERTICAL.get();
        // Narrow cone first: vanilla's ~18-degree attempt at full radius.
        Vec3 narrow = DefaultRandomPos.getPosTowards(mob, radius, vertical,
                objective, 0.3141592741012573);
        if (narrow != null) return narrow;
        // Widen to a 90-degree cone at half radius. Vanilla drops radius
        // when widening because a wider cone at full radius tends to pick
        // points behind terrain features that the narrow attempt already
        // rejected. Half-radius keeps the retry local to the raider.
        int wideRadius = Math.max(4, radius / 2);
        return DefaultRandomPos.getPosTowards(mob, wideRadius, vertical,
                objective, 1.5707963705062866);
    }

    private static void updateStuckTracker(Mob mob, UUID id, String teamKey,
                                           Vec3 objective, double distToObjectiveSq,
                                           long gameTime, long ticksL1, long ticksL2, long ticksL3) {
        StuckEntry entry = STUCK_TRACKER.get(id);
        if (entry == null) {
            STUCK_TRACKER.put(id, new StuckEntry(distToObjectiveSq, gameTime));
            return;
        }
        // Progress = meaningful drop in squared distance. 1 block ~ 1.0 in
        // linear terms; in squared terms the delta scales with distance, so
        // we use a fractional threshold: 4% closer counts as progress.
        double progressThreshold = entry.lastDistSq * 0.96;
        if (distToObjectiveSq < progressThreshold) {
            entry.lastDistSq = distToObjectiveSq;
            entry.lastProgressGameTime = gameTime;
            entry.escalationLevel = 0;
            entry.cachedFallbackTarget = null;
            entry.fallbackCacheUntilGameTime = gameTime;
            return;
        }
        long stalledFor = gameTime - entry.lastProgressGameTime;

        if (entry.escalationLevel < 1 && stalledFor >= ticksL1) {
            // Level 1: jump + fresh path.
            mob.getJumpControl().jump();
            entry.escalationLevel = 1;
            pathingTelemetry(teamKey).stuckEscalationsL1++;
        }
        if (entry.escalationLevel < 2 && stalledFor >= ticksL2) {
            // Level 2: main loop consults escalationLevel to widen aggro
            // on the next tick. No direct action needed here.
            entry.escalationLevel = 2;
            pathingTelemetry(teamKey).stuckEscalationsL2++;
        }
        if (entry.escalationLevel < 3 && stalledFor >= ticksL3) {
            // Level 3 (v4.17.0): short teleport forward. Fires only if the
            // config toggle is on. Moves the raider up to STUCK_L3_TELEPORT_BLOCKS
            // blocks along the horizontal vector to the objective, snapping to
            // the surface heightmap so we don't teleport into a wall. If the
            // destination fails our sanity checks we skip and let the next
            // sample retry. This is the last-ditch fix for terrain the
            // pathfinder cannot solve.
            if (RaidConfig.STUCK_L3_TELEPORT_ENABLED.get() && mob.level() instanceof ServerLevel serverLevel) {
                teleportStuckRaiderForward(serverLevel, mob, objective);
            }
            entry.escalationLevel = 3;
            pathingTelemetry(teamKey).stuckEscalationsL3++;
            // Reset the progress clock so we don't re-teleport on the very
            // next tick if the teleport itself didn't close the gap far
            // enough to trip the progress threshold.
            entry.lastDistSq = mob.distanceToSqr(objective);
            entry.lastProgressGameTime = gameTime;
            entry.cachedFallbackTarget = null;
            entry.fallbackCacheUntilGameTime = gameTime;
        }
    }

    /**
     * v4.17.0 Level 3 stuck escalation: teleport a raider a short distance
     * forward toward the objective. Distance capped by config so this is not
     * a free warp. Uses the WORLD_SURFACE heightmap to snap Y to the ground
     * so we never drop the raider inside a wall or under the floor. If the
     * destination is not a safe standing position we abort.
     */
    private static void teleportStuckRaiderForward(ServerLevel level, Mob mob, Vec3 objective) {
        int maxBlocks = RaidConfig.STUCK_L3_TELEPORT_BLOCKS.get();
        Vec3 pos = mob.position();
        double dx = objective.x - pos.x;
        double dz = objective.z - pos.z;
        double horizLen = Math.sqrt(dx * dx + dz * dz);
        if (horizLen < 1.0) return;
        double step = Math.min(maxBlocks, horizLen - 1.0);
        if (step < 2.0) return;
        double nx = pos.x + (dx / horizLen) * step;
        double nz = pos.z + (dz / horizLen) * step;
        int destX = (int) Math.floor(nx);
        int destZ = (int) Math.floor(nz);
        // WORLD_SURFACE_WG returns the highest non-air block, so +0 lands us
        // one block above it, i.e. standing on top. Use WORLD_SURFACE (not
        // MOTION_BLOCKING) so leaves/liquid don't confuse us.
        int destY = level.getHeight(Heightmap.Types.WORLD_SURFACE, destX, destZ);
        // Sanity check: destination must be loaded and non-lava under-block.
        BlockPos foot = new BlockPos(destX, destY, destZ);
        if (!level.isLoaded(foot)) return;
        var under = level.getBlockState(foot.below());
        if (under.getFluidState().is(net.minecraft.tags.FluidTags.LAVA)) return;
        // Two blocks of clearance above the destination for the raider hitbox.
        if (level.getBlockState(foot).isSolid() || level.getBlockState(foot.above()).isSolid()) return;
        mob.getNavigation().stop();
        mob.teleportTo(destX + 0.5, destY, destZ + 0.5);
        // A forward warp must never be charged as a fall: the raider was on
        // the ground before the teleport and is standing on the surface now.
        mob.fallDistance = 0;
    }

    /**
     * Is {@code candidate} an eligible aggression target for {@code raider}?
     * Requires the candidate be within {@code aggroRangeSq} AND on the
     * objective-side hemisphere of the raider (closer to the objective than
     * to the war camp). Camp reference is optional — without it we accept
     * any in-range candidate.
     */
    private static boolean eligibleTarget(Mob raider, LivingEntity candidate,
                                          Vec3 objective, Vec3 campVec, double aggroRangeSq) {
        double distSq = raider.distanceToSqr(candidate);
        if (distSq > aggroRangeSq) return false;
        if (campVec == null) return true;
        // Behind-us filter: reject candidates that are closer to the camp
        // than to the objective. Uses Vec3 distanceToSqr for a stable check
        // that ignores Y so cliffs and towers don't confuse it.
        Vec3 cp = candidate.position();
        double toObjective = cp.subtract(objective.x, cp.y, objective.z).horizontalDistanceSqr();
        double toCamp = cp.subtract(campVec.x, cp.y, campVec.z).horizontalDistanceSqr();
        return toObjective <= toCamp;
    }

    /**
     * Squared perpendicular distance from {@code point} to the infinite line
     * defined by {@code start} → {@code end}. Ignores Y — the invasion axis
     * is treated as a horizontal line so vertical terrain doesn't create
     * false drift. Returns 0 when start == end.
     */
    private static double perpendicularDistanceSq(Vec3 point, Vec3 start, Vec3 end) {
        double ex = end.x - start.x;
        double ez = end.z - start.z;
        double lengthSq = ex * ex + ez * ez;
        if (lengthSq <= 1.0E-6D) return 0.0D;
        double px = point.x - start.x;
        double pz = point.z - start.z;
        // (px, pz) projected onto (ex, ez); the perpendicular component's
        // length squared is |p|^2 - (p·e)^2 / |e|^2.
        double dot = px * ex + pz * ez;
        double lenSq = px * px + pz * pz;
        return Math.max(0.0D, lenSq - (dot * dot) / lengthSq);
    }

    private static List<Mob> alliedRecruits(ServerLevel level, RaidSavedData.DefensePoint point,
                                            RaidSavedData.Anchor anchor) {
        double radius = RaidConfig.RECRUIT_MOBILIZATION_RADIUS.get();
        AABB area = new AABB(point.pos()).inflate(radius, 64.0D, radius);
        return level.getEntitiesOfClass(Mob.class, area,
                mob -> mob.isAlive() && RecruitsBridge.belongsTo(mob, anchor.teamKey(), anchor.members()));
    }

    private static OptionalCompatBridge.CompatSnapshot nearbyCompatAssets(ServerLevel level,
            RaidSavedData.DefensePoint point, RaidSavedData.Anchor anchor) {
        double radius = RaidConfig.COMPAT_ASSET_RADIUS.get();
        AABB area = new AABB(point.pos()).inflate(radius, 64.0D, radius);
        return OptionalCompatBridge.scan(level, area, anchor.teamKey(), anchor.members());
    }

    private static String scoutingSummary(int recruitScale, int assetScale, int recruits,
                                          OptionalCompatBridge.CompatSnapshot compat) {
        List<String> details = new ArrayList<>();
        if (recruitScale > 0) details.add(recruits + " defending Recruits");
        if (assetScale > 0) details.add(compat.crewedAssets() + " faction war assets");
        return details.isEmpty() ? "." : " after scouting " + String.join(" and ", details) + ".";
    }

    private static int assetScalingEnemies(OptionalCompatBridge.CompatSnapshot compat) {
        int divisor = RaidConfig.CREWED_ASSETS_PER_EXTRA_ENEMY.get();
        return divisor <= 0 ? 0 : Math.min(RaidConfig.MAX_ASSET_SCALING_ENEMIES.get(),
                compat.crewedAssets() / divisor);
    }

    private static void mobilizeRecruits(ServerLevel level, List<Mob> recruits,
                                         RaidSavedData.RaidState state) {
        List<Mob> attackers = new ArrayList<>();
        for (UUID id : state.raiders) {
            Entity entity = level.getEntity(id);
            if (entity instanceof Mob mob && mob.isAlive()) attackers.add(mob);
        }
        if (attackers.isEmpty()) return;
        for (Mob recruit : recruits) {
            LivingEntity current = recruit.getTarget();
            if (current != null && current.isAlive()) continue;
            Mob closest = null;
            double closestDistance = Double.MAX_VALUE;
            for (Mob attacker : attackers) {
                double distance = recruit.distanceToSqr(attacker);
                if (distance < closestDistance) {
                    closest = attacker;
                    closestDistance = distance;
                }
            }
            if (closest != null) recruit.setTarget(closest);
        }
    }

    private static boolean updateCaptureProgress(MinecraftServer server, RaidSavedData.Anchor anchor,
                                                 RaidSavedData.DefensePoint point,
                                                 RaidSavedData.RaidState state, ServerLevel level,
                                                 List<ServerPlayer> members, List<Mob> recruits) {
        if (state.wave <= 0) return false;
        Vec3 center = Vec3.atCenterOf(point.pos());
        if ("siege_core".equals(point.name())) {
            state.breached = true;
            int[] counts = com.devfarinsky.siegeoverhaul.core.CoreOccupation.counts(level,point.pos(),state.teamKey,anchor.members());
            int maximum = RaidConfig.CAPTURE_TIME_SECONDS.get()*20;
            state.captureTicks = com.devfarinsky.siegeoverhaul.core.CoreControl.advance(state.captureTicks,maximum,counts[0],counts[1]);
            state.objectiveStatus = "Core capture " + state.captureTicks*100/maximum + "% | " + counts[0] + " enemies / " + counts[1] + " defenders";
            return state.captureTicks >= maximum && counts[0]>counts[1];
        }
        if (RaidConfig.ENABLE_BREACH_PHASE.get() && !state.breached) {
            Vec3 breachObjective = invasionObjective(level, point, state);
            double objectiveRadius = RaidConfig.BREACH_OBJECTIVE_RADIUS.get();
            double breachRadiusSq = objectiveRadius * objectiveRadius;
            int attackers = attackersInside(level, state, breachObjective, breachRadiusSq);
            int defenders = defendersInside(level, members, recruits, breachObjective, breachRadiusSq);
            int maximum = RaidConfig.BREACH_TIME_SECONDS.get() * 20;
            int bonus = RaidConfig.ENABLE_EFFORT_BONUS.get()
                    ? com.devfarinsky.siegeoverhaul.effort.RaidEffortTracker.consume(state.teamKey, ModConstants.TICK_INTERVAL) : 0;
            int decay = RaidConfig.BREACH_DECAY_PER_SECOND.get() * ModConstants.TICKS_PER_SECOND;
            state.breachTicks = com.devfarinsky.siegeoverhaul.raid.ObjectivePressure.advance(
                    state.breachTicks, maximum, attackers, defenders, decay, bonus);
            updateObjectiveFeedback(server, level, state, "Perimeter", attackers, defenders, state.breachTicks, decay);

            int band = maximum <= 0 ? 0 : state.breachTicks * 4 / maximum;
            if (band > state.lastBreachWarningBand && band < 4) {
                state.lastBreachWarningBand = band;
                int percent = band * 25;
                announce(server, anchor.teamKey(), Component.literal("Perimeter breach pressure: " + percent +
                        "%. Hold the outer defensive line!").withStyle(ChatFormatting.GOLD), band >= 3);
                // v2.31.0: chip format "Perimeter \u00b7 75%" instead of shouted percent line.
                // v2.33.0: routed through the shared actionBar factory.
                sendActionBar(server, anchor.teamKey(),
                        com.devfarinsky.siegeoverhaul.chat.ChatStyle.actionBar(
                                "Perimeter", com.devfarinsky.siegeoverhaul.chat.ChatStyle.pressureColor(percent),
                                percent + "%"));
            }
            if (state.breachTicks >= maximum) {
                state.breached = true;
                state.lastCaptureWarningBand = 0;
                announce(server, anchor.teamKey(), Component.literal("The perimeter has been breached. Invaders are pushing for the stronghold heart.")
                        .withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD), true);
                // v2.32.0: perimeter breach is MAJOR — the moment the raid
                // shifts from defense to survival deserves a real title card.
                showTitle(server, anchor.teamKey(), Component.literal("Perimeter Breached")
                                .withStyle(ChatFormatting.DARK_RED),
                        Component.literal("Fall back and defend the stronghold heart")
                                .withStyle(ChatFormatting.GOLD),
                        com.devfarinsky.siegeoverhaul.chat.ChatStyle.TitleWeight.MAJOR);
            }
            return false;
        }

        state.breached = true;
        double radiusSq = (double) RaidConfig.CAPTURE_RADIUS.get() * RaidConfig.CAPTURE_RADIUS.get();
        int attackers = attackersInside(level, state, center, radiusSq);
        int defenders = defendersInside(level, members, recruits, center, radiusSq);

        int maximum = RaidConfig.CAPTURE_TIME_SECONDS.get() * 20;
        int bonus = RaidConfig.ENABLE_EFFORT_BONUS.get()
                ? com.devfarinsky.siegeoverhaul.effort.RaidEffortTracker.consume(state.teamKey, ModConstants.TICK_INTERVAL) : 0;
        int decay = RaidConfig.CAPTURE_DECAY_PER_SECOND.get() * ModConstants.TICKS_PER_SECOND;
        state.captureTicks = com.devfarinsky.siegeoverhaul.raid.ObjectivePressure.advance(
                state.captureTicks, maximum, attackers, defenders, decay, bonus);
        updateObjectiveFeedback(server, level, state, "Stronghold", attackers, defenders, state.captureTicks, decay);

        int band = maximum <= 0 ? 0 : state.captureTicks * 4 / maximum;
        if (band > state.lastCaptureWarningBand && band < 4) {
            state.lastCaptureWarningBand = band;
            int percent = band * 25;
            announce(server, anchor.teamKey(), Component.literal("Invaders hold " + percent +
                    "% of the stronghold — push them out.").withStyle(ChatFormatting.DARK_RED),
                    band >= 3);
            sendActionBar(server, anchor.teamKey(),
                    com.devfarinsky.siegeoverhaul.chat.ChatStyle.actionBar(
                            "Stronghold", com.devfarinsky.siegeoverhaul.chat.ChatStyle.pressureColor(percent),
                            percent + "% held"));
        }
        return state.captureTicks >= maximum;
    }

    private static String compactObjectiveStatus(RaidSavedData.RaidState state) {
        return state.objectiveStatus.replace(" attackers / ", " vs ").replace(" defenders in ring", "");
    }

    private static void updateObjectiveFeedback(MinecraftServer server, ServerLevel level,
                                                 RaidSavedData.RaidState state, String phase,
                                                 int attackers, int defenders, int progress, int decay) {
        state.objectiveStatus = com.devfarinsky.siegeoverhaul.raid.ObjectivePressure.status(
                attackers, defenders, progress, decay);
        if (level.getGameTime() % ModConstants.secondsToTicks(5) == 0) {
            sendActionBar(server, state.teamKey, Component.literal(phase + ": " + state.objectiveStatus)
                    .withStyle(com.devfarinsky.siegeoverhaul.raid.ObjectivePressure.enemyControls(attackers, defenders)
                            ? ChatFormatting.RED : ChatFormatting.GREEN));
        }
    }

    private static int attackersInside(ServerLevel level, RaidSavedData.RaidState state,
                                       Vec3 center, double radiusSq) {
        int attackers = 0;
        for (UUID id : state.raiders) {
            Entity entity = level.getEntity(id);
            if (entity instanceof Mob mob && mob.isAlive() && mob.distanceToSqr(center) <= radiusSq) attackers++;
        }
        return attackers;
    }

    private static int defendersInside(ServerLevel level, List<ServerPlayer> members, List<Mob> recruits,
                                       Vec3 center, double radiusSq) {
        int defenders = 0;
        for (ServerPlayer player : members) {
            if (player.level() == level && player.isAlive() && !player.isSpectator() &&
                    player.distanceToSqr(center) <= radiusSq) defenders++;
        }
        for (Mob recruit : recruits) {
            if (recruit.isAlive() && recruit.distanceToSqr(center) <= radiusSq) defenders++;
        }
        return defenders;
    }

    private static Vec3 invasionObjective(ServerLevel level, RaidSavedData.DefensePoint point,
                                          RaidSavedData.RaidState state) {
        if ("siege_core".equals(point.name())) return Vec3.atCenterOf(point.pos());
        if (!RaidConfig.ENABLE_BREACH_PHASE.get() || state.breached) return Vec3.atCenterOf(point.pos());
        return invasionBreachObjective(level, point, state);
    }

    private static Vec3 invasionBreachObjective(ServerLevel level, RaidSavedData.DefensePoint point,
                                                RaidSavedData.RaidState state) {
        double distance = effectiveBreachRadius() - 3.0D;
        int x = point.pos().getX() + Mth.floor(Math.cos(state.approachAngle) * distance);
        int z = point.pos().getZ() + Mth.floor(Math.sin(state.approachAngle) * distance);
        int y = level.hasChunk(x >> 4, z >> 4) ?
                level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) : point.pos().getY();
        return new Vec3(x + 0.5D, y, z + 0.5D);
    }

    private static String formatVec(Vec3 position) {
        return Mth.floor(position.x) + ", " + Mth.floor(position.y) + ", " + Mth.floor(position.z);
    }

    private static int breachPercent(RaidSavedData.RaidState state) {
        if (state.breached || !RaidConfig.ENABLE_BREACH_PHASE.get()) return 100;
        return Mth.clamp(state.breachTicks * 100 /
                Math.max(1, RaidConfig.BREACH_TIME_SECONDS.get() * 20), 0, 100);
    }

    private static double effectiveBreachRadius() {
        return Math.max(RaidConfig.BREACH_RADIUS.get(), RaidConfig.CAPTURE_RADIUS.get() + 4.0D);
    }

    private static void setRaidMobsFrozen(ServerLevel level, RaidSavedData.RaidState state, boolean frozen) {
        com.devfarinsky.siegeoverhaul.camp.CampGuards.tick(level, state, frozen);
        for (UUID id : state.raiders) {
            Entity entity = level.getEntity(id);
            if (entity instanceof Mob mob && mob.isAlive()) {
                mob.setNoAi(frozen);
                if (frozen) mob.setTarget(null);
            }
        }
    }

    private static boolean hasDefenderNear(MinecraftServer server, RaidSavedData.DefensePoint point,
                                           List<ServerPlayer> members) {
        ServerLevel level = getLevel(server, point);
        if (level == null) return false;
        double radiusSq = (double) RaidConfig.DEFENSE_RADIUS.get() * RaidConfig.DEFENSE_RADIUS.get();
        for (ServerPlayer player : members) {
            if (player.level() == level && player.isAlive() && !player.isSpectator() &&
                    player.distanceToSqr(point.pos().getX() + 0.5, point.pos().getY() + 0.5,
                            point.pos().getZ() + 0.5) <= radiusSq) return true;
        }
        return false;
    }

    private static void finishRaid(MinecraftServer server, RaidSavedData data, String teamKey,
                                   boolean victory, boolean reward, String message) {
        com.devfarinsky.siegeoverhaul.naval.NavalConvoy.forget(teamKey);
        com.devfarinsky.siegeoverhaul.naval.BridgeBuilder.forget(teamKey);
        ACTIVE_COMPOSITIONS.remove(teamKey);
        com.devfarinsky.siegeoverhaul.formations.FormationDirector.forget(teamKey);
        com.devfarinsky.siegeoverhaul.effort.RaidEffortTracker.forget(teamKey);
        com.devfarinsky.siegeoverhaul.effort.StragglerTracker.forget(teamKey);
        com.devfarinsky.siegeoverhaul.siege.LadderBuilder.forget(teamKey);
        // v2.23.0: drop per-raider stuck-tracker entries for this raid.
        // The main loop removes single dead entries opportunistically, but
        // a raid ending (admin-stopped, victory, defeat) drops them all at
        // once so the map does not grow across many raids.
        RaidSavedData.RaidState finishingState = data.raids.get(teamKey);
        if (finishingState != null) {
            for (UUID rid : finishingState.raiders) STUCK_TRACKER.remove(rid);
        }
        PATHING_TELEMETRY.remove(teamKey);
        RaidSavedData.RaidState state = data.raids.remove(teamKey);
        RaidSavedData.Anchor anchor = data.anchors.get(teamKey);
        // v4.30.0: raid is over - reset the diplomacy relation so Recruits' HUD
        // and target selectors stop treating this raider faction as hostile.
        if (state != null && state.factionId != null && !state.factionId.isBlank()) {
            com.devfarinsky.siegeoverhaul.compat.RaiderDiplomacy.resetRelation(server, teamKey, state.factionId);
        }
        if (state != null && anchor != null) {
            RaidSavedData.DefensePoint point = anchor.point(state.defensePointName);
            ServerLevel level = getLevel(server, point);
            if (level != null) {
                com.devfarinsky.siegeoverhaul.camp.CampLoading.release(level,state.campSearchPos);
                com.devfarinsky.siegeoverhaul.camp.CampLoading.release(level,state.campPos);
                com.devfarinsky.siegeoverhaul.camp.CampLoading.release(level,point.pos());
                state.marchChunks.clear();
                com.devfarinsky.siegeoverhaul.siege.SiegeDeployment.cleanup(level, state);
                com.devfarinsky.siegeoverhaul.camp.CampGuards.cleanup(level, state);
                com.devfarinsky.siegeoverhaul.raid.RaidCavalry.cleanup(level,state.teamKey);
                for (UUID id : state.raiders) {
                    Entity entity = level.getEntity(id);
                    if (entity != null) entity.discard();
                }
                restoreBreachedBlocks(level, state);
                com.devfarinsky.siegeoverhaul.camp.CampBuilder.cleanup(level, state);
                com.devfarinsky.siegeoverhaul.camp.WarGate.cleanup(level,state);
                cleanupWarCamp(level, state);
            }
            long next = server.overworld().getGameTime() + randomCooldownTicks(server.overworld().random);
            data.anchors.put(teamKey, anchor.withNextRaid(next));
        }
        com.devfarinsky.siegeoverhaul.compat.CampClaims.cleanOrphans(server.overworld(), data);
        boolean eligibleVictory = victory && reward && state != null && state.rewardEligible;
        // v2.28.0: track the "no breach" bonus outside the block so the
        // announcement branch below can mention it and the War Journal path
        // can log the payout accurately.
        int noBreachBonus = 0;
        if (eligibleVictory) {
            int experience = RaidConfig.VICTORY_EXPERIENCE.get();
            List<ServerPlayer> winners = onlineMembers(server, teamKey);
            if (experience > 0) winners.forEach(p -> p.giveExperiencePoints(experience));
            int emeralds = EndlessSiege.active(state) ? 0 : guaranteedEmeraldReward(state);
            if (emeralds > 0) winners.forEach(p -> giveEmeralds(p, emeralds));
            // v2.28.0: no-breach Commander kill bonus. When the perimeter
            // was never breached during the entire siege AND the defenders
            // won (which by-definition means the Commander went down on the
            // final wave), grant every online member a bonus emerald stack
            // equal to half the base guaranteed reward, rounded up, with a
            // floor of 1. This makes the Unit Codex's Commander tip and the
            // Hollowfang lore promise true: shutout defense pays extra.
            if (RaidConfig.ENABLE_BREACH_PHASE.get() && !state.breached) {
                noBreachBonus = Math.max(1, (emeralds + 1) / 2);
                final int bonus = noBreachBonus;
                winners.forEach(p -> giveEmeralds(p, bonus));
            }
            if (RaidConfig.VICTORY_LOOT_ENABLED.get()) winners.forEach(p -> giveVictoryLoot(server, p));
            // v2.29.0: drop the defeated faction's banner as a trophy for
            // each winner. Uses ItemHandlerHelper so a full inventory spills
            // to the world instead of eating the drop. Skipped silently if
            // the state lacks a factionId (pre-2.29.0 in-flight save). This
            // is the ONLY loot path that references state.factionId directly
            // — all rendering code goes through FactionBanners helpers.
            if (state.factionId != null) {
                com.devfarinsky.siegeoverhaul.items.FactionBanners.FactionId trophy =
                        com.devfarinsky.siegeoverhaul.items.FactionBanners.FactionId.byIdOrDefault(state.factionId);
                winners.forEach(p -> {
                    net.minecraft.world.item.ItemStack banner =
                            com.devfarinsky.siegeoverhaul.items.FactionBanners.itemStackFor(trophy);
                    net.minecraftforge.items.ItemHandlerHelper.giveItemToPlayer(p, banner);
                });
            }
            // v3.2.0: queue equivalent spoils for OFFLINE members so absence
            // during the siege doesn't erase their share. They collect via
            // /siegeoverhaul claim after logging in. Also v3.2.0: pay a
            // partial share of guaranteed emeralds to non-faction defenders
            // ("ally defenders") who dealt damage to attackers during this
            // siege — encourages helping neighbors and cross-faction play.
            queueOfflineSpoils(server, data, anchor, teamKey, state, emeralds, noBreachBonus, experience,
                    RaidConfig.VICTORY_LOOT_ENABLED.get(), winners);
            payAllyDefenders(server, data, anchor, teamKey, state, emeralds);
        }
        // v2.12.0 Know Your Enemy — record this siege to the War Journal and
        // mark the attacking faction as discovered. Unit discovery happens
        // incrementally in RaidTickEvents (on damage/hit contact) so a team
        // that fled the wall still learns "a Ravager showed up" as soon as
        // they saw one, not only if they win.
        if (state != null) {
            recordWarJournal(server, data, teamKey, state, victory, eligibleVictory);
            if (state.narrative != null) {
                markFactionDiscovered(data, teamKey, state.narrative.factionId);
            }
        }

        // v4.32.0: fire advancement triggers for every online defender at the
        // moment the raid ends. We only fire the "victory" family when the
        // defenders actually won (victory && eligibleVictory) so a manual
        // /siegeoverhaul admin end doesn't unlock advancements. Faction
        // discovery fires for both wins and losses because scouting the
        // enemy costs blood either way.
        if (state != null) {
            java.util.List<ServerPlayer> present = onlineMembers(server, teamKey);
            String factionId = state.narrative != null && state.narrative.factionId != null
                    ? state.narrative.factionId : "";
            for (ServerPlayer p : present) {
                com.devfarinsky.siegeoverhaul.advancements.SiegeTriggers.FACTION_DISCOVERED
                        .trigger(p, factionId);
                if (eligibleVictory) {
                    com.devfarinsky.siegeoverhaul.advancements.SiegeTriggers.RAID_WON.trigger(p);
                    // Untouchable: this raid ended with zero counted faction
                    // deaths. Counter is server-side and additive so it stays
                    // fair across relogs.
                    if (state.defenderDeaths == 0) {
                        com.devfarinsky.siegeoverhaul.advancements.SiegeTriggers.NO_DEATH_VICTORY.trigger(p);
                    }
                    // Perimeter Intact: zero repair-queued blocks left at
                    // raid end. state.breachedBlocks is the same list the
                    // repair notifier reads, so this stays consistent with
                    // what the player sees in chat.
                    if (state.breachedBlocks == null || state.breachedBlocks.isEmpty()) {
                        com.devfarinsky.siegeoverhaul.advancements.SiegeTriggers.NO_BREACH_VICTORY.trigger(p);
                    }
                    // Endless-wave milestones: the current wave number at
                    // finishRaid is the last wave the defenders survived, so
                    // wave >= 10 fires the wave-10 goal, >= 25 fires the
                    // wave-25 goal, etc. Only meaningful in Endless mode.
                    if (EndlessSiege.active(state)) {
                        com.devfarinsky.siegeoverhaul.advancements.SiegeTriggers.ENDLESS_WAVE_REACHED
                                .trigger(p, state.wave);
                    }
                }
            }
        }
        ServerBossEvent bar = RaidBossBars.remove(teamKey);
        if (bar != null) bar.removeAllPlayers();
        // v2.34.0: raid ended without the Commander dying (defeat, disband,
        // reload) - tear down the Commander boss bar too so the HUD is clean.
        com.devfarinsky.siegeoverhaul.raid.CommanderBossBar.remove(teamKey);
        // v2.35.0: drop the cached claim geometry so the next raid on this
        // team recomputes against whatever Recruits reports fresh.
        com.devfarinsky.siegeoverhaul.raid.ClaimWaypoints.invalidate(teamKey);
        long elapsedTicks = state == null || state.startedGameTime <= 0 ? 0 :
                Math.max(0, server.overworld().getGameTime() - state.startedGameTime);
        String summary = state == null ? "" : " Defeated: " + state.totalDefeated +
                " of " + state.totalSpawned + " deployed" +
                (state.totalEscaped > 0 ? "; " + state.totalEscaped + " lost contact" : "") +
                (elapsedTicks > 0 ? "; duration " + formatTime(elapsedTicks / 20) : "") + ".";
        announce(server, teamKey, Component.literal(message + summary)
                .withStyle(victory ? ChatFormatting.GREEN : ChatFormatting.DARK_RED), victory);
        // v4.31.0: on defeat, close with a somber tone so the loss lands
        // audibly. Wither death shout at low pitch reads as a war horn
        // fading. Victory already fires the raid horn via announce(..., true)
        // above, so no cue here for the win path.
        if (!victory) {
            playCue(server, teamKey, SoundEvents.WITHER_DEATH, 0.6F);
        }
        // On defender victory, close with the raider faction's parting taunt if
        // one was rolled. Prefix with an em-dash to read as attribution.
        if (victory && state != null && state.narrative != null && state.narrative.victoryTaunt != null) {
            announce(server, teamKey, Component.literal("— " + state.narrative.victoryTaunt)
                    .withStyle(ChatFormatting.ITALIC, state.narrative.accent), false);
        }
        if (victory && reward && !eligibleVictory) {
            announce(server, teamKey, Component.literal("Practice siege complete. Manual test raids do not grant rewards by default.")
                    .withStyle(ChatFormatting.YELLOW), false);
        } else if (eligibleVictory) {
            int emeralds = EndlessSiege.active(state) ? 0 : guaranteedEmeraldReward(state);
            announce(server, teamKey, Component.literal((EndlessSiege.active(state) ? "Faction bank earned " + state.campaign.getLong("Deposited") + " emeralds during this siege. Victory grants " : "Victory spoils: " + emeralds +
                    " guaranteed emeralds, ") + RaidConfig.VICTORY_EXPERIENCE.get() +
                    " experience and bonus campaign loot for each online faction member.")
                    .withStyle(ChatFormatting.GREEN), false);
            if (noBreachBonus > 0) {
                announce(server, teamKey, Component.literal(
                        "Perimeter held. No-breach bonus: +" + noBreachBonus +
                                " emeralds per member.")
                        .withStyle(ChatFormatting.GOLD), false);
            }
        }
        // v2.32.0: outcome is DEFINING — slow fade, long hold, slow fade out
        // so the win or loss lands as a moment instead of a status ping.
        showTitle(server, teamKey,
                Component.literal(victory ? "Siege Broken" : "Stronghold Fallen")
                        .withStyle(victory ? ChatFormatting.GREEN : ChatFormatting.DARK_RED),
                Component.literal(victory ? "Your faction held the line" : "The invaders seized the objective")
                        .withStyle(ChatFormatting.GOLD),
                com.devfarinsky.siegeoverhaul.chat.ChatStyle.TitleWeight.DEFINING);
        data.setDirty();
    }

    /**
     * Server-authoritative HUD refresh. Owns the invasion boss bar shown to
     * every defender. In v2.15.0 the bar became the primary "clear intent"
     * surface — it now leads with an explicit phase name (Rally / March /
     * Breach / Occupation), names the objective stronghold, and reports the
     * front-line distance so defenders always know what the raiders want
     * and how close they are to getting it.
     */
    /**
     * v2.15.0: single-word phase name for the invasion HUD. Kept short so
     * the boss bar has room for the objective name + distance behind it.
     * Order matches the raid lifecycle: rally (camp forming) → march
     * (advancing on the objective) → breach (attacking the walls) →
     * occupation (standing on the stronghold to capture).
     */
    private static String raidPhaseLabel(RaidSavedData.RaidState state, boolean paused) {
        if (!paused && state.campPos==null && RaidConfig.BUILD_WAR_CAMPS.get() && !state.coreCaptured) return "Scouting camp land";
        if (!paused && state.preparationTicks > 0) return preparationLabel(state) + " • " + (state.preparationTicks + 1199) / 1200 + "m until assault";
        if (paused) return "Paused";
        if (state.coreCaptured) return "Reclaim core";
        if ("siege_core".equals(state.defensePointName) && state.wave > 0) return "Defend core";
        if (state.wave == 0) return "Rally";
        if (!state.breached && RaidConfig.ENABLE_BREACH_PHASE.get()) return "Breach";
        if (state.captureTicks > 0) return "Occupation";
        return "March";
    }

    /**
     * v2.15.0: human-readable " • Nm" distance from the raider war camp to
     * the current objective. Returns empty string when the camp position
     * is unknown (no fixed camp placed yet).
     */
    private static String frontLineDistanceHint(MinecraftServer server,
                                                RaidSavedData.Anchor anchor,
                                                RaidSavedData.RaidState state) {
        if (state.campPos == null || state.defensePointName == null) return "";
        RaidSavedData.DefensePoint point = anchor.point(state.defensePointName);
        if (point == null) return "";
        ServerLevel level = server.overworld();
        if (level == null) return "";
        Vec3 objective;
        try {
            objective = invasionObjective(level, point, state);
        } catch (RuntimeException ex) {
            return "";
        }
        double dx = objective.x - state.campPos.getX();
        double dz = objective.z - state.campPos.getZ();
        int meters = (int) Math.round(Math.sqrt(dx * dx + dz * dz));
        // v2.32.0: middle-dot separator to match the v2.31 chat presentation.
        return com.devfarinsky.siegeoverhaul.chat.ChatStyle.SEP + meters + "m";
    }

    private static void updateBossBar(MinecraftServer server, RaidSavedData.Anchor anchor,
                                      RaidSavedData.RaidState state, boolean paused) {
        // v2.15.0: allow servers to fully suppress the invasion HUD.
        if (!RaidConfig.HUD_ENABLED.get()) {
            ServerBossEvent existing = RaidBossBars.remove(anchor.teamKey());
            if (existing != null) existing.removeAllPlayers();
            return;
        }
        ServerBossEvent bar = RaidBossBars.getOrCreate(anchor.teamKey(),
                Component.literal("Faction Invasion"),
                BossEvent.BossBarColor.RED, BossEvent.BossBarOverlay.NOTCHED_10);
        List<ServerPlayer> currentMembers = onlineMembers(server, anchor.teamKey());
        for (ServerPlayer shown : new ArrayList<>(bar.getPlayers())) {
            if (!currentMembers.contains(shown)) bar.removePlayer(shown);
        }
        for (ServerPlayer p : currentMembers) bar.addPlayer(p);
        int totalWaves = EndlessSiege.active(state) ? 5 : RaidConfig.WAVES.get();
        float completedWaves = Math.max(0, (EndlessSiege.active(state) ? EndlessSiege.chapterWave(state.wave) : state.wave) - 1);
        int planned = Math.max(state.plannedWaveSize, state.waveStartingCount + state.pendingWaveSpawns);
        float clearedFraction = planned <= 0 ? 0.0F :
                1.0F - (float) (state.raiders.size() + state.pendingWaveSpawns) / planned;
        bar.setProgress(Mth.clamp((completedWaves + clearedFraction) / totalWaves, 0.0F, 1.0F));
        int capturePercent = Mth.clamp(state.captureTicks * 100 /
                Math.max(1, RaidConfig.CAPTURE_TIME_SECONDS.get() * 20), 0, 100);
        int breachPercent = breachPercent(state);

        // v2.15.0 "Clear Intent" — lead with the current phase name so a
        // player looking at the bar for one second knows the raider goal.
        String phase = raidPhaseLabel(state, paused);
        // Prefer the human-readable stronghold name when available; fall
        // back to the raw defense point id.
        String objectiveName = state.defensePointName != null && !state.defensePointName.isEmpty() ?
                state.defensePointName : "stronghold";
        // Front-line distance: how close the raiders' camp sits to the
        // objective. Fixed while the camp exists so it reads as a stable
        // "they're staging N blocks northeast" rather than jittering with
        // whichever raider happens to be leading. Now includes the middle-
        // dot separator via v2.32.0 style.
        String distanceHint = frontLineDistanceHint(server, anchor, state);

        // v2.32.0 HUD polish: assemble the label out of structured chips so
        // the whole bar reads on one visual rhythm (middle-dot separators)
        // instead of jumping between hyphens, colons, and bullets. Chip
        // order is stable across phases: [objective+distance] · [deployed]
        // · [pressure]. Empty chips are dropped by the composer.
        String epithet = (RaidConfig.NARRATIVE_IN_BOSS_BAR.get() && state.narrative != null
                && state.narrative.factionEpithet != null) ? state.narrative.factionEpithet : null;
        String label;
        if (paused) {
            label = com.devfarinsky.siegeoverhaul.chat.ChatStyle.bossbarLabel(epithet, phase, "faction offline");
        } else if (state.campPos==null && RaidConfig.BUILD_WAR_CAMPS.get() && !state.coreCaptured
                && !state.campSearchAbandoned) {
            bar.setProgress(0);
            // v4.35.0: surface the real scouting status so the player knows
            // whether we're loading chunks, blocked by config, or just
            // haven't found viable land yet. Falls back to a live progress
            // hint through the 200-site spiral.
            String detail = state.objectiveStatus != null && !state.objectiveStatus.isEmpty()
                    ? state.objectiveStatus
                    : "scanning " + state.campSearchStep + "/200 sites";
            label = com.devfarinsky.siegeoverhaul.chat.ChatStyle.bossbarLabel(epithet,
                    "Scouting camp land", detail);
        } else if (state.coreCaptured) {
            var core = RaidSavedData.get(server).siegeCores.get(state.teamKey);
            int progress = core == null ? 0 : core.getInt("RecaptureTicks");
            bar.setProgress(Mth.clamp((float)progress/(RaidConfig.CORE_RECAPTURE_SECONDS.get()*20),0,1));
            label = com.devfarinsky.siegeoverhaul.chat.ChatStyle.bossbarLabel(epithet,"Reclaim core",state.objectiveStatus);
        } else if (state.preparationTicks > 0 || state.wave == 0) {
            // Rally phase: approach direction is the useful chip.
            String target = objectiveName + distanceHint;
            String direction = "from the " + approachDirection(state.approachAngle);
            label = com.devfarinsky.siegeoverhaul.chat.ChatStyle.bossbarLabel(epithet, phase, direction, target);
        } else if (!state.breached && RaidConfig.ENABLE_BREACH_PHASE.get()) {
            String target = objectiveName + distanceHint;
            String deployed = state.raiders.size() + " deployed";
            String pressure = "breach " + breachPercent + "% | " + compactObjectiveStatus(state);
            label = com.devfarinsky.siegeoverhaul.chat.ChatStyle.bossbarLabel(epithet, phase, target, deployed, pressure);
        } else {
            String held = objectiveName + " " + capturePercent + "% | " + compactObjectiveStatus(state);
            String waveChip = EndlessSiege.waveLabel(state, totalWaves);
            String deployed = state.raiders.size() + " deployed"
                    + (state.pendingWaveSpawns > 0 ? " + " + state.pendingWaveSpawns + " reinforcing" : "");
            label = com.devfarinsky.siegeoverhaul.chat.ChatStyle.bossbarLabel(epithet, phase, held, waveChip, deployed);
        }

        // Idempotency: only push a new name Component when the label text
        // actually changed. Bossbar ticks at 20 Hz — skipping identical
        // re-renders saves a per-player packet every tick.
        String previous = bar.getName().getString();
        if (!previous.equals(label)) bar.setName(Component.literal(label));

        if ("siege_core".equals(state.defensePointName) && state.preparationTicks<=0 && !state.coreCaptured)
            bar.setProgress(capturePercent/100f);
        bar.setColor(state.coreCaptured ? BossEvent.BossBarColor.PURPLE : computeBossBarColor(state, paused, breachPercent, capturePercent));
    }

    /**
     * v2.32.0 HUD polish: flat table replacing the old nested ternary chain.
     * Same behavior, but each case is inspectable and one line long. Color
     * ramp:
     *
     * <pre>
     *   paused            → WHITE
     *   rally (wave 0)    → YELLOW  (staging, no pressure yet)
     *   pre-breach, low   → YELLOW  (breach &lt; 75%)
     *   pre-breach, high  → RED     (breach ≥ 75%)
     *   breached, low     → RED     (occupation &lt; 75%)
     *   breached, high    → PURPLE  (stronghold falling; distinct alarm color)
     * </pre>
     */
    private static BossEvent.BossBarColor computeBossBarColor(RaidSavedData.RaidState state, boolean paused,
                                                              int breachPercent, int capturePercent) {
        if (paused) return BossEvent.BossBarColor.WHITE;
        if (state.wave == 0) return BossEvent.BossBarColor.YELLOW;
        if (!state.breached) {
            return breachPercent >= 75 ? BossEvent.BossBarColor.RED : BossEvent.BossBarColor.YELLOW;
        }
        return capturePercent >= 75 ? BossEvent.BossBarColor.PURPLE : BossEvent.BossBarColor.RED;
    }

    /**
     * v2.31.0 Chat Presentation Overhaul: the {@code [Faction Raids]}
     * bracket prefix is gone. Team-scoped chat now leads with the diamond
     * glyph; cross-server broadcasts lead with crossed swords in broadcast
     * color so they visually part from personal tactical chat.
     *
     * <p>Callers pass an already-styled {@link Component}. To keep every
     * legacy callsite working without churn, we detect the leading glyph;
     * if the component doesn't start with one we prepend the team diamond
     * as a graceful fallback. Text hygiene (strip exclamation marks, kill
     * ALL CAPS) is enforced up at the callsites through
     * {@link com.devfarinsky.siegeoverhaul.chat.ChatStyle}, so this funnel
     * stays a thin dispatcher.</p>
     */
    private static void announce(MinecraftServer server, String teamKey, Component message, boolean horn) {
        Component styled = ensureGlyph(message);
        if (RaidConfig.ANNOUNCE_GLOBALLY.get()) {
            server.getPlayerList().broadcastSystemMessage(styled, false);
        } else onlineMembers(server, teamKey).forEach(p -> p.sendSystemMessage(styled));
        if (horn) onlineMembers(server, teamKey).forEach(p ->
                p.playNotifySound(SoundEvents.RAID_HORN.value(), SoundSource.HOSTILE, 1.0F, 1.0F));
    }

    /**
     * v4.31.0 tactical audio cues. Plays a short one-shot to every online
     * member of {@code teamKey} to punctuate a raid beat that used to be
     * silent (wave cleared, defeat, commander felled). Kept intentionally
     * quiet (0.7f) so it complements the chat line without stepping on
     * ambient music or combat sound. Uses {@link Player#playNotifySound}
     * so it plays client-side even if the player is not near the source
     * position.
     */
    private static void playCue(MinecraftServer server, String teamKey,
                                net.minecraft.sounds.SoundEvent sound,
                                float pitch) {
        onlineMembers(server, teamKey).forEach(p ->
                p.playNotifySound(sound, SoundSource.PLAYERS, 0.7F, pitch));
    }

    /**
     * If a caller hands us a raw {@link Component} that does not already
     * start with one of the v2.31.0 glyphs, prepend the team diamond so
     * the presentation contract holds. This keeps the visual style
     * consistent even for callsites that predate the {@code ChatStyle}
     * refactor.
     */
    private static Component ensureGlyph(Component message) {
        String plain = message.getString();
        if (plain.startsWith(com.devfarinsky.siegeoverhaul.chat.ChatStyle.GLYPH_TEAM)
                || plain.startsWith(com.devfarinsky.siegeoverhaul.chat.ChatStyle.GLYPH_CROSS)) {
            return message;
        }
        return Component.literal(com.devfarinsky.siegeoverhaul.chat.ChatStyle.GLYPH_TEAM)
                .withStyle(ChatFormatting.DARK_GRAY).append(message);
    }

    /**
     * v2.32.0 HUD polish: two overloads. The legacy no-weight signature
     * routes to {@link com.devfarinsky.siegeoverhaul.chat.ChatStyle.TitleWeight#MAJOR}
     * so existing callsites keep working. New callsites should pass a
     * weight to make the fade/hold timing match the event's importance:
     * routine phase transitions feel punchy, defining moments (siege
     * won or lost) linger.
     */
    private static void showTitle(MinecraftServer server, String teamKey, Component title, Component subtitle) {
        showTitle(server, teamKey, title, subtitle,
                com.devfarinsky.siegeoverhaul.chat.ChatStyle.TitleWeight.MAJOR);
    }

    private static void showTitle(MinecraftServer server, String teamKey, Component title, Component subtitle,
                                  com.devfarinsky.siegeoverhaul.chat.ChatStyle.TitleWeight weight) {
        if (!RaidConfig.SHOW_RAID_TITLES.get()) return;
        for (ServerPlayer player : onlineMembers(server, teamKey)) {
            player.connection.send(new ClientboundSetTitlesAnimationPacket(
                    weight.fadeInTicks, weight.holdTicks, weight.fadeOutTicks));
            player.connection.send(new ClientboundSetSubtitleTextPacket(subtitle));
            player.connection.send(new ClientboundSetTitleTextPacket(title));
        }
    }

    private static void sendActionBar(MinecraftServer server, String teamKey, Component message) {
        if (!RaidConfig.SHOW_ACTION_BAR_UPDATES.get()) return;
        onlineMembers(server, teamKey).forEach(player -> player.displayClientMessage(message, true));
    }

    private static int openDashboard(CommandSourceStack source) {
        try {
            ServerPlayer player = source.getPlayerOrException();
            RaidNetwork.openDashboard(player);
            return 1;
        } catch (Exception e) {
            source.sendFailure(Component.literal("Only a player can open the faction dashboard."));
            return 0;
        }
    }

    static boolean dashboardStart(ServerPlayer player) {
        return startOwnRaid(player.createCommandSourceStack(), null) > 0;
    }

    static boolean dashboardRefreshHome(ServerPlayer player) {
        return refreshAutomaticHome(player.createCommandSourceStack()) > 0;
    }

    static void dashboardHelp(ServerPlayer player) {
        help(player.createCommandSourceStack());
    }

    /**
     * v2.28.0: pack the optional-mod bridge availability + Recruits claim
     * linkage into a tuple so the three dashboard-snapshot construction
     * sites don't each duplicate the same reflection/lookup logic. The
     * result feeds the Codex compat strip and the Overview claim indicator.
     */
    private record CodexCompatInfo(boolean claimLinked, String claimName,
                                    boolean recruitsReady, boolean workersReady,
                                    boolean shipsReady, boolean siegeReady) {}

    private static CodexCompatInfo buildCodexCompatInfo(ServerLevel level, RaidSavedData.Anchor anchor,
                                                        RaidSavedData.DefensePoint activePoint) {
        boolean recruitsReady = com.devfarinsky.siegeoverhaul.compat.RecruitsClaimsBridge.available();
        boolean workersReady = OptionalCompatBridge.isLoaded(OptionalCompatBridge.WORKERS);
        boolean shipsReady = OptionalCompatBridge.isLoaded(OptionalCompatBridge.SMALL_SHIPS);
        boolean siegeReady = OptionalCompatBridge.isLoaded(OptionalCompatBridge.SIEGE_WEAPONS);
        boolean claimLinked = false;
        String claimName = "";
        if (recruitsReady && anchor != null && level != null) {
            // A synthetic claim point produced by selectAutomaticPoint has a
            // name prefixed with "claim:". If that's currently in use, we
            // want to say so. Even if no raid is active, resolve the claim
            // fresh so the pre-siege forecast can show the linkage too.
            java.util.Optional<com.devfarinsky.siegeoverhaul.compat.RecruitsClaimsBridge.ClaimSnapshot> snap =
                    com.devfarinsky.siegeoverhaul.compat.RecruitsClaimsBridge.resolveDefendingClaim(level, anchor);
            if (snap.isPresent()) {
                claimLinked = true;
                claimName = snap.get().claimName() == null ? "" : snap.get().claimName();
            } else if (activePoint != null && activePoint.name() != null && activePoint.name().startsWith("claim:")) {
                // Point name says the raid was born under a claim even if the
                // claim has since been resized past this specific point.
                claimLinked = true;
            }
        }
        return new CodexCompatInfo(claimLinked, claimName, recruitsReady, workersReady, shipsReady, siegeReady);
    }

    static DashboardSnapshot dashboardSnapshot(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        if (server == null) return DashboardSnapshot.unavailable();
        RaidSavedData data = RaidSavedData.get(server);
        String key = factionKeyForPlayer(data, player);
        RaidSavedData.Anchor anchor = data.anchors.get(key);
        if (anchor == null) {
            CodexCompatInfo compatInfoNoAnchor = buildCodexCompatInfo(null, null, null);
            return new DashboardSnapshot(teamDisplay(player), false, false, "No stronghold registered",
                    0, RaidConfig.WAVES.get(), 0, 0, 0, 0, false, 0, 0, 0, 0, 0, 0,
                    0, "No gate under attack", 0, "Place a Siege Core in your faction claim",
                    defaultEmeraldReward(), false,
                    "", "", "", "", "", 0, "", "", 0, "No stronghold",
                    "", "", java.util.List.of(), java.util.List.of(),
                    journalRowsFor(data, key),
                    compatInfoNoAnchor.claimLinked(), compatInfoNoAnchor.claimName(),
                    compatInfoNoAnchor.recruitsReady(), compatInfoNoAnchor.workersReady(),
                    compatInfoNoAnchor.shipsReady(), compatInfoNoAnchor.siegeReady());
        }
        RaidSavedData.RaidState state = data.raids.get(key);
        RaidSavedData.DefensePoint point = state == null ? anchor.primaryPoint() :
                anchor.point(state.defensePointName);
        ServerLevel level = getLevel(server, point);
        int recruits = level == null ? 0 : alliedRecruits(level, point, anchor).size();
        OptionalCompatBridge.CompatSnapshot compat = level == null ?
                OptionalCompatBridge.CompatSnapshot.EMPTY : nearbyCompatAssets(level, point, anchor);
        if (state == null) {
            long seconds = Math.max(0L,
                    (anchor.nextRaidGameTime() - server.overworld().getGameTime()) / 20L);
            int firstWaveTotal = Math.max(1, RaidConfig.BASE_ENEMIES_PER_WAVE.get());
            com.devfarinsky.siegeoverhaul.waves.WaveComposition preview =
                    com.devfarinsky.siegeoverhaul.waves.WaveComposer.compose(1, RaidConfig.WAVES.get(), firstWaveTotal);
            int score = computeDefenseScore(recruits, compat, firstWaveTotal, 1);
            String explainer = buildDefenseExplainer(recruits, compat, firstWaveTotal, 1);
            CodexCompatInfo compatInfoIdle = buildCodexCompatInfo(level, anchor, point);
            return new DashboardSnapshot(anchor.teamDisplay(), true, false,
                    point.dimension() + " • " + formatPos(point.pos()), 0, RaidConfig.WAVES.get(),
                    0, 0, 0, 0, false, 0, recruits, compat.workers(), compat.ships(), compat.siegeWeapons(),
                    assetScalingEnemies(compat), 0, "No gate under attack", 0,
                    com.devfarinsky.siegeoverhaul.core.CoreOccupation.occupied(data,key)?"Core occupied — outnumber enemies to recapture":formatTime(seconds), defaultEmeraldReward(), true,
                    "", "", "", "", "", 0,
                    preview.label.isEmpty() ? "Wave 1" : "Wave 1 — " + preview.label,
                    formatRoleCounts(preview),
                    score, defenseScoreLabel(score),
                    "", explainer,
                    discoveredUnitsFor(data, key), discoveredFactionsFor(data, key),
                    journalRowsFor(data, key),
                    compatInfoIdle.claimLinked(), compatInfoIdle.claimName(),
                    compatInfoIdle.recruitsReady(), compatInfoIdle.workersReady(),
                    compatInfoIdle.shipsReady(), compatInfoIdle.siegeReady());
        }
        int occupation = state.captureTicks * 100 /
                Math.max(1, RaidConfig.CAPTURE_TIME_SECONDS.get() * 20);
        int nextWaveNumber = EndlessSiege.active(state) ? (int)Math.min(Integer.MAX_VALUE,state.wave+1L) : Math.min(state.wave + 1, RaidConfig.WAVES.get());
        com.devfarinsky.siegeoverhaul.waves.WaveComposition nextPreview =
                com.devfarinsky.siegeoverhaul.waves.WaveComposer.compose(EndlessSiege.active(state) ? EndlessSiege.chapterWave(nextWaveNumber) : nextWaveNumber, EndlessSiege.active(state) ? 5 : RaidConfig.WAVES.get(),
                        Math.max(1, RaidConfig.BASE_ENEMIES_PER_WAVE.get()));
        String nextLabel = nextWaveNumber <= state.wave ? "Final wave in progress" :
                "Wave " + nextWaveNumber + (nextPreview.label.isEmpty() ? "" : " — " + nextPreview.label);
        String nextRoles = nextWaveNumber <= state.wave ? "" : formatRoleCounts(nextPreview);
        if (state.coreCaptured) {
            var occupiedCore = data.siegeCores.get(key);
            occupation = occupiedCore == null ? 0 : occupiedCore.getInt("RecaptureTicks")*100/(RaidConfig.CORE_RECAPTURE_SECONDS.get()*20);
            nextLabel = "Recapture your Siege Core"; nextRoles = "Outnumber the enemy at the core; no further assault waves.";
        }
        int score = computeDefenseScore(recruits, compat, state.raiders.size() + state.pendingWaveSpawns,
                state.wave);
        String campDir = state.campPos == null ? "" : approachDirection(state.approachAngle);
        int campDist = state.campPos == null ? 0 :
                (int) Math.round(Math.sqrt(point.pos().distSqr(state.campPos)));
        String facId = state.narrative != null ? state.narrative.factionId : "";
        String cbId = state.narrative != null ? state.narrative.casusBelliId : "";
        String opening = state.narrative != null && state.narrative.opening != null ? state.narrative.opening : "";
        String chant = state.narrative != null && state.narrative.chant != null ? state.narrative.chant : "";
        String threat = buildThreatBreakdown(level, state);
        String explainer = buildDefenseExplainer(recruits, compat,
                state.raiders.size() + state.pendingWaveSpawns, state.wave);
        CodexCompatInfo compatInfoActive = buildCodexCompatInfo(level, anchor, point);
        return new DashboardSnapshot(anchor.teamDisplay(), true, true,
                point.dimension() + " • " + formatPos(point.pos()) +
                        (state.campPos == null ? " • camp unavailable" : " • camp " + formatPos(state.campPos)),
                state.wave, EndlessSiege.active(state) ? nextCheckpoint(state.wave) : RaidConfig.WAVES.get(),
                state.raiders.size(), state.pendingWaveSpawns, state.totalDefeated, occupation,
                state.breached || !RaidConfig.ENABLE_BREACH_PHASE.get(), breachPercent(state),
                recruits, compat.workers(), compat.ships(), compat.siegeWeapons(), assetScalingEnemies(compat),
                state.breachedBlocks.size(),
                state.currentBreachBlock == null ? "No gate under attack" : formatPos(state.currentBreachBlock),
                gateBreachPercent(state), state.objectiveStatus,
                EndlessSiege.active(state) ? EndlessSiege.reward(nextWaveNumber) : guaranteedEmeraldReward(state), state.rewardEligible,
                facId, cbId, opening, chant, campDir, campDist,
                nextLabel, nextRoles, score, defenseScoreLabel(score),
                threat, explainer,
                discoveredUnitsFor(data, key), discoveredFactionsFor(data, key),
                journalRowsFor(data, key),
                compatInfoActive.claimLinked(), compatInfoActive.claimName(),
                compatInfoActive.recruitsReady(), compatInfoActive.workersReady(),
                compatInfoActive.shipsReady(), compatInfoActive.siegeReady());
    }

    /**
     * Human-readable breakdown of the defense score numerator + denominator.
     * Example: "Defense 5.5 (4 Recruits + 2 workers) vs 12 attackers x1.24 = 46/100".
     * Rendered on the Overview tab so players can see why the score is what it
     * is and what specifically to add to raise it.
     */
    private static String buildDefenseExplainer(int alliedRecruits,
                                                OptionalCompatBridge.CompatSnapshot compat,
                                                int incomingAttackers, int wave) {
        double defense = alliedRecruits + compat.workers() * 0.5D
                + compat.ships() * 0.75D + compat.siegeWeapons() * 1.25D;
        double waveMultiplier = 1.0D + Math.max(0, wave - 1) * 0.12D;
        double threat = Math.max(1.0D, incomingAttackers * waveMultiplier);
        // Compose the pieces that actually contributed; skip zero terms so the
        // string stays short and honest.
        StringBuilder parts = new StringBuilder();
        parts.append(alliedRecruits).append(" Recruits");
        if (compat.workers() > 0) parts.append(" + ").append(compat.workers()).append(" Workers×0.5");
        if (compat.ships() > 0) parts.append(" + ").append(compat.ships()).append(" ships×0.75");
        if (compat.siegeWeapons() > 0) parts.append(" + ").append(compat.siegeWeapons()).append(" engines×1.25");
        return String.format(java.util.Locale.ROOT,
                "Defense %.1f (%s) vs %d attackers ×%.2f = threat %.1f",
                defense, parts.toString(), incomingAttackers, waveMultiplier, threat);
    }

    private static java.util.List<String> discoveredUnitsFor(RaidSavedData data, String teamKey) {
        RaidSavedData.Discovery d = data.discoveries.get(teamKey);
        return d == null ? java.util.List.of() : java.util.List.copyOf(d.units);
    }

    private static java.util.List<String> discoveredFactionsFor(RaidSavedData data, String teamKey) {
        RaidSavedData.Discovery d = data.discoveries.get(teamKey);
        return d == null ? java.util.List.of() : java.util.List.copyOf(d.factions);
    }

    private static java.util.List<JournalRow> journalRowsFor(RaidSavedData data, String teamKey) {
        RaidSavedData.WarJournal j = data.journals.get(teamKey);
        if (j == null || j.entries.isEmpty()) return java.util.List.of();
        java.util.List<JournalRow> rows = new java.util.ArrayList<>(j.entries.size());
        for (RaidSavedData.WarJournal.Entry e : j.entries) {
            rows.add(new JournalRow(e.timestamp(), e.factionId(), e.factionName(),
                    e.casusBelliId(), e.wavesReached(), e.totalWaves(), e.outcome(), e.emeraldPayout()));
        }
        return rows;
    }

    /**
     * Computes a rough "can I survive this wave?" score 0-100 comparing
     * defensive strength (allied Recruits + workers + war assets bonus)
     * against incoming attackers scaled by wave number. This is intentionally
     * a heuristic — the goal is to give players actionable info like
     * "call for backup" or "you're ready", not a precise combat simulation.
     */
    private static int computeDefenseScore(int alliedRecruits, OptionalCompatBridge.CompatSnapshot compat,
                                           int incomingAttackers, int wave) {
        // Defenders: Recruits count for full weight, workers half, ships/siege give a small bonus.
        double defense = alliedRecruits + compat.workers() * 0.5D
                + compat.ships() * 0.75D + compat.siegeWeapons() * 1.25D;
        // Attackers scale by wave since later waves have higher-tier reserved slots.
        double waveMultiplier = 1.0D + Math.max(0, wave - 1) * 0.12D;
        double threat = Math.max(1.0D, incomingAttackers * waveMultiplier);
        double ratio = defense / threat;
        // Ratio of 1.0 == "even fight" ~= 60/100; 1.5+ = comfortable; 0.5 = badly outmatched.
        int score = (int) Math.round(Mth.clamp(ratio * 60.0D, 0.0D, 100.0D));
        return score;
    }

    private static String defenseScoreLabel(int score) {
        if (score >= 85) return "Overwhelming";
        if (score >= 70) return "Strong";
        if (score >= 55) return "Even fight";
        if (score >= 35) return "Outmatched";
        return "Call for backup";
    }

    private static String formatRoleCounts(com.devfarinsky.siegeoverhaul.waves.WaveComposition comp) {
        if (comp == null || comp.roleCounts.isEmpty()) return "Composition unknown";
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (var entry : comp.roleCounts.entrySet()) {
            if (!first) sb.append(" • ");
            first = false;
            sb.append(entry.getValue()).append(" ").append(prettyRole(entry.getKey()));
        }
        return sb.toString();
    }

    /**
     * Append a completed-siege entry to this team's War Journal. Called once
     * from {@link #finishRaid} on victory or defeat. The journal is intentionally
     * bounded ({@link RaidSavedData.WarJournal#MAX_ENTRIES}) so it stays cheap
     * to serialize into the dashboard packet and cheap to render in-book.
     */
    private static void recordWarJournal(MinecraftServer server, RaidSavedData data, String teamKey,
                                         RaidSavedData.RaidState state, boolean victory,
                                         boolean eligibleVictory) {
        RaidSavedData.WarJournal journal = data.journals.computeIfAbsent(teamKey,
                RaidSavedData.WarJournal::new);
        String factionId = state.narrative != null ? state.narrative.factionId : "";
        String factionName = state.narrative != null ? state.narrative.factionName : "Unknown raiders";
        String casusBelli = state.narrative != null ? state.narrative.casusBelliId : "";
        int payout = EndlessSiege.active(state) ? (int)Math.min(FactionBank.LIMIT, state.campaign.getLong("Deposited")) : eligibleVictory ? guaranteedEmeraldReward(state) : 0;
        // Outcome is a short machine-readable tag so the client can style it
        // (green vs. red vs. yellow) without brittle string matching.
        String outcome = victory ? (eligibleVictory ? "victory" : "victory_practice") : "defeat";
        journal.record(new RaidSavedData.WarJournal.Entry(
                server.overworld().getGameTime(), factionId, factionName, casusBelli,
                state.wave, EndlessSiege.active(state) ? nextCheckpoint(state.wave) : RaidConfig.WAVES.get(), outcome, payout));
        data.setDirty();
    }

    /**
     * Mark a faction as discovered for a team. Cheap idempotent operation
     * safe to call from any event handler. Returns true if newly discovered
     * (currently unused, but reserved for a future "Faction discovered!"
     * toast announcement).
     */
    private static boolean markFactionDiscovered(RaidSavedData data, String teamKey, String factionId) {
        if (factionId == null || factionId.isEmpty()) return false;
        RaidSavedData.Discovery d = data.discoveries.computeIfAbsent(teamKey, RaidSavedData.Discovery::new);
        if (d.addFaction(factionId)) {
            data.setDirty();
            return true;
        }
        return false;
    }

    /**
     * Mark a unit type as discovered for a team. Called from tick logic when
     * a raider is spawned or damages/damaged by a team member. Idempotent.
     */
    static boolean markUnitDiscovered(RaidSavedData data, String teamKey, String unitId) {
        if (unitId == null || unitId.isEmpty()) return false;
        RaidSavedData.Discovery d = data.discoveries.computeIfAbsent(teamKey, RaidSavedData.Discovery::new);
        if (d.addUnit(unitId)) {
            data.setDirty();
            return true;
        }
        return false;
    }

    /**
     * Walks the live raider roster and returns a human-friendly breakdown
     * of what is currently deployed — e.g. "4 Shieldman • 3 Bowman • 2 Captain".
     *
     * <p>Groups raiders by entity type first, then falls back to their
     * assigned {@link ModConstants.Tags#RAID_ROLE} tag when the type is a
     * generic vanilla monster. This is the exact data the client needs to
     * tell a player "a Ravager is inbound" instead of just "12 deployed."
     *
     * <p>Entities that have unloaded (chunk boundary, world unload, or
     * respawn edge cases) are silently skipped. The returned string caps
     * at the top 6 groups + a "+N more" suffix so the client string stays
     * short enough for the Overview panel.
     */
    private static String buildThreatBreakdown(ServerLevel level, RaidSavedData.RaidState state) {
        if (level == null || state == null || state.raiders.isEmpty()) return "";
        java.util.Map<String, Integer> counts = new java.util.LinkedHashMap<>();
        for (java.util.UUID id : state.raiders) {
            Entity entity = level.getEntity(id);
            if (!(entity instanceof Mob mob)) continue;
            String label = threatLabelFor(mob);
            counts.merge(label, 1, Integer::sum);
        }
        if (counts.isEmpty()) return "";
        // Sort by count descending so the most numerous threat leads.
        java.util.List<java.util.Map.Entry<String, Integer>> sorted = new java.util.ArrayList<>(counts.entrySet());
        sorted.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));
        int cap = 6;
        StringBuilder sb = new StringBuilder();
        int rendered = 0;
        int overflow = 0;
        for (java.util.Map.Entry<String, Integer> e : sorted) {
            if (rendered >= cap) { overflow += e.getValue(); continue; }
            if (rendered > 0) sb.append(" • ");
            sb.append(e.getValue()).append(" ").append(e.getKey());
            rendered++;
        }
        if (overflow > 0) sb.append(" • +").append(overflow).append(" more");
        return sb.toString();
    }

    /**
     * Maps a live raider entity + role tag to the client-side
     * {@code UnitCodex.Entry.id} that represents it. Returns "" when nothing
     * matches (e.g. a random modded mob a config includes that has no codex
     * page). Kept in sync with {@code client.codex.UnitCodex.ENTRIES}.
     */
    private static String codexIdFor(Mob raider, String role) {
        if ("commander".equals(role)) return "commander";
        ResourceLocation id = ForgeRegistries.ENTITY_TYPES.getKey(raider.getType());
        if (id == null) return "";
        return switch (id.toString()) {
            case "minecraft:pillager" -> "bowman";
            case "minecraft:vindicator" -> "shieldman";
            case "minecraft:ravager" -> "ravager";
            case "minecraft:illusioner", "minecraft:evoker" -> "illusioner";
            case "recruits:recruit", "recruits:recruit_shieldman" -> "shieldman";
            case "recruits:bowman" -> "bowman";
            case "recruits:crossbowman" -> "crossbowman";
            case "recruits:captain" -> "captain";
            case "recruits:patrol_leader" -> "patrol_leader";
            case "recruits:assassin" -> "assassin";
            case "recruits:siege_engineer" -> "siege_engineer";
            default -> "";
        };
    }

    /**
     * Turns one live raider into a short display name suitable for the
     * Overview threat panel. Prefers the entity's own type name (Ravager,
     * Illusioner) over the abstract combat role tag. For "recruits:*"
     * modded entities we strip the namespace and prettify.
     */
    private static String threatLabelFor(Mob mob) {
        ResourceLocation id = ForgeRegistries.ENTITY_TYPES.getKey(mob.getType());
        if (id != null) {
            String path = id.getPath();
            // Commander boss: read from role tag; the entity is otherwise a
            // generic Vindicator/Captain.
            String role = mob.getPersistentData().getString(RAID_ROLE_TAG);
            if ("commander".equals(role)) return "Commander";
            // Well-known vanilla and modded raiders get hand-picked names.
            // v2.28.0: Recruits entities added explicitly so the Threat
            // Breakdown reads "Patrol Leader" instead of the raw registry
            // path fallback — also collapses shieldman variants to one row.
            return switch (id.toString()) {
                case "minecraft:ravager" -> "Ravager";
                case "minecraft:illusioner" -> "Illusioner";
                case "minecraft:evoker" -> "Evoker";
                case "minecraft:witch" -> "Witch";
                case "minecraft:vindicator" -> "Vindicator";
                case "minecraft:pillager" -> "Pillager";
                case "recruits:recruit", "recruits:recruit_shieldman" -> "Shieldman";
                case "recruits:bowman" -> "Bowman";
                case "recruits:crossbowman" -> "Crossbowman";
                case "recruits:captain" -> "Captain";
                case "recruits:patrol_leader" -> "Patrol Leader";
                case "recruits:assassin" -> "Assassin";
                case "recruits:siege_engineer" -> "Siege Engineer";
                default -> prettyRole(path);
            };
        }
        String role = mob.getPersistentData().getString(RAID_ROLE_TAG);
        return role.isEmpty() ? "Raider" : prettyRole(role);
    }

    private static String prettyRole(String role) {
        if (role == null || role.isEmpty()) return "raider";
        // "recruit_shieldman" -> "Shieldman"
        String core = role.startsWith("recruit_") ? role.substring("recruit_".length()) : role;
        StringBuilder out = new StringBuilder();
        boolean upper = true;
        for (char c : core.toCharArray()) {
            if (c == '_') { out.append(' '); upper = true; continue; }
            out.append(upper ? Character.toUpperCase(c) : c);
            upper = false;
        }
        return out.toString();
    }

    private static int gateBreachPercent(RaidSavedData.RaidState state) {
        if (state.currentBreachBlock == null || state.currentBreachRequired <= 0) return 0;
        return Mth.clamp(state.blockBreachProgress.getOrDefault(state.currentBreachBlock.asLong(), 0) * 100 /
                state.currentBreachRequired, 0, 100);
    }

    public record DashboardSnapshot(String faction, boolean registered, boolean active, String stronghold,
                             int wave, int totalWaves, int deployed, int reinforcing, int defeated,
                             int occupationPercent, boolean breached, int breachPercent,
                             int recruits, int workers, int ships,
                             int siegeWeapons, int assetScalingEnemies, int breachedBlockCount,
                             String gateTarget, int gateBreachPercent, String cooldown,
                             int emeraldReward, boolean rewardEligible,
                             // v2.11.0 additions for tabbed Codex UI:
                             String factionId, String casusBelliId, String factionOpening,
                             String factionChant, String campDirection, int campDistance,
                             String nextWaveLabel, String nextWaveComposition,
                             int defenseScore, String defenseScoreLabel,
                             // v2.12.0 Know Your Enemy additions:
                             /** "4 Shieldman • 3 Bowman • 2 Captain" — what is deployed right now. */
                             String threatBreakdown,
                             /** Defense score explainer: "Recruits 4 + Workers 1 + Assets 2 = 5.5 defense" */
                             String defenseExplainer,
                             /** Sorted list of unit codex ids discovered by this team. */
                             java.util.List<String> discoveredUnits,
                             /** Sorted list of faction ids discovered by this team. */
                             java.util.List<String> discoveredFactions,
                             /** Newest-first list of War Journal entries. */
                             java.util.List<JournalRow> warJournal,
                             // v2.28.0 GUI-honesty additions:
                             /** True when this anchor's defense point is a synthetic claim point (name starts with "claim:"). */
                             boolean claimLinked,
                             /** Human-readable name of the linked Recruits claim; empty when {@link #claimLinked} is false. */
                             String claimName,
                             /** Which optional-mod bridges are actually loaded and available right now. Rendered on the Codex compat strip. */
                             boolean recruitsClaimsBridgeReady,
                             boolean workersBridgeReady,
                             boolean smallShipsBridgeReady,
                             boolean siegeWeaponsBridgeReady) {
        static DashboardSnapshot unavailable() {
            return new DashboardSnapshot("Unavailable", false, false, "Server unavailable", 0, 0,
                    0, 0, 0, 0, false, 0, 0, 0, 0, 0, 0,
                    0, "Unavailable", 0, "Unavailable", 0, false,
                    "", "", "", "", "", 0, "", "", 0, "Unknown",
                    "", "", java.util.List.of(), java.util.List.of(), java.util.List.of(),
                    false, "", false, false, false, false);
        }
    }

    /**
     * Wire-friendly projection of {@link RaidSavedData.WarJournal.Entry}.
     * The client renders this directly; kept as a compact record so encode/decode
     * stays trivial and the dashboard packet size grows linearly with journal
     * size, capped at {@link RaidSavedData.WarJournal#MAX_ENTRIES}.
     */
    public record JournalRow(long timestamp, String factionId, String factionName,
                             String casusBelliId, int wavesReached, int totalWaves,
                             String outcome, int emeraldPayout) {}

    private static List<ServerPlayer> onlineMembers(MinecraftServer server, String key) {
        List<ServerPlayer> result = new ArrayList<>();
        RaidSavedData.Anchor anchor = RaidSavedData.get(server).anchors.get(key);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            // Recruits factions are scoreboard teams. They are authoritative so
            // faction joins/leaves require no second roster command in this mod.
            if (key.startsWith("team:")) {
                if (teamKey(player).equals(key)) result.add(player);
            } else if (anchor != null && anchor.internalRoster()) {
                if (anchor.members().contains(player.getUUID())) result.add(player);
            } else if (teamKey(player).equals(key)) result.add(player);
        }
        return result;
    }

    private static void syncAutomaticHome(MinecraftServer server, RaidSavedData data,
                                          ServerPlayer observedPlayer, boolean force) {
        // New invasions require a placed Siege Core. Beds no longer create or move targets.
    }

    private static RaidSavedData.DefensePoint respawnPoint(MinecraftServer server, ServerPlayer player) {
        BlockPos pos = player.getRespawnPosition();
        ResourceLocation dimension;
        if (pos == null) {
            if (!RaidConfig.ALLOW_WORLD_SPAWN_FALLBACK.get()) return null;
            pos = server.overworld().getSharedSpawnPos();
            dimension = Level.OVERWORLD.location();
        } else dimension = player.getRespawnDimension().location();
        return new RaidSavedData.DefensePoint(RaidSavedData.HOME_POINT, dimension, pos.immutable());
    }

    private static void refreshIdleAnchorIdentities(MinecraftServer server, RaidSavedData data) {
        for (Map.Entry<String, RaidSavedData.Anchor> entry : new ArrayList<>(data.anchors.entrySet())) {
            String oldKey = entry.getKey();
            RaidSavedData.Anchor anchor = entry.getValue();
            if (data.raids.containsKey(oldKey) || RaidSavedData.UNKNOWN_OWNER.equals(anchor.ownerUuid())) continue;
            ServerPlayer owner = server.getPlayerList().getPlayer(anchor.ownerUuid());
            if (owner == null) continue;
            String currentKey = teamKey(owner);
            String currentDisplay = teamDisplay(owner);
            // Some faction mods assign their scoreboard team a moment after login. Never
            // downgrade a shared faction identity during that window.
            if (oldKey.startsWith("team:") && currentKey.startsWith("player:")) continue;
            if (anchor.internalRoster()) {
                if (!currentDisplay.equals(anchor.teamDisplay())) {
                    data.anchors.put(oldKey, anchor.withIdentity(oldKey, currentDisplay));
                    data.setDirty();
                }
                continue;
            }
            if (!currentKey.equals(oldKey)) {
                if (data.anchors.containsKey(currentKey)) continue;
                data.anchors.remove(oldKey);
                data.anchors.put(currentKey, anchor.withIdentity(currentKey, currentDisplay));
                data.setDirty();
            } else if (!currentDisplay.equals(anchor.teamDisplay())) {
                data.anchors.put(oldKey, anchor.withIdentity(oldKey, currentDisplay));
                data.setDirty();
            }
        }
    }

    private static boolean canManage(ServerPlayer player, RaidSavedData.Anchor anchor) {
        return player.hasPermissions(2) || !RaidConfig.OWNER_ONLY_MANAGEMENT.get() ||
                player.getUUID().equals(anchor.ownerUuid()) ||
                RecruitsBridge.factionLeader(player).filter(player.getUUID()::equals).isPresent();
    }

    /**
     * Global rolling counter of how many consecutive checks TPS has been
     * below the configured minimum. Reset to zero the moment TPS recovers.
     * Server-wide (not per-raid) because TPS is a server-wide metric — all
     * concurrent raids see the same value.
     *
     * <p>The 2.10.1 hotfix wraps the raw TPS-below-threshold check in this
     * counter so a single 60-100 ms tick spike (routine on healthy servers
     * during chunk loads or mob density peaks) no longer triggers the
     * "next wave delayed" pause. The pause only fires after TPS has been
     * below threshold for {@code minimumTpsSustainedTicks} consecutive
     * calls.
     */
    private static int belowTpsConsecutiveTicks;

    private static boolean shouldPauseForPerformance(MinecraftServer server, RaidSavedData data) {
        if (globalTrackedCount(data) >= RaidConfig.MAX_GLOBAL_RAIDERS.get()) {
            // Global cap is a hard limit, not a jitter-prone metric — no debounce.
            return true;
        }
        if (!RaidConfig.PAUSE_SPAWNING_BELOW_TPS.get()) {
            belowTpsConsecutiveTicks = 0;
            return false;
        }
        boolean belowNow = approximateTps(server) < RaidConfig.MINIMUM_TPS_TO_SPAWN.get();
        if (!belowNow) {
            belowTpsConsecutiveTicks = 0;
            return false;
        }
        belowTpsConsecutiveTicks++;
        return belowTpsConsecutiveTicks >= RaidConfig.MINIMUM_TPS_SUSTAINED_TICKS.get();
    }

    private static int globalTrackedCount(RaidSavedData data) {
        return data.raids.values().stream().mapToInt(r -> r.raiders.size() + r.campGuards.size()).sum();
    }

    private static String normalizeTeamKey(RaidSavedData data, String supplied) {
        if (data.anchors.containsKey(supplied)) return supplied;
        String prefixed = "team:" + supplied;
        return data.anchors.containsKey(prefixed) ? prefixed : supplied;
    }

    private static String teamKey(ServerPlayer player) {
        Team team = player.getTeam();
        return team == null ? "player:" + player.getUUID() : "team:" + team.getName();
    }

    private static String teamDisplay(ServerPlayer player) {
        Team team = player.getTeam();
        if (team instanceof PlayerTeam playerTeam) return playerTeam.getDisplayName().getString();
        return player.getGameProfile().getName();
    }

    private static ServerLevel getLevel(MinecraftServer server, RaidSavedData.DefensePoint point) {
        ResourceKey<Level> key = ResourceKey.create(Registries.DIMENSION, point.dimension());
        return server.getLevel(key);
    }

    private static String factionKeyForPlayer(RaidSavedData data, ServerPlayer player) {
        UUID id = player.getUUID();
        for (RaidSavedData.Anchor anchor : data.anchors.values()) {
            if (anchor.ownerUuid().equals(id) || anchor.internalRoster() && anchor.members().contains(id)) {
                return anchor.teamKey();
            }
        }
        return teamKey(player);
    }

    private static String associatedAnchorKeyForPlayer(RaidSavedData data, UUID id) {
        for (RaidSavedData.Anchor anchor : data.anchors.values()) {
            if (anchor.ownerUuid().equals(id) || anchor.internalRoster() && anchor.members().contains(id)) {
                return anchor.teamKey();
            }
        }
        return null;
    }

    private static Set<UUID> seedRoster(MinecraftServer server, ServerPlayer owner) {
        Set<UUID> members = new LinkedHashSet<>();
        members.add(owner.getUUID());
        String scoreboardKey = teamKey(owner);
        if (scoreboardKey.startsWith("team:")) {
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                if (members.size() >= RaidConfig.MAX_ROSTER_MEMBERS.get()) break;
                if (scoreboardKey.equals(teamKey(player))) members.add(player.getUUID());
            }
        }
        return members;
    }

    private static Set<UUID> seedLegacyRoster(MinecraftServer server, RaidSavedData.Anchor anchor,
                                              ServerPlayer manager) {
        Set<UUID> members = new LinkedHashSet<>(anchor.members());
        members.add(manager.getUUID());
        if (!RaidSavedData.UNKNOWN_OWNER.equals(anchor.ownerUuid())) members.add(anchor.ownerUuid());
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (teamKey(player).equals(anchor.teamKey())) members.add(player.getUUID());
        }
        return members;
    }

    private static String playerName(MinecraftServer server, UUID id) {
        if (RaidSavedData.UNKNOWN_OWNER.equals(id)) return "unclaimed";
        ServerPlayer online = server.getPlayerList().getPlayer(id);
        if (online != null) return online.getGameProfile().getName();
        return server.getProfileCache().get(id).map(profile -> profile.getName()).orElse(id.toString());
    }

    private static RaidSavedData.DefensePoint selectAutomaticPoint(MinecraftServer server,
                                                                    RaidSavedData.Anchor anchor,
                                                                    List<ServerPlayer> members) {
        RaidSavedData.DefensePoint core = com.devfarinsky.siegeoverhaul.core.SiegeCore.point(server, anchor.teamKey());
        return core != null && (!RaidConfig.REQUIRE_PLAYER_NEAR_ANCHOR.get() || hasDefenderNear(server, core, members)) ? core : null;
    }

    /**
     * v2.27.0: builds a synthetic {@link RaidSavedData.DefensePoint} anchored
     * at the center of the defender's Recruits claim, if one covers any of
     * the anchor's stored defense points. Claim center is a {@link ChunkPos};
     * we resolve it to the surface Y at the chunk's center block for a
     * usable raid target. Returns null when Recruits is absent, no friendly
     * claim overlaps, or the overworld is unavailable.
     */
    private static RaidSavedData.DefensePoint synthesizeClaimDefensePoint(MinecraftServer server,
                                                                          RaidSavedData.Anchor anchor) {
        ServerLevel overworld = server.overworld();
        if (overworld == null) return null;
        java.util.Optional<com.devfarinsky.siegeoverhaul.compat.RecruitsClaimsBridge.ClaimSnapshot> snap =
                com.devfarinsky.siegeoverhaul.compat.RecruitsClaimsBridge.resolveDefendingClaim(overworld, anchor);
        if (snap.isEmpty()) return null;
        com.devfarinsky.siegeoverhaul.compat.RecruitsClaimsBridge.ClaimSnapshot claim = snap.get();
        if (claim.center() == null) return null;
        int cx = claim.center().getMiddleBlockX();
        int cz = claim.center().getMiddleBlockZ();
        BlockPos surface = overworld.getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                new BlockPos(cx, 0, cz));
        // Use a stable claim-scoped point name so the raid state and any
        // logs consistently reference "claim:<uuid>" rather than a random
        // synthetic id per raid.
        String pointName = "claim:" + claim.claimId().toString().substring(0, 8);
        return new RaidSavedData.DefensePoint(pointName, overworld.dimension().location(), surface);
    }

    private static RaidSavedData.DefensePoint closestDefensePoint(MinecraftServer server,
                                                                   RaidSavedData.Anchor anchor,
                                                                   ServerPlayer player) {
        RaidSavedData.DefensePoint closest = null;
        double closestDistance = Double.MAX_VALUE;
        for (RaidSavedData.DefensePoint point : anchor.defensePoints().values()) {
            ServerLevel level = getLevel(server, point);
            if (level == null || player.level() != level) continue;
            double distance = player.distanceToSqr(point.pos().getX() + 0.5, point.pos().getY() + 0.5,
                    point.pos().getZ() + 0.5);
            if (distance < closestDistance) {
                closest = point;
                closestDistance = distance;
            }
        }
        return closest != null ? closest : anchor.primaryPoint();
    }

    private static String normalizePointName(String supplied) {
        if (supplied == null) return null;
        String normalized = supplied.toLowerCase(Locale.ROOT);
        return normalized.matches("[a-z0-9_-]{1,24}") ? normalized : null;
    }

    private static double approximateTps(MinecraftServer server) {
        float averageTickMs = server.getAverageTickTime();
        return averageTickMs <= 0.0F ? 20.0 : Math.min(20.0, 1000.0 / averageTickMs);
    }

    private static void giveVictoryLoot(MinecraftServer server, ServerPlayer player) {
        ResourceLocation id = ResourceLocation.tryParse(RaidConfig.VICTORY_LOOT_TABLE.get());
        if (id == null || !(player.level() instanceof ServerLevel level)) return;
        LootTable table = server.getLootData().getLootTable(id);
        LootParams params = new LootParams.Builder(level)
                .withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(player.blockPosition()))
                .withOptionalParameter(LootContextParams.THIS_ENTITY, player)
                .create(LootContextParamSets.CHEST);
        table.getRandomItems(params, stack -> giveOrDrop(player, stack));
    }

    private static int defaultEmeraldReward() {
        return RaidConfig.VICTORY_EMERALDS_BASE.get() +
                RaidConfig.VICTORY_EMERALDS_PER_WAVE.get() * RaidConfig.WAVES.get() +
                (RaidConfig.ENABLE_COMMANDER.get() ? RaidConfig.COMMANDER_EMERALD_BONUS.get() : 0);
    }

    private static int guaranteedEmeraldReward(RaidSavedData.RaidState state) {
        int reward = RaidConfig.VICTORY_EMERALDS_BASE.get() +
                RaidConfig.VICTORY_EMERALDS_PER_WAVE.get() * Math.max(0, state.wave);
        if (state.commanderDefeated) reward += RaidConfig.COMMANDER_EMERALD_BONUS.get();
        return reward;
    }

    private static void giveEmeralds(ServerPlayer player, int amount) {
        int remaining = Math.max(0, amount);
        while (remaining > 0) {
            int count = Math.min(Items.EMERALD.getMaxStackSize(), remaining);
            giveOrDrop(player, new ItemStack(Items.EMERALD, count));
            remaining -= count;
        }
    }

    /**
     * v3.2.0 — queue matching victory spoils for members who were offline
     * when their faction won a siege. Absent members are those in the
     * anchor's roster but NOT in the online winners list at reward time.
     * Each queued entry represents one full victory share: guaranteed
     * emeralds + no-breach bonus (if any) + XP + one loot roll (if enabled)
     * + trophy banner (if factionId known). Drained by /siegeoverhaul claim.
     */
    private static void queueOfflineSpoils(MinecraftServer server, RaidSavedData data,
                                            RaidSavedData.Anchor anchor, String teamKey,
                                            RaidSavedData.RaidState state, int emeralds,
                                            int noBreachBonus, int experience, boolean lootRoll,
                                            List<ServerPlayer> winners) {
        if (anchor == null) return;
        java.util.Set<UUID> onlineUuids = new java.util.HashSet<>();
        winners.forEach(p -> onlineUuids.add(p.getUUID()));
        int totalEmeralds = emeralds + noBreachBonus;
        long now = System.currentTimeMillis();
        String factionId = state == null ? "" : (state.factionId == null ? "" : state.factionId);
        String teamDisplay = anchor.teamDisplay() == null ? teamKey : anchor.teamDisplay();
        int queued = 0;
        for (UUID memberUuid : anchor.members()) {
            if (onlineUuids.contains(memberUuid)) continue;
            RaidSavedData.UnclaimedSpoils spoils = new RaidSavedData.UnclaimedSpoils(
                    factionId, totalEmeralds, Math.max(0, experience), lootRoll, now, teamDisplay);
            data.pendingSpoils.computeIfAbsent(memberUuid, k -> new ArrayList<>()).add(spoils);
            queued++;
        }
        if (queued > 0) data.setDirty();
    }

    /**
     * v3.2.0 — partial-share payout to non-faction defenders who dealt
     * damage during the siege. Splits a pool equal to 25% of the base
     * guaranteed emerald reward proportionally by damage contribution, with
     * a per-player floor of 1 emerald when they contributed at all. Online
     * ally defenders receive their share immediately with a chat message;
     * offline ally defenders get their share queued as UnclaimedSpoils.
     */
    private static void payAllyDefenders(MinecraftServer server, RaidSavedData data,
                                         RaidSavedData.Anchor anchor, String teamKey,
                                         RaidSavedData.RaidState state, int baseEmeralds) {
        if (state == null || state.allyDefenderDamage.isEmpty() || baseEmeralds <= 0) return;
        int pool = Math.max(1, baseEmeralds / 4);
        float totalDamage = 0f;
        for (float d : state.allyDefenderDamage.values()) totalDamage += Math.max(0f, d);
        if (totalDamage <= 0f) return;
        String teamDisplay = anchor == null || anchor.teamDisplay() == null ? teamKey : anchor.teamDisplay();
        long now = System.currentTimeMillis();
        for (Map.Entry<UUID, Float> entry : state.allyDefenderDamage.entrySet()) {
            float dmg = Math.max(0f, entry.getValue());
            if (dmg <= 0f) continue;
            int share = Math.max(1, Math.round(pool * (dmg / totalDamage)));
            ServerPlayer online = server.getPlayerList().getPlayer(entry.getKey());
            if (online != null) {
                giveEmeralds(online, share);
                online.sendSystemMessage(Component.literal("Ally defense reward: " + share + " emeralds for helping defend " + teamDisplay + ".")
                        .withStyle(ChatFormatting.GREEN));
            } else {
                RaidSavedData.UnclaimedSpoils spoils = new RaidSavedData.UnclaimedSpoils(
                        "", share, 0, false, now, teamDisplay + " (ally defense)");
                data.pendingSpoils.computeIfAbsent(entry.getKey(), k -> new ArrayList<>()).add(spoils);
            }
        }
        data.setDirty();
    }

    private static void giveOrDrop(ServerPlayer player, ItemStack stack) {
        if (!player.getInventory().add(stack)) player.drop(stack, false);
    }

    private static long randomCooldownTicks(RandomSource random) {
        int min = RaidConfig.MIN_COOLDOWN_MINUTES.get();
        int max = Math.max(min, RaidConfig.MAX_COOLDOWN_MINUTES.get());
        int minutes = min + random.nextInt(max - min + 1);
        return minutes * 60L * 20L;
    }

    private static String approachDirection(double angle) {
        double normalized = (angle % (Math.PI * 2.0D) + Math.PI * 2.0D) % (Math.PI * 2.0D);
        String[] directions = {"east", "southeast", "south", "southwest",
                "west", "northwest", "north", "northeast"};
        int index = (int) Math.floor((normalized + Math.PI / 8.0D) / (Math.PI / 4.0D)) & 7;
        return directions[index];
    }

    private static int nextCheckpoint(int wave) { return EndlessSiege.nextCheckpoint(wave); }

    private static String waveTitle(int wave) {
        int total = RaidConfig.WAVES.get();
        if (wave >= total) return "Command assault";
        return switch (wave) {
            case 1 -> "Vanguard";
            case 2 -> "Main assault";
            case 3 -> "Breach companies";
            case 4 -> "War-caster advance";
            default -> "Reinforcement wave";
        };
    }

    private static boolean shouldWarn(int seconds) {
        return seconds == 60 || seconds == 30 || seconds == 10 || seconds <= 5;
    }

    private static String formatPos(BlockPos p) {
        return p.getX() + ", " + p.getY() + ", " + p.getZ();
    }

    private static String formatTime(long seconds) {
        if (seconds < 60) return seconds + " seconds";
        long minutes = seconds / 60;
        long remainder = seconds % 60;
        return remainder == 0 ? minutes + " minutes" : minutes + "m " + remainder + "s";
    }

    /**
     * v4.28.8: format an in-game tick countdown for chat. 20 ticks = 1 second
     * of active play, 24000 ticks = one Minecraft day (20 real minutes).
     */
    private static String formatInterestCountdown(long ticks) {
        if (ticks <= 0) return "less than a minute";
        long seconds = ticks / 20L;
        long days = seconds / 1200L;
        long remSec = seconds - days * 1200L;
        long minutes = remSec / 60L;
        StringBuilder sb = new StringBuilder();
        if (days > 0) sb.append(days).append(" in-game day").append(days == 1 ? "" : "s");
        if (minutes > 0) {
            if (sb.length() > 0) sb.append(" ");
            sb.append(minutes).append("m");
        }
        if (sb.length() == 0) sb.append("less than a minute");
        return sb.toString();
    }

    private RaidEvents() {}
}
