package gq.yozakura.engine.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.shader.Framebuffer;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GLContext;

import java.nio.FloatBuffer;
import java.util.HashMap;
import java.util.Map;

public final class Blur {
    private static final Minecraft MC = Minecraft.getMinecraft();
    private static final FloatBuffer WEIGHTS = BufferUtils.createFloatBuffer(256);
    private static Program gaussianProgram;
    private static Program roundedBlurProgram;
    private static Framebuffer horizontalFramebuffer;
    private static boolean dirty = true;
    private static boolean disabled;
    private static boolean loggedFailure;

    private Blur() {
    }

    public static void invalidate() {
        dirty = true;
    }

    public static boolean drawBlur(float left, float top, float right, float bottom, float radius,
                                   float borderWidth, int fillColor, int borderColor) {
        if (disabled || right <= left || bottom <= top || !supportsShaders()) {
            return false;
        }
        try {
            if (!prepareHorizontalBlur(18.0f)) {
                return false;
            }
            Program program = getRoundedBlurProgram();
            if (program == null) {
                return false;
            }
            ShaderState state = beginProgram(program);
            try {
                MC.getFramebuffer().bindFramebuffer(true);
                GL11.glEnable(GL11.GL_TEXTURE_2D);
                GlStateManager.bindTexture(horizontalFramebuffer.framebufferTexture);

                float width = right - left;
                float height = bottom - top;
                float clampedBorder = Math.max(0.0f, Math.min(borderWidth, Math.min(width, height) / 2.0f));
                setRoundedUniforms(program, width, height, radius);
                program.set1f("borderWidth", clampedBorder);
                program.set1f("blurRadius", 18.0f);
                program.set2f("texelSize", 1.0f / Math.max(1.0f, MC.displayWidth), 1.0f / Math.max(1.0f, MC.displayHeight));
                program.set2f("direction", 0.0f, 2.0f);
                program.set1i("textureIn", 0);
                setColor(program, "fillColor", fillColor);
                setColor(program, "borderColor", borderColor);
                setGaussianWeights(program, 18.0f);
                drawQuad(left, top, right, bottom, 1.0f);
            } finally {
                endProgram(state);
            }
            return true;
        } catch (Throwable throwable) {
            disabled = true;
            logFailure(throwable);
            return false;
        }
    }

