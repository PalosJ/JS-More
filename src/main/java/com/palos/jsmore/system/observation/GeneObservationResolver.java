package com.palos.jsmore.system.observation;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;

final class GeneObservationResolver {
    private GeneObservationResolver() {
    }

    static List<ObservedGene> resolve(Object geneticModule) {
        Object geneData = ReflectionAccessCache.invoke(geneticModule, "getGeneData");
        if (geneData == null) {
            return List.of();
        }
        Object holder = ReflectionAccessCache.readField(geneData, "geneDataHolder");
        if (holder == null) {
            holder = ReflectionAccessCache.invoke(geneData, "getGeneDataHolder");
        }
        Object geneSet = ReflectionAccessCache.invoke(holder, "getGENE_SET");
        if (geneSet == null) {
            geneSet = ReflectionAccessCache.invoke(geneData, "getGENE_SET");
        }

        List<?> genes = toList(geneSet);
        if (genes.isEmpty()) {
            return List.of();
        }
        Set<String> seenIds = new LinkedHashSet<>();
        List<ObservedGene> result = new ArrayList<>();
        for (Object gene : genes) {
            try {
                ResourceLocation itemId = resolveItemId(gene);
                String geneId = itemId != null ? itemId.getPath() : resolveId(gene);
                geneId = ObservedGene.boundedId(geneId);
                if (geneId == null || geneId.isBlank() || !seenIds.add(geneId)) {
                    continue;
                }
                Component displayName = resolveDisplayComponent(gene);
                if (displayName == null || displayName.getString().isBlank()) {
                    displayName = resolveDisplayName(geneId);
                }
                result.add(new ObservedGene(
                        geneId,
                        itemId,
                        Component.literal(ObservedGene.boundedDisplayName(displayName))
                ));
                if (result.size() >= ObservedGene.MAX_COUNT) {
                    break;
                }
            } catch (RuntimeException ignored) {
                // A changed or malformed optional gene must not disable the whole observation overlay.
            }
        }
        return List.copyOf(result);
    }

    private static ResourceLocation resolveItemId(Object gene) {
        ResourceLocation registeredLocation = resolveRegisteredLocation(gene);
        if (registeredLocation != null && BuiltInRegistries.ITEM.containsKey(registeredLocation)) {
            return registeredLocation;
        }
        String geneId = normalizeId(ReflectionAccessCache.invoke(gene, "getItem"));
        if (geneId == null) {
            geneId = normalizeId(ReflectionAccessCache.invoke(gene, "getGeneItem"));
        }
        if (geneId == null) {
            geneId = normalizeId(ReflectionAccessCache.invoke(gene, "getDisplayItem"));
        }
        if (geneId == null) {
            geneId = resolveId(gene);
        }
        if (geneId == null || geneId.isBlank()) {
            return null;
        }
        ResourceLocation itemId = jurassicSagaItemId(geneId);
        return itemId != null && BuiltInRegistries.ITEM.containsKey(itemId) ? itemId : null;
    }

    private static String resolveId(Object gene) {
        ResourceLocation registeredLocation = resolveRegisteredLocation(gene);
        if (registeredLocation != null) {
            return registeredLocation.getPath();
        }
        String[] methodNames = {
                "getId",
                "getRegistryName",
                "getName",
                "getDescriptionId",
                "getTranslationKey"
        };
        for (String methodName : methodNames) {
            String id = normalizeId(ReflectionAccessCache.invoke(gene, methodName));
            if (id != null) {
                return id;
            }
        }
        String[] fieldNames = {
                "id",
                "name",
                "translationKey",
                "descriptionId",
                "item"
        };
        for (String fieldName : fieldNames) {
            String id = normalizeId(ReflectionAccessCache.readField(gene, fieldName));
            if (id != null) {
                return id;
            }
        }
        String classId = camelToSnake(gene.getClass().getSimpleName());
        if (classId.endsWith("_gene")) {
            classId = classId.substring(0, classId.length() - 5);
        }
        ResourceLocation classItemId = jurassicSagaItemId(classId);
        return classItemId != null && BuiltInRegistries.ITEM.containsKey(classItemId)
                ? classId
                : null;
    }

