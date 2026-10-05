package com.devfarinsky.siegeoverhaul.nativecompat;

import com.devfarinsky.siegeoverhaul.compat.RecruitsClaimsBridge;
import com.devfarinsky.siegeoverhaul.compat.WorkersBridge;
import com.devfarinsky.siegeoverhaul.core.CoreBlocks;
import com.devfarinsky.siegeoverhaul.core.PerimeterEarthworksManifest;
import com.devfarinsky.siegeoverhaul.core.PerimeterTerritory;
import com.devfarinsky.siegeoverhaul.core.SiegeCore;
import com.devfarinsky.siegeoverhaul.items.ModItems;
import com.talhanation.recruits.ClaimEvents;
import com.talhanation.recruits.FactionEvents;
import com.talhanation.recruits.world.RecruitsClaim;
import com.talhanation.recruits.world.RecruitsPlayerInfo;
import com.talhanation.workers.entities.BuilderEntity;
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
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Synthetic terrain/claim/stock setup only. Never writes the finished target or grants job authority. */
final class NativeEarthworksFixture {
    static final String WORLD = "siege-native-earthworks-one-fill", FACTION = "native_earthworks";
    static final BlockPos CORE = new BlockPos(132, 65, 4), CHEST = new BlockPos(136, 65, 8);
    static final BlockPos TARGET = new BlockPos(141, 64, 9), FOREIGN = new BlockPos(144, 64, 9);
    static final BlockPos SUPPORT = TARGET.below(), MARKER = new BlockPos(139, 65, 11);
    static final ChunkPos CLAIM = new ChunkPos(8, 0);
    static final int STOCK = 2;
    record Fixture(BuilderEntity builder, StorageArea storage, GetNeededItemsFromStorage originalStorageGoal) {}
    private NativeEarthworksFixture() {}

    static Fixture setup(ServerLevel level, ServerPlayer owner) throws Exception {
        require(level.dimension().equals(Level.OVERWORLD) && level.getServer().isSameThread()
                && WORLD.equals(level.getServer().getWorldData().getLevelName()), "Refusing non-fixture world/thread");
        require(owner == level.getServer().getPlayerList().getPlayer(owner.getUUID()) && owner.serverLevel() == level
                && !owner.isCreative() && !owner.isSpectator() && !owner.hasPermissions(2) && owner.mayBuild(),
                "Requires actual non-op integrated survival player");
        var cache = level.getServer().getProfileCache();
        var profile = cache == null ? null : cache.get(owner.getUUID()).orElse(null);
        require(profile != null && profile.getId().equals(owner.getUUID())
                && profile.getName().equals(owner.getScoreboardName()), "Normal server profile cache has no actual owner identity");
        require(owner.getTeam() == null && FactionEvents.recruitsFactionManager != null
                && ClaimEvents.recruitsClaimManager != null, "Native managers/membership not fresh");
        require(FactionEvents.recruitsFactionManager.getFactionByStringID(FACTION) == null
                && ClaimEvents.recruitsClaimManager.getClaim(CLAIM) == null, "Fixture faction/claim already exists");
        // Deliberately flat synthetic patch and two empty test cells. Neither target is ever prebuilt.
        for (int x = 124; x <= 148; x++) for (int z = -4; z <= 20; z++) {
            for (int y = 62; y <= 64; y++) level.setBlock(new BlockPos(x, y, z), Blocks.STONE.defaultBlockState(), 3);
            for (int y = 65; y <= 72; y++) level.setBlock(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState(), 3);
        }
        level.setBlock(TARGET, Blocks.AIR.defaultBlockState(), 3);
        level.setBlock(FOREIGN, Blocks.AIR.defaultBlockState(), 3);
        level.setDayTime(6000);
        owner.teleportTo(level, 134.5, 65, 12.5, -90, 30);
        FactionEvents.createTeam(false, owner, level, FACTION, "Native Earthworks QA", owner.getScoreboardName(),
                new ItemStack(Items.BLUE_BANNER), ChatFormatting.BLUE, (byte) 11);
        var faction = FactionEvents.recruitsFactionManager.getFactionByStringID(FACTION);
        require(faction != null && owner.getTeam() != null && FACTION.equals(owner.getTeam().getName()), "Native faction not indexed");
        var claim = new RecruitsClaim("Native Earthworks QA", faction);
        claim.setCenter(CLAIM); claim.setPlayer(new RecruitsPlayerInfo(owner.getUUID(), owner.getScoreboardName(), faction));
        claim.addChunk(CLAIM); claim.setHealth(claim.getMaxHealth());
        ClaimEvents.recruitsClaimManager.addOrUpdateClaim(level, claim); ClaimEvents.recruitsClaimManager.save(level);
        require(ClaimEvents.recruitsClaimManager.getClaim(CLAIM) == claim, "Native claim index missing");
        var coreStack = new ItemStack(ModItems.SIEGE_CORE.get());
        var context = new BlockPlaceContext(owner, InteractionHand.MAIN_HAND, coreStack,
                new BlockHitResult(Vec3.atBottomCenterOf(CORE), Direction.UP, CORE.below(), false));
        require(((BlockItem) coreStack.getItem()).place(context).consumesAction()
                && level.getBlockState(CORE).is(CoreBlocks.CORE.get())
                && SiegeCore.point(owner.server, SiegeCore.key(owner)) != null, "Real core item placement failed");
        // Its deferred startup callback is left to ordinary server ticks.
        level.setBlock(CHEST, Blocks.CHEST.defaultBlockState(), 3);
        var chest = (Container) level.getBlockEntity(CHEST);
        require(chest != null && chest.getContainerSize() == 27 && chest.isEmpty(), "Fresh single native chest unavailable");
        chest.setItem(0, new ItemStack(Items.DIRT, STOCK)); chest.setChanged();
        var createdStorage = WorkersBridge.createPlayerArea(level, "storagearea", CHEST,
                owner.getUUID(), owner.getScoreboardName(), 1, 1, 1);
        require(createdStorage instanceof StorageArea, "Wrong storage registry type");
        var storage = (StorageArea) createdStorage;
        require(level.addFreshEntity(storage), "Storage spawn failed"); storage.scanStorageBlocks();
        require(storage.storageMap.size() == 1 && storage.storageMap.get(CHEST) == chest, "Storage did not discover actual chest");
        var type = ForgeRegistries.ENTITY_TYPES.getValue(new ResourceLocation("workers", "builder"));
        require(type != null, "Native builder registry missing");
        var createdBuilder = type.create(level);
        require(createdBuilder instanceof BuilderEntity, "Wrong native builder type");
        var builder = (BuilderEntity) createdBuilder;
        builder.moveTo(141.5, 65, 8.5, 90, 0);
        builder.finalizeSpawn(level, level.getCurrentDifficultyAt(builder.blockPosition()), MobSpawnType.COMMAND, null, null);
        WorkersBridge.enablePlayerJob(builder, owner.getUUID()); builder.setPersistenceRequired(); builder.setNoAi(true);
        require(builder.getInventory().countItem(Items.DIRT) == 0 && builder.neededItems.isEmpty(), "Unexpected grading stock/request");
        require(level.addFreshEntity(builder) && storage.canWorkHere(builder), "Owned native builder/storage rejected");
        var originals = builder.goalSelector.getAvailableGoals().stream().map(g -> g.getGoal())
                .filter(g -> g.getClass() == GetNeededItemsFromStorage.class).map(GetNeededItemsFromStorage.class::cast).toList();
        require(originals.size() == 1, "Exactly one original native storage goal required");
        return new Fixture(builder, storage, originals.get(0));
    }

