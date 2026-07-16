package com.palos.jsmore.client.overlay;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ClientOverlaySessionClockTest {
    private static final ResourceLocation OVERWORLD =
            ResourceLocation.fromNamespaceAndPath("minecraft", "overworld");
    private static final ResourceLocation NETHER =
            ResourceLocation.fromNamespaceAndPath("minecraft", "the_nether");

    @BeforeEach
    @AfterEach
    void clearClock() {
        ClientOverlaySessionClock.clear();
    }

    @Test
    void firstCurrentLazilyBindsAtZeroAndReusesStamp() {
        Object level = new Object();

        ClientOverlaySessionClock.Stamp first = ClientOverlaySessionClock.current(level, OVERWORLD);
        ClientOverlaySessionClock.Stamp second = ClientOverlaySessionClock.current(level, OVERWORLD);

        assertEquals(0L, first.logicalTick());
        assertSame(first, second);
    }

    @Test
    void postTickAdvancesMonotonicallyWithoutReadingWorldGameTime() {
        Object level = new Object();
        ClientOverlaySessionClock.Stamp initial = ClientOverlaySessionClock.current(level, OVERWORLD);

        ClientOverlaySessionClock.Stamp firstTick = ClientOverlaySessionClock.advance(level, OVERWORLD);
        ClientOverlaySessionClock.Stamp secondTick = ClientOverlaySessionClock.advance(level, OVERWORLD);

        assertEquals(initial.generation(), firstTick.generation());
        assertEquals(1L, firstTick.logicalTick());
        assertEquals(2L, secondTick.logicalTick());
    }

    @Test
    void nullPostTickDoesNotCreateOrAdvanceASession() {
        ClientOverlaySessionClock.onPostTick(null);

        assertNull(ClientOverlaySessionClock.current((Object) null, OVERWORLD));
        assertEquals(0L, ClientOverlaySessionClock.current(new Object(), OVERWORLD).logicalTick());
    }

    @Test
    void levelIdentityOrDimensionChangeStartsANewGenerationAtZero() {
        Object firstLevel = new Object();
        ClientOverlaySessionClock.Stamp first = ClientOverlaySessionClock.current(firstLevel, OVERWORLD);
        ClientOverlaySessionClock.advance(firstLevel, OVERWORLD);

        ClientOverlaySessionClock.Stamp replacedLevel =
                ClientOverlaySessionClock.current(new Object(), OVERWORLD);
        ClientOverlaySessionClock.Stamp replacedDimension =
                ClientOverlaySessionClock.current(new Object(), NETHER);

        assertEquals(first.generation() + 1L, replacedLevel.generation());
        assertEquals(0L, replacedLevel.logicalTick());
        assertEquals(replacedLevel.generation() + 1L, replacedDimension.generation());
        assertEquals(0L, replacedDimension.logicalTick());
    }

    @Test
    void explicitClearAdvancesGenerationExactlyOnceBeforeNextBind() {
        Object level = new Object();
        ClientOverlaySessionClock.Stamp before = ClientOverlaySessionClock.current(level, OVERWORLD);

        ClientOverlaySessionClock.clear();
        ClientOverlaySessionClock.Stamp after = ClientOverlaySessionClock.current(new Object(), OVERWORLD);

        assertEquals(before.generation() + 1L, after.generation());
        assertEquals(0L, after.logicalTick());
    }

    @Test
    void incrementSaturatesAtLongMaximum() {
        assertEquals(Long.MAX_VALUE, ClientOverlaySessionClock.saturatedIncrement(Long.MAX_VALUE - 1L));
        assertEquals(Long.MAX_VALUE, ClientOverlaySessionClock.saturatedIncrement(Long.MAX_VALUE));
    }
}
