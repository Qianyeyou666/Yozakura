package gq.yozakura.ui.click.yozakura;

import gq.yozakura.engine.font.FontLoaders;
import gq.yozakura.engine.render.ui.RenderServices;
import gq.yozakura.manager.ModuleManager;
import gq.yozakura.util.animation.AnimationState;
import gq.yozakura.util.animation.AnimationUtil;

/**
 * Titlebar of the Nether v2.1 ClickGUI.
 *
 * <p>Layout: brand (icon + name + version badge) on the left, action buttons
 * (palette + close) on the right. The titlebar background uses a subtle accent
 * gradient on top of {@link ClickGuiTheme#SIDEBAR}; a 1px accent line is drawn
 * at the bottom edge.
 */
public final class ClickGuiTitleBar {
    private static final float BRAND_ICON_SIZE = 30f;
    private static final float BRAND_ICON_RADIUS = ClickGuiTheme.R_SM;
    private static final float TB_BTN_SIZE = 32f;
    private static final float PAD = 18f;
    private static final float GAP = 12f;

    public interface ActionListener {
        void onPaletteClicked();
        void onCloseClicked();
    }

    private final AnimationState anim;
    private ActionListener listener;

    public ClickGuiTitleBar(AnimationState anim) {
        this.anim = anim;
    }

    public void setActionListener(ActionListener listener) {
        this.listener = listener;
    }

