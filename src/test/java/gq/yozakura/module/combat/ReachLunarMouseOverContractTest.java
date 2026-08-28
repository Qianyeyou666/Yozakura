package gq.yozakura.module.combat;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ReachLunarMouseOverContractTest {
    @Test
    public void lunarPrecomputesExtendedMouseOverBeforeTheFirstAttackPress() throws IOException {
        String reach = source("src/main/java/gq/yozakura/module/combat/Reach.java");
        String generator = source("src/main/java/gq/yozakura/bridge/RuntimeEntityRendererHookGenerator.java");
        String renderer = source("src/main/java/gq/yozakura/bridge/StandaloneEntityRenderer.java");

        String mouseOver = method(reach, "public void onMouseOver(MouseOverEvent event)",
                "private void handleLeftClick()");
        assertTrue(mouseOver.contains("applyReach(event.getPartialTicks());"));
        assertFalse(mouseOver.contains("isAttackHeld()"));
        assertTrue(reach.contains("if (!canReach()) {\n            lastReachHit = null;"));

        int vanillaMouseOver = generator.indexOf("writeInvokeSpecial(code, superGetMouseOver);");
        int reachCallback = generator.indexOf("writeInvokeStatic(code, dispatchMouseOver);");
        assertTrue(vanillaMouseOver >= 0 && reachCallback > vanillaMouseOver);

        int moduleCallbacks = renderer.indexOf("EventManager.call(new MouseOverEvent(partialTicks));");
        int finalReachOverride = renderer.indexOf("Reach.applyRuntimeMouseOverOverride(partialTicks);");
        assertTrue(moduleCallbacks >= 0 && finalReachOverride > moduleCallbacks);
        assertTrue(reach.contains("public static void applyRuntimeMouseOverOverride(float partialTicks)"));
    }

    private static String method(String source, String start, String end) {
        int from = source.indexOf(start);
        int to = source.indexOf(end, from);
        return source.substring(from, to);
    }

    private static String source(String path) throws IOException {
        return new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.UTF_8);
    }
}
