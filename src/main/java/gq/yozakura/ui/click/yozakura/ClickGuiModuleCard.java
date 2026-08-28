package gq.yozakura.ui.click.yozakura;

import gq.yozakura.engine.font.FontLoaders;
import gq.yozakura.engine.render.ui.RenderServices;
import gq.yozakura.module.Module;
import gq.yozakura.util.animation.AnimationState;
import gq.yozakura.util.animation.AnimationUtil;
import gq.yozakura.value.Mode;
import gq.yozakura.value.Value;
import org.lwjgl.input.Keyboard;

import java.util.ArrayList;
import java.util.List;

/**
 * Renders a single module card and (when expanded) the settings panel below it.
 *
 * <p>The card shows: module icon | name + description | keybind badge | config gear | toggle.
 * Click on the info area toggles the module; click on the gear or right-click on the card
 * expands/collapses the settings panel. Only one card per GUI is expanded at a time
 * (coordinated by {@link #isExpanded()}).
 *
 * <p>Visual system follows the Nether v2.1 "layered depth + thick glass" spec:
 * <ul>
 *   <li>Enabled cards use a 135° accent gradient overlay (accent.07 → transparent at 70%)</li>
 *   <li>Hover applies a 2px lift, 1% scale, and a soft shadow</li>
 *   <li>Settings panel uses section-based layout (General / Advanced) when applicable</li>
 * </ul>
 */
public final class ClickGuiModuleCard {
    private static final float CARD_H = ClickGuiTheme.CARD_H;
    private static final float PAD_X = ClickGuiTheme.CARD_PAD_X;
    private static final float ICON_SIZE = 36f;
    private static final float TOGGLE_W = 40f;
    private static final float TOGGLE_H = 24f;
    private static final float CONFIG_BTN_SIZE = 30f;
    private static final float BIND_BTN_SIZE = 30f;
    private static final float GAP = 8f;
    private static final float EXPAND_PAD = 16f;
    private static final float SECTION_GAP = 16f;
    private static final float SECTION_HEADER_H = 22f;
    private static final float EXPAND_TITLE_H = 22f;

    private final Module module;
    private final String keyPrefix;
    private boolean expanded;
    private boolean listeningForKey;
    private boolean appeared;
    private int entranceIndex;
    private long entranceBaseTime;

    public ClickGuiModuleCard(Module module, String keyPrefix) {
        this.module = module;
        this.keyPrefix = keyPrefix;
    }

    /** Sets the card's index in the list and the base time for staggered entrance. */
    public void setEntrance(int index, long baseTime) {
        this.entranceIndex = index;
        this.entranceBaseTime = baseTime;
        this.appeared = false;
    }

    public Module module() {
        return module;
    }

    public boolean isExpanded() {
        return expanded;
    }

    public void setExpanded(boolean expanded) {
        this.expanded = expanded;
        if (!expanded) {
            listeningForKey = false;
        }
    }

    public boolean isListeningForKey() {
        return listeningForKey;
    }

    /** Height of the card + (if expanded) the expand panel given the visible values. */
    public float totalHeight(float w, ClickGuiValueRenderer valueRenderer) {
        float h = CARD_H;
        if (expanded) {
            h += expandHeight(w, valueRenderer);
        }
        return h;
    }

    private float expandHeight(float w, ClickGuiValueRenderer valueRenderer) {
        List<Section> sections = buildSections();
        if (sections.isEmpty()) return 0;
        float rowH = valueRenderer.rowHeight();
        float inner = EXPAND_PAD * 2 + EXPAND_TITLE_H;
        for (Section section : sections) {
            inner += section.headerHeight();
            inner += section.visibleRows() * rowH;
            if (!section.isLast) inner += SECTION_GAP;
        }
        return inner + 8f; // margin-top
    }

