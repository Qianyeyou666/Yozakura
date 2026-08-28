package gq.yozakura.ui.click.yozakura;

import gq.yozakura.engine.font.FontLoaders;
import gq.yozakura.engine.render.ui.RenderServices;
import gq.yozakura.module.render.ClickGUI;
import gq.yozakura.util.animation.AnimationState;
import gq.yozakura.util.animation.AnimationUtil;
import org.lwjgl.input.Keyboard;

/**
 * Sliding color picker panel at the bottom of the main panel.
 *
 * <p>Layout (left → right):
 * <ul>
 *   <li>SV (saturation/value) picker square — click/drag to pick S and V</li>
 *   <li>Hue bar — vertical, click/drag to pick hue</li>
 *   <li>Color info column: large preview swatch, hex input, preset dots</li>
 * </ul>
 *
 * <p>The picker edits the CUSTOM palette RGB values directly via
 * {@link ClickGUI#accentRed}/{@code accentGreen}/{@code accentBlue} and switches
 * the active palette to {@link ClickGUI.Palette#CUSTOM}.
 */
public final class ClickGuiColorPicker {
    private static final float SV_W = 190f;
    private static final float SV_H = 150f;
    private static final float HUE_W = 16f;
    private static final float HUE_H = 150f;
    private static final float INFO_GAP = 18f;
    private static final float PREVIEW_H = 40f;
    private static final float HEX_H = 34f;
    private static final float PRESET_DOT_SIZE = 22f;
    private static final float PRESET_GAP = 6f;
    private static final float PANEL_PAD_X = 22f;
    private static final float PANEL_PAD_Y = 18f;
    private static final float HEADER_H = 24f;

    private final AnimationState anim;
    private boolean open;
    private float openT;

    // HSV state (0..1)
    private float hue = 0.75f;
    private float sat = 0.68f;
    private float val = 0.96f;

    // Dragging state
    private boolean draggingSV;
    private boolean draggingHue;

    // Hex input state
    private boolean hexFocused;
    private String hexDraft = "";
    private int cursorBlink;

    public ClickGuiColorPicker(AnimationState anim) {
        this.anim = anim;
        syncFromCurrentAccent();
    }

    public boolean isOpen() {
        return open;
    }

    public void setOpen(boolean open) {
        this.open = open;
        if (open) {
            syncFromCurrentAccent();
            hexDraft = "";
            hexFocused = false;
        }
    }

    public void toggle() {
        setOpen(!open);
    }

    /** Syncs the internal HSV state from the current accent color. */
    private void syncFromCurrentAccent() {
        int accent = ClickGuiTheme.accent();
        float[] hsv = ClickGuiTheme.rgbToHsv(accent);
        hue = hsv[0];
        sat = hsv[1];
        val = hsv[2];
    }

    /** Draws the panel inside the supplied bounds (always called; respects open state). */
    public void draw(float x, float y, float w, float h, int mouseX, int mouseY, float frameScale) {
        // Animate open/close
        float target = open ? 1f : 0f;
        openT = anim.eased("cp-open", target, ClickGuiTheme.EASE_OUT_SPEED, frameScale,
                open ? 1f : 0f, AnimationUtil.Ease.OUT_CUBIC);
        if (openT <= 0.001f) return;

        float panelH = PANEL_PAD_Y * 2 + HEADER_H + SV_H;
        float drawH = panelH * openT;
        float panelY = y + h - drawH;

        // Clip to drawn portion so contents slide up smoothly
        ClickGuiRenderContext.pushScissor(x, panelY, w, drawH);
        try {
            drawPanel(x, panelY, w, panelH, mouseX, mouseY, frameScale);
        } finally {
            ClickGuiRenderContext.popScissor();
        }
    }

