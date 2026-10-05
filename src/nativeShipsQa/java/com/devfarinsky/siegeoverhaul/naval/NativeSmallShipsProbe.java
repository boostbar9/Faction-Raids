package com.devfarinsky.siegeoverhaul.naval;

import com.devfarinsky.siegeoverhaul.ModConstants;
import com.google.gson.GsonBuilder;
import com.talhanation.recruits.Main;
import com.talhanation.recruits.compat.smallships.SmallShips;
import com.talhanation.recruits.entities.CaptainEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.registries.ForgeRegistries;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Actual native entities and unchanged native defaults in a fresh disposable QA world. */
public final class NativeSmallShipsProbe {
    private final ServerLevel level;
    private final BlockPos origin;
    private final Map<String, Object> report = new LinkedHashMap<>();
    private final List<Entity> created = new ArrayList<>();
    private final List<Case> cases = new ArrayList<>();
    private Path evidence;
    private boolean finalRelease;
    private long preparedAt;

    private record Case(Boat boat, Mob recruit, CaptainEntity captain, boolean captainFirst) {}

    NativeSmallShipsProbe(ServerLevel level, BlockPos origin) { this.level = level; this.origin = origin; }

    void prepare() throws Exception {
        require(Boolean.getBoolean("siegeoverhaul.nativeShipsQa"), "Explicit opt-in required");
        String release = System.getProperty("siegeoverhaul.nativeShipsQa.release");
        require(List.of("final", "legacy").contains(release), "Unexpected release");
        finalRelease = release.equals("final");
        Path directory = Path.of(System.getProperty("siegeoverhaul.nativeShipsQa.directory")).toRealPath();
        require(directory.equals(Path.of("").toRealPath()), "Unexpected game directory");
        require(directory.endsWith(Path.of("build", "native-ships-" + release + "-qa", "server")), "Unsafe fixture path");
        require(!Files.exists(directory.resolve("eula.txt")), "GameTest fixture must not create an EULA acceptance file");
        evidence = directory.resolveSibling("evidence");
        Files.createDirectories(evidence);
        require(!Files.exists(evidence.resolve("result.json")), "Refusing existing evidence");
        report.put("status", "running");
        report.put("compatibility", "unverified");
        report.put("fixtureRelease", release);
        report.put("physicalDistribution", FMLEnvironment.dist.name());
        report.put("logicalSide", level.isClientSide ? "CLIENT" : "SERVER");
        report.put("serverClass", level.getServer().getClass().getName());
        report.put("limitations", List.of("Disposable initialized water scene and direct production/native APIs, not a naturally spawned raid",
                "No production compatibility flag, native config, vendor JAR or ownership is changed",
                "Entity NBT round trip is not a chunk unload or process restart",
                "Captain navigation, live client rendering, ocean routes and arbitrary modpacks remain unverified"));
        Map<String, String> versions = new LinkedHashMap<>();
        Map<String, Object> artifacts = new LinkedHashMap<>();
        for (String id : List.of("minecraft", "forge", "siegeoverhaul", "workers", "recruits", "smallships", "siegeweapons")) {
            var info = ModList.get().getModContainerById(id).orElseThrow().getModInfo();
            versions.put(id, info.getVersion().toString());
            if (List.of("workers", "recruits", "smallships", "siegeweapons").contains(id)) {
                Path jar = info.getOwningFile().getFile().getFilePath();
                require(Files.isRegularFile(jar), "Missing real companion JAR " + id);
                artifacts.put(id, Map.of("fileName", jar.getFileName().toString(), "sha256",
                        HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(jar))),
                        "kind", "ForgeGradle remapped runtime JAR, not original release bytes"));
            }
        }
        require(versions.get("smallships").equals(finalRelease ? "2.0.0" : "2.0.0-b1.4"), "Wrong Small Ships binary");
        require(versions.get("recruits").equals("1.15.2"), "Wrong Recruits binary");
        report.put("loadedModVersions", versions);
        report.put("loadedCompanionArtifacts", artifacts);
        report.put("recruitsSmallShipsLoaded", Main.isSmallShipsLoaded);
        report.put("recruitsSmallShipsCompatible", Main.isSmallShipsCompatible);
        require(Main.isSmallShipsLoaded, "Recruits did not detect installed ships");
        // Wide/deep/tall, explicitly initialized test water. No game-play terrain is edited.
        for (int x = -24; x <= 40; x++) for (int z = -20; z <= 20; z++) {
            BlockPos column = origin.offset(x, 0, z);
            require(level.hasChunkAt(column), "Fixture terrain is not loaded");
            for (int y = -3; y <= 14; y++)
                level.setBlock(column.above(y), y == -3 ? Blocks.STONE.defaultBlockState()
                        : y <= 0 ? Blocks.WATER.defaultBlockState() : Blocks.AIR.defaultBlockState(), 3);
        }
        level.setDayTime(6000);
        for (int i = 0; i < 4; i++) {
            String hull = i < 2 ? "cog" : "brigg";
            BlockPos pos = origin.offset((i % 2) * 20 - 10, 0, (i / 2) * 20 - 10);
            Boat boat = (Boat) create("smallships:" + hull, pos);
            Mob recruit = (Mob) create("recruits:recruit", pos);
            CaptainEntity captain = (CaptainEntity) create("recruits:captain", pos);
            recruit.setNoAi(true); captain.setNoAi(true);
            boolean captainFirst = i % 2 == 0;
            require(NavalFleet.board(boat, captainFirst ? captain : recruit), "First native boarding rejected: " + hull);
            require(NavalFleet.board(boat, captainFirst ? recruit : captain), "Second native boarding rejected: " + hull);
            cases.add(new Case(boat, recruit, captain, captainFirst));
        }
        preparedAt = level.getGameTime();
        write();
    }

    void inspect() throws Exception {
        require(level.getGameTime() - preparedAt >= 20, "No actual native ticking elapsed");
        List<Map<String, Object>> observations = new ArrayList<>();
        for (Case sample : cases) {
            Boat ship = sample.boat;
            require(ship.isAlive() && sample.recruit.isPassenger() && sample.captain.isPassenger(), "Native fixture crew lost");
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("hull", ForgeRegistries.ENTITY_TYPES.getKey(ship.getType()).toString());
            item.put("captainFirst", sample.captainFirst);
            item.put("passengerCount", ship.getPassengers().size());
            item.put("nativeRecruitsCaptainDriver", new SmallShips(ship, sample.captain).isCaptainDriver());
            item.put("controllingPassenger", ship.getControllingPassenger() == null ? "none" : ship.getControllingPassenger().getType().toString());
            item.put("captainPassengerIndex", ship.getPassengers().indexOf(sample.captain));
            if (finalRelease) {
                Object captainSeat = call(ship, "getSeatOf", new Class<?>[]{Entity.class}, sample.captain);
                Object recruitSeat = call(ship, "getSeatOf", new Class<?>[]{Entity.class}, sample.recruit);
                require(captainSeat != null && recruitSeat != null, "Native final crew lacks seat assignment");
                String captainType = String.valueOf(call(captainSeat, "type"));
                String recruitType = String.valueOf(call(recruitSeat, "type"));
                require(captainType.equals("DRIVER") && !recruitType.equals("DRIVER"), "Native helm eligibility broken");
                item.put("captainSeatType", captainType);
                item.put("recruitSeatType", recruitType);
                item.put("nativeHasHelmsman", call(ship, "hasHelmsman"));
                call(ship, "setLeft", new Class<?>[]{boolean.class}, true);
                item.put("nativeLeftAfterRequest", call(ship, "isLeft"));
                call(ship, "setLeft", new Class<?>[]{boolean.class}, false);
                // Native dockyard refusal must remain authoritative; never force boarding.
                Mob rejected = (Mob) create("recruits:recruit", ship.blockPosition());
                rejected.setNoAi(true);
                call(ship, "setDockyardWork", new Class<?>[]{boolean.class}, true);
                require(!NavalFleet.board(ship, rejected), "Production boarding bypassed native dockyard lock");
                call(ship, "setDockyardWork", new Class<?>[]{boolean.class}, false);
                item.put("nativeDockyardBoardingRefused", true);
                rejected.discard();
                // Demonstrate the stale fixed two-block clearance against a real mast envelope.
                BlockPos clearanceSite = origin.offset(34, 0, 0);
                BlockPos mastBlock = clearanceSite.above(8);
                level.setBlock(mastBlock, Blocks.STONE.defaultBlockState(), 3);
                item.put("legacyClearanceAcceptsHighObstruction", NavalFleet.isClearWaterFootprint(level, clearanceSite, 3));
                level.setBlock(mastBlock, Blocks.AIR.defaultBlockState(), 3);
                item.put("nativePartCount", ((List<?>) call(ship, "getParts")).size());
            }
            CompoundTag saved = new CompoundTag();
            require(ship.save(saved), "Native entity snapshot failed");
            Entity restored = EntityType.loadEntityRecursive(saved, level, entity -> entity);
            require(restored instanceof Boat && restored.getPassengers().size() == ship.getPassengers().size(), "Native NBT crew roundtrip failed");
            if (finalRelease) {
                Entity restoredCaptain = restored.getPassengers().stream().filter(e -> e.getUUID().equals(sample.captain.getUUID())).findFirst().orElseThrow();
                Object seat = call(restored, "getSeatOf", new Class<?>[]{Entity.class}, restoredCaptain);
                require(seat != null && "DRIVER".equals(String.valueOf(call(seat, "type"))), "Native saved helm assignment lost");
            }
            item.put("nativeEntityNbtCrewRoundTrip", true);
            item.put("noNavalOwnershipTagAdded", !ship.getPersistentData().contains(ModConstants.Tags.NAVAL_DISPOSABLE));
            observations.add(item);
        }
        report.put("observedNativeTicks", level.getGameTime() - preparedAt);
        report.put("hullsAndBoardingOrders", observations);
        report.put("status", "probe-completed");
        write();
    }

    private Entity create(String id, BlockPos pos) {
        ResourceLocation key = new ResourceLocation(id);
        require(ForgeRegistries.ENTITY_TYPES.containsKey(key), "Missing registry ID " + id);
        Entity entity = ForgeRegistries.ENTITY_TYPES.getValue(key).create(level);
        require(entity != null, "Native factory failed " + id);
        entity.moveTo(pos.getX() + .5, pos.getY() + .05, pos.getZ() + .5, 0, 0);
        require(level.addFreshEntity(entity), "Native spawn rejected " + id);
        created.add(entity);
        return entity;
    }

    private static Object call(Object target, String name) throws Exception { return call(target, name, new Class<?>[0]); }
    private static Object call(Object target, String name, Class<?>[] signature, Object... args) throws Exception {
        return target.getClass().getMethod(name, signature).invoke(target, args);
    }
    private static void require(boolean condition, String message) { if (!condition) throw new IllegalStateException(message); }
    private void write() throws Exception { Files.writeString(evidence.resolve("result.json"), new GsonBuilder().setPrettyPrinting().create().toJson(report) + "\n"); }
    void fail(Throwable failure) {
        report.put("status", "failed"); report.put("failure", failure.toString());
        try { write(); } catch (Exception ignored) { /* Required GameTest still fails. */ }
    }
}
