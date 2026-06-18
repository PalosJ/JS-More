package com.palos.jsrevise.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

class ItemTextureResourceTest {
    private static final String ITEM_TEXTURE_ROOT = "/assets/jsrevise/textures/item/";
    private static final List<String> VANILLA_CROSSBOW_STANDBY_ALPHA = List.of(
            "............#...",
            "........######..",
            "...##.########..",
            "..#..######.#...",
            "..#.####...#....",
            "...######.#.....",
            "..########......",
            "..#########.....",
            ".###.#######....",
            ".###..#######...",
            ".##..#.######...",
            ".##.#...######..",
            "####.....######.",
            ".##........#####",
            "............####",
            ".............##."
    );

    @Test
    void crossbowAnimationTexturesKeepStableMinecraftSizedStateSilhouettes() throws IOException {
        Map<String, Integer> expectedOpaquePixels = Map.ofEntries(
                Map.entry("crossbow_standby.png", 92),
                Map.entry("crossbow_pulling_0.png", 96),
                Map.entry("crossbow_pulling_1.png", 98),
                Map.entry("crossbow_pulling_2.png", 101),
                Map.entry("crossbow_loaded_1.png", 98),
                Map.entry("crossbow_loaded_2.png", 97),
                Map.entry("crossbow_loaded_3.png", 99),
                Map.entry("crossbow_loaded_4.png", 99),
                Map.entry("crossbow_loaded_5.png", 102),
                Map.entry("crossbow_loaded_6.png", 102)
        );

        for (Map.Entry<String, Integer> entry : expectedOpaquePixels.entrySet()) {
            BufferedImage image = readTexture(ITEM_TEXTURE_ROOT + entry.getKey());
            assertEquals(16, image.getWidth());
            assertEquals(16, image.getHeight());
            assertEquals(entry.getValue(), countOpaquePixels(image));
            assertTrue(isTransparent(image.getRGB(3, 2)));
            assertTrue(isTransparent(image.getRGB(2, 3)));
            assertTrue(isTransparent(image.getRGB(4, 2)));
            assertTrue(isTransparent(image.getRGB(2, 4)));
            assertTrue(isOpaque(image.getRGB(3, 3)));
            assertTrue(isOpaque(image.getRGB(4, 3)));
            assertTrue(isOpaque(image.getRGB(3, 4)));
            assertEquals(image.getRGB(12, 0), image.getRGB(0, 12));
            assertEquals(image.getRGB(12, 3), image.getRGB(3, 12));
            assertTrue(isOpaque(image.getRGB(8, 1)));
            assertTrue(isOpaque(image.getRGB(3, 8)));
            assertTrue(isOpaque(image.getRGB(2, 10)));
            assertTrue(isOpaque(image.getRGB(13, 13)));
            assertTrue(isTransparent(image.getRGB(13, 15)));
            assertTrue(isTransparent(image.getRGB(15, 13)));
            assertTrue(isTransparent(image.getRGB(14, 13)));
            assertTrue(isTransparent(image.getRGB(13, 14)));
            assertTrue(isTransparent(image.getRGB(14, 14)));
        }
    }

    @Test
    void crossbowModelOverridesFollowVanillaAnimationPrecedence() throws IOException {
        String model = readResourceText("/assets/jsrevise/models/item/anesthetic_crossbow.json");
        int pulling0 = model.indexOf("jsrevise:item/anesthetic_crossbow_pulling_0");
        int pulling1 = model.indexOf("jsrevise:item/anesthetic_crossbow_pulling_1");
        int pulling2 = model.indexOf("jsrevise:item/anesthetic_crossbow_pulling_2");
        int loaded1 = model.indexOf("jsrevise:item/anesthetic_crossbow_loaded_1");
        int loaded2 = model.indexOf("jsrevise:item/anesthetic_crossbow_loaded_2");
        int loaded3 = model.indexOf("jsrevise:item/anesthetic_crossbow_loaded_3");
        int loaded4 = model.indexOf("jsrevise:item/anesthetic_crossbow_loaded_4");
        int loaded5 = model.indexOf("jsrevise:item/anesthetic_crossbow_loaded_5");
        int loaded6 = model.indexOf("jsrevise:item/anesthetic_crossbow_loaded_6");

        assertTrue(pulling0 >= 0);
        assertTrue(pulling0 < pulling1);
        assertTrue(pulling1 < pulling2);
        assertTrue(pulling2 < loaded1);
        assertTrue(loaded1 < loaded2);
        assertTrue(loaded2 < loaded3);
        assertTrue(loaded3 < loaded4);
        assertTrue(loaded4 < loaded5);
        assertTrue(loaded5 < loaded6);
    }

