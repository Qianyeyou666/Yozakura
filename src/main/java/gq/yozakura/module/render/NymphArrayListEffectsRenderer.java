package gq.yozakura.module.render;

import gq.yozakura.engine.render.GLStateManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.shader.Framebuffer;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.EXTFramebufferObject;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GLContext;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/** Source-style Kawase blur snapshot used by the connected Nymph background mask. */
final class NymphArrayListEffectsRenderer {
    private static final String ROOT = "ui/shaders/nymph/";
    private static final int ATTRIB_MASK = GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT
            | GL11.GL_CURRENT_BIT | GL11.GL_DEPTH_BUFFER_BIT | GL11.GL_SCISSOR_BIT
            | GL11.GL_STENCIL_BUFFER_BIT | GL11.GL_TEXTURE_BIT | GL11.GL_VIEWPORT_BIT;
    private static final FloatBuffer MATRIX_BUFFER = BufferUtils.createFloatBuffer(16);

    private final Minecraft mc = Minecraft.getMinecraft();
    private Program downProgram;
    private Program upProgram;
    private Program compositeProgram;
    private Framebuffer first;
    private Framebuffer second;
    private int sourceTexture;
    private int sourceWidth;
    private int sourceHeight;
    private int blurredTexture;
    private boolean unavailable;
    private boolean failureLogged;

    boolean prepareBlur(float ignoredRadius) {
        return prepareBlur(4, 2.0F);
    }

    boolean prepareBlur(int iterations, float offset) {
        if (unavailable || !supportsShaders()) {
            logUnavailableOnce("OpenGL 2.0 or the Minecraft framebuffer is unavailable");
            return false;
        }
        RenderState state = RenderState.capture();
        GL11.glPushAttrib(ATTRIB_MASK);
        try {
            ensurePrograms();
            ensureTargets();
            if (!captureSource()) {
                logUnavailableOnce("could not capture the current framebuffer");
                return false;
            }
            int count = Math.max(1, Math.min(8, iterations));
            int source = sourceTexture;
            Framebuffer target = first;
            for (int index = 0; index < count; index++) {
                runPass(source, target, downProgram, offset);
                source = target.framebufferTexture;
                target = target == first ? second : first;
            }
            for (int index = count; index > 0; index--) {
                runPass(source, target, upProgram, offset);
                source = target.framebufferTexture;
                target = target == first ? second : first;
            }
            blurredTexture = source;
            return true;
        } catch (Throwable throwable) {
            unavailable = true;
            logFailure(throwable);
            return false;
        } finally {
            GL11.glPopAttrib();
            state.restore();
            // Blur passes bind their private targets. HUD callers must continue on Minecraft's framebuffer.
            mc.getFramebuffer().bindFramebuffer(false);
            GLStateManager.syncToCurrent();
            GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        }
    }

