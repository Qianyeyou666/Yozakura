package gq.yozakura.core;

import gq.yozakura.manager.ModuleManager;
import gq.yozakura.module.Module;
import gq.yozakura.module.render.ClickGUI;
import gq.yozakura.ui.click.timewarp.TimewarpClickGui;
import gq.yozakura.ui.click.yozakura.YozakuraPanelClickGui;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.client.gui.GuiScreen;
import org.lwjgl.input.Keyboard;

/** Opens ClickGUI from the vanilla main menu without enabling other module binds there. */
public final class ClickGuiKeyDispatcher {
    private ClickGuiKeyDispatcher() {
    }

    public static boolean handleKeyPress(int key, GuiScreen screen) {
        if (key == Keyboard.KEY_NONE || !(screen instanceof GuiMainMenu)
                || isClickGuiScreen(screen)) {
            return false;
        }
        for (Module module : ModuleManager.getModules()) {
            if (!(module instanceof ClickGUI) || module.getKey() != key) {
                continue;
            }
            module.toggle();
            return true;
        }
        return false;
    }

    private static boolean isClickGuiScreen(GuiScreen screen) {
        return screen instanceof TimewarpClickGui || screen instanceof YozakuraPanelClickGui;
    }
}
