package gq.yozakura.module.render;

/** Visual semantics and source geometry for the Nymphilila ArrayList backgrounds. */
final class NymphArrayListBackgroundPlan {
    private final HUD.NymphBackground mode;

    private NymphArrayListBackgroundPlan(HUD.NymphBackground mode) {
        this.mode = mode == null ? HUD.NymphBackground.NONE : mode;
    }

    static NymphArrayListBackgroundPlan forMode(HUD.NymphBackground mode) {
        return new NymphArrayListBackgroundPlan(mode);
    }

    static Bounds bounds(float textX, float rowY, float textWidth) {
        return new Bounds(textX - 2.0F, rowY - 3.0F,
                textX + textWidth + 3.0F, rowY + 8.0F);
    }

    static Bounds connector(Bounds first, Bounds second, float radius) {
        float seam = (first.bottom + second.top) * 0.5F;
        float left = Math.max(first.left, second.left);
        float right = Math.min(first.right, second.right);
        if (right <= left || second.top - first.bottom > 1.0F) {
            return null;
        }
        float halfHeight = Math.max(0.5F, radius);
        return new Bounds(left, seam - halfHeight, right, seam + halfHeight);
    }

    boolean hasSurface() {
        return mode != HUD.NymphBackground.NONE;
    }

    boolean hasBlur() {
        return mode == HUD.NymphBackground.BLUR;
    }

    boolean hasOutline() {
        return mode == HUD.NymphBackground.OUTLINE;
    }

    boolean hasBar() {
        return isLeftBar() || isRightBar();
    }

    boolean isLeftBar() {
        return mode == HUD.NymphBackground.BARLEFT;
    }

    boolean isRightBar() {
        return mode == HUD.NymphBackground.BARRIGHT;
    }

    static final class Bounds {
        float left;
        float top;
        float right;
        float bottom;

        Bounds(float left, float top, float right, float bottom) {
            this.left = left;
            this.top = top;
            this.right = right;
            this.bottom = bottom;
        }
    }
}
