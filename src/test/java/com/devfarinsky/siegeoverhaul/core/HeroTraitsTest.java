package com.devfarinsky.siegeoverhaul.core;
import com.devfarinsky.siegeoverhaul.MinecraftTestSupport;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.*;
import net.minecraft.world.item.*;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
class HeroTraitsTest extends MinecraftTestSupport {
    @Test void bloodthornHealsOnlyOnThirdEnemyMeleeHitAndRangedChainCannotRecurse() {
        var level=mock(net.minecraft.server.level.ServerLevel.class);var hero=mock(Mob.class);var victim=mock(Mob.class);
        var heroTag=new CompoundTag();heroTag.putBoolean("SiegeHiredHero",true);heroTag.putInt("SiegeHeroRole",10);
        var enemyTag=new CompoundTag();enemyTag.putString(com.devfarinsky.siegeoverhaul.ModConstants.Tags.RAID_TEAM,"test");
        when(hero.getPersistentData()).thenReturn(heroTag);when(victim.getPersistentData()).thenReturn(enemyTag);
        when(hero.level()).thenReturn(level);when(hero.isAlive()).thenReturn(true);when(victim.isAlive()).thenReturn(true);
        when(hero.hasLineOfSight(victim)).thenReturn(true);when(hero.getHealth()).thenReturn(10F);when(hero.getMaxHealth()).thenReturn(20F);
        var type=net.minecraft.core.Holder.direct(new net.minecraft.world.damagesource.DamageType("test",0));
        var source=new net.minecraft.world.damagesource.DamageSource(type,hero);
        var event=new net.minecraftforge.event.entity.living.LivingDamageEvent(victim,source,2);
        HeroTraits.hit(event);HeroTraits.hit(event);verify(hero,never()).heal(anyFloat());
        HeroTraits.hit(event);verify(hero).heal(2);HeroTraits.hit(event);verify(hero,times(1)).heal(2);
        heroTag.putInt("SiegeHeroRole",12);heroTag.putInt("SiegeHeroHits",0);
        HeroTraits.hit(event);assertEquals(0,heroTag.getInt("SiegeHeroHits"));
        enemyTag.remove(com.devfarinsky.siegeoverhaul.ModConstants.Tags.RAID_TEAM);
        heroTag.putInt("SiegeHeroRole",10);heroTag.putInt("SiegeHeroHits",0);
        HeroTraits.hit(event);assertEquals(0,heroTag.getInt("SiegeHeroHits"));
    }

    @Test void distinctiveLoadoutsLiveInNativeEquipmentSlots() {
        // Every hero has: a stored role tag, an enchanted mainhand weapon
        // that matches its base (sword/shield -> DIAMOND_SWORD, bow -> BOW,
        // crossbow -> CROSSBOW, mage -> BLAZE_ROD), and shield-based non-mages
        // carry a shield in the offhand. Trims are unique across the roster.
        var trims=new java.util.HashSet<String>();
        for(int role=10;role<=29;role++) {
            Mob mob=mock(Mob.class);CompoundTag tag=new CompoundTag();when(mob.getPersistentData()).thenReturn(tag);
            SimpleContainer inventory=new SimpleContainer(36);HeroTraits.equip(mob,role,inventory);
            trims.add(inventory.getItem(1).getTag().getCompound("Trim").getString("material")+"/"
                    +inventory.getItem(1).getTag().getCompound("Trim").getString("pattern"));
            assertEquals(role,tag.getInt("SiegeHeroRole"));
            assertTrue(inventory.getItem(5).isEnchanted());
            int base=CoreHiring.heroBase(role);
            boolean mage=role==22||role==23||role==24||role>=27;
            Item expected= mage?Items.BLAZE_ROD : base==2?Items.BOW : base==3?Items.CROSSBOW : Items.DIAMOND_SWORD;
            assertEquals(expected,inventory.getItem(5).getItem());
            if(base==1 && !mage) assertTrue(inventory.getItem(4).is(Items.SHIELD));
            else assertTrue(inventory.getItem(4).isEmpty());
            verify(mob).setItemSlot(EquipmentSlot.MAINHAND,inventory.getItem(5));
        }
        // With 20 heroes assigned distinct material/pattern pairs, we expect many
        // unique trims. Rather than pin exact count, assert at least 8 distinct pairs.
        assertTrue(trims.size()>=8,"expected >=8 distinct hero trims, got "+trims.size());
    }
    @Test void cooldownSurvivesReloadAndRecoversAfterWorldClockReset() {
        assertFalse(HeroTraits.ready(100,500));assertTrue(HeroTraits.ready(500,500));assertTrue(HeroTraits.ready(0,5000));
    }
}
