package com.palos.jsrevise.compat.aeronautics;

import dev.ryanhcode.sable.companion.SableCompanion;
import dev.ryanhcode.sable.companion.SubLevelAccess;
import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaterniondc;
import org.joml.Vector3d;
import org.joml.Vector3dc;

/**
 * Snapshot of the coordinate space that contains one capture-box controller.
 *
 * <p>This bridge deliberately depends only on the Sable Companion facade. It must remain safe to load when
 * Sable, Simulated, Create, and Aeronautics are absent.</p>
 */
public final class CaptureBoxWorldContext {
    private static final double MIN_SCALE = 1.0E-9D;
    private static final double MIN_QUATERNION_LENGTH_SQUARED = 1.0E-18D;
    private static final double MIN_HORIZONTAL_NORMAL_SQUARED = 1.0E-12D;
    private static final double TICKS_PER_SECOND = 20.0D;
    static final float DEFAULT_FEET_DISTANCE_DOWN = 0.0F;

    private final SpaceIdentity spaceIdentity;
    private final BlockPos localController;
    private final AABB localAabb;
    private final AABB globalAabb;
    private final Pose3dc pose;
    private final PointVelocitySource pointVelocitySource;
    private final boolean operational;

    private CaptureBoxWorldContext(
            SpaceIdentity spaceIdentity,
            BlockPos localController,
            AABB localAabb,
            Pose3dc pose,
            PointVelocitySource pointVelocitySource,
            boolean operational
    ) {
        this.spaceIdentity = Objects.requireNonNull(spaceIdentity, "spaceIdentity");
        this.localController = Objects.requireNonNull(localController, "localController").immutable();
        this.localAabb = Objects.requireNonNull(localAabb, "localAabb");
        this.pose = pose;
        this.pointVelocitySource = Objects.requireNonNull(pointVelocitySource, "pointVelocitySource");
        this.operational = operational;
        this.globalAabb = operational ? projectAabb(localAabb, pose) : localAabb;
    }

