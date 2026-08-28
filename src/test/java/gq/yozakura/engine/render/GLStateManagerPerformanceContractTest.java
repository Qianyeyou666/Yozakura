package gq.yozakura.engine.render;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class GLStateManagerPerformanceContractTest {
    private static final String PATH =
            "src/main/java/gq/yozakura/engine/render/GLStateManager.java";

    @Test
    public void twoDimensionalScopeExitDoesNotReadBackOpenGlState() throws IOException {
        String source = read();
        String end2d = method(source, "public static void end2D()", "public static void beginTextured2D");
        String endTextured2d = method(source, "public static void endTextured2D()", "public static void color(int color)");

        assertFalse("glPopAttrib already restores the driver state; end2D must not force glGet/glIsEnabled synchronization",
                end2d.contains("syncToCurrent()"));
        assertFalse("glPopAttrib already restores the driver state; endTextured2D must not force glGet/glIsEnabled synchronization",
                endTextured2d.contains("syncToCurrent()"));
        assertTrue("scope exit must still leave the fixed-function color in the standard white state",
                end2d.contains("color(1.0f, 1.0f, 1.0f, 1.0f);"));
        assertTrue("textured scope exit must still leave the fixed-function color in the standard white state",
                endTextured2d.contains("color(1.0f, 1.0f, 1.0f, 1.0f);"));
    }

    private static String read() throws IOException {
        return new String(Files.readAllBytes(Paths.get(PATH)), StandardCharsets.UTF_8);
    }

    private static String method(String source, String startMarker, String endMarker) {
        int start = source.indexOf(startMarker);
        int end = source.indexOf(endMarker, start);
        assertTrue("missing method " + startMarker, start >= 0 && end > start);
        return source.substring(start, end);
    }
}
