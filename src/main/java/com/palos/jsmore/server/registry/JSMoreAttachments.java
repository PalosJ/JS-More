package com.palos.jsmore.server.registry;

import com.palos.jsmore.JSMore;
import com.palos.jsmore.server.system.age.DinosaurAgeData;
import com.palos.jsmore.server.system.anesthetic.AnestheticData;
import com.palos.jsmore.server.system.anesthetic.AnestheticFloatData;
import com.palos.jsmore.server.system.breeding.PeriodicEggBreedingData;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

public final class JSMoreAttachments {
    private static final DeferredRegister<AttachmentType<?>> ATTACHMENTS =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, JSMore.MOD_ID);

    public static final DeferredHolder<AttachmentType<?>, AttachmentType<DinosaurAgeData>> DINOSAUR_AGE =
            ATTACHMENTS.register("dinosaur_age", () -> AttachmentType.serializable(DinosaurAgeData::new)
                    .sync(DinosaurAgeData.STREAM_CODEC)
                    .build());
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<AnestheticData>> ANESTHETIC =
            ATTACHMENTS.register("anesthetic", () -> AttachmentType.serializable(AnestheticData::new)
                    .sync(AnestheticData.STREAM_CODEC)
                    .build());
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<AnestheticFloatData>> ANESTHETIC_FLOAT =
            ATTACHMENTS.register("anesthetic_float", () -> AttachmentType.builder(AnestheticFloatData::new)
                    .sync(AnestheticFloatData.STREAM_CODEC)
                    .build());
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<PeriodicEggBreedingData>>
            PERIODIC_EGG_BREEDING =
            ATTACHMENTS.register(
                    "periodic_egg_breeding",
                    () -> AttachmentType.serializable(PeriodicEggBreedingData::new).build()
            );

    private JSMoreAttachments() {
    }

    public static void register(IEventBus modEventBus) {
        ATTACHMENTS.register(modEventBus);
    }
}
