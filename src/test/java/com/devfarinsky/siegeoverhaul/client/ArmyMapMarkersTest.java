package com.devfarinsky.siegeoverhaul.client;

import com.devfarinsky.siegeoverhaul.RaidNetwork;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ArmyMapMarkersTest {

    @AfterEach
    void reset() {
        ArmyMapMarkers.clear();
    }

    @Test
    void progressRunsFromZeroToOneAcrossTheUpdateWindow() {
        assertEquals(0.0, ArmyMapMarkers.progress(1000L, 1000L, 1000L));
        assertEquals(0.5, ArmyMapMarkers.progress(1500L, 1000L, 1000L));
        assertEquals(1.0, ArmyMapMarkers.progress(2500L, 1000L, 1000L));
        assertEquals(1.0, ArmyMapMarkers.progress(1000L, 0L, 1000L));
    }

    @Test
    void firstUpdateDrawsEveryReportedUnit() {
        ArmyMapMarkers.accept(new RaidNetwork.ArmyMarkers("team", List.of(
                new RaidNetwork.ArmyMarkers.Marker(1, 100, -40, false),
                new RaidNetwork.ArmyMarkers.Marker(2, 120, -60, true))));
        var drawn = ArmyMapMarkers.current();
        assertEquals(2, drawn.size());
        assertTrue(drawn.stream().anyMatch(ArmyMapMarkers.Drawn::equipment));
        assertTrue(drawn.stream().anyMatch(marker -> marker.x() == 100.5 && marker.z() == -39.5));
    }

    @Test
    void interpolatedPositionsStayBetweenTheTwoUpdates() {
        ArmyMapMarkers.accept(new RaidNetwork.ArmyMarkers("team",
                List.of(new RaidNetwork.ArmyMarkers.Marker(1, 0, 0, false))));
        ArmyMapMarkers.accept(new RaidNetwork.ArmyMarkers("team",
                List.of(new RaidNetwork.ArmyMarkers.Marker(1, 40, 0, false))));
        var drawn = ArmyMapMarkers.current();
        assertEquals(1, drawn.size());
        assertTrue(drawn.get(0).x() >= 0.5 && drawn.get(0).x() <= 40.5);
    }

    @Test
    void oversizedArmiesAreTrimmedOnTheWire() {
        var markers = new java.util.ArrayList<RaidNetwork.ArmyMarkers.Marker>();
        for (int i = 0; i < RaidNetwork.ArmyMarkers.LIMIT + 20; i++)
            markers.add(new RaidNetwork.ArmyMarkers.Marker(i, i, i, false));
        assertEquals(RaidNetwork.ArmyMarkers.LIMIT, new RaidNetwork.ArmyMarkers("team", markers).markers().size());
    }
}
