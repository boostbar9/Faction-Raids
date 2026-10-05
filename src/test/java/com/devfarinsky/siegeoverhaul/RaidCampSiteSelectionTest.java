package com.devfarinsky.siegeoverhaul;

import com.devfarinsky.siegeoverhaul.compat.CampClaims;
import com.devfarinsky.siegeoverhaul.compat.CorpseCompatibility;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.levelgen.Heightmap;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Runs the production site-selection pipeline and full terrain planner, with native claim API isolated. */
class RaidCampSiteSelectionTest extends MinecraftTestSupport {
    private final ServerLevel level = mock(ServerLevel.class);
    private final RaidSavedData.RaidState raid = new RaidSavedData.RaidState("team:test", "siege_core", 0);
    private final Map<BlockPos,BlockState> edits = new HashMap<>();

    @BeforeEach void ground() {
        raid.campSearchPos = new BlockPos(8,64,8);
        when(level.hasChunk(anyInt(),anyInt())).thenReturn(true);
        when(level.hasChunkAt(any())).thenReturn(true);
        when(level.getWorldBorder()).thenReturn(new WorldBorder());
        when(level.getMinBuildHeight()).thenReturn(-64); when(level.getMaxBuildHeight()).thenReturn(320);
        when(level.getHeight(any(Heightmap.Types.class),anyInt(),anyInt())).thenAnswer(c -> groundY(c.getArgument(1),c.getArgument(2)));
        when(level.getBlockState(any())).thenAnswer(c -> block(c.getArgument(0)));
        when(level.getFluidState(any())).thenAnswer(c -> block(c.getArgument(0)).getFluidState());
        when(level.setBlock(any(),any(),anyInt())).thenAnswer(c -> {
            edits.put(((BlockPos)c.getArgument(0)).immutable(),c.getArgument(1)); return true;
        });
    }
    @AfterEach void releaseReads() { clearInvocations(level); }
    private int groundY(int x,int z) {
        return Set.of(2,8,14).contains(x) && Set.of(2,8,14).contains(z) ? 68 : 64;
    }
    private BlockState block(BlockPos p) {
        return edits.getOrDefault(p,(p.getY()>=groundY(p.getX(),p.getZ()) ? Blocks.AIR
                : p.getY()==groundY(p.getX(),p.getZ())-1 ? Blocks.GRASS_BLOCK : Blocks.DIRT).defaultBlockState());
    }
    private BlockPos select(boolean claimable) throws Exception {
        try(var claims=mockStatic(CampClaims.class);var corpses=mockStatic(CorpseCompatibility.class)) {
            claims.when(() -> CampClaims.canClaim(eq(level),any())).thenReturn(claimable);
            claims.when(() -> CampClaims.footprint(any())).thenReturn(Set.of());
            claims.when(() -> CampClaims.create(eq(level),eq(raid),any())).thenReturn(true);
            var method=RaidEvents.class.getDeclaredMethod("findWarCampPosition",ServerLevel.class,
                    RaidSavedData.Anchor.class,BlockPos.class,double.class,RaidSavedData.RaidState.class);
            method.setAccessible(true);
            return (BlockPos)method.invoke(null,level,null,new BlockPos(-160,64,8),0D,raid);
        }
    }
    @Test void normalScoutingNowTriesAuthoritativeGradingBeforeRejectingFourBlockSoilMounds() throws Exception {
        BlockPos camp=select(true);
        assertNotNull(camp); assertEquals(64,camp.getY()); assertFalse(raid.campTerraformed);
        assertFalse(raid.campBlocks.isEmpty(),"Actual safe grading must execute, not just a boolean acceptance");
        assertTrue(raid.campBlocks.values().stream().anyMatch(t -> t.getCompound("Original").getString("Name").equals("minecraft:grass_block")));
        assertTrue(block(new BlockPos(2,67,2)).isAir());
    }
    @Test void disablingGradingKeepsTheStrictSurfaceRuleAndDoesNotPartlyLevelAnything() throws Exception {
        RaidConfig.LEVEL_CAMP_TERRAIN.set(false);
        assertNull(select(true)); assertTrue(raid.campBlocks.isEmpty());
        verify(level,never()).setBlock(any(),any(),anyInt());
    }
    @Test void protectedContainerInEveryCandidateFootprintStillRejectsWithoutMutation() throws Exception {
        edits.put(new BlockPos(8,68,8),Blocks.CHEST.defaultBlockState());
        assertNull(select(true)); assertTrue(raid.campBlocks.isEmpty());
        assertTrue(block(new BlockPos(8,68,8)).is(Blocks.CHEST));
        verify(level,never()).setBlock(any(),any(),anyInt());
    }
    @Test void claimSafetyStillRejectsEveryOtherwiseGradeableSite() throws Exception {
        assertNull(select(false)); assertTrue(raid.campBlocks.isEmpty());
        verify(level,never()).setBlock(any(),any(),anyInt());
    }
}
