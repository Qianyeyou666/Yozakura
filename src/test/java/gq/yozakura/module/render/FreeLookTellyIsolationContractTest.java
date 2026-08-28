package gq.yozakura.module.render;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class FreeLookTellyIsolationContractTest {
    @Test
    public void bridgeAssistPublishesWhetherTellyOwnsCameraControl() throws Exception {
        String source = read("src/main/java/gq/yozakura/module/world/BridgeAssist.java");
        String method = slice(source, "public boolean isTellyControlActive()", "private boolean canAssist()");

        assertTrue(method.contains("getState()"));
        assertTrue(method.contains("isTellyBridgeMode()"));
    }

    @Test
    public void freeLookYieldsBeforeApplyingAnyCameraRotation() throws Exception {
        String source = read("src/main/java/gq/yozakura/module/render/FreeLook.java");
        String method = slice(source, "public void onRenderTickStart(RenderTickStartEvent event)",
                "public void onRenderTickEnd(RenderTickEndEvent event)");

        int conflictCheck = method.indexOf("isTellyControlActive()");
        int suspend = method.indexOf("suspendForExternalCameraOwner()");
        int apply = method.indexOf("applyPlayerRotation(player, frame.getCameraYaw(), frame.getCameraPitch())");

        assertTrue(conflictCheck >= 0);
        assertTrue(suspend > conflictCheck);
        assertTrue(suspend < apply);
    }

    @Test
    public void yieldingDoesNotRestoreStalePlayerRotationOverTelly() throws Exception {
        String source = read("src/main/java/gq/yozakura/module/render/FreeLook.java");
        String method = slice(source, "private void suspendForExternalCameraOwner()", "private void restoreSession()");

        assertTrue(method.contains("cameraState.end(previousPerspective)"));
        assertTrue(method.contains("thirdPersonView = restore.getPerspective()"));
        assertFalse(method.contains("applyPlayerRotation("));
    }

    private static String read(String relativePath) throws Exception {
        Path path = Paths.get(relativePath);
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }

    private static String slice(String source, String startToken, String endToken) {
        int start = source.indexOf(startToken);
        int end = source.indexOf(endToken, start);
        if (start < 0 || end < 0) {
            return "";
        }
        return source.substring(start, end);
    }
}
