package com.palos.jsrevise.compat.aeronautics.linked;

import com.palos.jsrevise.compat.aeronautics.CaptureBoxRelocationState;
import com.palos.jsrevise.server.block.entity.BrokenDinosaurCaptureBoxBlockEntity;
import com.palos.jsrevise.server.block.entity.DinosaurCaptureCageBlockEntity;
import com.palos.jsrevise.server.registry.JSReviseItems;
import com.palos.jsrevise.server.system.capture.BrokenCaptureBoxDebrisData;
import com.palos.jsrevise.server.system.capture.CaptureBoxAccess;
import com.palos.jsrevise.server.system.capture.CaptureBoxAuthority;
import com.palos.jsrevise.server.system.capture.CaptureBoxStructure;
import com.palos.jsrevise.server.system.capture.CapturedDinosaurData;
import com.palos.jsrevise.server.system.capture.CapturedDinosaurVitals;
import com.palos.jsrevise.server.system.capture.DinosaurCaptureItemData;
import com.palos.jsrevise.server.system.capture.DinosaurCaptureService;
import com.palos.jsrevise.server.system.capture.DinosaurCaptureSupplies;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.physics.config.block_properties.PhysicsBlockPropertyHelper;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.simulated_team.simulated.content.blocks.physics_assembler.PhysicsAssemblerBlock;
import dev.simulated_team.simulated.util.SimAssemblyHelper;
import jp.jurassicsaga.server.animal.JSAnimals;
import jp.jurassicsaga.server.animal.animals.obj.JSAnimal;
import jp.jurassicsaga.server.animal.entity.obj.bases.JSAnimalBase;
import jp.jurassicsaga.server.animal.entity.obj.bases.JSAquaticBase;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;

/** Direct exact-profile calls kept behind {@link com.palos.jsrevise.compat.aeronautics.AeronauticsProfileGameTests}. */
public final class ExactAeronauticsGameTestBridge {
    private static final String PHYSICS_ASSEMBLER_BLOCK_ENTITY =
            "dev.simulated_team.simulated.content.blocks.physics_assembler.PhysicsAssemblerBlockEntity";

    private ExactAeronauticsGameTestBridge() {
    }

    public static void run(GameTestHelper helper, String scenario) throws Exception {
        switch (scenario) {
            case "broken-support" -> verifyBrokenSupport(helper);
            case "broken-mass" -> verifyBrokenMass(helper);
            case "isolated-broken" -> verifyIsolatedBrokenSupportRejected(helper);
            case "empty", "supplies", "valid", "unreadable", "broken" ->
                    verifyRoundTrip(helper, scenario);
            case "sublevel-break" -> verifySublevelDurabilityBreak(helper);
            case "assembler-entry" -> verifyAssemblerEntry(helper);
            case "incomplete" -> verifyIncompleteRejected(helper);
            default -> throw new IllegalArgumentException("unknown exact Aeronautics scenario " + scenario);
        }
        helper.succeed();
    }

    private static void verifyBrokenSupport(GameTestHelper helper) {
        BlockPos controller = helper.absolutePos(new BlockPos(6, 2, 6));
        if (!DinosaurCaptureService.placeBrokenCageBlocks(helper.getLevel(), controller, Direction.NORTH)) {
            throw new AssertionError("could not place canonical broken capture box");
        }
        for (Direction stickyDirection : Direction.values()) {
            BlockPos support = exteriorSupport(controller, Direction.NORTH, stickyDirection);
            BlockPos assemblerPos = support.relative(stickyDirection.getOpposite());
            BlockState assemblerState = assemblerState(stickyDirection);
            if (!helper.getLevel().setBlockAndUpdate(assemblerPos, assemblerState)) {
                throw new AssertionError("could not place formal Physics Assembler on " + stickyDirection);
            }
            try {
                if (!PhysicsAssemblerBlock.canAttach(helper.getLevel(), assemblerPos, stickyDirection)) {
                    throw new AssertionError("formal PhysicsAssemblerBlock.canAttach rejected broken support on "
                            + stickyDirection);
                }
                if (!assemblerState.canSurvive(helper.getLevel(), assemblerPos)) {
                    throw new AssertionError("formal PhysicsAssemblerBlock.canSurvive rejected broken support on "
                            + stickyDirection);
                }
            } finally {
                helper.getLevel().setBlockAndUpdate(assemblerPos, Blocks.AIR.defaultBlockState());
            }
        }
    }

    private static void verifyBrokenMass(GameTestHelper helper) {
        BlockPos controller = helper.absolutePos(new BlockPos(6, 2, 6));
        if (!DinosaurCaptureService.placeBrokenCageBlocks(helper.getLevel(), controller, Direction.NORTH)) {
            throw new AssertionError("could not place canonical broken capture box for mass verification");
        }
        double totalMass = 0.0D;
        for (CaptureBoxStructure.Placement placement : CaptureBoxStructure.placements(controller, Direction.NORTH)) {
            BlockState state = helper.getLevel().getBlockState(placement.pos());
            double mass = PhysicsBlockPropertyHelper.getMass(helper.getLevel(), placement.pos(), state);
            if (Double.compare(1.0D, mass) != 0) {
                throw new AssertionError("formal Sable mass for broken part " + placement + " was " + mass);
            }
            totalMass += mass;
        }
        if (Double.compare(CaptureBoxStructure.PART_COUNT, totalMass) != 0) {
            throw new AssertionError("formal Sable broken capture-box total mass was " + totalMass);
        }
    }

