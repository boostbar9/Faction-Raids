package com.devfarinsky.siegeoverhaul.nativecompat;

import com.devfarinsky.siegeoverhaul.SiegeOverhaul;
import com.devfarinsky.siegeoverhaul.core.PerimeterEarthworksJournal;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.talhanation.workers.entities.BuilderEntity;
import com.talhanation.workers.world.BuildBlock;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.level.gameevent.EuclideanGameEventListenerRegistry;
import net.minecraft.world.level.gameevent.GameEventListenerRegistry;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.level.levelgen.MarsagliaPolarGaussian;
import net.minecraft.world.level.levelgen.ThreadSafeLegacyRandomSource;
import net.minecraft.world.level.levelgen.Xoroshiro128PlusPlus;
import net.minecraft.world.level.levelgen.XoroshiroRandomSource;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicLong;

import static com.devfarinsky.siegeoverhaul.nativecompat.NativeEarthworksFixture.*;

/** Separately opted-in QA evidence only. Never installed in a work goal or production artifact. */
@Mod.EventBusSubscriber(modid = SiegeOverhaul.MOD_ID, value = Dist.CLIENT)
public final class NativeDirtCensusQa {
    private static final boolean ENABLED = Boolean.getBoolean("siegeoverhaul.nativeEarthworksQa")
            && Boolean.getBoolean("siegeoverhaul.nativeDirtCensusQa");
    private static final NativeDirtIntrospection FIELDS = new NativeDirtIntrospection();
    private static NativeDirtPolicy.Epoch epoch;
    private static NativeDirtCensus reader;
    private static ServerLevel world;
    private static long startedGeneration, startedGameTime;
    private static boolean completedAtServerStarted, captured;
    private NativeDirtCensusQa() {}

    @SubscribeEvent
    public static void serverStarted(ServerStartedEvent event) {
        if (!ENABLED) return;
        world = event.getServer().overworld();
        require(event.getServer().isSameThread() && WORLD.equals(event.getServer().getWorldData().getLevelName()),
                "Census lifecycle requires its fresh native QA server");
        require(epoch == null, "Only one fresh server lifecycle is supported per QA launch");
        epoch = new NativeDirtPolicy.Epoch(world);
        startedGeneration = epoch.beginReload();
        reader = new NativeDirtCensus(world, epoch);
        completedAtServerStarted = reader.completeReload(startedGeneration);
        startedGameTime = world.getGameTime();
    }

    /** Fired before reload listeners run. Later reloads deliberately remain invalidated in this fixture. */
    @SubscribeEvent
    public static void reloadBeginning(AddReloadListenerEvent event) {
        if (ENABLED && epoch != null) epoch.beginReload();
    }

    static NativeDirtCensusBoundary.Receipt capture(ServerLevel level, ServerPlayer owner, BuilderEntity worker,
                                                   EarthworksJobLedger.Job job, long stableSince, Path evidence) {
        if (!ENABLED) return null;
        return NativeDirtCensusBoundary.capture(() -> captureEvidence(level, owner, worker, job, stableSince, evidence));
    }

