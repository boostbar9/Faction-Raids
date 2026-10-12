package com.devfarinsky.siegeoverhaul.nativecompat;

import com.devfarinsky.siegeoverhaul.SiegeOverhaul;
import com.talhanation.recruits.config.RecruitsServerConfig;
import com.talhanation.recruits.entities.AbstractChunkLoaderEntity;
import com.talhanation.workers.entities.BuilderEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.ForcedChunksSavedData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.portal.PortalInfo;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.ITeleporter;
import net.minecraftforge.common.world.ForgeChunkManager;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.function.Function;

/**
 * QA-only supported native transit. The driver owns END-tick scheduling, bounded natural
 * eligibility windows, project authority, provider parity and global material accounting.
 * This object survives reopen, but never retains a ServerLevel or live actor between calls.
 */
final class NativeActiveHelperTransit {
    private static final BlockPos HOLDING_FEET = new BlockPos(8, 160, 8);
    private static final BlockPos RETURN_FEET = new BlockPos(137, 65, 9);
    private static final ResourceKey<Level> HOLDING_DIMENSION = Level.NETHER;
    private static final String HAND_PENDING = "SiegeProtectedHandRefreshPending";
    private static final String PROTECTED_AREA_LINK = "SiegeProtectedAreaReceipt"; // Existing production key, read only.
    private static final int HOLDING_CHUNK_X = 0, HOLDING_CHUNK_Z = 0;
    private final UUID helper, ticketOwner;
    private final Map<String, Object> report;
    private final List<Map<String, Object>> tickets = new ArrayList<>(), events = new ArrayList<>();
    private final Map<BlockPos, BlockState> holdingBaseline = new LinkedHashMap<>(), returnBaseline = new LinkedHashMap<>();
    private Map<String, Tag> ticketBefore;
    private boolean setup, held, poisoned;
    private long sequence;
    private Transfer active; // Synchronous call only; always discarded in finally.

    /** Mutable NBT never escapes: constructor and every accessor deep-copy values. */
    public record Inventory(List<CompoundTag> slots, CompoundTag main, CompoundTag off,
                            int beforeItemSlot, CompoundTag persistent) {
        public Inventory {
            slots = copySlots(slots); main = main.copy(); off = off.copy(); persistent = persistent.copy();
        }
        @Override public List<CompoundTag> slots() { return copySlots(slots); }
        @Override public CompoundTag main() { return main.copy(); }
        @Override public CompoundTag off() { return off.copy(); }
        @Override public CompoundTag persistent() { return persistent.copy(); }
    }

    private static final class Transfer {
        final BuilderEntity original;
        final ResourceKey<Level> destination;
        final Inventory before;
        final UUID owner;
        final int command;
        BuilderEntity joined;
        Inventory raw, low;
        int portalCalls, repositionCalls, highCalls, lowCalls;
        Transfer(BuilderEntity original, ResourceKey<Level> destination) {
            this.original = original; this.destination = destination;
            before = inventory(original); owner = original.getOwnerUUID(); command = original.getFollowState();
        }
    }

    NativeActiveHelperTransit(UUID helper, Map<String, Object> report) {
        this.helper = Objects.requireNonNull(helper); this.report = Objects.requireNonNull(report);
        UUID selected;
        do { selected = UUID.randomUUID(); } while (selected.equals(helper));
        ticketOwner = selected;
        report.put("activeHelperTicketOwner", ticketOwner.toString());
        report.put("activeHelperTicketEvents", tickets);
        report.put("activeHelperTransitEvents", events);
    }

