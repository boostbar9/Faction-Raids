package com.devfarinsky.siegeoverhaul.core;

import com.devfarinsky.siegeoverhaul.MinecraftTestSupport;
import com.devfarinsky.siegeoverhaul.RaidNetwork;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.UUID;
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
    @Test void truncatedNamesKeepSurrogatePairsIntactThroughPacketEncoding() {
        String name = "x".repeat(255) + "\uD83D\uDEE1" + " suffix";
        var job = new ConstructionReport.Job(name, 0, "", "", "", "");
        assertEquals("x".repeat(255), job.label());
        var buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            new RaidNetwork.ConstructionDetails(1, List.of(job)).encode(buffer);
            assertEquals(job, RaidNetwork.ConstructionDetails.decode(buffer).jobs().get(0));
        } finally { buffer.release(); }
    }
    @Test void stagedIdentitySectionStatusAndVerifiedCompletionSurviveRoundTrip() {
        UUID id = UUID.randomUUID();
        var active = new ConstructionReport.Job("Whole perimeter", 35, "35% placed", "Core 0, 64, 0", "Working", "12 x Stone",
                id, Long.MAX_VALUE, "Section 2 / 5", true, false);
        var complete = new ConstructionReport.Job("Whole perimeter", 100, "Whole perimeter verified", "", "Complete", "",
                UUID.randomUUID(), 1, "5 / 5 sections verified", false, true);
        var packet = new RaidNetwork.ConstructionDetails(100, List.of(active, complete));
        var buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            packet.encode(buffer);
            assertEquals(packet, RaidNetwork.ConstructionDetails.decode(buffer));
            assertEquals(0, buffer.readableBytes());
        } finally { buffer.release(); }
    }

    @Test void sectionTextIsBoundedWithoutSplittingSurrogatePairs() {
        var job = new ConstructionReport.Job("Wall", -1, "unknown", "", "", "",
                UUID.randomUUID(), 1, "x".repeat(255) + "\uD83D\uDEE1" + " suffix", true, false);
        assertEquals("x".repeat(255), job.sectionText());
        var buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            new RaidNetwork.ConstructionDetails(1, List.of(job)).encode(buffer);
            assertEquals(job, RaidNetwork.ConstructionDetails.decode(buffer).jobs().get(0));
        } finally { buffer.release(); }
    }

    @Test void allSixReportStringsRejectOversizeWireValues() {
        for (int field = 0; field < 6; field++) {
            var buffer = new FriendlyByteBuf(Unpooled.buffer());
            try {
                buffer.writeVarInt(1); buffer.writeVarInt(1);
                for (int i = 0; i < 5; i++) {
                    buffer.writeUtf(i == field ? "x".repeat(257) : "");
                    if (i == 0) buffer.writeVarInt(0);
                }
                buffer.writeBoolean(true); buffer.writeUUID(UUID.randomUUID()); buffer.writeLong(1);
                buffer.writeUtf(field == 5 ? "x".repeat(257) : ""); buffer.writeByte(1);
                assertThrows(io.netty.handler.codec.DecoderException.class, () -> RaidNetwork.ConstructionDetails.decode(buffer));
            } finally { buffer.release(); }
        }
    }

    @Test void malformedProjectIdentityFlagsAndProgressFailClosed() {
        UUID id = UUID.randomUUID();
        assertMalformed(0, 1, new UUID(0, 0), 1, "", 0);
        assertMalformed(0, 1, id, 0, "", 0);
        assertMalformed(0, 1, id, -1, "", 0);
        assertMalformed(0, 1, id, 1, "", 2); // Terminal-complete must have fully verified progress.
        assertMalformed(0, 1, id, 1, "", 3); // Complete may never also be cancelable.
        assertMalformed(0, 1, id, 1, "", 4);
        assertMalformed(0, 1, id, 1, "", 255);
        assertMalformed(0, 2, id, 1, "", 0);
        assertMalformed(0, 0, null, 0, "Forged stage status", 0);
        assertMalformed(0, 0, null, 0, "", 1);
        assertMalformed(0, 0, null, 0, "", 2);
        assertMalformed(-2, 1, id, 1, "", 0);
        assertMalformed(101, 1, id, 1, "", 0);
    }

    @Test void invalidMenuIdsAndTrailingOrTruncatedDataAreRejected() {
        for (int menuId : new int[]{-1, 0, 101, Integer.MAX_VALUE}) {
            assertThrows(IllegalArgumentException.class, () -> new RaidNetwork.ConstructionDetails(menuId, List.of()));
            var buffer = new FriendlyByteBuf(Unpooled.buffer());
            try {
                buffer.writeVarInt(menuId); buffer.writeVarInt(0);
                assertThrows(IllegalArgumentException.class, () -> RaidNetwork.ConstructionDetails.decode(buffer));
            } finally { buffer.release(); }
        }
        var buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            new RaidNetwork.ConstructionDetails(1, List.of()).encode(buffer); buffer.writeByte(0);
            assertThrows(IllegalArgumentException.class, () -> RaidNetwork.ConstructionDetails.decode(buffer));
            buffer.clear(); buffer.writeVarInt(1); buffer.writeVarInt(1); buffer.writeUtf("Truncated");
            assertThrows(IndexOutOfBoundsException.class, () -> RaidNetwork.ConstructionDetails.decode(buffer));
        } finally { buffer.release(); }
    }

    private static void assertMalformed(int percent, int present, UUID id, long generation, String section, int flags) {
        var buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            buffer.writeVarInt(1); buffer.writeVarInt(1); buffer.writeUtf("Wall"); buffer.writeVarInt(percent);
            for (int i = 0; i < 4; i++) buffer.writeUtf("");
            buffer.writeByte(present);
            if (present != 0) { buffer.writeUUID(id); buffer.writeLong(generation); }
            buffer.writeUtf(section); buffer.writeByte(flags);
            assertThrows(IllegalArgumentException.class, () -> RaidNetwork.ConstructionDetails.decode(buffer));
        } finally { buffer.release(); }
    }

}
