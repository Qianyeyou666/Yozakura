package gq.vapulite.Vapu.VapeClickGui;

import gq.vapulite.Manager.ModuleManager;
import gq.vapulite.Vapu.modules.Module;
import gq.vapulite.Vapu.utils.RenderUtil;
import gq.vapulite.font.FontLoaders;
import gq.vapulite.ui.UiPanel;
import org.lwjgl.input.Mouse;

import java.awt.Color;
import java.util.List;

final class ClickGuiModuleList {
    private final VapeClickGui gui;
    private final UiPanel panel;

    ClickGuiModuleList(VapeClickGui gui) {
        this.gui = gui;
        this.panel = new UiPanel().setTheme(gui.uiTheme);
    }

    void render(int mouseX, int mouseY, float introY) {
        gui.listScroll = gui.animate(gui.listScroll, gui.targetListScroll, 0.12f);
        float listHeight = gui.getListHeight();
        float panelY = gui.contentY + introY;
        panel.setBounds(gui.contentX, panelY, VapeClickGui.CARD_W, gui.panelH)
                .radius(VapeClickGui.PANEL_RADIUS)
                .fill(VapeClickGui.GLASS_FILL)
                .border(VapeClickGui.GLASS_BORDER)
                .shadow(new Color(0, 0, 0, 220).getRGB(), 88.0f, 9, 6.0f)
                .setAlpha(gui.openProgress)
                .render(mouseX, mouseY, 0.0f);

        float drawContentY = gui.getModuleListY() + introY;
        gui.beginScissor(gui.contentX + 8.0f, drawContentY, VapeClickGui.CARD_W - 16.0f, listHeight);
        try {
            float rowY = drawContentY + gui.listScroll;
            List<Module> modules = gui.getVisibleModules();
            for (int i = 0; i < modules.size(); i++) {
                Module module = modules.get(i);
                if (rowY + VapeClickGui.CARD_H >= drawContentY - 2 && rowY <= drawContentY + listHeight + 2) {
                    float stagger = Math.min(1.0f, Math.max(0.0f, gui.contentFade - i * 0.035f));
                    float eased = gui.easeSmooth(gui.easeOut(stagger));
                    drawModuleCard(module, gui.contentX + 10.0f, rowY + (1.0f - eased) * 8.0f,
                            VapeClickGui.CARD_H, mouseX, mouseY, eased);
                }
                rowY += VapeClickGui.CARD_H + 6.0f;
            }
        } finally {
            gui.endScissor();
        }
        drawModuleCount(panelY);
        drawScrollbar(drawContentY, listHeight);
    }

    boolean mouseClicked(int mouseX, int mouseY, int mouseButton) {
        if (!VapeClickGui.isHovered(gui.contentX, gui.getModuleListY(), gui.contentX + VapeClickGui.CARD_W,
                gui.getModuleListY() + gui.getListHeight(), mouseX, mouseY)) {
            return false;
        }
        float rowY = gui.getModuleListY() + gui.listScroll;
        List<Module> modules = gui.getVisibleModules();
        for (int i = 0; i < modules.size(); i++) {
            Module module = modules.get(i);
            float x = gui.contentX + 10.0f;
            if (VapeClickGui.isHovered(x, rowY, x + getRowWidth(),
                    rowY + VapeClickGui.CARD_H, mouseX, mouseY)) {
                if (mouseButton == 2) {
                    gui.startBinding(module);
                    return true;
                }
                if (mouseButton == 0 && isStarHit(x, rowY, mouseX, mouseY)) {
                    if (gui.favoriteModules.contains(module)) {
                        gui.favoriteModules.remove(module);
                    } else {
                        gui.favoriteModules.add(module);
                    }
                    gui.clickProgress.put(module, 1.0f);
                    return true;
                }
                if (mouseButton == 0 && gui.isSwitchHit(gui.getModuleSwitchX(x), gui.getModuleSwitchY(rowY), mouseX, mouseY)) {
                    module.setState(!module.getState());
                    gui.clickProgress.put(module, 1.0f);
                    return true;
                }
                if (mouseButton == 1) {
                    module.setState(!module.getState());
                    gui.clickProgress.put(module, 1.0f);
                    return true;
                }
                if (mouseButton == 0) {
                    VapeClickGui.selectModule(module);
                    gui.clickProgress.put(module, 1.0f);
                }
                gui.targetListScroll = gui.clamp(gui.targetListScroll,
                        -Math.max(0.0f, gui.getContentHeight() - gui.getListHeight()), 0.0f);
                return true;
            }
            rowY += VapeClickGui.CARD_H + 6.0f;
        }
        return false;
    }

