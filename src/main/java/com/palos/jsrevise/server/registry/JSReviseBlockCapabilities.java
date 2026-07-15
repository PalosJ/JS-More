package com.palos.jsrevise.server.registry;

import com.palos.jsrevise.server.block.entity.DinosaurCaptureCageBlockEntity;
import com.palos.jsrevise.server.system.capture.CaptureBoxAccess;
import com.palos.jsrevise.server.system.capture.CaptureBoxStructure;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;

/** Standard NeoForge capability exposure for the virtual capture-box hopper input. */
public final class JSReviseBlockCapabilities {
    private JSReviseBlockCapabilities() {
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
                JSReviseBlocks.DINOSAUR_CAPTURE_CAGE.get()
        );
    }
}
