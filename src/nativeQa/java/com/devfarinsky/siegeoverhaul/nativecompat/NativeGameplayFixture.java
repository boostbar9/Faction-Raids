package com.devfarinsky.siegeoverhaul.nativecompat;

import com.devfarinsky.siegeoverhaul.compat.WorkersBridge;
import com.devfarinsky.siegeoverhaul.core.CoreBlocks;
import com.devfarinsky.siegeoverhaul.core.DefenseBlueprint;
import com.devfarinsky.siegeoverhaul.core.SiegeCore;
import com.devfarinsky.siegeoverhaul.items.ModItems;
import com.talhanation.recruits.ClaimEvents;
import com.talhanation.recruits.FactionEvents;
import com.talhanation.recruits.world.RecruitsClaim;
import com.talhanation.recruits.world.RecruitsPlayerInfo;
import com.talhanation.workers.entities.BuilderEntity;
import com.talhanation.workers.entities.workarea.StorageArea;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
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

import java.util.List;
import java.util.UUID;

/** Fixture setup only: real registries/managers/entities; no plan, payment or construction shortcuts. */
final class NativeGameplayFixture {
    private static final String WORLD = "siege-native-gameplay";
    private static final String FACTION = "native_gameplay";
    private static final BlockPos CORE = new BlockPos(137, 65, 16);
    private static final BlockPos CHEST = new BlockPos(137, 65, 22);
    private static final BlockPos WALL = new BlockPos(145, 65, 15);
    private static final List<ChunkPos> CHUNKS = List.of(new ChunkPos(8, 0), new ChunkPos(9, 0),
            new ChunkPos(8, 1), new ChunkPos(9, 1));

    record Fixture(UUID builderId, UUID storageId, UUID claimId, String factionId,
                   BlockPos corePos, BlockPos chestPos, BlockPos wallAnchor,
                   DefenseBlueprint.Plan expectedPlan) {}

    private NativeGameplayFixture() {}

