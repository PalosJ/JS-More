package com.palos.jsrevise.mixin.aeronautics;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;

/** String-target marker for the exact Simulated movement bytecode patch. */
@Pseudo
@Mixin(targets = "dev.simulated_team.simulated.util.assembly.SimAssemblyContraption", remap = false)
public abstract class SimAssemblyContraptionMixin {
}
