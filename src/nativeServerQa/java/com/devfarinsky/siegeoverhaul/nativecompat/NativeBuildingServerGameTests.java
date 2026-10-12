package com.devfarinsky.siegeoverhaul.nativecompat;

import com.devfarinsky.siegeoverhaul.SiegeOverhaul;
import com.devfarinsky.siegeoverhaul.compat.WorkersBridge;
import com.google.gson.GsonBuilder;
import com.mojang.authlib.GameProfile;
import com.talhanation.workers.entities.BuilderEntity;
import com.talhanation.workers.network.MessageUpdateBuildArea;
import com.talhanation.workers.network.MessageUpdateOwner;
import com.talhanation.workers.network.MessageUpdateWorkArea;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestServer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.gametest.ForgeGameTestHooks;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Separate server-only source set. No client fixtures, client imports, mock mods or socket login. */
@GameTestHolder(SiegeOverhaul.MOD_ID)
@PrefixGameTestTemplate(false)
public final class NativeBuildingServerGameTests {
    public static final String LIMITATION = "Physical dedicated distribution in Forge GameTestServer with real companion mods. "
            + "FakePlayer actors test direct server APIs only; real multiplayer connection/authentication remains unverified. "
            + "Inventory authority policy cases inject an isolated real profile cache because GameTestServer supplies none. "
            + "Fixture initialization is not production commissioning, native AI construction, rendering, or a full server restart.";
    private static final List<String> CHECKS = new ArrayList<>();
    private static final Map<String, Object> REPORT = new LinkedHashMap<>();
    private static Path evidence;
    private static int executions;

    public NativeBuildingServerGameTests() {}

    @GameTest(template = "native_server_empty", timeoutTicks = 200, required = true)
    public static void protected_native_server_contracts(GameTestHelper helper) {
        try {
            require(++executions == 1, "Required server contract test executed more than once");
            initializeEvidence(helper.getLevel());
            verifyPhysicalServer(helper.getLevel());
            verifyNativeContracts(helper);
            REPORT.put("status", "passed");
            writeEvidence();
            helper.succeed();
            System.out.println("SIEGE_NATIVE_SERVER_QA_COMPLETED tests=1 checks=" + CHECKS.size());
        } catch (Throwable failure) {
            REPORT.put("status", "failed");
            REPORT.put("failure", failure.toString());
            try { writeEvidence(); } catch (Exception reportingFailure) { failure.addSuppressed(reportingFailure); }
            // The required GameTest, rather than a custom success exit, owns the process status.
            throw new IllegalStateException("Required native server contracts failed", failure);
        }
    }

    private static void initializeEvidence(ServerLevel level) throws Exception {
        require(Boolean.getBoolean("siegeoverhaul.nativeServerQa"), "Server QA must be explicitly opted in");
        Path directory = Path.of(System.getProperty("siegeoverhaul.nativeServerQa.directory")).toRealPath();
        require(directory.equals(Path.of("").toRealPath()), "Unexpected server working directory");
        require(directory.endsWith(Path.of("build", "native-server-qa", "server")), "Unsafe server directory");
        require(level.getServer().getWorldPath(LevelResource.ROOT).toRealPath()
                .equals(directory.resolve("worlds/siege-native-server-fixture").toRealPath()), "Unsafe fixture world");
        require(!Files.exists(directory.resolve("eula.txt")), "GameTest CI must not create or reuse eula.txt");
        evidence = directory.resolveSibling("evidence");
        Files.createDirectories(evidence);
        require(!Files.exists(evidence.resolve("result.json")), "Refusing to overwrite server evidence");
        REPORT.put("startedUtc", Instant.now().toString());
        REPORT.put("limitation", LIMITATION);
        REPORT.put("fixtureActor", "net.minecraftforge.common.util.FakePlayer; no authenticated network client");
        REPORT.put("worldReloadCoverage", "Real entity NBT round trip and actual SavedData disk flush/read; no process restart");
    }

