package gq.yozakura.util.render;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;

/**
 * Source-level contract test for {@link RenderUtil}'s integration with
 * {@code ShaderRenderer.beginShapeBatch()/endShapeBatch()}.
 *
 * <p>Background: every {@code RenderUtil.draw*Rect} call historically wrapped
 * its ShaderRenderer draw in {@code GLStateManager.begin2D()/end2D()} — a
 * per-call pushAttrib + pushMatrix + 6 GlStateManager state writes + final
 * syncToCurrent + color reset (~31 GL calls). When the caller brackets the
 * shape draws with {@code ShaderRenderer.beginShapeBatch()/endShapeBatch()},
 * that per-call cost is redundant: the batch already pushed one attrib stack
 * frame and configured the shared alpha/blend/depth state.</p>
 *
 * <p>Protected invariants:
 * <ul>
 *   <li>{@code ShaderRenderer} exposes {@code isBatchActive()} so callers and
 *       {@link RenderUtil} can poll batch state without reflection.</li>
 *   <li>Shape draws used by HUD panels (joined rounded, horizontal gradient,
 *       rounded, bordered, solid rect) skip {@code begin2D/end2D} when a
 *       batch is active, eliminating ~31 GL calls per shape inside a batch.</li>
 *   <li>Outside a batch, every shape draw still calls {@code begin2D/end2D}
 *       exactly once — no regression for callers that do not opt in.</li>
 *   <li>{@code beginShapeBatch} configures the texture/cull state needed by
 *       the raw-rect fallback paths so a skipped {@code begin2D} cannot
 *       leave texture_2D or cull face enabled mid-batch.</li>
 * </ul>
 */
public class RenderUtilBatchContractTest {
    private static final String RENDER_UTIL_PATH =
            "src/main/java/gq/yozakura/util/render/RenderUtil.java";
    private static final String SHADER_RENDERER_PATH =
            "src/main/java/gq/yozakura/engine/render/ShaderRenderer.java";

    @Test
    public void shaderRendererExposesBatchActivePoll() throws IOException {
        String source = read(SHADER_RENDERER_PATH);
        assertTrue("ShaderRenderer must expose isBatchActive() for callers",
                source.contains("public static boolean isBatchActive()"));
        assertTrue("isBatchActive() must return the batchMode field",
                source.contains("return batchMode;"));
    }

    @Test
    public void beginShapeBatchConfiguresTextureAndCullStateForRawFallback() throws IOException {
        String source = read(SHADER_RENDERER_PATH);
        String begin = method(source, "public static void beginShapeBatch() {");

        // Raw-rect fallback paths (roundedRectRaw/joinedRoundedRectRaw) use
        // fixed-function texturing and GL_BACK face culling. When begin2D is
        // skipped inside a batch, beginShapeBatch must disable texture_2D and
        // cull face so a fallback draw does not sample the currently-bound
        // texture or drop triangles whose winding it does not control.
        assertTrue("beginShapeBatch must disableTexture2D for raw fallback paths",
                begin.contains("disableTexture2D();"));
        assertTrue("beginShapeBatch must disableCull for raw fallback paths",
                begin.contains("disableCull();"));
        // ShaderRenderer path still requires alpha test for soft-edge cutoff,
        // and begin2D's disableAlpha must not survive into the batch.
        assertTrue("beginShapeBatch must enableAlpha after begin2D-style state",
                begin.contains("enableAlpha();"));
        assertTrue("beginShapeBatch must disableDepth",
                begin.contains("disableDepth();"));
    }

    @Test
    public void drawJoinedRoundedRectSkipsBegin2DInBatchMode() throws IOException {
        String source = read(RENDER_UTIL_PATH);
        // The 12-arg overload is the entry point ShapeRenderer.joinedRounded
        // dispatches to; the shorter overloads delegate to it.
        String drawJoined = method(source,
                "public static void drawJoinedRoundedRect(float x, float y, float x2, float y2,\n" +
                        "                                             float topLeftRadius, float topRightRadius,\n" +
                        "                                             float bottomRightRadius, float bottomLeftRadius,\n" +
                        "                                             float topJoinStart, float topJoinEnd,\n" +
                        "                                             float bottomJoinStart, float bottomJoinEnd,\n" +
                        "                                             float leftJoinStart, float leftJoinEnd,\n" +
                        "                                             float rightJoinStart, float rightJoinEnd, int color) {");

        assertBatchAware(source, drawJoined,
                "drawJoinedRoundedRect must skip begin2D/end2D when ShaderRenderer.isBatchActive()");
    }

