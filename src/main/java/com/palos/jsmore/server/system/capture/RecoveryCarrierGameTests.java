package com.palos.jsmore.server.system.capture;

import com.palos.jsmore.JSMore;
import com.palos.jsmore.server.registry.JSMoreItems;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

@GameTestHolder(JSMore.MOD_ID)
@PrefixGameTestTemplate(false)
public final class RecoveryCarrierGameTests {
    private RecoveryCarrierGameTests() {
    }

    @GameTest(template = "profile_compatibility", timeoutTicks = 120)
    public static void validCarrierKeepsLifetimeProtectionAcrossHazardsReloadAndRedrop(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Vec3 initial = Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(12, 3, 10))).add(0.0D, 0.125D, 0.0D);
        ItemStack stack = validRecoveryStack();
        ItemEntity carrier = new ItemEntity(level, initial.x, initial.y, initial.z, stack);
        CompoundTag aged = new CompoundTag();
        carrier.saveWithoutId(aged);
        aged.putShort("Age", (short) 6001);
        carrier.load(aged);
        carrier.setDeltaMovement(1.0D, -1.0D, 0.5D);
        carrier.setRemainingFireTicks(400);

        if (!RecoveryCarrierService.prepareForSpawn(carrier, level, initial)
                || !level.addFreshEntity(carrier)
                || !RecoveryCarrierService.onEntityJoin(carrier, level)
                || carrier.getAge() >= 0
                || !carrier.isInvulnerable()
                || !carrier.isNoGravity()
                || !carrier.isCurrentlyGlowing()
                || carrier.isOnFire()
                || !Vec3.ZERO.equals(carrier.getDeltaMovement())) {
            helper.fail("Valid recovery carrier did not receive idempotent lifetime/visual protection");
            return;
        }
        carrier.hurt(level.damageSources().cactus(), Float.MAX_VALUE);
        carrier.hurt(level.damageSources().lava(), Float.MAX_VALUE);
        carrier.hurt(level.damageSources().explosion((Entity) null, (Entity) null), Float.MAX_VALUE);
        if (carrier.isRemoved()) {
            helper.fail("Protected recovery carrier was destroyed by environmental damage");
            return;
        }

        BlockPos lavaPos = helper.absolutePos(new BlockPos(15, 3, 10));
        level.setBlockAndUpdate(lavaPos, Blocks.LAVA.defaultBlockState());
        carrier.setPos(lavaPos.getX() + 0.5D, lavaPos.getY() + 0.125D, lavaPos.getZ() + 0.5D);
        RecoveryCarrierService.onEntityJoin(carrier, level);
        if (!RecoveryCarrierService.isSafePosition(level, carrier.position())) {
            helper.fail("Protected recovery carrier did not leave lava for a bounded safe candidate");
            return;
        }

        carrier.setPos(initial.x, level.getMinBuildHeight() - 8.0D, initial.z);
        RecoveryCarrierService.maintain(carrier, level);
        if (!RecoveryCarrierService.isSafePosition(level, carrier.position())) {
            helper.fail("Protected recovery carrier did not return from below the world to a safe position");
            return;
        }

        CompoundTag saved = new CompoundTag();
        carrier.saveWithoutId(saved);
        ItemStack expectedStack = carrier.getItem().copy();
        carrier.discard();
        ItemEntity reloaded = new ItemEntity(level, initial.x, initial.y, initial.z, ItemStack.EMPTY);
        reloaded.load(saved);
        reloaded.setPos(initial.x, initial.y, initial.z);
        if (!level.addFreshEntity(reloaded)
                || !RecoveryCarrierService.onEntityJoin(reloaded, level)
                || !CaptureBoxAuthority.isProtectedRecoveryCarrier(reloaded.getItem())
                || !expectedStack.getComponents().equals(reloaded.getItem().getComponents())) {
            helper.fail("Reloaded recovery carrier lost its exact marker or lifecycle protection");
            return;
        }

        ItemEntity redropped = new ItemEntity(level, initial.x + 1.0D, initial.y, initial.z, reloaded.getItem().copy());
        if (!level.addFreshEntity(redropped)
                || !RecoveryCarrierService.onEntityJoin(redropped, level)
                || !CaptureBoxAuthority.isProtectedRecoveryCarrier(redropped.getItem())) {
            helper.fail("Picked-up/container recovery stack did not regain protection when re-dropped");
            return;
        }
        reloaded.discard();
        redropped.discard();
        helper.succeed();
    }

    @GameTest(template = "profile_compatibility", timeoutTicks = 100)
    public static void invalidMarkerNeverReceivesImmortality(GameTestHelper helper) {
        Vec3 position = Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(8, 3, 8))).add(0.0D, 0.125D, 0.0D);
        ItemStack invalid = new ItemStack(JSMoreItems.DINOSAUR_CAPTURE_CAGE.get());
        CustomData.update(
                DataComponents.CUSTOM_DATA,
                invalid,
                tag -> tag.put("JSMoreRelocationRecovery", StringTag.valueOf("forged"))
        );
        ItemEntity carrier = new ItemEntity(helper.getLevel(), position.x, position.y, position.z, invalid);

        if (RecoveryCarrierService.onEntityJoin(carrier, helper.getLevel())
                || CaptureBoxAuthority.isProtectedRecoveryCarrier(invalid)
                || !CaptureBoxAuthority.hasRecoveryMarker(invalid)
                || carrier.isInvulnerable()
                || carrier.isNoGravity()
                || carrier.isCurrentlyGlowing()) {
            helper.fail("Invalid recovery marker received protected entity lifecycle");
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "profile_compatibility", timeoutTicks = 100)
    public static void safeSearchFailsClosedWhenEveryBoundedCandidateIsBlocked(GameTestHelper helper) {
        BlockPos center = helper.absolutePos(new BlockPos(12, 4, 10));
        for (int y = -2; y <= 2; y++) {
            for (int x = -4; x <= 4; x++) {
                for (int z = -4; z <= 4; z++) {
                    if (x * x + z * z <= 16) {
                        helper.getLevel().setBlockAndUpdate(center.offset(x, y, z), Blocks.STONE.defaultBlockState());
                    }
                }
            }
        }
        Vec3 requested = Vec3.atBottomCenterOf(center).add(0.0D, 0.125D, 0.0D);
        if (RecoveryCarrierService.findSafePosition(helper.getLevel(), requested).isPresent()) {
            helper.fail("Bounded recovery search escaped its radius or accepted an obstructed candidate");
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "profile_compatibility", timeoutTicks = 100)
    public static void recoveryTickPathAvoidsRepeatedDeepMetadataInspection(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Vec3 position = Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(8, 3, 8)))
                .add(0.0D, 0.125D, 0.0D);
        RecoveryCarrierService.resetValidationStateForTests();

        ItemEntity ordinaryItem = new ItemEntity(level, position.x, position.y, position.z,
                new ItemStack(net.minecraft.world.item.Items.STICK));
        ItemEntity ordinaryCage = new ItemEntity(level, position.x, position.y, position.z,
                new ItemStack(JSMoreItems.DINOSAUR_CAPTURE_CAGE.get()));
        for (int tick = 0; tick < 20; tick++) {
            ordinaryItem.tickCount = tick;
            ordinaryCage.tickCount = tick;
            if (RecoveryCarrierService.maintain(ordinaryItem, level)
                    || RecoveryCarrierService.maintain(ordinaryCage, level)) {
                helper.fail("Ordinary item without a marker entered recovery protection");
                return;
            }
        }
        if (CaptureBoxAuthority.recoveryDeepInspectionCountForTests() != 0) {
            helper.fail("Ordinary item tick path performed a deep recovery metadata inspection");
            return;
        }

        ItemEntity valid = new ItemEntity(level, position.x, position.y, position.z, validRecoveryStack());
        valid.tickCount = 0;
        if (!RecoveryCarrierService.onEntityJoin(valid, level)) {
            helper.fail("Valid recovery carrier was rejected on join");
            return;
        }
        CaptureBoxAuthority.resetRecoveryDeepInspectionCountForTests();
        for (int tick = 1; tick < 20; tick++) {
            valid.tickCount = tick;
            valid.setInvulnerable(false);
            valid.setNoGravity(false);
            valid.setGlowingTag(false);
            if (!RecoveryCarrierService.maintain(valid, level)
                    || !valid.isInvulnerable()
                    || !valid.isNoGravity()
                    || !valid.isCurrentlyGlowing()) {
                helper.fail("Cached valid recovery carrier did not reapply constant-time protection");
                return;
            }
        }
        if (CaptureBoxAuthority.recoveryDeepInspectionCountForTests() != 0) {
            helper.fail("Valid recovery carrier was deeply inspected before its 20-tick interval");
            return;
        }
        valid.tickCount = 20;
        if (!RecoveryCarrierService.maintain(valid, level)
                || CaptureBoxAuthority.recoveryDeepInspectionCountForTests() != 1) {
            helper.fail("Valid recovery carrier did not perform exactly one periodic deep inspection");
            return;
        }

        CustomData.update(DataComponents.CUSTOM_DATA, valid.getItem(),
                tag -> tag.remove("JSMoreRelocationRecovery"));
        valid.tickCount = 21;
        if (RecoveryCarrierService.maintain(valid, level)
                || valid.isInvulnerable()
                || valid.isNoGravity()
                || valid.isCurrentlyGlowing()
                || valid.getAge() == Short.MIN_VALUE) {
            helper.fail("Removing the marker did not immediately revoke cached recovery protection");
            return;
        }

        ItemStack invalidStack = new ItemStack(JSMoreItems.DINOSAUR_CAPTURE_CAGE.get());
        CustomData.update(DataComponents.CUSTOM_DATA, invalidStack,
                tag -> tag.put("JSMoreRelocationRecovery", StringTag.valueOf("forged")));
        ItemEntity invalid = new ItemEntity(level, position.x, position.y, position.z, invalidStack);
        if (RecoveryCarrierService.onEntityJoin(invalid, level)
                || invalid.isInvulnerable()
                || invalid.isNoGravity()
                || invalid.isCurrentlyGlowing()) {
            helper.fail("Invalid marker received recovery protection");
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "profile_compatibility", timeoutTicks = 100)
    public static void unsafeLocalSearchFallsBackToSharedSpawnWithoutDuplicatingCarrier(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Vec3 spawnRequested = Vec3.atBottomCenterOf(level.getSharedSpawnPos())
                .add(0.0D, 0.125D, 0.0D);
        Vec3 spawnSafe = RecoveryCarrierService.findSafePosition(level, spawnRequested).orElse(null);
        if (spawnSafe == null) {
            helper.fail("GameTest shared spawn did not expose a loaded safe recovery position");
            return;
        }

        BlockPos center = helper.absolutePos(new BlockPos(12, 4, 10));
        for (int y = -2; y <= 2; y++) {
            for (int x = -4; x <= 4; x++) {
                for (int z = -4; z <= 4; z++) {
                    if (x * x + z * z <= 16) {
                        level.setBlockAndUpdate(center.offset(x, y, z), Blocks.STONE.defaultBlockState());
                    }
                }
            }
        }
        level.setBlockAndUpdate(center, Blocks.LAVA.defaultBlockState());
        Vec3 requested = Vec3.atBottomCenterOf(center).add(0.0D, 0.125D, 0.0D);
        if (RecoveryCarrierService.findSafePosition(level, requested).isPresent()) {
            helper.fail("Local recovery search fixture still has a safe candidate");
            return;
        }

        ItemEntity carrier = new ItemEntity(level, spawnSafe.x, spawnSafe.y, spawnSafe.z, validRecoveryStack());
        if (!level.addFreshEntity(carrier) || !RecoveryCarrierService.onEntityJoin(carrier, level)) {
            helper.fail("Could not initialize the sole recovery carrier");
            return;
        }
        carrier.setPos(requested.x, requested.y, requested.z);
        carrier.tickCount = 20;
        if (!RecoveryCarrierService.maintain(carrier, level)
                || !spawnSafe.equals(carrier.position())
                || level.getEntity(carrier.getUUID()) != carrier
                || !CaptureBoxAuthority.isProtectedRecoveryCarrier(carrier.getItem())) {
            helper.fail("Unsafe local carrier did not move to shared-spawn safety as one authority");
            return;
        }
        carrier.discard();
        helper.succeed();
    }

    @GameTest(template = "profile_compatibility", timeoutTicks = 100)
    public static void preTickRescuesBelowWorldCarrierBeforeVanillaDiscard(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Vec3 initial = Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(6, 3, 6)))
                .add(0.0D, 0.125D, 0.0D);
        ItemEntity carrier = new ItemEntity(level, initial.x, initial.y, initial.z, new ItemStack(Items.STICK));
        if (!level.addFreshEntity(carrier)) {
            helper.fail("Could not add recovery pre-tick fixture");
            return;
        }
        ItemStack expectedStack = validRecoveryStack("pre-tick-" + carrier.getUUID());
        UUID expectedUuid = carrier.getUUID();
        carrier.setItem(expectedStack.copy());
        RecoveryCarrierService.resetValidationStateForTests();
        carrier.tickCount = 0;
        carrier.setPos(initial.x, level.getMinBuildHeight() - 65.0D, initial.z);

        runFormalEntityTick(carrier);

        int deepInspections = CaptureBoxAuthority.recoveryDeepInspectionCountForTests();
        if (carrier.isRemoved()
                || level.getEntity(expectedUuid) != carrier
                || carrier.level() != level
                || carrier.getY() < level.getMinBuildHeight()
                || !RecoveryCarrierService.isSafePosition(level, carrier.position())
                || !expectedStack.getComponents().equals(carrier.getItem().getComponents())
                || deepInspections != 1
                || countMatchingRecoveryAuthorities(level, expectedStack) != 1) {
            helper.fail("Pre -> ItemEntity.tick -> Post did not preserve one verified recovery authority; deep="
                    + deepInspections);
            return;
        }
        carrier.discard();
        helper.succeed();
    }

    @GameTest(template = "profile_compatibility", timeoutTicks = 100)
    public static void preTickRejectsOrdinaryItemsCagesAndForgedMarkers(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Vec3 initial = Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(7, 3, 7)))
                .add(0.0D, 0.125D, 0.0D);
        ItemEntity ordinary = new ItemEntity(level, initial.x, initial.y, initial.z, new ItemStack(Items.STICK));
        ItemEntity cage = new ItemEntity(
                level,
                initial.x,
                initial.y,
                initial.z,
                new ItemStack(JSMoreItems.DINOSAUR_CAPTURE_CAGE.get())
        );
        ItemEntity forged = new ItemEntity(level, initial.x, initial.y, initial.z, new ItemStack(Items.STICK));
        if (!level.addFreshEntity(ordinary) || !level.addFreshEntity(cage) || !level.addFreshEntity(forged)) {
            helper.fail("Could not add ordinary pre-tick fixtures");
            return;
        }
        ItemStack forgedStack = new ItemStack(JSMoreItems.DINOSAUR_CAPTURE_CAGE.get());
        CustomData.update(
                DataComponents.CUSTOM_DATA,
                forgedStack,
                tag -> tag.put("JSMoreRelocationRecovery", StringTag.valueOf("forged"))
        );
        forged.setItem(forgedStack);
        RecoveryCarrierService.resetValidationStateForTests();
        for (ItemEntity item : new ItemEntity[]{ordinary, cage, forged}) {
            item.tickCount = 0;
            item.setPos(initial.x, level.getMinBuildHeight() - 65.0D, initial.z);
            DinosaurCaptureTickHandler.onEntityTickPre(new EntityTickEvent.Pre(item));
            if (item.isInvulnerable() || item.isNoGravity() || item.isCurrentlyGlowing()) {
                helper.fail("Non-valid recovery candidate received pre-tick protection");
                return;
            }
        }
        if (CaptureBoxAuthority.recoveryDeepInspectionCountForTests() != 1) {
            helper.fail("Pre-tick prefilter did not limit deep validation to the forged marker");
            return;
        }
        for (ItemEntity item : new ItemEntity[]{ordinary, cage, forged}) {
            item.tick();
            DinosaurCaptureTickHandler.onEntityTickPost(new EntityTickEvent.Post(item));
            if (!item.isRemoved()) {
                helper.fail("Non-valid recovery candidate bypassed vanilla below-world removal");
                return;
            }
        }
        if (CaptureBoxAuthority.recoveryDeepInspectionCountForTests() != 1) {
            helper.fail("Pre and Post repeated deep validation for an invalid marker in one tick");
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "profile_compatibility", timeoutTicks = 100)
    public static void preTickUsesSameDimensionEmergencyLandingWhenNormalSearchesFail(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos originalSpawn = level.getSharedSpawnPos();
        float originalSpawnAngle = level.getSharedSpawnAngle();
        BlockPos blockedSpawn = helper.absolutePos(new BlockPos(12, 4, 10));
        ItemEntity carrier = null;
        try {
            for (int y = -2; y <= 2; y++) {
                for (int x = -4; x <= 4; x++) {
                    for (int z = -4; z <= 4; z++) {
                        if (x * x + z * z <= 16) {
                            level.setBlockAndUpdate(blockedSpawn.offset(x, y, z), Blocks.STONE.defaultBlockState());
                        }
                    }
                }
            }
            level.setDefaultSpawnPos(blockedSpawn, 0.0F);
            Vec3 spawnRequested = Vec3.atBottomCenterOf(blockedSpawn).add(0.0D, 0.125D, 0.0D);
            Vec3 belowWorld = new Vec3(
                    blockedSpawn.getX() + 0.5D,
                    level.getMinBuildHeight() - 65.0D,
                    blockedSpawn.getZ() + 0.5D
            );
            if (RecoveryCarrierService.findSafePosition(level, belowWorld).isPresent()
                    || RecoveryCarrierService.findSafePosition(level, spawnRequested).isPresent()) {
                helper.fail("Emergency fixture still exposed a normal local or shared-spawn candidate");
                return;
            }

            carrier = new ItemEntity(level, spawnRequested.x, spawnRequested.y, spawnRequested.z,
                    new ItemStack(Items.STICK));
            if (!level.addFreshEntity(carrier)) {
                helper.fail("Could not add emergency recovery fixture");
                return;
            }
            ItemStack expectedStack = validRecoveryStack("emergency-" + carrier.getUUID());
            UUID expectedUuid = carrier.getUUID();
            carrier.setItem(expectedStack.copy());
            RecoveryCarrierService.resetValidationStateForTests();
            carrier.tickCount = 0;
            carrier.setPos(belowWorld.x, belowWorld.y, belowWorld.z);

            runFormalEntityTick(carrier);

            int deepInspections = CaptureBoxAuthority.recoveryDeepInspectionCountForTests();
            boolean safePosition = RecoveryCarrierService.isSafePosition(level, carrier.position());
            boolean stackPreserved = expectedStack.getComponents().equals(carrier.getItem().getComponents());
            int matchingAuthorities = countMatchingRecoveryAuthorities(level, expectedStack);
            if (carrier.isRemoved()
                    || carrier.level() != level
                    || level.getEntity(expectedUuid) != carrier
                    || carrier.getY() < level.getMinBuildHeight()
                    || carrier.getY() >= level.getMaxBuildHeight()
                    || !safePosition
                    || !carrier.isInvulnerable()
                    || !carrier.isNoGravity()
                    || !stackPreserved
                    || deepInspections != 1
                    || matchingAuthorities != 1) {
                helper.fail("Emergency landing did not preserve one same-dimension recovery authority; deep="
                        + deepInspections
                        + ", removed=" + carrier.isRemoved()
                        + ", level=" + (carrier.level() == level)
                        + ", indexed=" + (level.getEntity(expectedUuid) == carrier)
                        + ", pos=" + carrier.position()
                        + ", safe=" + safePosition
                        + ", invulnerable=" + carrier.isInvulnerable()
                        + ", noGravity=" + carrier.isNoGravity()
                        + ", stack=" + stackPreserved
                        + ", authorities=" + matchingAuthorities);
                return;
            }
            helper.succeed();
        } finally {
            level.setDefaultSpawnPos(originalSpawn, originalSpawnAngle);
            if (carrier != null) {
                carrier.discard();
            }
        }
    }

    private static void runFormalEntityTick(ItemEntity carrier) {
        DinosaurCaptureTickHandler.onEntityTickPre(new EntityTickEvent.Pre(carrier));
        carrier.tick();
        DinosaurCaptureTickHandler.onEntityTickPost(new EntityTickEvent.Post(carrier));
    }

    private static int countMatchingRecoveryAuthorities(ServerLevel level, ItemStack expectedStack) {
        int count = 0;
        for (Entity entity : level.getAllEntities()) {
            if (entity instanceof ItemEntity item
                    && !item.isRemoved()
                    && expectedStack.getComponents().equals(item.getItem().getComponents())) {
                count++;
            }
        }
        return count;
    }

    private static ItemStack validRecoveryStack() {
        return validRecoveryStack("preserve");
    }

    private static ItemStack validRecoveryStack(String opaqueValue) {
        ItemStack stack = new ItemStack(JSMoreItems.DINOSAUR_CAPTURE_CAGE.get());
        CompoundTag metadata = new CompoundTag();
        metadata.putString("id", "jsmore:dinosaur_capture_box");
        metadata.putInt("x", 1);
        metadata.putInt("y", 2);
        metadata.putInt("z", 3);
        CompoundTag future = new CompoundTag();
        future.putString("OpaqueField", opaqueValue);
        metadata.put("FutureRecoveryData", future);
        CustomData.update(
                DataComponents.CUSTOM_DATA,
                stack,
                tag -> tag.put("JSMoreRelocationRecovery", metadata)
        );
        return stack;
    }
}
