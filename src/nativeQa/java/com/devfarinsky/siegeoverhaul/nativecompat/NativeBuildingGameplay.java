package com.devfarinsky.siegeoverhaul.nativecompat;

import com.devfarinsky.siegeoverhaul.ModConstants;
import com.devfarinsky.siegeoverhaul.RaidNetwork;
import com.devfarinsky.siegeoverhaul.RaidSavedData;
import com.devfarinsky.siegeoverhaul.compat.WorkersBridge;
import com.devfarinsky.siegeoverhaul.core.*;
import com.devfarinsky.siegeoverhaul.items.DefensePlanItem;
import com.devfarinsky.siegeoverhaul.items.ModItems;
import com.talhanation.recruits.ClaimEvents;
import com.talhanation.recruits.world.RecruitsClaim;
import com.talhanation.workers.entities.BuilderEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundSetCarriedItemPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.registries.ForgeRegistries;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/** Bounded real-player/AI acceptance in a second fresh, cheats-off fixture world. */
final class NativeBuildingGameplay {
    private static final String WORLD = "siege-native-gameplay";
    private static final List<String> CHECKS = new ArrayList<>();
    private static final Map<String, Object> RESULT = new LinkedHashMap<>();
    private static CompletableFuture<Action> pending;
    private static NativeGameplayFixture.Fixture fixture;
    private static UUID playerId, jobId, ledgerGeneration;
    private static int clientStage, stage;
    private static long stageSince = -1, resumeAt, lastChange, lastPlaced = -1;
    private static long bankAfterManual;
    private static int suppliedCobble = 8, suppliedOak = 8;
    private static Map<Long, BlockState> pausedCells;
    private static List<ChunkPos> claimChunks;
    private static RecruitsClaim claim;
    private static boolean done;

    private enum Action { NONE, USE_BLOCK, USE_AIR, CANCEL, SHOW, RELOAD, CAPTURE_REVIEW, CAPTURE_PAID, AIM_WALL, DONE }
    private NativeBuildingGameplay() {}

