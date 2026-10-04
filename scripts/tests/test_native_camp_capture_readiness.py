"""Source-contract checks for the opt-in Camp observer, not native rendering evidence."""
from pathlib import Path
import re
import unittest


REPO = Path(__file__).resolve().parents[2]
SOURCE = (REPO / 'src/nativeQa/java/com/devfarinsky/siegeoverhaul/nativecompat/NativeCampQa.java').read_text()
RENDER = SOURCE.split('public static void render(', 1)[1].split('private static boolean captureTerrainCompiled(', 1)[0]
TICK = SOURCE.split('public static void tick(', 1)[1].split('private static Action step(', 1)[0]


class NativeCampCaptureReadinessTest(unittest.TestCase):
    def test_capture_deadline_is_finite_and_never_restarted_by_readiness_or_blank_retry(self):
        self.assertIn('CAPTURE_TIMEOUT = 30 * SECOND;', SOURCE)
        self.assertEqual(len(re.findall(r'captureStarted\s*=', SOURCE)), 1)
        self.assertIn('captureStarted = System.nanoTime();', TICK)
        for observer in [TICK, RENDER]:
            self.assertIn('System.nanoTime() - captureStarted < CAPTURE_TIMEOUT', observer)
        self.assertLess(RENDER.index('System.nanoTime() - captureStarted < CAPTURE_TIMEOUT'),
                        RENDER.index('if (!(observerReady'))

    def test_readback_and_write_recheck_original_deadline_before_acceptance(self):
        sampling_end = RENDER.index('captureDiagnostics.put("changedPixelSamples", changed)')
        write = RENDER.index('image.writeToFile')
        accept = RENDER.index('SHOTS.add(capture)')
        for section in [RENDER[sampling_end:write], RENDER[write:accept]]:
            self.assertIn('captureElapsed = System.nanoTime() - captureStarted;', section)
            self.assertIn('captureDiagnostics.put("elapsedSeconds", captureElapsed / (double)SECOND);', section)
            self.assertIn('require(captureElapsed < CAPTURE_TIMEOUT,', section)
            self.assertLess(section.index('captureElapsed = System.nanoTime() - captureStarted;'),
                            section.index('require(captureElapsed < CAPTURE_TIMEOUT,'))
        self.assertLess(accept, RENDER.index('put("framebufferCapture"'))
        self.assertLess(accept, RENDER.index('capture = null;'))

    def test_real_client_and_camera_readiness_precede_framebuffer_sampling(self):
        self.assertIn('mc.player.position().distanceToSqr(captureObserver) < 0.01', RENDER)
        self.assertIn('mc.player.isSpectator()', RENDER)
        self.assertIn('mc.level.getBlockState(captureCore).is(CoreBlocks.CORE.get())', RENDER)
        self.assertIn('camera.getPosition().distanceToSqr(mc.player.getEyePosition()) < 0.01', RENDER)
        self.assertIn('mc.getCameraEntity() == mc.player', RENDER)
        self.assertIn('observerReady && coreReceived && terrainCompiled && cameraReady', RENDER)
        self.assertLess(RENDER.index('if (++captureReadyFrames < 4) return;'),
                        RENDER.index('Screenshot.takeScreenshot'))
        self.assertIn('captureReadyFrames = 0;', RENDER)

    def test_terrain_wait_only_demands_the_nonempty_camp_view_target(self):
        terrain = SOURCE.split('private static boolean captureTerrainCompiled(', 1)[1].split('private static void initialize(', 1)[0]
        self.assertIn('mc.levelRenderer.isChunkCompiled(captureCore)', terrain)
        self.assertIn('BlockPos surface = captureCenter.below();', terrain)
        self.assertIn('mc.level.hasChunkAt(surface) && !mc.level.getBlockState(surface).isAir()', terrain)
        self.assertIn('mc.levelRenderer.isChunkCompiled(surface)', terrain)
        self.assertNotIn('for (', terrain)

    def test_blank_frames_cannot_be_written_or_counted_as_passed(self):
        self.assertIn('image.getWidth() >= 640 && image.getHeight() >= 360', RENDER)
        self.assertIn('y += 16', RENDER)
        self.assertIn('x += 16', RENDER)
        self.assertIn('if (image.getPixelRGBA(x, y) != first) changed++;', RENDER)
        blank = RENDER.split('if (changed <= 50) {', 1)[1].split('}', 1)[0]
        self.assertIn('++captureBlankFrames', blank)
        self.assertIn('return;', blank)
        self.assertNotIn('writeToFile', blank)
        threshold = RENDER.index('require(changed > 50, "Camp framebuffer appears blank")')
        self.assertLess(threshold, RENDER.index('image.writeToFile'))
        self.assertLess(RENDER.index('image.writeToFile'), RENDER.index('SHOTS.add(capture)'))

    def test_capture_readiness_has_no_world_or_unit_mutation(self):
        for forbidden in ['setBlock(', 'teleportTo(', 'setPos(', 'setNoAi(', 'setGameTime(', 'setDayTime(', '.tick(']:
            self.assertNotIn(forbidden, RENDER)
        aim = TICK.split('if (capture != null) {', 1)[1].split('if (clientPhase == 0)', 1)[0]
        self.assertLess(aim.index('distanceToSqr(captureObserver)'), aim.index('aim(mc,'))

    def test_pass_and_timeout_preserve_capture_diagnostics(self):
        for key in ['worldReady', 'observerReady', 'coreReceived', 'terrainCompiled', 'cameraReady',
                    'expectedObserver', 'clientPosition', 'cameraPosition', 'campCore', 'framebufferAttempts',
                    'blankFrames', 'changedPixelSamples']:
            self.assertIn('captureDiagnostics.put("' + key + '"', RENDER)
        self.assertIn('put("framebufferCapture", new LinkedHashMap<>(captureDiagnostics))', RENDER)
        self.assertIn('if (capture != null) REPORT.put("captureDiagnostics", captureDiagnostics)', SOURCE)
        self.assertIn('failure-native-camp.png', SOURCE)


if __name__ == '__main__':
    unittest.main()
