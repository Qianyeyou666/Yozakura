package gq.yozakura.module.render;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.Assert.assertTrue;

public class TargetHudFollowContractTest {
    @Test
    public void everyTargetHudStyleUsesTheSharedWorldFollowPosition() throws IOException {
        String source = new String(Files.readAllBytes(Paths.get(
                "src/main/java/gq/yozakura/module/render/TargetHUD.java")), StandardCharsets.UTF_8);

        assertTrue(source.contains("new Option<Boolean>(\"Follow\", \"Follow\", false)"));
        assertTrue(source.contains("followProjection.capture(target, event.partialTicks"));
        assertTrue(count(source, "resolveHudPosition(") >= 5);
    }

    private static int count(String source, String needle) {
        int count = 0;
        int index = 0;
        while ((index = source.indexOf(needle, index)) >= 0) {
            count++;
            index += needle.length();
        }
        return count;
    }
}
