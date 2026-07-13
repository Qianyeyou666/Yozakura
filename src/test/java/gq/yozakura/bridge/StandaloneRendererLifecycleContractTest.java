package gq.yozakura.bridge;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.Assert.assertTrue;

/**
 * Contract checks for renderer hooks that cross the standalone class-loader boundary.
 */
public class StandaloneRendererLifecycleContractTest {
    @Test
    public void runtimeRendererSubclassesArePreservedAndHooksRestoreOnlyWhatTheyOwn() throws IOException {
        String entityRenderer = source("src/main/java/gq/yozakura/bridge/StandaloneEntityRenderer.java");
        String gui = source("src/main/java/gq/yozakura/bridge/StandaloneGuiIngame.java");
        String living = source("src/main/java/gq/yozakura/bridge/StandaloneLivingRendererBridge.java");
        String bridge = source("src/main/java/gq/yozakura/bridge/StandaloneEventBridge.java");

        assertTrue("A runtime EntityRenderer subclass must not be replaced by the vanilla hook",
                entityRenderer.contains("current.getClass() != EntityRenderer.class"));
        assertTrue("Skipping an unsafe renderer replacement must be visible rather than silent",
                entityRenderer.contains("Render2D/Render3D dispatch was not installed")
                        && entityRenderer.contains("requires runtime verification"));
        assertTrue("The entity renderer hook must keep both sides of its reversible ownership pair",
                entityRenderer.contains("private static EntityRenderer originalRenderer;")
                        && entityRenderer.contains("private static StandaloneEntityRenderer installedRenderer;")
                        && entityRenderer.contains("public static void uninstall(Minecraft minecraft)"));
        assertTrue("Entity renderer teardown may restore only the hook installed by this loader",
                entityRenderer.contains("minecraft.entityRenderer == installed"));

        assertTrue("The GUI hook must retain its original instance for reversible teardown",
                gui.contains("private static GuiIngame originalGui;")
                        && gui.contains("private static StandaloneGuiIngame installedGui;")
                        && gui.contains("public static void uninstall(Minecraft minecraft)"));
        assertTrue("GUI teardown may restore only the hook installed by this loader",
                gui.contains("minecraft.ingameGUI == installed"));

        assertTrue("Living renderer wrappers must have an ownership-aware uninstall path",
                living.contains("public static void uninstall(Minecraft minecraft)")
                        && living.contains("restoreEntityRenderers")
                        && living.contains("restorePlayerRenderers")
                        && living.contains("restorePlayerRendererFields"));
        assertTrue("One failed renderer restoration must be reported without abandoning other owned wrappers",
                living.contains("logUninstallFailure")
                        && living.contains("Unable to restore standalone player skin renderer map")
                        && living.contains("Unable to read owned standalone renderer delegate"));
        assertTrue("Standalone shutdown must detach every renderer hook before the loader can be replaced",
                bridge.contains("StandaloneLivingRendererBridge.uninstall(mc);")
                        && bridge.contains("StandaloneGuiIngame.uninstall(mc);")
                        && bridge.contains("StandaloneEntityRenderer.uninstall(mc);"));
    }

    private static String source(String path) throws IOException {
        return new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.UTF_8);
    }
}
