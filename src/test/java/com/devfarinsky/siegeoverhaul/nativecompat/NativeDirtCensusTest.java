package com.devfarinsky.siegeoverhaul.nativecompat;

import com.devfarinsky.siegeoverhaul.MinecraftTestSupport;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.gameevent.EuclideanGameEventListenerRegistry;
import net.minecraft.world.level.gameevent.GameEventListener;
import net.minecraft.world.level.gameevent.GameEventListenerRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static com.devfarinsky.siegeoverhaul.nativecompat.NativeDirtPolicy.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Mock-world observation tests only. No native server/client or mining is run here. */
class NativeDirtCensusTest extends MinecraftTestSupport {
    @ParameterizedTest @ValueSource(strings = {
            "{\"replace\":false,\"entries\":[]}", "{\"entries\":[],\"replace\":true}"})
    void emptyModifierLayersHaveStrictBoundedShape(String value) { assertTrue(empty(value)); }
    @ParameterizedTest @ValueSource(strings = {
            "{}", "{\"entries\":[]}", "{\"replace\":false,\"entries\":[\"example:modifier\"]}",
            "{\"replace\":false,\"entries\":[],\"other\":true}", "{\"replace\":false,\"entries\":[],\"entries\":[]}",
            "{\"replace\":\"false\",\"entries\":[]}", "{\"replace\":false,\"entries\":[]} {}", "[]", "null", "{bad}"})
    void unknownNonemptyMalformedOrDuplicateModifierLayersRefuse(String value) { assertFalse(empty(value)); }
    @Test void resourceReadIsBoundedAndAlwaysClosesItsStream() throws Exception {
        AtomicInteger closed = new AtomicInteger();
        var stream = new ByteArrayInputStream(new byte[MAX_RESOURCE_BYTES + 1]) {
            @Override public void close() { closed.incrementAndGet(); }
        };
        assertThrows(Exception.class, () -> NativeDirtCensus.bytes(stream, MAX_RESOURCE_BYTES));
        assertEquals(1, closed.get());
        assertEquals(MAX_RESOURCE_BYTES, NativeDirtCensus.bytes(new ByteArrayInputStream(new byte[MAX_RESOURCE_BYTES]), MAX_RESOURCE_BYTES).length);
        assertFalse(NativeDirtCensus.emptyModifierLayer(new byte[MAX_RESOURCE_BYTES + 1]));
    }
    @Test void wrongThreadAndDimensionRefuseBeforeChunkAccess() throws Exception {
        World f = new World(); when(f.server.isSameThread()).thenReturn(false);
        assertEquals(Reason.WRONG_THREAD, f.read().census().observation().reason()); verifyNoInteractions(f.chunks);
        f = new World(); when(f.level.dimension()).thenReturn(Level.NETHER);
        assertEquals(Reason.WRONG_DIMENSION, f.read().census().observation().reason()); verifyNoInteractions(f.chunks);
    }
    @Test void worldBorderAndTargetBuildHeightAreCheckedBeforeChunks() throws Exception {
        World f = new World(); when(f.border.isWithinBounds(any(BlockPos.class))).thenReturn(false);
        assertEquals(Reason.OUTSIDE_WORLD, f.read().census().observation().reason()); verifyNoInteractions(f.chunks);
        f = new World(); when(f.level.isOutsideBuildHeight(any(BlockPos.class))).thenReturn(true);
        assertEquals(Reason.OUTSIDE_WORLD, f.read().census().observation().reason()); verifyNoInteractions(f.chunks);
    }
    @Test void missingChunkRefusesWithoutLoadingOrReadingTheTarget() throws Exception {
        World f = new World(); when(f.chunks.getChunkNow(anyInt(), anyInt())).thenReturn(null);
        var result = f.read(); assertEquals(Reason.UNLOADED, result.census().observation().reason());
        verify(f.level, never()).getBlockState(any(BlockPos.class)); verify(f.chunk, never()).getListenerRegistry(anyInt());
        assertEquals(0, f.registryReads.get());
    }
    @Test void nineAlreadyLoadedChunksAndExactly27ExistingSectionEntriesAreRead() throws Exception {
        World f = new World(); when(f.chunk.getBlockState(any(BlockPos.class))).thenReturn(Blocks.STONE.defaultBlockState());
        var result = f.read(); assertEquals(Reason.NOT_EXACT_DIRT, result.census().observation().reason());
        assertEquals(9, result.census().chunks()); assertEquals(27, result.census().sections()); assertEquals(27, f.registryReads.get());
        assertEquals(27, result.census().registries().size());
        assertEquals(new SectionProof(-1, 3, -1, RegistryKind.ABSENT, readyCheck()), result.census().registries().get(0));
        assertEquals(new SectionProof(1, 5, 1, RegistryKind.ABSENT, readyCheck()), result.census().registries().get(26));
        assertThrows(UnsupportedOperationException.class, () -> result.census().registries().clear());
        verify(f.chunks, times(9)).getChunkNow(anyInt(), anyInt()); verify(f.chunk, never()).getListenerRegistry(anyInt());
        assertTrue(f.registries.isEmpty());
    }
    @Test void currentWorldRegistriesAreRecheckedOnEveryObservation() throws Exception {
        World f = new World(); when(f.chunk.getBlockState(any(BlockPos.class))).thenReturn(Blocks.STONE.defaultBlockState());
        assertEquals(Reason.NOT_EXACT_DIRT, f.read().census().observation().reason());
        assertEquals(27, f.registryReads.get());
        assertEquals(Reason.NOT_EXACT_DIRT, f.read().census().observation().reason());
        assertEquals(54, f.registryReads.get());
        f.registries.put(3, mock(GameEventListenerRegistry.class));
        assertEquals(Reason.GAME_EVENT_LISTENER, f.read().census().observation().reason());
    }
    @Test void lowerOutOfBuildHeightSectionIsStillInspectedForDynamicEntityListeners() throws Exception {
        World f = new World(); f.target = new BlockPos(-1, -63, -1);
        // Target is valid, section -5 extends BELOW minimum terrain. Dispatcher still visits it.
        when(f.level.isOutsideBuildHeight(any(BlockPos.class))).thenAnswer(a -> ((BlockPos) a.getArgument(0)).getY() < -64);
        var registry = new EuclideanGameEventListenerRegistry(null, -5, ignored -> {});
        var field = EuclideanGameEventListenerRegistry.class.getDeclaredField("listeners"); field.setAccessible(true);
        GameEventListener listener = mock(GameEventListener.class);
        ((List<Object>) field.get(registry)).add(listener); f.registries.put(-5, registry);
        var refused = f.read().census();
        assertEquals(Reason.GAME_EVENT_LISTENER, refused.observation().reason()); verifyNoInteractions(listener);
        assertEquals(1, refused.registries().size());
        assertEquals(-5, refused.registries().get(0).sectionY());
        assertEquals(RegistryKind.NATIVE, refused.registries().get(0).kind());
        assertEquals(Reason.GAME_EVENT_LISTENER, refused.registries().get(0).check().reason());
        verify(f.level, never()).getBlockState(any(BlockPos.class)); verify(f.chunk, never()).getListenerRegistry(anyInt());
    }
    @Test void upperOutOfBuildHeightKeyAndNoopStatusesAreExportedWithoutClipping() throws Exception {
        World f = new World(); f.target = new BlockPos(-1, 319, -1);
        when(f.level.isOutsideBuildHeight(any(BlockPos.class))).thenAnswer(a -> ((BlockPos)a.getArgument(0)).getY() >= 320);
        when(f.chunk.getBlockState(any(BlockPos.class))).thenReturn(Blocks.STONE.defaultBlockState());
        f.registries.put(20, GameEventListenerRegistry.NOOP);
        var result = f.read().census();
        assertEquals(Reason.NOT_EXACT_DIRT, result.observation().reason());
        assertEquals(27, result.registries().size());
        assertEquals(9, result.registries().stream().filter(p -> p.sectionY() == 20 && p.kind() == RegistryKind.NOOP).count());
        verify(f.chunk, never()).getListenerRegistry(anyInt());
    }
    @Test void unknownRegistryExportsOnlyClosedKindAndFixedRefusal() throws Exception {
        World f = new World(); var unknown = mock(GameEventListenerRegistry.class); f.registries.put(3, unknown);
        var result = f.read().census();
        assertEquals(RegistryKind.UNKNOWN, result.registries().get(0).kind());
        assertEquals(Reason.GAME_EVENT_LISTENER, result.registries().get(0).check().reason());
        verifyNoInteractions(unknown);
    }
    @Test void retainedObservationExpiresWhenTheServerGameTimeChanges() throws Exception {
        World f = new World(); Object resources = new Object(), table = new Object(), modifiers = new Object();
        when(f.level.getGameTime()).thenReturn(10L);
        assertTrue(f.epoch.completeReload(f.epoch.beginReload(), resources, table, modifiers));
        var identity = new Identity(f.epoch, f.level, resources, table, modifiers, List.of(), 10, f.target.asLong(), List.of(), null, null);
        assertTrue(identity.current().ready());
        when(f.level.getGameTime()).thenReturn(11L);
        assertEquals(Reason.STALE_OBSERVATION, identity.current().reason());
    }
    @Test void publicEvaluationAlwaysRechecksFreshWorldFacts() throws Exception {
        World f = new World(); when(f.chunk.getBlockState(any(BlockPos.class))).thenReturn(Blocks.STONE.defaultBlockState());
        assertEquals(Reason.NOT_EXACT_DIRT, f.census.evaluate(f.target, null, null).check().reason());
        f.registries.put(3, mock(GameEventListenerRegistry.class));
        assertEquals(Reason.GAME_EVENT_LISTENER, f.census.evaluate(f.target, null, null).check().reason());
    }
    @Test void existingOrPendingBlockEntityDataRefusesWithoutGenericPromotionGetter() throws Exception {
        World existing = new World();
        var blockEntity = mock(net.minecraft.world.level.block.entity.BlockEntity.class);
        existing.existingBlockEntities.put(existing.target, blockEntity);
        assertEquals(Reason.NOT_EXACT_DIRT, existing.read().census().observation().reason());
        verify(existing.level, never()).getBlockEntity(any(BlockPos.class));
        verify(existing.chunk, never()).getBlockEntity(any(BlockPos.class));
        assertEquals(java.util.Map.of(existing.target, blockEntity), existing.existingBlockEntities); verifyNoInteractions(blockEntity);
        World pending = new World();
        var tag = new net.minecraft.nbt.CompoundTag(); pending.pendingBlockEntities.put(pending.target, tag);
        assertEquals(Reason.NOT_EXACT_DIRT, pending.read().census().observation().reason());
        verify(pending.level, never()).getBlockEntity(any(BlockPos.class));
        verify(pending.chunk, never()).getBlockEntity(any(BlockPos.class));
        assertSame(tag, pending.pendingBlockEntities.get(pending.target)); assertEquals(1, pending.pendingBlockEntities.size());
    }
    @Test void dropsAndEachSnapshotFlagRefuseBeforeResourceOrLootIntrospection() throws Exception {
        World f = new World(); when(f.rules.getBoolean(GameRules.RULE_DOBLOCKDROPS)).thenReturn(false);
        assertEquals(Reason.DROPS_DISABLED, f.read().census().observation().reason()); verify(f.server, never()).getResourceManager();
        f = new World(); f.level.captureBlockSnapshots = true;
        assertEquals(Reason.SNAPSHOT_ACTIVE, f.read().census().observation().reason()); verify(f.server, never()).getResourceManager();
        f = new World(); f.level.restoringBlockSnapshots = true;
        assertEquals(Reason.SNAPSHOT_ACTIVE, f.read().census().observation().reason()); verify(f.server, never()).getResourceManager();
    }
    private static boolean empty(String value) { return NativeDirtCensus.emptyModifierLayer(value.getBytes(StandardCharsets.UTF_8)); }
    private static final class World {
        final ServerLevel level = mock(ServerLevel.class);
        final MinecraftServer server = mock(MinecraftServer.class);
        final WorldBorder border = mock(WorldBorder.class);
        final ServerChunkCache chunks = mock(ServerChunkCache.class);
        final LevelChunk chunk = mock(LevelChunk.class);
        final GameRules rules = mock(GameRules.class);
        final Int2ObjectOpenHashMap<GameEventListenerRegistry> registries = new Int2ObjectOpenHashMap<>();
        final AtomicInteger registryReads = new AtomicInteger();
        final java.util.Map<BlockPos, net.minecraft.world.level.block.entity.BlockEntity> existingBlockEntities = new java.util.HashMap<>();
        final java.util.Map<BlockPos, net.minecraft.nbt.CompoundTag> pendingBlockEntities = new java.util.HashMap<>();
        final Epoch epoch = new Epoch(level);
        final NativeDirtCensus census;
        BlockPos target = new BlockPos(8, 64, 8);
        World() throws Exception {
            when(level.getServer()).thenReturn(server); when(server.isSameThread()).thenReturn(true);
            when(level.dimension()).thenReturn(Level.OVERWORLD); when(level.getWorldBorder()).thenReturn(border);
            when(border.isWithinBounds(any(BlockPos.class))).thenReturn(true);
            when(level.getChunkSource()).thenReturn(chunks); when(chunks.getChunkNow(anyInt(), anyInt())).thenReturn(chunk);
            NativeDirtIntrospectionTest.set(chunk, LevelChunk.class, "gameEventListenerRegistrySections", registries);
            when(chunk.getBlockState(any(BlockPos.class))).thenReturn(Blocks.DIRT.defaultBlockState());
            when(chunk.getBlockEntities()).thenReturn(existingBlockEntities);
            when(chunk.getBlockEntityNbt(any(BlockPos.class))).thenAnswer(a -> pendingBlockEntities.get(a.getArgument(0)));
            when(level.getGameRules()).thenReturn(rules); when(rules.getBoolean(GameRules.RULE_DOBLOCKDROPS)).thenReturn(true);
            var fields = new NativeDirtIntrospection((owner, srg) -> {
                if (srg.equals("f_244451_")) registryReads.incrementAndGet();
                return NativeDirtIntrospectionTest.LOOKUP.find(owner, srg);
            });
            census = new NativeDirtCensus(level, epoch, fields);
        }
        Observation read() { return census.inspect(target); }
    }
}
