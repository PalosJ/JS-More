package com.palos.jsrevise.server.system.capture;

import java.util.Objects;
import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

/** Lossless persistence state for the broken capture box's optional world debris. */
public final class BrokenCaptureBoxDebrisData {
    public static final String TAG_KEY = "JSReviseDebrisDetached";
    private static final byte ATTACHED_VALUE = 0;
    private static final byte DETACHED_VALUE = 1;
    private static final BrokenCaptureBoxDebrisData ABSENT = new BrokenCaptureBoxDebrisData(State.ABSENT, null);
    private static final BrokenCaptureBoxDebrisData ATTACHED =
            new BrokenCaptureBoxDebrisData(State.VALID_FALSE, null);
    private static final BrokenCaptureBoxDebrisData DETACHED =
            new BrokenCaptureBoxDebrisData(State.VALID_TRUE, null);

    private final State state;
    @Nullable
    private final Tag malformedRaw;

    private BrokenCaptureBoxDebrisData(State state, @Nullable Tag malformedRaw) {
        this.state = Objects.requireNonNull(state, "state");
        this.malformedRaw = malformedRaw == null ? null : malformedRaw.copy();
    }

    public static BrokenCaptureBoxDebrisData absent() {
        return ABSENT;
    }

    public static BrokenCaptureBoxDebrisData inspect(CompoundTag metadata) {
        Objects.requireNonNull(metadata, "metadata");
        Tag raw = metadata.get(TAG_KEY);
        if (raw == null) {
            return ABSENT;
        }
        if (raw.getId() != Tag.TAG_BYTE) {
            return malformed(raw);
        }
        byte value = metadata.getByte(TAG_KEY);
        if (value == ATTACHED_VALUE) {
            return ATTACHED;
        }
        if (value == DETACHED_VALUE) {
            return DETACHED;
        }
        return malformed(raw);
    }

    private static BrokenCaptureBoxDebrisData malformed(Tag raw) {
        return new BrokenCaptureBoxDebrisData(State.MALFORMED, Objects.requireNonNull(raw, "raw"));
    }

    public State state() {
        return this.state;
    }

    public boolean shouldRenderDebris() {
        return this.state == State.ABSENT || this.state == State.VALID_FALSE;
    }

    public boolean malformed() {
        return this.state == State.MALFORMED;
    }

    /** Ordinary gameplay may only make visible debris permanently detached. */
    public BrokenCaptureBoxDebrisData detach() {
        return switch (this.state) {
            case ABSENT, VALID_FALSE -> DETACHED;
            case VALID_TRUE -> this;
            case MALFORMED -> this;
        };
    }

    /** Relocation projection preserves malformed raw data instead of overwriting a possible future format. */
    public BrokenCaptureBoxDebrisData detachedProjection() {
        return this.state == State.MALFORMED ? this : DETACHED;
    }

    /** Writes this exact state while preserving malformed values byte-for-byte. */
    public void writeTo(CompoundTag metadata) {
        Objects.requireNonNull(metadata, "metadata");
        switch (this.state) {
            case ABSENT -> metadata.remove(TAG_KEY);
            case VALID_FALSE -> metadata.putByte(TAG_KEY, ATTACHED_VALUE);
            case VALID_TRUE -> metadata.putByte(TAG_KEY, DETACHED_VALUE);
            case MALFORMED -> metadata.put(TAG_KEY, Objects.requireNonNull(this.malformedRaw).copy());
        }
    }

    @Nullable
    public Tag malformedRawCopy() {
        return this.malformedRaw == null ? null : this.malformedRaw.copy();
    }

    public enum State {
        ABSENT,
        VALID_FALSE,
        VALID_TRUE,
        MALFORMED
    }
}
