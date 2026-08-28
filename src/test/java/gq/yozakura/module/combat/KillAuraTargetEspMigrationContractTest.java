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

        assertTrue(aura.contains("new String[]{\"None\", \"RELEASE\", \"INTERACT\", \"SWITCH\", \"BLINK\", \"LEGIT\", \"FullAB\", \"BypassAll\", \"Hypixel\"},"));
        assertTrue(aura.contains("new String[]{\"None\", \"RELEASE\", \"INTERACT\", \"SWITCH\", \"BLINK\", \"FullAB\", \"BypassAll\", \"Hypixel\"}"));
        assertTrue(aura.contains("this.autoBlock.getValue() == AUTOBLOCK_NONE"));
        assertTrue(modern.contains(".mode(\"AutoBlock\", \"None\", \"None\", \"RELEASE\", \"INTERACT\", \"SWITCH\", \"BLINK\", \"FullAB\", \"BypassAll\", \"Hypixel\")"));
        assertFalse(modern.contains(".bool(\"Show Target\""));
    }

    @Test
    public void replacedReferenceModesStayOutOfSwitchAndContinuousBlinkPaths() throws IOException {
        String aura = source("src/main/java/gq/yozakura/module/combat/KillAura.java");

        assertTrue(aura.contains("private static final int AUTOBLOCK_LEGIT = 5;"));
        assertTrue(aura.contains("private static final int AUTOBLOCK_FULL_AB = 6;"));
        int spoofGuard = aura.indexOf("mode == AUTOBLOCK_SWITCH || mode == AUTOBLOCK_BLINK");
        assertTrue("Spoof slot guard must remain restricted to SWITCH and BLINK",
                spoofGuard >= 0);
        String blinkUpdate = method(aura, "    private void updateAutoBlockBlink(",
                "    private void startReferenceBlink()");
        assertTrue("continuous Blink enable must remain restricted to BLINK only",
                blinkUpdate.contains("shouldBlock && mode == AUTOBLOCK_BLINK"));
        assertTrue("FullAB must own its phase-controlled Blink lifecycle",
                blinkUpdate.contains("mode != AUTOBLOCK_FULL_AB"));
        assertTrue("Hypixel must own its phase-controlled Blink lifecycle",
                blinkUpdate.contains("mode != AUTOBLOCK_HYPIXEL"));
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
