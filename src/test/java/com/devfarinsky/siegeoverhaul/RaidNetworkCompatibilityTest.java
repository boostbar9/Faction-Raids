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

    @Test void civilianWatchWireRejectsUnboundedMenusStaleEpochShapesAndExtraFlags() {
        var request = new RaidNetwork.CivilianWatch(4, 12L, true);
        var buffer = new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        try { request.encode(buffer); assertEquals(request, RaidNetwork.CivilianWatch.decode(buffer)); }
        finally { buffer.release(); }
        assertThrows(IllegalArgumentException.class, () -> new RaidNetwork.CivilianWatch(0, 1, true));
        assertThrows(IllegalArgumentException.class, () -> new RaidNetwork.CivilianWatch(101, 1, true));
        assertThrows(IllegalArgumentException.class, () -> new RaidNetwork.CivilianWatch(1, 0, true));
        var bad = new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        try { bad.writeVarInt(1); bad.writeLong(2); bad.writeByte(2);
            assertThrows(IllegalArgumentException.class, () -> RaidNetwork.CivilianWatch.decode(bad)); }
        finally { bad.release(); }
    }

    @Test void matchingSnapshotProtocolIsAccepted() {
        withChannelRegistration(() -> assertTrue(RaidNetwork.acceptsProtocol("22")));
    }

    @Test void olderPriceContractsAndMissingOrMalformedPeersAreRejected() {
        withChannelRegistration(() -> {
            for (String version : new String[]{"19", "18", "20", "21", "4.52.4", "ABSENT", "ACCEPTVANILLA", "", " 22", "22 "})
                assertFalse(RaidNetwork.acceptsProtocol(version), version);
            assertFalse(RaidNetwork.acceptsProtocol(null));
        });
    }
}
