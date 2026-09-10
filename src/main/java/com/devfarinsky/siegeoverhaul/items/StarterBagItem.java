package com.devfarinsky.siegeoverhaul.items;

import com.devfarinsky.siegeoverhaul.core.CoreHiring;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.ForgeConfigSpec;
import java.util.*;

/** One-use starter parcels. Unclaimed contents stay in item NBT when inventory is full. */
public final class StarterBagItem extends Item {
    private final boolean settlement;
    public StarterBagItem(boolean settlement) { super(new Properties().stacksTo(1).rarity(Rarity.UNCOMMON));this.settlement=settlement; }
    private static int configured(String field) throws ReflectiveOperationException {
        return (Integer)((ForgeConfigSpec.ConfigValue<?>)Class.forName("com.talhanation.recruits.config.RecruitsServerConfig").getField(field).get(null)).get();
    }
    public static int budget(int faction,int claim,int shield,int archer) {
        long needed=Math.max(0,faction)+(long)Math.max(0,claim)+2L*Math.max(0,shield)+2L*Math.max(0,archer)+32;
        return (int)Math.min(16384,Math.max(128,((needed+63)/64)*64));
    }
    List<ItemStack> contents() throws ReflectiveOperationException {
        List<ItemStack> out=new ArrayList<>();
        if(settlement) {
            // v4.13.0: guidebook removed from the starter bag; the Codex content
            // is baked into the Command Center HUD's Intel tab, so no book is
            // needed. Existing books still work but no new copies are handed out.
            add(out,ModItems.SIEGE_CORE.get(),1);add(out,Items.LOOM,1);
            add(out,Items.WHITE_BANNER,2);add(out,Items.BLUE_DYE,16);add(out,Items.WHITE_DYE,16);add(out,Items.BLACK_DYE,16);
            add(out,CoreHiring.currency(),budget(configured("FactionCreationCost"),configured("ClaimingCost"),CoreHiring.cost(1),CoreHiring.cost(2)));
        } else out.addAll(survivalContents());
        return out;
    }
    static List<ItemStack> survivalContents() {
        List<ItemStack> out=new ArrayList<>();
            for(Item item:List.of(Items.IRON_SWORD,Items.IRON_PICKAXE,Items.IRON_AXE,Items.IRON_SHOVEL,Items.SHIELD,
                    Items.IRON_HELMET,Items.IRON_CHESTPLATE,Items.IRON_LEGGINGS,Items.IRON_BOOTS,Items.WATER_BUCKET,Items.CRAFTING_TABLE,Items.FURNACE,Items.WHITE_BED))add(out,item,1);
            add(out,Items.COOKED_BEEF,16);add(out,Items.BREAD,16);add(out,Items.TORCH,32);
            add(out,Items.STONE_BRICKS,256);add(out,Items.STONE_BRICK_STAIRS,32);add(out,Items.STONE_BRICK_SLAB,32);
            add(out,Items.OAK_PLANKS,64);add(out,Items.GLASS_PANE,16);add(out,Items.OAK_DOOR,2);add(out,Items.CHEST,2);add(out,Items.COAL,16);
        return out;
    }
    private static void add(List<ItemStack> list,Item item,int count) {
        while(count>0) { int n=Math.min(count,item.getMaxStackSize());list.add(new ItemStack(item,n));count-=n; }
    }
    public static ListTag save(List<ItemStack> items) {
        ListTag tag=new ListTag();for(ItemStack item:items)if(!item.isEmpty())tag.add(item.save(new CompoundTag()));return tag;
    }
    /** Bounded insertion has identical overflow behavior in Survival and Creative. */
    public static void insert(net.minecraft.world.entity.player.Inventory inventory,ItemStack item) {
        for(int i=0;i<36 && !item.isEmpty();i++) {
            ItemStack existing=inventory.getItem(i);
            if(!existing.isEmpty() && ItemStack.isSameItemSameTags(existing,item)) {
                int n=Math.min(item.getCount(),Math.max(0,existing.getMaxStackSize()-existing.getCount()));
                existing.grow(n);item.shrink(n);
            }
        }
        for(int i=0;i<36 && !item.isEmpty();i++)if(inventory.getItem(i).isEmpty()) {
            int n=Math.min(item.getCount(),item.getMaxStackSize());inventory.setItem(i,item.split(n));
        }
    }
    @Override public InteractionResultHolder<ItemStack> use(Level level,Player player,InteractionHand hand) {
        ItemStack bag=player.getItemInHand(hand);
        if(level.isClientSide)return InteractionResultHolder.sidedSuccess(bag,true);
        var tag=bag.getOrCreateTag();
        if(!tag.contains("Supplies",Tag.TAG_LIST)) {
            try { tag.put("Supplies",save(contents())); }
            catch(ReflectiveOperationException | RuntimeException ex) {
                player.displayClientMessage(Component.literal("Starter supplies unavailable; try again after server setup."),true);
                return InteractionResultHolder.fail(bag);
            }
        }
        List<ItemStack> remaining=new ArrayList<>();int delivered=0;
        for(Tag entry:tag.getList("Supplies",Tag.TAG_COMPOUND)) {
            ItemStack item=ItemStack.of((CompoundTag)entry);int before=item.getCount();
            insert(player.getInventory(),item);delivered+=before-item.getCount();
            if(!item.isEmpty())remaining.add(item);
        }
        tag.put("Supplies",save(remaining));
        if(remaining.isEmpty())bag.shrink(1);
        player.getInventory().setChanged();player.containerMenu.broadcastChanges();
        player.displayClientMessage(Component.literal(remaining.isEmpty()?"Starter bag unpacked.":delivered==0?"Make inventory space, then open this bag again.":"Supplies unpacked. Remaining items are safe in this bag."),true);
        return InteractionResultHolder.sidedSuccess(bag,false);
    }
    @Override public void appendHoverText(ItemStack stack,Level level,List<Component> tooltip,TooltipFlag flag) {
        tooltip.add(Component.literal(settlement?"Faction setup, core and hiring funds":"Iron gear, food and a small stone-brick shelter"));
        tooltip.add(Component.literal("Right-click to unpack. Overflow stays inside."));
        if(stack.hasTag() && stack.getTag().contains("Supplies"))tooltip.add(Component.literal(stack.getTag().getList("Supplies",Tag.TAG_COMPOUND).size()+" stacks remaining"));
    }
    public static void giveOnce(ServerPlayer player) {
        CompoundTag flags=player.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
        if(flags.getBoolean("SiegeStarterBagsGiven"))return;
        for(Item item:List.of(ModItems.SETTLEMENT_BAG.get(),ModItems.SURVIVAL_BAG.get())) {
            ItemStack stack=new ItemStack(item);if(!player.getInventory().add(stack))player.drop(stack,false);
        }
        flags.putBoolean("SiegeStarterBagsGiven",true);flags.putBoolean("SiegeCoreGiven",true);flags.putBoolean("FactionRaidsGuidebookGiven",true);
        player.getPersistentData().put(Player.PERSISTED_NBT_TAG,flags);
        player.sendSystemMessage(Component.literal("Your two starter bags contain faction supplies and survival gear. Open them when ready; claim land before placing your Siege Core."));
    }
}
