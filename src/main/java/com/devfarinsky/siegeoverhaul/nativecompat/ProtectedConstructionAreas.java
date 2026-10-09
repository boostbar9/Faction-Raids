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
                    .sized(1.2F, 2.0F).fireImmune().noSummon()
                    .clientTrackingRange(ConstructionTracking.TRACKING_CHUNKS).build(ID.toString()));

    public static final RegistryObject<EntityType<EarthworksBuildArea>> EARTHWORKS_TYPE = TYPES.register("earthworks_build_area", () ->
            EntityType.Builder.<EarthworksBuildArea>of(EarthworksBuildArea::new, MobCategory.MISC)
                    .sized(1.2F, 2.0F).fireImmune().noSummon().clientTrackingRange(ConstructionTracking.TRACKING_CHUNKS)
                    .build(new ResourceLocation(SiegeOverhaul.MOD_ID, "earthworks_build_area").toString()));

    private ProtectedConstructionAreas() {}

    /** The caller still registers, starts, protects, assigns and pays for this sealed area. */
    public static Entity create(ServerPlayer owner, Mob builder, BlockPos origin,
                                int width, int depth, int height, CompoundTag blueprint) {
        return create(owner,builder,origin,width,depth,height,blueprint,null,null);
    }
    static Entity createStage(ServerPlayer owner,Mob builder,com.devfarinsky.siegeoverhaul.core.PerimeterProject project) {
        return createStage(owner,builder,project,null);
    }
    static Entity createStage(ServerPlayer owner,Mob builder,com.devfarinsky.siegeoverhaul.core.PerimeterProject project,BlockPos originalMarker) {
        var stage=project.active();if(stage==null)throw new IllegalArgumentException("No native section remains");
        var layout=stage.layout();
        var blueprint=com.devfarinsky.siegeoverhaul.core.TerritoryFortification.blueprint(layout.targets(),layout.min(),layout.max());
        Entity area=create(owner,builder,layout.origin(),layout.width(),layout.depth(),layout.height(),blueprint,project,originalMarker);
        area.setUUID(stage.areaId());return area;
    }
    private static Entity create(ServerPlayer owner, Mob builder, BlockPos origin,
                                 int width, int depth, int height, CompoundTag blueprint,
                                 com.devfarinsky.siegeoverhaul.core.PerimeterProject project,BlockPos originalMarker) {
        String capabilityProblem = NativeConstructionGuard.availabilityProblem();
        if (capabilityProblem != null) throw new IllegalStateException(capabilityProblem);
        if (owner == null || builder == null || builder.level() != owner.serverLevel())
            throw new IllegalArgumentException("An owned builder in this dimension is required");
        var plan = AcceptedConstructionPlan.decode(origin, Direction.SOUTH, width, depth, height, blueprint);
        BlockPos marker = project==null?ConstructionMarkerSite.find(owner, plan)
                :originalMarker==null?ConstructionMarkerSite.find(owner,plan,project.reservation(),project.header().territory())
                :ConstructionMarkerSite.reusable(owner,originalMarker,plan,project.reservation(),project.header().territory())?originalMarker:null;
        if (marker == null) throw new IllegalStateException(
                "Move onto clear ground inside your claim near the build site; no accessible native marker position is available.");
        var nativeBounds = new net.minecraft.world.phys.AABB(origin, origin.relative(Direction.SOUTH, depth - 1)
                .relative(Direction.WEST, width - 1).above(height));
        if (!ConstructionTracking.covers(net.minecraft.world.phys.Vec3.atBottomCenterOf(marker), nativeBounds))
            throw new IllegalStateException("The complete plan is too far from an accessible native marker; use a smaller construction job.");
        if(project!=null)for(var section:project.stages()) {
            var layout=section.layout();var bounds=new net.minecraft.world.phys.AABB(layout.min(),layout.max().offset(1,1,1));
            if(!ConstructionTracking.covers(net.minecraft.world.phys.Vec3.atBottomCenterOf(marker),bounds))
                throw new IllegalStateException("The original shovel site must cover every section's native tracking envelope. Move closer to the territory's center before commissioning.");
        }
        var area = TYPE.get().create(owner.serverLevel());
        if (area == null) throw new IllegalStateException("Native marker entity is unavailable");
        area.initialize(origin, marker, owner.getUUID(), owner.getGameProfile().getName(), builder.getUUID(),
                width, depth, height, blueprint);
        return area;
    }
}
