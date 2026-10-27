package com.palos.jsmore.server.system.capture;

/** A narrow bridge to the upstream growth calculation; never advances AI or reproduction. */
public interface CapturedGrowthAccess {
    double jsmore$settleGrowthAt(long gameTime, boolean applyHealing);

    void jsmore$settlePendingGrowth();

    default void jsmore$settleGrowthAt(long gameTime) {
        jsmore$settleGrowthAt(gameTime, true);
    }
}
