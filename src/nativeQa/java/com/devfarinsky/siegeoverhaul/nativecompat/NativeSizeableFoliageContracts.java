package com.devfarinsky.siegeoverhaul.nativecompat;

import com.devfarinsky.siegeoverhaul.RaidSavedData;
import com.devfarinsky.siegeoverhaul.core.FactionBank;
import com.devfarinsky.siegeoverhaul.core.PerimeterConstruction;
import com.devfarinsky.siegeoverhaul.core.PerimeterProjectStore;
import com.devfarinsky.siegeoverhaul.core.SiegeCore;
import com.talhanation.workers.entities.BuilderEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.registries.ForgeRegistries;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Real addon registry checks and read-only admission on explicitly seeded QA terrain. */
final class NativeSizeableFoliageContracts {
    private static final String SHORT = "sizeable_foliage:very_short_grass";
    private static final String CLASS = "com.craisinlord.sizeablefoliage.content.block.VeryShortGrassBlock";
    private static final List<String> EXCLUDED = List.of("sizeable_foliage:very_tall_grass",
            "sizeable_foliage:very_large_fern", "sizeable_foliage:big_bush", "sizeable_foliage:big_bush_part",
            "sizeable_foliage:big_sweet_berry_bush", "sizeable_foliage:big_sweet_berry_bush_part",
            "sizeable_foliage:fern_wall", "sizeable_foliage:torchflower_bush",
            "minecraft:wheat", "minecraft:oak_sapling", "minecraft:chest", "minecraft:water", "minecraft:stone_bricks");

    private NativeSizeableFoliageContracts() {}

    static boolean enabled() { return Boolean.getBoolean("siegeoverhaul.nativeQa.sizeableFoliage"); }
    static String plantId() { return enabled() ? SHORT : "minecraft:dandelion"; }
    static BlockState plantState() { return enabled() ? registered(SHORT).defaultBlockState() : Blocks.DANDELION.defaultBlockState(); }

