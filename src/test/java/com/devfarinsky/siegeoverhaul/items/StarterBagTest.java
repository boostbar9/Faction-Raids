package com.devfarinsky.siegeoverhaul.items;
import com.devfarinsky.siegeoverhaul.MinecraftTestSupport;
import com.devfarinsky.siegeoverhaul.core.TerritoryFortification;
import com.devfarinsky.siegeoverhaul.core.CoreHiring;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class StarterBagTest extends MinecraftTestSupport {
    /** Content/NBT contracts only; these do not prove book rendering or native claiming. */
    @Test void setupGuideUsesNativeScreensInsteadOfUnsupportedCommands() {
        ItemStack book = StarterBagItem.setupGuide();
        assertTrue(book.is(Items.WRITTEN_BOOK));
        CompoundTag tag = book.getOrCreateTag();
        assertEquals("Faction Setup Guide", tag.getString("title"));
        assertEquals("The Siege Overhaul", tag.getString("author"));
        assertEquals(0, tag.getInt("generation"));
        var pages = tag.getList("pages", Tag.TAG_STRING);
        assertEquals(5, pages.size());
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < pages.size(); i++) {
            Component page = Component.Serializer.fromJson(pages.getString(i));
            assertNotNull(page, "Every page must be valid vanilla book component JSON");
            text.append(page.getString()).append('\n');
        }
        String guide = text.toString().replaceAll("\\s+", " ");
        assertTrue(guide.contains("U by default"));
        assertTrue(guide.contains("Create Faction"));
        assertTrue(guide.contains("Options > Controls"));
        assertTrue(guide.contains("faction leader in the Overworld"));
        assertTrue(guide.contains("M by default"));
        assertTrue(guide.contains("Claim Area for the first 5x5 claim"));
        assertTrue(guide.contains("Claim Chunk extends an existing claim"));
        assertTrue(guide.contains("entire 5x5 area"));
        assertTrue(guide.contains("three-chunk buffer"));
        assertTrue(guide.contains("inventory, not the Siege Core Treasury"));
        assertFalse(guide.contains("/faction create"));
        assertFalse(guide.contains("/claim create"));
    }

    @Test void setupGuidePagesSurviveSavedBagOverflowWithoutChangingOtherContents() {
        ItemStack book = StarterBagItem.setupGuide();
        CompoundTag original = book.save(new CompoundTag());
        ItemStack emeralds = new ItemStack(Items.EMERALD, 32);
        var saved = StarterBagItem.save(List.of(book, emeralds));
        ItemStack restored = ItemStack.of(saved.getCompound(0));
        Inventory inv = new Inventory(mock(Player.class));
        for (int i = 0; i < 36; i++) inv.setItem(i, new ItemStack(Items.COBBLESTONE, 64));
        StarterBagItem.insert(inv, restored);
        assertEquals(original, restored.save(new CompoundTag()));
        inv.setItem(6, ItemStack.EMPTY);
        StarterBagItem.insert(inv, restored);
        assertTrue(restored.isEmpty());
        assertEquals(original, inv.getItem(6).save(new CompoundTag()));
        ItemStack savedCurrency = ItemStack.of(saved.getCompound(1));
        assertTrue(savedCurrency.is(Items.EMERALD));
        assertEquals(32, savedCurrency.getCount());
        assertEquals(32, emeralds.getCount());
    }

    @Test void buildingBagIsAShelterBudget() throws Exception {
        var contents=StarterBagItem.survivalContents();
        // v4.18.0 slimmed the shelter kit: stone bricks were 256, now 96.
        assertEquals(96,contents.stream().filter(s->s.is(Items.STONE_BRICKS)).mapToInt(ItemStack::getCount).sum());
        assertEquals(32,contents.stream().filter(s->s.is(Items.BREAD)||s.is(Items.COOKED_BEEF)).mapToInt(ItemStack::getCount).sum());
        assertEquals(2,contents.stream().filter(s->s.is(Items.CHEST)).mapToInt(ItemStack::getCount).sum());
    }
    @Test void fundsCoverFirstFactionClaimAndFourTroops() {
        assertEquals(192,StarterBagItem.budget(10,64,10,6));
        for(int cost:new int[]{0,10,100,1453})assertTrue(StarterBagItem.budget(cost,cost,cost,cost)>=cost*6);
    }
    @Test void defaultOpeningCanFundBuilderTwoDefendersAndWholePerimeterFee() throws Exception {
        // Pinned native defaults plus production hire uplift; server configs can differ.
        var uplift = CoreHiring.class.getDeclaredMethod("applyUplift", int.class, int.class);
        uplift.setAccessible(true);
        int faction = 10, claim = 64;
        int shield = (Integer) uplift.invoke(null, 10, 1);
        int archer = (Integer) uplift.invoke(null, 6, 2);
        int builder = (Integer) uplift.invoke(null, 20, 7);
        assertEquals(15, shield); assertEquals(9, archer); assertEquals(25, builder);
        int starterBudget = StarterBagItem.budget(faction, claim, shield, archer);
        assertEquals(192, starterBudget);
        assertEquals(64, TerritoryFortification.PRICE);
        assertEquals(5, starterBudget - faction - claim - builder - shield - archer
                - TerritoryFortification.PRICE);
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
