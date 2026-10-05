"""Census source contracts and forged-evidence rejection tests, never native acceptance."""
import copy
import hashlib
import importlib.util
import json
import shutil
import subprocess
from pathlib import Path
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[2]


def load(name, file):
    spec = importlib.util.spec_from_file_location(name, file)
    result = importlib.util.module_from_spec(spec); spec.loader.exec_module(result)
    return result


VERIFY = load('verify_dirt', ROOT / 'scripts/verify-native-dirt-census.py')
ONE_FILL_TEST = load('original_fixture_examples', ROOT / 'scripts/tests/test_native_earthworks_qa.py')
QA = ROOT / 'src/nativeEarthworksQa/java/com/devfarinsky/siegeoverhaul/nativecompat'


def illustrative_evidence():
    """Deliberately labeled synthetic JSON, only to test verifier grammar and rejections."""
    fill = ONE_FILL_TEST.illustrative_receipt()
    fill['target'] = [141, 64, 9]
    fill['dirtCensus'] = {'enabled': True, 'status': 'captured', 'artifactSha256': 'a' * 64, 'failureType': '', 'failurePhase': ''}
    for mod in ('smallships', 'siegeweapons'):
        fill['loadedCompanionArtifacts'][mod] = {'sha256': 'b' * 64}
    ready = {'reason': 'READY', 'detail': 'Illustrative structural verifier sample only'}
    versions = {'minecraft': '1.20.1', 'forge': '47.4.16', 'siegeoverhaul': '4.52.9', 'workers': '2.0.3',
                'recruits': '1.15.2', 'smallships': '2.0.0-b1.4', 'siegeweapons': '0.2.5'}
    modules = [{'layer': layer, 'name': name, 'version': '1', 'contentSha256': 'b' * 64, 'providers': []}
               for layer, name in [('BOOT', 'java.base'), ('SERVICE', 'fml'), ('PLUGIN', 'forge')]
               + [('GAME', mod) for mod in ('minecraft', 'siegeoverhaul', 'workers', 'recruits')]]
    modules.sort(key=lambda v: (v['layer'], v['name']))
    def origin(name):
        module = 'siegeoverhaul' if name in VERIFY.TRUSTED else 'workers' if '.workers.' in name else 'recruits' if '.recruits.' in name else 'forge' if name.startswith('net.minecraftforge') else 'minecraft'
        return {'type': name, 'module': module, 'source': 'file:/synthetic/' + module + '.jar', 'classResourceSha256': 'c' * 64}
    runtime = {'launch': 'development:forgeclientuserdev', 'distribution': 'CLIENT', 'javaRuntime': '17.0.20+12/OpenJDK 64-Bit Server VM',
               'mods': [{'mod': mod, 'version': versions[mod], 'sourceKind': 'development-combined-module' if mod == 'siegeoverhaul' else 'development-file',
                         'sha256': 'b' * 64, 'bytes': 100} for mod in sorted(versions)],
               'modules': modules, 'services': [{'name': 'fml', 'type': 'TRANSFORMATIONSERVICE', 'file': 'fml.jar'}],
               'pendingMixins': [], 'transformations': [{'owner': 'net.minecraft.world.level.Level', 'kind': 'PLUGIN', 'contextDigests': ['d' * 64]}],
               'firstParty': {'mod': 'siegeoverhaul', 'module': 'siegeoverhaul', 'trustedClasses': VERIFY.TRUSTED}}
    observed = {'generation': 1, 'gameTime': 280, 'target': (141 << 38) | (9 << 12) | 64,
                'dirt': {'pack': 'utf16-sha256:' + 'f' * 64, 'builtin': True, 'sha256': VERIFY.DIRT_HASH, 'bytes': 376},
                'modifierLayers': [], 'listeners': [{'event': event, 'callback': 'net.minecraftforge.common.ForgeHooks#example(' + event + ')void',
                    'priority': 'NORMAL', 'receiveCanceled': False, 'genericFilter': '*',
                    'owner': origin('net.minecraftforge.common.ForgeHooks'), 'declaringClass': origin('net.minecraftforge.common.ForgeHooks'),
                    'wrapper': 'net.minecraftforge.eventbus.ASMEventHandler/generated.Wrapper'} for event in sorted(VERIFY.EVENTS)],
                'implementation': [origin(name) for name in sorted(VERIFY.REQUIRED_CODE)], 'runtime': runtime,
                'chunks': 9, 'sections': 27,
                'registries': [{'chunkX': x, 'sectionY': y, 'chunkZ': z, 'kind': 'ABSENT', 'check': ready}
                               for x in range(7, 10) for z in range(-1, 2) for y in range(3, 6)],
                'lootGraph': ready, 'activeModifiers': 0, 'observation': ready}
    state = {'gameTime': 280, 'workerTick': 280, 'registryMapSizes': [0] * 9, 'existingBlockEntityCounts': [0] * 9,
             'pendingBlockEntityCounts': [0] * 9, 'worldCellCount': 33**3, 'targetState': 'minecraft:dirt',
             'rngKinds': ['net.minecraft.world.level.levelgen.LegacyRandomSource'] * 3,
             'inventorySlots': [27, 27, 41, 2], 'entities': 3, 'itemEntities': 0, 'xpEntities': 0, 'xpValue': 0,
             'nativeQueueSizes': [0] * 4, 'requestCount': 0, 'scheduledTickCounts': [0, 0],
             'journalState': 'STAGE_VERIFIED', 'journalReceipts': 1, 'inFlight': False}
    for key in ('worldCellsSha256', 'rngSha256', 'inventorySha256', 'nativeQueuesSha256', 'ledgerSha256', 'workerDataSha256'):
        state[key] = 'e' * 64
    return {'schema': 'native-dirt-census-qa-v1', 'profileStatus': 'PROFILE_UNREVIEWED', 'packagedProductionAcceptance': False,
            'miningCallbacksInvoked': 0, 'packIdentityEncoding': 'sha256-length-framed-utf16-code-units',
            'bindingAttempted': True, 'postBindStateCaptured': True, 'postInspectionStatesCaptured': [True, True],
            'noEffectsEvidenceComplete': True, 'target': [141, 64, 9], 'buildHeight': [-64, 320], 'stableSinceGameTime': 240,
            'captureGameTime': 280, 'lifecycle': {'signal': 'ServerStartedEvent', 'successfulCompletion': True,
            'startedGameTime': 0, 'startedGeneration': 1, 'currentGeneration': 1}, 'binding': ready, 'bindingCensus': copy.deepcopy(observed),
            'decision': {'reason': 'PROFILE_UNREVIEWED', 'detail': 'Illustrative sample has no approved profile'},
            'censuses': [copy.deepcopy(observed), copy.deepcopy(observed)],
            'metrics': [{'bytesRead': 0 if i == 0 else 1000, 'artifactsHashed': 0 if i == 0 else 7, 'modulesHashed': 0 if i == 0 else 6,
                         'cachedFileChecks': i * 7, 'cachedModuleChecks': i * 6} for i in range(4)],
            'states': [copy.deepcopy(state) for _ in range(4)], 'unchanged': dict.fromkeys(VERIFY.UNCHANGED, True),
            'status': 'captured'}, fill


