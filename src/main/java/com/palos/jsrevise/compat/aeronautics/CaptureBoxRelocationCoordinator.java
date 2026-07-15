package com.palos.jsrevise.compat.aeronautics;

import com.palos.jsrevise.JSRevise;
import com.palos.jsrevise.server.system.capture.CaptureBoxAccess;
import com.palos.jsrevise.server.system.capture.CaptureBoxAuthority;
import com.palos.jsrevise.server.system.capture.CaptureBoxStructure;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Capture-box authority transaction injected around exact Sable 2.0.3 block relocation.
 *
 * <p>The upstream method copies and removes blocks independently. This coordinator freezes the iterable,
 * snapshots each complete sixteen-part capture domain, keeps copied targets provisional, and selects exactly
 * one durable authority before source deletion. It deliberately does not claim transactionality for unrelated
 * aircraft blocks or process crashes.</p>
 */
public final class CaptureBoxRelocationCoordinator {
    private static final String TRANSFORM_CLASS =
            "dev.ryanhcode.sable.api.SubLevelAssemblyHelper$AssemblyTransform";
    private static final int MAX_DOMAINS_PER_MOVE = 32;
    private static final CaptureBoxRelocationState.State SOURCE_GUARD =
            new CaptureBoxRelocationState.State(false, true, true);
    private static final ThreadLocal<Context> ACTIVE = new ThreadLocal<>();

    private CaptureBoxRelocationCoordinator() {
    }

    public static boolean readyForCaptureRelocation() {
        return AeronauticsPatchReadiness.ready();
    }

    public static Iterable<BlockPos> prepare(ServerLevel sourceLevel, Object transform, Iterable<BlockPos> blocks) {
        Objects.requireNonNull(sourceLevel, "sourceLevel");
        Objects.requireNonNull(transform, "transform");
        List<BlockPos> frozen = freeze(blocks);
        if (!AeronauticsCompatibilityBootstrap.ready()) {
            return frozen;
        }
        if (ACTIVE.get() != null) {
            throw new IllegalStateException("nested Sable capture-box relocation is unsupported");
        }
        TransformAccess access = TransformAccess.resolve(transform);
        ServerLevel targetLevel = access.targetLevel(transform);
        Set<BlockPos> moved = Set.copyOf(frozen);
        LinkedHashMap<CaptureBoxRelocationState.StructureIdentity, CaptureBoxAccess.Resolved> domains =
                new LinkedHashMap<>();
        for (BlockPos pos : frozen) {
            CaptureBoxAccess.resolve(sourceLevel, pos).ifPresent(resolved -> domains.putIfAbsent(
                    resolved.identity(),
                    resolved
            ));
        }
        if (domains.isEmpty()) {
            return frozen;
        }
        if (domains.size() > MAX_DOMAINS_PER_MOVE) {
            throw new IllegalStateException("too many capture-box domains in one Sable relocation");
        }

        List<Transaction> transactions = new ArrayList<>(domains.size());
        try {
            for (CaptureBoxAccess.Resolved resolved : domains.values()) {
                if (!resolved.placements().stream().allMatch(placement -> moved.contains(placement.pos()))) {
                    throw new IllegalStateException("Sable relocation omitted part of a capture-box domain");
                }
                transactions.add(Transaction.prepare(sourceLevel, targetLevel, transform, access, resolved));
            }
            Context context = new Context(sourceLevel, targetLevel, transform, frozen, transactions);
            ACTIVE.set(context);
            return frozen;
        } catch (RuntimeException | LinkageError exception) {
            for (int index = transactions.size() - 1; index >= 0; index--) {
                try {
                    transactions.get(index).closeScopes();
                } catch (Throwable cleanupFailure) {
                    logCleanupFailure("Capture-box PREPARE guard cleanup failed closed", cleanupFailure);
                }
            }
            ACTIVE.remove();
            throw exception;
        }
    }

    public static void beforeSourceDeletion(ServerLevel sourceLevel, Object transform, Iterable<BlockPos> blocks) {
        Context context = ACTIVE.get();
        if (context == null) {
            return;
        }
        context.requireCall(sourceLevel, transform, blocks);
        context.decideBeforeSourceDeletion();
    }

    public static boolean shouldSkipSourceDeletion(ServerLevel sourceLevel, BlockPos sourcePos) {
        Context context = ACTIVE.get();
        if (context == null || context.sourceLevel != sourceLevel || sourcePos == null) {
            return false;
        }
        return context.shouldSkip(sourcePos);
    }

    /** Never throws; the injected exceptional exit must preserve the original upstream throwable. */
    public static void finish() {
        Context context = ACTIVE.get();
        try {
            runCleanupSafely(
                    context == null ? () -> { } : context::finishSafely,
                    context == null ? () -> { } : context::closeScopes
            );
        } finally {
            try {
                ACTIVE.remove();
            } catch (Throwable exception) {
                logCleanupFailure("Capture-box relocation ThreadLocal cleanup failed closed", exception);
            }
        }
    }

    static void runCleanupSafely(Runnable finishAction, Runnable closeAction) {
        Objects.requireNonNull(finishAction, "finishAction");
        Objects.requireNonNull(closeAction, "closeAction");
        try {
            finishAction.run();
        } catch (Throwable exception) {
            logCleanupFailure("Capture-box relocation cleanup failed closed", exception);
        } finally {
            try {
                closeAction.run();
            } catch (Throwable exception) {
                logCleanupFailure("Capture-box relocation guard cleanup failed closed", exception);
            }
        }
    }

    private static void logCleanupFailure(String message, Throwable exception) {
        try {
            JSRevise.LOGGER.error(message, exception);
        } catch (Throwable ignored) {
            // Cleanup is not allowed to replace an upstream Sable throwable, even if logging itself fails.
        }
    }