    static Fixture setup(ServerLevel level, ServerPlayer owner) throws ReflectiveOperationException {
        requireFixtureWorld(level);
        require(owner.serverLevel() == level && !owner.isCreative() && !owner.isSpectator()
                && !owner.hasPermissions(2) && owner.mayBuild(), "Fixture requires a real non-op survival owner");
        require(owner.getTeam() == null, "Fixture owner already belongs to a team");
        require(FactionEvents.recruitsFactionManager != null && ClaimEvents.recruitsClaimManager != null,
                "Native faction/claim managers are not ready");
        require(FactionEvents.recruitsFactionManager.getFactionByStringID(FACTION) == null,
                "Fixture faction already exists");
        for (ChunkPos chunk : CHUNKS) require(ClaimEvents.recruitsClaimManager.getClaim(chunk) == null,
                "Fixture claim already exists");

        // Explicitly bounded test terrain. Chunk generation/loading is fixture setup, never a job hook.
        for (int x = 124; x <= 163; x++) for (int z = -4; z <= 35; z++) {
            level.setBlock(new BlockPos(x, 64, z), Blocks.STONE.defaultBlockState(), 3);
            for (int y = 65; y <= 73; y++) level.setBlock(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState(), 3);
        }
        level.setDayTime(6000);
        owner.teleportTo(level, 140.5, 65, 12.5, 0, 25);

        // Native command-style setup builds membership, scoreboard and event/index state. This is
        // deliberately not evidence that the faction creation menu or its creation fee was tested.
        FactionEvents.createTeam(false, owner, level, FACTION, "Native Gameplay QA",
                owner.getScoreboardName(), new ItemStack(Items.BLUE_BANNER), ChatFormatting.BLUE, (byte) 11);
        var faction = FactionEvents.recruitsFactionManager.getFactionByStringID(FACTION);
        require(faction != null && owner.getTeam() != null && FACTION.equals(owner.getTeam().getName()),
                "Native faction creation was rejected or membership was not indexed");
        var claim = new RecruitsClaim("Native Gameplay QA", faction);
        claim.setCenter(new ChunkPos(CORE));
        claim.setPlayer(new RecruitsPlayerInfo(owner.getUUID(), owner.getScoreboardName(), faction));
        CHUNKS.forEach(claim::addChunk);
        claim.setHealth(claim.getMaxHealth());
        ClaimEvents.recruitsClaimManager.addOrUpdateClaim(level, claim);
        for (ChunkPos chunk : CHUNKS) require(ClaimEvents.recruitsClaimManager.getClaim(chunk) == claim,
                "Native claim update was rejected or its chunk index was not installed");
        ClaimEvents.recruitsClaimManager.save(level);

        // Run the real CoreItem placement contract and its normal deferred starter callback.
        ItemStack coreStack = new ItemStack(ModItems.SIEGE_CORE.get());
        var placement = new BlockPlaceContext(owner, InteractionHand.MAIN_HAND, coreStack,
                new BlockHitResult(Vec3.atBottomCenterOf(CORE), Direction.UP, CORE.below(), false));
        require(((BlockItem) coreStack.getItem()).place(placement).consumesAction(), "Core item placement failed");
        require(level.getBlockState(CORE).is(CoreBlocks.CORE.get())
                && SiegeCore.point(owner.server, SiegeCore.key(owner)) != null, "Core placement did not register its anchor");
        ((CoreBlocks.CoreBlock) CoreBlocks.CORE.get()).tick(level.getBlockState(CORE), level, CORE, level.random);
        // Freeze only unrelated startup NPCs, so they cannot wander into the measured footprint.
        // The tested builder is created below and its native goal set is never replaced.
        ListTag auxiliaries = new ListTag();
        int auxiliary = 0;
        for (Mob mob : level.getEntitiesOfClass(Mob.class, new AABB(CORE).inflate(10))) {
            mob.moveTo(135.5 + auxiliary % 3 * 2, 65, 9.5, 0, 0);
            mob.getNavigation().stop();
            mob.setNoAi(true);
            auxiliaries.add(StringTag.valueOf(mob.getUUID().toString()));
            auxiliary++;
        }
        owner.getPersistentData().put("NativeGameplayFixtureAuxiliaries", auxiliaries);

        level.setBlock(CHEST, Blocks.CHEST.defaultBlockState(), 3);
        replenishContainer(level, 8, 8);
        var storageEntity = WorkersBridge.createPlayerArea(level, "storagearea", CHEST,
                owner.getUUID(), owner.getScoreboardName(), 1, 1, 1);
        require(storageEntity instanceof StorageArea, "Native storage entity type is unsupported");
        StorageArea storage = (StorageArea) storageEntity;
        require(level.addFreshEntity(storage), "Native storage spawn failed");
        storage.scanStorageBlocks();
        require(storage.storageMap.size() == 1 && storage.storageMap.containsKey(CHEST),
                "Native storage did not discover the actual chest");

        var builderType = ForgeRegistries.ENTITY_TYPES.getValue(new ResourceLocation("workers", "builder"));
        require(builderType != null, "Native builder registry entry missing");
        var builderEntity = builderType.create(level);
        require(builderEntity instanceof BuilderEntity, "Native builder entity type is unsupported");
        BuilderEntity builder = (BuilderEntity) builderEntity;
        builder.moveTo(140.5, 65, 20, 0, 0);
        builder.finalizeSpawn(level, level.getCurrentDifficultyAt(builder.blockPosition()), MobSpawnType.COMMAND, null, null);
        WorkersBridge.enablePlayerJob(builder, owner.getUUID());
        builder.setPersistenceRequired();
        builder.setNoAi(true); // Only until the orchestrator finishes its perimeter transaction/cancel.
        var inventory = builder.getInventory();
        require(inventory.getContainerSize() >= 10, "Native builder cargo missing");
        require(inventory.countItem(Items.COBBLESTONE) == 0 && inventory.countItem(Items.OAK_PLANKS) == 0,
                "Unexpected native building stock would invalidate conservation assertions");
        // Finite fixture supplies in actual native cargo slots; no raider kit or recurring refill.
        inventory.setItem(6, new ItemStack(Items.BREAD, 16));
        inventory.setItem(7, new ItemStack(Items.DIAMOND_PICKAXE));
        inventory.setItem(8, new ItemStack(Items.DIAMOND_AXE));
        inventory.setItem(9, new ItemStack(Items.DIAMOND_SHOVEL));
        inventory.setChanged();
        // Reproduce the real idle/unprotected reload boundary before its first commission. Move
        // the existing finite tool using Workers' public switch (no added construction material),
        // then use the actual entity NBT lifecycle. The production handoff must restore the mirror.
        builder.switchMainHandItem(stack -> stack.is(Items.DIAMOND_PICKAXE));
        require(builder.getMainHandItem().is(Items.DIAMOND_PICKAXE), "Native tool switch failed");
        CompoundTag handBeforeReload = builder.getMainHandItem().save(new CompoundTag());
        CompoundTag idleBuilder = builder.saveWithoutId(new CompoundTag());
        builder.load(idleBuilder);
        require(builder.isNoAi() && builder.getMainHandItem() != builder.getInventory().getItem(5)
                && handBeforeReload.equals(builder.getMainHandItem().save(new CompoundTag()))
                && handBeforeReload.equals(builder.getInventory().getItem(5).save(new CompoundTag())),
                "Idle native NBT round-trip did not preserve equal values in split hand mirrors");
        require(!ProtectedBuilderHandMirror.pending(builder.getPersistentData())
                && !ProtectedBuilderHandMirror.reviewNeeded(builder.getPersistentData()),
                "Unguarded fixture must not invoke the protected hand repair");
        require(level.addFreshEntity(builder), "Native builder spawn failed");
        require(storage.canWorkHere(builder), "Native storage denies the owned builder");

        // Near enough for the real core review action. The orchestrator approaches the manual wall
        // separately after canceling the transaction-only perimeter job.
        owner.teleportTo(level, 141.5, 65, 12.5, 0, 25);
        require(SiegeCore.canUse(owner, CORE), "Fixture owner is out of core interaction range");
        require(DefenseBlueprint.Kind.WALL.anchor(WALL).equals(WALL), "Fixture wall anchor is not on the native plan grid");
        return new Fixture(builder.getUUID(), storage.getUUID(), claim.getUUID(), FACTION, CORE, CHEST, WALL,
                DefenseBlueprint.create(DefenseBlueprint.Kind.WALL, WALL, Direction.SOUTH));
    }

