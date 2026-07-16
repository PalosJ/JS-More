package com.palos.jsmore.server.system.worldgen;

import java.util.Map;
import java.util.function.Function;

final class OwnerScopedCache<O, K, V> {
    private volatile State<O, K, V> state = new State<>(null, Map.of());

    Map<K, V> getOrInitialize(O owner, Function<? super O, ? extends Map<K, V>> loader) {
        if (owner == null) {
            return Map.of();
        }
        State<O, K, V> current = this.state;
        if (current.owner() == owner) {
            return current.values();
        }
        synchronized (this) {
            current = this.state;
            if (current.owner() != owner) {
                Map<K, V> loaded = loader.apply(owner);
                current = new State<>(owner, loaded == null ? Map.of() : Map.copyOf(loaded));
                this.state = current;
            }
            return current.values();
        }
    }

    synchronized void clear(O owner) {
        if (owner == null || this.state.owner() == owner) {
            this.state = new State<>(null, Map.of());
        }
    }

    int size() {
        return this.state.values().size();
    }

    private record State<O, K, V>(O owner, Map<K, V> values) {
    }
}
