package com.devfarinsky.siegeoverhaul.camp;
import com.devfarinsky.siegeoverhaul.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class BuilderWorkShiftTest extends MinecraftTestSupport {
    public abstract static class WorkerApi extends Mob {
        public boolean isFleeing;
        protected WorkerApi(EntityType<? extends Mob> type,Level level){super(type,level);}
        public boolean shouldWork(){return true;}
        public boolean needsToGetToChest(){return false;}
    }
    @Test void nightShiftDelegatesOneNativeTickAndYieldsForFleeingOrSupplies() throws Exception {
        WorkerApi worker=mock(WorkerApi.class);ServerLevel level=mock(ServerLevel.class);Goal nativeGoal=mock(Goal.class);
        when(nativeGoal.getFlags()).thenReturn(EnumSet.of(Goal.Flag.MOVE,Goal.Flag.LOOK));
        when(worker.level()).thenReturn(level);when(worker.isAlive()).thenReturn(true);when(worker.shouldWork()).thenReturn(true);
        var tag=new CompoundTag();tag.putString(ModConstants.Tags.CAMP_WORKER_TEAM,"team:test");when(worker.getPersistentData()).thenReturn(tag);
        var data=new RaidSavedData();var raid=new RaidSavedData.RaidState("team:test","siege_core",0);
        raid.nativeCamp.putUUID(ModConstants.Tags.CAMP_OWNER,UUID.randomUUID());raid.pendingCampBlocks.put(0L,"minecraft:stone_bricks");data.raids.put(raid.teamKey,raid);
        try(var saves=mockStatic(RaidSavedData.class)) {
            saves.when(()->RaidSavedData.get(null)).thenReturn(data);
            var goal=new BuilderWorkShift(worker,nativeGoal);
            assertFalse(level.isDay());assertTrue(goal.canUse());goal.start();goal.tick();goal.stop();
            verify(nativeGoal).start();verify(nativeGoal).tick();verify(nativeGoal).stop();
            worker.isFleeing=true;assertFalse(goal.canContinueToUse());worker.isFleeing=false;
            when(worker.needsToGetToChest()).thenReturn(true);assertFalse(goal.canUse());
            when(worker.needsToGetToChest()).thenReturn(false);raid.pendingCampBlocks.clear();assertFalse(goal.canUse());
        }
    }
}
