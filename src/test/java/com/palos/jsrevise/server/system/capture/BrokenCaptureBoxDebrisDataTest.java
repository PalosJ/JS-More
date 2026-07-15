package com.palos.jsrevise.server.system.capture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import org.junit.jupiter.api.Test;

class BrokenCaptureBoxDebrisDataTest {
    @Test
    void absentAndValidBytesKeepTheirDistinctPersistenceStates() {
        CompoundTag absentTag = new CompoundTag();
        BrokenCaptureBoxDebrisData absent = BrokenCaptureBoxDebrisData.inspect(absentTag);
        assertEquals(BrokenCaptureBoxDebrisData.State.ABSENT, absent.state());
        assertTrue(absent.shouldRenderDebris());

        CompoundTag attachedTag = new CompoundTag();
        attachedTag.putByte(BrokenCaptureBoxDebrisData.TAG_KEY, (byte) 0);
        BrokenCaptureBoxDebrisData attached = BrokenCaptureBoxDebrisData.inspect(attachedTag);
        assertEquals(BrokenCaptureBoxDebrisData.State.VALID_FALSE, attached.state());
        assertTrue(attached.shouldRenderDebris());

        CompoundTag detachedTag = new CompoundTag();
        detachedTag.putByte(BrokenCaptureBoxDebrisData.TAG_KEY, (byte) 1);
        BrokenCaptureBoxDebrisData detached = BrokenCaptureBoxDebrisData.inspect(detachedTag);
        assertEquals(BrokenCaptureBoxDebrisData.State.VALID_TRUE, detached.state());
        assertFalse(detached.shouldRenderDebris());

        CompoundTag written = new CompoundTag();
        attached.writeTo(written);
        assertTrue(written.contains(BrokenCaptureBoxDebrisData.TAG_KEY, Tag.TAG_BYTE));
        assertEquals(0, written.getByte(BrokenCaptureBoxDebrisData.TAG_KEY));
        detached.writeTo(written);
        assertEquals(1, written.getByte(BrokenCaptureBoxDebrisData.TAG_KEY));
        absent.writeTo(written);
        assertFalse(written.contains(BrokenCaptureBoxDebrisData.TAG_KEY));
    }

    @Test
    void malformedTypeAndByteRemainLosslessAndFailSafeHidden() {
        CompoundTag malformedType = new CompoundTag();
        malformedType.put(BrokenCaptureBoxDebrisData.TAG_KEY, StringTag.valueOf("future-format"));
        BrokenCaptureBoxDebrisData stringData = BrokenCaptureBoxDebrisData.inspect(malformedType);
        assertEquals(BrokenCaptureBoxDebrisData.State.MALFORMED, stringData.state());
        assertTrue(stringData.malformed());
        assertFalse(stringData.shouldRenderDebris());
        assertEquals(StringTag.valueOf("future-format"), stringData.malformedRawCopy());

        CompoundTag rewritten = new CompoundTag();
        stringData.detach().writeTo(rewritten);
        assertEquals(StringTag.valueOf("future-format"), rewritten.get(BrokenCaptureBoxDebrisData.TAG_KEY));

        CompoundTag malformedByte = new CompoundTag();
        malformedByte.putByte(BrokenCaptureBoxDebrisData.TAG_KEY, (byte) 2);
        BrokenCaptureBoxDebrisData byteData = BrokenCaptureBoxDebrisData.inspect(malformedByte);
        assertEquals(BrokenCaptureBoxDebrisData.State.MALFORMED, byteData.state());
        CompoundTag byteRoundTrip = new CompoundTag();
        byteData.detachedProjection().writeTo(byteRoundTrip);
        assertEquals(2, byteRoundTrip.getByte(BrokenCaptureBoxDebrisData.TAG_KEY));
    }

    @Test
    void relocationProjectsEveryKnownStateToDetachedWithoutMutatingInput() {
        for (byte value : new byte[]{0, 1}) {
            CompoundTag metadata = new CompoundTag();
            metadata.putByte(BrokenCaptureBoxDebrisData.TAG_KEY, value);
            BrokenCaptureBoxDebrisData original = BrokenCaptureBoxDebrisData.inspect(metadata);
            CompoundTag projected = new CompoundTag();
            original.detachedProjection().writeTo(projected);

            assertEquals(value, metadata.getByte(BrokenCaptureBoxDebrisData.TAG_KEY));
            assertEquals(1, projected.getByte(BrokenCaptureBoxDebrisData.TAG_KEY));
        }

        CompoundTag projectedAbsent = new CompoundTag();
        BrokenCaptureBoxDebrisData.absent().detachedProjection().writeTo(projectedAbsent);
        assertEquals(1, projectedAbsent.getByte(BrokenCaptureBoxDebrisData.TAG_KEY));
        assertNull(BrokenCaptureBoxDebrisData.absent().malformedRawCopy());
    }
}
