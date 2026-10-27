package com.palos.jsmore.network;

import com.palos.jsmore.JSMore;
import com.palos.jsmore.server.registry.JSMoreItems;
import java.util.UUID;
import jp.jurassicsaga.server.entity.animal.JSAnimals;
import jp.jurassicsaga.server.entity.animal.entity.obj.bases.JSAnimalBase;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(JSMore.MOD_ID)
@PrefixGameTestTemplate(false)
public final class AnimalObservationGameTests {
    private AnimalObservationGameTests() { }

    @GameTest(template = "profile_compatibility", timeoutTicks = 120)
    @SuppressWarnings("removal")
    public static void serverObservationRejectsWrongIdentityDimensionDistanceAndEquipment(GameTestHelper helper) {
        var player = new net.neoforged.neoforge.common.util.FakePlayer(helper.getLevel(),
                new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), "[JSMore Test]"));
        JSAnimalBase animal = (JSAnimalBase) JSAnimals.getAnimal("jurassicsaga:goat").getEntityType().get().create(helper.getLevel());
        try {
            animal.setPos(helper.absolutePos(new BlockPos(8, 4, 8)).getCenter());
            helper.getLevel().addFreshEntity(animal);
            player.setPos(animal.position().add(0, 0, 3));
            player.setItemSlot(EquipmentSlot.HEAD, new ItemStack(JSMoreItems.DINO_DOCTOR_GOGGLES.get()));
            var request = new AnimalObservationRequestPayload(animal.getId(), animal.getUUID(), helper.getLevel().dimension().location(), UUID.randomUUID());
            helper.assertTrue(!AnimalObservationRequestPayload.resolveResponse(player, request).snapshotTag().isEmpty(), "Valid server observation unavailable");
            var wrongIdentity = new AnimalObservationRequestPayload(animal.getId(), UUID.randomUUID(), request.dimension(), request.requestId());
            helper.assertTrue(AnimalObservationRequestPayload.resolveResponse(player, wrongIdentity).snapshotTag().isEmpty(), "Recycled entity ID accepted");
            var wrongDimension = new AnimalObservationRequestPayload(animal.getId(), animal.getUUID(), ResourceLocation.withDefaultNamespace("the_nether"), request.requestId());
            helper.assertTrue(AnimalObservationRequestPayload.resolveResponse(player, wrongDimension).snapshotTag().isEmpty(), "Wrong dimension accepted");
            player.setPos(animal.position().add(100, 0, 0));
            helper.assertTrue(AnimalObservationRequestPayload.resolveResponse(player, request).snapshotTag().isEmpty(), "Remote observation accepted");
            player.setPos(animal.position().add(0, 0, 3)); player.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
            helper.assertTrue(AnimalObservationRequestPayload.resolveResponse(player, request).snapshotTag().isEmpty(), "Observation without goggles accepted");
        } finally {
            animal.discard();
            helper.getLevel().removePlayerImmediately(player, net.minecraft.world.entity.Entity.RemovalReason.DISCARDED);
            player.discard();
        }
        helper.succeed();
    }
}