    private static void verifyPhysicalServer(ServerLevel level) throws Exception {
        require(FMLEnvironment.dist == Dist.DEDICATED_SERVER, "Wrong physical distribution");
        require(ForgeGameTestHooks.isGametestServer(), "Not the supported Forge GameTest server mode");
        require(level.getServer() instanceof GameTestServer && !level.isClientSide && level.getServer().isSameThread(),
                "Tests must run on the GameTestServer level thread");
        require(level.getServer().getPlayerList().getPlayerCount() == 0, "Unexpected connected player");
        REPORT.put("physicalDistribution", FMLEnvironment.dist.name());
        REPORT.put("serverClass", level.getServer().getClass().getName());
        REPORT.put("logicalSide", "SERVER");
        REPORT.put("connectedPlayers", 0);
        Map<String, String> versions = new LinkedHashMap<>();
        Map<String, Object> artifacts = new LinkedHashMap<>();
        for (String id : List.of("minecraft", "forge", "siegeoverhaul", "workers", "recruits", "smallships", "siegeweapons")) {
            var info = ModList.get().getModContainerById(id).orElseThrow(() ->
                    new IllegalStateException("Missing actual runtime mod: " + id)).getModInfo();
            versions.put(id, info.getVersion().toString());
            if (Set.of("workers", "recruits", "smallships", "siegeweapons").contains(id)) {
                Path jar = info.getOwningFile().getFile().getFilePath();
                require(Files.isRegularFile(jar), "Actual companion JAR is unavailable: " + id);
                var digest = MessageDigest.getInstance("SHA-256");
                try (var input = Files.newInputStream(jar)) {
                    byte[] buffer = new byte[65536];
                    for (int read; (read = input.read(buffer)) >= 0;) if (read > 0) digest.update(buffer, 0, read);
                }
                artifacts.put(id, Map.of("fileName", jar.getFileName().toString(), "sha256",
                        java.util.HexFormat.of().formatHex(digest.digest()),
                        "kind", "ForgeGradle remapped development runtime JAR, not original release bytes"));
            }
        }
        require("1.20.1".equals(versions.get("minecraft")) && "47.4.16".equals(versions.get("forge")), "Unexpected Minecraft/Forge");
        require("2.0.3".equals(versions.get("workers")) && "1.15.2".equals(versions.get("recruits")), "Unexpected native versions");
        require(WorkersConstructionRuntime.problem() == null, "Native runtime capability fence failed");
        // Reflection resolves all declared signatures on the actual stripped production entity.
        // A leaked client Screen return type on this path must fail on the dedicated distribution.
        for (var method : ProtectedBuildArea.class.getDeclaredMethods())
            require(!method.getReturnType().getName().startsWith("net.minecraft.client."), "Client return type survived server stripping");
        REPORT.put("loadedModVersions", versions);
        REPORT.put("loadedCompanionArtifacts", artifacts);
        REPORT.put("nativeApiClasses", List.of(ProtectedBuildArea.class.getName(), BuilderEntity.class.getName(),
                com.talhanation.workers.entities.workarea.BuildArea.class.getName(), ProtectedConstructionActions.class.getName()));
        check("physical-dedicated-classloading");
    }

