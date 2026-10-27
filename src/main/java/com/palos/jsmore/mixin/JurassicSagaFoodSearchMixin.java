package com.palos.jsmore.mixin;

import com.palos.jsmore.compat.jurassicsaga.JurassicSagaFoodCandidateSorter;
import jp.jurassicsaga.server.entity.animal.animals.obj.behaviour.JSForageProfile;
import jp.jurassicsaga.server.entity.animal.entity.obj.bases.JSAnimalBase;
import jp.jurassicsaga.server.entity.animal.entity.obj.tasks.metabolism.food.JSFoodSearch;
import java.util.Comparator;
import java.util.List;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = JSFoodSearch.class, remap = false)
public abstract class JurassicSagaFoodSearchMixin {
    @Redirect(method = "gather", at = @At(value = "INVOKE",
            target = "Ljava/util/List;sort(Ljava/util/Comparator;)V"), require = 1, allow = 1)
    private static void jsmore$sortFoodCandidatesWithStableScores(List<Entity> candidates,
            Comparator<Entity> ignored, JSAnimalBase animal, float range, JSForageProfile profile) {
        JurassicSagaFoodCandidateSorter.sort(candidates, animal.position());
    }
}
