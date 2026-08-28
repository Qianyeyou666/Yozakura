package gq.yozakura.bridge.modern;

import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

import java.awt.image.BufferedImage;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;

final class ModernLegacyRenderer {
    private static final int ATTRIB_POSITION = 0;
    private static final int ATTRIB_UV = 1;
    private static final int ATTRIB_COLOR = 2;
    private static final int FLOATS_PER_VERTEX = 4;
    private static final int COLORED_FLOATS_PER_VERTEX = 6;
    private static final int VERTEX_COUNT = 6;
    private static final int MAX_BATCH_VERTICES = 512;
    private static final int STRIDE_BYTES = FLOATS_PER_VERTEX * 4;
    private static final int COLORED_STRIDE_BYTES = COLORED_FLOATS_PER_VERTEX * 4;
    private static final FloatBuffer QUAD_BUFFER = BufferUtils.createFloatBuffer(VERTEX_COUNT * FLOATS_PER_VERTEX);
    private static final FloatBuffer TRIANGLE_BUFFER = BufferUtils.createFloatBuffer(MAX_BATCH_VERTICES * FLOATS_PER_VERTEX);
    private static final FloatBuffer COLORED_TRIANGLE_BUFFER =
            BufferUtils.createFloatBuffer(MAX_BATCH_VERTICES * COLORED_FLOATS_PER_VERTEX);
    private static final float[] GRADIENT_QUAD_VERTICES = new float[6 * COLORED_FLOATS_PER_VERTEX];

    private static int quadProgram;
    private static int coloredProgram;
    private static int quadVao;
    private static int quadVbo;
    private static int uniformColor = -1;
    private static int uniformTextured = -1;
    private static int uniformSampler = -1;
    private static int currentColor = 0xFFFFFFFF;
    private static boolean programUnavailable;
    private static boolean coloredProgramUnavailable;
    private static boolean meshUnavailable;

    private ModernLegacyRenderer() {
    }

    static State begin(boolean textured) {
        try {
            if (!ensureProgram() || !ensureMesh()) {
                return null;
            }
            State state = captureState();
            GL20.glUseProgram(quadProgram);
            setupOverlayState();
            GL20.glUniform1i(uniformTextured, textured ? 1 : 0);
            GL20.glUniform1i(uniformSampler, 0);
            color(currentColor);
            return state;
        } catch (Throwable throwable) {
            ModernForgeEventBridge.log("Modern legacy renderer begin failed", throwable);
            return null;
        }
    }

    static void end(State state) {
        if (state == null) {
            return;
        }
        restoreState(state);
    }

    static void rect(float x1, float y1, float x2, float y2, int color) {
        if (x2 <= x1 || y2 <= y1 || alpha(color) <= 0) {
            return;
        }
        State state = begin(false);
        if (state == null) {
            return;
        }
        try {
            color(color);
            quad(x1, y1, x2, y2, 0.0f, 0.0f, 1.0f, 1.0f);
        } finally {
            end(state);
        }
    }

    static void rectInBatch(float x1, float y1, float x2, float y2, int color) {
        if (x2 <= x1 || y2 <= y1 || alpha(color) <= 0) {
            return;
        }
        color(color);
        quad(x1, y1, x2, y2, 0.0f, 0.0f, 1.0f, 1.0f);
    }

    static void triangleInBatch(float x1, float y1, float x2, float y2, float x3, float y3, int color) {
        if (alpha(color) <= 0) {
            return;
        }
        color(color);
        drawProgramTriangle(guiToNdcX(x1), guiToNdcY(y1),
                guiToNdcX(x2), guiToNdcY(y2),
                guiToNdcX(x3), guiToNdcY(y3));
    }

    static void trianglesInBatch(float[] xy, int vertexCount, int color) {
        if (xy == null || xy.length < vertexCount * 2 || vertexCount <= 0
                || vertexCount > MAX_BATCH_VERTICES || alpha(color) <= 0) {
            return;
        }
        color(color);
        drawProgramTriangles(xy, vertexCount);
    }

    static void coloredTriangles(float[] vertices, int vertexCount) {
        if (vertices == null || vertices.length < vertexCount * COLORED_FLOATS_PER_VERTEX
                || vertexCount <= 0 || vertexCount > MAX_BATCH_VERTICES) {
            return;
        }
        State state = null;
        try {
            if (!ensureColoredProgram() || !ensureMesh()) {
                return;
            }
            state = captureState();
            GL20.glUseProgram(coloredProgram);
            setupOverlayState();
            drawColoredProgramTriangles(vertices, vertexCount);
        } catch (Throwable throwable) {
            ModernForgeEventBridge.log("Modern colored triangle renderer failed", throwable);
        } finally {
            end(state);
        }
    }

