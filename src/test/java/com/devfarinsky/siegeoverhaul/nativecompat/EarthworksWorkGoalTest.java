package com.devfarinsky.siegeoverhaul.nativecompat;

import com.devfarinsky.siegeoverhaul.MinecraftTestSupport;
import com.talhanation.workers.entities.BuilderEntity;
import com.talhanation.workers.entities.workarea.BuildArea;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.goal.Goal;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Owning wrapper and sealed API tests. Actual GoalSelector/world ticks remain native QA gates. */
class EarthworksWorkGoalTest extends MinecraftTestSupport {
    @Test void ordinaryNativeGoalKeepsItsOriginalLifecycleAndCadence() {
        var worker=mock(BuilderEntity.class);var original=goal(false);var grading=goal(true);
        try(var selection=mockStatic(NativeEarthworksJobs.class)){
            selection.when(()->NativeEarthworksJobs.selected(worker)).thenReturn(false);
            var wrapper=new EarthworksWorkGoal(worker,original,()->grading);
            assertFalse(wrapper.requiresUpdateEveryTick());assertTrue(wrapper.canUse());wrapper.start();wrapper.tick();wrapper.stop();
            verify(original).start();verify(original).tick();verify(original).stop();verify(grading,never()).start();
        }
    }
    @Test void exactNewSelectionOwnsOneLifecycleAndNeverTicksOriginal() {
        var worker=mock(BuilderEntity.class);var original=goal(false);var grading=goal(true);var selected=new AtomicBoolean(true);
        try(var selection=mockStatic(NativeEarthworksJobs.class)){
            selection.when(()->NativeEarthworksJobs.selected(worker)).thenAnswer(call->selected.get());
            var wrapper=new EarthworksWorkGoal(worker,original,()->grading);wrapper.start();wrapper.tick();
            selected.set(false);assertFalse(wrapper.canContinueToUse());wrapper.tick();wrapper.stop();
            verify(grading).tick();verify(grading).stop();verify(original,never()).start();verify(original,never()).tick();
        }
    }
    @Test void malformedNewSelectionAndThrowingProviderNeverFallBackOrReplay() {
        var worker=mock(BuilderEntity.class);var original=goal(false);
        try(var selection=mockStatic(NativeEarthworksJobs.class)){
            selection.when(()->NativeEarthworksJobs.selected(worker)).thenReturn(true);
            var absent=new EarthworksWorkGoal(worker,original,()->null);assertFalse(absent.canUse());absent.start();absent.tick();
            var unavailable=new EarthworksWorkGoal(worker,original,()->{throw new LinkageError("missing");});assertFalse(unavailable.canUse());unavailable.start();unavailable.tick();
            verify(original,never()).start();verify(original,never()).tick();
        }
    }
    @Test void newMarkerSealsNativeStartOwnershipRecipeAndRemovalControls() throws Exception {
        assertEquals(BuildArea.class,EarthworksBuildArea.class.getSuperclass());
        for(String name:new String[]{"setStartBuild","setFreeArea","setAlwaysShowProjection","setTeamAccess","setDone"})
            assertNotNull(EarthworksBuildArea.class.getDeclaredMethod(name,boolean.class));
        assertNotNull(EarthworksBuildArea.class.getDeclaredMethod("setStructureNBT",CompoundTag.class));
        assertNotNull(EarthworksBuildArea.class.getDeclaredMethod("setPlayerUUID",java.util.UUID.class));
        assertNotNull(EarthworksBuildArea.class.getDeclaredMethod("setFacing",Direction.class));
        assertNotNull(EarthworksBuildArea.class.getDeclaredMethod("remove",Entity.RemovalReason.class));
        assertNotNull(EarthworksBuildArea.class.getDeclaredMethod("moveTo",BlockPos.class,float.class,float.class));
        assertNotNull(EarthworksBuildArea.class.getDeclaredMethod("scanBreakArea"));
        assertNotNull(EarthworksBuildArea.class.getDeclaredMethod("scanFreeArea"));
    }
    private static Goal goal(boolean everyTick){var goal=mock(Goal.class);when(goal.getFlags()).thenReturn(EnumSet.of(Goal.Flag.MOVE,Goal.Flag.LOOK));when(goal.canUse()).thenReturn(true);when(goal.canContinueToUse()).thenReturn(true);when(goal.requiresUpdateEveryTick()).thenReturn(everyTick);return goal;}
}
