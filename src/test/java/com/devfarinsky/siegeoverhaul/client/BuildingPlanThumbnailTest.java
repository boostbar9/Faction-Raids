package com.devfarinsky.siegeoverhaul.client;

import com.devfarinsky.siegeoverhaul.MinecraftTestSupport;
import com.devfarinsky.siegeoverhaul.core.DefenseBlueprint;
import com.devfarinsky.siegeoverhaul.core.PerimeterBlueprint;
import com.devfarinsky.siegeoverhaul.core.TerritoryFortification;
import com.mojang.math.Axis;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.ChunkPos;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class BuildingPlanThumbnailTest extends MinecraftTestSupport {
    @Test void allSixIllustrationsContainExactlyTheProductionBlueprintBlocksAndMaterials() {
        for (var kind : DefenseBlueprint.Kind.values()) {
            var plan = DefenseBlueprint.create(kind, BlockPos.ZERO, Direction.SOUTH);
            var thumbnail = new BuildingPlanThumbnail(plan.blocks());
            assertEquals(plan.blocks(), thumbnail.sourceBlocks(), kind.label);
            assertFalse(thumbnail.sourceBlocks().isEmpty(), kind.label);
            assertThrows(UnsupportedOperationException.class,
                    () -> thumbnail.sourceBlocks().put(BlockPos.ZERO.asLong(), "minecraft:dirt"));
        }
    }

    @Test void projectedBoundsMatchTheActualRenderPoseIncludingTheInvertedGuiYAxis() {
        var matrix = new Matrix4f().scale(1, -1, 1)
                .rotate(Axis.XP.rotationDegrees(30)).rotate(Axis.YP.rotationDegrees(45));
        for (int x = -5; x <= 5; x++) for (int y = 0; y <= 6; y++) for (int z = -3; z <= 9; z++) {
            var actual = matrix.transformPosition(x, y, z, new Vector3f());
            assertEquals(actual.x(), BuildingPlanThumbnail.projectedX(x, z), 0.00001);
            assertEquals(actual.y(), BuildingPlanThumbnail.projectedY(x, y, z), 0.00001);
        }
    }

    @Test void everyOccupiedCubeCornerFitsBetweenTheTitleDimensionsAndPriceAtEveryIllustratedCardSize() {
        for (var kind : DefenseBlueprint.Kind.values()) {
            var plan = DefenseBlueprint.create(kind, BlockPos.ZERO, Direction.SOUTH);
            var bounds = BuildingPlanThumbnail.bounds(plan.blocks());
            for (int cardWidth = 130; cardWidth <= 360; cardWidth += 13) {
                for (int cardHeight = 84; cardHeight <= 160; cardHeight += 7) {
                    int width = cardWidth - 14, height = cardHeight - 43;
                    var fit = BuildingPlanThumbnail.fit(bounds, width, height);
                    assertTrue(fit.scale() > 0 && fit.scale() <= 12, kind.label);
                    assertTrue(fit.offsetX() + fit.scale() * bounds.minX() >= 1.99999, kind.label);
                    assertTrue(fit.offsetX() + fit.scale() * bounds.maxX() <= width - 1.99999, kind.label);
                    assertTrue(fit.offsetY() + fit.scale() * bounds.minY() >= 1.99999, kind.label);
                    assertTrue(fit.offsetY() + fit.scale() * bounds.maxY() <= height - 1.99999, kind.label);
                    assertTrue(16 + height < cardHeight - 23, "Dimensions stay below the illustration");
                }
            }
        }
    }

    @Test void emptySourceHasFiniteBoundsAndDoesNotRequireInventedGeometry() {
        var thumbnail = new BuildingPlanThumbnail(Map.of());
        assertTrue(thumbnail.sourceBlocks().isEmpty());
        var fit = BuildingPlanThumbnail.fit(thumbnail.bounds(), 100, 40);
        assertTrue(Double.isFinite(fit.scale()));
        assertTrue(Double.isFinite(fit.offsetX()));
        assertTrue(Double.isFinite(fit.offsetY()));
    }

    @Test void perimeterExampleUsesOneFixedFlatClaimAndTheExistingSelectedPalette() {
        var palettes = new PerimeterBlueprint.Palette[] {PerimeterBlueprint.Palette.STONE_BRICKS,
                PerimeterBlueprint.Palette.COBBLESTONE, PerimeterBlueprint.Palette.OAK};
        assertEquals(TerritoryFortification.MATERIALS.length, palettes.length);
        for (int material = 0; material < palettes.length; material++) {
            var expected = PerimeterBlueprint.create(Set.of(new ChunkPos(0, 0)),
                    (x, z) -> PerimeterBlueprint.Surface.ready(0), palettes[material]);
            var example = BuildingPlanThumbnail.perimeterExample(material);
            assertTrue(expected.valid());
            assertEquals(expected.blocks(), example.sourceBlocks());
            assertTrue(example.sourceBlocks().size() < 1000, "Example has a fixed, small render budget");
            var fit = BuildingPlanThumbnail.fit(example.bounds(), 480, 73);
            assertTrue(fit.offsetY() + fit.scale() * example.bounds().minY() >= 1.99999);
            assertTrue(fit.offsetY() + fit.scale() * example.bounds().maxY() <= 71.00001);
        }
    }
}
