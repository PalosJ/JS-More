package com.palos.jsmore.compat.aeronautics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class CaptureBoxRelocationStateTest {
    private static final ResourceLocation DIMENSION =
            ResourceLocation.fromNamespaceAndPath("minecraft", "overworld");
    private static final ResourceLocation KIND =
            ResourceLocation.fromNamespaceAndPath("jsmore", "dinosaur_capture_box");

    @AfterEach
    void clearState() {
        CaptureBoxRelocationState.clearAll();
    }

    @Test
    void defaultsToNoopAndAggregatesNestedScopesWithoutOrderDependence() {
        Object level = new Object();
        CaptureBoxRelocationState.StructureIdentity identity = identity(0);
        assertEquals(CaptureBoxRelocationState.State.NONE, query(level, identity));

        CaptureBoxRelocationState.Scope provisional = CaptureBoxRelocationState.openForTesting(
                level,
                identity,
                new CaptureBoxRelocationState.State(true, true, false)
        );
        CaptureBoxRelocationState.Scope drops = CaptureBoxRelocationState.openForTesting(
                level,
                identity,
                new CaptureBoxRelocationState.State(false, false, true)
        );
        assertEquals(CaptureBoxRelocationState.State.PROVISIONAL, query(level, identity));

        provisional.close();
        assertEquals(
                new CaptureBoxRelocationState.State(false, false, true),
                query(level, identity)
        );
        provisional.close();
        drops.close();
        assertEquals(CaptureBoxRelocationState.State.NONE, query(level, identity));
        assertEquals(0, CaptureBoxRelocationState.activeIdentityCountForTesting());
    }

    @Test
    void isolatesIdenticalStructureKeysByLevelIdentity() {
        Object firstLevel = new Object();
        Object secondLevel = new Object();
        CaptureBoxRelocationState.StructureIdentity identity = identity(1);
        try (CaptureBoxRelocationState.Scope ignored = CaptureBoxRelocationState.openForTesting(
                firstLevel,
                identity,
                CaptureBoxRelocationState.State.PROVISIONAL
        )) {
            assertTrue(query(firstLevel, identity).provisional());
            assertFalse(query(secondLevel, identity).provisional());
        }
    }

    @Test
    void boundsDistinctActiveIdentitiesButAllowsNestingTheSameIdentity() {
        Object level = new Object();
        List<CaptureBoxRelocationState.Scope> scopes = new ArrayList<>();
        try {
            for (int index = 0; index < CaptureBoxRelocationState.MAX_ACTIVE_IDENTITIES; index++) {
                scopes.add(CaptureBoxRelocationState.openForTesting(
                        level,
                        identity(index),
                        CaptureBoxRelocationState.State.PROVISIONAL
                ));
            }
            assertEquals(
                    CaptureBoxRelocationState.MAX_ACTIVE_IDENTITIES,
                    CaptureBoxRelocationState.activeIdentityCountForTesting()
            );
            try (CaptureBoxRelocationState.Scope nested = CaptureBoxRelocationState.openForTesting(
                    level,
                    identity(0),
                    CaptureBoxRelocationState.State.PROVISIONAL
            )) {
                assertTrue(query(level, identity(0)).provisional());
            }
            assertThrows(
                    IllegalStateException.class,
                    () -> CaptureBoxRelocationState.openForTesting(
                            level,
                            identity(CaptureBoxRelocationState.MAX_ACTIVE_IDENTITIES),
                            CaptureBoxRelocationState.State.PROVISIONAL
                    )
            );
        } finally {
            scopes.forEach(CaptureBoxRelocationState.Scope::close);
        }
        assertEquals(0, CaptureBoxRelocationState.activeIdentityCountForTesting());
    }

    @Test
    void clearAllInvalidatesExistingScopesAndTheirLateCloseIsSafe() {
        Object level = new Object();
        CaptureBoxRelocationState.StructureIdentity identity = identity(2);
        CaptureBoxRelocationState.Scope scope = CaptureBoxRelocationState.openForTesting(
                level,
                identity,
                CaptureBoxRelocationState.State.PROVISIONAL
        );

        CaptureBoxRelocationState.clearAll();
        assertEquals(CaptureBoxRelocationState.State.NONE, query(level, identity));
        assertEquals(0, CaptureBoxRelocationState.activeIdentityCountForTesting());
        scope.close();
        assertEquals(0, CaptureBoxRelocationState.activeIdentityCountForTesting());
    }

    @Test
    void noOpScopeDoesNotConsumeTheBoundedRegistry() {
        Object level = new Object();
        try (CaptureBoxRelocationState.Scope ignored = CaptureBoxRelocationState.openForTesting(
                level,
                identity(3),
                CaptureBoxRelocationState.State.NONE
        )) {
            assertEquals(0, CaptureBoxRelocationState.activeIdentityCountForTesting());
        }
    }

    private static CaptureBoxRelocationState.State query(
            Object level,
            CaptureBoxRelocationState.StructureIdentity identity
    ) {
        return CaptureBoxRelocationState.queryForTesting(level, identity);
    }

    private static CaptureBoxRelocationState.StructureIdentity identity(int x) {
        return new CaptureBoxRelocationState.StructureIdentity(
                new CaptureBoxWorldContext.SpaceIdentity(DIMENSION, Optional.of(UUID.fromString(
                        "00000000-0000-0000-0000-" + String.format("%012d", x + 1)
                ))),
                new BlockPos(x, 64, 0),
                KIND
        );
    }
}