    @Test
    public void drawRoundedRectSkipsBegin2DInBatchMode() throws IOException {
        String source = read(RENDER_UTIL_PATH);
        // The 6-arg round/color overload is the one ShapeRenderer.rounded hits.
        // The (x, y, x2, y2, borderColor, fillColor) overload is a thin
        // delegate to drawRoundedBorderedRect and does not own its own state.
        String drawRounded = method(source,
                "public static void drawRoundedRect(float x, float y, float x2, float y2, float round, int color) {");
        assertBatchAware(source, drawRounded,
                "drawRoundedRect must skip begin2D/end2D when ShaderRenderer.isBatchActive()");
    }

    @Test
    public void drawRoundedBorderedRectSkipsBegin2DInBatchMode() throws IOException {
        String source = read(RENDER_UTIL_PATH);
        String drawBordered = method(source,
                "public static void drawRoundedBorderedRect(float x, float y, float x2, float y2, float radius, float borderWidth, int fillColor, int borderColor) {");
        assertBatchAware(source, drawBordered,
                "drawRoundedBorderedRect must skip begin2D/end2D when ShaderRenderer.isBatchActive()");
    }

    @Test
    public void drawRectSkipsBegin2DInBatchMode() throws IOException {
        String source = read(RENDER_UTIL_PATH);
        String drawRect = method(source,
                "public static void drawRect(float left, float top, float right, float bottom, int color) {");
        assertBatchAware(source, drawRect,
                "drawRect must skip begin2D/end2D when ShaderRenderer.isBatchActive()");
    }

    @Test
    public void drawHorizontalGradientRectSkipsBegin2DInBatchMode() throws IOException {
        String source = read(RENDER_UTIL_PATH);
        // The horizontal gradient variant is used by NightBloom raised bands.
        String drawGradient = method(source,
                "public static void drawHorizontalGradientRect(float x, float y, float x2, float y2, int leftColor, int rightColor) {");
        assertBatchAware(source, drawGradient,
                "drawHorizontalGradientRect must skip begin2D/end2D when ShaderRenderer.isBatchActive()");
    }

    /**
     * Asserts that the given method body uses the standard "ownsBatch" pattern:
     * queries {@code ShaderRenderer.isBatchActive()} once before the try block,
     * calls {@code begin2D/end2D} only when the caller does not own the batch,
     * and never calls {@code begin2D/end2D} unconditionally.
     */
    private static void assertBatchAware(String fullSource, String methodBody, String message) {
        // The pattern: boolean ownsBatch = !ShaderRenderer.isBatchActive();
        assertTrue(message + " (must query isBatchActive before the try block)",
                methodBody.contains("ShaderRenderer.isBatchActive()"));
        // begin2D/end2D must be guarded by the ownsBatch flag, not called
        // unconditionally. The unconditional idiom looks like
        // "GLStateManager.begin2D();\n        try {" (no guard line between).
        assertFalse(message + " (begin2D must not be called unconditionally)",
                methodBody.contains("GLStateManager.begin2D();\n        try {"));
        assertFalse(message + " (end2D must not be called unconditionally in finally)",
                methodBody.contains("} finally {\n            GLStateManager.end2D();\n        }"));
        // The standard guard idiom.
        assertTrue(message + " (must use ownsBatch guard)",
                methodBody.contains("if (ownsBatch)"));
    }

    /**
     * Extracts the method body starting at {@code startMarker} and ending at
     * the next {@code public static} method signature. This avoids the caller
     * having to know the exact ordering of methods in the source file.
     */
    private static String method(String source, String startMarker) {
        int start = source.indexOf(startMarker);
        assertTrue("missing method with marker: " + startMarker, start >= 0);
        // Find the next "    public static " method signature after start.
        // Using 4-space indent + public static ensures we land on a real
        // method declaration, not an inner class or field.
        int searchFrom = start + startMarker.length();
        int nextPublic = source.indexOf("\n    public static ", searchFrom);
        assertTrue("missing next public static method after: " + startMarker,
                nextPublic > start);
        return source.substring(start, nextPublic);
    }

    private static String read(String path) throws IOException {
        return new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.UTF_8);
    }
}
