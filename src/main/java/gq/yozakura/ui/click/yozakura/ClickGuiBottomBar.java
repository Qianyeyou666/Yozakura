package gq.yozakura.ui.click.yozakura;

import gq.yozakura.manager.ModuleManager;
import gq.yozakura.module.render.ClickGUI;
import gq.yozakura.engine.font.FontLoaders;
import gq.yozakura.engine.render.ui.RenderServices;
import net.minecraft.client.gui.ScaledResolution;

/**
 * ClickGUI 底部栏组件，显示用户信息和快捷键提示。
 * <p>
 * 左侧显示当前用户配置文件名，右侧显示 GUI 打开/关闭快捷键提示（Right Shift）。
 * 包级私有（package-private），仅供 {@link YozakuraClickGui} 内部使用。
 */
final class ClickGuiBottomBar {
    /** 关联的主 GUI 实例 */
    private final YozakuraClickGui gui;

    ClickGuiBottomBar(YozakuraClickGui gui) {
        this.gui = gui;
    }

    /**
     * 渲染底部栏。
     * <p>
     * 绘制两个毛玻璃卡片：左侧配置文件信息和右侧快捷键提示。
     *
     * @param sr 当前屏幕分辨率信息
     */
    void render(ScaledResolution sr, int mouseX, int mouseY) {
        float x = gui.contentX;
        float y = getY(sr);
        float w = getWidth();
        float h = YozakuraClickGui.BOTTOM_BAR_H;

        RenderServices.shapes().shadow(x, y, x + w, y + h, YozakuraClickGui.PANEL_RADIUS,
                gui.withAlpha(gui.shadowColor(230), 76.0f * gui.guiAlpha), 9, 5.5f);
        gui.drawPanelGlass(x, y, x + w, y + h, YozakuraClickGui.PANEL_RADIUS, 1.0f,
                gui.withAlpha(gui.guiColors().glassFill, gui.getAlpha(gui.guiColors().glassFill) * gui.guiAlpha),
                gui.withAlpha(gui.guiColors().glassBorder, gui.getAlpha(gui.guiColors().glassBorder) * gui.guiAlpha));

        float profileW = 136.0f;
        float settingsW = 224.0f;
        float actionsW = 150.0f;
        float keybindW = 150.0f;
        float aboutW = Math.max(120.0f, w - profileW - settingsW - actionsW - keybindW);

        float cursor = x;
        drawProfileSection(cursor, y, profileW);
        cursor += profileW;
        drawDivider(cursor, y, h);
        drawUiSettingsSection(cursor, y, settingsW, mouseX, mouseY);
        cursor += settingsW;
        drawDivider(cursor, y, h);
        drawQuickActionsSection(cursor, y, actionsW);
        cursor += actionsW;
        drawDivider(cursor, y, h);
        drawKeybindSection(cursor, y, keybindW);
        cursor += keybindW;
        drawDivider(cursor, y, h);
        drawAboutSection(cursor, y, aboutW);
    }

    boolean mouseClicked(ScaledResolution sr, int mouseX, int mouseY, int mouseButton) {
        if (mouseButton != 0) {
            return false;
        }
        float x = gui.contentX;
        float y = getY(sr);
        float settingsX = x + 136.0f;
        if (!YozakuraClickGui.isHovered(x, y, x + getWidth(), y + YozakuraClickGui.BOTTOM_BAR_H, mouseX, mouseY)) {
            return false;
        }
        if (handleThemeSwatchClick(settingsX, y, mouseX, mouseY)) {
            return true;
        }
        if (YozakuraClickGui.isHovered(settingsX + 58.0f, y + 50.0f, settingsX + 156.0f, y + 58.0f, mouseX, mouseY)) {
            double pct = gui.clamp((mouseX - (settingsX + 58.0f)) / 98.0f, 0.0D, 1.0D);
            ClickGUI.clickGuiAlpha.setValue(0.3D + pct * 0.7D);
            return true;
        }
        return true;
    }

