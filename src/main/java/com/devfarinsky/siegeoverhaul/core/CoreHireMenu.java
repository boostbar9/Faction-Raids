package com.devfarinsky.siegeoverhaul.core;

import com.devfarinsky.siegeoverhaul.RaidSavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.*;

/** Read-only offer display; server owns stock, prices, permissions and purchases. */
public final class CoreHireMenu extends AbstractContainerMenu {
    private final ServerPlayer owner;
    private final BlockPos pos;
    private final SimpleContainer display = new SimpleContainer(5);
    private final ContainerData data = new SimpleContainerData(15);
    private long shownAt = Long.MIN_VALUE;
    public CoreHireMenu(int id, Inventory inventory) { this(id, inventory, null); }
    public CoreHireMenu(int id, Inventory inventory, BlockPos pos) {
        super(CoreMenus.HIRING.get(), id);
        this.owner = inventory.player instanceof ServerPlayer sp ? sp : null;
        this.pos = pos == null ? null : pos.immutable();
        for (int i = 0; i < 5; i++) addSlot(new Slot(display, i, -1000, -1000) {
            @Override public boolean mayPlace(ItemStack stack) { return false; }
            @Override public boolean mayPickup(Player player) { return false; }
        });
        for (int i = 0; i < 4; i++) { data.set(i, -1); data.set(i + 4, -1); }
        addDataSlots(data);
        if (owner != null) refresh();
    }
    public int role(int slot) { return data.get(slot); }
    public int cost(int slot) { return data.get(slot + 4); }
    public boolean sold(int slot) { return (data.get(8) & (1 << slot)) != 0; }
    public int emeralds() { return data.get(14); }
    public int seconds() { return data.get(9); }
    public long rotation() {
        long value = 0;
        for (int i = 0; i < 4; i++) value |= (long) (data.get(10 + i) & 0xffff) << (i * 16);
        return value;
    }
    private void refresh() {
        if (owner == null || !stillValid(owner)) return;
        RaidSavedData saved = RaidSavedData.get(owner.server);
        CompoundTag core = saved.siegeCores.get(SiegeCore.key(owner));
        long now = owner.server.overworld().getGameTime();
        if (CoreOffers.refresh(core, now, owner.serverLevel().random)) saved.setDirty();
        long rotation = core.getLong("RefreshAt");
        int[] offers = core.getIntArray("Offers");
        for (int i = 0; i < 4; i++) {
            int role=i==3?core.getInt("HeroRole"):offers[i];
            data.set(i, role);
            display.setItem(i, new ItemStack(CoreHiring.icon(role)));
            try { data.set(i + 4, Math.min(32767, Math.max(0, CoreHiring.cost(role)))); }
            catch (ReflectiveOperationException | RuntimeException ex) { data.set(i + 4, -1); }
        }
        try { display.setItem(4, new ItemStack(CoreHiring.currency())); }
        catch (ReflectiveOperationException | RuntimeException ex) { display.setItem(4, ItemStack.EMPTY); }
        data.set(8, core.getInt("Sold"));
        data.set(9, (int) Math.min(900, Math.max(0, (rotation - now + 19) / 20)));
        for (int i = 0; i < 4; i++) data.set(10 + i, (int) ((rotation >>> (i * 16)) & 0xffff));
        data.set(14,owner.getInventory().items.stream().filter(stack->stack.is(Items.EMERALD)).mapToInt(ItemStack::getCount).sum());
        shownAt = now;
    }
    @Override public boolean stillValid(Player player) {
        return owner == null ? pos == null : player == owner && SiegeCore.canUse(owner, pos);
    }
    @Override public ItemStack quickMoveStack(Player player, int slot) { return ItemStack.EMPTY; }
    @Override public void clicked(int slot, int button, ClickType click, Player player) { }
    public void purchase(ServerPlayer player, int index, long expectedRotation) {
        if (owner == null || !stillValid(player)) return;
        refresh();
        RaidSavedData saved = RaidSavedData.get(owner.server);
        CompoundTag core = saved.siegeCores.get(SiegeCore.key(owner));
        if (CoreOffers.canPurchase(core, index, expectedRotation)
                && CoreHiring.hire(owner, pos, index==3?core.getInt("HeroRole"):core.getIntArray("Offers")[index])) {
            core.putInt("Sold", core.getInt("Sold") | (1 << index));
            saved.setDirty();
        }
        refresh();
        super.broadcastChanges();
    }
    @Override public boolean clickMenuButton(Player player,int button) {
        if(owner==null || player!=owner || !stillValid(player) || button<20 || button>22)return false;
        boolean bought=CoreLoot.purchase(owner,button-20);
        if(bought){owner.inventoryMenu.broadcastChanges();refresh();super.broadcastChanges();}
        return bought;
    }
    @Override public void broadcastChanges() {
        if (owner != null && owner.server.overworld().getGameTime() - shownAt >= 20) refresh();
        super.broadcastChanges();
    }
}
