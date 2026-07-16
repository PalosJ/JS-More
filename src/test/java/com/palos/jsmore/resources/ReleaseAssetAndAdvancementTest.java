package com.palos.jsmore.resources;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

class ReleaseAssetAndAdvancementTest {
    private static final Path PROJECT_ROOT = findProjectRoot();
    private static final Path RESOURCE_ROOT = PROJECT_ROOT.resolve("src/main/resources");
    private static final Path RECIPE_ROOT = RESOURCE_ROOT.resolve("data/jsmore/recipe");
    private static final Path ADVANCEMENT_ROOT = RESOURCE_ROOT.resolve("data/jsmore/advancement");
    private static final String PROJECT_ICON_SHA256 =
            "77fbadb45e91c871d4165c05d3ab29bd48988e2e12bfc66a76c02d77f2ffde62";
    private static final String GOGGLES_TEXTURE_SHA256 =
            "20809b1d786cd12a97944948f06abca53a41c68d3bb16f4f994b1ad7ba47d84e";
    private static final Map<String, String> RECIPE_UNLOCK_INGREDIENTS = Map.of(
            "anesthetic_crossbow", "minecraft:tripwire_hook",
            "anesthetic_dart", "jsmore:anesthetic_syringe",
            "anesthetic_syringe", "jsmore:anesthetic_potion",
            "dino_doctor_goggles", "jurassicsaga:guidebook",
            "dinosaur_capture_box", "minecraft:iron_door"
    );
    private static final Map<String, String> FAMILY_MANIFEST_SHA256 = Map.of(
            "complete_capture_box", "50198b50fe597d7db07a5d7ca78a2f54aba98c22860684d6892ad7b5d167fc86",
            "broken_capture_box", "70817105001dc0ecf6153a35fdd3b97ce823d3667983fcd6c0702bbf90e439a3",
            "anesthetic_dart", "e62d4d7755fc5db5f463765d881002e890582be99b0cd6272be26ad18198b6a7",
            "anesthetic_crossbow", "22f984ce00e275d6c6042126ec003e887dc7692d3ab3602f1b4c5ccce69a6fa8",
            "anesthetic_potion", "f56577943de5aba7647388858dd20c2493a328a93793123b144d3c255c78666b",
            "anesthetic_syringe", "ef7f21660684f986dff6dd32ca0e62221cdf940f465575c13790a57e3b7e3783",
            "dino_doctor_goggles", "8708175b94ebfee7bcc85cf84f71561e4539bd2f23ff06bac8a5ec603b0dea1d"
    );
    private static final Map<String, Integer> FAMILY_FILE_COUNTS = Map.of(
            "complete_capture_box", 24,
            "broken_capture_box", 10,
            "anesthetic_dart", 3,
            "anesthetic_crossbow", 21,
            "anesthetic_potion", 2,
            "anesthetic_syringe", 1,
            "dino_doctor_goggles", 2
    );

    @Test
    void projectIconMatchesTheApprovedDeterministicDerivative() throws IOException {
        Path iconPath = RESOURCE_ROOT.resolve("jsmore.png");
        assertTrue(Files.isRegularFile(iconPath));
        assertEquals(PROJECT_ICON_SHA256, sha256(Files.readAllBytes(iconPath)));

        BufferedImage icon = ImageIO.read(iconPath.toFile());
        assertNotNull(icon);
        assertEquals(256, icon.getWidth());
        assertEquals(256, icon.getHeight());
        assertTrue(icon.getColorModel().hasAlpha());
        assertBinaryAlpha(icon);
        assertEquals(0, alpha(icon.getRGB(0, 0)));
        assertEquals(0, alpha(icon.getRGB(255, 0)));
        assertEquals(0, alpha(icon.getRGB(0, 255)));
        assertEquals(0, alpha(icon.getRGB(255, 255)));
    }

    @Test
    void gogglesTextureMatchesTheFrozenFileAndBasicPixelFormat() throws IOException {
        Path gogglesPath = RESOURCE_ROOT.resolve("assets/jsmore/textures/item/dino_doctor_goggles.png");
        assertEquals(GOGGLES_TEXTURE_SHA256, sha256(Files.readAllBytes(gogglesPath)));

        BufferedImage goggles = ImageIO.read(gogglesPath.toFile());
        assertNotNull(goggles);
        assertEquals(16, goggles.getWidth());
        assertEquals(16, goggles.getHeight());
        assertTrue(goggles.getColorModel().hasAlpha());
        assertBinaryAlpha(goggles);
    }

