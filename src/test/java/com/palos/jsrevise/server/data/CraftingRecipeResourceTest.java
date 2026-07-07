package com.palos.jsrevise.server.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class CraftingRecipeResourceTest {
    private static final String RECIPE_ROOT = "/data/jsrevise/recipe/";

    @Test
    void craftingRecipesUseSingularRecipePathAndCurrentResultShape() throws IOException {
        for (RecipeExpectation recipe : expectedRecipes().values()) {
            String recipeText = readRecipeText(recipe.fileName());
            assertFalse(
                    recipeText.contains("jsrevise:dinosaur_capture_cage"),
                    recipe.fileName() + " must use the current capture box ID"
            );
            JsonObject root = parseRecipe(recipeText);
            assertEquals("minecraft:crafting_shaped", root.get("type").getAsString());
            assertEquals(recipe.category(), root.get("category").getAsString());

            JsonObject result = root.getAsJsonObject("result");
            assertNotNull(result);
            assertEquals(1, result.get("count").getAsInt());
            assertEquals(recipe.resultId(), result.get("id").getAsString());
            assertFalse(result.has("item"), recipe.fileName() + " must not use the legacy result.item field");

            assertNull(
                    CraftingRecipeResourceTest.class.getResource("/data/jsrevise/recipes/" + recipe.fileName()),
                    recipe.fileName() + " must stay under the 1.21.1 singular recipe path"
            );
        }
    }

    @Test
    void craftingRecipesMatchDecodedPatternsAndIngredients() throws IOException {
        for (RecipeExpectation recipe : expectedRecipes().values()) {
            JsonObject root = parseRecipe(readRecipeText(recipe.fileName()));
            assertPatternEquals(recipe.pattern(), root);
            assertKeyItemsEqual(recipe.keyItems(), root.getAsJsonObject("key"));
            assertAllPatternSymbolsHaveKeys(root);
        }
    }

    private static Map<String, RecipeExpectation> expectedRecipes() {
        return Map.of(
                "dino_doctor_goggles", new RecipeExpectation(
                        "dino_doctor_goggles.json",
                        "equipment",
                        "jsrevise:dino_doctor_goggles",
                        List.of(
                                "N N",
                                "GBG"
                        ),
                        Map.of(
                                'B', "jurassicsaga:guidebook",
                                'G', "minecraft:glass_pane",
                                'N', "minecraft:iron_nugget"
                        )
                ),
                "dinosaur_capture_box", new RecipeExpectation(
                        "dinosaur_capture_box.json",
                        "misc",
                        "jsrevise:dinosaur_capture_box",
                        List.of(
                                "III",
                                "IWD",
                                "III"
                        ),
                        Map.of(
                                'D', "minecraft:iron_door",
                                'I', "minecraft:iron_block",
                                'W', "minecraft:blaze_powder"
                        )
                ),
                "anesthetic_crossbow", new RecipeExpectation(
                        "anesthetic_crossbow.json",
                        "equipment",
                        "jsrevise:anesthetic_crossbow",
                        List.of(
                                "III",
                                "SHS",
                                " I "
                        ),
                        Map.of(
                                'H', "minecraft:tripwire_hook",
                                'I', "minecraft:iron_ingot",
                                'S', "minecraft:string"
                        )
                )
        );
    }

    private static String readRecipeText(String fileName) throws IOException {
        try (InputStream stream = CraftingRecipeResourceTest.class.getResourceAsStream(RECIPE_ROOT + fileName)) {
            assertNotNull(stream, "Missing recipe resource: " + fileName);
            try (InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                StringBuilder builder = new StringBuilder();
                char[] buffer = new char[1024];
                int read;
                while ((read = reader.read(buffer)) >= 0) {
                    builder.append(buffer, 0, read);
                }
                return builder.toString();
            }
        }
    }

    private static JsonObject parseRecipe(String recipeText) {
        return JsonParser.parseString(recipeText).getAsJsonObject();
    }

    private static void assertPatternEquals(List<String> expectedPattern, JsonObject root) {
        List<String> actualPattern = root.getAsJsonArray("pattern")
                .asList()
                .stream()
                .map(JsonElement::getAsString)
                .toList();
        assertEquals(expectedPattern, actualPattern);
    }

    private static void assertKeyItemsEqual(Map<Character, String> expectedKeyItems, JsonObject key) {
        assertEquals(expectedKeyItems.size(), key.size());
        for (Map.Entry<Character, String> entry : expectedKeyItems.entrySet()) {
            String symbol = String.valueOf(entry.getKey());
            JsonObject ingredient = key.getAsJsonObject(symbol);
            assertNotNull(ingredient, "Missing key symbol: " + symbol);
            assertEquals(entry.getValue(), ingredient.get("item").getAsString());
            assertFalse(ingredient.has("tag"), "Recipe key " + symbol + " should use an exact item reference");
        }
    }

    private static void assertAllPatternSymbolsHaveKeys(JsonObject root) {
        JsonObject key = root.getAsJsonObject("key");
        Set<Character> usedSymbols = new HashSet<>();
        for (JsonElement rowElement : root.getAsJsonArray("pattern")) {
            String row = rowElement.getAsString();
            assertTrue(row.length() <= 3, "Crafting pattern rows must fit a 3x3 grid");
            for (int i = 0; i < row.length(); i++) {
                char symbol = row.charAt(i);
                if (symbol != ' ') {
                    usedSymbols.add(symbol);
                    assertTrue(key.has(String.valueOf(symbol)), "Pattern uses undefined key: " + symbol);
                }
            }
        }

        assertEquals(usedSymbols.size(), key.size(), "Recipe should not define unused key symbols");
    }

    private record RecipeExpectation(
            String fileName,
            String category,
            String resultId,
            List<String> pattern,
            Map<Character, String> keyItems
    ) {
    }
}
