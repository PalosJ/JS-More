package com.palos.jsrevise.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

class ItemTextureResourceTest {
    private static final String ITEM_TEXTURE_ROOT = "/assets/jsrevise/textures/item/";
    private static final String BLOCK_TEXTURE_ROOT = "/assets/jsrevise/textures/block/";
    private static final String ENTITY_TEXTURE_ROOT = "/assets/jsrevise/textures/entity/projectiles/";
    private static final int ANESTHETIC_DARK_CYAN = 0xFF1987AB;
    private static final int ANESTHETIC_MID_CYAN = 0xFF2F98BB;
    private static final int ANESTHETIC_LIGHT_CYAN = 0xFF41ADD0;
    private static final int ANESTHETIC_BRIGHT_CYAN = 0xFF58BEDD;
    private static final String ANESTHETIC_DART_ENTITY_ARGB_GRID_SHA256 =
            "bd44fe55aa50129ff8afffe1de2781809184fb1d7edc98181685d8ce924dc5af";
    private static final Map<String, String> ANESTHETIC_DART_ITEM_ARGB_GRID_SHA256 = Map.ofEntries(
            Map.entry("anesthetic_dart.png", "dfac88670534523c5829ca6dd167ea8adcd2c23c701b65c98a7fb514d2870f4b"),
            Map.entry("crossbow_loaded_1.png", "99fdb38f9bc422d86acc2e11c9ec7efc1358c39e022de90c6c0bcd6d98cc352a"),
            Map.entry("crossbow_loaded_2.png", "b46300e0b6e6294ff4100ddcc7df513c590bdda4bcdd3db7c56676576b2f54f8"),
            Map.entry("crossbow_loaded_3.png", "6e9e307c6c80c7202ece8fcbfc41d2684d51d2ad431906573b893fc103f4ed8d"),
            Map.entry("crossbow_loaded_4.png", "5deb3be949337c697554a0d729c3ec502188dd8bdbbc9061d5a639f488e77d14"),
            Map.entry("crossbow_loaded_5.png", "bb63afed9b8698eedcc0a1e6007a594f41ceab2ff31ff4acd2a7abed8b0c57dd"),
            Map.entry("crossbow_loaded_6.png", "b2abefe56464ee218511f46735366712773a54a9a19ab87404cea306283feee1")
    );
    private static final List<ExpectedPixel> LOADED_DART_CORE_PIXELS = List.of(
            new ExpectedPixel(3, 3, 0xFF939393),
            new ExpectedPixel(4, 4, 0xFFE2E2E2),
            new ExpectedPixel(5, 5, 0xFFF5F5F5),
            new ExpectedPixel(6, 6, ANESTHETIC_BRIGHT_CYAN),
            new ExpectedPixel(7, 6, ANESTHETIC_MID_CYAN),
            new ExpectedPixel(7, 7, ANESTHETIC_LIGHT_CYAN),
            new ExpectedPixel(8, 7, ANESTHETIC_BRIGHT_CYAN),
            new ExpectedPixel(8, 8, 0xFFE2E2E2)
    );
    private static final List<ExpectedPixel> RESTORED_LOADED_CROSSBOW_PIXELS = List.of(
            new ExpectedPixel(6, 5, 0xFF222B33),
            new ExpectedPixel(5, 6, 0xFF222B33),
            new ExpectedPixel(6, 7, 0xFF52606B),
            new ExpectedPixel(7, 8, 0xFF52606B)
    );
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
            Map.entry("dinosaur_capture_box_front.png", new int[]{32, 32}),
            Map.entry("dinosaur_capture_box_back.png", new int[]{32, 32}),
            Map.entry("dinosaur_capture_box_side_badge.png", new int[]{64, 32}),
            Map.entry("dinosaur_capture_box_side_bars.png", new int[]{64, 32}),
            Map.entry("dinosaur_capture_box_top.png", new int[]{32, 64}),
            Map.entry("dinosaur_capture_box_bottom.png", new int[]{32, 64})
    );
    private static final Map<String, Integer> DINOSAUR_CAPTURE_CAGE_LIGHT_GRAY_MINIMUMS = Map.ofEntries(
            Map.entry("dinosaur_capture_box_front.png", 360),
            Map.entry("dinosaur_capture_box_back.png", 650),
            Map.entry("dinosaur_capture_box_side_badge.png", 950),
            Map.entry("dinosaur_capture_box_side_bars.png", 950),
            Map.entry("dinosaur_capture_box_top.png", 1300),
            Map.entry("dinosaur_capture_box_bottom.png", 150)
    );
    private static final Map<String, int[]> BROKEN_DINOSAUR_CAPTURE_BOX_BLOCK_TEXTURES = Map.ofEntries(
            Map.entry("broken_dinosaur_capture_box_front.png", new int[]{32, 32, 208}),
            Map.entry("broken_dinosaur_capture_box_back.png", new int[]{32, 32, 18}),
            Map.entry("broken_dinosaur_capture_box_side_badge.png", new int[]{64, 32, 566}),
            Map.entry("broken_dinosaur_capture_box_side_bars.png", new int[]{64, 32, 588}),
            Map.entry("broken_dinosaur_capture_box_top.png", new int[]{32, 64, 440}),
            Map.entry("broken_dinosaur_capture_box_bottom.png", new int[]{32, 64, 0}),
            Map.entry("broken_dinosaur_capture_box_debris_sheet.png", new int[]{64, 64, 2914})
    );
    private static final List<String> DINOSAUR_CAPTURE_CAGE_BLOCK_MODELS = List.of(
            "dinosaur_capture_box_x0_y0_z0",
            "dinosaur_capture_box_x1_y0_z0",
            "dinosaur_capture_box_x0_y0_z1",
            "dinosaur_capture_box_x1_y0_z1",
            "dinosaur_capture_box_x0_y0_z2",
            "dinosaur_capture_box_x1_y0_z2",
            "dinosaur_capture_box_x0_y0_z3",
            "dinosaur_capture_box_x1_y0_z3",
            "dinosaur_capture_box_x0_y1_z0",
            "dinosaur_capture_box_x1_y1_z0",
            "dinosaur_capture_box_x0_y1_z1",
            "dinosaur_capture_box_x1_y1_z1",
            "dinosaur_capture_box_x0_y1_z2",
            "dinosaur_capture_box_x1_y1_z2",
            "dinosaur_capture_box_x0_y1_z3",
            "dinosaur_capture_box_x1_y1_z3"
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
                Map.entry("crossbow_standby.png", 94),
                Map.entry("crossbow_pulling_0.png", 99),
                Map.entry("crossbow_pulling_1.png", 101),
                Map.entry("crossbow_pulling_2.png", 104),
                Map.entry("crossbow_loaded_1.png", 99),
                Map.entry("crossbow_loaded_2.png", 99),
                Map.entry("crossbow_loaded_3.png", 101),
                Map.entry("crossbow_loaded_4.png", 101),
                Map.entry("crossbow_loaded_5.png", 104),
                Map.entry("crossbow_loaded_6.png", 104)
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
    void anestheticPotionAndDartResourcesKeepPixelArtAndMedicineIdentity() throws IOException {
        String potionModel = readResourceText("/assets/jsrevise/models/item/anesthetic_potion.json");
        String dartModel = readResourceText("/assets/jsrevise/models/item/anesthetic_dart.json");
        assertGeneratedItemModelReferences(potionModel, "jsrevise:item/anesthetic_potion");
        assertGeneratedItemModelReferences(dartModel, "jsrevise:item/anesthetic_dart");

        BufferedImage potion = readTexture(ITEM_TEXTURE_ROOT + "anesthetic_potion.png");
        BufferedImage dart = readTexture(ITEM_TEXTURE_ROOT + "anesthetic_dart.png");
        BufferedImage entityDart = readTexture(ENTITY_TEXTURE_ROOT + "anesthetic_dart.png");

        assertPixelArtTexture(potion, 16, 16);
        assertPixelArtTexture(dart, 16, 16);
        assertPixelArtTexture(entityDart, 32, 32);
        assertMedicinePalette(potion);
        assertMedicinePalette(entityDart);

        assertTrue(countPixels(potion, ItemTextureResourceTest::isCyanMedicine) >= 24);
        assertTrue(countPixels(potion, ItemTextureResourceTest::isContainerWhiteGray) >= 12);

        assertEquals(13, countPixels(dart, ItemTextureResourceTest::isAnestheticDartItemCyanMedicine));
        assertTrue(countPixels(dart, color -> color == ANESTHETIC_MID_CYAN) > 0);
        assertTrue(countPixels(dart, color -> color == ANESTHETIC_LIGHT_CYAN) > 0);
        assertTrue(countPixels(dart, color -> color == ANESTHETIC_BRIGHT_CYAN) > 0);
        assertEquals(0, countPixels(dart, color -> color == ANESTHETIC_DARK_CYAN));
        assertEquals(0, countPixels(dart, color -> color == 0xFF313131));
        assertTrue(countPixels(dart, ItemTextureResourceTest::isContainerWhiteGray) >= 12);
        assertAnestheticDartMatchesEntityModelProportions(dart);

        assertTrue(countPixels(entityDart, ItemTextureResourceTest::isCyanMedicine) >= 40);
        assertTrue(countPixels(entityDart, ItemTextureResourceTest::isContainerWhiteGray) >= 250);
        assertEntityDartTailFeatherFacesExact(entityDart);
    }

    private static void assertAnestheticDartMatchesEntityModelProportions(BufferedImage dart) {
        PixelBounds dartBounds = boundsOfPixels(dart, ItemTextureResourceTest::isOpaque);
        assertEquals(1, dartBounds.minX());
        assertEquals(1, dartBounds.minY());
        assertEquals(14, dartBounds.maxX());
        assertEquals(14, dartBounds.maxY());
        assertEquals(49, dartBounds.count(), "The item dart should keep the approved side-profile silhouette");

        int tailPixels = 0;
        int middlePixels = 0;
        int needlePixels = 0;
        for (int y = 0; y < dart.getHeight(); y++) {
            for (int x = 0; x < dart.getWidth(); x++) {
                int d = x - y;
                boolean actualOpaque = isOpaque(dart.getRGB(x, y));
                if (!actualOpaque) {
                    continue;
                }
                if (d <= -6) {
                    tailPixels++;
                } else if (d <= 7) {
                    middlePixels++;
                } else {
                    needlePixels++;
                    assertEquals(15, x + y, "The forward needle must stay one pixel wide on the dart axis");
                }
            }
        }

        assertEquals(15, tailPixels, "The rear flange, narrow rod, and feather should keep their approved weight");
        assertEquals(31, middlePixels, "The cartridge and fittings should keep their approved visual weight");
        assertEquals(3, needlePixels, "The shortened forward needle should occupy three diagonal positions");
        assertEquals(List.of(-3, -1, 1, 3), opaqueDartCrossSection(dart, -6));
        assertEquals(List.of(0), opaqueDartCrossSection(dart, -7));
        assertEquals(List.of(-1, 1), opaqueDartCrossSection(dart, -8));
        assertEquals(List.of(0), opaqueDartCrossSection(dart, -9));
        assertEquals(List.of(-1, 1), opaqueDartCrossSection(dart, -10));
        assertEquals(List.of(-2, 0, 2), opaqueDartCrossSection(dart, -11));
        assertEquals(List.of(-1, 1), opaqueDartCrossSection(dart, -12));
        assertEquals(0xFFF5F5F5, dart.getRGB(14, 1));
        assertEquals(0xFFE2E2E2, dart.getRGB(13, 2));
        assertEquals(0xFF939393, dart.getRGB(12, 3));
        assertEquals(0, countEnclosedTransparentPixels(dart),
                "The dart may have open edge notches but must not contain enclosed transparent holes");
        assertEquals(0, countDartAxisAlphaMismatches(dart),
                "The dart silhouette must stay symmetric around x+y=15");
        assertSingleEightConnectedAlphaComponent(dart);
    }

    private static List<Integer> opaqueDartCrossSection(BufferedImage image, int targetQ) {
        List<Integer> crossSection = new ArrayList<>();
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                if (x - y == targetQ && isOpaque(image.getRGB(x, y))) {
                    crossSection.add(x + y - 15);
                }
            }
        }
        return crossSection;
    }

    private static int countEnclosedTransparentPixels(BufferedImage image) {
        boolean[][] exterior = new boolean[image.getHeight()][image.getWidth()];
        int[] queueX = new int[image.getWidth() * image.getHeight()];
        int[] queueY = new int[queueX.length];
        int head = 0;
        int tail = 0;

        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                boolean boundary = x == 0 || y == 0 || x == image.getWidth() - 1 || y == image.getHeight() - 1;
                if (boundary && isTransparent(image.getRGB(x, y)) && !exterior[y][x]) {
                    exterior[y][x] = true;
                    queueX[tail] = x;
                    queueY[tail++] = y;
                }
            }
        }

        int[][] directions = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        while (head < tail) {
            int x = queueX[head];
            int y = queueY[head++];
            for (int[] direction : directions) {
                int nextX = x + direction[0];
                int nextY = y + direction[1];
                if (nextX < 0 || nextX >= image.getWidth()
                        || nextY < 0 || nextY >= image.getHeight()
                        || exterior[nextY][nextX]
                        || isOpaque(image.getRGB(nextX, nextY))) {
                    continue;
                }
                exterior[nextY][nextX] = true;
                queueX[tail] = nextX;
                queueY[tail++] = nextY;
            }
        }

        int enclosed = 0;
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                if (isTransparent(image.getRGB(x, y)) && !exterior[y][x]) {
                    enclosed++;
                }
            }
        }
        return enclosed;
    }

    private static int countDartAxisAlphaMismatches(BufferedImage image) {
        assertEquals(image.getWidth(), image.getHeight());
        int mismatches = 0;
        int axisMax = image.getWidth() - 1;
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                if (isOpaque(image.getRGB(x, y))
                        != isOpaque(image.getRGB(axisMax - y, axisMax - x))) {
                    mismatches++;
                }
            }
        }
        return mismatches;
    }

    private static void assertEntityDartTailFeatherFacesExact(BufferedImage entityDart) {
        List<String> expectedFace = List.of(".LL.", "LHHL", "LHHL", "LHHL", "LHHL", ".LL.");
        for (int y = 24; y < 32; y++) {
            for (int x = 0; x < 32; x++) {
                int expected = 0x00000000;
                if (x < 16 && y < 30) {
                    char symbol = expectedFace.get(y - 24).charAt(x % 4);
                    if (symbol == 'L') {
                        expected = 0xFFE2E2E2;
                    } else if (symbol == 'H') {
                        expected = 0xFFF5F5F5;
                    }
                }
                assertEquals(expected, entityDart.getRGB(x, y),
                        "Unexpected entity tail atlas pixel at (" + x + ", " + y + ")");
            }
        }
        assertEquals(0, countPixelsInRegion(entityDart, 0, 24, 32, 8, color -> color == 0xFF313131));
        assertEquals(0, countPixelsInRegion(entityDart, 0, 24, 32, 8, color -> color == 0xFF5D686E));
    }

    private static void assertSingleEightConnectedAlphaComponent(BufferedImage image) {
        boolean[][] visited = new boolean[image.getHeight()][image.getWidth()];
        int[] queueX = new int[image.getWidth() * image.getHeight()];
        int[] queueY = new int[queueX.length];
        int head = 0;
        int tail = 0;
        findStart:
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                if (isOpaque(image.getRGB(x, y))) {
                    queueX[tail] = x;
                    queueY[tail++] = y;
                    visited[y][x] = true;
                    break findStart;
                }
            }
        }
        assertTrue(tail > 0, "The dart silhouette must contain opaque pixels");
        int connectedPixels = 0;

        while (head < tail) {
            int x = queueX[head];
            int y = queueY[head++];
            connectedPixels++;
            for (int offsetY = -1; offsetY <= 1; offsetY++) {
                for (int offsetX = -1; offsetX <= 1; offsetX++) {
                    int nextX = x + offsetX;
                    int nextY = y + offsetY;
                    if ((offsetX == 0 && offsetY == 0)
                            || nextX < 0 || nextX >= image.getWidth()
                            || nextY < 0 || nextY >= image.getHeight()
                            || visited[nextY][nextX]
                            || !isOpaque(image.getRGB(nextX, nextY))) {
                        continue;
                    }
                    visited[nextY][nextX] = true;
                    queueX[tail] = nextX;
                    queueY[tail++] = nextY;
                }
            }
        }

        assertEquals(countOpaquePixels(image), connectedPixels, "The dart silhouette must form one 8-connected component");
    }

    @Test
    void loadedCrossbowFramesKeepTheSharedDartCoreAndRestoredBowPixels() throws IOException {
        for (int loaded = 1; loaded <= 6; loaded++) {
            BufferedImage frame = readTexture(ITEM_TEXTURE_ROOT + "crossbow_loaded_" + loaded + ".png");
            assertPixelArtTexture(frame, 16, 16);
            assertExpectedPixels(frame, LOADED_DART_CORE_PIXELS, "Loaded frame " + loaded + " dart core");
            assertExpectedPixels(
                    frame,
                    RESTORED_LOADED_CROSSBOW_PIXELS,
                    "Loaded frame " + loaded + " restored crossbow body"
            );
        }
    }

    @Test
    void anestheticDartItemEntityAndLoadedFramesKeepApprovedArgbPixelGrids() throws IOException {
        for (Map.Entry<String, String> entry : ANESTHETIC_DART_ITEM_ARGB_GRID_SHA256.entrySet()) {
            BufferedImage texture = readTexture(ITEM_TEXTURE_ROOT + entry.getKey());
            assertPixelArtTexture(texture, 16, 16);
            assertEquals(
                    entry.getValue(),
                    sha256ArgbGrid(texture),
                    entry.getKey() + " must keep all 256 approved ARGB pixels"
            );
        }
        BufferedImage entityTexture = readTexture(ENTITY_TEXTURE_ROOT + "anesthetic_dart.png");
        assertPixelArtTexture(entityTexture, 32, 32);
        assertEquals(
                ANESTHETIC_DART_ENTITY_ARGB_GRID_SHA256,
                sha256ArgbGrid(entityTexture),
                "anesthetic_dart entity texture must keep all 1024 approved ARGB pixels"
        );
    }

    @Test
    void dinosaurCaptureCageResourcesStayReferencedAndPixelClean() throws IOException {
        String itemModel = readResourceText("/assets/jsrevise/models/item/dinosaur_capture_box.json");
        assertCaptureCageItemModelUsesBlockStyleBakedCuboid(itemModel);
        assertNull(
                ItemTextureResourceTest.class.getResource("/assets/jsrevise/textures/item/dinosaur_capture_box.png"),
                "Capture box item should not keep a standalone 16x16 item texture"
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
        BufferedImage front = readTexture(BLOCK_TEXTURE_ROOT + "dinosaur_capture_box_front.png");
        BufferedImage back = readTexture(BLOCK_TEXTURE_ROOT + "dinosaur_capture_box_back.png");
        assertFrontIsOnlyDoorPanel(front, back);
        assertFrontHasUnifiedOuterBorderAndCaps(front);
        assertFrontKeepsExtractedDoorWindows(front);
        assertFrontDoesNotKeepStrayOuterDarkStrokes(front);
        assertFrontKeepsExtractedDoubleDoorStructure(front);
        assertFrontKeepsUpperLatchWithoutLowerPadlock(front);
        BufferedImage sideBadge = readTexture(BLOCK_TEXTURE_ROOT + "dinosaur_capture_box_side_badge.png");
        BufferedImage sideBars = readTexture(BLOCK_TEXTURE_ROOT + "dinosaur_capture_box_side_bars.png");
        assertEquals(
                0,
                countDifferentPixels(sideBadge, sideBars),
                "Capture box side textures must stay pixel-identical"
        );
        assertLongSideKeepsSingleLargeDinosaurBadge(sideBadge);
        assertLongSideKeepsSingleLargeDinosaurBadge(sideBars);
        assertLongSideBadgeIsRaisedAndCentered(sideBadge);
        assertLongSideBadgeIsRaisedAndCentered(sideBars);

        String blockState = readResourceText("/assets/jsrevise/blockstates/dinosaur_capture_box.json");
        assertTrue(blockState.contains("\"variants\""));
        assertTrue(!blockState.contains("\"multipart\""));

        for (Map.Entry<String, Integer> facing : DINOSAUR_CAPTURE_CAGE_Y_ROTATIONS.entrySet()) {
            for (int offsetY = 0; offsetY < 2; offsetY++) {
                for (int offsetZ = 0; offsetZ < 4; offsetZ++) {
                    for (int offsetX = 0; offsetX < 2; offsetX++) {
                        String modelName = "dinosaur_capture_box_x" + offsetX + "_y" + offsetY + "_z" + offsetZ;
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
                "/assets/jsrevise/models/block/dinosaur_capture_box.json"
        ));

        String lootTable = readResourceText("/data/jsrevise/loot_table/blocks/dinosaur_capture_box.json");
        assertTrue(lootTable.contains("\"type\": \"minecraft:block\""));
        assertTrue(lootTable.contains("\"pools\": []"));

        String pickaxeTag = readResourceText("/data/minecraft/tags/block/mineable/pickaxe.json");
        String ironToolTag = readResourceText("/data/minecraft/tags/block/needs_iron_tool.json");
        String stoneToolTag = readResourceText("/data/minecraft/tags/block/needs_stone_tool.json");
        assertTagContains(pickaxeTag, "jsrevise:dinosaur_capture_box");
        assertTagContains(ironToolTag, "jsrevise:dinosaur_capture_box");
        assertTagDoesNotContain(stoneToolTag, "jsrevise:dinosaur_capture_box");
    }

    @Test
    void brokenDinosaurCaptureBoxResourcesStayStandaloneAndModelBased() throws IOException {
        String itemModel = readResourceText("/assets/jsrevise/models/item/broken_dinosaur_capture_box.json");
        String captureBoxItemModel = readResourceText("/assets/jsrevise/models/item/dinosaur_capture_box.json");
        assertBrokenCaptureBoxItemModelUsesBlockStyleBakedCuboid(itemModel, captureBoxItemModel);
        assertNull(
                ItemTextureResourceTest.class.getResource(
                        "/assets/jsrevise/textures/item/broken_dinosaur_capture_box.png"
                ),
                "Broken capture box should not keep a standalone 16x16 item texture"
        );

        String blockModel = readResourceText("/assets/jsrevise/models/block/broken_dinosaur_capture_box.json");
        assertTrue(blockModel.contains("jsrevise:block/broken_dinosaur_capture_box_side_badge"));
        assertTrue(
                !blockModel.contains("\"elements\"") || EMPTY_MODEL_ELEMENTS_PATTERN.matcher(blockModel).find(),
                "Broken capture box placed-state geometry should come from the visual-only BER"
        );
        assertTrue(!blockModel.contains("\"from\""));
        assertTrue(!blockModel.contains("\"to\""));
        assertTrue(!blockModel.contains("\"faces\""));

        for (Map.Entry<String, int[]> blockTexture : BROKEN_DINOSAUR_CAPTURE_BOX_BLOCK_TEXTURES.entrySet()) {
            BufferedImage texture = readTexture(BLOCK_TEXTURE_ROOT + blockTexture.getKey());
            int[] expected = blockTexture.getValue();
            assertEquals(expected[0], texture.getWidth());
            assertEquals(expected[1], texture.getHeight());
            assertEquals(expected[2], countPixels(texture, ItemTextureResourceTest::isTransparent),
                    blockTexture.getKey() + " should keep the approved broken alpha holes");
            assertEquals(0, countPartialAlphaPixels(texture),
                    blockTexture.getKey() + " should use only opaque or fully transparent pixels");
        }
        BufferedImage brokenFront = readTexture(BLOCK_TEXTURE_ROOT + "broken_dinosaur_capture_box_front.png");
        BufferedImage brokenBack = readTexture(BLOCK_TEXTURE_ROOT + "broken_dinosaur_capture_box_back.png");
        BufferedImage brokenSideBadge = readTexture(BLOCK_TEXTURE_ROOT + "broken_dinosaur_capture_box_side_badge.png");
        BufferedImage brokenSideBars = readTexture(BLOCK_TEXTURE_ROOT + "broken_dinosaur_capture_box_side_bars.png");
        BufferedImage brokenTop = readTexture(BLOCK_TEXTURE_ROOT + "broken_dinosaur_capture_box_top.png");
        BufferedImage brokenBottom = readTexture(BLOCK_TEXTURE_ROOT + "broken_dinosaur_capture_box_bottom.png");
        BufferedImage brokenDebrisSheet = readTexture(BLOCK_TEXTURE_ROOT + "broken_dinosaur_capture_box_debris_sheet.png");
        assertBrokenFrontKeepsReducedLatch(brokenFront);
        assertBrokenBackKeepsSmallTripleClawMarks(brokenBack);
        assertBrokenCaptureBoxCornersAreUneven(
                brokenFront,
                brokenBack,
                brokenSideBadge,
                brokenSideBars,
                brokenTop,
                brokenBottom
        );
        assertBrokenCornerHoleConnectsAcrossAdjacentFaces(
                brokenFront,
                brokenBack,
                brokenSideBadge,
                brokenSideBars,
                brokenTop
        );
        assertSmallVerticalCornerChipsConnectAcrossAdjacentFaces(
                brokenFront,
                brokenBack,
                brokenSideBadge,
                brokenSideBars,
                brokenTop
        );
        assertBrokenLongSideAlphaMasksStayDistinct(brokenSideBadge, brokenSideBars);
        assertBrokenDebrisSheetUvIslandsMatchSourceFaces(
                brokenDebrisSheet,
                brokenFront,
                brokenSideBadge,
                brokenSideBars,
                brokenTop
        );

        String blockState = readResourceText("/assets/jsrevise/blockstates/broken_dinosaur_capture_box.json");
        for (Map.Entry<String, Integer> facing : DINOSAUR_CAPTURE_CAGE_Y_ROTATIONS.entrySet()) {
            for (int offsetY = 0; offsetY < 2; offsetY++) {
                for (int offsetZ = 0; offsetZ < 4; offsetZ++) {
                    for (int offsetX = 0; offsetX < 2; offsetX++) {
                        String variantKey = "facing=" + facing.getKey()
                                + ",offset_x=" + offsetX
                                + ",offset_y=" + offsetY
                                + ",offset_z=" + offsetZ;
                        assertBlockStateVariantUsesModelAndRotation(
                                blockState,
                                variantKey,
                                "broken_dinosaur_capture_box",
                                facing.getValue()
                        );
                    }
                }
            }
        }
        assertTrue(!blockState.contains("\"multipart\""));

        String lootTable = readResourceText("/data/jsrevise/loot_table/blocks/broken_dinosaur_capture_box.json");
        assertTrue(lootTable.contains("\"type\": \"minecraft:block\""));
        assertTrue(lootTable.contains("\"name\": \"jsrevise:broken_dinosaur_capture_box\""));

        String pickaxeTag = readResourceText("/data/minecraft/tags/block/mineable/pickaxe.json");
        String ironToolTag = readResourceText("/data/minecraft/tags/block/needs_iron_tool.json");
        String stoneToolTag = readResourceText("/data/minecraft/tags/block/needs_stone_tool.json");
        assertTagContains(pickaxeTag, "jsrevise:broken_dinosaur_capture_box");
        assertTagContains(stoneToolTag, "jsrevise:broken_dinosaur_capture_box");
        assertTagDoesNotContain(ironToolTag, "jsrevise:broken_dinosaur_capture_box");
    }

    @Test
    void dinosaurCaptureBoxLanguageKeysUseNewRegistryId() throws IOException {
        String zhCn = readResourceText("/assets/jsrevise/lang/zh_cn.json");
        assertTrue(zhCn.contains("\"block.jsrevise.dinosaur_capture_box\": \"恐龙捕获箱\""));
        assertTrue(zhCn.contains("\"item.jsrevise.dinosaur_capture_box\": \"恐龙捕获箱\""));
        assertTrue(zhCn.contains("\"tooltip.jsrevise.dinosaur_capture_box.durability\": \"箱体耐久：%s\""));
        assertTrue(zhCn.contains("\"tooltip.jsrevise.dinosaur_capture_box.captured_duration\": \"捕获时长：%s\""));
        assertTrue(zhCn.contains("\"overlay.jsrevise.capture_box.duration\": \"捕获时长：\""));
        assertTrue(zhCn.contains("\"block.jsrevise.broken_dinosaur_capture_box\": \"破损的恐龙捕获箱\""));
        assertTrue(zhCn.contains("\"item.jsrevise.broken_dinosaur_capture_box\": \"破损的恐龙捕获箱\""));

        String enUs = readResourceText("/assets/jsrevise/lang/en_us.json");
        assertTrue(enUs.contains("\"block.jsrevise.dinosaur_capture_box\": \"Dinosaur Capture Box\""));
        assertTrue(enUs.contains("\"item.jsrevise.dinosaur_capture_box\": \"Dinosaur Capture Box\""));
        assertTrue(enUs.contains("\"tooltip.jsrevise.dinosaur_capture_box.durability\": \"Box Durability: %s\""));
        assertTrue(enUs.contains("\"tooltip.jsrevise.dinosaur_capture_box.captured_duration\": \"Capture Duration: %s\""));
        assertTrue(enUs.contains("\"overlay.jsrevise.capture_box.duration\": \"Capture Duration: \""));
        assertTrue(enUs.contains("\"block.jsrevise.broken_dinosaur_capture_box\": \"Broken Dinosaur Capture Box\""));
        assertTrue(enUs.contains("\"item.jsrevise.broken_dinosaur_capture_box\": \"Broken Dinosaur Capture Box\""));

        for (String key : List.of(
                "tooltip.jsrevise.dinosaur_capture_box.supplies_unreadable",
                "tooltip.jsrevise.dinosaur_capture_box.anesthetic_reserve",
                "tooltip.jsrevise.dinosaur_capture_box.carnivore_reserve",
                "tooltip.jsrevise.dinosaur_capture_box.herbivore_reserve",
                "overlay.jsrevise.capture_box.title",
                "overlay.jsrevise.capture_box.anesthetic_reserve",
                "overlay.jsrevise.capture_box.carnivore_reserve",
                "overlay.jsrevise.capture_box.herbivore_reserve",
                "format.jsrevise.duration.seconds",
                "format.jsrevise.duration.minutes",
                "format.jsrevise.duration.hours"
        )) {
            assertTrue(zhCn.contains("\"" + key + "\""), "Missing zh_cn key " + key);
            assertTrue(enUs.contains("\"" + key + "\""), "Missing en_us key " + key);
        }
    }

    @Test
    void loadedCrossbowStringReturnsOneDistinctSymmetricStepPerShot() throws IOException {
        List<BufferedImage> loadedStates = new ArrayList<>();
        List<Integer> adjacentDifferenceCounts = new ArrayList<>();

        for (int loaded = 1; loaded <= 6; loaded++) {
            BufferedImage current = readTexture(ITEM_TEXTURE_ROOT + "crossbow_loaded_" + loaded + ".png");
            assertEquals(current.getRGB(12, 3), current.getRGB(3, 12));
            assertEquals(current.getRGB(12, 4), current.getRGB(4, 12));
            assertEquals(isOpaque(current.getRGB(5, 12)), isOpaque(current.getRGB(12, 5)));
            assertEquals(isOpaque(current.getRGB(6, 12)), isOpaque(current.getRGB(12, 6)));
            for (BufferedImage earlier : loadedStates) {
                assertTrue(countDifferentPixels(earlier, current) > 0);
            }
            if (!loadedStates.isEmpty()) {
                adjacentDifferenceCounts.add(countDifferentPixels(loadedStates.get(loadedStates.size() - 1), current));
            }
            loadedStates.add(current);
        }

        assertEquals(
                List.of(5, 16, 12, 15, 4),
                adjacentDifferenceCounts,
                "Loaded crossbow string frames should keep the approved six-step progression"
        );
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
    void crossbowKeepsVanillaTopDownAnimationAndCompactForwardFacingDart() throws IOException {
        BufferedImage standby = readTexture(ITEM_TEXTURE_ROOT + "crossbow_standby.png");
        BufferedImage charged = readTexture(ITEM_TEXTURE_ROOT + "crossbow_loaded_6.png");
        double vanillaOverlap = alphaIntersectionOverUnion(standby, VANILLA_CROSSBOW_STANDBY_ALPHA);

        assertTrue(vanillaOverlap >= 0.72D && vanillaOverlap <= 0.90D);
        assertTrue(isTransparent(charged.getRGB(3, 2)));
        assertTrue(isTransparent(charged.getRGB(2, 3)));
        assertTrue(isAnestheticDartItemCyanMedicine(charged.getRGB(6, 6)));
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

    private static String sha256ArgbGrid(BufferedImage image) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (int y = 0; y < image.getHeight(); y++) {
                for (int x = 0; x < image.getWidth(); x++) {
                    int argb = image.getRGB(x, y);
                    digest.update((byte) (argb >>> 24));
                    digest.update((byte) (argb >>> 16));
                    digest.update((byte) (argb >>> 8));
                    digest.update((byte) argb);
                }
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new AssertionError("SHA-256 must be available", exception);
        }
    }

    private static String readResourceText(String path) throws IOException {
        try (InputStream stream = ItemTextureResourceTest.class.getResourceAsStream(path)) {
            assertNotNull(stream, "Missing resource: " + path);
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static JsonObject parseResourceJson(String resourceText) {
        return JsonParser.parseString(resourceText).getAsJsonObject();
    }

    private static void assertGeneratedItemModelReferences(String model, String textureId) {
        JsonObject modelJson = parseResourceJson(model);
        assertEquals("minecraft:item/generated", modelJson.get("parent").getAsString());
        assertEquals(textureId, modelJson.getAsJsonObject("textures").get("layer0").getAsString());
    }

    private static void assertPixelArtTexture(BufferedImage image, int expectedWidth, int expectedHeight) {
        assertEquals(expectedWidth, image.getWidth());
        assertEquals(expectedHeight, image.getHeight());
        assertEquals(0, countPartialAlphaPixels(image), "Pixel-art textures must use binary alpha");
    }

    private static void assertMedicinePalette(BufferedImage image) {
        assertTrue(countPixels(image, color -> color == ANESTHETIC_DARK_CYAN) > 0);
        assertTrue(countPixels(image, color -> color == ANESTHETIC_MID_CYAN) > 0);
        assertTrue(countPixels(image, color -> color == ANESTHETIC_LIGHT_CYAN) > 0);
    }

    private static void assertExpectedPixels(
            BufferedImage image,
            List<ExpectedPixel> expectedPixels,
            String messagePrefix
    ) {
        for (ExpectedPixel expected : expectedPixels) {
            assertEquals(
                    expected.argb(),
                    image.getRGB(expected.x(), expected.y()),
                    messagePrefix + " at (" + expected.x() + ", " + expected.y() + ")"
            );
        }
    }

    private static void assertBerPlaceholderModel(String modelName, String model) {
        assertTrue(
                model.contains("jsrevise:block/dinosaur_capture_box_side_badge"),
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
        assertTrue(!model.contains("item/generated"), "Capture box item should not use flat generated item rendering");
        assertTrue(
                !model.contains("jsrevise:item/dinosaur_capture_box"),
                "Capture box item should not reference the old 16x16 item texture"
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
                "dinosaur_capture_box_front",
                "dinosaur_capture_box_back",
                "dinosaur_capture_box_side_badge",
                "dinosaur_capture_box_side_bars",
                "dinosaur_capture_box_top",
                "dinosaur_capture_box_bottom"
        )) {
            assertTrue(
                    model.contains("jsrevise:block/" + texture),
                    "Capture box item model should reuse block texture " + texture
            );
        }
    }

    private static void assertBrokenCaptureBoxItemModelUsesBlockStyleBakedCuboid(
            String model,
            String captureBoxItemModel
    ) {
        JsonObject modelJson = parseResourceJson(model);
        JsonObject captureBoxModelJson = parseResourceJson(captureBoxItemModel);
        assertTrue(!model.contains("item/generated"), "Broken capture box should use a baked block model");
        assertTrue(model.contains("\"render_type\": \"minecraft:cutout\""));
        assertTrue(model.contains("\"elements\""));
        assertTrue(model.contains("\"display\""));
        assertTrue(model.contains("\"gui\""));
        assertTrue(model.contains("\"faces\""));
        JsonObject textures = modelJson.getAsJsonObject("textures");
        assertEquals(7, textures.size(), "Broken capture box item model should only keep body textures");
        assertFalse(textures.has("debris"), "World-only debris must not be baked into the item model");
        Map<String, String> expectedTextures = Map.ofEntries(
                Map.entry("particle", "jsrevise:block/broken_dinosaur_capture_box_side_badge"),
                Map.entry("front", "jsrevise:block/broken_dinosaur_capture_box_front"),
                Map.entry("back", "jsrevise:block/broken_dinosaur_capture_box_back"),
                Map.entry("side_badge", "jsrevise:block/broken_dinosaur_capture_box_side_badge"),
                Map.entry("side_bars", "jsrevise:block/broken_dinosaur_capture_box_side_bars"),
                Map.entry("top", "jsrevise:block/broken_dinosaur_capture_box_top"),
                Map.entry("bottom", "jsrevise:block/broken_dinosaur_capture_box_bottom")
        );
        for (Map.Entry<String, String> texture : expectedTextures.entrySet()) {
            assertEquals(texture.getValue(), textures.get(texture.getKey()).getAsString());
        }

        JsonArray elements = modelJson.getAsJsonArray("elements");
        assertEquals(1, elements.size(), "Broken capture box item model should contain only the box body");
        JsonObject body = elements.get(0).getAsJsonObject();
        assertJsonArrayEquals(new double[]{4.0D, 4.0D, 0.0D}, body.getAsJsonArray("from"));
        assertJsonArrayEquals(new double[]{12.0D, 12.0D, 16.0D}, body.getAsJsonArray("to"));

        JsonObject faces = body.getAsJsonObject("faces");
        Map<String, String> expectedFaces = Map.of(
                "north", "#front",
                "south", "#back",
                "west", "#side_badge",
                "east", "#side_bars",
                "up", "#top",
                "down", "#bottom"
        );
        assertEquals(expectedFaces.size(), faces.size());
        for (Map.Entry<String, String> face : expectedFaces.entrySet()) {
            JsonObject faceJson = faces.getAsJsonObject(face.getKey());
            assertEquals(face.getValue(), faceJson.get("texture").getAsString());
            assertJsonArrayEquals(new double[]{0.0D, 0.0D, 16.0D, 16.0D}, faceJson.getAsJsonArray("uv"));
        }
        assertFalse(model.contains("#debris"), "Broken capture box item elements must not reference world debris");
        assertEquals(
                captureBoxModelJson.getAsJsonObject("display"),
                modelJson.getAsJsonObject("display"),
                "Broken and complete capture boxes should keep identical item display transforms"
        );
    }

    private static void assertTagContains(String tagJson, String blockId) {
        assertTrue(tagContains(tagJson, blockId), blockId + " should be present in the tag");
    }

    private static void assertTagDoesNotContain(String tagJson, String blockId) {
        assertFalse(tagContains(tagJson, blockId), blockId + " should not be present in the tag");
    }

    private static boolean tagContains(String tagJson, String blockId) {
        JsonArray values = parseResourceJson(tagJson).getAsJsonArray("values");
        for (JsonElement value : values) {
            if (blockId.equals(tagValueId(value))) {
                return true;
            }
        }
        return false;
    }

    private static String tagValueId(JsonElement value) {
        if (value.isJsonObject()) {
            return value.getAsJsonObject().get("id").getAsString();
        }
        return value.getAsString();
    }

    private static void assertJsonArrayEquals(double[] expected, JsonArray actual) {
        assertEquals(expected.length, actual.size());
        for (int index = 0; index < expected.length; index++) {
            assertEquals(expected[index], actual.get(index).getAsDouble(), 1.0E-6D);
        }
    }

    private static void assertBrokenFrontKeepsReducedLatch(BufferedImage front) {
        PixelBounds goldBounds = boundsOfPixels(front, ItemTextureResourceTest::isCaptureCageBadgeGold);
        assertEquals(8, goldBounds.count());
        assertEquals(14, goldBounds.minX());
        assertEquals(18, goldBounds.minY());
        assertEquals(17, goldBounds.maxX());
        assertEquals(19, goldBounds.maxY());
        assertEquals(
                0,
                countPixelsInRegion(front, 13, 13, 8, 5, ItemTextureResourceTest::isCaptureCageBadgeGold),
                "Broken capture box front should not keep the oversized old latch"
        );
    }

    private static void assertBrokenBackKeepsSmallTripleClawMarks(BufferedImage back) {
        assertTrue(countPixelsInRegion(back, 7, 7, 6, 8, ItemTextureResourceTest::isCaptureCageDoorLine) >= 6);
        assertTrue(countPixelsInRegion(back, 13, 7, 5, 7, ItemTextureResourceTest::isCaptureCageDoorLine) >= 6);
        assertTrue(countPixelsInRegion(back, 18, 7, 7, 8, ItemTextureResourceTest::isCaptureCageDoorLine) >= 7);
        assertTrue(
                countPixelsInRegion(back, 2, 3, 28, 16, ItemTextureResourceTest::isCaptureCageDoorLine) <= 50,
                "Broken capture box back claw marks should stay small"
        );
    }

    private static void assertBrokenCaptureBoxCornersAreUneven(
            BufferedImage front,
            BufferedImage back,
            BufferedImage sideBadge,
            BufferedImage sideBars,
            BufferedImage top,
            BufferedImage bottom
    ) {
        assertEquals(13, countTransparentCorner(back, false, false));
        assertEquals(0, countTransparentCorner(back, true, false));
        assertEquals(0, countTransparentCorner(back, false, true));
        assertEquals(0, countTransparentCorner(back, true, true));
        assertEquals(0, countTransparentCorners(bottom));
        assertEquals(0, countTransparentCorner(front, false, true));
        assertEquals(0, countTransparentCorner(front, true, true));
        assertEquals(0, countTransparentCorner(sideBadge, false, true));
        assertEquals(0, countTransparentCorner(sideBadge, true, true));
        assertEquals(0, countTransparentCorner(sideBars, false, false));
        assertEquals(0, countTransparentCorner(sideBars, false, true));
        assertEquals(0, countTransparentCorner(sideBars, true, true));
        assertEquals(0, countTransparentCorner(top, true, true));
        assertTrue(countTransparentCorner(front, false, false) >= 10);
        assertTrue(countTransparentCorner(front, true, false) >= 10);
        assertTrue(countTransparentCorner(sideBadge, false, false) >= 10);
        assertTrue(countTransparentCorner(sideBadge, true, false) >= 10);
        assertTrue(countTransparentCorner(sideBars, true, false) >= 10);
        assertTrue(countTransparentCorner(top, false, false) >= 10);
        assertTrue(countTransparentCorner(top, true, false) >= 10);
        assertTrue(countTransparentCorner(top, false, true) >= 10);
    }

    private static void assertBrokenCornerHoleConnectsAcrossAdjacentFaces(
            BufferedImage front,
            BufferedImage back,
            BufferedImage sideBadge,
            BufferedImage sideBars,
            BufferedImage top
    ) {
        assertTrue(
                countPixelsInRegion(front, front.getWidth() - 1, 0, 1, 12, ItemTextureResourceTest::isTransparent)
                        >= 10,
                "Broken front right edge should open into the shared front-left-top corner"
        );
        assertTrue(
                countPixelsInRegion(sideBadge, 0, 0, 1, 12, ItemTextureResourceTest::isTransparent) >= 9,
                "Broken side_badge left edge should continue the shared corner hole"
        );
        assertTrue(
                countSharedTransparentVerticalEdge(front, front.getWidth() - 1, sideBadge, 0, 0, 12) >= 8,
                "Broken front and side_badge alpha masks should meet along the vertical corner edge"
        );
        assertTrue(
                countPixelsInRegion(front, front.getWidth() - 10, 0, 10, 1, ItemTextureResourceTest::isTransparent)
                        >= 6,
                "Broken front top edge should be open at the corner"
        );
        assertTrue(
                countPixelsInRegion(sideBadge, 0, 0, 12, 1, ItemTextureResourceTest::isTransparent) >= 8,
                "Broken side_badge top edge should be open at the same corner"
        );
        assertTrue(
                countPixelsInRegion(top, 0, 0, 12, 1, ItemTextureResourceTest::isTransparent) >= 9,
                "Broken top front-left edge should continue the same corner hole"
        );
        assertTrue(
                countPixelsInRegion(top, 0, 0, 1, 12, ItemTextureResourceTest::isTransparent) >= 9,
                "Broken top side-left edge should continue the same corner hole"
        );
        assertTrue(
                countPixelsInRegion(front, 0, 0, 1, 10, ItemTextureResourceTest::isTransparent) >= 5,
                "Broken front left edge should open into the shared front-right-top corner"
        );
        assertTrue(
                countPixelsInRegion(sideBars, sideBars.getWidth() - 1, 0, 1, 10,
                        ItemTextureResourceTest::isTransparent) >= 5,
                "Broken side_bars right edge should continue the shared corner hole"
        );
        assertTrue(
                countSharedTransparentVerticalEdge(front, 0, sideBars, sideBars.getWidth() - 1, 0, 10) >= 5,
                "Broken front and side_bars alpha masks should meet along the vertical corner edge"
        );
        assertTrue(
                countPixelsInRegion(front, 0, 0, 10, 1, ItemTextureResourceTest::isTransparent) >= 5,
                "Broken front top-left edge should be open at the corner"
        );
        assertTrue(
                countPixelsInRegion(sideBars, sideBars.getWidth() - 10, 0, 10, 1,
                        ItemTextureResourceTest::isTransparent) >= 5,
                "Broken side_bars top-right edge should be open at the same corner"
        );
        assertTrue(
                countPixelsInRegion(top, top.getWidth() - 10, 0, 10, 1,
                        ItemTextureResourceTest::isTransparent) >= 5,
                "Broken top front-right edge should continue the same corner hole"
        );
        assertTrue(
                countPixelsInRegion(top, top.getWidth() - 1, 0, 1, 10,
                        ItemTextureResourceTest::isTransparent) >= 5,
                "Broken top side-right edge should continue the same corner hole"
        );
        assertTrue(
                countPixelsInRegion(sideBadge, sideBadge.getWidth() - 1, 0, 1, 10,
                        ItemTextureResourceTest::isTransparent) >= 5,
                "Broken side_badge right edge should open into the shared rear-left-top corner"
        );
        assertTrue(
                countPixelsInRegion(back, 0, 0, 1, 10, ItemTextureResourceTest::isTransparent) >= 5,
                "Broken back left edge should continue the shared rear-left-top corner"
        );
        assertTrue(
                countSharedTransparentVerticalEdge(sideBadge, sideBadge.getWidth() - 1, back, 0, 0, 10) >= 5,
                "Broken side_badge and back alpha masks should meet along the rear-left vertical corner edge"
        );
        assertTrue(
                countPixelsInRegion(sideBadge, sideBadge.getWidth() - 10, 0, 10, 1,
                        ItemTextureResourceTest::isTransparent) >= 5,
                "Broken side_badge top-right edge should be open at the corner"
        );
        assertTrue(
                countPixelsInRegion(back, 0, 0, 10, 1, ItemTextureResourceTest::isTransparent) >= 5,
                "Broken back top-left edge should be open at the same rear-left-top corner"
        );
        assertTrue(
                countPixelsInRegion(top, 0, top.getHeight() - 1, 10, 1,
                        ItemTextureResourceTest::isTransparent) >= 5,
                "Broken top rear-left edge should continue the same corner hole"
        );
        assertTrue(
                countSharedTransparentHorizontalEdge(back, 0, top, top.getHeight() - 1, 0, 10) >= 5,
                "Broken back and top alpha masks should meet along the rear-left top edge"
        );
        assertTrue(
                countPixelsInRegion(top, 0, top.getHeight() - 10, 1, 10,
                        ItemTextureResourceTest::isTransparent) >= 5,
                "Broken top side-left rear edge should continue the same corner hole"
        );
    }

    private static void assertSmallVerticalCornerChipsConnectAcrossAdjacentFaces(
            BufferedImage front,
            BufferedImage back,
            BufferedImage sideBadge,
            BufferedImage sideBars,
            BufferedImage top
    ) {
        assertEquals(
                3,
                countPixelsInRegion(sideBadge, sideBadge.getWidth() - 1, 11, 1, 3,
                        ItemTextureResourceTest::isTransparent),
                "Broken side_badge right edge should keep the small rear-left vertical chip"
        );
        assertEquals(
                3,
                countPixelsInRegion(back, 0, 11, 1, 3, ItemTextureResourceTest::isTransparent),
                "Broken back left edge should continue the side_badge vertical chip"
        );
        assertEquals(
                3,
                countSharedTransparentVerticalEdge(sideBadge, sideBadge.getWidth() - 1, back, 0, 11, 3),
                "Broken side_badge and back alpha masks should meet along the small rear-left vertical chip"
        );
        assertEquals(
                3,
                countPixelsInRegion(sideBars, sideBars.getWidth() - 1, 11, 1, 3,
                        ItemTextureResourceTest::isTransparent),
                "Broken side_bars right edge should keep the small front-left vertical chip"
        );
        assertEquals(
                3,
                countPixelsInRegion(front, 0, 11, 1, 3, ItemTextureResourceTest::isTransparent),
                "Broken front left edge should continue the side_bars vertical chip"
        );
        assertEquals(
                3,
                countSharedTransparentVerticalEdge(sideBars, sideBars.getWidth() - 1, front, 0, 11, 3),
                "Broken side_bars and front alpha masks should meet along the small front-left vertical chip"
        );
        assertTrue(
                isTransparent(top.getRGB(0, 22)) && isTransparent(sideBadge.getRGB(22, 0)),
                "Broken top left-edge chip should continue onto the side_badge top edge"
        );
        assertTrue(
                countPixelsInRegion(sideBadge, 22, 0, 2, 2, ItemTextureResourceTest::isTransparent) >= 3,
                "Broken side_badge top-edge companion chip should not remain a single isolated pixel"
        );
        assertTrue(
                isTransparent(top.getRGB(top.getWidth() - 1, 34)) && isTransparent(sideBars.getRGB(29, 0)),
                "Broken top right-edge chip should continue onto the side_bars top edge"
        );
        assertTrue(
                countPixelsInRegion(sideBars, 28, 0, 2, 2, ItemTextureResourceTest::isTransparent) >= 3,
                "Broken side_bars top-edge companion chip should not remain a single isolated pixel"
        );
    }

    private static void assertBrokenLongSideAlphaMasksStayDistinct(BufferedImage sideBadge, BufferedImage sideBars) {
        double holeOverlap = transparentAlphaIntersectionOverUnion(sideBadge, sideBars);
        assertTrue(
                holeOverlap < 0.85D,
                "Broken long side alpha masks should not be near-identical; IoU was " + holeOverlap
        );
    }

    private static void assertBrokenDebrisSheetUvIslandsMatchSourceFaces(
            BufferedImage debrisSheet,
            BufferedImage front,
            BufferedImage sideBadge,
            BufferedImage sideBars,
            BufferedImage top
    ) {
        assertDebrisIslandSamplesFace(debrisSheet, front, 0, 0, 17, 15, 2, 14, 85, "front_left_door_shard");
        assertDebrisIslandSamplesFace(debrisSheet, front, 22, 0, 18, 19, 14, 13, 180, "front_right_latch_shard");
        assertDebrisIslandSamplesFace(debrisSheet, sideBadge, 42, 0, 21, 21, 16, 4, 250, "left_side_panel_shard");
        assertDebrisIslandSamplesFace(debrisSheet, sideBars, 0, 26, 16, 16, 4, 12, 60, "right_side_rail_shard");
        assertDebrisIslandSamplesFace(debrisSheet, sideBars, 18, 44, 18, 14, 28, 12, 100,
                "right_side_mid_hole_shard");
        assertDebrisIslandSamplesFace(debrisSheet, top, 22, 31, 19, 11, 0, 0, 85, "top_front_panel_flap");
        assertDebrisIslandSamplesFace(debrisSheet, top, 43, 32, 16, 19, 14, 0, 170, "top_side_panel_flap");
    }

    private static void assertDebrisIslandSamplesFace(
            BufferedImage debrisSheet,
            BufferedImage source,
            int debrisX,
            int debrisY,
            int width,
            int height,
            int sourceX,
            int sourceY,
            int minimumMatchingOpaquePixels,
            String name
    ) {
        int matchingOpaquePixels = 0;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int debrisColor = debrisSheet.getRGB(debrisX + x, debrisY + y);
                if (isOpaque(debrisColor) && debrisColor == source.getRGB(sourceX + x, sourceY + y)) {
                    matchingOpaquePixels++;
                }
            }
        }
        assertTrue(
                matchingOpaquePixels >= minimumMatchingOpaquePixels,
                name + " should sample opaque pixels from its matching broken capture box face"
        );
    }

    private static int countTransparentCorners(BufferedImage image) {
        return countTransparentCorner(image, false, false)
                + countTransparentCorner(image, true, false)
                + countTransparentCorner(image, false, true)
                + countTransparentCorner(image, true, true);
    }

    private static int countTransparentCorner(BufferedImage image, boolean right, boolean bottom) {
        return countPixelsInRegion(
                image,
                right ? image.getWidth() - 4 : 0,
                bottom ? image.getHeight() - 4 : 0,
                4,
                4,
                ItemTextureResourceTest::isTransparent
        );
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

    private static int countSharedTransparentVerticalEdge(
            BufferedImage first,
            int firstX,
            BufferedImage second,
            int secondX,
            int minY,
            int height
    ) {
        int count = 0;
        for (int y = minY; y < minY + height; y++) {
            if (isTransparent(first.getRGB(firstX, y)) && isTransparent(second.getRGB(secondX, y))) {
                count++;
            }
        }
        return count;
    }

    private static int countSharedTransparentHorizontalEdge(
            BufferedImage first,
            int firstY,
            BufferedImage second,
            int secondY,
            int minX,
            int width
    ) {
        int count = 0;
        for (int x = minX; x < minX + width; x++) {
            if (isTransparent(first.getRGB(x, firstY)) && isTransparent(second.getRGB(x, secondY))) {
                count++;
            }
        }
        return count;
    }

    private static double transparentAlphaIntersectionOverUnion(BufferedImage first, BufferedImage second) {
        assertEquals(first.getWidth(), second.getWidth());
        assertEquals(first.getHeight(), second.getHeight());

        int intersection = 0;
        int union = 0;
        for (int y = 0; y < first.getHeight(); y++) {
            for (int x = 0; x < first.getWidth(); x++) {
                boolean firstTransparent = isTransparent(first.getRGB(x, y));
                boolean secondTransparent = isTransparent(second.getRGB(x, y));
                if (firstTransparent && secondTransparent) {
                    intersection++;
                }
                if (firstTransparent || secondTransparent) {
                    union++;
                }
            }
        }
        return union == 0 ? 0.0D : (double) intersection / union;
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
                    "Capture box front border ring " + ring + " should use the unified outline color");
            assertEquals(CAPTURE_CAGE_OUTER_BORDER, front.getRGB(x, maxY),
                    "Capture box front border ring " + ring + " should use the unified outline color");
        }
        for (int y = ring + 1; y < maxY; y++) {
            assertEquals(CAPTURE_CAGE_OUTER_BORDER, front.getRGB(ring, y),
                    "Capture box front border ring " + ring + " should use the unified outline color");
            assertEquals(CAPTURE_CAGE_OUTER_BORDER, front.getRGB(maxX, y),
                    "Capture box front border ring " + ring + " should use the unified outline color");
        }
    }

    private static void assertFrontDoesNotUseThirdFullBorderRing(BufferedImage front) {
        int maxX = front.getWidth() - 1;
        int maxY = front.getHeight() - 1;
        assertTrue(
                countPixelsInRegion(front, 0, 2, front.getWidth(), 1, color -> color == CAPTURE_CAGE_OUTER_BORDER)
                        < front.getWidth() / 2,
                "Capture box front should not keep a third full dark top border row"
        );
        assertTrue(
                countPixelsInRegion(front, 0, maxY - 2, front.getWidth(), 1, color -> color == CAPTURE_CAGE_OUTER_BORDER)
                        < front.getWidth() / 2,
                "Capture box front should not keep a third full dark bottom border row"
        );
        assertTrue(
                countPixelsInRegion(front, 2, 0, 1, front.getHeight(), color -> color == CAPTURE_CAGE_OUTER_BORDER)
                        < front.getHeight() / 2,
                "Capture box front should not keep a third full dark left border column"
        );
        assertTrue(
                countPixelsInRegion(front, maxX - 2, 0, 1, front.getHeight(), color -> color == CAPTURE_CAGE_OUTER_BORDER)
                        < front.getHeight() / 2,
                "Capture box front should not keep a third full dark right border column"
        );
    }

    private static void assertFrontKeepsExtractedDoorWindows(BufferedImage front) {
        assertExtractedDoorWindow(front, 5);
        assertExtractedDoorWindow(front, 20);
        assertEquals(
                0,
                countPixelsInRegion(front, 14, 7, 4, 8, color -> color == CAPTURE_CAGE_WINDOW_PANE),
                "Capture box windows should avoid the center seam"
        );
        assertEquals(
                0,
                countPixelsInRegion(front, 13, 18, 6, 8, color -> color == CAPTURE_CAGE_WINDOW_PANE),
                "Capture box windows should avoid the lock"
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
                    "Capture box front should not keep stray border-dark strokes near the door top or window sides"
            );
        }
    }

    private static void assertExtractedDoorWindow(BufferedImage front, int minX) {
        for (int x = minX; x <= minX + 6; x++) {
            assertEquals(CAPTURE_CAGE_WINDOW_PANE, front.getRGB(x, 7),
                    "Capture box front should keep the raised upper window cap");
        }
        for (int x = minX; x <= minX + 6; x++) {
            assertEquals(CAPTURE_CAGE_WINDOW_PANE, front.getRGB(x, 14),
                    "Capture box front should keep the raised lower window cap");
        }
        for (int y = 8; y <= 13; y++) {
            assertEquals(CAPTURE_CAGE_WINDOW_PANE, front.getRGB(minX, y),
                    "Capture box front should keep the raised left window side");
            assertEquals(CAPTURE_CAGE_WINDOW_PANE, front.getRGB(minX + 6, y),
                    "Capture box front should keep the raised right window side");
        }
        for (int x = minX + 1; x <= minX + 5; x++) {
            assertEquals(CAPTURE_CAGE_PANEL_SHADOW, front.getRGB(x, 9),
                    "Capture box front should keep the raised upper window slat");
            assertEquals(CAPTURE_CAGE_PANEL_SHADOW, front.getRGB(x, 12),
                    "Capture box front should keep the raised lower window slat");
        }
        assertEquals(
                0,
                countPixelsInRegion(front, minX, 15, 7, 1, color -> color == CAPTURE_CAGE_WINDOW_PANE),
                "Capture box front should not leave the old lower window cap after raising the window"
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
                "Capture box front should keep the extracted full-width mid-door crossbar"
        );
        assertTrue(
                countPixelsInRegion(front, 4, 22, 9, 2, color -> color == CAPTURE_CAGE_PANEL_SHADOW) >= 8,
                "Capture box front should keep the extracted lower-left door panel texture"
        );
        assertTrue(
                countPixelsInRegion(front, 19, 22, 9, 2, color -> color == CAPTURE_CAGE_PANEL_SHADOW) >= 8,
                "Capture box front should keep the extracted lower-right door panel texture"
        );
        assertEquals(
                CAPTURE_CAGE_PANEL_SHADOW,
                front.getRGB(20, 15),
                "Capture box front should keep the right-side vertical panel texture after raising the window"
        );
    }

    private static void assertExtractedCenterDivider(BufferedImage front, int y) {
        assertEquals(CAPTURE_CAGE_CROSSBAR, front.getRGB(15, y),
                "Capture box front should keep the left side of the extracted center divider");
        assertEquals(CAPTURE_CAGE_OUTER_BORDER, front.getRGB(16, y),
                "Capture box front should keep the right side of the extracted center divider");
    }

    private static void assertFrontKeepsUpperLatchWithoutLowerPadlock(BufferedImage front) {
        assertTrue(
                countPixelsInRegion(front, 13, 18, 6, 3, ItemTextureResourceTest::isCaptureCageBadgeGold) >= 16,
                "Capture box front should keep the upper horizontal gold latch"
        );
        for (int y = 18; y <= 19; y++) {
            assertTrue(isCaptureCageBadgeGold(front.getRGB(15, y)),
                    "Capture box front upper latch should cover the left center seam");
            assertTrue(isCaptureCageBadgeGold(front.getRGB(16, y)),
                    "Capture box front upper latch should cover the right center seam");
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
                "Capture box front should not keep the large lower yellow padlock"
        );
        for (int y = 21; y <= 27; y++) {
            assertEquals(CAPTURE_CAGE_CROSSBAR, front.getRGB(15, y),
                    "Capture box center seam should continue below the upper latch");
            assertEquals(CAPTURE_CAGE_OUTER_BORDER, front.getRGB(16, y),
                    "Capture box center seam should continue below the upper latch");
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
            case "dinosaur_capture_box_front.png",
                 "dinosaur_capture_box_back.png",
                 "dinosaur_capture_box_side_badge.png",
                 "dinosaur_capture_box_side_bars.png",
                 "dinosaur_capture_box_top.png",
                 "dinosaur_capture_box_bottom.png" -> true;
            default -> false;
        };
    }

    private static void assertCaptureCageOuterBorder(String textureName, BufferedImage texture) {
        for (int x = 0; x < texture.getWidth(); x++) {
            assertEquals(CAPTURE_CAGE_OUTER_BORDER, texture.getRGB(x, 0),
                    textureName + " top border should use the unified capture box outline color");
            assertEquals(CAPTURE_CAGE_OUTER_BORDER, texture.getRGB(x, texture.getHeight() - 1),
                    textureName + " bottom border should use the unified capture box outline color");
        }
        for (int y = 0; y < texture.getHeight(); y++) {
            assertEquals(CAPTURE_CAGE_OUTER_BORDER, texture.getRGB(0, y),
                    textureName + " left border should use the unified capture box outline color");
            assertEquals(CAPTURE_CAGE_OUTER_BORDER, texture.getRGB(texture.getWidth() - 1, y),
                    textureName + " right border should use the unified capture box outline color");
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

    private static boolean isAnestheticDartItemCyanMedicine(int color) {
        return color == ANESTHETIC_MID_CYAN
                || color == ANESTHETIC_LIGHT_CYAN
                || color == ANESTHETIC_BRIGHT_CYAN;
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

    private record ExpectedPixel(int x, int y, int argb) {
    }

    @FunctionalInterface
    private interface ColorPredicate {
        boolean test(int color);
    }
}
