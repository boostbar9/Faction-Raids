package com.devfarinsky.siegeoverhaul.compat;

import com.devfarinsky.siegeoverhaul.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.*;
import net.minecraftforge.fml.common.Mod;
import java.util.*;

/** Bounded corpse housekeeping. Nonempty player/unknown corpses are never expired by this addon. */
@Mod.EventBusSubscriber(modid=SiegeOverhaul.MOD_ID)
public final class SiegeCorpseCleanup {
    private record Death(net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimension,Vec3 pos,String name,long tick) {}
    private record Ref(net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimension,UUID id) {}
    private static final ArrayDeque<Death> DEATHS=new ArrayDeque<>();
    private static final ArrayDeque<Ref> CORPSES=new ArrayDeque<>();
    private static final Set<UUID> TRACKED=new HashSet<>();
    private static int ticks;
    private SiegeCorpseCleanup() {}
    @SubscribeEvent(priority=EventPriority.LOWEST)
    public static void death(LivingDeathEvent event){
        if(event.isCanceled() || !(event.getEntity() instanceof Mob mob) || !(mob.level() instanceof ServerLevel level))return;
        if(mob.getPersistentData().getString(ModConstants.Tags.RAID_TEAM).isBlank()
                && mob.getPersistentData().getString(com.devfarinsky.siegeoverhaul.camp.CampGuards.TEAM_TAG).isBlank())return;
        if(!RecruitsBridge.isRecruitSoldier(mob))return;
        DEATHS.addLast(new Death(level.dimension(),mob.position(),mob.getName().getString(),level.getGameTime()));
        while(DEATHS.size()>128)DEATHS.removeFirst();
    }
    static boolean recruitCorpse(Object corpse) throws ReflectiveOperationException {
        Object id=corpse.getClass().getMethod("getCorpseUUID").invoke(corpse);
        if(!(id instanceof Optional<?> optional) || !(optional.orElse(null) instanceof UUID uuid))return false;
        return Boolean.TRUE.equals(Class.forName("com.talhanation.recruits.compat.corpse.RecruitCorpseAppearance")
                .getMethod("isRecruitCorpse",UUID.class).invoke(null,uuid));
    }
    @SubscribeEvent public static void join(EntityJoinLevelEvent event){
        if(event.isCanceled() || !(event.getLevel() instanceof ServerLevel level) || !CorpseCompatibility.isCorpse(event.getEntity()))return;
        Entity corpse=event.getEntity();
        if(TRACKED.add(corpse.getUUID()))CORPSES.addLast(new Ref(level.dimension(),corpse.getUUID()));
        if(event.loadedFromDisk())return;
        try {
            if(!recruitCorpse(corpse))return;
            String name=(String)corpse.getClass().getMethod("getCorpseName").invoke(corpse);
            var iterator=DEATHS.iterator();
            while(iterator.hasNext()){
                Death death=iterator.next();long age=level.getGameTime()-death.tick;
                if(age>2){iterator.remove();continue;}
                if(age>=0 && death.dimension.equals(level.dimension()) && death.name.equals(name) && death.pos.distanceToSqr(corpse.position())<.01){
                    corpse.getPersistentData().putLong("SiegeCorpseBorn",level.getGameTime());
                    corpse.getPersistentData().putBoolean("SiegeEnemyCorpse",true);iterator.remove();break;
                }
            }
        }catch(ReflectiveOperationException|RuntimeException|LinkageError ignored){ /* Unknown APIs retain their bodies. */ }
    }
    @SubscribeEvent public static void tick(TickEvent.ServerTickEvent event){
        if(event.phase!=TickEvent.Phase.END || ++ticks<20)return;ticks=0;service(event.getServer());
    }
    private static void service(MinecraftServer server){
        int budget=Math.min(32,CORPSES.size());
        for(int i=0;i<budget;i++){
            Ref ref=CORPSES.removeFirst();ServerLevel level=server.getLevel(ref.dimension);Entity corpse=level==null?null:level.getEntity(ref.id);
            if(corpse==null || !CorpseCompatibility.isCorpse(corpse)){TRACKED.remove(ref.id);continue;}
            try {
                long now=level.getGameTime();CompoundTag tag=corpse.getPersistentData();
                boolean empty=Boolean.TRUE.equals(corpse.getClass().getMethod("isEmpty").invoke(corpse));
                if(empty){
                    if(!tag.contains("SiegeEmptySince"))tag.putLong("SiegeEmptySince",now);
                    int delay=RaidConfig.EMPTY_CORPSE_SECONDS.get();
                    if(delay>0 && now-tag.getLong("SiegeEmptySince")>=delay*20L)corpse.discard();
                }else{
                    tag.remove("SiegeEmptySince");int delay=RaidConfig.SIEGE_CORPSE_SECONDS.get();
                    if(delay>0 && tag.getBoolean("SiegeEnemyCorpse") && now-tag.getLong("SiegeCorpseBorn")>=delay*20L
                            && recruitCorpse(corpse))spill(level,corpse);
                }
            }catch(ReflectiveOperationException|RuntimeException|LinkageError ignored){ /* No destructive fallback. */ }
            if(corpse.isRemoved())TRACKED.remove(ref.id);else CORPSES.addLast(ref);
        }
    }
    static List<ItemStack> inventory(Object corpse) throws ReflectiveOperationException {
        Object death=corpse.getClass().getMethod("getDeath").invoke(corpse);List<ItemStack> items=new ArrayList<>();
        for(String getter:new String[]{"getMainInventory","getArmorInventory","getOffHandInventory","getAdditionalItems"}){
            Object value=death.getClass().getMethod(getter).invoke(death);
            if(!(value instanceof List<?> list))throw new IllegalStateException("Unknown corpse inventory");
            for(Object entry:list){if(!(entry instanceof ItemStack stack))throw new IllegalStateException("Unknown corpse item");if(!stack.isEmpty())items.add(stack);}
        }
        if(items.size()>128)throw new IllegalStateException("Corpse inventory exceeds safe conversion budget");
        return items;
    }
    static boolean spill(ServerLevel level,Entity corpse) throws ReflectiveOperationException {
        List<ItemStack> originals=inventory(corpse);List<ItemEntity> spawned=new ArrayList<>();
        try{
            for(ItemStack original:originals){
                ItemEntity item=new ItemEntity(level,corpse.getX(),corpse.getY()+.2,corpse.getZ(),original.copy());
                item.setDefaultPickUpDelay();
                if(!level.addFreshEntity(item)){spawned.forEach(Entity::discard);return false;}
                spawned.add(item);
            }
        }catch(RuntimeException ex){spawned.forEach(Entity::discard);throw ex;}
        originals.forEach(stack->stack.setCount(0));corpse.discard();return true;
    }
    @SubscribeEvent public static void stopped(ServerStoppedEvent event){DEATHS.clear();CORPSES.clear();TRACKED.clear();ticks=0;}
}
