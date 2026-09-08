package com.devfarinsky.siegeoverhaul.core;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.*;
import net.minecraft.world.item.enchantment.Enchantments;
import java.util.List;

/** Explicit fixed odds, one reward per box, atomic main-inventory purchases. */
public final class CoreLoot {
    private CoreLoot() {}
    public static final int OPEN_TICKS=60;
    public static final String[] NAMES={"Field Supplies","Veteran Armory","Royal Treasury"};
    public static int price(int box){return switch(box){case 0->16;case 1->48;case 2->96;default->-1;};}
    public static String odds() { return "Common 50% | Uncommon 30% | Rare 15% | Epic 5%"; }
    public static String rarity(int tier) { return switch(tier){case 0->"Common";case 1->"Uncommon";case 2->"Rare";default->"Epic";}; }
    public record Receipt(ItemStack prize,int tier) {}
    public static ItemStack reward(int box,int roll) {
        if(price(box)<0 || roll<0 || roll>=100)throw new IllegalArgumentException("Invalid loot roll");
        int tier=roll<50?0:roll<80?1:roll<95?2:3;
        ItemStack result;
        if(box==0)return switch(tier){case 0->new ItemStack(Items.GOLDEN_APPLE,2);case 1->new ItemStack(Items.ARROW,64);case 2->new ItemStack(Items.DIAMOND,4);default->new ItemStack(Items.ENCHANTED_GOLDEN_APPLE);};
        if(box==1) {
            result=new ItemStack(switch(tier){case 0->Items.BOW;case 1->Items.DIAMOND_AXE;case 2->Items.DIAMOND_CHESTPLATE;default->Items.DIAMOND_SWORD;});
            result.enchant(switch(tier){case 0->Enchantments.POWER_ARROWS;case 1->Enchantments.BLOCK_EFFICIENCY;case 2->Enchantments.ALL_DAMAGE_PROTECTION;default->Enchantments.SHARPNESS;},switch(tier){case 1->3;case 3->4;default->2;});
        } else {
            if(tier==0)return new ItemStack(Items.GOLDEN_APPLE,8);
            result=new ItemStack(tier==1?Items.DIAMOND_BOOTS:tier==2?Items.DIAMOND_CHESTPLATE:Items.NETHERITE_SWORD);
            result.enchant(tier==3?Enchantments.SHARPNESS:Enchantments.ALL_DAMAGE_PROTECTION,tier==2?4:3);
            if(tier==1)result.enchant(Enchantments.FALL_PROTECTION,4);
        }
        return result;
    }
    static boolean fits(List<ItemStack> inventory,ItemStack reward) {
        int room=0;
        for(var stack:inventory) {
            if(stack.isEmpty())room+=reward.getMaxStackSize();
            else if(ItemStack.isSameItemSameTags(stack,reward))room+=Math.max(0,stack.getMaxStackSize()-stack.getCount());
            if(room>=reward.getCount())return true;
        }
        return false;
    }
    public static boolean purchase(ServerPlayer player,int box) { return purchaseWithReceipt(player,box)!=null; }
    public static Receipt purchaseWithReceipt(ServerPlayer player,int box) {
        int price=price(box);if(price<0)return null;
        long now=player.serverLevel().getGameTime();var data=player.getPersistentData();long next=data.getLong("SiegeLootNext");
        if(next>now && next<=now+OPEN_TICKS)return null;
        var inventory=player.getInventory();
        int emeralds=inventory.items.stream().filter(s->s.is(Items.EMERALD)).mapToInt(ItemStack::getCount).sum();
        if(emeralds<price){player.sendSystemMessage(Component.literal("You need "+price+" emeralds."));return null;}
        // Require space for every possible outcome before rolling; full inventories
        // cannot be used to filter unwanted rewards or lose a paid reward.
        for(int roll:new int[]{0,50,80,95})if(!fits(inventory.items,reward(box,roll))) {
            player.sendSystemMessage(Component.literal("Make room in your inventory before opening a box."));return null;
        }
        int roll=player.getRandom().nextInt(100);
        ItemStack prize=reward(box,roll);
        int remaining=price;
        for(var stack:inventory.items)if(stack.is(Items.EMERALD)) {
            int take=Math.min(remaining,stack.getCount());stack.shrink(take);remaining-=take;if(remaining==0)break;
        }
        // Capacity was checked on this same server thread; payment can only free space.
        inventory.add(prize.copy());inventory.setChanged();data.putLong("SiegeLootNext",now+OPEN_TICKS);
        // Keep chat free of reward details while the client plays its sealed reveal.
        // Delivery remains immediate, so closing the menu cannot lose a paid prize.
        player.sendSystemMessage(Component.literal("Opening "+NAMES[box]+"... Reward secured in your inventory."));
        return new Receipt(prize.copy(),roll<50?0:roll<80?1:roll<95?2:3);
    }
}
