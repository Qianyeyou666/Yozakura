package gq.yozakura.core.modern;

import gq.yozakura.engine.render.ui.LiquidGlassSettings;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

final class ModernShaderRenderer {
    private static final float EDGE_SOFTNESS = 0.75f;
    private static final float EDGE_PADDING = 1.0f;
    private static final float MAX_LIQUID_GLASS_BLUR_RADIUS = 64.0f;
    private static final float MIN_LIQUID_GLASS_BLUR_DOWNSCALE = 0.10f;
    private static final float MAX_LIQUID_GLASS_BLUR_DOWNSCALE = 1.0f;
    private static final float MAX_GAUSSIAN_PASS_RADIUS = 10.0f;
    private static final int MAX_GAUSSIAN_ITERATIONS = 10;
    private static final int BLUR_KEY_SCALE = 1000;
    private static final float BLUR_RADIUS_KEY_STEP = 2.0f;
    private static final float BLUR_DOWNSCALE_KEY_STEP = 0.05f;
    private static final long GLASS_CAPTURE_INTERVAL_MS = 50L;
    private static final String LIQUID_GLASS_FRAGMENT_RESOURCE =
            "/assets/minecraft/yozakura/shaders/liquid_glass_150.frag";
    private static final IntBuffer VIEWPORT_BUFFER = BufferUtils.createIntBuffer(16);
    private static final Map<BlurKey, BlurCache> BLUR_CACHES = new HashMap<BlurKey, BlurCache>();

    private static Program roundedProgram;
    private static Program roundedBorderProgram;
    private static Program liquidGlassProgram;
    private static Program gaussianBlurProgram;
    private static int screenTexture;
    private static int capturedWidth;
    private static int capturedHeight;
    private static int glassCaptureVersion = 1;
    private static int sharedGlassSourceVersion = -1;
    private static int sharedGlassViewportX;
    private static int sharedGlassViewportY;
    private static long lastGlassCaptureMs;
    private static boolean screenTextureConfigured;
    private static boolean shaderUnavailable;
    private static boolean framebufferUnavailable;
    private static Boolean shaderSupported;
    private static Boolean framebufferSupported;

    private ModernShaderRenderer() {
    }