    @Test
    void hiddenRecipeUnlockAdvancementsCoverEveryCraftingRecipe() throws IOException {
        assertEquals(RECIPE_UNLOCK_INGREDIENTS.keySet(), jsonFileStems(RECIPE_ROOT));
        Path unlockRoot = ADVANCEMENT_ROOT.resolve("recipes");
        assertEquals(RECIPE_UNLOCK_INGREDIENTS.keySet(), jsonFileStems(unlockRoot));

        for (Map.Entry<String, String> entry : RECIPE_UNLOCK_INGREDIENTS.entrySet()) {
            String recipeId = "jsmore:" + entry.getKey();
            JsonObject root = readJson(unlockRoot.resolve(entry.getKey() + ".json"));
            assertFalse(root.has("display"), entry.getKey() + " recipe unlock must stay hidden");
            assertEquals("minecraft:recipes/root", root.get("parent").getAsString());

            JsonObject criteria = root.getAsJsonObject("criteria");
            assertEquals(Set.of("has_ingredient", "has_the_recipe"), criteria.keySet());
            JsonObject ingredient = criteria.getAsJsonObject("has_ingredient");
            assertEquals("minecraft:inventory_changed", ingredient.get("trigger").getAsString());
            assertEquals(
                    List.of(entry.getValue()),
                    criterionItemIds(ingredient),
                    entry.getKey() + " unlock must use a real recipe ingredient"
            );

            JsonObject recipeCriterion = criteria.getAsJsonObject("has_the_recipe");
            assertEquals("minecraft:recipe_unlocked", recipeCriterion.get("trigger").getAsString());
            assertEquals(recipeId,
                    recipeCriterion.getAsJsonObject("conditions").get("recipe").getAsString());
            assertEquals(
                    Set.of("has_ingredient", "has_the_recipe"),
                    stringSet(root.getAsJsonArray("requirements").get(0).getAsJsonArray())
            );
            assertEquals(
                    List.of(recipeId),
                    stringList(root.getAsJsonObject("rewards").getAsJsonArray("recipes"))
            );
        }
    }

    @Test
    void visibleAdvancementsDefineTheSurvivalTransportEntryAndGoal() throws IOException {
        JsonObject entry = readJson(ADVANCEMENT_ROOT.resolve("acquire_anesthetic_potion.json"));
        assertFalse(entry.has("parent"));
        assertVisibleDisplay(
                entry,
                "jsmore:anesthetic_potion",
                "advancement.jsmore.acquire_anesthetic_potion.title",
                "advancement.jsmore.acquire_anesthetic_potion.description"
        );
        assertEquals(
                List.of("jsmore:anesthetic_potion"),
                criterionItemIds(entry.getAsJsonObject("criteria").getAsJsonObject("has_anesthetic_potion"))
        );
        assertEquals(
                "minecraft:textures/gui/advancements/backgrounds/adventure.png",
                entry.getAsJsonObject("display").get("background").getAsString()
        );

        JsonObject transport = readJson(ADVANCEMENT_ROOT.resolve("transport_ready.json"));
        assertEquals("jsmore:acquire_anesthetic_potion", transport.get("parent").getAsString());
        assertVisibleDisplay(
                transport,
                "jsmore:dinosaur_capture_box",
                "advancement.jsmore.transport_ready.title",
                "advancement.jsmore.transport_ready.description"
        );
        assertEquals(
                Set.of(
                        "jsmore:anesthetic_crossbow",
                        "jsmore:anesthetic_dart",
                        "jsmore:dinosaur_capture_box"
                ),
                new HashSet<>(criterionItemIds(
                        transport.getAsJsonObject("criteria").getAsJsonObject("has_transport_kit")
                ))
        );
    }