    private static void verifyNativeContracts(GameTestHelper helper) throws Exception {
        ServerLevel level = helper.getLevel();
        BlockPos origin = helper.absolutePos(new BlockPos(10, 1, 8));
        BlockPos marker = helper.absolutePos(new BlockPos(2, 1, 2));
        // Bounded fixture terrain inside the source-generated 16 x 8 x 16 scene.
        for (int x = 0; x < 16; x++) for (int z = 0; z < 16; z++) {
            helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
            for (int y = 1; y < 8; y++) helper.setBlock(new BlockPos(x, y, z), Blocks.AIR);
        }
        FakePlayer owner = actor(level, "SiegeServerOwner", "853b93e4-bb07-4ca7-a5f2-ffb39167e162", marker);
        FakePlayer outsider = actor(level, "SiegeServerOther", "137cebb2-6932-4663-8777-021856d8603e", marker);
        var type = ForgeRegistries.ENTITY_TYPES.getValue(new ResourceLocation("workers", "builder"));
        require(type != null, "Real native builder factory missing");
        Entity rawBuilder = type.create(level);
        require(rawBuilder instanceof BuilderEntity, "Real native builder factory returned unsupported entity");
        BuilderEntity builder = (BuilderEntity) rawBuilder;
        builder.moveTo(Vec3.atBottomCenterOf(marker.offset(2, 0, 0)));
        builder.setNoAi(true); builder.setPersistenceRequired();
        WorkersBridge.enablePlayerJob(builder, owner.getUUID());
        builder.getInventory().setItem(6, new ItemStack(Items.COBBLESTONE, 17));
        require(level.addFreshEntity(builder), "Could not publish native builder fixture");
        CompoundTag blueprint = blueprint();
        ProtectedBuildArea area = newArea(level, owner, builder, origin, marker, blueprint);
        area.initializeBlueprint(blueprint);
        area.initializeProjection(true);
        require(level.addFreshEntity(area), "Could not publish protected native entity");
        require(area.nativeQueuesReady() && !area.stackToPlace.isEmpty(), "Real noncreative native queue initialization failed");
        Vec3 position = area.position();
        var plan = AcceptedConstructionPlan.capture(area);
        require(area.getArea().equals(new AABB(origin, origin.south(2).west(2).above(2))), "Native construction envelope shifted");
        require(!BlockPos.containing(position).equals(origin) && area.getBoundingBox().contains(position.add(0, .5, 0)),
                "Marker position/pick box is not independent of native origin");
        require(!area.canWorkHere(builder), "Unpaid area became discoverable");
        check("real-native-factories-and-queues");

        CompoundTag malicious = blueprint.copy();
        malicious.getList("blocks", 10).getCompound(0).put("state", NbtUtils.writeBlockState(Blocks.TNT.defaultBlockState()));
        ListTag entities = new ListTag(); CompoundTag pig = new CompoundTag();
        pig.putString("entity_type", "minecraft:pig"); entities.add(pig); malicious.put("entities", entities);
        int pigs = level.getEntitiesOfClass(net.minecraft.world.entity.animal.Pig.class, new AABB(origin).inflate(8)).size();
        new MessageUpdateBuildArea(area.getUUID(), 30, 30, 30, malicious, true, true, true).update(area);
        area.setStartBuild(true); area.setFreeArea(true); area.setFacing(Direction.WEST); area.setDone(true);
        var ownership = new MessageUpdateOwner(); ownership.playerUUID = outsider.getUUID(); ownership.playerName = "Other";
        ownership.updateWorkArea(area); area.setTeamAccess(true); area.setTeamStringID("foreign");
        var deletion = new MessageUpdateWorkArea(); deletion.destroy = true; deletion.updateWorkArea(area, owner);
        area.moveTo(100, 100, 100); area.moveTo(200, 200, 200, 90, 30);
        area.moveTo(new Vec3(300, 300, 300)); area.moveTo(new BlockPos(400, 400, 400), 180, 20); area.setPos(500, 500, 500);
        require(area.position().equals(position) && area.getOriginPos().equals(origin) && plan.matches(area), "Native control changed sealed plan/position");
        require(owner.getUUID().equals(area.getPlayerUUID()) && !area.getTeamAccess() && area.getTeamStringID().isEmpty()
                && !area.getFreeArea() && !area.isDone() && !area.isRemoved(), "Native ownership/delete/completion bypass");
        for (BlockPos cell : plan.cells.keySet()) require(level.getBlockState(cell).isAir(), "Native creative update wrote a block");
        require(level.getEntitiesOfClass(net.minecraft.world.entity.animal.Pig.class, new AABB(origin).inflate(8)).size() == pigs,
                "Native creative update spawned an entity");
        check("sealed-native-packets-and-move-overloads");

        ProtectedBuildArea scratch = newArea(level, owner, builder, origin, marker, blueprint);
        blueprint.getList("blocks", 10).getCompound(0).put("state", NbtUtils.writeBlockState(Blocks.TNT.defaultBlockState()));
        require(scratch.getStructureNBT().equals(area.getStructureNBT()), "Initializer retained caller's nested mutable NBT");
        scratch.initializeBlueprint(scratch.getStructureNBT()); scratch.stackToPlace.clear(); scratch.setStartBuild(false);
        require(scratch.stackToPlace.isEmpty(), "Native restart rebuilt accepted queues");
        CompoundTag alias = scratch.getStructureNBT(); alias.putInt("width", 900);
        require(scratch.getStructureNBT().equals(area.getStructureNBT()), "Getter exposed mutable NBT");
        expectFailure(() -> scratch.initializeBlueprint(alias), "Changed blueprint accepted");
        CompoundTag saved = scratch.saveWithoutId(new CompoundTag());
        ProtectedBuildArea decoded = ProtectedConstructionAreas.TYPE.get().create(level);
        require(decoded != null, "Decode factory missing"); decoded.load(saved);
        require(decoded.getOriginPos().equals(origin) && decoded.position().equals(scratch.position())
                && decoded.getArea().equals(area.getArea()) && builder.getUUID().equals(decoded.reservedBuilderId())
                && AcceptedConstructionPlan.capture(decoded).cells.equals(plan.cells), "Native entity NBT round trip changed contract");
        decoded.rebuildAcceptedQueues(); require(decoded.nativeQueuesReady(), "Loaded native queue reconstruction failed");
        CompoundTag malformed = saved.copy(); malformed.remove("SiegeNativeSealed");
        ProtectedBuildArea invalid = ProtectedConstructionAreas.TYPE.get().create(level);
        require(invalid != null, "Malformed-load factory missing"); invalid.load(malformed);
        expectFailure(invalid::rebuildAcceptedQueues, "Malformed entity became editable");
        invalid.setStartBuild(true); require(!invalid.nativeQueuesReady(), "Malformed entity got native queues");
        check("native-nbt-roundtrip-and-defensive-copies");

        BlockPos distant = new BlockPos(20_000_000, 65, 20_000_000);
        require(!level.hasChunkAt(distant), "Unloaded probe unexpectedly loaded");
        ProtectedBuildArea unloaded = newArea(level, owner, builder, distant, marker, area.getStructureNBT());
        expectFailure(unloaded::rebuildAcceptedQueues, "Unloaded blueprint reconstructed queues");
        require(!unloaded.nativeQueuesReady() && !level.hasChunkAt(distant), "Queue recovery force-loaded chunk");
        check("unloaded-blueprint-remains-unloaded");

        Set<BlockPos> reserved = new LinkedHashSet<>(plan.cells.keySet());
        BlockPos headroom = origin.above(2); reserved.add(headroom);
        var reservation = AcceptedConstructionReservation.capture(level, plan, reserved);
        var reloadedReservation = AcceptedConstructionReservation.load(plan, reservation.save());
        require(reloadedReservation.cells.equals(reserved) && reloadedReservation.problem(level) == null,
                "Reservation round trip lost clearance");
        level.setBlock(headroom, Blocks.STONE.defaultBlockState(), 3);
        require(reloadedReservation.problem(level) != null, "Real-world clearance obstruction was accepted");
        level.setBlock(headroom, Blocks.AIR.defaultBlockState(), 3);
        var ledger = ConstructionEditLedger.get(level);
        require(ledger.register(area.getUUID(), reserved), "Actual SavedData ledger registration failed");
        require(ledger.reserves(Set.of(headroom)), "Ledger omitted reserved headroom");
        ledger.record(headroom); require(ledger.edited(area.getUUID()), "Headroom edit history missing");
        level.getDataStorage().save();
        Path ledgerFile = level.getServer().getWorldPath(LevelResource.ROOT).resolve("data/siege_construction_edits.dat");
        require(Files.isRegularFile(ledgerFile), "SavedData ledger did not reach the isolated world disk");
        var diskLedger = ConstructionEditLedger.load(NbtIo.readCompressed(ledgerFile.toFile()).getCompound("data"));
        require(diskLedger.sameGeneration(ledger.generation()) && diskLedger.matches(area.getUUID(), reserved)
                && diskLedger.edited(area.getUUID()), "Persisted SavedData lost reservation/history/generation");
        diskLedger.retire(area.getUUID(), false);
        var retired = ConstructionEditLedger.load(diskLedger.save(new CompoundTag()));
        require(retired.retired(area.getUUID()) && !retired.contains(area.getUUID()), "Retirement receipt lost on reload");
        require(!ConstructionEditLedger.load(new CompoundTag()).canRetire(area.getUUID()), "Missing ledger was treated as cancellation");
        check("clearance-and-world-saveddata-disk-roundtrip");

        int stock = builder.getInventory().countItem(Items.COBBLESTONE);
        builder.currentBuildArea = area;
        area.getPersistentData().putBoolean("SiegeConstructionCommissionPaid", true); // Explicit contract fixture only.
        ProtectedConstructionActions.handle(outsider, area.getUUID(), ProtectedConstructionActions.HIDE);
        ProtectedConstructionActions.handle(outsider, area.getUUID(), ProtectedConstructionActions.CANCEL);
        require(area.getAlwaysShowProjection() && !area.isRemoved() && builder.currentBuildArea == area, "Outsider changed owner job");
        owner.setPos(position.x + 32, position.y, position.z);
        ProtectedConstructionActions.handle(owner, area.getUUID(), ProtectedConstructionActions.HIDE);
        require(area.getAlwaysShowProjection(), "Out-of-range owner changed projection");
        owner.setPos(position.x, position.y, position.z); owner.setGameMode(GameType.SPECTATOR);
        ProtectedConstructionActions.handle(owner, area.getUUID(), ProtectedConstructionActions.CANCEL);
        require(!area.isRemoved(), "Spectator canceled owner job");
        owner.setGameMode(GameType.SURVIVAL);
        ProtectedConstructionActions.handle(owner, area.getUUID(), ProtectedConstructionActions.HIDE);
        require(!area.getAlwaysShowProjection(), "Nearby fixture owner could not hide projection");
        ProtectedConstructionActions.handle(owner, area.getUUID(), ProtectedConstructionActions.SHOW);
        require(area.getAlwaysShowProjection(), "Nearby fixture owner could not show projection");
        require(!area.abortBeforePayment(), "Paid area accepted unpaid rollback");
        ProtectedConstructionActions.handle(owner, area.getUUID(), ProtectedConstructionActions.CANCEL);
        require(area.isRemoved() && builder.currentBuildArea == null && !ledger.contains(area.getUUID())
                && !ledger.retired(area.getUUID()), "Authorized cancellation did not retire exact loaded job");
        require(builder.getInventory().countItem(Items.COBBLESTONE) == stock
                && owner.getUUID().equals(WorkersBridge.readWorkerOwner(builder)), "Cancellation changed stock or worker ownership");
        check("fakeplayer-direct-handler-authorization-and-cancel");
        Map<String, Object> authorityReport = new LinkedHashMap<>();
        REPORT.put("inventoryAuthority", authorityReport);
        NativeInventoryAuthorityServerContracts.verify(helper, owner, outsider, builder, authorityReport);
        check("offline-native-inventory-authority-and-loss-recovery");
        builder.discard();
        REPORT.put("builderRecovery", com.devfarinsky.siegeoverhaul.core.NativeBuilderRecoveryServerContracts
                .verify(level, helper.absolutePos(new BlockPos(8, 1, 8))));
        check("native-builder-recovery-vegetation-safety");
    }

