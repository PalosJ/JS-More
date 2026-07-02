package com.palos.jsrevise.server.system.anesthetic;

import com.palos.jsrevise.JSRevise;
import com.palos.jsrevise.config.JSReviseConfig;
import com.palos.jsrevise.mixin.TravelersAnimalAnimationModuleAccessor;
import com.palos.jsrevise.network.SleepAnimationGuardPayload;
import com.palos.jsrevise.server.registry.JSReviseAttachments;
import com.palos.jsrevise.server.system.size.DinosaurSizeProfile;
import com.palos.jsrevise.server.system.size.DinosaurSizeSystem;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.WeakHashMap;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import jp.jurassicsaga.server.animal.entity.obj.bases.JSAnimalBase;
import jp.jurassicsaga.server.animal.entity.obj.bases.JSAquaticBase;
import jp.jurassicsaga.server.animal.entity.obj.bases.JSAvianBase;
import jp.jurassicsaga.server.animal.entity.obj.bases.JSEntityDataHolder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import travelers.azurelib.common.animation.dispatch.command.AzCommand;
import travelers.azurelib.common.animation.dispatch.command.action.AzAction;
import travelers.azurelib.common.animation.dispatch.command.action.impl.controller.AzControllerPlayAnimationSequenceAction;
import travelers.azurelib.common.animation.dispatch.command.action.impl.root.AzRootPlayAnimationSequenceAction;
import travelers.azurelib.common.animation.dispatch.command.sequence.AzAnimationSequence;
import travelers.azurelib.common.animation.dispatch.command.stage.AzAnimationStage;
import travelers.server.animal.entity.other.TravelersAnimalAnimationModule;

public final class DinosaurAnestheticSystem {
    private static final String ANESTHETIC_SLEEP_SAVE_MARKER = "jsrevise.anesthetic_sleeping";
    private static final String RAW_SLEEP_SAVE_MARKER = "jsrevise.raw_sleeping";
    private static final String JURASSIC_SAGA_NAMESPACE = "jurassicsaga";
    private static final String LUDODACTYLUS_PATH = "ludodactylus";
    private static final String DILOPHOSAURUS_PATH = "dilophosaurus";
    private static final String SLEEP_IN_LEAF = "sleep_in";
    private static final String SLEEP_LOOP_LEAF = "sleep_loop";
    private static final int SLEEP_STABILIZATION_TICKS = 3;
    private static final int CLIENT_SLEEP_ANIMATION_GUARD_TICKS = 30;
    private static final int CLIENT_SLEEP_STAGE_LOCAL_GUARD_TICKS = 10;
    private static final int MAX_CLIENT_SLEEP_ANIMATION_GUARD_TICKS = 60;
    private static final int MAX_SLEEP_ANIMATION_GUARD_ENTRIES = 512;
    private static final long CLIENT_SLEEP_ANIMATION_GUARD_RESEND_TICKS = 10L;
    private static final long DEBUG_SLEEP_LOG_INTERVAL_TICKS = 100L;
    private static final Map<Integer, Long> CLIENT_SLEEP_ANIMATION_GUARDS = new HashMap<>();
    private static final Map<Integer, Boolean> CLIENT_SLEEP_ANIMATION_GUARD_SEEN_SLEEP = new HashMap<>();
    private static Object clientSleepAnimationGuardCurrentSessionKey;
    private static long clientSleepAnimationGuardLastGameTime = Long.MIN_VALUE;
    private static final Map<JSAnimalBase, Long> SERVER_SLEEP_ANIMATION_GUARD_SYNC_TICKS = new WeakHashMap<>();
    private static final Map<JSAnimalBase, Boolean> SLEEP_ANIMATION_TAKEOVERS = new WeakHashMap<>();
    private static final Map<JSAnimalBase, Long> SLEEP_STABILIZATION_EXPIRE_TICKS = new WeakHashMap<>();
    private static final Map<String, Long> DEBUG_SLEEP_LOG_TICKS = new HashMap<>();

    private DinosaurAnestheticSystem() {
    }

    public static void applyAnesthetic(JSAnimalBase animal) {
        if (isUsable(animal)) {
            AnestheticStateService.applyImmediately(animal);
        }
    }

    public static void applyAnestheticInjection(JSAnimalBase animal) {
        if (isUsable(animal)) {
            AnestheticStateService.queueInjection(animal);
        }
    }

    public static boolean tryApplyAnestheticInjection(JSAnimalBase animal) {
        if (!isUsable(animal)) {
            return false;
        }
        applyAnestheticInjection(animal);
        return true;
    }

    public static boolean isAnesthetized(JSAnimalBase animal) {
        return isUsable(animal) && AnestheticStateService.isActive(animal);
    }

    public static CompoundTag saveRelativeAnestheticState(JSAnimalBase animal) {
        if (!isUsable(animal)) {
            return new CompoundTag();
        }
        AnestheticData data = animal.getExistingDataOrNull(JSReviseAttachments.ANESTHETIC);
        return data == null ? new CompoundTag() : data.serializeRelativeNBT(animal.level().getGameTime());
    }

    public static void restoreRelativeAnestheticState(
            JSAnimalBase animal,
            CompoundTag relativeTag,
            long capturedGameTime
    ) {
        if (!isUsable(animal) || animal.level().isClientSide) {
            return;
        }
        if (relativeTag == null || relativeTag.isEmpty()) {
            animal.removeData(JSReviseAttachments.ANESTHETIC);
            animal.removeData(JSReviseAttachments.ANESTHETIC_FLOAT);
            return;
        }
        long currentGameTime = animal.level().getGameTime();
        long baseGameTime = Math.min(Math.max(0L, capturedGameTime), Math.max(0L, currentGameTime));
        AnestheticData data = animal.getData(JSReviseAttachments.ANESTHETIC);
        data.deserializeRelativeNBT(null, relativeTag, baseGameTime, currentGameTime);
        if (data.isEmpty()) {
            animal.removeData(JSReviseAttachments.ANESTHETIC);
            animal.removeData(JSReviseAttachments.ANESTHETIC_FLOAT);
        } else {
            animal.syncData(JSReviseAttachments.ANESTHETIC);
        }
    }

    public static boolean isFloating(JSAnimalBase animal) {
        AnestheticFloatData data = animal.getExistingDataOrNull(JSReviseAttachments.ANESTHETIC_FLOAT);
        return isAnesthetized(animal) && data != null && data.isFloating();
    }