    private void drawPanel(float x, float y, float w, float h, int mouseX, int mouseY, float frameScale) {
        // Background — gradient from settings to slightly transparent
        RenderServices.shapes().rect(x, y, x + w, y + h, ClickGuiTheme.SETTINGS);
        // Top accent divider
        RenderServices.shapes().horizontalGradient(x, y, x + w, y + 1,
                ClickGuiTheme.withAlpha(ClickGuiTheme.accent(), 0x00),
                ClickGuiTheme.withAlpha(ClickGuiTheme.accent(), 0x66));
        RenderServices.shapes().horizontalGradient(x + w / 2f, y, x + w, y + 1,
                ClickGuiTheme.withAlpha(ClickGuiTheme.accent(), 0x66),
                ClickGuiTheme.withAlpha(ClickGuiTheme.accent(), 0x00));

        // ===== Header =====
        float headerY = y + PANEL_PAD_Y;
        // Title with palette icon
        float titleX = x + PANEL_PAD_X;
        RenderServices.shapes().roundedWH(titleX, headerY + 2f, 3f, 11f, 1.5f, ClickGuiTheme.accent());
        FontLoaders.BRICOLAGE14.drawString("Color Picker", titleX + 10, headerY + 4, ClickGuiTheme.FG);
        // Subtitle
        String sub = "Customize accent color";
        float subW = FontLoaders.INTER12.getStringWidth(sub);
        FontLoaders.INTER12.drawString(sub, x + w - PANEL_PAD_X - subW, headerY + 6, ClickGuiTheme.FG_4);

        // ===== Body =====
        float bodyY = headerY + HEADER_H + 4f;
        float svX = x + PANEL_PAD_X;
        float svY = bodyY;
        float hueX = svX + SV_W + INFO_GAP;
        float hueY = bodyY;
        float infoX = hueX + HUE_W + INFO_GAP;

        drawSVPicker(svX, svY, mouseX, mouseY);
        drawHueBar(hueX, hueY, mouseX, mouseY);
        drawColorInfo(infoX, svY, x + w - infoX - PANEL_PAD_X, mouseX, mouseY);
    }

    private void drawSVPicker(float x, float y, int mouseX, int mouseY) {
        // Background — base hue color, with white-to-color horizontal and color-to-black vertical
        float[] hsvTop = ClickGuiTheme.rgbToHsv(ClickGuiTheme.hsvToArgb(hue, 1f, 1f, 0xFF));
        int baseColor = ClickGuiTheme.hsvToArgb(hue, 1f, 1f, 0xFF);
        // White-to-baseColor horizontal gradient
        RenderServices.shapes().roundedWH(x, y, SV_W, SV_H, ClickGuiTheme.R_SM, 0xFFFFFFFF);
        RenderServices.shapes().horizontalGradient(x, y, x + SV_W, y + SV_H, 0xFFFFFFFF, baseColor);
        // Black-to-transparent vertical gradient (overlay)
        RenderServices.shapes().verticalGradient(x, y, x + SV_W, y + SV_H,
                ClickGuiTheme.withAlpha(0x000000, 0x00), 0xFF000000);
        // Border
        RenderServices.shapes().roundedBorderWH(x, y, SV_W, SV_H, ClickGuiTheme.R_SM, 1f,
                0x00000000, ClickGuiTheme.BORDER_2);

        // Cursor
        float cursorX = x + sat * SV_W;
        float cursorY = y + (1f - val) * SV_H;
        int cursorColor = ClickGuiTheme.hsvToArgb(hue, sat, val, 0xFF);
        // Outer white ring
        RenderServices.shapes().circle(cursorX, cursorY, 0, 360, 8f, 0xFFFFFFFF);
        RenderServices.shapes().circle(cursorX, cursorY, 0, 360, 6f, cursorColor);
    }

    private void drawHueBar(float x, float y, int mouseX, int mouseY) {
        // Hue gradient — vertical, top to bottom: red → yellow → green → cyan → blue → magenta → red
        // Rendered as stacked solid color segments (16 segments) for simplicity
        int segments = 36;
        float segH = HUE_H / segments;
        for (int i = 0; i < segments; i++) {
            float segHue = i / (float) segments;
            int segColor = ClickGuiTheme.hsvToArgb(segHue, 1f, 1f, 0xFF);
            RenderServices.shapes().rect(x, y + i * segH, x + HUE_W, y + (i + 1) * segH, segColor);
        }
        // Border
        RenderServices.shapes().roundedBorderWH(x, y, HUE_W, HUE_H, 8f, 1f,
                0x00000000, ClickGuiTheme.BORDER_2);

        // Cursor — small horizontal pill
        float cursorY = y + hue * HUE_H;
        int cursorColor = ClickGuiTheme.hsvToArgb(hue, 1f, 1f, 0xFF);
        RenderServices.shapes().roundedWH(x - 3f, cursorY - 3f, HUE_W + 6f, 6f, 3f, 0xFFFFFFFF);
        RenderServices.shapes().roundedWH(x - 1f, cursorY - 1f, HUE_W + 2f, 2f, 1f, cursorColor);
    }

