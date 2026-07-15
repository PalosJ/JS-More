package com.palos.jsrevise.client.overlay;

import com.palos.jsrevise.compat.curios.DinoDoctorGogglesWearResolver;
import com.palos.jsrevise.server.block.entity.DinosaurCaptureCageBlockEntity;
import com.palos.jsrevise.server.registry.JSReviseBlocks;
import com.palos.jsrevise.server.registry.JSReviseItems;
import com.palos.jsrevise.server.system.capture.DinosaurCaptureSupplies;
import com.palos.jsrevise.system.observation.CaptureBoxObservationTarget;
import com.palos.jsrevise.system.observation.CaptureCageObservationSnapshot;
import com.palos.jsrevise.system.observation.DinosaurObservationSnapshot;
import com.palos.jsrevise.system.observation.DinosaurObservationSystem;
import com.palos.jsrevise.system.observation.ObservedGene;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalLong;
import jp.jurassicsaga.server.animal.entity.obj.bases.JSAnimalBase;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public final class DinoDoctorOverlayRenderer {
    private static final int REAL_AGE_DAYS_PER_YEAR = 365;
    private static final double REAL_AGE_DAYS_PER_MONTH = REAL_AGE_DAYS_PER_YEAR / 12.0D;
    private static final double OBSERVE_RANGE = 8.0D;
    public static final LayeredDraw.Layer OVERLAY = DinoDoctorOverlayRenderer::renderOverlay;
    private static final int PANEL_PADDING = 4;
    private static final int PANEL_HORIZONTAL_PADDING = 6;
    static final int PANEL_RIGHT_EXTRA_PADDING = 5;
    static final int PANEL_TOP_PADDING = 8;
    static final int PANEL_BOTTOM_PADDING = 4;
    static final int PANEL_BORDER_TOP_COLOR = 0x7AFFFFFF;
    static final int PANEL_BORDER_BOTTOM_COLOR = 0x60FFFFFF;
    static final int CONTENT_X_OFFSET = 20;
    static final int LINE_HEIGHT = 12;
    static final int TITLE_LINE_HEIGHT = 14;
    static final int PROGRESS_LABEL_HEIGHT = 12;
    static final int PROGRESS_BAR_Y_OFFSET = PROGRESS_LABEL_HEIGHT;
    static final int PROGRESS_BAR_WIDTH = 66;
    static final int PROGRESS_BAR_HEIGHT = 7;
    static final int PROGRESS_BAR_BORDER_WIDTH = 1;
    static final int PROGRESS_OUTER_WIDTH = PROGRESS_BAR_WIDTH + PROGRESS_BAR_BORDER_WIDTH * 2;
    static final int PROGRESS_OUTER_HEIGHT = PROGRESS_BAR_HEIGHT + PROGRESS_BAR_BORDER_WIDTH * 2;
    static final int PROGRESS_BOTTOM_GAP = 3;
    static final int PROGRESS_LINE_HEIGHT = PROGRESS_LABEL_HEIGHT + PROGRESS_OUTER_HEIGHT + PROGRESS_BOTTOM_GAP;
    static final int PROGRESS_BORDER_COLOR = 0xFFBFD1E6;
    static final int PROGRESS_TRACK_COLOR = 0x2EFFFFFF;
    static final int EGG_PROGRESS_DARK_COLOR = 0xFFEED1AF;
    static final int EGG_PROGRESS_LIGHT_COLOR = 0xFFFFEDC6;
    static final int CAPTURE_PANEL_THEME_COLOR = 0xA9A79E;
    static final int CAPTURE_PANEL_RADIUS = 3;
    static final int CAPTURE_PANEL_BORDER_WIDTH = 1;
    static final int CAPTURE_PANEL_HORIZONTAL_PADDING = 6;
    static final int CAPTURE_PANEL_TOP_PADDING = 4;
    static final int CAPTURE_PANEL_BOTTOM_PADDING = 4;
    static final int CAPTURE_TITLE_LINE_HEIGHT = 19;
    static final int CAPTURE_INFO_LINE_HEIGHT = 12;
    static final int CAPTURE_TITLE_ICON_SIZE = 15;
    static final int CAPTURE_TITLE_ICON_GAP = 3;
    static final int CAPTURE_CONTENT_X_OFFSET = CAPTURE_TITLE_ICON_SIZE + CAPTURE_TITLE_ICON_GAP;
    static final int ANESTHETIC_PROGRESS_DARK_COLOR = 0xFF2F98BB;
    static final int ANESTHETIC_PROGRESS_LIGHT_COLOR = 0xFF41ADD0;
    static final int WATER_PROGRESS_DARK_COLOR = 0xFF2D5FA8;
    static final int WATER_PROGRESS_LIGHT_COLOR = 0xFF4C87D9;
    static final int CARNIVORE_PROGRESS_DARK_COLOR = 0xFF8D3F2D;
    static final int CARNIVORE_PROGRESS_LIGHT_COLOR = 0xFFC5674A;
    static final int HERBIVORE_PROGRESS_DARK_COLOR = 0xFF3F8C3A;
    static final int HERBIVORE_PROGRESS_LIGHT_COLOR = 0xFF6FBC58;
    static final int HUNGER_PROGRESS_DARK_COLOR = CARNIVORE_PROGRESS_DARK_COLOR;
    static final int HUNGER_PROGRESS_LIGHT_COLOR = CARNIVORE_PROGRESS_LIGHT_COLOR;
    static final int THIRST_PROGRESS_DARK_COLOR = WATER_PROGRESS_DARK_COLOR;
    static final int THIRST_PROGRESS_LIGHT_COLOR = WATER_PROGRESS_LIGHT_COLOR;
    static final int MOOD_PROGRESS_DARK_COLOR = HERBIVORE_PROGRESS_DARK_COLOR;
    static final int MOOD_PROGRESS_LIGHT_COLOR = HERBIVORE_PROGRESS_LIGHT_COLOR;
    private static final int GENE_ICON_SIZE = 18;
    private static final int GENE_COLUMN_GAP = 2;
    private static final int GENE_ROW_GAP = 6;
    private static final float OVERLAY_SCALE = 0.85F;
    static final float LABEL_FONT_SCALE = 0.98F / OVERLAY_SCALE;
    static final float FONT_SCALE = LABEL_FONT_SCALE;
    static final float TITLE_FONT_SCALE = 1.05F / OVERLAY_SCALE;
    static final float CAPTURE_VALUE_FONT_SCALE = FONT_SCALE;
    static final float CAPTURE_LABEL_FONT_SCALE = LABEL_FONT_SCALE;
    static final float CAPTURE_TITLE_FONT_SCALE = TITLE_FONT_SCALE;
    private static final float PANEL_BACKGROUND_TOP_SHADE = 0.24F;
    private static final float PANEL_BACKGROUND_BOTTOM_SHADE = 0.17F;
    private static final int PANEL_BACKGROUND_TOP_ALPHA = 0x68;
    private static final int PANEL_BACKGROUND_BOTTOM_ALPHA = 0x72;
    private static final int CAPTURE_PANEL_BACKGROUND_TOP_ALPHA = 0xDA;
    private static final int CAPTURE_PANEL_BACKGROUND_BOTTOM_ALPHA = 0xE6;
    static final float GENE_LABEL_SCALE = 0.585F / OVERLAY_SCALE;
    private static final long TARGET_REFRESH_TICKS = 3L;
    private static final long CAGE_TARGET_MISS_GRACE_TICKS = 5L;
    private static final long NO_TICK = -1L;
    private static ObservedTarget cachedTarget;
    private static long cachedTargetGeneration = Long.MIN_VALUE;
    private static ResourceLocation cachedTargetDimensionId;
    private static long lastTargetScanTick = NO_TICK;
    private static long lastCageConfirmedTick = NO_TICK;

    private DinoDoctorOverlayRenderer() {
    }

    public static void renderOverlay(GuiGraphics guiGraphics, DeltaTracker deltaTracker) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.options.hideGui || minecraft.player == null || minecraft.gameMode == null) {
            clearCache();
            return;
        }

        if (!DinoDoctorGogglesWearResolver.isWearing(minecraft.player)) {
            clearCache();
            return;
        }

        ObservedTarget target = resolveObservedTargetCached(minecraft);
        if (target == null) {
            ClientCaptureCageObservationCache.stopObserving(minecraft.level);
            return;
        }
        if (target.animal() != null) {
            ClientCaptureCageObservationCache.stopObserving(minecraft.level);
            renderAnimalOverlay(guiGraphics, minecraft, target.animal());
            return;
        }
        renderCaptureBoxOverlay(guiGraphics, minecraft, target.cageTarget());
    }

    private static void renderAnimalOverlay(GuiGraphics guiGraphics, Minecraft minecraft, JSAnimalBase animal) {
        DinosaurObservationSnapshot snapshot = DinosaurObservationSystem.capture(animal)
                .withEggLayingProgress(ClientEggLayingProgressCache.getOrRequest(animal));
        DinosaurDnaVisualResolver.DnaVisual dnaVisual = DinosaurDnaVisualResolver.resolve(animal);
        List<OverlayLine> lines = createObservationLines(snapshot);
        Optional<OverlayLine> eggProgressLine = createEggProgressLine(snapshot);
        PanelMeasurements measurements = measureObservationPanel(minecraft, snapshot, lines, eggProgressLine);
        int virtualWidth = Mth.floor(guiGraphics.guiWidth() / OVERLAY_SCALE);
        int virtualHeight = Mth.floor(guiGraphics.guiHeight() / OVERLAY_SCALE);
        DualPanelLayout.SinglePanelLayout layout = DualPanelLayout.arrangeSingleLeft(
                virtualWidth,
                virtualHeight,
                measurements.panelWidth(),
                measurements.panelHeight()
        );

        guiGraphics.pose().pushPose();
        guiGraphics.pose().scale(OVERLAY_SCALE, OVERLAY_SCALE, 1.0F);
        drawObservationPanel(
                guiGraphics,
                minecraft,
                dnaVisual,
                snapshot,
                lines,
                eggProgressLine,
                measurements,
                layout.panel(),
                layout.scale()
        );
        guiGraphics.pose().popPose();
    }

    private static void renderCaptureBoxOverlay(
            GuiGraphics guiGraphics,
            Minecraft minecraft,
            CaptureBoxObservationTarget.Target target
    ) {
        if (minecraft.level == null) {
            return;
        }
        BlockPos controllerPos = target.controller();
        DinosaurCaptureCageBlockEntity cage = target.cage();

        DinosaurCaptureSupplies supplies = cage.getSupplies();
        Optional<CaptureCageObservationSnapshot> cageSnapshot = Optional.empty();
        if (cage.hasCapturedDinosaur() && !cage.hasUnreadableContents()) {
            cageSnapshot = ClientCaptureCageObservationCache.getOrRequest(minecraft.level, target);
        } else {
            ClientCaptureCageObservationCache.invalidate(controllerPos);
        }
        CaptureBoxHudViewModel viewModel = createCaptureBoxViewModel(cage, supplies, cageSnapshot);
        List<CapturePanelLine> captureBoxLines = createCaptureBoxLines(viewModel);
        CapturePanelMeasurements captureBoxMeasurements = measureCapturePanel(minecraft, captureBoxLines);

        DinosaurObservationSnapshot snapshot = cageSnapshot.map(CaptureCageObservationSnapshot::observation).orElse(null);
        List<OverlayLine> observationLines = snapshot == null
                ? List.of()
                : createCaptureCageObservationLines(cageSnapshot.orElseThrow());
        Optional<OverlayLine> noEggProgress = Optional.empty();
        PanelMeasurements observationMeasurements = snapshot == null
                ? PanelMeasurements.empty()
                : measureObservationPanel(minecraft, snapshot, observationLines, noEggProgress);

        int virtualWidth = Mth.floor(guiGraphics.guiWidth() / OVERLAY_SCALE);
        int virtualHeight = Mth.floor(guiGraphics.guiHeight() / OVERLAY_SCALE);
        DualPanelLayout layout = DualPanelLayout.arrange(
                virtualWidth,
                virtualHeight,
                observationMeasurements.panelWidth(),
                observationMeasurements.panelHeight(),
                captureBoxMeasurements.panelWidth(),
                captureBoxMeasurements.panelHeight()
        );

        guiGraphics.pose().pushPose();
        guiGraphics.pose().scale(OVERLAY_SCALE, OVERLAY_SCALE, 1.0F);
        if (snapshot != null && layout.left().isPresent()) {
            drawObservationPanel(
                    guiGraphics,
                    minecraft,
                    DinosaurDnaVisualResolver.resolve(snapshot.ageEstimate().speciesId()),
                    snapshot,
                    observationLines,
                    noEggProgress,
                    observationMeasurements,
                    layout.left().orElseThrow(),
                    layout.scale()
            );
        }
        drawCaptureBoxPanel(
                guiGraphics,
                minecraft,
                captureBoxLines,
                captureBoxMeasurements,
                layout.right(),
                layout.scale()
        );
        guiGraphics.pose().popPose();
    }

    static CaptureBoxHudViewModel createCaptureBoxViewModel(
            DinosaurCaptureCageBlockEntity cage,
            DinosaurCaptureSupplies supplies,
            Optional<CaptureCageObservationSnapshot> snapshot
    ) {
        if (cage.hasUnreadableCapturedDinosaur()) {
            return CaptureBoxHudViewModel.unavailable(
                    supplies.anesthetic(),
                    supplies.water(),
                    supplies.carnivore(),
                    supplies.herbivore(),
                    cage.hasUnreadableSupplies(),
                    true
            );
        }
        if (snapshot.isPresent()) {
            CaptureCageObservationSnapshot observed = snapshot.get();
            return CaptureBoxHudViewModel.occupied(
                    supplies.anesthetic(),
                    supplies.water(),
                    supplies.carnivore(),
                    supplies.herbivore(),
                    cage.hasUnreadableSupplies(),
                    observed.capturedDurationTicks(),
                    observed.cageDurability()
            );
        }
        if (!cage.hasCapturedDinosaur()) {
            return CaptureBoxHudViewModel.empty(
                    supplies.anesthetic(),
                    supplies.water(),
                    supplies.carnivore(),
                    supplies.herbivore(),
                    cage.hasUnreadableSupplies()
            );
        }
        return CaptureBoxHudViewModel.unavailable(
                supplies.anesthetic(),
                supplies.water(),
                supplies.carnivore(),
                supplies.herbivore(),
                cage.hasUnreadableSupplies(),
                false
        );
    }

    static List<OverlayLine> createObservationLines(DinosaurObservationSnapshot snapshot) {
        List<OverlayLine> lines = new ArrayList<>();
        lines.add(OverlayLine.title(snapshot.displayName(), 0xDDEEFF));
        lines.add(OverlayLine.labeled(Component.translatable("overlay.jsrevise.age.label"), formatAge(snapshot), 0xBFD1E6));
        lines.add(OverlayLine.labeled(Component.translatable("overlay.jsrevise.health"), formatHealth(snapshot), 0xBFD1E6));
        lines.add(OverlayLine.labeled(Component.translatable("overlay.jsrevise.gender"), formatGender(snapshot), 0xBFD1E6));
        lines.add(createVitalProgressLine(
                Component.translatable("overlay.jsrevise.hunger"),
                snapshot.hungerPercent(),
                HUNGER_PROGRESS_DARK_COLOR,
                HUNGER_PROGRESS_LIGHT_COLOR
        ));
        lines.add(createVitalProgressLine(
                Component.translatable("overlay.jsrevise.thirst"),
                snapshot.thirstPercent(),
                THIRST_PROGRESS_DARK_COLOR,
                THIRST_PROGRESS_LIGHT_COLOR
        ));
        lines.add(createVitalProgressLine(
                Component.translatable("overlay.jsrevise.mood"),
                snapshot.moodPercent(),
                MOOD_PROGRESS_DARK_COLOR,
                MOOD_PROGRESS_LIGHT_COLOR
        ));
        OptionalLong remainingTicks = positiveTicks(snapshot.remainingAnestheticTicks());
        OptionalLong queuedTicks = positiveTicks(snapshot.queuedAnestheticTicks());
        OptionalLong pendingTicks = positiveTicks(snapshot.pendingAnestheticTicks());
        if (remainingTicks.isPresent() || queuedTicks.isPresent()) {
            Component suffix = queuedTicks.isPresent()
                    ? Component.translatable(
                            "overlay.jsrevise.anesthetic.addition",
                            formatAnestheticDuration(queuedTicks.getAsLong())
                    )
                    : Component.empty();
            Component label = Component.translatable("overlay.jsrevise.anesthetic.remaining");
            if (remainingTicks.isPresent()) {
                lines.add(OverlayLine.labeledWithSuffix(
                        label,
                        formatAnestheticDuration(remainingTicks.getAsLong()),
                        0xBFD1E6,
                        suffix,
                        0x7D8794,
                        0.75F
                ));
            } else {
                lines.add(OverlayLine.withSuffix(
                        List.of(OverlayTextSegment.label(label)),
                        0xBFD1E6,
                        suffix,
                        0x7D8794,
                        0.75F
                ));
            }
        }
        if (pendingTicks.isPresent()) {
            lines.add(OverlayLine.labeled(
                    Component.translatable("overlay.jsrevise.anesthetic.pending"),
                    formatAnestheticDuration(pendingTicks.getAsLong()),
                    0xBFD1E6
            ));
        }
        boolean hasGenes = !snapshot.genes().isEmpty();
        if (hasGenes) {
            lines.add(OverlayLine.singleLabel(Component.translatable("overlay.jsrevise.genes"), 0x9FB1C4));
        }
        return List.copyOf(lines);
    }

    static Optional<OverlayLine> createEggProgressLine(DinosaurObservationSnapshot snapshot) {
        return snapshot.eggLayingProgress().map(progress -> OverlayLine.progress(
                formatEggLayingProgress(),
                0xBFD1E6,
                progress.progress(),
                EGG_PROGRESS_DARK_COLOR,
                EGG_PROGRESS_LIGHT_COLOR
        ));
    }

    static List<OverlayLine> createCaptureCageObservationLines(CaptureCageObservationSnapshot snapshot) {
        return createObservationLines(snapshot.observation());
    }

    private static OptionalLong positiveTicks(OptionalLong ticks) {
        return ticks.isPresent() && ticks.getAsLong() > 0L ? ticks : OptionalLong.empty();
    }

    private static OverlayLine createVitalProgressLine(
            Component label,
            OptionalDouble percent,
            int darkColor,
            int lightColor
    ) {
        boolean known = percent.isPresent() && Double.isFinite(percent.getAsDouble());
        double boundedPercent = known ? Mth.clamp(percent.getAsDouble(), 0.0D, 100.0D) : 0.0D;
        Component value = known
                ? plainValue(formatDecimal(boundedPercent) + "%")
                : plainValue(Component.translatable("overlay.jsrevise.unknown"));
        return OverlayLine.progressWithInlineValue(
                label,
                value,
                0xBFD1E6,
                boundedPercent / 100.0D,
                known,
                darkColor,
                lightColor
        );
    }

    static List<CapturePanelLine> createCaptureBoxLines(CaptureBoxHudViewModel viewModel) {
        List<CapturePanelLine> lines = new ArrayList<>();
        lines.add(CapturePanelLine.title(Component.translatable("overlay.jsrevise.capture_box.title"), 0xDDEEFF));
        if (viewModel.suppliesUnreadable()) {
            lines.add(CapturePanelLine.warning(
                    Component.translatable("tooltip.jsrevise.dinosaur_capture_box.supplies_unreadable"),
                    0xFF7777
            ));
        } else {
            lines.add(CapturePanelLine.progress(
                    Component.translatable("overlay.jsrevise.capture_box.anesthetic_reserve"),
                    0xBFD1E6,
                    viewModel.anestheticReserve(),
                    viewModel.capacity(DinosaurCaptureSupplies.Type.ANESTHETIC),
                    viewModel.anestheticProgress(),
                    ANESTHETIC_PROGRESS_DARK_COLOR,
                    ANESTHETIC_PROGRESS_LIGHT_COLOR
            ));
            lines.add(CapturePanelLine.progress(
                    Component.translatable("overlay.jsrevise.capture_box.water_reserve"),
                    0xBFD1E6,
                    viewModel.waterReserve(),
                    viewModel.capacity(DinosaurCaptureSupplies.Type.WATER),
                    viewModel.waterProgress(),
                    WATER_PROGRESS_DARK_COLOR,
                    WATER_PROGRESS_LIGHT_COLOR
            ));
            lines.add(CapturePanelLine.progress(
                    Component.translatable("overlay.jsrevise.capture_box.carnivore_reserve"),
                    0xBFD1E6,
                    viewModel.carnivoreReserve(),
                    viewModel.capacity(DinosaurCaptureSupplies.Type.CARNIVORE),
                    viewModel.carnivoreProgress(),
                    CARNIVORE_PROGRESS_DARK_COLOR,
                    CARNIVORE_PROGRESS_LIGHT_COLOR
            ));
            lines.add(CapturePanelLine.progress(
                    Component.translatable("overlay.jsrevise.capture_box.herbivore_reserve"),
                    0xBFD1E6,
                    viewModel.herbivoreReserve(),
                    viewModel.capacity(DinosaurCaptureSupplies.Type.HERBIVORE),
                    viewModel.herbivoreProgress(),
                    HERBIVORE_PROGRESS_DARK_COLOR,
                    HERBIVORE_PROGRESS_LIGHT_COLOR
            ));
        }
        if (viewModel.capturedDinosaurUnreadable()) {
            lines.add(CapturePanelLine.warning(
                    Component.translatable("tooltip.jsrevise.dinosaur_capture_box.unreadable"),
                    0xFF7777
            ));
        }
        viewModel.capturedDurationTicks().ifPresent(ticks -> lines.add(CapturePanelLine.info(
                Component.translatable("overlay.jsrevise.capture_box.duration"),
                plainValue(CaptureDurationFormatter.format(ticks)),
                0xBFD1E6
        )));
        viewModel.durability().ifPresent(durability -> lines.add(CapturePanelLine.info(
                Component.translatable("overlay.jsrevise.capture_box.durability"),
                plainValue(CaptureBoxDurabilityFormatter.format(durability)),
                0xBFD1E6
        )));
        return List.copyOf(lines);
    }

    private static PanelMeasurements measureObservationPanel(
            Minecraft minecraft,
            DinosaurObservationSnapshot snapshot,
            List<OverlayLine> lines,
            Optional<OverlayLine> eggProgressLine
    ) {
        int maxWidth = maximumLineWidth(minecraft, lines, eggProgressLine);
        GeneLayout geneLayout = GeneLayout.empty();
        boolean hasGenes = !snapshot.genes().isEmpty();
        if (hasGenes) {
            geneLayout = computeGeneLayout(minecraft, snapshot.genes());
            maxWidth = Math.max(maxWidth, geneLayout.totalWidth());
        }
        int contentHeight = lines.stream().mapToInt(OverlayLine::height).sum();
        if (hasGenes) {
            contentHeight += 2 + geneLayout.totalHeight();
        }
        if (eggProgressLine.isPresent()) {
            contentHeight += (hasGenes ? LINE_HEIGHT : 0) + eggProgressLine.get().height();
        }
        return new PanelMeasurements(
                maxWidth,
                observationPanelWidth(maxWidth),
                contentHeight + PANEL_TOP_PADDING + PANEL_BOTTOM_PADDING,
                geneLayout
        );
    }

    static int observationPanelWidth(int contentWidth) {
        return Math.max(0, contentWidth)
                + CONTENT_X_OFFSET
                + PANEL_HORIZONTAL_PADDING * 2
                + PANEL_RIGHT_EXTRA_PADDING;
    }

    private static CapturePanelMeasurements measureCapturePanel(
            Minecraft minecraft,
            List<CapturePanelLine> lines
    ) {
        int maxWidth = 0;
        int contentHeight = 0;
        for (CapturePanelLine line : lines) {
            maxWidth = Math.max(maxWidth, measureCaptureLineWidth(minecraft, line));
            contentHeight += line.height();
        }
        return new CapturePanelMeasurements(
                maxWidth + CAPTURE_PANEL_HORIZONTAL_PADDING * 2,
                contentHeight + CAPTURE_PANEL_TOP_PADDING + CAPTURE_PANEL_BOTTOM_PADDING
        );
    }

    private static int maximumLineWidth(
            Minecraft minecraft,
            List<OverlayLine> lines,
            Optional<OverlayLine> extraLine
    ) {
        int maxWidth = 0;
        for (OverlayLine line : lines) {
            maxWidth = Math.max(maxWidth, measureLineWidth(minecraft, line));
        }
        if (extraLine.isPresent()) {
            maxWidth = Math.max(maxWidth, measureLineWidth(minecraft, extraLine.get()));
        }
        return maxWidth;
    }

    private static void drawObservationPanel(
            GuiGraphics guiGraphics,
            Minecraft minecraft,
            DinosaurDnaVisualResolver.DnaVisual dnaVisual,
            DinosaurObservationSnapshot snapshot,
            List<OverlayLine> lines,
            Optional<OverlayLine> eggProgressLine,
            PanelMeasurements measurements,
            PanelRect panel,
            float panelScale
    ) {
        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(panel.left(), panel.top(), 0.0F);
        guiGraphics.pose().scale(panelScale, panelScale, 1.0F);
        fillRoundedGradient(
                guiGraphics,
                0,
                0,
                measurements.panelWidth(),
                measurements.panelHeight(),
                5,
                panelBackgroundTopColor(dnaVisual.themeColor()),
                panelBackgroundBottomColor(dnaVisual.themeColor())
        );
        strokeRoundedGradient(
                guiGraphics,
                -1,
                -1,
                measurements.panelWidth() + 1,
                measurements.panelHeight() + 1,
                6,
                0,
                0,
                measurements.panelWidth(),
                measurements.panelHeight(),
                5,
                PANEL_BORDER_TOP_COLOR,
                PANEL_BORDER_BOTTOM_COLOR
        );

        int contentX = PANEL_HORIZONTAL_PADDING;
        int contentY = PANEL_TOP_PADDING;
        ItemStack titleIcon = dnaVisual.icon().isEmpty()
                ? new ItemStack(JSReviseItems.DINO_DOCTOR_GOGGLES.get())
                : dnaVisual.icon();
        guiGraphics.renderItem(titleIcon, contentX - 2, contentY - 2);
        int lineY = contentY;
        for (OverlayLine line : lines) {
            drawLine(
                    guiGraphics,
                    minecraft,
                    contentX + CONTENT_X_OFFSET,
                    lineY,
                    line
            );
            lineY += line.height();
        }
        if (!snapshot.genes().isEmpty()) {
            int genesY = lineY + 2;
            drawGenes(
                    guiGraphics,
                    minecraft,
                    snapshot.genes(),
                    contentX + CONTENT_X_OFFSET,
                    genesY,
                    measurements.geneLayout()
            );
            lineY = genesY + measurements.geneLayout().totalHeight();
        }
        if (eggProgressLine.isPresent()) {
            if (!snapshot.genes().isEmpty()) {
                lineY += LINE_HEIGHT;
            }
            drawLine(
                    guiGraphics,
                    minecraft,
                    contentX + CONTENT_X_OFFSET,
                    lineY,
                    eggProgressLine.get()
            );
        }
        guiGraphics.pose().popPose();
    }

    private static void drawCaptureBoxPanel(
            GuiGraphics guiGraphics,
            Minecraft minecraft,
            List<CapturePanelLine> lines,
            CapturePanelMeasurements measurements,
            PanelRect panel,
            float panelScale
    ) {
        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(panel.left(), panel.top(), 0.0F);
        guiGraphics.pose().scale(panelScale, panelScale, 1.0F);
        fillRoundedGradient(
                guiGraphics,
                0,
                0,
                measurements.panelWidth(),
                measurements.panelHeight(),
                CAPTURE_PANEL_RADIUS,
                capturePanelBackgroundTopColor(),
                capturePanelBackgroundBottomColor()
        );
        strokeRoundedGradient(
                guiGraphics,
                -CAPTURE_PANEL_BORDER_WIDTH,
                -CAPTURE_PANEL_BORDER_WIDTH,
                measurements.panelWidth() + CAPTURE_PANEL_BORDER_WIDTH,
                measurements.panelHeight() + CAPTURE_PANEL_BORDER_WIDTH,
                CAPTURE_PANEL_RADIUS + CAPTURE_PANEL_BORDER_WIDTH,
                0,
                0,
                measurements.panelWidth(),
                measurements.panelHeight(),
                CAPTURE_PANEL_RADIUS,
                PANEL_BORDER_TOP_COLOR,
                PANEL_BORDER_BOTTOM_COLOR
        );

        int contentX = CAPTURE_PANEL_HORIZONTAL_PADDING;
        int lineY = CAPTURE_PANEL_TOP_PADDING;
        for (CapturePanelLine line : lines) {
            drawCaptureLine(
                    guiGraphics,
                    minecraft,
                    contentX + captureContentOffset(line.kind()),
                    lineY,
                    line
            );
            lineY += line.height();
        }
        guiGraphics.pose().popPose();
    }

    private static int measureCaptureLineWidth(
            Minecraft minecraft,
            CapturePanelLine line
    ) {
        int contentWidth = switch (line.kind()) {
            case TITLE -> CAPTURE_TITLE_ICON_SIZE
                    + CAPTURE_TITLE_ICON_GAP
                    + scaledTextWidth(minecraft, line.label(), CAPTURE_TITLE_FONT_SCALE);
            case PROGRESS -> Math.max(
                    scaledTextWidth(minecraft, line.label(), CAPTURE_LABEL_FONT_SCALE)
                            + scaledTextWidth(minecraft, line.value(), CAPTURE_VALUE_FONT_SCALE),
                    PROGRESS_OUTER_WIDTH
            );
            case INFO -> scaledTextWidth(minecraft, line.label(), CAPTURE_LABEL_FONT_SCALE)
                    + scaledTextWidth(minecraft, line.value(), CAPTURE_VALUE_FONT_SCALE);
            case WARNING -> scaledTextWidth(minecraft, line.label(), CAPTURE_LABEL_FONT_SCALE);
        };
        return contentWidth + captureContentOffset(line.kind());
    }

    static int captureContentOffset(CapturePanelLineKind kind) {
        return kind == CapturePanelLineKind.TITLE ? 0 : CAPTURE_CONTENT_X_OFFSET;
    }

    private static int scaledTextWidth(Minecraft minecraft, Component text, float scale) {
        return (int) Math.ceil(minecraft.font.width(text) * scale);
    }

    static int centeredTextY(int rowY, int rowHeight, int scaledTextHeight) {
        return rowY + Math.floorDiv(rowHeight - Math.max(0, scaledTextHeight), 2);
    }

    static int alignedMainTextY(int rowY, int rowHeight, int fontLineHeight, float mainScale) {
        return centeredTextY(rowY, rowHeight, (int) Math.ceil(Math.max(0, fontLineHeight) * mainScale));
    }

    static int captureMainTextY(int rowY, int rowHeight, int fontLineHeight) {
        return alignedMainTextY(rowY, rowHeight, fontLineHeight, CAPTURE_LABEL_FONT_SCALE);
    }

    private static int captureMainTextY(Minecraft minecraft, int rowY, int rowHeight) {
        return captureMainTextY(rowY, rowHeight, minecraft.font.lineHeight);
    }

    private static int centeredTextY(Minecraft minecraft, int rowY, int rowHeight, float scale) {
        return alignedMainTextY(rowY, rowHeight, minecraft.font.lineHeight, scale);
    }

    private static void drawCaptureLine(
            GuiGraphics guiGraphics,
            Minecraft minecraft,
            int x,
            int y,
            CapturePanelLine line
    ) {
        switch (line.kind()) {
            case TITLE -> {
                float iconScale = CAPTURE_TITLE_ICON_SIZE / 16.0F;
                int iconY = y + (CAPTURE_TITLE_LINE_HEIGHT - CAPTURE_TITLE_ICON_SIZE) / 2;
                guiGraphics.pose().pushPose();
                guiGraphics.pose().translate(x, iconY, 0.0F);
                guiGraphics.pose().scale(iconScale, iconScale, 1.0F);
                guiGraphics.renderItem(new ItemStack(JSReviseItems.DINOSAUR_CAPTURE_CAGE.get()), 0, 0);
                guiGraphics.pose().popPose();
                drawScaledString(
                        guiGraphics,
                        minecraft,
                        line.label(),
                        x + CAPTURE_TITLE_ICON_SIZE + CAPTURE_TITLE_ICON_GAP,
                        centeredTextY(minecraft, y, CAPTURE_TITLE_LINE_HEIGHT, CAPTURE_TITLE_FONT_SCALE),
                        line.color(),
                        CAPTURE_TITLE_FONT_SCALE
                );
            }
            case PROGRESS -> {
                int labelWidth = scaledTextWidth(minecraft, line.label(), CAPTURE_LABEL_FONT_SCALE);
                int mainTextY = captureMainTextY(minecraft, y, PROGRESS_LABEL_HEIGHT);
                drawScaledString(
                        guiGraphics,
                        minecraft,
                        line.label(),
                        x,
                        mainTextY,
                        line.color(),
                        CAPTURE_LABEL_FONT_SCALE
                );
                drawScaledString(
                        guiGraphics,
                        minecraft,
                        line.value(),
                        x + labelWidth,
                        mainTextY,
                        line.color(),
                        CAPTURE_VALUE_FONT_SCALE
                );
                int progressY = y + PROGRESS_LABEL_HEIGHT;
                drawProgressBar(
                        guiGraphics,
                        x,
                        progressY,
                        line.progress(),
                        true,
                        line.progressDarkColor(),
                        line.progressLightColor()
                );
            }
            case INFO -> {
                int mainTextY = captureMainTextY(minecraft, y, CAPTURE_INFO_LINE_HEIGHT);
                drawScaledString(
                        guiGraphics,
                        minecraft,
                        line.label(),
                        x,
                        mainTextY,
                        line.color(),
                        CAPTURE_LABEL_FONT_SCALE
                );
                int labelWidth = scaledTextWidth(minecraft, line.label(), CAPTURE_LABEL_FONT_SCALE);
                drawScaledString(
                        guiGraphics,
                        minecraft,
                        line.value(),
                        x + labelWidth,
                        mainTextY,
                        line.color(),
                        CAPTURE_VALUE_FONT_SCALE
                );
            }
            case WARNING -> drawScaledString(
                    guiGraphics,
                    minecraft,
                    line.label(),
                    x,
                    centeredTextY(minecraft, y, CAPTURE_INFO_LINE_HEIGHT, CAPTURE_LABEL_FONT_SCALE),
                    line.color(),
                    CAPTURE_LABEL_FONT_SCALE
            );
        }
    }

    private static int withAlpha(int rgb, int alpha) {
        return alpha << 24 | rgb & 0xFFFFFF;
    }

    static int panelBackgroundTopColor(int themeColor) {
        return withAlpha(shadeRgb(themeColor, PANEL_BACKGROUND_TOP_SHADE), PANEL_BACKGROUND_TOP_ALPHA);
    }

    static int panelBackgroundBottomColor(int themeColor) {
        return withAlpha(shadeRgb(themeColor, PANEL_BACKGROUND_BOTTOM_SHADE), PANEL_BACKGROUND_BOTTOM_ALPHA);
    }

    static int capturePanelBackgroundTopColor() {
        return withAlpha(shadeRgb(CAPTURE_PANEL_THEME_COLOR, PANEL_BACKGROUND_TOP_SHADE),
                CAPTURE_PANEL_BACKGROUND_TOP_ALPHA);
    }

    static int capturePanelBackgroundBottomColor() {
        return withAlpha(shadeRgb(CAPTURE_PANEL_THEME_COLOR, PANEL_BACKGROUND_BOTTOM_SHADE),
                CAPTURE_PANEL_BACKGROUND_BOTTOM_ALPHA);
    }

    private static int shadeRgb(int color, float factor) {
        int red = Mth.floor((color >> 16 & 0xFF) * factor);
        int green = Mth.floor((color >> 8 & 0xFF) * factor);
        int blue = Mth.floor((color & 0xFF) * factor);
        return red << 16 | green << 8 | blue;
    }

    private static void fillRoundedGradient(
            GuiGraphics guiGraphics,
            int left,
            int top,
            int right,
            int bottom,
            int radius,
            int topColor,
            int bottomColor
    ) {
        int height = bottom - top;
        for (int row = 0; row < height; row++) {
            int inset = roundedInset(row, height, radius);
            float progress = height <= 1 ? 0.0F : (float) row / (height - 1);
            guiGraphics.fill(left + inset, top + row, right - inset, top + row + 1, lerpArgb(topColor, bottomColor, progress));
        }
    }

    private static void strokeRoundedGradient(
            GuiGraphics guiGraphics,
            int outerLeft,
            int outerTop,
            int outerRight,
            int outerBottom,
            int outerRadius,
            int innerLeft,
            int innerTop,
            int innerRight,
            int innerBottom,
            int innerRadius,
            int topColor,
            int bottomColor
    ) {
        int outerHeight = outerBottom - outerTop;
        int innerHeight = innerBottom - innerTop;
        for (int row = 0; row < outerHeight; row++) {
            int y = outerTop + row;
            int outerInset = roundedInset(row, outerHeight, outerRadius);
            int outerStart = outerLeft + outerInset;
            int outerEnd = outerRight - outerInset;
            float progress = outerHeight <= 1 ? 0.0F : (float) row / (outerHeight - 1);
            int color = lerpArgb(topColor, bottomColor, progress);
            if (y < innerTop || y >= innerBottom) {
                guiGraphics.fill(outerStart, y, outerEnd, y + 1, color);
                continue;
            }

            int innerRow = y - innerTop;
            int innerInset = roundedInset(innerRow, innerHeight, innerRadius);
            int innerStart = innerLeft + innerInset;
            int innerEnd = innerRight - innerInset;
            if (outerStart < innerStart) {
                guiGraphics.fill(outerStart, y, Math.min(innerStart, outerEnd), y + 1, color);
            }
            if (innerEnd < outerEnd) {
                guiGraphics.fill(Math.max(innerEnd, outerStart), y, outerEnd, y + 1, color);
            }
        }
    }

    private static int roundedInset(int row, int height, int radius) {
        int edgeDistance = Math.min(row, height - row - 1);
        if (edgeDistance >= radius) {
            return 0;
        }
        double offset = radius - edgeDistance - 0.5D;
        return Math.max(0, (int) Math.ceil(radius - Math.sqrt(radius * radius - offset * offset)));
    }

    private static int lerpArgb(int from, int to, float progress) {
        int alpha = Mth.floor(Mth.lerp(progress, from >>> 24, to >>> 24));
        int red = Mth.floor(Mth.lerp(progress, from >> 16 & 0xFF, to >> 16 & 0xFF));
        int green = Mth.floor(Mth.lerp(progress, from >> 8 & 0xFF, to >> 8 & 0xFF));
        int blue = Mth.floor(Mth.lerp(progress, from & 0xFF, to & 0xFF));
        return alpha << 24 | red << 16 | green << 8 | blue;
    }

    private static ObservedTarget resolveObservedTargetCached(Minecraft minecraft) {
        if (minecraft.level == null) {
            clearCache();
            return null;
        }
        ClientOverlaySessionClock.Stamp stamp = ClientOverlaySessionClock.current(minecraft.level);
        if (stamp == null) {
            clearTargetCache();
            return null;
        }
        if (stamp.generation() != cachedTargetGeneration
                || !stamp.dimensionId().equals(cachedTargetDimensionId)) {
            clearTargetCache();
            cachedTargetGeneration = stamp.generation();
            cachedTargetDimensionId = stamp.dimensionId();
        }

        long logicalTick = stamp.logicalTick();
        if (cachedTarget != null && !cachedTarget.isStillValid(minecraft)) {
            invalidateLocallyInvalidCaptureTarget(cachedTarget.cagePos());
            cachedTarget = null;
            lastCageConfirmedTick = NO_TICK;
            lastTargetScanTick = NO_TICK;
        }
        if (!isTargetScanDue(logicalTick, lastTargetScanTick)) {
            return cachedTarget;
        }

        lastTargetScanTick = logicalTick;
        ObservedTarget resolved = resolveObservedTarget(minecraft);
        if (resolved != null) {
            cachedTarget = resolved;
            lastCageConfirmedTick = resolved.cagePos() == null ? NO_TICK : logicalTick;
            return resolved;
        }
        if (cachedTarget != null && cachedTarget.cagePos() != null && cachedTarget.isStillValid(minecraft)) {
            if (shouldRetainCageMiss(logicalTick, lastCageConfirmedTick)) {
                return cachedTarget;
            }
        }
        cachedTarget = null;
        lastCageConfirmedTick = NO_TICK;
        return null;
    }

    public static void clearCache() {
        clearTargetCache();
        DinosaurDnaVisualResolver.clearCache();
        ClientEggLayingProgressCache.clearCache();
        ClientCaptureCageObservationCache.clearCache();
    }

    private static void clearTargetCache() {
        cachedTarget = null;
        cachedTargetGeneration = Long.MIN_VALUE;
        cachedTargetDimensionId = null;
        lastTargetScanTick = NO_TICK;
        lastCageConfirmedTick = NO_TICK;
    }

    static boolean isTargetScanDue(long logicalTick, long lastScanTick) {
        return lastScanTick == NO_TICK
                || logicalTick < lastScanTick
                || logicalTick - lastScanTick >= TARGET_REFRESH_TICKS;
    }

    static boolean shouldRetainCageMiss(long logicalTick, long lastConfirmedTick) {
        return lastConfirmedTick != NO_TICK
                && logicalTick >= lastConfirmedTick
                && logicalTick - lastConfirmedTick <= CAGE_TARGET_MISS_GRACE_TICKS;
    }

    static void invalidateLocallyInvalidCaptureTarget(BlockPos cagePos) {
        ClientCaptureCageObservationCache.invalidate(cagePos);
    }

    private static ObservedTarget resolveObservedTarget(Minecraft minecraft) {
        JSAnimalBase animal = resolveObservedAnimal(minecraft);
        if (animal != null) {
            return ObservedTarget.animal(animal);
        }
        CaptureBoxObservationTarget.Target cageTarget = resolveObservedCageTarget(minecraft);
        return cageTarget == null ? null : ObservedTarget.cage(cageTarget);
    }

    private static JSAnimalBase resolveObservedAnimal(Minecraft minecraft) {
        if (minecraft.level == null || minecraft.player == null) {
            return null;
        }
        Vec3 start = minecraft.player.getEyePosition();
        HitResult hitResult = minecraft.hitResult;
        if (hitResult instanceof EntityHitResult entityHitResult
                && entityHitResult.getEntity() instanceof JSAnimalBase animal
                && isWithinObservationRange(start, animal.getBoundingBox())) {
            return animal;
        }

        Vec3 viewVector = minecraft.player.getViewVector(1.0F);
        Vec3 end = start.add(viewVector.scale(OBSERVE_RANGE));
        AABB searchBox = minecraft.player.getBoundingBox().expandTowards(viewVector.scale(OBSERVE_RANGE)).inflate(1.5D);
        double closestDistanceSqr = resolveHitDistanceLimitSqr(start, hitResult);
        JSAnimalBase closestAnimal = null;
        for (JSAnimalBase candidate : minecraft.level.getEntitiesOfClass(JSAnimalBase.class, searchBox, Entity::isAlive)) {
            AABB candidateBox = candidate.getBoundingBox().inflate(Math.max(0.15D, candidate.getPickRadius()));
            Optional<Vec3> intercept = candidateBox.clip(start, end);
            if (intercept.isEmpty()) {
                if (!candidateBox.contains(start)) {
                    continue;
                }
                intercept = Optional.of(start);
            }

            double interceptDistanceSqr = start.distanceToSqr(intercept.get());
            if (interceptDistanceSqr >= closestDistanceSqr) {
                continue;
            }
            closestDistanceSqr = interceptDistanceSqr;
            closestAnimal = candidate;
        }
        return closestAnimal;
    }

    static boolean isWithinObservationRange(Vec3 observer, AABB bounds) {
        return DinosaurObservationSystem.isWithinObservationRange(observer, bounds);
    }

    private static CaptureBoxObservationTarget.Target resolveObservedCageTarget(Minecraft minecraft) {
        if (minecraft.level == null || minecraft.player == null || !(minecraft.hitResult instanceof BlockHitResult blockHitResult)) {
            return null;
        }
        BlockPos hitPos = blockHitResult.getBlockPos();
        Optional<CaptureBoxObservationTarget.Target> target = CaptureBoxObservationTarget.resolve(
                minecraft.level,
                hitPos
        );
        if (target.isEmpty()) {
            return null;
        }
        CaptureBoxObservationTarget.Target resolved = target.orElseThrow();
        Optional<Vec3> globalEye = CaptureBoxObservationTarget.globalEye(
                minecraft.player,
                resolved.spaceIdentity()
        );
        return globalEye.isPresent() && resolved.isWithinRange(globalEye.orElseThrow()) ? resolved : null;
    }

    private static double resolveHitDistanceLimitSqr(Vec3 start, HitResult hitResult) {
        if (hitResult == null || hitResult.getType() == HitResult.Type.MISS) {
            return OBSERVE_RANGE * OBSERVE_RANGE;
        }
        return start.distanceToSqr(hitResult.getLocation());
    }

    private static Component formatAge(DinosaurObservationSnapshot snapshot) {
        OptionalDouble currentRealAgeYears = snapshot.ageEstimate().estimatedCurrentRealAgeYears();
        if (currentRealAgeYears.isPresent()) {
            return formatDetailedRealAge(currentRealAgeYears.getAsDouble());
        }

        OptionalLong currentGameAgeTicks = snapshot.ageEstimate().estimatedCurrentGameAgeTicks();
        if (currentGameAgeTicks.isPresent()) {
            double gameDays = currentGameAgeTicks.getAsLong() / 24000.0D;
            return formatValue("overlay.jsrevise.age.game", formatDecimal(gameDays));
        }

        return plainValue(Component.translatable("overlay.jsrevise.age.unknown"));
    }

    private static Component formatDetailedRealAge(double years) {
        long totalDays = Math.max(0L, (long) Math.floor(years * REAL_AGE_DAYS_PER_YEAR + 1.0E-6D));
        int wholeYears = (int) (totalDays / REAL_AGE_DAYS_PER_YEAR);
        double remainingDays = totalDays - ((long) wholeYears * REAL_AGE_DAYS_PER_YEAR);
        int months = (int) Math.floor(remainingDays / REAL_AGE_DAYS_PER_MONTH);
        int days = (int) Math.floor(remainingDays - (months * REAL_AGE_DAYS_PER_MONTH) + 1.0E-6D);
        if (wholeYears > 0) {
            if (months > 0) {
                return formatValue(
                        "overlay.jsrevise.age.real.year_month",
                        Integer.toString(wholeYears),
                        Integer.toString(months)
                );
            }
            return formatValue("overlay.jsrevise.age.real.year_only", Integer.toString(wholeYears));
        }
        if (months > 0) {
            if (days > 0) {
                return formatValue(
                        "overlay.jsrevise.age.real.month_day",
                        Integer.toString(months),
                        Integer.toString(days)
                );
            }
            return formatValue("overlay.jsrevise.age.real.month_only", Integer.toString(months));
        }
        return formatValue("overlay.jsrevise.age.real.day_only", Integer.toString(Math.max(0, days)));
    }

    private static Component formatHealth(DinosaurObservationSnapshot snapshot) {
        OptionalDouble currentHealth = snapshot.currentHealth();
        OptionalDouble maxHealth = snapshot.maxHealth();
        Component value = currentHealth.isPresent()
                ? Component.literal(
                        formatHealthValue(currentHealth.getAsDouble())
                                + (maxHealth.isPresent() ? " / " + formatHealthValue(maxHealth.getAsDouble()) : "")
                )
                : Component.translatable("overlay.jsrevise.unknown");
        return plainValue(value);
    }

    private static Component formatGender(DinosaurObservationSnapshot snapshot) {
        Component gender = snapshot.male()
                .map(male -> Component.translatable(male ? "overlay.jsrevise.gender.male" : "overlay.jsrevise.gender.female"))
                .orElseGet(() -> Component.translatable("overlay.jsrevise.unknown"));
        return plainValue(gender);
    }

    private static Component formatAnestheticDuration(long ticks) {
        return plainValue(CaptureDurationFormatter.format(ticks));
    }

    private static Component formatEggLayingProgress() {
        return plainValue(Component.translatable("overlay.jsrevise.egg_laying"));
    }

    private static Component formatValue(String translationKey, Object... values) {
        Object[] styledValues = new Object[values.length];
        for (int index = 0; index < values.length; index++) {
            Object value = values[index];
            styledValues[index] = value instanceof Component component
                    ? plainValue(component)
                    : plainValue(String.valueOf(value));
        }
        return plainValue(Component.translatable(translationKey, styledValues));
    }

    private static Component plainValue(String text) {
        return plainValue(Component.literal(text));
    }

    private static Component plainValue(Component text) {
        return text.copy().withStyle(style -> style.withBold(false));
    }

    private static int measureLineWidth(Minecraft minecraft, OverlayLine line) {
        int width = measureMainSegmentsWidth(minecraft, line);
        if (line.hasSuffix()) {
            int labelWidth = measureFirstSegmentWidth(minecraft, line);
            int suffixWidth = (int) Math.ceil(
                    minecraft.font.width(line.suffixText()) * line.suffixScale() * FONT_SCALE
            );
            width = lineWidthWithSuffix(width, labelWidth, suffixWidth, line.suffixPlacement());
        }
        if (line.hasProgress()) {
            width = Math.max(width, PROGRESS_OUTER_WIDTH);
        }
        return width;
    }

    private static int measureFirstSegmentWidth(Minecraft minecraft, OverlayLine line) {
        if (line.mainSegments().isEmpty()) {
            return 0;
        }
        OverlayTextSegment segment = line.mainSegments().getFirst();
        return (int) Math.ceil(minecraft.font.width(segment.text()) * segment.scale());
    }

    static int lineWidthWithSuffix(
            int mainWidth,
            int valueStartOffset,
            int suffixWidth,
            SuffixPlacement placement
    ) {
        int safeMainWidth = Math.max(0, mainWidth);
        int safeValueStart = Math.max(0, valueStartOffset);
        int safeSuffixWidth = Math.max(0, suffixWidth);
        return placement == SuffixPlacement.BELOW_VALUE
                ? Math.max(safeMainWidth, safeValueStart + safeSuffixWidth)
                : safeMainWidth + 4 + safeSuffixWidth;
    }

    static int suffixXOffset(int mainWidth, int valueStartOffset, SuffixPlacement placement) {
        return placement == SuffixPlacement.BELOW_VALUE
                ? Math.max(0, valueStartOffset)
                : Math.max(0, mainWidth) + 4;
    }

    private static int measureMainSegmentsWidth(Minecraft minecraft, OverlayLine line) {
        int width = 0;
        for (OverlayTextSegment segment : line.mainSegments()) {
            width += (int) Math.ceil(minecraft.font.width(segment.text()) * segment.scale());
        }
        return width;
    }

    private static void drawLine(
            GuiGraphics guiGraphics,
            Minecraft minecraft,
            int x,
            int y,
            OverlayLine line
    ) {
        int drawX = x;
        int textRowHeight = line.hasProgress() ? PROGRESS_LABEL_HEIGHT : line.height();
        if (line.suffixPlacement() == SuffixPlacement.BELOW_VALUE) {
            textRowHeight = LINE_HEIGHT;
        }
        int mainTextY = centeredTextY(minecraft, y, textRowHeight, line.mainScale());
        for (OverlayTextSegment segment : line.mainSegments()) {
            drawScaledString(
                    guiGraphics,
                    minecraft,
                    segment.text(),
                    drawX,
                    mainTextY,
                    line.color(),
                    segment.scale()
            );
            drawX += (int) Math.ceil(minecraft.font.width(segment.text()) * segment.scale());
        }
        if (line.hasProgress()) {
            drawProgressBar(
                    guiGraphics,
                    x,
                    y + PROGRESS_BAR_Y_OFFSET,
                    line.progress(),
                    line.progressKnown(),
                    line.progressDarkColor(),
                    line.progressLightColor()
            );
        }
        if (!line.hasSuffix()) {
            return;
        }

        int mainWidth = measureMainSegmentsWidth(minecraft, line);
        int valueStartOffset = measureFirstSegmentWidth(minecraft, line);
        float suffixScale = line.suffixScale() * FONT_SCALE;
        boolean belowValue = line.suffixPlacement() == SuffixPlacement.BELOW_VALUE;
        drawScaledString(
                guiGraphics,
                minecraft,
                line.suffixText(),
                x + suffixXOffset(mainWidth, valueStartOffset, line.suffixPlacement()),
                centeredTextY(
                        minecraft,
                        belowValue ? y + LINE_HEIGHT : y,
                        belowValue ? LINE_HEIGHT : textRowHeight,
                        suffixScale
                ),
                line.suffixColor(),
                suffixScale
        );
    }

    private static void drawProgressBar(
            GuiGraphics guiGraphics,
            int x,
            int y,
            double progress,
            boolean progressKnown,
            int darkColor,
            int lightColor
    ) {
        int trackX = x + PROGRESS_BAR_BORDER_WIDTH;
        int trackY = y + PROGRESS_BAR_BORDER_WIDTH;
        int trackRight = trackX + PROGRESS_BAR_WIDTH;
        int trackBottom = trackY + PROGRESS_BAR_HEIGHT;
        guiGraphics.fill(
                trackX,
                trackY,
                trackRight,
                trackBottom,
                PROGRESS_TRACK_COLOR
        );
        int fillWidth = progressFillWidth(progress, progressKnown);
        if (fillWidth > 0) {
            fillVerticalGradient(
                    guiGraphics,
                    trackX,
                    trackY,
                    trackX + fillWidth,
                    trackBottom,
                    lightColor,
                    darkColor
            );
        }

        int outerRight = x + PROGRESS_OUTER_WIDTH;
        int outerBottom = y + PROGRESS_OUTER_HEIGHT;
        guiGraphics.fill(x, y, outerRight, trackY, PROGRESS_BORDER_COLOR);
        guiGraphics.fill(x, trackBottom, outerRight, outerBottom, PROGRESS_BORDER_COLOR);
        guiGraphics.fill(x, trackY, trackX, trackBottom, PROGRESS_BORDER_COLOR);
        guiGraphics.fill(trackRight, trackY, outerRight, trackBottom, PROGRESS_BORDER_COLOR);
    }

    static int progressFillWidth(double progress, boolean progressKnown) {
        return progressKnown ? Mth.floor(PROGRESS_BAR_WIDTH * clampProgress(progress)) : 0;
    }

    private static void fillVerticalGradient(
            GuiGraphics guiGraphics,
            int left,
            int top,
            int right,
            int bottom,
            int topColor,
            int bottomColor
    ) {
        int height = bottom - top;
        for (int row = 0; row < height; row++) {
            float progress = height <= 1 ? 0.0F : (float) row / (height - 1);
            guiGraphics.fill(left, top + row, right, top + row + 1, lerpArgb(topColor, bottomColor, progress));
        }
    }

    static double clampProgress(double progress) {
        if (!Double.isFinite(progress)) {
            return 0.0D;
        }
        return Math.max(0.0D, Math.min(1.0D, progress));
    }

    private static void drawScaledString(
            GuiGraphics guiGraphics,
            Minecraft minecraft,
            Component text,
            int x,
            int y,
            int color,
            float scale
    ) {
        guiGraphics.pose().pushPose();
        guiGraphics.pose().scale(scale, scale, 1.0F);
        guiGraphics.drawString(
                minecraft.font,
                text.getVisualOrderText(),
                x / scale,
                y / scale,
                color,
                false
        );
        guiGraphics.pose().popPose();
    }

    private static GeneLayout computeGeneLayout(Minecraft minecraft, List<ObservedGene> genes) {
        int widestLabel = 0;
        for (ObservedGene gene : genes) {
            widestLabel = Math.max(widestLabel, (int) Math.ceil(minecraft.font.width(gene.displayName()) * GENE_LABEL_SCALE));
        }
        int columns = Math.min(genes.size(), widestLabel > 52 ? 3 : 4);
        int cellWidth = Mth.clamp(Math.max(GENE_ICON_SIZE + 6, widestLabel + PANEL_PADDING), 40, 96);
        int labelHeight = Math.max(6, (int) Math.ceil(minecraft.font.lineHeight * GENE_LABEL_SCALE));
        int cellHeight = GENE_ICON_SIZE + 3 + labelHeight;
        int rows = Mth.ceil(genes.size() / (double) columns);
        int totalWidth = columns * cellWidth + Math.max(0, columns - 1) * GENE_COLUMN_GAP;
        int totalHeight = rows * cellHeight + Math.max(0, rows - 1) * GENE_ROW_GAP;
        return new GeneLayout(columns, cellWidth, cellHeight, totalWidth, totalHeight);
    }

    private static void drawGenes(GuiGraphics guiGraphics, Minecraft minecraft, List<ObservedGene> genes, int x, int y, GeneLayout layout) {
        float iconScale = GENE_ICON_SIZE / 16.0F;
        for (int index = 0; index < genes.size(); index++) {
            ObservedGene gene = genes.get(index);
            int column = index % layout.columns();
            int row = index / layout.columns();
            int cellX = x + column * (layout.cellWidth() + GENE_COLUMN_GAP);
            int cellY = y + row * (layout.cellHeight() + GENE_ROW_GAP);
            int iconX = cellX + (layout.cellWidth() - GENE_ICON_SIZE) / 2;
            ItemStack geneStack = createGeneStack(gene.itemId());
            if (!geneStack.isEmpty()) {
                guiGraphics.pose().pushPose();
                guiGraphics.pose().translate(iconX, cellY, 0.0F);
                guiGraphics.pose().scale(iconScale, iconScale, 1.0F);
                guiGraphics.renderItem(geneStack, 0, 0);
                guiGraphics.pose().popPose();
            }
            drawScaledCenteredString(
                    guiGraphics,
                    minecraft,
                    gene.displayName(),
                    cellX + layout.cellWidth() / 2,
                    cellY + GENE_ICON_SIZE + 3,
                    0x7D8794,
                    GENE_LABEL_SCALE
            );
        }
    }

    private static void drawScaledCenteredString(
            GuiGraphics guiGraphics,
            Minecraft minecraft,
            Component text,
            int centerX,
            int y,
            int color,
            float scale
    ) {
        int scaledWidth = (int) Math.ceil(minecraft.font.width(text) * scale);
        float drawX = centerX - (scaledWidth / 2.0F);
        guiGraphics.pose().pushPose();
        guiGraphics.pose().scale(scale, scale, 1.0F);
        guiGraphics.drawString(
                minecraft.font,
                text.getVisualOrderText(),
                drawX / scale,
                y / scale,
                color,
                false
        );
        guiGraphics.pose().popPose();
    }

    private static ItemStack createGeneStack(ResourceLocation itemId) {
        if (itemId == null || !BuiltInRegistries.ITEM.containsKey(itemId)) {
            return ItemStack.EMPTY;
        }
        Item item = BuiltInRegistries.ITEM.get(itemId);
        return item == null ? ItemStack.EMPTY : new ItemStack(item);
    }

    private static String formatDecimal(double value) {
        return String.format(Locale.ROOT, "%.1f", value);
    }

    private static String formatHealthValue(double value) {
        return String.format(Locale.ROOT, value >= 100.0D ? "%.0f" : "%.1f", value);
    }

    enum CapturePanelLineKind {
        TITLE,
        PROGRESS,
        INFO,
        WARNING
    }

    enum SuffixPlacement {
        INLINE,
        BELOW_VALUE
    }

    record CapturePanelLine(
            CapturePanelLineKind kind,
            Component label,
            Component value,
            int color,
            double progress,
            int progressDarkColor,
            int progressLightColor
    ) {
        CapturePanelLine {
            if (kind == null) {
                throw new IllegalArgumentException("Capture panel line kind is required");
            }
            label = label == null ? Component.empty() : label;
            value = value == null ? Component.empty() : value;
            progress = clampProgress(progress);
        }

        private static CapturePanelLine title(Component title, int color) {
            return new CapturePanelLine(
                    CapturePanelLineKind.TITLE,
                    title.copy().withStyle(ChatFormatting.BOLD),
                    Component.empty(),
                    color,
                    0.0D,
                    0,
                    0
            );
        }

        private static CapturePanelLine progress(
                Component label,
                int color,
                int amount,
                int capacity,
                double progress,
                int darkColor,
                int lightColor
        ) {
            int safeCapacity = Math.max(0, capacity);
            int safeAmount = Math.max(0, Math.min(safeCapacity, amount));
            return new CapturePanelLine(
                    CapturePanelLineKind.PROGRESS,
                    plainValue(label),
                    plainValue(safeAmount + "/" + safeCapacity),
                    color,
                    progress,
                    darkColor,
                    lightColor
            );
        }

        private static CapturePanelLine info(Component label, Component value, int color) {
            return new CapturePanelLine(
                    CapturePanelLineKind.INFO,
                    plainValue(label),
                    plainValue(value),
                    color,
                    0.0D,
                    0,
                    0
            );
        }

        private static CapturePanelLine warning(Component warning, int color) {
            return new CapturePanelLine(
                    CapturePanelLineKind.WARNING,
                    plainValue(warning),
                    Component.empty(),
                    color,
                    0.0D,
                    0,
                    0
            );
        }

        boolean hasProgress() {
            return this.kind == CapturePanelLineKind.PROGRESS;
        }

        int height() {
            return switch (this.kind) {
                case TITLE -> CAPTURE_TITLE_LINE_HEIGHT;
                case PROGRESS -> PROGRESS_LINE_HEIGHT;
                case INFO, WARNING -> CAPTURE_INFO_LINE_HEIGHT;
            };
        }
    }

    record OverlayLine(
            List<OverlayTextSegment> mainSegments,
            int color,
            Component suffixText,
            int suffixColor,
            float suffixScale,
            SuffixPlacement suffixPlacement,
            double progress,
            boolean progressKnown,
            boolean progressLine,
            int progressDarkColor,
            int progressLightColor,
            float mainScale,
            int lineHeight
    ) {
        OverlayLine {
            mainSegments = List.copyOf(mainSegments);
            suffixPlacement = suffixPlacement == null ? SuffixPlacement.INLINE : suffixPlacement;
            progress = clampProgress(progress);
        }

        private static OverlayLine labeled(Component label, Component value, int color) {
            return new OverlayLine(
                    List.of(OverlayTextSegment.label(label), OverlayTextSegment.value(value)),
                    color,
                    Component.empty(),
                    color,
                    1.0F,
                    SuffixPlacement.INLINE,
                    0.0D,
                    false,
                    false,
                    EGG_PROGRESS_DARK_COLOR,
                    EGG_PROGRESS_LIGHT_COLOR,
                    LABEL_FONT_SCALE,
                    LINE_HEIGHT
            );
        }

        private static OverlayLine singleLabel(Component label, int color) {
            return new OverlayLine(
                    List.of(OverlayTextSegment.label(label)),
                    color,
                    Component.empty(),
                    color,
                    1.0F,
                    SuffixPlacement.INLINE,
                    0.0D,
                    false,
                    false,
                    EGG_PROGRESS_DARK_COLOR,
                    EGG_PROGRESS_LIGHT_COLOR,
                    LABEL_FONT_SCALE,
                    LINE_HEIGHT
            );
        }

        private static OverlayLine title(Component mainText, int color) {
            return new OverlayLine(
                    List.of(OverlayTextSegment.title(mainText)),
                    color,
                    Component.empty(),
                    color,
                    1.0F,
                    SuffixPlacement.INLINE,
                    0.0D,
                    false,
                    false,
                    EGG_PROGRESS_DARK_COLOR,
                    EGG_PROGRESS_LIGHT_COLOR,
                    TITLE_FONT_SCALE,
                    TITLE_LINE_HEIGHT
            );
        }

        private static OverlayLine labeledWithSuffix(
                Component label,
                Component value,
                int color,
                Component suffixText,
                int suffixColor,
                float suffixScale
        ) {
            SuffixPlacement placement = suffixText.getString().isEmpty()
                    ? SuffixPlacement.INLINE
                    : SuffixPlacement.BELOW_VALUE;
            return withSuffix(
                    List.of(OverlayTextSegment.label(label), OverlayTextSegment.value(value)),
                    color,
                    suffixText,
                    suffixColor,
                    suffixScale,
                    placement
            );
        }

        private static OverlayLine withSuffix(
                List<OverlayTextSegment> mainSegments,
                int color,
                Component suffixText,
                int suffixColor,
                float suffixScale
        ) {
            return withSuffix(
                    mainSegments,
                    color,
                    suffixText,
                    suffixColor,
                    suffixScale,
                    SuffixPlacement.INLINE
            );
        }

        private static OverlayLine withSuffix(
                List<OverlayTextSegment> mainSegments,
                int color,
                Component suffixText,
                int suffixColor,
                float suffixScale,
                SuffixPlacement suffixPlacement
        ) {
            return new OverlayLine(
                    mainSegments,
                    color,
                    suffixText,
                    suffixColor,
                    suffixScale,
                    suffixPlacement,
                    0.0D,
                    false,
                    false,
                    EGG_PROGRESS_DARK_COLOR,
                    EGG_PROGRESS_LIGHT_COLOR,
                    LABEL_FONT_SCALE,
                    suffixPlacement == SuffixPlacement.BELOW_VALUE ? LINE_HEIGHT * 2 : LINE_HEIGHT
            );
        }

        private static OverlayLine progress(Component mainText, int color, double progress) {
            return progress(mainText, color, progress, EGG_PROGRESS_DARK_COLOR, EGG_PROGRESS_LIGHT_COLOR);
        }

        private static OverlayLine progress(
                Component mainText,
                int color,
                double progress,
                int progressDarkColor,
                int progressLightColor
        ) {
            return new OverlayLine(
                    List.of(OverlayTextSegment.label(mainText)),
                    color,
                    Component.empty(),
                    color,
                    1.0F,
                    SuffixPlacement.INLINE,
                    progress,
                    true,
                    true,
                    progressDarkColor,
                    progressLightColor,
                    LABEL_FONT_SCALE,
                    PROGRESS_LINE_HEIGHT
            );
        }

        private static OverlayLine progressWithInlineValue(
                Component mainText,
                Component value,
                int color,
                double progress,
                boolean progressKnown,
                int progressDarkColor,
                int progressLightColor
        ) {
            return new OverlayLine(
                    List.of(OverlayTextSegment.label(mainText), OverlayTextSegment.value(value)),
                    color,
                    Component.empty(),
                    color,
                    1.0F,
                    SuffixPlacement.INLINE,
                    progress,
                    progressKnown,
                    true,
                    progressDarkColor,
                    progressLightColor,
                    LABEL_FONT_SCALE,
                    PROGRESS_LINE_HEIGHT
            );
        }

        boolean hasSuffix() {
            return !this.suffixText.getString().isEmpty();
        }

        boolean hasProgress() {
            return this.progressLine;
        }

        int height() {
            return this.lineHeight;
        }

        Component mainText() {
            if (this.mainSegments.size() == 1) {
                return this.mainSegments.get(0).text();
            }
            MutableComponent text = Component.empty();
            for (OverlayTextSegment segment : this.mainSegments) {
                text.append(segment.text());
            }
            return text;
        }
    }

    record OverlayTextSegment(Component text, float scale) {
        private static OverlayTextSegment label(Component text) {
            return new OverlayTextSegment(plainValue(text), LABEL_FONT_SCALE);
        }

        private static OverlayTextSegment value(Component text) {
            return new OverlayTextSegment(plainValue(text), FONT_SCALE);
        }

        private static OverlayTextSegment title(Component text) {
            return new OverlayTextSegment(text.copy().withStyle(ChatFormatting.BOLD), TITLE_FONT_SCALE);
        }
    }

    private record PanelMeasurements(
            int maxContentWidth,
            int panelWidth,
            int panelHeight,
            GeneLayout geneLayout
    ) {
        private static PanelMeasurements empty() {
            return new PanelMeasurements(0, 0, 0, GeneLayout.empty());
        }
    }

    private record CapturePanelMeasurements(
            int panelWidth,
            int panelHeight
    ) {
    }

    private record GeneLayout(int columns, int cellWidth, int cellHeight, int totalWidth, int totalHeight) {
        private static GeneLayout empty() {
            return new GeneLayout(1, 0, 0, 0, 0);
        }
    }

    private record ObservedTarget(JSAnimalBase animal, CaptureBoxObservationTarget.Target cageTarget) {
        static ObservedTarget animal(JSAnimalBase animal) {
            return new ObservedTarget(animal, null);
        }

        static ObservedTarget cage(CaptureBoxObservationTarget.Target cageTarget) {
            return new ObservedTarget(null, cageTarget);
        }

        BlockPos cagePos() {
            return this.cageTarget == null ? null : this.cageTarget.controller();
        }

        boolean isStillValid(Minecraft minecraft) {
            if (minecraft.level == null || minecraft.player == null) {
                return false;
            }
            if (this.animal != null) {
                return this.animal.isAlive()
                        && !this.animal.isRemoved()
                        && isWithinObservationRange(minecraft.player.getEyePosition(), this.animal.getBoundingBox());
            }
            if (this.cageTarget == null) {
                return false;
            }
            Optional<CaptureBoxObservationTarget.Target> current = CaptureBoxObservationTarget.resolve(
                    minecraft.level,
                    this.cageTarget.controller()
            );
            if (current.isEmpty()
                    || !current.orElseThrow().spaceIdentity().equals(this.cageTarget.spaceIdentity())
                    || current.orElseThrow().cage() != this.cageTarget.cage()) {
                return false;
            }
            CaptureBoxObservationTarget.Target resolved = current.orElseThrow();
            return CaptureBoxObservationTarget.globalEye(minecraft.player, resolved.spaceIdentity())
                    .filter(resolved::isWithinRange)
                    .isPresent();
        }
    }

    static boolean isCaptureBoxObservationBlock(BlockState state) {
        return state.is(JSReviseBlocks.DINOSAUR_CAPTURE_CAGE.get());
    }
}
