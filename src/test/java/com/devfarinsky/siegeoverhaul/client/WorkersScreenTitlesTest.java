package com.devfarinsky.siegeoverhaul.client;

import com.devfarinsky.siegeoverhaul.MinecraftTestSupport;
import com.talhanation.workers.client.gui.BuildAreaScreen;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.ScreenNarrationCollector;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class WorkersScreenTitlesTest extends MinecraftTestSupport {
    @Test void unnamedBuildAreaGetsANarratableTitle() {
        var screen = new BuildAreaScreen(null);
        assertTrue(WorkersScreenTitles.repair(screen));
        assertEquals("Construction Area", screen.getTitle().getString());
        var narration = new ScreenNarrationCollector();
        assertDoesNotThrow(() -> narration.update(output -> output.add(NarratedElementType.TITLE, screen.getTitle())));
        assertFalse(WorkersScreenTitles.repair(screen));
    }
    @Test void namedAreasAndUnrelatedScreensArePreserved() {
        Component title = Component.literal("North Wall");
        var named = new BuildAreaScreen(title);
        assertFalse(WorkersScreenTitles.repair(named));
        assertSame(title, named.getTitle());
        Screen other = new Screen(null) {};
        assertFalse(WorkersScreenTitles.repair(other));
        assertNull(other.getTitle());
        assertFalse(WorkersScreenTitles.repair(null));
    }
}
