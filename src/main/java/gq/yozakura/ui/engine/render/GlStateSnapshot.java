package gq.yozakura.ui.engine.render;

import java.util.Arrays;

/**
 * GL 状态快照：捕获某一时刻的全部相关 GL 状态，用于 {@link GlStateGuard} 恢复。
 *
 * <p>字段为可变 public：由 {@link GlStateAccess#capture()} 实现逐字段填充。
 * 这样可以避免每加一项状态都要修改构造函数签名。
 *
 * <p>{@link #copy()} 防御性复制所有数组字段；普通字段按值复制。
 * {@link #equals(Object)} / {@link #hashCode()} 比较全部字段（数组按内容）。
 *
 * <p>覆盖的状态（按 AGENTS.md 要求）：
 * <ul>
 *   <li>framebuffer binding（GL_FRAMEBUFFER_BINDING）</li>
 *   <li>viewport（GL_VIEWPORT，4 int）</li>
 *   <li>projection / modelview 矩阵（16 float each，OpenGL column-major）</li>
 *   <li>matrix mode（GL_MATRIX_MODE）</li>
 *   <li>current shader program（GL_CURRENT_PROGRAM，0 表示固定功能）</li>
 *   <li>active texture unit（GL_ACTIVE_TEXTURE）</li>
 *   <li>texture 2D enabled + binding（GL_TEXTURE_BINDING_2D）</li>
 *   <li>blend enabled + src/dst factor</li>
 *   <li>alpha test enabled</li>
 *   <li>depth test enabled + depth mask</li>
 *   <li>scissor test enabled + scissor box（4 int）</li>
 *   <li>stencil test enabled</li>
 *   <li>current color（GL_CURRENT_COLOR，4 float）</li>
 * </ul>
 */
public final class GlStateSnapshot {
    public int framebufferBinding;
    public int[] viewport;             // 4 int: x, y, w, h
    public float[] projectionMatrix;   // 16 float, column-major
    public float[] modelviewMatrix;    // 16 float, column-major
    public int matrixMode;
    public int currentProgram;
    public int activeTextureUnit;
    public boolean texture2dEnabled;
    public int textureBinding2D;
    public boolean textureUnit0Enabled;
    public int textureUnit0Binding2D;
    public boolean blendEnabled;
    public int blendSrc;
    public int blendDst;
    public int blendSrcAlpha;
    public int blendDstAlpha;
    public boolean alphaTestEnabled;
    public int alphaTestFunc;
    public float alphaTestRef;
    public boolean depthTestEnabled;
    public boolean depthMask;
    public boolean scissorTestEnabled;
    public int[] scissorBox;           // 4 int: x, y, w, h
    public boolean stencilTestEnabled;
    public int stencilFunc;
    public int stencilRef;
    public int stencilValueMask;
    public int stencilWriteMask;
    public int stencilFail;
    public int stencilDepthFail;
    public int stencilDepthPass;
    public float[] currentColor;       // 4 float: r, g, b, a

    public GlStateSnapshot copy() {
        GlStateSnapshot c = new GlStateSnapshot();
        c.framebufferBinding = framebufferBinding;
        c.viewport = copyArray(viewport);
        c.projectionMatrix = copyArray(projectionMatrix);
        c.modelviewMatrix = copyArray(modelviewMatrix);
        c.matrixMode = matrixMode;
        c.currentProgram = currentProgram;
        c.activeTextureUnit = activeTextureUnit;
        c.texture2dEnabled = texture2dEnabled;
        c.textureBinding2D = textureBinding2D;
        c.textureUnit0Enabled = textureUnit0Enabled;
        c.textureUnit0Binding2D = textureUnit0Binding2D;
        c.blendEnabled = blendEnabled;
        c.blendSrc = blendSrc;
        c.blendDst = blendDst;
        c.blendSrcAlpha = blendSrcAlpha;
        c.blendDstAlpha = blendDstAlpha;
        c.alphaTestEnabled = alphaTestEnabled;
        c.alphaTestFunc = alphaTestFunc;
        c.alphaTestRef = alphaTestRef;
        c.depthTestEnabled = depthTestEnabled;
        c.depthMask = depthMask;
        c.scissorTestEnabled = scissorTestEnabled;
        c.scissorBox = copyArray(scissorBox);
        c.stencilTestEnabled = stencilTestEnabled;
        c.stencilFunc = stencilFunc;
        c.stencilRef = stencilRef;
        c.stencilValueMask = stencilValueMask;
        c.stencilWriteMask = stencilWriteMask;
        c.stencilFail = stencilFail;
        c.stencilDepthFail = stencilDepthFail;
        c.stencilDepthPass = stencilDepthPass;
        c.currentColor = copyArray(currentColor);
        return c;
    }