    /** The sole world-writing entry point, required before commission. */
    void setup(ServerLevel overworld) {
        serverThread(overworld.getServer());
        require(!setup && !poisoned && overworld.dimension().equals(Level.OVERWORLD), "Invalid transit setup");
        require(NativeStagedHandoffFixture.WORLD.equals(overworld.getServer().getWorldData().getLevelName()),
                "Transit setup requires the isolated native QA world");
        require(overworld.getEntity(helper) instanceof BuilderEntity, "Fresh helper is not registered");
        BuilderEntity worker = (BuilderEntity) overworld.getEntity(helper);
        require(!ProtectedBuilderHandLifecycle.selected(worker.getPersistentData())
                && !worker.getPersistentData().hasUUID(PROTECTED_AREA_LINK),
                "Holding terrain must be prepared before commission");
        require(!RecruitsServerConfig.RecruitsChunkLoading.get(), "Native worker chunk loading must be disabled before spawn");
        require(noInheritedChunk(worker), "Fresh helper already has inherited chunk-loading state");
        for (ServerLevel level : overworld.getServer().getAllLevels())
            require(level.getEntity(ticketOwner) == null, "QA ticket UUID must not identify an existing actor");
        assertNoTickets(overworld.getServer());
        ServerLevel holding = holdingLevel(overworld.getServer());
        // Explicit synthetic setup only. No chunk acquisition is permitted elsewhere in this class.
        holding.getChunk(HOLDING_CHUNK_X, HOLDING_CHUNK_Z);
        require(holding.getEntitiesOfClass(Entity.class, new AABB(5, 159, 5, 12, 165, 12)).isEmpty(),
                "Synthetic holding room would overwrite an occupied site");
        for (int x = 5; x <= 11; x++) for (int z = 5; z <= 11; z++) for (int y = 159; y <= 164; y++) {
            BlockPos cell = new BlockPos(x, y, z);
            require(new ChunkPos(cell).equals(new ChunkPos(HOLDING_CHUNK_X, HOLDING_CHUNK_Z)), "Holding setup escaped its chunk");
            require(holding.getBlockEntity(cell) == null, "Holding setup would overwrite a block entity");
            BlockState state = x == 5 || x == 11 || z == 5 || z == 11 || y == 159 || y == 164
                    ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState();
            holdingBaseline.put(cell, state);
        }
        for (int y = RETURN_FEET.getY() - 1; y <= RETURN_FEET.getY() + 3; y++) {
            BlockPos cell = new BlockPos(RETURN_FEET.getX(), y, RETURN_FEET.getZ());
            require(fullChunk(overworld, cell), "Return landing is not already loaded");
            BlockState state = overworld.getBlockState(cell);
            require(y == RETURN_FEET.getY() - 1 ? state.is(Blocks.STONE) : state.isAir(),
                    "Return landing must already be a clear, stone-supported courtyard cell");
            returnBaseline.put(cell, state);
        }
        var oracle = NativeStagedHandoffFixture.independentHollowOracle();
        require(returnBaseline.keySet().stream().noneMatch(cell -> oracle.targets().containsKey(cell.asLong())
                        || oracle.clearance().contains(cell.asLong())), "Return landing overlaps the fixture plan");
        requireUnreserved(holding, holdingBaseline.keySet());
        requireUnreserved(overworld, returnBaseline.keySet());
        // Finish every occupancy/reservation preflight before the first synthetic terrain write.
        for (var cell : holdingBaseline.entrySet()) holding.setBlock(cell.getKey(), cell.getValue(), 3);
        require(safeLanding(holding, worker, HOLDING_FEET) && safeLanding(overworld, worker, RETURN_FEET),
                "Precommission holding/return landing is unsafe");
        setup = true;
        report.put("activeHelperHoldingSetup", Map.of("synthetic", true, "dimension", HOLDING_DIMENSION.location().toString(),
                "feet", HOLDING_FEET.toShortString(), "returnFeet", RETURN_FEET.toShortString(),
                "holdingBaseline", blockEvidence(holdingBaseline), "returnBaseline", blockEvidence(returnBaseline),
                "description", "Precommission sealed breathable stone room; no bed, storage or work marker"));
        verifySetup(overworld.getServer());
    }

    ServerLevel holdingLevel(MinecraftServer server) {
        ServerLevel level = server.getLevel(HOLDING_DIMENSION);
        require(level != null, "The intended vanilla holding dimension does not exist");
        return level;
    }
    BlockPos holdingFeet() { return HOLDING_FEET; }
    BlockPos returnFeet() { return RETURN_FEET; }

    boolean holdingReady(MinecraftServer server) {
        serverThread(server); healthy();
        return ready(holdingLevel(server), HOLDING_FEET);
    }
    boolean returnReady(ServerLevel overworld, BuilderEntity worker) {
        serverThread(overworld.getServer()); healthy();
        require(overworld.dimension().equals(Level.OVERWORLD), "Return dimension changed");
        if (!ready(overworld, RETURN_FEET)) return false;
        verifyBaseline(overworld, returnBaseline); requireUnreserved(overworld, returnBaseline.keySet());
        return safeLanding(overworld, worker, RETURN_FEET);
    }

    /** No loading: call only after holdingReady when the holding geometry is needed. */
    void verifySetup(MinecraftServer server) {
        serverThread(server); require(setup, "Holding setup has not completed");
        ServerLevel holding = holdingLevel(server), overworld = server.overworld();
        require(fullChunk(holding, HOLDING_FEET) && fullChunk(overworld, RETURN_FEET),
                "Baseline verification requires already available chunks");
        verifyBaseline(holding, holdingBaseline); verifyBaseline(overworld, returnBaseline);
        requireUnreserved(holding, holdingBaseline.keySet()); requireUnreserved(overworld, returnBaseline.keySet());
    }

