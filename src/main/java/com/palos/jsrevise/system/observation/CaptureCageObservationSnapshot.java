package com.palos.jsrevise.system.observation;

import com.palos.jsrevise.server.system.age.DinosaurAgeEstimate;
import com.palos.jsrevise.server.system.capture.CapturedDinosaurData;
import com.palos.jsrevise.server.system.size.DinosaurLifecycleStage;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalLong;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

public record CaptureCageObservationSnapshot(
        ResourceLocation entityTypeId,
        DinosaurObservationSnapshot observation,
        long capturedDurationTicks,
        int cageDurability
) {
    private static final String ENTITY_TYPE = "EntityType";
    private static final String DISPLAY_NAME = "DisplayName";
    private static final String AGE = "Age";
    private static final String SPECIES_ID = "SpeciesId";
    private static final String LIFECYCLE_STAGE = "LifecycleStage";
    private static final String GROWTH_PERCENTAGE = "GrowthPercentage";
    private static final String CURRENT_GAME_AGE_TICKS = "CurrentGameAgeTicks";
    private static final String ADULT_GAME_AGE_TICKS = "AdultGameAgeTicks";
    private static final String CURRENT_REAL_AGE_YEARS = "CurrentRealAgeYears";
    private static final String ADULT_REAL_AGE_YEARS = "AdultRealAgeYears";
    private static final String CURRENT_HEALTH = "CurrentHealth";
    private static final String MAX_HEALTH = "MaxHealth";
    private static final String MALE = "Male";
    private static final String HUNGER_PERCENT = "HungerPercent";
    private static final String THIRST_PERCENT = "ThirstPercent";
    private static final String MOOD_PERCENT = "MoodPercent";
    private static final String PENDING_ANESTHETIC_TICKS = "PendingAnestheticTicks";
    private static final String REMAINING_ANESTHETIC_TICKS = "RemainingAnestheticTicks";
    private static final String QUEUED_ANESTHETIC_TICKS = "QueuedAnestheticTicks";
    private static final String CAPTURED_DURATION_TICKS = "CapturedDurationTicks";
    private static final String CAGE_DURABILITY = "CageDurability";
    private static final String GENES = "Genes";
    private static final String GENE_ID = "Id";
    private static final String GENE_ITEM_ID = "ItemId";
    private static final String GENE_DISPLAY_NAME = "DisplayName";

    public CaptureCageObservationSnapshot {
        capturedDurationTicks = Math.max(0L, capturedDurationTicks);
        cageDurability = Math.max(0, Math.min(CapturedDinosaurData.MAX_DURABILITY, cageDurability));
        if (entityTypeId == null) {
            entityTypeId = ResourceLocation.fromNamespaceAndPath("jsrevise", "unknown");
        }
    }

    public static CaptureCageObservationSnapshot from(
            ResourceLocation entityTypeId,
            DinosaurObservationSnapshot observation,
            long capturedDurationTicks
    ) {
        return from(entityTypeId, observation, capturedDurationTicks, CapturedDinosaurData.MAX_DURABILITY);
    }

    public static CaptureCageObservationSnapshot from(
            ResourceLocation entityTypeId,
            DinosaurObservationSnapshot observation,
            long capturedDurationTicks,
            int cageDurability
    ) {
        return new CaptureCageObservationSnapshot(entityTypeId, observation, capturedDurationTicks, cageDurability);
    }

    public CompoundTag serializeNBT() {
        CompoundTag tag = new CompoundTag();
        tag.putString(ENTITY_TYPE, this.entityTypeId.toString());
        tag.putString(DISPLAY_NAME, this.observation.displayName().getString());
        tag.put(AGE, serializeAge(this.observation.ageEstimate()));
        putOptionalDouble(tag, CURRENT_HEALTH, this.observation.currentHealth());
        putOptionalDouble(tag, MAX_HEALTH, this.observation.maxHealth());
        this.observation.male().ifPresent(value -> tag.putBoolean(MALE, value));
        putOptionalDouble(tag, HUNGER_PERCENT, this.observation.hungerPercent());
        putOptionalDouble(tag, THIRST_PERCENT, this.observation.thirstPercent());
        putOptionalDouble(tag, MOOD_PERCENT, this.observation.moodPercent());
        putOptionalLong(tag, PENDING_ANESTHETIC_TICKS, this.observation.pendingAnestheticTicks());
        putOptionalLong(tag, REMAINING_ANESTHETIC_TICKS, this.observation.remainingAnestheticTicks());
        putOptionalLong(tag, QUEUED_ANESTHETIC_TICKS, this.observation.queuedAnestheticTicks());
        tag.putLong(CAPTURED_DURATION_TICKS, this.capturedDurationTicks);
        tag.putInt(CAGE_DURABILITY, this.cageDurability);
        ListTag genes = new ListTag();
        for (ObservedGene gene : this.observation.genes()) {
            CompoundTag geneTag = new CompoundTag();
            geneTag.putString(GENE_ID, gene.id());
            geneTag.putString(GENE_ITEM_ID, gene.itemId().toString());
            geneTag.putString(GENE_DISPLAY_NAME, gene.displayName().getString());
            genes.add(geneTag);
        }
        tag.put(GENES, genes);
        return tag;
    }

    public static Optional<CaptureCageObservationSnapshot> deserializeNBT(CompoundTag tag) {
        if (tag == null || !tag.contains(ENTITY_TYPE, Tag.TAG_STRING) || !tag.contains(AGE, Tag.TAG_COMPOUND)) {
            return Optional.empty();
        }
        ResourceLocation entityTypeId = ResourceLocation.tryParse(tag.getString(ENTITY_TYPE));
        DinosaurAgeEstimate ageEstimate = deserializeAge(tag.getCompound(AGE)).orElse(null);
        if (entityTypeId == null || ageEstimate == null) {
            return Optional.empty();
        }
        DinosaurObservationSnapshot observation = new DinosaurObservationSnapshot(
                Component.literal(tag.contains(DISPLAY_NAME, Tag.TAG_STRING) ? tag.getString(DISPLAY_NAME) : ""),
                ageEstimate,
                readOptionalDouble(tag, CURRENT_HEALTH),
                readOptionalDouble(tag, MAX_HEALTH),
                tag.contains(MALE, Tag.TAG_BYTE) ? Optional.of(tag.getBoolean(MALE)) : Optional.empty(),
                readOptionalDouble(tag, HUNGER_PERCENT),
                readOptionalDouble(tag, THIRST_PERCENT),
                readOptionalDouble(tag, MOOD_PERCENT),
                readOptionalLong(tag, PENDING_ANESTHETIC_TICKS),
                readOptionalLong(tag, REMAINING_ANESTHETIC_TICKS),
                readOptionalLong(tag, QUEUED_ANESTHETIC_TICKS),
                Optional.empty(),
                readGenes(tag.getList(GENES, Tag.TAG_COMPOUND))
        );
        return Optional.of(new CaptureCageObservationSnapshot(
                entityTypeId,
                observation,
                Math.max(0L, tag.getLong(CAPTURED_DURATION_TICKS)),
                tag.contains(CAGE_DURABILITY, Tag.TAG_INT)
                        ? tag.getInt(CAGE_DURABILITY)
                        : CapturedDinosaurData.MAX_DURABILITY
        ));
    }

    private static CompoundTag serializeAge(DinosaurAgeEstimate age) {
        CompoundTag tag = new CompoundTag();
        tag.putString(SPECIES_ID, age.speciesId().toString());
        tag.putString(LIFECYCLE_STAGE, age.lifecycleStage().name());
        tag.putDouble(GROWTH_PERCENTAGE, sanitizePercent(age.growthPercentage()));
        putOptionalLong(tag, CURRENT_GAME_AGE_TICKS, age.estimatedCurrentGameAgeTicks());
        putOptionalLong(tag, ADULT_GAME_AGE_TICKS, age.estimatedAdultGameAgeTicks());
        putOptionalDouble(tag, CURRENT_REAL_AGE_YEARS, age.estimatedCurrentRealAgeYears());
        putOptionalDouble(tag, ADULT_REAL_AGE_YEARS, age.estimatedAdultRealAgeYears());
        return tag;
    }

    private static Optional<DinosaurAgeEstimate> deserializeAge(CompoundTag tag) {
        if (tag == null || !tag.contains(SPECIES_ID, Tag.TAG_STRING) || !tag.contains(LIFECYCLE_STAGE, Tag.TAG_STRING)) {
            return Optional.empty();
        }
        ResourceLocation speciesId = ResourceLocation.tryParse(tag.getString(SPECIES_ID));
        DinosaurLifecycleStage stage;
        try {
            stage = DinosaurLifecycleStage.valueOf(tag.getString(LIFECYCLE_STAGE));
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
        if (speciesId == null) {
            return Optional.empty();
        }
        return Optional.of(new DinosaurAgeEstimate(
                speciesId,
                stage,
                sanitizePercent(tag.getDouble(GROWTH_PERCENTAGE)),
                readOptionalLong(tag, CURRENT_GAME_AGE_TICKS),
                readOptionalLong(tag, ADULT_GAME_AGE_TICKS),
                readOptionalDouble(tag, CURRENT_REAL_AGE_YEARS),
                readOptionalDouble(tag, ADULT_REAL_AGE_YEARS)
        ));
    }

    private static List<ObservedGene> readGenes(ListTag genesTag) {
        List<ObservedGene> genes = new ArrayList<>();
        for (int index = 0; index < genesTag.size(); index++) {
            CompoundTag geneTag = genesTag.getCompound(index);
            ResourceLocation itemId = ResourceLocation.tryParse(geneTag.getString(GENE_ITEM_ID));
            if (itemId == null) {
                continue;
            }
            genes.add(new ObservedGene(
                    geneTag.getString(GENE_ID),
                    itemId,
                    Component.literal(geneTag.getString(GENE_DISPLAY_NAME))
            ));
        }
        return List.copyOf(genes);
    }

    private static void putOptionalDouble(CompoundTag tag, String key, OptionalDouble value) {
        if (value.isPresent() && Double.isFinite(value.getAsDouble())) {
            tag.putDouble(key, value.getAsDouble());
        }
    }

    private static OptionalDouble readOptionalDouble(CompoundTag tag, String key) {
        if (!tag.contains(key, Tag.TAG_DOUBLE)) {
            return OptionalDouble.empty();
        }
        double value = tag.getDouble(key);
        return Double.isFinite(value) ? OptionalDouble.of(value) : OptionalDouble.empty();
    }

    private static void putOptionalLong(CompoundTag tag, String key, OptionalLong value) {
        value.ifPresent(number -> tag.putLong(key, Math.max(0L, number)));
    }

    private static OptionalLong readOptionalLong(CompoundTag tag, String key) {
        return tag.contains(key, Tag.TAG_LONG) ? OptionalLong.of(Math.max(0L, tag.getLong(key))) : OptionalLong.empty();
    }

    private static double sanitizePercent(double value) {
        if (!Double.isFinite(value)) {
            return 0.0D;
        }
        return Math.max(0.0D, Math.min(100.0D, value));
    }
}
