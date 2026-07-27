package com.palos.jsmore.server.system.egg;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

class EggCollectorServiceTest {
    @Test
    void collectionRangeIsAClosedRadiusSixteenSphereAroundBlockCenter() {
        BlockPos collector = BlockPos.ZERO;
        Vec3 center = Vec3.atCenterOf(collector);

        assertTrue(EggCollectorService.isWithinCollectionSphere(collector, center.add(16.0D, 0.0D, 0.0D)));
        assertTrue(EggCollectorService.isWithinCollectionSphere(collector, center.add(8.0D, 8.0D, 8.0D)));
        assertFalse(EggCollectorService.isWithinCollectionSphere(collector, center.add(16.0001D, 0.0D, 0.0D)));
        assertFalse(EggCollectorService.isWithinCollectionSphere(collector, center.add(10.0D, 10.0D, 10.0D)));
    }

    @Test
    void scanTicksAreTenTicksApartAndStaggeredByPosition() {
        BlockPos first = BlockPos.ZERO;
        BlockPos second = new BlockPos(1, 0, 0);
        long firstTick = firstScanAtOrAfterZero(first);
        long secondTick = firstScanAtOrAfterZero(second);

        assertTrue(EggCollectorService.shouldScan(firstTick, first));
        assertTrue(EggCollectorService.shouldScan(firstTick + EggCollectorService.SCAN_INTERVAL_TICKS, first));
        assertFalse(EggCollectorService.shouldScan(firstTick + 1L, first));
        assertTrue(firstTick != secondTick);
    }

    @Test
    void collectionUsesTheLoadedEntityQueryWithoutAnyChunkLoadingCall() throws IOException {
        // GameTest structure tickets keep adjacent test chunks loaded, so the non-loading boundary is locked at the
        // actual bytecode API call: Level#getEntitiesOfClass enumerates loaded entity sections without getChunk calls.
        ClassNode node = new ClassNode();
        try (InputStream stream = EggCollectorService.class.getResourceAsStream("EggCollectorService.class")) {
            Assertions.assertNotNull(stream);
            new ClassReader(stream).accept(node, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        }
        MethodNode collect = node.methods.stream()
                .filter(method -> method.name.equals("collect"))
                .findFirst()
                .orElseThrow();
        Set<String> loadingCalls = Set.of("getChunk", "getChunkAt", "getChunkNow");
        boolean usesLoadedEntityQuery = false;
        for (var instruction : collect.instructions) {
            if (instruction instanceof MethodInsnNode call) {
                usesLoadedEntityQuery |= call.name.equals("getEntitiesOfClass");
                assertFalse(loadingCalls.contains(call.name), "Collection must never request or load a chunk");
            }
        }
        assertTrue(usesLoadedEntityQuery, "Collection must query the level's already-loaded entity sections");
    }

    private static long firstScanAtOrAfterZero(BlockPos pos) {
        for (long tick = 0L; tick < EggCollectorService.SCAN_INTERVAL_TICKS; tick++) {
            if (EggCollectorService.shouldScan(tick, pos)) {
                return tick;
            }
        }
        throw new AssertionError("No scan tick in one interval");
    }
}
