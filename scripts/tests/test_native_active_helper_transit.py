"""Source-contract checks only: no Java type resolution, game launch or native behavioral proof."""
from pathlib import Path
import re
import unittest


ROOT = Path(__file__).resolve().parents[2]
SOURCE = ROOT / "src/nativeQa/java/com/devfarinsky/siegeoverhaul/nativecompat/NativeActiveHelperTransit.java"


def method(source, signature):
    start = source.index(signature)
    opening = source.index("{", start)
    depth = 1
    at = opening + 1
    while depth:
        depth += (source[at] == "{") - (source[at] == "}")
        at += 1
    return source[opening + 1:at - 1]


class NativeActiveHelperTransitContract(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.source = SOURCE.read_text()

    def test_only_setup_writes_world_or_acquires_chunks(self):
        setup = method(self.source, "void setup(ServerLevel overworld)")
        self.assertEqual(self.source.count(".setBlock("), 1)
        self.assertEqual(self.source.count(".getChunk("), 1)
        self.assertIn(".setBlock(", setup)
        self.assertIn(".getChunk(", setup)
        self.assertIn("!setup", setup)
        self.assertIn("before commission", setup)
        for forbidden in ("setChunkForced(", "addRegionTicket(", "removeRegionTicket(",
                          "setForcedChunkLoadingCallback(", "new TicketType", "setNoAi(",
                          "setDeltaMovement(", ".moveTo(", ".setItem(", "setItemInHand(",
                          "EntityType.load", "saveWithoutId(", "readAdditionalSaveData(",
                          "ProtectedBuilderHandMirror.arm(", "NativeConstructionGuard.areaJoined("):
            self.assertNotIn(forbidden, self.source)

    def test_ticket_contract_is_exact_owned_ticking_tuple(self):
        calls = re.findall(r"ForgeChunkManager\.forceChunk\((.*?)\);", self.source, re.S)
        self.assertEqual(len(calls), 2)
        common = "holdingLevel(server), SiegeOverhaul.MOD_ID, ticketOwner, HOLDING_CHUNK_X, HOLDING_CHUNK_Z, "
        self.assertEqual([" ".join(call.split()) for call in calls],
                         [common + "true, true", common + "false, true"])
        add = method(self.source, "void ensureHoldingTicket(MinecraftServer server)")
        self.assertLess(add.index("requireOwnedCount(before, 0)"), add.index("ticketBefore = before"))
        self.assertLess(add.index('ticketEvent("add-pending"'), add.index("ForgeChunkManager.forceChunk"))
        self.assertIn("require(result", add)
        self.assertIn("poisoned = true", add)
        self.assertIn("cleanPending(server)", add)
        remove = method(self.source, "private void cleanPending(MinecraftServer server)")
        self.assertIn("ticketBefore != null", remove)
        self.assertIn("currentUnrelated = unrelated(before)", remove)
        self.assertIn("after.equals(currentUnrelated)", remove)
        self.assertIn("require(result", remove)
        self.assertIn("require(!held", remove)

    def test_diverged_census_cannot_strand_owned_ticket_or_recover_green(self):
        remove = method(self.source, "private void cleanPending(MinecraftServer server)")
        divergence = method(remove, "if (baselineDiverged)")
        self.assertIn("!currentUnrelated.equals(ticketBefore)", remove)
        self.assertIn("poisoned = true", divergence)
        self.assertIn('ticketFailure("cleanup-original-census-diverged"', divergence)
        for early_exit in ("throw ", "require(", "return;"):
            self.assertNotIn(early_exit, divergence)
        self.assertNotIn("require(unrelated(before).equals(ticketBefore)", remove)
        self.assertNotIn("after.equals(ticketBefore)", remove)
        self.assertLess(remove.index("requireOwnedCount(before, 1)"), remove.index("ForgeChunkManager.forceChunk"))
        self.assertLess(remove.index("after.equals(currentUnrelated)"), remove.index("held = false; ticketBefore = null;"))
        cleared = remove.index("held = false; ticketBefore = null;")
        self.assertGreater(remove.rindex("require(!baselineDiverged"), cleared)
        self.assertIn("!poisoned", method(self.source, "void assertNoTickets(MinecraftServer server)"))
        self.assertNotIn("poisoned = false", self.source)

    def test_negative_old_census_restoration_is_rejected(self):
        # Mutation regression for the exact original failure mode. This remains a
        # source contract, not a substitute for executing Forge's native ticket API.
        remove = method(self.source, "private void cleanPending(MinecraftServer server)")

        def preserves_current_census_and_defers_divergence(body):
            divergence = method(body, "if (baselineDiverged)")
            return ("after.equals(currentUnrelated)" in body
                    and "after.equals(ticketBefore)" not in body
                    and "require(unrelated(before).equals(ticketBefore)" not in body
                    and all(token not in divergence for token in ("throw ", "require(", "return;"))
                    and body.rindex("require(!baselineDiverged") > body.index("held = false; ticketBefore = null;"))

        self.assertTrue(preserves_current_census_and_defers_divergence(remove))
        restore_old = remove.replace("after.equals(currentUnrelated)", "after.equals(ticketBefore)")
        strand_owned = remove.replace("if (baselineDiverged) {",
                                     'if (baselineDiverged) { require(false, "early baseline failure");')
        self.assertFalse(preserves_current_census_and_defers_divergence(restore_old))
        self.assertFalse(preserves_current_census_and_defers_divergence(strand_owned))

    def test_saved_ticket_census_is_all_levels_and_fail_closed(self):
        census = method(self.source, "private Map<String, Tag> census(MinecraftServer server)")
        self.assertIn("server.getAllLevels()", census)
        self.assertIn("saved.save(new CompoundTag())", census)
        self.assertIn("ForcedChunksSavedData::load", census)
        self.assertNotIn("computeIfAbsent", census)
        for name in ("ForgeForced", "ModForced", "Chunk", "Blocks", "TickingBlocks", "Entities", "TickingEntities"):
            self.assertIn('"' + name + '"', census)
        self.assertIn("NbtUtils.loadUUID(owner)", census)
        self.assertIn("owner.copy()", census)
        self.assertIn("putIfAbsent", census)
        self.assertIn("ownedCount(values) == count", self.source)
        zero = method(self.source, "void assertNoTickets(MinecraftServer server)")
        self.assertIn("requireOwnedCount(current, 0)", zero)
        self.assertIn("ticketBefore == null && !poisoned", zero)

    def test_single_native_reposition_and_settled_registration(self):
        transfer = method(self.source, "BuilderEntity transfer(BuilderEntity original")
        self.assertEqual(transfer.count("original.changeDimension("), 1)
        self.assertEqual(transfer.count("nativeReposition.apply(false)"), 1)
        self.assertNotIn("unusedVanillaPortalInfo.apply", transfer)
        self.assertIn("new PortalInfo(landing, Vec3.ZERO, yaw, pitch)", transfer)
        self.assertIn("assertSettled(original, returned)", transfer)
        self.assertIn("settled.equals(observation.low)", transfer)
        self.assertIn("finally { active = null; }", transfer)
        settled = method(self.source, "void assertSettled(BuilderEntity original")
        self.assertIn("Entity.RemovalReason.CHANGED_DIMENSION", settled)
        self.assertIn("!reason.shouldDestroy() && !reason.shouldSave()", settled)
        self.assertIn("level.getEntity(helper) == destination", settled)
        unique = method(self.source, "static void assertUnique(MinecraftServer server")
        self.assertIn("server.getAllLevels()", unique)
        self.assertIn("level.getAllEntities()", unique)
        self.assertIn("count == 1", unique)

    def test_join_observers_read_only_local_values(self):
        for signature in ("void onJoin(EntityJoinLevelEvent event)", "void onJoined(EntityJoinLevelEvent event)"):
            body = method(self.source, signature)
            for forbidden in ("getBlockState", "getBlockEntity", "getChunk", "noCollision", "assertUnique",
                              "ready(", "verifySetup", "changeDimension", "getGameTime", "needsToSleep"):
                self.assertNotIn(forbidden, body)
        high = method(self.source, "void onJoin(EntityJoinLevelEvent event)")
        low = method(self.source, "void onJoined(EntityJoinLevelEvent event)")
        self.assertIn("value.equals(active.before)", high)
        self.assertIn("CompoundTag expected = active.raw.persistent()", low)
        self.assertIn("expected.putBoolean(HAND_PENDING, true)", low)
        self.assertIn("value.equals(armed)", low)
        leave = method(self.source, "void onLeave(EntityLeaveLevelEvent event)")
        self.assertIn("worker == active.original", leave)
        self.assertIn('"sourceInventoryResampled", false', leave)

    def test_readiness_and_natural_eligibility(self):
        ready = method(self.source, "private static boolean ready(ServerLevel level")
        self.assertIn("fullChunk(level, feet)", ready)
        self.assertIn("level.areEntitiesLoaded", ready)
        self.assertIn("level.isPositionEntityTicking", ready)
        self.assertIn("getChunkNow", method(self.source, "private static boolean fullChunk(ServerLevel level"))
        eligibility = method(self.source, "static List<String> eligibility(BuilderEntity worker)")
        for required in ("worker.isSleeping()", "getBeforeItemSlot() != -1", "neededItems.isEmpty()",
                         "ProtectedBuilderHandMirror.pending", "ProtectedBuilderHandMirror.reviewNeeded",
                         "ProtectedInventoryCleanup.outstanding", "ProtectedStorageAccess.runningProblem",
                         "worker.isPassenger()", "worker.isLeashed()", "worker.getTarget()",
                         "worker.onGround()", "worker.fallDistance != 0", "noInheritedChunk(worker)"):
            self.assertIn(required, eligibility)
        self.assertNotIn("needsToSleep", eligibility)
        self.assertNotIn("Vec3.ZERO", eligibility)
        inherited = method(self.source, "private static boolean noInheritedChunk(BuilderEntity worker)")
        self.assertIn('getDeclaredField("loadedChunk")', inherited)
        self.assertNotIn("field.set(", inherited)

    def test_inventory_values_are_deep_copied(self):
        for accessor in ("slots() { return copySlots(slots); }", "main() { return main.copy(); }",
                         "off() { return off.copy(); }", "persistent() { return persistent.copy(); }"):
            self.assertIn(accessor, self.source)
        inventory = method(self.source, "static Inventory inventory(BuilderEntity worker)")
        self.assertIn("i < container.getContainerSize()", inventory)
        self.assertIn("EquipmentSlot.values()", inventory)
        self.assertIn("worker.getItemBySlot(equipment)", inventory)
        self.assertIn("worker.getBeforeItemSlot()", inventory)


if __name__ == "__main__":
    unittest.main()