    private static void verifyIsolatedBrokenSupportRejected(GameTestHelper helper) throws Exception {
        BlockPos nominalController = helper.absolutePos(new BlockPos(6, 2, 6));
        CaptureBoxStructure.Placement isolated = CaptureBoxStructure.placements(nominalController, Direction.NORTH)
                .stream()
                .filter(placement -> !placement.isController())
                .findFirst()
                .orElseThrow();
        BlockPos partPos = isolated.pos();
        BlockState partState = CaptureBoxStructure.Kind.BROKEN.canonicalState(
                Direction.NORTH,
                isolated.offsetX(),
                isolated.offsetY(),
                isolated.offsetZ()
        );
        Direction stickyDirection = Direction.EAST;
        BlockPos assemblerPos = partPos.relative(stickyDirection.getOpposite());
        BlockState assemblerState = assemblerState(stickyDirection);
        SubLevelContainer container = SubLevelContainer.getContainer(helper.getLevel());
        if (container == null) {
            throw new AssertionError("formal Sable runtime has no server sublevel container");
        }
        Set<UUID> existingSubLevels = new HashSet<>();
        for (SubLevel subLevel : container.getAllSubLevels()) {
            existingSubLevels.add(subLevel.getUniqueId());
        }

        try {
            if (!helper.getLevel().setBlockAndUpdate(partPos, partState)
                    || !helper.getLevel().setBlockAndUpdate(assemblerPos, assemblerState)) {
                throw new AssertionError("could not place isolated broken-part assembly fixture");
            }
            if (CaptureBoxStructure.Kind.BROKEN.controller(partState)) {
                throw new AssertionError("isolated broken-part fixture unexpectedly used a controller state");
            }
            if (!PhysicsAssemblerBlock.canAttach(helper.getLevel(), assemblerPos, stickyDirection)
                    || !assemblerState.canSurvive(helper.getLevel(), assemblerPos)) {
                throw new AssertionError("formal Physics Assembler could not attach to isolated broken support");
            }

            SimAssemblyHelper.AssemblyResult result = null;
            try {
                result = SimAssemblyHelper.assembleFromSingleBlock(
                        helper.getLevel(),
                        assemblerPos,
                        partPos,
                        true,
                        true
                );
            } catch (com.simibubi.create.content.contraptions.AssemblyException expected) {
                // The canonical movement gate reports the same rejection used by the Physics Assembler UI.
            }
            if (result != null || findNewSubLevel(container, existingSubLevels) != null) {
                throw new AssertionError("formal SimAssemblyHelper moved an isolated broken capture-box part");
            }
            if (!helper.getLevel().getBlockState(partPos).equals(partState)
                    || CaptureBoxAccess.resolve(helper.getLevel(), partPos).isPresent()) {
                throw new AssertionError("rejected isolated broken part was removed or became canonical");
            }
        } finally {
            try {
                cleanupNewSubLevels(helper, container, existingSubLevels, assemblerPos);
            } finally {
                helper.getLevel().setBlockAndUpdate(assemblerPos, Blocks.AIR.defaultBlockState());
                helper.getLevel().setBlockAndUpdate(partPos, Blocks.AIR.defaultBlockState());
            }
        }
    }

