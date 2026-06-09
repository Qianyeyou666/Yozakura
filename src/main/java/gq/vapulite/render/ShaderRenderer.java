package gq.vapulite.render;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GLContext;

import java.util.HashMap;
import java.util.Map;

public final class ShaderRenderer {
    private static final float EDGE_SOFTNESS = 0.75f;
    private static final float EDGE_PADDING = 1.0f;

    private static Program solidProgram;
    private static Program gradientProgram;
    private static Program roundedProgram;
    private static Program roundedGradientProgram;
    private static Program roundedBorderProgram;
    private static Program roundedShadowProgram;
    private static boolean disabled;
    private static boolean loggedFailure;

    private ShaderRenderer() {
    }

    public static boolean drawRect(float left, float top, float right, float bottom, int color) {
        Program program = getSolidProgram();
        if (program == null || right <= left || bottom <= top) {
            return false;
        }

        program.use();
        try {
            setColor(program, "color", color);
            drawQuad(left, top, right, bottom, 0.0f);
        } finally {
            GL20.glUseProgram(0);
        }
        return true;
    }

    public static boolean drawGradientRect(float left, float top, float right, float bottom,
                                           int topLeft, int bottomLeft, int topRight, int bottomRight) {
        Program program = getGradientProgram();
        if (program == null || right <= left || bottom <= top) {
            return false;
        }

        program.use();
        try {
            setColor(program, "color1", topLeft);
            setColor(program, "color2", bottomLeft);
            setColor(program, "color3", topRight);
            setColor(program, "color4", bottomRight);
            drawQuad(left, top, right, bottom, 0.0f);
        } finally {
            GL20.glUseProgram(0);
        }
        return true;
    }

    public static boolean drawRoundedRect(float left, float top, float right, float bottom, float radius, int color) {
        Program program = getRoundedProgram();
        if (program == null || right <= left || bottom <= top) {
            return false;
        }

        program.use();
        try {
            setRoundedUniforms(program, right - left, bottom - top, radius);
            setColor(program, "color", color);
            drawQuad(left, top, right, bottom, EDGE_PADDING);
        } finally {
            GL20.glUseProgram(0);
        }
        return true;
    }

    public static boolean drawRoundedGradientRect(float left, float top, float right, float bottom, float radius,
                                                  int topLeft, int bottomLeft, int topRight, int bottomRight) {
        Program program = getRoundedGradientProgram();
        if (program == null || right <= left || bottom <= top) {
            return false;
        }

        program.use();
        try {
            setRoundedUniforms(program, right - left, bottom - top, radius);
            setColor(program, "color1", topLeft);
            setColor(program, "color2", bottomLeft);
            setColor(program, "color3", topRight);
            setColor(program, "color4", bottomRight);
            drawQuad(left, top, right, bottom, EDGE_PADDING);
        } finally {
            GL20.glUseProgram(0);
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

        program.use();
        try {
            setRoundedUniforms(program, width, height, radius);
            program.set1f("borderWidth", clampedBorder);
            setColor(program, "fillColor", fillColor);
            setColor(program, "borderColor", borderColor);
            drawQuad(left, top, right, bottom, EDGE_PADDING);
        } finally {
            GL20.glUseProgram(0);
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

        program.use();
        try {
            setRoundedUniforms(program, width, height, radius);
            program.set1f("shadowSize", spread);
            setColor(program, "color", color);
            drawQuad(left, top, right, bottom, spread + EDGE_PADDING);
        } finally {
            GL20.glUseProgram(0);
        }
        return true;
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
}
