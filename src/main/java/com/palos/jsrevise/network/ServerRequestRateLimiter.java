package com.palos.jsrevise.network;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

final class ServerRequestRateLimiter {
    private final long cooldownTicks;
    private final int maxTrackers;
    private final Map<UUID, Tracker> trackers = new HashMap<>();

    ServerRequestRateLimiter(long cooldownTicks, int maxTrackers) {
        this.cooldownTicks = Math.max(0L, cooldownTicks);
        this.maxTrackers = Math.max(1, maxTrackers);
    }

    boolean allow(UUID playerId, long gameTime) {
        if (playerId == null) {
            return false;
        }
        synchronized (this.trackers) {
            cleanupExpired(gameTime);
            Tracker tracker = this.trackers.get(playerId);
            if (tracker != null && gameTime < tracker.nextAllowedTick()) {
                this.trackers.put(playerId, new Tracker(tracker.nextAllowedTick(), gameTime));
                return false;
            }
            if (!this.trackers.containsKey(playerId) && this.trackers.size() >= this.maxTrackers) {
                removeOneTracker();
            }
            this.trackers.put(playerId, new Tracker(nextAllowedTick(gameTime), gameTime));
            return true;
        }
    }

    void clear() {
        synchronized (this.trackers) {
            this.trackers.clear();
        }
    }

    int size() {
        synchronized (this.trackers) {
            return this.trackers.size();
        }
    }

    private void cleanupExpired(long gameTime) {
        Iterator<Map.Entry<UUID, Tracker>> iterator = this.trackers.entrySet().iterator();
        while (iterator.hasNext()) {
            Tracker tracker = iterator.next().getValue();
            if (tracker == null || gameTime < tracker.lastSeenTick() || tracker.nextAllowedTick() <= gameTime) {
                iterator.remove();
            }
        }
    }

    private void removeOneTracker() {
        Iterator<UUID> iterator = this.trackers.keySet().iterator();
        if (iterator.hasNext()) {
            iterator.next();
            iterator.remove();
        }
    }

    private long nextAllowedTick(long gameTime) {
        return gameTime > Long.MAX_VALUE - this.cooldownTicks
                ? Long.MAX_VALUE
                : gameTime + this.cooldownTicks;
    }

    private record Tracker(long nextAllowedTick, long lastSeenTick) {
    }
}
