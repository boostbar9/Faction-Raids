package com.devfarinsky.siegeoverhaul.compat;

import com.devfarinsky.siegeoverhaul.core.PerimeterBlueprint;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import org.junit.jupiter.api.Test;

import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class FactionTerritoryTest {
    private record Claim(String owner, Collection<?> chunks) {}
    private final RecruitsClaimsBridge.TerritoryReader reader = new RecruitsClaimsBridge.TerritoryReader() {
        public String owner(Object claim) { return ((Claim) claim).owner(); }
        public Collection<?> chunks(Object claim) { return ((Claim) claim).chunks(); }
    };
    private RecruitsClaimsBridge.TerritorySnapshot collect(Collection<?> claims, int limit) throws Exception {
        return RecruitsClaimsBridge.collectTerritory(claims, "team:blue", limit, reader);
    }

    @Test void allSameFactionRecordsAreUnionedIncludingIslandsAndOverlappingChunks() throws Exception {
        var a = new ChunkPos(-2, 0); var b = new ChunkPos(-1, 0); var island = new ChunkPos(2, 2);
        var result = collect(List.of(new Claim("blue", Set.of(a, b)), new Claim("blue", Set.of(b, island)),
                new Claim("red", Set.of(new ChunkPos(0, 0)))), 4096);
        assertTrue(result.ready()); assertEquals("blue", result.factionStringId());
        assertEquals(Set.of(a, b, island), result.chunks());
        assertThrows(UnsupportedOperationException.class, () -> result.chunks().clear());
    }

    @Test void separateAdjacentClaimRecordsHaveNoInternalWallAndAnIslandIsNeverLost() throws Exception {
        var result = collect(List.of(new Claim("blue", Set.of(new ChunkPos(0, 0))),
                new Claim("blue", Set.of(new ChunkPos(1, 0))),
                new Claim("blue", Set.of(new ChunkPos(3, 0)))), 4096);
        var plan = PerimeterBlueprint.create(result.chunks(), (x, z) -> PerimeterBlueprint.Surface.ready(64),
                PerimeterBlueprint.Palette.COBBLESTONE);
        assertTrue(plan.valid());
        for (int x = 11; x <= 20; x++) assertFalse(plan.blocks().containsKey(new BlockPos(x, 64, 8).asLong()));
        assertTrue(plan.blocks().containsKey(new BlockPos(48, 64, 8).asLong()));
        assertEquals(Set.of(0, 1), plan.columns().stream().map(PerimeterBlueprint.Column::componentId)
                .collect(java.util.stream.Collectors.toSet()));
    }

    @Test void holeAcrossMultipleRecordsRetainsItsInnerBoundary() throws Exception {
        List<Claim> claims = new ArrayList<>();
        for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++)
            if (x != 0 || z != 0) claims.add(new Claim("blue", Set.of(new ChunkPos(x, z))));
        var result = collect(claims, 4096);
        var plan = PerimeterBlueprint.create(result.chunks(), (x, z) -> PerimeterBlueprint.Surface.ready(64),
                PerimeterBlueprint.Palette.OAK);
        assertTrue(plan.valid());
        assertTrue(plan.blocks().containsKey(new BlockPos(-1, 64, 8).asLong()));
        assertFalse(plan.blocks().keySet().stream().anyMatch(p -> new ChunkPos(BlockPos.of(p)).equals(new ChunkPos(0, 0))));
    }

    @Test void overBudgetWholeUnionReturnsNoPartialTerritory() throws Exception {
        var result = collect(List.of(new Claim("blue", Set.of(new ChunkPos(0, 0))),
                new Claim("blue", Set.of(new ChunkPos(1, 0)))), 1);
        assertFalse(result.ready()); assertTrue(result.chunks().isEmpty()); assertNotNull(result.problem());
    }

    @Test void invalidOrAbsentFriendlyChunksCannotBecomeAReadyPartialPlan() throws Exception {
        for (Collection<?> claims : List.of(List.of(new Claim("red", Set.of(new ChunkPos(0, 0)))),
                List.of(new Claim("blue", Arrays.asList(new ChunkPos(0, 0), null))),
                List.of(new Claim("blue", List.of("not a chunk"))),
                List.of(new Claim("blue", Set.of(new ChunkPos(0, 0))), new Claim("blue", null)))) {
            var result = collect(claims, 4096);
            assertFalse(result.ready()); assertTrue(result.chunks().isEmpty());
        }
    }

    @Test void unresolvedOwnersCannotSilentlyDisappearFromACompleteUnion() throws Exception {
        for (String owner : Arrays.asList(null, "", "  ")) {
            var result = collect(List.of(new Claim("blue", Set.of(new ChunkPos(0, 0))),
                    new Claim(owner, Set.of(new ChunkPos(1, 0)))), 4096);
            assertFalse(result.ready()); assertTrue(result.chunks().isEmpty());
        }
    }

    @Test void enemyChunkCollectionsAreNotReadAndRegistryBoundsPrecedeReading() throws Exception {
        var hostile = new Claim("red", null);
        assertTrue(collect(List.of(hostile, new Claim("blue", Set.of(new ChunkPos(0, 0)))), 1).ready());
        var oversized = Collections.nCopies(65537, new Claim("blue", Set.of(new ChunkPos(0, 0))));
        assertFalse(collect(oversized, 4096).ready());
    }
}