    private void drawColorInfo(float x, float y, float w, int mouseX, int mouseY) {
        int currentColor = ClickGuiTheme.hsvToArgb(hue, sat, val, 0xFF);

        // Preview swatch
        RenderServices.shapes().roundedWH(x, y, w, PREVIEW_H, ClickGuiTheme.R_SM, currentColor);
        RenderServices.shapes().roundedBorderWH(x, y, w, PREVIEW_H, ClickGuiTheme.R_SM, 1f,
                0x00000000, ClickGuiTheme.BORDER_2);

        // Hex input row
        float hexY = y + PREVIEW_H + 10f;
        String hex = ClickGuiTheme.toHex(currentColor);
        String displayHex = hexFocused ? hexDraft : hex.toUpperCase();
        if (displayHex.isEmpty()) displayHex = "#";
        int hexBg = hexFocused ? ClickGuiTheme.CARD_HOVER : ClickGuiTheme.CARD;
        int hexBorder = hexFocused ? ClickGuiTheme.accent() : ClickGuiTheme.BORDER;
        RenderServices.shapes().roundedBorderWH(x, hexY, w, HEX_H, ClickGuiTheme.R_SM, 1f, hexBg, hexBorder);
        if (hexFocused) {
            // Focus glow
            RenderServices.shapes().roundedWH(x - 2f, hexY - 2f, w + 4f, HEX_H + 4f,
                    ClickGuiTheme.R_SM + 1f, ClickGuiTheme.accentDim());
        }
        FontLoaders.MONO12.drawString(displayHex, x + 12f, hexY + (HEX_H - 12f) / 2f, ClickGuiTheme.FG);
        // Blinking cursor when focused
        if (hexFocused && (cursorBlink / 30) % 2 == 0) {
            float cursorX = x + 12f + FontLoaders.MONO12.getStringWidth(displayHex);
            RenderServices.shapes().rect(cursorX, hexY + 8f, cursorX + 1f, hexY + HEX_H - 8f,
                    ClickGuiTheme.accentHover());
        }

        // Preset dots row
        float dotY = hexY + HEX_H + 12f;
        float dotX = x;
        for (int i = 0; i < ClickGuiTheme.PRESET_COLORS.length; i++) {
            int preset = ClickGuiTheme.PRESET_COLORS[i];
            boolean active = preset == currentColor;
            // Border (white ring when active)
            if (active) {
                RenderServices.shapes().circle(dotX + PRESET_DOT_SIZE / 2f,
                        dotY + PRESET_DOT_SIZE / 2f, 0, 360,
                        PRESET_DOT_SIZE / 2f + 2f, 0xFFFFFFFF);
            }
            RenderServices.shapes().circle(dotX + PRESET_DOT_SIZE / 2f,
                    dotY + PRESET_DOT_SIZE / 2f, 0, 360,
                    PRESET_DOT_SIZE / 2f, preset);
            // Inner highlight
            RenderServices.shapes().circle(dotX + PRESET_DOT_SIZE / 2f,
                    dotY + PRESET_DOT_SIZE / 2f, 0, 360,
                    PRESET_DOT_SIZE / 2f - 1f, ClickGuiTheme.withAlpha(0xFFFFFF, 0x14));
            dotX += PRESET_DOT_SIZE + PRESET_GAP;
        }

        cursorBlink++;
    }

    /** Handles mouse clicks. Returns true if consumed. */
    public boolean mouseClicked(float x, float y, float w, float h, int mouseX, int mouseY, int button) {
        if (!open || openT < 0.5f || button != 0) return false;

        float panelH = PANEL_PAD_Y * 2 + HEADER_H + SV_H;
        float panelY = y + h - panelH;
        if (mouseY < panelY) {
            // Click outside panel — keep hex input state consistent
            hexFocused = false;
            return false;
        }

        float bodyY = panelY + PANEL_PAD_Y + HEADER_H + 4f;
        float svX = x + PANEL_PAD_X;
        float svY = bodyY;
        float hueX = svX + SV_W + INFO_GAP;
        float hueY = bodyY;
        float infoX = hueX + HUE_W + INFO_GAP;

        // SV picker
        if (mouseX >= svX && mouseX <= svX + SV_W && mouseY >= svY && mouseY <= svY + SV_H) {
            draggingSV = true;
            updateSVFromMouse(svX, svY, mouseX, mouseY);
            applyToAccent();
            return true;
        }
        // Hue bar
        if (mouseX >= hueX - 3f && mouseX <= hueX + HUE_W + 3f
                && mouseY >= hueY && mouseY <= hueY + HUE_H) {
            draggingHue = true;
            updateHueFromMouse(hueY, mouseY);
            applyToAccent();
            return true;
        }
        // Hex input box
        float hexY = svY + PREVIEW_H + 10f;
        if (mouseX >= infoX && mouseX <= infoX + (x + w - infoX - PANEL_PAD_X)
                && mouseY >= hexY && mouseY <= hexY + HEX_H) {
            hexFocused = true;
            hexDraft = ClickGuiTheme.toHex(ClickGuiTheme.hsvToArgb(hue, sat, val, 0xFF)).toUpperCase();
            return true;
        }
        // Preset dots
        float dotY = hexY + HEX_H + 12f;
        float dotX = infoX;
        for (int i = 0; i < ClickGuiTheme.PRESET_COLORS.length; i++) {
            if (mouseX >= dotX && mouseX <= dotX + PRESET_DOT_SIZE
                    && mouseY >= dotY && mouseY <= dotY + PRESET_DOT_SIZE) {
                int preset = ClickGuiTheme.PRESET_COLORS[i];
                float[] hsv = ClickGuiTheme.rgbToHsv(preset);
                hue = hsv[0];
                sat = hsv[1];
                val = hsv[2];
                applyToAccent();
                hexFocused = false;
                return true;
            }
            dotX += PRESET_DOT_SIZE + PRESET_GAP;
        }

        hexFocused = false;
        return false;
    }

