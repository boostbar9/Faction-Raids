package com.devfarinsky.factionraids.compat;

import com.devfarinsky.factionraids.FactionLogger;
import com.devfarinsky.factionraids.OptionalCompatBridge;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;

import java.lang.reflect.Method;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Compile-time-free bridge to Villager Workers. Uses only registry lookups
 * and reflection on documented public methods so Faction-Raids still loads
 * (and this bridge silently no-ops) when Workers isn't installed.
 *
 * <p>Method surface intentionally narrow — only what the camp construction
 * phase needs:
 *
 * <ul>
 *     <li>{@link #spawnLumberArea(ServerLevel, BlockPos, int, int, int, UUID, String)}
 *         — a designated tree-cutting zone. Lumberjacks assigned to the same
 *         area will chop trees inside it.</li>
 *     <li>{@link #spawnBuildArea(ServerLevel, BlockPos, Direction, CompoundTag, UUID, String)}
 *         — a blueprint zone. Builders assigned to the same area consume the
 *         structure NBT and place blocks.</li>
 *     <li>{@link #spawnLumberjack(ServerLevel, BlockPos, UUID, String)}
 *         — a lumberjack owned by the raider faction.</li>
 *     <li>{@link #spawnBuilder(ServerLevel, BlockPos, UUID, String)}
 *         — a builder owned by the raider faction.</li>
 *     <li>{@link #isWorkAreaDone(Entity)} — reports whether a work area
 *         entity has completed its task (Workers sets {@code isDone=true}).</li>
 * </ul>
 *
 * <p>All spawned entities are tagged via {@link OptionalCompatBridge} so the
 * existing scan/cleanup paths recognise them as raider-owned assets.
 */
public final class WorkersBridge {

    private static final ResourceLocation LUMBERAREA_ID = new ResourceLocation("workers", "lumberarea");
    private static final ResourceLocation BUILDAREA_ID = new ResourceLocation("workers", "buildarea");
    private static final ResourceLocation LUMBERJACK_ID = new ResourceLocation("workers", "lumberjack");
    private static final ResourceLocation BUILDER_ID = new ResourceLocation("workers", "builder");

    /**
     * Method names we've already warned about for reflective failures. Prevents
     * log spam when a Workers major-version bump removes a setter \u2014 we log
     * once per method, not once per raid tick.
     */
    private static final Set<String> WARNED_METHODS = ConcurrentHashMap.newKeySet();

    private WorkersBridge() {}

    // ---------- capability query ----------

    /** True when the Workers mod is loaded and camp integration is enabled. */
    public static boolean available() {
        return OptionalCompatBridge.isLoaded(OptionalCompatBridge.WORKERS);
    }

    // ---------- work areas ----------

    /**
     * Spawns a Workers "LumberArea" — a rectangular zone Lumberjacks will
     * search for trees within.
     *
     * @param level     server level
     * @param center    center of the area (workers reads the area's size from
     *                  its own width/depth data)
     * @param width     x-extent in blocks (odd numbers work best)
     * @param depth     z-extent in blocks
     * @param height    y-extent in blocks
     * @param owner     UUID of the raider faction "owner" (used by tagging)
     * @param teamKey   raider faction team key (used by tagging)
     * @return the spawned entity, or empty when Workers is missing or the spawn failed
     */
    public static Optional<Entity> spawnLumberArea(ServerLevel level, BlockPos center,
                                                    int width, int depth, int height,
                                                    UUID owner, String teamKey) {
        return spawnWorkAreaEntity(level, LUMBERAREA_ID, center, width, depth, height,
                owner, teamKey);
    }

    /**
     * Spawns a Workers "BuildArea" — a zone loaded with a structure NBT that
     * Builders will assemble.
     *
     * @param level         server level
     * @param anchor        BlockPos at the low corner of the structure
     * @param facing        rotation direction (workers reads FACING data)
     * @param structureNbt  structure NBT (as produced by {@code /structure save})
     * @param owner         raider faction owner UUID
     * @param teamKey       raider faction team key
     * @return the spawned entity, or empty when Workers is missing or the spawn failed
     */
    public static Optional<Entity> spawnBuildArea(ServerLevel level, BlockPos anchor,
                                                   Direction facing, CompoundTag structureNbt,
                                                   UUID owner, String teamKey) {
        if (!available()) return Optional.empty();
        Optional<Entity> spawned = spawnWorkAreaEntity(level, BUILDAREA_ID, anchor,
                sizeOf(structureNbt, "x", 5), sizeOf(structureNbt, "z", 5),
                sizeOf(structureNbt, "y", 5), owner, teamKey);
        spawned.ifPresent(entity -> {
            invokeVoid(entity, "setFacing", new Class<?>[]{Direction.class}, new Object[]{facing});
            invokeVoid(entity, "setStructureNBT", new Class<?>[]{CompoundTag.class},
                    new Object[]{structureNbt});
            invokeVoid(entity, "setStartBuild", new Class<?>[]{boolean.class}, new Object[]{false});
        });
        return spawned;
    }

    private static Optional<Entity> spawnWorkAreaEntity(ServerLevel level, ResourceLocation typeId,
                                                        BlockPos pos, int width, int depth, int height,
                                                        UUID owner, String teamKey) {
        if (!available()) return Optional.empty();
        EntityType<?> type = level.registryAccess()
                .registryOrThrow(Registries.ENTITY_TYPE)
                .getOptional(typeId).orElse(null);
        if (type == null) return Optional.empty();
        Entity entity = type.create(level);
        if (entity == null) return Optional.empty();
        entity.setPos(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D);
        invokeVoid(entity, "setWidth", new Class<?>[]{int.class}, new Object[]{width});
        invokeVoid(entity, "setDepth", new Class<?>[]{int.class}, new Object[]{depth});
        invokeVoid(entity, "setHeight", new Class<?>[]{int.class}, new Object[]{height});
        // Workers 2.0.3: AbstractWorkAreaEntity#setPlayerUUID takes a raw UUID
        // (not Optional<UUID>). Passing Optional.class silently failed reflection
        // lookup, leaving PLAYER_UUID at Optional.empty() -- then getPlayerUUID()
        // returned null on save and CompoundTag.putUUID crashed with NPE.
        // See PR notes: this is the fix for the LumberArea save crash reported
        // by players on modpacks that bundle Talhanation's Villager Workers.
        invokeVoid(entity, "setPlayerUUID", new Class<?>[]{UUID.class}, new Object[]{owner});
        invokeVoid(entity, "setPlayerName", new Class<?>[]{String.class}, new Object[]{teamKey});
        invokeVoid(entity, "setTeamStringID", new Class<?>[]{String.class}, new Object[]{teamKey});
        if (!level.addFreshEntity(entity)) return Optional.empty();
        OptionalCompatBridge.tagAssetOwnership(entity, teamKey, owner);
        return Optional.of(entity);
    }

    // ---------- worker entities ----------

    /** Spawns a Lumberjack owned by the raider faction. */
    public static Optional<Entity> spawnLumberjack(ServerLevel level, BlockPos pos,
                                                    UUID owner, String teamKey) {
        return spawnWorker(level, LUMBERJACK_ID, pos, owner, teamKey);
    }

    /** Spawns a Builder owned by the raider faction. */
    public static Optional<Entity> spawnBuilder(ServerLevel level, BlockPos pos,
                                                 UUID owner, String teamKey) {
        return spawnWorker(level, BUILDER_ID, pos, owner, teamKey);
    }

    private static Optional<Entity> spawnWorker(ServerLevel level, ResourceLocation typeId,
                                                 BlockPos pos, UUID owner, String teamKey) {
        if (!available()) return Optional.empty();
        EntityType<?> type = level.registryAccess()
                .registryOrThrow(Registries.ENTITY_TYPE)
                .getOptional(typeId).orElse(null);
        if (type == null) return Optional.empty();
        Entity spawned = type.spawn(level, pos, MobSpawnType.EVENT);
        if (spawned == null) return Optional.empty();
        invokeVoid(spawned, "setOwnerUUID", new Class<?>[]{UUID.class}, new Object[]{owner});
        invokeVoid(spawned, "setOwned", new Class<?>[]{boolean.class}, new Object[]{true});
        OptionalCompatBridge.tagAssetOwnership(spawned, teamKey, owner);
        return Optional.of(spawned);
    }

    // ---------- completion query ----------

    /** True when a work area entity has finished its task. */
    public static boolean isWorkAreaDone(Entity area) {
        if (area == null || !available()) return false;
        // AbstractWorkAreaEntity.isDone is a public field
        try {
            java.lang.reflect.Field f = area.getClass().getField("isDone");
            return f.getBoolean(area);
        } catch (ReflectiveOperationException e) {
            return false;
        }
    }

    // ---------- reflection helpers ----------

    private static void invokeVoid(Object target, String name, Class<?>[] sig, Object[] args) {
        try {
            Method m = target.getClass().getMethod(name, sig);
            m.invoke(target, args);
        } catch (ReflectiveOperationException e) {
            // Workers' API is unstable across versions and a missing setter
            // should degrade gracefully rather than crash the raid. But a
            // silent skip means a whole build area can end up mis-configured
            // with defaults \u2014 log the first failure per method so server
            // owners have a diagnostic hook without log spam every tick.
            String key = target.getClass().getName() + "#" + name;
            if (WARNED_METHODS.add(key)) {
                FactionLogger.LOG.debug(
                        "WorkersBridge: reflective call {} failed ({}) \u2014 subsequent failures suppressed.",
                        key, e.toString());
            }
        }
    }

    private static int sizeOf(CompoundTag structureNbt, String axis, int fallback) {
        if (structureNbt == null || !structureNbt.contains("size")) return fallback;
        try {
            net.minecraft.nbt.ListTag size = structureNbt.getList("size", 3); // TAG_INT
            int idx = switch (axis) {
                case "x" -> 0;
                case "y" -> 1;
                case "z" -> 2;
                default -> -1;
            };
            if (idx < 0 || idx >= size.size()) return fallback;
            int v = size.getInt(idx);
            return v > 0 ? v : fallback;
        } catch (Exception ignored) {
            return fallback;
        }
    }
}
