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
    private static final int GUARD_COUNT = 6;
    private CampGuards() {}
    public static void start(ServerLevel level, RaidSavedData data, RaidSavedData.RaidState raid) {
        if (raid.campGuardsStarted || raid.campPos == null || !level.hasChunkAt(raid.campPos)) return;
        if (!com.devfarinsky.siegeoverhaul.compat.CampClaims.owns(level, raid)) return;
        raid.campGuardsStarted = true;
        data.setDirty();
        List<String> guardRoles = doctrine(raid.factionId).guardRoles();
        for (int slot=0; slot<guardRoles.size(); slot++) {
            String id=guardRoles.get(slot);
            int total = data.raids.values().stream().mapToInt(r -> r.raiders.size() + r.campGuards.size()).sum();
            if (total >= RaidConfig.MAX_GLOBAL_RAIDERS.get() || raid.raiders.size()+raid.campGuards.size() >= RaidConfig.MAX_ACTIVE_RAIDERS.get()) break;
            var type = ForgeRegistries.ENTITY_TYPES.getValue(new ResourceLocation("recruits", id));
            if (type == null || !(type.create(level) instanceof Mob guard)) continue;
            boolean placed = false;
            for (BlockPos p : candidates(raid,slot)) {
                if (!level.hasChunkAt(p) || !level.getWorldBorder().isWithinBounds(p)
                        || planned(raid,p) || planned(raid,p.above())
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
                guard.setCustomName(net.minecraft.network.chat.Component.literal(guardName(raid.factionId)));
                guard.getClass().getMethod("setListen", boolean.class).invoke(guard,false);
                guard.getClass().getMethod("setHoldPos", net.minecraft.world.phys.Vec3.class).invoke(guard,guard.position());
                guard.getClass().getMethod("setFollowState", int.class).invoke(guard,3);
                var inventory = guard.getClass().getMethod("getInventory").invoke(guard);
                if (inventory instanceof net.minecraft.world.SimpleContainer container) {
                    container.addItem(new ItemStack(Items.BREAD,16));
                    if (id.equals("bowman") || id.equals("crossbowman") || id.equals("scout"))
                        container.addItem(new ItemStack(Items.ARROW,64));
                }
                com.devfarinsky.siegeoverhaul.items.FactionUniforms.apply(guard,raid.factionId,"guard");
                strengthen(guard,raid.wave);
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
            strengthen(guard,raid.wave);
            applyStructureEffects(raid,guard);
            guard.setNoAi(frozen);
            if (frozen) guard.setTarget(null);
            if (!frozen && raid.campPos != null) {
                try {
                    var nbt=guard.getPersistentData();
                    if (!nbt.contains("SiegeGuardPost")) {
                        int slot=new TreeSet<>(raid.campGuards).headSet(id).size()%GUARD_COUNT;
                        BlockPos post=candidates(raid,slot).stream().filter(p -> safePost(level,raid,p)).findFirst().orElse(guard.blockPosition());
                        nbt.putInt("SiegeGuardSlot",slot); nbt.putLong("SiegeGuardPost",post.asLong());
                    }
                    if(nbt.getInt("SiegeGuardSlot")>=4 && !nbt.getBoolean("SiegeMainGatePost")
                            && raid.warGate.contains("PerimeterGate",net.minecraft.nbt.Tag.TAG_LONG)) {
                        Direction facing=Direction.from2DDataValue(raid.warGate.getInt("PerimeterGateFacing"));
                        BlockPos gate=BlockPos.of(raid.warGate.getLong("PerimeterGate"))
                                .relative(facing.getOpposite(),2)
                                .relative(facing.getClockWise(),nbt.getInt("SiegeGuardSlot")%2==0?3:-3);
                        BlockPos post=null;
                        for(int dy:new int[]{0,1,-1,2,-2}) if(safePost(level,raid,gate.above(dy))) { post=gate.above(dy); break; }
                        if(post!=null) { nbt.putLong("SiegeGuardPost",post.asLong());nbt.putBoolean("SiegeMainGatePost",true); }
                    }
                    if(nbt.getInt("SiegeGuardSlot")<2 && !nbt.getBoolean("SiegeWarGatePost") && WarGate.ready(level,raid)) {
                        BlockPos gate=WarGate.center(raid).relative(WarGate.facing(raid),2)
                                .relative(WarGate.facing(raid).getClockWise(),nbt.getInt("SiegeGuardSlot")==0?2:-2).above();
                        if(safePost(level,raid,gate)) { nbt.putLong("SiegeGuardPost",gate.asLong());nbt.putBoolean("SiegeWarGatePost",true); }
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
                    int leash=doctrine(raid.factionId).leashBlocks();
                    if (guard.getTarget()!=null && guard.getTarget().distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(post))>leash*leash) guard.setTarget(null);
                    guard.setCustomName(net.minecraft.network.chat.Component.literal(guardName(raid.factionId)));
                } catch (ReflectiveOperationException ex) { FactionLogger.LOG.warn("Cannot maintain camp guard orders",ex); }
            }
        }
    }
    static com.devfarinsky.siegeoverhaul.narrative.OlympianHostIdentity.CampDoctrine doctrine(String factionId) {
        return com.devfarinsky.siegeoverhaul.narrative.OlympianHostIdentity.forFaction(factionId).campDoctrine();
    }
    static String guardName(String factionId) {
        var host=com.devfarinsky.siegeoverhaul.narrative.OlympianHostIdentity.forFaction(factionId);
        return host.factionId().isBlank()?"Raider Camp Guard"
                : host.hostName()+" "+host.campDoctrine().guardTitle();
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
    /**
     * Two War Gate sentries, two rear flank posts and two sentries who take up
     * station either side of the camp's main gate once the perimeter wall has
     * one. The central approach itself always stays open.
     */
    static List<BlockPos> candidates(RaidSavedData.RaidState raid, int slot) {
        double x=-Math.cos(raid.approachAngle), z=-Math.sin(raid.approachAngle);
        Direction front=Math.abs(x)>=Math.abs(z)?(x>=0?Direction.EAST:Direction.WEST):(z>=0?Direction.SOUTH:Direction.NORTH);
        Direction side=front.getClockWise();
        BlockPos ideal=slot>=4
                ? raid.campPos.relative(front,CampPerimeter.RADIUS-1).relative(side,slot%2==0?3:-3)
                : raid.campPos.relative(front,slot<2?7:-5).relative(side,slot%2==0?4:-4);
        List<BlockPos> positions=new ArrayList<>();
        // Prefer the assigned horizontal post, but follow small rises and dips.
        addElevations(positions,ideal);
        for(int r=1;r<=2;r++) for(Direction d:Direction.Plane.HORIZONTAL)
            addElevations(positions,ideal.relative(d,r));
        return positions;
    }
    private static void addElevations(List<BlockPos> positions,BlockPos base) {
        for(int dy:new int[]{0,1,-1,2,-2}) positions.add(base.above(dy));
    }
    private static boolean planned(RaidSavedData.RaidState raid,BlockPos pos) {
        return raid.pendingCampBlocks.containsKey(pos.asLong()) || raid.pendingFortifications.containsKey(pos.asLong());
    }
    static boolean safePost(ServerLevel level,RaidSavedData.RaidState raid,BlockPos p) {
        return level.hasChunkAt(p) && level.getWorldBorder().isWithinBounds(p) && level.getBlockState(p).getCollisionShape(level,p).isEmpty()
                && level.getBlockState(p.above()).getCollisionShape(level,p.above()).isEmpty()
                && level.getFluidState(p).isEmpty() && level.getFluidState(p.above()).isEmpty() && level.getBlockState(p.below()).isFaceSturdy(level,p.below(),Direction.UP)
                && !planned(raid,p) && !planned(raid,p.above());
    }
    private static void strengthen(Mob guard, int wave) {
        var nbt = guard.getPersistentData();
        // Guards from before the ramped profile keep the stats they were given.
        if (nbt.getBoolean("SiegeVeteranGuard") && !nbt.contains("SiegeGuardStrengthStep")) return;
        int step = GuardStrength.step(wave, RaidConfig.WAVES.get());
        if (nbt.contains("SiegeGuardStrengthStep") && nbt.getInt("SiegeGuardStrengthStep") >= step) return;
        var health = guard.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH);
        var damage = guard.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE);
        var knockback = guard.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.KNOCKBACK_RESISTANCE);
        // Remember the native stat line once so later steps never stack on themselves.
        if (!nbt.contains("SiegeGuardNativeHealth"))
            nbt.putDouble("SiegeGuardNativeHealth", health != null ? health.getBaseValue() : guard.getMaxHealth());
        if (!nbt.contains("SiegeGuardNativeDamage"))
            nbt.putDouble("SiegeGuardNativeDamage", damage != null ? damage.getBaseValue() : 0.0D);
        double ramp = GuardStrength.ramp(wave, RaidConfig.WAVES.get(), RaidConfig.CAMP_GUARD_STRENGTH.get());
        float oldMax = guard.getMaxHealth(), oldHealth = guard.getHealth();
        if (health != null) health.setBaseValue(GuardStrength.health(nbt.getDouble("SiegeGuardNativeHealth"), ramp));
        // Preserve damage on existing sentries; never heal or refill equipment every tick.
        guard.setHealth(oldMax > 0 ? guard.getMaxHealth() * oldHealth / oldMax : oldHealth);
        if (damage != null) damage.setBaseValue(GuardStrength.damage(nbt.getDouble("SiegeGuardNativeDamage"), ramp));
        if (knockback != null)
            knockback.setBaseValue(Math.max(knockback.getBaseValue(), GuardStrength.knockback(ramp)));
        nbt.putInt("SiegeGuardStrengthStep", step);
    }
    private static final UUID ARMOURY_DAMAGE_MOD = UUID.fromString("8f3a6b2e-1c4d-4e5f-9a7b-2d6c8e0f1a34");
    /** Live-and-die camp benefits, reapplied each pass so destroying a building removes them at once. */
    private static void applyStructureEffects(RaidSavedData.RaidState raid, Mob guard) {
        int regen = CampStructures.guardRegen(CampStructures.standing(raid, CampStructures.Kind.GRANARY));
        if (regen > 0 && guard.getHealth() < guard.getMaxHealth()) guard.heal(regen);
        var damage = guard.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE);
        if (damage == null) return;
        double bonus = CampStructures.armouryDamageBonus(CampStructures.standing(raid, CampStructures.Kind.ARMOURY));
        var existing = damage.getModifier(ARMOURY_DAMAGE_MOD);
        if (bonus > 0) {
            if (existing == null || existing.getAmount() != bonus) {
                if (existing != null) damage.removeModifier(ARMOURY_DAMAGE_MOD);
                damage.addTransientModifier(new net.minecraft.world.entity.ai.attributes.AttributeModifier(
                        ARMOURY_DAMAGE_MOD, "Siege Armoury", bonus,
                        net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADDITION));
            }
        } else if (existing != null) damage.removeModifier(ARMOURY_DAMAGE_MOD);
    }
    public static void cleanup(ServerLevel level, RaidSavedData.RaidState raid) {
        for (UUID id : raid.campGuards) { Entity guard=level.getEntity(id); if (guard != null) guard.discard(); }
        raid.campGuards.clear();
    }
}
