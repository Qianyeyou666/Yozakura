package gq.yozakura.module.render;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class StorageEspBatchContractTest {
    @Test
    public void outlineModeBatchesStorageEdgesAndFacesIntoOneDrawPerTopology() throws IOException {
        String source = source();
        String batch = method(source, "private void renderStorageBatch()", "private boolean hasSelectedStorage()");

        assertTrue(source.contains("renderStorageBatch();"));
        assertTrue(batch.contains("renderer.begin(GL11.GL_LINES, DefaultVertexFormats.POSITION_COLOR);"));
        assertTrue(batch.contains("renderer.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR);"));
        assertFalse(batch.contains("RenderGlobal.drawSelectionBoundingBox"));
    }

    private static String source() throws IOException {
        return new String(Files.readAllBytes(Paths.get(
                "src/main/java/gq/yozakura/module/render/StorageESP.java")), StandardCharsets.UTF_8);
    }

    private static String method(String source, String start, String end) {
        int begin = source.indexOf(start);
        int finish = source.indexOf(end, begin + start.length());
        return begin < 0 || finish < 0 ? "" : source.substring(begin, finish);
    }
}
