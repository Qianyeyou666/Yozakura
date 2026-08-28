package gq.yozakura.ui.engine.paint;

/** Paint-only retained transform used by interactive transitions without relayout. */
public final class PaintVisualState {
    public static final PaintVisualState IDENTITY = new PaintVisualState(0.0F, 0.0F, 1.0F);

    private final float translateX;
    private final float translateY;
    private final float opacity;

    public PaintVisualState(float translateX, float translateY, float opacity) {
        this.translateX = translateX;
        this.translateY = translateY;
        this.opacity = Math.max(0.0F, Math.min(1.0F, opacity));
    }

    public float translateX() { return translateX; }
    public float translateY() { return translateY; }
    public float opacity() { return opacity; }
}