    /**
     * Draws the card at (x, y). Returns the total height consumed.
     */
    public float draw(float x, float y, float w, int mouseX, int mouseY,
                      float frameScale, AnimationState anim,
                      ClickGuiValueRenderer valueRenderer) {
        boolean enabled = module.getState();
        boolean hover = isCardHover(x, y, w, mouseX, mouseY);

        // Entrance fade-in animation (per-card, plays once on first appearance)
        if (!appeared) {
            appeared = true;
            anim.snap("card-fade:" + keyPrefix, 0f);
        }
        float fadeIn = anim.eased("card-fade:" + keyPrefix, 1f, 0.12f, frameScale, 0f, AnimationUtil.Ease.OUT_CUBIC);
        float fadeOffset = (1f - fadeIn) * 12f;
        float fadeAlpha = fadeIn;

        // Hover animation
        float hoverT = anim.eased("card-hover:" + keyPrefix, hover ? 1f : 0f,
                ClickGuiTheme.SPRING_SPEED, frameScale, 0f, AnimationUtil.Ease.OUT_CUBIC);
        float lift = hoverT * 2f;
        float scale = 1f + hoverT * 0.01f;
        float cardY = y - lift - fadeOffset;
        float cardW = w * scale;
        float cardX = x + (w - cardW) / 2f;

        // Ambient shadow — always present on every card (matches design --shadow-ambient 0 8px 24px rgba(0,0,0,.08))
        int ambientAlpha = (int) (0x14 * fadeAlpha);
        RenderServices.shapes().shadow(cardX, cardY, cardX + cardW, cardY + CARD_H,
                ClickGuiTheme.R_MD, (ambientAlpha << 24), 4, 4f);
        // Hover shadow (soft, accent-tinted when enabled, dark otherwise) — layered on top of ambient
        if (hoverT > 0.05f) {
            int shadowColor = enabled
                    ? ClickGuiTheme.withAlpha(ClickGuiTheme.accent(), (int) (0x33 * hoverT))
                    : ClickGuiTheme.withAlpha(0x000000, (int) (0x55 * hoverT));
            RenderServices.shapes().shadow(cardX, cardY, cardX + cardW, cardY + CARD_H,
                    ClickGuiTheme.R_MD, shadowColor, 6, 8f);
        }

        // ===== Card background =====
        // Base color
        int baseBg = ClickGuiTheme.blend(ClickGuiTheme.CARD, ClickGuiTheme.CARD_HOVER, hoverT * 0.5f);
        RenderServices.shapes().joinedRounded(cardX, cardY, cardX + cardW, cardY + CARD_H,
                ClickGuiTheme.R_MD, ClickGuiTheme.R_MD,
                expanded ? 0f : ClickGuiTheme.R_MD,
                expanded ? 0f : ClickGuiTheme.R_MD,
                baseBg);

        // Enabled: diagonal accent gradient overlay (135deg, accent.07 → transparent 70%) — matches design
        if (enabled) {
            int accentStrong = ClickGuiTheme.withAlpha(ClickGuiTheme.accent(), 0x12);
            int accentMid = ClickGuiTheme.withAlpha(ClickGuiTheme.accent(), 0x09);
            int accentClear = ClickGuiTheme.withAlpha(ClickGuiTheme.accent(), 0x00);
            ClickGuiRenderContext.pushScissor(cardX, cardY, cardW, CARD_H);
            try {
                RenderServices.shapes().roundedGradient(cardX, cardY, cardX + cardW, cardY + CARD_H,
                        ClickGuiTheme.R_MD, accentStrong, accentMid, accentMid, accentClear);
            } finally {
                ClickGuiRenderContext.popScissor();
            }
        }

        // Border
        int borderColor = enabled
                ? ClickGuiTheme.blend(
                        ClickGuiTheme.withAlpha(ClickGuiTheme.accent(), 0x2E),
                        ClickGuiTheme.withAlpha(ClickGuiTheme.accent(), 0x40), hoverT)
                : ClickGuiTheme.blend(ClickGuiTheme.BORDER, ClickGuiTheme.withAlpha(ClickGuiTheme.accent(), 0x24), hoverT);
        RenderServices.shapes().roundedBorderWH(cardX, cardY, cardW, CARD_H, ClickGuiTheme.R_MD, 1f,
                0x00000000, borderColor);

        // Top inner highlight (1px white at 4% alpha) — subtle glass effect
        RenderServices.shapes().horizontalGradient(cardX + 2f, cardY + 1f, cardX + cardW - 2f, cardY + 2f,
                ClickGuiTheme.withAlpha(0xFFFFFF, 0x00),
                ClickGuiTheme.withAlpha(0xFFFFFF, 0x08));
        RenderServices.shapes().horizontalGradient(cardX + cardW / 2f, cardY + 1f, cardX + cardW - 2f, cardY + 2f,
                ClickGuiTheme.withAlpha(0xFFFFFF, 0x08),
                ClickGuiTheme.withAlpha(0xFFFFFF, 0x00));

        // Accent bar (left side) when enabled
        if (enabled) {
            float barH = CARD_H * 0.62f;
            float barY = cardY + (CARD_H - barH) / 2f;
            RenderServices.shapes().roundedWH(cardX, barY, 3f, barH, 1.5f, ClickGuiTheme.accent());
        }

        // ===== Info area (icon + name + desc) =====
        float infoX = cardX + PAD_X;
        float infoRight = cardX + cardW - PAD_X;
        float actionsW = TOGGLE_W + CONFIG_BTN_SIZE + GAP;
        float infoW = infoRight - actionsW - infoX - ICON_SIZE - GAP;

        // Icon box
        float iconX = infoX;
        float iconY = cardY + (CARD_H - ICON_SIZE) / 2f;
        drawIconBox(iconX, iconY, enabled, hover, hoverT);

        // Name + desc
        float textX = iconX + ICON_SIZE + GAP;
        FontLoaders.BRICOLAGE14.drawString(module.getName(), textX, cardY + 13, ClickGuiTheme.FG);
        String desc = module.getDes();
        if (desc != null && desc.length() > 0) {
            FontLoaders.INTER12.drawString(truncate(desc, infoW), textX, cardY + 30, ClickGuiTheme.FG_3);
        }

        // ===== Actions (right side) =====
        float actionX = infoRight - TOGGLE_W;
        float toggleY = cardY + (CARD_H - TOGGLE_H) / 2f;
        drawToggle(actionX, toggleY, enabled, hover, frameScale, anim);

        actionX -= CONFIG_BTN_SIZE + GAP;
        boolean hasSettings = !module.getValues().isEmpty();
        boolean configHover = mouseX >= actionX && mouseX <= actionX + CONFIG_BTN_SIZE
                && mouseY >= toggleY - 3 && mouseY <= toggleY - 3 + CONFIG_BTN_SIZE;
        float configRot = anim.eased("cfg-rot:" + keyPrefix, configHover ? 1f : 0f,
                ClickGuiTheme.SPRING_SPEED, frameScale, 0f, AnimationUtil.Ease.OUT_CUBIC);
        drawConfigBtn(actionX, toggleY - 3, configHover, hasSettings, enabled, configRot, frameScale, anim);

        float consumed = CARD_H;

        // ===== Expand panel =====
        if (expanded) {
            float expandH = expandHeight(w, valueRenderer);
            if (expandH > 0) {
                float panelY = cardY + CARD_H;
                // Panel background (squared top corners match card's squared bottom)
                RenderServices.shapes().joinedRounded(cardX, panelY, cardX + cardW, panelY + expandH,
                        0f, 0f, ClickGuiTheme.R_MD, ClickGuiTheme.R_MD, ClickGuiTheme.SETTINGS);
                RenderServices.shapes().roundedBorderWH(cardX, panelY, cardW, expandH, ClickGuiTheme.R_MD,
                        1f, ClickGuiTheme.SETTINGS, ClickGuiTheme.BORDER_2);
                // Panel ambient shadow
                RenderServices.shapes().shadow(cardX, panelY, cardX + cardW, panelY + expandH,
                        ClickGuiTheme.R_MD, 0x22000000, 4, 6f);
                // Top accent divider — gradient line (full width, centered glow)
                RenderServices.shapes().horizontalGradient(cardX, panelY,
                        cardX + cardW, panelY + 1f,
                        ClickGuiTheme.withAlpha(ClickGuiTheme.accent(), 0x00),
                        ClickGuiTheme.withAlpha(ClickGuiTheme.accent(), 0x66));
                RenderServices.shapes().horizontalGradient(cardX + cardW / 2f, panelY,
                        cardX + cardW, panelY + 1f,
                        ClickGuiTheme.withAlpha(ClickGuiTheme.accent(), 0x66),
                        ClickGuiTheme.withAlpha(ClickGuiTheme.accent(), 0x00));

                // Inner top highlight (1px white at 4% alpha) — subtle glass effect
                RenderServices.shapes().horizontalGradient(cardX + 2f, panelY + 1f,
                        cardX + cardW - 2f, panelY + 2f,
                        ClickGuiTheme.withAlpha(0xFFFFFF, 0x00),
                        ClickGuiTheme.withAlpha(0xFFFFFF, 0x0A));
                RenderServices.shapes().horizontalGradient(cardX + cardW / 2f, panelY + 1f,
                        cardX + cardW - 2f, panelY + 2f,
                        ClickGuiTheme.withAlpha(0xFFFFFF, 0x0A),
                        ClickGuiTheme.withAlpha(0xFFFFFF, 0x00));

                // Title — accent vertical bar + "SETTINGS" label
                RenderServices.shapes().roundedWH(cardX + EXPAND_PAD, panelY + EXPAND_PAD + 5, 3f, 11f, 1.5f,
                        ClickGuiTheme.accent());
                FontLoaders.MONO10.drawString("SETTINGS", cardX + EXPAND_PAD + 10, panelY + EXPAND_PAD + 4,
                        ClickGuiTheme.FG_3);

                // Section-based value rows
                float rowX = cardX + EXPAND_PAD;
                float rowW = cardW - EXPAND_PAD * 2;
                float cursorY = panelY + EXPAND_PAD + EXPAND_TITLE_H;
                List<Section> sections = buildSections();
                for (int s = 0; s < sections.size(); s++) {
                    Section section = sections.get(s);
                    if (section.header != null) {
                        // Section header — rounded bar + uppercase label + bottom divider (matches .section-header)
                        String headerText = section.header.toUpperCase();
                        RenderServices.shapes().roundedWH(rowX, cursorY + 10, 2, 9, 1f,
                                ClickGuiTheme.FG_4);
                        FontLoaders.MONO10.drawString(headerText, rowX + 8, cursorY + 8, ClickGuiTheme.FG_4);
                        // Bottom divider line — starts after the label, fades out to the right
                        float lineY = cursorY + SECTION_HEADER_H - 4f;
                    float labelW = FontLoaders.MONO10.getStringWidth(headerText);
                        RenderServices.shapes().horizontalGradient(rowX + 8 + labelW + 6, lineY,
                                rowX + rowW, lineY + 1f,
                                ClickGuiTheme.BORDER_2, ClickGuiTheme.withAlpha(ClickGuiTheme.BORDER_2, 0x00));
                        cursorY += SECTION_HEADER_H;
                    }
                    for (Value<?> v : section.values) {
                        if (!v.isVisible()) continue;
                        String key = keyPrefix + ":" + v.getName();
                        float h = valueRenderer.drawValue(v, key, rowX, cursorY, rowW, mouseX, mouseY,
                                frameScale, expanded);
                        cursorY += h;
                    }
                    if (!section.isLast) cursorY += SECTION_GAP;
                }
                consumed += expandH;
            }
        }

        return consumed;
    }

