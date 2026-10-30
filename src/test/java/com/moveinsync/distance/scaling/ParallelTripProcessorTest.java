package com.moveinsync.distance.scaling;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ParallelTripProcessorTest {

    @Test
    @DisplayName("All trips are processed and results are returned in the exact input order")
    void testResultsRemainInInputOrder() {
        List<Integer> inputs = IntStream.range(0, 500).boxed().toList();

        try (ParallelTripProcessor processor = new ParallelTripProcessor(8)) {
            List<Integer> results = processor.processInOrder(inputs, i -> i * 2);

            assertThat(results).hasSize(inputs.size());
            for (int i = 0; i < inputs.size(); i++) {
                assertThat(results.get(i)).isEqualTo(inputs.get(i) * 2);
            }
        }
    }

    @Test
    @DisplayName("Work is genuinely spread across multiple worker threads")
    void testMultipleWorkersAreUsed() {
        Set<String> threadNames = ConcurrentHashMap.newKeySet();
        List<Integer> inputs = IntStream.range(0, 200).boxed().toList();

        try (ParallelTripProcessor processor = new ParallelTripProcessor(4)) {
            processor.processInOrder(inputs, i -> {
                threadNames.add(Thread.currentThread().getName());
                // Small sleep forces overlap across the worker pool.
                try {
                    Thread.sleep(1L);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                }
                return i;
            });
        }

        assertThat(threadNames).hasSizeGreaterThan(1);
        assertThat(threadNames).allMatch(name -> name.startsWith("trip-worker-"));
    }

    @Test
    @DisplayName("Parallel results are identical to the serial results (deterministic)")
    void testParallelMatchesSerial() {
        List<Integer> inputs = IntStream.range(0, 100).boxed().toList();
        List<Integer> serial = new ArrayList<>();
        for (Integer input : inputs) {
            serial.add(input * 3 + 1);
        }

        try (ParallelTripProcessor processor = new ParallelTripProcessor(4)) {
            List<Integer> parallel = processor.processInOrder(inputs, i -> i * 3 + 1);
            assertThat(parallel).isEqualTo(serial);
        }
    }

    @Test
    @DisplayName("Empty input yields empty output without touching the worker pool")
    void testEmptyInput() {
        try (ParallelTripProcessor processor = new ParallelTripProcessor(2)) {
            assertThat(processor.processInOrder(List.of(), i -> i)).isEmpty();
        }
    }

    @Test
    @DisplayName("A failing trip task surfaces as IllegalStateException with the original cause")
    void testTaskFailureIsPropagated() {
        AtomicInteger counter = new AtomicInteger(0);
        List<Integer> inputs = List.of(1, 2, 3, 4, 5, 6, 7, 8);

        try (ParallelTripProcessor processor = new ParallelTripProcessor(2)) {
            assertThatThrownBy(() -> processor.processInOrder(inputs, i -> {
                if (counter.incrementAndGet() == 3) {
                    throw new IllegalArgumentException("boom");
                }
                return i;
            }))
                    .isInstanceOf(IllegalStateException.class)
                    .hasRootCauseInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    @DisplayName("Worker count must be positive")
    void testInvalidWorkerCount() {
        assertThatThrownBy(() -> new ParallelTripProcessor(0))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
