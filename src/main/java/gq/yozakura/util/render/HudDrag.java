package gq.yozakura.util.render;

import gq.yozakura.engine.render.ui.VisualPalette;
import gq.yozakura.value.Numbers;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiChat;
import net.minecraft.client.gui.ScaledResolution;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

public final class HudDrag {
    private static final Minecraft MC = Minecraft.getMinecraft();
    private static final HudDragSession SESSION = new HudDragSession();
    private static final VisualPalette PALETTE = VisualPalette.nightBloom();
    private static String selectedId;
    private static ActiveBinding activeBinding;

    private HudDrag() {
    }

    public static boolean isEditMode() {
        return MC.currentScreen instanceof GuiChat;
    }

    public static float[] update(String id, Numbers<Double> xValue, Numbers<Double> yValue,
                                 float defaultX, float defaultY, float width, float height,
                                 ScaledResolution sr) {
        return update(id, xValue, yValue, null, defaultX, defaultY, width, height, sr);
    }

    public static float[] update(String id, Numbers<Double> xValue, Numbers<Double> yValue,
                                 Numbers<Double> scaleValue, float defaultX, float defaultY,
                                 float width, float height, ScaledResolution sr) {
        HudDragSession.Bounds bounds = new HudDragSession.Bounds(sr.getScaledWidth(), sr.getScaledHeight(), width, height);
        HudDragSession.Position defaultPosition = HudDragSession.clampToBounds(
                new HudDragSession.Position(resolvePosition(xValue, defaultX), resolvePosition(yValue, defaultY)), bounds);

        if (!isEditMode()) {
            cancelSession();
            selectedId = null;
            return asArray(defaultPosition);
        }

        boolean leftDown = Mouse.isButtonDown(0);
        boolean escapeDown = Keyboard.isKeyDown(Keyboard.KEY_ESCAPE);
        float mouseX = logicalMouseX(sr);
        float mouseY = logicalMouseY(sr);
        settleReleasedSession();
        if (SESSION.isTracking(SESSION.getActiveId())) {
            if (escapeDown) {
                finishSession(true);
            } else if (!leftDown) {
                finishSession(false);
            }
        }

        HudDragSession.Position position = defaultPosition;
        if (SESSION.isTracking(id)) {
            position = SESSION.drag(mouseX, mouseY, bounds).getPosition();
        } else if (SESSION.getState() == HudDragSession.DragState.IDLE && !escapeDown && leftDown
                && isHovered(mouseX, mouseY, defaultPosition.getX(), defaultPosition.getY(), width, height)) {
            SESSION.arm(id, defaultPosition, mouseX, mouseY);
            activeBinding = new ActiveBinding(id, xValue, yValue);
            selectedId = id;
            position = defaultPosition;
        }

        return asArray(position);
    }

    public static void drawHint(String id, float x, float y, float width, float height, float radius) {
        if (!isEditMode()) {
            return;
        }
        ScaledResolution sr = new ScaledResolution(MC);
        boolean hovered = isHovered(logicalMouseX(sr), logicalMouseY(sr), x, y, width, height);
        boolean active = SESSION.isTracking(id);
        boolean selected = id.equals(selectedId);
        boolean releasing = SESSION.isReleaseFor(id);
        if (!hovered && !active && !selected && !releasing) {
            return;
        }
        HudDragSession.DragState state = active || releasing ? SESSION.getState() : HudDragSession.DragState.IDLE;
        int color = state == HudDragSession.DragState.SNAP_PREVIEW
                ? withAlpha(PALETTE.getBorderFocus(), 0xE8)
                : active ? withAlpha(PALETTE.getAccentPrimary(), 0xC8)
                : selected ? withAlpha(PALETTE.getAccentAlt(), 0xB8)
                : withAlpha(PALETTE.getAccentPrimary(), 0x88);
        if (state == HudDragSession.DragState.SNAP_PREVIEW) {
            drawSnapGuides(sr, SESSION.getPreview());
        }
        RenderUtil.drawRoundedBorderedRect(x - 1.0f, y - 1.0f, x + width + 1.0f, y + height + 1.0f,
                Math.max(2.0f, radius + 1.0f), 1.0f, 0x00000000, color);
        RenderUtil.drawRoundedRect(x + width / 2.0f - 12.0f, y + 4.0f,
                x + width / 2.0f + 12.0f, y + 6.0f, 1.0f, color);
    }