    public static AnestheticVisualState resolveVisualState(JSAnimalBase animal) {
        AnestheticFloatData data = animal == null
                ? null
                : animal.getExistingDataOrNull(JSReviseAttachments.ANESTHETIC_FLOAT);
        if (data == null || !data.isFloating()) {
            return null;
        }
        DinosaurSizeProfile profile = DinosaurSizeSystem.resolveProfile(animal);
        return new AnestheticVisualState(
                data.phase(),
                data.targetBaseY(),
                data.fluidSurfaceY(),
                AnestheticFloatScaling.renderExposureHeight(animal, profile),
                AnestheticFloatScaling.bobbingAmplitude(animal, profile),
                AnestheticFloatScaling.bobbingCycleTicks(animal, profile),
                data.bobbingStartedAt()
        );
    }

    public static boolean shouldBridgeSleepState(JSAnimalBase animal) {
        return isUsable(animal) && AnestheticStateService.isActiveOrReady(animal);
    }

    public static void rememberClientSleepAnimationGuard(JSAnimalBase animal, int durationTicks) {
        if (animal == null) {
            return;
        }
        rememberClientSleepAnimationGuard(animal.level(), animal.getId(), durationTicks);
    }

    public static void rememberClientSleepAnimationGuard(Level level, int entityId, int durationTicks) {
        int safeDurationTicks = sanitizeClientSleepAnimationGuardDuration(durationTicks);
        if (level == null || !level.isClientSide || entityId < 0) {
            return;
        }
        if (safeDurationTicks <= 0) {
            forgetClientSleepAnimationGuard(level, entityId);
            logSleepAnimationTrace(
                    "client_guard_cleared",
                    level,
                    entityId,
                    "duration=" + durationTicks
            );
            return;
        }
        long gameTime = level.getGameTime();
        long expireTick = clientSleepAnimationGuardExpireTick(gameTime, safeDurationTicks);
        Object sessionKey = clientSleepAnimationGuardSessionKey(level);
        synchronized (CLIENT_SLEEP_ANIMATION_GUARDS) {
            refreshClientSleepAnimationGuardSession(sessionKey, gameTime);
            cleanupClientSleepAnimationGuards(gameTime);
            if (!CLIENT_SLEEP_ANIMATION_GUARDS.containsKey(entityId)
                    && CLIENT_SLEEP_ANIMATION_GUARDS.size() >= MAX_SLEEP_ANIMATION_GUARD_ENTRIES) {
                return;
            }
            CLIENT_SLEEP_ANIMATION_GUARDS.put(entityId, expireTick);
        }
        logSleepAnimationTrace(
                "client_guard_remembered",
                level,
                entityId,
                "duration=" + safeDurationTicks + " expire=" + expireTick
        );
    }

    public static void forgetClientSleepAnimationGuard(JSAnimalBase animal) {
        if (animal == null) {
            return;
        }
        forgetClientSleepAnimationGuard(animal.level(), animal.getId());
    }

    public static void forgetClientSleepAnimationGuard(Level level, int entityId) {
        if (level == null || entityId < 0) {
            return;
        }
        long gameTime = level.getGameTime();
        Object sessionKey = level.isClientSide ? clientSleepAnimationGuardSessionKey(level) : null;
        synchronized (CLIENT_SLEEP_ANIMATION_GUARDS) {
            if (sessionKey != null) {
                refreshClientSleepAnimationGuardSession(sessionKey, gameTime);
            }
            CLIENT_SLEEP_ANIMATION_GUARDS.remove(entityId);
            CLIENT_SLEEP_ANIMATION_GUARD_SEEN_SLEEP.remove(entityId);
        }
    }

    public static void clearClientSleepAnimationGuards() {
        synchronized (CLIENT_SLEEP_ANIMATION_GUARDS) {
            CLIENT_SLEEP_ANIMATION_GUARDS.clear();
            CLIENT_SLEEP_ANIMATION_GUARD_SEEN_SLEEP.clear();
            clientSleepAnimationGuardCurrentSessionKey = null;
            clientSleepAnimationGuardLastGameTime = Long.MIN_VALUE;
        }
    }

    public static int sanitizeClientSleepAnimationGuardDuration(int durationTicks) {
        if (durationTicks <= 0) {
            return 0;
        }
        return Math.min(durationTicks, MAX_CLIENT_SLEEP_ANIMATION_GUARD_TICKS);
    }

    static long clientSleepAnimationGuardExpireTick(long gameTime, int durationTicks) {
        int safeDurationTicks = sanitizeClientSleepAnimationGuardDuration(durationTicks);
        if (safeDurationTicks <= 0) {
            return gameTime;
        }
        return gameTime > Long.MAX_VALUE - safeDurationTicks
                ? Long.MAX_VALUE
                : gameTime + safeDurationTicks;
    }

    static boolean isClientSleepAnimationGuardActive(long gameTime, long expireTick) {
        return expireTick > gameTime;
    }

    static boolean shouldKeepClientSleepAnimationGuard(
            Object rememberedSessionKey,
            Object currentSessionKey,
            long rememberedGameTime,
            long gameTime,
            Long expireTick
    ) {
        return expireTick != null
                && rememberedSessionKey != null
                && !shouldResetClientSleepAnimationGuardSession(
                        rememberedSessionKey,
                        currentSessionKey,
                        rememberedGameTime,
                        gameTime
                )
                && isClientSleepAnimationGuardActive(gameTime, expireTick);
    }

    static boolean shouldResetClientSleepAnimationGuardSession(
            Object rememberedSessionKey,
            Object currentSessionKey,
            long rememberedGameTime,
            long gameTime
    ) {
        return rememberedSessionKey != null
                && (!Objects.equals(rememberedSessionKey, currentSessionKey) || gameTime < rememberedGameTime);
    }

    static long sleepStabilizationExpireTick(long gameTime, int durationTicks) {
        if (durationTicks <= 0) {
            return gameTime;
        }
        return gameTime > Long.MAX_VALUE - durationTicks
                ? Long.MAX_VALUE
                : gameTime + durationTicks;
    }

    static boolean isSleepStabilizationActive(long gameTime, long expireTick) {
        return expireTick > gameTime;
    }

    static boolean shouldStartSleepAnimationTakeover(boolean guardActive, boolean takeoverRemembered) {
        return guardActive && !takeoverRemembered;
    }

