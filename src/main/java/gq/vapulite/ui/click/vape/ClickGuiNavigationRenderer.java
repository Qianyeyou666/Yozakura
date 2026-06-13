package gq.vapulite.ui.click.vape;

import gq.vapulite.engine.font.CFontRenderer;
import gq.vapulite.engine.font.FontLoaders;
import gq.vapulite.engine.render.ui.RenderServices;
import gq.vapulite.util.animation.AnimUtil;

final class ClickGuiNavigationRenderer {
    private final VapeClickGui gui;

    ClickGuiNavigationRenderer(VapeClickGui gui) {
        this.gui = gui;
    }

    void drawTopBarBackground(float introY) {
        float x = gui.contentX;
        float y = gui.navY + introY;
        float right = gui.navX + gui.navW;
        RenderServices.shapes().shadow(x, y, right, y + VapeClickGui.NAV_H, 9.0f,
                gui.withAlpha(gui.shadowColor(210), 70.0f * gui.guiAlpha), 7, 5.0f);
        gui.drawPanelGlass(x, y, right, y + VapeClickGui.NAV_H, 9.0f, 1.0f,
                gui.withAlpha(gui.guiColors().glassFillSoft, gui.getAlpha(gui.guiColors().glassFillSoft) * gui.guiAlpha),
                gui.withAlpha(gui.guiColors().glassBorder, gui.getAlpha(gui.guiColors().glassBorder) * gui.guiAlpha));
    }

    void drawNavigation(int mouseX, int mouseY, float introY) {
        float y = gui.navY + introY;
        RenderServices.shapes().shadow(gui.navX, y, gui.navX + gui.navW, y + VapeClickGui.NAV_H, 9.0f,
                gui.withAlpha(gui.shadowColor(210), 70.0f * gui.guiAlpha), 7, 5.0f);
        gui.drawPanelGlass(gui.navX, y, gui.navX + gui.navW, y + VapeClickGui.NAV_H, 9.0f, 1.0f,
                gui.withAlpha(gui.guiColors().glassFillSoft, gui.getAlpha(gui.guiColors().glassFillSoft) * gui.guiAlpha),
                gui.withAlpha(gui.guiColors().glassBorder, gui.getAlpha(gui.guiColors().glassBorder) * gui.guiAlpha));

        float tabW = gui.navW / GuiTab.values().length;
        float targetX = gui.navX + VapeClickGui.currentTab.ordinal() * tabW + 2.0f;
        gui.navIndicatorX = gui.animate(gui.navIndicatorX, targetX, 0.18f);

        RenderServices.shapes().shadow(gui.navIndicatorX, y + 4.0f,
                gui.navIndicatorX + tabW - 4.0f, y + VapeClickGui.NAV_H - 4.0f, 7.0f,
                gui.withAlpha(gui.guiColors().accent, 85.0f * gui.guiAlpha), 5, 4.0f);
        RenderServices.shapes().roundedBorder(gui.navIndicatorX, y + 4.0f,
                gui.navIndicatorX + tabW - 4.0f, y + VapeClickGui.NAV_H - 4.0f, 7.0f, 0.8f,
                gui.withAlpha(gui.guiColors().detailSelectedFill, 232.0f * gui.guiAlpha),
                gui.withAlpha(gui.guiColors().detailSelectedBorder, 80.0f * gui.guiAlpha));

        for (int i = 0; i < GuiTab.values().length; i++) {
            drawTab(GuiTab.values()[i], i, tabW, y, mouseX, mouseY);
        }
    }

    private void drawTab(GuiTab tab, int index, float tabW, float y, int mouseX, int mouseY) {
        float x = gui.navX + index * tabW;
        boolean hovered = VapeClickGui.isHovered(x, y, x + tabW, y + VapeClickGui.NAV_H, mouseX, mouseY);
        float hover = gui.animateTabMap(tab, hovered && tab != VapeClickGui.currentTab && !gui.closing ? 1.0f : 0.0f, 0.18f);
        if (hover > 0.01f) {
            gui.drawSoftRect(x + 3.0f, y + 4.0f, x + tabW - 3.0f, y + VapeClickGui.NAV_H - 4.0f, 7.0f,
                    gui.withAlpha(gui.guiColors().navDefaultHover, 190.0f * hover * gui.guiAlpha));
        }

        int textColor = tab == VapeClickGui.currentTab ? gui.guiColors().text : gui.guiColors().muted;
        int color = gui.withAlpha(textColor, 245.0f * gui.guiAlpha);
        CFontRenderer navIconFont = FontLoaders.I16;
        String title = gui.trim(tab.title, FontLoaders.F14, Math.max(18.0f, tabW - 42.0f));
        float iconW = navIconFont.getStringWidth(tab.icon);
        float titleW = FontLoaders.F14.getStringWidth(title);
        float gap = 10.0f;
        float groupX = x + (tabW - iconW - gap - titleW) / 2.0f;
        float titleOffsetX = -1.5f;
        float titleOffsetY = 1.5f;
        // 选中标签页文字弹跳
        boolean isSelected = tab == VapeClickGui.currentTab;
        float bounceY = isSelected ? AnimUtil.bounceY(gui.navBounce.get(index)) : 0f;
        float textY = y + 10.0f + titleOffsetY - hover * 0.4f + bounceY;
        float iconY = y + VapeClickGui.NAV_H / 2.0f - hover * 0.4f + bounceY;
        gui.drawCenteredIcon(tab.icon, navIconFont, groupX + iconW / 2.0f, iconY, color);
        gui.drawFont(title, groupX + iconW + gap + titleOffsetX, textY + 2.0f, color);
    }
}
