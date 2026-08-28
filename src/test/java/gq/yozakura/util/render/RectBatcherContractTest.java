package gq.yozakura.util.render;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.Assert.assertTrue;

/**
 * Source-level contract test for {@link RectBatcher}. The class delegates to
 * LWJGL/GLStateManager, so we cannot exercise it in a host-less test JVM;
 * instead we assert the structural invariants that protect the render-thread
 * hot path. The pattern mirrors {@code ChamsTexturedContractTest}.
 *
 * <p>Protected invariants:
 * <ul>
 *   <li>{@code begin()} is the single entry that acquires 2D state; {@code add()}
 *       refuses to run without it (prevents silent state leaks).</li>
 *   <li>{@code flush()} always releases 2D state, even on empty batch.</li>
 *   <li>Same-color rectangles accumulate into one {@code glBegin/glEnd} pair;
 *       a color change forces a flush of the previous bucket.</li>
 *   <li>Alpha {@code <= 0} and zero-area rectangles are skipped, matching
 *       {@code RenderUtil.drawRect}'s early-out contract.</li>
 *   <li>Public {@code RenderUtil.drawRect} API is unchanged (no regression in
 *       callers that do not opt into batching).</li>
 * </ul>
 */
public class RectBatcherContractTest {

    @Test
    public void beginAcquiresTwoDStateOnceAndFlushReleasesIt() throws IOException {
        String source = source("src/main/java/gq/yozakura/util/render/RectBatcher.java");
        String beginBody = between(source, "public void begin()", "public void add");
        String flushBody = between(source, "public void flush()", "private void flushBucket");

        assertTrue("begin() must call GLStateManager.begin2D() exactly once",
                beginBody.contains("GLStateManager.begin2D();"));
        assertTrue("begin() must guard against double-open",
                beginBody.contains("RectBatcher.begin() called while already open"));
        assertTrue("flush() must call GLStateManager.end2D() exactly once",
                flushBody.contains("GLStateManager.end2D();"));
        assertTrue("flush() must be safe to call on an already-closed batcher",
                flushBody.contains("if (!open)"));
    }

    @Test
    public void addEnforcesBeginFirstAndSkipsTransparentAndZeroArea() throws IOException {
        String source = source("src/main/java/gq/yozakura/util/render/RectBatcher.java");
        String addBody = between(source, "public void add", "public void flush");

        assertTrue("add() must require begin() first",
                addBody.contains("RectBatcher.add() called without begin()"));
        assertTrue("add() must skip alpha<=0 colors",
                addBody.contains("RenderUtil.getAlpha(color) <= 0"));
        assertTrue("add() must skip zero-area rectangles",
                addBody.contains("right <= left || bottom <= top"));
    }

    @Test
    public void sameColorRectsShareOneGLBeginAndColorChangeFlushes() throws IOException {
        String source = source("src/main/java/gq/yozakura/util/render/RectBatcher.java");
        String addBody = between(source, "public void add", "public void flush");
        String flushBucketBody = between(source, "private void flushBucket", "private void ensureCapacity");

        assertTrue("add() must flush the bucket when color changes",
                addBody.contains("if (currentColor != color)"));
        assertTrue("flushBucket must set color once then issue a single glBegin/glEnd",
                flushBucketBody.contains("RenderUtil.glColor(currentColor);"));
        assertTrue("flushBucket must use GL_QUADS for the batched geometry",
                flushBucketBody.contains("GL11.glBegin(GL11.GL_QUADS);"));
        assertTrue("flushBucket must guard the glEnd in a finally to avoid GL state leak",
                flushBucketBody.contains("} finally {"));
    }

    @Test
    public void renderUtilDrawRectPublicContractIsUnchanged() throws IOException {
        String source = source("src/main/java/gq/yozakura/util/render/RenderUtil.java");
        // Public drawRect signature must still exist and still pay for its own
        // begin2D/end2D pair (so non-batching callers see no behavior change).
        assertTrue("drawRect public API must remain",
                source.contains("public static void drawRect(float left, float top, float right, float bottom, int color)"));
        assertTrue("drawRect must still call begin2D for itself",
                source.contains("GLStateManager.begin2D();"));
        assertTrue("drawRect must still call end2D for itself",
                source.contains("GLStateManager.end2D();"));
    }

    private static String source(String relativePath) throws IOException {
        String path = System.getProperty("user.dir") + "/" + relativePath;
        return new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.UTF_8);
    }

    private static String between(String source, String startMarker, String endMarker) {
        int start = source.indexOf(startMarker);
        if (start < 0) {
            return "";
        }
        int end = source.indexOf(endMarker, start + startMarker.length());
        if (end < 0) {
            return source.substring(start);
        }
        return source.substring(start, end);
    }
}
