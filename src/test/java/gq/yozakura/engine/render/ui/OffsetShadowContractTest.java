package gq.yozakura.engine.render.ui;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.Assert.assertTrue;

public class OffsetShadowContractTest {
    @Test
    public void offsetShadowReusesTheExistingShaderAndFallbackPathWithTranslatedBounds() throws IOException {
        String shapeRenderer = source("src/main/java/gq/yozakura/engine/render/ui/ShapeRenderer.java");
        String renderUtil = source("src/main/java/gq/yozakura/util/render/RenderUtil.java");

        assertTrue(shapeRenderer.contains("public void shadowOffset("));
        assertTrue(shapeRenderer.contains("RenderUtil.drawSoftShadowOffset("));
        assertTrue(renderUtil.contains("public static void drawSoftShadowOffset("));
        assertTrue(renderUtil.contains("drawSoftShadow(x + offsetX, y + offsetY, x2 + offsetX, y2 + offsetY,"));
    }

    private static String source(String path) throws IOException {
        return new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.UTF_8);
    }
}
