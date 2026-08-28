package gq.yozakura.ui.click.yozakura;

/** Pure window geometry shared by rendering and hit testing. */
public final class ClickGuiWindowGeometry {
    public static final float DESIGN_WIDTH = 960f;
    public static final float DESIGN_HEIGHT = 640f;
    public static final float VIEWPORT_MARGIN = 16f;

    private float x;
    private float y;
    private float scale = 1f;
    private boolean dragging;
    private float dragOffsetX;
    private float dragOffsetY;

    public void resize(float screenWidth, float screenHeight, float savedX, float savedY) {
        resize(screenWidth, screenHeight, 1f, VIEWPORT_MARGIN, savedX, savedY);
    }

    public void resizeForGui(float screenWidth, float screenHeight, float guiScaleFactor,
                             float savedX, float savedY) {
        float factor = Math.max(1f, guiScaleFactor);
        resize(screenWidth, screenHeight, 1f / factor, VIEWPORT_MARGIN / factor, savedX, savedY);
    }

    private void resize(float screenWidth, float screenHeight, float maximumScale, float margin,
                        float savedX, float savedY) {
        float usableWidth = Math.max(1f, screenWidth - margin * 2f);
        float usableHeight = Math.max(1f, screenHeight - margin * 2f);
        scale = Math.min(maximumScale,
                Math.min(usableWidth / DESIGN_WIDTH, usableHeight / DESIGN_HEIGHT));

        if (savedX < 0f || savedY < 0f) {
            x = (screenWidth - width()) / 2f;
            y = (screenHeight - height()) / 2f;
        } else {
            x = savedX;
            y = savedY;
        }
        clamp(screenWidth, screenHeight);
    }

    public boolean beginDrag(float mouseX, float mouseY) {
        if (!contains(mouseX, mouseY) || localY(mouseY) > ClickGuiTheme.TITLEBAR_H) {
            return false;
        }
        dragging = true;
        dragOffsetX = mouseX - x;
        dragOffsetY = mouseY - y;
        return true;
    }

    public void dragTo(float mouseX, float mouseY, float screenWidth, float screenHeight) {
        if (!dragging) {
            return;
        }
        x = mouseX - dragOffsetX;
        y = mouseY - dragOffsetY;
        clamp(screenWidth, screenHeight);
    }

    public void endDrag() {
        dragging = false;
    }

    private void clamp(float screenWidth, float screenHeight) {
        x = clamp(x, 0f, Math.max(0f, screenWidth - width()));
        y = clamp(y, 0f, Math.max(0f, screenHeight - height()));
    }

    private static float clamp(float value, float minimum, float maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    public boolean contains(float mouseX, float mouseY) {
        return mouseX >= x && mouseX <= x + width() && mouseY >= y && mouseY <= y + height();
    }

    public float localX(float screenX) { return (screenX - x) / scale; }
    public float localY(float screenY) { return (screenY - y) / scale; }
    public float x() { return x; }
    public float y() { return y; }
    public float scale() { return scale; }
    public float width() { return DESIGN_WIDTH * scale; }
    public float height() { return DESIGN_HEIGHT * scale; }
    public boolean isDragging() { return dragging; }
}
