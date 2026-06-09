package gq.vapulite.Vapu.VapeClickGui;

import gq.vapulite.Manager.ModuleManager;
import gq.vapulite.Vapu.modules.Module;
import gq.vapulite.Vapu.utils.RenderUtil;
import gq.vapulite.Vapu.value.Value;
import gq.vapulite.font.FontLoaders;
import net.minecraft.client.gui.ScaledResolution;

import java.awt.Color;
import java.util.List;

final class ClickGuiSidePanel {
    private final VapeClickGui gui;

    ClickGuiSidePanel(VapeClickGui gui) {
        this.gui = gui;
    }

    void render(ScaledResolution sr, int mouseX, int mouseY, float introY) {
        if (!gui.sidePanelVisible) {
            return;
        }
        float y = gui.contentY + introY;
        drawUserPanel(y);
        drawStatsPanel(y + 56.0f);
        drawModuleSummary(y + 130.0f);
    }

    boolean mouseClicked(int mouseX, int mouseY, int mouseButton) {
        if (mouseButton != 0 || !gui.sidePanelVisible || gui.selectedModule == null) {
            return false;
        }
        float summaryY = gui.contentY + 130.0f;
        float summaryH = Math.min(180.0f, gui.panelH - 130.0f);
        float chipX = gui.sideX + 16.0f;
        float chipY = summaryY + summaryH - 30.0f;
        if (VapeClickGui.isHovered(chipX, chipY, chipX + gui.sideW - 32.0f, chipY + 18.0f, mouseX, mouseY)
                || VapeClickGui.isHovered(gui.sideX + 12.0f, summaryY + 100.0f,
                gui.sideX + gui.sideW - 12.0f, summaryY + 120.0f, mouseX, mouseY)) {
            gui.startBinding(gui.selectedModule);
            return true;
        }
        return false;
    }

    private void drawUserPanel(float y) {
        RenderUtil.drawSoftShadow(gui.sideX, gui.navY, gui.sideX + gui.sideW, gui.navY + VapeClickGui.NAV_H,
                VapeClickGui.PANEL_RADIUS, gui.withAlpha(new Color(0, 0, 0, 220).getRGB(),
                        70.0f * gui.openProgress), 8, 5.0f);
        RenderUtil.drawFrostedGlassRect(gui.sideX, gui.navY, gui.sideX + gui.sideW, gui.navY + VapeClickGui.NAV_H,
                VapeClickGui.PANEL_RADIUS, 1.0f,
                gui.withAlpha(VapeClickGui.GLASS_FILL, 206.0f * gui.openProgress),
                gui.withAlpha(VapeClickGui.GLASS_BORDER, 50.0f * gui.openProgress));
        gui.drawSoftRect(gui.sideX + 10.0f, gui.navY + 6.0f, gui.sideX + 26.0f, gui.navY + 22.0f, 5.0f,
                gui.withAlpha(new Color(81, 87, 103, 220).getRGB(), 220.0f * gui.openProgress));
        gui.drawCenteredIcon(FontLoaders.ICON_USER, FontLoaders.I14, gui.sideX + 18.0f, gui.navY + 14.0f,
                gui.withAlpha(VapeClickGui.TEXT, 235.0f * gui.openProgress));
        gui.drawFont("VapuUser", gui.sideX + 34.0f, gui.navY + 6.0f,
                gui.withAlpha(VapeClickGui.TEXT, 240.0f * gui.openProgress));
        gui.drawFont("Premium", gui.sideX + 34.0f, gui.navY + 18.0f,
                gui.withAlpha(VapeClickGui.ACCENT, 210.0f * gui.openProgress));
        gui.drawCenteredIcon(FontLoaders.ICON_DROPDOWN_ARROW, FontLoaders.I14,
                gui.sideX + gui.sideW - 13.0f, gui.navY + 14.0f,
                gui.withAlpha(VapeClickGui.MUTED, 190.0f * gui.openProgress));
    }

