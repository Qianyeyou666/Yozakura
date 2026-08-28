package gq.yozakura.engine.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL14;

import java.nio.FloatBuffer;
import java.util.Stack;

public final class GLStateManager {
    private static final Minecraft MC = Minecraft.getMinecraft();
    private static final int ATTRIB_MASK = GL11.GL_ENABLE_BIT
            | GL11.GL_CURRENT_BIT
            | GL11.GL_COLOR_BUFFER_BIT
            | GL11.GL_DEPTH_BUFFER_BIT
            | GL11.GL_HINT_BIT
            | GL11.GL_LINE_BIT
            | GL11.GL_SCISSOR_BIT
            | GL11.GL_TEXTURE_BIT;
    private static final FloatBuffer COLOR_BUFFER = BufferUtils.createFloatBuffer(16);
    private static final Stack<int[]> SCISSORS = new Stack<int[]>();

    private GLStateManager() {
    }

    public static void enableBlend() {
        GL11.glEnable(GL11.GL_BLEND);
        GlStateManager.enableBlend();
    }

    public static void disableBlend() {
        GL11.glDisable(GL11.GL_BLEND);
        GlStateManager.disableBlend();
    }

    public static void enableAlpha() {
        GL11.glEnable(GL11.GL_ALPHA_TEST);
        GlStateManager.enableAlpha();
    }

    public static void disableAlpha() {
        GL11.glDisable(GL11.GL_ALPHA_TEST);
        GlStateManager.disableAlpha();
    }

    public static void enableTexture2D() {
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GlStateManager.enableTexture2D();
    }

    public static void disableTexture2D() {
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GlStateManager.disableTexture2D();
    }

    public static void enableDepth() {
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        GlStateManager.enableDepth();
    }

    public static void disableDepth() {
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GlStateManager.disableDepth();
    }

    public static void depthMask(boolean mask) {
        GL11.glDepthMask(mask);
        GlStateManager.depthMask(mask);
    }

    public static void enableCull() {
        GL11.glEnable(GL11.GL_CULL_FACE);
        GlStateManager.enableCull();
    }

    public static void disableCull() {
        GL11.glDisable(GL11.GL_CULL_FACE);
        GlStateManager.disableCull();
    }

    public static void enableLighting() {
        GL11.glEnable(GL11.GL_LIGHTING);
        GlStateManager.enableLighting();
    }

    public static void disableLighting() {
        GL11.glDisable(GL11.GL_LIGHTING);
        GlStateManager.disableLighting();
    }

    public static void enableRescaleNormal() {
        GL11.glEnable(GL12.GL_RESCALE_NORMAL);
        GlStateManager.enableRescaleNormal();
    }

    public static void disableRescaleNormal() {
        GL11.glDisable(GL12.GL_RESCALE_NORMAL);
        GlStateManager.disableRescaleNormal();
    }

    public static void blendFuncSeparate(int srcRgb, int dstRgb, int srcAlpha, int dstAlpha) {
        OpenGlHelper.glBlendFunc(srcRgb, dstRgb, srcAlpha, dstAlpha);
        GlStateManager.tryBlendFuncSeparate(srcRgb, dstRgb, srcAlpha, dstAlpha);
    }

    public static void blendFunc(int src, int dst) {
        GL11.glBlendFunc(src, dst);
        GlStateManager.blendFunc(src, dst);
    }

    public static void setActiveTexture(int textureUnit) {
        GL13.glActiveTexture(textureUnit);
        GlStateManager.setActiveTexture(textureUnit);
        GL13.glActiveTexture(textureUnit);
    }

    public static void bindTexture2D(int texture) {
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
        GlStateManager.bindTexture(texture);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
    }

    public static void color(float red, float green, float blue, float alpha) {
        GL11.glColor4f(red, green, blue, alpha);
        GlStateManager.resetColor();
        GlStateManager.color(red, green, blue, alpha);
    }

    public static void lineSmooth(boolean enabled) {
        if (enabled) {
            GL11.glEnable(GL11.GL_LINE_SMOOTH);
        } else {
            GL11.glDisable(GL11.GL_LINE_SMOOTH);
        }
    }

