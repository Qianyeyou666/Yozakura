package gq.yozakura.engine.render;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class JoinedRoundedRenderingContractTest {
    private static final String SHADER_SOURCE =
            "src/main/java/gq/yozakura/engine/render/ShaderRenderer.java";
    private static final String RENDER_UTIL_SOURCE =
            "src/main/java/gq/yozakura/util/render/RenderUtil.java";

    @Test
    public void joinedRowsUsePerCornerDerivativeAntialiasingWithoutOutsidePadding() throws IOException {
        String source = source(SHADER_SOURCE);
        String method = between(source, "public static boolean drawJoinedRoundedRect(",
                "public static boolean drawRoundedGradientRect(");
        String fragment = between(source, "private static final String JOINED_ROUNDED_FRAGMENT",
                "private static final String ROUNDED_GRADIENT_FRAGMENT");

        assertTrue(method.contains("getJoinedRoundedProgram()"));
        assertTrue("the joined quad must stop exactly at the row bounds",
                method.contains("drawQuad(left, top, right, bottom, 0.0f)"));
        assertFalse("outside padding would restore the dark double-blended seam",
                method.contains("EDGE_PADDING"));
        assertTrue(fragment.contains("uniform vec4 cornerRadii;"));
        assertTrue("touching translucent rows must identify their shared horizontal edges",
                fragment.contains("uniform vec4 joinRanges;"));
        assertTrue(method.contains("program.set4f(\"joinRanges\", topJoinStart, topJoinEnd, "
                + "bottomJoinStart, bottomJoinEnd)"));
        assertTrue("screen-space derivatives keep the corner smooth at every HUD scale",
                fragment.contains("fwidth(distance)"));
        assertTrue("coverage must be centered on the mathematical edge without expanding the quad",
                fragment.contains("smoothstep(-antialias, antialias, distance)"));
        assertTrue("a shared edge must become fully covered instead of leaking the world background",
                fragment.contains("coverage = max(coverage, max(joinedCoverage, sideCoverage))"));
        assertTrue("join ranges describe hidden shared topology, so their endpoints must be fully covered",
                fragment.contains("float intervalMask(float value, vec2 range)"));
        assertTrue("only the actual overlap interval may suppress edge antialiasing",
                fragment.contains("intervalMask(coord.x, joinRanges.xy"));
        assertTrue("the bottom edge must use the bottom pair rather than the top interval",
                fragment.contains("bottomBand * intervalMask(coord.x, joinRanges.zw"));
        assertTrue("side joins must cover only their exact vertical interval",
                fragment.contains("intervalMask(coord.y, sideJoinRanges.xy"));
        assertFalse("feathering a hidden join endpoint leaves a dark vertical groove between translucent rows",
                fragment.contains("intervalCoverage"));
    }

    @Test
    public void joinedRoundedUtilityKeepsThePolygonOnlyAsAShaderFallback() throws IOException {
        String source = source(RENDER_UTIL_SOURCE);
        String method = between(source, "public static void drawJoinedRoundedRect(",
                "public static void drawRoundRect(");

        assertTrue(method.contains("ShaderRenderer.drawJoinedRoundedRect("));
        assertTrue(method.contains("joinedRoundedRectRaw("));
        assertTrue(method.indexOf("ShaderRenderer.drawJoinedRoundedRect(")
                < method.indexOf("joinedRoundedRectRaw("));
    }

    private static String between(String source, String start, String end) {
        int startIndex = source.indexOf(start);
        if (startIndex < 0) {
            return "";
        }
        int endIndex = source.indexOf(end, startIndex + start.length());
        return source.substring(startIndex, endIndex < 0 ? source.length() : endIndex);
    }

    private static String source(String path) throws IOException {
        return new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.UTF_8);
    }
}
