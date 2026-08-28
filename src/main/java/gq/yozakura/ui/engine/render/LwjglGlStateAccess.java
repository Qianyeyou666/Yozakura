package gq.yozakura.ui.engine.render;

import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.EXTFramebufferObject;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL20;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;

/**
 * LWJGL 2 implementation of the UI renderer's OpenGL state boundary.
 *
 * <p>Buffer reuse: capture/restore historically allocated 7~8 NIO buffers per call
 * (BufferUtils.createIntBuffer/createFloatBuffer). On the per-frame hot path these
 * short-lived buffers churn the young generation needlessly. They are now instance
 * fields, reused across capture/restore calls. Single-threaded (render thread),
 * non-reentrant: each getIntegers/getFloats reads back into the buffer and copies
 * into a fresh result array before the next call, so reuse is safe.
 *
 * <p>Lazy allocation: the NIO buffers require {@code org.lwjgl.BufferUtils}, which
 * in turn requires the LWJGL native classes on the classpath. They are therefore
 * allocated on first use in {@link #capture()}/{@link #restore(GlStateSnapshot)}
 * instead of at field initialization. This keeps construction cheap (the compile()
 * hot path never touches GL state) and lets host-less unit tests instantiate the
 * renderer for compile-cache verification without dragging in LWJGL.
 */
public final class LwjglGlStateAccess implements GlStateAccess {
    /** Reusable query buffer for integer state (16 capacity covers all current queries). */
    private IntBuffer intQuery;
    /** Reusable query buffer for float state. */
    private FloatBuffer floatQuery;
    /** Reusable matrix upload buffer for restore. */
    private FloatBuffer matrixUpload;

    /** Allocates the three reusable NIO buffers on first use. Idempotent and cheap. */
    private void ensureBuffers() {
        if (intQuery == null) {
            intQuery = BufferUtils.createIntBuffer(16);
            floatQuery = BufferUtils.createFloatBuffer(16);
            matrixUpload = BufferUtils.createFloatBuffer(16);
        }
    }