    public static void pointSmooth(boolean enabled) {
        if (enabled) {
            GL11.glEnable(GL11.GL_POINT_SMOOTH);
        } else {
            GL11.glDisable(GL11.GL_POINT_SMOOTH);
        }
    }

    public static void polygonSmooth(boolean enabled) {
        if (enabled) {
            GL11.glEnable(GL11.GL_POLYGON_SMOOTH);
        } else {
            GL11.glDisable(GL11.GL_POLYGON_SMOOTH);
        }
    }

    public static void multisample(boolean enabled) {
        if (enabled) {
            GL11.glEnable(GL13.GL_MULTISAMPLE);
        } else {
            GL11.glDisable(GL13.GL_MULTISAMPLE);
        }
    }

    public static void textureEnvModulate() {
        GL11.glTexEnvi(GL11.GL_TEXTURE_ENV, GL11.GL_TEXTURE_ENV_MODE, GL11.GL_MODULATE);
    }

    public static void lineWidth(float width) {
        GL11.glLineWidth(width);
    }

    public static void pointSize(float size) {
        GL11.glPointSize(size);
    }

    public static void hint(int target, int mode) {
        GL11.glHint(target, mode);
    }

    public static void shadeModel(int mode) {
        GL11.glShadeModel(mode);
        GlStateManager.shadeModel(mode);
    }

    public static void syncToCurrent() {
        syncBlend();
        syncAlpha();
        syncTexture2D();
        syncDepth();
        syncCull();
        syncLighting();
        syncRescaleNormal();
        syncDepthMask();
        syncBlendFunc();
        syncActiveTextureAndBinding();
        syncColor();
    }

    private static void syncBlend() {
        if (GL11.glIsEnabled(GL11.GL_BLEND)) {
            enableBlend();
        } else {
            disableBlend();
        }
    }

    private static void syncAlpha() {
        if (GL11.glIsEnabled(GL11.GL_ALPHA_TEST)) {
            enableAlpha();
        } else {
            disableAlpha();
        }
    }

    private static void syncTexture2D() {
        if (GL11.glIsEnabled(GL11.GL_TEXTURE_2D)) {
            enableTexture2D();
        } else {
            disableTexture2D();
        }
    }

    private static void syncDepth() {
        if (GL11.glIsEnabled(GL11.GL_DEPTH_TEST)) {
            enableDepth();
        } else {
            disableDepth();
        }
    }

    private static void syncCull() {
        if (GL11.glIsEnabled(GL11.GL_CULL_FACE)) {
            enableCull();
        } else {
            disableCull();
        }
    }

    private static void syncLighting() {
        if (GL11.glIsEnabled(GL11.GL_LIGHTING)) {
            enableLighting();
        } else {
            disableLighting();
        }
    }

    private static void syncRescaleNormal() {
        if (GL11.glIsEnabled(GL12.GL_RESCALE_NORMAL)) {
            enableRescaleNormal();
        } else {
            disableRescaleNormal();
        }
    }

