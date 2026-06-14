package gq.yozakura.ui.click.yozakura;

import gq.yozakura.engine.font.FontLoaders;
import gq.yozakura.engine.render.ui.RenderServices;
import net.minecraft.client.gui.ScaledResolution;

import java.awt.Color;

final class ClickGuiOverlayRenderer {
    private final YozakuraClickGui gui;

    ClickGuiOverlayRenderer(YozakuraClickGui gui) {
        this.gui = gui;
    }

    void drawKeybindOverlay(ScaledResolution sr) {
        if (gui.bindingModule == null) {
            return;
        }
        RenderServices.shapes().rect(0.0f, 0.0f, sr.getScaledWidth(), sr.getScaledHeight(),
                gui.withAlpha(new Color(0, 0, 0).getRGB(), 92.0f));
        float boxW = 210.0f;
        float boxH = 84.0f;
        float x = sr.getScaledWidth() / 2.0f - boxW / 2.0f;
        float y = sr.getScaledHeight() / 2.0f - boxH / 2.0f;

        gui.drawThemedGlass(x, y, x + boxW, y + boxH, 8.0f, 1.0f,
                gui.withAlpha(gui.guiColors().glassFill, 218.0f),
                gui.withAlpha(gui.guiColors().accent, 130.0f));
        gui.drawCenteredText("KEYBIND", x, y + 14.0f, x + boxW, y + 25.0f, gui.guiColors().text);
        gui.drawCenteredText(gui.bindingModule.getName(), x, y + 34.0f, x + boxW, y + 45.0f,
                gui.withAlpha(gui.guiColors().text, 220.0f));
        gui.drawCenteredText("Current: " + gui.getKeyName(gui.bindingModule), x, y + 49.0f,
                x + boxW, y + 60.0f, gui.withAlpha(gui.guiColors().muted, 215.0f));
        gui.drawCenteredText("Press key, DEL clears, ESC cancels", x, y + 66.0f,
                x + boxW, y + 77.0f, gui.withAlpha(gui.guiColors().muted, 185.0f));
    }

    void drawToast(ScaledResolution sr) {
        if (gui.toastText == null) {
            return;
        }
        long elapsed = System.currentTimeMillis() - gui.toastStarted;
        if (elapsed > 2500L) {
            gui.toastText = null;
            return;
        }
        float alpha = elapsed < 1800L ? 1.0f : 1.0f - (elapsed - 1800L) / 700.0f;
        float w = FontLoaders.F14.getStringWidth(gui.toastText) + 20.0f;
        float x = sr.getScaledWidth() / 2.0f - w / 2.0f;
        float y = gui.navY + YozakuraClickGui.NAV_H + YozakuraClickGui.SEARCH_H + 12.0f;
        gui.drawThemedGlass(x, y, x + w, y + 17.0f, 6.0f, 0.8f,
                gui.withAlpha(gui.guiColors().glassFillSoft, 194.0f * alpha),
                gui.withAlpha(gui.guiColors().accent, 75.0f * alpha));
        gui.drawCenteredText(gui.toastText, x, y + 4.0f, x + w, y + 14.0f,
                gui.withAlpha(gui.guiColors().text, 230.0f * alpha));
    }
}
