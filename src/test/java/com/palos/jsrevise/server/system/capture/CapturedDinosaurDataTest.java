package com.palos.jsrevise.server.system.capture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Constructor;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

class CapturedDinosaurDataTest {
    @Test
    void deserializationRemovesPassengersAndUnsafeRuntimePlacementData() {
        UUID uuid = UUID.randomUUID();
        CompoundTag entityNbt = new CompoundTag();
        entityNbt.putString("id", "minecraft:pig");
        entityNbt.putUUID("UUID", uuid);
        entityNbt.put("Passengers", new ListTag());
        entityNbt.put("Motion", new ListTag());
        entityNbt.put("Pos", new ListTag());
        entityNbt.put("Rotation", new ListTag());
        entityNbt.putString("leash", "legacy");
        entityNbt.putString("Leash", "legacy");
        entityNbt.putFloat("FallDistance", 12.0F);
        entityNbt.putBoolean("OnGround", true);

        CapturedDinosaurData data = new CapturedDinosaurData(
                ResourceLocation.fromNamespaceAndPath("minecraft", "pig"),
                uuid,
                "Pig",
                10L,
                20L,
                30L,
                2000,
                entityNbt,
                new CompoundTag(),
                CapturedDinosaurVitals.deserializeNBT(new CompoundTag())
        );
        CompoundTag serialized = data.serializeNBT();
        CapturedDinosaurData decoded = CapturedDinosaurData.deserializeNBT(serialized).orElseThrow();
        CompoundTag safe = decoded.sanitizedEntityNbt();

        assertFalse(safe.contains("Passengers"));
        assertFalse(safe.contains("Motion"));
        assertFalse(safe.contains("Pos"));
        assertFalse(safe.contains("Rotation"));
        assertFalse(safe.contains("leash"));
        assertFalse(safe.contains("Leash"));
        assertFalse(safe.contains("FallDistance"));
        assertFalse(safe.contains("OnGround"));
        assertEquals("minecraft:pig", safe.getString("id"));
        assertTrue(safe.hasUUID("UUID"));
        assertEquals(CapturedDinosaurData.MAX_DURABILITY, decoded.durability());
    }

    @Test
    void captureSanitizationRejectsEmptyEntityNbt() {
        assertFalse(CapturedDinosaurData.sanitizeEntityNbtForCapture(
                ResourceLocation.fromNamespaceAndPath("minecraft", "pig"),
                UUID.randomUUID(),
                new CompoundTag()
        ).isPresent());
    }

    @Test
    void captureSanitizationRejectsOversizedEntityNbt() {
        CompoundTag entityNbt = new CompoundTag();
        entityNbt.putString("Oversized", "x".repeat(1024 * 1024 + 256));

        assertFalse(CapturedDinosaurData.sanitizeEntityNbtForCapture(
                ResourceLocation.fromNamespaceAndPath("minecraft", "pig"),
                UUID.randomUUID(),
                entityNbt
        ).isPresent());
    }

    @Test
    void captureSanitizationKeepsLegalEntityNbt() {
        UUID uuid = UUID.randomUUID();
        CompoundTag entityNbt = new CompoundTag();
        entityNbt.putFloat("Health", 12.0F);
        entityNbt.put("Passengers", new ListTag());

        CompoundTag safe = CapturedDinosaurData.sanitizeEntityNbtForCapture(
                ResourceLocation.fromNamespaceAndPath("minecraft", "pig"),
                uuid,
                entityNbt
        ).orElseThrow();

        assertFalse(safe.contains("Passengers"));
        assertEquals("minecraft:pig", safe.getString("id"));
        assertTrue(safe.hasUUID("UUID"));
        assertEquals(uuid, safe.getUUID("UUID"));
        assertEquals(12.0F, safe.getFloat("Health"));
    }

    @Test
    void deserializationTreatsMalformedUuidAsUnreadableWithoutThrowing() {
        CompoundTag stored = storedDataTag(new CompoundTag());
        stored.putIntArray("OriginalUuid", new int[]{1});

        Optional<CapturedDinosaurData> decoded = assertDoesNotThrow(
                () -> CapturedDinosaurData.deserializeNBT(stored)
        );

        assertTrue(decoded.isEmpty());
    }

    @Test
    void deserializationRejectsEntityNbtThatExceedsLimitAfterIdentitySanitization() {
        CompoundTag entityNbt = largestIdentityFreeEntityNbtWithinLimit();
        CompoundTag stored = storedDataTag(entityNbt);

        assertFalse(entityNbt.contains("id"));
        assertFalse(entityNbt.contains("UUID"));
        assertTrue(entityNbt.sizeInBytes() <= 1024 * 1024);
        assertTrue(CapturedDinosaurData.deserializeNBT(stored).isEmpty());
    }

