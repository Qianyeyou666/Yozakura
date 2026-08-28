package gq.yozakura.ui.click.yozakura;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;

/** Immutable top-left-origin ARGB image loaded from the bundled Panel cursor PNG. */
public final class PanelClickGuiCursorImage {
    private static final String RESOURCE_PATH =
            "/assets/yozakura/ui/cursor/breezex-black-left-ptr.png";
    private static final int SOURCE_SIZE = 32;
    private static final int SOURCE_HOTSPOT_X = 9;
    private static final int SOURCE_HOTSPOT_Y = 4;
    private final int width;
    private final int height;
    private final int hotspotX;
    private final int hotspotY;
    private final int[] argb;

    private PanelClickGuiCursorImage(int width, int height, int hotspotX, int hotspotY, int[] argb) {
        this.width = width;
        this.height = height;
        this.hotspotX = hotspotX;
        this.hotspotY = hotspotY;
        this.argb = argb;
    }

    public static PanelClickGuiCursorImage outlinedArrow() {
        return outlinedArrow(SOURCE_SIZE);
    }

    public static PanelClickGuiCursorImage outlinedArrow(int canvasSize) {
        if (canvasSize < SOURCE_SIZE) {
            throw new IllegalArgumentException("canvasSize must be at least " + SOURCE_SIZE);
        }
        BufferedImage source = readBundledPng();
        if (source.getWidth() != SOURCE_SIZE || source.getHeight() != SOURCE_SIZE) {
            throw new IllegalStateException("Panel cursor PNG must be 32x32");
        }
        BufferedImage image = canvasSize == SOURCE_SIZE ? source : scale(source, canvasSize);
        int[] pixels = new int[canvasSize * canvasSize];
        image.getRGB(0, 0, canvasSize, canvasSize, pixels, 0, canvasSize);

        float scale = canvasSize / (float) SOURCE_SIZE;
        int hotspotX = Math.round(SOURCE_HOTSPOT_X * scale);
        int hotspotY = Math.round(SOURCE_HOTSPOT_Y * scale);
        return new PanelClickGuiCursorImage(canvasSize, canvasSize, hotspotX, hotspotY, pixels);
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    public int hotspotX() {
        return hotspotX;
    }

    public int hotspotY() {
        return hotspotY;
    }

    /** LWJGL converts this lower-left hotspot back to the platform's top-left coordinates. */
    public int lwjglHotspotY() {
        return height - 1 - hotspotY;
    }

    public int argbAt(int x, int y) {
        if (x < 0 || y < 0 || x >= width || y >= height) {
            return 0;
        }
        return argb[y * width + x];
    }

    public int[] topLeftArgbPixels() {
        return argb.clone();
    }

    public int[] nativeArgbPixels() {
        int[] nativePixels = new int[argb.length];
        for (int y = 0; y < height; y++) {
            int nativeY = height - 1 - y;
            System.arraycopy(argb, y * width, nativePixels, nativeY * width, width);
        }
        return nativePixels;
    }

    private static BufferedImage readBundledPng() {
        InputStream stream = PanelClickGuiCursorImage.class.getResourceAsStream(RESOURCE_PATH);
        if (stream == null) {
            throw new IllegalStateException("Missing Panel cursor PNG: " + RESOURCE_PATH);
        }
        try {
            BufferedImage image = ImageIO.read(stream);
            if (image == null) {
                throw new IllegalStateException("Invalid Panel cursor PNG: " + RESOURCE_PATH);
            }
            return image;
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to read Panel cursor PNG: " + RESOURCE_PATH, exception);
        } finally {
            try {
                stream.close();
            } catch (IOException ignored) {
            }
        }
    }

    private static BufferedImage scale(BufferedImage source, int size) {
        BufferedImage scaled = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = scaled.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                    RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING,
                    RenderingHints.VALUE_RENDER_QUALITY);
            graphics.drawImage(source, 0, 0, size, size, null);
        } finally {
            graphics.dispose();
        }
        return scaled;
    }
}