    static void beginFrame() {
        long now = System.currentTimeMillis();
        if (now - lastGlassCaptureMs < GLASS_CAPTURE_INTERVAL_MS) {
            return;
        }
        lastGlassCaptureMs = now;
        advanceGlassCaptureVersion();
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

    static boolean drawRoundedRect(float left, float top, float right, float bottom, float radius, int color) {
        Program program = getRoundedProgram();
        if (program == null || right <= left || bottom <= top || alpha(color) <= 0) {
            return false;
        }
        ShaderState state = beginProgram(program);
        try {
            setRoundedUniforms(program, right - left, bottom - top, radius);
            setColor(program, "color", color);
            drawQuad(left, top, right, bottom, EDGE_PADDING);
            return true;
        } catch (Throwable throwable) {
            logFailure("Modern rounded shader failed", throwable);
            return false;
        } finally {
            endProgram(state);
        }
    }

    static boolean drawRoundedBorder(float left, float top, float right, float bottom, float radius,
                                     float borderWidth, int fillColor, int borderColor) {
        Program program = getRoundedBorderProgram();
        if (program == null || right <= left || bottom <= top
                || (alpha(fillColor) <= 0 && alpha(borderColor) <= 0)) {
            return false;
        }
        float width = right - left;
        float height = bottom - top;
        float clampedBorder = Math.max(0.0f, Math.min(borderWidth, Math.min(width, height) / 2.0f));
        ShaderState state = beginProgram(program);
        try {
            setRoundedUniforms(program, width, height, radius);
            program.set1f("borderWidth", clampedBorder);
            setColor(program, "fillColor", fillColor);
            setColor(program, "borderColor", borderColor);
            drawQuad(left, top, right, bottom, EDGE_PADDING);
            return true;
        } catch (Throwable throwable) {
            logFailure("Modern rounded border shader failed", throwable);
            return false;
        } finally {
            endProgram(state);
        }
    }

    static boolean drawLiquidGlass(float left, float top, float right, float bottom, float radius,
                                   float borderWidth, int fillColor, int borderColor) {
        return drawLiquidGlass(left, top, right, bottom, radius, borderWidth, fillColor, borderColor,
                LiquidGlassSettings.defaults());
    }

    static boolean drawLiquidGlass(float left, float top, float right, float bottom, float radius,
                                   float borderWidth, int fillColor, int borderColor,
                                   LiquidGlassSettings settings) {
        LiquidGlassSettings resolved = settings == null ? LiquidGlassSettings.defaults() : settings;
        Program program = getLiquidGlassProgram();
        if (program == null || right <= left || bottom <= top
                || (alpha(fillColor) <= 0 && alpha(borderColor) <= 0)) {
            return false;
        }
        BlurCache blurCache = ensureFrostedGlassTexture(resolved);
        if (blurCache == null || !blurCache.ready) {
            return false;
        }

        float width = right - left;
        float height = bottom - top;
        float clampedBorder = Math.max(0.0f, Math.min(borderWidth, Math.min(width, height) / 2.0f));
        ShaderState state = beginProgram(program);
        try {
            setActiveTexture(GL13.GL_TEXTURE0);
            bindTexture(blurCache.textureB);
            setRoundedUniforms(program, width, height, radius);
            program.set1f("borderWidth", clampedBorder);
            uploadLiquidGlassSettings(program, resolved);
            program.set2f("screenSize", blurCache.blurWidth, blurCache.blurHeight);
            program.set2f("viewportSize", blurCache.sourceWidth, blurCache.sourceHeight);
            program.set1i("screenTex", 0);
            setColor(program, "fillColor", fillColor);
            setColor(program, "borderColor", borderColor);
            drawQuad(left, top, right, bottom, EDGE_PADDING);
            return true;
        } catch (Throwable throwable) {
            logFailure("Modern liquid glass shader failed", throwable);
            return false;
        } finally {
            endProgram(state);
        }
    }

    private static BlurCache ensureFrostedGlassTexture(LiquidGlassSettings settings) {
        if (!supportsShaders() || !supportsFramebufferBlur()) {
            return null;
        }
        TextureState textureState = saveTexture0State();
        try {
            if (!ModernGlCompat.getViewport(VIEWPORT_BUFFER)) {
                return null;
            }
            int viewportX = VIEWPORT_BUFFER.get(0);
            int viewportY = VIEWPORT_BUFFER.get(1);
            int viewportW = VIEWPORT_BUFFER.get(2);
            int viewportH = VIEWPORT_BUFFER.get(3);
            if (viewportW <= 0 || viewportH <= 0) {
                return null;
            }

            BlurKey key = BlurKey.from(settings);
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
            GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, viewportW, viewportH, 0,
                    GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, (ByteBuffer) null);
            capturedWidth = viewportW;
            capturedHeight = viewportH;
            sharedGlassSourceVersion = -1;
        }
        return true;
    }

