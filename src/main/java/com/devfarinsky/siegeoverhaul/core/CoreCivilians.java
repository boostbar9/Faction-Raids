package com.devfarinsky.siegeoverhaul.core;

import com.devfarinsky.siegeoverhaul.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraftforge.event.entity.living.*;
import net.minecraftforge.event.entity.EntityTravelToDimensionEvent;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Owned vanilla villagers retain their native brain, trades, beds and breeding. */
@Mod.EventBusSubscriber(modid=SiegeOverhaul.MOD_ID)
public final class CoreCivilians {
    public static final int PRICE=16;
    private static final String OWNER="SiegeCivilianFaction", SAFE="SiegeCivilianSafe", NAME="SiegeCivilianName";
    private static final VillagerProfession[] JOBS={VillagerProfession.FARMER,VillagerProfession.FISHERMAN,
        VillagerProfession.FLETCHER,VillagerProfession.LIBRARIAN,VillagerProfession.CLERIC,
        VillagerProfession.ARMORER,VillagerProfession.TOOLSMITH,VillagerProfession.WEAPONSMITH,
        VillagerProfession.BUTCHER,VillagerProfession.LEATHERWORKER,VillagerProfession.MASON,
        VillagerProfession.SHEPHERD,VillagerProfession.CARTOGRAPHER};
    private CoreCivilians() {}
    public static CompoundTag ledger(RaidSavedData data,String key) {
        return data.civilianFactions.computeIfAbsent(key,k->new CompoundTag());
    }
    public static void coreRemoved(ServerLevel level,BlockPos pos) {
        if(!level.dimension().equals(net.minecraft.world.level.Level.OVERWORLD))return;
        var data=RaidSavedData.get(level.getServer());
        for(var core:data.siegeCores.values())if(core.contains("Position") && core.getLong("Position")==pos.asLong())core.putBoolean("CoreRemoved",true);
        data.setDirty();
    }
    public static void onCoreTick(ServerLevel level,BlockPos pos) {
        var data=RaidSavedData.get(level.getServer());
        for(var core:data.siegeCores.values()) if(core.contains("Position") && core.getLong("Position")==pos.asLong() && core.hasUUID("CivilianPendingOwner")) {
            var player=level.getServer().getPlayerList().getPlayer(core.getUUID("CivilianPendingOwner"));
            if(player!=null)tryStarters(player,pos);
        }
    }
    public static void tryStarters(ServerPlayer player,BlockPos core) {
        if(!SiegeCore.canUse(player,core))return;
        var data=RaidSavedData.get(player.server);var ledger=ledger(data,SiegeCore.key(player));
        while(CivilianLedger.grantStarter(ledger,()->spawn(player,core,false)))data.setDirty();
        data.setDirty();
    }
    public static boolean recruit(ServerPlayer player,BlockPos core) {
        return SiegeCore.canUse(player,core) && spawn(player,core,true);
    }
    private static boolean spawn(ServerPlayer player,BlockPos core,boolean paid) {
        ServerLevel level=player.serverLevel();String key=SiegeCore.key(player);
        var data=RaidSavedData.get(player.server);var ledger=ledger(data,key);
        if(CivilianLedger.count(ledger)>=CivilianLedger.LIMIT) {
            player.sendSystemMessage(Component.literal("Your faction has reached its 64-civilian limit."));return false;
        }
        if(paid && PaymentSource.available(player,PRICE)<PRICE && !player.isCreative())return false;
        Villager villager=EntityType.VILLAGER.create(level);if(villager==null)return false;
        BlockPos site=null;
        for(int r=2;r<=5 && site==null;r++)for(int x=-r;x<=r && site==null;x++)for(int z=-r;z<=r && site==null;z++) {
            if(Math.abs(x)!=r && Math.abs(z)!=r)continue;
            for(int y=-2;y<=2;y++) {
                BlockPos candidate=core.offset(x,y,z);
                if(HirePlacement.safe(level,villager,candidate,p->SiegeCore.claimed(level,p,key))) {site=candidate;break;}
            }
        }
        if(site==null) {villager.discard();player.sendSystemMessage(Component.literal("Clear safe ground inside your claim beside the core for civilians."));return false;}
        villager.moveTo(site.getX()+.5,site.getY(),site.getZ()+.5,0,0);
        villager.finalizeSpawn(level,level.getCurrentDifficultyAt(site),MobSpawnType.EVENT,null,null);
        initialize(villager,key,site);
        if(!CivilianLedger.register(ledger,villager.getUUID(),level.getGameTime())) {villager.discard();return false;}
        if(!level.addFreshEntity(villager)) {CivilianLedger.remove(ledger,villager.getUUID());villager.discard();return false;}
        if(paid && !PaymentSource.consume(player,PRICE)) {villager.discard();CivilianLedger.remove(ledger,villager.getUUID());return false;}
        var team=level.getScoreboard().getPlayerTeam(key.substring(5));
        if(team!=null)level.getScoreboard().addPlayerToTeam(villager.getStringUUID(),team);
        data.setDirty();return true;
    }
    private static void initialize(Villager villager,String key,BlockPos safe) {
        var tag=villager.getPersistentData();tag.putString(OWNER,key);tag.putLong(SAFE,safe.asLong());
        tag.putString(NAME,RecruitPersonality.name(villager.getRandom()));
        villager.setVillagerData(villager.getVillagerData().setProfession(JOBS[villager.getRandom().nextInt(JOBS.length)]));
        villager.setVillagerXp(1); // keep assigned profession; restocking still requires its workstation
        villager.setPersistenceRequired();name(villager);
    }
    private static void name(Villager v) {
        v.setCustomName(Component.literal(v.getPersistentData().getString(NAME)+" · ")
            .append(Component.translatable("entity.minecraft.villager."+v.getVillagerData().getProfession().name())));
        v.setCustomNameVisible(true);
    }
    @SubscribeEvent public static void born(BabyEntitySpawnEvent event) {
        String a=event.getParentA().getPersistentData().getString(OWNER);
        String b=event.getParentB().getPersistentData().getString(OWNER);
        if(a.isEmpty() && b.isEmpty())return;
        if(!a.equals(b) || !(event.getChild() instanceof Villager baby)
                || !(event.getParentA().level() instanceof ServerLevel level)) {event.setCanceled(true);return;}
        var data=RaidSavedData.get(level.getServer());var ledger=ledger(data,a);
        if(!SiegeCore.claimed(level,event.getParentA().blockPosition(),a)
                || CivilianLedger.count(ledger)>=CivilianLedger.LIMIT) {event.setCanceled(true);return;}
        initialize(baby,a,event.getParentA().blockPosition());
        // Registration happens when the child actually joins the world; another mod can cancel birth.

    }
    @SubscribeEvent(priority=net.minecraftforge.eventbus.api.EventPriority.LOWEST)
    public static void joined(net.minecraftforge.event.entity.EntityJoinLevelEvent event) {
        if(event.isCanceled() || !(event.getEntity() instanceof Villager v) || !(event.getLevel() instanceof ServerLevel level))return;
        String key=v.getPersistentData().getString(OWNER);if(key.isEmpty())return;
        var data=RaidSavedData.get(level.getServer());
        if(!CivilianLedger.register(ledger(data,key),v.getUUID(),level.getGameTime()))event.setCanceled(true);
        if(!event.isCanceled() && key.startsWith("team:")) {
            var team=level.getScoreboard().getPlayerTeam(key.substring(5));
            if(team!=null)level.getScoreboard().addPlayerToTeam(v.getStringUUID(),team);
        }
        data.setDirty();
    }
    @SubscribeEvent public static void tick(LivingEvent.LivingTickEvent event) {
        if(!(event.getEntity() instanceof Villager v) || !(v.level() instanceof ServerLevel level))return;
        String key=v.getPersistentData().getString(OWNER);if(key.isEmpty())return;
        BlockPos here=v.blockPosition();
        if(SiegeCore.claimed(level,here,key)) {
            waiting(v,level,key,false);
            if(v.onGround() && !v.isInWaterOrBubble() && level.noCollision(v))v.getPersistentData().putLong(SAFE,here.asLong());
        } else {
            v.getNavigation().stop();v.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
            BlockPos safe=BlockPos.of(v.getPersistentData().getLong(SAFE));
            boolean usable=SiegeCore.claimed(level,safe,key) && HirePlacement.safe(level,v,safe,p->SiegeCore.claimed(level,p,key));
            if(!usable && v.tickCount%100==0) {
                safe=recovery(level,v,key);usable=safe!=null;
            }
            if(usable) {
                v.stopRiding();v.teleportTo(safe.getX()+.5,safe.getY(),safe.getZ()+.5);v.setDeltaMovement(0,0,0);
                v.getPersistentData().putLong(SAFE,safe.asLong());waiting(v,level,key,false);
            } else {waiting(v,level,key,true);v.setDeltaMovement(0,0,0);}

        }
        if(v.tickCount%100==0) {
            name(v);
            for(var memory:java.util.List.of(MemoryModuleType.HOME,MemoryModuleType.JOB_SITE,MemoryModuleType.POTENTIAL_JOB_SITE,MemoryModuleType.MEETING_POINT))
                v.getBrain().getMemory(memory).ifPresent(pos->{if(!pos.dimension().equals(level.dimension()) || !SiegeCore.claimed(level,pos.pos(),key)){v.releasePoi(memory);v.getBrain().eraseMemory(memory);}});
        }
    }
    static BlockPos recovery(ServerLevel level,Villager v,String key) {
        var core=RaidSavedData.get(level.getServer()).siegeCores.get(key);
        if(core==null || !core.contains("Position"))return null;
        BlockPos origin=BlockPos.of(core.getLong("Position"));
        for(int dx:new int[]{0,2,-2,4,-4})for(int dz:new int[]{0,2,-2,4,-4})for(int dy:new int[]{0,1,-1}) {
            BlockPos site=origin.offset(dx,dy,dz);
            if(SiegeCore.claimed(level,site,key) && HirePlacement.safe(level,v,site,p->SiegeCore.claimed(level,p,key)))return site;
        }
        return null;
    }
    private static void waiting(Villager v,ServerLevel level,String key,boolean waiting) {
        var tag=v.getPersistentData();if(tag.getBoolean("SiegeCivilianWaiting")==waiting)return;
        var data=RaidSavedData.get(level.getServer());
        CivilianLedger.pause(ledger(data,key),v.getUUID(),waiting,level.getGameTime());
        if(waiting){tag.putBoolean("SiegeCivilianWasNoAi",v.isNoAi());v.setNoAi(true);}
        else {v.setNoAi(tag.getBoolean("SiegeCivilianWasNoAi"));tag.remove("SiegeCivilianWasNoAi");}
        tag.putBoolean("SiegeCivilianWaiting",waiting);data.setDirty();
    }
    @SubscribeEvent public static void dimension(EntityTravelToDimensionEvent event) {
        if(event.getEntity() instanceof Villager && !event.getEntity().getPersistentData().getString(OWNER).isEmpty())event.setCanceled(true);
    }
    @SubscribeEvent public static void removed(EntityLeaveLevelEvent event) {
        var v=event.getEntity();var reason=v.getRemovalReason();
        if(!(v instanceof Villager) || !(event.getLevel() instanceof ServerLevel level) || reason==null || !reason.shouldDestroy())return;
        String key=v.getPersistentData().getString(OWNER);if(key.isEmpty())return;
        var data=RaidSavedData.get(level.getServer());CivilianLedger.remove(ledger(data,key),v.getUUID());data.setDirty();
    }
    @SubscribeEvent public static void serverTick(TickEvent.ServerTickEvent event) {
        if(event.phase!=TickEvent.Phase.END)return;
        var server=event.getServer();long now=server.overworld().getGameTime();if(now%1200!=0)return;
        var data=RaidSavedData.get(server);
        for(var entry:data.civilianFactions.entrySet()) {
            var core=data.siegeCores.get(entry.getKey());if(core==null){CivilianLedger.settle(entry.getValue(),new CompoundTag(),now,false);continue;}
            BlockPos pos=BlockPos.of(core.getLong("Position"));
            boolean eligible=core.contains("Position") && !core.getBoolean("CoreRemoved") && !core.getBoolean("Occupied")
                    && SiegeCore.claimed(server.overworld(),pos,entry.getKey())
                    && (!server.overworld().hasChunkAt(pos) || server.overworld().getBlockState(pos).is(CoreBlocks.CORE.get()));
            CivilianLedger.settle(entry.getValue(),core,now,eligible);
        }
        if(!data.civilianFactions.isEmpty())data.setDirty();
    }
}