    void ensureHoldingTicket(MinecraftServer server) {
        serverThread(server); healthy(); require(setup, "Ticket requested before setup");
        if (held) {
            Map<String, Tag> current = census(server);
            requireOwnedCount(current, 1); require(unrelated(current).equals(ticketBefore), "Unrelated persistent tickets changed");
            return; // Do not make a duplicate native add call.
        }
        require(ticketBefore == null, "Previous QA ticket intent is unresolved");
        Map<String, Tag> before = census(server); requireOwnedCount(before, 0);
        ticketBefore = before;
        ticketEvent("add-pending", before, null); // Intent is visible BEFORE the potentially partial native call.
        try {
            boolean result = ForgeChunkManager.forceChunk(holdingLevel(server), SiegeOverhaul.MOD_ID, ticketOwner,
                    HOLDING_CHUNK_X, HOLDING_CHUNK_Z, true, true);
            Map<String, Tag> after = census(server);
            ticketEvent("add-return", after, result);
            require(result, "Native holding-ticket add returned false");
            requireOwnedCount(after, 1); require(unrelated(after).equals(before), "Ticket add changed unrelated census entries");
            held = true;
        } catch (Throwable problem) {
            poisoned = true;
            ticketFailure("add-failed", problem);
            inspectFailure(server, "add-exception-census", problem);
            try { cleanPending(server); } catch (Throwable cleanup) { problem.addSuppressed(cleanup); }
            throw new IllegalStateException("Holding ticket add failed; fresh world is unusable", problem);
        }
    }

    void releaseHoldingTicket(MinecraftServer server) {
        serverThread(server);
        require(ticketBefore != null && held, "No successful QA holding-ticket acquisition to remove");
        try { cleanPending(server); }
        catch (Throwable problem) {
            poisoned = true; ticketFailure("remove-failed", problem);
            inspectFailure(server, "remove-exception-census", problem);
            throw new IllegalStateException("Holding ticket removal is uncertain; fresh world is unusable", problem);
        }
    }

    /** May remove only a preflight-absent tuple covered by this exact pending QA intent. */
    private void cleanPending(MinecraftServer server) {
        require(ticketBefore != null, "Missing QA ticket intent");
        Map<String, Tag> before = census(server);
        Map<String, Tag> currentUnrelated = unrelated(before);
        boolean baselineDiverged = !currentUnrelated.equals(ticketBefore);
        if (baselineDiverged) {
            // Fail the fixture permanently, but do not strand our independently identified ticket.
            // Cleanup must preserve the current unrelated entries, never restore the old census.
            poisoned = true;
            ticketFailure("cleanup-original-census-diverged",
                    new IllegalStateException("Unrelated ticket census changed since the QA add preflight"));
        }
        int owned = ownedCount(before);
        require(owned <= 1, "Ambiguous QA ticket ownership; refusing broad cleanup");
        if (owned == 0) {
            require(!held, "Expected holding-ticket removal is missing");
            ticketEvent("failed-add-no-tracker-tuple", before, null);
            // An exceptional add with no tracker entry still fails permanently; it is not proof of ticket success.
            ticketBefore = null;
            require(!baselineDiverged, "Original ticket census diverged; world remains failed despite no owned tuple");
            return;
        }
        requireOwnedCount(before, 1);
        ticketEvent("remove-pending", before, null);
        boolean result = ForgeChunkManager.forceChunk(holdingLevel(server), SiegeOverhaul.MOD_ID, ticketOwner,
                HOLDING_CHUNK_X, HOLDING_CHUNK_Z, false, true);
        Map<String, Tag> after = census(server);
        ticketEvent("remove-return", after, result);
        require(result, "Native holding-ticket removal returned false");
        requireOwnedCount(after, 0); require(after.equals(currentUnrelated), "Ticket cleanup changed current unrelated census entries");
        held = false; ticketBefore = null;
        require(!baselineDiverged, "Original ticket census diverged; world remains failed after owned-ticket removal");
    }

    void assertNoTickets(MinecraftServer server) {
        serverThread(server);
        Map<String, Tag> current = census(server); requireOwnedCount(current, 0);
        ticketEvent("zero-owned-verified", current, null);
        require(!held && ticketBefore == null && !poisoned, "QA holding-ticket state is unresolved or failed");
    }

    /** Failure/stop cleanup never changes terrain, inventory, goals, links or unrelated tickets. */
    void cleanup(MinecraftServer server) {
        serverThread(server);
        if (ticketBefore != null) {
            try { cleanPending(server); }
            catch (Throwable problem) {
                poisoned = true; ticketFailure("cleanup-failed", problem);
                inspectFailure(server, "cleanup-exception-census", problem);
                throw new IllegalStateException("QA ticket cleanup could not be verified; do not reuse this world", problem);
            }
        }
        assertNoTickets(server);
    }

