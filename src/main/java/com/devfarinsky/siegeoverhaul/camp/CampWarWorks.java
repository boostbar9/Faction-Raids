package com.devfarinsky.siegeoverhaul.camp;

import com.devfarinsky.siegeoverhaul.*;
import com.devfarinsky.siegeoverhaul.RaidSavedData.RaidState;
import com.devfarinsky.siegeoverhaul.core.EnemyCoreSite;
import com.devfarinsky.siegeoverhaul.core.SiegeCore;
import com.devfarinsky.siegeoverhaul.narrative.OlympianPresentation;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.LongTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.*;

/**
 * War works: the projects a war camp keeps building after its perimeter is complete.
 *
 * <p>Each work is a faction hall outside the wall with a keystone block. Once its native job
 * finishes, its effect applies while the keystone stands; destroying the keystone ends it and
 * the crew rebuilds it a bounded number of times. When every work stands the crew expands the
 * camp with tents. Everything is built through {@link NativeCampConstruction}, so every cell is
 * in the camp restoration ledger and removed when the raid ends.
 */
public final class CampWarWorks {
    private CampWarWorks() {}
    static final String ROOT = "CampWarWorks";
    static final int WORK_RING = 18, TENT_RING = 28, MAX_ATTEMPTS = 3, MAX_TENT_FAILURES = 6;
    static final int AURA_RADIUS = 32, LOOKOUT_RADIUS = 40;
    private static final UUID WEAPONSMITH_ARMOR = UUID.fromString("5b1e4c7a-9d2f-4a63-8e10-c4f7a2b9d615");

    public enum Work {
        SUPPLY_DEPOT("depot", "Supply Depot", "minecraft:composter",
                "The camp builds twice as fast while it stands."),
        LOOKOUT("lookout", "Lookout Tower", "minecraft:note_block",
                "Defenders near the camp are revealed while it stands."),
        INFIRMARY("infirmary", "Field Infirmary", "minecraft:cauldron",
                "Raiders and guards near the camp heal while it stands."),
        WEAPONSMITH("weaponsmith", "Weaponsmith", "minecraft:grindstone",
                "New raiders arrive with extra armor while it stands."),
        BARRACKS("barracks", "Barracks", "minecraft:loom",
                "Every wave brings extra raiders while it stands."),
        WAR_SHRINE("shrine", "War Shrine", "minecraft:crying_obsidian",
                "New raiders arrive empowered with Strength while it stands.");
        public final String key, title, keystoneBlock, purpose;
        Work(String key, String title, String keystoneBlock, String purpose) {
            this.key = key; this.title = title; this.keystoneBlock = keystoneBlock; this.purpose = purpose;
        }
    }

    public static boolean enabled() { return RaidConfig.CAMP_WAR_WORKS.get(); }

    // --- State ---------------------------------------------------------------------------------

    static CompoundTag root(RaidState raid) { return raid.campaign.getCompound(ROOT); }
    private static void save(RaidState raid, CompoundTag root) { raid.campaign.put(ROOT, root); }
    static CompoundTag entry(RaidState raid, Work work) { return root(raid).getCompound("Works").getCompound(work.key); }
    private static void putEntry(RaidState raid, Work work, CompoundTag entry) {
        CompoundTag root = root(raid), works = root.getCompound("Works");
        works.put(work.key, entry); root.put("Works", works); save(raid, root);
    }

    /** Built once and its keystone currently stands: its effect is live. */
    public static boolean standing(RaidState raid, Work work) {
        CompoundTag entry = entry(raid, work);
        return entry.getBoolean("Built") && !entry.getBoolean("AwaitingBuild") && entry.getBoolean("Active");
    }

    enum Action { BUILD, REBUILD, TENT, NONE }
    record Next(Action action, Work work) {}

