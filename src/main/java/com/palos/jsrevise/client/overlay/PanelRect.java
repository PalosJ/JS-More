package com.palos.jsrevise.client.overlay;

public record PanelRect(int left, int top, int right, int bottom) {
    public PanelRect {
        if (right < left || bottom < top) {
            throw new IllegalArgumentException("Panel bounds must not be inverted");
        }
    }

    public int width() {
        return this.right - this.left;
    }

    public int height() {
        return this.bottom - this.top;
    }

    public boolean overlaps(PanelRect other) {
        return this.left < other.right
                && this.right > other.left
                && this.top < other.bottom
                && this.bottom > other.top;
    }

    public boolean isInside(int width, int height, int margin) {
        return this.left >= margin
                && this.top >= margin
                && this.right <= width - margin
                && this.bottom <= height - margin;
    }
}
