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
final class NativeStagedHandoffFixture {
    static final String WORLD = "siege-native-staged-handoff";
    static final String FACTION = "native_handoff";
    static final ChunkPos CLAIM_CHUNK = new ChunkPos(8, 0);
    static final BlockPos CORE = new BlockPos(135, 65, 7);
    static final BlockPos CHEST = new BlockPos(136, 65, 8);
    static final Set<ChunkPos> TERRITORY = Set.of(CLAIM_CHUNK);
    static final List<BlockPos> CHESTS = List.of(CHEST);
    // New-plan oracle from the approved hollow profile in acbc66025e09152db75ab419b34fad81a3dd290e.
    // These are this fixture's finite stock totals, never controller rules or saved-plan reinterpretation.
    static final int COBBLE = 352, OAK = 220, BLOCKS = 572;
    static final int CAVITY_AIR = 396, HEADROOM_AIR = 352, RESERVED = 1320;
    static final String GEOMETRY_SOURCE = "acbc66025e09152db75ab419b34fad81a3dd290e";
    static final AABB BOUNDS = new AABB(104, 63, -24, 168, 80, 40);

    record Fixture(UUID builderId, List<UUID> storageIds, List<UUID> claimIds, PerimeterBlueprint.Plan plan, HollowOracle oracle,
                   BuilderWorkGoal buildGoal, GetNeededItemsFromStorage storageGoal,
                   Map<BlockPos, BlockState> nonPlanCells, List<String> parkedAuxiliaries,
                   Map<UUID, BlockPos> isolatedAuxiliaries, Map<BlockPos, BlockState> isolationCells) {}

    private NativeStagedHandoffFixture() {}

