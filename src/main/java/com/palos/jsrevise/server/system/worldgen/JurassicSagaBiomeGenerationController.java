package com.palos.jsrevise.server.system.worldgen;

import static java.util.Map.entry;

import com.palos.jsrevise.config.JSReviseConfig;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.biome.Biome;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

public final class JurassicSagaBiomeGenerationController {
    private static final String JURASSIC_SAGA_NAMESPACE = "jurassicsaga";
    private static final Map<String, ResourceLocation> BIOME_REPLACEMENTS = Map.ofEntries(
            entry("burnt_forest", ResourceLocation.withDefaultNamespace("forest")),
            entry("grassy_plains", ResourceLocation.withDefaultNamespace("plains")),
            entry("mediterranean_scrub_forest", ResourceLocation.withDefaultNamespace("forest")),
            entry("mediterranean_scrub_plains", ResourceLocation.withDefaultNamespace("plains")),
            entry("redwood", ResourceLocation.withDefaultNamespace("old_growth_pine_taiga")),
            entry("redwood_plains", ResourceLocation.withDefaultNamespace("meadow")),
            entry("sulphur_springs", ResourceLocation.withDefaultNamespace("windswept_hills")),
            entry("trench", ResourceLocation.withDefaultNamespace("deep_lukewarm_ocean"))
    );
    private static final OwnerScopedCache<MinecraftServer, String, Holder<Biome>> REPLACEMENT_CACHE =
            new OwnerScopedCache<>();

    private JurassicSagaBiomeGenerationController() {
    }

    public static Holder<Biome> replaceIfDisabled(Holder<Biome> biomeHolder) {
        if (!isBiomeGenerationDisabled()) {
            return biomeHolder;
        }

        ResourceLocation biomeId = biomeHolder.unwrapKey()
                .map(ResourceKey::location)
                .orElse(null);
        if (biomeId == null || !JURASSIC_SAGA_NAMESPACE.equals(biomeId.getNamespace())) {
            return biomeHolder;
        }

        if (!BIOME_REPLACEMENTS.containsKey(biomeId.getPath())) {
            return biomeHolder;
        }

        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            return biomeHolder;
        }

        Holder<Biome> fallbackHolder = REPLACEMENT_CACHE
                .getOrInitialize(server, JurassicSagaBiomeGenerationController::createReplacementCache)
                .get(biomeId.getPath());
        return fallbackHolder != null ? fallbackHolder : biomeHolder;
    }

    public static boolean isBiomeGenerationDisabled() {
        return JSReviseConfig.DISABLE_JURASSIC_SAGA_BIOME_GENERATION.get();
    }

    public static void clearCache(MinecraftServer server) {
        REPLACEMENT_CACHE.clear(server);
    }

    static boolean hasReplacementForJurassicSagaBiome(String biomePath) {
        return BIOME_REPLACEMENTS.containsKey(biomePath);
    }

    static ResourceLocation replacementForJurassicSagaBiome(String biomePath) {
        return BIOME_REPLACEMENTS.get(biomePath);
    }

    private static Map<String, Holder<Biome>> createReplacementCache(MinecraftServer server) {
        Map<String, Holder<Biome>> replacements = new HashMap<>();
        var biomeRegistry = server.registryAccess().lookupOrThrow(Registries.BIOME);
        BIOME_REPLACEMENTS.forEach((jurassicSagaPath, fallbackBiomeId) -> biomeRegistry
                .get(ResourceKey.create(Registries.BIOME, fallbackBiomeId))
                .ifPresent(holder -> replacements.put(jurassicSagaPath, holder)));
        return replacements;
    }
}
