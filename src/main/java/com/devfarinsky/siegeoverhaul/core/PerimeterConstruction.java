package com.devfarinsky.siegeoverhaul.core;

import com.devfarinsky.siegeoverhaul.FactionLogger;
import com.devfarinsky.siegeoverhaul.RaidSavedData;
import com.devfarinsky.siegeoverhaul.compat.ClaimBridge;
import com.devfarinsky.siegeoverhaul.compat.RecruitsClaimsBridge;
import com.devfarinsky.siegeoverhaul.compat.WorkersBridge;
import com.devfarinsky.siegeoverhaul.items.ModItems;
import com.devfarinsky.siegeoverhaul.nativecompat.NativeConstructionGuard;
import com.devfarinsky.siegeoverhaul.nativecompat.BlueprintNetworkBudget;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.*;
import java.util.function.Predicate;

/** Reviewable template-style perimeter commissions. Legacy saved areas are never rewritten. */
public final class PerimeterConstruction {
    static final long MAX_NATIVE_SCAN_WORK = 1_048_576L + 65_536L;
    public static final String SITE_MIN = "SiegeDefenseSiteMin", SITE_MAX = "SiegeDefenseSiteMax";
    public record Quote(BlockPos core, int material, UUID owner, UUID builder,
                        PerimeterStageLayout.Layout layout, Map<Long, net.minecraft.world.level.block.state.BlockState> before,
                        Map<Long, net.minecraft.world.level.block.state.BlockState> clearance, String fingerprint) {
        public Quote { core=core.immutable(); before=Map.copyOf(before); clearance=Map.copyOf(clearance); }
    }
    public record Preparation(Mob builder, PerimeterBlueprint.Plan plan, String claimIdentity, String problem,
                              RecruitsClaimsBridge.TerritorySnapshot territory, Quote quote) {
        Preparation(Mob builder, PerimeterBlueprint.Plan plan, String claimIdentity, String problem,
                    RecruitsClaimsBridge.TerritorySnapshot territory) {
            this(builder,plan,claimIdentity,problem,territory,null);
        }
        Preparation(Mob builder, PerimeterBlueprint.Plan plan, String claimIdentity, String problem) {
            this(builder, plan, claimIdentity, problem, null);
        }
        static Preparation failed(String problem) { return new Preparation(null, null, "", problem); }
        public boolean ready() { return problem == null && builder != null && plan != null && plan.valid(); }
    }
    private PerimeterConstruction() {}

