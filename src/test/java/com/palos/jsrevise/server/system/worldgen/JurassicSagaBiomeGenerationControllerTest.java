package com.palos.jsrevise.server.system.worldgen;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;
import jp.jurassicsaga.server.world.biome.JSBiome;
import jp.jurassicsaga.server.world.biome.JSBiomes;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

class JurassicSagaBiomeGenerationControllerTest {
    @Test
    void coversEveryCurrentJurassicSagaBiome() {
        List<String> missing = Arrays.stream(JSBiomes.class.getDeclaredFields())
                .filter(field -> Modifier.isStatic(field.getModifiers()))
                .filter(field -> JSBiome.class.isAssignableFrom(field.getType()))
                .map(field -> {
                    try {
                        return ((JSBiome) field.get(null)).getBiomeKey().location().getPath();
                    } catch (IllegalAccessException exception) {
                        throw new AssertionError("Cannot read Jurassic Saga biome field " + field.getName(), exception);
                    }
                })
                .filter(path -> !JurassicSagaBiomeGenerationController.hasReplacementForJurassicSagaBiome(path))
                .toList();

        assertEquals(List.of(), missing);
    }

    @Test
    void sulphurSpringsFallsBackToWindsweptHills() {
        assertEquals(
                ResourceLocation.withDefaultNamespace("windswept_hills"),
                JurassicSagaBiomeGenerationController.replacementForJurassicSagaBiome("sulphur_springs")
        );
    }
}
