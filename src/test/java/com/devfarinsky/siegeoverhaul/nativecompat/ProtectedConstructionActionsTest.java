package com.devfarinsky.siegeoverhaul.nativecompat;

import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class ProtectedConstructionActionsTest {
    @Test void cancellationAndProjectionRequireTheExactNearbyOwner() {
        UUID owner = UUID.randomUUID();
        assertTrue(ProtectedConstructionActions.authorized(owner, owner, true));
        assertFalse(ProtectedConstructionActions.authorized(UUID.randomUUID(), owner, true));
        assertFalse(ProtectedConstructionActions.authorized(owner, owner, false));
        assertFalse(ProtectedConstructionActions.authorized(null, owner, true));
    }
}
