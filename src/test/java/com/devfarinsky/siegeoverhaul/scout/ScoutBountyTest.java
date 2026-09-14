package com.devfarinsky.siegeoverhaul.scout;

import com.devfarinsky.siegeoverhaul.MinecraftTestSupport;
import com.devfarinsky.siegeoverhaul.RaidSavedData;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScoutBountyTest extends MinecraftTestSupport {

    @Test
    void scoutPayoutCarriesIntoTheFollowingRaidCap() {
        RaidSavedData data = new RaidSavedData();
        ScoutMission mission = new ScoutMission("team:test", 10, 20, null);
        mission.bountyPaid = 24;
        data.scoutMissions.put(mission.teamKey, mission);
        RaidSavedData.RaidState raid = new RaidSavedData.RaidState("team:test", "siege_core", 0);
        raid.campaign.putInt("BountyPaid", 3);

        assertNull(ScoutManager.consumePreviewedNarrative(data, mission.teamKey, raid));
        assertEquals(27, raid.campaign.getInt("BountyPaid"));
        assertFalse(data.scoutMissions.containsKey(mission.teamKey));
    }

    @Test
    void scoutBountyProgressSurvivesSaveAndReload() {
        ScoutMission mission = new ScoutMission("team:test", 10, 20, null);
        mission.bountyPaid = 18;

        assertEquals(18, ScoutMission.load(mission.save()).bountyPaid);
    }

    @Test
    void nonRewardingManualRaidAndNonFactionKillsSuppressScoutBounties() {
        RaidSavedData data = new RaidSavedData();
        assertTrue(ScoutManager.scoutBountyEligible(data, "team:test", true));
        assertFalse(ScoutManager.scoutBountyEligible(data, "team:test", false));

        RaidSavedData.RaidState manual = new RaidSavedData.RaidState("team:test", "siege_core", 0);
        manual.rewardEligible = false;
        data.raids.put(manual.teamKey, manual);
        assertFalse(ScoutManager.scoutBountyEligible(data, manual.teamKey, true));

        manual.rewardEligible = true;
        assertTrue(ScoutManager.scoutBountyEligible(data, manual.teamKey, true));
    }
}
