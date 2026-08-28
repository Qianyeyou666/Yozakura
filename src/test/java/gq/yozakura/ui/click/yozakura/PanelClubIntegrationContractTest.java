package gq.yozakura.ui.click.yozakura;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PanelClubIntegrationContractTest {
    @Test
    public void configManagerProvidesLocalAndCloudTabs() throws IOException {
        String panel = source("src/main/java/gq/yozakura/ui/click/yozakura/YozakuraPanelClickGui.java");

        assertTrue(panel.contains("PanelClubGeometry.localTab(bounds)"));
        assertTrue(panel.contains("PanelClubGeometry.cloudTab(bounds)"));
        assertTrue(panel.contains("drawCloudConfigPage("));
        assertTrue(panel.contains("ClubService.getInstance()"));
    }

    @Test
    public void configHallActionsKeepUploadDownloadAndUseSeparate() throws IOException {
        String panel = source("src/main/java/gq/yozakura/ui/click/yozakura/YozakuraPanelClickGui.java");
        String draw = method(panel, "    private void drawCloudConfigPage(",
                "    private void drawCloudConfigList(");

        assertTrue(panel.contains("clubService.refreshHallConfigs()"));
        assertTrue(panel.contains("clubService.uploadConfigToHall("));
        assertTrue(panel.contains("clubService.downloadHallConfig("));
        assertTrue(panel.contains("clubService.useHallConfig("));
        assertTrue(panel.contains("clubService.deleteHallConfig("));
        assertTrue(panel.contains("PanelClubGeometry.deleteButton(bounds)"));
        assertTrue(panel.contains("ownsSelectedCloudConfig(state)"));
        assertTrue(panel.contains("ConfigBridge.readProfileSnapshot(selected)"));
        assertTrue(panel.contains("ConfigBridge.saveProfileSnapshot("));
        assertTrue(panel.contains("ConfigBridge.importSnapshot("));
        assertFalse(draw.contains("clubService.uploadConfigToHall("));
        assertFalse(draw.contains("clubService.downloadHallConfig("));
        assertFalse(draw.contains("clubService.useHallConfig("));
    }

    @Test
    public void configHallSearchUsesStableIdsAndOwnUploadsRemainDeletable() throws IOException {
        String panel = source("src/main/java/gq/yozakura/ui/click/yozakura/YozakuraPanelClickGui.java");

        assertTrue(panel.contains("PanelClubGeometry.searchField(bounds)"));
        assertTrue(panel.contains("PanelCloudConfigSearchModel.filter("));
        assertTrue(panel.contains("selectedCloudConfigId"));
        assertTrue(panel.contains("PanelCloudConfigSearchModel.findById("));
        assertFalse(panel.contains("private int selectedCloudConfig = -1;"));
        assertTrue(panel.contains("state.ownsConfig(selected.getId())"));
        assertFalse(panel.contains("state.getUsername().equalsIgnoreCase(selected.getOwner())"));
        assertTrue(panel.contains("clubService.deleteHallConfig(selected)"));
    }

    @Test
    public void configHallDoesNotReplaceTheLocalProfileManager() throws IOException {
        String panel = source("src/main/java/gq/yozakura/ui/click/yozakura/YozakuraPanelClickGui.java");

        assertTrue(panel.contains("languageText(\"Local\", \"本地配置\")"));
        assertTrue(panel.contains("languageText(\"Config Hall\", \"配置大厅\")"));
        assertTrue(panel.contains("ConfigBridge.saveProfile(name)"));
        assertTrue(panel.contains("ConfigBridge.loadProfile(selected)"));
    }

    @Test
    public void panelClosePersistsCurrentConfigAndStableSessionState() throws IOException {
        String panel = source("src/main/java/gq/yozakura/ui/click/yozakura/YozakuraPanelClickGui.java");
        String close = method(panel, "    public void onGuiClosed() {",
                "    @Override\n    public boolean doesGuiPauseGame()");

        assertTrue(panel.contains("PanelClickGuiSessionState"));
        assertTrue(close.contains("panelSessionState.capture("));
        assertTrue(close.contains("ConfigBridge.saveModulesQuietly();"));
        assertFalse(close.contains("selectedModule = null;"));
    }

    @Test
    public void cloudPageDoesNotProvideASecondLoginFlow() throws IOException {
        String panel = source("src/main/java/gq/yozakura/ui/click/yozakura/YozakuraPanelClickGui.java");

        assertFalse(panel.contains("clubPassword"));
        assertFalse(panel.contains("clubUsername"));
        assertFalse(panel.contains("ClubField"));
        assertFalse(panel.contains("drawClubTextField("));
        assertFalse(panel.contains("submitClubAuthentication("));
        assertFalse(panel.contains("clubService.login("));
        assertFalse(panel.contains("clubService.register("));
        assertFalse(panel.contains("PanelClubGeometry.loginButton("));
        assertFalse(panel.contains("PanelClubGeometry.registerButton("));
        assertFalse(panel.contains("PanelClubGeometry.logoutButton("));
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
