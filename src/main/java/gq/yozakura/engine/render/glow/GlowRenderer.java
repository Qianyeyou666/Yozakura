package gq.yozakura.engine.render.glow;

import gq.yozakura.engine.font.CFontRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.shader.Framebuffer;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.EXTFramebufferObject;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GLContext;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Batched screen-space glow renderer. Commands are recorded while the normal
 * HUD is drawn, then replayed into a mask and composited after the frame.
 */
public final class GlowRenderer {
    private static final String MASK_SHADER_RESOURCE =
            "/assets/minecraft/yozakura/shaders/glow_mask.frag";
    private static final String BLUR_SHADER_RESOURCE =
            "/assets/minecraft/yozakura/shaders/glow_blur.frag";
    private static final String COMPOSITE_SHADER_RESOURCE =
            "/assets/minecraft/yozakura/shaders/glow_composite.frag";
    private static final GlowProfile[] RENDER_ORDER = new GlowProfile[]{
            GlowProfile.SHADOW, GlowProfile.PANEL, GlowProfile.ACCENT, GlowProfile.TEXT
    };
    private static final int GLOW_ATTRIB_MASK = GL11.GL_ENABLE_BIT
            | GL11.GL_COLOR_BUFFER_BIT
            | GL11.GL_CURRENT_BIT
            | GL11.GL_DEPTH_BUFFER_BIT
            | GL11.GL_HINT_BIT
            | GL11.GL_SCISSOR_BIT
            | GL11.GL_STENCIL_BUFFER_BIT
            | GL11.GL_TEXTURE_BIT
            | GL11.GL_VIEWPORT_BIT;
    private static final FloatBuffer MATRIX_READ_BUFFER = BufferUtils.createFloatBuffer(16);
    private static final FloatBuffer MATRIX_UPLOAD_BUFFER = BufferUtils.createFloatBuffer(16);
    private static final IntBuffer INTEGER_BUFFER = BufferUtils.createIntBuffer(16);
    private static final FloatBuffer WEIGHT_BUFFER =
            BufferUtils.createFloatBuffer(GaussianKernel.MAX_RADIUS + 1);
    private static final String VERTEX_SHADER =
            "#version 120\n" +
                    "void main() {\n" +
                    "    gl_TexCoord[0] = gl_MultiTexCoord0;\n" +
                    "    gl_Position = gl_ModelViewProjectionMatrix * gl_Vertex;\n" +
                    "}\n";

    private final List<GlowCommand> commands = new ArrayList<GlowCommand>();
    private final Map<Integer, float[]> kernels = new HashMap<Integer, float[]>();
    private GlowProfile.Quality quality = GlowProfile.Quality.MEDIUM;
    private float globalStrength = 1.0f;
    private boolean frameOpen;
    private String failureReason;
    private boolean failureLogged;
    private Framebuffer maskFramebuffer;
    private Framebuffer horizontalFramebuffer;
    private Framebuffer verticalFramebuffer;
    private Program maskProgram;
    private Program blurProgram;
    private Program compositeProgram;

    public void beginFrame() {
        if (frameOpen) {
            throw new IllegalStateException("Glow frame started before the previous frame was flushed");
        }
        frameOpen = true;
        commands.clear();
    }

    public boolean isFrameOpen() {
        return frameOpen;
    }

