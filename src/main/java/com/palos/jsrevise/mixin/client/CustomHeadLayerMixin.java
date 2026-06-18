package com.palos.jsrevise.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.palos.jsrevise.server.item.DinoDoctorGogglesItem;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.layers.CustomHeadLayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(CustomHeadLayer.class)
public abstract class CustomHeadLayerMixin {
    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void jsrevise$hideDoctorGogglesHeadItem(
            PoseStack poseStack,
            MultiBufferSource bufferSource,
            int packedLight,
            LivingEntity entity,
            float limbSwing,
            float limbSwingAmount,
            float partialTicks,
            float ageInTicks,
            float netHeadYaw,
            float headPitch,
            CallbackInfo callbackInfo
    ) {
        if (!(entity instanceof Player player)) {
            return;
        }

        ItemStack headStack = player.getItemBySlot(EquipmentSlot.HEAD);
        if (headStack.getItem() instanceof DinoDoctorGogglesItem) {
            callbackInfo.cancel();
        }
    }
}
