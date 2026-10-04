package com.devfarinsky.siegeoverhaul.client;

import com.devfarinsky.siegeoverhaul.MinecraftTestSupport;
import com.devfarinsky.siegeoverhaul.RaidEvents;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.util.FormattedCharSequence;
import org.junit.jupiter.api.Test;
import org.lwjgl.glfw.GLFW;

import java.util.Collections;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Input/state regressions, not a native visual playtest. */
class SiegeCommandInteractionTest extends MinecraftTestSupport {
    private static Object get(Object screen,String name) throws Exception {
        var field=SiegeCommandScreen.class.getDeclaredField(name);field.setAccessible(true);return field.get(screen);
    }
    private static void set(Object screen,String name,Object value) throws Exception {
        var field=SiegeCommandScreen.class.getDeclaredField(name);field.setAccessible(true);field.set(screen,value);
    }
    private static SiegeCommandScreen create() throws Exception {
        var snapshot=mock(RaidEvents.DashboardSnapshot.class);
        when(snapshot.warJournal()).thenReturn(List.of());
        var screen=new SiegeCommandScreen(snapshot);
        screen.width=320;screen.height=240;
        var font=mock(Font.class);
        when(font.split(any(FormattedText.class),anyInt())).thenReturn(Collections.nCopies(20,FormattedCharSequence.EMPTY));
        var field=Screen.class.getDeclaredField("font");field.setAccessible(true);field.set(screen,font);
        screen.init();return screen;
    }
    @Test void onlyTheThreeReachablePagesAreExposed() {
        assertArrayEquals(new String[]{"Core","How to play","Journal"},
                java.util.Arrays.stream(SiegeCommandScreen.Tab.values()).map(tab->tab.label).toArray(String[]::new));
    }
    @Test void periodicSnapshotKeepsTheFocusedWidgetAndReaderPosition() throws Exception {
        var screen=create();
        set(screen,"activeTab",SiegeCommandScreen.Tab.DEFENSE);screen.init();
        var guide=(AbstractWidget)get(screen,"guide");screen.setFocused(guide);
        set(screen,"guideIndex",3);set(screen,"guideScroll",80);
        var snapshot=mock(RaidEvents.DashboardSnapshot.class);when(snapshot.warJournal()).thenReturn(List.of());
        screen.updateSnapshot(snapshot);
        assertSame(guide,get(screen,"guide"));assertSame(guide,screen.getFocused());
        assertEquals(3,get(screen,"guideIndex"));assertEquals(80,get(screen,"guideScroll"));
    }
    @Test void guideBodyCanReachItsEndAndOnlyScrollsWithinItsViewport() throws Exception {
        var screen=create();set(screen,"activeTab",SiegeCommandScreen.Tab.DEFENSE);screen.init();
        var guide=(AbstractWidget)get(screen,"guide");guide.setFocused(true);
        assertTrue(guide.keyPressed(GLFW.GLFW_KEY_END,0,0));
        int end=(Integer)get(screen,"guideScroll");assertTrue(end>0);
        assertTrue(guide.keyPressed(GLFW.GLFW_KEY_DOWN,0,0));assertEquals(end,get(screen,"guideScroll"));
        assertFalse(screen.mouseScrolled(0,0,-1));assertEquals(end,get(screen,"guideScroll"));
        assertTrue(guide.keyPressed(GLFW.GLFW_KEY_HOME,0,0));assertEquals(0,get(screen,"guideScroll"));
        assertTrue(screen.mouseScrolled(guide.getX()+2,guide.getY()+2,-1));assertTrue((Integer)get(screen,"guideScroll")>0);
    }
    @Test void reinitializingCannotLeaveFocusOnARemovedOrDisabledControl() throws Exception {
        var screen=create();set(screen,"activeTab",SiegeCommandScreen.Tab.DEFENSE);screen.init();
        var old=(CoreButton)get(screen,"previous");
        screen.setFocused(old);
        screen.init();
        assertNotSame(old,screen.getFocused());assertFalse(old.isFocused());
        assertTrue(screen.children().contains(screen.getFocused()));
        assertTrue(((AbstractWidget)screen.getFocused()).active);
    }
    @Test void journalScrollStopsAtTheLastPageAndSnapshotShrinkClampsIt() throws Exception {
        var screen=create();
        var snapshot=mock(RaidEvents.DashboardSnapshot.class);
        when(snapshot.warJournal()).thenReturn(Collections.nCopies(10,mock(RaidEvents.JournalRow.class)));
        screen.updateSnapshot(snapshot);set(screen,"activeTab",SiegeCommandScreen.Tab.JOURNAL);screen.init();
        var body=((SiegeCommandLayout)get(screen,"layout")).body();
        for(int i=0;i<20;i++) screen.mouseScrolled(body.x()+2,body.y()+2,-1);
        var layout=(SiegeCommandLayout)get(screen,"layout");assertEquals(layout.journalPages(10)-1,get(screen,"journalPage"));
        assertFalse(((CoreButton)get(screen,"next")).active);
        when(snapshot.warJournal()).thenReturn(List.of());screen.updateSnapshot(snapshot);
        assertEquals(0,get(screen,"journalPage"));assertFalse(((CoreButton)get(screen,"previous")).active);
    }
    @Test void actualTabClickCannotRestoreFocusToTheRemovedClickedButton() throws Exception {
        var screen = create();
        var old = screen.children().stream().filter(c -> c instanceof CoreButton button
                && button.getMessage().getString().equals("How to play")).map(c -> (CoreButton)c).findFirst().orElseThrow();
        var client = mock(net.minecraft.client.Minecraft.class);
        when(client.getSoundManager()).thenReturn(mock(net.minecraft.client.sounds.SoundManager.class));
        try (var singleton = mockStatic(net.minecraft.client.Minecraft.class)) {
            singleton.when(net.minecraft.client.Minecraft::getInstance).thenReturn(client);
            assertTrue(screen.mouseClicked(old.getX() + 2, old.getY() + 2, 0));
        }
        assertFalse(screen.children().contains(old));
        assertNotSame(old, screen.getFocused());
        assertTrue(screen.children().contains(screen.getFocused()));
        assertTrue(((AbstractWidget) screen.getFocused()).active);
    }

    @Test void lastTipAndShrunkenJournalMoveFocusOffDisabledPagers() throws Exception {
        var screen = create();
        set(screen, "activeTab", SiegeCommandScreen.Tab.DEFENSE);
        set(screen, "guideIndex", com.devfarinsky.siegeoverhaul.client.codex.DefensePlaybook.TIPS.size() - 2);
        screen.init();
        var next = (CoreButton)get(screen, "next"); screen.setFocused(next); next.onPress();
        assertFalse(next.active); assertNotSame(next, screen.getFocused());
        assertTrue(((AbstractWidget)screen.getFocused()).active);
        var snapshot = mock(RaidEvents.DashboardSnapshot.class);
        when(snapshot.warJournal()).thenReturn(Collections.nCopies(10, mock(RaidEvents.JournalRow.class)));
        screen.updateSnapshot(snapshot); set(screen, "activeTab", SiegeCommandScreen.Tab.JOURNAL);
        set(screen, "journalPage", 1); screen.init();
        var previous = (CoreButton)get(screen, "previous"); screen.setFocused(previous);
        when(snapshot.warJournal()).thenReturn(List.of()); screen.updateSnapshot(snapshot);
        assertFalse(previous.active); assertNotSame(previous, screen.getFocused());
        assertTrue(screen.children().contains(screen.getFocused()));
        assertTrue(((AbstractWidget)screen.getFocused()).active);
    }

}
