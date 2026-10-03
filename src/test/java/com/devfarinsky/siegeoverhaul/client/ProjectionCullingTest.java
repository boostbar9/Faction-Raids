package com.devfarinsky.siegeoverhaul.client;

import com.devfarinsky.siegeoverhaul.MinecraftTestSupport;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProjectionCullingTest extends MinecraftTestSupport {
    @Test @SuppressWarnings("unchecked")
    void nativeRendererBypassesOnlyTheMarkerFrustumDuringTheProjectionPass() {
        EntityRenderer<Entity> renderer = mock(EntityRenderer.class, CALLS_REAL_METHODS);
        var frustum = mock(Frustum.class);
        var area = mock(Entity.class);
        when(area.shouldRender(0, 64, 0)).thenReturn(true);
        when(area.getBoundingBoxForCulling()).thenReturn(new AABB(30, 63, 30, 31, 65, 31));
        when(frustum.isVisible(any(AABB.class))).thenReturn(false);
        assertFalse(renderer.shouldRender(area, frustum, 0, 64, 0));

        var state = new ProjectionCulling();
        state.update(area, true);
        assertTrue(renderer.shouldRender(area, frustum, 0, 64, 0));
        verify(frustum, times(1)).isVisible(any(AABB.class));

        state.clear();
        assertFalse(renderer.shouldRender(area, frustum, 0, 64, 0));
        verify(frustum, times(2)).isVisible(any(AABB.class));
    }

    @Test @SuppressWarnings("unchecked")
    void nativeDistanceLimitStillAppliesToAnUnculledProjection() {
        EntityRenderer<Entity> renderer = mock(EntityRenderer.class, CALLS_REAL_METHODS);
        var frustum = mock(Frustum.class);
        var area = mock(Entity.class);
        when(area.shouldRender(1000, 64, 1000)).thenReturn(false);
        var state = new ProjectionCulling();
        state.update(area, true);
        assertFalse(renderer.shouldRender(area, frustum, 1000, 64, 1000));
        verifyNoInteractions(frustum);
        state.clear();
        assertFalse(area.noCulling);
    }

    @Test void inViewProjectionSurvivesRepeatedFramesThenRestoresMarkerCulling() {
        var state = new ProjectionCulling(); var area = mock(Entity.class);
        state.update(area, true);
        assertTrue(area.noCulling);
        state.update(area, true);
        state.update(area, false);
        assertFalse(area.noCulling);
        verifyNoInteractions(area);
    }

    @Test void originalUnculledEntitiesStayUnculled() {
        var state = new ProjectionCulling(); var area = mock(Entity.class);
        area.noCulling = true;
        state.update(area, true); state.update(area, false);
        assertTrue(area.noCulling);
    }

    @Test void frameEndOrWorldChangeReleaseAllTemporaryOverrides() {
        var state = new ProjectionCulling(); var removed = mock(Entity.class); var retained = mock(Entity.class);
        state.update(removed, true); state.update(retained, true);
        state.clear();
        assertFalse(removed.noCulling);
        assertFalse(retained.noCulling);
    }

    @Test void testsTheWholePlanInsteadOfTheOffscreenShovelMarker() {
        var state = new ProjectionCulling(); var area = mock(Entity.class);
        var plan = new AABB(-32, 63, -16, 16, 70, 32);
        var cameraRegion = new AABB(-32, 63, 24, -24, 70, 32);
        state.update(area, plan, cameraRegion::intersects);
        assertTrue(area.noCulling);
        state.clear();
        state.update(area, plan, bounds -> false);
        assertFalse(area.noCulling);
        state.update(area, null, bounds -> { fail("Disabled projections must not run a frustum test"); return true; });
        assertFalse(area.noCulling);
        verifyNoInteractions(area);
    }

    @Test void disabledProjectionNeverChangesAnUntrackedEntity() {
        var state = new ProjectionCulling(); var area = mock(Entity.class);
        state.update(area, false); assertFalse(area.noCulling);
        area.noCulling = true;
        state.update(area, false); assertTrue(area.noCulling);
    }
}
