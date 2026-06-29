package com.palos.jsrevise.system.observation;

import com.palos.jsrevise.server.system.age.DinosaurAgeEstimate;
import com.palos.jsrevise.server.system.age.DinosaurAgeSystem;
import com.palos.jsrevise.server.system.anesthetic.DinosaurAnestheticSystem;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalLong;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import jp.jurassicsaga.server.animal.entity.obj.bases.JSAnimalBase;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class DinosaurObservationSystem {
    private static final long CACHE_TICKS = 5L;
    private static final long CACHE_EXPIRY_TICKS = 200L;
    private static final int MAX_CACHE_ENTRIES = 2048;
    private static final ConcurrentHashMap<CacheKey, CachedSnapshot> SNAPSHOTS = new ConcurrentHashMap<>();
    private static final AtomicLong NEXT_CLEANUP_TICK = new AtomicLong();

    private DinosaurObservationSystem() {
    }

    public static DinosaurObservationSnapshot capture(JSAnimalBase animal) {
        long gameTime = animal.level().getGameTime();
        CacheKey key = new CacheKey(animal.getUUID(), animal.level().dimension().location());
        CachedSnapshot cached = SNAPSHOTS.get(key);
        if (cached != null && gameTime >= cached.capturedAt() && gameTime - cached.capturedAt() <= CACHE_TICKS) {
            return cached.snapshot();
        }
        DinosaurObservationSnapshot snapshot = captureFresh(animal);
        SNAPSHOTS.put(key, new CachedSnapshot(gameTime, snapshot));
        cleanExpiredEntries(gameTime);
        return snapshot;
    }

    public static void invalidate(UUID entityId) {
        if (entityId != null) {
            SNAPSHOTS.keySet().removeIf(key -> key.entityId().equals(entityId));
        }
    }

    public static void clearCache() {
        SNAPSHOTS.clear();
        NEXT_CLEANUP_TICK.set(0L);
    }

    private static DinosaurObservationSnapshot captureFresh(JSAnimalBase animal) {
        DinosaurAgeEstimate ageEstimate = DinosaurAgeSystem.estimate(animal);
        Object metabolismModule = resolveMetabolismModule(animal);
        Object geneticModule = resolveGeneticModule(animal);
        long pendingTicks = DinosaurAnestheticSystem.getPendingAnestheticTicks(animal);
        long remainingTicks = DinosaurAnestheticSystem.getRemainingAnestheticTicks(animal);
        long queuedTicks = DinosaurAnestheticSystem.getQueuedAnestheticTicks(animal);

        return new DinosaurObservationSnapshot(
                animal.getDisplayName(),
                ageEstimate,
                OptionalDouble.of(Math.max(0.0D, animal.getHealth())),
                OptionalDouble.of(Math.max(0.0D, animal.getMaxHealth())),
                resolveGender(geneticModule),
                ReflectionAccessCache.invokePercentage(metabolismModule, "hungerPercentage"),
                resolveThirstPercentage(metabolismModule),
                resolveMoodPercentage(metabolismModule),
                pendingTicks > 0L ? OptionalLong.of(pendingTicks) : OptionalLong.empty(),
                remainingTicks > 0L ? OptionalLong.of(remainingTicks) : OptionalLong.empty(),
                queuedTicks > 0L ? OptionalLong.of(queuedTicks) : OptionalLong.empty(),
                Optional.empty(),
                GeneObservationResolver.resolve(geneticModule)
        );
    }

    public static boolean isWithinObservationRange(Vec3 observer, AABB bounds) {
        double closestX = Mth.clamp(observer.x, bounds.minX, bounds.maxX);
        double closestY = Mth.clamp(observer.y, bounds.minY, bounds.maxY);
        double closestZ = Mth.clamp(observer.z, bounds.minZ, bounds.maxZ);
        return observer.distanceToSqr(closestX, closestY, closestZ) <= 64.0D;
    }

    private static Object resolveMetabolismModule(JSAnimalBase animal) {
        try {
            return animal.getModules().getMetabolismModule();
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private static Object resolveGeneticModule(JSAnimalBase animal) {
        try {
            return animal.getModules().getGeneticModule();
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private static Optional<Boolean> resolveGender(Object geneticModule) {
        Object value = ReflectionAccessCache.invoke(geneticModule, "isMale");
        return value instanceof Boolean male ? Optional.of(male) : Optional.empty();
    }

    private static OptionalDouble resolveThirstPercentage(Object metabolismModule) {
        OptionalDouble thirst = ReflectionAccessCache.invokePercentage(metabolismModule, "thirstPercentage");
        if (thirst.isPresent()) {
            return thirst;
        }
        thirst = ReflectionAccessCache.invokePercentage(metabolismModule, "waterPercentage");
        return thirst.isPresent()
                ? thirst
                : ReflectionAccessCache.invokePercentage(metabolismModule, "staminaPercentage");
    }

    private static OptionalDouble resolveMoodPercentage(Object metabolismModule) {
        OptionalDouble frustration = ReflectionAccessCache.invokePercentage(
                metabolismModule,
                "frustrationPercentage"
        );
        return frustration.isPresent()
                ? OptionalDouble.of(Math.max(0.0D, 100.0D - frustration.getAsDouble()))
                : OptionalDouble.empty();
    }

    private static void cleanExpiredEntries(long gameTime) {
        long nextCleanup = NEXT_CLEANUP_TICK.get();
        if (SNAPSHOTS.size() <= MAX_CACHE_ENTRIES && gameTime < nextCleanup) {
            return;
        }
        if (!NEXT_CLEANUP_TICK.compareAndSet(nextCleanup, gameTime + CACHE_EXPIRY_TICKS)) {
            return;
        }
        SNAPSHOTS.entrySet().removeIf(entry -> {
            long capturedAt = entry.getValue().capturedAt();
            return gameTime < capturedAt || gameTime - capturedAt > CACHE_EXPIRY_TICKS;
        });
        if (SNAPSHOTS.size() > MAX_CACHE_ENTRIES) {
            SNAPSHOTS.clear();
        }
    }

    private record CacheKey(UUID entityId, ResourceLocation dimensionId) {
    }

    private record CachedSnapshot(long capturedAt, DinosaurObservationSnapshot snapshot) {
    }
}
