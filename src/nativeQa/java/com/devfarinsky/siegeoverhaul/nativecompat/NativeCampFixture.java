package com.devfarinsky.siegeoverhaul.nativecompat;

import com.devfarinsky.siegeoverhaul.camp.CampLoading;
import com.devfarinsky.siegeoverhaul.core.CoreBlocks;
import com.devfarinsky.siegeoverhaul.core.SiegeCore;
import com.devfarinsky.siegeoverhaul.items.ModItems;
import com.talhanation.recruits.ClaimEvents;
import com.talhanation.recruits.FactionEvents;
import com.talhanation.recruits.world.RecruitsClaim;
import com.talhanation.recruits.world.RecruitsPlayerInfo;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.FlatLevelSource;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.WorldDimensions;
import net.minecraft.world.level.levelgen.flat.FlatLayerInfo;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.*;

/** Finite, labeled test terrain and defender setup. Production alone schedules and establishes camps. */
final class NativeCampFixture {
    static final List<ChunkPos> CLAIMS = List.of(new ChunkPos(0, 0), new ChunkPos(-2, 0));
    record Fixture(String world, String faction, BlockPos core, int surface,
                   List<UUID> claimIds, Map<BlockPos, BlockState> protectedCells,
                   Map<BlockPos, CompoundTag> protectedContainers) {}
    record Landing(Fixture fixture, Map<String, Object> evidence) {}
    private NativeCampFixture() {}

    static WorldDimensions dimensions(RegistryAccess access, boolean hostile) {
        WorldDimensions dimensions = access.registryOrThrow(Registries.WORLD_PRESET)
                .getOrThrow(WorldPresets.FLAT).createWorldDimensions();
        FlatLevelSource original = (FlatLevelSource) dimensions.overworld();
        var layers = List.of(new FlatLayerInfo(1, Blocks.BEDROCK), new FlatLayerInfo(60, Blocks.DIRT),
                new FlatLayerInfo(hostile ? 3 : 1, hostile ? Blocks.WATER : Blocks.GRASS_BLOCK));
        var settings = original.settings().withBiomeAndLayers(layers, Optional.empty(), original.settings().getBiome());
        return dimensions.replaceOverworldGenerator(access, new FlatLevelSource(settings));
    }

