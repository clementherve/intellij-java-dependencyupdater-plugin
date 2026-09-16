package com.github.clementherve.intellijjavadependencyupdaterplugin.service;

import com.intellij.openapi.progress.ProgressIndicator;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * Checks many dependencies concurrently on a small bounded thread pool instead of one blocking
 * repository call at a time. Maven Central and Nexus have no batch-lookup API, so this is what
 * actually cuts scan time: the same number of HTTP calls, just in flight together.
 */
public final class ParallelDependencyChecker {

    private static final int MAX_CONCURRENCY = 8;

    private ParallelDependencyChecker() {
    }

    /**
     * Runs {@code check} for every item in {@code items} on a bounded thread pool, then delivers
     * each result to {@code onResult} sequentially, on the calling thread, in the same order as
     * {@code items}. {@code check} must not throw - it should catch its own failures and encode
     * them in {@code R}. Stops delivering results as soon as {@code indicator} is canceled.
     */
    public static <T, R> void run(@NotNull List<T> items,
                                   @NotNull ProgressIndicator indicator,
                                   @NotNull Function<T, R> check,
                                   @NotNull BiConsumer<T, R> onResult) {
        if (items.isEmpty()) {
            return;
        }

        ExecutorService pool = Executors.newFixedThreadPool(Math.min(MAX_CONCURRENCY, items.size()));
        try {
            List<Future<R>> futures = new ArrayList<>(items.size());
            for (T item : items) {
                futures.add(pool.submit(() -> check.apply(item)));
            }

            for (int i = 0; i < items.size(); i++) {
                if (indicator.isCanceled()) {
                    return;
                }
                onResult.accept(items.get(i), getUnchecked(futures.get(i)));
            }
        } finally {
            pool.shutdownNow();
        }
    }

    @NotNull
    private static <R> R getUnchecked(@NotNull Future<R> future) {
        try {
            return future.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        } catch (ExecutionException e) {
            // check() is expected to catch its own failures - surface a bug loudly rather than swallowing it.
            throw new IllegalStateException(e.getCause());
        }
    }
}
