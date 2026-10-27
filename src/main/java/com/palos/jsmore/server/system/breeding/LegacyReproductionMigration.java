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
        boolean hasNew = original.contains(NEW);
        if (hasNew && validRecord(original, NEW, "gestation", "cooldown", NEW_FATHER)) {
            result.remove(OLD_FATHER);
            return result;
        }
        if (hasNew) preserveRaw(animal, original);

        // An invalid new record must not take priority over usable legacy data.
        // Once a new record exists, its father belongs to that record, not the fallback.
        String father = !hasNew && original.contains(NEW_FATHER) ? NEW_FATHER : OLD_FATHER;
        CompoundTag old = original.getCompound("js.metabolism");
        if (validRecord(original, "js.metabolism", "js.metabolism.gestation", "js.metabolism.breedingcooldown", father)) {
            CompoundTag migrated = new CompoundTag();
            migrated.putInt("gestation", old.getInt("js.metabolism.gestation"));
            migrated.putInt("cooldown", old.getInt("js.metabolism.breedingcooldown"));
            result.put(NEW, migrated);
            result.remove(NEW_FATHER);
            if (original.contains(father)) result.put(NEW_FATHER, original.get(father).copy());
        } else {
            preserveRaw(animal, original);
            result.put(NEW, new CompoundTag());
            result.remove(NEW_FATHER);
        }
        result.remove(OLD_FATHER);
        return result;
    }

    private static boolean validRecord(CompoundTag original, String record, String gestation, String cooldown, String father) {
        CompoundTag data = original.getCompound(record);
        return (!original.contains(record) || original.contains(record, Tag.TAG_COMPOUND))
                && validTicks(data, gestation) && validTicks(data, cooldown)
                && (data.getInt(gestation) <= 0 || original.contains(father, Tag.TAG_COMPOUND))
                && (!original.contains(father) || (original.contains(father, Tag.TAG_COMPOUND)
                    && PeriodicEggBreedingData.decodeGenes(original.getCompound(father)).isPresent()));
    }

    private static void preserveRaw(JSAnimalBase animal, CompoundTag original) {
        if (animal.getPersistentData().contains(RAW)) return;
        CompoundTag raw = new CompoundTag();
        for (String key : new String[] {NEW, NEW_FATHER, "js.metabolism", OLD_FATHER}) {
            if (original.contains(key)) raw.put(key, original.get(key).copy());
        }
        animal.getPersistentData().put(RAW, raw);
    }

    private static boolean validTicks(CompoundTag data, String key) {
        return !data.contains(key) || (data.contains(key, Tag.TAG_INT) && data.getInt(key) >= 0);
    }
}