    public static CaptureBoxWorldContext resolve(Level level, BlockPos localController, AABB localAabb) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(localController, "localController");
        Objects.requireNonNull(localAabb, "localAabb");
        ResourceLocation dimensionId = level.dimension().location();
        AeronauticsCompatibilityGate.Report compatibility =
                AeronauticsCompatibilityGate.probeRuntimeOnce();
        if (!compatibility.supported()) {
            return staticWorld(dimensionId, localController, localAabb);
        }
        try {
            SubLevelAccess subLevel = SableCompanion.INSTANCE.getContaining(level, localController);
            if (subLevel == null) {
                return staticWorld(dimensionId, localController, localAabb);
            }
            UUID sublevelId = subLevel.getUniqueId();
            Pose3dc logicalPose = subLevel.logicalPose();
            if (sublevelId == null || !isUsablePose(logicalPose)) {
                return unavailable(dimensionId, localController, localAabb);
            }
            Pose3dc poseSnapshot = new Pose3d(logicalPose);
            return create(
                    new SpaceIdentity(dimensionId, Optional.of(sublevelId)),
                    localController,
                    localAabb,
                    poseSnapshot,
                    localPoint -> toVec3(SableCompanion.INSTANCE.getVelocity(
                            level,
                            subLevel,
                            toVector(localPoint),
                            new Vector3d()
                    )),
                    true
            );
        } catch (LinkageError | RuntimeException exception) {
            return unavailable(dimensionId, localController, localAabb);
        }
    }

    public static CaptureBoxWorldContext staticWorld(
            ResourceLocation dimensionId,
            BlockPos localController,
            AABB localAabb
    ) {
        return create(
                new SpaceIdentity(dimensionId, Optional.empty()),
                localController,
                localAabb,
                null,
                ignored -> Vec3.ZERO,
                true
        );
    }

    public static Optional<SpaceIdentity> identify(Level level, BlockPos localController) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(localController, "localController");
        ResourceLocation dimensionId = level.dimension().location();
        return identify(
                dimensionId,
                AeronauticsCompatibilityGate.probeRuntimeOnce().status(),
                () -> SableCompanion.INSTANCE.getContaining(level, localController)
        );
    }

    /**
     * Returns true for a moving sublevel and for an unavailable exact-profile lookup. Callers use the latter
     * as a conservative visual/lifecycle fallback; an absent optional stack still resolves as a static world.
     */
    public static boolean isSublevelOrUncertain(Level level, BlockPos localController) {
        try {
            Optional<SpaceIdentity> identity = identify(level, localController);
            return identity.isEmpty() || identity.orElseThrow().sublevelId().isPresent();
        } catch (LinkageError | RuntimeException exception) {
            return true;
        }
    }

    static Optional<SpaceIdentity> identify(
            ResourceLocation dimensionId,
            AeronauticsCompatibilityGate.Status compatibility,
            CompanionLookup lookup
    ) {
        Objects.requireNonNull(dimensionId, "dimensionId");
        Objects.requireNonNull(compatibility, "compatibility");
        Objects.requireNonNull(lookup, "lookup");
        if (compatibility != AeronauticsCompatibilityGate.Status.SUPPORTED) {
            return Optional.of(new SpaceIdentity(dimensionId, Optional.empty()));
        }
        try {
            SubLevelAccess subLevel = lookup.find();
            if (subLevel == null) {
                return Optional.of(new SpaceIdentity(dimensionId, Optional.empty()));
            }
            return Optional.ofNullable(subLevel.getUniqueId())
                    .map(id -> new SpaceIdentity(dimensionId, Optional.of(id)));
        } catch (LinkageError | RuntimeException exception) {
            return Optional.empty();
        }
    }

    /**
     * Resolves the entity's tracked-or-vehicle sublevel and inverse-projects its global feet position.
     */
    public static Optional<TrackedFeet> trackedFeet(Entity entity) {
        return trackedFeet(entity, DEFAULT_FEET_DISTANCE_DOWN);
    }

    /**
     * @param distanceDown additional distance below the entity's feet; this is not a render partial tick
     */
    public static Optional<TrackedFeet> trackedFeet(Entity entity, float distanceDown) {
        Objects.requireNonNull(entity, "entity");
        ResourceLocation dimensionId = entity.level().dimension().location();
        if (!AeronauticsCompatibilityGate.probeRuntimeOnce().supported()) {
            Vec3 feet = entity.position().add(0.0D, -distanceDown, 0.0D);
            return Optional.of(new TrackedFeet(
                    new SpaceIdentity(dimensionId, Optional.empty()),
                    feet,
                    feet
            ));
        }
        try {
            SubLevelAccess subLevel = SableCompanion.INSTANCE.getTrackingOrVehicleSubLevel(entity);
            Vector3dc feet = SableCompanion.INSTANCE.getFeetPos(entity, distanceDown);
            if (!isFinite(feet)) {
                return Optional.empty();
            }
            Vec3 globalFeet = toVec3(feet);
            if (subLevel == null) {
                return Optional.of(new TrackedFeet(
                        new SpaceIdentity(dimensionId, Optional.empty()),
                        globalFeet,
                        globalFeet
                ));
            }
            UUID sublevelId = subLevel.getUniqueId();
            Pose3dc pose = subLevel.logicalPose();
            if (sublevelId == null || !isUsablePose(pose)) {
                return Optional.empty();
            }
            return Optional.of(trackedFeet(
                    new SpaceIdentity(dimensionId, Optional.of(sublevelId)),
                    globalFeet,
                    new Pose3d(pose)
            ));
        } catch (LinkageError | RuntimeException exception) {
            return Optional.empty();
        }
    }

    /**
     * Resolves a placement coordinate space for an entity and inverse-projects a global facing vector. The
     * returned context can project every prospective local structure part back into global coordinates for
     * border, permission, and distance checks.
     */
    public static Optional<TrackedPlacement> trackedPlacement(Entity entity, Vec3 globalFacing) {
        Objects.requireNonNull(entity, "entity");
        requireFinite(globalFacing, "globalFacing");
        ResourceLocation dimensionId = entity.level().dimension().location();
        AeronauticsCompatibilityGate.Report compatibility = AeronauticsCompatibilityGate.probeRuntimeOnce();
        if (!compatibility.supported()) {
            Vec3 feet = entity.position();
            CaptureBoxWorldContext context = staticWorld(
                    dimensionId,
                    BlockPos.containing(feet),
                    new AABB(BlockPos.containing(feet))
            );
            return placement(context, feet, feet, globalFacing);
        }
        try {
            SubLevelAccess subLevel = SableCompanion.INSTANCE.getTrackingOrVehicleSubLevel(entity);
            Vector3dc companionFeet = SableCompanion.INSTANCE.getFeetPos(entity, DEFAULT_FEET_DISTANCE_DOWN);
            if (!isFinite(companionFeet)) {
                return Optional.empty();
            }
            Vec3 globalFeet = toVec3(companionFeet);
            if (subLevel == null) {
                CaptureBoxWorldContext context = staticWorld(
                        dimensionId,
                        BlockPos.containing(globalFeet),
                        new AABB(BlockPos.containing(globalFeet))
                );
                return placement(context, globalFeet, globalFeet, globalFacing);
            }
            UUID sublevelId = subLevel.getUniqueId();
            Pose3dc pose = subLevel.logicalPose();
            if (sublevelId == null || !isUsablePose(pose)) {
                return Optional.empty();
            }
            Pose3dc snapshot = new Pose3d(pose);
            Vec3 localFeet = toVec3(snapshot.transformPositionInverse(toVector(globalFeet), new Vector3d()));
            BlockPos localAnchor = BlockPos.containing(localFeet);
            CaptureBoxWorldContext context = create(
                    new SpaceIdentity(dimensionId, Optional.of(sublevelId)),
                    localAnchor,
                    new AABB(localAnchor),
                    snapshot,
                    localPoint -> toVec3(SableCompanion.INSTANCE.getVelocity(
                            entity.level(),
                            subLevel,
                            toVector(localPoint),
                            new Vector3d()
                    )),
                    true
            );
            return placement(context, globalFeet, localFeet, globalFacing);
        } catch (LinkageError | RuntimeException exception) {
            return Optional.empty();
        }
    }

    /**
     * Returns an eye position in the parent world's global coordinates.
     */
    public static Optional<Vec3> globalObservationEye(Entity observer, float partialTick) {
        Objects.requireNonNull(observer, "observer");
        if (!AeronauticsCompatibilityGate.probeRuntimeOnce().supported()) {
            Vec3 eye = observer.getEyePosition(partialTick);
            return isFinite(eye) ? Optional.of(eye) : Optional.empty();
        }
        try {
            Vec3 eye = SableCompanion.INSTANCE.getEyePositionInterpolated(observer, partialTick);
            return isFinite(eye) ? Optional.of(eye) : Optional.empty();
        } catch (LinkageError | RuntimeException exception) {
            return Optional.empty();
        }
    }

    /**
     * Safe plot-grid query for common code. Absent or drifted stacks have no usable plot grid; an exact-stack
     * facade failure is conservative because treating a plot coordinate as global can strand an entity.
     */
    public static boolean isPlotGrid(Level level, Vec3 position) {
        Objects.requireNonNull(level, "level");
        requireFinite(position, "position");
        return isPlotGrid(
                AeronauticsCompatibilityGate.probeRuntimeOnce().status(),
                () -> SableCompanion.INSTANCE.isInPlotGrid(
                        level,
                        Mth.floor(position.x) >> 4,
                        Mth.floor(position.z) >> 4
                )
        );
    }

    static boolean isPlotGrid(
            AeronauticsCompatibilityGate.Status compatibility,
            PlotGridQuery query
    ) {
        Objects.requireNonNull(compatibility, "compatibility");
        Objects.requireNonNull(query, "query");
        if (compatibility != AeronauticsCompatibilityGate.Status.SUPPORTED) {
            return false;
        }
        try {
            return query.isInPlotGrid();
        } catch (LinkageError | RuntimeException exception) {
            return true;
        }
    }

    public SpaceIdentity spaceIdentity() {
        return this.spaceIdentity;
    }

    public BlockPos localController() {
        return this.localController;
    }

    public AABB localAabb() {
        return this.localAabb;
    }

    public AABB globalAabb() {
        return this.globalAabb;
    }

    public boolean operational() {
        return this.operational;
    }

    public Vec3 localToGlobal(Vec3 localPosition) {
        requireOperational();
        requireFinite(localPosition, "localPosition");
        if (this.pose == null) {
            return localPosition;
        }
        return toVec3(this.pose.transformPosition(toVector(localPosition), new Vector3d()));
    }

    public Vec3 globalToLocal(Vec3 globalPosition) {
        requireOperational();
        requireFinite(globalPosition, "globalPosition");
        if (this.pose == null) {
            return globalPosition;
        }
        return toVec3(this.pose.transformPositionInverse(toVector(globalPosition), new Vector3d()));
    }

    public Vec3 localNormalToGlobal(Vec3 localNormal) {
        requireOperational();
        requireFinite(localNormal, "localNormal");
        if (this.pose == null) {
            return localNormal;
        }
        return toVec3(this.pose.transformNormal(toVector(localNormal), new Vector3d()));
    }

    public Vec3 globalNormalToLocal(Vec3 globalNormal) {
        requireOperational();
        requireFinite(globalNormal, "globalNormal");
        if (this.pose == null) {
            return globalNormal;
        }
        return toVec3(this.pose.transformNormalInverse(toVector(globalNormal), new Vector3d()));
    }

    public float globalYaw(Direction localFacing) {
        Objects.requireNonNull(localFacing, "localFacing");
        Vec3 localNormal = new Vec3(
                localFacing.getStepX(),
                localFacing.getStepY(),
                localFacing.getStepZ()
        );
        Vec3 globalNormal = localNormalToGlobal(localNormal);
        return yawFromNormal(globalNormal, yawFromNormal(localNormal, 0.0F));
    }

    /**
     * Returns the craft's point velocity in Minecraft blocks per tick. Companion reports metres per second.
     */
    public Vec3 pointVelocityBlocksPerTick(Vec3 localPoint) {
        requireOperational();
        requireFinite(localPoint, "localPoint");
        try {
            return metersPerSecondToBlocksPerTick(this.pointVelocitySource.velocityAt(localPoint));
        } catch (LinkageError | RuntimeException exception) {
            return Vec3.ZERO;
        }
    }

    static CaptureBoxWorldContext create(
            SpaceIdentity spaceIdentity,
            BlockPos localController,
            AABB localAabb,
            Pose3dc pose,
            PointVelocitySource pointVelocitySource,
            boolean operational
    ) {
        boolean usable = operational && (pose == null || isUsablePose(pose));
        Pose3dc snapshot = usable && pose != null ? new Pose3d(pose) : null;
        return new CaptureBoxWorldContext(
                spaceIdentity,
                localController,
                localAabb,
                snapshot,
                pointVelocitySource,
                usable
        );
    }

    static TrackedFeet trackedFeet(SpaceIdentity identity, Vec3 globalFeet, Pose3dc pose) {
        Objects.requireNonNull(identity, "identity");
        requireFinite(globalFeet, "globalFeet");
        if (!isUsablePose(pose)) {
            throw new IllegalArgumentException("pose must be finite and invertible");
        }
        Vec3 localFeet = toVec3(pose.transformPositionInverse(toVector(globalFeet), new Vector3d()));
        return new TrackedFeet(identity, globalFeet, localFeet);
    }

    private static Optional<TrackedPlacement> placement(
            CaptureBoxWorldContext context,
            Vec3 globalFeet,
            Vec3 localFeet,
            Vec3 globalFacing
    ) {
        Vec3 localFacing;
        try {
            localFacing = context.globalNormalToLocal(globalFacing);
        } catch (IllegalArgumentException | IllegalStateException exception) {
            return Optional.empty();
        }
        if (!isFinite(localFacing)
                || localFacing.x * localFacing.x + localFacing.z * localFacing.z < MIN_HORIZONTAL_NORMAL_SQUARED) {
            return Optional.empty();
        }
        float yaw = yawFromNormal(localFacing, 0.0F);
        Direction horizontalFacing = Direction.fromYRot(yaw);
        return Optional.of(new TrackedPlacement(
                context,
                globalFeet,
                localFeet,
                localFacing,
                horizontalFacing
        ));
    }

    static AABB projectAabb(AABB localAabb, Pose3dc pose) {
        if (pose == null) {
            return localAabb;
        }
        double minX = Double.POSITIVE_INFINITY;
        double minY = Double.POSITIVE_INFINITY;
        double minZ = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        double maxY = Double.NEGATIVE_INFINITY;
        double maxZ = Double.NEGATIVE_INFINITY;
        for (double x : new double[]{localAabb.minX, localAabb.maxX}) {
            for (double y : new double[]{localAabb.minY, localAabb.maxY}) {
                for (double z : new double[]{localAabb.minZ, localAabb.maxZ}) {
                    Vector3d projected = pose.transformPosition(new Vector3d(x, y, z), new Vector3d());
                    minX = Math.min(minX, projected.x);
                    minY = Math.min(minY, projected.y);
                    minZ = Math.min(minZ, projected.z);
                    maxX = Math.max(maxX, projected.x);
                    maxY = Math.max(maxY, projected.y);
                    maxZ = Math.max(maxZ, projected.z);
                }
            }
        }
        return new AABB(minX, minY, minZ, maxX, maxY, maxZ);
    }

    static Vec3 metersPerSecondToBlocksPerTick(Vec3 velocity) {
        if (!isFinite(velocity)) {
            return Vec3.ZERO;
        }
        return velocity.scale(1.0D / TICKS_PER_SECOND);
    }

    static float yawFromNormal(Vec3 normal, float fallback) {
        if (!isFinite(normal)) {
            return fallback;
        }
        double horizontalSquared = normal.x * normal.x + normal.z * normal.z;
        if (horizontalSquared < MIN_HORIZONTAL_NORMAL_SQUARED) {
            return fallback;
        }
        return (float) Math.toDegrees(Math.atan2(-normal.x, normal.z));
    }

    private static CaptureBoxWorldContext unavailable(
            ResourceLocation dimensionId,
            BlockPos localController,
            AABB localAabb
    ) {
        return create(
                new SpaceIdentity(dimensionId, Optional.empty()),
                localController,
                localAabb,
                null,
                ignored -> Vec3.ZERO,
                false
        );
    }

    private void requireOperational() {
        if (!this.operational) {
            throw new IllegalStateException("capture-box world context is not operational");
        }
    }

    private static boolean isUsablePose(Pose3dc pose) {
        if (pose == null
                || !isFinite(pose.position())
                || !isFinite(pose.rotationPoint())
                || !isFinite(pose.scale())
                || !isFinite(pose.orientation())) {
            return false;
        }
        Vector3dc scale = pose.scale();
        Quaterniondc orientation = pose.orientation();
        return Math.abs(scale.x()) >= MIN_SCALE
                && Math.abs(scale.y()) >= MIN_SCALE
                && Math.abs(scale.z()) >= MIN_SCALE
                && orientation.x() * orientation.x()
                + orientation.y() * orientation.y()
                + orientation.z() * orientation.z()
                + orientation.w() * orientation.w() >= MIN_QUATERNION_LENGTH_SQUARED;
    }

    private static boolean isFinite(Vector3dc vector) {
        return vector != null
                && Double.isFinite(vector.x())
                && Double.isFinite(vector.y())
                && Double.isFinite(vector.z());
    }

    private static boolean isFinite(Quaterniondc quaternion) {
        return quaternion != null
                && Double.isFinite(quaternion.x())
                && Double.isFinite(quaternion.y())
                && Double.isFinite(quaternion.z())
                && Double.isFinite(quaternion.w());
    }

    private static boolean isFinite(Vec3 vector) {
        return vector != null
                && Double.isFinite(vector.x)
                && Double.isFinite(vector.y)
                && Double.isFinite(vector.z);
    }

    private static void requireFinite(Vec3 vector, String name) {
        if (!isFinite(vector)) {
            throw new IllegalArgumentException(name + " must be finite");
        }
    }

    private static Vector3d toVector(Vec3 vector) {
        return new Vector3d(vector.x, vector.y, vector.z);
    }

    private static Vec3 toVec3(Vector3dc vector) {
        return new Vec3(vector.x(), vector.y(), vector.z());
    }

    @FunctionalInterface
    interface PointVelocitySource {
        Vec3 velocityAt(Vec3 localPoint);
    }

    @FunctionalInterface
    interface CompanionLookup {
        SubLevelAccess find();
    }

    @FunctionalInterface
    interface PlotGridQuery {
        boolean isInPlotGrid();
    }

    public record SpaceIdentity(ResourceLocation dimensionId, Optional<UUID> sublevelId) {
        public SpaceIdentity {
            Objects.requireNonNull(dimensionId, "dimensionId");
            sublevelId = Objects.requireNonNull(sublevelId, "sublevelId");
        }
    }

    public record TrackedFeet(SpaceIdentity spaceIdentity, Vec3 globalFeet, Vec3 localFeet) {
        public TrackedFeet {
            Objects.requireNonNull(spaceIdentity, "spaceIdentity");
            requireFinite(globalFeet, "globalFeet");
            requireFinite(localFeet, "localFeet");
        }
    }

    public record TrackedPlacement(
            CaptureBoxWorldContext worldContext,
            Vec3 globalFeet,
            Vec3 localFeet,
            Vec3 localFacing,
            Direction horizontalFacing
    ) {
        public TrackedPlacement {
            Objects.requireNonNull(worldContext, "worldContext");
            requireFinite(globalFeet, "globalFeet");
            requireFinite(localFeet, "localFeet");
            requireFinite(localFacing, "localFacing");
            Objects.requireNonNull(horizontalFacing, "horizontalFacing");
            if (!horizontalFacing.getAxis().isHorizontal()) {
                throw new IllegalArgumentException("horizontalFacing must be horizontal");
            }
        }

        public SpaceIdentity spaceIdentity() {
            return this.worldContext.spaceIdentity();
        }
    }
}