    static boolean shouldUseSleepAnimationGuard(
            boolean bridgeSleepState,
            boolean rawSleeping,
            boolean clientGuard,
            boolean stabilizationActive
    ) {
        return bridgeSleepState || rawSleeping || clientGuard || stabilizationActive;
    }

    static boolean shouldPreventRawSleepClear(
            boolean sleeping,
            boolean bridgeSleepState,
            boolean stabilizationActive
    ) {
        return !sleeping && (bridgeSleepState || stabilizationActive);
    }

    static boolean shouldLogSleepAnimationTrace(long gameTime, Long lastLogTick) {
        return lastLogTick == null || elapsedSince(gameTime, lastLogTick) >= DEBUG_SLEEP_LOG_INTERVAL_TICKS;
    }

    static int clearNonSleepAnimationTransitions(Map<String, ?> animationMap) {
        if (animationMap == null || animationMap.isEmpty()) {
            return 0;
        }
        int previousSize = animationMap.size();
        animationMap.keySet().removeIf(key -> !isSleepAnimationTransitionKey(key));
        return previousSize - animationMap.size();
    }

    static boolean shouldPrepareClientSleepStageGuard(List<String> stageNames) {
        if (stageNames == null || stageNames.isEmpty()) {
            return false;
        }
        for (String stageName : stageNames) {
            if (isSleepEntryOrLoopAnimation(stageName)) {
                return true;
            }
        }
        return false;
    }

    static int clientSleepStageLocalGuardDurationTicks() {
        return sanitizeClientSleepAnimationGuardDuration(CLIENT_SLEEP_STAGE_LOCAL_GUARD_TICKS);
    }

    static boolean isSleepAnimationTransitionKey(String animationKey) {
        if (animationKey == null || animationKey.isBlank()) {
            return false;
        }
        String normalizedKey = animationKey.toLowerCase(Locale.ROOT);
        return containsAnimationLeaf(normalizedKey, "sleep_in")
                && containsAnimationLeaf(normalizedKey, "sleep_loop")
                && containsAnimationLeaf(normalizedKey, "sleep_out");
    }

    private static boolean containsAnimationLeaf(String normalizedName, String leafName) {
        String[] parts = normalizedName.split("[.:]");
        for (String part : parts) {
            if (leafName.equals(part)) {
                return true;
            }
        }
        return false;
    }

    public static void prepareAnimationSleepState(JSAnimalBase animal) {
        if (!isUsable(animal)) {
            return;
        }
        if (!animal.level().isClientSide && animal.hasData(JSReviseAttachments.ANESTHETIC)) {
            AnestheticStateService.update(animal);
        }
        if (!shouldBridgeSleepState(animal)) {
            logSleepAnimationTrace(animal, "prepare_anesthetic_sleep_skipped", () -> sleepTraceState(animal));
            return;
        }
        forceSleepState(animal);
        logSleepAnimationTrace(animal, "prepare_anesthetic_sleep", () -> sleepTraceState(animal));
    }

    public static void keepAnimationSleepState(JSAnimalBase animal) {
        if (isUsable(animal) && (shouldBridgeSleepState(animal) || isSleepStabilizationActive(animal))) {
            forceSleepState(animal);
        }
    }

    public static void prepareNaturalSleepState(JSAnimalBase animal, BooleanSupplier canSleep) {
        if (!isUsable(animal)
                || animal.isMoving()
                || !animal.shouldSleep()
                || animal.isSleeping()
                || !canSleep.getAsBoolean()) {
            logSleepAnimationTrace(animal, "prepare_natural_sleep_skipped", () -> sleepTraceState(animal));
            return;
        }
        animal.setSleeping(true);
        rememberSleepStabilizationIfAbsent(animal, "natural_prepare");
        logSleepAnimationTrace(animal, "prepare_natural_sleep", () -> sleepTraceState(animal));
    }

    static boolean shouldPrepareNaturalSleepState(
            boolean moving,
            boolean shouldSleep,
            boolean sleeping,
            boolean canSleep
    ) {
        return !moving && shouldSleep && !sleeping && canSleep;
    }

    private static void forceSleepState(JSAnimalBase animal) {
        animal.setSleeping(true);
        if (animal instanceof JSAvianBase avian) {
            stabilizeAvianSleepStateIfGuarded(avian);
        }
    }

    public static boolean prepareNativeSleepAnimationGuard(
            JSAnimalBase animal,
            TravelersAnimalAnimationModule animationModule
    ) {
        return prepareNativeSleepAnimationGuard(animal, animationModule, false);
    }

    /**
     * @deprecated Use {@link #prepareNativeSleepAnimationGuard(JSAnimalBase, TravelersAnimalAnimationModule)}.
     */
    @Deprecated(forRemoval = false)
    public static boolean playAnestheticSleepAnimation(JSAnimalBase animal, TravelersAnimalAnimationModule animationModule) {
        return prepareNativeSleepAnimationGuard(animal, animationModule);
    }

    /**
     * @deprecated Use {@link #prepareNativeSleepAnimationGuard(JSAnimalBase, TravelersAnimalAnimationModule)}.
     */
    @Deprecated(forRemoval = false)
    public static boolean playGuardedSleepAnimation(JSAnimalBase animal, TravelersAnimalAnimationModule animationModule) {
        return prepareNativeSleepAnimationGuard(animal, animationModule);
    }

    private static boolean prepareNativeSleepAnimationGuard(
            JSAnimalBase animal,
            TravelersAnimalAnimationModule animationModule,
            boolean forceGuardSync
    ) {
        if (!isUsable(animal)) {
            return false;
        }
        if (!animal.level().isClientSide) {
            prepareAnimationSleepState(animal);
        }
        if (animal instanceof JSAvianBase avian) {
            stabilizeAvianSleepStateIfGuarded(avian);
        }
        if (!shouldUseSleepAnimationGuard(animal)) {
            forgetSleepAnimationTakeover(animal);
            logSleepAnimationTrace(animal, "sleep_guard_skipped", () -> sleepTraceState(animal));
            return false;
        }

        boolean takeover = false;
        if (animationModule != null) {
            takeover = rememberSleepAnimationTakeover(animal);
            if (takeover) {
                takeOverSleepAnimation(animal, animationModule);
            }
        }
        sendClientSleepAnimationGuardIfNeeded(animal, forceGuardSync);
        boolean takeoverStarted = takeover;
        logSleepAnimationTrace(
                animal,
                "sleep_guard_prepared",
                () -> sleepTraceState(animal) + " takeover=" + takeoverStarted + " forcedSync=" + forceGuardSync
        );
        return true;
    }

