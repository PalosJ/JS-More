package com.palos.jsmore.server.system.anesthetic;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

public final class AnestheticFloatData {
    public static final StreamCodec<RegistryFriendlyByteBuf, AnestheticFloatData> STREAM_CODEC = StreamCodec.of(
            (buffer, data) -> {
                buffer.writeByte(data.phase.ordinal());
                buffer.writeBoolean(data.waterCaptured);
                buffer.writeBoolean(data.surfaceBroken);
                buffer.writeDouble(data.targetBaseY);
                buffer.writeDouble(data.fluidSurfaceY);
                buffer.writeVarLong(Math.max(0L, data.bobbingStartedAt));
            },
            buffer -> {
                AnestheticFloatData data = new AnestheticFloatData();
                data.phase = Phase.fromOrdinal(buffer.readUnsignedByte());
                data.waterCaptured = buffer.readBoolean();
                data.surfaceBroken = buffer.readBoolean();
                data.targetBaseY = sanitizeCoordinate(buffer.readDouble());
                data.fluidSurfaceY = sanitizeCoordinate(buffer.readDouble());
                data.bobbingStartedAt = Math.max(0L, buffer.readVarLong());
                return data;
            }
    );

    private Phase phase = Phase.IDLE;
    private boolean waterCaptured;
    private boolean surfaceBroken;
    private boolean avianLocked;
    private boolean initialActiveMotionNeutralized;
    private boolean behaviorTasksStopped;
    private long lastProcessedTick = Long.MIN_VALUE;
    private long bobbingStartedAt;
    private long lastEffectTick = Long.MIN_VALUE;
    private int lastBobbingDirection;
    private double targetBaseY = Double.NaN;
    private double fluidSurfaceY = Double.NaN;
    private double anchorX = Double.NaN;
    private double anchorZ = Double.NaN;
    private long surfaceCacheTick = Long.MIN_VALUE;
    private double surfaceCacheX;
    private double surfaceCacheY;
    private double surfaceCacheZ;

    public Phase phase() {
        return this.phase;
    }

    public boolean setPhase(Phase phase) {
        if (this.phase == phase) {
            return false;
        }
        this.phase = phase;
        return true;
    }

    public boolean isFloating() {
        return this.phase == Phase.RISING || this.phase == Phase.BOBBING;
    }

    public boolean waterCaptured() {
        return this.waterCaptured;
    }

    public boolean setWaterCaptured(boolean waterCaptured) {
        if (this.waterCaptured == waterCaptured) {
            return false;
        }
        this.waterCaptured = waterCaptured;
        return true;
    }

    public boolean surfaceBroken() {
        return this.surfaceBroken;
    }

    public boolean setSurfaceBroken(boolean surfaceBroken) {
        if (this.surfaceBroken == surfaceBroken) {
            return false;
        }
        this.surfaceBroken = surfaceBroken;
        return true;
    }

    public boolean avianLocked() {
        return this.avianLocked;
    }

    public void setAvianLocked(boolean avianLocked) {
        this.avianLocked = avianLocked;
    }

    boolean markInitialActiveMotionNeutralized() {
        if (this.initialActiveMotionNeutralized) {
            return false;
        }
        this.initialActiveMotionNeutralized = true;
        return true;
    }

    boolean markBehaviorTasksStopped() {
        if (this.behaviorTasksStopped) {
            return false;
        }
        this.behaviorTasksStopped = true;
        return true;
    }

    public long lastProcessedTick() {
        return this.lastProcessedTick;
    }

    public void setLastProcessedTick(long lastProcessedTick) {
        this.lastProcessedTick = lastProcessedTick;
    }

    public long bobbingStartedAt() {
        return this.bobbingStartedAt;
    }

    public void setBobbingStartedAt(long bobbingStartedAt) {
        this.bobbingStartedAt = bobbingStartedAt;
    }

    public long lastEffectTick() {
        return this.lastEffectTick;
    }

    public void setLastEffectTick(long lastEffectTick) {
        this.lastEffectTick = lastEffectTick;
    }

    public int lastBobbingDirection() {
        return this.lastBobbingDirection;
    }

    public void setLastBobbingDirection(int lastBobbingDirection) {
        this.lastBobbingDirection = lastBobbingDirection;
    }

    public double targetBaseY() {
        return this.targetBaseY;
    }

    public void setTargetBaseY(double targetBaseY) {
        this.targetBaseY = sanitizeCoordinate(targetBaseY);
    }

    public double fluidSurfaceY() {
        return this.fluidSurfaceY;
    }

    public void setFluidSurfaceY(double fluidSurfaceY) {
        this.fluidSurfaceY = sanitizeCoordinate(fluidSurfaceY);
    }

    public boolean hasPositionAnchor() {
        return Double.isFinite(this.anchorX) && Double.isFinite(this.anchorZ);
    }

    public double anchorX() {
        return this.anchorX;
    }

    public double anchorZ() {
        return this.anchorZ;
    }

    public void setPositionAnchor(double x, double z) {
        this.anchorX = sanitizeCoordinate(x);
        this.anchorZ = sanitizeCoordinate(z);
    }

    public void clearPositionAnchor() {
        this.anchorX = Double.NaN;
        this.anchorZ = Double.NaN;
    }

    public boolean hasUsableSurfaceCache(long gameTime, double x, double y, double z) {
        return hasUsableSurfaceCache(gameTime, x, y, z, 5L);
    }

    public boolean hasUsableSurfaceCache(long gameTime, double x, double y, double z, long maxAge) {
        if (!Double.isFinite(this.fluidSurfaceY)
                || gameTime < this.surfaceCacheTick
                || gameTime - this.surfaceCacheTick > Math.max(0L, maxAge)) {
            return false;
        }
        double dx = x - this.surfaceCacheX;
        double dy = y - this.surfaceCacheY;
        double dz = z - this.surfaceCacheZ;
        return dx * dx + dy * dy + dz * dz <= 2.25D;
    }

    public void updateSurfaceCache(long gameTime, double x, double y, double z, double surfaceY) {
        this.surfaceCacheTick = gameTime;
        this.surfaceCacheX = x;
        this.surfaceCacheY = y;
        this.surfaceCacheZ = z;
        this.fluidSurfaceY = sanitizeCoordinate(surfaceY);
    }

    public void clearSurfaceCache() {
        this.surfaceCacheTick = Long.MIN_VALUE;
        this.fluidSurfaceY = Double.NaN;
    }

    public void resetForRelease() {
        this.waterCaptured = false;
        this.surfaceBroken = false;
        this.initialActiveMotionNeutralized = false;
        this.behaviorTasksStopped = false;
        this.bobbingStartedAt = 0L;
        this.lastBobbingDirection = 0;
        this.targetBaseY = Double.NaN;
        clearPositionAnchor();
        clearSurfaceCache();
    }

    public enum Phase {
        IDLE,
        FALLING,
        RISING,
        BOBBING,
        RELEASING;

        private static Phase fromOrdinal(int ordinal) {
            Phase[] values = values();
            return ordinal >= 0 && ordinal < values.length ? values[ordinal] : IDLE;
        }
    }

    private static double sanitizeCoordinate(double coordinate) {
        return Double.isFinite(coordinate) ? coordinate : Double.NaN;
    }
}
