package com.devfarinsky.factionraids;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
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
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
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
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.*;

public final class RaidEvents {
    private static final Component MESSAGE_PREFIX = Component.literal("[Faction Raids] ")
            .withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD);
    private static final String RAID_TEAM_TAG = "FactionRaidsTeam";
    private static final String RAID_ROLE_TAG = "FactionRaidsRole";
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
        data.setDirty();
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
        String teamKey = event.getEntity().getPersistentData().getString(RAID_TEAM_TAG);
        if (teamKey.isBlank()) return;
        RaidSavedData data = RaidSavedData.get(level.getServer());
        RaidSavedData.RaidState state = data.raids.get(teamKey);
        if (state == null || !state.raiders.remove(event.getEntity().getUUID())) return;
        state.missingTicks.remove(event.getEntity().getUUID());
        state.totalDefeated++;
        if (event.getEntity().getUUID().equals(state.commanderUuid) && !state.commanderDefeated) {
            RaidSavedData.Anchor anchor = data.anchors.get(teamKey);
            if (anchor != null) markCommanderDefeated(level.getServer(), anchor, state);
        }
        data.setDirty();
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        BOSS_BARS.values().forEach(ServerBossEvent::removeAllPlayers);
        BOSS_BARS.clear();
    }

    private static void registerCommands(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("factionraids")
                .executes(ctx -> openDashboard(ctx.getSource()))
                .then(Commands.literal("menu").executes(ctx -> openDashboard(ctx.getSource())))
                .then(Commands.literal("anchor")
                        .then(Commands.literal("set").executes(ctx -> setAnchor(ctx.getSource())))
                        .then(Commands.literal("claim").executes(ctx -> claimLegacyAnchor(ctx.getSource())))
                        .then(Commands.literal("remove").executes(ctx -> removeAnchor(ctx.getSource()))))
                .then(Commands.literal("home")
                        .then(Commands.literal("automatic").executes(ctx -> enableAutomaticHome(ctx.getSource())))
                        .then(Commands.literal("refresh").executes(ctx -> refreshAutomaticHome(ctx.getSource()))))
                .then(Commands.literal("territory")
                        .then(Commands.literal("add")
                                .then(Commands.argument("name", StringArgumentType.word())
                                        .executes(ctx -> addDefensePoint(ctx.getSource(),
                                                StringArgumentType.getString(ctx, "name")))))
                        .then(Commands.literal("remove")
                                .then(Commands.argument("name", StringArgumentType.word())
                                        .executes(ctx -> removeDefensePoint(ctx.getSource(),
                                                StringArgumentType.getString(ctx, "name")))))
                        .then(Commands.literal("list").executes(ctx -> listDefensePoints(ctx.getSource()))))
                .then(Commands.literal("member")
                        .then(Commands.literal("add")
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(ctx -> addMember(ctx.getSource(),
                                                EntityArgument.getPlayer(ctx, "player")))))
                        .then(Commands.literal("remove")
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(ctx -> removeMember(ctx.getSource(),
                                                EntityArgument.getPlayer(ctx, "player")))))
                        .then(Commands.literal("list").executes(ctx -> listMembers(ctx.getSource()))))
                .then(Commands.literal("start")
                        .executes(ctx -> startOwnRaid(ctx.getSource(), null))
                        .then(Commands.argument("point", StringArgumentType.word())
                                .executes(ctx -> startOwnRaid(ctx.getSource(),
                                        StringArgumentType.getString(ctx, "point")))))
                .then(Commands.literal("status").executes(ctx -> status(ctx.getSource())))
                .then(Commands.literal("debug").executes(ctx -> debug(ctx.getSource())))
                .then(Commands.literal("help").executes(ctx -> help(ctx.getSource())))
                .then(Commands.literal("stop").requires(s -> s.hasPermission(2))
                        .executes(ctx -> stopOwnRaid(ctx.getSource())))
                .then(Commands.literal("admin").requires(s -> s.hasPermission(2))
                        .then(Commands.literal("list").executes(ctx -> adminList(ctx.getSource())))
                        .then(Commands.literal("stop")
                                .then(Commands.argument("team", StringArgumentType.word())
                                        .executes(ctx -> adminStop(ctx.getSource(), StringArgumentType.getString(ctx, "team")))))
                        .then(Commands.literal("remove")
                                .then(Commands.argument("team", StringArgumentType.word())
                                        .executes(ctx -> adminRemove(ctx.getSource(), StringArgumentType.getString(ctx, "team")))))
                        .then(Commands.literal("repair")
                                .then(Commands.argument("team", StringArgumentType.word())
                                        .executes(ctx -> adminRepair(ctx.getSource(), StringArgumentType.getString(ctx, "team")))))));
    }

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
                    ". Illager invasions will target your faction players here.").withStyle(ChatFormatting.GREEN), false);
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
                if (data.raids.containsKey(anchor.teamKey()) || now < anchor.nextRaidGameTime()) continue;
                List<ServerPlayer> members = onlineMembers(server, anchor.teamKey());
                if (members.isEmpty()) continue;
                RaidSavedData.DefensePoint point = selectAutomaticPoint(server, anchor, members);
                if (point == null) continue;
                beginRaid(server, data, anchor, point, true);
            }
        }

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
        ServerLevel raidLevel = getLevel(server, point);
        if (raidLevel != null && RaidConfig.BUILD_WAR_CAMPS.get()) buildWarCamp(raidLevel, point, state);
        data.raids.put(anchor.teamKey(), state);
        data.setDirty();
        announce(server, anchor.teamKey(), Component.literal("Illager scouts have found " + anchor.teamDisplay() + " at '" + point.name() +
                        "'! A war camp " + (state.campPos == null ? "is forming" : "has been raised at " + formatPos(state.campPos)) +
                        " to the " + approachDirection(state.approachAngle) + ". The siege begins in " +
                        formatTime(RaidConfig.WARNING_SECONDS.get()) + ". Rally your Recruits and defend the stronghold.")
                        .withStyle(ChatFormatting.GOLD), true);
        showTitle(server, anchor.teamKey(), Component.literal("SIEGE INCOMING").withStyle(ChatFormatting.DARK_RED),
                Component.literal("Enemy war camp sighted to the " + approachDirection(state.approachAngle))
                        .withStyle(ChatFormatting.GOLD));
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
            buildWarCamp(level, point, state);
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

        reconcileTaggedMobs(level, point, state);
        updateTrackedMobs(level, state);
        List<Mob> recruits = alliedRecruits(level, point, anchor);
        if (RaidConfig.MOBILIZE_RECRUITS.get()) mobilizeRecruits(level, recruits, state);
        redirectRaiders(level, state, members, recruits, point);

        if (updateCaptureProgress(server, anchor, point, state, level, members, recruits)) {
            finishRaid(server, data, teamKey, false, false,
                    anchor.teamDisplay() + "'s stronghold has fallen to the illager siege!");
            return;
        }

        boolean defended = hasDefenderNear(server, point, members);
        if (defended) state.abandonedTicks = 0;
        else state.abandonedTicks += 20;
        int abandonmentMinutes = RaidConfig.ABANDON_DEFEAT_MINUTES.get();
        if (abandonmentMinutes > 0 && state.abandonedTicks >= abandonmentMinutes * 60 * 20) {
            finishRaid(server, data, teamKey, false, false,
                    anchor.teamDisplay() + " abandoned its territory. The illager invasion has prevailed!");
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
                        anchor.teamDisplay() + " has crushed the illager invasion!");
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
                    queueWave(server, level, data, anchor, point, state, members, recruits);
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
                    state.totalEscaped++;
                } else state.missingTicks.put(id, missing);
                continue;
            }
            state.missingTicks.remove(id);
            if (!(entity instanceof Mob) || !entity.isAlive()) {
                iterator.remove();
                state.totalDefeated++;
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
        state.waveStartingCount = 0;
        state.pendingWaveSpawns = wanted;
        state.squadsSpawned = 0;
        state.ticksToNextWave = 0;
        state.ticksToNextSquad = 0;
        state.lastWarningSecond = Integer.MAX_VALUE;
        announce(server, anchor.teamKey(), Component.literal(waveTitle(state.wave) + " — wave " + state.wave + "/" +
                RaidConfig.WAVES.get() + ": " + wanted + " invaders are advancing from the " +
                approachDirection(state.approachAngle) +
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

        int spawned = 0;
        for (int i = 0; i < wanted; i++) {
            int waveIndex = state.waveStartingCount + spawned;
            Mob raider = createAttackerForWave(level, state.wave, waveIndex);
            if (raider == null) continue;
            BlockPos spawn = findSpawnPosition(level, point.pos(), level.random, raider,
                    state.approachAngle, state.campPos);
            if (spawn == null) continue;
            raider.moveTo(spawn.getX() + 0.5, spawn.getY(), spawn.getZ() + 0.5,
                    level.random.nextFloat() * 360.0F, 0.0F);
            raider.finalizeSpawn(level, level.getCurrentDifficultyAt(spawn), MobSpawnType.EVENT, null, null);
            boolean squadLeader = spawned == 0;
            if (squadLeader && raider instanceof Raider vanillaRaider) vanillaRaider.setPatrolLeader(true);
            RecruitsBridge.configureHostileRaidRecruit(raider);
            raider.setPersistenceRequired();
            raider.getPersistentData().putString(RAID_TEAM_TAG, anchor.teamKey());
            if (level.addFreshEntity(raider)) {
                assignSiegeRole(raider, state, waveIndex, squadLeader);
                state.raiders.add(raider.getUUID());
                state.totalSpawned++;
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
            announce(server, anchor.teamKey(), Component.literal("No safe invasion entrance was found. Retrying in " +
                    RaidConfig.SPAWN_RETRY_SECONDS.get() + " seconds.").withStyle(ChatFormatting.YELLOW), false);
            return;
        }

        state.waveStartingCount += spawned;
        state.pendingWaveSpawns -= spawned;
        state.squadsSpawned++;
        state.ticksToNextSquad = state.pendingWaveSpawns > 0 ?
                RaidConfig.SQUAD_INTERVAL_SECONDS.get() * 20 : 0;
        if (state.squadsSpawned > 1 || state.pendingWaveSpawns > 0) {
            announce(server, anchor.teamKey(), Component.literal("Assault squad " + state.squadsSpawned +
                    " entered the battlefield; " + state.pendingWaveSpawns + " reinforcements remain in the war camp.")
                    .withStyle(ChatFormatting.GRAY), false);
        }
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
            raider.setCustomName(Component.literal("Illager Siege Commander").withStyle(ChatFormatting.DARK_RED));
            raider.setCustomNameVisible(true);
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
        announce(server, anchor.teamKey(), Component.literal("The siege commander has fallen! Illager " +
                        pressure + " lost 30 seconds of progress.")
                .withStyle(ChatFormatting.GREEN), true);
        sendActionBar(server, anchor.teamKey(), Component.literal("COMMANDER DEFEATED • " +
                        (state.breached ? "Occupation" : "Breach") + " pushed back")
                .withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD));
    }

    private static Mob createAttackerForWave(ServerLevel level, int wave, int index) {
        if (!RaidConfig.USE_RECRUIT_INVADERS.get()) return createVanillaAttacker(level, wave, index);
        if (wave >= RaidConfig.WAVES.get() && index == 1) return EntityType.RAVAGER.create(level);
        if (RaidConfig.ENABLE_ILLUSIONERS.get() && wave >= 4 && index == 2) {
            return EntityType.ILLUSIONER.create(level);
        }

        String recruitType;
        if (wave >= RaidConfig.WAVES.get() && index == 0) recruitType = "patrol_leader";
        else if (wave >= 4 && index % 9 == 1 && ForgeRegistries.ENTITY_TYPES.containsKey(
                new ResourceLocation("recruits", "siege_engineer"))) recruitType = "siege_engineer";
        else if (wave >= 4 && index % 8 == 3) recruitType = "assassin";
        else if (wave >= 3 && index % 7 == 0) recruitType = "captain";
        else if ((index + wave) % 4 == 0) recruitType = "recruit_shieldman";
        else if ((index + wave) % 3 == 0) recruitType = "bowman";
        else if ((index + wave) % 2 == 0) recruitType = "crossbowman";
        else recruitType = "recruit";

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

    private static void buildWarCamp(ServerLevel level, RaidSavedData.DefensePoint point,
                                     RaidSavedData.RaidState state) {
        state.campBuildAttempted = true;
        BlockPos camp = findWarCampPosition(level, point.pos(), state.approachAngle);
        if (camp == null) return;
        state.campPos = camp;

        placeCampBlock(level, state, surfacePosition(level, camp.getX(), camp.getZ()), Blocks.CAMPFIRE);
        placeCampBlock(level, state, surfacePosition(level, camp.getX() - 2, camp.getZ()), Blocks.BARREL);
        placeCampBlock(level, state, surfacePosition(level, camp.getX() + 2, camp.getZ()), Blocks.CRAFTING_TABLE);
        placeCampBlock(level, state, surfacePosition(level, camp.getX(), camp.getZ() - 3), Blocks.RED_BANNER);

        int tentY = camp.getY();
        for (int dx : new int[]{-3, 3}) {
            for (int dz : new int[]{3, 6}) {
                placeCampBlock(level, state, new BlockPos(camp.getX() + dx, tentY,
                        camp.getZ() + dz), Blocks.SPRUCE_FENCE);
                placeCampBlock(level, state, new BlockPos(camp.getX() + dx, tentY + 1,
                        camp.getZ() + dz), Blocks.SPRUCE_FENCE);
            }
        }
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = 3; dz <= 6; dz++) {
                placeCampBlock(level, state, new BlockPos(camp.getX() + dx, tentY + 2,
                        camp.getZ() + dz), Blocks.RED_WOOL);
            }
        }

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
    }

    private static BlockPos findWarCampPosition(ServerLevel level, BlockPos anchor, double approachAngle) {
        int min = RaidConfig.MIN_SPAWN_DISTANCE.get();
        int max = Math.max(min, RaidConfig.MAX_SPAWN_DISTANCE.get());
        for (int attempt = 0; attempt < 32; attempt++) {
            double angle = approachAngle + (level.random.nextDouble() - 0.5D) * 0.5D;
            int distance = Math.max(min, max - level.random.nextInt(Math.max(1, Math.min(16, max - min + 1))));
            int x = anchor.getX() + Mth.floor(Math.cos(angle) * distance);
            int z = anchor.getZ() + Mth.floor(Math.sin(angle) * distance);
            if (!level.hasChunk(x >> 4, z >> 4)) continue;
            BlockPos center = surfacePosition(level, x, z);
            if (!validCampSurface(level, center, anchor)) continue;
            return center;
        }
        return null;
    }

    private static boolean validCampSurface(ServerLevel level, BlockPos center, BlockPos anchor) {
        if (!level.getWorldBorder().isWithinBounds(center) || Math.abs(center.getY() - anchor.getY()) > 48 ||
                !level.getFluidState(center).isEmpty() || !level.getBlockState(center).canBeReplaced() ||
                !level.getBlockState(center.below()).isFaceSturdy(level, center.below(), Direction.UP)) return false;
        for (int dx : new int[]{-3, 3}) {
            for (int dz : new int[]{-3, 6}) {
                BlockPos sample = surfacePosition(level, center.getX() + dx, center.getZ() + dz);
                if (Math.abs(sample.getY() - center.getY()) > 2 || !level.getFluidState(sample).isEmpty()) return false;
            }
        }
        return true;
    }

    private static BlockPos surfacePosition(ServerLevel level, int x, int z) {
        return new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
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

    private static void redirectRaiders(ServerLevel level, RaidSavedData.RaidState state,
                                        List<ServerPlayer> members, List<Mob> recruits,
                                        RaidSavedData.DefensePoint point) {
        boolean glow = RaidConfig.GLOW_FINAL_ENEMIES.get() && state.raiders.size() <= 3;
        for (UUID id : state.raiders) {
            Entity entity = level.getEntity(id);
            if (!(entity instanceof Mob mob) || !mob.isAlive()) continue;
            LivingEntity closest = null;
            double closestDistance = Double.MAX_VALUE;
            for (ServerPlayer player : members) {
                if (!player.isAlive() || player.level() != level || player.isSpectator()) continue;
                double distance = mob.distanceToSqr(player);
                if (distance < closestDistance) {
                    closest = player;
                    closestDistance = distance;
                }
            }
            for (Mob recruit : recruits) {
                if (!recruit.isAlive()) continue;
                double distance = mob.distanceToSqr(recruit);
                if (distance < closestDistance) {
                    closest = recruit;
                    closestDistance = distance;
                }
            }
            if (closest != null && closestDistance <= (double) RaidConfig.DEFENSE_RADIUS.get() *
                    RaidConfig.DEFENSE_RADIUS.get()) mob.setTarget(closest);
            else {
                Vec3 objective = invasionObjective(level, point, state);
                mob.getNavigation().moveTo(objective.x, objective.y, objective.z,
                        RaidConfig.RAIDER_ADVANCE_SPEED.get());
            }
            if (glow) mob.addEffect(new MobEffectInstance(MobEffects.GLOWING, 40, 0, false, false));
        }
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
                announce(server, anchor.teamKey(), Component.literal("The perimeter has been breached! Illagers are pushing for the stronghold heart.")
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

        int band = maximum <= 0 ? 0 : state.captureTicks * 4 / maximum;
        if (band > state.lastCaptureWarningBand && band < 4) {
            state.lastCaptureWarningBand = band;
            int percent = band * 25;
            announce(server, anchor.teamKey(), Component.literal("Stronghold occupation: " + percent +
                    "%. Push the illagers out of the inner defense ring!").withStyle(ChatFormatting.DARK_RED),
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
        RaidSavedData.RaidState state = data.raids.remove(teamKey);
        RaidSavedData.Anchor anchor = data.anchors.get(teamKey);
        if (state != null && anchor != null) {
            RaidSavedData.DefensePoint point = anchor.point(state.defensePointName);
            ServerLevel level = getLevel(server, point);
            if (level != null) {
                for (UUID id : state.raiders) {
                    Entity entity = level.getEntity(id);
                    if (entity != null) entity.discard();
                }
                cleanupWarCamp(level, state);
            }
            long next = server.overworld().getGameTime() + randomCooldownTicks(server.overworld().random);
            data.anchors.put(teamKey, anchor.withNextRaid(next));
        }
        boolean eligibleVictory = victory && reward && state != null && state.rewardEligible;
        if (eligibleVictory) {
            int experience = RaidConfig.VICTORY_EXPERIENCE.get();
            List<ServerPlayer> winners = onlineMembers(server, teamKey);
            if (experience > 0) winners.forEach(p -> p.giveExperiencePoints(experience));
            int emeralds = guaranteedEmeraldReward(state);
            if (emeralds > 0) winners.forEach(p -> giveEmeralds(p, emeralds));
            if (RaidConfig.VICTORY_LOOT_ENABLED.get()) winners.forEach(p -> giveVictoryLoot(server, p));
        }
        ServerBossEvent bar = BOSS_BARS.remove(teamKey);
        if (bar != null) bar.removeAllPlayers();
        long elapsedTicks = state == null || state.startedGameTime <= 0 ? 0 :
                Math.max(0, server.overworld().getGameTime() - state.startedGameTime);
        String summary = state == null ? "" : " Defeated: " + state.totalDefeated +
                " of " + state.totalSpawned + " deployed" +
                (state.totalEscaped > 0 ? "; " + state.totalEscaped + " lost contact" : "") +
                (elapsedTicks > 0 ? "; duration " + formatTime(elapsedTicks / 20) : "") + ".";
        announce(server, teamKey, Component.literal(message + summary)
                .withStyle(victory ? ChatFormatting.GREEN : ChatFormatting.DARK_RED), victory);
        if (victory && reward && !eligibleVictory) {
            announce(server, teamKey, Component.literal("Practice siege complete. Manual test raids do not grant rewards by default.")
                    .withStyle(ChatFormatting.YELLOW), false);
        } else if (eligibleVictory) {
            int emeralds = guaranteedEmeraldReward(state);
            announce(server, teamKey, Component.literal("Victory spoils: " + emeralds +
                    " guaranteed emeralds, " + RaidConfig.VICTORY_EXPERIENCE.get() +
                    " experience and bonus campaign loot for each online faction member.")
                    .withStyle(ChatFormatting.GREEN), false);
        }
        showTitle(server, teamKey,
                Component.literal(victory ? "SIEGE BROKEN" : "STRONGHOLD FALLEN")
                        .withStyle(victory ? ChatFormatting.GREEN : ChatFormatting.DARK_RED),
                Component.literal(victory ? "Your faction held the line" : "The illagers seized the objective")
                        .withStyle(ChatFormatting.GOLD));
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
        int planned = Math.max(state.plannedWaveSize, state.waveStartingCount + state.pendingWaveSpawns);
        float clearedFraction = planned <= 0 ? 0.0F :
                1.0F - (float) (state.raiders.size() + state.pendingWaveSpawns) / planned;
        bar.setProgress(Mth.clamp((completedWaves + clearedFraction) / totalWaves, 0.0F, 1.0F));
        int capturePercent = Mth.clamp(state.captureTicks * 100 /
                Math.max(1, RaidConfig.CAPTURE_TIME_SECONDS.get() * 20), 0, 100);
        int breachPercent = breachPercent(state);
        String label = paused ? "Invasion paused — faction offline" : state.wave == 0 ?
                "Siege camp forming to the " + approachDirection(state.approachAngle) :
                !state.breached && RaidConfig.ENABLE_BREACH_PHASE.get() ?
                        "Perimeter assault • " + state.raiders.size() + " deployed • breach " +
                                breachPercent + "%" :
                        waveTitle(state.wave) + " • " + state.raiders.size() + " deployed" +
                                (state.pendingWaveSpawns > 0 ? " + " + state.pendingWaveSpawns + " reinforcing" : "") +
                                " • stronghold " + capturePercent + "% occupied";
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
            return player.openMenu(new SimpleMenuProvider(
                    (id, inventory, ignored) -> new RaidDashboardMenu(id, inventory, player),
                    Component.literal("Faction Raids • Command Table"))).isPresent() ? 1 : 0;
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

    static DashboardSnapshot dashboardSnapshot(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        if (server == null) return DashboardSnapshot.unavailable();
        RaidSavedData data = RaidSavedData.get(server);
        String key = factionKeyForPlayer(data, player);
        RaidSavedData.Anchor anchor = data.anchors.get(key);
        if (anchor == null) {
            return new DashboardSnapshot(teamDisplay(player), false, false, "No stronghold registered",
                    0, RaidConfig.WAVES.get(), 0, 0, 0, 0, false, 0, 0, 0, 0, 0, 0, "Sleep at your base",
                    defaultEmeraldReward(), false);
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
            return new DashboardSnapshot(anchor.teamDisplay(), true, false,
                    point.dimension() + " • " + formatPos(point.pos()), 0, RaidConfig.WAVES.get(),
                    0, 0, 0, 0, false, 0, recruits, compat.workers(), compat.ships(), compat.siegeWeapons(),
                    assetScalingEnemies(compat), formatTime(seconds), defaultEmeraldReward(), true);
        }
        int occupation = state.captureTicks * 100 /
                Math.max(1, RaidConfig.CAPTURE_TIME_SECONDS.get() * 20);
        return new DashboardSnapshot(anchor.teamDisplay(), true, true,
                point.dimension() + " • " + formatPos(point.pos()) +
                        (state.campPos == null ? " • camp unavailable" : " • camp " + formatPos(state.campPos)),
                state.wave, RaidConfig.WAVES.get(),
                state.raiders.size(), state.pendingWaveSpawns, state.totalDefeated, occupation,
                state.breached || !RaidConfig.ENABLE_BREACH_PHASE.get(), breachPercent(state),
                recruits, compat.workers(), compat.ships(), compat.siegeWeapons(), assetScalingEnemies(compat), "Siege active",
                guaranteedEmeraldReward(state), state.rewardEligible);
    }

    record DashboardSnapshot(String faction, boolean registered, boolean active, String stronghold,
                             int wave, int totalWaves, int deployed, int reinforcing, int defeated,
                             int occupationPercent, boolean breached, int breachPercent,
                             int recruits, int workers, int ships,
                             int siegeWeapons, int assetScalingEnemies, String cooldown,
                             int emeraldReward, boolean rewardEligible) {
        static DashboardSnapshot unavailable() {
            return new DashboardSnapshot("Unavailable", false, false, "Server unavailable", 0, 0,
                    0, 0, 0, 0, false, 0, 0, 0, 0, 0, 0, "Unavailable", 0, false);
        }
    }

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

    private static boolean shouldPauseForPerformance(MinecraftServer server, RaidSavedData data) {
        if (globalTrackedCount(data) >= RaidConfig.MAX_GLOBAL_RAIDERS.get()) return true;
        if (!RaidConfig.PAUSE_SPAWNING_BELOW_TPS.get()) return false;
        return approximateTps(server) < RaidConfig.MINIMUM_TPS_TO_SPAWN.get();
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
