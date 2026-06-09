package gq.vapulite.Vapu.VapeClickGui;

import gq.vapulite.Vapu.modules.Module;
import gq.vapulite.Vapu.utils.RenderUtil;
import gq.vapulite.Vapu.value.Mode;
import gq.vapulite.Vapu.value.Numbers;
import gq.vapulite.Vapu.value.Option;
import gq.vapulite.Vapu.value.Value;
import gq.vapulite.font.FontLoaders;
import gq.vapulite.ui.UiPanel;

import java.awt.Color;
import java.util.Arrays;

final class ClickGuiDetailPanel {
    private static final String[] DETAIL_TABS = new String[]{"General", "Targets", "Extra", "Rotation", "Visuals"};
    private static final float DROPDOWN_ROW_H = 18.0f;
    private final VapeClickGui gui;
    private final UiPanel panel;
    private Mode expandedMode; // 当前展开下拉栏的Mode，null表示没有展开

    ClickGuiDetailPanel(VapeClickGui gui) {
        this.gui = gui;
        this.panel = new UiPanel().setTheme(gui.uiTheme);
    }

    void render(int mouseX, int mouseY, float introY) {
        float y = gui.contentY + introY;
        gui.settingsScroll = gui.animate(gui.settingsScroll, gui.targetSettingsScroll, 0.14f);
        panel.setBounds(gui.detailX, y, gui.detailW, gui.panelH)
                .radius(VapeClickGui.PANEL_RADIUS)
                .fill(VapeClickGui.GLASS_FILL)
                .border(VapeClickGui.GLASS_BORDER)
                .shadow(new Color(0, 0, 0, 230).getRGB(), 92.0f, 10, 7.0f)
                .setAlpha(gui.openProgress)
                .render(mouseX, mouseY, 0.0f);

        if (gui.selectedModule == null) {
            gui.drawCenteredText("No modules", gui.detailX, y + gui.panelH / 2.0f - 8.0f,
                    gui.detailX + gui.detailW, y + gui.panelH / 2.0f + 8.0f,
                    gui.withAlpha(VapeClickGui.MUTED, 210.0f * gui.openProgress));
            return;
        }

        float headerX = gui.detailX + 20.0f;
        float headerY = y + 16.0f;
        drawModuleIcon(gui.selectedModule, headerX + 13.0f, headerY + 13.0f);
        FontLoaders.F16.drawString(gui.trim(gui.selectedModule.getName(), FontLoaders.F16, gui.detailW - 116.0f),
                headerX + 38.0f, headerY + 1.0f, gui.withAlpha(VapeClickGui.TEXT, 255.0f * gui.openProgress));
        gui.drawFont(gui.trim(gui.getDescription(gui.selectedModule), FontLoaders.F14, gui.detailW - 150.0f),
                headerX + 38.0f, headerY + 18.0f, gui.withAlpha(VapeClickGui.MUTED, 206.0f * gui.openProgress));
        gui.drawSwitch(gui.getDetailSwitchX(), gui.getDetailSwitchY(y), gui.selectedModule.getState(), 1.0f, gui.selectedModule);

        drawTabs(y);
        drawDetailValues(y, mouseX, mouseY);
        // 在 scissor 之外绘制 Mode 下拉栏，确保不被裁剪
        if (expandedMode != null) {
            drawModeDropdown(expandedMode, mouseX, mouseY);
        }
    }

