package com.devfarinsky.siegeoverhaul.client;

import com.devfarinsky.siegeoverhaul.core.PerimeterBlueprint;
import com.devfarinsky.siegeoverhaul.core.TerritoryFortification;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.ChunkPos;
import net.minecraftforge.client.model.data.ModelData;
import org.joml.Vector3f;

import java.util.List;
import java.util.Map;
import java.util.Set;

/** Read-only GUI illustration of the exact supplied blueprint, using current Minecraft block models. */
final class BuildingPlanThumbnail {
    private static final float PITCH = 30, YAW = 45;
    private static final double SIN_YAW = Math.sin(Math.toRadians(YAW));
    private static final double COS_YAW = Math.cos(Math.toRadians(YAW));
    private static final double SIN_PITCH = Math.sin(Math.toRadians(PITCH));
    private static final double COS_PITCH = Math.cos(Math.toRadians(PITCH));
    private final Map<Long, String> sourceBlocks;
    private final List<Voxel> voxels;
    private final Bounds bounds;

    BuildingPlanThumbnail(Map<Long, String> blocks) {
        sourceBlocks = Map.copyOf(blocks);
        voxels = sourceBlocks.entrySet().stream().map(entry -> new Voxel(BlockPos.of(entry.getKey()),
                BuiltInRegistries.BLOCK.get(new ResourceLocation(entry.getValue())).defaultBlockState())).toList();
        bounds = bounds(sourceBlocks);
    }

    Map<Long, String> sourceBlocks() { return sourceBlocks; }
    Bounds bounds() { return bounds; }

    /** Fixed illustrative input only; no world, player, live claim, or site validation is read here. */
    static BuildingPlanThumbnail perimeterExample(int material) {
        var palette = new PerimeterBlueprint.Palette(TerritoryFortification.material(material).blockId(),
                "minecraft:oak_planks", "minecraft:dirt");
        var plan = PerimeterBlueprint.create(Set.of(new ChunkPos(0, 0)),
                (x, z) -> PerimeterBlueprint.Surface.ready(0), palette);
        return new BuildingPlanThumbnail(plan.blocks());
    }

    void render(GuiGraphics graphics, int x, int y, int width, int height) {
        if (voxels.isEmpty() || width <= 4 || height <= 4) return;
        Fit fit = fit(bounds, width, height);
        var pose = graphics.pose();
        // GuiGraphics scissor coordinates are screen coordinates, so include the command frame's
        // exceptional tiny-window scale rather than clipping against unscaled widget coordinates.
        var topLeft = pose.last().pose().transformPosition(x, y, 0, new Vector3f());
        var bottomRight = pose.last().pose().transformPosition(x + width, y + height, 0, new Vector3f());
        graphics.flush();
        graphics.enableScissor((int) Math.floor(topLeft.x()), (int) Math.floor(topLeft.y()),
                (int) Math.ceil(bottomRight.x()), (int) Math.ceil(bottomRight.y()));
        pose.pushPose();
        try {
            pose.translate(x + fit.offsetX(), y + fit.offsetY(), 150);
            pose.scale((float) fit.scale(), (float) -fit.scale(), (float) fit.scale());
            pose.mulPose(Axis.XP.rotationDegrees(PITCH));
            pose.mulPose(Axis.YP.rotationDegrees(YAW));
            Lighting.setupFor3DItems();
            RenderSystem.enableDepthTest();
            var renderer = Minecraft.getInstance().getBlockRenderer();
            var buffers = graphics.bufferSource();
            for (Voxel voxel : voxels) {
                pose.pushPose();
                pose.translate(voxel.position().getX(), voxel.position().getY(), voxel.position().getZ());
                // Resolve baked models each frame: resource-pack reloads stay authoritative.
                renderer.renderSingleBlock(voxel.state(), pose, buffers, 0xF000F0,
                        OverlayTexture.NO_OVERLAY, ModelData.EMPTY, null);
                pose.popPose();
            }
            buffers.endBatch();
        } finally {
            pose.popPose();
            graphics.disableScissor();
            Lighting.setupFor3DItems();
        }
    }

    /** Project every occupied cube corner, not the larger empty clearance volume. */
    static Bounds bounds(Map<Long, String> blocks) {
        double minX = Double.POSITIVE_INFINITY, minY = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY, maxY = Double.NEGATIVE_INFINITY;
        for (long cell : blocks.keySet()) {
            var position = BlockPos.of(cell);
            for (int dx = 0; dx <= 1; dx++) for (int dy = 0; dy <= 1; dy++) for (int dz = 0; dz <= 1; dz++) {
                double x = position.getX() + dx, y = position.getY() + dy, z = position.getZ() + dz;
                double projectedX = projectedX(x, z), projectedY = projectedY(x, y, z);
                minX = Math.min(minX, projectedX); maxX = Math.max(maxX, projectedX);
                minY = Math.min(minY, projectedY); maxY = Math.max(maxY, projectedY);
            }
        }
        return blocks.isEmpty() ? new Bounds(0, 0, 1, 1) : new Bounds(minX, minY, maxX, maxY);
    }

    static double projectedX(double x, double z) { return COS_YAW * x + SIN_YAW * z; }
    static double projectedY(double x, double y, double z) {
        return -COS_PITCH * y - SIN_PITCH * SIN_YAW * x + SIN_PITCH * COS_YAW * z;
    }

    static Fit fit(Bounds bounds, int width, int height) {
        double scale = Math.max(0, Math.min(12, Math.min((width - 4) / (bounds.maxX() - bounds.minX()),
                (height - 4) / (bounds.maxY() - bounds.minY()))));
        return new Fit(scale, width / 2.0 - scale * (bounds.minX() + bounds.maxX()) / 2,
                height / 2.0 - scale * (bounds.minY() + bounds.maxY()) / 2);
    }

    record Bounds(double minX, double minY, double maxX, double maxY) {}
    record Fit(double scale, double offsetX, double offsetY) {}
    private record Voxel(BlockPos position, BlockState state) {}
}
