package com.devfarinsky.siegeoverhaul.nativecompat;

import com.devfarinsky.siegeoverhaul.compat.WorkersBridge;
import com.devfarinsky.siegeoverhaul.core.CoreBlocks;
import com.devfarinsky.siegeoverhaul.core.PerimeterBlueprint;
import com.devfarinsky.siegeoverhaul.core.SiegeCore;
import com.devfarinsky.siegeoverhaul.items.ModItems;
import com.talhanation.recruits.ClaimEvents;
import com.talhanation.recruits.FactionEvents;
import com.talhanation.recruits.world.RecruitsClaim;
import com.talhanation.recruits.world.RecruitsPlayerInfo;
import com.talhanation.workers.entities.BuilderEntity;
import com.talhanation.workers.entities.ai.BuilderWorkGoal;
import com.talhanation.workers.entities.ai.GetNeededItemsFromStorage;
import com.talhanation.workers.entities.workarea.StorageArea;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Only isolated terrain/faction/core/finite stock setup. Production creates the paid build area. */
final class NativeTerritoryPerimeterFixture {
    static final String WORLD = "siege-native-territory-perimeter";
    static final String FACTION = "native_territory";
    static final List<ChunkPos> CORE_CLAIM = List.of(new ChunkPos(8, 0), new ChunkPos(9, 0));
    static final ChunkPos SECOND_CLAIM = new ChunkPos(8, 1), EXPANSION = new ChunkPos(9, 1);
    static final Set<ChunkPos> TERRITORY = Set.of(CORE_CLAIM.get(0), CORE_CLAIM.get(1), SECOND_CLAIM);
    static final BlockPos CORE = new BlockPos(135, 65, 7);
    static final List<BlockPos> CHESTS = List.of(new BlockPos(136, 65, 8), new BlockPos(151, 65, 8));
    static final int COBBLE = 1836, OAK = 540, BLOCKS = 2376;
    static final AABB BOUNDS = new AABB(104, 63, -24, 184, 80, 56);

    record Fixture(UUID builderId, List<UUID> storageIds, List<UUID> claimIds, PerimeterBlueprint.Plan plan,
                   BuilderWorkGoal buildGoal, GetNeededItemsFromStorage storageGoal,
                   Map<BlockPos, BlockState> nonPlanCells, List<String> parkedAuxiliaries) {}

    private NativeTerritoryPerimeterFixture() {}

