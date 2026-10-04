package com.devfarinsky.siegeoverhaul.core;

import com.devfarinsky.siegeoverhaul.MinecraftTestSupport;
import com.devfarinsky.siegeoverhaul.RaidNetwork;
import com.devfarinsky.siegeoverhaul.RaidSavedData;
import com.devfarinsky.siegeoverhaul.nativecompat.NativePerimeterProjects;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PerimeterCancelPacketTest extends MinecraftTestSupport {
    @Test void cancellationRoundTripContainsOnlyMenuProjectAndGeneration() {
        var packet = new RaidNetwork.PerimeterProjectCancel(100, UUID.randomUUID(), Long.MAX_VALUE);
        var buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            packet.encode(buffer);
            assertEquals(25, buffer.readableBytes());
            assertEquals(packet, RaidNetwork.PerimeterProjectCancel.decode(buffer));
            assertFalse(buffer.isReadable());
        } finally { buffer.release(); }
    }

    @Test void malformedIdentifiersGenerationsAndExtraMetadataAreRejected() {
        UUID id = UUID.randomUUID();
        for (int menu : new int[]{-1, 0, 101, Integer.MAX_VALUE}) assertMalformed(menu, id, 1);
        for (long generation : new long[]{Long.MIN_VALUE, -1, 0}) assertMalformed(1, id, generation);
        assertMalformed(1, new UUID(0, 0), 1);
        assertThrows(IllegalArgumentException.class, () -> new RaidNetwork.PerimeterProjectCancel(1, null, 1));
        var buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            new RaidNetwork.PerimeterProjectCancel(1, id, 1).encode(buffer);
            buffer.writeUtf("Client cannot assert a terminal receipt or geometry");
            assertThrows(IllegalArgumentException.class, () -> RaidNetwork.PerimeterProjectCancel.decode(buffer));
            buffer.clear(); buffer.writeVarInt(1); buffer.writeUUID(id);
            assertThrows(IndexOutOfBoundsException.class, () -> RaidNetwork.PerimeterProjectCancel.decode(buffer));
        } finally { buffer.release(); }
    }

    @Test void actualMatchingOpenMenuAndItsOwnerAreRequiredBeforeNativeCancellation() throws Exception {
        try (var fixture = new Fixture()) {
            assertFalse(fixture.packet.handle(null));
            fixture.player.containerMenu = null;
            assertFalse(fixture.packet.handle(fixture.player));
            fixture.player.containerMenu = mock(AbstractContainerMenu.class);
            assertFalse(fixture.packet.handle(fixture.player));
            fixture.player.containerMenu = fixture.menu;
            setField(AbstractContainerMenu.class, fixture.menu, "containerId", 8);
            assertFalse(fixture.packet.handle(fixture.player));
            setField(AbstractContainerMenu.class, fixture.menu, "containerId", 7);
            setField(CoreHireMenu.class, fixture.menu, "owner", mock(ServerPlayer.class));
            assertFalse(fixture.packet.handle(fixture.player));
            setField(CoreHireMenu.class, fixture.menu, "owner", fixture.player);
            when(fixture.player.isAlive()).thenReturn(false);
            assertFalse(fixture.packet.handle(fixture.player));
            when(fixture.player.isAlive()).thenReturn(true); when(fixture.player.isSpectator()).thenReturn(true);
            assertFalse(fixture.packet.handle(fixture.player));
            fixture.nativeProjects.verifyNoInteractions();
            when(fixture.player.isSpectator()).thenReturn(false);
            assertTrue(fixture.packet.handle(fixture.player));
            fixture.nativeProjects.verify(() -> NativePerimeterProjects.cancel(fixture.player, fixture.id(), 1));
        }
    }

    @Test void staleClosedOrInvalidCoreMenuCannotBorrowNearbyMarkerAuthority() throws Exception {
        try (var fixture = new Fixture()) {
            fixture.cores.when(() -> SiegeCore.canUse(fixture.player, fixture.pos())).thenReturn(false);
            assertFalse(fixture.packet.handle(fixture.player));
            fixture.cores.when(() -> SiegeCore.canUse(fixture.player, fixture.pos())).thenReturn(true);
            setField(CoreHireMenu.class, fixture.menu, "pos", fixture.pos().above());
            assertFalse(fixture.packet.handle(fixture.player));
            fixture.nativeProjects.verifyNoInteractions();
        }
    }

    @Test void projectMustBelongToSenderAndAuthoritativeCoreAtTheRequestedGeneration() throws Exception {
        try (var fixture = new Fixture()) {
            assertFalse(new RaidNetwork.PerimeterProjectCancel(7, UUID.randomUUID(), 1).handle(fixture.player));
            assertFalse(new RaidNetwork.PerimeterProjectCancel(7, fixture.id(), 2).handle(fixture.player));
            when(fixture.player.getUUID()).thenReturn(UUID.randomUUID());
            assertFalse(fixture.packet.handle(fixture.player));
            when(fixture.player.getUUID()).thenReturn(fixture.project.header().owner());
            fixture.cores.when(() -> SiegeCore.key(fixture.player)).thenReturn("team:other");
            fixture.saved.siegeCores.put("team:other", fixture.core);
            assertFalse(fixture.packet.handle(fixture.player));
            fixture.nativeProjects.verifyNoInteractions();
        }
    }

    @Test void legitimateCoreRelocationPreservesProjectIdentityWhenTheCurrentMenuIsValid() throws Exception {
        try (var fixture = new Fixture()) {
            BlockPos relocated = fixture.pos().above();
            fixture.core.putLong("Position", relocated.asLong());
            setField(CoreHireMenu.class, fixture.menu, "pos", relocated);
            fixture.cores.when(() -> SiegeCore.canUse(fixture.player, fixture.pos())).thenReturn(false);
            fixture.cores.when(() -> SiegeCore.canUse(fixture.player, relocated)).thenReturn(true);
            assertTrue(fixture.packet.handle(fixture.player));
            fixture.nativeProjects.verify(() -> NativePerimeterProjects.cancel(fixture.player, fixture.id(), 1));
        }
    }

    @Test void missingMalformedOccupiedRemovedAndTerminalAuthorityFailClosed() throws Exception {
        try (var fixture = new Fixture()) {
            fixture.saved.siegeCores.clear();
            assertFalse(fixture.packet.handle(fixture.player));
            fixture.saved.siegeCores.put("team:test", fixture.core);
            fixture.core.putString("Position", "0");
            assertFalse(fixture.packet.handle(fixture.player));
            fixture.core.putLong("Position", fixture.pos().asLong());
            fixture.core.putBoolean("Occupied", true);
            assertFalse(fixture.packet.handle(fixture.player));
            fixture.core.remove("Occupied"); fixture.core.putBoolean("CoreRemoved", true);
            assertFalse(fixture.packet.handle(fixture.player));
            fixture.core.remove("CoreRemoved");
            CompoundTag valid = fixture.core.getCompound(PerimeterProjectStore.KEY).copy();
            fixture.core.getCompound(PerimeterProjectStore.KEY).putInt("Version", 999);
            assertFalse(fixture.packet.handle(fixture.player));
            fixture.core.put(PerimeterProjectStore.KEY, valid);
            var canceled = fixture.project.cancel(fixture.project.check(), "Canceled");
            PerimeterProjectStore.replace(fixture.core, fixture.project.check(), canceled, () -> {});
            assertFalse(fixture.packet.handle(fixture.player));
            fixture.nativeProjects.verifyNoInteractions();
        }
    }

    @Test void completedAndCompactedProjectsCannotBeCanceledOrReconstructed() throws Exception {
        try (var fixture = new Fixture()) {
            var current = PerimeterProjectStore.consumeOnce(fixture.core, fixture.id(), fixture.project.manifestHash(),
                    PerimeterProject.NEW_PROJECT_PRICE, true, () -> {}).project();
            while (current.activeStage() < current.stages().size()) {
                current = fixture.replace(current, current.activate(current.check()));
                current = fixture.replace(current, current.verifyStage(current.check(), current.expectedStageReceipt()));
                current = fixture.replace(current, current.retireVerifiedStage(current.check()));
            }
            current = fixture.replace(current, current.complete(current.check(), current.manifestHash()));
            assertFalse(fixture.packet.handle(fixture.player));
            // Model witness only: native cleanup must independently produce these facts in production.
            var h = current.header();
            var proof = new PerimeterTerminalReceipt.CleanupProof(h.projectId(), h.generation(), current.manifestHash(), current.revision(),
                    current.state(), h.owner(), h.builder(), UUID.randomUUID(), current.stages().stream().map(PerimeterProject.Stage::areaId).toList(),
                    "1".repeat(64), "2".repeat(64), "3".repeat(64));
            PerimeterProjectStore.compact(fixture.core, current.check(), proof, () -> {});
            assertNull(PerimeterProjectStore.get(fixture.core, fixture.id()));
            assertNotNull(PerimeterProjectStore.terminal(fixture.core, fixture.id()));
            assertFalse(fixture.packet.handle(fixture.player));
            fixture.nativeProjects.verifyNoInteractions();
        }
    }

    @Test void nativeCancellationFailureIsNotReportedAsSuccess() throws Exception {
        try (var fixture = new Fixture()) {
            fixture.nativeProjects.when(() -> NativePerimeterProjects.cancel(fixture.player, fixture.id(), 1)).thenReturn(false);
            assertFalse(fixture.packet.handle(fixture.player));
            fixture.nativeProjects.verify(() -> NativePerimeterProjects.cancel(fixture.player, fixture.id(), 1));
        }
    }

    private static void assertMalformed(int menu, UUID id, long generation) {
        assertThrows(IllegalArgumentException.class, () -> new RaidNetwork.PerimeterProjectCancel(menu, id, generation));
        var buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            buffer.writeVarInt(menu); buffer.writeUUID(id); buffer.writeLong(generation);
            assertThrows(IllegalArgumentException.class, () -> RaidNetwork.PerimeterProjectCancel.decode(buffer));
        } finally { buffer.release(); }
    }

    private static void setField(Class<?> type, Object target, String name, Object value) throws Exception {
        var field = type.getDeclaredField(name); field.setAccessible(true); field.set(target, value);
    }

    private static final class Fixture implements AutoCloseable {
        final ServerPlayer player = mock(ServerPlayer.class);
        final CoreHireMenu menu = mock(CoreHireMenu.class, CALLS_REAL_METHODS);
        final PerimeterProject project = PerimeterProjectTest.project();
        final RaidSavedData saved = new RaidSavedData();
        final CompoundTag core = new CompoundTag();
        final MockedStatic<RaidSavedData> data = mockStatic(RaidSavedData.class);
        final MockedStatic<SiegeCore> cores = mockStatic(SiegeCore.class);
        final MockedStatic<NativePerimeterProjects> nativeProjects = mockStatic(NativePerimeterProjects.class);
        final RaidNetwork.PerimeterProjectCancel packet = new RaidNetwork.PerimeterProjectCancel(7, id(), 1);
        Fixture() throws Exception {
            setField(AbstractContainerMenu.class, menu, "containerId", 7);
            setField(CoreHireMenu.class, menu, "owner", player);
            setField(CoreHireMenu.class, menu, "pos", pos());
            player.containerMenu = menu;
            when(player.getUUID()).thenReturn(project.header().owner());
            when(player.isAlive()).thenReturn(true);
            core.putLong("Position", pos().asLong());
            saved.siegeCores.put("team:test", core);
            PerimeterProjectStore.prepare(core, project, () -> {});
            data.when(() -> RaidSavedData.get(player.server)).thenReturn(saved);
            cores.when(() -> SiegeCore.key(player)).thenReturn("team:test");
            cores.when(() -> SiegeCore.canUse(player, pos())).thenReturn(true);
            nativeProjects.when(() -> NativePerimeterProjects.cancel(player, id(), 1)).thenReturn(true);
        }
        UUID id() { return project.header().projectId(); }
        BlockPos pos() { return project.header().originalCore(); }
        PerimeterProject replace(PerimeterProject before, PerimeterProject after) {
            return PerimeterProjectStore.replace(core, before.check(), after, () -> {});
        }
        @Override public void close() {
            nativeProjects.close(); cores.close(); data.close();
        }
    }
}
