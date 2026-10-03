package com.devfarinsky.siegeoverhaul.compat;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Optional;

/** Read-only native projection settings; no companion classes are linked. */
public final class WorkersProjectionView {
    private record Api(Method enabled, Method canSee, Method area, Field showBox) {}

    private static final ClassValue<Optional<Api>> APIS = new ClassValue<>() {
        @Override protected Optional<Api> computeValue(Class<?> type) {
            try {
                Field showBox;
                try { showBox = type.getField("showBox"); }
                catch (NoSuchFieldException olderApi) { showBox = null; }
                return Optional.of(new Api(type.getMethod("getAlwaysShowProjection"),
                        type.getMethod("canPlayerSee", Player.class), type.getMethod("getArea"), showBox));
            } catch (ReflectiveOperationException | RuntimeException unavailable) {
                return Optional.empty();
            }
        }
    };

    private WorkersProjectionView() {}

    /** Same visibility permission and world-space bounds as Workers' native renderer. */
    public static AABB visibleBounds(Object area, Player player) {
        return visibleBounds(area, player, null);
    }

    /**
     * Mirrors WorkerAreaRenderer's native permission and visibility gates.
     * A large focus-only plan still needs full-plan culling while focused.
     * focusedArea must come from Recruits' own client focus calculation, not
     * a replacement raycast with different range or occlusion semantics.
     */
    public static AABB visibleBounds(Object area, Player player, Object focusedArea) {
        if (area == null || player == null) return null;
        var api = APIS.get(area.getClass()).orElse(null);
        if (api == null) return null;
        try {
            if (!Boolean.TRUE.equals(api.canSee().invoke(area, player))) return null;
            boolean showBox = api.showBox() != null && api.showBox().getBoolean(area);
            if (!showBox && focusedArea != area && !Boolean.TRUE.equals(api.enabled().invoke(area))) return null;
            if (!(api.area().invoke(area) instanceof AABB box)) return null;
            return new AABB(box.minX, box.minY, box.minZ, box.maxX + 1, box.maxY, box.maxZ + 1);
        } catch (ReflectiveOperationException | RuntimeException unavailable) {
            return null;
        }
    }
}
