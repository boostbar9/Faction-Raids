package com.devfarinsky.siegeoverhaul.nativecompat;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class WorkersConstructionRuntimeTest {
    @Test void onlyTheAuditedExactRuntimePairEnablesTheNewProtectedPath() {
        assertTrue(WorkersConstructionRuntime.supportedVersions("2.0.3", "1.15.2"));
        assertFalse(WorkersConstructionRuntime.supportedVersions("2.0.4", "1.15.2"));
        assertFalse(WorkersConstructionRuntime.supportedVersions("2.0.3", "1.15.3"));
        assertFalse(WorkersConstructionRuntime.supportedVersions("2.0.3-custom", "1.15.2"));
        assertFalse(WorkersConstructionRuntime.supportedVersions(null, "1.15.2"));
    }
}