    @Override
    public GlStateSnapshot capture() {
        ensureBuffers();
        GlStateSnapshot state = new GlStateSnapshot();
        state.framebufferBinding = GL11.glGetInteger(
                EXTFramebufferObject.GL_FRAMEBUFFER_BINDING_EXT);
        state.viewport = getIntegers(GL11.GL_VIEWPORT, 4);
        state.projectionMatrix = getFloats(GL11.GL_PROJECTION_MATRIX, 16);
        state.modelviewMatrix = getFloats(GL11.GL_MODELVIEW_MATRIX, 16);
        state.matrixMode = GL11.glGetInteger(GL11.GL_MATRIX_MODE);
        state.currentProgram = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        state.activeTextureUnit = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        state.texture2dEnabled = GL11.glIsEnabled(GL11.GL_TEXTURE_2D);
        state.textureBinding2D = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        if (state.activeTextureUnit == GL13.GL_TEXTURE0) {
            state.textureUnit0Enabled = state.texture2dEnabled;
            state.textureUnit0Binding2D = state.textureBinding2D;
        } else {
            GL13.glActiveTexture(GL13.GL_TEXTURE0);
            state.textureUnit0Enabled = GL11.glIsEnabled(GL11.GL_TEXTURE_2D);
            state.textureUnit0Binding2D = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
            GL13.glActiveTexture(state.activeTextureUnit);
        }
        state.blendEnabled = GL11.glIsEnabled(GL11.GL_BLEND);
        state.blendSrc = GL11.glGetInteger(GL11.GL_BLEND_SRC);
        state.blendDst = GL11.glGetInteger(GL11.GL_BLEND_DST);
        state.blendSrcAlpha = GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA);
        state.blendDstAlpha = GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA);
        state.alphaTestEnabled = GL11.glIsEnabled(GL11.GL_ALPHA_TEST);
        state.alphaTestFunc = GL11.glGetInteger(GL11.GL_ALPHA_TEST_FUNC);
        state.alphaTestRef = GL11.glGetFloat(GL11.GL_ALPHA_TEST_REF);
        state.depthTestEnabled = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        state.depthMask = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        state.scissorTestEnabled = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
        state.scissorBox = getIntegers(GL11.GL_SCISSOR_BOX, 4);
        state.stencilTestEnabled = GL11.glIsEnabled(GL11.GL_STENCIL_TEST);
        state.stencilFunc = GL11.glGetInteger(GL11.GL_STENCIL_FUNC);
        state.stencilRef = GL11.glGetInteger(GL11.GL_STENCIL_REF);
        state.stencilValueMask = GL11.glGetInteger(GL11.GL_STENCIL_VALUE_MASK);
        state.stencilWriteMask = GL11.glGetInteger(GL11.GL_STENCIL_WRITEMASK);
        state.stencilFail = GL11.glGetInteger(GL11.GL_STENCIL_FAIL);
        state.stencilDepthFail = GL11.glGetInteger(GL11.GL_STENCIL_PASS_DEPTH_FAIL);
        state.stencilDepthPass = GL11.glGetInteger(GL11.GL_STENCIL_PASS_DEPTH_PASS);
        state.currentColor = getFloats(GL11.GL_CURRENT_COLOR, 4);
        return state;
    }

    @Override
    public void restore(GlStateSnapshot state) {
        if (state == null) {
            throw new IllegalArgumentException("snapshot must not be null");
        }
        ensureBuffers();
        EXTFramebufferObject.glBindFramebufferEXT(
                EXTFramebufferObject.GL_FRAMEBUFFER_EXT, state.framebufferBinding);
        requireLength(state.viewport, 4, "viewport");
        GL11.glViewport(state.viewport[0], state.viewport[1], state.viewport[2], state.viewport[3]);

        requireLength(state.projectionMatrix, 16, "projectionMatrix");
        requireLength(state.modelviewMatrix, 16, "modelviewMatrix");
        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glLoadMatrix(toMatrixBuffer(state.projectionMatrix));
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glLoadMatrix(toMatrixBuffer(state.modelviewMatrix));

        GL20.glUseProgram(state.currentProgram);
        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        setEnabled(GL11.GL_TEXTURE_2D, state.textureUnit0Enabled);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, state.textureUnit0Binding2D);
        GL13.glActiveTexture(state.activeTextureUnit);
        setEnabled(GL11.GL_TEXTURE_2D, state.texture2dEnabled);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, state.textureBinding2D);

        setEnabled(GL11.GL_BLEND, state.blendEnabled);
        GL14.glBlendFuncSeparate(state.blendSrc, state.blendDst,
                state.blendSrcAlpha, state.blendDstAlpha);
        setEnabled(GL11.GL_ALPHA_TEST, state.alphaTestEnabled);
        GL11.glAlphaFunc(state.alphaTestFunc, state.alphaTestRef);
        setEnabled(GL11.GL_DEPTH_TEST, state.depthTestEnabled);
        GL11.glDepthMask(state.depthMask);

        requireLength(state.scissorBox, 4, "scissorBox");
        GL11.glScissor(state.scissorBox[0], state.scissorBox[1],
                state.scissorBox[2], state.scissorBox[3]);
        setEnabled(GL11.GL_SCISSOR_TEST, state.scissorTestEnabled);

        GL11.glStencilFunc(state.stencilFunc, state.stencilRef, state.stencilValueMask);
        GL11.glStencilMask(state.stencilWriteMask);
        GL11.glStencilOp(state.stencilFail, state.stencilDepthFail, state.stencilDepthPass);
        setEnabled(GL11.GL_STENCIL_TEST, state.stencilTestEnabled);

        requireLength(state.currentColor, 4, "currentColor");
        GL11.glColor4f(state.currentColor[0], state.currentColor[1],
                state.currentColor[2], state.currentColor[3]);
        GL11.glMatrixMode(state.matrixMode);
    }

    /**
     * Reusable integer query: clears position, writes GL state into buffer, copies
     * into a fresh int[] of exact size. Each call overwrites the previous buffer
     * content (caller is expected to consume the returned array immediately).
     */
    private int[] getIntegers(int property, int count) {
        // Some LWJGL drivers write vector state in 16-byte chunks. Buffer is sized 16
        // to accommodate all current queries (max count = 16 for matrices is via
        // getFloats; here max count = 4 for viewport/scissor).
        intQuery.clear();
        GL11.glGetInteger(property, intQuery);
        int[] result = new int[count];
        for (int i = 0; i < count; i++) {
            result[i] = intQuery.get(i);
        }
        return result;
    }

    private float[] getFloats(int property, int count) {
        floatQuery.clear();
        GL11.glGetFloat(property, floatQuery);
        float[] result = new float[count];
        for (int i = 0; i < count; i++) {
            result[i] = floatQuery.get(i);
        }
        return result;
    }

    private FloatBuffer toMatrixBuffer(float[] values) {
        matrixUpload.clear();
        matrixUpload.put(values);
        matrixUpload.flip();
        return matrixUpload;
    }

    private static void setEnabled(int capability, boolean enabled) {
        if (enabled) {
            GL11.glEnable(capability);
        } else {
            GL11.glDisable(capability);
        }
    }

    private static void requireLength(int[] value, int length, String name) {
        if (value == null || value.length < length) {
            throw new IllegalArgumentException(name + " must contain at least " + length + " values");
        }
    }

    private static void requireLength(float[] value, int length, String name) {
        if (value == null || value.length < length) {
            throw new IllegalArgumentException(name + " must contain at least " + length + " values");
        }
    }
}