    /** The next project, decided from saved state only: new works, then rebuilds, then tents. */
    static Next next(RaidState raid) {
        if (!enabled()) return new Next(Action.NONE, null);
        for (Work work : Work.values()) {
            CompoundTag entry = entry(raid, work);
            if (!entry.getBoolean("Built") && entry.getInt("Attempts") < MAX_ATTEMPTS) return new Next(Action.BUILD, work);
        }
        for (Work work : Work.values()) {
            CompoundTag entry = entry(raid, work);
            if (entry.getBoolean("Built") && !entry.getBoolean("AwaitingBuild") && !entry.getBoolean("Active")
                    && entry.getInt("Rebuilds") < RaidConfig.CAMP_WORKS_REBUILDS.get()) return new Next(Action.REBUILD, work);
        }
        CompoundTag root = root(raid);
        if (root.getInt("Tents") < RaidConfig.CAMP_WORKS_TENTS.get() && root.getInt("TentFailures") < MAX_TENT_FAILURES)
            return new Next(Action.TENT, null);
        return new Next(Action.NONE, null);
    }

    /** True while the crew still has war works to do, so lost builders are worth replacing. */
    public static boolean hasWork(RaidState raid) { return next(raid).action() != Action.NONE; }

    /** Ticks between projects; a standing supply depot halves the wait. */
    public static int intervalTicks(RaidState raid) {
        int base = RaidConfig.CAMP_WORKS_SECONDS.get() * 20;
        return standing(raid, Work.SUPPLY_DEPOT) ? Math.max(20, base / 2) : base;
    }

    // --- Construction ----------------------------------------------------------------------------

    /** One project per interval. Called only when no camp job is active and the crew is alive. */
    static void develop(ServerLevel level, RaidState raid) {
        var anchor = RaidSavedData.get(level.getServer()).anchors.get(raid.teamKey);
        if (anchor == null || raid.campPos == null) return;
        Next next = next(raid);
        switch (next.action()) {
            case BUILD -> build(level, raid, anchor, next.work());
            case REBUILD -> rebuild(level, raid, next.work());
            case TENT -> tent(level, raid, anchor);
            case NONE -> { }
        }
    }

    private static void build(ServerLevel level, RaidState raid, RaidSavedData.Anchor anchor, Work work) {
        CompoundTag entry = entry(raid, work).copy();
        entry.putInt("Attempts", entry.getInt("Attempts") + 1);
        Set<net.minecraft.world.level.ChunkPos> checked = new HashSet<>();
        for (BlockPos candidate : workSites(raid, work)) {
            if (crowded(raid, candidate, 10)) continue;
            var site = CampDevelopment.sitePlan(level, raid, anchor, checked, candidate, true,
                    (c, entrance) -> CampWarWorksLayout.structure(c, entrance, work, raid.factionId));
            if (site.isEmpty()) continue;
            raid.pendingCampBlocks.putAll(site.get().plan());
            if (NativeCampConstruction.start(level, raid)) {
                BlockPos keystone = CampStructures.keystone(site.get().center(), site.get().entrance());
                entry.putLong("Center", site.get().center().asLong());
                entry.putInt("Entrance", site.get().entrance().get2DDataValue());
                entry.putLong("Pos", keystone.asLong());
                entry.putString("Block", work.keystoneBlock);
                entry.putBoolean("AwaitingBuild", true);
                entry.putBoolean("Active", false);
                putEntry(raid, work, entry);
                announce(level.getServer(), raid.teamKey, Component.literal("Enemy builders are raising a "
                        + work.title + " outside their walls. " + work.purpose + " Destroy its "
                        + blockName(work.keystoneBlock) + " to stop it.").withStyle(ChatFormatting.GOLD));
                return;
            }
            raid.pendingCampBlocks.clear();
        }
        putEntry(raid, work, entry);
    }

