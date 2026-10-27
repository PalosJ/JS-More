package com.palos.jsmore.compat.jade;

import java.util.HashSet;
import java.util.Set;
import jp.jurassicsaga.server.entity.animal.entity.obj.bases.JSAnimalBase;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import snownee.jade.api.Accessor;
import snownee.jade.api.EntityAccessor;
import snownee.jade.api.JadeIds;
import snownee.jade.api.ui.IBoxElement;
import snownee.jade.api.ui.TooltipRect;
import snownee.jade.impl.ui.TextElement;

/** Observes the rendered tooltip; never edits or removes upstream content. */
final class JadeRenderedAnimalInformation {
    private static final ResourceLocation ANIMAL = ResourceLocation.fromNamespaceAndPath("jurassicsaga", "animal");
    private JadeRenderedAnimalInformation() { }

    static void afterRender(IBoxElement root, TooltipRect rect, GuiGraphics graphics, Accessor<?> accessor) {
        if (!(accessor instanceof EntityAccessor entities) || !(entities.getEntity() instanceof JSAnimalBase animal)) return;
        Set<String> keys = new HashSet<>();
        var elements = root.getTooltip().get(ANIMAL);
        for (int i = 0; i < elements.size() && i < 256; i++) {
            if (elements.get(i) instanceof TextElement text && text.text instanceof Component component)
                collectKeys(component, keys, 0);
        }
        boolean genes = false;
        if (keys.contains("jade.jurassicsaga.genes")) {
            var geneData = animal.getModules().getGeneticModule().getGeneData();
            if (geneData != null && !geneData.geneDataHolder.getGENE_SET().isEmpty()) {
                Set<String> expected = new HashSet<>();
                geneData.geneDataHolder.getGENE_SET().stream().limit(64)
                        .forEach(gene -> collectKeys(gene.getTranslatableObject(), expected, 0));
                genes = !expected.isEmpty() && keys.containsAll(expected);
            }
        }
        JadeAnimalTooltipPolicy.recordRendered(graphics, animal.level(), animal.getUUID(), new JadeAnimalTooltipPolicy.Fields(
                !root.getTooltip().get(JadeIds.MC_ENTITY_HEALTH).isEmpty(), keys.contains("jade.jurassicsaga.sex"), genes));
    }

    private static void collectKeys(Component component, Set<String> keys, int depth) {
        if (depth > 12 || keys.size() >= 256) return;
        if (component.getContents() instanceof TranslatableContents translated) keys.add(translated.getKey());
        for (int i = 0; i < component.getSiblings().size() && i < 64; i++)
            collectKeys(component.getSiblings().get(i), keys, depth + 1);
    }
}