    private void drawStatsPanel(float y) {
        RenderUtil.drawSoftShadow(gui.sideX, y, gui.sideX + gui.sideW, y + 64.0f, VapeClickGui.PANEL_RADIUS,
                gui.withAlpha(new Color(0, 0, 0, 220).getRGB(), 70.0f * gui.openProgress), 8, 5.0f);
        RenderUtil.drawFrostedGlassRect(gui.sideX, y, gui.sideX + gui.sideW, y + 64.0f, VapeClickGui.PANEL_RADIUS, 1.0f,
                gui.withAlpha(VapeClickGui.GLASS_FILL, 204.0f * gui.openProgress),
                gui.withAlpha(VapeClickGui.GLASS_BORDER, 48.0f * gui.openProgress));
        drawStat("FPS", gui.getLiveFpsText(), gui.sideX + 12.0f, y + 12.0f, VapeClickGui.TEXT);
        drawStat("Ping", gui.getPingText(), gui.sideX + 68.0f, y + 12.0f, new Color(118, 213, 144).getRGB());
        drawStat("Modules", gui.getEnabledModules() + "/" + ModuleManager.getModules().size(), gui.sideX + 122.0f, y + 12.0f,
                VapeClickGui.ACCENT);
        float graphY = y + 48.0f;
        for (int i = 0; i < 10; i++) {
            float px = gui.sideX + 12.0f + i * 14.0f;
            float spike = (i % 3 == 1 ? 6.0f : i % 4 == 0 ? 3.0f : 1.5f);
            RenderUtil.drawLine(px, graphY, px + 8.0f, graphY - spike, 0.7f,
                    gui.withAlpha(VapeClickGui.ACCENT, 120.0f * gui.openProgress));
            RenderUtil.drawLine(px + 8.0f, graphY - spike, px + 14.0f, graphY - 1.5f, 0.7f,
                    gui.withAlpha(VapeClickGui.ACCENT, 90.0f * gui.openProgress));
        }
    }

    private void drawModuleSummary(float y) {
        float h = Math.min(180.0f, gui.panelH - 130.0f);
        RenderUtil.drawSoftShadow(gui.sideX, y, gui.sideX + gui.sideW, y + h, VapeClickGui.PANEL_RADIUS,
                gui.withAlpha(new Color(0, 0, 0, 230).getRGB(), 78.0f * gui.openProgress), 9, 6.0f);
        RenderUtil.drawFrostedGlassRect(gui.sideX, y, gui.sideX + gui.sideW, y + h, VapeClickGui.PANEL_RADIUS, 1.0f,
                gui.withAlpha(VapeClickGui.GLASS_FILL, 210.0f * gui.openProgress),
                gui.withAlpha(VapeClickGui.GLASS_BORDER, 48.0f * gui.openProgress));
        if (gui.selectedModule == null) {
            gui.drawCenteredText("Select a module", gui.sideX, y + h / 2.0f - 8.0f,
                    gui.sideX + gui.sideW, y + h / 2.0f + 8.0f,
                    gui.withAlpha(VapeClickGui.MUTED, 200.0f * gui.openProgress));
            return;
        }
        gui.drawCenteredIcon(ClickGuiIcons.forModule(gui.selectedModule), FontLoaders.I18,
                gui.sideX + 23.0f, y + 23.0f, gui.withAlpha(VapeClickGui.ACCENT, 220.0f * gui.openProgress));
        gui.drawFont(gui.trim(gui.selectedModule.getName(), FontLoaders.F16, gui.sideW - 62.0f),
                gui.sideX + 38.0f, y + 18.0f, gui.withAlpha(VapeClickGui.TEXT, 245.0f * gui.openProgress));
        gui.drawCenteredIcon(FontLoaders.ICON_STAR_OUTLINE, FontLoaders.I18,
                gui.sideX + gui.sideW - 24.0f, y + 22.0f,
                gui.withAlpha(VapeClickGui.MUTED, 190.0f * gui.openProgress));
        gui.drawFont(gui.trim(gui.getDescription(gui.selectedModule), FontLoaders.F14, gui.sideW - 32.0f),
                gui.sideX + 16.0f, y + 42.0f, gui.withAlpha(VapeClickGui.MUTED, 205.0f * gui.openProgress));
        RenderUtil.drawLine(gui.sideX + 16.0f, y + 74.0f, gui.sideX + gui.sideW - 16.0f, y + 74.0f, 0.6f,
                gui.withAlpha(new Color(95, 101, 118).getRGB(), 36.0f * gui.openProgress));
        drawSummaryRows(y + 86.0f);
        RenderUtil.drawLine(gui.sideX + 16.0f, y + h - 42.0f,
                gui.sideX + gui.sideW - 16.0f, y + h - 42.0f, 0.6f,
                gui.withAlpha(new Color(95, 101, 118).getRGB(), 36.0f * gui.openProgress));
        drawKeyChip(gui.sideX + 16.0f, y + h - 30.0f, gui.sideW - 32.0f, 18.0f, gui.selectedModule);
    }

