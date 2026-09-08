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
    @Test void distinctiveLoadoutsLiveInNativeEquipmentSlots() {
        var trims=new java.util.HashSet<String>();
        for(int role=10;role<=13;role++) {
            Mob mob=mock(Mob.class);CompoundTag tag=new CompoundTag();when(mob.getPersistentData()).thenReturn(tag);
            SimpleContainer inventory=new SimpleContainer(36);HeroTraits.equip(mob,role,inventory);
            trims.add(inventory.getItem(1).getTag().getCompound("Trim").getString("material"));
            assertEquals(role,tag.getInt("SiegeHeroRole"));
            assertTrue(inventory.getItem(5).isEnchanted());
            assertEquals(role==12?Items.BOW:role==13?Items.CROSSBOW:Items.DIAMOND_SWORD,inventory.getItem(5).getItem());
            if(role<12)assertTrue(inventory.getItem(4).is(Items.SHIELD));else assertTrue(inventory.getItem(4).isEmpty());
            verify(mob).setItemSlot(EquipmentSlot.MAINHAND,inventory.getItem(5));
        }
        assertEquals(4,trims.size());
    }
    @Test void cooldownSurvivesReloadAndRecoversAfterWorldClockReset() {
        assertFalse(HeroTraits.ready(100,500));assertTrue(HeroTraits.ready(500,500));assertTrue(HeroTraits.ready(0,5000));
    }
}