    private static void verifyRoundTrip(GameTestHelper helper, String scenario) throws Exception {
        BlockPos controller = helper.absolutePos(new BlockPos(6, 2, 6));
        CaptureBoxStructure.Kind kind = "broken".equals(scenario)
                ? CaptureBoxStructure.Kind.BROKEN
                : CaptureBoxStructure.Kind.COMPLETE;
        place(helper, controller, kind, true);
        seed(helper, controller, scenario);
        CaptureBoxAccess.Resolved source = CaptureBoxAccess.resolve(helper.getLevel(), controller).orElseThrow();
        CompoundTag before = source.controllerBlockEntity()
                .saveWithFullMetadata(helper.getLevel().registryAccess())
                .copy();
        CompoundTag expected = expectedAfterRelocation(kind, before);
        ItemStack beforeMirror = "valid".equals(scenario) ? mirroredStack(source) : ItemStack.EMPTY;
        if (("valid".equals(scenario) || "unreadable".equals(scenario) || "broken".equals(scenario))
                && !before.contains("FutureFormalAeronauticsMetadata")) {
            throw new AssertionError("formal fixture lacks preserved unknown controller metadata");
        }

        SimAssemblyHelper.AssemblyResult result = null;
        BlockPos localController = null;
        try {
            result = SimAssemblyHelper.assembleFromSingleBlock(
                    helper.getLevel(),
                    controller,
                    controller,
                    true,
                    true
            );
            if (result == null) {
                throw new AssertionError("formal SimAssemblyHelper returned no sublevel for " + scenario);
            }
            localController = controller.offset(result.offset());
            if (CaptureBoxAccess.resolve(helper.getLevel(), controller).isPresent()) {
                throw new AssertionError("assembly left the source capture-box authority in the parent level");
            }
            CaptureBoxAccess.Resolved moved = CaptureBoxAccess.resolve(result.subLevel().getLevel(), localController)
                    .orElseThrow(() -> new AssertionError("assembled sublevel lacks a canonical capture box"));
            CompoundTag movedMetadata = moved.controllerBlockEntity()
                    .saveWithFullMetadata(helper.getLevel().registryAccess())
                    .copy();
            if (moved.kind() != kind
                    || !CaptureBoxAuthority.equivalentIgnoringPosition(expected, movedMetadata)) {
                throw new AssertionError("assembly changed capture-box kind, UUID, raw data, supplies, or durability");
            }
            assertAssembledSoleAuthority(helper, controller, result.subLevel(), moved, "direct " + scenario);
        } finally {
            if (result != null && localController != null) {
                disassembleAndRemove(helper, result, localController, controller);
            }
        }

        CaptureBoxAccess.Resolved restored = CaptureBoxAccess.resolve(helper.getLevel(), controller)
                .orElseThrow(() -> new AssertionError("disassembly did not restore the canonical capture box"));
        assertRestoredSoleAuthority(
                helper,
                controller,
                result.subLevel(),
                localController,
                Direction.NORTH,
                "direct " + scenario
        );
        CompoundTag after = restored.controllerBlockEntity()
                .saveWithFullMetadata(helper.getLevel().registryAccess())
                .copy();
        if (restored.kind() != kind || !CaptureBoxAuthority.equivalentIgnoringPosition(expected, after)) {
            throw new AssertionError("assembly round-trip changed capture-box authority metadata");
        }
        if ("valid".equals(scenario)
                && !beforeMirror.getComponents().equals(mirroredStack(restored).getComponents())) {
            throw new AssertionError("assembly round-trip changed standard DAMAGE/MAX_DAMAGE mirror components");
        }
    }

    private static void verifySublevelDurabilityBreak(GameTestHelper helper) throws Exception {
        BlockPos controller = helper.absolutePos(new BlockPos(6, 2, 6));
        place(helper, controller, CaptureBoxStructure.Kind.COMPLETE, true);
        DinosaurCaptureCageBlockEntity source = (DinosaurCaptureCageBlockEntity) CaptureBoxAccess
                .resolve(helper.getLevel(), controller)
                .orElseThrow()
                .controllerBlockEntity();
        CapturedDinosaurData zeroDurability = zeroDurabilityAnimalPayload(helper);
        UUID uuid = zeroDurability.originalUuid();
        if (!source.setContents(zeroDurability, DinosaurCaptureSupplies.EMPTY)) {
            throw new AssertionError("could not seed sublevel durability-break payload");
        }

        SimAssemblyHelper.AssemblyResult result = null;
        BlockPos localController = null;
        try {
            result = SimAssemblyHelper.assembleFromSingleBlock(
                    helper.getLevel(),
                    controller,
                    controller,
                    true,
                    true
            );
            if (result == null) {
                throw new AssertionError("formal SimAssemblyHelper returned no sublevel for durability break");
            }
            localController = controller.offset(result.offset());
            CaptureBoxAccess.Resolved moved = CaptureBoxAccess
                    .resolve(result.subLevel().getLevel(), localController)
                    .orElseThrow(() -> new AssertionError("sublevel durability-break fixture is not canonical"));
            if (!(moved.controllerBlockEntity() instanceof DinosaurCaptureCageBlockEntity movedCage)) {
                throw new AssertionError("sublevel durability-break fixture lacks a complete controller");
            }

            DinosaurCaptureService.settlePlacedCage(movedCage);
            if (result.subLevel().isRemoved()) {
                throw new AssertionError("standalone sublevel was marked removed during durability break");
            }
            SubLevelContainer container = SubLevelContainer.getContainer(helper.getLevel());
            if (container == null) {
                throw new AssertionError("formal Sable runtime lost its server sublevel container");
            }
            UUID subLevelId = result.subLevel().getUniqueId();
            container.processSubLevelRemovals();
            if (result.subLevel().isRemoved() || container.getSubLevel(subLevelId) != result.subLevel()) {
                throw new AssertionError("standalone durability break was removed on the container cleanup pass");
            }
            CaptureBoxAccess.Resolved broken = CaptureBoxAccess
                    .resolve(result.subLevel().getLevel(), localController)
                    .orElseThrow(() -> new AssertionError("sublevel durability break left no canonical residue"));
            if (broken.kind() != CaptureBoxStructure.Kind.BROKEN
                    || !(broken.controllerBlockEntity() instanceof BrokenDinosaurCaptureBoxBlockEntity brokenEntity)) {
                throw new AssertionError("sublevel durability break did not create a broken controller");
            }
            CompoundTag metadata = brokenEntity
                    .saveWithFullMetadata(helper.getLevel().registryAccess())
                    .copy();
            Entity released = helper.getLevel().getEntity(uuid);
            int boxAuthorities = (CaptureBoxAccess.resolve(helper.getLevel(), controller).isPresent() ? 1 : 0)
                    + (CaptureBoxAccess.resolve(result.subLevel().getLevel(), localController).isPresent() ? 1 : 0)
                    + protectedCarrierCount(result.subLevel().getLevel(), localController, broken.facing());
            if (metadata.getByte(BrokenCaptureBoxDebrisData.TAG_KEY) != 1
                    || brokenEntity.shouldRenderDebris()
                    || released == null
                    || boxAuthorities != 1) {
                throw new AssertionError("sublevel durability break did not keep one detached broken-box authority");
            }
            released.discard();
        } finally {
            Entity released = helper.getLevel().getEntity(uuid);
            if (released != null) {
                released.discard();
            }
            if (result != null && localController != null) {
                disassembleAndRemove(helper, result, localController, controller);
            }
        }

        CaptureBoxAccess.Resolved restored = CaptureBoxAccess.resolve(helper.getLevel(), controller)
                .orElseThrow(() -> new AssertionError("durability-break cleanup did not restore the broken box"));
        if (restored.kind() != CaptureBoxStructure.Kind.BROKEN
                || !(restored.controllerBlockEntity() instanceof BrokenDinosaurCaptureBoxBlockEntity broken)
                || broken.shouldRenderDebris()
                || broken.saveWithFullMetadata(helper.getLevel().registryAccess())
                .getByte(BrokenCaptureBoxDebrisData.TAG_KEY) != 1) {
            throw new AssertionError("durability-break disassembly did not preserve detached broken debris");
        }
    }

