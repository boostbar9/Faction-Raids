package com.devfarinsky.siegeoverhaul;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DashboardCollectionsTest extends MinecraftTestSupport {
    @Test void impossibleCountsAreRejectedBeforeAllocation() {
        for (int count : new int[]{-1, Integer.MIN_VALUE, Integer.MAX_VALUE}) {
            var strings = new FriendlyByteBuf(Unpooled.buffer());
            var rows = new FriendlyByteBuf(Unpooled.buffer());
            try {
                strings.writeVarInt(count);
                rows.writeVarInt(count);
                assertThrows(IllegalArgumentException.class,
                        () -> RaidNetwork.DashboardSync.readStringList(strings));
                assertThrows(IllegalArgumentException.class,
                        () -> RaidNetwork.DashboardSync.readJournalRows(rows));
            } finally { strings.release(); rows.release(); }
        }
    }

    @Test void entryCountsMustFitTheRemainingPayload() {
        var strings = new FriendlyByteBuf(Unpooled.buffer());
        var rows = new FriendlyByteBuf(Unpooled.buffer());
        try {
            strings.writeVarInt(2);
            strings.writeUtf(""); // Only one length byte is available.
            rows.writeVarInt(1);
            rows.writeZero(14); // A journal row needs at least 15 bytes.
            assertThrows(IllegalArgumentException.class,
                    () -> RaidNetwork.DashboardSync.readStringList(strings));
            assertThrows(IllegalArgumentException.class,
                    () -> RaidNetwork.DashboardSync.readJournalRows(rows));
        } finally { strings.release(); rows.release(); }
    }

    @Test void validStringsAndMinimumSizeJournalRowsDecodeCompletely() {
        var strings = new FriendlyByteBuf(Unpooled.buffer());
        var rows = new FriendlyByteBuf(Unpooled.buffer());
        try {
            strings.writeVarInt(2);
            strings.writeUtf(""); strings.writeUtf("archer");
            assertEquals(List.of("", "archer"), RaidNetwork.DashboardSync.readStringList(strings));
            assertEquals(0, strings.readableBytes());
            rows.writeVarInt(1);
            rows.writeLong(42);
            rows.writeUtf(""); rows.writeUtf(""); rows.writeUtf("");
            rows.writeVarInt(0); rows.writeVarInt(0);
            rows.writeUtf(""); rows.writeVarInt(0);
            assertEquals(List.of(new RaidEvents.JournalRow(42, "", "", "", 0, 0, "", 0)),
                    RaidNetwork.DashboardSync.readJournalRows(rows));
            assertEquals(0, rows.readableBytes());
        } finally { strings.release(); rows.release(); }
    }

    @Test void emptyCollectionsRemainValid() {
        var strings = new FriendlyByteBuf(Unpooled.buffer());
        var rows = new FriendlyByteBuf(Unpooled.buffer());
        try {
            strings.writeVarInt(0); rows.writeVarInt(0);
            assertEquals(List.of(), RaidNetwork.DashboardSync.readStringList(strings));
            assertEquals(List.of(), RaidNetwork.DashboardSync.readJournalRows(rows));
        } finally { strings.release(); rows.release(); }
    }

    @Test void armyFactionTruncationPreservesSupplementaryCharactersOnTheWire() {
        var packet = new RaidNetwork.ArmyMarkers("x".repeat(63) + "\uD83D\uDE00", List.of());
        assertEquals("x".repeat(63), packet.faction());
        var fitting = new RaidNetwork.ArmyMarkers("x".repeat(62) + "\uD83D\uDE00", List.of());
        assertEquals(64, fitting.faction().length());
        var buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            packet.encode(buffer); fitting.encode(buffer);
            assertEquals(packet, RaidNetwork.ArmyMarkers.decode(buffer));
            assertEquals(fitting, RaidNetwork.ArmyMarkers.decode(buffer));
            assertEquals(0, buffer.readableBytes());
        } finally { buffer.release(); }
    }
}
