package com.devfarinsky.siegeoverhaul.nativecompat;

import com.devfarinsky.siegeoverhaul.core.PerimeterEarthworksManifest;
import com.talhanation.workers.entities.AbstractWorkerEntity;
import com.talhanation.workers.entities.BuilderEntity;
import com.talhanation.workers.entities.workarea.BuildArea;
import com.talhanation.workers.world.BuildBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkHooks;

import java.util.HashMap;
import java.util.Map;

/** Separate sealed marker for exact local grading. Packet setters can never start, move or widen its work. */
public final class EarthworksBuildArea extends BuildArea {
    private boolean trusted, sealed, retiring;
    private BlockPos origin;
    private AABB envelope;
    private CompoundTag identity;
    private Map<BlockPos, BlockState> targets;

    public EarthworksBuildArea(EntityType<?> type, Level level) { super(type, level); }
    void initialize(EarthworksJobLedger.Job job, BlockPos marker) {
        if (sealed || level().isClientSide || !getUUID().equals(job.area)) throw new IllegalStateException("Foreign earthworks marker");
        CompoundTag selector = NativeEarthworksJobs.selector(job);
        if (identity != null && !identity.equals(selector)) throw new IllegalStateException("Saved earthworks marker identity changed");
        int minX=Integer.MAX_VALUE,minY=Integer.MAX_VALUE,minZ=Integer.MAX_VALUE,maxX=Integer.MIN_VALUE,maxY=Integer.MIN_VALUE,maxZ=Integer.MIN_VALUE;
        Map<BlockPos,BlockState> exact = new HashMap<>();
        for (var step : job.manifest.steps()) {
            BlockPos pos=BlockPos.of(step.pos()); minX=Math.min(minX,pos.getX()); minY=Math.min(minY,pos.getY()); minZ=Math.min(minZ,pos.getZ());
            maxX=Math.max(maxX,pos.getX()); maxY=Math.max(maxY,pos.getY()); maxZ=Math.max(maxZ,pos.getZ());
            if(step.kind()!=PerimeterEarthworksManifest.Kind.CUT)exact.put(pos,step.after());
        }
        long width=(long)maxX-minX+1, height=(long)maxY-minY+1, depth=(long)maxZ-minZ+1;
        if(width<1||width>32||height<1||height>32||depth<1||depth>32||width*height*depth>32_768)
            throw new IllegalArgumentException("Local grading exceeds the reviewed native envelope");
        origin=new BlockPos(maxX,minY,minZ); envelope=new AABB(minX,minY,minZ,maxX+1D,maxY+1D,maxZ+1D);
        if(!ConstructionTracking.covers(Vec3.atBottomCenterOf(marker),envelope))throw new IllegalArgumentException("Marker cannot track all exact cells");
        trusted=true;
        try {
            setPlayerUUID(job.manifest.header().owner()); setPlayerName("Reviewed local grading"); setTeamStringID(""); setTeamAccess(false);
            setFacing(Direction.SOUTH);setWidthSize((int)width);setDepthSize((int)depth);setHeightSize((int)height);
            setPos(marker.getX()+.5,marker.getY(),marker.getZ()+.5); setFreeArea(false); super.setDone(false);
            targets=Map.copyOf(exact); identity=selector.copy();
            stackToPlace.clear(); stackToPlaceMultiBlock.clear(); stackToBreak.clear(); stackToFree.clear();
            for(int i=job.read().journal().nextStep();i<job.manifest.steps().size();i++) {
                var step=job.manifest.steps().get(i);
                if(step.kind()!=PerimeterEarthworksManifest.Kind.CUT)stackToPlace.push(new BuildBlock(BlockPos.of(step.pos()),step.after()));
            }
            sealed=true;
        } finally {trusted=false;}
    }
    boolean matches(EarthworksJobLedger.Job job) { return sealed && identity!=null && identity.equals(NativeEarthworksJobs.selector(job)); }
    @Override public boolean canWorkHere(AbstractWorkerEntity worker) {
        if(!sealed || !(worker instanceof BuilderEntity builder))return false;
        var job=NativeEarthworksJobs.authenticated(builder,true);return job!=null && matches(job) && super.canWorkHere(worker);
    }
    @Override public BlockState getStateFromPos(BlockPos pos) {return targets==null?null:targets.get(pos);}
    @Override public BlockPos getOriginPos(){return origin==null?super.getOriginPos():origin;}
    @Override public AABB createArea(){return envelope==null?super.createArea():envelope;}
    @Override public AABB getArea(){return createArea();}
    @Override public void scanBreakArea(){/* No ambient clearing is ever delegated. */}
    @Override public void scanFreeArea(){/* No ambient clearing is ever delegated. */}
    @Override public void setStartBuild(boolean creative){/* Packets never activate this marker. */}
    @Override public void setStructureNBT(CompoundTag tag){if(trusted)super.setStructureNBT(tag.copy());}
    @Override public CompoundTag getStructureNBT(){return new CompoundTag();}
    @Override public void setWidthSize(int value){if(trusted)super.setWidthSize(value);}
    @Override public void setDepthSize(int value){if(trusted)super.setDepthSize(value);}
    @Override public void setHeightSize(int value){if(trusted)super.setHeightSize(value);}
    @Override public void setFacing(Direction value){if(trusted)super.setFacing(value);}
    @Override public void setPlayerUUID(java.util.UUID value){if(trusted)super.setPlayerUUID(value);}
    @Override public void setPlayerName(String value){if(trusted)super.setPlayerName(value);}
    @Override public void setTeamStringID(String value){if(trusted)super.setTeamStringID(value);}
    @Override public void setTeamAccess(boolean value){if(trusted)super.setTeamAccess(value);}
    @Override public void setFreeArea(boolean value){super.setFreeArea(false);}
    @Override public void setAlwaysShowProjection(boolean value){if(trusted)super.setAlwaysShowProjection(value);}
    @Override public void setDone(boolean value){if(trusted)super.setDone(value);}
    @Override public void setTime(int value){if(trusted||value==0)super.setTime(value);}
    @Override public void setPos(double x,double y,double z){if(trusted||!isAddedToWorld()||level().isClientSide)super.setPos(x,y,z);}
    private boolean movable(){return trusted||!isAddedToWorld();}
    @Override public void moveTo(double x,double y,double z){if(movable())super.moveTo(x,y,z);}
    @Override public void moveTo(double x,double y,double z,float yaw,float pitch){if(movable())super.moveTo(x,y,z,yaw,pitch);}
    @Override public void moveTo(Vec3 pos){if(movable())super.moveTo(pos);}
    @Override public void moveTo(BlockPos pos,float yaw,float pitch){if(movable())super.moveTo(pos,yaw,pitch);}
    @Override public boolean hurt(DamageSource source,float amount){return false;}
    @Override public void remove(RemovalReason reason){if(reason!=RemovalReason.DISCARDED||retiring)super.remove(reason);}
    void retire(){retiring=true;try{super.remove(RemovalReason.DISCARDED);}finally{retiring=false;}}
    @Override @net.minecraftforge.api.distmarker.OnlyIn(net.minecraftforge.api.distmarker.Dist.CLIENT)
    public net.minecraft.client.gui.screens.Screen getScreen(Player player){return null;}
    @Override public void addAdditionalSaveData(CompoundTag tag){
        // Only the world-ledger-bound identity is authoritative; never persist mutable queues or a native editable recipe.
        if(identity!=null)tag.put("EarthworksIdentity",identity.copy());
    }
    @Override public void readAdditionalSaveData(CompoundTag tag){
        sealed=false;targets=null;origin=null;envelope=null;
        identity=tag.contains("EarthworksIdentity",net.minecraft.nbt.Tag.TAG_COMPOUND)?tag.getCompound("EarthworksIdentity").copy():new CompoundTag();
        stackToPlace.clear();stackToPlaceMultiBlock.clear();stackToBreak.clear();stackToFree.clear();
    }
    @Override public Packet<ClientGamePacketListener> getAddEntityPacket(){return NetworkHooks.getEntitySpawningPacket(this);}
}
