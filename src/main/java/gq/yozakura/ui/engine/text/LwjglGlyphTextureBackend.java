package gq.yozakura.ui.engine.text;

import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

import java.nio.ByteBuffer;

/** GL_ALPHA8 atlas pages with region-only updates. Render-thread confined. */
public final class LwjglGlyphTextureBackend implements GlyphTextureBackend {
    @Override
    public int createPage(int width, int height) {
        int texture = GL11.glGenTextures();
        if (texture <= 0) {
            throw new IllegalStateException("OpenGL failed to allocate glyph atlas texture");
        }
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
        // Glyph alpha is already antialiased by Java2D. Nearest sampling preserves
        // that hinted coverage at the physical-pixel grid instead of blurring it again.
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_ALPHA8, width, height,
                0, GL11.GL_ALPHA, GL11.GL_UNSIGNED_BYTE, (ByteBuffer) null);
        return texture;
    }

    @Override
    public void uploadAlpha(int textureId, int x, int y, int width, int height, byte[] alpha) {
        if (textureId <= 0 || alpha == null || alpha.length != width * height) {
            throw new IllegalArgumentException("invalid glyph atlas upload");
        }
        int oldAlignment = GL11.glGetInteger(GL11.GL_UNPACK_ALIGNMENT);
        int oldRowLength = GL11.glGetInteger(GL11.GL_UNPACK_ROW_LENGTH);
        try {
            GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, 1);
            GL11.glPixelStorei(GL11.GL_UNPACK_ROW_LENGTH, 0);
            ByteBuffer pixels = BufferUtils.createByteBuffer(alpha.length);
            pixels.put(alpha).flip();
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, textureId);
            GL11.glTexSubImage2D(GL11.GL_TEXTURE_2D, 0, x, y, width, height,
                    GL11.GL_ALPHA, GL11.GL_UNSIGNED_BYTE, pixels);
        } finally {
            GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, oldAlignment);
            GL11.glPixelStorei(GL11.GL_UNPACK_ROW_LENGTH, oldRowLength);
        }
    }

    @Override
    public void deletePage(int textureId) {
        if (textureId > 0) {
            GL11.glDeleteTextures(textureId);
        }
    }
}
