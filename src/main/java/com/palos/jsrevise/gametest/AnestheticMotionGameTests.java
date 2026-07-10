package com.palos.jsrevise.gametest;

import com.palos.jsrevise.JSRevise;
import com.palos.jsrevise.server.registry.JSReviseAttachments;
import com.palos.jsrevise.server.system.anesthetic.AnestheticFloatData;
import com.palos.jsrevise.server.system.anesthetic.DinosaurAnestheticSystem;
import jp.jurassicsaga.server.animal.JSAnimals;
import jp.jurassicsaga.server.animal.animals.obj.JSAnimal;
import jp.jurassicsaga.server.animal.entity.obj.bases.JSAnimalBase;
import jp.jurassicsaga.server.animal.entity.obj.bases.JSAquaticBase;
import jp.jurassicsaga.server.animal.entity.obj.bases.JSAvianBase;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(JSRevise.MOD_ID)
@PrefixGameTestTemplate(false)
public final class AnestheticMotionGameTests {
    private AnestheticMotionGameTests() {
    }

    @GameTest(template = "profile_compatibility", timeoutTicks = 200)
    public static void floatingRiseRespectsSolidCollision(GameTestHelper helper) {
        JSAnimalBase animal = createSmallTerrestrialAnimal(helper);
        if (animal == null) {
            helper.fail("Jurassic Saga registered no small terrestrial animal for collision testing");
            return;
        }
        BlockPos base = helper.absolutePos(new BlockPos(5, 3, 5));
        int ceilingY = base.getY() + 3;
        fillWaterColumnWithCeiling(helper, base, ceilingY);
        animal.setPos(base.getX() + 0.5D, base.getY(), base.getZ() + 0.5D);
        animal.setPersistenceRequired();
        if (!helper.getLevel().addFreshEntity(animal)) {
            helper.fail("Could not add the collision-test animal to the GameTest level");
            return;
        }
        double startY = animal.getY();
        long startTick = helper.getTick();
        String[] terminalFailure = {null};
        DinosaurAnestheticSystem.applyAnesthetic(animal);

        helper.succeedWhen(() -> {
            if (terminalFailure[0] == null && animal.isRemoved()) {
                terminalFailure[0] = "Collision-test animal was removed before the assertion completed";
            }
            if (terminalFailure[0] == null && animal.getBoundingBox().maxY > ceilingY + 1.0E-4D) {
                terminalFailure[0] = "Collision-aware floating movement crossed the solid ceiling";
            }
            if (terminalFailure[0] == null && !helper.getLevel().noCollision(animal)) {
                terminalFailure[0] = "Floating movement left the animal intersecting a solid block";
            }
            if (terminalFailure[0] != null) {
                helper.fail(terminalFailure[0]);
            }
            if (helper.getTick() - startTick < 20L || animal.getY() <= startY + 0.20D) {
                helper.fail("Anesthetized animal has not risen toward the water surface yet");
            }
        });
    }

    @GameTest(template = "profile_compatibility", timeoutTicks = 200)
    public static void lavaDoesNotCountAsFloatingWater(GameTestHelper helper) {
        JSAnimalBase animal = createSmallTerrestrialAnimal(helper);
        if (animal == null) {
            helper.fail("Jurassic Saga registered no small terrestrial animal for fluid-tag testing");
            return;
        }
        BlockPos base = helper.absolutePos(new BlockPos(5, 3, 5));
        for (BlockPos pos : BlockPos.betweenClosed(base.offset(-2, -1, -2), base.offset(2, 4, 2))) {
            helper.getLevel().setBlock(pos, Blocks.LAVA.defaultBlockState(), Block.UPDATE_ALL);
        }
        animal.setPos(base.getX() + 0.5D, base.getY(), base.getZ() + 0.5D);
        animal.setInvulnerable(true);
        animal.setPersistenceRequired();
        if (!helper.getLevel().addFreshEntity(animal)) {
            helper.fail("Could not add the lava-test animal to the GameTest level");
            return;
        }
        long startTick = helper.getTick();
        String[] terminalFailure = {null};
        DinosaurAnestheticSystem.applyAnesthetic(animal);

        helper.succeedWhen(() -> {
            if (terminalFailure[0] == null && animal.isRemoved()) {
                terminalFailure[0] = "Lava-test animal was removed before the assertion completed";
            }
            if (terminalFailure[0] == null) {
                AnestheticFloatData floatData = animal.getData(JSReviseAttachments.ANESTHETIC_FLOAT);
                if (floatData.isFloating() || animal.isNoGravity()) {
                    terminalFailure[0] = "A non-water fluid incorrectly activated anesthetic floating";
                }
            }
            if (terminalFailure[0] != null) {
                helper.fail(terminalFailure[0]);
            }
            if (helper.getTick() - startTick < 20L) {
                helper.fail("Waiting for twenty real lava-test ticks");
            }
        });
    }

    private static void fillWaterColumnWithCeiling(GameTestHelper helper, BlockPos base, int ceilingY) {
        for (BlockPos pos : BlockPos.betweenClosed(base.offset(-2, -1, -2), base.offset(2, 6, 2))) {
            helper.getLevel().setBlock(
                    pos,
                    pos.getY() == ceilingY ? Blocks.STONE.defaultBlockState() : Blocks.WATER.defaultBlockState(),
                    Block.UPDATE_ALL
            );
        }
    }

    private static JSAnimalBase createSmallTerrestrialAnimal(GameTestHelper helper) {
        for (JSAnimal<?> registeredAnimal : JSAnimals.getAnimals()) {
            Entity entity = registeredAnimal.getEntityType().get().create(helper.getLevel());
            if (entity instanceof JSAnimalBase animal
                    && !(animal instanceof JSAquaticBase)
                    && !(animal instanceof JSAvianBase)
                    && animal.getBbWidth() <= 2.0F
                    && animal.getBbHeight() <= 2.0F) {
                return animal;
            }
            if (entity != null) {
                entity.discard();
            }
        }
        return null;
    }
}
