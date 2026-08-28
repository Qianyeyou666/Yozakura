package gq.yozakura.ui.click.yozakura;

import gq.yozakura.engine.render.ui.RenderServices;
import gq.yozakura.module.ModuleType;

/**
 * Hand-drawn SVG-style icons for the Nether v2.1 ClickGUI that have no matching
 * glyph in the icon font — the brand "layers" mark and the palette button.
 *
 * <p>All icons are drawn stroked (no fill) with round caps/joins, matching the
 * source SVG specifications. Coordinates are expressed in a 24x24 viewBox and
 * scaled to the requested size at draw time, centered on the given (cx, cy).
 */
final class ClickGuiIconShapes {
    private ClickGuiIconShapes() {
    }

    /**
     * The brand "layers" mark — three stacked isometric layers
     * (top closed diamond + two open chevrons). Matches the source SVG:
     * <pre>
     * &lt;path d="M12 2L2 7l10 5 10-5-10-5z"/&gt;
     * &lt;path d="M2 17l10 5 10-5"/&gt;
     * &lt;path d="M2 12l10 5 10-5"/&gt;
     * </pre>
     *
     * @param cx         center x in screen pixels
     * @param cy         center y in screen pixels
     * @param size       target icon size in pixels (maps the 24x24 viewBox)
     * @param strokeWidth stroke width in pixels
     * @param color      ARGB stroke color
     */
    static void drawLayers(float cx, float cy, float size, float strokeWidth, int color) {
        float s = size / 24f;
        // Top diamond (closed): (12,2)(2,7)(12,12)(22,7)(12,2)
        lineScaled(cx, cy, s, strokeWidth, color, 12, 2, 2, 7);
        lineScaled(cx, cy, s, strokeWidth, color, 2, 7, 12, 12);
        lineScaled(cx, cy, s, strokeWidth, color, 12, 12, 22, 7);
        lineScaled(cx, cy, s, strokeWidth, color, 22, 7, 12, 2);
        // Middle chevron: (2,12)(12,17)(22,12)
        lineScaled(cx, cy, s, strokeWidth, color, 2, 12, 12, 17);
        lineScaled(cx, cy, s, strokeWidth, color, 12, 17, 22, 12);
        // Bottom chevron: (2,17)(12,22)(22,17)
        lineScaled(cx, cy, s, strokeWidth, color, 2, 17, 12, 22);
        lineScaled(cx, cy, s, strokeWidth, color, 12, 22, 22, 17);
    }

    /**
     * Palette icon — a circle body with a thumb-hole notch at the top and two
     * small paint wells. Approximates the source SVG:
     * <pre>
     * &lt;circle cx="12" cy="12" r="10"/&gt;
     * &lt;path d="M12 2a10 10 0 0 1 0 20 7 7 0 0 1 0-14 4 4 0 0 0 0-6"/&gt;
     * </pre>
     *
     * @param cx         center x in screen pixels
     * @param cy         center y in screen pixels
     * @param size       target icon size in pixels (maps the 24x24 viewBox)
     * @param strokeWidth stroke width in pixels
     * @param color      ARGB stroke color
     */
    static void drawPalette(float cx, float cy, float size, float strokeWidth, int color) {
        float s = size / 24f;
        float r = 10f * s;
        // Outer circle (the palette body)
        RenderServices.shapes().circleOutline(cx, cy, r, strokeWidth, color);
        // Thumb hole — small circle outline near the top-center inside the body
        RenderServices.shapes().circleOutline(cx, cy - 4.5f * s, 2.1f * s, strokeWidth, color);
        // Two paint wells (small filled dots) so the icon reads clearly as a palette
        RenderServices.shapes().circle(cx - 3.5f * s, cy + 1.5f * s, 0, 360, 1.3f * s, color);
        RenderServices.shapes().circle(cx + 3.5f * s, cy + 1.5f * s, 0, 360, 1.3f * s, color);
    }

    static void drawSearch(float cx, float cy, float size, float strokeWidth, int color) {
        float s = size / 24f;
        RenderServices.shapes().circleOutline(cx - 2f * s, cy - 2f * s, 7f * s, strokeWidth, color);
        RenderServices.shapes().line(cx + 3f * s, cy + 3f * s, cx + 9f * s, cy + 9f * s,
                strokeWidth, color);
    }

    static void drawClose(float cx, float cy, float size, float strokeWidth, int color) {
        float r = size * 0.32f;
        RenderServices.shapes().line(cx - r, cy - r, cx + r, cy + r, strokeWidth, color);
        RenderServices.shapes().line(cx - r, cy + r, cx + r, cy - r, strokeWidth, color);
    }

    static void drawGear(float cx, float cy, float size, float strokeWidth, int color) {
        float s = size / 24f;
        RenderServices.shapes().circleOutline(cx, cy, 3f * s, strokeWidth, color);
        RenderServices.shapes().circleOutline(cx, cy, 7.5f * s, strokeWidth, color);
        for (int i = 0; i < 8; i++) {
            double angle = Math.PI * i / 4.0;
            float x1 = cx + (float) Math.cos(angle) * 8f * s;
            float y1 = cy + (float) Math.sin(angle) * 8f * s;
            float x2 = cx + (float) Math.cos(angle) * 10f * s;
            float y2 = cy + (float) Math.sin(angle) * 10f * s;
            RenderServices.shapes().line(x1, y1, x2, y2, strokeWidth, color);
        }
    }