    @Test
    void constructorRejectsInvalidEntityNbtInsteadOfStoringEmptySnapshot() {
        CompoundTag oversized = new CompoundTag();
        oversized.putString("Oversized", "x".repeat(1024 * 1024 + 256));

        assertThrows(IllegalArgumentException.class, () -> new CapturedDinosaurData(
                ResourceLocation.fromNamespaceAndPath("minecraft", "pig"),
                UUID.randomUUID(),
                "Pig",
                10L,
                20L,
                30L,
                CapturedDinosaurData.MAX_DURABILITY,
                oversized,
                new CompoundTag(),
                CapturedDinosaurVitals.deserializeNBT(new CompoundTag())
        ));
    }

    @Test
    void runtimeUpdateKeepsPreviousEntityNbtWhenNewSnapshotIsOversized() {
        UUID uuid = UUID.randomUUID();
        CompoundTag previousNbt = new CompoundTag();
        previousNbt.putFloat("Health", 12.0F);
        CapturedDinosaurData previous = new CapturedDinosaurData(
                ResourceLocation.fromNamespaceAndPath("minecraft", "pig"),
                uuid,
                "Pig",
                10L,
                20L,
                30L,
                CapturedDinosaurData.MAX_DURABILITY,
                previousNbt,
                new CompoundTag(),
                CapturedDinosaurVitals.deserializeNBT(new CompoundTag())
        );
        CompoundTag oversizedRuntimeNbt = new CompoundTag();
        oversizedRuntimeNbt.putString("Oversized", "x".repeat(1024 * 1024 + 256));
        int updatedDurability = CapturedDinosaurData.MAX_DURABILITY * 3 / 4;

        CapturedDinosaurData updated = previous.withRuntimeState(
                40L,
                updatedDurability,
                oversizedRuntimeNbt,
                new CompoundTag(),
                CapturedDinosaurVitals.deserializeNBT(new CompoundTag())
        );

        assertEquals(updatedDurability, updated.durability());
        assertEquals(40L, updated.lastSettledGameTime());
        assertEquals(12.0F, updated.entityNbt().getFloat("Health"));
        assertEquals("minecraft:pig", updated.entityNbt().getString("id"));
        assertEquals(uuid, updated.entityNbt().getUUID("UUID"));
        assertFalse(updated.entityNbt().contains("Oversized"));
    }

    @Test
    void oldTenArgumentConstructorRemainsPublicAndDefaultsRemainderToZero() throws ReflectiveOperationException {
        Constructor<CapturedDinosaurData> constructor = CapturedDinosaurData.class.getConstructor(
                ResourceLocation.class,
                UUID.class,
                String.class,
                long.class,
                long.class,
                long.class,
                int.class,
                CompoundTag.class,
                CompoundTag.class,
                CapturedDinosaurVitals.class
        );
        UUID uuid = UUID.randomUUID();
        CompoundTag entityNbt = new CompoundTag();
        entityNbt.putFloat("Health", 20.0F);

        CapturedDinosaurData data = constructor.newInstance(
                ResourceLocation.fromNamespaceAndPath("minecraft", "pig"),
                uuid,
                "Pig",
                0L,
                0L,
                0L,
                CapturedDinosaurData.MAX_DURABILITY,
                entityNbt,
                new CompoundTag(),
                CapturedDinosaurVitals.deserializeNBT(new CompoundTag())
        );

        assertEquals(0, data.durabilityRemainderTicks());
    }

    @Test
    void durabilityRemainderRoundTripsAndClampsToSingleSecondFraction() {
        UUID uuid = UUID.randomUUID();
        CompoundTag entityNbt = new CompoundTag();
        entityNbt.putFloat("Health", 20.0F);
        CapturedDinosaurData data = new CapturedDinosaurData(
                ResourceLocation.fromNamespaceAndPath("minecraft", "pig"),
                uuid,
                "Pig",
                0L,
                0L,
                0L,
                CapturedDinosaurData.MAX_DURABILITY,
                entityNbt,
                new CompoundTag(),
                CapturedDinosaurVitals.deserializeNBT(new CompoundTag()),
                27
        );

        CapturedDinosaurData decoded = CapturedDinosaurData.deserializeNBT(data.serializeNBT()).orElseThrow();

        assertEquals(19, data.durabilityRemainderTicks());
        assertEquals(19, decoded.durabilityRemainderTicks());
    }

    private static CompoundTag storedDataTag(CompoundTag entityNbt) {
        CompoundTag stored = new CompoundTag();
        stored.putString("EntityType", "minecraft:pig");
        stored.putUUID("OriginalUuid", UUID.randomUUID());
        stored.put("EntityNbt", entityNbt);
        return stored;
    }

    private static CompoundTag largestIdentityFreeEntityNbtWithinLimit() {
        int low = 1;
        int high = 1024 * 1024;
        CompoundTag largest = new CompoundTag();
        while (low <= high) {
            int length = (low + high) >>> 1;
            CompoundTag candidate = new CompoundTag();
            candidate.putByteArray("Padding", new byte[length]);
            if (candidate.sizeInBytes() <= 1024 * 1024) {
                largest = candidate;
                low = length + 1;
            } else {
                high = length - 1;
            }
        }
        return largest;
    }
}
