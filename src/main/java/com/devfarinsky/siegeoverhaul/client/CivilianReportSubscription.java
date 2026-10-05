package com.devfarinsky.siegeoverhaul.client;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiConsumer;
/** Each visibility transition has a fresh identity, including after closing/reusing a container ID. */
final class CivilianReportSubscription {
    private static final AtomicLong NEXT_REQUEST = new AtomicLong(1);
    private boolean watching;
    void update(boolean visible, BiConsumer<Long, Boolean> action) {
        if (visible == watching) return;
        watching = visible;
        action.accept(NEXT_REQUEST.getAndIncrement(), visible);
    }
}