    /** Driver guarantees ordinary END and a passively observed stable natural eligibility window. */
    BuilderEntity transfer(BuilderEntity original, ServerLevel destination, BlockPos feet) {
        MinecraftServer server = destination.getServer(); serverThread(server); healthy();
        require(setup && active == null && original != null && helper.equals(original.getUUID()), "Invalid helper transfer request");
        require(original.level() instanceof ServerLevel, "Helper source is not a server level");
        ServerLevel source = (ServerLevel) original.level();
        boolean outbound = source.dimension().equals(Level.OVERWORLD) && destination.dimension().equals(HOLDING_DIMENSION)
                && feet.equals(HOLDING_FEET);
        boolean returning = source.dimension().equals(HOLDING_DIMENSION) && destination.dimension().equals(Level.OVERWORLD)
                && feet.equals(RETURN_FEET);
        require(source.getServer() == server && (outbound || returning), "Transfer is outside declared holding/return route");
        List<String> rejected = eligibility(original);
        require(rejected.isEmpty(), "Helper is not naturally transfer-eligible: " + rejected);
        require(ready(source, original.blockPosition()) && ready(destination, feet), "Source/destination is not full, entity-ready and ticking");
        require(held, "Holding transit requires the exact recorded QA ticket");
        Map<String, Tag> currentTickets = census(server); requireOwnedCount(currentTickets, 1);
        require(unrelated(currentTickets).equals(ticketBefore), "Ticket census changed before transfer");
        verifySetup(server);
        require(safeLanding(destination, original, feet), "Declared destination is not collision-free and supported");
        assertUnique(server, helper, original);
        Transfer observation = new Transfer(original, destination.dimension()); active = observation;
        float yaw = original.getYRot(), pitch = original.getXRot();
        Vec3 landing = Vec3.atBottomCenterOf(feet);
        event("transfer-before", original, observation.before);
        try {
            Entity nativeResult = original.changeDimension(destination, new ITeleporter() {
                @Override public PortalInfo getPortalInfo(Entity entity, ServerLevel target,
                                                          Function<ServerLevel, PortalInfo> unusedVanillaPortalInfo) {
                    require(entity == original && target == destination && ++observation.portalCalls == 1,
                            "Unexpected portal-information invocation");
                    return new PortalInfo(landing, Vec3.ZERO, yaw, pitch);
                }
                @Override public Entity placeEntity(Entity entity, ServerLevel current, ServerLevel target, float ignoredYaw,
                                                     Function<Boolean, Entity> nativeReposition) {
                    require(entity == original && current == source && target == destination
                            && ++observation.repositionCalls == 1, "Unexpected native reposition invocation");
                    return nativeReposition.apply(false);
                }
            });
            require(nativeResult instanceof BuilderEntity && nativeResult.getClass() == original.getClass()
                    && nativeResult != original, "Native transfer canceled, failed or returned an unexpected replacement");
            BuilderEntity returned = (BuilderEntity) nativeResult;
            require(observation.portalCalls == 1 && observation.repositionCalls == 1
                    && observation.highCalls == 1 && observation.lowCalls == 1 && observation.joined == returned,
                    "Native transfer lacks one exact event-local destination admission");
            assertSettled(original, returned);
            require(Objects.equals(returned.getOwnerUUID(), observation.owner) && returned.getFollowState() == observation.command,
                    "Settled destination owner/command differs from the pre-transfer actor");
            require(returned.position().equals(landing) && returned.getDeltaMovement().equals(Vec3.ZERO)
                    && returned.getYRot() == yaw && returned.getXRot() == pitch, "Native landing pose differs from reviewed stimulus");
            Inventory settled = inventory(returned);
            require(settled.equals(observation.low), "Settled inventory/persistent receipts differ from LOWEST join");
            require(returned.neededItems != null && returned.neededItems.isEmpty(), "Transfer reconstructed unexpected supply requests");
            event("transfer-settled", returned, settled);
            return returned;
        } catch (Throwable problem) {
            poisoned = true;
            events.add(Map.of("ordinal", ++sequence, "kind", "transfer-failed", "problem", problem.toString(),
                    "sourceRemoved", original.isRemoved(), "sourceReason", String.valueOf(original.getRemovalReason())));
            throw new IllegalStateException("Native transfer failed; never retry or revive this helper", problem);
        } finally { active = null; }
    }

