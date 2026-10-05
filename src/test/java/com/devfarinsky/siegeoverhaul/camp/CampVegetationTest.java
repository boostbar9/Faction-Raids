package com.devfarinsky.siegeoverhaul.camp;

import com.devfarinsky.siegeoverhaul.MinecraftTestSupport;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.shapes.Shapes;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CampVegetationTest extends MinecraftTestSupport {
    private static final ResourceLocation SHORT_GRASS = new ResourceLocation("sizeable_foliage", "very_short_grass");
    private static final String SHORT_GRASS_CLASS = "com.craisinlord.sizeablefoliage.content.block.VeryShortGrassBlock";

    @Test void optionalVersionFenceRejectsUnreviewedOrUnavailableRuntimeMetadata() {
        assertTrue(CampVegetation.reviewedOptionalVersion("1.2.1"));
        for (String version : List.of("", "1.2.0", "1.2.2", "2.0.0", "1.2.1-custom"))
            assertFalse(CampVegetation.reviewedOptionalVersion(version), version);
        assertFalse(CampVegetation.reviewedOptionalVersion(null));
        var mods = mock(net.minecraftforge.fml.ModList.class);
        var container = mock(net.minecraftforge.fml.ModContainer.class);
        var info = mock(net.minecraftforge.forgespi.language.IModInfo.class);
        when(container.getModInfo()).thenReturn(info);
        when(info.getVersion()).thenReturn(new org.apache.maven.artifact.versioning.DefaultArtifactVersion("1.2.1"));
        try (var runtime = mockStatic(net.minecraftforge.fml.ModList.class)) {
            assertEquals("", CampVegetation.loadedSizeableVersion());
            runtime.when(net.minecraftforge.fml.ModList::get).thenReturn(mods);
            when(mods.getModContainerById("sizeable_foliage")).thenReturn(java.util.Optional.empty());
            assertEquals("", CampVegetation.loadedSizeableVersion());
            doReturn(java.util.Optional.of(container)).when(mods).getModContainerById("sizeable_foliage");
            assertTrue(CampVegetation.reviewedOptionalVersion(CampVegetation.loadedSizeableVersion()));
            when(info.getVersion()).thenReturn(new org.apache.maven.artifact.versioning.DefaultArtifactVersion("1.2.2"));
            assertFalse(CampVegetation.reviewedOptionalVersion(CampVegetation.loadedSizeableVersion()));
            runtime.when(net.minecraftforge.fml.ModList::get).thenThrow(new IllegalStateException("Unavailable metadata"));
            assertEquals("", CampVegetation.loadedSizeableVersion());
        }
    }

    @Test void optionalCompatibilityRequiresTheReviewedRegistryIdAndImplementation() {
        assertTrue(CampVegetation.reviewedOptionalType(SHORT_GRASS, SHORT_GRASS_CLASS));
        for (String path : List.of("very_tall_grass", "very_large_fern", "big_bush", "big_bush_part",
                "torchflower_bush", "fern_wall", "big_sweet_berry_bush", "big_sweet_berry_bush_part"))
            assertFalse(CampVegetation.reviewedOptionalType(new ResourceLocation("sizeable_foliage", path), SHORT_GRASS_CLASS));
        assertFalse(CampVegetation.reviewedOptionalType(new ResourceLocation("other_mod", "very_short_grass"), SHORT_GRASS_CLASS));
        assertFalse(CampVegetation.reviewedOptionalType(SHORT_GRASS, "other.mod.VeryShortGrassBlock"));
        assertFalse(CampVegetation.reviewedOptionalType(SHORT_GRASS, Blocks.GRASS.getClass().getName()));
        assertFalse(CampVegetation.reviewedOptionalType(null, SHORT_GRASS_CLASS));
    }

    @Test void reviewedGrassMustRetainItsOneCellHarmlessVanillaGrassProperties() {
        // Structural replica only: the opt-in native QA loads the real addon implementation.
        assertTrue(CampVegetation.singleCellGrassShape(Blocks.GRASS.defaultBlockState()));
        for (var block : List.of(Blocks.STONE, Blocks.DIRT, Blocks.OAK_LOG, Blocks.OAK_PLANKS,
                Blocks.CHEST, Blocks.WHEAT, Blocks.SWEET_BERRY_BUSH, Blocks.WITHER_ROSE,
                Blocks.TALL_GRASS, Blocks.LARGE_FERN, Blocks.OAK_SAPLING, Blocks.WATER,
                Blocks.LAVA, Blocks.COBWEB, Blocks.SNOW, Blocks.FIRE, Blocks.POPPY))
            assertFalse(CampVegetation.singleCellGrassShape(block.defaultBlockState()), block.toString());
        BlockState changed = spy(Blocks.GRASS.defaultBlockState());
        doReturn(true).when(changed).hasBlockEntity();
        assertFalse(CampVegetation.singleCellGrassShape(changed));
        doReturn(false).when(changed).hasBlockEntity();
        doReturn(Fluids.WATER.defaultFluidState()).when(changed).getFluidState();
        assertFalse(CampVegetation.singleCellGrassShape(changed));
        doReturn(Fluids.EMPTY.defaultFluidState()).when(changed).getFluidState();
        doReturn(Shapes.block()).when(changed).getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
        assertFalse(CampVegetation.singleCellGrassShape(changed));
        doReturn(Shapes.empty()).when(changed).getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
        doReturn(1.0F).when(changed).getDestroySpeed(EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
        assertFalse(CampVegetation.singleCellGrassShape(changed));
        doReturn(0.0F).when(changed).getDestroySpeed(EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
        doReturn(false).when(changed).canBeReplaced();
        assertFalse(CampVegetation.singleCellGrassShape(changed));
    }

    @Test void missingOptionalModDoesNotRegisterBlocksOrBroadenVanillaClearing() {
        assertFalse(BuiltInRegistries.BLOCK.containsKey(SHORT_GRASS));
        for (var block : List.of(Blocks.GRASS, Blocks.FERN, Blocks.DEAD_BUSH, Blocks.POPPY))
            assertTrue(CampVegetation.singleCellPlant(block.defaultBlockState()), block.toString());
        for (var block : List.of(Blocks.TALL_GRASS, Blocks.LARGE_FERN, Blocks.SUNFLOWER, Blocks.WITHER_ROSE,
                Blocks.WHEAT, Blocks.CHEST, Blocks.WATER, Blocks.LAVA, Blocks.OAK_LOG, Blocks.STONE))
            assertFalse(CampVegetation.singleCellPlant(block.defaultBlockState()), block.toString());
        assertFalse(CampVegetation.singleCellPlant(null));
        assertFalse(CampVegetation.reviewedOptionalPlant(Blocks.GRASS.defaultBlockState(), SHORT_GRASS));
        assertFalse(BuiltInRegistries.BLOCK.containsKey(SHORT_GRASS));
    }
}
