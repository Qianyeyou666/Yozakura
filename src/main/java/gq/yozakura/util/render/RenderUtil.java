package gq.yozakura.util.render;

import gq.yozakura.engine.render.Blur;
import gq.yozakura.engine.render.GLStateManager;
import gq.yozakura.engine.render.ShaderRenderer;
import gq.yozakura.engine.render.ui.RenderServices;
import gq.yozakura.util.time.TimerUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.texture.TextureUtil;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.BlockPos;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.Display;
import org.lwjgl.opengl.GL11;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import javax.imageio.ImageIO;

public class RenderUtil {
    public static Minecraft mc = Minecraft.getMinecraft();

    private static final int DEFAULT_ARC_SEGMENTS = 24;
    private static final double[] DEGREE_SIN = new double[361];
    private static final double[] DEGREE_COS = new double[361];

    static {
        for (int i = 0; i < DEGREE_SIN.length; i++) {
            double radians = Math.toRadians(i);
            DEGREE_SIN[i] = Math.sin(radians);
            DEGREE_COS[i] = Math.cos(radians);
        }
    }

    // --- Fields merged from RenderUtils ---
    private static final AxisAlignedBB DEFAULT_AABB = new AxisAlignedBB(0.0, 0.0, 0.0, 1.0, 1.0, 1.0);
    private static final Map<Integer, Boolean> glCapMap = new HashMap<>();
    public static TimerUtil splashTimer = new TimerUtil();
    public static int splashTickPos = 0;
    public static boolean isSplash = false;

    // --- Scissor state (merged from GuiRenderUtils) ---
    private static float scissorX, scissorY, scissorWidth, scissorHeight, scissorSF;
    private static boolean isScissoring;

    // ==================== Color & State Utilities ====================

    public static void resetColor() {
        RenderServices.context().resetColor();
    }

    public static void start() {
        RenderServices.context().start2D();
    }

    public static void stop() {
        RenderServices.context().stop2D();
    }

    public static void scaleStart(float x, float y, float scale) {
        RenderServices.context().scaleStart(x, y, scale);
    }

    public static void scaleEnd() {
        RenderServices.context().scaleEnd();
    }

    public static int applyOpacity(int color, float opacity) {
        return RenderServices.context().applyOpacity(color, opacity);
    }

    public static Color applyOpacity(Color color, float opacity) {
        return RenderServices.context().applyOpacity(color, opacity);
    }

    public static int applyAlpha(int color, int alpha) {
        return (color & 0x00FFFFFF) | (Math.max(0, Math.min(255, alpha)) << 24);
    }

    public static int getAlpha(int color) {
        return color >>> 24 & 255;
    }

    public static int reAlpha(int color, float alpha) {
        return applyAlpha(color, Math.round(GLStateManager.clamp01(alpha) * 255.0f));
    }

    public static void glColor(int hex) {
        float alpha = (hex >> 24 & 0xFF) / 255.0F;
        float red = (hex >> 16 & 0xFF) / 255.0F;
        float green = (hex >> 8 & 0xFF) / 255.0F;
        float blue = (hex & 0xFF) / 255.0F;
        GL11.glColor4f(red, green, blue, alpha);
    }

    public static void glColor(float alpha, int redRGB, int greenRGB, int blueRGB) {
        GL11.glColor4f(redRGB / 255.0f, greenRGB / 255.0f, blueRGB / 255.0f, GLStateManager.clamp01(alpha));
    }

    // ==================== Hover Detection ====================

    public static boolean isHovering(float mouseX, float mouseY, float xLeft, float yUp, float xRight, float yBottom) {
        return mouseX > xLeft && mouseX < xRight && mouseY > yUp && mouseY < yBottom;
    }

    public static boolean isHoveringBound(float mouseX, float mouseY, float xLeft, float yUp, float width, float height) {
        return mouseX > xLeft && mouseX < xLeft + width && mouseY > yUp && mouseY < yUp + height;
    }

    // ==================== Basic Shape Drawing ====================

    public static void drawRect(float left, float top, float right, float bottom, int color) {
        if (getAlpha(color) <= 0) {
            return;
        }
        normalizeRect(Rect.tmp, left, top, right, bottom);
        GLStateManager.begin2D();
        try {
            if (!ShaderRenderer.drawRect(Rect.tmp.left, Rect.tmp.top, Rect.tmp.right, Rect.tmp.bottom, color)) {
                fillRectRaw(Rect.tmp.left, Rect.tmp.top, Rect.tmp.right, Rect.tmp.bottom, color);
            }
        } finally {
            GLStateManager.end2D();
        }
    }

    public static void drawRoundedRect(float left, float top, float right, float bottom, int color) {
        drawRoundedRect(left, top, right, bottom, 2.0f, color);
    }

    public static void drawRoundedRect(float x, float y, float x2, float y2, final int borderColor, final int fillColor) {
        drawRoundedBorderedRect(x, y, x2, y2, 3.0f, 1.0f, fillColor, borderColor);
    }

    public static void drawRoundedRect(float x, float y, float x2, float y2, float round, int color) {
        if (getAlpha(color) <= 0) {
            return;
        }
        normalizeRect(Rect.tmp, x, y, x2, y2);
        float radius = GLStateManager.clampRadius(round, Rect.tmp.width(), Rect.tmp.height());
        GLStateManager.begin2D();
        try {
            if (!ShaderRenderer.drawRoundedRect(Rect.tmp.left, Rect.tmp.top, Rect.tmp.right, Rect.tmp.bottom, radius, color)) {
                roundedRectRaw(Rect.tmp.left, Rect.tmp.top, Rect.tmp.right, Rect.tmp.bottom, radius, color);
            }
        } finally {
            GLStateManager.end2D();
        }
    }

    /**
     * Draws a per-corner rounded rectangle for touching translucent surfaces. Its shader
     * antialiases inside the exact supplied bounds, so shared edges stay smooth without
     * feathering into the neighboring row and blending the same pixels twice.
     */
    public static void drawJoinedRoundedRect(float x, float y, float x2, float y2,
                                             float topLeftRadius, float topRightRadius,
                                             float bottomRightRadius, float bottomLeftRadius,
                                             int color) {
        drawJoinedRoundedRect(x, y, x2, y2,
                topLeftRadius, topRightRadius, bottomRightRadius, bottomLeftRadius,
                1.0F, 0.0F, 1.0F, 0.0F,
                1.0F, 0.0F, 1.0F, 0.0F, color);
    }

    public static void drawJoinedRoundedRect(float x, float y, float x2, float y2,
                                             float topLeftRadius, float topRightRadius,
                                             float bottomRightRadius, float bottomLeftRadius,
                                             float topJoinStart, float topJoinEnd,
                                             float bottomJoinStart, float bottomJoinEnd, int color) {
        drawJoinedRoundedRect(x, y, x2, y2,
                topLeftRadius, topRightRadius, bottomRightRadius, bottomLeftRadius,
                topJoinStart, topJoinEnd, bottomJoinStart, bottomJoinEnd,
                1.0F, 0.0F, 1.0F, 0.0F, color);
    }

    /**
     * Draws an antialiased rounded surface with independently joined intervals on all four sides.
     * Horizontal ranges are measured from the left edge; vertical ranges are measured from the top.
     */
    public static void drawJoinedRoundedRect(float x, float y, float x2, float y2,
                                             float topLeftRadius, float topRightRadius,
                                             float bottomRightRadius, float bottomLeftRadius,
                                             float topJoinStart, float topJoinEnd,
                                             float bottomJoinStart, float bottomJoinEnd,
                                             float leftJoinStart, float leftJoinEnd,
                                             float rightJoinStart, float rightJoinEnd, int color) {
        if (getAlpha(color) <= 0) {
            return;
        }
        normalizeRect(Rect.tmp, x, y, x2, y2);
        float maximumRadius = Math.min(Rect.tmp.width(), Rect.tmp.height()) * 0.5F;
        float topLeft = clampCornerRadius(topLeftRadius, maximumRadius);
        float topRight = clampCornerRadius(topRightRadius, maximumRadius);
        float bottomRight = clampCornerRadius(bottomRightRadius, maximumRadius);
        float bottomLeft = clampCornerRadius(bottomLeftRadius, maximumRadius);
        GLStateManager.begin2D();
        try {
            if (!ShaderRenderer.drawJoinedRoundedRect(
                    Rect.tmp.left, Rect.tmp.top, Rect.tmp.right, Rect.tmp.bottom,
                    topLeft, topRight, bottomRight, bottomLeft,
                    topJoinStart, topJoinEnd, bottomJoinStart, bottomJoinEnd,
                    leftJoinStart, leftJoinEnd, rightJoinStart, rightJoinEnd, color)) {
                joinedRoundedRectRaw(Rect.tmp.left, Rect.tmp.top, Rect.tmp.right, Rect.tmp.bottom,
                        topLeft, topRight, bottomRight, bottomLeft, color);
            }
        } finally {
            GLStateManager.end2D();
        }
    }

    public static void drawRoundRect(float x, float y, float x1, float y1, int color) {
        drawRoundedRect(x, y, x1, y1, 3.0f, color);
    }

    public static void drawFastRoundedRect(int left, float top, int right, float bottom, float radius, int color) {
        drawRoundedRect(left, top, right, bottom, radius, color);
    }

    public static void drawBorderedRect(float left, float top, float right, float bottom, float thickness, int color) {
        if (thickness <= 0.0f || getAlpha(color) <= 0) {
            return;
        }
        drawRect(left - thickness, top, left, bottom, color);
        drawRect(right, top, right + thickness, bottom, color);
        drawRect(left, top - thickness, right, top, color);
        drawRect(left, bottom, right, bottom + thickness, color);
    }