    private void drawStat(String label, String value, float x, float y, int valueColor) {
        gui.drawFont(label, x, y, gui.withAlpha(VapeClickGui.MUTED, 175.0f * gui.openProgress));
        gui.drawFont(value, x, y + 15.0f, gui.withAlpha(valueColor, 235.0f * gui.openProgress));
    }

    private void drawSummaryRows(float y) {
        if (gui.selectedModule == null) {
            return;
        }
        String[][] rows = new String[][]{
                new String[]{"State", gui.selectedModule.getState() ? "Enabled" : "Disabled"},
                new String[]{"Key", gui.getKeyName(gui.selectedModule)},
                new String[]{"Options", String.valueOf(gui.selectedModule.getValues().size())}
        };
        for (int i = 0; i < rows.length; i++) {
            float rowY = y + i * 20.0f;
            gui.drawFont(rows[i][0], gui.sideX + 16.0f, rowY, gui.withAlpha(VapeClickGui.MUTED, 178.0f * gui.openProgress));
            gui.drawFont(rows[i][1], gui.sideX + gui.sideW - 16.0f - FontLoaders.F14.getStringWidth(rows[i][1]), rowY,
                    gui.withAlpha(i == 0 && gui.selectedModule.getState() ? VapeClickGui.ACCENT : VapeClickGui.TEXT,
                            220.0f * gui.openProgress));
        }
        List<Value> values = gui.selectedModule.getValues();
        if (!values.isEmpty()) {
            Value value = values.get(0);
            gui.drawFont(gui.trim(value.getName(), FontLoaders.F14, 72.0f), gui.sideX + 16.0f, y + 65.0f,
                    gui.withAlpha(VapeClickGui.MUTED, 170.0f * gui.openProgress));
            gui.drawFont(gui.trim(gui.getValueText(value), FontLoaders.F14, 70.0f), gui.sideX + gui.sideW - 86.0f, y + 65.0f,
                    gui.withAlpha(VapeClickGui.TEXT, 215.0f * gui.openProgress));
        }
    }

    private void drawKeyChip(float x, float y, float w, float h, Module module) {
        RenderUtil.drawFrostedGlassRect(x, y, x + w, y + h, 6.0f, 0.8f,
                gui.withAlpha(VapeClickGui.GLASS_FILL_SOFT, 198.0f * gui.openProgress),
                gui.withAlpha(VapeClickGui.GLASS_BORDER, 50.0f * gui.openProgress));
        gui.drawCenteredText(gui.getKeyName(module), x, y + 4.0f, x + w, y + h - 2.0f,
                gui.withAlpha(VapeClickGui.TEXT, 220.0f * gui.openProgress));
    }
}
