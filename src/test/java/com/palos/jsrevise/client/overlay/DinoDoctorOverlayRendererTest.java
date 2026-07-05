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
            assertTrue(labelSegment.scale() > DinoDoctorOverlayRenderer.FONT_SCALE);
            assertTrue(labelSegment.scale() < DinoDoctorOverlayRenderer.TITLE_FONT_SCALE);
        }
    }

    @Test
    void keepsLabelValuesAndAnestheticSuffixRegularWeightWithSeparateScales() {
        List<DinoDoctorOverlayRenderer.OverlayLine> lines = DinoDoctorOverlayRenderer.createObservationLines(
                snapshotWithEggProgressAndGenes()
        );

        assertLabelValueLine(lines.get(1), "overlay.jsrevise.age.label");
        assertLabelValueLine(lines.get(2), "overlay.jsrevise.health");
        assertLabelValueLine(lines.get(3), "overlay.jsrevise.gender");
        assertLabelValueLine(lines.get(4), "overlay.jsrevise.hunger");
        assertLabelValueLine(lines.get(5), "overlay.jsrevise.thirst");
        assertLabelValueLine(lines.get(6), "overlay.jsrevise.mood");
        assertLabelValueLine(lines.get(7), "overlay.jsrevise.anesthetic.pending");
        assertLabelValueLine(lines.get(8), "overlay.jsrevise.anesthetic.remaining");
        assertSingleRegularLabel(lines.get(9), "overlay.jsrevise.genes");

        DinoDoctorOverlayRenderer.OverlayLine anestheticRemaining = lines.get(8);
        assertTrue(anestheticRemaining.hasSuffix());
        assertFalse(anestheticRemaining.suffixText().getStyle().isBold());
        assertRegularTranslatableArguments(anestheticRemaining.suffixText(), 1);
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
        assertEquals(DinoDoctorOverlayRenderer.LINE_HEIGHT, DinoDoctorOverlayRenderer.PROGRESS_BAR_Y_OFFSET);
        assertEquals(
                DinoDoctorOverlayRenderer.PROGRESS_BAR_Y_OFFSET + DinoDoctorOverlayRenderer.PROGRESS_BAR_HEIGHT,
                DinoDoctorOverlayRenderer.PROGRESS_LINE_HEIGHT
        );
        assertEquals(65, DinoDoctorOverlayRenderer.PROGRESS_BAR_WIDTH);
        assertEquals(13, DinoDoctorOverlayRenderer.PROGRESS_BAR_HEIGHT);

        TranslatableContents eggLabel = assertInstanceOf(TranslatableContents.class, progress.mainText().getContents());
        assertEquals("overlay.jsrevise.egg_laying", eggLabel.getKey());
        assertEquals(0, eggLabel.getArgs().length);
    }

    @Test
    void captureCageLinesShowDurabilityAndDurationBeforeGenes() {
        int durability = CapturedDinosaurData.MAX_DURABILITY * 3 / 4;
        CaptureCageObservationSnapshot snapshot = CaptureCageObservationSnapshot.from(
                ResourceLocation.fromNamespaceAndPath("jurassicsaga", "test_dino"),
                snapshotWithEggProgressAndGenes(),
                400L,
                durability
        );

        List<DinoDoctorOverlayRenderer.OverlayLine> lines =
                DinoDoctorOverlayRenderer.createCaptureCageObservationLines(snapshot);

        assertLabelValueLine(lines.get(9), "overlay.jsrevise.capture_box.durability");
        assertEquals(
                durability + "/" + CapturedDinosaurData.MAX_DURABILITY,
                lines.get(9).mainSegments().get(1).text().getString()
        );
        assertLabelValueLine(lines.get(10), "overlay.jsrevise.capture_box.duration");
        assertSingleRegularLabel(lines.get(11), "overlay.jsrevise.genes");
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
    void exposesMatchingPanelPaddingAndWhiteBorderColorsForLayoutVerification() {
        assertEquals(DinoDoctorOverlayRenderer.PANEL_TOP_PADDING, DinoDoctorOverlayRenderer.PANEL_BOTTOM_PADDING);
        assertEquals(0xFFFFFF, DinoDoctorOverlayRenderer.PANEL_BORDER_TOP_COLOR & 0xFFFFFF);
        assertEquals(0xFFFFFF, DinoDoctorOverlayRenderer.PANEL_BORDER_BOTTOM_COLOR & 0xFFFFFF);
        assertEquals(0x680C1824, DinoDoctorOverlayRenderer.panelBackgroundTopColor(0x336699));
        assertEquals(0x7208111A, DinoDoctorOverlayRenderer.panelBackgroundBottomColor(0x336699));
        assertNotEquals(0xFFFFFF, DinoDoctorOverlayRenderer.panelBackgroundTopColor(0x336699) & 0xFFFFFF);
        assertNotEquals(0xFFFFFF, DinoDoctorOverlayRenderer.panelBackgroundBottomColor(0x336699) & 0xFFFFFF);
    }

    @Test
    void exposesJadeNestedBoxAndSimpleProgressStyleConstants() {
        assertEquals(1, DinoDoctorOverlayRenderer.JADE_NESTED_BOX_BORDER_WIDTH);
        assertEquals(0xFF808080, DinoDoctorOverlayRenderer.JADE_NESTED_BOX_BORDER_COLOR);
        assertEquals(0xFFB2B2B2, DinoDoctorOverlayRenderer.JADE_PROGRESS_DARK_COLOR);
        assertEquals(0xFFFFFFFF, DinoDoctorOverlayRenderer.JADE_PROGRESS_LIGHT_COLOR);
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
        assertTrue(segments.get(0).scale() > segments.get(1).scale());
        assertTrue(segments.get(0).scale() < DinoDoctorOverlayRenderer.TITLE_FONT_SCALE);
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
}
