package com.palos.jsmore.compat.jurassicsaga;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.DoubleSupplier;
import java.util.function.ToDoubleFunction;
import java.util.function.ToIntFunction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/** Pre-samples Jurassic Saga food-candidate jitter so the sort comparator remains stable. */
public final class JurassicSagaFoodCandidateSorter {
    private static final double JITTER = 0.25D;

    private JurassicSagaFoodCandidateSorter() {
    }

    public static void sort(ArrayList<Entity> candidates, Vec3 origin) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        sort(
                candidates,
                candidate -> origin.distanceToSqr(candidate.position()),
                Entity::getId,
                random::nextDouble
        );
    }

    static <T> void sort(
            ArrayList<T> candidates,
            ToDoubleFunction<T> distanceSquared,
            ToIntFunction<T> entityId,
            DoubleSupplier randomSample
    ) {
        IdentityHashMap<T, CandidateScore> scores = new IdentityHashMap<>();
        for (T candidate : candidates) {
            if (candidate == null || scores.containsKey(candidate)) {
                continue;
            }
            double distance = distanceSquared.applyAsDouble(candidate);
            double sample = randomSample.getAsDouble();
            double score = distance * (1.0D + sample * JITTER);
            if (!Double.isFinite(distance) || distance < 0.0D || !Double.isFinite(score)) {
                score = Double.POSITIVE_INFINITY;
            }
            scores.put(candidate, new CandidateScore(score, entityId.applyAsInt(candidate)));
        }

        candidates.sort(stableComparator(scores));
    }

    private static <T> Comparator<T> stableComparator(IdentityHashMap<T, CandidateScore> scores) {
        return (left, right) -> {
            if (left == right) {
                return 0;
            }
            if (left == null) {
                return 1;
            }
            if (right == null) {
                return -1;
            }

            CandidateScore leftScore = scores.get(left);
            CandidateScore rightScore = scores.get(right);
            int scoreComparison = Double.compare(leftScore.score(), rightScore.score());
            return scoreComparison != 0
                    ? scoreComparison
                    : Integer.compare(leftScore.entityId(), rightScore.entityId());
        };
    }

    private record CandidateScore(double score, int entityId) {
    }
}
