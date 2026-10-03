package com.devfarinsky.siegeoverhaul.nativecompat;

import com.talhanation.workers.entities.AbstractWorkerEntity;
import com.talhanation.workers.entities.workarea.BuildArea;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkHooks;

import java.util.UUID;

/**
 * A NEW native BuildArea type with a sealed world origin and native renderer.
 * Native packet setters default to deny, on both sides and before registration.
 * Only bounded server initialization/reload may change the accepted contract.
 */
public final class ProtectedBuildArea extends BuildArea {
    private static final EntityDataAccessor<BlockPos> NATIVE_ORIGIN = SynchedEntityData.defineId(
            ProtectedBuildArea.class, EntityDataSerializers.BLOCK_POS);
    private static final EntityDataAccessor<Boolean> ORIGIN_READY = SynchedEntityData.defineId(
            ProtectedBuildArea.class, EntityDataSerializers.BOOLEAN);
    private static final String ORIGIN = "SiegeNativeOrigin", BUILDER = "SiegeNativeBuilder", VALID = "SiegeNativeSealed";
    private boolean trustedChanges;
    private boolean trustedRemoval;
    private boolean initialized;
    private boolean queuesReady;
    private boolean completionVerified;
    private boolean retirementHandled;
    private UUID reservedBuilder;

    public ProtectedBuildArea(EntityType<?> type, Level level) { super(type, level); }

