package com.devfarinsky.siegeoverhaul.nativecompat;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.talhanation.workers.entities.BuilderEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.common.util.ITeleporter;
import net.minecraftforge.fml.ModList;

import java.net.JarURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Read-only loaded-provider check, before creating any lifecycle fixture world. */
final class NativeActiveHelperApiParity {
    private static final String CONTRACT = "214c5fa633fdfae31f510016ade492ffab239ad8a1752730089979edb1b29aa5";
    private static final List<String> CHAIN = List.of(
            "com.talhanation.workers.entities.BuilderEntity", "com.talhanation.workers.entities.AbstractWorkerEntity",
            "com.talhanation.recruits.entities.AbstractChunkLoaderEntity", "com.talhanation.recruits.entities.BowmanEntity",
            "com.talhanation.recruits.entities.AbstractRecruitEntity", "com.talhanation.recruits.entities.AbstractInventoryEntity",
            "com.talhanation.recruits.pathfinding.AsyncPathfinderMob", "net.minecraft.world.entity.PathfinderMob",
            "net.minecraft.world.entity.Mob", "net.minecraft.world.entity.LivingEntity", "net.minecraft.world.entity.Entity");

    private NativeActiveHelperApiParity() {}

    static Map<String, Object> verify(Path evidence) throws Exception {
        Path gate = Path.of(System.getProperty("siegeoverhaul.nativeQa.helperApiGate", "")).toAbsolutePath().normalize();
        require(Files.isRegularFile(gate) && !Files.isSymbolicLink(gate), "Missing native helper prelaunch gate");
        JsonObject receipt = JsonParser.parseString(Files.readString(gate)).getAsJsonObject();
        require(receipt.get("formatVersion").getAsInt() == 1 && receipt.get("semanticApiGatePassed").getAsBoolean()
                && !receipt.get("gameplayAccepted").getAsBoolean() && receipt.get("loadedArtifactParityRequired").getAsBoolean()
                && CONTRACT.equals(receipt.get("reviewedGateContractSha256").getAsString()), "Unreviewed native API gate contract");
        for (String key : List.of("sourceCommit", "sourceTree")) {
            String file = key.equals("sourceCommit") ? "source-commit.txt" : "source-tree.txt";
            require(Files.readString(evidence.resolve(file)).trim().equals(receipt.get(key).getAsString()), "Gate/source identity differs: " + key);
        }
        var versions = receipt.getAsJsonObject("versions");
        require("1.20.1".equals(versions.get("minecraftVersion").getAsString())
                && "47.4.16".equals(versions.get("forgeVersion").getAsString())
                && "official".equals(versions.get("mappingChannel").getAsString())
                && "1.20.1".equals(versions.get("mappingVersion").getAsString()), "Changed API runtime versions");
        for (var expected : Map.of("minecraft", "1.20.1", "forge", "47.4.16", "workers", "2.0.3", "recruits", "1.15.2").entrySet())
            require(ModList.get().getModContainerById(expected.getKey()).orElseThrow().getModInfo().getVersion().toString().equals(expected.getValue()),
                    "Loaded native version differs: " + expected.getKey());
        Map<String, String> suppliers = new LinkedHashMap<>();
        for (var value : receipt.getAsJsonArray("suppliers")) {
            var item = value.getAsJsonObject(); Path path = Path.of(item.get("canonicalPath").getAsString());
            String hash = item.get("sha256").getAsString();
            require(path.isAbsolute() && path.equals(path.toRealPath()) && Files.isRegularFile(path)
                    && hash.equals(hash(path)) && suppliers.put(path.toString(), hash) == null, "Changed/duplicate loaded supplier");
        }
        require(suppliers.size() == 3, "Unexpected native supplier closure");
        var classes = new LinkedHashMap<String, Object>();
        var codeSourceUrls = new LinkedHashMap<String, String>();
        for (var value : receipt.getAsJsonArray("classes")) {
            var item = value.getAsJsonObject(); String name = item.get("name").getAsString();
            Class<?> type = Class.forName(name, false, BuilderEntity.class.getClassLoader());
            String expected = item.get("supplierCanonicalPath").getAsString();
            URL location = codeSource(type);
            try {
                Path source = ProviderLocation.resolve(location, Path.of(expected));
                codeSourceUrls.put(name, location.toExternalForm());
                require(source.toString().equals(expected) && suppliers.get(expected) != null
                        && suppliers.get(expected).equals(item.get("supplierSha256").getAsString()), "Wrong loaded class provider: " + name + "; CodeSource=" + location);
                String resource = "/" + name.replace('.', '/') + ".class";
                byte[] bytes;
                try (var input = type.getResourceAsStream(resource)) {
                    require(input != null, "Missing raw loaded class resource: " + name + "; CodeSource=" + location);
                    bytes = input.readNBytes(8 * 1024 * 1024 + 1);
                }
                require(bytes.length > 0 && bytes.length <= 8 * 1024 * 1024
                        && hash(bytes).equals(item.get("classSha256").getAsString()), "Changed raw class resource: " + name + "; CodeSource=" + location);
                require(classes.put(name, Map.of("provider", source.toString(), "supplierSha256", suppliers.get(expected),
                        "rawResourceSha256", hash(bytes))) == null, "Duplicate class identity");
            } catch (Exception | AssertionError | LinkageError failure) {
                throw new IllegalStateException("Loaded class parity failed: " + name + "; CodeSource=" + location, failure);
            }
        }
        require(classes.size() >= 60 && classes.size() <= 256 && classes.keySet().containsAll(CHAIN)
                && classes.containsKey("com.talhanation.recruits.inventory.RecruitSimpleContainer"), "Incomplete loaded class closure");
        var actualChain = new ArrayList<String>();
        for (Class<?> type = BuilderEntity.class; type != Object.class; type = type.getSuperclass()) actualChain.add(type.getName());
        require(actualChain.equals(CHAIN), "Loaded worker inheritance changed");
        require(owner("changeDimension", ServerLevel.class, ITeleporter.class) == Entity.class
                && owner("restoreFrom", Entity.class) == Entity.class
                && owner("removeAfterChangingDimensions") == net.minecraft.world.entity.Mob.class,
                "Loaded transfer method owner changed");
        return Map.of("passed", true, "gateSha256", hash(gate), "contractSha256", CONTRACT, "classes", classes,
                "superclassChain", actualChain, "codeSourceUrls", codeSourceUrls,
                "scope", "Exact loaded providers and raw class resources; transformed method bodies are not observed");
    }

