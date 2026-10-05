/*
 * Copyright 2026 Ivan Matiushkin
 * Licensed under the Apache License, Version 2.0.
 */
package io.github.resilience4j.retry.internal;

import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class ScheduledSupplierFailureTest {

    @Test
    void scheduledSupplierExceptionMustCompleteOriginalFuture() {
        try (var scheduler = new ControlledScheduler()) {
            var calls = new AtomicInteger();
            var failure = new IllegalStateException("client failed before returning a stage");
            var result = retry().<String>executeCompletionStage(scheduler, () -> {
                if (calls.incrementAndGet() == 1) {
                    return CompletableFuture.failedFuture(new IllegalArgumentException("temporary"));
                }
                throw failure;
            }).toCompletableFuture();

            assertFalse(result.isDone());
            scheduler.runNext();

            assertFailure(result, failure);
            assertEquals(2, calls.get());
            assertTrue(scheduler.pending.isEmpty(), "a synchronous failure is terminal");
        }
    }

    @Test
    void resultBasedRetryMustAlsoCompleteOnSynchronousFailure() {
        try (var scheduler = new ControlledScheduler()) {
            var calls = new AtomicInteger();
            var failure = new IllegalStateException("failed before stage");
            var retry = Retry.of("result", RetryConfig.<String>custom()
                .maxAttempts(3).retryOnResult("pending"::equals).build());
            var result = retry.<String>executeCompletionStage(scheduler, () -> {
                if (calls.incrementAndGet() == 1) {
                    return CompletableFuture.completedFuture("pending");
                }
                throw failure;
            }).toCompletableFuture();

            scheduler.runNext();

            assertFailure(result, failure);
            assertEquals(2, calls.get());
            assertTrue(scheduler.pending.isEmpty());
        }
    }

    @Test
    void scheduledErrorMustCompleteOriginalFutureWithoutRetry() {
        try (var scheduler = new ControlledScheduler()) {
            var calls = new AtomicInteger();
            var failure = new AssertionError("synthetic fatal failure");
            var result = retry().<String>executeCompletionStage(scheduler, () -> {
                if (calls.incrementAndGet() == 1) {
                    return CompletableFuture.failedFuture(new IllegalArgumentException("temporary"));
                }
                throw failure;
            }).toCompletableFuture();

            scheduler.runNext();

            assertFailure(result, failure);
            assertEquals(2, calls.get());
            assertTrue(scheduler.pending.isEmpty());
        }
    }

    @Test
    void initialSynchronousExceptionStillPropagates() {
        try (var scheduler = new ControlledScheduler()) {
            var failure = new IllegalArgumentException("initial failure");

            var thrown = assertThrows(IllegalArgumentException.class,
                () -> retry().executeCompletionStage(scheduler, () -> { throw failure; }));

            assertSame(failure, thrown);
            assertTrue(scheduler.pending.isEmpty());
        }
    }

    @Test
    void ordinaryAsyncRetryStillRecovers() {
        try (var scheduler = new ControlledScheduler()) {
            var calls = new AtomicInteger();
            var result = retry().executeCompletionStage(scheduler, () ->
                calls.incrementAndGet() < 3
                    ? CompletableFuture.failedFuture(new IllegalStateException("temporary"))
                    : CompletableFuture.completedFuture("ok")).toCompletableFuture();

            scheduler.runNext();
            assertFalse(result.isDone());
            scheduler.runNext();

            assertEquals("ok", result.join());
            assertEquals(3, calls.get());
            assertTrue(scheduler.pending.isEmpty());
        }
    }

    @Test
    void ordinaryAsyncRetryStillExhaustsConfiguredAttempts() {
        try (var scheduler = new ControlledScheduler()) {
            var calls = new AtomicInteger();
            var failure = new IllegalArgumentException("unavailable");
            var result = retry().executeCompletionStage(scheduler, () -> {
                calls.incrementAndGet();
                return CompletableFuture.failedFuture(failure);
            }).toCompletableFuture();

            scheduler.runNext();
            scheduler.runNext();

            assertFailure(result, failure);
            assertEquals(3, calls.get());
            assertTrue(scheduler.pending.isEmpty());
        }
    }

    @Test
    void rejectedSchedulingMustCompleteOriginalFuture() {
        try (var scheduler = new ControlledScheduler()) {
            scheduler.reject = true;

            var result = retry().executeCompletionStage(scheduler,
                () -> CompletableFuture.failedFuture(new IllegalStateException("temporary")))
                .toCompletableFuture();

            assertTrue(result.isCompletedExceptionally());
            assertInstanceOf(RejectedExecutionException.class,
                assertThrows(CompletionException.class, result::join).getCause());
        }
    }

    private static Retry retry() {
        return Retry.of("scheduled-failure", RetryConfig.custom()
            .maxAttempts(3).waitDuration(Duration.ofMillis(10)).build());
    }

    private static void assertFailure(CompletableFuture<?> result, Throwable failure) {
        assertTrue(result.isDone(), "the caller must not remain pending");
        assertSame(failure, assertThrows(CompletionException.class, result::join).getCause());
    }

    /** Controls scheduler execution without sleeping or starting worker threads. */
    private static class ControlledScheduler extends ScheduledThreadPoolExecutor {
        final Queue<Runnable> pending = new ArrayDeque<>();
        boolean reject;

        ControlledScheduler() { super(1); }

        @Override
        public ScheduledFuture<?> schedule(Runnable command, long delay, TimeUnit unit) {
            if (reject) { throw new RejectedExecutionException("synthetic shutdown"); }
            assertTrue(delay >= 0);
            pending.add(command);
            // The code under test does not consume the scheduling handle.
            return null;
        }

        void runNext() {
            assertFalse(pending.isEmpty(), "expected a scheduled retry");
            // Real scheduled executors capture thrown failures in a separate task Future.
            // Reproduce that separation; assertions concern the original caller Future.
            try { pending.remove().run(); } catch (Throwable ignored) { }
        }
    }
}
