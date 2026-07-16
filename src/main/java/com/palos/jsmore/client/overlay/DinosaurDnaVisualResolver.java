package com.palos.jsmore.client.overlay;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.NativeImage;
import com.palos.jsmore.server.system.profile.DinosaurProfileResolver;
import java.io.IOException;
import java.io.Reader;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import jp.jurassicsaga.server.animal.entity.obj.bases.JSAnimalBase;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.FastColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

final class DinosaurDnaVisualResolver {
    private static final int FALLBACK_THEME_COLOR = 0x4F718E;
    private static final ConcurrentHashMap<ResourceLocation, Integer> THEME_COLORS = new ConcurrentHashMap<>();

    private DinosaurDnaVisualResolver() {
    }

    static DnaVisual resolve(JSAnimalBase animal) {
        ResourceLocation speciesId = DinosaurProfileResolver.speciesId(animal);
        return resolve(speciesId);
    }

    static DnaVisual resolve(ResourceLocation speciesId) {
        ResourceLocation coinId = ResourceLocation.fromNamespaceAndPath(
                speciesId.getNamespace(),
                speciesId.getPath() + "_coin"
        );
        ItemStack icon = resolveCoin(coinId);
        int themeColor = THEME_COLORS.computeIfAbsent(coinId, DinosaurDnaVisualResolver::loadThemeColor);
        return new DnaVisual(icon, themeColor);
    }

    static void clearCache() {
        THEME_COLORS.clear();
    }

    private static ItemStack resolveCoin(ResourceLocation coinId) {
        if (!BuiltInRegistries.ITEM.containsKey(coinId)) {
            return ItemStack.EMPTY;
        }
        Item item = BuiltInRegistries.ITEM.get(coinId);
        return item == null ? ItemStack.EMPTY : new ItemStack(item);
    }

    private static int loadThemeColor(ResourceLocation coinId) {
        ResourceManager resourceManager = Minecraft.getInstance().getResourceManager();
        ResourceLocation modelId = ResourceLocation.fromNamespaceAndPath(
                coinId.getNamespace(),
                "models/item/" + coinId.getPath() + ".json"
        );
        Optional<Resource> modelResource = resourceManager.getResource(modelId);
        if (modelResource.isEmpty()) {
            return FALLBACK_THEME_COLOR;
        }

        try (Reader reader = modelResource.get().openAsReader()) {
            JsonObject model = JsonParser.parseReader(reader).getAsJsonObject();
            JsonObject textures = model.getAsJsonObject("textures");
            if (textures == null || !textures.has("layer0")) {
                return FALLBACK_THEME_COLOR;
            }
            ResourceLocation textureId = ResourceLocation.parse(textures.get("layer0").getAsString());
            ResourceLocation textureFile = ResourceLocation.fromNamespaceAndPath(
                    textureId.getNamespace(),
                    "textures/" + textureId.getPath() + ".png"
            );
            return loadDominantColor(resourceManager, textureFile);
        } catch (RuntimeException | IOException exception) {
            return FALLBACK_THEME_COLOR;
        }
    }

    private static int loadDominantColor(ResourceManager resourceManager, ResourceLocation textureFile) throws IOException {
        Optional<Resource> textureResource = resourceManager.getResource(textureFile);
        if (textureResource.isEmpty()) {
            return FALLBACK_THEME_COLOR;
        }
        try (NativeImage image = NativeImage.read(textureResource.get().open())) {
            double totalWeight = 0.0D;
            double redTotal = 0.0D;
            double greenTotal = 0.0D;
            double blueTotal = 0.0D;
            for (int y = 0; y < image.getHeight(); y++) {
                for (int x = 0; x < image.getWidth(); x++) {
                    int color = image.getPixelRGBA(x, y);
                    int alpha = FastColor.ABGR32.alpha(color);
                    if (alpha < 48) {
                        continue;
                    }
                    int red = FastColor.ABGR32.red(color);
                    int green = FastColor.ABGR32.green(color);
                    int blue = FastColor.ABGR32.blue(color);
                    int maximum = Math.max(red, Math.max(green, blue));
                    int minimum = Math.min(red, Math.min(green, blue));
                    double saturation = maximum == 0 ? 0.0D : (maximum - minimum) / (double) maximum;
                    double brightness = maximum / 255.0D;
                    if (saturation < 0.18D || brightness < 0.12D) {
                        continue;
                    }
                    double weight = (alpha / 255.0D) * saturation * (0.35D + brightness);
                    totalWeight += weight;
                    redTotal += red * weight;
                    greenTotal += green * weight;
                    blueTotal += blue * weight;
                }
            }
            if (totalWeight <= 0.0D) {
                return FALLBACK_THEME_COLOR;
            }
            int red = (int) Math.round(redTotal / totalWeight);
            int green = (int) Math.round(greenTotal / totalWeight);
            int blue = (int) Math.round(blueTotal / totalWeight);
            return red << 16 | green << 8 | blue;
        }
    }

    record DnaVisual(ItemStack icon, int themeColor) {
    }
}
