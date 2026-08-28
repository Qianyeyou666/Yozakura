package gq.yozakura.ui.engine.render;

/** Converts top-left logical UI clips to bottom-left OpenGL framebuffer scissor boxes. */
public final class ScissorTransform {
    private ScissorTransform() { }

    public static int[] toFramebuffer(ClipRect clip, float scale, float originX,
                                      float originY, int framebufferHeight) {
        if (clip == null) {
            throw new IllegalArgumentException("clip must not be null");
        }
        if (scale <= 0.0F || framebufferHeight < 0) {
            throw new IllegalArgumentException("scale must be positive and framebufferHeight non-negative");
        }
        int x = Math.round((originX + clip.x()) * scale);
        int width = Math.round(clip.width() * scale);
        int height = Math.round(clip.height() * scale);
        int y = framebufferHeight
                - Math.round((originY + clip.y() + clip.height()) * scale);
        return new int[]{x, y, width, height};
    }

    public static int[] toFramebuffer(ClipRect clip, float uiScale, float framebufferScale,
                                      float originX, float originY, int framebufferHeight) {
        if (clip == null) throw new IllegalArgumentException("clip must not be null");
        if (uiScale <= 0.0F || framebufferScale <= 0.0F) {
            throw new IllegalArgumentException("scales must be positive");
        }
        int x = Math.round((originX + clip.x() * uiScale) * framebufferScale);
        int width = Math.round(clip.width() * uiScale * framebufferScale);
        int height = Math.round(clip.height() * uiScale * framebufferScale);
        int y = framebufferHeight - Math.round(
                (originY + (clip.y() + clip.height()) * uiScale) * framebufferScale);
        return new int[]{x, y, width, height};
    }
}
