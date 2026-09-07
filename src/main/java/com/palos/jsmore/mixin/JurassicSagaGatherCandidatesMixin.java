package com.palos.jsmore.mixin;

import com.palos.jsmore.compat.jurassicsaga.JurassicSagaFoodCandidateSorter;
import java.util.List;
import java.util.Comparator;
import jp.jurassicsaga.server.animal.entity.obj.bases.JSAnimalBase;
import jp.jurassicsaga.server.animal.entity.obj.tasks.JSTaskBase;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Pseudo
@Mixin(
        targets = "jp.jurassicsaga.server.animal.entity.obj.tasks.metabolism.JSFindFoodTask",
        remap = false
)
public abstract class JurassicSagaGatherCandidatesMixin extends JSTaskBase {
    protected JurassicSagaGatherCandidatesMixin(JSAnimalBase animal) {
        super(animal);
    }

    @Redirect(
            method = "gatherCandidates(Lnet/minecraft/world/level/Level;F)Ljava/util/List;",
            at = @At(
                    value = "INVOKE",
                    target = "Ljava/util/List;sort(Ljava/util/Comparator;)V"
            ),
            require = 1
    )
    private void jsmore$sortFoodCandidatesWithStableScores(
            List<Entity> candidates,
            Comparator<Entity> ignoredComparator
    ) {
        JurassicSagaFoodCandidateSorter.sort(candidates, this.animal.position());
    }
}
