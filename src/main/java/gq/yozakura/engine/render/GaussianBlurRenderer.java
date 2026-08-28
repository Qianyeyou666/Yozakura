package gq.yozakura.engine.render;

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

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.util.HashMap;
import java.util.Map;

/**
 * Small, bounded Gaussian blur for HUD cards. It captures only the region
 * behind the panel, runs a separable horizontal + vertical blur with a custom
 * GLSL program, then composites it through a rounded mask with fill/border.
 */
public final class GaussianBlurRenderer {
    private static final int ATTRIB_MASK = GL11.GL_ENABLE_BIT
            | GL11.GL_COLOR_BUFFER_BIT
            | GL11.GL_CURRENT_BIT
            | GL11.GL_DEPTH_BUFFER_BIT
            | GL11.GL_TEXTURE_BIT
            | GL11.GL_VIEWPORT_BIT
            | GL11.GL_SCISSOR_BIT
            | GL11.GL_STENCIL_BUFFER_BIT;
    private static final FloatBuffer MATRIX_BUFFER = BufferUtils.createFloatBuffer(16);

    private static Program blurProgram;
    private static Program compositeProgram;
    private static Framebuffer horizontalFramebuffer;
    private static Framebuffer verticalFramebuffer;
    private static int sourceTexture;
    private static int sourceWidth;
    private static int sourceHeight;
    private static boolean disabled;
    private static boolean failureLogged;

    private GaussianBlurRenderer() {
    }