    /** Draws the titlebar inside the supplied bounds. */
    public void draw(float x, float y, float w, float h, int mouseX, int mouseY, float frameScale) {
        // Background — sidebar color with subtle accent wash on top (matches design gradient)
        RenderServices.shapes().rect(x, y, x + w, y + h, ClickGuiTheme.SIDEBAR);
        // Accent wash — top 40% of titlebar, diagonal feel via vertical gradient
        RenderServices.shapes().verticalGradient(x, y, x + w, y + h * 0.4f,
                ClickGuiTheme.withAlpha(ClickGuiTheme.accent(), 0x0F),
                ClickGuiTheme.withAlpha(ClickGuiTheme.accent(), 0x00));
        // Top inner highlight (1px white at 4% alpha) — glass effect
        RenderServices.shapes().horizontalGradient(x + 2f, y + 1f, x + w - 2f, y + 2f,
                ClickGuiTheme.withAlpha(0xFFFFFF, 0x00),
                ClickGuiTheme.withAlpha(0xFFFFFF, 0x0A));
        RenderServices.shapes().horizontalGradient(x + w / 2f, y + 1f, x + w - 2f, y + 2f,
                ClickGuiTheme.withAlpha(0xFFFFFF, 0x0A),
                ClickGuiTheme.withAlpha(0xFFFFFF, 0x00));

        // Bottom divider — accent line (stronger, matches design's 30% accent)
        RenderServices.shapes().horizontalGradient(x + PAD, y + h - 1, x + w - PAD, y + h,
                ClickGuiTheme.withAlpha(ClickGuiTheme.accent(), 0x00),
                ClickGuiTheme.withAlpha(ClickGuiTheme.accent(), 0x4D));
        RenderServices.shapes().horizontalGradient(x + w / 2f, y + h - 1, x + w - PAD, y + h,
                ClickGuiTheme.withAlpha(ClickGuiTheme.accent(), 0x4D),
                ClickGuiTheme.withAlpha(ClickGuiTheme.accent(), 0x00));

        // ===== Brand (left side) =====
        float brandX = x + PAD;
        float brandY = y + (h - BRAND_ICON_SIZE) / 2f;

        // Brand icon shadow (matches design: 0 4px 12px rgba(accent,.4))
        RenderServices.shapes().shadow(brandX, brandY, brandX + BRAND_ICON_SIZE, brandY + BRAND_ICON_SIZE,
                BRAND_ICON_RADIUS, ClickGuiTheme.withAlpha(ClickGuiTheme.accent(), 0x66), 4, 4f);

        // Brand icon — 135deg accent gradient (accent → accentHover)
        // Layered: base accent, then diagonal gradient overlay (accentHover top-left → transparent bottom-right)
        RenderServices.shapes().roundedWH(brandX, brandY, BRAND_ICON_SIZE, BRAND_ICON_SIZE,
                BRAND_ICON_RADIUS, ClickGuiTheme.accent());
        // Diagonal gradient overlay using corner colors (topLeft=accentHover, bottomRight=transparent)
        ClickGuiRenderContext.pushScissor(brandX, brandY, BRAND_ICON_SIZE, BRAND_ICON_SIZE);
        try {
            RenderServices.shapes().roundedGradient(brandX, brandY, brandX + BRAND_ICON_SIZE, brandY + BRAND_ICON_SIZE,
                    BRAND_ICON_RADIUS,
                    ClickGuiTheme.withAlpha(ClickGuiTheme.accentHover(), 0x80),
                    ClickGuiTheme.withAlpha(ClickGuiTheme.accentHover(), 0x20),
                    ClickGuiTheme.withAlpha(ClickGuiTheme.accentHover(), 0x20),
                    ClickGuiTheme.withAlpha(ClickGuiTheme.accentHover(), 0x00));
        } finally {
            ClickGuiRenderContext.popScissor();
        }
        // Inner top highlight (inset 0 1px 0 rgba(255,255,255,.25))
        RenderServices.shapes().horizontalGradient(brandX + 2f, brandY + 1f,
                brandX + BRAND_ICON_SIZE - 2f, brandY + 2f,
                ClickGuiTheme.withAlpha(0xFFFFFF, 0x00),
                ClickGuiTheme.withAlpha(0xFFFFFF, 0x40));
        RenderServices.shapes().horizontalGradient(brandX + BRAND_ICON_SIZE / 2f, brandY + 1f,
                brandX + BRAND_ICON_SIZE - 2f, brandY + 2f,
                ClickGuiTheme.withAlpha(0xFFFFFF, 0x40),
                ClickGuiTheme.withAlpha(0xFFFFFF, 0x00));

        // Logo — hand-drawn "layers" mark (white), matches design SVG (16px, stroke 2.5)
        ClickGuiIconShapes.drawLayers(brandX + BRAND_ICON_SIZE / 2f,
                brandY + BRAND_ICON_SIZE / 2f,
                16f, 1.67f, 0xFFFFFFFF);

        // Brand name
        float nameX = brandX + BRAND_ICON_SIZE + GAP - 2f;
        FontLoaders.BRICOLAGE16.drawString(ClickGuiTheme.BRAND_NAME, nameX, brandY + 6, ClickGuiTheme.FG);

        // Version badge
        String version = ClickGuiTheme.BRAND_VERSION;
        float nameW = FontLoaders.BRICOLAGE16.getStringWidth(ClickGuiTheme.BRAND_NAME);
        float badgeX = nameX + nameW + 8f;
        float badgeW = FontLoaders.MONO10.getStringWidth(version) + 14f;
        float badgeH = 16f;
        float badgeY = brandY + 6f;
        RenderServices.shapes().roundedBorderWH(badgeX, badgeY, badgeW, badgeH, 5f, 1f,
                ClickGuiTheme.accentDim(), ClickGuiTheme.withAlpha(ClickGuiTheme.accent(), 0x2E));
        FontLoaders.MONO10.drawString(version,
                badgeX + (badgeW - FontLoaders.MONO10.getStringWidth(version)) / 2f,
                badgeY + (badgeH - 10f) / 2f,
                ClickGuiTheme.accentHover());

        drawEnabledCount(x, y, w, h);

        // ===== Action buttons (right side) =====
        float closeX = x + w - PAD - TB_BTN_SIZE;
        float btnY = y + (h - TB_BTN_SIZE) / 2f;

        // Close button
        boolean closeHover = isHover(mouseX, mouseY, closeX, btnY, TB_BTN_SIZE);
        drawActionButton(closeX, btnY, closeHover, FontLoaders.ICON_XMARK, true, frameScale, "tb-close");

        // Palette button sits immediately to the left, matching the design reference.
        float paletteX = closeX - TB_BTN_SIZE - 6f;
        boolean paletteHover = isHover(mouseX, mouseY, paletteX, btnY, TB_BTN_SIZE);
        drawActionButton(paletteX, btnY, paletteHover, null, false, frameScale, "tb-palette");
    }

