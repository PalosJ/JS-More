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
import java.util.regex.Pattern;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

class ItemTextureResourceTest {
    private static final String ITEM_TEXTURE_ROOT = "/assets/jsrevise/textures/item/";
    private static final String BLOCK_TEXTURE_ROOT = "/assets/jsrevise/textures/block/";
    private static final int CAPTURE_CAGE_OUTER_BORDER = 0xFF40474A;
    private static final int CAPTURE_CAGE_WINDOW_PANE = 0xFF232A2D;
    private static final int CAPTURE_CAGE_PANEL_SHADOW = 0xFF9DA4A1;
    private static final int CAPTURE_CAGE_CROSSBAR = 0xFF697274;
    private static final Pattern EMPTY_MODEL_ELEMENTS_PATTERN = Pattern.compile(
            "\"elements\"\\s*:\\s*\\[\\s*\\]",
            Pattern.DOTALL
    );
    private static final Map<String, Integer> DINOSAUR_CAPTURE_CAGE_Y_ROTATIONS = Map.of(
            "north", 0,
            "east", 90,
            "south", 180,
            "west", 270
    );
    private static final Map<String, int[]> DINOSAUR_CAPTURE_CAGE_BLOCK_TEXTURES = Map.ofEntries(
            Map.entry("dinosaur_capture_cage_front.png", new int[]{32, 32}),
            Map.entry("dinosaur_capture_cage_back.png", new int[]{32, 32}),
            Map.entry("dinosaur_capture_cage_side_badge.png", new int[]{64, 32}),
            Map.entry("dinosaur_capture_cage_side_bars.png", new int[]{64, 32}),
            Map.entry("dinosaur_capture_cage_top.png", new int[]{32, 64}),
            Map.entry("dinosaur_capture_cage_bottom.png", new int[]{32, 64})
    );
    private static final Map<String, Integer> DINOSAUR_CAPTURE_CAGE_LIGHT_GRAY_MINIMUMS = Map.ofEntries(
            Map.entry("dinosaur_capture_cage_front.png", 360),
            Map.entry("dinosaur_capture_cage_back.png", 650),
            Map.entry("dinosaur_capture_cage_side_badge.png", 950),
            Map.entry("dinosaur_capture_cage_side_bars.png", 950),
            Map.entry("dinosaur_capture_cage_top.png", 1300),
            Map.entry("dinosaur_capture_cage_bottom.png", 150)
    );
    private static final List<String> DINOSAUR_CAPTURE_CAGE_BLOCK_MODELS = List.of(
            "dinosaur_capture_cage_x0_y0_z0",
            "dinosaur_capture_cage_x1_y0_z0",
            "dinosaur_capture_cage_x0_y0_z1",
            "dinosaur_capture_cage_x1_y0_z1",
            "dinosaur_capture_cage_x0_y0_z2",
            "dinosaur_capture_cage_x1_y0_z2",
            "dinosaur_capture_cage_x0_y0_z3",
            "dinosaur_capture_cage_x1_y0_z3",
            "dinosaur_capture_cage_x0_y1_z0",
            "dinosaur_capture_cage_x1_y1_z0",
            "dinosaur_capture_cage_x0_y1_z1",
            "dinosaur_capture_cage_x1_y1_z1",
            "dinosaur_capture_cage_x0_y1_z2",
            "dinosaur_capture_cage_x1_y1_z2",
            "dinosaur_capture_cage_x0_y1_z3",
            "dinosaur_capture_cage_x1_y1_z3"
    );
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
    void dinosaurCaptureCageResourcesStayReferencedAndPixelClean() throws IOException {
        String itemModel = readResourceText("/assets/jsrevise/models/item/dinosaur_capture_cage.json");
        assertCaptureCageItemModelUsesBlockStyleBakedCuboid(itemModel);
        assertNull(
                ItemTextureResourceTest.class.getResource("/assets/jsrevise/textures/item/dinosaur_capture_cage.png"),
                "Capture cage item should not keep a standalone 16x16 item texture"
        );

        for (Map.Entry<String, int[]> blockTexture : DINOSAUR_CAPTURE_CAGE_BLOCK_TEXTURES.entrySet()) {
            BufferedImage texture = readTexture(BLOCK_TEXTURE_ROOT + blockTexture.getKey());
            assertEquals(blockTexture.getValue()[0], texture.getWidth());
            assertEquals(blockTexture.getValue()[1], texture.getHeight());
            assertEquals(texture.getWidth() * texture.getHeight(), countOpaquePixels(texture));
            assertEquals(0, countPartialAlphaPixels(texture));
            assertTrue(
                    countPixels(texture, ItemTextureResourceTest::isContainerWhiteGray)
                            >= DINOSAUR_CAPTURE_CAGE_LIGHT_GRAY_MINIMUMS.get(blockTexture.getKey()),
                    blockTexture.getKey() + " should keep the white-gray container palette"
            );
            if (isDinosaurCaptureCageMainFace(blockTexture.getKey())) {
                assertCaptureCageOuterBorder(blockTexture.getKey(), texture);
            }
        }
        BufferedImage front = readTexture(BLOCK_TEXTURE_ROOT + "dinosaur_capture_cage_front.png");
        BufferedImage back = readTexture(BLOCK_TEXTURE_ROOT + "dinosaur_capture_cage_back.png");
        assertFrontIsOnlyDoorPanel(front, back);
        assertFrontHasUnifiedOuterBorderAndCaps(front);
        assertFrontKeepsExtractedDoorWindows(front);
        assertFrontDoesNotKeepStrayOuterDarkStrokes(front);
        assertFrontKeepsExtractedDoubleDoorStructure(front);
        assertFrontKeepsUpperLatchWithoutLowerPadlock(front);
        BufferedImage sideBadge = readTexture(BLOCK_TEXTURE_ROOT + "dinosaur_capture_cage_side_badge.png");
        BufferedImage sideBars = readTexture(BLOCK_TEXTURE_ROOT + "dinosaur_capture_cage_side_bars.png");
        assertEquals(
                0,
                countDifferentPixels(sideBadge, sideBars),
                "Capture cage side textures must stay pixel-identical"
        );
        assertLongSideKeepsSingleLargeDinosaurBadge(sideBadge);
        assertLongSideKeepsSingleLargeDinosaurBadge(sideBars);
        assertLongSideBadgeIsRaisedAndCentered(sideBadge);
        assertLongSideBadgeIsRaisedAndCentered(sideBars);

        String blockState = readResourceText("/assets/jsrevise/blockstates/dinosaur_capture_cage.json");
        assertTrue(blockState.contains("\"variants\""));
        assertTrue(!blockState.contains("\"multipart\""));

        for (Map.Entry<String, Integer> facing : DINOSAUR_CAPTURE_CAGE_Y_ROTATIONS.entrySet()) {
            for (int offsetY = 0; offsetY < 2; offsetY++) {
                for (int offsetZ = 0; offsetZ < 4; offsetZ++) {
                    for (int offsetX = 0; offsetX < 2; offsetX++) {
                        String modelName = "dinosaur_capture_cage_x" + offsetX + "_y" + offsetY + "_z" + offsetZ;
                        String variantKey = "facing=" + facing.getKey()
                                + ",offset_x=" + offsetX
                                + ",offset_y=" + offsetY
                                + ",offset_z=" + offsetZ;
                        assertBlockStateVariantUsesModelAndRotation(
                                blockState,
                                variantKey,
                                modelName,
                                facing.getValue()
                        );
                    }
                }
            }
        }

        for (String blockModelName : DINOSAUR_CAPTURE_CAGE_BLOCK_MODELS) {
            String blockModel = readResourceText("/assets/jsrevise/models/block/" + blockModelName + ".json");
            assertBerPlaceholderModel(blockModelName, blockModel);
        }
        assertNull(ItemTextureResourceTest.class.getResource(
                "/assets/jsrevise/models/block/dinosaur_capture_cage.json"
        ));

        String lootTable = readResourceText("/data/jsrevise/loot_table/blocks/dinosaur_capture_cage.json");
        assertTrue(lootTable.contains("\"type\": \"minecraft:block\""));
        assertTrue(lootTable.contains("\"pools\": []"));

        String pickaxeTag = readResourceText("/data/minecraft/tags/block/mineable/pickaxe.json");
        String ironToolTag = readResourceText("/data/minecraft/tags/block/needs_iron_tool.json");
        assertTrue(pickaxeTag.contains("\"jsrevise:dinosaur_capture_cage\""));
        assertTrue(ironToolTag.contains("\"jsrevise:dinosaur_capture_cage\""));
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

    private static void assertBerPlaceholderModel(String modelName, String model) {
        assertTrue(
                model.contains("jsrevise:block/dinosaur_capture_cage_side_badge"),
                modelName + " should keep a valid particle texture for block particles"
        );
        assertTrue(
                !model.contains("\"elements\"") || EMPTY_MODEL_ELEMENTS_PATTERN.matcher(model).find(),
                modelName + " should be an empty BER placeholder model"
        );
        assertTrue(!model.contains("\"from\""), modelName + " should not bake geometry alongside the BER");
        assertTrue(!model.contains("\"to\""), modelName + " should not bake geometry alongside the BER");
        assertTrue(!model.contains("\"faces\""), modelName + " should not bake geometry alongside the BER");
    }

    private static void assertCaptureCageItemModelUsesBlockStyleBakedCuboid(String model) {
        assertTrue(!model.contains("item/generated"), "Capture cage item should not use flat generated item rendering");
        assertTrue(
                !model.contains("jsrevise:item/dinosaur_capture_cage"),
                "Capture cage item should not reference the old 16x16 item texture"
        );
        assertTrue(model.contains("\"elements\""));
        assertTrue(model.contains("\"display\""));
        assertTrue(model.contains("\"gui\""));
        assertTrue(model.contains("\"faces\""));
        assertTrue(Pattern.compile("\"from\"\\s*:\\s*\\[\\s*4\\s*,\\s*4\\s*,\\s*0\\s*]")
                .matcher(model)
                .find());
        assertTrue(Pattern.compile("\"to\"\\s*:\\s*\\[\\s*12\\s*,\\s*12\\s*,\\s*16\\s*]")
                .matcher(model)
                .find());

        for (String texture : List.of(
                "dinosaur_capture_cage_front",
                "dinosaur_capture_cage_back",
                "dinosaur_capture_cage_side_badge",
                "dinosaur_capture_cage_side_bars",
                "dinosaur_capture_cage_top",
                "dinosaur_capture_cage_bottom"
        )) {
            assertTrue(
                    model.contains("jsrevise:block/" + texture),
                    "Capture cage item model should reuse block texture " + texture
            );
        }
    }

    private static void assertBlockStateVariantUsesModelAndRotation(
            String blockState,
            String variantKey,
            String modelName,
            int yRotation
    ) {
        Pattern pattern = Pattern.compile(
                "\"" + Pattern.quote(variantKey) + "\"\\s*:\\s*\\{"
                        + "[^}]*\"model\"\\s*:\\s*\"jsrevise:block/" + Pattern.quote(modelName) + "\""
                        + "[^}]*\"y\"\\s*:\\s*" + yRotation
                        + "\\s*\\}",
                Pattern.DOTALL
        );
        assertTrue(pattern.matcher(blockState).find(), variantKey + " should use model " + modelName
                + " with y rotation " + yRotation);
    }

    private static int countOpaquePixels(BufferedImage image) {
        return countPixels(image, ItemTextureResourceTest::isOpaque);
    }

    private static int countPartialAlphaPixels(BufferedImage image) {
        return countPixels(image, color -> {
            int alpha = (color >>> 24) & 0xFF;
            return alpha > 0 && alpha < 255;
        });
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

    private static int countPixelsInRegion(
            BufferedImage image,
            int minX,
            int minY,
            int width,
            int height,
            ColorPredicate predicate
    ) {
        int count = 0;
        for (int y = minY; y < minY + height; y++) {
            for (int x = minX; x < minX + width; x++) {
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

    private static int countPixelsOnRectangleBorder(
            BufferedImage image,
            int minX,
            int minY,
            int maxX,
            int maxY,
            ColorPredicate predicate
    ) {
        int count = 0;
        for (int x = minX; x <= maxX; x++) {
            if (predicate.test(image.getRGB(x, minY))) {
                count++;
            }
            if (predicate.test(image.getRGB(x, maxY))) {
                count++;
            }
        }
        for (int y = minY + 1; y < maxY; y++) {
            if (predicate.test(image.getRGB(minX, y))) {
                count++;
            }
            if (predicate.test(image.getRGB(maxX, y))) {
                count++;
            }
        }
        return count;
    }

    private static PixelBounds boundsOfPixels(BufferedImage image, ColorPredicate predicate) {
        int minX = image.getWidth();
        int minY = image.getHeight();
        int maxX = -1;
        int maxY = -1;
        int count = 0;
        long sumX = 0L;
        long sumY = 0L;
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                if (predicate.test(image.getRGB(x, y))) {
                    minX = Math.min(minX, x);
                    minY = Math.min(minY, y);
                    maxX = Math.max(maxX, x);
                    maxY = Math.max(maxY, y);
                    count++;
                    sumX += x;
                    sumY += y;
                }
            }
        }

        assertTrue(count > 0);
        return new PixelBounds(minX, minY, maxX, maxY, count, sumX, sumY);
    }

    private static void assertFrontIsOnlyDoorPanel(BufferedImage front, BufferedImage back) {
        assertTrue(countPixelsInRegion(front, 4, 3, 24, 25, ItemTextureResourceTest::isCaptureCageDoorLine) >= 100);
        assertTrue(countPixelsInRegion(back, 4, 3, 24, 25, ItemTextureResourceTest::isCaptureCageDoorLine) <= 10);
        assertTrue(
                countPixelsInRegion(front, 4, 7, 24, 8, ItemTextureResourceTest::isCaptureCageWindowPixel) >= 70
        );
        assertTrue(
                countPixelsInRegion(back, 4, 7, 24, 8, ItemTextureResourceTest::isCaptureCageWindowPixel) <= 25
        );
        assertTrue(
                countPixelsInRegion(front, 13, 18, 6, 3, ItemTextureResourceTest::isCaptureCageBadgeGold) >= 16
        );
        assertEquals(
                0,
                countPixelsInRegion(back, 13, 18, 6, 3, ItemTextureResourceTest::isCaptureCageBadgeGold)
        );
        assertTrue(countDifferentPixels(front, back) >= 180);
    }

    private static void assertFrontHasUnifiedOuterBorderAndCaps(BufferedImage front) {
        assertCaptureCageBorderRing(front, 0);
        assertCaptureCageBorderRing(front, 1);
        assertFrontDoesNotUseThirdFullBorderRing(front);
    }

    private static void assertCaptureCageBorderRing(BufferedImage front, int ring) {
        int maxX = front.getWidth() - 1 - ring;
        int maxY = front.getHeight() - 1 - ring;
        for (int x = ring; x <= maxX; x++) {
            assertEquals(CAPTURE_CAGE_OUTER_BORDER, front.getRGB(x, ring),
                    "Capture cage front border ring " + ring + " should use the unified outline color");
            assertEquals(CAPTURE_CAGE_OUTER_BORDER, front.getRGB(x, maxY),
                    "Capture cage front border ring " + ring + " should use the unified outline color");
        }
        for (int y = ring + 1; y < maxY; y++) {
            assertEquals(CAPTURE_CAGE_OUTER_BORDER, front.getRGB(ring, y),
                    "Capture cage front border ring " + ring + " should use the unified outline color");
            assertEquals(CAPTURE_CAGE_OUTER_BORDER, front.getRGB(maxX, y),
                    "Capture cage front border ring " + ring + " should use the unified outline color");
        }
    }

    private static void assertFrontDoesNotUseThirdFullBorderRing(BufferedImage front) {
        int maxX = front.getWidth() - 1;
        int maxY = front.getHeight() - 1;
        assertTrue(
                countPixelsInRegion(front, 0, 2, front.getWidth(), 1, color -> color == CAPTURE_CAGE_OUTER_BORDER)
                        < front.getWidth() / 2,
                "Capture cage front should not keep a third full dark top border row"
        );
        assertTrue(
                countPixelsInRegion(front, 0, maxY - 2, front.getWidth(), 1, color -> color == CAPTURE_CAGE_OUTER_BORDER)
                        < front.getWidth() / 2,
                "Capture cage front should not keep a third full dark bottom border row"
        );
        assertTrue(
                countPixelsInRegion(front, 2, 0, 1, front.getHeight(), color -> color == CAPTURE_CAGE_OUTER_BORDER)
                        < front.getHeight() / 2,
                "Capture cage front should not keep a third full dark left border column"
        );
        assertTrue(
                countPixelsInRegion(front, maxX - 2, 0, 1, front.getHeight(), color -> color == CAPTURE_CAGE_OUTER_BORDER)
                        < front.getHeight() / 2,
                "Capture cage front should not keep a third full dark right border column"
        );
    }

    private static void assertFrontKeepsExtractedDoorWindows(BufferedImage front) {
        assertExtractedDoorWindow(front, 5);
        assertExtractedDoorWindow(front, 20);
        assertEquals(
                0,
                countPixelsInRegion(front, 14, 7, 4, 8, color -> color == CAPTURE_CAGE_WINDOW_PANE),
                "Capture cage windows should avoid the center seam"
        );
        assertEquals(
                0,
                countPixelsInRegion(front, 13, 18, 6, 8, color -> color == CAPTURE_CAGE_WINDOW_PANE),
                "Capture cage windows should avoid the lock"
        );
    }

    private static void assertFrontDoesNotKeepStrayOuterDarkStrokes(BufferedImage front) {
        for (int[] coordinate : List.of(
                new int[]{3, 2},
                new int[]{3, 3},
                new int[]{19, 2},
                new int[]{19, 3},
                new int[]{3, 14},
                new int[]{19, 14},
                new int[]{3, 19},
                new int[]{19, 19}
        )) {
            assertTrue(
                    front.getRGB(coordinate[0], coordinate[1]) != CAPTURE_CAGE_OUTER_BORDER,
                    "Capture cage front should not keep stray border-dark strokes near the door top or window sides"
            );
        }
    }

    private static void assertExtractedDoorWindow(BufferedImage front, int minX) {
        for (int x = minX; x <= minX + 6; x++) {
            assertEquals(CAPTURE_CAGE_WINDOW_PANE, front.getRGB(x, 7),
                    "Capture cage front should keep the raised upper window cap");
        }
        for (int x = minX; x <= minX + 6; x++) {
            assertEquals(CAPTURE_CAGE_WINDOW_PANE, front.getRGB(x, 14),
                    "Capture cage front should keep the raised lower window cap");
        }
        for (int y = 8; y <= 13; y++) {
            assertEquals(CAPTURE_CAGE_WINDOW_PANE, front.getRGB(minX, y),
                    "Capture cage front should keep the raised left window side");
            assertEquals(CAPTURE_CAGE_WINDOW_PANE, front.getRGB(minX + 6, y),
                    "Capture cage front should keep the raised right window side");
        }
        for (int x = minX + 1; x <= minX + 5; x++) {
            assertEquals(CAPTURE_CAGE_PANEL_SHADOW, front.getRGB(x, 9),
                    "Capture cage front should keep the raised upper window slat");
            assertEquals(CAPTURE_CAGE_PANEL_SHADOW, front.getRGB(x, 12),
                    "Capture cage front should keep the raised lower window slat");
        }
        assertEquals(
                0,
                countPixelsInRegion(front, minX, 15, 7, 1, color -> color == CAPTURE_CAGE_WINDOW_PANE),
                "Capture cage front should not leave the old lower window cap after raising the window"
        );
    }

    private static void assertFrontKeepsExtractedDoubleDoorStructure(BufferedImage front) {
        for (int y = 2; y <= 17; y++) {
            assertExtractedCenterDivider(front, y);
        }
        for (int y = 28; y <= 29; y++) {
            assertExtractedCenterDivider(front, y);
        }
        assertTrue(
                countPixelsInRegion(front, 0, 16, front.getWidth(), 1, color -> color == CAPTURE_CAGE_CROSSBAR) >= 18,
                "Capture cage front should keep the extracted full-width mid-door crossbar"
        );
        assertTrue(
                countPixelsInRegion(front, 4, 22, 9, 2, color -> color == CAPTURE_CAGE_PANEL_SHADOW) >= 8,
                "Capture cage front should keep the extracted lower-left door panel texture"
        );
        assertTrue(
                countPixelsInRegion(front, 19, 22, 9, 2, color -> color == CAPTURE_CAGE_PANEL_SHADOW) >= 8,
                "Capture cage front should keep the extracted lower-right door panel texture"
        );
        assertEquals(
                CAPTURE_CAGE_PANEL_SHADOW,
                front.getRGB(20, 15),
                "Capture cage front should keep the right-side vertical panel texture after raising the window"
        );
    }

    private static void assertExtractedCenterDivider(BufferedImage front, int y) {
        assertEquals(CAPTURE_CAGE_CROSSBAR, front.getRGB(15, y),
                "Capture cage front should keep the left side of the extracted center divider");
        assertEquals(CAPTURE_CAGE_OUTER_BORDER, front.getRGB(16, y),
                "Capture cage front should keep the right side of the extracted center divider");
    }

    private static void assertFrontKeepsUpperLatchWithoutLowerPadlock(BufferedImage front) {
        assertTrue(
                countPixelsInRegion(front, 13, 18, 6, 3, ItemTextureResourceTest::isCaptureCageBadgeGold) >= 16,
                "Capture cage front should keep the upper horizontal gold latch"
        );
        for (int y = 18; y <= 19; y++) {
            assertTrue(isCaptureCageBadgeGold(front.getRGB(15, y)),
                    "Capture cage front upper latch should cover the left center seam");
            assertTrue(isCaptureCageBadgeGold(front.getRGB(16, y)),
                    "Capture cage front upper latch should cover the right center seam");
            assertTrue(front.getRGB(15, y) != CAPTURE_CAGE_OUTER_BORDER);
            assertTrue(front.getRGB(16, y) != CAPTURE_CAGE_OUTER_BORDER);
        }
        assertEquals(
                0,
                countPixelsInRegion(
                        front,
                        13,
                        21,
                        7,
                        7,
                        color -> isCaptureCageBadgeGold(color) || isWarmDinosaurSilhouette(color)
                ),
                "Capture cage front should not keep the large lower yellow padlock"
        );
        for (int y = 21; y <= 27; y++) {
            assertEquals(CAPTURE_CAGE_CROSSBAR, front.getRGB(15, y),
                    "Capture cage center seam should continue below the upper latch");
            assertEquals(CAPTURE_CAGE_OUTER_BORDER, front.getRGB(16, y),
                    "Capture cage center seam should continue below the upper latch");
        }
    }

    private static void assertLongSideKeepsSingleLargeDinosaurBadge(BufferedImage texture) {
        int centerX = 16;
        int centerWidth = 32;
        int edgeWidth = 16;

        assertTrue(countPixelsInRegion(
                texture,
                centerX,
                0,
                centerWidth,
                texture.getHeight(),
                ItemTextureResourceTest::isCaptureCageBadgeGold
        ) >= 180);
        assertTrue(countPixelsInRegion(
                texture,
                centerX,
                0,
                centerWidth,
                texture.getHeight(),
                ItemTextureResourceTest::isWarmDinosaurSilhouette
        ) >= 120);
        assertTrue(countPixelsInRegion(
                texture,
                0,
                0,
                edgeWidth,
                texture.getHeight(),
                ItemTextureResourceTest::isCaptureCageBadgeGold
        ) <= 25);
        assertTrue(countPixelsInRegion(
                texture,
                texture.getWidth() - edgeWidth,
                0,
                edgeWidth,
                texture.getHeight(),
                ItemTextureResourceTest::isCaptureCageBadgeGold
        ) <= 25);
        assertTrue(countPixelsInRegion(
                texture,
                0,
                0,
                edgeWidth,
                texture.getHeight(),
                ItemTextureResourceTest::isWarmDinosaurSilhouette
        ) <= 25);
        assertTrue(countPixelsInRegion(
                texture,
                texture.getWidth() - edgeWidth,
                0,
                edgeWidth,
                texture.getHeight(),
                ItemTextureResourceTest::isWarmDinosaurSilhouette
        ) <= 25);
    }

    private static void assertLongSideBadgeIsRaisedAndCentered(BufferedImage texture) {
        PixelBounds badgeBounds = boundsOfPixels(
                texture,
                color -> isCaptureCageBadgeGold(color) || isWarmDinosaurSilhouette(color)
        );

        assertEquals(18, badgeBounds.minX());
        assertEquals(46, badgeBounds.maxX());
        assertEquals(4, badgeBounds.minY());
        assertEquals(27, badgeBounds.maxY());
        assertTrue(badgeBounds.centerX() >= 31.0D && badgeBounds.centerX() <= 33.0D);
        assertTrue(badgeBounds.centerY() >= 15.0D && badgeBounds.centerY() <= 16.1D);
    }

    private static boolean isDinosaurCaptureCageMainFace(String textureName) {
        return switch (textureName) {
            case "dinosaur_capture_cage_front.png",
                 "dinosaur_capture_cage_back.png",
                 "dinosaur_capture_cage_side_badge.png",
                 "dinosaur_capture_cage_side_bars.png",
                 "dinosaur_capture_cage_top.png",
                 "dinosaur_capture_cage_bottom.png" -> true;
            default -> false;
        };
    }

    private static void assertCaptureCageOuterBorder(String textureName, BufferedImage texture) {
        for (int x = 0; x < texture.getWidth(); x++) {
            assertEquals(CAPTURE_CAGE_OUTER_BORDER, texture.getRGB(x, 0),
                    textureName + " top border should use the unified capture cage outline color");
            assertEquals(CAPTURE_CAGE_OUTER_BORDER, texture.getRGB(x, texture.getHeight() - 1),
                    textureName + " bottom border should use the unified capture cage outline color");
        }
        for (int y = 0; y < texture.getHeight(); y++) {
            assertEquals(CAPTURE_CAGE_OUTER_BORDER, texture.getRGB(0, y),
                    textureName + " left border should use the unified capture cage outline color");
            assertEquals(CAPTURE_CAGE_OUTER_BORDER, texture.getRGB(texture.getWidth() - 1, y),
                    textureName + " right border should use the unified capture cage outline color");
        }
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

    private static boolean isContainerWhiteGray(int color) {
        int alpha = (color >>> 24) & 0xFF;
        int red = (color >>> 16) & 0xFF;
        int green = (color >>> 8) & 0xFF;
        int blue = color & 0xFF;
        return alpha != 0
                && red >= 150
                && green >= 150
                && blue >= 145
                && Math.abs(red - green) <= 35
                && Math.abs(green - blue) <= 35;
    }

    private static boolean isCaptureCageBadgeGold(int color) {
        int alpha = (color >>> 24) & 0xFF;
        int red = (color >>> 16) & 0xFF;
        int green = (color >>> 8) & 0xFF;
        int blue = color & 0xFF;
        return alpha != 0
                && red >= 150
                && green >= 95
                && green <= 230
                && blue <= 95
                && red > blue + 55
                && green > blue + 25;
    }

    private static boolean isWarmDinosaurSilhouette(int color) {
        int alpha = (color >>> 24) & 0xFF;
        int red = (color >>> 16) & 0xFF;
        int green = (color >>> 8) & 0xFF;
        int blue = color & 0xFF;
        return alpha != 0
                && red <= 95
                && green <= 85
                && blue <= 75
                && red >= blue;
    }

    private static boolean isCaptureCageDoorLine(int color) {
        int alpha = (color >>> 24) & 0xFF;
        int red = (color >>> 16) & 0xFF;
        int green = (color >>> 8) & 0xFF;
        int blue = color & 0xFF;
        return alpha != 0
                && red <= 95
                && green <= 110
                && blue <= 115
                && red <= blue + 25;
    }

    private static boolean isCaptureCageWindowDark(int color) {
        int alpha = (color >>> 24) & 0xFF;
        int red = (color >>> 16) & 0xFF;
        int green = (color >>> 8) & 0xFF;
        int blue = color & 0xFF;
        return alpha != 0 && red <= 25 && green <= 35 && blue <= 40;
    }

    private static boolean isCaptureCageWindowPixel(int color) {
        return color == CAPTURE_CAGE_WINDOW_PANE
                || color == CAPTURE_CAGE_OUTER_BORDER
                || color == CAPTURE_CAGE_PANEL_SHADOW
                || isCaptureCageWindowDark(color);
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

    private record PixelBounds(int minX, int minY, int maxX, int maxY, int count, long sumX, long sumY) {
        double centerX() {
            return (double) sumX / count;
        }

        double centerY() {
            return (double) sumY / count;
        }
    }

    @FunctionalInterface
    private interface ColorPredicate {
        boolean test(int color);
    }
}
