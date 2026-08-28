package gq.yozakura.module.render;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ClickGuiPanelOnlyContractTest {
    @Test
    public void blockHitKeepsHypixelAtFourAndAppendsHelperAtFive() throws IOException {
        String settings = source("src/main/java/gq/yozakura/module/combat/BlockHitSettings.java");
        String blockHit = source("src/main/java/gq/yozakura/module/combat/BlockHit.java");

        assertTrue(settings.contains(
                "\"Manual\", \"Predict\", \"Auto\", \"Lag\", \"Hypixel\", \"noprehyp\""));
        assertTrue(blockHit.contains("private static final int MODE_HYPIXEL = 4;"));
        assertTrue(blockHit.contains("private static final int MODE_NO_PRE_HYP = 5;"));
    }

    @Test
    public void clickGuiCanOpenPanelOrTimewarpScreen() throws IOException {
        String clickGui = source("src/main/java/gq/yozakura/module/render/ClickGUI.java");
        String dispatcher = source("src/main/java/gq/yozakura/core/ClickGuiKeyDispatcher.java");
        String modern = source("src/main/java/gq/yozakura/core/ModernForgeClient.java");

        String styleEnum = between(clickGui, "public enum GuiStyle", "public enum Palette");
        assertTrue(styleEnum.contains("PANEL"));
        assertFalse(styleEnum.contains("MATERIAL"));
        assertFalse(styleEnum.contains("YOZAKURA"));
        assertFalse(styleEnum.contains("SAKURA"));
        assertTrue(clickGui.contains("mc.displayGuiScreen(new TimewarpClickGui())"));
        assertTrue(clickGui.contains("mc.displayGuiScreen(new YozakuraPanelClickGui())"));
        assertTrue(clickGui.contains("if (style == GuiStyle.PANEL)"));
        assertFalse(clickGui.contains("new MaterialClickGui()"));
        assertFalse(clickGui.contains("new YozakuraClickGui()"));
        assertFalse(clickGui.contains("new SakuraClickGui()"));
        assertTrue(dispatcher.contains("screen instanceof TimewarpClickGui"));
        assertTrue(dispatcher.contains("screen instanceof YozakuraPanelClickGui"));
        assertFalse(dispatcher.contains("screen instanceof YozakuraClickGui"));
        assertFalse(dispatcher.contains("screen instanceof SakuraClickGui"));
        assertFalse(dispatcher.contains("screen instanceof MaterialClickGui"));
        assertFalse(modern.contains("ModernWebClickGuiService"));
    }

    private static String source(String path) throws IOException {
        return new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.UTF_8);
    }

    private static String between(String source, String start, String end) {
        int startIndex = source.indexOf(start);
        int endIndex = source.indexOf(end, startIndex + start.length());
        return source.substring(startIndex, endIndex);
    }
}
