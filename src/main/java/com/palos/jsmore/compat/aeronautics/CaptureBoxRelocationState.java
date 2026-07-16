package com.palos.jsmore.compat.aeronautics;

import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

/**
 * Ephemeral relocation flags shared by capture-box lifecycle code and the optional Aeronautics adapter.
 */
public final class CaptureBoxRelocationState {
    public static final int MAX_ACTIVE_IDENTITIES = 64;

    private static final Object LOCK = new Object();
    private static final Map<Object, Map<StructureIdentity, Counts>> ACTIVE_BY_LEVEL = new IdentityHashMap<>();
    private static int activeIdentities;

    private CaptureBoxRelocationState() {
    }

    public static Scope open(Level level, StructureIdentity identity, State state) {
        return openInternal(Objects.requireNonNull(level, "level"), identity, state);
    }

    public static Scope open(Level level, BlockPos controller, ResourceLocation kindToken, State state) {
        Objects.requireNonNull(level, "level");
        Optional<CaptureBoxWorldContext.SpaceIdentity> spaceIdentity =
                CaptureBoxWorldContext.identify(level, controller);
        if (spaceIdentity.isEmpty()) {
            throw new IllegalStateException("capture-box world identity is unavailable");
        }
        return open(level, new StructureIdentity(spaceIdentity.orElseThrow(), controller, kindToken), state);
    }

    public static State query(Level level, StructureIdentity identity) {
        return queryInternal(Objects.requireNonNull(level, "level"), identity);
    }

    public static State query(Level level, BlockPos controller, ResourceLocation kindToken) {
        Objects.requireNonNull(level, "level");
        return CaptureBoxWorldContext.identify(level, controller)
                .map(identity -> query(level, new StructureIdentity(identity, controller, kindToken)))
                .orElse(State.NONE);
    }

    public static boolean isProvisional(Level level, StructureIdentity identity) {
        return query(level, identity).provisional();
    }

    public static boolean suppressRemoval(Level level, StructureIdentity identity) {
        return query(level, identity).suppressRemoval();
    }

    public static boolean suppressDrops(Level level, StructureIdentity identity) {
        return query(level, identity).suppressDrops();
    }

    public static void clearAll() {
        synchronized (LOCK) {
            ACTIVE_BY_LEVEL.clear();
            activeIdentities = 0;
        }
    }

    static Scope openForTesting(Object levelIdentity, StructureIdentity identity, State state) {
        return openInternal(Objects.requireNonNull(levelIdentity, "levelIdentity"), identity, state);
    }

    static State queryForTesting(Object levelIdentity, StructureIdentity identity) {
        return queryInternal(Objects.requireNonNull(levelIdentity, "levelIdentity"), identity);
    }

    static int activeIdentityCountForTesting() {
        synchronized (LOCK) {
            return activeIdentities;
        }
    }

    private static Scope openInternal(Object levelIdentity, StructureIdentity identity, State state) {
        Objects.requireNonNull(identity, "identity");
        Objects.requireNonNull(state, "state");
        if (State.NONE.equals(state)) {
            return Scope.noop();
        }
        synchronized (LOCK) {
            Map<StructureIdentity, Counts> levelStates = ACTIVE_BY_LEVEL.computeIfAbsent(
                    levelIdentity,
                    ignored -> new HashMap<>()
            );
            Counts counts = levelStates.get(identity);
            if (counts == null) {
                if (activeIdentities >= MAX_ACTIVE_IDENTITIES) {
                    if (levelStates.isEmpty()) {
                        ACTIVE_BY_LEVEL.remove(levelIdentity);
                    }
                    throw new IllegalStateException("too many active capture-box relocation identities");
                }
                counts = new Counts();
                levelStates.put(identity, counts);
                activeIdentities++;
            }
            counts.add(state);
        }
        return new Scope(levelIdentity, identity, state, false);
    }

    private static State queryInternal(Object levelIdentity, StructureIdentity identity) {
        Objects.requireNonNull(identity, "identity");
        synchronized (LOCK) {
            Map<StructureIdentity, Counts> levelStates = ACTIVE_BY_LEVEL.get(levelIdentity);
            Counts counts = levelStates == null ? null : levelStates.get(identity);
            return counts == null ? State.NONE : counts.state();
        }
    }

    private static void close(Object levelIdentity, StructureIdentity identity, State state) {
        synchronized (LOCK) {
            Map<StructureIdentity, Counts> levelStates = ACTIVE_BY_LEVEL.get(levelIdentity);
            if (levelStates == null) {
                return;
            }
            Counts counts = levelStates.get(identity);
            if (counts == null) {
                return;
            }
            counts.remove(state);
            if (counts.empty()) {
                levelStates.remove(identity);
                activeIdentities--;
            }
            if (levelStates.isEmpty()) {
                ACTIVE_BY_LEVEL.remove(levelIdentity);
            }
        }
    }

    public record StructureIdentity(
            CaptureBoxWorldContext.SpaceIdentity spaceIdentity,
            BlockPos controller,
            ResourceLocation kindToken
    ) {
        public StructureIdentity {
            Objects.requireNonNull(spaceIdentity, "spaceIdentity");
            controller = Objects.requireNonNull(controller, "controller").immutable();
            Objects.requireNonNull(kindToken, "kindToken");
        }
    }

    public record State(boolean provisional, boolean suppressRemoval, boolean suppressDrops) {
        public static final State NONE = new State(false, false, false);
        public static final State PROVISIONAL = new State(true, true, true);
    }

    public static final class Scope implements AutoCloseable {
        private final Object levelIdentity;
        private final StructureIdentity identity;
        private final State state;
        private final boolean noop;
        private boolean closed;

        private Scope(Object levelIdentity, StructureIdentity identity, State state, boolean noop) {
            this.levelIdentity = levelIdentity;
            this.identity = identity;
            this.state = state;
            this.noop = noop;
        }

        private static Scope noop() {
            return new Scope(null, null, State.NONE, true);
        }

        @Override
        public void close() {
            if (this.closed) {
                return;
            }
            this.closed = true;
            if (!this.noop) {
                CaptureBoxRelocationState.close(this.levelIdentity, this.identity, this.state);
            }
        }
    }

    private static final class Counts {
        private int scopes;
        private int provisional;
        private int suppressRemoval;
        private int suppressDrops;

        private void add(State state) {
            this.scopes++;
            if (state.provisional()) {
                this.provisional++;
            }
            if (state.suppressRemoval()) {
                this.suppressRemoval++;
            }
            if (state.suppressDrops()) {
                this.suppressDrops++;
            }
        }

        private void remove(State state) {
            if (this.scopes <= 0) {
                return;
            }
            this.scopes--;
            if (state.provisional() && this.provisional > 0) {
                this.provisional--;
            }
            if (state.suppressRemoval() && this.suppressRemoval > 0) {
                this.suppressRemoval--;
            }
            if (state.suppressDrops() && this.suppressDrops > 0) {
                this.suppressDrops--;
            }
        }

        private State state() {
            return new State(this.provisional > 0, this.suppressRemoval > 0, this.suppressDrops > 0);
        }

        private boolean empty() {
            return this.scopes == 0;
        }
    }
}
