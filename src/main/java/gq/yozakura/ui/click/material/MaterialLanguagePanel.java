package gq.yozakura.ui.click.material;

import gq.yozakura.core.ClientLanguage;
import gq.yozakura.engine.font.FontLoaders;
import gq.yozakura.engine.render.ShaderRenderer;
import gq.yozakura.engine.render.ui.RenderServices;
import gq.yozakura.module.render.ClickGUI;

/**
 * Material ClickGUI 中独立的语言设置浮层。
 */
final class MaterialLanguagePanel {
    private static final float PANEL_W = 310.0f;
    private static final float PANEL_H = 166.0f;

    private final MaterialClickGui gui;
    private boolean open;

    MaterialLanguagePanel(MaterialClickGui gui) {
        this.gui = gui;
    }

    void open() {
        open = true;
    }

    void close() {
        open = false;
    }

    boolean isOpen() {
        return open;
    }

    void render(int mouseX, int mouseY) {
        if (!open) {
            return;
        }
        MaterialClickLayout layout = gui.layout();
        MaterialClickTheme theme = gui.theme();
        float s = layout.scale;
        Bounds bounds = bounds(layout);

        RenderServices.shapes().rounded(layout.x, layout.y, layout.x + layout.w, layout.y + layout.h,
                layout.radius, theme.withAlpha(0xFF000000, 102.0f * theme.alpha()));
        ShaderRenderer.invalidateFrostedGlass();
        RenderServices.liquidGlass().roundedBorder(bounds.x, bounds.y, bounds.x + bounds.w, bounds.y + bounds.h,
                18.0f * s, 1.0f * s,
                theme.withAlpha(MaterialClickTheme.SURFACE, 226.0f * theme.alpha()),
                theme.withAlpha(MaterialClickTheme.PRIMARY, 76.0f * theme.alpha()));

        float contentX = bounds.x + 20.0f * s;
        FontLoaders.F20.drawString(ClickGUI.languageText("Language", "语言"), contentX, bounds.y + 18.0f * s,
                theme.text());
        FontLoaders.C14.drawString(ClickGUI.languageText("Choose the interface language", "选择界面语言"),
                contentX, bounds.y + 44.0f * s, theme.muted());

        float dividerY = bounds.y + 62.0f * s;
        RenderServices.shapes().line(contentX, dividerY, bounds.x + bounds.w - 20.0f * s, dividerY,
                0.45f * s, theme.withAlpha(MaterialClickTheme.OUTLINE, 34.0f * theme.alpha()));
        FontLoaders.C14.drawString(ClickGUI.languageText("Mode", "模式"), contentX, bounds.y + 74.0f * s,
                theme.muted());

        float optionX = contentX;
        float optionW = bounds.w - 40.0f * s;
        float optionH = 20.0f * s;
        float englishY = bounds.y + 88.0f * s;
        float chineseY = englishY + 25.0f * s;
        drawOption(ClientLanguage.ENGLISH, optionX, englishY, optionW, optionH, mouseX, mouseY);
        drawOption(ClientLanguage.CHINESE, optionX, chineseY, optionW, optionH, mouseX, mouseY);

        FontLoaders.C14.drawString(ClickGUI.languageText("Changes apply immediately", "切换后立即生效"),
                contentX, bounds.y + 144.0f * s, theme.faint());
    }

    boolean mouseClicked(int mouseX, int mouseY, int button) {
        if (!open) {
            return false;
        }
        if (button != 0) {
            return true;
        }

        Bounds bounds = bounds(gui.layout());
        if (!MaterialClickLayout.contains(bounds.x, bounds.y, bounds.x + bounds.w, bounds.y + bounds.h, mouseX, mouseY)) {
            close();
            return true;
        }

        float s = gui.layout().scale;
        float optionX = bounds.x + 20.0f * s;
        float optionW = bounds.w - 40.0f * s;
        float optionH = 20.0f * s;
        float englishY = bounds.y + 88.0f * s;
        float chineseY = englishY + 25.0f * s;
        if (MaterialClickLayout.contains(optionX, englishY, optionX + optionW, englishY + optionH, mouseX, mouseY)) {
            ClickGUI.setLanguage(ClientLanguage.ENGLISH);
            return true;
        }
        if (MaterialClickLayout.contains(optionX, chineseY, optionX + optionW, chineseY + optionH, mouseX, mouseY)) {
            ClickGUI.setLanguage(ClientLanguage.CHINESE);
            return true;
        }
        return true;
    }

    private void drawOption(ClientLanguage language, float x, float y, float w, float h, int mouseX, int mouseY) {
        MaterialClickTheme theme = gui.theme();
        float s = gui.layout().scale;
        boolean active = ClickGUI.getLanguage() == language;
        boolean hovered = MaterialClickLayout.contains(x, y, x + w, y + h, mouseX, mouseY);
        float hover = gui.easedAnimation("language.panel.hover." + language.name(), hovered ? 1.0f : 0.0f,
                0.24f, 0.0f, gq.yozakura.util.animation.AnimationUtil.Ease.OUT_CUBIC);
        int fill = active ? MaterialClickTheme.PRIMARY_CONTAINER : 0xFFFFFFFF;
        float fillAlpha = active ? 142.0f : 18.0f + 16.0f * hover;
        int border = active ? MaterialClickTheme.PRIMARY : MaterialClickTheme.OUTLINE;
        float borderAlpha = active ? 82.0f : 14.0f + 16.0f * hover;
        RenderServices.shapes().roundedBorder(x, y, x + w, y + h, 7.0f * s, 0.55f * s,
                theme.withAlpha(fill, fillAlpha * theme.alpha()),
                theme.withAlpha(border, borderAlpha * theme.alpha()));
        String label = language.getDisplayName();
        float textY = y + Math.max(0.0f, h - FontLoaders.C14.getStringHeight(label)) / 2.0f + 0.5f * s;
        FontLoaders.C14.drawString(label, x + 9.0f * s, textY,
                active ? theme.withAlpha(MaterialClickTheme.ON_PRIMARY_CONTAINER, 255.0f * theme.alpha()) : theme.muted());
    }

    private Bounds bounds(MaterialClickLayout layout) {
        float s = layout.scale;
        float width = Math.min(PANEL_W * s, layout.contentW - 24.0f * s);
        float height = PANEL_H * s;
        return new Bounds(layout.contentX + (layout.contentW - width) / 2.0f,
                layout.y + (layout.h - height) / 2.0f, width, height);
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
