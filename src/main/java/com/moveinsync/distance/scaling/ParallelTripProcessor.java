package com.moveinsync.distance.scaling;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

/**
 * Bounded parallel processor for independent trips.
 *
 * <p>Different trips are independent and can be processed concurrently. A single trip's GPS pings
 * are <b>not</b> independent and must remain strictly timestamp-ordered inside the engine, so
 * parallelism is applied at the <b>trip</b> granularity only.</p>
 *
 * <p>Results are returned in the exact input order, which keeps the pipeline deterministic even
 * though execution was concurrent. Worker count is bounded and should be sized against CPU cores,
 * heap, database connection pool and external routing rate limits.</p>
 */
public final class ParallelTripProcessor implements AutoCloseable {

    private final ExecutorService pool;
    private final int workerCount;

    public ParallelTripProcessor(int workerCount) {
        if (workerCount <= 0) {
            throw new IllegalArgumentException("workerCount must be > 0");
        }
        this.workerCount = workerCount;
        AtomicInteger threadIndex = new AtomicInteger(0);
        ThreadFactory factory = runnable -> {
            Thread thread = new Thread(runnable, "trip-worker-" + threadIndex.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
        this.pool = Executors.newFixedThreadPool(workerCount, factory);
    }

    /**
     * Processes all inputs concurrently and returns results in input order.
     *
     * @throws IllegalStateException if the current thread is interrupted or any task fails
     */
    public <I, O> List<O> processInOrder(List<I> inputs, Function<I, O> task) {
        Objects.requireNonNull(inputs, "inputs must not be null");
        Objects.requireNonNull(task, "task must not be null");
        if (inputs.isEmpty()) {
            return List.of();
        }

        List<Future<O>> futures = new ArrayList<>(inputs.size());
        for (I input : inputs) {
            futures.add(pool.submit(() -> task.apply(input)));
        }

        List<O> results = new ArrayList<>(inputs.size());
        for (Future<O> future : futures) {
            try {
                results.add(future.get());
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted while processing trips in parallel", ex);
            } catch (ExecutionException ex) {
                throw new IllegalStateException("Trip processing task failed", ex.getCause());
            }
        }
        return results;
    }

    public int getWorkerCount() {
        return workerCount;
    }

    @Override
    public void close() {
        pool.shutdown();
        try {
            if (!pool.awaitTermination(10, TimeUnit.SECONDS)) {
                pool.shutdownNow();
            }
        } catch (InterruptedException ex) {
            pool.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
