package com.devfarinsky.siegeoverhaul.camp;
import com.devfarinsky.siegeoverhaul.RaidSavedData;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class GuardPostTest extends com.devfarinsky.siegeoverhaul.MinecraftTestSupport {
    @Test void sanctuarySentriesFollowSavedCoreAndLeaveItsFrontClear() {
        for(int degrees=0;degrees<360;degrees+=45) {
            var raid=new RaidSavedData.RaidState("team:test","siege_core",0);raid.campPos=new BlockPos(100,64,100);raid.approachAngle=Math.toRadians(degrees);
            var core=raid.campPos.offset(2,1,-2);raid.campaign.putLong("EnemyCore",core.asLong());
            var front=CampPerimeter.mainGateSide(raid);var side=front.getClockWise();
            assertEquals(core.relative(front.getOpposite()).relative(side),CampGuards.candidates(raid,2).get(0));
            assertEquals(core.relative(front.getOpposite()).relative(side.getOpposite()),CampGuards.candidates(raid,3).get(0));
            assertNotEquals(CampGuards.candidates(raid,2).get(0),CampGuards.candidates(raid,3).get(0));
            var moved=core.east(3);raid.campaign.putLong("EnemyCore",moved.asLong());
            assertEquals(moved.relative(front.getOpposite()).relative(side),CampGuards.candidates(raid,2).get(0));
        }
    }
}
