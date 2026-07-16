package com.palos.jsmore.identity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.palos.jsmore.JSMore;
import com.palos.jsmore.server.registry.JSMoreAttachments;
import com.palos.jsmore.server.registry.JSMoreBlockEntityTypes;
import com.palos.jsmore.server.registry.JSMoreBlocks;
import com.palos.jsmore.server.registry.JSMoreCreativeTabs;
import com.palos.jsmore.server.registry.JSMoreEntityTypes;
import com.palos.jsmore.server.registry.JSMoreItems;
import com.palos.jsmore.server.registry.JSMoreRecipeSerializers;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import net.neoforged.neoforge.registries.DeferredHolder;
import org.junit.jupiter.api.Test;

final class ProjectIdentityTest {
    private static final List<String> OLD_TECHNICAL_IDENTITIES = List.of(
            "js" + "revise",
            "js" + "-revise",
            "js" + " revise",
            "js" + "_revise"
    );
    private static final Set<String> TEXT_EXTENSIONS = Set.of(
            "java", "json", "gradle", "properties", "toml", "yml", "yaml", "xml", "md", "txt"
    );

    @Test
    void projectAndPackagedResourceIdentityIsCurrent() throws IOException {
        Path root = projectRoot();
        assertEquals("jsmore", JSMore.MOD_ID);
        assertEquals("JS More", JSMore.MOD_NAME);
        assertEquals("com.palos.jsmore", JSMore.class.getPackageName());

        String properties = Files.readString(root.resolve("gradle.properties"), StandardCharsets.UTF_8);
        String settings = Files.readString(root.resolve("settings.gradle"), StandardCharsets.UTF_8);
        String metadata = Files.readString(
                root.resolve("src/main/templates/META-INF/neoforge.mods.toml"), StandardCharsets.UTF_8
        );
        String mixins = Files.readString(
                root.resolve("src/main/resources/jsmore.mixins.json"), StandardCharsets.UTF_8
        );
        assertTrue(properties.contains("mod_id=jsmore"));
        assertTrue(properties.contains("mod_name=JS More"));
        assertTrue(properties.contains("mod_group_id=com.palos.jsmore"));
        assertTrue(properties.contains("https://github.com/PalosJ/JS-More"));
        assertTrue(settings.contains("rootProject.name = 'JS More'"));
        assertTrue(metadata.contains("modId=\"${mod_id}\""));
        assertTrue(metadata.contains("config=\"${mod_id}.mixins.json\""));
        assertTrue(mixins.contains("\"package\": \"com.palos.jsmore.mixin\""));
        assertTrue(mixins.contains("\"plugin\": \"com.palos.jsmore.mixin.JSMoreMixinPlugin\""));

        assertTrue(Files.isDirectory(root.resolve("src/main/resources/assets/jsmore")));
        assertTrue(Files.isDirectory(root.resolve("src/main/resources/data/jsmore")));
        assertFalse(Files.exists(root.resolve(Path.of("src/main/resources/assets", "js" + "revise"))));
        assertFalse(Files.exists(root.resolve(Path.of("src/main/resources/data", "js" + "revise"))));
        assertFalse(Files.exists(root.resolve(Path.of("src/main/java/com/palos", "js" + "revise"))));
        assertFalse(Files.exists(root.resolve(Path.of("src/test/java/com/palos", "js" + "revise"))));
    }

    @Test
    void everyDeferredRegistryIdentityUsesCurrentNamespace() throws IllegalAccessException {
        List<String> wrongNamespaces = new ArrayList<>();
        for (Class<?> registry : List.of(
                JSMoreAttachments.class,
                JSMoreBlockEntityTypes.class,
                JSMoreBlocks.class,
                JSMoreCreativeTabs.class,
                JSMoreEntityTypes.class,
                JSMoreItems.class,
                JSMoreRecipeSerializers.class
        )) {
            for (Field field : registry.getDeclaredFields()) {
                if (!Modifier.isStatic(field.getModifiers()) || !DeferredHolder.class.isAssignableFrom(field.getType())) {
                    continue;
                }
                DeferredHolder<?, ?> holder = (DeferredHolder<?, ?>) field.get(null);
                if (!JSMore.MOD_ID.equals(holder.getId().getNamespace())) {
                    wrongNamespaces.add(registry.getSimpleName() + "." + field.getName() + "=" + holder.getId());
                }
            }
        }
        assertEquals(List.of(), wrongNamespaces);
    }

    @Test
    void oldTechnicalIdentityOnlyAppearsInApprovedBreakingNoticeDocuments() throws IOException {
        Path root = projectRoot();
        List<String> violations = new ArrayList<>();

        try (var paths = Files.walk(root)) {
            paths.filter(Files::isRegularFile)
                    .map(Path::normalize)
                    .filter(path -> isAuditedProjectFile(root, path))
                    .forEach(path -> {
                        String relative = root.relativize(path).toString().replace('\\', '/');
                        if (containsOldTechnicalIdentity(relative)) {
                            violations.add(relative + " [path]");
                            return;
                        }
                        try {
                            String text = Files.readString(path, StandardCharsets.UTF_8);
                            if (containsOldTechnicalIdentity(text)) {
                                violations.add(relative + " [content]");
                            }
                        } catch (IOException exception) {
                            throw new IllegalStateException("Could not inspect " + relative, exception);
                        }
                    });
        }

        assertEquals(List.of(), violations);
    }

    @Test
    void oldTechnicalIdentityMatcherIsCaseInsensitiveAndSeparatorComplete() {
        assertTrue(containsOldTechnicalIdentity("constant-pool:" + "JS" + "REVISE"));
        assertTrue(containsOldTechnicalIdentity("path/" + "JS" + "-REVISE"));
        assertTrue(containsOldTechnicalIdentity("display=" + "JS" + " REVISE"));
        assertTrue(containsOldTechnicalIdentity("field_" + "JS" + "_REVISE"));
        assertFalse(containsOldTechnicalIdentity("JS More"));
    }

    private static boolean containsOldTechnicalIdentity(String value) {
        String normalized = value.toLowerCase(Locale.ROOT);
        return OLD_TECHNICAL_IDENTITIES.stream().anyMatch(normalized::contains);
    }

    private static Path projectRoot() {
        Path current = Path.of("").toAbsolutePath().normalize();
        while (current != null && !Files.isRegularFile(current.resolve("gradle.properties"))) {
            current = current.getParent();
        }
        if (current == null) {
            throw new IllegalStateException("Could not locate the Gradle project root from " + Path.of("").toAbsolutePath());
        }
        return current;
    }

    private static boolean isAuditedProjectFile(Path root, Path path) {
        String relative = root.relativize(path).toString().replace('\\', '/');
        if (relative.startsWith("build/")
                || relative.startsWith("run/")
                || relative.startsWith("repo/")
                || relative.startsWith(".git/")
                || relative.startsWith(".gradle/")
                || relative.startsWith(".vscode/")
                || relative.equals("README.md")
                || relative.equals("CHANGELOG.md")) {
            return false;
        }
        String name = path.getFileName().toString();
        int extensionIndex = name.lastIndexOf('.');
        return extensionIndex >= 0 && TEXT_EXTENSIONS.contains(name.substring(extensionIndex + 1).toLowerCase());
    }
}
