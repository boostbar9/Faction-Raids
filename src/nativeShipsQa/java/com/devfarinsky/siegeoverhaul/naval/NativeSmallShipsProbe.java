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
import net.minecraft.world.phys.AABB;
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
    private final Map<java.util.UUID, Float> beforeTurns = new LinkedHashMap<>();
    private final Map<String, Object> adapterChecks = new LinkedHashMap<>();

    private record Case(Boat boat, Mob recruit, CaptainEntity captain, boolean captainFirst, int shipTicks, int captainTicks, int recruitTicks) {}

    record CrewReceipt(java.util.UUID ship, java.util.UUID captain, java.util.UUID recruit) {}
    List<CrewReceipt> receipts() { return cases.stream().map(c -> new CrewReceipt(c.boat.getUUID(), c.captain.getUUID(), c.recruit.getUUID())).toList(); }

    NativeSmallShipsProbe(ServerLevel level, BlockPos origin) { this.level = level; this.origin = origin; }

    void prepare() throws Exception {
        require(Boolean.getBoolean("siegeoverhaul.nativeShipsQa"), "Explicit opt-in required");
        String release = System.getProperty("siegeoverhaul.nativeShipsQa.release");
        require(List.of("final", "legacy").contains(release), "Unexpected release");
        finalRelease = release.equals("final");
        Path directory = Path.of(System.getProperty("siegeoverhaul.nativeShipsQa.directory")).toRealPath();
        require(directory.equals(Path.of("").toRealPath()), "Unexpected game directory");
        boolean clientFixture = Boolean.getBoolean("siegeoverhaul.nativeShipsClientQa");
        String fixtureSuffix = clientFixture ? "-client" : Boolean.getBoolean("siegeoverhaul.nativeShipsQa.adapter") ? "-adapter" : "";
        require(directory.endsWith(Path.of("build", "native-ships-" + release + fixtureSuffix + "-qa", clientFixture ? "client" : "server")), "Unsafe fixture path");
        require(!Files.exists(directory.resolve("eula.txt")), "GameTest fixture must not create an EULA acceptance file");
        evidence = directory.resolveSibling("evidence");
        Files.createDirectories(evidence);
        require(!Files.exists(evidence.resolve("result.json")), "Refusing existing evidence");
        report.put("status", "running");
        report.put("compatibility", "unverified");
        report.put("fixtureRelease", release);
        report.put("qaOnlyAdapterEnabled", Boolean.getBoolean("siegeoverhaul.nativeShipsQa.adapter"));
        report.put("physicalDistribution", FMLEnvironment.dist.name());
        report.put("logicalSide", level.isClientSide ? "CLIENT" : "SERVER");
        report.put("serverClass", level.getServer().getClass().getName());
        report.put("limitations", List.of("Disposable initialized water scene and direct production/native APIs, not a naturally spawned raid",
                "No production compatibility flag, native config or vendor JAR is changed; synthetic fixture raid/ownership tags are restored after the regression",
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
        for (int x = -22; x <= 22; x++) for (int z = -22; z <= 22; z++) {
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
            recruit.setPersistenceRequired(); captain.setPersistenceRequired();
            boolean captainFirst = i % 2 == 0;
            require(NavalFleet.board(boat, captainFirst ? captain : recruit), "First native boarding rejected: " + hull);
            require(NavalFleet.board(boat, captainFirst ? recruit : captain), "Second native boarding rejected: " + hull);
            cases.add(new Case(boat, recruit, captain, captainFirst, boat.tickCount, captain.tickCount, recruit.tickCount));
        }
        preparedAt = level.getGameTime();
        write();
    }

    void inspect() throws Exception {
        require(level.getGameTime() - preparedAt >= 20, "No actual native ticking elapsed");
        List<Map<String, Object>> observations = new ArrayList<>();
        for (Case sample : cases) {
            Boat ship = sample.boat;
            require(ship.isAlive() && sample.recruit.getVehicle() == ship && sample.captain.getVehicle() == ship
                    && ship.getPassengers().size() == 2, "Native fixture crew lost or moved");
            require(ship.tickCount - sample.shipTicks >= 19 && sample.captain.tickCount - sample.captainTicks >= 19
                    && sample.recruit.tickCount - sample.recruitTicks >= 19, "Native actors did not actually tick: " + ship.getType() + " first=" + sample.captainFirst
                    + " ship=" + (ship.tickCount - sample.shipTicks) + " captain=" + (sample.captain.tickCount - sample.captainTicks)
                    + " recruit=" + (sample.recruit.tickCount - sample.recruitTicks));
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("hull", ForgeRegistries.ENTITY_TYPES.getKey(ship.getType()).toString());
            item.put("captainFirst", sample.captainFirst);
            item.put("entityTicks", Map.of("ship", ship.tickCount - sample.shipTicks,
                    "captain", sample.captain.tickCount - sample.captainTicks, "recruit", sample.recruit.tickCount - sample.recruitTicks));
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
                BlockPos clearanceSite = origin.offset(18, 0, 0);
                Object mast = ((List<?>) call(ship, "getParts")).stream().filter(part -> {
                    try { return Boolean.TRUE.equals(call(part, "mast")); }
                    catch (Exception failure) { throw new IllegalStateException(failure); }
                }).findFirst().orElseThrow();
                AABB mastBounds = (AABB) call(mast, "boxAt", new Class<?>[]{double.class, double.class, double.class, float.class},
                        clearanceSite.getX() + .5, clearanceSite.getY() + .05, clearanceSite.getZ() + .5, 0F);
                BlockPos mastBlock = BlockPos.containing((mastBounds.minX + mastBounds.maxX) / 2,
                        mastBounds.maxY - 1, (mastBounds.minZ + mastBounds.maxZ) / 2);
                require(mastBlock.getY() > clearanceSite.getY() + 2 && mastBounds.intersects(new AABB(mastBlock)),
                        "High-obstruction fixture misses actual translated native mast");
                item.put("nativeMastIntersectsObstruction", true);
                level.setBlock(mastBlock, Blocks.STONE.defaultBlockState(), 3);
                item.put("legacyClearanceAcceptsHighObstruction", NavalFleet.isClearWaterFootprint(level, clearanceSite, 3));
                level.setBlock(mastBlock, Blocks.AIR.defaultBlockState(), 3);
                item.put("nativePartCount", ((List<?>) call(ship, "getParts")).size());
            }
            CompoundTag saved = new CompoundTag();
            require(ship.save(saved), "Native entity snapshot failed");
            Entity restored = EntityType.loadEntityRecursive(saved, level, entity -> entity);
            require(restored instanceof Boat && restored.getPassengers().size() == ship.getPassengers().size()
                    && restored.getPassengers().stream().map(Entity::getUUID).collect(java.util.stream.Collectors.toSet())
                    .equals(ship.getPassengers().stream().map(Entity::getUUID).collect(java.util.stream.Collectors.toSet())),
                    "Native NBT crew UUID roundtrip failed");
            if (finalRelease) {
                Entity restoredCaptain = restored.getPassengers().stream().filter(e -> e.getUUID().equals(sample.captain.getUUID())).findFirst().orElseThrow();
                Object seat = call(restored, "getSeatOf", new Class<?>[]{Entity.class}, restoredCaptain);
                require(seat != null && "DRIVER".equals(String.valueOf(call(seat, "type"))), "Native saved helm assignment lost");
            }
            item.put("nativeEntityNbtCrewRoundTrip", true);
            // Exercise the actual production recovery/tick path on an unowned native
            // vessel carrying one lost raider plus an unrelated captain.
            String team = "team:native-ships-qa-" + ship.getId();
            sample.recruit.getPersistentData().putString(ModConstants.Tags.RAID_TEAM, team);
            sample.captain.getPersistentData().putString(ModConstants.Tags.RAID_TEAM, team);
            var raid = new com.devfarinsky.siegeoverhaul.RaidSavedData.RaidState(team, "fixture", 0);
            raid.raiders.add(sample.recruit.getUUID()); raid.navalBeachPos = origin.offset(100, 0, 100);
            Vec3 velocity = ship.getDeltaMovement(); float originalYaw = ship.getYRot();
            NavalConvoy.recover(level, raid);
            require(NavalConvoy.isRaiderBoat(team, ship), "Lost native raider crew was not recovered for safe landing");
            require(!NavalConvoy.mayControl(ship, team), "Recovery claimed an unowned native ship");
            NavalConvoy.tick(team, level, raid.navalBeachPos);
            require(ship.getYRot() == originalYaw && ship.getDeltaMovement().equals(velocity), "Convoy steered an unowned native ship");
            // Even our own spawned vessel must yield when an unrelated living passenger boards.
            ship.getPersistentData().putBoolean(ModConstants.Tags.NAVAL_DISPOSABLE, true);
            require(NavalConvoy.mayControl(ship, team), "Owned all-raid fixture did not allow convoy control");
            sample.captain.getPersistentData().remove(ModConstants.Tags.RAID_TEAM);
            require(!NavalConvoy.mayControl(ship, team), "Unrelated native captain lost control to convoy");
            NavalConvoy.tick(team, level, raid.navalBeachPos);
            require(ship.getYRot() == originalYaw && ship.getDeltaMovement().equals(velocity), "Convoy steered with an unrelated native captain");
            NavalConvoy.forget(team);
            ship.getPersistentData().remove(ModConstants.Tags.NAVAL_TEAM);
            ship.getPersistentData().remove(ModConstants.Tags.NAVAL_BEACH);
            ship.getPersistentData().remove(ModConstants.Tags.NAVAL_DISPOSABLE);
            sample.recruit.getPersistentData().remove(ModConstants.Tags.RAID_TEAM);
            require(!ship.getPersistentData().contains(ModConstants.Tags.NAVAL_DISPOSABLE)
                    && !ship.getPersistentData().contains(ModConstants.Tags.NAVAL_TEAM)
                    && !ship.getPersistentData().contains(ModConstants.Tags.NAVAL_BEACH)
                    && !sample.recruit.getPersistentData().contains(ModConstants.Tags.RAID_TEAM)
                    && !sample.captain.getPersistentData().contains(ModConstants.Tags.RAID_TEAM), "Synthetic ownership tags were not restored");
            item.put("noNavalOwnershipTagAdded", true);
            item.put("productionRecoveryLeavesUnownedNativeShipMotionUnchanged", true);
            item.put("unrelatedNativeCaptainBlocksConvoySteering", true);
            observations.add(item);
        }
        report.put("observedNativeTicks", level.getGameTime() - preparedAt);
        report.put("hullsAndBoardingOrders", observations);
        report.put("status", "probe-completed");
        write();
    }

    void beginPrototype() throws Exception {
        require(finalRelease && Boolean.getBoolean("siegeoverhaul.nativeShipsQa.adapter"), "Prototype must be explicit and final-only");
        require(!Main.isSmallShipsCompatible, "Prototype must not activate the production compatibility gate");
        for (Case sample : cases) {
            require(sample.boat instanceof com.devfarinsky.siegeoverhaul.naval.prototype.FinalShipsTurnCommands.ShipHook,
                    "Native Ship prototype injection was not applied");
            SmallShips wrapper = new SmallShips(sample.boat, sample.captain);
            require(wrapper instanceof com.devfarinsky.siegeoverhaul.naval.prototype.FinalShipsTurnCommands.RecruitsHook,
                    "Native Recruits prototype injection was not applied");
            require(wrapper.isCaptainDriver(), "Prototype must recognize actual helm independent of boarding order");
            require(sample.boat.getControllingPassenger() == null, "Prototype changed shared controlling-passenger attribution");
            beforeTurns.put(sample.boat.getUUID(), sample.boat.getYRot());
        }
        report.put("status", "prototype-running");
        report.put("qaOnlyAdapter", adapterChecks);
        write();
    }

    void issuePrototypeTurns() throws Exception {
        for (Case sample : cases) {
            Boat ship = sample.boat;
            float yaw = ship.getYRot();
            new SmallShips(ship, sample.captain).rotateShip(sample.captainFirst, !sample.captainFirst);
            require(ship.getYRot() == yaw, "Adapter performed legacy direct-yaw mutation");
            require(Boolean.TRUE.equals(call(ship, sample.captainFirst ? "isLeft" : "isRight")), "Native turn getter did not see valid captain command");
        }
    }

    void finishPrototypeTurns() throws Exception {
        List<Map<String, Object>> turns = new ArrayList<>();
        for (Case sample : cases) {
            float change = net.minecraft.util.Mth.wrapDegrees(sample.boat.getYRot() - beforeTurns.get(sample.boat.getUUID()));
            require(sample.captainFirst ? change < -.1F : change > .1F, "Native physics did not perform requested turn: " + change);
            turns.add(Map.of("hull", sample.boat.getType().toString(), "captainFirst", sample.captainFirst, "nativeYawChange", change));
        }
        adapterChecks.put("nativeTurnsWithoutDirectYawWrites", turns);
        write();
    }

    void finishPrototypeExpiry() throws Exception {
        for (Case sample : cases) {
            Boat ship = sample.boat;
            require(!Boolean.TRUE.equals(call(ship, "isLeft")) && !Boolean.TRUE.equals(call(ship, "isRight")), "Expired commands kept steering");
            SmallShips wrapper = new SmallShips(ship, sample.captain);
            Object helm = call(ship, "getSeatOf", new Class<?>[]{Entity.class}, sample.captain);
            int helmId = ((Number) call(helm, "id")).intValue();
            Object bench = null;
            for (Object seat : (List<?>) call(ship, "getSeats")) {
                int id = ((Number) call(seat, "id")).intValue();
                if (!"DRIVER".equals(String.valueOf(call(seat, "type"))) && Boolean.TRUE.equals(call(ship, "isSeatFree", new Class<?>[]{int.class}, id))) {
                    bench = seat; break;
                }
            }
            require(bench != null, "Fixture has no free non-helm seat");
            wrapper.rotateShip(false, true);
            require(Boolean.TRUE.equals(call(ship, "isRight")), "Control receipt missing before transfer");
            call(ship, "assignSeat", new Class<?>[]{Entity.class, int.class}, sample.captain, ((Number) call(bench, "id")).intValue());
            require(!wrapper.isCaptainDriver() && !Boolean.TRUE.equals(call(ship, "isRight")), "Passenger captain retained helm authority");
            float yaw = ship.getYRot();
            Object sails = call(ship, "getSailState");
            wrapper.rotateShip(false, true);
            wrapper.setSailState(4);
            require(ship.getYRot() == yaw && sails.equals(call(ship, "getSailState")), "Passenger captain changed another station's controls");
            require(Boolean.TRUE.equals(call(ship, "isSeatFree", new Class<?>[]{int.class}, helmId)), "QA would overwrite an occupied helm");
            call(ship, "assignSeat", new Class<?>[]{Entity.class, int.class}, sample.captain, helmId);
            require(wrapper.isCaptainDriver(), "Restored captain did not regain helm identity");
            call(ship, "setDockyardWork", new Class<?>[]{boolean.class}, true);
            wrapper.rotateShip(false, true);
            require(!Boolean.TRUE.equals(call(ship, "isRight")), "Dockyard lock allowed a turn receipt");
            call(ship, "setDockyardWork", new Class<?>[]{boolean.class}, false);
            require(sample.recruit.getVehicle() == ship && sample.captain.getVehicle() == ship && ship.getPassengers().size() == 2,
                    "Prototype displaced native crew");
        }
        adapterChecks.put("commandExpiryAndHelmLoss", true);
        adapterChecks.put("passengerCaptainCannotSteerOrChangeSails", true);
        adapterChecks.put("dockyardTurnRefused", true);
        adapterChecks.put("sharedControllingPassengerUnchanged", true);
        adapterChecks.put("productionCompatibilityGateUnchanged", !Main.isSmallShipsCompatible);
        adapterChecks.put("scope", "QA-only hooks and direct native wrapper calls. Native ticks own rotation; no waypoint or release compatibility claim.");
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
