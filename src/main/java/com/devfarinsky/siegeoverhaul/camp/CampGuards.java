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
        for (int slot=0; slot<TYPES.length; slot++) {
            String id=TYPES[slot];
            int total = data.raids.values().stream().mapToInt(r -> r.raiders.size() + r.campGuards.size()).sum();
            if (total >= RaidConfig.MAX_GLOBAL_RAIDERS.get() || raid.raiders.size()+raid.campGuards.size() >= RaidConfig.MAX_ACTIVE_RAIDERS.get()) break;
            var type = ForgeRegistries.ENTITY_TYPES.getValue(new ResourceLocation("recruits", id));
            if (type == null || !(type.create(level) instanceof Mob guard)) continue;
            boolean placed = false;
            for (BlockPos p : candidates(raid,slot)) {
                if (!level.hasChunkAt(p) || !level.getWorldBorder().isWithinBounds(p)
                        || raid.pendingCampBlocks.containsKey(p.asLong()) || raid.pendingCampBlocks.containsKey(p.above().asLong())
                        || !level.getFluidState(p).isEmpty() || !level.getBlockState(p.below()).isFaceSturdy(level,p.below(),Direction.UP)) continue;
                guard.moveTo(p.getX()+.5,p.getY(),p.getZ()+.5,0,0);
                if (level.noCollision(guard) && level.getEntities(guard,guard.getBoundingBox()).isEmpty()) { placed=true; break; }
            }
            if (!placed) { guard.discard(); continue; }
            try {
                guard.finalizeSpawn(level, level.getCurrentDifficultyAt(guard.blockPosition()), MobSpawnType.EVENT,null,null);
                guard.getPersistentData().putString(TEAM_TAG,raid.teamKey);
                guard.getPersistentData().putInt("SiegeGuardSlot",slot);
                guard.getPersistentData().putLong("SiegeGuardPost",guard.blockPosition().asLong());
                RecruitsBridge.configureHostileRaidRecruit(guard);
                RecruitsBridge.assignToRaidersFaction(guard);
                guard.getClass().getMethod("setAggroState", int.class).invoke(guard,1);
                guard.setCustomName(net.minecraft.network.chat.Component.literal(com.devfarinsky.siegeoverhaul.compat.RaiderFactions.name(raid.factionId)+" Camp Guard"));
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
            if (!frozen && raid.campPos != null) {
                try {
                    var nbt=guard.getPersistentData();
                    if (!nbt.contains("SiegeGuardPost")) {
                        int slot=new TreeSet<>(raid.campGuards).headSet(id).size()%TYPES.length;
                        BlockPos post=candidates(raid,slot).stream().filter(p -> safePost(level,raid,p)).findFirst().orElse(guard.blockPosition());
                        nbt.putInt("SiegeGuardSlot",slot); nbt.putLong("SiegeGuardPost",post.asLong());
                    }
                    BlockPos post=BlockPos.of(nbt.getLong("SiegeGuardPost"));
                    if (!safePost(level,raid,post)) {
                        post=candidates(raid,nbt.getInt("SiegeGuardSlot")).stream().filter(p -> safePost(level,raid,p)).findFirst().orElse(post);
                        nbt.putLong("SiegeGuardPost",post.asLong());
                    }
                    guard.getClass().getMethod("setAggroState",int.class).invoke(guard,1);
                    guard.getClass().getMethod("setListen",boolean.class).invoke(guard,false);
                    guard.getClass().getMethod("setHoldPos",net.minecraft.world.phys.Vec3.class).invoke(guard,net.minecraft.world.phys.Vec3.atBottomCenterOf(post));
                    if (!Integer.valueOf(3).equals(guard.getClass().getMethod("getFollowState").invoke(guard)))
                        guard.getClass().getMethod("setFollowState",int.class).invoke(guard,3);
                    if (guard.getTarget()!=null && guard.getTarget().distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(post))>144) guard.setTarget(null);
                    guard.setCustomName(net.minecraft.network.chat.Component.literal(com.devfarinsky.siegeoverhaul.compat.RaiderFactions.name(raid.factionId)+" Camp Guard"));
                } catch (ReflectiveOperationException ex) { FactionLogger.LOG.warn("Cannot maintain camp guard orders",ex); }
            }
        }
    }
    public static void muster(ServerLevel level,RaidSavedData.RaidState raid) {
        int slot=0;
        for(UUID id:new TreeSet<>(raid.raiders)) {
            Entity entity=level.getEntity(id);
            if(!(entity instanceof Mob mob) || mob.isPassenger())continue;
            BlockPos post=raid.campPos.offset(-6+(slot%6)*2,0,-4+((slot/6)%5)*2); slot++;
            if(!safePost(level,raid,post))continue;
            try {
                var nbt=mob.getPersistentData();
                if(!nbt.contains("SiegeMusterAggro")) nbt.putInt("SiegeMusterAggro",(Integer)mob.getClass().getMethod("getState").invoke(mob));
                mob.getClass().getMethod("setAggroState",int.class).invoke(mob,1);
                mob.getClass().getMethod("setHoldPos",net.minecraft.world.phys.Vec3.class).invoke(mob,net.minecraft.world.phys.Vec3.atBottomCenterOf(post));
                if(!Integer.valueOf(3).equals(mob.getClass().getMethod("getFollowState").invoke(mob)))
                    mob.getClass().getMethod("setFollowState",int.class).invoke(mob,3);
            } catch(ReflectiveOperationException ex) { FactionLogger.LOG.warn("Cannot muster camp unit",ex); }
        }
    }
    public static void releaseMuster(Mob mob) {
        var nbt=mob.getPersistentData(); if(!nbt.contains("SiegeMusterAggro"))return;
        try {
            mob.getClass().getMethod("setFollowState",int.class).invoke(mob,0);
            mob.getClass().getMethod("setAggroState",int.class).invoke(mob,nbt.getInt("SiegeMusterAggro"));
            nbt.remove("SiegeMusterAggro");
        } catch(ReflectiveOperationException ex) { FactionLogger.LOG.warn("Cannot release mustered unit",ex); }
    }
    /** Two gate sentries and two rear flank posts; the central approach remains open. */
    static List<BlockPos> candidates(RaidSavedData.RaidState raid, int slot) {
        double x=-Math.cos(raid.approachAngle), z=-Math.sin(raid.approachAngle);
        Direction front=Math.abs(x)>=Math.abs(z)?(x>=0?Direction.EAST:Direction.WEST):(z>=0?Direction.SOUTH:Direction.NORTH);
        Direction side=front.getClockWise();
        BlockPos ideal=raid.campPos.relative(front,slot<2?7:-5).relative(side,slot%2==0?4:-4);
        List<BlockPos> positions=new ArrayList<>(); positions.add(ideal);
        for(int r=1;r<=2;r++) for(Direction d:Direction.Plane.HORIZONTAL) positions.add(ideal.relative(d,r));
        return positions;
    }
    private static boolean safePost(ServerLevel level,RaidSavedData.RaidState raid,BlockPos p) {
        return level.hasChunkAt(p) && level.getBlockState(p).getCollisionShape(level,p).isEmpty()
                && level.getBlockState(p.above()).getCollisionShape(level,p.above()).isEmpty()
                && level.getFluidState(p).isEmpty() && level.getBlockState(p.below()).isFaceSturdy(level,p.below(),Direction.UP)
                && !raid.pendingCampBlocks.containsKey(p.asLong()) && !raid.pendingCampBlocks.containsKey(p.above().asLong());
    }
    public static void cleanup(ServerLevel level, RaidSavedData.RaidState raid) {
        for (UUID id : raid.campGuards) { Entity guard=level.getEntity(id); if (guard != null) guard.discard(); }
        raid.campGuards.clear();
    }
}
