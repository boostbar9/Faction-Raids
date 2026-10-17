package com.devfarinsky.siegeoverhaul.nativecompat;

import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.gameevent.GameEventListenerRegistry;
import net.minecraft.world.level.gameevent.GameEventDispatcher;
import net.minecraft.world.level.gameevent.EuclideanGameEventListenerRegistry;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraftforge.common.ForgeHooks;
import net.minecraftforge.common.ForgeInternalHandler;
import net.minecraftforge.common.loot.LootModifierManager;
import net.minecraftforge.eventbus.ASMEventHandler;
import net.minecraftforge.eventbus.ClassLoaderFactory;
import net.minecraftforge.eventbus.EventBus;
import net.minecraftforge.eventbus.ModLauncherFactory;

import java.io.InputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

import static com.devfarinsky.siegeoverhaul.nativecompat.NativeDirtPolicy.*;

/**
 * Read-only server census for one exact dirt target. No world RNG, chunk loads, entity creation,
 * getListenerRegistry cache creation, item functions, condition evaluation or event dispatch.
 * Not an event subscriber and not connected to the native controller.
 */
public final class NativeDirtCensus {
    private static final ResourceLocation DIRT = new ResourceLocation("minecraft", "loot_tables/blocks/dirt.json");
    private static final ResourceLocation MODIFIERS = new ResourceLocation("forge", "loot_modifiers/global_loot_modifiers.json");
    private static final int MAX_CLASS_BYTES = 1_048_576, MAX_ORIGINS = 128;
    private final ServerLevel level;
    private final Epoch epoch;
    private final NativeDirtIntrospection fields;
    private final Map<Class<?>, CodeOrigin> origins = new HashMap<>();
    private long originGeneration = -1, boundGeneration = -1;
    private final NativeDirtRuntime runtimeReader = new NativeDirtRuntime();

    public NativeDirtCensus(ServerLevel level, Epoch epoch) { this(level, epoch, new NativeDirtIntrospection()); }
    NativeDirtCensus(ServerLevel level, Epoch epoch, NativeDirtIntrospection fields) {
        this.level = java.util.Objects.requireNonNull(level); this.epoch = java.util.Objects.requireNonNull(epoch);
        this.fields = java.util.Objects.requireNonNull(fields);
    }

    /** Startup-byte/read counters for bounded native-QA evidence; current world facts are not cached. */
    public NativeDirtRuntime.Metrics runtimeMetrics() { return runtimeReader.metrics(); }

    /** QA-only comparison of independently captured inputs. It neither grants nor persists admission. */
    boolean sameFrozenRuntimeInputs(NativeDirtCensus other) {
        return other != null && other != this && level == other.level && epoch == other.epoch
                && boundGeneration >= 0 && boundGeneration == epoch.generation() && boundGeneration == other.boundGeneration
                && runtimeReader.sameCapturedInputs(other.runtimeReader);
    }

    /** Call only from the corresponding proven successful reload completion/ServerStarted. */
    public boolean completeReload(long generation) {
        if (!level.getServer().isSameThread()) return false;
        try {
            return epoch.completeReload(generation, level.getServer().getResourceManager(),
                    level.getServer().getLootData().getLootTable(NativeDirtIntrospection.TABLE), NativeDirtIntrospection.modifierManager());
        } catch (ReflectiveOperationException | RuntimeException | LinkageError unavailable) { return false; }
    }

    /** Explicit startup/commissioning-only stage; never call from a work pulse. No CUT approval is produced. */
    public Check bindRuntime(BlockPos target) { return bindRuntimeCensus(target).observation(); }

    /** Same explicit off-pulse binding, retaining safe partial evidence on refusal. Never exports Identity. */
    public Census bindRuntimeCensus(BlockPos target) { return observe(target, true).census(); }

    /** Public dispatch-admission boundary: always makes a fresh synchronous world census. */
    public Decision evaluate(BlockPos target, Profile reviewed, Identity previous) {
        return NativeDirtPolicy.evaluate(inspect(target), reviewed, previous);
    }

