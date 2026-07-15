package com.palos.jsrevise.server.system.capture;

import com.palos.jsrevise.server.system.anesthetic.DinosaurAnestheticSystem;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import jp.jurassicsaga.server.animal.entity.obj.bases.JSAnimalBase;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
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
        CapturedDinosaurVitals vitals,
        int durabilityRemainderTicks
) {
    public static final int MAX_DURABILITY = 20;
    private static final int LEGACY_MAX_DURABILITY = 100;
    private static final int PREVIOUS_MAX_DURABILITY = 500;
    private static final String ENTITY_TYPE = "EntityType";
    private static final String ORIGINAL_UUID = "OriginalUuid";
    private static final String DISPLAY_NAME = "DisplayName";
    private static final String CAPTURED_GAME_TIME = "CapturedGameTime";
    private static final String LAST_SETTLED_GAME_TIME = "LastSettledGameTime";
    private static final String ANESTHETIC_REFERENCE_GAME_TIME = "AnestheticReferenceGameTime";
    private static final String DURABILITY = "Durability";
    private static final String DURABILITY_CAPACITY = "DurabilityCapacity";
    private static final String DURABILITY_REMAINDER_TICKS = "DurabilityRemainderTicks";
    private static final String ENTITY_NBT = "EntityNbt";
    private static final String RELATIVE_ANESTHETIC = "RelativeAnesthetic";
    private static final String VITALS = "Vitals";
    private static final int MAX_DISPLAY_NAME_LENGTH = 256;
    private static final int MAX_ENTITY_NBT_BYTES = 1024 * 1024;
    private static final int MAX_STORED_SUBTAG_BYTES = 64 * 1024;
    private static final int MAX_PENDING_DOSES = 64;
    private static final String ACTIVE_REMAINING_TICKS = "ActiveRemainingTicks";
    private static final String PENDING_DOSES = "PendingDoses";
    private static final String DELAY_TICKS = "DelayTicks";
    private static final String DURATION_TICKS = "DurationTicks";
    private static final Set<String> KNOWN_KEYS = Set.of(
            ENTITY_TYPE,
            ORIGINAL_UUID,
            DISPLAY_NAME,
            CAPTURED_GAME_TIME,
            LAST_SETTLED_GAME_TIME,
            ANESTHETIC_REFERENCE_GAME_TIME,
            DURABILITY,
            DURABILITY_CAPACITY,
            DURABILITY_REMAINDER_TICKS,
            ENTITY_NBT,
            RELATIVE_ANESTHETIC,
            VITALS
    );
    private static final Set<String> KNOWN_RELATIVE_ANESTHETIC_KEYS = Set.of(
            ACTIVE_REMAINING_TICKS,
            PENDING_DOSES
    );
    private static final Set<String> KNOWN_PENDING_DOSE_KEYS = Set.of(DELAY_TICKS, DURATION_TICKS);

    public CapturedDinosaurData(
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
        this(
                entityTypeId,
                originalUuid,
                displayName,
                capturedGameTime,
                lastSettledGameTime,
                anestheticReferenceGameTime,
                durability,
                entityNbt,
                relativeAnestheticNbt,
                vitals,
                0
        );
    }

    public CapturedDinosaurData {
        displayName = sanitizeDisplayName(displayName);
        capturedGameTime = Math.max(0L, capturedGameTime);
        lastSettledGameTime = Math.max(capturedGameTime, lastSettledGameTime);
        anestheticReferenceGameTime = Math.max(0L, anestheticReferenceGameTime);
        durability = sanitizeDurability(durability);
        entityNbt = sanitizeEntityNbtForStoredData(entityTypeId, originalUuid, entityNbt);
        relativeAnestheticNbt = relativeAnestheticNbt == null ? new CompoundTag() : relativeAnestheticNbt.copy();
        vitals = vitals == null ? CapturedDinosaurVitals.deserializeNBT(new CompoundTag()) : vitals;
        durabilityRemainderTicks = Math.max(0, Math.min(19, durabilityRemainderTicks));
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
                CapturedDinosaurVitals.capture(animal),
                0
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
        return withRuntimeState(
                currentGameTime,
                durability,
                this.durabilityRemainderTicks,
                entityNbt,
                relativeAnestheticNbt,
                vitals
        );
    }

    public CapturedDinosaurData withRuntimeState(
            long currentGameTime,
            int durability,
            int durabilityRemainderTicks,
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
                vitals,
                durabilityRemainderTicks
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
                this.vitals,
                this.durabilityRemainderTicks
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
        tag.putInt(DURABILITY_CAPACITY, MAX_DURABILITY);
        tag.putInt(DURABILITY_REMAINDER_TICKS, this.durabilityRemainderTicks);
        tag.put(ENTITY_NBT, this.entityNbt.copy());
        tag.put(RELATIVE_ANESTHETIC, this.relativeAnestheticNbt.copy());
        tag.put(VITALS, this.vitals.serializeNBT());
        return tag;
    }

    public static Optional<CapturedDinosaurData> deserializeNBT(CompoundTag tag) {
        return decodeNBT(tag).map(DecodeResult::data);
    }

    public static Optional<DecodeResult> decodeNBT(CompoundTag tag) {
        try {
            return decodeNBTSafely(tag);
        } catch (RuntimeException exception) {
            return Optional.empty();
        }
    }

    private static Optional<DecodeResult> decodeNBTSafely(CompoundTag tag) {
        if (tag == null
                || tag.getAllKeys().stream().anyMatch(key -> !KNOWN_KEYS.contains(key))
                || !tag.contains(ENTITY_TYPE, Tag.TAG_STRING)
                || !tag.contains(ORIGINAL_UUID, Tag.TAG_INT_ARRAY)
                || !tag.hasUUID(ORIGINAL_UUID)
                || !tag.contains(ENTITY_NBT, Tag.TAG_COMPOUND)
                || !hasExpectedOptionalTypes(tag)) {
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
        if (entityNbt.isEmpty() || estimatedNbtSize(entityNbt) > MAX_ENTITY_NBT_BYTES) {
            return Optional.empty();
        }
        CompoundTag anestheticTag = tag.contains(RELATIVE_ANESTHETIC, Tag.TAG_COMPOUND)
                ? tag.getCompound(RELATIVE_ANESTHETIC).copy()
                : new CompoundTag();
        if (!isValidRelativeAnestheticTag(anestheticTag)) {
            return Optional.empty();
        }
        CompoundTag vitalsTag = tag.contains(VITALS, Tag.TAG_COMPOUND)
                ? tag.getCompound(VITALS)
                : new CompoundTag();
        if (!CapturedDinosaurVitals.isValidStoredTag(vitalsTag)) {
            return Optional.empty();
        }
        long capturedGameTime = Math.max(0L, tag.getLong(CAPTURED_GAME_TIME));
        long lastSettledGameTime = tag.contains(LAST_SETTLED_GAME_TIME, Tag.TAG_LONG)
                ? tag.getLong(LAST_SETTLED_GAME_TIME)
                : capturedGameTime;
        long anestheticReferenceGameTime = tag.contains(ANESTHETIC_REFERENCE_GAME_TIME, Tag.TAG_LONG)
                ? tag.getLong(ANESTHETIC_REFERENCE_GAME_TIME)
                : capturedGameTime;
        boolean legacyCapacity = !tag.contains(DURABILITY_CAPACITY);
        if (!legacyCapacity && !tag.contains(DURABILITY_CAPACITY, Tag.TAG_INT)) {
            return Optional.empty();
        }
        int storedCapacity = legacyCapacity ? LEGACY_MAX_DURABILITY : tag.getInt(DURABILITY_CAPACITY);
        if (storedCapacity != LEGACY_MAX_DURABILITY
                && storedCapacity != PREVIOUS_MAX_DURABILITY
                && storedCapacity != MAX_DURABILITY) {
            return Optional.empty();
        }
        if (tag.contains(DURABILITY) && !tag.contains(DURABILITY, Tag.TAG_INT)) {
            return Optional.empty();
        }
        int storedDurability = tag.contains(DURABILITY, Tag.TAG_INT)
                ? Math.max(0, Math.min(storedCapacity, tag.getInt(DURABILITY)))
                : storedCapacity;
        boolean needsRewrite = storedCapacity != MAX_DURABILITY;
        int durability = needsRewrite
                ? migrateDurability(storedDurability, storedCapacity)
                : storedDurability;
        int durabilityRemainderTicks = tag.contains(DURABILITY_REMAINDER_TICKS, Tag.TAG_INT)
                ? tag.getInt(DURABILITY_REMAINDER_TICKS)
                : 0;
        return Optional.of(new DecodeResult(new CapturedDinosaurData(
                entityTypeId,
                uuid,
                tag.contains(DISPLAY_NAME, Tag.TAG_STRING) ? tag.getString(DISPLAY_NAME) : "",
                capturedGameTime,
                lastSettledGameTime,
                anestheticReferenceGameTime,
                durability,
                entityNbt,
                anestheticTag,
                CapturedDinosaurVitals.deserializeNBT(vitalsTag),
                durabilityRemainderTicks
        ), needsRewrite));
    }

    private static int migrateDurability(int storedDurability, int storedCapacity) {
        if (storedDurability <= 0) {
            return 0;
        }
        long bounded = Math.min(storedCapacity, storedDurability);
        if (storedCapacity == LEGACY_MAX_DURABILITY) {
            // 1.0.124 migrated 100-capacity boxes to 500 while preserving damage already taken.
            // Project through that established state so direct upgrades and sequential upgrades agree.
            bounded = PREVIOUS_MAX_DURABILITY - (LEGACY_MAX_DURABILITY - bounded);
            storedCapacity = PREVIOUS_MAX_DURABILITY;
        }
        return (int) Math.min(
                MAX_DURABILITY,
                (bounded * MAX_DURABILITY + storedCapacity - 1L) / storedCapacity
        );
    }

    private static int sanitizeDurability(int durability) {
        return Math.max(0, Math.min(MAX_DURABILITY, durability));
    }

    private static boolean hasExpectedOptionalTypes(CompoundTag tag) {
        return hasOptionalType(tag, DISPLAY_NAME, Tag.TAG_STRING)
                && hasOptionalType(tag, CAPTURED_GAME_TIME, Tag.TAG_LONG)
                && hasOptionalType(tag, LAST_SETTLED_GAME_TIME, Tag.TAG_LONG)
                && hasOptionalType(tag, ANESTHETIC_REFERENCE_GAME_TIME, Tag.TAG_LONG)
                && hasOptionalType(tag, DURABILITY, Tag.TAG_INT)
                && hasOptionalType(tag, DURABILITY_CAPACITY, Tag.TAG_INT)
                && hasOptionalType(tag, DURABILITY_REMAINDER_TICKS, Tag.TAG_INT)
                && hasOptionalType(tag, RELATIVE_ANESTHETIC, Tag.TAG_COMPOUND)
                && hasOptionalType(tag, VITALS, Tag.TAG_COMPOUND);
    }

    private static boolean isValidRelativeAnestheticTag(CompoundTag tag) {
        if (tag == null
                || estimatedNbtSize(tag) > MAX_STORED_SUBTAG_BYTES
                || tag.getAllKeys().stream().anyMatch(key -> !KNOWN_RELATIVE_ANESTHETIC_KEYS.contains(key))
                || !hasOptionalType(tag, ACTIVE_REMAINING_TICKS, Tag.TAG_LONG)
                || !hasOptionalType(tag, PENDING_DOSES, Tag.TAG_LIST)) {
            return false;
        }
        if (!tag.contains(PENDING_DOSES)) {
            return true;
        }
        Tag pendingTag = tag.get(PENDING_DOSES);
        if (!(pendingTag instanceof ListTag pending)
                || pending.size() > MAX_PENDING_DOSES
                || (!pending.isEmpty() && pending.getElementType() != Tag.TAG_COMPOUND)) {
            return false;
        }
        for (int index = 0; index < pending.size(); index++) {
            Tag entry = pending.get(index);
            if (!(entry instanceof CompoundTag dose)
                    || dose.getAllKeys().stream().anyMatch(key -> !KNOWN_PENDING_DOSE_KEYS.contains(key))
                    || !dose.contains(DELAY_TICKS, Tag.TAG_LONG)
                    || !dose.contains(DURATION_TICKS, Tag.TAG_INT)) {
                return false;
            }
        }
        return true;
    }

    private static boolean hasOptionalType(CompoundTag tag, String key, int type) {
        return !tag.contains(key) || tag.contains(key, type);
    }

    public CompoundTag sanitizedEntityNbt() {
        return sanitizeEntityNbt(this.entityTypeId, this.originalUuid, this.entityNbt);
    }

    static Optional<CompoundTag> sanitizeEntityNbtForCapture(ResourceLocation entityTypeId, UUID uuid, CompoundTag source) {
        if (source == null || source.isEmpty() || estimatedNbtSize(source) > MAX_ENTITY_NBT_BYTES) {
            return Optional.empty();
        }
        CompoundTag safe = sanitizeEntityNbt(entityTypeId, uuid, source);
        return safe.isEmpty() || estimatedNbtSize(safe) > MAX_ENTITY_NBT_BYTES
                ? Optional.empty()
                : Optional.of(safe);
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
        safe.remove("leash");
        safe.remove("Leash");
        safe.remove("RootVehicle");
        safe.remove("Motion");
        safe.remove("Pos");
        safe.remove("Rotation");
        safe.remove("FallFlying");
        safe.remove("FallDistance");
        safe.remove("OnGround");
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

    public record DecodeResult(CapturedDinosaurData data, boolean needsRewrite) {
    }
}