    private void drawIconBox(float x, float y, boolean enabled, boolean hover, float hoverT) {
        int bg;
        int border;
        if (enabled) {
            bg = ClickGuiTheme.blend(ClickGuiTheme.accentDim(), ClickGuiTheme.withAlpha(ClickGuiTheme.accent(), 0x22), hoverT);
            border = ClickGuiTheme.blend(
                    ClickGuiTheme.withAlpha(ClickGuiTheme.accent(), 0x38),
                    ClickGuiTheme.withAlpha(ClickGuiTheme.accent(), 0x55), hoverT);
        } else {
            bg = ClickGuiTheme.blend(ClickGuiTheme.CARD_HOVER, ClickGuiTheme.SELECTED, hover ? 1f : 0f);
            border = ClickGuiTheme.blend(ClickGuiTheme.BORDER, ClickGuiTheme.BORDER_2, hoverT);
        }
        RenderServices.shapes().roundedBorderWH(x, y, ICON_SIZE, ICON_SIZE, ClickGuiTheme.R_SM,
                1f, bg, border);
        // Inner top highlight
        RenderServices.shapes().horizontalGradient(x + 2f, y + 1f, x + ICON_SIZE - 2f, y + 2f,
                ClickGuiTheme.withAlpha(0xFFFFFF, 0x00),
                ClickGuiTheme.withAlpha(0xFFFFFF, enabled ? 0x10 : 0x08));
        RenderServices.shapes().horizontalGradient(x + ICON_SIZE / 2f, y + 1f, x + ICON_SIZE - 2f, y + 2f,
                ClickGuiTheme.withAlpha(0xFFFFFF, enabled ? 0x10 : 0x08),
                ClickGuiTheme.withAlpha(0xFFFFFF, 0x00));

        // Module icon glyph
        int iconColor = enabled ? ClickGuiTheme.accentHover()
                : (hover ? ClickGuiTheme.FG_2 : ClickGuiTheme.FG_3);
        ClickGuiIconShapes.drawModule(module.getName(), x + ICON_SIZE / 2f, y + ICON_SIZE / 2f,
                18f, 1.4f, iconColor);
    }