    /** HIGHEST observer: entity-local values only, including the expected temporary source overlap. */
    void onJoin(EntityJoinLevelEvent event) {
        if (!(event.getEntity() instanceof BuilderEntity worker) || !helper.equals(worker.getUUID())
                || event.getLevel().isClientSide()) return;
        Inventory value = inventory(worker); event("join-high", worker, value, event.loadedFromDisk(), event.isCanceled());
        if (active == null) return;
        require(event.getLevel().dimension().equals(active.destination) && !event.loadedFromDisk() && !event.isCanceled()
                && worker != active.original && ++active.highCalls == 1, "Unexpected raw native destination join");
        require(worker.getClass() == active.original.getClass() && Objects.equals(worker.getOwnerUUID(), active.owner)
                && worker.getFollowState() == active.command, "Raw destination type/owner/command changed");
        require(value.equals(active.before), "Native serialization changed cargo, hands or pre-production persistent receipts");
        active.joined = worker; active.raw = value;
    }

    /** LOWEST observer: attest only the exact production hand-arm change, without invoking it. */
    void onJoined(EntityJoinLevelEvent event) {
        if (!(event.getEntity() instanceof BuilderEntity worker) || !helper.equals(worker.getUUID())
                || event.getLevel().isClientSide()) return;
        Inventory value = inventory(worker); event("join-low", worker, value, event.loadedFromDisk(), event.isCanceled());
        if (active == null) return;
        require(worker == active.joined && !event.loadedFromDisk() && !event.isCanceled() && ++active.lowCalls == 1,
                "Unexpected settled native destination join event");
        CompoundTag expected = active.raw.persistent();
        expected.putBoolean(HAND_PENDING, true); // A copied comparison value only; never entity persistent data.
        Inventory armed = new Inventory(active.raw.slots(), active.raw.main(), active.raw.off(), active.raw.beforeItemSlot(), expected);
        require(value.equals(armed) && ProtectedBuilderHandMirror.pending(worker.getPersistentData()),
                "Production join changed more than the expected hand-arm receipt");
        active.low = value;
    }

    void onLeave(EntityLeaveLevelEvent event) {
        if (!(event.getEntity() instanceof BuilderEntity worker) || !helper.equals(worker.getUUID())
                || event.getLevel().isClientSide()) return;
        if (active != null && worker == active.original) {
            // Mob.removeAfterChangingDimensions clears source equipment. Never recensus that
            // removed source as material custody, even if this callback happens before the clear.
            events.add(Map.of("ordinal", ++sequence, "kind", "dimension-source-leave", "uuid", helper.toString(),
                    "dimension", event.getLevel().dimension().location().toString(), "entityTick", worker.tickCount,
                    "removed", worker.isRemoved(), "removalReason", String.valueOf(worker.getRemovalReason()),
                    "sourceInventoryResampled", false));
        } else event("leave-observed-not-final-removal", worker, inventory(worker));
    }

    /** Repeat at ordinary END; later inventory/rebind transitions are the driver's separate proof. */
    void assertSettled(BuilderEntity original, BuilderEntity destination) {
        require(destination.level() instanceof ServerLevel, "Returned helper has no server level");
        ServerLevel level = (ServerLevel) destination.level(); serverThread(level.getServer());
        require(original != destination && original.getClass() == destination.getClass()
                && helper.equals(original.getUUID()) && helper.equals(destination.getUUID())
                && Objects.equals(original.getOwnerUUID(), destination.getOwnerUUID()), "Settled custody identity changed");
        Entity.RemovalReason reason = original.getRemovalReason();
        require(original.isRemoved() && reason == Entity.RemovalReason.CHANGED_DIMENSION
                && !reason.shouldDestroy() && !reason.shouldSave(), "Source removal is not native non-destructive dimension change");
        require(destination.isAlive() && !destination.isRemoved() && level.getEntity(helper) == destination,
                "Returned helper was not successfully registered");
        assertUnique(level.getServer(), helper, destination);
    }

    static void assertUnique(MinecraftServer server, UUID id, BuilderEntity expected) {
        serverThread(server); int count = 0;
        for (ServerLevel level : server.getAllLevels()) {
            Entity registered = level.getEntity(id);
            if (registered != null) require(registered == expected && registered.isAlive() && !registered.isRemoved(),
                    "UUID resolves to another or removed registered actor");
            for (Entity actor : level.getAllEntities()) if (id.equals(actor.getUUID()) && actor.isAlive() && !actor.isRemoved()) {
                require(actor == expected && registered == actor, "Live duplicate or unregistered helper UUID"); count++;
            }
        }
        require(count == 1 && expected.isAlive() && !expected.isRemoved(), "Expected exactly one live registered helper UUID");
    }

