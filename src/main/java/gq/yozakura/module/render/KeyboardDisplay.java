package gq.yozakura.module.render;

import gq.yozakura.bridge.YozakuraEventBridge;
import gq.yozakura.event.bridge.Render2DEvent;
import gq.yozakura.event.bus.EventTarget;
import gq.yozakura.module.ModuleType;
import gq.yozakura.module.Module;
import gq.yozakura.util.color.ColorUtils;
import gq.yozakura.util.render.HudDrag;
import gq.yozakura.value.Numbers;
import gq.yozakura.value.Option;
import gq.yozakura.engine.font.FontLoaders;
import gq.yozakura.engine.render.ui.RenderServices;
import gq.yozakura.engine.render.ui.VisualPalette;
import gq.yozakura.util.animation.UiClock;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class KeyboardDisplay extends Module {
    private static final VisualPalette NIGHT_BLOOM = VisualPalette.nightBloom();
    private static final float NIGHT_BLOOM_RADIUS = 4.0F;
    private static final int NIGHT_BLOOM_SURFACE = 0xDC16161A;
    private static final int NIGHT_BLOOM_PRIMARY = 0xFFFF4FC7;

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
    private final Map<String, NightBloomKeyFeedback> nightBloomFeedback = new HashMap<String, NightBloomKeyFeedback>();
    private final List<Long> leftClicks = new ArrayList<Long>();
    private final List<Long> rightClicks = new ArrayList<Long>();
    private final Layout layoutScratch = new Layout();
    private final UiClock nightBloomClock = new UiClock();
    private boolean previousLeft;
    private boolean previousRight;

    public KeyboardDisplay() {
        super("KeyboardDisplay", Keyboard.KEY_NONE, ModuleType.Render, "Show movement keys and mouse CPS on HUD");
        Chinese = "键盘显示";
        this.addValues(showMovement, showSpace, showMouse, showCps, vertical, scale, alpha, xPosition, yPosition,
                bottomMargin);
    }

    @EventTarget
    public void onRender(Render2DEvent event) {
        renderOverlay();
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onRender(RenderGameOverlayEvent.Text event) {
        if (!YozakuraEventBridge.hasRenderedOverlayThisFrame()) {
            renderOverlay();
        }
    }

    private void renderOverlay() {
        if (!isInGame() || mc.currentScreen instanceof GuiMainMenu) {
            return;
        }

        updateInputState();

        float uiScale = Math.max(0.6f, scale.getValue().floatValue());
        float keySize = 24.0f * uiScale;
        float gap = 4.0f * uiScale;
        Layout layout = layoutScratch;
        if (Boolean.TRUE.equals(vertical.getValue())) {
            buildVerticalLayout(layout, keySize, gap);
        } else {
            buildHorizontalLayout(layout, keySize, gap);
        }

        if (layout.keys.isEmpty()) {
            return;
        }

        ScaledResolution sr = new ScaledResolution(mc);
        float defaultX = Math.min(xPosition.getValue().floatValue(), Math.max(0.0f, sr.getScaledWidth() - layout.width));
        float defaultY = sr.getScaledHeight() - layout.height - bottomMargin.getValue().floatValue();
        defaultY = Math.max(4.0f, Math.min(defaultY, sr.getScaledHeight() - layout.height - 4.0f));
        boolean nightBloom = HUD.getActiveStyle() == HUD.HudStyle.NIGHT_BLOOM;
        float[] pos = nightBloom
                ? HudDrag.updateDocked("keyboard_display", xPosition, yPosition, scale, defaultX, defaultY,
                layout.width, layout.height, NIGHT_BLOOM_RADIUS, sr)
                : HudDrag.update("keyboard_display", xPosition, yPosition, scale, defaultX, defaultY,
                layout.width, layout.height, sr);
        float drawX = pos[0];
        float drawY = pos[1];

        if (nightBloom && NightBloomHudDockRenderer.isDocked("keyboard_display")) {
            float opacity = Math.max(0.0F, Math.min(1.0F, alpha.getValue().floatValue() / 255.0F));
            NightBloomHudDockRenderer.drawPanel("keyboard_display", drawX, drawY, layout.width, layout.height,
                    NIGHT_BLOOM_RADIUS, opacity, NIGHT_BLOOM_SURFACE);
        }

        int index = 0;
        for (KeyBox key : layout.keys) {
            drawKey(drawX + key.x, drawY + key.y, key.width, key.height, key.id, index++);
        }
        if (nightBloom) {
            HudDrag.drawDockHint("keyboard_display", drawX, drawY, layout.width, layout.height,
                    NIGHT_BLOOM_RADIUS);
        } else {
            HudDrag.drawHint("keyboard_display", drawX, drawY, layout.width, layout.height,
                    Math.max(2.0f, 5.0f * scale.getValue().floatValue()));
        }
        HudDrag.handleScroll("keyboard_display", scale, drawX, drawY, layout.width, layout.height, 0.6f, 1.8f);
    }

    @Override
    public void disable() {
        HudDrag.unregisterDocked("keyboard_display");
        animations.clear();
        nightBloomFeedback.clear();
        nightBloomClock.reset();
        leftClicks.clear();
        rightClicks.clear();
        previousLeft = false;
        previousRight = false;
    }

    private void buildVerticalLayout(Layout layout, float keySize, float gap) {
        layout.clear();
        float totalWidth = keySize * 3.0f + gap * 2.0f;
        float y = 0.0f;

        if (Boolean.TRUE.equals(showMovement.getValue())) {
            layout.add(FORWARD, keySize + gap, y, keySize, keySize);
            y += keySize + gap;
            layout.add(LEFT, 0.0f, y, keySize, keySize);
            layout.add(BACK, keySize + gap, y, keySize, keySize);
            layout.add(RIGHT, (keySize + gap) * 2.0f, y, keySize, keySize);
            y += keySize + gap;
        }

        if (Boolean.TRUE.equals(showSpace.getValue())) {
            layout.add(JUMP, 0.0f, y, totalWidth, keySize * 0.72f);
            y += keySize * 0.72f + gap;
        }

        if (Boolean.TRUE.equals(showMouse.getValue())) {
            float mouseWidth = (totalWidth - gap) / 2.0f;
            layout.add(LMB, 0.0f, y, mouseWidth, keySize);
            layout.add(RMB, mouseWidth + gap, y, mouseWidth, keySize);
            y += keySize + gap;
        }

        layout.width = totalWidth;
        layout.height = Math.max(0.0f, y - gap);
    }

    private void buildHorizontalLayout(Layout layout, float keySize, float gap) {
        layout.clear();
        float x = 0.0f;

        if (Boolean.TRUE.equals(showMovement.getValue())) {
            layout.add(FORWARD, x, 0.0f, keySize, keySize);
            x += keySize + gap;
            layout.add(LEFT, x, 0.0f, keySize, keySize);
            x += keySize + gap;
            layout.add(BACK, x, 0.0f, keySize, keySize);
            x += keySize + gap;
            layout.add(RIGHT, x, 0.0f, keySize, keySize);
            x += keySize + gap;
        }

        if (Boolean.TRUE.equals(showSpace.getValue())) {
            layout.add(JUMP, x, 0.0f, keySize * 2.0f, keySize);
            x += keySize * 2.0f + gap;
        }

        if (Boolean.TRUE.equals(showMouse.getValue())) {
            layout.add(LMB, x, 0.0f, keySize * 1.55f, keySize);
            x += keySize * 1.55f + gap;
            layout.add(RMB, x, 0.0f, keySize * 1.55f, keySize);
            x += keySize * 1.55f + gap;
        }

        layout.width = Math.max(0.0f, x - gap);
        layout.height = keySize;
    }

    private void drawKey(float x, float y, float width, float height, String id, int index) {
        if (HUD.getActiveStyle() == HUD.HudStyle.NIGHT_BLOOM) {
            drawNightBloomKey(x, y, width, height, id, getNightBloomFeedback(id));
            return;
        }
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

    private void drawNightBloomKey(float x, float y, float width, float height, String id, float feedback) {
        float opacity = Math.max(0.0F, Math.min(1.0F, alpha.getValue().floatValue() / 255.0F));
        float round = NIGHT_BLOOM_RADIUS;
        int textColor = ColorUtils.interpolate(NIGHT_BLOOM.getTextPrimary(), NIGHT_BLOOM_PRIMARY, feedback);

        HUD.drawNightBloomShadow(x, y, x + width, y + height, round, opacity);
        RenderServices.shapes().rounded(x, y, x + width, y + height, round,
                multiplyAlpha(NIGHT_BLOOM_SURFACE, opacity));
        if (feedback > 0.01F) {
            RenderServices.shapes().progressBar(x + 3.0F, y + height - 3.4F, x + width - 3.0F, y + height - 1.8F,
                    1.0F, feedback, multiplyAlpha(NIGHT_BLOOM.getSurfaceOverlay(), opacity * 0.82F),
                    multiplyAlpha(NIGHT_BLOOM_PRIMARY, opacity * 0.96F));
        }

        String label = getLabel(id);
        if ((LMB.equals(id) || RMB.equals(id)) && Boolean.TRUE.equals(showCps.getValue())) {
            int cps = LMB.equals(id) ? leftClicks.size() : rightClicks.size();
            String cpsText = cps + " CPS";
            HUD.drawNightBloomText(FontLoaders.C18, label,
                    x + (width - FontLoaders.C18.getStringWidth(label)) * 0.5F, y + 6.0F,
                    multiplyAlpha(textColor, opacity),
                    multiplyAlpha(NIGHT_BLOOM_PRIMARY, opacity * (0.42F + feedback * 0.24F)),
                    0.42F + feedback * 0.08F);
            HUD.drawNightBloomText(FontLoaders.C14, cpsText,
                    x + (width - FontLoaders.C14.getStringWidth(cpsText)) * 0.5F, y + 17.0F,
                    multiplyAlpha(NIGHT_BLOOM.getTextPrimary(), opacity * 0.82F),
                    multiplyAlpha(NIGHT_BLOOM_PRIMARY, opacity * 0.20F), 0.18F);
        } else {
            HUD.drawNightBloomText(FontLoaders.C18, label,
                    x + (width - FontLoaders.C18.getStringWidth(label)) * 0.5F,
                    y + (height - FontLoaders.C18.getHeight()) * 0.5F + 3.0F,
                    multiplyAlpha(textColor, opacity),
                    multiplyAlpha(NIGHT_BLOOM_PRIMARY, opacity * (0.42F + feedback * 0.24F)),
                    0.42F + feedback * 0.08F);
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
        boolean nightBloom = HUD.getActiveStyle() == HUD.HudStyle.NIGHT_BLOOM;
        float nightBloomDelta = nightBloom ? nightBloomClock.tick(System.nanoTime()) : 0.0F;
        if (!nightBloom) {
            nightBloomClock.reset();
            nightBloomFeedback.clear();
        }
        updateKeyAnimation(FORWARD, acceptInput && isBindingDown(mc.gameSettings.keyBindForward.getKeyCode()), nightBloom, nightBloomDelta);
        updateKeyAnimation(LEFT, acceptInput && isBindingDown(mc.gameSettings.keyBindLeft.getKeyCode()), nightBloom, nightBloomDelta);
        updateKeyAnimation(BACK, acceptInput && isBindingDown(mc.gameSettings.keyBindBack.getKeyCode()), nightBloom, nightBloomDelta);
        updateKeyAnimation(RIGHT, acceptInput && isBindingDown(mc.gameSettings.keyBindRight.getKeyCode()), nightBloom, nightBloomDelta);
        updateKeyAnimation(JUMP, acceptInput && isBindingDown(mc.gameSettings.keyBindJump.getKeyCode()), nightBloom, nightBloomDelta);

        boolean leftDown = acceptInput && Mouse.isButtonDown(0);
        boolean rightDown = acceptInput && Mouse.isButtonDown(1);
        updateKeyAnimation(LMB, leftDown, nightBloom, nightBloomDelta);
        updateKeyAnimation(RMB, rightDown, nightBloom, nightBloomDelta);

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

    private void updateKeyAnimation(String id, boolean pressed, boolean nightBloom, float deltaSeconds) {
        setAnimated(id, pressed);
        if (!nightBloom) {
            return;
        }
        NightBloomKeyFeedback feedback = nightBloomFeedback.get(id);
        if (feedback == null) {
            feedback = new NightBloomKeyFeedback();
            nightBloomFeedback.put(id, feedback);
        }
        feedback.setPressed(pressed);
        feedback.update(deltaSeconds);
    }

    private float getNightBloomFeedback(String id) {
        NightBloomKeyFeedback feedback = nightBloomFeedback.get(id);
        return feedback == null ? 0.0F : feedback.get();
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

    private static int multiplyAlpha(int color, float alpha) {
        int sourceAlpha = color >>> 24 & 255;
        int resolvedAlpha = Math.round(sourceAlpha * Math.max(0.0F, Math.min(1.0F, alpha)));
        return color & 0x00FFFFFF | resolvedAlpha << 24;
    }

    private static class KeyBox {
        private String id;
        private float x;
        private float y;
        private float width;
        private float height;

        private void set(String id, float x, float y, float width, float height) {
            this.id = id;
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
        }
    }

    private static class Layout {
        private static final int MAX_KEYS = 7;
        private final ArrayList<KeyBox> keys = new ArrayList<KeyBox>(MAX_KEYS);
        private final KeyBox[] pool = new KeyBox[MAX_KEYS];
        private float width;
        private float height;
        private int used;

        private Layout() {
            for (int i = 0; i < pool.length; i++) {
                pool[i] = new KeyBox();
            }
        }

        private void clear() {
            keys.clear();
            used = 0;
            width = 0.0f;
            height = 0.0f;
        }

        private void add(String id, float x, float y, float width, float height) {
            if (used >= pool.length) {
                return;
            }
            KeyBox key = pool[used++];
            key.set(id, x, y, width, height);
            keys.add(key);
        }
    }
}
