package com.palos.jsmore.mixin.client;

import com.palos.jsmore.client.system.anesthetic.ClientSleepAnimationCommandGuard;
import collinvht.travelers.client.azure.common.animation.dispatch.command.AzCommand;
import collinvht.travelers.client.azure.common.network.packet.AzEntityDispatchCommandPacket;
import collinvht.travelers.client.azure.common.util.client.ClientUtils;
import jp.jurassicsaga.server.animal.entity.obj.bases.JSAnimalBase;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = AzEntityDispatchCommandPacket.class, remap = false)
public abstract class AzEntityDispatchCommandPacketMixin {
    @Shadow
    public abstract int entityId();

    @Shadow
    public abstract AzCommand dispatchCommand();

    @Inject(method = "apply", at = @At("HEAD"), cancellable = true, require = 0)
    private void jsmore$blockOrdinaryAzureAnimationDuringSleep(CallbackInfo callbackInfo) {
        Level level = ClientUtils.getLevel();
        if (level == null) {
            return;
        }
        Entity entity = level.getEntity(this.entityId());
        if (entity instanceof JSAnimalBase animal) {
            AzCommand command = this.dispatchCommand();
            ClientSleepAnimationCommandGuard.prepare(animal, command);
            if (ClientSleepAnimationCommandGuard.shouldBlock(animal, command)) {
                callbackInfo.cancel();
            }
        }
    }
}