    private void drawToggle(float x, float y, boolean on, boolean hover,
                            float frameScale, AnimationState anim) {
        float target = on ? 1f : 0f;
        float t = anim.eased("tg-card:" + keyPrefix, target, ClickGuiTheme.SPRING_SPEED,
                frameScale, on ? 1f : 0f, AnimationUtil.Ease.OUT_CUBIC);
        // Background — accent gradient when on (approximated with two-layer gradient)
        int bgBase = ClickGuiTheme.blend(ClickGuiTheme.CARD_HOVER, ClickGuiTheme.accent(), t);
        int border = ClickGuiTheme.blend(ClickGuiTheme.BORDER_2,
                ClickGuiTheme.withAlpha(ClickGuiTheme.accent(), 0x59), t);
        RenderServices.shapes().roundedBorderWH(x, y, TOGGLE_W, TOGGLE_H, ClickGuiTheme.R_CAPSULE,
                1f, bgBase, border);
        // Inner top highlight when on (subtle glass effect)
        if (t > 0.05f) {
            RenderServices.shapes().horizontalGradient(x + 4f, y + 1f, x + TOGGLE_W - 4f, y + 2f,
                    ClickGuiTheme.withAlpha(0xFFFFFF, 0x00),
                    ClickGuiTheme.withAlpha(0xFFFFFF, (int) (0x18 * t)));
            RenderServices.shapes().horizontalGradient(x + TOGGLE_W / 2f, y + 1f, x + TOGGLE_W - 4f, y + 2f,
                    ClickGuiTheme.withAlpha(0xFFFFFF, (int) (0x18 * t)),
                    ClickGuiTheme.withAlpha(0xFFFFFF, 0x00));
        }
        // Knob — 18px diameter circle, slides 16px
        float knobR = 9f;
        float knobX = x + 11f + t * 16f;
        float knobY = y + TOGGLE_H / 2f;
        int knobColor = ClickGuiTheme.blend(ClickGuiTheme.FG_3, 0xFFFFFFFF, t);
        float knobScale = hover ? 1.1f : 1f;
        float knobRNow = knobR * knobScale;
        // Knob shadow
        RenderServices.shapes().circle(knobX, knobY + 0.5f, 0, 360, knobRNow,
                ClickGuiTheme.withAlpha(0x000000, 0x33));
        RenderServices.shapes().circle(knobX, knobY, 0, 360, knobRNow, knobColor);
        // Knob inner top highlight
        RenderServices.shapes().circle(knobX, knobY - 1f, 0, 180, knobRNow - 2f,
                ClickGuiTheme.withAlpha(0xFFFFFF, on ? 0x33 : 0x14));
    }

