package com.palos.jsmore.gametest;

import com.palos.jsmore.JSMore;
import java.util.List;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Locks the absence contract of the minimal runtime profile. */
@GameTestHolder(JSMore.MOD_ID)
@PrefixGameTestTemplate(false)
public final class RuntimeProfileGameTests {
    public static final String PROFILE_PROPERTY = "jsmore.test.runtime-profile";
    private static final List<String> MINIMAL_EXCLUDED_MODS = List.of(
            "curios",
            "jade",
            "terrablender",
            "aeronautics_bundled",
            "simulated",
            "sable",
            "create"
    );

    private RuntimeProfileGameTests() {
    }

    @GameTest(template = "profile_compatibility", timeoutTicks = 100)
    public static void minimalRuntimeDoesNotLoadOptionalIntegrations(GameTestHelper helper) {
        String profile = System.getProperty(PROFILE_PROPERTY, "default");
        if (!"minimal".equals(profile)) {
            helper.succeed();
            return;
        }
        List<String> loaded = MINIMAL_EXCLUDED_MODS.stream()
                .filter(modId -> ModList.get().isLoaded(modId))
                .toList();
        if (!loaded.isEmpty()) {
            helper.fail("Minimal runtime loaded optional integration mods: " + String.join(", ", loaded));
            return;
        }
        JSMore.LOGGER.info("JS More minimal runtime optional integrations absent: {}", MINIMAL_EXCLUDED_MODS);
        helper.succeed();
    }
}
