package com.devfarinsky.siegeoverhaul.compat;

import com.devfarinsky.siegeoverhaul.MinecraftTestSupport;
import com.devfarinsky.siegeoverhaul.RaidSavedData;
import com.devfarinsky.siegeoverhaul.camp.CampTerrain;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.function.Predicate;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CorpseCompatibilityTest extends MinecraftTestSupport {
    @Test void absentModDoesNotQueryEntitiesOrChangeVanillaBehavior() {
        var level=mock(ServerLevel.class);
        assertFalse(CorpseCompatibility.available());
        assertFalse(CorpseCompatibility.blocksAt(level,BlockPos.ZERO));
        assertFalse(CorpseCompatibility.blocksAny(level,List.of(0L)));
        verifyNoInteractions(level);
    }
    @Test void blueprintUsesOneQueryAndOnlyActualOverlapsPause() {
        var level=mock(ServerLevel.class);var corpse=mock(Entity.class);var other=mock(Entity.class);
        when(corpse.getBoundingBox()).thenReturn(new AABB(0,0,0,1,0.5,1));
        when(other.getBoundingBox()).thenReturn(new AABB(0,0,0,1,1,1));
        try(var bridge=mockStatic(CorpseCompatibility.class,CALLS_REAL_METHODS)) {
            bridge.when(CorpseCompatibility::available).thenReturn(true);
            bridge.when(()->CorpseCompatibility.isCorpse(corpse)).thenReturn(true);
            bridge.when(()->CorpseCompatibility.isCorpse(other)).thenReturn(false);
            when(level.getEntities((Entity)isNull(),any(AABB.class),any())).thenAnswer(call->{
                AABB bounds=call.getArgument(1);Predicate<Entity> filter=call.getArgument(2);
                return List.of(corpse,other).stream().filter(filter).filter(e->e.getBoundingBox().intersects(bounds)).toList();
            });
            assertTrue(CorpseCompatibility.blocksAny(level,List.of(BlockPos.ZERO.asLong(),new BlockPos(4,0,0).asLong())));
            verify(level,times(1)).getEntities((Entity)isNull(),any(AABB.class),any());
            when(corpse.getBoundingBox()).thenReturn(new AABB(2,0,0,3,0.5,1));
            assertFalse(CorpseCompatibility.blocksAny(level,List.of(BlockPos.ZERO.asLong(),new BlockPos(4,0,0).asLong())));
            assertFalse(CorpseCompatibility.blocksAt(level,BlockPos.ZERO));
            verify(corpse,never()).discard();verify(other,never()).discard();
        }
    }
    @Test void earthworkWaitsWithoutMutatingTerrainOrLedgerThenSucceeds() {
        var level=mock(ServerLevel.class);var raid=new RaidSavedData.RaidState("team:test","siege_core",0);
        var plan=new CampTerrain.Plan(List.of(new CampTerrain.Change(BlockPos.ZERO,Blocks.AIR.defaultBlockState(),Blocks.STONE.defaultBlockState())));
        when(level.hasChunkAt(any())).thenReturn(true);when(level.getBlockState(any())).thenReturn(Blocks.AIR.defaultBlockState());
        when(level.setBlock(any(),any(),anyInt())).thenReturn(true);
        try(var bridge=mockStatic(CorpseCompatibility.class)) {
            bridge.when(()->CorpseCompatibility.blocksAny(eq(level),any())).thenReturn(true);
            assertFalse(CampTerrain.apply(level,raid,plan));assertTrue(raid.campBlocks.isEmpty());
            verify(level,never()).setBlock(any(),any(),anyInt());
            bridge.when(()->CorpseCompatibility.blocksAny(eq(level),any())).thenReturn(false);
            assertTrue(CampTerrain.apply(level,raid,plan));assertTrue(raid.campBlocks.containsKey(0L));
            verify(level).setBlock(BlockPos.ZERO,Blocks.STONE.defaultBlockState(),3);
        }
    }
}