    static Fixture setup(ServerLevel level, ServerPlayer owner, String world, boolean hostile) throws Exception {
        require(level.getServer().isSameThread() && world.equals(level.getServer().getWorldData().getLevelName())
                && world.startsWith("siege-native-camp-"), "Refusing a non-camp-fixture world");
        require(owner != null && !owner.isCreative() && !owner.isSpectator() && !owner.hasPermissions(2)
                && owner.mayBuild() && owner.getTeam() == null, "Camp actor must be a fresh non-op Survival player");
        require(FactionEvents.recruitsFactionManager != null && ClaimEvents.recruitsClaimManager != null,
                "Native camp fixture managers are unavailable");
        String factionId = hostile ? "native_camp_water" : "native_camp_flat";
        require(FactionEvents.recruitsFactionManager.getFactionByStringID(factionId) == null,
                "Refusing an existing camp fixture faction");
        int surface = level.getHeight(Heightmap.Types.WORLD_SURFACE, 8, 8);
        if (hostile) {
            require(level.getBlockState(new BlockPos(8, surface - 1, 8)).is(Blocks.WATER), "Hostile world has no generated shallow water");
            for (int x = -7; x <= 23; x++) for (int z = -7; z <= 23; z++) dryColumn(level, new BlockPos(x, surface, z));
            for (int x = -25; x <= -21; x++) for (int z = 7; z <= 11; z++) dryColumn(level, new BlockPos(x, surface, z));
        }
        BlockPos core = new BlockPos(8, surface, 8);
        level.setBlock(core.below(), Blocks.STONE.defaultBlockState(), 3); // Stable fixture support, unaffected by random grass spread beneath the core.
        owner.teleportTo(level, 8.5, surface, 5.5, 0, 20); level.setDayTime(6000);
        FactionEvents.createTeam(false, owner, level, factionId, "Native Camp QA " + (hostile ? "Water" : "Flat"),
                owner.getScoreboardName(), new ItemStack(Items.BLUE_BANNER), ChatFormatting.BLUE, (byte)11);
        var faction = FactionEvents.recruitsFactionManager.getFactionByStringID(factionId);
        require(faction != null && owner.getTeam() != null && owner.getTeam().getName().equals(factionId), "Actual camp defender faction was not created");
        var claimIds = new ArrayList<UUID>();
        for (ChunkPos chunk : CLAIMS) {
            require(ClaimEvents.recruitsClaimManager.getClaim(chunk) == null, "Refusing an existing defender/neighbor claim");
            var claim = new RecruitsClaim("Protected camp QA " + chunk, faction);
            claim.setCenter(chunk); claim.addChunk(chunk);
            claim.setPlayer(new RecruitsPlayerInfo(owner.getUUID(), owner.getScoreboardName(), faction));
            claim.setHealth(claim.getMaxHealth());
            ClaimEvents.recruitsClaimManager.addOrUpdateClaim(level, claim);
            require(ClaimEvents.recruitsClaimManager.getClaim(chunk) == claim, "Native protected claim index missing");
            claimIds.add(claim.getUUID());
        }
        ClaimEvents.recruitsClaimManager.save(level);
        ItemStack item = new ItemStack(ModItems.SIEGE_CORE.get());
        var context = new BlockPlaceContext(owner, InteractionHand.MAIN_HAND, item,
                new BlockHitResult(Vec3.atBottomCenterOf(core), Direction.UP, core.below(), false));
        require(((BlockItem)item.getItem()).place(context).consumesAction() && level.getBlockState(core).is(CoreBlocks.CORE.get()),
                "Actual CoreItem placement failed");
        ((CoreBlocks.CoreBlock) CoreBlocks.CORE.get()).tick(level.getBlockState(core), level, core, level.random);
        require(SiegeCore.point(owner.server, SiegeCore.key(owner)) != null, "Actual defender core/anchor flow failed");
        // Only defender starter NPCs are parked. The subsequently spawned enemy crew is untouched.
        for (Mob mob : level.getEntitiesOfClass(Mob.class, new AABB(core).inflate(24))) {
            mob.getNavigation().stop(); mob.setNoAi(true);
        }
        Map<BlockPos, CompoundTag> containers = new LinkedHashMap<>();
        for (BlockPos pos : List.of(core.offset(3, 0, 2), new BlockPos(-23, surface, 9))) {
            level.setBlock(pos.below(), Blocks.STONE.defaultBlockState(), 3);
            level.setBlock(pos, Blocks.CHEST.defaultBlockState(), 3);
            Container chest = (Container)level.getBlockEntity(pos);
            var treasure = new ItemStack(Items.DIAMOND, 3); treasure.getOrCreateTag().putString("CampQaProtected", world);
            chest.setItem(0, treasure); chest.setChanged();
            containers.put(pos, level.getBlockEntity(pos).saveWithFullMetadata().copy());
        }
        level.setBlock(core.offset(-3, 0, 2), Blocks.STONE_BRICKS.defaultBlockState(), 3);
        level.setBlock(core.offset(-3, 1, 2), Blocks.OAK_LOG.defaultBlockState(), 3);
        level.setBlock(core.offset(-3, -1, 2), Blocks.STONE.defaultBlockState(), 3);
        Map<BlockPos, BlockState> protectedCells = new LinkedHashMap<>();
        for (ChunkPos chunk : CLAIMS) for (int x = chunk.getMinBlockX(); x <= chunk.getMaxBlockX(); x++)
            for (int z = chunk.getMinBlockZ(); z <= chunk.getMaxBlockZ(); z++) for (int y = surface - 3; y <= surface + 8; y++) {
                BlockPos pos = new BlockPos(x, y, z); protectedCells.put(pos, level.getBlockState(pos));
            }
        return new Fixture(world, factionId, core, surface, List.copyOf(claimIds), Map.copyOf(protectedCells), Map.copyOf(containers));
    }

