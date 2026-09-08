package com.devfarinsky.siegeoverhaul.compat;
import com.devfarinsky.siegeoverhaul.*;
import com.devfarinsky.siegeoverhaul.camp.CampGuards;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.scores.PlayerTeam;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
class EnemyHiringProtectionTest extends MinecraftTestSupport {
    @Test void raidCrewAndGuardsAreProtectedButOrdinaryUnitsAndHeroesAreNot() {
        Mob mob=mock(Mob.class);CompoundTag tag=new CompoundTag();when(mob.getPersistentData()).thenReturn(tag);
        assertFalse(EnemyHiringProtection.enemy(mob));tag.putBoolean("SiegeHiredHero",true);
        assertFalse(EnemyHiringProtection.enemy(mob));
        for(String key:new String[]{ModConstants.Tags.RAID_TEAM,ModConstants.Tags.CAMP_WORKER_TEAM,CampGuards.TEAM_TAG}) {
            tag.putString(key,"team:defenders");assertTrue(EnemyHiringProtection.enemy(mob));tag.remove(key);
        }
    }
    @Test void factionMembershipProtectsLegacyEnemiesWithoutRaidTags() {
        Mob mob=mock(Mob.class);when(mob.getPersistentData()).thenReturn(new CompoundTag());
        PlayerTeam team=mock(PlayerTeam.class);when(mob.getTeam()).thenReturn(team);
        for(var faction:com.devfarinsky.siegeoverhaul.items.FactionBanners.FactionId.values()) {
            when(team.getName()).thenReturn("siegeoverhaul_"+faction.id);assertTrue(EnemyHiringProtection.enemy(mob));
        }
        when(team.getName()).thenReturn("player_faction");assertFalse(EnemyHiringProtection.enemy(mob));
    }
}
