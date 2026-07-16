package com.palos.jsmore.server.registry;

import com.palos.jsmore.JSMore;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class JSMoreCreativeTabs {
    private static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, JSMore.MOD_ID);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> MAIN_TAB = CREATIVE_MODE_TABS.register(
            "main",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.jsmore.main"))
                    .icon(() -> new ItemStack(JSMoreItems.DINOSAUR_CAPTURE_CAGE.get()))
                    .displayItems((parameters, output) -> {
                        output.accept(JSMoreItems.DINO_DOCTOR_GOGGLES.get());
                        output.accept(JSMoreItems.ANESTHETIC_POTION.get());
                        output.accept(JSMoreItems.ANESTHETIC_SYRINGE.get());
                        output.accept(JSMoreItems.ANESTHETIC_DART.get());
                        output.accept(JSMoreItems.ANESTHETIC_CROSSBOW.get());
                        output.accept(JSMoreItems.DINOSAUR_CAPTURE_CAGE.get());
                        output.accept(JSMoreItems.BROKEN_DINOSAUR_CAPTURE_BOX.get());
                    })
                    .build()
    );

    private JSMoreCreativeTabs() {
    }

    public static void register(IEventBus modEventBus) {
        CREATIVE_MODE_TABS.register(modEventBus);
    }
}
