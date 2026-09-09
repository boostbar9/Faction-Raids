package com.devfarinsky.siegeoverhaul.formations;

import net.minecraft.world.entity.Mob;
import net.minecraftforge.registries.ForgeRegistries;
import java.util.*;

/** Small role squads retain membership across chunk boundaries; distant stragglers regroup. */
public final class MarchSquads {
    private MarchSquads() {}
    public static Map<String,List<Mob>> group(List<Mob> input) {
        List<Mob> units=new ArrayList<>(input);units.sort(Comparator.comparing(Mob::getUUID));
        Map<String,List<Mob>> groups=new LinkedHashMap<>();List<Mob> unassigned=new ArrayList<>();
        for(Mob mob:units) {
            String key=mob.getPersistentData().getString("SiegeMarchSquad");
            if(!key.startsWith(role(mob)+":")){unassigned.add(mob);continue;}
            var group=groups.computeIfAbsent(key,k->new ArrayList<>());
            if(group.size()<6 && (group.isEmpty() || mob.position().distanceToSqr(group.get(0).position())<=24*24))group.add(mob);
            else unassigned.add(mob);
        }
        // A saved one-person squad cannot form up. Let its survivor join nearby
        // role peers again, while preserving every intact squad and its slots.
        var iterator=groups.entrySet().iterator();
        while(iterator.hasNext()) {
            var entry=iterator.next();
            if(entry.getValue().size()<2) {
                unassigned.addAll(entry.getValue());iterator.remove();
            }
        }
        unassigned.sort(Comparator.comparing(Mob::getUUID));
        for(Mob mob:unassigned) {
            String prefix=role(mob)+":";String chosen=null;double best=12*12;
            for(var entry:groups.entrySet()) {
                var members=entry.getValue();if(!entry.getKey().startsWith(prefix)||members.isEmpty()||members.size()>=6)continue;
                double distance=mob.position().distanceToSqr(members.get(0).position());
                if(distance<=best){best=distance;chosen=entry.getKey();}
            }
            if(chosen==null)chosen=prefix+mob.getUUID();
            groups.computeIfAbsent(chosen,k->new ArrayList<>()).add(mob);
            mob.getPersistentData().putString("SiegeMarchSquad",chosen);
        }
        groups.values().forEach(g->g.sort(Comparator.comparing(Mob::getUUID)));
        return groups;
    }
    private static String role(Mob mob) {
        var id=ForgeRegistries.ENTITY_TYPES.getKey(mob.getType());return FormationTactics.group(id==null?"":id.getPath());
    }
}
