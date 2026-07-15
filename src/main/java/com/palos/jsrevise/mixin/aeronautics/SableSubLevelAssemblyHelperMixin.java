package com.palos.jsrevise.mixin.aeronautics;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;

/** String-target marker for the exact Sable moveBlocks transaction bytecode patch. */
@Pseudo
@Mixin(targets = "dev.ryanhcode.sable.api.SubLevelAssemblyHelper", remap = false)
public abstract class SableSubLevelAssemblyHelperMixin {
}
