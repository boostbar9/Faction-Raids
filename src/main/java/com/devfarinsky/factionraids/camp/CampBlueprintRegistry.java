package com.devfarinsky.factionraids.camp;

import com.devfarinsky.factionraids.FactionLogger;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Built-in camp blueprint pool. Blueprints are loaded lazily from
 * {@code data/factionraids/camps/<id>/*.nbt} on the classpath. The registry
 * is designed to survive missing files — if a bundled blueprint fails to
 * load, {@link #getFallback()} always returns a code-only default so a raid
 * can still show a token camp presence.
 *
 * <p>Registered camp sizes:
 * <ul>
 *     <li>{@code small} — one tent + one banner. Used for probing raids.</li>
 *     <li>{@code medium} — tent + watchtower + palisade + banner. Standard.</li>
 *     <li>{@code large} — reserved for future high-tier factions; currently
 *         resolves to medium if no NBT ships.</li>
 * </ul>
 *
 * <p>The actual {@code .nbt} files are added in a follow-up commit — this
 * class ships wired up but resolving to {@link #getFallback()} until authored
 * blueprints exist on the classpath.
 */
public final class CampBlueprintRegistry {

    private static final ConcurrentMap<String, CampBlueprint> BLUEPRINTS = new ConcurrentHashMap<>();
    private static volatile boolean bootstrapped;

    private CampBlueprintRegistry() {}

    /** Blueprint chosen by name, or {@link #getFallback()} when unknown. */
    public static CampBlueprint get(String id) {
        ensureBootstrapped();
        return BLUEPRINTS.getOrDefault(id, getFallback());
    }

    /** Blueprint chosen for a given raid tier / faction size. */
    public static CampBlueprint chooseFor(int factionSize) {
        if (factionSize <= 6) return get("small");
        if (factionSize <= 14) return get("medium");
        return get("large");
    }

    /**
     * A minimal code-only blueprint that spawns nothing but a lumber area.
     * Used when no bundled NBT loads. Guaranteed non-null.
     */
    public static CampBlueprint getFallback() {
        return new CampBlueprint(
                "fallback",
                new CampBlueprint.Size(9, 9),
                8,
                6,
                Collections.emptyList());
    }

    private static void ensureBootstrapped() {
        if (bootstrapped) return;
        synchronized (BLUEPRINTS) {
            if (bootstrapped) return;
            registerBundled("small",     new CampBlueprint.Size(7, 7),   6,  6, tents(1));
            registerBundled("medium",    new CampBlueprint.Size(11, 11), 10, 8, tents(2));
            registerBundled("large",     new CampBlueprint.Size(15, 15), 14, 8, tents(3));
            bootstrapped = true;
        }
    }

    private static void registerBundled(String id, CampBlueprint.Size size,
                                        int lumberRadius, int lumberHeight,
                                        List<CampBlueprint.Placement> placements) {
        // Placements load their NBT lazily — a missing resource yields an
        // empty list of placements (still a valid camp, just no structures).
        List<CampBlueprint.Placement> resolved = new ArrayList<>();
        for (CampBlueprint.Placement p : placements) {
            if (p.structureNbt() != null) {
                resolved.add(p);
                continue;
            }
            CompoundTag nbt = loadStructure("data/factionraids/camps/" + id + "/" + p.name() + ".nbt");
            if (nbt != null) {
                resolved.add(new CampBlueprint.Placement(p.name(), p.offsetX(), p.offsetZ(),
                        p.facing(), nbt));
            }
        }
        BLUEPRINTS.put(id, new CampBlueprint(id, size, lumberRadius, lumberHeight, resolved));
    }

    private static List<CampBlueprint.Placement> tents(int count) {
        List<CampBlueprint.Placement> out = new ArrayList<>(count + 1);
        // Tent placements: back row, spaced 4 blocks apart, facing the approach.
        int startX = -((count - 1) * 2);
        for (int i = 0; i < count; i++) {
            out.add(new CampBlueprint.Placement(
                    "tent",
                    startX + i * 4,
                    -3,
                    Direction.NORTH,
                    null));
        }
        // Front-of-camp watchtower, offset toward the approach.
        out.add(new CampBlueprint.Placement("watchtower", 0, 3, Direction.NORTH, null));
        // Central banner, always present so the camp is visible even without other NBT.
        out.add(new CampBlueprint.Placement("banner", 0, 0, Direction.NORTH, null));
        return out;
    }

    private static CompoundTag loadStructure(String classpathPath) {
        try (InputStream in = CampBlueprintRegistry.class.getClassLoader()
                .getResourceAsStream(classpathPath)) {
            if (in == null) return null;
            return NbtIo.readCompressed(in);
        } catch (Exception e) {
            FactionLogger.LOG.debug("Camp blueprint {} not present in resources", classpathPath);
            return null;
        }
    }
}
