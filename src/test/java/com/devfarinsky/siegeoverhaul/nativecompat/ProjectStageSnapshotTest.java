package com.devfarinsky.siegeoverhaul.nativecompat;

import com.devfarinsky.siegeoverhaul.MinecraftTestSupport;
import com.devfarinsky.siegeoverhaul.core.PerimeterProject;
import com.devfarinsky.siegeoverhaul.core.TerritoryFortification;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Stack;

import static org.junit.jupiter.api.Assertions.*;

class ProjectStageSnapshotTest extends MinecraftTestSupport {
    private AcceptedConstructionPlan plan(PerimeterProject project) {
        var stage=project.active().layout();
        return AcceptedConstructionPlan.decode(stage.origin(),Direction.SOUTH,stage.width(),stage.depth(),stage.height(),
                TerritoryFortification.blueprint(stage.targets(),stage.min(),stage.max()));
    }
    private CompoundTag reservation(PerimeterProject project) {
        var stage=project.active().layout(); CompoundTag tag=new CompoundTag(); tag.putInt("Version",1);
        tag.putLongArray("Cells",stage.reservation().stream().mapToLong(Long::longValue).toArray());
        ListTag clear=new ListTag();
        for(long pos:stage.clearance()) {var cell=new CompoundTag();cell.putLong("Pos",pos);
            cell.put("State",NbtUtils.writeBlockState(project.clearanceBefore().get(pos)));clear.add(cell);}
        tag.put("Clearance",clear);return tag;
    }
    private Map<BlockPos,BlockState> before(PerimeterProject project) {
        Map<BlockPos,BlockState> result=new HashMap<>();
        project.active().layout().targets().keySet().forEach(pos->result.put(BlockPos.of(pos),project.before().get(pos)));return result;
    }
    private boolean matches(PerimeterProject p,AcceptedConstructionPlan plan,CompoundTag reserved,Map<BlockPos,BlockState> before) {
        return NativeConstructionGuard.projectSnapshotMatches(p,plan,AcceptedConstructionReservation.load(plan,reserved),before,
                p.header().owner(),p.header().builder(),p.header().coreKey(),p.header().originalCore());
    }
    @Test void exactOriginalContextAndGeometryBindTheLoadedStage() {
        var p=ConstructionProjectLedgerTest.project();var plan=plan(p);var r=reservation(p);
        assertTrue(matches(p,plan,r,before(p)));
        assertFalse(NativeConstructionGuard.projectSnapshotMatches(p,plan,AcceptedConstructionReservation.load(plan,r),before(p),
                p.header().owner(),p.header().builder(),"team:other",p.header().originalCore()));
    }
    @Test void changingBothEntityRecipesToAnotherAllowedPaletteCannotAcquireManifestAuthority() {
        var p=ConstructionProjectLedgerTest.project();var stage=p.active().layout();
        var blueprint=TerritoryFortification.blueprint(stage.targets(),stage.min(),stage.max());
        var cell=blueprint.getList("blocks",10).getCompound(0);
        String old=cell.getCompound("state").getString("Name");
        cell.put("state",NbtUtils.writeBlockState(old.equals("minecraft:cobblestone")?Blocks.OAK_PLANKS.defaultBlockState():Blocks.COBBLESTONE.defaultBlockState()));
        var changed=AcceptedConstructionPlan.decode(stage.origin(),Direction.SOUTH,stage.width(),stage.depth(),stage.height(),blueprint);
        assertEquals(plan(p).cells.keySet(),changed.cells.keySet());
        assertFalse(matches(p,changed,reservation(p),before(p)));
    }
    @Test void validButDifferentBeforeOrClearanceAndEnvelopeAreNotAdoptedOnReload() {
        var p=ConstructionProjectLedgerTest.project();var plan=plan(p);var changedBefore=before(p);
        changedBefore.put(changedBefore.keySet().iterator().next(),Blocks.DANDELION.defaultBlockState());
        assertFalse(matches(p,plan,reservation(p),changedBefore));
        var changedClear=reservation(p);assertFalse(changedClear.getList("Clearance",10).isEmpty());
        changedClear.getList("Clearance",10).getCompound(0).put("State",NbtUtils.writeBlockState(Blocks.DANDELION.defaultBlockState()));
        assertFalse(matches(p,plan,changedClear,before(p)));
        var stage=p.active().layout();var wider=TerritoryFortification.blueprint(stage.targets(),stage.min(),stage.max().east());
        var changedPlan=AcceptedConstructionPlan.decode(stage.origin().east(),Direction.SOUTH,stage.width()+1,stage.depth(),stage.height(),wider);
        assertEquals(plan.cells,changedPlan.cells);assertFalse(matches(p,changedPlan,reservation(p),before(p)));
    }
    @Test void prechargeCandidateRequiresTheExactNativeRecipeEvenWhenSolidTargetsMatch() {
        // The same matcher now guards the local candidate in protectStage before
        // it writes the handoff receipt or returns success to the payment caller.
        var p=ConstructionProjectLedgerTest.project();var stage=p.active().layout();
        var nativeTag=TerritoryFortification.blueprint(stage.targets(),stage.min(),stage.max());
        nativeTag.putString("entities","malformed native metadata must not be normalized away");
        var changed=AcceptedConstructionPlan.decode(stage.origin(),Direction.SOUTH,stage.width(),stage.depth(),stage.height(),nativeTag);
        assertEquals(plan(p).cells,changed.cells);
        assertFalse(matches(p,changed,reservation(p),before(p)));
        var reloaded=PerimeterProject.load(p.save());
        assertTrue(matches(reloaded,plan(p),reservation(p),before(p)), "An exact save/reload retains the native recipe");
    }
    public static class Queues {public Object stackToPlace=new Stack<>(),stackToBreak=new Stack<>(),stackToFree;}
    @Test void emptyAreaQueuesDoNotProveAnIndependentNativeGoalHasNoPendingWork() throws Exception {
        var goal=new Queues();assertTrue(NativeConstructionGuard.nativeGoalQueuesComplete(goal));
        var pending=new Stack<BlockPos>();pending.push(new BlockPos(0,65,0));goal.stackToPlace=pending;
        assertFalse(NativeConstructionGuard.nativeGoalQueuesComplete(goal));
        goal.stackToPlace=new Stack<>();goal.stackToBreak=pending;assertFalse(NativeConstructionGuard.nativeGoalQueuesComplete(goal));
        goal.stackToBreak=new Stack<>();goal.stackToFree="unknown";assertFalse(NativeConstructionGuard.nativeGoalQueuesComplete(goal));
    }
}
