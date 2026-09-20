package com.devfarinsky.siegeoverhaul.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class EntityPortraitTest {
    @Test void expensiveLivePreviewOnlyRunsForHoveredPortrait() {
        assertTrue(EntityPortrait.livePreview(10,20,40,10,20));
        assertTrue(EntityPortrait.livePreview(10,20,40,49.9f,59.9f));
        assertFalse(EntityPortrait.livePreview(10,20,40,50,30));
        assertFalse(EntityPortrait.livePreview(10,20,40,30,60));
        assertFalse(EntityPortrait.livePreview(10,20,40,9,30));
    }
}