    boolean updateScroll(int mouseX, int mouseY, int wheel) {
        if (wheel == 0 || !VapeClickGui.isHovered(gui.contentX, gui.getModuleListY(),
                gui.contentX + VapeClickGui.CARD_W, gui.getModuleListY() + gui.getListHeight(), mouseX, mouseY)) {
            return false;
        }
        gui.targetListScroll += wheel > 0 ? 34.0f : -34.0f;
        gui.targetListScroll = gui.clamp(gui.targetListScroll,
                -Math.max(0.0f, gui.getContentHeight() - gui.getListHeight()), 0.0f);
        return true;
    }

    boolean handleScrollbarClick(int mouseX, int mouseY, int mouseButton) {
        if (mouseButton != 0) {
            return false;
        }
        VapeClickGui.ScrollbarMetrics metrics = gui.getScrollbarMetrics(gui.getModuleListY(), gui.getListHeight());
        if (!metrics.visible) {
            return false;
        }
        boolean onTrack = VapeClickGui.isHovered(metrics.trackX - 4.0f, metrics.trackY, metrics.trackX + 6.0f,
                metrics.trackY + metrics.trackHeight, mouseX, mouseY);
        if (!onTrack) {
            return false;
        }
        boolean onThumb = VapeClickGui.isHovered(metrics.trackX - 4.0f, metrics.thumbY, metrics.trackX + 6.0f,
                metrics.thumbY + metrics.thumbHeight, mouseX, mouseY);
        gui.draggingScrollbar = true;
        gui.scrollbarDragOffset = onThumb ? mouseY - metrics.thumbY : metrics.thumbHeight / 2.0f;
        updateScrollbarDrag(mouseY);
        return true;
    }

    void updateScrollbarDrag(int mouseY) {
        if (!gui.draggingScrollbar) {
            return;
        }
        if (!Mouse.isButtonDown(0)) {
            gui.draggingScrollbar = false;
            return;
        }
        VapeClickGui.ScrollbarMetrics metrics = gui.getScrollbarMetrics(gui.getModuleListY(), gui.getListHeight());
        if (!metrics.visible) {
            gui.draggingScrollbar = false;
            return;
        }
        float thumbTop = gui.clamp(mouseY - gui.scrollbarDragOffset, metrics.trackY,
                metrics.trackY + metrics.trackHeight - metrics.thumbHeight);
        float pct = (thumbTop - metrics.trackY) / Math.max(1.0f, metrics.trackHeight - metrics.thumbHeight);
        gui.targetListScroll = -metrics.maxScroll * pct;
        gui.listScroll = gui.animate(gui.listScroll, gui.targetListScroll, 0.38f);
    }

    private void drawModuleCard(Module module, float x, float y, float height, int mouseX, int mouseY, float alpha) {
        boolean selected = gui.selectedModule == module;
        float rowW = getRowWidth();
        boolean hovered = VapeClickGui.isHovered(x, y, x + rowW, y + height, mouseX, mouseY);
        float hover = gui.animateMap(gui.hoverProgress, module, hovered && !gui.closing ? 1.0f : 0.0f, 0.16f);
        float click = gui.animateMap(gui.clickProgress, module, 0.0f, 0.22f);
        if (selected || hover > 0.04f) {
            RenderUtil.drawSoftShadow(x, y, x + rowW, y + height, 7.0f,
                    gui.withAlpha(selected ? new Color(92, 85, 235).getRGB() : new Color(0, 0, 0, 190).getRGB(),
                            (selected ? 42.0f : 18.0f + hover * 12.0f) * alpha * gui.openProgress), 5, 3.0f);
        }
        if (click > 0.02f) {
            RenderUtil.drawSoftShadow(x, y, x + rowW, y + height, 7.0f,
                    gui.withAlpha(new Color(112, 101, 255).getRGB(), (14.0f + click * 36.0f) * alpha * gui.openProgress), 5, 2.8f);
        }
        if (selected) {
            int selectedFill = new Color(55, 54, 130, 218).getRGB();
            RenderUtil.drawFrostedGlassRect(x, y, x + rowW, y + height, 7.0f, 0.8f,
                    gui.withAlpha(selectedFill, 218.0f * alpha * gui.openProgress),
                    gui.withAlpha(new Color(132, 121, 255).getRGB(), 78.0f * alpha * gui.openProgress));
        } else if (hover > 0.01f) {
            int hoverFill = gui.blendColor(new Color(255, 255, 255, 0).getRGB(), new Color(34, 39, 52, 94).getRGB(), hover);
            RenderUtil.drawRoundedRect(x, y, x + rowW, y + height, VapeClickGui.CARD_RADIUS,
                    gui.withAlpha(hoverFill, gui.getAlpha(hoverFill) * alpha * gui.openProgress));
        }
        if (!selected) {
            RenderUtil.drawLine(x + 9.0f, y + height - 0.5f, x + rowW - 9.0f, y + height - 0.5f, 0.6f,
                    gui.withAlpha(new Color(102, 110, 128).getRGB(), 22.0f * alpha * gui.openProgress));
        }
        drawCardHeader(module, x, y, selected, alpha);
    }

