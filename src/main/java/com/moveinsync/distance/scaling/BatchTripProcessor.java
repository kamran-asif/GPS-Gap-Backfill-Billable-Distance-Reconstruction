package com.moveinsync.distance.scaling;

import java.util.List;
import java.util.Objects;

/**
 * Bounded batch processor for large trip datasets.
 *
 * <p>Loading an entire dataset into memory (e.g. 1.5 crore GPS pings) risks {@code OutOfMemoryError}.
 * This processor streams the dataset in fixed-size chunks, processes each chunk, and only ever holds
 * one batch in memory at a time. Each batch is retried a bounded number of times, so a single transient
 * failure cannot abort the whole run.</p>
 *
 * <p>Production note: {@code fetch(offset, limit)} is intentionally offset-based for clarity; for very
 * large datasets a keyset / cursor based fetcher avoids deep-offset scans.</p>
 *
 * @param <T> record type being processed (e.g. a trip id, or a page of pings)
 */
public final class BatchTripProcessor<T> {

    @FunctionalInterface
    public interface BatchFetcher<T> {
        /**
         * Fetches one page of records.
         *
         * @param offset zero-based record offset
         * @param limit  maximum records to return; must never be exceeded
         */
        List<T> fetch(int offset, int limit);
    }

    @FunctionalInterface
    public interface BatchHandler<T> {
        void handle(List<T> batch);
    }

    private final int batchSize;
    private final int maxRetries;

    public BatchTripProcessor(int batchSize, int maxRetries) {
        if (batchSize <= 0) {
            throw new IllegalArgumentException("batchSize must be > 0");
        }
        if (maxRetries < 0) {
            throw new IllegalArgumentException("maxRetries must be >= 0");
        }
        this.batchSize = batchSize;
        this.maxRetries = maxRetries;
    }

    /**
     * Processes up to {@code totalRecords} records in bounded batches.
     *
     * @return an immutable report of what was processed, retried and dropped
     */
    public BatchReport process(int totalRecords, BatchFetcher<T> fetcher, BatchHandler<T> handler) {
        Objects.requireNonNull(fetcher, "fetcher must not be null");
        Objects.requireNonNull(handler, "handler must not be null");

        int offset = 0;
        int batchesStarted = 0;
        int recordsProcessed = 0;
        int failedBatches = 0;
        int retriedBatches = 0;
        int largestBatchObserved = 0;

        while (offset < totalRecords) {
            int limit = Math.min(batchSize, totalRecords - offset);
            List<T> batch = fetcher.fetch(offset, limit);

            if (batch == null || batch.isEmpty()) {
                break;
            }
            if (batch.size() > batchSize) {
                throw new IllegalStateException("Batch fetcher returned " + batch.size()
                        + " records, exceeding configured batch size " + batchSize);
            }

            largestBatchObserved = Math.max(largestBatchObserved, batch.size());
            batchesStarted++;

            int attempt = 0;
            boolean succeeded = false;
            while (attempt <= maxRetries) {
                try {
                    handler.handle(batch);
                    succeeded = true;
                    break;
                } catch (RuntimeException ex) {
                    attempt++;
                    if (attempt <= maxRetries) {
                        retriedBatches++;
                    }
                }
            }

            if (succeeded) {
                recordsProcessed += batch.size();
            } else {
                failedBatches++;
            }

            // Always advance to guarantee termination even when a batch permanently fails.
            offset += batch.size();
        }

        return new BatchReport(totalRecords, batchesStarted, recordsProcessed, failedBatches,
                retriedBatches, largestBatchObserved, batchSize);
    }

    public int getBatchSize() {
        return batchSize;
    }

    public int getMaxRetries() {
        return maxRetries;
    }

    /**
     * Immutable outcome of a batch run.
     */
    public record BatchReport(int totalRecords,
                             int batchesStarted,
                             int recordsProcessed,
                             int failedBatches,
                             int retriedBatches,
                             int largestBatchObserved,
                             int configuredBatchSize) {

        public boolean isFullyProcessed() {
            return failedBatches == 0 && recordsProcessed == totalRecords;
        }

        public boolean isMemoryBounded() {
            return largestBatchObserved <= configuredBatchSize;
        }
    }
}
