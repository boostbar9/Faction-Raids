package com.devfarinsky.siegeoverhaul.core;

import com.devfarinsky.siegeoverhaul.enchant.ModEnchantments;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.*;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import java.util.List;

/**
 * War chests: explicit fixed odds, one reward per box, atomic purchases.
 *
 * <p>Each chest is a themed set with one named reward per rarity. Rewards are
 * hand-built war trophies — a custom name, lore that explains what makes the
 * piece special, and a signature enchantment loadout that usually includes one
 * of the mod's own {@link ModEnchantments} that cannot be obtained anywhere
 * else. The odds are unchanged (50/30/15/5) and every outcome is still
 * required to fit in the player's inventory before a roll is taken.
 */
public final class CoreLoot {
    private CoreLoot() {}
    public static final int OPEN_TICKS=60;
    public static final String[] NAMES={"Field Supplies","Veteran Armory","Royal Treasury"};
    public static int price(int box){return switch(box){case 0->16;case 1->48;case 2->96;default->-1;};}
    public static String odds() { return "Common 50% | Uncommon 30% | Rare 15% | Epic 5%"; }
    public static String rarity(int tier) { return switch(tier){case 0->"Common";case 1->"Uncommon";case 2->"Rare";default->"Epic";}; }
    /** Highest rarity a chest can roll. Every chest can reach Epic. */
    public static int topTier(int box) { if(price(box)<0)throw new IllegalArgumentException("Invalid loot box"); return 3; }
    /** Rarity tier for a 0-99 roll, matching the advertised odds exactly. */
    public static int tier(int roll) {
        if(roll<0 || roll>=100)throw new IllegalArgumentException("Invalid loot roll");
        return roll<50?0:roll<80?1:roll<95?2:3;
    }
    public record Receipt(ItemStack prize,int tier) {}

    public static ItemStack reward(int box,int roll) {
        if(price(box)<0)throw new IllegalArgumentException("Invalid loot box");
        int tier=tier(roll);
        return switch(box) {
            case 0 -> fieldSupplies(tier);
            case 1 -> veteranArmory(tier);
            default -> royalTreasury(tier);
        };
    }

    /** Cheap chest: campaign consumables rising to a signature scout kit. */
    private static ItemStack fieldSupplies(int tier) {
        return switch(tier) {
            case 0 -> named(new ItemStack(Items.GOLDEN_APPLE,3),"Campaign Rations",
                    ChatFormatting.GRAY,"Pressed gold leaf and honey.","Enough to hold a wall.");
            case 1 -> named(new ItemStack(Items.SPECTRAL_ARROW,32),"Signal Shafts",
                    ChatFormatting.GREEN,"Marks whatever it strikes.","Quartermaster issue.");
            case 2 -> trophy(new ItemStack(Items.LEATHER_BOOTS),"Scout's Stride",
                    ChatFormatting.AQUA,
                    List.of("Worn thin by a thousand night marches.","Signature: sure-footed on any ground."),
                    entry(Enchantments.FALL_PROTECTION,4),
                    entry(Enchantments.SOUL_SPEED,3),
                    entry(Enchantments.DEPTH_STRIDER,2),
                    entry(Enchantments.UNBREAKING,3),
                    entry(Enchantments.MENDING,1));
            default -> trophy(new ItemStack(Items.LEATHER_CHESTPLATE),"Quartermaster's Cloak",
                    ChatFormatting.LIGHT_PURPLE,
                    List.of("Every patch is a battle it survived.","Signature: Bulwark — hardens as you are surrounded."),
                    entry(ModEnchantments.BULWARK,1),
                    entry(Enchantments.ALL_DAMAGE_PROTECTION,4),
                    entry(Enchantments.THORNS,2),
                    entry(Enchantments.UNBREAKING,3),
                    entry(Enchantments.MENDING,1));
        };
    }

