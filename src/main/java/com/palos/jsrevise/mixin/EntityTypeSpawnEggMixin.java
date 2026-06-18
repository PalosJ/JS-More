package com.palos.jsrevise.mixin;

import com.palos.jsrevise.server.system.age.DinosaurAgeSystem;
import jp.jurassicsaga.server.animal.entity.obj.bases.JSAnimalBase;
import jp.jurassicsaga.server.animal.entity.obj.info.AnimalGrowthStage;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(net.minecraft.world.entity.EntityType.class)
public abstract class EntityTypeSpawnEggMixin {
    @Inject(
            method = "spawn(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/entity/MobSpawnType;ZZ)Lnet/minecraft/world/entity/Entity;",
            at = @At("RETURN")
    )
    private void jsrevise$adjustSpawnEggGrowth(
            ServerLevel level,
            ItemStack stack,
            Player player,
            BlockPos pos,
            MobSpawnType spawnType,
            boolean alignPosition,
            boolean invertY,
            CallbackInfoReturnable<Entity> callbackInfo
    ) {
        Entity entity = callbackInfo.getReturnValue();
        if (spawnType != MobSpawnType.SPAWN_EGG || !(entity instanceof JSAnimalBase animal)) {
            return;
        }

        boolean spawnBaby = player != null && player.isShiftKeyDown();
        animal.getModules().getGrowthStageModule().setGrowthStage(spawnBaby ? AnimalGrowthStage.BABY : AnimalGrowthStage.ADULT);
        animal.refreshDimensions();
        animal.hasImpulse = true;
        animal.hurtMarked = true;
        DinosaurAgeSystem.initializeSpawnEggAge(animal, spawnBaby);
    }
}
