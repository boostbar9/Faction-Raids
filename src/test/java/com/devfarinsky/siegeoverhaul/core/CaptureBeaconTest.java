package com.devfarinsky.siegeoverhaul.core;

import com.devfarinsky.siegeoverhaul.*;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import java.util.List;
import static com.devfarinsky.siegeoverhaul.core.CaptureStatus.Participation.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CaptureBeaconTest extends MinecraftTestSupport {
    private static final ResourceLocation OVERWORLD = Level.OVERWORLD.location();
    private static RaidNetwork.CaptureBeam packet(int x, int percent, long time) {
        return new RaidNetwork.CaptureBeam(OVERWORLD, new BlockPos(x, 64, 0), percent, time, 6, 2, true, 2, 1, COUNTED);
    }
    private CaptureBeacon.State state() {
        var state = new CaptureBeacon.State(); state.world(new Object(), OVERWORLD); return state;
    }
    @Test void packetPreservesServerGeometryCountsParticipationAndClearSignal() {
        for (int progress : new int[]{-1, 0, 25, 50, 75, 100}) for (var status : CaptureStatus.Participation.values()) {
            var p = new RaidNetwork.CaptureBeam(OVERWORLD, new BlockPos(-120, 64, 0), progress, 3456,
                    32, 16, false, 300, 2, status);
            var buffer = new FriendlyByteBuf(Unpooled.buffer());
            try { p.encode(buffer); assertEquals(p, RaidNetwork.CaptureBeam.decode(buffer)); assertEquals(0, buffer.readableBytes()); }
            finally { buffer.release(); }
        }
        assertThrows(IllegalArgumentException.class, () -> packet(0, 101, 0));
        assertThrows(IllegalArgumentException.class, () -> packet(0, -2, 0));
        assertThrows(IllegalArgumentException.class, () -> packet(0, 0, -1));
    }
    @Test void malformedWireRangesFailClosedBeforeReachingGeometryOrCache() {
        int[][] malformed = {{1,2,0,0,0}, {33,2,0,0,0}, {6,0,0,0,0}, {6,17,0,0,0},
                {6,2,-1,0,0}, {6,2,0,-1,0}, {6,2,0,0,255}};
        for (int[] fields : malformed) {
            var b = new FriendlyByteBuf(Unpooled.buffer());
            try {
                b.writeResourceLocation(OVERWORLD); b.writeBlockPos(BlockPos.ZERO); b.writeByte(0); b.writeLong(100);
                b.writeByte(fields[0]); b.writeByte(fields[1]); b.writeBoolean(true);
                b.writeVarInt(fields[2]); b.writeVarInt(fields[3]); b.writeByte(fields[4]);
                assertThrows(IllegalArgumentException.class, () -> RaidNetwork.CaptureBeam.decode(b));
            } finally { b.release(); }
        }
        var b = new FriendlyByteBuf(Unpooled.buffer());
        try { packet(0, 50, 100).encode(b); b.writerIndex(b.writerIndex() - 1);
            assertThrows(IndexOutOfBoundsException.class, () -> RaidNetwork.CaptureBeam.decode(b));
        } finally { b.release(); }
    }
    @Test void progressClampsWithoutOverflowAndDoesNotShowCompletionEarly() {
        assertEquals(99, CaptureBeacon.percent(2399, 2400)); assertEquals(100, CaptureBeacon.percent(2400, 2400));
        assertEquals(0, CaptureBeacon.percent(-1, 2400)); assertEquals(0, CaptureBeacon.percent(1, 0));
        assertEquals(100, CaptureBeacon.percent(Integer.MAX_VALUE, 1));
    }
    @Test void colorHasDistinctMilestonesAndInterpolatesContinuously() {
        float[][] stops = {{1F,.12F,.08F},{1F,.48F,.05F},{1F,.9F,.12F},{.55F,1F,.12F},{.12F,1F,.35F}};
        for (int i = 0; i < stops.length; i++) assertArrayEquals(stops[i], CaptureBeacon.color(i * 25), .0001F);
        for (int i = 1; i <= 100; i++) {
            var before = CaptureBeacon.color(i - 1); var after = CaptureBeacon.color(i);
            for (int channel = 0; channel < 3; channel++) {
                assertTrue(after[channel] >= 0 && after[channel] <= 1);
                assertTrue(Math.abs(after[channel] - before[channel]) < .03F);
            }
        }
    }
    @Test void idleCoreRemainsVisibleAndReorderedPacketsCannotRestoreLostProgress() {
        var state = state(); state.accept(packet(0, 75, 100), 100);
        state.accept(packet(0, 50, 120), 120); state.accept(packet(0, 100, 100), 120);
        assertEquals(50, state.active(120).get(0).percent());
        state.accept(packet(0, 0, 140), 140); assertEquals(0, state.active(140).get(0).percent());
    }
    @Test void expiryAndClearTombstonesPreventPermanentOrResurrectedMarkers() {
        var state = state(); state.accept(packet(0, 100, 100), 100);
        assertEquals(1, state.active(159).size()); assertTrue(state.active(160).isEmpty());
        state.accept(packet(0, 40, 200), 200); state.accept(packet(0, -1, 220), 220);
        state.accept(packet(0, 40, 200), 220); state.accept(packet(0, 40, 220), 220);
        assertTrue(state.active(220).isEmpty());
        state.accept(packet(0, 40, 240), 240); assertEquals(1, state.active(240).size());
        state.accept(packet(1, 40, 100), 300); state.accept(packet(1, 40, 400), 300);
        assertTrue(state.active(300).isEmpty());
    }
    @Test void disconnectWorldReplacementAndDimensionChangesClearAndRejectSnapshots() {
        var state = state(); state.accept(packet(0, 50, 100), 100);
        state.world(null, null); state.accept(packet(0, 50, 100), 100); assertTrue(state.active(100).isEmpty());
        Object world = new Object(); state.world(world, OVERWORLD); state.accept(packet(0, 50, 100), 100);
        state.world(world, OVERWORLD); assertEquals(1, state.active(100).size());
        state.world(new Object(), OVERWORLD); assertTrue(state.active(100).isEmpty());
        state.world(new Object(), Level.NETHER.location()); state.accept(packet(0, 50, 100), 100);
        assertTrue(state.active(100).isEmpty());
    }
    @Test void futureToleranceHasNoLongOverflowAndClientCacheHasAHardBudget() {
        var state = state();
        for (int i = 0; i < 100; i++) state.accept(packet(i, 50, 100), 100);
        assertEquals(CaptureBeacon.LIMIT, state.active(100).size());
        assertTrue(state.active(100).stream().noneMatch(p -> p.pos().getX() == 0));
        state.clear(); state.accept(packet(0, 50, Long.MAX_VALUE), Long.MAX_VALUE - 4);
        assertEquals(1, state.active(Long.MAX_VALUE).size());
        state.clear(); state.accept(packet(0, 50, 106), 100); assertTrue(state.active(100).isEmpty());
    }
    @Test void personalServerStatusDoesNotConfuseAlliedProgressWithViewerEligibility() {
        var level = mock(ServerLevel.class); var player = mock(ServerPlayer.class); var pos = new BlockPos(0, 64, 0);
        when(player.isAlive()).thenReturn(true); when(player.position()).thenReturn(new Vec3(7.5, 64, .5));
        assertEquals(OUTSIDE, CaptureBeacon.participation(level, player, pos, 6, 2, true));
        when(player.position()).thenReturn(new Vec3(.5, 67, .5));
        assertEquals(HEIGHT, CaptureBeacon.participation(level, player, pos, 6, 2, true));
        when(player.position()).thenReturn(new Vec3(.5, 64, .5));
        assertEquals(UNAVAILABLE, CaptureBeacon.participation(level, player, pos, 6, 2, true));
        assertEquals(COUNTED, CaptureBeacon.participation(level, player, pos, 6, 2, false),
                "Sight-disabled capture must not require intermediate loaded columns");
        when(level.hasChunk(anyInt(), anyInt())).thenReturn(true);
        try (var sight = mockStatic(CaptureRing.class, CALLS_REAL_METHODS)) {
            sight.when(() -> CaptureRing.visible(level, pos, player.position())).thenReturn(false);
            assertEquals(BLOCKED, CaptureBeacon.participation(level, player, pos, 6, 2, true));
            assertEquals(COUNTED, CaptureBeacon.participation(level, player, pos, 6, 2, false));
            sight.when(() -> CaptureRing.visible(level, pos, player.position())).thenReturn(true);
            assertEquals(COUNTED, CaptureBeacon.participation(level, player, pos, 6, 2, true));
        }
        when(player.isCreative()).thenReturn(true);
        assertEquals(CREATIVE, CaptureBeacon.participation(level, player, pos, 6, 2, true));
        when(player.isSpectator()).thenReturn(true);
        assertEquals(SPECTATOR, CaptureBeacon.participation(level, player, pos, 6, 2, true));
        when(player.isAlive()).thenReturn(false);
        assertEquals(DEAD, CaptureBeacon.participation(level, player, pos, 6, 2, true));
        verify(level, never()).getChunk(anyInt(), anyInt());
    }
    @Test void idleSnapshotsOnlyReachNearbyMembersAndCarryCustomServerGeometry() {
        var level = mock(ServerLevel.class); var near = mock(ServerPlayer.class); var far = mock(ServerPlayer.class); var foreign = mock(ServerPlayer.class);
        when(level.dimension()).thenReturn(Level.OVERWORLD); when(level.getGameTime()).thenReturn(200L);
        when(level.players()).thenReturn(List.of(near, far, foreign)); when(near.isAlive()).thenReturn(true);
        when(near.position()).thenReturn(new Vec3(20.5, 64, .5));
        when(far.distanceToSqr(any(Vec3.class))).thenReturn(193D * 193);
        RaidConfig.CORE_CAPTURE_RADIUS.set(9); RaidConfig.CORE_CAPTURE_VERTICAL.set(4); RaidConfig.CORE_CAPTURE_REQUIRE_SIGHT.set(false);
        var builder = mock(net.minecraftforge.network.NetworkRegistry.ChannelBuilder.class, RETURNS_SELF);
        when(builder.simpleChannel()).thenReturn(mock(net.minecraftforge.network.simple.SimpleChannel.class));
        try (var registration = mockStatic(net.minecraftforge.network.NetworkRegistry.ChannelBuilder.class)) {
            registration.when(() -> net.minecraftforge.network.NetworkRegistry.ChannelBuilder.named(any())).thenReturn(builder);
            try (var keys = mockStatic(SiegeCore.class); var network = mockStatic(RaidNetwork.class)) {
                keys.when(() -> SiegeCore.key(near)).thenReturn("team:blue"); keys.when(() -> SiegeCore.key(far)).thenReturn("team:blue");
                keys.when(() -> SiegeCore.key(foreign)).thenReturn("team:red");
                var pos = new BlockPos(0, 64, 0);
                CaptureBeacon.send(level, "team:blue", pos, 1200, 2400, 2, 1);
                network.verify(() -> RaidNetwork.sendCaptureBeam(near, new RaidNetwork.CaptureBeam(OVERWORLD, pos, 50, 200, 9, 4, false, 2, 1, OUTSIDE)));
                CaptureBeacon.send(level, "team:blue", pos, 0, 2400, 0, 0);
                network.verify(() -> RaidNetwork.sendCaptureBeam(near, new RaidNetwork.CaptureBeam(OVERWORLD, pos, 0, 200, 9, 4, false, 0, 0, OUTSIDE)));
                network.verifyNoMoreInteractions();
            }
        }
        verify(level, never()).getChunk(anyInt(), anyInt()); verify(level, never()).getBlockState(any());
        verify(level, never()).setBlock(any(), any(), anyInt());
    }
}
