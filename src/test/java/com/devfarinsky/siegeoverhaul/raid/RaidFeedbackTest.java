package com.devfarinsky.siegeoverhaul.raid;

import com.devfarinsky.siegeoverhaul.MinecraftTestSupport;
import com.devfarinsky.siegeoverhaul.RaidSavedData;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RaidFeedbackTest extends MinecraftTestSupport {
    private RaidSavedData.RaidState raid() {
        return new RaidSavedData.RaidState("team:test", "siege_core", 0);
    }

    @Test void stoppedOrWithdrawnSiegeDoesNotClaimTheCoreWasCaptured() {
        assertEquals("Siege Ended", RaidFeedback.outcomeTitle(false));
        assertEquals("See chat for the siege outcome", RaidFeedback.outcomeSubtitle(false));
        assertFalse(RaidFeedback.outcomeSubtitle(false).contains("seized"));
        assertEquals("Siege Broken", RaidFeedback.outcomeTitle(true));
        assertEquals("Your faction held the line", RaidFeedback.outcomeSubtitle(true));
    }

    @Test void activeSearchAndTerrainWaitDoNotClaimThatACampExists() {
        var state = raid();
        assertEquals("Searching for camp land", RaidFeedback.phase(state, false, true, true));
        state.campSearchPos = new BlockPos(160, 64, 0);
        assertEquals("Waiting for camp terrain", RaidFeedback.phase(state, false, true, true));
        assertNull(state.campPos);
    }

    @Test void regroupingAndWiderRecoveryNeverClaimThatPreparationHasStarted() {
        var state = raid(); state.preparationTicks = 3600;
        state.campSearchRecovery = true; state.campSearchRetryTicks = 1200;
        assertEquals("Regrouping camp scouts", RaidFeedback.phase(state,false,true,true));
        state.campSearchRetryTicks = 0;
        assertEquals("Searching farther for camp land", RaidFeedback.phase(state,false,true,true));
        state.campSearchPos = new BlockPos(608,64,8);
        assertEquals("Waiting for farther camp terrain", RaidFeedback.phase(state,false,true,true));
        assertEquals(3600,state.preparationTicks); assertFalse(state.campSearchAbandoned);
    }

    @Test void abandonedSearchBecomesCamplessPreparationThenAnAssault() {
        var state = raid();
        state.campSearchAbandoned = true;
        state.preparationTotalTicks = 3600;
        state.preparationTicks = 3600;
        assertEquals("Preparing assault • 3m preparation left", RaidFeedback.phase(state, false, true, true));
        state.preparationTicks = 0;
        state.wave = 1;
        assertEquals("Defend core", RaidFeedback.phase(state, false, true, true));
        assertNull(state.campPos);
        assertTrue(state.campSearchAbandoned);
    }

    @Test void disabledCampsNeverShowAWorkSiteOrAnActiveSearch() {
        var state = raid();
        state.preparationTicks = 1200;
        assertEquals("Preparing assault • 1m preparation left", RaidFeedback.phase(state, false, false, true));
        state.preparationTicks = 0;
        assertEquals("Rally", RaidFeedback.phase(state, false, false, true));
    }

    @Test void onlyEstablishedCampsUseCampConstructionLabels() {
        var state = raid();
        state.campPos = new BlockPos(160, 64, 0);
        state.preparationTotalTicks = 3600;
        state.preparationTicks = 3600;
        assertEquals("Establishing camp", RaidFeedback.preparation(state));
        state.preparationTicks = 2400;
        assertEquals("Fortifying camp", RaidFeedback.preparation(state));
        state.preparationTicks = 1200;
        assertEquals("Mustering army", RaidFeedback.preparation(state));
    }

    @Test void authoritativeOccupationAndOfflinePauseTakePriorityWithoutMutatingState() {
        var state = raid();
        state.coreCaptured = true;
        state.preparationTicks = 1200;
        var before = state.save();
        assertEquals("Reclaim core", RaidFeedback.phase(state, false, true, true));
        assertEquals("Paused", RaidFeedback.phase(state, true, true, true));
        assertEquals(before, state.save());
    }

    @Test void codexDeliveryNamesTheActualDestination() {
        assertEquals("Warlord's Codex added to your inventory.", RaidFeedback.codexDelivery(true, false));
        assertEquals("Your inventory is full. Your Warlord's Codex was dropped at your feet.",
                RaidFeedback.codexDelivery(false, true));
    }
    @Test void rejectedDropDoesNotClaimDelivery() {
        assertEquals("The Codex could not be delivered. Make room in your inventory and try again in 60 seconds.",
                RaidFeedback.codexDelivery(false, false));
    }

}
