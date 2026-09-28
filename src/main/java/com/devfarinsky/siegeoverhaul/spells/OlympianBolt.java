package com.devfarinsky.siegeoverhaul.spells;

import com.devfarinsky.siegeoverhaul.SiegeOverhaul;
import com.devfarinsky.siegeoverhaul.compat.EnemyHiringProtection;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.syncher.*;
import net.minecraft.world.effect.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.*;
import net.minecraftforge.network.NetworkHooks;
import net.minecraftforge.registries.*;

/** Original, non-griefing projectiles. Vanilla swept collision handles walls and fast bolts. */
public class OlympianBolt extends ThrowableItemProjectile {
    public static final DeferredRegister<EntityType<?>> TYPES=DeferredRegister.create(ForgeRegistries.ENTITY_TYPES,SiegeOverhaul.MOD_ID);
    public static final RegistryObject<EntityType<OlympianBolt>> TYPE=TYPES.register("olympian_bolt",()->
            EntityType.Builder.<OlympianBolt>of(OlympianBolt::new,MobCategory.MISC).sized(.25F,.25F)
                    .clientTrackingRange(4).updateInterval(1).build("siegeoverhaul:olympian_bolt"));
    private static final EntityDataAccessor<Integer> KIND=SynchedEntityData.defineId(OlympianBolt.class,EntityDataSerializers.INT);
    public static final int LIGHTNING=22,FIRE=27,WATER=29,ICE=30;
    private int age;
    private double distance;
    public OlympianBolt(EntityType<? extends ThrowableItemProjectile> type,Level level){super(type,level);}
    public static boolean validKind(int kind){return kind==LIGHTNING || kind==FIRE || kind==WATER || kind==ICE;}
    public int kind(){return entityData.get(KIND);}
    public void kind(int value){entityData.set(KIND,validKind(value)?value:FIRE);}
    @Override protected void defineSynchedData(){super.defineSynchedData();entityData.define(KIND,FIRE);}
    @Override protected Item getDefaultItem(){return switch(kind()){case ICE->Items.PRISMARINE_CRYSTALS;case WATER->Items.HEART_OF_THE_SEA;case LIGHTNING->Items.END_ROD;default->Items.FIRE_CHARGE;};}
    @Override protected float getGravity(){return 0;}
    public static void launch(Level level,Player caster,int kind){
        var bolt=new OlympianBolt(TYPE.get(),level);bolt.kind(kind);bolt.setOwner(caster);
        bolt.setPos(caster.getEyePosition());
        double speed=kind==LIGHTNING?4:kind==FIRE?1.2:kind==WATER?1.6:2;
        bolt.setDeltaMovement(caster.getLookAngle().normalize().scale(speed));
        if (level.addFreshEntity(bolt)) level.playSound(null,caster.blockPosition(),
                kind==FIRE?net.minecraft.sounds.SoundEvents.FIRECHARGE_USE:kind==ICE?net.minecraft.sounds.SoundEvents.SNOWBALL_THROW:
                kind==WATER?net.minecraft.sounds.SoundEvents.GENERIC_SPLASH:net.minecraft.sounds.SoundEvents.TRIDENT_THROW,
                net.minecraft.sounds.SoundSource.PLAYERS,kind==WATER?.2F:.6F,1.1F);
    }
    @Override public void tick(){
        // Never load chunks for a travelling spell, and never resume orphaned casts after logout.
        if(!level().isClientSide && ((distance+=getDeltaMovement().length())>48 || ++age>40 || !(getOwner() instanceof Player owner)
                || !owner.isAlive() || owner.isSpectator() || !owner.getAbilities().instabuild
                || !loadedTravel(level(),position(),getDeltaMovement()))){discard();return;}
        super.tick();
        if(level().isClientSide && !isRemoved() && com.devfarinsky.siegeoverhaul.HeroVisualConfig.PARTICLES.get()>0) {
            var particle=switch(kind()){case ICE->ParticleTypes.SNOWFLAKE;case WATER->ParticleTypes.SPLASH;
                case LIGHTNING->ParticleTypes.ELECTRIC_SPARK;default->ParticleTypes.FLAME;};
            // Fixed per-projectile budget; normal particle settings and distance culling apply.
            Vec3 delta=getDeltaMovement();
            for(int i=0;i<(com.devfarinsky.siegeoverhaul.HeroVisualConfig.PARTICLES.get()==1?1:3);i++)level().addParticle(particle,getX()-delta.x*i/3,getY()-delta.y*i/3,getZ()-delta.z*i/3,0,0,0);
        }
    }
    static boolean loadedTravel(Level level,Vec3 from,Vec3 motion) {
        Vec3 to=from.add(motion);
        // A diagonal step can cross a third chunk even when both endpoints are loaded.
        // Include the bolt's half-width, and bound the lookup rectangle to four chunks.
        int minX=net.minecraft.util.Mth.floor(Math.min(from.x,to.x)-.125)>>4;
        int maxX=net.minecraft.util.Mth.floor(Math.max(from.x,to.x)+.125)>>4;
        int minZ=net.minecraft.util.Mth.floor(Math.min(from.z,to.z)-.125)>>4;
        int maxZ=net.minecraft.util.Mth.floor(Math.max(from.z,to.z)+.125)>>4;
        if(maxX-minX>1 || maxZ-minZ>1)return false;
        for(int x=minX;x<=maxX;x++)for(int z=minZ;z<=maxZ;z++)
            if(!level.hasChunkAt(new BlockPos(x<<4,net.minecraft.util.Mth.floor(from.y),z<<4)))return false;
        return true;
    }
    public static boolean eligible(Player owner,LivingEntity target){
        return target!=owner && !(target instanceof Player) && target.isAlive() && !target.isSpectator()
                && !owner.isAlliedTo(target) && EnemyHiringProtection.enemy(target);
    }
    @Override protected boolean canHitEntity(Entity target) {
        // Friendly troops must not absorb casts fired from behind a defensive line.
        return getOwner() instanceof Player owner && target instanceof LivingEntity living
                && eligible(owner, living) && super.canHitEntity(target);
    }
    @Override protected void onHit(HitResult hit){
        if(level().isClientSide)return;
        if(getOwner() instanceof Player owner && owner.isAlive() && owner.getAbilities().instabuild && !owner.isSpectator()){
            if(hit instanceof EntityHitResult entity && entity.getEntity() instanceof LivingEntity target)
                affect(owner,target);
            if(kind()==FIRE) {
                for(var target:level().getEntitiesOfClass(LivingEntity.class,new AABB(hit.getLocation(),hit.getLocation()).inflate(2)))
                    if(!(hit instanceof EntityHitResult entity && entity.getEntity()==target)
                            && target.distanceToSqr(hit.getLocation())<=4 && owner.hasLineOfSight(target)
                            && level().clip(new net.minecraft.world.level.ClipContext(hit.getLocation().subtract(getDeltaMovement().normalize().scale(.1)),target.getEyePosition(),net.minecraft.world.level.ClipContext.Block.COLLIDER,net.minecraft.world.level.ClipContext.Fluid.NONE,this)).getType()==HitResult.Type.MISS)
                        affect(owner,target);
            }
            if(level() instanceof net.minecraft.server.level.ServerLevel server){
                server.sendParticles(kind()==FIRE?ParticleTypes.FLAME:kind()==ICE?ParticleTypes.SNOWFLAKE:kind()==WATER?ParticleTypes.SPLASH:ParticleTypes.ELECTRIC_SPARK,
                        hit.getLocation().x,hit.getLocation().y,hit.getLocation().z,12,.3,.3,.3,.03);
                server.playSound(null,blockPosition(),kind()==FIRE?net.minecraft.sounds.SoundEvents.FIRECHARGE_USE:kind()==ICE?net.minecraft.sounds.SoundEvents.GLASS_BREAK:kind()==WATER?net.minecraft.sounds.SoundEvents.GENERIC_SPLASH:net.minecraft.sounds.SoundEvents.TRIDENT_HIT,
                        net.minecraft.sounds.SoundSource.PLAYERS,.6F,1.2F);
            }
        }
        discard();
    }
    void affect(Player owner,LivingEntity target){
        if(!eligible(owner,target))return;
        float damage=kind()==WATER?2:kind()==LIGHTNING?5:6;
        if(!target.hurt(level().damageSources().indirectMagic(this,owner),damage))return;
        if(kind()==FIRE)target.setSecondsOnFire(4);
        if(kind()==ICE)target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN,80,1));
        if(kind()==WATER){
            Vec3 direction=getDeltaMovement().normalize();target.push(direction.x*.35,.08,direction.z*.35);
            target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN,30,0));
        }
    }
    @Override public boolean shouldBeSaved(){return false;}
    @Override public void addAdditionalSaveData(CompoundTag tag){super.addAdditionalSaveData(tag);tag.putInt("OlympianKind",kind());tag.putInt("SpellAge",age);}
    @Override public void readAdditionalSaveData(CompoundTag tag){super.readAdditionalSaveData(tag);kind(tag.getInt("OlympianKind"));age=Math.max(0,tag.getInt("SpellAge"));}
    @Override public Packet<ClientGamePacketListener> getAddEntityPacket(){return NetworkHooks.getEntitySpawningPacket(this);}
}