    private static ProtectedBuildArea newArea(ServerLevel level, FakePlayer owner, BuilderEntity builder,
                                              BlockPos origin, BlockPos marker, CompoundTag blueprint) {
        ProtectedBuildArea area = ProtectedConstructionAreas.TYPE.get().create(level);
        require(area != null, "Production protected entity factory unavailable");
        area.initialize(origin, marker, owner.getUUID(), owner.getGameProfile().getName(), builder.getUUID(), 3, 3, 2, blueprint);
        return area;
    }

    private static FakePlayer actor(ServerLevel level, String name, String id, BlockPos position) {
        FakePlayer player = FakePlayerFactory.get(level, new GameProfile(UUID.fromString(id), name));
        player.setGameMode(GameType.SURVIVAL);
        player.setPos(position.getX() + .5, position.getY(), position.getZ() + .5);
        require(player.isAlive() && !player.isCreative() && !player.isSpectator(), "Invalid FakePlayer fixture actor");
        return player;
    }

    private static CompoundTag blueprint() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("width", 3); tag.putInt("depth", 3); tag.putInt("height", 2); tag.putString("facing", "south");
        ListTag cells = new ListTag();
        for (int z = 0; z < 3; z++) for (int y = 0; y < 2; y++) {
            CompoundTag cell = new CompoundTag();
            cell.putInt("x", 0); cell.putInt("y", y); cell.putInt("z", z);
            cell.put("state", NbtUtils.writeBlockState(Blocks.COBBLESTONE.defaultBlockState())); cells.add(cell);
        }
        tag.put("blocks", cells);
        return tag;
    }

    private static void writeEvidence() throws Exception {
        if (evidence == null) return;
        REPORT.put("testsExecuted", executions);
        REPORT.put("checks", List.copyOf(CHECKS));
        REPORT.put("finishedUtc", Instant.now().toString());
        Files.writeString(evidence.resolve("result.json"), new GsonBuilder().setPrettyPrinting().create().toJson(REPORT));
    }
    private static void check(String id) { CHECKS.add(id); System.out.println("SIEGE_NATIVE_SERVER_QA_CHECK " + id); }
    private static void require(boolean value, String message) { if (!value) throw new IllegalStateException(message); }
    private static void expectFailure(Runnable action, String message) {
        try { action.run(); } catch (IllegalStateException expected) { return; }
        throw new IllegalStateException(message);
    }
}
