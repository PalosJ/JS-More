package com.palos.jsmore.server.registry;

import com.palos.jsmore.JSMore;
import com.palos.jsmore.server.item.AnestheticCrossbowItem;
import com.palos.jsmore.server.item.AnestheticSyringeItem;
import com.palos.jsmore.server.item.BrokenDinosaurCaptureBoxItem;
import com.palos.jsmore.server.item.DinoDoctorGogglesItem;
import com.palos.jsmore.server.item.DinosaurCaptureCageItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class JSMoreItems {
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(JSMore.MOD_ID);

    public static final DeferredItem<Item> ANESTHETIC_SYRINGE =
            ITEMS.register("anesthetic_syringe", AnestheticSyringeItem::new);
    public static final DeferredItem<Item> ANESTHETIC_POTION =
            ITEMS.registerSimpleItem("anesthetic_potion", new Item.Properties().stacksTo(64));
    public static final DeferredItem<Item> ANESTHETIC_DART =
            ITEMS.registerSimpleItem("anesthetic_dart", new Item.Properties().stacksTo(64));
    public static final DeferredItem<Item> ANESTHETIC_CROSSBOW =
            ITEMS.register("anesthetic_crossbow", AnestheticCrossbowItem::new);
    public static final DeferredItem<Item> DINO_DOCTOR_GOGGLES =
            ITEMS.register("dino_doctor_goggles", DinoDoctorGogglesItem::new);
    public static final DeferredItem<Item> DINOSAUR_CAPTURE_CAGE =
            ITEMS.register(
                    "dinosaur_capture_box",
                    () -> new DinosaurCaptureCageItem(JSMoreBlocks.DINOSAUR_CAPTURE_CAGE.get())
            );
    public static final DeferredItem<Item> BROKEN_DINOSAUR_CAPTURE_BOX =
            ITEMS.register(
                    "broken_dinosaur_capture_box",
                    () -> new BrokenDinosaurCaptureBoxItem(JSMoreBlocks.BROKEN_DINOSAUR_CAPTURE_BOX.get())
            );
    public static final DeferredItem<BlockItem> EGG_COLLECTOR =
            ITEMS.registerSimpleBlockItem(JSMoreBlocks.EGG_COLLECTOR);

    private JSMoreItems() {
    }

    public static void register(IEventBus modEventBus) {
        ITEMS.register(modEventBus);
    }
}