    static Fixture setup(ServerLevel level, ServerPlayer owner) throws Exception {
        require(level != null && level.dimension().equals(Level.OVERWORLD) && level.getServer().isSameThread()
                && WORLD.equals(level.getServer().getWorldData().getLevelName()), "Refusing a non-fixture world/thread");
        require(owner != null && owner.serverLevel() == level && !owner.isCreative() && !owner.isSpectator()
                && !owner.hasPermissions(2) && owner.mayBuild(), "Requires a real non-op survival owner");
        require(owner.getTeam() == null && FactionEvents.recruitsFactionManager != null
                && ClaimEvents.recruitsClaimManager != null, "Native faction/claim managers are unavailable or owner already joined");
        require(FactionEvents.recruitsFactionManager.getFactionByStringID(FACTION) == null
                && ClaimEvents.recruitsClaimManager.getClaim(CLAIM_CHUNK) == null, "Refusing an existing faction/claim");

        // 24-block natural-ground apron around all four sides: no cliff at the claim edge.
        // Chunk loads and terrain writes happen once before review, never during native work.
        for (int x = 104; x <= 167; x++) for (int z = -24; z <= 39; z++) {
            level.setBlock(new BlockPos(x, 64, z), Blocks.STONE.defaultBlockState(), 3);
            for (int y = 65; y <= 75; y++) level.setBlock(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState(), 3);
        }
        level.setDayTime(6000);
        owner.teleportTo(level, 136.5, 65, 5.5, 0, 20);
        FactionEvents.createTeam(false, owner, level, FACTION, "Native Handoff QA", owner.getScoreboardName(),
                new ItemStack(Items.BLUE_BANNER), ChatFormatting.BLUE, (byte) 11);
        var faction = FactionEvents.recruitsFactionManager.getFactionByStringID(FACTION);
        require(faction != null && owner.getTeam() != null && FACTION.equals(owner.getTeam().getName()),
                "Native faction membership was not indexed");
        RecruitsClaim claim = new RecruitsClaim("Native Handoff QA", faction);
        claim.setCenter(CLAIM_CHUNK);
        claim.setPlayer(new RecruitsPlayerInfo(owner.getUUID(), owner.getScoreboardName(), faction));
        claim.addChunk(CLAIM_CHUNK); claim.setHealth(claim.getMaxHealth());
        ClaimEvents.recruitsClaimManager.addOrUpdateClaim(level, claim);
        require(ClaimEvents.recruitsClaimManager.getClaim(CLAIM_CHUNK) == claim, "Native one-chunk claim was not indexed");
        ClaimEvents.recruitsClaimManager.save(level);

        ItemStack core = new ItemStack(ModItems.SIEGE_CORE.get());
        var placement = new BlockPlaceContext(owner, InteractionHand.MAIN_HAND, core,
                new BlockHitResult(Vec3.atBottomCenterOf(CORE), Direction.UP, CORE.below(), false));
        require(((BlockItem) core.getItem()).place(placement).consumesAction()
                && level.getBlockState(CORE).is(CoreBlocks.CORE.get())
                && SiegeCore.point(owner.server, SiegeCore.key(owner)) != null, "Production core placement failed");
        ((CoreBlocks.CoreBlock) CoreBlocks.CORE.get()).tick(level.getBlockState(CORE), level, CORE, level.random);
        List<String> parked = new ArrayList<>();
        Map<UUID, BlockPos> isolated = new LinkedHashMap<>();
        Map<BlockPos, BlockState> isolationCells = new LinkedHashMap<>();
        int i = 0;
        for (Mob mob : level.getEntitiesOfClass(Mob.class, new AABB(CORE).inflate(16))) {
            // Keep replacement-fixture civilians inside their claim: native faction
            // recovery otherwise returns outside parked NoAI actors into the west
            // construction approach. Only pre-review unrelated fixture actors move.
            if (Boolean.getBoolean("siegeoverhaul.nativeQa.sharedCrew")) {
                // Setup-only isolation: NoAI does not disable CoreCivilians' outside-claim
                // recovery. A sealed same-claim cell avoids both that return and ground
                // route collisions, without overhead blocks changing the heightmap.
                require(i < 4 && mob.getBbWidth() <= .8F && mob.getBbHeight() <= 2F,
                        "Unexpected auxiliary count/dimensions for isolated fixture parking");
                BlockPos feet = new BlockPos(134 + (i % 2) * 3, 48, 6 + (i / 2) * 3);
                for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
                    BlockPos cap = feet.offset(dx, 16, dz);
                    require(level.getBlockState(cap).is(Blocks.STONE), "Auxiliary cell lacks sealed ground separation");
                    isolationCells.put(cap, level.getBlockState(cap));
                    for (int dy = -1; dy <= 2; dy++) {
                        BlockPos cell = feet.offset(dx, dy, dz);
                        require(level.hasChunkAt(cell) && CLAIM_CHUNK.equals(new ChunkPos(cell))
                                && level.getBlockState(cell).isAir(), "Auxiliary holding cell is not clear same-claim fixture space");
                        BlockState state = dx == 0 && dz == 0 && (dy == 0 || dy == 1)
                                ? Blocks.AIR.defaultBlockState() : Blocks.STONE.defaultBlockState();
                        level.setBlock(cell, state, 3); isolationCells.put(cell, state);
                    }
                }
                mob.moveTo(feet.getX() + .5, feet.getY(), feet.getZ() + .5, 0, 0);
                mob.setDeltaMovement(0, 0, 0);
                require(SiegeCore.claimed(level, feet, SiegeCore.key(owner)) && level.noCollision(mob),
                        "Auxiliary cell is not safe inside its original claim");
                isolated.put(mob.getUUID(), feet);
            } else if (Boolean.getBoolean("siegeoverhaul.nativeQa.builderReplacement"))
                mob.moveTo(136.5+i%3,65,10.5-i/3,0,0);
            else mob.moveTo(107.5 + i % 5 * 2, 65, -20.5 + i / 5 * 2, 0, 0);
            mob.getNavigation().stop(); mob.setNoAi(true); parked.add(mob.getUUID().toString()); i++;
        }

        level.setBlock(CHEST, Blocks.CHEST.defaultBlockState(), 3);
        require(level.getBlockEntity(CHEST) instanceof Container, "Fixture chest unavailable");
        Container chest = (Container) level.getBlockEntity(CHEST);
        require(chest.isEmpty() && chest.getContainerSize() == 27, "Fixture requires one empty real chest");
        int slot = 0;
        for (var material : Map.of(Items.COBBLESTONE, COBBLE, Items.OAK_PLANKS, OAK).entrySet()) {
            int remaining = material.getValue();
            while (remaining > 0) {
                int count = Math.min(64, remaining);
                chest.setItem(slot++, new ItemStack(material.getKey(), count)); remaining -= count;
            }
        }
        chest.setChanged();
        var storageRaw = WorkersBridge.createPlayerArea(level, "storagearea", CHEST, owner.getUUID(),
                owner.getScoreboardName(), 1, 1, 1);
        require(storageRaw instanceof StorageArea, "Unsupported native storage type");
        StorageArea storage = (StorageArea) storageRaw;
        require(level.addFreshEntity(storage), "Native storage spawn failed");
        storage.scanStorageBlocks();
        require(storage.storageMap.size() == 1 && storage.storageMap.containsKey(CHEST), "Storage did not scan the actual chest");