    static Inventory inventory(BuilderEntity worker) {
        var container = worker.getInventory();
        require(container != null && container.getContainerSize() >= 6 && container.getContainerSize() <= 128,
                "Native inventory shape unavailable");
        var slots = new ArrayList<CompoundTag>();
        for (int i = 0; i < container.getContainerSize(); i++) slots.add(container.getItem(i).save(new CompoundTag()).copy());
        for (EquipmentSlot equipment : EquipmentSlot.values()) {
            int index = worker.getInventorySlotIndex(equipment);
            require(index >= 0 && index < slots.size()
                    && slots.get(index).equals(worker.getItemBySlot(equipment).save(new CompoundTag())),
                    "Native equipment/container value alias differs: " + equipment);
        }
        return new Inventory(slots, worker.getMainHandItem().save(new CompoundTag()), worker.getOffhandItem().save(new CompoundTag()),
                worker.getBeforeItemSlot(), worker.getPersistentData());
    }

    static List<String> eligibility(BuilderEntity worker) {
        var reasons = new ArrayList<String>();
        if (worker == null) return List.of("missing-helper");
        if (!worker.isAlive() || worker.isRemoved()) reasons.add("dead-or-removed");
        if (worker.getOwnerUUID() == null) reasons.add("missing-owner");
        if (worker.isNoAi() || worker.getFollowState() != 6 || worker.isFleeing) reasons.add("not-ordinary-work-command");
        if (worker.isSleeping()) reasons.add("sleeping");
        if (ProtectedBuilderHandMirror.activeUse(worker)) reasons.add("active-item-use");
        if (worker.isInWaterOrBubble() || worker.isInLava()) reasons.add("liquid");
        if (worker.isPassenger() || worker.isVehicle() || !worker.getPassengers().isEmpty() || worker.isLeashed()) reasons.add("riding-or-leashed");
        if (worker.getTarget() != null || worker.getLastHurtByMob() != null || worker.getLastHurtMob() != null) reasons.add("combat");
        if (worker.getBeforeItemSlot() != -1) reasons.add("BeforeItemSlot-not-closed");
        if (worker.neededItems == null || !worker.neededItems.isEmpty()) reasons.add("native-supply-requests");
        if (ProtectedBuilderHandMirror.pending(worker.getPersistentData())) reasons.add("hand-mirror-pending");
        if (ProtectedBuilderHandMirror.reviewNeeded(worker.getPersistentData())) reasons.add("hand-review-required");
        if (ProtectedInventoryCleanup.outstanding(worker.getPersistentData())) reasons.add("inventory-cleanup");
        if (ProtectedStorageAccess.runningProblem(worker) != null || worker.forcedDeposit) reasons.add("storage-transfer");
        if (!finite(worker.position()) || !finite(worker.getDeltaMovement()) || !Float.isFinite(worker.getYRot())
                || !Float.isFinite(worker.getXRot()) || !Float.isFinite(worker.fallDistance)) reasons.add("nonfinite-pose");
        if (!worker.onGround() || worker.fallDistance != 0) reasons.add("not-grounded");
        if (RecruitsServerConfig.RecruitsChunkLoading.get() || !noInheritedChunk(worker)) reasons.add("native-chunk-loading");
        inventory(worker); // Malformed equipment/cargo is a hard failure, never an eligibility repair.
        return List.copyOf(reasons);
    }

    private static boolean noInheritedChunk(BuilderEntity worker) {
        try {
            Field field = AbstractChunkLoaderEntity.class.getDeclaredField("loadedChunk");
            require(field.trySetAccessible(), "Cannot inspect exact inherited chunk-loading field");
            Object value = field.get(worker);
            require(value instanceof Optional<?>, "Inherited chunk-loading field shape changed");
            return ((Optional<?>) value).isEmpty();
        } catch (ReflectiveOperationException problem) { throw new IllegalStateException("Cannot verify inherited native tickets", problem); }
    }

