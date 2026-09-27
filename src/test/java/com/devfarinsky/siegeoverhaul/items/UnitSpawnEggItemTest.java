package com.devfarinsky.siegeoverhaul.items;

import com.devfarinsky.siegeoverhaul.MinecraftTestSupport;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.phys.AABB;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class UnitSpawnEggItemTest extends MinecraftTestSupport {
    public interface NativeRecruit {
        SimpleContainer getInventory();
        void setXpLevel(int value);
    }
    private static class Fixture {
        final ServerLevel level=mock(ServerLevel.class);
        final Player player=mock(Player.class);
        final UseOnContext ctx=mock(UseOnContext.class);
        final EntityType<?> type=mock(EntityType.class);
        final Mob mob=mock(Mob.class,withSettings().extraInterfaces(NativeRecruit.class));
        final NativeRecruit recruit=(NativeRecruit)mob;
        final ItemStack stack=new ItemStack(Items.EGG,2);
        final CompoundTag tag=new CompoundTag();
        final SimpleContainer inventory=new SimpleContainer(16);
        final Abilities abilities=new Abilities();
        Fixture() {
            when(ctx.getClickedPos()).thenReturn(new BlockPos(0,64,0));when(ctx.getClickedFace()).thenReturn(Direction.UP);
            when(ctx.getPlayer()).thenReturn(player);when(ctx.getItemInHand()).thenReturn(stack);
            when(player.getAbilities()).thenReturn(abilities);
            when(level.getBlockState(any())).thenReturn(Blocks.STONE.defaultBlockState());
            when(level.hasChunkAt(any())).thenReturn(true);
            var border=mock(WorldBorder.class);when(level.getWorldBorder()).thenReturn(border);when(border.isWithinBounds(any(BlockPos.class))).thenReturn(true);
            doReturn(mob).when(type).create(level);when(mob.getPersistentData()).thenReturn(tag);
            when(mob.getBoundingBox()).thenReturn(new AABB(0,65,0,1,67,1));
            when(level.noCollision(mob)).thenReturn(true);when(level.getEntities(eq(mob),any(AABB.class))).thenReturn(List.of());
            when(level.addFreshEntity(mob)).thenReturn(true);when(recruit.getInventory()).thenReturn(inventory);
        }
        InteractionResult spawn(){return new UnitSpawnEggItem(22,Rarity.EPIC).spawn(level,ctx,type);}
    }
    @Test void outfitsHeroBeforePublishingAndConsumesOnlySurvivalEgg() {
        for(boolean creative:new boolean[]{false,true}) {
            var f=new Fixture();f.abilities.instabuild=creative;
            when(f.level.addFreshEntity(f.mob)).thenAnswer(call->{assertEquals(22,f.tag.getInt("SiegeHeroRole"));assertTrue(f.tag.getBoolean("SiegeHiredHero"));assertFalse(f.inventory.getItem(5).isEmpty());return true;});
            assertEquals(InteractionResult.CONSUME,f.spawn());assertEquals(creative?2:1,f.stack.getCount());
            verify(f.mob).setPersistenceRequired();verify(f.recruit).setXpLevel(10);
        }
    }
    @Test void rejectedInsertionOrOutfittingNeverLeavesPartialUnitOrConsumesEgg() {
        for(boolean badInventory:new boolean[]{false,true}) {
            var f=new Fixture();
            if(badInventory)when(f.recruit.getInventory()).thenReturn(null);else when(f.level.addFreshEntity(f.mob)).thenReturn(false);
            assertEquals(InteractionResult.FAIL,f.spawn());assertEquals(2,f.stack.getCount());verify(f.mob).discard();
            if(badInventory)verify(f.level,never()).addFreshEntity(any());
        }
    }
    @Test void blockedOrOccupiedSpaceNeverFallsBackInsideClickedSolidBlock() {
        var f=new Fixture();when(f.level.noCollision(f.mob)).thenReturn(false);
        assertEquals(InteractionResult.FAIL,f.spawn());verify(f.mob).moveTo(.5,65,.5,0,0);
        verify(f.level,never()).addFreshEntity(any());assertEquals(2,f.stack.getCount());
        var occupied=new Fixture();when(occupied.level.getEntities(eq(occupied.mob),any(AABB.class))).thenReturn(List.of(mock(Entity.class)));
        assertEquals(InteractionResult.FAIL,occupied.spawn());verify(occupied.level,never()).addFreshEntity(any());
    }
    @Test void unloadedAndOutsideWorldLocationsDoNotCreateEntities() {
        for(int reason=0;reason<3;reason++) {
            var f=new Fixture();
            if(reason==0)when(f.level.hasChunkAt(any())).thenReturn(false);
            if(reason==1)when(f.level.getWorldBorder().isWithinBounds(any(BlockPos.class))).thenReturn(false);
            if(reason==2)when(f.level.isOutsideBuildHeight(any(BlockPos.class))).thenReturn(true);
            assertEquals(InteractionResult.FAIL,f.spawn());verify(f.type,never()).create(any());assertEquals(2,f.stack.getCount());
        }
    }
}