        var type = ForgeRegistries.ENTITY_TYPES.getValue(new ResourceLocation("workers", "builder"));
        require(type != null, "Native builder registry unavailable");
        var builderRaw = type.create(level);
        require(builderRaw instanceof BuilderEntity, "Native builder creation failed");
        BuilderEntity builder = (BuilderEntity) builderRaw;
        builder.moveTo(134.5, 65, 9.5, 0, 0);
        builder.finalizeSpawn(level, level.getCurrentDifficultyAt(builder.blockPosition()), MobSpawnType.COMMAND, null, null);
        WorkersBridge.enablePlayerJob(builder, owner.getUUID()); builder.setPersistenceRequired();
        builder.setNoAi(true); // Initial fixture hold only; released immediately after the direct server commission.
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
        require(level.addFreshEntity(builder) && storage.canWorkHere(builder), "Native builder/storage ownership failed");
        // Hold the actual native goal objects for public read-only diagnostics after production wraps them.
        BuilderWorkGoal buildGoal = builder.goalSelector.getAvailableGoals().stream().map(g -> g.getGoal())
                .filter(BuilderWorkGoal.class::isInstance).map(BuilderWorkGoal.class::cast).findFirst().orElseThrow();
        GetNeededItemsFromStorage storageGoal = builder.goalSelector.getAvailableGoals().stream().map(g -> g.getGoal())
                .filter(GetNeededItemsFromStorage.class::isInstance).map(GetNeededItemsFromStorage.class::cast).findFirst().orElseThrow();

