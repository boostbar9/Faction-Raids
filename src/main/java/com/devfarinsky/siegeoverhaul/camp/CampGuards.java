package com.devfarinsky.siegeoverhaul.camp;

import com.devfarinsky.siegeoverhaul.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.*;
import net.minecraft.world.item.*;
import net.minecraftforge.registries.ForgeRegistries;
import java.util.*;

/** A finite initial garrison, independent of wave completion and never replenished. */
public final class CampGuards {
    public static final String TEAM_TAG = "SiegeCampGuardTeam";
    private static final String[] TYPES = {"recruit_shieldman", "recruit_shieldman", "bowman", "recruit"};
    private CampGuards() {}
    public static void start(ServerLevel level, RaidSavedData data, RaidSavedData.RaidState raid) {
        if (raid.campGuardsStarted || raid.campPos == null || !level.hasChunkAt(raid.campPos)) return;
        if (!com.devfarinsky.siegeoverhaul.compat.CampClaims.owns(level, raid)) return;
        raid.campGuardsStarted = true;
        data.setDirty();
        for (String id : TYPES) {
            int total = data.raids.values().stream().mapToInt(r -> r.raiders.size() + r.campGuards.size()).sum();
            if (total >= RaidConfig.MAX_GLOBAL_RAIDERS.get() || raid.raiders.size()+raid.campGuards.size() >= RaidConfig.MAX_ACTIVE_RAIDERS.get()) break;
            var type = ForgeRegistries.ENTITY_TYPES.getValue(new ResourceLocation("recruits", id));
            if (type == null || !(type.create(level) instanceof Mob guard)) continue;
            boolean placed = false;
            for (int radius=3; radius<=7 && !placed; radius++) for (Direction dir : Direction.Plane.HORIZONTAL) {
                BlockPos p = raid.campPos.relative(dir, radius);
                if (!level.hasChunkAt(p) || !level.getWorldBorder().isWithinBounds(p)
                        || raid.pendingCampBlocks.containsKey(p.asLong()) || raid.pendingCampBlocks.containsKey(p.above().asLong())
                        || !level.getFluidState(p).isEmpty() || !level.getBlockState(p.below()).isFaceSturdy(level,p.below(),Direction.UP)) continue;
                guard.moveTo(p.getX()+.5,p.getY(),p.getZ()+.5,0,0);
                if (level.noCollision(guard) && level.getEntities(guard,guard.getBoundingBox()).isEmpty()) { placed=true; break; }
            }
            if (!placed) { guard.discard(); continue; }
            try {
                guard.finalizeSpawn(level, level.getCurrentDifficultyAt(guard.blockPosition()), MobSpawnType.EVENT,null,null);
                RecruitsBridge.configureHostileRaidRecruit(guard);
                RecruitsBridge.assignToRaidersFaction(guard);
                guard.getClass().getMethod("setListen", boolean.class).invoke(guard,false);
                guard.getClass().getMethod("setHoldPos", net.minecraft.world.phys.Vec3.class).invoke(guard,guard.position());
                guard.getClass().getMethod("setFollowState", int.class).invoke(guard,3);
                var inventory = guard.getClass().getMethod("getInventory").invoke(guard);
                if (inventory instanceof net.minecraft.world.SimpleContainer container) {
                    container.addItem(new ItemStack(Items.BREAD,16));
                    if (id.equals("bowman")) container.addItem(new ItemStack(Items.ARROW,64));
                }
                com.devfarinsky.siegeoverhaul.items.FactionUniforms.apply(guard,raid.factionId,"guard");
                guard.setPersistenceRequired();
                guard.setCanPickUpLoot(false);
                guard.getPersistentData().putString(TEAM_TAG,raid.teamKey);
                if (level.addFreshEntity(guard)) raid.campGuards.add(guard.getUUID());
                else guard.discard();
            } catch (ReflectiveOperationException | RuntimeException ex) {
                guard.discard(); FactionLogger.LOG.warn("Camp guard initialization failed",ex);
            }
        }
        FactionLogger.LOG.info("Camp {} established with {} guards", raid.teamKey, raid.campGuards.size());
        data.setDirty();
    }
    public static void tick(ServerLevel level, RaidSavedData.RaidState raid, boolean frozen) {
        for (UUID id : new ArrayList<>(raid.campGuards)) {
            Entity entity=level.getEntity(id);
            if (entity == null) continue; // unloaded identity is still needed for cleanup
            if (!(entity instanceof Mob guard) || !guard.isAlive()) { raid.campGuards.remove(id); continue; }
            com.devfarinsky.siegeoverhaul.items.FactionUniforms.apply(guard,raid.factionId,"guard");
            guard.setNoAi(frozen);
            if (frozen) guard.setTarget(null);
            // Native hold orders remain authoritative; guards are not redirected with assault waves.
        }
    }
    public static void cleanup(ServerLevel level, RaidSavedData.RaidState raid) {
        for (UUID id : raid.campGuards) { Entity guard=level.getEntity(id); if (guard != null) guard.discard(); }
        raid.campGuards.clear();
    }
}
