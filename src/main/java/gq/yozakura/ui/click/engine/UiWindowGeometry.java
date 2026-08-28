package gq.yozakura.ui.click.engine;

/** Aspect-preserving drag/resize geometry clamped to the Minecraft scaled viewport. */
final class UiWindowGeometry {
    private final float designWidth;
    private final float designHeight;
    private float x;
    private float y;
    private float scale = 1.0F;
    private int viewportWidth;
    private int viewportHeight;
    private Interaction interaction = Interaction.NONE;
    private float startPointerX;
    private float startPointerY;
    private float startX;
    private float startY;
    private float startScale;
    private boolean initialized;

    UiWindowGeometry(float designWidth, float designHeight) {
        this.designWidth = designWidth;
        this.designHeight = designHeight;
    }

    void updateViewport(int width, int height) {
        updateViewport(width, height, 1.0F);
    }

    void updateViewport(int width, int height, float guiScaleFactor) {
        viewportWidth = width;
        viewportHeight = height;
        float maximum = maximumScale();
        if (!initialized) {
            float authoredScale = 1.0F / Math.max(1.0F, guiScaleFactor);
            scale = Math.min(authoredScale, maximum);
            x = (width - designWidth * scale) * 0.5F;
            y = (height - designHeight * scale) * 0.5F;
            initialized = true;
        } else {
            scale = Math.min(scale, maximum);
            clampPosition();
        }
    }

    void beginMove(float pointerX, float pointerY) {
        begin(Interaction.MOVE, pointerX, pointerY);
    }

    void beginResize(float pointerX, float pointerY) {
        begin(Interaction.RESIZE, pointerX, pointerY);
    }

    void updatePointer(float pointerX, float pointerY) {
        if (interaction == Interaction.MOVE) {
            x = startX + pointerX - startPointerX;
            y = startY + pointerY - startPointerY;
            clampPosition();
        } else if (interaction == Interaction.RESIZE) {
            float dxScale = (pointerX - startPointerX) / designWidth;
            float dyScale = (pointerY - startPointerY) / designHeight;
            scale = clamp(startScale + Math.max(dxScale, dyScale), 0.30F, maximumScale());
            clampPosition();
        }
    }

    void endInteraction() { interaction = Interaction.NONE; }
    boolean isInteracting() { return interaction != Interaction.NONE; }
    float x() { return x; }
    float y() { return y; }
    float scale() { return scale; }
    boolean contains(float px, float py) {
        return px >= x && py >= y && px <= x + designWidth * scale
                && py <= y + designHeight * scale;
    }

    private void begin(Interaction next, float pointerX, float pointerY) {
        interaction = next;
        startPointerX = pointerX;
        startPointerY = pointerY;
        startX = x;
        startY = y;
        startScale = scale;
    }

    private float maximumScale() {
        if (viewportWidth <= 0 || viewportHeight <= 0) return 1.0F;
        return Math.max(0.30F, Math.min((viewportWidth - 12.0F) / designWidth,
                (viewportHeight - 12.0F) / designHeight));
    }

    private void clampPosition() {
        float width = designWidth * scale;
        float height = designHeight * scale;
        x = clamp(x, 6.0F, Math.max(6.0F, viewportWidth - width - 6.0F));
        y = clamp(y, 6.0F, Math.max(6.0F, viewportHeight - height - 6.0F));
    }

    private static float clamp(float value, float minimum, float maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private enum Interaction { NONE, MOVE, RESIZE }
}
