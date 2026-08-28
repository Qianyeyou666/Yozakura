package gq.yozakura.ui.click.timewarp;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class TimewarpClickGuiContractTest {
    @Test
    public void clickGuiEntryUsesIndependentTimewarpScreen() throws IOException {
        String clickGui = source("src/main/java/gq/yozakura/module/render/ClickGUI.java");
        String screen = source("src/main/java/gq/yozakura/ui/click/timewarp/TimewarpClickGui.java");

        assertTrue(clickGui.contains("new TimewarpClickGui()"));
        assertTrue(clickGui.contains("new YozakuraPanelClickGui()"));
        assertTrue(clickGui.contains("GuiStyle.PANEL"));
        assertTrue(clickGui.contains("GuiStyle.NEW"));
        assertFalse(clickGui.contains("GuiStyle.TIMEWARP"));
        assertTrue(screen.contains("extends GuiScreen"));
        assertFalse(screen.contains("extends YozakuraPanelClickGui"));
    }

    @Test
    public void visualLayersHaveDedicatedAnimationKeys() throws IOException {
        String screen = source("src/main/java/gq/yozakura/ui/click/timewarp/TimewarpClickGui.java");
        String values = source("src/main/java/gq/yozakura/ui/click/timewarp/TimewarpClickGuiValueRenderer.java");

        assertTrue(screen.contains("nav-hover:"));
        assertTrue(screen.contains("nav-selection-y"));
        assertTrue(screen.contains("module-entry:"));
        assertTrue(screen.contains("module-hover:"));
        assertTrue(screen.contains("module-toggle:"));
        assertTrue(screen.contains("TimewarpClickGuiPageTransition"));
        assertTrue(values.contains("value-enter:"));
        assertTrue(values.contains("value-hover:"));
        assertTrue(values.contains("value-toggle:"));
        assertTrue(values.contains("value-toggle-pulse:"));
        assertTrue(values.contains("value-slider:"));
        assertTrue(values.contains("value-slider-focus:"));
        assertTrue(values.contains("value-dropdown:"));
        assertTrue(values.contains("value-popup:"));
        assertTrue(values.contains("value-option-hover:"));
    }

    @Test
    public void screenConnectsPaletteConfigsWindowInteractionAndCursor() throws IOException {
        String screen = source("src/main/java/gq/yozakura/ui/click/timewarp/TimewarpClickGui.java");
        String values = source("src/main/java/gq/yozakura/ui/click/timewarp/TimewarpClickGuiValueRenderer.java");

        assertTrue(screen.contains("TimewarpClickGuiTheme.current()"));
        assertTrue(screen.contains("PanelPaletteColorControl"));
        assertTrue(screen.contains("PanelPaletteColorPicker"));
        assertTrue(screen.contains("drawPaletteColorRow("));
        assertTrue(screen.contains("handlePaletteColorClick("));
        assertTrue(screen.contains("paletteColorPicker.mouseDragged("));
        assertTrue(screen.contains("paletteColorPicker.mouseReleased();"));
        assertTrue(screen.contains("ConfigBridge.listProfiles()"));
        assertTrue(screen.contains("ConfigBridge.saveProfile("));
        assertTrue(screen.contains("ConfigBridge.loadProfile("));
        assertTrue(screen.contains("ConfigBridge.openProfileDirectory()"));
        assertTrue(screen.contains("cursor.install()"));
        assertTrue(screen.contains("cursor.restore()"));
        assertTrue(screen.contains("draggingWindow"));
        assertTrue(screen.contains("resizingWindow"));
        assertTrue(values.contains("TimewarpClickGuiTheme"));
    }

    @Test
    public void detailHeaderExposesEditableKeyboardAndMouseKeybinds() throws IOException {
        String screen = source("src/main/java/gq/yozakura/ui/click/timewarp/TimewarpClickGui.java");
        String geometry = source("src/main/java/gq/yozakura/ui/click/timewarp/TimewarpClickGuiGeometry.java");

        assertTrue(geometry.contains("public static Rect detailKeybindButton(Layout layout)"));
        assertTrue(screen.contains("drawDetailKeybind(module, mouseX, mouseY, theme, pageVisibility);"));
        assertTrue(screen.contains("TimewarpClickGuiGeometry.detailKeybindButton(layout)"));
        assertTrue(screen.contains("listeningKeybind ? \"...\" : compactKeyName(module.getKey())"));
        assertTrue(screen.contains("listeningKeybind = true;"));
        assertTrue(screen.contains("keybind.contains(mouseX, mouseY) && mouseButton == 0"));
        assertTrue(screen.contains("PanelModuleKeybind.encodeMouseButton(mouseButton)"));
        assertTrue(screen.contains("keyCode == Keyboard.KEY_BACK || keyCode == Keyboard.KEY_DELETE"));
        assertTrue(screen.contains("pages.detailModule().setKey(keyCode);"));
    }

    @Test
    public void screenHasNoBottomLeftLogoOrDeveloperBadge() throws IOException {
        String screen = source("src/main/java/gq/yozakura/ui/click/timewarp/TimewarpClickGui.java").toLowerCase();

        assertFalse(screen.contains("drawlogo"));
        assertFalse(screen.contains("developer badge"));
        assertFalse(screen.contains("hourglass"));
    }

    @Test
    public void referenceSkinUsesCompactRowsAndTheLivePalette() throws IOException {
        String screen = source("src/main/java/gq/yozakura/ui/click/timewarp/TimewarpClickGui.java");
        String values = source("src/main/java/gq/yozakura/ui/click/timewarp/TimewarpClickGuiValueRenderer.java");
        String theme = source("src/main/java/gq/yozakura/ui/click/timewarp/TimewarpClickGuiTheme.java");

        assertTrue(screen.contains("module-row-divider"));
        assertFalse(screen.contains("row.y() - lift"));
        assertTrue(values.contains("public static final float ROW_HEIGHT = 34.0f"));
        assertTrue(screen.contains("drawString(\"yozakura\""));
        assertFalse(screen.contains("drawString(\"timewarp\""));
        assertTrue(theme.contains("palette.getCanvas()"));
        assertTrue(theme.contains("palette.getSurface()"));
        assertTrue(theme.contains("palette.getSurfaceRaised()"));
        assertTrue(theme.contains("palette.getSurfaceOverlay()"));
        assertTrue(theme.contains("palette.getTextPrimary()"));
        assertTrue(theme.contains("palette.getTextSecondary()"));
        assertTrue(theme.contains("palette.getTextDisabled()"));
        assertTrue(theme.contains("palette.getBorderSubtle()"));
        assertTrue(theme.contains("palette.getAccentPrimary()"));
        assertFalse(theme.contains("return 0xFF"));
    }

    @Test
    public void configsPageConnectsTheParameterHall() throws IOException {
        String screen = source("src/main/java/gq/yozakura/ui/click/timewarp/TimewarpClickGui.java");

        assertTrue(screen.contains("ClubService.getInstance()"));
        assertTrue(screen.contains("clubService.refreshHallConfigs()"));
        assertTrue(screen.contains("clubService.uploadConfigToHall("));
        assertTrue(screen.contains("clubService.downloadHallConfig("));
        assertTrue(screen.contains("clubService.useHallConfig("));
        assertTrue(screen.contains("clubService.deleteHallConfig("));
        assertTrue(screen.contains("clubService.consumePendingDownload()"));
        assertTrue(screen.contains("clubService.consumePendingUse()"));
        assertTrue(screen.contains("PanelCloudConfigSearchModel.filter("));
        assertTrue(screen.contains("language(\"Parameter Hall\", \"参数大厅\")"));
    }

    private static String source(String path) throws IOException {
        return new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.UTF_8)
                .replace("\r\n", "\n");
    }
}
