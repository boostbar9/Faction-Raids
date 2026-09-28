package com.devfarinsky.siegeoverhaul.compat;

import com.devfarinsky.siegeoverhaul.MinecraftTestSupport;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class WorkersConstructionViewTest extends MinecraftTestSupport {
    public static class Area {
        public List<Object> stackToPlace = new ArrayList<>(List.of(new Object(), new Object()));
        public List<Object> stackToPlaceMultiBlock = new ArrayList<>(List.of(new Object()));
        public CompoundTag getStructureNBT() {
            var tag = new CompoundTag(); var cells = new ListTag();
            for (int i = 0; i < 8; i++) cells.add(new CompoundTag());
            tag.put("blocks", cells); return tag;
        }
    }
    public static class Worker {
        public Object currentBuildArea;
        public boolean isFleeing;
        public List<Need> neededItems = new ArrayList<>();
        int order = 6; boolean night, supplies;
        public int getFollowState() { return order; }
        public boolean needsToSleep() { return night; }
        public boolean needsToGetItems() { return supplies; }
    }
    public static class Need {
        public int count; public boolean required;
        Need(int count, boolean required) { this.count = count; this.required = required; }
        public Object getMatchKey() { return Items.COBBLESTONE; }
    }
    @Test void reportsRemainingNativeQueuesWithoutConsumingThem() {
        var area = new Area();
        var progress = WorkersConstructionView.progress(area, 8);
        assertEquals(62, progress.percent()); assertEquals(3, progress.remaining());
        assertEquals(2, area.stackToPlace.size()); assertEquals(1, area.stackToPlaceMultiBlock.size());
        area.stackToPlace.clear(); area.stackToPlaceMultiBlock.clear();
        assertEquals(100, WorkersConstructionView.progress(area, 8).percent());
    }
    @Test void legacyJobsRecoverTheirDenominatorFromTheSavedBlueprint() {
        var progress = WorkersConstructionView.progress(new Area(), 0);
        assertEquals(8, progress.total()); assertEquals(3, progress.remaining());
    }
    @Test void missingOrInconsistentApisNeverClaimCompletion() {
        assertEquals(-1, WorkersConstructionView.progress(new Object(), 8).percent());
        assertEquals(-1, WorkersConstructionView.progress(new Area(), 2).percent());
    }
    @Test void ownerCommandsAndDangerTakePrecedenceOverSupplyTrips() {
        var worker = new Worker(); var area = new Area(); worker.currentBuildArea = area;
        worker.supplies = true;
        assertTrue(WorkersConstructionView.activity(worker, area).startsWith("Collecting"));
        worker.order = 2;
        assertTrue(WorkersConstructionView.activity(worker, area).contains("owner command"));
        worker.isFleeing = true;
        assertEquals("Fleeing danger", WorkersConstructionView.activity(worker, area));
        assertEquals(2, worker.order); assertSame(area, worker.currentBuildArea);
    }
    @Test void restAndReassignmentAreNotReportedAsActiveConstruction() {
        var worker = new Worker(); var area = new Area(); worker.currentBuildArea = area; worker.night = true;
        assertEquals("Night/rest period", WorkersConstructionView.activity(worker, area));
        worker.currentBuildArea = new Area();
        assertEquals("Assigned to another work area", WorkersConstructionView.activity(worker, area));
        assertTrue(WorkersConstructionView.activity(null, area).startsWith("Builder unavailable"));
    }
    @Test void supplyReportOnlyIncludesCurrentPositiveRequiredRequests() {
        var worker = new Worker();
        worker.neededItems.addAll(List.of(new Need(12, true), new Need(0, true), new Need(5, false)));
        assertEquals(List.of("12 x Cobblestone"), WorkersConstructionView.requests(worker));
        assertEquals(12, worker.neededItems.get(0).count); assertEquals(3, worker.neededItems.size());
    }
}