        PerimeterBlueprint.Plan plan = PerimeterBlueprint.create(Set.of(CLAIM_CHUNK),
                (x, z) -> PerimeterBlueprint.Surface.ready(65), PerimeterBlueprint.Palette.COBBLESTONE);
        require(plan.valid() && plan.blocks().size() == BLOCKS
                && plan.materialCounts().equals(Map.of("minecraft:cobblestone", COBBLE, "minecraft:oak_planks", OAK)),
                "Independent one-claim perimeter oracle changed");
        HollowOracle oracle = independentHollowOracle();
        require(plan.blocks().equals(oracle.targets()) && plan.clearance().equals(oracle.clearance())
                && plan.columns().size() == oracle.columns(), "Production new plan differs from the independent hollow skin/deck/cavity oracle");
        assertHollowAir(level, oracle);
        for (long cell : plan.blocks().keySet()) require(CLAIM_CHUNK.equals(new ChunkPos(BlockPos.of(cell)))
                && level.getBlockState(BlockPos.of(cell)).isAir(), "Plan leaves actual claim or starts prebuilt");
        require(!plan.blocks().containsKey(CORE.asLong()) && !plan.blocks().containsKey(CHEST.asLong()),
                "Core/chest overlaps five-wide ring");
        // Containers and the core must also clear production's two-cell reactive-neighbor guard.
        // In a six-wide courtyard, only the central two rows/columns are far enough from both walls.
        for (long cell : plan.blocks().keySet()) require(
                NativeConstructionGuard.neighborhoodProblem(level, BlockPos.of(cell)) == null,
                "Fixture puts a reactive/protected neighbor inside the native safety envelope at " + BlockPos.of(cell));
        Map<BlockPos, BlockState> nonPlan = new LinkedHashMap<>();
        for (int x = 122; x <= 149; x++) for (int z = -6; z <= 21; z++)
            for (int y = 64; y <= 71; y++) {
                BlockPos cell = new BlockPos(x, y, z);
                if (!plan.blocks().containsKey(cell.asLong())) nonPlan.put(cell, level.getBlockState(cell));
            }
        for (var entry : isolationCells.entrySet()) {
            require(!plan.blocks().containsKey(entry.getKey().asLong()) && !plan.clearance().contains(entry.getKey().asLong()),
                    "Auxiliary setup overlaps reviewed targets or reserved clearance");
            nonPlan.put(entry.getKey(), entry.getValue());
        }
        return new Fixture(builder.getUUID(), List.of(storage.getUUID()), List.of(claim.getUUID()), plan, oracle, buildGoal, storageGoal,
                Map.copyOf(nonPlan), List.copyOf(parked), Map.copyOf(isolated), Map.copyOf(isolationCells));
    }

    /** Passive checks only: never relocate an auxiliary after the setup/commission boundary. */
    static Map<String, Object> auxiliaryIsolationSnapshot(ServerLevel level, Fixture fixture) {
        require(!fixture.isolatedAuxiliaries().isEmpty() && fixture.isolatedAuxiliaries().size() <= 4
                && fixture.parkedAuxiliaries().size() == fixture.isolatedAuxiliaries().size()
                && fixture.isolatedAuxiliaries().keySet().stream().map(UUID::toString).collect(java.util.stream.Collectors.toSet())
                    .equals(Set.copyOf(fixture.parkedAuxiliaries())), "Auxiliary isolation identities changed");
        // Native standing searches are at most six columns out and twelve blocks down/up.
        var workEnvelope = new AABB(fixture.plan().min(), fixture.plan().max().offset(1, 1, 1)).inflate(8, 12, 8);
        var actors = new ArrayList<Map<String, Object>>();
        for (var entry : fixture.isolatedAuxiliaries().entrySet().stream().sorted(Map.Entry.comparingByKey()).toList()) {
            require(level.getEntity(entry.getKey()) instanceof Mob, "Isolated auxiliary is unloaded or missing");
            Mob mob = (Mob) level.getEntity(entry.getKey()); BlockPos feet = entry.getValue();
            AABB body = mob.getBoundingBox();
            require(mob.isAlive() && mob.isNoAi() && mob.level() == level
                    && mob.position().distanceToSqr(Vec3.atBottomCenterOf(feet)) < 1.0E-12
                    && body.minY == 48 && body.maxY <= 50 && body.minX >= feet.getX() && body.maxX <= feet.getX() + 1
                    && body.minZ >= feet.getZ() && body.maxZ <= feet.getZ() + 1 && !body.intersects(workEnvelope)
                    && SiegeCore.claimed(level, feet, "team:" + FACTION) && level.noCollision(mob)
                    && level.getBlockState(feet).isAir() && level.getBlockState(feet.above()).isAir()
                    && level.getBlockState(feet.below()).is(Blocks.STONE), "Auxiliary escaped its safe isolated cell");
            actors.add(Map.of("uuid", mob.getUUID().toString(), "type", String.valueOf(ForgeRegistries.ENTITY_TYPES.getKey(mob.getType())),
                    "feet", List.of(feet.getX(), feet.getY(), feet.getZ()),
                    "bounds", List.of(body.minX, body.minY, body.minZ, body.maxX, body.maxY, body.maxZ), "alive", true, "noAi", true,
                    "sameClaim", true, "clearOfWorkEnvelope", true));
        }
        var cells = new ArrayList<Map<String, Object>>();
        require(fixture.isolationCells().size() == fixture.isolatedAuxiliaries().size() * 45, "Auxiliary baseline size changed");
        for (var entry : fixture.isolationCells().entrySet().stream().sorted(Map.Entry.comparingByKey()).toList()) {
            BlockPos pos = entry.getKey();
            require(level.hasChunkAt(pos) && level.getBlockState(pos).equals(entry.getValue())
                    && !fixture.plan().blocks().containsKey(pos.asLong()) && !fixture.plan().clearance().contains(pos.asLong()),
                    "Auxiliary shell/air/ground-cap baseline changed");
            if (pos.getY() < 64) require(pos.getY() < fixture.plan().min().getY() - 14,
                    "Auxiliary holding cell enters the native standing envelope");
            cells.add(Map.of("position", List.of(pos.getX(), pos.getY(), pos.getZ()),
                    "block", String.valueOf(ForgeRegistries.BLOCKS.getKey(entry.getValue().getBlock()))));
        }
        return Map.of("gameTime", level.getGameTime(), "feetY", 48, "planMinY", fixture.plan().min().getY(),
                "standingVerticalRange", 12, "auxiliaries", actors, "baseline", cells);
    }

    /** Bread-only initial helper on reviewed clear terrain; helpers do not perform native mining.
     * The coordinator keeps its normal tool kit, which its successor may recover through native drops.
     * Never rearrange cargo after either worker has entered the commissioned scenario.
     */
    static BuilderEntity spawnSharedHelper(ServerLevel level, ServerPlayer owner) throws ReflectiveOperationException {
        require(WORLD.equals(level.getServer().getWorldData().getLevelName()) && level.getServer().isSameThread(),
                "Refusing shared helper outside isolated fixture");
        var type = ForgeRegistries.ENTITY_TYPES.getValue(new ResourceLocation("workers", "builder"));
        require(type != null, "Shared helper registry unavailable");
        var raw = type.create(level);
        require(raw instanceof BuilderEntity, "Shared helper type changed");
        BuilderEntity helper = (BuilderEntity) raw;
        helper.moveTo(137.5, 65, 9.5, 0, 0);
        helper.finalizeSpawn(level, level.getCurrentDifficultyAt(helper.blockPosition()), MobSpawnType.COMMAND, null, null);
        WorkersBridge.enablePlayerJob(helper, owner.getUUID()); helper.setPersistenceRequired();
        helper.setNoAi(true); // Fixture preparation only; released together with the coordinator.
        require(helper.getInventory().countItem(Items.COBBLESTONE) == 0
                && helper.getInventory().countItem(Items.OAK_PLANKS) == 0
                && !helper.getMainHandItem().is(Items.COBBLESTONE) && !helper.getMainHandItem().is(Items.OAK_PLANKS)
                && !helper.getOffhandItem().is(Items.COBBLESTONE) && !helper.getOffhandItem().is(Items.OAK_PLANKS),
                "Shared helper must not create any additional construction supply");
        require(helper.getInventory().getContainerSize() >= 10 && helper.getInventory().getContainerSize() <= 128,
                "Shared helper cargo inventory is outside the bounded fixture contract");
        for (int slot = 6; slot < helper.getInventory().getContainerSize(); slot++)
            require(helper.getInventory().getItem(slot).isEmpty(), "Shared helper has unexpected initial cargo in slot " + slot);
        helper.getInventory().setItem(6, new ItemStack(Items.BREAD, 64));
        helper.getInventory().setChanged();
        require(owner.getUUID().equals(WorkersBridge.readWorkerOwner(helper)) && level.addFreshEntity(helper),
                "Shared helper exact ownership/spawn failed");
        return helper;
    }

    record HollowOracle(Map<Long, String> targets, Set<Long> clearance, Set<Long> cavities,
                        Set<Long> headroom, int columns, int skinColumns) {}

    /** Independent rectangle-distance oracle; does not use production columns or compiler output. */
    static HollowOracle independentHollowOracle() {
        Map<Long, String> targets = new LinkedHashMap<>();
        Set<Long> clearance = new java.util.HashSet<>(), cavities = new java.util.HashSet<>(), headroom = new java.util.HashSet<>();
        int columns = 0, skinColumns = 0;
        for (int x = 0; x < 16; x++) for (int z = 0; z < 16; z++) {
            int inwardDistance = Math.min(Math.min(x + 1, 16 - x), Math.min(z + 1, 16 - z));
            if (inwardDistance > 5) continue;
            boolean skin = inwardDistance == 1 || inwardDistance == 5;
            columns++; if (skin) skinColumns++;
            for (int y = 0; y < 6; y++) {
                long cell = new BlockPos(CLAIM_CHUNK.getMinBlockX() + x, 65 + y, CLAIM_CHUNK.getMinBlockZ() + z).asLong();
                if (y == 3) targets.put(cell, "minecraft:oak_planks");
                else if (skin && (y < 3 || y == 4)) targets.put(cell, "minecraft:cobblestone");
                else {
                    clearance.add(cell);
                    if (y < 3) cavities.add(cell); else headroom.add(cell);
                }
            }
        }
        require(columns == 220 && skinColumns == 88 && columns - skinColumns == 132,
                "Independent hollow ring column formula changed");
        require(targets.size() == BLOCKS && targets.values().stream().filter("minecraft:cobblestone"::equals).count() == COBBLE
                && targets.values().stream().filter("minecraft:oak_planks"::equals).count() == OAK
                && cavities.size() == CAVITY_AIR && headroom.size() == HEADROOM_AIR
                && clearance.size() == CAVITY_AIR + HEADROOM_AIR && targets.size() + clearance.size() == RESERVED,
                "Independent hollow materials, protected cavities or walkway headroom changed");
        return new HollowOracle(Map.copyOf(targets), Set.copyOf(clearance), Set.copyOf(cavities), Set.copyOf(headroom), columns, skinColumns);
    }

    static void assertHollowAir(ServerLevel level, HollowOracle oracle) {
        for (long cell : oracle.cavities()) require(level.getBlockState(BlockPos.of(cell)).equals(Blocks.AIR.defaultBlockState())
                && level.getBlockEntity(BlockPos.of(cell)) == null, "Protected hollow cavity is no longer exact AIR: " + BlockPos.of(cell));
        for (long cell : oracle.headroom()) require(level.getBlockState(BlockPos.of(cell)).equals(Blocks.AIR.defaultBlockState())
                && level.getBlockEntity(BlockPos.of(cell)) == null, "Protected walkway/headroom is no longer exact AIR: " + BlockPos.of(cell));
    }

    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
