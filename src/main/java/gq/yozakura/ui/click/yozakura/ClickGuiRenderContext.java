package gq.yozakura.ui.click.yozakura;

import gq.yozakura.engine.render.GLStateManager;

/** Maps design-space drawing bounds to Minecraft screen-space clipping bounds. */
public final class ClickGuiRenderContext {
    private static final ClickGuiRenderContext ACTIVE = new ClickGuiRenderContext();

    private float originX;
    private float originY;
    private float scale = 1f;

    public void configure(float originX, float originY, float scale) {
        this.originX = originX;
        this.originY = originY;
        this.scale = Math.max(0.0001f, scale);
    }

    public float screenX(float designX) { return originX + designX * scale; }
    public float screenY(float designY) { return originY + designY * scale; }
    public float screenWidth(float designWidth) { return designWidth * scale; }
    public float screenHeight(float designHeight) { return designHeight * scale; }

    public static void activate(float originX, float originY, float scale) {
        ACTIVE.configure(originX, originY, scale);
    }

    public static void pushScissor(float x, float y, float width, float height) {
        GLStateManager.pushScissor(ACTIVE.screenX(x), ACTIVE.screenY(y),
                ACTIVE.screenWidth(width), ACTIVE.screenHeight(height));
    }

    public static void popScissor() {
        GLStateManager.popScissor();
    }
}