    private static boolean fullChunk(ServerLevel level, BlockPos feet) {
        return level.getChunkSource().getChunkNow(feet.getX() >> 4, feet.getZ() >> 4) != null;
    }
    private static boolean ready(ServerLevel level, BlockPos feet) {
        return fullChunk(level, feet) && level.areEntitiesLoaded(new ChunkPos(feet).toLong()) && level.isPositionEntityTicking(feet);
    }
    private static boolean safeLanding(ServerLevel level, BuilderEntity worker, BlockPos feet) {
        require(fullChunk(level, feet), "Landing safety must not load a chunk");
        require(worker.getBbWidth() > 0 && worker.getBbWidth() <= .8F && worker.getBbHeight() > 0 && worker.getBbHeight() <= 2F,
                "Unexpected helper dimensions");
        AABB box = worker.getBoundingBox().move(Vec3.atBottomCenterOf(feet).subtract(worker.position()));
        return level.getBlockState(feet.below()).is(Blocks.STONE) && level.getBlockState(feet).isAir()
                && level.getBlockState(feet.above()).isAir() && level.noCollision(worker, box);
    }
    private static void requireUnreserved(ServerLevel level, Set<BlockPos> cells) {
        // Read existing saved data without computeIfAbsent or any production reconciliation/cache write.
        ConstructionEditLedger ledger = level.getDataStorage().get(ConstructionEditLedger::load, "siege_construction_edits");
        require(ledger == null || !ledger.reserves(cells), "Holding/return geometry intersects a protected reservation");
    }
    private static void verifyBaseline(ServerLevel level, Map<BlockPos, BlockState> baseline) {
        for (var entry : baseline.entrySet()) {
            require(fullChunk(level, entry.getKey()), "Baseline read would load an unavailable chunk");
            require(level.getBlockState(entry.getKey()).equals(entry.getValue()) && level.getBlockEntity(entry.getKey()) == null,
                    "Prepared holding/return baseline changed at " + entry.getKey());
        }
    }

    /** Lossless per-entry census; map/list iteration order is not native tracker identity. */
    private Map<String, Tag> census(MinecraftServer server) {
        var values = new TreeMap<String, Tag>();
        for (ServerLevel level : server.getAllLevels()) {
            ForcedChunksSavedData saved = level.getDataStorage().get(ForcedChunksSavedData::load, "chunks");
            CompoundTag root = saved == null ? new ForcedChunksSavedData().save(new CompoundTag()) : saved.save(new CompoundTag());
            require(root.getAllKeys().equals(Set.of("Forced")) || root.getAllKeys().equals(Set.of("Forced", "ForgeForced")),
                    "Unexpected persistent forced-chunk schema");
            require(root.contains("Forced", Tag.TAG_LONG_ARRAY), "Missing vanilla forced-chunk census");
            String dimension = level.dimension().location().toString();
            values.put(dimension + "/Forced", root.get("Forced").copy());
            if (!root.contains("ForgeForced")) continue;
            ListTag mods = exactList(root, "ForgeForced", Tag.TAG_COMPOUND);
            Set<String> seenMods = new java.util.HashSet<>();
            for (Tag modValue : mods) {
                CompoundTag mod = (CompoundTag) modValue;
                require(mod.getAllKeys().equals(Set.of("Mod", "ModForced")) && mod.contains("Mod", Tag.TAG_STRING),
                        "Unexpected mod-ticket schema");
                String modId = mod.getString("Mod"); require(!modId.isBlank() && seenMods.add(modId), "Duplicate/empty ticket mod");
                Set<Long> seenChunks = new java.util.HashSet<>();
                for (Tag chunkValue : exactList(mod, "ModForced", Tag.TAG_COMPOUND)) {
                    CompoundTag chunk = (CompoundTag) chunkValue;
                    require(chunk.contains("Chunk", Tag.TAG_LONG) && Set.of("Chunk", "Blocks", "TickingBlocks", "Entities", "TickingEntities")
                            .containsAll(chunk.getAllKeys()), "Unexpected chunk-ticket schema");
                    long position = chunk.getLong("Chunk"); require(seenChunks.add(position), "Duplicate ticket chunk entry");
                    for (String kind : List.of("Blocks", "TickingBlocks", "Entities", "TickingEntities")) {
                        if (!chunk.contains(kind)) continue;
                        boolean entity = kind.endsWith("Entities");
                        for (Tag owner : exactList(chunk, kind, entity ? Tag.TAG_INT_ARRAY : Tag.TAG_COMPOUND)) {
                            String ownerId = entity ? NbtUtils.loadUUID(owner).toString() : owner.toString();
                            String key = dimension + "/" + modId + "/" + position + "/" + kind + "/" + ownerId;
                            require(values.putIfAbsent(key, owner.copy()) == null, "Duplicate persistent ticket tuple");
                        }
                    }
                }
            }
        }
        return Map.copyOf(values);
    }
    private static ListTag exactList(CompoundTag tag, String name, int elementType) {
        require(tag.contains(name, Tag.TAG_LIST), "Missing ticket list: " + name);
        ListTag result = (ListTag) tag.get(name);
        require(result.isEmpty() || result.getElementType() == elementType, "Ticket list type changed: " + name);
        return result;
    }
    private String ownedKey() {
        return HOLDING_DIMENSION.location() + "/" + SiegeOverhaul.MOD_ID + "/"
                + ChunkPos.asLong(HOLDING_CHUNK_X, HOLDING_CHUNK_Z) + "/TickingEntities/" + ticketOwner;
    }
    private int ownedCount(Map<String, Tag> values) {
        return (int) values.keySet().stream().filter(key -> key.endsWith("/" + ticketOwner)).count();
    }
    private void requireOwnedCount(Map<String, Tag> values, int count) {
        require(ownedCount(values) == count && (count == 0 || values.containsKey(ownedKey())),
                "QA ticket owner is missing, duplicated, or outside its exact dimension/mod/chunk/ticking tuple");
    }
    private Map<String, Tag> unrelated(Map<String, Tag> values) {
        var copy = new TreeMap<>(values); copy.remove(ownedKey()); return Map.copyOf(copy);
    }

