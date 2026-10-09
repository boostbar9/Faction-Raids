package com.devfarinsky.siegeoverhaul.nativecompat;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Ephemeral, per-marker work fences. Neither a claim nor completion grants world or inventory
 * authority: the caller must still validate the durable project and observe native work receipts.
 * There is deliberately no saved lease or completed-cell list. Construct a fresh instance on reload
 * and close the old instance on marker unload, removal, or project replacement.
 *
 * <p>The server-thread caller retains each token and releases it on sleep, supply waiting, or any
 * other interruption. Renew before continuing active native work; tokens are valid only before the
 * TTL boundary. All operations are synchronized, but this does not make external native actions safe
 * off the server thread or atomic with these fences.</p>
 */
public final class SharedConstructionWorkClaims implements AutoCloseable {
    public static final int MAX_ACTIVE_WORKERS = 4;
    public static final int MAX_TTL_TICKS = 200;
    public static final int MAX_AUTHORIZED_TARGETS = 65_536;

    /**
     * An opaque reservation generation, authenticated by object identity, never its readable fields.
     * Cannot be reconstructed from worker/cell values or carried into another coordinator instance.
     */
    public static final class Token {
        private final UUID worker;
        private final long target;

        private Token(UUID worker, long target) {
            this.worker = worker;
            this.target = target;
        }

        public UUID worker() { return worker; }
        public long target() { return target; }
    }

    private static final class Lease {
        final Token token;
        long renewedAt;

        Lease(Token token, long renewedAt) {
            this.token = token;
            this.renewedAt = renewedAt;
        }
    }

    private final Set<Long> authorizedTargets;
    private final int ttlTicks;
    private final Map<UUID, Lease> workers = new HashMap<>();
    private boolean observedTime;
    private long lastGameTick;
    private boolean closed;

    /** Packed cell coordinates are immutable values; the authorized set is defensively copied. */
    public SharedConstructionWorkClaims(Set<Long> authorizedTargets, int ttlTicks) {
        if (ttlTicks < 1 || ttlTicks > MAX_TTL_TICKS)
            throw new IllegalArgumentException("Work claim TTL must be between 1 and " + MAX_TTL_TICKS + " ticks");
        if (authorizedTargets.size() > MAX_AUTHORIZED_TARGETS)
            throw new IllegalArgumentException("Work claim targets exceed the accepted native plan limit");
        this.authorizedTargets = Set.copyOf(authorizedTargets);
        if (this.authorizedTargets.size() > MAX_AUTHORIZED_TARGETS)
            throw new IllegalArgumentException("Work claim targets exceed the accepted native plan limit");
        this.ttlTicks = ttlTicks;
    }

    /**
     * Returns a new generation, or null if the worker/cell is already claimed, outside the accepted
     * set, capacity is full, or time is invalid. Repeated claims never silently renew an old token.
     * The caller separately verifies worker authorization and that this target still needs work.
     */
    public synchronized Token claim(UUID worker, long target, long gameTick) {
        if (!advance(gameTick) || worker == null || worker.equals(new UUID(0, 0))
                || !authorizedTargets.contains(target) || workers.containsKey(worker)
                || workers.size() >= MAX_ACTIVE_WORKERS) return null;
        for (Lease lease : workers.values()) if (lease.token.target == target) return null;
        Token token = new Token(worker, target);
        workers.put(worker, new Lease(token, gameTick));
        return token;
    }

    /** Extends only the exact current generation; an expired token cannot renew itself. */
    public synchronized boolean renew(Token token, long gameTick) {
        if (!advance(gameTick) || !current(token)) return false;
        workers.get(token.worker).renewedAt = gameTick;
        return true;
    }

    /**
     * Consumes only the exact current token after the caller verifies native completion. Records no
     * completed target and performs no action; durable project/native receipts remain authoritative.
     */
    public synchronized boolean complete(Token token, long gameTick) {
        return release(token, gameTick);
    }

    /** Release on sleep, supply waiting, interruption, or worker unload; stale callbacks do nothing. */
    public synchronized boolean release(Token token, long gameTick) {
        if (!advance(gameTick) || !current(token)) return false;
        workers.remove(token.worker);
        return true;
    }

    /** Expires idle claims. Negative/backward time clears all generations and rejects this call. */
    public synchronized boolean tick(long gameTick) {
        return advance(gameTick);
    }

    /** Marker unload is terminal. Queued callbacks cannot revive this coordinator after reload. */
    @Override
    public synchronized void close() {
        closed = true;
        workers.clear();
    }

    private boolean current(Token token) {
        if (token == null) return false;
        Lease lease = workers.get(token.worker);
        return lease != null && lease.token == token;
    }

    private boolean advance(long gameTick) {
        if (closed) return false;
        if (gameTick < 0 || observedTime && gameTick < lastGameTick) {
            workers.clear();
            // A valid regressed clock starts a new epoch, but the discovering operation fails closed.
            observedTime = gameTick >= 0;
            lastGameTick = gameTick;
            return false;
        }
        observedTime = true;
        lastGameTick = gameTick;
        // Nonnegative monotonic ticks make subtraction safe even near Long.MAX_VALUE.
        workers.values().removeIf(lease -> gameTick - lease.renewedAt >= ttlTicks);
        return true;
    }
}