    public static boolean shouldUseSleepAnimationGuard(JSAnimalBase animal) {
        return shouldUseSleepAnimationGuard(
                shouldBridgeSleepState(animal),
                isRawSleeping(animal),
                hasClientSleepAnimationGuard(animal),
                isSleepStabilizationActive(animal)
        );
    }

    public static boolean shouldSkipClientProceduralAnimation(JSAnimalBase animal) {
        return shouldUseSleepAnimationGuard(animal);
    }

    public static boolean shouldRedirectKnownUpstreamSleepInFlashbackToLoop(JSAnimalBase animal, String animationName) {
        if (!shouldUseSleepAnimationGuard(animal)) {
            return false;
        }
        ResourceLocation entityId = BuiltInRegistries.ENTITY_TYPE.getKey(animal.getType());
        boolean redirect = shouldRedirectKnownUpstreamSleepInFlashbackToLoop(
                true,
                entityId == null ? null : entityId.getNamespace(),
                entityId == null ? null : entityId.getPath(),
                animationName
        );
        if (redirect) {
            logSleepAnimationTrace(
                    animal,
                    "upstream_sleep_in_flashback_redirect",
                    () -> sleepTraceState(animal) + " animation=" + animationName + " replacement=" + SLEEP_LOOP_LEAF
            );
        }
        return redirect;
    }

    public static boolean shouldRedirectLudodactylusSleepInToLoop(JSAnimalBase animal, String animationName) {
        return shouldRedirectKnownUpstreamSleepInFlashbackToLoop(animal, animationName);
    }

    static boolean shouldRedirectKnownUpstreamSleepInFlashbackToLoop(
            boolean guardActive,
            String speciesNamespace,
            String speciesPath,
            String animationName
    ) {
        return guardActive
                && isKnownUpstreamSleepInFlashbackCase(speciesNamespace, speciesPath)
                && SLEEP_IN_LEAF.equals(animationLeafName(animationName));
    }

    static boolean shouldRedirectLudodactylusSleepInToLoop(
            boolean guardActive,
            String speciesNamespace,
            String speciesPath,
            String animationName
    ) {
        return shouldRedirectKnownUpstreamSleepInFlashbackToLoop(
                guardActive,
                speciesNamespace,
                speciesPath,
                animationName
        );
    }

    private static boolean isKnownUpstreamSleepInFlashbackCase(String speciesNamespace, String speciesPath) {
        return JURASSIC_SAGA_NAMESPACE.equals(speciesNamespace)
                && (LUDODACTYLUS_PATH.equals(speciesPath) || DILOPHOSAURUS_PATH.equals(speciesPath));
    }

    public static boolean shouldBlockNonSleepAnimation(JSAnimalBase animal, String animationName) {
        if (!shouldBlockOrdinaryAnimation(animal)) {
            return false;
        }
        boolean blocked = !allowsSleepOrDeathAnimation(animationName);
        if (blocked) {
            logSleepAnimationTrace(
                    animal,
                    "ordinary_animation_blocked",
                    () -> sleepTraceState(animal) + " animation=" + animationName
            );
        }
        return blocked;
    }

    static boolean allowsSleepOrDeathAnimation(String animationName) {
        String leafName = animationLeafName(animationName);
        return isSleepEntryOrLoopLeaf(leafName) || leafName.contains("death");
    }

    static boolean isSleepEntryOrLoopAnimation(String animationName) {
        return isSleepEntryOrLoopLeaf(animationLeafName(animationName));
    }

    private static boolean isSleepEntryOrLoopLeaf(String leafName) {
        return leafName.equals(SLEEP_IN_LEAF) || leafName.equals(SLEEP_LOOP_LEAF);
    }

    private static String animationLeafName(String animationName) {
        if (animationName == null || animationName.isBlank()) {
            return "";
        }
        String normalizedName = animationName.toLowerCase(Locale.ROOT);
        int separatorIndex = Math.max(normalizedName.lastIndexOf('.'), normalizedName.lastIndexOf(':'));
        return separatorIndex >= 0 ? normalizedName.substring(separatorIndex + 1) : normalizedName;
    }

    public static boolean prepareClientSleepAnimationGuard(JSAnimalBase animal, AzCommand command) {
        if (!isUsable(animal) || command == null) {
            return false;
        }
        return prepareClientSleepAnimationGuard(animal, clientAnimationStageNames(command));
    }

    private static boolean prepareClientSleepAnimationGuard(JSAnimalBase animal, List<String> stageNames) {
        if (!isUsable(animal)
                || !animal.level().isClientSide
                || !shouldPrepareClientSleepStageGuard(stageNames)) {
            return false;
        }
        int durationTicks = clientSleepStageLocalGuardDurationTicks();
        rememberClientSleepAnimationGuard(animal, durationTicks);
        if (animal instanceof JSAvianBase avian) {
            stabilizeAvianSleepStateIfGuarded(avian);
        }
        logSleepAnimationTrace(
                animal,
                "client_sleep_stage_guarded",
                () -> sleepTraceState(animal) + " stages=" + stageNames + " duration=" + durationTicks
        );
        return true;
    }

    public static boolean shouldBlockClientAnimationCommand(JSAnimalBase animal, AzCommand command) {
        if (!shouldUseSleepAnimationGuard(animal) || command == null) {
            return false;
        }
        List<String> stageNames = clientAnimationStageNames(command);
        boolean blocked = shouldBlockClientAnimationStages(true, stageNames);
        if (blocked) {
            logSleepAnimationTrace(
                    animal,
                    "client_azure_animation_blocked",
                    () -> sleepTraceState(animal) + " stages=" + stageNames
            );
        } else if (!stageNames.isEmpty()) {
            logSleepAnimationTrace(
                    animal,
                    "client_azure_animation_allowed",
                    () -> sleepTraceState(animal) + " stages=" + stageNames
            );
        }
        return blocked;
    }

