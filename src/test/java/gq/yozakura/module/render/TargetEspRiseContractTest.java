package gq.yozakura.module.render;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.Assert.assertTrue;

public class TargetEspRiseContractTest {
    @Test
    public void exposesRiseSigmaRingModeAndKeepsItSeparateFromLegacyScan() throws IOException {
        String source = source("src/main/java/gq/yozakura/module/render/TargetESP.java");
        String webClickGui = source("src/main/java/gq/yozakura/ui/click/web/ModernWebClickGuiController.java");

        assertTrue(source.contains("RISE"));
        assertTrue(webClickGui.contains("\"Scan\", \"Rise\", \"Cosmic\""));
        assertTrue(source.contains("drawRiseSigmaRing("));
        assertTrue(source.contains("current == EspMode.RISE"));
        assertTrue(source.contains("GL11.GL_TRIANGLE_STRIP"));
        assertTrue(source.contains("riseSigmaRingHeight"));
        assertTrue(source.contains("riseSigmaRingSegments"));
    }

    @Test
    public void riseGeometryUsesMovingVerticalGradientAndDoesNotRequireShader() throws IOException {
        String source = source("src/main/java/gq/yozakura/module/render/TargetESP.java");
        String rise = method(source, "    private void drawRiseSigmaRing(", "    private void drawNightBloom(");

        assertTrue(rise.contains("riseSigmaRingHeight(bodyHeight, time)"));
        assertTrue(rise.contains("riseSigmaRingTrailOffset(bodyHeight, time)"));
        assertTrue(rise.contains("setColor(ringColor, alpha * 0.25f)"));
        assertTrue(rise.contains("setColor(trailColor, 0.0f)"));
        assertTrue(rise.contains("GL11.GL_TRIANGLE_STRIP"));
        assertTrue(rise.contains("GL11.glVertex3d"));
    }

    private static String source(String path) throws IOException {
        return new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.UTF_8)
                .replace("\r\n", "\n");
    }

    private static String method(String source, String start, String end) {
        int begin = source.indexOf(start);
        int finish = source.indexOf(end, begin + start.length());
        assertTrue(begin >= 0 && finish > begin);
        return source.substring(begin, finish);
    }
}
