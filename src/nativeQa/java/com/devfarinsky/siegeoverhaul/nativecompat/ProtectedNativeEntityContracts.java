package com.devfarinsky.siegeoverhaul.nativecompat;

import com.devfarinsky.siegeoverhaul.compat.WorkersBridge;
import com.talhanation.workers.entities.AbstractWorkerEntity;
import com.talhanation.workers.entities.BuilderEntity;
import com.talhanation.workers.network.MessageUpdateBuildArea;
import com.talhanation.workers.network.MessageUpdateOwner;
import com.talhanation.workers.network.MessageUpdateWorkArea;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Real Forge lifecycle contracts. These must run in nativeQa, never a mock registry. */
final class ProtectedNativeEntityContracts {
    private ProtectedNativeEntityContracts() {}

    static List<String> verify(ServerLevel level, ServerPlayer owner, ProtectedBuildArea area, Mob builder) throws Exception {
        List<String> checked = new ArrayList<>();
        BlockPos origin = area.getOriginPos(); Vec3 marker = area.position();
        CompoundTag blueprint = area.getStructureNBT();
        int width = area.getWidthSize(), depth = area.getDepthSize(), height = area.getHeightSize();
        var plan = AcceptedConstructionPlan.capture(area);
        require(!origin.equals(BlockPos.containing(marker)), "Fixture must separate marker and native origin");
        require(area.getArea().equals(new AABB(origin, origin.relative(area.getFacing(), depth - 1)
                .relative(area.getFacing().getClockWise(), width - 1).above(height))), "Native envelope moved with marker");
        require(area.getBoundingBox().contains(marker.add(0, .5, 0)), "Physical shovel lacks matching pick box");
        checked.add("Real entity: independent shovel/pick box and immutable native origin/envelope");

        CompoundTag malicious = new CompoundTag(); malicious.putInt("width", width); malicious.putString("facing", "south");
        ListTag blocks = new ListTag(); CompoundTag cell = new CompoundTag();
        cell.putInt("x", width - 1); cell.putInt("y", 0); cell.putInt("z", 0);
        cell.put("state", NbtUtils.writeBlockState(Blocks.TNT.defaultBlockState())); blocks.add(cell); malicious.put("blocks", blocks);
        ListTag entities = new ListTag(); CompoundTag entity = new CompoundTag(); entity.putString("entity_type", "minecraft:pig");
        entities.add(entity); malicious.put("entities", entities);
        var beforeOrigin = level.getBlockState(origin);
        int pigs = level.getEntitiesOfClass(net.minecraft.world.entity.animal.Pig.class, new AABB(origin).inflate(8)).size();
        new MessageUpdateBuildArea(area.getUUID(), 30, 30, 30, malicious, true, true, true).update(area);
        area.setStartBuild(true);
        require(area.getStructureNBT().equals(blueprint) && area.getWidthSize() == width && area.getDepthSize() == depth
                && area.getHeightSize() == height && !area.getFreeArea(), "Native creative packet changed sealed contract");
        require(level.getBlockState(origin).equals(beforeOrigin)
                && level.getEntitiesOfClass(net.minecraft.world.entity.animal.Pig.class, new AABB(origin).inflate(8)).size() == pigs,
                "Native creative packet wrote blocks or spawned entities");
        checked.add("Real native creative packet cannot replace accepted blocks or spawn entities");

        area.moveTo(100, 100, 100); area.moveTo(200, 200, 200, 90, 30);
        area.moveTo(new Vec3(300, 300, 300)); area.moveTo(new BlockPos(400, 400, 400), 180, 20); area.setPos(500, 500, 500);
        require(area.position().equals(marker) && area.getOriginPos().equals(origin), "Entity moveTo bypassed sealed position");
        checked.add("Real Entity moveTo overloads and setPos cannot relocate a published marker");

        var ownership = new MessageUpdateOwner(); ownership.playerUUID = UUID.randomUUID(); ownership.playerName = "Other";
        ownership.updateWorkArea(area); area.setFacing(Direction.WEST); area.setTeamAccess(true); area.setTeamStringID("foreign");
        var deletion = new MessageUpdateWorkArea(); deletion.destroy = true; deletion.updateWorkArea(area, owner);
        require(owner.getUUID().equals(area.getPlayerUUID()) && area.getFacing() == Direction.SOUTH
                && !area.getTeamAccess() && area.getTeamStringID().isEmpty() && !area.isRemoved(),
                "Native ownership/rotate/delete controls bypassed protection");
        checked.add("Real native ownership/rotation/delete packets remain sealed");

        ProtectedBuildArea scratch = ProtectedConstructionAreas.TYPE.get().create(level);
        require(scratch != null, "Cannot construct real native contract fixture");
        CompoundTag supplied = blueprint.copy();
        scratch.initialize(origin, BlockPos.containing(marker).offset(-3, 0, 0), owner.getUUID(), owner.getGameProfile().getName(),
                builder.getUUID(), width, depth, height, supplied);
        supplied.getList("blocks", 10).getCompound(0).getCompound("state").putString("Name", "minecraft:tnt");
        require(scratch.getStructureNBT().equals(blueprint), "Native setter retained a mutable nested input alias");
        scratch.initializeBlueprint(blueprint);
        require(scratch.nativeQueuesReady() && !scratch.stackToPlace.isEmpty(), "Native noncreative queue initializer did not run");
        scratch.stackToPlace.clear(); scratch.setStartBuild(false);
        require(scratch.stackToPlace.isEmpty(), "Native restart packet rebuilt queues");
        CompoundTag alias = scratch.getStructureNBT(); alias.putInt("width", 900);
        require(scratch.getStructureNBT().equals(blueprint), "Native NBT getter leaked an editable alias");
        expectFailure(() -> scratch.initializeBlueprint(alias), "Changed initializer blueprint was accepted");
        checked.add("Real native queue restart is denied and NBT getters are defensive copies");

        CompoundTag saved = scratch.saveWithoutId(new CompoundTag());
        ProtectedBuildArea decoded = ProtectedConstructionAreas.TYPE.get().create(level);
        require(decoded != null, "Cannot construct decode fixture"); decoded.load(saved);
        require(decoded.getOriginPos().equals(origin) && decoded.getArea().equals(area.getArea())
                && AcceptedConstructionPlan.capture(decoded).cells.equals(plan.cells), "Native NBT round trip changed blueprint");
        CompoundTag malformed = saved.copy(); malformed.remove("SiegeNativeSealed");
        ProtectedBuildArea invalid = ProtectedConstructionAreas.TYPE.get().create(level);
        require(invalid != null, "Cannot construct malformed fixture"); invalid.load(malformed);
        expectFailure(invalid::rebuildAcceptedQueues, "Malformed new entity recipe became editable");
        invalid.setStartBuild(true);
        require(!invalid.nativeQueuesReady(), "Malformed new entity acquired native queues");
        checked.add("Real native save/load preserves origin and malformed sealed saves stay closed");
        verifyRejectedBlueprintRecovery(level, owner, saved, builder);
        checked.add("Oversized saved blueprint preserves raw recipe/owner; absent unproven builder cleanup is refused and authenticated cancellation succeeds with the real loaded fixture builder");

        // A distant never-requested chunk proves queue reconstruction does not
        // turn a marker load into a world load. It is never registered or rendered.
        BlockPos unloadedOrigin = new BlockPos(20_000_000, 65, 20_000_000);
        require(!level.hasChunkAt(unloadedOrigin), "Distant unloaded contract probe unexpectedly loaded");
        ProtectedBuildArea distant = ProtectedConstructionAreas.TYPE.get().create(level);
        require(distant != null, "Cannot construct deferred-load fixture");
        distant.initialize(unloadedOrigin, BlockPos.containing(marker), owner.getUUID(), "QA", builder.getUUID(), width, depth, height, blueprint);
        expectFailure(distant::rebuildAcceptedQueues, "Unloaded native cells were queried during reconstruction");
        require(!distant.nativeQueuesReady() && !level.hasChunkAt(unloadedOrigin), "Queue reconstruction force-loaded a chunk");
        checked.add("Real queue reconstruction leaves unloaded blueprint chunks untouched");

        require(!scratch.canWorkHere((AbstractWorkerEntity) builder), "Unpaid area became discoverable");
        scratch.getPersistentData().putBoolean("SiegeConstructionCommissionPaid", true);
        Entity other = builder.getType().create(level);
        require(other instanceof AbstractWorkerEntity && !other.getUUID().equals(builder.getUUID()), "Cannot create distinct real native worker");
        require(!scratch.canWorkHere((AbstractWorkerEntity)other), "A different native builder discovered the reserved paid area");
        other.discard();
        require(!scratch.abortBeforePayment(), "Paid area used unpaid rollback");
        scratch.remove(Entity.RemovalReason.DISCARDED); require(!scratch.isRemoved(), "Raw paid delete succeeded");
        scratch.getPersistentData().putBoolean("SiegeConstructionCommissionPaid", false);
        require(scratch.abortBeforePayment() && scratch.isRemoved(), "Unpaid rollback could not clean up");
        checked.add("Real native area rejects unpaid and wrong-builder discovery; paid delete and unpaid rollback stay distinct");
        return List.copyOf(checked);
    }

