package com.devfarinsky.siegeoverhaul.camp;
import com.devfarinsky.siegeoverhaul.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.material.Fluids;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class CampBannerPlacementTest extends MinecraftTestSupport {
    @Test void reportedDefenderRoofCannotReceiveACampBanner() {
        var raid=new RaidSavedData.RaidState("team:test","siege_core",0);
        raid.campPos=new BlockPos(-854,73,-95);
        var level=mock(ServerLevel.class);
        assertFalse(CampBannerPlacement.canPlace(level,raid,new BlockPos(-828,80,-149)));
        verifyNoInteractions(level);
    }
    @Test void existingBannerIsProtectedButAnEmptyCampLocationIsAllowed() {
        var raid=new RaidSavedData.RaidState("team:test","siege_core",0);
        raid.campPos=new BlockPos(-854,73,-95);
        BlockPos pos=new BlockPos(-850,74,-95);
        var level=mock(ServerLevel.class); var border=mock(WorldBorder.class);
        when(level.hasChunkAt(pos)).thenReturn(true);
        when(level.getWorldBorder()).thenReturn(border);
        when(border.isWithinBounds(pos)).thenReturn(true);
        when(level.getBlockState(pos)).thenReturn(Blocks.BLACK_BANNER.defaultBlockState());
        assertFalse(CampBannerPlacement.canPlace(level,raid,pos));
        verify(level,never()).getBlockEntity(pos);
        when(level.getBlockState(pos)).thenReturn(Blocks.AIR.defaultBlockState());
        when(level.getFluidState(pos)).thenReturn(Fluids.EMPTY.defaultFluidState());
        assertTrue(CampBannerPlacement.canPlace(level,raid,pos));
    }
}