    public boolean isAvailable() {
        return failureReason == null;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public GlowProfile.Quality getQuality() {
        return quality;
    }

    public void setQuality(GlowProfile.Quality quality) {
        if (quality == null) {
            throw new IllegalArgumentException("quality must not be null");
        }
        if (frameOpen) {
            throw new IllegalStateException("Glow quality cannot change during a frame");
        }
        this.quality = quality;
    }

    public float getGlobalStrength() {
        return globalStrength;
    }

    public void setGlobalStrength(float globalStrength) {
        if (frameOpen) {
            throw new IllegalStateException("Glow strength cannot change during a frame");
        }
        this.globalStrength = GlowProfile.clampStrength(globalStrength);
    }

    public void queueText(CFontRenderer font, String text, double x, double y,
                          int glowColor, float strength, GlowProfile profile) {
        requireOpenFrame();
        if (font == null || text == null || text.length() == 0) {
            return;
        }
        float resolvedStrength = GlowProfile.clampStrength(strength);
        if (resolvedStrength <= 0.0f || alpha(glowColor) <= 0) {
            return;
        }
        if (profile == null) {
            throw new IllegalArgumentException("profile must not be null");
        }
        commands.add(new TextCommand(font, text, x, y, glowColor, resolvedStrength, profile,
                RenderSnapshot.capture()));
    }

    public void queueRoundedRect(float left, float top, float right, float bottom, float radius,
                                 int glowColor, float strength, GlowProfile profile) {
        requireOpenFrame();
        float resolvedStrength = GlowProfile.clampStrength(strength);
        if (right <= left || bottom <= top || radius < 0.0f || resolvedStrength <= 0.0f
                || alpha(glowColor) <= 0) {
            return;
        }
        if (profile == null) {
            throw new IllegalArgumentException("profile must not be null");
        }
        commands.add(new RoundedRectCommand(left, top, right, bottom, radius, glowColor,
                resolvedStrength, profile, RenderSnapshot.capture()));
    }

    public void flush() {
        if (!frameOpen) {
            throw new IllegalStateException("Glow frame was flushed without beginFrame()");
        }

        RenderState state = null;
        boolean attribPushed = false;
        boolean matricesPushed = false;
        try {
            if (commands.isEmpty() || failureReason != null) {
                return;
            }
            ensureSupported();
            state = RenderState.capture();
            GL11.glPushAttrib(GLOW_ATTRIB_MASK);
            attribPushed = true;
            pushMatrices();
            matricesPushed = true;

            ensurePrograms();
            Minecraft minecraft = Minecraft.getMinecraft();
            for (GlowProfile profile : RENDER_ORDER) {
                if (hasCommands(profile)) {
                    renderProfile(minecraft, profile, state);
                }
            }
        } catch (Throwable throwable) {
            fail(throwable);
        } finally {
            if (matricesPushed) {
                popMatrices(state == null ? GL11.GL_MODELVIEW : state.matrixMode);
            }
            if (attribPushed) {
                GL11.glPopAttrib();
            }
            if (state != null) {
                state.restore();
            }
            gq.yozakura.engine.render.GLStateManager.syncToCurrent();
            GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);
            commands.clear();
            frameOpen = false;
        }
    }

    private void requireOpenFrame() {
        if (!frameOpen) {
            throw new IllegalStateException("Glow commands require beginFrame() before drawing");
        }
    }

    private void ensureSupported() {
        boolean supported;
        try {
            supported = OpenGlHelper.isFramebufferEnabled()
                    && GLContext.getCapabilities() != null
                    && GLContext.getCapabilities().OpenGL20;
        } catch (Throwable throwable) {
            throw new IllegalStateException("Unable to query OpenGL glow support", throwable);
        }
        if (!supported) {
            throw new IllegalStateException("Glow requires framebuffer support and OpenGL 2.0 shaders");
        }
    }

    private void ensurePrograms() {
        if (maskProgram == null) {
            maskProgram = createProgram(MASK_SHADER_RESOURCE);
        }
        if (blurProgram == null) {
            blurProgram = createProgram(BLUR_SHADER_RESOURCE);
        }
        if (compositeProgram == null) {
            compositeProgram = createProgram(COMPOSITE_SHADER_RESOURCE);
        }
    }

