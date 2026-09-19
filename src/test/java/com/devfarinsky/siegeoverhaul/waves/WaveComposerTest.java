package com.devfarinsky.siegeoverhaul.waves;
import com.devfarinsky.siegeoverhaul.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class WaveComposerTest extends MinecraftTestSupport {
    @Test void everyOlympianHostUsesItsOwnDoctrineAndOpeningRoster() {
        RaidConfig.ENABLE_WAVE_COMPOSITION.set(true);
        RaidConfig.ENABLE_COMMANDER.set(false);
        RaidConfig.ENABLE_ILLUSIONERS.set(false);
        for(var host:com.devfarinsky.siegeoverhaul.narrative.OlympianHostIdentity.hosts()) {
            var opening=WaveComposer.compose(host.factionId(),1,5,12);
            var assault=WaveComposer.compose(host.factionId(),4,5,20);
            assertEquals(host.formation(),opening.formation);
            assertEquals(host.waveLabel(),opening.label);
            assertEquals(expectedMix(host.openingRoles(),12),opening.roleCounts);
            assertEquals(expectedMix(host.assaultRoles(),20),assault.roleCounts);
            assertEquals(12,opening.roleCounts.values().stream().mapToInt(Integer::intValue).sum());
            assertEquals(20,assault.roleCounts.values().stream().mapToInt(Integer::intValue).sum());
        }
    }
    private static Map<String,Integer> expectedMix(List<String> priority,int total) {
        Map<String,Integer> expected=new LinkedHashMap<>();
        for(int i=0;i<total;i++)expected.merge(priority.get(i%priority.size()),1,Integer::sum);
        return expected;
    }
    @Test void reservedSlotsNeverSkipOrDuplicateTheComposition() {
        for(boolean commander:new boolean[]{false,true})for(boolean illusions:new boolean[]{false,true})for(int wave=1;wave<=5;wave++) {
            int cursor=0;
            for(int i=0;i<30;i++) {
                int index=WaveComposer.compositionIndex(wave,5,i,commander,illusions);
                if(WaveComposer.reserved(wave,5,i,commander,illusions))assertEquals(-1,index);
                else assertEquals(cursor++,index);
            }
        }
    }
    @Test void everyCombatTypeHasAPlaceAcrossAFullSiege() {
        RaidConfig.ENABLE_WAVE_COMPOSITION.set(true); RaidConfig.ENABLE_COMMANDER.set(true);
        Set<String> types=new HashSet<>();
        for(int wave=1;wave<=5;wave++)types.addAll(WaveComposer.compose(wave,5,20).roleCounts.keySet());
        assertFalse(types.contains("siege_engineer")); // Engineers are reserved for supplied engines, not infantry slots.
        types.add("siege_engineer"); // SiegeDeployment provides the dedicated per-wave support slot.
        types.add("patrol_leader"); // Explicit reserved final commander, outside the ordinary composition.
        assertEquals(new HashSet<>(WaveComposer.COMBAT_TYPES),types);
        assertFalse(types.contains("messenger"));assertFalse(types.contains("villager_noble"));
    }
    @Test void tinyAndCustomWavesAlwaysFitTheirBudget() {
        RaidConfig.ENABLE_WAVE_COMPOSITION.set(true);
        for(boolean commander:new boolean[]{false,true})for(boolean illusions:new boolean[]{false,true}) {
            RaidConfig.ENABLE_COMMANDER.set(commander);RaidConfig.ENABLE_ILLUSIONERS.set(illusions);
            for(int waves=1;waves<=5;waves++)for(int wave=1;wave<=waves;wave++)for(int total=0;total<=30;total++) {
                var plan=WaveComposer.compose(wave,waves,total);
                int reserved=0;for(int i=0;i<total;i++)if(WaveComposer.reserved(wave,waves,i,commander,illusions))reserved++;
                assertEquals(total-reserved,plan.roleCounts.values().stream().mapToInt(Integer::intValue).sum());
                assertNull(plan.roleAt(-1)); assertNull(plan.roleAt(total));
            }
        }
    }
}
