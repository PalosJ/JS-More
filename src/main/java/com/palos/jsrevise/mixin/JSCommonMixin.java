package com.palos.jsrevise.mixin;

import com.palos.jsrevise.JSRevise;
import jp.jurassicsaga.JSCommon;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = JSCommon.class, remap = false)
public final class JSCommonMixin {
    @Inject(method = "<init>", at = @At("TAIL"))
    private static void jsrevise$initAddon(CallbackInfo callbackInfo) {
        JSRevise.init();
    }
}
