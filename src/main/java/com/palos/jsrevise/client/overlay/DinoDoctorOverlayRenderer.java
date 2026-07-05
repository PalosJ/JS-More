package com.palos.jsrevise.client.overlay;

import com.palos.jsrevise.compat.curios.DinoDoctorGogglesWearResolver;
import com.palos.jsrevise.server.block.DinosaurCaptureCageBlock;
import com.palos.jsrevise.server.registry.JSReviseBlocks;
import com.palos.jsrevise.server.registry.JSReviseItems;
import com.palos.jsrevise.server.system.capture.CapturedDinosaurData;
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
    static final int PANEL_TOP_PADDING = 8;
    static final int PANEL_BOTTOM_PADDING = PANEL_TOP_PADDING;
    static final int PANEL_BORDER_TOP_COLOR = 0x7AFFFFFF;
    static final int PANEL_BORDER_BOTTOM_COLOR = 0x60FFFFFF;
    private static final int CONTENT_X_OFFSET = 20;
    static final int LINE_HEIGHT = 12;
    static final int TITLE_LINE_HEIGHT = 14;
    static final int PROGRESS_BAR_Y_OFFSET = LINE_HEIGHT;
    static final int PROGRESS_BAR_WIDTH = 65;
    static final int PROGRESS_BAR_HEIGHT = 13;
    static final int PROGRESS_LINE_HEIGHT = PROGRESS_BAR_Y_OFFSET + PROGRESS_BAR_HEIGHT;
    static final int JADE_NESTED_BOX_BORDER_WIDTH = 1;
    static final int JADE_NESTED_BOX_BORDER_COLOR = 0xFF808080;
    static final int JADE_PROGRESS_DARK_COLOR = 0xFFB2B2B2;
    static final int JADE_PROGRESS_LIGHT_COLOR = 0xFFFFFFFF;
    private static final int GENE_ICON_SIZE = 18;
    private static final int GENE_COLUMN_GAP = 2;
    private static final int GENE_ROW_GAP = 6;
    private static final float OVERLAY_SCALE = 0.85F;
    static final float FONT_SCALE = 0.90F / OVERLAY_SCALE;
    static final float LABEL_FONT_SCALE = 0.98F / OVERLAY_SCALE;
    static final float TITLE_FONT_SCALE = 1.05F / OVERLAY_SCALE;
    private static final float PANEL_BACKGROUND_TOP_SHADE = 0.24F;
    private static final float PANEL_BACKGROUND_BOTTOM_SHADE = 0.17F;
    private static final int PANEL_BACKGROUND_TOP_ALPHA = 0x68;
    private static final int PANEL_BACKGROUND_BOTTOM_ALPHA = 0x72;
    private static final float GENE_LABEL_SCALE = 0.65F * FONT_SCALE;
    private static final long TARGET_REFRESH_TICKS = 3L;
    private static ObservedTarget cachedTarget;
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

        ObservedTarget target = resolveObservedTargetCached(minecraft);
        if (target == null) {
            return;
        }

        DinosaurDnaVisualResolver.DnaVisual dnaVisual;
        DinosaurObservationSnapshot snapshot;
        List<OverlayLine> lines;
        Optional<OverlayLine> eggProgressLine = Optional.empty();
        if (target.animal() != null) {
            JSAnimalBase animal = target.animal();
            dnaVisual = DinosaurDnaVisualResolver.resolve(animal);
            snapshot = DinosaurObservationSystem.capture(animal)
                    .withEggLayingProgress(ClientEggLayingProgressCache.getOrRequest(animal));
            lines = createObservationLines(snapshot);
            eggProgressLine = createEggProgressLine(snapshot);
        } else {
            Optional<CaptureCageObservationSnapshot> cageSnapshot =
                    ClientCaptureCageObservationCache.getOrRequest(minecraft.level, target.cagePos());
            if (cageSnapshot.isEmpty()) {
                return;
            }
            snapshot = cageSnapshot.get().observation();
            dnaVisual = DinosaurDnaVisualResolver.resolve(snapshot.ageEstimate().speciesId());
            lines = createCaptureCageObservationLines(cageSnapshot.get());
        }
        int themeColor = dnaVisual.themeColor();
        boolean hasGenes = !snapshot.genes().isEmpty();

        int maxWidth = 0;
        for (OverlayLine line : lines) {
            maxWidth = Math.max(maxWidth, measureLineWidth(minecraft, line));
        }
        if (eggProgressLine.isPresent()) {
            maxWidth = Math.max(maxWidth, measureLineWidth(minecraft, eggProgressLine.get()));
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
        int linesHeight = 0;
        for (OverlayLine line : lines) {
            linesHeight += line.height();
        }
        int contentBottom = y + linesHeight;
        if (hasGenes) {
            contentBottom += 2 + geneLayout.totalHeight();
        }
        if (eggProgressLine.isPresent()) {
            contentBottom += (hasGenes ? LINE_HEIGHT : 0) + eggProgressLine.get().height();
        }

        int panelLeft = x - PANEL_HORIZONTAL_PADDING;
        int panelTop = y - PANEL_TOP_PADDING;
        int panelRight = x + width + PANEL_HORIZONTAL_PADDING;
        int panelBottom = contentBottom + PANEL_BOTTOM_PADDING;
        int borderTop = PANEL_BORDER_TOP_COLOR;
        int borderBottom = PANEL_BORDER_BOTTOM_COLOR;
        int backgroundTop = panelBackgroundTopColor(themeColor);
        int backgroundBottom = panelBackgroundBottomColor(themeColor);

        guiGraphics.pose().pushPose();
        guiGraphics.pose().scale(OVERLAY_SCALE, OVERLAY_SCALE, 1.0F);
        fillRoundedGradient(guiGraphics, panelLeft, panelTop, panelRight, panelBottom, 5, backgroundTop, backgroundBottom);
        strokeRoundedGradient(
                guiGraphics,
                panelLeft - 1,
                panelTop - 1,
                panelRight + 1,
                panelBottom + 1,
                6,
                panelLeft,
                panelTop,
                panelRight,
                panelBottom,
                5,
                borderTop,
                borderBottom
        );

        ItemStack titleIcon = dnaVisual.icon().isEmpty()
                ? new ItemStack(JSReviseItems.DINO_DOCTOR_GOGGLES.get())
                : dnaVisual.icon();
        guiGraphics.renderItem(titleIcon, x - 2, y - 2);

        int lineY = y;
        for (OverlayLine line : lines) {
            drawLine(guiGraphics, minecraft, x + CONTENT_X_OFFSET, lineY, line);
            lineY += line.height();
        }
        if (hasGenes) {
            int genesY = lineY + 2;
            drawGenes(guiGraphics, minecraft, snapshot.genes(), x + CONTENT_X_OFFSET, genesY, geneLayout);
            lineY = genesY + geneLayout.totalHeight();
        }
        if (eggProgressLine.isPresent()) {
            if (hasGenes) {
                lineY += LINE_HEIGHT;
            }
            drawLine(guiGraphics, minecraft, x + CONTENT_X_OFFSET, lineY, eggProgressLine.get());
        }
        guiGraphics.pose().popPose();
    }

    static List<OverlayLine> createObservationLines(DinosaurObservationSnapshot snapshot) {
        List<OverlayLine> lines = new ArrayList<>();
        lines.add(OverlayLine.title(snapshot.displayName(), 0xDDEEFF));
        lines.add(OverlayLine.labeled(Component.translatable("overlay.jsrevise.age.label"), formatAge(snapshot), 0xBFD1E6));
        lines.add(OverlayLine.labeled(Component.translatable("overlay.jsrevise.health"), formatHealth(snapshot), 0xBFD1E6));
        lines.add(OverlayLine.labeled(Component.translatable("overlay.jsrevise.gender"), formatGender(snapshot), 0xBFD1E6));
        lines.add(OverlayLine.labeled(Component.translatable("overlay.jsrevise.hunger"), formatPercent(snapshot.hungerPercent()), 0xBFD1E6));
        lines.add(OverlayLine.labeled(Component.translatable("overlay.jsrevise.thirst"), formatPercent(snapshot.thirstPercent()), 0xBFD1E6));
        lines.add(OverlayLine.labeled(Component.translatable("overlay.jsrevise.mood"), formatPercent(snapshot.moodPercent()), 0xBFD1E6));
        if (snapshot.pendingAnestheticTicks().isPresent()) {
            lines.add(OverlayLine.labeled(
                    Component.translatable("overlay.jsrevise.anesthetic.pending"),
                    formatAnestheticDuration(snapshot.pendingAnestheticTicks().getAsLong()),
                    0xBFD1E6
            ));
        }
        if (snapshot.remainingAnestheticTicks().isPresent() || snapshot.queuedAnestheticTicks().isPresent()) {
            Component suffix = snapshot.queuedAnestheticTicks().isPresent()
                    ? Component.translatable(
                            "overlay.jsrevise.anesthetic.addition",
                            plainValue(formatDecimal(snapshot.queuedAnestheticTicks().getAsLong() / 20.0D) + "s")
                    )
                    : Component.empty();
            Component label = Component.translatable("overlay.jsrevise.anesthetic.remaining");
            if (snapshot.remainingAnestheticTicks().isPresent()) {
                lines.add(OverlayLine.labeledWithSuffix(
                        label,
                        formatAnestheticDuration(snapshot.remainingAnestheticTicks().getAsLong()),
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
                progress.progress()
        ));
    }

    static List<OverlayLine> createCaptureCageObservationLines(CaptureCageObservationSnapshot snapshot) {
        List<OverlayLine> lines = new ArrayList<>(createObservationLines(snapshot.observation()));
        OverlayLine durabilityLine = OverlayLine.labeled(
                Component.translatable("overlay.jsrevise.capture_box.durability"),
                plainValue(snapshot.cageDurability() + "/" + CapturedDinosaurData.MAX_DURABILITY),
                0xBFD1E6
        );
        OverlayLine durationLine = OverlayLine.labeled(
                Component.translatable("overlay.jsrevise.capture_box.duration"),
                formatAnestheticDuration(snapshot.capturedDurationTicks()),
                0xBFD1E6
        );
        if (!snapshot.observation().genes().isEmpty() && !lines.isEmpty()) {
            lines.add(lines.size() - 1, durabilityLine);
            lines.add(lines.size() - 1, durationLine);
        } else {
            lines.add(durabilityLine);
            lines.add(durationLine);
        }
        return List.copyOf(lines);
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
        long gameTime = minecraft.level.getGameTime();
        if (cachedTarget != null
                && cachedTarget.isStillValid(minecraft)
                && gameTime < nextTargetRefreshTick) {
            return cachedTarget;
        }
        cachedTarget = resolveObservedTarget(minecraft);
        nextTargetRefreshTick = gameTime + TARGET_REFRESH_TICKS;
        return cachedTarget;
    }

    public static void clearCache() {
        cachedTarget = null;
        nextTargetRefreshTick = 0L;
        DinosaurDnaVisualResolver.clearCache();
        ClientEggLayingProgressCache.clearCache();
        ClientCaptureCageObservationCache.clearCache();
    }

    private static ObservedTarget resolveObservedTarget(Minecraft minecraft) {
        JSAnimalBase animal = resolveObservedAnimal(minecraft);
        if (animal != null) {
            return ObservedTarget.animal(animal);
        }
        BlockPos cagePos = resolveObservedCagePos(minecraft);
        return cagePos == null ? null : ObservedTarget.cage(cagePos);
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

    private static BlockPos resolveObservedCagePos(Minecraft minecraft) {
        if (minecraft.level == null || minecraft.player == null || !(minecraft.hitResult instanceof BlockHitResult blockHitResult)) {
            return null;
        }
        BlockPos hitPos = blockHitResult.getBlockPos();
        BlockState state = minecraft.level.getBlockState(hitPos);
        if (!isCaptureBoxObservationBlock(state)) {
            return null;
        }
        BlockPos controllerPos = DinosaurCaptureCageBlock.controllerPos(hitPos, state);
        if (!isWithinObservationRange(minecraft.player.getEyePosition(), cageBounds(controllerPos, state.getValue(DinosaurCaptureCageBlock.FACING)))) {
            return null;
        }
        return controllerPos;
    }

    private static AABB cageBounds(BlockPos controllerPos, net.minecraft.core.Direction facing) {
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;
        for (DinosaurCaptureCageBlock.PartPlacement placement : DinosaurCaptureCageBlock.placements(controllerPos, facing)) {
            BlockPos pos = placement.pos();
            minX = Math.min(minX, pos.getX());
            minY = Math.min(minY, pos.getY());
            minZ = Math.min(minZ, pos.getZ());
            maxX = Math.max(maxX, pos.getX());
            maxY = Math.max(maxY, pos.getY());
            maxZ = Math.max(maxZ, pos.getZ());
        }
        return new AABB(minX, minY, minZ, maxX + 1.0D, maxY + 1.0D, maxZ + 1.0D);
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

    private static Component formatPercent(OptionalDouble percent) {
        if (percent.isPresent()) {
            return plainValue(formatDecimal(percent.getAsDouble()) + "%");
        }
        return plainValue(Component.translatable("overlay.jsrevise.unknown"));
    }

    private static Component formatAnestheticDuration(long ticks) {
        return plainValue(formatDecimal(ticks / 20.0D) + "s");
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
            width += 4 + (int) Math.ceil(
                    minecraft.font.width(line.suffixText()) * line.suffixScale() * FONT_SCALE
            );
        }
        if (line.hasProgress()) {
            width = Math.max(width, PROGRESS_BAR_WIDTH);
        }
        return width;
    }

    private static int measureMainSegmentsWidth(Minecraft minecraft, OverlayLine line) {
        int width = 0;
        for (OverlayTextSegment segment : line.mainSegments()) {
            width += (int) Math.ceil(minecraft.font.width(segment.text()) * segment.scale());
        }
        return width;
    }

    private static void drawLine(GuiGraphics guiGraphics, Minecraft minecraft, int x, int y, OverlayLine line) {
        int drawX = x;
        for (OverlayTextSegment segment : line.mainSegments()) {
            drawScaledString(guiGraphics, minecraft, segment.text(), drawX, y, line.color(), segment.scale());
            drawX += (int) Math.ceil(minecraft.font.width(segment.text()) * segment.scale());
        }
        if (line.hasProgress()) {
            drawProgressBar(guiGraphics, x, y + PROGRESS_BAR_Y_OFFSET, line.progress());
        }
        if (!line.hasSuffix()) {
            return;
        }

        int mainWidth = measureMainSegmentsWidth(minecraft, line);
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

    private static void drawProgressBar(GuiGraphics guiGraphics, int x, int y, double progress) {
        double clamped = clampProgress(progress);
        int left = x;
        int top = y;
        int right = x + PROGRESS_BAR_WIDTH;
        int bottom = y + PROGRESS_BAR_HEIGHT;
        guiGraphics.fill(left, top, right, top + JADE_NESTED_BOX_BORDER_WIDTH, JADE_NESTED_BOX_BORDER_COLOR);
        guiGraphics.fill(left, bottom - JADE_NESTED_BOX_BORDER_WIDTH, right, bottom, JADE_NESTED_BOX_BORDER_COLOR);
        guiGraphics.fill(left, top + JADE_NESTED_BOX_BORDER_WIDTH, left + JADE_NESTED_BOX_BORDER_WIDTH, bottom - JADE_NESTED_BOX_BORDER_WIDTH, JADE_NESTED_BOX_BORDER_COLOR);
        guiGraphics.fill(right - JADE_NESTED_BOX_BORDER_WIDTH, top + JADE_NESTED_BOX_BORDER_WIDTH, right, bottom - JADE_NESTED_BOX_BORDER_WIDTH, JADE_NESTED_BOX_BORDER_COLOR);

        int innerLeft = left + JADE_NESTED_BOX_BORDER_WIDTH;
        int innerTop = top + JADE_NESTED_BOX_BORDER_WIDTH;
        int innerRight = right - JADE_NESTED_BOX_BORDER_WIDTH;
        int innerBottom = bottom - JADE_NESTED_BOX_BORDER_WIDTH;
        int innerWidth = innerRight - innerLeft;
        int fillWidth = Mth.floor(innerWidth * clamped);
        if (fillWidth > 0) {
            int fillRight = innerLeft + Math.min(innerWidth, fillWidth);
            int splitY = innerTop + ((innerBottom - innerTop) / 2);
            fillVerticalGradient(guiGraphics, innerLeft, innerTop, fillRight, splitY, JADE_PROGRESS_DARK_COLOR, JADE_PROGRESS_LIGHT_COLOR);
            fillVerticalGradient(guiGraphics, innerLeft, splitY, fillRight, innerBottom, JADE_PROGRESS_LIGHT_COLOR, JADE_PROGRESS_DARK_COLOR);
        }
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

    record OverlayLine(
            List<OverlayTextSegment> mainSegments,
            int color,
            Component suffixText,
            int suffixColor,
            float suffixScale,
            double progress,
            boolean progressLine,
            float mainScale,
            int lineHeight
    ) {
        OverlayLine {
            mainSegments = List.copyOf(mainSegments);
        }

        private static OverlayLine labeled(Component label, Component value, int color) {
            return new OverlayLine(
                    List.of(OverlayTextSegment.label(label), OverlayTextSegment.value(value)),
                    color,
                    Component.empty(),
                    color,
                    1.0F,
                    0.0D,
                    false,
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
                    0.0D,
                    false,
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
                    0.0D,
                    false,
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
            return withSuffix(
                    List.of(OverlayTextSegment.label(label), OverlayTextSegment.value(value)),
                    color,
                    suffixText,
                    suffixColor,
                    suffixScale
            );
        }

        private static OverlayLine withSuffix(
                List<OverlayTextSegment> mainSegments,
                int color,
                Component suffixText,
                int suffixColor,
                float suffixScale
        ) {
            return new OverlayLine(
                    mainSegments,
                    color,
                    suffixText,
                    suffixColor,
                    suffixScale,
                    0.0D,
                    false,
                    LABEL_FONT_SCALE,
                    LINE_HEIGHT
            );
        }

        private static OverlayLine progress(Component mainText, int color, double progress) {
            return new OverlayLine(
                    List.of(OverlayTextSegment.label(mainText)),
                    color,
                    Component.empty(),
                    color,
                    1.0F,
                    clampProgress(progress),
                    true,
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

    private record GeneLayout(int columns, int cellWidth, int cellHeight, int totalWidth, int totalHeight) {
        private static GeneLayout empty() {
            return new GeneLayout(1, 0, 0, 0, 0);
        }
    }

    private record ObservedTarget(JSAnimalBase animal, BlockPos cagePos) {
        static ObservedTarget animal(JSAnimalBase animal) {
            return new ObservedTarget(animal, null);
        }

        static ObservedTarget cage(BlockPos cagePos) {
            return new ObservedTarget(null, cagePos.immutable());
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
            if (this.cagePos == null) {
                return false;
            }
            BlockState state = minecraft.level.getBlockState(this.cagePos);
            return isCaptureBoxObservationBlock(state)
                    && isWithinObservationRange(
                    minecraft.player.getEyePosition(),
                    cageBounds(this.cagePos, state.getValue(DinosaurCaptureCageBlock.FACING))
            );
        }
    }

    static boolean isCaptureBoxObservationBlock(BlockState state) {
        return state.is(JSReviseBlocks.DINOSAUR_CAPTURE_CAGE.get());
    }
}
