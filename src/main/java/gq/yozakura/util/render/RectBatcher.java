package gq.yozakura.util.render;

import gq.yozakura.engine.render.GLStateManager;
import org.lwjgl.opengl.GL11;

/**
 * Immediate-mode rectangle batching for the legacy {@code RenderUtil.drawRect}
 * hot path. Each {@link RenderUtil#drawRect} call historically pays for a full
 * {@link GLStateManager#begin2D()}/{@link GLStateManager#end2D()} pair (which
 * includes {@code glPushAttrib} + {@code glPushMatrix} + a {@code syncToCurrent}
 * that issues ~7 {@code glGet*} queries). When ESP draws ~14 rects per entity
 * across 30+ entities, that is 420+ state push/pop + 420+ GL query syncs per
 * frame, all to emit rectangles that share the same 2D ortho state.
 *
 * <p>{@code RectBatcher} lets a caller wrap a sequence of same-state rectangle
 * draws in {@link #begin()}/{@link #flush()}, so the 2D state is paid exactly
 * once and consecutive same-color rectangles share a single {@code glBegin/glEnd}
 * pair. The public {@code RenderUtil.drawRect} API is unchanged; batching is
 * strictly opt-in on hot paths that opt to use it.
 *
 * <p>Constraints:
 * <ul>
 *   <li>Single-threaded render-thread use only (same assumption as
 *       {@code GLStateManager} and {@code RenderUtil.Rect.tmp}).</li>
 *   <li>Not reentrant. {@code begin()} must not be called while another batch
 *       is open.</li>
 *   <li>Only axis-aligned solid rectangles are supported. Gradients, rounded
 *       rects and textured rects stay on their existing code paths.</li>
 *   <li>Alpha {@code <= 0} entries are silently skipped to match
 *       {@code RenderUtil.drawRect}'s early-out contract.</li>
 * </ul>
 *
 * <p>Color tracking is single-bucket: when {@link #add} sees a different color
 * than the current bucket, it flushes the bucket and starts a new one. This
 * matches ESP's draw pattern (consecutive same-color rects for borders/corners)
 * without the overhead of a full color-keyed map.
 */
public final class RectBatcher {
    /** Initial vertex capacity (8 floats = 4 vertices = 1 quad). Grows as needed. */
    private static final int INITIAL_CAPACITY = 64;
    /** Sentinels for "no color bucket active yet". */
    private static final int NO_COLOR = 0;

    private float[] vertices = new float[INITIAL_CAPACITY];
    private int vertexCount;
    private int currentColor = NO_COLOR;
    private boolean open;

    /**
     * Begins a batched 2D rectangle sequence. Acquires the shared 2D GL state
     * (ortho + blend + no texture) exactly once for the whole sequence.
     *
     * <p>Must be paired with {@link #flush()} in a try/finally.
     */
    public void begin() {
        if (open) {
            throw new IllegalStateException("RectBatcher.begin() called while already open");
        }
        GLStateManager.begin2D();
        open = true;
        currentColor = NO_COLOR;
        vertexCount = 0;
    }

    /**
     * Adds a solid rectangle to the current batch. If the color differs from
     * the current bucket, the previous bucket is flushed first (one
     * {@code glBegin/glEnd}) so that per-vertex color state stays consistent.
     *
     * <p>Coordinates are passed as primitives on purpose: {@code RenderUtil}'s
     * shared {@code Rect.tmp} static instance would be a use-after-free hazard
     * if a caller accumulated references to it across multiple {@code add}s.
     */
    public void add(float left, float top, float right, float bottom, int color) {
        if (!open) {
            throw new IllegalStateException("RectBatcher.add() called without begin()");
        }
        if (RenderUtil.getAlpha(color) <= 0) {
            return;
        }
        if (right <= left || bottom <= top) {
            return;
        }
        if (currentColor != color) {
            flushBucket();
            currentColor = color;
        }
        ensureCapacity(8);
        vertices[vertexCount++] = left;
        vertices[vertexCount++] = top;
        vertices[vertexCount++] = left;
        vertices[vertexCount++] = bottom;
        vertices[vertexCount++] = right;
        vertices[vertexCount++] = bottom;
        vertices[vertexCount++] = right;
        vertices[vertexCount++] = top;
    }

    /**
     * Flushes any pending rectangles and releases the 2D GL state acquired in
     * {@link #begin()}. Safe to call on an empty batch (still pays one
     * begin2D/end2D pair, which is the no-batch baseline).
     */
    public void flush() {
        if (!open) {
            return;
        }
        flushBucket();
        GLStateManager.end2D();
        open = false;
        currentColor = NO_COLOR;
    }

    /** Flushes the current color bucket without releasing the 2D state. */
    private void flushBucket() {
        if (vertexCount == 0) {
            return;
        }
        RenderUtil.glColor(currentColor);
        GL11.glBegin(GL11.GL_QUADS);
        try {
            for (int i = 0; i < vertexCount; i += 2) {
                GL11.glVertex2f(vertices[i], vertices[i + 1]);
            }
        } finally {
            GL11.glEnd();
        }
        vertexCount = 0;
    }

    private void ensureCapacity(int floatsNeeded) {
        if (vertexCount + floatsNeeded <= vertices.length) {
            return;
        }
        float[] grown = new float[vertices.length * 2];
        System.arraycopy(vertices, 0, grown, 0, vertexCount);
        vertices = grown;
    }
}