    private static boolean prepareHorizontalBlur(float radius) {
        Program program = getGaussianProgram();
        if (program == null) {
            return false;
        }
        horizontalFramebuffer = createFramebuffer(horizontalFramebuffer);
        if (!dirty) {
            return horizontalFramebuffer != null;
        }
        if (horizontalFramebuffer == null) {
            return false;
        }
        horizontalFramebuffer.framebufferClear();
        horizontalFramebuffer.bindFramebuffer(true);
        ShaderState state = beginProgram(program);
        try {
            GL11.glEnable(GL11.GL_TEXTURE_2D);
            GlStateManager.bindTexture(MC.getFramebuffer().framebufferTexture);
            OpenGlHelper.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO);
            program.set1i("textureIn", 0);
            program.set2f("texelSize", 1.0f / Math.max(1.0f, MC.displayWidth), 1.0f / Math.max(1.0f, MC.displayHeight));
            program.set2f("direction", 2.0f, 0.0f);
            program.set1f("radius", radius);
            setGaussianWeights(program, radius);
            drawFullscreenQuad();
        } finally {
            endProgram(state);
            horizontalFramebuffer.unbindFramebuffer();
            MC.getFramebuffer().bindFramebuffer(true);
        }
        dirty = false;
        return true;
    }

    private static Framebuffer createFramebuffer(Framebuffer framebuffer) {
        if (framebuffer == null || framebuffer.framebufferWidth != MC.displayWidth
                || framebuffer.framebufferHeight != MC.displayHeight) {
            if (framebuffer != null) {
                framebuffer.deleteFramebuffer();
            }
            framebuffer = new Framebuffer(MC.displayWidth, MC.displayHeight, false);
            framebuffer.setFramebufferFilter(GL11.GL_LINEAR);
        }
        return framebuffer;
    }

    private static Program getGaussianProgram() {
        if (gaussianProgram == null) {
            gaussianProgram = createProgram(GAUSSIAN_FRAGMENT);
        }
        return gaussianProgram;
    }

    private static Program getRoundedBlurProgram() {
        if (roundedBlurProgram == null) {
            roundedBlurProgram = createProgram(ROUNDED_BLUR_FRAGMENT);
        }
        return roundedBlurProgram;
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

    private static ShaderState beginProgram(Program program) {
        int previousProgram = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT | GL11.GL_TEXTURE_BIT | GL11.GL_DEPTH_BUFFER_BIT);
        program.use();
        GlStateManager.enableAlpha();
        GlStateManager.enableBlend();
        GlStateManager.disableDepth();
        GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);
        return new ShaderState(previousProgram);
    }

    private static void endProgram(ShaderState state) {
        GL20.glUseProgram(state.previousProgram);
        GL11.glPopAttrib();
        GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);
    }

    private static void setRoundedUniforms(Program program, float width, float height, float radius) {
        float clampedRadius = Math.max(0.0f, Math.min(radius, Math.min(width, height) / 2.0f));
        program.set2f("rectSize", width, height);
        program.set1f("radius", clampedRadius);
        program.set1f("padding", 1.0f);
        program.set1f("softness", 0.75f);
    }

    private static void setGaussianWeights(Program program, float radius) {
        if (Math.abs(program.weightsRadius - radius) < 0.001f) {
            return;
        }
        float sigma = Math.max(radius / 2.0f, 0.001f);
        WEIGHTS.clear();
        for (int i = 0; i < 256; i++) {
            WEIGHTS.put(i <= radius ? calculateGaussianValue(i, sigma) : 0.0f);
        }
        WEIGHTS.flip();
        GL20.glUniform1(program.uniform("weights"), WEIGHTS);
        program.weightsRadius = radius;
    }

    private static float calculateGaussianValue(float x, float sigma) {
        double output = 1.0D / Math.sqrt(2.0D * Math.PI * sigma * sigma);
        return (float) (output * Math.exp(-(x * x) / (2.0D * sigma * sigma)));
    }

    private static void setColor(Program program, String uniform, int color) {
        program.set4f(uniform,
                ((color >> 16) & 255) / 255.0f,
                ((color >> 8) & 255) / 255.0f,
                (color & 255) / 255.0f,
                ((color >> 24) & 255) / 255.0f);
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
        System.err.println("[Yozakura] Blur renderer disabled, falling back to rects: " + throwable.getMessage());
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
        ScaledResolution sr = new ScaledResolution(MC);
        float width = (float) sr.getScaledWidth_double();
        float height = (float) sr.getScaledHeight_double();
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glTexCoord2f(0.0f, 1.0f);
        GL11.glVertex2f(0.0f, 0.0f);
        GL11.glTexCoord2f(0.0f, 0.0f);
        GL11.glVertex2f(0.0f, height);
        GL11.glTexCoord2f(1.0f, 0.0f);
        GL11.glVertex2f(width, height);
        GL11.glTexCoord2f(1.0f, 1.0f);
        GL11.glVertex2f(width, 0.0f);
        GL11.glEnd();
    }

    private static final class ShaderState {
        private final int previousProgram;

        private ShaderState(int previousProgram) {
            this.previousProgram = previousProgram;
        }
    }

    private static final class Program {
        private final int id;
        private final Map<String, Integer> uniformCache = new HashMap<String, Integer>();
        private float weightsRadius = -1.0f;

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

    private static final String VERTEX_SHADER =
            "#version 120\n" +
            "void main() {\n" +
            "    gl_TexCoord[0] = gl_MultiTexCoord0;\n" +
            "    gl_Position = gl_ModelViewProjectionMatrix * gl_Vertex;\n" +
            "}\n";

    private static final String GAUSSIAN_FRAGMENT =
            "#version 120\n" +
            "uniform sampler2D textureIn;\n" +
            "uniform vec2 texelSize, direction;\n" +
            "uniform float radius;\n" +
            "uniform float weights[256];\n" +
            "#define offset texelSize * direction\n" +
            "void main() {\n" +
            "    vec3 color = texture2D(textureIn, gl_TexCoord[0].st).rgb * weights[0];\n" +
            "    float totalWeight = weights[0];\n" +
            "    for (float f = 1.0; f <= radius; f++) {\n" +
            "        float weight = weights[int(abs(f))];\n" +
            "        color += texture2D(textureIn, gl_TexCoord[0].st + f * offset).rgb * weight;\n" +
            "        color += texture2D(textureIn, gl_TexCoord[0].st - f * offset).rgb * weight;\n" +
            "        totalWeight += weight * 2.0;\n" +
            "    }\n" +
            "    gl_FragColor = vec4(color / max(totalWeight, 0.0001), 1.0);\n" +
            "}\n";

    private static final String ROUNDED_BLUR_FRAGMENT =
            "#version 120\n" +
            "uniform sampler2D textureIn;\n" +
            "uniform vec2 texelSize, direction;\n" +
            "uniform vec2 rectSize;\n" +
            "uniform vec4 fillColor;\n" +
            "uniform vec4 borderColor;\n" +
            "uniform float radius;\n" +
            "uniform float borderWidth;\n" +
            "uniform float padding;\n" +
            "uniform float softness;\n" +
            "uniform float blurRadius;\n" +
            "uniform float weights[256];\n" +
            "#define offset texelSize * direction\n" +
            "float roundSDF(vec2 p, vec2 halfSize, float r) {\n" +
            "    vec2 q = abs(p) - halfSize + vec2(r);\n" +
            "    return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - r;\n" +
            "}\n" +
            "vec3 blurredSample(vec2 uv) {\n" +
            "    vec3 color = texture2D(textureIn, uv).rgb * weights[0];\n" +
            "    float totalWeight = weights[0];\n" +
            "    for (float f = 1.0; f <= blurRadius; f++) {\n" +
            "        float weight = weights[int(abs(f))];\n" +
            "        color += texture2D(textureIn, uv + f * offset).rgb * weight;\n" +
            "        color += texture2D(textureIn, uv - f * offset).rgb * weight;\n" +
            "        totalWeight += weight * 2.0;\n" +
            "    }\n" +
            "    return color / max(totalWeight, 0.0001);\n" +
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
            "    vec2 uv = gl_FragCoord.xy * texelSize;\n" +
            "    vec3 blur = blurredSample(uv);\n" +
            "    vec3 glass = mix(blur, fillColor.rgb, clamp(fillColor.a, 0.0, 0.88));\n" +
            "    vec3 edge = mix(glass, borderColor.rgb, clamp(borderColor.a * 3.0, 0.0, 1.0));\n" +
            "    float fillAlpha = clamp(fillColor.a * 0.86 + 0.035, 0.0, 0.88) * innerAlpha;\n" +
            "    float borderAlpha = borderColor.a * borderMask;\n" +
            "    float alpha = max(fillAlpha, borderAlpha);\n" +
            "    gl_FragColor = vec4(mix(glass, edge, borderMask), alpha);\n" +
            "}\n";
}
