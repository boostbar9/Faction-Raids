"""Source/fixture contract checks. Not a substitute for the actual Minecraft run."""
from pathlib import Path
import re
import unittest

ROOT = Path(__file__).resolve().parents[2]
QA = ROOT / 'src/nativeQa/java/com/devfarinsky/siegeoverhaul/nativecompat/NativeCampLifecycleQa.java'
FIXTURE = QA.with_name('NativeCampLifecycleFixture.java')


class NativeCampLifecycleContractTest(unittest.TestCase):
    def test_registered_isolated_mode_does_not_start_baseline_harness(self):
        self.assertIn("'camp-lifecycle': 'build/native-camp-lifecycle-qa/client'", (ROOT / 'build.gradle').read_text())
        self.assertIn('"camp-lifecycle"', QA.with_name('NativeBuildingQa.java').read_text())
        self.assertIn('"camp-lifecycle".equals', QA.read_text())
        self.assertIn('"native-camp-lifecycle-qa", "client"', QA.read_text())

    def test_actual_command_and_world_reload_without_raid_writes(self):
        source = QA.read_text()
        self.assertIn('sendCommand("siegeoverhaul start")', source)
        self.assertIn('mc.level.disconnect(); mc.clearLevel();', source)
        self.assertIn('createWorldOpenFlows().loadLevel', source)
        self.assertIn('ServerStoppingEvent', source)
        self.assertIn('ServerStartedEvent', source)
        self.assertIn('loadedAuthority.equals(stoppedAuthority)', source)
        for text in [source, FIXTURE.read_text()]:
            self.assertIsNone(re.search(r'\braid\.\w+\s*(?:=(?!=)|\+=|-=|\+\+|--)', text))
            for forbidden in ['RaidState.load(', 'CampScouting.advance(', 'CampScouting.selectCandidate(',
                              'setGameTime(', 'processRaid(', 'buildWarCamp(', 'setAccessible(']:
                self.assertNotIn(forbidden, text)

    def test_deep_water_and_recovery_only_finite_fully_supported_island(self):
        source = FIXTURE.read_text()
        for expected in ['WATER_DEPTH = 24', 'ISLAND_HALF = 28', 'WIDTH * WIDTH * WATER_DEPTH',
                         'cursor + 4096', 'CampLoading.recoveryCandidate', 'CampLoading.candidate',
                         'CampLoading.localCandidate', 'ISLAND_HALF - 9', 'Blocks.WATER',
                         'addBoundarySpike', 'expectedCamp(island).offset(16, 0, 0)']:
            self.assertIn(expected, source)
        self.assertIn('islandCompletedAt < firstRecoveryAt', QA.read_text())
        self.assertIn('lastIslandTick == level.getGameTime()', QA.read_text())

    def test_actual_native_wave_and_protection_checks_required(self):
        source = QA.read_text()
        for expected in ['raid.wave > 0 && raid.totalSpawned > 0', 'cooldownObservedTicks >= 1200', 'raid.preparationTicks == raid.preparationTotalTicks',
                         'raid.campSearchDiagnostics.exhausted()', 'CampClaims.owns(level, raid)',
                         'WarGate.ready(level, raid)', 'NativeCampFixture.verifyProtected(level, fixture)',
                         'raid.campaign.getBoolean("CampGradePreservedEdges")',
                         'raid.campPos.equals(NativeCampLifecycleFixture.expectedCamp(islandCenter))',
                         '!raid.campBlocks.containsKey(pos.asLong())', 'EventPriority.LOWEST']:
            self.assertIn(expected, source)
        workflow = (ROOT / '.github/workflows/native-camp-lifecycle-qa.yml').read_text()
        self.assertIn('siegePreparationMinutes = 3', workflow)
        self.assertIn('timeout-minutes: 50', workflow)
        self.assertIn('45m ./gradlew', workflow)
        self.assertIn('verify-native-camp-lifecycle-evidence.py', workflow)


if __name__ == '__main__':
    unittest.main()
