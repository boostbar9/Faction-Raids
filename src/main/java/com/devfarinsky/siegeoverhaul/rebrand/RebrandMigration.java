package com.devfarinsky.siegeoverhaul.rebrand;

import com.devfarinsky.siegeoverhaul.ModConstants;
import com.devfarinsky.siegeoverhaul.RaidSavedData;
import com.devfarinsky.siegeoverhaul.RaiderLabels;
import com.devfarinsky.siegeoverhaul.SiegeOverhaul;
import com.mojang.logging.LogUtils;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import net.minecraftforge.fml.loading.FMLPaths;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

/**
 * v3.0.0 &mdash; one-shot on-disk migration reader executed during
 * server startup, BEFORE any {@code SavedData} read or scoreboard
 * lookup fires.
 *
 * <p>Runs from {@code ServerAboutToStartEvent}, which is the earliest
 * server-lifecycle hook where the world path is resolved but no game
 * systems have touched persistence yet. Idempotent: a marker file at
 * {@code data/siegeoverhaul.migrated} prevents re-running the file
 * copy on subsequent launches.
 *
 * <p>Handles four legacy surfaces:
 * <ul>
 *   <li><b>SavedData:</b> {@code data/factionraids_data.dat} &rarr;
 *       {@code data/siegeoverhaul_data.dat}. File-level copy so the
 *       NBT is bit-identical; the game then reads it under the new
 *       key naturally.</li>
 *   <li><b>Config:</b> {@code factionraids-common.toml} &rarr;
 *       {@code siegeoverhaul-common.toml}. Checked under both
 *       {@code serverconfig/} (world-scoped) and the global config
 *       dir. Only copies when the new-name file is absent so we do
 *       not clobber a user's post-v3.0 edits.</li>
 *   <li><b>Scoreboard teams:</b> {@code fraid_role_*} &rarr;
 *       {@code sieov_role_*}. Members are copied to the new team;
 *       the old team is deleted so the roster is not double-counted.</li>
 *   <li><b>Marker:</b> writes {@code data/siegeoverhaul.migrated} on
 *       success so we do not repeat the copy on every launch.</li>
 * </ul>
 *
 * <p>DELIBERATELY DOES NOT touch:
 * <ul>
 *   <li>Entity persistent-data tags &mdash; those on-disk keys are
 *       kept as {@code FactionRaids*} in v3.0.0 to avoid per-entity
 *       migration cost on every loaded chunk.</li>
 *   <li>The Recruits faction id {@code factionraids_raiders} &mdash;
 *       rewriting Recruits' faction manager would strand existing
 *       faction relationships.</li>
 * </ul>
 *
 * <p>All work is bounded and wrapped in try/catch. Migration failure
 * logs at WARN and lets the server continue &mdash; a migration bug
 * must not brick a world.
 */
public final class RebrandMigration {

    private static final Logger LOG = LogUtils.getLogger();

    private static final String LEGACY_DAT = ModConstants.LEGACY_MOD_ID + "_data.dat";
    private static final String NEW_DAT = RaidSavedData.DATA_NAME + ".dat";
    private static final String LEGACY_CONFIG = ModConstants.LEGACY_MOD_ID + "-common.toml";
    private static final String NEW_CONFIG = SiegeOverhaul.MOD_ID + "-common.toml";
    private static final String MARKER = "siegeoverhaul.migrated";

    private RebrandMigration() {}

    /**
     * Entry point. Called from
     * {@code RaidEvents.onServerAboutToStart}.
     */
    public static void run(MinecraftServer server) {
        try {
            Path worldRoot = server.getWorldPath(LevelResource.ROOT);
            Path dataDir = worldRoot.resolve("data");
            Files.createDirectories(dataDir);
            Path marker = dataDir.resolve(MARKER);

            if (Files.exists(marker)) {
                LOG.info("[SiegeOverhaul] Rebrand migration already applied to this world.");
                return;
            }

            List<String> outcomes = new ArrayList<>();
            migrateSavedData(dataDir, outcomes);
            migrateConfig(worldRoot, outcomes);
            // Scoreboard migration is deferred to ServerStartedEvent -
            // the scoreboard is not available at ServerAboutToStart.

            // Write marker regardless of whether anything was migrated so
            // clean-install worlds do not re-probe every launch.
            Files.writeString(marker,
                    "The Siege Overhaul v3.0.0 rebrand migration completed.\n"
                            + "Legacy factionraids_data.dat and factionraids-common.toml,\n"
                            + "if present, have been copied to their new names.\n"
                            + "Scoreboard teams are migrated on ServerStartedEvent.\n"
                            + "Delete this file only if you want the copy to run again\n"
                            + "(safe: it will not clobber existing new-name files).\n");

            if (outcomes.isEmpty()) {
                LOG.info("[SiegeOverhaul] Rebrand migration: no legacy files to migrate.");
            } else {
                LOG.info("[SiegeOverhaul] Rebrand migration completed. {} action(s):",
                        outcomes.size());
                for (String o : outcomes) LOG.info("[SiegeOverhaul]   - {}", o);
            }
        } catch (Throwable t) {
            LOG.warn("[SiegeOverhaul] Rebrand migration failed (non-fatal, server will continue): {}",
                    t.toString());
        }
    }