    static boolean shouldBlockClientAnimationStages(boolean guardActive, List<String> stageNames) {
        if (!guardActive || stageNames == null || stageNames.isEmpty()) {
            return false;
        }
        for (String stageName : stageNames) {
            if (!allowsSleepOrDeathAnimation(stageName)) {
                return true;
            }
        }
        return false;
    }

    private static boolean shouldBlockOrdinaryAnimation(JSAnimalBase animal) {
        return shouldUseSleepAnimationGuard(animal);
    }

    public static void syncSleepStateForTracking(JSAnimalBase animal) {
        if (!isUsable(animal) || animal.level().isClientSide) {
            return;
        }
        prepareAnimationSleepState(animal);
        boolean bridgeSleepState = shouldBridgeSleepState(animal);
        if (bridgeSleepState) {
            animal.syncData(JSReviseAttachments.ANESTHETIC);
            animal.syncData(JSReviseAttachments.ANESTHETIC_FLOAT);
        }
        if (!bridgeSleepState && !isRawSleeping(animal)) {
            return;
        }
        prepareNativeSleepAnimationGuard(animal, animal.getAnimationModule(), true);
        logSleepAnimationTrace(animal, "tracking_sleep_sync", () -> sleepTraceState(animal));
    }

    public static boolean shouldSuppressTravelersControllers(JSAnimalBase animal) {
        return shouldBridgeSleepState(animal);
    }

    public static boolean shouldSuppressMovement(JSAnimalBase animal) {
        return shouldBridgeSleepState(animal);
    }

    public static Vec3 filterTravelInput(JSAnimalBase animal, Vec3 travelInput) {
        return shouldSuppressMovement(animal) ? Vec3.ZERO : travelInput;
    }

    public static boolean shouldExposeAvianSleepState(JSAvianBase avian) {
        if (shouldBridgeSleepState(avian)) {
            stabilizeAvianSleepStateIfGuarded(avian);
            return true;
        }
        if (!isRawSleeping(avian)) {
            return false;
        }
        stabilizeAvianSleepStateIfGuarded(avian);
        return true;
    }

    public static boolean shouldSuppressAvianFlightControl(JSAvianBase avian) {
        return shouldStabilizeAvianSleepState(avian);
    }

    public static void stabilizeRawAvianSleepState(JSAvianBase avian) {
        stabilizeAvianSleepStateIfGuarded(avian);
    }

    public static void stabilizeAvianSleepStateIfGuarded(JSAvianBase avian) {
        if (shouldStabilizeAvianSleepState(avian)) {
            clearAvianFlightState(avian);
        }
    }

    public static void handleRawSleepingChanged(JSAnimalBase animal, boolean sleeping) {
        if (sleeping && animal instanceof JSAvianBase avian) {
            stabilizeAvianSleepStateIfGuarded(avian);
        }
        if (!sleeping) {
            forgetSleepStabilization(animal);
            forgetSleepAnimationTakeover(animal);
            sendClientSleepAnimationGuardClear(animal);
        } else if (!shouldBridgeSleepState(animal)) {
            rememberSleepStabilizationIfAbsent(animal, "raw_sleep_true");
        }
        logSleepAnimationTrace(animal, "raw_sleep_changed", () -> sleepTraceState(animal) + " sleeping=" + sleeping);
    }

    public static boolean shouldPreventRawSleepClear(JSAnimalBase animal, boolean sleeping) {
        boolean bridgeSleepState = shouldBridgeSleepState(animal);
        boolean stabilizationActive = isSleepStabilizationActive(animal);
        boolean prevent = shouldPreventRawSleepClear(sleeping, bridgeSleepState, stabilizationActive);
        if (prevent && !bridgeSleepState && stabilizationActive) {
            logSleepAnimationTrace(animal, "raw_sleep_clear_stabilized", () -> sleepTraceState(animal));
        }
        return prevent;
    }

    public static void writeAnestheticSleepSaveMarker(JSAnimalBase animal, CompoundTag tag) {
        if (tag == null || !isUsable(animal)) {
            return;
        }
        if (shouldBridgeSleepState(animal)) {
            tag.putBoolean(ANESTHETIC_SLEEP_SAVE_MARKER, true);
        }
        if (shouldBridgeSleepState(animal) || isRawSleeping(animal)) {
            tag.putBoolean(RAW_SLEEP_SAVE_MARKER, true);
        }
    }

    public static void restoreAnestheticSleepSaveMarker(JSAnimalBase animal, CompoundTag tag) {
        if (!isUsable(animal) || tag == null) {
            return;
        }
        boolean hasAnestheticMarker = tag.contains(ANESTHETIC_SLEEP_SAVE_MARKER, Tag.TAG_BYTE)
                && tag.getBoolean(ANESTHETIC_SLEEP_SAVE_MARKER);
        boolean hasRawMarker = tag.contains(RAW_SLEEP_SAVE_MARKER, Tag.TAG_BYTE)
                && tag.getBoolean(RAW_SLEEP_SAVE_MARKER);
        if (hasAnestheticMarker || hasRawMarker) {
            animal.setSleeping(true);
            if (animal instanceof JSAvianBase avian) {
                stabilizeAvianSleepStateIfGuarded(avian);
            }
            rememberSleepStabilizationIfAbsent(animal, hasAnestheticMarker ? "restore_anesthetic" : "restore_raw");
            logSleepAnimationTrace(animal, "restore_sleep_marker", () -> sleepTraceState(animal)
                    + " anestheticMarker=" + hasAnestheticMarker + " rawMarker=" + hasRawMarker);
        }
    }

    public static boolean handlePassiveFloatingTravel(JSAnimalBase animal) {
        if (animal instanceof JSAquaticBase || !isFloating(animal)) {
            return false;
        }
        AnestheticMovementController.moveWithPassiveHorizontalForces(animal);
        return true;
    }

    public static long getPendingAnestheticTicks(JSAnimalBase animal) {
        return AnestheticStateService.pendingTicks(animal);
    }

    public static long getRemainingAnestheticTicks(JSAnimalBase animal) {
        return AnestheticStateService.remainingTicks(animal);
    }

    public static long getQueuedAnestheticTicks(JSAnimalBase animal) {
        return AnestheticStateService.queuedTicks(animal);
    }

