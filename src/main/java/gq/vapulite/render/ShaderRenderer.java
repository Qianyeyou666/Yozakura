package gq.vapulite.render;

import net.minecraft.client.renderer.GlStateManager;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GLContext;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.HashMap;
import java.util.Map;

public final class ShaderRenderer {
    private static final float EDGE_SOFTNESS = 0.75f;
    private static final float EDGE_PADDING = 1.0f;
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
    private static Program roundedBorderProgram;
    private static Program roundedShadowProgram;
    private static Program circleProgram;
    private static Program arcProgram;
    private static Program lineProgram;
    private static Program circleBadgeProgram;
    private static Program frostedGlassProgram;
    private static final IntBuffer VIEWPORT_BUFFER = BufferUtils.createIntBuffer(16);
    private static int screenTexture;
    private static int capturedWidth;
    private static int capturedHeight;
    private static boolean frostedGlassDirty = true;
    private static boolean disabled;
    private static boolean loggedFailure;

    private ShaderRenderer() {
    }

    public static void invalidateFrostedGlass() {
        frostedGlassDirty = true;
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
        if (program == null || right <= left || bottom <= top || !ensureFrostedGlassTexture()) {
            return false;
        }

        float width = right - left;
        float height = bottom - top;
        float clampedBorder = Math.max(0.0f, Math.min(borderWidth, Math.min(width, height) / 2.0f));
        ShaderState shaderState = beginProgram(program);
        try {
            GL13.glActiveTexture(GL13.GL_TEXTURE0);
            GL11.glEnable(GL11.GL_TEXTURE_2D);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, screenTexture);
            setRoundedUniforms(program, width, height, radius);
            program.set1f("borderWidth", clampedBorder);
            program.set1f("grainStrength", 0.028f);
            program.set1f("blurRadius", 4.6f);
            program.set2f("screenSize", capturedWidth, capturedHeight);
            program.set1i("screenTex", 0);
            setColor(program, "fillColor", fillColor);
            setColor(program, "borderColor", borderColor);
            drawQuad(left, top, right, bottom, EDGE_PADDING);
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

    private static boolean ensureFrostedGlassTexture() {
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
            if (screenTexture == 0) {
                screenTexture = GL11.glGenTextures();
                frostedGlassDirty = true;
            }
            GL13.glActiveTexture(GL13.GL_TEXTURE0);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, screenTexture);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
            if (capturedWidth != viewportW || capturedHeight != viewportH) {
                GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGB, viewportW, viewportH, 0,
                        GL11.GL_RGB, GL11.GL_UNSIGNED_BYTE, (ByteBuffer) null);
                capturedWidth = viewportW;
                capturedHeight = viewportH;
                frostedGlassDirty = true;
            }
            if (frostedGlassDirty) {
                GL11.glCopyTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, viewportX, viewportY, viewportW, viewportH);
                frostedGlassDirty = false;
            }
            return true;
        } finally {
            restoreTexture0State(textureState);
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
        System.err.println("[VapuLite] Shader renderer disabled, falling back to GL11: " + throwable.getMessage());
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

    private static final String FROSTED_GLASS_FRAGMENT =
            "#version 120\n" +
            "uniform vec2 rectSize;\n" +
            "uniform vec4 fillColor;\n" +
            "uniform vec4 borderColor;\n" +
            "uniform sampler2D screenTex;\n" +
            "uniform vec2 screenSize;\n" +
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
            "vec4 blurSample(vec2 uv) {\n" +
            "    vec2 texel = 1.0 / max(screenSize, vec2(1.0));\n" +
            "    vec2 radiusVec = texel * blurRadius;\n" +
            "    vec4 c = texture2D(screenTex, uv) * 0.160;\n" +
            "    c += texture2D(screenTex, uv + radiusVec * vec2(-1.0, 0.0)) * 0.120;\n" +
            "    c += texture2D(screenTex, uv + radiusVec * vec2(1.0, 0.0)) * 0.120;\n" +
            "    c += texture2D(screenTex, uv + radiusVec * vec2(0.0, -1.0)) * 0.120;\n" +
            "    c += texture2D(screenTex, uv + radiusVec * vec2(0.0, 1.0)) * 0.120;\n" +
            "    c += texture2D(screenTex, uv + radiusVec * vec2(-0.72, -0.72)) * 0.090;\n" +
            "    c += texture2D(screenTex, uv + radiusVec * vec2(0.72, -0.72)) * 0.090;\n" +
            "    c += texture2D(screenTex, uv + radiusVec * vec2(-0.72, 0.72)) * 0.090;\n" +
            "    c += texture2D(screenTex, uv + radiusVec * vec2(0.72, 0.72)) * 0.090;\n" +
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
            "    float grain = noise(gl_FragCoord.xy * 0.85) - 0.5;\n" +
            "    float topGlow = (1.0 - st.y) * 0.035;\n" +
            "    float sideGlow = (1.0 - st.x) * 0.012;\n" +
            "    vec2 screenUv = clamp(gl_FragCoord.xy / max(screenSize, vec2(1.0)), vec2(0.001), vec2(0.999));\n" +
            "    vec2 refractUv = screenUv + vec2(noise(gl_FragCoord.yx * 0.13) - 0.5, grain) / max(screenSize, vec2(1.0)) * 2.4;\n" +
            "    vec3 blurred = blurSample(refractUv).rgb;\n" +
            "    vec3 tint = fillColor.rgb + vec3(topGlow + sideGlow + grain * grainStrength);\n" +
            "    vec3 glass = mix(blurred, tint, clamp(fillColor.a * 0.65 + 0.24, 0.0, 0.78));\n" +
            "    vec3 edge = mix(glass, borderColor.rgb + vec3(0.06), clamp(borderColor.a * 3.2, 0.0, 1.0));\n" +
            "    float fillAlpha = clamp(fillColor.a * 0.78 + 0.045, 0.0, 0.80) * innerAlpha;\n" +
            "    float borderAlpha = borderColor.a * borderMask;\n" +
            "    float totalAlpha = max(fillAlpha, borderAlpha);\n" +
            "    vec3 rgb = mix(glass, edge, borderMask);\n" +
            "    gl_FragColor = vec4(clamp(rgb, 0.0, 1.0), totalAlpha);\n" +
            "}\n";
}
