package com.devfarinsky.siegeoverhaul.client;

import com.devfarinsky.siegeoverhaul.SiegeOverhaul;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.VersionChecker;
import net.minecraftforge.fml.common.Mod;

/** Reads Forge's asynchronous check; never performs network I/O on the game thread. */
@Mod.EventBusSubscriber(modid = SiegeOverhaul.MOD_ID, value = Dist.CLIENT)
public final class UpdateChatNotifier {
    private static final UpdateNoticeState STATE = new UpdateNoticeState();
    private static final String DOWNLOAD =
            "https://www.curseforge.com/minecraft/mc-mods/siege-overhaul/files";

    private UpdateChatNotifier() {}

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (!STATE.shouldPoll(minecraft.player != null && minecraft.level != null)) return;
        ModList.get().getModContainerById(SiegeOverhaul.MOD_ID).ifPresent(container -> {
            var result = VersionChecker.getResult(container.getModInfo());
            if ((result.status() != VersionChecker.Status.OUTDATED
                    && result.status() != VersionChecker.Status.BETA_OUTDATED)
                    || result.target() == null) return;

            Component link = Component.translatable("siegeoverhaul.update.download")
                    .withStyle(style -> style.withColor(ChatFormatting.AQUA).withUnderlined(true)
                            .withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, DOWNLOAD))
                            .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                                    Component.translatable("siegeoverhaul.update.hover"))));
            minecraft.gui.getChat().addMessage(Component.translatable("siegeoverhaul.update.available",
                    result.target().toString(), container.getModInfo().getVersion().toString())
                    .withStyle(ChatFormatting.GOLD).append(" ").append(link));
            minecraft.gui.getChat().addMessage(Component.translatable("siegeoverhaul.update.multiplayer")
                    .withStyle(ChatFormatting.GRAY));
            STATE.markNotified();
        });
    }
}
