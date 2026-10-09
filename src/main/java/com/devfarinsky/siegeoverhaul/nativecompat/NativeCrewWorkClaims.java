package com.devfarinsky.siegeoverhaul.nativecompat;

import com.talhanation.workers.entities.BuilderEntity;
import com.talhanation.workers.entities.ai.BuilderWorkGoal;
import com.talhanation.workers.entities.workarea.BuildArea;
import com.talhanation.workers.world.BuildBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.block.state.BlockState;

import java.lang.ref.WeakReference;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.Stack;
import java.util.WeakHashMap;

/**
 * Server-thread scheduling for an already authenticated shared native project. This is not an
 * admission, inventory, world-edit or completion authority. The caller must validate the raw native
 * queues first, then recheck the selected mutation cell and arrival before dispatching Workers.
 * Only a private placement queue and its worker-local target can be changed here; native shared
 * BuildBlock queues and potentially aliased mining queues are never edited.
 */
public final class NativeCrewWorkClaims {
    private static final int TTL_TICKS = SharedConstructionWorkClaims.MAX_TTL_TICKS;
    private static final Map<Entity, Session> SESSIONS = new WeakHashMap<>();
    private static final Set<Entity> CLOSED = Collections.newSetFromMap(new WeakHashMap<>());
    private static final Map<Mob, Held> HELD = new WeakHashMap<>();

    private static final class Session {
        final Map<BlockPos, BlockState> accepted;
        final SharedConstructionWorkClaims claims;

        Session(Map<BlockPos, BlockState> accepted) {
            Map<BlockPos, BlockState> copy = new HashMap<>();
            accepted.forEach((pos, state) -> copy.put(pos.immutable(), state));
            this.accepted = Map.copyOf(copy);
            Set<Long> cells = new HashSet<>();
            copy.keySet().forEach(pos -> cells.add(pos.asLong()));
            claims = new SharedConstructionWorkClaims(cells, TTL_TICKS);
        }
    }

    // Weak references avoid a value -> goal -> worker cycle retaining a WeakHashMap key.
    private record Held(Session session, WeakReference<Entity> area, WeakReference<BuilderWorkGoal> goal,
                        SharedConstructionWorkClaims.Token token) {}

    private NativeCrewWorkClaims() {}

    /**
     * False means no native dispatch is safe now (including all available cells being claimed).
     * Native states other than PLACE_BLOCKS retain all their fields and only release an old token.
     * An empty private queue may dispatch normally so Workers can request its next material batch.
     */
    public static synchronized boolean prepare(Mob worker, Goal nativeGoal, Entity area,
                                               Map<BlockPos, BlockState> accepted, Set<Long> completed) {
        if (!(nativeGoal instanceof BuilderWorkGoal goal) || goal.state != BuilderWorkGoal.State.PLACE_BLOCKS) {
            release(worker);
            return true;
        }
        try {
            if (!(worker instanceof BuilderEntity builder) || goal.builderEntity != builder
                    || !(area instanceof ProtectedBuildArea buildArea) || builder.currentBuildArea != area
                    || !(worker.level() instanceof ServerLevel level) || area.level() != level
                    || area.isRemoved() || CLOSED.contains(area) || accepted == null || completed == null
                    || accepted.size() > SharedConstructionWorkClaims.MAX_AUTHORIZED_TARGETS
                    || completed.size() > accepted.size() || !privatePlacementQueue(goal, buildArea))
                return blocked(worker);

            Session session = SESSIONS.get(area);
            if (session == null) {
                session = new Session(accepted);
                SESSIONS.put(area, session);
            } else if (!session.accepted.equals(accepted)) {
                // A changed recipe cannot silently inherit live tokens from this marker generation.
                return blocked(worker);
            }
            for (long cell : completed) if (!session.accepted.containsKey(BlockPos.of(cell))) return blocked(worker);
            long tick = level.getGameTime();
            if (!session.claims.tick(tick)) return blocked(worker);

            Set<BlockPos> pending = pendingTargets(buildArea, session.accepted);
            Stack<BlockPos> next = new Stack<>();
            Set<BlockPos> unique = new HashSet<>();
            // Stage changes only after every potential prune has a loaded, exact-state proof. An
            // invalid lower stack entry must not be hidden by successfully pruning the top entry.
            for (BlockPos pos : goal.stackToPlace) {
                if (pos == null || !unique.add(pos) || !session.accepted.containsKey(pos)) return blocked(worker);
                int status = targetStatus(level, pos, session.accepted, pending, completed);
                if (status < 0) return blocked(worker);
                if (status == 0) next.push(pos.immutable());
            }
            BlockPos current = goal.blockPos;
            if (current != null) {
                int status = targetStatus(level, current, session.accepted, pending, completed);
                if (status < 0) return blocked(worker);
                // A previously popped native target still has first priority. If another worker
                // holds it, restore that priority in this private queue before selecting a fallback.
                if (status == 0) {
                    next.remove(current);
                    next.push(current.immutable());
                }
            }

            Held held = HELD.get(worker);
            if (held != null && (held.session != session || held.area.get() != area || held.goal.get() != goal
                    || current == null || held.token.target() != current.asLong() || !next.contains(current))) {
                release(worker);
                held = null;
            }
            if (held != null && !session.claims.renew(held.token, tick)) {
                release(worker);
                held = null;
            }

            SharedConstructionWorkClaims.Token token = held == null ? null : held.token;
            BlockPos selected = token == null ? null : BlockPos.of(token.target());
            for (int i = next.size() - 1; token == null && i >= 0; i--) {
                BlockPos candidate = next.get(i);
                token = session.claims.claim(worker.getUUID(), candidate.asLong(), tick);
                if (token != null) selected = candidate;
            }
            if (token != null) {
                next.remove(selected);
                HELD.put(worker, new Held(session, new WeakReference<>(area), new WeakReference<>(goal), token));
            }
            // Assign a private copy, never mutate an upstream stack object which another goal may
            // still retain. Preserve relative order of every unselected and unfinished target.
            goal.stackToPlace = next;
            goal.blockPos = selected;
            return selected != null || next.isEmpty();
        } catch (RuntimeException | LinkageError unavailable) {
            return blocked(worker);
        }
    }

