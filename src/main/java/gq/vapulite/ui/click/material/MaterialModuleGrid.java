package gq.vapulite.ui.click.material;

import gq.vapulite.engine.font.FontLoaders;
import gq.vapulite.engine.render.ui.RenderServices;
import gq.vapulite.manager.ModuleManager;
import gq.vapulite.module.Module;
import gq.vapulite.module.ModuleType;

import java.util.ArrayList;
import java.util.List;

/**
 * 右侧模块卡片网格。
 *
 * <p>按照参考 HTML 的 module-grid 做双列布局；卡片高度根据模块设置项动态增长，
 * 通过滚动区域保证所有模块和值都可访问。</p>
 */
final class MaterialModuleGrid {
    private final MaterialClickGui gui;
    private final MaterialValueRenderer valueRenderer;
    private float scroll;
    private float targetScroll;

    MaterialModuleGrid(MaterialClickGui gui) {
        this.gui = gui;
        this.valueRenderer = new MaterialValueRenderer(gui);
    }

    void resetScroll() {
        scroll = 0.0f;
        targetScroll = 0.0f;
        valueRenderer.closeDropdown();
    }

    void render(int mouseX, int mouseY) {
        valueRenderer.updateDragging(mouseX);
        MaterialClickLayout layout = gui.layout();
        MaterialClickTheme theme = gui.theme();
        float maxScroll = Math.max(0.0f, contentHeight() - layout.gridH);
        targetScroll = MaterialClickLayout.clamp(targetScroll, -maxScroll, 0.0f);
        scroll = gui.animate(scroll, targetScroll, 0.22f);

        drawHeader();
        gui.beginScissor(layout.gridX, layout.gridY, layout.gridW, layout.gridH);
        try {
            drawCards(mouseX, mouseY);
        } finally {
            gui.endScissor();
        }
        drawScrollbar(theme, maxScroll);
    }

    private void drawHeader() {
        MaterialClickLayout layout = gui.layout();
        MaterialClickTheme theme = gui.theme();
        ModuleType type = gui.currentType();
        FontLoaders.F24.drawString(type.toString(), layout.contentX, layout.contentY, theme.text());
    }

    private void drawCards(int mouseX, int mouseY) {
        MaterialClickLayout layout = gui.layout();
        float s = layout.scale;
        float gap = 16.0f * s;
        float cardW = (layout.gridW - gap) / 2.0f;
        float y = layout.gridY + scroll;
        List<Module> modules = modules();

        for (int i = 0; i < modules.size(); i += 2) {
            Module leftModule = modules.get(i);
            Module rightModule = i + 1 < modules.size() ? modules.get(i + 1) : null;
            float leftH = MaterialModuleCard.measure(gui, valueRenderer, leftModule, cardW);
            float rightH = rightModule == null ? 0.0f : MaterialModuleCard.measure(gui, valueRenderer, rightModule, cardW);
            float rowH = Math.max(leftH, rightH);

            if (y + rowH >= layout.gridY && y <= layout.gridY + layout.gridH) {
                new MaterialModuleCard(gui, valueRenderer, leftModule, layout.gridX, y, cardW, leftH)
                        .render(mouseX, mouseY);
                if (rightModule != null) {
                    new MaterialModuleCard(gui, valueRenderer, rightModule, layout.gridX + cardW + gap, y, cardW, rightH)
                            .render(mouseX, mouseY);
                }
            }
            y += rowH + gap;
        }
    }

    boolean mouseClicked(int mouseX, int mouseY, int button) {
        MaterialClickLayout layout = gui.layout();
        if (!layout.inGrid(mouseX, mouseY)) {
            valueRenderer.closeDropdown();
            return false;
        }
        float s = layout.scale;
        float gap = 16.0f * s;
        float cardW = (layout.gridW - gap) / 2.0f;
        float y = layout.gridY + scroll;
        List<Module> modules = modules();

        for (int i = 0; i < modules.size(); i += 2) {
            Module leftModule = modules.get(i);
            Module rightModule = i + 1 < modules.size() ? modules.get(i + 1) : null;
            float leftH = MaterialModuleCard.measure(gui, valueRenderer, leftModule, cardW);
            float rightH = rightModule == null ? 0.0f : MaterialModuleCard.measure(gui, valueRenderer, rightModule, cardW);
            float rowH = Math.max(leftH, rightH);

            MaterialModuleCard left = new MaterialModuleCard(gui, valueRenderer, leftModule, layout.gridX, y, cardW, leftH);
            if (left.mouseClicked(mouseX, mouseY, button)) {
                return true;
            }
            if (rightModule != null) {
                MaterialModuleCard right = new MaterialModuleCard(gui, valueRenderer, rightModule,
                        layout.gridX + cardW + gap, y, cardW, rightH);
                if (right.mouseClicked(mouseX, mouseY, button)) {
                    return true;
                }
            }
            y += rowH + gap;
        }
        valueRenderer.closeDropdown();
        return true;
    }

    void mouseWheel(int mouseX, int mouseY, int wheel) {
        MaterialClickLayout layout = gui.layout();
        if (!layout.inGrid(mouseX, mouseY) && !layout.inWindow(mouseX, mouseY)) {
            return;
        }
        targetScroll += wheel / 6.0f;
        float maxScroll = Math.max(0.0f, contentHeight() - layout.gridH);
        targetScroll = MaterialClickLayout.clamp(targetScroll, -maxScroll, 0.0f);
    }

    void mouseReleased() {
        valueRenderer.releaseDrag();
    }

    private void drawScrollbar(MaterialClickTheme theme, float maxScroll) {
        MaterialClickLayout layout = gui.layout();
        if (maxScroll <= 1.0f) {
            return;
        }
        float trackX = layout.gridX + layout.gridW + 8.0f * layout.scale;
        float trackY = layout.gridY;
        float trackH = layout.gridH;
        float thumbH = Math.max(34.0f * layout.scale, trackH * (trackH / (trackH + maxScroll)));
        float pct = -scroll / maxScroll;
        float thumbY = trackY + (trackH - thumbH) * pct;
        RenderServices.shapes().rounded(trackX, trackY, trackX + 3.0f * layout.scale, trackY + trackH,
                1.5f * layout.scale, theme.withAlpha(0xFFFFFFFF, 16.0f * theme.alpha()));
        RenderServices.shapes().rounded(trackX, thumbY, trackX + 3.0f * layout.scale, thumbY + thumbH,
                1.5f * layout.scale, theme.withAlpha(MaterialClickTheme.PRIMARY, 92.0f * theme.alpha()));
    }

    private float contentHeight() {
        MaterialClickLayout layout = gui.layout();
        float gap = 16.0f * layout.scale;
        float cardW = (layout.gridW - gap) / 2.0f;
        float h = 0.0f;
        List<Module> modules = modules();
        for (int i = 0; i < modules.size(); i += 2) {
            float leftH = MaterialModuleCard.measure(gui, valueRenderer, modules.get(i), cardW);
            float rightH = i + 1 < modules.size() ? MaterialModuleCard.measure(gui, valueRenderer, modules.get(i + 1), cardW) : 0.0f;
            h += Math.max(leftH, rightH);
            if (i + 2 < modules.size()) {
                h += gap;
            }
        }
        return h;
    }

    private List<Module> modules() {
        List<Module> output = new ArrayList<Module>();
        for (Module module : ModuleManager.getModulesInType(gui.currentType())) {
            if (module != null) {
                output.add(module);
            }
        }
        return output;
    }
}
