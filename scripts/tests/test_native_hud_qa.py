"""Static/receipt HUD contracts. Synthetic receipts here are not Minecraft evidence."""
import contextlib
import importlib.util
import io
import json
from pathlib import Path
import re
import shutil
import subprocess
import struct
import tempfile
import unittest
import zlib

REPO = Path(__file__).resolve().parents[2]
SPEC = importlib.util.spec_from_file_location('verify_native_hud', REPO / 'scripts/verify-native-hud.py')
verify = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(verify)
HARNESS = REPO / 'src/nativeQa/java/com/devfarinsky/siegeoverhaul/nativecompat/NativeHudQa.java'


def synthetic_png(width, height):
    """A valid generated test image for receipt-parser tests, never a QA artifact."""
    def chunk(kind, value):
        body = kind + value
        return struct.pack('>I', len(value)) + body + struct.pack('>I', zlib.crc32(body) & 0xffffffff)
    rows = b''.join(b'\x00' + bytes((x + y) % 256 for x in range(width * 3)) for y in range(height))
    return (b'\x89PNG\r\n\x1a\n' + chunk(b'IHDR', struct.pack('>IIBBBBB', width, height, 8, 2, 0, 0, 0))
            + chunk(b'IDAT', zlib.compress(rows)) + chunk(b'IEND', b''))


