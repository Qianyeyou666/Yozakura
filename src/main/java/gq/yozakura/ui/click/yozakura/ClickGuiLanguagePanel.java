package gq.yozakura.ui.click.yozakura;

import gq.yozakura.core.ClientLanguage;
import gq.yozakura.engine.font.FontLoaders;
import gq.yozakura.engine.render.ui.RenderServices;
import gq.yozakura.module.render.ClickGUI;
import net.minecraft.client.gui.ScaledResolution;

/**
 * Yozakura ClickGUI 的语言设置浮层。
 *
 * <p>入口嵌入顶部导航栏，内容使用与详情面板一致的毛玻璃、阴影和选中状态颜色。</p>
 */
final class ClickGuiLanguagePanel {
    private static final float NAV_BUTTON_W = 32.0f;
    private static final float PANEL_W = 264.0f;
    private static final float PANEL_H = 154.0f;
    private static final float OPTION_H = 23.0f;

    private final YozakuraClickGui gui;
    private boolean open;
    private float openProgress;
    private float navigationHover;
    private float englishHover;
    private float chineseHover;

    ClickGuiLanguagePanel(YozakuraClickGui gui) {
        this.gui = gui;
    }

    void reset() {
        open = false;
        openProgress = 0.0f;
        navigationHover = 0.0f;
        englishHover = 0.0f;
        chineseHover = 0.0f;
    }

    boolean isOpen() {
        return open;
    }

    void open() {
        open = true;
    }

    void close() {
        open = false;
    }

    float navigationTabsWidth() {
        return Math.max(1.0f, gui.navW - NAV_BUTTON_W);
    }

    void drawNavigationButton(int mouseX, int mouseY, float introY) {
        float x = navigationButtonX();
        float y = gui.navY + introY + 4.0f;
        float w = NAV_BUTTON_W - 6.0f;
        float h = YozakuraClickGui.NAV_H - 8.0f;
        boolean hovered = YozakuraClickGui.isHovered(x, y, x + w, y + h, mouseX, mouseY);
        navigationHover = gui.animate(navigationHover, hovered && !gui.closing ? 1.0f : 0.0f, 0.18f);
        float hover = gui.easeSmooth(navigationHover);

        if (hover > 0.01f || open) {
            RenderServices.shapes().shadow(x, y, x + w, y + h, 6.0f,
                    gui.withAlpha(gui.guiColors().accent, (18.0f + hover * 46.0f + (open ? 24.0f : 0.0f)) * gui.guiAlpha),
                    4, 2.5f);
        }
        gui.drawSoftRect(x, y, x + w, y + h, 6.0f,
                gui.withAlpha(open ? gui.guiColors().detailSelectedFill : gui.guiColors().navDefaultHover,
                        (open ? 196.0f : 116.0f * hover) * gui.guiAlpha));
        gui.drawCenteredIcon(FontLoaders.ICON_SETTINGS, FontLoaders.I16, x + w / 2.0f, y + h / 2.0f,
                gui.withAlpha(open || hover > 0.15f ? gui.guiColors().text : gui.guiColors().muted,
                        (open ? 242.0f : 194.0f + hover * 45.0f) * gui.guiAlpha));
    }

    boolean handleNavigationClick(int mouseX, int mouseY) {
        float x = navigationButtonX();
        float y = gui.navY + 4.0f;
        float w = NAV_BUTTON_W - 6.0f;
        float h = YozakuraClickGui.NAV_H - 8.0f;
        if (!YozakuraClickGui.isHovered(x, y, x + w, y + h, mouseX, mouseY)) {
            return false;
        }
        open();
        return true;
    }

    void render(ScaledResolution sr, int mouseX, int mouseY) {
        openProgress = gui.animate(openProgress, open ? 1.0f : 0.0f, 0.20f);
        if (openProgress <= 0.01f) {
            return;
        }

        float alpha = gui.easeSmooth(openProgress) * gui.guiAlpha;
        Bounds bounds = bounds(sr);
        RenderServices.shapes().rect(0.0f, 0.0f, sr.getScaledWidth(), sr.getScaledHeight(),
                gui.withAlpha(0xFF000000, 92.0f * alpha));
        RenderServices.shapes().shadow(bounds.x, bounds.y, bounds.x + bounds.w, bounds.y + bounds.h,
                YozakuraClickGui.PANEL_RADIUS, gui.withAlpha(gui.shadowColor(230), 112.0f * alpha), 10, 7.0f);
        gui.drawThemedGlass(bounds.x, bounds.y, bounds.x + bounds.w, bounds.y + bounds.h,
                YozakuraClickGui.PANEL_RADIUS, 1.0f,
                gui.withAlpha(gui.guiColors().glassFill, 235.0f * alpha),
                gui.withAlpha(gui.guiColors().detailSelectedBorder, 94.0f * alpha));

        float contentX = bounds.x + 18.0f;
        gui.drawCenteredIcon(FontLoaders.ICON_SETTINGS, FontLoaders.I18, contentX + 7.0f, bounds.y + 20.0f,
                gui.withAlpha(gui.guiColors().accent, 232.0f * alpha));
        gui.drawFont(ClickGUI.languageText("Language", "语言"), contentX + 20.0f, bounds.y + 13.0f,
                gui.withAlpha(gui.guiColors().text, 246.0f * alpha));
        gui.drawFont(ClickGUI.languageText("Choose the interface language", "选择界面语言"), contentX + 20.0f,
                bounds.y + 28.0f, gui.withAlpha(gui.guiColors().muted, 204.0f * alpha));
        RenderServices.shapes().line(contentX, bounds.y + 48.0f, bounds.x + bounds.w - 18.0f, bounds.y + 48.0f,
                0.75f, gui.withAlpha(gui.guiColors().glassBorder, 92.0f * alpha));

        gui.drawFont(ClickGUI.languageText("Mode", "模式"), contentX, bounds.y + 61.0f,
                gui.withAlpha(gui.guiColors().muted, 218.0f * alpha));
        gui.drawFont(ClickGUI.getLanguage().getDisplayName(), bounds.x + bounds.w - 75.0f, bounds.y + 61.0f,
                gui.withAlpha(gui.guiColors().accent, 232.0f * alpha));

        float optionX = contentX;
        float optionW = bounds.w - 36.0f;
        float englishY = bounds.y + 78.0f;
        float chineseY = englishY + OPTION_H + 5.0f;
        drawOption(ClientLanguage.ENGLISH, optionX, englishY, optionW, OPTION_H, mouseX, mouseY, alpha);
        drawOption(ClientLanguage.CHINESE, optionX, chineseY, optionW, OPTION_H, mouseX, mouseY, alpha);
        gui.drawFont(ClickGUI.languageText("Changes apply immediately", "切换后立即生效"), contentX, bounds.y + 139.0f,
                gui.withAlpha(gui.guiColors().faint, 184.0f * alpha));
    }

