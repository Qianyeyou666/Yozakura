package gq.vapulite.Vapu.VapeClickGui;

import gq.vapulite.Vapu.utils.RenderUtil;
import gq.vapulite.font.FontLoaders;
import net.minecraft.client.gui.ScaledResolution;

import java.awt.Color;

final class ClickGuiBottomBar {
    private final VapeClickGui gui;

    ClickGuiBottomBar(VapeClickGui gui) {
        this.gui = gui;
    }

    void render(ScaledResolution sr) {
        float y = sr.getScaledHeight() - 34.0f;
        float profileW = 132.0f;
        gui.drawThemedGlass(gui.contentX, y, gui.contentX + profileW, y + 25.0f, 8.0f, 1.0f,
                gui.withAlpha(gui.guiColors().glassFillSoft, 190.0f * gui.guiAlpha),
                gui.withAlpha(gui.guiColors().glassBorder, 48.0f * gui.guiAlpha));
        gui.drawSoftRect(gui.contentX + 10.0f, y + 5.0f, gui.contentX + 26.0f, y + 21.0f, 5.0f,
                gui.withAlpha(new Color(81, 87, 103, 220).getRGB(), 220.0f * gui.guiAlpha));
        gui.drawCenteredIcon(FontLoaders.ICON_USER, FontLoaders.I14, gui.contentX + 18.0f, y + 13.0f,
                gui.withAlpha(gui.guiColors().text, 235.0f * gui.guiAlpha));
        gui.drawFont("Default", gui.contentX + 34.0f, y + 5.0f,
                gui.withAlpha(gui.guiColors().text, 235.0f * gui.guiAlpha));
        gui.drawFont("Profile 1", gui.contentX + 34.0f, y + 17.0f,
                gui.withAlpha(gui.guiColors().muted, 190.0f * gui.guiAlpha));
        gui.drawCenteredIcon(FontLoaders.ICON_DROPDOWN_ARROW, FontLoaders.I14, gui.contentX + profileW - 16.0f, y + 13.0f,
                gui.withAlpha(gui.guiColors().muted, 182.0f * gui.guiAlpha));

        float hintW = 96.0f;
        float hintX = sr.getScaledWidth() - hintW - 16.0f;
        gui.drawThemedGlass(hintX, y, hintX + hintW, y + 25.0f, 8.0f, 1.0f,
                gui.withAlpha(gui.guiColors().glassFillSoft, 190.0f * gui.guiAlpha),
                gui.withAlpha(gui.guiColors().glassBorder, 48.0f * gui.guiAlpha));
        gui.drawCenteredText("Right Shift", hintX + 8.0f, y + 7.0f, hintX + 64.0f, y + 19.0f,
                gui.withAlpha(gui.guiColors().text, 220.0f * gui.guiAlpha));
        gui.drawSoftRect(hintX + 66.0f, y + 5.0f, hintX + hintW - 7.0f, y + 20.0f, 6.0f,
                gui.withAlpha(new Color(69, 62, 154, 232).getRGB(), 232.0f * gui.guiAlpha));
        gui.drawCenteredText("GUI", hintX + 66.0f, y + 8.0f, hintX + hintW - 7.0f, y + 19.0f,
                gui.withAlpha(gui.guiColors().text, 245.0f * gui.guiAlpha));
    }
}
