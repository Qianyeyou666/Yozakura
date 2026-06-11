package gq.vapulite.Vapu.utils;

import gq.vapulite.render.RenderState;
import gq.vapulite.render.Blur;
import gq.vapulite.render.ShaderRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;

import java.awt.Color;

public class RenderUtil {
    public static Minecraft mc = Minecraft.getMinecraft();

    private static final int DEFAULT_ARC_SEGMENTS = 24;

    public static void drawRoundedRect(float left, float top, float right, float bottom, int color) {
        drawRoundedRect(left, top, right, bottom, 2.0f, color);
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

    public static void drawImage(ResourceLocation image, int x, int y, float width, float height, float alpha) {
        drawTexturedRect(image, x, y, x + width, y + height, alpha);
    }

    public static void drawTexturedRect(ResourceLocation image, float x, float y, float x2, float y2, float alpha) {
        if (image == null || alpha <= 0.0f) {
            return;
        }
        normalizeRect(Rect.tmp, x, y, x2, y2);
        RenderState.beginTextured2D(alpha);
        try {
            mc.getTextureManager().bindTexture(image);
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
        } finally {
            RenderState.endTextured2D();
        }
    }

    public static int reAlpha(int color, float alpha) {
        return applyAlpha(color, Math.round(RenderState.clamp01(alpha) * 255.0f));
    }

    public static boolean isHovering(float mouseX, float mouseY, float xLeft, float yUp, float xRight, float yBottom) {
        return mouseX > xLeft && mouseX < xRight && mouseY > yUp && mouseY < yBottom;
    }

    public static boolean isHoveringBound(float mouseX, float mouseY, float xLeft, float yUp, float width, float height) {
        return mouseX > xLeft && mouseX < xLeft + width && mouseY > yUp && mouseY < yUp + height;
    }

    public static void drawRoundedRect(float x, float y, float x2, float y2, final int borderColor, final int fillColor) {
        drawRoundedBorderedRect(x, y, x2, y2, 3.0f, 1.0f, fillColor, borderColor);
    }

    public static void drawRoundRect(float x, float y, float x1, float y1, int color) {
        drawRoundedRect(x, y, x1, y1, 3.0f, color);
    }

    public static void drawHLine(float x, float y, float right, int bottom) {
        drawRect(Math.min(x, y), right, Math.max(x, y) + 1.0f, right + 1.0f, bottom);
    }

    public static void drawVLine(float x, float y, float right, int bottom) {
        drawRect(x, Math.min(y, right), x + 1.0f, Math.max(y, right), bottom);
    }

    public static void drawRect(float left, float top, float right, float bottom, int color) {
        if (getAlpha(color) <= 0) {
            return;
        }
        normalizeRect(Rect.tmp, left, top, right, bottom);
        RenderState.begin2D();
        try {
            if (!ShaderRenderer.drawRect(Rect.tmp.left, Rect.tmp.top, Rect.tmp.right, Rect.tmp.bottom, color)) {
                fillRectRaw(Rect.tmp.left, Rect.tmp.top, Rect.tmp.right, Rect.tmp.bottom, color);
            }
        } finally {
            RenderState.end2D();
        }
    }

    public static void drawOutlinedRect(float x, float y, float width, float height, float lineSize, int lineColor) {
        drawRect(x, y, width, y + lineSize, lineColor);
        drawRect(x, height - lineSize, width, height, lineColor);
        drawRect(x, y + lineSize, x + lineSize, height - lineSize, lineColor);
        drawRect(width - lineSize, y + lineSize, width, height - lineSize, lineColor);
    }

    public static void drawFastRoundedRect(int left, float top, int right, float bottom, float radius, int color) {
        drawRoundedRect(left, top, right, bottom, radius, color);
    }

    public static int width() {
        return new ScaledResolution(mc).getScaledWidth();
    }

    public static int height() {
        return new ScaledResolution(mc).getScaledHeight();
    }

    public static void drawRoundedRect(float x, float y, float x2, float y2, float round, int color) {
        if (getAlpha(color) <= 0) {
            return;
        }
        normalizeRect(Rect.tmp, x, y, x2, y2);
        float radius = RenderState.clampRadius(round, Rect.tmp.width(), Rect.tmp.height());
        RenderState.begin2D();
        try {
            if (!ShaderRenderer.drawRoundedRect(Rect.tmp.left, Rect.tmp.top, Rect.tmp.right, Rect.tmp.bottom, radius, color)) {
                roundedRectRaw(Rect.tmp.left, Rect.tmp.top, Rect.tmp.right, Rect.tmp.bottom, radius, color);
            }
        } finally {
            RenderState.end2D();
        }
    }

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
            RenderState.begin2D();
            try {
                if (ShaderRenderer.drawCircle(x, y, w, color)) {
                    return;
                }
            } finally {
                RenderState.end2D();
            }
        }
        int segments = Math.max(12, Math.min(160, (int) Math.ceil((end - start) / 3.0f)));
        RenderState.begin2D();
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
            RenderState.end2D();
        }
    }

    public static void drawCircle(float x, float y, int start, int end, float radius, int color) {
        if (radius <= 0.0f || getAlpha(color) <= 0) {
            return;
        }
        int sweep = Math.abs(end - start);
        if (sweep >= 359) {
            RenderState.begin2D();
            try {
                if (ShaderRenderer.drawCircle(x, y, radius, color)) {
                    return;
                }
            } finally {
                RenderState.end2D();
            }
        }
        arcEllipse(x, y, start, end, radius, radius, color);
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
        RenderState.beginTextured2D(getAlpha(color) / 255.0f);
        try {
            mc.getTextureManager().bindTexture(res);
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
            RenderState.endTextured2D();
        }
    }

    public static void layeredRect(float right, float bottom, float x2, float y2, int outline, int inline, int background) {
        drawRect(right, bottom, x2, y2, outline);
        drawRect(right + 0.5f, bottom + 0.5f, x2 - 0.5f, y2 - 0.5f, inline);
        drawRect(right + 1.0f, bottom + 1.0f, x2 - 1.0f, y2 - 1.0f, background);
    }

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

    public static void doGlScissor(float x, float y, float windowWidth2, float windowHeight2) {
        RenderState.applyScissor(x, y, windowWidth2, windowHeight2);
    }

    public static void glColor(float alpha, int redRGB, int greenRGB, int blueRGB) {
        GL11.glColor4f(redRGB / 255.0f, greenRGB / 255.0f, blueRGB / 255.0f, RenderState.clamp01(alpha));
    }

    public static class R2DUtils {
        public static void enableGL2D() {
            RenderState.begin2D();
        }

        public static void disableGL2D() {
            RenderState.end2D();
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

    public static void drawGradientRect(float x, float y, float x1, float y1, int topColor, int bottomColor) {
        drawVerticalGradientRect(x, y, x1, y1, topColor, bottomColor);
    }

    public static void drawVerticalGradientRect(float x, float y, float x2, float y2, int topColor, int bottomColor) {
        if (getAlpha(topColor) <= 0 && getAlpha(bottomColor) <= 0) {
            return;
        }
        normalizeRect(Rect.tmp, x, y, x2, y2);
        RenderState.begin2D();
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
            RenderState.end2D();
        }
    }

    public static void drawHorizontalGradientRect(float x, float y, float x2, float y2, int leftColor, int rightColor) {
        if (getAlpha(leftColor) <= 0 && getAlpha(rightColor) <= 0) {
            return;
        }
        normalizeRect(Rect.tmp, x, y, x2, y2);
        RenderState.begin2D();
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
            RenderState.end2D();
        }
    }

    public static void drawRoundedHueRect(float x, float y, float x2, float y2, float radius, float alpha) {
        if (alpha <= 0.0f) {
            return;
        }
        normalizeRect(Rect.tmp, x, y, x2, y2);
        float width = Rect.tmp.width();
        float height = Rect.tmp.height();
        radius = RenderState.clampRadius(radius, width, height);
        RenderState.begin2D();
        try {
            if (ShaderRenderer.drawRoundedHueRect(Rect.tmp.left, Rect.tmp.top, Rect.tmp.right, Rect.tmp.bottom,
                    radius, Math.max(0.0f, Math.min(1.0f, alpha)))) {
                return;
            }
        } finally {
            RenderState.end2D();
        }

        RenderState.pushScissor(Rect.tmp.left, Rect.tmp.top, width, height);
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
            RenderState.popScissor();
        }
    }

    public static void drawRoundedBorderedRect(float x, float y, float x2, float y2, float radius, float borderWidth, int fillColor, int borderColor) {
        normalizeRect(Rect.tmp, x, y, x2, y2);
        float width = Rect.tmp.width();
        float height = Rect.tmp.height();
        radius = RenderState.clampRadius(radius, width, height);
        if (borderWidth <= 0.0f || getAlpha(borderColor) <= 0) {
            drawRoundedRect(Rect.tmp.left, Rect.tmp.top, Rect.tmp.right, Rect.tmp.bottom, radius, fillColor);
            return;
        }
        borderWidth = Math.min(borderWidth, Math.min(width, height) / 2.0f);
        RenderState.begin2D();
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
            RenderState.end2D();
        }
    }

    public static void drawFrostedGlassRect(float x, float y, float x2, float y2, float radius,
                                            float borderWidth, int fillColor, int borderColor) {
        normalizeRect(Rect.tmp, x, y, x2, y2);
        float width = Rect.tmp.width();
        float height = Rect.tmp.height();
        radius = RenderState.clampRadius(radius, width, height);
        borderWidth = Math.max(0.0f, Math.min(borderWidth, Math.min(width, height) / 2.0f));

        RenderState.begin2D();
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
            RenderState.end2D();
        }

        drawRoundedBorderedRect(Rect.tmp.left, Rect.tmp.top, Rect.tmp.right, Rect.tmp.bottom,
                radius, borderWidth, fillColor, borderColor);
        drawVerticalGradientRect(Rect.tmp.left + 1.0f, Rect.tmp.top + 1.0f,
                Rect.tmp.right - 1.0f, Rect.tmp.bottom - 1.0f,
                applyAlpha(0x00FFFFFF, Math.min(30, Math.max(0, getAlpha(fillColor) / 5))),
                applyAlpha(0x00000000, 0));
    }

    public static void drawGradientBorderedRect(float x, float y, float x2, float y2, float radius, float borderWidth,
                                                int fillColor, int leftBorderColor, int rightBorderColor) {
        normalizeRect(Rect.tmp, x, y, x2, y2);
        drawRoundedBorderedRect(Rect.tmp.left, Rect.tmp.top, Rect.tmp.right, Rect.tmp.bottom, radius, borderWidth, fillColor, leftBorderColor);
        if (borderWidth > 0.0f) {
            drawHorizontalGradientRect(Rect.tmp.left, Rect.tmp.top, Rect.tmp.right, Rect.tmp.top + borderWidth, leftBorderColor, rightBorderColor);
        }
    }

    public static void drawLine(float x, float y, float x2, float y2, float lineWidth, int color) {
        if (lineWidth <= 0.0f || getAlpha(color) <= 0) {
            return;
        }
        RenderState.begin2D();
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
            RenderState.end2D();
        }
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
        RenderState.begin2D();
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
            RenderState.end2D();
        }
    }

    public static void drawCircleOutline(float x, float y, float radius, float lineWidth, int color) {
        drawArcOutline(x, y, radius, 0.0f, 360.0f, lineWidth, color);
    }

    public static void drawCircleBadge(float centerX, float centerY, float radius, float ringWidth,
                                       float progress, int fillColor, int trackColor, int progressColor) {
        if (radius <= 0.0f || ringWidth <= 0.0f) {
            return;
        }
        float clampedProgress = RenderState.clamp01(progress);
        RenderState.begin2D();
        try {
            if (ShaderRenderer.drawCircleBadge(centerX, centerY, radius, ringWidth,
                    clampedProgress, fillColor, trackColor, progressColor)) {
                return;
            }
        } finally {
            RenderState.end2D();
        }

        drawCircle(centerX, centerY, 0, 360, Math.max(0.0f, radius - ringWidth), fillColor);
        drawCircleOutline(centerX, centerY, radius - ringWidth / 2.0f, ringWidth, trackColor);
        if (clampedProgress > 0.0f) {
            drawArcOutline(centerX, centerY, radius - ringWidth / 2.0f, -90.0f,
                    -90.0f + 360.0f * clampedProgress, ringWidth, progressColor);
        }
    }

    public static void drawProgressBar(float x, float y, float x2, float y2, float radius, float progress, int backgroundColor, int fillColor) {
        float clampedProgress = RenderState.clamp01(progress);
        if (getAlpha(backgroundColor) > 0) {
            drawRoundedRect(x, y, x2, y2, radius, backgroundColor);
        }
        if (clampedProgress <= 0.0f || getAlpha(fillColor) <= 0) {
            return;
        }
        normalizeRect(Rect.tmp, x, y, x2, y2);
        float fillRight = Rect.tmp.left + Rect.tmp.width() * clampedProgress;
        RenderState.pushScissor(Rect.tmp.left, Rect.tmp.top, fillRight - Rect.tmp.left, Rect.tmp.height());
        try {
            drawRoundedRect(Rect.tmp.left, Rect.tmp.top, Rect.tmp.right, Rect.tmp.bottom, radius, fillColor);
        } finally {
            RenderState.popScissor();
        }
    }

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
        float clampedRadius = RenderState.clampRadius(radius, right - left, bottom - top);

        RenderState.begin2D();
        try {
            if (ShaderRenderer.drawRoundedShadow(left, top, right, bottom, clampedRadius, spread, color)) {
                return;
            }
        } finally {
            RenderState.end2D();
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

    public static int applyAlpha(int color, int alpha) {
        return (color & 0x00FFFFFF) | (Math.max(0, Math.min(255, alpha)) << 24);
    }

    public static int getAlpha(int color) {
        return color >>> 24 & 255;
    }

    public static void glColor(int hex) {
        float alpha = (hex >> 24 & 0xFF) / 255.0F;
        float red = (hex >> 16 & 0xFF) / 255.0F;
        float green = (hex >> 8 & 0xFF) / 255.0F;
        float blue = (hex & 0xFF) / 255.0F;
        GL11.glColor4f(red, green, blue, alpha);
    }

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

    private static void roundedCornerVertices(float centerX, float centerY, float radius, float start, float end) {
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
