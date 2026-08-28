package gq.yozakura.module.render;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class FreeLookRenderIsolationContractTest {
    @Test
    public void customWorldOverlaysAreDispatchedAfterRealFacingIsRestored() throws Exception {
        String source = read("src/main/java/gq/yozakura/bridge/StandaloneEntityRenderer.java");

        int restore = source.indexOf("FreeLook.restorePlayerFacingForOverlays()");
        int render3d = source.indexOf("EventManager.call(new Render3DEvent(partialTicks))");
        int renderWorldLast = source.indexOf("EventManager.call(new gq.yozakura.bridge.forge.RenderWorldLastEvent(partialTicks))");

        assertTrue("standalone bridge must restore real facing before overlay dispatch", restore >= 0);
        assertTrue(restore < render3d);
        assertTrue(restore < renderWorldLast);
    }

    @Test
    public void forgeWorldOverlayBridgeAlsoRestoresRealFacing() throws Exception {
        String source = read("src/main/java/gq/yozakura/bridge/YozakuraEventBridge.java");
        String method = slice(source, "public void onRender3D(RenderWorldLastEvent event)", "@SubscribeEvent\n    public void onMouse");

        int restore = method.indexOf("FreeLook.restorePlayerFacingForOverlays()");
        int dispatch = method.indexOf("EventManager.call(new Render3DEvent(event.partialTicks))");

        assertTrue(restore >= 0);
        assertTrue(restore < dispatch);
    }

    @Test
    public void overlayRestoreDoesNotEndTheFreeLookSession() throws Exception {
        String source = read("src/main/java/gq/yozakura/module/render/FreeLook.java");
        String method = slice(source, "private void restorePlayerFacing()", "public static void restorePlayerFacingForOverlays()");

        assertTrue(method.contains("cameraState.restore(previousPerspective)"));
        assertFalse(method.contains("cameraState.end("));
        assertFalse(method.contains("thirdPersonView ="));
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
