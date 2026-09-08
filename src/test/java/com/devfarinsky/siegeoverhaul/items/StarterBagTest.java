package com.devfarinsky.siegeoverhaul.items;
import com.devfarinsky.siegeoverhaul.MinecraftTestSupport;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class StarterBagTest extends MinecraftTestSupport {
    @Test void buildingBagIsAShelterBudget() throws Exception {
        var contents=new StarterBagItem(false).contents();
        assertEquals(256,contents.stream().filter(s->s.is(Items.STONE_BRICKS)).mapToInt(ItemStack::getCount).sum());
        assertEquals(32,contents.stream().filter(s->s.is(Items.BREAD)||s.is(Items.COOKED_BEEF)).mapToInt(ItemStack::getCount).sum());
        assertEquals(2,contents.stream().filter(s->s.is(Items.CHEST)).mapToInt(ItemStack::getCount).sum());
    }
    @Test void fundsCoverFirstFactionClaimAndFourTroops() {
        assertEquals(192,StarterBagItem.budget(10,64,10,6));
        for(int cost:new int[]{0,10,100,1453})assertTrue(StarterBagItem.budget(cost,cost,cost,cost)>=cost*6);
    }
    @Test void overflowRemainsAndPartialStacksMergeWithoutLoss() {
        Inventory inv=new Inventory(mock(Player.class));
        for(int i=0;i<36;i++)inv.setItem(i,new ItemStack(Items.COBBLESTONE,64));
        inv.setItem(5,new ItemStack(Items.EMERALD,60));
        ItemStack money=new ItemStack(Items.EMERALD,32);StarterBagItem.insert(inv,money);
        assertEquals(64,inv.getItem(5).getCount());assertEquals(28,money.getCount());
        var saved=StarterBagItem.save(List.of(money));
        ItemStack restored=ItemStack.of((CompoundTag)saved.get(0));
        inv.setItem(6,ItemStack.EMPTY);StarterBagItem.insert(inv,restored);
        assertTrue(restored.isEmpty());assertEquals(28,inv.getItem(6).getCount());
    }
    @Test void fullInventoryDoesNotDestroyOrDropContents() {
        Inventory inv=new Inventory(mock(Player.class));
        for(int i=0;i<36;i++)inv.setItem(i,new ItemStack(Items.COBBLESTONE,64));
        ItemStack tool=new ItemStack(Items.IRON_PICKAXE);tool.setDamageValue(7);
        StarterBagItem.insert(inv,tool);assertEquals(1,tool.getCount());assertEquals(7,tool.getDamageValue());
    }
}
