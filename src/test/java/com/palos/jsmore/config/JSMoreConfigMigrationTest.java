package com.palos.jsmore.config;

import static org.junit.jupiter.api.Assertions.*;

import com.electronwill.nightconfig.core.CommentedConfig;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JSMoreConfigMigrationTest {
    @TempDir Path directory;

    @Test
    void newInstallAndBothLegacyValuesMigrateOnceAndRetainSubsequentOptIn() {
        for (Boolean legacyValue : new Boolean[] {null, false, true}) {
            CommentedConfig config = CommentedConfig.inMemory();
            if (legacyValue != null) config.set("disable_jurassicsaga_biome_generation", legacyValue);
            config.set("debug_logging", true);
            assertTrue(JSMoreConfigMigration.migrateValues(config));
            assertEquals(false, config.get("disable_jurassicsaga_biome_generation"));
            assertEquals(1, (Integer) config.get("config_schema_version"));
            assertEquals(true, config.get("debug_logging"));
            config.set("disable_jurassicsaga_biome_generation", true);
            for (int restart = 0; restart < 3; restart++) {
                assertFalse(JSMoreConfigMigration.migrateValues(config));
                assertEquals(true, config.get("disable_jurassicsaga_biome_generation"));
            }
        }
    }

    @Test
    void backupPreservesExactLegacyBytesAndNeverOverwritesPriorBackups() throws IOException {
        Path file = directory.resolve(JSMoreConfigMigration.COMMON_FILE);
        assertNull(JSMoreConfigMigration.backupLegacyFile(file));
        String old = "# my comment\r\ndisable_jurassicsaga_biome_generation = true\r\ndebug_logging = true\r\n";
        Files.writeString(file, old);
        Path first = JSMoreConfigMigration.backupLegacyFile(file);
        assertEquals(old, Files.readString(first));
        Path second = JSMoreConfigMigration.backupLegacyFile(file);
        assertNotEquals(first, second);
        assertEquals(old, Files.readString(file));
        assertEquals(old, Files.readString(first));
        Files.writeString(file, "config_schema_version = 1\ndisable_jurassicsaga_biome_generation = true\n");
        assertNull(JSMoreConfigMigration.backupLegacyFile(file));
    }

    @Test
    void malformedLegacyFileIsPreservedBeforeNeoForgeRepairsIt() throws IOException {
        Path file = directory.resolve(JSMoreConfigMigration.COMMON_FILE);
        Files.writeString(file, "invalid = [\n");
        assertArrayEquals(Files.readAllBytes(file),
                Files.readAllBytes(JSMoreConfigMigration.backupLegacyFile(file)));
    }

}