    private void ticketEvent(String kind, Map<String, Tag> census, Boolean result) {
        var entry = new LinkedHashMap<String, Object>();
        entry.put("ordinal", ++sequence); entry.put("kind", kind); entry.put("nanoTime", System.nanoTime());
        entry.put("dimension", HOLDING_DIMENSION.location().toString()); entry.put("mod", SiegeOverhaul.MOD_ID);
        entry.put("owner", ticketOwner.toString()); entry.put("chunkX", HOLDING_CHUNK_X); entry.put("chunkZ", HOLDING_CHUNK_Z);
        entry.put("ticking", true); if (result != null) entry.put("nativeReturn", result);
        var snapshot = new TreeMap<String, String>(); census.forEach((key, value) -> snapshot.put(key, value.toString()));
        entry.put("census", Map.copyOf(snapshot)); tickets.add(Map.copyOf(entry));
    }
    private void ticketFailure(String kind, Throwable problem) {
        tickets.add(Map.of("ordinal", ++sequence, "kind", kind, "problem", problem.toString(), "worldReusable", false));
    }
    private void inspectFailure(MinecraftServer server, String kind, Throwable original) {
        try { ticketEvent(kind, census(server), null); }
        catch (Throwable uncertain) {
            original.addSuppressed(uncertain); ticketFailure("failure-census-unavailable", uncertain);
        }
    }
    private void event(String kind, BuilderEntity worker, Inventory inventory) {
        event(kind, worker, inventory, null, null);
    }
    private void event(String kind, BuilderEntity worker, Inventory inventory, Boolean disk, Boolean canceled) {
        var value = new LinkedHashMap<String, Object>();
        value.put("ordinal", ++sequence); value.put("nanoTime", System.nanoTime()); value.put("kind", kind);
        value.put("uuid", worker.getUUID().toString()); value.put("owner", String.valueOf(worker.getOwnerUUID()));
        value.put("dimension", worker.level().dimension().location().toString()); value.put("entityTick", worker.tickCount);
        value.put("inventory", inventoryEvidence(inventory)); value.put("alive", worker.isAlive()); value.put("removed", worker.isRemoved());
        value.put("removalReason", String.valueOf(worker.getRemovalReason())); value.put("followState", worker.getFollowState());
        value.put("mainAliasesSlot5", worker.getMainHandItem() == worker.getInventory().getItem(5));
        value.put("currentArea", worker.currentBuildArea == null ? "null" : worker.currentBuildArea.getUUID().toString());
        value.put("neededItems", worker.neededItems == null ? -1 : worker.neededItems.size());
        // needsToSleep is an off-duty dimension predicate, not actual isSleeping. Never
        // query the dimension from an entity join callback, and never gate Nether return on it.
        if (kind.startsWith("transfer-")) value.put("nativeOffDuty", worker.needsToSleep());
        if (disk != null) value.put("loadedFromDisk", disk); if (canceled != null) value.put("canceled", canceled);
        events.add(Map.copyOf(value));
    }
    private static Map<String, Object> inventoryEvidence(Inventory value) {
        return Map.of("slots", value.slots().stream().map(CompoundTag::toString).toList(), "main", value.main().toString(),
                "off", value.off().toString(), "beforeItemSlot", value.beforeItemSlot(), "persistent", value.persistent().toString());
    }
    private static Map<String, String> blockEvidence(Map<BlockPos, BlockState> baseline) {
        var values = new TreeMap<String, String>(); baseline.forEach((pos, state) -> values.put(pos.toShortString(), state.toString()));
        return Map.copyOf(values);
    }
    private static List<CompoundTag> copySlots(List<CompoundTag> values) { return values.stream().map(CompoundTag::copy).toList(); }
    private static boolean finite(Vec3 vector) { return Double.isFinite(vector.x) && Double.isFinite(vector.y) && Double.isFinite(vector.z); }
    private void healthy() { require(!poisoned, "This fresh helper-transit fixture has already failed"); }
    private static void serverThread(MinecraftServer server) { require(server != null && server.isSameThread(), "Transit requires the ordinary server thread"); }
    private static void require(boolean condition, String message) { if (!condition) throw new IllegalStateException(message); }
}
