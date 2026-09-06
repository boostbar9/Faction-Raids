package com.devfarinsky.factionraids.scout;

import com.devfarinsky.factionraids.ModConstants;
import com.devfarinsky.factionraids.RaidConfig;
import com.devfarinsky.factionraids.RaidSavedData;
import com.devfarinsky.factionraids.narrative.RaidNarrative;
import com.devfarinsky.factionraids.narrative.RaidNarrativeSelector;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.monster.Pillager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.WrittenBookItem;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * v2.26.0 pre-raid scouting coordinator.
 *
 * <p>Responsibilities:
 * <ol>
 *   <li>Roll a scout mission at the start of the middle third of each
 *       anchor's cooldown, if none is already scheduled.</li>
 *   <li>Tick pending missions; spawn scout mobs at their scheduled time.</li>
 *   <li>Clean up scouts when the raid starts, the anchor is deleted, or
 *       the mission expires.</li>
 *   <li>On scout death, drop an intel letter (a WrittenBook) naming the
 *       attacker faction that will actually come next.</li>
 * </ol>
 *
 * <p>All entry points check {@link RaidConfig#SCOUTING_ENABLED} first; if
 * scouting is disabled server-wide the manager becomes a no-op. Missions
 * are stored on {@link RaidSavedData} via a small NBT round-trip so a
 * server restart mid-cooldown preserves them.
 *
 * <p>Design note: scouts are Pillagers under the hood because Pillagers
 * are non-passive but non-hostile-by-default (they attack players on
 * sight, but we override their goal stack). Using a Pillager rather than
 * a custom entity type avoids needing a client-side registration and
 * makes the scout look distinctively raider-faction without new assets.
 */
public final class ScoutManager {

    private ScoutManager() {}

    // -------- Persistence hooks (called from RaidSavedData) ------------

    /**
     * Append the manager's per-anchor scout missions to {@code root}.
     * Called from {@code RaidSavedData.save}. Storage lives outside the
     * anchor record so old saves round-trip cleanly when the feature is
     * disabled — a missing tag is treated as no scheduled missions.
     */
    public static void save(RaidSavedData data, CompoundTag root) {
        ListTag list = new ListTag();
        for (ScoutMission m : data.scoutMissions.values()) list.add(m.save());
        root.put("ScoutMissions", list);
    }

    public static void load(RaidSavedData data, CompoundTag root) {
        if (!root.contains("ScoutMissions", net.minecraft.nbt.Tag.TAG_LIST)) return;
        ListTag list = root.getList("ScoutMissions", net.minecraft.nbt.Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            ScoutMission m = ScoutMission.load(list.getCompound(i));
            if (m != null && !m.teamKey.isBlank()) data.scoutMissions.put(m.teamKey, m);
        }
    }

    // -------- Scheduling ------------------------------------------------

    /**
     * If this anchor is in cooldown and does not have a scheduled mission,
     * roll one. Called every server tick from the main raid tick loop.
     * Idempotent per anchor: only rolls once per cooldown.
     */
    public static void maybeSchedule(MinecraftServer server, RaidSavedData data, RaidSavedData.Anchor anchor) {
        if (!RaidConfig.SCOUTING_ENABLED.get()) return;
        if (data.raids.containsKey(anchor.teamKey())) return; // active raid — no scouts
        if (data.scoutMissions.containsKey(anchor.teamKey())) return; // already scheduled
        long now = server.overworld().getGameTime();
        long raidAt = anchor.nextRaidGameTime();
        long ticksUntilRaid = raidAt - now;
        // Only roll if we're at least 4 minutes from the raid. Prevents
        // scouts from spawning during the warning countdown or landing
        // uselessly right before the assault begins.
        long minLeadTicks = 4L * 60L * 20L;
        if (ticksUntilRaid < minLeadTicks) return;
        // Roll spawn time in the middle third of the remaining cooldown.
        long spawnAt = now + ticksUntilRaid / 3L
                + (long) (ThreadLocalRandom.current().nextDouble() * (ticksUntilRaid / 3.0));
        // Expiration = 3 minutes after spawn OR just before the raid, whichever comes first.
        long expireAt = Math.min(spawnAt + 3L * 60L * 20L, raidAt - 60L * 20L);
        // Pre-select the narrative for the raid that will follow, so the
        // intel letter tells the truth. beginRaid re-uses this narrative
        // via a lookup on the same map so the promise is kept.
        RaidNarrative previewed = RaidNarrativeSelector.select(
                server.overworld().random,
                anchor.teamDisplay(),
                anchor.primaryPoint().name());
        data.scoutMissions.put(anchor.teamKey(),
                new ScoutMission(anchor.teamKey(), spawnAt, expireAt, previewed));
        data.setDirty();
    }

    /**
     * Tick pending scout missions for all anchors. Spawns scouts at the
     * scheduled time, drops expired missions, and cleans up dead scouts.
     */
    public static void tick(MinecraftServer server, RaidSavedData data) {
        if (data.scoutMissions.isEmpty()) return;
        long now = server.overworld().getGameTime();
        Iterator<ScoutMission> it = data.scoutMissions.values().iterator();
        while (it.hasNext()) {
            ScoutMission m = it.next();
            RaidSavedData.Anchor anchor = data.anchors.get(m.teamKey);
            // Anchor deleted mid-cooldown -> drop mission and any live scouts.
            if (anchor == null) {
                despawnLiveScouts(server, m);
                it.remove();
                data.setDirty();
                continue;
            }
            // Raid started before the scout mission expired -> the raid
            // itself takes over the narrative; live scouts are recalled.
            if (data.raids.containsKey(anchor.teamKey())) {
                despawnLiveScouts(server, m);
                it.remove();
                data.setDirty();
                continue;
            }
            if (!m.spawned && now >= m.spawnGameTime) {
                spawnScouts(server, anchor, m);
                m.spawned = true;
                data.setDirty();
            }
            // Expired: mission window closed. Any straggling scouts flee/despawn.
            if (now >= m.expireGameTime) {
                despawnLiveScouts(server, m);
                it.remove();
                data.setDirty();
            } else if (m.spawned) {
                // Prune UUIDs of scouts that quietly died or unloaded so the
                // saved mission stays small.
                pruneDeadScoutUuids(server, m);
            }
        }
    }

    // -------- Spawning --------------------------------------------------

    private static void spawnScouts(MinecraftServer server, RaidSavedData.Anchor anchor, ScoutMission m) {
        ServerLevel level = server.overworld();
        BlockPos anchorPos = anchor.primaryPoint().pos();
        int distance = RaidConfig.SCOUT_SPAWN_DISTANCE.get();
        int count = 1 + level.random.nextInt(RaidConfig.SCOUT_PARTY_SIZE.get());
        double approachAngle = level.random.nextDouble() * Math.PI * 2.0;
        // Spawn scouts as a tight group so they arrive together.
        BlockPos spawnGround = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                new BlockPos(
                        anchorPos.getX() + (int) Math.round(Math.cos(approachAngle) * distance),
                        0,
                        anchorPos.getZ() + (int) Math.round(Math.sin(approachAngle) * distance)));
        BlockPos lookout = RaiderScoutGoal.findLookoutNear(level, anchorPos,
                Math.max(40, distance / 2));
        int observeTicks = RaidConfig.SCOUT_OBSERVE_SECONDS.get() * 20;
        double speed = 1.0;
        for (int i = 0; i < count; i++) {
            Pillager scout = EntityType.PILLAGER.create(level);
            if (scout == null) continue;
            BlockPos jitter = spawnGround.offset(
                    level.random.nextInt(5) - 2, 0, level.random.nextInt(5) - 2);
            scout.setPos(jitter.getX() + 0.5, jitter.getY(), jitter.getZ() + 0.5);
            // Overwrite the default vanilla goal stack with our scout goal
            // ONLY. Vanilla pillager targeting would otherwise cause it
            // to open fire the moment it saw a player, breaking the
            // observe-and-flee fantasy.
            scout.goalSelector.getAvailableGoals().clear();
            scout.targetSelector.getAvailableGoals().clear();
            scout.goalSelector.addGoal(0, new RaiderScoutGoal(scout, lookout, spawnGround,
                    observeTicks, speed));
            // Give the scout a spyglass instead of a crossbow so it looks
            // like an observer, not a shooter. Cosmetic and vanilla-safe.
            scout.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND,
                    new ItemStack(Items.SPYGLASS));
            scout.setDropChance(net.minecraft.world.entity.EquipmentSlot.MAINHAND, 0.0f);
            scout.setPersistenceRequired();
            // Mark for our friendly-fire logic AND our scout-exclusion logic.
            scout.getPersistentData().putString(ModConstants.Tags.RAID_TEAM, anchor.teamKey());
            scout.getPersistentData().putBoolean(ModConstants.Tags.SCOUT, true);
            // Custom name so defenders instantly know what they're looking at.
            String factionName = m.previewedNarrative != null && m.previewedNarrative.factionName != null
                    ? m.previewedNarrative.factionName : "Unknown";
            scout.setCustomName(Component.literal(factionName + " Scout")
                    .withStyle(ChatFormatting.GRAY));
            scout.setCustomNameVisible(false); // reveal only on proximity via v2.15 label system if desired
            if (level.addFreshEntity(scout)) {
                m.scoutUuids.add(scout.getUUID());
            }
        }
        // Only announce if there is at least one online defender to see it.
        // Silence for offline factions is intentional: nothing to notice.
        List<ServerPlayer> members = onlineMembersOf(server, anchor);
        if (!members.isEmpty() && !m.scoutUuids.isEmpty()) {
            // The announcement is deliberately vague so scouts remain a
            // discoverable event, not a spoiler. "Movement on the horizon"
            // encourages defenders to go look rather than telegraphing
            // exactly what's out there.
            for (ServerPlayer p : members) {
                p.displayClientMessage(Component.literal("You sense movement on the horizon.")
                        .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC), true);
            }
        }
    }

    // -------- Death / hurt handlers -------------------------------------

    /**
     * Called from RaidEvents' hurt handler when the victim carries the
     * SCOUT tag. Flips the goal into flee mode; scouts never fight back.
     */
    public static void onScoutHurt(Mob scout) {
        if (!(scout instanceof PathfinderMob)) return;
        for (net.minecraft.world.entity.ai.goal.WrappedGoal wrapped : scout.goalSelector.getAvailableGoals()) {
            if (wrapped.getGoal() instanceof RaiderScoutGoal sg) {
                sg.triggerFlee();
                return;
            }
        }
    }

    /**
     * Called from the LivingDeath handler when a SCOUT-tagged mob dies.
     * Drops an intel letter (WrittenBook) revealing the attacker faction
     * and their casus belli. Removes the UUID from the mission so we
     * don't try to despawn a dead entity later.
     */
    public static void onScoutKilled(MinecraftServer server, RaidSavedData data, Mob scout) {
        String team = scout.getPersistentData().getString(ModConstants.Tags.RAID_TEAM);
        ScoutMission m = data.scoutMissions.get(team);
        if (m == null) return;
        m.scoutUuids.remove(scout.getUUID());
        data.setDirty();
        if (!RaidConfig.SCOUT_DROP_INTEL_LETTER.get()) return;
        ItemStack letter = buildIntelLetter(m);
        if (scout.level() instanceof ServerLevel level) {
            net.minecraft.world.entity.item.ItemEntity drop = new net.minecraft.world.entity.item.ItemEntity(
                    level, scout.getX(), scout.getY() + 0.5, scout.getZ(), letter);
            drop.setDefaultPickUpDelay();
            level.addFreshEntity(drop);
        }
    }

    private static ItemStack buildIntelLetter(ScoutMission m) {
        ItemStack book = new ItemStack(Items.WRITTEN_BOOK);
        CompoundTag tag = book.getOrCreateTag();
        // v2.33.0: intel letter presentation overhaul. Cleaner title, better
        // author fallback, typographic quotes on the war chant, and a
        // dedicated second page for the casus belli when one is known.
        tag.putString("title", "Scout's Intel");
        String author = (m.previewedNarrative != null && m.previewedNarrative.factionName != null
                && !m.previewedNarrative.factionName.isBlank())
                ? m.previewedNarrative.factionName
                : "An anonymous scout";
        tag.putString("author", author);
        ListTag pages = new ListTag();
        addPage(pages, buildPage1(m));
        String page2 = buildPage2(m);
        if (page2 != null && !page2.isBlank()) addPage(pages, page2);
        tag.put("pages", pages);
        tag.putInt("generation", 1);
        return book;
    }

    /**
     * First page: identity + opening + war chant. Prose flow, no template seams.
     */
    private static String buildPage1(ScoutMission m) {
        if (m.previewedNarrative == null) {
            return "An unknown raider force approaches.\n\nThe rest of this letter is illegible.";
        }
        StringBuilder p = new StringBuilder();
        String name = nullSafe(m.previewedNarrative.factionName);
        String epithet = m.previewedNarrative.factionEpithet;
        if (epithet != null && !epithet.isBlank()) {
            p.append("The ").append(name).append(" march.\n");
            p.append("They call themselves ").append(epithet).append(".\n\n");
        } else {
            p.append("The ").append(name).append(" march.\n\n");
        }
        String opening = m.previewedNarrative.opening;
        if (opening != null && !opening.isBlank()) {
            p.append(opening).append("\n\n");
        }
        String chant = m.previewedNarrative.chant;
        if (chant != null && !chant.isBlank()) {
            // Use typographic quotes for the chant so it reads as speech,
            // not source code. \u201c and \u201d are curly double quotes.
            p.append("Their chant:\n\u201c").append(chant).append("\u201d");
        }
        return p.toString();
    }

    /**
     * Second page: why they're marching. Derived from the casus belli id
     * so defenders learn the pretext. Returns null when there's nothing
     * meaningful to show (unknown narrative or missing casus belli).
     */
    private static String buildPage2(ScoutMission m) {
        if (m.previewedNarrative == null) return null;
        String cbId = m.previewedNarrative.casusBelliId;
        if (cbId == null || cbId.isBlank()) return null;
        return "They march under the banner of\n\u201c" + humanizeCasusBelli(cbId) + ".\u201d\n\n"
                + "Prepare accordingly.";
    }

    /**
     * Turns a casus belli id like {@code raider_plunder} or {@code holy_war}
     * into a display phrase like {@code Raider Plunder} or {@code Holy War}.
     * Kept local so we don't reach across packages just to title-case a string.
     */
    private static String humanizeCasusBelli(String id) {
        String[] parts = id.replace('-', '_').split("_");
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            if (parts[i].isEmpty()) continue;
            if (out.length() > 0) out.append(' ');
            out.append(Character.toUpperCase(parts[i].charAt(0)));
            if (parts[i].length() > 1) out.append(parts[i].substring(1).toLowerCase());
        }
        return out.length() == 0 ? "war" : out.toString();
    }

    private static void addPage(ListTag pages, String text) {
        pages.add(net.minecraft.nbt.StringTag.valueOf(
                "{\"text\":\"" + escapeJson(text) + "\"}"));
    }

    // -------- Cleanup helpers ------------------------------------------

    private static void despawnLiveScouts(MinecraftServer server, ScoutMission m) {
        for (ServerLevel level : server.getAllLevels()) {
            for (UUID id : new ArrayList<>(m.scoutUuids)) {
                Entity e = level.getEntity(id);
                if (e != null) e.discard();
            }
        }
        m.scoutUuids.clear();
    }

    private static void pruneDeadScoutUuids(MinecraftServer server, ScoutMission m) {
        if (m.scoutUuids.isEmpty()) return;
        // Only prune if the primary world has the chunk loaded — otherwise
        // we can't tell alive-and-elsewhere from silently-dead.
        ServerLevel level = server.overworld();
        m.scoutUuids.removeIf(id -> {
            Entity e = level.getEntity(id);
            return e != null && !e.isAlive();
        });
    }

    /**
     * Look up the previewed narrative for an anchor's next raid, if any.
     * Called from {@code beginRaid} so the raid honors the promise the
     * scout's intel letter made. Returns null when scouting is off or no
     * mission ran for this cooldown.
     */
    public static RaidNarrative consumePreviewedNarrative(RaidSavedData data, String teamKey) {
        ScoutMission m = data.scoutMissions.remove(teamKey);
        if (m == null) return null;
        // Also despawn any lingering scouts — the raid itself is starting.
        // Do a best-effort discard synchronous here; the caller has the
        // server context if needed.
        return m.previewedNarrative;
    }

    // -------- Small helpers --------------------------------------------

    private static List<ServerPlayer> onlineMembersOf(MinecraftServer server, RaidSavedData.Anchor anchor) {
        List<ServerPlayer> out = new ArrayList<>();
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (anchor.members().contains(p.getUUID())) out.add(p);
        }
        return out;
    }

    private static String nullSafe(String s) { return s == null ? "" : s; }

    private static String escapeJson(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
    }
}