    private void drawProfileSection(float x, float y, float w) {
        gui.drawSoftRect(x + 12.0f, y + 18.0f, x + 36.0f, y + 42.0f, 7.0f,
                gui.withAlpha(gui.guiColors().detailSelectedFill, 226.0f * gui.guiAlpha));
        gui.drawCenteredText("A", x + 12.0f, y + 23.0f, x + 36.0f, y + 40.0f,
                gui.withAlpha(gui.guiColors().text, 232.0f * gui.guiAlpha));
        gui.drawFont("Default", x + 48.0f, y + 19.0f,
                gui.withAlpha(gui.guiColors().text, 238.0f * gui.guiAlpha));
        gui.drawFont("Default Profile", x + 48.0f, y + 36.0f,
                gui.withAlpha(gui.guiColors().muted, 192.0f * gui.guiAlpha));
        gui.drawCenteredIcon(FontLoaders.ICON_DROPDOWN_ARROW, FontLoaders.I14, x + w - 18.0f, y + 31.0f,
                gui.withAlpha(gui.guiColors().muted, 180.0f * gui.guiAlpha));
        gui.drawFont(ModuleManager.getModules().size() + " modules", x + 48.0f, y + 55.0f,
                gui.withAlpha(gui.guiColors().faint, 160.0f * gui.guiAlpha));
    }

    private void drawUiSettingsSection(float x, float y, float w, int mouseX, int mouseY) {
        drawSectionTitle(FontLoaders.ICON_SETTINGS, "UI Settings", x + 16.0f, y + 17.0f);
        gui.drawFont("Palette", x + 16.0f, y + 40.0f,
                gui.withAlpha(gui.guiColors().muted, 190.0f * gui.guiAlpha));
        drawThemeSwatches(x + 58.0f, y + 34.0f, mouseX, mouseY);
        gui.drawFont(formatPalette(ClickGUI.palette.getValue()), x + w - 74.0f, y + 37.0f,
                gui.withAlpha(gui.guiColors().text, 214.0f * gui.guiAlpha));

        gui.drawFont("Transparency", x + 16.0f, y + 61.0f,
                gui.withAlpha(gui.guiColors().muted, 190.0f * gui.guiAlpha));
        float trackX = x + 58.0f;
        float trackY = y + 64.0f;
        float pct = (ClickGUI.clickGuiAlpha.getValue().floatValue() - 0.3f) / 0.7f;
        pct = gui.clamp(pct, 0.0f, 1.0f);
        RenderServices.shapes().line(trackX, trackY, trackX + 98.0f, trackY, 2.0f,
                gui.withAlpha(gui.guiColors().valueTrack, 190.0f * gui.guiAlpha));
        RenderServices.shapes().line(trackX, trackY, trackX + 98.0f * pct, trackY, 2.0f,
                gui.withAlpha(gui.guiColors().accent, 220.0f * gui.guiAlpha));
        RenderServices.shapes().circle(trackX + 98.0f * pct, trackY, 0, 360, 3.0f,
                gui.withAlpha(gui.guiColors().accent, 235.0f * gui.guiAlpha));
        gui.drawFont(Math.round(ClickGUI.clickGuiAlpha.getValue().floatValue() * 100.0f) + "%",
                x + w - 36.0f, y + 59.0f, gui.withAlpha(gui.guiColors().muted, 185.0f * gui.guiAlpha));
    }

    private void drawThemeSwatches(float x, float y, int mouseX, int mouseY) {
        drawThemeSwatch(ClickGUI.Palette.NIGHT_BLOOM, x, y, 0xFFE98BC1, mouseX, mouseY);
        drawThemeSwatch(ClickGUI.Palette.SAKURA, x + 24.0f, y, 0xFFE56B9D, mouseX, mouseY);
        drawThemeSwatch(ClickGUI.Palette.OCEAN, x + 48.0f, y, 0xFF4CC8FF, mouseX, mouseY);
        drawThemeSwatch(ClickGUI.Palette.GRAPHITE, x + 72.0f, y, 0xFFA7C7E7, mouseX, mouseY);
        drawThemeSwatch(ClickGUI.Palette.CUSTOM, x + 96.0f, y, ClickGUI.currentPalette().getAccentPrimary(), mouseX, mouseY);
    }

