package com.palos.jsrevise.server.registry;

import com.palos.jsrevise.JSRevise;
import com.palos.jsrevise.server.item.AnestheticCrossbowItem;
import com.palos.jsrevise.server.item.AnestheticSyringeItem;
import com.palos.jsrevise.server.item.DinoDoctorGogglesItem;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class JSReviseItems {
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(JSRevise.MOD_ID);

    public static final DeferredItem<Item> ANESTHETIC_SYRINGE =
            ITEMS.register("anesthetic_syringe", AnestheticSyringeItem::new);
    public static final DeferredItem<Item> ANESTHETIC_CROSSBOW =
            ITEMS.register("anesthetic_crossbow", AnestheticCrossbowItem::new);
    public static final DeferredItem<Item> DINO_DOCTOR_GOGGLES =
            ITEMS.register("dino_doctor_goggles", DinoDoctorGogglesItem::new);

    private JSReviseItems() {
    }

    public static void register(IEventBus modEventBus) {
        ITEMS.register(modEventBus);
    }
}
