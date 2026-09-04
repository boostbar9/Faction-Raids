package com.devfarinsky.factionraids;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.monster.Vex;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.raid.Raider;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Team;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.*;

public final class RaidEvents {
    private static final String RAID_TEAM_TAG = "FactionRaidsTeam";
    private static final Map<String, ServerBossEvent> BOSS_BARS = new HashMap<>();
    private static int tickCounter;

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        registerCommands(event.getDispatcher());
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
        if (!(event.getLevel() instanceof ServerLevel level) || !(event.getEntity() instanceof Vex vex)) return;
        Mob owner = vex.getOwner();
        if (owner == null || !owner.getPersistentData().contains(RAID_TEAM_TAG)) return;
        String teamKey = owner.getPersistentData().getString(RAID_TEAM_TAG);
        if (teamKey.isBlank()) return;
        vex.getPersistentData().putString(RAID_TEAM_TAG, teamKey);
        vex.setPersistenceRequired();
        RaidSavedData data = RaidSavedData.get(level.getServer());
        RaidSavedData.RaidState state = data.raids.get(teamKey);
        if (state != null) {
            state.raiders.add(vex.getUUID());
            data.setDirty();
        }
    }

    @SubscribeEvent
    public static void onLivingAttack(LivingAttackEvent event) {
        if (!RaidConfig.PROTECT_VILLAGERS.get()) return;
        Entity attacker = event.getSource().getEntity();
        if (!(attacker instanceof Mob mob) || !mob.getPersistentData().contains(RAID_TEAM_TAG)) return;
        if (event.getEntity() instanceof AbstractVillager || event.getEntity() instanceof IronGolem) {
            event.setCanceled(true);
            mob.setTarget(null);
        }
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        BOSS_BARS.values().forEach(ServerBossEvent::removeAllPlayers);
        BOSS_BARS.clear();
    }

    private static void registerCommands(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("factionraids")
                .then(Commands.literal("anchor")
                        .then(Commands.literal("set").executes(ctx -> setAnchor(ctx.getSource())))
                        .then(Commands.literal("claim").executes(ctx -> claimLegacyAnchor(ctx.getSource())))
                        .then(Commands.literal("remove").executes(ctx -> removeAnchor(ctx.getSource()))))
                .then(Commands.literal("start").executes(ctx -> startOwnRaid(ctx.getSource())))
                .then(Commands.literal("status").executes(ctx -> status(ctx.getSource())))
                .then(Commands.literal("stop").requires(s -> s.hasPermission(2))
                        .executes(ctx -> stopOwnRaid(ctx.getSource())))
                .then(Commands.literal("admin").requires(s -> s.hasPermission(2))
                        .then(Commands.literal("list").executes(ctx -> adminList(ctx.getSource())))
                        .then(Commands.literal("stop")
                                .then(Commands.argument("team", StringArgumentType.word())
                                        .executes(ctx -> adminStop(ctx.getSource(), StringArgumentType.getString(ctx, "team")))))
                        .then(Commands.literal("remove")
                                .then(Commands.argument("team", StringArgumentType.word())
                                        .executes(ctx -> adminRemove(ctx.getSource(), StringArgumentType.getString(ctx, "team")))))));
    }

    private static int setAnchor(CommandSourceStack source) {
        try {
            ServerPlayer player = source.getPlayerOrException();
            String key = teamKey(player);
            String display = teamDisplay(player);
            RaidSavedData data = RaidSavedData.get(source.getServer());
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
            data.anchors.put(key, new RaidSavedData.Anchor(key, display, player.getUUID(),
                    player.level().dimension().location(), player.blockPosition(), next));
            data.setDirty();
            source.sendSuccess(() -> Component.literal("Faction raid anchor set at " + formatPos(player.blockPosition()) +
                    ". Illager invasions will target your faction players here.").withStyle(ChatFormatting.GREEN), false);
            return 1;
        } catch (Exception e) {
            source.sendFailure(Component.literal("Only a player can set a faction raid anchor."));
            return 0;
        }
    }

    private static int claimLegacyAnchor(CommandSourceStack source) {
        try {
            ServerPlayer player = source.getPlayerOrException();
            String key = teamKey(player);
            RaidSavedData data = RaidSavedData.get(source.getServer());
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
            String key = teamKey(player);
            RaidSavedData data = RaidSavedData.get(source.getServer());
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

    private static int startOwnRaid(CommandSourceStack source) {
        try {
            ServerPlayer player = source.getPlayerOrException();
            String key = teamKey(player);
            RaidSavedData data = RaidSavedData.get(source.getServer());
            RaidSavedData.Anchor anchor = data.anchors.get(key);
            if (anchor == null) {
                source.sendFailure(Component.literal("Set your faction's anchor first with /factionraids anchor set"));
                return 0;
            }
            if (!canManage(player, anchor)) {
                source.sendFailure(Component.literal("Only the anchor owner or an operator can manually start an invasion."));
                return 0;
            }
            if (data.raids.containsKey(key)) {
                source.sendFailure(Component.literal("Your faction already has an active invasion."));
                return 0;
            }
            if (!hasDefenderNear(source.getServer(), anchor, onlineMembers(source.getServer(), key))) {
                source.sendFailure(Component.literal("Stand near your faction anchor before starting the invasion."));
                return 0;
            }
            if (!beginRaid(source.getServer(), data, anchor)) {
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
            String key = teamKey(player);
            RaidSavedData data = RaidSavedData.get(source.getServer());
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
            String key = teamKey(player);
            RaidSavedData data = RaidSavedData.get(source.getServer());
            RaidSavedData.Anchor anchor = data.anchors.get(key);
            if (anchor == null) {
                source.sendSuccess(() -> Component.literal("No faction raid anchor. Use /factionraids anchor set"), false);
                return 1;
            }
            RaidSavedData.RaidState state = data.raids.get(key);
            if (state != null) {
                source.sendSuccess(() -> Component.literal("Invasion active: wave " + state.wave + "/" +
                        RaidConfig.WAVES.get() + ", " + state.raiders.size() + " enemies tracked. Team key: " + key), false);
            } else {
                long now = source.getServer().overworld().getGameTime();
                long seconds = Math.max(0L, (anchor.nextRaidGameTime() - now) / 20L);
                source.sendSuccess(() -> Component.literal("Anchor " + formatPos(anchor.pos()) +
                        "; next automatic invasion eligible in " + formatTime(seconds) + ". Team key: " + key), false);
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
                source.sendSuccess(() -> Component.literal(anchor.teamKey() + " — " + anchor.teamDisplay() + " at " +
                        formatPos(anchor.pos()) + (data.raids.containsKey(anchor.teamKey()) ? " [ACTIVE]" : "")), false));
        return data.anchors.size();
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

    private static void tick(MinecraftServer server) {
        RaidSavedData data = RaidSavedData.get(server);
        refreshIdleAnchorIdentities(server, data);
        long now = server.overworld().getGameTime();

        if (RaidConfig.AUTOMATIC_RAIDS.get() && data.raids.size() < RaidConfig.MAX_CONCURRENT_RAIDS.get()) {
            for (RaidSavedData.Anchor anchor : new ArrayList<>(data.anchors.values())) {
                if (data.raids.size() >= RaidConfig.MAX_CONCURRENT_RAIDS.get()) break;
                if (data.raids.containsKey(anchor.teamKey()) || now < anchor.nextRaidGameTime()) continue;
                List<ServerPlayer> members = onlineMembers(server, anchor.teamKey());
                if (members.isEmpty()) continue;
                if (RaidConfig.REQUIRE_PLAYER_NEAR_ANCHOR.get() && !hasDefenderNear(server, anchor, members)) continue;
                beginRaid(server, data, anchor);
            }
        }

        for (String teamKey : new ArrayList<>(data.raids.keySet())) processRaid(server, data, teamKey);
        data.setDirty();
    }

    private static boolean beginRaid(MinecraftServer server, RaidSavedData data, RaidSavedData.Anchor anchor) {
        if (data.raids.size() >= RaidConfig.MAX_CONCURRENT_RAIDS.get()) return false;
        RaidSavedData.RaidState state = new RaidSavedData.RaidState(anchor.teamKey(), RaidConfig.WARNING_SECONDS.get() * 20);
        data.raids.put(anchor.teamKey(), state);
        data.setDirty();
        announce(server, anchor.teamKey(), Component.literal("[Faction Raid] ").withStyle(ChatFormatting.DARK_RED)
                .append(Component.literal("Illager scouts have found " + anchor.teamDisplay() + "! The invasion begins in " +
                        formatTime(RaidConfig.WARNING_SECONDS.get()) + ". Villagers are not the objective—defend the territory yourselves.")
                        .withStyle(ChatFormatting.GOLD)), true);
        updateBossBar(server, anchor, state, false);
        return true;
    }

    private static void processRaid(MinecraftServer server, RaidSavedData data, String teamKey) {
        RaidSavedData.Anchor anchor = data.anchors.get(teamKey);
        RaidSavedData.RaidState state = data.raids.get(teamKey);
        if (anchor == null || state == null) return;
        ServerLevel level = getLevel(server, anchor);
        if (level == null) return;

        List<ServerPlayer> members = onlineMembers(server, teamKey);
        if (members.isEmpty() && RaidConfig.PAUSE_WHEN_FACTION_OFFLINE.get()) {
            state.offlinePauseAnnounced = true;
            updateBossBar(server, anchor, state, true);
            return;
        }
        if (state.offlinePauseAnnounced) {
            state.offlinePauseAnnounced = false;
            announce(server, teamKey, Component.literal("The paused invasion has resumed.").withStyle(ChatFormatting.YELLOW), false);
        }

        reconcileTaggedMobs(level, anchor, state);
        updateTrackedMobs(level, state);
        redirectRaiders(level, state, members);

        boolean defended = hasDefenderNear(server, anchor, members);
        if (defended) state.abandonedTicks = 0;
        else state.abandonedTicks += 20;
        if (state.abandonedTicks >= RaidConfig.ABANDON_DEFEAT_MINUTES.get() * 60 * 20) {
            finishRaid(server, data, teamKey, false, false,
                    anchor.teamDisplay() + " abandoned its territory. The illager invasion has prevailed!");
            return;
        }

        if (state.wave > 0 && state.raiders.isEmpty() && state.ticksToNextWave <= 0) {
            if (state.wave >= RaidConfig.WAVES.get()) {
                finishRaid(server, data, teamKey, true, true,
                        anchor.teamDisplay() + " has crushed the illager invasion!");
                return;
            }
            state.ticksToNextWave = RaidConfig.TIME_BETWEEN_WAVES_SECONDS.get() * 20;
            announce(server, teamKey, Component.literal("Wave " + state.wave + " cleared. Next wave in " +
                    RaidConfig.TIME_BETWEEN_WAVES_SECONDS.get() + " seconds.").withStyle(ChatFormatting.GREEN), false);
        }

        if (state.ticksToNextWave > 0) {
            state.ticksToNextWave -= 20;
            int seconds = Math.max(0, (state.ticksToNextWave + 19) / 20);
            if (shouldWarn(seconds) && seconds < state.lastWarningSecond) {
                state.lastWarningSecond = seconds;
                announce(server, teamKey, Component.literal("Illager wave arrives in " + seconds + " seconds!")
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
                    spawnWave(server, level, data, anchor, state, members);
                }
            }
        }
        updateBossBar(server, anchor, state, false);
    }

    private static void updateTrackedMobs(ServerLevel level, RaidSavedData.RaidState state) {
        int grace = RaidConfig.MISSING_ENTITY_GRACE_SECONDS.get() * 20;
        Iterator<UUID> iterator = state.raiders.iterator();
        while (iterator.hasNext()) {
            UUID id = iterator.next();
            Entity entity = level.getEntity(id);
            if (entity == null) {
                int missing = state.missingTicks.getOrDefault(id, 0) + 20;
                if (missing >= grace) {
                    iterator.remove();
                    state.missingTicks.remove(id);
                } else state.missingTicks.put(id, missing);
                continue;
            }
            state.missingTicks.remove(id);
            if (!(entity instanceof Mob) || !entity.isAlive()) iterator.remove();
        }
    }

    private static void reconcileTaggedMobs(ServerLevel level, RaidSavedData.Anchor anchor,
                                            RaidSavedData.RaidState state) {
        state.reconcileTicks -= 20;
        if (state.reconcileTicks > 0) return;
        state.reconcileTicks = 10 * 20;
        double radius = RaidConfig.DEFENSE_RADIUS.get() + RaidConfig.MAX_SPAWN_DISTANCE.get() + 32.0;
        AABB area = new AABB(anchor.pos()).inflate(radius, 128.0, radius);
        for (Mob mob : level.getEntitiesOfClass(Mob.class, area,
                m -> state.teamKey.equals(m.getPersistentData().getString(RAID_TEAM_TAG)))) {
            if (mob.isAlive()) {
                state.raiders.add(mob.getUUID());
                state.missingTicks.remove(mob.getUUID());
            }
        }
    }

    private static void spawnWave(MinecraftServer server, ServerLevel level, RaidSavedData data,
                                  RaidSavedData.Anchor anchor, RaidSavedData.RaidState state,
                                  List<ServerPlayer> members) {
        int nextWave = state.wave + 1;
        int playerCount = Math.max(1, members.size());
        int wanted = RaidConfig.BASE_ENEMIES_PER_WAVE.get() +
                (playerCount - 1) * RaidConfig.ENEMIES_PER_EXTRA_PLAYER.get() + (nextWave - 1) * 2;
        int globalRemaining = Math.max(0, RaidConfig.MAX_GLOBAL_RAIDERS.get() - globalTrackedCount(data));
        wanted = Math.min(wanted, Math.min(RaidConfig.MAX_ACTIVE_RAIDERS.get(), globalRemaining));
        if (wanted <= 0) {
            state.ticksToNextWave = RaidConfig.SPAWN_RETRY_SECONDS.get() * 20;
            return;
        }

        int spawned = 0;
        for (int i = 0; i < wanted; i++) {
            Raider raider = createRaiderForWave(level, nextWave, i);
            if (raider == null) continue;
            BlockPos spawn = findSpawnPosition(level, anchor.pos(), level.random, raider);
            if (spawn == null) continue;
            raider.moveTo(spawn.getX() + 0.5, spawn.getY(), spawn.getZ() + 0.5,
                    level.random.nextFloat() * 360.0F, 0.0F);
            raider.finalizeSpawn(level, level.getCurrentDifficultyAt(spawn), MobSpawnType.EVENT, null, null);
            raider.setPersistenceRequired();
            raider.getPersistentData().putString(RAID_TEAM_TAG, anchor.teamKey());
            if (level.addFreshEntity(raider)) {
                state.raiders.add(raider.getUUID());
                spawned++;
            }
        }
        if (spawned == 0) {
            state.ticksToNextWave = RaidConfig.SPAWN_RETRY_SECONDS.get() * 20;
            announce(server, anchor.teamKey(), Component.literal("No safe invasion entrance was found. Retrying in " +
                    RaidConfig.SPAWN_RETRY_SECONDS.get() + " seconds.").withStyle(ChatFormatting.YELLOW), false);
            return;
        }

        state.wave = nextWave;
        state.waveStartingCount = spawned;
        state.ticksToNextWave = 0;
        announce(server, anchor.teamKey(), Component.literal("Wave " + state.wave + "/" + RaidConfig.WAVES.get() +
                " has begun—" + spawned + " illagers are attacking your faction!").withStyle(ChatFormatting.RED), true);
        data.setDirty();
    }

    private static Raider createRaiderForWave(ServerLevel level, int wave, int index) {
        EntityType<? extends Raider> type;
        if (wave >= 5 && index == 0) type = EntityType.RAVAGER;
        else if (wave >= 4 && index == 1) type = EntityType.EVOKER;
        else if (wave >= 3 && index == 2) type = EntityType.WITCH;
        else if ((index + wave) % 3 == 0) type = EntityType.VINDICATOR;
        else type = EntityType.PILLAGER;
        return type.create(level);
    }

    private static BlockPos findSpawnPosition(ServerLevel level, BlockPos anchor, RandomSource random, Mob mob) {
        int min = RaidConfig.MIN_SPAWN_DISTANCE.get();
        int max = Math.max(min, RaidConfig.MAX_SPAWN_DISTANCE.get());
        for (int attempt = 0; attempt < 32; attempt++) {
            double angle = random.nextDouble() * Math.PI * 2.0;
            int distance = min + random.nextInt(max - min + 1);
            int x = anchor.getX() + Mth.floor(Math.cos(angle) * distance);
            int z = anchor.getZ() + Mth.floor(Math.sin(angle) * distance);
            if (!level.hasChunk(x >> 4, z >> 4)) continue;
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            if (Math.abs(y - anchor.getY()) > 48) continue;
            BlockPos p = new BlockPos(x, y, z);
            BlockState ground = level.getBlockState(p.below());
            if (!level.getWorldBorder().isWithinBounds(p) || !level.getFluidState(p).isEmpty() ||
                    !level.getFluidState(p.below()).isEmpty() || !level.isEmptyBlock(p) ||
                    !level.isEmptyBlock(p.above()) || !ground.isFaceSturdy(level, p.below(), Direction.UP)) continue;
            mob.moveTo(x + 0.5, y, z + 0.5, 0.0F, 0.0F);
            if (level.noCollision(mob)) return p;
        }
        return null;
    }

    private static void redirectRaiders(ServerLevel level, RaidSavedData.RaidState state,
                                        List<ServerPlayer> members) {
        boolean glow = RaidConfig.GLOW_FINAL_ENEMIES.get() && state.raiders.size() <= 3;
        for (UUID id : state.raiders) {
            Entity entity = level.getEntity(id);
            if (!(entity instanceof Mob mob) || !mob.isAlive()) continue;
            ServerPlayer closest = null;
            double closestDistance = Double.MAX_VALUE;
            for (ServerPlayer player : members) {
                if (!player.isAlive() || player.level() != level || player.isSpectator()) continue;
                double distance = mob.distanceToSqr(player);
                if (distance < closestDistance) {
                    closest = player;
                    closestDistance = distance;
                }
            }
            if (closest != null) mob.setTarget(closest);
            if (glow) mob.addEffect(new MobEffectInstance(MobEffects.GLOWING, 40, 0, false, false));
        }
    }

    private static boolean hasDefenderNear(MinecraftServer server, RaidSavedData.Anchor anchor,
                                           List<ServerPlayer> members) {
        ServerLevel level = getLevel(server, anchor);
        if (level == null) return false;
        double radiusSq = (double) RaidConfig.DEFENSE_RADIUS.get() * RaidConfig.DEFENSE_RADIUS.get();
        for (ServerPlayer player : members) {
            if (player.level() == level && player.isAlive() && !player.isSpectator() &&
                    player.distanceToSqr(anchor.pos().getX() + 0.5, anchor.pos().getY() + 0.5,
                            anchor.pos().getZ() + 0.5) <= radiusSq) return true;
        }
        return false;
    }

    private static void finishRaid(MinecraftServer server, RaidSavedData data, String teamKey,
                                   boolean victory, boolean reward, String message) {
        RaidSavedData.RaidState state = data.raids.remove(teamKey);
        RaidSavedData.Anchor anchor = data.anchors.get(teamKey);
        if (state != null && anchor != null) {
            ServerLevel level = getLevel(server, anchor);
            if (level != null) {
                for (UUID id : state.raiders) {
                    Entity entity = level.getEntity(id);
                    if (entity != null) entity.discard();
                }
            }
            long next = server.overworld().getGameTime() + randomCooldownTicks(server.overworld().random);
            data.anchors.put(teamKey, anchor.withNextRaid(next));
        }
        if (victory && reward) {
            int experience = RaidConfig.VICTORY_EXPERIENCE.get();
            if (experience > 0) onlineMembers(server, teamKey).forEach(p -> p.giveExperiencePoints(experience));
        }
        ServerBossEvent bar = BOSS_BARS.remove(teamKey);
        if (bar != null) bar.removeAllPlayers();
        announce(server, teamKey, Component.literal(message)
                .withStyle(victory ? ChatFormatting.GREEN : ChatFormatting.DARK_RED), victory);
        data.setDirty();
    }

    private static void updateBossBar(MinecraftServer server, RaidSavedData.Anchor anchor,
                                      RaidSavedData.RaidState state, boolean paused) {
        ServerBossEvent bar = BOSS_BARS.computeIfAbsent(anchor.teamKey(), key ->
                new ServerBossEvent(Component.literal("Illager Invasion"), BossEvent.BossBarColor.RED,
                        BossEvent.BossBarOverlay.NOTCHED_10));
        List<ServerPlayer> currentMembers = onlineMembers(server, anchor.teamKey());
        for (ServerPlayer shown : new ArrayList<>(bar.getPlayers())) {
            if (!currentMembers.contains(shown)) bar.removePlayer(shown);
        }
        for (ServerPlayer p : currentMembers) bar.addPlayer(p);
        int totalWaves = RaidConfig.WAVES.get();
        float completedWaves = Math.max(0, state.wave - 1);
        float clearedFraction = state.waveStartingCount <= 0 ? 0.0F :
                1.0F - (float) state.raiders.size() / state.waveStartingCount;
        bar.setProgress(Mth.clamp((completedWaves + clearedFraction) / totalWaves, 0.0F, 1.0F));
        String label = paused ? "Invasion paused — faction offline" : state.wave == 0 ? "Invasion approaching" :
                "Wave " + state.wave + "/" + totalWaves + " • " + state.raiders.size() + " remaining";
        bar.setName(Component.literal(label));
    }

    private static void announce(MinecraftServer server, String teamKey, Component message, boolean horn) {
        if (RaidConfig.ANNOUNCE_GLOBALLY.get()) {
            server.getPlayerList().broadcastSystemMessage(message, false);
        } else onlineMembers(server, teamKey).forEach(p -> p.sendSystemMessage(message));
        if (horn) onlineMembers(server, teamKey).forEach(p ->
                p.playNotifySound(SoundEvents.RAID_HORN.value(), SoundSource.HOSTILE, 1.0F, 1.0F));
    }

    private static List<ServerPlayer> onlineMembers(MinecraftServer server, String key) {
        List<ServerPlayer> result = new ArrayList<>();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (teamKey(player).equals(key)) result.add(player);
        }
        return result;
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
            // downgrade a shared faction anchor to a solo-player anchor during that window.
            if (oldKey.startsWith("team:") && currentKey.startsWith("player:")) continue;
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
                player.getUUID().equals(anchor.ownerUuid());
    }

    private static boolean shouldPauseForPerformance(MinecraftServer server, RaidSavedData data) {
        if (globalTrackedCount(data) >= RaidConfig.MAX_GLOBAL_RAIDERS.get()) return true;
        if (!RaidConfig.PAUSE_SPAWNING_BELOW_TPS.get()) return false;
        float averageTickMs = server.getAverageTickTime();
        double approximateTps = averageTickMs <= 0.0F ? 20.0 : Math.min(20.0, 1000.0 / averageTickMs);
        return approximateTps < RaidConfig.MINIMUM_TPS_TO_SPAWN.get();
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

    private static ServerLevel getLevel(MinecraftServer server, RaidSavedData.Anchor anchor) {
        ResourceKey<Level> key = ResourceKey.create(Registries.DIMENSION, anchor.dimension());
        return server.getLevel(key);
    }

    private static long randomCooldownTicks(RandomSource random) {
        int min = RaidConfig.MIN_COOLDOWN_MINUTES.get();
        int max = Math.max(min, RaidConfig.MAX_COOLDOWN_MINUTES.get());
        int minutes = min + random.nextInt(max - min + 1);
        return minutes * 60L * 20L;
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
