package gq.vapulite.ui.click.material;

import gq.vapulite.module.render.ClickGUI;
import net.minecraft.client.gui.ScaledResolution;

/**
 * 新 ClickGUI 的布局快照。
 *
 * <p>所有区域坐标都在 Minecraft 缩放后的 GUI 坐标系内，避免渲染和点击命中
 * 使用两套坐标导致错位。</p>
 */
final class MaterialClickLayout {
    static final float BASE_W = 860.0f;
    static final float BASE_H = 540.0f;
    private static final float MAX_SCALE = 0.62f;

    final float scale;
    final float x;
    final float y;
    final float w;
    final float h;
    final float sidebarW;
    final float radius;
    final float contentX;
    final float contentY;
    final float contentW;
    final float gridX;
    final float gridY;
    final float gridW;
    final float gridH;

    private MaterialClickLayout(float scale, float x, float y, float w, float h) {
        this.scale = scale;
        this.x = x;
        this.y = y;
        this.w = w;
        this.h = h;
        this.sidebarW = 200.0f * scale;
        this.radius = 28.0f * scale;
        this.contentX = x + sidebarW + 40.0f * scale;
        this.contentY = y + 36.0f * scale;
        this.contentW = w - sidebarW - 80.0f * scale;
        this.gridX = contentX;
        this.gridY = contentY + 58.0f * scale;
        this.gridW = contentW;
        this.gridH = y + h - 34.0f * scale - gridY;
    }

    static MaterialClickLayout calculate(ScaledResolution sr) {
        float availableW = Math.max(320.0f, sr.getScaledWidth() - 24.0f);
        float availableH = Math.max(240.0f, sr.getScaledHeight() - 24.0f);
        float scale = Math.min(MAX_SCALE, Math.min(availableW / BASE_W, availableH / BASE_H));
        float w = BASE_W * scale;
        float h = BASE_H * scale;

        float savedX = ClickGUI.windowX.getValue().floatValue();
        float savedY = ClickGUI.windowY.getValue().floatValue();
        float x = savedX >= 0.0f ? savedX : (sr.getScaledWidth() - w) / 2.0f;
        float y = savedY >= 0.0f ? savedY : (sr.getScaledHeight() - h) / 2.0f;

        x = clamp(x, 8.0f, Math.max(8.0f, sr.getScaledWidth() - w - 8.0f));
        y = clamp(y, 8.0f, Math.max(8.0f, sr.getScaledHeight() - h - 8.0f));
        return new MaterialClickLayout(scale, x, y, w, h);
    }

    boolean inWindow(float mouseX, float mouseY) {
        return contains(x, y, x + w, y + h, mouseX, mouseY);
    }

    boolean inDragHeader(float mouseX, float mouseY) {
        return contains(x, y, x + w, y + 64.0f * scale, mouseX, mouseY);
    }

    boolean inGrid(float mouseX, float mouseY) {
        return contains(gridX, gridY, gridX + gridW, gridY + gridH, mouseX, mouseY);
    }

    static boolean contains(float x1, float y1, float x2, float y2, float mouseX, float mouseY) {
        return mouseX >= x1 && mouseX <= x2 && mouseY >= y1 && mouseY <= y2;
    }

    static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}
