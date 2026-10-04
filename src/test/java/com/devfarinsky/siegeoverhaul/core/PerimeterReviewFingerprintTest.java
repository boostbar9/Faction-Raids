package com.devfarinsky.siegeoverhaul.core;

import com.devfarinsky.siegeoverhaul.MinecraftTestSupport;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class PerimeterReviewFingerprintTest extends MinecraftTestSupport {
    private final UUID owner=UUID.randomUUID(), builder=UUID.randomUUID();
    private final BlockPos core=new BlockPos(8,64,8);
    private final PerimeterBlueprint.Plan plan=PerimeterBlueprint.create(Set.of(new ChunkPos(0,0)),
            (x,z)->PerimeterBlueprint.Surface.ready(63),PerimeterBlueprint.Palette.COBBLESTONE,
            new PerimeterBlueprint.Limits(64,32768,20480,32768,4,-64,320,256,1048576));
    private final PerimeterStageLayout.Layout layout=PerimeterStageLayout.partition(plan, stage->null);
    private Map<Long,BlockState> before() { var map=new LinkedHashMap<Long,BlockState>(); plan.blocks().keySet().forEach(p->map.put(p,Blocks.AIR.defaultBlockState())); return map; }
    private Map<Long,BlockState> clearance() { var map=new LinkedHashMap<Long,BlockState>(); plan.clearance().forEach(p->map.put(p,Blocks.AIR.defaultBlockState())); return map; }
    private String hash(Map<Long,BlockState> before,Map<Long,BlockState> clear,UUID assigned) {
        return PerimeterReviewFingerprint.create(plan,layout,before,clear,core,1,"team:blue:[0]",owner,assigned);
    }
    @Test void deterministicAcrossMapOrderButOriginalPropertiesAndAssignmentChangeReview() {
        var before=before(); var clear=clearance(); String original=hash(before,clear,builder);
        var keys=new ArrayList<>(before.keySet()); Collections.reverse(keys);
        var reverse=new LinkedHashMap<Long,BlockState>(); keys.forEach(p->reverse.put(p,before.get(p)));
        assertEquals(original,hash(reverse,clear,builder));
        assertNotEquals(original,hash(before,clear,UUID.randomUUID()));
        long cell=keys.get(0); before.put(cell,Blocks.SNOW.defaultBlockState().setValue(BlockStateProperties.LAYERS,1));
        String one=hash(before,clear,builder); assertNotEquals(original,one);
        before.put(cell,Blocks.SNOW.defaultBlockState().setValue(BlockStateProperties.LAYERS,2));
        assertNotEquals(one,hash(before,clear,builder));
    }
    @Test void solidLegacyPreviewFingerprintCannotAuthorizeChangedHollowGeometry() {
        var old = LegacySolidPerimeterFixture.plan();
        var fresh = PerimeterStageLayoutTest.flat(Set.of(new ChunkPos(0, 0)));
        assertNotEquals(PerimeterPreview.fingerprint(old.blocks(), core, 1, "claim"),
                PerimeterPreview.fingerprint(fresh.blocks(), core, 1, "claim"));
        var oldBefore = new LinkedHashMap<Long, BlockState>(); var oldClear = new LinkedHashMap<Long, BlockState>();
        var newBefore = new LinkedHashMap<Long, BlockState>(); var newClear = new LinkedHashMap<Long, BlockState>();
        old.blocks().keySet().forEach(p -> oldBefore.put(p, Blocks.AIR.defaultBlockState()));
        old.clearance().forEach(p -> oldClear.put(p, Blocks.AIR.defaultBlockState()));
        fresh.blocks().keySet().forEach(p -> newBefore.put(p, Blocks.AIR.defaultBlockState()));
        fresh.clearance().forEach(p -> newClear.put(p, Blocks.AIR.defaultBlockState()));
        assertNotEquals(PerimeterReviewFingerprint.create(old, PerimeterStageLayout.partition(old, s -> null),
                        oldBefore, oldClear, core, 1, "claim", owner, builder),
                PerimeterReviewFingerprint.create(fresh, PerimeterStageLayout.partition(fresh, s -> null),
                        newBefore, newClear, core, 1, "claim", owner, builder));
    }
    @Test void ChangedHeadroomAndIncompleteBaselinesCannotRetainAcceptance() {
        var before=before(); var clear=clearance(); String original=hash(before,clear,builder);
        assertFalse(clear.isEmpty()); long cell=clear.keySet().iterator().next();
        clear.put(cell,Blocks.DANDELION.defaultBlockState()); assertNotEquals(original,hash(before,clear,builder));
        clear.remove(cell); assertThrows(IllegalArgumentException.class,()->hash(before,clear,builder));
        before.remove(before.keySet().iterator().next()); assertThrows(IllegalArgumentException.class,()->hash(before,clearance(),builder));
    }
}
