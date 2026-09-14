package com.devfarinsky.siegeoverhaul.naval;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;

import java.util.UUID;

/** A reserved bridge attempt. Material already placed is never refunded on cancellation. */
public final class BridgePlan {
    public final UUID builder;
    public final BlockPos start;
    public final BlockPos objective;
    public final Direction direction;
    public final int span;
    public final long deadline;
    public int next = 1;
    public long nextPlacement;

    public BridgePlan(UUID builder, BlockPos start, BlockPos objective, Direction direction, int span, long deadline) {
        this.builder = builder;
        this.start = start.immutable();
        this.objective = objective.immutable();
        this.direction = direction;
        this.span = span;
        this.deadline = deadline;
    }

    public BlockPos shore() { return start.relative(direction, span + 1); }
    public BlockPos floor(int index) { return start.relative(direction, index).below(); }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("Builder", builder);
        tag.putLong("Start", start.asLong());
        tag.putLong("Objective", objective.asLong());
        tag.putString("Direction", direction.getName());
        tag.putInt("Span", span);
        tag.putInt("Next", next);
        tag.putLong("Deadline", deadline);
        tag.putLong("NextPlacement", nextPlacement);
        return tag;
    }

    public static BridgePlan load(CompoundTag tag) {
        Direction direction = Direction.byName(tag.getString("Direction"));
        int span = tag.getInt("Span"), next = tag.getInt("Next");
        if (!tag.hasUUID("Builder") || !tag.contains("Start") || !tag.contains("Objective")
                || direction == null || direction.getAxis().isVertical() || span < 1 || span > 64
                || next < 1 || next > span + 1) return null;
        BridgePlan plan = new BridgePlan(tag.getUUID("Builder"), BlockPos.of(tag.getLong("Start")),
                BlockPos.of(tag.getLong("Objective")), direction, span, tag.getLong("Deadline"));
        plan.next = next;
        plan.nextPlacement = tag.getLong("NextPlacement");
        return plan;
    }
}