    private void drawThemeSwatch(ClickGUI.Palette palette, float x, float y, int color, int mouseX, int mouseY) {
        boolean selected = ClickGUI.palette.getValue() == palette;
        boolean hovered = YozakuraClickGui.isHovered(x - 5.0f, y - 5.0f, x + 21.0f, y + 21.0f, mouseX, mouseY);
        Float current = gui.themeSwatchProgress.get(palette);
        float progress = current == null ? (selected ? 1.0f : 0.0f) : current.floatValue();
        progress = gui.animate(progress, selected ? 1.0f : 0.0f, 0.18f);
        gui.themeSwatchProgress.put(palette, progress);
        Float currentHover = gui.themeSwatchHoverProgress.get(palette);
        float hover = currentHover == null ? 0.0f : currentHover.floatValue();
        hover = gui.animate(hover, hovered ? 1.0f : 0.0f, 0.20f);
        gui.themeSwatchHoverProgress.put(palette, hover);

        float selectedEase = gui.easeSmooth(progress);
        float hoverEase = gui.easeSmooth(hover);
        float lift = selectedEase * 1.2f + hoverEase * 0.7f;
        float size = 14.0f + selectedEase * 2.0f + hoverEase * 1.0f;
        float px = x - lift;
        float py = y - lift;
        if (progress > 0.02f || hover > 0.02f) {
            RenderServices.shapes().shadow(px - 2.0f, py - 2.0f, px + size + 2.0f, py + size + 2.0f, 5.0f,
                    gui.withAlpha(gui.guiColors().accent, (20.0f + 72.0f * progress + 34.0f * hover) * gui.guiAlpha), 5, 3.0f);
        }
        RenderServices.shapes().roundedBorder(px, py, px + size, py + size, 4.0f, 1.0f,
                gui.withAlpha(color, (210.0f + 35.0f * progress + 18.0f * hover) * gui.guiAlpha),
                gui.withAlpha(selected ? gui.guiColors().accent : gui.guiColors().glassBorder,
                        (70.0f + 116.0f * progress + 46.0f * hover) * gui.guiAlpha));
        if (palette == ClickGUI.Palette.SAKURA) {
            RenderServices.shapes().roundedBorder(px + 1.0f, py + 1.0f, px + size - 1.0f, py + size - 1.0f, 3.0f, 0.5f,
                    gui.withAlpha(0x00FFFFFF, 0.0f),
                    gui.withAlpha(0xFFB8C0CC, 80.0f * gui.guiAlpha));
        }
    }

    private boolean handleThemeSwatchClick(float settingsX, float y, int mouseX, int mouseY) {
        float swatchX = settingsX + 58.0f;
        float swatchY = y + 34.0f;
        ClickGUI.Palette[] palettes = ClickGUI.Palette.values();
        for (int i = 0; i < palettes.length; i++) {
            float x = swatchX + i * 24.0f;
            if (YozakuraClickGui.isHovered(x - 5.0f, swatchY - 5.0f, x + 21.0f, swatchY + 21.0f, mouseX, mouseY)) {
                ClickGUI.palette.setValue(palettes[i]);
                gui.themeFadeProgress = 1.0f;
                gui.addToast("Palette -> " + formatPalette(palettes[i]));
                return true;
            }
        }
        return false;
    }

