package com.palos.jsmore.client.system.anesthetic;

import collinvht.travelers.server.animal.TravelersAnimal;
import collinvht.travelers.server.animal.obj.animation.PlayBehaviourType;
import collinvht.travelers.server.animal.obj.animation.TravelersAnimationDefinition;
import collinvht.travelers.server.animal.obj.locator.ResourceLocator;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.palos.jsmore.server.system.anesthetic.DinosaurAnestheticSystem;
import java.io.IOException;
import java.io.Reader;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.WeakHashMap;
import jp.jurassicsaga.server.animal.animations.obj.JSAnimations;
import jp.jurassicsaga.server.animal.entity.obj.bases.JSAnimalBase;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

public final class ClientAnestheticAnimationFallback {
    private static final String STANDARD_SLEEP_IN = "animation.sleep_in";
    private static final String STANDARD_SLEEP_LOOP = "animation.sleep_loop";
    private static final String LEGACY_SLEEP = "animation.sleep";
    private static final Map<ResourceLocation, SleepAnimationCapability> CAPABILITY_CACHE = new HashMap<>();
    private static final GuardStateTracker<JSAnimalBase> ACTIVE_GUARD_STATES = new GuardStateTracker<>();

    private ClientAnestheticAnimationFallback() {
    }

    public static void applyAtRenderHead(JSAnimalBase animal) {
        if (animal == null || !animal.level().isClientSide) {
            return;
        }
        if (!DinosaurAnestheticSystem.shouldUseSleepAnimationGuard(animal)) {
            forgetGuardCapability(animal);
            return;
        }
        ResourceLocation animationLocation = resolveAnimationLocation(animal);
        SleepAnimationCapability capability = resolveCapability(animationLocation);
        SleepAnimationCapability action = ACTIVE_GUARD_STATES.actionFor(
                animal,
                animationLocation,
                capability
        );
        if (action == null || action == SleepAnimationCapability.STANDARD) {
            return;
        }
        if (action == SleepAnimationCapability.LEGACY_SLEEP) {
            LegacySleepAnimationHolder.ANIMATION.sendForEntity(animal);
        } else {
            JSAnimations.IDLE.stopForEntity(animal);
        }
        DinosaurAnestheticSystem.logSleepAnimationTrace(
                animal,
                "client_sleep_animation_fallback",
                "capability=" + action
        );
    }

    public static void onEntityTickPost(EntityTickEvent.Post event) {
        if (event.getEntity() instanceof JSAnimalBase animal
                && animal.level().isClientSide
                && !DinosaurAnestheticSystem.shouldUseSleepAnimationGuard(animal)) {
            forget(animal);
        }
    }

    public static void forget(JSAnimalBase animal) {
        if (animal != null) {
            ACTIVE_GUARD_STATES.forget(animal);
        }
    }

    public static void clearCache() {
        synchronized (CAPABILITY_CACHE) {
            CAPABILITY_CACHE.clear();
        }
        ACTIVE_GUARD_STATES.clear();
    }

    static SleepAnimationCapability classifyAnimationNames(Set<String> animationNames) {
        if (animationNames != null
                && animationNames.contains(STANDARD_SLEEP_IN)
                && animationNames.contains(STANDARD_SLEEP_LOOP)) {
            return SleepAnimationCapability.STANDARD;
        }
        if (animationNames != null && animationNames.contains(LEGACY_SLEEP)) {
            return SleepAnimationCapability.LEGACY_SLEEP;
        }
        return SleepAnimationCapability.NONE;
    }

    static SleepAnimationCapability classifyAnimationJson(Reader reader) {
        if (reader == null) {
            return SleepAnimationCapability.NONE;
        }
        JsonElement rootElement = JsonParser.parseReader(reader);
        if (!rootElement.isJsonObject()) {
            return SleepAnimationCapability.NONE;
        }
        JsonObject animations = rootElement.getAsJsonObject().getAsJsonObject("animations");
        return animations == null
                ? SleepAnimationCapability.NONE
                : classifyAnimationNames(animations.keySet());
    }

    private static SleepAnimationCapability resolveCapability(ResourceLocation animationLocation) {
        if (animationLocation == null) {
            return SleepAnimationCapability.NONE;
        }
        synchronized (CAPABILITY_CACHE) {
            SleepAnimationCapability cached = CAPABILITY_CACHE.get(animationLocation);
            if (cached != null) {
                return cached;
            }
        }

        SleepAnimationCapability resolved = readCapability(animationLocation);
        synchronized (CAPABILITY_CACHE) {
            CAPABILITY_CACHE.put(animationLocation, resolved);
        }
        return resolved;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static ResourceLocation resolveAnimationLocation(JSAnimalBase animal) {
        try {
            TravelersAnimal travelersAnimal = animal.getAnimal();
            if (travelersAnimal == null) {
                return null;
            }
            ResourceLocator locator = travelersAnimal
                    .getAnimalAttributes()
                    .getEntityBaseProperties()
                    .getLocator();
            return locator == null ? null : locator.getAnimationLocation(animal);
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private static SleepAnimationCapability readCapability(ResourceLocation animationLocation) {
        try {
            Resource resource = Minecraft.getInstance()
                    .getResourceManager()
                    .getResource(animationLocation)
                    .orElse(null);
            if (resource == null) {
                return SleepAnimationCapability.NONE;
            }
            try (Reader reader = resource.openAsReader()) {
                return classifyAnimationJson(reader);
            }
        } catch (IOException | RuntimeException exception) {
            return SleepAnimationCapability.NONE;
        }
    }

    private static void forgetGuardCapability(JSAnimalBase animal) {
        forget(animal);
    }

    enum SleepAnimationCapability {
        STANDARD,
        LEGACY_SLEEP,
        NONE
    }

    static final class GuardStateTracker<K> {
        private final Map<K, GuardState> states = new WeakHashMap<>();

        synchronized SleepAnimationCapability actionFor(
                K key,
                ResourceLocation animationLocation,
                SleepAnimationCapability capability
        ) {
            GuardState current = states.get(key);
            if (current != null && Objects.equals(current.animationLocation(), animationLocation)) {
                return null;
            }
            GuardState next = new GuardState(animationLocation, capability);
            states.put(key, next);
            return next.capability();
        }

        synchronized void forget(K key) {
            states.remove(key);
        }

        synchronized void clear() {
            states.clear();
        }
    }

    private record GuardState(
            ResourceLocation animationLocation,
            SleepAnimationCapability capability
    ) {
    }

    private static final class LegacySleepAnimationHolder {
        private static final TravelersAnimationDefinition ANIMATION = new TravelersAnimationDefinition(
                LEGACY_SLEEP,
                JSAnimations.base_controller,
                PlayBehaviourType.LOOP
        ).disableLook().disablePhysics();

        private LegacySleepAnimationHolder() {
        }
    }
}
