package com.devfarinsky.siegeoverhaul.nativecompat;

import com.talhanation.workers.entities.AbstractWorkerEntity;
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

    private static void require(boolean value, String message) { if (!value) throw new AssertionError(message); }
    private static void expectFailure(Runnable action, String message) {
        try { action.run(); } catch (IllegalStateException expected) { return; }
        throw new AssertionError(message);
    }
}