    /** Continues an in-progress SV/hue drag. Returns true if consumed. */
    public boolean mouseDragged(float x, float y, float w, float h, int mouseX, int mouseY) {
        if (!open) return false;
        if (!draggingSV && !draggingHue) return false;

        float panelH = PANEL_PAD_Y * 2 + HEADER_H + SV_H;
        float panelY = y + h - panelH;
        float bodyY = panelY + PANEL_PAD_Y + HEADER_H + 4f;
        float svX = x + PANEL_PAD_X;
        float svY = bodyY;
        float hueY = bodyY;

        if (draggingSV) {
            updateSVFromMouse(svX, svY, mouseX, mouseY);
            applyToAccent();
            return true;
        }
        if (draggingHue) {
            updateHueFromMouse(hueY, mouseY);
            applyToAccent();
            return true;
        }
        return false;
    }

    public void mouseReleased() {
        draggingSV = false;
        draggingHue = false;
    }

    /** Handles keyboard input for the hex input field. Returns true if consumed. */
    public boolean keyTyped(char typedChar, int keyCode) {
        if (!hexFocused) return false;
        if (keyCode == Keyboard.KEY_ESCAPE) {
            hexFocused = false;
            return true;
        }
        if (keyCode == Keyboard.KEY_RETURN) {
            commitHexDraft();
            hexFocused = false;
            return true;
        }
        if (keyCode == Keyboard.KEY_BACK) {
            if (hexDraft.length() > 1) {
                hexDraft = hexDraft.substring(0, hexDraft.length() - 1);
            }
            return true;
        }
        // Accept hex chars (0-9, a-f, A-F) and leading #
        if (typedChar == '#' && hexDraft.isEmpty()) {
            hexDraft = "#";
            return true;
        }
        if ((typedChar >= '0' && typedChar <= '9')
                || (typedChar >= 'a' && typedChar <= 'f')
                || (typedChar >= 'A' && typedChar <= 'F')) {
            // Limit to #XXXXXX (7 chars)
            if (hexDraft.length() < 7) {
                String candidate = hexDraft.isEmpty() ? ("#" + typedChar) : (hexDraft + typedChar);
                hexDraft = candidate;
                // Auto-commit if we have a full 6-digit hex
                if (hexDraft.length() == 7) {
                    commitHexDraft();
                }
            }
            return true;
        }
        return false;
    }

    private void commitHexDraft() {
        int parsed = ClickGuiTheme.fromHex(hexDraft);
        float[] hsv = ClickGuiTheme.rgbToHsv(parsed);
        hue = hsv[0];
        sat = hsv[1];
        val = hsv[2];
        applyToAccent();
    }

    private void updateSVFromMouse(float svX, float svY, int mouseX, int mouseY) {
        sat = Math.max(0f, Math.min(1f, (mouseX - svX) / SV_W));
        val = Math.max(0f, Math.min(1f, 1f - (mouseY - svY) / SV_H));
    }

    private void updateHueFromMouse(float hueY, int mouseY) {
        hue = Math.max(0f, Math.min(1f, (mouseY - hueY) / HUE_H));
    }

    /** Writes the current HSV-derived RGB into ClickGUI.accentRed/Green/Blue and switches palette to CUSTOM. */
    private void applyToAccent() {
        int rgb = ClickGuiTheme.hsvToArgb(hue, sat, val, 0xFF);
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        int b = rgb & 0xFF;
        ClickGUI.palette.setValue(ClickGUI.Palette.CUSTOM);
        ClickGUI.accentRed.setValue((double) r);
        ClickGUI.accentGreen.setValue((double) g);
        ClickGUI.accentBlue.setValue((double) b);
    }
}
