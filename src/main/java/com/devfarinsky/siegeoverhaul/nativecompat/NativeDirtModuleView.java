package com.devfarinsky.siegeoverhaul.nativecompat;

import cpw.mods.cl.JarModuleFinder;
import cpw.mods.niofs.union.UnionFileSystem;
import cpw.mods.niofs.union.UnionFileSystemProvider;
import cpw.mods.niofs.union.UnionPath;

import java.io.IOException;
import java.lang.module.ModuleReader;
import java.lang.module.ModuleReference;
import java.net.URI;
import java.nio.file.DirectoryStream;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/** Pinned public SecureJar 2.1.10 view. No private fields, new file systems, module activation or opens. */
final class NativeDirtModuleView {
    static final Map<String, String> API = Map.ofEntries(
            Map.entry("cpw.mods.cl.JarModuleFinder$JarModuleReader", "f19d88bce86e5462da87e2fb2f1a55df1e2539a7b8f3f455450827c6d6ce33bb"),
            Map.entry("cpw.mods.cl.JarModuleFinder$JarModuleReference", "b4a14f444ad51cce8eeba864b74f4b501bcab781090dc1970705421415e04611"),
            Map.entry("cpw.mods.jarhandling.impl.Jar", "bba6a4ee9327d364967a3cfec4707d695d5962cb42a20a4b212a26434a5b9055"),
            Map.entry("cpw.mods.jarhandling.impl.Jar$JarModuleDataProvider", "045c87e04bf053c21da1cd06ea9d3a2426eed5bdc3464069d0191e1758401ab0"),
            Map.entry("cpw.mods.niofs.union.UnionFileSystem", "de67081ffbb73a4db7cddfbed23b397cdceea4cfe59938cf3ee16318018434c4"),
            Map.entry("cpw.mods.niofs.union.UnionFileSystemProvider", "4385f3c63f714548e56f6ebcad2d8b490b9b54132159040c270ab677b48c3504"),
            Map.entry("cpw.mods.niofs.union.UnionPath", "fd276c431e380b9c1abac20de80c9c05d10d64b3c530862613aaa5262e6817a9"));
    record Selection(String name, String selected) {} // empty selected means actual reader.find is absent
    private record Stamp(long size, FileTime modified, Object fileKey, boolean directory) {}
    private record Ancestor(Object fileKey, boolean directory) {}
    private record Node(Path path, String name) {}
    record View(List<Selection> entries, Map<Path, Stamp> metadata, Map<Path, Ancestor> ancestors) {
        View { entries = List.copyOf(entries); metadata = Map.copyOf(metadata); ancestors = Map.copyOf(ancestors); }
    }
    private NativeDirtModuleView() {}

