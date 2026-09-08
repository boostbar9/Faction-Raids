package com.devfarinsky.siegeoverhaul.core;
import com.devfarinsky.siegeoverhaul.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class CoreControlTest extends MinecraftTestSupport {
    @Test void aMajorityMustHoldForTheFullTimerRegardlessOfArmySize() {
        int small=0,large=0;
        for(int i=0;i<119;i++) { small=CoreControl.advance(small,2400,2,1); large=CoreControl.advance(large,2400,200,1); }
        assertEquals(2380,small); assertEquals(small,large);
        assertEquals(2400,CoreControl.advance(small,2400,2,1));
    }
    @Test void emptyOrTiedRingCannotCaptureOrRecapture() {
        assertEquals(1200,CoreControl.advance(1200,2400,0,0));
        assertEquals(1200,CoreControl.advance(1200,2400,3,3));
        assertEquals(1180,CoreControl.advance(1200,2400,1,2));
        assertEquals(0,CoreControl.advance(0,2400,0,1));
    }
    @Test void roofAndNearbyOutsideTroopsCannotCount() {
        RaidConfig.CORE_CAPTURE_RADIUS.set(10);
        assertTrue(CoreOccupation.inRing(new Vec3(10.5,.5,.5),BlockPos.ZERO));
        assertFalse(CoreOccupation.inRing(new Vec3(10.6,.5,.5),BlockPos.ZERO));
        assertFalse(CoreOccupation.inRing(new Vec3(.5,4,.5),BlockPos.ZERO));
        assertTrue(CoreOccupation.inRing(new Vec3(.5,3.5,.5),BlockPos.ZERO));
    }
    @Test void occupationAndRecaptureSurviveRaidRemovalAndSaveReload() {
        var data=new RaidSavedData(); var core=new CompoundTag();
        core.putLong("Position",BlockPos.ZERO.asLong()); core.putBoolean("Occupied",true);
        var id=java.util.UUID.randomUUID(); core.putUUID("OccupiedClaim",id); core.putInt("RecaptureTicks",860);
        data.siegeCores.put("team:blue",core);
        var state=new RaidSavedData.RaidState("team:blue","siege_core",0); state.coreCaptured=true;
        data.raids.put(state.teamKey,state);
        var loaded=RaidSavedData.load(data.save(new CompoundTag()));
        assertTrue(loaded.raids.get(state.teamKey).coreCaptured);
        loaded.raids.clear();
        loaded=RaidSavedData.load(loaded.save(new CompoundTag()));
        assertTrue(CoreOccupation.occupied(loaded,"team:blue"));
        assertEquals(id,loaded.siegeCores.get("team:blue").getUUID("OccupiedClaim"));
        assertEquals(860,loaded.siegeCores.get("team:blue").getInt("RecaptureTicks"));
    }
}
