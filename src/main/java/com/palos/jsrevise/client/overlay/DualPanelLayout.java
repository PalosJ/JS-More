package com.palos.jsrevise.client.overlay;

import java.util.Optional;

public record DualPanelLayout(Optional<PanelRect> left, PanelRect right, float scale) {
    static final int MARGIN = 4;
    static final int PREFERRED_LEFT_CENTER_CLEARANCE = 80;
    static final int PREFERRED_RIGHT_CENTER_CLEARANCE = 96;
    static final int MIN_CENTER_CLEARANCE = 24;
    private static final int STACK_GAP = 4;
    private static final float MIN_SCALE = 0.75F;
    private static final float[] SCALES = {1.0F, 0.80F, MIN_SCALE};

    public DualPanelLayout {
        left = left == null ? Optional.empty() : left;
        if (right == null) {
            throw new IllegalArgumentException("Right panel is required");
        }
    }

    public static DualPanelLayout arrange(
            int viewportWidth,
            int viewportHeight,
            int leftWidth,
            int leftHeight,
            int rightWidth,
            int rightHeight
    ) {
        validateViewport(viewportWidth, viewportHeight, rightWidth, rightHeight);
        boolean hasLeft = leftWidth > 0 && leftHeight > 0;
        if (!hasLeft) {
            return arrangeSingleRight(viewportWidth, viewportHeight, rightWidth, rightHeight);
        }

        for (float scale : SCALES) {
            DualPanelLayout layout = sideBySide(
                    viewportWidth,
                    viewportHeight,
                    leftWidth,
                    leftHeight,
                    rightWidth,
                    rightHeight,
                    scale,
                    PREFERRED_LEFT_CENTER_CLEARANCE,
                    PREFERRED_RIGHT_CENTER_CLEARANCE
            );
            if (fits(layout, viewportWidth, viewportHeight)) {
                return layout;
            }
        }

        int adaptiveLeftClearance = maximumLeftClearance(viewportWidth, leftWidth, MIN_SCALE);
        int adaptiveRightClearance = maximumRightClearance(viewportWidth, rightWidth, MIN_SCALE);
        if (adaptiveLeftClearance >= MIN_CENTER_CLEARANCE
                && adaptiveRightClearance >= MIN_CENTER_CLEARANCE) {
            DualPanelLayout compact = sideBySide(
                    viewportWidth,
                    viewportHeight,
                    leftWidth,
                    leftHeight,
                    rightWidth,
                    rightHeight,
                    MIN_SCALE,
                    Math.min(PREFERRED_LEFT_CENTER_CLEARANCE, adaptiveLeftClearance),
                    Math.min(PREFERRED_RIGHT_CENTER_CLEARANCE, adaptiveRightClearance)
            );
            if (fits(compact, viewportWidth, viewportHeight)) {
                return compact;
            }
        }

        for (float scale : SCALES) {
            DualPanelLayout layout = stacked(
                    viewportWidth,
                    viewportHeight,
                    leftWidth,
                    leftHeight,
                    rightWidth,
                    rightHeight,
                    scale
            );
            if (fits(layout, viewportWidth, viewportHeight)) {
                return layout;
            }
        }
        return stacked(
                viewportWidth,
                viewportHeight,
                leftWidth,
                leftHeight,
                rightWidth,
                rightHeight,
                MIN_SCALE
        );
    }

    static SinglePanelLayout arrangeSingleLeft(
            int viewportWidth,
            int viewportHeight,
            int panelWidth,
            int panelHeight
    ) {
        validateViewport(viewportWidth, viewportHeight, panelWidth, panelHeight);
        for (float scale : SCALES) {
            SinglePanelLayout layout = singleLeft(
                    viewportWidth,
                    viewportHeight,
                    panelWidth,
                    panelHeight,
                    scale,
                    PREFERRED_LEFT_CENTER_CLEARANCE
            );
            if (layout.panel().isInside(viewportWidth, viewportHeight, MARGIN)) {
                return layout;
            }
        }
        int adaptiveClearance = maximumLeftClearance(viewportWidth, panelWidth, MIN_SCALE);
        if (adaptiveClearance >= MIN_CENTER_CLEARANCE) {
            SinglePanelLayout compact = singleLeft(
                    viewportWidth,
                    viewportHeight,
                    panelWidth,
                    panelHeight,
                    MIN_SCALE,
                    Math.min(PREFERRED_LEFT_CENTER_CLEARANCE, adaptiveClearance)
            );
            if (compact.panel().isInside(viewportWidth, viewportHeight, MARGIN)) {
                return compact;
            }
        }
        int width = scaled(panelWidth, MIN_SCALE);
        int height = scaled(panelHeight, MIN_SCALE);
        return new SinglePanelLayout(
                new PanelRect(MARGIN, centeredTop(viewportHeight, height), MARGIN + width, centeredTop(viewportHeight, height) + height),
                MIN_SCALE
        );
    }