    public static boolean drawRoundedBlur(float left, float top, float right, float bottom,
                                          float radius, float borderWidth, int fillColor,
                                          int borderColor, float blurRadius, float blurDownscale) {
        if (disabled || right <= left || bottom <= top || blurRadius <= 0.01F
                || !supportsShaders()) {
            return false;
        }
        try {
            ScaledResolution resolution = new ScaledResolution(Minecraft.getMinecraft());
            float guiScale = Math.max(1.0F, resolution.getScaleFactor());
            float logicalPadding = Math.max(8.0F, blurRadius / guiScale + 6.0F);
            float physicalPadding = logicalPadding * guiScale;

            int panelX = Math.round(left * guiScale);
            int panelY = Math.round((resolution.getScaledHeight() - bottom) * guiScale);
            int panelWidth = Math.max(1, Math.round((right - left) * guiScale));
            int panelHeight = Math.max(1, Math.round((bottom - top) * guiScale));
            int padding = Math.max(1, Math.round(physicalPadding));

            int[] viewport = readIntegers(GL11.GL_VIEWPORT, 4);
            int viewportX = viewport[0];
            int viewportY = viewport[1];
            int viewportWidth = Math.max(1, viewport[2]);
            int viewportHeight = Math.max(1, viewport[3]);
            int regionX = Math.max(viewportX, panelX - padding);
            int regionY = Math.max(viewportY, panelY - padding);
            int regionRight = Math.min(viewportX + viewportWidth, panelX + panelWidth + padding);
            int regionTop = Math.min(viewportY + viewportHeight, panelY + panelHeight + padding);
            int regionWidth = regionRight - regionX;
            int regionHeight = regionTop - regionY;
            if (regionWidth <= 0 || regionHeight <= 0) {
                return false;
            }

            if (!ensureSourceTexture(regionWidth, regionHeight)) {
                return false;
            }
            RenderState state = RenderState.capture();
            GL11.glPushAttrib(ATTRIB_MASK);
            try {
                setActiveTexture(OpenGlHelper.defaultTexUnit);
                bindTexture(sourceTexture);
                GL11.glGetError();
                GL11.glCopyTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0,
                        regionX, regionY, regionWidth, regionHeight);
                if (GL11.glGetError() != GL11.GL_NO_ERROR) {
                    return false;
                }

                float downscale = Math.max(0.25F, Math.min(1.0F, blurDownscale));
                int targetWidth = Math.max(1, Math.round(regionWidth * downscale));
                int targetHeight = Math.max(1, Math.round(regionHeight * downscale));
                if (!ensureTargets(targetWidth, targetHeight)) {
                    return false;
                }

                Program blur = getBlurProgram();
                if (blur == null) {
                    return false;
                }
                runBlurPass(sourceTexture, horizontalFramebuffer, targetWidth, targetHeight,
                        regionWidth, regionHeight, blurRadius, true);
                runBlurPass(horizontalFramebuffer.framebufferTexture, verticalFramebuffer,
                        targetWidth, targetHeight, targetWidth, targetHeight,
                        blurRadius * downscale, false);

                state.restoreRenderTargetAndMatrices();
                return drawComposite(left, top, right, bottom, radius, borderWidth,
                        fillColor, borderColor, regionX, regionY,
                        regionWidth, regionHeight, logicalPadding);
            } finally {
                GL11.glPopAttrib();
                state.restore();
                gq.yozakura.engine.render.GLStateManager.syncToCurrent();
                GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
            }
        } catch (Throwable throwable) {
            disabled = true;
            logFailure(throwable);
            return false;
        }
    }

    private static void runBlurPass(int sourceTextureId, Framebuffer target,
                                    int targetWidth, int targetHeight,
                                    int sourceWidth, int sourceHeight,
                                    float radius, boolean horizontal) {
        target.framebufferClear();
        target.bindFramebuffer(true);
        GL11.glViewport(0, 0, targetWidth, targetHeight);
        loadIdentityMatrices();
        configurePassState();

        Program program = blurProgram;
        program.use();
        setActiveTexture(OpenGlHelper.defaultTexUnit);
        bindTexture(sourceTextureId);
        program.set1i("originalTexture", 0);
        program.set2f("texelSize", 1.0F / Math.max(1.0F, sourceWidth),
                1.0F / Math.max(1.0F, sourceHeight));
        program.set2f("direction", horizontal ? 1.0F : 0.0F,
                horizontal ? 0.0F : 1.0F);
        program.set1f("radius", Math.min(64.0F, Math.max(1.0F, radius)));
        drawFullscreenQuad();
    }

    private static boolean drawComposite(float left, float top, float right, float bottom,
                                         float radius, float borderWidth, int fillColor,
                                         int borderColor, int regionX, int regionY,
                                         int regionWidth, int regionHeight, float padding) {
        float width = right - left;
        float height = bottom - top;
        float clampedBorder = Math.max(0.0F, Math.min(borderWidth, Math.min(width, height) / 2.0F));
        Program program = getCompositeProgram();
        if (program == null) {
            return false;
        }
        setActiveTexture(OpenGlHelper.defaultTexUnit);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        bindTexture(verticalFramebuffer.framebufferTexture);
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        GlStateManager.disableAlpha();
        GlStateManager.disableDepth();
        GlStateManager.disableCull();
        program.use();
        program.set1i("textureIn", 0);
        program.set2f("rectSize", width, height);
        program.set1f("radius", Math.max(0.0F, Math.min(radius, Math.min(width, height) / 2.0F)));
        program.set1f("borderWidth", clampedBorder);
        program.set1f("padding", padding);
        program.set1f("softness", 0.75F);
        program.set2f("regionOrigin", regionX, regionY);
        program.set2f("regionSize", Math.max(1, regionWidth), Math.max(1, regionHeight));
        setColor(program, "fillColor", fillColor);
        setColor(program, "borderColor", borderColor);
        drawQuad(left, top, right, bottom, padding);
        return true;
    }

    private static boolean ensureSourceTexture(int width, int height) {
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
        if (sourceWidth != width || sourceHeight != height) {
            GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGB, width, height, 0,
                    GL11.GL_RGB, GL11.GL_UNSIGNED_BYTE, (ByteBuffer) null);
            sourceWidth = width;
            sourceHeight = height;
        }
        return true;
    }

    private static boolean ensureTargets(int width, int height) {
        horizontalFramebuffer = ensureFramebuffer(horizontalFramebuffer, width, height);
        verticalFramebuffer = ensureFramebuffer(verticalFramebuffer, width, height);
        return horizontalFramebuffer != null && verticalFramebuffer != null;
    }

    private static Framebuffer ensureFramebuffer(Framebuffer framebuffer, int width, int height) {
        if (framebuffer != null && framebuffer.framebufferWidth == width
                && framebuffer.framebufferHeight == height) {
            return framebuffer;
        }
        if (framebuffer != null) {
            framebuffer.deleteFramebuffer();
        }
        Framebuffer created = new Framebuffer(width, height, false);
        created.setFramebufferColor(0.0F, 0.0F, 0.0F, 0.0F);
        created.setFramebufferFilter(GL11.GL_LINEAR);
        setActiveTexture(OpenGlHelper.defaultTexUnit);
        bindTexture(created.framebufferTexture);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
        return created;
    }

    private static Program getBlurProgram() {
        if (blurProgram == null) {
            blurProgram = createProgram(BLUR_FRAGMENT);
        }
        return blurProgram;
    }

    private static Program getCompositeProgram() {
        if (compositeProgram == null) {
            compositeProgram = createProgram(COMPOSITE_FRAGMENT);
        }
        return compositeProgram;
    }

    private static Program createProgram(String fragmentSource) {
        if (disabled || !supportsShaders()) {
            return null;
        }
        int vertexShader = 0;
        int fragmentShader = 0;
        int program = 0;
        try {
            vertexShader = compileShader(GL20.GL_VERTEX_SHADER, VERTEX_SHADER);
            fragmentShader = compileShader(GL20.GL_FRAGMENT_SHADER, fragmentSource);
            program = GL20.glCreateProgram();
            GL20.glAttachShader(program, vertexShader);
            GL20.glAttachShader(program, fragmentShader);
            GL20.glLinkProgram(program);
            if (GL20.glGetProgrami(program, GL20.GL_LINK_STATUS) == 0) {
                throw new IllegalStateException(GL20.glGetProgramInfoLog(program, 4096));
            }
            return new Program(program);
        } catch (Throwable throwable) {
            disabled = true;
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

    private static int compileShader(int type, String source) {
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

    private static void setColor(Program program, String uniform, int color) {
        program.set4f(uniform,
                ((color >> 16) & 255) / 255.0F,
                ((color >> 8) & 255) / 255.0F,
                (color & 255) / 255.0F,
                ((color >>> 24) & 255) / 255.0F);
    }

    private static void configurePassState() {
        GL11.glDisable(GL11.GL_ALPHA_TEST);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glDisable(GL11.GL_CULL_FACE);
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glDisable(GL11.GL_STENCIL_TEST);
        GL11.glDisable(GL11.GL_SCISSOR_TEST);
        GL11.glDisable(GL11.GL_BLEND);
        GL11.glDepthMask(false);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
    }

    private static void drawFullscreenQuad() {
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glTexCoord2f(0.0F, 0.0F);
        GL11.glVertex2f(-1.0F, -1.0F);
        GL11.glTexCoord2f(0.0F, 1.0F);
        GL11.glVertex2f(-1.0F, 1.0F);
        GL11.glTexCoord2f(1.0F, 1.0F);
        GL11.glVertex2f(1.0F, 1.0F);
        GL11.glTexCoord2f(1.0F, 0.0F);
        GL11.glVertex2f(1.0F, -1.0F);
        GL11.glEnd();
    }

    private static void drawQuad(float left, float top, float right, float bottom, float padding) {
        float drawLeft = left - padding;
        float drawTop = top - padding;
        float drawRight = right + padding;
        float drawBottom = bottom + padding;
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glTexCoord2f(0.0F, 0.0F);
        GL11.glVertex2f(drawLeft, drawTop);
        GL11.glTexCoord2f(0.0F, 1.0F);
        GL11.glVertex2f(drawLeft, drawBottom);
        GL11.glTexCoord2f(1.0F, 1.0F);
        GL11.glVertex2f(drawRight, drawBottom);
        GL11.glTexCoord2f(1.0F, 0.0F);
        GL11.glVertex2f(drawRight, drawTop);
        GL11.glEnd();
    }

    private static boolean supportsShaders() {
        try {
            return OpenGlHelper.isFramebufferEnabled()
                    && GLContext.getCapabilities() != null
                    && GLContext.getCapabilities().OpenGL20;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static void logFailure(Throwable throwable) {
        if (failureLogged) {
            return;
        }
        failureLogged = true;
        System.err.println("[Yozakura] GaussianBlurRenderer unavailable: "
                + (throwable.getMessage() == null ? String.valueOf(throwable) : throwable.getMessage()));
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

    private static int[] readIntegers(int target, int count) {
        java.nio.IntBuffer buffer = java.nio.IntBuffer.allocate(count);
        GL11.glGetInteger(target, buffer);
        int[] values = new int[count];
        buffer.rewind();
        buffer.get(values);
        return values;
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
        MATRIX_BUFFER.put(values);
        MATRIX_BUFFER.flip();
        GL11.glLoadMatrix(MATRIX_BUFFER);
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
        private final float[] projection;
        private final float[] modelView;

        private RenderState(int framebuffer, int viewportX, int viewportY,
                            int viewportWidth, int viewportHeight, int program,
                            int activeTexture, int texture0, int texture1,
                            int matrixMode, float[] projection, float[] modelView) {
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
            this.projection = projection;
            this.modelView = modelView;
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
            return new RenderState(framebuffer, viewport[0], viewport[1], viewport[2], viewport[3],
                    program, activeTexture, texture0, texture1, matrixMode,
                    readMatrix(GL11.GL_PROJECTION_MATRIX), readMatrix(GL11.GL_MODELVIEW_MATRIX));
        }

        private void restoreRenderTargetAndMatrices() {
            OpenGlHelper.glBindFramebuffer(OpenGlHelper.GL_FRAMEBUFFER, framebuffer);
            GL11.glViewport(viewportX, viewportY, viewportWidth, viewportHeight);
            loadMatrix(GL11.GL_PROJECTION, projection);
            loadMatrix(GL11.GL_MODELVIEW, modelView);
        }

        private void restore() {
            restoreRenderTargetAndMatrices();
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
            uniforms.put(name, location);
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

    private static final String VERTEX_SHADER =
            "#version 120\n" +
            "void main() {\n" +
            "    gl_TexCoord[0] = gl_MultiTexCoord0;\n" +
            "    gl_Position = gl_ModelViewProjectionMatrix * gl_Vertex;\n" +
            "}\n";

    private static final String BLUR_FRAGMENT =
            "#version 120\n" +
            "uniform sampler2D originalTexture;\n" +
            "uniform vec2 texelSize, direction;\n" +
            "uniform float radius;\n" +
            "#define precalculated texelSize * direction\n" +
            "float gauss(float x, float sigma) {\n" +
            "    return .4 * exp(-.5 * x * x / (sigma * sigma)) / sigma;\n" +
            "}\n" +
            "void main() {\n" +
            "    vec3 color = vec3(0.0);\n" +
            "    for(float i = -radius; i <= radius; i++) {\n" +
            "        color += texture2D(originalTexture, gl_TexCoord[0].st + i * precalculated).rgb * gauss(i, radius / 2);\n" +
            "    }\n" +
            "    gl_FragColor = vec4(color, 1.0);\n" +
            "}\n";

    private static final String COMPOSITE_FRAGMENT =
            "#version 120\n" +
            "uniform sampler2D textureIn;\n" +
            "uniform vec2 rectSize;\n" +
            "uniform vec4 fillColor;\n" +
            "uniform vec4 borderColor;\n" +
            "uniform float radius;\n" +
            "uniform float borderWidth;\n" +
            "uniform float padding;\n" +
            "uniform float softness;\n" +
            "uniform vec2 regionOrigin;\n" +
            "uniform vec2 regionSize;\n" +
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
            "    vec2 uv = (gl_FragCoord.xy - regionOrigin) / max(regionSize, vec2(0.001));\n" +
            "    vec3 blur = texture2D(textureIn, clamp(uv, vec2(0.0), vec2(1.0))).rgb;\n" +
            "    float tintAmount = clamp(fillColor.a * 0.35, 0.0, 0.55);\n" +
            "    vec3 glass = mix(blur, fillColor.rgb, tintAmount);\n" +
            "    vec3 edge = mix(glass, borderColor.rgb, clamp(borderColor.a * 3.0, 0.0, 1.0));\n" +
            "    float fillAlpha = clamp(fillColor.a * 0.45 + 0.08, 0.0, 0.78) * innerAlpha;\n" +
            "    float borderAlpha = borderColor.a * borderMask;\n" +
            "    float totalAlpha = max(fillAlpha, borderAlpha);\n" +
            "    gl_FragColor = vec4(mix(glass, edge, borderMask), totalAlpha);\n" +
            "}\n";
}
