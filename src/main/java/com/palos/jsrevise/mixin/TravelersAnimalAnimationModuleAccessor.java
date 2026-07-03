package com.palos.jsrevise.mixin;

import collinvht.travelers.server.animal.entity.other.TravelersAnimalAnimationModule;
import java.util.Map;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = TravelersAnimalAnimationModule.class, remap = false)
public interface TravelersAnimalAnimationModuleAccessor {
    @Accessor("animationMap")
    Map<String, ?> jsrevise$getAnimationMap();
}
