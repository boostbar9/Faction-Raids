package com.devfarinsky.factionraids.rebrand;

import com.devfarinsky.factionraids.FactionRaids;
import com.mojang.logging.LogUtils;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.level.storage.LevelResource;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * v2.36.0 &mdash; one-shot startup probe that inventories every
 * persistence surface currently in use by Faction Raids on this server.
 *
 * <p>Purpose: v3.0.0 will rename the mod id from {@code factionraids}
 * to {@code siegeoverhaul}, which changes the on-disk keys for
 * {@code SavedData}, entity persistent-data tags, and scoreboard team
 * memberships. Before we ship v3.0 with a migration reader, we want
 * ground-truth audit data from real-world worlds &mdash; the developer's
 * dev world and any server admin willing to grep their logs &mdash;
 * confirming the audit at {@code docs/rebrand-audit-v2.36.md} is
 * exhaustive.
 *
 * <p>This probe is READ-ONLY. It never mutates persistence. It logs an
 * INFO summary once per server start, then does nothing further.
 *
 * <p>What it inventories:
 * <ul>
 *   <li>Presence and byte size of {@code data/factionraids_data.dat}
 *       under the world root.</li>
 *   <li>Scoreboard teams whose name starts with {@code fraid_role_},
 *       and their member counts.</li>
 *   <li>Legacy config file presence under both {@code serverconfig/}
 *       and the global config dir.</li>
 * </ul>
 *
 * <p>What it deliberately does NOT do:
 * <ul>
 *   <li>Scan entity persistent-data tags &mdash; that would require
 *       iterating every loaded chunk's entity list, which is expensive
 *       on large worlds. Sample-based coverage during raid ticks is a
 *       better probe if we need it, and can be added later.</li>
 *   <li>Touch Recruits' faction manager (the
 *       {@code factionraids_raiders} faction id is deliberately kept
 *       across the rename to avoid stranding faction relationships).</li>
 * </ul>
 */
public final class RebrandProbe {

    private static final Logger LOG = LogUtils.getLogger();

    /** Prevents double-emission if a mod fires ServerStartedEvent twice. */
    private static boolean emitted = false;

    private RebrandProbe() {}

    public static void run(MinecraftServer server) {
        if (emitted) return;
        emitted = true;

        // ---- Deprecation banner ---------------------------------------
        // One-time, single line. Server admins skim logs; a wall of text
        // gets ignored. Anyone who wants detail can read the linked doc.
        LOG.info("[Faction Raids] Heads up: v3.0.0 will rename this mod to "
                + "'The Siege Overhaul'. Your saves and config will migrate "
                + "automatically on the v3.0 upgrade. See release notes for details.");

        // ---- Persistence probe ---------------------------------------
        // Bounded work: three cheap checks. If any throws, we swallow
        // (logged) and keep going - a diagnostic must never break startup.
        try {
            List<String> findings = new ArrayList<>();
            probeSavedData(server, findings);
            probeScoreboardTeams(server, findings);
            probeConfigFile(server, findings);

            if (findings.isEmpty()) {
                LOG.info("[Faction Raids] Rebrand probe: no legacy persistence "
                        + "surfaces detected on this server. Clean world.");
            } else {
                LOG.info("[Faction Raids] Rebrand probe: found {} legacy "
                        + "persistence surface(s) that v3.0.0 will migrate:",
                        findings.size());
                for (String f : findings) {
                    LOG.info("[Faction Raids]   - {}", f);
                }
            }
        } catch (Throwable t) {
            LOG.warn("[Faction Raids] Rebrand probe failed (non-fatal): {}",
                    t.getMessage());
        }
    }

    /**
     * Reset for tests and for the ServerStopping event so a subsequent
     * integrated-server restart within the same JVM re-runs the probe.
     */
    public static void reset() {
        emitted = false;
    }

    // ---- Individual probes ------------------------------------------

    /**
     * Look for {@code data/factionraids_data.dat} under the world root.
     * That's the single {@code SavedData} file we currently write.
     */
    private static void probeSavedData(MinecraftServer server, List<String> findings) {
        try {
            Path worldRoot = server.getWorldPath(LevelResource.ROOT);
            Path dataDir = worldRoot.resolve("data");
            if (!Files.isDirectory(dataDir)) return;
            Path legacyDat = dataDir.resolve("factionraids_data.dat");
            if (Files.isRegularFile(legacyDat)) {
                long sz = Files.size(legacyDat);
                findings.add("SavedData: data/factionraids_data.dat (" + sz + " bytes)");
            }
        } catch (IOException e) {
            LOG.warn("[Faction Raids] SavedData probe I/O error: {}", e.getMessage());
        }
    }

    /**
     * Count scoreboard teams matching the current {@code fraid_role_}
     * prefix. The overworld carries the singleton scoreboard.
     */
    private static void probeScoreboardTeams(MinecraftServer server, List<String> findings) {
        Scoreboard sb = server.overworld().getScoreboard();
        int matched = 0;
        int totalMembers = 0;
        for (PlayerTeam t : sb.getPlayerTeams()) {
            if (t.getName().startsWith("fraid_role_")) {
                matched++;
                totalMembers += t.getPlayers().size();
            }
        }
        if (matched > 0) {
            findings.add("Scoreboard: " + matched + " team(s) with fraid_role_ prefix, "
                    + totalMembers + " total member(s)");
        }
    }

    /**
     * Look for the legacy Forge config file in the two well-known
     * locations. The world-scoped serverconfig always wins over the
     * global config dir if both exist &mdash; but for the audit we just
     * report both.
     */
    private static void probeConfigFile(MinecraftServer server, List<String> findings) {
        try {
            Path worldScoped = server.getWorldPath(LevelResource.ROOT)
                    .resolve("serverconfig")
                    .resolve(FactionRaids.MOD_ID + "-common.toml");
            if (Files.isRegularFile(worldScoped)) {
                findings.add("Config (world-scoped): serverconfig/"
                        + worldScoped.getFileName());
            }
            Path globalConfigDir = net.minecraftforge.fml.loading.FMLPaths.CONFIGDIR.get();
            if (globalConfigDir != null) {
                Path global = globalConfigDir.resolve(FactionRaids.MOD_ID + "-common.toml");
                if (Files.isRegularFile(global)) {
                    findings.add("Config (global): " + global.getFileName());
                }
            }
        } catch (Throwable t) {
            LOG.warn("[Faction Raids] Config probe error: {}", t.getMessage());
        }
    }
}
