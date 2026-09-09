package com.devfarinsky.siegeoverhaul.compat;

import com.devfarinsky.siegeoverhaul.MinecraftTestSupport;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SiegeCorpseCleanupTest extends MinecraftTestSupport {
    public interface CorpseApi { Contents getDeath(); }
    public static class Contents {
        public final List<ItemStack> main=new ArrayList<>(),armor=new ArrayList<>(),offhand=new ArrayList<>(),extra=new ArrayList<>();
        public List<ItemStack> getMainInventory(){return main;}
        public List<ItemStack> getArmorInventory(){return armor;}
        public List<ItemStack> getOffHandInventory(){return offhand;}
        public List<ItemStack> getAdditionalItems(){return extra;}
    }
    private Entity corpse(Contents contents){
        Entity entity=mock(Entity.class,withSettings().extraInterfaces(CorpseApi.class));
        when(((CorpseApi)entity).getDeath()).thenReturn(contents); return entity;
    }
    @Test void successfulConversionCopiesAllInventoryGroupsAndTagsBeforeClearingBody() throws Exception {
        var contents=new Contents();
        contents.main.add(new ItemStack(Items.EMERALD,12)); contents.armor.add(new ItemStack(Items.IRON_HELMET));
        contents.offhand.add(new ItemStack(Items.SHIELD)); contents.extra.add(new ItemStack(Items.DIAMOND));
        contents.armor.get(0).getOrCreateTag().putString("CustomOwner","keep");
        Entity body=corpse(contents); var level=mock(ServerLevel.class);
        when(level.addFreshEntity(any())).thenReturn(true);
        List<ItemStack> copies=new ArrayList<>();
        try(var drops=mockConstruction(ItemEntity.class,(item,context)->copies.add((ItemStack)context.arguments().get(4)))){
            assertTrue(SiegeCorpseCleanup.spill(level,body)); assertEquals(4,drops.constructed().size());
            assertEquals(12,copies.get(0).getCount()); assertEquals("keep",copies.get(1).getTag().getString("CustomOwner"));
            assertTrue(contents.main.get(0).isEmpty()); assertTrue(contents.armor.get(0).isEmpty());
            assertTrue(contents.offhand.get(0).isEmpty()); assertTrue(contents.extra.get(0).isEmpty());
            verify(body).discard();
        }
    }
    @Test void failedDropRollsBackSpawnedItemsAndPreservesOriginalInventory() throws Exception {
        var contents=new Contents(); contents.main.add(new ItemStack(Items.EMERALD,12)); contents.extra.add(new ItemStack(Items.DIAMOND));
        Entity body=corpse(contents); var level=mock(ServerLevel.class); when(level.addFreshEntity(any())).thenReturn(true,false);
        try(var drops=mockConstruction(ItemEntity.class)){
            assertFalse(SiegeCorpseCleanup.spill(level,body));
            verify(drops.constructed().get(0)).discard(); verify(body,never()).discard();
            assertEquals(12,contents.main.get(0).getCount()); assertEquals(1,contents.extra.get(0).getCount());
        }
    }
    @Test void unknownInventoryApiCannotDiscardBodyOrSpawnItems() {
        Entity body=mock(Entity.class); var level=mock(ServerLevel.class);
        assertThrows(ReflectiveOperationException.class,()->SiegeCorpseCleanup.spill(level,body));
        verify(body,never()).discard(); verifyNoInteractions(level);
    }
    @Test void unknownPlayerAndLegacyBodiesAreNotEligibleForEnemyExpiration() {
        var tag=new net.minecraft.nbt.CompoundTag();
        assertFalse(SiegeCorpseCleanup.expiredEnemy(tag,100000,120));
        tag.putLong("SiegeCorpseBorn",100);
        assertFalse(SiegeCorpseCleanup.expiredEnemy(tag,100000,120));
        tag.putBoolean("SiegeEnemyCorpse",true);
        assertFalse(SiegeCorpseCleanup.expiredEnemy(tag,2499,120));
        assertTrue(SiegeCorpseCleanup.expiredEnemy(tag.copy(),2500,120));
        assertFalse(SiegeCorpseCleanup.expiredEnemy(tag,2500,0));
        assertFalse(SiegeCorpseCleanup.expiredEnemy(tag,0,120));
    }
    @Test void oversizedInventoryIsPreservedWithoutUnboundedDropBurst() {
        var contents=new Contents(); for(int i=0;i<129;i++) contents.main.add(new ItemStack(Items.DIAMOND));
        Entity body=corpse(contents); var level=mock(ServerLevel.class);
        assertThrows(IllegalStateException.class,()->SiegeCorpseCleanup.spill(level,body));
        verify(body,never()).discard(); verifyNoInteractions(level); assertFalse(contents.main.get(0).isEmpty());
    }
}
