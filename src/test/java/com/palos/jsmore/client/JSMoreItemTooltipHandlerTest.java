package com.palos.jsmore.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.palos.jsmore.client.overlay.CaptureBoxDurabilityFormatter;
import com.palos.jsmore.server.system.capture.CapturedDinosaurData;
import com.palos.jsmore.server.system.capture.CapturedDinosaurVitals;
import com.palos.jsmore.server.system.capture.DinosaurCaptureItemData;
import com.palos.jsmore.server.system.capture.DinosaurCaptureSupplies;
import java.util.List;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.Test;

class JSMoreItemTooltipHandlerTest {
    @Test
    void formatsCaptureBoxDurabilityAsBoundedWholePercentages() {
        assertEquals("0%", CaptureBoxDurabilityFormatter.format(Long.MIN_VALUE));
        assertEquals("0%", CaptureBoxDurabilityFormatter.format(0L));
        assertEquals("0%", CaptureBoxDurabilityFormatter.format(1L));
        assertEquals("75%", CaptureBoxDurabilityFormatter.format(375L));
        assertEquals("99%", CaptureBoxDurabilityFormatter.format(499L));
        assertEquals("100%", CaptureBoxDurabilityFormatter.format(500L));
        assertEquals("100%", CaptureBoxDurabilityFormatter.format(Long.MAX_VALUE));
    }

    @Test
    void showsFourZeroPercentReservesForEmptyAndCapturedRecoveryBoxes() {
        ItemStack empty = new ItemStack(Items.STONE);
        assertReserveLines(JSMoreItemTooltipHandler.createCaptureSupplyTooltip(empty), 0, 0, 0, 0);

        ItemStack capturedRecovery = empty.copy();
        DinosaurCaptureItemData.setRawCaptureTag(capturedRecovery, StringTag.valueOf("raw-capture"));
        assertReserveLines(JSMoreItemTooltipHandler.createCaptureSupplyTooltip(capturedRecovery), 0, 0, 0, 0);
    }

    @Test
    void showsValidReservePercentagesAndUnreadableSupplyRecoveryWarning() {
        ItemStack valid = new ItemStack(Items.STONE);
        DinosaurCaptureItemData.setSupplies(valid, new DinosaurCaptureSupplies(40, 10, 10, 5));
        assertReserveLines(JSMoreItemTooltipHandler.createCaptureSupplyTooltip(valid), 100, 50, 50, 25);

        ItemStack unreadable = new ItemStack(Items.STONE);
        DinosaurCaptureItemData.setRawSuppliesTag(unreadable, StringTag.valueOf("raw-supplies"));
        List<Component> lines = JSMoreItemTooltipHandler.createCaptureSupplyTooltip(unreadable);
        assertEquals(1, lines.size());
        assertTranslation(lines.getFirst(), "tooltip.jsmore.dinosaur_capture_box.supplies_unreadable");
        assertEquals(ChatFormatting.RED.getColor(), lines.getFirst().getStyle().getColor().getValue());
    }