    public static void drawOutlinedRect(float x, float y, float width, float height, float lineSize, int lineColor) {
        drawRect(x, y, width, y + lineSize, lineColor);
        drawRect(x, height - lineSize, width, height, lineColor);
        drawRect(x, y + lineSize, x + lineSize, height - lineSize, lineColor);
        drawRect(width - lineSize, y + lineSize, width, height - lineSize, lineColor);
    }

    public static void drawRoundedBorderedRect(float x, float y, float x2, float y2, float radius, float borderWidth, int fillColor, int borderColor) {
        normalizeRect(Rect.tmp, x, y, x2, y2);
        float width = Rect.tmp.width();
        float height = Rect.tmp.height();
        radius = GLStateManager.clampRadius(radius, width, height);
        if (borderWidth <= 0.0f || getAlpha(borderColor) <= 0) {
            drawRoundedRect(Rect.tmp.left, Rect.tmp.top, Rect.tmp.right, Rect.tmp.bottom, radius, fillColor);
            return;
        }
        borderWidth = Math.min(borderWidth, Math.min(width, height) / 2.0f);
        GLStateManager.begin2D();
        try {
            if (!ShaderRenderer.drawRoundedBorderedRect(Rect.tmp.left, Rect.tmp.top, Rect.tmp.right, Rect.tmp.bottom,
                    radius, borderWidth, fillColor, borderColor)) {
                roundedRectRaw(Rect.tmp.left, Rect.tmp.top, Rect.tmp.right, Rect.tmp.bottom, radius, borderColor);
                if (getAlpha(fillColor) > 0 && width > borderWidth * 2.0f && height > borderWidth * 2.0f) {
                    roundedRectRaw(Rect.tmp.left + borderWidth, Rect.tmp.top + borderWidth,
                            Rect.tmp.right - borderWidth, Rect.tmp.bottom - borderWidth,
                            Math.max(0.0f, radius - borderWidth), fillColor);
                }
            }
        } finally {
            GLStateManager.end2D();
        }
    }

    public static void drawGradientBorderedRect(float x, float y, float x2, float y2, float radius, float borderWidth,
                                                int fillColor, int leftBorderColor, int rightBorderColor) {
        normalizeRect(Rect.tmp, x, y, x2, y2);
        drawRoundedBorderedRect(Rect.tmp.left, Rect.tmp.top, Rect.tmp.right, Rect.tmp.bottom, radius, borderWidth, fillColor, leftBorderColor);
        if (borderWidth > 0.0f) {
            drawHorizontalGradientRect(Rect.tmp.left, Rect.tmp.top, Rect.tmp.right, Rect.tmp.top + borderWidth, leftBorderColor, rightBorderColor);
        }
    }

    public static void layeredRect(float right, float bottom, float x2, float y2, int outline, int inline, int background) {
        drawRect(right, bottom, x2, y2, outline);
        drawRect(right + 0.5f, bottom + 0.5f, x2 - 0.5f, y2 - 0.5f, inline);
        drawRect(right + 1.0f, bottom + 1.0f, x2 - 1.0f, y2 - 1.0f, background);
    }

    public static void drawHLine(float x, float y, float right, int bottom) {
        drawRect(Math.min(x, y), right, Math.max(x, y) + 1.0f, right + 1.0f, bottom);
    }

    public static void drawVLine(float x, float y, float right, int bottom) {
        drawRect(x, Math.min(y, right), x + 1.0f, Math.max(y, right), bottom);
    }

    // ==================== Gradient Drawing ====================

    public static void drawGradientRect(float x, float y, float x1, float y1, int topColor, int bottomColor) {
        drawVerticalGradientRect(x, y, x1, y1, topColor, bottomColor);
    }

    public static void drawVerticalGradientRect(float x, float y, float x2, float y2, int topColor, int bottomColor) {
        if (getAlpha(topColor) <= 0 && getAlpha(bottomColor) <= 0) {
            return;
        }
        normalizeRect(Rect.tmp, x, y, x2, y2);
        GLStateManager.begin2D();
        try {
            if (!ShaderRenderer.drawGradientRect(Rect.tmp.left, Rect.tmp.top, Rect.tmp.right, Rect.tmp.bottom,
                    topColor, bottomColor, topColor, bottomColor)) {
                GL11.glShadeModel(GL11.GL_SMOOTH);
                GL11.glBegin(GL11.GL_QUADS);
                glColor(topColor);
                GL11.glVertex2f(Rect.tmp.left, Rect.tmp.top);
                GL11.glVertex2f(Rect.tmp.right, Rect.tmp.top);
                glColor(bottomColor);
                GL11.glVertex2f(Rect.tmp.right, Rect.tmp.bottom);
                GL11.glVertex2f(Rect.tmp.left, Rect.tmp.bottom);
                GL11.glEnd();
                GL11.glShadeModel(GL11.GL_FLAT);
            }
        } finally {
            GLStateManager.end2D();
        }
    }

    public static void drawHorizontalGradientRect(float x, float y, float x2, float y2, int leftColor, int rightColor) {
        if (getAlpha(leftColor) <= 0 && getAlpha(rightColor) <= 0) {
            return;
        }
        normalizeRect(Rect.tmp, x, y, x2, y2);
        GLStateManager.begin2D();
        try {
            if (!ShaderRenderer.drawGradientRect(Rect.tmp.left, Rect.tmp.top, Rect.tmp.right, Rect.tmp.bottom,
                    leftColor, leftColor, rightColor, rightColor)) {
                GL11.glShadeModel(GL11.GL_SMOOTH);
                GL11.glBegin(GL11.GL_QUADS);
                glColor(leftColor);
                GL11.glVertex2f(Rect.tmp.left, Rect.tmp.top);
                GL11.glVertex2f(Rect.tmp.left, Rect.tmp.bottom);
                glColor(rightColor);
                GL11.glVertex2f(Rect.tmp.right, Rect.tmp.bottom);
                GL11.glVertex2f(Rect.tmp.right, Rect.tmp.top);
                GL11.glEnd();
                GL11.glShadeModel(GL11.GL_FLAT);
            }
        } finally {
            GLStateManager.end2D();
        }
    }
    public static void drawRoundedGradientRect(float x, float y, float x2, float y2, float radius,
                                               int topLeft, int bottomLeft, int topRight, int bottomRight) {
        if (getAlpha(topLeft) <= 0 && getAlpha(bottomLeft) <= 0
                && getAlpha(topRight) <= 0 && getAlpha(bottomRight) <= 0) {
            return;
        }
        normalizeRect(Rect.tmp, x, y, x2, y2);
        float width = Rect.tmp.width();
        float height = Rect.tmp.height();
        radius = GLStateManager.clampRadius(radius, width, height);
        GLStateManager.begin2D();
        try {
            if (!ShaderRenderer.drawRoundedGradientRect(Rect.tmp.left, Rect.tmp.top, Rect.tmp.right, Rect.tmp.bottom,
                    radius, topLeft, bottomLeft, topRight, bottomRight)) {
                GL11.glShadeModel(GL11.GL_SMOOTH);
                GL11.glBegin(GL11.GL_QUADS);
                glColor(topLeft);
                GL11.glVertex2f(Rect.tmp.left, Rect.tmp.top);
                glColor(bottomLeft);
                GL11.glVertex2f(Rect.tmp.left, Rect.tmp.bottom);
                glColor(bottomRight);
                GL11.glVertex2f(Rect.tmp.right, Rect.tmp.bottom);
                glColor(topRight);
                GL11.glVertex2f(Rect.tmp.right, Rect.tmp.top);
                GL11.glEnd();
                GL11.glShadeModel(GL11.GL_FLAT);
            }
        } finally {
            GLStateManager.end2D();
        }
    }


    public static void drawRoundedHueRect(float x, float y, float x2, float y2, float radius, float alpha) {
        if (alpha <= 0.0f) {
            return;
        }
        normalizeRect(Rect.tmp, x, y, x2, y2);
        float width = Rect.tmp.width();
        float height = Rect.tmp.height();
        radius = GLStateManager.clampRadius(radius, width, height);
        GLStateManager.begin2D();
        try {
            if (ShaderRenderer.drawRoundedHueRect(Rect.tmp.left, Rect.tmp.top, Rect.tmp.right, Rect.tmp.bottom,
                    radius, Math.max(0.0f, Math.min(1.0f, alpha)))) {
                return;
            }
        } finally {
            GLStateManager.end2D();
        }

        GLStateManager.pushScissor(Rect.tmp.left, Rect.tmp.top, width, height);
        try {
            int segments = 18;
            float segmentW = width / segments;
            for (int i = 0; i < segments; i++) {
                float left = Rect.tmp.left + i * segmentW;
                float right = i == segments - 1 ? Rect.tmp.right : left + segmentW + 0.5f;
                int start = Color.HSBtoRGB(i / (float) segments, 0.86f, 1.0f);
                int end = Color.HSBtoRGB((i + 1) / (float) segments, 0.86f, 1.0f);
                drawHorizontalGradientRect(left, Rect.tmp.top, right, Rect.tmp.bottom,
                        applyAlpha(start, Math.round(255.0f * alpha)),
                        applyAlpha(end, Math.round(255.0f * alpha)));
            }
            drawVerticalGradientRect(Rect.tmp.left, Rect.tmp.top, Rect.tmp.right, Rect.tmp.bottom,
                    applyAlpha(0x00FFFFFF, 0),
                    applyAlpha(0x000000, Math.round(148.0f * alpha)));
        } finally {
            GLStateManager.popScissor();
        }
    }

