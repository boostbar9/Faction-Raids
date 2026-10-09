package com.devfarinsky.siegeoverhaul.core;

import com.talhanation.workers.entities.BuilderEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.LinkedHashMap;
import java.util.Map;

/** Real loaded-world destination queries only; not a timed recovery or commissioned-work playtest. */
public final class NativeBuilderRecoveryServerContracts {
    private NativeBuilderRecoveryServerContracts() {}

    public static Map<String, Object> verify(ServerLevel level, BlockPos center) {
        int top=level.getMinBuildHeight();
        for (int x=-4;x<=4;x++) for (int z=-4;z<=4;z++) {
            BlockPos p=center.offset(x,0,z);
            require(level.hasChunkAt(p),"Recovery fixture must not load chunks");
            top=Math.max(top,level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,p.getX(),p.getZ()));
        }
        BlockPos feet=center.atY(top+8);
        require(feet.getY()+3<level.getMaxBuildHeight(),"Recovery fixture lacks bounded sky clearance");
        Map<BlockPos, BlockState> original=new LinkedHashMap<>();
        for (int x=-4;x<=4;x++) for (int z=-4;z<=4;z++) for (int y=-1;y<=1;y++) {
            BlockPos p=feet.offset(x,y,z); original.put(p,level.getBlockState(p));
        }
        var type=ForgeRegistries.ENTITY_TYPES.getValue(new ResourceLocation("workers","builder"));
        require(type!=null,"Actual Workers builder factory missing");
        var entity=type.create(level);
        require(entity instanceof BuilderEntity,"Actual Workers builder factory unsupported");
        BuilderEntity builder=(BuilderEntity)entity;
        builder.moveTo(Vec3.atBottomCenterOf(feet.below(4)));
        Vec3 start=builder.position();
        Map<String,Object> report=new LinkedHashMap<>();
        try {
            for (int x=-4;x<=4;x++) for (int z=-4;z<=4;z++)
                level.setBlock(feet.offset(x,-1,z),Blocks.DIRT.defaultBlockState(),2);
            fillBody(level,feet,Blocks.AIR.defaultBlockState(),Blocks.AIR.defaultBlockState());
            require(feet.equals(BuilderGroundRecovery.findSurface(level,builder)),"Actual clear platform not admitted");
            report.put("airControl","passed");

            fillBody(level,feet,Blocks.WITHER_ROSE.defaultBlockState(),Blocks.AIR.defaultBlockState());
            require(level.getBlockState(feet).getCollisionShape(level,feet).isEmpty(),"Vanilla rose unexpectedly collides");
            require(BuilderGroundRecovery.findSurface(level,builder)==null,"Rose-filled platform admitted");
            report.put("allFeetRosesRejected","passed");

            fillBody(level,feet,Blocks.AIR.defaultBlockState(),Blocks.AIR.defaultBlockState());
            level.setBlock(feet,Blocks.WITHER_ROSE.defaultBlockState(),2);
            require(level.getBlockState(feet).is(Blocks.WITHER_ROSE),"Nearest-foot fixture disappeared");
            requireNearestSafeNeighbor(level,builder,feet);
            report.put("nearestFeetRoseRejected","passed");

            level.setBlock(feet,Blocks.AIR.defaultBlockState(),2);
            // Explicit synthetic head-cell fixture; no claim that a floating rose grows naturally.
            level.setBlock(feet.above(),Blocks.WITHER_ROSE.defaultBlockState(),
                    Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
            require(level.getBlockState(feet.above()).is(Blocks.WITHER_ROSE),"Upper-body fixture disappeared");
            requireNearestSafeNeighbor(level,builder,feet);
            report.put("upperBodyRoseRejected","passed");

            fillBody(level,feet,Blocks.POPPY.defaultBlockState(),Blocks.AIR.defaultBlockState());
            require(feet.equals(BuilderGroundRecovery.findSurface(level,builder)),"Harmless native flower was rejected");
            report.put("poppyControl","passed");
            var lower=Blocks.TALL_GRASS.defaultBlockState();
            fillBody(level,feet,lower,lower.setValue(DoublePlantBlock.HALF,DoubleBlockHalf.UPPER));
            require(feet.equals(BuilderGroundRecovery.findSurface(level,builder)),"Harmless paired native grass was rejected");
            report.put("tallGrassControl","passed");
            var sunflower=Blocks.SUNFLOWER.defaultBlockState();
            fillBody(level,feet,sunflower,sunflower.setValue(DoublePlantBlock.HALF,DoubleBlockHalf.UPPER));
            require(feet.equals(BuilderGroundRecovery.findSurface(level,builder)),"Harmless paired native flower was rejected");
            report.put("sunflowerControl","passed");
            require(start.equals(builder.position()),"Destination query moved native worker");
            report.put("workerUnmoved","passed");
            report.put("scope","Direct destination queries with a real Workers entity and vanilla block states in a loaded GameTest world; no timed teleport, native AI construction or authenticated multiplayer claim.");
            return report;
        } finally {
            for (var cell:original.entrySet()) level.setBlock(cell.getKey(),cell.getValue(),2);
            builder.discard();
        }
    }

    private static void fillBody(ServerLevel level,BlockPos feet,BlockState lower,BlockState upper) {
        for (int x=-4;x<=4;x++) for (int z=-4;z<=4;z++) {
            level.setBlock(feet.offset(x,0,z),lower,2);
            level.setBlock(feet.offset(x,1,z),upper,2);
        }
        // Neighbor shape updates can remove plants on invalid footing. Verify the real scene,
        // so an accidentally emptied fixture cannot masquerade as a harmless-plant success.
        for (int x=-4;x<=4;x++) for (int z=-4;z<=4;z++) {
            BlockPos p=feet.offset(x,0,z);
            require(level.getBlockState(p).equals(lower),"Lower recovery fixture changed at "+p);
            require(level.getBlockState(p.above()).equals(upper),"Upper recovery fixture changed at "+p.above());
        }
    }

    private static void requireNearestSafeNeighbor(ServerLevel level,BuilderEntity builder,BlockPos feet) {
        BlockPos selected=BuilderGroundRecovery.findSurface(level,builder);
        require(selected!=null && selected.getY()==feet.getY()
                && Math.abs(selected.getX()-feet.getX())+Math.abs(selected.getZ()-feet.getZ())==1,
                "Recovery failed to select an adjacent rose-free site");
    }
    private static void require(boolean value,String message) {
        if (!value) throw new IllegalStateException(message);
    }
}