    public static void tickServer(JSAnimalBase animal) {
        if (!isUsable(animal) || animal.level().isClientSide) {
            return;
        }
        if (!animal.hasData(JSReviseAttachments.ANESTHETIC)) {
            return;
        }
        long gameTime = animal.level().getGameTime();
        AnestheticFloatData existingFloatData = animal.getExistingDataOrNull(JSReviseAttachments.ANESTHETIC_FLOAT);
        if (existingFloatData != null && existingFloatData.lastProcessedTick() == gameTime) {
            return;
        }

        if (!AnestheticStateService.update(animal)) {
            if (existingFloatData != null && existingFloatData.phase() != AnestheticFloatData.Phase.IDLE) {
                AnestheticMovementController.release(animal, existingFloatData);
            }
            AnestheticData anestheticData = animal.getExistingDataOrNull(JSReviseAttachments.ANESTHETIC);
            if (anestheticData != null && anestheticData.isEmpty()) {
                animal.removeData(JSReviseAttachments.ANESTHETIC);
                animal.removeData(JSReviseAttachments.ANESTHETIC_FLOAT);
            }
            return;
        }

        AnestheticFloatData floatData = existingFloatData != null
                ? existingFloatData
                : animal.getData(JSReviseAttachments.ANESTHETIC_FLOAT);
        floatData.setLastProcessedTick(gameTime);
        stabilizeCommonState(animal);
        AnestheticMovementController.tick(animal, floatData);
    }

    /**
     * @deprecated Compatibility bridge for integrations using the previous AI hook.
     */
    @Deprecated(forRemoval = false)
    public static void tickPostAi(JSAnimalBase animal) {
        tickServer(animal);
    }

    /**
     * @deprecated Compatibility bridge for integrations using the previous avian AI hook.
     */
    @Deprecated(forRemoval = false)
    public static void tickPostAvianAi(JSAvianBase avian) {
        tickServer(avian);
    }

    /**
     * @deprecated Compatibility bridge for integrations using the previous movement hook.
     */
    @Deprecated(forRemoval = false)
    public static void tickFinalizedMovement(JSAnimalBase animal) {
        tickServer(animal);
    }

    private static void stabilizeCommonState(JSAnimalBase animal) {
        AnestheticFloatData floatData = animal.getData(JSReviseAttachments.ANESTHETIC_FLOAT);
        AnestheticBehaviorController.suspend(animal, floatData);
        animal.setTarget(null);
        animal.setPendingTarget(null);
        animal.setInvestigateTarget(null);
        animal.setFleeTarget(null);
        animal.setAttackDelay(-1);
        animal.setCurAttackTicks(0);
        animal.setCurEatTicks(0);
        animal.setCurSleepInteruptions(0);
        animal.setAggressive(false);
        animal.setIsPanicking(false);
        animal.setStalking(false);
        animal.setObserving(false);
        animal.setLeaping(false);
        animal.getNavigationController().stop();
        animal.setSleeping(true);
    }

    private static boolean isRawSleeping(JSAnimalBase animal) {
        return isUsable(animal) && animal.getEntityData().get(JSEntityDataHolder.sleeping);
    }

    private static boolean isSleepStabilizationActive(JSAnimalBase animal) {
        if (!isUsable(animal)) {
            return false;
        }
        long gameTime = animal.level().getGameTime();
        synchronized (SLEEP_STABILIZATION_EXPIRE_TICKS) {
            Long expireTick = SLEEP_STABILIZATION_EXPIRE_TICKS.get(animal);
            if (expireTick == null) {
                return false;
            }
            if (isSleepStabilizationActive(gameTime, expireTick)) {
                return true;
            }
            SLEEP_STABILIZATION_EXPIRE_TICKS.remove(animal);
            return false;
        }
    }

    private static boolean hasClientSleepAnimationGuard(JSAnimalBase animal) {
        if (!isUsable(animal) || !animal.level().isClientSide) {
            return false;
        }
        long gameTime = animal.level().getGameTime();
        int entityId = animal.getId();
        Object sessionKey = clientSleepAnimationGuardSessionKey(animal.level());
        boolean localSleep = shouldBridgeSleepState(animal)
                || isRawSleeping(animal)
                || isSleepStabilizationActive(animal);
        boolean releaseAwake = false;
        synchronized (CLIENT_SLEEP_ANIMATION_GUARDS) {
            Long expireTick = CLIENT_SLEEP_ANIMATION_GUARDS.get(entityId);
            if (shouldKeepClientSleepAnimationGuard(
                    clientSleepAnimationGuardCurrentSessionKey,
                    sessionKey,
                    clientSleepAnimationGuardLastGameTime,
                    gameTime,
                    expireTick
            )) {
                rememberClientSleepAnimationGuardSession(sessionKey, gameTime);
                if (localSleep) {
                    CLIENT_SLEEP_ANIMATION_GUARD_SEEN_SLEEP.put(entityId, Boolean.TRUE);
                    return true;
                }
                if (Boolean.TRUE.equals(CLIENT_SLEEP_ANIMATION_GUARD_SEEN_SLEEP.get(entityId))) {
                    CLIENT_SLEEP_ANIMATION_GUARDS.remove(entityId);
                    CLIENT_SLEEP_ANIMATION_GUARD_SEEN_SLEEP.remove(entityId);
                    releaseAwake = true;
                } else {
                    return true;
                }
            } else if (shouldResetClientSleepAnimationGuardSession(
                    clientSleepAnimationGuardCurrentSessionKey,
                    sessionKey,
                    clientSleepAnimationGuardLastGameTime,
                    gameTime
            )) {
                clearClientSleepAnimationGuardEntries();
                rememberClientSleepAnimationGuardSession(sessionKey, gameTime);
                return false;
            } else {
                CLIENT_SLEEP_ANIMATION_GUARDS.remove(entityId);
                CLIENT_SLEEP_ANIMATION_GUARD_SEEN_SLEEP.remove(entityId);
                rememberClientSleepAnimationGuardSession(sessionKey, gameTime);
                return false;
            }
        }
        if (releaseAwake) {
            forgetSleepAnimationTakeover(animal);
            logSleepAnimationTrace(animal, "client_guard_released", "reason=awake");
        }
        return false;
    }

    private static Object clientSleepAnimationGuardSessionKey(Level level) {
        return level.dimension().location() + "@" + System.identityHashCode(level);
    }