class NativeHudSourceContracts(unittest.TestCase):
    def test_hud_is_explicit_opt_in_and_unshipped(self):
        build = (REPO / 'build.gradle').read_text()
        source = HARNESS.read_text()
        self.assertIn('"hud".equals(System.getProperty("siegeoverhaul.nativeQa.mode"))', source)
        self.assertIn('Boolean.getBoolean("siegeoverhaul.nativeQa")', source)
        self.assertIn("'hud': 'build/native-hud-qa/client'", build)
        self.assertIn("source sourceSets.nativeQa", build)
        self.assertNotIn('from sourceSets.nativeQa', build)
        baseline = (HARNESS.parent / 'NativeBuildingQa.java').read_text()
        self.assertRegex(baseline, r'Set.of\([^;]+"hud"')

    def test_fixture_uses_real_frames_and_native_keyboard_without_private_writes(self):
        source = HARNESS.read_text()
        for item in ['Screenshot.takeScreenshot(mc.getMainRenderTarget())', 'pixels.writeToFile',
                     'new Robot()', 'keyboard.keyPress(key)', 'GLFW.glfwFocusWindow',
                     'new CoreHireScreen(', 'new SiegeCommandScreen(', 'new ProtectedConstructionScreen(',
                     'new SiegeOverhaulConfigScreen(', 'new CoreHireMenu(', 'createFreshLevel(',
                     'Refusing an existing HUD fixture world', 'Refusing to overwrite existing HUD evidence',
                     'client data only, no live faction/core or server transaction',
                     'Hired, unavailable, insufficient and affordable', 'private static List<String> portraitEvidence']:
            self.assertIn(item, source)
        self.assertNotRegex(source, r'\bfield\.set(?:Int|Long|Boolean|Float|Double)?\(')
        for action in ['purchaseCoreOffer(', 'protectedConstructionAction(', 'setBlock(',
                       'clickMenuButton(', 'verifyCompletion(', 'commissionPaid(', 'projectionAuthorized(']:
            self.assertNotIn(action, source)
        self.assertIn('click("No")', source)
        self.assertNotIn('click("Yes")', source)

    def test_headless_hint_is_changed_only_after_isolation_and_before_awt_initialization(self):
        source = HARNESS.read_text()
        start = source.index('private static void initialize(Minecraft mc)')
        keyboard_start = source.index('private static void initializeKeyboard(Minecraft mc)')
        initialize = source[start:keyboard_start]
        self.assertLess(initialize.index('REPORT.put("mode", "hud")'), initialize.index('initializeKeyboard(mc)'))
        self.assertLess(initialize.index('REPORT.put("coverage"'), initialize.index('initializeKeyboard(mc)'))
        self.assertLess(initialize.index('directory.toRealPath()'), initialize.index('initializeKeyboard(mc)'))
        self.assertLess(initialize.index('REPORT.put("loadedModVersions"'), initialize.index('initializeKeyboard(mc)'))
        keyboard = source[keyboard_start:source.index('private static void requestViewport', keyboard_start)]
        self.assertIn('mc.screen instanceof TitleScreen && mc.level == null && mc.getSingleplayerServer() == null', keyboard)
        self.assertIn('GLFW.GLFW_VISIBLE', keyboard)
        self.assertIn('System.getenv("DISPLAY")', keyboard)
        self.assertLess(keyboard.index('"ready", false'), keyboard.index('System.setProperty("java.awt.headless", "false")'))
        self.assertLess(keyboard.index('System.setProperty("java.awt.headless", "false")'), keyboard.index('java.awt.GraphicsEnvironment.isHeadless()'))
        self.assertLess(keyboard.index('require(!headless'), keyboard.index('new Robot()'))
        self.assertLess(keyboard.index('new Robot()'), keyboard.index('"ready", true'))
        self.assertIn('refusing to replace cached AWT state', keyboard)
        self.assertNotIn('getDeclaredField', keyboard)
        self.assertNotIn('setAccessible', keyboard)
        self.assertNotIn('keyPressed(', keyboard)
        self.assertEqual(source.count('System.setProperty("java.awt.headless"'), 1)

    @unittest.skipUnless(shutil.which('java'), 'Java is required for the isolated AWT cache contract')
    def test_awt_property_respects_cached_headless_state_in_separate_jvms(self):
        # This proves the JVM property timing only; it does not open a display,
        # construct Robot, deliver keys or stand in for native Minecraft QA.
        source = r"""
        public class HudHeadlessTiming {
            public static void main(String[] args) {
                System.setProperty("java.awt.headless", "true");
                boolean cachedCase = args[0].equals("cached");
                if (cachedCase && !java.awt.GraphicsEnvironment.isHeadless())
                    throw new AssertionError("Initial headless state was not cached");
                System.setProperty("java.awt.headless", "false");
                boolean headless = java.awt.GraphicsEnvironment.isHeadless();
                if (headless != cachedCase)
                    throw new AssertionError("Headless cache changed or early property was ignored");
                System.out.println("graphicsEnvironmentHeadless=" + headless);
            }
        }
        """
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / 'HudHeadlessTiming.java'
            path.write_text(source)
            for mode, result in [('before', 'false'), ('cached', 'true')]:
                with self.subTest(mode=mode):
                    completed = subprocess.run([shutil.which('java'), str(path), mode],
                                               text=True, capture_output=True, timeout=20)
                    self.assertEqual(completed.returncode, 0, completed.stderr)
                    self.assertIn('graphicsEnvironmentHeadless=' + result, completed.stdout)

    def test_native_font_checks_complete_civilian_guidance_before_capture(self):
        source = HARNESS.read_text()
        self.assertIn('currentPage() == CoreCommandPage.CIVILIANS', source)
        self.assertIn('getDeclaredMethod("civilianGuidance", boolean.class)', source)
        self.assertIn('mc.font.split(Component.literal(guidance), layout.width() - 40)', source)
        self.assertIn('require(lineCount <= lineBudget', source)
        self.assertIn('view.put("civilianGuidanceLineBudget", lineBudget)', source)

    def test_workflow_read_only_and_bounded_online_then_offline(self):
        workflow = (REPO / '.github/workflows/native-hud-qa.yml').read_text()
        for text in ['contents: read', 'persist-credentials: false', 'cache-read-only: true',
                     "'native-hud-qa'", 'timeout-minutes: 35', 'set -euo pipefail',
                     '--mode hud --smallships-version 2.0.0-b1.4', 'python3 scripts/verify-native-hud.py',
                     'if-no-files-found: error', 'path: build/native-hud-qa/evidence/']:
            self.assertIn(text, workflow)
        commands = [line for line in workflow.splitlines() if './gradlew ' in line]
        self.assertEqual(len(commands), 2)
        self.assertIn('20m', commands[0]); self.assertIn('prepareNativeQaClient', commands[0])
        self.assertNotIn('--offline', commands[0])
        self.assertIn('10m', commands[1]); self.assertIn('--offline', commands[1])
        self.assertIn('runClient', commands[1])
        for command in commands:
            self.assertIn('-PnativeQa=true -PnativeQaMode=hud', command)
            self.assertNotRegex(command, r'--exclude-task|--continue|\s-x\s')
        self.assertNotIn('secrets.', workflow)
        self.assertNotIn('contents: write', workflow)

    def test_exact_named_matrix_has_all_pages_plans_states_and_native_inspection(self):
        expected = verify.expected_screenshots()
        self.assertEqual(len(expected), 106)
        for prefix in verify.MATRICES:
            for page in verify.PAGES:
                self.assertIn(f'{prefix}-{page}.png', expected)
            for plan in verify.PLANS:
                self.assertIn(f'{prefix}-plan-{plan}.png', expected)
        for prefix in verify.MATRICES[:2]:
            for state in verify.STATES:
                self.assertIn(f'{prefix}-{state}.png', expected)
        self.assertIn('roomy-scale1-native-inspection.png', expected)


