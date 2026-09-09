package com.devfarinsky.siegeoverhaul.core;

import com.devfarinsky.siegeoverhaul.MinecraftTestSupport;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.RandomSource;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.*;
import net.minecraft.world.item.*;
import org.junit.jupiter.api.Test;
import java.util.HashSet;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

class RecruitPersonalityTest extends MinecraftTestSupport {
    @Test void everySoldierGetsNativeArmorRoleWeaponAndFiniteSupplies() {
        for (int role=0; role<4; role++) {
            Mob mob=mock(Mob.class);when(mob.getPersistentData()).thenReturn(new CompoundTag());
            when(mob.getRandom()).thenReturn(RandomSource.create(42+role));
            SimpleContainer inventory=new SimpleContainer(36);
            RecruitPersonality.prepare(mob,role,inventory);
            EquipmentSlot[] slots={EquipmentSlot.HEAD,EquipmentSlot.CHEST,EquipmentSlot.LEGS,EquipmentSlot.FEET};
            for(int i=0;i<4;i++) {
                assertTrue(inventory.getItem(i).getItem() instanceof ArmorItem);
                verify(mob).setItemSlot(slots[i],inventory.getItem(i));
                assertEquals("minecraft:sentry",inventory.getItem(i).getTag().getCompound("Trim").getString("pattern"));
            }
            assertTrue(inventory.getItem(5).is(role==2?Items.BOW:role==3?Items.CROSSBOW:Items.IRON_SWORD));
            assertTrue(inventory.getItem(5).hasCustomHoverName());
            verify(mob).setItemSlot(EquipmentSlot.MAINHAND,inventory.getItem(5));
            assertEquals(8,inventory.getItem(6).getCount());assertTrue(inventory.getItem(6).is(Items.BREAD));
            if(role>=2){assertTrue(inventory.getItem(7).is(Items.ARROW));assertEquals(32,inventory.getItem(7).getCount());}
            if(role==1){assertTrue(inventory.getItem(4).is(Items.SHIELD));verify(mob).setItemSlot(EquipmentSlot.OFFHAND,inventory.getItem(4));}
            verify(mob).setCustomName(any());
        }
    }
    @Test void repeatedPreparationDoesNotRefillOrReplacePlayerEquipment() {
        Mob mob=mock(Mob.class);CompoundTag tag=new CompoundTag();when(mob.getPersistentData()).thenReturn(tag);
        when(mob.getRandom()).thenReturn(RandomSource.create(8));
        SimpleContainer inventory=new SimpleContainer(36);RecruitPersonality.prepare(mob,2,inventory);
        ItemStack custom=new ItemStack(Items.DIAMOND_CHESTPLATE);inventory.setItem(1,custom);inventory.setItem(7,ItemStack.EMPTY);
        clearInvocations(mob);RecruitPersonality.prepare(mob,2,inventory);
        assertSame(custom,inventory.getItem(1));assertTrue(inventory.getItem(7).isEmpty());
        verify(mob,never()).setCustomName(any());verify(mob,never()).setItemSlot(any(),any());
    }
    @Test void workersAndHeroesKeepTheirExistingIdentityAndGear() {
        for(int role:new int[]{-1,4,9,10,13}) {
            Mob mob=mock(Mob.class);SimpleContainer inventory=new SimpleContainer(36);
            RecruitPersonality.prepare(mob,role,inventory);verifyNoInteractions(mob);assertTrue(inventory.isEmpty());
        }
    }
    @Test void namesHaveBroadRepeatableVariety() {
        RandomSource a=RandomSource.create(12),b=RandomSource.create(12);HashSet<String> names=new HashSet<>();
        for(int i=0;i<100;i++){String name=RecruitPersonality.name(a);assertEquals(name,RecruitPersonality.name(b));names.add(name);}
        assertTrue(names.size()>75);
    }
}
