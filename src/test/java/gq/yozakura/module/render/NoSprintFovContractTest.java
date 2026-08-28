package gq.yozakura.module.render;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.Assert.assertTrue;

public class NoSprintFovContractTest {
    @Test
    public void moduleHandlesForgeFovAndIsRegistered() throws IOException {
        String module = source("src/main/java/gq/yozakura/module/render/NoSprintFOV.java");
        String manager = source("src/main/java/gq/yozakura/manager/ModuleManager.java");
        String lunarBridge = source("src/main/java/gq/yozakura/bridge/StandaloneEntityRenderer.java");

        assertTrue(module.contains("FOVUpdateEvent"));
        assertTrue(module.contains("SprintFovPolicy.withoutSprint"));
        assertTrue(manager.contains("addModule(\"NoSprintFOV\""));
        assertTrue(lunarBridge.contains("prepareLunarSprintFov"));
        assertTrue(lunarBridge.contains("restoreLunarSprintFov"));
        assertTrue(lunarBridge.contains("SprintFovPolicy.withoutSprint"));
    }

    private static String source(String path) throws IOException {
        return new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.UTF_8);
    }
}
