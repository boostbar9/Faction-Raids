package com.devfarinsky.siegeoverhaul.client;

import java.util.function.IntConsumer;

/** Sends one start/stop per visibility transition, including resize and close. */
final class BuildingReportSubscription {
    private boolean watching;

    void update(boolean visible, IntConsumer action) {
        if (visible == watching) return;
        watching = visible;
        action.accept(visible ? 84 : 85);
    }
}
