package gq.yozakura.ui.click.qml;

import gq.yozakura.engine.render.GLStateManager;
import io.github.humbleui.skija.Canvas;
import io.github.humbleui.skija.ColorAlphaType;
import io.github.humbleui.skija.ColorSpace;
import io.github.humbleui.skija.ColorType;
import io.github.humbleui.skija.ImageInfo;
import io.github.humbleui.skija.Pixmap;
import io.github.humbleui.skija.Surface;
import io.github.timer_err.qml4j.render.SurfaceBackend;
import net.minecraft.client.shader.Framebuffer;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

import java.nio.ByteBuffer;

/**
 * CPU Skia surface uploaded to a private Minecraft texture on the client GL thread.
 *
 * Skija GPU objects carry native finalizers which may execute on Java's Reference
 * Handler thread. Keeping QML raster-only prevents those finalizers from calling
 * OpenGL without Minecraft's current context.
 */
public final class MinecraftSkiaSurfaceBackend implements SurfaceBackend {
    private int width;
    private int height;
    private Framebuffer framebuffer;
    private ByteBuffer pixels;
    private Pixmap pixmap;
    private Surface surface;

    @Override
    public void init(int width, int height) {
        this.width = width;
        this.height = height;
        rebuildSurface();
    }

    @Override
    public Canvas acquireCanvas() {
        Canvas canvas = surface.getCanvas();
        canvas.clear(0x00000000);
        return canvas;
    }

    @Override
    public void present() {
        int savedTexture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        int savedUnpackAlignment = GL11.glGetInteger(GL11.GL_UNPACK_ALIGNMENT);
        GL11.glPushAttrib(GL11.GL_TEXTURE_BIT | GL11.GL_COLOR_BUFFER_BIT);
        try {
            pixels.position(0);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, framebuffer.framebufferTexture);
            GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, 4);
            GL11.glTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, width, height,
                    GL12.GL_BGRA, GL11.GL_UNSIGNED_BYTE, pixels);
        } finally {
            GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, savedUnpackAlignment);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, savedTexture);
            GL11.glPopAttrib();
            pixels.position(0);
            GLStateManager.syncToCurrent();
        }
    }

    @Override
    public void resize(int width, int height) {
        if (this.width == width && this.height == height) return;
        this.width = width;
        this.height = height;
        rebuildSurface();
    }

    @Override
    public int width() {
        return width;
    }

    @Override
    public int height() {
        return height;
    }

    public int textureId() {
        return framebuffer == null ? 0 : framebuffer.framebufferTexture;
    }

    public void endFrameIfNeeded() {
        // Raster drawing does not change Minecraft's OpenGL state.
    }

    @Override
    public void dispose() {
        closeRasterObjects();
        pixels = null;
        if (framebuffer != null) {
            framebuffer.deleteFramebuffer();
            framebuffer = null;
        }
    }

    private void rebuildSurface() {
        closeRasterObjects();
        if (framebuffer != null) framebuffer.deleteFramebuffer();

        pixels = BufferUtils.createByteBuffer(width * height * 4);
        ImageInfo imageInfo = new ImageInfo(width, height, ColorType.BGRA_8888,
                ColorAlphaType.PREMUL, ColorSpace.getSRGB());
        pixmap = Pixmap.make(imageInfo, pixels, width * 4);
        surface = Surface.makeRasterDirect(pixmap);
        if (surface == null) {
            pixmap.close();
            pixmap = null;
            pixels = null;
            throw new IllegalStateException("Skija could not create the QML raster surface");
        }
        framebuffer = new Framebuffer(width, height, false);
    }

    private void closeRasterObjects() {
        if (surface != null) {
            surface.close();
            surface = null;
        }
        if (pixmap != null) {
            pixmap.close();
            pixmap = null;
        }
    }
}
