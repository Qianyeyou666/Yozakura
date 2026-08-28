package gq.yozakura.bridge.modern;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.util.LinkedHashMap;
import java.util.Map;

final class ModernGlassRenderer {
    private static final int CACHE_LIMIT = 96;
    private static final Map<String, GlassTexture> CACHE = new LinkedHashMap<String, GlassTexture>(32, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, GlassTexture> eldest) {
            return size() > CACHE_LIMIT;
        }
    };

    private ModernGlassRenderer() {
    }

    static boolean draw(Object graphics, Object minecraft, int x, int y, int width, int height, int radius,
                        int fillColor, int borderColor, int glowColor, float noise, float highlight) {
        if (width <= 0 || height <= 0) {
            return false;
        }
        int w = Math.max(1, width);
        int h = Math.max(1, height);
        int r = Math.max(0, Math.min(radius, Math.min(w, h) / 2));
        String key = w + "x" + h + ":" + r + ":" + fillColor + ":" + borderColor + ":"
                + glowColor + ":" + Math.round(noise * 1000.0f) + ":" + Math.round(highlight * 1000.0f);
        GlassTexture texture = CACHE.get(key);
        if (texture == null) {
            texture = new GlassTexture(w, h, createImage(w, h, r, fillColor, borderColor, glowColor, noise, highlight));
            CACHE.put(key, texture);
        }
        return texture.draw(graphics, minecraft, x, y);
    }

    private static BufferedImage createImage(int width, int height, int radius, int fillColor, int borderColor,
                                             int glowColor, float noise, float highlight) {
        int pad = Math.max(6, radius);
        int imageW = width + pad * 2;
        int imageH = height + pad * 2;
        BufferedImage image = new BufferedImage(imageW, imageH, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        setup(graphics);

        RoundRectangle2D.Float outer = new RoundRectangle2D.Float(pad, pad, width, height,
                radius * 2.0f, radius * 2.0f);
        drawGlow(graphics, outer, glowColor, pad);

        graphics.setPaint(new GradientPaint(0.0f, pad, awt(fillColor, 1.08f),
                0.0f, pad + height, awt(fillColor, 0.82f)));
        graphics.fill(outer);

        graphics.setClip(outer);
        drawHighlight(graphics, pad, pad, width, height, radius, highlight);
        drawNoise(graphics, pad, pad, width, height, noise);
        graphics.setClip(null);

        if (alpha(borderColor) > 0) {
            graphics.setStroke(new BasicStroke(1.0f));
            graphics.setColor(awt(borderColor, 1.0f));
            graphics.draw(outer);
        }
        graphics.dispose();
        return image;
    }

    private static void drawGlow(Graphics2D graphics, RoundRectangle2D.Float shape, int color, int pad) {
        int alpha = alpha(color);
        if (alpha <= 0) {
            return;
        }
        for (int i = pad; i >= 1; i--) {
            float amount = (pad - i + 1) / (float) (pad * pad);
            graphics.setStroke(new BasicStroke(i * 2.0f));
            graphics.setColor(awt((color & 0x00FFFFFF) | (Math.max(1, Math.round(alpha * amount)) << 24), 1.0f));
            graphics.draw(shape);
        }
    }

    private static void drawHighlight(Graphics2D graphics, int x, int y, int width, int height, int radius,
                                      float strength) {
        int alpha = Math.max(0, Math.min(72, Math.round(42.0f * strength)));
        if (alpha <= 0) {
            return;
        }
        RoundRectangle2D.Float top = new RoundRectangle2D.Float(x + 2, y + 1,
                Math.max(1, width - 4), Math.max(1, height / 2),
                Math.max(1, radius * 1.6f), Math.max(1, radius * 1.6f));
        graphics.setPaint(new GradientPaint(0.0f, y, new Color(255, 255, 255, alpha),
                0.0f, y + Math.max(1, height / 2), new Color(255, 255, 255, 0)));
        graphics.fill(top);

        graphics.setColor(new Color(255, 246, 250, Math.max(8, alpha / 2)));
        graphics.fillRoundRect(x + 4, y + 2, Math.max(1, width - 8), 1, radius, radius);
    }

    private static void drawNoise(Graphics2D graphics, int x, int y, int width, int height, float amount) {
        int alpha = Math.max(0, Math.min(18, Math.round(amount * 255.0f)));
        if (alpha <= 0) {
            return;
        }
        for (int py = 0; py < height; py += 3) {
            for (int px = (py % 2); px < width; px += 4) {
                int seed = (px * 31 + py * 17 + width * 7 + height * 13) & 15;
                if (seed < 5) {
                    graphics.setColor(new Color(255, 255, 255, Math.max(1, alpha - seed)));
                } else if (seed > 12) {
                    graphics.setColor(new Color(0, 0, 0, Math.max(1, alpha / 2)));
                } else {
                    continue;
                }
                graphics.fillRect(x + px, y + py, 1, 1);
            }
        }
    }

    private static void setup(Graphics2D graphics) {
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        graphics.setRenderingHint(RenderingHints.KEY_ALPHA_INTERPOLATION, RenderingHints.VALUE_ALPHA_INTERPOLATION_QUALITY);
        graphics.setRenderingHint(RenderingHints.KEY_COLOR_RENDERING, RenderingHints.VALUE_COLOR_RENDER_QUALITY);
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
    }

    private static Color awt(int color, float brightness) {
        int a = alpha(color);
        int r = Math.max(0, Math.min(255, Math.round(((color >>> 16) & 255) * brightness)));
        int g = Math.max(0, Math.min(255, Math.round(((color >>> 8) & 255) * brightness)));
        int b = Math.max(0, Math.min(255, Math.round((color & 255) * brightness)));
        return new Color(r, g, b, a);
    }

    private static int alpha(int color) {
        return (color >>> 24) & 255;
    }

    private static final class GlassTexture {
        private final int width;
        private final int height;
        private final BufferedImage image;
        private int textureId;
        private boolean failed;

        private GlassTexture(int width, int height, BufferedImage image) {
            this.width = width;
            this.height = height;
            this.image = image;
        }

        private boolean draw(Object graphics, Object minecraft, int x, int y) {
            if (!ensure(minecraft)) {
                return false;
            }
            int padX = Math.max(0, (image.getWidth() - width) / 2);
            int padY = Math.max(0, (image.getHeight() - height) / 2);
            ModernLegacyRenderer.State state = ModernLegacyRenderer.begin(true);
            if (state == null) {
                return false;
            }
            try {
                ModernLegacyRenderer.bindTexture(textureId);
                ModernLegacyRenderer.color(0xFFFFFFFF);
                ModernLegacyRenderer.texturedQuad(x, y, width, height,
                        padX, padY, width, height, image.getWidth(), image.getHeight());
            } finally {
                ModernLegacyRenderer.end(state);
            }
            return true;
        }

        private boolean ensure(Object minecraft) {
            if (textureId != 0) {
                return true;
            }
            if (failed || image == null) {
                return false;
            }
            try {
                textureId = ModernLegacyRenderer.createTexture(image);
                if (textureId == 0) {
                    failed = true;
                    return false;
                }
                return true;
            } catch (Throwable throwable) {
                failed = true;
                ModernForgeEventBridge.log("Modern glass texture registration failed", throwable);
                return false;
            }
        }
    }
}