    private static CapturedDinosaurData zeroDurabilityAnimalPayload(GameTestHelper helper) {
        JSAnimalBase animal = null;
        for (JSAnimal<?> registeredAnimal : JSAnimals.getAnimals()) {
            Entity candidate = registeredAnimal.getEntityType().get().create(helper.getLevel());
            if (candidate instanceof JSAnimalBase jsAnimal
                    && !(jsAnimal instanceof JSAquaticBase)
                    && jsAnimal.getBbWidth() <= 2.0F
                    && jsAnimal.getBbHeight() <= 2.0F) {
                animal = jsAnimal;
                break;
            }
            if (candidate != null) {
                candidate.discard();
            }
        }
        if (animal == null) {
            throw new AssertionError("no small non-aquatic Jurassic Saga animal is available");
        }
        CapturedDinosaurData captured;
        try {
            captured = CapturedDinosaurData.capture(animal)
                    .orElseThrow(() -> new AssertionError("could not snapshot durability-break animal"));
        } finally {
            animal.discard();
        }
        long gameTime = helper.getLevel().getGameTime();
        return new CapturedDinosaurData(
                captured.entityTypeId(),
                captured.originalUuid(),
                captured.displayName(),
                gameTime,
                gameTime,
                gameTime,
                0,
                captured.entityNbt(),
                captured.relativeAnestheticNbt(),
                captured.vitals(),
                captured.durabilityRemainderTicks()
        );
    }

