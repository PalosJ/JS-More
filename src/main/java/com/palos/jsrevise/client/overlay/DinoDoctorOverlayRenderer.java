package com.palos.jsrevise.client.overlay;

import com.palos.jsrevise.compat.curios.DinoDoctorGogglesWearResolver;
import com.palos.jsrevise.server.registry.JSReviseItems;
import com.palos.jsrevise.system.observation.DinosaurObservationSnapshot;
import com.palos.jsrevise.system.observation.ObservedGene;
import com.palos.jsrevise.system.observation.DinosaurObservationSystem;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalLong;
import jp.jurassicsaga.server.animal.entity.obj.bases.JSAnimalBase;
import net.minecraft.client.Minecraft;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public final class DinoDoctorOverlayRenderer {
    private static final int REAL_AGE_DAYS_PER_YEAR = 365;
    private static final double REAL_AGE_DAYS_PER_MONTH = REAL_AGE_DAYS_PER_YEAR / 12.0D;
    private static final double OBSERVE_RANGE = 8.0D;
    public static final LayeredDraw.Layer OVERLAY = DinoDoctorOverlayRenderer::renderOverlay;
    private static final int PANEL_PADDING = 4;
    private static final int CONTENT_X_OFFSET = 20;
    private static final int LINE_HEIGHT = 11;
    private static final int GENE_ICON_SIZE = 18;
    private static final int GENE_COLUMN_GAP = 2;
    private static final int GENE_ROW_GAP = 6;
    private static final float OVERLAY_SCALE = 0.85F;
    private static final float FONT_SCALE = 0.90F / OVERLAY_SCALE;
    private static final float GENE_LABEL_SCALE = 0.65F * FONT_SCALE;
    private static final long TARGET_REFRESH_TICKS = 3L;
    private static JSAnimalBase cachedAnimal;
    private static long nextTargetRefreshTick;

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

        JSAnimalBase animal = resolveObservedAnimalCached(minecraft);
        if (animal == null) {
            return;
        }

        DinosaurObservationSnapshot snapshot = DinosaurObservationSystem.capture(animal);
        DinosaurDnaVisualResolver.DnaVisual dnaVisual = DinosaurDnaVisualResolver.resolve(animal);
        List<OverlayLine> lines = new ArrayList<>();
        lines.add(OverlayLine.basic(snapshot.displayName(), 0xDDEEFF));
        lines.add(OverlayLine.basic(formatAge(snapshot), 0xBFD1E6));
        lines.add(OverlayLine.basic(formatHealth(snapshot), 0xBFD1E6));
        lines.add(OverlayLine.basic(formatGender(snapshot), 0xBFD1E6));
        lines.add(OverlayLine.basic(formatPercent("overlay.jsrevise.hunger", snapshot.hungerPercent()), 0xBFD1E6));
        lines.add(OverlayLine.basic(formatPercent("overlay.jsrevise.thirst", snapshot.thirstPercent()), 0xBFD1E6));
        lines.add(OverlayLine.basic(formatPercent("overlay.jsrevise.mood", snapshot.moodPercent()), 0xBFD1E6));
        if (snapshot.pendingAnestheticTicks().isPresent()) {
            lines.add(OverlayLine.basic(formatAnestheticLine("overlay.jsrevise.anesthetic.pending", snapshot.pendingAnestheticTicks().getAsLong()), 0xBFD1E6));
        }
        if (snapshot.remainingAnestheticTicks().isPresent() || snapshot.queuedAnestheticTicks().isPresent()) {
            Component suffix = snapshot.queuedAnestheticTicks().isPresent()
                    ? Component.translatable("overlay.jsrevise.anesthetic.addition", formatDecimal(snapshot.queuedAnestheticTicks().getAsLong() / 20.0D) + "s")
                    : Component.empty();
            Component mainText = snapshot.remainingAnestheticTicks().isPresent()
                    ? formatAnestheticLine("overlay.jsrevise.anesthetic.remaining", snapshot.remainingAnestheticTicks().getAsLong())
                    : Component.translatable("overlay.jsrevise.anesthetic.remaining_label");
            lines.add(OverlayLine.withSuffix(
                    mainText,
                    0xBFD1E6,
                    suffix,
                    0x7D8794,
                    0.75F
            ));
        }
        boolean hasGenes = !snapshot.genes().isEmpty();
        if (hasGenes) {
            lines.add(OverlayLine.basic(Component.translatable("overlay.jsrevise.genes"), 0x9FB1C4));
        }

        int maxWidth = 0;
        for (OverlayLine line : lines) {
            maxWidth = Math.max(maxWidth, measureLineWidth(minecraft, line));
        }
        GeneLayout geneLayout = GeneLayout.empty();
        if (hasGenes) {
            geneLayout = computeGeneLayout(minecraft, snapshot.genes());
            maxWidth = Math.max(maxWidth, geneLayout.totalWidth());
        }

        int virtualWidth = Mth.floor(guiGraphics.guiWidth() / OVERLAY_SCALE);
        int virtualHeight = Mth.floor(guiGraphics.guiHeight() / OVERLAY_SCALE);
        int x = Math.max(12, (virtualWidth / 2) - maxWidth - 88);
        int y = Math.max(12, (virtualHeight / 2) - 82);
        int width = maxWidth + 28;
        int contentHeight = 12 + (lines.size() * LINE_HEIGHT);
        int height = contentHeight + (hasGenes ? geneLayout.totalHeight() + 8 : 0);

        int panelLeft = x - 6;
        int panelTop = y - 8;
        int panelRight = x + width + 6;
        int panelBottom = y + height + 6;
        int themeColor = dnaVisual.themeColor();
        int borderTop = withAlpha(mixRgb(themeColor, 0xFFFFFF, 0.34F), 0x7A);
        int borderBottom = withAlpha(shadeRgb(themeColor, 0.48F), 0x60);
        int backgroundTop = withAlpha(shadeRgb(themeColor, 0.24F), 0x3E);
        int backgroundBottom = withAlpha(shadeRgb(themeColor, 0.17F), 0x42);

        guiGraphics.pose().pushPose();
        guiGraphics.pose().scale(OVERLAY_SCALE, OVERLAY_SCALE, 1.0F);
        fillRoundedGradient(guiGraphics, panelLeft - 1, panelTop - 1, panelRight + 1, panelBottom + 1, 6, borderTop, borderBottom);
        fillRoundedGradient(guiGraphics, panelLeft, panelTop, panelRight, panelBottom, 5, backgroundTop, backgroundBottom);

        ItemStack titleIcon = dnaVisual.icon().isEmpty()
                ? new ItemStack(JSReviseItems.DINO_DOCTOR_GOGGLES.get())
                : dnaVisual.icon();
        guiGraphics.renderItem(titleIcon, x - 2, y - 2);

        int lineY = y;
        for (OverlayLine line : lines) {
            drawLine(guiGraphics, minecraft, x + CONTENT_X_OFFSET, lineY, line);
            lineY += LINE_HEIGHT;
        }
        if (hasGenes) {
            drawGenes(guiGraphics, minecraft, snapshot.genes(), x + CONTENT_X_OFFSET, lineY + 2, geneLayout);
        }
        guiGraphics.pose().popPose();
    }

    private static int withAlpha(int rgb, int alpha) {
        return alpha << 24 | rgb & 0xFFFFFF;
    }

    private static int shadeRgb(int color, float factor) {
        int red = Mth.floor((color >> 16 & 0xFF) * factor);
        int green = Mth.floor((color >> 8 & 0xFF) * factor);
        int blue = Mth.floor((color & 0xFF) * factor);
        return red << 16 | green << 8 | blue;
    }

    private static int mixRgb(int from, int to, float progress) {
        int red = Mth.floor(Mth.lerp(progress, from >> 16 & 0xFF, to >> 16 & 0xFF));
        int green = Mth.floor(Mth.lerp(progress, from >> 8 & 0xFF, to >> 8 & 0xFF));
        int blue = Mth.floor(Mth.lerp(progress, from & 0xFF, to & 0xFF));
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

    private static JSAnimalBase resolveObservedAnimalCached(Minecraft minecraft) {
        if (minecraft.level == null) {
            clearCache();
            return null;
        }
        long gameTime = minecraft.level.getGameTime();
        if (cachedAnimal != null
                && cachedAnimal.isAlive()
                && !cachedAnimal.isRemoved()
                && isWithinObservationRange(minecraft.player.getEyePosition(), cachedAnimal.getBoundingBox())
                && gameTime < nextTargetRefreshTick) {
            return cachedAnimal;
        }
        cachedAnimal = resolveObservedAnimal(minecraft);
        nextTargetRefreshTick = gameTime + TARGET_REFRESH_TICKS;
        return cachedAnimal;
    }

    public static void clearCache() {
        cachedAnimal = null;
        nextTargetRefreshTick = 0L;
        DinosaurDnaVisualResolver.clearCache();
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
        double closestX = Mth.clamp(observer.x, bounds.minX, bounds.maxX);
        double closestY = Mth.clamp(observer.y, bounds.minY, bounds.maxY);
        double closestZ = Mth.clamp(observer.z, bounds.minZ, bounds.maxZ);
        return observer.distanceToSqr(closestX, closestY, closestZ) <= OBSERVE_RANGE * OBSERVE_RANGE;
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
            return Component.translatable("overlay.jsrevise.age.game", formatDecimal(gameDays));
        }

        return Component.translatable("overlay.jsrevise.age.unknown");
    }

    private static Component formatDetailedRealAge(double years) {
        long totalDays = Math.max(0L, (long) Math.floor(years * REAL_AGE_DAYS_PER_YEAR + 1.0E-6D));
        int wholeYears = (int) (totalDays / REAL_AGE_DAYS_PER_YEAR);
        double remainingDays = totalDays - ((long) wholeYears * REAL_AGE_DAYS_PER_YEAR);
        int months = (int) Math.floor(remainingDays / REAL_AGE_DAYS_PER_MONTH);
        int days = (int) Math.floor(remainingDays - (months * REAL_AGE_DAYS_PER_MONTH) + 1.0E-6D);
        if (wholeYears > 0) {
            if (months > 0) {
                return Component.translatable("overlay.jsrevise.age.real.year_month", Integer.toString(wholeYears), Integer.toString(months));
            }
            return Component.translatable("overlay.jsrevise.age.real.year_only", Integer.toString(wholeYears));
        }
        if (months > 0) {
            if (days > 0) {
                return Component.translatable("overlay.jsrevise.age.real.month_day", Integer.toString(months), Integer.toString(days));
            }
            return Component.translatable("overlay.jsrevise.age.real.month_only", Integer.toString(months));
        }
        return Component.translatable("overlay.jsrevise.age.real.day_only", Integer.toString(Math.max(0, days)));
    }

    private static Component formatHealth(DinosaurObservationSnapshot snapshot) {
        OptionalDouble currentHealth = snapshot.currentHealth();
        OptionalDouble maxHealth = snapshot.maxHealth();
        String value = currentHealth.isPresent()
                ? formatHealthValue(currentHealth.getAsDouble()) + (maxHealth.isPresent() ? " / " + formatHealthValue(maxHealth.getAsDouble()) : "")
                : Component.translatable("overlay.jsrevise.unknown").getString();
        return Component.translatable("overlay.jsrevise.health", value);
    }

    private static Component formatGender(DinosaurObservationSnapshot snapshot) {
        Component gender = snapshot.male()
                .map(male -> Component.translatable(male ? "overlay.jsrevise.gender.male" : "overlay.jsrevise.gender.female"))
                .orElseGet(() -> Component.translatable("overlay.jsrevise.unknown"));
        return Component.translatable("overlay.jsrevise.gender", gender);
    }

    private static Component formatPercent(String translationKey, OptionalDouble percent) {
        if (percent.isPresent()) {
            return Component.translatable(translationKey, formatDecimal(percent.getAsDouble()) + "%");
        }
        return Component.translatable(translationKey, Component.translatable("overlay.jsrevise.unknown"));
    }

    private static Component formatAnestheticLine(String translationKey, long ticks) {
        return Component.translatable(translationKey, formatDecimal(ticks / 20.0D) + "s");
    }

    private static int measureLineWidth(Minecraft minecraft, OverlayLine line) {
        int width = (int) Math.ceil(minecraft.font.width(line.mainText()) * FONT_SCALE);
        if (line.hasSuffix()) {
            width += 4 + (int) Math.ceil(
                    minecraft.font.width(line.suffixText()) * line.suffixScale() * FONT_SCALE
            );
        }
        return width;
    }

    private static void drawLine(GuiGraphics guiGraphics, Minecraft minecraft, int x, int y, OverlayLine line) {
        drawScaledString(guiGraphics, minecraft, line.mainText(), x, y, line.color(), FONT_SCALE);
        if (!line.hasSuffix()) {
            return;
        }

        int mainWidth = (int) Math.ceil(minecraft.font.width(line.mainText()) * FONT_SCALE);
        float suffixScale = line.suffixScale() * FONT_SCALE;
        guiGraphics.pose().pushPose();
        guiGraphics.pose().scale(suffixScale, suffixScale, 1.0F);
        guiGraphics.drawString(
                minecraft.font,
                line.suffixText().getVisualOrderText(),
                (x + mainWidth + 4) / suffixScale,
                (y + 2.0F) / suffixScale,
                line.suffixColor(),
                false
        );
        guiGraphics.pose().popPose();
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

    private record OverlayLine(Component mainText, int color, Component suffixText, int suffixColor, float suffixScale) {
        private static OverlayLine basic(Component mainText, int color) {
            return new OverlayLine(mainText, color, Component.empty(), color, 1.0F);
        }

        private static OverlayLine withSuffix(Component mainText, int color, Component suffixText, int suffixColor, float suffixScale) {
            return new OverlayLine(mainText, color, suffixText, suffixColor, suffixScale);
        }

        private boolean hasSuffix() {
            return !this.suffixText.getString().isEmpty();
        }
    }

    private record GeneLayout(int columns, int cellWidth, int cellHeight, int totalWidth, int totalHeight) {
        private static GeneLayout empty() {
            return new GeneLayout(1, 0, 0, 0, 0);
        }
    }
}
