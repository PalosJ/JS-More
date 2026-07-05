package com.palos.jsrevise.mixin.client;

import com.palos.jsrevise.server.block.BrokenDinosaurCaptureBoxBlock;
import com.palos.jsrevise.server.block.DinosaurCaptureCageBlock;
import com.palos.jsrevise.server.registry.JSReviseBlocks;
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
    private final Map<Integer, CageMirror> jsrevise$cageDestroyMirrors = new HashMap<>();
    @Unique
    private boolean jsrevise$mirroringDestroyProgress;

    @Inject(method = "destroyBlockProgress", at = @At("HEAD"))
    private void jsrevise$mirrorCaptureCageDestroyProgress(
            int breakerId,
            BlockPos pos,
            int progress,
            CallbackInfo callbackInfo
    ) {
        if (this.jsrevise$mirroringDestroyProgress) {
            return;
        }

        CageMirror previous = this.jsrevise$cageDestroyMirrors.remove(breakerId);
        if (previous != null) {
            this.jsrevise$clearMirrorProgress(breakerId, previous);
        }
        if (progress < 0 || pos == null) {
            return;
        }

        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            return;
        }
        BlockState state = level.getBlockState(pos);
        CageMirror mirror = jsrevise$mirrorForState(pos, state);
        if (mirror == null) {
            return;
        }

        this.jsrevise$cageDestroyMirrors.put(breakerId, mirror);
        this.jsrevise$withMirrorGuard(() -> {
            if (mirror.broken()) {
                for (BrokenDinosaurCaptureBoxBlock.PartPlacement placement :
                        BrokenDinosaurCaptureBoxBlock.placements(mirror.controllerPos(), mirror.facing())) {
                    if (!placement.pos().equals(pos)) {
                        ((LevelRenderer) (Object) this).destroyBlockProgress(
                                jsrevise$fakeBreakerId(breakerId, jsrevise$partIndex(placement)),
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
                                jsrevise$fakeBreakerId(breakerId, jsrevise$partIndex(placement)),
                                placement.pos(),
                                progress
                        );
                    }
                }
            }
        });
    }

    @Inject(method = "setLevel", at = @At("HEAD"), require = 0)
    private void jsrevise$clearCaptureCageDestroyProgressOnLevelChange(ClientLevel level, CallbackInfo callbackInfo) {
        this.jsrevise$cageDestroyMirrors.clear();
    }

    @Inject(method = "clear", at = @At("HEAD"), require = 0)
    private void jsrevise$clearCaptureCageDestroyProgressOnRendererClear(CallbackInfo callbackInfo) {
        this.jsrevise$cageDestroyMirrors.clear();
    }

    @Unique
    private void jsrevise$clearMirrorProgress(int breakerId, CageMirror mirror) {
        this.jsrevise$withMirrorGuard(() -> {
            if (mirror.broken()) {
                for (BrokenDinosaurCaptureBoxBlock.PartPlacement placement :
                        BrokenDinosaurCaptureBoxBlock.placements(mirror.controllerPos(), mirror.facing())) {
                    ((LevelRenderer) (Object) this).destroyBlockProgress(
                            jsrevise$fakeBreakerId(breakerId, jsrevise$partIndex(placement)),
                            placement.pos(),
                            -1
                    );
                }
            } else {
                for (DinosaurCaptureCageBlock.PartPlacement placement :
                        DinosaurCaptureCageBlock.placements(mirror.controllerPos(), mirror.facing())) {
                    ((LevelRenderer) (Object) this).destroyBlockProgress(
                            jsrevise$fakeBreakerId(breakerId, jsrevise$partIndex(placement)),
                            placement.pos(),
                            -1
                    );
                }
            }
        });
    }

    @Unique
    private void jsrevise$withMirrorGuard(Runnable action) {
        this.jsrevise$mirroringDestroyProgress = true;
        try {
            action.run();
        } finally {
            this.jsrevise$mirroringDestroyProgress = false;
        }
    }

    @Unique
    private static int jsrevise$fakeBreakerId(int breakerId, int partIndex) {
        return Integer.MIN_VALUE | ((breakerId * DinosaurCaptureCageBlock.PART_COUNT + partIndex) & Integer.MAX_VALUE);
    }

    @Unique
    private static CageMirror jsrevise$mirrorForState(BlockPos pos, BlockState state) {
        if (state.is(JSReviseBlocks.DINOSAUR_CAPTURE_CAGE.get())) {
            return new CageMirror(
                    DinosaurCaptureCageBlock.controllerPos(pos, state),
                    state.getValue(DinosaurCaptureCageBlock.FACING),
                    false
            );
        }
        if (state.is(JSReviseBlocks.BROKEN_DINOSAUR_CAPTURE_BOX.get())) {
            return new CageMirror(
                    BrokenDinosaurCaptureBoxBlock.controllerPos(pos, state),
                    state.getValue(BrokenDinosaurCaptureBoxBlock.FACING),
                    true
            );
        }
        return null;
    }

    @Unique
    private static int jsrevise$partIndex(DinosaurCaptureCageBlock.PartPlacement placement) {
        return (placement.offsetY() * DinosaurCaptureCageBlock.LENGTH * DinosaurCaptureCageBlock.WIDTH)
                + (placement.offsetZ() * DinosaurCaptureCageBlock.WIDTH)
                + placement.offsetX();
    }

    @Unique
    private static int jsrevise$partIndex(BrokenDinosaurCaptureBoxBlock.PartPlacement placement) {
        return (placement.offsetY() * BrokenDinosaurCaptureBoxBlock.LENGTH * BrokenDinosaurCaptureBoxBlock.WIDTH)
                + (placement.offsetZ() * BrokenDinosaurCaptureBoxBlock.WIDTH)
                + placement.offsetX();
    }

    @Unique
    private record CageMirror(BlockPos controllerPos, Direction facing, boolean broken) {
    }
}