    private static String normalizeId(Object value) {
        if (value == null) {
            return null;
        }
        String candidate = switch (value) {
            case ResourceLocation resourceLocation -> resourceLocation.getPath();
            case Item item -> {
                ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(item);
                yield itemId == null ? null : itemId.getPath();
            }
            case Component component -> component.getString();
            case Enum<?> enumValue -> enumValue.name().toLowerCase(Locale.ROOT);
            default -> safeToString(value);
        };
        if (candidate == null || candidate.isBlank()) {
            return null;
        }
        candidate = candidate.toLowerCase(Locale.ROOT);
        int separator = candidate.indexOf(':');
        if (separator >= 0 && separator + 1 < candidate.length()) {
            candidate = candidate.substring(separator + 1);
        }
        if (candidate.startsWith("gene.jurassicsaga.")) {
            candidate = candidate.substring("gene.jurassicsaga.".length());
        }
        candidate = candidate.replace('-', '_');
        if (candidate.endsWith("_gene")) {
            candidate = candidate.substring(0, candidate.length() - 5);
        }
        ResourceLocation itemId = jurassicSagaItemId(candidate);
        return itemId != null && BuiltInRegistries.ITEM.containsKey(itemId) ? candidate : null;
    }

    private static ResourceLocation resolveRegisteredLocation(Object gene) {
        Object value = ReflectionAccessCache.invoke(gene, "getRegisteredLocation");
        return value instanceof ResourceLocation resourceLocation ? resourceLocation : null;
    }

    private static Component resolveDisplayComponent(Object gene) {
        Object value = ReflectionAccessCache.invoke(gene, "getTranslatableObject");
        return value instanceof Component component ? component : null;
    }

    private static Component resolveDisplayName(String geneId) {
        String translationKey = "gene.jurassicsaga." + geneId;
        String translated = Component.translatable(translationKey).getString();
        return translationKey.equals(translated)
                ? Component.literal(prettify(geneId))
                : Component.literal(stripGeneSuffix(translated));
    }

    private static List<?> toList(Object value) {
        if (value == null) {
            return List.of();
        }
        if (value instanceof List<?> list) {
            return list;
        }
        if (value instanceof Iterable<?> iterable) {
            List<Object> result = new ArrayList<>();
            iterable.forEach(result::add);
            return result;
        }
        if (value.getClass().isArray()) {
            int length = Array.getLength(value);
            List<Object> result = new ArrayList<>(length);
            for (int index = 0; index < length; index++) {
                result.add(Array.get(value, index));
            }
            return result;
        }
        return List.of();
    }

    private static String stripGeneSuffix(String value) {
        return value.endsWith(" Gene") ? value.substring(0, value.length() - 5) : value;
    }

    private static String prettify(String geneId) {
        StringBuilder builder = new StringBuilder(geneId.length() + 4);
        for (String part : geneId.split("_")) {
            if (part.isBlank()) {
                continue;
            }
            if (!builder.isEmpty()) {
                builder.append(' ');
            }
            builder.append(Character.toUpperCase(part.charAt(0)));
            if (part.length() > 1) {
                builder.append(part.substring(1));
            }
        }
        return builder.toString();
    }

    private static String camelToSnake(String value) {
        StringBuilder builder = new StringBuilder(value.length() + 8);
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (Character.isUpperCase(character) && index > 0) {
                builder.append('_');
            }
            builder.append(Character.toLowerCase(character));
        }
        return builder.toString();
    }

    private static ResourceLocation jurassicSagaItemId(String path) {
        return path == null ? null : ResourceLocation.tryParse("jurassicsaga:" + path);
    }

    private static String safeToString(Object value) {
        try {
            return value.toString();
        } catch (RuntimeException exception) {
            return null;
        }
    }
}
