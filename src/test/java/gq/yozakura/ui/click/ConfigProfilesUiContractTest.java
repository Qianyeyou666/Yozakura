package gq.yozakura.ui.click;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import gq.yozakura.ui.click.yozakura.PanelClickGuiLayout;
import gq.yozakura.ui.click.yozakura.PanelConfigManagerGeometry;

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
    public void yozakuraSidebarProvidesProfileEntry() throws IOException {
        String sidebar = source("src/main/java/gq/yozakura/ui/click/yozakura/ClickGuiSidebar.java");
        String gui = source("src/main/java/gq/yozakura/ui/click/yozakura/YozakuraClickGui.java");

        assertTrue(sidebar.contains("onProfileClicked"));
        assertTrue(gui.contains("openProfileScreen()"));
    }

    @Test
    public void panelExtractsCfgManagerIntoANativeProfilesPage() throws IOException {
        String panel = source("src/main/java/gq/yozakura/ui/click/yozakura/YozakuraPanelClickGui.java");

        assertTrue(panel.contains("private boolean configManagerMode"));
        assertTrue(panel.contains("drawConfigManagerPage("));
        assertTrue(panel.contains("ConfigBridge.listProfiles()"));
        assertTrue(panel.contains("ConfigBridge.saveProfile("));
        assertTrue(panel.contains("ConfigBridge.loadProfile("));
        assertTrue(panel.contains("panelPositionInitialized = false;"));
        assertTrue(panel.contains("rebuildLayout();"));
        assertTrue(panel.contains("isConfigManagerModule(module)"));
        assertTrue(panel.contains("!isConfigManagerModule(module)"));
        assertTrue(panel.contains("EpsilonPanelGeometry.railConfigManagerItem(rail)"));
        assertTrue(panel.contains("openConfigManager();"));
        assertFalse(panel.contains("drawConfigManagerEntry(bounds"));
        assertFalse(panel.contains("configManagerEntryBounds(layout.modules())"));
    }

    @Test
    public void panelProfileNameTextIsClippedInsideItsField() throws IOException {
        String panel = source("src/main/java/gq/yozakura/ui/click/yozakura/YozakuraPanelClickGui.java");
        String page = method(panel, "    private void drawConfigManagerPage(",
                "    private void drawConfigButton(");

        assertTrue(page.contains("GLStateManager.pushScissor(nameField.x()"));
        assertTrue(page.contains("GLStateManager.popScissor();"));
    }

    @Test
    public void panelProfileActionsStayInsideTheMinimumWidthContentColumn() throws Exception {
        PanelClickGuiLayout.Layout layout = PanelClickGuiLayout.compute(
                1280.0f, 720.0f, 120.0f,
                PanelClickGuiLayout.PANEL_MIN_WIDTH,
                PanelClickGuiLayout.PANEL_MIN_HEIGHT);
        PanelClickGuiLayout.Rect bounds = layout.detail();
        PanelClickGuiLayout.Rect[] controls = {
                PanelConfigManagerGeometry.nameField(bounds),
                PanelConfigManagerGeometry.saveButton(bounds),
                PanelConfigManagerGeometry.loadButton(bounds),
                PanelConfigManagerGeometry.refreshButton(bounds),
                PanelConfigManagerGeometry.folderButton(bounds)
        };
        float previousRight = bounds.x() + 14.0f;
        for (PanelClickGuiLayout.Rect control : controls) {
            assertTrue("config action controls overlap",
                    control.x() >= previousRight);
            assertTrue("config action control exceeds the content column",
                    control.right() <= bounds.right() - 14.0f + 0.001f);
            previousRight = control.right();
        }
    }

    private static String source(String path) throws IOException {
        return new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.UTF_8)
                .replace("\r\n", "\n");
    }

    private static String method(String source, String start, String end) {
        int startIndex = source.indexOf(start);
        int endIndex = source.indexOf(end, startIndex);
        return source.substring(startIndex, endIndex);
    }
}
