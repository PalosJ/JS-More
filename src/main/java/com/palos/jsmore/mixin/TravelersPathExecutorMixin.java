package com.palos.jsmore.mixin;

import com.palos.jsmore.compat.travelers.TravelersPathExecutorLifecycle;
import java.util.concurrent.ExecutorService;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "collinvht.travelers.server.animal.entity.pathingsystem.navigation.TravelersPathNavigation", remap = false)
public abstract class TravelersPathExecutorMixin {
    @Shadow @Final private static ExecutorService PATH_EXECUTOR;

    @Inject(method = "<clinit>", at = @At("TAIL"), require = 1)
    private static void jsmore$letTheServerProcessExit(CallbackInfo callback) {
        TravelersPathExecutorLifecycle.useDaemonWorkers(PATH_EXECUTOR);
    }
}