    /** Adds all requested material or throws before changing any chest slot. Never clears old stock. */
    static void replenishContainer(ServerLevel level, int countCobble, int countOak) {
        requireFixtureWorld(level);
        require(countCobble >= 0 && countOak >= 0 && countCobble <= 1728 && countOak <= 1728,
                "Fixture resupply is outside the bounded chest capacity");
        require(level.hasChunkAt(CHEST) && level.getBlockEntity(CHEST) instanceof Container,
                "Fixture chest is unavailable");
        Container chest = (Container) level.getBlockEntity(CHEST);
        require(chest.getContainerSize() == 27, "Fixture expects a single real chest");
        SimpleContainer proposed = new SimpleContainer(chest.getContainerSize());
        for (int slot = 0; slot < chest.getContainerSize(); slot++) proposed.setItem(slot, chest.getItem(slot).copy());
        addExact(proposed, Items.COBBLESTONE, countCobble);
        addExact(proposed, Items.OAK_PLANKS, countOak);
        for (int slot = 0; slot < chest.getContainerSize(); slot++) chest.setItem(slot, proposed.getItem(slot).copy());
        chest.setChanged();
    }

    private static void addExact(SimpleContainer inventory, Item item, int amount) {
        while (amount > 0) {
            int count = Math.min(item.getMaxStackSize(), amount);
            require(inventory.addItem(new ItemStack(item, count)).isEmpty(), "Fixture chest has no room for exact resupply");
            amount -= count;
        }
    }

    private static void requireFixtureWorld(ServerLevel level) {
        require(level != null && level.dimension().equals(Level.OVERWORLD) && level.getServer().isSameThread()
                && WORLD.equals(level.getServer().getWorldData().getLevelName()), "Refusing a non-fixture world or thread");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