    private static void verifyAssemblerEntry(GameTestHelper helper) throws Exception {
        BlockPos controller = helper.absolutePos(new BlockPos(6, 2, 6));
        place(helper, controller, CaptureBoxStructure.Kind.COMPLETE, true);
        seed(helper, controller, "valid");
        CaptureBoxAccess.Resolved source = CaptureBoxAccess.resolve(helper.getLevel(), controller).orElseThrow();
        CompoundTag before = source.controllerBlockEntity()
                .saveWithFullMetadata(helper.getLevel().registryAccess())
                .copy();
        ItemStack beforeMirror = mirroredStack(source);

        Direction stickyDirection = Direction.EAST;
        BlockPos support = exteriorSupport(controller, Direction.NORTH, stickyDirection);
        BlockPos assemblerPos = support.relative(stickyDirection.getOpposite());
        BlockState assemblerState = assemblerState(stickyDirection);
        if (!helper.getLevel().setBlockAndUpdate(assemblerPos, assemblerState)) {
            throw new AssertionError("could not place formal Physics Assembler entry fixture");
        }
        BlockEntity sourceAssemblerBlockEntity = helper.getLevel().getBlockEntity(assemblerPos);
        if (!isPhysicsAssembler(sourceAssemblerBlockEntity)) {
            throw new AssertionError("formal Physics Assembler fixture has no block entity");
        }

        SubLevelContainer container = SubLevelContainer.getContainer(helper.getLevel());
        if (container == null) {
            throw new AssertionError("formal Sable runtime has no server sublevel container");
        }
        Set<UUID> existingSubLevels = new HashSet<>();
        for (SubLevel subLevel : container.getAllSubLevels()) {
            existingSubLevels.add(subLevel.getUniqueId());
        }

        SubLevel assembled = null;
        BlockPos localAssembler = null;
        BlockPos localController = null;
        try {
            invokeAssemblerEntry(sourceAssemblerBlockEntity);
            Object sourceException = lastAssemblerException(sourceAssemblerBlockEntity);
            if (sourceException != null) {
                throw new AssertionError("PhysicsAssemblerBlockEntity entry retained lastException: "
                        + sourceException);
            }
            assembled = findNewSubLevel(container, existingSubLevels);
            if (assembled == null) {
                throw new AssertionError("PhysicsAssemblerBlockEntity entry did not create a sublevel");
            }
            BlockEntity movedAssembler = findBlockEntity(assembled, PHYSICS_ASSEMBLER_BLOCK_ENTITY);
            if (movedAssembler == null) {
                throw new AssertionError("assembled sublevel lacks the formal Physics Assembler block entity");
            }
            localAssembler = movedAssembler.getBlockPos();
            if (lastAssemblerException(movedAssembler) != null) {
                throw new AssertionError("moved Physics Assembler retained lastException");
            }
            CaptureBoxAccess.Resolved moved = findCaptureBox(assembled);
            if (moved == null) {
                throw new AssertionError("PhysicsAssemblerBlockEntity entry omitted the canonical 16-part box");
            }
            localController = moved.controller();
            if (CaptureBoxAccess.resolve(helper.getLevel(), controller).isPresent()) {
                throw new AssertionError("PhysicsAssemblerBlockEntity entry retained parent source authority");
            }
            CompoundTag movedMetadata = moved.controllerBlockEntity()
                    .saveWithFullMetadata(helper.getLevel().registryAccess())
                    .copy();
            if (moved.kind() != CaptureBoxStructure.Kind.COMPLETE
                    || !CaptureBoxAuthority.equivalentIgnoringPosition(before, movedMetadata)) {
                throw new AssertionError("PhysicsAssemblerBlockEntity entry changed capture-box authority");
            }
            assertAssembledSoleAuthority(helper, controller, assembled, moved, "assembler entry");
        } finally {
            if (assembled != null) {
                if (localAssembler != null) {
                    disassembleAndRemove(helper, assembled, localAssembler, assemblerPos);
                } else {
                    removeImmediately(helper, assembled);
                }
            }
        }

        CaptureBoxAccess.Resolved restored = CaptureBoxAccess.resolve(helper.getLevel(), controller)
                .orElseThrow(() -> new AssertionError("assembler entry cleanup did not restore capture box"));
        assertRestoredSoleAuthority(
                helper,
                controller,
                assembled,
                localController,
                Direction.NORTH,
                "assembler entry"
        );
        CompoundTag after = restored.controllerBlockEntity()
                .saveWithFullMetadata(helper.getLevel().registryAccess())
                .copy();
        if (!CaptureBoxAuthority.equivalentIgnoringPosition(before, after)
                || !beforeMirror.getComponents().equals(mirroredStack(restored).getComponents())) {
            throw new AssertionError("assembler entry round-trip changed metadata or durability mirror");
        }
        BlockEntity restoredAssemblerBlockEntity = helper.getLevel().getBlockEntity(assemblerPos);
        if (!isPhysicsAssembler(restoredAssemblerBlockEntity)
                || lastAssemblerException(restoredAssemblerBlockEntity) != null) {
            throw new AssertionError("assembler entry cleanup did not restore a healthy Physics Assembler");
        }
    }

    private static void verifyIncompleteRejected(GameTestHelper helper) throws Exception {
        BlockPos controller = helper.absolutePos(new BlockPos(6, 2, 6));
        place(helper, controller, CaptureBoxStructure.Kind.COMPLETE, false);
        SimAssemblyHelper.AssemblyResult result = null;
        try {
            result = SimAssemblyHelper.assembleFromSingleBlock(
                    helper.getLevel(),
                    controller,
                    controller,
                    true,
                    true
            );
            if (result != null) {
                throw new AssertionError("formal SimAssemblyHelper moved an incomplete capture box");
            }
        } catch (com.simibubi.create.content.contraptions.AssemblyException expected) {
            // Exact failure path used by the Physics Assembler UI.
        } finally {
            if (result != null) {
                BlockPos localController = controller.offset(result.offset());
                disassembleAndRemove(helper, result, localController, controller);
            }
        }
        if (CaptureBoxAccess.resolve(helper.getLevel(), controller).isPresent()) {
            throw new AssertionError("incomplete fixture unexpectedly became canonical");
        }
    }

    private static void disassembleAndRemove(
            GameTestHelper helper,
            SimAssemblyHelper.AssemblyResult result,
            BlockPos localController,
            BlockPos controller
    ) {
        disassembleAndRemove(helper, result.subLevel(), localController, controller);
    }

