package com.palos.jsmore.client;

import com.palos.jsmore.client.render.FloatingModelGeometryResolver;
import com.palos.jsmore.server.system.anesthetic.AnestheticVisualState;
import com.palos.jsmore.server.system.anesthetic.DinosaurAnestheticSystem;
import jp.jurassicsaga.server.animal.entity.obj.bases.JSAnimalBase;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class ClientFloatingEffects {
    private static final ConcurrentMap<UUID, Long> LAST_AMBIENT_EFFECT_TICKS = new ConcurrentHashMap<>();

    private ClientFloatingEffects() {
    }

    public static void onEntityTickPost(EntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof JSAnimalBase animal)
                || !animal.level().isClientSide) {
            return;
        }

        AnestheticVisualState state = DinosaurAnestheticSystem.resolveVisualState(animal);
        if (state == null
                || !state.isBobbing()
                || !Double.isFinite(state.surfaceY())) {
            return;
        }

        long gameTime = animal.level().getGameTime();
        UUID animalId = animal.getUUID();
        FloatingModelGeometryResolver.ModelFootprint footprint =
                FloatingModelGeometryResolver.surfaceFootprint(animal);
        int ambientInterval = SurfaceParticleScaling.ambientInterval(
                animal.getBbWidth(),
                animal.getBbHeight(),
                footprint.representativeWidth()
        );
        Long lastEffectTick = LAST_AMBIENT_EFFECT_TICKS.get(animalId);
        if (lastEffectTick != null
                && gameTime >= lastEffectTick
                && gameTime - lastEffectTick < ambientInterval) {
            return;
        }
        LAST_AMBIENT_EFFECT_TICKS.put(animalId, gameTime);
        ClientSurfaceEffects.spawnAmbientFloating(
                animal,
                state.surfaceY(),
                footprint
        );
    }

    public static void invalidate(UUID animalId) {
        LAST_AMBIENT_EFFECT_TICKS.remove(animalId);
    }

    static void recordSurfaceEvent(UUID animalId, long gameTime) {
        LAST_AMBIENT_EFFECT_TICKS.put(animalId, gameTime);
    }

    public static void clearCache() {
        LAST_AMBIENT_EFFECT_TICKS.clear();
    }
}
