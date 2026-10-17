package com.devfarinsky.siegeoverhaul;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MagicFxPacketTest extends MinecraftTestSupport {
    @Test void magicEffectPacketRoundTripsAndRejectsBadInput() {
        var packet = new RaidNetwork.MagicFxPacket(1, 2, 1.5F, 64F, -3F, 4F, 0F, 0F, 12, -1);
        var buf = new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        try {
            packet.encode(buf);
            assertEquals(packet, RaidNetwork.MagicFxPacket.decode(buf));
            assertFalse(buf.isReadable());
        } finally { buf.release(); }
        assertThrows(IllegalArgumentException.class, () -> new RaidNetwork.MagicFxPacket(9, 0, 0, 0, 0, 0, 0, 0, 1, -1));
        assertThrows(IllegalArgumentException.class, () -> new RaidNetwork.MagicFxPacket(0, 99, 0, 0, 0, 0, 0, 0, 1, -1));
        assertThrows(IllegalArgumentException.class, () -> new RaidNetwork.MagicFxPacket(0, 0, Float.NaN, 0, 0, 0, 0, 0, 1, -1));
    }
}