    /** Review creates or refreshes a free plan; no builder orders, block mutations or payment. */
    public static boolean review(ServerPlayer player, BlockPos core, int material) {
        if (material < 0 || material >= TerritoryFortification.MATERIALS.length) return false;
        Preparation prepared = prepare(player, core, material);
        ItemStack stack = ItemStack.EMPTY;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack candidate = player.getInventory().getItem(i);
            if (candidate.is(ModItems.PERIMETER_PLAN.get())) { stack = candidate; break; }
        }
        boolean fresh = stack.isEmpty();
        if (fresh) stack = new ItemStack(ModItems.PERIMETER_PLAN.get());
        try { writePreview(stack, player, core, material, prepared); }
        catch (IllegalArgumentException tooLarge) { return fail(player, "This perimeter is too fragmented to preview safely in one plan."); }
        if (fresh && !player.getInventory().add(stack)) return fail(player, "Make room in your inventory for the free perimeter plan.");
        player.inventoryMenu.broadcastChanges();
        player.sendSystemMessage(Component.literal("Hold your Perimeter Plan to review the exact wall footprint. Use it to confirm after reviewing; sneak-use cancels. "
                + TerritoryFortification.PRICE + " faction Treasury emeralds are charged only after the builder accepts."));
        if (prepared.problem() != null) player.sendSystemMessage(Component.literal(prepared.problem()));
        return true;
    }

    /** Server confirmation rebuilds the quote and checks the entire site again. */
    public static boolean confirm(ServerPlayer player, ItemStack stack) {
        return confirm(player, stack, selection -> prepare(player, selection.core(), selection.material()),
                (prepared, material) -> startJob(player, prepared, material));
    }

    /** Narrow side-effect seam: validation/refresh remains real in lifecycle tests. */
    static boolean confirm(ServerPlayer player, ItemStack stack,
                           java.util.function.Function<PerimeterPreview.Selection, Preparation> prepare,
                           java.util.function.BiPredicate<Preparation, Integer> start) {
        var selection = PerimeterPreview.read(stack, player.getUUID(), player.level().dimension().location(), player.level().getGameTime());
        if (selection == null) return fail(player, "This saved plan cannot be confirmed here. Use it as its original owner in the original dimension, or refresh it in Building at your core.");
        if (!selection.canConfirm(player.level().getGameTime())) return false;
        if (player.distanceToSqr(selection.core().getX() + .5, selection.core().getY(), selection.core().getZ() + .5) > 256.0 * 256.0)
            return fail(player, "Return within 256 blocks of your core before confirming this perimeter.");
        Preparation prepared = prepare.apply(selection);
        String hash = fingerprint(prepared, selection.core(), selection.material());
        if (!prepared.ready() || !selection.ready() || !selection.fingerprint().equals(hash)) {
            try { writePreview(stack, player, selection.core(), selection.material(), prepared); }
            catch (IllegalArgumentException tooLarge) { PerimeterPreview.clear(stack); return fail(player, "The changed plan is too fragmented to preview safely."); }
            player.inventoryMenu.broadcastChanges();
            return fail(player, prepared.problem() == null
                    ? "The plan changed. Review the refreshed footprint and materials, then use it again to confirm. No payment taken."
                    : prepared.problem());
        }
        if (!start.test(prepared, selection.material())) return false;
        PerimeterPreview.clear(stack);
        if (!player.isCreative()) stack.shrink(1);
        player.inventoryMenu.broadcastChanges();
        return true;
    }

    public static Preparation prepare(ServerPlayer player, BlockPos core, int material) {
        if (player == null || core == null || material < 0 || material >= TerritoryFortification.MATERIALS.length)
            return Preparation.failed("Invalid perimeter selection.");
        ServerLevel level = player.serverLevel(); String key = SiegeCore.key(player);
        var corePoint = SiegeCore.point(player.server, key);
        if (!player.isAlive() || player.isSpectator() || !player.mayBuild() || !level.dimension().equals(Level.OVERWORLD)
                || corePoint == null || !core.equals(corePoint.pos()))
            return Preparation.failed("Use your active Siege Core in your faction's Overworld claim.");
        if (!WorkersBridge.available() || !RecruitsClaimsBridge.available())
            return Preparation.failed("Perimeter construction requires Villager Recruits and Workers 2.");
        var anchor = RaidSavedData.get(player.server).anchors.get(key);
        var claim = anchor == null ? Optional.<RecruitsClaimsBridge.ClaimSnapshot>empty()
                : RecruitsClaimsBridge.resolveDefendingClaim(level, anchor);
        if (claim.isEmpty()) return Preparation.failed("Your core needs a valid faction claim.");
        var nativeClaim = claim.get();
        var territory = RecruitsClaimsBridge.getFactionTerritory(level, nativeClaim.ownerFactionStringId(), PerimeterTerritory.MAX_CHUNKS);
        if (!territory.ready()) return Preparation.failed(territory.problem());
        if (!territory.chunks().contains(new ChunkPos(core)))
            return Preparation.failed("Your active core must be inside the complete faction territory.");
        var identity = anchor.withIdentity(nativeClaim.ownerFactionStringId(), anchor.teamDisplay());
        var limits = new PerimeterBlueprint.Limits(PerimeterTerritory.MAX_CHUNKS, TerritoryFortification.MAX_PERIMETER_BLOCKS,
                20480, PerimeterPreview.MAX_CELLS, TerritoryFortification.FOUNDATION_DEPTH,
                level.getMinBuildHeight(), level.getMaxBuildHeight(), 256, 1048576L);
        var palette = switch (material) {
            case 1 -> PerimeterBlueprint.Palette.COBBLESTONE;
            case 2 -> PerimeterBlueprint.Palette.OAK;
            default -> PerimeterBlueprint.Palette.STONE_BRICKS;
        };
        var plan = PerimeterBlueprint.create(territory.chunks(), (x, z) -> {
            WallSurface.Ground ground = WallSurface.inspectGround(level, new BlockPos(x, core.getY(), z));
            return ground.base() == null ? PerimeterBlueprint.Surface.blocked(ground.problem())
                    : PerimeterBlueprint.Surface.ready(ground.base().getY());
        }, palette, limits);
        String claimIdentity = key + ":" + nativeClaim.ownerFactionStringId() + ":"
                + territory.chunks().stream().map(ChunkPos::toLong).sorted().toList();
        if (!plan.valid()) return new Preparation(null, plan, claimIdentity, plan.problemSummary(), territory);
        if (!nativeScanWithinBudget(plan)) return new Preparation(null, plan, claimIdentity,
                "The complete perimeter exceeds the bounded native Workers scan budget. Use smaller manual sections; no payment or partial job is created.", territory);
        PerimeterStageLayout.Layout layout;
        try {
            layout=PerimeterStageLayout.partition(plan, part -> BlueprintNetworkBudget.problem(
                    TerritoryFortification.blueprint(part.targets(),part.min(),part.max())));
        } catch(IllegalArgumentException unavailable) {
            return new Preparation(null,plan,claimIdentity,"The complete perimeter cannot be represented as bounded native sections. No payment or partial job is created.",territory);
        }
        Map<ChunkPos, Boolean> permissions = new HashMap<>();
        Predicate<BlockPos> permitted = p -> permissions.computeIfAbsent(new ChunkPos(p), chunk ->
                territory.chunks().contains(chunk)
                        && RecruitsClaimsBridge.isChunkOwnedBy(level, chunk, territory.factionStringId())
                        && !ClaimBridge.isForeignClaim(level, chunk, identity))
                && level.mayInteract(player, p);
        String problem = siteProblem(level, plan, permitted);
        if (problem != null) return new Preparation(null, plan, claimIdentity, problem, territory);
        String nativeProblem = NativeConstructionGuard.availabilityProblem();
        if (nativeProblem != null && !nativeProblem.isBlank()) return new Preparation(null, plan, claimIdentity, nativeProblem, territory);
        var supplySites = plan.columns().stream().flatMap(column -> java.util.stream.Stream.of(
                column.foundationBase(), column.base().above(5))).toList();
        var resources = ConstructionResources.find(level, player, core, territory.chunks(), supplySites);
        if (resources.problem() != null) return new Preparation(null, plan, claimIdentity, resources.problem(), territory);
        Mob builder = resources.builder();
        if (!player.isCreative() && PaymentSource.available(player, TerritoryFortification.PRICE) < TerritoryFortification.PRICE)
            return new Preparation(builder, plan, claimIdentity, "You need " + TerritoryFortification.PRICE + " emeralds in the faction Treasury before commissioning.", territory);
        Map<Long,net.minecraft.world.level.block.state.BlockState> before=new HashMap<>(),clearance=new HashMap<>();
        for(long cell:plan.blocks().keySet())before.put(cell,level.getBlockState(BlockPos.of(cell)));
        for(long cell:plan.clearance())clearance.put(cell,level.getBlockState(BlockPos.of(cell)));
        String hash=PerimeterReviewFingerprint.create(plan,layout,before,clearance,core,material,claimIdentity,player.getUUID(),builder.getUUID());
        Quote quote=new Quote(core,material,player.getUUID(),builder.getUUID(),layout,before,clearance,hash);
        return new Preparation(builder,plan,claimIdentity,null,territory,quote);
    }

    /** Validate every reserved cell, including the hollow body and empty walking headroom, before payment. */
    static String siteProblem(ServerLevel level, PerimeterBlueprint.Plan plan, Predicate<BlockPos> permitted) {
        if (plan == null || !plan.valid()) return "The full perimeter plan is not ready.";
        for (var column : plan.columns()) {
            BlockPos bottom = column.foundationBase();
            if (bottom.getY() - 1 < level.getMinBuildHeight() || column.base().getY() + 5 >= level.getMaxBuildHeight())
                return "The perimeter exceeds the world's build height.";
            if (!level.hasChunkAt(bottom) || !level.getWorldBorder().isWithinBounds(bottom))
                return "The whole perimeter must be loaded and inside the world border.";
        }
        List<BlockPos> remaining = new ArrayList<>();
        for (var column : plan.columns()) {
            BlockPos bottom = column.foundationBase(); BlockPos ground = bottom.below();
            var support = level.getBlockState(ground);
            if (support.hasBlockEntity() || !support.getFluidState().isEmpty() || HirePlacement.dangerous(support)
                    || !support.isFaceSturdy(level, ground, Direction.UP)) return "The perimeter needs dry, safe, solid foundations.";
            for (int y = bottom.getY(); y <= column.base().getY() + 5; y++) {
                BlockPos p = bottom.atY(y);
                if (!permitted.test(p)) return "The entire footprint and walkway must remain inside your faction territory with building permission.";
                var current = level.getBlockState(p); String target = plan.blocks().get(p.asLong());
                String currentId = String.valueOf(ForgeRegistries.BLOCKS.getKey(current.getBlock()));
                boolean already = target != null && target.equals(currentId) && !current.hasBlockEntity()
                        && current.getFluidState().isEmpty();
                // Review exactly the same plant/clearance rule as the sealed native area.
                // In particular, a paired plant cannot be silently accepted as empty headroom.
                String cellProblem = NativeConstructionGuard.initialPlacementProblem(level, p, current,
                        already ? current : Blocks.AIR.defaultBlockState());
                if (cellProblem != null) return cellProblem;
                if (target != null && !already) remaining.add(p.immutable());
                if (target != null && !level.getEntities((Entity) null, new AABB(p), Entity::isAlive).isEmpty())
                    return "Move players, creatures and vehicles out of the planned wall cells.";
            }
        }
        if (remaining.isEmpty()) return "This perimeter is already built. Nothing to commission.";
        String neighborhood = NativeConstructionGuard.placementNeighborhoodProblem(level, remaining);
        if (neighborhood != null) return neighborhood;
        String reservation = ConstructionReservations.problem(level, reservedCells(plan));
        if (reservation != null) return reservation;
        return null;
    }

    /** Reserve wall targets, under-deck cavities and walkway headroom, but not the open courtyard. */
    static Set<BlockPos> reservedCells(PerimeterBlueprint.Plan plan) {
        Set<BlockPos> cells = new HashSet<>();
        plan.blocks().keySet().forEach(p -> cells.add(BlockPos.of(p)));
        plan.clearance().forEach(p -> cells.add(BlockPos.of(p)));
        return Set.copyOf(cells);
    }

    /** Native scan includes the Y endpoint; the protected area indexes only current pending stacks during that scan. */
    static boolean nativeScanWithinBudget(PerimeterBlueprint.Plan plan) {
        if (plan == null || !plan.valid()) return false;
        try {
            long width = (long) plan.max().getX() - plan.min().getX() + 1;
            long depth = (long) plan.max().getZ() - plan.min().getZ() + 1;
            long scannedHeight = (long) plan.max().getY() - plan.min().getY() + 2;
            long volume = Math.multiplyExact(Math.multiplyExact(width, depth), scannedHeight);
            long work = Math.addExact(volume, plan.blocks().size());
            return volume <= 1_048_576L && work <= MAX_NATIVE_SCAN_WORK;
        } catch (ArithmeticException overflow) { return false; }
    }

    static boolean startJob(ServerPlayer player, Preparation prepared, int material) {
        if(player==null || prepared==null || !prepared.ready() || prepared.quote()==null
                || prepared.territory()==null || !prepared.territory().ready())return false;
        Quote quote=prepared.quote();
        if(quote.material()!=material || !quote.owner().equals(player.getUUID())
                || !quote.builder().equals(prepared.builder().getUUID()))return false;
        String exact=PerimeterReviewFingerprint.create(prepared.plan(),quote.layout(),quote.before(),quote.clearance(),
                quote.core(),material,prepared.claimIdentity(),player.getUUID(),prepared.builder().getUUID());
        if(!exact.equals(quote.fingerprint()))return false;
        return com.devfarinsky.siegeoverhaul.nativecompat.NativePerimeterProjects.start(player,prepared.builder(),quote.core(),material,
                prepared.plan(),quote.layout(),quote.before(),quote.clearance(),prepared.territory(),quote.fingerprint());
    }

    private static void writePreview(ItemStack stack, ServerPlayer player, BlockPos core, int material, Preparation result) {
        Map<Long, String> cells = result.plan() == null || !result.plan().valid() ? Map.of() : result.plan().blocks();
        PerimeterPreview.set(stack, player.getUUID(), player.level().dimension().location(), core, material,
                player.level().getGameTime(), fingerprint(result, core, material), result.problem(),
                result.plan() == null || !result.plan().valid() ? "Materials are calculated once a complete safe plan is available."
                        : materials(result.plan())+(result.quote()==null?"":"; "+result.quote().layout().stages().size()+" native sections; one "+TerritoryFortification.PRICE+"-emerald fee"), cells);
    }
    private static String fingerprint(Preparation result, BlockPos core, int material) {
        if(result.quote()!=null)return result.quote().fingerprint();
        // Unready reviews and injected lifecycle-test preparations do not carry an accepted staged quote.
        return result.plan() == null || !result.plan().valid() ? "" : PerimeterPreview.fingerprint(result.plan().blocks(), core, material, result.claimIdentity());
    }
    static String materials(PerimeterBlueprint.Plan plan) {
        return plan.materialCounts().entrySet().stream().map(e -> e.getValue() + " " + e.getKey().replace("minecraft:", "").replace('_', ' '))
                .reduce((a, b) -> a + ", " + b).orElse("no materials");
    }
    private static boolean fail(ServerPlayer player, String message) { player.sendSystemMessage(Component.literal(message)); return false; }
}
