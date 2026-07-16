package com.palos.jsmore.client.system.anesthetic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.palos.jsmore.client.system.anesthetic.ClientAnestheticAnimationFallback.GuardStateTracker;
import com.palos.jsmore.client.system.anesthetic.ClientAnestheticAnimationFallback.SleepAnimationCapability;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

class ClientAnestheticAnimationFallbackTest {
    private static final String ANIMATION_ROOT = "/assets/jurassicsaga/travelers/animations/animal/";

    @Test
    void classifiesOnlyCompleteExactStandardSleepCapability() {
        assertEquals(
                SleepAnimationCapability.STANDARD,
                ClientAnestheticAnimationFallback.classifyAnimationNames(Set.of(
                        "animation.sleep_in",
                        "animation.sleep_loop",
                        "animation.sleep_out"
                ))
        );
        assertEquals(
                SleepAnimationCapability.NONE,
                ClientAnestheticAnimationFallback.classifyAnimationNames(Set.of("animation.sleep_in"))
        );
        assertEquals(
                SleepAnimationCapability.NONE,
                ClientAnestheticAnimationFallback.classifyAnimationNames(Set.of("animation.sleep_loop"))
        );
        assertEquals(
                SleepAnimationCapability.NONE,
                ClientAnestheticAnimationFallback.classifyAnimationNames(Set.of(
                        "animation.sleeping_idle",
                        "animation.sleep_in_rest"
                ))
        );
    }

    @Test
    void classifiesOnlyTheExactLegacySleepLeaf() {
        assertEquals(
                SleepAnimationCapability.LEGACY_SLEEP,
                ClientAnestheticAnimationFallback.classifyAnimationNames(Set.of("animation.sleep"))
        );
        assertEquals(
                SleepAnimationCapability.NONE,
                ClientAnestheticAnimationFallback.classifyAnimationNames(Set.of("animation.legacy_sleep"))
        );
    }

    @Test
    void realJurassicSagaAnimationResourcesCoverAllFallbackModes() throws IOException {
        Map<String, SleepAnimationCapability> expected = Map.ofEntries(
                Map.entry("jp1/tylosaurus/tylosaurus.animation.json", SleepAnimationCapability.NONE),
                Map.entry("jp1/tylosaurus/tylosaurus_baby.animation.json", SleepAnimationCapability.NONE),
                Map.entry("jw4/hainosaurus/hainosaurus.animation.json", SleepAnimationCapability.NONE),
                Map.entry("games_l/bananogmius/bananogmius.animation.json", SleepAnimationCapability.NONE),
                Map.entry("misc_games/oreochima/oreochima.animation.json", SleepAnimationCapability.NONE),
                Map.entry("misc_games/diplomystus/diplomystus.animation.json", SleepAnimationCapability.NONE),
                Map.entry("misc_extant/bonito/bonito.animation.json", SleepAnimationCapability.NONE),
                Map.entry("misc_extant/guapote/guapote.animation.json", SleepAnimationCapability.NONE),
                Map.entry("misc_games/cameroceras/cameroceras.animation.json", SleepAnimationCapability.NONE),
                Map.entry("misc_legacy/mawsonia/mawsonia.animation.json", SleepAnimationCapability.NONE),
                Map.entry("generic/frog.animation.json", SleepAnimationCapability.NONE),
                Map.entry("generic/frog_baby.animation.json", SleepAnimationCapability.NONE),
                Map.entry(
                        "jp_novel/meganeura/meganeura.animation.json",
                        SleepAnimationCapability.LEGACY_SLEEP
                ),
                Map.entry("jp_novel/meganeura/meganeura_baby.animation.json", SleepAnimationCapability.NONE),
                Map.entry(
                        "jp1/triceratops/triceratops.animation.json",
                        SleepAnimationCapability.STANDARD
                )
        );
        for (Map.Entry<String, SleepAnimationCapability> entry : expected.entrySet()) {
            assertEquals(entry.getValue(), classifyResource(entry.getKey()), entry.getKey());
        }
    }

    @Test
    void unchangedResourceProducesOneActionPerGuard() {
        GuardStateTracker<String> tracker = new GuardStateTracker<>();
        ResourceLocation location = animationLocation("none");

        assertEquals(SleepAnimationCapability.NONE, tracker.actionFor(
                "animal",
                location,
                SleepAnimationCapability.NONE
        ));
        assertNull(tracker.actionFor("animal", location, SleepAnimationCapability.NONE));
    }

    @Test
    void resourceChangesReclassifyNoneStandardAndLegacyTransitions() {
        GuardStateTracker<String> tracker = new GuardStateTracker<>();

        assertEquals(SleepAnimationCapability.NONE, tracker.actionFor(
                "none-to-standard",
                animationLocation("none-a"),
                SleepAnimationCapability.NONE
        ));
        assertEquals(SleepAnimationCapability.STANDARD, tracker.actionFor(
                "none-to-standard",
                animationLocation("standard-a"),
                SleepAnimationCapability.STANDARD
        ));

        assertEquals(SleepAnimationCapability.NONE, tracker.actionFor(
                "none-to-legacy",
                animationLocation("none-b"),
                SleepAnimationCapability.NONE
        ));
        assertEquals(SleepAnimationCapability.LEGACY_SLEEP, tracker.actionFor(
                "none-to-legacy",
                animationLocation("legacy-b"),
                SleepAnimationCapability.LEGACY_SLEEP
        ));

        assertEquals(SleepAnimationCapability.STANDARD, tracker.actionFor(
                "standard-to-none",
                animationLocation("standard-c"),
                SleepAnimationCapability.STANDARD
        ));
        assertEquals(SleepAnimationCapability.NONE, tracker.actionFor(
                "standard-to-none",
                animationLocation("none-c"),
                SleepAnimationCapability.NONE
        ));
    }

    @Test
    void offscreenAwakeForgetAndCacheClearAllowTheSameResourceToActAgain() {
        GuardStateTracker<String> tracker = new GuardStateTracker<>();
        ResourceLocation location = animationLocation("none");

        assertEquals(SleepAnimationCapability.NONE, tracker.actionFor(
                "animal",
                location,
                SleepAnimationCapability.NONE
        ));
        tracker.forget("animal");
        assertEquals(SleepAnimationCapability.NONE, tracker.actionFor(
                "animal",
                location,
                SleepAnimationCapability.NONE
        ));
        assertNull(tracker.actionFor("animal", location, SleepAnimationCapability.NONE));
        tracker.clear();
        assertEquals(SleepAnimationCapability.NONE, tracker.actionFor(
                "animal",
                location,
                SleepAnimationCapability.NONE
        ));
    }

    private static SleepAnimationCapability classifyResource(String relativePath) throws IOException {
        String resourcePath = ANIMATION_ROOT + relativePath;
        try (InputStream stream = jurassicSagaAnchor().getResourceAsStream(resourcePath)) {
            assertNotNull(stream, "Jurassic Saga animation resource is missing: " + resourcePath);
            try (Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                return ClientAnestheticAnimationFallback.classifyAnimationJson(reader);
            }
        }
    }

    private static Class<?> jurassicSagaAnchor() {
        try {
            return Class.forName(
                    "jp.jurassicsaga.server.animal.animations.obj.JSAnimations",
                    false,
                    Thread.currentThread().getContextClassLoader()
            );
        } catch (ClassNotFoundException exception) {
            throw new AssertionError("Jurassic Saga 0.2.1 JSAnimations class is unavailable", exception);
        }
    }

    private static ResourceLocation animationLocation(String path) {
        return ResourceLocation.fromNamespaceAndPath("jsmore", "test/" + path);
    }
}
