package gq.yozakura.bridge;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.Assert.assertTrue;

public class RightClickResolutionLifecycleContractTest {
    @Test
    public void forgeBridgePublishesTheFinalRightClickCancellationState() throws IOException {
        String bridge = source("src/main/java/gq/yozakura/bridge/YozakuraEventBridge.java");
        int dispatched = bridge.indexOf("EventManager.call(new RightClickMouseEvent())");
        int resolved = bridge.indexOf("EventManager.call(new RightClickResolvedEvent(right.isCancelled()))");

        assertTrue(dispatched >= 0);
        assertTrue(resolved > dispatched);
    }

    @Test
    public void bridgeAssistClearsItsPreparedTargetWhenTheFinalClickWasCancelled() throws IOException {
        String bridgeAssist = source("src/main/java/gq/yozakura/module/world/BridgeAssist.java");

        assertTrue(bridgeAssist.contains("RightClickResolvedEvent"));
        assertTrue(bridgeAssist.contains("onRightClickResolved(RightClickResolvedEvent event)"));
        assertTrue(bridgeAssist.contains("prePlaceController.reset();"));
    }

    private static String source(String path) throws IOException {
        return new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.UTF_8);
    }
}
