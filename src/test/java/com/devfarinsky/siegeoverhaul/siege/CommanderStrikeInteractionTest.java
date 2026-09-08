package com.devfarinsky.siegeoverhaul.siege;
import com.devfarinsky.siegeoverhaul.*;
import com.devfarinsky.siegeoverhaul.core.SiegeCore;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.ai.control.LookControl;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.*;
import net.minecraftforge.event.ForgeEventFactory;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
class CommanderStrikeInteractionTest extends MinecraftTestSupport {
    @Test void damageInterruptsBeforeAnyBlockMutationAndStartsCooldown(){runStrike(true);}
    @Test void uninterruptedStrikeSnapshotsExactlyOneReachableBlock(){runStrike(false);}
    private void runStrike(boolean interrupt) {
        ServerLevel level=mock(ServerLevel.class);MinecraftServer server=mock(MinecraftServer.class);Mob mob=mock(Mob.class);
        CompoundTag tag=new CompoundTag();tag.putString(ModConstants.Tags.RAID_TEAM,"team:test");tag.putString(ModConstants.Tags.RAID_ROLE,"commander");
        var data=new RaidSavedData();var raid=new RaidSavedData.RaidState("team:test","siege_core",0);raid.wave=5;data.raids.put(raid.teamKey,raid);
        BlockPos wall=new BlockPos(1,1,0);Vec3 eye=new Vec3(.5,1.6,.5);
        when(mob.level()).thenReturn(level);when(mob.getPersistentData()).thenReturn(tag);when(mob.isAlive()).thenReturn(true);
        when(mob.position()).thenReturn(new Vec3(.5,0,.5));when(mob.getEyePosition()).thenReturn(eye);when(mob.blockPosition()).thenReturn(BlockPos.ZERO);
        when(mob.getNavigation()).thenReturn(mock(PathNavigation.class));when(mob.getLookControl()).thenReturn(mock(LookControl.class));
        when(level.getServer()).thenReturn(server);when(level.getGameTime()).thenReturn(100L);when(level.players()).thenReturn(java.util.List.of());
        when(level.hasChunkAt(any())).thenReturn(true);when(level.getFluidState(any())).thenReturn(Fluids.EMPTY.defaultFluidState());
        when(level.getBlockState(any())).thenAnswer(call->wall.equals(call.getArgument(0))?Blocks.STONE_BRICKS.defaultBlockState():Blocks.AIR.defaultBlockState());
        when(level.clip(any(ClipContext.class))).thenReturn(new BlockHitResult(Vec3.atCenterOf(wall),Direction.WEST,wall,false));
        try(var saves=mockStatic(RaidSavedData.class);var core=mockStatic(SiegeCore.class);var grief=mockStatic(ForgeEventFactory.class)) {
            saves.when(()->RaidSavedData.get(server)).thenReturn(data);
            core.when(()->SiegeCore.point(server,raid.teamKey)).thenReturn(new RaidSavedData.DefensePoint("siege_core",Level.OVERWORLD.location(),new BlockPos(10,0,0)));
            core.when(()->SiegeCore.claimed(eq(level),any(),eq(raid.teamKey))).thenReturn(true);
            grief.when(()->ForgeEventFactory.getMobGriefingEvent(level,mob)).thenReturn(true);
            var goal=new CommanderWallStrikeGoal(mob);assertTrue(goal.canUse());goal.start();
            for(int i=0;i<59;i++)goal.tick();
            verify(level,never()).setBlock(any(),any(),anyInt());
            if(interrupt) {
                tag.putLong("SiegeBossHurtAt",1);assertFalse(goal.canContinueToUse());goal.tick();
                verify(level,never()).setBlock(any(),any(),anyInt());assertTrue(raid.breachedBlocks.isEmpty());
            } else {
                goal.tick();verify(level).setBlock(eq(wall),eq(Blocks.AIR.defaultBlockState()),anyInt());
                assertEquals(1,raid.breachedBlocks.size());assertEquals("minecraft:stone_bricks",raid.breachedBlocks.get(wall.asLong()).getString("Name"));
            }
            goal.stop();assertFalse(tag.getBoolean(CommanderWallStrikeGoal.CHARGING));assertEquals(300,tag.getLong("SiegeBossNextStrike"));
        }
    }
}
