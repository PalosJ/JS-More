package com.palos.jsmore.client.render;

import collinvht.travelers.server.animal.entity.SmartAnimalBase;
import collinvht.travelers.server.animal.obj.locator.ResourceLocator;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.Reader;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import jp.jurassicsaga.server.animal.entity.obj.bases.JSAnimalBase;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;

public final class FloatingModelGeometryResolver {
    private static final ConcurrentHashMap<ResourceLocation, ModelGeometry> CACHE = new ConcurrentHashMap<>();
    private static final ModelGeometry UNKNOWN = new ModelGeometry(
            Double.NaN,
            Double.NaN,
            Double.NaN,
            Double.NaN,
            Double.NaN,
            Double.NaN
    );

    private FloatingModelGeometryResolver() {
    }

    public static double minimumModelY(JSAnimalBase animal) {
        ResourceLocation modelId = resolveModelId(animal);
        return modelId == null
                ? Double.NaN
                : CACHE.computeIfAbsent(modelId, FloatingModelGeometryResolver::loadBounds).minimumY();
    }

    public static double maximumModelY(JSAnimalBase animal) {
        ResourceLocation modelId = resolveModelId(animal);
        return modelId == null
                ? Double.NaN
                : CACHE.computeIfAbsent(modelId, FloatingModelGeometryResolver::loadBounds).maximumY();
    }

    public static ModelFootprint surfaceFootprint(JSAnimalBase animal) {
        float collisionRadius = Math.max(0.20F, animal.getBbWidth() * 0.65F);
        ResourceLocation modelId = resolveModelId(animal);
        ModelGeometry geometry = modelId == null
                ? UNKNOWN
                : CACHE.computeIfAbsent(modelId, FloatingModelGeometryResolver::loadBounds);
        if (!geometry.isValid()) {
            return new ModelFootprint(collisionRadius, collisionRadius);
        }

        double renderScale = Math.max(0.01D, animal.getRenderScale());
        float halfWidth = (float) ((geometry.maximumX() - geometry.minimumX()) * renderScale / 32.0D);
        float halfLength = (float) ((geometry.maximumZ() - geometry.minimumZ()) * renderScale / 32.0D);
        return new ModelFootprint(
                clampFootprintRadius(Math.max(collisionRadius, halfWidth), 6.5F),
                clampFootprintRadius(Math.max(collisionRadius, halfLength), 8.0F)
        );
    }

    public static void clearCache() {
        CACHE.clear();
    }

    @SuppressWarnings("unchecked")
    private static ResourceLocation resolveModelId(JSAnimalBase animal) {
        try {
            ResourceLocator<SmartAnimalBase> locator = (ResourceLocator<SmartAnimalBase>) animal
                    .getAnimal()
                    .getAnimalAttributes()
                    .getEntityBaseProperties()
                    .getLocator();
            return locator == null ? null : locator.getModelLocation(animal);
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private static ModelGeometry loadBounds(ResourceLocation modelId) {
        Optional<Resource> resource = Minecraft.getInstance().getResourceManager().getResource(modelId);
        if (resource.isEmpty()) {
            return UNKNOWN;
        }
        try (Reader reader = resource.get().openAsReader()) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            ModelGeometry geometry = parseGeometry(root);
            return geometry.isValid() ? geometry : UNKNOWN;
        } catch (RuntimeException | IOException exception) {
            return UNKNOWN;
        }
    }

    static ModelGeometry parseGeometry(JsonObject root) {
        JsonArray geometries = root.getAsJsonArray("minecraft:geometry");
        if (geometries == null) {
            return UNKNOWN;
        }
        double minimumX = Double.POSITIVE_INFINITY;
        double minimumY = Double.POSITIVE_INFINITY;
        double minimumZ = Double.POSITIVE_INFINITY;
        double maximumX = Double.NEGATIVE_INFINITY;
        double maximumY = Double.NEGATIVE_INFINITY;
        double maximumZ = Double.NEGATIVE_INFINITY;
        for (JsonElement geometryElement : geometries) {
            JsonArray bones = geometryElement.getAsJsonObject().getAsJsonArray("bones");
            if (bones == null) {
                continue;
            }
            for (JsonElement boneElement : bones) {
                JsonArray cubes = boneElement.getAsJsonObject().getAsJsonArray("cubes");
                if (cubes == null) {
                    continue;
                }
                for (JsonElement cubeElement : cubes) {
                    JsonObject cube = cubeElement.getAsJsonObject();
                    JsonArray origin = cube.getAsJsonArray("origin");
                    JsonArray size = cube.getAsJsonArray("size");
                    if (origin == null || origin.size() < 3 || size == null || size.size() < 3) {
                        continue;
                    }
                    double inflate = cube.has("inflate") ? cube.get("inflate").getAsDouble() : 0.0D;
                    minimumX = Math.min(minimumX, origin.get(0).getAsDouble() - inflate);
                    minimumY = Math.min(minimumY, origin.get(1).getAsDouble() - inflate);
                    minimumZ = Math.min(minimumZ, origin.get(2).getAsDouble() - inflate);
                    maximumX = Math.max(
                            maximumX,
                            origin.get(0).getAsDouble() + size.get(0).getAsDouble() + inflate
                    );
                    maximumY = Math.max(
                            maximumY,
                            origin.get(1).getAsDouble() + size.get(1).getAsDouble() + inflate
                    );
                    maximumZ = Math.max(
                            maximumZ,
                            origin.get(2).getAsDouble() + size.get(2).getAsDouble() + inflate
                    );
                }
            }
        }
        return Double.isFinite(minimumX)
                && Double.isFinite(minimumY)
                && Double.isFinite(minimumZ)
                && Double.isFinite(maximumX)
                && Double.isFinite(maximumY)
                && Double.isFinite(maximumZ)
                ? new ModelGeometry(minimumX, minimumY, minimumZ, maximumX, maximumY, maximumZ)
                : UNKNOWN;
    }

    static double parseMaximumY(JsonObject root) {
        return parseGeometry(root).maximumY();
    }

    private static float clampFootprintRadius(float radius, float maximum) {
        if (!Float.isFinite(radius)) {
            return 0.40F;
        }
        return Math.max(0.40F, Math.min(maximum, radius));
    }

    public record ModelFootprint(float halfWidth, float halfLength) {
        public ModelFootprint {
            halfWidth = clampFootprintRadius(halfWidth, 6.5F);
            halfLength = clampFootprintRadius(halfLength, 8.0F);
        }

        public float representativeWidth() {
            return (float) (Math.sqrt(this.halfWidth * this.halfLength) * 2.0D);
        }
    }

    record ModelGeometry(
            double minimumX,
            double minimumY,
            double minimumZ,
            double maximumX,
            double maximumY,
            double maximumZ
    ) {
        private boolean isValid() {
            return Double.isFinite(this.minimumX)
                    && Double.isFinite(this.minimumY)
                    && Double.isFinite(this.minimumZ)
                    && Double.isFinite(this.maximumX)
                    && Double.isFinite(this.maximumY)
                    && Double.isFinite(this.maximumZ);
        }
    }
}