    /** Reporting/QA evidence only. Dispatch callers use evaluate, never retain this as permission. */
    public Observation inspect(BlockPos target) { return observe(target, false); }

    private Observation observe(BlockPos target, boolean bindStartup) {
        List<ResourceProof> layers = new ArrayList<>();
        List<ListenerProof> listeners = new ArrayList<>();
        List<CodeOrigin> implementation = new ArrayList<>();
        List<SectionProof> registries = new ArrayList<>();
        Check graph = null;
        Integer activeModifiers = null;
        ResourceProof dirt = null;
        int chunks = 0, sections = 0;
        long gameTime = -1;
        try {
            if (!level.getServer().isSameThread()) throw stop(Reason.WRONG_THREAD, "dirt census requires the server thread");
            gameTime = level.getGameTime();
            if (level.dimension() != Level.OVERWORLD) throw stop(Reason.WRONG_DIMENSION, "dirt grading supports the reviewed Overworld only");
            if (target == null || !inside(target)) throw stop(Reason.OUTSIDE_WORLD, "dirt target must be in build bounds and its event radius within the world border");
            if (GameEvent.BLOCK_DESTROY.getNotificationRadius() != 16) throw stop(Reason.API_UNAVAILABLE, "BLOCK_DESTROY notification radius changed");
            LevelChunk targetChunk = null;
            // Vanilla dispatch uses floor(origin +/- radius); block center and integer pos produce
            // the same section coordinates. Exactly 3 x 3 x 3 for radius 16, including negatives.
            for (int x = (target.getX() - 16) >> 4; x <= (target.getX() + 16) >> 4; x++) {
                for (int z = (target.getZ() - 16) >> 4; z <= (target.getZ() + 16) >> 4; z++) {
                    LevelChunk chunk = level.getChunkSource().getChunkNow(x, z);
                    if (chunk == null) throw stop(Reason.UNLOADED, "the entire nine-chunk game-event radius must already be loaded");
                    if (chunk.getClass() != LevelChunk.class) throw stop(Reason.API_UNAVAILABLE, "unknown loaded chunk implementation");
                    if (x == target.getX() >> 4 && z == target.getZ() >> 4) targetChunk = chunk;
                    if (++chunks > MAX_CHUNKS) throw stop(Reason.READ_LIMIT, "game-event chunk budget exceeded");
                    for (int y = (target.getY() - 16) >> 4; y <= (target.getY() + 16) >> 4; y++) {
                        if (++sections > MAX_SECTIONS) throw stop(Reason.READ_LIMIT, "game-event section budget exceeded");
                        Object existing = fields.existingRegistry(chunk, y);
                        Check registry = fields.registry(existing);
                        RegistryKind kind = existing == null ? RegistryKind.ABSENT
                                : existing == GameEventListenerRegistry.NOOP ? RegistryKind.NOOP
                                : existing.getClass() == EuclideanGameEventListenerRegistry.class ? RegistryKind.NATIVE : RegistryKind.UNKNOWN;
                        registries.add(new SectionProof(x, y, z, kind, registry));
                        if (!registry.ready()) throw new Refused(registry);
                    }
                }
            }
            if (targetChunk == null || targetChunk.getBlockState(target) != Blocks.DIRT.defaultBlockState())
                throw stop(Reason.NOT_EXACT_DIRT, "only one exact ordinary vanilla dirt state is eligible for this policy");
            // Generic getBlockEntity can remove/promote pending NBT. These pinned getters are
            // direct existing-map reads, so even malformed/orphan data is refused without creation.
            var existingBlockEntities = targetChunk.getBlockEntities();
            if (existingBlockEntities == null || existingBlockEntities.getClass() != HashMap.class)
                throw stop(Reason.API_UNAVAILABLE, "existing block-entity map is unavailable or unsupported");
            if (existingBlockEntities.containsKey(target) || targetChunk.getBlockEntityNbt(target) != null)
                throw stop(Reason.NOT_EXACT_DIRT, "dirt target has existing or pending block-entity data");
            if (!level.getGameRules().getBoolean(GameRules.RULE_DOBLOCKDROPS)) throw stop(Reason.DROPS_DISABLED, "block drops are disabled");
            if (level.captureBlockSnapshots || level.restoringBlockSnapshots) throw stop(Reason.SNAPSHOT_ACTIVE, "Forge block snapshot capture/restoration is active");
            Object resources = level.getServer().getResourceManager();
            LootTable table = level.getServer().getLootData().getLootTable(NativeDirtIntrospection.TABLE);
            LootModifierManager manager = NativeDirtIntrospection.modifierManager();
            Check current = epoch.current(level, resources, table, manager);
            if (!current.ready()) throw new Refused(current);
            if (!bindStartup && boundGeneration != epoch.generation())
                throw stop(Reason.RUNTIME_UNPROVEN, "the current resource generation needs explicit off-pulse runtime binding");
            if (originGeneration != epoch.generation()) { origins.clear(); originGeneration = epoch.generation(); }
            Resource resource = level.getServer().getResourceManager().getResource(DIRT).orElse(null);
            if (resource == null) throw stop(Reason.RESOURCE_UNAVAILABLE, "effective dirt loot resource is missing");
            dirt = resource(resource);
            if (!dirt.builtin() || dirt.bytes() != 376 || !VANILLA_DIRT_SHA256.equals(dirt.sha256()))
                throw stop(Reason.RESOURCE_CHANGED, "effective dirt loot bytes differ from the pinned vanilla resource");
            graph = fields.loot(table);
            if (!graph.ready()) throw new Refused(graph);
            // This exact native manager is the execution authority; no modifier is invoked.
            activeModifiers = manager.getAllLootMods().size();
            if (activeModifiers != 0) throw stop(Reason.MODIFIERS_ACTIVE, "active global loot modifiers are unsupported for exact dirt accounting");
            var stack = level.getServer().getResourceManager().getResourceStack(MODIFIERS);
            if (stack.size() > MAX_RESOURCE_LAYERS) throw stop(Reason.READ_LIMIT, "global modifier resource layers exceed the census bound");
            for (Resource layer : stack) {
                byte[] bytes = bytes(layer.open(), MAX_RESOURCE_BYTES);
                layers.add(new ResourceProof(layer.sourcePackId(), layer.isBuiltin(), hash(bytes), bytes.length));
                if (!emptyModifierLayer(bytes, layer.isBuiltin())) throw stop(Reason.MODIFIERS_ACTIVE, "global modifier layer is not an exact empty supported list");
            }
            NativeDirtListeners.Read observed = NativeDirtListeners.read(this::origin);
            listeners.addAll(observed.proof());
            for (Class<?> type : List.of(NativeDirtPolicy.class, NativeDirtCensus.class, NativeDirtIntrospection.class,
                    NativeDirtListeners.class, NativeDirtRuntime.class, Level.class, Block.class, Item.class, BlockItem.class, ItemStack.class,
                    LootTable.class, LootPool.class, GameEvent.class, GameEventDispatcher.class,
                    EuclideanGameEventListenerRegistry.class, LevelChunk.class, ServerLevel.class,
                    net.minecraft.world.level.gameevent.GameEventListenerRegistry.class,
                    net.minecraft.world.entity.Entity.class, net.minecraft.world.entity.item.ItemEntity.class,
                    net.minecraft.world.level.block.state.BlockBehaviour.class,
                    net.minecraft.world.level.storage.loot.entries.LootItem.class,
                    net.minecraft.world.level.storage.loot.entries.LootPoolEntryContainer.class,
                    net.minecraft.world.level.storage.loot.entries.LootPoolSingletonContainer.class,
                    net.minecraft.world.level.storage.loot.providers.number.ConstantValue.class,
                    net.minecraft.world.level.storage.loot.predicates.ExplosionCondition.class,
                    net.minecraftforge.eventbus.api.EventListenerHelper.class, ForgeInternalHandler.class,
                    ForgeHooks.class, LootModifierManager.class, EventBus.class, ASMEventHandler.class,
                    ClassLoaderFactory.class, ModLauncherFactory.class)) implementation.add(origin(type));
            for (String name : List.of("com.talhanation.workers.entities.AbstractWorkerEntity",
                    "com.talhanation.workers.entities.BuilderEntity", "com.talhanation.workers.entities.ai.BuilderWorkGoal",
                    "com.talhanation.recruits.entities.AbstractRecruitEntity",
                    "net.minecraftforge.eventbus.internal.CacheConcurrent"))
                implementation.add(origin(Class.forName(name, false, NativeDirtCensus.class.getClassLoader())));
            for (Class<?> type : NativeDirtModuleView.implementationClasses()) implementation.add(origin(type));
            implementation.add(origin(cpw.mods.cl.JarModuleFinder.class));
            for (Class<?> type : NativeDirtRuntime.trustedFirstPartyClasses()) {
                if (implementation.stream().noneMatch(o -> o.type().equals(type.getName()))) implementation.add(origin(type));
            }
            List<CodeOrigin> relevant = new ArrayList<>(implementation);
            for (var listener : listeners) { relevant.add(listener.owner()); relevant.add(listener.declaringClass()); }
            List<Class<?>> actualClasses = new ArrayList<>(origins.keySet()); actualClasses.addAll(observed.dispatchClasses());
            NativeDirtRuntime.Catalog runtime = bindStartup ? runtimeReader.bind(relevant, actualClasses) : runtimeReader.read(relevant, actualClasses);
            List<Object> liveObjects = new ArrayList<>(observed.identity()); liveObjects.addAll(runtimeReader.liveIdentity(actualClasses));
            var identity = new Identity(epoch, level, resources, table, manager, liveObjects, gameTime, target.asLong(), relevant, runtime, runtimeReader.localAudit());
            current = epoch.current(level, level.getServer().getResourceManager(),
                    level.getServer().getLootData().getLootTable(NativeDirtIntrospection.TABLE), NativeDirtIntrospection.modifierManager());
            if (!current.ready()) throw new Refused(current);
            current = identity.current();
            if (!current.ready()) throw new Refused(current);
            if (bindStartup) boundGeneration = epoch.generation();
            return new Observation(new Census(epoch.generation(), gameTime, target.asLong(), dirt, layers, listeners,
                    implementation, runtime, chunks, sections, registries, graph, activeModifiers, readyCheck()), identity);
        } catch (Refused refused) {
            return failed(target, gameTime, dirt, layers, listeners, implementation, chunks, sections, registries, graph, activeModifiers, refused.check);
        } catch (NativeDirtListeners.Limit limit) {
            return failed(target, gameTime, dirt, layers, listeners, implementation, chunks, sections, registries, graph, activeModifiers,
                    denied(Reason.READ_LIMIT, "bounded Forge listener census exceeded its read budget"));
        } catch (NativeDirtListeners.Unresolved unknown) {
            return failed(target, gameTime, dirt, layers, listeners, implementation, chunks, sections, registries, graph, activeModifiers,
                    denied(Reason.LISTENER_UNRESOLVED, unknown.getMessage()));
        } catch (NativeDirtRuntime.Unsupported unsupported) {
            return failed(target, gameTime, dirt, layers, listeners, implementation, chunks, sections, registries, graph, activeModifiers,
                    denied(Reason.RUNTIME_UNPROVEN, unsupported.getMessage()));
        } catch (Exception | LinkageError unavailable) {
            return failed(target, gameTime, dirt, layers, listeners, implementation, chunks, sections, registries, graph, activeModifiers,
                    denied(Reason.API_UNAVAILABLE, "required read-only dirt introspection, origins or mappings are unavailable at runtime stage " + runtimeReader.stage()));
        }
    }
    private Observation failed(BlockPos target, long time, ResourceProof dirt, List<ResourceProof> layers,
                               List<ListenerProof> listeners, List<CodeOrigin> implementation, int chunks, int sections,
                               List<SectionProof> registries, Check graph, Integer activeModifiers, Check check) {
        return new Observation(new Census(epoch.generation(), time, target == null ? 0 : target.asLong(), dirt,
                layers, listeners, implementation, null, chunks, sections, registries, graph, activeModifiers, check), null);
    }
    private boolean inside(BlockPos p) {
        if (Math.abs((long) p.getX()) > 29_999_968 || Math.abs((long) p.getZ()) > 29_999_968) return false;
        for (int x : new int[] {-16, 16}) for (int z : new int[] {-16, 16})
            if (!level.getWorldBorder().isWithinBounds(p.offset(x, 0, z))) return false;
        return !level.isOutsideBuildHeight(p);
    }
    private ResourceProof resource(Resource resource) throws Exception {
        byte[] bytes = bytes(resource.open(), MAX_RESOURCE_BYTES);
        return new ResourceProof(resource.sourcePackId(), resource.isBuiltin(), hash(bytes), bytes.length);
    }
    private CodeOrigin origin(Class<?> type) throws Exception {
        NativeDirtRuntime.requireTrustedClassIdentity(type);
        CodeOrigin cached = origins.get(type);
        if (cached != null) return cached;
        if (origins.size() >= MAX_ORIGINS) throw stop(Reason.READ_LIMIT, "class provenance census exceeded its read budget");
        var source = type.getProtectionDomain().getCodeSource();
        if (source == null || source.getLocation() == null) throw stop(Reason.RUNTIME_UNPROVEN, "loaded class has no verifiable code origin");
        byte[] data = bytes(type.getResourceAsStream("/" + type.getName().replace('.', '/') + ".class"), MAX_CLASS_BYTES);
        CodeOrigin proof = new CodeOrigin(type.getName(), String.valueOf(type.getModule().getName()),
                NativeDirtRuntime.safeSource(source.getLocation().toExternalForm()), hash(data));
        origins.put(type, proof); return proof;
    }
    static byte[] bytes(InputStream stream, int limit) throws Exception {
        if (stream == null) throw stop(Reason.RESOURCE_UNAVAILABLE, "required resource stream is unavailable");
        try (stream) {
            byte[] bytes = stream.readNBytes(limit + 1);
            if (bytes.length > limit) throw stop(Reason.READ_LIMIT, "resource exceeds the bounded census size");
            return bytes;
        }
    }
    static String hash(byte[] bytes) throws Exception { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
    /** One independently pinned built-in Forge resource contains a documentary comment, not a modifier. */
    static boolean emptyModifierLayer(byte[] bytes, boolean builtin) {
        if (builtin && bytes.length == 254) {
            try { if (FORGE_EMPTY_GLM_SHA256.equals(hash(bytes))) return true; }
            catch (Exception unavailable) { return false; }
        }
        return emptyModifierLayer(bytes);
    }
    /** Deliberately accepts only empty lists; a replaced-away nonempty layer is still unsupported. */
    static boolean emptyModifierLayer(byte[] bytes) {
        if (bytes.length > MAX_RESOURCE_BYTES) return false;
        try (JsonReader reader = new JsonReader(new StringReader(new String(bytes, StandardCharsets.UTF_8)))) {
            reader.setLenient(false); reader.beginObject();
            boolean entries = false, replace = false;
            while (reader.hasNext()) {
                String field = reader.nextName();
                if (field.equals("replace") && !replace && reader.peek() == JsonToken.BOOLEAN) { reader.nextBoolean(); replace = true; }
                else if (field.equals("entries") && !entries && reader.peek() == JsonToken.BEGIN_ARRAY) {
                    reader.beginArray(); if (reader.hasNext()) return false; reader.endArray(); entries = true;
                } else return false;
            }
            reader.endObject();
            return entries && replace && reader.peek() == JsonToken.END_DOCUMENT;
        } catch (Exception malformed) { return false; }
    }
    private static Refused stop(Reason reason, String message) { return new Refused(denied(reason, message)); }
    private static final class Refused extends Exception {
        final Check check;
        Refused(Check check) { this.check = check; }
    }
}
