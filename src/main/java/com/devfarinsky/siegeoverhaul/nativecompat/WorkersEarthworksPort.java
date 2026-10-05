package com.devfarinsky.siegeoverhaul.nativecompat;

import com.devfarinsky.siegeoverhaul.compat.WorkersBridge;
import com.devfarinsky.siegeoverhaul.core.PerimeterEarthworksJournal;
import com.devfarinsky.siegeoverhaul.core.PerimeterEarthworksManifest;
import com.devfarinsky.siegeoverhaul.core.PerimeterProjectLink;
import com.talhanation.workers.entities.BuilderEntity;
import com.talhanation.workers.entities.ai.BuilderWorkGoal;
import com.talhanation.workers.entities.workarea.BuildArea;
import com.talhanation.workers.world.BuildBlockParse;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.BlockItem;
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
        /** Must audit the dirt loot/datapack, global modifier and drop-event configuration; vanilla identity alone is insufficient. */
        String nativeDropConfigurationProblem(PerimeterEarthworksManifest manifest, PerimeterEarthworksJournal journal);
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
        String materialApi = pinnedMaterialApiProblem(); if (materialApi != null) return materialApi;
        String lease = authority.newLeaseProblem(manifest, journal, worker, area); if (lease != null) return lease;
        return authority.nativeInventoryProblem(manifest, journal, worker);
    }
    @Override public String admissionProblem(PerimeterEarthworksManifest manifest, PerimeterEarthworksJournal journal) {
        String identity = leaseProblem(manifest, journal); if (identity != null) return identity;
        if (level.captureBlockSnapshots || level.restoringBlockSnapshots) return "Another snapshot/rollback transaction owns world mutations";
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
        if (step.kind() == PerimeterEarthworksManifest.Kind.CUT) {
            String drops = authority.nativeDropConfigurationProblem(manifest, journal); if (drops != null) return drops;
            if (!reviewedDirtToolDispatch(step.before())) return "Live dirt mining tags no longer dispatch the reviewed shovel";
        }
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
        if (step.kind() == PerimeterEarthworksManifest.Kind.FILL) {
            String support = fillSupportProblem(level, manifest, step); if (support != null) return support;
        }
        if (step.kind() != PerimeterEarthworksManifest.Kind.CUT) {
            var parsed = BuildBlockParse.parseBlock(step.after().getBlock());
            if (parsed == null || !exactFullBlockMaterial(step.after(), parsed.getItem(), parsed.wasParsed()))
                return "The live native recipe requires a different item/state contract and a fresh bound quote";
            if (!step.after().equals(area.getStateFromPos(target)) || area.findPairedMultiBlockState(target) != null || !preparationMatchesActive(step))
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
    static String fillSupportProblem(ServerLevel level, PerimeterEarthworksManifest manifest, PerimeterEarthworksManifest.Step step) {
        BlockPos below=BlockPos.of(step.pos()).below();
        if(step.kind()!=PerimeterEarthworksManifest.Kind.FILL || !manifest.observations().containsKey(below.asLong()) || !level.hasChunkAt(below))
            return "A fill needs exact observed loaded footing";
        return level.getBlockEntity(below)==null && level.getFluidState(below).isEmpty()
                &&level.getBlockState(below).isFaceSturdy(level,below,Direction.UP)?null:"A fill has no safe supported footing";
    }
    private boolean safeLoaded(BlockPos pos) {
        return pos.getY() >= level.getMinBuildHeight() && pos.getY() < level.getMaxBuildHeight()
                && level.getWorldBorder().isWithinBounds(pos) && level.hasChunkAt(pos);
    }
    @Override public boolean suppliesReady(PerimeterEarthworksManifest.Step step) {
        if (NativeEarthworksJobs.selected(worker)) return EarthworksInventoryAccess.suppliesReady(worker, step);
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
        if (!preparationMatchesActive(step)) throw new IllegalStateException("Native preparation would not request the active step material");
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
        IdentityHashMap<ItemStack, Boolean> identities = new IdentityHashMap<>(); Map<NativeEarthworksAdapter.Stock, Integer> stock = new HashMap<>();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack item = inventory.getItem(i); if (item == null) throw new IllegalStateException("Missing native inventory stack");
            if (item.isEmpty()) continue;
            claimStackIdentity(identities, item);
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
            claimStackIdentity(identities, item);
            if (drops.put(entity.getUUID(), new NativeEarthworksAdapter.Drop(stock(item), item.getCount())) != null)
                throw new IllegalStateException("Duplicate ground entity identity");
        }
        Map<UUID, Integer> experience = new HashMap<>();
        var orbs = level.getEntitiesOfClass(ExperienceOrb.class, dropBox);
        requireNoExperience(orbs); // Value/UUID alone omits the merged-orb pickup count; this first adapter allows no nearby XP.
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
    static String pinnedMaterialApiProblem() {
        try {
            Class<?> type = BuildBlockParse.class;
            if (type.getMethod("parseBlock", net.minecraft.world.level.block.Block.class).getReturnType() != type
                    || type.getMethod("getItem").getReturnType() != Item.class
                    || type.getMethod("wasParsed").getReturnType() != boolean.class)
                return "The pinned native material parser ABI changed";
            for (var method : type.getMethods()) if (method.getName().equals("parseBlock") && method.getParameterCount() != 1)
                return "A different native material parser ABI needs its own reviewed contract";
            return null;
        } catch (ReflectiveOperationException | LinkageError unavailable) { return "The audited Workers 8351157 material parser is unavailable"; }
    }
    static boolean exactFullBlockMaterial(net.minecraft.world.level.block.state.BlockState target, Item consumed, boolean placeAsBase) {
        if (!(target.is(Blocks.DIRT) || target.is(Blocks.COBBLESTONE) || target.is(Blocks.STONE_BRICKS) || target.is(Blocks.OAK_PLANKS))) return false;
        var placed = placeAsBase && consumed instanceof BlockItem block ? block.getBlock().defaultBlockState() : target;
        return consumed == target.getBlock().asItem() && target.equals(placed);
    }
    static void requireNoExperience(java.util.List<? extends ExperienceOrb> orbs) {
        if (!orbs.isEmpty()) throw new IllegalStateException("Nearby experience needs a separately audited full-orb accounting adapter");
    }
    static void claimStackIdentity(IdentityHashMap<ItemStack, Boolean> seen, ItemStack item) {
        if (item == null || item.isEmpty() || seen.put(item, true) != null || item.getCount() < 1 || item.getCount() > item.getMaxStackSize())
            throw new IllegalStateException("Aliased or overstacked native inventory/ground stack");
    }
    static boolean reviewedDirtToolDispatch(net.minecraft.world.level.block.state.BlockState state) {
        return state.is(Blocks.DIRT) && state.is(BlockTags.MINEABLE_WITH_SHOVEL);
    }
    private boolean preparationMatchesActive(PerimeterEarthworksManifest.Step step) {
        if (area.stackToPlace.isEmpty()) return false;
        int min = area.stackToPlace.stream().mapToInt(block -> block.getPos().getY()).min().orElseThrow();
        return area.stackToPlace.stream().filter(block -> block.getPos().getY() == min)
                .allMatch(block -> block.getState().getBlock().asItem() == step.after().getBlock().asItem());
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
    /** Type/length framing is injective, including delimiters, nesting and unusual UTF-16 strings. */
    static String canonical(Tag tag) {
        String payload;
        if (tag instanceof CompoundTag compound) {
            StringBuilder value = new StringBuilder().append(compound.size()).append(';');
            compound.getAllKeys().stream().sorted().forEach(key -> value.append(stringFrame(key)).append(canonical(compound.get(key))));
            payload = value.toString();
        } else if (tag instanceof ListTag list) {
            StringBuilder value = new StringBuilder().append(list.getElementType()).append(';').append(list.size()).append(';');
            for (Tag item : list) value.append(canonical(item)); payload = value.toString();
        } else if (tag instanceof net.minecraft.nbt.StringTag string) payload = stringFrame(string.getAsString());
        else payload = tag.getAsString(); // Numeric and primitive-array SNBT is ASCII; the tag ID distinguishes types.
        return tag.getId() + ":" + payload.length() + ":" + payload;
    }
    private static String stringFrame(String text) {
        StringBuilder value = new StringBuilder().append(text.length()).append(':');
        for (int i = 0; i < text.length(); i++) {
            int c = text.charAt(i);
            for (int shift = 12; shift >= 0; shift -= 4) value.append(Character.forDigit((c >>> shift) & 15, 16));
        }
        return value.toString();
    }
}
