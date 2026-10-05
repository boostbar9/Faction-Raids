package com.devfarinsky.siegeoverhaul.nativecompat;

import com.devfarinsky.siegeoverhaul.camp.CampLoading;
import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.FlatLevelSource;
import net.minecraft.world.level.levelgen.WorldDimensions;
import net.minecraft.world.level.levelgen.flat.FlatLayerInfo;
import net.minecraft.world.level.levelgen.presets.WorldPresets;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Labeled terrain only. Never selects a site or writes production raid state. */
final class NativeCampLifecycleFixture {
    static final int WATER_DEPTH = 24, ISLAND_HALF = 28;
    private static final int WIDTH = ISLAND_HALF * 2 + 1;
    static final int ISLAND_WRITES = WIDTH * WIDTH * WATER_DEPTH;
    private NativeCampLifecycleFixture() {}

    static WorldDimensions dimensions(RegistryAccess access) {
        WorldDimensions dimensions = access.registryOrThrow(Registries.WORLD_PRESET)
                .getOrThrow(WorldPresets.FLAT).createWorldDimensions();
        FlatLevelSource original = (FlatLevelSource) dimensions.overworld();
        var layers = List.of(new FlatLayerInfo(1, Blocks.BEDROCK), new FlatLayerInfo(60, Blocks.DIRT),
                new FlatLayerInfo(WATER_DEPTH, Blocks.WATER));
        var settings = original.settings().withBiomeAndLayers(layers, Optional.empty(), original.settings().getBiome());
        return dimensions.replaceOverworldGenerator(access, new FlatLevelSource(settings));
    }

    static BlockPos islandCenter(BlockPos core, double approachAngle) {
        BlockPos center = CampLoading.recoveryCandidate(core, approachAngle, 0);
        // The fixed island exists during BOTH original passes. No original
        // local candidate may fit even its required flat 19x19 core on the island.
        for (int attempt = 0; attempt < 200; attempt++) {
            BlockPos scout = CampLoading.candidate(core, approachAngle, attempt);
            for (boolean expanded : List.of(false, true)) for (int local = 0; local < (expanded ? 25 : 9); local++) {
                BlockPos candidate = CampLoading.localCandidate(scout, local, expanded);
                int dx = Math.abs(candidate.getX() - center.getX());
                int dz = Math.abs(candidate.getZ() - center.getZ());
                require(dx > ISLAND_HALF - 9 || dz > ISLAND_HALF - 9,
                        "Recovery island could fit an original-pass camp core");
            }
        }
        return center;
    }

    /** At most 4,096 physical terrain writes per ordinary server tick, fully rooted in generated dirt. */
    static int buildIslandBatch(ServerLevel level, NativeCampFixture.Fixture fixture, BlockPos center, int cursor) {
        require(fixture.world().equals(level.getServer().getWorldData().getLevelName())
                        && fixture.world().equals("siege-native-camp-recovery-island"), "Refusing non-isolated island setup");
        int end = Math.min(ISLAND_WRITES, cursor + 4096);
        for (int index = cursor; index < end; index++) {
            int depth = index % WATER_DEPTH + 1;
            int column = index / WATER_DEPTH;
            BlockPos pos = center.offset(column % WIDTH - ISLAND_HALF, -depth, column / WIDTH - ISLAND_HALF);
            require(!fixture.protectedCells().containsKey(pos), "Island fixture intersects a protected cell");
            require(level.getBlockState(pos).is(Blocks.WATER), "Recovery island must replace generated deep water only");
            level.setBlock(pos, depth == 1 ? Blocks.GRASS_BLOCK.defaultBlockState() : Blocks.DIRT.defaultBlockState(), 3);
        }
        return end;
    }

    static BlockPos expectedCamp(BlockPos island) {
        return CampLoading.localCandidate(island, 0, true);
    }

    static NativeCampFixture.Fixture addBoundarySpike(ServerLevel level, NativeCampFixture.Fixture fixture, BlockPos island) {
        // The first genuine local site's outer survey boundary contains a steep,
        // pre-existing raw-rock rise. Its core and three other exits stay flat.
        // Strict global smoothing rejects this EDGE; preservation-aware grading
        // must choose this actual site while leaving all four stones untouched.
        BlockPos base = expectedCamp(island).offset(16, 0, 0);
        var protectedCells = new LinkedHashMap<>(fixture.protectedCells());
        for (int y = 0; y < 4; y++) {
            BlockPos pos = base.above(y);
            require(level.getBlockState(pos).isAir(), "Boundary spike must replace fixture air only");
            level.setBlock(pos, Blocks.STONE.defaultBlockState(), 3);
            protectedCells.put(pos, level.getBlockState(pos));
        }
        return new NativeCampFixture.Fixture(fixture.world(), fixture.faction(), fixture.core(), fixture.surface(),
                fixture.claimIds(), Map.copyOf(protectedCells), fixture.protectedContainers());
    }

    static Map<String, Object> evidence(BlockPos center, long completedAt, long recoveryAt) {
        return Map.of("center", center.toShortString(), "width", WIDTH, "soilDepth", WATER_DEPTH,
                "fixtureWrites", ISLAND_WRITES + 4, "completedAtGameTime", completedAt,
                "recoveryCooldownAtGameTime", recoveryAt, "originalCandidatesCannotFitCore", true,
                "protectedBoundarySpike", expectedCamp(center).offset(16, 0, 0).toShortString(),
                "setup", "Finite fully supported soil island and four-block raw-stone boundary rise added during initial scouting, before either original pass exhausts; no camp site or raid state is forced");
    }
    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
