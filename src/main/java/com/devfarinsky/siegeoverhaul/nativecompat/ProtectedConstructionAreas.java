package com.devfarinsky.siegeoverhaul.nativecompat;

import com.devfarinsky.siegeoverhaul.SiegeOverhaul;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** New-only protected native entity; legacy workers:buildarea registrations stay untouched. */
public final class ProtectedConstructionAreas {
    public static final ResourceLocation ID = new ResourceLocation(SiegeOverhaul.MOD_ID, "protected_build_area");
    public static final DeferredRegister<EntityType<?>> TYPES = DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, SiegeOverhaul.MOD_ID);
    public static final RegistryObject<EntityType<ProtectedBuildArea>> TYPE = TYPES.register("protected_build_area", () ->
            EntityType.Builder.<ProtectedBuildArea>of(ProtectedBuildArea::new, MobCategory.MISC)
                    .sized(1.2F, 2.0F).fireImmune().noSummon().build(ID.toString()));

    private ProtectedConstructionAreas() {}

    /** The caller still registers, starts, protects, assigns and pays for this sealed area. */
    public static Entity create(ServerPlayer owner, Mob builder, BlockPos origin,
                                int width, int depth, int height, CompoundTag blueprint) {
        String capabilityProblem = NativeConstructionGuard.availabilityProblem();
        if (capabilityProblem != null) throw new IllegalStateException(capabilityProblem);
        if (owner == null || builder == null || builder.level() != owner.serverLevel())
            throw new IllegalArgumentException("An owned builder in this dimension is required");
        var plan = AcceptedConstructionPlan.decode(origin, Direction.SOUTH, width, depth, height, blueprint);
        BlockPos marker = ConstructionMarkerSite.find(owner, plan);
        if (marker == null) throw new IllegalStateException(
                "Move onto clear ground inside your claim near the build site; no accessible native marker position is available.");
        var area = TYPE.get().create(owner.serverLevel());
        if (area == null) throw new IllegalStateException("Native marker entity is unavailable");
        area.initialize(origin, marker, owner.getUUID(), owner.getGameProfile().getName(), builder.getUUID(),
                width, depth, height, blueprint);
        return area;
    }
}
