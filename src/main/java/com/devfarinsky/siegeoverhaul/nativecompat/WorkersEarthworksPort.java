package com.devfarinsky.siegeoverhaul.nativecompat;

import com.devfarinsky.siegeoverhaul.compat.WorkersBridge;
import com.devfarinsky.siegeoverhaul.core.PerimeterEarthworksJournal;
import com.devfarinsky.siegeoverhaul.core.PerimeterEarthworksManifest;
import com.devfarinsky.siegeoverhaul.core.PerimeterProjectLink;
import com.talhanation.workers.entities.BuilderEntity;
import com.talhanation.workers.entities.ai.BuilderWorkGoal;
import com.talhanation.workers.entities.workarea.BuildArea;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ShovelItem;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.level.GameRules;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.Stack;
import java.util.UUID;

/** Actual pinned Workers callbacks/readers for an unregistered NEW-earthworks goal. Never wraps a legacy area. */
final class WorkersEarthworksPort implements NativeEarthworksAdapter.Port {
    interface Authority {
        /** Must prove NEW exclusive goal/area lease, journal identity, review, and deduplicated whole-project budgets. */
        String newLeaseProblem(PerimeterEarthworksManifest manifest, PerimeterEarthworksJournal journal, BuilderEntity worker, BuildArea area);
        long editRevision(BlockPos pos);
        /** New-manifest storage/hand authority must be integrated; old job receipts cannot be borrowed. */
        String nativeInventoryProblem(PerimeterEarthworksManifest manifest, PerimeterEarthworksJournal journal, BuilderEntity worker);
        /** Unknown origin always needs specific authorization for these exact originals, not a natural tag. */
        boolean exactRemovalReviewed(String manifestHash, long pos, long observedEditRevision);
        /** Already loaded, claimed route to the actual standing body and connected post-mutation escape. */
        String standingAndEscapeProblem(PerimeterEarthworksManifest manifest, PerimeterEarthworksJournal journal, BuilderEntity worker);
        /** Normal navigation only; implementation must use the reviewed route and revalidate async endpoints. */
        void navigateReviewedRoute(PerimeterEarthworksManifest manifest, PerimeterEarthworksJournal journal, BuilderEntity worker);
    }
    private final ServerLevel level;
    private final BuilderEntity worker;
    private final BuildArea area;
    private final BuilderWorkGoal nativeGoal;
    private final Authority authority;
    private final BlockPos core;
    private String boundIntent;
    private NativeEarthworksAdapter.Stock boundTool;

