package com.palos.jsmore.server.system.capture;

import com.palos.jsmore.JSMore;
import com.palos.jsmore.config.AnimalDespawnProtection;
import com.palos.jsmore.config.JSMoreServerConfig;
import com.palos.jsmore.server.system.DinosaurDespawnPolicy;
import jp.jurassicsaga.server.entity.animal.JSAnimals;
import jp.jurassicsaga.server.entity.animal.entity.obj.bases.JSAnimalBase;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(JSMore.MOD_ID)
@PrefixGameTestTemplate(false)
public final class AnimalDespawnGameTests {
    private AnimalDespawnGameTests() { }

    @GameTest(template = "profile_compatibility", timeoutTicks = 100)
    @SuppressWarnings("removal")
    public static void actualDespawnRespectsModesCaptureHistoryAndNativePersistence(GameTestHelper helper) {
        var player = new net.neoforged.neoforge.common.util.FakePlayer(helper.getLevel(),
                new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), "[JSMore Test]"));
        var previous = JSMoreServerConfig.animalDespawnProtection();
        int tested = 0;
        try {
            helper.getLevel().addNewPlayer(player);
            for (var species : JSAnimals.getAnimals()) {
                for (var mode : AnimalDespawnProtection.values()) {
                    JSMoreServerConfig.ANIMAL_DESPAWN_PROTECTION.set(mode);
                    JSMoreServerConfig.ANIMAL_DESPAWN_PROTECTION.clearCache(); // simulate the documented world restart
                    for (boolean captured : new boolean[] {false, true}) {
                        JSAnimalBase animal = (JSAnimalBase) species.getEntityType().get().create(helper.getLevel());
                        boolean nativePersistent = animal.isPersistenceRequired() || animal.requiresCustomPersistence()
                                || species.getAnimalAttributes().getEntityAttributeProperties().isPersistent();
                        animal.setPos(player.position().add(100_000, 10, 100_000));
                        if (captured) DinosaurCaptureHistory.markRestoredAnimal(animal);
                        boolean expected = nativePersistent || DinosaurDespawnPolicy.protects(mode, animal.getType().getCategory(), captured);
                        animal.checkDespawn();
                        helper.assertTrue(animal.isRemoved() != expected, "Despawn mismatch for " + species.getAnimalAttributes().getAnimalName() + " / " + mode + " / " + captured);
                        animal.discard(); tested++;
                    }
                }
            }
            JSMoreServerConfig.ANIMAL_DESPAWN_PROTECTION.set(AnimalDespawnProtection.OFF);
            JSMoreServerConfig.ANIMAL_DESPAWN_PROTECTION.clearCache();
            JSAnimalBase nativePersistent = (JSAnimalBase) JSAnimals.getAnimal("jurassicsaga:goat").getEntityType().get().create(helper.getLevel());
            nativePersistent.setPos(player.position().add(100_000, 10, 100_000)); nativePersistent.setPersistenceRequired();
            nativePersistent.checkDespawn();
            helper.assertTrue(!nativePersistent.isRemoved(), "OFF removed upstream vanilla-persistent animal");
            nativePersistent.discard();
            helper.assertTrue(tested > 0, "Despawn fixtures did not cover registered animals");
        } finally {
            JSMoreServerConfig.ANIMAL_DESPAWN_PROTECTION.set(previous);
            JSMoreServerConfig.ANIMAL_DESPAWN_PROTECTION.clearCache();
            helper.getLevel().removePlayerImmediately(player, net.minecraft.world.entity.Entity.RemovalReason.DISCARDED); player.discard();
        }
        helper.succeed();
    }
}
