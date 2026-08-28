package gq.yozakura.ui.click.qml;

/** Coordinate-safe drag and aspect-ratio resize state for the offscreen QML texture. */
final class QmlWindowGeometry {
    private final float logicalWidth;
    private final float logicalHeight;
    private int viewportWidth = -1;
    private int viewportHeight = -1;
    private int guiScale = -1;
    private float x;
    private float y;
    private float scale = 1.0F;
    private float minScale = 0.1F;
    private float maxScale = 1.0F;
    private float pressMouseX;
    private float pressMouseY;
    private float pressX;
    private float pressY;
    private float pressScale;
    private boolean moving;
    private boolean resizing;

    QmlWindowGeometry(float logicalWidth, float logicalHeight) {
        this.logicalWidth = logicalWidth;
        this.logicalHeight = logicalHeight;
    }

    void updateViewport(int width, int height, int guiScale) {
        int safeGuiScale = Math.max(1, guiScale);
        if (width == viewportWidth && height == viewportHeight && safeGuiScale == this.guiScale) {
            return;
        }
        viewportWidth = width;
        viewportHeight = height;
        this.guiScale = safeGuiScale;
        float physicalPixelScale = 1.0F / safeGuiScale;
        float fitScale = Math.min(width * 0.94F / logicalWidth, height * 0.94F / logicalHeight);
        maxScale = Math.max(0.1F, Math.min(physicalPixelScale, fitScale));
        minScale = Math.max(0.1F, maxScale * 0.5F);
        scale = maxScale;
        x = (width - renderedWidth()) * 0.5F;
        y = (height - renderedHeight()) * 0.5F;
        moving = false;
        resizing = false;
    }

    void beginMove(float mouseX, float mouseY) {
        moving = true;
        resizing = false;
        rememberPress(mouseX, mouseY);
    }

    void beginResize(float mouseX, float mouseY) {
        resizing = true;
        moving = false;
        rememberPress(mouseX, mouseY);
    }

    void updatePointer(float mouseX, float mouseY) {
        if (moving) {
            x = pressX + mouseX - pressMouseX;
            y = pressY + mouseY - pressMouseY;
            clampPosition();
        } else if (resizing) {
            float widthDelta = (mouseX - pressMouseX) / logicalWidth;
            float heightDelta = (mouseY - pressMouseY) / logicalHeight;
            scale = clamp(pressScale + (widthDelta + heightDelta) * 0.5F, minScale, maxScale);
            clampPosition();
        }
    }

    void endInteraction() {
        moving = false;
        resizing = false;
    }

    boolean isInteracting() {
        return moving || resizing;
    }

    float toLocalX(float mouseX) {
        return (mouseX - x) / scale;
    }

    float toLocalY(float mouseY) {
        return (mouseY - y) / scale;
    }

    boolean contains(float mouseX, float mouseY) {
        return mouseX >= x && mouseY >= y
                && mouseX <= x + renderedWidth() && mouseY <= y + renderedHeight();
    }

    float x() {
        return x;
    }

    float y() {
        return y;
    }

    float scale() {
        return scale;
    }

    float renderedWidth() {
        return logicalWidth * scale;
    }

    float renderedHeight() {
        return logicalHeight * scale;
    }

    private void rememberPress(float mouseX, float mouseY) {
        pressMouseX = mouseX;
        pressMouseY = mouseY;
        pressX = x;
        pressY = y;
        pressScale = scale;
    }

    private void clampPosition() {
        x = clamp(x, 0.0F, Math.max(0.0F, viewportWidth - renderedWidth()));
        y = clamp(y, 0.0F, Math.max(0.0F, viewportHeight - renderedHeight()));
    }

    private static float clamp(float value, float minimum, float maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }
}
