package gq.yozakura.ui.click.material;

import gq.yozakura.engine.font.FontLoaders;
import gq.yozakura.engine.render.ui.RenderServices;
import gq.yozakura.module.ModuleType;
import gq.yozakura.module.render.ClickGUI;
import gq.yozakura.util.animation.AnimationUtil;

/**
 * 左侧分类导航栏。
 *
 * <p>对应参考 HTML 的 sidebar/nav-item 区域，负责分类切换和分类统计。</p>
 */
final class MaterialClickSidebar {
    private static final ModuleType[] TABS = new ModuleType[]{
            ModuleType.Combat, ModuleType.Movement, ModuleType.Render, ModuleType.Player,
            ModuleType.World, ModuleType.Config, ModuleType.Other
    };

    private final MaterialClickGui gui;

    MaterialClickSidebar(MaterialClickGui gui) {
        this.gui = gui;
    }

    void render(int mouseX, int mouseY) {
        MaterialClickLayout layout = gui.layout();
        MaterialClickTheme theme = gui.theme();
        float s = layout.scale;
        float navX = layout.x + 16.0f * s;
        float brandX = layout.x + 32.0f * s;
        float brandY = layout.y + 32.0f * s;

        RenderServices.shapes().rounded(brandX, brandY + 4.0f * s, brandX + 8.0f * s, brandY + 12.0f * s,
                4.0f * s, theme.withAlpha(MaterialClickTheme.PRIMARY, 255.0f * theme.alpha()));
        FontLoaders.F30.drawString("Yozakura", brandX + 17.0f * s, brandY - 3.0f * s, theme.text());

        float itemY = layout.y + 92.0f * s;
        float itemW = layout.sidebarW - 32.0f * s;
        drawActiveIndicator(navX, itemY, itemW, 44.0f * s);
        for (ModuleType type : TABS) {
            drawTab(type, navX, itemY, itemW, 44.0f * s, mouseX, mouseY);
            itemY += 50.0f * s;
        }
        drawLanguageButton(navX, layout.y + layout.h - 64.0f * s, itemW, 44.0f * s, mouseX, mouseY);
    }

    private void drawActiveIndicator(float x, float firstY, float w, float h) {
        MaterialClickLayout layout = gui.layout();
        MaterialClickTheme theme = gui.theme();
        int index = activeIndex();
        if (index < 0) {
            return;
        }
        float targetY = firstY + index * 50.0f * layout.scale;
        float indicatorY = gui.animation("sidebar.indicator.y", targetY, 0.28f, targetY);
        float alpha = gui.easedAnimation("sidebar.indicator.alpha", 1.0f, 0.22f, 0.0f, AnimationUtil.Ease.OUT_CUBIC);
        RenderServices.shapes().rounded(x, indicatorY, x + w, indicatorY + h, h / 2.0f,
                theme.withAlpha(MaterialClickTheme.PRIMARY_CONTAINER, 255.0f * theme.alpha() * alpha));
    }

    private void drawTab(ModuleType type, float x, float y, float w, float h, int mouseX, int mouseY) {
        MaterialClickLayout layout = gui.layout();
        MaterialClickTheme theme = gui.theme();
        boolean active = gui.currentType() == type;
        boolean hovered = MaterialClickLayout.contains(x, y, x + w, y + h, mouseX, mouseY);
        float activeProgress = gui.easedAnimation("sidebar.active." + type.name(), active ? 1.0f : 0.0f,
                0.24f, 0.0f, AnimationUtil.Ease.OUT_CUBIC);
        float hover = gui.easedAnimation("sidebar.hover." + type.name(), hovered && !active ? 1.0f : 0.0f,
                0.28f, 0.0f, AnimationUtil.Ease.OUT_CUBIC);

        if (hover > 0.01f) {
            RenderServices.shapes().rounded(x, y, x + w, y + h, h / 2.0f,
                    theme.withAlpha(0xFFFFFFFF, 12.0f * theme.alpha() * hover));
        }

        String label = type.getName();
        int inactiveColor = theme.blend(MaterialClickTheme.TEXT, MaterialClickTheme.PRIMARY, hover);
        int color = theme.withAlpha(theme.blend(inactiveColor, MaterialClickTheme.ON_PRIMARY_CONTAINER, activeProgress),
                255.0f * theme.alpha());
        float textX = x + 18.0f * layout.scale;
        if (activeProgress > 0.5f) {
            float textY = y + Math.max(0.0f, h - FontLoaders.TB18.getStringHeight(label)) / 2.0f + 1.0f * layout.scale;
            FontLoaders.TB18.drawString(label, textX, textY, color);
        } else {
            float textY = y + Math.max(0.0f, h - FontLoaders.F18.getStringHeight(label)) / 2.0f + 1.0f * layout.scale;
            FontLoaders.F18.drawString(label, textX, textY, color);
        }
    }