    static boolean hasActiveContextForTests() {
        return ACTIVE.get() != null;
    }

    static TestRelocation beginTestRelocation(
            ServerLevel level,
            BlockPos sourcePart,
            BlockPos targetPart,
            boolean copyOriginalToTarget
    ) {
        Objects.requireNonNull(level, "level");
        CaptureBoxAccess.Resolved source = CaptureBoxAccess.resolve(level, sourcePart).orElseThrow();
        CaptureBoxAccess.Resolved target = CaptureBoxAccess.resolve(level, targetPart).orElseThrow();
        if (source.identity().equals(target.identity()) || source.kind() != target.kind()) {
            throw new IllegalArgumentException("test relocation needs distinct canonical structures of one kind");
        }
        CaptureBoxRelocationState.Scope sourceScope = CaptureBoxRelocationState.open(
                level,
                source.identity(),
                SOURCE_GUARD
        );
        CaptureBoxRelocationState.Scope targetScope = null;
        try {
            targetScope = CaptureBoxRelocationState.open(
                    level,
                    target.identity(),
                    CaptureBoxRelocationState.State.PROVISIONAL
            );
            CaptureBoxAuthority.Result<CaptureBoxAuthority.Snapshot> snapshot =
                    CaptureBoxAuthority.snapshot(level, source.controller());
            if (!snapshot.success()) {
                throw new IllegalStateException("test source snapshot failed: " + snapshot.code());
            }
            if (copyOriginalToTarget) {
                CaptureBoxAuthority.Result<CaptureBoxAuthority.Snapshot> restored = CaptureBoxAuthority.restore(
                        level,
                        target.controller(),
                        snapshot.value()
                );
                if (!restored.success()) {
                    throw new IllegalStateException("test target copy failed: " + restored.code());
                }
            }
            Transaction transaction = new Transaction(
                    level,
                    level,
                    source.identity(),
                    target.identity(),
                    snapshot.value(),
                    stateMap(level, source.placements()),
                    stateMap(level, target.placements()),
                    sourceScope,
                    targetScope
            );
            return new TestRelocation(transaction);
        } catch (RuntimeException | LinkageError exception) {
            closeScopeSafely(targetScope, "Capture-box test target guard cleanup failed closed");
            closeScopeSafely(sourceScope, "Capture-box test source guard cleanup failed closed");
            throw exception;
        }
    }

    private static Map<BlockPos, BlockState> stateMap(
            ServerLevel level,
            Iterable<CaptureBoxStructure.Placement> placements
    ) {
        LinkedHashMap<BlockPos, BlockState> states = new LinkedHashMap<>();
        for (CaptureBoxStructure.Placement placement : placements) {
            states.put(placement.pos(), level.getBlockState(placement.pos()));
        }
        return states;
    }

    private static List<BlockPos> freeze(Iterable<BlockPos> blocks) {
        Objects.requireNonNull(blocks, "blocks");
        List<BlockPos> frozen = new ArrayList<>();
        for (BlockPos block : blocks) {
            if (block == null) {
                throw new IllegalArgumentException("Sable relocation block list contains null");
            }
            BlockPos immutable = block.immutable();
            frozen.add(immutable);
        }
        return List.copyOf(frozen);
    }

    private enum Authority {
        PREPARED,
        SOURCE,
        TARGET,
        ITEM
    }

    @FunctionalInterface
    private interface OwnedStateClearer {
        boolean clear(
                ServerLevel level,
                CaptureBoxRelocationState.StructureIdentity identity,
                Map<BlockPos, BlockState> expected
        );
    }

    private static final class Context {
        private final ServerLevel sourceLevel;
        private final ServerLevel targetLevel;
        private final Object transform;
        private final List<BlockPos> blocks;
        private final List<Transaction> transactions;
        private boolean decisionReached;

        private Context(
                ServerLevel sourceLevel,
                ServerLevel targetLevel,
                Object transform,
                List<BlockPos> blocks,
                List<Transaction> transactions
        ) {
            this.sourceLevel = sourceLevel;
            this.targetLevel = targetLevel;
            this.transform = transform;
            this.blocks = blocks;
            this.transactions = List.copyOf(transactions);
        }

        private void requireCall(ServerLevel sourceLevel, Object transform, Iterable<BlockPos> blocks) {
            if (this.sourceLevel != sourceLevel || this.transform != transform || this.blocks != blocks) {
                throw new IllegalStateException("Sable relocation callback arguments changed after PREPARE");
            }
        }

        private void decideBeforeSourceDeletion() {
            if (this.decisionReached) {
                throw new IllegalStateException("Sable relocation decision was invoked more than once");
            }
            this.decisionReached = true;
            for (Transaction transaction : this.transactions) {
                transaction.decideBeforeSourceDeletion();
            }
        }

        private boolean shouldSkip(BlockPos pos) {
            for (Transaction transaction : this.transactions) {
                if (transaction.skipSourceDeletion && transaction.sourceStates.containsKey(pos)) {
                    return true;
                }
            }
            return false;
        }

        private void finishSafely() {
            for (Transaction transaction : this.transactions) {
                try {
                    transaction.finish(this.decisionReached);
                } catch (Throwable exception) {
                    logCleanupFailure(
                            "Unable to finish capture-box relocation at "
                                    + transaction.sourceIdentity.controller(),
                            exception
                    );
                    try {
                        transaction.lastChanceSourceRescue();
                    } catch (Throwable rescueFailure) {
                        logCleanupFailure("Last-chance capture-box source rescue failed closed", rescueFailure);
                    }
                }
            }
        }

        private void closeScopes() {
            for (int index = this.transactions.size() - 1; index >= 0; index--) {
                try {
                    this.transactions.get(index).closeScopes();
                } catch (Throwable exception) {
                    logCleanupFailure("Capture-box relocation scope cleanup failed closed", exception);
                }
            }
        }
    }