    private static DualPanelLayout arrangeSingleRight(
            int viewportWidth,
            int viewportHeight,
            int rightWidth,
            int rightHeight
    ) {
        for (float scale : SCALES) {
            DualPanelLayout layout = singleRight(
                    viewportWidth,
                    viewportHeight,
                    rightWidth,
                    rightHeight,
                    scale,
                    PREFERRED_RIGHT_CENTER_CLEARANCE
            );
            if (fits(layout, viewportWidth, viewportHeight)) {
                return layout;
            }
        }
        int adaptiveClearance = maximumRightClearance(viewportWidth, rightWidth, MIN_SCALE);
        if (adaptiveClearance >= MIN_CENTER_CLEARANCE) {
            DualPanelLayout compact = singleRight(
                    viewportWidth,
                    viewportHeight,
                    rightWidth,
                    rightHeight,
                    MIN_SCALE,
                    Math.min(PREFERRED_RIGHT_CENTER_CLEARANCE, adaptiveClearance)
            );
            if (fits(compact, viewportWidth, viewportHeight)) {
                return compact;
            }
        }
        int width = scaled(rightWidth, MIN_SCALE);
        int height = scaled(rightHeight, MIN_SCALE);
        PanelRect right = new PanelRect(
                viewportWidth - MARGIN - width,
                centeredTop(viewportHeight, height),
                viewportWidth - MARGIN,
                centeredTop(viewportHeight, height) + height
        );
        return new DualPanelLayout(Optional.empty(), right, MIN_SCALE);
    }

    private static DualPanelLayout sideBySide(
            int viewportWidth,
            int viewportHeight,
            int leftWidth,
            int leftHeight,
            int rightWidth,
            int rightHeight,
            float scale,
            int leftCenterClearance,
            int rightCenterClearance
    ) {
        int scaledLeftWidth = scaled(leftWidth, scale);
        int scaledLeftHeight = scaled(leftHeight, scale);
        int scaledRightWidth = scaled(rightWidth, scale);
        int scaledRightHeight = scaled(rightHeight, scale);
        int center = viewportWidth / 2;
        int top = centeredTop(viewportHeight, Math.max(scaledLeftHeight, scaledRightHeight));
        PanelRect left = new PanelRect(
                center - leftCenterClearance - scaledLeftWidth,
                top,
                center - leftCenterClearance,
                top + scaledLeftHeight
        );
        PanelRect right = new PanelRect(
                center + rightCenterClearance,
                top,
                center + rightCenterClearance + scaledRightWidth,
                top + scaledRightHeight
        );
        return new DualPanelLayout(Optional.of(left), right, scale);
    }

    private static DualPanelLayout singleRight(
            int viewportWidth,
            int viewportHeight,
            int rightWidth,
            int rightHeight,
            float scale,
            int centerClearance
    ) {
        int width = scaled(rightWidth, scale);
        int height = scaled(rightHeight, scale);
        int left = viewportWidth / 2 + centerClearance;
        int top = centeredTop(viewportHeight, height);
        return new DualPanelLayout(
                Optional.empty(),
                new PanelRect(left, top, left + width, top + height),
                scale
        );
    }

    private static SinglePanelLayout singleLeft(
            int viewportWidth,
            int viewportHeight,
            int panelWidth,
            int panelHeight,
            float scale,
            int centerClearance
    ) {
        int width = scaled(panelWidth, scale);
        int height = scaled(panelHeight, scale);
        int right = viewportWidth / 2 - centerClearance;
        int top = centeredTop(viewportHeight, height);
        return new SinglePanelLayout(new PanelRect(right - width, top, right, top + height), scale);
    }

    private static DualPanelLayout stacked(
            int viewportWidth,
            int viewportHeight,
            int leftWidth,
            int leftHeight,
            int rightWidth,
            int rightHeight,
            float scale
    ) {
        int scaledLeftWidth = scaled(leftWidth, scale);
        int scaledLeftHeight = scaled(leftHeight, scale);
        int scaledRightWidth = scaled(rightWidth, scale);
        int scaledRightHeight = scaled(rightHeight, scale);
        int totalHeight = scaledLeftHeight + STACK_GAP + scaledRightHeight;
        int top = centeredTop(viewportHeight, totalHeight);
        int leftX = Math.max(MARGIN, viewportWidth / 2 - PREFERRED_LEFT_CENTER_CLEARANCE - scaledLeftWidth);
        int rightX = viewportWidth - MARGIN - scaledRightWidth;
        PanelRect left = new PanelRect(leftX, top, leftX + scaledLeftWidth, top + scaledLeftHeight);
        PanelRect right = new PanelRect(
                rightX,
                left.bottom() + STACK_GAP,
                rightX + scaledRightWidth,
                left.bottom() + STACK_GAP + scaledRightHeight
        );
        return new DualPanelLayout(Optional.of(left), right, scale);
    }

    private static int maximumLeftClearance(int viewportWidth, int leftWidth, float scale) {
        int center = viewportWidth / 2;
        return center - MARGIN - scaled(leftWidth, scale);
    }

    private static int maximumRightClearance(int viewportWidth, int rightWidth, float scale) {
        int center = viewportWidth / 2;
        return viewportWidth - MARGIN - center - scaled(rightWidth, scale);
    }

    private static boolean fits(DualPanelLayout layout, int viewportWidth, int viewportHeight) {
        if (!layout.right().isInside(viewportWidth, viewportHeight, MARGIN)) {
            return false;
        }
        return layout.left().isEmpty()
                || layout.left().orElseThrow().isInside(viewportWidth, viewportHeight, MARGIN)
                && !layout.left().orElseThrow().overlaps(layout.right());
    }

    private static void validateViewport(int viewportWidth, int viewportHeight, int panelWidth, int panelHeight) {
        if (viewportWidth <= MARGIN * 2 || viewportHeight <= MARGIN * 2
                || panelWidth <= 0 || panelHeight <= 0) {
            throw new IllegalArgumentException("Viewport and panel dimensions must be positive");
        }
    }

    private static int centeredTop(int viewportHeight, int height) {
        return Math.max(MARGIN, (viewportHeight - height) / 2);
    }

    private static int scaled(int value, float scale) {
        return Math.max(1, (int) Math.ceil(value * scale));
    }

    record SinglePanelLayout(PanelRect panel, float scale) {
    }
}
