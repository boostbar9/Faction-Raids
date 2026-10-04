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
final class NativeStagedPerimeterFixture {
    static final String WORLD = "siege-native-staged-perimeter";
    static final String FACTION = "native_staged";
    static final Set<ChunkPos> TERRITORY = territory();
    static final BlockPos CORE = new BlockPos(166, 65, 39);
    static final List<BlockPos> CHESTS = List.of(new BlockPos(163, 65, 35), new BlockPos(171, 65, 35),
            new BlockPos(163, 65, 43), new BlockPos(171, 65, 43));
    static final int COBBLE = 2400, OAK = 1500, BLOCKS = 3900;
    static final AABB BOUNDS = new AABB(112, 63, -16, 224, 82, 96);

    private static Set<ChunkPos> territory() {
        var chunks = new java.util.HashSet<ChunkPos>();
        for (int x = 8; x <= 12; x++) for (int z = 0; z <= 4; z++) chunks.add(new ChunkPos(x, z));
        return Set.copyOf(chunks);
    }

    static net.minecraft.world.level.levelgen.WorldDimensions dimensions(net.minecraft.core.RegistryAccess access) {
        var dimensions = access.registryOrThrow(net.minecraft.core.registries.Registries.WORLD_PRESET)
                .getOrThrow(net.minecraft.world.level.levelgen.presets.WorldPresets.FLAT).createWorldDimensions();
        var original = (net.minecraft.world.level.levelgen.FlatLevelSource) dimensions.overworld();
        var layers = List.of(new net.minecraft.world.level.levelgen.flat.FlatLayerInfo(1, Blocks.BEDROCK),
                new net.minecraft.world.level.levelgen.flat.FlatLayerInfo(128, Blocks.STONE));
        var settings = original.settings().withBiomeAndLayers(layers, java.util.Optional.empty(), original.settings().getBiome());
        return dimensions.replaceOverworldGenerator(access, new net.minecraft.world.level.levelgen.FlatLevelSource(settings));
    }

    record Fixture(UUID builderId, List<UUID> storageIds, List<UUID> claimIds, PerimeterBlueprint.Plan plan,
                   BuilderWorkGoal buildGoal, GetNeededItemsFromStorage storageGoal,
                   Map<BlockPos, BlockState> nonPlanCells, List<String> parkedAuxiliaries) {}

    private NativeStagedPerimeterFixture() {}

