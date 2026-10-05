package com.devfarinsky.siegeoverhaul.nativecompat;

import cpw.mods.modlauncher.Launcher;
import cpw.mods.modlauncher.TransformerAuditTrail;
import cpw.mods.modlauncher.api.IEnvironment;
import cpw.mods.modlauncher.api.IModuleLayerManager;
import cpw.mods.modlauncher.api.ITransformerActivity;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.loading.FMLLoader;
import net.minecraftforge.fml.loading.moddiscovery.ModFile;
import net.minecraftforge.fml.loading.moddiscovery.ModFileInfo;
import net.minecraftforge.fml.loading.moddiscovery.ModInfo;
import org.spongepowered.asm.mixin.Mixins;

import java.io.InputStream;
import java.lang.management.ManagementFactory;
import java.lang.module.ModuleReference;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Concrete read-only production binding for a trusted ordinary JVM, not malicious-owner attestation.
 * Entire loaded module contents and original mod files are pinned; launcher records and actual
 * transformation audit metadata are compared. This never asks transformers to run or opens modules.
 */
public final class NativeDirtRuntime {
    static final int MAX_MODS = 32, MAX_MODULES = 256, MAX_ENTRIES = 65_536, MAX_SERVICES = 64;
    static final long MAX_MODULE_BYTES = 256L * 1024 * 1024, MAX_TOTAL_BYTES = 1024L * 1024 * 1024;
    static final int MAX_ENTRY_BYTES = 32 * 1024 * 1024;
    public record Artifact(String mod, String version, String sourceKind, String sha256, long bytes) {}
    public record ModuleProof(String layer, String name, String version, String contentSha256, List<String> providers) {
        public ModuleProof { providers = List.copyOf(providers); }
    }
    public record Activity(String owner, String kind, List<String> contextDigests) {
        public Activity { contextDigests = List.copyOf(contextDigests); }
    }
    public record FirstParty(String mod, String module, List<String> trustedClasses) {
        public FirstParty { trustedClasses = List.copyOf(trustedClasses); }
    }
    public record Catalog(String launch, String distribution, String javaRuntime, List<Artifact> mods,
                          List<ModuleProof> modules, List<Map<String, String>> services,
                          List<String> pendingMixins, List<Activity> transformations, FirstParty firstParty) {
        public Catalog {
            mods = List.copyOf(mods); modules = List.copyOf(modules);
            services = services.stream().map(Map::copyOf).toList(); pendingMixins = List.copyOf(pendingMixins);
            transformations = List.copyOf(transformations);
        }
        /** Built-in reviewed profiles need no circular hash of their containing Siege JAR. */
        Catalog reviewedShape() {
            if (firstParty == null) return this;
            List<Artifact> portableMods = mods.stream().map(a -> a.mod().equals(firstParty.mod())
                    ? new Artifact(a.mod(), a.version(), a.sourceKind(), "trusted-first-party", 0) : a).toList();
            List<ModuleProof> portableModules = modules.stream().map(m -> m.layer().equals("GAME") && m.name().equals(firstParty.module())
                    ? new ModuleProof(m.layer(), m.name(), m.version(), "trusted-first-party", m.providers()) : m).toList();
            return new Catalog(launch, distribution, javaRuntime, portableMods, portableModules, services,
                    pendingMixins, transformations, firstParty);
        }
    }
    record FileStamp(long bytes, long modified, String key, String sha256) {}
    record ModuleDigest(String sha256, long bytes) {}
    private final Map<Path, FileStamp> files = new HashMap<>();
    private final Map<ModuleReference, ModuleDigest> modules = new HashMap<>();
    private long bytesRead, cachedFileChecks, cachedModuleChecks;
    private boolean startupBound;
    private Object boundSiegeFile;
    private record RawActivity(String owner, String kind, List<String> context) { RawActivity { context = List.copyOf(context); } }
    private List<RawActivity> lastRawAudit = List.of();
    Object localAudit() { return lastRawAudit; } // opaque internal equality only; never part of exported Catalog
    private final Map<Module, ModuleReference> boundModules = new java.util.IdentityHashMap<>();
    private final Map<Module, ClassLoader> boundLoaders = new java.util.IdentityHashMap<>();
    public record Metrics(long bytesRead, int artifactsHashed, int modulesHashed, long cachedFileChecks, long cachedModuleChecks) {}
    /** Observation counters only; no filesystem read or runtime mutation. */
    public Metrics metrics() { return new Metrics(bytesRead, files.size(), modules.size(), cachedFileChecks, cachedModuleChecks); }