    static void gradientQuad(float left, float top, float right, float bottom,
                             int topLeft, int bottomLeft, int topRight, int bottomRight) {
        if (right <= left || bottom <= top
                || (alpha(topLeft) <= 0 && alpha(bottomLeft) <= 0
                && alpha(topRight) <= 0 && alpha(bottomRight) <= 0)) {
            return;
        }
        int cursor = 0;
        cursor = putColoredGuiVertex(GRADIENT_QUAD_VERTICES, cursor, left, top, topLeft);
        cursor = putColoredGuiVertex(GRADIENT_QUAD_VERTICES, cursor, left, bottom, bottomLeft);
        cursor = putColoredGuiVertex(GRADIENT_QUAD_VERTICES, cursor, right, bottom, bottomRight);
        cursor = putColoredGuiVertex(GRADIENT_QUAD_VERTICES, cursor, left, top, topLeft);
        cursor = putColoredGuiVertex(GRADIENT_QUAD_VERTICES, cursor, right, bottom, bottomRight);
        putColoredGuiVertex(GRADIENT_QUAD_VERTICES, cursor, right, top, topRight);
        coloredTriangles(GRADIENT_QUAD_VERTICES, 6);
    }

    static void bindTexture(int texture) {
        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
    }

    static void configureTexture() {
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
    }

    static int createTexture(BufferedImage image) {
        if (image == null || image.getWidth() <= 0 || image.getHeight() <= 0) {
            return 0;
        }
        return ModernNativeTextureBridge.createTexture(image);
    }

    static void color(int color) {
        currentColor = normalizeColor(color);
        if (quadProgram != 0 && uniformColor >= 0) {
            GL20.glUniform4f(uniformColor,
                    ((currentColor >>> 16) & 255) / 255.0f,
                    ((currentColor >>> 8) & 255) / 255.0f,
                    (currentColor & 255) / 255.0f,
                    ((currentColor >>> 24) & 255) / 255.0f);
        }
    }

    static void texturedQuad(float x, float y, float width, float height,
                             float srcX, float srcY, float srcWidth, float srcHeight,
                             float atlasWidth, float atlasHeight) {
        if (width <= 0.0f || height <= 0.0f || atlasWidth <= 0.0f || atlasHeight <= 0.0f) {
            return;
        }
        quad(x, y, x + width, y + height,
                srcX / atlasWidth, srcY / atlasHeight,
                (srcX + srcWidth) / atlasWidth, (srcY + srcHeight) / atlasHeight);
    }

    static void drawShaderGuiQuad(float left, float top, float right, float bottom, float padding) {
        float drawLeft = left - padding;
        float drawTop = top - padding;
        float drawRight = right + padding;
        float drawBottom = bottom + padding;
        drawProgramQuad(guiToNdcX(drawLeft), guiToNdcY(drawTop),
                guiToNdcX(drawRight), guiToNdcY(drawBottom),
                0.0f, 0.0f, 1.0f, 1.0f);
    }

    static void drawShaderFullscreenQuad() {
        drawProgramQuad(-1.0f, 1.0f, 1.0f, -1.0f,
                0.0f, 0.0f, 1.0f, 1.0f);
    }

    static int guiWidth() {
        Object minecraft = ModernMinecraftAccess.minecraft();
        Object window = window(minecraft);
        int width = intValue(ModernForgeEventBridge.invoke(window, "getGuiScaledWidth"), -1);
        if (width <= 0) {
            width = intValue(ModernForgeEventBridge.invoke(window, "m_85445_"), -1);
        }
        if (width <= 0) {
            width = Math.max(1, ModernGlCompat.viewport()[2]);
        }
        return width;
    }

    static int guiHeight() {
        Object minecraft = ModernMinecraftAccess.minecraft();
        Object window = window(minecraft);
        int height = intValue(ModernForgeEventBridge.invoke(window, "getGuiScaledHeight"), -1);
        if (height <= 0) {
            height = intValue(ModernForgeEventBridge.invoke(window, "m_85446_"), -1);
        }
        if (height <= 0) {
            height = Math.max(1, ModernGlCompat.viewport()[3]);
        }
        return height;
    }

