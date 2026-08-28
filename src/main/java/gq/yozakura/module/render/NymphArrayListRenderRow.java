package gq.yozakura.module.render;

/** Reused per-frame layout snapshot shared by the Nymph background and text passes. */
final class NymphArrayListRenderRow {
    String text;
    float textX;
    float textY;
    float textWidth;
    float rowY;
    float progress;
    int color;
    boolean scaleIn;
    final NymphArrayListBackgroundPlan.Bounds surface =
            new NymphArrayListBackgroundPlan.Bounds(0.0F, 0.0F, 0.0F, 0.0F);

    void set(String text, float textX, float textY, float textWidth, float rowY,
             float progress, int color, boolean scaleIn) {
        this.text = text;
        this.textX = textX;
        this.textY = textY;
        this.textWidth = textWidth;
        this.rowY = rowY;
        this.progress = progress;
        this.color = color;
        this.scaleIn = scaleIn;

        float left = textX - 2.0F;
        float top = rowY - 3.0F;
        float right = textX + textWidth + 3.0F;
        float bottom = rowY + 8.0F;
        if (scaleIn) {
            float scale = Math.max(0.01F, progress);
            float centerX = (left + right) * 0.5F;
            float centerY = (top + bottom) * 0.5F;
            float halfWidth = (right - left) * 0.5F * scale;
            float halfHeight = (bottom - top) * 0.5F * scale;
            left = centerX - halfWidth;
            right = centerX + halfWidth;
            top = centerY - halfHeight;
            bottom = centerY + halfHeight;
        }
        surface.left = left;
        surface.top = top;
        surface.right = right;
        surface.bottom = bottom;
    }
}