    private static final class Transaction {
        private final ServerLevel sourceLevel;
        private final ServerLevel targetLevel;
        private final CaptureBoxRelocationState.StructureIdentity sourceIdentity;
        private final CaptureBoxRelocationState.StructureIdentity targetIdentity;
        private final CaptureBoxAuthority.Snapshot sourceOriginal;
        private final CaptureBoxAuthority.Snapshot targetExpected;
        private final Map<BlockPos, BlockState> sourceStates;
        private final Map<BlockPos, BlockState> targetStates;
        private final CaptureBoxRelocationState.Scope sourceScope;
        private final CaptureBoxRelocationState.Scope targetScope;
        private OwnedStateClearer ownedStateClearer = CaptureBoxRelocationCoordinator::clearOwned;
        private Authority authority = Authority.PREPARED;
        private ItemEntity recoveryCarrier;
        private boolean skipSourceDeletion = true;
        private boolean scopesClosed;

        private Transaction(
                ServerLevel sourceLevel,
                ServerLevel targetLevel,
                CaptureBoxRelocationState.StructureIdentity sourceIdentity,
                CaptureBoxRelocationState.StructureIdentity targetIdentity,
                CaptureBoxAuthority.Snapshot sourceOriginal,
                Map<BlockPos, BlockState> sourceStates,
                Map<BlockPos, BlockState> targetStates,
                CaptureBoxRelocationState.Scope sourceScope,
                CaptureBoxRelocationState.Scope targetScope
        ) {
            this.sourceLevel = sourceLevel;
            this.targetLevel = targetLevel;
            this.sourceIdentity = sourceIdentity;
            this.targetIdentity = targetIdentity;
            this.sourceOriginal = sourceOriginal.copy();
            this.targetExpected = CaptureBoxAuthority.projectRelocationTarget(sourceOriginal);
            this.sourceStates = Map.copyOf(sourceStates);
            this.targetStates = Map.copyOf(targetStates);
            this.sourceScope = sourceScope;
            this.targetScope = targetScope;
        }

        private static Transaction prepare(
                ServerLevel sourceLevel,
                ServerLevel targetLevel,
                Object transform,
                TransformAccess access,
                CaptureBoxAccess.Resolved source
        ) {
            LinkedHashMap<BlockPos, BlockState> sourceStates = new LinkedHashMap<>();
            LinkedHashMap<BlockPos, BlockState> targetStates = new LinkedHashMap<>();
            Set<BlockPos> targetUnique = new HashSet<>();
            for (CaptureBoxStructure.Placement placement : source.placements()) {
                BlockPos sourcePos = placement.pos();
                BlockPos targetPos = access.applyPos(transform, sourcePos);
                if (!targetUnique.add(targetPos)) {
                    throw new IllegalStateException("Sable transform collapsed capture-box placements");
                }
                sourceStates.put(sourcePos, sourceLevel.getBlockState(sourcePos));
                targetStates.put(targetPos, access.applyState(transform, sourceLevel.getBlockState(sourcePos)));
            }
            BlockPos targetController = access.applyPos(transform, source.controller());
            CaptureBoxWorldContext.SpaceIdentity targetSpace = CaptureBoxWorldContext
                    .identify(targetLevel, targetController)
                    .orElseThrow(() -> new IllegalStateException("target sublevel identity is unavailable"));
            CaptureBoxRelocationState.StructureIdentity targetIdentity =
                    new CaptureBoxRelocationState.StructureIdentity(
                            targetSpace,
                            targetController,
                            source.kind().token()
                    );
            if (sourceLevel == targetLevel && source.identity().equals(targetIdentity)) {
                throw new IllegalStateException("Sable relocation did not change capture-box identity");
            }
            if (targetStates.size() != CaptureBoxStructure.PART_COUNT
                    || targetStates.entrySet().stream().anyMatch(entry -> CaptureBoxAccess
                    .identityForState(targetLevel, entry.getKey(), entry.getValue())
                    .filter(targetIdentity::equals)
                    .isEmpty())) {
                throw new IllegalStateException("Sable transform did not preserve a canonical capture-box domain");
            }
            if (!targetSlotsAvailable(
                    targetStates.keySet(),
                    targetLevel::isLoaded,
                    targetLevel::getBlockState,
                    pos -> targetLevel.getBlockEntity(pos) != null
            )) {
                throw new IllegalStateException("Sable relocation target is loaded with an existing block or block entity");
            }

            CaptureBoxRelocationState.Scope sourceScope = CaptureBoxRelocationState.open(
                    sourceLevel,
                    source.identity(),
                    SOURCE_GUARD
            );
            CaptureBoxRelocationState.Scope targetScope = null;
            try {
                CaptureBoxAuthority.Result<CaptureBoxAuthority.Snapshot> snapshot =
                        CaptureBoxAuthority.snapshot(sourceLevel, source.controller());
                if (!snapshot.success()) {
                    throw new IllegalStateException("capture-box source snapshot failed: " + snapshot.code());
                }
                targetScope = CaptureBoxRelocationState.open(
                        targetLevel,
                        targetIdentity,
                        CaptureBoxRelocationState.State.PROVISIONAL
                );
                return new Transaction(
                        sourceLevel,
                        targetLevel,
                        source.identity(),
                        targetIdentity,
                        snapshot.value(),
                        sourceStates,
                        targetStates,
                        sourceScope,
                        targetScope
                );
            } catch (RuntimeException | LinkageError exception) {
                closeScopeSafely(targetScope, "Capture-box target guard cleanup failed closed");
                closeScopeSafely(sourceScope, "Capture-box source guard cleanup failed closed");
                throw exception;
            }
        }