    private void drawConfigBtn(float x, float y, boolean hover, boolean hasSettings,
                               boolean enabled, float rotationT, float frameScale, AnimationState anim) {
        float hoverT = anim.eased("cfg-hover:" + keyPrefix, hover ? 1f : 0f,
                ClickGuiTheme.SPRING_SPEED, frameScale, 0f, AnimationUtil.Ease.OUT_CUBIC);
        int bg = enabled ? ClickGuiTheme.withAlpha(ClickGuiTheme.accent(), 0x0F) : ClickGuiTheme.CARD_HOVER;
        bg = ClickGuiTheme.blend(bg, ClickGuiTheme.SELECTED, hoverT);
        int border = enabled ? ClickGuiTheme.withAlpha(ClickGuiTheme.accent(), 0x38) : ClickGuiTheme.BORDER_2;
        int color = enabled ? ClickGuiTheme.accentHover() : (hover ? ClickGuiTheme.FG : ClickGuiTheme.FG_3);
        RenderServices.shapes().roundedBorderWH(x, y, CONFIG_BTN_SIZE, CONFIG_BTN_SIZE,
                ClickGuiTheme.R_SM, 1f, bg, border);
        // Inner top highlight
        RenderServices.shapes().horizontalGradient(x + 2f, y + 1f, x + CONFIG_BTN_SIZE - 2f, y + 2f,
                ClickGuiTheme.withAlpha(0xFFFFFF, 0x00),
                ClickGuiTheme.withAlpha(0xFFFFFF, 0x08));
        RenderServices.shapes().horizontalGradient(x + CONFIG_BTN_SIZE / 2f, y + 1f, x + CONFIG_BTN_SIZE - 2f, y + 2f,
                ClickGuiTheme.withAlpha(0xFFFFFF, 0x08),
                ClickGuiTheme.withAlpha(0xFFFFFF, 0x00));
        // Gear glyph — rotate 45deg on hover (rotationT: 0..1)
        float rot = rotationT * 45f;
        float cx = x + CONFIG_BTN_SIZE / 2f;
        float cy = y + CONFIG_BTN_SIZE / 2f;
        if (rot > 0.5f) {
            // Approximate rotation by drawing icon with GL matrix rotation
            gq.yozakura.engine.render.GLStateManager.begin2D();
            try {
                org.lwjgl.opengl.GL11.glPushMatrix();
                org.lwjgl.opengl.GL11.glTranslatef(cx, cy, 0f);
                org.lwjgl.opengl.GL11.glRotatef(rot, 0f, 0f, 1f);
                org.lwjgl.opengl.GL11.glTranslatef(-cx, -cy, 0f);
                ClickGuiIconShapes.drawGear(cx, cy, 15f, 1.35f, color);
                org.lwjgl.opengl.GL11.glPopMatrix();
            } finally {
                gq.yozakura.engine.render.GLStateManager.end2D();
            }
        } else {
            ClickGuiIconShapes.drawGear(cx, cy, 15f, 1.35f, color);
        }
    }

