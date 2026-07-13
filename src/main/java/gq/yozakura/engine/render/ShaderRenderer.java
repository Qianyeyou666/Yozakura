package gq.yozakura.engine.render;

import gq.yozakura.engine.render.ui.LiquidGlassSettings;
import net.minecraft.client.renderer.GlStateManager;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GLContext;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.IntBuffer;
import java.util.HashMap;
import java.util.Map;

public final class ShaderRenderer {
    private static final float EDGE_SOFTNESS = 0.75f;
    private static final float EDGE_PADDING = 1.0f;
    /**
     * Shared LiquidGlass preset used when draw calls do not pass a custom settings struct.
     *
     * <p>Parameter meanings and tunable default values live in {@link LiquidGlassSettings#defaults()}.</p>
     */
    public static final LiquidGlassSettings LIQUID_GLASS_PRESET = LiquidGlassSettings.defaults();
    private static final LiquidGlassSettings FROSTED_GLASS_PRESET =
            LIQUID_GLASS_PRESET.withBlurRadius(10.0f);
    private static final LiquidGlassSettings VIEWPORT_FEATHER_BLUR_PRESET =
            LIQUID_GLASS_PRESET.withBlurIterations(2)
                    .withBlurRadius(5.0f)
                    .withBlurDownscale(0.90f);
    private static final float MAX_LIQUID_GLASS_BLUR_RADIUS = 64.0f;
    private static final float MIN_LIQUID_GLASS_BLUR_DOWNSCALE = 0.1f;
    private static final float MAX_LIQUID_GLASS_BLUR_DOWNSCALE = 1.0f;
    private static final float MAX_GAUSSIAN_PASS_RADIUS = 10.0f;
    private static final int MAX_GAUSSIAN_ITERATIONS = 10;
    private static final int BLUR_KEY_SCALE = 1000;
    private static final float BLUR_RADIUS_KEY_STEP = 2.0f;
    private static final float BLUR_DOWNSCALE_KEY_STEP = 0.05f;
    private static final long GLASS_CAPTURE_INTERVAL_MS = 50L;
    private static final int SHADER_ATTRIB_MASK = GL11.GL_ENABLE_BIT
            | GL11.GL_COLOR_BUFFER_BIT
            | GL11.GL_CURRENT_BIT
            | GL11.GL_DEPTH_BUFFER_BIT
            | GL11.GL_TEXTURE_BIT
            | GL11.GL_LINE_BIT;

    private static Program solidProgram;
    private static Program gradientProgram;
    private static Program roundedProgram;
    private static Program roundedGradientProgram;
    private static Program roundedHueProgram;
    private static Program roundedPaletteProgram;
    private static Program roundedBorderProgram;
    private static Program roundedShadowProgram;
    private static Program circleProgram;
    private static Program arcProgram;
    private static Program lineProgram;
    private static Program circleBadgeProgram;
    private static Program frostedGlassProgram;
    private static Program liquidGlassProgram;
    private static Program viewportFeatherBlurProgram;
    private static Program killShatterProgram;
    private static Program gaussianBlurProgram;
    private static final String LIQUID_GLASS_FRAGMENT_RESOURCE =
            "/assets/minecraft/yozakura/shaders/liquid_glass.frag";
    private static final String KILL_SHATTER_FRAGMENT_RESOURCE =
            "/assets/minecraft/yozakura/shaders/kill_shatter.frag";
    private static final IntBuffer VIEWPORT_BUFFER = BufferUtils.createIntBuffer(16);
    private static final Map<BlurKey, BlurCache> BLUR_CACHES = new HashMap<BlurKey, BlurCache>();
    private static int screenTexture;
    private static int capturedWidth;
    private static int capturedHeight;
    private static int glassCaptureVersion = 1;
    private static int sharedGlassSourceVersion = -1;
    private static int sharedGlassViewportX;
    private static int sharedGlassViewportY;
    private static long lastGlassCaptureMs;
    private static boolean screenTextureConfigured;
    private static boolean loggedFailure;

    private ShaderRenderer() {
    }

    public static void invalidateFrostedGlass() {
        lastGlassCaptureMs = 0L;
        advanceGlassCaptureVersion();
        Blur.invalidate();
    }

    public static void beginOverlayFrame() {
        long now = System.currentTimeMillis();
        if (now - lastGlassCaptureMs < GLASS_CAPTURE_INTERVAL_MS) {
            return;
        }
        lastGlassCaptureMs = now;
        advanceGlassCaptureVersion();
        Blur.invalidate();
    }

    private static void advanceGlassCaptureVersion() {
        glassCaptureVersion++;
        if (glassCaptureVersion == Integer.MAX_VALUE) {
            glassCaptureVersion = 1;
            for (BlurCache cache : BLUR_CACHES.values()) {
                cache.sourceVersion = 0;
            }
        }
    }

    public static LiquidGlassSettings defaultLiquidGlassSettings() {
        return LIQUID_GLASS_PRESET;
    }

