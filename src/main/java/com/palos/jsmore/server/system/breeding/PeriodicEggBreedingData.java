package com.palos.jsmore.server.system.breeding;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import jp.jurassicsaga.server.animal.JSAnimals;
import jp.jurassicsaga.server.animal.animals.obj.JSAnimal;
import jp.jurassicsaga.server.generic.gene.JSGene;
import jp.jurassicsaga.server.generic.gene.JSGenetics;
import jp.jurassicsaga.server.generic.gene.obj.GeneType;
import jp.jurassicsaga.server.generic.gene.obj.JSGeneData;
import jp.jurassicsaga.server.generic.obj.ActiveTime;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.common.util.INBTSerializable;

public final class PeriodicEggBreedingData implements INBTSerializable<CompoundTag> {
    private static final String STATE_KEY = "State";
    private static final String MATERNAL_GENES_KEY = "MaternalGenes";
    private static final String GENE_COUNT_KEY = "js.genetic_data.size";
    private static final String GENE_ENTRY_PREFIX = "js.genetic_data.";
    private static final String BASE_QUALITY_KEY = "js.genetic_data.quality.base";
    private static final String DONOR_QUALITY_KEY =
            "js.genetic_data.quality.donor.quality";
    private static final String GENE_SEED_KEY = "js.genetic_data.seed";
    private static final String BASE_NAME_KEY = "js.genetic_data.name.base";
    private static final String DONOR_NAME_KEY = "js.genetic_data.name.donor";
    private static final String GENE_TYPE_KEY = "js.genetics.type";
    private static final String ACTIVE_TIME_KEY = "js.genetics.activetime";
    private static final String STUNTED_KEY = "js.genetics.stunted";
    private static final String SIZE_DIMORPHISM_KEY = "js.genetic_data.size_dimorphism";
    private static final int MAX_GENES = 8;
    private static final int MAX_QUALITY = 100;
    private static final int MAX_OWNER_NAME_LENGTH = 256;
    private static final float MIN_SIZE_DIMORPHISM_EXCLUSIVE = -1.0F;
    private static final float MAX_SIZE_DIMORPHISM = 1.0F;
    private static final int MAX_SERIALIZED_BYTES = 64 * 1024;

    private State state = State.IDLE;
    private CompoundTag maternalGenes = new CompoundTag();

    public State state() {
        return this.state;
    }

    public boolean isPending() {
        return this.state == State.PENDING;
    }

    public boolean arm(JSGeneData genes, JSAnimal<?> expectedOwner) {
        if (isPending()
                || genes == null
                || expectedOwner == null
                || genes.getAnimal() != expectedOwner) {
            return false;
        }
        Optional<CompoundTag> validated = validatedGenes(genes, expectedOwner);
        if (validated.isEmpty()) {
            return false;
        }
        this.state = State.PENDING;
        this.maternalGenes = validated.get();
        return true;
    }

    public Optional<JSGeneData> maternalGeneData() {
        return decodeGenes(this.maternalGenes);
    }

    public Optional<JSGeneData> maternalGeneData(JSAnimal<?> expectedOwner) {
        if (!isPending() || expectedOwner == null) {
            return Optional.empty();
        }
        Optional<JSGeneData> decoded = decodeGenes(this.maternalGenes);
        if (decoded.isEmpty() || decoded.get().getAnimal() != expectedOwner) {
            clear();
            return Optional.empty();
        }
        return decoded;
    }

    public boolean validateOwner(JSAnimal<?> expectedOwner) {
        if (!isPending()) {
            return true;
        }
        return maternalGeneData(expectedOwner).isPresent();
    }

    public CompoundTag maternalGenesNbt() {
        return this.maternalGenes.copy();
    }

    public void clear() {
        this.state = State.IDLE;
        this.maternalGenes = new CompoundTag();
    }

    @Override
    public CompoundTag serializeNBT(HolderLookup.Provider provider) {
        CompoundTag tag = new CompoundTag();
        if (!isPending()) {
            tag.putString(STATE_KEY, State.IDLE.name());
            return tag;
        }
        Optional<JSGeneData> decoded = decodeGenes(this.maternalGenes);
        if (decoded.isEmpty()) {
            clear();
            tag.putString(STATE_KEY, State.IDLE.name());
            return tag;
        }
        tag.putString(STATE_KEY, State.PENDING.name());
        tag.put(MATERNAL_GENES_KEY, this.maternalGenes.copy());
        return tag;
    }

