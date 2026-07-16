package gq.yozakura.module.combat;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class KillAuraTargetEspMigrationContractTest {
    @Test
    public void targetEspOwnsTheThreeLegacyKillAuraVisualModes() throws IOException {
        String aura = source("src/main/java/gq/yozakura/module/combat/KillAura.java");
        String targetEsp = source("src/main/java/gq/yozakura/module/render/TargetESP.java");

        assertTrue(targetEsp.contains("DEFAULT,\n        HUD,\n        SCAN,"));
        assertTrue(targetEsp.contains("drawLegacyBox(target, current == EspMode.HUD)"));
        assertTrue(targetEsp.contains("drawLegacyScan(bodyHeight, alphaScale)"));
        assertFalse(aura.contains("showTarget"));
        assertFalse(aura.contains("renderScan("));
        assertFalse(aura.contains("public void onRender(Render3DEvent event)"));
    }

    @Test
    public void autoBlockNoneIsTheDefaultAndCannotEnterBlockState() throws IOException {
        String aura = source("src/main/java/gq/yozakura/module/combat/KillAura.java");
        String modern = source("src/main/java/gq/yozakura/ui/click/web/ModernWebClickGuiController.java");

        assertTrue(aura.contains("new String[]{\"None\", \"RELEASE\", \"INTERACT\", \"SWITCH\", \"BLINK\"}"));
        assertTrue(aura.contains("this.autoBlock.getValue() == AUTOBLOCK_NONE"));
        assertTrue(modern.contains(".mode(\"AutoBlock\", \"None\", \"None\", \"RELEASE\", \"INTERACT\", \"SWITCH\", \"BLINK\")"));
        assertFalse(modern.contains(".bool(\"Show Target\""));
    }

    private static String source(String path) throws IOException {
        return new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.UTF_8)
                .replace("\r\n", "\n");
    }
}