    private static void disassembleAndRemove(
            GameTestHelper helper,
            SubLevel subLevel,
            BlockPos localController,
            BlockPos controller
    ) {
        try {
            SimAssemblyHelper.disassembleSubLevel(
                    helper.getLevel(),
                    subLevel,
                    localController,
                    controller,
                    Rotation.NONE,
                    true
            );
        } finally {
            // Sable normally removes an emptied sublevel at the end of its next container tick. A GameTest can
            // assemble and disassemble in one tick, so remove it now instead of allowing its empty heat map to
            // attempt a split before the normal end-of-tick removal pass.
            removeImmediately(helper, subLevel);
        }
    }

    private static void removeImmediately(GameTestHelper helper, SubLevel subLevel) {
        subLevel.markRemoved();
        SubLevelContainer container = SubLevelContainer.getContainer(helper.getLevel());
        if (container != null) {
            container.processSubLevelRemovals();
        }
    }

    private static SubLevel findNewSubLevel(SubLevelContainer container, Set<UUID> existing) {
        SubLevel found = null;
        for (SubLevel candidate : container.getAllSubLevels()) {
            if (existing.contains(candidate.getUniqueId())) {
                continue;
            }
            if (found != null) {
                throw new AssertionError("Physics Assembler entry unexpectedly created multiple sublevels");
            }
            found = candidate;
        }
        return found;
    }

    private static void cleanupNewSubLevels(
            GameTestHelper helper,
            SubLevelContainer container,
            Set<UUID> existing,
            BlockPos parentAssembler
    ) {
        for (SubLevel candidate : new ArrayList<>(container.getAllSubLevels())) {
            if (existing.contains(candidate.getUniqueId())) {
                continue;
            }
            BlockEntity movedAssembler = findBlockEntity(candidate, PHYSICS_ASSEMBLER_BLOCK_ENTITY);
            if (movedAssembler != null) {
                disassembleAndRemove(
                        helper,
                        candidate,
                        movedAssembler.getBlockPos(),
                        parentAssembler
                );
            } else {
                removeImmediately(helper, candidate);
            }
        }
    }

    private static void assertAssembledSoleAuthority(
            GameTestHelper helper,
            BlockPos parentController,
            SubLevel targetSubLevel,
            CaptureBoxAccess.Resolved target,
            String scenario
    ) {
        boolean parentAuthority = CaptureBoxAccess.resolve(helper.getLevel(), parentController).isPresent();
        boolean targetAuthority = CaptureBoxAccess.resolve(
                targetSubLevel.getLevel(),
                target.controller()
        ).isPresent();
        int recoveryCarriers = protectedCarrierCount(helper.getLevel(), parentController, Direction.NORTH)
                + protectedCarrierCount(targetSubLevel.getLevel(), target.controller(), target.facing());
        int authorityCount = (parentAuthority ? 1 : 0) + (targetAuthority ? 1 : 0) + recoveryCarriers;
        if (authorityCount != 1 || parentAuthority || !targetAuthority || recoveryCarriers != 0) {
            throw new AssertionError(scenario + " assembly authority mismatch: parent=" + parentAuthority
                    + ", target=" + targetAuthority + ", protectedCarriers=" + recoveryCarriers);
        }
    }

    private static void assertRestoredSoleAuthority(
            GameTestHelper helper,
            BlockPos parentController,
            SubLevel previousTargetSubLevel,
            BlockPos previousTargetController,
            Direction previousTargetFacing,
            String scenario
    ) {
        boolean parentAuthority = CaptureBoxAccess.resolve(helper.getLevel(), parentController).isPresent();
        boolean targetAuthority = CaptureBoxAccess.resolve(
                previousTargetSubLevel.getLevel(),
                previousTargetController
        ).isPresent();
        int recoveryCarriers = protectedCarrierCount(helper.getLevel(), parentController, Direction.NORTH)
                + protectedCarrierCount(
                previousTargetSubLevel.getLevel(),
                previousTargetController,
                previousTargetFacing
        );
        int authorityCount = (parentAuthority ? 1 : 0) + (targetAuthority ? 1 : 0) + recoveryCarriers;
        if (authorityCount != 1 || !parentAuthority || targetAuthority || recoveryCarriers != 0) {
            throw new AssertionError(scenario + " disassembly authority mismatch: parent=" + parentAuthority
                    + ", target=" + targetAuthority + ", protectedCarriers=" + recoveryCarriers);
        }
    }

    private static int protectedCarrierCount(Level level, BlockPos controller, Direction facing) {
        AABB bounds = CaptureBoxStructure.localAabb(controller, facing).inflate(4.0D);
        return level.getEntitiesOfClass(
                ItemEntity.class,
                bounds,
                carrier -> !carrier.isRemoved()
                        && CaptureBoxAuthority.isProtectedRecoveryCarrier(carrier.getItem())
        ).size();
    }

