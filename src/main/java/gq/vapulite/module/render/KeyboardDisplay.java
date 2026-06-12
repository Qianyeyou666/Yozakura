package gq.vapulite.module.render;

import gq.vapulite.module.ModuleType;
import gq.vapulite.module.Module;
import gq.vapulite.util.color.ColorUtils;
import gq.vapulite.util.render.HudDrag;
import gq.vapulite.value.Numbers;
import gq.vapulite.value.Option;
import gq.vapulite.engine.font.FontLoaders;
import gq.vapulite.engine.render.ui.RenderServices;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class KeyboardDisplay extends Module {
    private static final String FORWARD = "forward";
    private static final String LEFT = "left";
    private static final String BACK = "back";
    private static final String RIGHT = "right";
    private static final String JUMP = "jump";
    private static final String LMB = "lmb";
    private static final String RMB = "rmb";

    private final Option<Boolean> showMovement = new Option<Boolean>("Movement", "Movement", true);
    private final Option<Boolean> showSpace = new Option<Boolean>("Space", "Space", true);
    private final Option<Boolean> showMouse = new Option<Boolean>("Mouse", "Mouse", true);
    private final Option<Boolean> showCps = new Option<Boolean>("CPS", "CPS", true);
    private final Option<Boolean> vertical = new Option<Boolean>("Vertical", "Vertical", true);
    private final Numbers<Double> scale = new Numbers<Double>("Scale", "Scale", 1.0, 0.6, 1.8, 0.1);
    private final Numbers<Double> alpha = new Numbers<Double>("Alpha", "Alpha", 135.0, 40.0, 230.0, 5.0);
    private final Numbers<Double> xPosition = new Numbers<Double>("X", "X", 6.0, 0.0, 600.0, 1.0);
    private final Numbers<Double> bottomMargin = new Numbers<Double>("Bottom", "Bottom", 58.0, 0.0, 400.0, 1.0);
    private final Numbers<Double> yPosition = new Numbers<Double>("Y", "Y", -1.0, -1.0, 1200.0, 1.0);

    private final Map<String, Float> animations = new HashMap<String, Float>();
    private final List<Long> leftClicks = new ArrayList<Long>();
    private final List<Long> rightClicks = new ArrayList<Long>();
    private boolean previousLeft;
    private boolean previousRight;

    public KeyboardDisplay() {
        super("KeyboardDisplay", Keyboard.KEY_NONE, ModuleType.Render, "Show movement keys and mouse CPS on HUD");
        Chinese = "键盘显示";
        this.addValues(showMovement, showSpace, showMouse, showCps, vertical, scale, alpha, xPosition, yPosition,
                bottomMargin);
    }

    @SubscribeEvent
    public void onRender(RenderGameOverlayEvent.Text event) {
        if (!isInGame() || mc.currentScreen instanceof GuiMainMenu) {
            return;
        }

        updateInputState();

        float uiScale = Math.max(0.6f, scale.getValue().floatValue());
        float keySize = 24.0f * uiScale;
        float gap = 4.0f * uiScale;
        Layout layout = Boolean.TRUE.equals(vertical.getValue())
                ? buildVerticalLayout(keySize, gap)
                : buildHorizontalLayout(keySize, gap);

        if (layout.keys.isEmpty()) {
            return;
        }

        ScaledResolution sr = new ScaledResolution(mc);
        float defaultX = Math.min(xPosition.getValue().floatValue(), Math.max(0.0f, sr.getScaledWidth() - layout.width));
        float defaultY = sr.getScaledHeight() - layout.height - bottomMargin.getValue().floatValue();
        defaultY = Math.max(4.0f, Math.min(defaultY, sr.getScaledHeight() - layout.height - 4.0f));
        float[] pos = HudDrag.update("keyboard_display", xPosition, yPosition, scale, defaultX, defaultY,
                layout.width, layout.height, sr);
        float drawX = pos[0];
        float drawY = pos[1];

        int index = 0;
        for (KeyBox key : layout.keys) {
            drawKey(drawX + key.x, drawY + key.y, key.width, key.height, key.id, index++);
        }
        HudDrag.drawHint("keyboard_display", drawX, drawY, layout.width, layout.height,
                Math.max(2.0f, 5.0f * scale.getValue().floatValue()));
        HudDrag.handleScroll("keyboard_display", scale, drawX, drawY, layout.width, layout.height, 0.6f, 1.8f);
    }

    @Override
    public void disable() {
        animations.clear();
        leftClicks.clear();
        rightClicks.clear();
        previousLeft = false;
        previousRight = false;
    }

    private Layout buildVerticalLayout(float keySize, float gap) {
        ArrayList<KeyBox> keys = new ArrayList<KeyBox>();
        float totalWidth = keySize * 3.0f + gap * 2.0f;
        float y = 0.0f;

        if (Boolean.TRUE.equals(showMovement.getValue())) {
            keys.add(new KeyBox(FORWARD, keySize + gap, y, keySize, keySize));
            y += keySize + gap;
            keys.add(new KeyBox(LEFT, 0.0f, y, keySize, keySize));
            keys.add(new KeyBox(BACK, keySize + gap, y, keySize, keySize));
            keys.add(new KeyBox(RIGHT, (keySize + gap) * 2.0f, y, keySize, keySize));
            y += keySize + gap;
        }

        if (Boolean.TRUE.equals(showSpace.getValue())) {
            keys.add(new KeyBox(JUMP, 0.0f, y, totalWidth, keySize * 0.72f));
            y += keySize * 0.72f + gap;
        }

        if (Boolean.TRUE.equals(showMouse.getValue())) {
            float mouseWidth = (totalWidth - gap) / 2.0f;
            keys.add(new KeyBox(LMB, 0.0f, y, mouseWidth, keySize));
            keys.add(new KeyBox(RMB, mouseWidth + gap, y, mouseWidth, keySize));
            y += keySize + gap;
        }

        return new Layout(keys, totalWidth, Math.max(0.0f, y - gap));
    }

    private Layout buildHorizontalLayout(float keySize, float gap) {
        ArrayList<KeyBox> keys = new ArrayList<KeyBox>();
        float x = 0.0f;

        if (Boolean.TRUE.equals(showMovement.getValue())) {
            keys.add(new KeyBox(FORWARD, x, 0.0f, keySize, keySize));
            x += keySize + gap;
            keys.add(new KeyBox(LEFT, x, 0.0f, keySize, keySize));
            x += keySize + gap;
            keys.add(new KeyBox(BACK, x, 0.0f, keySize, keySize));
            x += keySize + gap;
            keys.add(new KeyBox(RIGHT, x, 0.0f, keySize, keySize));
            x += keySize + gap;
        }

        if (Boolean.TRUE.equals(showSpace.getValue())) {
            keys.add(new KeyBox(JUMP, x, 0.0f, keySize * 2.0f, keySize));
            x += keySize * 2.0f + gap;
        }

        if (Boolean.TRUE.equals(showMouse.getValue())) {
            keys.add(new KeyBox(LMB, x, 0.0f, keySize * 1.55f, keySize));
            x += keySize * 1.55f + gap;
            keys.add(new KeyBox(RMB, x, 0.0f, keySize * 1.55f, keySize));
            x += keySize * 1.55f + gap;
        }

        return new Layout(keys, Math.max(0.0f, x - gap), keySize);
    }

    private void drawKey(float x, float y, float width, float height, String id, int index) {
        float animation = animations.containsKey(id) ? animations.get(id) : 0.0f;
        int accent = ColorUtils.rainbow(220, 18, index);
        int baseAlpha = alpha.getValue().intValue();
        int background = ColorUtils.applyAlpha(0xFF10131A, Math.min(255, baseAlpha + Math.round(animation * 70.0f)));
        int border = ColorUtils.applyAlpha(ColorUtils.interpolate(0xFF5D6675, accent, animation), Math.min(210, 85 + Math.round(animation * 120.0f)));
        float round = Math.max(2.0f, 5.0f * scale.getValue().floatValue());

        if (HUD.useVapeSimpleStyle()) {
            drawVapeKey(x, y, width, height, id, animation, accent);
            return;
        }

        if (animation > 0.05f) {
            RenderServices.shapes().shadow(x, y, x + width, y + height, round, ColorUtils.applyAlpha(accent, Math.round(90.0f * animation)), 5, 3.0f);
        }
        RenderServices.shapes().roundedBorder(x, y, x + width, y + height, round, 1.0f, background, border);
        if (animation > 0.02f) {
            RenderServices.shapes().progressBar(x + 2.0f, y + height - 3.0f, x + width - 2.0f, y + height - 1.5f,
                    1.0f, animation, 0x00000000, ColorUtils.applyAlpha(accent, 210));
        }

        String label = getLabel(id);
        int textColor = ColorUtils.interpolate(0xFFC8D0DA, 0xFFFFFFFF, animation);
        if ((LMB.equals(id) || RMB.equals(id)) && Boolean.TRUE.equals(showCps.getValue())) {
            int cps = LMB.equals(id) ? leftClicks.size() : rightClicks.size();
            String cpsText = cps + " CPS";
            FontLoaders.C18.drawString(label, x + (width - FontLoaders.C18.getStringWidth(label)) / 2.0f, y + 6.0f, textColor);
            FontLoaders.C14.drawString(cpsText, x + (width - FontLoaders.C14.getStringWidth(cpsText)) / 2.0f, y + 17.0f, 0xFFC8D0DA);
        } else {
            FontLoaders.C18.drawString(label, x + (width - FontLoaders.C18.getStringWidth(label)) / 2.0f,
                    y + (height - FontLoaders.C18.getHeight()) / 2.0f + 3.0f, textColor);
        }
    }

    private void drawVapeKey(float x, float y, float width, float height, String id, float animation, int accent) {
        int baseAlpha = alpha.getValue().intValue();
        int fillAlpha = Math.min(220, baseAlpha + Math.round(animation * 42.0f));
        if (animation > 0.04f) {
            RenderServices.shapes().shadow(x, y, x + width, y + height, 2.0f,
                    ColorUtils.applyAlpha(accent, Math.round(44.0f * animation)), 4, 1.8f);
        }
        RenderServices.shapes().rect(x, y, x + width, y + height, ColorUtils.applyAlpha(0xFF050505, fillAlpha));
        RenderServices.shapes().horizontalGradient(x + 1.0f, y + 1.0f, x + width - 1.0f, y + Math.max(3.0f, height * 0.35f),
                ColorUtils.applyAlpha(0xFFFFFFFF, 14 + Math.round(animation * 12.0f)),
                ColorUtils.applyAlpha(0xFF000000, 0));
        RenderServices.shapes().borderedRect(x, y, x + width, y + height, 0.7f,
                ColorUtils.applyAlpha(ColorUtils.interpolate(0xFF343434, accent, animation), 84 + Math.round(animation * 70.0f)));
        RenderServices.shapes().rect(x, y + height - 2.0f, x + width, y + height,
                ColorUtils.applyAlpha(ColorUtils.interpolate(0xFF6F7680, accent, animation),
                        Math.min(235, 92 + Math.round(animation * 132.0f))));
        if (animation > 0.02f) {
            RenderServices.shapes().rect(x + 2.0f, y + height - 4.5f, x + 2.0f + (width - 4.0f) * animation,
                    y + height - 3.3f, ColorUtils.applyAlpha(accent, 185));
        }

        String label = getLabel(id);
        int textColor = ColorUtils.interpolate(0xFFD4D4D4, 0xFFFFFFFF, animation);
        if ((LMB.equals(id) || RMB.equals(id)) && Boolean.TRUE.equals(showCps.getValue())) {
            int cps = LMB.equals(id) ? leftClicks.size() : rightClicks.size();
            String cpsText = cps + " CPS";
            FontLoaders.C16.drawString(label, x + (width - FontLoaders.C16.getStringWidth(label)) / 2.0f,
                    y + 5.5f, textColor);
            FontLoaders.C12.drawString(cpsText, x + (width - FontLoaders.C12.getStringWidth(cpsText)) / 2.0f,
                    y + 16.5f, 0xFFD4D4D4);
        } else {
            FontLoaders.C16.drawString(label, x + (width - FontLoaders.C16.getStringWidth(label)) / 2.0f,
                    y + (height - FontLoaders.C16.getHeight()) / 2.0f + 3.0f, textColor);
        }
    }

    private void updateInputState() {
        boolean acceptInput = mc.currentScreen == null;
        setAnimated(FORWARD, acceptInput && isBindingDown(mc.gameSettings.keyBindForward.getKeyCode()));
        setAnimated(LEFT, acceptInput && isBindingDown(mc.gameSettings.keyBindLeft.getKeyCode()));
        setAnimated(BACK, acceptInput && isBindingDown(mc.gameSettings.keyBindBack.getKeyCode()));
        setAnimated(RIGHT, acceptInput && isBindingDown(mc.gameSettings.keyBindRight.getKeyCode()));
        setAnimated(JUMP, acceptInput && isBindingDown(mc.gameSettings.keyBindJump.getKeyCode()));

        boolean leftDown = acceptInput && Mouse.isButtonDown(0);
        boolean rightDown = acceptInput && Mouse.isButtonDown(1);
        setAnimated(LMB, leftDown);
        setAnimated(RMB, rightDown);

        long now = System.currentTimeMillis();
        if (leftDown && !previousLeft) {
            leftClicks.add(now);
        }
        if (rightDown && !previousRight) {
            rightClicks.add(now);
        }
        previousLeft = leftDown;
        previousRight = rightDown;
        trimClicks(leftClicks, now);
        trimClicks(rightClicks, now);
    }

    private void setAnimated(String id, boolean pressed) {
        float current = animations.containsKey(id) ? animations.get(id) : 0.0f;
        float target = pressed ? 1.0f : 0.0f;
        current += (target - current) * 0.28f;
        if (Math.abs(current - target) < 0.01f) {
            current = target;
        }
        animations.put(id, current);
    }

    private boolean isBindingDown(int keyCode) {
        if (keyCode == Keyboard.KEY_NONE) {
            return false;
        }
        if (keyCode < 0) {
            return Mouse.isButtonDown(keyCode + 100);
        }
        return Keyboard.isKeyDown(keyCode);
    }

    private String getLabel(String id) {
        if (FORWARD.equals(id)) {
            return getKeyName(mc.gameSettings.keyBindForward.getKeyCode(), "W");
        }
        if (LEFT.equals(id)) {
            return getKeyName(mc.gameSettings.keyBindLeft.getKeyCode(), "A");
        }
        if (BACK.equals(id)) {
            return getKeyName(mc.gameSettings.keyBindBack.getKeyCode(), "S");
        }
        if (RIGHT.equals(id)) {
            return getKeyName(mc.gameSettings.keyBindRight.getKeyCode(), "D");
        }
        if (JUMP.equals(id)) {
            return getKeyName(mc.gameSettings.keyBindJump.getKeyCode(), "SPACE");
        }
        if (LMB.equals(id)) {
            return "LMB";
        }
        if (RMB.equals(id)) {
            return "RMB";
        }
        return id;
    }

    private String getKeyName(int keyCode, String fallback) {
        if (keyCode < 0) {
            return "M" + (keyCode + 101);
        }
        String name = Keyboard.getKeyName(keyCode);
        return name == null ? fallback : name;
    }

    private void trimClicks(List<Long> clicks, long now) {
        for (int i = clicks.size() - 1; i >= 0; i--) {
            if (now - clicks.get(i) > 1000L) {
                clicks.remove(i);
            }
        }
    }

    private static class KeyBox {
        private final String id;
        private final float x;
        private final float y;
        private final float width;
        private final float height;

        private KeyBox(String id, float x, float y, float width, float height) {
            this.id = id;
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
        }
    }

    private static class Layout {
        private final ArrayList<KeyBox> keys;
        private final float width;
        private final float height;

        private Layout(ArrayList<KeyBox> keys, float width, float height) {
            this.keys = keys;
            this.width = width;
            this.height = height;
        }
    }
}