    @Override protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(NATIVE_ORIGIN, BlockPos.ZERO);
        entityData.define(ORIGIN_READY, false);
    }

    void initialize(BlockPos origin, BlockPos markerFeet, UUID owner, String playerName,
                    UUID builder, int width, int depth, int height, CompoundTag blueprint) {
        if (initialized || level().isClientSide) throw new IllegalStateException("Area already sealed");
        AcceptedConstructionPlan.decode(origin, Direction.SOUTH, width, depth, height, blueprint);
        if (owner == null || builder == null) throw new IllegalArgumentException("Owned builder required");
        trustedChanges = true;
        try {
            entityData.set(NATIVE_ORIGIN, origin.immutable());
            entityData.set(ORIGIN_READY, true);
            setPlayerUUID(owner); setPlayerName(playerName); setTeamStringID(""); setTeamAccess(false);
            setFacing(Direction.SOUTH); setWidthSize(width); setDepthSize(depth); setHeightSize(height);
            setStructureNBT(blueprint); setFreeArea(false);
            setPos(markerFeet.getX() + .5, markerFeet.getY(), markerFeet.getZ() + .5);
            reservedBuilder = builder;
            initialized = true;
        } finally { trustedChanges = false; }
    }

    /** Our bridge calls this only for the already accepted immutable blueprint. */
    public void initializeBlueprint(CompoundTag blueprint) {
        if (level().isClientSide || !initialized || !entityData.get(ORIGIN_READY)
                || !super.getStructureNBT().equals(blueprint)) throw new IllegalStateException("Accepted blueprint changed");
        rebuildAcceptedQueues();
    }

    /** Native restart packets cannot call this; no creative/direct-world-write branch is used. */
    void rebuildAcceptedQueues() {
        if (level().isClientSide || !initialized || !entityData.get(ORIGIN_READY))
            throw new IllegalStateException("Unverified native origin");
        AcceptedConstructionPlan plan;
        try { plan = AcceptedConstructionPlan.capture(this); }
        catch (ReflectiveOperationException unavailable) { throw new IllegalStateException("Native plan cannot be verified", unavailable); }
        for (BlockPos cell : plan.cells.keySet())
            if (!level().hasChunkAt(cell)) throw new IllegalStateException("Native blueprint cells must already be loaded");
        super.setStartBuild(false);
        queuesReady = true;
    }

    public boolean nativeQueuesReady() { return queuesReady; }

    void verifyCompletion() { completionVerified = true; }

    UUID reservedBuilderId() { return reservedBuilder; }
    void projectionAuthorized(boolean value) { super.setAlwaysShowProjection(value); }
    boolean retirementHandled() { return retirementHandled; }
    void removeAuthorized() {
        retirementHandled = true;
        trustedRemoval = true;
        try { super.remove(RemovalReason.DISCARDED); }
        finally { trustedRemoval = false; }
    }


    public boolean abortBeforePayment() {
        if (NativeConstructionGuard.commissionPaid(this)) return false;
        trustedRemoval = true;
        try { super.remove(RemovalReason.DISCARDED); return true; }
        finally { trustedRemoval = false; }
    }

    /** Initial presentation only; the unauthenticated native update packet cannot alter it. */
    public void initializeProjection(boolean value) {
        if (NativeConstructionGuard.commissionPaid(this)) return;
        super.setAlwaysShowProjection(value);
    }

    @Override public void setStartBuild(boolean creative) { /* Native packet path is always denied. */ }
    @Override public void setStructureNBT(CompoundTag tag) {
        if (trustedChanges) super.setStructureNBT(tag.copy());
    }
    @Override public CompoundTag getStructureNBT() {
        CompoundTag tag = super.getStructureNBT();
        return tag == null ? new CompoundTag() : tag.copy();
    }
    @Override public void setWidthSize(int value) { if (trustedChanges) super.setWidthSize(value); }
    @Override public void setDepthSize(int value) { if (trustedChanges) super.setDepthSize(value); }
    @Override public void setHeightSize(int value) { if (trustedChanges) super.setHeightSize(value); }
    @Override public void setFacing(Direction value) { if (trustedChanges) super.setFacing(value); }
    @Override public void setPlayerUUID(UUID value) { if (trustedChanges) super.setPlayerUUID(value); }
    @Override public void setPlayerName(String value) { if (trustedChanges) super.setPlayerName(value); }
    @Override public void setTeamStringID(String value) { if (trustedChanges) super.setTeamStringID(value); }
    @Override public void setTeamAccess(boolean value) { if (trustedChanges) super.setTeamAccess(value); }
    @Override public void setFreeArea(boolean value) { super.setFreeArea(false); }
    @Override public void setAlwaysShowProjection(boolean value) {
        if (trustedChanges) super.setAlwaysShowProjection(value);
    }
    @Override public void setCustomName(Component value) { if (trustedChanges) super.setCustomName(value); }
    @Override public Component getCustomName() {
        Component value = super.getCustomName();
        return value == null ? Component.literal("Commissioned Construction") : value;
    }
    @Override
    @net.minecraftforge.api.distmarker.OnlyIn(net.minecraftforge.api.distmarker.Dist.CLIENT)
    public net.minecraft.client.gui.screens.Screen getScreen(Player player) {
        return new com.devfarinsky.siegeoverhaul.client.ProtectedConstructionScreen(this, player);
    }
    @Override public void setPos(double x, double y, double z) {
        // Entity construction/load and normal client tracking need the base position.
        // Published server markers cannot be moved by either native moveTo overload.
        if (trustedChanges || !isAddedToWorld() || level().isClientSide) super.setPos(x, y, z);
    }
    // Entity.moveTo uses final setPosRaw BEFORE reapplyPosition/setPos. Seal
    // every public native-control overload instead of relying on setPos alone.
    private boolean allowMoveTo() { return trustedChanges || !isAddedToWorld(); }
    @Override public void moveTo(double x, double y, double z) {
        if (allowMoveTo()) super.moveTo(x, y, z);
    }
    @Override public void moveTo(double x, double y, double z, float yaw, float pitch) {
        if (allowMoveTo()) super.moveTo(x, y, z, yaw, pitch);
    }
    @Override public void moveTo(Vec3 pos) { if (allowMoveTo()) super.moveTo(pos); }
    @Override public void moveTo(BlockPos pos, float yaw, float pitch) {
        if (allowMoveTo()) super.moveTo(pos, yaw, pitch);
    }

    @Override public void remove(RemovalReason reason) {
        if (reason != RemovalReason.DISCARDED || trustedRemoval || completionVerified) super.remove(reason);
    }
    @Override public void setDone(boolean value) {
        if (trustedChanges || value && completionVerified) super.setDone(value);
    }
    @Override public void scanFreeArea() { stackToFree.clear(); }
    @Override public void setTime(int value) {
        // Native AI only resets to zero. The unauthenticated move packet adds
        // DONE_TIME; that presentation/control side effect is denied as well.
        if (trustedChanges || value == 0) super.setTime(value);
    }

    @Override public boolean hurt(DamageSource source, float amount) {
        if (source.getEntity() instanceof Player player && player.isCreative() && player.isCrouching()
                && player.hasPermissions(2)) {
            trustedRemoval = true;
            try { return super.hurt(source, amount); }
            finally { trustedRemoval = false; }
        }
        return false;
    }

    @Override public boolean canWorkHere(AbstractWorkerEntity worker) {
        return initialized && queuesReady && reservedBuilder != null && worker != null && reservedBuilder.equals(worker.getUUID())
                && NativeConstructionGuard.commissionPaid(this) && WorkersConstructionRuntime.problem() == null
                && super.canWorkHere(worker);
    }

    @Override public BlockPos getOriginPos() {
        return entityData.get(ORIGIN_READY) ? entityData.get(NATIVE_ORIGIN) : super.getOriginPos();
    }
    @Override public AABB createArea() {
        // The superclass constructor invokes this after defineSynchedData but
        // before subclass fields are initialized. Synced defaults are safe here.
        if (!entityData.get(ORIGIN_READY)) return super.createArea();
        BlockPos origin = getOriginPos();
        BlockPos end = origin.relative(getFacing(), getDepthSize() - 1)
                .relative(getFacing().getClockWise(), getWidthSize() - 1).above(getHeightSize());
        return new AABB(origin, end);
    }
    @Override public AABB getArea() { return createArea(); }
    @Override public boolean shouldRenderAtSqrDistance(double distance) {
        return entityData.get(ORIGIN_READY)
                ? distance < ConstructionTracking.renderDistanceSquared(position(), getArea())
                : super.shouldRenderAtSqrDistance(distance);
    }


    @Override public void readAdditionalSaveData(CompoundTag tag) {
        initialized = false; queuesReady = false; completionVerified = false;
        trustedChanges = true;
        try {
            super.readAdditionalSaveData(tag);
            super.setDone(false); // Completion is revalidated after reload, never trusted from a client control.
            boolean valid = tag.getBoolean(VALID) && tag.contains(ORIGIN) && tag.hasUUID(BUILDER);
            if (valid) {
                entityData.set(NATIVE_ORIGIN, BlockPos.of(tag.getLong(ORIGIN)));
                entityData.set(ORIGIN_READY, true);
                reservedBuilder = tag.getUUID(BUILDER);
                AcceptedConstructionPlan.capture(this);
                initialized = true;
            } else entityData.set(ORIGIN_READY, false);
        } catch (ReflectiveOperationException | RuntimeException unavailable) {
            initialized = false; reservedBuilder = null; entityData.set(ORIGIN_READY, false);
        } finally { trustedChanges = false; }
    }

    @Override public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putBoolean(VALID, initialized && entityData.get(ORIGIN_READY));
        if (initialized && reservedBuilder != null) {
            tag.putLong(ORIGIN, getOriginPos().asLong()); tag.putUUID(BUILDER, reservedBuilder);
        }
    }

    @Override public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
    }
}