    private static CaptureBoxAccess.Resolved findCaptureBox(SubLevel subLevel) {
        for (BlockPos pos : positionsInside(subLevel)) {
            CaptureBoxAccess.Resolved resolved = CaptureBoxAccess.resolve(subLevel.getLevel(), pos).orElse(null);
            if (resolved != null) {
                return resolved;
            }
        }
        return null;
    }

    private static BlockEntity findBlockEntity(SubLevel subLevel, String className) {
        for (BlockPos pos : positionsInside(subLevel)) {
            BlockEntity blockEntity = subLevel.getLevel().getBlockEntity(pos);
            if (blockEntity != null && className.equals(blockEntity.getClass().getName())) {
                return blockEntity;
            }
        }
        return null;
    }

    private static Iterable<BlockPos> positionsInside(SubLevel subLevel) {
        var bounds = subLevel.getPlot().getBoundingBox();
        long sizeX = (long) bounds.maxX() - bounds.minX() + 1L;
        long sizeY = (long) bounds.maxY() - bounds.minY() + 1L;
        long sizeZ = (long) bounds.maxZ() - bounds.minZ() + 1L;
        long volume = sizeX * sizeY * sizeZ;
        if (sizeX <= 0L || sizeY <= 0L || sizeZ <= 0L || volume > 4_096L) {
            throw new AssertionError("formal assembly produced an invalid or unexpectedly large plot bounds");
        }
        return BlockPos.betweenClosed(
                bounds.minX(),
                bounds.minY(),
                bounds.minZ(),
                bounds.maxX(),
                bounds.maxY(),
                bounds.maxZ()
        );
    }

    private static BlockPos exteriorSupport(
            BlockPos controller,
            Direction facing,
            Direction stickyDirection
    ) {
        return CaptureBoxStructure.placements(controller, facing).stream()
                .min(Comparator.comparingInt(placement -> projection(placement.pos(), stickyDirection)))
                .orElseThrow()
                .pos();
    }

    private static int projection(BlockPos pos, Direction direction) {
        return pos.getX() * direction.getStepX()
                + pos.getY() * direction.getStepY()
                + pos.getZ() * direction.getStepZ();
    }