        private void decideBeforeSourceDeletion() {
            boolean targetValid = targetMatchesSourceOriginal();
            boolean sourceValid = sourceMatchesOriginal();
            if (targetValid && commitTarget()) {
                return;
            }
            if ((sourceValid || ensureSourceAuthority()) && rollbackTargetToSource()) {
                return;
            }
            if (targetValid && commitTarget()) {
                return;
            }
            if (!rescueToItem()) {
                lastChanceSourceRescue();
            }
        }

        private boolean commitTarget() {
            if (!targetMatchesSourceOriginal()) {
                return false;
            }
            if (!CaptureBoxAuthority.equivalentIgnoringPosition(this.sourceOriginal, this.targetExpected)) {
                CaptureBoxAuthority.Result<CaptureBoxAuthority.Snapshot> projected = CaptureBoxAuthority.restore(
                        this.targetLevel,
                        this.targetIdentity.controller(),
                        this.targetExpected
                );
                if (!projected.success()
                        || !CaptureBoxAuthority.equivalentIgnoringPosition(this.targetExpected, projected.value())) {
                    return false;
                }
            }
            if (!targetMatchesExpected()) {
                return false;
            }
            if (!neutralizeSource()) {
                return false;
            }
            this.authority = Authority.TARGET;
            this.skipSourceDeletion = false;
            return verifySelectedAuthority();
        }

        private boolean rollbackTargetToSource() {
            if (!ensureSourceAuthority() || !neutralizeTargetFragment()) {
                return false;
            }
            if (!clearTargetOwned()) {
                logCleanupFailure(
                        "Capture-box target proxy cleanup left non-authoritative residue",
                        new IllegalStateException("target proxy cleanup was incomplete")
                );
            }
            this.authority = Authority.SOURCE;
            this.skipSourceDeletion = true;
            return verifySelectedAuthority();
        }

        private void finish(boolean decisionReached) {
            if (!decisionReached || this.authority == Authority.PREPARED) {
                if (!rollbackTargetToSource() && !rescueToItem()) {
                    lastChanceSourceRescue();
                }
                requireSelectedAuthority("capture-box relocation finish");
                return;
            }
            switch (this.authority) {
                case SOURCE -> {
                    if (sourceMatchesOriginal() && !targetMatchesExpected() && !trackedCarrierPresent()) {
                        if (!clearTargetOwned()) {
                            logCleanupFailure(
                                    "SOURCE authority retained with non-authoritative target residue",
                                    new IllegalStateException("target proxy cleanup was incomplete")
                            );
                        }
                    } else if (!rollbackTargetToSource() && !rescueToItem()) {
                        lastChanceSourceRescue();
                    }
                }
                case TARGET -> {
                    if (trackedCarrierPresent()) {
                        if (!settleTrackedCarrier(this.recoveryCarrier, this::ensureSourceAuthority)) {
                            throw new IllegalStateException("TARGET authority could not reconcile tracked carrier");
                        }
                    } else if (targetMatchesExpected()) {
                        if (!clearSourceOwned()
                                && !rollbackTargetToSource()
                                && !rescueToItem()) {
                            lastChanceSourceRescue();
                        }
                    } else if (!rollbackTargetToSource() && !rescueToItem()) {
                        lastChanceSourceRescue();
                    }
                }
                case ITEM -> {
                    boolean sourceCleared = clearSourceOwned();
                    boolean targetCleared = clearTargetOwned();
                    boolean sourceResidue = hasOwnedState(
                            this.sourceLevel,
                            this.sourceIdentity,
                            this.sourceStates.keySet()
                    );
                    boolean targetResidue = hasOwnedState(
                            this.targetLevel,
                            this.targetIdentity,
                            this.targetStates.keySet()
                    );
                    if (!sourceCleared || !targetCleared || sourceResidue || targetResidue) {
                        logCleanupFailure(
                                "Protected recovery carrier committed but capture-box world residue remains"
                                        + " (source=" + sourceResidue + ", target=" + targetResidue + ")",
                                new IllegalStateException("capture-box ITEM authority cleanup was incomplete")
                        );
                    }
                    if (!verifySelectedAuthority() && !settleTrackedCarrier(this.recoveryCarrier, null)) {
                        throw new IllegalStateException("ITEM authority could not be reconciled at finish");
                    }
                }
                case PREPARED -> throw new IllegalStateException("unreachable relocation state");
            }
            requireSelectedAuthority("capture-box relocation finish");
        }

        private boolean sourceMatchesOriginal() {
            CaptureBoxAuthority.Result<CaptureBoxAuthority.Snapshot> current =
                    CaptureBoxAuthority.snapshot(this.sourceLevel, this.sourceIdentity.controller());
            return current.success()
                    && CaptureBoxAuthority.equivalentIgnoringPosition(this.sourceOriginal, current.value());
        }

        private boolean targetMatchesSourceOriginal() {
            CaptureBoxAuthority.Result<CaptureBoxAuthority.Snapshot> current =
                    CaptureBoxAuthority.snapshot(this.targetLevel, this.targetIdentity.controller());
            return current.success()
                    && CaptureBoxAuthority.equivalentIgnoringPosition(this.sourceOriginal, current.value());
        }

        private boolean targetMatchesExpected() {
            CaptureBoxAuthority.Result<CaptureBoxAuthority.Snapshot> current =
                    CaptureBoxAuthority.snapshot(this.targetLevel, this.targetIdentity.controller());
            return current.success()
                    && CaptureBoxAuthority.equivalentIgnoringPosition(this.targetExpected, current.value());
        }

