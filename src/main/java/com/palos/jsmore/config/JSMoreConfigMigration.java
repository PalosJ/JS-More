package com.palos.jsmore.config;

import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import net.neoforged.fml.event.config.ModConfigEvent;

public final class JSMoreConfigMigration {
    public static final String COMMON_FILE = "jsmore-common.toml";

    private JSMoreConfigMigration() {
    }

    /** Preserve original bytes before NeoForge corrects missing keys or comments. */
    public static Path backupLegacyFile(Path file) {
        if (!Files.isRegularFile(file)) {
            return null;
        }
        try (CommentedFileConfig config = CommentedFileConfig.builder(file).sync().build()) {
            config.load();
            Object schema = config.get("config_schema_version");
            if (schema instanceof Number number && number.longValue() >= 1
                    && number.longValue() <= Integer.MAX_VALUE
                    && number.doubleValue() == number.longValue()) {
                return null;
            }
        } catch (RuntimeException malformedConfig) {
            // Let NeoForge repair malformed configuration only after its raw bytes are preserved.
        }
        for (int suffix = 0; suffix < 1000; suffix++) {
            Path backup = file.resolveSibling(file.getFileName() + ".pre-schema-1"
                    + (suffix == 0 ? "" : "." + suffix) + ".bak");
            try {
                Files.copy(file, backup);
                return backup;
            } catch (FileAlreadyExistsException occupied) {
                // Never overwrite an earlier backup.
            } catch (IOException exception) {
                throw new IllegalStateException("Cannot back up legacy JS More configuration: " + file, exception);
            }
        }
        throw new IllegalStateException("Too many JS More configuration backups beside " + file);
    }

    public static void onLoading(ModConfigEvent.Loading event) {
        if (event.getConfig().getSpec() != JSMoreConfig.SPEC) {
            return;
        }
        if (migrateValues(event.getConfig().getLoadedConfig().config())) {
            JSMoreConfig.DISABLE_JURASSIC_SAGA_BIOME_GENERATION.clearCache();
            JSMoreConfig.CONFIG_SCHEMA_VERSION.clearCache();
            JSMoreConfig.SPEC.save();
        }
    }

    static boolean migrateValues(com.electronwill.nightconfig.core.CommentedConfig config) {
        Object schema = config.get("config_schema_version");
        if (schema instanceof Number number && number.longValue() >= 1) {
            return false;
        }
        // New files start at schema 0 too. This runs before common setup and world generation.
        config.set("disable_jurassicsaga_biome_generation", false);
        config.set("config_schema_version", 1);
        return true;
    }
}
