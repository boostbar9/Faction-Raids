package com.devfarinsky.factionraids;

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
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.registries.ForgeRegistries;

import com.devfarinsky.factionraids.command.RaidCommands;
import com.devfarinsky.factionraids.raid.RaidBossBars;

import java.util.*;

public final class RaidEvents {
    // Shared constants live in ModConstants / raid.RaidTags; the local aliases below
    // keep the huge body of this class readable while the incremental split continues.
    private static final Component MESSAGE_PREFIX = ModConstants.MESSAGE_PREFIX;
    private static final String RAID_TEAM_TAG = ModConstants.Tags.RAID_TEAM;
    private static final String RAID_ROLE_TAG = ModConstants.Tags.RAID_ROLE;

    /**
     * Runtime handles for in-progress camp construction, keyed by team key.
     * NOT persisted — camps are ephemeral to the raid and get rebuilt on
     * every raid start; on server restart mid-raid the entities remain but
     * the phase advances immediately since we lost the tracking handle.
     */
    static final java.util.Map<String, com.devfarinsky.factionraids.camp.CampBuilder.CampState>
            ACTIVE_CAMPS = new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * Per-raid wave composition (progressive picker + formation choice).
     * Populated by queueWave and consulted by createAttackerForWave and the
     * FormationDirector tick. Cleared in finishRaid.
     */
    private static final Map<String, com.devfarinsky.factionraids.waves.WaveComposition> ACTIVE_COMPOSITIONS = new HashMap<>();
    private static int tickCounter;

    // v2.23.0 Press-the-Attack: per-raider stuck tracker. Keyed by raider UUID.
    // Value carries the last observed distance-to-objective, the tick that
    // distance was recorded, and how many escalations we have applied. Entries
    // for dead raiders age out because redirectRaiders skips missing entities
    // and finishRaid clears state. Concurrent map because multiple worlds may
    // tick raids in parallel on some server setups.
    static final java.util.Map<UUID, StuckEntry> STUCK_TRACKER =
            new java.util.concurrent.ConcurrentHashMap<>();

    static final class StuckEntry {
        double lastDistSq;
        long lastProgressGameTime;
        int escalationLevel; // 0 = fresh, 1 = jump+burst, 2 = wide-aggro + longer teleport-to-front