    @Test
    void advancementLanguageKeysStayCompleteAndEquivalent() throws IOException {
        JsonObject enUs = readJson(RESOURCE_ROOT.resolve("assets/jsmore/lang/en_us.json"));
        JsonObject zhCn = readJson(RESOURCE_ROOT.resolve("assets/jsmore/lang/zh_cn.json"));
        assertEquals(enUs.keySet(), zhCn.keySet());

        for (String key : List.of(
                "advancement.jsmore.acquire_anesthetic_potion.title",
                "advancement.jsmore.acquire_anesthetic_potion.description",
                "advancement.jsmore.transport_ready.title",
                "advancement.jsmore.transport_ready.description"
        )) {
            assertTrue(enUs.has(key), "Missing en_us key " + key);
            assertTrue(zhCn.has(key), "Missing zh_cn key " + key);
            assertFalse(enUs.get(key).getAsString().isBlank());
            assertFalse(zhCn.get(key).getAsString().isBlank());
        }
    }

    @Test
    void resourceTreeContainsNoOldTechnicalNamespace() throws IOException {
        String oldLower = "js" + "revise";
        String oldCamel = "JS" + "Revise";
        String oldDashed = "JS" + "-revise";
        List<String> violations = new ArrayList<>();

        try (var paths = Files.walk(RESOURCE_ROOT)) {
            for (Path path : paths.filter(Files::isRegularFile).toList()) {
                String relative = RESOURCE_ROOT.relativize(path).toString().replace('\\', '/');
                if (relative.contains(oldLower) || relative.contains(oldCamel) || relative.contains(oldDashed)) {
                    violations.add(relative + " [path]");
                    continue;
                }
                if (isTextResource(path)) {
                    String text = Files.readString(path, StandardCharsets.UTF_8);
                    if (text.contains(oldLower) || text.contains(oldCamel) || text.contains(oldDashed)) {
                        violations.add(relative + " [content]");
                    }
                }
            }
        }

        assertEquals(List.of(), violations);
    }

    @Test
    void provenanceHasFinalHashesForEveryPackagedAssetFamily() throws IOException {
        String provenance = Files.readString(PROJECT_ROOT.resolve("ASSET_PROVENANCE.md"), StandardCharsets.UTF_8);
        assertFalse(provenance.contains("PENDING"), "Provenance must not retain an unresolved hash status");
        assertTrue(provenance.contains(PROJECT_ICON_SHA256.toUpperCase(Locale.ROOT)));
        assertTrue(provenance.contains(GOGGLES_TEXTURE_SHA256.toUpperCase(Locale.ROOT)));

        for (Map.Entry<String, String> entry : FAMILY_MANIFEST_SHA256.entrySet()) {
            List<String> paths = familyPaths(entry.getKey());
            assertEquals(FAMILY_FILE_COUNTS.get(entry.getKey()), paths.size(),
                    entry.getKey() + " family scope changed");
            assertEquals(entry.getValue(), familyManifestSha256(paths),
                    entry.getKey() + " family manifest changed");
            assertTrue(
                    provenance.contains(entry.getValue().toUpperCase(Locale.ROOT)),
                    "Provenance is missing " + entry.getKey() + " manifest hash"
            );
        }
    }

    private static void assertVisibleDisplay(JsonObject advancement, String iconId, String titleKey,
                                             String descriptionKey) {
        JsonObject display = advancement.getAsJsonObject("display");
        assertNotNull(display);
        assertEquals(iconId, display.getAsJsonObject("icon").get("id").getAsString());
        assertEquals(titleKey, display.getAsJsonObject("title").get("translate").getAsString());
        assertEquals(descriptionKey,
                display.getAsJsonObject("description").get("translate").getAsString());
        assertEquals("task", display.get("frame").getAsString());
        assertTrue(display.get("show_toast").getAsBoolean());
        assertTrue(display.get("announce_to_chat").getAsBoolean());
        assertFalse(display.get("hidden").getAsBoolean());
    }

    private static List<String> criterionItemIds(JsonObject criterion) {
        assertEquals("minecraft:inventory_changed", criterion.get("trigger").getAsString());
        JsonArray items = criterion.getAsJsonObject("conditions").getAsJsonArray("items");
        List<String> result = new ArrayList<>();
        for (JsonElement item : items) {
            result.add(item.getAsJsonObject().get("items").getAsString());
        }
        return result;
    }

    private static Set<String> jsonFileStems(Path root) throws IOException {
        try (var paths = Files.list(root)) {
            Set<String> result = new HashSet<>();
            paths.filter(Files::isRegularFile)
                    .map(path -> path.getFileName().toString())
                    .filter(name -> name.endsWith(".json"))
                    .map(name -> name.substring(0, name.length() - ".json".length()))
                    .forEach(result::add);
            return result;
        }
    }