    private static void rebuild(ServerLevel level, RaidState raid, Work work) {
        CompoundTag entry = entry(raid, work).copy();
        entry.putInt("Rebuilds", entry.getInt("Rebuilds") + 1);
        putEntry(raid, work, entry);
        if (!entry.contains("Center", Tag.TAG_LONG) || !entry.contains("Entrance", Tag.TAG_INT)) return;
        BlockPos center = BlockPos.of(entry.getLong("Center"));
        Direction entrance = Direction.from2DDataValue(entry.getInt("Entrance"));
        long keystone = entry.getLong("Pos");
        // Only refill what is missing; never replace a block a defender put in the way.
        Map<Long, String> missing = new LinkedHashMap<>();
        CampWarWorksLayout.structure(center, entrance, work, raid.factionId).forEach((key, block) -> {
            BlockPos p = BlockPos.of(key);
            if (level.hasChunkAt(p) && level.getBlockState(p).isAir() && level.getFluidState(p).isEmpty()
                    && level.getBlockEntity(p) == null) missing.put(key, block);
        });
        if (!missing.containsKey(keystone)) return;
        raid.pendingCampBlocks.putAll(missing);
        if (NativeCampConstruction.start(level, raid)) {
            entry.putBoolean("AwaitingBuild", true);
            putEntry(raid, work, entry);
            announce(level.getServer(), raid.teamKey, Component.literal("Enemy builders are rebuilding their "
                    + work.title + ".").withStyle(ChatFormatting.GOLD));
            return;
        }
        raid.pendingCampBlocks.clear();
    }

