package gq.yozakura.util.render;

import gq.yozakura.value.Numbers;

/**
 * Stable named HUD drag handle. A renderer may submit different geometry every frame, while the
 * handle keeps the gesture identity and persistence bindings independent from render branches.
 */
public final class HudDragging {
    private final String id;
    private final float initialX;
    private final float initialY;
    private Numbers<Double> xValue;
    private Numbers<Double> yValue;
    private float x;
    private float y;
    private float width;
    private float height;
    private float hoverProgress;
    private long lastHoverNanos;

    HudDragging(String id, float initialX, float initialY) {
        if (id == null || id.length() == 0) {
            throw new IllegalArgumentException("id must not be empty");
        }
        this.id = id;
        this.initialX = initialX;
        this.initialY = initialY;
        this.x = initialX;
        this.y = initialY;
    }

    public String getId() {
        return id;
    }

    public float getInitialX() {
        return initialX;
    }

    public float getInitialY() {
        return initialY;
    }

    public float getX() {
        return x;
    }

    public float getY() {
        return y;
    }

    public float getWidth() {
        return width;
    }

    public float getHeight() {
        return height;
    }

    void bind(Numbers<Double> xValue, Numbers<Double> yValue) {
        this.xValue = xValue;
        this.yValue = yValue;
    }

    Numbers<Double> getXValue() {
        return xValue;
    }

    Numbers<Double> getYValue() {
        return yValue;
    }

    void setPosition(float x, float y) {
        this.x = x;
        this.y = y;
    }

    void setBounds(float width, float height) {
        this.width = Math.max(0.0F, width);
        this.height = Math.max(0.0F, height);
    }

    float updateHoverProgress(boolean hovered, long nowNanos) {
        if (lastHoverNanos == 0L) {
            lastHoverNanos = nowNanos;
        }
        float delta = Math.min(0.05F, Math.max(0.0F, (nowNanos - lastHoverNanos) / 1000000000.0F));
        lastHoverNanos = nowNanos;
        float target = hovered ? 1.0F : 0.0F;
        float step = delta / 0.25F;
        if (hoverProgress < target) {
            hoverProgress = Math.min(target, hoverProgress + step);
        } else if (hoverProgress > target) {
            hoverProgress = Math.max(target, hoverProgress - step);
        }
        return decelerate(hoverProgress);
    }

    private static float decelerate(float progress) {
        float inverse = 1.0F - Math.max(0.0F, Math.min(1.0F, progress));
        return 1.0F - inverse * inverse;
    }
}
