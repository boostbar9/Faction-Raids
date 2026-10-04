package com.devfarinsky.siegeoverhaul.core;

import com.devfarinsky.siegeoverhaul.MinecraftTestSupport;
import com.devfarinsky.siegeoverhaul.compat.WorkersConstructionView;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class PerimeterConstructionReportTest extends MinecraftTestSupport {
    private static PerimeterProject running() {
        var paid = PerimeterProjectTest.project().paid(false);
        return paid.activate(paid.check());
    }

    @Test void unavailableCurrentSectionIsUnknownEvenWhenAnEarlierSectionWasVerified() {
        var project = running();
        project = project.verifyStage(project.check(), project.expectedStageReceipt());
        int verified = project.completedTargetCount();
        project = project.retireVerifiedStage(project.check());
        var report = ConstructionReport.projectSummary(project, null, "", "");
        assertEquals(-1, report.percent());
        assertTrue(report.progressText().contains("At least " + verified));
        assertTrue(report.progressText().contains("unknown"));
        assertFalse(report.complete());
        assertTrue(report.cancelable());
        assertTrue(report.sectionText().contains("64 emeralds paid once"));
        assertTrue(report.sectionText().contains("materials separate"));
    }

    @Test void availableProgressAddsOnlyCurrentPlacementToTheVerifiedReceiptPrefix() {
        var project = running();
        project = project.verifyStage(project.check(), project.expectedStageReceipt());
        project = project.retireVerifiedStage(project.check());
        project = project.activate(project.check());
        int current = project.active().layout().targets().size(), added = Math.min(25, current);
        var report = ConstructionReport.projectSummary(project, new WorkersConstructionView.Progress(current, current - added),
                "Working / travelling", "192 cobblestone");
        int placed = project.completedTargetCount() + added;
        assertEquals((int) ((long) placed * 100 / project.targets().size()), report.percent());
        assertTrue(report.progressText().startsWith(placed + " / "));
        assertEquals("192 cobblestone", report.supplies());
        assertEquals(project.header().projectId(), report.projectId());
        assertEquals(project.header().generation(), report.generation());
        assertFalse(report.complete());
    }

    @Test void wrongNativeTotalsAndUnavailableCountsNeverContributeProgress() {
        var project = running(); int current = project.active().layout().targets().size();
        for (var progress : new WorkersConstructionView.Progress[]{new WorkersConstructionView.Progress(current + 1, 0),
                new WorkersConstructionView.Progress(current, -1), new WorkersConstructionView.Progress(current, current + 1)}) {
            var report = ConstructionReport.projectSummary(project, progress, "Working", "");
            assertEquals(-1, report.percent());
            assertTrue(report.progressText().contains("unknown"));
            assertFalse(report.complete());
        }
    }

    @Test void currentReceiptIsNeverCountedAgainAsNativePlacement() {
        var project = running();
        project = project.verifyStage(project.check(), project.expectedStageReceipt());
        var report = ConstructionReport.projectSummary(project,
                new WorkersConstructionView.Progress(project.active().layout().targets().size(), 0), "", "");
        assertTrue(report.progressText().startsWith(project.completedTargetCount() + " / "));
        assertEquals((int) ((long) project.completedTargetCount() * 100 / project.targets().size()), report.percent());
    }

    @Test void allPlacedIsExplicitlyAwaitingFinalVerificationUntilTheTerminalStateExists() {
        var project = running();
        while (project.active() != null) {
            if (project.state() != PerimeterProject.State.RUNNING) project = project.activate(project.check());
            project = project.verifyStage(project.check(), project.expectedStageReceipt());
            project = project.retireVerifiedStage(project.check());
        }
        assertEquals(PerimeterProject.State.VERIFYING_COMPLETE, project.state());
        var pending = ConstructionReport.projectSummary(project, null, "", "");
        assertEquals(100, pending.percent());
        assertFalse(pending.complete()); assertTrue(pending.cancelable());
        assertTrue(pending.activity().contains("Awaiting final verification"));
        var paused = project.blockRecovery(project.check(), "Load the final terrain cells");
        var blocked = ConstructionReport.projectSummary(paused, null, "", "");
        assertFalse(blocked.complete());
        assertTrue(blocked.activity().contains("Awaiting final verification"));
        assertTrue(blocked.activity().contains("Load the final terrain cells"));
        var complete = ConstructionReport.projectSummary(project.complete(project.check(), project.manifestHash()), null, "", "");
        assertTrue(complete.complete()); assertFalse(complete.cancelable());
        assertTrue(complete.progressText().contains("blocks verified"));
    }

    @Test void canceledAndUnpaidReportsDoNotClaimCompletionOrAnotherFee() {
        var unpaid = PerimeterProjectTest.project();
        var pending = ConstructionReport.projectSummary(unpaid, null, "", "");
        assertTrue(pending.sectionText().contains("not paid"));
        assertFalse(pending.sectionText().contains("paid once"));
        var running = running();
        var canceled = ConstructionReport.projectSummary(running.cancel(running.check(), "Owner canceled"), null, "", "");
        assertFalse(canceled.complete()); assertFalse(canceled.cancelable()); assertEquals(-1, canceled.percent());
        assertTrue(canceled.activity().contains("no refund"));
        assertTrue(canceled.sectionText().contains("64 emeralds paid once"));
    }

    @Test void legacyConstructorStaysCompatibleAndProjectFlagsRequireExactIdentity() {
        var old = new ConstructionReport.Job("Wall", 30, "30%", "Here", "Working", "Blocks");
        assertNull(old.projectId()); assertEquals(0, old.generation()); assertEquals("", old.sectionText());
        assertFalse(old.cancelable()); assertFalse(old.complete());
        assertThrows(IllegalArgumentException.class, () -> new ConstructionReport.Job("", 0, "", "", "", "",
                null, 0, "", true, false));
        assertThrows(IllegalArgumentException.class, () -> new ConstructionReport.Job("", 100, "", "", "", "",
                UUID.randomUUID(), 1, "", true, true));
        assertThrows(IllegalArgumentException.class, () -> new ConstructionReport.Job("", 0, "", "", "", "",
                new UUID(0, 0), 1, "", false, false));
    }
}
