package com.devfarinsky.siegeoverhaul.naval;

import com.devfarinsky.siegeoverhaul.MinecraftTestSupport;
import com.devfarinsky.siegeoverhaul.RaidSavedData;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class NavalAssaultPolicyTest extends MinecraftTestSupport {
    @Test
    void coastalCampRemainsEligibleAndPoseidonCommitsMostOfItsSignatureWave() {
        var raid=new RaidSavedData.RaidState("team:test","siege_core",0);
        raid.factionId="blackbay_reavers";
        raid.campPos=new BlockPos(80,64,80);
        raid.navalStagingPos=new BlockPos(0,63,0);
        raid.navalBeachPos=new BlockPos(20,64,20);
        raid.preparationTicks=0;

        raid.wave=4;
        assertTrue(NavalAssaultPolicy.available(raid));
        assertEquals(20,NavalAssaultPolicy.sharePercent(raid,20,5));

        raid.wave=5;
        assertEquals(75,NavalAssaultPolicy.sharePercent(raid,20,5));
        raid.wave=10;
        assertEquals(75,NavalAssaultPolicy.sharePercent(raid,20,5));
    }

    @Test
    void otherHostsKeepConfiguredShareAndUnavailableRoutesSpawnNoNavalUnits() {
        var raid=new RaidSavedData.RaidState("team:test","siege_core",0);
        raid.factionId="hollowfang_clan";
        raid.campPos=BlockPos.ZERO;
        raid.navalStagingPos=new BlockPos(0,63,0);
        raid.navalBeachPos=new BlockPos(20,64,20);
        raid.wave=5;
        assertEquals(30,NavalAssaultPolicy.sharePercent(raid,30,5));

        raid.preparationTicks=20;
        assertFalse(NavalAssaultPolicy.available(raid));
        assertEquals(0,NavalAssaultPolicy.sharePercent(raid,30,5));
        raid.preparationTicks=0;
        raid.navalBeachPos=null;
        assertFalse(NavalAssaultPolicy.available(raid));
    }
}