    static boolean tick(Minecraft mc) throws Exception {
        if (done) return true;
        if (clientStage == 0) {
            mc.setScreen(null); mc.level.disconnect(); mc.clearLevel(); mc.setScreen(new TitleScreen());
            clientStage = 1; return false;
        }
        if (clientStage == 1) {
            if (mc.getSingleplayerServer() != null || !(mc.screen instanceof TitleScreen)) return false;
            Path world = mc.gameDirectory.toPath().resolve("saves").resolve(WORLD);
            require(!Files.exists(world), "Refusing an existing gameplay world");
            GameRules rules = new GameRules();
            rules.getRule(GameRules.RULE_DAYLIGHT).set(false, null);
            rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false, null);
            rules.getRule(GameRules.RULE_WEATHER_CYCLE).set(false, null);
            clientStage = 2;
            mc.createWorldOpenFlows().createFreshLevel(WORLD,
                    new LevelSettings(WORLD, GameType.SURVIVAL, false, Difficulty.PEACEFUL, false,
                            rules, WorldDataConfiguration.DEFAULT), new WorldOptions(20261004L, false, false),
                    access -> access.registryOrThrow(Registries.WORLD_PRESET).getOrThrow(WorldPresets.FLAT).createWorldDimensions());
            return false;
        }
        if (clientStage == 4) {
            if (mc.getSingleplayerServer() != null || !(mc.screen instanceof TitleScreen)) return false;
            clientStage = 2;
            mc.createWorldOpenFlows().loadLevel(new TitleScreen(), WORLD);
            return false;
        }
        if (mc.level == null || mc.player == null || mc.getSingleplayerServer() == null || mc.screen != null) return false;
        if (playerId == null) playerId = mc.player.getUUID();
        clientStage = 3;
        if (pending != null) {
            if (!pending.isDone()) return false;
            Action action = pending.join(); pending = null;
            switch (action) {
                case AIM_WALL -> {
                    mc.player.setYRot(0); mc.player.yRotO = 0;
                    mc.player.setXRot(25); mc.player.xRotO = 25;
                    return false;
                }
                case USE_BLOCK -> {
                    BlockPos anchor = fixture.wallAnchor();
                    mc.player.setYRot(0); mc.player.yRotO = 0;
                    mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND,
                            new BlockHitResult(new Vec3(anchor.getX() + .5, anchor.getY(), anchor.getZ() + .5),
                                    Direction.UP, anchor.below(), false));
                }
                case USE_AIR -> mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND);
                case CAPTURE_REVIEW -> {
                    aim(mc, new Vec3(fixture.wallAnchor().getX() + .5, 67, 30));
                    NativeBuildingQa.captureGameplay("14-production-free-perimeter-review.png");
                    return false;
                }
                case CAPTURE_PAID -> {
                    ProtectedBuildArea target = null;
                    for (var entity : mc.level.entitiesForRendering())
                        if (jobId.equals(entity.getUUID()) && entity instanceof ProtectedBuildArea p) target = p;
                    require(target != null, "Paid native marker did not synchronize to client");
                    aim(mc, target.position().add(0, .8, 0));
                    NativeBuildingQa.captureGameplay("15-production-paid-native-marker.png");
                    return false;
                }
                case CANCEL -> RaidNetwork.protectedConstructionAction(jobId, ProtectedConstructionActions.CANCEL);
                case SHOW -> RaidNetwork.protectedConstructionAction(jobId, ProtectedConstructionActions.SHOW);
                case RELOAD -> {
                    mc.level.disconnect(); mc.clearLevel(); mc.setScreen(new TitleScreen()); clientStage = 4;
                    return false;
                }
                case DONE -> { done = true; return true; }
                default -> { }
            }
        }
        CompletableFuture<Action> next = new CompletableFuture<>(); pending = next;
        mc.getSingleplayerServer().execute(() -> {
            try {
                var server = mc.getSingleplayerServer();
                next.complete(step(server.overworld(), server.getPlayerList().getPlayer(playerId)));
            } catch (Throwable failure) { next.completeExceptionally(failure); }
        });
        return false;
    }

    static Map<String, Object> result() {
        var result = new LinkedHashMap<>(RESULT);
        result.put("status", done ? "passed" : "incomplete"); result.put("stage", stage);
        result.put("assertions", List.copyOf(CHECKS));
        result.put("scope", "Fresh cheats-off integrated world; real survival player, real Recruits claims, production plan item packets and native worker AI. Fixture-only terrain/faction/core/stock setup.");
        result.put("notCovered", List.of("Dedicated network server", "All shader/resource-pack combinations", "Offline owner with a separately connected second player", "Scan-work upper-bound performance", "Malicious custom client fuzzing", "Player same-state edits and late-block obstruction integration", "1024/1025 exact rendered geometry", "Competing active builder integration"));
        return result;
    }

    private static Action step(ServerLevel level, ServerPlayer owner) throws Exception {
        require(owner != null, "Real gameplay player unavailable");
        long now = level.getGameTime();
        if (stageSince < 0) stageSince = now;
        require(now - stageSince < 2400, "Gameplay stage " + stage + " timed out: " + diagnostics(level));
        if (now < resumeAt) return Action.NONE;
        switch (stage) {
            case 0 -> {
                require(!owner.isCreative() && !owner.hasPermissions(2) && owner.mayBuild(), "Payment actor is not real non-op survival");
                fixture = NativeGameplayFixture.setup(level, owner);
                require(SiegeCore.point(owner.server, SiegeCore.key(owner)) != null, "Actual core/claim/anchor unavailable");
                builder(level).setNoAi(true); // Transaction-only perimeter; wall AI below is enabled.
                FactionBank.credit(core(owner), 2000); RaidSavedData.get(owner.server).setDirty();
                require(PerimeterConstruction.review(owner, fixture.corePos(), 1), "Real perimeter review rejected");
                require(balance(owner) == 2000 && protectedAreas(level) == 0, "Free perimeter review changed money/jobs");
                select(owner, ModItems.PERIMETER_PLAN.get());
                check("Real non-op survival actor, indexed native faction/claim and production core placement");
                advance(now, 1, 20);
            }
            case 1 -> {
                var selection = PerimeterPreview.read(owner.getMainHandItem(), owner.getUUID(), level.dimension().location(), now);
                require(selection != null && selection.ready(), "Real perimeter review is blocked: " + (selection == null ? "missing selection" : selection.problem()));
                advance(now, 100, 20); return Action.CAPTURE_REVIEW;
            }
            case 100 -> { advance(now, 2, 25); return Action.USE_AIR; }
            case 2 -> {
                require(balance(owner) == 1100, "Perimeter did not debit exactly 900 Treasury emeralds");
                jobId = linkedJob(level);
                var area = area(level);
                require(NativeConstructionGuard.commissionPaid(area), "Perimeter was not activated after payment");
                require(!area.getAlwaysShowProjection(), "Large perimeter default should be focus-only");
                require(owner.getMainHandItem().isEmpty(), "Confirmed perimeter plan was not consumed");
                check("Real perimeter plan packet commissions once, consumes plan and debits exactly 900 Treasury");
                advance(now, 101, 20); return Action.CAPTURE_PAID;
            }
            case 101 -> { advance(now, 3, 20); return Action.SHOW; }
            case 3 -> {
                require(area(level).getAlwaysShowProjection(), "Authenticated explicit large-plan projection control failed");
                check("Authenticated owner projection action overrides large-plan focus-only default");
                advance(now, 4, 20); return Action.CANCEL;
            }
            case 4 -> {
                require(level.getEntity(jobId) == null && !NativeConstructionGuard.hasReservation(level, jobId),
                        "Real perimeter cancellation did not retire marker/reservation");
                require(balance(owner) == 1100 && builder(level).currentBuildArea == null,
                        "Perimeter cancellation refunded or left builder attached");
                check("Real perimeter cancellation retires reservation/builder without refund");
                FactionBank.debit(core(owner), 1100); RaidSavedData.get(owner.server).setDirty();
                require(DefenseStructures.givePlan(owner, DefenseBlueprint.Kind.WALL.ordinal()), "Free manual plan delivery failed");
                select(owner, ModItems.defensePlan(DefenseBlueprint.Kind.WALL).get());
                owner.teleportTo(fixture.wallAnchor().getX() + .5, 65, fixture.wallAnchor().getZ() - 3.5);
                advance(now, 5, 20); return Action.AIM_WALL;
            }
            case 5 -> {
                require(owner.getDirection() == Direction.SOUTH, "Actual movement packets did not establish manual-plan facing");
                advance(now, 6, 20); return Action.USE_BLOCK;
            }
            case 6 -> {
                require(balance(owner) == 0 && protectedAreas(level) == 0 && owner.getMainHandItem().getItem() instanceof DefensePlanItem,
                        "Free manual preview changed Treasury, plan or job count");
                require(DefensePreview.read(owner.getMainHandItem(), level.dimension().location(), owner.getUUID(), now) != null,
                        "Actual plan use did not create an owner-bound preview");
                advance(now, 7, 20); return Action.USE_BLOCK;
            }
            case 7 -> {
                require(balance(owner) == 0 && protectedAreas(level) == 0 && owner.getMainHandItem().getItem() instanceof DefensePlanItem,
                        "Insufficient Treasury failure spent money, consumed plan or leaked a job");
                require(!ConstructionEditLedger.get(level).reserves(plannedPositions()), "Failed commission leaked reservation");
                check("Actual free manual preview and insufficient-Treasury confirmation preserve funds, plan and reservations");
                FactionBank.credit(core(owner), 1000); RaidSavedData.get(owner.server).setDirty();
                builder(level).setNoAi(false);
                advance(now, 8, 20); return Action.USE_BLOCK;
            }
            case 8 -> {
                require(balance(owner) == 910, "Manual commissioning did not debit exactly 90 Treasury emeralds");
                bankAfterManual = balance(owner); jobId = linkedJob(level);
                require(NativeConstructionGuard.commissionPaid(area(level)) && owner.getMainHandItem().isEmpty(),
                        "Manual job was not paid/activated or plan remains");
                require(!builder(level).isNoAi(), "Native builder AI is disabled");
                check("Actual manual plan confirmation consumes one plan and charges exactly 90 Treasury");
                advance(now, 9, 20); return Action.USE_BLOCK;
            }
            case 9 -> {
                require(balance(owner) == bankAfterManual && protectedAreas(level) == 1,
                        "Repeated actual use duplicated payment or commissioned job");
                check("Repeated post-confirmation use cannot duplicate payment or job");
                lastPlaced = placed(level); lastChange = now; advance(now, 10, 0);
            }
            case 10 -> {
                long count = placed(level);
                if (count != lastPlaced) { lastPlaced = count; lastChange = now; }
                if (count <= 0 || now - lastChange < 100 || builder(level).neededItems.isEmpty()) return Action.NONE;
                require(count < fixture.expectedPlan().blocks().size(), "Finite initial stock unexpectedly completed wall");
                conservation(level);
                check("Actual native builder places from finite chest stock, then stalls with material request");
                RESULT.put("initialStockPlacedBlocks", count);
                replenish(level, 32, 32); lastPlaced = count; advance(now, 11, 0);
            }
            case 11 -> {
                if (placed(level) <= lastPlaced) return Action.NONE;
                check("Actual native resupply resumes placement without another Treasury debit");
                claim = ClaimEvents.recruitsClaimManager.getClaim(fixture.claimId());
                require(claim != null, "Real claim vanished before permission case");
                claimChunks = List.copyOf(claim.getClaimedChunks());
                for (ChunkPos chunk : claimChunks) claim.removeChunk(chunk);
                ClaimEvents.recruitsClaimManager.addOrUpdateClaim(level, claim);
                advance(now, 12, 20);
            }
            case 12 -> {
                require(NativeConstructionGuard.status(area(level)).contains("Core")
                                || NativeConstructionGuard.status(area(level)).contains("claim"),
                        "Native guard did not expose claim/core pause: " + diagnostics(level));
                pausedCells = snapshot(level); advance(now, 13, 80);
            }
            case 13 -> {
                require(snapshot(level).equals(pausedCells) && balance(owner) == bankAfterManual,
                        "Native world or Treasury mutated while claim permission was absent");
                check("Actual native claim removal pauses world mutations and preserves payment");
                for (ChunkPos chunk : claimChunks) claim.addChunk(chunk);
                ClaimEvents.recruitsClaimManager.addOrUpdateClaim(level, claim); ClaimEvents.recruitsClaimManager.save(level);
                lastPlaced = placed(level); advance(now, 14, 0);
            }
            case 14 -> {
                if (placed(level) <= lastPlaced) return Action.NONE;
                require(balance(owner) == bankAfterManual, "Claim restore charged again");
                owner.setGameMode(GameType.SPECTATOR); advance(now, 15, 20);
            }
            case 15 -> {
                require(NativeConstructionGuard.status(area(level)).contains("permission"), "Owner permission pause missing");
                pausedCells = snapshot(level); ledgerGeneration = ConstructionEditLedger.get(level).generation();
                advance(now, 16, 80);
            }
            case 16 -> {
                require(snapshot(level).equals(pausedCells), "Native mutation continued during owner permission pause");
                owner.server.saveEverything(false, true, true);
                check("Owner permission loss pauses actual AI without changing world cells");
                advance(now, 17, 0); return Action.RELOAD;
            }
            case 17 -> {
                require(snapshot(level).equals(pausedCells) && balance(owner) == bankAfterManual,
                        "Mid-job world restart changed protected cells or Treasury");
                require(NativeConstructionGuard.commissionPaid(area(level))
                                && ConstructionEditLedger.get(level).sameGeneration(ledgerGeneration)
                                && NativeConstructionGuard.hasReservation(level, jobId), "Restart lost paid job or durable reservation");
                check("Mid-job real world restart preserves exact cells, paid state and durable ledger identity");
                owner.setGameMode(GameType.SURVIVAL); replenish(level, 128, 128); advance(now, 18, 0);
            }
            case 18 -> {
                if (placed(level) < fixture.expectedPlan().blocks().size()) return Action.NONE;
                conservation(level);
                require(balance(owner) == bankAfterManual, "Completion charged again");
                check("Native AI completes the exact manual template after resupply/restart with material conservation");
                RESULT.put("completedManualBlocks", placed(level));
                RESULT.put("manualTreasuryDebit", 90); RESULT.put("perimeterTreasuryDebit", 900);
                advance(now, 19, 30);
            }
            case 19 -> {
                require(level.getEntity(jobId) == null || ((ProtectedBuildArea) level.getEntity(jobId)).isDone(),
                        "Native completion did not retire or complete its marker");
                RESULT.put("status", "passed"); return Action.DONE;
            }
            default -> throw new AssertionError("Unexpected gameplay stage " + stage);
        }
        return Action.NONE;
    }

    private static void aim(Minecraft mc, Vec3 target) {
        Vec3 delta = target.subtract(mc.player.getEyePosition());
        float yaw = (float) (Math.toDegrees(Math.atan2(delta.z, delta.x)) - 90);
        float pitch = (float) -Math.toDegrees(Math.atan2(delta.y, Math.sqrt(delta.x * delta.x + delta.z * delta.z)));
        mc.player.setYRot(yaw); mc.player.setXRot(pitch); mc.player.yRotO = yaw; mc.player.xRotO = pitch;
    }
    private static void select(ServerPlayer owner, Item item) {
        int slot = -1;
        for (int i = 0; i < owner.getInventory().getContainerSize(); i++)
            if (owner.getInventory().getItem(i).is(item)) { slot = i; break; }
        require(slot >= 0, "Production plan not found in actual inventory");
        if (slot >= 9) {
            ItemStack held = owner.getInventory().getItem(8);
            owner.getInventory().setItem(8, owner.getInventory().getItem(slot)); owner.getInventory().setItem(slot, held); slot = 8;
        }
        owner.getInventory().selected = slot; owner.inventoryMenu.broadcastChanges();
        owner.connection.send(new ClientboundSetCarriedItemPacket(slot));
    }
    private static CompoundTag core(ServerPlayer owner) {
        var tag = RaidSavedData.get(owner.server).siegeCores.get(SiegeCore.key(owner));
        require(tag != null, "No real core Treasury"); return tag;
    }
    private static long balance(ServerPlayer owner) { return FactionBank.balance(core(owner)); }
    private static BuilderEntity builder(ServerLevel level) {
        var entity = level.getEntity(fixture.builderId()); require(entity instanceof BuilderEntity, "Native gameplay builder unavailable");
        return (BuilderEntity) entity;
    }
    private static UUID linkedJob(ServerLevel level) {
        var tag = builder(level).getPersistentData();
        require(tag.hasUUID(ModConstants.Tags.PLAYER_FORTIFICATION_AREA_ID), "Production builder/job link missing");
        return tag.getUUID(ModConstants.Tags.PLAYER_FORTIFICATION_AREA_ID);
    }
    private static ProtectedBuildArea area(ServerLevel level) {
        var entity = level.getEntity(jobId); require(entity instanceof ProtectedBuildArea, "Commissioned native marker unavailable");
        return (ProtectedBuildArea) entity;
    }
    private static int protectedAreas(ServerLevel level) {
        return level.getEntitiesOfClass(ProtectedBuildArea.class, new AABB(120, 60, -8, 168, 80, 40), e -> e.isAlive()).size();
    }
    private static Set<BlockPos> plannedPositions() {
        Set<BlockPos> positions = new HashSet<>(); fixture.expectedPlan().blocks().keySet().forEach(p -> positions.add(BlockPos.of(p))); return positions;
    }
    private static Map<Long, BlockState> snapshot(ServerLevel level) {
        Map<Long, BlockState> snapshot = new HashMap<>();
        fixture.expectedPlan().blocks().keySet().forEach(p -> snapshot.put(p, level.getBlockState(BlockPos.of(p)))); return Map.copyOf(snapshot);
    }
    private static long placed(ServerLevel level) {
        return fixture.expectedPlan().blocks().entrySet().stream().filter(entry -> {
            var block = ForgeRegistries.BLOCKS.getValue(new net.minecraft.resources.ResourceLocation(entry.getValue()));
            return block != null && level.getBlockState(BlockPos.of(entry.getKey())).is(block);
        }).count();
    }
    private static void replenish(ServerLevel level, int cobble, int oak) {
        NativeGameplayFixture.replenishContainer(level, cobble, oak);
        suppliedCobble += cobble; suppliedOak += oak;
    }
    private static int count(Container inventory, Item item) {
        int total = 0; for (int i = 0; i < inventory.getContainerSize(); i++) if (inventory.getItem(i).is(item)) total += inventory.getItem(i).getCount();
        return total;
    }
    private static void conservation(ServerLevel level) {
        var be = level.getBlockEntity(fixture.chestPos()); require(be instanceof Container, "Real stock chest is missing");
        Container chest = (Container) be;
        for (Item item : List.of(Items.COBBLESTONE, Items.OAK_PLANKS)) {
            int supplied = item == Items.COBBLESTONE ? suppliedCobble : suppliedOak;
            long built = fixture.expectedPlan().blocks().keySet().stream()
                    .filter(p -> level.getBlockState(BlockPos.of(p)).getBlock().asItem() == item).count();
            long accounted = built + count(chest, item) + count(builder(level).getInventory(), item);
            require(accounted == supplied, "Native material conservation failed for " + item + ": supplied=" + supplied + ", accounted=" + accounted);
        }
    }
    private static String diagnostics(ServerLevel level) {
        if (fixture == null) return "fixture not initialized";
        var entity = level.getEntity(fixture.builderId());
        if (!(entity instanceof BuilderEntity builder)) return "builder absent";
        String pause = jobId != null && level.getEntity(jobId) instanceof ProtectedBuildArea area ? NativeConstructionGuard.status(area) : "no marker";
        String requested = String.join(", ", com.devfarinsky.siegeoverhaul.compat.WorkersConstructionView.requests(builder));
        return "placed=" + placed(level) + ", follow=" + builder.getFollowState() + ", requests=" + requested
                + ", sleeping=" + builder.needsToSleep() + ", noAi=" + builder.isNoAi()
                + ", navigationDone=" + builder.getNavigation().isDone()
                + ", nativeRemaining=" + (builder.currentBuildArea == null ? -1 : builder.currentBuildArea.stackToPlace.size())
                + ", pause=" + pause;
    }
    private static void advance(long now, int next, int delay) { stage = next; stageSince = now; resumeAt = now + delay; }
    private static void check(String text) { CHECKS.add(text); }
    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
