package gq.yozakura.ui.click.web;

import gq.yozakura.util.minecraft.Helper;
import gq.yozakura.manager.ModuleManager;
import gq.yozakura.module.Module;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import org.lwjgl.input.Keyboard;

import java.io.IOException;

/** Transparent Minecraft input owner behind the native WebView2 child view. */
public final class WebView2ClickGuiScreen extends GuiScreen {
    private boolean opened;

    public static void open(Minecraft minecraft) {
        minecraft.displayGuiScreen(new WebView2ClickGuiScreen());
    }

    @Override
    public void initGui() {
        Keyboard.enableRepeatEvents(true);
        openWebView();
    }

    private void openWebView() {
        try {
            opened = WebView2Bridge.show(WebClickGuiService.embeddedUrl());
        } catch (Throwable throwable) {
            opened = false;
        }
        if (!opened) {
            failAndClose();
        }
    }

    @Override
    public void updateScreen() {
        if (opened) {
            WebView2Bridge.syncBounds();
        }
        if (opened && WebView2Bridge.consumeCloseRequest()) {
            mc.displayGuiScreen(null);
        }
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        Module clickGui = ModuleManager.getModule("ClickGUI");
        int closeKey = clickGui == null ? Keyboard.KEY_NONE : clickGui.getKey();
        if (keyCode == Keyboard.KEY_ESCAPE || closeKey != Keyboard.KEY_NONE && keyCode == closeKey) {
            mc.displayGuiScreen(null);
            return;
        }
        super.keyTyped(typedChar, keyCode);
    }

    @Override
    public void onGuiClosed() {
        opened = false;
        WebView2Bridge.hide();
        Keyboard.enableRepeatEvents(false);
        super.onGuiClosed();
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    private void failAndClose() {
        Helper.sendMessage("WebView2 ClickGUI failed: " + WebView2Bridge.lastError());
        mc.displayGuiScreen(null);
    }

}