    private void drawCardHeader(Module module, float x, float y, boolean selected, float alpha) {
        boolean enabled = module.getState();
        float centerY = y + 24.0f;
        drawModuleIcon(module, x + 20.0f, centerY, selected, alpha);
        String name = gui.trim(module.getName(), FontLoaders.F14, 86.0f);
        gui.drawFont(name, x + 42.0f, y + 12.0f,
                gui.withAlpha(enabled ? VapeClickGui.TEXT : new Color(205, 208, 214).getRGB(), 255.0f * alpha * gui.openProgress));
        gui.drawFont(gui.trim(gui.getDescription(module), FontLoaders.F14, 90.0f), x + 42.0f, y + 27.0f,
                gui.withAlpha(VapeClickGui.MUTED, 198.0f * alpha * gui.openProgress));
        gui.drawSwitch(gui.getModuleSwitchX(x), gui.getModuleSwitchY(y), enabled, alpha, module);
        drawStarIcon(getStarCenterX(x), centerY, gui.favoriteModules.contains(module), alpha);
    }

    private void drawModuleCount(float panelY) {
        int visible = gui.getVisibleModules().size();
        int enabled = gui.getEnabledModules();
        int total = ModuleManager.getModules().size();
        String text = gui.searchQuery.length() == 0
                ? enabled + " enabled / " + total + " modules"
                : visible + " results / " + total + " modules";
        gui.drawFont(text, gui.contentX + 16.0f, panelY + gui.panelH - 20.0f,
                gui.withAlpha(VapeClickGui.MUTED, 205.0f * gui.openProgress));
    }

    private float getRowWidth() {
        return VapeClickGui.CARD_W - 20.0f;
    }

    private float getStarCenterX(float rowX) {
        return rowX + getRowWidth() - 18.0f;
    }

    private boolean isStarHit(float rowX, float rowY, int mouseX, int mouseY) {
        float centerX = getStarCenterX(rowX);
        float centerY = rowY + 24.0f;
        return VapeClickGui.isHovered(centerX - 11.0f, centerY - 11.0f, centerX + 11.0f, centerY + 11.0f, mouseX, mouseY);
    }

    private void drawModuleIcon(Module module, float centerX, float centerY, boolean selected, float alpha) {
        int color = gui.withAlpha(selected || module.getState() ? new Color(226, 230, 246).getRGB() : new Color(166, 174, 190).getRGB(),
                220.0f * alpha * gui.openProgress);
        gui.drawCenteredIcon(ClickGuiIcons.forModule(module), FontLoaders.I20, centerX, centerY, color);
    }

    private void drawStarIcon(float centerX, float centerY, boolean favorite, float alpha) {
        int color = gui.withAlpha(favorite ? new Color(156, 147, 255).getRGB() : new Color(142, 149, 166).getRGB(),
                (favorite ? 230.0f : 176.0f) * alpha * gui.openProgress);
        gui.drawCenteredIcon(favorite ? FontLoaders.ICON_STAR : FontLoaders.ICON_STAR_OUTLINE,
                FontLoaders.I18, centerX, centerY, color);
    }

    private void drawScrollbar(float drawContentY, float listHeight) {
        VapeClickGui.ScrollbarMetrics metrics = gui.getScrollbarMetrics(drawContentY, listHeight);
        gui.scrollbarAlpha = gui.animate(gui.scrollbarAlpha, metrics.visible ? 1.0f : 0.0f, 0.18f);
        if (gui.scrollbarAlpha <= 0.01f) {
            return;
        }
        float dragBoost = gui.draggingScrollbar ? 1.0f : 0.0f;
        gui.drawSoftRect(metrics.trackX, metrics.trackY, metrics.trackX + 2.2f,
                metrics.trackY + metrics.trackHeight, 2.0f,
                gui.withAlpha(new Color(255, 255, 255, 32).getRGB(), 32.0f * gui.scrollbarAlpha * gui.openProgress));
        RenderUtil.drawSoftShadow(metrics.trackX, metrics.thumbY, metrics.trackX + 2.2f,
                metrics.thumbY + metrics.thumbHeight, 2.0f,
                gui.withAlpha(VapeClickGui.ACCENT, (35.0f + dragBoost * 60.0f) * gui.scrollbarAlpha * gui.openProgress), 4, 2.0f);
        gui.drawSoftRect(metrics.trackX, metrics.thumbY, metrics.trackX + 2.2f,
                metrics.thumbY + metrics.thumbHeight, 2.0f,
                gui.withAlpha(VapeClickGui.ACCENT, (150.0f + dragBoost * 70.0f) * gui.scrollbarAlpha * gui.openProgress));
    }
}