    public static void drawRoundedPaletteRect(float x, float y, float x2, float y2, float radius,
                                              float hue, float alpha) {
        if (alpha <= 0.0f) {
            return;
        }
        normalizeRect(Rect.tmp, x, y, x2, y2);
        float width = Rect.tmp.width();
        float height = Rect.tmp.height();
        radius = GLStateManager.clampRadius(radius, width, height);
        GLStateManager.begin2D();
        try {
            if (ShaderRenderer.drawRoundedPaletteRect(Rect.tmp.left, Rect.tmp.top, Rect.tmp.right, Rect.tmp.bottom,
                    radius, Math.max(0.0f, Math.min(1.0f, hue)), Math.max(0.0f, Math.min(1.0f, alpha)))) {
                return;
            }
        } finally {
            GLStateManager.end2D();
        }

        int hueColor = Color.HSBtoRGB(Math.max(0.0f, Math.min(1.0f, hue)), 1.0f, 1.0f);
        drawRoundedRect(Rect.tmp.left, Rect.tmp.top, Rect.tmp.right, Rect.tmp.bottom, radius,
                applyAlpha(hueColor, Math.round(255.0f * alpha)));
        drawRoundedGradientRect(Rect.tmp.left, Rect.tmp.top, Rect.tmp.right, Rect.tmp.bottom, radius,
                applyAlpha(0x00FFFFFF, Math.round(246.0f * alpha)),
                applyAlpha(0x00FFFFFF, Math.round(246.0f * alpha)),
                applyAlpha(0x00FFFFFF, 0),
                applyAlpha(0x00FFFFFF, 0));
        drawRoundedGradientRect(Rect.tmp.left, Rect.tmp.top, Rect.tmp.right, Rect.tmp.bottom, radius,
                applyAlpha(0x000000, 0),
                applyAlpha(0x000000, Math.round(226.0f * alpha)),
                applyAlpha(0x000000, 0),
                applyAlpha(0x000000, Math.round(226.0f * alpha)));
    }

    // ==================== Circle & Arc Drawing ====================

    public static void circle(float x, float y, float radius, int fill) {
        drawCircle(x, y, 0, 360, radius, fill);
    }

    public static void arc(float x, float y, float start, float end, float radius, int color) {
        arcEllipse(x, y, start, end, radius, radius, color);
    }

    public static void color(int color) {
        glColor(color);
    }

    public static void arcEllipse(float x, float y, float start, float end, float w, float h, int color) {
        if (w <= 0.0f || h <= 0.0f || getAlpha(color) <= 0) {
            return;
        }
        if (start > end) {
            float temp = start;
            start = end;
            end = temp;
        }
        if (Math.abs(w - h) < 0.001f && end - start >= 359.5f) {
            GLStateManager.begin2D();
            try {
                if (ShaderRenderer.drawCircle(x, y, w, color)) {
                    return;
                }
            } finally {
                GLStateManager.end2D();
            }
        }
        int segments = Math.max(12, Math.min(160, (int) Math.ceil((end - start) / 3.0f)));
        GLStateManager.begin2D();
        try {
            glColor(color);
            GL11.glBegin(GL11.GL_TRIANGLE_FAN);
            GL11.glVertex2f(x, y);
            for (int i = 0; i <= segments; i++) {
                float angle = start + (end - start) * i / segments;
                double radians = Math.toRadians(angle);
                GL11.glVertex2d(x + Math.cos(radians) * w, y + Math.sin(radians) * h);
            }
            GL11.glEnd();
        } finally {
            GLStateManager.end2D();
        }
    }

    public static void drawCircle(float x, float y, int start, int end, float radius, int color) {
        if (radius <= 0.0f || getAlpha(color) <= 0) {
            return;
        }
        int sweep = Math.abs(end - start);
        if (sweep >= 359) {
            GLStateManager.begin2D();
            try {
                if (ShaderRenderer.drawCircle(x, y, radius, color)) {
                    return;
                }
            } finally {
                GLStateManager.end2D();
            }
        }
        arcEllipse(x, y, start, end, radius, radius, color);
    }

    /**
     * Alternative circle drawing using polygon smoothing for anti-aliased edges.
     * Signature differs from {@link #drawCircle(float, float, int, int, float, int)} to coexist.
     */
    public static void drawCircle(double x, double y, double radius, int c) {
        GL11.glEnable(32925); // GL_MULTISAMPLE
        GL11.glEnable(2881); // GL_POLYGON_SMOOTH
        float alpha = (float) (c >> 24 & 255) / 255.0f;
        float red = (float) (c >> 16 & 255) / 255.0f;
        float green = (float) (c >> 8 & 255) / 255.0f;
        float blue = (float) (c & 255) / 255.0f;
        boolean blend = GL11.glIsEnabled(3042);
        boolean line = GL11.glIsEnabled(2848);
        boolean texture = GL11.glIsEnabled(3553);
        if (!blend) {
            GL11.glEnable(3042);
        }
        if (!line) {
            GL11.glEnable(2848);
        }
        if (texture) {
            GL11.glDisable(3553);
        }
        GL11.glBlendFunc(770, 771);
        GL11.glColor4f(red, green, blue, alpha);
        GL11.glBegin(9);
        int i = 0;
        while (i <= 360) {
            GL11.glVertex2d(x + DEGREE_SIN[i] * radius, y + DEGREE_COS[i] * radius);
            ++i;
        }
        GL11.glEnd();
        if (texture) {
            GL11.glEnable(3553);
        }
        if (!line) {
            GL11.glDisable(2848);
        }
        if (!blend) {
            GL11.glDisable(3042);
        }
        GL11.glDisable(GL11.GL_POLYGON_SMOOTH);
        GL11.glClear(0);
    }

    public static void drawCircleWithTexture(float cX, float cY, int start, int end, float radius, ResourceLocation res, int color) {
        if (res == null || radius <= 0.0f || getAlpha(color) <= 0) {
            return;
        }
        if (start > end) {
            int temp = start;
            start = end;
            end = temp;
        }
        int segments = Math.max(12, Math.min(160, (int) Math.ceil((end - start) / 3.0f)));
        GLStateManager.beginTextured2D(getAlpha(color) / 255.0f);
        try {
            bindTextureSafe(res);
            glColor(color);
            GL11.glBegin(GL11.GL_TRIANGLE_FAN);
            GL11.glTexCoord2f(0.5f, 0.5f);
            GL11.glVertex2f(cX, cY);
            for (int i = 0; i <= segments; i++) {
                float angle = start + (end - start) * i / (float) segments;
                double radians = Math.toRadians(angle);
                float sin = (float) Math.sin(radians);
                float cos = (float) Math.cos(radians);
                GL11.glTexCoord2f(cos * 0.5f + 0.5f, sin * 0.5f + 0.5f);
                GL11.glVertex2f(cX + cos * radius, cY + sin * radius);
            }
            GL11.glEnd();
        } finally {
            GLStateManager.endTextured2D();
        }
    }

    public static void drawCircleOutline(float x, float y, float radius, float lineWidth, int color) {
        drawArcOutline(x, y, radius, 0.0f, 360.0f, lineWidth, color);
    }

    public static void drawArcOutline(float x, float y, float radius, float start, float end, float lineWidth, int color) {
        if (radius <= 0.0f || lineWidth <= 0.0f || getAlpha(color) <= 0) {
            return;
        }
        if (start > end) {
            float temp = start;
            start = end;
            end = temp;
        }
        int segments = Math.max(12, Math.min(160, (int) Math.ceil((end - start) / 3.0f)));
        GLStateManager.begin2D();
        try {
            if (ShaderRenderer.drawArc(x, y, radius, start, end, lineWidth, color)) {
                return;
            }
            GL11.glLineWidth(lineWidth);
            glColor(color);
            GL11.glBegin(GL11.GL_LINE_STRIP);
            for (int i = 0; i <= segments; i++) {
                float angle = start + (end - start) * i / segments;
                double radians = Math.toRadians(angle);
                GL11.glVertex2d(x + Math.cos(radians) * radius, y + Math.sin(radians) * radius);
            }
            GL11.glEnd();
        } finally {
            GLStateManager.end2D();
        }
    }

    public static void drawCircleBadge(float centerX, float centerY, float radius, float ringWidth,
                                       float progress, int fillColor, int trackColor, int progressColor) {
        if (radius <= 0.0f || ringWidth <= 0.0f) {
            return;
        }
        float clampedProgress = GLStateManager.clamp01(progress);
        GLStateManager.begin2D();
        try {
            if (ShaderRenderer.drawCircleBadge(centerX, centerY, radius, ringWidth,
                    clampedProgress, fillColor, trackColor, progressColor)) {
                return;
            }
        } finally {
            GLStateManager.end2D();
        }

        drawCircle(centerX, centerY, 0, 360, Math.max(0.0f, radius - ringWidth), fillColor);
        drawCircleOutline(centerX, centerY, radius - ringWidth / 2.0f, ringWidth, trackColor);
        if (clampedProgress > 0.0f) {
            drawArcOutline(centerX, centerY, radius - ringWidth / 2.0f, -90.0f,
                    -90.0f + 360.0f * clampedProgress, ringWidth, progressColor);
        }
    }

    // ==================== Line Drawing ====================

