package com.devfarinsky.siegeoverhaul.core;

import com.devfarinsky.siegeoverhaul.MinecraftTestSupport;
import com.devfarinsky.siegeoverhaul.RaidSavedData;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class EndlessSiegeTest extends MinecraftTestSupport {
    @Test void votesRejectOutsidersStaleLinksAndDuplicateBallotsAcrossReload() {
        var voter = UUID.randomUUID(); var other = UUID.randomUUID(); var campaign = new CompoundTag();
        EndlessSiege.begin(campaign, 5, List.of(voter, other), "current");
        assertFalse(EndlessSiege.cast(campaign, UUID.randomUUID(), "current", true));
        assertFalse(EndlessSiege.cast(campaign, voter, "old", true));
        assertTrue(EndlessSiege.cast(campaign, voter, "current", true));
        var state = new RaidSavedData.RaidState("team:test", "siege_core", 0); state.campaign = campaign;
        campaign = RaidSavedData.RaidState.load(state.save()).campaign;
        assertFalse(EndlessSiege.cast(campaign, voter, "current", false));
        assertEquals(EndlessSiege.Decision.WAIT, EndlessSiege.decision(campaign));
        assertTrue(EndlessSiege.cast(campaign, other, "current", false));
        assertEquals(EndlessSiege.Decision.CONTINUE, EndlessSiege.tick(campaign));
        assertFalse(EndlessSiege.cast(campaign, other, "current", true));
    }
    @Test void strictMajorityWinsAndAbstentionTimesOutWithoutEndingSiege() {
        var a=UUID.randomUUID(); var b=UUID.randomUUID(); var c=UUID.randomUUID(); var vote=new CompoundTag();
        EndlessSiege.begin(vote,10,List.of(a,b,c),"ten");
        EndlessSiege.cast(vote,a,"ten",true); EndlessSiege.cast(vote,b,"ten",true);
        assertEquals(EndlessSiege.Decision.RETREAT,EndlessSiege.tick(vote));
        EndlessSiege.begin(vote,15,List.of(a,b,c),"fifteen");
        EndlessSiege.cast(vote,a,"fifteen",true);
        for(int i=0;i<59;i++) assertEquals(EndlessSiege.Decision.WAIT,EndlessSiege.tick(vote));
        assertEquals(EndlessSiege.Decision.CONTINUE,EndlessSiege.tick(vote));
        EndlessSiege.begin(vote,20,List.of(a),"solo"); EndlessSiege.cast(vote,a,"solo",true);
        assertEquals(EndlessSiege.Decision.RETREAT,EndlessSiege.tick(vote));
    }
    @Test void clearedWaveCreditsOnlyOnceAcrossSaveReload() {
        var data=new RaidSavedData(); var state=new RaidSavedData.RaidState("team:test","siege_core",0);
        state.wave=6; state.rewardEligible=true; data.raids.put(state.teamKey,state); data.siegeCores.put(state.teamKey,new CompoundTag());
        long reward=EndlessSiege.award(data,state,1000,100); assertTrue(reward>0);
        data=RaidSavedData.load(data.save(new CompoundTag())); state=data.raids.get("team:test");
        assertEquals(0,EndlessSiege.award(data,state,2000,100));
        assertEquals(reward,FactionBank.balance(data.siegeCores.get(state.teamKey)));
        state.wave=7; assertEquals(reward,EndlessSiege.award(data,state,2000,100));
        assertEquals(2*reward,FactionBank.balance(data.siegeCores.get(state.teamKey)));
    }
    @Test void laterChaptersIncreaseRewardsAndStagedSizeWithoutOverflow() {
        assertEquals(1,EndlessSiege.chapterWave(6)); assertEquals(5,EndlessSiege.chapterWave(10));
        assertTrue(EndlessSiege.reward(6)>EndlessSiege.reward(5));
        assertTrue(EndlessSiege.waveSize(10,6,40)>EndlessSiege.waveSize(10,1,40));
        assertEquals(320,EndlessSiege.waveSize(10,Integer.MAX_VALUE,40));
        assertTrue(EndlessSiege.reward(Integer.MAX_VALUE)>0);
    }
}
