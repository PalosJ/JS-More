package com.palos.jsmore.server.system.capture;

import com.palos.jsmore.compat.aeronautics.CaptureBoxRelocationState;
import java.util.HashSet;
import java.util.Set;

/** Thread-confined recursion guard for one canonical capture-box domain. */
public final class CaptureBoxRemovalGuard {
    private static final ThreadLocal<Set<CaptureBoxRelocationState.StructureIdentity>> ACTIVE =
            ThreadLocal.withInitial(HashSet::new);

    private CaptureBoxRemovalGuard() {
    }

    public static boolean isActive(CaptureBoxRelocationState.StructureIdentity identity) {
        return identity != null && ACTIVE.get().contains(identity);
    }

    public static Scope open(CaptureBoxRelocationState.StructureIdentity identity) {
        if (identity == null) {
            return Scope.noop();
        }
        Set<CaptureBoxRelocationState.StructureIdentity> active = ACTIVE.get();
        boolean owner = active.add(identity);
        return new Scope(identity, owner);
    }

    public static final class Scope implements AutoCloseable {
        private final CaptureBoxRelocationState.StructureIdentity identity;
        private final boolean owner;
        private boolean closed;

        private Scope(CaptureBoxRelocationState.StructureIdentity identity, boolean owner) {
            this.identity = identity;
            this.owner = owner;
        }

        private static Scope noop() {
            return new Scope(null, false);
        }

        public boolean ownsGuard() {
            return this.owner;
        }

        @Override
        public void close() {
            if (this.closed || !this.owner) {
                return;
            }
            this.closed = true;
            Set<CaptureBoxRelocationState.StructureIdentity> active = ACTIVE.get();
            active.remove(this.identity);
            if (active.isEmpty()) {
                ACTIVE.remove();
            }
        }
    }
}
