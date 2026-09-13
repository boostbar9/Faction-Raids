package com.devfarinsky.siegeoverhaul.client;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class UpdateNoticeStateTest {
    @Test void waitsForWorldAndChecksOnceASecondAfterJoinDelay() {
        var state = new UpdateNoticeState();
        for (int i = 0; i < 200; i++) assertFalse(state.shouldPoll(false));
        for (int i = 1; i < 100; i++) assertFalse(state.shouldPoll(true));
        assertTrue(state.shouldPoll(true));
        for (int i = 0; i < 19; i++) assertFalse(state.shouldPoll(true));
        assertTrue(state.shouldPoll(true));
    }

    @Test void pendingCheckCanFinishLaterWithoutLosingNotification() {
        var state = new UpdateNoticeState();
        int polls = 0;
        for (int i = 0; i < 200; i++) if (state.shouldPoll(true)) polls++;
        assertEquals(6, polls);
    }

    @Test void notificationDoesNotRepeatAfterReconnect() {
        var state = new UpdateNoticeState();
        state.markNotified();
        assertFalse(state.shouldPoll(false));
        for (int i = 0; i < 300; i++) assertFalse(state.shouldPoll(true));
    }

    @Test void disconnectRestartsJoinDelay() {
        var state = new UpdateNoticeState();
        for (int i = 0; i < 99; i++) state.shouldPoll(true);
        state.shouldPoll(false);
        assertFalse(state.shouldPoll(true));
    }
}
