package com.devfarinsky.siegeoverhaul.camp;
import com.devfarinsky.siegeoverhaul.MinecraftTestSupport;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class BuilderSupportTest extends MinecraftTestSupport {
    @Test void kitHasArmorAndFourApplesWithoutRefillingConsumedSuppliesOrBrokenArmor() {
        Mob worker=mock(Mob.class);when(worker.getPersistentData()).thenReturn(new CompoundTag());
        SimpleContainer inventory=new SimpleContainer(36);BuilderSupport.equip(worker,inventory);
        assertTrue(inventory.getItem(1).is(Items.DIAMOND_CHESTPLATE));assertTrue(inventory.getItem(1).isEnchanted());
        assertTrue(inventory.getItem(6).is(Items.GOLDEN_APPLE));assertEquals(4,inventory.getItem(6).getCount());
        inventory.getItem(6).shrink(3);inventory.setItem(1,ItemStack.EMPTY);
        BuilderSupport.equip(worker,inventory);
        assertEquals(1,inventory.getItem(6).getCount());assertTrue(inventory.getItem(1).isEmpty());
    }
    @Test void fullBackpackRetainsUndeliveredApplesAcrossSaves() {
        Mob worker=mock(Mob.class);CompoundTag data=new CompoundTag();when(worker.getPersistentData()).thenReturn(data);
        SimpleContainer inventory=new SimpleContainer(36);for(int i=6;i<36;i++)inventory.setItem(i,new ItemStack(Items.COBBLESTONE,64));
        BuilderSupport.equip(worker,inventory);
        var saved=data.copy().getList("SiegeBuilderSupplies",net.minecraft.nbt.Tag.TAG_COMPOUND);
        assertEquals(4,ItemStack.of(saved.getCompound(0)).getCount());
        assertTrue(ItemStack.of(saved.getCompound(0)).is(Items.GOLDEN_APPLE));
    }
}
