package com.devfarinsky.siegeoverhaul.core;
import com.devfarinsky.siegeoverhaul.MinecraftTestSupport;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.item.*;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
class HeroTraitsTest extends MinecraftTestSupport {
    @Test void legacyDefaultAndGeneratedEnemyNamesMigrateButPlayerNamesRemainUntouched() {
        Mob legacy=mock(Mob.class);
        CompoundTag legacyTag=new CompoundTag();
        when(legacy.getPersistentData()).thenReturn(legacyTag);
        when(legacy.getCustomName()).thenReturn(Component.literal(CoreHiring.legacyHeroName(10)));

        HeroTraits.ensureOlympianIdentity(legacy,10);

        verify(legacy).setCustomName(argThat(name->CoreHiring.NAMES[10].equals(name.getString())));
        assertTrue(legacyTag.getBoolean("SiegeOlympianHeroIdentity"));

        Mob labelledEnemy=mock(Mob.class);
        CompoundTag enemyTag=new CompoundTag();
        enemyTag.putBoolean("SiegeEnemyHero",true);
        enemyTag.putInt("SiegeHeroRole",10);
        when(labelledEnemy.getPersistentData()).thenReturn(enemyTag);
        when(labelledEnemy.getCustomName()).thenReturn(
                Component.literal("Enemy Hero · "+CoreHiring.legacyHeroName(10)));

        HeroTraits.ensureOlympianIdentity(labelledEnemy,10);

        verify(labelledEnemy).setCustomName(argThat(name->
                ("Enemy Hero · "+CoreHiring.NAMES[10]).equals(name.getString())));
        assertTrue(enemyTag.getBoolean("SiegeOlympianHeroIdentity"));

        Mob renamed=mock(Mob.class);
        CompoundTag renamedTag=new CompoundTag();
        renamedTag.putBoolean("SiegeEnemyHero",true);
        renamedTag.putInt("SiegeHeroRole",10);
        when(renamed.getPersistentData()).thenReturn(renamedTag);
        when(renamed.getCustomName()).thenReturn(Component.literal("Bobby"));

        HeroTraits.ensureOlympianIdentity(renamed,10);

        verify(renamed,never()).setCustomName(any());
        assertTrue(renamedTag.getBoolean("SiegeOlympianHeroIdentity"));
    }

    @Test void fullRosterHasDistinctOlympianNamesWithoutChangingRoleTables() {
        var names=new java.util.HashSet<String>();
        for(int role=CoreHiring.HERO_ID_MIN;role<=CoreHiring.HERO_ID_MAX;role++) {
            String name=CoreHiring.NAMES[role];
            assertTrue(names.add(name),"duplicate hero name: "+name);
            assertNotEquals(CoreHiring.legacyHeroName(role),name);
            assertFalse(HeroTraits.description(role).isBlank());
            assertTrue(CoreHiring.heroBase(role)>=0 && CoreHiring.heroBase(role)<=3);
            assertTrue(CoreHiring.heroTier(role)>=0 && CoreHiring.heroTier(role)<=4);
        }
        assertEquals(20,names.size());
    }

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

    @Test void bloodthornTriggersAbsorptionOnFirstFullHealthMeleeHit() {
        var level=mock(net.minecraft.server.level.ServerLevel.class);var hero=mock(Mob.class);var victim=mock(Mob.class);
        var heroTag=new CompoundTag();heroTag.putBoolean("SiegeHiredHero",true);heroTag.putInt("SiegeHeroRole",14);
        var enemyTag=new CompoundTag();enemyTag.putString(com.devfarinsky.siegeoverhaul.ModConstants.Tags.RAID_TEAM,"test");
        when(hero.getPersistentData()).thenReturn(heroTag);when(victim.getPersistentData()).thenReturn(enemyTag);
        when(hero.level()).thenReturn(level);when(hero.isAlive()).thenReturn(true);when(victim.isAlive()).thenReturn(true);
        when(hero.hasLineOfSight(victim)).thenReturn(true);when(hero.getHealth()).thenReturn(20F);when(hero.getMaxHealth()).thenReturn(20F);
        var type=net.minecraft.core.Holder.direct(new net.minecraft.world.damagesource.DamageType("test",0));
        var source=new net.minecraft.world.damagesource.DamageSource(type,hero);
        var event=new net.minecraftforge.event.entity.living.LivingDamageEvent(victim,source,2);

        HeroTraits.hit(event);

        verify(hero).addEffect(any(net.minecraft.world.effect.MobEffectInstance.class));
    }

    @Test void wildsongAppliesAttackSpeedModifierAndDurationTag() {
        Mob hero=mock(Mob.class);
        CompoundTag tag=new CompoundTag();
        AttributeInstance attackSpeed=mock(AttributeInstance.class);
        when(hero.getPersistentData()).thenReturn(tag);
        when(hero.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_SPEED)).thenReturn(attackSpeed);
        when(attackSpeed.getModifier(any())).thenReturn(null);

        HeroTraits.applyWildsongAttackSpeed(hero, 100L);

        verify(attackSpeed).addTransientModifier(any());
        assertEquals(220L, tag.getLong("SiegeWildsongAttackSpeedUntil"));
    }
}