    private static void tent(ServerLevel level, RaidState raid, RaidSavedData.Anchor anchor) {
        CompoundTag root = root(raid);
        Set<net.minecraft.world.level.ChunkPos> checked = new HashSet<>();
        String cloth = clothFor(raid.factionId);
        for (BlockPos candidate : tentSites(raid)) {
            if (crowded(raid, candidate, 7)) continue;
            Map<Long, Integer> ground = new HashMap<>();
            int top = Integer.MIN_VALUE;
            boolean ok = true;
            for (int dx = -2; dx <= 2 && ok; dx++) for (int dz = -2; dz <= 2 && ok; dz++) {
                BlockPos column = candidate.offset(dx, 0, dz);
                if (!level.hasChunkAt(column) || !level.getWorldBorder().isWithinBounds(column)
                        || CampPerimeter.mainApproachColumn(raid, column) || nearGate(raid, column)
                        || CampStructures.accessColumn(raid, column) || EnemyCoreSite.reserved(raid, column)
                        || !CampDevelopment.claimed(level, raid, anchor, checked, column)) { ok = false; break; }
                int surface = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column.getX(), column.getZ());
                BlockPos support = column.atY(surface - 1);
                if (!level.getFluidState(support).isEmpty()
                        || !level.getBlockState(support).isFaceSturdy(level, support, Direction.UP)) { ok = false; break; }
                ground.put(column.atY(0).asLong(), surface);
                top = Math.max(top, surface);
            }
            if (!ok || Math.abs(top - raid.campPos.getY()) > 3) continue;
            int floor = top;
            if (ground.values().stream().anyMatch(y -> floor - y > 1)) continue;
            BlockPos at = candidate.atY(floor);
            raid.pendingCampBlocks.putAll(CampWarWorksLayout.tent(at, ground, cloth));
            if (NativeCampConstruction.start(level, raid)) {
                root.putInt("Tents", root.getInt("Tents") + 1);
                root.putInt("TentFailures", 0);
                ListTag centers = root.getList("TentCenters", Tag.TAG_LONG);
                centers.add(LongTag.valueOf(at.asLong()));
                root.put("TentCenters", centers);
                save(raid, root);
                return;
            }
            raid.pendingCampBlocks.clear();
        }
        root.putInt("TentFailures", root.getInt("TentFailures") + 1);
        save(raid, root);
    }

    /** Called with the completed job, before it is cleared: unlock works whose keystone was in it. */
    static void constructionCompleted(RaidState raid) {
        CompoundTag root = root(raid), works = root.getCompound("Works");
        boolean changed = false;
        for (Work work : Work.values()) {
            CompoundTag entry = works.getCompound(work.key);
            if (entry.getBoolean("AwaitingBuild") && entry.contains("Pos", Tag.TAG_LONG)
                    && work.keystoneBlock.equals(raid.pendingCampBlocks.get(entry.getLong("Pos")))) {
                entry.putBoolean("AwaitingBuild", false);
                entry.putBoolean("Built", true);
                works.put(work.key, entry);
                changed = true;
            }
        }
        if (changed) { root.put("Works", works); save(raid, root); }
    }

    // --- Site selection ------------------------------------------------------------------------

    /** Eight sites on a ring outside the wall, starting on a side that varies per work. */
    static List<BlockPos> workSites(RaidState raid, Work work) {
        List<BlockPos> sites = new ArrayList<>();
        int start = work.ordinal() * 3;
        for (int i = 0; i < 8; i++) {
            double angle = Math.PI * 2 * ((start + i) % 8) / 8.0;
            sites.add(raid.campPos.offset((int) Math.round(Math.cos(angle) * WORK_RING), 0,
                    (int) Math.round(Math.sin(angle) * WORK_RING)));
        }
        sites.removeIf(p -> nearGate(raid, p));
        return sites;
    }

    static List<BlockPos> tentSites(RaidState raid) {
        List<BlockPos> sites = new ArrayList<>();
        int start = root(raid).getInt("Tents") * 5;
        for (int i = 0; i < 12; i++) {
            double angle = Math.PI * 2 * ((start + i) % 12 + 0.5) / 12.0;
            sites.add(raid.campPos.offset((int) Math.round(Math.cos(angle) * TENT_RING), 0,
                    (int) Math.round(Math.sin(angle) * TENT_RING)));
        }
        return sites;
    }

    /** Keep clear of the War Gate pad and its approach. */
    static boolean nearGate(RaidState raid, BlockPos pos) {
        if (!raid.warGate.contains("Center", Tag.TAG_LONG)) return false;
        BlockPos gate = BlockPos.of(raid.warGate.getLong("Center"));
        int dx = pos.getX() - gate.getX(), dz = pos.getZ() - gate.getZ();
        return dx * dx + dz * dz < 12 * 12;
    }

    /** Too close to another work or tent already placed. */
    static boolean crowded(RaidState raid, BlockPos pos, int distance) {
        List<BlockPos> taken = new ArrayList<>();
        CompoundTag works = root(raid).getCompound("Works");
        for (Work work : Work.values()) {
            CompoundTag entry = works.getCompound(work.key);
            if (entry.contains("Center", Tag.TAG_LONG)) taken.add(BlockPos.of(entry.getLong("Center")));
        }
        for (Tag tag : root(raid).getList("TentCenters", Tag.TAG_LONG)) taken.add(BlockPos.of(((LongTag) tag).getAsLong()));
        for (BlockPos other : taken) {
            int dx = pos.getX() - other.getX(), dz = pos.getZ() - other.getZ();
            if (dx * dx + dz * dz < distance * distance) return true;
        }
        return false;
    }

    private static String clothFor(String faction) {
        return switch (faction == null ? "" : faction) {
            case "blackbay_reavers" -> "minecraft:cyan_wool";
            case "hollowfang_clan" -> "minecraft:red_wool";
            case "emberchant_zealots" -> "minecraft:orange_wool";
            case "crownfall_exiles" -> "minecraft:white_wool";
            case "wilds_marauders" -> "minecraft:green_wool";
            default -> "minecraft:brown_wool";
        };
    }

    // --- Keystones and effects ----------------------------------------------------------------

    /** Flip each built work between standing and lost, announce edges, and apply area effects. */
    public static void tick(ServerLevel level, RaidState raid) {
        CompoundTag root = root(raid);
        if (root.isEmpty() || raid.campPos == null) return;
        CompoundTag works = root.getCompound("Works");
        boolean dirty = false;
        for (Work work : Work.values()) {
            if (!works.contains(work.key, Tag.TAG_COMPOUND)) continue;
            CompoundTag entry = works.getCompound(work.key);
            if (!entry.getBoolean("Built") || entry.getBoolean("AwaitingBuild")) continue;
            BlockPos pos = BlockPos.of(entry.getLong("Pos"));
            if (!level.hasChunkAt(pos)) continue;
            boolean present = matches(level, pos, work.keystoneBlock);
            boolean active = entry.getBoolean("Active");
            if (present && !active) {
                entry.putBoolean("Active", true);
                announce(level.getServer(), raid.teamKey, Component.literal("Enemy " + work.title + " is up. "
                        + work.purpose).withStyle(ChatFormatting.RED));
                dirty = true;
            } else if (!present && active) {
                entry.putBoolean("Active", false);
                announce(level.getServer(), raid.teamKey, Component.literal("Enemy " + work.title
                        + " destroyed. Its advantage is gone.").withStyle(ChatFormatting.GREEN));
                dirty = true;
            }
            if (present && level.getGameTime() % 20L == 0L) {
                var particle = OlympianPresentation.forFaction(raid.factionId).particle();
                level.sendParticles(particle, pos.getX() + 0.5D, pos.getY() + 1.2D, pos.getZ() + 0.5D, 3, 0.45D, 0.35D, 0.45D, 0.02D);
            }
        }
        if (dirty) { root.put("Works", works); save(raid, root); RaidSavedData.get(level.getServer()).setDirty(); }
        applyAuras(level, raid);
    }

    private static void applyAuras(ServerLevel level, RaidState raid) {
        BlockPos camp = raid.campPos;
        if (standing(raid, Work.INFIRMARY)) {
            double r2 = (double) AURA_RADIUS * AURA_RADIUS;
            for (Set<UUID> group : List.of(raid.raiders, raid.campGuards)) for (UUID id : group) {
                if (level.getEntity(id) instanceof LivingEntity unit && unit.isAlive()
                        && unit.getHealth() < unit.getMaxHealth()
                        && unit.distanceToSqr(camp.getX() + 0.5, camp.getY(), camp.getZ() + 0.5) <= r2) unit.heal(1.0F);
            }
        }
        if (standing(raid, Work.LOOKOUT)) {
            double r2 = (double) LOOKOUT_RADIUS * LOOKOUT_RADIUS;
            for (ServerPlayer player : level.players()) {
                if (!raid.teamKey.equals(SiegeCore.key(player)) || player.isSpectator() || player.isCreative()) continue;
                if (player.distanceToSqr(camp.getX() + 0.5, camp.getY(), camp.getZ() + 0.5) <= r2)
                    player.addEffect(new MobEffectInstance(MobEffects.GLOWING, 60, 0, true, false, true));
            }
        }
    }

    /** Extra raiders the barracks adds to each wave while it stands. */
    public static int waveBonus(RaidState raid) {
        return standing(raid, Work.BARRACKS) ? RaidConfig.BARRACKS_EXTRA_RAIDERS.get() : 0;
    }

    /** Apply spawn-time boons from the weaponsmith and war shrine to a freshly spawned raider. */
    public static void onRaiderSpawned(RaidState raid, Mob raider) {
        if (standing(raid, Work.WEAPONSMITH)) {
            var armor = raider.getAttribute(Attributes.ARMOR);
            if (armor != null && armor.getModifier(WEAPONSMITH_ARMOR) == null)
                armor.addPermanentModifier(new AttributeModifier(WEAPONSMITH_ARMOR, "Siege Weaponsmith", 4.0D,
                        AttributeModifier.Operation.ADDITION));
        }
        if (standing(raid, Work.WAR_SHRINE))
            raider.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 20 * 90, 0, false, true, true));
    }

    private static boolean matches(ServerLevel level, BlockPos pos, String block) {
        ResourceLocation id = ForgeRegistries.BLOCKS.getKey(level.getBlockState(pos).getBlock());
        return id != null && id.toString().equals(block);
    }

    private static String blockName(String id) {
        return id.substring(id.indexOf(':') + 1).replace('_', ' ');
    }

    private static void announce(MinecraftServer server, String teamKey, Component message) {
        Component styled = Component.empty().append(ModConstants.MESSAGE_PREFIX).append(message);
        if (RaidConfig.ANNOUNCE_GLOBALLY.get()) server.getPlayerList().broadcastSystemMessage(styled, false);
        else for (var player : server.getPlayerList().getPlayers())
            if (teamKey.equals(SiegeCore.key(player))) player.sendSystemMessage(styled);
    }
}
