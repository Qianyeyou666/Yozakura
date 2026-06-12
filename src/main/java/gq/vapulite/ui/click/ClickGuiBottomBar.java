package gq.vapulite.ui.click;

import gq.vapulite.manager.ModuleManager;
import gq.vapulite.module.render.ClickGUI;
import gq.vapulite.module.render.HUD;
import gq.vapulite.engine.font.FontLoaders;
import gq.vapulite.engine.render.ui.RenderServices;
import net.minecraft.client.gui.ScaledResolution;

/**
 * ClickGUI 底部栏组件，显示用户信息和快捷键提示。
 * <p>
 * 左侧显示当前用户配置文件名，右侧显示 GUI 打开/关闭快捷键提示（Right Shift）。
 * 包级私有（package-private），仅供 {@link VapeClickGui} 内部使用。
 */
final class ClickGuiBottomBar {
    /** 关联的主 GUI 实例 */
    private final VapeClickGui gui;

    ClickGuiBottomBar(VapeClickGui gui) {
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
        float h = VapeClickGui.BOTTOM_BAR_H;

        RenderServices.shapes().shadow(x, y, x + w, y + h, VapeClickGui.PANEL_RADIUS,
                gui.withAlpha(gui.shadowColor(230), 76.0f * gui.guiAlpha), 9, 5.5f);
        gui.drawPanelGlass(x, y, x + w, y + h, VapeClickGui.PANEL_RADIUS, 1.0f,
                gui.withAlpha(gui.guiColors().glassFill, 218.0f * gui.guiAlpha),
                gui.withAlpha(gui.guiColors().glassBorder, 54.0f * gui.guiAlpha));

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
        if (!VapeClickGui.isHovered(x, y, x + getWidth(), y + VapeClickGui.BOTTOM_BAR_H, mouseX, mouseY)) {
            return false;
        }
        if (handleThemeSwatchClick(settingsX, y, mouseX, mouseY)) {
            return true;
        }
        if (VapeClickGui.isHovered(settingsX + 58.0f, y + 50.0f, settingsX + 156.0f, y + 58.0f, mouseX, mouseY)) {
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
        gui.drawFont("Theme", x + 16.0f, y + 40.0f,
                gui.withAlpha(gui.guiColors().muted, 190.0f * gui.guiAlpha));
        drawThemeSwatches(x + 58.0f, y + 34.0f, mouseX, mouseY);
        gui.drawFont(formatTheme(HUD.getTheme()), x + w - 60.0f, y + 37.0f,
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
        drawThemeSwatch(HUD.Theme.SAKURA, x, y, 0xFFE56B9D, mouseX, mouseY);
        drawThemeSwatch(HUD.Theme.LIGHT, x + 24.0f, y, 0xFFF8F8FA, mouseX, mouseY);
        drawThemeSwatch(HUD.Theme.DARK, x + 48.0f, y, 0xFF282836, mouseX, mouseY);
    }

    private void drawThemeSwatch(HUD.Theme theme, float x, float y, int color, int mouseX, int mouseY) {
        boolean selected = HUD.getTheme() == theme;
        boolean hovered = VapeClickGui.isHovered(x - 5.0f, y - 5.0f, x + 21.0f, y + 21.0f, mouseX, mouseY);
        Float current = gui.themeSwatchProgress.get(theme);
        float progress = current == null ? (selected ? 1.0f : 0.0f) : current.floatValue();
        progress = gui.animate(progress, selected ? 1.0f : 0.0f, 0.18f);
        gui.themeSwatchProgress.put(theme, progress);
        Float currentHover = gui.themeSwatchHoverProgress.get(theme);
        float hover = currentHover == null ? 0.0f : currentHover.floatValue();
        hover = gui.animate(hover, hovered ? 1.0f : 0.0f, 0.20f);
        gui.themeSwatchHoverProgress.put(theme, hover);

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
        if (theme == HUD.Theme.LIGHT) {
            RenderServices.shapes().roundedBorder(px + 1.0f, py + 1.0f, px + size - 1.0f, py + size - 1.0f, 3.0f, 0.5f,
                    gui.withAlpha(0x00FFFFFF, 0.0f),
                    gui.withAlpha(0xFFB8C0CC, 80.0f * gui.guiAlpha));
        }
    }

    private boolean handleThemeSwatchClick(float settingsX, float y, int mouseX, int mouseY) {
        float swatchX = settingsX + 58.0f;
        float swatchY = y + 34.0f;
        HUD.Theme[] themes = new HUD.Theme[]{HUD.Theme.SAKURA, HUD.Theme.LIGHT, HUD.Theme.DARK};
        for (int i = 0; i < themes.length; i++) {
            float x = swatchX + i * 24.0f;
            if (VapeClickGui.isHovered(x - 5.0f, swatchY - 5.0f, x + 21.0f, swatchY + 21.0f, mouseX, mouseY)) {
                HUD.setTheme(themes[i]);
                gui.themeFadeProgress = 1.0f;
                gui.addToast("Theme -> " + formatTheme(themes[i]));
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
        gui.drawFont("Made with " + (HUD.isSakuraTheme() ? "Sakura" : "care"), x + 16.0f, y + 59.0f,
                gui.withAlpha(gui.guiColors().muted, 185.0f * gui.guiAlpha));
        gui.drawFont("vape.gg", x + 16.0f, y + 74.0f,
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

    private String formatTheme(HUD.Theme theme) {
        if (theme == HUD.Theme.SAKURA) {
            return "Sakura";
        }
        if (theme == HUD.Theme.LIGHT) {
            return "Light";
        }
        return "Dark";
    }

    private float getY(ScaledResolution sr) {
        return Math.min(sr.getScaledHeight() - VapeClickGui.BOTTOM_BAR_H - 8.0f,
                gui.contentY + gui.panelH + VapeClickGui.GAP);
    }

    private float getWidth() {
        return gui.sidePanelVisible ? gui.sideX + gui.sideW - gui.contentX : gui.detailX + gui.detailW - gui.contentX;
    }
}