    public static void drawLine(float x, float y, float x2, float y2, float lineWidth, int color) {
        if (lineWidth <= 0.0f || getAlpha(color) <= 0) {
            return;
        }
        GLStateManager.begin2D();
        try {
            if (ShaderRenderer.drawLine(x, y, x2, y2, lineWidth, color)) {
                return;
            }
            GL11.glLineWidth(lineWidth);
            glColor(color);
            GL11.glBegin(GL11.GL_LINES);
            GL11.glVertex2f(x, y);
            GL11.glVertex2f(x2, y2);
            GL11.glEnd();
        } finally {
            GLStateManager.end2D();
        }
    }

    // ==================== Progress Bar ====================

    public static void drawProgressBar(float x, float y, float x2, float y2, float radius, float progress, int backgroundColor, int fillColor) {
        float clampedProgress = GLStateManager.clamp01(progress);
        if (getAlpha(backgroundColor) > 0) {
            drawRoundedRect(x, y, x2, y2, radius, backgroundColor);
        }
        if (clampedProgress <= 0.0f || getAlpha(fillColor) <= 0) {
            return;
        }
        normalizeRect(Rect.tmp, x, y, x2, y2);
        float fillRight = Rect.tmp.left + Rect.tmp.width() * clampedProgress;
        GLStateManager.pushScissor(Rect.tmp.left, Rect.tmp.top, fillRight - Rect.tmp.left, Rect.tmp.height());
        try {
            drawRoundedRect(Rect.tmp.left, Rect.tmp.top, Rect.tmp.right, Rect.tmp.bottom, radius, fillColor);
        } finally {
            GLStateManager.popScissor();
        }
    }

    // ==================== Shadow & Frosted Glass ====================

    public static void drawSoftShadow(float x, float y, float x2, float y2, float radius, int color, int layers, float spread) {
        int alpha = getAlpha(color);
        if (alpha <= 0 || layers <= 0 || spread <= 0.0f) {
            return;
        }
        normalizeRect(Rect.tmp, x, y, x2, y2);
        float left = Rect.tmp.left;
        float top = Rect.tmp.top;
        float right = Rect.tmp.right;
        float bottom = Rect.tmp.bottom;
        float clampedRadius = GLStateManager.clampRadius(radius, right - left, bottom - top);

        GLStateManager.begin2D();
        try {
            if (ShaderRenderer.drawRoundedShadow(left, top, right, bottom, clampedRadius, spread, color)) {
                return;
            }
        } finally {
            GLStateManager.end2D();
        }

        for (int i = layers; i >= 1; i--) {
            float distance = i / (float) layers;
            float proximity = (layers - i + 1.0f) / layers;
            float offset = spread * distance;
            int layerAlpha = Math.max(1, Math.round(alpha * proximity * proximity * 0.5f));
            drawRoundedRect(left - offset, top - offset,
                    right + offset, bottom + offset,
                    clampedRadius + offset, applyAlpha(color, layerAlpha));
        }
    }

    public static void drawSoftShadowOffset(float x, float y, float x2, float y2, float radius,
                                            float offsetX, float offsetY, int color, int layers,
                                            float blurSpread) {
        drawSoftShadow(x + offsetX, y + offsetY, x2 + offsetX, y2 + offsetY,
                radius, color, layers, blurSpread);
    }

    public static void drawFrostedGlassRect(float x, float y, float x2, float y2, float radius,
                                            float borderWidth, int fillColor, int borderColor) {
        normalizeRect(Rect.tmp, x, y, x2, y2);
        float width = Rect.tmp.width();
        float height = Rect.tmp.height();
        radius = GLStateManager.clampRadius(radius, width, height);
        borderWidth = Math.max(0.0f, Math.min(borderWidth, Math.min(width, height) / 2.0f));

        GLStateManager.begin2D();
        try {
            if (Blur.drawBlur(Rect.tmp.left, Rect.tmp.top, Rect.tmp.right, Rect.tmp.bottom,
                    radius, borderWidth, fillColor, borderColor)) {
                return;
            }
            if (ShaderRenderer.drawFrostedGlass(Rect.tmp.left, Rect.tmp.top, Rect.tmp.right, Rect.tmp.bottom,
                    radius, borderWidth, fillColor, borderColor)) {
                return;
            }
        } finally {
            GLStateManager.end2D();
        }

        drawRoundedBorderedRect(Rect.tmp.left, Rect.tmp.top, Rect.tmp.right, Rect.tmp.bottom,
                radius, borderWidth, fillColor, borderColor);
        drawVerticalGradientRect(Rect.tmp.left + 1.0f, Rect.tmp.top + 1.0f,
                Rect.tmp.right - 1.0f, Rect.tmp.bottom - 1.0f,
                applyAlpha(0x00FFFFFF, Math.min(30, Math.max(0, getAlpha(fillColor) / 5))),
                applyAlpha(0x00000000, 0));
    }

    // ==================== Classpath Texture Cache ====================
    // Lunar Client's TextureManager can't find resources from VapuLite's JAR
    // because the JAR isn't registered as a Minecraft resource pack.
    // Fall back to loading textures directly from the classpath via GL.

    private static final Map<String, Integer> classpathTextureCache = new HashMap<>();