    boolean mouseClicked(int mouseX, int mouseY, int mouseButton) {
        if (gui.selectedModule == null) {
            return false;
        }
        // 优先处理展开的下拉栏点击
        if (expandedMode != null && mouseButton == 0) {
            if (handleDropdownClick(expandedMode, mouseX, mouseY)) {
                return true;
            }
            // 点击下拉栏外部 → 关闭
            expandedMode = null;
            return true;
        }
        float headerToggleX = gui.getDetailSwitchX();
        float headerToggleY = gui.getDetailSwitchY(gui.contentY);
        if (mouseButton == 0 && gui.isSwitchHit(headerToggleX, headerToggleY, mouseX, mouseY)) {
            gui.selectedModule.setState(!gui.selectedModule.getState());
            gui.clickProgress.put(gui.selectedModule, 1.0f);
            return true;
        }
        if (mouseButton == 0 && handleTabClick(mouseX, mouseY)) {
            return true;
        }
        float x = gui.getDetailValuesX();
        float y = gui.getDetailValuesY(gui.contentY);
        float w = gui.getDetailValuesWidth();
        float h = gui.getDetailValuesHeight();
        if (!VapeClickGui.isHovered(x - 8.0f, y, x + w + 8.0f, y + h, mouseX, mouseY)) {
            return false;
        }
        return handleInlineValueClick(gui.selectedModule, x, y + gui.settingsScroll, w, mouseX, mouseY, mouseButton);
    }

    boolean updateScroll(int mouseX, int mouseY, int wheel) {
        if (wheel == 0 || gui.selectedModule == null || !VapeClickGui.isHovered(gui.getDetailValuesX(), gui.getDetailValuesY(gui.contentY),
                gui.getDetailValuesX() + gui.getDetailValuesWidth(), gui.getDetailValuesY(gui.contentY) + gui.getDetailValuesHeight(),
                mouseX, mouseY)) {
            return false;
        }
        gui.targetSettingsScroll += wheel > 0 ? 28.0f : -28.0f;
        gui.targetSettingsScroll = gui.clamp(gui.targetSettingsScroll,
                -Math.max(0.0f, gui.getSettingsContentHeight(gui.selectedModule) - gui.getDetailValuesHeight()), 0.0f);
        return true;
    }

    void updateNumberValue(Numbers value, int mouseX, float x, float w) {
        double min = value.getMinimum().doubleValue();
        double max = value.getMaximum().doubleValue();
        double inc = value.getIncrement().doubleValue();
        if (inc <= 0.0D) {
            inc = 0.1D;
        }
        double pct = gui.clamp((mouseX - x) / w, 0.0D, 1.0D);
        double result = min + (max - min) * pct;
        result = Math.round(result / inc) * inc;
        result = Math.max(min, Math.min(max, result));
        value.setValue(result);
    }

    private boolean handleInlineValueClick(Module module, float x, float valueY, float width,
                                           int mouseX, int mouseY, int mouseButton) {
        for (Value value : module.getValues()) {
            float valueH = gui.getValueHeight(value);
            if (VapeClickGui.isHovered(x, valueY, x + width, valueY + valueH, mouseX, mouseY)) {
                if (value instanceof Option && mouseButton == 0) {
                    value.setValue(!Boolean.TRUE.equals(value.getValue()));
                    gui.valueActiveProgress.put(value, 1.0f);
                    return true;
                }
                if (value instanceof Mode && mouseButton == 0) {
                    // 点击Mode → 展开/收起下拉栏
                    expandedMode = (expandedMode == value) ? null : (Mode) value;
                    gui.valueActiveProgress.put(value, 1.0f);
                    return true;
                }
                if (value instanceof Numbers && mouseButton == 0) {
                    gui.draggingNumber = value;
                    gui.draggingNumberX = gui.getSliderBarX(x, width);
                    gui.draggingNumberW = gui.getSliderBarWidth(width);
                    updateNumberValue((Numbers) value, mouseX, gui.draggingNumberX, gui.draggingNumberW);
                    return true;
                }
            }
            valueY += valueH;
        }
        return false;
    }

    private void drawModuleIcon(Module module, float centerX, float centerY) {
        RenderUtil.drawFrostedGlassRect(centerX - 13.0f, centerY - 13.0f, centerX + 13.0f, centerY + 13.0f,
                8.0f, 0.8f, gui.withAlpha(VapeClickGui.GLASS_FILL_SOFT, 178.0f * gui.openProgress),
                gui.withAlpha(VapeClickGui.ACCENT, 74.0f * gui.openProgress));
        RenderUtil.drawSoftShadow(centerX - 13.0f, centerY - 13.0f, centerX + 13.0f, centerY + 13.0f,
                8.0f, gui.withAlpha(VapeClickGui.ACCENT, 34.0f * gui.openProgress), 4, 2.0f);
        gui.drawCenteredIcon(ClickGuiIcons.forModule(module), FontLoaders.I20, centerX, centerY,
                gui.withAlpha(new Color(226, 232, 248).getRGB(), 236.0f * gui.openProgress));
    }

