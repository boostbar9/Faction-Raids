package com.devfarinsky.siegeoverhaul.siege;

import com.devfarinsky.siegeoverhaul.*;
import com.devfarinsky.siegeoverhaul.core.SiegeCore;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.*;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.*;
import net.minecraftforge.event.ForgeEventFactory;
import java.util.EnumSet;

/** A interruptible, melee-range pick strike, never a remote explosion or instant tunnel. */
public final class CommanderWallStrikeGoal extends Goal {
    public static final String CHARGING="SiegeBossWallCharge";
    public static final int WINDUP=60, COOLDOWN=200;
    private final Mob mob;
    private BlockPos target;
    private BlockState original;
    private int work;
    private long hurtStamp;
    public CommanderWallStrikeGoal(Mob mob) { this.mob=mob;setFlags(EnumSet.of(Flag.MOVE,Flag.LOOK,Flag.JUMP)); }
    public static boolean breakable(BlockState state) {
        // Only common full building blocks; no core, storage, obsidian, ores or machinery.
        return state.is(Blocks.STONE) || state.is(Blocks.COBBLESTONE) || state.is(Blocks.MOSSY_COBBLESTONE)
                || state.is(Blocks.STONE_BRICKS) || state.is(Blocks.MOSSY_STONE_BRICKS) || state.is(Blocks.CRACKED_STONE_BRICKS)
                || state.is(Blocks.BRICKS) || state.is(Blocks.DEEPSLATE_BRICKS) || state.is(Blocks.COBBLED_DEEPSLATE)
                || state.is(net.minecraft.tags.BlockTags.PLANKS);
    }
    private RaidSavedData.RaidState raid(ServerLevel level) {
        return RaidSavedData.get(level.getServer()).raids.get(mob.getPersistentData().getString(ModConstants.Tags.RAID_TEAM));
    }
    private boolean allowed(ServerLevel level) {
        var raid=raid(level);
        return mob.isAlive() && !mob.isNoAi() && !mob.isPassenger() && !mob.onClimbable()
                && "commander".equals(mob.getPersistentData().getString(ModConstants.Tags.RAID_ROLE))
                && raid!=null && raid.wave>0 && raid.preparationTicks<=0
                && RaidConfig.ENABLE_GATE_BREACHING.get()
                && raid.breachedBlocks.size()<RaidConfig.MAX_RESTORABLE_BLOCKS.get()
                && ForgeEventFactory.getMobGriefingEvent(level,mob);
    }
    private boolean reachable(ServerLevel level,BlockPos pos) {
        Vec3 end=Vec3.atCenterOf(pos);
        if(mob.getEyePosition().distanceToSqr(end)>9 || !level.hasChunkAt(pos))return false;
        var hit=level.clip(new ClipContext(mob.getEyePosition(),end,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,mob));
        return hit.getType()==HitResult.Type.BLOCK && hit.getBlockPos().equals(pos);
    }
    @Override public boolean canUse() {
        if(!(mob.level() instanceof ServerLevel level) || !allowed(level) || RaiderLadderGoal.assigned(mob))return false;
        long now=level.getGameTime(),next=mob.getPersistentData().getLong("SiegeBossNextStrike");
        if(next>now && next<=now+COOLDOWN)return false;
        if(mob.tickCount%10!=0)return false;
        var raid=raid(level);var point=SiegeCore.point(level.getServer(),raid.teamKey);if(point==null)return false;
        Vec3 toward=Vec3.atCenterOf(point.pos()).subtract(mob.position()).multiply(1,0,1);
        target=null;double best=Double.MAX_VALUE;
        for(BlockPos p:BlockPos.betweenClosed(mob.blockPosition().offset(-2,0,-2),mob.blockPosition().offset(2,1,2))) {
            Vec3 offset=Vec3.atCenterOf(p).subtract(mob.position()).multiply(1,0,1);
            if(offset.dot(toward)<=0 || !level.hasChunkAt(p) || !SiegeCore.claimed(level,p,raid.teamKey)
                    || raid.campBlocks.containsKey(p.asLong()) || !breakable(level.getBlockState(p))
                    || level.getBlockState(p).hasBlockEntity() || !level.getFluidState(p).isEmpty()
                    || !reachable(level,p))continue;
            double distance=mob.distanceToSqr(Vec3.atCenterOf(p));
            if(distance<best){best=distance;target=p.immutable();}
        }
        if(target==null)return false;
        original=level.getBlockState(target);return true;
    }
    @Override public void start() {
        work=0;hurtStamp=mob.getPersistentData().getLong("SiegeBossHurtAt");
        mob.getPersistentData().putBoolean(CHARGING,true);mob.getNavigation().stop();
        if(mob.level() instanceof ServerLevel level) {
            level.playSound(null,mob.blockPosition(),SoundEvents.ANVIL_LAND,SoundSource.HOSTILE,.8F,.6F);
            for(var player:level.players())if(player.distanceToSqr(mob)<1024)player.displayClientMessage(
                    net.minecraft.network.chat.Component.literal("Commander winding up a wall strike — hit or knock them back to interrupt!"),true);
        }
    }
    @Override public boolean canContinueToUse() {
        if(!(mob.level() instanceof ServerLevel level) || target==null || work>=WINDUP || !allowed(level))return false;
        return mob.getPersistentData().getLong("SiegeBossHurtAt")==hurtStamp && reachable(level,target)
                && level.getBlockState(target).equals(original)
                && SiegeCore.claimed(level,target,mob.getPersistentData().getString(ModConstants.Tags.RAID_TEAM));
    }
    @Override public boolean requiresUpdateEveryTick(){return true;}
    @Override public void tick() {
        if(!(mob.level() instanceof ServerLevel level) || !canContinueToUse())return;
        mob.getNavigation().stop();mob.getLookControl().setLookAt(target.getX()+.5,target.getY()+.5,target.getZ()+.5,30,30);
        work++;
        level.destroyBlockProgress(mob.getId(),target,Math.min(9,work*10/WINDUP));
        if(work%10==0){mob.swing(InteractionHand.MAIN_HAND);level.sendParticles(ParticleTypes.CRIT,target.getX()+.5,target.getY()+.5,target.getZ()+.5,6,.4,.4,.4,.02);}
        if(work<WINDUP)return;
        var raid=raid(level);
        if(raid==null || !breakable(level.getBlockState(target)) || !level.getBlockState(target).equals(original))return;
        var snapshot=BlockRestoration.serialize(level,target);
        if(snapshot.isEmpty())return;
        raid.breachedBlocks.putIfAbsent(target.asLong(),snapshot);
        RaidSavedData.get(level.getServer()).setDirty();
        level.setBlock(target,Blocks.AIR.defaultBlockState(),Block.UPDATE_ALL | Block.UPDATE_SUPPRESS_DROPS);
        level.playSound(null,target,SoundEvents.GENERIC_EXPLODE,SoundSource.HOSTILE,.6F,1.2F);
    }
    @Override public void stop() {
        if(mob.level() instanceof ServerLevel level) {
            if(target!=null)level.destroyBlockProgress(mob.getId(),target,-1);
            mob.getPersistentData().putLong("SiegeBossNextStrike",level.getGameTime()+COOLDOWN);
        }
        mob.getPersistentData().remove(CHARGING);target=null;work=0;
    }
}
