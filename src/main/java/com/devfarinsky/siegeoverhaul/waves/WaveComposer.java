package com.devfarinsky.siegeoverhaul.waves;

import com.devfarinsky.siegeoverhaul.RaidConfig;
import com.devfarinsky.siegeoverhaul.formations.Formation;
import com.devfarinsky.siegeoverhaul.narrative.OlympianHostIdentity;
import java.util.*;

/** Progressive combat roster. Every slot is budgeted, including tiny/custom waves. */
public final class WaveComposer {
    private WaveComposer() {}
    public static final List<String> COMBAT_TYPES=List.of("recruit","recruit_shieldman","bowman","crossbowman","nomad","horseman","scout","captain","siege_engineer","assassin","assassin_leader","patrol_leader");
    public static boolean reserved(int wave,int totalWaves,int index,boolean commander,boolean illusioners) {
        return wave>=totalWaves && (index==1 || commander && index==0) || wave>=4 && illusioners && index==2;
    }
    public static int compositionIndex(int wave,int totalWaves,int index,boolean commander,boolean illusioners) {
        if(index<0 || reserved(wave,totalWaves,index,commander,illusioners))return -1;
        int skipped=0; for(int i=0;i<Math.min(index,3);i++)if(reserved(wave,totalWaves,i,commander,illusioners))skipped++;
        return index-skipped;
    }
    public static WaveComposition compose(int wave,int totalWaves,int total) {
        return compose("", wave, totalWaves, total);
    }
    public static WaveComposition compose(String factionId,int wave,int totalWaves,int total) {
        if(!RaidConfig.ENABLE_WAVE_COMPOSITION.get() || total<=0)return new WaveComposition(total,Map.of(),Formation.NONE,"");
        int available=total;
        for(int i=0;i<Math.min(total,3);i++)if(reserved(wave,totalWaves,i,RaidConfig.ENABLE_COMMANDER.get(),RaidConfig.ENABLE_ILLUSIONERS.get()))available--;
        OlympianHostIdentity host=OlympianHostIdentity.forFaction(factionId);
        List<String> priority=host.rolesForWave(wave,totalWaves);
        List<String> slots=new ArrayList<>(available);
        Map<String,Integer> mix=new LinkedHashMap<>();
        for(int i=0;i<available;i++) {
            String role=priority.get(i%priority.size());
            slots.add(role);
            mix.merge(role,1,Integer::sum);
        }
        return new WaveComposition(total,slots,mix,host.formation(),host.labelForWave(wave,totalWaves));
    }
}