    static Map<String, Object> runtime() throws Exception {
        var info = ModList.get().getModContainerById("sizeable_foliage").orElseThrow().getModInfo();
        require("1.2.1".equals(info.getVersion().toString()), "Unreviewed Sizeable Foliage runtime version");
        Path jar = info.getOwningFile().getFile().getFilePath();
        require(Files.isRegularFile(jar), "Loaded Sizeable Foliage artifact is not a real JAR");
        Block block = registered(SHORT);
        require(CLASS.equals(block.getClass().getName()) && block.getClass().getSuperclass() == BushBlock.class,
                "Unexpected short-grass runtime class or inheritance");
        List<String> methods = Arrays.stream(block.getClass().getDeclaredMethods()).map(java.lang.reflect.Method::getName).sorted().toList();
        Set<String> sideEffects = Set.of("onRemove", "playerDestroy", "playerWillDestroy", "neighborChanged",
                "onPlace", "updateShape", "createBlockStateDefinition");
        require(methods.stream().noneMatch(sideEffects::contains), "Reviewed addon now overrides lifecycle/state methods");
        return Map.of("modId", "sizeable_foliage", "version", "1.2.1", "officialVersionId", "m2kFJSUs",
                "runtimeClass", block.getClass().getName(), "declaredMethods", methods,
                "noDeclaredRemovalOrMultipartOverrides", true, "fileName", jar.getFileName().toString(),
                "sha256", HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(jar))),
                "artifactScope", "Actual ForgeGradle remapped runtime JAR; original official release is independently checksum-verified");
    }

    static Map<String, Object> verifyReviews(ServerLevel level, ServerPlayer owner,
                                             NativeGameplayFixture.Fixture fixture, BuilderEntity builder) {
        CompoundTag core = RaidSavedData.get(owner.server).siegeCores.get(SiegeCore.key(owner));
        long treasury = FactionBank.balance(core);
        CompoundTag workerData = builder.getPersistentData().copy();
        ListTag inventory = inventory(builder);
        CompoundTag hand = builder.getMainHandItem().save(new CompoundTag());
        var clean = PerimeterConstruction.prepare(owner, fixture.corePos(), 1);
        require(clean.ready(), "Clean native perimeter is not reviewable: " + clean.problem());
        BlockPos target = clean.plan().blocks().keySet().stream().sorted().map(BlockPos::of)
                .filter(pos -> pos.getY() == fixture.corePos().getY() && level.getBlockState(pos).isAir())
                .findFirst().orElseThrow();
        BlockPos cavity = new BlockPos(130, 65, 8);
        require(clean.plan().clearance().contains(cavity.asLong()) && !clean.plan().blocks().containsKey(cavity.asLong()),
                "Expected explicitly reserved perimeter cavity is absent");
        var original = new LinkedHashMap<BlockPos, BlockState>();
        for (BlockPos center : List.of(target, cavity)) for (int dy = -1; dy <= 3; dy++) {
            BlockPos pos = center.above(dy);
            require(level.hasChunkAt(pos) && level.getBlockEntity(pos) == null, "Unsafe addon review fixture cell");
            original.put(pos, level.getBlockState(pos));
        }
        Map<String, String> rejections = new LinkedHashMap<>();
        try {
            BlockState grass = plantState();
            for (BlockPos pos : List.of(target, cavity)) {
                level.setBlock(pos.below(), Blocks.GRASS_BLOCK.defaultBlockState(), 2);
                level.setBlock(pos, grass, 2);
                require(grass.canSurvive(level, pos) && grass.getCollisionShape(level, pos).isEmpty()
                                && grass.getFluidState().isEmpty() && !grass.hasBlockEntity()
                                && grass.canBeReplaced() && grass.getDestroySpeed(level, pos) == 0.0F
                                && !(grass.getBlock() instanceof EntityBlock) && !(grass.getBlock() instanceof CropBlock)
                                && !(grass.getBlock() instanceof DoublePlantBlock) && grass.getProperties().isEmpty(),
                        "Actual short-grass state violates reviewed single-cell empty-collision contract");
            }
            require(com.devfarinsky.siegeoverhaul.core.NativeQaBuilderStanding.admits(level, builder, target)
                            && com.devfarinsky.siegeoverhaul.core.NativeQaBuilderStanding.admits(level, builder, cavity),
                    "Reviewed real short grass must permit collision-free builder standing without being cleared");
            var accepted = PerimeterConstruction.prepare(owner, fixture.corePos(), 1);
            require(accepted.ready(), "Actual Sizeable Foliage grass perimeter review rejected: " + accepted.problem());
            require(level.getBlockState(target).equals(grass) && level.getBlockState(cavity).equals(grass),
                    "Free positive review removed a plant");
            level.setBlock(cavity, original.get(cavity), 2);
            for (String id : EXCLUDED) {
                BlockState obstruction = registered(id).defaultBlockState();
                level.setBlock(target, obstruction, 2);
                String problem = PerimeterConstruction.prepare(owner, fixture.corePos(), 1).problem();
                require(problem != null && problem.contains(target.toShortString()) && problem.contains(id),
                        "Unsafe real-registry obstruction was accepted or lost exact diagnostics: " + id + " -> " + problem);
                require(level.getBlockState(target).equals(obstruction), "Free rejection changed its obstacle: " + id);
                require(!com.devfarinsky.siegeoverhaul.core.NativeQaBuilderStanding.admits(level, builder, target),
                        "Unaudited or unsafe standing occupancy was admitted: " + id);
                rejections.put(id, problem);
            }
        } finally {
            // Fixture cleanup only, after read-only assertions. No accepted construction is active here.
            original.forEach((pos, state) -> level.setBlock(pos, state, 2));
        }
        require(original.entrySet().stream().allMatch(entry -> level.getBlockState(entry.getKey()).equals(entry.getValue())),
                "Addon review fixture did not restore its terrain");
        require(FactionBank.balance(core) == treasury && PerimeterProjectStore.all(core).isEmpty()
                        && workerData.equals(builder.getPersistentData()) && inventory.equals(inventory(builder))
                        && hand.equals(builder.getMainHandItem().save(new CompoundTag())),
                "Addon read-only reviews changed Treasury, projects, worker receipt or inventory");
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("targetCell", target.toShortString()); result.put("clearanceCell", cavity.toShortString());
        result.put("shortGrassTargetAndClearanceAccepted", true); result.put("runtimeStateSafetyVerified", true);
        result.put("plantsPreservedDuringReview", true); result.put("shortGrassStandingSiteAccepted", true);
        result.put("rejections", rejections); result.put("treasuryDebit", FactionBank.balance(core) - treasury);
        result.put("exactWorkerInventoryAndReceipts", true); result.put("fixtureTerrainRestored", true);
        result.put("scope", "Explicit pre-commission registry states on bounded fixture terrain; production prepare and standing-site checks only. No native walking, rejected confirmation packet or naturally grown multipart layout is claimed.");
        return Map.copyOf(result);
    }

    private static Block registered(String id) {
        ResourceLocation key = new ResourceLocation(id);
        require(ForgeRegistries.BLOCKS.containsKey(key), "Required actual addon/vanilla registry entry is missing: " + id);
        return ForgeRegistries.BLOCKS.getValue(key);
    }

    private static ListTag inventory(BuilderEntity builder) {
        ListTag items = new ListTag();
        for (int slot = 0; slot < builder.getInventory().getContainerSize(); slot++) {
            CompoundTag item = builder.getInventory().getItem(slot).save(new CompoundTag());
            item.putInt("Slot", slot); items.add(item);
        }
        return items;
    }

    private static void require(boolean value, String message) {
        if (!value) throw new IllegalStateException(message);
    }
}
