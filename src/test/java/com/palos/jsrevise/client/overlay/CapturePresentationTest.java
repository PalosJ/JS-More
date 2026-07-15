package com.palos.jsrevise.client.overlay;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.palos.jsrevise.compat.jade.JadeAnimalTooltipPolicy;
import com.palos.jsrevise.server.system.capture.CapturedDinosaurData;
import com.palos.jsrevise.server.system.capture.DinosaurCaptureSupplies;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import org.junit.jupiter.api.Test;

class CapturePresentationTest {
    @Test
    void formatsCapturedDurationUsingSecondsMinutesAndHours() {
        assertTranslation(CaptureDurationFormatter.format(0L), "format.jsrevise.duration.seconds", "0.0");
        assertTranslation(CaptureDurationFormatter.format(1_199L), "format.jsrevise.duration.seconds", "60.0");
        assertTranslation(CaptureDurationFormatter.format(1_200L), "format.jsrevise.duration.minutes", "1.0");
        assertTranslation(CaptureDurationFormatter.format(71_999L), "format.jsrevise.duration.minutes", "60.0");
        assertTranslation(CaptureDurationFormatter.format(72_000L), "format.jsrevise.duration.hours", "1.0");
        assertTranslation(CaptureDurationFormatter.format(20L * 60L * 60L * 49L), "format.jsrevise.duration.hours", "49.0");
        assertTranslation(CaptureDurationFormatter.format(-20L), "format.jsrevise.duration.seconds", "0.0");
    }

    @Test
    void createsOrderedBoundedSupplyPercentagesOrOneRecoveryWarning() {
        List<Component> lines = CaptureSupplyTooltipLines.create(40, 10, 10, -3, false);

        assertTranslation(lines.get(0), "tooltip.jsrevise.dinosaur_capture_box.anesthetic_reserve", 100);
        assertTranslation(lines.get(1), "tooltip.jsrevise.dinosaur_capture_box.water_reserve", 50);
        assertTranslation(lines.get(2), "tooltip.jsrevise.dinosaur_capture_box.carnivore_reserve", 50);
        assertTranslation(lines.get(3), "tooltip.jsrevise.dinosaur_capture_box.herbivore_reserve", 0);
        assertEquals(100, CaptureSupplyTooltipLines.percent(DinosaurCaptureSupplies.Type.ANESTHETIC, 99));
        assertEquals(50, CaptureSupplyTooltipLines.percent(DinosaurCaptureSupplies.Type.WATER, 10));

        List<Component> unreadable = CaptureSupplyTooltipLines.create(40, 20, 20, 20, true);
        assertEquals(1, unreadable.size());
        assertTranslation(unreadable.getFirst(), "tooltip.jsrevise.dinosaur_capture_box.supplies_unreadable");
    }

    @Test
    void onlyFiltersJadeAnimalDetailsWhileGogglesAreWorn() {
        assertFalse(JadeAnimalTooltipPolicy.shouldFilter(true, false));
        assertFalse(JadeAnimalTooltipPolicy.shouldFilter(false, true));
        assertTrue(JadeAnimalTooltipPolicy.shouldFilter(true, true));
    }

    @Test
    void captureBoxViewModelDoesNotInventRecoveryData() {
        CaptureBoxHudViewModel empty = CaptureBoxHudViewModel.empty(50, 25, 10, -1, false);
        assertEquals(40, empty.anestheticReserve());
        assertEquals(20, empty.waterReserve());
        assertEquals(1.0D, empty.anestheticProgress(), 1.0E-9D);
        assertEquals(1.0D, empty.waterProgress(), 1.0E-9D);
        assertTrue(empty.capturedDurationTicks().isEmpty());
        assertEquals(CapturedDinosaurData.MAX_DURABILITY, empty.durability().orElseThrow());

        CaptureBoxHudViewModel recovery = CaptureBoxHudViewModel.unavailable(1, 2, 3, 4, false, true);
        assertTrue(recovery.capturedDinosaurUnreadable());
        assertTrue(recovery.capturedDurationTicks().isEmpty());
        assertTrue(recovery.durability().isEmpty());

        CaptureBoxHudViewModel validCaptureRawSupplies = CaptureBoxHudViewModel.occupied(
                0, 0, 0, 0, true, 200L, 450
        );
        assertTrue(validCaptureRawSupplies.capturedDurationTicks().isEmpty());
        assertTrue(validCaptureRawSupplies.durability().isEmpty());

        CaptureBoxHudViewModel emptyCaptureRawSupplies = CaptureBoxHudViewModel.empty(0, 0, 0, 0, true);
        assertTrue(emptyCaptureRawSupplies.capturedDurationTicks().isEmpty());
        assertTrue(emptyCaptureRawSupplies.durability().isEmpty());

        CaptureBoxHudViewModel rawBoth = CaptureBoxHudViewModel.unavailable(0, 0, 0, 0, true, true);
        assertTrue(rawBoth.capturedDurationTicks().isEmpty());
        assertTrue(rawBoth.durability().isEmpty());
    }