        private boolean ensureSourceAuthority() {
            if (!ensureStructure(this.sourceLevel, this.sourceIdentity, this.sourceStates)) {
                return false;
            }
            if (sourceMatchesOriginal()) {
                return true;
            }
            CaptureBoxAuthority.Result<CaptureBoxAuthority.Snapshot> restored = CaptureBoxAuthority.restore(
                    this.sourceLevel,
                    this.sourceIdentity.controller(),
                    this.sourceOriginal
            );
            return restored.success()
                    && CaptureBoxAuthority.equivalentIgnoringPosition(this.sourceOriginal, restored.value());
        }

        private boolean neutralizeTargetFragment() {
            return CaptureBoxAuthority.forceNeutralizeFragment(this.targetLevel, this.targetIdentity).success();
        }

        private boolean rescueToItem() {
            return rescueToItem(
                    this.targetLevel::addFreshEntity,
                    this::neutralizeTargetFragment,
                    this::neutralizeSource,
                    this::ensureSourceAuthority
            );
        }

        private boolean rescueToItem(Predicate<ItemEntity> carrierSpawner) {
            return rescueToItem(
                    carrierSpawner,
                    this::neutralizeTargetFragment,
                    this::neutralizeSource,
                    this::ensureSourceAuthority
            );
        }

        private boolean rescueToItem(
                Predicate<ItemEntity> carrierSpawner,
                BooleanSupplier targetNeutralizer,
                BooleanSupplier sourceNeutralizer,
                BooleanSupplier sourceRestorer
        ) {
            Objects.requireNonNull(carrierSpawner, "carrierSpawner");
            Objects.requireNonNull(targetNeutralizer, "targetNeutralizer");
            Objects.requireNonNull(sourceNeutralizer, "sourceNeutralizer");
            Objects.requireNonNull(sourceRestorer, "sourceRestorer");
            if (trackedCarrierPresent()) {
                return settleTrackedCarrier(this.recoveryCarrier, sourceRestorer);
            }
            CaptureBoxAuthority.Result<ItemStack> recovery = CaptureBoxAuthority.recoveryStack(this.targetExpected);
            if (!recovery.success()) {
                return false;
            }
            Vec3 local = Vec3.atCenterOf(this.targetIdentity.controller()).add(0.0D, 0.75D, 0.0D);
            Optional<Vec3> global = Optional.empty();
            try {
                CaptureBoxWorldContext worldContext = CaptureBoxWorldContext.resolve(
                        this.targetLevel,
                        this.targetIdentity.controller(),
                        new AABB(this.targetIdentity.controller())
                );
                if (worldContext.operational()) {
                    Vec3 projected = worldContext.localToGlobal(local);
                    global = validateGlobalRecoveryPoint(
                            projected,
                            () -> CaptureBoxWorldContext.isPlotGrid(this.targetLevel, projected)
                    );
                }
            } catch (RuntimeException | LinkageError ignored) {
                // Without a verified global point, the source remains the only acceptable authority.
            }
            if (global.isEmpty()) {
                return false;
            }
            Vec3 spawn = global.orElseThrow();
            ItemEntity carrier = new ItemEntity(
                    this.targetLevel,
                    spawn.x,
                    spawn.y,
                    spawn.z,
                    recovery.value().copy()
            );
            boolean spawnReturned = false;
            try {
                spawnReturned = carrierSpawner.test(carrier);
            } catch (RuntimeException | LinkageError exception) {
                logCleanupFailure("Capture-box recovery carrier spawn threw", exception);
            }
            boolean carrierSpawned = isSpawnedCarrier(this.targetLevel, carrier);
            if (!carrierSpawned) {
                if (spawnReturned) {
                    logCleanupFailure(
                            "Capture-box recovery spawner reported success without adding the carrier",
                            new IllegalStateException("recovery carrier is absent from the target level")
                    );
                }
                return false;
            }
            this.recoveryCarrier = carrier;

            boolean targetNeutralized = runRescueStep(
                    targetNeutralizer,
                    "Capture-box recovery target neutralization failed closed"
            );
            boolean sourceNeutralized = runRescueStep(
                    sourceNeutralizer,
                    "Capture-box recovery source neutralization failed closed"
            );
            if (!sourceNeutralized && !sourceMatchesOriginal() && !targetMatchesExpected()) {
                runRescueStep(
                        sourceRestorer,
                        "Capture-box recovery source restoration failed closed"
                );
            }
            if (!targetNeutralized || !sourceNeutralized) {
                // Failure booleans describe the attempted operation, not the resulting authority. Resolve from
                // exact snapshots so a false-after-write and a true-without-write are both handled safely.
                return settleTrackedCarrier(carrier, sourceRestorer);
            }
            return settleTrackedCarrier(carrier, null);
        }

        /**
         * Chooses authority from exact snapshots plus the already-spawned protected carrier. Physical proxy
         * parts are cleanup residue only: once their controller metadata is neutralized they cannot duplicate
         * the payload, even if a persistent block cleaner is temporarily unable to remove them.
         */
        private boolean settleTrackedCarrier(ItemEntity carrier, BooleanSupplier sourceRestorer) {
            if (!trackedCarrierMatchesExpected(carrier)) {
                return false;
            }
            boolean sourceAuthority = sourceMatchesOriginal();
            boolean targetAuthority = targetMatchesExpected();

            if (!sourceAuthority && !targetAuthority && sourceRestorer != null) {
                runRescueStep(sourceRestorer, "Capture-box recovery source restoration failed closed");
                sourceAuthority = sourceMatchesOriginal();
                targetAuthority = targetMatchesExpected();
            }

            if (sourceAuthority && targetAuthority) {
                // Prefer the original source when both exact snapshots survived. If target neutralization cannot
                // be re-established, try the inverse and preserve the exact projected target instead.
                runRescueStep(
                        this::neutralizeTargetFragment,
                        "Capture-box recovery fallback target neutralization failed closed"
                );
                sourceAuthority = sourceMatchesOriginal();
                targetAuthority = targetMatchesExpected();
                if (sourceAuthority && targetAuthority) {
                    runRescueStep(
                            this::neutralizeSource,
                            "Capture-box recovery fallback source neutralization failed closed"
                    );
                    sourceAuthority = sourceMatchesOriginal();
                    targetAuthority = targetMatchesExpected();
                }
            }

            if (sourceAuthority && !targetAuthority) {
                return selectWorldAuthority(Authority.SOURCE, carrier);
            }
            if (targetAuthority && !sourceAuthority) {
                return selectWorldAuthority(Authority.TARGET, carrier);
            }
            if (!sourceAuthority && !targetAuthority) {
                return selectItemAuthority(carrier);
            }
            return false;
        }