    /** Explicit off-pulse startup/admission stage. No other API can introduce a new runtime input. */
    Catalog bind(List<NativeDirtPolicy.CodeOrigin> relevant, List<Class<?>> actualClasses) throws Exception {
        if (startupBound) return read(relevant, actualClasses);
        Catalog catalog = collect(relevant, actualClasses); freezeStartupInputs(); return catalog;
    }
    /** Work-tick path: refuses unbound/new inputs before any artifact/module content IO. */
    Catalog read(List<NativeDirtPolicy.CodeOrigin> relevant, List<Class<?>> actualClasses) throws Exception {
        if (!startupBound) throw new Unsupported("Startup runtime catalog must be bound outside the native work pulse");
        return collect(relevant, actualClasses);
    }
    void freezeStartupInputs() { startupBound = true; }
    private Catalog collect(List<NativeDirtPolicy.CodeOrigin> relevant, List<Class<?>> actualClasses) throws Exception {
        if (Launcher.INSTANCE == null || ModList.get() == null
                || !"1.20.1".equals(FMLLoader.versionInfo().mcVersion()) || !"47.4.16".equals(FMLLoader.versionInfo().forgeVersion()))
            throw new Unsupported("Only a reviewed 1.20.1/47.4.16 launch profile is supported");
        if (Boolean.getBoolean("mixin.hotSwap") || instrumented(ManagementFactory.getRuntimeMXBean().getInputArguments()))
            throw new Unsupported("Instrumented/agent or mixin hot-swap launch is unsupported");
        var environment = Launcher.INSTANCE.environment();
        var manager = environment.findModuleLayerManager().orElseThrow(() -> new Unsupported("Missing loaded module layers"));
        List<ModuleProof> moduleProof = new ArrayList<>();
        Map<String, ModuleReference> gameModules = new HashMap<>();
        for (var layer : IModuleLayerManager.Layer.values()) {
            var loadedLayer = manager.getLayer(layer).orElseThrow(() -> new Unsupported("Incomplete loaded module layers"));
            for (Module module : loadedLayer.modules()) {
                var resolved = loadedLayer.configuration().findModule(module.getName()).orElseThrow(() -> new Unsupported("Loaded Module has no resolved reference"));
                if (moduleProof.size() >= MAX_MODULES) throw new Unsupported("Loaded module census exceeds its bound");
                ModuleReference reference = resolved.reference();
                if (startupBound && (boundModules.get(module) != reference || boundLoaders.get(module) != module.getClassLoader()))
                    throw new Unsupported("A loaded Module/ClassLoader identity changed after startup binding");
                if (!startupBound) { boundModules.put(module, reference); boundLoaders.put(module, module.getClassLoader()); }
                if (layer == IModuleLayerManager.Layer.GAME) gameModules.put(reference.descriptor().name(), reference);
                var descriptor = reference.descriptor();
                String location = reference.location().orElseThrow(() -> new Unsupported("Module source is unavailable")).getScheme();
                // The ordinary trusted Java runtime is named/versioned, not re-hashed as game content.
                String hash = "jrt".equals(location) ? "trusted-java-runtime" : module(reference).sha256();
                List<String> providers = new ArrayList<>();
                for (var service : descriptor.provides()) for (String provider : service.providers()) {
                    if (providers.size() >= MAX_SERVICES) throw new Unsupported("Module service census exceeds its bound");
                    providers.add(service.service() + "=" + provider);
                }
                providers.sort(String::compareTo);
                moduleProof.add(new ModuleProof(layer.name(), descriptor.name(), descriptor.rawVersion().orElse(""), hash, providers));
            }
        }
        moduleProof.sort(Comparator.comparing(ModuleProof::layer).thenComparing(ModuleProof::name));
        List<Artifact> modProof = new ArrayList<>();
        var loaded = ModList.get().getMods();
        if (loaded.isEmpty() || loaded.size() > MAX_MODS) throw new Unsupported("Unsupported loaded mod count");
        for (var mod : loaded) {
            if (mod.getClass() != ModInfo.class || mod.getOwningFile().getClass() != ModFileInfo.class
                    || mod.getOwningFile().getFile().getClass() != ModFile.class)
                throw new Unsupported("Unknown mod/file metadata implementation");
            Path path = mod.getOwningFile().getFile().getFilePath();
            if (Files.isRegularFile(path)) {
                var file = file(path);
                modProof.add(new Artifact(mod.getModId(), mod.getVersion().toString(),
                        FMLLoader.isProduction() ? "packaged-file" : "development-file", file.sha256(), file.bytes()));
            } else {
                // Userdev's own mod combines compiled-class and resource directories in one SecureJar
                // module. Hash that entire resolved module, never only the first/primary directory.
                if (FMLLoader.isProduction()) throw new Unsupported("Packaged profile has a non-file mod input");
                String moduleName = mod.getOwningFile().getFile().getSecureJar().name();
                ModuleReference reference = gameModules.get(moduleName);
                if (reference == null) throw new Unsupported("Development mod has no exact resolved GAME module");
                var content = module(reference);
                modProof.add(new Artifact(mod.getModId(), mod.getVersion().toString(), "development-combined-module", content.sha256(), content.bytes()));
            }
        }
        modProof.sort(Comparator.comparing(Artifact::mod));
        for (int i = 1; i < modProof.size(); i++) if (modProof.get(i).mod().equals(modProof.get(i - 1).mod()))
            throw new Unsupported("Duplicate loaded mod identity");
        List<Map<String, String>> services = new ArrayList<>();
        var entries = environment.getProperty(IEnvironment.Keys.MODLIST.get()).orElseThrow(() -> new Unsupported("Missing active launcher census"));
        if (entries.size() > MAX_SERVICES) throw new Unsupported("Launcher service census exceeds its bound");
        for (var entry : entries) services.add(safeService(entry));
        services.sort(Comparator.comparing(m -> new TreeMap<>(m).toString()));
        // Config's package is not exported by Mixin 0.8.5. Do not open it or invoke
        // reflective getters. A pending configuration is unsupported; applied mixins
        // are bound by full module inputs and the actual relevant-class audit trail.
        if (!Mixins.getConfigs().isEmpty() || Mixins.getUnvisitedCount() != 0)
            throw new Unsupported("Pending mixin configurations require fresh completed runtime admission");
        List<String> mixins = List.of();
        var audit = environment.getProperty(IEnvironment.Keys.AUDITTRAIL.get()).orElseThrow(() -> new Unsupported("Missing transformation audit trail"));
        if (audit.getClass() != TransformerAuditTrail.class) throw new Unsupported("Unknown transformation audit implementation");
        Class<?> activityClass = Class.forName("cpw.mods.modlauncher.TransformerAuditTrail$TransformerActivity", false, audit.getClass().getClassLoader());
        List<Activity> transformations = new ArrayList<>();
        List<RawActivity> rawAudit = new ArrayList<>();
        int auditContextBytes = 0;
        for (String name : auditNames(relevant, actualClasses.stream().map(Class::getName).toList())) {
            var activities = audit.getActivityFor(name);
            if (activities.size() > 128) throw new Unsupported("Relevant transformation census exceeds its bound");
            for (var activity : activities) {
                if (activity.getClass() != activityClass) throw new Unsupported("Unknown transformation activity record");
                if (activity.getType() == ITransformerActivity.Type.REASON) continue; // load reason does not transform bytecode
                String[] context = activity.getContext();
                if (context.length > 16) throw new Unsupported("Transformation context exceeds its bound");
                for (String part : context) {
                    if (part == null || part.length() > 4096) throw new Unsupported("Unknown transformation context; no values exported");
                    auditContextBytes += 4 + part.length() * 2;
                }
                if (auditContextBytes > 262_144 || transformations.size() >= 2048)
                    throw new Unsupported("Transformation metadata exceeds its total bound; no values exported");
                transformations.add(safeActivity(name, activity.getType().name(), context));
                rawAudit.add(new RawActivity(name, activity.getType().name(), List.of(context.clone())));
            }
        }
        liveIdentity(actualClasses); // every actual Class must belong to the exact bound Module/reference/loader
        lastRawAudit = List.copyOf(rawAudit);
        return new Catalog((FMLLoader.isProduction() ? "production:" : "development:") + FMLLoader.launcherHandlerName(), FMLLoader.getDist().name(),
                System.getProperty("java.runtime.version") + "/" + System.getProperty("java.vm.name"), modProof,
                moduleProof, services, mixins, transformations, firstParty(manager));
    }
    private FirstParty firstParty(IModuleLayerManager manager) throws Exception {
        Module own = NativeDirtPolicy.class.getModule(); ClassLoader loader = NativeDirtPolicy.class.getClassLoader();
        var fileInfo = ModList.get().getModFileById("siegeoverhaul");
        var game = manager.getLayer(IModuleLayerManager.Layer.GAME).orElseThrow(() -> new Unsupported("Missing first-party GAME layer"));
        if (!own.isNamed() || loader == null || fileInfo == null || fileInfo.getClass() != ModFileInfo.class
                || fileInfo.getFile().getClass() != ModFile.class
                || !fileInfo.getFile().getSecureJar().name().equals(own.getName())
                || game.findModule(own.getName()).orElse(null) != own || !boundModules.containsKey(own)
                || boundLoaders.get(own) != loader)
            throw new Unsupported("The actual Siege ModFile/Module/ClassLoader trust anchor is unavailable");
        if (startupBound && boundSiegeFile != fileInfo.getFile())
            throw new Unsupported("The actual Siege ModFile identity changed after startup binding");
        if (!startupBound) boundSiegeFile = fileInfo.getFile();
        List<Class<?>> trusted = trustedFirstPartyClasses();
        for (Class<?> type : trusted) if (type.getModule() != own || type.getClassLoader() != loader)
            throw new Unsupported("An explicit first-party class escaped the actual Siege module binding");
        return new FirstParty("siegeoverhaul", own.getName(), trusted.stream().map(Class::getName).sorted().toList());
    }
    static List<Class<?>> trustedFirstPartyClasses() {
        // Explicit first-party implementation identities. Never a namespace/package-name exemption.
        return List.of(NativeDirtPolicy.class, NativeDirtCensus.class, NativeDirtRuntime.class,
                NativeDirtIntrospection.class, NativeDirtListeners.class,
                com.devfarinsky.siegeoverhaul.RaidEvents.class,
                com.devfarinsky.siegeoverhaul.core.CoreCivilians.class,
                com.devfarinsky.siegeoverhaul.compat.EnemyHiringProtection.class,
                com.devfarinsky.siegeoverhaul.compat.SiegeCorpseCleanup.class,
                NativeConstructionEvents.class, NativeConstructionGuard.class);
    }
    static void requireTrustedClassIdentity(Class<?> type) throws Unsupported {
        for (Class<?> known : trustedFirstPartyClasses()) {
            if (type.getName().equals(known.getName()) && type != known)
                throw new Unsupported("An explicit first-party class name resolved to a different Class identity");
        }
    }
    List<Object> liveIdentity(List<Class<?>> classes) throws Unsupported {
        if (classes.size() > 512) throw new Unsupported("Live class binding exceeds its bound");
        List<Object> identity = new ArrayList<>();
        for (Class<?> type : classes.stream().distinct().sorted(Comparator.comparing(Class::getName)).toList()) {
            requireTrustedClassIdentity(type);
            Module module = type.getModule(); ModuleReference reference = boundModules.get(module);
            ClassLoader loader = type.getClassLoader();
            if (!module.isNamed() || reference == null || loader == null || boundLoaders.get(module) != loader
                    || module.getClassLoader() != loader || !reference.descriptor().name().equals(module.getName()))
                throw new Unsupported("A relevant Class is outside the bound runtime Module/reference/ClassLoader");
            identity.add(type); identity.add(module); identity.add(loader); identity.add(reference);
        }
        return List.copyOf(identity);
    }
    static List<String> auditNames(List<NativeDirtPolicy.CodeOrigin> relevant, List<String> dispatchClasses) throws Unsupported {
        java.util.Set<String> names = new java.util.TreeSet<>(dispatchClasses);
        for (var origin : relevant) names.add(origin.type());
        if (names.size() > 512) throw new Unsupported("Relevant dispatch-class audit set exceeds its bound");
        for (String name : names) if (name == null || name.length() > 4096) throw new Unsupported("Unknown dispatch class name");
        return List.copyOf(names);
    }
    static Activity safeActivity(String owner, String kind, String[] context) throws Exception {
        if (context.length > 16) throw new Unsupported("Transformation context exceeds its bound; no values exported");
        List<String> digests = new ArrayList<>();
        for (String part : context) {
            if (part == null || part.length() > 4096) throw new Unsupported("Unknown transformation context; no values exported");
            // Preserve every Java String exactly, including distinct unpaired surrogates.
            // UTF-8 replacement encoding would merge those into the same digest input.
            ByteBuffer framed = ByteBuffer.allocate(4 + part.length() * 2).putInt(part.length());
            for (int i = 0; i < part.length(); i++) framed.putChar(part.charAt(i));
            digests.add(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(framed.array())));
        }
        return new Activity(owner, kind, digests);
    }
    static Map<String, String> safeService(Map<String, String> entry) throws Unsupported {
        if (!entry.keySet().equals(java.util.Set.of("name", "type", "file")))
            throw new Unsupported("Unknown launcher metadata schema; no values exported");
        String name = entry.get("name"), type = entry.get("type"), file = entry.get("file");
        if (name == null || !name.matches("[A-Za-z0-9_.-]{1,128}")
                || !("PLUGINSERVICE".equals(type) || "TRANSFORMATIONSERVICE".equals(type))
                || file == null || !file.matches("[A-Za-z0-9_.+-]{1,256}"))
            throw new Unsupported("Unsupported launcher metadata values; no values exported");
        return Map.of("name", name, "type", type, "file", file);
    }
    static String safeSource(String value) throws Unsupported {
        if (value == null || value.length() > 4096) throw new Unsupported("Unsupported code-source URI; no value exported");
        try {
            var uri = new java.net.URI(value);
            if (uri.getRawUserInfo() != null || uri.getRawQuery() != null || uri.getRawFragment() != null)
                throw new Unsupported("Code-source URI contains unsupported private components; no value exported");
            if ("jar".equals(uri.getScheme())) {
                String nested = uri.getRawSchemeSpecificPart(); int end = nested.indexOf("!/");
                if (nested.startsWith("jar:") || end < 0 || end != nested.length() - 2)
                    throw new Unsupported("Only a shallow local jar-root code-source URI is supported; no value exported");
                safeSource(nested.substring(0, end));
            } else if (!("file".equals(uri.getScheme()) || "union".equals(uri.getScheme()))
                    || uri.getRawAuthority() != null && !uri.getRawAuthority().isEmpty())
                throw new Unsupported("Unsupported code-source URI origin; no value exported");
            return value;
        } catch (java.net.URISyntaxException malformed) { throw new Unsupported("Malformed code-source URI; no value exported"); }
    }
    static boolean instrumented(List<String> arguments) throws Unsupported {
        if (arguments.size() > 512) throw new Unsupported("Launch argument census exceeds its bound; no arguments exported");
        for (String argument : arguments) {
            if (argument == null || argument.length() > 16_384)
                throw new Unsupported("Launch argument size exceeds its bound; no arguments exported");
            if (instrumentation(argument)) return true;
        }
        return false;
    }
    static boolean instrumentation(String argument) {
        String lower = argument.toLowerCase(java.util.Locale.ROOT);
        return lower.startsWith("-javaagent") || lower.startsWith("-agentlib") || lower.startsWith("-agentpath")
                || lower.startsWith("-xrun") || lower.contains("mixin.hotswap=true");
    }
    FileStamp file(Path source) throws Exception {
        Path path = source.toRealPath();
        if (!Files.isRegularFile(path)) throw new Unsupported("Exploded/virtual mod inputs require a separately audited profile");
        var attributes = Files.readAttributes(path, java.nio.file.attribute.BasicFileAttributes.class);
        if (attributes.size() > MAX_MODULE_BYTES) throw new Unsupported("Mod artifact exceeds the read bound");
        String key = String.valueOf(attributes.fileKey());
        FileStamp cached = files.get(path);
        if (cached != null) {
            cachedFileChecks++;
            if (cached.bytes() != attributes.size() || cached.modified() != attributes.lastModifiedTime().toMillis() || !cached.key().equals(key))
                throw new Unsupported("Loaded mod file changed during this live session");
            return cached;
        }
        if (startupBound) throw new Unsupported("A new mod file appeared after startup binding");
        if (files.size() >= MAX_MODS) throw new Unsupported("Mod artifact count exceeds the read bound");
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        long count;
        try (InputStream stream = Files.newInputStream(path)) { count = update(digest, stream, MAX_MODULE_BYTES); }
        var after = Files.readAttributes(path, java.nio.file.attribute.BasicFileAttributes.class);
        if (count != attributes.size() || after.size() != attributes.size() || !after.lastModifiedTime().equals(attributes.lastModifiedTime()))
            throw new Unsupported("Loaded artifact changed while being read");
        var result = new FileStamp(count, attributes.lastModifiedTime().toMillis(), key, HexFormat.of().formatHex(digest.digest()));
        files.put(path, result); return result;
    }
    ModuleDigest module(ModuleReference reference) throws Exception {
        ModuleDigest existing = modules.get(reference);
        if (existing != null) { cachedModuleChecks++; return existing; }
        if (startupBound) throw new Unsupported("A new module identity appeared after startup binding");
        if (modules.size() >= MAX_MODULES) throw new Unsupported("Module count exceeds the read bound");
        MessageDigest digest = MessageDigest.getInstance("SHA-256"); long total = 0;
        try (var reader = reference.open(); var listing = reader.list()) {
            List<String> names = listing.limit(MAX_ENTRIES + 1L).sorted().toList();
            if (names.size() > MAX_ENTRIES) throw new Unsupported("Module resource count exceeds the read bound");
            for (String name : names) {
                if (name.endsWith("/")) continue;
                if (name.length() > 4096) throw new Unsupported("Module entry name exceeds the read bound");
                byte[] key = name.getBytes(StandardCharsets.UTF_8);
                digest.update(ByteBuffer.allocate(4).putInt(key.length).array()); digest.update(key);
                MessageDigest entry = MessageDigest.getInstance("SHA-256");
                try (InputStream stream = reader.open(name).orElseThrow(() -> new Unsupported("Module resource missing"))) {
                    total += update(entry, stream, MAX_ENTRY_BYTES);
                }
                if (total > MAX_MODULE_BYTES) throw new Unsupported("Module contents exceed the read bound");
                digest.update(entry.digest());
            }
        }
        var result = new ModuleDigest(HexFormat.of().formatHex(digest.digest()), total); modules.put(reference, result); return result;
    }
    private long update(MessageDigest digest, InputStream stream, long bound) throws Exception {
        long read = 0; byte[] buffer = new byte[8192]; int count;
        while ((count = stream.read(buffer)) != -1) {
            read += count; bytesRead += count;
            if (read > bound || bytesRead > MAX_TOTAL_BYTES) throw new Unsupported("Runtime artifact census exceeds its byte budget");
            digest.update(buffer, 0, count);
        }
        return read;
    }
    static final class Unsupported extends Exception { Unsupported(String message) { super(message); } }
}
