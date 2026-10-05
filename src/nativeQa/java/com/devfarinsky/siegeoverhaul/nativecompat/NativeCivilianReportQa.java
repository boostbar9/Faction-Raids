package com.devfarinsky.siegeoverhaul.nativecompat;

import com.devfarinsky.siegeoverhaul.RaidSavedData;
import com.devfarinsky.siegeoverhaul.client.CoreHireScreen;
import com.devfarinsky.siegeoverhaul.core.*;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.*;

/** Real report transport/lifecycle using existing starter villagers, never a client display sample. */
final class NativeCivilianReportQa {
    private static CivilianReport.Snapshot expected, firstReceived;
    private static CompoundTag ledgerBefore;
    private static ListTag inventoryBefore;
    private static long bankBefore, taxesBefore, lastTaxesBefore, deadline;
    private static BlockPos corePos;
    private static CoreHireMenu priorMenu;
    private static int stage, firstMenuId, reopenedMenuId;
    private static final Map<String, Object> EVIDENCE = new LinkedHashMap<>();
    private NativeCivilianReportQa() {}

    /** Server thread. Reuses production core-placement residents without spawning, hiring or changing AI. */
    static void begin(ServerPlayer owner, BlockPos core) {
        require(owner.containerMenu instanceof CoreHireMenu && owner.containerMenu.stillValid(owner), "Civilian acceptance needs a real valid core menu");
        corePos = core.immutable();
        var data = RaidSavedData.get(owner.server); String key = SiegeCore.key(owner);
        var ledger = data.civilianFactions.get(key);
        require(ledger != null && !ledger.getCompound("Residents").isEmpty(), "Existing production starter residents are missing");
        expected = CoreCivilians.snapshot(owner);
        require(expected.residents().size() == ledger.getCompound("Residents").size() && expected.loaded() > 0,
                "Existing starter residents must be loaded for real entity parity");
        require(expected.taxEligible(), "Live fixture core should be tax eligible");
        var rows = new ArrayList<Map<String, Object>>();
        for (var resident : expected.residents()) {
            require(ledger.getCompound("Residents").contains(resident.id().toString()), "Report includes an unregistered resident");
            var entity = owner.server.overworld().getEntity(resident.id());
            require(entity instanceof Villager && entity.isAlive(), "Real owned resident entity missing");
            var villager = (Villager) entity; var nativeData = villager.getVillagerData(); var tag = villager.getPersistentData();
            require(key.equals(tag.getString("SiegeCivilianFaction")) && resident.loaded()
                            && !resident.name().isBlank() && resident.name().equals(tag.getString("SiegeCivilianName"))
                            && resident.profession().equals(BuiltInRegistries.VILLAGER_PROFESSION.getKey(nativeData.getProfession()))
                            && resident.type().equals(BuiltInRegistries.VILLAGER_TYPE.getKey(nativeData.getType()))
                            && resident.level() == nativeData.getLevel() && resident.baby() == villager.isBaby()
                            && resident.bed() == villager.getBrain().getMemory(MemoryModuleType.HOME).isPresent()
                            && resident.workstation() == villager.getBrain().getMemory(MemoryModuleType.JOB_SITE).isPresent()
                            && resident.paused() == ledger.getCompound("Paused").getBoolean(resident.id().toString()),
                    "Server civilian report differs from actual owned villager identity or state");
            rows.add(Map.of("uuid", resident.id().toString(), "name", resident.name(),
                    "profession", resident.profession().toString(), "type", resident.type().toString(),
                    "level", resident.level(), "status", resident.status(), "nativeAiPaused", villager.isNoAi()));
        }
        ledgerBefore = ledger.copy(); inventoryBefore = owner.getInventory().save(new ListTag());
        var savedCore = data.siegeCores.get(key);
        bankBefore = FactionBank.balance(savedCore); taxesBefore = savedCore.getLong("CivilianTaxesTotal");
        lastTaxesBefore = savedCore.getLong("CivilianTaxesLast");
        deadline = System.nanoTime() + 40_000_000_000L;
        EVIDENCE.put("serverRows", rows); EVIDENCE.put("serverEntityParity", true);
        EVIDENCE.put("scope", "Real production starter villagers in the existing claimed-core Survival fixture. Startup NPC AI was already paused by the fixture; no new villagers or care/tax state is created for this check.");
        EVIDENCE.put("notCovered", "Native villager AI, breeding, bed/workstation acquisition, unloaded residents, tax accrual, dedicated servers and hostile clients.");
    }