    /** Mid chest: line-infantry war gear with a siege-cracking finish. */
    private static ItemStack veteranArmory(int tier) {
        return switch(tier) {
            case 0 -> trophy(new ItemStack(Items.IRON_SWORD),"Drillmaster's Blade",
                    ChatFormatting.WHITE,
                    List.of("Blunted on ten thousand training shields.","Still finds the gap in a guard."),
                    entry(Enchantments.SHARPNESS,3),
                    entry(Enchantments.KNOCKBACK,1),
                    entry(Enchantments.UNBREAKING,3));
            case 1 -> trophy(new ItemStack(Items.DIAMOND_AXE),"Siegebreaker Maul",
                    ChatFormatting.GREEN,
                    List.of("Made for gates, not trees.","Signature: Siegebreaker — tears through raider armour."),
                    entry(ModEnchantments.SIEGEBREAKER,2),
                    entry(Enchantments.SHARPNESS,4),
                    entry(Enchantments.BLOCK_EFFICIENCY,4),
                    entry(Enchantments.UNBREAKING,3));
            case 2 -> trophy(new ItemStack(Items.DIAMOND_CHESTPLATE),"Ironwall Cuirass",
                    ChatFormatting.AQUA,
                    List.of("Taken from a captain who never stepped back.","Signature: Bulwark — hardens as you are surrounded."),
                    entry(ModEnchantments.BULWARK,2),
                    entry(Enchantments.ALL_DAMAGE_PROTECTION,4),
                    entry(Enchantments.THORNS,3),
                    entry(Enchantments.UNBREAKING,3),
                    entry(Enchantments.MENDING,1));
            default -> trophy(new ItemStack(Items.BOW),"Stormcaller Longbow",
                    ChatFormatting.LIGHT_PURPLE,
                    List.of("Its string hums before the thunder does.","Signature: Plunderer — kills bank war-key progress."),
                    entry(ModEnchantments.PLUNDERER,2),
                    entry(Enchantments.POWER_ARROWS,5),
                    entry(Enchantments.PUNCH_ARROWS,2),
                    entry(Enchantments.FLAMING_ARROWS,1),
                    entry(Enchantments.INFINITY_ARROWS,1),
                    entry(Enchantments.UNBREAKING,3));
        };
    }

    /** Expensive chest: crown regalia, up to a truly unique blade. */
    private static ItemStack royalTreasury(int tier) {
        return switch(tier) {
            case 0 -> named(new ItemStack(Items.ENCHANTED_GOLDEN_APPLE,2),"Royal Physician's Draught",
                    ChatFormatting.GOLD,"Bottled for the king's own guard.","Never issued to anyone else.");
            case 1 -> trophy(new ItemStack(Items.NETHERITE_LEGGINGS),"Warden's Greaves",
                    ChatFormatting.GREEN,
                    List.of("Forged for the watch that never sleeps.","Signature: carries its own repairs."),
                    entry(Enchantments.ALL_DAMAGE_PROTECTION,4),
                    entry(Enchantments.FALL_PROTECTION,4),
                    entry(Enchantments.UNBREAKING,3),
                    entry(Enchantments.MENDING,1));
            case 2 -> trophy(new ItemStack(Items.NETHERITE_CHESTPLATE),"Crown Aegis",
                    ChatFormatting.AQUA,
                    List.of("The crest of a kingdom that has never fallen.","Signature: Bulwark III — hardest when outnumbered."),
                    entry(ModEnchantments.BULWARK,3),
                    entry(Enchantments.ALL_DAMAGE_PROTECTION,4),
                    entry(Enchantments.THORNS,3),
                    entry(Enchantments.UNBREAKING,3),
                    entry(Enchantments.MENDING,1));
            default -> trophy(new ItemStack(Items.NETHERITE_SWORD),"Kingsbane",
                    ChatFormatting.LIGHT_PURPLE,
                    List.of("Named for the last king it met.","Signature: Siegebreaker III and Plunderer III."),
                    entry(ModEnchantments.SIEGEBREAKER,3),
                    entry(ModEnchantments.PLUNDERER,3),
                    entry(Enchantments.SHARPNESS,5),
                    entry(Enchantments.FIRE_ASPECT,2),
                    entry(Enchantments.MOB_LOOTING,3),
                    entry(Enchantments.UNBREAKING,3),
                    entry(Enchantments.MENDING,1));
        };
    }