    private void drawBindBtn(float x, float y, boolean hover, boolean listening,
                             float frameScale, AnimationState anim) {
        float hoverT = anim.eased("bind-hover:" + keyPrefix, hover ? 1f : 0f,
                ClickGuiTheme.SPRING_SPEED, frameScale, 0f, AnimationUtil.Ease.OUT_CUBIC);
        int bg = ClickGuiTheme.blend(ClickGuiTheme.CARD_HOVER, ClickGuiTheme.SELECTED, hoverT);
        int border = listening ? ClickGuiTheme.accent()
                : ClickGuiTheme.blend(ClickGuiTheme.BORDER_2,
                        ClickGuiTheme.withAlpha(ClickGuiTheme.accent(), 0x33), hoverT);
        int color = listening ? ClickGuiTheme.accentHover()
                : ClickGuiTheme.blend(ClickGuiTheme.FG_3, ClickGuiTheme.FG_2, hoverT);
        RenderServices.shapes().roundedBorderWH(x, y, BIND_BTN_SIZE, BIND_BTN_SIZE,
                ClickGuiTheme.R_SM, 1f, bg, border);
        // Inner highlight
        RenderServices.shapes().horizontalGradient(x + 2f, y + 1f, x + BIND_BTN_SIZE - 2f, y + 2f,
                ClickGuiTheme.withAlpha(0xFFFFFF, 0x00),
                ClickGuiTheme.withAlpha(0xFFFFFF, 0x06));
        RenderServices.shapes().horizontalGradient(x + BIND_BTN_SIZE / 2f, y + 1f, x + BIND_BTN_SIZE - 2f, y + 2f,
                ClickGuiTheme.withAlpha(0xFFFFFF, 0x06),
                ClickGuiTheme.withAlpha(0xFFFFFF, 0x00));

        String label;
        if (listening) {
            label = "_";
        } else {
            int keyCode = module.getKey();
            label = keyCode <= 0 ? "—" : keyName(keyCode);
        }
        float textW = FontLoaders.MONO10.getStringWidth(label);
        FontLoaders.MONO10.drawString(label, x + (BIND_BTN_SIZE - textW) / 2f, y + 11, color);
    }

