package com.palos.jsmore.server.system;

import com.palos.jsmore.server.system.age.DinosaurAgeSystem;
import com.palos.jsmore.server.system.anesthetic.DinosaurAnestheticSystem;
import jp.jurassicsaga.server.animal.entity.obj.bases.JSAnimalBase;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

public final class JSAnimalTickHandler {
    private JSAnimalTickHandler() {
    }

    public static void onEntityTickPost(EntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof JSAnimalBase animal) || animal.level().isClientSide) {
            return;
        }
        DinosaurAgeSystem.tick(animal);
        DinosaurAnestheticSystem.tickServer(animal);
    }

    public static void onPlayerStartTracking(PlayerEvent.StartTracking event) {
        if (event.getTarget() instanceof JSAnimalBase animal) {
            DinosaurAnestheticSystem.syncSleepStateForTracking(animal);
        }
    }
}
