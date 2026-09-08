package com.devfarinsky.siegeoverhaul.waves;

import com.devfarinsky.siegeoverhaul.RaidConfig;
import com.devfarinsky.siegeoverhaul.formations.Formation;
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
        if(!RaidConfig.ENABLE_WAVE_COMPOSITION.get() || total<=0)return new WaveComposition(total,Map.of(),Formation.NONE,"");
        int available=total;
        for(int i=0;i<Math.min(total,3);i++)if(reserved(wave,totalWaves,i,RaidConfig.ENABLE_COMMANDER.get(),RaidConfig.ENABLE_ILLUSIONERS.get()))available--;
        List<String> priority=wave<=1?List.of("recruit_shieldman","bowman","recruit","scout"):
                wave==2?List.of("captain","crossbowman","horseman","nomad","recruit_shieldman","recruit","bowman"):
                List.of("recruit_shieldman","assassin","assassin_leader","captain","horseman","nomad","crossbowman","scout","recruit","recruit_shieldman","bowman");
        Map<String,Integer> mix=new LinkedHashMap<>();
        for(int i=0;i<available;i++) {
            String role=i<priority.size()?priority.get(i):List.of("recruit_shieldman","recruit","bowman","crossbowman").get((i-priority.size())%4);
            mix.merge(role,1,Integer::sum);
        }
        return new WaveComposition(total,mix,Formation.LINE,wave<=1?"Infantry and scouts":wave==2?"Mobile support":"Combined arms assault");
    }
}