    private static Class<?> owner(String method, Class<?>... parameters) throws NoSuchMethodException {
        for (Class<?> type = BuilderEntity.class; type != null; type = type.getSuperclass())
            try { return type.getDeclaredMethod(method, parameters).getDeclaringClass(); } catch (NoSuchMethodException absent) { }
        throw new NoSuchMethodException(method);
    }

    private static URL codeSource(Class<?> type) {
        var domain = type.getProtectionDomain();
        URL location = domain == null || domain.getCodeSource() == null ? null : domain.getCodeSource().getLocation();
        require(location != null, "Missing loaded provider URL: " + type.getName());
        return location;
    }

    /** JDK-only resolver, exercised directly by the source-extracted Java regression harness. */
    static final class ProviderLocation {
        private ProviderLocation() {}

        static Path resolve(URL location, Path expected) throws Exception {
            try {
                check(location != null && location.toExternalForm().length() <= 8192, "Missing/bounded provider URL");
                check(expected != null && expected.isAbsolute() && expected.equals(expected.toRealPath())
                        && Files.isRegularFile(expected) && expected.toString().endsWith(".jar")
                        && expected.toString().indexOf('#') < 0 && expected.toString().indexOf('!') < 0,
                        "Unverified or ambiguous expected supplier file");
                var uri = location.toURI();
                cleanUri(uri);
                Path result;
                if ("file".equals(uri.getScheme())) result = Path.of(uri);
                else if ("jar".equals(uri.getScheme())) {
                    var connection = (JarURLConnection) location.openConnection(); // Metadata only; never connect or read a URL.
                    check(connection.getEntryName() == null || connection.getEntryName().isEmpty(), "Non-root JAR provider");
                    var file = connection.getJarFileURL().toURI(); cleanUri(file);
                    check("file".equals(file.getScheme()), "Non-file JAR provider"); result = Path.of(file);
                } else if ("union".equals(uri.getScheme())) {
                    // SecureJarHandler makeKey appends #index to an existing UnionPath key.
                    // Resolve only a bounded index chain attached to the exact reviewed gate path;
                    // never strip arbitrary hashes from a guessed filename or search another JAR.
                    String base = expected.toUri().getPath(), path = uri.getPath();
                    check(path != null && path.startsWith(base), "Wrong union supplier prefix");
                    String suffix = path.substring(base.length());
                    check(suffix.matches("(?:#(?:0|[1-9][0-9]{0,9})){1,8}!/?"), "Unreviewed union index/root shape");
                    String indices = suffix.substring(0, suffix.indexOf('!'));
                    String literal = base;
                    for (String index : indices.substring(1).split("#")) {
                        check(Long.parseLong(index) <= Integer.MAX_VALUE, "Union index exceeds native integer range");
                        literal += "#" + index;
                        // A real filename (or symlink, or uncertain status) with a numeric suffix
                        // makes this textual interpretation ambiguous. Do not silently choose.
                        check(Files.notExists(Path.of(literal), java.nio.file.LinkOption.NOFOLLOW_LINKS),
                                "Numeric-hash filename makes union supplier ambiguous");
                    }
                    result = expected;
                } else throw new IllegalArgumentException("Unreviewed loaded provider protocol");
                check(result.equals(expected) && result.isAbsolute() && result.equals(result.toRealPath())
                        && Files.isRegularFile(result), "Provider is not the exact reviewed canonical supplier");
                return result;
            } catch (Exception | AssertionError problem) {
                throw new IllegalStateException("Loaded provider rejected: CodeSource=" + location
                        + "; expected=" + expected + "; " + problem.getMessage(), problem);
            }
        }

        private static void cleanUri(java.net.URI uri) {
            check(uri.getRawAuthority() == null && uri.getRawQuery() == null && uri.getRawFragment() == null,
                    "Provider authority, query or fragment is unsupported");
            String raw = uri.getRawSchemeSpecificPart().toLowerCase(java.util.Locale.ROOT);
            check(!raw.matches(".*%(?:2f|5c|2e|25|21).*"), "Encoded provider separator/traversal is unsupported");
        }
        private static void check(boolean okay, String why) { if (!okay) throw new IllegalArgumentException(why); }
    }

    private static String hash(Path path) throws Exception {
        var digest = MessageDigest.getInstance("SHA-256");
        try (var input = Files.newInputStream(path)) { byte[] buffer = new byte[65536]; int count;
            while ((count = input.read(buffer)) != -1) digest.update(buffer, 0, count); }
        return HexFormat.of().formatHex(digest.digest());
    }
    private static String hash(byte[] bytes) throws Exception { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
    private static void require(boolean okay, String why) { if (!okay) throw new AssertionError(why); }
}
