package com.devfarinsky.siegeoverhaul.nativecompat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.module.ModuleFinder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.junit.jupiter.api.Assertions.*;

/** Actual bounded file/module hashing plus catalog contracts; no launched Forge/native acceptance. */
class NativeDirtRuntimeTest {
    @TempDir Path dir;
    @Test void exactArtifactBytesAreHashedAndChangedLoadedFileRefuses() throws Exception {
        Path file = dir.resolve("mod.jar"); Files.writeString(file, "fixture bytes");
        var reader = new NativeDirtRuntime(); var first = reader.file(file);
        assertEquals(NativeDirtCensus.hash(Files.readAllBytes(file)), first.sha256());
        assertSame(first, reader.file(file));
        Files.writeString(file, "different fixture bytes");
        assertThrows(NativeDirtRuntime.Unsupported.class, () -> reader.file(file));
    }
    @Test void explodedInputAndOversizedArtifactRefuse() throws Exception {
        var reader = new NativeDirtRuntime(); assertThrows(NativeDirtRuntime.Unsupported.class, () -> reader.file(dir));
        Path huge = dir.resolve("huge.jar");
        try (var file = new java.io.RandomAccessFile(huge.toFile(), "rw")) { file.setLength(NativeDirtRuntime.MAX_MODULE_BYTES + 1); }
        assertThrows(NativeDirtRuntime.Unsupported.class, () -> reader.file(huge));
    }
    @Test void moduleContentHashIncludesResourceNamesAndContentsAndCachesImmutableReferences() throws Exception {
        Path one = jar("one.jar", "config.json", "one");
        Path two = jar("two.jar", "config.json", "two");
        Path renamed = jar("renamed.jar", "other.json", "one");
        var reader = new NativeDirtRuntime();
        var reference = ModuleFinder.of(one).findAll().iterator().next();
        var first = reader.module(reference); assertSame(first, reader.module(reference));
        assertNotEquals(first.sha256(), reader.module(ModuleFinder.of(two).findAll().iterator().next()).sha256());
        assertNotEquals(first.sha256(), reader.module(ModuleFinder.of(renamed).findAll().iterator().next()).sha256());
    }
    @Test void freshInputSealUsesSameReferencesAndEqualValuesAfterIndependentReads() throws Exception {
        Path artifact = jar("sealed.jar", "fixture.json", "unchanged input");
        var reference = ModuleFinder.of(artifact).findAll().iterator().next();
        var first = new NativeDirtRuntime(); var second = new NativeDirtRuntime();
        first.file(artifact); first.module(reference); second.file(artifact); second.module(reference);
        assertTrue(first.sameCapturedInputs(second));
        assertEquals(first.metrics().bytesRead(), second.metrics().bytesRead());
        assertTrue(second.metrics().bytesRead() > 0);
        var differentReference = new NativeDirtRuntime(); differentReference.file(artifact);
        differentReference.module(ModuleFinder.of(artifact).findAll().iterator().next());
        assertFalse(first.sameCapturedInputs(differentReference));
    }
    @Test void repeatedWorkTickChecksNeverRehashStartupBytes() throws Exception {
        Path artifact = jar("immutable.jar", "fixture.json", "immutable startup input");
        var reference = ModuleFinder.of(artifact).findAll().iterator().next();
        var reader = new NativeDirtRuntime(); reader.file(artifact); reader.module(reference);
        reader.freezeStartupInputs();
        var initial = reader.metrics(); assertTrue(initial.bytesRead() > 0);
        for (int tick = 0; tick < 30; tick++) { reader.file(artifact); reader.module(reference); }
        var after = reader.metrics();
        assertEquals(initial.bytesRead(), after.bytesRead());
        assertEquals(1, after.artifactsHashed()); assertEquals(1, after.modulesHashed());
        assertEquals(30, after.cachedFileChecks()); assertEquals(30, after.cachedModuleChecks());
    }
    @ParameterizedTest @ValueSource(strings = {"-javaagent:test.jar", "-agentlib:jdwp", "-agentpath:/agent", "-Xrunjdwp:server=y", "-Dmixin.hotSwap=true"})
    void knownInstrumentationLaunchesAreExplicitlyUnsupported(String argument) { assertTrue(NativeDirtRuntime.instrumentation(argument)); }
    @Test void unboundOrNewRuntimeInputsRefuseBeforeArtifactBytesAreRead() throws Exception {
        var reader = new NativeDirtRuntime();
        assertThrows(NativeDirtRuntime.Unsupported.class, () -> reader.read(List.of(), List.of()));
        assertEquals(0, reader.metrics().bytesRead());
        Path original = jar("original.jar", "value.json", "original");
        var originalModule = ModuleFinder.of(original).findAll().iterator().next();
        reader.file(original); reader.module(originalModule); reader.freezeStartupInputs();
        long before = reader.metrics().bytesRead();
        Path added = jar("added.jar", "value.json", "new code");
        assertThrows(NativeDirtRuntime.Unsupported.class, () -> reader.file(added));
        assertThrows(NativeDirtRuntime.Unsupported.class, () -> reader.module(ModuleFinder.of(added).findAll().iterator().next()));
        assertEquals(before, reader.metrics().bytesRead());
    }
    @Test void actualHandlerAndGeneratedDelegateNamesAreIncludedInTransformationAudit() throws Exception {
        var owner = new NativeDirtPolicy.CodeOrigin("example.Owner", "example", "file:/example.jar", "a".repeat(64));
        var names = NativeDirtRuntime.auditNames(List.of(owner), List.of("net.minecraftforge.eventbus.ASMEventHandler$1", "example.__Owner_callback_EntityEvent"));
        assertEquals(List.of("example.Owner", "example.__Owner_callback_EntityEvent", "net.minecraftforge.eventbus.ASMEventHandler$1"), names);
    }
    @Test void classOutsideTheBoundModuleCatalogCannotUseMatchingClaimedNames() {
        var reader = new NativeDirtRuntime();
        assertThrows(NativeDirtRuntime.Unsupported.class, () -> reader.liveIdentity(List.of(NativeDirtRuntimeTest.class)));
        assertThrows(NativeDirtRuntime.Unsupported.class, () -> reader.liveIdentity(List.of(String.class)));
    }
    @Test void narrowFirstPartyShapeAvoidsSelfHashButRetainsThirdPartyAndCallbackShape() {
        var own = new NativeDirtRuntime.FirstParty("siegeoverhaul", "siegeoverhaul", List.of("known.OwnedClass"));
        var first = new NativeDirtRuntime.Catalog("production", "server", "java17",
                List.of(new NativeDirtRuntime.Artifact("siegeoverhaul", "1", "packaged-file", "a", 10),
                        new NativeDirtRuntime.Artifact("workers", "2", "packaged-file", "exact-third-party", 20)),
                List.of(new NativeDirtRuntime.ModuleProof("GAME", "siegeoverhaul", "1", "a", List.of())), List.of(), List.of(), List.of(), own);
        var changedSelf = new NativeDirtRuntime.Catalog(first.launch(), first.distribution(), first.javaRuntime(),
                List.of(new NativeDirtRuntime.Artifact("siegeoverhaul", "1", "packaged-file", "b", 50), first.mods().get(1)),
                List.of(new NativeDirtRuntime.ModuleProof("GAME", "siegeoverhaul", "1", "b", List.of())), List.of(), List.of(), List.of(), own);
        assertNotEquals(first, changedSelf); assertEquals(first.reviewedShape(), changedSelf.reviewedShape());
        var thirdPartyChanged = new NativeDirtRuntime.Catalog(first.launch(), first.distribution(), first.javaRuntime(),
                List.of(first.mods().get(0), new NativeDirtRuntime.Artifact("workers", "2", "packaged-file", "other", 20)),
                first.modules(), List.of(), List.of(), List.of(), own);
        assertNotEquals(first.reviewedShape(), thirdPartyChanged.reviewedShape());
        var expected = new NativeDirtPolicy.CodeOrigin("known.OwnedClass", "siegeoverhaul", "file:/a", "a");
        var changed = new NativeDirtPolicy.CodeOrigin("known.OwnedClass", "siegeoverhaul", "file:/b", "b");
        assertEquals(NativeDirtPolicy.logical(expected, own), NativeDirtPolicy.logical(changed, own));
        var unknown = new NativeDirtPolicy.CodeOrigin("known.UnreviewedClass", "siegeoverhaul", "file:/a", "a");
        var unknownChanged = new NativeDirtPolicy.CodeOrigin("known.UnreviewedClass", "siegeoverhaul", "file:/b", "b");
        assertNotEquals(NativeDirtPolicy.logical(unknown, own), NativeDirtPolicy.logical(unknownChanged, own));
    }
    @Test void censusExportNeverEchoesUnknownSecretLikeMetadataOrLaunchArguments() throws Exception {
        String secret = "secret-token-DO-NOT-EXPORT";
        var unknown = assertThrows(NativeDirtRuntime.Unsupported.class, () -> NativeDirtRuntime.safeService(
                Map.of("name", "forge", "type", "PLUGINSERVICE", "file", "forge.jar", "api_key", secret)));
        assertFalse(unknown.getMessage().contains(secret));
        var value = assertThrows(NativeDirtRuntime.Unsupported.class, () -> NativeDirtRuntime.safeService(
                Map.of("name", "forge", "type", "PLUGINSERVICE", "file", "forge.jar?token=" + secret)));
        assertFalse(value.getMessage().contains(secret));
        assertFalse(NativeDirtRuntime.instrumented(List.of("-Dprivate.token=" + secret)));
        assertThrows(NativeDirtRuntime.Unsupported.class, () -> NativeDirtRuntime.instrumented(List.of("x".repeat(16_385))));
        assertEquals(Map.of("name", "forge", "type", "PLUGINSERVICE", "file", "forge-47.4.16.jar"),
                NativeDirtRuntime.safeService(Map.of("name", "forge", "type", "PLUGINSERVICE", "file", "forge-47.4.16.jar")));
    }
    @Test void sourceUrisRejectPrivateComponentsWithoutEchoingThem() throws Exception {
        assertEquals("jar:file:/mods/forge.jar!/", NativeDirtRuntime.safeSource("jar:file:/mods/forge.jar!/"));
        assertEquals("union:/mods/forge.jar%230!/", NativeDirtRuntime.safeSource("union:/mods/forge.jar%230!/"));
        for (String source : List.of("https://user:secret@example/a.jar", "file:/a.jar?token=secret", "file:/a.jar#secret", "jar:https://user:secret@example/a.jar!/", "jar:jar:file:/a.jar!/!/", "jar:file:/a.jar!/entry?token=secret")) {
            var refusal = assertThrows(NativeDirtRuntime.Unsupported.class, () -> NativeDirtRuntime.safeSource(source));
            assertFalse(refusal.getMessage().contains("secret"));
        }
    }
    @Test void arbitraryTransformerLabelsAreBoundByDigestWithoutExportingRawData() throws Exception {
        String label = "plugin-private-token-DO-NOT-EXPORT";
        var safe = NativeDirtRuntime.safeActivity("known.Type", "PLUGIN", new String[]{"known-plugin", label});
        assertFalse(safe.toString().contains(label)); assertFalse(safe.toString().contains("known-plugin"));
        assertEquals(safe, NativeDirtRuntime.safeActivity("known.Type", "PLUGIN", new String[]{"known-plugin", label}));
        assertNotEquals(safe, NativeDirtRuntime.safeActivity("known.Type", "PLUGIN", new String[]{label, "known-plugin"}));
        assertTrue(safe.contextDigests().stream().allMatch(digest -> digest.matches("[0-9a-f]{64}")));
    }
    @Test void contextDigestsDistinguishEveryUtf16CodeUnitIncludingUnpairedSurrogates() throws Exception {
        String highA = String.valueOf((char) 0xD800), highB = String.valueOf((char) 0xD801);
        assertNotEquals(NativeDirtRuntime.safeActivity("known.Type", "PLUGIN", new String[]{highA}),
                NativeDirtRuntime.safeActivity("known.Type", "PLUGIN", new String[]{highB}));
        assertNotEquals(NativeDirtRuntime.safeActivity("known.Type", "PLUGIN", new String[]{highA}),
                NativeDirtRuntime.safeActivity("known.Type", "PLUGIN", new String[]{"?"}));
    }
    @Test void ordinaryHeapOptionIsNotMistakenForAnAgent() { assertFalse(NativeDirtRuntime.instrumentation("-Xmx2G")); }
    @Test void catalogPinsUnknownModsServicesPendingMixinsAndActualTransformerMetadata() {
        var baseline = catalog(List.of(), List.of(), List.of(), List.of());
        assertNotEquals(baseline, catalog(List.of(new NativeDirtRuntime.Artifact("unknown", "1", "fixture", "a".repeat(64), 1)), List.of(), List.of(), List.of()));
        assertNotEquals(baseline, catalog(List.of(), List.of(Map.of("name", "unknown")), List.of(), List.of()));
        assertNotEquals(baseline, catalog(List.of(), List.of(), List.of("new.mixins.json"), List.of()));
        assertNotEquals(baseline, catalog(List.of(), List.of(), List.of(), List.of(new NativeDirtRuntime.Activity("Block", "PLUGIN", List.of("unknown")))));
        assertThrows(UnsupportedOperationException.class, () -> baseline.mods().clear());
    }
    @Test void actualSecureJarApiBytesArePinnedWithoutConstructingItsFileSystems() throws Exception {
        assertTrue(NativeDirtModuleView.validateApi() > 0);
        assertEquals(7, NativeDirtModuleView.implementationClasses().size());
    }
    @ParameterizedTest @ValueSource(strings = {"../x", "a/../x", "/absolute", "a//b", "a/", "a\\b", "./x", "x\nsecret"})
    void noncanonicalModuleNamesRefuseBeforeUnionNormalization(String name) {
        assertThrows(java.io.IOException.class, () -> NativeDirtModuleView.canonical(name));
    }
    @Test void moduleNamesAndMultiReleaseAliasesRetainExactSpelling() throws Exception {
        NativeDirtModuleView.canonical("ordinary/path.json");
        assertEquals("pkg/Only.class", NativeDirtModuleView.alias("META-INF/versions/11/pkg/Only.class"));
        assertNull(NativeDirtModuleView.alias("ordinary/path.json"));
        for (String name : List.of("META-INF/versions/-1/Only.class", "META-INF/versions/01/Only.class",
                "META-INF/versions/+11/Only.class", "META-INF/versions/bad/Only.class", "META-INF/versions/11"))
            assertThrows(java.io.IOException.class, () -> NativeDirtModuleView.alias(name));
    }
    @Test void nullListFromUnknownReaderIsNeverTreatedAsEmptyModule() throws Exception {
        var closed = new java.util.concurrent.atomic.AtomicBoolean();
        var reference = new java.lang.module.ModuleReference(java.lang.module.ModuleDescriptor.newAutomaticModule("example").build(), java.net.URI.create("file:/example")) {
            @Override public java.lang.module.ModuleReader open() {
                return new java.lang.module.ModuleReader() {
                    public java.util.Optional<java.net.URI> find(String name) { return java.util.Optional.empty(); }
                    public java.util.stream.Stream<String> list() { return null; }
                    public void close() { closed.set(true); }
                };
            }
        };
        var runtime = new NativeDirtRuntime();
        assertThrows(NativeDirtRuntime.Unsupported.class, () -> runtime.module(reference));
        assertTrue(closed.get()); assertEquals(0, runtime.metrics().bytesRead()); assertEquals(0, runtime.metrics().modulesHashed());
    }
    @Test void moduleCacheUsesActualReferenceIdentityNotOverridableEquality() throws Exception {
        var first = ModuleFinder.of(jar("identityfirst.jar", "input.json", "one")).findAll().iterator().next();
        var second = ModuleFinder.of(jar("identitysecond.jar", "input.json", "two")).findAll().iterator().next();
        class EqualReference extends java.lang.module.ModuleReference {
            final java.lang.module.ModuleReference delegate;
            EqualReference(java.lang.module.ModuleReference original) { super(original.descriptor(), original.location().orElseThrow()); delegate = original; }
            public java.lang.module.ModuleReader open() throws java.io.IOException { return delegate.open(); }
            public boolean equals(Object other) { throw new AssertionError("Reference equality must not be invoked"); }
            public int hashCode() { throw new AssertionError("Reference hashCode must not be invoked"); }
        }
        var runtime = new NativeDirtRuntime();
        assertNotEquals(runtime.module(new EqualReference(first)).sha256(), runtime.module(new EqualReference(second)).sha256());
        assertEquals(2, runtime.metrics().modulesHashed());
    }
    private static NativeDirtRuntime.Catalog catalog(List<NativeDirtRuntime.Artifact> mods, List<Map<String, String>> services,
                                                      List<String> mixins, List<NativeDirtRuntime.Activity> activity) {
        return new NativeDirtRuntime.Catalog("forgeserver", "DEDICATED_SERVER", "fixture", mods, List.of(), services, mixins, activity, null);
    }
    private Path jar(String name, String resource, String contents) throws Exception {
        Path file = dir.resolve(name);
        try (var output = new JarOutputStream(Files.newOutputStream(file))) {
            output.putNextEntry(new JarEntry(resource)); output.write(contents.getBytes(java.nio.charset.StandardCharsets.UTF_8)); output.closeEntry();
        }
        return file;
    }
}
