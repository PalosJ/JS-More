package com.palos.jsrevise.system.observation;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

public record ObservedGene(
        String id,
        ResourceLocation itemId,
        Component displayName
) {
}