class NativeHudReceiptVerifier(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.pngs = {size: synthetic_png(*size) for size in [(960, 720), (1440, 960)]}

    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        required = ['Native OS E key', 'Native Ctrl+Tab', 'Native Ctrl+Shift+Tab', 'Native Escape',
                    'Actual 1440x960 to 960x720', 'Hired, unavailable, insufficient', 'without a purchase',
                    'Codex Journal', 'focus across sync', 'Settings empty-filter']
        self.data = {'mode': 'hud', 'status': 'passed', 'completedSteps': 200, 'plannedSteps': 200,
                     'assertions': required + [f'synthetic assertion {i}' for i in range(20)],
                     'loadedModVersions': {'minecraft': '1.20.1', 'forge': '47.4.16', 'workers': '2.0.3',
                                           'recruits': '1.15.2', 'smallships': '2.0.0-b1.4', 'siegeweapons': '0.2.5'},
                     'loadedCompanionArtifacts': {mod: {'sha256': 'a' * 64, 'kind': 'remapped synthetic test metadata'}
                                                   for mod in ['workers', 'recruits', 'smallships', 'siegeweapons']},
                     'notCovered': ['synthetic parser fixture, no Minecraft execution'], 'coverage': 'synthetic client-menu receipt',
                     'screenshots': sorted(verify.expected_screenshots()), 'views': []}
        for name in self.data['screenshots']:
            size = (1440, 960) if name.startswith('roomy-') else (960, 720)
            scale = 1 if name.startswith('roomy-scale1') else 3 if 'scale3' in name or name.startswith('resized-') else 2
            view = {'screenshot': name, 'fixture': 'Synthetic parser fixture only', 'nonblankSamples': 51,
                    'viewport': {'framebufferWidth': size[0], 'framebufferHeight': size[1], 'guiWidth': size[0] // scale,
                                 'guiHeight': size[1] // scale, 'requestedGuiScale': scale},
                    'widgets': [{'label': 'Synthetic button', 'x': 1, 'y': 1, 'width': 10, 'height': 10, 'active': True, 'focused': True}]}
            if name.endswith('-army.png'): view['nativePortraits'] = ['com.talhanation.synthetic.Test'] * 4
            if '-plan-' in name:
                view['selectedPlan'] = name.removesuffix('.png').split('-plan-')[1].upper()
                view['nativeBlueprintCards'] = [{'plan': view['selectedPlan'], 'sourceBlueprintMatches': True,
                                                'blockModels': 15, 'caption': 'Synthetic plan'}]
            if 'native-inspection' in name: view['nativeStructurePreviewCount'] = 1
            self.data['views'].append(view)
            (self.root / name).write_bytes(self.pngs[size])
        (self.root / 'source-commit.txt').write_text('a' * 40 + '\n')

    def run_verifier(self):
        (self.root / 'result.json').write_text(json.dumps(self.data))
        with contextlib.redirect_stdout(io.StringIO()):
            return verify.verify(self.root)

    def test_complete_synthetic_receipt_parses_without_claiming_runtime(self):
        self.assertEqual(self.run_verifier()['status'], 'passed')

    def test_missing_screenshot_fails(self):
        (self.root / self.data['screenshots'][0]).unlink()
        with self.assertRaises(FileNotFoundError): self.run_verifier()

    def test_missing_named_case_fails(self):
        self.data['screenshots'].pop()
        with self.assertRaises(AssertionError): self.run_verifier()

    def test_incomplete_steps_fail(self):
        self.data['completedSteps'] -= 1
        with self.assertRaises(AssertionError): self.run_verifier()

    def test_wrong_native_version_fails(self):
        self.data['loadedModVersions']['workers'] = 'unknown'
        with self.assertRaises(AssertionError): self.run_verifier()

    def test_outside_hitbox_fails(self):
        self.data['views'][0]['widgets'][0]['x'] = -1
        with self.assertRaises(AssertionError): self.run_verifier()

    def test_overlapping_hitboxes_fail(self):
        self.data['views'][0]['widgets'].append(dict(self.data['views'][0]['widgets'][0]))
        with self.assertRaises(AssertionError): self.run_verifier()

    def test_disabled_focus_fails(self):
        self.data['views'][0]['widgets'][0]['active'] = False
        with self.assertRaises(AssertionError): self.run_verifier()

    def test_portrait_fallback_fails(self):
        army = next(view for view in self.data['views'] if view['screenshot'].endswith('-army.png'))
        army['nativePortraits'].pop()
        with self.assertRaises(AssertionError): self.run_verifier()

    def test_wrong_blueprint_models_fail(self):
        plan = next(view for view in self.data['views'] if '-plan-' in view['screenshot'])
        plan['nativeBlueprintCards'][0]['sourceBlueprintMatches'] = False
        with self.assertRaises(AssertionError): self.run_verifier()

    def test_missing_native_preview_fails(self):
        native = next(view for view in self.data['views'] if 'native-inspection' in view['screenshot'])
        native['nativeStructurePreviewCount'] = 0
        with self.assertRaises(AssertionError): self.run_verifier()

    def test_png_viewport_mismatch_fails(self):
        self.data['views'][0]['viewport']['framebufferWidth'] = 1
        with self.assertRaises(AssertionError): self.run_verifier()


if __name__ == '__main__':
    unittest.main()