class CensusVerifierTest(unittest.TestCase):
    def setUp(self): self.result, self.fill = illustrative_evidence()
    def reject(self, pattern=None):
        with self.assertRaisesRegex(ValueError, pattern or '.'):
            VERIFY.validate(self.result, self.fill)
    def mutate_census(self, callback):
        for row in [self.result['bindingCensus'], *self.result['censuses']]: callback(row)

    def test_structural_synthetic_example_is_not_native_acceptance(self):
        self.assertIs(VERIFY.validate(self.result, self.fill), self.result)
    def test_observed_profile_cannot_approve_itself(self):
        self.result['profileStatus'] = 'APPROVED'; self.reject()
    def test_null_profile_must_refuse(self):
        self.result['decision']['reason'] = 'READY'; self.reject()
    def test_no_packaged_claim_from_dev_runtime(self):
        self.result['packagedProductionAcceptance'] = True; self.reject()
    def test_production_catalog_is_distinct(self):
        self.mutate_census(lambda c: c['runtime'].update(launch='production:forgeclient')); self.reject()
    def test_no_mining_callbacks(self):
        self.result['miningCallbacksInvoked'] = 1; self.reject()
    def test_before_stable_window_refuses(self):
        self.result['stableSinceGameTime'] = 279; self.reject()
    def test_wrong_finished_target_refuses(self):
        self.result['target'] = [0, 64, 0]; self.reject()
    def test_false_lifecycle_completion_refuses(self):
        self.result['lifecycle']['successfulCompletion'] = False; self.reject()
    def test_reload_invalidates(self):
        self.result['lifecycle']['currentGeneration'] = 2; self.reject()
    def test_player_datapack_sync_not_reload_completion(self):
        self.result['lifecycle']['signal'] = 'OnDatapackSyncEvent'; self.reject()
    def test_binding_refusal_is_preserved(self):
        self.result['binding']['reason'] = 'RUNTIME_UNPROVEN'; self.reject()
    def test_incomplete_radius_refuses(self):
        self.mutate_census(lambda c: c.update(sections=26)); self.reject()
    def test_duplicate_coordinate_cannot_hide_omission(self):
        self.mutate_census(lambda c: c['registries'].__setitem__(26, copy.deepcopy(c['registries'][0]))); self.reject()
    def test_registry_status_is_not_ignored(self):
        self.mutate_census(lambda c: c['registries'][0]['check'].update(reason='GAME_EVENT_LISTENER')); self.reject()
    def test_unknown_registry_refuses(self):
        self.mutate_census(lambda c: c['registries'][0].update(kind='UNKNOWN')); self.reject()
    def test_changed_dirt_bytes_refuse(self):
        self.mutate_census(lambda c: c['dirt'].update(sha256='f' * 64)); self.reject()
    def test_loot_graph_has_independent_check(self):
        self.mutate_census(lambda c: c['lootGraph'].update(reason='LOOT_GRAPH_CHANGED')); self.reject()
    def test_active_glm_refuses(self):
        self.mutate_census(lambda c: c.update(activeModifiers=1)); self.reject()
    def test_original_artifact_is_not_remapped_runtime(self):
        self.mutate_census(lambda c: c['runtime']['mods'][-1].update(sourceKind='packaged-file')); self.reject()
    def test_companion_hash_bound_to_original_one_fill_result(self):
        self.fill['loadedCompanionArtifacts']['workers']['sha256'] = 'a' * 64; self.reject()
    def test_required_class_cannot_be_omitted(self):
        self.mutate_census(lambda c: c['implementation'].pop()); self.reject()
    def test_first_party_is_exact_explicit_set(self):
        self.mutate_census(lambda c: c['runtime']['firstParty'].update(trustedClasses=['com.example.Anything'])); self.reject()
    def test_first_party_content_remains_real_hash(self):
        self.mutate_census(lambda c: next(m for m in c['runtime']['mods'] if m['mod'] == 'siegeoverhaul').update(sha256='trusted-first-party')); self.reject()
    def test_first_party_module_must_match_modfile_content(self):
        self.mutate_census(lambda c: next(m for m in c['runtime']['modules'] if m['name'] == 'siegeoverhaul').update(contentSha256='a' * 64)); self.reject()
    def test_required_module_layer_cannot_disappear(self):
        self.mutate_census(lambda c: c['runtime']['modules'].__setitem__(slice(None), [m for m in c['runtime']['modules'] if m['layer'] != 'PLUGIN'])); self.reject()
    def test_pending_mixin_refuses(self):
        self.mutate_census(lambda c: c['runtime'].update(pendingMixins=['unexpected'])); self.reject()
    def test_no_arbitrary_transformer_labels(self):
        self.mutate_census(lambda c: c['runtime']['transformations'][0].update(context=['token=secret'])); self.reject()
    def test_no_arbitrary_transformer_label_inside_digest(self):
        self.mutate_census(lambda c: c['runtime']['transformations'][0].update(contextDigests=['token=secret'])); self.reject()
    def test_no_arbitrary_plugin_metadata(self):
        self.mutate_census(lambda c: c['runtime']['services'][0].update(secret='private')); self.reject()
    def test_no_credential_uri_components(self):
        for uri in ('file:/jar?token=secret', 'file:/jar#secret', 'file://user:password@localhost/jar', 'https://example.com/jar'):
            with self.subTest(uri=uri):
                self.result, self.fill = illustrative_evidence()
                self.mutate_census(lambda c: c['implementation'][0].update(source=uri)); self.reject()
    def test_no_raw_jvm_arguments(self):
        self.result['jvmArgs'] = ['secret']; self.reject()
    def test_no_private_identity_export(self):
        self.mutate_census(lambda c: c.update(identity={'rawAudit': ['secret']})); self.reject()
    def test_unknown_pack_text_refuses(self):
        self.mutate_census(lambda c: c['dirt'].update(pack='secret=value')); self.reject()
    def test_post_bind_state_cannot_be_assumed(self):
        self.result['postBindStateCaptured'] = False; self.reject()
    def test_missing_post_inspection_state_refuses(self):
        self.result['postInspectionStatesCaptured'] = [True, False]; self.reject()
    def test_partial_state_evidence_cannot_look_complete(self):
        self.result['noEffectsEvidenceComplete'] = False; self.reject()
    def test_no_raw_pack_identity_is_accepted(self):
        self.mutate_census(lambda c: c['dirt'].update(pack='Mod Resources')); self.reject()
    def test_pack_identity_encoding_is_explicit(self):
        self.result['packIdentityEncoding'] = 'raw'; self.reject()
    def test_failure_phase_cannot_hide_under_captured_receipt(self):
        self.fill['dirtCensus']['failurePhase'] = 'OUTPUT_WRITE'; self.reject()
    def test_actual_partial_binding_census_is_required(self):
        self.result.pop('bindingCensus'); self.reject()
    def test_binding_census_cannot_be_replaced_by_fresh_summary(self):
        self.result['bindingCensus']['dirt']['sha256'] = 'a' * 64; self.reject()
    def test_fresh_census_must_match(self):
        self.result['censuses'][1]['implementation'][0]['classResourceSha256'] = 'a' * 64; self.reject()
    def test_no_startup_reread_on_inspection(self):
        self.result['metrics'][3]['bytesRead'] += 1; self.reject()
    def test_module_recheck_cannot_be_skipped(self):
        self.result['metrics'][3]['cachedModuleChecks'] = self.result['metrics'][2]['cachedModuleChecks']; self.reject()
    def test_cannot_substitute_profile_normalization_for_real_census(self):
        self.mutate_census(lambda c: c['runtime']['mods'][0].update(sha256='trusted-first-party')); self.reject()
    def test_each_no_effect_category_required(self):
        for category in VERIFY.UNCHANGED:
            with self.subTest(category=category):
                self.result, self.fill = illustrative_evidence(); self.result['unchanged'].pop(category); self.reject()
    def test_private_equality_cannot_be_true_when_digest_changed(self):
        self.result['states'][1]['rngSha256'] = 'a' * 64; self.reject()
    def test_world_rng_queues_receipts_and_inventories_cannot_change(self):
        for key in ('worldCellsSha256', 'inventorySha256', 'nativeQueuesSha256', 'ledgerSha256', 'workerDataSha256'):
            with self.subTest(key=key):
                self.result, self.fill = illustrative_evidence(); self.result['states'][3][key] = 'a' * 64; self.reject()
    def test_no_world_advance_during_observer(self):
        self.result['states'][3]['gameTime'] += 1; self.reject()
    def test_missing_rng_state_refuses(self):
        self.result['states'][0]['rngKinds'].pop(); self.reject()
    def test_items_or_xp_refuse(self):
        self.result['states'][0]['xpEntities'] = 1; self.reject()
    def test_dirty_observer_is_not_captured(self):
        self.result['unchanged']['worldCells'] = False; self.reject()
    def test_two_launch_profile_ignores_only_installation_source(self):
        other = copy.deepcopy(self.result)
        other['censuses'][0]['implementation'][0]['source'] = 'union:/different/install!/'
        other['censuses'][0]['gameTime'] = 1000
        self.assertEqual(VERIFY.profile_shape(self.result), VERIFY.profile_shape(other))
        other['censuses'][0]['implementation'][0]['classResourceSha256'] = 'a' * 64
        self.assertNotEqual(VERIFY.profile_shape(self.result), VERIFY.profile_shape(other))
    def test_listener_order_remains_exact_across_launches(self):
        other = copy.deepcopy(self.result); other['censuses'][0]['listeners'].reverse()
        self.assertNotEqual(VERIFY.profile_shape(self.result), VERIFY.profile_shape(other))
    def test_missing_capture_receipt_refuses(self):
        self.fill.pop('dirtCensus'); self.reject()
    def test_refused_capture_does_not_rewrite_original_result(self):
        self.fill['dirtCensus'].update(status='refused', artifactSha256='', failureType='java.lang.AssertionError')
        self.reject()
        self.assertIs(ONE_FILL_TEST.VERIFY.validate(self.fill), self.fill)
    def test_missing_or_malformed_safe_digest_refuses(self):
        for digest in ('', 'token=secret', 'a' * 63):
            with self.subTest(digest=digest):
                self.fill['dirtCensus']['artifactSha256'] = digest; self.reject()
    def test_mismatched_sidecar_digest_refuses(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / 'dirt-census.json'; data = json.dumps(self.result).encode(); path.write_bytes(data)
            self.fill['dirtCensus']['artifactSha256'] = hashlib.sha256(data).hexdigest()
            VERIFY.verify_sidecar_digest(path, self.fill)
            path.write_bytes(data + b' ')
            with self.assertRaisesRegex(ValueError, 'differs'): VERIFY.verify_sidecar_digest(path, self.fill)
    def test_false_integer_fields_refuse(self):
        self.mutate_census(lambda c: c.update(generation=True)); self.reject()

    def test_rejects_duplicate_or_nonfinite_json(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / 'bad.json'
            for value in ('{"key":1,"key":2}', '{"x":NaN}', '{"x":Infinity}'):
                path.write_text(value)
                with self.assertRaises(ValueError): VERIFY.strict_load(path)


class CiRunIdentityTest(unittest.TestCase):
    def test_distinct_runs_or_attempts_are_accepted(self):
        with tempfile.TemporaryDirectory() as directory:
            a, b = Path(directory) / 'a', Path(directory) / 'b'; a.mkdir(); b.mkdir()
            (a / 'ci-run.json').write_text('{"runId":"123","runAttempt":"1"}')
            for receipt in ({'runId': '124', 'runAttempt': '1'}, {'runId': '123', 'runAttempt': '2'}):
                (b / 'ci-run.json').write_text(json.dumps(receipt)); VERIFY.distinct_ci_runs(a, b)
    def test_same_run_attempt_cannot_be_copied_as_two_launches(self):
        with tempfile.TemporaryDirectory() as directory:
            a, b = Path(directory) / 'a', Path(directory) / 'b'; a.mkdir(); b.mkdir()
            for path in (a, b): (path / 'ci-run.json').write_text('{"runId":"123","runAttempt":"1"}')
            with self.assertRaisesRegex(ValueError, 'Distinct CI'): VERIFY.distinct_ci_runs(a, b)
    def test_missing_ci_receipt_refuses(self):
        with tempfile.TemporaryDirectory() as directory:
            with self.assertRaises(FileNotFoundError): VERIFY.ci_identity(directory)
    def test_malformed_private_or_unknown_ci_metadata_refuses(self):
        with tempfile.TemporaryDirectory() as directory:
            for receipt in ({'runId': '0', 'runAttempt': '1'}, {'runId': '123', 'runAttempt': '0'},
                            {'runId': 'secret=value', 'runAttempt': '1'}, {'runId': 123, 'runAttempt': '1'},
                            {'runId': '123', 'runAttempt': '1', 'environment': 'private'}):
                with self.subTest(receipt=receipt):
                    (Path(directory) / 'ci-run.json').write_text(json.dumps(receipt))
                    with self.assertRaises(ValueError): VERIFY.ci_identity(directory)


class CaptureBoundaryExecutionTest(unittest.TestCase):
    """Run the actual dependency-free QA failure boundary on Java17; no Minecraft launch."""
    @classmethod
    def setUpClass(cls):
        compiler, cls.java = shutil.which('javac'), shutil.which('java')
        if not compiler or not cls.java:
            raise RuntimeError('Java 17+ javac/java must be on PATH for the QA boundary regression')
        cls.temp = tempfile.TemporaryDirectory(); root = Path(cls.temp.name)
        harness = root / 'BoundaryHarness.java'
        harness.write_text(r"""
package com.devfarinsky.siegeoverhaul.nativecompat;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
public class BoundaryHarness {
    public static void main(String[] args) throws Exception {
        Path directory = Path.of(args[1]);
        Path original = directory.resolve("result.json");
        byte[] passed = "{\"status\":\"passed\",\"journalState\":\"STAGE_VERIFIED\"}".getBytes(StandardCharsets.UTF_8);
        Files.write(original, passed);
        if (args[0].startsWith("window-")) {
            java.util.concurrent.atomic.AtomicInteger reads = new java.util.concurrent.atomic.AtomicInteger(), posts = new java.util.concurrent.atomic.AtomicInteger();
            Object privateValue = new Object();
            var window = NativeDirtCensusBoundary.readWindow(() -> {
                reads.incrementAndGet();
                if (args[0].equals("window-read-failure") || args[0].equals("window-both-fail")) throw new AssertionError("token=secret");
                return privateValue;
            }, () -> {
                posts.incrementAndGet();
                if (args[0].equals("window-post-failure") || args[0].equals("window-both-fail")) throw new IllegalArgumentException("token=secret");
                return null;
            });
            boolean readFails = args[0].equals("window-read-failure") || args[0].equals("window-both-fail");
            boolean postFails = args[0].equals("window-post-failure") || args[0].equals("window-both-fail");
            if (reads.get() != 1 || posts.get() != 1 || window.postStateCaptured() == postFails
                    || window.readFailureType().isEmpty() == readFails || window.postFailureType().isEmpty() == postFails
                    || (!readFails && window.value() != privateValue)) throw new AssertionError("Read/post state accounting differs");
            if (window.readFailureType().contains("secret") || window.postFailureType().contains("secret")) throw new AssertionError("Unsafe failure text");
            if (!java.util.Arrays.equals(passed, Files.readAllBytes(original))) throw new AssertionError("Original outcome changed");
            System.out.println("PASS " + args[0]); return;
        }
        if (args[0].startsWith("identity-")) {
            String value = args[0].equals("identity-spaces") ? "Mod Resources" : "token=secret\n";
            String identity = NativeDirtCensusBoundary.packIdentity(value);
            if (!identity.matches("utf16-sha256:[0-9a-f]{64}") || identity.contains(value)) throw new AssertionError("Raw identity escaped");
            if (args[0].equals("identity-surrogates") && NativeDirtCensusBoundary.packIdentity("\ud800").equals(NativeDirtCensusBoundary.packIdentity("\ud801")))
                throw new AssertionError("Exact UTF16 identity lost");
            if (args[0].equals("identity-limit")) {
                boolean refused = false;
                try { NativeDirtCensusBoundary.packIdentity("a".repeat(4097)); } catch (IllegalArgumentException expected) { refused = true; }
                if (!refused) throw new AssertionError("Identity bound ignored");
            }
            System.out.println("PASS " + args[0]); return;
        }
        var receipt = NativeDirtCensusBoundary.capture(() -> {
            switch (args[0]) {
                case "preflight": throw new AssertionError("token=secret-preflight");
                case "export": throw new IllegalArgumentException("token=secret-export");
                case "serialization": throw new UnsupportedOperationException("token=secret-serialization");
                case "linkage": throw new NoClassDefFoundError("token=secret-linkage");
                case "size": return new NativeDirtCensusBoundary.Payload(directory, "x".repeat(2_000_001), "captured");
                case "utf8size": return new NativeDirtCensusBoundary.Payload(directory, "\u20ac".repeat(800_000), "captured");
                case "existing": Files.writeString(directory.resolve("dirt-census.json"), "old-sidecar"); break;
                case "missingdirectory": return new NativeDirtCensusBoundary.Payload(directory.resolve("absent"), "{}", "captured");
                case "invalidstatus": return new NativeDirtCensusBoundary.Payload(directory, "{}", "approved");
                case "success": break;
                default: throw new AssertionError("Unknown test scenario");
            }
            return new NativeDirtCensusBoundary.Payload(directory, "{}", "captured");
        });
        if (!java.util.Arrays.equals(passed, Files.readAllBytes(original))) throw new AssertionError("Original one-FILL outcome changed");
        if (!receipt.enabled() || receipt.toString().contains("token=") || receipt.toString().contains("secret"))
            throw new AssertionError("Unsafe receipt");
        if (args[0].equals("success")) {
            String digest = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(directory.resolve("dirt-census.json"))));
            if (!receipt.status().equals("captured") || !receipt.artifactSha256().equals(digest) || !receipt.failureType().isEmpty() || !receipt.failurePhase().isEmpty())
                throw new AssertionError("Missing exact successful capture digest");
        } else {
            if (!receipt.status().equals("refused") || !receipt.artifactSha256().isEmpty() || receipt.failureType().isEmpty()
                    || !java.util.Set.of("CAPTURE", "PAYLOAD_BOUNDS", "OUTPUT_WRITE").contains(receipt.failurePhase()))
                throw new AssertionError("Refusal escaped or retained approval");
            if (args[0].equals("existing") && !Files.readString(directory.resolve("dirt-census.json")).equals("old-sidecar"))
                throw new AssertionError("Existing sidecar was overwritten");
        }
        System.out.println("PASS " + args[0]);
    }
}
""")
        result = subprocess.run([compiler, '--release', '17', '-d', str(root),
                                 str(QA / 'NativeDirtCensusBoundary.java'), str(harness)], capture_output=True, text=True)
        if result.returncode: raise AssertionError(result.stderr)
    @classmethod
    def tearDownClass(cls): cls.temp.cleanup()
    def run_case(self, case):
        with tempfile.TemporaryDirectory() as directory:
            result = subprocess.run([self.java, '-cp', self.temp.name,
                'com.devfarinsky.siegeoverhaul.nativecompat.BoundaryHarness', case, directory], capture_output=True, text=True)
            self.assertEqual(result.returncode, 0, result.stderr); self.assertEqual(result.stdout.strip(), 'PASS ' + case)
    def test_preflight_refusal_preserves_original(self): self.run_case('preflight')
    def test_export_refusal_preserves_original(self): self.run_case('export')
    def test_serialization_refusal_preserves_original(self): self.run_case('serialization')
    def test_linkage_refusal_preserves_original(self): self.run_case('linkage')
    def test_character_size_refusal_preserves_original(self): self.run_case('size')
    def test_utf8_byte_size_refusal_preserves_original(self): self.run_case('utf8size')
    def test_existing_sidecar_preserved_and_refused(self): self.run_case('existing')
    def test_io_failure_preserves_original(self): self.run_case('missingdirectory')
    def test_status_cannot_bless_profile(self): self.run_case('invalidstatus')
    def test_success_binds_exact_written_bytes(self): self.run_case('success')
    def test_read_failure_still_captures_post_state(self): self.run_case('window-read-failure')
    def test_post_failure_is_explicit(self): self.run_case('window-post-failure')
    def test_both_failures_retained(self): self.run_case('window-both-fail')
    def test_private_value_stays_exact(self): self.run_case('window-success')
    def test_pack_spaces_are_digest_only(self): self.run_case('identity-spaces')
    def test_pack_secrets_are_digest_only(self): self.run_case('identity-secret')
    def test_pack_surrogates_remain_distinct(self): self.run_case('identity-surrogates')
    def test_pack_identity_is_bounded(self): self.run_case('identity-limit')


class CensusSourceContractTest(unittest.TestCase):
    def test_opt_in_is_default_off(self):
        java = (QA / 'NativeDirtCensusQa.java').read_text()
        self.assertIn('Boolean.getBoolean("siegeoverhaul.nativeEarthworksQa")', java)
        self.assertIn('&& Boolean.getBoolean("siegeoverhaul.nativeDirtCensusQa")', java)
        gradle = (ROOT / 'gradle/native-earthworks-qa.gradle').read_text()
        self.assertIn("gradleProperty('nativeDirtCensusQa')", gradle)
        self.assertIn(".getOrElse('false')", gradle)
    def test_capture_follows_original_finish_assertions_and_stable_window(self):
        java = (QA / 'NativeEarthworksQa.java').read_text()
        capture = java.index('NativeDirtCensusQa.capture(')
        self.assertGreater(capture, java.index('"First-slice receipt/retirement boundary changed"'))
        self.assertGreater(capture, java.index('"Exact native dispatch evidence missing"'))
        self.assertIn('now - stableSince < 40', java)
        self.assertEqual(java.count('NativeDirtCensusQa.capture('), 1)
    def test_original_fixture_and_verifier_remain_frozen(self):
        expected = {
            'src/nativeEarthworksQa/java/com/devfarinsky/siegeoverhaul/nativecompat/NativeEarthworksFixture.java': 'd3eebb8eea8f1a5d5feea2c33cb38cd8304673c8c204f8c1494b7d843cf1c2dc',
            'scripts/verify-native-earthworks.py': '3cf9304e9a9a92b4f89a4066e04ddaa04aa8fe28a887f79c4e2ff3a7ef6e49ae',
        }
        for path, digest in expected.items(): self.assertEqual(hashlib.sha256((ROOT / path).read_bytes()).hexdigest(), digest)
    def test_no_profile_factory_native_callbacks_or_effectful_probes(self):
        java = (QA / 'NativeDirtCensusQa.java').read_text()
        for forbidden in ('new NativeDirtPolicy.Profile', 'new Profile(', '.destroyBlock(', '.setBlock(', '.nextInt(', '.nextLong(',
                          '.nextDouble(', '.nextGaussian(', '.setSeed(', '.setNoAi(', '.invoke(', '.setAccessible(',
                          'new ItemStack(', 'new ItemEntity(', 'getListenerRegistry(', 'getInputArguments(', 'System.getenv(',
                          '.getLootTable(', '.getRandomItems(', '.post(', '.canUse(', '.tick(', '.evaluate(TARGET,'):
            self.assertNotIn(forbidden, java)
        self.assertIn('NativeDirtPolicy.evaluate(read.value(), null, null)', java)
        self.assertIn('observations.add(exportCensus(read.value().census()))', java)
        self.assertNotIn('toJsonTree(observation)', java)
        self.assertNotIn('toJsonTree(reader)', java)
        self.assertNotIn('toJsonTree(snapshots)', java)
    def test_entire_observer_is_inside_nonthrowing_failure_boundary(self):
        java = (QA / 'NativeDirtCensusQa.java').read_text()
        public = java[java.index('static NativeDirtCensusBoundary.Receipt capture('):java.index('private static NativeDirtCensusBoundary.Payload captureEvidence(')]
        self.assertIn('NativeDirtCensusBoundary.capture(() -> captureEvidence(', public)
        self.assertNotIn('require(', public)
        boundary = (QA / 'NativeDirtCensusBoundary.java').read_text()
        boundary = boundary[boundary.index('    static Receipt capture('):]
        self.assertLess(boundary.index('try {'), boundary.index('operation.run()'))
        self.assertLess(boundary.index('Files.write('), boundary.index('catch (Exception | AssertionError | LinkageError'))
        self.assertNotIn('getMessage()', boundary)
        self.assertNotIn('toString()', boundary)
        fixture = (QA / 'NativeEarthworksQa.java').read_text()
        self.assertIn('if (censusReceipt != null) REPORT.put("dirtCensus", censusReceipt)', fixture)
        self.assertNotIn('require(censusReceipt', fixture)

    def test_real_reload_lifecycle_and_deliberate_invalidation(self):
        java = (QA / 'NativeDirtCensusQa.java').read_text()
        self.assertIn('ServerStartedEvent event', java); self.assertIn('reader.completeReload(startedGeneration)', java)
        self.assertIn('AddReloadListenerEvent event', java); self.assertIn('if (ENABLED && epoch != null) epoch.beginReload()', java)
        self.assertNotIn('OnDatapackSyncEvent', java)
        self.assertEqual(java.count('reader.bindRuntimeCensus(TARGET)'), 1)
        self.assertIn('out.put("bindingCensus", exportCensus(binding.value()))', java)
    def test_private_rng_nbt_and_live_identity_not_exported(self):
        java = (QA / 'NativeDirtCensusQa.java').read_text()
        self.assertIn('state.put("rng", rng)', java); self.assertIn('exported.put("rngSha256", hash(rng.stream().map(RngState::values).toList().toString()))', java)
        self.assertNotIn('exported.put("rng",', java)
        self.assertIn('new Snapshot(state, exported)', java)
        self.assertIn('Snapshot::evidence', java)
        boundary = (QA / 'NativeDirtCensusBoundary.java').read_text()
        self.assertIn('StandardOpenOption.CREATE_NEW', boundary)
    def test_read_windows_capture_post_state_before_export(self):
        java = (QA / 'NativeDirtCensusQa.java').read_text()
        self.assertIn('NativeDirtCensusBoundary.readWindow(() -> reader.bindRuntimeCensus(TARGET)', java)
        self.assertIn('postBindCaptured = binding.postStateCaptured()', java)
        self.assertLess(java.index('postBindCaptured = binding.postStateCaptured()'), java.index('exportCensus(binding.value())'))
        self.assertIn('observations.size() == 2', java)
        self.assertIn('!equal.isEmpty()', java)
        self.assertIn('"phase", failedPhase.name()', java)
        self.assertNotIn('toJsonTree(binding)', java)
        self.assertNotIn('out.put("bindingCensus", binding.value())', java)
        self.assertIn('NativeDirtCensusBoundary.packIdentity(original.pack())', java)
        self.assertIn('original.builtin(), original.sha256(), original.bytes()', java)

    def test_forge_layer_fixture_is_exact_official_resource(self):
        resource = ROOT / 'src/test/resources/native-dirt/forge-47.4.16-empty-global-loot-modifiers.json'
        data = resource.read_bytes()
        self.assertEqual(len(data), 254)
        self.assertEqual(hashlib.sha256(data).hexdigest(), 'ed72002040acf4aa51ce8d92dc9591bbf423f9be9860022e36060eaabb0ca4f3')
        parsed = json.loads(data); self.assertEqual(set(parsed), {'comment', 'replace', 'entries'})
        self.assertIs(parsed['replace'], False); self.assertEqual(parsed['entries'], [])
        provider = (ROOT / 'src/main/java/com/devfarinsky/siegeoverhaul/nativecompat/NativeDirtCensus.java').read_text()
        self.assertIn('builtin && bytes.length == 254', provider)
        self.assertIn('FORGE_EMPTY_GLM_SHA256.equals(hash(bytes))', provider)
        self.assertIn('emptyModifierLayer(bytes, layer.isBuiltin())', provider)
        self.assertNotIn('field.equals("comment")', provider)

    def test_production_cut_refusal_unchanged(self):
        production = (ROOT / 'src/main/java/com/devfarinsky/siegeoverhaul/nativecompat/EarthworksExecution.java').read_text()
        self.assertNotIn('NativeDirtCensus', production)
        self.assertNotIn('NativeDirtPolicy', production)
    def test_pr_census_opt_in_is_explicit_and_same_repository_only(self):
        workflow = (ROOT / '.github/workflows/native-earthworks-qa.yml').read_text()
        job_guard = workflow[workflow.index('    if: >-'):workflow.index('    runs-on:')]
        self.assertIn("contains(github.event.pull_request.labels.*.name, 'native-earthworks-qa')", job_guard)
        self.assertIn("contains(github.event.pull_request.labels.*.name, 'native-dirt-census-qa')", job_guard)
        self.assertIn('github.event.pull_request.head.repo.full_name == github.repository', job_guard)
        flag = workflow[workflow.index('      NATIVE_DIRT_CENSUS:'):workflow.index('    steps:')]
        self.assertIn("github.event_name == 'workflow_dispatch' && inputs.dirt_census", flag)
        self.assertIn("github.event_name == 'pull_request' && github.event.pull_request.head.repo.full_name == github.repository &&", flag)
        self.assertIn("contains(github.event.pull_request.labels.*.name, 'native-dirt-census-qa')", flag)
        self.assertNotIn("'native-earthworks-qa'", flag)
        self.assertNotIn('pull_request_target', workflow)
        self.assertIn('"$GITHUB_RUN_ID" "$GITHUB_RUN_ATTEMPT"', workflow)
        self.assertIn('build/native-earthworks-qa/evidence/ci-run.json', workflow)

    def test_separate_workflow_opt_in_keeps_original_verifier(self):
        workflow = (ROOT / '.github/workflows/native-earthworks-qa.yml').read_text()
        self.assertIn('dirt_census:', workflow); self.assertIn('default: false', workflow)
        self.assertIn('verify-native-earthworks.py', workflow); self.assertIn('verify-native-dirt-census.py', workflow)
        self.assertIn('contents: read', workflow); self.assertNotIn('secrets.', workflow)
        self.assertNotIn('--add-opens', workflow); self.assertNotIn('-javaagent', workflow)


if __name__ == '__main__': unittest.main()
