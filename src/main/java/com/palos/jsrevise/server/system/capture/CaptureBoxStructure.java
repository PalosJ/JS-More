package com.palos.jsrevise.server.system.capture;

import com.palos.jsrevise.JSRevise;
import com.palos.jsrevise.server.block.BrokenDinosaurCaptureBoxBlock;
import com.palos.jsrevise.server.block.DinosaurCaptureCageBlock;
import com.palos.jsrevise.server.block.entity.BrokenDinosaurCaptureBoxBlockEntity;
import com.palos.jsrevise.server.block.entity.DinosaurCaptureCageBlockEntity;
import com.palos.jsrevise.server.registry.JSReviseBlocks;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/** The fixed, capture-box-specific 2 x 4 x 2 structure description. */
public final class CaptureBoxStructure {
    public static final int WIDTH = 2;
    public static final int LENGTH = 4;
    public static final int HEIGHT = 2;
    public static final int PART_COUNT = WIDTH * LENGTH * HEIGHT;

    private CaptureBoxStructure() {
    }

    public static List<Placement> placements(BlockPos controller, Direction facing) {
        Objects.requireNonNull(controller, "controller");
        Objects.requireNonNull(facing, "facing");
        List<Placement> placements = new ArrayList<>(PART_COUNT);
        for (int offsetY = 0; offsetY < HEIGHT; offsetY++) {
            for (int offsetZ = 0; offsetZ < LENGTH; offsetZ++) {
                for (int offsetX = 0; offsetX < WIDTH; offsetX++) {
                    placements.add(new Placement(
                            partPos(controller, facing, offsetX, offsetY, offsetZ),
                            offsetX,
                            offsetY,
                            offsetZ
                    ));
                }
            }
        }
        return List.copyOf(placements);
    }

    public static BlockPos controllerPos(BlockPos partPos, BlockState state, Kind kind) {
        Direction facing = kind.facing(state);
        return partPos
                .relative(facing.getClockWise(), -kind.offsetX(state))
                .relative(facing, -kind.offsetZ(state))
                .below(kind.offsetY(state));
    }

    public static AABB localAabb(BlockPos controller, Direction facing) {
        List<Placement> placements = placements(controller, facing);
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;
        for (Placement placement : placements) {
            BlockPos pos = placement.pos();
            minX = Math.min(minX, pos.getX());
            minY = Math.min(minY, pos.getY());
            minZ = Math.min(minZ, pos.getZ());
            maxX = Math.max(maxX, pos.getX());
            maxY = Math.max(maxY, pos.getY());
            maxZ = Math.max(maxZ, pos.getZ());
        }
        return new AABB(minX, minY, minZ, maxX + 1.0D, maxY + 1.0D, maxZ + 1.0D);
    }

    public static BlockState canonicalState(
            BlockState baseState,
            Kind kind,
            Direction facing,
            int offsetX,
            int offsetY,
            int offsetZ
    ) {
        Objects.requireNonNull(baseState, "baseState");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(facing, "facing");
        boolean controller = offsetX == 0 && offsetY == 0 && offsetZ == 0;
        return switch (kind) {
            case COMPLETE -> baseState
                    .setValue(DinosaurCaptureCageBlock.FACING, facing)
                    .setValue(DinosaurCaptureCageBlock.OFFSET_X, offsetX)
                    .setValue(DinosaurCaptureCageBlock.OFFSET_Y, offsetY)
                    .setValue(DinosaurCaptureCageBlock.OFFSET_Z, offsetZ)
                    .setValue(DinosaurCaptureCageBlock.CONTROLLER, controller);
            case BROKEN -> baseState
                    .setValue(BrokenDinosaurCaptureBoxBlock.FACING, facing)
                    .setValue(BrokenDinosaurCaptureBoxBlock.OFFSET_X, offsetX)
                    .setValue(BrokenDinosaurCaptureBoxBlock.OFFSET_Y, offsetY)
                    .setValue(BrokenDinosaurCaptureBoxBlock.OFFSET_Z, offsetZ)
                    .setValue(BrokenDinosaurCaptureBoxBlock.CONTROLLER, controller);
        };
    }

    private static BlockPos partPos(
            BlockPos controller,
            Direction facing,
            int offsetX,
            int offsetY,
            int offsetZ
    ) {
        return controller
                .relative(facing.getClockWise(), offsetX)
                .relative(facing, offsetZ)
                .above(offsetY);
    }

    public enum Kind {
        COMPLETE(JSRevise.id("dinosaur_capture_box")),
        BROKEN(JSRevise.id("broken_dinosaur_capture_box"));

        private final ResourceLocation token;

        Kind(ResourceLocation token) {
            this.token = token;
        }

        public ResourceLocation token() {
            return this.token;
        }

        public Block block() {
            return switch (this) {
                case COMPLETE -> JSReviseBlocks.DINOSAUR_CAPTURE_CAGE.get();
                case BROKEN -> JSReviseBlocks.BROKEN_DINOSAUR_CAPTURE_BOX.get();
            };
        }

        public BlockState canonicalState(Direction facing, int offsetX, int offsetY, int offsetZ) {
            return CaptureBoxStructure.canonicalState(
                    block().defaultBlockState(),
                    this,
                    facing,
                    offsetX,
                    offsetY,
                    offsetZ
            );
        }

        public boolean matches(BlockState state) {
            return state != null && state.is(block());
        }

        public Direction facing(BlockState state) {
            return switch (this) {
                case COMPLETE -> state.getValue(DinosaurCaptureCageBlock.FACING);
                case BROKEN -> state.getValue(BrokenDinosaurCaptureBoxBlock.FACING);
            };
        }

        public int offsetX(BlockState state) {
            return switch (this) {
                case COMPLETE -> state.getValue(DinosaurCaptureCageBlock.OFFSET_X);
                case BROKEN -> state.getValue(BrokenDinosaurCaptureBoxBlock.OFFSET_X);
            };
        }

        public int offsetY(BlockState state) {
            return switch (this) {
                case COMPLETE -> state.getValue(DinosaurCaptureCageBlock.OFFSET_Y);
                case BROKEN -> state.getValue(BrokenDinosaurCaptureBoxBlock.OFFSET_Y);
            };
        }

        public int offsetZ(BlockState state) {
            return switch (this) {
                case COMPLETE -> state.getValue(DinosaurCaptureCageBlock.OFFSET_Z);
                case BROKEN -> state.getValue(BrokenDinosaurCaptureBoxBlock.OFFSET_Z);
            };
        }

        public boolean controller(BlockState state) {
            return switch (this) {
                case COMPLETE -> state.getValue(DinosaurCaptureCageBlock.CONTROLLER);
                case BROKEN -> state.getValue(BrokenDinosaurCaptureBoxBlock.CONTROLLER);
            };
        }

        public boolean isControllerBlockEntity(BlockEntity blockEntity) {
            return switch (this) {
                case COMPLETE -> blockEntity instanceof DinosaurCaptureCageBlockEntity;
                case BROKEN -> blockEntity instanceof BrokenDinosaurCaptureBoxBlockEntity;
            };
        }

        public static Optional<Kind> fromState(BlockState state) {
            if (state == null) {
                return Optional.empty();
            }
            for (Kind kind : values()) {
                if (kind.matches(state)) {
                    return Optional.of(kind);
                }
            }
            return Optional.empty();
        }
    }

    public record Placement(BlockPos pos, int offsetX, int offsetY, int offsetZ) {
        public Placement {
            pos = pos.immutable();
        }

        public boolean isController() {
            return this.offsetX == 0 && this.offsetY == 0 && this.offsetZ == 0;
        }
    }
}
