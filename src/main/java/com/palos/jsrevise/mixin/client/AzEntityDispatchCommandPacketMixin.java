package com.palos.jsrevise.mixin.client;

import com.palos.jsrevise.server.system.anesthetic.DinosaurAnestheticSystem;
import jp.jurassicsaga.server.animal.entity.obj.bases.JSAnimalBase;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import travelers.azurelib.common.animation.dispatch.command.AzCommand;
import travelers.azurelib.common.network.packet.AzEntityDispatchCommandPacket;
import travelers.azurelib.common.util.client.ClientUtils;

@Mixin(value = AzEntityDispatchCommandPacket.class, remap = false)
public abstract class AzEntityDispatchCommandPacketMixin {
    @Shadow
    public abstract int entityId();

    @Shadow
    public abstract AzCommand dispatchCommand();

    @Inject(method = "handle", at = @At("HEAD"), cancellable = true, require = 0)
    private void jsrevise$blockOrdinaryAzureAnimationDuringSleep(CallbackInfo callbackInfo) {
        Level level = ClientUtils.getLevel();
        if (level == null) {
            return;
        }
        Entity entity = level.getEntity(this.entityId());
        if (entity instanceof JSAnimalBase animal) {
            AzCommand command = this.dispatchCommand();
            DinosaurAnestheticSystem.prepareClientSleepAnimationGuard(animal, command);
            if (DinosaurAnestheticSystem.shouldBlockClientAnimationCommand(animal, command)) {
                callbackInfo.cancel();
            }
        }
    }
}
