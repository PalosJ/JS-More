package com.palos.jsrevise.compat.aeronautics;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

final class FormalAeronauticsClassBytes {
    private FormalAeronauticsClassBytes() {
    }

    static byte[] simulatedClass(String classEntry) throws IOException {
        Path bundle = requiredPath(AeronauticsCompatibilityGate.AERONAUTICS_BUNDLE_PROPERTY);
        byte[] nested = zipEntry(bundle, AeronauticsCompatibilityGate.SIMULATED_NESTED_JAR);
        return nestedEntry(nested, classEntry);
    }

    static byte[] aeronauticsClass(String classEntry) throws IOException {
        Path bundle = requiredPath(AeronauticsCompatibilityGate.AERONAUTICS_BUNDLE_PROPERTY);
        byte[] nested = zipEntry(bundle, AeronauticsCompatibilityGate.AERONAUTICS_NESTED_JAR);
        return nestedEntry(nested, classEntry);
    }

    static byte[] sableClass(String classEntry) throws IOException {
        return zipEntry(requiredPath(AeronauticsCompatibilityGate.SABLE_JAR_PROPERTY), classEntry);
    }

    static byte[] createClass(String classEntry) throws IOException {
        return zipEntry(requiredPath(AeronauticsCompatibilityGate.CREATE_JAR_PROPERTY), classEntry);
    }

    private static Path requiredPath(String property) {
        String value = System.getProperty(property);
        if (value == null || value.isBlank()) {
            throw new AssertionError("missing formal archive property " + property);
        }
        return Path.of(value);
    }

    private static byte[] zipEntry(Path archive, String entryName) throws IOException {
        try (ZipFile zip = new ZipFile(archive.toFile())) {
            ZipEntry entry = zip.getEntry(entryName);
            if (entry == null || entry.isDirectory()) {
                throw new IOException("missing " + entryName + " in " + archive);
            }
            return zip.getInputStream(entry).readAllBytes();
        }
    }

    private static byte[] nestedEntry(byte[] archive, String entryName) throws IOException {
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(archive))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (entryName.equals(entry.getName()) && !entry.isDirectory()) {
                    return zip.readAllBytes();
                }
            }
        }
        throw new IOException("missing nested entry " + entryName);
    }
}