    @Test
    void crossbowModelReferencesOnlyExistingAnimationModels() throws IOException {
        String model = readResourceText("/assets/jsrevise/models/item/anesthetic_crossbow.json");
        for (String referencedModel : List.of(
                "anesthetic_crossbow_pulling_0",
                "anesthetic_crossbow_pulling_1",
                "anesthetic_crossbow_pulling_2",
                "anesthetic_crossbow_loaded_1",
                "anesthetic_crossbow_loaded_2",
                "anesthetic_crossbow_loaded_3",
                "anesthetic_crossbow_loaded_4",
                "anesthetic_crossbow_loaded_5",
                "anesthetic_crossbow_loaded_6"
        )) {
            assertTrue(model.contains("jsrevise:item/" + referencedModel));
            try (InputStream stream = ItemTextureResourceTest.class.getResourceAsStream(
                    "/assets/jsrevise/models/item/" + referencedModel + ".json"
            )) {
                assertNotNull(stream, "Missing referenced model: " + referencedModel);
            }
        }
        assertNull(ItemTextureResourceTest.class.getResource(
                "/assets/jsrevise/models/item/anesthetic_crossbow_arrow.json"
        ));
        assertNull(ItemTextureResourceTest.class.getResource(
                "/assets/jsrevise/textures/item/crossbow_arrow.png"
        ));
    }

    @Test
    void loadedCrossbowStringReturnsOneDistinctSymmetricStepPerShot() throws IOException {
        BufferedImage previous = null;

        for (int loaded = 1; loaded <= 6; loaded++) {
            BufferedImage current = readTexture(ITEM_TEXTURE_ROOT + "crossbow_loaded_" + loaded + ".png");
            assertEquals(current.getRGB(12, 3), current.getRGB(3, 12));
            assertEquals(current.getRGB(12, 4), current.getRGB(4, 12));
            assertEquals(isOpaque(current.getRGB(5, 12)), isOpaque(current.getRGB(12, 5)));
            assertEquals(isOpaque(current.getRGB(6, 12)), isOpaque(current.getRGB(12, 6)));
            if (previous != null) {
                assertTrue(countDifferentPixels(previous, current) > 0);
            }
            previous = current;
        }
    }

    @Test
    void redesignedItemsKeepTheirRequiredColorIdentity() throws IOException {
        BufferedImage goggles = readTexture(ITEM_TEXTURE_ROOT + "dino_doctor_goggles.png");
        BufferedImage chargedCrossbow = readTexture(ITEM_TEXTURE_ROOT + "crossbow_loaded_6.png");

        assertTrue(countPixels(goggles, ItemTextureResourceTest::isBlue) >= 10);
        assertTrue(countPixels(goggles, ItemTextureResourceTest::isNearWhite) >= 16);
        assertTrue(countPixels(chargedCrossbow, ItemTextureResourceTest::isCyanMedicine) >= 2);
    }

    @Test
    void doctorGogglesKeepSymmetricGeometryWithDirectionalLighting() throws IOException {
        BufferedImage goggles = readTexture(ITEM_TEXTURE_ROOT + "dino_doctor_goggles.png");
        int directionalColorDifferences = 0;

        for (int y = 0; y < goggles.getHeight(); y++) {
            for (int x = 0; x < goggles.getWidth() / 2; x++) {
                int mirroredColor = goggles.getRGB(goggles.getWidth() - 1 - x, y);
                assertEquals(isOpaque(goggles.getRGB(x, y)), isOpaque(mirroredColor));
                if (isOpaque(goggles.getRGB(x, y)) && goggles.getRGB(x, y) != mirroredColor) {
                    directionalColorDifferences++;
                }
            }
        }

        assertTrue(directionalColorDifferences >= 20);
        assertTrue(brightness(goggles.getRGB(2, 4)) > brightness(goggles.getRGB(5, 4)));
        assertTrue(isTransparent(goggles.getRGB(1, 4)));
        assertTrue(isOpaque(goggles.getRGB(2, 4)));
        assertTrue(isOpaque(goggles.getRGB(1, 7)));
        assertTrue(isTransparent(goggles.getRGB(14, 4)));
        assertTrue(isOpaque(goggles.getRGB(13, 4)));
        assertTrue(isOpaque(goggles.getRGB(14, 7)));
    }