    public static int mouseX(ScaledResolution sr) {
        return (int) logicalMouseX(sr);
    }

    public static int mouseY(ScaledResolution sr) {
        return (int) logicalMouseY(sr);
    }

    public static boolean isDragging(String id) {
        return SESSION.isTracking(id);
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
        if (!isEditMode() || scaleValue == null) {
            return;
        }
        ScaledResolution sr = new ScaledResolution(MC);
        if (!isHovered(logicalMouseX(sr), logicalMouseY(sr), x, y, width, height)) {
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
        if (value.getValue() == null || Math.abs(value.getValue() - rounded) > 0.0001D) {
            value.setNumberValue(rounded);
        }
    }

    private static void finishSession(boolean cancelled) {
        HudDragSession.Completion completion = cancelled ? SESSION.cancel() : SESSION.release();
        if (activeBinding == null || completion.getPosition() == null) {
            return;
        }
        if (cancelled || completion.shouldPersist()) {
            setNumber(activeBinding.xValue, completion.getPosition().getX());
            setNumber(activeBinding.yValue, completion.getPosition().getY());
        }
    }

    private static void cancelSession() {
        if (SESSION.isTracking(SESSION.getActiveId())) {
            finishSession(true);
        }
        SESSION.acknowledgeRelease();
        activeBinding = null;
    }

    private static void settleReleasedSession() {
        if (SESSION.getState() != HudDragSession.DragState.RELEASE) {
            return;
        }
        SESSION.acknowledgeRelease();
        activeBinding = null;
    }

    private static float[] asArray(HudDragSession.Position position) {
        return new float[]{position.getX(), position.getY()};
    }

    private static float logicalMouseX(ScaledResolution sr) {
        return HudDragSession.toLogicalCoordinate(Mouse.getX(), MC.displayWidth, sr.getScaledWidth());
    }

    private static float logicalMouseY(ScaledResolution sr) {
        return HudDragSession.toLogicalYFromBottom(Mouse.getY(), MC.displayHeight, sr.getScaledHeight());
    }

    private static void drawSnapGuides(ScaledResolution sr, HudDragSession.Preview preview) {
        int guideColor = withAlpha(PALETTE.getAccentAlt(), 0x9C);
        if (preview.getHorizontalSnap() != HudDragSession.SnapTarget.NONE) {
            float guideX = snapGuideX(sr.getScaledWidth(), preview.getHorizontalSnap());
            RenderUtil.drawRect(guideX - 0.5F, 2.0F, guideX + 0.5F, sr.getScaledHeight() - 2.0F, guideColor);
        }
        if (preview.getVerticalSnap() != HudDragSession.SnapTarget.NONE) {
            float guideY = snapGuideY(sr.getScaledHeight(), preview.getVerticalSnap());
            RenderUtil.drawRect(2.0F, guideY - 0.5F, sr.getScaledWidth() - 2.0F, guideY + 0.5F, guideColor);
        }
    }

    private static float snapGuideX(float screenWidth, HudDragSession.SnapTarget target) {
        if (target == HudDragSession.SnapTarget.CENTER) {
            return screenWidth * 0.5F;
        }
        return target == HudDragSession.SnapTarget.END ? screenWidth - HudDragSession.SAFE_MARGIN
                : HudDragSession.SAFE_MARGIN;
    }

    private static float snapGuideY(float screenHeight, HudDragSession.SnapTarget target) {
        if (target == HudDragSession.SnapTarget.CENTER) {
            return screenHeight * 0.5F;
        }
        return target == HudDragSession.SnapTarget.END ? screenHeight - HudDragSession.SAFE_MARGIN
                : HudDragSession.SAFE_MARGIN;
    }

    private static boolean isHovered(float mouseX, float mouseY, float x, float y, float width, float height) {
        return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
    }

    private static int withAlpha(int color, int alpha) {
        return (color & 0x00FFFFFF) | (Math.max(0, Math.min(255, alpha)) << 24);
    }

    private static final class ActiveBinding {
        private final String id;
        private final Numbers<Double> xValue;
        private final Numbers<Double> yValue;

        private ActiveBinding(String id, Numbers<Double> xValue, Numbers<Double> yValue) {
            this.id = id;
            this.xValue = xValue;
            this.yValue = yValue;
        }
    }
}