    private int activeIndex() {
        for (int i = 0; i < TABS.length; i++) {
            if (gui.currentType() == TABS[i]) {
                return i;
            }
        }
        return -1;
    }

    private void drawLanguageButton(float x, float y, float w, float h, int mouseX, int mouseY) {
        MaterialClickLayout layout = gui.layout();
        MaterialClickTheme theme = gui.theme();
        boolean open = gui.isLanguagePanelOpen();
        boolean hovered = MaterialClickLayout.contains(x, y, x + w, y + h, mouseX, mouseY);
        float hover = gui.easedAnimation("sidebar.language.hover", hovered && !open ? 1.0f : 0.0f,
                0.28f, 0.0f, AnimationUtil.Ease.OUT_CUBIC);
        float active = gui.easedAnimation("sidebar.language.active", open ? 1.0f : 0.0f,
                0.24f, 0.0f, AnimationUtil.Ease.OUT_CUBIC);
        if (active > 0.01f || hover > 0.01f) {
            int fill = theme.blend(0xFFFFFFFF, MaterialClickTheme.PRIMARY_CONTAINER, active);
            float alpha = (12.0f * hover + 255.0f * active) * theme.alpha();
            RenderServices.shapes().rounded(x, y, x + w, y + h, h / 2.0f, theme.withAlpha(fill, alpha));
        }

        String label = ClickGUI.languageText("Settings", "设置");
        int color = theme.withAlpha(theme.blend(MaterialClickTheme.TEXT, MaterialClickTheme.ON_PRIMARY_CONTAINER, active),
                255.0f * theme.alpha());
        String icon = FontLoaders.ICON_SETTINGS;
        float iconY = y + Math.max(0.0f, h - FontLoaders.I16.getStringHeight(icon)) / 2.0f + 0.5f * layout.scale;
        FontLoaders.I16.drawString(icon, x + 18.0f * layout.scale, iconY, color);
        float textY = y + Math.max(0.0f, h - FontLoaders.F18.getStringHeight(label)) / 2.0f + 1.0f * layout.scale;
        FontLoaders.F18.drawString(label, x + 42.0f * layout.scale, textY, color);
    }

    boolean mouseClicked(int mouseX, int mouseY, int button) {
        if (button != 0) {
            return false;
        }
        MaterialClickLayout layout = gui.layout();
        float s = layout.scale;
        float x = layout.x + 16.0f * s;
        float y = layout.y + 92.0f * s;
        float w = layout.sidebarW - 32.0f * s;
        for (ModuleType type : TABS) {
            if (MaterialClickLayout.contains(x, y, x + w, y + 44.0f * s, mouseX, mouseY)) {
                gui.setCurrentType(type);
                return true;
            }
            y += 50.0f * s;
        }
        float languageY = layout.y + layout.h - 64.0f * s;
        if (MaterialClickLayout.contains(x, languageY, x + w, languageY + 44.0f * s, mouseX, mouseY)) {
            gui.openLanguagePanel();
            return true;
        }
        return false;
    }
}
