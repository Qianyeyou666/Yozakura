package gq.yozakura.ui.click;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ConfigProfilesUiContractTest {
    @Test
    public void everyClickGuiStyleGetsTheSharedProfilesEntry() throws IOException {
        String manager = source("src/main/java/gq/yozakura/manager/ModuleManager.java");
        String module = source("src/main/java/gq/yozakura/module/config/ConfigProfiles.java");

        assertTrue(manager.contains("new ConfigProfiles()"));
        assertTrue(manager.contains("addModule(\"cfgmanager\""));
        assertTrue(module.contains("super(\"cfgmanager\""));
        assertTrue(module.contains("ModuleType.Config")
                && module.contains("new ConfigProfileScreen(mc.currentScreen)"));
    }

    @Test
    public void legacyGlobalConfigActionsAreNotExposed() throws IOException {
        String manager = source("src/main/java/gq/yozakura/manager/ModuleManager.java");
        String modern = source("src/main/java/gq/yozakura/ui/click/web/ModernWebClickGuiController.java");

        assertFalse(manager.contains("new IGN()"));
        assertFalse(manager.contains("new LoadConfig()"));
        assertFalse(manager.contains("new SaveConfig()"));
        assertFalse(manager.contains("new Uninject()"));
        assertFalse(modern.contains("add(\"CopyName\""));
        assertFalse(modern.contains("add(\"LoadConfig\""));
        assertFalse(modern.contains("add(\"SaveConfig\""));
        assertFalse(modern.contains("add(\"Uninject\""));
    }

    @Test
    public void profileScreenExposesDiscoveryFolderSaveAndLoadActions() throws IOException {
        String screen = source("src/main/java/gq/yozakura/ui/click/ConfigProfileScreen.java");

        assertTrue(screen.contains("ConfigBridge.listProfiles()"));
        assertTrue(screen.contains("ConfigBridge.openProfileDirectory()"));
        assertTrue(screen.contains("ConfigBridge.saveProfile("));
        assertTrue(screen.contains("ConfigBridge.loadProfile("));
    }

    @Test
    public void yozakuraBottomBarProvidesDirectProfileAndFolderActions() throws IOException {
        String bottomBar = source("src/main/java/gq/yozakura/ui/click/yozakura/ClickGuiBottomBar.java");

        assertTrue(bottomBar.contains("gui.openProfileScreen();"));
        assertTrue(bottomBar.contains("ConfigBridge.openProfileDirectory();"));
    }

    private static String source(String path) throws IOException {
        return new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.UTF_8)
                .replace("\r\n", "\n");
    }
}