    private static BlockState assemblerState(Direction stickyDirection) {
        Block block = BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath(
                "simulated",
                "physics_assembler"
        ));
        if (!(block instanceof PhysicsAssemblerBlock)) {
            throw new AssertionError("formal simulated:physics_assembler block is unavailable");
        }
        BlockState base = block.defaultBlockState();
        for (AttachFace face : AttachFace.values()) {
            for (Direction horizontal : Direction.Plane.HORIZONTAL) {
                BlockState candidate = base
                        .setValue(BlockStateProperties.ATTACH_FACE, face)
                        .setValue(BlockStateProperties.HORIZONTAL_FACING, horizontal);
                if (PhysicsAssemblerBlock.getStickyFacing(candidate) == stickyDirection) {
                    return candidate;
                }
            }
        }
        throw new AssertionError("formal Physics Assembler has no state for sticky direction " + stickyDirection);
    }

    private static boolean isPhysicsAssembler(BlockEntity blockEntity) {
        return blockEntity != null && PHYSICS_ASSEMBLER_BLOCK_ENTITY.equals(blockEntity.getClass().getName());
    }

    private static void invokeAssemblerEntry(BlockEntity assembler) throws ReflectiveOperationException {
        invokeNoArgs(assembler, "assembleOrDisassemble");
    }

    private static Object lastAssemblerException(BlockEntity assembler) throws ReflectiveOperationException {
        return invokeNoArgs(assembler, "getLastAssemblyException");
    }

    private static Object invokeNoArgs(BlockEntity target, String methodName) throws ReflectiveOperationException {
        Method method = target.getClass().getMethod(methodName);
        try {
            return method.invoke(target);
        } catch (InvocationTargetException exception) {
            Throwable cause = exception.getCause();
            throw new IllegalStateException("formal Physics Assembler entry threw from " + methodName, cause);
        }
    }

    private static ItemStack mirroredStack(CaptureBoxAccess.Resolved resolved) {
        if (!(resolved.controllerBlockEntity() instanceof DinosaurCaptureCageBlockEntity cage)
                || cage.getCapturedDinosaur() == null) {
            throw new AssertionError("VALID fixture lacks captured durability authority");
        }
        CapturedDinosaurData captured = cage.getCapturedDinosaur();
        ItemStack stack = new ItemStack(JSReviseItems.DINOSAUR_CAPTURE_CAGE.get());
        if (!DinosaurCaptureItemData.setContents(stack, captured, cage.getSupplies())) {
            throw new AssertionError("could not materialize standard durability mirror");
        }
        Integer maxDamage = stack.get(DataComponents.MAX_DAMAGE);
        Integer damage = stack.get(DataComponents.DAMAGE);
        int expectedDamage = CapturedDinosaurData.MAX_DURABILITY - captured.durability();
        if (maxDamage == null
                || maxDamage != CapturedDinosaurData.MAX_DURABILITY
                || damage == null
                || damage != expectedDamage) {
            throw new AssertionError("VALID fixture has incorrect DAMAGE/MAX_DAMAGE mirror");
        }
        return stack;
    }

    private static void addUnknownMetadata(GameTestHelper helper, BlockPos controller, String scenario) {
        CaptureBoxAccess.Resolved resolved = CaptureBoxAccess.resolve(helper.getLevel(), controller).orElseThrow();
        try (CaptureBoxRelocationState.Scope ignored = CaptureBoxRelocationState.open(
                helper.getLevel(),
                resolved.identity(),
                CaptureBoxRelocationState.State.PROVISIONAL
        )) {
            CaptureBoxAuthority.Result<CaptureBoxAuthority.Snapshot> snapshot =
                    CaptureBoxAuthority.snapshot(helper.getLevel(), controller);
            if (!snapshot.success()) {
                throw new AssertionError("could not snapshot unknown-metadata fixture: " + snapshot.code());
            }
            CompoundTag metadata = snapshot.value().fullMetadata();
            CompoundTag unknown = new CompoundTag();
            unknown.putString("Scenario", scenario);
            unknown.putLong("Sentinel", 0x4A53524556495345L);
            metadata.put("FutureFormalAeronauticsMetadata", unknown);
            CaptureBoxAuthority.Result<CaptureBoxAuthority.Snapshot> restored = CaptureBoxAuthority.restore(
                    helper.getLevel(),
                    controller,
                    new CaptureBoxAuthority.Snapshot(
                            snapshot.value().kind(),
                            snapshot.value().facing(),
                            snapshot.value().controller(),
                            snapshot.value().spaceIdentity(),
                            metadata,
                            snapshot.value().recoveryStack()
                    )
            );
            if (!restored.success()) {
                throw new AssertionError("could not restore unknown-metadata fixture: " + restored.code());
            }
        }
    }

    private static void place(
            GameTestHelper helper,
            BlockPos controller,
            CaptureBoxStructure.Kind kind,
            boolean complete
    ) {
        int remaining = complete ? CaptureBoxStructure.PART_COUNT : CaptureBoxStructure.PART_COUNT - 1;
        for (CaptureBoxStructure.Placement placement : CaptureBoxStructure.placements(controller, Direction.NORTH)) {
            if (remaining-- <= 0) {
                break;
            }
            if (!helper.getLevel().setBlockAndUpdate(
                    placement.pos(),
                    kind.canonicalState(
                            Direction.NORTH,
                            placement.offsetX(),
                            placement.offsetY(),
                            placement.offsetZ()
                    )
            )) {
                throw new AssertionError("could not place " + kind + " capture-box part " + placement);
            }
        }
    }

    private static void seed(GameTestHelper helper, BlockPos controller, String scenario) {
        if ("broken".equals(scenario)) {
            addUnknownMetadata(helper, controller, scenario);
            return;
        }
        if ("empty".equals(scenario)) {
            return;
        }
        BlockEntity blockEntity = helper.getLevel().getBlockEntity(controller);
        if (!(blockEntity instanceof DinosaurCaptureCageBlockEntity cage)) {
            throw new AssertionError("complete scenario has no controller block entity");
        }
        switch (scenario) {
            case "supplies" -> {
                if (!cage.setContents(null, new DinosaurCaptureSupplies(7, 4, 5, 3))) {
                    throw new AssertionError("could not seed supplied capture box");
                }
            }
            case "valid" -> {
                if (!cage.setContents(
                        payload(UUID.randomUUID()),
                        new DinosaurCaptureSupplies(9, 2, 4, 6)
                )) {
                    throw new AssertionError("could not seed VALID capture box");
                }
            }
            case "unreadable" -> {
                cage.setUnreadableCapturedDinosaur(StringTag.valueOf("aeronautics-raw-capture"));
                cage.setUnreadableSupplies(StringTag.valueOf("aeronautics-raw-supplies"));
            }
            default -> throw new IllegalArgumentException("unsupported seed scenario " + scenario);
        }
        if ("valid".equals(scenario) || "unreadable".equals(scenario)) {
            addUnknownMetadata(helper, controller, scenario);
        }
    }

    private static CompoundTag expectedAfterRelocation(CaptureBoxStructure.Kind kind, CompoundTag original) {
        CompoundTag expected = original.copy();
        if (kind == CaptureBoxStructure.Kind.BROKEN) {
            BrokenCaptureBoxDebrisData.inspect(expected).detachedProjection().writeTo(expected);
        }
        return expected;
    }

    private static CapturedDinosaurData payload(UUID uuid) {
        CompoundTag entityNbt = new CompoundTag();
        entityNbt.putFloat("Health", 20.0F);
        return new CapturedDinosaurData(
                BuiltInRegistries.ENTITY_TYPE.getKey(EntityType.PIG),
                uuid,
                "Aeronautics formal assembly",
                20L,
                20L,
                20L,
                15,
                entityNbt,
                new CompoundTag(),
                CapturedDinosaurVitals.deserializeNBT(new CompoundTag())
        );
    }
}