    static Fixture setup(ServerLevel level, ServerPlayer owner) throws Exception {
        require(level != null && level.dimension().equals(Level.OVERWORLD) && level.getServer().isSameThread()
                && WORLD.equals(level.getServer().getWorldData().getLevelName()), "Refusing a non-fixture world/thread");
        require(owner != null && owner.serverLevel() == level && !owner.isCreative() && !owner.isSpectator()
                && !owner.hasPermissions(2) && owner.mayBuild(), "Requires a real non-op survival owner");
        require(owner.getTeam() == null && FactionEvents.recruitsFactionManager != null
                && ClaimEvents.recruitsClaimManager != null, "Native faction/claim managers are unavailable or owner already joined");
        require(FactionEvents.recruitsFactionManager.getFactionByStringID(FACTION) == null
                && TERRITORY.stream().allMatch(c -> ClaimEvents.recruitsClaimManager.getClaim(c) == null)
                && ClaimEvents.recruitsClaimManager.getClaim(EXPANSION) == null, "Refusing an existing faction/claim");

        // 24-block natural-ground apron around all four sides: no cliff at the claim edge.
        // Chunk loads and terrain writes happen once before review, never during native work.
        for (int x = 104; x <= 183; x++) for (int z = -24; z <= 55; z++) {
            level.setBlock(new BlockPos(x, 64, z), Blocks.STONE.defaultBlockState(), 3);
            for (int y = 65; y <= 75; y++) level.setBlock(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState(), 3);
        }
        level.setDayTime(6000);
        owner.teleportTo(level, 136.5, 65, 5.5, 0, 20);
        FactionEvents.createTeam(false, owner, level, FACTION, "Native Territory QA", owner.getScoreboardName(),
                new ItemStack(Items.BLUE_BANNER), ChatFormatting.BLUE, (byte) 11);
        var faction = FactionEvents.recruitsFactionManager.getFactionByStringID(FACTION);
        require(faction != null && owner.getTeam() != null && FACTION.equals(owner.getTeam().getName()),
                "Native faction membership was not indexed");
        RecruitsClaim claim = new RecruitsClaim("Native Territory Core Claim", faction);
        claim.setCenter(CORE_CLAIM.get(0));
        claim.setPlayer(new RecruitsPlayerInfo(owner.getUUID(), owner.getScoreboardName(), faction));
        CORE_CLAIM.forEach(claim::addChunk); claim.setHealth(claim.getMaxHealth());
        ClaimEvents.recruitsClaimManager.addOrUpdateClaim(level, claim);
        RecruitsClaim second = new RecruitsClaim("Native Territory Separate Claim", faction);
        second.setCenter(SECOND_CLAIM);
        second.setPlayer(new RecruitsPlayerInfo(owner.getUUID(), owner.getScoreboardName(), faction));
        second.addChunk(SECOND_CLAIM); second.setHealth(second.getMaxHealth());
        ClaimEvents.recruitsClaimManager.addOrUpdateClaim(level, second);
        require(!claim.getUUID().equals(second.getUUID())
                && CORE_CLAIM.stream().allMatch(c -> ClaimEvents.recruitsClaimManager.getClaim(c) == claim)
                && ClaimEvents.recruitsClaimManager.getClaim(SECOND_CLAIM) == second,
                "Two separate native same-faction claim records were not indexed");
        ClaimEvents.recruitsClaimManager.save(level);

        ItemStack core = new ItemStack(ModItems.SIEGE_CORE.get());
        var placement = new BlockPlaceContext(owner, InteractionHand.MAIN_HAND, core,
                new BlockHitResult(Vec3.atBottomCenterOf(CORE), Direction.UP, CORE.below(), false));
        require(((BlockItem) core.getItem()).place(placement).consumesAction()
                && level.getBlockState(CORE).is(CoreBlocks.CORE.get())
                && SiegeCore.point(owner.server, SiegeCore.key(owner)) != null, "Production core placement failed");
        ((CoreBlocks.CoreBlock) CoreBlocks.CORE.get()).tick(level.getBlockState(CORE), level, CORE, level.random);
        List<String> parked = new ArrayList<>();
        int i = 0;
        for (Mob mob : level.getEntitiesOfClass(Mob.class, new AABB(CORE).inflate(16))) {
            // Unrelated starter NPCs only; outside both the wall and the candidate-builder radius.
            mob.moveTo(107.5 + i % 5 * 2, 65, -20.5 + i / 5 * 2, 0, 0);
            mob.getNavigation().stop(); mob.setNoAi(true); parked.add(mob.getUUID().toString()); i++;
        }

        List<UUID> storageIds = new ArrayList<>();
        for (BlockPos chestPos : CHESTS) {
            level.setBlock(chestPos, Blocks.CHEST.defaultBlockState(), 3);
            require(level.getBlockEntity(chestPos) instanceof Container, "Fixture chest unavailable");
            Container chest = (Container) level.getBlockEntity(chestPos);
            require(chest.isEmpty() && chest.getContainerSize() == 27, "Fixture requires two empty separate single chests");
            int slot = 0;
            // Each exact half requires 15 cobble stacks + 5 oak stacks. Neither is replenished.
            for (var material : Map.of(Items.COBBLESTONE, COBBLE / 2, Items.OAK_PLANKS, OAK / 2).entrySet()) {
                int remaining = material.getValue();
                while (remaining > 0) {
                    int count = Math.min(64, remaining);
                    chest.setItem(slot++, new ItemStack(material.getKey(), count)); remaining -= count;
                }
            }
            require(slot == 20, "Unexpected finite stock packing");
            chest.setChanged(); // Initial fixture supply only, never a native-transfer/reload workaround.
            var raw = WorkersBridge.createPlayerArea(level, "storagearea", chestPos, owner.getUUID(),
                    owner.getScoreboardName(), 1, 1, 1);
            require(raw instanceof StorageArea, "Unsupported native storage type");
            StorageArea storage = (StorageArea) raw;
            require(level.addFreshEntity(storage), "Native storage spawn failed");
            storage.scanStorageBlocks();
            require(storage.storageMap.size() == 1 && storage.storageMap.containsKey(chestPos), "Storage did not scan its actual chest");
            storageIds.add(storage.getUUID());
        }

        var type = ForgeRegistries.ENTITY_TYPES.getValue(new ResourceLocation("workers", "builder"));
        require(type != null, "Native builder registry unavailable");
        var builderRaw = type.create(level);
        require(builderRaw instanceof BuilderEntity, "Native builder creation failed");
        BuilderEntity builder = (BuilderEntity) builderRaw;
        builder.moveTo(134.5, 65, 9.5, 0, 0);
        builder.finalizeSpawn(level, level.getCurrentDifficultyAt(builder.blockPosition()), MobSpawnType.COMMAND, null, null);
        WorkersBridge.enablePlayerJob(builder, owner.getUUID()); builder.setPersistenceRequired();
        builder.setNoAi(true); // Only to keep it clear of the preview while the user-plan packet is in flight.
        require(builder.getInventory().getContainerSize() >= 10
                && builder.getInventory().countItem(Items.COBBLESTONE) == 0
                && builder.getInventory().countItem(Items.OAK_PLANKS) == 0
                && !builder.getMainHandItem().is(Items.COBBLESTONE) && !builder.getMainHandItem().is(Items.OAK_PLANKS),
                "Unexpected initial construction material");
        builder.getInventory().setItem(6, new ItemStack(Items.BREAD, 64));
        builder.getInventory().setItem(7, new ItemStack(Items.DIAMOND_PICKAXE));
        builder.getInventory().setItem(8, new ItemStack(Items.DIAMOND_AXE));
        builder.getInventory().setItem(9, new ItemStack(Items.DIAMOND_SHOVEL));
        builder.getInventory().setChanged();
        require(level.addFreshEntity(builder), "Native builder spawn failed");
        for (UUID storageId : storageIds) require(((StorageArea) level.getEntity(storageId)).canWorkHere(builder),
                "Native builder/storage ownership failed");
        // Hold the actual native goal objects for public read-only diagnostics after production wraps them.
        BuilderWorkGoal buildGoal = builder.goalSelector.getAvailableGoals().stream().map(g -> g.getGoal())
                .filter(BuilderWorkGoal.class::isInstance).map(BuilderWorkGoal.class::cast).findFirst().orElseThrow();
        GetNeededItemsFromStorage storageGoal = builder.goalSelector.getAvailableGoals().stream().map(g -> g.getGoal())
                .filter(GetNeededItemsFromStorage.class::isInstance).map(GetNeededItemsFromStorage.class::cast).findFirst().orElseThrow();

        PerimeterBlueprint.Plan plan = PerimeterBlueprint.create(TERRITORY,
                (x, z) -> PerimeterBlueprint.Surface.ready(65), PerimeterBlueprint.Palette.COBBLESTONE);
        require(plan.valid() && plan.blocks().size() == BLOCKS
                && plan.materialCounts().equals(Map.of("minecraft:cobblestone", COBBLE, "minecraft:oak_planks", OAK)),
                "Expected flat L-territory totals changed");
        for (long cell : plan.blocks().keySet()) require(TERRITORY.contains(new ChunkPos(BlockPos.of(cell)))
                && level.getBlockState(BlockPos.of(cell)).isAir(), "Plan leaves actual claim or starts prebuilt");
        require(!plan.blocks().containsKey(CORE.asLong()) && CHESTS.stream().noneMatch(p -> plan.blocks().containsKey(p.asLong())),
                "Core/chest overlaps five-wide ring");
        // Containers and the core must also clear production's two-cell reactive-neighbor guard.
        // In a six-wide courtyard, only the central two rows/columns are far enough from both walls.
        for (long cell : plan.blocks().keySet()) require(
                NativeConstructionGuard.neighborhoodProblem(level, BlockPos.of(cell)) == null,
                "Fixture puts a reactive/protected neighbor inside the native safety envelope at " + BlockPos.of(cell));
        Map<Long, String> oracle = independentOracle();
        require(plan.blocks().equals(oracle) && plan.columns().size() == 540 && plan.runs().size() == 6,
                "Production compiler differs from independent distance-to-unowned L-territory oracle");
        for (long cell : plan.clearance()) require(TERRITORY.contains(new ChunkPos(BlockPos.of(cell))),
                "Reserved clearance escaped actual territory");
        assertInternalBordersOpen(level, oracle);
        Map<BlockPos, BlockState> nonPlan = new LinkedHashMap<>();
        for (int x = 122; x <= 165; x++) for (int z = -6; z <= 37; z++)
            for (int y = 64; y <= 71; y++) {
                BlockPos cell = new BlockPos(x, y, z);
                if (!plan.blocks().containsKey(cell.asLong())) nonPlan.put(cell, level.getBlockState(cell));
            }
        return new Fixture(builder.getUUID(), List.copyOf(storageIds), List.of(claim.getUUID(), second.getUUID()), plan, buildGoal, storageGoal,
                Map.copyOf(nonPlan), List.copyOf(parked));
    }