    boolean drawBlurredSurface(float left, float top, float right, float bottom,
                               float radius, int tintColor) {
        if (blurredTexture == 0 || right <= left || bottom <= top || unavailable) {
            return false;
        }
        RenderState state = RenderState.capture();
        GL11.glPushAttrib(ATTRIB_MASK);
        try {
            GL11.glEnable(GL11.GL_TEXTURE_2D);
            GL11.glEnable(GL11.GL_BLEND);
            OpenGlHelper.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA,
                    GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);
            GL11.glDisable(GL11.GL_ALPHA_TEST);
            GL11.glDisable(GL11.GL_DEPTH_TEST);
            GL11.glDepthMask(false);
            setActiveTexture(OpenGlHelper.defaultTexUnit);
            bindTexture(blurredTexture);
            compositeProgram.use();
            compositeProgram.set1i("inTexture", 0);
            compositeProgram.set2f("framebufferSize", mc.displayWidth, mc.displayHeight);
            compositeProgram.set2f("rectSize", right - left, bottom - top);
            compositeProgram.set1f("radius", Math.max(0.0F, radius));
            compositeProgram.setColor("tint", tintColor);
            drawQuad(left, top, right, bottom);
            return true;
        } finally {
            GL11.glPopAttrib();
            state.restore();
            GLStateManager.syncToCurrent();
            GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        }
    }

    void dispose() {
        if (first != null) {
            first.deleteFramebuffer();
            first = null;
        }
        if (second != null) {
            second.deleteFramebuffer();
            second = null;
        }
        deleteProgram(downProgram);
        deleteProgram(upProgram);
        deleteProgram(compositeProgram);
        downProgram = null;
        upProgram = null;
        compositeProgram = null;
        blurredTexture = 0;
        if (sourceTexture != 0) {
            GL11.glDeleteTextures(sourceTexture);
            sourceTexture = 0;
        }
        sourceWidth = 0;
        sourceHeight = 0;
        unavailable = false;
    }

    private void ensurePrograms() throws Exception {
        if (downProgram == null) {
            downProgram = createProgram("kawase_down.frag");
            upProgram = createProgram("kawase_up.frag");
            compositeProgram = createProgram("rounded_composite.frag");
        }
    }

    private void ensureTargets() {
        first = ensureTarget(first);
        second = ensureTarget(second);
    }

    private Framebuffer ensureTarget(Framebuffer framebuffer) {
        if (framebuffer != null && framebuffer.framebufferWidth == mc.displayWidth
                && framebuffer.framebufferHeight == mc.displayHeight) {
            return framebuffer;
        }
        if (framebuffer != null) {
            framebuffer.deleteFramebuffer();
        }
        Framebuffer created = new Framebuffer(mc.displayWidth, mc.displayHeight, false);
        created.setFramebufferFilter(GL11.GL_LINEAR);
        bindTexture(created.framebufferTexture);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
        return created;
    }

    private boolean captureSource() {
        if (!ensureSourceTexture()) {
            return false;
        }
        mc.getFramebuffer().bindFramebuffer(false);
        setActiveTexture(OpenGlHelper.defaultTexUnit);
        bindTexture(sourceTexture);
        GL11.glGetError();
        GL11.glCopyTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0,
                0, 0, mc.displayWidth, mc.displayHeight);
        return GL11.glGetError() == GL11.GL_NO_ERROR;
    }

    private boolean ensureSourceTexture() {
        if (mc.displayWidth <= 0 || mc.displayHeight <= 0) {
            return false;
        }
        if (sourceTexture == 0) {
            sourceTexture = GL11.glGenTextures();
            sourceWidth = 0;
            sourceHeight = 0;
        }
        setActiveTexture(OpenGlHelper.defaultTexUnit);
        bindTexture(sourceTexture);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
        if (sourceWidth != mc.displayWidth || sourceHeight != mc.displayHeight) {
            GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA,
                    mc.displayWidth, mc.displayHeight, 0, GL11.GL_RGBA,
                    GL11.GL_UNSIGNED_BYTE, (ByteBuffer) null);
            sourceWidth = mc.displayWidth;
            sourceHeight = mc.displayHeight;
        }
        return true;
    }

    private void runPass(int sourceTexture, Framebuffer target, Program program, float offset) {
        target.framebufferClear();
        target.bindFramebuffer(true);
        GL11.glViewport(0, 0, mc.displayWidth, mc.displayHeight);
        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glLoadIdentity();
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glLoadIdentity();
        GL11.glDisable(GL11.GL_ALPHA_TEST);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glDisable(GL11.GL_BLEND);
        GL11.glDepthMask(false);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        setActiveTexture(OpenGlHelper.defaultTexUnit);
        bindTexture(sourceTexture);
        program.use();
        program.set1i("inTexture", 0);
        program.set2f("offset", offset, offset);
        program.set2f("halfpixel", 0.5F / mc.displayWidth, 0.5F / mc.displayHeight);
        drawFullscreenQuad();
    }

    private Program createProgram(String fragmentName) throws Exception {
        int vertex = compile(GL20.GL_VERTEX_SHADER, readResource("passthrough.vert"));
        int fragment = compile(GL20.GL_FRAGMENT_SHADER, readResource(fragmentName));
        int id = GL20.glCreateProgram();
        try {
            GL20.glAttachShader(id, vertex);
            GL20.glAttachShader(id, fragment);
            GL20.glLinkProgram(id);
            if (GL20.glGetProgrami(id, GL20.GL_LINK_STATUS) == 0) {
                throw new IllegalStateException(fragmentName + ": " + GL20.glGetProgramInfoLog(id, 4096));
            }
            return new Program(id);
        } finally {
            GL20.glDeleteShader(vertex);
            GL20.glDeleteShader(fragment);
        }
    }

    private int compile(int type, String source) {
        int shader = GL20.glCreateShader(type);
        GL20.glShaderSource(shader, source);
        GL20.glCompileShader(shader);
        if (GL20.glGetShaderi(shader, GL20.GL_COMPILE_STATUS) == 0) {
            String log = GL20.glGetShaderInfoLog(shader, 4096);
            GL20.glDeleteShader(shader);
            throw new IllegalStateException(log);
        }
        return shader;
    }

    private String readResource(String name) throws Exception {
        InputStream stream = mc.getResourceManager().getResource(
                new ResourceLocation("yozakura", ROOT + name)).getInputStream();
        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[2048];
            int read;
            while ((read = stream.read(buffer)) >= 0) {
                output.write(buffer, 0, read);
            }
            return new String(output.toByteArray(), StandardCharsets.UTF_8);
        } finally {
            stream.close();
        }
    }

    private static void drawFullscreenQuad() {
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glTexCoord2f(0.0F, 0.0F); GL11.glVertex2f(-1.0F, -1.0F);
        GL11.glTexCoord2f(0.0F, 1.0F); GL11.glVertex2f(-1.0F, 1.0F);
        GL11.glTexCoord2f(1.0F, 1.0F); GL11.glVertex2f(1.0F, 1.0F);
        GL11.glTexCoord2f(1.0F, 0.0F); GL11.glVertex2f(1.0F, -1.0F);
        GL11.glEnd();
    }

    private static void drawQuad(float left, float top, float right, float bottom) {
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glTexCoord2f(0.0F, 0.0F); GL11.glVertex2f(left, top);
        GL11.glTexCoord2f(0.0F, 1.0F); GL11.glVertex2f(left, bottom);
        GL11.glTexCoord2f(1.0F, 1.0F); GL11.glVertex2f(right, bottom);
        GL11.glTexCoord2f(1.0F, 0.0F); GL11.glVertex2f(right, top);
        GL11.glEnd();
    }

    private boolean supportsShaders() {
        try {
            return mc.getFramebuffer() != null && mc.displayWidth > 0 && mc.displayHeight > 0
                    && GLContext.getCapabilities() != null && GLContext.getCapabilities().OpenGL20;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private void logUnavailableOnce(String reason) {
        if (!failureLogged) {
            failureLogged = true;
            System.err.println("[Yozakura] Nymph ArrayList Kawase blur unavailable: " + reason);
        }
    }

    private void logFailure(Throwable throwable) {
        logUnavailableOnce(throwable.getMessage() == null ? throwable.toString() : throwable.getMessage());
    }

    private static void deleteProgram(Program program) {
        if (program != null) {
            GL20.glDeleteProgram(program.id);
        }
    }

    private static void setActiveTexture(int unit) {
        OpenGlHelper.setActiveTexture(unit);
        GlStateManager.setActiveTexture(unit);
    }

    private static void bindTexture(int texture) {
        GlStateManager.bindTexture(texture);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
    }

    private static float[] readMatrix(int target) {
        MATRIX_BUFFER.clear();
        GL11.glGetFloat(target, MATRIX_BUFFER);
        MATRIX_BUFFER.rewind();
        float[] values = new float[16];
        MATRIX_BUFFER.get(values);
        return values;
    }

    private static void loadMatrix(int mode, float[] values) {
        GL11.glMatrixMode(mode);
        MATRIX_BUFFER.clear();
        MATRIX_BUFFER.put(values).flip();
        GL11.glLoadMatrix(MATRIX_BUFFER);
    }

    private static final class RenderState {
        private final int framebuffer;
        private final int[] viewport;
        private final int program;
        private final int activeTexture;
        private final int texture0;
        private final int texture1;
        private final int matrixMode;
        private final float[] projection;
        private final float[] modelView;

        private RenderState(int framebuffer, int[] viewport, int program, int activeTexture,
                            int texture0, int texture1, int matrixMode,
                            float[] projection, float[] modelView) {
            this.framebuffer = framebuffer;
            this.viewport = viewport;
            this.program = program;
            this.activeTexture = activeTexture;
            this.texture0 = texture0;
            this.texture1 = texture1;
            this.matrixMode = matrixMode;
            this.projection = projection;
            this.modelView = modelView;
        }

        static RenderState capture() {
            int active = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
            setActiveTexture(OpenGlHelper.defaultTexUnit);
            int texture0 = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
            setActiveTexture(OpenGlHelper.defaultTexUnit + 1);
            int texture1 = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
            setActiveTexture(active);
            java.nio.IntBuffer buffer = BufferUtils.createIntBuffer(4);
            GL11.glGetInteger(GL11.GL_VIEWPORT, buffer);
            int[] viewport = new int[4];
            buffer.rewind();
            buffer.get(viewport);
            return new RenderState(GL11.glGetInteger(EXTFramebufferObject.GL_FRAMEBUFFER_BINDING_EXT),
                    viewport, GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM), active, texture0, texture1,
                    GL11.glGetInteger(GL11.GL_MATRIX_MODE), readMatrix(GL11.GL_PROJECTION_MATRIX),
                    readMatrix(GL11.GL_MODELVIEW_MATRIX));
        }

        void restore() {
            OpenGlHelper.glBindFramebuffer(OpenGlHelper.GL_FRAMEBUFFER, framebuffer);
            GL11.glViewport(viewport[0], viewport[1], viewport[2], viewport[3]);
            loadMatrix(GL11.GL_PROJECTION, projection);
            loadMatrix(GL11.GL_MODELVIEW, modelView);
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

        private Program(int id) { this.id = id; }
        private void use() { GL20.glUseProgram(id); }
        private int uniform(String name) {
            Integer location = uniforms.get(name);
            if (location == null) {
                location = GL20.glGetUniformLocation(id, name);
                uniforms.put(name, location);
            }
            return location.intValue();
        }
        private void set1i(String name, int value) { GL20.glUniform1i(uniform(name), value); }
        private void set1f(String name, float value) { GL20.glUniform1f(uniform(name), value); }
        private void set2f(String name, float x, float y) { GL20.glUniform2f(uniform(name), x, y); }
        private void setColor(String name, int color) {
            GL20.glUniform4f(uniform(name), ((color >> 16) & 255) / 255.0F,
                    ((color >> 8) & 255) / 255.0F, (color & 255) / 255.0F,
                    ((color >>> 24) & 255) / 255.0F);
        }
    }
}