    /** Must be called on sleep, supply collection, interruption, reassignment and worker unload. */
    public static synchronized void release(Mob worker) {
        Held held = HELD.remove(worker);
        if (held == null) return;
        Entity area = held.area.get();
        if (area != null && area.level() instanceof ServerLevel level)
            held.session.claims.release(held.token, level.getGameTime());
    }

    /** Marker unload/removal is terminal for this object; a reloaded marker gets fresh claims. */
    public static synchronized void close(Entity area) {
        if (area == null) return;
        CLOSED.add(area);
        Session session = SESSIONS.remove(area);
        if (session != null) session.claims.close();
        HELD.values().removeIf(held -> held.area.get() == area);
    }

    /** After the existing guard observes native receipts; this never creates a completion receipt. */
    public static synchronized void after(Mob worker, Entity area) {
        Held held = HELD.get(worker);
        if (held == null || held.area.get() != area) return; // A stale old-marker callback has no authority.
        BuilderWorkGoal goal = held.goal.get();
        if (!(worker instanceof BuilderEntity builder) || builder.currentBuildArea != area || goal == null
                || goal.state != BuilderWorkGoal.State.PLACE_BLOCKS || goal.blockPos == null
                || goal.blockPos.asLong() != held.token.target() || !(worker.level() instanceof ServerLevel level)
                || area.level() != level || area.isRemoved()) {
            release(worker);
            return;
        }
        BlockPos pos = BlockPos.of(held.token.target());
        if (!level.hasChunkAt(pos)) {
            release(worker);
        } else if (held.session.accepted.get(pos).equals(level.getBlockState(pos))) {
            held.session.claims.complete(held.token, level.getGameTime());
            HELD.remove(worker);
        }
    }

    private static boolean blocked(Mob worker) {
        release(worker);
        return false;
    }

    private static boolean privatePlacementQueue(BuilderWorkGoal goal, BuildArea area) {
        Object queue = goal.stackToPlace;
        return queue != null && goal.stackToPlace.size() <= SharedConstructionWorkClaims.MAX_AUTHORIZED_TARGETS
                && queue != area.stackToPlace && queue != area.stackToPlaceMultiBlock
                && queue != area.stackToBreak && queue != area.stackToFree
                && queue != goal.stackToBreak && queue != goal.stackToFree;
    }

    private static Set<BlockPos> pendingTargets(BuildArea area, Map<BlockPos, BlockState> accepted) {
        if (area.stackToPlace == null || area.stackToPlace.size() > accepted.size())
            throw new IllegalArgumentException("Invalid shared native placement queue");
        Set<BlockPos> pending = new HashSet<>();
        for (BuildBlock cell : area.stackToPlace) {
            if (cell == null || cell.getPos() == null || !cell.getState().equals(accepted.get(cell.getPos()))
                    || !pending.add(cell.getPos().immutable()))
                throw new IllegalArgumentException("Changed shared native placement target");
        }
        return pending;
    }

    /** -1: unsafe/unreadable; 0: unfinished; 1: safely skippable. */
    private static int targetStatus(ServerLevel level, BlockPos pos, Map<BlockPos, BlockState> accepted,
                                    Set<BlockPos> pending, Set<Long> completed) {
        BlockState expected = accepted.get(pos);
        if (expected == null || !level.hasChunkAt(pos)) return -1;
        BlockState actual = level.getBlockState(pos);
        if (actual == null) return -1;
        boolean matches = expected.equals(actual);
        // Missing queue entries and previous completion are never permission to rebuild a cell.
        if ((!pending.contains(pos) || completed.contains(pos.asLong())) && !matches) return -1;
        return matches ? 1 : 0;
    }
}
