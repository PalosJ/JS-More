package com.palos.jsrevise;

import com.mojang.logging.LogUtils;
import collinvht.travelers.core.TravelersLib;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;

public final class JSRevise {
    public static final String MOD_ID = "jsrevise";
    public static final String MOD_NAME = "JS-revise";
    public static final Logger LOGGER = LogUtils.getLogger();

    private static final AtomicBoolean INITIALIZED = new AtomicBoolean(false);

    private JSRevise() {
    }

    public static void init() {
        if (!INITIALIZED.compareAndSet(false, true)) {
            return;
        }

        // 对齐官方附属模组的接入顺序，让 Travelers/Jurassic Saga 正确识别当前附属。
        TravelersLib.registerMod(MOD_ID);
        LOGGER.info("Initialized Jurassic Saga addon bootstrap for {}", MOD_NAME);
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }
}