    static List<Class<?>> implementationClasses() throws ClassNotFoundException {
        var types = new ArrayList<Class<?>>();
        for (String name : API.keySet().stream().sorted().toList())
            types.add(Class.forName(name, false, JarModuleFinder.class.getClassLoader()));
        return List.copyOf(types);
    }
    static long validateApi() throws Exception {
        long bytes = 0;
        for (Class<?> type : implementationClasses()) {
            if (type.getModule() != JarModuleFinder.class.getModule() || type.getClassLoader() != JarModuleFinder.class.getClassLoader())
                throw new NativeDirtRuntime.Unsupported("SecureJar implementation escaped its exact module/loader");
            byte[] data = NativeDirtCensus.bytes(type.getResourceAsStream("/" + type.getName().replace('.', '/') + ".class"), 1_048_576);
            bytes += data.length;
            if (!API.get(type.getName()).equals(NativeDirtCensus.hash(data)))
                throw new NativeDirtRuntime.Unsupported("SecureJar public enumeration implementation differs from pinned 2.1.10");
        }
        return bytes;
    }
    static void requireReader(ModuleReference reference, ModuleReader reader) throws Exception {
        var loader = JarModuleFinder.class.getClassLoader();
        if (reference.getClass() != Class.forName("cpw.mods.cl.JarModuleFinder$JarModuleReference", false, loader)
                || reader.getClass() != Class.forName("cpw.mods.cl.JarModuleFinder$JarModuleReader", false, loader))
            throw new NativeDirtRuntime.Unsupported("Null module listing is unsupported for this reference/reader identity");
    }
    static View read(ModuleReference reference, ModuleReader reader, int limit) throws Exception {
        if (limit < 1 || limit > NativeDirtRuntime.MAX_ENTRIES) throw new NativeDirtRuntime.Unsupported("Invalid module enumeration bound");
        requireReader(reference, reader);
        URI location = reference.location().orElseThrow(() -> new NativeDirtRuntime.Unsupported("Module location is absent"));
        safeUnionUri(location);
        Path root = Path.of(location); // Resolve an existing provider file system. Never mount/open a substitute.
        if (root.getClass() != UnionPath.class || root.getFileSystem().getClass() != UnionFileSystem.class
                || root.getFileSystem().provider().getClass() != UnionFileSystemProvider.class
                || !root.isAbsolute() || !root.equals(root.getRoot())
                || !reader.find("").orElseThrow(() -> new NativeDirtRuntime.Unsupported("Module root lookup is unavailable")).equals(location))
            throw new NativeDirtRuntime.Unsupported("Exact existing resolved union root is unavailable");
        var scan = new Scan(limit);
        // The pinned provider forwards this filter to raw backing streams BEFORE its filter/map/eager set.
        // Returning false avoids provider allocation and still sees excluded directory descendants.
        try (DirectoryStream<Path> ignored = Files.newDirectoryStream(root, path -> {
            Path actual = scan.actual(path);
            scan.recordRoot(actual.getParent());
            scan.add(actual, actual.getFileName().toString());
            return false;
        })) { for (Path unexpected : ignored) throw new IOException("Pinned forwarding filter was not honored"); }
        scan.walk();
        var names = new TreeSet<>(scan.names);
        for (String name : scan.names) {
            String alias = alias(name);
            if (alias != null) names.add(alias);
            if (names.size() > limit) throw new IOException("Module logical-name budget exceeded");
        }
        var selected = new ArrayList<Selection>();
        for (String name : names) {
            URI found = reader.find(name).orElse(null);
            if (found == null) { selected.add(new Selection(name, "")); continue; }
            safeUnionUri(found);
            Path target = Path.of(found);
            if (target.getClass() != UnionPath.class || target.getFileSystem() != root.getFileSystem()
                    || !target.isAbsolute() || !target.startsWith(root) || !target.equals(target.normalize()))
                throw new NativeDirtRuntime.Unsupported("Module lookup escaped its exact union view");
            String relative = root.relativize(target).toString(); canonical(relative);
            if (!scan.names.contains(relative)) throw new NativeDirtRuntime.Unsupported("Reader-selected resource is outside enumerated raw candidates");
            selected.add(new Selection(name, relative));
        }
        return new View(selected, scan.metadata, scan.ancestors);
    }
    static String alias(String name) throws IOException {
        if (!name.startsWith("META-INF/versions/")) return null;
        String suffix = name.substring("META-INF/versions/".length()); int slash = suffix.indexOf('/');
        if (slash < 1 || !suffix.substring(0, slash).matches("[1-9][0-9]{0,8}"))
            throw new IOException("Unsupported multi-release version spelling");
        String alias = suffix.substring(slash + 1); canonical(alias); return alias;
    }
    static void canonical(String name) throws IOException {
        if (name == null || name.isEmpty() || name.length() > 4096 || name.startsWith("/") || name.endsWith("/")
                || name.indexOf('\\') >= 0 || name.codePoints().anyMatch(c -> c < 32 || c == 127 || c >= 0xD800 && c <= 0xDFFF))
            throw new IOException("Unsupported module resource name");
        String[] parts = name.split("/", -1);
        if (parts.length > 128) throw new IOException("Module path depth exceeded");
        for (String part : parts) if (part.isEmpty() || part.equals(".") || part.equals(".."))
            throw new IOException("Ambiguous module resource path");
    }
    private static void safeUnionUri(URI uri) throws NativeDirtRuntime.Unsupported {
        NativeDirtRuntime.safeSource(uri.toString());
        if (!"union".equals(uri.getScheme())) throw new NativeDirtRuntime.Unsupported("Module view is not the existing union provider");
    }
    private static final class Scan {
        final int limit;
        int rawCount;
        long nameUnits;
        final TreeSet<String> names = new TreeSet<>();
        final ArrayDeque<Node> directories = new ArrayDeque<>();
        final Map<Path, Stamp> metadata = new HashMap<>();
        final Map<Path, Ancestor> ancestors = new HashMap<>();
        Scan(int limit) { this.limit = limit; }
        Path actual(Path path) throws IOException {
            Path actual = path.toAbsolutePath();
            if (!actual.equals(actual.normalize()) || actual.toString().length() > 4096)
                throw new IOException("Noncanonical backing module path");
            var filesystem = actual.getFileSystem();
            if (filesystem == FileSystems.getDefault()) {
                var provider = filesystem.provider();
                if (provider.getClass().getModule() != Object.class.getModule()
                        || !provider.getClass().getName().matches("sun\\.nio\\.fs\\.(Linux|MacOSX|Windows)FileSystemProvider")
                        || actual.getClass().getModule() != Object.class.getModule())
                    throw new IOException("Unknown default filesystem implementation");
            } else {
                var provider = filesystem.provider();
                if (!provider.getClass().getName().equals("jdk.nio.zipfs.ZipFileSystemProvider")
                        || provider.getClass().getModule() != ModuleLayer.boot().findModule("jdk.zipfs").orElse(null)
                        || !actual.getClass().getName().equals("jdk.nio.zipfs.ZipPath")
                        || actual.getClass().getModule() != provider.getClass().getModule())
                    throw new IOException("Unknown backing filesystem provider");
                URI uri = actual.toUri(); String scheme = uri.getRawSchemeSpecificPart(); int boundary = scheme.indexOf("!/");
                if (!"jar".equals(uri.getScheme()) || boundary < 0 || scheme.indexOf("!/", boundary + 2) >= 0)
                    throw new IOException("Unknown backing archive origin");
                URI archive = URI.create(scheme.substring(0, boundary));
                if (!"file".equals(archive.getScheme()) || archive.getRawAuthority() != null || archive.getRawQuery() != null
                        || archive.getRawFragment() != null || uri.getRawQuery() != null || uri.getRawFragment() != null)
                    throw new IOException("Unsafe backing archive URI");
                Path file = Path.of(archive).toAbsolutePath();
                if (file.getFileSystem() != FileSystems.getDefault() || !file.equals(file.normalize())) throw new IOException("Unknown archive filesystem");
                actual(file);
                checkAncestors(file); stamp(file, false);
            }
            checkAncestors(actual); return actual;
        }
        void checkAncestors(Path path) throws IOException {
            if (path.getNameCount() > 128) throw new IOException("Backing ancestor depth exceeded");
            for (Path p = path; p != null; p = p.getParent()) {
                if (ancestors.containsKey(p)) continue;
                BasicFileAttributes attributes = Files.readAttributes(p, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
                if (attributes.isSymbolicLink() || attributes.isOther()) throw new IOException("Backing symlink or special file is unsupported");
                ancestors.put(p, new Ancestor(attributes.fileKey(), attributes.isDirectory()));
                if (ancestors.size() > limit * 2 + 2048) throw new IOException("Backing ancestor budget exceeded");
            }
        }
        BasicFileAttributes stamp(Path path, boolean directory) throws IOException {
            BasicFileAttributes attributes = Files.readAttributes(path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
            if (attributes.isSymbolicLink() || attributes.isOther() || directory != attributes.isDirectory()
                    || !directory && !attributes.isRegularFile()) throw new IOException("Unsupported backing entry type");
            metadata.put(path, new Stamp(attributes.size(), attributes.lastModifiedTime(), attributes.fileKey(), directory));
            if (metadata.size() > limit * 2 + 2048) throw new IOException("Backing metadata budget exceeded");
            return attributes;
        }
        void recordRoot(Path root) throws IOException { if (root == null) throw new IOException("Backing root absent"); actual(root); stamp(root, true); }
        void add(Path path, String name) throws IOException {
            if (++rawCount > limit) throw new IOException("Raw backing entry budget exceeded before union allocation");
            canonical(name);
            nameUnits += name.length() + path.toString().length();
            if (nameUnits > 16L * 1024 * 1024) throw new IOException("Backing name metadata exceeds its bound");
            Path actual = actual(path);
            BasicFileAttributes attributes = Files.readAttributes(actual, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
            stamp(actual, attributes.isDirectory());
            if (attributes.isDirectory()) directories.addLast(new Node(actual, name)); else names.add(name);
        }
        void walk() throws IOException {
            while (!directories.isEmpty()) {
                Node node = directories.removeFirst();
                try (var ignored = Files.newDirectoryStream(node.path(), path -> {
                    add(path, node.name() + "/" + path.getFileName()); return false;
                })) { for (Path unexpected : ignored) throw new IOException("Backing filter was not honored"); }
            }
        }
    }
}
