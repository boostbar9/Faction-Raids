package com.devfarinsky.siegeoverhaul.nativecompat;

import com.devfarinsky.siegeoverhaul.MinecraftTestSupport;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.animal.horse.Horse;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProtectedHorseUpkeepTest extends MinecraftTestSupport {
    @Test void publicIdentityCheckReturnsTheExactNativeCaptureWithoutChangingItemsOrNbt() {
        var horse=mock(Horse.class);var item=new ItemStack(Items.BREAD,12);
        item.getOrCreateTag().putString("qa","preserved");
        var live=new SimpleContainer(item);var before=item.save(new CompoundTag());
        when(horse.hasInventoryChanged(live)).thenReturn(false);
        assertSame(live,ProtectedUpkeepAccess.capturedHorseInventory(horse,live));
        assertSame(item,live.getItem(0));assertEquals(before,item.save(new CompoundTag()));
        verify(horse).hasInventoryChanged(live);verifyNoMoreInteractions(horse);
    }
    @Test void equalContentsDoNotAuthorizeAStaleNativeContainerAfterHorseReload() {
        var horse=mock(Horse.class);var live=new SimpleContainer(new ItemStack(Items.BREAD,12));
        var stale=new SimpleContainer(live.getItem(0).copy());
        when(horse.hasInventoryChanged(live)).thenReturn(false);
        when(horse.hasInventoryChanged(stale)).thenReturn(true);
        assertThrows(IllegalStateException.class,()->ProtectedUpkeepAccess.capturedHorseInventory(horse,stale));
        assertSame(live,ProtectedUpkeepAccess.capturedHorseInventory(horse,live));
        assertEquals(12,stale.getItem(0).getCount());assertEquals(12,live.getItem(0).getCount());
    }
    @Test void missingOrUnknownContainerImplementationsAreRejectedBeforeNativeIdentityLookup() {
        var horse=mock(Horse.class);Container unknown=new SimpleContainer(2) {};
        assertThrows(IllegalStateException.class,()->ProtectedUpkeepAccess.capturedHorseInventory(horse,null));
        assertThrows(IllegalStateException.class,()->ProtectedUpkeepAccess.capturedHorseInventory(horse,unknown));
        verifyNoInteractions(horse);
    }
}
