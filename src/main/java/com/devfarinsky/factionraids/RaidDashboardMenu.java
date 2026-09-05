package com.devfarinsky.factionraids;

import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;

/**
 * Server-owned command dashboard rendered through Minecraft's vanilla six-row
 * chest screen. This keeps the interface lightweight and usable by clients
 * without a custom screen or networking library.
 */
public final class RaidDashboardMenu extends ChestMenu {
    private static final int DASHBOARD_SIZE = 54;
    private static final int REFRESH_HOME_SLOT = 38;
    private static final int START_RAID_SLOT = 40;
    private static final int HELP_SLOT = 42;
    private static final int CLOSE_SLOT = 49;

    private final Container dashboard;
    private final ServerPlayer serverPlayer;

    public RaidDashboardMenu(int containerId, Inventory inventory, ServerPlayer player) {
        this(containerId, inventory, player, new SimpleContainer(DASHBOARD_SIZE));
    }

    private RaidDashboardMenu(int containerId, Inventory inventory, ServerPlayer player,
                              Container dashboard) {
        super(MenuType.GENERIC_9x6, containerId, inventory, dashboard, 6);
        this.dashboard = dashboard;
        this.serverPlayer = player;
        refresh();
    }

    private void refresh() {
        RaidEvents.DashboardSnapshot snapshot = RaidEvents.dashboardSnapshot(serverPlayer);
        ItemStack filler = named(Items.BLACK_STAINED_GLASS_PANE, " ", ChatFormatting.BLACK);
        for (int slot = 0; slot < DASHBOARD_SIZE; slot++) dashboard.setItem(slot, filler.copy());

        for (int slot : new int[]{0, 1, 2, 3, 4, 5, 6, 7, 8, 45, 46, 47, 48, 50, 51, 52, 53}) {
            dashboard.setItem(slot, named(Items.RED_STAINED_GLASS_PANE, " ", ChatFormatting.DARK_RED));
        }

        dashboard.setItem(4, icon(Items.BLACK_BANNER, snapshot.faction(), ChatFormatting.GOLD,
                List.of("Faction command table", snapshot.active() ? "Siege status: ACTIVE" : "Siege status: secure")));
        dashboard.setItem(20, icon(Items.RECOVERY_COMPASS, "Stronghold", ChatFormatting.AQUA,
                List.of(snapshot.stronghold(), snapshot.registered() ? "Target locked to your faction" :
                        "Sleep at the base to register it")));
        dashboard.setItem(22, icon(Items.CROSSBOW, snapshot.active() ? "Active Siege" : "Siege Readiness",
                snapshot.active() ? ChatFormatting.RED : ChatFormatting.GREEN,
                snapshot.active() ? List.of("Wave " + snapshot.wave() + " / " + snapshot.totalWaves(),
                        snapshot.deployed() + " deployed • " + snapshot.reinforcing() + " reinforcing",
                        snapshot.defeated() + " enemies defeated",
                        snapshot.breached() ? "Occupation pressure: " + snapshot.occupationPercent() + "%" :
                                "Outer breach pressure: " + snapshot.breachPercent() + "%") :
                        List.of("Next automatic siege: " + snapshot.cooldown(), "The stronghold is currently secure")));
        dashboard.setItem(24, icon(Items.SHIELD, "Defending Army", ChatFormatting.BLUE,
                List.of(snapshot.recruits() + " allied Recruits detected nearby",
                        snapshot.workers() + " protected Villager Workers nearby",
                        "Workers integration: " + OptionalCompatBridge.workersStatus(),
                        "Recruits strengthen and defend the siege target")));
        dashboard.setItem(26, icon(Items.OAK_BOAT, "War Assets", ChatFormatting.DARK_AQUA,
                List.of(snapshot.ships() + " faction Small Ships nearby",
                        snapshot.siegeWeapons() + " faction Siege Weapons nearby",
                        "Integrations: ships " + OptionalCompatBridge.smallShipsStatus() +
                                " • siege " + OptionalCompatBridge.siegeWeaponsStatus(),
                        "+" + snapshot.assetScalingEnemies() + " equipment-scaled invaders per wave",
                        "Mount equipment once to register or capture it")));
        dashboard.setItem(31, icon(Items.EMERALD_BLOCK, "Victory Treasury", ChatFormatting.GREEN,
                List.of(snapshot.emeraldReward() + " guaranteed emeralds per online member",
                        RaidConfig.VICTORY_EXPERIENCE.get() + " experience plus campaign loot",
                        snapshot.rewardEligible() ? "This siege is reward eligible" :
                                "Practice sieges do not grant rewards")));

        dashboard.setItem(REFRESH_HOME_SLOT, icon(Items.RED_BED, "Refresh Stronghold", ChatFormatting.AQUA,
                List.of("Update the target from your current respawn point", "Click to refresh")));
        dashboard.setItem(START_RAID_SLOT, icon(Items.WRITABLE_BOOK,
                "Use /factionraids start", ChatFormatting.GOLD,
                List.of("The in-game Test Siege button was removed in v2.11.0",
                        "Run /factionraids start in chat to trigger a siege now",
                        "Available to every player at their own stronghold")));
        dashboard.setItem(HELP_SLOT, icon(Items.WRITABLE_BOOK, "Command Guide", ChatFormatting.YELLOW,
                List.of("Print the essential command list in chat", "Click for help")));
        dashboard.setItem(CLOSE_SLOT, icon(Items.BARRIER, "Close Command Table", ChatFormatting.RED,
                List.of("Return to the battlefield")));

        broadcastChanges();
    }

    @Override
    public void clicked(int slotId, int button, ClickType clickType, Player player) {
        if (slotId >= 0 && slotId < DASHBOARD_SIZE) {
            if (slotId == REFRESH_HOME_SLOT) RaidEvents.dashboardRefreshHome(serverPlayer);
            // START_RAID_SLOT is intentionally no-op in v2.11.0 — the icon is a
            // hint pointing players at /factionraids start. See slot init above.
            else if (slotId == HELP_SLOT) RaidEvents.dashboardHelp(serverPlayer);
            else if (slotId == CLOSE_SLOT) {
                serverPlayer.closeContainer();
                return;
            }
            refresh();
            return;
        }
        super.clicked(slotId, button, clickType, player);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    private static ItemStack named(Item item, String name, ChatFormatting color) {
        ItemStack stack = new ItemStack(item);
        stack.setHoverName(Component.literal(name).withStyle(color));
        return stack;
    }

    private static ItemStack icon(Item item, String name, ChatFormatting color, List<String> loreLines) {
        ItemStack stack = named(item, name, color);
        CompoundTag display = stack.getOrCreateTagElement("display");
        ListTag lore = new ListTag();
        for (String line : loreLines) {
            Component component = Component.literal(line).withStyle(ChatFormatting.GRAY);
            lore.add(StringTag.valueOf(Component.Serializer.toJson(component)));
        }
        display.put("Lore", lore);
        return stack;
    }
}