    private static NativeDirtCensusBoundary.Payload captureEvidence(ServerLevel level, ServerPlayer owner, BuilderEntity worker,
                                                                   EarthworksJobLedger.Job job, long stableSince, Path evidence) throws Exception {
        require(!captured && level == world && level.getServer().isSameThread(), "Census world/thread/capture differs");
        captured = true;
        require(level.getBlockState(TARGET) == Blocks.DIRT.defaultBlockState()
                && job.read().journal().state() == PerimeterEarthworksJournal.State.STAGE_VERIFIED
                && stableSince >= 0 && level.getGameTime() - stableSince >= 40
                && job.read().inFlight() == null, "Census must follow genuine stable one-FILL completion");
        var out = new LinkedHashMap<String, Object>();
        out.put("schema", "native-dirt-census-qa-v1");
        out.put("profileStatus", "PROFILE_UNREVIEWED");
        out.put("packagedProductionAcceptance", false);
        out.put("miningCallbacksInvoked", 0);
        out.put("target", List.of(TARGET.getX(), TARGET.getY(), TARGET.getZ()));
        out.put("buildHeight", List.of(level.getMinBuildHeight(), level.getMaxBuildHeight()));
        out.put("stableSinceGameTime", stableSince);
        out.put("captureGameTime", level.getGameTime());
        out.put("lifecycle", Map.of("signal", "ServerStartedEvent", "successfulCompletion", completedAtServerStarted,
                "startedGameTime", startedGameTime, "startedGeneration", startedGeneration, "currentGeneration", epoch.generation()));
        var snapshots = new ArrayList<Snapshot>();
        var metrics = new ArrayList<NativeDirtRuntime.Metrics>();
        var observations = new ArrayList<NativeDirtPolicy.Census>();
        try {
            require(completedAtServerStarted && epoch.generation() == startedGeneration,
                    "Initial resource completion missing or a later reload invalidated the census");
            snapshots.add(snapshot(level, owner, worker, job)); metrics.add(reader.runtimeMetrics());
            // Off-pulse commissioning after work has settled, never from the native mining callback.
            NativeDirtPolicy.Census binding = reader.bindRuntimeCensus(TARGET);
            validateCensusExport(binding);
            out.put("binding", binding.observation()); out.put("bindingCensus", binding);
            snapshots.add(snapshot(level, owner, worker, job)); metrics.add(reader.runtimeMetrics());
            for (int i = 0; i < 2; i++) {
                NativeDirtPolicy.Observation observation = reader.inspect(TARGET);
                validateCensusExport(observation.census());
                observations.add(observation.census());
                // No profile is constructed from this evidence. Any earlier refusal is retained exactly.
                out.put("decision", NativeDirtPolicy.evaluate(observation, null, null).check());
                snapshots.add(snapshot(level, owner, worker, job)); metrics.add(reader.runtimeMetrics());
            }
            out.put("censuses", observations);
            out.put("metrics", metrics);
            out.put("states", snapshots.stream().map(Snapshot::evidence).toList());
            var equal = new LinkedHashMap<String, Boolean>();
            for (String key : snapshots.get(0).privateState.keySet()) {
                Object expected = snapshots.get(0).privateState.get(key);
                equal.put(key, snapshots.stream().allMatch(s -> expected.equals(s.privateState.get(key))));
            }
            out.put("unchanged", equal);
            out.put("status", equal.values().stream().allMatch(Boolean::booleanValue)
                    && observations.stream().allMatch(c -> c.observation().ready()) ? "captured" : "refused");
        } catch (Exception | AssertionError unavailable) {
            // Exception strings can contain local/plugin inputs. Export only a fixed diagnostic/type.
            out.put("status", "refused");
            out.put("failure", Map.of("reason", "QA_STATE_UNAVAILABLE", "type", unavailable.getClass().getName()));
            out.put("censuses", observations); out.put("metrics", metrics);
            out.put("states", snapshots.stream().map(Snapshot::evidence).toList());
        }
        JsonElement safe = new GsonBuilder().serializeNulls().create().toJsonTree(out);
        validateExport(safe, 0, new int[]{0});
        String json = new GsonBuilder().serializeNulls().setPrettyPrinting().create().toJson(safe);
        require(json.length() <= 2_000_000, "Bounded census evidence exceeded its output budget");
        return new NativeDirtCensusBoundary.Payload(evidence, json, (String)out.get("status"));
    }