    WorkersEarthworksPort(ServerLevel level, BuilderEntity worker, BuildArea area, BlockPos core, Authority authority) {
        this.level = Objects.requireNonNull(level); this.worker = Objects.requireNonNull(worker); this.area = Objects.requireNonNull(area);
        this.core = Objects.requireNonNull(core).immutable(); this.authority = Objects.requireNonNull(authority);
        this.nativeGoal = new BuilderWorkGoal(worker); // Private callback delegate, never another accepted goal's fields.
    }
    BuilderWorkGoal nativeGoal() { return nativeGoal; }
    void navigate(PerimeterEarthworksManifest manifest, PerimeterEarthworksJournal journal) {
        if (leaseProblem(manifest, journal) == null && nativeEligible())
            authority.navigateReviewedRoute(manifest, journal, worker);
    }
    @Override public long gameTime() { return level.getGameTime(); }
    @Override public int nativeTickCount() { return worker.tickCount; }
    @Override public boolean nativeEligible() {
        return worker.currentBuildArea == area && worker.isAlive() && !worker.isPassenger() && !worker.isLeashed()
                && worker.getTarget() == null && !worker.isFleeing && !ProtectedBuilderHandMirror.activeUse(worker)
                && !worker.needsToGetItems() && nativeGoal.canUse();
    }
    String leaseProblem(PerimeterEarthworksManifest manifest, PerimeterEarthworksJournal journal) {
        if (!level.getServer().isSameThread() || worker.level() != level || area.level() != level || area.isRemoved()
                || !manifest.header().builder().equals(worker.getUUID())
                || !manifest.header().owner().equals(WorkersBridge.readWorkerOwner(worker))
                || !manifest.header().dimension().equals(level.dimension().location().toString())) return "Earthworks owner, worker or world changed";
        if (area instanceof ProtectedBuildArea || area.getPersistentData().contains("SiegeProtectedConstructionV1") || PerimeterProjectLink.reserved(worker))
            return "Existing accepted construction cannot be converted to the new earthworks adapter";
        String runtime = WorkersConstructionRuntime.problem(); if (runtime != null) return runtime;
        String lease = authority.newLeaseProblem(manifest, journal, worker, area); if (lease != null) return lease;
        return authority.nativeInventoryProblem(manifest, journal, worker);
    }
    @Override public String admissionProblem(PerimeterEarthworksManifest manifest, PerimeterEarthworksJournal journal) {
        String identity = leaseProblem(manifest, journal); if (identity != null) return identity;
        if (worker.currentBuildArea != area || area.getFreeArea() || !area.stackToFree.isEmpty()
                || !area.stackToPlaceMultiBlock.isEmpty()) return "Unexpected native area or clearing/multipart queue";
        Set<BlockPos> mutations = new HashSet<>();
        for (var cell : manifest.observations().values()) {
            BlockPos pos = BlockPos.of(cell.pos());
            if (!safeLoaded(pos)) return "A reviewed earthworks observation is unloaded or outside the world";
            if (cell.role() == PerimeterEarthworksManifest.Role.WORK) mutations.add(pos);
        }
        // All writes stay inside existing native construction claim authority. Outside approaches are observations only.
        String claims = NativeConstructionPolicy.problem(level, worker, area, manifest.header().owner(),
                "team:" + manifest.header().faction(), core, mutations);
        if (claims != null) return claims;
        var step = manifest.steps().get(journal.nextStep()); BlockPos target = BlockPos.of(step.pos());
        if (step.kind() == PerimeterEarthworksManifest.Kind.CUT && !level.getGameRules().getBoolean(GameRules.RULE_DOBLOCKDROPS))
            return "Native dirt-drop accounting requires block drops to be enabled";
        if (step.kind() == PerimeterEarthworksManifest.Kind.CUT && !authority.exactRemovalReviewed(manifest.hash(), step.pos(),
                manifest.observations().get(step.pos()).editRevision())) return "This exact dirt removal has not been reviewed";
        if (level.getBlockEntity(target) != null) return "A block entity protects the target";
        String neighbors = NativeConstructionGuard.neighborhoodProblem(level, target); if (neighbors != null) return neighbors;
        if (step.kind() == PerimeterEarthworksManifest.Kind.CUT)
            for (int x = -2; x <= 2; x++) for (int y = -2; y <= 2; y++) for (int z = -2; z <= 2; z++) {
                int distance = Math.abs(x) + Math.abs(y) + Math.abs(z);
                if (distance > 0 && distance <= 2 && !stableCutNeighbor(level.getBlockState(target.offset(x, y, z))))
                    return "Dirt removal has an unsupported neighboring dependency";
            }
        AABB body = worker.getBoundingBox();
        for (BlockPos pos : BlockPos.betweenClosed((int)Math.floor(body.minX), (int)Math.floor(body.minY) - 1,
                (int)Math.floor(body.minZ), (int)Math.floor(body.maxX), (int)Math.ceil(body.maxY), (int)Math.floor(body.maxZ)))
            if (!safeLoaded(pos)) return "The worker body or footing crosses unloaded terrain";
        if (!worker.onGround() || (worker.isInWaterOrBubble() || worker.isInLava()) || !level.noCollision(worker, body)
                || !level.getBlockState(worker.blockPosition().below()).isFaceSturdy(level, worker.blockPosition().below(), Direction.UP)
                || body.intersects(new AABB(target)) || worker.blockPosition().below().equals(target)
                || worker.getEyePosition().distanceToSqr(target.getCenter()) > 9)
            return "The actual worker standing position is unsafe for this mutation";
        if (!level.getEntities((Entity)null, new AABB(target), entity -> entity != area && NativeConstructionGuard.blocksPlacement(entity)).isEmpty())
            return "An entity occupies the exact work cell";
        String access = authority.standingAndEscapeProblem(manifest, journal, worker); if (access != null) return access;
        if (step.kind() != PerimeterEarthworksManifest.Kind.CUT) {
            if (!step.after().equals(area.getStateFromPos(target)) || area.findPairedMultiBlockState(target) != null)
                return "Native placement differs from the exact full-block target";
        }
        if (area.stackToPlace.size() > NativeEarthworksAdapter.MAX_STEPS) return "Native pending material queue exceeds this local region";
        for (var block : area.stackToPlace) {
            boolean exact = manifest.steps().stream().anyMatch(s -> s.kind() != PerimeterEarthworksManifest.Kind.CUT
                    && s.pos() == block.getPos().asLong() && s.after().equals(block.getState()));
            if (!exact) return "Native material queue contains off-plan work";
        }
        int[] progress = breakProgress();
        if (step.kind() == PerimeterEarthworksManifest.Kind.CUT && (progress[0] != 0 || progress[1] != 0 || progress[2] != 0)) {
            if (journal.pending() == null || !journal.pending().hash().equals(boundIntent)
                    || !Objects.equals(boundTool, stock(selectedShovel()))) return "Unbound or changed native partial mining progress";
        } else if (step.kind() != PerimeterEarthworksManifest.Kind.CUT && (progress[0] != 0 || progress[1] != 0 || progress[2] != 0))
            return "An earlier native mining target has unresolved partial progress";
        if (journal.pending() != null) boundIntent = journal.pending().hash();
        return null;
    }
    private boolean safeLoaded(BlockPos pos) {
        return pos.getY() >= level.getMinBuildHeight() && pos.getY() < level.getMaxBuildHeight()
                && level.getWorldBorder().isWithinBounds(pos) && level.hasChunkAt(pos);
    }
    @Override public boolean suppliesReady(PerimeterEarthworksManifest.Step step) {
        if (step.kind() == PerimeterEarthworksManifest.Kind.CUT) {
            ItemStack selected = selectedShovel();
            // New shovel predicates are not trusted by protected storage yet. Pause rather than broadening that guard.
            int shovels = 0;
            for (int i = 0; i < worker.getInventory().getContainerSize(); i++)
                if (worker.getInventory().getItem(i).getItem() instanceof ShovelItem) shovels += worker.getInventory().getItem(i).getCount();
            return shovels == 1 && selected.is(Items.IRON_SHOVEL) && selected.getCount() == 1 && !selected.isEnchanted()
                    && !(selected.hasTag() && selected.getTag().getBoolean("Unbreakable"))
                    && selected.getDamageValue() < selected.getMaxDamage() - 1;
        }
        var item = step.after().getBlock().asItem();
        ItemStack matching = worker.getMatchingItem(stack -> stack.is(item));
        if (matching != null && !matching.isEmpty()) {
            // Native matching sees equipment too, while switchMainHandItem only sees main hand/cargo.
            if (matching == worker.getMainHandItem()) return true;
            for (int i = 6; i < worker.getInventory().getContainerSize(); i++) if (matching == worker.getInventory().getItem(i)) return true;
            return false;
        }
        // Genuine native preparation creates the existing trusted material request predicates. This branch cannot place.
        nativeGoal.blockPos = null; nativeGoal.setState(BuilderWorkGoal.State.PREPARE_PLACE_BLOCKS); nativeGoal.tick();
        return false;
    }
    @Override public NativeEarthworksAdapter.Frame snapshot(PerimeterEarthworksManifest manifest, PerimeterEarthworksManifest.Step step) {
        Map<Long, NativeEarthworksAdapter.Cell> cells = new HashMap<>();
        for (long packed : manifest.observations().keySet()) if (!safeLoaded(BlockPos.of(packed))) throw new IllegalStateException("Unloaded observation");
        for (long packed : manifest.observations().keySet()) {
            BlockPos pos = BlockPos.of(packed); cells.put(packed, new NativeEarthworksAdapter.Cell(level.getBlockState(pos), authority.editRevision(pos)));
        }
        var inventory = worker.getInventory();
        if (inventory.getContainerSize() < 6 || inventory.getContainerSize() > 128 || inventory.getItem(5) != worker.getMainHandItem())
            throw new IllegalStateException("Native hand/inventory identity is unverified");
        Map<ItemStack, Boolean> identities = new IdentityHashMap<>(); Map<NativeEarthworksAdapter.Stock, Integer> stock = new HashMap<>();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack item = inventory.getItem(i); if (item == null) throw new IllegalStateException("Missing native inventory stack");
            if (item.isEmpty()) continue;
            if (identities.put(item, true) != null || item.getCount() < 1 || item.getCount() > item.getMaxStackSize())
                throw new IllegalStateException("Aliased or overstacked native inventory");
            stock.merge(stock(item), item.getCount(), Integer::sum);
        }
        AABB dropBox = new AABB(BlockPos.of(step.pos())).inflate(2);
        for (BlockPos pos : BlockPos.betweenClosed((int)Math.floor(dropBox.minX), (int)Math.floor(dropBox.minY), (int)Math.floor(dropBox.minZ),
                (int)Math.floor(dropBox.maxX), (int)Math.floor(dropBox.maxY), (int)Math.floor(dropBox.maxZ)))
            if (!safeLoaded(pos)) throw new IllegalStateException("Unloaded drop observation envelope");
        Map<UUID, NativeEarthworksAdapter.Drop> drops = new HashMap<>();
        var entities = level.getEntitiesOfClass(ItemEntity.class, dropBox);
        if (entities.size() > NativeEarthworksAdapter.MAX_DROPS) throw new IllegalStateException("Too many nearby ground items");
        for (ItemEntity entity : entities) {
            ItemStack item = entity.getItem(); if (item.isEmpty()) continue;
            if (drops.put(entity.getUUID(), new NativeEarthworksAdapter.Drop(stock(item), item.getCount())) != null)
                throw new IllegalStateException("Duplicate ground entity identity");
        }
        Map<UUID, Integer> experience = new HashMap<>();
        var orbs = level.getEntitiesOfClass(ExperienceOrb.class, dropBox);
        if (orbs.size() > NativeEarthworksAdapter.MAX_DROPS) throw new IllegalStateException("Too many ground experience entities");
        for (ExperienceOrb orb : orbs) experience.put(orb.getUUID(), orb.getValue());
        return new NativeEarthworksAdapter.Frame(cells, stock, drops, experience);
    }
    @Override public void invokeExact(PerimeterEarthworksManifest.Step step) {
        BlockPos target = BlockPos.of(step.pos());
        nativeGoal.blockPos = target;
        if (step.kind() == PerimeterEarthworksManifest.Kind.CUT) {
            // Non-null exact target bypasses the native multi-target reordering/LOS-pruning selector, not mining progress.
            nativeGoal.mineBlocks(new Stack<>()); boundTool = stock(worker.getMainHandItem());
        } else nativeGoal.placeBlocks(new Stack<>());
    }
    private ItemStack selectedShovel() {
        ItemStack selected = worker.getMainHandItem(); if (selected.getItem() instanceof ShovelItem) return selected;
        for (int i = 6; i < worker.getInventory().getContainerSize(); i++)
            if (worker.getInventory().getItem(i).getItem() instanceof ShovelItem) return worker.getInventory().getItem(i);
        return ItemStack.EMPTY;
    }
    static boolean stableCutNeighbor(net.minecraft.world.level.block.state.BlockState state) {
        // The old placement predicate admits plants; removing their dirt support is a different effect.
        return state.isAir() || state.is(Blocks.DIRT) || state.is(Blocks.STONE) || state.is(Blocks.COBBLESTONE)
                || state.is(Blocks.STONE_BRICKS) || state.is(Blocks.OAK_PLANKS) || state.is(Blocks.BEDROCK);
    }
    private int[] breakProgress() {
        try {
            Class<?> type = Class.forName("com.talhanation.workers.entities.AbstractWorkerEntity"); int[] values = new int[3]; int index = 0;
            for (String name : new String[]{"currentTimeBreak", "breakingTime", "previousTimeBreak"}) {
                Field field = type.getDeclaredField(name); field.setAccessible(true); values[index++] = field.getInt(worker);
            }
            return values;
        } catch (ReflectiveOperationException unavailable) { throw new IllegalStateException("Pinned native break progress is unreadable", unavailable); }
    }
    private static NativeEarthworksAdapter.Stock stock(ItemStack item) {
        CompoundTag data = item.save(new CompoundTag()); data.remove("id"); data.remove("Count");
        if (data.contains("tag", Tag.TAG_COMPOUND)) {
            CompoundTag tag = data.getCompound("tag"); tag.remove("Damage"); if (tag.isEmpty()) data.remove("tag");
        }
        return new NativeEarthworksAdapter.Stock(BuiltInRegistries.ITEM.getKey(item.getItem()).toString(),
                data.isEmpty() ? "" : canonical(data), item.getDamageValue());
    }
    private static String canonical(Tag tag) {
        if (tag instanceof CompoundTag compound) {
            StringBuilder value = new StringBuilder("{");
            compound.getAllKeys().stream().sorted().forEach(key -> value.append(key.length()).append(':').append(key).append('=').append(canonical(compound.get(key))).append(';'));
            return value.append('}').toString();
        }
        if (tag instanceof ListTag list) { StringBuilder value = new StringBuilder("["); for (Tag item : list) value.append(canonical(item)).append(';'); return value.append(']').toString(); }
        return tag.getId() + ":" + tag.getAsString();
    }
}