    private static void verifyRejectedBlueprintRecovery(ServerLevel level, ServerPlayer owner, CompoundTag validSave, Mob nativeBuilder) throws Exception {
        CompoundTag rejectedSave = validSave.copy();
        Entity recoveryRaw = nativeBuilder.getType().create(level);
        require(recoveryRaw instanceof BuilderEntity, "Cannot create real native quarantine recovery actor");
        BuilderEntity recovery = (BuilderEntity) recoveryRaw;
        UUID absentBuilder = recovery.getUUID(); // Native-generated identity; deliberately not yet registered in the world.
        rejectedSave.putUUID("SiegeNativeBuilder", absentBuilder);
        rejectedSave.remove("ForgeData"); // Dedicated unassigned fixture has no protected receipt to contradict its saved identity.
        CompoundTag raw = validSave.getCompound("structureNBT").copy();
        ListTag cells = raw.getList("blocks", 10);
        CompoundTag first = cells.getCompound(0).copy();
        // Fixed malformed network-budget stress fixture, not a current wall geometry/material oracle.
        while (cells.size() < 6600) cells.add(first.copy());
        CompoundTag unknown = new CompoundTag(); unknown.putString("keep", "unrecognized original metadata");
        raw.put("unrecognizedCapability", unknown);
        rejectedSave.put("structureNBT", raw);
        require(BlueprintNetworkBudget.problem(raw) != null, "Oversized fixture unexpectedly fits native synchronization");
        ProtectedBuildArea rejected = ProtectedConstructionAreas.TYPE.get().create(level);
        require(rejected != null, "Cannot construct quarantine fixture"); rejected.load(rejectedSave);
        require(owner.getUUID().equals(rejected.getPlayerUUID()) && absentBuilder.equals(rejected.reservedBuilderId()),
                "Rejected load lost authenticated cancellation identity");
        require(rejected.getStructureNBT().isEmpty()
                && rejected.getEntityData().get(com.talhanation.workers.entities.workarea.BuildArea.STRUCTURE).isEmpty(),
                "Rejected raw blueprint entered native synchronized data");
        expectFailure(rejected::rebuildAcceptedQueues, "Rejected blueprint reconstructed native queues");
        require(!rejected.nativeQueuesReady() && rejected.stackToPlace.isEmpty(), "Rejected blueprint retained pending targets");
        CompoundTag savedAgain = rejected.saveWithoutId(new CompoundTag());
        require(savedAgain.getCompound("structureNBT").equals(raw)
                && savedAgain.getUUID("playerUUID").equals(owner.getUUID())
                && savedAgain.getUUID("SiegeNativeBuilder").equals(absentBuilder)
                && savedAgain.getBoolean("SiegeNativeSealed") == rejectedSave.getBoolean("SiegeNativeSealed"),
                "Rejected raw recipe or saved identity was normalized or lost");
        ProtectedBuildArea reloaded = ProtectedConstructionAreas.TYPE.get().create(level);
        require(reloaded != null, "Cannot reload quarantine fixture"); reloaded.load(savedAgain);
        require(reloaded.getStructureNBT().isEmpty() && owner.getUUID().equals(reloaded.getPlayerUUID())
                && reloaded.saveWithoutId(new CompoundTag()).getCompound("structureNBT").equals(raw),
                "A repeated rejected reload changed recipe or ownership");
        CompoundTag malformedIdentity = rejectedSave.copy();
        malformedIdentity.putString("ForgeData", "original malformed identity evidence");
        for (int reload = 0; reload < 3; reload++) {
            ProtectedBuildArea untrusted = ProtectedConstructionAreas.TYPE.get().create(level);
            require(untrusted != null, "Cannot construct malformed ForgeData quarantine fixture");
            untrusted.load(malformedIdentity);
            require(untrusted.getPlayerUUID() == null && untrusted.reservedBuilderId() == null
                    && untrusted.getStructureNBT().isEmpty() && !untrusted.nativeQueuesReady(),
                    "Malformed persistent identity acquired native authority after reload");
            malformedIdentity = untrusted.saveWithoutId(new CompoundTag());
            require("original malformed identity evidence".equals(malformedIdentity.getString("ForgeData"))
                    && raw.equals(malformedIdentity.getCompound("structureNBT")),
                    "Real Forge save ordering lost original malformed identity evidence");
        }
        require(level.addFreshEntity(reloaded), "Cannot register owner cancellation fixture");
        var ledger = ConstructionEditLedger.get(level);
        require(ledger.register(reloaded.getUUID(), java.util.Set.of(reloaded.blockPosition())), "Cannot register cancellation fixture reservation");
        CompoundTag reservationBefore = ledger.save(new CompoundTag());
        CompoundTag quarantineBefore = reloaded.saveWithoutId(new CompoundTag());
        ProtectedConstructionActions.handle(owner, reloaded.getUUID(), ProtectedConstructionActions.CANCEL);
        require(ledger.save(new CompoundTag()).equals(reservationBefore)
                        && reloaded.saveWithoutId(new CompoundTag()).equals(quarantineBefore)
                        && !reloaded.isRemoved() && ledger.contains(reloaded.getUUID()) && !ledger.retired(reloaded.getUUID())
                        && ledger.handLifecycle(absentBuilder) == null
                        && owner.getUUID().equals(reloaded.getPlayerUUID()) && absentBuilder.equals(reloaded.reservedBuilderId())
                        && reloaded.saveWithoutId(new CompoundTag()).getCompound("structureNBT").equals(raw),
                "Missing old builder provenance released or normalized quarantined evidence");
        // Explicit fixture setup: this empty, never-commissioned native actor was absent from the world above.
        // No hand receipt, paid state, material stock or production acceptance is manufactured.
        recovery.setPos(reloaded.position().add(-4, 0, 0));
        recovery.setNoAi(true); WorkersBridge.enablePlayerJob(recovery, owner.getUUID());
        require(recovery.getInventory().isEmpty() && recovery.getMainHandItem().isEmpty()
                        && owner.getUUID().equals(WorkersBridge.readWorkerOwner(recovery))
                        && level.addFreshEntity(recovery), "Cannot register empty owned quarantine recovery actor");
        CompoundTag recoveryBefore = recovery.saveWithoutId(new CompoundTag());
        ProtectedConstructionActions.handle(owner, reloaded.getUUID(), ProtectedConstructionActions.CANCEL);
        require(reloaded.isRemoved() && !ledger.contains(reloaded.getUUID()) && !ledger.retired(reloaded.getUUID())
                        && recovery.currentBuildArea == null && !WorkersBridge.hasActiveBuildArea(recovery)
                        && recovery.isNoAi() && owner.getUUID().equals(WorkersBridge.readWorkerOwner(recovery))
                        && !ProtectedBuilderHandLifecycle.selected(recovery.getPersistentData()) && ledger.handLifecycle(absentBuilder) == null
                        && recovery.saveWithoutId(new CompoundTag()).getList("Items", 10).equals(recoveryBefore.getList("Items", 10))
                        && recovery.saveWithoutId(new CompoundTag()).getList("HandItems", 10).equals(recoveryBefore.getList("HandItems", 10)),
                "Authenticated loaded-builder quarantine cleanup changed inventory or retained construction");
        recovery.discard();
    }

    private static void require(boolean value, String message) { if (!value) throw new AssertionError(message); }
    private static void expectFailure(Runnable action, String message) {
        try { action.run(); } catch (IllegalStateException expected) { return; }
        throw new AssertionError(message);
    }
}
