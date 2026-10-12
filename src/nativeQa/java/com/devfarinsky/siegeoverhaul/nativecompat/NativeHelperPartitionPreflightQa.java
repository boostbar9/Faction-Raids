package com.devfarinsky.siegeoverhaul.nativecompat;

import com.devfarinsky.siegeoverhaul.SiegeOverhaul;
import com.devfarinsky.siegeoverhaul.core.PerimeterBlueprint;
import com.devfarinsky.siegeoverhaul.core.PerimeterStageLayout;
import com.devfarinsky.siegeoverhaul.core.TerritoryFortification;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.EntityDataSerializer;
import net.minecraft.world.level.ChunkPos;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerAboutToStartEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** World-free title-screen proof of the reviewed cap-only partition through the actual native codec. */
@Mod.EventBusSubscriber(modid = SiegeOverhaul.MOD_ID, value = Dist.CLIENT)
public final class NativeHelperPartitionPreflightQa {
    private static final String MODE = "helper-partition-preflight";
    private static final boolean ENABLED = Boolean.getBoolean("siegeoverhaul.nativeQa")
            && MODE.equals(System.getProperty("siegeoverhaul.nativeQa.mode"));
    private static final String PIN = "64d4deeadebea0808cf38cbca69ebf72b68b3e2ed089958e3a41d1a37a15a115";
    private static final String RESOURCE = "/native-helper-partition/cap64-candidate-partition.json";
    private static final int CAP = 64;
    private static final long LIMIT_NANOS = 600_000_000_000L;
    private static final Gson JSON = new GsonBuilder().setPrettyPrinting().serializeNulls().create();
    private static final Map<String, Object> REPORT = new LinkedHashMap<>();
    private static final List<Map<String, Object>> CALLS = new ArrayList<>(), ROUNDTRIPS = new ArrayList<>();
    private static boolean attempted, finished;
    private static volatile boolean serverObserved;
    private static long started;
    private static Path evidence;
    private static Object serializerInstance;
    private static Map<String, Object> serializerContract;

    private NativeHelperPartitionPreflightQa() {}

    @SubscribeEvent
    public static void serverStarting(ServerAboutToStartEvent event) {
        if (!ENABLED) return;
        serverObserved = true;
        throw new AssertionError("World-free partition preflight observed an integrated server start");
    }