    private static boolean buildFrostedBlur(BlurCache cache, int sourceTexture, int width, int height,
                                            float blurRadius, int blurIterations, float blurDownscale) {
        if (cache == null || !supportsFramebufferBlur() || width <= 0 || height <= 0
                || getGaussianBlurProgram() == null) {
            return false;
        }
        int targetWidth = Math.max(1, Math.round(width * blurDownscale));
        int targetHeight = Math.max(1, Math.round(height * blurDownscale));
        int previousFramebuffer = GL11.glGetInteger(GL30.GL_FRAMEBUFFER_BINDING);
        VIEWPORT_BUFFER.clear();
        if (!ModernGlCompat.getViewport(VIEWPORT_BUFFER)) {
            cache.ready = false;
            return false;
        }
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
            logFailure("Modern liquid glass blur failed", throwable);
            return false;
        } finally {
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, previousFramebuffer);
            GL11.glViewport(previousViewportX, previousViewportY, previousViewportW, previousViewportH);
        }
    }

    private static void runBlurPass(int sourceTexture, int framebuffer, int width, int height,
                                    int sourceWidth, int sourceHeight, float blurRadius, float dirX, float dirY) {
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, framebuffer);
        GL11.glViewport(0, 0, width, height);
        Program program = getGaussianBlurProgram();
        if (program == null) {
            return;
        }
        ShaderState state = beginProgram(program);
        try {
            setActiveTexture(GL13.GL_TEXTURE0);
            bindTexture(sourceTexture);
            program.set1i("screenTex", 0);
            program.set2f("u_resolution", Math.max(1.0f, sourceWidth), Math.max(1.0f, sourceHeight));
            program.set2f("u_direction", dirX, dirY);
            program.set1f("u_radius", clampGaussianPassRadius(blurRadius));
            drawFullscreenPassQuad();
        } finally {
            endProgram(state);
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

    private static void setupBlurTexture(int texture, int width, int height) {
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, width, height, 0,
                GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, (ByteBuffer) null);
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
        GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0,
                GL11.GL_TEXTURE_2D, texture, 0);
        int status = GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER);
        if (status != GL30.GL_FRAMEBUFFER_COMPLETE) {
            logFailure("Modern liquid glass framebuffer incomplete: 0x" + Integer.toHexString(status), null);
            return false;
        }
        return true;
    }

    private static Program getRoundedProgram() {
        if (roundedProgram == null) {
            roundedProgram = createProgram("modern rounded", ROUNDED_FRAGMENT);
        }
        return roundedProgram;
    }

    private static Program getRoundedBorderProgram() {
        if (roundedBorderProgram == null) {
            roundedBorderProgram = createProgram("modern rounded border", ROUNDED_BORDER_FRAGMENT);
        }
        return roundedBorderProgram;
    }

    private static Program getLiquidGlassProgram() {
        if (liquidGlassProgram == null) {
            String source = loadShaderResource(LIQUID_GLASS_FRAGMENT_RESOURCE);
            liquidGlassProgram = source == null ? null : createProgram("modern liquid glass", source);
        }
        return liquidGlassProgram;
    }

    private static Program getGaussianBlurProgram() {
        if (gaussianBlurProgram == null) {
            gaussianBlurProgram = createProgram("modern gaussian blur", GAUSSIAN_BLUR_FRAGMENT);
        }
        return gaussianBlurProgram;
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
            GL20.glBindAttribLocation(program, 0, "a_pos");
            GL20.glBindAttribLocation(program, 1, "a_uv");
            GL20.glLinkProgram(program);
            if (GL20.glGetProgrami(program, GL20.GL_LINK_STATUS) == 0) {
                throw new IllegalStateException(GL20.glGetProgramInfoLog(program, 4096));
            }
            return new Program(program);
        } catch (Throwable throwable) {
            logFailure("Modern shader program failed: " + label, throwable);
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
            throw new IllegalStateException(label + ": " + log);
        }
        return shader;
    }

    private static String loadShaderResource(String resourcePath) {
        InputStream stream = ModernShaderRenderer.class.getResourceAsStream(resourcePath);
        if (stream == null) {
            logFailure("Missing modern shader resource " + resourcePath, null);
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
            logFailure("Unable to read modern shader resource", exception);
            return null;
        } finally {
            try {
                stream.close();
            } catch (IOException ignored) {
            }
        }
    }

    private static ShaderState beginProgram(Program program) {
        if (program == null) {
            return null;
        }
        int previousProgram = 0;
        try {
            previousProgram = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        } catch (Throwable ignored) {
            previousProgram = 0;
        }
        TextureState textureState = saveTexture0State();
        RenderState renderState = captureRenderState();
        program.use();
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glDisable(GL11.GL_CULL_FACE);
        GL11.glDepthMask(false);
        return new ShaderState(previousProgram, textureState, renderState);
    }

    private static void endProgram(ShaderState state) {
        if (state == null) {
            return;
        }
        try {
            GL20.glUseProgram(state.previousProgram);
        } catch (Throwable ignored) {
            GL20.glUseProgram(0);
        }
        restoreRenderState(state.renderState);
        restoreTexture0State(state.textureState);
    }

    private static RenderState captureRenderState() {
        RenderState state = new RenderState();
        state.blend = safeIsEnabled(GL11.GL_BLEND);
        state.depth = safeIsEnabled(GL11.GL_DEPTH_TEST);
        state.cull = safeIsEnabled(GL11.GL_CULL_FACE);
        state.depthMask = safeGetBoolean(GL11.GL_DEPTH_WRITEMASK, true);
        state.blendSrc = safeGetInteger(GL11.GL_BLEND_SRC);
        state.blendDst = safeGetInteger(GL11.GL_BLEND_DST);
        return state;
    }

    private static void restoreRenderState(RenderState state) {
        if (state == null) {
            return;
        }
        setEnabled(GL11.GL_BLEND, state.blend);
        setEnabled(GL11.GL_DEPTH_TEST, state.depth);
        setEnabled(GL11.GL_CULL_FACE, state.cull);
        GL11.glDepthMask(state.depthMask);
        if (state.blendSrc != 0 || state.blendDst != 0) {
            GL11.glBlendFunc(state.blendSrc, state.blendDst);
        }
    }

    private static void setEnabled(int cap, boolean enabled) {
        if (enabled) {
            GL11.glEnable(cap);
        } else {
            GL11.glDisable(cap);
        }
    }

    private static int safeGetInteger(int pname) {
        try {
            return GL11.glGetInteger(pname);
        } catch (Throwable ignored) {
            return 0;
        }
    }

    private static boolean safeGetBoolean(int pname, boolean fallback) {
        try {
            return GL11.glGetBoolean(pname);
        } catch (Throwable ignored) {
            return fallback;
        }
    }

    private static boolean safeIsEnabled(int cap) {
        try {
            return GL11.glIsEnabled(cap);
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static TextureState saveTexture0State() {
        try {
            int activeTexture = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
            setActiveTexture(GL13.GL_TEXTURE0);
            int texture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
            return new TextureState(activeTexture, texture);
        } catch (Throwable throwable) {
            return null;
        }
    }

    private static void restoreTexture0State(TextureState state) {
        if (state == null) {
            return;
        }
        setActiveTexture(GL13.GL_TEXTURE0);
        bindTexture(state.texture);
        setActiveTexture(state.activeTexture);
    }

    private static void setActiveTexture(int textureUnit) {
        GL13.glActiveTexture(textureUnit);
    }

    private static void bindTexture(int texture) {
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
    }

    private static void setRoundedUniforms(Program program, float width, float height, float radius) {
        float clampedRadius = Math.max(0.0f, Math.min(radius, Math.min(width, height) / 2.0f));
        program.set2f("rectSize", width, height);
        program.set1f("radius", clampedRadius);
        program.set1f("padding", EDGE_PADDING);
        program.set1f("softness", EDGE_SOFTNESS);
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

    private static void setColor(Program program, String uniform, int color) {
        program.set4f(uniform,
                ((color >> 16) & 255) / 255.0f,
                ((color >> 8) & 255) / 255.0f,
                (color & 255) / 255.0f,
                ((color >> 24) & 255) / 255.0f);
    }

    private static void drawQuad(float left, float top, float right, float bottom, float padding) {
        float drawLeft = left - padding;
        float drawTop = top - padding;
        float drawRight = right + padding;
        float drawBottom = bottom + padding;

        ModernLegacyRenderer.drawShaderGuiQuad(drawLeft, drawTop, drawRight, drawBottom, 0.0f);
    }

    private static void drawFullscreenPassQuad() {
        ModernLegacyRenderer.drawShaderFullscreenQuad();
    }

    private static boolean supportsShaders() {
        if (shaderSupported != null) {
            return shaderSupported.booleanValue();
        }
        if (shaderUnavailable) {
            shaderSupported = Boolean.FALSE;
            return false;
        }
        try {
            String version = GL11.glGetString(GL11.GL_VERSION);
            if (version == null || version.length() == 0) {
                shaderUnavailable = true;
                shaderSupported = Boolean.FALSE;
                return false;
            }
            int program = GL20.glCreateProgram();
            if (program != 0) {
                GL20.glDeleteProgram(program);
            }
            shaderSupported = Boolean.TRUE;
            return true;
        } catch (Throwable throwable) {
            shaderUnavailable = true;
            shaderSupported = Boolean.FALSE;
            logFailure("Modern OpenGL shader support unavailable", throwable);
            return false;
        }
    }

    private static boolean supportsFramebufferBlur() {
        if (framebufferSupported != null) {
            return framebufferSupported.booleanValue();
        }
        if (framebufferUnavailable) {
            framebufferSupported = Boolean.FALSE;
            return false;
        }
        try {
            int framebuffer = GL30.glGenFramebuffers();
            if (framebuffer != 0) {
                GL30.glDeleteFramebuffers(framebuffer);
            }
            framebufferSupported = Boolean.TRUE;
            return true;
        } catch (Throwable throwable) {
            framebufferUnavailable = true;
            framebufferSupported = Boolean.FALSE;
            logFailure("Modern OpenGL framebuffer support unavailable", throwable);
            return false;
        }
    }

    private static int guiWidth() {
        Object minecraft = ModernMinecraftAccess.minecraft();
        Object window = window(minecraft);
        int width = intValue(ModernForgeEventBridge.invoke(window, "getGuiScaledWidth"), -1);
        if (width <= 0) {
            width = intValue(ModernForgeEventBridge.invoke(window, "m_85445_"), -1);
        }
        return width;
    }

    private static int guiHeight() {
        Object minecraft = ModernMinecraftAccess.minecraft();
        Object window = window(minecraft);
        int height = intValue(ModernForgeEventBridge.invoke(window, "getGuiScaledHeight"), -1);
        if (height <= 0) {
            height = intValue(ModernForgeEventBridge.invoke(window, "m_85446_"), -1);
        }
        return height;
    }

    private static Object window(Object minecraft) {
        Object window = ModernForgeEventBridge.invoke(minecraft, "getWindow");
        if (window == null) {
            window = ModernForgeEventBridge.invoke(minecraft, "m_91268_");
        }
        if (window == null) {
            window = ModernForgeEventBridge.field(minecraft, "window");
        }
        if (window == null) {
            window = ModernForgeEventBridge.field(minecraft, "f_91067_");
        }
        return window;
    }

    private static int intValue(Object value, int fallback) {
        return value instanceof Number ? ((Number) value).intValue() : fallback;
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

    private static int quantizedBlurRadiusKey(float blurRadius) {
        float clamped = clampLiquidGlassBlurRadius(blurRadius);
        return Math.round(Math.round(clamped / BLUR_RADIUS_KEY_STEP) * BLUR_RADIUS_KEY_STEP * BLUR_KEY_SCALE);
    }

    private static int quantizedBlurDownscaleKey(float blurDownscale) {
        float clamped = clampLiquidGlassBlurDownscale(blurDownscale);
        return Math.round(Math.round(clamped / BLUR_DOWNSCALE_KEY_STEP) * BLUR_DOWNSCALE_KEY_STEP * BLUR_KEY_SCALE);
    }

    private static int alpha(int color) {
        return (color >>> 24) & 255;
    }

    private static void clearGlErrors() {
        try {
            int guard = 0;
            while (GL11.glGetError() != GL11.GL_NO_ERROR && guard++ < 8) {
                // Drain stale errors from Minecraft's render stack before testing our draw.
            }
        } catch (Throwable ignored) {
        }
    }

    private static boolean shaderSucceeded() {
        try {
            return GL11.glGetError() == GL11.GL_NO_ERROR;
        } catch (Throwable throwable) {
            return false;
        }
    }

    private static void logFailure(String message, Throwable throwable) {
        ModernForgeEventBridge.log(message, throwable);
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
            return new BlurKey(
                    quantizedBlurRadiusKey(settings.blurRadius()),
                    quantizedBlurDownscaleKey(settings.blurDownscale()),
                    clampGaussianIterations(settings.blurIterations()));
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
        private final Map<String, Integer> uniforms = new HashMap<String, Integer>();

        private Program(int id) {
            this.id = id;
        }

        private void use() {
            GL20.glUseProgram(id);
        }

        private int uniform(String name) {
            Integer cached = uniforms.get(name);
            if (cached != null) {
                return cached.intValue();
            }
            int location = GL20.glGetUniformLocation(id, name);
            uniforms.put(name, Integer.valueOf(location));
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
        private final RenderState renderState;

        private ShaderState(int previousProgram, TextureState textureState, RenderState renderState) {
            this.previousProgram = previousProgram;
            this.textureState = textureState;
            this.renderState = renderState;
        }
    }

    private static final class RenderState {
        private int blendSrc;
        private int blendDst;
        private boolean blend;
        private boolean depth;
        private boolean cull;
        private boolean depthMask;
    }

    private static final String VERTEX_SHADER =
            "#version 150\n" +
            "in vec2 a_pos;\n" +
            "in vec2 a_uv;\n" +
            "out vec2 v_uv;\n" +
            "void main() {\n" +
            "    v_uv = a_uv;\n" +
            "    gl_Position = vec4(a_pos, 0.0, 1.0);\n" +
            "}\n";

    private static final String ROUNDED_FRAGMENT =
            "#version 150\n" +
            "uniform vec2 rectSize;\n" +
            "uniform vec4 color;\n" +
            "uniform float radius;\n" +
            "uniform float padding;\n" +
            "uniform float softness;\n" +
            "in vec2 v_uv;\n" +
            "out vec4 fragColor;\n" +
            "float roundSDF(vec2 p, vec2 halfSize, float r) {\n" +
            "    vec2 q = abs(p) - halfSize + vec2(r);\n" +
            "    return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - r;\n" +
            "}\n" +
            "void main() {\n" +
            "    vec2 size = max(rectSize, vec2(0.001));\n" +
            "    vec2 coord = v_uv * (size + vec2(padding * 2.0)) - vec2(padding);\n" +
            "    vec2 halfSize = size * 0.5;\n" +
            "    float r = min(radius, min(halfSize.x, halfSize.y));\n" +
            "    float dist = roundSDF(coord - halfSize, halfSize, r);\n" +
            "    float alpha = color.a * (1.0 - smoothstep(0.0, softness, dist));\n" +
            "    if (alpha <= 0.0) discard;\n" +
            "    fragColor = vec4(color.rgb, alpha);\n" +
            "}\n";

    private static final String ROUNDED_BORDER_FRAGMENT =
            "#version 150\n" +
            "uniform vec2 rectSize;\n" +
            "uniform vec4 fillColor;\n" +
            "uniform vec4 borderColor;\n" +
            "uniform float radius;\n" +
            "uniform float borderWidth;\n" +
            "uniform float padding;\n" +
            "uniform float softness;\n" +
            "in vec2 v_uv;\n" +
            "out vec4 fragColor;\n" +
            "float roundSDF(vec2 p, vec2 halfSize, float r) {\n" +
            "    vec2 q = abs(p) - halfSize + vec2(r);\n" +
            "    return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - r;\n" +
            "}\n" +
            "void main() {\n" +
            "    vec2 size = max(rectSize, vec2(0.001));\n" +
            "    vec2 coord = v_uv * (size + vec2(padding * 2.0)) - vec2(padding);\n" +
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
            "    float alpha = max(fillAlpha, borderAlpha);\n" +
            "    if (alpha <= 0.0) discard;\n" +
            "    vec3 rgb = (fillColor.rgb * fillAlpha + borderColor.rgb * borderAlpha) / max(fillAlpha + borderAlpha, 0.0001);\n" +
            "    fragColor = vec4(rgb, alpha);\n" +
            "}\n";

    private static final String GAUSSIAN_BLUR_FRAGMENT =
            "#version 150\n" +
            "uniform sampler2D screenTex;\n" +
            "uniform vec2 u_direction;\n" +
            "uniform vec2 u_resolution;\n" +
            "uniform float u_radius;\n" +
            "in vec2 v_uv;\n" +
            "out vec4 fragColor;\n" +
            "vec4 blur13(vec2 uv, vec2 resolution, vec2 direction) {\n" +
            "    vec4 color = vec4(0.0);\n" +
            "    vec2 off1 = vec2(1.411764705882353) * direction;\n" +
            "    vec2 off2 = vec2(3.2941176470588234) * direction;\n" +
            "    vec2 off3 = vec2(5.176470588235294) * direction;\n" +
            "    color += texture(screenTex, uv) * 0.1964825501511404;\n" +
            "    color += texture(screenTex, uv + (off1 / resolution)) * 0.2969069646728344;\n" +
            "    color += texture(screenTex, uv - (off1 / resolution)) * 0.2969069646728344;\n" +
            "    color += texture(screenTex, uv + (off2 / resolution)) * 0.09447039785044732;\n" +
            "    color += texture(screenTex, uv - (off2 / resolution)) * 0.09447039785044732;\n" +
            "    color += texture(screenTex, uv + (off3 / resolution)) * 0.010381362401148057;\n" +
            "    color += texture(screenTex, uv - (off3 / resolution)) * 0.010381362401148057;\n" +
            "    return color;\n" +
            "}\n" +
            "void main() {\n" +
            "    fragColor = blur13(v_uv, u_resolution, u_direction * u_radius);\n" +
            "}\n";
}