    private record Snapshot(Map<String, Object> privateState, Map<String, Object> evidence) {}
    private static final class IdentityRef {
        private final Object value;
        private IdentityRef(Object value) { this.value = value; }
        @Override public boolean equals(Object other) { return other instanceof IdentityRef reference && value == reference.value; }
        @Override public int hashCode() { return System.identityHashCode(value); }
    }
    private record StackState(IdentityRef item, String itemId, int count, CompoundTag tag) {}
    private record RngState(IdentityRef source, IdentityRef engine, IdentityRef gaussian, List<Object> values) {}
    private record RegistryState(Object identity, Object processing, List<?> listeners, List<?> additions, Set<?> removals) {}
    private static Snapshot snapshot(ServerLevel level, ServerPlayer owner, BuilderEntity worker,
                                     EarthworksJobLedger.Job job) throws Exception {
        var state = new LinkedHashMap<String, Object>();
        var exported = new LinkedHashMap<String, Object>();
        state.put("clock", List.of(level.getGameTime(), level.getDayTime(), worker.tickCount));
        exported.put("gameTime", level.getGameTime()); exported.put("workerTick", worker.tickCount);
        var chunks = new HashMap<Long, LevelChunk>();
        var registries = new ArrayList<Object>(); var registrySizes = new ArrayList<Integer>();
        var blockEntities = new ArrayList<Object>(); var pendingBlockEntities = new ArrayList<Object>();
        for (int x = (TARGET.getX() - 16) >> 4; x <= (TARGET.getX() + 16) >> 4; x++)
            for (int z = (TARGET.getZ() - 16) >> 4; z <= (TARGET.getZ() + 16) >> 4; z++) {
                LevelChunk chunk = level.getChunkSource().getChunkNow(x, z);
                require(chunk != null && chunk.getClass() == LevelChunk.class, "Snapshot needs nine existing exact chunks");
                chunks.put(net.minecraft.world.level.ChunkPos.asLong(x, z), chunk);
                Object raw = FIELDS.read(chunk, LevelChunk.class, "f_244451_", Int2ObjectMap.class);
                require(raw != null && raw.getClass() == Int2ObjectOpenHashMap.class, "Unknown registry map");
                var map = (Int2ObjectMap<?>) raw; require(map.size() <= 128, "Registry map budget exceeded");
                registrySizes.add(map.size()); var entries = new TreeMap<Integer, Object>();
                for (var entry : map.int2ObjectEntrySet()) entries.put(entry.getIntKey(), registryState(entry.getValue()));
                registries.add(entries);
                require(chunk.getBlockEntities().size() <= 128, "Existing block-entity map budget exceeded");
                var existingEntities = new HashMap<BlockPos, IdentityRef>();
                chunk.getBlockEntities().forEach((pos, entity) -> existingEntities.put(pos.immutable(), new IdentityRef(entity)));
                blockEntities.add(existingEntities);
                Object pending = FIELDS.read(chunk, ChunkAccess.class, "f_187609_", Map.class);
                require(pending != null && pending.getClass() == HashMap.class && ((Map<?, ?>)pending).size() <= 128,
                        "Pending block-entity map unavailable");
                var copied = new HashMap<Object, CompoundTag>();
                for (var entry : ((Map<?, ?>)pending).entrySet()) {
                    require(entry.getKey() instanceof BlockPos && entry.getValue() instanceof CompoundTag, "Unknown pending block entity");
                    copied.put(entry.getKey(), copyTag((CompoundTag)entry.getValue()));
                }
                pendingBlockEntities.add(copied);
            }
        state.put("registries", registries); state.put("blockEntities", blockEntities); state.put("pendingBlockEntities", pendingBlockEntities);
        exported.put("registryMapSizes", registrySizes);
        exported.put("existingBlockEntityCounts", blockEntities.stream().map(v -> ((Map<?, ?>)v).size()).toList());
        exported.put("pendingBlockEntityCounts", pendingBlockEntities.stream().map(v -> ((Map<?, ?>)v).size()).toList());
        var cells = new ArrayList<Integer>();
        for (BlockPos p : BlockPos.betweenClosed(TARGET.offset(-16, -16, -16), TARGET.offset(16, 16, 16))) {
            if (level.isOutsideBuildHeight(p)) continue;
            LevelChunk chunk = chunks.get(net.minecraft.world.level.ChunkPos.asLong(p.getX() >> 4, p.getZ() >> 4));
            cells.add(Block.getId(chunk.getBlockState(p)));
        }
        state.put("worldCells", cells); exported.put("worldCellCount", cells.size()); exported.put("worldCellsSha256", hash(cells.toString()));
        exported.put("targetState", BuiltInRegistries.BLOCK.getKey(level.getBlockState(TARGET).getBlock()).toString());
        var rng = List.of(randomState(level.random), randomState((RandomSource)FIELDS.read(level, Level.class, "f_220348_", RandomSource.class)),
                randomState((RandomSource)FIELDS.read(worker, Entity.class, "f_19796_", RandomSource.class)));
        state.put("rng", rng); exported.put("rngSha256", hash(rng.stream().map(RngState::values).toList().toString()));
        exported.put("rngKinds", rng.stream().map(v -> v.values.get(0)).toList());
        var chestEntity = chunks.get(net.minecraft.world.level.ChunkPos.asLong(CHEST.getX() >> 4, CHEST.getZ() >> 4)).getBlockEntities().get(CHEST);
        require(chestEntity instanceof Container, "Existing finite chest absent");
        var inventories = List.of(inventory((Container)chestEntity), inventory(worker.getInventory()), inventory(owner.getInventory()),
                List.of(stack(worker.getMainHandItem()), stack(worker.getOffhandItem())));
        state.put("inventories", inventories); exported.put("inventorySha256", inventoryHash(inventories));
        exported.put("inventorySlots", inventories.stream().map(List::size).toList());
        var entities = new ArrayList<Entity>();
        level.getEntities(EntityTypeTest.forClass(Entity.class), new AABB(TARGET).inflate(16), e -> true, entities, 129);
        require(entities.size() <= 128, "Local entity snapshot budget exceeded");
        var entityState = new ArrayList<Object>(); int itemCount = 0, xpCount = 0, xpValue = 0;
        for (Entity entity : entities.stream().sorted(java.util.Comparator.comparingInt(Entity::getId)).toList()) {
            entityState.add(List.of(new IdentityRef(entity), entity.position(), entity.getDeltaMovement(), entity.tickCount));
            if (entity instanceof ItemEntity item) { itemCount++; entityState.add(List.of(stack(item.getItem()), item.getAge(), item.lifespan)); }
            if (entity instanceof ExperienceOrb orb) { xpCount++; xpValue += orb.getValue(); entityState.add(orb.getValue()); }
        }
        state.put("entities", entityState); exported.put("entities", entities.size());
        exported.put("itemEntities", itemCount); exported.put("xpEntities", xpCount); exported.put("xpValue", xpValue);
        var area = worker.currentBuildArea; require(area != null && worker.neededItems.isEmpty(), "Stable native area/request state missing");
        require(area.stackToBreak.size() <= 4096 && area.stackToFree.size() <= 4096, "Native break queue budget exceeded");
        var queues = List.of(area.stackToBreak.stream().map(BlockPos::immutable).toList(), area.stackToFree.stream().map(BlockPos::immutable).toList(),
                buildQueue(area.stackToPlace), buildQueue(area.stackToPlaceMultiBlock));
        state.put("nativeQueues", List.of(new IdentityRef(area), queues, area.getFreeArea(), area.getFreeAreaDone(), worker.neededItems.size()));
        exported.put("nativeQueueSizes", queues.stream().map(List::size).toList()); exported.put("requestCount", worker.neededItems.size());
        exported.put("nativeQueuesSha256", hash(queues.toString()));
        state.put("scheduledTickCounts", List.of(level.getBlockTicks().count(), level.getFluidTicks().count()));
        exported.put("scheduledTickCounts", state.get("scheduledTickCounts"));
        CompoundTag ledger = EarthworksJobLedger.get(level).save(new CompoundTag());
        state.put("ledger", copyTag(ledger)); exported.put("ledgerSha256", tagHash(ledger));
        CompoundTag workerData = copyTag(worker.getPersistentData()); state.put("workerData", workerData);
        exported.put("workerDataSha256", tagHash(workerData));
        exported.put("journalState", job.read().journal().state().name()); exported.put("journalReceipts", job.read().journal().receipts().size());
        exported.put("inFlight", job.read().inFlight() != null);
        return new Snapshot(state, exported);
    }
    private static Object registryState(Object registry) throws Exception {
        if (registry == null) return "ABSENT";
        if (registry == GameEventListenerRegistry.NOOP) return new IdentityRef(registry);
        require(registry.getClass() == EuclideanGameEventListenerRegistry.class, "Unknown registry snapshot type");
        var owner = EuclideanGameEventListenerRegistry.class;
        var listeners = (List<?>)FIELDS.read(registry, owner, "f_244422_", List.class);
        var additions = (List<?>)FIELDS.read(registry, owner, "f_244008_", List.class);
        var removals = (Set<?>)FIELDS.read(registry, owner, "f_244308_", Set.class);
        require(listeners.size() <= 256 && additions.size() <= 256 && removals.size() <= 256, "Registry snapshot budget exceeded");
        return new RegistryState(new IdentityRef(registry), FIELDS.read(registry, owner, "f_244249_", boolean.class),
                listeners.stream().map(IdentityRef::new).toList(), additions.stream().map(IdentityRef::new).toList(),
                removals.stream().map(IdentityRef::new).collect(java.util.stream.Collectors.toUnmodifiableSet()));
    }
    private static RngState randomState(RandomSource random) throws Exception {
        Class<?> type = random.getClass(); Object gaussian, engine; var values = new ArrayList<Object>(); values.add(type.getName());
        if (type == LegacyRandomSource.class || type == ThreadSafeLegacyRandomSource.class) {
            boolean legacy = type == LegacyRandomSource.class;
            AtomicLong seed = (AtomicLong)FIELDS.read(random, type, legacy ? "f_188575_" : "f_224661_", AtomicLong.class);
            engine = seed; values.add(seed.get()); gaussian = FIELDS.read(random, type, legacy ? "f_188576_" : "f_224662_", MarsagliaPolarGaussian.class);
        } else if (type == XoroshiroRandomSource.class) {
            engine = FIELDS.read(random, type, "f_190099_", Xoroshiro128PlusPlus.class);
            values.add(FIELDS.read(engine, Xoroshiro128PlusPlus.class, "f_190089_", long.class));
            values.add(FIELDS.read(engine, Xoroshiro128PlusPlus.class, "f_190090_", long.class));
            gaussian = FIELDS.read(random, type, "f_190100_", MarsagliaPolarGaussian.class);
        } else throw new IllegalStateException("Unsupported exact RNG type");
        require(gaussian != null && gaussian.getClass() == MarsagliaPolarGaussian.class, "Unsupported Gaussian state");
        values.add(Double.doubleToRawLongBits((double)FIELDS.read(gaussian, MarsagliaPolarGaussian.class, "f_188598_", double.class)));
        values.add(FIELDS.read(gaussian, MarsagliaPolarGaussian.class, "f_188599_", boolean.class));
        return new RngState(new IdentityRef(random), new IdentityRef(engine), new IdentityRef(gaussian), List.copyOf(values));
    }
    private static List<StackState> inventory(Container container) throws Exception {
        require(container.getContainerSize() <= 64, "Inventory snapshot budget exceeded");
        var result = new ArrayList<StackState>();
        for (int i = 0; i < container.getContainerSize(); i++) result.add(stack(container.getItem(i)));
        return result;
    }
    private static StackState stack(ItemStack value) throws Exception {
        return new StackState(new IdentityRef(value.getItem()), BuiltInRegistries.ITEM.getKey(value.getItem()).toString(), value.getCount(), value.getTag() == null ? null : copyTag(value.getTag()));
    }
    private static String inventoryHash(List<? extends List<StackState>> inventories) throws Exception {
        var values = new ArrayList<Object>();
        for (var inventory : inventories) {
            var slots = new ArrayList<Object>();
            for (var stack : inventory) slots.add(List.of(stack.itemId,
                    stack.count, stack.tag == null ? "no-tag" : tagHash(stack.tag)));
            values.add(slots);
        }
        return hash(values.toString());
    }
    private static List<List<Long>> buildQueue(List<BuildBlock> queue) {
        require(queue.size() <= 4096, "Native placement queue budget exceeded");
        return queue.stream().map(b -> List.of(b.getPos().asLong(), (long)Block.getId(b.getState()))).toList();
    }
    private static CompoundTag copyTag(CompoundTag tag) throws Exception { require(tag.getClass() == CompoundTag.class, "Unknown compound snapshot type"); tagBytes(tag); return tag.copy(); }
    private static String tagHash(CompoundTag tag) throws Exception { return NativeDirtCensus.hash(tagBytes(tag)); }
    private static byte[] tagBytes(CompoundTag tag) throws Exception {
        var buffer = new ByteArrayOutputStream() {
            @Override public synchronized void write(int value) { require(count < 262_144, "NBT snapshot budget exceeded"); super.write(value); }
            @Override public synchronized void write(byte[] value, int offset, int size) {
                require(size <= 262_144 - count, "NBT snapshot budget exceeded"); super.write(value, offset, size);
            }
        };
        NbtIo.write(tag, new DataOutputStream(buffer)); return buffer.toByteArray();
    }
    private static String hash(String value) throws Exception { return NativeDirtCensus.hash(value.getBytes(StandardCharsets.UTF_8)); }

