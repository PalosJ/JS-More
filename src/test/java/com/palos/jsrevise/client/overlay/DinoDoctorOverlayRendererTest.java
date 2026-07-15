package com.palos.jsrevise.client.overlay;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.palos.jsrevise.server.registry.JSReviseBlocks;
import com.palos.jsrevise.server.system.age.DinosaurAgeEstimate;
import com.palos.jsrevise.server.system.capture.CapturedDinosaurData;
import com.palos.jsrevise.server.system.size.DinosaurLifecycleStage;
import com.palos.jsrevise.system.observation.CaptureCageObservationSnapshot;
import com.palos.jsrevise.system.observation.DinosaurObservationSnapshot;
import com.palos.jsrevise.system.observation.EggLayingProgress;
import com.palos.jsrevise.system.observation.ObservedGene;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalLong;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class DinoDoctorOverlayRendererTest {
    @Test
    void enforcesTheEightBlockObservationRangeAgainstEntityBounds() {
        Vec3 observer = Vec3.ZERO;

        assertTrue(DinoDoctorOverlayRenderer.isWithinObservationRange(
                observer,
                new AABB(8.0D, -0.5D, -0.5D, 9.0D, 0.5D, 0.5D)
        ));
        assertFalse(DinoDoctorOverlayRenderer.isWithinObservationRange(
                observer,
                new AABB(8.01D, -0.5D, -0.5D, 9.0D, 0.5D, 0.5D)
        ));
    }

    @Test
    void clampsEggLayingProgressBarValues() {
        assertEquals(0.0D, DinoDoctorOverlayRenderer.clampProgress(Double.NaN), 1.0E-9D);
        assertEquals(0.0D, DinoDoctorOverlayRenderer.clampProgress(-0.5D), 1.0E-9D);
        assertEquals(0.5D, DinoDoctorOverlayRenderer.clampProgress(0.5D), 1.0E-9D);
        assertEquals(1.0D, DinoDoctorOverlayRenderer.clampProgress(1.5D), 1.0E-9D);
    }

    @Test
    void calculatesFillInsideTheBorderedSixtySixPixelTrack() {
        assertEquals(0, DinoDoctorOverlayRenderer.progressFillWidth(1.0D, false));
        assertEquals(0, DinoDoctorOverlayRenderer.progressFillWidth(Double.NaN, true));
        assertEquals(0, DinoDoctorOverlayRenderer.progressFillWidth(-0.5D, true));
        assertEquals(33, DinoDoctorOverlayRenderer.progressFillWidth(0.5D, true));
        assertEquals(66, DinoDoctorOverlayRenderer.progressFillWidth(1.0D, true));
        assertEquals(66, DinoDoctorOverlayRenderer.progressFillWidth(1.5D, true));
    }

    @Test
    void keepsTheObservedAnimalNameAsLargeBoldTitleAndLabelsBetweenTitleAndValues() {
        List<DinoDoctorOverlayRenderer.OverlayLine> lines = DinoDoctorOverlayRenderer.createObservationLines(
                snapshotWithEggProgressAndGenes()
        );

        DinoDoctorOverlayRenderer.OverlayLine title = lines.get(0);
        DinoDoctorOverlayRenderer.OverlayTextSegment titleSegment = title.mainSegments().get(0);
        assertTrue(titleSegment.text().getStyle().isBold());
        assertEquals(DinoDoctorOverlayRenderer.TITLE_FONT_SCALE, titleSegment.scale(), 1.0E-6F);
        assertEquals(DinoDoctorOverlayRenderer.TITLE_FONT_SCALE, title.mainScale(), 1.0E-6F);
        assertEquals(DinoDoctorOverlayRenderer.TITLE_LINE_HEIGHT, title.height());
        for (int index = 1; index < lines.size(); index++) {
            DinoDoctorOverlayRenderer.OverlayLine line = lines.get(index);
            DinoDoctorOverlayRenderer.OverlayTextSegment labelSegment = line.mainSegments().get(0);
            assertFalse(labelSegment.text().getStyle().isBold());
            assertEquals(DinoDoctorOverlayRenderer.LABEL_FONT_SCALE, labelSegment.scale(), 1.0E-6F);
            assertEquals(DinoDoctorOverlayRenderer.FONT_SCALE, labelSegment.scale(), 1.0E-6F);
            assertTrue(labelSegment.scale() < DinoDoctorOverlayRenderer.TITLE_FONT_SCALE);
        }
    }

    @Test
    void keepsLabelValuesAtOneScaleAndAnestheticSuffixVisuallySecondary() {
        List<DinoDoctorOverlayRenderer.OverlayLine> lines = DinoDoctorOverlayRenderer.createObservationLines(
                snapshotWithEggProgressAndGenes()
        );

        assertLabelValueLine(lines.get(1), "overlay.jsrevise.age.label");
        assertLabelValueLine(lines.get(2), "overlay.jsrevise.health");
        assertLabelValueLine(lines.get(3), "overlay.jsrevise.gender");
        assertInlineProgressLine(
                lines.get(4),
                "overlay.jsrevise.hunger",
                "52.0%",
                0.52D,
                DinoDoctorOverlayRenderer.HUNGER_PROGRESS_DARK_COLOR,
                DinoDoctorOverlayRenderer.HUNGER_PROGRESS_LIGHT_COLOR
        );
        assertInlineProgressLine(
                lines.get(5),
                "overlay.jsrevise.thirst",
                "63.0%",
                0.63D,
                DinoDoctorOverlayRenderer.THIRST_PROGRESS_DARK_COLOR,
                DinoDoctorOverlayRenderer.THIRST_PROGRESS_LIGHT_COLOR
        );
        assertInlineProgressLine(
                lines.get(6),
                "overlay.jsrevise.mood",
                "74.0%",
                0.74D,
                DinoDoctorOverlayRenderer.MOOD_PROGRESS_DARK_COLOR,
                DinoDoctorOverlayRenderer.MOOD_PROGRESS_LIGHT_COLOR
        );
        assertLabelValueLine(lines.get(7), "overlay.jsrevise.anesthetic.remaining");
        assertLabelValueLine(lines.get(8), "overlay.jsrevise.anesthetic.pending");
        assertSingleRegularLabel(lines.get(9), "overlay.jsrevise.genes");

        DinoDoctorOverlayRenderer.OverlayLine anestheticRemaining = lines.get(7);
        assertTrue(anestheticRemaining.hasSuffix());
        assertEquals(DinoDoctorOverlayRenderer.SuffixPlacement.BELOW_VALUE,
                anestheticRemaining.suffixPlacement());
        assertEquals(DinoDoctorOverlayRenderer.LINE_HEIGHT * 2, anestheticRemaining.height());
        assertFalse(anestheticRemaining.suffixText().getStyle().isBold());
        assertRegularTranslatableArguments(anestheticRemaining.suffixText(), 1);
        assertEquals(0.75F, anestheticRemaining.suffixScale(), 1.0E-6F);
        assertEquals((0.75F * 0.98F) / 0.85F,
                anestheticRemaining.suffixScale() * DinoDoctorOverlayRenderer.FONT_SCALE, 1.0E-6F);
    }

    @Test
    void createsEggLayingProgressAsSeparateBottomLineWithoutSeconds() {
        List<DinoDoctorOverlayRenderer.OverlayLine> lines = DinoDoctorOverlayRenderer.createObservationLines(
                snapshotWithEggProgressAndGenes()
        );
        Optional<DinoDoctorOverlayRenderer.OverlayLine> progressLine = DinoDoctorOverlayRenderer.createEggProgressLine(
                snapshotWithEggProgressAndGenes()
        );

        DinoDoctorOverlayRenderer.OverlayLine genes = lines.get(lines.size() - 1);
        assertTrue(progressLine.isPresent());
        DinoDoctorOverlayRenderer.OverlayLine progress = progressLine.get();
        assertTrue(progress.hasProgress());
        assertFalse(progress.mainSegments().get(0).text().getStyle().isBold());
        assertEquals(DinoDoctorOverlayRenderer.LABEL_FONT_SCALE, progress.mainSegments().get(0).scale(), 1.0E-6F);
        assertFalse(genes.hasProgress());
        assertEquals("overlay.jsrevise.genes", ((TranslatableContents) genes.mainText().getContents()).getKey());
        assertEquals(12, DinoDoctorOverlayRenderer.LINE_HEIGHT);
        assertEquals(14, DinoDoctorOverlayRenderer.TITLE_LINE_HEIGHT);
        assertEquals(DinoDoctorOverlayRenderer.PROGRESS_LINE_HEIGHT, progress.height());
        assertEquals(DinoDoctorOverlayRenderer.PROGRESS_LABEL_HEIGHT, DinoDoctorOverlayRenderer.PROGRESS_BAR_Y_OFFSET);
        assertEquals(
                DinoDoctorOverlayRenderer.PROGRESS_BAR_Y_OFFSET
                        + DinoDoctorOverlayRenderer.PROGRESS_OUTER_HEIGHT
                        + DinoDoctorOverlayRenderer.PROGRESS_BOTTOM_GAP,
                DinoDoctorOverlayRenderer.PROGRESS_LINE_HEIGHT
        );
        assertEquals(66, DinoDoctorOverlayRenderer.PROGRESS_BAR_WIDTH);
        assertEquals(7, DinoDoctorOverlayRenderer.PROGRESS_BAR_HEIGHT);
        assertEquals(68, DinoDoctorOverlayRenderer.PROGRESS_OUTER_WIDTH);
        assertEquals(9, DinoDoctorOverlayRenderer.PROGRESS_OUTER_HEIGHT);
        assertEquals(1, progress.mainSegments().size());

        TranslatableContents eggLabel = assertInstanceOf(TranslatableContents.class, progress.mainText().getContents());
        assertEquals("overlay.jsrevise.egg_laying", eggLabel.getKey());
        assertEquals(0, eggLabel.getArgs().length);
    }

    @Test
    void captureCageLeftPanelContainsOnlyDinosaurObservationLines() {
        int durability = CapturedDinosaurData.MAX_DURABILITY * 3 / 4;
        CaptureCageObservationSnapshot snapshot = CaptureCageObservationSnapshot.from(
                ResourceLocation.fromNamespaceAndPath("jurassicsaga", "test_dino"),
                snapshotWithEggProgressAndGenes(),
                400L,
                durability
        );

        List<DinoDoctorOverlayRenderer.OverlayLine> lines =
                DinoDoctorOverlayRenderer.createCaptureCageObservationLines(snapshot);

        assertEquals(DinoDoctorOverlayRenderer.createObservationLines(snapshot.observation()), lines);
        assertLabelValueLine(lines.get(7), "overlay.jsrevise.anesthetic.remaining");
        assertLabelValueLine(lines.get(8), "overlay.jsrevise.anesthetic.pending");
        assertSingleRegularLabel(lines.getLast(), "overlay.jsrevise.genes");
    }

    @Test
    void captureBoxRightPanelUsesOrderedColoredBarsThenDurationAndDurability() {
        CaptureBoxHudViewModel viewModel = CaptureBoxHudViewModel.occupied(
                40, 15, 10, 5, false, 72_000L, 15
        );

        List<DinoDoctorOverlayRenderer.CapturePanelLine> lines =
                DinoDoctorOverlayRenderer.createCaptureBoxLines(viewModel);

        assertCaptureTranslation(lines.get(0), "overlay.jsrevise.capture_box.title");
        assertEquals(0xDDEEFF, lines.get(0).color());
        assertCaptureProgressLine(
                lines.get(1),
                "overlay.jsrevise.capture_box.anesthetic_reserve",
                1.0D,
                "40/40",
                DinoDoctorOverlayRenderer.ANESTHETIC_PROGRESS_DARK_COLOR,
                DinoDoctorOverlayRenderer.ANESTHETIC_PROGRESS_LIGHT_COLOR
        );
        assertCaptureProgressLine(
                lines.get(2),
                "overlay.jsrevise.capture_box.water_reserve",
                0.75D,
                "15/20",
                DinoDoctorOverlayRenderer.WATER_PROGRESS_DARK_COLOR,
                DinoDoctorOverlayRenderer.WATER_PROGRESS_LIGHT_COLOR
        );
        assertCaptureProgressLine(
                lines.get(3),
                "overlay.jsrevise.capture_box.carnivore_reserve",
                0.5D,
                "10/20",
                DinoDoctorOverlayRenderer.CARNIVORE_PROGRESS_DARK_COLOR,
                DinoDoctorOverlayRenderer.CARNIVORE_PROGRESS_LIGHT_COLOR
        );
        assertCaptureProgressLine(
                lines.get(4),
                "overlay.jsrevise.capture_box.herbivore_reserve",
                0.25D,
                "5/20",
                DinoDoctorOverlayRenderer.HERBIVORE_PROGRESS_DARK_COLOR,
                DinoDoctorOverlayRenderer.HERBIVORE_PROGRESS_LIGHT_COLOR
        );
        assertCaptureTranslation(lines.get(5), "overlay.jsrevise.capture_box.duration");
        TranslatableContents duration = assertInstanceOf(
                TranslatableContents.class,
                lines.get(5).value().getContents()
        );
        assertEquals("format.jsrevise.duration.hours", duration.getKey());
        assertCaptureTranslation(lines.get(6), "overlay.jsrevise.capture_box.durability");
        assertEquals("75%", lines.get(6).value().getString());
        assertTrue(List.of(DinoDoctorOverlayRenderer.CapturePanelLine.class.getRecordComponents()).stream()
                .noneMatch(component -> component.getName().equals("capacity")));
    }

    @Test
    void allEightHudProgressRowsShareTheSameGeometry() {
        List<DinoDoctorOverlayRenderer.OverlayLine> observationLines =
                DinoDoctorOverlayRenderer.createObservationLines(snapshotWithEggProgressAndGenes());
        DinoDoctorOverlayRenderer.OverlayLine eggLine = DinoDoctorOverlayRenderer.createEggProgressLine(
                snapshotWithEggProgressAndGenes()
        ).orElseThrow();
        List<DinoDoctorOverlayRenderer.CapturePanelLine> captureLines =
                DinoDoctorOverlayRenderer.createCaptureBoxLines(
                        CaptureBoxHudViewModel.occupied(20, 10, 10, 10, false, 1L, 20)
                );

        assertEquals(3, observationLines.stream().filter(DinoDoctorOverlayRenderer.OverlayLine::hasProgress).count());
        assertEquals(DinoDoctorOverlayRenderer.PROGRESS_LINE_HEIGHT, eggLine.height());
        assertEquals(4, captureLines.stream().filter(DinoDoctorOverlayRenderer.CapturePanelLine::hasProgress).count());
        assertTrue(observationLines.stream()
                .filter(DinoDoctorOverlayRenderer.OverlayLine::hasProgress)
                .allMatch(line -> line.height() == DinoDoctorOverlayRenderer.PROGRESS_LINE_HEIGHT));
        assertTrue(captureLines.stream()
                .filter(DinoDoctorOverlayRenderer.CapturePanelLine::hasProgress)
                .allMatch(line -> line.height() == DinoDoctorOverlayRenderer.PROGRESS_LINE_HEIGHT));
    }

    @Test
    void emptyAndRecoveryCaptureBoxPanelsDoNotInventCapturedData() {
        List<DinoDoctorOverlayRenderer.CapturePanelLine> emptyLines = DinoDoctorOverlayRenderer.createCaptureBoxLines(
                CaptureBoxHudViewModel.empty(0, 0, 0, 0, false)
        );
        assertEquals(6, emptyLines.size());
        assertCaptureTranslation(emptyLines.getLast(), "overlay.jsrevise.capture_box.durability");
        assertEquals("100%", emptyLines.getLast().value().getString());

        List<DinoDoctorOverlayRenderer.CapturePanelLine> recoveryLines = DinoDoctorOverlayRenderer.createCaptureBoxLines(
                CaptureBoxHudViewModel.unavailable(1, 2, 3, 4, false, true)
        );
        assertEquals(6, recoveryLines.size());
        assertCaptureTranslation(recoveryLines.getLast(), "tooltip.jsrevise.dinosaur_capture_box.unreadable");
    }

    @Test
    void brokenCaptureBoxIsNotAValidHudCaptureBoxTarget() {
        assertTrue(DinoDoctorOverlayRenderer.isCaptureBoxObservationBlock(
                JSReviseBlocks.DINOSAUR_CAPTURE_CAGE.get().defaultBlockState()
        ));
        assertFalse(DinoDoctorOverlayRenderer.isCaptureBoxObservationBlock(
                JSReviseBlocks.BROKEN_DINOSAUR_CAPTURE_BOX.get().defaultBlockState()
        ));
    }

    @Test
    void omitsEggLayingProgressLineWhenSnapshotHasNoProgress() {
        Optional<DinoDoctorOverlayRenderer.OverlayLine> progressLine = DinoDoctorOverlayRenderer.createEggProgressLine(
                snapshotWithEggProgressAndGenes().withEggLayingProgress(Optional.empty())
        );

        assertTrue(progressLine.isEmpty());
    }

    @Test
    void exposesCompactBottomPaddingAndWhitePanelBorderColorsForLayoutVerification() {
        assertEquals(8, DinoDoctorOverlayRenderer.PANEL_TOP_PADDING);
        assertEquals(4, DinoDoctorOverlayRenderer.PANEL_BOTTOM_PADDING);
        assertEquals(0xFFFFFF, DinoDoctorOverlayRenderer.PANEL_BORDER_TOP_COLOR & 0xFFFFFF);
        assertEquals(0xFFFFFF, DinoDoctorOverlayRenderer.PANEL_BORDER_BOTTOM_COLOR & 0xFFFFFF);
        assertEquals(0x680C1824, DinoDoctorOverlayRenderer.panelBackgroundTopColor(0x336699));
        assertEquals(0x7208111A, DinoDoctorOverlayRenderer.panelBackgroundBottomColor(0x336699));
        assertNotEquals(0xFFFFFF, DinoDoctorOverlayRenderer.panelBackgroundTopColor(0x336699) & 0xFFFFFF);
        assertNotEquals(0xFFFFFF, DinoDoctorOverlayRenderer.panelBackgroundBottomColor(0x336699) & 0xFFFFFF);
    }

    @Test
    void exposesUnifiedProgressAndExpandedCapturePanelMetrics() {
        assertEquals(0xFFEED1AF, DinoDoctorOverlayRenderer.EGG_PROGRESS_DARK_COLOR);
        assertEquals(0xFFFFEDC6, DinoDoctorOverlayRenderer.EGG_PROGRESS_LIGHT_COLOR);
        assertEquals(3, DinoDoctorOverlayRenderer.CAPTURE_PANEL_RADIUS);
        assertEquals(1, DinoDoctorOverlayRenderer.CAPTURE_PANEL_BORDER_WIDTH);
        assertEquals(0xA9A79E, DinoDoctorOverlayRenderer.CAPTURE_PANEL_THEME_COLOR);
        assertEquals(0xDA282825, DinoDoctorOverlayRenderer.capturePanelBackgroundTopColor());
        assertEquals(0xE61C1C1A, DinoDoctorOverlayRenderer.capturePanelBackgroundBottomColor());
        assertEquals(0x68282825, DinoDoctorOverlayRenderer.panelBackgroundTopColor(
                DinoDoctorOverlayRenderer.CAPTURE_PANEL_THEME_COLOR
        ));
        assertEquals(0x721C1C1A, DinoDoctorOverlayRenderer.panelBackgroundBottomColor(
                DinoDoctorOverlayRenderer.CAPTURE_PANEL_THEME_COLOR
        ));
        assertEquals(0xFFBFD1E6, DinoDoctorOverlayRenderer.PROGRESS_BORDER_COLOR);
        assertEquals(0x2EFFFFFF, DinoDoctorOverlayRenderer.PROGRESS_TRACK_COLOR);
        assertEquals(66, DinoDoctorOverlayRenderer.PROGRESS_BAR_WIDTH);
        assertEquals(7, DinoDoctorOverlayRenderer.PROGRESS_BAR_HEIGHT);
        assertEquals(1, DinoDoctorOverlayRenderer.PROGRESS_BAR_BORDER_WIDTH);
        assertEquals(68, DinoDoctorOverlayRenderer.PROGRESS_OUTER_WIDTH);
        assertEquals(9, DinoDoctorOverlayRenderer.PROGRESS_OUTER_HEIGHT);
        assertEquals(12, DinoDoctorOverlayRenderer.PROGRESS_LABEL_HEIGHT);
        assertEquals(3, DinoDoctorOverlayRenderer.PROGRESS_BOTTOM_GAP);
        assertEquals(24, DinoDoctorOverlayRenderer.PROGRESS_LINE_HEIGHT);
        assertEquals(19, DinoDoctorOverlayRenderer.CAPTURE_TITLE_LINE_HEIGHT);
        assertEquals(12, DinoDoctorOverlayRenderer.CAPTURE_INFO_LINE_HEIGHT);
        assertEquals(6, DinoDoctorOverlayRenderer.CAPTURE_PANEL_HORIZONTAL_PADDING);
        assertEquals(4, DinoDoctorOverlayRenderer.CAPTURE_PANEL_TOP_PADDING);
        assertEquals(4, DinoDoctorOverlayRenderer.CAPTURE_PANEL_BOTTOM_PADDING);
        assertEquals(
                150,
                DinoDoctorOverlayRenderer.PROGRESS_OUTER_WIDTH * DinoDoctorOverlayRenderer.PROGRESS_OUTER_HEIGHT
                        - DinoDoctorOverlayRenderer.PROGRESS_BAR_WIDTH * DinoDoctorOverlayRenderer.PROGRESS_BAR_HEIGHT
        );
        assertEquals(0.98F / 0.85F, DinoDoctorOverlayRenderer.FONT_SCALE, 1.0E-6F);
        assertEquals(0.98F / 0.85F, DinoDoctorOverlayRenderer.LABEL_FONT_SCALE, 1.0E-6F);
        assertEquals(DinoDoctorOverlayRenderer.LABEL_FONT_SCALE,
                DinoDoctorOverlayRenderer.FONT_SCALE, 1.0E-6F);
        assertEquals(1.05F / 0.85F, DinoDoctorOverlayRenderer.TITLE_FONT_SCALE, 1.0E-6F);
        assertEquals(0.585F / 0.85F, DinoDoctorOverlayRenderer.GENE_LABEL_SCALE, 1.0E-6F);
        assertEquals(DinoDoctorOverlayRenderer.FONT_SCALE,
                DinoDoctorOverlayRenderer.CAPTURE_VALUE_FONT_SCALE, 1.0E-6F);
        assertEquals(DinoDoctorOverlayRenderer.LABEL_FONT_SCALE,
                DinoDoctorOverlayRenderer.CAPTURE_LABEL_FONT_SCALE, 1.0E-6F);
        assertEquals(DinoDoctorOverlayRenderer.TITLE_FONT_SCALE,
                DinoDoctorOverlayRenderer.CAPTURE_TITLE_FONT_SCALE, 1.0E-6F);
        assertEquals(15, DinoDoctorOverlayRenderer.CAPTURE_TITLE_ICON_SIZE);
        assertEquals(20, DinoDoctorOverlayRenderer.CONTENT_X_OFFSET);
        assertEquals(5, DinoDoctorOverlayRenderer.PANEL_RIGHT_EXTRA_PADDING);
        assertEquals(18, DinoDoctorOverlayRenderer.CAPTURE_CONTENT_X_OFFSET);
        assertEquals(137, DinoDoctorOverlayRenderer.observationPanelWidth(100));
        assertEquals(0, DinoDoctorOverlayRenderer.captureContentOffset(
                DinoDoctorOverlayRenderer.CapturePanelLineKind.TITLE
        ));
        assertEquals(18, DinoDoctorOverlayRenderer.captureContentOffset(
                DinoDoctorOverlayRenderer.CapturePanelLineKind.PROGRESS
        ));
        assertEquals(18, DinoDoctorOverlayRenderer.captureContentOffset(
                DinoDoctorOverlayRenderer.CapturePanelLineKind.INFO
        ));
        assertEquals(18, DinoDoctorOverlayRenderer.captureContentOffset(
                DinoDoctorOverlayRenderer.CapturePanelLineKind.WARNING
        ));
        assertEquals(2, DinoDoctorOverlayRenderer.centeredTextY(0, 19, 15));
        assertEquals(6, DinoDoctorOverlayRenderer.PANEL_TOP_PADDING - 2);
        assertEquals(6, DinoDoctorOverlayRenderer.CAPTURE_PANEL_TOP_PADDING
                + DinoDoctorOverlayRenderer.centeredTextY(0, 19, 15));
        assertEquals(4, DinoDoctorOverlayRenderer.centeredTextY(0, 19, 11));
        assertEquals(12, DinoDoctorOverlayRenderer.centeredTextY(12, 9, 9));
        assertEquals(0, DinoDoctorOverlayRenderer.centeredTextY(0, 12, 11));
        assertEquals(1, DinoDoctorOverlayRenderer.centeredTextY(0, 12, 10));
        assertEquals(2, DinoDoctorOverlayRenderer.centeredTextY(0, 12, 8));
        int sharedMainY = DinoDoctorOverlayRenderer.alignedMainTextY(
                0,
                12,
                9,
                DinoDoctorOverlayRenderer.LABEL_FONT_SCALE
        );
        assertEquals(0, sharedMainY);
        assertEquals(sharedMainY, DinoDoctorOverlayRenderer.captureMainTextY(
                0,
                DinoDoctorOverlayRenderer.PROGRESS_LABEL_HEIGHT,
                9
        ));
        assertEquals(sharedMainY, DinoDoctorOverlayRenderer.captureMainTextY(
                0,
                DinoDoctorOverlayRenderer.CAPTURE_INFO_LINE_HEIGHT,
                9
        ));
        assertEquals(
                sharedMainY,
                DinoDoctorOverlayRenderer.alignedMainTextY(
                        0,
                        12,
                        9,
                        DinoDoctorOverlayRenderer.FONT_SCALE
                ),
                "Labels and values must resolve to the same vertical anchor"
        );
        assertEquals(0xFF2D5FA8, DinoDoctorOverlayRenderer.WATER_PROGRESS_DARK_COLOR);
        assertEquals(0xFF4C87D9, DinoDoctorOverlayRenderer.WATER_PROGRESS_LIGHT_COLOR);
    }

    @Test
    void capturePanelCompleteValidHeightIsOneHundredFortySevenPixels() {
        List<DinoDoctorOverlayRenderer.CapturePanelLine> lines = DinoDoctorOverlayRenderer.createCaptureBoxLines(
                CaptureBoxHudViewModel.occupied(40, 20, 20, 20, false, 72_000L, 20)
        );

        int contentHeight = lines.stream().mapToInt(DinoDoctorOverlayRenderer.CapturePanelLine::height).sum();

        assertEquals(147, contentHeight
                + DinoDoctorOverlayRenderer.CAPTURE_PANEL_TOP_PADDING
                + DinoDoctorOverlayRenderer.CAPTURE_PANEL_BOTTOM_PADDING);
    }

    @Test
    void cageAndAnimalObservationsBothOmitMissingOrNonPositiveAnestheticRows() {
        DinosaurObservationSnapshot observation = snapshot(
                OptionalDouble.empty(),
                OptionalDouble.empty(),
                OptionalDouble.empty(),
                OptionalLong.of(0L),
                OptionalLong.of(-1L),
                OptionalLong.of(0L)
        );
        CaptureCageObservationSnapshot cage = CaptureCageObservationSnapshot.from(
                ResourceLocation.fromNamespaceAndPath("jurassicsaga", "test_dino"),
                observation,
                20L,
                20
        );

        List<DinoDoctorOverlayRenderer.OverlayLine> animalLines =
                DinoDoctorOverlayRenderer.createObservationLines(observation);
        List<DinoDoctorOverlayRenderer.OverlayLine> cageLines =
                DinoDoctorOverlayRenderer.createCaptureCageObservationLines(cage);

        assertEquals(7, animalLines.size());
        assertEquals(animalLines, cageLines);
    }

    @Test
    void anestheticRowsUseLocalizedSecondsMinutesAndHoursIncludingQueuedAddition() {
        long[] ticks = {1_199L, 1_200L, 71_999L, 72_000L, 20L * 60L * 60L * 25L};
        String[] keys = {
                "format.jsrevise.duration.seconds",
                "format.jsrevise.duration.minutes",
                "format.jsrevise.duration.minutes",
                "format.jsrevise.duration.hours",
                "format.jsrevise.duration.hours"
        };
        String[] values = {"60.0", "1.0", "60.0", "1.0", "25.0"};
        for (int index = 0; index < ticks.length; index++) {
            List<DinoDoctorOverlayRenderer.OverlayLine> lines = DinoDoctorOverlayRenderer.createObservationLines(
                    snapshot(
                            OptionalDouble.empty(),
                            OptionalDouble.empty(),
                            OptionalDouble.empty(),
                            OptionalLong.empty(),
                            OptionalLong.of(ticks[index]),
                            OptionalLong.empty()
                    )
            );
            assertLabelValueLine(lines.get(7), "overlay.jsrevise.anesthetic.remaining");
            assertDuration(lines.get(7).mainSegments().get(1).text(), keys[index], values[index]);
            assertFalse(lines.get(7).hasSuffix());
            assertEquals(DinoDoctorOverlayRenderer.SuffixPlacement.INLINE, lines.get(7).suffixPlacement());
            assertEquals(DinoDoctorOverlayRenderer.LINE_HEIGHT, lines.get(7).height());
        }

        List<DinoDoctorOverlayRenderer.OverlayLine> queuedLines = DinoDoctorOverlayRenderer.createObservationLines(
                snapshot(
                        OptionalDouble.empty(),
                        OptionalDouble.empty(),
                        OptionalDouble.empty(),
                        OptionalLong.empty(),
                        OptionalLong.of(72_000L),
                        OptionalLong.of(20L * 60L * 60L * 25L)
                )
        );
        DinoDoctorOverlayRenderer.OverlayLine remaining = queuedLines.get(7);
        assertDuration(remaining.mainSegments().get(1).text(), "format.jsrevise.duration.hours", "1.0");
        assertEquals(DinoDoctorOverlayRenderer.SuffixPlacement.BELOW_VALUE, remaining.suffixPlacement());
        assertEquals(24, remaining.height());
        TranslatableContents addition = assertInstanceOf(TranslatableContents.class, remaining.suffixText().getContents());
        Component queuedValue = assertInstanceOf(Component.class, addition.getArgs()[0]);
        assertDuration(queuedValue, "format.jsrevise.duration.hours", "25.0");
    }

    @Test
    void anestheticRowsKeepRemainingAbovePendingAndQueuedOnlyHasNoZeroBaseValue() {
        List<DinoDoctorOverlayRenderer.OverlayLine> queuedOnly = DinoDoctorOverlayRenderer.createObservationLines(
                snapshot(
                        OptionalDouble.empty(),
                        OptionalDouble.empty(),
                        OptionalDouble.empty(),
                        OptionalLong.empty(),
                        OptionalLong.empty(),
                        OptionalLong.of(200L)
                )
        );
        assertEquals(8, queuedOnly.size());
        assertSingleRegularLabel(queuedOnly.get(7), "overlay.jsrevise.anesthetic.remaining");
        assertTrue(queuedOnly.get(7).hasSuffix());
        assertEquals(DinoDoctorOverlayRenderer.SuffixPlacement.INLINE, queuedOnly.get(7).suffixPlacement());
        assertEquals(12, queuedOnly.get(7).height());

        List<DinoDoctorOverlayRenderer.OverlayLine> pendingOnly = DinoDoctorOverlayRenderer.createObservationLines(
                snapshot(
                        OptionalDouble.empty(),
                        OptionalDouble.empty(),
                        OptionalDouble.empty(),
                        OptionalLong.of(40L),
                        OptionalLong.empty(),
                        OptionalLong.empty()
                )
        );
        assertEquals(8, pendingOnly.size());
        assertLabelValueLine(pendingOnly.get(7), "overlay.jsrevise.anesthetic.pending");

        List<DinoDoctorOverlayRenderer.OverlayLine> allStates = DinoDoctorOverlayRenderer.createObservationLines(
                snapshot(
                        OptionalDouble.empty(),
                        OptionalDouble.empty(),
                        OptionalDouble.empty(),
                        OptionalLong.of(40L),
                        OptionalLong.of(200L),
                        OptionalLong.of(60L)
                )
        );
        assertEquals(9, allStates.size());
        assertLabelValueLine(allStates.get(7), "overlay.jsrevise.anesthetic.remaining");
        assertTrue(allStates.get(7).hasSuffix());
        assertEquals(DinoDoctorOverlayRenderer.SuffixPlacement.BELOW_VALUE,
                allStates.get(7).suffixPlacement());
        assertEquals(24, allStates.get(7).height());
        assertLabelValueLine(allStates.get(8), "overlay.jsrevise.anesthetic.pending");
    }

    @Test
    void suffixLayoutAlignsQueuedAdditionToTheValueStartWithoutInflatingTheMainRow() {
        int mainWidth = 96;
        int labelWidth = 54;
        int chineseMinuteSuffixWidth = 46;
        int englishHourSuffixWidth = 74;

        assertEquals(labelWidth, DinoDoctorOverlayRenderer.suffixXOffset(
                mainWidth,
                labelWidth,
                DinoDoctorOverlayRenderer.SuffixPlacement.BELOW_VALUE
        ));
        assertEquals(mainWidth + 4, DinoDoctorOverlayRenderer.suffixXOffset(
                mainWidth,
                labelWidth,
                DinoDoctorOverlayRenderer.SuffixPlacement.INLINE
        ));
        assertEquals(100, DinoDoctorOverlayRenderer.lineWidthWithSuffix(
                mainWidth,
                labelWidth,
                chineseMinuteSuffixWidth,
                DinoDoctorOverlayRenderer.SuffixPlacement.BELOW_VALUE
        ));
        assertEquals(128, DinoDoctorOverlayRenderer.lineWidthWithSuffix(
                mainWidth,
                labelWidth,
                englishHourSuffixWidth,
                DinoDoctorOverlayRenderer.SuffixPlacement.BELOW_VALUE
        ));
        assertEquals(174, DinoDoctorOverlayRenderer.lineWidthWithSuffix(
                mainWidth,
                labelWidth,
                englishHourSuffixWidth,
                DinoDoctorOverlayRenderer.SuffixPlacement.INLINE
        ));
    }

    @Test
    void vitalProgressClampsFiniteValuesAndLeavesInvalidValuesUnknown() {
        List<DinoDoctorOverlayRenderer.OverlayLine> finite = DinoDoctorOverlayRenderer.createObservationLines(
                snapshot(
                        OptionalDouble.of(120.0D),
                        OptionalDouble.of(-5.0D),
                        OptionalDouble.of(50.25D),
                        OptionalLong.empty(),
                        OptionalLong.empty(),
                        OptionalLong.empty()
                )
        );
        assertInlineProgressLine(
                finite.get(4), "overlay.jsrevise.hunger", "100.0%", 1.0D,
                DinoDoctorOverlayRenderer.HUNGER_PROGRESS_DARK_COLOR,
                DinoDoctorOverlayRenderer.HUNGER_PROGRESS_LIGHT_COLOR
        );
        assertInlineProgressLine(
                finite.get(5), "overlay.jsrevise.thirst", "0.0%", 0.0D,
                DinoDoctorOverlayRenderer.THIRST_PROGRESS_DARK_COLOR,
                DinoDoctorOverlayRenderer.THIRST_PROGRESS_LIGHT_COLOR
        );
        assertInlineProgressLine(
                finite.get(6), "overlay.jsrevise.mood", "50.3%", 0.5025D,
                DinoDoctorOverlayRenderer.MOOD_PROGRESS_DARK_COLOR,
                DinoDoctorOverlayRenderer.MOOD_PROGRESS_LIGHT_COLOR
        );

        List<DinoDoctorOverlayRenderer.OverlayLine> invalid = DinoDoctorOverlayRenderer.createObservationLines(
                snapshot(
                        OptionalDouble.empty(),
                        OptionalDouble.of(Double.NaN),
                        OptionalDouble.of(Double.POSITIVE_INFINITY),
                        OptionalLong.empty(),
                        OptionalLong.empty(),
                        OptionalLong.empty()
                )
        );
        for (int index = 4; index <= 6; index++) {
            DinoDoctorOverlayRenderer.OverlayLine line = invalid.get(index);
            assertTrue(line.hasProgress());
            assertFalse(line.progressKnown());
            assertEquals(0.0D, line.progress(), 1.0E-9D);
            assertEquals(2, line.mainSegments().size());
            TranslatableContents unknown = assertInstanceOf(
                    TranslatableContents.class,
                    line.mainSegments().get(1).text().getContents()
            );
            assertEquals("overlay.jsrevise.unknown", unknown.getKey());
        }
    }

    @Test
    void targetScanAndCaptureMissGraceUseMonotonicElapsedTicks() {
        assertTrue(DinoDoctorOverlayRenderer.isTargetScanDue(0L, -1L));
        assertFalse(DinoDoctorOverlayRenderer.isTargetScanDue(2L, 0L));
        assertTrue(DinoDoctorOverlayRenderer.isTargetScanDue(3L, 0L));
        assertFalse(DinoDoctorOverlayRenderer.isTargetScanDue(Long.MAX_VALUE, Long.MAX_VALUE));
        assertTrue(DinoDoctorOverlayRenderer.isTargetScanDue(Long.MAX_VALUE, Long.MAX_VALUE - 3L));

        assertTrue(DinoDoctorOverlayRenderer.shouldRetainCageMiss(3L, 0L));
        assertFalse(DinoDoctorOverlayRenderer.shouldRetainCageMiss(6L, 0L));
        assertTrue(DinoDoctorOverlayRenderer.shouldRetainCageMiss(15L, 10L));
        assertFalse(DinoDoctorOverlayRenderer.shouldRetainCageMiss(16L, 10L));
        assertFalse(DinoDoctorOverlayRenderer.shouldRetainCageMiss(9L, 10L));
        assertFalse(DinoDoctorOverlayRenderer.shouldRetainCageMiss(10L, -1L));
    }

    @Test
    void locallyInvalidCaptureTargetDropsLastKnownGoodAndRejectsLateReply() {
        ResourceLocation dimensionId = ResourceLocation.fromNamespaceAndPath("minecraft", "overworld");
        BlockPos cagePos = new BlockPos(4, 5, 6);
        ClientOverlaySessionClock.Stamp stamp = new ClientOverlaySessionClock.Stamp(1L, dimensionId, 10L);
        CaptureCageObservationSnapshot cageSnapshot = CaptureCageObservationSnapshot.from(
                ResourceLocation.fromNamespaceAndPath("jurassicsaga", "test_dino"),
                snapshot(
                        OptionalDouble.empty(),
                        OptionalDouble.empty(),
                        OptionalDouble.empty(),
                        OptionalLong.empty(),
                        OptionalLong.empty(),
                        OptionalLong.empty()
                ),
                20L,
                20
        );

        ClientCaptureCageObservationCache.clearCache();
        try {
            ClientCaptureCageObservationCache.getOrRequest(stamp, cagePos, ignored -> { });
            ClientCaptureCageObservationCache.remember(stamp, cagePos, Optional.of(cageSnapshot), true);
            assertTrue(ClientCaptureCageObservationCache.isCached(dimensionId, cagePos));

            DinoDoctorOverlayRenderer.invalidateLocallyInvalidCaptureTarget(cagePos);
            assertFalse(ClientCaptureCageObservationCache.isCached(dimensionId, cagePos));

            ClientCaptureCageObservationCache.remember(
                    new ClientOverlaySessionClock.Stamp(1L, dimensionId, 11L),
                    cagePos,
                    Optional.of(cageSnapshot),
                    true
            );
            assertFalse(ClientCaptureCageObservationCache.isCached(dimensionId, cagePos));
        } finally {
            ClientCaptureCageObservationCache.clearCache();
        }
    }

    private static void assertCaptureProgressLine(
            DinoDoctorOverlayRenderer.CapturePanelLine line,
            String labelKey,
            double progress,
            String count,
            int darkColor,
            int lightColor
    ) {
        assertTrue(line.hasProgress());
        assertCaptureTranslation(line, labelKey);
        assertEquals(progress, line.progress(), 1.0E-9D);
        assertEquals(count, line.value().getString());
        assertEquals(darkColor, line.progressDarkColor());
        assertEquals(lightColor, line.progressLightColor());
        assertEquals(DinoDoctorOverlayRenderer.PROGRESS_LINE_HEIGHT, line.height());
    }

    private static void assertCaptureTranslation(
            DinoDoctorOverlayRenderer.CapturePanelLine line,
            String key
    ) {
        TranslatableContents contents = assertInstanceOf(TranslatableContents.class, line.label().getContents());
        assertEquals(key, contents.getKey());
    }

    private static void assertLabelValueLine(DinoDoctorOverlayRenderer.OverlayLine line, String labelKey) {
        List<DinoDoctorOverlayRenderer.OverlayTextSegment> segments = line.mainSegments();
        assertEquals(2, segments.size());

        TranslatableContents label = assertInstanceOf(TranslatableContents.class, segments.get(0).text().getContents());
        assertEquals(labelKey, label.getKey());
        assertFalse(segments.get(0).text().getStyle().isBold());
        assertFalse(segments.get(1).text().getStyle().isBold());
        assertEquals(DinoDoctorOverlayRenderer.LABEL_FONT_SCALE, segments.get(0).scale(), 1.0E-6F);
        assertEquals(DinoDoctorOverlayRenderer.FONT_SCALE, segments.get(1).scale(), 1.0E-6F);
        assertEquals(segments.get(0).scale(), segments.get(1).scale(), 1.0E-6F);
        assertTrue(segments.get(0).scale() < DinoDoctorOverlayRenderer.TITLE_FONT_SCALE);
    }

    private static void assertInlineProgressLine(
            DinoDoctorOverlayRenderer.OverlayLine line,
            String labelKey,
            String value,
            double progress,
            int darkColor,
            int lightColor
    ) {
        assertTrue(line.hasProgress());
        assertTrue(line.progressKnown());
        assertEquals(progress, line.progress(), 1.0E-9D);
        assertEquals(darkColor, line.progressDarkColor());
        assertEquals(lightColor, line.progressLightColor());
        assertEquals(DinoDoctorOverlayRenderer.PROGRESS_LINE_HEIGHT, line.height());
        List<DinoDoctorOverlayRenderer.OverlayTextSegment> segments = line.mainSegments();
        assertEquals(2, segments.size());
        TranslatableContents label = assertInstanceOf(TranslatableContents.class, segments.getFirst().text().getContents());
        assertEquals(labelKey, label.getKey());
        assertEquals(value, segments.get(1).text().getString());
        assertEquals(DinoDoctorOverlayRenderer.LABEL_FONT_SCALE, segments.get(0).scale(), 1.0E-6F);
        assertEquals(DinoDoctorOverlayRenderer.FONT_SCALE, segments.get(1).scale(), 1.0E-6F);
        assertEquals(segments.get(0).scale(), segments.get(1).scale(), 1.0E-6F);
    }

    private static void assertDuration(Component value, String key, String argument) {
        TranslatableContents contents = assertInstanceOf(TranslatableContents.class, value.getContents());
        assertEquals(key, contents.getKey());
        assertEquals(List.of(argument), List.of(contents.getArgs()));
    }

    private static void assertSingleRegularLabel(DinoDoctorOverlayRenderer.OverlayLine line, String labelKey) {
        List<DinoDoctorOverlayRenderer.OverlayTextSegment> segments = line.mainSegments();
        assertEquals(1, segments.size());
        TranslatableContents label = assertInstanceOf(TranslatableContents.class, segments.get(0).text().getContents());
        assertEquals(labelKey, label.getKey());
        assertFalse(segments.get(0).text().getStyle().isBold());
        assertEquals(DinoDoctorOverlayRenderer.LABEL_FONT_SCALE, segments.get(0).scale(), 1.0E-6F);
    }

    private static void assertRegularTranslatableArguments(Component text, int expectedArguments) {
        TranslatableContents contents = assertInstanceOf(TranslatableContents.class, text.getContents());
        int componentArguments = 0;
        for (Object argument : contents.getArgs()) {
            Component component = assertInstanceOf(Component.class, argument);
            assertFalse(component.getStyle().isBold());
            componentArguments++;
        }
        assertEquals(expectedArguments, componentArguments);
    }

    private static DinosaurObservationSnapshot snapshotWithEggProgressAndGenes() {
        ResourceLocation speciesId = ResourceLocation.fromNamespaceAndPath("jsrevise", "test_species");
        DinosaurAgeEstimate ageEstimate = new DinosaurAgeEstimate(
                speciesId,
                DinosaurLifecycleStage.ADULT,
                1.0D,
                OptionalLong.empty(),
                OptionalLong.empty(),
                OptionalDouble.of(2.25D),
                OptionalDouble.empty()
        );
        ObservedGene gene = new ObservedGene(
                "test_gene",
                ResourceLocation.fromNamespaceAndPath("minecraft", "stone"),
                Component.literal("Test Gene")
        );
        return new DinosaurObservationSnapshot(
                Component.literal("Test Dinosaur"),
                ageEstimate,
                OptionalDouble.of(80.5D),
                OptionalDouble.of(120.0D),
                Optional.of(true),
                OptionalDouble.of(52.0D),
                OptionalDouble.of(63.0D),
                OptionalDouble.of(74.0D),
                OptionalLong.of(40L),
                OptionalLong.of(200L),
                OptionalLong.of(60L),
                EggLayingProgress.create(40, 100),
                List.of(gene)
        );
    }

    private static DinosaurObservationSnapshot snapshot(
            OptionalDouble hunger,
            OptionalDouble thirst,
            OptionalDouble mood,
            OptionalLong pending,
            OptionalLong remaining,
            OptionalLong queued
    ) {
        ResourceLocation speciesId = ResourceLocation.fromNamespaceAndPath("jsrevise", "test_species");
        DinosaurAgeEstimate ageEstimate = new DinosaurAgeEstimate(
                speciesId,
                DinosaurLifecycleStage.ADULT,
                1.0D,
                OptionalLong.empty(),
                OptionalLong.empty(),
                OptionalDouble.empty(),
                OptionalDouble.empty()
        );
        return new DinosaurObservationSnapshot(
                Component.literal("Test Dinosaur"),
                ageEstimate,
                OptionalDouble.empty(),
                OptionalDouble.empty(),
                Optional.empty(),
                hunger,
                thirst,
                mood,
                pending,
                remaining,
                queued,
                Optional.empty(),
                List.of()
        );
    }
}