    /** Handles a mouse click. Returns true if consumed. */
    public boolean mouseClicked(float x, float y, float w, int mouseX, int mouseY, int button,
                                ClickGuiValueRenderer valueRenderer) {
        if (button != 0 && button != 1 && button != 2) return false;
        boolean hover = isCardHover(x, y, w, mouseX, mouseY);
        if (!hover && !isInExpandPanel(x, y, w, mouseX, mouseY, valueRenderer)) {
            return false;
        }

        // Compute action positions (same as draw)
        float infoRight = x + w - PAD_X;
        float actionX = infoRight - TOGGLE_W;
        float toggleY = y + (CARD_H - TOGGLE_H) / 2f;

        // Expand panel click?
        if (expanded && isInExpandPanel(x, y, w, mouseX, mouseY, valueRenderer)) {
            float expandTop = y + CARD_H + 8f + EXPAND_PAD + EXPAND_TITLE_H;
            float rowX = x + EXPAND_PAD;
            float rowW = w - EXPAND_PAD * 2;
            float cursorY = expandTop;
            List<Section> sections = buildSections();
            for (Section section : sections) {
                if (section.header != null) cursorY += SECTION_HEADER_H;
                for (Value<?> v : section.values) {
                    if (!v.isVisible()) continue;
                    String key = keyPrefix + ":" + v.getName();
                    float rowH = valueRenderer.rowHeight();
                    if (mouseY >= cursorY && mouseY <= cursorY + rowH) {
                        if (valueRenderer.mouseClicked(v, key, rowX, cursorY, rowW, mouseX, mouseY, button)) {
                            return true;
                        }
                    }
                    cursorY += rowH;
                }
                if (!section.isLast) cursorY += SECTION_GAP;
            }
        }

        if (!hover) return false;

        // Toggle click
        if (mouseX >= actionX && mouseX <= actionX + TOGGLE_W
                && mouseY >= toggleY && mouseY <= toggleY + TOGGLE_H) {
            module.toggle();
            return true;
        }
        // Config button
        float cfgX = actionX - CONFIG_BTN_SIZE - GAP;
        float cfgY = toggleY - 3;
        if (mouseX >= cfgX && mouseX <= cfgX + CONFIG_BTN_SIZE
                && mouseY >= cfgY && mouseY <= cfgY + CONFIG_BTN_SIZE) {
            expanded = !expanded;
            return true;
        }
        // Middle click keeps key binding available without adding a third row action.
        if (button == 2) {
            listeningForKey = true;
            return true;
        }
        // Info area click — toggle module (left button)
        if (button == 0 && mouseX >= x + PAD_X && mouseX < cfgX) {
            module.toggle();
            return true;
        }
        // Right-click anywhere on card — toggle expand
        if (button == 1 && !module.getValues().isEmpty()) {
            expanded = !expanded;
            return true;
        }
        return false;
    }

    /** Handles a slider drag continued within this card's panel. */
    public boolean mouseDragged(float x, float y, float w, int mouseX, int mouseY,
                                ClickGuiValueRenderer valueRenderer) {
        if (!expanded) return false;
        String draggedKey = valueRenderer.getDraggedSliderKey();
        if (draggedKey == null) return false;
        List<Section> sections = buildSections();
        float rowX = x + EXPAND_PAD;
        float rowW = w - EXPAND_PAD * 2;
        float cursorY = y + CARD_H + 8f + EXPAND_PAD + EXPAND_TITLE_H;
        for (Section section : sections) {
            if (section.header != null) cursorY += SECTION_HEADER_H;
            for (Value<?> v : section.values) {
                if (!v.isVisible()) continue;
                String key = keyPrefix + ":" + v.getName();
                if (key.equals(draggedKey) && v instanceof gq.yozakura.value.Numbers) {
                    valueRenderer.updateDraggedSlider((gq.yozakura.value.Numbers<?>) v, key, rowX, cursorY, rowW, mouseX);
                    return true;
                }
                cursorY += valueRenderer.rowHeight();
            }
            if (!section.isLast) cursorY += SECTION_GAP;
        }
        return false;
    }

