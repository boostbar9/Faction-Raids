package com.devfarinsky.siegeoverhaul.raid;

import com.devfarinsky.siegeoverhaul.MinecraftTestSupport;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import net.minecraft.world.entity.ai.navigation.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.pathfinder.*;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RaiderDoorsTest extends MinecraftTestSupport {
    final ServerLevel level=mock(ServerLevel.class);
    final Mob mob=mock(Mob.class);
    final BlockPos entrance=new BlockPos(1,64,0);
    final Map<BlockPos,BlockState> blocks=new HashMap<>();
    RaiderDoorsTest() {
        when(level.hasChunkAt(any())).thenReturn(true);
        var border=mock(WorldBorder.class);when(level.getWorldBorder()).thenReturn(border);
        when(border.isWithinBounds(any(BlockPos.class))).thenReturn(true);
        when(level.getBlockState(any())).thenAnswer(a->blocks.getOrDefault(a.getArgument(0),Blocks.AIR.defaultBlockState()));
        when(mob.position()).thenReturn(new Vec3(.5,64,.5));when(mob.getY()).thenReturn(64D);
        when(mob.level()).thenReturn(level);
        door(Blocks.OAK_DOOR,false);
    }
    void door(Block type,boolean open) {
        blocks.put(entrance,type.defaultBlockState().setValue(DoorBlock.HALF,DoubleBlockHalf.LOWER).setValue(DoorBlock.OPEN,open));
        blocks.put(entrance.above(),type.defaultBlockState().setValue(DoorBlock.HALF,DoubleBlockHalf.UPPER).setValue(DoorBlock.OPEN,open));
    }
    Path path() {return new Path(List.of(new Node(0,64,0),new Node(1,64,0),new Node(2,64,0)),new BlockPos(3,64,0),true);}
    @Test void supportsNativeAsyncAndVanillaGroundButNotFlyingOrSwimming() {
        assertTrue(RaiderDoors.supports(mock(com.talhanation.recruits.pathfinding.AsyncGroundPathNavigation.class)));
        assertTrue(RaiderDoors.supports(mock(GroundPathNavigation.class)));
        assertFalse(RaiderDoors.supports(mock(FlyingPathNavigation.class)));
        assertFalse(RaiderDoors.supports(mock(WaterBoundPathNavigation.class)));
    }
    @Test void installsOnceOnNativeNavigatorAndEnablesBothDoorFlags() throws ReflectiveOperationException {
        var navigation=mock(com.talhanation.recruits.pathfinding.AsyncGroundPathNavigation.class);
        var evaluator=mock(NodeEvaluator.class);when(navigation.getNodeEvaluator()).thenReturn(evaluator);
        when(mob.getNavigation()).thenReturn(navigation);
        var field=Mob.class.getDeclaredField("goalSelector");field.setAccessible(true);
        field.set(mob,new GoalSelector(()->net.minecraft.util.profiling.InactiveProfiler.INSTANCE));
        RaiderDoors.install(mob);RaiderDoors.install(mob);
        assertEquals(1,mob.goalSelector.getAvailableGoals().size());
        verify(evaluator,times(2)).setCanOpenDoors(true);verify(evaluator,times(2)).setCanPassDoors(true);
        assertTrue(mob.goalSelector.getAvailableGoals().iterator().next().getGoal().getFlags().isEmpty());
    }
    @Test void activeSiegeOpensDoorWithoutClosingItAndHonorsThrottle() throws ReflectiveOperationException {
        var nav=mock(com.talhanation.recruits.pathfinding.AsyncGroundPathNavigation.class);
        when(nav.getNodeEvaluator()).thenReturn(mock(NodeEvaluator.class));when(nav.getPath()).thenReturn(path());
        when(mob.getNavigation()).thenReturn(nav);when(mob.isAlive()).thenReturn(true);
        when(level.getRandom()).thenReturn(net.minecraft.util.RandomSource.create(1));
        when(level.getGameTime()).thenReturn(100L);
        var tag=new net.minecraft.nbt.CompoundTag();tag.putString(com.devfarinsky.siegeoverhaul.ModConstants.Tags.RAID_TEAM,"team:test");
        when(mob.getPersistentData()).thenReturn(tag);
        var field=Mob.class.getDeclaredField("goalSelector");field.setAccessible(true);
        field.set(mob,new GoalSelector(()->net.minecraft.util.profiling.InactiveProfiler.INSTANCE));
        RaiderDoors.install(mob);var goal=mob.goalSelector.getAvailableGoals().iterator().next().getGoal();
        var data=new com.devfarinsky.siegeoverhaul.RaidSavedData();
        data.raids.put("team:test",new com.devfarinsky.siegeoverhaul.RaidSavedData.RaidState("team:test","siege_core",0));
        try(var saves=mockStatic(com.devfarinsky.siegeoverhaul.RaidSavedData.class);
            var claims=mockStatic(com.devfarinsky.siegeoverhaul.compat.ClaimBridge.class)) {
            saves.when(()->com.devfarinsky.siegeoverhaul.RaidSavedData.get(level.getServer())).thenReturn(data);
            assertTrue(goal.canUse());goal.start();assertFalse(goal.canContinueToUse());goal.stop();
            verify(level,times(1)).setBlock(eq(entrance),argThat(state->state.getValue(DoorBlock.OPEN)),eq(10));
            assertFalse(goal.canUse()); // same tick never performs a second scan/open
            when(level.getGameTime()).thenReturn(105L);data.raids.clear();assertFalse(goal.canUse());
        }
    }
    @Test void findsDoorWithoutHorizontalCollisionAndAfterPassingItsNode() {
        assertFalse(mob.horizontalCollision);
        var path=path();assertEquals(entrance,RaiderDoors.find(level,mob,path,p->true));
        path.advance();path.advance();assertEquals(entrance,RaiderDoors.find(level,mob,path,p->true));
        // Opening is left to DoorBlock; selection never destroys or changes blocks.
        verify(level,never()).setBlock(any(),any(),anyInt());
    }
    @Test void refusesIronOpenIncompleteProtectedAndUnloadedDoors() {
        assertNull(RaiderDoors.find(level,mob,path(),p->false));
        door(Blocks.IRON_DOOR,false);assertNull(RaiderDoors.find(level,mob,path(),p->true));
        door(Blocks.OAK_DOOR,true);assertNull(RaiderDoors.find(level,mob,path(),p->true));
        door(Blocks.OAK_DOOR,false);blocks.remove(entrance.above());assertNull(RaiderDoors.find(level,mob,path(),p->true));
        door(Blocks.OAK_DOOR,false);when(level.hasChunkAt(any())).thenReturn(false);
        assertNull(RaiderDoors.find(level,mob,path(),p->true));
    }
    @Test void ignoresUnrelatedDistantAndFinishedRoutes() {
        assertNull(RaiderDoors.find(level,mob,new Path(List.of(new Node(0,64,4)),new BlockPos(0,64,4),true),p->true));
        when(mob.position()).thenReturn(new Vec3(20,64,0));assertNull(RaiderDoors.find(level,mob,path(),p->true));
        var path=path();for(int i=0;i<3;i++)path.advance();assertNull(RaiderDoors.find(level,mob,path,p->true));
    }
}