    private void drawTabs(float panelY) {
        float tabX = gui.detailX + 6.0f;
        float tabY = panelY + 58.0f;
        float tabW = gui.detailW - 12.0f;
        float tabH = 31.0f;
        RenderUtil.drawFrostedGlassRect(tabX, tabY, tabX + tabW, tabY + tabH, 5.0f, 0.8f,
                gui.withAlpha(VapeClickGui.GLASS_FILL_SOFT, 164.0f * gui.openProgress),
                gui.withAlpha(VapeClickGui.GLASS_BORDER, 40.0f * gui.openProgress));
        float each = tabW / DETAIL_TABS.length;
        for (int i = 0; i < DETAIL_TABS.length; i++) {
            float x = tabX + each * i;
            boolean active = i == gui.detailTabIndex;
            gui.drawCenteredText(DETAIL_TABS[i], x, tabY + 10.0f, x + each, tabY + 22.0f,
                    gui.withAlpha(active ? VapeClickGui.TEXT : VapeClickGui.MUTED,
                            (active ? 238.0f : 196.0f) * gui.openProgress));
            if (active) {
                RenderUtil.drawSoftShadow(x + 9.0f, tabY + tabH - 2.0f, x + each - 9.0f, tabY + tabH,
                        2.0f, gui.withAlpha(VapeClickGui.ACCENT, 100.0f * gui.openProgress), 4, 2.0f);
                RenderUtil.drawHorizontalGradientRect(x + 9.0f, tabY + tabH - 1.4f, x + each - 9.0f, tabY + tabH - 0.4f,
                        gui.withAlpha(VapeClickGui.ACCENT, 215.0f * gui.openProgress),
                        gui.withAlpha(new Color(152, 135, 255).getRGB(), 215.0f * gui.openProgress));
            }
        }
    }

    private boolean handleTabClick(int mouseX, int mouseY) {
        float tabX = gui.detailX + 6.0f;
        float tabY = gui.contentY + 58.0f;
        float tabW = gui.detailW - 12.0f;
        float tabH = 31.0f;
        if (!VapeClickGui.isHovered(tabX, tabY, tabX + tabW, tabY + tabH, mouseX, mouseY)) {
            return false;
        }
        int index = (int) ((mouseX - tabX) / (tabW / DETAIL_TABS.length));
        gui.detailTabIndex = Math.max(0, Math.min(DETAIL_TABS.length - 1, index));
        if (gui.detailTabIndex == 2 && gui.selectedModule != null) {
            gui.startBinding(gui.selectedModule);
        }
        return true;
    }