    public boolean keyTyped(char typedChar, int keyCode) {
        if (!listeningForKey) return false;
        if (keyCode == Keyboard.KEY_ESCAPE) {
            listeningForKey = false;
            return true;
        }
        if (keyCode == Keyboard.KEY_BACK || keyCode == Keyboard.KEY_DELETE) {
            module.setKey(0);
            listeningForKey = false;
            return true;
        }
        module.setKey(keyCode);
        listeningForKey = false;
        return true;
    }

    private boolean isCardHover(float x, float y, float w, int mouseX, int mouseY) {
        return mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + CARD_H;
    }

    private boolean isInExpandPanel(float x, float y, float w, int mouseX, int mouseY,
                                    ClickGuiValueRenderer valueRenderer) {
        if (!expanded) return false;
        float expandH = expandHeight(w, valueRenderer);
        if (expandH <= 0) return false;
        return mouseX >= x && mouseX <= x + w
                && mouseY >= y + CARD_H && mouseY <= y + CARD_H + expandH;
    }

    private String truncate(String text, float maxWidth) {
        if (FontLoaders.INTER12.getStringWidth(text) <= maxWidth) return text;
        String ellipsis = "…";
        float ellipsisW = FontLoaders.INTER12.getStringWidth(ellipsis);
        int lo = 0, hi = text.length();
        while (lo < hi) {
            int mid = (lo + hi + 1) / 2;
            if (FontLoaders.INTER12.getStringWidth(text.substring(0, mid)) + ellipsisW <= maxWidth) {
                lo = mid;
            } else {
                hi = mid - 1;
            }
        }
        return text.substring(0, lo) + ellipsis;
    }

    private static String keyName(int keyCode) {
        String name = Keyboard.getKeyName(keyCode);
        if (name == null) return "?";
        if (name.startsWith("KEY_")) name = name.substring(4);
        if (name.length() > 3) name = name.substring(0, 3);
        return name.toUpperCase();
    }

    // ===== Section-based layout for expand panel =====

    /**
     * Builds a sectioned view of this module's visible values.
     *
     * <p>Layout rules (matching the Nether v2.1 spec):
     * <ul>
     *   <li>≤2 visible values → single untitled section</li>
     *   <li>Has a "mode" value → General (mode + 1 other) / Advanced (the rest)</li>
     *   <li>Otherwise → single "Settings" section</li>
     * </ul>
     */
    private List<Section> buildSections() {
        List<Value<?>> visible = new ArrayList<Value<?>>();
        for (Value<?> v : module.getValues()) {
            if (v.isVisible()) visible.add(v);
        }
        List<Section> sections = new ArrayList<Section>();
        if (visible.isEmpty()) return sections;

        boolean hasMode = false;
        for (Value<?> v : visible) {
            if (v instanceof Mode) { hasMode = true; break; }
        }

        if (visible.size() <= 2) {
            sections.add(new Section(null, visible));
        } else if (hasMode) {
            List<Value<?>> general = new ArrayList<Value<?>>();
            List<Value<?>> advanced = new ArrayList<Value<?>>();
            // First the mode value
            for (Value<?> v : visible) {
                if (v instanceof Mode) general.add(v);
            }
            // Then up to 1 other value in General
            int added = 0;
            for (Value<?> v : visible) {
                if (!(v instanceof Mode) && added < 1) {
                    general.add(v);
                    added++;
                } else if (!(v instanceof Mode)) {
                    advanced.add(v);
                }
            }
            sections.add(new Section("General", general));
            if (!advanced.isEmpty()) sections.add(new Section("Advanced", advanced));
        } else {
            sections.add(new Section("Settings", visible));
        }

        // Mark the last section
        for (int i = 0; i < sections.size(); i++) {
            sections.get(i).isLast = (i == sections.size() - 1);
        }
        return sections;
    }

    private static final class Section {
        final String header;
        final List<Value<?>> values;
        boolean isLast;

        Section(String header, List<Value<?>> values) {
            this.header = header;
            this.values = values;
            this.isLast = false;
        }

        int headerHeight() {
            return header == null ? 0 : (int) SECTION_HEADER_H;
        }

        int visibleRows() {
            int count = 0;
            for (Value<?> v : values) if (v.isVisible()) count++;
            return count;
        }
    }
}
