package gq.yozakura.module.render;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.Assert.assertTrue;

public class TargetHudAimRangeContractTest {
    @Test
    public void crosshairTargetingIsLimitedToSixBlocks() throws IOException {
        String source = new String(Files.readAllBytes(Paths.get(
                "src/main/java/gq/yozakura/module/render/TargetHUD.java")), StandardCharsets.UTF_8);

        assertTrue(source.contains("private static final double AIM_TARGET_RANGE = 6.0D"));
        assertTrue(source.contains("mc.thePlayer.getDistanceSqToEntity(direct) <= AIM_TARGET_RANGE * AIM_TARGET_RANGE"));
    }
}