        StuckEntry(double distSq, long gameTime) {
            this.lastDistSq = distSq;
            this.lastProgressGameTime = gameTime;
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

    @SubscribeEvent
    public static void onEntityJoin(EntityJoinLevelEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level) || !(event.getEntity() instanceof Mob mob)) return;
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
            com.devfarinsky.factionraids.scout.ScoutManager.onScoutHurt(victim);
            return;
        }
        if (!RaidConfig.SHOUT_TO_ALLIES.get()) return;
        Entity attacker = event.getSource().getEntity();
        if (!(attacker instanceof LivingEntity livingAttacker)) return;
        // Never alert against another raider (friendly fire from vex/etc).
        if (attacker.getPersistentData().getString(RAID_TEAM_TAG).equals(team)) return;
        if (!(victim.level() instanceof ServerLevel level)) return;
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
        String defendedFaction = mob.getPersistentData().getString(RAID_TEAM_TAG);
        RaidSavedData.Anchor anchor = event.getEntity().level() instanceof ServerLevel level ?
                RaidSavedData.get(level.getServer()).anchors.get(defendedFaction) : null;
        boolean protectedWorker = RaidConfig.PROTECT_WORKERS.get() && anchor != null &&
                OptionalCompatBridge.workerBelongsToFaction(event.getEntity(), defendedFaction,
                        anchor.members());
        if (protectedVanillaCivilian || protectedWorker) {
            event.setCanceled(true);
            mob.setTarget(null);
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
                        com.devfarinsky.factionraids.effort.RaidEffortTracker
                                .onDefenderKilled(killerTeam);
                    }
                }
            }
        }

        if (victimTeamKey.isBlank()) return;
        RaidSavedData data = RaidSavedData.get(level.getServer());
        // v2.26.0 scout death: drop the intel letter and record the removal
        // in the scout mission bookkeeping. Scouts are never in a raid so we
        // return before the raid-state handling below.
        if (event.getEntity() instanceof Mob scoutVictim
                && scoutVictim.getPersistentData().getBoolean(ModConstants.Tags.SCOUT)) {
            com.devfarinsky.factionraids.scout.ScoutManager.onScoutKilled(level.getServer(), data, scoutVictim);
            return;
        }
        RaidSavedData.RaidState state = data.raids.get(victimTeamKey);
        if (state == null || !state.raiders.remove(event.getEntity().getUUID())) return;
        state.missingTicks.remove(event.getEntity().getUUID());
        state.totalDefeated++;
        if (event.getEntity().getUUID().equals(state.commanderUuid) && !state.commanderDefeated) {
            RaidSavedData.Anchor anchor = data.anchors.get(victimTeamKey);
            if (anchor != null) markCommanderDefeated(level.getServer(), anchor, state);
        }
        data.setDirty();
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
     * Give each player the Faction Raids guidebook once on first login.
     * The gift is idempotent — marked with a player-NBT flag so it never
     * duplicates on subsequent logins, dimension changes, or /kill respawns.
     */
    @SubscribeEvent
    public static void onPlayerLoggedIn(net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent event) {
        if (!RaidConfig.SPAWN_GUIDEBOOK_ON_JOIN.get()) return;
        if (!(event.getEntity() instanceof ServerPlayer sp)) return;
        net.minecraft.nbt.CompoundTag persistent = sp.getPersistentData()
                .getCompound(net.minecraft.world.entity.player.Player.PERSISTED_NBT_TAG);
        if (persistent.getBoolean("FactionRaidsGuidebookGiven")) return;
        net.minecraft.world.item.ItemStack book = new net.minecraft.world.item.ItemStack(
                com.devfarinsky.factionraids.items.ModItems.GUIDEBOOK.get());
        if (!sp.getInventory().add(book)) sp.drop(book, false);
        persistent.putBoolean("FactionRaidsGuidebookGiven", true);
        sp.getPersistentData().put(net.minecraft.world.entity.player.Player.PERSISTED_NBT_TAG, persistent);
    }

    /**
     * Ensure the shared "Raiders" faction exists as soon as the server is
     * fully started. Idempotent — subsequent restarts skip the create path
     * once the scoreboard team and Recruits faction manager entry exist.
     */
    @SubscribeEvent
    public static void onServerStarted(net.minecraftforge.event.server.ServerStartedEvent event) {
        RecruitsBridge.ensureRaidersFaction(event.getServer());
        // v2.15.0: register the role-colored glow teams so raiders can
        // join them at spawn without a per-spawn registry check.
        RaiderLabels.onServerStarted(event.getServer());
        // v2.28.0: startup audit log. Emits the exact list of player-facing
        // commands the server has just registered, plus the set of codex ids
        // the raid system can generate. Server owners can eyeball this list
        // against the wiki/README to catch drift; the Codex is baked into the
        // client, so a mismatch means an update needs to be shipped.
        FactionLogger.LOG.info("[FactionRaids] Registered player commands: {}",
                "/factionraids [menu|anchor set|anchor claim|anchor remove|home automatic|" +
                        "home refresh|territory add|territory remove|territory list|member add|" +
                        "member remove|member list|start|status|help|debug*|stop*|admin list*|" +
                        "admin stop*|admin remove*|admin repair*] (* = op-only)");
        FactionLogger.LOG.info("[FactionRaids] Codex ids the raid system can emit: {}",
                "[shieldman, bowman, crossbowman, captain, assassin, siege_engineer, patrol_leader, " +
                        "ravager, illusioner, commander]");
        FactionLogger.LOG.info("[FactionRaids] Optional-mod bridges: Recruits={} Workers={} SmallShips={} SiegeWeapons={}",
                com.devfarinsky.factionraids.compat.RecruitsClaimsBridge.available(),
                OptionalCompatBridge.isLoaded(OptionalCompatBridge.WORKERS),
                OptionalCompatBridge.isLoaded(OptionalCompatBridge.SMALL_SHIPS),
                OptionalCompatBridge.isLoaded(OptionalCompatBridge.SIEGE_WEAPONS));
    }


    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        RaidBossBars.shutdown();
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
    @SubscribeEvent
    public static void onCampBlockBroken(net.minecraftforge.event.level.BlockEvent.BreakEvent event) {
        if (!RaidConfig.CAMP_DESTRUCTIBLE_STRUCTURES.get()) return;
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        RaidSavedData data = RaidSavedData.get(level.getServer());
        BlockPos pos = event.getPos();
        for (RaidSavedData.RaidState state : data.raids.values()) {
            if (state.wave <= 0) continue;
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
                "The war camp campfire is extinguished — no reinforcements will march.")
                .withStyle(ChatFormatting.GREEN), true);
    }

    /**
     * Command banner. Broken morale ends the current wave: the wave-cleared
     * bookkeeping path in the main tick loop already handles the "raiders
     * defeated → next wave" transition, so we drop remaining raiders here
     * and let the tick loop clean up naturally.
     */
    private static void handleBannerBroken(ServerLevel level, RaidSavedData.RaidState state) {
        // Force-remove currently deployed raiders from the tracked set.
        // They still exist in the world but no longer count — defenders
        // can mop up. The wave-clear bookkeeping runs next tick.
        for (UUID id : new ArrayList<>(state.raiders)) {
            Entity entity = level.getEntity(id);
            if (entity instanceof Mob mob && mob.isAlive()) {
                // Small "scatter" push: apply a slight upward + outward
                // impulse so it visually reads as "morale broken."
                mob.setDeltaMovement(mob.getDeltaMovement().add(
                        (level.random.nextDouble() - 0.5D) * 0.6D,
                        0.3D,
                        (level.random.nextDouble() - 0.5D) * 0.6D));
            }
        }
        state.raiders.clear();
        state.missingTicks.clear();
        state.lastKnownChunks.clear();
        state.pendingWaveSpawns = 0;
        state.bannerPos = null;
        announce(level.getServer(), state.teamKey, Component.literal(
                "The command banner falls — the current wave's morale breaks and its raiders scatter.")
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
    public static int adminListCmd(CommandSourceStack s) { return adminList(s); }
    public static int adminStopCmd(CommandSourceStack s, String k) { return adminStop(s, k); }
    public static int adminRemoveCmd(CommandSourceStack s, String k) { return adminRemove(s, k); }
    public static int adminRepairCmd(CommandSourceStack s, String k) { return adminRepair(s, k); }

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
        try {
            ServerPlayer player = source.getPlayerOrException();
            if (respawnPoint(source.getServer(), player) == null) {
                source.sendFailure(Component.literal("Set a bed or respawn anchor before enabling an automatic stronghold."));
                return 0;
            }
            RaidSavedData data = RaidSavedData.get(source.getServer());
            String key = factionKeyForPlayer(data, player);
            RaidSavedData.Anchor anchor = data.anchors.get(key);
            if (anchor == null) {
                syncAutomaticHome(source.getServer(), data, player, true);
                anchor = data.anchors.get(teamKey(player));
            } else if (!canManage(player, anchor)) {
                source.sendFailure(Component.literal("Only the faction leader, home owner, or an operator can change the stronghold."));
                return 0;
            } else {
                data.anchors.put(key, anchor.withAutomaticHome(true));
                syncAutomaticHome(source.getServer(), data, player, true);
            }
            data.setDirty();
            RaidSavedData.Anchor result = data.anchors.get(teamKey(player));
            RaidSavedData.DefensePoint home = result == null ? null : result.primaryPoint();
            if (home == null) {
                source.sendFailure(Component.literal("The automatic stronghold could not be created."));
                return 0;
            }
            source.sendSuccess(() -> Component.literal("Automatic stronghold enabled at " + formatPos(home.pos()) +
                    ". It follows the faction leader's respawn point.").withStyle(ChatFormatting.GREEN), false);
            return 1;
        } catch (Exception e) {
            source.sendFailure(Component.literal("Only a player can enable an automatic stronghold."));
            return 0;
        }
    }

    private static int refreshAutomaticHome(CommandSourceStack source) {
        try {
            ServerPlayer player = source.getPlayerOrException();
            if (respawnPoint(source.getServer(), player) == null) {
                source.sendFailure(Component.literal("Set a bed or respawn anchor before refreshing the stronghold."));
                return 0;
            }
            RaidSavedData data = RaidSavedData.get(source.getServer());
            RaidSavedData.Anchor current = data.anchors.get(factionKeyForPlayer(data, player));
            if (current != null && !canManage(player, current)) {
                source.sendFailure(Component.literal("Only the faction leader, home owner, or an operator can refresh the stronghold."));
                return 0;
            }
            syncAutomaticHome(source.getServer(), data, player, true);
            RaidSavedData.Anchor anchor = data.anchors.get(teamKey(player));
            if (anchor == null) {
                source.sendFailure(Component.literal("The automatic stronghold could not be refreshed."));
                return 0;
            }
            RaidSavedData.DefensePoint home = anchor.primaryPoint();
            source.sendSuccess(() -> Component.literal("Stronghold refreshed from respawn point: " +
                    home.dimension() + " at " + formatPos(home.pos())).withStyle(ChatFormatting.GREEN), false);
            return 1;
        } catch (Exception e) {
            source.sendFailure(Component.literal("Only a player can refresh an automatic stronghold."));
            return 0;
        }
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
                source.sendFailure(Component.literal("Set your home anchor first with /factionraids anchor set"));
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
                source.sendFailure(Component.literal("The home point is moved with /factionraids anchor set, not removed."));
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
                    source.sendFailure(Component.literal("Unknown defense point. Use /factionraids territory list"));
                    return 0;
                }
            } else {
                point = anchor.automaticHome() ? respawnPoint(source.getServer(), player) :
                        closestDefensePoint(source.getServer(), anchor, player);
            }
            if (point == null) {
                source.sendFailure(Component.literal("Set a bed or respawn anchor before starting an invasion, or enable allowWorldSpawnFallback in the config."));
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
                source.sendSuccess(() -> Component.literal("Sleep in a bed or use a respawn anchor near your base, then run /factionraids home refresh."), false);
                return 1;
            }
            RaidSavedData.RaidState state = data.raids.get(key);
            source.sendSuccess(() -> MESSAGE_PREFIX.copy().append(Component.literal(anchor.teamDisplay())
                    .withStyle(ChatFormatting.GOLD)), false);
            if (state != null) {
                RaidSavedData.DefensePoint point = anchor.point(state.defensePointName);
                ServerLevel raidLevel = getLevel(source.getServer(), point);
                int occupation = state.captureTicks * 100 /
                        Math.max(1, RaidConfig.CAPTURE_TIME_SECONDS.get() * 20);
                int breach = breachPercent(state);
                source.sendSuccess(() -> Component.literal("Phase: ").withStyle(ChatFormatting.GRAY)
                        .append(Component.literal(state.wave == 0 ? "War camp forming" :
                                (!state.breached && RaidConfig.ENABLE_BREACH_PHASE.get() ?
                                        "Perimeter breach " + breach + "% — wave " + state.wave + "/" + RaidConfig.WAVES.get() :
                                        waveTitle(state.wave) + " — wave " + state.wave + "/" + RaidConfig.WAVES.get()))
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
        source.sendSuccess(() -> Component.literal("/factionraids status").withStyle(ChatFormatting.AQUA)
                .append(Component.literal(" — stronghold and siege status")), false);
        source.sendSuccess(() -> Component.literal("/factionraids start").withStyle(ChatFormatting.AQUA)
                .append(Component.literal(" — begin a controlled siege test")), false);
        source.sendSuccess(() -> Component.literal("/factionraids home refresh").withStyle(ChatFormatting.AQUA)
                .append(Component.literal(" — update the automatic stronghold from your respawn point")), false);
        source.sendSuccess(() -> Component.literal("/factionraids territory list").withStyle(ChatFormatting.AQUA)
                .append(Component.literal(" — list every defended location")), false);
        source.sendSuccess(() -> Component.literal("/factionraids debug").withStyle(ChatFormatting.AQUA)
                .append(Component.literal(" — show faction integration and performance details")), false);
        return 1;
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
                source.sendSuccess(() -> Component.literal("Raid state: wave " + state.wave + "/" + RaidConfig.WAVES.get() +
                        " at '" + state.defensePointName + "' | tracked: " + state.raiders.size() +
                        " | queued: " + state.pendingWaveSpawns + " | squads: " + state.squadsSpawned +
                        " | loaded: " + loaded + " | missing grace: " + state.missingTicks.size() +
                        " | deployed/defeated/lost: " + state.totalSpawned + "/" +
                        state.totalDefeated + "/" + state.totalEscaped +
                        " | breach: " + (state.breached ? "open" : breachPercent(state) + "%") +
                        " | occupation: " + (state.captureTicks / 20) + "s" +
                        (state.commanderUuid != null ? " | commander: " +
                                (state.commanderDefeated ? "defeated" : "active") : "")), false);
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
        data.setDirty();
        source.sendSuccess(() -> Component.literal("Reconciled invasion " + key + ": " +
                state.raiders.size() + " enemies tracked.").withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    private static void tick(MinecraftServer server) {
        RaidSavedData data = RaidSavedData.get(server);
        if (RaidConfig.AUTOMATIC_PLAYER_HOMES.get()) {
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                syncAutomaticHome(server, data, player, false);
            }
        }
        refreshIdleAnchorIdentities(server, data);
        long now = server.overworld().getGameTime();

        if (RaidConfig.AUTOMATIC_RAIDS.get() && data.raids.size() < RaidConfig.MAX_CONCURRENT_RAIDS.get()) {
            for (RaidSavedData.Anchor anchor : new ArrayList<>(data.anchors.values())) {
                if (data.raids.size() >= RaidConfig.MAX_CONCURRENT_RAIDS.get()) break;
                if (data.raids.containsKey(anchor.teamKey()) || now < anchor.nextRaidGameTime()) {
                    // Anchor is in cooldown: consider scheduling a scout mission.
                    // maybeSchedule is idempotent and cheap when already scheduled.
                    com.devfarinsky.factionraids.scout.ScoutManager.maybeSchedule(server, data, anchor);
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
        // Runs even when AUTOMATIC_RAIDS is disabled so admins can /factionraids
        // start manually while scouts remain a background flavor system.
        com.devfarinsky.factionraids.scout.ScoutManager.tick(server, data);

        for (String teamKey : new ArrayList<>(data.raids.keySet())) processRaid(server, data, teamKey);
        data.setDirty();
    }

    private static boolean beginRaid(MinecraftServer server, RaidSavedData data, RaidSavedData.Anchor anchor,
                                     RaidSavedData.DefensePoint point, boolean rewardEligible) {
        if (data.raids.size() >= RaidConfig.MAX_CONCURRENT_RAIDS.get()) return false;
        // Persist the selected player's respawn target for the full siege. This
        // allows a faction with several bases to be attacked at any member's
        // home without requiring one registered point per player.
        anchor = anchor.withPoint(point);
        data.anchors.put(anchor.teamKey(), anchor);
        RaidSavedData.RaidState state = new RaidSavedData.RaidState(anchor.teamKey(), point.name(),
                RaidConfig.WARNING_SECONDS.get() * 20);
        state.approachAngle = server.overworld().random.nextDouble() * Math.PI * 2.0D;
        state.startedGameTime = server.overworld().getGameTime();
        state.rewardEligible = rewardEligible;
        // Pick who is attacking and why. Selection is guaranteed non-null even
        // when the narrative system is disabled — fields on the returned
        // record are just left blank, and the branded fallback below reads the
        // opening line directly from the neutral narrative.
        // v2.26.0 honor the promise made by the scout intel letter: if a
        // scout mission was scheduled during this cooldown and previewed a
        // narrative to defenders, reuse that narrative here so the raid
        // matches the letter's contents. Falls back to a fresh selection
        // if no scout mission ran or if scouting is disabled.
        com.devfarinsky.factionraids.narrative.RaidNarrative previewed =
                com.devfarinsky.factionraids.scout.ScoutManager.consumePreviewedNarrative(data, anchor.teamKey());
        state.narrative = previewed != null ? previewed :
                com.devfarinsky.factionraids.narrative.RaidNarrativeSelector.select(
                        server.overworld().random, anchor.teamDisplay(), point.name());
        ServerLevel raidLevel = getLevel(server, point);
        if (raidLevel != null && RaidConfig.BUILD_WAR_CAMPS.get()) buildWarCamp(raidLevel, anchor, point, state);
        // Amphibious detection: if a large enough open-water body sits within
        // reach of the objective, mark a staging point and beach. Boat spawns
        // then run alongside land spawns for the rest of the raid.
        if (raidLevel != null) {
            com.devfarinsky.factionraids.naval.NavalStagingScanner.NavalStaging naval =
                    com.devfarinsky.factionraids.naval.NavalStagingScanner.scan(raidLevel, point.pos());
            if (naval.found()) {
                state.navalStagingPos = naval.surface();
                state.navalBeachPos = naval.beach();
                announce(server, anchor.teamKey(), Component.literal(
                        "Sails spotted offshore! A raider fleet is staging at " + formatPos(naval.surface()) +
                        " and will beach near " + formatPos(naval.beach()) + ".")
                        .withStyle(ChatFormatting.AQUA), false);
            }
            // Wave-1 prefab siege engines: spawn immediately at the war camp so
            // defenders can already see the pressure before the first squad marches.
            int placed = com.devfarinsky.factionraids.siege.SiegeConstruction.spawnPrefabEngines(
                    raidLevel, state, point.pos(), anchor.teamKey());
            if (placed > 0) {
                announce(server, anchor.teamKey(), Component.literal(
                        placed + (placed == 1 ? " siege engine has been raised at the war camp."
                                : " siege engines have been raised at the war camp."))
                        .withStyle(ChatFormatting.GOLD), false);
            }
            // Real Workers-driven camp construction phase. Runs in parallel
            // to the prefab camp for now; the prefab guarantees a visible camp
            // presence, while the Workers phase makes it feel alive when the
            // Workers mod is installed. A future PR removes the prefab once the
            // Workers path is proven at scale.
            final RaidSavedData.Anchor anchorForCamp = anchor;
            com.devfarinsky.factionraids.camp.CampBuilder.startCamp(
                            raidLevel, point.pos(), state.approachAngle,
                            Math.max(1, anchorForCamp.members().size()),
                            anchorForCamp.ownerUuid(), anchorForCamp.teamKey())
                    .ifPresent(cs -> ACTIVE_CAMPS.put(anchorForCamp.teamKey(), cs));
        }
        data.raids.put(anchor.teamKey(), state);
        data.setDirty();
        ChatFormatting accent = state.narrative != null && state.narrative.accent != null
                ? state.narrative.accent : ChatFormatting.GOLD;
        String opening = state.narrative != null && state.narrative.opening != null
                ? state.narrative.opening
                : "Enemy scouts have found " + anchor.teamDisplay() + " at '" + point.name() + "'";
        String detail = " A war camp " + (state.campPos == null ? "is forming" : "has been raised at " + formatPos(state.campPos)) +
                " to the " + approachDirection(state.approachAngle) + ". The siege begins in " +
                formatTime(RaidConfig.WARNING_SECONDS.get()) + ". Rally your Recruits and defend the stronghold.";
        announce(server, anchor.teamKey(), Component.literal(opening + detail).withStyle(accent), true);
        if (state.narrative != null && state.narrative.chant != null) {
            announce(server, anchor.teamKey(),
                    Component.literal(state.narrative.chant).withStyle(ChatFormatting.ITALIC, accent), false);
        }
        String subtitle = state.narrative != null && state.narrative.factionEpithet != null
                ? "The " + state.narrative.factionEpithet + " march from the " + approachDirection(state.approachAngle)
                : "Enemy war camp sighted to the " + approachDirection(state.approachAngle);
        showTitle(server, anchor.teamKey(), Component.literal("SIEGE INCOMING").withStyle(ChatFormatting.DARK_RED),
                Component.literal(subtitle).withStyle(accent));
        updateBossBar(server, anchor, state, false);
        return true;
    }

    private static void processRaid(MinecraftServer server, RaidSavedData data, String teamKey) {
        RaidSavedData.Anchor anchor = data.anchors.get(teamKey);
        RaidSavedData.RaidState state = data.raids.get(teamKey);
        if (anchor == null || state == null) return;
        RaidSavedData.DefensePoint point = anchor.point(state.defensePointName);
        ServerLevel level = getLevel(server, point);
        if (level == null) return;

        // Upgrade active raids from older saves exactly once. Those raids did
        // not persist a physical camp, so build one when 2.6 first processes it.
        if (RaidConfig.BUILD_WAR_CAMPS.get() && !state.campBuildAttempted) {
            buildWarCamp(level, anchor, point, state);
        }

        List<ServerPlayer> members = onlineMembers(server, teamKey);
        if (members.isEmpty() && RaidConfig.PAUSE_WHEN_FACTION_OFFLINE.get()) {
            state.offlinePauseAnnounced = true;
            setRaidMobsFrozen(level, state, true);
            updateBossBar(server, anchor, state, true);
            return;
        }
        setRaidMobsFrozen(level, state, false);
        if (state.offlinePauseAnnounced) {
            state.offlinePauseAnnounced = false;
            announce(server, teamKey, Component.literal("The paused invasion has resumed.").withStyle(ChatFormatting.YELLOW), false);
        }

        // Tick the Workers-driven camp construction phase if one is active
        // for this raid. Phase completion just logs today; the follow-up PR
        // wires this into the wave-scheduling code so the assault only starts
        // once the camp is up.
        com.devfarinsky.factionraids.camp.CampBuilder.CampState camp = ACTIVE_CAMPS.get(teamKey);
        if (camp != null && camp.tick(level)) {
            com.devfarinsky.factionraids.FactionLogger.LOG.info(
                    "Camp phase for {} reached {} — assault may proceed.",
                    teamKey, camp.phase);
        }

        reconcileTaggedMobs(level, point, state);
        updateTrackedMobs(level, state);
        // v2.28.0: Captain aura \u2014 the Unit Codex has always promised that
        // Captains buff their squad. Now they actually do: any raider tagged
        // role="captain" pulses Strength I to friendly raiders within 8
        // blocks. Refreshed every server-tick pass (20 ticks) with a 60-tick
        // effect so it never flickers between passes but decays if the
        // captain dies.
        tickCaptainAura(level, state);
        // v2.14.0: drain the deferred camp-build queue one structure at a
        // time. Runs cheaply every tick and no-ops once the queue empties.
        progressDeferredCampBuilds(level, state);
        // v2.15.0: render an objective marker column so defenders see
        // exactly where the raiders are marching. Rate-limited inside.
        broadcastObjectiveBeacon(level, point, state);
        List<Mob> recruits = alliedRecruits(level, point, anchor);
        if (RaidConfig.MOBILIZE_RECRUITS.get()) mobilizeRecruits(level, recruits, state);
        redirectRaiders(level, state, members, recruits, point);

        // Amphibious support: steer active raider boats toward the beach, and
        // let stalled ground raiders drop planks over narrow water spans.
        // Both are no-ops when the raid has no naval staging or the bridge/
        // convoy has nothing to do.
        com.devfarinsky.factionraids.naval.NavalConvoy.tick(teamKey, level);
        if (com.devfarinsky.factionraids.naval.BridgeBuilder.tick(level, state, point.pos())) {
            announce(server, teamKey, Component.literal(
                    "Raiders have laid a bridge to bypass your defenses.")
                    .withStyle(ChatFormatting.AQUA), false);
            data.setDirty();
        }

        // Siege upkeep: steer unmanned engines toward the objective, detect
        // engine kills, and detonate any sappers that reached the wall.
        int engineLosses = com.devfarinsky.factionraids.siege.SiegeDeployment.tick(
                level, state, point.pos());
        if (engineLosses > 0) {
            announce(server, teamKey, Component.literal(
                    (engineLosses == 1 ? "A siege engine" : engineLosses + " siege engines")
                            + " has been destroyed.")
                    .withStyle(ChatFormatting.GREEN), false);
            data.setDirty();
        }
        int detonations = com.devfarinsky.factionraids.siege.SapperRunner.tick(
                level, state, point.pos(), state.raiders);
        if (detonations > 0) {
            announce(server, teamKey, Component.literal(
                    (detonations == 1 ? "A demolition charge" : detonations + " demolition charges")
                            + " has breached the defenses.")
                    .withStyle(ChatFormatting.RED), false);
            data.setDirty();
        }
        // Formation cohesion: reapply the wave's shape periodically while
        // raiders advance on the objective. Rate-limited internally.
        com.devfarinsky.factionraids.waves.WaveComposition activeComposition = ACTIVE_COMPOSITIONS.get(teamKey);
        if (activeComposition != null) {
            com.devfarinsky.factionraids.formations.FormationDirector.tick(
                    level, state, point.pos(), activeComposition.formation);
        }
        // Rescue stragglers that stall on the way to the objective; drop the
        // second-time offenders from the wave count so the raid can advance.
        // v2.13.0: stragglers now dropped silently — the action bar already
        // shows the live deployed/reinforcing counts, so a fresh chat line
        // every time one raider gets stuck was pure noise.
        com.devfarinsky.factionraids.effort.StragglerTracker.tick(level, state, point.pos());
        // When raiders stall against a wall, build a temporary ladder column.
        // Rate-limited internally; ladders are tracked in campBlocks and
        // cleaned up when the raid ends via the existing camp pipeline.
        if (com.devfarinsky.factionraids.siege.LadderBuilder.tick(level, state, point.pos())) {
            announce(server, teamKey, Component.literal("Raiders have raised a ladder to scale your defenses!")
                    .withStyle(ChatFormatting.GOLD), false);
            data.setDirty();
        }
        processPhysicalBreaching(level, point, state);

        if (updateCaptureProgress(server, anchor, point, state, level, members, recruits)) {
            finishRaid(server, data, teamKey, false, false,
                    anchor.teamDisplay() + "'s stronghold has fallen to the enemy siege!");
            return;
        }

        boolean defended = hasDefenderNear(server, point, members);
        if (defended) state.abandonedTicks = 0;
        else state.abandonedTicks += 20;
        int abandonmentMinutes = RaidConfig.ABANDON_DEFEAT_MINUTES.get();
        if (abandonmentMinutes > 0 && state.abandonedTicks >= abandonmentMinutes * 60 * 20) {
            finishRaid(server, data, teamKey, false, false,
                    anchor.teamDisplay() + " abandoned its territory. The invasion has prevailed!");
            return;
        }

        if (state.pendingWaveSpawns > 0) {
            state.ticksToNextSquad -= 20;
            if (state.ticksToNextSquad <= 0) {
                if (shouldPauseForPerformance(server, data)) {
                    state.ticksToNextSquad = 10 * 20;
                    if (!state.performancePauseAnnounced) {
                        state.performancePauseAnnounced = true;
                        announce(server, teamKey, Component.literal("The next assault squad is waiting for server performance to recover.")
                                .withStyle(ChatFormatting.YELLOW), false);
                    }
                } else {
                    state.performancePauseAnnounced = false;
                    spawnNextSquad(server, level, data, anchor, point, state);
                }
            }
        }

        if (state.wave > 0 && state.pendingWaveSpawns <= 0 && state.raiders.isEmpty() &&
                state.ticksToNextWave <= 0) {
            if (state.wave >= RaidConfig.WAVES.get()) {
                finishRaid(server, data, teamKey, true, true,
                        anchor.teamDisplay() + " has crushed the enemy invasion!");
                return;
            }
            state.ticksToNextWave = RaidConfig.TIME_BETWEEN_WAVES_SECONDS.get() * 20;
            state.lastWarningSecond = Integer.MAX_VALUE;
            announce(server, teamKey, Component.literal("Wave " + state.wave + " cleared. Next wave in " +
                    RaidConfig.TIME_BETWEEN_WAVES_SECONDS.get() + " seconds.").withStyle(ChatFormatting.GREEN), false);
        }

        if (state.ticksToNextWave > 0) {
            state.ticksToNextWave -= 20;
            int seconds = Math.max(0, (state.ticksToNextWave + 19) / 20);
            if (shouldWarn(seconds) && seconds < state.lastWarningSecond) {
                state.lastWarningSecond = seconds;
                announce(server, teamKey, Component.literal("Enemy wave arrives in " + seconds + " seconds!")
                        .withStyle(ChatFormatting.GOLD), seconds == 10);
            }
            if (state.ticksToNextWave <= 0) {
                if (shouldPauseForPerformance(server, data)) {
                    state.ticksToNextWave = 10 * 20;
                    if (!state.performancePauseAnnounced) {
                        state.performancePauseAnnounced = true;
                        announce(server, teamKey, Component.literal("Next wave delayed briefly to protect server performance.")
                                .withStyle(ChatFormatting.YELLOW), false);
                    }
                } else {
                    state.performancePauseAnnounced = false;
                    queueWave(server, level, data, anchor, point, state, members, recruits);
                }
            }
        }
        updateBossBar(server, anchor, state, false);
    }

    /**
     * Reconciles the tracked-raider set each tick with what's actually in the
     * world. v2.13.0 rewrites the escape logic to be chunk-aware so raiders
     * are no longer counted as escaped when they were simply out of view but
     * their chunk was still loaded.
     *
     * <p>Rules:
     * <ul>
     *   <li>Entity found alive → refresh {@code lastKnownChunks}, clear
     *       missing timer.</li>
     *   <li>Entity found but not a live Mob → count as defeated, drop.</li>
     *   <li>Entity not found AND last-known chunk is currently loaded → the
     *       mob really is gone (probably died silently to fall damage or a
     *       mob-remover). Count as defeated, drop.</li>
     *   <li>Entity not found AND last-known chunk is unloaded → legitimate
     *       chunk-unload. Only in this case does the grace timer advance.</li>
     * </ul>
     */
    private static void updateTrackedMobs(ServerLevel level, RaidSavedData.RaidState state) {
        int grace = RaidConfig.MISSING_ENTITY_GRACE_SECONDS.get() * 20;
        Iterator<UUID> iterator = state.raiders.iterator();
        while (iterator.hasNext()) {
            UUID id = iterator.next();
            Entity entity = level.getEntity(id);
            if (entity != null && entity instanceof Mob mob && entity.isAlive()) {
                // Alive and loaded — remember where they are so we can
                // distinguish "unloaded" from "vanished" next tick.
                state.lastKnownChunks.put(id,
                        new net.minecraft.world.level.ChunkPos(entity.blockPosition()).toLong());
                state.missingTicks.remove(id);
                // v2.15.0: refresh role label + glow visibility.
                RaiderLabels.tick(mob);
                continue;
            }
            if (entity != null) {
                // Loaded but no longer a live mob (dying, removed, wrong type).
                iterator.remove();
                state.missingTicks.remove(id);
                state.lastKnownChunks.remove(id);
                state.totalDefeated++;
                continue;
            }
            // Entity is not loaded. Was their last known chunk still loaded?
            Long chunkKey = state.lastKnownChunks.get(id);
            boolean chunkLoaded = false;
            if (chunkKey != null) {
                net.minecraft.world.level.ChunkPos cp = new net.minecraft.world.level.ChunkPos(chunkKey);
                chunkLoaded = level.getChunkSource().hasChunk(cp.x, cp.z);
            }
            if (chunkLoaded) {
                // Chunk is here, mob is not — dead by some other means.
                iterator.remove();
                state.missingTicks.remove(id);
                state.lastKnownChunks.remove(id);
                state.totalDefeated++;
                continue;
            }
            // Genuinely unloaded — tick the grace timer.
            int missing = state.missingTicks.getOrDefault(id, 0) + 20;
            if (missing >= grace) {
                iterator.remove();
                state.missingTicks.remove(id);
                state.lastKnownChunks.remove(id);
                state.totalEscaped++;
            } else {
                state.missingTicks.put(id, missing);
            }
        }
    }

    /**
     * v2.28.0: Real Captain aura. For every raider whose role tag equals
     * {@code captain}, pulse Strength I to friendly raiders (raiders that
     * share this raid's team key) within 8 blocks. Effect duration is 60
     * ticks so it comfortably survives the 20-tick server-tick cadence but
     * expires quickly if the captain dies or is separated.
     *
     * <p>The pulse is one-way (captain \u2192 nearby squadmates); the captain
     * does not buff themselves, and captains do not stack (Strength I is
     * already amplitude 0, so a second captain's application is idempotent).
     * Ambient/visible flags are false so no particles fire on the raider
     * models \u2014 the buff is a mechanical effect, not a visual one.
     */
    private static void tickCaptainAura(ServerLevel level, RaidSavedData.RaidState state) {
        if (state.raiders.isEmpty()) return;
        java.util.List<Mob> captains = new java.util.ArrayList<>();
        for (UUID id : state.raiders) {
            Entity e = level.getEntity(id);
            if (!(e instanceof Mob mob) || !mob.isAlive()) continue;
            String role = mob.getPersistentData().getString(RAID_ROLE_TAG);
            if ("captain".equals(role)) captains.add(mob);
        }
        if (captains.isEmpty()) return;
        // For each captain, scan the raider set once and buff anyone in range.
        final double auraRadiusSq = 8.0 * 8.0;
        for (Mob captain : captains) {
            for (UUID id : state.raiders) {
                if (captain.getUUID().equals(id)) continue;
                Entity e = level.getEntity(id);
                if (!(e instanceof Mob squadmate) || !squadmate.isAlive()) continue;
                if (squadmate.distanceToSqr(captain) > auraRadiusSq) continue;
                squadmate.addEffect(new MobEffectInstance(
                        MobEffects.DAMAGE_BOOST, 60, 0, false, false));
            }
        }
    }

    private static void reconcileTaggedMobs(ServerLevel level, RaidSavedData.DefensePoint point,
                                            RaidSavedData.RaidState state) {
        state.reconcileTicks -= 20;
        if (state.reconcileTicks > 0) return;
        state.reconcileTicks = 10 * 20;
        double radius = RaidConfig.DEFENSE_RADIUS.get() + RaidConfig.MAX_SPAWN_DISTANCE.get() + 32.0;
        AABB area = new AABB(point.pos()).inflate(radius, 128.0, radius);
        for (Mob mob : level.getEntitiesOfClass(Mob.class, area,
                m -> state.teamKey.equals(m.getPersistentData().getString(RAID_TEAM_TAG)))) {
            if (mob.isAlive()) {
                state.raiders.add(mob.getUUID());
                state.missingTicks.remove(mob.getUUID());
            }
        }
    }

    private static void queueWave(MinecraftServer server, ServerLevel level, RaidSavedData data,
                                  RaidSavedData.Anchor anchor, RaidSavedData.DefensePoint point,
                                  RaidSavedData.RaidState state, List<ServerPlayer> members,
                                  List<Mob> recruits) {
        int nextWave = state.wave + 1;
        int playerCount = Math.max(1, members.size());
        int recruitScale = 0;
        int divisor = RaidConfig.RECRUITS_PER_EXTRA_ENEMY.get();
        if (divisor > 0) recruitScale = Math.min(RaidConfig.MAX_RECRUIT_SCALING_ENEMIES.get(),
                recruits.size() / divisor);
        OptionalCompatBridge.CompatSnapshot compat = nearbyCompatAssets(level, point, anchor);
        int assetScale = assetScalingEnemies(compat);
        int wanted = RaidConfig.BASE_ENEMIES_PER_WAVE.get() +
                (playerCount - 1) * RaidConfig.ENEMIES_PER_EXTRA_PLAYER.get() +
                (nextWave - 1) * 2 + recruitScale + assetScale;
        wanted = Math.min(wanted, RaidConfig.MAX_ACTIVE_RAIDERS.get());
        if (wanted <= 0) {
            state.ticksToNextWave = RaidConfig.SPAWN_RETRY_SECONDS.get() * 20;
            return;
        }

        state.wave = nextWave;
        state.plannedWaveSize = wanted;
        // Wave 2+: roll for an on-site siege engine build. Announcement is
        // handled inside SiegeConstruction to keep the chatter focused there.
        if (state.wave >= 2) {
            boolean built = com.devfarinsky.factionraids.siege.SiegeConstruction
                    .maybeStartLaterWaveBuild(level, state, point.pos(), anchor.teamKey());
            if (built) {
                announce(server, anchor.teamKey(), Component.literal(
                        "Raider engineers are wheeling a new siege engine into position.")
                        .withStyle(ChatFormatting.GOLD), false);
            }
        }
        state.waveStartingCount = 0;
        state.pendingWaveSpawns = wanted;
        state.squadsSpawned = 0;

        // Build the progressive composition for this wave. This picks role
        // counts (shieldmen/bowmen/captains/etc.) and a formation shape the
        // FormationDirector will hold on advance.
        com.devfarinsky.factionraids.waves.WaveComposition composition =
                com.devfarinsky.factionraids.waves.WaveComposer.compose(nextWave, RaidConfig.WAVES.get(), wanted);
        ACTIVE_COMPOSITIONS.put(anchor.teamKey(), composition);
        state.ticksToNextWave = 0;
        state.ticksToNextSquad = 0;
        state.lastWarningSecond = Integer.MAX_VALUE;
        String formationSuffix = "";
        if (RaidConfig.ANNOUNCE_WAVE_FORMATION.get() && composition != null &&
                composition.formation != com.devfarinsky.factionraids.formations.Formation.NONE &&
                !composition.label.isEmpty()) {
            formationSuffix = " — " + composition.label;
        }
        announce(server, anchor.teamKey(), Component.literal(waveTitle(state.wave) + " — wave " + state.wave + "/" +
                RaidConfig.WAVES.get() + ": " + wanted + " invaders are advancing from the " +
                approachDirection(state.approachAngle) + formationSuffix +
                scoutingSummary(recruitScale, assetScale, recruits.size(), compat))
                .withStyle(ChatFormatting.RED), true);
        if (state.wave >= RaidConfig.WAVES.get()) {
            showTitle(server, anchor.teamKey(), Component.literal("COMMAND ASSAULT")
                            .withStyle(ChatFormatting.DARK_RED),
                    Component.literal("Break the commander and hold the stronghold")
                            .withStyle(ChatFormatting.GOLD));
        }
        spawnNextSquad(server, level, data, anchor, point, state);
    }

    private static void spawnNextSquad(MinecraftServer server, ServerLevel level, RaidSavedData data,
                                       RaidSavedData.Anchor anchor, RaidSavedData.DefensePoint point,
                                       RaidSavedData.RaidState state) {
        int perSquad = RaidConfig.STAGED_SQUADS.get() ? RaidConfig.SQUAD_SIZE.get() : state.pendingWaveSpawns;
        int factionCapacity = Math.max(0, RaidConfig.MAX_ACTIVE_RAIDERS.get() - state.raiders.size());
        int globalCapacity = Math.max(0, RaidConfig.MAX_GLOBAL_RAIDERS.get() - globalTrackedCount(data));
        int wanted = Math.min(state.pendingWaveSpawns, Math.min(perSquad, Math.min(factionCapacity, globalCapacity)));
        if (wanted <= 0) {
            state.ticksToNextSquad = RaidConfig.SPAWN_RETRY_SECONDS.get() * 20;
            return;
        }

        // Naval share: when a staging point is available, route a percentage of
        // this squad into boats. The rest still spawn on land as usual.
        boolean amphibious = state.navalStagingPos != null && state.navalBeachPos != null;
        int navalShare = amphibious ? (wanted * RaidConfig.NAVAL_WAVE_SHARE_PERCENT.get() + 50) / 100 : 0;

        int spawned = 0;
        for (int i = 0; i < wanted; i++) {
            int waveIndex = state.waveStartingCount + spawned;
            Mob raider = createAttackerForWave(level, anchor.teamKey(), state.wave, waveIndex);
            if (raider == null) continue;

            boolean asNaval = i < navalShare;
            BlockPos spawn = asNaval
                    ? state.navalStagingPos
                    : findSpawnPosition(level, point.pos(), level.random, raider,
                            state.approachAngle, state.campPos);
            if (spawn == null) continue;
            raider.moveTo(spawn.getX() + 0.5, spawn.getY(), spawn.getZ() + 0.5,
                    level.random.nextFloat() * 360.0F, 0.0F);
            raider.finalizeSpawn(level, level.getCurrentDifficultyAt(spawn), MobSpawnType.EVENT, null, null);
            boolean squadLeader = spawned == 0;
            if (squadLeader && raider instanceof Raider vanillaRaider) vanillaRaider.setPatrolLeader(true);
            RecruitsBridge.configureHostileRaidRecruit(raider);
            // Assign to the shared "Raiders" faction so players can't hire
            // this mob and it doesn't friendly-fire other raiders. Faction is
            // created lazily on server-start; this call is a no-op if the
            // scoreboard team hasn't been registered yet.
            RecruitsBridge.assignToRaidersFaction(raider);
            raider.setPersistenceRequired();
            raider.getPersistentData().putString(RAID_TEAM_TAG, anchor.teamKey());
            if (level.addFreshEntity(raider)) {
                assignSiegeRole(raider, state, waveIndex, squadLeader);
                state.raiders.add(raider.getUUID());
                state.totalSpawned++;
                if (asNaval) {
                    // Hand the raider off to NavalFleet, which picks a Small
                    // Ships warship when the mod is installed and falls back
                    // to a vanilla oak boat otherwise. If the vessel fails to
                    // spawn entirely, the raider is left swimming — valid
                    // fallback rather than a hard error.
                    com.devfarinsky.factionraids.naval.NavalFleet.spawn(level,
                            raider.blockPosition()).ifPresent(vessel -> {
                        vessel.setYRot(raider.getYRot());
                        raider.startRiding(vessel, true);
                        com.devfarinsky.factionraids.naval.NavalConvoy.enlist(
                                anchor.teamKey(), vessel, state.navalBeachPos);
                    });
                }
                // Roll for sapper promotion. Cheap, capped, non-leaders only
                // so squad leaders keep their role.
                if (!squadLeader
                        && com.devfarinsky.factionraids.siege.SiegeConstruction.canPromoteSapper(state)
                        && level.random.nextInt(100) < 15) {
                    com.devfarinsky.factionraids.siege.SiegeConstruction.assignSapper(state, raider);
                }
                if (RaidConfig.SPAWN_ARRIVAL_EFFECTS.get()) {
                    level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE,
                            raider.getX(), raider.getY() + 0.5D, raider.getZ(),
                            8, 0.6D, 0.25D, 0.6D, 0.02D);
                }
                spawned++;
            }
        }
        if (spawned == 0) {
            state.ticksToNextSquad = RaidConfig.SPAWN_RETRY_SECONDS.get() * 20;
            // Retry chatter moved to the action bar — it fires often enough
            // that it deserves a transient hint, not a chat line.
            sendActionBar(server, anchor.teamKey(), Component.literal("Scouts blocked — next assault attempt in " +
                    RaidConfig.SPAWN_RETRY_SECONDS.get() + "s").withStyle(ChatFormatting.YELLOW));
            return;
        }

        state.waveStartingCount += spawned;
        state.pendingWaveSpawns -= spawned;
        state.squadsSpawned++;
        state.ticksToNextSquad = state.pendingWaveSpawns > 0 ?
                RaidConfig.SQUAD_INTERVAL_SECONDS.get() * 20 : 0;
        // v2.13.0: per-squad chat announcements removed. The action bar shows
        // the live count and refreshes every squad; opening the codex shows
        // the same info in more detail. This used to flood chat with 3-4
        // "Assault squad N entered the battlefield" lines per wave.
        sendActionBar(server, anchor.teamKey(), Component.literal("Wave " + state.wave + ": " +
                state.raiders.size() + " deployed • " + state.pendingWaveSpawns + " reinforcing")
                .withStyle(ChatFormatting.RED));
        data.setDirty();
    }

    private static void assignSiegeRole(Mob raider, RaidSavedData.RaidState state,
                                        int waveIndex, boolean squadLeader) {
        boolean commander = RaidConfig.ENABLE_COMMANDER.get() && state.wave >= RaidConfig.WAVES.get() &&
                waveIndex == 0;
        String role;
        ResourceLocation typeId = ForgeRegistries.ENTITY_TYPES.getKey(raider.getType());
        String recruitType = typeId != null && "recruits".equals(typeId.getNamespace()) ? typeId.getPath() : "";
        if (commander) role = "commander";
        else if (raider.getType() == EntityType.VINDICATOR || raider.getType() == EntityType.RAVAGER ||
                recruitType.equals("recruit") || recruitType.equals("recruit_shieldman") ||
                recruitType.equals("siege_engineer")) role = "breacher";
        else if (raider.getType() == EntityType.WITCH || raider.getType() == EntityType.EVOKER ||
                raider.getType() == EntityType.ILLUSIONER) role = "warcaster";
        else if (recruitType.equals("captain") || recruitType.equals("patrol_leader")) role = "captain";
        else if (squadLeader) role = "captain";
        else role = "marksman";
        raider.getPersistentData().putString(RAID_ROLE_TAG, role);
        // v2.15.0: name tag + role-colored glow team membership.
        RaiderLabels.applyRole(raider, role);
        // Mark this raider's unit id as discovered for the defending team.
        // We use the codex id, which for commander is fixed and for everyone
        // else is the entity type path (matches UnitCodex.Entry.id). This
        // fires per spawn so a team that watches a wave form up learns the
        // roster even before landing a hit — line-of-sight is enough.
        try {
            MinecraftServer server = raider.getServer();
            if (server != null && state != null && state.teamKey != null) {
                String codexId = codexIdFor(raider, role);
                if (!codexId.isEmpty()) {
                    markUnitDiscovered(RaidSavedData.get(server), state.teamKey, codexId);
                }
            }
        } catch (Throwable ignored) {
            // Discovery is a cosmetic dashboard feature — never let a bookkeeping
            // failure break raider spawning.
        }

        if ("breacher".equals(role)) {
            raider.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 20 * 60 * 60, 0, false, false));
        } else if ("captain".equals(role)) {
            raider.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 20 * 60 * 60, 0, false, false));
        } else if (commander) {
            var health = raider.getAttribute(Attributes.MAX_HEALTH);
            if (health != null) {
                health.setBaseValue(health.getBaseValue() * RaidConfig.COMMANDER_HEALTH_MULTIPLIER.get());
                raider.setHealth(raider.getMaxHealth());
            }
            raider.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 20 * 60 * 60, 0, false, false));
            // v2.15.0: RaiderLabels.applyRole already set the styled name.
            // Skip forcing visibility so PROXIMITY / OFF modes still apply.
            if (RaidConfig.RAIDER_LABEL_MODE.get() == RaidConfig.LabelMode.ALWAYS) {
                raider.setCustomName(Component.literal("Siege Commander").withStyle(ChatFormatting.DARK_RED));
                raider.setCustomNameVisible(true);
            }
            state.commanderUuid = raider.getUUID();
            state.commanderDefeated = false;
        }
    }

    private static void markCommanderDefeated(MinecraftServer server, RaidSavedData.Anchor anchor,
                                              RaidSavedData.RaidState state) {
        state.commanderDefeated = true;
        if (!state.breached && RaidConfig.ENABLE_BREACH_PHASE.get()) {
            state.breachTicks = Math.max(0, state.breachTicks - 30 * 20);
        } else state.captureTicks = Math.max(0, state.captureTicks - 30 * 20);
        String pressure = !state.breached && RaidConfig.ENABLE_BREACH_PHASE.get() ?
                "breach pressure" : "occupation";
        announce(server, anchor.teamKey(), Component.literal("The siege commander has fallen! Enemy " +
                        pressure + " lost 30 seconds of progress.")
                .withStyle(ChatFormatting.GREEN), true);
        sendActionBar(server, anchor.teamKey(), Component.literal("COMMANDER DEFEATED • " +
                        (state.breached ? "Occupation" : "Breach") + " pushed back")
                .withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD));
    }

    private static Mob createAttackerForWave(ServerLevel level, String teamKey, int wave, int index) {
        if (!RaidConfig.USE_RECRUIT_INVADERS.get()) return createVanillaAttacker(level, wave, index);
        if (wave >= RaidConfig.WAVES.get() && index == 1) return EntityType.RAVAGER.create(level);
        if (RaidConfig.ENABLE_ILLUSIONERS.get() && wave >= 4 && index == 2) {
            return EntityType.ILLUSIONER.create(level);
        }

        String recruitType = null;
        // Progressive composition overrides for indices > 0 (index 0 slots the commander in final wave).
        if (!(wave >= RaidConfig.WAVES.get() && index == 0)) {
            com.devfarinsky.factionraids.waves.WaveComposition comp = ACTIVE_COMPOSITIONS.get(teamKey);
            if (comp != null && RaidConfig.ENABLE_WAVE_COMPOSITION.get()) {
                // Account for reserved slots: subtract them so composition indexing
                // starts from the first non-reserved slot.
                int reserved = 0;
                if (wave >= RaidConfig.WAVES.get() && RaidConfig.ENABLE_COMMANDER.get()) reserved += 1;
                if (wave >= RaidConfig.WAVES.get()) reserved += 1; // ravager
                if (wave >= 4 && RaidConfig.ENABLE_ILLUSIONERS.get()) reserved += 1;
                int compIndex = index - reserved;
                if (compIndex >= 0) recruitType = comp.roleAt(compIndex);
            }
        }

        // Legacy fallback picker (still authoritative for commander slot and when composition is off/exhausted).
        if (recruitType == null) {
            if (wave >= RaidConfig.WAVES.get() && index == 0) recruitType = "patrol_leader";
            else if (wave >= 4 && index % 9 == 1 && ForgeRegistries.ENTITY_TYPES.containsKey(
                    new ResourceLocation("recruits", "siege_engineer"))) recruitType = "siege_engineer";
            else if (wave >= 4 && index % 8 == 3) recruitType = "assassin";
            else if (wave >= 3 && index % 7 == 0) recruitType = "captain";
            else if ((index + wave) % 4 == 0) recruitType = "recruit_shieldman";
            else if ((index + wave) % 3 == 0) recruitType = "bowman";
            else if ((index + wave) % 2 == 0) recruitType = "crossbowman";
            else recruitType = "recruit";
        }

        EntityType<?> type = ForgeRegistries.ENTITY_TYPES.getValue(new ResourceLocation("recruits", recruitType));
        Entity created = type == null ? null : type.create(level);
        if (created instanceof Mob mob) return mob;
        return index % 3 == 0 ? EntityType.VINDICATOR.create(level) : EntityType.PILLAGER.create(level);
    }

    private static Mob createVanillaAttacker(ServerLevel level, int wave, int index) {
        if (wave >= RaidConfig.WAVES.get() && index == 0) return EntityType.RAVAGER.create(level);
        if (wave >= 4 && index == 1) return EntityType.EVOKER.create(level);
        if (RaidConfig.ENABLE_ILLUSIONERS.get() && wave >= 4 && index == 2) {
            return EntityType.ILLUSIONER.create(level);
        }
        if (wave >= 3 && index == 3) return EntityType.WITCH.create(level);
        return (index + wave) % 3 == 0 ? EntityType.VINDICATOR.create(level) : EntityType.PILLAGER.create(level);
    }

    private static BlockPos findSpawnPosition(ServerLevel level, BlockPos anchor, RandomSource random, Mob mob,
                                              double approachAngle, BlockPos camp) {
        if (camp != null) {
            for (int attempt = 0; attempt < 20; attempt++) {
                double angle = random.nextDouble() * Math.PI * 2.0D;
                int distance = 4 + random.nextInt(7);
                int x = camp.getX() + Mth.floor(Math.cos(angle) * distance);
                int z = camp.getZ() + Mth.floor(Math.sin(angle) * distance);
                BlockPos candidate = safeSurfaceSpawn(level, anchor, mob, x, z);
                if (candidate != null) return candidate;
            }
        }
        int min = RaidConfig.MIN_SPAWN_DISTANCE.get();
        int max = Math.max(min, RaidConfig.MAX_SPAWN_DISTANCE.get());
        for (int attempt = 0; attempt < 32; attempt++) {
            // A siege approaches from a coherent front instead of materializing
            // in a random ring on every spawn attempt.
            double angle = approachAngle + (random.nextDouble() - 0.5D) * 0.65D;
            int distance = min + random.nextInt(max - min + 1);
            int x = anchor.getX() + Mth.floor(Math.cos(angle) * distance);
            int z = anchor.getZ() + Mth.floor(Math.sin(angle) * distance);
            BlockPos candidate = safeSurfaceSpawn(level, anchor, mob, x, z);
            if (candidate != null) return candidate;
        }
        return null;
    }

    private static BlockPos safeSurfaceSpawn(ServerLevel level, BlockPos anchor, Mob mob, int x, int z) {
        if (!level.hasChunk(x >> 4, z >> 4)) return null;
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        if (Math.abs(y - anchor.getY()) > 48) return null;
        BlockPos p = new BlockPos(x, y, z);
        BlockState ground = level.getBlockState(p.below());
        if (!level.getWorldBorder().isWithinBounds(p) || !level.getFluidState(p).isEmpty() ||
                !level.getFluidState(p.below()).isEmpty() || !level.isEmptyBlock(p) ||
                !level.isEmptyBlock(p.above()) || !ground.isFaceSturdy(level, p.below(), Direction.UP)) return null;
        mob.moveTo(x + 0.5, y, z + 0.5, 0.0F, 0.0F);
        return level.noCollision(mob) ? p : null;
    }

    /**
     * v2.14.0 war-camp construction.
     *
     * <p>Split into two phases:
     * <ol>
     *   <li><b>Core (instant, always runs).</b> Palisade, campfire, banner,
     *       barrel, forward marker banners. Defenders need to see the camp
     *       exists from tick one, and the strategic blocks (campfire /
     *       banner / barrel) have to be present the moment the raid
     *       starts so the destructible-camp mechanic works.</li>
     *   <li><b>Decorative (progressive).</b> Watchtowers, forge cluster,
     *       barracks tents. Queued into {@code state.deferredCampBuilds}
     *       and drained one structure every few seconds by
     *       {@link #progressDeferredCampBuilds(ServerLevel, RaidSavedData.RaidState)}
     *       so the camp visibly grows over ~20-30 seconds. When Villager
     *       Workers 2 is installed the existing {@code CampBuilder} +
     *       {@code WorkersBridge} path already spawns Builders alongside;
     *       they now have something to fill in around the core layout.</li>
     * </ol>
     *
     * <p>Three strategic positions are remembered on
     * {@link RaidSavedData.RaidState}: {@code campfirePos},
     * {@code bannerPos}, {@code barrelPos}. Breaking any of these triggers
     * a strategic effect — see {@link #onCampBlockBroken}.
     */
    private static void buildWarCamp(ServerLevel level, RaidSavedData.Anchor anchor,
                                     RaidSavedData.DefensePoint point,
                                     RaidSavedData.RaidState state) {
        state.campBuildAttempted = true;
        BlockPos camp = findWarCampPosition(level, anchor, point.pos(), state.approachAngle, state);
        if (camp == null) return;
        state.campPos = camp;

        final int cx = camp.getX();
        final int cz = camp.getZ();
        // v2.17.0: capture the camp's validated plane Y so every prefab
        // structure (watchtowers, forge, tents, banners) sits on the same
        // plane as the palisade instead of each building following its own
        // local heightmap. On rolling terrain the old per-column
        // surfacePosition() calls placed one watchtower base on a hill and
        // the diagonal-corner one 3 blocks lower, so the camp looked
        // shattered. validCampSurface has already guaranteed every column
        // inside the footprint is within +/-3 of this Y.
        final int cy = camp.getY();
        final int r = 9;  // palisade ring "radius" (half-extent); actual footprint 19x19.
        final double frontAngle = state.approachAngle; // camp -> objective vector

        // -----------------------------------------------------------------
        // PHASE 1: instant strategic core
        // -----------------------------------------------------------------

        // 1) Palisade ring with a front-facing sally gate.
        //
        // v2.16.4: the previous gate math measured Euclidean distance from a
        // continuous point (cos*r, sin*r) to each fence block. At diagonal
        // approach angles (~45\u00b0) that continuous point sits far from any
        // palisade block \u2014 the nearest ring blocks at (\u00b1r, y) or (x, \u00b1r)
        // ended up > 2.2 away, so ZERO fence blocks got removed and the
        // camp had no exit at all. Raiders spawned inside then couldn't get
        // out to path toward the objective.
        //
        // Fix: snap the gate to the palisade side whose normal is closest
        // to the approach direction (the axis with the larger absolute
        // component), then carve out a 3-wide gap of both fence rows on
        // that side, centered on where the approach vector crosses the
        // ring. This guarantees a passable gap on every approach angle,
        // whether cardinal or diagonal.
        final int gateHalfWidth = 1; // 1 -> gap is 3 blocks wide (\u00b11 + center)
        final boolean gateOnXAxis = Math.abs(Math.cos(frontAngle)) >= Math.abs(Math.sin(frontAngle));
        // Which side of the ring the gate cuts through, and where along that side.
        final int gateWallCoord = gateOnXAxis
                ? (Math.cos(frontAngle) >= 0 ? r : -r)
                : (Math.sin(frontAngle) >= 0 ? r : -r);
        // Where the approach vector crosses the chosen wall, clamped inside
        // the wall span so we can never carve off the wall's corner.
        final int gateCenterAlong = gateOnXAxis
                ? Mth.clamp(Mth.floor(Math.sin(frontAngle) * r), -(r - gateHalfWidth), r - gateHalfWidth)
                : Mth.clamp(Mth.floor(Math.cos(frontAngle) * r), -(r - gateHalfWidth), r - gateHalfWidth);
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                if (Math.abs(dx) != r && Math.abs(dz) != r) continue;
                // Skip the gate slot: on the chosen wall, within the gate
                // half-width of the crossing point.
                boolean isGate = gateOnXAxis
                        ? (dx == gateWallCoord && Math.abs(dz - gateCenterAlong) <= gateHalfWidth)
                        : (dz == gateWallCoord && Math.abs(dx - gateCenterAlong) <= gateHalfWidth);
                if (isGate) continue;
                BlockPos ground = surfacePosition(level, cx + dx, cz + dz);
                placeCampBlock(level, state, ground, Blocks.SPRUCE_FENCE);
                placeCampBlock(level, state, ground.above(), Blocks.SPRUCE_FENCE);
            }
        }

        // 2) Central campfire — reinforcement heart.
        BlockPos campfire = surfacePosition(level, cx, cz);
        placeCampBlock(level, state, campfire, Blocks.CAMPFIRE);
        state.campfirePos = campfire;

        // 3) Supply barrel.
        BlockPos barrel = surfacePosition(level, cx - 3, cz);
        placeCampBlock(level, state, barrel, Blocks.BARREL);
        state.barrelPos = barrel;

        // 4) Command banner on a stone platform, facing the objective.
        int bannerDx = Mth.floor(Math.cos(frontAngle) * 4.0D);
        int bannerDz = Mth.floor(Math.sin(frontAngle) * 4.0D);
        BlockPos bannerBase = surfacePosition(level, cx + bannerDx, cz + bannerDz);
        placeCampBlock(level, state, bannerBase, Blocks.STONE_BRICKS);
        placeCampBlock(level, state, bannerBase.above(), Blocks.RED_BANNER);
        state.bannerPos = bannerBase.above();

        // 5) Forward marker banners flanking the objective breach lane.
        Vec3 breach = invasionObjective(level, point, state);
        int bx = Mth.floor(breach.x);
        int bz = Mth.floor(breach.z);
        double perpendicular = state.approachAngle + Math.PI / 2.0D;
        for (int side : new int[]{-1, 1}) {
            int x = bx + Mth.floor(Math.cos(perpendicular) * 4.0D * side);
            int z = bz + Mth.floor(Math.sin(perpendicular) * 4.0D * side);
            BlockPos marker = surfacePosition(level, x, z);
            placeCampBlock(level, state, marker, Blocks.RED_WOOL);
            placeCampBlock(level, state, marker.above(), Blocks.RED_BANNER);
        }

        // -----------------------------------------------------------------
        // PHASE 2: queue decorative structures for progressive build-out
        // -----------------------------------------------------------------
        state.deferredCampBuilds.clear();

        // Four corner watchtowers (one queued build per corner). All four
        // share the palisade's plane Y so they line up as a coherent camp
        // silhouette even when the ground slopes gently across the footprint.
        int[][] corners = {{-r, -r}, {-r, r}, {r, -r}, {r, r}};
        for (int[] c : corners) {
            final int wx = cx + c[0];
            final int wz = cz + c[1];
            state.deferredCampBuilds.add(() -> buildWatchtower(level, state, wx, wz, cy));
        }

        // Forge cluster (single queued build).
        state.deferredCampBuilds.add(() -> buildForge(level, state, cx, cz, cy));

        // Two barracks tents (one queued build each).
        double rearAngle = frontAngle + Math.PI;
        int rearDx = Mth.floor(Math.cos(rearAngle) * 5.0D);
        int rearDz = Mth.floor(Math.sin(rearAngle) * 5.0D);
        int perpX = Mth.floor(-Math.sin(rearAngle) * 3.0D);
        int perpZ = Mth.floor(Math.cos(rearAngle) * 3.0D);
        for (int tent = -1; tent <= 1; tent += 2) {
            final int tx = cx + rearDx + perpX * tent;
            final int tz = cz + rearDz + perpZ * tent;
            state.deferredCampBuilds.add(() -> buildTent(level, state, tx, tz, cy));
        }
        // First deferred build fires after a short delay so the camp core
        // has visibly "settled" before the next structure appears.
        state.deferredCampCooldown = 40; // 2s
    }

    /**
     * Drains one deferred camp structure per {@code DEFERRED_INTERVAL_TICKS}
     * ticks. Called from the main raid tick loop while a raid is active.
     * No-op when the queue is empty or the cooldown hasn't elapsed.
     *
     * <p>Rate is intentionally conservative (≈3s between structures) so a
     * camp with 7 deferred builds visibly assembles over ~20s. When
     * Villager Workers 2 is loaded, Builders spawned by CampBuilder are
     * already present alongside and will animate around the placements.
     */
    private static final int DEFERRED_INTERVAL_TICKS = 60;
    private static void progressDeferredCampBuilds(ServerLevel level, RaidSavedData.RaidState state) {
        if (state.deferredCampBuilds.isEmpty()) return;
        if (state.deferredCampCooldown > 0) {
            state.deferredCampCooldown--;
            return;
        }
        Runnable next = state.deferredCampBuilds.poll();
        if (next != null) {
            try {
                next.run();
            } catch (RuntimeException ex) {
                FactionLogger.LOG.warn("Deferred camp build failed at raid {}: {}",
                        state.teamKey, ex.getMessage());
            }
        }
        state.deferredCampCooldown = DEFERRED_INTERVAL_TICKS;
    }

    /**
     * v2.15.0: server-broadcast objective beacon.
     *
     * <p>Emits a vertical column of {@link ParticleTypes#FLAME} particles
     * over the objective block so defenders can see exactly where the
     * raiders are marching. Fades out for viewers within 16 blocks — by
     * then you're already standing on the stronghold and don't need
     * pointing at it.
     *
     * <p>Uses per-player {@link ServerLevel#sendParticles(ServerPlayer,
     * net.minecraft.core.particles.ParticleOptions, boolean, double, double,
     * double, int, double, double, double, double)} so viewers outside
     * the fade radius still see it while a nearby defender doesn't get
     * spammed. "Force" is true so distance doesn't cull it — the beacon
     * needs to be visible from the far edge of the base.
     */
    private static void broadcastObjectiveBeacon(ServerLevel level,
                                                 RaidSavedData.DefensePoint point,
                                                 RaidSavedData.RaidState state) {
        if (!RaidConfig.OBJECTIVE_BEACON.get()) return;
        // No beacon while occupation is happening — raiders are on top of
        // the objective and the marker becomes noise.
        if (state.breached && state.captureTicks > 0) return;
        Vec3 objective;
        try {
            objective = invasionObjective(level, point, state);
        } catch (RuntimeException ex) {
            return;
        }
        final double fadeRadiusSq = 16.0D * 16.0D;
        for (ServerPlayer viewer : level.players()) {
            if (viewer.isSpectator()) continue;
            double dx = viewer.getX() - objective.x;
            double dz = viewer.getZ() - objective.z;
            if (dx * dx + dz * dz <= fadeRadiusSq) continue;
            // 12-block flame column, one particle per meter, slight jitter
            // for a torch-plume look. Speed 0 keeps it vertical.
            for (int dy = 0; dy < 12; dy++) {
                level.sendParticles(viewer, ParticleTypes.FLAME, true,
                        objective.x, objective.y + dy, objective.z,
                        1, 0.12D, 0.0D, 0.12D, 0.0D);
            }
        }
    }

    /**
     * One corner watchtower: 4-log column with a banner top.
     *
     * <p>v2.17.0: anchored to the camp's plane Y rather than local terrain.
     * If the ground under this corner deviates from the plane by more
     * than 3 blocks (already unlikely thanks to validCampSurface's grid
     * check) we skip this tower rather than build a floating or buried
     * one.
     */
    private static void buildWatchtower(ServerLevel level, RaidSavedData.RaidState state,
                                        int cx, int cz, int campY) {
        BlockPos base = campPlanePosition(level, cx, cz, campY);
        if (base == null) return;
        for (int dy = 0; dy < 4; dy++) {
            placeCampBlock(level, state, base.above(dy), Blocks.SPRUCE_LOG);
        }
        placeCampBlock(level, state, base.above(4), Blocks.RED_BANNER);
    }

    /**
     * Forge cluster: anvil + furnace + crafting table on the east side.
     *
     * <p>v2.17.0: anchored to the camp plane Y so the three pieces sit at
     * the same height rather than tracking each column's individual
     * surface (previously an anvil on a bump could float one block above
     * the crafting table beside it).
     */
    private static void buildForge(ServerLevel level, RaidSavedData.RaidState state,
                                   int cx, int cz, int campY) {
        BlockPos anvil = campPlanePosition(level, cx + 3, cz, campY);
        BlockPos furnace = campPlanePosition(level, cx + 3, cz + 1, campY);
        BlockPos craft = campPlanePosition(level, cx + 3, cz - 1, campY);
        if (anvil != null) placeCampBlock(level, state, anvil, Blocks.ANVIL);
        if (furnace != null) placeCampBlock(level, state, furnace, Blocks.FURNACE);
        if (craft != null) placeCampBlock(level, state, craft, Blocks.CRAFTING_TABLE);
    }

    /**
     * Places one dark-oak-and-wool tent centered on the given world column.
     * The tent is 5 wide, 5 deep, and 4 tall with an open front. Called from
     * {@link #buildWarCamp} to place the two barracks tents.
     *
     * <p>v2.17.0: the floor Y is now the camp plane Y, not the local
     * heightmap under the tent center. Previously a tent placed on a
     * gentle rise ended up with its walls half-buried on the uphill side
     * and floating on the downhill side. If the local terrain deviates
     * from the plane by more than 3 blocks we skip the tent entirely.
     */
    private static void buildTent(ServerLevel level, RaidSavedData.RaidState state, int cx, int cz, int campY) {
        BlockPos anchor = campPlanePosition(level, cx, cz, campY);
        if (anchor == null) return;
        int floorY = anchor.getY();
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                placeCampBlock(level, state, new BlockPos(cx + dx, floorY, cz + dz),
                        Blocks.DARK_OAK_PLANKS);
            }
        }
        for (int dx : new int[]{-2, 2}) {
            for (int dz : new int[]{-2, 2}) {
                for (int dy = 1; dy <= 3; dy++) {
                    placeCampBlock(level, state, new BlockPos(cx + dx, floorY + dy, cz + dz),
                            Blocks.SPRUCE_LOG);
                }
            }
        }
        // Canopy roof.
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                placeCampBlock(level, state, new BlockPos(cx + dx, floorY + 4, cz + dz),
                        Blocks.RED_WOOL);
            }
        }
        // Rear + side walls; leaves front (dz = -2) open.
        for (int dx = -2; dx <= 2; dx++) {
            for (int dy = 1; dy <= 3; dy++) {
                placeCampBlock(level, state, new BlockPos(cx + dx, floorY + dy, cz + 2),
                        Blocks.RED_WOOL);
            }
        }
        for (int dz = -1; dz <= 1; dz++) {
            for (int dy = 1; dy <= 3; dy++) {
                placeCampBlock(level, state, new BlockPos(cx - 2, floorY + dy, cz + dz),
                        Blocks.RED_WOOL);
                placeCampBlock(level, state, new BlockPos(cx + 2, floorY + dy, cz + dz),
                        Blocks.RED_WOOL);
            }
        }
    }

    /**
     * v2.16.1 - accepts the RaidState so the naval staging position can
     * veto sites that would place the palisade on top of the boat spawn.
     */
    private static BlockPos findWarCampPosition(ServerLevel level, RaidSavedData.Anchor anchorRecord,
                                                 BlockPos anchor, double approachAngle,
                                                 RaidSavedData.RaidState state) {
        int min = RaidConfig.MIN_SPAWN_DISTANCE.get();
        int max = Math.max(min, RaidConfig.MAX_SPAWN_DISTANCE.get());
        BlockPos navalStaging = state == null ? null : state.navalStagingPos;
        int navalGuardSq = CAMP_NAVAL_MIN_DISTANCE * CAMP_NAVAL_MIN_DISTANCE;
        // v2.27.0: resolve the defender's Recruits claim once per camp
        // search. When present + config on, reject candidate origins whose
        // chunk lies inside the defender's claimed footprint so raiders
        // don't build siege infrastructure inside the walls they're
        // supposed to be breaching.
        java.util.Set<net.minecraft.world.level.ChunkPos> excludedChunks = java.util.Collections.emptySet();
        if (RaidConfig.CLAIM_AWARE_ANCHORS.get() && RaidConfig.RESPECT_DEFENDER_CLAIMS.get()
                && anchorRecord != null
                && com.devfarinsky.factionraids.compat.RecruitsClaimsBridge.available()) {
            java.util.Optional<com.devfarinsky.factionraids.compat.RecruitsClaimsBridge.ClaimSnapshot> snap =
                    com.devfarinsky.factionraids.compat.RecruitsClaimsBridge.resolveDefendingClaim(level, anchorRecord);
            if (snap.isPresent()) excludedChunks = snap.get().chunks();
        }
        for (int attempt = 0; attempt < 32; attempt++) {
            double angle = approachAngle + (level.random.nextDouble() - 0.5D) * 0.5D;
            int distance = Math.max(min, max - level.random.nextInt(Math.max(1, Math.min(16, max - min + 1))));
            int x = anchor.getX() + Mth.floor(Math.cos(angle) * distance);
            int z = anchor.getZ() + Mth.floor(Math.sin(angle) * distance);
            if (!level.hasChunk(x >> 4, z >> 4)) continue;
            if (!excludedChunks.isEmpty()
                    && excludedChunks.contains(new net.minecraft.world.level.ChunkPos(x >> 4, z >> 4))) continue;
            BlockPos center = surfacePosition(level, x, z);
            if (!validCampSurface(level, center, anchor)) continue;
            // v2.16.1 - keep the palisade clear of the boat spawn. The
            // camp footprint is 19x19 (9 per side + gate); anything closer
            // than 24 blocks would put boats inside the fence.
            if (navalStaging != null) {
                int ddx = center.getX() - navalStaging.getX();
                int ddz = center.getZ() - navalStaging.getZ();
                if (ddx * ddx + ddz * ddz < navalGuardSq) continue;
            }
            return center;
        }
        return null;
    }

    /**
     * Horizontal buffer (blocks, squared distance) between the war camp
     * center and the naval staging point. Palisade half-extent is 9 plus
     * a two-block breathing gap, so 24 keeps the fence and boats clearly
     * separate even on jittered spawns.
     */
    private static final int CAMP_NAVAL_MIN_DISTANCE = 24;

    private static boolean validCampSurface(ServerLevel level, BlockPos center, BlockPos anchor) {
        if (!level.getWorldBorder().isWithinBounds(center) || Math.abs(center.getY() - anchor.getY()) > 48 ||
                !level.getBlockState(center).canBeReplaced()) return false;
        // v2.16.1: the center block is *above* the ground. Water detection
        // has to look at what the palisade will actually stand on, which
        // is center.below(). Previously we only checked getFluidState(center)
        // - always air over water because MOTION_BLOCKING_NO_LEAVES returns
        // the block *above* the top water block - so camps happily spawned
        // on the ocean.
        if (isWaterOrLava(level, center) || isWaterOrLava(level, center.below())) return false;
        if (!level.getBlockState(center.below()).isFaceSturdy(level, center.below(), Direction.UP)) return false;
        // v2.17.0: sample the whole footprint on a coarse grid (every 3
        // blocks across the 19x19 palisade), not just the corners. A
        // corners-only check let camps spawn where a puddle or ravine
        // sat mid-footprint, so shallow water at the interior would end
        // up with fences submerged. Also reject if the terrain height at
        // any sample deviates from the center by more than 3 blocks so
        // watchtowers and tents don't end up floating or buried when the
        // palisade sits across a ridge.
        final int r = 9;
        for (int dx = -r; dx <= r; dx += 3) {
            for (int dz = -r; dz <= r; dz += 3) {
                BlockPos sample = surfacePosition(level, center.getX() + dx, center.getZ() + dz);
                if (Math.abs(sample.getY() - center.getY()) > 3) return false;
                // Check the sample (always air, being top-of-column) AND the
                // block just below, which is what a fence or a tent floor
                // actually stands on. Both must be dry.
                if (isWaterOrLava(level, sample) || isWaterOrLava(level, sample.below())) return false;
            }
        }
        return true;
    }

    /**
     * v2.16.1 - true when the block at {@code pos} contains any fluid
     * (water or lava). Used by camp validation so palisades never spawn
     * with any part of their footprint sitting on liquid.
     */
    private static boolean isWaterOrLava(ServerLevel level, BlockPos pos) {
        return !level.getFluidState(pos).isEmpty();
    }

    private static BlockPos surfacePosition(ServerLevel level, int x, int z) {
        return new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
    }

    /**
     * v2.17.0: helper for prefab camp builders. Returns a position at the
     * camp's plane Y (the validated flat center Y) rather than the local
     * heightmap. On rolling terrain this keeps watchtower bases, tent
     * floors, and forge platforms coplanar with the palisade instead of
     * following every dip and rise, which used to produce floating tents
     * and buried anvils. Returns null if the local terrain deviates from
     * campY by more than 3 blocks -- caller should skip placement so we
     * never sink structures into cliffs or hang them in mid-air.
     */
    private static BlockPos campPlanePosition(ServerLevel level, int x, int z, int campY) {
        int localTop = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        if (Math.abs(localTop - campY) > 3) return null;
        return new BlockPos(x, campY, z);
    }

    private static void placeCampBlock(ServerLevel level, RaidSavedData.RaidState state,
                                       BlockPos pos, Block block) {
        if (!level.getWorldBorder().isWithinBounds(pos) || !level.getFluidState(pos).isEmpty() ||
                !level.getBlockState(pos).canBeReplaced()) return;
        if (!level.setBlock(pos, block.defaultBlockState(), 3)) return;
        ResourceLocation id = ForgeRegistries.BLOCKS.getKey(block);
        if (id != null) state.campBlocks.put(pos.asLong(), id.toString());
    }

    private static void cleanupWarCamp(ServerLevel level, RaidSavedData.RaidState state) {
        if (!RaidConfig.CLEANUP_WAR_CAMPS.get()) return;
        // Remove banners and canopy before their supports so neighbor updates
        // cannot pop temporary camp blocks into collectible item drops.
        List<Map.Entry<Long, String>> placed = new ArrayList<>(state.campBlocks.entrySet());
        placed.sort((left, right) -> Integer.compare(
                BlockPos.of(right.getKey()).getY(), BlockPos.of(left.getKey()).getY()));
        for (Map.Entry<Long, String> entry : placed) {
            BlockPos pos = BlockPos.of(entry.getKey());
            ResourceLocation current = ForgeRegistries.BLOCKS.getKey(level.getBlockState(pos).getBlock());
            if (current != null && current.toString().equals(entry.getValue())) {
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
            }
        }
        state.campBlocks.clear();
    }

    private static void processPhysicalBreaching(ServerLevel level, RaidSavedData.DefensePoint point,
                                                 RaidSavedData.RaidState state) {
        BlockPos previousTarget = state.currentBreachBlock;
        state.currentBreachBlock = null;
        state.currentBreachRequired = 0;
        if (!RaidConfig.ENABLE_GATE_BREACHING.get() || state.wave <= 0 ||
                state.breachedBlocks.size() >= RaidConfig.MAX_RESTORABLE_BLOCKS.get()) {
            if (previousTarget != null) level.destroyBlockProgress(breakerAnimationId(state), previousTarget, -1);
            return;
        }

        Vec3 objective = invasionObjective(level, point, state);
        // v2.16.0: also remember which breachers picked each target so we
        // can command them to actually walk up and swing at it below.
        Map<BlockPos, Integer> pressure = new HashMap<>();
        Map<BlockPos, List<Mob>> contributors = new HashMap<>();
        int evaluatedBreachers = 0;
        for (UUID id : state.raiders) {
            Entity entity = level.getEntity(id);
            if (!(entity instanceof Mob mob) || !mob.isAlive()) continue;
            String role = mob.getPersistentData().getString(RAID_ROLE_TAG);
            if (!role.equals("breacher") && !role.equals("commander")) continue;
            if (++evaluatedBreachers > 8) break;
            BlockPos target = findNearbyBreachableBlock(level, mob, objective, point.pos(), state);
            if (target != null) {
                BlockPos key = target.immutable();
                pressure.merge(key, 1, Integer::sum);
                contributors.computeIfAbsent(key, k -> new ArrayList<>()).add(mob);
            }
        }
        if (pressure.isEmpty()) {
            if (previousTarget != null) level.destroyBlockProgress(breakerAnimationId(state), previousTarget, -1);
            state.blockBreachProgress.replaceAll((position, progress) -> Math.max(0, progress - 1));
            state.blockBreachProgress.values().removeIf(progress -> progress <= 0);
            return;
        }

        Map.Entry<BlockPos, Integer> focus = pressure.entrySet().stream()
                .max(Map.Entry.comparingByValue()).orElse(null);
        if (focus == null) return;
        BlockPos target = focus.getKey();
        List<Mob> targetContributors = contributors.getOrDefault(target, List.of());
        BlockState targetState = level.getBlockState(target);
        int required = breachWorkRequired(targetState);
        if (previousTarget != null && !previousTarget.equals(target)) {
            level.destroyBlockProgress(breakerAnimationId(state), previousTarget, -1);
        }
        state.blockBreachProgress.replaceAll((position, oldProgress) ->
                position == target.asLong() ? oldProgress : Math.max(0, oldProgress - 1));
        state.blockBreachProgress.values().removeIf(oldProgress -> oldProgress <= 0);
        int progress = state.blockBreachProgress.merge(target.asLong(), Math.min(4, focus.getValue()), Integer::sum);
        state.currentBreachBlock = target;
        state.currentBreachRequired = required;
        level.destroyBlockProgress(breakerAnimationId(state), target,
                Mth.clamp(progress * 10 / Math.max(1, required), 0, 9));

        boolean swingTick = progress == 1 || progress % Math.max(2, required / 4) == 0;
        if (swingTick) {
            level.playSound(null, target, SoundEvents.ZOMBIE_ATTACK_WOODEN_DOOR,
                    SoundSource.HOSTILE, 0.75F, 0.75F + level.random.nextFloat() * 0.25F);
            level.sendParticles(ParticleTypes.CRIT, target.getX() + 0.5D, target.getY() + 0.5D,
                    target.getZ() + 0.5D, 6, 0.35D, 0.35D, 0.35D, 0.05D);
        }
        // v2.16.0: command the contributing breachers to actually walk up
        // to the block and swing at it, so the visual matches the credit.
        driveBreachersToTarget(level, targetContributors, target, swingTick);
        if (progress >= required) breachAndRemember(level, state, target);
    }

    /**
     * v2.16.0 "Physical Breaching": make the credited breachers actually
     * walk up to the target block, face it, and swing at it.
     *
     * <p>The block-breach system is server-side accounting - a raider in
     * the 7x4x7 scan box around a door adds pressure to it. Before this
     * pass, a raider could stand three blocks away doing nothing while
     * the door visibly broke. That undermined player trust in the mechanic.
     *
     * <p>Behavior:
     * <ul>
     *   <li>No-op when the {@code PHYSICAL_BREACHING} config is off.</li>
     *   <li>Sorts contributors by distance to the target and drives the
     *       nearest two only - a whole squad piling onto one door pathfinds
     *       badly and looks ridiculous. Other contributors still credit
     *       pressure so the door breaks at the same speed.</li>
     *   <li>Path target is the nearest air-space neighbor of the block
     *       (so the raider stops <em>next</em> to the door, not inside it).</li>
     *   <li>Every tick: force look-at so the head tracks the door.</li>
     *   <li>On {@code swingTick}: fire {@link Mob#swing(InteractionHand)}
     *       so the arm animation lines up with the door-hit sound.</li>
     * </ul>
     */
    private static void driveBreachersToTarget(ServerLevel level, List<Mob> contributors,
                                               BlockPos target, boolean swingTick) {
        if (contributors.isEmpty()) return;
        if (!RaidConfig.PHYSICAL_BREACHING.get()) return;

        // v2.16.2: filter out ravagers before sorting. A ravager has a
        // ~2x2 hitbox and cannot fit into the 1-block-wide neighbor slot
        // beside a door - forcing it to try just makes it loop on nav
        // failures. Ravagers still credit breach pressure (they scan the
        // same 7x4x7 box) and their normal charge AI carries them into
        // the wall for cosmetic ramming; they just don't get the "stand
        // and swing" treatment.
        List<Mob> eligible = new ArrayList<>(contributors.size());
        for (Mob mob : contributors) {
            if (mob.getType() == EntityType.RAVAGER) continue;
            eligible.add(mob);
        }
        if (eligible.isEmpty()) return;

        // Nearest two contributors only. Squeezing 8 mobs into one 1x2
        // slot is worse than letting them idle - the extras still credit
        // pressure so the door breaks at the same speed.
        eligible.sort((a, b) -> Double.compare(
                a.distanceToSqr(target.getX() + 0.5, target.getY() + 0.5, target.getZ() + 0.5),
                b.distanceToSqr(target.getX() + 0.5, target.getY() + 0.5, target.getZ() + 0.5)));
        int drive = Math.min(2, eligible.size());

        // Pick the best stand-next-to-the-block position: the horizontal
        // neighbor closest to the leading breacher, that the raider can
        // stand in without suffocating.
        Mob leader = eligible.get(0);
        BlockPos standPos = pickBreacherStandPos(level, target, leader);

        for (int i = 0; i < drive; i++) {
            Mob mob = eligible.get(i);
            // v2.16.2: re-issue the nav command every tick when the mob
            // isn't already adjacent. The breacher's own goal system
            // (MeleeAttackGoal, WalkTowardsTargetGoal from Recruits, etc.)
            // fights us by re-pathing toward the objective - the 4-block
            // gate we had in v2.16.0 let the mob's AI win and it faced
            // away while swinging. 2.25-block gate + every-tick re-issue
            // keeps the raider oriented on the door.
            if (standPos != null) {
                double distSq = mob.distanceToSqr(standPos.getX() + 0.5,
                        standPos.getY(), standPos.getZ() + 0.5);
                // Adjacent = 1.5^2 = 2.25. Beyond that, force the path
                // every tick regardless of nav.isDone(); the cost is a
                // path recompute for at most 2 mobs per raid tick, well
                // under the pathfinding budget.
                if (distSq > 2.25D) {
                    mob.getNavigation().moveTo(
                            standPos.getX() + 0.5, standPos.getY(), standPos.getZ() + 0.5, 1.15D);
                }
            }
            // Head tracks the target every tick so the mob "looks at"
            // what it's hitting.
            mob.getLookControl().setLookAt(
                    target.getX() + 0.5, target.getY() + 0.5, target.getZ() + 0.5);
            // Swing on the same cadence as the door-hit sound. Only the
            // leading breacher swings so a whole crowd doesn't strobe.
            if (swingTick && i == 0) {
                mob.swing(InteractionHand.MAIN_HAND);
            }
        }
    }

    /**
     * Pick a walkable neighbor position for the breacher to stand in
     * while attacking the target block. Prefers the horizontal neighbor
     * closest to the leader; falls back to any air-space neighbor with
     * solid ground under it. Returns null when no reasonable slot exists
     * (in that case the caller skips pathing but still swings in place).
     */
    private static BlockPos pickBreacherStandPos(ServerLevel level, BlockPos target, Mob leader) {
        BlockPos best = null;
        double bestDistSq = Double.MAX_VALUE;
        BlockPos[] neighbors = {
                target.north(), target.south(), target.east(), target.west()
        };
        for (BlockPos candidate : neighbors) {
            // Feet air, head air, block below solid enough to stand on.
            if (!level.getBlockState(candidate).isAir()) continue;
            if (!level.getBlockState(candidate.above()).isAir()) continue;
            if (!level.getBlockState(candidate.below()).isSolid()) continue;
            double distSq = leader.distanceToSqr(
                    candidate.getX() + 0.5, candidate.getY(), candidate.getZ() + 0.5);
            if (distSq < bestDistSq) {
                best = candidate.immutable();
                bestDistSq = distSq;
            }
        }
        return best;
    }

    private static int breakerAnimationId(RaidSavedData.RaidState state) {
        return 0x60000000 ^ state.teamKey.hashCode();
    }

    private static BlockPos findNearbyBreachableBlock(ServerLevel level, Mob mob, Vec3 objective,
                                                       BlockPos stronghold,
                                                       RaidSavedData.RaidState state) {
        BlockPos origin = mob.blockPosition();
        double maximumDistanceSq = (double) RaidConfig.DEFENSE_RADIUS.get() * RaidConfig.DEFENSE_RADIUS.get();
        BlockPos best = null;
        double bestScore = Double.MAX_VALUE;
        for (BlockPos candidate : BlockPos.betweenClosed(origin.offset(-3, -1, -3), origin.offset(3, 2, 3))) {
            if (candidate.distSqr(stronghold) > maximumDistanceSq ||
                    state.campBlocks.containsKey(candidate.asLong())) continue;
            BlockState blockState = level.getBlockState(candidate);
            if (!isBreachableDefense(blockState)) continue;
            double mobDistance = candidate.distSqr(origin);
            double objectiveDistance = Vec3.atCenterOf(candidate).distanceToSqr(objective);
            double score = mobDistance * 4.0D + objectiveDistance * 0.02D;
            if (score < bestScore) {
                best = candidate.immutable();
                bestScore = score;
            }
        }
        return best;
    }

    private static boolean isBreachableDefense(BlockState state) {
        Block block = state.getBlock();
        if (state.hasProperty(BlockStateProperties.OPEN) && state.getValue(BlockStateProperties.OPEN)) return false;
        return block instanceof DoorBlock || block instanceof FenceGateBlock ||
                block instanceof TrapDoorBlock || block instanceof FenceBlock || block == Blocks.IRON_BARS;
    }

    private static int breachWorkRequired(BlockState state) {
        boolean reinforced = state.getBlock() == Blocks.IRON_DOOR || state.getBlock() == Blocks.IRON_TRAPDOOR ||
                state.getBlock() == Blocks.IRON_BARS;
        return reinforced ? RaidConfig.REINFORCED_BREACH_SECONDS.get() : RaidConfig.WOODEN_BREACH_SECONDS.get();
    }

    private static void breachAndRemember(ServerLevel level, RaidSavedData.RaidState raid, BlockPos target) {
        boolean firstPhysicalBreach = raid.breachedBlocks.isEmpty();
        List<BlockPos> affected = new ArrayList<>();
        affected.add(target.immutable());
        BlockState initial = level.getBlockState(target);
        if (initial.getBlock() instanceof DoorBlock) {
            for (BlockPos adjacent : List.of(target.above(), target.below())) {
                if (level.getBlockState(adjacent).getBlock() == initial.getBlock()) affected.add(adjacent.immutable());
            }
        }
        affected.removeIf(position -> raid.breachedBlocks.containsKey(position.asLong()));
        int capacity = RaidConfig.MAX_RESTORABLE_BLOCKS.get() - raid.breachedBlocks.size();
        if (affected.isEmpty() || affected.size() > capacity) {
            // v2.19.0 RE2: drop the target's progress entry when we bail on
            // capacity. Otherwise progress stays >= required and this method
            // re-enters and re-bails every tick, livelocking breachers on a
            // door that will never break. Clearing progress lets the breacher
            // pick a different block on the next tick.
            raid.blockBreachProgress.remove(target.asLong());
            raid.currentBreachBlock = null;
            raid.currentBreachRequired = 0;
            level.destroyBlockProgress(breakerAnimationId(raid), target, -1);
            return;
        }

        for (BlockPos position : affected) {
            BlockState state = level.getBlockState(position);
            if (!isBreachableDefense(state)) continue;
            raid.breachedBlocks.put(position.asLong(), serializeBlockState(state));
        }
        affected.sort((left, right) -> Integer.compare(right.getY(), left.getY()));
        for (BlockPos position : affected) {
            if (raid.breachedBlocks.containsKey(position.asLong())) {
                level.setBlock(position, Blocks.AIR.defaultBlockState(), 3);
                raid.blockBreachProgress.remove(position.asLong());
            }
        }
        raid.currentBreachBlock = null;
        raid.currentBreachRequired = 0;
        level.destroyBlockProgress(breakerAnimationId(raid), target, -1);
        level.playSound(null, target, SoundEvents.ZOMBIE_BREAK_WOODEN_DOOR,
                SoundSource.HOSTILE, 1.1F, 0.8F);
        level.sendParticles(ParticleTypes.POOF, target.getX() + 0.5D, target.getY() + 0.5D,
                target.getZ() + 0.5D, 14, 0.5D, 0.75D, 0.5D, 0.08D);
        sendActionBar(level.getServer(), raid.teamKey, Component.literal("DEFENSE BROKEN • " +
                raid.breachedBlocks.size() + " block(s) queued for repair").withStyle(ChatFormatting.RED));
        if (RaidConfig.ENABLE_EFFORT_BONUS.get()) {
            com.devfarinsky.factionraids.effort.RaidEffortTracker.onBreachTick(raid.teamKey);
        }
        if (firstPhysicalBreach || raid.breachedBlocks.size() % 5 == 0) {
            announce(level.getServer(), raid.teamKey, Component.literal("The attackers have broken through a defense at " +
                    formatPos(target) + ". It is queued for restoration after the siege.")
                    .withStyle(ChatFormatting.DARK_RED), false);
        }
    }

    private static CompoundTag serializeBlockState(BlockState state) {
        CompoundTag tag = new CompoundTag();
        ResourceLocation id = ForgeRegistries.BLOCKS.getKey(state.getBlock());
        if (id == null) return tag;
        tag.putString("Name", id.toString());
        CompoundTag properties = new CompoundTag();
        for (Property<?> property : state.getProperties()) {
            properties.putString(property.getName(), propertyValueName(state, property));
        }
        tag.put("Properties", properties);
        return tag;
    }

    private static <T extends Comparable<T>> String propertyValueName(BlockState state, Property<T> property) {
        return property.getName(state.getValue(property));
    }

    private static BlockState deserializeBlockState(CompoundTag tag) {
        ResourceLocation id = ResourceLocation.tryParse(tag.getString("Name"));
        Block block = id == null ? null : ForgeRegistries.BLOCKS.getValue(id);
        if (block == null || block == Blocks.AIR) return Blocks.AIR.defaultBlockState();
        BlockState state = block.defaultBlockState();
        CompoundTag properties = tag.getCompound("Properties");
        for (Property<?> property : state.getProperties()) {
            if (properties.contains(property.getName())) {
                state = applySerializedProperty(state, property, properties.getString(property.getName()));
            }
        }
        return state;
    }

    private static <T extends Comparable<T>> BlockState applySerializedProperty(BlockState state,
                                                                                 Property<T> property,
                                                                                 String value) {
        return property.getValue(value).map(parsed -> state.setValue(property, parsed)).orElse(state);
    }

    private static void restoreBreachedBlocks(ServerLevel level, RaidSavedData.RaidState raid) {
        if (!RaidConfig.RESTORE_BREACHED_BLOCKS.get()) return;
        List<Map.Entry<Long, CompoundTag>> blocks = new ArrayList<>(raid.breachedBlocks.entrySet());
        blocks.sort(Comparator.comparingInt(entry -> BlockPos.of(entry.getKey()).getY()));
        int restoredCount = 0;
        int preservedCount = 0;
        for (Map.Entry<Long, CompoundTag> entry : blocks) {
            BlockPos position = BlockPos.of(entry.getKey());
            if (!level.getBlockState(position).isAir()) {
                preservedCount++;
                continue;
            }
            BlockState restored = deserializeBlockState(entry.getValue());
            if (!restored.isAir() && level.setBlock(position, restored, 3)) restoredCount++;
        }
        raid.breachedBlocks.clear();
        raid.blockBreachProgress.clear();
        if (restoredCount > 0 || preservedCount > 0) {
            announce(level.getServer(), raid.teamKey, Component.literal("Siege recovery restored " + restoredCount +
                    " defense block(s)" + (preservedCount > 0 ? "; " + preservedCount +
                    " player-replaced position(s) were preserved." : ".")).withStyle(ChatFormatting.GREEN), false);
        }
    }

    /**
     * v2.14.0 focused aggression pass.
     *
     * <p>Prior versions used {@code DEFENSE_RADIUS} (default 160) as the
     * defender-acquire range, which meant raiders that spawned 100 blocks
     * out would spot a defender inside the base, lock onto them, and let
     * vanilla {@code MeleeAttackGoal} path them all the way in — which is
     * why Devin was seeing raiders "running around inside the base"
     * instead of pushing the objective.
     *
     * <p>New rules:
     * <ul>
     *   <li>Defender aggro is capped by {@code AGGRO_RADIUS} (default 40).
     *       Farther defenders are ignored, not targeted.</li>
     *   <li>Only defenders on the raider's objective-side hemisphere are
     *       eligible — we don't chase somebody who is behind us.</li>
     *   <li>Breachers and the commander skip defender acquisition
     *       entirely when {@code BREACHERS_IGNORE_DEFENDERS} is on so gate
     *       breach progress is uninterrupted.</li>
     *   <li>If the raider has drifted more than
     *       {@code OFF_AXIS_DRIFT_LIMIT} blocks perpendicular to the
     *       invasion axis, the current target is dropped and the raider
     *       is re-anchored to the objective.</li>
     * </ul>
     */
    private static void redirectRaiders(ServerLevel level, RaidSavedData.RaidState state,
                                        List<ServerPlayer> members, List<Mob> recruits,
                                        RaidSavedData.DefensePoint point) {
        boolean glow = RaidConfig.GLOW_FINAL_ENEMIES.get() && state.raiders.size() <= 3;
        Vec3 objective = invasionObjective(level, point, state);
        double baseSpeed = RaidConfig.RAIDER_ADVANCE_SPEED.get();
        // 2.10.1 aggression pass: raiders inside this range of the objective
        // get a 30% speed burst so they push the last stretch instead of
        // dawdling once the pathfinder detects walls or defenders.
        double burstRangeSq = 32.0 * 32.0;
        double burstMultiplier = 1.30;

        // 2.14.0: reasoning aids for aggression rules.
        double aggroRange = RaidConfig.AGGRO_RADIUS.get();
        double aggroRangeSq = aggroRange * aggroRange;
        double driftLimit = RaidConfig.OFF_AXIS_DRIFT_LIMIT.get();
        double driftLimitSq = driftLimit * driftLimit;
        boolean breachersIgnore = RaidConfig.BREACHERS_IGNORE_DEFENDERS.get();
        // Camp position is our best estimate of the "back of the army"
        // — anything closer to the camp than to the objective is behind us.
        Vec3 campVec = state.campPos == null ? null : Vec3.atCenterOf(state.campPos);

        // v2.23.0 Press-the-Attack config snapshot (avoid re-reading per raider):
        boolean stuckEnabled = RaidConfig.STUCK_DETECTION_ENABLED.get();
        int stuckSeconds = RaidConfig.STUCK_ESCALATION_SECONDS.get();
        long stuckTicksL1 = stuckSeconds * 20L;
        long stuckTicksL2 = stuckTicksL1 * 2L;
        double innerMultiplier = RaidConfig.INNER_AGGRO_MULTIPLIER.get();
        double innerRangeSq = (aggroRange * 1.5) * (aggroRange * 1.5); // "at the objective" band
        double widenedAggroRangeSq = (aggroRange * innerMultiplier) * (aggroRange * innerMultiplier);
        boolean forceRepath = RaidConfig.FORCE_REPATH_WHEN_IDLE.get();
        long gameTime = level.getGameTime();

        for (UUID id : state.raiders) {
            Entity entity = level.getEntity(id);
            if (!(entity instanceof Mob mob) || !mob.isAlive()) {
                if (stuckEnabled) STUCK_TRACKER.remove(id);
                continue;
            }

            double distToObjectiveSq = mob.distanceToSqr(objective);

            // Role-gated aggression: breachers and the commander skip the
            // defender search entirely and only path to the objective.
            String role = mob.getPersistentData().getString(RAID_ROLE_TAG);
            boolean lockedOnObjective = breachersIgnore &&
                    (role.equals("breacher") || role.equals("commander"));

            // v2.23.0: widened aggro cone when this raider is already at the
            // objective. Fixes the "reached the base, no defender in the
            // strict front cone, just standing there" case. The widened
            // range is used for lookup only; the strict cone still governs
            // outer-approach raiders. Also promoted when the stuck tracker
            // reaches escalation level 2 for any raider.
            StuckEntry stuck = stuckEnabled ? STUCK_TRACKER.get(id) : null;
            boolean atObjective = distToObjectiveSq < innerRangeSq;
            boolean wideAggro = atObjective || (stuck != null && stuck.escalationLevel >= 2);
            double effectiveAggroRangeSq = wideAggro ? widenedAggroRangeSq : aggroRangeSq;

            LivingEntity closest = null;
            double closestDistance = Double.MAX_VALUE;
            if (!lockedOnObjective) {
                for (ServerPlayer player : members) {
                    if (!player.isAlive() || player.level() != level || player.isSpectator()) continue;
                    // Widened aggro also relaxes the behind-us filter: when a
                    // raider is at the objective, chasing a defender "behind"
                    // the objective ring is the correct behaviour.
                    if (!eligibleTarget(mob, player, objective,
                            wideAggro ? null : campVec, effectiveAggroRangeSq)) continue;
                    double distance = mob.distanceToSqr(player);
                    if (distance < closestDistance) {
                        closest = player;
                        closestDistance = distance;
                    }
                }
                for (Mob recruit : recruits) {
                    if (!recruit.isAlive()) continue;
                    if (!eligibleTarget(mob, recruit, objective,
                            wideAggro ? null : campVec, effectiveAggroRangeSq)) continue;
                    double distance = mob.distanceToSqr(recruit);
                    if (distance < closestDistance) {
                        closest = recruit;
                        closestDistance = distance;
                    }
                }
            }
            boolean acquired = closest != null;

            // Off-axis drift check: if this raider is dragging a chase
            // sideways deep off the invasion axis, drop the target and snap
            // back to the objective. Skipped in wide-aggro mode because the
            // point of wide-aggro is exactly to allow that lateral engagement.
            if (campVec != null && !wideAggro) {
                double perpDistSq = perpendicularDistanceSq(mob.position(), campVec, objective);
                if (perpDistSq > driftLimitSq) {
                    mob.setTarget(null);
                    acquired = false;
                }
            }

            if (acquired) mob.setTarget(closest);
            else if (lockedOnObjective) mob.setTarget(null);

            // 2.10.1 aggression pass: ALWAYS keep the objective nav goal alive.
            double speed = distToObjectiveSq < burstRangeSq ? baseSpeed * burstMultiplier : baseSpeed;
            // v2.23.0 stuck-escalation speed bump on top of the burst
            // multiplier so escalated raiders visibly push harder.
            if (stuck != null && stuck.escalationLevel >= 1) speed *= 1.15;

            // v2.23.0: force re-path every redirect tick when the raider has
            // no target. The old "only if isDone()" gate parked raiders whose
            // path had failed against a wall since the nav reports done and
            // never retries. Force-repath is cheap (once per second per
            // raider) and lets the pathfinder try a fresh route each tick.
            //
            // v2.24.0: when this raider has been marked stuck (escalation
            // level >= 1) AND the cone fallback is enabled, do not retry the
            // direct route to the objective (we already know it fails).
            // Instead ask DefaultRandomPos.getPosTowards for a random
            // reachable point in a narrow cone toward the objective, then
            // widen to a full hemisphere if that fails. This mirrors
            // Mojang's RaiderMoveThroughVillageGoal fallback: narrow cone
            // (pi/10) at full radius, then wide cone (pi/2) at half radius.
            // If both fail we give up this tick rather than spam a route we
            // know is unreachable. Healthy raiders (no stuck entry) still
            // path straight at the objective because that is faster when it
            // works.
            if (!acquired && forceRepath) {
                Vec3 target = null;
                if (RaidConfig.CONE_FALLBACK_ENABLED.get()
                        && stuck != null && stuck.escalationLevel >= 1
                        && mob instanceof PathfinderMob pmob) {
                    target = coneFallbackTarget(pmob, objective);
                }
                if (target != null) {
                    mob.getNavigation().moveTo(target.x, target.y, target.z, speed);
                } else {
                    mob.getNavigation().moveTo(objective.x, objective.y, objective.z, speed);
                }
            } else if (!acquired || mob.getNavigation().isDone()) {
                mob.getNavigation().moveTo(objective.x, objective.y, objective.z, speed);
            }

            // v2.23.0 stuck detection. We only care about raiders that are
            // not currently melee-engaged (acquired == false) AND are more
            // than a few blocks from the objective (already-there raiders
            // aren't stuck, they've won). Progress is measured as a
            // meaningful reduction in distanceToObjectiveSq over the window.
            if (stuckEnabled && !acquired && distToObjectiveSq > 25.0) {
                updateStuckTracker(mob, id, distToObjectiveSq, gameTime,
                        stuckTicksL1, stuckTicksL2);
            } else if (stuckEnabled) {
                // Either engaged in melee, or we've reached the objective:
                // clear stuck state so the next stall starts a fresh clock.
                STUCK_TRACKER.remove(id);
            }

            if (glow) mob.addEffect(new MobEffectInstance(MobEffects.GLOWING, 40, 0, false, false));
        }
    }

    /**
     * v2.23.0 Press-the-Attack: update the stuck tracker for one raider and
     * apply escalations when the stall crosses each threshold.
     *
     * <p>Progress rule: if the raider has moved at least ~1 block closer to
     * the objective since the last observation, we reset the timer. Anything
     * less counts as stalled.
     *
     * <p>Escalations:
     * <ul>
     *   <li><b>Level 1</b> (stuckSeconds elapsed): force a jump input and
     *       nudge the raider a fresh moveTo with a small speed bump. Handles
     *       the case where the raider is one block below the target ledge.</li>
     *   <li><b>Level 2</b> (2x stuckSeconds): the widened-aggro flag in the
     *       main loop kicks in next tick because we set escalationLevel to 2
     *       here; the raider will start looking for defenders in a wider,
     *       hemisphere-agnostic radius. This is the terminal escalation:
     *       we deliberately do not force the raider onto the physical
     *       breach queue, because that subsystem selects its own targets
     *       through role-gated pressure voting and injecting arbitrary
     *       raiders would corrupt its accounting.</li>
     * </ul>
     */
    /**
     * v2.24.0 vanilla-style cone-widening fallback for stuck raiders.
     *
     * <p>Mirrors {@code Raider.RaiderMoveThroughVillageGoal.tick()} in
     * vanilla Minecraft 1.20.1: when the direct path to the objective
     * fails, ask {@code DefaultRandomPos.getPosTowards} for a random
     * <em>reachable</em> point in a narrow cone (pi/10, ~18 degrees) toward
     * the objective at full radius. If that returns null, widen the cone to
     * a full hemisphere (pi/2, 90 degrees) at half the radius. The point of
     * this cascade is to give the pathfinder a target it can actually route
     * to, which unstalls raiders parked against an unreachable objective.
     *
     * <p>Returns {@code null} if no reachable target could be found at
     * either search width. The caller falls back to the direct objective in
     * that case rather than skipping the tick, so a raider whose nav layer
     * happens to fail one search still gets pinged toward the objective.
     *
     * <p>Called only for raiders whose {@link StuckEntry#escalationLevel}
     * is >= 1, so healthy raiders keep their fast direct path.
     */
    private static Vec3 coneFallbackTarget(PathfinderMob mob, Vec3 objective) {
        int radius = RaidConfig.CONE_FALLBACK_RADIUS.get();
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

    private static void updateStuckTracker(Mob mob, UUID id, double distToObjectiveSq,
                                           long gameTime, long ticksL1, long ticksL2) {
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
            return;
        }
        long stalledFor = gameTime - entry.lastProgressGameTime;

        if (entry.escalationLevel < 1 && stalledFor >= ticksL1) {
            // Level 1: jump + fresh path.
            mob.getJumpControl().jump();
            entry.escalationLevel = 1;
        }
        if (entry.escalationLevel < 2 && stalledFor >= ticksL2) {
            // Level 2: main loop consults escalationLevel to widen aggro
            // on the next tick. No direct action needed here.
            entry.escalationLevel = 2;
        }
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
        if (RaidConfig.ENABLE_BREACH_PHASE.get() && !state.breached) {
            Vec3 breachObjective = invasionObjective(level, point, state);
            double objectiveRadius = RaidConfig.BREACH_OBJECTIVE_RADIUS.get();
            double breachRadiusSq = objectiveRadius * objectiveRadius;
            int attackers = attackersInside(level, state, breachObjective, breachRadiusSq);
            int defenders = defendersInside(level, members, recruits, breachObjective, breachRadiusSq);
            int maximum = RaidConfig.BREACH_TIME_SECONDS.get() * 20;
            if (attackers > defenders && attackers > 0) {
                state.breachTicks = Math.min(maximum, state.breachTicks + 20);
            } else state.breachTicks = Math.max(0,
                    state.breachTicks - RaidConfig.BREACH_DECAY_PER_SECOND.get() * 20);
            // Effort bonus — stack on top of presence baseline. Drains a
            // fixed slice per tick so kills/breaches feel additive but capped.
            if (RaidConfig.ENABLE_EFFORT_BONUS.get()) {
                int bonus = com.devfarinsky.factionraids.effort.RaidEffortTracker
                        .consume(state.teamKey, 20);
                if (bonus > 0) state.breachTicks = Math.min(maximum, state.breachTicks + bonus);
            }

            int band = maximum <= 0 ? 0 : state.breachTicks * 4 / maximum;
            if (band > state.lastBreachWarningBand && band < 4) {
                state.lastBreachWarningBand = band;
                int percent = band * 25;
                announce(server, anchor.teamKey(), Component.literal("Perimeter breach pressure: " + percent +
                        "%. Hold the outer defensive line!").withStyle(ChatFormatting.GOLD), band >= 3);
                sendActionBar(server, anchor.teamKey(), Component.literal("PERIMETER BREACH: " + percent + "%")
                        .withStyle(band >= 3 ? ChatFormatting.RED : ChatFormatting.GOLD, ChatFormatting.BOLD));
            }
            if (state.breachTicks >= maximum) {
                state.breached = true;
                state.lastCaptureWarningBand = 0;
                announce(server, anchor.teamKey(), Component.literal("The perimeter has been breached! Invaders are pushing for the stronghold heart.")
                        .withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD), true);
                showTitle(server, anchor.teamKey(), Component.literal("PERIMETER BREACHED")
                                .withStyle(ChatFormatting.DARK_RED),
                        Component.literal("Fall back and defend the stronghold heart")
                                .withStyle(ChatFormatting.GOLD));
            }
            return false;
        }

        state.breached = true;
        double radiusSq = (double) RaidConfig.CAPTURE_RADIUS.get() * RaidConfig.CAPTURE_RADIUS.get();
        int attackers = attackersInside(level, state, center, radiusSq);
        int defenders = defendersInside(level, members, recruits, center, radiusSq);

        int maximum = RaidConfig.CAPTURE_TIME_SECONDS.get() * 20;
        if (attackers > defenders && attackers > 0) state.captureTicks = Math.min(maximum, state.captureTicks + 20);
        else state.captureTicks = Math.max(0,
                state.captureTicks - RaidConfig.CAPTURE_DECAY_PER_SECOND.get() * 20);
        // Effort bonus — same accrual applied to capture progress.
        if (RaidConfig.ENABLE_EFFORT_BONUS.get()) {
            int captureBonus = com.devfarinsky.factionraids.effort.RaidEffortTracker
                    .consume(state.teamKey, 20);
            if (captureBonus > 0) state.captureTicks = Math.min(maximum, state.captureTicks + captureBonus);
        }

        int band = maximum <= 0 ? 0 : state.captureTicks * 4 / maximum;
        if (band > state.lastCaptureWarningBand && band < 4) {
            state.lastCaptureWarningBand = band;
            int percent = band * 25;
            announce(server, anchor.teamKey(), Component.literal("Invaders hold " + percent +
                    "% of the stronghold — push them out.").withStyle(ChatFormatting.DARK_RED),
                    band >= 3);
            sendActionBar(server, anchor.teamKey(), Component.literal("STRONGHOLD OCCUPATION: " + percent + "%")
                    .withStyle(band >= 3 ? ChatFormatting.RED : ChatFormatting.GOLD, ChatFormatting.BOLD));
        }
        return state.captureTicks >= maximum;
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
        com.devfarinsky.factionraids.naval.NavalConvoy.forget(teamKey);
        com.devfarinsky.factionraids.naval.BridgeBuilder.forget(teamKey);
        ACTIVE_COMPOSITIONS.remove(teamKey);
        com.devfarinsky.factionraids.formations.FormationDirector.forget(teamKey);
        com.devfarinsky.factionraids.effort.RaidEffortTracker.forget(teamKey);
        com.devfarinsky.factionraids.effort.StragglerTracker.forget(teamKey);
        com.devfarinsky.factionraids.siege.LadderBuilder.forget(teamKey);
        // v2.23.0: drop per-raider stuck-tracker entries for this raid.
        // The main loop removes single dead entries opportunistically, but
        // a raid ending (admin-stopped, victory, defeat) drops them all at
        // once so the map does not grow across many raids.
        RaidSavedData.RaidState finishingState = data.raids.get(teamKey);
        if (finishingState != null) {
            for (UUID rid : finishingState.raiders) STUCK_TRACKER.remove(rid);
        }
        // v2.19.0 RE1: drop the ACTIVE_CAMPS entry unconditionally, before
        // the level-guarded branch below. Prior code only removed the entry
        // when the raid's dimension was loaded, so an admin stop or dim
        // removal that finished a raid with level == null leaked a
        // CampBuilder.CampState per raid, keyed by team, until server restart.
        com.devfarinsky.factionraids.camp.CampBuilder.CampState leakedCamp =
                ACTIVE_CAMPS.remove(teamKey);
        RaidSavedData.RaidState state = data.raids.remove(teamKey);
        RaidSavedData.Anchor anchor = data.anchors.get(teamKey);
        if (state != null && anchor != null) {
            RaidSavedData.DefensePoint point = anchor.point(state.defensePointName);
            ServerLevel level = getLevel(server, point);
            if (level != null) {
                com.devfarinsky.factionraids.siege.SiegeDeployment.cleanup(level, state);
                for (UUID id : state.raiders) {
                    Entity entity = level.getEntity(id);
                    if (entity != null) entity.discard();
                }
                restoreBreachedBlocks(level, state);
                cleanupWarCamp(level, state);
                if (leakedCamp != null) leakedCamp.cleanup(level);
            }
            long next = server.overworld().getGameTime() + randomCooldownTicks(server.overworld().random);
            data.anchors.put(teamKey, anchor.withNextRaid(next));
        }
        boolean eligibleVictory = victory && reward && state != null && state.rewardEligible;
        // v2.28.0: track the "no breach" bonus outside the block so the
        // announcement branch below can mention it and the War Journal path
        // can log the payout accurately.
        int noBreachBonus = 0;
        if (eligibleVictory) {
            int experience = RaidConfig.VICTORY_EXPERIENCE.get();
            List<ServerPlayer> winners = onlineMembers(server, teamKey);
            if (experience > 0) winners.forEach(p -> p.giveExperiencePoints(experience));
            int emeralds = guaranteedEmeraldReward(state);
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
        ServerBossEvent bar = RaidBossBars.remove(teamKey);
        if (bar != null) bar.removeAllPlayers();
        long elapsedTicks = state == null || state.startedGameTime <= 0 ? 0 :
                Math.max(0, server.overworld().getGameTime() - state.startedGameTime);
        String summary = state == null ? "" : " Defeated: " + state.totalDefeated +
                " of " + state.totalSpawned + " deployed" +
                (state.totalEscaped > 0 ? "; " + state.totalEscaped + " lost contact" : "") +
                (elapsedTicks > 0 ? "; duration " + formatTime(elapsedTicks / 20) : "") + ".";
        announce(server, teamKey, Component.literal(message + summary)
                .withStyle(victory ? ChatFormatting.GREEN : ChatFormatting.DARK_RED), victory);
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
            int emeralds = guaranteedEmeraldReward(state);
            announce(server, teamKey, Component.literal("Victory spoils: " + emeralds +
                    " guaranteed emeralds, " + RaidConfig.VICTORY_EXPERIENCE.get() +
                    " experience and bonus campaign loot for each online faction member.")
                    .withStyle(ChatFormatting.GREEN), false);
            if (noBreachBonus > 0) {
                announce(server, teamKey, Component.literal(
                        "Perimeter held. No-breach bonus: +" + noBreachBonus +
                                " emeralds per member.")
                        .withStyle(ChatFormatting.GOLD), false);
            }
        }
        showTitle(server, teamKey,
                Component.literal(victory ? "SIEGE BROKEN" : "STRONGHOLD FALLEN")
                        .withStyle(victory ? ChatFormatting.GREEN : ChatFormatting.DARK_RED),
                Component.literal(victory ? "Your faction held the line" : "The invaders seized the objective")
                        .withStyle(ChatFormatting.GOLD));
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
        if (paused) return "Paused";
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
        return " • " + meters + "m";
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
        int totalWaves = RaidConfig.WAVES.get();
        float completedWaves = Math.max(0, state.wave - 1);
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
        // whichever raider happens to be leading.
        String distanceHint = frontLineDistanceHint(server, anchor, state);

        String label = paused ? phase + " — faction offline" : state.wave == 0 ?
                phase + " to the " + approachDirection(state.approachAngle) +
                        " (target: " + objectiveName + distanceHint + ")" :
                !state.breached && RaidConfig.ENABLE_BREACH_PHASE.get() ?
                        phase + ": " + objectiveName + distanceHint +
                                " • " + state.raiders.size() + " deployed • breach " +
                                breachPercent + "%" :
                        phase + ": " + objectiveName + " " + capturePercent + "% held" +
                                " • wave " + Math.max(1, state.wave) + "/" + totalWaves +
                                " • " + state.raiders.size() + " deployed" +
                                (state.pendingWaveSpawns > 0 ? " + " + state.pendingWaveSpawns + " reinforcing" : "");
        // Prepend the raider epithet if narrative is enabled and available, so
        // the boss bar reads e.g. "Ship-Wolves — Perimeter assault…" instead of
        // the generic "Faction Invasion" everyone gets today.
        if (RaidConfig.NARRATIVE_IN_BOSS_BAR.get() && state.narrative != null &&
                state.narrative.factionEpithet != null) {
            label = state.narrative.factionEpithet + " — " + label;
        }
        bar.setName(Component.literal(label));
        bar.setColor(paused ? BossEvent.BossBarColor.WHITE : !state.breached ?
                (breachPercent >= 75 ? BossEvent.BossBarColor.RED : BossEvent.BossBarColor.YELLOW) : capturePercent >= 75 ?
                BossEvent.BossBarColor.PURPLE : state.wave == 0 ? BossEvent.BossBarColor.YELLOW :
                BossEvent.BossBarColor.RED);
    }

    private static void announce(MinecraftServer server, String teamKey, Component message, boolean horn) {
        Component branded = MESSAGE_PREFIX.copy().append(message);
        if (RaidConfig.ANNOUNCE_GLOBALLY.get()) {
            server.getPlayerList().broadcastSystemMessage(branded, false);
        } else onlineMembers(server, teamKey).forEach(p -> p.sendSystemMessage(branded));
        if (horn) onlineMembers(server, teamKey).forEach(p ->
                p.playNotifySound(SoundEvents.RAID_HORN.value(), SoundSource.HOSTILE, 1.0F, 1.0F));
    }

    private static void showTitle(MinecraftServer server, String teamKey, Component title, Component subtitle) {
        if (!RaidConfig.SHOW_RAID_TITLES.get()) return;
        for (ServerPlayer player : onlineMembers(server, teamKey)) {
            player.connection.send(new ClientboundSetTitlesAnimationPacket(10, 50, 15));
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
        boolean recruitsReady = com.devfarinsky.factionraids.compat.RecruitsClaimsBridge.available();
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
            java.util.Optional<com.devfarinsky.factionraids.compat.RecruitsClaimsBridge.ClaimSnapshot> snap =
                    com.devfarinsky.factionraids.compat.RecruitsClaimsBridge.resolveDefendingClaim(level, anchor);
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
                    0, "No gate under attack", 0, "Sleep at your base",
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
            com.devfarinsky.factionraids.waves.WaveComposition preview =
                    com.devfarinsky.factionraids.waves.WaveComposer.compose(1, RaidConfig.WAVES.get(), firstWaveTotal);
            int score = computeDefenseScore(recruits, compat, firstWaveTotal, 1);
            String explainer = buildDefenseExplainer(recruits, compat, firstWaveTotal, 1);
            CodexCompatInfo compatInfoIdle = buildCodexCompatInfo(level, anchor, point);
            return new DashboardSnapshot(anchor.teamDisplay(), true, false,
                    point.dimension() + " • " + formatPos(point.pos()), 0, RaidConfig.WAVES.get(),
                    0, 0, 0, 0, false, 0, recruits, compat.workers(), compat.ships(), compat.siegeWeapons(),
                    assetScalingEnemies(compat), 0, "No gate under attack", 0,
                    formatTime(seconds), defaultEmeraldReward(), true,
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
        int nextWaveNumber = Math.min(state.wave + 1, RaidConfig.WAVES.get());
        com.devfarinsky.factionraids.waves.WaveComposition nextPreview =
                com.devfarinsky.factionraids.waves.WaveComposer.compose(nextWaveNumber, RaidConfig.WAVES.get(),
                        Math.max(1, RaidConfig.BASE_ENEMIES_PER_WAVE.get()));
        String nextLabel = nextWaveNumber <= state.wave ? "Final wave in progress" :
                "Wave " + nextWaveNumber + (nextPreview.label.isEmpty() ? "" : " — " + nextPreview.label);
        String nextRoles = nextWaveNumber <= state.wave ? "" : formatRoleCounts(nextPreview);
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
                state.wave, RaidConfig.WAVES.get(),
                state.raiders.size(), state.pendingWaveSpawns, state.totalDefeated, occupation,
                state.breached || !RaidConfig.ENABLE_BREACH_PHASE.get(), breachPercent(state),
                recruits, compat.workers(), compat.ships(), compat.siegeWeapons(), assetScalingEnemies(compat),
                state.breachedBlocks.size(),
                state.currentBreachBlock == null ? "No gate under attack" : formatPos(state.currentBreachBlock),
                gateBreachPercent(state), "Siege active",
                guaranteedEmeraldReward(state), state.rewardEligible,
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

    private static String formatRoleCounts(com.devfarinsky.factionraids.waves.WaveComposition comp) {
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
        int payout = eligibleVictory ? guaranteedEmeraldReward(state) : 0;
        // Outcome is a short machine-readable tag so the client can style it
        // (green vs. red vs. yellow) without brittle string matching.
        String outcome = victory ? (eligibleVictory ? "victory" : "victory_practice") : "defeat";
        journal.record(new RaidSavedData.WarJournal.Entry(
                server.overworld().getGameTime(), factionId, factionName, casusBelli,
                state.wave, RaidConfig.WAVES.get(), outcome, payout));
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
        String key = teamKey(observedPlayer);
        RaidSavedData.Anchor anchor = data.anchors.get(key);

        if (anchor == null) {
            String oldKey = associatedAnchorKeyForPlayer(data, observedPlayer.getUUID());
            if (oldKey != null && !oldKey.equals(key) && !data.raids.containsKey(oldKey)) {
                RaidSavedData.Anchor old = data.anchors.remove(oldKey);
                if (old != null) {
                    anchor = old.withIdentity(key, teamDisplay(observedPlayer));
                    data.anchors.put(key, anchor);
                }
            }
        }

        UUID leaderId = RecruitsBridge.factionLeader(observedPlayer)
                .orElse(anchor != null && !RaidSavedData.UNKNOWN_OWNER.equals(anchor.ownerUuid()) ?
                        anchor.ownerUuid() : observedPlayer.getUUID());
        ServerPlayer homePlayer = server.getPlayerList().getPlayer(leaderId);

        if (anchor == null) {
            // If a faction leader is offline during first discovery, the first
            // online member establishes a usable temporary home. It is corrected
            // automatically the next time the leader joins.
            if (homePlayer == null) homePlayer = observedPlayer;
            RaidSavedData.DefensePoint home = respawnPoint(server, homePlayer);
            if (home == null) return;
            Set<UUID> members = seedRoster(server, observedPlayer);
            members.add(leaderId);
            Map<String, RaidSavedData.DefensePoint> points = new LinkedHashMap<>();
            points.put(RaidSavedData.HOME_POINT, home);
            long next = server.overworld().getGameTime() + randomCooldownTicks(server.overworld().random);
            RaidSavedData.Anchor created = new RaidSavedData.Anchor(key, teamDisplay(observedPlayer), leaderId,
                    members, false, true, points, next);
            data.anchors.put(key, created);
            data.setDirty();
            return;
        }

        Set<UUID> members = new LinkedHashSet<>(anchor.members());
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (teamKey(player).equals(key)) members.add(player.getUUID());
        }
        members.add(leaderId);
        RaidSavedData.Anchor updated = anchor.withOwner(leaderId).withRoster(members, false)
                .withIdentity(key, teamDisplay(observedPlayer));

        if ((force || updated.automaticHome() && RaidConfig.FOLLOW_RESPAWN_POINT.get()) &&
                homePlayer != null && !data.raids.containsKey(key)) {
            RaidSavedData.DefensePoint home = respawnPoint(server, homePlayer);
            if (home != null) updated = updated.withPoint(home).withAutomaticHome(true);
        }
        if (!updated.equals(anchor)) {
            data.anchors.put(key, updated);
            data.setDirty();
        }
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
        return data.raids.values().stream().mapToInt(r -> r.raiders.size()).sum();
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
        // v2.27.0: claim-center pass. If Recruits is loaded and the anchor
        // sits inside a friendly claim, prefer the claim's center as the
        // defense point. Keeps raids attacking what the player actually
        // built and claimed instead of the anchor block itself. Only used
        // when the defender-near check passes (or is disabled).
        if (RaidConfig.CLAIM_AWARE_ANCHORS.get()
                && RaidConfig.USE_CLAIM_CENTER_AS_DEFENSE_POINT.get()
                && com.devfarinsky.factionraids.compat.RecruitsClaimsBridge.available()) {
            RaidSavedData.DefensePoint claimPoint = synthesizeClaimDefensePoint(server, anchor);
            if (claimPoint != null
                    && (!RaidConfig.REQUIRE_PLAYER_NEAR_ANCHOR.get() || hasDefenderNear(server, claimPoint, members))) {
                return claimPoint;
            }
        }
        if (anchor.automaticHome()) {
            List<RaidSavedData.DefensePoint> playerHomes = new ArrayList<>();
            for (ServerPlayer member : members) {
                RaidSavedData.DefensePoint home = respawnPoint(server, member);
                if (home == null) continue;
                if (!RaidConfig.REQUIRE_PLAYER_NEAR_ANCHOR.get() || hasDefenderNear(server, home, members)) {
                    playerHomes.add(home);
                }
            }
            if (!playerHomes.isEmpty()) {
                return playerHomes.get(server.overworld().random.nextInt(playerHomes.size()));
            }
        }
        List<RaidSavedData.DefensePoint> eligible = new ArrayList<>();
        for (RaidSavedData.DefensePoint point : anchor.defensePoints().values()) {
            if (!RaidConfig.REQUIRE_PLAYER_NEAR_ANCHOR.get() || hasDefenderNear(server, point, members)) {
                eligible.add(point);
            }
        }
        if (eligible.isEmpty()) return null;
        return eligible.get(server.overworld().random.nextInt(eligible.size()));
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
        java.util.Optional<com.devfarinsky.factionraids.compat.RecruitsClaimsBridge.ClaimSnapshot> snap =
                com.devfarinsky.factionraids.compat.RecruitsClaimsBridge.resolveDefendingClaim(overworld, anchor);
        if (snap.isEmpty()) return null;
        com.devfarinsky.factionraids.compat.RecruitsClaimsBridge.ClaimSnapshot claim = snap.get();
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

    private RaidEvents() {}
}