        private boolean selectWorldAuthority(Authority selected, ItemEntity carrier) {
            if (selected != Authority.SOURCE && selected != Authority.TARGET) {
                throw new IllegalArgumentException("world authority must be SOURCE or TARGET");
            }
            if (!discardCarrier(carrier)) {
                // A carrier that cannot be discarded remains authoritative. Neutralize the selected world copy
                // and fall back to ITEM rather than returning with two tracked authorities.
                boolean neutralized = selected == Authority.SOURCE
                        ? runRescueStep(
                        this::neutralizeSource,
                        "Capture-box recovery source neutralization after carrier-discard failure failed closed"
                )
                        : runRescueStep(
                        this::neutralizeTargetFragment,
                        "Capture-box recovery target neutralization after carrier-discard failure failed closed"
                );
                return neutralized && selectItemAuthority(carrier);
            }
            this.authority = selected;
            this.skipSourceDeletion = selected == Authority.SOURCE;
            if (selected == Authority.SOURCE) {
                if (!clearTargetOwned()) {
                    logCleanupFailure(
                            "SOURCE authority selected with non-authoritative target residue",
                            new IllegalStateException("target proxy cleanup was incomplete")
                    );
                }
            } else if (!clearSourceOwned()) {
                logCleanupFailure(
                        "TARGET authority selected with non-authoritative source residue",
                        new IllegalStateException("source proxy cleanup was incomplete")
                );
            }
            return verifySelectedAuthority();
        }

        private boolean selectItemAuthority(ItemEntity carrier) {
            if (!trackedCarrierMatchesExpected(carrier)
                    || sourceMatchesOriginal()
                    || targetMatchesExpected()) {
                return false;
            }
            this.authority = Authority.ITEM;
            this.skipSourceDeletion = false;
            if (!clearSourceOwned()) {
                logCleanupFailure(
                        "ITEM authority selected with non-authoritative source residue",
                        new IllegalStateException("source proxy cleanup was incomplete")
                );
            }
            if (!clearTargetOwned()) {
                logCleanupFailure(
                        "ITEM authority selected with non-authoritative target residue",
                        new IllegalStateException("target proxy cleanup was incomplete")
                );
            }
            return verifySelectedAuthority();
        }

        private boolean discardCarrier(ItemEntity carrier) {
            try {
                carrier.remove(Entity.RemovalReason.DISCARDED);
            } catch (RuntimeException | LinkageError exception) {
                logCleanupFailure("Capture-box recovery carrier discard failed closed", exception);
                return false;
            }
            return !isSpawnedCarrier(this.targetLevel, carrier);
        }

        private static boolean isSpawnedCarrier(ServerLevel level, ItemEntity carrier) {
            return carrier != null
                    && !carrier.isRemoved()
                    && carrier.isAddedToLevel()
                    && level.getEntity(carrier.getUUID()) == carrier;
        }

        private static boolean runRescueStep(BooleanSupplier action, String failureMessage) {
            try {
                return action.getAsBoolean();
            } catch (RuntimeException | LinkageError exception) {
                logCleanupFailure(failureMessage, exception);
                return false;
            }
        }

        private boolean neutralizeSource() {
            CaptureBoxAuthority.Result<CaptureBoxAuthority.Snapshot> neutralized = CaptureBoxAuthority.forceNeutralize(
                    this.sourceLevel,
                    this.sourceIdentity.controller()
            );
            if (!neutralized.success()) {
                return false;
            }
            if (!sourceMatchesOriginal()) {
                return true;
            }

            // An already-empty complete cage serializes identically before and after payload neutralization.
            // Remove its owned proxy domain now so the exact source snapshot cannot remain authoritative while
            // the projected target is committed. A false-after-write cleaner is still accepted once the exact
            // source snapshot is gone; leftover non-controller parts carry no metadata authority.
            clearSourceOwned();
            return !sourceMatchesOriginal();
        }

        private void lastChanceSourceRescue() {
            if (trackedCarrierPresent()) {
                if (settleTrackedCarrier(this.recoveryCarrier, this::ensureSourceAuthority)) {
                    requireSelectedAuthority("last-chance carrier reconciliation");
                    return;
                }
                throw new IllegalStateException("last-chance tracked carrier reconciliation failed closed");
            }
            this.skipSourceDeletion = true;
            if (ensureSourceAuthority()) {
                boolean targetNeutralized = neutralizeTargetFragment();
                boolean targetCleared = clearTargetOwned();
                if ((targetNeutralized || targetCleared) && !targetMatchesExpected()) {
                    this.authority = Authority.SOURCE;
                    requireSelectedAuthority("last-chance source rescue");
                    return;
                }
            }
            throw new IllegalStateException("last-chance capture-box source rescue could not isolate one authority");
        }

        private boolean trackedCarrierPresent() {
            return isSpawnedCarrier(this.targetLevel, this.recoveryCarrier)
                    && CaptureBoxAuthority.isProtectedRecoveryCarrier(this.recoveryCarrier.getItem());
        }

