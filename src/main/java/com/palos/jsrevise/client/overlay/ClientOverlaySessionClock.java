package com.palos.jsrevise.client.overlay;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * Monotonic client-session time for presentation caches that must not follow server game-time corrections.
 */
public final class ClientOverlaySessionClock {
    private static Object activeLevelIdentity;
    private static ResourceLocation activeDimensionId;
    private static long generation;
    private static long logicalTick;
    private static Stamp activeStamp;

    private ClientOverlaySessionClock() {
    }

    static Stamp current(Level level) {
        if (level == null) {
            return null;
        }
        return current(level, level.dimension().location());
    }

    static Stamp current(Object levelIdentity, ResourceLocation dimensionId) {
        if (levelIdentity == null || dimensionId == null) {
            return null;
        }
        bind(levelIdentity, dimensionId);
        return activeStamp;
    }

    public static void onPostTick(ClientTickEvent.Post event) {
        if (event == null) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        Level level = minecraft.level;
        if (level != null) {
            advance(level, level.dimension().location());
        }
    }

    static Stamp advance(Object levelIdentity, ResourceLocation dimensionId) {
        if (levelIdentity == null || dimensionId == null) {
            return null;
        }
        bind(levelIdentity, dimensionId);
        logicalTick = saturatedIncrement(logicalTick);
        activeStamp = new Stamp(generation, activeDimensionId, logicalTick);
        return activeStamp;
    }

    public static void clear() {
        generation = saturatedIncrement(generation);
        activeLevelIdentity = null;
        activeDimensionId = null;
        logicalTick = 0L;
        activeStamp = null;
    }

    static long saturatedIncrement(long value) {
        return value == Long.MAX_VALUE ? Long.MAX_VALUE : value + 1L;
    }

    private static void bind(Object levelIdentity, ResourceLocation dimensionId) {
        if (activeLevelIdentity == null) {
            activeLevelIdentity = levelIdentity;
            activeDimensionId = dimensionId;
            logicalTick = 0L;
            activeStamp = new Stamp(generation, dimensionId, logicalTick);
            return;
        }
        if (activeLevelIdentity == levelIdentity && activeDimensionId.equals(dimensionId)) {
            return;
        }
        generation = saturatedIncrement(generation);
        activeLevelIdentity = levelIdentity;
        activeDimensionId = dimensionId;
        logicalTick = 0L;
        activeStamp = new Stamp(generation, dimensionId, logicalTick);
    }

    public record Stamp(long generation, ResourceLocation dimensionId, long logicalTick) {
        public Stamp {
            if (dimensionId == null) {
                throw new IllegalArgumentException("Dimension id is required");
            }
            logicalTick = Math.max(0L, logicalTick);
        }
    }
}
