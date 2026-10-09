package com.devfarinsky.siegeoverhaul.nativecompat;

import cpw.mods.cl.JarModuleFinder;
import cpw.mods.jarhandling.SecureJar;
import cpw.mods.jarhandling.impl.SimpleJarMetadata;

import java.io.IOException;
import java.lang.module.ModuleReference;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;
import java.util.function.BiPredicate;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

/** Synthetic file inputs only, in the existing Forge context. No modules/classes are activated. */
final class NativeDirtModuleViewQa {
    record Result(String status, String failedCase, String failureType, List<String> passedCases) {
        Result { passedCases = List.copyOf(passedCases); }
    }
    @FunctionalInterface private interface Check { void run() throws Exception; }
    private NativeDirtModuleViewQa() {}
    static Result run(Path parent) throws Exception {
        Path root = Files.createTempDirectory(parent, "securejar-census-selftest-");
        var cases = new LinkedHashMap<String, Check>();
        cases.put("combined-roots-and-fresh-seal", () -> {
            Path a = directory(root, "combined-a"), b = directory(root, "combined-b");
            write(a, "a.txt", "first"); write(b, "b.txt", "second");
            ModuleReference reference = reference(false, null, a, b);
            try (var reader = reference.open()) {
                require(reader.list() == null && reader.find("a.txt").isPresent() && reader.find("b.txt").isPresent());
            }
            var frozen = new NativeDirtRuntime(); var before = frozen.module(reference);
            var unchanged = new NativeDirtRuntime();
            require(before.equals(unchanged.module(reference)) && frozen.sameCapturedInputs(unchanged));
            require(unchanged.metrics().bytesRead() == frozen.metrics().bytesRead() && unchanged.metrics().bytesRead() > 0);
            write(b, "b.txt", "changed secondary input");
            require(frozen.module(reference) == before); // Explicit frozen-input cache, NOT mutable-input validation.
            var fresh = new NativeDirtRuntime(); require(!before.sha256().equals(fresh.module(reference).sha256()));
            require(!frozen.sameCapturedInputs(fresh));
        });
        cases.put("overlay-precedence-and-backing-seal", () -> {
            Path a = directory(root, "overlay-a"), b = directory(root, "overlay-b");
            write(a, "same.txt", "first"); write(b, "same.txt", "second");
            ModuleReference reference = reference(false, null, a, b);
            String actual;
            try (var reader = reference.open(); var input = reader.open("same.txt").orElseThrow()) {
                actual = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            }
            require(actual.equals("first") || actual.equals("second"));
            Path winner = actual.equals("first") ? a : b, loser = winner == a ? b : a;
            var first = new NativeDirtRuntime(); var digest = first.module(reference);
            write(loser, "same.txt", "changed masked input");
            var second = new NativeDirtRuntime(); require(digest.sha256().equals(second.module(reference).sha256()));
            require(!first.sameCapturedInputs(second)); // Same effective bytes still do not hide changed backing metadata.
            write(winner, "same.txt", "changed visible input");
            require(!digest.sha256().equals(new NativeDirtRuntime().module(reference).sha256()));
        });
        cases.put("hidden-directory-visible-descendant", () -> {
            Path directory = directory(root, "filtered"); write(directory, "pkg/visible.txt", "visible");
            var reference = reference(false, (path, base) -> !path.equals("pkg") && !path.equals("pkg/"), directory);
            try (var reader = reference.open()) {
                require(reader.find("pkg/visible.txt").isPresent());
                var view = NativeDirtModuleView.read(reference, reader, 64);
                require(view.entries().stream().anyMatch(e -> e.name().equals("pkg/visible.txt") && !e.selected().isEmpty()));
            }
            require(new NativeDirtRuntime().module(reference).bytes() > 0);
        });
        cases.put("multi-release-version-only-and-boundary", () -> {
            Path directory = directory(root, "multi");
            write(directory, "META-INF/versions/11/pkg/Only.class", "eleven");
            write(directory, "Base.class", "base");
            write(directory, "META-INF/versions/17/Base.class", "equal-runtime-version");
            var reference = reference(true, null, directory);
            try (var reader = reference.open()) {
                var view = NativeDirtModuleView.read(reference, reader, 64);
                for (String name : List.of("pkg/Only.class", "Base.class")) {
                    String selected = reader.find(name).map(uri -> Path.of(reference.location().orElseThrow()).relativize(Path.of(uri)).toString()).orElse("");
                    require(view.entries().stream().anyMatch(e -> e.name().equals(name) && e.selected().equals(selected)));
                }
                require(reader.find("pkg/Only.class").isPresent());
            }
            require(new NativeDirtRuntime().module(reference).bytes() > 0);
        });
        cases.put("unsupported-multi-release-spelling", () -> {
            Path directory = directory(root, "negative-version"); write(directory, "META-INF/versions/-1/Only.class", "negative");
            var reference = reference(true, null, directory);
            try (var reader = reference.open()) { require(reader.find("Only.class").isPresent()); }
            refused(() -> new NativeDirtRuntime().module(reference));
        });
        cases.put("raw-entry-budget-before-union-allocation", () -> {
            Path directory = directory(root, "bounded");
            for (int i = 0; i < 20; i++) write(directory, "entry-" + i + ".txt", "small");
            var reference = reference(false, null, directory);
            refused(() -> new NativeDirtRuntime().module(reference, 4));
        });
        cases.put("literal-backslash-refused", () -> {
            Path directory = directory(root, "ambiguous"); write(directory, "bad\\name.txt", "ambiguous");
            var reference = reference(false, null, directory);
            refused(() -> new NativeDirtRuntime().module(reference));
        });
        cases.put("backing-file-symlink-refused", () -> {
            Path directory = directory(root, "link-entry"); write(directory, "target.txt", "fixture");
            Files.createSymbolicLink(directory.resolve("link.txt"), directory.resolve("target.txt"));
            var reference = reference(false, null, directory);
            refused(() -> new NativeDirtRuntime().module(reference));
        });
        cases.put("backing-root-symlink-refused", () -> {
            Path directory = directory(root, "root-target"); write(directory, "value.txt", "fixture");
            Path linked = root.resolve("linked-root"); Files.createSymbolicLink(linked, directory);
            var reference = reference(false, null, linked);
            refused(() -> new NativeDirtRuntime().module(reference));
        });
        cases.put("removed-root-refused", () -> {
            Path directory = directory(root, "removed"); write(directory, "value.txt", "fixture");
            var reference = reference(false, null, directory);
            Files.delete(directory.resolve("value.txt")); Files.delete(directory);
            refused(() -> new NativeDirtRuntime().module(reference));
        });
        cases.put("zip-and-directory-combined-view", () -> {
            Path zip = root.resolve("fixture.jar");
            try (var out = new JarOutputStream(Files.newOutputStream(zip))) {
                out.putNextEntry(new JarEntry("zip.txt")); out.write("archive".getBytes(StandardCharsets.UTF_8)); out.closeEntry();
            }
            Path directory = directory(root, "zip-extra"); write(directory, "dir.txt", "directory");
            var reference = reference(false, null, zip, directory);
            try (var reader = reference.open()) {
                var view = NativeDirtModuleView.read(reference, reader, 64);
                require(view.entries().stream().anyMatch(e -> e.name().equals("zip.txt") && !e.selected().isEmpty()));
                require(view.entries().stream().anyMatch(e -> e.name().equals("dir.txt") && !e.selected().isEmpty()));
            }
            require(new NativeDirtRuntime().module(reference).bytes() > 0);
        });
        var passed = new ArrayList<String>();
        for (var test : cases.entrySet()) {
            try { test.getValue().run(); passed.add(test.getKey()); }
            catch (Exception | AssertionError | LinkageError problem) {
                return new Result("refused", test.getKey(), NativeDirtCensusBoundary.safeType(problem), passed);
            }
        }
        return new Result("passed", "", "", passed);
    }
    private static ModuleReference reference(boolean multiRelease, BiPredicate<String, String> filter, Path... paths) {
        var metadata = new SimpleJarMetadata("qa.census.fixture", "1", Set.of(), List.of());
        var secure = SecureJar.from(() -> {
            var manifest = new Manifest(); manifest.getMainAttributes().putValue("Manifest-Version", "1.0");
            if (multiRelease) manifest.getMainAttributes().putValue("Multi-Release", "true"); return manifest;
        }, jar -> metadata, filter, paths);
        return JarModuleFinder.of(secure).findAll().iterator().next();
    }
    private static Path directory(Path root, String name) throws IOException { return Files.createDirectory(root.resolve(name)); }
    private static void write(Path root, String name, String contents) throws IOException {
        Path file = root.resolve(name); Files.createDirectories(file.getParent()); Files.writeString(file, contents);
    }
    private static void require(boolean condition) { if (!condition) throw new AssertionError("SecureJar fixture contract differs"); }
    private static void refused(Check test) throws Exception {
        boolean refused = false;
        try { test.run(); }
        catch (NativeDirtRuntime.Unsupported | IOException | java.nio.file.DirectoryIteratorException expected) { refused = true; }
        require(refused);
    }
}