    private void drawDetailValues(float panelY, int mouseX, int mouseY) {
        float x = gui.getDetailValuesX();
        float y = gui.getDetailValuesY(panelY);
        float w = gui.getDetailValuesWidth();
        float h = gui.getDetailValuesHeight();
        Module module = gui.selectedModule;
        if (module.getValues().isEmpty()) {
            gui.drawFont("No settings available", x, y + 8.0f,
                    gui.withAlpha(VapeClickGui.MUTED, 210.0f * gui.openProgress));
            return;
        }

        float contentHeight = gui.getSettingsContentHeight(module);
        gui.targetSettingsScroll = gui.clamp(gui.targetSettingsScroll, -Math.max(0.0f, contentHeight - h), 0.0f);
        gui.settingsScroll = gui.clamp(gui.settingsScroll, -Math.max(0.0f, contentHeight - h), 0.0f);

        gui.beginScissor(x - 2.0f, y, w + 4.0f, h);
        try {
            float valueY = y + gui.settingsScroll;
            int index = 0;
            for (Value value : module.getValues()) {
                float rowAlpha = Math.max(0.0f, Math.min(1.0f, 1.0f - index * 0.015f));
                if (valueY + VapeClickGui.VALUE_ROW_H >= y - 2.0f && valueY <= y + h + 2.0f) {
                    float active = gui.animateValueMap(gui.valueActiveProgress, value,
                            gui.draggingNumber == value ? 1.0f : 0.0f, 0.18f);
                    if (active > 0.02f) {
                        gui.drawSoftRect(x - 6.0f, valueY + 1.0f, x + w + 2.0f,
                                valueY + VapeClickGui.VALUE_ROW_H - 2.0f, 6.0f,
                                gui.withAlpha(new Color(36, 41, 55, 160).getRGB(),
                                        120.0f * active * gui.openProgress));
                    }
                    if (value instanceof Option) {
                        drawOption((Option) value, x, valueY, w, rowAlpha);
                    } else if (value instanceof Numbers) {
                        drawNumber((Numbers) value, x, valueY, w, rowAlpha);
                    } else if (value instanceof Mode) {
                        drawMode((Mode) value, x, valueY, w, rowAlpha);
                    }
                }
                valueY += gui.getValueHeight(value);
                index++;
            }
        } finally {
            gui.endScissor();
        }
        drawSettingsScrollbar(panelY, contentHeight, h);
    }

    private void drawOption(Option value, float x, float y, float w, float alpha) {
        boolean enabled = Boolean.TRUE.equals(value.getValue());
        gui.drawFont(gui.trim(value.getName(), FontLoaders.F14, w - 68.0f), x, y + 8.0f,
                gui.withAlpha(enabled ? VapeClickGui.TEXT : VapeClickGui.MUTED, 255.0f * alpha * gui.openProgress));
        gui.drawSwitch(gui.getOptionSwitchX(x, w), gui.getOptionSwitchY(y), enabled, alpha, value);
    }

    private void drawNumber(Numbers value, float x, float y, float w, float alpha) {
        double min = value.getMinimum().doubleValue();
        double max = value.getMaximum().doubleValue();
        double current = ((Number) value.getValue()).doubleValue();
        float pct = (float) ((current - min) / Math.max(0.0001D, max - min));
        pct = Math.max(0.0f, Math.min(1.0f, pct));
        value.animX = gui.animate(value.animX, pct, 0.18f);
        float active = gui.animateValueMap(gui.valueActiveProgress, value, gui.draggingNumber == value ? 1.0f : 0.0f, 0.24f);
        float labelW = gui.getDetailLabelWidth(w);
        float barX = gui.getSliderBarX(x, w);
        float barW = gui.getSliderBarWidth(w);
        float barY = y + 15.0f;
        float pillW = gui.getDetailValuePillWidth();
        float pillX = x + w - pillW;
        gui.drawFont(gui.trim(value.getName(), FontLoaders.F14, labelW - 8.0f), x, y + 8.0f,
                gui.withAlpha(VapeClickGui.TEXT, 245.0f * alpha * gui.openProgress));
        RenderUtil.drawRoundedRect(barX, barY, barX + barW, barY + 2.0f, 2.0f,
                gui.withAlpha(new Color(61, 67, 82, 180).getRGB(), 178.0f * alpha * gui.openProgress));
        RenderUtil.drawProgressBar(barX, barY, barX + barW, barY + 2.0f, 2.0f, value.animX,
                0x00000000, gui.withAlpha(new Color(132, 117, 255).getRGB(), 230.0f * alpha * gui.openProgress));
        float knob = 3.2f + active * 1.0f;
        RenderUtil.drawSoftShadow(barX + barW * value.animX - knob, barY - 3.0f,
                barX + barW * value.animX + knob, barY + 5.0f, 4.0f,
                gui.withAlpha(new Color(132, 117, 255).getRGB(), 100.0f * alpha * gui.openProgress), 4, 2.0f);
        RenderUtil.drawRoundedRect(barX + barW * value.animX - knob, barY - knob + 1.0f,
                barX + barW * value.animX + knob, barY + knob + 1.0f, knob,
                gui.withAlpha(new Color(145, 128, 255).getRGB(), 255.0f * alpha * gui.openProgress));
        drawValuePill(gui.formatNumber(current), pillX, y + 3.0f, pillW, alpha);
    }

