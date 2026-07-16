package com.palos.jsmore.mixin.client;

import com.palos.jsmore.server.block.BrokenDinosaurCaptureBoxBlock;
import com.palos.jsmore.server.block.DinosaurCaptureCageBlock;
import com.palos.jsmore.server.registry.JSMoreBlocks;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelRenderer.class)
public abstract class LevelRendererDestroyProgressMixin {
    @Unique
    private final Map<Integer, CageMirror> jsmore$cageDestroyMirrors = new HashMap<>();
    @Unique
    private boolean jsmore$mirroringDestroyProgress;

    @Inject(method = "destroyBlockProgress", at = @At("HEAD"))
    private void jsmore$mirrorCaptureCageDestroyProgress(
            int breakerId,
            BlockPos pos,
            int progress,
            CallbackInfo callbackInfo
    ) {
        if (this.jsmore$mirroringDestroyProgress) {
            return;
        }

        CageMirror previous = this.jsmore$cageDestroyMirrors.remove(breakerId);
        if (previous != null) {
            this.jsmore$clearMirrorProgress(breakerId, previous);
        }
        if (progress < 0 || pos == null) {
            return;
        }

        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            return;
        }
        BlockState state = level.getBlockState(pos);
        CageMirror mirror = jsmore$mirrorForState(pos, state);
        if (mirror == null) {
            return;
        }

        this.jsmore$cageDestroyMirrors.put(breakerId, mirror);
        this.jsmore$withMirrorGuard(() -> {
            if (mirror.broken()) {
                for (BrokenDinosaurCaptureBoxBlock.PartPlacement placement :
                        BrokenDinosaurCaptureBoxBlock.placements(mirror.controllerPos(), mirror.facing())) {
                    if (!placement.pos().equals(pos)) {
                        ((LevelRenderer) (Object) this).destroyBlockProgress(
                                jsmore$fakeBreakerId(breakerId, jsmore$partIndex(placement)),
                                placement.pos(),
                                progress
                        );
                    }
                }
            } else {
                for (DinosaurCaptureCageBlock.PartPlacement placement :
                        DinosaurCaptureCageBlock.placements(mirror.controllerPos(), mirror.facing())) {
                    if (!placement.pos().equals(pos)) {
                        ((LevelRenderer) (Object) this).destroyBlockProgress(
                                jsmore$fakeBreakerId(breakerId, jsmore$partIndex(placement)),
                                placement.pos(),
                                progress
                        );
                    }
                }
            }
        });
    }

    @Inject(method = "setLevel", at = @At("HEAD"), require = 0)
    private void jsmore$clearCaptureCageDestroyProgressOnLevelChange(ClientLevel level, CallbackInfo callbackInfo) {
        this.jsmore$cageDestroyMirrors.clear();
    }

    @Inject(method = "clear", at = @At("HEAD"), require = 0)
    private void jsmore$clearCaptureCageDestroyProgressOnRendererClear(CallbackInfo callbackInfo) {
        this.jsmore$cageDestroyMirrors.clear();
    }

    @Unique
    private void jsmore$clearMirrorProgress(int breakerId, CageMirror mirror) {
        this.jsmore$withMirrorGuard(() -> {
            if (mirror.broken()) {
                for (BrokenDinosaurCaptureBoxBlock.PartPlacement placement :
                        BrokenDinosaurCaptureBoxBlock.placements(mirror.controllerPos(), mirror.facing())) {
                    ((LevelRenderer) (Object) this).destroyBlockProgress(
                            jsmore$fakeBreakerId(breakerId, jsmore$partIndex(placement)),
                            placement.pos(),
                            -1
                    );
                }
            } else {
                for (DinosaurCaptureCageBlock.PartPlacement placement :
                        DinosaurCaptureCageBlock.placements(mirror.controllerPos(), mirror.facing())) {
                    ((LevelRenderer) (Object) this).destroyBlockProgress(
                            jsmore$fakeBreakerId(breakerId, jsmore$partIndex(placement)),
                            placement.pos(),
                            -1
                    );
                }
            }
        });
    }

    @Unique
    private void jsmore$withMirrorGuard(Runnable action) {
        this.jsmore$mirroringDestroyProgress = true;
        try {
            action.run();
        } finally {
            this.jsmore$mirroringDestroyProgress = false;
        }
    }

    @Unique
    private static int jsmore$fakeBreakerId(int breakerId, int partIndex) {
        return Integer.MIN_VALUE | ((breakerId * DinosaurCaptureCageBlock.PART_COUNT + partIndex) & Integer.MAX_VALUE);
    }

    @Unique
    private static CageMirror jsmore$mirrorForState(BlockPos pos, BlockState state) {
        if (state.is(JSMoreBlocks.DINOSAUR_CAPTURE_CAGE.get())) {
            return new CageMirror(
                    DinosaurCaptureCageBlock.controllerPos(pos, state),
                    state.getValue(DinosaurCaptureCageBlock.FACING),
                    false
            );
        }
        if (state.is(JSMoreBlocks.BROKEN_DINOSAUR_CAPTURE_BOX.get())) {
            return new CageMirror(
                    BrokenDinosaurCaptureBoxBlock.controllerPos(pos, state),
                    state.getValue(BrokenDinosaurCaptureBoxBlock.FACING),
                    true
            );
        }
        return null;
    }

    @Unique
    private static int jsmore$partIndex(DinosaurCaptureCageBlock.PartPlacement placement) {
        return (placement.offsetY() * DinosaurCaptureCageBlock.LENGTH * DinosaurCaptureCageBlock.WIDTH)
                + (placement.offsetZ() * DinosaurCaptureCageBlock.WIDTH)
                + placement.offsetX();
    }

    @Unique
    private static int jsmore$partIndex(BrokenDinosaurCaptureBoxBlock.PartPlacement placement) {
        return (placement.offsetY() * BrokenDinosaurCaptureBoxBlock.LENGTH * BrokenDinosaurCaptureBoxBlock.WIDTH)
                + (placement.offsetZ() * BrokenDinosaurCaptureBoxBlock.WIDTH)
                + placement.offsetX();
    }

    @Unique
    private record CageMirror(BlockPos controllerPos, Direction facing, boolean broken) {
    }
}