    private static void refreshClientSleepAnimationGuardSession(Object sessionKey, long gameTime) {
        if (shouldResetClientSleepAnimationGuardSession(
                clientSleepAnimationGuardCurrentSessionKey,
                sessionKey,
                clientSleepAnimationGuardLastGameTime,
                gameTime
        )) {
            clearClientSleepAnimationGuardEntries();
        }
        rememberClientSleepAnimationGuardSession(sessionKey, gameTime);
    }

    private static void rememberClientSleepAnimationGuardSession(Object sessionKey, long gameTime) {
        clientSleepAnimationGuardCurrentSessionKey = sessionKey;
        clientSleepAnimationGuardLastGameTime = gameTime;
    }

    private static void clearClientSleepAnimationGuardEntries() {
        CLIENT_SLEEP_ANIMATION_GUARDS.clear();
        CLIENT_SLEEP_ANIMATION_GUARD_SEEN_SLEEP.clear();
    }

    private static void sendClientSleepAnimationGuardIfNeeded(JSAnimalBase animal, boolean force) {
        if (!shouldSendServerSleepAnimationGuard(animal)) {
            return;
        }
        long gameTime = animal.level().getGameTime();
        synchronized (SERVER_SLEEP_ANIMATION_GUARD_SYNC_TICKS) {
            cleanupServerSleepAnimationGuardSyncs(gameTime);
            Long lastSyncTick = SERVER_SLEEP_ANIMATION_GUARD_SYNC_TICKS.get(animal);
            if (!force
                    && lastSyncTick != null
                    && elapsedSince(gameTime, lastSyncTick) < CLIENT_SLEEP_ANIMATION_GUARD_RESEND_TICKS) {
                return;
            }
            if (!SERVER_SLEEP_ANIMATION_GUARD_SYNC_TICKS.containsKey(animal)
                    && SERVER_SLEEP_ANIMATION_GUARD_SYNC_TICKS.size() >= MAX_SLEEP_ANIMATION_GUARD_ENTRIES
                    && !force) {
                return;
            }
            SERVER_SLEEP_ANIMATION_GUARD_SYNC_TICKS.put(animal, gameTime);
        }
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(
                animal,
                new SleepAnimationGuardPayload(animal.getId(), CLIENT_SLEEP_ANIMATION_GUARD_TICKS)
        );
        logSleepAnimationTrace(animal, "server_guard_sent", () -> sleepTraceState(animal)
                + " force=" + force + " duration=" + CLIENT_SLEEP_ANIMATION_GUARD_TICKS);
    }

    private static void sendClientSleepAnimationGuardClear(JSAnimalBase animal) {
        if (!isUsable(animal) || animal.level().isClientSide) {
            return;
        }
        synchronized (SERVER_SLEEP_ANIMATION_GUARD_SYNC_TICKS) {
            SERVER_SLEEP_ANIMATION_GUARD_SYNC_TICKS.remove(animal);
        }
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(
                animal,
                new SleepAnimationGuardPayload(animal.getId(), 0)
        );
        logSleepAnimationTrace(animal, "server_guard_clear_sent", () -> sleepTraceState(animal));
    }

    private static boolean shouldSendServerSleepAnimationGuard(JSAnimalBase animal) {
        return isUsable(animal)
                && !animal.level().isClientSide
                && (shouldBridgeSleepState(animal) || isRawSleeping(animal) || isSleepStabilizationActive(animal));
    }