    private void drawActionButton(float x, float y, boolean hover, String iconChar,
                                   boolean danger, float frameScale, String key) {
        float hoverT = anim.eased(key + ":hover", hover ? 1f : 0f,
                ClickGuiTheme.SPRING_SPEED, frameScale, 0f, AnimationUtil.Ease.OUT_CUBIC);
        float scale = 1f + hoverT * 0.02f;
        float size = TB_BTN_SIZE * scale;
        float drawX = x + (TB_BTN_SIZE - size) / 2f;
        float drawY = y + (TB_BTN_SIZE - size) / 2f;

        int bg = ClickGuiTheme.blend(0x00000000, ClickGuiTheme.CARD, hoverT);
        int border = ClickGuiTheme.blend(0x00000000, ClickGuiTheme.BORDER, hoverT);
        int color;
        if (danger) {
            color = ClickGuiTheme.blend(ClickGuiTheme.FG_3, ClickGuiTheme.RED, hoverT);
            bg = ClickGuiTheme.blend(bg, ClickGuiTheme.withAlpha(ClickGuiTheme.RED, 0x1F), hoverT);
            border = ClickGuiTheme.blend(border, ClickGuiTheme.withAlpha(ClickGuiTheme.RED, 0x33), hoverT);
        } else {
            color = ClickGuiTheme.blend(ClickGuiTheme.FG_3, ClickGuiTheme.FG, hoverT);
        }
        if (hoverT > 0.01f) {
            RenderServices.shapes().roundedBorderWH(drawX, drawY, size, size, ClickGuiTheme.R_SM,
                    1f, bg, border);
        }
        if (iconChar == null) {
            ClickGuiIconShapes.drawPalette(drawX + size / 2f, drawY + size / 2f,
                    16f, 1.4f, color);
        } else if (FontLoaders.ICON_XMARK.equals(iconChar)) {
            ClickGuiIconShapes.drawClose(drawX + size / 2f, drawY + size / 2f,
                    16f, 1.4f, color);
        } else {
            FontLoaders.I16.drawString(iconChar,
                    drawX + (size - FontLoaders.I16.getStringWidth(iconChar)) / 2f,
                    drawY + (size - 14f) / 2f,
                    color);
        }
    }

    /** Handles mouse clicks inside the titlebar (action buttons only). Returns true if consumed. */
    public boolean mouseClicked(float x, float y, float w, float h, int mouseX, int mouseY, int button) {
        if (button != 0) return false;
        if (mouseY < y || mouseY > y + h) return false;
        if (listener == null) return false;

        float closeX = x + w - PAD - TB_BTN_SIZE;
        float btnY = y + (h - TB_BTN_SIZE) / 2f;
        float paletteX = closeX - TB_BTN_SIZE - 6f;
        if (isHover(mouseX, mouseY, paletteX, btnY, TB_BTN_SIZE)) {
            listener.onPaletteClicked();
            return true;
        }
        if (isHover(mouseX, mouseY, closeX, btnY, TB_BTN_SIZE)) {
            listener.onCloseClicked();
            return true;
        }
        return false;
    }

    private void drawEnabledCount(float x, float y, float w, float h) {
        int count = ModuleManager.getEnabledModules().size();
        String number = String.valueOf(count);
        String suffix = " active";
        float numberW = FontLoaders.MONO11.getStringWidth(number);
        float suffixW = FontLoaders.INTER12.getStringWidth(suffix);
        float pillW = 30f + numberW + suffixW;
        float pillH = 26f;
        float pillX = x + (w - pillW) / 2f;
        float pillY = y + (h - pillH) / 2f;
        RenderServices.shapes().roundedBorderWH(pillX, pillY, pillW, pillH, 13f, 1f,
                ClickGuiTheme.withAlpha(ClickGuiTheme.CARD, 0xCC), ClickGuiTheme.BORDER_2);
        RenderServices.shapes().circle(pillX + 12f, pillY + pillH / 2f, 0, 360, 3f,
                ClickGuiTheme.GREEN);
        RenderServices.shapes().circle(pillX + 12f, pillY + pillH / 2f, 0, 360, 6f,
                ClickGuiTheme.withAlpha(ClickGuiTheme.GREEN, 0x22));
        float textX = pillX + 22f;
        FontLoaders.MONO11.drawString(number, textX, pillY + 8f, ClickGuiTheme.FG);
        FontLoaders.INTER12.drawString(suffix, textX + numberW, pillY + 8f, ClickGuiTheme.FG_3);
    }

    private static boolean isHover(float mouseX, float mouseY, float x, float y, float size) {
        return mouseX >= x && mouseX <= x + size && mouseY >= y && mouseY <= y + size;
    }
}