    @SubscribeEvent
    public static void clientTick(TickEvent.ClientTickEvent event) {
        if (!ENABLED || finished || event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (started == 0) started = System.nanoTime();
        try {
            require(System.nanoTime() - started < LIMIT_NANOS, "Partition preflight exceeded its hard ten-minute cap");
            assertWorldFree(mc);
            if (!(mc.screen instanceof TitleScreen)) return;
            require(!attempted, "Partition preflight was already attempted"); attempted = true;
            initialize(mc);
            assertWorldFree(mc);
            Map<String, Object> parity = NativeActiveHelperApiParity.verify(evidence);
            REPORT.put("loadedApiParity", parity);
            attestSerializer(parity);
            JsonObject pins = readPins();
            PerimeterBlueprint.Plan plan = PerimeterBlueprint.create(Set.of(new ChunkPos(8, 0)),
                    (x, z) -> PerimeterBlueprint.Surface.ready(65), PerimeterBlueprint.Palette.COBBLESTONE);
            require(plan.valid(), "Fixed flat native plan is invalid: " + plan.problemSummary());
            REPORT.put("plan", planEvidence(plan));
            require(plan.blocks().size() == 572 && plan.clearance().size() == 748 && plan.columns().size() == 220
                    && plan.materialCounts().equals(Map.of("minecraft:cobblestone", 352, "minecraft:oak_planks", 220)),
                    "Fixed plan target/clearance/material scope changed");
            require(TerritoryFortification.PRICE == 64, "Production fee changed before preflight");
            REPORT.put("productionFee", TerritoryFortification.PRICE);
            JsonObject expected = pins.getAsJsonObject("candidate");
            PerimeterStageLayout.Layout layout = PerimeterStageLayout.partition(plan, part -> validateNative(part, expected));
            PerimeterStageLayout.validate(plan, layout);
            require(CALLS.size() == 46 && layout.stages().size() == 16, "Reviewed partition/call count changed");
            var partition = new LinkedHashMap<String, Object>();
            partition.put("cap", CAP); partition.put("layoutDigest", layout.digest());
            partition.put("stages", layout.stages().stream().map(NativeHelperPartitionPreflightQa::stageEvidence).toList());
            partition.put("validatorCalls", List.copyOf(CALLS)); REPORT.put("partition", partition);
            require(JSON.toJsonTree(partition).equals(expected), "Native partition differs from the complete immutable cap-64 candidate");
            requireLossless(plan, layout);
            SerializerShape.requireStable(serializerInstance, EntityDataSerializers.COMPOUND_TAG);
            serializerContract.put("instanceStable", true);
            assertWorldFree(mc);
            REPORT.put("worldCreated", false); REPORT.put("integratedServerStarted", false);
            REPORT.put("workersCreated", 0); REPORT.put("commissionPerformed", false);
            REPORT.put("paymentCharged", 0); REPORT.put("gameplayExecuted", false);
            REPORT.put("phase", "FINISHED"); finish(mc, null);
        } catch (Throwable problem) { finish(mc, problem); }
    }

    private static void initialize(Minecraft mc) throws Exception {
        require(!Boolean.getBoolean("siegeoverhaul.nativeQa.activeHelper")
                && !Boolean.getBoolean("siegeoverhaul.nativeQa.sharedCrew")
                && !Boolean.getBoolean("siegeoverhaul.nativeQa.builderReplacement")
                && System.getProperty("siegeoverhaul.nativeQa.activeHelperCase") == null,
                "World-free preflight cannot enable another scenario");
        Path directory = Path.of(System.getProperty("siegeoverhaul.nativeQa.directory", "")).toAbsolutePath().normalize();
        require(directory.equals(directory.toRealPath()) && directory.endsWith(Path.of("build/native-helper-partition-qa/client"))
                && mc.gameDirectory.toPath().toRealPath().equals(directory), "Unexpected isolated preflight directory");
        evidence = directory.getParent().resolve("evidence");
        require(Files.isDirectory(evidence) && evidence.equals(evidence.toRealPath())
                && !Files.exists(evidence.resolve("result.json")), "Missing or previously used preflight evidence directory");
        REPORT.put("formatVersion", 1); REPORT.put("scenario", "native-helper-partition-preflight");
        REPORT.put("phase", "PREFLIGHT"); REPORT.put("candidateSha256", PIN);
        REPORT.put("sourceCommit", Files.readString(evidence.resolve("source-commit.txt")).trim());
        REPORT.put("sourceTree", Files.readString(evidence.resolve("source-tree.txt")).trim());
        REPORT.put("nativeRoundTrips", ROUNDTRIPS);
        REPORT.put("scope", "Native registry/COMPOUND_TAG partition preflight at title screen only; no Case L gameplay");
    }

    private static void assertWorldFree(Minecraft mc) throws Exception {
        require(!serverObserved && mc.level == null && mc.player == null && mc.getSingleplayerServer() == null
                && emptyWorldStorage(mc.gameDirectory.toPath().toAbsolutePath().normalize().resolve("saves")),
                "World/server/player appeared during world-free preflight");
    }

    /** The client may create the saves container; any entry still fails world-free scope. */
    private static boolean emptyWorldStorage(Path saves) throws Exception {
        if (Files.isSymbolicLink(saves)) return false;
        if (!Files.exists(saves)) return true;
        if (!Files.isDirectory(saves) || !saves.equals(saves.toRealPath())) return false;
        try (var entries = Files.list(saves)) { return entries.findAny().isEmpty(); }
    }

    private static JsonObject readPins() throws Exception {
        byte[] bytes;
        try (var input = NativeHelperPartitionPreflightQa.class.getResourceAsStream(RESOURCE)) {
            require(input != null, "Missing immutable cap-64 candidate resource"); bytes = input.readNBytes(256 * 1024 + 1);
        }
        require(bytes.length > 0 && bytes.length <= 256 * 1024
                && PIN.equals(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes))),
                "Changed or oversized cap-64 candidate resource");
        return JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8)).getAsJsonObject();
    }

    private static void attestSerializer(Map<String, Object> parity) throws Exception {
        Map<?, ?> supplier = (Map<?, ?>) parity.get("forgeSuperclassTail");
        Path path = Path.of((String) supplier.get("provider")); String hash = (String) supplier.get("supplierSha256");
        serializerInstance = EntityDataSerializers.COMPOUND_TAG;
        List<Method> methods = SerializerShape.verify(serializerInstance, EntityDataSerializer.class,
                EntityDataSerializers.class, FriendlyByteBuf.class, CompoundTag.class);
        Class<?> implementation = serializerInstance.getClass();
        var closure = new LinkedHashSet<Class<?>>(List.of(CompoundTag.class, ListTag.class, NbtUtils.class,
                FriendlyByteBuf.class, EntityDataSerializers.class, EntityDataSerializer.class, implementation));
        methods.forEach(method -> closure.add(method.getDeclaringClass()));
        if (methods.get(1).getReturnType() != Object.class) closure.add(methods.get(1).getReturnType());
        require(closure.size() >= 7 && closure.size() <= 10, "Unexpected serializer resource closure");
        var proofs = new ArrayList<Map<String, String>>();
        for (Class<?> type : closure) proofs.add(NativeActiveHelperApiParity.TailResource.verify(type, path, hash));
        serializerContract = new LinkedHashMap<>();
        serializerContract.put("interface", EntityDataSerializer.class.getName());
        serializerContract.put("fieldOwner", EntityDataSerializers.class.getName()); serializerContract.put("field", "COMPOUND_TAG");
        serializerContract.put("implementation", implementation.getName()); serializerContract.put("sameDefiningLoader", true);
        serializerContract.put("implementationConcrete", true); serializerContract.put("instanceStable", false);
        serializerContract.put("methods", methods.stream().map(NativeHelperPartitionPreflightQa::methodEvidence).toList());
        REPORT.put("serializerContract", serializerContract);
        REPORT.put("compoundTagImplementation", implementation.getName()); REPORT.put("serializerParity", List.copyOf(proofs));
        SerializerShape.requireStable(serializerInstance, EntityDataSerializers.COMPOUND_TAG);
    }

    private static Map<String, Object> methodEvidence(Method method) {
        var result = new LinkedHashMap<String, Object>();
        result.put("name", method.getName()); result.put("owner", method.getDeclaringClass().getName());
        result.put("parameters", java.util.Arrays.stream(method.getParameterTypes()).map(Class::getName).toList());
        result.put("returnType", method.getReturnType().getName()); result.put("public", true);
        result.put("static", false); result.put("abstract", false); result.put("returnCompatible", true);
        result.put("bridge", method.isBridge()); result.put("synthetic", method.isSynthetic()); return result;
    }

    /** Actual field-instance metadata; no assumed anonymous owner or method-body claim. */
    static final class SerializerShape {
        private SerializerShape() {}
        static List<Method> verify(Object instance, Class<?> type, Class<?> fieldOwner, Class<?> buffer, Class<?> payload) throws Exception {
            if (instance == null || !type.isInterface() || !type.isInstance(instance)) throw new AssertionError("Wrong native serializer instance/type");
            verifyType(instance.getClass(), type, fieldOwner);
            return methods(instance.getClass(), buffer, payload);
        }
        static void verifyType(Class<?> implementation, Class<?> type, Class<?> fieldOwner) {
            if (!type.isAssignableFrom(implementation) || implementation.isInterface() || Modifier.isAbstract(implementation.getModifiers()))
                throw new AssertionError("Native serializer implementation is not concrete");
            if (implementation.getClassLoader() != type.getClassLoader() || type.getClassLoader() != fieldOwner.getClassLoader())
                throw new AssertionError("Native serializer defining loader differs");
        }
        static List<Method> methods(Class<?> implementation, Class<?> buffer, Class<?> payload) throws Exception {
            Method write = implementation.getMethod("write", buffer, Object.class);
            Method read = implementation.getMethod("read", buffer);
            for (Method method : List.of(write, read)) {
                int flags = method.getModifiers();
                if (!Modifier.isPublic(flags) || Modifier.isStatic(flags) || Modifier.isAbstract(flags))
                    throw new AssertionError("Native serializer method is not public concrete instance behavior");
            }
            if (write.getReturnType() != void.class) throw new AssertionError("Native serializer write return changed");
            if (read.getReturnType() != Object.class && !payload.isAssignableFrom(read.getReturnType()))
                throw new AssertionError("Native serializer read return is incompatible");
            return List.of(write, read);
        }
        static void requireStable(Object initial, Object current) {
            if (initial == null || initial != current) throw new AssertionError("Native COMPOUND_TAG instance changed");
        }
    }

    private static String validateNative(PerimeterStageLayout.Stage part, JsonObject expected) {
        int ordinal = CALLS.size();
        require(ordinal < 46, "Extra native partition validator call");
        CompoundTag blueprint = TerritoryFortification.blueprint(part.targets(), part.min(), part.max());
        int entries = blueprint.getList("blocks", 10).size();
        require(entries == part.targets().size(), "Native blueprint omitted an unregistered target material");
        SerializerShape.requireStable(serializerInstance, EntityDataSerializers.COMPOUND_TAG);
        String nativeProblem = BlueprintNetworkBudget.problem(blueprint);
        SerializerShape.requireStable(serializerInstance, EntityDataSerializers.COMPOUND_TAG);
        var nativeEvidence = new LinkedHashMap<String, Object>();
        nativeEvidence.put("ordinal", ordinal); nativeEvidence.put("index", part.index());
        nativeEvidence.put("layoutStageDigest", part.digest()); nativeEvidence.put("blueprintBlockCount", entries);
        nativeEvidence.put("targetCount", part.targets().size()); nativeEvidence.put("nativeRoundTripPassed", nativeProblem == null);
        nativeEvidence.put("problem", nativeProblem); ROUNDTRIPS.add(nativeEvidence);
        // Native rejection is fatal, never a reason to silently adopt a different partition.
        String result = Decision.problem(nativeProblem, part.targets().size());
        var call = Map.<String, Object>of("index", part.index(), "columnCount", part.columns().size(),
                "targetCount", part.targets().size(), "layoutStageDigest", part.digest(), "capAccepts", result == null);
        require(JSON.toJsonTree(call).equals(expected.getAsJsonArray("validatorCalls").get(ordinal)),
                "Native validator proposal/order differs at call " + ordinal);
        CALLS.add(call); return result;
    }

    /** JDK-only decision boundary is executed directly by focused negative regressions. */
    static final class Decision {
        private Decision() {}
        static String problem(String nativeProblem, int targets) {
            if (nativeProblem != null) throw new AssertionError("Native serializer rejected reviewed proposal: " + nativeProblem);
            if (targets < 1 || targets > 572) throw new AssertionError("Unexpected bounded native proposal target count");
            return targets > 64 ? "QA-only reviewed cap-64 partition pressure" : null;
        }
    }

    private static Map<String, Object> stageEvidence(PerimeterStageLayout.Stage part) {
        var stage = new LinkedHashMap<String, Object>();
        stage.put("index", part.index()); stage.put("columns", part.columns()); stage.put("targets", targets(part.targets()));
        stage.put("clearance", part.clearance().stream().sorted().toList()); stage.put("targetCount", part.targets().size());
        stage.put("clearanceCount", part.clearance().size()); stage.put("reservationCount", part.reservation().size());
        stage.put("materials", materials(part.targets())); stage.put("min", coordinates(part.min())); stage.put("max", coordinates(part.max()));
        stage.put("layoutStageDigest", part.digest()); return stage;
    }

    private static Map<String, Object> planEvidence(PerimeterBlueprint.Plan plan) {
        var result = new LinkedHashMap<String, Object>();
        result.put("targetCount", plan.blocks().size()); result.put("clearanceCount", plan.clearance().size());
        result.put("columnCount", plan.columns().size()); result.put("materials", plan.materialCounts());
        result.put("min", coordinates(plan.min())); result.put("max", coordinates(plan.max()));
        result.put("columns", plan.columns().stream().map(column -> new BlockPos(column.base().getX(), 0, column.base().getZ()).asLong()).sorted().toList());
        result.put("targets", targets(plan.blocks())); result.put("clearance", plan.clearance().stream().sorted().toList()); return result;
    }

    private static List<Map<String, Object>> targets(Map<Long, String> targets) {
        return targets.entrySet().stream().sorted(Map.Entry.comparingByKey())
                .map(entry -> Map.<String, Object>of("cell", entry.getKey(), "material", entry.getValue())).toList();
    }
    private static Map<String, Integer> materials(Map<Long, String> targets) {
        var result = new LinkedHashMap<String, Integer>(); targets.values().forEach(material -> result.merge(material, 1, Integer::sum)); return result;
    }
    private static List<Integer> coordinates(BlockPos pos) { return List.of(pos.getX(), pos.getY(), pos.getZ()); }

    private static void requireLossless(PerimeterBlueprint.Plan plan, PerimeterStageLayout.Layout layout) {
        var targets = new LinkedHashMap<Long, String>(); var clearance = new HashSet<Long>(); var reserved = new HashSet<Long>();
        for (var part : layout.stages()) {
            for (var entry : part.targets().entrySet()) require(targets.put(entry.getKey(), entry.getValue()) == null, "Overlapping native target stage");
            for (long cell : part.clearance()) require(clearance.add(cell), "Overlapping native clearance stage");
            for (long cell : part.reservation()) require(reserved.add(cell), "Overlapping native reservation stage");
        }
        var expected = new HashSet<>(plan.blocks().keySet()); expected.addAll(plan.clearance());
        require(targets.equals(plan.blocks()) && clearance.equals(plan.clearance()) && reserved.equals(expected)
                && reserved.size() == 1320, "Lossy native partition union");
    }

    private static void finish(Minecraft mc, Throwable failure) {
        if (finished) return;
        finished = true;
        try { assertWorldFree(mc); } catch (Throwable worldFailure) { if (failure == null) failure = worldFailure; }
        REPORT.put("result", failure == null ? "passed" : "failed");
        REPORT.put("elapsedSeconds", (System.nanoTime() - started) / 1_000_000_000.0);
        if (failure != null) {
            REPORT.put("failure", failure.toString());
            com.devfarinsky.siegeoverhaul.FactionLogger.LOG.error("World-free partition preflight failed", failure);
        }
        try {
            if (evidence != null) Files.writeString(evidence.resolve("result.json"), JSON.toJson(REPORT), StandardOpenOption.CREATE_NEW);
        } catch (Exception writeFailure) { com.devfarinsky.siegeoverhaul.FactionLogger.LOG.error("Cannot write partition preflight evidence", writeFailure); }
        mc.stop();
    }
    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