    static void drawCategory(ModuleType type, float cx, float cy, float size, float strokeWidth, int color) {
        float s = size / 24f;
        if (type == ModuleType.Combat) {
            path(cx, cy, s, strokeWidth, color, 14.5f,17.5f, 3,6, 3,3, 6,3, 17.5f,14.5f);
            path(cx, cy, s, strokeWidth, color, 13,19, 19,13);
            path(cx, cy, s, strokeWidth, color, 16,16, 20,20, 19,21, 21,19);
        } else if (type == ModuleType.Movement) {
            path(cx, cy, s, strokeWidth, color, 13,4, 13,20);
            path(cx, cy, s, strokeWidth, color, 17,8, 13,4, 9,8);
            path(cx, cy, s, strokeWidth, color, 4,12, 11,12);
        } else if (type == ModuleType.Render) {
            path(cx, cy, s, strokeWidth, color, 2,12, 6,7, 12,5, 18,7, 22,12,
                    18,17, 12,19, 6,17, 2,12);
            RenderServices.shapes().circleOutline(cx, cy, 3f * s, strokeWidth, color);
        } else if (type == ModuleType.Player) {
            RenderServices.shapes().circleOutline(cx, cy - 5f * s, 4f * s, strokeWidth, color);
            path(cx, cy, s, strokeWidth, color, 4,21, 4,19, 6,16, 9,15, 15,15, 18,16, 20,19, 20,21);
        } else if (type == ModuleType.World) {
            RenderServices.shapes().circleOutline(cx, cy, 10f * s, strokeWidth, color);
            path(cx, cy, s, strokeWidth, color, 2,12, 22,12);
            path(cx, cy, s, strokeWidth, color, 12,2, 9,7, 8,12, 9,17, 12,22);
            path(cx, cy, s, strokeWidth, color, 12,2, 15,7, 16,12, 15,17, 12,22);
        } else {
            drawGear(cx, cy, size, strokeWidth, color);
        }
    }

    static void drawModule(String name, float cx, float cy, float size, float strokeWidth, int color) {
        String key = name == null ? "" : name.replace(" ", "").toLowerCase();
        float s = size / 24f;
        if (key.contains("killaura")) {
            RenderServices.shapes().circleOutline(cx, cy, 10f * s, strokeWidth, color);
            path(cx, cy, s, strokeWidth, color, 12,8, 12,16);
            path(cx, cy, s, strokeWidth, color, 8,12, 16,12);
        } else if (key.contains("velocity") || key.contains("sprint")) {
            drawCategory(ModuleType.Movement, cx, cy, size, strokeWidth, color);
        } else if (key.contains("aim")) {
            RenderServices.shapes().circleOutline(cx, cy, 10f * s, strokeWidth, color);
            RenderServices.shapes().circleOutline(cx, cy, 6f * s, strokeWidth, color);
            RenderServices.shapes().circleOutline(cx, cy, 2f * s, strokeWidth, color);
        } else if (key.contains("critical") || key.contains("speed")) {
            path(cx, cy, s, strokeWidth, color, 13,2, 3,14, 12,14, 11,22, 21,10, 12,10, 13,2);
        } else if (key.contains("reach")) {
            path(cx, cy, s, strokeWidth, color, 15,3, 21,3, 21,9);
            path(cx, cy, s, strokeWidth, color, 21,3, 14,10);
        } else if (key.contains("esp") || key.contains("render")) {
            drawCategory(ModuleType.Render, cx, cy, size, strokeWidth, color);
        } else {
            float r = 8f * s;
            RenderServices.shapes().line(cx - r, cy - r, cx + r, cy - r, strokeWidth, color);
            RenderServices.shapes().line(cx + r, cy - r, cx + r, cy + r, strokeWidth, color);
            RenderServices.shapes().line(cx + r, cy + r, cx - r, cy + r, strokeWidth, color);
            RenderServices.shapes().line(cx - r, cy + r, cx - r, cy - r, strokeWidth, color);
        }
    }

    private static void path(float cx, float cy, float scale, float strokeWidth, int color, float... points) {
        for (int i = 0; i + 3 < points.length; i += 2) {
            float x1 = cx + (points[i] - 12f) * scale;
            float y1 = cy + (points[i + 1] - 12f) * scale;
            float x2 = cx + (points[i + 2] - 12f) * scale;
            float y2 = cy + (points[i + 3] - 12f) * scale;
            RenderServices.shapes().line(x1, y1, x2, y2, strokeWidth, color);
        }
    }

    private static void lineScaled(float cx, float cy, float s, float strokeWidth, int color,
                                   float x1, float y1, float x2, float y2) {
        // Map the 24x24 viewBox so its center (12,12) lands on (cx, cy)
        float px1 = cx + (x1 - 12f) * s;
        float py1 = cy + (y1 - 12f) * s;
        float px2 = cx + (x2 - 12f) * s;
        float py2 = cy + (y2 - 12f) * s;
        RenderServices.shapes().line(px1, py1, px2, py2, strokeWidth, color);
    }
}
