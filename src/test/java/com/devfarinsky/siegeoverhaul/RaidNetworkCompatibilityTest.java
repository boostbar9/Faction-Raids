package com.devfarinsky.siegeoverhaul;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** The same real predicate is registered for both sides of the Forge channel handshake. */
class RaidNetworkCompatibilityTest {
    private void withChannelRegistration(Runnable assertions) {
        // Plain JUnit lacks Forge's transformed NetworkEvent constructors. Replace only
        // registration, as CaptureBeaconTest does; the acceptance predicate stays real.
        var builder = mock(net.minecraftforge.network.NetworkRegistry.ChannelBuilder.class, RETURNS_SELF);
        when(builder.simpleChannel()).thenReturn(mock(net.minecraftforge.network.simple.SimpleChannel.class));
        try (var registration = mockStatic(net.minecraftforge.network.NetworkRegistry.ChannelBuilder.class)) {
            registration.when(() -> net.minecraftforge.network.NetworkRegistry.ChannelBuilder.named(any())).thenReturn(builder);
            assertions.run();
        }
    }

    @Test void matchingCaptureGeometryProtocolIsAccepted() {
        withChannelRegistration(() -> assertTrue(RaidNetwork.acceptsProtocol("21")));
    }

    @Test void olderPriceContractsAndMissingOrMalformedPeersAreRejected() {
        withChannelRegistration(() -> {
            for (String version : new String[]{"20", "19", "18", "22", "4.52.4", "ABSENT", "ACCEPTVANILLA", "", " 21", "21 "})
                assertFalse(RaidNetwork.acceptsProtocol(version), version);
            assertFalse(RaidNetwork.acceptsProtocol(null));
        });
    }
}
