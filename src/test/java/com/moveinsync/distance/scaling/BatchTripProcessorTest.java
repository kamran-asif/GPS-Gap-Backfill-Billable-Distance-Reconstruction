package com.moveinsync.distance.scaling;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BatchTripProcessorTest {

    @Test
    @DisplayName("All records are processed exactly once in bounded chunks (never exceeding batch size)")
    void testProcessesAllRecordsInBoundedBatches() {
        int totalRecords = 1_000_000;
        int batchSize = 10_000;
        BatchTripProcessor<Integer> processor = new BatchTripProcessor<>(batchSize, 0);

        // Simulates a repository over a large dataset without materialising it in memory.
        BatchTripProcessor.BatchFetcher<Integer> fetcher = (offset, limit) -> {
            List<Integer> page = new ArrayList<>(limit);
            for (int i = 0; i < limit; i++) {
                page.add(offset + i);
            }
            return page;
        };

        List<Integer> seen = new ArrayList<>();
        BatchTripProcessor.BatchHandler<Integer> handler = batch -> {
            assertThat(batch.size()).isLessThanOrEqualTo(batchSize);
            seen.addAll(batch);
        };

        BatchTripProcessor.BatchReport report = processor.process(totalRecords, fetcher, handler);

        assertThat(report.batchesStarted()).isEqualTo(100);
        assertThat(report.largestBatchObserved()).isEqualTo(batchSize);
        assertThat(report.isMemoryBounded()).isTrue();
        assertThat(report.recordsProcessed()).isEqualTo(totalRecords);
        assertThat(report.failedBatches()).isZero();
        assertThat(report.isFullyProcessed()).isTrue();
        assertThat(seen).hasSize(totalRecords);
        assertThat(seen.get(0)).isZero();
        assertThat(seen.get(totalRecords - 1)).isEqualTo(totalRecords - 1);
    }

    @Test
    @DisplayName("A transiently failing batch is retried and eventually succeeds")
    void testRetriesTransientBatchFailure() {
        BatchTripProcessor<Integer> processor = new BatchTripProcessor<>(3, 3);
        AtomicInteger attempts = new AtomicInteger(0);

        List<Integer> processed = new ArrayList<>();
        BatchTripProcessor.BatchReport report = processor.process(
                6,
                (offset, limit) -> List.of(offset, offset + 1, offset + 2).subList(0, limit),
                batch -> {
                    int attempt = attempts.incrementAndGet();
                    if (attempt == 2) {
                        throw new IllegalStateException("Transient database timeout");
                    }
                    processed.addAll(batch);
                }
        );

        assertThat(report.retriedBatches()).isEqualTo(1);
        assertThat(report.failedBatches()).isZero();
        assertThat(report.isFullyProcessed()).isTrue();
        assertThat(processed).hasSize(6);
    }

    @Test
    @DisplayName("A permanently failing batch is counted and the run still terminates")
    void testPermanentlyFailingBatchDoesNotHang() {
        BatchTripProcessor<Integer> processor = new BatchTripProcessor<>(2, 2);

        BatchTripProcessor.BatchReport report = processor.process(
                4,
                (offset, limit) -> List.of(offset, offset + 1).subList(0, limit),
                batch -> {
                    if (batch.get(0) == 0) {
                        throw new IllegalStateException("Permanent failure");
                    }
                }
        );

        assertThat(report.batchesStarted()).isEqualTo(2);
        assertThat(report.failedBatches()).isEqualTo(1);
        assertThat(report.retriedBatches()).isEqualTo(2);
        assertThat(report.recordsProcessed()).isEqualTo(2);
        assertThat(report.isFullyProcessed()).isFalse();
    }

    @Test
    @DisplayName("A fetcher returning more than the configured batch size is rejected (memory bound violated)")
    void testFetcherExceedingBatchSizeIsRejected() {
        BatchTripProcessor<Integer> processor = new BatchTripProcessor<>(2, 0);

        assertThatThrownBy(() -> processor.process(
                10,
                (offset, limit) -> List.of(0, 1, 2, 3),
                batch -> { }
        ))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("exceeding configured batch size");
    }
}