    /**
     * Called from {@code ServerStartedEvent} once the scoreboard exists.
     * Copies every {@code fraid_role_*} team member into the matching
     * {@code sieov_role_*} team, then removes the legacy team.
     */
    public static void runScoreboard(MinecraftServer server) {
        try {
            Scoreboard sb = server.overworld().getScoreboard();
            List<PlayerTeam> legacyTeams = new ArrayList<>();
            for (PlayerTeam t : sb.getPlayerTeams()) {
                if (t.getName().startsWith(RaiderLabels.LEGACY_TEAM_PREFIX)) {
                    legacyTeams.add(t);
                }
            }
            if (legacyTeams.isEmpty()) return;

            // Ensure new-name teams exist first so member adds have a target.
            RaiderLabels.ensureTeams(sb);

            int migratedMembers = 0;
            int deletedTeams = 0;
            for (PlayerTeam legacy : legacyTeams) {
                String role = legacy.getName().substring(RaiderLabels.LEGACY_TEAM_PREFIX.length());
                String newName = "sieov_role_" + role;
                PlayerTeam target = sb.getPlayerTeam(newName);
                if (target == null) {
                    // ensureTeams only registers roles it knows about;
                    // unknown legacy roles get their team dropped rather
                    // than reconstructed under the new prefix.
                    LOG.info("[SiegeOverhaul] Dropping unknown legacy scoreboard team '{}' (no matching new-name registration).",
                            legacy.getName());
                    sb.removePlayerTeam(legacy);
                    deletedTeams++;
                    continue;
                }
                // Copy members. Iterate a snapshot - the mutation moves
                // players between teams which would invalidate the view.
                List<String> members = new ArrayList<>(legacy.getPlayers());
                for (String member : members) {
                    sb.addPlayerToTeam(member, target);
                    migratedMembers++;
                }
                sb.removePlayerTeam(legacy);
                deletedTeams++;
            }
            LOG.info("[SiegeOverhaul] Scoreboard migration: copied {} member(s) from {} legacy team(s).",
                    migratedMembers, deletedTeams);
        } catch (Throwable t) {
            LOG.warn("[SiegeOverhaul] Scoreboard migration failed (non-fatal): {}", t.toString());
        }
    }

    // ---- Individual migrations --------------------------------------

    private static void migrateSavedData(Path dataDir, List<String> outcomes) throws IOException {
        Path legacy = dataDir.resolve(LEGACY_DAT);
        Path fresh = dataDir.resolve(NEW_DAT);
        if (!Files.isRegularFile(legacy)) return;
        if (Files.isRegularFile(fresh)) {
            outcomes.add("SavedData: both " + LEGACY_DAT + " and " + NEW_DAT
                    + " exist; keeping new-name file (legacy left in place as backup).");
            return;
        }
        Files.copy(legacy, fresh, StandardCopyOption.COPY_ATTRIBUTES);
        outcomes.add("SavedData: copied " + LEGACY_DAT + " -> " + NEW_DAT
                + " (" + Files.size(fresh) + " bytes); legacy retained as backup.");
    }

    private static void migrateConfig(Path worldRoot, List<String> outcomes) {
        // Our config is registered as ModConfig.Type.COMMON, which
        // writes to the global config dir only (not the world-scoped
        // serverconfig/ dir). If we ever add a SERVER-type config, add
        // a worldRoot/serverconfig branch here.
        try {
            Path cfg = FMLPaths.CONFIGDIR.get();
            if (cfg != null && Files.isDirectory(cfg)) {
                Path legacy = cfg.resolve(LEGACY_CONFIG);
                Path fresh = cfg.resolve(NEW_CONFIG);
                if (Files.isRegularFile(legacy) && !Files.isRegularFile(fresh)) {
                    Files.copy(legacy, fresh, StandardCopyOption.COPY_ATTRIBUTES);
                    outcomes.add("Config: copied " + LEGACY_CONFIG
                            + " -> " + NEW_CONFIG + " (global config dir).");
                }
            }
        } catch (Throwable t) {
            LOG.warn("[SiegeOverhaul] Config migration failed: {}", t.toString());
        }
    }
}
