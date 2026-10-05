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

/** Isolated stepped/gated fixture translated +2048 X away from vanilla spawn tickets.
 * Terrain, finite stock and original goals match NativeStagedPerimeterFixture.
 * This fixture never changes spawn or adds/removes force tickets. */
final class NativeStagedUnloadFixture {
    static final String WORLD = "siege-native-staged-unload";
    static final String FACTION = "native_unload";
    static final Set<ChunkPos> TERRITORY = territory();
    static final BlockPos CORE = new BlockPos(2214, 65, 39);
    static final List<BlockPos> CHESTS = List.of(new BlockPos(2211, 65, 35), new BlockPos(2219, 65, 35),
            new BlockPos(2211, 65, 43), new BlockPos(2219, 65, 43));
    static final List<BlockPos> TERRAIN_DIPS = List.of(new BlockPos(2182, 64, 2),
            new BlockPos(2183, 64, 2), new BlockPos(2249, 64, 77));
    static final int COBBLE = 3000, OAK = 2000, DIRT = 256;
    static final AABB BOUNDS = new AABB(2160, 63, -16, 2272, 82, 96);

    private static Set<ChunkPos> territory() {
        var chunks = new java.util.HashSet<ChunkPos>();
        for (int x = 136; x <= 140; x++) for (int z = 0; z <= 4; z++) chunks.add(new ChunkPos(x, z));
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
                   Map<BlockPos, BlockState> nonPlanCells, List<String> parkedAuxiliaries) {
        Fixture withPlan(PerimeterBlueprint.Plan reviewed, ServerLevel level) {
            require(reviewed != null && reviewed.valid(), "Production reviewed plan is not buildable");
            for (long packed : reviewed.blocks().keySet()) {
                BlockPos cell = BlockPos.of(packed);
                require(TERRITORY.contains(new ChunkPos(cell)) && level.getBlockState(cell).isAir(),
                        "Plan target leaves actual claim or starts prebuilt: " + cell);
                require(NativeConstructionGuard.neighborhoodProblem(level, cell) == null,
                        "Fixture puts a reactive/protected neighbor inside the native safety envelope at " + cell);
            }
            require(!reviewed.blocks().containsKey(CORE.asLong()) && CHESTS.stream().noneMatch(p -> reviewed.blocks().containsKey(p.asLong())),
                    "Core/chest overlaps stepped perimeter");
            Map<BlockPos, BlockState> nonPlan = new LinkedHashMap<>();
            for (int x = 2172; x <= 2259; x++) for (int z = -4; z <= 83; z++)
                for (int y = 63; y <= 72; y++) {
                    BlockPos cell = new BlockPos(x, y, z);
                    if (!reviewed.blocks().containsKey(cell.asLong())) nonPlan.put(cell, level.getBlockState(cell));
                }
            return new Fixture(builderId, storageIds, claimIds, reviewed, buildGoal, storageGoal,
                    Map.copyOf(nonPlan), parkedAuxiliaries);
        }
    }

    private NativeStagedUnloadFixture() {}

    static Fixture setup(ServerLevel level, ServerPlayer owner) throws Exception {
        require(!com.talhanation.recruits.config.RecruitsServerConfig.RecruitsChunkLoading.get(),
                "This explicit unload fixture requires its own loaded RecruitsChunkLoading=false config before any native NPC/worker spawn");
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
        for (int x = 135; x <= 141; x++) for (int z = -1; z <= 5; z++) level.getChunk(x, z);
        require(level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE, 2215, 40) == 65,
                "Generated fixture footing is not at the declared height");
        for (BlockPos dip : TERRAIN_DIPS) {
            require(level.getBlockState(dip).is(Blocks.STONE) && level.getBlockState(dip.above()).isAir(),
                    "Fixture dip does not start as one-block natural stone");
            level.setBlock(dip, Blocks.AIR.defaultBlockState(), 3);
            require(level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                    dip.getX(), dip.getZ()) == dip.getY(), "Fixture dip was not exposed as one-block lower ground");
        }
        level.setDayTime(6000);
        owner.teleportTo(level, 2215.5, 65, 37.5, 0, 20);
        FactionEvents.createTeam(false, owner, level, FACTION, "Native Unload QA", owner.getScoreboardName(),
                new ItemStack(Items.BLUE_BANNER), ChatFormatting.BLUE, (byte) 11);
        var faction = FactionEvents.recruitsFactionManager.getFactionByStringID(FACTION);
        require(faction != null && owner.getTeam() != null && FACTION.equals(owner.getTeam().getName()),
                "Native faction membership was not indexed");
        RecruitsClaim claim = new RecruitsClaim("Native Unload Stepped Claim", faction);
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
            mob.moveTo(2193.5 + i % 5 * 2, 65, 30.5 + i / 5 * 2, 0, 0);
            mob.getNavigation().stop(); mob.setNoAi(true); parked.add(mob.getUUID().toString()); i++;
        }

        List<UUID> storageIds = new ArrayList<>();
        for (BlockPos chestPos : CHESTS) {
            level.setBlock(chestPos, Blocks.CHEST.defaultBlockState(), 3);
            require(level.getBlockEntity(chestPos) instanceof Container, "Fixture chest unavailable");
            Container chest = (Container) level.getBlockEntity(chestPos);
            require(chest.isEmpty() && chest.getContainerSize() == 27, "Fixture requires four empty separate single chests");
            int slot = 0;
            // Bounded surplus only. No chest is replenished; final accounting proves conservation.
            Map<net.minecraft.world.item.Item, Integer> stock = new LinkedHashMap<>();
            stock.put(Items.COBBLESTONE, COBBLE / 4); stock.put(Items.OAK_PLANKS, OAK / 4); stock.put(Items.DIRT, DIRT / 4);
            for (var material : stock.entrySet()) {
                int remaining = material.getValue();
                while (remaining > 0) {
                    int count = Math.min(64, remaining);
                    chest.setItem(slot++, new ItemStack(material.getKey(), count)); remaining -= count;
                }
            }
            require(slot == 21, "Unexpected finite stock packing");
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
        builder.moveTo(2217.5, 65, 39.5, 0, 0);
        builder.finalizeSpawn(level, level.getCurrentDifficultyAt(builder.blockPosition()), MobSpawnType.COMMAND, null, null);
        WorkersBridge.enablePlayerJob(builder, owner.getUUID()); builder.setPersistenceRequired();
        builder.setNoAi(true); // Only to keep it clear of the preview while the user-plan packet is in flight.
        require(builder.getInventory().getContainerSize() >= 10
                && builder.getInventory().countItem(Items.COBBLESTONE) == 0
                && builder.getInventory().countItem(Items.OAK_PLANKS) == 0
                && builder.getInventory().countItem(Items.DIRT) == 0
                && !builder.getMainHandItem().is(Items.COBBLESTONE) && !builder.getMainHandItem().is(Items.OAK_PLANKS)
                && !builder.getMainHandItem().is(Items.DIRT),
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

        return new Fixture(builder.getUUID(), List.copyOf(storageIds), List.of(claim.getUUID()), null, buildGoal, storageGoal,
                Map.of(), List.copyOf(parked));
    }

    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
