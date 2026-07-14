package gq.yozakura.bridge;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.Assert.assertTrue;

/**
 * Guards the boundary between PRE-update rotation claims and a pre-place
 * target that is safe to expose to vanilla's right-click code.
 */
public class RotationResolutionLifecycleContractTest {
    @Test
    public void bothBridgesResolveRotationAfterAllPreListenersHaveRun() throws IOException {
        assertResolutionDispatch(source("src/main/java/gq/yozakura/bridge/YozakuraEventBridge.java"));
        assertResolutionDispatch(source("src/main/java/gq/yozakura/bridge/StandaloneEventBridge.java"));
    }

    @Test
    public void bridgeAssistPublishesPrePlaceOnlyFromTheResolvedRotationBoundary() throws IOException {
        String bridgeAssist = source("src/main/java/gq/yozakura/module/world/BridgeAssist.java");
        String controller = source("src/main/java/gq/yozakura/module/world/BridgeAssistPrePlaceController.java");

        assertTrue(bridgeAssist.contains("RotationResolvedEvent"));
        assertTrue(bridgeAssist.contains("prePlaceController.onRotationResolved(event)"));
        assertTrue(controller.contains("void onRotationResolved(RotationResolvedEvent event)"));
        assertTrue(controller.contains("pendingTarget"));
    }

    private static void assertResolutionDispatch(String source) {
        int rotationExit = source.indexOf("RotationExitState.apply(update)");
        int resolution = source.indexOf("EventManager.call(new RotationResolvedEvent(update))");
        int rotationState = source.indexOf("RotationState.applyState", rotationExit);

        assertTrue(rotationExit >= 0);
        assertTrue(resolution > rotationExit);
        assertTrue(rotationState > resolution);
    }

    private static String source(String path) throws IOException {
        return new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.UTF_8);
    }
}
