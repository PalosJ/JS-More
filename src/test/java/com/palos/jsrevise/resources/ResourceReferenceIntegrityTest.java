package com.palos.jsrevise.resources;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ResourceReferenceIntegrityTest {
    private static final Path PROJECT_ROOT = findProjectRoot();
    private static final Path ASSET_ROOT = PROJECT_ROOT.resolve("src/main/resources/assets/jsrevise");
    private static final Set<String> DYNAMIC_RENDERER_TEXTURES = Set.of(
            "block/dinosaur_capture_box_front",
            "block/dinosaur_capture_box_back",
            "block/dinosaur_capture_box_side_badge",
            "block/dinosaur_capture_box_side_bars",
            "block/dinosaur_capture_box_top",
            "block/dinosaur_capture_box_bottom",
            "block/broken_dinosaur_capture_box_front",
            "block/broken_dinosaur_capture_box_back",
            "block/broken_dinosaur_capture_box_side_badge",
            "block/broken_dinosaur_capture_box_side_bars",
            "block/broken_dinosaur_capture_box_top",
            "block/broken_dinosaur_capture_box_bottom",
            "block/broken_dinosaur_capture_box_debris_sheet"
    );

    @Test
    void everyOwnedPngHasAStaticModelReferenceOrExplicitRendererConsumer() throws IOException {
        Path textureRoot = ASSET_ROOT.resolve("textures");
        Path modelRoot = ASSET_ROOT.resolve("models");
        assertTrue(Files.isDirectory(textureRoot), "Missing JS-revise texture root");
        assertTrue(Files.isDirectory(modelRoot), "Missing JS-revise model root");

        String modelJson = readAllJson(modelRoot);
        List<String> unreferenced = new ArrayList<>();
        try (var paths = Files.walk(textureRoot)) {
            paths.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".png"))
                    .forEach(path -> {
                        String textureId = textureRoot.relativize(path)
                                .toString()
                                .replace('\\', '/');
                        textureId = textureId.substring(0, textureId.length() - ".png".length());
                        if (!modelJson.contains("\"jsrevise:" + textureId + "\"")
                                && !DYNAMIC_RENDERER_TEXTURES.contains(textureId)) {
                            unreferenced.add(textureId);
                        }
                    });
        }

        assertTrue(unreferenced.isEmpty(), "Unreferenced owned PNG resources: " + unreferenced);
    }

    @Test
    void dynamicRendererAllowlistMatchesConcreteRendererPaths() throws IOException {
        String rendererSources = Files.readString(PROJECT_ROOT.resolve(
                "src/main/java/com/palos/jsrevise/client/render/DinosaurCaptureCageRenderer.java"
        )) + Files.readString(PROJECT_ROOT.resolve(
                "src/main/java/com/palos/jsrevise/client/render/BrokenDinosaurCaptureBoxRenderer.java"
        ));

        for (String textureId : DYNAMIC_RENDERER_TEXTURES) {
            assertTrue(
                    Files.isRegularFile(ASSET_ROOT.resolve("textures/" + textureId + ".png")),
                    "Dynamic renderer texture is missing: " + textureId
            );
            assertTrue(
                    rendererSources.contains("\"textures/" + textureId + ".png\""),
                    "Dynamic texture allowlist entry has no concrete renderer consumer: " + textureId
            );
        }
    }

    private static String readAllJson(Path root) throws IOException {
        StringBuilder result = new StringBuilder();
        try (var paths = Files.walk(root)) {
            for (Path path : paths.filter(Files::isRegularFile)
                    .filter(file -> file.getFileName().toString().endsWith(".json"))
                    .toList()) {
                result.append(Files.readString(path));
            }
        }
        return result.toString();
    }

    private static Path findProjectRoot() {
        Path candidate = Path.of("").toAbsolutePath();
        while (candidate != null) {
            if (Files.isRegularFile(candidate.resolve("build.gradle"))) {
                return candidate;
            }
            candidate = candidate.getParent();
        }
        throw new IllegalStateException("Could not locate JS-revise project root from " + Path.of("").toAbsolutePath());
    }
}