    private static void cleanupClientSleepAnimationGuards(long gameTime) {
        Iterator<Map.Entry<Integer, Long>> iterator = CLIENT_SLEEP_ANIMATION_GUARDS.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Integer, Long> entry = iterator.next();
            Integer entityId = entry.getKey();
            Long expireTick = entry.getValue();
            if (entityId == null
                || expireTick == null
                || !isClientSleepAnimationGuardActive(gameTime, expireTick)) {
                iterator.remove();
                CLIENT_SLEEP_ANIMATION_GUARD_SEEN_SLEEP.remove(entityId);
            }
        }
    }

    private static void cleanupServerSleepAnimationGuardSyncs(long gameTime) {
        Iterator<Map.Entry<JSAnimalBase, Long>> iterator = SERVER_SLEEP_ANIMATION_GUARD_SYNC_TICKS.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<JSAnimalBase, Long> entry = iterator.next();
            JSAnimalBase animal = entry.getKey();
            Long lastSyncTick = entry.getValue();
            if (animal == null
                    || !isUsable(animal)
                    || lastSyncTick == null
                    || elapsedSince(gameTime, lastSyncTick) > MAX_CLIENT_SLEEP_ANIMATION_GUARD_TICKS) {
                iterator.remove();
            }
        }
    }

    private static long elapsedSince(long gameTime, long previousTick) {
        if (previousTick == Long.MIN_VALUE || gameTime < previousTick) {
            return Long.MAX_VALUE;
        }
        return gameTime - previousTick;
    }

    private static boolean shouldStabilizeAvianSleepState(JSAvianBase avian) {
        return isUsable(avian)
                && (shouldBridgeSleepState(avian)
                || isRawSleeping(avian)
                || isSleepStabilizationActive(avian)
                || hasClientSleepAnimationGuard(avian));
    }

    private static void clearAvianFlightState(JSAvianBase avian) {
        avian.setFlying(false);
        avian.setDiving(false);
        avian.setGliding(false);
        avian.setFlapping(false);
    }

    private static boolean rememberSleepAnimationTakeover(JSAnimalBase animal) {
        boolean guardActive = shouldUseSleepAnimationGuard(animal);
        synchronized (SLEEP_ANIMATION_TAKEOVERS) {
            boolean remembered = SLEEP_ANIMATION_TAKEOVERS.containsKey(animal);
            if (!shouldStartSleepAnimationTakeover(guardActive, remembered)) {
                return false;
            }
            SLEEP_ANIMATION_TAKEOVERS.put(animal, Boolean.TRUE);
            return true;
        }
    }

    private static void forgetSleepAnimationTakeover(JSAnimalBase animal) {
        if (animal == null) {
            return;
        }
        synchronized (SLEEP_ANIMATION_TAKEOVERS) {
            SLEEP_ANIMATION_TAKEOVERS.remove(animal);
        }
    }

    private static void takeOverSleepAnimation(
            JSAnimalBase animal,
            TravelersAnimalAnimationModule animationModule
    ) {
        if (!isUsable(animal)) {
            return;
        }
        int clearedTransitions = clearNonSleepAnimationState(animationModule);
        logSleepAnimationTrace(
                animal,
                "sleep_animation_takeover",
                () -> sleepTraceState(animal)
                        + " clearedTransitions=" + clearedTransitions
                        + " baseControllerStop=skipped"
        );
        if (animal.level().isClientSide) {
            logSleepAnimationTrace(
                    animal,
                    "client_azure_private_clear_skipped",
                    "reason=controller internals are not stable across TravelersLib versions"
            );
        }
    }

    private static int clearNonSleepAnimationState(TravelersAnimalAnimationModule animationModule) {
        if (!(animationModule instanceof TravelersAnimalAnimationModuleAccessor accessor)) {
            return 0;
        }
        return clearNonSleepAnimationTransitions(accessor.jsrevise$getAnimationMap());
    }

    public static void logSleepAnimationTrace(JSAnimalBase animal, String event, String detail) {
        if (animal == null || !debugLogging()) {
            return;
        }
        logSleepAnimationTrace(event, animal.level(), animal.getId(), describeAnimal(animal) + " " + detail);
    }

    private static void logSleepAnimationTrace(JSAnimalBase animal, String event, Supplier<String> detailSupplier) {
        if (!debugLogging() || animal == null) {
            return;
        }
        String detail = detailSupplier == null ? "" : detailSupplier.get();
        logSleepAnimationTrace(event, animal.level(), animal.getId(), describeAnimal(animal) + " " + detail);
    }

    public static void logSleepAnimationTrace(String event, Level level, int entityId, String detail) {
        if (!debugLogging() || level == null || event == null || event.isBlank()) {
            return;
        }
        long gameTime = level.getGameTime();
        String side = level.isClientSide ? "client" : "server";
        String key = side + ":" + entityId + ":" + event;
        synchronized (DEBUG_SLEEP_LOG_TICKS) {
            Long lastLogTick = DEBUG_SLEEP_LOG_TICKS.get(key);
            if (!shouldLogSleepAnimationTrace(gameTime, lastLogTick)) {
                return;
            }
            cleanupDebugSleepLogs(gameTime);
            DEBUG_SLEEP_LOG_TICKS.put(key, gameTime);
        }
        JSRevise.LOGGER.info(
                "[sleep-animation] event={} side={} tick={} entity={} {}",
                event,
                side,
                gameTime,
                entityId,
                detail == null ? "" : detail
        );
    }

    private static void cleanupDebugSleepLogs(long gameTime) {
        Iterator<Map.Entry<String, Long>> iterator = DEBUG_SLEEP_LOG_TICKS.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, Long> entry = iterator.next();
            Long lastTick = entry.getValue();
            if (lastTick == null || elapsedSince(gameTime, lastTick) > DEBUG_SLEEP_LOG_INTERVAL_TICKS * 4L) {
                iterator.remove();
            }
        }
    }

    private static String sleepTraceState(JSAnimalBase animal) {
        if (!isUsable(animal)) {
            return "usable=false";
        }
        return "bridge=" + shouldBridgeSleepState(animal)
                + " raw=" + isRawSleeping(animal)
                + " clientGuard=" + hasClientSleepAnimationGuard(animal)
                + " stable=" + isSleepStabilizationActive(animal)
                + " moving=" + animal.isMoving()
                + " shouldSleep=" + animal.shouldSleep()
                + " onGround=" + animal.onGround()
                + " inWater=" + animal.isInWater()
                + avianTraceState(animal);
    }

    private static String describeAnimal(JSAnimalBase animal) {
        return "type=" + animal.getType()
                + " class=" + animal.getClass().getName()
                + " uuid=" + animal.getUUID()
                + " tickCount=" + animal.tickCount;
    }

    private static String avianTraceState(JSAnimalBase animal) {
        if (!(animal instanceof JSAvianBase avian)) {
            return "";
        }
        return " flying=" + avian.isFlying()
                + " diving=" + avian.isDiving()
                + " gliding=" + avian.isGliding()
                + " flapping=" + avian.isFlapping();
    }

    private static void rememberSleepStabilizationIfAbsent(JSAnimalBase animal, String reason) {
        if (!isUsable(animal)) {
            return;
        }
        long gameTime = animal.level().getGameTime();
        synchronized (SLEEP_STABILIZATION_EXPIRE_TICKS) {
            Long expireTick = SLEEP_STABILIZATION_EXPIRE_TICKS.get(animal);
            if (expireTick != null && isSleepStabilizationActive(gameTime, expireTick)) {
                return;
            }
            SLEEP_STABILIZATION_EXPIRE_TICKS.put(
                    animal,
                    sleepStabilizationExpireTick(gameTime, SLEEP_STABILIZATION_TICKS)
            );
        }
        logSleepAnimationTrace(animal, "sleep_stabilization_started", () -> sleepTraceState(animal)
                + " reason=" + reason + " duration=" + SLEEP_STABILIZATION_TICKS);
    }

    private static void forgetSleepStabilization(JSAnimalBase animal) {
        if (animal == null) {
            return;
        }
        synchronized (SLEEP_STABILIZATION_EXPIRE_TICKS) {
            SLEEP_STABILIZATION_EXPIRE_TICKS.remove(animal);
        }
    }

    private static List<String> clientAnimationStageNames(AzCommand command) {
        List<String> stageNames = new ArrayList<>();
        List<AzAction> actions = command.actions();
        if (actions == null || actions.isEmpty()) {
            return stageNames;
        }
        for (AzAction action : actions) {
            if (action instanceof AzControllerPlayAnimationSequenceAction controllerAction) {
                addStageNames(controllerAction.sequence(), stageNames);
            } else if (action instanceof AzRootPlayAnimationSequenceAction rootAction) {
                addStageNames(rootAction.sequence(), stageNames);
            }
        }
        return stageNames;
    }

    private static void addStageNames(AzAnimationSequence sequence, List<String> stageNames) {
        if (sequence == null || sequence.stages() == null) {
            return;
        }
        for (AzAnimationStage stage : sequence.stages()) {
            if (stage != null && stage.name() != null) {
                stageNames.add(stage.name());
            }
        }
    }

    private static boolean debugLogging() {
        try {
            return JSReviseConfig.DEBUG_LOGGING.get();
        } catch (RuntimeException exception) {
            return false;
        }
    }

    private static boolean isUsable(JSAnimalBase animal) {
        return animal != null && animal.isAlive() && !animal.isRemoved();
    }
}
