package com.devfarinsky.siegeoverhaul.compat;

import com.devfarinsky.siegeoverhaul.MinecraftTestSupport;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class WorkersProjectionViewTest extends MinecraftTestSupport {
    public static class Area {
        boolean enabled = true, permitted = true;
        public boolean showBox;
        public boolean getAlwaysShowProjection() { return enabled; }
        public boolean canPlayerSee(Player player) { return permitted; }
        public AABB getArea() { return new AABB(-32, 63, -16, 15, 70, 31); }
    }

    public static class UnavailableBoundsArea extends Area {
        @Override public AABB getArea() { throw new IllegalStateException("Native area unavailable"); }
    }

    public static class UnavailableSettingsArea extends Area {
        @Override public boolean getAlwaysShowProjection() {
            throw new IllegalStateException("Native setting unavailable");
        }
    }
    public static class BrokenArea extends Area {
        @Override public AABB getArea() { throw new IllegalStateException("Unavailable companion state"); }
    }

    @Test void usesTheWholeFixedPlanIncludingItsLastBlock() {
        var area = new Area();
        var player = mock(Player.class);
        var expected = new AABB(-32, 63, -16, 16, 70, 32);
        assertEquals(expected, WorkersProjectionView.visibleBounds(area, player));
        assertEquals(expected, WorkersProjectionView.visibleBounds(area, player));
    }

    @Test void disabledManualProjectionAndPrivateAreasKeepNativeCulling() {
        var area = new Area(); var player = mock(Player.class);
        area.enabled = false;
        assertNull(WorkersProjectionView.visibleBounds(area, player));
        area.enabled = true; area.permitted = false;
        assertNull(WorkersProjectionView.visibleBounds(area, player));
    }

    @Test void focusOnlyLargePlansUseTheNativeFocusGateWithoutChangingTheirSetting() {
        var area = new Area(); var player = mock(Player.class);
        area.enabled = false;
        assertNotNull(WorkersProjectionView.visibleBounds(area, player, area));
        assertNull(WorkersProjectionView.visibleBounds(area, player, new Area()));
        assertFalse(area.enabled);
        area.permitted = false;
        assertNull(WorkersProjectionView.visibleBounds(area, player, area));
    }

    @Test void nativeShowBoxEnablesBoundsWithoutAlwaysShowOrFocus() {
        var area = new Area(); var player = mock(Player.class);
        area.enabled = false; area.showBox = true;
        assertNotNull(WorkersProjectionView.visibleBounds(area, player, null));
        area.permitted = false;
        assertNull(WorkersProjectionView.visibleBounds(area, player, area));
    }

    @Test void nativeShortCircuitDoesNotReadAlwaysShowWhenBoxOrFocusAlreadyVisible() {
        var area = new UnavailableSettingsArea(); var player = mock(Player.class);
        assertNotNull(WorkersProjectionView.visibleBounds(area, player, area));
        area.showBox = true;
        assertNotNull(WorkersProjectionView.visibleBounds(area, player, null));
    }

    @Test void missingCompanionApiOrPlayerFailsClosed() {
        assertNull(WorkersProjectionView.visibleBounds(new Object(), mock(Player.class)));
        assertNull(WorkersProjectionView.visibleBounds(new BrokenArea(), mock(Player.class)));
        assertNull(WorkersProjectionView.visibleBounds(new Area(), null));
        assertNull(WorkersProjectionView.visibleBounds(null, mock(Player.class)));
    }

    @Test void throwingNativeGettersLeaveNormalRenderingUntouched() {
        var player = mock(Player.class);
        assertNull(WorkersProjectionView.visibleBounds(new UnavailableBoundsArea(), player));
        assertNull(WorkersProjectionView.visibleBounds(new UnavailableSettingsArea(), player));
    }
}
