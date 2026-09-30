package com.devfarinsky.siegeoverhaul.client;

import com.devfarinsky.siegeoverhaul.SiegeOverhaul;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;

/** Workers 2.0.3 passes an unnamed BuildArea's null custom name to Screen. */
@Mod.EventBusSubscriber(modid = SiegeOverhaul.MOD_ID, value = Dist.CLIENT)
public final class WorkersScreenTitles {
    private WorkersScreenTitles() {}

    @SubscribeEvent
    public static void opening(ScreenEvent.Opening event) {
        repair(event.getNewScreen());
    }

    static boolean repair(Screen screen) {
        if (screen == null || screen.getTitle() != null
                || !screen.getClass().getName().equals("com.talhanation.workers.client.gui.BuildAreaScreen")) return false;
        // Forge remaps this stable SRG field name in both development and production.
        try {
            java.lang.reflect.Field title;
            try {
                title = ObfuscationReflectionHelper.findField(Screen.class, "f_96539_");
            } catch (RuntimeException unavailableMappingService) {
                // Unit tests run with named Minecraft classes outside Forge's launcher.
                title = Screen.class.getDeclaredField("title");
                title.setAccessible(true);
            }
            title.set(screen, Component.literal("Construction Area"));
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("Cannot repair Workers construction screen title", failure);
        }
        return true;
    }
}
