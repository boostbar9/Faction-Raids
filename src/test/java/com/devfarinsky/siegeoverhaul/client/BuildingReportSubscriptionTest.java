package com.devfarinsky.siegeoverhaul.client;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class BuildingReportSubscriptionTest {
    @Test void repeatedInitAndRepeatedClicksDoNotDuplicateSubscriptions() {
        var subscription = new BuildingReportSubscription();
        var actions = new ArrayList<Integer>();
        subscription.update(false, actions::add);
        subscription.update(true, actions::add);
        subscription.update(true, actions::add);
        subscription.update(true, actions::add);
        assertEquals(List.of(84), actions);
    }

    @Test void leavingConstructionAndClosingStopOnceAndReopeningRestarts() {
        var subscription = new BuildingReportSubscription();
        var actions = new ArrayList<Integer>();
        subscription.update(true, actions::add);
        subscription.update(false, actions::add); // Another subtab, top-level page or close.
        subscription.update(false, actions::add); // removed follows onClose.
        subscription.update(true, actions::add); // Reopened screen / return from a modal.
        subscription.update(false, actions::add);
        assertEquals(List.of(84, 85, 84, 85), actions);
    }
}
