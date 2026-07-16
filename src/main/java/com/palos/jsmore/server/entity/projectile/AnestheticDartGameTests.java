package com.palos.jsmore.server.entity.projectile;

import com.palos.jsmore.JSMore;
import com.palos.jsmore.server.registry.JSMoreAttachments;
import com.palos.jsmore.server.registry.JSMoreEntityTypes;
import com.palos.jsmore.server.registry.JSMoreItems;
import jp.jurassicsaga.server.animal.JSAnimals;
import jp.jurassicsaga.server.animal.animals.obj.JSAnimal;
import jp.jurassicsaga.server.animal.entity.obj.bases.JSAnimalBase;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(JSMore.MOD_ID)
@PrefixGameTestTemplate(false)
public final class AnestheticDartGameTests {
    private AnestheticDartGameTests() {
    }

    @GameTest(template = "profile_compatibility", timeoutTicks = 200)
    public static void entityHitsNeverLeaveAnEmbeddedDart(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        Cow cow = EntityType.COW.create(helper.getLevel());
        if (cow == null) {
            helper.fail("Could not create vanilla invalid-target entity");
            return;
        }
        cow.setPos(helper.absolutePos(new BlockPos(4, 2, 4)).getCenter());
        helper.getLevel().addFreshEntity(cow);

        AnestheticDartEntity mobFiredDart = new AnestheticDartEntity(
                helper.getLevel(),
                cow,
                new ItemStack(JSMoreItems.ANESTHETIC_DART.get())
        );
        if (mobFiredDart.pickup != AbstractArrow.Pickup.DISALLOWED) {
            helper.fail("Non-player-fired dart did not retain vanilla disallowed pickup semantics");
            return;
        }

        AnestheticDartEntity invalidTargetDart = createDart(helper, player);
        invalidTargetDart.onHitEntity(new EntityHitResult(cow, cow.getBoundingBox().getCenter()));
        if (!invalidTargetDart.isRemoved()) {
            helper.fail("Dart remained after hitting a non-anesthetizable entity");
            return;
        }

        JSAnimalBase animal = createTestAnimal(helper);
        if (animal == null) {
            helper.fail("Jurassic Saga registered no JSAnimalBase for dart testing");
            return;
        }
        AnestheticDartEntity successfulDart = createDart(helper, player);
        float healthBefore = animal.getHealth();
        successfulDart.onHitEntity(new EntityHitResult(animal, animal.getBoundingBox().getCenter()));
        if (!successfulDart.isRemoved()) {
            helper.fail("Dart remained on an entity after a successful anesthetic hit");
            return;
        }
        if (Math.abs(animal.getHealth() - (healthBefore - 1.0F)) > 1.0E-4F) {
            helper.fail("Successful anesthetic dart did not deal exactly one damage");
            return;
        }
        if (!animal.hasData(JSMoreAttachments.ANESTHETIC)) {
            helper.fail("Successful anesthetic dart did not queue anesthetic data");
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "profile_compatibility", timeoutTicks = 200)
    public static void blockHitUsesVanillaInGroundNbtAndCanBePickedUp(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        BlockPos relativeBlockPos = new BlockPos(3, 2, 3);
        helper.setBlock(relativeBlockPos, Blocks.STONE);
        BlockPos blockPos = helper.absolutePos(relativeBlockPos);
        Vec3 hitLocation = Vec3.atCenterOf(blockPos).add(0.0D, 0.5D, 0.0D);

        AnestheticDartEntity dart = createDart(helper, player);
        dart.setPos(hitLocation.subtract(0.0D, 0.05D, 0.0D));
        dart.setDeltaMovement(0.0D, -1.0D, 0.0D);
        dart.onHitBlock(new BlockHitResult(hitLocation, Direction.UP, blockPos, false));
        if (dart.isRemoved()) {
            helper.fail("Dart disappeared instead of remaining in the hit block");
            return;
        }
        if (dart.pickup != AbstractArrow.Pickup.ALLOWED) {
            helper.fail("Survival-fired dart was not pickup-enabled");
            return;
        }

        CompoundTag saved = dart.saveWithoutId(new CompoundTag());
        if (!saved.getBoolean("inGround") || saved.contains("DartEmbedded")) {
            helper.fail("Dart did not use vanilla in-ground NBT exclusively");
            return;
        }
        AnestheticDartEntity loaded = new AnestheticDartEntity(
                JSMoreEntityTypes.ANESTHETIC_DART.get(),
                helper.getLevel()
        );
        loaded.load(saved);
        if (loaded.pickup != AbstractArrow.Pickup.ALLOWED
                || !loaded.getPickupItemStackOrigin().is(JSMoreItems.ANESTHETIC_DART.get())) {
            helper.fail("Vanilla arrow NBT did not preserve dart pickup state and item");
            return;
        }

        int before = player.getInventory().countItem(JSMoreItems.ANESTHETIC_DART.get());
        dart.shakeTime = 0;
        dart.playerTouch(player);
        int after = player.getInventory().countItem(JSMoreItems.ANESTHETIC_DART.get());
        if (!dart.isRemoved() || after != before + 1) {
            helper.fail("Survival player could not pick the in-ground dart back up");
            return;
        }

        AnestheticDartEntity creativeDart = new AnestheticDartEntity(
                helper.getLevel(),
                helper.makeMockPlayer(GameType.CREATIVE),
                new ItemStack(JSMoreItems.ANESTHETIC_DART.get())
        );
        if (creativeDart.pickup != AbstractArrow.Pickup.CREATIVE_ONLY) {
            helper.fail("Creative-fired dart did not use vanilla creative-only pickup semantics");
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "profile_compatibility", timeoutTicks = 200)
    public static void inGroundDartUsesVanilla1200TickLifetime(GameTestHelper helper) {
        BlockPos relativeBlockPos = new BlockPos(3, 2, 3);
        helper.setBlock(relativeBlockPos, Blocks.STONE);
        BlockPos blockPos = helper.absolutePos(relativeBlockPos);
        Vec3 hitLocation = Vec3.atCenterOf(blockPos).add(0.0D, 0.5D, 0.0D);

        AnestheticDartEntity seed = createDart(helper, helper.makeMockPlayer(GameType.SURVIVAL));
        seed.setPos(hitLocation.subtract(0.0D, 0.05D, 0.0D));
        seed.setDeltaMovement(0.0D, -1.0D, 0.0D);
        seed.onHitBlock(new BlockHitResult(hitLocation, Direction.UP, blockPos, false));
        CompoundTag saved = seed.saveWithoutId(new CompoundTag());
        seed.discard();
        saved.putShort("life", (short) 1198);

        AnestheticDartEntity loaded = new AnestheticDartEntity(
                JSMoreEntityTypes.ANESTHETIC_DART.get(),
                helper.getLevel()
        );
        loaded.load(saved);
        if (!helper.getLevel().addFreshEntity(loaded) || loaded.isRemoved()) {
            helper.fail("Could not load an in-ground dart at vanilla life tick 1198");
            return;
        }
        helper.runAfterDelay(1L, () -> {
            if (loaded.isRemoved()) {
                helper.fail("In-ground dart was removed at vanilla life tick 1199");
            }
        });
        helper.runAfterDelay(2L, () -> {
            if (!loaded.isRemoved()) {
                helper.fail("In-ground dart survived vanilla arrow life tick 1200");
                return;
            }
            helper.succeed();
        });
    }

    private static AnestheticDartEntity createDart(GameTestHelper helper, Player owner) {
        AnestheticDartEntity dart = new AnestheticDartEntity(
                helper.getLevel(),
                owner,
                new ItemStack(JSMoreItems.ANESTHETIC_DART.get())
        );
        dart.setPos(owner.getX(), owner.getEyeY(), owner.getZ());
        helper.getLevel().addFreshEntity(dart);
        return dart;
    }

    private static JSAnimalBase createTestAnimal(GameTestHelper helper) {
        for (JSAnimal<?> registeredAnimal : JSAnimals.getAnimals()) {
            Entity entity = registeredAnimal.getEntityType().get().create(helper.getLevel());
            if (entity instanceof JSAnimalBase animal && animal.getMaxHealth() > 2.0F) {
                animal.setPos(helper.absolutePos(new BlockPos(5, 2, 5)).getCenter());
                animal.setNoAi(true);
                animal.setPersistenceRequired();
                if (helper.getLevel().addFreshEntity(animal)) {
                    return animal;
                }
            }
            if (entity != null) {
                entity.discard();
            }
        }
        return null;
    }
}