    private void drawQuickActionsSection(float x, float y, float w) {
        drawSectionTitle(FontLoaders.ICON_SPARK, "Quick Actions", x + 16.0f, y + 17.0f);
        String[] icons = new String[]{FontLoaders.ICON_REFRESH, FontLoaders.ICON_DOWNLOAD_ALT,
                FontLoaders.ICON_FOLDER, FontLoaders.ICON_CODE, FontLoaders.ICON_SETTINGS};
        for (int i = 0; i < icons.length; i++) {
            float bx = x + 16.0f + i * 24.0f;
            gui.drawThemedGlass(bx, y + 42.0f, bx + 19.0f, y + 61.0f, 5.0f, 0.7f,
                    gui.withAlpha(gui.guiColors().glassFillSoft, 214.0f * gui.guiAlpha),
                    gui.withAlpha(gui.guiColors().glassBorder, 44.0f * gui.guiAlpha));
            gui.drawCenteredIcon(icons[i], FontLoaders.I14, bx + 9.5f, y + 51.5f,
                    gui.withAlpha(gui.guiColors().muted, 200.0f * gui.guiAlpha));
        }
    }

    private void drawKeybindSection(float x, float y, float w) {
        drawSectionTitle(FontLoaders.ICON_CLOCK, "Keybinds", x + 16.0f, y + 17.0f);
        gui.drawFont("GUI", x + 16.0f, y + 42.0f, gui.withAlpha(gui.guiColors().text, 220.0f * gui.guiAlpha));
        gui.drawFont("Right Shift", x + w - 70.0f, y + 42.0f,
                gui.withAlpha(gui.guiColors().muted, 188.0f * gui.guiAlpha));
        gui.drawFont("Sprint", x + 16.0f, y + 61.0f, gui.withAlpha(gui.guiColors().text, 220.0f * gui.guiAlpha));
        gui.drawFont("R", x + w - 28.0f, y + 61.0f, gui.withAlpha(gui.guiColors().muted, 188.0f * gui.guiAlpha));
    }

    private void drawAboutSection(float x, float y, float w) {
        drawSectionTitle(FontLoaders.ICON_INFO, "About", x + 16.0f, y + 17.0f);
        gui.drawFont("Vape Lite v1.3", x + 16.0f, y + 42.0f,
                gui.withAlpha(gui.guiColors().text, 222.0f * gui.guiAlpha));
        gui.drawFont("Made with " + (ClickGUI.palette.getValue() == ClickGUI.Palette.SAKURA ? "Sakura" : "care"), x + 16.0f, y + 59.0f,
                gui.withAlpha(gui.guiColors().muted, 185.0f * gui.guiAlpha));
        gui.drawFont("yozakura.gg", x + 16.0f, y + 74.0f,
                gui.withAlpha(gui.guiColors().accent, 190.0f * gui.guiAlpha));
    }

    private void drawSectionTitle(String icon, String title, float x, float y) {
        gui.drawCenteredIcon(icon, FontLoaders.I14, x + 5.0f, y + 4.0f,
                gui.withAlpha(gui.guiColors().accent, 212.0f * gui.guiAlpha));
        gui.drawFont(title, x + 17.0f, y, gui.withAlpha(gui.guiColors().text, 228.0f * gui.guiAlpha));
    }

    private void drawDivider(float x, float y, float h) {
        RenderServices.shapes().line(x, y + 1.0f, x, y + h - 1.0f, 0.7f,
                gui.withAlpha(gui.guiColors().glassBorder, 70.0f * gui.guiAlpha));
    }

    private String formatPalette(ClickGUI.Palette palette) {
        switch (palette) {
            case NIGHT_BLOOM:
                return "Night Bloom";
            case SAKURA:
                return "Sakura";
            case OCEAN:
                return "Ocean";
            case GRAPHITE:
                return "Graphite";
            case CUSTOM:
                return "Custom";
            default:
                throw new IllegalArgumentException("Unsupported palette: " + palette);
        }
    }

    private float getY(ScaledResolution sr) {
        return Math.min(sr.getScaledHeight() - YozakuraClickGui.BOTTOM_BAR_H - 8.0f,
                gui.contentY + gui.panelH + YozakuraClickGui.GAP);
    }

    private float getWidth() {
        return gui.sidePanelVisible ? gui.sideX + gui.sideW - gui.contentX : gui.detailX + gui.detailW - gui.contentX;
    }
}