    private void renderProfile(Minecraft minecraft, GlowProfile profile, RenderState state) {
        int displayWidth = Math.max(1, minecraft.displayWidth);
        int displayHeight = Math.max(1, minecraft.displayHeight);
        float downsample = quality.getDownsample();
        int targetWidth = Math.max(1, Math.round(displayWidth * downsample));
        int targetHeight = Math.max(1, Math.round(displayHeight * downsample));
        ensureTargets(targetWidth, targetHeight);

        ScaledResolution scaledResolution = new ScaledResolution(minecraft);
        int radius = profile.resolveKernelRadius(scaledResolution.getScaleFactor(), quality);
        renderMask(profile, targetWidth, targetHeight, displayWidth, displayHeight);
        renderBlur(maskFramebuffer, horizontalFramebuffer, radius, true);
        renderBlur(horizontalFramebuffer, verticalFramebuffer, radius, false);
        renderComposite(state);
    }

    private boolean hasCommands(GlowProfile profile) {
        for (GlowCommand command : commands) {
            if (command.profile == profile) {
                return true;
            }
        }
        return false;
    }

    private void ensureTargets(int width, int height) {
        if (matches(maskFramebuffer, width, height)
                && matches(horizontalFramebuffer, width, height)
                && matches(verticalFramebuffer, width, height)) {
            return;
        }
        deleteTargets();
        maskFramebuffer = createTarget(width, height);
        horizontalFramebuffer = createTarget(width, height);
        verticalFramebuffer = createTarget(width, height);
    }

    private static boolean matches(Framebuffer framebuffer, int width, int height) {
        return framebuffer != null && framebuffer.framebufferWidth == width
                && framebuffer.framebufferHeight == height;
    }

    private Framebuffer createTarget(int width, int height) {
        Framebuffer framebuffer = new Framebuffer(width, height, false);
        framebuffer.setFramebufferColor(0.0f, 0.0f, 0.0f, 0.0f);
        framebuffer.setFramebufferFilter(GL11.GL_LINEAR);
        setActiveTexture(OpenGlHelper.defaultTexUnit);
        bindTexture(framebuffer.framebufferTexture);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
        return framebuffer;
    }

    private void deleteTargets() {
        deleteTarget(maskFramebuffer);
        deleteTarget(horizontalFramebuffer);
        deleteTarget(verticalFramebuffer);
        maskFramebuffer = null;
        horizontalFramebuffer = null;
        verticalFramebuffer = null;
    }

    private static void deleteTarget(Framebuffer framebuffer) {
        if (framebuffer != null) {
            framebuffer.deleteFramebuffer();
        }
    }

    private void renderMask(GlowProfile profile, int targetWidth, int targetHeight,
                            int displayWidth, int displayHeight) {
        clearAndBind(maskFramebuffer);
        configureMaskState();
        maskProgram.use();
        maskProgram.set1i("textureIn", 0);

        for (GlowCommand command : commands) {
            if (command.profile != profile) {
                continue;
            }
            command.snapshot.apply(targetWidth, targetHeight, displayWidth, displayHeight);
            maskProgram.set4f("maskColor", red(command.color), green(command.color), blue(command.color),
                    alpha(command.color) / 255.0f);
            maskProgram.set1f("strength", command.strength);
            if (command instanceof TextCommand) {
                TextCommand text = (TextCommand) command;
                maskProgram.set1i("mode", 0);
                text.font.drawStringForGlowMask(text.text, text.x, text.y);
            } else {
                RoundedRectCommand rect = (RoundedRectCommand) command;
                float physicalScale = Math.max(0.001f,
                        rect.snapshot.guiScaleFactor * quality.getDownsample());
                float padding = Math.max(1.0f / physicalScale, rect.radius * 0.15f);
                maskProgram.set1i("mode", 1);
                maskProgram.set2f("rectSize", rect.right - rect.left, rect.bottom - rect.top);
                maskProgram.set1f("radius", rect.radius);
                maskProgram.set1f("padding", padding);
                maskProgram.set1f("softness", Math.max(0.0001f, 0.75f / physicalScale));
                drawQuad(rect.left, rect.top, rect.right, rect.bottom, padding);
            }
        }
    }