    private static int[] copyArray(int[] src) {
        return src == null ? null : Arrays.copyOf(src, src.length);
    }

    private static float[] copyArray(float[] src) {
        return src == null ? null : Arrays.copyOf(src, src.length);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof GlStateSnapshot)) return false;
        GlStateSnapshot s = (GlStateSnapshot) o;
        return framebufferBinding == s.framebufferBinding
                && matrixMode == s.matrixMode
                && currentProgram == s.currentProgram
                && activeTextureUnit == s.activeTextureUnit
                && texture2dEnabled == s.texture2dEnabled
                && textureBinding2D == s.textureBinding2D
                && textureUnit0Enabled == s.textureUnit0Enabled
                && textureUnit0Binding2D == s.textureUnit0Binding2D
                && blendEnabled == s.blendEnabled
                && blendSrc == s.blendSrc
                && blendDst == s.blendDst
                && blendSrcAlpha == s.blendSrcAlpha
                && blendDstAlpha == s.blendDstAlpha
                && alphaTestEnabled == s.alphaTestEnabled
                && alphaTestFunc == s.alphaTestFunc
                && Float.floatToIntBits(alphaTestRef) == Float.floatToIntBits(s.alphaTestRef)
                && depthTestEnabled == s.depthTestEnabled
                && depthMask == s.depthMask
                && scissorTestEnabled == s.scissorTestEnabled
                && stencilTestEnabled == s.stencilTestEnabled
                && stencilFunc == s.stencilFunc
                && stencilRef == s.stencilRef
                && stencilValueMask == s.stencilValueMask
                && stencilWriteMask == s.stencilWriteMask
                && stencilFail == s.stencilFail
                && stencilDepthFail == s.stencilDepthFail
                && stencilDepthPass == s.stencilDepthPass
                && Arrays.equals(viewport, s.viewport)
                && Arrays.equals(projectionMatrix, s.projectionMatrix)
                && Arrays.equals(modelviewMatrix, s.modelviewMatrix)
                && Arrays.equals(scissorBox, s.scissorBox)
                && Arrays.equals(currentColor, s.currentColor);
    }

    @Override
    public int hashCode() {
        int result = framebufferBinding;
        result = 31 * result + matrixMode;
        result = 31 * result + currentProgram;
        result = 31 * result + activeTextureUnit;
        result = 31 * result + (texture2dEnabled ? 1 : 0);
        result = 31 * result + textureBinding2D;
        result = 31 * result + (textureUnit0Enabled ? 1 : 0);
        result = 31 * result + textureUnit0Binding2D;
        result = 31 * result + (blendEnabled ? 1 : 0);
        result = 31 * result + blendSrc;
        result = 31 * result + blendDst;
        result = 31 * result + blendSrcAlpha;
        result = 31 * result + blendDstAlpha;
        result = 31 * result + (alphaTestEnabled ? 1 : 0);
        result = 31 * result + alphaTestFunc;
        result = 31 * result + Float.floatToIntBits(alphaTestRef);
        result = 31 * result + (depthTestEnabled ? 1 : 0);
        result = 31 * result + (depthMask ? 1 : 0);
        result = 31 * result + (scissorTestEnabled ? 1 : 0);
        result = 31 * result + (stencilTestEnabled ? 1 : 0);
        result = 31 * result + stencilFunc;
        result = 31 * result + stencilRef;
        result = 31 * result + stencilValueMask;
        result = 31 * result + stencilWriteMask;
        result = 31 * result + stencilFail;
        result = 31 * result + stencilDepthFail;
        result = 31 * result + stencilDepthPass;
        result = 31 * result + Arrays.hashCode(viewport);
        result = 31 * result + Arrays.hashCode(projectionMatrix);
        result = 31 * result + Arrays.hashCode(modelviewMatrix);
        result = 31 * result + Arrays.hashCode(scissorBox);
        result = 31 * result + Arrays.hashCode(currentColor);
        return result;
    }
}
