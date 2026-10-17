package com.devfarinsky.siegeoverhaul.core;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** New gate-aware stages contain one component, bounding direct live approach checks to four gates. */
public final class PerimeterGateStages {
    private PerimeterGateStages() {}

    public static PerimeterStageLayout.Layout partition(PerimeterBlueprint.Plan plan,
                                                         PerimeterStageLayout.NativeStageValidator nativeValidator) {
        Objects.requireNonNull(nativeValidator, "The actual native serializer check is required");
        Map<Long, Integer> components = columns(plan);
        return PerimeterStageLayout.partition(plan, stage -> {
            if (component(stage, components) < 0) return "Gate-aware native sections cannot span claim components";
            return nativeValidator.problem(stage);
        });
    }

    /** Rechecked on new preparation and codec restore, not inferred from native/entity selectors. */
    static List<Integer> components(PerimeterBlueprint.Plan plan, PerimeterStageLayout.Layout layout) {
        Map<Long, Integer> columns = columns(plan); List<Integer> result = new ArrayList<>();
        for (var stage : layout.stages()) {
            int component = component(stage, columns);
            if (component < 0) throw new IllegalArgumentException("Gate-aware native section has mixed or missing claim components");
            result.add(component);
        }
        return List.copyOf(result);
    }

    private static Map<Long, Integer> columns(PerimeterBlueprint.Plan plan) {
        if (plan == null || !plan.valid() || plan.columns().isEmpty() || plan.columns().size() > PerimeterStageLayout.MAX_COLUMNS)
            throw new IllegalArgumentException("A complete bounded gate wall is required");
        Map<Long, Integer> result = new HashMap<>();
        for (var column : plan.columns()) {
            if (column == null || column.base() == null || column.componentId() < 0
                    || result.putIfAbsent(PerimeterStageLayout.column(column.base().asLong()), column.componentId()) != null)
                throw new IllegalArgumentException("Invalid gate stage column membership");
        }
        return result;
    }

    private static int component(PerimeterStageLayout.Stage stage, Map<Long, Integer> columns) {
        Integer component = null;
        for (long cell : stage.columns()) {
            Integer current = columns.get(cell);
            if (current == null || component != null && !component.equals(current)) return -1;
            component = current;
        }
        return component == null ? -1 : component;
    }
}
