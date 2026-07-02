package com.palos.jsrevise.server.registry;

import com.palos.jsrevise.JSRevise;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class JSReviseCreativeTabs {
    private static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, JSRevise.MOD_ID);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> MAIN_TAB = CREATIVE_MODE_TABS.register(
            "main",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.jsrevise.main"))
                    .icon(() -> new ItemStack(JSReviseItems.DINOSAUR_CAPTURE_CAGE.get()))
                    .displayItems((parameters, output) -> {
                        output.accept(JSReviseItems.DINO_DOCTOR_GOGGLES.get());
                        output.accept(JSReviseItems.ANESTHETIC_CROSSBOW.get());
                        output.accept(JSReviseItems.ANESTHETIC_SYRINGE.get());
                        output.accept(JSReviseItems.DINOSAUR_CAPTURE_CAGE.get());
                    })
                    .build()
    );

    private JSReviseCreativeTabs() {
    }

    public static void register(IEventBus modEventBus) {
        CREATIVE_MODE_TABS.register(modEventBus);
    }
}