    private static void syncDepthMask() {
        depthMask(GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK));
    }

    private static void syncBlendFunc() {
        blendFuncSeparate(GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB),
                GL11.glGetInteger(GL14.GL_BLEND_DST_RGB),
                GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA),
                GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA));
    }

    private static void syncActiveTextureAndBinding() {
        int activeTexture = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        int boundTexture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        setActiveTexture(activeTexture);
        bindTexture2D(boundTexture);
    }

    private static void syncColor() {
        COLOR_BUFFER.clear();
        GL11.glGetFloat(GL11.GL_CURRENT_COLOR, COLOR_BUFFER);
        color(COLOR_BUFFER.get(0), COLOR_BUFFER.get(1), COLOR_BUFFER.get(2), COLOR_BUFFER.get(3));
    }

    public static void begin2D() {
        GL11.glPushAttrib(ATTRIB_MASK);
        GL11.glPushMatrix();
        enableBlend();
        disableTexture2D();
        disableDepth();
        disableAlpha();
        disableCull();
        blendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO);
        lineSmooth(true);
        GL11.glHint(GL11.GL_LINE_SMOOTH_HINT, GL11.GL_NICEST);
    }

    public static void end2D() {
        GL11.glPopMatrix();
        GL11.glPopAttrib();
        color(1.0f, 1.0f, 1.0f, 1.0f);
    }

    public static void beginTextured2D(float alpha) {
        GL11.glPushAttrib(ATTRIB_MASK);
        GL11.glPushMatrix();
        disableDepth();
        enableBlend();
        enableTexture2D();
        depthMask(false);
        blendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO);
        color(1.0f, 1.0f, 1.0f, clamp01(alpha));
    }

    public static void endTextured2D() {
        depthMask(true);
        GL11.glPopMatrix();
        GL11.glPopAttrib();
        color(1.0f, 1.0f, 1.0f, 1.0f);
    }

    public static void color(int color) {
        float alpha = (color >> 24 & 0xFF) / 255.0f;
        float red = (color >> 16 & 0xFF) / 255.0f;
        float green = (color >> 8 & 0xFF) / 255.0f;
        float blue = (color & 0xFF) / 255.0f;
        color(red, green, blue, alpha);
    }

    public static float clamp01(float value) {
        return Math.max(0.0f, Math.min(1.0f, value));
    }

    public static float clampRadius(float radius, float width, float height) {
        return Math.max(0.0f, Math.min(radius, Math.min(Math.abs(width), Math.abs(height)) / 2.0f));
    }

    public static void applyScissor(float x, float y, float width, float height) {
        int[] bounds = scaledScissor(x, y, width, height);
        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        GL11.glScissor(bounds[0], bounds[1], bounds[2], bounds[3]);
    }

    public static void applyScissor(float x, float y, float width, float height, float scaleFactor) {
        int[] bounds = scaledScissor(x, y, width, height, scaleFactor);
        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        GL11.glScissor(bounds[0], bounds[1], bounds[2], bounds[3]);
    }

    public static void disableScissor() {
        SCISSORS.clear();
        GL11.glDisable(GL11.GL_SCISSOR_TEST);
    }

    public static void pushScissor(float x, float y, float width, float height) {
        int[] bounds = scaledScissor(x, y, width, height);

        if (!SCISSORS.isEmpty()) {
            int[] parent = SCISSORS.peek();
            int x2 = Math.min(bounds[0] + bounds[2], parent[0] + parent[2]);
            int y2 = Math.min(bounds[1] + bounds[3], parent[1] + parent[3]);
            bounds[0] = Math.max(bounds[0], parent[0]);
            bounds[1] = Math.max(bounds[1], parent[1]);
            bounds[2] = Math.max(0, x2 - bounds[0]);
            bounds[3] = Math.max(0, y2 - bounds[1]);
        }

        SCISSORS.push(bounds);
        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        GL11.glScissor(bounds[0], bounds[1], bounds[2], bounds[3]);
    }

    private static int[] scaledScissor(float x, float y, float width, float height) {
        ScaledResolution scaled = new ScaledResolution(MC);
        int scale = scaled.getScaleFactor();
        return scaledScissor(x, y, width, height, scale);
    }

    private static int[] scaledScissor(float x, float y, float width, float height, float scale) {
        int scaledX = Math.round(x * scale);
        int scaledY = Math.round(MC.displayHeight - (y + height) * scale);
        int scaledWidth = Math.round(width * scale);
        int scaledHeight = Math.round(height * scale);
        return new int[]{scaledX, scaledY, Math.max(0, scaledWidth), Math.max(0, scaledHeight)};
    }

    public static void popScissor() {
        if (SCISSORS.isEmpty()) {
            GL11.glDisable(GL11.GL_SCISSOR_TEST);
            return;
        }

        SCISSORS.pop();
        if (SCISSORS.isEmpty()) {
            GL11.glDisable(GL11.GL_SCISSOR_TEST);
            return;
        }

        int[] bounds = SCISSORS.peek();
        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        GL11.glScissor(bounds[0], bounds[1], bounds[2], bounds[3]);
    }
}
