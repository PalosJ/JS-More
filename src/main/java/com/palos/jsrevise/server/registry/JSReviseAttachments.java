package com.palos.jsrevise.server.registry;

import com.palos.jsrevise.JSRevise;
import com.palos.jsrevise.server.system.age.DinosaurAgeData;
import com.palos.jsrevise.server.system.anesthetic.AnestheticData;
import com.palos.jsrevise.server.system.anesthetic.AnestheticFloatData;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

public final class JSReviseAttachments {
    private static final DeferredRegister<AttachmentType<?>> ATTACHMENTS =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, JSRevise.MOD_ID);

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

    private JSReviseAttachments() {
    }

    public static void register(IEventBus modEventBus) {
        ATTACHMENTS.register(modEventBus);
    }
}
