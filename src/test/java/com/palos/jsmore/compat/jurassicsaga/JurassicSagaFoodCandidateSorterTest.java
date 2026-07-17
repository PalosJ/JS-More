package com.palos.jsmore.compat.jurassicsaga;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class JurassicSagaFoodCandidateSorterTest {
    @Test
    void samplesEveryIdentityOnceAndUsesAStableTotalOrder() {
        Candidate far = new Candidate(1, 9.0D);
        Candidate invalid = new Candidate(0, Double.NaN);
        Candidate positiveInfinity = new Candidate(2, Double.POSITIVE_INFINITY);
        Candidate negativeInfinity = new Candidate(3, Double.NEGATIVE_INFINITY);
        Candidate near = new Candidate(20, 4.0D);
        Candidate tiedNear = new Candidate(5, 4.0D);
        CapturingArrayList<Candidate> candidates = new CapturingArrayList<>();
        candidates.addAll(List.of(far, invalid, positiveInfinity, negativeInfinity, near, tiedNear));
        candidates.add(null);
        candidates.add(near);
        AtomicInteger samples = new AtomicInteger();

        JurassicSagaFoodCandidateSorter.sort(
                candidates,
                Candidate::distanceSquared,
                Candidate::id,
                () -> {
                    samples.incrementAndGet();
                    return 0.0D;
                }
        );

        assertEquals(6, samples.get());
        assertEquals(List.of(tiedNear, near, near, far, invalid), candidates.subList(0, 5));
        assertTrue(candidates.indexOf(positiveInfinity) > candidates.indexOf(far));
        assertTrue(candidates.indexOf(negativeInfinity) > candidates.indexOf(far));
        assertNull(candidates.getLast());
        Comparator<? super Candidate> comparator = candidates.comparator();
        assertEquals(0, comparator.compare(near, near));
        assertEquals(0, comparator.compare(null, null));
        assertTrue(comparator.compare(tiedNear, near) < 0);
        assertTrue(comparator.compare(near, tiedNear) > 0);
        assertTrue(comparator.compare(invalid, null) < 0);
        int repeatedComparison = comparator.compare(tiedNear, far);
        assertEquals(repeatedComparison, comparator.compare(tiedNear, far));
    }

    @Test
    void preSampledJitterRemainsTransitiveDuringTheSort() {
        Candidate first = new Candidate(3, 1.0D);
        Candidate second = new Candidate(2, 4.0D);
        Candidate third = new Candidate(1, 9.0D);
        CapturingArrayList<Candidate> candidates = new CapturingArrayList<>();
        candidates.addAll(List.of(third, first, second));
        double[] samples = {0.99D, 0.01D, 0.50D};
        AtomicInteger nextSample = new AtomicInteger();

        JurassicSagaFoodCandidateSorter.sort(
                candidates,
                Candidate::distanceSquared,
                Candidate::id,
                () -> samples[nextSample.getAndIncrement()]
        );

        assertEquals(3, nextSample.get());
        assertEquals(List.of(first, second, third), candidates);
        Comparator<? super Candidate> comparator = candidates.comparator();
        assertTrue(comparator.compare(first, second) < 0);
        assertTrue(comparator.compare(second, third) < 0);
        assertTrue(comparator.compare(first, third) < 0);
    }

    @Test
    void controlledJitterCanChangeOrderWithoutChangingComparatorResults() {
        Candidate first = new Candidate(1, 100.0D);
        Candidate second = new Candidate(2, 105.0D);
        CapturingArrayList<Candidate> firstOrdering = new CapturingArrayList<>();
        firstOrdering.addAll(List.of(first, second));
        double[] firstSamples = {0.0D, 0.99D};
        AtomicInteger firstIndex = new AtomicInteger();
        JurassicSagaFoodCandidateSorter.sort(
                firstOrdering,
                Candidate::distanceSquared,
                Candidate::id,
                () -> firstSamples[firstIndex.getAndIncrement()]
        );
        assertEquals(List.of(first, second), firstOrdering);

        CapturingArrayList<Candidate> secondOrdering = new CapturingArrayList<>();
        secondOrdering.addAll(List.of(first, second));
        double[] secondSamples = {0.99D, 0.0D};
        AtomicInteger secondIndex = new AtomicInteger();
        JurassicSagaFoodCandidateSorter.sort(
                secondOrdering,
                Candidate::distanceSquared,
                Candidate::id,
                () -> secondSamples[secondIndex.getAndIncrement()]
        );
        assertEquals(List.of(second, first), secondOrdering);
        assertEquals(
                secondOrdering.comparator().compare(first, second),
                secondOrdering.comparator().compare(first, second)
        );
    }

    @Test
    void densePermutationsKeepAContractSafeOrder() {
        List<Candidate> fixture = new ArrayList<>();
        for (int index = 0; index < 96; index++) {
            fixture.add(new Candidate(index, 16.0D + (index % 11)));
        }
        for (int permutation = 0; permutation < 8; permutation++) {
            CapturingArrayList<Candidate> candidates = new CapturingArrayList<>();
            candidates.addAll(fixture);
            Collections.rotate(candidates, permutation * 7);
            AtomicInteger sample = new AtomicInteger(permutation);
            JurassicSagaFoodCandidateSorter.sort(
                    candidates,
                    Candidate::distanceSquared,
                    Candidate::id,
                    () -> (sample.getAndIncrement() % 100) / 100.0D
            );
            Comparator<? super Candidate> comparator = candidates.comparator();
            for (int index = 0; index < candidates.size(); index++) {
                assertEquals(0, comparator.compare(candidates.get(index), candidates.get(index)));
                if (index + 1 < candidates.size()) {
                    assertTrue(comparator.compare(candidates.get(index), candidates.get(index + 1)) <= 0);
                }
            }
        }
    }

    private record Candidate(int id, double distanceSquared) {
    }

    private static final class CapturingArrayList<T> extends ArrayList<T> {
        private Comparator<? super T> comparator;

        @Override
        public void sort(Comparator<? super T> comparator) {
            this.comparator = comparator;
            super.sort(comparator);
        }

        private Comparator<? super T> comparator() {
            return this.comparator;
        }
    }
}