    /** One enchantment to apply. A level of 0 marks the entry as skipped. */
    private record Entry(Enchantment enchantment,int level) {}
    private static Entry entry(Enchantment enchantment,int level){return new Entry(enchantment,level);}
    /**
     * Signature enchantments resolve through a {@code RegistryObject}, which is
     * empty until Forge registration runs. Unit tests and any load order that
     * has not registered them yet drop the entry instead of failing the roll,
     * so a reward is always produced.
     */
    private static Entry entry(net.minecraftforge.registries.RegistryObject<Enchantment> holder,int level) {
        return holder.isPresent() ? new Entry(holder.get(),level) : new Entry(Enchantments.UNBREAKING,0);
    }

    private static ItemStack trophy(ItemStack stack,String name,ChatFormatting color,
                                    List<String> lore,Entry... enchantments) {
        for(Entry entry:enchantments) if(entry.level()>0) stack.enchant(entry.enchantment(),entry.level());
        return decorate(stack,name,color,lore);
    }

    private static ItemStack named(ItemStack stack,String name,ChatFormatting color,String... lore) {
        return decorate(stack,name,color,List.of(lore));
    }

    /** Apply the display name and italic grey lore block used by every trophy. */
    private static ItemStack decorate(ItemStack stack,String name,ChatFormatting color,List<String> lore) {
        MutableComponent title=Component.literal(name).withStyle(style->style.withColor(color).withItalic(false));
        stack.setHoverName(title);
        if(lore.isEmpty())return stack;
        ListTag lines=new ListTag();
        for(String line:lore) lines.add(StringTag.valueOf(Component.Serializer.toJson(
                Component.literal(line).withStyle(style->style.withColor(ChatFormatting.GRAY).withItalic(true)))));
        CompoundTag display=stack.getOrCreateTagElement("display");
        display.put("Lore",lines);
        return stack;
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
        // A banked war key opens any chest for free; emeralds are only checked
        // when the player has no key to spend.
        boolean useKey=LootKeys.keys(player)>0;
        if(!useKey) {
            // Bank-first affordability check via PaymentSource.
            long combined = PaymentSource.available(player, price);
            if(combined<price){player.sendSystemMessage(Component.literal("You need "+price+" emeralds (bank + inventory)."));return null;}
        }
        // Require space for every possible outcome before rolling; full inventories
        // cannot be used to filter unwanted rewards or lose a paid reward.
        for(int roll:new int[]{0,50,80,95})if(!fits(inventory.items,reward(box,roll))) {
            player.sendSystemMessage(Component.literal("Make room in your inventory before opening a box."));return null;
        }
        int roll=player.getRandom().nextInt(100);
        ItemStack prize=reward(box,roll);
        if(useKey) { if(!LootKeys.spend(player)) return null; }
        else if(!PaymentSource.consume(player, price)) return null;
        // Capacity was checked on this same server thread; payment can only free space.
        inventory.add(prize.copy());inventory.setChanged();data.putLong("SiegeLootNext",now+OPEN_TICKS);
        // Keep chat free of reward details while the client plays its sealed reveal.
        // Delivery remains immediate, so closing the menu cannot lose a paid prize.
        player.sendSystemMessage(Component.literal("Opening "+NAMES[box]
                +(useKey?" with a war key":"")+"... Reward secured in your inventory."));
        return new Receipt(prize.copy(),tier(roll));
    }
}
