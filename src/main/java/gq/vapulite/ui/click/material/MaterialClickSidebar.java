package gq.vapulite.ui.click.material;

import gq.vapulite.engine.font.FontLoaders;
import gq.vapulite.engine.render.ui.RenderServices;
import gq.vapulite.module.ModuleType;

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
        FontLoaders.F30.drawString("VapuLite", brandX + 17.0f * s, brandY - 3.0f * s, theme.text());

        float itemY = layout.y + 92.0f * s;
        float itemW = layout.sidebarW - 32.0f * s;
        for (ModuleType type : TABS) {
            drawTab(type, navX, itemY, itemW, 44.0f * s, mouseX, mouseY);
            itemY += 50.0f * s;
        }
    }

    private void drawTab(ModuleType type, float x, float y, float w, float h, int mouseX, int mouseY) {
        MaterialClickLayout layout = gui.layout();
        MaterialClickTheme theme = gui.theme();
        boolean active = gui.currentType() == type;
        boolean hovered = MaterialClickLayout.contains(x, y, x + w, y + h, mouseX, mouseY);
        float hover = hovered && !active ? 1.0f : 0.0f;

        if (active) {
            RenderServices.shapes().rounded(x, y, x + w, y + h, h / 2.0f,
                    theme.withAlpha(MaterialClickTheme.PRIMARY_CONTAINER, 255.0f * theme.alpha()));
        } else if (hover > 0.0f) {
            RenderServices.shapes().rounded(x, y, x + w, y + h, h / 2.0f,
                    theme.withAlpha(0xFFFFFFFF, 10.0f * theme.alpha()));
        }

        String label = type.toString();
        int color = active ? theme.withAlpha(MaterialClickTheme.ON_PRIMARY_CONTAINER, 255.0f * theme.alpha()) : theme.muted();
        if (active) {
            float textY = y + Math.max(0.0f, h - FontLoaders.TB18.getStringHeight(label)) / 2.0f + 1.0f * layout.scale;
            FontLoaders.TB18.drawString(label, x + 18.0f * layout.scale, textY, color);
        } else {
            float textY = y + Math.max(0.0f, h - FontLoaders.F18.getStringHeight(label)) / 2.0f + 1.0f * layout.scale;
            FontLoaders.F18.drawString(label, x + 18.0f * layout.scale, textY, color);
        }
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
        return false;
    }
}
