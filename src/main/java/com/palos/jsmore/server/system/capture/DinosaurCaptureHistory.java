package com.palos.jsmore.server.system.capture;

import jp.jurassicsaga.server.entity.animal.entity.obj.bases.JSAnimalBase;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

/** Per-individual history, independent of the currently selected protection mode. */
public final class DinosaurCaptureHistory {
    static final String KEY = "jsmore:captured_once";

    private DinosaurCaptureHistory() {}

    public static boolean wasCaptured(JSAnimalBase animal) {
        CompoundTag data = animal.getPersistentData();
        return data.contains(KEY, Tag.TAG_BYTE) && data.getByte(KEY) == 1;
    }

    static void markRestoredAnimal(JSAnimalBase animal) {
        animal.getPersistentData().putBoolean(KEY, true);
    }

    /** Mark only the prospective payload; failed capture must not change the live source. */
    static void markCapturedPayload(CompoundTag entityNbt) {
        CompoundTag data = entityNbt.getCompound("NeoForgeData").copy();
        data.putBoolean(KEY, true);
        entityNbt.put("NeoForgeData", data);
    }
}
