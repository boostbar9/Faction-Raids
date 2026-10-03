package com.devfarinsky.siegeoverhaul.compat;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;

import java.lang.reflect.Method;
import java.util.Optional;

/** Read-only native projection settings; no companion classes are linked. */
public final class WorkersProjectionView {
    private record Api(Method enabled, Method canSee, Method area) {}

    private static final ClassValue<Optional<Api>> APIS = new ClassValue<>() {
        @Override protected Optional<Api> computeValue(Class<?> type) {
            try {
                return Optional.of(new Api(type.getMethod("getAlwaysShowProjection"),
                        type.getMethod("canPlayerSee", Player.class), type.getMethod("getArea")));
            } catch (ReflectiveOperationException | RuntimeException unavailable) {
                return Optional.empty();
            }
        }
    };

    private WorkersProjectionView() {}

    /** Same visibility permission and world-space bounds as Workers' native renderer. */
    public static AABB visibleBounds(Object area, Player player) {
        if (area == null || player == null) return null;
        var api = APIS.get(area.getClass()).orElse(null);
        if (api == null) return null;
        try {
            if (!Boolean.TRUE.equals(api.enabled().invoke(area))
                    || !Boolean.TRUE.equals(api.canSee().invoke(area, player))) return null;
            if (!(api.area().invoke(area) instanceof AABB box)) return null;
            return new AABB(box.minX, box.minY, box.minZ, box.maxX + 1, box.maxY, box.maxZ + 1);
        } catch (ReflectiveOperationException | RuntimeException unavailable) {
            return null;
        }
    }
}
