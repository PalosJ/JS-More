package com.palos.jsrevise.compat.jade;

import com.palos.jsrevise.compat.curios.DinoDoctorGogglesWearResolver;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import jp.jurassicsaga.server.animal.entity.obj.bases.JSAnimalBase;
import net.minecraft.network.chat.Component;
import snownee.jade.api.Accessor;
import snownee.jade.api.EntityAccessor;
import snownee.jade.api.ITooltip;
import snownee.jade.api.ui.IBoxElement;
import snownee.jade.api.ui.IElement;

public final class JSReviseJadeTooltipFilter {
    private JSReviseJadeTooltipFilter() {
    }

    public static void filterAnimalTooltip(IBoxElement rootElement, Accessor<?> accessor) {
        boolean jurassicSagaAnimal = accessor instanceof EntityAccessor entityAccessor
                && entityAccessor.getEntity() instanceof JSAnimalBase;
        if (!jurassicSagaAnimal || accessor.getPlayer() == null
                || !JadeAnimalTooltipPolicy.shouldFilter(
                true,
                DinoDoctorGogglesWearResolver.isWearing(accessor.getPlayer())
        )) {
            return;
        }
        ITooltip tooltip = rootElement.getTooltip();
        if (tooltip.isEmpty()) {
            return;
        }

        String genderLabel = normalize(Component.translatable("guidebook.jurassicsaga.gender").getString());
        String genesLabel = normalize(Component.translatable("guidebook.jurassicsaga.genes").getString());
        List<List<IElement>> retainedLines = new ArrayList<>();
        int linesToSkip = 0;
        for (int lineIndex = 0; lineIndex < tooltip.size(); lineIndex++) {
            List<IElement> line = readLine(tooltip, lineIndex);
            if (linesToSkip > 0) {
                linesToSkip--;
                continue;
            }
            String message = normalize(readLineMessage(line));
            if (!genderLabel.isBlank() && message.contains(genderLabel)) {
                continue;
            }
            if (!genesLabel.isBlank() && message.contains(genesLabel)) {
                linesToSkip = 2;
                continue;
            }
            retainedLines.add(line);
        }

        tooltip.clear();
        for (List<IElement> line : retainedLines) {
            tooltip.add(line);
        }
    }

    private static List<IElement> readLine(ITooltip tooltip, int lineIndex) {
        List<IElement> line = new ArrayList<>();
        line.addAll(tooltip.get(lineIndex, IElement.Align.LEFT));
        line.addAll(tooltip.get(lineIndex, IElement.Align.CENTER));
        line.addAll(tooltip.get(lineIndex, IElement.Align.RIGHT));
        return line;
    }

    private static String readLineMessage(List<IElement> line) {
        StringBuilder builder = new StringBuilder();
        for (IElement element : line) {
            String message = element.getCachedMessage();
            if (message == null || message.isBlank()) {
                message = element.getMessage();
            }
            if (message == null || message.isBlank()) {
                continue;
            }
            if (!builder.isEmpty()) {
                builder.append(' ');
            }
            builder.append(message);
        }
        return builder.toString();
    }

    private static String normalize(String value) {
        return value == null
                ? ""
                : value.replace('：', ':').replace('\u00A0', ' ').toLowerCase(Locale.ROOT).trim();
    }
}