    static Landing hostileLanding(ServerLevel level, Fixture fixture, BlockPos scout, boolean rocky) {
        require(CampLoading.ready(level, scout), "Hostile landing setup must use the actually loaded native scout neighborhood");
        BlockPos center = CampLoading.localCandidate(scout, 0, true).atY(fixture.surface());
        int changes = 0;
        // The first expanded candidate sits near the northwest edge; an east-facing landing stays inside the loaded neighborhood.
        for (int distance = 13; distance <= 27; distance++) for (int side = -1; side <= 1; side++) {
            dryColumn(level, center.offset(distance, 0, side)); changes += 3;
        }
        BlockPos tree = center.offset(3, 0, -3);
        for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++) { dryColumn(level, tree.offset(x, 0, z)); changes += 3; }
        changes += tree(level, tree);
        changes += tree(level, center.offset(17, 0, 0));
        // Unclaimed pre-existing player storage/structure forces safe local rejection/selection,
        // independently of the defender/neighbor claim exclusions. It is outside the next local site's earthworks.
        BlockPos protectedChest = center.offset(-14, 0, 0);
        dryColumn(level, protectedChest); level.setBlock(protectedChest.below(), Blocks.STONE.defaultBlockState(), 3);
        level.setBlock(protectedChest, Blocks.CHEST.defaultBlockState(), 3);
        Container chest = (Container)level.getBlockEntity(protectedChest);
        chest.setItem(0, new ItemStack(Items.DIAMOND, 3)); chest.setChanged();
        BlockPos protectedWall = protectedChest.offset(0, 0, 1);
        dryColumn(level, protectedWall); level.setBlock(protectedWall.below(), Blocks.STONE.defaultBlockState(), 3);
        level.setBlock(protectedWall, Blocks.STONE_BRICKS.defaultBlockState(), 3);
        changes += 10;
        Map<BlockPos, BlockState> protectedCells = new LinkedHashMap<>(fixture.protectedCells());
        protectedCells.put(protectedChest, level.getBlockState(protectedChest));
        protectedCells.put(protectedWall, level.getBlockState(protectedWall));
        Map<BlockPos, CompoundTag> containers = new LinkedHashMap<>(fixture.protectedContainers());
        containers.put(protectedChest, level.getBlockEntity(protectedChest).saveWithFullMetadata().copy());
        // An exposed one-block rock rise is inside every expanded local site's
        // core. The median water plane would quarry it; the camp must safely
        // raise its plane and retain this exact original support instead.
        BlockPos rock = scout.atY(fixture.surface());
        if (rocky) {
            dryColumn(level, rock);
            level.setBlock(rock, Blocks.STONE.defaultBlockState(), 3);
            protectedCells.put(rock, level.getBlockState(rock));
            changes += 4;
        }
        Fixture updated = new Fixture(fixture.world(), fixture.faction(), fixture.core(), fixture.surface(),
                fixture.claimIds(), Map.copyOf(protectedCells), Map.copyOf(containers));
        require(changes < 300, "Hostile terrain setup exceeded its bounded cell count");
        Map<String,Object> evidence = new LinkedHashMap<>();
        evidence.put("scout",scout.toShortString()); evidence.put("intendedFallbackCenter",center.toShortString());
        evidence.put("surface",fixture.surface()); evidence.put("waterDepth",3);
        evidence.put("dryLandingWidth",3); evidence.put("fixtureWrites",changes);
        evidence.put("unclaimedProtectedChest",protectedChest.toShortString());
        evidence.put("terrain","Vanilla generated shallow-water layers plus bounded dry landing and supported oak log/leaf fixture; no raid state changes");
        evidence.put("rockyRise",rocky);
        if(rocky)evidence.put("unchangedRockSupport",rock.toShortString());
        return new Landing(updated,Map.copyOf(evidence));
    }

    static void verifyProtected(ServerLevel level, Fixture fixture) {
        for (var entry : fixture.protectedCells().entrySet()) require(level.getBlockState(entry.getKey()).equals(entry.getValue()),
                "Camp mutated a protected defender/neighbor cell at " + entry.getKey());
        for (var entry : fixture.protectedContainers().entrySet()) require(level.getBlockEntity(entry.getKey()) != null
                        && level.getBlockEntity(entry.getKey()).saveWithFullMetadata().equals(entry.getValue()),
                "Camp mutated protected chest contents/NBT at " + entry.getKey());
        for (int i = 0; i < CLAIMS.size(); i++) require(ClaimEvents.recruitsClaimManager.getClaim(CLAIMS.get(i)) != null
                        && fixture.claimIds().get(i).equals(ClaimEvents.recruitsClaimManager.getClaim(CLAIMS.get(i)).getUUID()),
                "Camp replaced a protected native claim");
    }

    private static void dryColumn(ServerLevel level, BlockPos air) {
        for (int depth = 1; depth <= 3; depth++) level.setBlock(air.below(depth),
                depth == 1 ? Blocks.GRASS_BLOCK.defaultBlockState() : Blocks.DIRT.defaultBlockState(), 3);
    }
    private static int tree(ServerLevel level, BlockPos base) {
        require(level.getBlockState(base.below()).is(Blocks.GRASS_BLOCK), "Fixture tree lacks rooted natural soil");
        int count = 0;
        for (int y = 0; y < 4; y++) { level.setBlock(base.above(y), Blocks.OAK_LOG.defaultBlockState(), 3); count++; }
        for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++) for (int y = 3; y <= 4; y++)
            if (x != 0 || z != 0 || y == 4) {
                level.setBlock(base.offset(x, y, z), Blocks.OAK_LEAVES.defaultBlockState()
                        .setValue(LeavesBlock.PERSISTENT, false).setValue(LeavesBlock.DISTANCE, 1), 3); count++;
            }
        return count;
    }
    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
