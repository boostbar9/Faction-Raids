"""Static offline-launch contracts; not a substitute for a real ForgeGradle run."""
from pathlib import Path
import unittest


REPO = Path(__file__).resolve().parents[2]


class NativeQaRuntimePreparationTest(unittest.TestCase):
    def test_preparation_is_opt_in_and_resolves_launch_collections_in_its_action(self):
        build = (REPO / 'build.gradle').read_text()
        opt_in = build.index("if (providers.gradleProperty('nativeQa')")
        start = build.index("    tasks.register('prepareNativeQaClient') {")
        end = build.index('\n    }\n}', start)
        task = build[start:end]
        self.assertLess(opt_in, start)
        self.assertLess(end, build.index("if (providers.gradleProperty('nativeServerQa')"))
        self.assertIn("dependsOn 'nativeQaClasses', 'prepareRunClient'", task)
        action = task.split('doLast {', 1)[1]
        self.assertIn("tasks.named('runClient').get()", action)
        self.assertIn('clientRun.minecraftArtifacts', action)
        self.assertIn('clientRun.runtimeClasspathArtifacts', action)
        self.assertIn('clientRun.runConfig.get().allSources.collect { it.runtimeClasspath }', action)
        self.assertIn('runtimeFiles.files.size()', action)
        self.assertNotRegex(task, r'dependsOn[^\n]*[\'"]runClient[\'"]')
        self.assertNotRegex(action, r'\b(exec|javaexec|execute|setActions|onlyIf)\s*[(\{]')

    def test_both_workflows_warm_online_before_offline_launch_with_unchanged_caps(self):
        for mode, prep_cap, run_cap in [
                ('staged-handoff', 'timeout --signal=KILL 1800s', 'timeout --signal=KILL 600s'),
                ('staged-unload', 'timeout --signal=TERM --kill-after=10s 15m',
                 'timeout --signal=TERM --kill-after=10s 10m')]:
            with self.subTest(mode=mode):
                workflow = (REPO / '.github/workflows' / f'native-{mode}-qa.yml').read_text()
                commands = [line.strip() for line in workflow.splitlines() if './gradlew ' in line]
                self.assertEqual(len(commands), 2)
                prepare, launch = commands
                self.assertIn('prepareNativeQaClient', prepare)
                self.assertNotIn('--offline', prepare)
                self.assertIn(prep_cap, prepare)
                self.assertIn('--offline', launch)
                self.assertRegex(launch, r'\brunClient\b')
                self.assertIn(run_cap, workflow)
                for command in commands:
                    self.assertIn('-PnativeQa=true', command)
                    self.assertIn(f'-PnativeQaMode={mode}', command)
                    self.assertNotRegex(command, r'(?:--exclude-task|--continue|--refresh-dependencies|\s-x\s)')
                self.assertIn('set -euo pipefail', workflow)
                self.assertIn('persist-credentials: false', workflow)
                self.assertIn('cache-read-only: true', workflow)
                self.assertIn('contents: read', workflow)
                self.assertIn('scripts/audit-native-inventory-bytecode.py', workflow)
                self.assertIn('if-no-files-found: error', workflow)
                self.assertLess(workflow.index('prepareNativeQaClient'), workflow.index('--offline'))

    def test_native_acceptance_verifiers_remain_required(self):
        handoff = (REPO / '.github/workflows/native-staged-handoff-qa.yml').read_text()
        unload = (REPO / '.github/workflows/native-staged-unload-qa.yml').read_text()
        self.assertIn('run: python3 scripts/verify-native-staged-handoff.py', handoff)
        for invariant in ["data['nativeInventoryReloadVerified']", "data['returnedConservationVerified']",
                          "finalization['serializationInvocationObservedNotFsync']", "data['cancelPacketAuthenticated']"]:
            self.assertIn(invariant, unload)
        for mode in ['handoff', 'unload']:
            guide = (REPO / 'docs' / f'native-staged-{mode}-qa.md').read_text()
            self.assertIn('prepareNativeQaClient', guide)
            self.assertIn('--offline runClient', guide)


if __name__ == '__main__':
    unittest.main()