    static void freezeUnrelatedStartup(ServerLevel level, BuilderEntity tested) {
        int index = 0;
        for (Mob mob : level.getEntitiesOfClass(Mob.class, new AABB(CORE).inflate(18))) {
            if (mob == tested) continue;
            mob.moveTo(129.5 + index++ % 2 * 2, 65, 1.5, 0, 0);
            mob.getNavigation().stop(); mob.setNoAi(true);
        }
    }

    static PerimeterEarthworksManifest manifest(ServerLevel level, ServerPlayer owner, BuilderEntity builder,
                                                BlockPos target, boolean unloadedObservation) {
        var territory = RecruitsClaimsBridge.getFactionTerritory(level, FACTION, PerimeterTerritory.MAX_CHUNKS);
        require(territory.ready() && territory.chunks().equals(java.util.Set.of(CLAIM)), "Exact current territory differs");
        var observations = new ArrayList<PerimeterEarthworksManifest.Observation>();
        if (unloadedObservation) {
            // Expected values only. Never read/generate the deliberately unloaded remote chunk.
            observations.add(new PerimeterEarthworksManifest.Observation(target.below().asLong(), Blocks.STONE.defaultBlockState(),
                    PerimeterEarthworksManifest.Role.DEPENDENCY, 0));
        } else {
            for (BlockPos pos : BlockPos.betweenClosed(target.offset(-2, -2, -2), target.offset(2, 2, 2))) {
                if (pos.equals(target)) continue;
                observations.add(new PerimeterEarthworksManifest.Observation(pos.asLong(), level.getBlockState(pos),
                        PerimeterEarthworksManifest.Role.DEPENDENCY, 0));
            }
        }
        observations.add(new PerimeterEarthworksManifest.Observation(target.asLong(), Blocks.AIR.defaultBlockState(),
                PerimeterEarthworksManifest.Role.WORK, 0));
        var header = new PerimeterEarthworksManifest.Header(UUID.randomUUID(), 1, owner.getUUID(), builder.getUUID(),
                level.dimension().location().toString(), FACTION, EarthworksCommission.claimsDigest(territory),
                NativeEarthworksAdapter.evidenceHash("synthetic-flat-one-fill-v1", target.asLong()),
                "synthetic-native-qa-one-fill-v1", 0, 65, level.getMinBuildHeight(), level.getMaxBuildHeight(), 1, 64);
        return new PerimeterEarthworksManifest(header, observations, List.of(new PerimeterEarthworksManifest.Step(0,
                PerimeterEarthworksManifest.Kind.FILL, target.asLong(), Blocks.AIR.defaultBlockState(), Blocks.DIRT.defaultBlockState(), null)));
    }
    static BlockPos unloadedCell() { return new BlockPos(1_000_000, 64, 1_000_000); }
    static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