    private static int getClasspathTexture(String path) {
        Integer cached = classpathTextureCache.get(path);
        if (cached != null) {
            return cached;
        }
        try {
            InputStream stream = RenderUtil.class.getResourceAsStream("/" + path);
            if (stream == null) {
                return -1;
            }
            BufferedImage image = ImageIO.read(stream);
            stream.close();
            if (image == null) {
                return -1;
            }
            int id = GL11.glGenTextures();
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, id);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL11.GL_CLAMP);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL11.GL_CLAMP);
            TextureUtil.uploadTextureImage(id, image);
            classpathTextureCache.put(path, id);
            return id;
        } catch (Throwable ignored) {
            return -1;
        }
    }

    /**
     * Binds a texture. Classpath-first: Lunar's TextureManager silently swallows
     * IOExceptions from missing resources, so we always try the classpath before
     * falling back to Minecraft's TextureManager (for skins, vanilla textures).
     */
    private static void bindTextureSafe(ResourceLocation location) {
        String path = "assets/" + location.getResourceDomain() + "/textures/" + location.getResourcePath();
        int id = getClasspathTexture(path);
        if (id >= 0) {
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, id);
            return;
        }
        mc.getTextureManager().bindTexture(location);
    }

    // ==================== Image / Texture Drawing ====================

    public static void drawImage(ResourceLocation image, int x, int y, float width, float height, float alpha) {
        drawTexturedRect(image, x, y, x + width, y + height, alpha);
    }

    /**
     * Draws an image with a {@link Color} tint (merged from RenderUtils).
     */
    public static void drawImage(float x, float y, final int width, final int height, final ResourceLocation image, Color color) {
        GLStateManager.beginTextured2D(color.getAlpha() / 255.0f);
        try {
            GL11.glColor4f(color.getRed() / 255.0f, color.getGreen() / 255.0f, color.getBlue() / 255.0f, color.getAlpha() / 255.0f);
            bindTextureSafe(image);
            Gui.drawModalRectWithCustomSizedTexture((int) x, (int) y, 0.0f, 0.0f, width, height, (float) width, (float) height);
        } finally {
            GLStateManager.endTextured2D();
        }
    }

    public static void drawTexturedRect(ResourceLocation image, float x, float y, float x2, float y2, float alpha) {
        if (image == null || alpha <= 0.0f) {
            return;
        }
        normalizeRect(Rect.tmp, x, y, x2, y2);
        GLStateManager.beginTextured2D(alpha);
        try {
            bindTextureSafe(image);
            // Switch to linear filtering to remove aliasing
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
            GL11.glBegin(GL11.GL_QUADS);
            GL11.glTexCoord2f(0.0f, 0.0f);
            GL11.glVertex2f(Rect.tmp.left, Rect.tmp.top);
            GL11.glTexCoord2f(0.0f, 1.0f);
            GL11.glVertex2f(Rect.tmp.left, Rect.tmp.bottom);
            GL11.glTexCoord2f(1.0f, 1.0f);
            GL11.glVertex2f(Rect.tmp.right, Rect.tmp.bottom);
            GL11.glTexCoord2f(1.0f, 0.0f);
            GL11.glVertex2f(Rect.tmp.right, Rect.tmp.top);
            GL11.glEnd();
            // Restore default nearest filtering
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
        } finally {
            GLStateManager.endTextured2D();
        }
    }

    // ==================== 3D Overlay Drawing ====================

    public static void drawOutlinedBoundingBox(AxisAlignedBB aa) {
        if (aa == null) {
            return;
        }
        GL11.glBegin(GL11.GL_LINE_STRIP);
        GL11.glVertex3d(aa.minX, aa.minY, aa.minZ);
        GL11.glVertex3d(aa.maxX, aa.minY, aa.minZ);
        GL11.glVertex3d(aa.maxX, aa.minY, aa.maxZ);
        GL11.glVertex3d(aa.minX, aa.minY, aa.maxZ);
        GL11.glVertex3d(aa.minX, aa.minY, aa.minZ);
        GL11.glEnd();
        GL11.glBegin(GL11.GL_LINE_STRIP);
        GL11.glVertex3d(aa.minX, aa.maxY, aa.minZ);
        GL11.glVertex3d(aa.maxX, aa.maxY, aa.minZ);
        GL11.glVertex3d(aa.maxX, aa.maxY, aa.maxZ);
        GL11.glVertex3d(aa.minX, aa.maxY, aa.maxZ);
        GL11.glVertex3d(aa.minX, aa.maxY, aa.minZ);
        GL11.glEnd();
        GL11.glBegin(GL11.GL_LINES);
        GL11.glVertex3d(aa.minX, aa.minY, aa.minZ);
        GL11.glVertex3d(aa.minX, aa.maxY, aa.minZ);
        GL11.glVertex3d(aa.maxX, aa.minY, aa.minZ);
        GL11.glVertex3d(aa.maxX, aa.maxY, aa.minZ);
        GL11.glVertex3d(aa.maxX, aa.minY, aa.maxZ);
        GL11.glVertex3d(aa.maxX, aa.maxY, aa.maxZ);
        GL11.glVertex3d(aa.minX, aa.minY, aa.maxZ);
        GL11.glVertex3d(aa.minX, aa.maxY, aa.maxZ);
        GL11.glEnd();
    }

    public static void drawBoundingBox(AxisAlignedBB aa) {
        if (aa == null) {
            return;
        }
        GL11.glBegin(GL11.GL_QUADS);
        vertex(aa.minX, aa.minY, aa.minZ);
        vertex(aa.maxX, aa.minY, aa.minZ);
        vertex(aa.maxX, aa.minY, aa.maxZ);
        vertex(aa.minX, aa.minY, aa.maxZ);

        vertex(aa.minX, aa.maxY, aa.maxZ);
        vertex(aa.maxX, aa.maxY, aa.maxZ);
        vertex(aa.maxX, aa.maxY, aa.minZ);
        vertex(aa.minX, aa.maxY, aa.minZ);

        vertex(aa.minX, aa.minY, aa.maxZ);
        vertex(aa.maxX, aa.minY, aa.maxZ);
        vertex(aa.maxX, aa.maxY, aa.maxZ);
        vertex(aa.minX, aa.maxY, aa.maxZ);

        vertex(aa.minX, aa.maxY, aa.minZ);
        vertex(aa.maxX, aa.maxY, aa.minZ);
        vertex(aa.maxX, aa.minY, aa.minZ);
        vertex(aa.minX, aa.minY, aa.minZ);

        vertex(aa.minX, aa.minY, aa.minZ);
        vertex(aa.minX, aa.minY, aa.maxZ);
        vertex(aa.minX, aa.maxY, aa.maxZ);
        vertex(aa.minX, aa.maxY, aa.minZ);

        vertex(aa.maxX, aa.maxY, aa.minZ);
        vertex(aa.maxX, aa.maxY, aa.maxZ);
        vertex(aa.maxX, aa.minY, aa.maxZ);
        vertex(aa.maxX, aa.minY, aa.minZ);
        GL11.glEnd();
    }

    public static void drawEntityESP(double x, double y, double z, double width, double height, float red,
                                     float green, float blue, float alpha) {
        drawEntityESP(new AxisAlignedBB(x - width, y, z - width, x + width, y + height, z + width), red, green, blue, alpha);
    }

    public static void drawEntityESP(double x, double y, double z, double x1, double y1, double z1, float red,
                                     float green, float blue, float alpha) {
        drawEntityESP(new AxisAlignedBB(x, y, z, x1, y1, z1), red, green, blue, alpha);
    }

    public static void drawEntityESP(AxisAlignedBB axisAlignedBB, float red,
                                     float green, float blue, float alpha) {
        if (axisAlignedBB == null) {
            return;
        }
        begin3DOverlay(true);
        try {
            GL11.glLineWidth(1.0f);
            GL11.glColor4f(red, green, blue, 1.0f);
            drawOutlinedBoundingBox(axisAlignedBB);
            GL11.glColor4f(red, green, blue, alpha);
            drawBoundingBox(axisAlignedBB);
        } finally {
            end3DOverlay();
        }
    }

    /**
     * Draws an outlined entity ESP with custom line width (merged from RenderUtils).
     * This is a simpler outline-only variant compared to {@link #drawEntityESP}.
     */
    public static void drawOutlinedEntityESP(double x, double y, double z, double width, double height, float red,
                                             float green, float blue, float alpha) {
        GL11.glPushMatrix();
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(770, 771);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_LINE_SMOOTH);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glDepthMask(false);
        GL11.glLineWidth(1.5f);
        GL11.glColor4f(red, green, blue, alpha);
        drawOutlinedBoundingBox(new AxisAlignedBB(x - width, y, z - width, x + width, y + height, z + width));
        GL11.glDisable(GL11.GL_LINE_SMOOTH);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        GL11.glDepthMask(true);
        GL11.glDisable(GL11.GL_BLEND);
        GL11.glPopMatrix();
    }

    // ==================== World Block ESP Drawing ====================

    public static void drawBlockESP(BlockPos pos, float red, float green, float blue) {
        GL11.glPushMatrix();
        GL11.glEnable(3042);
        GL11.glBlendFunc(770, 771);
        GL11.glEnable(2848);
        GL11.glLineWidth(1.0f);
        GL11.glDisable(3553);
        GL11.glEnable(2884);
        GL11.glDisable(2929);
        GL11.glDisable(2896);
        double renderPosX = mc.getRenderManager().viewerPosX;
        double renderPosY = mc.getRenderManager().viewerPosY;
        double renderPosZ = mc.getRenderManager().viewerPosZ;
        GL11.glTranslated(-renderPosX, -renderPosY, -renderPosZ);
        GL11.glTranslated(pos.getX(), pos.getY(), pos.getZ());
        GL11.glColor4f(red, green, blue, 0.3f);
        drawSolidBox();
        GL11.glColor4f(red, green, blue, 0.7f);
        drawOutlinedBox();
        GL11.glColor4f(1.0f, 1.0f, 1.0f, 1.0f);
        GL11.glEnable(2896);
        GL11.glEnable(2929);
        GL11.glEnable(3553);
        GL11.glDisable(3042);
        GL11.glDisable(2848);
        GL11.glPopMatrix();
    }

    public static void drawSolidBox() {
        drawSolidBox(DEFAULT_AABB);
    }

    public static void drawSolidBox(AxisAlignedBB bb) {
        GL11.glBegin(7);
        GL11.glVertex3d(bb.minX, bb.minY, bb.minZ);
        GL11.glVertex3d(bb.maxX, bb.minY, bb.minZ);
        GL11.glVertex3d(bb.maxX, bb.minY, bb.maxZ);
        GL11.glVertex3d(bb.minX, bb.minY, bb.maxZ);
        GL11.glVertex3d(bb.minX, bb.maxY, bb.minZ);
        GL11.glVertex3d(bb.minX, bb.maxY, bb.maxZ);
        GL11.glVertex3d(bb.maxX, bb.maxY, bb.maxZ);
        GL11.glVertex3d(bb.maxX, bb.maxY, bb.minZ);
        GL11.glVertex3d(bb.minX, bb.minY, bb.minZ);
        GL11.glVertex3d(bb.minX, bb.maxY, bb.minZ);
        GL11.glVertex3d(bb.maxX, bb.maxY, bb.minZ);
        GL11.glVertex3d(bb.maxX, bb.minY, bb.minZ);
        GL11.glVertex3d(bb.maxX, bb.minY, bb.minZ);
        GL11.glVertex3d(bb.maxX, bb.maxY, bb.minZ);
        GL11.glVertex3d(bb.maxX, bb.maxY, bb.maxZ);
        GL11.glVertex3d(bb.maxX, bb.minY, bb.maxZ);
        GL11.glVertex3d(bb.minX, bb.minY, bb.maxZ);
        GL11.glVertex3d(bb.maxX, bb.minY, bb.maxZ);
        GL11.glVertex3d(bb.maxX, bb.maxY, bb.maxZ);
        GL11.glVertex3d(bb.minX, bb.maxY, bb.maxZ);
        GL11.glVertex3d(bb.minX, bb.minY, bb.minZ);
        GL11.glVertex3d(bb.minX, bb.minY, bb.maxZ);
        GL11.glVertex3d(bb.minX, bb.maxY, bb.maxZ);
        GL11.glVertex3d(bb.minX, bb.maxY, bb.minZ);
        GL11.glEnd();
    }

    public static void drawOutlinedBox() {
        drawOutlinedBox(DEFAULT_AABB);
    }

    public static void drawOutlinedBox(AxisAlignedBB bb) {
        GL11.glBegin(1);
        GL11.glVertex3d(bb.minX, bb.minY, bb.minZ);
        GL11.glVertex3d(bb.maxX, bb.minY, bb.minZ);
        GL11.glVertex3d(bb.maxX, bb.minY, bb.minZ);
        GL11.glVertex3d(bb.maxX, bb.minY, bb.maxZ);
        GL11.glVertex3d(bb.maxX, bb.minY, bb.maxZ);
        GL11.glVertex3d(bb.minX, bb.minY, bb.maxZ);
        GL11.glVertex3d(bb.minX, bb.minY, bb.maxZ);
        GL11.glVertex3d(bb.minX, bb.minY, bb.minZ);
        GL11.glVertex3d(bb.minX, bb.minY, bb.minZ);
        GL11.glVertex3d(bb.minX, bb.maxY, bb.minZ);
        GL11.glVertex3d(bb.maxX, bb.minY, bb.minZ);
        GL11.glVertex3d(bb.maxX, bb.maxY, bb.minZ);
        GL11.glVertex3d(bb.maxX, bb.minY, bb.maxZ);
        GL11.glVertex3d(bb.maxX, bb.maxY, bb.maxZ);
        GL11.glVertex3d(bb.minX, bb.minY, bb.maxZ);
        GL11.glVertex3d(bb.minX, bb.maxY, bb.maxZ);
        GL11.glVertex3d(bb.minX, bb.maxY, bb.minZ);
        GL11.glVertex3d(bb.maxX, bb.maxY, bb.minZ);
        GL11.glVertex3d(bb.maxX, bb.maxY, bb.minZ);
        GL11.glVertex3d(bb.maxX, bb.maxY, bb.maxZ);
        GL11.glVertex3d(bb.maxX, bb.maxY, bb.maxZ);
        GL11.glVertex3d(bb.minX, bb.maxY, bb.maxZ);
        GL11.glVertex3d(bb.minX, bb.maxY, bb.maxZ);
        GL11.glVertex3d(bb.minX, bb.maxY, bb.minZ);
        GL11.glEnd();
    }

    // ==================== Scissor ====================

    public static void doGlScissor(float x, float y, float windowWidth2, float windowHeight2) {
        GLStateManager.applyScissor(x, y, windowWidth2, windowHeight2);
    }

    // ==================== GL Capability Management ====================

    public static void clearCaps() {
        glCapMap.clear();
    }

    public static void enableGlCap(final int cap) {
        setGlCap(cap, true);
    }

    public static void enableGlCap(final int... caps) {
        for (final int cap : caps)
            setGlCap(cap, true);
    }

    public static void disableGlCap(final int cap) {
        setGlCap(cap, false);
    }

    public static void disableGlCap(final int... caps) {
        for (final int cap : caps)
            setGlCap(cap, false);
    }

    public static void setGlCap(final int cap, final boolean state) {
        glCapMap.put(cap, GL11.glGetBoolean(cap));
        setGlState(cap, state);
    }

    public static void setGlState(final int cap, final boolean state) {
        if (state)
            GL11.glEnable(cap);
        else
            GL11.glDisable(cap);
    }

    // ==================== Utility ====================

    public static String DF(double value, int maxvalue) {
        DecimalFormat df = new DecimalFormat("0", DecimalFormatSymbols.getInstance(Locale.ENGLISH));
        df.setMaximumFractionDigits(maxvalue);
        return df.format(value);
    }

    public static int width() {
        return new ScaledResolution(mc).getScaledWidth();
    }

    public static int height() {
        return new ScaledResolution(mc).getScaledHeight();
    }

    // ==================== Scissor / Crop (merged from GuiRenderUtils) ====================

    /**
     * Gets current scissor data
     * @return float[] of scissorX,scissorY,scissorWidth,scissorHeight,scissorSF or -1 for none
     */
    public static float[] getScissor() {
        if (isScissoring) {
            return new float[]{scissorX, scissorY, scissorWidth, scissorHeight, scissorSF};
        }
        return new float[]{-1};
    }

    public static void beginCrop(float x, float y, float width, float height) {
        float scaleFactor = getScaleFactor();
        beginCrop(x, y, width, height, scaleFactor);
    }

    public static void beginCropFixed(float x, float y, float width, float height) {
        float scaleFactor = getScaleFactor();
        beginCrop(x, y, width, height, scaleFactor);
    }

    public static void beginCrop(float x, float y, float width, float height, float scaleFactor) {
        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        GL11.glScissor((int) (x * scaleFactor), (int) (Display.getHeight() - (y + height) * scaleFactor), (int) (width * scaleFactor), (int) (height * scaleFactor));
        isScissoring = true;
        scissorX = x;
        scissorY = y;
        scissorWidth = width;
        scissorHeight = height;
        scissorSF = scaleFactor;
    }

    public static void endCrop() {
        GL11.glDisable(GL11.GL_SCISSOR_TEST);
        isScissoring = false;
    }

    // ==================== 3D Render Enable/Disable (merged from GuiRenderUtils) ====================

    public static void enableRender3D(boolean disableDepth) {
        if (disableDepth) {
            GL11.glDepthMask(false);
            GL11.glDisable(2929);
        }
        GL11.glDisable(3008);
        GL11.glEnable(3042);
        GL11.glDisable(3553);
        GL11.glBlendFunc(770, 771);
        GL11.glEnable(2848);
        GL11.glHint(3154, 4354);
        GL11.glLineWidth(1.0F);
    }

    public static void disableRender3D(boolean enableDepth) {
        if (enableDepth) {
            GL11.glDepthMask(true);
            GL11.glEnable(2929);
        }
        GL11.glEnable(3553);
        GL11.glDisable(3042);
        GL11.glEnable(3008);
        GL11.glDisable(2848);
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
    }

    public static void enableRender2D() {
        RenderServices.context().start2D();
        GL11.glLineWidth(1.0F);
    }

    public static void disableRender2D() {
        GlStateManager.shadeModel(GL11.GL_FLAT);
        RenderServices.context().stop2D();
    }

    // ==================== 2D Primitives (merged from GuiRenderUtils) ====================

    public static void setColor(int colorHex) {
        float alpha = (float) (colorHex >> 24 & 255) / 255.0F;
        float red = (float) (colorHex >> 16 & 255) / 255.0F;
        float green = (float) (colorHex >> 8 & 255) / 255.0F;
        float blue = (float) (colorHex & 255) / 255.0F;
        GL11.glColor4f(red, green, blue, alpha);
    }

    /**
     * Draws a filled rect defined by width/height (not right/bottom coords).
     * Renamed from drawRect to avoid collision with RenderUtil's coordinate-based drawRect.
     */
    public static void drawRectWH(float x, float y, float width, float height, int color) {
        RenderServices.shapes().rectWH(x, y, width, height, color);
    }

    public static void drawRect(float x, float y, float width, float height, Color color) {
        drawRectWH(x, y, width, height, color.getRGB());
    }

    public static void drawBorderedRect(float x, float y, float width, float height, float borderWidth, Color rectColor, Color borderColor) {
        drawBorderedRect(x, y, width, height, borderWidth, rectColor.getRGB(), borderColor.getRGB());
    }

    public static void drawBorderedRect(float x, float y, float width, float height, float borderWidth, int rectColor, int borderColor) {
        drawRectWH(x + borderWidth, y + borderWidth, width - borderWidth * 2.0F, height - borderWidth * 2.0F, rectColor);
        drawRectWH(x, y, width, borderWidth, borderColor);
        drawRectWH(x, y + borderWidth, borderWidth, height - borderWidth, borderColor);
        drawRectWH(x + width - borderWidth, y + borderWidth, borderWidth, height - borderWidth, borderColor);
        drawRectWH(x + borderWidth, y + height - borderWidth, width - borderWidth * 2.0F, borderWidth, borderColor);
    }

    public static void drawBorder(float x, float y, float width, float height, float borderWidth, int borderColor) {
        drawRectWH(x + borderWidth, y + borderWidth, width - borderWidth * 2.0F, borderWidth, borderColor);
        drawRectWH(x, y + borderWidth, borderWidth, height - borderWidth, borderColor);
        drawRectWH(x + width - borderWidth, y + borderWidth, borderWidth, height - borderWidth, borderColor);
        drawRectWH(x + borderWidth, y + height - borderWidth, width - borderWidth * 2.0F, borderWidth, borderColor);
    }

    public static void drawRoundedRect(float x, float y, float width, float height, float edgeRadius, int color, float borderWidth, int borderColor) {
        if (color == 16777215) color = 0xFFF;
        if (borderColor == 16777215) borderColor = 0xFFF;
        RenderServices.shapes().roundedBorderWH(x, y, width, height, edgeRadius, borderWidth, color, borderColor);
    }

    public static void drawImageSpread(ResourceLocation image, float x, float y, float width, float height, float alpha) {
        GLStateManager.beginTextured2D(alpha);
        try {
            bindTextureSafe(image);
            Gui.drawModalRectWithCustomSizedTexture((int) x, (int) y, 0.0f, 0.0f, (int) width, (int) height, 25, 25);
        } finally {
            GLStateManager.endTextured2D();
        }
    }

    // ==================== Circle Drawing Variants (merged from GuiRenderUtils) ====================

    /** Draws a circle outline with configurable line width. */
    public static void drawCircle(float x, float y, float radius, float lineWidth, int color) {
        enableRender2D();
        setColor(color);
        GL11.glLineWidth(lineWidth);
        int vertices = (int) Math.min(Math.max(radius, 45.0F), 360.0F);
        GL11.glBegin(2);
        for (int i = 0; i < vertices; ++i) {
            double angleRadians = 6.283185307179586D * (double) i / (double) vertices;
            GL11.glVertex2d((double) x + Math.sin(angleRadians) * (double) radius, (double) y + Math.cos(angleRadians) * (double) radius);
        }
        GL11.glEnd();
        disableRender2D();
    }

    public static void drawFilledCircle(float x, float y, float radius, int color) {
        enableRender2D();
        setColor(color);
        int vertices = (int) Math.min(Math.max(radius, 45.0F), 360.0F);
        GL11.glBegin(9);
        for (int i = 0; i < vertices; ++i) {
            double angleRadians = 6.283185307179586D * (double) i / (double) vertices;
            GL11.glVertex2d((double) x + Math.sin(angleRadians) * (double) radius, (double) y + Math.cos(angleRadians) * (double) radius);
        }
        GL11.glEnd();
        disableRender2D();
        drawCircle(x, y, radius, 1.5F, 16777215);
    }

    public static void drawFilledCircleNoBorder(float x, float y, float radius, int color) {
        enableRender2D();
        setColor(color);
        int vertices = (int) Math.min(Math.max(radius, 45.0F), 360.0F);
        GL11.glBegin(9);
        for (int i = 0; i < vertices; ++i) {
            double angleRadians = 6.283185307179586D * (double) i / (double) vertices;
            GL11.glVertex2d((double) x + Math.sin(angleRadians) * (double) radius, (double) y + Math.cos(angleRadians) * (double) radius);
        }
        GL11.glEnd();
        disableRender2D();
    }

    // ==================== 3D Line Drawing (merged from GuiRenderUtils) ====================

    public static void drawLine3D(double x1, double y1, double z1, double x2, double y2, double z2, int color) {
        drawLine3D(x1, y1, z1, x2, y2, z2, color, true);
    }

    public static void drawLine3D(double x1, double y1, double z1, double x2, double y2, double z2, int color, boolean disableDepth) {
        enableRender3D(disableDepth);
        setColor(color);
        GL11.glBegin(1);
        GL11.glVertex3d(x1, y1, z1);
        GL11.glVertex3d(x2, y2, z2);
        GL11.glEnd();
        disableRender3D(disableDepth);
    }

    public static void drawLine2D(double x1, double y1, double x2, double y2, float width, int color) {
        enableRender2D();
        setColor(color);
        GL11.glLineWidth(width);
        GL11.glBegin(1);
        GL11.glVertex2d(x1, y1);
        GL11.glVertex2d(x2, y2);
        GL11.glEnd();
        disableRender2D();
    }

    public static void drawPoint(int x, int y, float size, int color) {
        enableRender2D();
        setColor(color);
        GL11.glPointSize(size);
        GL11.glEnable(2832);
        GL11.glBegin(0);
        GL11.glVertex2d((double) x, (double) y);
        GL11.glEnd();
        GL11.glDisable(2832);
        disableRender2D();
    }

    // ==================== 3D Box Drawing (merged from GuiRenderUtils) ====================

    public static void drawOutlinedBox(AxisAlignedBB boundingBox, int color) {
        drawOutlinedBox(boundingBox, color, true);
    }

    public static void drawOutlinedBox(AxisAlignedBB boundingBox, int color, boolean disableDepth) {
        if (boundingBox != null) {
            enableRender3D(disableDepth);
            setColor(color);
            GL11.glBegin(3);
            GL11.glVertex3d(boundingBox.minX, boundingBox.minY, boundingBox.minZ);
            GL11.glVertex3d(boundingBox.maxX, boundingBox.minY, boundingBox.minZ);
            GL11.glVertex3d(boundingBox.maxX, boundingBox.minY, boundingBox.maxZ);
            GL11.glVertex3d(boundingBox.minX, boundingBox.minY, boundingBox.maxZ);
            GL11.glVertex3d(boundingBox.minX, boundingBox.minY, boundingBox.minZ);
            GL11.glEnd();
            GL11.glBegin(3);
            GL11.glVertex3d(boundingBox.minX, boundingBox.maxY, boundingBox.minZ);
            GL11.glVertex3d(boundingBox.maxX, boundingBox.maxY, boundingBox.minZ);
            GL11.glVertex3d(boundingBox.maxX, boundingBox.maxY, boundingBox.maxZ);
            GL11.glVertex3d(boundingBox.minX, boundingBox.maxY, boundingBox.maxZ);
            GL11.glVertex3d(boundingBox.minX, boundingBox.maxY, boundingBox.minZ);
            GL11.glEnd();
            GL11.glBegin(1);
            GL11.glVertex3d(boundingBox.minX, boundingBox.minY, boundingBox.minZ);
            GL11.glVertex3d(boundingBox.minX, boundingBox.maxY, boundingBox.minZ);
            GL11.glVertex3d(boundingBox.maxX, boundingBox.minY, boundingBox.minZ);
            GL11.glVertex3d(boundingBox.maxX, boundingBox.maxY, boundingBox.minZ);
            GL11.glVertex3d(boundingBox.maxX, boundingBox.minY, boundingBox.maxZ);
            GL11.glVertex3d(boundingBox.maxX, boundingBox.maxY, boundingBox.maxZ);
            GL11.glVertex3d(boundingBox.minX, boundingBox.minY, boundingBox.maxZ);
            GL11.glVertex3d(boundingBox.minX, boundingBox.maxY, boundingBox.maxZ);
            GL11.glEnd();
            disableRender3D(disableDepth);
        }
    }

    public static void drawBox(AxisAlignedBB boundingBox, int color) {
        drawBox(boundingBox, color, true);
    }

    public static void drawBox(AxisAlignedBB boundingBox, int color, boolean disableDepth) {
        if (boundingBox != null) {
            enableRender3D(disableDepth);
            setColor(color);
            GL11.glBegin(7);
            GL11.glVertex3d(boundingBox.minX, boundingBox.minY, boundingBox.maxZ);
            GL11.glVertex3d(boundingBox.maxX, boundingBox.minY, boundingBox.maxZ);
            GL11.glVertex3d(boundingBox.maxX, boundingBox.maxY, boundingBox.maxZ);
            GL11.glVertex3d(boundingBox.minX, boundingBox.maxY, boundingBox.maxZ);
            GL11.glEnd();
            GL11.glBegin(7);
            GL11.glVertex3d(boundingBox.maxX, boundingBox.minY, boundingBox.maxZ);
            GL11.glVertex3d(boundingBox.minX, boundingBox.minY, boundingBox.maxZ);
            GL11.glVertex3d(boundingBox.minX, boundingBox.maxY, boundingBox.maxZ);
            GL11.glVertex3d(boundingBox.maxX, boundingBox.maxY, boundingBox.maxZ);
            GL11.glEnd();
            GL11.glBegin(7);
            GL11.glVertex3d(boundingBox.minX, boundingBox.minY, boundingBox.minZ);
            GL11.glVertex3d(boundingBox.minX, boundingBox.minY, boundingBox.maxZ);
            GL11.glVertex3d(boundingBox.minX, boundingBox.maxY, boundingBox.maxZ);
            GL11.glVertex3d(boundingBox.minX, boundingBox.maxY, boundingBox.minZ);
            GL11.glEnd();
            GL11.glBegin(7);
            GL11.glVertex3d(boundingBox.minX, boundingBox.minY, boundingBox.maxZ);
            GL11.glVertex3d(boundingBox.minX, boundingBox.minY, boundingBox.minZ);
            GL11.glVertex3d(boundingBox.minX, boundingBox.maxY, boundingBox.minZ);
            GL11.glVertex3d(boundingBox.minX, boundingBox.maxY, boundingBox.maxZ);
            GL11.glEnd();
            GL11.glBegin(7);
            GL11.glVertex3d(boundingBox.maxX, boundingBox.minY, boundingBox.maxZ);
            GL11.glVertex3d(boundingBox.maxX, boundingBox.minY, boundingBox.minZ);
            GL11.glVertex3d(boundingBox.maxX, boundingBox.maxY, boundingBox.minZ);
            GL11.glVertex3d(boundingBox.maxX, boundingBox.maxY, boundingBox.maxZ);
            GL11.glEnd();
            GL11.glBegin(7);
            GL11.glVertex3d(boundingBox.maxX, boundingBox.minY, boundingBox.minZ);
            GL11.glVertex3d(boundingBox.maxX, boundingBox.minY, boundingBox.maxZ);
            GL11.glVertex3d(boundingBox.maxX, boundingBox.maxY, boundingBox.maxZ);
            GL11.glVertex3d(boundingBox.maxX, boundingBox.maxY, boundingBox.minZ);
            GL11.glEnd();
            GL11.glBegin(7);
            GL11.glVertex3d(boundingBox.minX, boundingBox.minY, boundingBox.minZ);
            GL11.glVertex3d(boundingBox.maxX, boundingBox.minY, boundingBox.minZ);
            GL11.glVertex3d(boundingBox.maxX, boundingBox.maxY, boundingBox.minZ);
            GL11.glVertex3d(boundingBox.minX, boundingBox.maxY, boundingBox.minZ);
            GL11.glEnd();
            GL11.glBegin(7);
            GL11.glVertex3d(boundingBox.maxX, boundingBox.minY, boundingBox.minZ);
            GL11.glVertex3d(boundingBox.minX, boundingBox.minY, boundingBox.minZ);
            GL11.glVertex3d(boundingBox.minX, boundingBox.maxY, boundingBox.minZ);
            GL11.glVertex3d(boundingBox.maxX, boundingBox.maxY, boundingBox.minZ);
            GL11.glEnd();
            GL11.glBegin(7);
            GL11.glVertex3d(boundingBox.minX, boundingBox.maxY, boundingBox.minZ);
            GL11.glVertex3d(boundingBox.maxX, boundingBox.maxY, boundingBox.minZ);
            GL11.glVertex3d(boundingBox.maxX, boundingBox.maxY, boundingBox.maxZ);
            GL11.glVertex3d(boundingBox.minX, boundingBox.maxY, boundingBox.maxZ);
            GL11.glEnd();
            GL11.glBegin(7);
            GL11.glVertex3d(boundingBox.maxX, boundingBox.maxY, boundingBox.minZ);
            GL11.glVertex3d(boundingBox.minX, boundingBox.maxY, boundingBox.minZ);
            GL11.glVertex3d(boundingBox.minX, boundingBox.maxY, boundingBox.maxZ);
            GL11.glVertex3d(boundingBox.maxX, boundingBox.maxY, boundingBox.maxZ);
            GL11.glEnd();
            GL11.glBegin(7);
            GL11.glVertex3d(boundingBox.minX, boundingBox.minY, boundingBox.minZ);
            GL11.glVertex3d(boundingBox.maxX, boundingBox.minY, boundingBox.minZ);
            GL11.glVertex3d(boundingBox.maxX, boundingBox.minY, boundingBox.maxZ);
            GL11.glVertex3d(boundingBox.minX, boundingBox.minY, boundingBox.maxZ);
            GL11.glEnd();
            GL11.glBegin(7);
            GL11.glVertex3d(boundingBox.maxX, boundingBox.minY, boundingBox.minZ);
            GL11.glVertex3d(boundingBox.minX, boundingBox.minY, boundingBox.minZ);
            GL11.glVertex3d(boundingBox.minX, boundingBox.minY, boundingBox.maxZ);
            GL11.glVertex3d(boundingBox.maxX, boundingBox.minY, boundingBox.maxZ);
            GL11.glEnd();
            disableRender3D(disableDepth);
        }
    }

    // ==================== Glow & Color Utilities (merged from GuiRenderUtils) ====================

    /**
     * Draws a soft glow behind a rounded-rect area using multi-layer alpha falloff.
     * @param x        left edge of the glow source rect
     * @param y        top edge of the glow source rect
     * @param x2       right edge of the glow source rect
     * @param y2       bottom edge of the glow source rect
     * @param radius   corner radius of the glow source rect
     * @param glowColor ARGB glow colour (alpha channel controls overall intensity)
     * @param intensity 0.0 = invisible, 1.0 = full spread — clamped to [0.1, 1.0]
     */
    public static void drawGlowAround(float x, float y, float x2, float y2, float radius, int glowColor, float intensity) {
        if (intensity <= 0.0f) return;
        int baseAlpha = (glowColor >>> 24);
        if (baseAlpha <= 0) return;

        float clampedIntensity = Math.min(1.0f, Math.max(0.1f, intensity));
        int layers = 8;
        float spread = 7.0f * clampedIntensity;
        RenderServices.shapes().shadow(x, y, x2, y2, radius,
                (glowColor & 0x00FFFFFF) | (Math.round(baseAlpha * 0.6f) << 24),
                layers, spread);
    }

    public static int darker(int hexColor, int factor) {
        float alpha = (float) (hexColor >> 24 & 255);
        float red = Math.max((float) (hexColor >> 16 & 255) - (float) (hexColor >> 16 & 255) / (100.0F / (float) factor), 0.0F);
        float green = Math.max((float) (hexColor >> 8 & 255) - (float) (hexColor >> 8 & 255) / (100.0F / (float) factor), 0.0F);
        float blue = Math.max((float) (hexColor & 255) - (float) (hexColor & 255) / (100.0F / (float) factor), 0.0F);
        return (int) ((float) (((int) alpha << 24) + ((int) red << 16) + ((int) green << 8)) + blue);
    }

    public static int opacity(int hexColor, int factor) {
        float alpha = Math.max((float) (hexColor >> 24 & 255) - (float) (hexColor >> 24 & 255) / (100.0F / (float) factor), 0.0F);
        float red = (float) (hexColor >> 16 & 255);
        float green = (float) (hexColor >> 8 & 255);
        float blue = (float) (hexColor & 255);
        return (int) ((float) (((int) alpha << 24) + ((int) red << 16) + ((int) green << 8)) + blue);
    }

    // ==================== Display Helpers (merged from GuiRenderUtils) ====================

    public static float getScaleFactor() {
        ScaledResolution scaledResolution = new ScaledResolution(mc);
        return scaledResolution.getScaleFactor();
    }

    public static int getDisplayWidth() {
        ScaledResolution scaledResolution = new ScaledResolution(mc);
        int displayWidth = scaledResolution.getScaledWidth();
        return displayWidth;
    }

    public static int getDisplayHeight() {
        ScaledResolution scaledResolution = new ScaledResolution(mc);
        int displayHeight = scaledResolution.getScaledHeight();
        return displayHeight;
    }

    // ==================== R2DUtils Inner Class ====================

    public static class R2DUtils {
        public static void enableGL2D() {
            GLStateManager.begin2D();
        }

        public static void disableGL2D() {
            GLStateManager.end2D();
        }

        public static void drawRoundedRect(float x, float y, float x1, float y1, int borderC, int insideC) {
            RenderUtil.drawRoundedBorderedRect(x, y, x1, y1, 3.0f, 1.0f, insideC, borderC);
        }

        public static void drawRect(double x2, double y2, double x1, double y1, int color) {
            RenderUtil.drawRect((float) x2, (float) y2, (float) x1, (float) y1, color);
        }

        public static void drawHLine(float x, float y, float x1, int y1) {
            RenderUtil.drawHLine(x, y, x1, y1);
        }

        public static void drawVLine(float x, float y, float x1, int y1) {
            RenderUtil.drawVLine(x, y, x1, y1);
        }

        public static void drawHLine(float x, float y, float x1, int y1, int y2) {
            RenderUtil.drawHorizontalGradientRect(Math.min(x, y), x1, Math.max(x, y) + 1.0f, x1 + 1.0f, y1, y2);
        }

        public static void drawGradientRect(float x, float y, float x1, float y1, int topColor, int bottomColor) {
            RenderUtil.drawVerticalGradientRect(x, y, x1, y1, topColor, bottomColor);
        }

        public static void glColor(int hex) {
            RenderUtil.glColor(hex);
        }
    }

    // ==================== Private Helpers ====================

    private static void fillRectRaw(float left, float top, float right, float bottom, int color) {
        glColor(color);
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glVertex2f(left, top);
        GL11.glVertex2f(left, bottom);
        GL11.glVertex2f(right, bottom);
        GL11.glVertex2f(right, top);
        GL11.glEnd();
    }

    private static void roundedRectRaw(float left, float top, float right, float bottom, float radius, int color) {
        if (radius <= 0.0f) {
            fillRectRaw(left, top, right, bottom, color);
            return;
        }
        glColor(color);
        GL11.glBegin(GL11.GL_TRIANGLE_FAN);
        GL11.glVertex2f((left + right) / 2.0f, (top + bottom) / 2.0f);
        roundedCornerVertices(left + radius, top + radius, radius, 180.0f, 270.0f);
        roundedCornerVertices(right - radius, top + radius, radius, 270.0f, 360.0f);
        roundedCornerVertices(right - radius, bottom - radius, radius, 0.0f, 90.0f);
        roundedCornerVertices(left + radius, bottom - radius, radius, 90.0f, 180.0f);
        GL11.glVertex2f(left, top + radius);
        GL11.glEnd();
    }

    private static void joinedRoundedRectRaw(float left, float top, float right, float bottom,
                                             float topLeftRadius, float topRightRadius,
                                             float bottomRightRadius, float bottomLeftRadius,
                                             int color) {
        glColor(color);
        GL11.glBegin(GL11.GL_TRIANGLE_FAN);
        GL11.glVertex2f((left + right) * 0.5F, (top + bottom) * 0.5F);
        roundedCornerVertices(left + topLeftRadius, top + topLeftRadius,
                topLeftRadius, 180.0F, 270.0F);
        roundedCornerVertices(right - topRightRadius, top + topRightRadius,
                topRightRadius, 270.0F, 360.0F);
        roundedCornerVertices(right - bottomRightRadius, bottom - bottomRightRadius,
                bottomRightRadius, 0.0F, 90.0F);
        roundedCornerVertices(left + bottomLeftRadius, bottom - bottomLeftRadius,
                bottomLeftRadius, 90.0F, 180.0F);
        GL11.glVertex2f(left, top + topLeftRadius);
        GL11.glEnd();
    }

    private static float clampCornerRadius(float radius, float maximumRadius) {
        return Math.max(0.0F, Math.min(radius, maximumRadius));
    }

    private static void roundedCornerVertices(float centerX, float centerY, float radius, float start, float end) {
        if (radius <= 0.0F) {
            GL11.glVertex2f(centerX, centerY);
            return;
        }
        for (int i = 0; i <= DEFAULT_ARC_SEGMENTS; i++) {
            float angle = start + (end - start) * i / DEFAULT_ARC_SEGMENTS;
            double radians = Math.toRadians(angle);
            GL11.glVertex2d(centerX + Math.cos(radians) * radius, centerY + Math.sin(radians) * radius);
        }
    }

    private static void begin3DOverlay(boolean disableDepth) {
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_CURRENT_BIT | GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT | GL11.GL_LINE_BIT);
        GL11.glPushMatrix();
        if (disableDepth) {
            GL11.glDepthMask(false);
            GL11.glDisable(GL11.GL_DEPTH_TEST);
        }
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_ALPHA_TEST);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glEnable(GL11.GL_LINE_SMOOTH);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
    }

    private static void end3DOverlay() {
        GL11.glPopMatrix();
        GL11.glPopAttrib();
        GL11.glColor4f(1.0f, 1.0f, 1.0f, 1.0f);
    }

    private static void vertex(double x, double y, double z) {
        GL11.glVertex3d(x, y, z);
    }

    private static void normalizeRect(Rect target, float x, float y, float x2, float y2) {
        target.left = Math.min(x, x2);
        target.top = Math.min(y, y2);
        target.right = Math.max(x, x2);
        target.bottom = Math.max(y, y2);
    }

    private static final class Rect {
        private static final Rect tmp = new Rect();
        private float left;
        private float top;
        private float right;
        private float bottom;

        private float width() {
            return right - left;
        }

        private float height() {
            return bottom - top;
        }
    }
}