    @Override
    public void deserializeNBT(HolderLookup.Provider provider, CompoundTag tag) {
        clear();
        if (tag == null
                || tag.sizeInBytes() > MAX_SERIALIZED_BYTES
                || !tag.contains(STATE_KEY, Tag.TAG_STRING)
                || !State.PENDING.name().equals(tag.getString(STATE_KEY))
                || !tag.contains(MATERNAL_GENES_KEY, Tag.TAG_COMPOUND)) {
            return;
        }
        CompoundTag candidate = tag.getCompound(MATERNAL_GENES_KEY).copy();
        if (decodeGenes(candidate).isEmpty()) {
            return;
        }
        this.state = State.PENDING;
        this.maternalGenes = candidate;
    }

    private static Optional<CompoundTag> validatedGenes(
            JSGeneData genes,
            JSAnimal<?> expectedOwner
    ) {
        if (genes == null
                || expectedOwner == null
                || genes.getAnimal() != expectedOwner) {
            return Optional.empty();
        }
        try {
            CompoundTag serialized = genes.saveToNbt(new CompoundTag());
            return decodeGenes(serialized)
                    .filter(decoded -> decoded.getAnimal() == expectedOwner)
                    .map(ignored -> serialized.copy());
        } catch (RuntimeException | LinkageError exception) {
            return Optional.empty();
        }
    }

    private static Optional<JSGeneData> decodeGenes(CompoundTag tag) {
        if (tag == null
                || tag.isEmpty()
                || tag.sizeInBytes() > MAX_SERIALIZED_BYTES
                || !isSerializedGeneSchemaSafe(tag)) {
            return Optional.empty();
        }
        try {
            JSGeneData decoded = new JSGeneData();
            decoded.loadFromNbt(tag.copy());
            if (!decoded.isValid()
                    || decoded.getAnimal() == null
                    || !isCanonicalDecodedForm(tag, decoded)) {
                return Optional.empty();
            }
            return Optional.of(decoded);
        } catch (RuntimeException | LinkageError exception) {
            return Optional.empty();
        }
    }

    static boolean isSerializedGeneSchemaSafe(CompoundTag tag) {
        if (tag == null
                || !hasExactType(tag, GENE_COUNT_KEY, Tag.TAG_INT)
                || !hasExactType(tag, BASE_QUALITY_KEY, Tag.TAG_INT)
                || !hasExactType(tag, DONOR_QUALITY_KEY, Tag.TAG_INT)
                || !hasExactType(tag, GENE_SEED_KEY, Tag.TAG_INT)
                || !hasExactType(tag, SIZE_DIMORPHISM_KEY, Tag.TAG_FLOAT)
                || !hasExactType(tag, BASE_NAME_KEY, Tag.TAG_STRING)
                || !hasExactType(tag, DONOR_NAME_KEY, Tag.TAG_STRING)
                || !hasExactType(tag, GENE_TYPE_KEY, Tag.TAG_INT)
                || !hasExactType(tag, ACTIVE_TIME_KEY, Tag.TAG_INT)
                || !hasExactType(tag, STUNTED_KEY, Tag.TAG_BYTE)) {
            return false;
        }

        int geneCount = tag.getInt(GENE_COUNT_KEY);
        int baseQuality = tag.getInt(BASE_QUALITY_KEY);
        int donorQuality = tag.getInt(DONOR_QUALITY_KEY);
        float sizeDimorphism = tag.getFloat(SIZE_DIMORPHISM_KEY);
        int geneType = tag.getInt(GENE_TYPE_KEY);
        int activeTime = tag.getInt(ACTIVE_TIME_KEY);
        byte stunted = tag.getByte(STUNTED_KEY);
        String ownerName = tag.getString(BASE_NAME_KEY);
        String donorName = tag.getString(DONOR_NAME_KEY);
        if (geneCount < 0
                || geneCount > MAX_GENES
                || baseQuality < 0
                || baseQuality > MAX_QUALITY
                || donorQuality < 0
                || donorQuality > MAX_QUALITY
                || !Float.isFinite(sizeDimorphism)
                || sizeDimorphism <= MIN_SIZE_DIMORPHISM_EXCLUSIVE
                || sizeDimorphism > MAX_SIZE_DIMORPHISM
                || geneType < 0
                || geneType >= GeneType.values().length
                || activeTime < 0
                || activeTime >= ActiveTime.values().length
                || (stunted != 0 && stunted != 1)
                || !isKnownAnimalName(ownerName, false)
                || !isKnownAnimalName(donorName, true)
                || !hasOnlyDeclaredCanonicalGeneSlots(tag, geneCount)) {
            return false;
        }

        List<JSGene> recognized = new ArrayList<>(geneCount);
        Set<ResourceLocation> seenIds = new HashSet<>(geneCount);
        for (int index = 1; index <= geneCount; index++) {
            String key = GENE_ENTRY_PREFIX + index;
            if (!hasExactType(tag, key, Tag.TAG_STRING)) {
                return false;
            }
            String encodedId = tag.getString(key);
            ResourceLocation id = ResourceLocation.tryParse(encodedId);
            if (id == null || !id.toString().equals(encodedId) || !seenIds.add(id)) {
                return false;
            }
            JSGene gene = JSGenetics.getGene(id);
            if (gene == null
                    || !id.equals(gene.getRegisteredLocation())
                    || !isCompatibleWithAll(gene, recognized)) {
                return false;
            }
            recognized.add(gene);
        }
        return true;
    }

