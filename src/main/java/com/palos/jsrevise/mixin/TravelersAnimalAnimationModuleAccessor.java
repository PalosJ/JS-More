package com.palos.jsrevise.mixin;

import java.util.Map;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import travelers.server.animal.entity.other.TravelersAnimalAnimationModule;

@Mixin(value = TravelersAnimalAnimationModule.class, remap = false)
public interface TravelersAnimalAnimationModuleAccessor {
    @Accessor("animationMap")
    Map<String, ?> jsrevise$getAnimationMap();
}