    @Test
    void mixedRecoveryStatesNeverExposeRuntimeCaptureValues() {
        CapturedDinosaurData captured = capturedData();

        ItemStack valid = new ItemStack(Items.STONE);
        DinosaurCaptureItemData.setContents(valid, captured, new DinosaurCaptureSupplies(1, 2, 3, 4));
        assertEquals(7, JSMoreItemTooltipHandler.createCaptureCageRuntimeTooltip(valid, 20L).size());

        ItemStack validCaptureRawSupplies = new ItemStack(Items.STONE);
        DinosaurCaptureItemData.set(validCaptureRawSupplies, captured);
        DinosaurCaptureItemData.setRawSuppliesTag(validCaptureRawSupplies, StringTag.valueOf("raw-supplies"));
        assertNoRuntimeValues(JSMoreItemTooltipHandler.createCaptureCageRuntimeTooltip(
                validCaptureRawSupplies, 20L
        ));

        ItemStack rawCaptureValidSupplies = new ItemStack(Items.STONE);
        DinosaurCaptureItemData.setSupplies(rawCaptureValidSupplies, new DinosaurCaptureSupplies(1, 2, 3, 4));
        DinosaurCaptureItemData.setRawCaptureTag(rawCaptureValidSupplies, StringTag.valueOf("raw-capture"));
        assertNoRuntimeValues(JSMoreItemTooltipHandler.createCaptureCageRuntimeTooltip(
                rawCaptureValidSupplies, 20L
        ));

        ItemStack rawBoth = new ItemStack(Items.STONE);
        DinosaurCaptureItemData.setRawCaptureTag(rawBoth, StringTag.valueOf("raw-capture"));
        DinosaurCaptureItemData.setRawSuppliesTag(rawBoth, StringTag.valueOf("raw-supplies"));
        assertNoRuntimeValues(JSMoreItemTooltipHandler.createCaptureCageRuntimeTooltip(rawBoth, 20L));
    }

    @Test
    void validCaptureRuntimeTooltipUsesOneProjectedDurabilityPercentageArgument() {
        ItemStack valid = new ItemStack(Items.STONE);
        DinosaurCaptureItemData.setContents(
                valid,
                capturedData(375),
                new DinosaurCaptureSupplies(1, 2, 3, 4)
        );

        List<Component> lines = JSMoreItemTooltipHandler.createCaptureCageRuntimeTooltip(valid, 0L);

        assertEquals(7, lines.size());
        assertTranslation(lines.get(4), "tooltip.jsmore.dinosaur_capture_box.durability", "75%");
        assertEquals(ChatFormatting.DARK_GREEN.getColor(), lines.get(4).getStyle().getColor().getValue());
    }

    private static void assertReserveLines(
            List<Component> lines,
            int anesthetic,
            int water,
            int carnivore,
            int herbivore
    ) {
        assertEquals(4, lines.size());
        assertTranslation(lines.get(0), "tooltip.jsmore.dinosaur_capture_box.anesthetic_reserve", anesthetic);
        assertTranslation(lines.get(1), "tooltip.jsmore.dinosaur_capture_box.water_reserve", water);
        assertTranslation(lines.get(2), "tooltip.jsmore.dinosaur_capture_box.carnivore_reserve", carnivore);
        assertTranslation(lines.get(3), "tooltip.jsmore.dinosaur_capture_box.herbivore_reserve", herbivore);
    }

    private static void assertTranslation(Component component, String key, Object... expectedArguments) {
        TranslatableContents contents = (TranslatableContents) component.getContents();
        assertEquals(key, contents.getKey());
        assertEquals(List.of(expectedArguments), List.of(contents.getArgs()));
    }

    private static void assertNoRuntimeValues(List<Component> lines) {
        assertFalse(lines.stream().map(Component::getContents)
                .filter(TranslatableContents.class::isInstance)
                .map(TranslatableContents.class::cast)
                .map(TranslatableContents::getKey)
                .anyMatch(key -> key.endsWith(".durability")
                        || key.endsWith(".anesthetic_remaining")
                        || key.endsWith(".captured_duration")));
    }

    private static CapturedDinosaurData capturedData() {
        return capturedData(CapturedDinosaurData.MAX_DURABILITY);
    }

    private static CapturedDinosaurData capturedData(int durability) {
        ResourceLocation entityType = ResourceLocation.fromNamespaceAndPath("minecraft", "pig");
        UUID uuid = UUID.randomUUID();
        CompoundTag entityNbt = new CompoundTag();
        entityNbt.putString("id", entityType.toString());
        entityNbt.putUUID("UUID", uuid);
        return new CapturedDinosaurData(
                entityType,
                uuid,
                "Pig",
                0L,
                0L,
                0L,
                durability,
                entityNbt,
                new CompoundTag(),
                CapturedDinosaurVitals.deserializeNBT(new CompoundTag())
        );
    }
}
