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

    @Test
    void legacyDurabilityMigrationProjectsThroughEstablishedFiveHundredCapacityState() {
        CapturedDinosaurData.DecodeResult decoded = decodeLegacyDurability(75);
        CompoundTag explicitLegacy = storedDataTag(new CompoundTag());
        explicitLegacy.getCompound("EntityNbt").putFloat("Health", 20.0F);
        explicitLegacy.putInt("Durability", 75);
        explicitLegacy.putInt("DurabilityCapacity", 100);
        CapturedDinosaurData.DecodeResult explicit = CapturedDinosaurData.decodeNBT(explicitLegacy).orElseThrow();

        assertEquals(20, decodeLegacyDurability(100).data().durability());
        assertEquals(19, decoded.data().durability());
        assertEquals(19, explicit.data().durability());
        assertEquals(17, decodeLegacyDurability(1).data().durability());
        assertEquals(0, decodeLegacyDurability(0).data().durability());
        assertTrue(decoded.needsRewrite());
        assertTrue(explicit.needsRewrite());
        assertEquals(20, decoded.data().serializeNBT().getInt("DurabilityCapacity"));
    }

    @Test
    void explicitCurrentCapacityDoesNotMigratePreviousCapacityDoesAndUnknownIsUnreadable() {
        CompoundTag current = storedDataTag(new CompoundTag());
        current.getCompound("EntityNbt").putFloat("Health", 20.0F);
        current.putInt("Durability", 15);
        current.putInt("DurabilityCapacity", 20);
        CapturedDinosaurData.DecodeResult decoded = CapturedDinosaurData.decodeNBT(current).orElseThrow();
        assertEquals(15, decoded.data().durability());
        assertFalse(decoded.needsRewrite());

        current.putInt("Durability", 375);
        current.putInt("DurabilityCapacity", 500);
        CapturedDinosaurData.DecodeResult previous = CapturedDinosaurData.decodeNBT(current).orElseThrow();
        assertEquals(15, previous.data().durability());
        assertTrue(previous.needsRewrite());

        current.putInt("Durability", 1);
        CapturedDinosaurData.DecodeResult nearlyBrokenPrevious = CapturedDinosaurData.decodeNBT(current).orElseThrow();
        assertEquals(1, nearlyBrokenPrevious.data().durability());

        current.putInt("DurabilityCapacity", 250);
        assertTrue(CapturedDinosaurData.decodeNBT(current).isEmpty());
    }

    @Test
    void strictCaptureStructureRejectsUnknownAndWronglyTypedKnownFields() {
        CompoundTag unknownTopLevel = validStoredDataTag();
        unknownTopLevel.putInt("FutureField", 1);
        assertTrue(CapturedDinosaurData.decodeNBT(unknownTopLevel).isEmpty());

        CompoundTag wrongTimeType = validStoredDataTag();
        wrongTimeType.putString("CapturedGameTime", "bad");
        assertTrue(CapturedDinosaurData.decodeNBT(wrongTimeType).isEmpty());

        CompoundTag unknownRelative = validStoredDataTag();
        CompoundTag relative = new CompoundTag();
        relative.putLong("ActiveRemainingTicks", 20L);
        relative.putInt("FutureField", 1);
        unknownRelative.put("RelativeAnesthetic", relative);
        assertTrue(CapturedDinosaurData.decodeNBT(unknownRelative).isEmpty());

        CompoundTag wrongVitals = validStoredDataTag();
        CompoundTag vitals = new CompoundTag();
        vitals.putString("HungerPoints", "bad");
        wrongVitals.put("Vitals", vitals);
        assertTrue(CapturedDinosaurData.decodeNBT(wrongVitals).isEmpty());
    }

    @Test
    void relativeAnestheticAcceptsSixtyFourPendingEntriesAndRejectsSixtyFive() {
        CompoundTag maximum = validStoredDataTag();
        maximum.put("RelativeAnesthetic", relativeWithPendingCount(64));
        assertTrue(CapturedDinosaurData.decodeNBT(maximum).isPresent());

        CompoundTag oversized = validStoredDataTag();
        oversized.put("RelativeAnesthetic", relativeWithPendingCount(65));
        assertTrue(CapturedDinosaurData.decodeNBT(oversized).isEmpty());
    }

    private static CompoundTag storedDataTag(CompoundTag entityNbt) {
        CompoundTag stored = new CompoundTag();
        stored.putString("EntityType", "minecraft:pig");
        stored.putUUID("OriginalUuid", UUID.randomUUID());
        stored.put("EntityNbt", entityNbt);
        return stored;
    }

    private static CompoundTag validStoredDataTag() {
        CompoundTag tag = storedDataTag(new CompoundTag());
        tag.getCompound("EntityNbt").putFloat("Health", 20.0F);
        return tag;
    }

    private static CompoundTag relativeWithPendingCount(int count) {
        CompoundTag relative = new CompoundTag();
        relative.putLong("ActiveRemainingTicks", 0L);
        ListTag pending = new ListTag();
        for (int index = 0; index < count; index++) {
            CompoundTag dose = new CompoundTag();
            dose.putLong("DelayTicks", index + 1L);
            dose.putInt("DurationTicks", 200);
            pending.add(dose);
        }
        relative.put("PendingDoses", pending);
        return relative;
    }

    private static CapturedDinosaurData.DecodeResult decodeLegacyDurability(int durability) {
        CompoundTag legacy = storedDataTag(new CompoundTag());
        legacy.getCompound("EntityNbt").putFloat("Health", 20.0F);
        legacy.putInt("Durability", durability);
        return CapturedDinosaurData.decodeNBT(legacy).orElseThrow();
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