    private void renderBlur(Framebuffer source, Framebuffer target, int radius, boolean horizontal) {
        clearAndBind(target);
        configurePassState(false);
        loadIdentityMatrices();
        blurProgram.use();
        setActiveTexture(OpenGlHelper.defaultTexUnit);
        bindTexture(source.framebufferTexture);
        blurProgram.set1i("sourceTexture", 0);
        blurProgram.set2f("texelSize", horizontal ? 1.0f / source.framebufferWidth : 0.0f,
                horizontal ? 0.0f : 1.0f / source.framebufferHeight);
        blurProgram.set1i("radius", radius);
        blurProgram.setWeights(kernel(radius));
        drawFullscreenQuad();
    }

    private void renderComposite(RenderState state) {
        OpenGlHelper.glBindFramebuffer(OpenGlHelper.GL_FRAMEBUFFER, state.framebuffer);
        GL11.glViewport(state.viewportX, state.viewportY, state.viewportWidth, state.viewportHeight);
        configurePassState(true);
        loadIdentityMatrices();
        compositeProgram.use();
        setActiveTexture(OpenGlHelper.defaultTexUnit);
        bindTexture(verticalFramebuffer.framebufferTexture);
        compositeProgram.set1i("blurTexture", 0);
        setActiveTexture(OpenGlHelper.defaultTexUnit + 1);
        bindTexture(maskFramebuffer.framebufferTexture);
        compositeProgram.set1i("maskTexture", 1);
        compositeProgram.set1f("strength", globalStrength);
        setActiveTexture(OpenGlHelper.defaultTexUnit);
        drawFullscreenQuad();
    }

    private float[] kernel(int radius) {
        Integer key = Integer.valueOf(radius);
        float[] cached = kernels.get(key);
        if (cached == null) {
            cached = GaussianKernel.create(radius);
            kernels.put(key, cached);
        }
        return cached;
    }