    public static void warmMaterialClickGuiResources() {
        if (!supportsShaders()) {
            return;
        }
        TextureState textureState = null;
        int previousFramebuffer = 0;
        boolean restoreFramebuffer = false;
        try {
            Program solid = getSolidProgram();
            Program gradient = getGradientProgram();
            Program rounded = getRoundedProgram();
            Program roundedGradient = getRoundedGradientProgram();
            Program roundedHue = getRoundedHueProgram();
            Program roundedPalette = getRoundedPaletteProgram();
            Program roundedBorder = getRoundedBorderProgram();
            Program roundedShadow = getRoundedShadowProgram();
            Program circle = getCircleProgram();
            Program arc = getArcProgram();
            Program line = getLineProgram();
            Program circleBadge = getCircleBadgeProgram();
            Program frostedGlass = getFrostedGlassProgram();
            Program liquidGlass = getLiquidGlassProgram();
            Program viewportFeather = getViewportFeatherBlurProgram();
            Program gaussian = getGaussianBlurProgram();

            warmUniforms(solid, "color");
            warmUniforms(gradient, "color1", "color2", "color3", "color4");
            warmUniforms(rounded, "rectSize", "color", "radius", "padding", "softness");
            warmUniforms(roundedGradient, "rectSize", "color1", "color2", "color3", "color4",
                    "radius", "padding", "softness");
            warmUniforms(roundedHue, "rectSize", "radius", "padding", "softness", "alpha");
            warmUniforms(roundedPalette, "rectSize", "radius", "padding", "softness", "hue", "alpha");
            warmUniforms(roundedBorder, "rectSize", "radius", "padding", "softness",
                    "borderWidth", "fillColor", "borderColor");
            warmUniforms(roundedShadow, "rectSize", "radius", "padding", "softness", "shadowSize", "color");
            warmUniforms(circle, "circleRadius", "padding", "softness", "color");
            warmUniforms(arc, "circleRadius", "lineWidth", "startAngle", "endAngle", "padding", "softness", "color");
            warmUniforms(line, "lineWidth", "color");
            warmUniforms(circleBadge, "circleRadius", "ringWidth", "progress", "padding", "softness",
                    "fillColor", "trackColor", "progressColor");
            warmUniforms(frostedGlass, "rectSize", "radius", "padding", "softness", "borderWidth",
                    "grainStrength", "blurRadius", "screenSize", "viewportSize", "screenTex",
                    "fillColor", "borderColor");
            warmUniforms(liquidGlass, "rectSize", "radius", "padding", "softness", "borderWidth",
                    "refraction", "edge", "highlight", "u_powerFactor", "u_fPower", "u_a", "u_b",
                    "u_c", "u_d", "u_noise", "u_glowWeight", "u_glowBias", "u_glowEdge0",
                    "u_glowEdge1", "screenSize", "viewportSize", "screenTex", "fillColor", "borderColor");
            warmUniforms(viewportFeather, "screenTex", "sourceTex", "screenSize", "viewportSize",
                    "topEdge", "opacity");
            warmUniforms(gaussian, "screenTex", "u_resolution", "u_direction", "u_radius");

            if (!supportsFramebufferBlur()) {
                return;
            }
            textureState = saveTexture0State();
            previousFramebuffer = GL11.glGetInteger(GL30.GL_FRAMEBUFFER_BINDING);
            restoreFramebuffer = true;
            VIEWPORT_BUFFER.clear();
            GL11.glGetInteger(GL11.GL_VIEWPORT, VIEWPORT_BUFFER);
            int viewportW = VIEWPORT_BUFFER.get(2);
            int viewportH = VIEWPORT_BUFFER.get(3);
            if (viewportW <= 0 || viewportH <= 0) {
                return;
            }
            setActiveTexture(GL13.GL_TEXTURE0);
            warmBlurCacheTargets(LIQUID_GLASS_PRESET, viewportW, viewportH);
            warmBlurCacheTargets(FROSTED_GLASS_PRESET, viewportW, viewportH);
            warmBlurCacheTargets(VIEWPORT_FEATHER_BLUR_PRESET, viewportW, viewportH);
        } catch (Throwable ignored) {
        } finally {
            if (restoreFramebuffer) {
                GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, previousFramebuffer);
            }
            restoreTexture0State(textureState);
            GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);
        }
    }

    public static boolean drawRect(float left, float top, float right, float bottom, int color) {
        Program program = getSolidProgram();
        if (program == null || right <= left || bottom <= top) {
            return false;
        }

        ShaderState shaderState = beginProgram(program);
        try {
            setColor(program, "color", color);
            drawQuad(left, top, right, bottom, 0.0f);
        } finally {
            endProgram(shaderState);
        }
        return true;
    }

    public static boolean drawGradientRect(float left, float top, float right, float bottom,
                                           int topLeft, int bottomLeft, int topRight, int bottomRight) {
        Program program = getGradientProgram();
        if (program == null || right <= left || bottom <= top) {
            return false;
        }

        ShaderState shaderState = beginProgram(program);
        try {
            setColor(program, "color1", topLeft);
            setColor(program, "color2", bottomLeft);
            setColor(program, "color3", topRight);
            setColor(program, "color4", bottomRight);
            drawQuad(left, top, right, bottom, 0.0f);
        } finally {
            endProgram(shaderState);
        }
        return true;
    }

    public static boolean drawRoundedRect(float left, float top, float right, float bottom, float radius, int color) {
        Program program = getRoundedProgram();
        if (program == null || right <= left || bottom <= top) {
            return false;
        }

        ShaderState shaderState = beginProgram(program);
        try {
            setRoundedUniforms(program, right - left, bottom - top, radius);
            setColor(program, "color", color);
            drawQuad(left, top, right, bottom, EDGE_PADDING);
        } finally {
            endProgram(shaderState);
        }
        return true;
    }

    public static boolean drawRoundedGradientRect(float left, float top, float right, float bottom, float radius,
                                                  int topLeft, int bottomLeft, int topRight, int bottomRight) {
        Program program = getRoundedGradientProgram();
        if (program == null || right <= left || bottom <= top) {
            return false;
        }

        ShaderState shaderState = beginProgram(program);
        try {
            setRoundedUniforms(program, right - left, bottom - top, radius);
            setColor(program, "color1", topLeft);
            setColor(program, "color2", bottomLeft);
            setColor(program, "color3", topRight);
            setColor(program, "color4", bottomRight);
            drawQuad(left, top, right, bottom, EDGE_PADDING);
        } finally {
            endProgram(shaderState);
        }
        return true;
    }

    public static boolean drawRoundedHueRect(float left, float top, float right, float bottom, float radius,
                                             float alpha) {
        Program program = getRoundedHueProgram();
        if (program == null || right <= left || bottom <= top || alpha <= 0.0f) {
            return false;
        }

        ShaderState shaderState = beginProgram(program);
        try {
            setRoundedUniforms(program, right - left, bottom - top, radius);
            program.set1f("alpha", Math.max(0.0f, Math.min(1.0f, alpha)));
            drawQuad(left, top, right, bottom, EDGE_PADDING);
        } finally {
            endProgram(shaderState);
        }
        return true;
    }

    public static boolean drawRoundedPaletteRect(float left, float top, float right, float bottom, float radius,
                                                 float hue, float alpha) {
        Program program = getRoundedPaletteProgram();
        if (program == null || right <= left || bottom <= top || alpha <= 0.0f) {
            return false;
        }

        ShaderState shaderState = beginProgram(program);
        try {
            setRoundedUniforms(program, right - left, bottom - top, radius);
            program.set1f("hue", Math.max(0.0f, Math.min(1.0f, hue)));
            program.set1f("alpha", Math.max(0.0f, Math.min(1.0f, alpha)));
            drawQuad(left, top, right, bottom, EDGE_PADDING);
        } finally {
            endProgram(shaderState);
        }
        return true;
    }

    public static boolean drawRoundedBorderedRect(float left, float top, float right, float bottom, float radius,
                                                  float borderWidth, int fillColor, int borderColor) {
        Program program = getRoundedBorderProgram();
        if (program == null || right <= left || bottom <= top) {
            return false;
        }

        float width = right - left;
        float height = bottom - top;
        float clampedBorder = Math.max(0.0f, Math.min(borderWidth, Math.min(width, height) / 2.0f));

        ShaderState shaderState = beginProgram(program);
        try {
            setRoundedUniforms(program, width, height, radius);
            program.set1f("borderWidth", clampedBorder);
            setColor(program, "fillColor", fillColor);
            setColor(program, "borderColor", borderColor);
            drawQuad(left, top, right, bottom, EDGE_PADDING);
        } finally {
            endProgram(shaderState);
        }
        return true;
    }

    public static boolean drawRoundedShadow(float left, float top, float right, float bottom, float radius,
                                            float spread, int color) {
        Program program = getRoundedShadowProgram();
        if (program == null || right <= left || bottom <= top || spread <= 0.0f) {
            return false;
        }

        float width = right - left;
        float height = bottom - top;

        ShaderState shaderState = beginProgram(program);
        try {
            setRoundedUniforms(program, width, height, radius);
            program.set1f("shadowSize", spread);
            setColor(program, "color", color);
            drawQuad(left, top, right, bottom, spread + EDGE_PADDING);
        } finally {
            endProgram(shaderState);
        }
        return true;
    }

    public static boolean drawFrostedGlass(float left, float top, float right, float bottom, float radius,
                                           float borderWidth, int fillColor, int borderColor) {
        Program program = getFrostedGlassProgram();
        if (program == null || right <= left || bottom <= top) {
            return false;
        }
        BlurCache blurCache = ensureFrostedGlassTexture(FROSTED_GLASS_PRESET);
        if (blurCache == null) {
            return false;
        }

        float width = right - left;
        float height = bottom - top;
        float clampedBorder = Math.max(0.0f, Math.min(borderWidth, Math.min(width, height) / 2.0f));
        ShaderState shaderState = beginProgram(program);
        try {
            setActiveTexture(GL13.GL_TEXTURE0);
            GL11.glEnable(GL11.GL_TEXTURE_2D);
            int sourceTexture = blurCache.ready ? blurCache.textureB : blurCache.sourceTexture;
            int sourceWidth = blurCache.ready ? blurCache.blurWidth : blurCache.sourceWidth;
            int sourceHeight = blurCache.ready ? blurCache.blurHeight : blurCache.sourceHeight;
            bindTexture(sourceTexture);
            setRoundedUniforms(program, width, height, radius);
            program.set1f("borderWidth", clampedBorder);
            program.set1f("grainStrength", 0.0f);
            program.set1f("blurRadius", 13.5f);
            program.set2f("screenSize", sourceWidth, sourceHeight);
            program.set2f("viewportSize", blurCache.sourceWidth, blurCache.sourceHeight);
            program.set1i("screenTex", 0);
            setColor(program, "fillColor", fillColor);
            setColor(program, "borderColor", borderColor);
            drawQuad(left, top, right, bottom, EDGE_PADDING);
        } finally {
            endProgram(shaderState);
        }
        return true;
    }

    public static boolean drawLiquidGlass(float left, float top, float right, float bottom, float radius,
                                          float borderWidth, int fillColor, int borderColor) {
        return drawLiquidGlass(left, top, right, bottom, radius, borderWidth, fillColor, borderColor,
                LIQUID_GLASS_PRESET);
    }

    public static boolean drawLiquidGlass(float left, float top, float right, float bottom, float radius,
                                          float borderWidth, int fillColor, int borderColor,
                                          LiquidGlassSettings settings) {
        LiquidGlassSettings resolvedSettings = liquidGlassSettingsOrDefault(settings);
        Program program = getLiquidGlassProgram();
        if (program == null || right <= left || bottom <= top) {
            return false;
        }
        BlurCache blurCache = ensureFrostedGlassTexture(resolvedSettings);
        if (blurCache == null || !blurCache.ready) {
            return false;
        }

        float width = right - left;
        float height = bottom - top;
        float clampedBorder = Math.max(0.0f, Math.min(borderWidth, Math.min(width, height) / 2.0f));
        ShaderState shaderState = beginProgram(program);
        try {
            setActiveTexture(GL13.GL_TEXTURE0);
            GL11.glEnable(GL11.GL_TEXTURE_2D);
            int sourceTexture = blurCache.textureB;
            int sourceWidth = blurCache.blurWidth;
            int sourceHeight = blurCache.blurHeight;
            bindTexture(sourceTexture);
            setRoundedUniforms(program, width, height, radius);
            program.set1f("borderWidth", clampedBorder);
            uploadLiquidGlassSettings(program, resolvedSettings);
            program.set2f("screenSize", sourceWidth, sourceHeight);
            program.set2f("viewportSize", blurCache.sourceWidth, blurCache.sourceHeight);
            program.set1i("screenTex", 0);
            setColor(program, "fillColor", fillColor);
            setColor(program, "borderColor", borderColor);
            drawQuad(left, top, right, bottom, EDGE_PADDING);
        } finally {
            endProgram(shaderState);
        }
        return true;
    }

    public static boolean drawViewportFeatherBlur(float left, float top, float right, float bottom,
                                                  boolean topEdge, float opacity) {
        Program program = getViewportFeatherBlurProgram();
        if (program == null || right <= left || bottom <= top || opacity <= 0.0f) {
            return false;
        }
        BlurCache blurCache = ensureFrostedGlassTexture(VIEWPORT_FEATHER_BLUR_PRESET);
        if (blurCache == null || !blurCache.ready) {
            return false;
        }

        ShaderState shaderState = beginProgram(program);
        TextureState texture1State = saveTextureState(GL13.GL_TEXTURE1);
        try {
            setActiveTexture(GL13.GL_TEXTURE0);
            GL11.glEnable(GL11.GL_TEXTURE_2D);
            bindTexture(blurCache.textureB);
            program.set1i("screenTex", 0);
            setActiveTexture(GL13.GL_TEXTURE1);
            GL11.glEnable(GL11.GL_TEXTURE_2D);
            bindTexture(blurCache.sourceTexture);
            program.set1i("sourceTex", 1);
            setActiveTexture(GL13.GL_TEXTURE0);
            program.set2f("screenSize", Math.max(1.0f, blurCache.blurWidth), Math.max(1.0f, blurCache.blurHeight));
            program.set2f("viewportSize", Math.max(1.0f, blurCache.sourceWidth), Math.max(1.0f, blurCache.sourceHeight));
            program.set1f("topEdge", topEdge ? 1.0f : 0.0f);
            program.set1f("opacity", Math.max(0.0f, Math.min(1.0f, opacity)));
            drawQuad(left, top, right, bottom, 0.0f);
        } finally {
            restoreTextureState(GL13.GL_TEXTURE1, texture1State);
            endProgram(shaderState);
        }
        return true;
    }

    public static boolean prepareViewportFeatherBlur() {
        Program program = getViewportFeatherBlurProgram();
        if (program == null) {
            return false;
        }
        BlurCache blurCache = ensureFrostedGlassTexture(VIEWPORT_FEATHER_BLUR_PRESET);
        if (blurCache == null || !blurCache.ready) {
            return false;
        }
        ShaderState shaderState = beginProgram(program);
        TextureState texture1State = saveTextureState(GL13.GL_TEXTURE1);
        try {
            setActiveTexture(GL13.GL_TEXTURE0);
            GL11.glEnable(GL11.GL_TEXTURE_2D);
            bindTexture(blurCache.textureB);
            program.set1i("screenTex", 0);
            setActiveTexture(GL13.GL_TEXTURE1);
            GL11.glEnable(GL11.GL_TEXTURE_2D);
            bindTexture(blurCache.sourceTexture);
            program.set1i("sourceTex", 1);
            setActiveTexture(GL13.GL_TEXTURE0);
            program.set2f("screenSize", Math.max(1.0f, blurCache.blurWidth), Math.max(1.0f, blurCache.blurHeight));
            program.set2f("viewportSize", Math.max(1.0f, blurCache.sourceWidth), Math.max(1.0f, blurCache.sourceHeight));
            program.set1f("topEdge", 1.0f);
            program.set1f("opacity", 1.0f);
            drawQuad(-4.0f, -4.0f, -3.0f, -3.0f, 0.0f);
        } finally {
            restoreTextureState(GL13.GL_TEXTURE1, texture1State);
            endProgram(shaderState);
        }
        return true;
    }

    public static boolean drawKillShatterDistortion(float centerX, float centerY, float radius,
                                                    float progress, float strength, float seed) {
        Program program = getKillShatterProgram();
        if (program == null || radius <= 1.0f || !ensureScreenTexture()) {
            return false;
        }

        ShaderState shaderState = beginProgram(program);
        try {
            GL13.glActiveTexture(GL13.GL_TEXTURE0);
            GL11.glEnable(GL11.GL_TEXTURE_2D);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, screenTexture);
            program.set1i("screenTex", 0);
            program.set2f("screenSize", Math.max(1.0f, capturedWidth), Math.max(1.0f, capturedHeight));
            program.set2f("center", centerX, centerY);
            program.set1f("radius", radius);
            program.set1f("progress", Math.max(0.0f, Math.min(1.0f, progress)));
            program.set1f("strength", Math.max(0.0f, Math.min(2.0f, strength)));
            program.set1f("seed", seed);
            program.set1f("time", (System.nanoTime() % 60000000000L) / 1000000000.0f);
            drawFullscreenPassQuad();
        } finally {
            endProgram(shaderState);
        }
        return true;
    }

    public static boolean drawCircle(float centerX, float centerY, float radius, int color) {
        Program program = getCircleProgram();
        if (program == null || radius <= 0.0f) {
            return false;
        }

        float clampedRadius = Math.max(0.5f, radius);
        float padding = Math.max(1.5f, EDGE_SOFTNESS + 0.75f);

        ShaderState shaderState = beginProgram(program);
        try {
            program.set1f("circleRadius", clampedRadius);
            program.set1f("padding", padding);
            program.set1f("softness", EDGE_SOFTNESS);
            setColor(program, "color", color);
            drawQuad(centerX - clampedRadius, centerY - clampedRadius,
                    centerX + clampedRadius, centerY + clampedRadius, padding);
        } finally {
            endProgram(shaderState);
        }
        return true;
    }

    public static boolean drawArc(float centerX, float centerY, float radius, float start, float end,
                                  float lineWidth, int color) {
        Program program = getArcProgram();
        if (program == null || radius <= 0.0f || lineWidth <= 0.0f) {
            return false;
        }

        if (end < start) {
            float temp = start;
            start = end;
            end = temp;
        }

        float clampedRadius = Math.max(0.5f, radius);
        float clampedLine = Math.max(0.5f, Math.min(lineWidth, clampedRadius * 2.0f));
        float sweep = Math.max(0.0f, Math.min(360.0f, end - start));
        float padding = Math.max(1.5f, clampedLine + EDGE_SOFTNESS + 0.75f);

        ShaderState shaderState = beginProgram(program);
        try {
            program.set1f("arcRadius", clampedRadius);
            program.set1f("lineWidth", clampedLine);
            program.set1f("startAngle", (float) Math.toRadians(start));
            program.set1f("sweepAngle", (float) Math.toRadians(sweep));
            program.set1f("padding", padding);
            program.set1f("softness", EDGE_SOFTNESS);
            setColor(program, "color", color);
            drawQuad(centerX - clampedRadius, centerY - clampedRadius,
                    centerX + clampedRadius, centerY + clampedRadius, padding);
        } finally {
            endProgram(shaderState);
        }
        return true;
    }

    public static boolean drawLine(float x, float y, float x2, float y2, float lineWidth, int color) {
        Program program = getLineProgram();
        if (program == null || lineWidth <= 0.0f) {
            return false;
        }

        float minX = Math.min(x, x2);
        float minY = Math.min(y, y2);
        float maxX = Math.max(x, x2);
        float maxY = Math.max(y, y2);
        float pad = Math.max(1.5f, lineWidth * 0.5f + EDGE_SOFTNESS + 0.75f);
        float left = minX - pad;
        float top = minY - pad;
        float right = maxX + pad;
        float bottom = maxY + pad;

        ShaderState shaderState = beginProgram(program);
        try {
            program.set2f("rectSize", right - left, bottom - top);
            program.set2f("startPoint", x - left, y - top);
            program.set2f("endPoint", x2 - left, y2 - top);
            program.set1f("lineWidth", Math.max(0.5f, lineWidth));
            program.set1f("softness", EDGE_SOFTNESS);
            setColor(program, "color", color);
            drawQuad(left, top, right, bottom, 0.0f);
        } finally {
            endProgram(shaderState);
        }
        return true;
    }

    public static boolean drawCircleBadge(float centerX, float centerY, float radius, float ringWidth,
                                          float progress, int fillColor, int trackColor, int progressColor) {
        Program program = getCircleBadgeProgram();
        if (program == null || radius <= 0.0f || ringWidth <= 0.0f) {
            return false;
        }

        float clampedRadius = Math.max(0.5f, radius);
        float clampedRing = Math.max(0.5f, Math.min(ringWidth, clampedRadius));
        float padding = Math.max(1.5f, clampedRing + 0.75f);
        float left = centerX - clampedRadius;
        float top = centerY - clampedRadius;
        float right = centerX + clampedRadius;
        float bottom = centerY + clampedRadius;

        ShaderState shaderState = beginProgram(program);
        try {
            program.set1f("badgeRadius", clampedRadius);
            program.set1f("ringWidth", clampedRing);
            program.set1f("progress", Math.max(0.0f, Math.min(1.0f, progress)));
            program.set1f("padding", padding);
            program.set1f("softness", EDGE_SOFTNESS);
            setColor(program, "fillColor", fillColor);
            setColor(program, "trackColor", trackColor);
            setColor(program, "progressColor", progressColor);
            drawQuad(left, top, right, bottom, padding);
        } finally {
            endProgram(shaderState);
        }
        return true;
    }

    private static BlurCache ensureFrostedGlassTexture(LiquidGlassSettings settings) {
        if (!supportsShaders()) {
            return null;
        }
        LiquidGlassSettings resolvedSettings = liquidGlassSettingsOrDefault(settings);
        TextureState textureState = saveTexture0State();
        try {
            VIEWPORT_BUFFER.clear();
            GL11.glGetInteger(GL11.GL_VIEWPORT, VIEWPORT_BUFFER);
            int viewportX = VIEWPORT_BUFFER.get(0);
            int viewportY = VIEWPORT_BUFFER.get(1);
            int viewportW = VIEWPORT_BUFFER.get(2);
            int viewportH = VIEWPORT_BUFFER.get(3);
            if (viewportW <= 0 || viewportH <= 0) {
                return null;
            }

            BlurKey key = BlurKey.from(resolvedSettings);
            BlurCache cache = blurCacheFor(key);
            int targetWidth = Math.max(1, Math.round(viewportW * key.blurDownscale()));
            int targetHeight = Math.max(1, Math.round(viewportH * key.blurDownscale()));
            setActiveTexture(GL13.GL_TEXTURE0);
            if (!ensureSharedGlassSource(viewportX, viewportY, viewportW, viewportH)) {
                return null;
            }
            cache.sourceTexture = screenTexture;
            cache.sourceWidth = viewportW;
            cache.sourceHeight = viewportH;
            if (cache.sourceVersion != glassCaptureVersion || cache.blurWidth != targetWidth
                    || cache.blurHeight != targetHeight) {
                cache.ready = buildFrostedBlur(cache, screenTexture, viewportW, viewportH,
                        key.blurRadius(), key.blurIterations(), key.blurDownscale());
                cache.sourceVersion = glassCaptureVersion;
            }
            return cache;
        } finally {
            restoreTexture0State(textureState);
        }
    }

    private static boolean ensureSharedGlassSource(int viewportX, int viewportY, int viewportW, int viewportH) {
        if (viewportW <= 0 || viewportH <= 0) {
            return false;
        }
        boolean needsCapture = sharedGlassSourceVersion != glassCaptureVersion
                || capturedWidth != viewportW
                || capturedHeight != viewportH
                || sharedGlassViewportX != viewportX
                || sharedGlassViewportY != viewportY;
        if (!ensureScreenTextureStorage(viewportW, viewportH)) {
            return false;
        }
        if (!needsCapture) {
            return true;
        }
        bindTexture(screenTexture);
        GL11.glCopyTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, viewportX, viewportY, viewportW, viewportH);
        sharedGlassSourceVersion = glassCaptureVersion;
        sharedGlassViewportX = viewportX;
        sharedGlassViewportY = viewportY;
        return true;
    }

    private static boolean ensureScreenTexture() {
        if (!supportsShaders()) {
            return false;
        }
        TextureState textureState = saveTexture0State();
        try {
            VIEWPORT_BUFFER.clear();
            GL11.glGetInteger(GL11.GL_VIEWPORT, VIEWPORT_BUFFER);
            int viewportX = VIEWPORT_BUFFER.get(0);
            int viewportY = VIEWPORT_BUFFER.get(1);
            int viewportW = VIEWPORT_BUFFER.get(2);
            int viewportH = VIEWPORT_BUFFER.get(3);
            if (viewportW <= 0 || viewportH <= 0) {
                return false;
            }
            setActiveTexture(GL13.GL_TEXTURE0);
            if (!ensureScreenTextureStorage(viewportW, viewportH)) {
                return false;
            }
            bindTexture(screenTexture);
            GL11.glCopyTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, viewportX, viewportY, viewportW, viewportH);
            sharedGlassSourceVersion = -1;
            return true;
        } finally {
            restoreTexture0State(textureState);
        }
    }

    private static boolean ensureScreenTextureStorage(int viewportW, int viewportH) {
        boolean created = false;
        if (screenTexture == 0) {
            screenTexture = GL11.glGenTextures();
            screenTextureConfigured = false;
            created = true;
        }
        bindTexture(screenTexture);
        configureScreenTexture();
        if (created || capturedWidth != viewportW || capturedHeight != viewportH) {
            GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGB, viewportW, viewportH, 0,
                    GL11.GL_RGB, GL11.GL_UNSIGNED_BYTE, (ByteBuffer) null);
            capturedWidth = viewportW;
            capturedHeight = viewportH;
            sharedGlassSourceVersion = -1;
        }
        return true;
    }

    private static boolean buildFrostedBlur(BlurCache cache, int sourceTexture, int width, int height, float blurRadius,
                                            int blurIterations, float blurDownscale) {
        if (cache == null || !supportsFramebufferBlur() || width <= 0 || height <= 0
                || getGaussianBlurProgram() == null) {
            return false;
        }
        int targetWidth = Math.max(1, Math.round(width * blurDownscale));
        int targetHeight = Math.max(1, Math.round(height * blurDownscale));
        int previousFramebuffer = GL11.glGetInteger(GL30.GL_FRAMEBUFFER_BINDING);
        VIEWPORT_BUFFER.clear();
        GL11.glGetInteger(GL11.GL_VIEWPORT, VIEWPORT_BUFFER);
        int previousViewportX = VIEWPORT_BUFFER.get(0);
        int previousViewportY = VIEWPORT_BUFFER.get(1);
        int previousViewportW = VIEWPORT_BUFFER.get(2);
        int previousViewportH = VIEWPORT_BUFFER.get(3);
        try {
            if (!cache.ensureTargets(targetWidth, targetHeight)) {
                cache.ready = false;
                return false;
            }
            int iterations = clampGaussianIterations(blurIterations);
            if (iterations == 0) {
                runBlurPass(sourceTexture, cache.framebufferB, targetWidth, targetHeight,
                        width, height, 0.0f, 0.0f, 0.0f);
                return true;
            }
            float passRadius = clampGaussianPassRadius(blurRadius / Math.max(1, iterations));
            int passSourceTexture = sourceTexture;
            int sourceWidth = width;
            int sourceHeight = height;
            for (int i = 0; i < iterations; i++) {
                runBlurPass(passSourceTexture, cache.framebufferA, targetWidth, targetHeight,
                        sourceWidth, sourceHeight, passRadius, 1.0f, 0.0f);
                runBlurPass(cache.textureA, cache.framebufferB, targetWidth, targetHeight,
                        targetWidth, targetHeight, passRadius, 0.0f, 1.0f);
                passSourceTexture = cache.textureB;
                sourceWidth = targetWidth;
                sourceHeight = targetHeight;
            }
            return true;
        } catch (Throwable throwable) {
            cache.ready = false;
            logFailure(throwable);
            return false;
        } finally {
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, previousFramebuffer);
            GL11.glViewport(previousViewportX, previousViewportY, previousViewportW, previousViewportH);
        }
    }

    private static BlurCache blurCacheFor(BlurKey key) {
        BlurCache cache = BLUR_CACHES.get(key);
        if (cache == null) {
            cache = new BlurCache();
            BLUR_CACHES.put(key, cache);
        }
        return cache;
    }

    private static void warmBlurCacheTargets(LiquidGlassSettings settings, int viewportW, int viewportH) {
        if (settings == null || viewportW <= 0 || viewportH <= 0 || !supportsFramebufferBlur()) {
            return;
        }
        BlurKey key = BlurKey.from(settings);
        BlurCache cache = blurCacheFor(key);
        int targetWidth = Math.max(1, Math.round(viewportW * key.blurDownscale()));
        int targetHeight = Math.max(1, Math.round(viewportH * key.blurDownscale()));
        ensureScreenTextureStorage(viewportW, viewportH);
        cache.sourceTexture = screenTexture;
        cache.sourceWidth = viewportW;
        cache.sourceHeight = viewportH;
        cache.ensureTargets(targetWidth, targetHeight);
    }

    private static void setupBlurTexture(int texture, int width, int height) {
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGB, width, height, 0,
                GL11.GL_RGB, GL11.GL_UNSIGNED_BYTE, (ByteBuffer) null);
    }

    private static void configureScreenTexture() {
        if (screenTextureConfigured) {
            return;
        }
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
        screenTextureConfigured = true;
    }

    private static boolean attachBlurTarget(int framebuffer, int texture) {
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, framebuffer);
        GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, texture, 0);
        return GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER) == GL30.GL_FRAMEBUFFER_COMPLETE;
    }

    private static void runBlurPass(int sourceTexture, int framebuffer, int width, int height,
                                    int sourceWidth, int sourceHeight, float blurRadius, float dirX, float dirY) {
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, framebuffer);
        GL11.glViewport(0, 0, width, height);
        Program program = getGaussianBlurProgram();
        ShaderState shaderState = beginProgram(program);
        try {
            setActiveTexture(GL13.GL_TEXTURE0);
            GL11.glEnable(GL11.GL_TEXTURE_2D);
            bindTexture(sourceTexture);
            program.set1i("screenTex", 0);
            program.set2f("u_resolution", Math.max(1.0f, sourceWidth), Math.max(1.0f, sourceHeight));
            program.set2f("u_direction", dirX, dirY);
            program.set1f("u_radius", clampGaussianPassRadius(blurRadius));
            drawFullscreenPassQuad();
        } finally {
            endProgram(shaderState);
        }
    }

    private static float clampLiquidGlassBlurRadius(float blurRadius) {
        return Math.max(0.0f, Math.min(MAX_LIQUID_GLASS_BLUR_RADIUS, blurRadius));
    }

    private static float clampLiquidGlassBlurDownscale(float blurDownscale) {
        return Math.max(MIN_LIQUID_GLASS_BLUR_DOWNSCALE,
                Math.min(MAX_LIQUID_GLASS_BLUR_DOWNSCALE, blurDownscale));
    }

    private static int clampGaussianIterations(int iterations) {
        return Math.max(0, Math.min(MAX_GAUSSIAN_ITERATIONS, iterations));
    }

    private static float clampGaussianPassRadius(float blurRadius) {
        return Math.max(0.0f, Math.min(MAX_GAUSSIAN_PASS_RADIUS, blurRadius));
    }

    private static LiquidGlassSettings liquidGlassSettingsOrDefault(LiquidGlassSettings settings) {
        return settings == null ? LIQUID_GLASS_PRESET : settings;
    }

    private static void uploadLiquidGlassSettings(Program program, LiquidGlassSettings settings) {
        program.set1f("refraction", Math.max(0.0f, Math.min(1.4f, settings.refractionScale())));
        program.set1f("highlight", Math.max(0.0f, Math.min(1.35f, settings.highlight())));
        program.set1f("u_powerFactor", Math.max(1.001f, Math.min(6.0f, settings.powerFactor())));
        program.set1f("u_fPower", Math.max(-1.5f, Math.min(6.0f, settings.refractionPower())));
        program.set1f("u_a", Math.max(0.0f, Math.min(5.0f, settings.refractionA())));
        program.set1f("u_b", Math.max(0.0f, Math.min(6.0f, settings.refractionB())));
        program.set1f("u_c", Math.max(0.0f, Math.min(6.0f, settings.refractionC())));
        program.set1f("u_d", Math.max(0.0f, Math.min(10.0f, settings.refractionD())));
        program.set1f("u_noise", Math.max(0.0f, Math.min(0.3f, settings.noise())));
        program.set1f("u_glowWeight", Math.max(-1.0f, Math.min(1.0f, settings.glowWeight())));
        program.set1f("u_glowBias", Math.max(-1.0f, Math.min(1.0f, settings.glowBias())));
        program.set1f("u_glowEdge0", Math.max(-1.0f, Math.min(1.0f, settings.glowEdge0())));
        program.set1f("u_glowEdge1", Math.max(-1.0f, Math.min(1.0f, settings.glowEdge1())));
    }

    private static boolean supportsFramebufferBlur() {
        try {
            return GLContext.getCapabilities() != null && GLContext.getCapabilities().OpenGL30;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static void setRoundedUniforms(Program program, float width, float height, float radius) {
        float clampedRadius = Math.max(0.0f, Math.min(radius, Math.min(width, height) / 2.0f));
        program.set2f("rectSize", width, height);
        program.set1f("radius", clampedRadius);
        program.set1f("padding", EDGE_PADDING);
        program.set1f("softness", EDGE_SOFTNESS);
    }

    private static Program getSolidProgram() {
        if (solidProgram == null) {
            solidProgram = createProgram(SOLID_FRAGMENT);
        }
        return solidProgram;
    }

    private static Program getGradientProgram() {
        if (gradientProgram == null) {
            gradientProgram = createProgram(GRADIENT_FRAGMENT);
        }
        return gradientProgram;
    }

    private static Program getRoundedProgram() {
        if (roundedProgram == null) {
            roundedProgram = createProgram(ROUNDED_FRAGMENT);
        }
        return roundedProgram;
    }

    private static Program getRoundedGradientProgram() {
        if (roundedGradientProgram == null) {
            roundedGradientProgram = createProgram(ROUNDED_GRADIENT_FRAGMENT);
        }
        return roundedGradientProgram;
    }

    private static Program getRoundedHueProgram() {
        if (roundedHueProgram == null) {
            roundedHueProgram = createProgram(ROUNDED_HUE_FRAGMENT);
        }
        return roundedHueProgram;
    }

    private static Program getRoundedPaletteProgram() {
        if (roundedPaletteProgram == null) {
            roundedPaletteProgram = createProgram(ROUNDED_PALETTE_FRAGMENT);
        }
        return roundedPaletteProgram;
    }

    private static Program getRoundedBorderProgram() {
        if (roundedBorderProgram == null) {
            roundedBorderProgram = createProgram(ROUNDED_BORDER_FRAGMENT);
        }
        return roundedBorderProgram;
    }

    private static Program getRoundedShadowProgram() {
        if (roundedShadowProgram == null) {
            roundedShadowProgram = createProgram(ROUNDED_SHADOW_FRAGMENT);
        }
        return roundedShadowProgram;
    }

    private static Program getCircleProgram() {
        if (circleProgram == null) {
            circleProgram = createProgram(CIRCLE_FRAGMENT);
        }
        return circleProgram;
    }

    private static Program getArcProgram() {
        if (arcProgram == null) {
            arcProgram = createProgram(ARC_FRAGMENT);
        }
        return arcProgram;
    }

    private static Program getLineProgram() {
        if (lineProgram == null) {
            lineProgram = createProgram(LINE_FRAGMENT);
        }
        return lineProgram;
    }

    private static Program getCircleBadgeProgram() {
        if (circleBadgeProgram == null) {
            circleBadgeProgram = createProgram(CIRCLE_BADGE_FRAGMENT);
        }
        return circleBadgeProgram;
    }

    private static Program getFrostedGlassProgram() {
        if (frostedGlassProgram == null) {
            frostedGlassProgram = createProgram(FROSTED_GLASS_FRAGMENT);
        }
        return frostedGlassProgram;
    }

    private static Program getLiquidGlassProgram() {
        if (liquidGlassProgram == null) {
            liquidGlassProgram = createProgramFromResource(LIQUID_GLASS_FRAGMENT_RESOURCE);
        }
        return liquidGlassProgram;
    }

    private static Program getViewportFeatherBlurProgram() {
        if (viewportFeatherBlurProgram == null) {
            viewportFeatherBlurProgram = createProgram(VIEWPORT_FEATHER_BLUR_FRAGMENT);
        }
        return viewportFeatherBlurProgram;
    }

    private static Program getKillShatterProgram() {
        if (killShatterProgram == null) {
            killShatterProgram = createProgramFromResource(KILL_SHATTER_FRAGMENT_RESOURCE);
        }
        return killShatterProgram;
    }

    private static Program getGaussianBlurProgram() {
        if (gaussianBlurProgram == null) {
            gaussianBlurProgram = createProgram(GAUSSIAN_BLUR_FRAGMENT);
        }
        return gaussianBlurProgram;
    }

    private static Program createProgramFromResource(String fragmentResource) {
        String fragmentSource = loadShaderResource(fragmentResource);
        if (fragmentSource == null) {
            return null;
        }
        return createProgram(fragmentResource, fragmentSource);
    }

    private static String loadShaderResource(String resourcePath) {
        InputStream stream = ShaderRenderer.class.getResourceAsStream(resourcePath);
        if (stream == null) {
            logResourceFailure(new IllegalStateException("Missing shader resource " + resourcePath));
            return null;
        }
        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int read;
            while ((read = stream.read(buffer)) != -1) {
                output.write(buffer, 0, read);
            }
            return new String(output.toByteArray(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            logResourceFailure(exception);
            return null;
        } finally {
            try {
                stream.close();
            } catch (IOException ignored) {
            }
        }
    }

    private static Program createProgram(String fragmentSource) {
        return createProgram("inline shader", fragmentSource);
    }

    private static Program createProgram(String label, String fragmentSource) {
        if (!supportsShaders()) {
            return null;
        }

        int vertexShader = 0;
        int fragmentShader = 0;
        int program = 0;
        try {
            vertexShader = compileShader(GL20.GL_VERTEX_SHADER, VERTEX_SHADER, label + " vertex");
            fragmentShader = compileShader(GL20.GL_FRAGMENT_SHADER, fragmentSource, label + " fragment");
            program = GL20.glCreateProgram();
            GL20.glAttachShader(program, vertexShader);
            GL20.glAttachShader(program, fragmentShader);
            GL20.glLinkProgram(program);
            if (GL20.glGetProgrami(program, GL20.GL_LINK_STATUS) == 0) {
                throw new IllegalStateException(GL20.glGetProgramInfoLog(program, 4096));
            }
            return new Program(program);
        } catch (Throwable throwable) {
            logFailure(throwable);
            if (program != 0) {
                GL20.glDeleteProgram(program);
            }
            return null;
        } finally {
            if (program != 0 && vertexShader != 0) {
                GL20.glDetachShader(program, vertexShader);
            }
            if (program != 0 && fragmentShader != 0) {
                GL20.glDetachShader(program, fragmentShader);
            }
            if (vertexShader != 0) {
                GL20.glDeleteShader(vertexShader);
            }
            if (fragmentShader != 0) {
                GL20.glDeleteShader(fragmentShader);
            }
        }
    }

    private static int compileShader(int type, String source, String label) {
        int shader = GL20.glCreateShader(type);
        GL20.glShaderSource(shader, source);
        GL20.glCompileShader(shader);
        if (GL20.glGetShaderi(shader, GL20.GL_COMPILE_STATUS) == 0) {
            String log = GL20.glGetShaderInfoLog(shader, 4096);
            GL20.glDeleteShader(shader);
            throw new IllegalStateException(label + ": " + log + " sourceTail=" + sourceTail(source));
        }
        return shader;
    }

    private static String sourceTail(String source) {
        if (source == null) {
            return "<null>";
        }
        int start = Math.max(0, source.length() - 220);
        return source.substring(start).replace('\n', ' ').replace('\r', ' ');
    }

    private static boolean supportsShaders() {
        try {
            return GLContext.getCapabilities() != null && GLContext.getCapabilities().OpenGL20;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static void logFailure(Throwable throwable) {
        if (loggedFailure) {
            return;
        }
        loggedFailure = true;
        System.err.println("[Yozakura] Shader program failed: " + throwable.getMessage());
    }

    private static void logResourceFailure(Throwable throwable) {
        if (loggedFailure) {
            return;
        }
        loggedFailure = true;
        System.err.println("[Yozakura] Shader resource unavailable: " + throwable.getMessage());
    }

    private static void setColor(Program program, String uniform, int color) {
        program.set4f(uniform,
                ((color >> 16) & 255) / 255.0f,
                ((color >> 8) & 255) / 255.0f,
                (color & 255) / 255.0f,
                ((color >> 24) & 255) / 255.0f);
    }

    private static ShaderState beginProgram(Program program) {
        int previousProgram = 0;
        try {
            previousProgram = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        } catch (Throwable ignored) {
            previousProgram = 0;
        }
        TextureState textureState = saveTexture0State();
        GL11.glPushAttrib(SHADER_ATTRIB_MASK);
        program.use();
        // This is the shared UI-shader entry point (rounded panels, glass and
        // shadows), not the off-screen glow mask.  Keep its original alpha
        // cutoff and blend destination semantics; the glow renderer owns its
        // separate premultiplied-alpha state locally.
        GlStateManager.enableAlpha();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        GlStateManager.disableDepth();
        GL11.glDepthMask(false);
        return new ShaderState(previousProgram, textureState);
    }

    private static void endProgram(ShaderState state) {
        if (state == null) {
            GL20.glUseProgram(0);
            return;
        }
        GL20.glUseProgram(state.previousProgram);
        GL11.glPopAttrib();
        restoreTexture0State(state.textureState);
        gq.yozakura.engine.render.GLStateManager.syncToCurrent();
        GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);
    }

    private static TextureState saveTexture0State() {
        int activeTexture = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        setActiveTexture(GL13.GL_TEXTURE0);
        int texture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        return new TextureState(activeTexture, texture);
    }

    private static void restoreTexture0State(TextureState state) {
        if (state == null) {
            return;
        }
        setActiveTexture(GL13.GL_TEXTURE0);
        bindTexture(state.texture);
        setActiveTexture(state.activeTexture);
    }

    private static TextureState saveTextureState(int textureUnit) {
        int activeTexture = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        setActiveTexture(textureUnit);
        int texture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        setActiveTexture(activeTexture);
        return new TextureState(activeTexture, texture);
    }

    private static void restoreTextureState(int textureUnit, TextureState state) {
        if (state == null) {
            return;
        }
        setActiveTexture(textureUnit);
        bindTexture(state.texture);
        setActiveTexture(state.activeTexture);
    }

    private static void setActiveTexture(int textureUnit) {
        try {
            GlStateManager.setActiveTexture(textureUnit);
        } catch (Throwable ignored) {
            GL13.glActiveTexture(textureUnit);
        }
        GL13.glActiveTexture(textureUnit);
    }

    private static void bindTexture(int texture) {
        try {
            GlStateManager.bindTexture(texture);
        } catch (Throwable ignored) {
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
        }
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
    }

    private static void drawQuad(float left, float top, float right, float bottom, float padding) {
        float drawLeft = left - padding;
        float drawTop = top - padding;
        float drawRight = right + padding;
        float drawBottom = bottom + padding;

        GL11.glBegin(GL11.GL_QUADS);
        GL11.glTexCoord2f(0.0f, 0.0f);
        GL11.glVertex2f(drawLeft, drawTop);
        GL11.glTexCoord2f(0.0f, 1.0f);
        GL11.glVertex2f(drawLeft, drawBottom);
        GL11.glTexCoord2f(1.0f, 1.0f);
        GL11.glVertex2f(drawRight, drawBottom);
        GL11.glTexCoord2f(1.0f, 0.0f);
        GL11.glVertex2f(drawRight, drawTop);
        GL11.glEnd();
    }

    private static void drawFullscreenPassQuad() {
        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glPushMatrix();
        GL11.glLoadIdentity();
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glPushMatrix();
        GL11.glLoadIdentity();
        try {
            GL11.glBegin(GL11.GL_QUADS);
            GL11.glTexCoord2f(0.0f, 0.0f);
            GL11.glVertex2f(-1.0f, -1.0f);
            GL11.glTexCoord2f(1.0f, 0.0f);
            GL11.glVertex2f(1.0f, -1.0f);
            GL11.glTexCoord2f(1.0f, 1.0f);
            GL11.glVertex2f(1.0f, 1.0f);
            GL11.glTexCoord2f(0.0f, 1.0f);
            GL11.glVertex2f(-1.0f, 1.0f);
            GL11.glEnd();
        } finally {
            GL11.glPopMatrix();
            GL11.glMatrixMode(GL11.GL_PROJECTION);
            GL11.glPopMatrix();
            GL11.glMatrixMode(GL11.GL_MODELVIEW);
        }
    }

    private static void warmUniforms(Program program, String... names) {
        if (program == null || names == null) {
            return;
        }
        for (String name : names) {
            if (name != null) {
                program.uniform(name);
            }
        }
    }

    private static int quantizedBlurRadiusKey(float blurRadius) {
        float clamped = clampLiquidGlassBlurRadius(blurRadius);
        return Math.round(Math.round(clamped / BLUR_RADIUS_KEY_STEP) * BLUR_RADIUS_KEY_STEP * BLUR_KEY_SCALE);
    }

    private static int quantizedBlurDownscaleKey(float blurDownscale) {
        float clamped = clampLiquidGlassBlurDownscale(blurDownscale);
        return Math.round(Math.round(clamped / BLUR_DOWNSCALE_KEY_STEP) * BLUR_DOWNSCALE_KEY_STEP * BLUR_KEY_SCALE);
    }

    private static final class BlurKey {
        private final int radius;
        private final int downscale;
        private final int iterations;

        private BlurKey(int radius, int downscale, int iterations) {
            this.radius = radius;
            this.downscale = downscale;
            this.iterations = iterations;
        }

        private static BlurKey from(LiquidGlassSettings settings) {
            LiquidGlassSettings resolvedSettings = liquidGlassSettingsOrDefault(settings);
            return new BlurKey(
                    quantizedBlurRadiusKey(resolvedSettings.blurRadius()),
                    quantizedBlurDownscaleKey(resolvedSettings.blurDownscale()),
                    clampGaussianIterations(resolvedSettings.blurIterations()));
        }

        private float blurRadius() {
            return radius / (float) BLUR_KEY_SCALE;
        }

        private float blurDownscale() {
            return downscale / (float) BLUR_KEY_SCALE;
        }

        private int blurIterations() {
            return iterations;
        }

        @Override
        public boolean equals(Object object) {
            if (this == object) {
                return true;
            }
            if (!(object instanceof BlurKey)) {
                return false;
            }
            BlurKey other = (BlurKey) object;
            return radius == other.radius && downscale == other.downscale && iterations == other.iterations;
        }

        @Override
        public int hashCode() {
            int result = radius;
            result = 31 * result + downscale;
            result = 31 * result + iterations;
            return result;
        }
    }

    private static final class BlurCache {
        private int sourceTexture;
        private int sourceWidth;
        private int sourceHeight;
        private int textureA;
        private int textureB;
        private int framebufferA;
        private int framebufferB;
        private int blurWidth;
        private int blurHeight;
        private boolean targetsReady;
        private boolean ready;
        private int sourceVersion = -1;

        private boolean ensureTargets(int width, int height) {
            boolean created = false;
            if (textureA == 0) {
                textureA = GL11.glGenTextures();
                created = true;
            }
            if (textureB == 0) {
                textureB = GL11.glGenTextures();
                created = true;
            }
            if (framebufferA == 0) {
                framebufferA = GL30.glGenFramebuffers();
                created = true;
            }
            if (framebufferB == 0) {
                framebufferB = GL30.glGenFramebuffers();
                created = true;
            }
            boolean sizeChanged = blurWidth != width || blurHeight != height;
            if (!created && !sizeChanged && targetsReady) {
                return true;
            }
            setupBlurTexture(textureA, width, height);
            setupBlurTexture(textureB, width, height);
            blurWidth = width;
            blurHeight = height;
            targetsReady = attachBlurTarget(framebufferA, textureA)
                    && attachBlurTarget(framebufferB, textureB);
            if (!targetsReady) {
                ready = false;
            }
            return targetsReady;
        }
    }

    private static final class Program {
        private final int id;
        private final Map<String, Integer> uniformCache = new HashMap<String, Integer>();

        private Program(int id) {
            this.id = id;
        }

        private void use() {
            GL20.glUseProgram(id);
        }

        private int uniform(String name) {
            Integer cached = uniformCache.get(name);
            if (cached != null) {
                return cached.intValue();
            }
            int location = GL20.glGetUniformLocation(id, name);
            uniformCache.put(name, location);
            return location;
        }

        private void set1f(String name, float value) {
            GL20.glUniform1f(uniform(name), value);
        }

        private void set1i(String name, int value) {
            GL20.glUniform1i(uniform(name), value);
        }

        private void set2f(String name, float first, float second) {
            GL20.glUniform2f(uniform(name), first, second);
        }

        private void set4f(String name, float first, float second, float third, float fourth) {
            GL20.glUniform4f(uniform(name), first, second, third, fourth);
        }
    }

    private static final class TextureState {
        private final int activeTexture;
        private final int texture;

        private TextureState(int activeTexture, int texture) {
            this.activeTexture = activeTexture;
            this.texture = texture;
        }
    }

    private static final class ShaderState {
        private final int previousProgram;
        private final TextureState textureState;

        private ShaderState(int previousProgram, TextureState textureState) {
            this.previousProgram = previousProgram;
            this.textureState = textureState;
        }
    }

    private static final String VERTEX_SHADER =
            "#version 120\n" +
            "void main() {\n" +
            "    gl_TexCoord[0] = gl_MultiTexCoord0;\n" +
            "    gl_Position = gl_ModelViewProjectionMatrix * gl_Vertex;\n" +
            "}\n";

    private static final String SOLID_FRAGMENT =
            "#version 120\n" +
            "uniform vec4 color;\n" +
            "void main() {\n" +
            "    gl_FragColor = color;\n" +
            "}\n";

    private static final String GRADIENT_FRAGMENT =
            "#version 120\n" +
            "uniform vec4 color1;\n" +
            "uniform vec4 color2;\n" +
            "uniform vec4 color3;\n" +
            "uniform vec4 color4;\n" +
            "#define NOISE (0.5 / 255.0)\n" +
            "float dither(vec2 coord) {\n" +
            "    return mix(NOISE, -NOISE, fract(sin(dot(coord, vec2(12.9898, 78.233))) * 43758.5453));\n" +
            "}\n" +
            "void main() {\n" +
            "    vec2 st = clamp(gl_TexCoord[0].st, vec2(0.0), vec2(1.0));\n" +
            "    vec4 top = mix(color1, color3, st.x);\n" +
            "    vec4 bottom = mix(color2, color4, st.x);\n" +
            "    vec4 result = mix(top, bottom, st.y);\n" +
            "    result.rgb += dither(st);\n" +
            "    gl_FragColor = result;\n" +
            "}\n";

    private static final String ROUNDED_FRAGMENT =
            "#version 120\n" +
            "uniform vec2 rectSize;\n" +
            "uniform vec4 color;\n" +
            "uniform float radius;\n" +
            "uniform float padding;\n" +
            "uniform float softness;\n" +
            "float roundSDF(vec2 p, vec2 halfSize, float r) {\n" +
            "    vec2 q = abs(p) - halfSize + vec2(r);\n" +
            "    return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - r;\n" +
            "}\n" +
            "void main() {\n" +
            "    vec2 size = max(rectSize, vec2(0.001));\n" +
            "    vec2 coord = gl_TexCoord[0].st * (size + vec2(padding * 2.0)) - vec2(padding);\n" +
            "    vec2 halfSize = size * 0.5;\n" +
            "    float r = min(radius, min(halfSize.x, halfSize.y));\n" +
            "    float distance = roundSDF(coord - halfSize, halfSize, r);\n" +
            "    float alpha = 1.0 - smoothstep(0.0, softness, distance);\n" +
            "    gl_FragColor = vec4(color.rgb, color.a * alpha);\n" +
            "}\n";

    private static final String ROUNDED_GRADIENT_FRAGMENT =
            "#version 120\n" +
            "uniform vec2 rectSize;\n" +
            "uniform vec4 color1;\n" +
            "uniform vec4 color2;\n" +
            "uniform vec4 color3;\n" +
            "uniform vec4 color4;\n" +
            "uniform float radius;\n" +
            "uniform float padding;\n" +
            "uniform float softness;\n" +
            "#define NOISE (0.5 / 255.0)\n" +
            "float roundSDF(vec2 p, vec2 halfSize, float r) {\n" +
            "    vec2 q = abs(p) - halfSize + vec2(r);\n" +
            "    return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - r;\n" +
            "}\n" +
            "float dither(vec2 coord) {\n" +
            "    return mix(NOISE, -NOISE, fract(sin(dot(coord, vec2(12.9898, 78.233))) * 43758.5453));\n" +
            "}\n" +
            "void main() {\n" +
            "    vec2 size = max(rectSize, vec2(0.001));\n" +
            "    vec2 coord = gl_TexCoord[0].st * (size + vec2(padding * 2.0)) - vec2(padding);\n" +
            "    vec2 st = clamp(coord / size, vec2(0.0), vec2(1.0));\n" +
            "    vec2 halfSize = size * 0.5;\n" +
            "    float r = min(radius, min(halfSize.x, halfSize.y));\n" +
            "    float distance = roundSDF(coord - halfSize, halfSize, r);\n" +
            "    float alpha = 1.0 - smoothstep(0.0, softness, distance);\n" +
            "    vec4 top = mix(color1, color3, st.x);\n" +
            "    vec4 bottom = mix(color2, color4, st.x);\n" +
            "    vec4 result = mix(top, bottom, st.y);\n" +
            "    result.rgb += dither(st);\n" +
            "    gl_FragColor = vec4(result.rgb, result.a * alpha);\n" +
            "}\n";

    private static final String ROUNDED_HUE_FRAGMENT =
            "#version 120\n" +
            "uniform vec2 rectSize;\n" +
            "uniform float radius;\n" +
            "uniform float padding;\n" +
            "uniform float softness;\n" +
            "uniform float alpha;\n" +
            "#define NOISE (0.45 / 255.0)\n" +
            "float roundSDF(vec2 p, vec2 halfSize, float r) {\n" +
            "    vec2 q = abs(p) - halfSize + vec2(r);\n" +
            "    return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - r;\n" +
            "}\n" +
            "float dither(vec2 coord) {\n" +
            "    return mix(NOISE, -NOISE, fract(sin(dot(coord, vec2(12.9898, 78.233))) * 43758.5453));\n" +
            "}\n" +
            "vec3 hsv2rgb(vec3 c) {\n" +
            "    vec3 p = abs(fract(c.xxx + vec3(0.0, 0.6666667, 0.3333333)) * 6.0 - 3.0);\n" +
            "    return c.z * mix(vec3(1.0), clamp(p - 1.0, 0.0, 1.0), c.y);\n" +
            "}\n" +
            "void main() {\n" +
            "    vec2 size = max(rectSize, vec2(0.001));\n" +
            "    vec2 coord = gl_TexCoord[0].st * (size + vec2(padding * 2.0)) - vec2(padding);\n" +
            "    vec2 st = clamp(coord / size, vec2(0.0), vec2(1.0));\n" +
            "    vec2 halfSize = size * 0.5;\n" +
            "    float r = min(radius, min(halfSize.x, halfSize.y));\n" +
            "    float distance = roundSDF(coord - halfSize, halfSize, r);\n" +
            "    float mask = 1.0 - smoothstep(0.0, softness, distance);\n" +
            "    float brightness = mix(1.0, 0.42, st.y);\n" +
            "    vec3 rgb = hsv2rgb(vec3(st.x, 0.86, brightness));\n" +
            "    rgb += dither(st);\n" +
            "    gl_FragColor = vec4(rgb, alpha * mask);\n" +
            "}\n";

    private static final String ROUNDED_PALETTE_FRAGMENT =
            "#version 120\n" +
            "uniform vec2 rectSize;\n" +
            "uniform float radius;\n" +
            "uniform float padding;\n" +
            "uniform float softness;\n" +
            "uniform float hue;\n" +
            "uniform float alpha;\n" +
            "#define NOISE (0.35 / 255.0)\n" +
            "float roundSDF(vec2 p, vec2 halfSize, float r) {\n" +
            "    vec2 q = abs(p) - halfSize + vec2(r);\n" +
            "    return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - r;\n" +
            "}\n" +
            "float dither(vec2 coord) {\n" +
            "    return mix(NOISE, -NOISE, fract(sin(dot(coord, vec2(12.9898, 78.233))) * 43758.5453));\n" +
            "}\n" +
            "vec3 hsv2rgb(vec3 c) {\n" +
            "    vec3 p = abs(fract(c.xxx + vec3(0.0, 0.6666667, 0.3333333)) * 6.0 - 3.0);\n" +
            "    return c.z * mix(vec3(1.0), clamp(p - 1.0, 0.0, 1.0), c.y);\n" +
            "}\n" +
            "void main() {\n" +
            "    vec2 size = max(rectSize, vec2(0.001));\n" +
            "    vec2 coord = gl_TexCoord[0].st * (size + vec2(padding * 2.0)) - vec2(padding);\n" +
            "    vec2 st = clamp(coord / size, vec2(0.0), vec2(1.0));\n" +
            "    vec2 halfSize = size * 0.5;\n" +
            "    float r = min(radius, min(halfSize.x, halfSize.y));\n" +
            "    float distance = roundSDF(coord - halfSize, halfSize, r);\n" +
            "    float mask = 1.0 - smoothstep(0.0, softness, distance);\n" +
            "    vec3 rgb = hsv2rgb(vec3(hue, st.x, 1.0 - st.y));\n" +
            "    rgb += dither(st);\n" +
            "    gl_FragColor = vec4(rgb, alpha * mask);\n" +
            "}\n";

    private static final String ROUNDED_BORDER_FRAGMENT =
            "#version 120\n" +
            "uniform vec2 rectSize;\n" +
            "uniform vec4 fillColor;\n" +
            "uniform vec4 borderColor;\n" +
            "uniform float radius;\n" +
            "uniform float borderWidth;\n" +
            "uniform float padding;\n" +
            "uniform float softness;\n" +
            "float roundSDF(vec2 p, vec2 halfSize, float r) {\n" +
            "    vec2 q = abs(p) - halfSize + vec2(r);\n" +
            "    return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - r;\n" +
            "}\n" +
            "void main() {\n" +
            "    vec2 size = max(rectSize, vec2(0.001));\n" +
            "    vec2 coord = gl_TexCoord[0].st * (size + vec2(padding * 2.0)) - vec2(padding);\n" +
            "    vec2 halfSize = size * 0.5;\n" +
            "    float outerRadius = min(radius, min(halfSize.x, halfSize.y));\n" +
            "    float outerDistance = roundSDF(coord - halfSize, halfSize, outerRadius);\n" +
            "    float outerAlpha = 1.0 - smoothstep(0.0, softness, outerDistance);\n" +
            "    vec2 innerHalf = max(halfSize - vec2(borderWidth), vec2(0.0));\n" +
            "    float innerRadius = max(outerRadius - borderWidth, 0.0);\n" +
            "    float innerDistance = roundSDF(coord - halfSize, innerHalf, innerRadius);\n" +
            "    float innerAlpha = 1.0 - smoothstep(0.0, softness, innerDistance);\n" +
            "    float borderMask = clamp(outerAlpha - innerAlpha, 0.0, 1.0);\n" +
            "    float fillAlpha = fillColor.a * innerAlpha;\n" +
            "    float borderAlpha = borderColor.a * borderMask;\n" +
            "    float totalAlpha = min(fillAlpha + borderAlpha, 1.0);\n" +
            "    vec3 result = vec3(0.0);\n" +
            "    if (totalAlpha > 0.0) {\n" +
            "        result = (fillColor.rgb * fillAlpha + borderColor.rgb * borderAlpha) / totalAlpha;\n" +
            "    }\n" +
            "    gl_FragColor = vec4(result, totalAlpha);\n" +
            "}\n";

    private static final String ROUNDED_SHADOW_FRAGMENT =
            "#version 120\n" +
            "uniform vec2 rectSize;\n" +
            "uniform vec4 color;\n" +
            "uniform float radius;\n" +
            "uniform float padding;\n" +
            "uniform float softness;\n" +
            "uniform float shadowSize;\n" +
            "float roundSDF(vec2 p, vec2 halfSize, float r) {\n" +
            "    vec2 q = abs(p) - halfSize + vec2(r);\n" +
            "    return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - r;\n" +
            "}\n" +
            "void main() {\n" +
            "    vec2 size = max(rectSize, vec2(0.001));\n" +
            "    float shadow = max(shadowSize, 0.001);\n" +
            "    float totalPadding = shadow + padding;\n" +
            "    vec2 coord = gl_TexCoord[0].st * (size + vec2(totalPadding * 2.0)) - vec2(totalPadding);\n" +
            "    vec2 halfSize = size * 0.5;\n" +
            "    float r = min(radius, min(halfSize.x, halfSize.y));\n" +
            "    float distance = roundSDF(coord - halfSize, halfSize, r);\n" +
            "    float outside = smoothstep(-softness, 0.0, distance);\n" +
            "    float falloff = 1.0 - smoothstep(0.0, shadow, distance);\n" +
            "    float alpha = color.a * outside * falloff * falloff;\n" +
            "    gl_FragColor = vec4(color.rgb, alpha);\n" +
            "}\n";

    private static final String CIRCLE_FRAGMENT =
            "#version 120\n" +
            "uniform vec4 color;\n" +
            "uniform float circleRadius;\n" +
            "uniform float padding;\n" +
            "uniform float softness;\n" +
            "void main() {\n" +
            "    float radius = max(circleRadius, 0.001);\n" +
            "    vec2 size = vec2(radius * 2.0);\n" +
            "    vec2 coord = gl_TexCoord[0].st * (size + vec2(padding * 2.0)) - vec2(padding);\n" +
            "    float dist = length(coord - vec2(radius));\n" +
            "    float alpha = 1.0 - smoothstep(radius - softness, radius, dist);\n" +
            "    gl_FragColor = vec4(color.rgb, color.a * alpha);\n" +
            "}\n";

    private static final String ARC_FRAGMENT =
            "#version 120\n" +
            "uniform vec4 color;\n" +
            "uniform float arcRadius;\n" +
            "uniform float lineWidth;\n" +
            "uniform float startAngle;\n" +
            "uniform float sweepAngle;\n" +
            "uniform float padding;\n" +
            "uniform float softness;\n" +
            "const float PI2 = 6.28318530718;\n" +
            "void main() {\n" +
            "    float radius = max(arcRadius, 0.001);\n" +
            "    float width = max(lineWidth, 0.001);\n" +
            "    vec2 size = vec2(radius * 2.0);\n" +
            "    vec2 coord = gl_TexCoord[0].st * (size + vec2(padding * 2.0)) - vec2(padding);\n" +
            "    vec2 delta = coord - vec2(radius);\n" +
            "    float dist = length(delta);\n" +
            "    float ring = 1.0 - smoothstep(width * 0.5, width * 0.5 + softness, abs(dist - radius));\n" +
            "    float sweep = clamp(sweepAngle, 0.0, PI2);\n" +
            "    float angle = mod(atan(delta.y, delta.x) - startAngle + PI2, PI2);\n" +
            "    float cap = max(softness / max(radius, 1.0), 0.001);\n" +
            "    float angleMask = 1.0;\n" +
            "    if (sweep < PI2 - 0.001) {\n" +
            "        angleMask = smoothstep(0.0, cap, angle) * (1.0 - smoothstep(max(0.0, sweep - cap), sweep, angle));\n" +
            "    }\n" +
            "    gl_FragColor = vec4(color.rgb, color.a * ring * angleMask);\n" +
            "}\n";

    private static final String LINE_FRAGMENT =
            "#version 120\n" +
            "uniform vec2 rectSize;\n" +
            "uniform vec2 startPoint;\n" +
            "uniform vec2 endPoint;\n" +
            "uniform vec4 color;\n" +
            "uniform float lineWidth;\n" +
            "uniform float softness;\n" +
            "void main() {\n" +
            "    vec2 point = gl_TexCoord[0].st * max(rectSize, vec2(0.001));\n" +
            "    vec2 line = endPoint - startPoint;\n" +
            "    float len2 = max(dot(line, line), 0.0001);\n" +
            "    float t = clamp(dot(point - startPoint, line) / len2, 0.0, 1.0);\n" +
            "    vec2 nearest = startPoint + line * t;\n" +
            "    float dist = length(point - nearest);\n" +
            "    float alpha = 1.0 - smoothstep(lineWidth * 0.5, lineWidth * 0.5 + softness, dist);\n" +
            "    gl_FragColor = vec4(color.rgb, color.a * alpha);\n" +
            "}\n";

    private static final String CIRCLE_BADGE_FRAGMENT =
            "#version 120\n" +
            "uniform vec4 fillColor;\n" +
            "uniform vec4 trackColor;\n" +
            "uniform vec4 progressColor;\n" +
            "uniform float badgeRadius;\n" +
            "uniform float ringWidth;\n" +
            "uniform float progress;\n" +
            "uniform float padding;\n" +
            "uniform float softness;\n" +
            "void main() {\n" +
            "    float radius = max(badgeRadius, 0.001);\n" +
            "    float ring = clamp(ringWidth, 0.001, radius);\n" +
            "    vec2 size = vec2(radius * 2.0);\n" +
            "    vec2 coord = gl_TexCoord[0].st * (size + vec2(padding * 2.0)) - vec2(padding);\n" +
            "    vec2 center = vec2(radius);\n" +
            "    vec2 delta = coord - center;\n" +
            "    float dist = length(delta);\n" +
            "    float innerRadius = max(radius - ring, 0.0);\n" +
            "    float fillMask = 1.0 - smoothstep(innerRadius - softness, innerRadius, dist);\n" +
            "    float outerMask = 1.0 - smoothstep(radius - softness, radius, dist);\n" +
            "    float innerCut = 1.0 - smoothstep(innerRadius - softness, innerRadius, dist);\n" +
            "    float ringMask = clamp(outerMask - innerCut, 0.0, 1.0);\n" +
            "    float angle = mod(atan(delta.y, delta.x) + 1.57079632679, 6.28318530718) / 6.28318530718;\n" +
            "    float progressMask = step(angle, clamp(progress, 0.0, 1.0));\n" +
            "    vec4 ringColor = mix(trackColor, progressColor, progressMask);\n" +
            "    float fillAlpha = fillColor.a * fillMask;\n" +
            "    float ringAlpha = ringColor.a * ringMask;\n" +
            "    float alpha = max(fillAlpha, ringAlpha);\n" +
            "    vec3 rgb = vec3(0.0);\n" +
            "    if (alpha > 0.0) {\n" +
            "        rgb = (fillColor.rgb * fillAlpha + ringColor.rgb * ringAlpha) / max(fillAlpha + ringAlpha, 0.0001);\n" +
            "    }\n" +
            "    gl_FragColor = vec4(rgb, alpha);\n" +
            "}\n";

    private static final String GAUSSIAN_BLUR_FRAGMENT =
            "#version 120\n" +
            "uniform sampler2D screenTex;\n" +
            "uniform vec2 u_direction;\n" +
            "uniform vec2 u_resolution;\n" +
            "uniform float u_radius;\n" +
            "vec4 blur13(vec2 uv, vec2 resolution, vec2 direction) {\n" +
            "    vec4 color = vec4(0.0);\n" +
            "    vec2 off1 = vec2(1.411764705882353) * direction;\n" +
            "    vec2 off2 = vec2(3.2941176470588234) * direction;\n" +
            "    vec2 off3 = vec2(5.176470588235294) * direction;\n" +
            "    color += texture2D(screenTex, uv) * 0.1964825501511404;\n" +
            "    color += texture2D(screenTex, uv + (off1 / resolution)) * 0.2969069646728344;\n" +
            "    color += texture2D(screenTex, uv - (off1 / resolution)) * 0.2969069646728344;\n" +
            "    color += texture2D(screenTex, uv + (off2 / resolution)) * 0.09447039785044732;\n" +
            "    color += texture2D(screenTex, uv - (off2 / resolution)) * 0.09447039785044732;\n" +
            "    color += texture2D(screenTex, uv + (off3 / resolution)) * 0.010381362401148057;\n" +
            "    color += texture2D(screenTex, uv - (off3 / resolution)) * 0.010381362401148057;\n" +
            "    return color;\n" +
            "}\n" +
            "void main() {\n" +
            "    gl_FragColor = blur13(gl_TexCoord[0].st, u_resolution, u_direction * u_radius);\n" +
            "}\n";

    private static final String FROSTED_GLASS_FRAGMENT =
            "#version 120\n" +
            "uniform vec2 rectSize;\n" +
            "uniform vec4 fillColor;\n" +
            "uniform vec4 borderColor;\n" +
            "uniform sampler2D screenTex;\n" +
            "uniform vec2 screenSize;\n" +
            "uniform vec2 viewportSize;\n" +
            "uniform float radius;\n" +
            "uniform float borderWidth;\n" +
            "uniform float padding;\n" +
            "uniform float softness;\n" +
            "uniform float grainStrength;\n" +
            "uniform float blurRadius;\n" +
            "float roundSDF(vec2 p, vec2 halfSize, float r) {\n" +
            "    vec2 q = abs(p) - halfSize + vec2(r);\n" +
            "    return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - r;\n" +
            "}\n" +
            "float noise(vec2 p) {\n" +
            "    return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453123);\n" +
            "}\n" +
            "float softNoise(vec2 p) {\n" +
            "    vec2 i = floor(p);\n" +
            "    vec2 f = fract(p);\n" +
            "    f = f * f * (3.0 - 2.0 * f);\n" +
            "    float a = noise(i);\n" +
            "    float b = noise(i + vec2(1.0, 0.0));\n" +
            "    float c = noise(i + vec2(0.0, 1.0));\n" +
            "    float d = noise(i + vec2(1.0, 1.0));\n" +
            "    return mix(mix(a, b, f.x), mix(c, d, f.x), f.y);\n" +
            "}\n" +
            "vec3 bloomPick(vec3 color) {\n" +
            "    float luma = dot(color, vec3(0.299, 0.587, 0.114));\n" +
            "    float mask = smoothstep(0.46, 0.94, luma);\n" +
            "    return color * mask;\n" +
            "}\n" +
            "vec2 safeUv(vec2 uv) {\n" +
            "    return clamp(uv, vec2(0.0015), vec2(0.9985));\n" +
            "}\n" +
            "vec4 glassBlur(vec2 uv) {\n" +
            "    vec2 texel = 1.0 / max(screenSize, vec2(1.0));\n" +
            "    vec2 r1 = texel * blurRadius;\n" +
            "    vec2 r2 = r1 * 2.15;\n" +
            "    vec2 r3 = r1 * 3.65;\n" +
            "    vec4 c = texture2D(screenTex, safeUv(uv)) * 0.150;\n" +
            "    c += texture2D(screenTex, safeUv(uv + r1 * vec2( 1.0,  0.0))) * 0.082;\n" +
            "    c += texture2D(screenTex, safeUv(uv + r1 * vec2(-1.0,  0.0))) * 0.082;\n" +
            "    c += texture2D(screenTex, safeUv(uv + r1 * vec2( 0.0,  1.0))) * 0.082;\n" +
            "    c += texture2D(screenTex, safeUv(uv + r1 * vec2( 0.0, -1.0))) * 0.082;\n" +
            "    c += texture2D(screenTex, safeUv(uv + r1 * vec2( 0.72,  0.72))) * 0.060;\n" +
            "    c += texture2D(screenTex, safeUv(uv + r1 * vec2(-0.72,  0.72))) * 0.060;\n" +
            "    c += texture2D(screenTex, safeUv(uv + r1 * vec2( 0.72, -0.72))) * 0.060;\n" +
            "    c += texture2D(screenTex, safeUv(uv + r1 * vec2(-0.72, -0.72))) * 0.060;\n" +
            "    c += texture2D(screenTex, safeUv(uv + r2 * vec2( 1.0,  0.0))) * 0.043;\n" +
            "    c += texture2D(screenTex, safeUv(uv + r2 * vec2(-1.0,  0.0))) * 0.043;\n" +
            "    c += texture2D(screenTex, safeUv(uv + r2 * vec2( 0.0,  1.0))) * 0.043;\n" +
            "    c += texture2D(screenTex, safeUv(uv + r2 * vec2( 0.0, -1.0))) * 0.043;\n" +
            "    c += texture2D(screenTex, safeUv(uv + r2 * vec2( 0.72,  0.72))) * 0.030;\n" +
            "    c += texture2D(screenTex, safeUv(uv + r2 * vec2(-0.72,  0.72))) * 0.030;\n" +
            "    c += texture2D(screenTex, safeUv(uv + r2 * vec2( 0.72, -0.72))) * 0.030;\n" +
            "    c += texture2D(screenTex, safeUv(uv + r2 * vec2(-0.72, -0.72))) * 0.030;\n" +
            "    c += texture2D(screenTex, safeUv(uv + r3 * vec2( 0.90,  0.44))) * 0.019;\n" +
            "    c += texture2D(screenTex, safeUv(uv + r3 * vec2(-0.90,  0.44))) * 0.019;\n" +
            "    c += texture2D(screenTex, safeUv(uv + r3 * vec2( 0.44, -0.90))) * 0.019;\n" +
            "    c += texture2D(screenTex, safeUv(uv + r3 * vec2(-0.44, -0.90))) * 0.019;\n" +
            "    return c / 1.039;\n" +
            "}\n" +
            "vec3 bokehGlow(vec2 uv) {\n" +
            "    vec2 texel = 1.0 / max(screenSize, vec2(1.0));\n" +
            "    vec2 r = texel * blurRadius * 4.8;\n" +
            "    vec3 c = vec3(0.0);\n" +
            "    c += bloomPick(texture2D(screenTex, safeUv(uv + r * vec2( 1.00,  0.00))).rgb) * 0.095;\n" +
            "    c += bloomPick(texture2D(screenTex, safeUv(uv + r * vec2( 0.70,  0.70))).rgb) * 0.088;\n" +
            "    c += bloomPick(texture2D(screenTex, safeUv(uv + r * vec2( 0.00,  1.00))).rgb) * 0.095;\n" +
            "    c += bloomPick(texture2D(screenTex, safeUv(uv + r * vec2(-0.70,  0.70))).rgb) * 0.088;\n" +
            "    c += bloomPick(texture2D(screenTex, safeUv(uv + r * vec2(-1.00,  0.00))).rgb) * 0.095;\n" +
            "    c += bloomPick(texture2D(screenTex, safeUv(uv + r * vec2(-0.70, -0.70))).rgb) * 0.088;\n" +
            "    c += bloomPick(texture2D(screenTex, safeUv(uv + r * vec2( 0.00, -1.00))).rgb) * 0.095;\n" +
            "    c += bloomPick(texture2D(screenTex, safeUv(uv + r * vec2( 0.70, -0.70))).rgb) * 0.088;\n" +
            "    vec2 r2 = r * 1.85;\n" +
            "    c += bloomPick(texture2D(screenTex, safeUv(uv + r2 * vec2( 0.92,  0.38))).rgb) * 0.055;\n" +
            "    c += bloomPick(texture2D(screenTex, safeUv(uv + r2 * vec2(-0.92,  0.38))).rgb) * 0.055;\n" +
            "    c += bloomPick(texture2D(screenTex, safeUv(uv + r2 * vec2( 0.38, -0.92))).rgb) * 0.055;\n" +
            "    c += bloomPick(texture2D(screenTex, safeUv(uv + r2 * vec2(-0.38, -0.92))).rgb) * 0.055;\n" +
            "    return c;\n" +
            "}\n" +
            "void main() {\n" +
            "    vec2 size = max(rectSize, vec2(0.001));\n" +
            "    vec2 coord = gl_TexCoord[0].st * (size + vec2(padding * 2.0)) - vec2(padding);\n" +
            "    vec2 st = clamp(coord / size, vec2(0.0), vec2(1.0));\n" +
            "    vec2 halfSize = size * 0.5;\n" +
            "    float outerRadius = min(radius, min(halfSize.x, halfSize.y));\n" +
            "    float outerDistance = roundSDF(coord - halfSize, halfSize, outerRadius);\n" +
            "    float outerAlpha = 1.0 - smoothstep(0.0, softness, outerDistance);\n" +
            "    vec2 innerHalf = max(halfSize - vec2(borderWidth), vec2(0.0));\n" +
            "    float innerRadius = max(outerRadius - borderWidth, 0.0);\n" +
            "    float innerDistance = roundSDF(coord - halfSize, innerHalf, innerRadius);\n" +
            "    float innerAlpha = 1.0 - smoothstep(0.0, softness, innerDistance);\n" +
            "    float borderMask = clamp(outerAlpha - innerAlpha, 0.0, 1.0);\n" +
            "    float grain = 0.0;\n" +
            "    float fineGrain = 0.0;\n" +
            "    float fog = softNoise(gl_FragCoord.xy * 0.018) - 0.5;\n" +
            "    float topGlow = (1.0 - st.y) * 0.052;\n" +
            "    float sideGlow = (1.0 - st.x) * 0.018;\n" +
            "    vec2 screenUv = safeUv(gl_FragCoord.xy / max(viewportSize, vec2(1.0)));\n" +
            "    vec2 refractUv = screenUv + vec2(noise(gl_FragCoord.yx * 0.13) - 0.5, grain + fog * 0.65) / max(screenSize, vec2(1.0)) * 7.0;\n" +
            "    vec3 blurred = glassBlur(refractUv).rgb;\n" +
            "    vec3 bokeh = bokehGlow(refractUv);\n" +
            "    float luma = dot(blurred, vec3(0.299, 0.587, 0.114));\n" +
            "    blurred = mix(vec3(luma), blurred, 0.82);\n" +
            "    blurred = mix(blurred, vec3(0.5) + (blurred - vec3(0.5)) * 0.78, 0.55);\n" +
            "    vec3 tint = fillColor.rgb + vec3(topGlow + sideGlow + grain * grainStrength);\n" +
            "    float tintAmount = clamp(fillColor.a * 0.36 + 0.13, 0.16, 0.50);\n" +
            "    vec3 glass = mix(blurred, tint, tintAmount);\n" +
            "    glass += bokeh * 0.72;\n" +
            "    glass += vec3(0.030 * (1.0 - st.y) + 0.010 * (1.0 - st.x));\n" +
            "    glass += vec3(fog * 0.026);\n" +
            "    glass = mix(glass, glass + fillColor.rgb * 0.10, smoothstep(0.35, 1.0, length(bokeh)));\n" +
            "    vec3 edge = mix(glass, borderColor.rgb + vec3(0.10), clamp(borderColor.a * 3.4, 0.0, 1.0));\n" +
            "    float fillAlpha = clamp(fillColor.a * 0.56 + 0.070, 0.10, 0.66) * innerAlpha;\n" +
            "    float borderAlpha = borderColor.a * borderMask * 1.08;\n" +
            "    float totalAlpha = max(fillAlpha, borderAlpha);\n" +
            "    vec3 rgb = mix(glass, edge, borderMask);\n" +
            "    gl_FragColor = vec4(clamp(rgb, 0.0, 1.0), totalAlpha);\n" +
            "}\n";

    private static final String VIEWPORT_FEATHER_BLUR_FRAGMENT =
            "#version 120\n" +
            "uniform sampler2D screenTex;\n" +
            "uniform sampler2D sourceTex;\n" +
            "uniform vec2 screenSize;\n" +
            "uniform vec2 viewportSize;\n" +
            "uniform float topEdge;\n" +
            "uniform float opacity;\n" +
            "vec2 safeUv(vec2 uv) {\n" +
            "    return clamp(uv, vec2(0.0015), vec2(0.9985));\n" +
            "}\n" +
            "float smoother(float x) {\n" +
            "    x = clamp(x, 0.0, 1.0);\n" +
            "    return x * x * x * (x * (x * 6.0 - 15.0) + 10.0);\n" +
            "}\n" +
            "void main() {\n" +
            "    vec2 st = clamp(gl_TexCoord[0].st, vec2(0.0), vec2(1.0));\n" +
            "    float topMask = smoother(1.0 - st.y);\n" +
            "    float bottomMask = smoother(st.y);\n" +
            "    float transition = mix(bottomMask, topMask, clamp(topEdge, 0.0, 1.0));\n" +
            "    float mask = pow(clamp(transition, 0.0, 1.0), 0.92) * clamp(opacity, 0.0, 1.0);\n" +
            "    vec2 uv = safeUv(gl_FragCoord.xy / max(viewportSize, vec2(1.0)));\n" +
            "    vec3 source = texture2D(sourceTex, uv).rgb;\n" +
            "    vec3 blurred = texture2D(screenTex, uv).rgb;\n" +
            "    gl_FragColor = vec4(mix(source, blurred, clamp(transition, 0.0, 1.0)), mask);\n" +
            "}\n";
}