    @Test
    void laysOutRepresentativeViewportsWithoutOverlapOrClipping() {
        int capturePanelWidthWithTitleAlignedContent = 168;
        for (int[] viewport : new int[][]{{320, 180}, {426, 240}, {854, 480}}) {
            DualPanelLayout layout = DualPanelLayout.arrange(
                    viewport[0], viewport[1], 160, 220, capturePanelWidthWithTitleAlignedContent, 147
            );
            assertLayoutInside(layout, viewport[0], viewport[1]);
            assertTrue(layout.scale() >= 0.75F);
            PanelRect left = layout.left().orElseThrow();
            if (layout.right().top() < left.bottom()) {
                assertTrue(viewport[0] / 2 - left.right() >= DualPanelLayout.MIN_CENTER_CLEARANCE);
                assertTrue(layout.right().left() - viewport[0] / 2 >= DualPanelLayout.MIN_CENTER_CLEARANCE);
            }
        }

        assertEquals(0.75F, DualPanelLayout.arrange(
                320, 180, 160, 220, capturePanelWidthWithTitleAlignedContent, 147
        ).scale());
        assertEquals(0.75F, DualPanelLayout.arrange(
                426, 240, 160, 220, capturePanelWidthWithTitleAlignedContent, 147
        ).scale());
        assertEquals(1.0F, DualPanelLayout.arrange(
                854, 480, 160, 220, capturePanelWidthWithTitleAlignedContent, 147
        ).scale());

        for (int[] viewport : new int[][]{{320, 180}, {426, 240}, {854, 480}}) {
            DualPanelLayout emptyBox = DualPanelLayout.arrange(
                    viewport[0], viewport[1], 0, 0, capturePanelWidthWithTitleAlignedContent, 147
            );
            assertTrue(emptyBox.left().isEmpty());
            assertTrue(emptyBox.right().isInside(viewport[0], viewport[1], 4));
            assertTrue(emptyBox.right().left() - viewport[0] / 2 >= DualPanelLayout.MIN_CENTER_CLEARANCE);

            DualPanelLayout.SinglePanelLayout animal = DualPanelLayout.arrangeSingleLeft(
                    viewport[0], viewport[1], 160, 220
            );
            assertTrue(animal.panel().isInside(viewport[0], viewport[1], 4));
            assertTrue(viewport[0] / 2 - animal.panel().right() >= DualPanelLayout.MIN_CENTER_CLEARANCE);
            assertTrue(animal.scale() >= 0.75F);
        }

        DualPanelLayout.SinglePanelLayout wideAnimal = DualPanelLayout.arrangeSingleLeft(854, 480, 160, 220);
        assertEquals(DualPanelLayout.PREFERRED_LEFT_CENTER_CLEARANCE, 427 - wideAnimal.panel().right());

        DualPanelLayout wideEmptyBox = DualPanelLayout.arrange(
                854, 480, 0, 0, capturePanelWidthWithTitleAlignedContent, 147
        );
        assertEquals(DualPanelLayout.PREFERRED_RIGHT_CENTER_CLEARANCE, wideEmptyBox.right().left() - 427);

        DualPanelLayout wideDual = DualPanelLayout.arrange(
                854, 480, 160, 220, capturePanelWidthWithTitleAlignedContent, 147
        );
        assertEquals(DualPanelLayout.PREFERRED_LEFT_CENTER_CLEARANCE,
                427 - wideDual.left().orElseThrow().right());
        assertEquals(DualPanelLayout.PREFERRED_RIGHT_CENTER_CLEARANCE, wideDual.right().left() - 427);

        DualPanelLayout stacked = DualPanelLayout.arrange(320, 180, 250, 40, 250, 40);
        PanelRect stackedLeft = stacked.left().orElseThrow();
        assertTrue(stacked.right().top() >= stackedLeft.bottom() + 4);
        assertEquals(316, stacked.right().right());
        assertLayoutInside(stacked, 320, 180);
    }

    private static void assertLayoutInside(DualPanelLayout layout, int width, int height) {
        assertTrue(layout.left().isPresent());
        PanelRect left = layout.left().orElseThrow();
        assertTrue(left.isInside(width, height, 4));
        assertTrue(layout.right().isInside(width, height, 4));
        assertFalse(left.overlaps(layout.right()));
    }

    private static void assertTranslation(Component component, String key, Object... expectedArguments) {
        TranslatableContents contents = (TranslatableContents) component.getContents();
        assertEquals(key, contents.getKey());
        assertEquals(List.of(expectedArguments), List.of(contents.getArgs()));
    }
}
