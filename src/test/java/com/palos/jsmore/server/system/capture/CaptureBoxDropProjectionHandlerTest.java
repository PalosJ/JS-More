package com.palos.jsmore.server.system.capture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.palos.jsmore.server.registry.JSMoreItems;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class CaptureBoxDropProjectionHandlerTest {
    @Test
    void bothCaptureBoxDropsUseTheProjectionPath() {
        assertTrue(CaptureBoxDropProjectionHandler.isCaptureBoxDrop(
                new ItemStack(JSMoreItems.DINOSAUR_CAPTURE_CAGE.get())
        ));
        assertTrue(CaptureBoxDropProjectionHandler.isCaptureBoxDrop(
                new ItemStack(JSMoreItems.BROKEN_DINOSAUR_CAPTURE_BOX.get())
        ));
        assertFalse(CaptureBoxDropProjectionHandler.isCaptureBoxDrop(new ItemStack(Items.STONE)));
    }

    @Test
    void projectedDropAddsRotatedThrowAndCraftPointVelocityExactlyOnce() {
        Vec3 position = new Vec3(14.5D, 82.0D, -7.25D);
        Vec3 rotatedThrow = new Vec3(-0.1D, 0.2D, 0.05D);
        Vec3 craft = new Vec3(0.4D, -0.05D, 0.2D);

        CaptureBoxDropProjectionHandler.DropProjection projection =
                CaptureBoxDropProjectionHandler.finishProjection(position, rotatedThrow, craft, false).orElseThrow();

        assertEquals(position, projection.position());
        assertEquals(0.3D, projection.velocity().x, 1.0E-12D);
        assertEquals(0.15D, projection.velocity().y, 1.0E-12D);
        assertEquals(0.25D, projection.velocity().z, 1.0E-12D);
        assertTrue(CaptureBoxDropProjectionHandler.finishProjection(position, rotatedThrow, craft, true).isEmpty());
        assertTrue(CaptureBoxDropProjectionHandler.finishProjection(
                new Vec3(Double.NaN, 0.0D, 0.0D),
                rotatedThrow,
                craft,
                false
        ).isEmpty());
    }
}
