package gq.yozakura.module;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Locks the Epsilon-style module bind-mode and visibility data model:
 * a {@code BindMode} enum (TOGGLE / HOLD) plus a {@code hidden} flag,
 * both persisted through the config pipeline.
 *
 * <p>Behavioral setters are verified through source contracts because
 * instantiating {@link Module} would trigger the Minecraft bootstrap.
 */
public class ModuleBindModeContractTest {
    @Test
    public void bindModeEnumExposesToggleAndHoldOnly() {
        Module.BindMode[] modes = Module.BindMode.values();
        assertEquals(2, modes.length);
        assertEquals("TOGGLE", modes[0].name());
        assertEquals("HOLD", modes[1].name());
    }

    @Test
    public void moduleDefaultsToToggleAndVisible() throws IOException {
        String source = moduleSource();
        assertTrue(source.contains("private BindMode bindMode = BindMode.TOGGLE;"));
        assertTrue(source.contains("private boolean hidden;"));
    }

    @Test
    public void settersMarkConfigDirtyOnlyOnRealChanges() throws IOException {
        String source = moduleSource();
        String bindSetter = between(source, "public void setBindMode(BindMode bindMode)");
        assertTrue(bindSetter.contains("if (this.bindMode == bindMode)"));
        assertTrue(bindSetter.contains("YozakuraClientState.markConfigDirty();"));

        String hiddenSetter = between(source, "public void setHidden(boolean hidden)");
        assertTrue(hiddenSetter.contains("if (this.hidden == hidden)"));
        assertTrue(hiddenSetter.contains("YozakuraClientState.markConfigDirty();"));
    }

    @Test
    public void configPipelinePersistsBindModeAndHidden() throws IOException {
        String source = new String(Files.readAllBytes(Paths.get(
                "src/main/java/gq/yozakura/manager/FileManager.java")),
                StandardCharsets.UTF_8).replace("\r\n", "\n");
        // Save side
        assertTrue(source.contains("moduleJson.addProperty(\"bindMode\", module.getBindMode().name());"));
        assertTrue(source.contains("moduleJson.addProperty(\"hidden\", module.isHidden());"));
        // Load side
        assertTrue(source.contains("moduleJson.has(\"bindMode\")"));
        assertTrue(source.contains("moduleJson.has(\"hidden\")"));
    }

    @Test
    public void mouseButtonsUseASeparateNegativeKeySpace() {
        assertEquals(-2, gq.yozakura.ui.click.yozakura.PanelModuleKeybind.encodeMouseButton(0));
        assertEquals(-4, gq.yozakura.ui.click.yozakura.PanelModuleKeybind.encodeMouseButton(2));
        assertTrue(gq.yozakura.ui.click.yozakura.PanelModuleKeybind.isMouseButton(-2));
        assertEquals(2, gq.yozakura.ui.click.yozakura.PanelModuleKeybind.decodeMouseButton(-4));
        assertEquals("M3", gq.yozakura.ui.click.yozakura.PanelModuleKeybind.compactName(-4));
    }

    @Test
    public void forgeAndStandaloneDispatchMouseBindings() throws IOException {
        String forge = source("src/main/java/gq/yozakura/core/Client.java");
        String standalone = source("src/main/java/gq/yozakura/core/StandaloneClient.java");

        assertTrue(forge.contains("public void mouseInput(InputEvent.MouseInputEvent event)"));
        assertTrue(forge.contains("PanelModuleKeybind.encodeMouseButton(button)"));
        assertTrue(standalone.contains("handleMouseButtons();"));
        assertTrue(standalone.contains("toggleModulesBoundTo(keyBind);"));
        assertTrue(standalone.contains("releaseHoldModulesBoundTo(keyBind);"));
    }

    @Test
    public void clickGuiBindCanOpenFromTheVanillaMainMenuOnly() throws IOException {
        String forge = source("src/main/java/gq/yozakura/core/Client.java");
        String standalone = source("src/main/java/gq/yozakura/core/StandaloneClient.java");

        assertTrue(forge.contains("ClickGuiKeyDispatcher.handleKeyPress(key, mc.currentScreen)"));
        assertTrue(forge.contains("private void pollMainMenuClickGuiBind()"));
        assertTrue(forge.contains("Keyboard.isKeyDown(key)"));
        assertTrue(forge.contains("mainMenuClickGuiKeyDown = true;"));
        assertTrue(forge.contains("if (down && !mainMenuClickGuiKeyDown)"));
        assertTrue(standalone.contains("ClickGuiKeyDispatcher.handleKeyPress(key, mc.currentScreen)"));
        assertTrue(forge.contains("if (pressed && mc.currentScreen != null)"));
        assertTrue(standalone.contains("boolean mainMenu = mc.currentScreen instanceof GuiMainMenu;"));
        assertTrue(standalone.contains("if ((mc.currentScreen != null && !mainMenu) || !Keyboard.isCreated())"));
        assertTrue(standalone.contains("if (!mainMenu && !clickGuiOpened)"));

        String dispatcher = source(
                "src/main/java/gq/yozakura/core/ClickGuiKeyDispatcher.java");
        assertTrue(dispatcher.contains("screen instanceof GuiMainMenu"));
        assertTrue(dispatcher.contains("module instanceof ClickGUI"));
        assertTrue(dispatcher.contains("module.getKey() != key"));
        assertTrue(dispatcher.contains("module.toggle();"));
        assertTrue(dispatcher.contains("screen instanceof TimewarpClickGui"));
    }

    private static String moduleSource() throws IOException {
        return source("src/main/java/gq/yozakura/module/Module.java");
    }

    private static String source(String path) throws IOException {
        return new String(Files.readAllBytes(Paths.get(path)),
                StandardCharsets.UTF_8).replace("\r\n", "\n");
    }

    private static String between(String source, String methodSignature) {
        int start = source.indexOf(methodSignature);
        assertTrue("missing " + methodSignature, start >= 0);
        int end = source.indexOf("\n    }", start);
        assertTrue(end > start);
        return source.substring(start, end);
    }
}
