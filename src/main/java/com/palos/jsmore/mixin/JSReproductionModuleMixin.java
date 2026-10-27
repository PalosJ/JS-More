package com.palos.jsmore.mixin;

import com.palos.jsmore.server.system.breeding.LegacyReproductionMigration;
import jp.jurassicsaga.server.entity.animal.entity.obj.bases.JSAnimalBase;
import jp.jurassicsaga.server.entity.animal.entity.obj.modules.JSAnimalModuleBase;
import jp.jurassicsaga.server.entity.animal.entity.obj.modules.obj.JSReproductionModule;
import net.minecraft.nbt.CompoundTag;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(value = JSReproductionModule.class, remap = false)
public abstract class JSReproductionModuleMixin extends JSAnimalModuleBase {
    protected JSReproductionModuleMixin(JSAnimalBase owner) { super(owner); }

    @ModifyVariable(method = "loadNbt", at = @At("HEAD"), argsOnly = true)
    private CompoundTag jsmore$preserveLegacyReproduction(CompoundTag tag) {
        return LegacyReproductionMigration.prepare(this.owner, tag);
    }
}
