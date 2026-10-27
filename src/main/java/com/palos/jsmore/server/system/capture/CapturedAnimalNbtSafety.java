package com.palos.jsmore.server.system.capture;

import jp.jurassicsaga.server.generic.gene.obj.GeneType;
import jp.jurassicsaga.server.generic.obj.ActiveTime;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

/** Reject unsafe upstream decoder inputs before constructing a temporary animal. Never edits raw data. */
final class CapturedAnimalNbtSafety {
    private static final int MAX_GENES = 64;

    private CapturedAnimalNbtSafety() { }

    static boolean canRestore(CompoundTag entity) {
        if (!entity.contains("js.genetics.data")) return true;
        if (!entity.contains("js.genetics.data", Tag.TAG_COMPOUND)) return false;
        CompoundTag genes = entity.getCompound("js.genetics.data");
        if (!optionalInt(genes, "js.genetic_data.size")
                || genes.getInt("js.genetic_data.size") < 0
                || genes.getInt("js.genetic_data.size") > MAX_GENES
                || !validOrdinal(genes, "js.genetics.type", GeneType.values().length)
                || !validOrdinal(genes, "js.genetics.activetime", ActiveTime.values().length)) return false;
        if (genes.contains("js.genetic_data.size_dimorphism")
                && (!genes.contains("js.genetic_data.size_dimorphism", Tag.TAG_FLOAT)
                    || !Float.isFinite(genes.getFloat("js.genetic_data.size_dimorphism")))) return false;
        // This count is consumed by an upstream integer loop; byte-size limits alone do not bound it.
        for (int i = 1; i <= genes.getInt("js.genetic_data.size"); i++) {
            String key = "js.genetic_data." + i;
            if (!genes.contains(key, Tag.TAG_STRING)
                    || genes.getString(key).length() > 256) return false;
        }
        if (genes.contains("js.genetics.unstable")) {
            if (!(genes.get("js.genetics.unstable") instanceof ListTag unstable)
                    || unstable.size() > MAX_GENES
                    || (!unstable.isEmpty() && unstable.getElementType() != Tag.TAG_STRING)) return false;
            for (int i = 0; i < unstable.size(); i++) if (unstable.getString(i).length() > 256) return false;
        }
        return true;
    }

    private static boolean validOrdinal(CompoundTag tag, String key, int count) {
        return optionalInt(tag, key) && tag.getInt(key) >= 0 && tag.getInt(key) < count;
    }

    private static boolean optionalInt(CompoundTag tag, String key) {
        return !tag.contains(key) || tag.contains(key, Tag.TAG_INT);
    }
}