        private boolean trackedCarrierMatchesExpected(ItemEntity carrier) {
            if (!isSpawnedCarrier(this.targetLevel, carrier)
                    || !CaptureBoxAuthority.isProtectedRecoveryCarrier(carrier.getItem())) {
                return false;
            }
            CaptureBoxAuthority.Result<ItemStack> expected = CaptureBoxAuthority.recoveryStack(this.targetExpected);
            return expected.success()
                    && carrier.getItem().getItem() == expected.value().getItem()
                    && carrier.getItem().getCount() == expected.value().getCount()
                    && carrier.getItem().getComponents().equals(expected.value().getComponents());
        }

        private boolean verifySelectedAuthority() {
            boolean sourceAuthority = sourceMatchesOriginal();
            boolean targetAuthority = targetMatchesExpected();
            boolean carrierAuthority = trackedCarrierPresent();
            int count = (sourceAuthority ? 1 : 0)
                    + (targetAuthority ? 1 : 0)
                    + (carrierAuthority ? 1 : 0);
            if (count != 1) {
                return false;
            }
            return switch (this.authority) {
                case SOURCE -> sourceAuthority && !targetAuthority && !carrierAuthority;
                case TARGET -> targetAuthority && !sourceAuthority && !carrierAuthority;
                case ITEM -> carrierAuthority
                        && trackedCarrierMatchesExpected(this.recoveryCarrier)
                        && !sourceAuthority
                        && !targetAuthority;
                case PREPARED -> false;
            };
        }

        private void requireSelectedAuthority(String operation) {
            if (!verifySelectedAuthority()) {
                throw new IllegalStateException(operation + " did not finish with exactly one exact authority");
            }
        }

        private boolean clearSourceOwned() {
            return runOwnedClear(
                    this.sourceLevel,
                    this.sourceIdentity,
                    this.sourceStates,
                    "Capture-box source structure cleanup failed closed"
            );
        }

        private boolean clearTargetOwned() {
            return runOwnedClear(
                    this.targetLevel,
                    this.targetIdentity,
                    this.targetStates,
                    "Capture-box target structure cleanup failed closed"
            );
        }

        private boolean runOwnedClear(
                ServerLevel level,
                CaptureBoxRelocationState.StructureIdentity identity,
                Map<BlockPos, BlockState> expected,
                String failureMessage
        ) {
            try {
                return this.ownedStateClearer.clear(level, identity, expected);
            } catch (RuntimeException | LinkageError exception) {
                logCleanupFailure(failureMessage, exception);
                return false;
            }
        }

        private void closeScopes() {
            if (this.scopesClosed) {
                return;
            }
            this.scopesClosed = true;
            try {
                this.targetScope.close();
            } finally {
                this.sourceScope.close();
            }
        }
    }

    static final class TestRelocation implements AutoCloseable {
        private final Transaction transaction;
        private boolean decided;
        private boolean finished;

        private TestRelocation(Transaction transaction) {
            this.transaction = transaction;
        }

        CaptureBoxAuthority.Snapshot original() {
            return this.transaction.sourceOriginal.copy();
        }

        CaptureBoxAuthority.Snapshot expected() {
            return this.transaction.targetExpected.copy();
        }

        void decideBeforeSourceDeletion() {
            if (this.decided || this.finished) {
                throw new IllegalStateException("test relocation already decided or finished");
            }
            this.transaction.decideBeforeSourceDeletion();
            this.decided = true;
        }

        boolean skipsSourceDeletion() {
            return this.transaction.skipSourceDeletion;
        }

        boolean attemptRejectedItemRescue() {
            if (this.decided || this.finished) {
                throw new IllegalStateException("test relocation already decided or finished");
            }
            return this.transaction.rescueToItem(ignored -> false);
        }

        boolean attemptInjectedItemRescue(
                Predicate<ItemEntity> carrierSpawner,
                BooleanSupplier targetNeutralizer,
                BooleanSupplier sourceNeutralizer,
                BooleanSupplier sourceRestorer
        ) {
            if (this.decided || this.finished) {
                throw new IllegalStateException("test relocation already decided or finished");
            }
            boolean accepted = this.transaction.rescueToItem(
                    carrierSpawner,
                    targetNeutralizer,
                    sourceNeutralizer,
                    sourceRestorer
            );
            if (accepted) {
                this.decided = true;
            }
            return accepted;
        }

        boolean sourceAuthoritySelected() {
            return this.transaction.authority == Authority.SOURCE;
        }

        boolean targetAuthoritySelected() {
            return this.transaction.authority == Authority.TARGET;
        }

        boolean itemAuthoritySelected() {
            return this.transaction.authority == Authority.ITEM;
        }

        boolean restoreSourceAuthorityForTests() {
            return this.transaction.ensureSourceAuthority();
        }

        void failNextSourceClearForTests(boolean throwFailure) {
            OwnedStateClearer delegate = this.transaction.ownedStateClearer;
            this.transaction.ownedStateClearer = new OwnedStateClearer() {
                private boolean failed;

                @Override
                public boolean clear(
                        ServerLevel level,
                        CaptureBoxRelocationState.StructureIdentity identity,
                        Map<BlockPos, BlockState> expected
                ) {
                    if (!this.failed && identity.equals(TestRelocation.this.transaction.sourceIdentity)) {
                        this.failed = true;
                        if (throwFailure) {
                            throw new IllegalStateException("injected source clear failure");
                        }
                        return false;
                    }
                    return delegate.clear(level, identity, expected);
                }
            };
        }

        void failAllOwnedClearsForTests(boolean throwFailure) {
            this.transaction.ownedStateClearer = (level, identity, expected) -> {
                if (throwFailure) {
                    throw new IllegalStateException("injected persistent owned-state clear failure");
                }
                return false;
            };
        }

