package com.palos.jsmore.server.registry;

import com.palos.jsmore.server.block.entity.DinosaurCaptureCageBlockEntity;
import com.palos.jsmore.server.system.capture.CaptureBoxAccess;
import com.palos.jsmore.server.system.capture.CaptureBoxStructure;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;

/** Standard NeoForge capability exposure for the virtual capture-box hopper input. */
public final class JSMoreBlockCapabilities {
    private JSMoreBlockCapabilities() {
    }

    public static void register(RegisterCapabilitiesEvent event) {
        event.registerBlock(
                Capabilities.ItemHandler.BLOCK,
                (level, pos, state, blockEntity, side) -> CaptureBoxAccess.resolve(level, pos)
                        .filter(resolved -> resolved.kind() == CaptureBoxStructure.Kind.COMPLETE)
                        .map(CaptureBoxAccess.Resolved::controllerBlockEntity)
                        .filter(DinosaurCaptureCageBlockEntity.class::isInstance)
                        .map(DinosaurCaptureCageBlockEntity.class::cast)
                        .filter(cage -> !cage.hasUnreadableContents())
                        .map(DinosaurCaptureCageBlockEntity::getItemHandler)
                        .orElse(null),
                JSMoreBlocks.DINOSAUR_CAPTURE_CAGE.get()
        );
    }
}
