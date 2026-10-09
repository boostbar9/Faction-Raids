package com.devfarinsky.siegeoverhaul.nativecompat;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class SharedConstructionWorkClaimsTest {
    private static final UUID FIRST = new UUID(1, 1), SECOND = new UUID(2, 2);

    @Test void allowsOnlyOneTargetPerWorkerAndOneWorkerPerTarget() {
        var claims = claims(10);
        var token = claims.claim(FIRST, 1, 0);
        assertNotNull(token);
        assertEquals(FIRST, token.worker());
        assertEquals(1L, token.target());
        assertNull(claims.claim(FIRST, 1, 0));
        assertNull(claims.claim(FIRST, 2, 0));
        assertNull(claims.claim(SECOND, 1, 0));
        assertNotNull(claims.claim(SECOND, 2, 0));
    }

    @Test void capacityCountsActiveWorkersAndReleasedCapacityCanBeReused() {
        var claims = claims(10);
        var first = claims.claim(FIRST, 1, 0);
        for (int index = 2; index <= 4; index++)
            assertNotNull(claims.claim(new UUID(index, index), index, 0));
        assertNull(claims.claim(new UUID(5, 5), 5, 0));
        assertTrue(claims.release(first, 1));
        assertNotNull(claims.claim(new UUID(5, 5), 5, 1));
        assertNull(claims.claim(FIRST, 1, 1));
    }

    @Test void authorizedTargetsAreDefensivelyCopiedAndCannotBeExpandedByTheCaller() {
        var input = new HashSet<>(Set.of(1L));
        var claims = new SharedConstructionWorkClaims(input, 10);
        input.clear();
        input.add(2L);
        assertNotNull(claims.claim(FIRST, 1, 0));
        assertNull(claims.claim(SECOND, 2, 0));
        assertNull(claims.claim(SECOND, Long.MIN_VALUE, 0));
        assertNull(new SharedConstructionWorkClaims(Set.of(), 10).claim(FIRST, 1, 0));
    }

    @Test void malformedWorkersTargetsAndUnboundedTtlCannotCreateClaims() {
        var claims = claims(10);
        assertNull(claims.claim(null, 1, 0));
        assertNull(claims.claim(new UUID(0, 0), 1, 0));
        assertThrows(NullPointerException.class, () -> new SharedConstructionWorkClaims(null, 10));
        var withNull = new HashSet<Long>();
        withNull.add(null);
        assertThrows(NullPointerException.class, () -> new SharedConstructionWorkClaims(withNull, 10));
        for (int ttl : new int[]{Integer.MIN_VALUE, -1, 0, 201, Integer.MAX_VALUE})
            assertThrows(IllegalArgumentException.class, () -> new SharedConstructionWorkClaims(Set.of(1L), ttl));
        assertNotNull(new SharedConstructionWorkClaims(Set.of(1L), 1).claim(FIRST, 1, 0));
        assertNotNull(new SharedConstructionWorkClaims(Set.of(1L), 200).claim(FIRST, 1, 0));
    }

    @Test void authorizedTargetsCannotExceedTheExistingNativePlanCap() {
        Set<Long> targets = new HashSet<>();
        for (long cell = 0; cell < SharedConstructionWorkClaims.MAX_AUTHORIZED_TARGETS; cell++) targets.add(cell);
        var claims = new SharedConstructionWorkClaims(targets, 10);
        assertNotNull(claims.claim(FIRST, SharedConstructionWorkClaims.MAX_AUTHORIZED_TARGETS - 1L, 0));
        targets.add((long) SharedConstructionWorkClaims.MAX_AUTHORIZED_TARGETS);
        assertThrows(IllegalArgumentException.class, () -> new SharedConstructionWorkClaims(targets, 10));
        assertNull(claims.claim(SECOND, SharedConstructionWorkClaims.MAX_AUTHORIZED_TARGETS, 0));
    }

    @Test void expiryBoundaryReleasesCellAndWorkerWithoutAllowingOldCompletionOrRenewal() {
        var claims = claims(10);
        var old = claims.claim(FIRST, 1, 20);
        assertNull(claims.claim(SECOND, 1, 29));
        var replacement = claims.claim(SECOND, 1, 30);
        assertNotNull(replacement);
        assertFalse(claims.complete(old, 30));
        assertFalse(claims.renew(old, 30));
        assertFalse(claims.release(old, 30));
        assertTrue(claims.renew(replacement, 30));
        assertNotNull(claims.claim(FIRST, 2, 30));
    }

    @Test void expiredTokenCannotRenewEvenWithoutAnyReplacement() {
        var claims = claims(10);
        var old = claims.claim(FIRST, 1, 0);
        assertFalse(claims.renew(old, 10));
        assertFalse(claims.complete(old, 10));
        assertNotNull(claims.claim(FIRST, 1, 10));
    }

    @Test void renewalIsExplicitAndPreservesOnlyTheCurrentGeneration() {
        var claims = claims(10);
        var token = claims.claim(FIRST, 1, 0);
        assertTrue(claims.renew(token, 9));
        assertNull(claims.claim(FIRST, 1, 18));
        assertFalse(claims.renew(token, 19));
        var next = claims.claim(FIRST, 1, 19);
        assertNotNull(next);
        assertNotSame(token, next);
        assertFalse(claims.complete(token, 19));
        assertTrue(claims.complete(next, 19));
    }

    @Test void duplicateClaimsAndIdleTicksNeverExtendTheTtl() {
        var claims = claims(10);
        var token = claims.claim(FIRST, 1, 0);
        assertNull(claims.claim(FIRST, 1, 9));
        assertTrue(claims.tick(9));
        assertTrue(claims.tick(10));
        assertFalse(claims.complete(token, 10));
        assertNotNull(claims.claim(SECOND, 1, 10));
    }

    @Test void completionConsumesExactTokenWithoutCreatingIndependentProgress() {
        var claims = claims(10);
        var token = claims.claim(FIRST, 1, 0);
        assertTrue(claims.complete(token, 1));
        assertFalse(claims.complete(token, 1));
        assertFalse(claims.release(token, 1));
        assertFalse(claims.renew(token, 1));
        // The durable project must decide whether work is still needed; this fence stores no progress.
        var fresh = claims.claim(FIRST, 1, 1);
        assertNotNull(fresh);
        assertNotSame(token, fresh);
        assertFalse(claims.complete(token, 1));
        assertTrue(claims.complete(fresh, 1));
    }

    @Test void sleepSupplyInterruptionAndWorkerUnloadReleaseOnlyTheRetainedGeneration() {
        for (String reason : List.of("sleep", "supply", "interruption", "worker-unload")) {
            var claims = claims(10);
            var old = claims.claim(FIRST, 1, 0);
            assertTrue(claims.release(old, 1), reason);
            var replacement = claims.claim(FIRST, 1, 1);
            assertNotNull(replacement, reason);
            assertNotSame(old, replacement, reason);
            assertFalse(claims.release(old, 1), reason);
            assertFalse(claims.complete(old, 1), reason);
            assertTrue(claims.complete(replacement, 1), reason);
        }
    }

    @Test void markerUnloadIsTerminalAndReloadCannotAcceptTokensFromTheOldInstance() {
        var before = claims(10);
        var old = before.claim(FIRST, 1, 0);
        before.close();
        before.close();
        assertNull(before.claim(FIRST, 1, 1));
        assertFalse(before.tick(1));
        assertFalse(before.renew(old, 1));
        assertFalse(before.complete(old, 1));
        assertFalse(before.release(old, 1));
        var after = claims(10);
        var fresh = after.claim(FIRST, 1, 1);
        assertNotNull(fresh);
        assertNotSame(old, fresh);
        assertFalse(after.renew(old, 1));
        assertFalse(after.complete(old, 1));
        assertFalse(after.release(old, 1));
        assertTrue(after.complete(fresh, 1));
    }

    @Test void anotherLiveMarkerCannotAuthenticateEvenMatchingWorkerAndCellFields() {
        var one = claims(10);
        var two = claims(10);
        var first = one.claim(FIRST, 1, 0);
        var other = two.claim(FIRST, 1, 0);
        assertFalse(one.renew(other, 0));
        assertFalse(one.release(other, 0));
        assertFalse(one.complete(other, 0));
        assertTrue(one.complete(first, 0));
        assertTrue(two.complete(other, 0));
    }

    @Test void generationTokensCannotBePubliclyConstructedOrSerialized() {
        assertTrue(Modifier.isFinal(SharedConstructionWorkClaims.Token.class.getModifiers()));
        for (var constructor : SharedConstructionWorkClaims.Token.class.getDeclaredConstructors())
            assertTrue(Modifier.isPrivate(constructor.getModifiers()));
        assertFalse(java.io.Serializable.class.isAssignableFrom(SharedConstructionWorkClaims.Token.class));
        for (var field : SharedConstructionWorkClaims.Token.class.getDeclaredFields())
            assertTrue(Modifier.isPrivate(field.getModifiers()) && Modifier.isFinal(field.getModifiers()));
    }

    @Test void backwardTimeReleasesEveryClaimAndRejectsTheDiscoveringOperation() {
        var claims = claims(10);
        var first = claims.claim(FIRST, 1, 100);
        var second = claims.claim(SECOND, 2, 100);
        assertFalse(claims.renew(first, 99));
        assertFalse(claims.complete(second, 99));
        var replacement = claims.claim(SECOND, 1, 99);
        assertNotNull(replacement);
        assertFalse(claims.release(first, 99));
        assertTrue(claims.complete(replacement, 99));
    }

    @Test void claimCompletionReleaseAndIdleTickAllFailClosedOnTimeRegression() {
        for (String operation : List.of("claim", "complete", "release", "tick")) {
            var claims = claims(10);
            var old = claims.claim(FIRST, 1, 100);
            switch (operation) {
                case "claim" -> assertNull(claims.claim(SECOND, 2, 99));
                case "complete" -> assertFalse(claims.complete(old, 99));
                case "release" -> assertFalse(claims.release(old, 99));
                case "tick" -> assertFalse(claims.tick(99));
                default -> fail(operation);
            }
            assertFalse(claims.complete(old, 99), operation);
            assertNotNull(claims.claim(FIRST, 1, 99), operation);
        }
    }

    @Test void negativeTimeInvalidatesOldTokensAndCannotIssueNewOnes() {
        var claims = claims(10);
        var token = claims.claim(FIRST, 1, 0);
        assertFalse(claims.tick(-1));
        assertNull(claims.claim(SECOND, 1, Long.MIN_VALUE));
        assertFalse(claims.complete(token, 0));
        assertNotNull(claims.claim(SECOND, 1, 0));
    }

    @Test void ttlArithmeticDoesNotOverflowAtLargeGameTimes() {
        var claims = claims(10);
        var token = claims.claim(FIRST, 1, Long.MAX_VALUE - 10);
        assertTrue(claims.tick(Long.MAX_VALUE - 1));
        assertFalse(claims.complete(token, Long.MAX_VALUE));
        assertNotNull(claims.claim(FIRST, 1, Long.MAX_VALUE));
        assertFalse(claims.tick(Long.MIN_VALUE));
    }

    @Test void missingTokensCannotRenewCompleteOrReleaseOtherWorkers() {
        var claims = claims(10);
        var token = claims.claim(FIRST, 1, 0);
        assertFalse(claims.renew(null, 0));
        assertFalse(claims.complete(null, 0));
        assertFalse(claims.release(null, 0));
        assertTrue(claims.complete(token, 0));
    }

    @Test void concurrentClaimsAreAtomicForCellWorkerAndCapacity() throws Exception {
        var sameCell = claims(10);
        assertEquals(1, raceClaims(sameCell, false, true));
        var sameWorker = claims(10);
        assertEquals(1, raceClaims(sameWorker, true, false));
        var capacity = claims(10);
        assertEquals(SharedConstructionWorkClaims.MAX_ACTIVE_WORKERS, raceClaims(capacity, false, false));
    }

    @Test void concurrentCompletionAndReleaseConsumeAGenerationOnlyOnce() throws Exception {
        var claims = claims(10);
        var token = claims.claim(FIRST, 1, 0);
        List<Callable<Boolean>> operations = new ArrayList<>();
        for (int index = 0; index < 16; index++) {
            boolean complete = index % 2 == 0;
            operations.add(() -> complete ? claims.complete(token, 1) : claims.release(token, 1));
        }
        assertEquals(1, race(operations).stream().filter(Boolean::booleanValue).count());
        assertNotNull(claims.claim(FIRST, 1, 1));
        assertFalse(claims.renew(token, 1));
    }

    private static SharedConstructionWorkClaims claims(int ttl) {
        Set<Long> cells = new HashSet<>();
        for (long cell = 1; cell <= 16; cell++) cells.add(cell);
        return new SharedConstructionWorkClaims(cells, ttl);
    }

    private static long raceClaims(SharedConstructionWorkClaims claims, boolean sameWorker, boolean sameCell)
            throws Exception {
        List<Callable<SharedConstructionWorkClaims.Token>> operations = new ArrayList<>();
        for (int index = 1; index <= 16; index++) {
            UUID worker = sameWorker ? FIRST : new UUID(index, index);
            long target = sameCell ? 1 : index;
            operations.add(() -> claims.claim(worker, target, 0));
        }
        return race(operations).stream().filter(java.util.Objects::nonNull).count();
    }

    private static <T> List<T> race(List<Callable<T>> operations) throws Exception {
        var executor = Executors.newFixedThreadPool(operations.size());
        var ready = new CountDownLatch(operations.size());
        var start = new CountDownLatch(1);
        try {
            List<Future<T>> futures = new ArrayList<>();
            for (Callable<T> operation : operations) futures.add(executor.submit(() -> {
                ready.countDown();
                if (!start.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("Race never started");
                return operation.call();
            }));
            assertTrue(ready.await(5, TimeUnit.SECONDS), "Concurrent callers did not become ready");
            start.countDown();
            List<T> results = new ArrayList<>();
            for (Future<T> future : futures) results.add(future.get(5, TimeUnit.SECONDS));
            return results;
        } finally {
            start.countDown();
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
        }
    }
}