        void finish() {
            if (this.finished) {
                return;
            }
            try {
                this.transaction.finish(this.decided);
            } finally {
                this.finished = true;
                this.transaction.closeScopes();
            }
        }

        @Override
        public void close() {
            finish();
        }
    }

    static Optional<Vec3> validateGlobalRecoveryPoint(Vec3 projected, BooleanSupplier plotGridQuery) {
        Objects.requireNonNull(projected, "projected");
        Objects.requireNonNull(plotGridQuery, "plotGridQuery");
        if (!Double.isFinite(projected.x)
                || !Double.isFinite(projected.y)
                || !Double.isFinite(projected.z)) {
            return Optional.empty();
        }
        try {
            return plotGridQuery.getAsBoolean() ? Optional.empty() : Optional.of(projected);
        } catch (RuntimeException | LinkageError exception) {
            return Optional.empty();
        }
    }

    private static void closeScopeSafely(CaptureBoxRelocationState.Scope scope, String message) {
        if (scope == null) {
            return;
        }
        try {
            scope.close();
        } catch (Throwable exception) {
            logCleanupFailure(message, exception);
        }
    }

    static boolean targetSlotsAvailable(
            Iterable<BlockPos> targets,
            Predicate<BlockPos> loaded,
            Function<BlockPos, BlockState> stateLookup,
            Predicate<BlockPos> hasBlockEntity
    ) {
        if (targets == null || loaded == null || stateLookup == null || hasBlockEntity == null) {
            return false;
        }
        HashSet<BlockPos> unique = new HashSet<>();
        for (BlockPos target : targets) {
            if (target == null
                    || !unique.add(target)
                    || !loaded.test(target)
                    || hasBlockEntity.test(target)) {
                return false;
            }
            BlockState state = stateLookup.apply(target);
            if (state == null || !state.isAir()) {
                return false;
            }
        }
        return unique.size() == CaptureBoxStructure.PART_COUNT;
    }

    private static boolean ensureStructure(
            ServerLevel level,
            CaptureBoxRelocationState.StructureIdentity identity,
            Map<BlockPos, BlockState> expected
    ) {
        for (Map.Entry<BlockPos, BlockState> entry : expected.entrySet()) {
            BlockPos pos = entry.getKey();
            BlockState actual = level.getBlockState(pos);
            if (actual.equals(entry.getValue())) {
                continue;
            }
            Optional<CaptureBoxRelocationState.StructureIdentity> actualIdentity =
                    CaptureBoxAccess.identityForState(level, pos, actual);
            if (!actual.isAir() && !actualIdentity.filter(identity::equals).isPresent()) {
                return false;
            }
            if (!level.setBlock(pos, entry.getValue(), 3)) {
                return false;
            }
        }
        if (level.getBlockEntity(identity.controller()) == null) {
            BlockState controller = expected.get(identity.controller());
            if (controller == null
                    || !level.setBlock(identity.controller(), Blocks.AIR.defaultBlockState(), 3)
                    || !level.setBlock(identity.controller(), controller, 3)) {
                return false;
            }
        }
        CaptureBoxAccess.invalidateCapabilitiesFromState(
                level,
                identity.controller(),
                expected.get(identity.controller())
        );
        return CaptureBoxAccess.resolveIncludingProvisional(level, identity.controller())
                .map(resolved -> resolved.identity().equals(identity))
                .orElse(false);
    }

    private static boolean clearOwned(
            ServerLevel level,
            CaptureBoxRelocationState.StructureIdentity identity,
            Map<BlockPos, BlockState> expected
    ) {
        for (BlockPos pos : expected.keySet()) {
            BlockState state = level.getBlockState(pos);
            if (state.isAir()) {
                continue;
            }
            if (CaptureBoxAccess.identityForState(level, pos, state).filter(identity::equals).isPresent()) {
                if (level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3)) {
                    level.invalidateCapabilities(pos);
                }
            }
        }
        return !hasOwnedState(level, identity, expected.keySet());
    }

    private static boolean hasOwnedState(
            ServerLevel level,
            CaptureBoxRelocationState.StructureIdentity identity,
            Set<BlockPos> positions
    ) {
        for (BlockPos pos : positions) {
            BlockState state = level.getBlockState(pos);
            if (!state.isAir()
                    && CaptureBoxAccess.identityForState(level, pos, state).filter(identity::equals).isPresent()) {
                return true;
            }
        }
        return false;
    }

    private record TransformAccess(Method targetLevel, Method applyPos, Method applyState) {
        private static TransformAccess resolve(Object transform) {
            Class<?> type = transform.getClass();
            if (!TRANSFORM_CLASS.equals(type.getName())) {
                throw new IllegalStateException("unexpected Sable AssemblyTransform " + type.getName());
            }
            try {
                return new TransformAccess(
                        type.getMethod("getLevel"),
                        type.getMethod("apply", BlockPos.class),
                        type.getMethod("apply", BlockState.class)
                );
            } catch (NoSuchMethodException exception) {
                throw new IllegalStateException("Sable AssemblyTransform descriptor drifted", exception);
            }
        }

        private ServerLevel targetLevel(Object transform) {
            return invoke(this.targetLevel, transform, ServerLevel.class);
        }

        private BlockPos applyPos(Object transform, BlockPos pos) {
            return invoke(this.applyPos, transform, BlockPos.class, pos).immutable();
        }

        private BlockState applyState(Object transform, BlockState state) {
            return invoke(this.applyState, transform, BlockState.class, state);
        }

        private static <T> T invoke(Method method, Object owner, Class<T> result, Object... arguments) {
            try {
                return result.cast(method.invoke(owner, arguments));
            } catch (IllegalAccessException | InvocationTargetException | ClassCastException exception) {
                throw new IllegalStateException("Sable AssemblyTransform invocation failed", exception);
            }
        }
    }
}