    private static void quad(float left, float top, float right, float bottom,
                             float u1, float v1, float u2, float v2) {
        drawProgramQuad(guiToNdcX(left), guiToNdcY(top), guiToNdcX(right), guiToNdcY(bottom),
                u1, v1, u2, v2);
    }

    private static void drawProgramQuad(float left, float top, float right, float bottom,
                                        float u1, float v1, float u2, float v2) {
        if (!ensureMesh()) {
            return;
        }
        QUAD_BUFFER.clear();
        putVertex(left, top, u1, v1);
        putVertex(left, bottom, u1, v2);
        putVertex(right, bottom, u2, v2);
        putVertex(left, top, u1, v1);
        putVertex(right, bottom, u2, v2);
        putVertex(right, top, u2, v1);
        QUAD_BUFFER.flip();

        int previousArrayBuffer = safeGetInteger(GL15.GL_ARRAY_BUFFER_BINDING);
        int previousVao = safeGetInteger(GL30.GL_VERTEX_ARRAY_BINDING);
        try {
            GL30.glBindVertexArray(quadVao);
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, quadVbo);
            GL15.glBufferData(GL15.GL_ARRAY_BUFFER, QUAD_BUFFER, GL15.GL_STREAM_DRAW);
            GL20.glEnableVertexAttribArray(ATTRIB_POSITION);
            GL20.glEnableVertexAttribArray(ATTRIB_UV);
            GL20.glVertexAttribPointer(ATTRIB_POSITION, 2, GL11.GL_FLOAT, false, STRIDE_BYTES, 0L);
            GL20.glVertexAttribPointer(ATTRIB_UV, 2, GL11.GL_FLOAT, false, STRIDE_BYTES, 8L);
            GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, VERTEX_COUNT);
        } finally {
            try {
                GL20.glDisableVertexAttribArray(ATTRIB_UV);
                GL20.glDisableVertexAttribArray(ATTRIB_POSITION);
            } catch (Throwable ignored) {
            }
            try {
                GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, previousArrayBuffer);
                GL30.glBindVertexArray(previousVao);
            } catch (Throwable ignored) {
            }
        }
    }

    private static void drawProgramTriangle(float x1, float y1, float x2, float y2, float x3, float y3) {
        if (!ensureMesh()) {
            return;
        }
        QUAD_BUFFER.clear();
        putVertex(x1, y1, 0.0f, 0.0f);
        putVertex(x2, y2, 0.0f, 0.0f);
        putVertex(x3, y3, 0.0f, 0.0f);
        QUAD_BUFFER.flip();

        int previousArrayBuffer = safeGetInteger(GL15.GL_ARRAY_BUFFER_BINDING);
        int previousVao = safeGetInteger(GL30.GL_VERTEX_ARRAY_BINDING);
        try {
            GL30.glBindVertexArray(quadVao);
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, quadVbo);
            GL15.glBufferData(GL15.GL_ARRAY_BUFFER, QUAD_BUFFER, GL15.GL_STREAM_DRAW);
            GL20.glEnableVertexAttribArray(ATTRIB_POSITION);
            GL20.glEnableVertexAttribArray(ATTRIB_UV);
            GL20.glVertexAttribPointer(ATTRIB_POSITION, 2, GL11.GL_FLOAT, false, STRIDE_BYTES, 0L);
            GL20.glVertexAttribPointer(ATTRIB_UV, 2, GL11.GL_FLOAT, false, STRIDE_BYTES, 8L);
            GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, 3);
        } finally {
            try {
                GL20.glDisableVertexAttribArray(ATTRIB_UV);
                GL20.glDisableVertexAttribArray(ATTRIB_POSITION);
            } catch (Throwable ignored) {
            }
            try {
                GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, previousArrayBuffer);
                GL30.glBindVertexArray(previousVao);
            } catch (Throwable ignored) {
            }
        }
    }

    private static void drawProgramTriangles(float[] xy, int vertexCount) {
        if (!ensureMesh()) {
            return;
        }
        TRIANGLE_BUFFER.clear();
        for (int i = 0; i < vertexCount; i++) {
            int offset = i * 2;
            putBatchVertex(guiToNdcX(xy[offset]), guiToNdcY(xy[offset + 1]), 0.0f, 0.0f);
        }
        TRIANGLE_BUFFER.flip();

        int previousArrayBuffer = safeGetInteger(GL15.GL_ARRAY_BUFFER_BINDING);
        int previousVao = safeGetInteger(GL30.GL_VERTEX_ARRAY_BINDING);
        try {
            GL30.glBindVertexArray(quadVao);
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, quadVbo);
            GL15.glBufferData(GL15.GL_ARRAY_BUFFER, TRIANGLE_BUFFER, GL15.GL_STREAM_DRAW);
            GL20.glEnableVertexAttribArray(ATTRIB_POSITION);
            GL20.glEnableVertexAttribArray(ATTRIB_UV);
            GL20.glVertexAttribPointer(ATTRIB_POSITION, 2, GL11.GL_FLOAT, false, STRIDE_BYTES, 0L);
            GL20.glVertexAttribPointer(ATTRIB_UV, 2, GL11.GL_FLOAT, false, STRIDE_BYTES, 8L);
            GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, vertexCount);
        } finally {
            try {
                GL20.glDisableVertexAttribArray(ATTRIB_UV);
                GL20.glDisableVertexAttribArray(ATTRIB_POSITION);
            } catch (Throwable ignored) {
            }
            try {
                GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, previousArrayBuffer);
                GL30.glBindVertexArray(previousVao);
            } catch (Throwable ignored) {
            }
        }
    }

    private static void drawColoredProgramTriangles(float[] vertices, int vertexCount) {
        if (!ensureMesh()) {
            return;
        }
        COLORED_TRIANGLE_BUFFER.clear();
        for (int i = 0; i < vertexCount; i++) {
            int offset = i * COLORED_FLOATS_PER_VERTEX;
            putColoredVertex(guiToNdcX(vertices[offset]), guiToNdcY(vertices[offset + 1]),
                    vertices[offset + 2], vertices[offset + 3],
                    vertices[offset + 4], vertices[offset + 5]);
        }
        COLORED_TRIANGLE_BUFFER.flip();

        int previousArrayBuffer = safeGetInteger(GL15.GL_ARRAY_BUFFER_BINDING);
        int previousVao = safeGetInteger(GL30.GL_VERTEX_ARRAY_BINDING);
        try {
            GL30.glBindVertexArray(quadVao);
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, quadVbo);
            GL15.glBufferData(GL15.GL_ARRAY_BUFFER, COLORED_TRIANGLE_BUFFER, GL15.GL_STREAM_DRAW);
            GL20.glEnableVertexAttribArray(ATTRIB_POSITION);
            GL20.glEnableVertexAttribArray(ATTRIB_COLOR);
            GL20.glVertexAttribPointer(ATTRIB_POSITION, 2, GL11.GL_FLOAT, false, COLORED_STRIDE_BYTES, 0L);
            GL20.glVertexAttribPointer(ATTRIB_COLOR, 4, GL11.GL_FLOAT, false, COLORED_STRIDE_BYTES, 8L);
            GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, vertexCount);
        } finally {
            try {
                GL20.glDisableVertexAttribArray(ATTRIB_COLOR);
                GL20.glDisableVertexAttribArray(ATTRIB_POSITION);
            } catch (Throwable ignored) {
            }
            try {
                GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, previousArrayBuffer);
                GL30.glBindVertexArray(previousVao);
            } catch (Throwable ignored) {
            }
        }
    }

    private static boolean ensureProgram() {
        if (quadProgram != 0) {
            return true;
        }
        if (programUnavailable) {
            return false;
        }
        int vertex = 0;
        int fragment = 0;
        int program = 0;
        try {
            vertex = compile(GL20.GL_VERTEX_SHADER, QUAD_VERTEX_SHADER, "modern legacy vertex");
            fragment = compile(GL20.GL_FRAGMENT_SHADER, QUAD_FRAGMENT_SHADER, "modern legacy fragment");
            program = GL20.glCreateProgram();
            GL20.glAttachShader(program, vertex);
            GL20.glAttachShader(program, fragment);
            GL20.glBindAttribLocation(program, ATTRIB_POSITION, "a_pos");
            GL20.glBindAttribLocation(program, ATTRIB_UV, "a_uv");
            GL20.glLinkProgram(program);
            if (GL20.glGetProgrami(program, GL20.GL_LINK_STATUS) == 0) {
                throw new IllegalStateException(GL20.glGetProgramInfoLog(program, 4096));
            }
            quadProgram = program;
            uniformColor = GL20.glGetUniformLocation(program, "u_color");
            uniformTextured = GL20.glGetUniformLocation(program, "u_textured");
            uniformSampler = GL20.glGetUniformLocation(program, "u_texture");
            return true;
        } catch (Throwable throwable) {
            programUnavailable = true;
            ModernForgeEventBridge.log("Modern legacy shader program failed", throwable);
            if (program != 0) {
                GL20.glDeleteProgram(program);
            }
            return false;
        } finally {
            if (program != 0 && vertex != 0) {
                GL20.glDetachShader(program, vertex);
            }
            if (program != 0 && fragment != 0) {
                GL20.glDetachShader(program, fragment);
            }
            if (vertex != 0) {
                GL20.glDeleteShader(vertex);
            }
            if (fragment != 0) {
                GL20.glDeleteShader(fragment);
            }
        }
    }

    private static boolean ensureColoredProgram() {
        if (coloredProgram != 0) {
            return true;
        }
        if (coloredProgramUnavailable) {
            return false;
        }
        int vertex = 0;
        int fragment = 0;
        int program = 0;
        try {
            vertex = compile(GL20.GL_VERTEX_SHADER, COLORED_VERTEX_SHADER, "modern colored vertex");
            fragment = compile(GL20.GL_FRAGMENT_SHADER, COLORED_FRAGMENT_SHADER, "modern colored fragment");
            program = GL20.glCreateProgram();
            GL20.glAttachShader(program, vertex);
            GL20.glAttachShader(program, fragment);
            GL20.glBindAttribLocation(program, ATTRIB_POSITION, "a_pos");
            GL20.glBindAttribLocation(program, ATTRIB_COLOR, "a_color");
            GL20.glLinkProgram(program);
            if (GL20.glGetProgrami(program, GL20.GL_LINK_STATUS) == 0) {
                throw new IllegalStateException(GL20.glGetProgramInfoLog(program, 4096));
            }
            coloredProgram = program;
            return true;
        } catch (Throwable throwable) {
            coloredProgramUnavailable = true;
            ModernForgeEventBridge.log("Modern colored shader program failed", throwable);
            if (program != 0) {
                GL20.glDeleteProgram(program);
            }
            return false;
        } finally {
            if (program != 0 && vertex != 0) {
                GL20.glDetachShader(program, vertex);
            }
            if (program != 0 && fragment != 0) {
                GL20.glDetachShader(program, fragment);
            }
            if (vertex != 0) {
                GL20.glDeleteShader(vertex);
            }
            if (fragment != 0) {
                GL20.glDeleteShader(fragment);
            }
        }
    }

    private static boolean ensureMesh() {
        if (quadVao != 0 && quadVbo != 0) {
            return true;
        }
        if (meshUnavailable) {
            return false;
        }
        try {
            quadVao = GL30.glGenVertexArrays();
            quadVbo = GL15.glGenBuffers();
            if (quadVao == 0 || quadVbo == 0) {
                throw new IllegalStateException("OpenGL did not allocate modern HUD mesh");
            }
            return true;
        } catch (Throwable throwable) {
            meshUnavailable = true;
            ModernForgeEventBridge.log("Modern legacy mesh creation failed", throwable);
            return false;
        }
    }

    private static int compile(int type, String source, String label) {
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

    private static State captureState() {
        State state = new State();
        state.previousProgram = safeGetInteger(GL20.GL_CURRENT_PROGRAM);
        state.activeTexture = safeGetInteger(GL13.GL_ACTIVE_TEXTURE);
        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        state.texture = safeGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        state.arrayBuffer = safeGetInteger(GL15.GL_ARRAY_BUFFER_BINDING);
        state.vertexArray = safeGetInteger(GL30.GL_VERTEX_ARRAY_BINDING);
        state.blend = safeIsEnabled(GL11.GL_BLEND);
        state.depth = safeIsEnabled(GL11.GL_DEPTH_TEST);
        state.cull = safeIsEnabled(GL11.GL_CULL_FACE);
        state.depthMask = safeGetBoolean(GL11.GL_DEPTH_WRITEMASK, true);
        state.blendSrc = safeGetInteger(GL11.GL_BLEND_SRC);
        state.blendDst = safeGetInteger(GL11.GL_BLEND_DST);
        return state;
    }

    private static void setupOverlayState() {
        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glDepthMask(false);
        GL11.glDisable(GL11.GL_CULL_FACE);
    }

    private static void restoreState(State state) {
        try {
            GL20.glUseProgram(state.previousProgram);
        } catch (Throwable ignored) {
            GL20.glUseProgram(0);
        }
        setEnabled(GL11.GL_BLEND, state.blend);
        setEnabled(GL11.GL_DEPTH_TEST, state.depth);
        setEnabled(GL11.GL_CULL_FACE, state.cull);
        GL11.glDepthMask(state.depthMask);
        if (state.blendSrc != 0 || state.blendDst != 0) {
            GL11.glBlendFunc(state.blendSrc, state.blendDst);
        }
        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, state.texture);
        try {
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, state.arrayBuffer);
            GL30.glBindVertexArray(state.vertexArray);
        } catch (Throwable ignored) {
        }
        GL13.glActiveTexture(state.activeTexture);
    }

    private static float guiToNdcX(float x) {
        return x / Math.max(1.0f, guiWidth()) * 2.0f - 1.0f;
    }

    private static float guiToNdcY(float y) {
        return 1.0f - y / Math.max(1.0f, guiHeight()) * 2.0f;
    }

    private static void putVertex(float x, float y, float u, float v) {
        QUAD_BUFFER.put(x).put(y).put(u).put(v);
    }

    private static void putBatchVertex(float x, float y, float u, float v) {
        TRIANGLE_BUFFER.put(x).put(y).put(u).put(v);
    }

    private static void putColoredVertex(float x, float y, float red, float green, float blue, float alpha) {
        COLORED_TRIANGLE_BUFFER.put(x).put(y).put(red).put(green).put(blue).put(alpha);
    }

    private static int putColoredGuiVertex(float[] target, int cursor, float x, float y, int color) {
        target[cursor++] = x;
        target[cursor++] = y;
        target[cursor++] = ((color >>> 16) & 255) / 255.0f;
        target[cursor++] = ((color >>> 8) & 255) / 255.0f;
        target[cursor++] = (color & 255) / 255.0f;
        target[cursor++] = ((color >>> 24) & 255) / 255.0f;
        return cursor;
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

    private static int intValue(Object value, int fallback) {
        return value instanceof Number ? ((Number) value).intValue() : fallback;
    }

    private static int normalizeColor(int color) {
        return (color & 0xFC000000) == 0 ? color | 0xFF000000 : color;
    }

    private static int alpha(int color) {
        return (color >>> 24) & 255;
    }

    static final class State {
        private int previousProgram;
        private int activeTexture;
        private int texture;
        private int arrayBuffer;
        private int vertexArray;
        private int blendSrc;
        private int blendDst;
        private boolean blend;
        private boolean depth;
        private boolean cull;
        private boolean depthMask;
    }

    private static final String QUAD_VERTEX_SHADER =
            "#version 150\n" +
            "in vec2 a_pos;\n" +
            "in vec2 a_uv;\n" +
            "out vec2 v_uv;\n" +
            "void main() {\n" +
            "    v_uv = a_uv;\n" +
            "    gl_Position = vec4(a_pos, 0.0, 1.0);\n" +
            "}\n";

    private static final String QUAD_FRAGMENT_SHADER =
            "#version 150\n" +
            "uniform sampler2D u_texture;\n" +
            "uniform int u_textured;\n" +
            "uniform vec4 u_color;\n" +
            "in vec2 v_uv;\n" +
            "out vec4 fragColor;\n" +
            "void main() {\n" +
            "    vec4 sampleColor = u_textured != 0 ? texture(u_texture, v_uv) : vec4(1.0);\n" +
            "    fragColor = sampleColor * u_color;\n" +
            "}\n";

    private static final String COLORED_VERTEX_SHADER =
            "#version 150\n" +
            "in vec2 a_pos;\n" +
            "in vec4 a_color;\n" +
            "out vec4 v_color;\n" +
            "void main() {\n" +
            "    v_color = a_color;\n" +
            "    gl_Position = vec4(a_pos, 0.0, 1.0);\n" +
            "}\n";

    private static final String COLORED_FRAGMENT_SHADER =
            "#version 150\n" +
            "in vec4 v_color;\n" +
            "out vec4 fragColor;\n" +
            "void main() {\n" +
            "    fragColor = v_color;\n" +
            "}\n";
}
