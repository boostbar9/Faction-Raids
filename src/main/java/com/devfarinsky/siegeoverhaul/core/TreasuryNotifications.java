package com.devfarinsky.siegeoverhaul.core;

import com.devfarinsky.siegeoverhaul.RaidSavedData;
import com.devfarinsky.siegeoverhaul.SiegeOverhaul;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.server.ServerLifecycleHooks;
import java.util.ArrayList;
import java.util.List;

/** Server-side Treasury notices, delivered after purchase handlers finish their feedback. */
@Mod.EventBusSubscriber(modid = SiegeOverhaul.MOD_ID)
public final class TreasuryNotifications {
    private record Notice(String faction, long delta) {}
    private static final List<Notice> PENDING = new ArrayList<>();
    private TreasuryNotifications() {}

    static void changed(CompoundTag core, long delta) {
        if (delta == 0) return;
        var server = ServerLifecycleHooks.getCurrentServer();
        if (server == null || !server.isSameThread()) return;
        // Identity matters: a detached preview or copied save must never announce a transaction.
        RaidSavedData.get(server).siegeCores.forEach((key, live) -> {
            if (live == core) PENDING.add(new Notice(key, delta));
        });
    }

    public static Component message(long delta) {
        String amount = String.format(java.util.Locale.ROOT, "%+,d", delta);
        String unit = delta == 1 || delta == -1 ? " emerald " : " emeralds ";
        return Component.literal("[Treasury] ").withStyle(ChatFormatting.GOLD)
                .append(Component.literal(amount + unit + (delta > 0 ? "deposited" : "removed"))
                        .withStyle(delta > 0 ? ChatFormatting.GREEN : ChatFormatting.RED));
    }

    @SubscribeEvent
    public static void flush(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || PENDING.isEmpty()) return;
        var notices = List.copyOf(PENDING);
        PENDING.clear();
        for (var player : event.getServer().getPlayerList().getPlayers()) {
            String faction = SiegeCore.key(player);
            long gained = 0, spent = 0;
            for (var notice : notices) if (notice.faction().equals(faction)) {
                if (notice.delta() > 0) gained += notice.delta();
                else spent += notice.delta();
            }
            if (gained != 0 || spent != 0) {
                var summary = Component.empty();
                if (gained != 0) summary.append(message(gained));
                if (gained != 0 && spent != 0) summary.append(Component.literal(" | ").withStyle(ChatFormatting.GRAY));
                if (spent != 0) summary.append(message(spent));
                // v4.30.0: chat only. The action-bar copy stomped the raid
                // objective HUD every time a purchase or bounty landed.
                player.sendSystemMessage(summary);
            }
        }
    }

    @SubscribeEvent
    public static void stopped(ServerStoppedEvent event) { PENDING.clear(); }
}