    boolean mouseClicked(ScaledResolution sr, int mouseX, int mouseY, int mouseButton) {
        if (!open) {
            return false;
        }
        if (mouseButton != 0) {
            return true;
        }

        Bounds bounds = bounds(sr);
        if (!YozakuraClickGui.isHovered(bounds.x, bounds.y, bounds.x + bounds.w, bounds.y + bounds.h, mouseX, mouseY)) {
            close();
            return true;
        }

        float optionX = bounds.x + 18.0f;
        float optionW = bounds.w - 36.0f;
        float englishY = bounds.y + 78.0f;
        float chineseY = englishY + OPTION_H + 5.0f;
        if (YozakuraClickGui.isHovered(optionX, englishY, optionX + optionW, englishY + OPTION_H, mouseX, mouseY)) {
            ClickGUI.setLanguage(ClientLanguage.ENGLISH);
            return true;
        }
        if (YozakuraClickGui.isHovered(optionX, chineseY, optionX + optionW, chineseY + OPTION_H, mouseX, mouseY)) {
            ClickGUI.setLanguage(ClientLanguage.CHINESE);
            return true;
        }
        return true;
    }

    private void drawOption(ClientLanguage language, float x, float y, float w, float h,
                            int mouseX, int mouseY, float alpha) {
        boolean selected = ClickGUI.getLanguage() == language;
        boolean hovered = YozakuraClickGui.isHovered(x, y, x + w, y + h, mouseX, mouseY);
        if (language == ClientLanguage.ENGLISH) {
            englishHover = gui.animate(englishHover, hovered ? 1.0f : 0.0f, 0.18f);
        } else {
            chineseHover = gui.animate(chineseHover, hovered ? 1.0f : 0.0f, 0.18f);
        }
        float hover = gui.easeSmooth(language == ClientLanguage.ENGLISH ? englishHover : chineseHover);
        int fill = selected ? gui.guiColors().modeRowSelected : gui.guiColors().glassFillSoft;
        int border = selected ? gui.guiColors().detailSelectedBorder : gui.guiColors().glassBorder;
        gui.drawThemedGlass(x, y, x + w, y + h, 6.0f, 0.75f,
                gui.withAlpha(fill, (selected ? 178.0f : 148.0f + hover * 36.0f) * alpha),
                gui.withAlpha(border, (selected ? 116.0f : 48.0f + hover * 46.0f) * alpha));
        if (selected || hover > 0.01f) {
            RenderServices.shapes().shadow(x + 2.0f, y + 2.0f, x + w - 2.0f, y + h - 2.0f, 5.0f,
                    gui.withAlpha(gui.guiColors().accent,
                            (selected ? 26.0f : 13.0f * hover) * alpha), 4, 2.0f);
        }
        gui.drawFont(language.getDisplayName(), x + 11.0f, y + 6.0f,
                gui.withAlpha(selected ? gui.guiColors().text : gui.guiColors().muted,
                        (selected ? 240.0f : 206.0f + hover * 25.0f) * alpha));
        if (selected) {
            gui.drawCenteredIcon(FontLoaders.ICON_CHECKMARK, FontLoaders.I14, x + w - 14.0f, y + h / 2.0f,
                    gui.withAlpha(gui.guiColors().accent, 238.0f * alpha));
        }
    }

    private float navigationButtonX() {
        return gui.navX + gui.navW - NAV_BUTTON_W + 3.0f;
    }

    private Bounds bounds(ScaledResolution sr) {
        float width = Math.min(PANEL_W, Math.max(210.0f, sr.getScaledWidth() - 28.0f));
        float height = Math.min(PANEL_H, Math.max(132.0f, sr.getScaledHeight() - 28.0f));
        return new Bounds(sr.getScaledWidth() / 2.0f - width / 2.0f,
                sr.getScaledHeight() / 2.0f - height / 2.0f, width, height);
    }

    private static final class Bounds {
        private final float x;
        private final float y;
        private final float w;
        private final float h;

        private Bounds(float x, float y, float w, float h) {
            this.x = x;
            this.y = y;
            this.w = w;
            this.h = h;
        }
    }
}