    /** Client thread. Every tab and reopen uses the actual screen hitboxes and normal core-use packet. */
    static boolean tick(Minecraft mc) {
        require(System.nanoTime() < deadline, "Live civilian report lifecycle exceeded 40 seconds");
        require(mc.player != null && mc.gameMode != null && mc.getConnection() != null, "Live report lost its real client connection");
        switch (stage) {
            case 0 -> {
                var menu = menu(mc); firstMenuId = menu.containerId;
                select(mc, "Civilians"); require(menu.civilianReport() == null, "Civilians reused a report before its watch response");
                stage++;
            }
            case 1 -> {
                var menu = menu(mc); if (menu.civilianReport() == null) return false;
                parity(menu); firstReceived = menu.civilianReport();
                EVIDENCE.put("productionWatchAndSnapshot", true);
                NativeBuildingQa.captureGameplay("19-live-core-civilians.png"); stage++;
            }
            case 2 -> {
                priorMenu = menu(mc); select(mc, "Building");
                require(priorMenu.civilianReport() == null, "Leaving Civilians retained resident names/status");
                EVIDENCE.put("leaveClearsReport", true); stage++;
            }
            case 3 -> {
                select(mc, "Civilians"); require(menu(mc).civilianReport() == null, "Returning reused stale resident rows"); stage++;
            }
            case 4 -> {
                var menu = menu(mc); if (menu.civilianReport() == null) return false;
                parity(menu); require(menu.civilianReport() != firstReceived, "Return did not receive a fresh decoded report");
                EVIDENCE.put("returnReceivesFreshReport", true);
                mc.player.closeContainer(); require(priorMenu.civilianReport() == null, "Closing retained the old report"); stage++;
            }
            case 5 -> {
                if (mc.screen != null || mc.player.containerMenu instanceof CoreHireMenu) return false;
                mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND,
                        new BlockHitResult(Vec3.atCenterOf(corePos).add(0, .5, 0), Direction.UP, corePos, false));
                stage++;
            }
            case 6 -> {
                if (!(mc.screen instanceof CoreHireScreen) || !(mc.player.containerMenu instanceof CoreHireMenu)) return false;
                var menu = menu(mc); reopenedMenuId = menu.containerId;
                require(menu != priorMenu && reopenedMenuId != firstMenuId && menu.civilianReport() == null,
                        "Actual core reopen reused an old menu/report");
                select(mc, "Civilians"); require(menu.civilianReport() == null, "Reopened report was populated before its watch response"); stage++;
            }
            case 7 -> {
                var menu = menu(mc); if (menu.civilianReport() == null) return false;
                parity(menu); require(menu.civilianReport() != firstReceived, "Reopen reused old decoded rows");
                EVIDENCE.put("reopenReceivesFreshReport", true);
                NativeBuildingQa.captureGameplay("20-live-core-civilians-reopened.png"); stage++;
            }
            case 8 -> { mc.player.closeContainer(); stage++; return true; }
            default -> { return true; }
        }
        return false;
    }

    /** Server thread after the final real Close packet. Exact state comparisons catch accidental actions. */
    static Map<String, Object> finish(ServerPlayer owner) {
        require(stage == 9 && !(owner.containerMenu instanceof CoreHireMenu), "Server did not receive final menu close");
        var data = RaidSavedData.get(owner.server); String key = SiegeCore.key(owner); var core = data.siegeCores.get(key);
        require(ledgerBefore.equals(data.civilianFactions.get(key)), "Read-only report changed resident ledger/tax clocks");
        require(inventoryBefore.equals(owner.getInventory().save(new ListTag())), "Read-only report changed personal inventory");
        require(FactionBank.balance(core) == bankBefore && core.getLong("CivilianTaxesTotal") == taxesBefore
                        && core.getLong("CivilianTaxesLast") == lastTaxesBefore,
                "Read-only report changed Treasury or collected taxes");
        require(expected.equals(CoreCivilians.snapshot(owner)), "Read-only report lifecycle changed resident observations");
        EVIDENCE.put("ledgerUnchanged", true); EVIDENCE.put("inventoryUnchanged", true);
        EVIDENCE.put("treasuryAndTaxesUnchanged", true); EVIDENCE.put("firstMenuId", firstMenuId);
        EVIDENCE.put("reopenedMenuId", reopenedMenuId); EVIDENCE.put("residentCount", expected.residents().size());
        EVIDENCE.put("recruitActions", 0); EVIDENCE.put("status", "passed");
        return Map.copyOf(EVIDENCE);
    }
    private static CoreHireMenu menu(Minecraft mc) {
        require(mc.screen instanceof CoreHireScreen && mc.player.containerMenu instanceof CoreHireMenu,
                "Real civilian screen/menu disappeared");
        var menu = (CoreHireMenu) mc.player.containerMenu;
        require(((CoreHireScreen) mc.screen).getMenu() == menu, "Actual client screen uses the wrong menu");
        return menu;
    }
    private static void parity(CoreHireMenu menu) {
        require(expected.equals(menu.civilianReport()), "Production S2C civilian rows differ from independently inspected server villagers");
    }
    private static void select(Minecraft mc, String label) {
        for (int attempts = 0; !NativeBuildingQa.hasVisibleButton(mc, label) && attempts < 7; attempts++)
            NativeBuildingQa.clickVisibleButton(mc, ">");
        NativeBuildingQa.clickVisibleButton(mc, label);
    }
    private static void require(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
