package com.devfarinsky.siegeoverhaul.client;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.function.Predicate;

/** Owns only temporary client frustum overrides, preserving the entity's original flag. */
final class ProjectionCulling {
    private final Map<Entity, Boolean> previous = new IdentityHashMap<>();

    void update(Entity entity, AABB bounds, Predicate<AABB> inView) {
        update(entity, bounds != null && inView.test(bounds.inflate(.05)));
    }

    void update(Entity entity, boolean projectionInView) {
        if (projectionInView) {
            previous.putIfAbsent(entity, entity.noCulling);
            entity.noCulling = true;
        } else restore(entity);
    }

    void clear() {
        previous.forEach((entity, flag) -> entity.noCulling = flag);
        previous.clear();
    }

    private void restore(Entity entity) {
        Boolean flag = previous.remove(entity);
        if (flag != null) entity.noCulling = flag;
    }
}
