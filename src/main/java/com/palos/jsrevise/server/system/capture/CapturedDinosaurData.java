package com.palos.jsrevise.server.system.capture;

import com.palos.jsrevise.server.system.anesthetic.DinosaurAnestheticSystem;
import java.util.Optional;
import java.util.UUID;
import jp.jurassicsaga.server.animal.entity.obj.bases.JSAnimalBase;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

public record CapturedDinosaurData(
        ResourceLocation entityTypeId,
        UUID originalUuid,
        String displayName,
        long capturedGameTime,
        long lastSettledGameTime,
        long anestheticReferenceGameTime,
        int durability,
        CompoundTag entityNbt,
        CompoundTag relativeAnestheticNbt,
        CapturedDinosaurVitals vitals
) {
    public static final int MAX_DURABILITY = 1000;
    private static final String ENTITY_TYPE = "EntityType";
    private static final String ORIGINAL_UUID = "OriginalUuid";
    private static final String DISPLAY_NAME = "DisplayName";
    private static final String CAPTURED_GAME_TIME = "CapturedGameTime";
    private static final String LAST_SETTLED_GAME_TIME = "LastSettledGameTime";
    private static final String ANESTHETIC_REFERENCE_GAME_TIME = "AnestheticReferenceGameTime";
    private static final String DURABILITY = "Durability";
    private static final String ENTITY_NBT = "EntityNbt";
    private static final String RELATIVE_ANESTHETIC = "RelativeAnesthetic";
    private static final String VITALS = "Vitals";
    private static final int MAX_DISPLAY_NAME_LENGTH = 256;
    private static final int MAX_ENTITY_NBT_BYTES = 1024 * 1024;

    public CapturedDinosaurData {
        displayName = sanitizeDisplayName(displayName);
        capturedGameTime = Math.max(0L, capturedGameTime);
        lastSettledGameTime = Math.max(capturedGameTime, lastSettledGameTime);
        anestheticReferenceGameTime = Math.max(0L, anestheticReferenceGameTime);
        durability = sanitizeDurability(durability);
        entityNbt = sanitizeEntityNbtForStoredData(entityTypeId, originalUuid, entityNbt);
        relativeAnestheticNbt = relativeAnestheticNbt == null ? new CompoundTag() : relativeAnestheticNbt.copy();
        vitals = vitals == null ? CapturedDinosaurVitals.deserializeNBT(new CompoundTag()) : vitals;
    }

    public static Optional<CapturedDinosaurData> capture(JSAnimalBase animal) {
        if (animal == null || animal.level().isClientSide || !animal.isAlive() || animal.isRemoved()) {
            return Optional.empty();
        }
        ResourceLocation entityTypeId = BuiltInRegistries.ENTITY_TYPE.getKey(animal.getType());
        if (entityTypeId == null) {
            return Optional.empty();
        }
        UUID uuid = animal.getUUID();
        long gameTime = animal.level().getGameTime();
        Optional<CompoundTag> entityNbt = sanitizeEntityNbtForCapture(
                entityTypeId,
                uuid,
                animal.saveWithoutId(new CompoundTag())
        );
        if (entityNbt.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new CapturedDinosaurData(
                entityTypeId,
                uuid,
                animal.getDisplayName().getString(),
                gameTime,
                gameTime,
                gameTime,
                MAX_DURABILITY,
                entityNbt.get(),
                DinosaurAnestheticSystem.saveRelativeAnestheticState(animal),
                CapturedDinosaurVitals.capture(animal)
        ));
    }

    public long capturedDurationTicks(long currentGameTime) {
        return Math.max(0L, currentGameTime - this.capturedGameTime);
    }

    public long remainingAnestheticTicks(long currentGameTime) {
        if (this.relativeAnestheticNbt.isEmpty()) {
            return 0L;
        }
        long storedRemaining = Math.max(0L, this.relativeAnestheticNbt.getLong("ActiveRemainingTicks"));
        long elapsed = Math.max(0L, currentGameTime - this.anestheticReferenceGameTime);
        return Math.max(0L, storedRemaining - elapsed);
    }

    public CapturedDinosaurData withRuntimeState(
            long currentGameTime,
            int durability,
            CompoundTag entityNbt,
            CompoundTag relativeAnestheticNbt,
            CapturedDinosaurVitals vitals
    ) {
        CompoundTag runtimeEntityNbt = sanitizeEntityNbtForRuntimeUpdate(entityTypeId, originalUuid, entityNbt)
                .orElse(this.entityNbt.copy());
        return new CapturedDinosaurData(
                this.entityTypeId,
                this.originalUuid,
                this.displayName,
                this.capturedGameTime,
                currentGameTime,
                currentGameTime,
                durability,
                runtimeEntityNbt,
                relativeAnestheticNbt,
                vitals
        );
    }

    public CapturedDinosaurData withDurabilityAndSettlement(long currentGameTime, int durability) {
        return new CapturedDinosaurData(
                this.entityTypeId,
                this.originalUuid,
                this.displayName,
                this.capturedGameTime,
                currentGameTime,
                this.anestheticReferenceGameTime,
                durability,
                this.entityNbt,
                this.relativeAnestheticNbt,
                this.vitals
        );
    }

    public CompoundTag serializeNBT() {
        CompoundTag tag = new CompoundTag();
        tag.putString(ENTITY_TYPE, this.entityTypeId.toString());
        tag.putUUID(ORIGINAL_UUID, this.originalUuid);
        tag.putString(DISPLAY_NAME, this.displayName);
        tag.putLong(CAPTURED_GAME_TIME, this.capturedGameTime);
        tag.putLong(LAST_SETTLED_GAME_TIME, this.lastSettledGameTime);
        tag.putLong(ANESTHETIC_REFERENCE_GAME_TIME, this.anestheticReferenceGameTime);
        tag.putInt(DURABILITY, this.durability);
        tag.put(ENTITY_NBT, this.entityNbt.copy());
        tag.put(RELATIVE_ANESTHETIC, this.relativeAnestheticNbt.copy());
        tag.put(VITALS, this.vitals.serializeNBT());
        return tag;
    }

    public static Optional<CapturedDinosaurData> deserializeNBT(CompoundTag tag) {
        if (tag == null
                || !tag.contains(ENTITY_TYPE, Tag.TAG_STRING)
                || !tag.hasUUID(ORIGINAL_UUID)
                || !tag.contains(ENTITY_NBT, Tag.TAG_COMPOUND)) {
            return Optional.empty();
        }
        ResourceLocation entityTypeId = ResourceLocation.tryParse(tag.getString(ENTITY_TYPE));
        if (entityTypeId == null || !BuiltInRegistries.ENTITY_TYPE.containsKey(entityTypeId)) {
            return Optional.empty();
        }
        CompoundTag entityNbt = tag.getCompound(ENTITY_NBT).copy();
        if (entityNbt.isEmpty() || estimatedNbtSize(entityNbt) > MAX_ENTITY_NBT_BYTES) {
            return Optional.empty();
        }
        UUID uuid = tag.getUUID(ORIGINAL_UUID);
        entityNbt = sanitizeEntityNbt(entityTypeId, uuid, entityNbt);
        CompoundTag anestheticTag = tag.contains(RELATIVE_ANESTHETIC, Tag.TAG_COMPOUND)
                ? tag.getCompound(RELATIVE_ANESTHETIC).copy()
                : new CompoundTag();
        CompoundTag vitalsTag = tag.contains(VITALS, Tag.TAG_COMPOUND)
                ? tag.getCompound(VITALS)
                : new CompoundTag();
        long capturedGameTime = Math.max(0L, tag.getLong(CAPTURED_GAME_TIME));
        long lastSettledGameTime = tag.contains(LAST_SETTLED_GAME_TIME, Tag.TAG_LONG)
                ? tag.getLong(LAST_SETTLED_GAME_TIME)
                : capturedGameTime;
        long anestheticReferenceGameTime = tag.contains(ANESTHETIC_REFERENCE_GAME_TIME, Tag.TAG_LONG)
                ? tag.getLong(ANESTHETIC_REFERENCE_GAME_TIME)
                : capturedGameTime;
        int durability = tag.contains(DURABILITY, Tag.TAG_INT)
                ? tag.getInt(DURABILITY)
                : MAX_DURABILITY;
        return Optional.of(new CapturedDinosaurData(
                entityTypeId,
                uuid,
                tag.contains(DISPLAY_NAME, Tag.TAG_STRING) ? tag.getString(DISPLAY_NAME) : "",
                capturedGameTime,
                lastSettledGameTime,
                anestheticReferenceGameTime,
                durability,
                entityNbt,
                anestheticTag,
                CapturedDinosaurVitals.deserializeNBT(vitalsTag)
        ));
    }

    private static int sanitizeDurability(int durability) {
        return Math.max(0, Math.min(MAX_DURABILITY, durability));
    }

    public CompoundTag sanitizedEntityNbt() {
        return sanitizeEntityNbt(this.entityTypeId, this.originalUuid, this.entityNbt);
    }

    static Optional<CompoundTag> sanitizeEntityNbtForCapture(ResourceLocation entityTypeId, UUID uuid, CompoundTag source) {
        if (source == null || source.isEmpty() || estimatedNbtSize(source) > MAX_ENTITY_NBT_BYTES) {
            return Optional.empty();
        }
        CompoundTag safe = sanitizeEntityNbt(entityTypeId, uuid, source);
        return safe.isEmpty() ? Optional.empty() : Optional.of(safe);
    }

    private static Optional<CompoundTag> sanitizeEntityNbtForRuntimeUpdate(
            ResourceLocation entityTypeId,
            UUID uuid,
            CompoundTag source
    ) {
        return sanitizeEntityNbtForCapture(entityTypeId, uuid, source);
    }

    private static CompoundTag sanitizeEntityNbtForStoredData(ResourceLocation entityTypeId, UUID uuid, CompoundTag source) {
        Optional<CompoundTag> safe = sanitizeEntityNbtForCapture(entityTypeId, uuid, source);
        if (safe.isEmpty()) {
            throw new IllegalArgumentException("Captured dinosaur entity NBT must be non-empty and within size limits");
        }
        return safe.get();
    }

    private static CompoundTag sanitizeEntityNbt(ResourceLocation entityTypeId, UUID uuid, CompoundTag source) {
        CompoundTag safe = source == null ? new CompoundTag() : source.copy();
        safe.remove("Passengers");
        safe.remove("Leash");
        safe.remove("RootVehicle");
        safe.remove("Motion");
        safe.remove("Pos");
        safe.remove("Rotation");
        safe.remove("FallFlying");
        if (entityTypeId != null) {
            safe.putString("id", entityTypeId.toString());
        }
        if (uuid != null) {
            safe.putUUID("UUID", uuid);
        }
        return safe;
    }

    private static int estimatedNbtSize(CompoundTag tag) {
        try {
            return tag.sizeInBytes();
        } catch (RuntimeException exception) {
            return Integer.MAX_VALUE;
        }
    }

    private static String sanitizeDisplayName(String displayName) {
        if (displayName == null || displayName.isBlank()) {
            return "";
        }
        return displayName.length() > MAX_DISPLAY_NAME_LENGTH
                ? displayName.substring(0, MAX_DISPLAY_NAME_LENGTH)
                : displayName;
    }
}
