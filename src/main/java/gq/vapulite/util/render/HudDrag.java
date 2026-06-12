package gq.vapulite.util.render;

import gq.vapulite.value.Numbers;
import gq.vapulite.ui.click.material.MaterialClickGui;
import gq.vapulite.ui.click.sakura.SakuraClickGui;
import gq.vapulite.ui.click.vape.VapeClickGui;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.client.gui.ScaledResolution;
import org.lwjgl.input.Mouse;

public final class HudDrag {
    private static final Minecraft MC = Minecraft.getMinecraft();
    private static String activeId;
    private static String selectedId;
    private static float dragOffsetX;
    private static float dragOffsetY;
    private HudDrag() {
    }

    public static boolean isEditMode() {
        return MC.currentScreen != null && !(MC.currentScreen instanceof GuiMainMenu);
    }

    public static float[] update(String id, Numbers<Double> xValue, Numbers<Double> yValue,
                                 float defaultX, float defaultY, float width, float height,
                                 ScaledResolution sr) {
        return update(id, xValue, yValue, null, defaultX, defaultY, width, height, sr);
    }

    public static float[] update(String id, Numbers<Double> xValue, Numbers<Double> yValue,
                                 Numbers<Double> scaleValue, float defaultX, float defaultY,
                                 float width, float height, ScaledResolution sr) {
        float x = resolvePosition(xValue, defaultX);
        float y = resolvePosition(yValue, defaultY);
        x = clamp(x, 2.0f, Math.max(2.0f, sr.getScaledWidth() - width - 2.0f));
        y = clamp(y, 2.0f, Math.max(2.0f, sr.getScaledHeight() - height - 2.0f));

        if (!isEditMode() || isClickGuiOpen()) {
            if (!Mouse.isButtonDown(0)) {
                activeId = null;
                selectedId = null;
            }
            if (isClickGuiOpen()) {
                activeId = null;
                selectedId = null;
            }
            return new float[]{x, y};
        }

        boolean leftDown = Mouse.isButtonDown(0);
        int mouseX = mouseX(sr);
        int mouseY = mouseY(sr);
        boolean hovered = isHovered(mouseX, mouseY, x, y, width, height);

        if (!leftDown) {
            activeId = null;
        } else if (activeId == null && hovered) {
            activeId = id;
            selectedId = id;
            dragOffsetX = mouseX - x;
            dragOffsetY = mouseY - y;
        }

        if (id.equals(activeId)) {
            x = clamp(mouseX - dragOffsetX, 2.0f, Math.max(2.0f, sr.getScaledWidth() - width - 2.0f));
            y = clamp(mouseY - dragOffsetY, 2.0f, Math.max(2.0f, sr.getScaledHeight() - height - 2.0f));
            setNumber(xValue, x);
            setNumber(yValue, y);
        }

        return new float[]{x, y};
    }

    public static void drawHint(String id, float x, float y, float width, float height, float radius) {
        if (!isEditMode() || isClickGuiOpen()) {
            return;
        }
        ScaledResolution sr = new ScaledResolution(MC);
        boolean hovered = isHovered(mouseX(sr), mouseY(sr), x, y, width, height);
        boolean active = id.equals(activeId);
        boolean selected = id.equals(selectedId);
        if (!hovered && !active && !selected) {
            return;
        }
        int color = active ? 0xC870C1DC : selected ? 0xB88B7CFF : 0x8870C1DC;
        RenderUtil.drawRoundedBorderedRect(x - 1.0f, y - 1.0f, x + width + 1.0f, y + height + 1.0f,
                Math.max(2.0f, radius + 1.0f), 1.0f, 0x00000000, color);
        RenderUtil.drawRoundedRect(x + width / 2.0f - 12.0f, y + 4.0f,
                x + width / 2.0f + 12.0f, y + 6.0f, 1.0f, color);
        RenderUtil.drawRoundedRect(x + width - 9.0f, y + height - 9.0f,
                x + width - 3.0f, y + height - 7.6f, 0.8f, color);
        RenderUtil.drawRoundedRect(x + width - 7.6f, y + height - 7.6f,
                x + width - 3.0f, y + height - 6.2f, 0.8f, color);
    }

    public static int mouseX(ScaledResolution sr) {
        return Mouse.getX() * sr.getScaledWidth() / Math.max(1, MC.displayWidth);
    }

    public static int mouseY(ScaledResolution sr) {
        return sr.getScaledHeight() - Mouse.getY() * sr.getScaledHeight() / Math.max(1, MC.displayHeight) - 1;
    }

    public static boolean isDragging(String id) {
        return id != null && id.equals(activeId);
    }

    public static boolean isSelected(String id) {
        return id != null && id.equals(selectedId);
    }

    /**
     * 在编辑模式下，当鼠标悬停在 HUD 元素上时，通过滚轮调整缩放值。
     * 步进自动读取 scaleValue 的 increment。
     */
    public static void handleScroll(String id, Numbers<Double> scaleValue,
                                    float x, float y, float width, float height,
                                    float minScale, float maxScale) {
        if (!isEditMode() || isClickGuiOpen() || scaleValue == null) {
            return;
        }
        ScaledResolution sr = new ScaledResolution(MC);
        if (!isHovered(mouseX(sr), mouseY(sr), x, y, width, height)) {
            return;
        }
        int wheel = Mouse.getDWheel();
        if (wheel == 0) {
            return;
        }
        double current = scaleValue.getValue();
        double step = scaleValue.getIncrement().doubleValue();
        double delta = wheel > 0 ? step : -step;
        double next = Math.max(minScale, Math.min(maxScale, current + delta));
        next = Math.round(next * 100.0) / 100.0;
        if (Math.abs(next - current) > 0.0001) {
            scaleValue.setValue(next);
        }
    }

    private static boolean isClickGuiOpen() {
        return MC.currentScreen instanceof VapeClickGui
                || MC.currentScreen instanceof MaterialClickGui
                || MC.currentScreen instanceof SakuraClickGui;
    }

    private static float resolvePosition(Numbers<Double> value, float fallback) {
        if (value == null || value.getValue() == null || value.getValue() < 0.0D) {
            return fallback;
        }
        return value.getValue().floatValue();
    }

    private static void setNumber(Numbers<Double> value, float position) {
        if (value == null) {
            return;
        }
        double rounded = Math.round(position * 10.0f) / 10.0D;
        if (Math.abs(value.getValue() - rounded) > 0.04D) {
            value.setValue(rounded);
        }
    }

    private static boolean isHovered(float mouseX, float mouseY, float x, float y, float width, float height) {
        return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}