    static Fixture setup(ServerLevel level, ServerPlayer owner) throws Exception {
        require(level != null && level.dimension().equals(Level.OVERWORLD) && level.getServer().isSameThread()
                && WORLD.equals(level.getServer().getWorldData().getLevelName()), "Refusing a non-fixture world/thread");
        require(owner != null && owner.serverLevel() == level && !owner.isCreative() && !owner.isSpectator()
                && !owner.hasPermissions(2) && owner.mayBuild(), "Requires a real non-op survival owner");
        require(owner.getTeam() == null && FactionEvents.recruitsFactionManager != null
                && ClaimEvents.recruitsClaimManager != null, "Native faction/claim managers are unavailable or owner already joined");
        require(FactionEvents.recruitsFactionManager.getFactionByStringID(FACTION) == null
                && TERRITORY.stream().allMatch(c -> ClaimEvents.recruitsClaimManager.getClaim(c) == null), "Refusing an existing faction/claim");

        // Vanilla custom superflat generates the stable stone footing everywhere. Load the bounded
        // fixture apron once before review; normal player simulation distance owns loading thereafter.
        for (int x = 7; x <= 13; x++) for (int z = -1; z <= 5; z++) level.getChunk(x, z);
        require(level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE, 167, 40) == 65,
                "Generated fixture footing is not at the declared height");
        level.setDayTime(6000);
        owner.teleportTo(level, 167.5, 65, 37.5, 0, 20);
        FactionEvents.createTeam(false, owner, level, FACTION, "Native Staged QA", owner.getScoreboardName(),
                new ItemStack(Items.BLUE_BANNER), ChatFormatting.BLUE, (byte) 11);
        var faction = FactionEvents.recruitsFactionManager.getFactionByStringID(FACTION);
        require(faction != null && owner.getTeam() != null && FACTION.equals(owner.getTeam().getName()),
                "Native faction membership was not indexed");
        RecruitsClaim claim = new RecruitsClaim("Native Staged 5x5 Claim", faction);
        claim.setCenter(new ChunkPos(CORE));
        claim.setPlayer(new RecruitsPlayerInfo(owner.getUUID(), owner.getScoreboardName(), faction));
        TERRITORY.forEach(claim::addChunk); claim.setHealth(claim.getMaxHealth());
        ClaimEvents.recruitsClaimManager.addOrUpdateClaim(level, claim);
        require(TERRITORY.stream().allMatch(c -> ClaimEvents.recruitsClaimManager.getClaim(c) == claim),
                "The complete native 25-chunk claim was not indexed");
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
            // Only unrelated starter NPCs are parked, safely inside the open courtyard.
            mob.moveTo(145.5 + i % 5 * 2, 65, 30.5 + i / 5 * 2, 0, 0);
            mob.getNavigation().stop(); mob.setNoAi(true); parked.add(mob.getUUID().toString()); i++;
        }

        List<UUID> storageIds = new ArrayList<>();
        for (BlockPos chestPos : CHESTS) {
            level.setBlock(chestPos, Blocks.CHEST.defaultBlockState(), 3);
            require(level.getBlockEntity(chestPos) instanceof Container, "Fixture chest unavailable");
            Container chest = (Container) level.getBlockEntity(chestPos);
            require(chest.isEmpty() && chest.getContainerSize() == 27, "Fixture requires four empty separate single chests");
            int slot = 0;
            // Each exact quarter is 600 cobble + 375 oak: 16 slots. No chest is replenished.
            for (var material : Map.of(Items.COBBLESTONE, COBBLE / 4, Items.OAK_PLANKS, OAK / 4).entrySet()) {
                int remaining = material.getValue();
                while (remaining > 0) {
                    int count = Math.min(64, remaining);
                    chest.setItem(slot++, new ItemStack(material.getKey(), count)); remaining -= count;
                }
            }
            require(slot == 16, "Unexpected finite stock packing");
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
        builder.moveTo(169.5, 65, 39.5, 0, 0);
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
                "Expected flat 5x5 perimeter totals changed");
        NativeHollowWallOracle.assertFlatPlan(plan, TERRITORY);
        NativeHollowWallOracle.assertCavitiesAir(level, TERRITORY);
        for (long cell : plan.blocks().keySet()) require(TERRITORY.contains(new ChunkPos(BlockPos.of(cell)))
                && level.getBlockState(BlockPos.of(cell)).isAir(), "Plan leaves actual claim or starts prebuilt");
        require(!plan.blocks().containsKey(CORE.asLong()) && CHESTS.stream().noneMatch(p -> plan.blocks().containsKey(p.asLong())),
                "Core/chest overlaps five-wide ring");
        // Containers and the core must also clear production's two-cell reactive-neighbor guard.
        // The central supplies in this 70-wide courtyard are well outside every wall's neighbor envelope.
        for (long cell : plan.blocks().keySet()) require(
                NativeConstructionGuard.neighborhoodProblem(level, BlockPos.of(cell)) == null,
                "Fixture puts a reactive/protected neighbor inside the native safety envelope at " + BlockPos.of(cell));
        Map<Long, String> oracle = independentOracle();
        require(plan.blocks().equals(oracle) && plan.columns().size() == 1500 && plan.runs().size() == 4,
                "Production compiler differs from independent distance-to-unowned 5x5 oracle");
        for (long cell : plan.clearance()) require(TERRITORY.contains(new ChunkPos(BlockPos.of(cell))),
                "Reserved clearance escaped actual territory");
        assertInternalBordersOpen(level, oracle);
        Map<BlockPos, BlockState> nonPlan = new LinkedHashMap<>();
        for (int x = 124; x <= 211; x++) for (int z = -4; z <= 83; z++)
            for (int y = 64; y <= 71; y++) {
                BlockPos cell = new BlockPos(x, y, z);
                if (!plan.blocks().containsKey(cell.asLong())) nonPlan.put(cell, level.getBlockState(cell));
            }
        return new Fixture(builder.getUUID(), List.copyOf(storageIds), List.of(claim.getUUID()), plan, buildGoal, storageGoal,
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
                if (distance == 1 || distance == 5)
                    for (int dy = 0; dy < 3; dy++) cells.put(new BlockPos(x, 65 + dy, z).asLong(), "minecraft:cobblestone");
                cells.put(new BlockPos(x, 68, z).asLong(), "minecraft:oak_planks");
                if (distance == 1 || distance == 5) cells.put(new BlockPos(x, 69, z).asLong(), "minecraft:cobblestone");
            }
        return Map.copyOf(cells);
    }

    static void assertInternalBordersOpen(ServerLevel level, Map<Long, String> oracle) {
        // All interior chunk seams stay open. Only the global five-wide ring may contain targets.
        for (int x : List.of(143, 144, 159, 160, 175, 176, 191, 192))
            for (int z = 5; z <= 74; z++) for (int y = 65; y <= 70; y++) {
                BlockPos cell = new BlockPos(x, y, z);
                require(!oracle.containsKey(cell.asLong()) && level.getBlockState(cell).isAir(), "Internal east-west chunk seam was walled: " + cell);
            }
        for (int z : List.of(15, 16, 31, 32, 47, 48, 63, 64))
            for (int x = 133; x <= 202; x++) for (int y = 65; y <= 70; y++) {
                BlockPos cell = new BlockPos(x, y, z);
                require(!oracle.containsKey(cell.asLong()) && level.getBlockState(cell).isAir(), "Internal north-south chunk seam was walled: " + cell);
            }
    }

    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