    private void drawMode(Mode value, float x, float y, float w, float alpha) {
        float labelW = gui.getDetailLabelWidth(w);
        float pillW = Math.min(112.0f, Math.max(72.0f, w - labelW));
        float pillX = x + w - pillW;
        gui.drawFont(gui.trim(value.getName(), FontLoaders.F14, labelW - 8.0f), x, y + 8.0f,
                gui.withAlpha(VapeClickGui.TEXT, 245.0f * alpha * gui.openProgress));
        RenderUtil.drawFrostedGlassRect(pillX, y + 3.0f, pillX + pillW, y + 23.0f, 5.0f, 0.8f,
                gui.withAlpha(VapeClickGui.GLASS_FILL_SOFT, 194.0f * alpha * gui.openProgress),
                gui.withAlpha(VapeClickGui.GLASS_BORDER, 48.0f * alpha * gui.openProgress));
        gui.drawFont(gui.trim(value.getModeAsString(), FontLoaders.F14, pillW - 28.0f), pillX + 12.0f, y + 9.0f,
                gui.withAlpha(VapeClickGui.TEXT, 230.0f * alpha * gui.openProgress));
        gui.drawFont("v", pillX + pillW - 15.0f, y + 8.0f,
                gui.withAlpha(VapeClickGui.MUTED, 185.0f * alpha * gui.openProgress));
    }

    private void drawValuePill(String text, float x, float y, float w, float alpha) {
        RenderUtil.drawFrostedGlassRect(x, y, x + w, y + 20.0f, 5.0f, 0.8f,
                gui.withAlpha(VapeClickGui.GLASS_FILL_SOFT, 190.0f * alpha * gui.openProgress),
                gui.withAlpha(VapeClickGui.GLASS_BORDER, 46.0f * alpha * gui.openProgress));
        gui.drawCenteredText(text, x, y + 5.0f, x + w, y + 17.0f,
                gui.withAlpha(VapeClickGui.TEXT, 220.0f * alpha * gui.openProgress));
    }

    private void drawSettingsScrollbar(float panelY, float contentHeight, float viewHeight) {
        if (contentHeight <= viewHeight + 1.0f) {
            return;
        }
        float trackX = gui.detailX + gui.detailW - 10.0f;
        float trackY = gui.getDetailValuesY(panelY) + 2.0f;
        float trackH = viewHeight - 4.0f;
        float thumbH = Math.max(22.0f, viewHeight / Math.max(1.0f, contentHeight) * trackH);
        float maxScroll = Math.max(1.0f, contentHeight - viewHeight);
        float pct = gui.clamp(-gui.settingsScroll / maxScroll, 0.0f, 1.0f);
        float thumbY = trackY + (trackH - thumbH) * pct;
        gui.drawSoftRect(trackX, trackY, trackX + 2.0f, trackY + trackH, 2.0f,
                gui.withAlpha(new Color(255, 255, 255, 26).getRGB(), 26.0f * gui.openProgress));
        gui.drawSoftRect(trackX, thumbY, trackX + 2.0f, thumbY + thumbH, 2.0f,
                gui.withAlpha(VapeClickGui.ACCENT, 150.0f * gui.openProgress));
    }

    // ==================== Mode 下拉栏 ====================

    /**
     * 计算某个Mode值在屏幕上的渲染位置(考虑到设置滚动偏移)
     */
    private float getModeValueY(Mode value) {
        float y = gui.getDetailValuesY(gui.contentY) + gui.settingsScroll;
        for (Value v : gui.selectedModule.getValues()) {
            if (v == value) return y;
            y += gui.getValueHeight(v);
        }
        return y;
    }

