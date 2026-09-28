package com.devfarinsky.siegeoverhaul.core;

import com.devfarinsky.siegeoverhaul.MinecraftTestSupport;
import com.devfarinsky.siegeoverhaul.RaidNetwork;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class ConstructionPacketTest extends MinecraftTestSupport {
    @Test void progressStatusAndSuppliesSurviveRoundTrip() {
        var job = new ConstructionReport.Job("Wall", 35, "35% placed", "Marker 0, 64, 0", "Sleeping", "12 x Cobblestone");
        var packet = new RaidNetwork.ConstructionDetails(7, List.of(job));
        var buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            packet.encode(buffer);
            assertEquals(packet, RaidNetwork.ConstructionDetails.decode(buffer));
            assertEquals(0, buffer.readableBytes());
        } finally { buffer.release(); }
    }
    @Test void payloadIsBoundedAndUnknownProgressStaysUnknown() {
        var job = new ConstructionReport.Job("x".repeat(1000), -1, "unknown", "", "", "x".repeat(2000));
        var packet = new RaidNetwork.ConstructionDetails(1, java.util.Collections.nCopies(20, job));
        assertEquals(12, packet.jobs().size());
        assertEquals(256, packet.jobs().get(0).label().length());
        assertEquals(256, packet.jobs().get(0).supplies().length());
        assertEquals(-1, packet.jobs().get(0).percent());
    }
    @Test void malformedCountsAreRejectedBeforeReadingEntries() {
        for (int count : new int[]{-1, 13, Integer.MAX_VALUE}) {
            var buffer = new FriendlyByteBuf(Unpooled.buffer());
            try {
                buffer.writeVarInt(1); buffer.writeVarInt(count);
                assertThrows(IllegalArgumentException.class, () -> RaidNetwork.ConstructionDetails.decode(buffer));
            } finally { buffer.release(); }
        }
    }
}