    @Test
    void crossbowKeepsVanillaTopDownAnimationAndCompactForwardFacingSyringe() throws IOException {
        BufferedImage standby = readTexture(ITEM_TEXTURE_ROOT + "crossbow_standby.png");
        BufferedImage charged = readTexture(ITEM_TEXTURE_ROOT + "crossbow_loaded_6.png");
        double vanillaOverlap = alphaIntersectionOverUnion(standby, VANILLA_CROSSBOW_STANDBY_ALPHA);

        assertTrue(vanillaOverlap >= 0.72D && vanillaOverlap <= 0.90D);
        assertTrue(isTransparent(charged.getRGB(3, 2)));
        assertTrue(isTransparent(charged.getRGB(2, 3)));
        assertTrue(isCyanMedicine(charged.getRGB(6, 6)));
        assertTrue(!isCyanMedicine(charged.getRGB(9, 9)));
    }

    private static BufferedImage readTexture(String path) throws IOException {
        try (InputStream stream = ItemTextureResourceTest.class.getResourceAsStream(path)) {
            assertNotNull(stream, "Missing texture: " + path);
            BufferedImage image = ImageIO.read(stream);
            assertNotNull(image, "Unreadable texture: " + path);
            return image;
        }
    }

    private static String readResourceText(String path) throws IOException {
        try (InputStream stream = ItemTextureResourceTest.class.getResourceAsStream(path)) {
            assertNotNull(stream, "Missing resource: " + path);
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static int countOpaquePixels(BufferedImage image) {
        return countPixels(image, ItemTextureResourceTest::isOpaque);
    }

    private static int countPixels(BufferedImage image, ColorPredicate predicate) {
        int count = 0;
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                if (predicate.test(image.getRGB(x, y))) {
                    count++;
                }
            }
        }
        return count;
    }

    private static int countDifferentPixels(BufferedImage first, BufferedImage second) {
        int count = 0;
        for (int y = 0; y < first.getHeight(); y++) {
            for (int x = 0; x < first.getWidth(); x++) {
                if (first.getRGB(x, y) != second.getRGB(x, y)) {
                    count++;
                }
            }
        }
        return count;
    }

    private static boolean isBlue(int color) {
        int alpha = (color >>> 24) & 0xFF;
        int red = (color >>> 16) & 0xFF;
        int green = (color >>> 8) & 0xFF;
        int blue = color & 0xFF;
        return alpha != 0 && blue >= 90 && blue > red * 1.4D && blue > green * 1.1D;
    }

    private static boolean isNearWhite(int color) {
        int alpha = (color >>> 24) & 0xFF;
        int red = (color >>> 16) & 0xFF;
        int green = (color >>> 8) & 0xFF;
        int blue = color & 0xFF;
        return alpha != 0 && red >= 185 && green >= 195 && blue >= 195;
    }

    private static boolean isCyanMedicine(int color) {
        int alpha = (color >>> 24) & 0xFF;
        int red = (color >>> 16) & 0xFF;
        int green = (color >>> 8) & 0xFF;
        int blue = color & 0xFF;
        return alpha != 0 && red <= 80 && green >= 120 && blue >= 160;
    }

    private static boolean isOpaque(int color) {
        return ((color >>> 24) & 0xFF) != 0;
    }

    private static boolean isTransparent(int color) {
        return !isOpaque(color);
    }

    private static double brightness(int color) {
        int red = (color >>> 16) & 0xFF;
        int green = (color >>> 8) & 0xFF;
        int blue = color & 0xFF;
        return red * 0.2126D + green * 0.7152D + blue * 0.0722D;
    }

    private static double alphaIntersectionOverUnion(BufferedImage image, List<String> referenceRows) {
        int intersection = 0;
        int union = 0;
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                boolean actual = isOpaque(image.getRGB(x, y));
                boolean reference = referenceRows.get(y).charAt(x) == '#';
                if (actual && reference) {
                    intersection++;
                }
                if (actual || reference) {
                    union++;
                }
            }
        }
        return union == 0 ? 0.0D : (double) intersection / union;
    }

    @FunctionalInterface
    private interface ColorPredicate {
        boolean test(int color);
    }
}
