package com.devfarinsky.siegeoverhaul.client;

/** Client-thread state: wait for world chat to settle, then notify once per launch. */
final class UpdateNoticeState {
    private int worldTicks;
    private boolean notified;

    boolean shouldPoll(boolean inWorld) {
        if (!inWorld) {
            worldTicks = 0;
            return false;
        }
        if (notified) return false;
        worldTicks++;
        return worldTicks >= 100 && worldTicks % 20 == 0;
    }

    void markNotified() {
        notified = true;
    }
}