    private static Set<String> stringSet(JsonArray array) {
        return new HashSet<>(stringList(array));
    }

    private static List<String> stringList(JsonArray array) {
        List<String> result = new ArrayList<>();
        for (JsonElement element : array) {
            result.add(element.getAsString());
        }
        return result;
    }

    private static JsonObject readJson(Path path) throws IOException {
        try (var reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        }
    }

    private static void assertBinaryAlpha(BufferedImage image) {
        Set<Integer> alphaValues = new HashSet<>();
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                alphaValues.add(alpha(image.getRGB(x, y)));
            }
        }
        assertEquals(Set.of(0, 255), alphaValues);
    }

    private static int alpha(int argb) {
        return argb >>> 24;
    }

    private static boolean isTextResource(Path path) {
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        return name.endsWith(".json")
                || name.endsWith(".toml")
                || name.endsWith(".mcmeta")
                || name.endsWith(".properties")
                || name.endsWith(".txt")
                || name.endsWith(".yml")
                || name.endsWith(".yaml");
    }

    private static List<String> familyPaths(String family) throws IOException {
        Predicate<String> predicate = switch (family) {
            case "complete_capture_box" -> path ->
                    path.equals("assets/jsmore/blockstates/dinosaur_capture_box.json")
                            || path.startsWith("assets/jsmore/models/block/dinosaur_capture_box_")
                            || path.equals("assets/jsmore/models/item/dinosaur_capture_box.json")
                            || path.startsWith("assets/jsmore/textures/block/dinosaur_capture_box_");
            case "broken_capture_box" -> path ->
                    path.equals("assets/jsmore/blockstates/broken_dinosaur_capture_box.json")
                            || path.equals("assets/jsmore/models/block/broken_dinosaur_capture_box.json")
                            || path.equals("assets/jsmore/models/item/broken_dinosaur_capture_box.json")
                            || path.startsWith("assets/jsmore/textures/block/broken_dinosaur_capture_box_");
            case "anesthetic_dart" -> path ->
                    path.equals("assets/jsmore/models/item/anesthetic_dart.json")
                            || path.equals("assets/jsmore/textures/item/anesthetic_dart.png")
                            || path.equals("assets/jsmore/textures/entity/projectiles/anesthetic_dart.png");
            case "anesthetic_crossbow" -> path ->
                    path.startsWith("assets/jsmore/models/item/anesthetic_crossbow")
                            || path.startsWith("assets/jsmore/textures/item/crossbow_");
            case "anesthetic_potion" -> path ->
                    path.equals("assets/jsmore/models/item/anesthetic_potion.json")
                            || path.equals("assets/jsmore/textures/item/anesthetic_potion.png");
            case "anesthetic_syringe" -> path ->
                    path.equals("assets/jsmore/models/item/anesthetic_syringe.json");
            case "dino_doctor_goggles" -> path ->
                    path.equals("assets/jsmore/models/item/dino_doctor_goggles.json")
                            || path.equals("assets/jsmore/textures/item/dino_doctor_goggles.png");
            default -> throw new IllegalArgumentException("Unknown asset family " + family);
        };

        try (var paths = Files.walk(RESOURCE_ROOT)) {
            return paths.filter(Files::isRegularFile)
                    .map(path -> RESOURCE_ROOT.relativize(path).toString().replace('\\', '/'))
                    .filter(predicate)
                    .sorted()
                    .toList();
        }
    }

    private static String familyManifestSha256(List<String> relativePaths) throws IOException {
        StringBuilder manifest = new StringBuilder();
        for (String relative : relativePaths) {
            manifest.append(relative)
                    .append('\t')
                    .append(sha256(Files.readAllBytes(RESOURCE_ROOT.resolve(relative))))
                    .append('\n');
        }
        return sha256(manifest.toString().getBytes(StandardCharsets.UTF_8));
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException exception) {
            throw new AssertionError("SHA-256 must be available", exception);
        }
    }

    private static Path findProjectRoot() {
        Path candidate = Path.of("").toAbsolutePath();
        while (candidate != null) {
            if (Files.isRegularFile(candidate.resolve("build.gradle"))) {
                return candidate;
            }
            candidate = candidate.getParent();
        }
        throw new IllegalStateException("Could not locate JS More project root from " + Path.of("").toAbsolutePath());
    }
}
