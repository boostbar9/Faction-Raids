package com.devfarinsky.siegeoverhaul.camp;

import com.devfarinsky.siegeoverhaul.*;
import com.devfarinsky.siegeoverhaul.compat.*;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CampLayoutRoutingTest extends MinecraftTestSupport {
    private static class Site {
        final ServerLevel level=mock(ServerLevel.class);
        final RaidSavedData.RaidState raid=new RaidSavedData.RaidState("team:test","siege_core",0);
        final RaidSavedData saved=new RaidSavedData();
        final Map<BlockPos,BlockState> world=new HashMap<>();
        boolean solidGround=true;
        final RecruitsClaimsBridge.ClaimSnapshot claim=mock(RecruitsClaimsBridge.ClaimSnapshot.class);
        Site() {
            raid.campPos=new BlockPos(-40,64,-40);raid.campClaimId=UUID.randomUUID();
            // Real gate faces north; the original approach would choose west.
            raid.warGate.putLong("Center",raid.campPos.north(18).asLong());
            raid.warGate.putInt("Facing",Direction.NORTH.get2DDataValue());
            var gate=WarGate.blueprint(raid.campPos.north(18),Direction.NORTH);
            var cells=new net.minecraft.nbt.CompoundTag();
            gate.forEach((key,id)->{
                cells.putString(Long.toString(key),id);
                world.put(BlockPos.of(key),net.minecraftforge.registries.ForgeRegistries.BLOCKS.getValue(new net.minecraft.resources.ResourceLocation(id)).defaultBlockState());
            });
            raid.warGate.put("Blocks",cells);
            UUID id=UUID.randomUUID();raid.campWorkers.add(id);var worker=mock(Mob.class);
            when(level.getEntity(id)).thenReturn(worker);when(worker.isAlive()).thenReturn(true);
            when(level.hasChunkAt(any())).thenReturn(true);when(level.getHeight(any(),anyInt(),anyInt())).thenReturn(64);
            when(level.getMinBuildHeight()).thenReturn(-64);when(level.getMaxBuildHeight()).thenReturn(320);
            when(level.getFluidState(any())).thenReturn(net.minecraft.world.level.material.Fluids.EMPTY.defaultFluidState());
            var border=mock(net.minecraft.world.level.border.WorldBorder.class);when(level.getWorldBorder()).thenReturn(border);
            when(border.isWithinBounds(any(BlockPos.class))).thenReturn(true);
            when(level.getBlockState(any())).thenAnswer(call->{BlockPos p=call.getArgument(0);return world.getOrDefault(p,
                    (p.getY()<64 && solidGround?Blocks.STONE:Blocks.AIR).defaultBlockState());});
            saved.anchors.put(raid.teamKey,new RaidSavedData.Anchor(raid.teamKey,"Test",UUID.randomUUID(),Set.of(),false,false,Map.of(),0));
            when(claim.claimId()).thenReturn(raid.campClaimId);when(claim.ownerFactionStringId()).thenReturn("enemy");
        }
        void ticks(int count,java.util.function.Consumer<Map<Long,String>> accepted) {
            try(var camps=mockStatic(CampClaims.class);
                var jobs=mockStatic(NativeCampConstruction.class);var saves=mockStatic(RaidSavedData.class);
                var claims=mockStatic(RecruitsClaimsBridge.class);var external=mockStatic(ClaimBridge.class)) {
                assertTrue(WarGate.ready(level,raid),"fixture must have a complete, real gate");
                camps.when(()->CampClaims.owns(level,raid)).thenReturn(true);
                saves.when(()->RaidSavedData.get(null)).thenReturn(saved);
                claims.when(()->RecruitsClaimsBridge.getClaimAt(eq(level),any(BlockPos.class))).thenReturn(Optional.of(claim));
                jobs.when(()->NativeCampConstruction.start(level,raid)).thenAnswer(call->{accepted.accept(Map.copyOf(raid.pendingCampBlocks));return true;});
                for(int i=0;i<count;i++) {raid.campUpgradeTicks=RaidConfig.CAMP_UPGRADE_SECONDS.get()*20;CampDevelopment.tick(level,raid);raid.pendingCampBlocks.clear();}
            }
        }
    }

    @Test void blockedWingsCanBuildThreeSeparateCornerPavilionsWithClearGateAvenue() {
        var s=new Site();var occupied=new HashSet<Long>();var commissioned=new ArrayList<BlockPos>();
        for(Direction d:Direction.Plane.HORIZONTAL)for(int radius:new int[]{7,8})
            s.world.put(s.raid.campPos.relative(d,radius).above(),Blocks.STONE.defaultBlockState());
        s.ticks(3,plan->{
            var entry=s.raid.campaign.getCompound(ModConstants.Tags.CAMP_STRUCTURES)
                    .getCompound(CampStructures.Kind.forStage(s.raid.campUpgradeStage).key);
            BlockPos center=BlockPos.of(entry.getLong("Center"));commissioned.add(center);
            assertEquals(7,Math.abs(center.getX()-s.raid.campPos.getX()));
            assertEquals(7,Math.abs(center.getZ()-s.raid.campPos.getZ()));
            assertTrue(plan.size()<512);
            for(var cell:plan.entrySet()) {
                BlockPos p=BlockPos.of(cell.getKey());
                assertTrue(occupied.add(cell.getKey()),"pavilions overlap");
                assertFalse(CampPerimeter.mainApproachColumn(s.raid,p));
                s.world.put(p,net.minecraftforge.registries.ForgeRegistries.BLOCKS.getValue(new net.minecraft.resources.ResourceLocation(cell.getValue())).defaultBlockState());
            }
        });
        assertEquals(3,s.raid.campUpgradeStage);assertEquals(3,new HashSet<>(commissioned).size());
        var reloaded=RaidSavedData.RaidState.load(s.raid.save());
        assertEquals(s.raid.campaign,reloaded.campaign);
        verify(s.level,never()).setBlock(any(),any(),anyInt());
    }

    @Test void unsupportedFoundationsRejectAllSitesWithoutStartingAJob() {
        var s=new Site();s.solidGround=false;
        s.ticks(1,plan->fail("unsupported pavilion accepted"));assertEquals(0,s.raid.campUpgradeStage);
        assertTrue(s.raid.campaign.getCompound(ModConstants.Tags.CAMP_STRUCTURES).isEmpty());
    }

    @Test void supplyBarrelsRespectSavedGateDirectionAndKeepTheAvenueOpen() {
        var s=new Site();
        for(Direction front:Direction.Plane.HORIZONTAL) {
            s.raid.warGate.putInt("PerimeterGateFacing",front.get2DDataValue());s.world.clear();
            // Block every supply site except one in the avenue and one safely behind camp.
            for(Direction d:Direction.Plane.HORIZONTAL)for(int r=2;r<=6;r++)
                s.world.put(s.raid.campPos.relative(d,r),Blocks.STONE.defaultBlockState());
            s.world.remove(s.raid.campPos.relative(front,3));
            BlockPos safe=s.raid.campPos.relative(front.getOpposite(),4);s.world.remove(safe);
            assertEquals(safe,NativeCampConstruction.findSupplyPosition(s.level,s.raid));
        }
    }
}