    /**
     * 绘制Mode下拉栏 — 在scissor之外渲染，展示所有可选的mode
     */
    private void drawModeDropdown(Mode value, int mouseX, int mouseY) {
        Object[] modes = value.getModes();
        if (modes.length == 0) return;

        float labelW = gui.getDetailLabelWidth(gui.getDetailValuesWidth());
        float pillW = Math.min(112.0f, Math.max(72.0f, gui.getDetailValuesWidth() - labelW));
        float pillX = gui.getDetailValuesX() + gui.getDetailValuesWidth() - pillW;
        float valueY = getModeValueY(value);
        float dropdownY = valueY + 23.0f; // pill底部

        int hoveredIndex = -1;
        for (int i = 0; i < modes.length; i++) {
            if (VapeClickGui.isHovered(pillX, dropdownY + i * DROPDOWN_ROW_H,
                    pillX + pillW, dropdownY + (i + 1) * DROPDOWN_ROW_H, mouseX, mouseY)) {
                hoveredIndex = i;
                break;
            }
        }

        // 下拉栏背景
        float dropdownH = modes.length * DROPDOWN_ROW_H;
        RenderUtil.drawFrostedGlassRect(pillX, dropdownY, pillX + pillW, dropdownY + dropdownH,
                5.0f, 0.9f,
                gui.withAlpha(new Color(18, 22, 30, 240).getRGB(), 238.0f * gui.openProgress),
                gui.withAlpha(VapeClickGui.GLASS_BORDER, 62.0f * gui.openProgress));
        RenderUtil.drawSoftShadow(pillX, dropdownY, pillX + pillW, dropdownY + dropdownH,
                5.0f, gui.withAlpha(new Color(0, 0, 0, 200).getRGB(), 82.0f * gui.openProgress), 6, 3.0f);

        // 每行
        for (int i = 0; i < modes.length; i++) {
            float rowY = dropdownY + i * DROPDOWN_ROW_H;
            boolean selected = modes[i] == value.getValue();
            boolean hovered = i == hoveredIndex;

            if (hovered || selected) {
                gui.drawSoftRect(pillX + 2.0f, rowY + 1.0f, pillX + pillW - 2.0f, rowY + DROPDOWN_ROW_H - 1.0f,
                        4.0f, gui.withAlpha(selected ? new Color(88, 90, 178, 160).getRGB()
                                : new Color(55, 58, 70, 140).getRGB(),
                        (selected ? 178.0f : 110.0f) * gui.openProgress));
            }
            gui.drawFont(gui.trim(modes[i].toString(), FontLoaders.F14, pillW - 20.0f),
                    pillX + 10.0f, rowY + 4.0f,
                    gui.withAlpha(selected ? VapeClickGui.ACCENT : VapeClickGui.TEXT,
                            (selected ? 240.0f : 210.0f) * gui.openProgress));
        }
    }

    /**
     * 处理下拉栏内的点击 — 点击某行则切换mode并关闭下拉栏
     */
    private boolean handleDropdownClick(Mode value, int mouseX, int mouseY) {
        Object[] modes = value.getModes();
        float labelW = gui.getDetailLabelWidth(gui.getDetailValuesWidth());
        float pillW = Math.min(112.0f, Math.max(72.0f, gui.getDetailValuesWidth() - labelW));
        float pillX = gui.getDetailValuesX() + gui.getDetailValuesWidth() - pillW;
        float dropdownY = getModeValueY(value) + 23.0f;

        for (int i = 0; i < modes.length; i++) {
            float rowY = dropdownY + i * DROPDOWN_ROW_H;
            if (VapeClickGui.isHovered(pillX, rowY, pillX + pillW, rowY + DROPDOWN_ROW_H,
                    mouseX, mouseY)) {
                value.setValue(modes[i]);
                expandedMode = null;
                gui.valueActiveProgress.put(value, 1.0f);
                return true;
            }
        }
        return false;
    }
}