    private static void clearAndBind(Framebuffer framebuffer) {
        framebuffer.bindFramebuffer(true);
        GL11.glDisable(GL11.GL_SCISSOR_TEST);
        GL11.glColorMask(true, true, true, true);
        GL11.glClearColor(0.0f, 0.0f, 0.0f, 0.0f);
        GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);
    }

    private static void configureMaskState() {
        GL11.glDisable(GL11.GL_ALPHA_TEST);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glDisable(GL11.GL_CULL_FACE);
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glDisable(GL11.GL_STENCIL_TEST);
        GL11.glDisable(GL11.GL_SCISSOR_TEST);
        GL11.glDepthMask(false);
        GL11.glEnable(GL11.GL_BLEND);
        OpenGlHelper.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA,
                GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GlStateManager.disableAlpha();
        GlStateManager.disableDepth();
        GlStateManager.disableCull();
        GlStateManager.disableLighting();
        GlStateManager.enableBlend();
        GlStateManager.depthMask(false);
        GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA,
                GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GlStateManager.enableTexture2D();
        GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);
    }

    private static void configurePassState(boolean composite) {
        GL11.glDisable(GL11.GL_ALPHA_TEST);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glDisable(GL11.GL_CULL_FACE);
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glDisable(GL11.GL_STENCIL_TEST);
        GL11.glDisable(GL11.GL_SCISSOR_TEST);
        GL11.glDepthMask(false);
        if (composite) {
            GL11.glEnable(GL11.GL_BLEND);
            OpenGlHelper.glBlendFunc(GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA,
                    GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);
        } else {
            GL11.glDisable(GL11.GL_BLEND);
        }
        GlStateManager.disableAlpha();
        GlStateManager.disableDepth();
        GlStateManager.disableCull();
        GlStateManager.disableLighting();
        GlStateManager.depthMask(false);
        if (composite) {
            GlStateManager.enableBlend();
            GlStateManager.tryBlendFuncSeparate(GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA,
                    GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);
        } else {
            GlStateManager.disableBlend();
        }
        GlStateManager.enableTexture2D();
        GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);
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

    private static void drawFullscreenQuad() {
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glTexCoord2f(0.0f, 0.0f);
        GL11.glVertex2f(-1.0f, -1.0f);
        GL11.glTexCoord2f(0.0f, 1.0f);
        GL11.glVertex2f(-1.0f, 1.0f);
        GL11.glTexCoord2f(1.0f, 1.0f);
        GL11.glVertex2f(1.0f, 1.0f);
        GL11.glTexCoord2f(1.0f, 0.0f);
        GL11.glVertex2f(1.0f, -1.0f);
        GL11.glEnd();
    }

    private static void pushMatrices() {
        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glPushMatrix();
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glPushMatrix();
    }

    private static void popMatrices(int matrixMode) {
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glPopMatrix();
        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glPopMatrix();
        GL11.glMatrixMode(matrixMode);
    }

    private static void loadIdentityMatrices() {
        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glLoadIdentity();
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glLoadIdentity();
    }

    private static void setActiveTexture(int textureUnit) {
        OpenGlHelper.setActiveTexture(textureUnit);
        GlStateManager.setActiveTexture(textureUnit);
    }

    private static void bindTexture(int texture) {
        GlStateManager.bindTexture(texture);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
    }

    private static int alpha(int color) {
        return color >>> 24 & 255;
    }

    private static float red(int color) {
        return (color >>> 16 & 255) / 255.0f;
    }

    private static float green(int color) {
        return (color >>> 8 & 255) / 255.0f;
    }

    private static float blue(int color) {
        return (color & 255) / 255.0f;
    }

    private void fail(Throwable throwable) {
        if (failureReason == null) {
            String message = throwable == null ? null : throwable.getMessage();
            failureReason = message == null || message.length() == 0
                    ? String.valueOf(throwable) : message;
        }
        if (!failureLogged) {
            failureLogged = true;
            System.err.println("[Yozakura] Glow renderer unavailable: " + failureReason);
        }
    }

    private static Program createProgram(String fragmentResource) {
        int vertexShader = 0;
        int fragmentShader = 0;
        int program = 0;
        try {
            vertexShader = compileShader(GL20.GL_VERTEX_SHADER, VERTEX_SHADER,
                    fragmentResource + " vertex");
            fragmentShader = compileShader(GL20.GL_FRAGMENT_SHADER, loadResource(fragmentResource),
                    fragmentResource + " fragment");
            program = GL20.glCreateProgram();
            GL20.glAttachShader(program, vertexShader);
            GL20.glAttachShader(program, fragmentShader);
            GL20.glLinkProgram(program);
            if (GL20.glGetProgrami(program, GL20.GL_LINK_STATUS) == 0) {
                throw new IllegalStateException(fragmentResource + ": "
                        + GL20.glGetProgramInfoLog(program, 4096));
            }
            return new Program(program);
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

    private static String loadResource(String resource) {
        InputStream stream = GlowRenderer.class.getResourceAsStream(resource);
        if (stream == null) {
            throw new IllegalStateException("Missing shader resource " + resource);
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
            throw new IllegalStateException("Unable to load shader resource " + resource, exception);
        } finally {
            try {
                stream.close();
            } catch (IOException ignored) {
            }
        }
    }

    private abstract static class GlowCommand {
        final int color;
        final float strength;
        final GlowProfile profile;
        final RenderSnapshot snapshot;

        private GlowCommand(int color, float strength, GlowProfile profile, RenderSnapshot snapshot) {
            this.color = color;
            this.strength = strength;
            this.profile = profile;
            this.snapshot = snapshot;
        }
    }

    private static final class TextCommand extends GlowCommand {
        private final CFontRenderer font;
        private final String text;
        private final double x;
        private final double y;

        private TextCommand(CFontRenderer font, String text, double x, double y, int color,
                            float strength, GlowProfile profile, RenderSnapshot snapshot) {
            super(color, strength, profile, snapshot);
            this.font = font;
            this.text = text;
            this.x = x;
            this.y = y;
        }
    }

    private static final class RoundedRectCommand extends GlowCommand {
        private final float left;
        private final float top;
        private final float right;
        private final float bottom;
        private final float radius;

        private RoundedRectCommand(float left, float top, float right, float bottom, float radius,
                                   int color, float strength, GlowProfile profile, RenderSnapshot snapshot) {
            super(color, strength, profile, snapshot);
            this.left = left;
            this.top = top;
            this.right = right;
            this.bottom = bottom;
            this.radius = radius;
        }
    }

    private static final class RenderSnapshot {
        private final float[] modelView;
        private final float[] projection;
        private final int viewportX;
        private final int viewportY;
        private final int viewportWidth;
        private final int viewportHeight;
        private final boolean scissorEnabled;
        private final int scissorX;
        private final int scissorY;
        private final int scissorWidth;
        private final int scissorHeight;
        private final float guiScaleFactor;

        private RenderSnapshot(float[] modelView, float[] projection, int viewportX, int viewportY,
                               int viewportWidth, int viewportHeight, boolean scissorEnabled,
                               int scissorX, int scissorY, int scissorWidth, int scissorHeight,
                               float guiScaleFactor) {
            this.modelView = modelView;
            this.projection = projection;
            this.viewportX = viewportX;
            this.viewportY = viewportY;
            this.viewportWidth = viewportWidth;
            this.viewportHeight = viewportHeight;
            this.scissorEnabled = scissorEnabled;
            this.scissorX = scissorX;
            this.scissorY = scissorY;
            this.scissorWidth = scissorWidth;
            this.scissorHeight = scissorHeight;
            this.guiScaleFactor = guiScaleFactor;
        }

        private static RenderSnapshot capture() {
            float[] modelView = readMatrix(GL11.GL_MODELVIEW_MATRIX);
            float[] projection = readMatrix(GL11.GL_PROJECTION_MATRIX);
            int[] viewport = readIntegers(GL11.GL_VIEWPORT, 4);
            boolean scissor = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
            int[] scissorBox = scissor ? readIntegers(GL11.GL_SCISSOR_BOX, 4) : new int[]{0, 0, 0, 0};
            Minecraft minecraft = Minecraft.getMinecraft();
            float scale = minecraft == null ? 1.0f : new ScaledResolution(minecraft).getScaleFactor();
            return new RenderSnapshot(modelView, projection, viewport[0], viewport[1], viewport[2], viewport[3],
                    scissor, scissorBox[0], scissorBox[1], scissorBox[2], scissorBox[3], Math.max(1.0f, scale));
        }

        private void apply(int targetWidth, int targetHeight, int displayWidth, int displayHeight) {
            GL11.glViewport(scale(viewportX, targetWidth, displayWidth),
                    scale(viewportY, targetHeight, displayHeight),
                    Math.max(1, scale(viewportWidth, targetWidth, displayWidth)),
                    Math.max(1, scale(viewportHeight, targetHeight, displayHeight)));
            if (scissorEnabled) {
                GL11.glEnable(GL11.GL_SCISSOR_TEST);
                GL11.glScissor(scale(scissorX, targetWidth, displayWidth),
                        scale(scissorY, targetHeight, displayHeight),
                        Math.max(0, scale(scissorWidth, targetWidth, displayWidth)),
                        Math.max(0, scale(scissorHeight, targetHeight, displayHeight)));
            } else {
                GL11.glDisable(GL11.GL_SCISSOR_TEST);
            }
            GL11.glMatrixMode(GL11.GL_PROJECTION);
            loadMatrix(projection);
            GL11.glMatrixMode(GL11.GL_MODELVIEW);
            loadMatrix(modelView);
        }

        private static int scale(int value, int targetSize, int displaySize) {
            return Math.round(value * targetSize / (float) Math.max(1, displaySize));
        }
    }

    private static final class RenderState {
        private final int framebuffer;
        private final int viewportX;
        private final int viewportY;
        private final int viewportWidth;
        private final int viewportHeight;
        private final int program;
        private final int activeTexture;
        private final int texture0;
        private final int texture1;
        private final int matrixMode;

        private RenderState(int framebuffer, int viewportX, int viewportY, int viewportWidth, int viewportHeight,
                            int program, int activeTexture, int texture0, int texture1, int matrixMode) {
            this.framebuffer = framebuffer;
            this.viewportX = viewportX;
            this.viewportY = viewportY;
            this.viewportWidth = viewportWidth;
            this.viewportHeight = viewportHeight;
            this.program = program;
            this.activeTexture = activeTexture;
            this.texture0 = texture0;
            this.texture1 = texture1;
            this.matrixMode = matrixMode;
        }

        private static RenderState capture() {
            int framebuffer = GL11.glGetInteger(EXTFramebufferObject.GL_FRAMEBUFFER_BINDING_EXT);
            int[] viewport = readIntegers(GL11.GL_VIEWPORT, 4);
            int program = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
            int matrixMode = GL11.glGetInteger(GL11.GL_MATRIX_MODE);
            int activeTexture = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
            setActiveTexture(OpenGlHelper.defaultTexUnit);
            int texture0 = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
            setActiveTexture(OpenGlHelper.defaultTexUnit + 1);
            int texture1 = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
            setActiveTexture(activeTexture);
            return new RenderState(framebuffer, viewport[0], viewport[1], viewport[2], viewport[3], program,
                    activeTexture, texture0, texture1, matrixMode);
        }

        private void restore() {
            OpenGlHelper.glBindFramebuffer(OpenGlHelper.GL_FRAMEBUFFER, framebuffer);
            GL11.glViewport(viewportX, viewportY, viewportWidth, viewportHeight);
            GL20.glUseProgram(program);
            setActiveTexture(OpenGlHelper.defaultTexUnit);
            bindTexture(texture0);
            setActiveTexture(OpenGlHelper.defaultTexUnit + 1);
            bindTexture(texture1);
            setActiveTexture(activeTexture);
            GL11.glMatrixMode(matrixMode);
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

        private void set1i(String name, int value) {
            GL20.glUniform1i(uniform(name), value);
        }

        private void set1f(String name, float value) {
            GL20.glUniform1f(uniform(name), value);
        }

        private void set2f(String name, float first, float second) {
            GL20.glUniform2f(uniform(name), first, second);
        }

        private void set4f(String name, float first, float second, float third, float fourth) {
            GL20.glUniform4f(uniform(name), first, second, third, fourth);
        }

        private void setWeights(float[] weights) {
            WEIGHT_BUFFER.clear();
            for (int index = 0; index <= GaussianKernel.MAX_RADIUS; index++) {
                WEIGHT_BUFFER.put(index < weights.length ? weights[index] : 0.0f);
            }
            WEIGHT_BUFFER.flip();
            GL20.glUniform1(uniform("weights"), WEIGHT_BUFFER);
        }
    }

    private static float[] readMatrix(int target) {
        MATRIX_READ_BUFFER.clear();
        GL11.glGetFloat(target, MATRIX_READ_BUFFER);
        MATRIX_READ_BUFFER.rewind();
        float[] values = new float[16];
        MATRIX_READ_BUFFER.get(values);
        return values;
    }

    private static int[] readIntegers(int target, int count) {
        INTEGER_BUFFER.clear();
        GL11.glGetInteger(target, INTEGER_BUFFER);
        INTEGER_BUFFER.rewind();
        int[] values = new int[count];
        INTEGER_BUFFER.get(values, 0, count);
        return values;
    }

    private static void loadMatrix(float[] values) {
        MATRIX_UPLOAD_BUFFER.clear();
        MATRIX_UPLOAD_BUFFER.put(values);
        MATRIX_UPLOAD_BUFFER.flip();
        GL11.glLoadMatrix(MATRIX_UPLOAD_BUFFER);
    }
}
