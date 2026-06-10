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
                VapeClickGui.PANEL_RADIUS, gui.withAlpha(gui.shadowColor(220),
                        70.0f * gui.guiAlpha), 8, 5.0f);
        RenderUtil.drawFrostedGlassRect(gui.sideX, gui.navY, gui.sideX + gui.sideW, gui.navY + VapeClickGui.NAV_H,
                VapeClickGui.PANEL_RADIUS, 1.0f,
                gui.withAlpha(gui.guiColors().glassFill, 206.0f * gui.guiAlpha),
                gui.withAlpha(gui.guiColors().glassBorder, 50.0f * gui.guiAlpha));
        gui.drawSoftRect(gui.sideX + 10.0f, gui.navY + 6.0f, gui.sideX + 26.0f, gui.navY + 22.0f, 5.0f,
                gui.withAlpha(new Color(81, 87, 103, 220).getRGB(), 220.0f * gui.guiAlpha));
        gui.drawCenteredIcon(FontLoaders.ICON_USER, FontLoaders.I14, gui.sideX + 18.0f, gui.navY + 14.0f,
                gui.withAlpha(gui.guiColors().text, 235.0f * gui.guiAlpha));
        gui.drawFont("VapuUser", gui.sideX + 34.0f, gui.navY + 6.0f,
                gui.withAlpha(gui.guiColors().text, 240.0f * gui.guiAlpha));
        gui.drawFont("Premium", gui.sideX + 34.0f, gui.navY + 18.0f,
                gui.withAlpha(gui.guiColors().accent, 210.0f * gui.guiAlpha));
        gui.drawCenteredIcon(FontLoaders.ICON_DROPDOWN_ARROW, FontLoaders.I14,
                gui.sideX + gui.sideW - 13.0f, gui.navY + 14.0f,
                gui.withAlpha(gui.guiColors().muted, 190.0f * gui.guiAlpha));
    }

    private void drawStatsPanel(float y) {
        RenderUtil.drawSoftShadow(gui.sideX, y, gui.sideX + gui.sideW, y + 64.0f, VapeClickGui.PANEL_RADIUS,
                gui.withAlpha(gui.shadowColor(220), 70.0f * gui.guiAlpha), 8, 5.0f);
        RenderUtil.drawFrostedGlassRect(gui.sideX, y, gui.sideX + gui.sideW, y + 64.0f, VapeClickGui.PANEL_RADIUS, 1.0f,
                gui.withAlpha(gui.guiColors().glassFill, 204.0f * gui.guiAlpha),
                gui.withAlpha(gui.guiColors().glassBorder, 48.0f * gui.guiAlpha));
        drawStat("FPS", gui.getLiveFpsText(), gui.sideX + 12.0f, y + 12.0f, gui.guiColors().text);
        drawStat("Ping", gui.getPingText(), gui.sideX + 68.0f, y + 12.0f, new Color(118, 213, 144).getRGB());
        drawStat("Modules", gui.getEnabledModules() + "/" + ModuleManager.getModules().size(), gui.sideX + 122.0f, y + 12.0f,
                gui.guiColors().accent);
        drawFpsGraph(y);
    }

    private void drawFpsGraph(float y) {
        float graphX = gui.sideX + 12.0f;
        float graphTop = y + 43.0f;
        float graphW = gui.sideW - 24.0f;
        float graphH = 14.0f;
        float graphBottom = graphTop + graphH;
        int count = gui.getFpsGraphSize();

        RenderUtil.drawLine(graphX, graphBottom, graphX + graphW, graphBottom, 0.55f,
                gui.withAlpha(new Color(105, 128, 148).getRGB(), 32.0f * gui.guiAlpha));
        if (count < 2) {
            float yMid = graphBottom - graphH * 0.48f;
            RenderUtil.drawLine(graphX, yMid, graphX + graphW, yMid, 0.7f,
                    gui.withAlpha(gui.guiColors().accent, 70.0f * gui.guiAlpha));
            return;
        }

        float min = Float.MAX_VALUE;
        float max = 0.0f;
        for (int i = 0; i < count; i++) {
            float sample = gui.getFpsGraphSample(i);
            min = Math.min(min, sample);
            max = Math.max(max, sample);
        }
        if (max - min < 24.0f) {
            float center = (min + max) * 0.5f;
            min = Math.max(0.0f, center - 12.0f);
            max = center + 12.0f;
        }

        float step = graphW / Math.max(1.0f, count - 1.0f);
        float previousX = graphX;
        float previousY = graphBottom - normalizedFps(gui.getFpsGraphSample(0), min, max) * graphH;
        for (int i = 1; i < count; i++) {
            float px = graphX + step * i;
            float py = graphBottom - normalizedFps(gui.getFpsGraphSample(i), min, max) * graphH;
            RenderUtil.drawLine(previousX, previousY, px, py, 2.0f,
                    gui.withAlpha(gui.guiColors().accent, 28.0f * gui.guiAlpha));
            RenderUtil.drawLine(previousX, previousY, px, py, 0.85f,
                    gui.withAlpha(new Color(116, 198, 229).getRGB(), 145.0f * gui.guiAlpha));
            previousX = px;
            previousY = py;
        }
        RenderUtil.drawCircle(previousX, previousY, 0, 360, 1.7f,
                gui.withAlpha(new Color(148, 224, 255).getRGB(), 190.0f * gui.guiAlpha));
    }

    private float normalizedFps(float fps, float min, float max) {
        return gui.clamp((fps - min) / Math.max(1.0f, max - min), 0.0f, 1.0f);
    }

    private void drawModuleSummary(float y) {
        float h = Math.min(180.0f, gui.panelH - 130.0f);
        RenderUtil.drawSoftShadow(gui.sideX, y, gui.sideX + gui.sideW, y + h, VapeClickGui.PANEL_RADIUS,
                gui.withAlpha(gui.shadowColor(230), 78.0f * gui.guiAlpha), 9, 6.0f);
        RenderUtil.drawFrostedGlassRect(gui.sideX, y, gui.sideX + gui.sideW, y + h, VapeClickGui.PANEL_RADIUS, 1.0f,
                gui.withAlpha(gui.guiColors().glassFill, 210.0f * gui.guiAlpha),
                gui.withAlpha(gui.guiColors().glassBorder, 48.0f * gui.guiAlpha));
        if (gui.selectedModule == null) {
            gui.drawCenteredText("Select a module", gui.sideX, y + h / 2.0f - 8.0f,
                    gui.sideX + gui.sideW, y + h / 2.0f + 8.0f,
                    gui.withAlpha(gui.guiColors().muted, 200.0f * gui.guiAlpha));
            return;
        }
        gui.drawCenteredIcon(ClickGuiIcons.forModule(gui.selectedModule), FontLoaders.I18,
                gui.sideX + 23.0f, y + 23.0f, gui.withAlpha(gui.guiColors().accent, 220.0f * gui.guiAlpha));
        gui.drawFont(gui.trim(gui.selectedModule.getName(), FontLoaders.F16, gui.sideW - 62.0f),
                gui.sideX + 38.0f, y + 18.0f, gui.withAlpha(gui.guiColors().text, 245.0f * gui.guiAlpha));
        gui.drawCenteredIcon(FontLoaders.ICON_STAR_OUTLINE, FontLoaders.I18,
                gui.sideX + gui.sideW - 24.0f, y + 22.0f,
                gui.withAlpha(gui.guiColors().muted, 190.0f * gui.guiAlpha));
        gui.drawFont(gui.trim(gui.getDescription(gui.selectedModule), FontLoaders.F14, gui.sideW - 32.0f),
                gui.sideX + 16.0f, y + 42.0f, gui.withAlpha(gui.guiColors().muted, 205.0f * gui.guiAlpha));
        RenderUtil.drawLine(gui.sideX + 16.0f, y + 74.0f, gui.sideX + gui.sideW - 16.0f, y + 74.0f, 0.6f,
                gui.withAlpha(new Color(95, 101, 118).getRGB(), 36.0f * gui.guiAlpha));
        drawSummaryRows(y + 86.0f);
        RenderUtil.drawLine(gui.sideX + 16.0f, y + h - 42.0f,
                gui.sideX + gui.sideW - 16.0f, y + h - 42.0f, 0.6f,
                gui.withAlpha(new Color(95, 101, 118).getRGB(), 36.0f * gui.guiAlpha));
        drawKeyChip(gui.sideX + 16.0f, y + h - 30.0f, gui.sideW - 32.0f, 18.0f, gui.selectedModule);
    }

    private void drawStat(String label, String value, float x, float y, int valueColor) {
        gui.drawFont(label, x, y, gui.withAlpha(gui.guiColors().muted, 175.0f * gui.guiAlpha));
        gui.drawFont(value, x, y + 15.0f, gui.withAlpha(valueColor, 235.0f * gui.guiAlpha));
    }

    private void drawSummaryRows(float y) {
        if (gui.selectedModule == null) {
            return;
        }
        String[][] rows = new String[][]{
                new String[]{"State", gui.selectedModule.getState() ? "Enabled" : "Disabled"},
                new String[]{"Key", gui.getKeyName(gui.selectedModule)},
                new String[]{"Options", String.valueOf(gui.getVisibleValueCount(gui.selectedModule))}
        };
        for (int i = 0; i < rows.length; i++) {
            float rowY = y + i * 20.0f;
            gui.drawFont(rows[i][0], gui.sideX + 16.0f, rowY, gui.withAlpha(gui.guiColors().muted, 178.0f * gui.guiAlpha));
            gui.drawFont(rows[i][1], gui.sideX + gui.sideW - 16.0f - FontLoaders.F14.getStringWidth(rows[i][1]), rowY,
                    gui.withAlpha(i == 0 && gui.selectedModule.getState() ? gui.guiColors().accent : gui.guiColors().text,
                            220.0f * gui.guiAlpha));
        }
        List<Value> values = gui.selectedModule.getValues();
        for (Value value : values) {
            if (!value.isVisible() || gui.isHiddenPaletteValue(gui.selectedModule, value)) {
                continue;
            }
            gui.drawFont(gui.trim(value.getName(), FontLoaders.F14, 72.0f), gui.sideX + 16.0f, y + 65.0f,
                    gui.withAlpha(gui.guiColors().muted, 170.0f * gui.guiAlpha));
            gui.drawFont(gui.trim(gui.getValueText(value), FontLoaders.F14, 70.0f), gui.sideX + gui.sideW - 86.0f, y + 65.0f,
                    gui.withAlpha(gui.guiColors().text, 215.0f * gui.guiAlpha));
            break;
        }
    }

    private void drawKeyChip(float x, float y, float w, float h, Module module) {
        RenderUtil.drawFrostedGlassRect(x, y, x + w, y + h, 6.0f, 0.8f,
                gui.withAlpha(gui.guiColors().glassFillSoft, 198.0f * gui.guiAlpha),
                gui.withAlpha(gui.guiColors().glassBorder, 50.0f * gui.guiAlpha));
        gui.drawCenteredText(gui.getKeyName(module), x, y + 4.0f, x + w, y + h - 2.0f,
                gui.withAlpha(gui.guiColors().text, 220.0f * gui.guiAlpha));
    }
}
