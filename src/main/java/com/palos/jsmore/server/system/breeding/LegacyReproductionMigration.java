package com.palos.jsmore.server.system.breeding;

import jp.jurassicsaga.server.entity.animal.entity.obj.bases.JSAnimalBase;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

/** Only adapts old saved module fields. The base animal's mate-search cooldown is unrelated. */
public final class LegacyReproductionMigration {
    private static final String NEW = "js.reproduction";
    private static final String NEW_FATHER = "js.reproduction.father";
    private static final String OLD_FATHER = "js.metabolism.data.father";
    private static final String RAW = "jsmore:unreadable_reproduction";

    private LegacyReproductionMigration() {}

    public static CompoundTag prepare(JSAnimalBase animal, CompoundTag original) {
        if (animal.level().isClientSide) return original;
        CompoundTag result = original.copy();
        CompoundTag data = original.contains(NEW) ? original.getCompound(NEW) : original.getCompound("js.metabolism");
        String gestation = original.contains(NEW) ? "gestation" : "js.metabolism.gestation";
        String cooldown = original.contains(NEW) ? "cooldown" : "js.metabolism.breedingcooldown";
        String father = original.contains(NEW) || original.contains(NEW_FATHER) ? NEW_FATHER : OLD_FATHER;
        if (original.contains(NEW)) result.remove(OLD_FATHER);
        boolean valid = (!original.contains(NEW) || original.contains(NEW, Tag.TAG_COMPOUND))
                && (original.contains(NEW) || !original.contains("js.metabolism") || original.contains("js.metabolism", Tag.TAG_COMPOUND))
                && validTicks(data, gestation) && validTicks(data, cooldown)
                && (data.getInt(gestation) <= 0 || original.contains(father, Tag.TAG_COMPOUND))
                && (!original.contains(father) || (original.contains(father, Tag.TAG_COMPOUND)
                    && PeriodicEggBreedingData.decodeGenes(original.getCompound(father)).isPresent()));
        if (!valid) {
            CompoundTag raw = new CompoundTag();
            for (String key : new String[] {NEW, NEW_FATHER, "js.metabolism", OLD_FATHER}) {
                if (original.contains(key)) raw.put(key, original.get(key).copy());
            }
            // One retained record per animal, no repeated attempts to decode unsafe genes.
            if (!animal.getPersistentData().contains(RAW)) animal.getPersistentData().put(RAW, raw);
            result.put(NEW, new CompoundTag());
            result.remove(NEW_FATHER);
            result.remove(OLD_FATHER);
            return result;
        }
        if (!original.contains(NEW)) {
            CompoundTag migrated = new CompoundTag();
            migrated.putInt("gestation", data.getInt(gestation));
            migrated.putInt("cooldown", data.getInt(cooldown));
            result.put(NEW, migrated);
        }
        return result;
    }

    private static boolean validTicks(CompoundTag data, String key) {
        return !data.contains(key) || (data.contains(key, Tag.TAG_INT) && data.getInt(key) >= 0);
    }
}