    /** Independent geometric oracle: five Chebyshev layers inward from any unowned column. */
    static Map<Long, String> independentOracle() {
        Map<Long, String> cells = new LinkedHashMap<>();
        for (ChunkPos chunk : TERRITORY) for (int x = chunk.getMinBlockX(); x <= chunk.getMaxBlockX(); x++)
            for (int z = chunk.getMinBlockZ(); z <= chunk.getMaxBlockZ(); z++) {
                int distance = 6;
                for (int dx = -5; dx <= 5; dx++) for (int dz = -5; dz <= 5; dz++)
                    if (!TERRITORY.contains(new ChunkPos(new BlockPos(x + dx, 65, z + dz))))
                        distance = Math.min(distance, Math.max(Math.abs(dx), Math.abs(dz)));
                if (distance > 5) continue;
                for (int dy = 0; dy < 3; dy++) cells.put(new BlockPos(x, 65 + dy, z).asLong(), "minecraft:cobblestone");
                cells.put(new BlockPos(x, 68, z).asLong(), "minecraft:oak_planks");
                if (distance == 1 || distance == 5) cells.put(new BlockPos(x, 69, z).asLong(), "minecraft:cobblestone");
            }
        return Map.copyOf(cells);
    }

    static void assertInternalBordersOpen(ServerLevel level, Map<Long, String> oracle) {
        // Endpoints near the external perimeter legitimately contain wall. The central corridors must not.
        for (int x : List.of(143, 144)) for (int z = 5; z <= 10; z++) for (int y = 65; y <= 70; y++) {
            BlockPos cell = new BlockPos(x, y, z);
            require(!oracle.containsKey(cell.asLong()) && level.getBlockState(cell).isAir(), "Internal east-west claim border was walled: " + cell);
        }
        for (int z : List.of(15, 16)) for (int x = 133; x <= 138; x++) for (int y = 65; y <= 70; y++) {
            BlockPos cell = new BlockPos(x, y, z);
            require(!oracle.containsKey(cell.asLong()) && level.getBlockState(cell).isAir(), "Internal north-south claim border was walled: " + cell);
        }
    }

    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