    private static void validateCensusExport(NativeDirtPolicy.Census census) {
        var resources = new ArrayList<>(census.modifierLayers());
        if (census.dirt() != null) resources.add(census.dirt());
        for (var resource : resources)
            require(resource.pack().matches("[A-Za-z0-9_.:-]{1,128}"), "Resource pack identifier is not safe census metadata");
    }

    /** Only the explicit DTO/maps above reach Gson; no Identity, reader, runtime arguments, or private snapshots. */
    private static void validateExport(JsonElement value, int depth, int[] nodes) {
        require(depth <= 16 && ++nodes[0] <= 100_000, "Census export nesting/count budget exceeded");
        if (value.isJsonObject()) for (var entry : value.getAsJsonObject().entrySet()) {
            require(entry.getKey().matches("[A-Za-z][A-Za-z0-9]*"), "Unexpected export key");
            validateExport(entry.getValue(), depth + 1, nodes);
        } else if (value.isJsonArray()) for (var child : value.getAsJsonArray()) validateExport(child, depth + 1, nodes);
        else if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()) {
            String text = value.getAsString(); require(text.length() <= 4096, "Census export string budget exceeded");
            require(text.codePoints().noneMatch(c -> c < 32 || c == 127), "Unsupported export control characters");
        }
    }
}