    private static boolean isCanonicalDecodedForm(
            CompoundTag original,
            JSGeneData decoded
    ) {
        int declaredCount = original.getInt(GENE_COUNT_KEY);
        List<JSGene> decodedGenes = decoded.geneDataHolder.getGENE_SET();
        if (decodedGenes.size() != declaredCount) {
            return false;
        }
        for (int index = 0; index < declaredCount; index++) {
            ResourceLocation expected = ResourceLocation.tryParse(
                    original.getString(GENE_ENTRY_PREFIX + (index + 1))
            );
            if (expected == null
                    || !expected.equals(decodedGenes.get(index).getRegisteredLocation())) {
                return false;
            }
        }

        CompoundTag canonical = decoded.saveToNbt(new CompoundTag());
        for (String key : List.of(
                GENE_COUNT_KEY,
                BASE_QUALITY_KEY,
                DONOR_QUALITY_KEY,
                GENE_SEED_KEY,
                SIZE_DIMORPHISM_KEY,
                BASE_NAME_KEY,
                DONOR_NAME_KEY,
                GENE_TYPE_KEY,
                ACTIVE_TIME_KEY,
                STUNTED_KEY
        )) {
            if (!original.get(key).equals(canonical.get(key))) {
                return false;
            }
        }
        for (int index = 1; index <= declaredCount; index++) {
            String key = GENE_ENTRY_PREFIX + index;
            if (!original.get(key).equals(canonical.get(key))) {
                return false;
            }
        }
        return true;
    }

    private static boolean isCompatibleWithAll(
            JSGene candidate,
            List<JSGene> existing
    ) {
        try {
            if (!candidate.canAddTo(existing)) {
                return false;
            }
            for (JSGene gene : existing) {
                if (!gene.canAddTo(List.of(candidate))) {
                    return false;
                }
            }
            return true;
        } catch (RuntimeException | LinkageError exception) {
            return false;
        }
    }

    private static boolean isKnownAnimalName(String name, boolean emptyAllowed) {
        if (name == null
                || name.length() > MAX_OWNER_NAME_LENGTH
                || (!emptyAllowed && name.isBlank())) {
            return false;
        }
        if (name.isEmpty()) {
            return emptyAllowed;
        }
        try {
            return JSAnimals.getAnimal(name) != null;
        } catch (RuntimeException | LinkageError exception) {
            return false;
        }
    }

    private static boolean hasOnlyDeclaredCanonicalGeneSlots(
            CompoundTag tag,
            int declaredCount
    ) {
        for (String key : tag.getAllKeys()) {
            if (!key.startsWith(GENE_ENTRY_PREFIX)) {
                continue;
            }
            String suffix = key.substring(GENE_ENTRY_PREFIX.length());
            if (suffix.isEmpty()) {
                continue;
            }
            boolean allDigits = true;
            for (int index = 0; index < suffix.length(); index++) {
                char character = suffix.charAt(index);
                if (character < '0' || character > '9') {
                    allDigits = false;
                    break;
                }
            }
            if (!allDigits) {
                continue;
            }

            int slot;
            try {
                slot = Integer.parseInt(suffix);
            } catch (NumberFormatException exception) {
                return false;
            }
            if (slot < 1
                    || slot > declaredCount
                    || !Integer.toString(slot).equals(suffix)) {
                return false;
            }
        }
        return true;
    }

    private static boolean hasExactType(CompoundTag tag, String key, int expectedType) {
        Tag value = tag.get(key);
        return value != null && value.getId() == expectedType;
    }

    public enum State {
        IDLE,
        PENDING
    }
}
