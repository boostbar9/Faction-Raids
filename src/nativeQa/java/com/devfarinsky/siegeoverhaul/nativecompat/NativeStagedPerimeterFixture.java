package com.devfarinsky.siegeoverhaul.nativecompat;

import com.devfarinsky.siegeoverhaul.compat.WorkersBridge;
import com.devfarinsky.siegeoverhaul.core.CoreBlocks;
import com.devfarinsky.siegeoverhaul.core.PerimeterBlueprint;
import com.devfarinsky.siegeoverhaul.core.PerimeterSteppedProfile;
import com.devfarinsky.siegeoverhaul.core.PerimeterSteppedTopology;
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
    static final int FLAT_SURFACE_Y = 65, LOWERED_SURFACE_Y = 64;
    static final List<BlockPos> TERRAIN_LOWERED_BAND = loweredBand();
    static final List<BlockPos> TERRAIN_FILL_DIPS = fillDips();
    static final int COBBLE = 3000, OAK = 2000, DIRT = 256;
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
            Map<String, Object> step = NativeStagedPerimeterFixture.stepEvidence(reviewed);
            require(Boolean.TRUE.equals(step.get("loweredBandAtExpectedBase")), "Production plan filled the lowered band instead of stepping down");
            require(Boolean.TRUE.equals(step.get("transitionClearanceVerified")), "Production plan lacks required one-block transition clearance");
            Map<BlockPos, BlockState> nonPlan = new LinkedHashMap<>();
            for (int x = 124; x <= 211; x++) for (int z = -4; z <= 83; z++)
                for (int y = 63; y <= 72; y++) {
                    BlockPos cell = new BlockPos(x, y, z);
                    if (!reviewed.blocks().containsKey(cell.asLong())) nonPlan.put(cell, level.getBlockState(cell));
                }
            return new Fixture(builderId, storageIds, claimIds, reviewed, buildGoal, storageGoal,
                    Map.copyOf(nonPlan), parkedAuxiliaries);
        }
        Map<String, Object> stepEvidence() { return NativeStagedPerimeterFixture.stepEvidence(plan); }
        Map<String, Object> completedStepStandingEvidence(ServerLevel level, BuilderEntity builder) {
            return NativeStagedPerimeterFixture.completedStepStandingEvidence(plan, level, builder);
        }
    }

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
        for (BlockPos dip : terrainCuts()) {
            require(level.getBlockState(dip).is(Blocks.STONE) && level.getBlockState(dip.above()).isAir(),
                    "Fixture lowered terrain does not start as one-block natural stone: " + dip);
            level.setBlock(dip, Blocks.AIR.defaultBlockState(), 3);
            require(level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                    dip.getX(), dip.getZ()) == dip.getY(), "Fixture lowered terrain was not exposed as one-block lower ground: " + dip);
        }
        level.setDayTime(6000);
        owner.teleportTo(level, 167.5, 65, 37.5, 0, 20);
        FactionEvents.createTeam(false, owner, level, FACTION, "Native Staged QA", owner.getScoreboardName(),
                new ItemStack(Items.BLUE_BANNER), ChatFormatting.BLUE, (byte) 11);
        var faction = FactionEvents.recruitsFactionManager.getFactionByStringID(FACTION);
        require(faction != null && owner.getTeam() != null && FACTION.equals(owner.getTeam().getName()),
                "Native faction membership was not indexed");
        RecruitsClaim claim = new RecruitsClaim("Native Staged Stepped Claim", faction);
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
            // Only unrelated starter NPCs are parked, far outside the native build/travel envelope.
            BlockPos park = auxiliaryParking(i);
            require(!TERRITORY.contains(new ChunkPos(park)) && !park.equals(CORE) && !CHESTS.contains(park),
                    "Auxiliary parking must stay outside the claim and fixture work cells");
            mob.moveTo(park.getX() + 0.5, park.getY(), park.getZ() + 0.5, 0, 0);
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
        builder.moveTo(169.5, 65, 39.5, 0, 0);
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

    private static List<BlockPos> terrainCuts() {
        var cuts = new java.util.TreeSet<BlockPos>(java.util.Comparator.comparingInt((BlockPos pos) -> pos.getX())
                .thenComparingInt(pos -> pos.getY()).thenComparingInt(pos -> pos.getZ()));
        cuts.addAll(TERRAIN_LOWERED_BAND); cuts.addAll(TERRAIN_FILL_DIPS);
        return List.copyOf(cuts);
    }

    private static BlockPos auxiliaryParking(int index) {
        return new BlockPos(116 + index % 5 * 2, FLAT_SURFACE_Y, 88 + index / 5 * 2);
    }

    private static List<BlockPos> loweredBand() {
        var topology = PerimeterSteppedTopology.create(topologyClaim());
        require(topology.valid(), topology.problem());
        for (var loop : topology.loops()) if (loop.outer()) {
            var bands = loop.bands();
            for (int i = 0; i < bands.size(); i++) {
                var band = bands.get(i);
                var previous = bands.get((i + bands.size() - 1) % bands.size());
                var next = bands.get((i + 1) % bands.size());
                if (band.kind() == PerimeterSteppedProfile.Kind.STRAIGHT
                        && previous.kind() == PerimeterSteppedProfile.Kind.STRAIGHT
                        && next.kind() == PerimeterSteppedProfile.Kind.STRAIGHT)
                    return band.cells().stream().sorted()
                            .map(cell -> new BlockPos(cell.x(), LOWERED_SURFACE_Y, cell.z())).toList();
            }
        }
        throw new AssertionError("Fixture claim lacks a complete straight band that can step between straight seams");
    }

    private static List<BlockPos> fillDips() {
        Set<Long> lowered = TERRAIN_LOWERED_BAND.stream().map(pos -> xz(pos).asLong()).collect(java.util.stream.Collectors.toSet());
        var topology = PerimeterSteppedTopology.create(topologyClaim());
        for (var loop : topology.loops()) if (loop.outer()) for (var band : loop.bands())
            if (band.kind() == PerimeterSteppedProfile.Kind.STRAIGHT) {
                var cells = band.cells().stream().sorted()
                        .filter(cell -> !lowered.contains(new BlockPos(cell.x(), 0, cell.z()).asLong()))
                        .limit(3).map(cell -> new BlockPos(cell.x(), LOWERED_SURFACE_Y, cell.z())).toList();
                if (cells.size() == 3) return cells;
            }
        throw new AssertionError("Fixture claim lacks separate fill-dip cells");
    }

    private static Set<PerimeterSteppedTopology.Chunk> topologyClaim() {
        var claim = new java.util.TreeSet<PerimeterSteppedTopology.Chunk>();
        TERRITORY.forEach(chunk -> claim.add(new PerimeterSteppedTopology.Chunk(chunk.x, chunk.z)));
        return Set.copyOf(claim);
    }

    private static Map<String, Object> stepEvidence(PerimeterBlueprint.Plan plan) {
        require(plan != null && plan.valid(), "Step evidence requires a valid plan");
        Map<Long, PerimeterBlueprint.Column> columns = new LinkedHashMap<>();
        plan.columns().forEach(column -> columns.put(xz(column.base()).asLong(), column));
        var baseLevels = new java.util.TreeSet<Integer>();
        plan.columns().forEach(column -> baseLevels.add(column.base().getY()));
        int loweredAtExpectedBase = 0;
        for (BlockPos lowered : TERRAIN_LOWERED_BAND) {
            PerimeterBlueprint.Column column = columns.get(xz(lowered).asLong());
            require(column != null, "Lowered fixture band was not part of the production plan: " + lowered);
            if (column.base().getY() == LOWERED_SURFACE_Y) loweredAtExpectedBase++;
        }
        var transitions = new java.util.TreeSet<String>();
        int transitionClearance = 0;
        for (var column : plan.columns()) for (Direction direction : List.of(Direction.EAST, Direction.SOUTH)) {
            PerimeterBlueprint.Column other = columns.get(xz(column.base().relative(direction)).asLong());
            if (other == null || Math.abs(other.base().getY() - column.base().getY()) != 1) continue;
            int y = Math.min(column.base().getY(), other.base().getY()) + 6;
            BlockPos a = column.base().atY(y), b = other.base().atY(y);
            require(plan.clearance().contains(a.asLong()) && plan.clearance().contains(b.asLong()),
                    "Non-level transition lacks persistent movement clearance: " + a + " / " + b);
            transitionClearance++;
            transitions.add(column.base().toShortString() + "<->" + other.base().toShortString() + " jumpY=" + y);
        }
        require(loweredAtExpectedBase == TERRAIN_LOWERED_BAND.size(), "Not every lowered band column retained the expected base");
        require(baseLevels.contains(FLAT_SURFACE_Y) && baseLevels.contains(LOWERED_SURFACE_Y),
                "Fixture plan did not retain distinct flat and lowered deck bases");
        require(transitionClearance > 0, "Fixture plan has no non-level transition");
        return Map.of("loweredBandCells", TERRAIN_LOWERED_BAND.size(),
                "fillDipCells", TERRAIN_FILL_DIPS.size(),
                "baseLevels", List.copyOf(baseLevels),
                "nonLevelTransitionCount", transitionClearance,
                "transitions", List.copyOf(transitions),
                "loweredBandAtExpectedBase", true,
                "transitionClearanceVerified", true);
    }

    private static Map<String, Object> completedStepStandingEvidence(PerimeterBlueprint.Plan plan, ServerLevel level,
                                                                     BuilderEntity builder) {
        require(plan != null && plan.valid() && level != null && builder != null, "Completed step evidence requires a valid world and builder");
        Map<Long, PerimeterBlueprint.Column> columns = new LinkedHashMap<>();
        plan.columns().forEach(column -> columns.put(xz(column.base()).asLong(), column));
        for (var column : plan.columns()) {
            if (!walkLane(column)) continue;
            for (Direction direction : List.of(Direction.EAST, Direction.SOUTH)) {
                PerimeterBlueprint.Column other = columns.get(xz(column.base().relative(direction)).asLong());
                if (other == null || !walkLane(other) || Math.abs(other.base().getY() - column.base().getY()) != 1)
                    continue;
                PerimeterBlueprint.Column lower = column.base().getY() < other.base().getY() ? column : other;
                PerimeterBlueprint.Column higher = lower == column ? other : column;
                BlockPos lowerDeck = lower.base().above(3), higherDeck = higher.base().above(3);
                BlockPos lowerFeet = lower.base().above(4), higherFeet = higher.base().above(4);
                require(level.getBlockState(lowerDeck).is(Blocks.OAK_PLANKS)
                                && level.getBlockState(higherDeck).is(Blocks.OAK_PLANKS),
                        "Completed non-level walk seam is missing oak deck blocks");
                require(air(level, lowerFeet) && air(level, lowerFeet.above()) && air(level, lowerFeet.above(2))
                                && air(level, higherFeet) && air(level, higherFeet.above()),
                        "Completed non-level walk seam lacks body/jump clearance");
                require(noCollisionAt(level, builder, lowerFeet) && noCollisionAt(level, builder, higherFeet),
                        "Native builder shape cannot stand on both completed non-level seam decks");
                Map<String, Object> evidence = new LinkedHashMap<>();
                evidence.put("verified", true);
                evidence.put("lowerDeck", lowerDeck.toShortString());
                evidence.put("higherDeck", higherDeck.toShortString());
                evidence.put("lowerFeet", lowerFeet.toShortString());
                evidence.put("higherFeet", higherFeet.toShortString());
                evidence.put("lowerFeetY", lowerFeet.getY());
                evidence.put("higherFeetY", higherFeet.getY());
                evidence.put("jumpClearanceY", lower.base().above(6).getY());
                evidence.put("direction", direction.getName());
                evidence.put("lowerCollisionFree", true);
                evidence.put("higherCollisionFree", true);
                return Map.copyOf(evidence);
            }
        }
        throw new AssertionError("No completed non-level walk seam with standing evidence was found");
    }

    private static boolean walkLane(PerimeterBlueprint.Column column) {
        return column.inwardDistance() >= 2 && column.inwardDistance() <= 4;
    }

    private static boolean air(ServerLevel level, BlockPos pos) { return level.getBlockState(pos).isAir(); }

    private static boolean noCollisionAt(ServerLevel level, BuilderEntity builder, BlockPos feet) {
        AABB box = builder.getBoundingBox().move(Vec3.atBottomCenterOf(feet).subtract(builder.position()));
        return level.noCollision(builder, box);
    }

    private static BlockPos xz(BlockPos pos) { return new BlockPos(pos.getX(), 0, pos.getZ()); }

    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
