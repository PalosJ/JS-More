package com.palos.jsmore.network;

import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;

public final class ServerRequestRateLimiters {
    private static final long SERVER_REQUEST_COOLDOWN_TICKS = 5L;
    private static final int MAX_REQUEST_TRACKERS = 2048;
    private static final ServerRequestRateLimiter CAPTURE_CAGE_OBSERVATION =
            new ServerRequestRateLimiter(SERVER_REQUEST_COOLDOWN_TICKS, MAX_REQUEST_TRACKERS);
    private static final ServerRequestRateLimiter EGG_LAYING_PROGRESS =
            new ServerRequestRateLimiter(SERVER_REQUEST_COOLDOWN_TICKS, MAX_REQUEST_TRACKERS);

    private ServerRequestRateLimiters() {
    }

    public static boolean allowCaptureCageObservation(ServerPlayer player) {
        return player != null && allowCaptureCageObservation(player.getUUID(), player.level().getGameTime());
    }

    public static boolean allowEggLayingProgress(ServerPlayer player) {
        return player != null && allowEggLayingProgress(player.getUUID(), player.level().getGameTime());
    }

    static boolean allowCaptureCageObservation(UUID playerId, long gameTime) {
        return CAPTURE_CAGE_OBSERVATION.allow(playerId, gameTime);
    }

    static boolean allowEggLayingProgress(UUID playerId, long gameTime) {
        return EGG_LAYING_PROGRESS.allow(playerId, gameTime);
    }

    static int captureCageObservationTrackerCount() {
        return CAPTURE_CAGE_OBSERVATION.size();
    }

    static int eggLayingProgressTrackerCount() {
        return EGG_LAYING_PROGRESS.size();
    }

    public static void clearAll() {
        CAPTURE_CAGE_OBSERVATION.clear();
        EGG_LAYING_PROGRESS.clear();
    }
}
