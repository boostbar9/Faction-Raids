package com.devfarinsky.siegeoverhaul.core;

import com.devfarinsky.siegeoverhaul.RaidSavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.*;

/** Vanilla nine-slot client screen; server handles every purchase and never exposes removable stock. */
public final class CoreHireMenu extends ChestMenu {
    private final ServerPlayer owner;
    private final BlockPos pos;
    private final SimpleContainer display;
    private long shownAt = Long.MIN_VALUE;
    private long displayedRotation;
    public CoreHireMenu(int id, Inventory inventory, BlockPos pos) {
        this(id, inventory, pos, new SimpleContainer(9));
    }
    private CoreHireMenu(int id, Inventory inventory, BlockPos pos, SimpleContainer display) {
        super(MenuType.GENERIC_9x1, id, inventory, display, 1);
        this.owner = (ServerPlayer) inventory.player;
        this.pos = pos.immutable();
        this.display = display;
        refresh();
    }
    private void refresh() {
        if (!SiegeCore.canUse(owner, pos)) return;
        RaidSavedData data = RaidSavedData.get(owner.server);
        CompoundTag core = data.siegeCores.get(SiegeCore.key(owner));
        long now = owner.server.overworld().getGameTime();
        if (CoreOffers.refresh(core, now, owner.serverLevel().random)) data.setDirty();
        displayedRotation = core.getLong("RefreshAt");
        display.clearContent();
        int[] offers = core.getIntArray("Offers");
        for (int i = 0; i < 3; i++) {
            int role = offers[i];
            boolean sold = (core.getInt("Sold") & (1 << i)) != 0;
            ItemStack icon = new ItemStack(sold ? Items.BARRIER : role == 1 ? Items.SHIELD : role == 2 ? Items.BOW : Items.IRON_SWORD);
            try {
                icon.setHoverName(Component.literal(CoreHiring.NAMES[role] + (sold ? " — Hired" : " — " + CoreHiring.cost(role) + " ")).append(sold ? Component.empty() : CoreHiring.currency().getDescription()));
                ListTag lore = new ListTag();
                lore.add(StringTag.valueOf(Component.Serializer.toJson(Component.literal(sold ? "Returns next rotation" : "Click to hire • faction stock"))));
                icon.getOrCreateTagElement("display").put("Lore", lore);
            } catch (ReflectiveOperationException | RuntimeException ex) { icon.setHoverName(Component.literal("Hiring unavailable")); }
            display.setItem(1 + i * 3, icon);
        }
        long seconds = Math.max(0, (displayedRotation - now + 19) / 20);
        ItemStack clock = new ItemStack(Items.CLOCK);
        clock.setHoverName(Component.literal("New offers in " + seconds / 60 + ":" + String.format(java.util.Locale.ROOT, "%02d", seconds % 60)));
        display.setItem(8, clock);
        shownAt = now;
    }
    @Override public boolean stillValid(Player player) { return player == owner && SiegeCore.canUse(owner, pos); }
    @Override public ItemStack quickMoveStack(Player player, int slot) { return ItemStack.EMPTY; }
    @Override public void clicked(int slot, int button, ClickType click, Player player) {
        if (!stillValid(player) || click != ClickType.PICKUP || button != 0 || slot < 1 || slot > 7 || (slot - 1) % 3 != 0) return;
        RaidSavedData data = RaidSavedData.get(owner.server);
        CompoundTag core = data.siegeCores.get(SiegeCore.key(owner));
        long oldRotation = displayedRotation;
        refresh();
        if (oldRotation != displayedRotation) { super.broadcastChanges(); return; }
        int index = (slot - 1) / 3;
        if ((core.getInt("Sold") & (1 << index)) == 0 && CoreHiring.hire(owner, pos, core.getIntArray("Offers")[index])) {
            core.putInt("Sold", core.getInt("Sold") | (1 << index));
            data.setDirty();
        }
        refresh();
        super.broadcastChanges();
    }
    @Override public void broadcastChanges() {
        if (owner != null && owner.server.overworld().getGameTime() - shownAt >= 20) refresh();
        super.broadcastChanges();
    }
}
