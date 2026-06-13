package gq.vapulite.ui.click.material;

import gq.vapulite.engine.font.FontLoaders;
import gq.vapulite.engine.render.ShaderRenderer;
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
    private static final float CARD_TOP_PADDING = 5.0f;
    private static final float CARD_BOTTOM_PADDING = 9.0f;
    private static final float CLIP_EXTEND_Y = 46.0f;
    private static final float CLIP_EXTEND_LEFT = 12.0f;
    private static final float TOP_HEADER_CLEARANCE = 26.0f;
    private static final float TOP_CLIP_RAISE = 28.0f;
    private static final float BOTTOM_CLIP_GUARD = 12.0f;
    private static final float FEATHER_HEIGHT = 45.0f;
    private static final float FEATHER_OUTSET_X = 28.0f;

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
        valueRenderer.updateDragging(mouseX, mouseY);
        MaterialClickLayout layout = gui.layout();
        MaterialClickTheme theme = gui.theme();
        List<Module> currentModules = modules(gui.currentType());
        float maxScroll = maxScroll(layout, currentModules);
        targetScroll = MaterialClickLayout.clamp(targetScroll, -maxScroll, 0.0f);
        scroll = gui.animate(scroll, targetScroll, 0.22f);

        float clipTop = clipTop(layout);
        float clipBottom = clipBottom(layout);
        float clipLeft = clipLeft(layout);
        gui.beginScissor(clipLeft, clipTop, layout.gridX + layout.gridW - clipLeft, clipBottom - clipTop);
        try {
            drawCards(currentModules, mouseX, mouseY, clipTop, clipBottom, scroll);
        } finally {
            gui.endScissor();
        }
        drawViewportFeather(maxScroll);
        drawHeader();
        drawScrollbar(theme, maxScroll);
    }

    private void drawHeader() {
        MaterialClickLayout layout = gui.layout();
        MaterialClickTheme theme = gui.theme();
        String title = gui.currentType().toString();
        float headerTop = layout.y;
        float headerBottom = clipTop(layout);
        float titleHeight = FontLoaders.getFontRender(28).getStringHeight(title);
        float availableGap = Math.max(0.0f, headerBottom - headerTop - titleHeight);
        float titleY = headerTop + availableGap / 1.618f + 2.0f * layout.scale;
        FontLoaders.getFontRender(28).drawString(title, layout.contentX, titleY,
                theme.text());
    }

    private void drawCards(List<Module> modules, int mouseX, int mouseY, float clipTop, float clipBottom,
                           float renderScroll) {
        if (modules == null || modules.isEmpty()) {
            return;
        }
        MaterialClickLayout layout = gui.layout();
        float s = layout.scale;
        float gap = 16.0f * s;
        float cardW = (layout.gridW - gap) / 2.0f;
        float leftY = firstCardY(layout, renderScroll);
        float rightY = leftY;
        for (int i = 0; i < modules.size(); i++) {
            Module module = modules.get(i);
            float valueH = valueRenderer.measure(module, cardW - 40.0f * s);
            float h = MaterialModuleCard.measure(gui, module, cardW, valueH);
            boolean useLeft = i % 2 == 0;
            float x = useLeft ? layout.gridX : layout.gridX + cardW + gap;
            float y = useLeft ? leftY : rightY;
            if (y + h >= clipTop && y <= clipBottom) {
                new MaterialModuleCard(gui, valueRenderer, module, x, y,
                        cardW, h, 1.0f, valueH).render(mouseX, mouseY);
            }
            if (useLeft) {
                leftY += h + gap;
            } else {
                rightY += h + gap;
            }
        }
    }

    boolean mouseClicked(int mouseX, int mouseY, int button) {
        MaterialClickLayout layout = gui.layout();
        if (!inExtendedGrid(layout, mouseX, mouseY)) {
            valueRenderer.closeDropdown();
            return false;
        }
        float s = layout.scale;
        float gap = 16.0f * s;
        float cardW = (layout.gridW - gap) / 2.0f;
        float leftY = firstCardY(layout);
        float rightY = leftY;
        List<Module> modules = modules(gui.currentType());

        for (int i = 0; i < modules.size(); i++) {
            Module module = modules.get(i);
            float valueH = valueRenderer.measure(module, cardW - 40.0f * s);
            float h = MaterialModuleCard.measure(gui, module, cardW, valueH);
            boolean useLeft = i % 2 == 0;
            float x = useLeft ? layout.gridX : layout.gridX + cardW + gap;
            float y = useLeft ? leftY : rightY;
            MaterialModuleCard card = new MaterialModuleCard(gui, valueRenderer, module, x, y, cardW, h, 1.0f, valueH);
            if (card.mouseClicked(mouseX, mouseY, button)) {
                return true;
            }
            if (useLeft) {
                leftY += h + gap;
            } else {
                rightY += h + gap;
            }
        }
        valueRenderer.closeDropdown();
        return true;
    }

    void mouseWheel(int mouseX, int mouseY, int wheel) {
        MaterialClickLayout layout = gui.layout();
        if (!inExtendedGrid(layout, mouseX, mouseY) && !layout.inWindow(mouseX, mouseY)) {
            return;
        }
        targetScroll += wheel / 6.0f;
        float maxScroll = maxScroll(layout);
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

    private void drawViewportFeather(float maxScroll) {
        if (maxScroll <= 1.0f) {
            return;
        }
        MaterialClickLayout layout = gui.layout();
        float s = layout.scale;
        float clipTop = clipTop(layout);
        float clipBottom = clipBottom(layout);
        float featherH = Math.min(FEATHER_HEIGHT * s, (clipBottom - clipTop) / 2.4f);
        float topStart = clipTop - 1f;
        float topEnd = Math.min(clipBottom, topStart + featherH);
        float bottomEnd = clipBottom + 1f;
        float bottomStart = Math.max(clipTop, bottomEnd - featherH);
        float topStrength = MaterialClickLayout.clamp(-scroll / Math.max(1.0f, featherH), 0.0f, 1.0f);
        float remainingScroll = Math.max(0.0f, maxScroll + scroll);
        float bottomStrength = remainingScroll <= 1.0f * s
                ? 0.0f
                : MaterialClickLayout.clamp((remainingScroll - 1.0f * s) / Math.max(1.0f, featherH), 0.0f, 1.0f);
        float x1 = layout.gridX - FEATHER_OUTSET_X * s;
        float x2 = layout.gridX + layout.gridW + (FEATHER_OUTSET_X) * s;

        if (topStrength <= 0.01f && bottomStrength <= 0.01f) {
            return;
        }

        if (gui.hasBindingOverlay()) {
            MaterialClickTheme theme = gui.theme();
            if (topStrength > 0.01f) {
                RenderServices.shapes().rounded(x1, topStart, x2, topEnd, 0.0f,
                        theme.withAlpha(MaterialClickTheme.SURFACE, 44.0f * theme.alpha() * topStrength));
            }
            if (bottomStrength > 0.01f) {
                RenderServices.shapes().rounded(x1, bottomStart, x2, bottomEnd, 0.0f,
                        theme.withAlpha(MaterialClickTheme.SURFACE, 44.0f * theme.alpha() * bottomStrength));
            }
            return;
        }

        if (topStrength > 0.01f) {
            ShaderRenderer.drawViewportFeatherBlur(x1, topStart, x2, topEnd, true, topStrength);
        }
        if (bottomStrength > 0.01f) {
            ShaderRenderer.drawViewportFeatherBlur(x1, bottomStart, x2, bottomEnd, false, bottomStrength);
        }
    }

    private float contentHeight(List<Module> modules) {
        MaterialClickLayout layout = gui.layout();
        float gap = 16.0f * layout.scale;
        float cardW = (layout.gridW - gap) / 2.0f;
        float leftH = 0.0f;
        float rightH = 0.0f;
        for (int i = 0; i < modules.size(); i++) {
            Module module = modules.get(i);
            float valueH = valueRenderer.measure(module, cardW - 40.0f * layout.scale);
            float cardH = MaterialModuleCard.measure(gui, module, cardW, valueH);
            if (i % 2 == 0) {
                leftH += cardH + gap;
            } else {
                rightH += cardH + gap;
            }
        }
        float cardsH = Math.max(0.0f, Math.max(leftH, rightH) - gap);
        return CARD_TOP_PADDING * layout.scale + cardsH + CARD_BOTTOM_PADDING * layout.scale;
    }

    private float firstCardY(MaterialClickLayout layout) {
        return firstCardY(layout, scroll);
    }

    private float firstCardY(MaterialClickLayout layout, float renderScroll) {
        return layout.gridY + CARD_TOP_PADDING * layout.scale + renderScroll;
    }

    private float maxScroll(MaterialClickLayout layout) {
        return maxScroll(layout, modules(gui.currentType()));
    }

    private float maxScroll(MaterialClickLayout layout, List<Module> modules) {
        return Math.max(0.0f, contentHeight(modules) - (clipBottom(layout) - layout.gridY));
    }

    private float clipTop(MaterialClickLayout layout) {
        float s = layout.scale;
        return Math.max(layout.contentY + TOP_HEADER_CLEARANCE * s, layout.gridY - TOP_CLIP_RAISE * s);
    }

    private float clipBottom(MaterialClickLayout layout) {
        return Math.min(layout.y + layout.h - BOTTOM_CLIP_GUARD * layout.scale,
                layout.gridY + layout.gridH + CLIP_EXTEND_Y * layout.scale);
    }

    private float clipLeft(MaterialClickLayout layout) {
        return layout.gridX - CLIP_EXTEND_LEFT * layout.scale;
    }


    private boolean inExtendedGrid(MaterialClickLayout layout, float mouseX, float mouseY) {
        return MaterialClickLayout.contains(clipLeft(layout), clipTop(layout),
                layout.gridX + layout.gridW, clipBottom(layout), mouseX, mouseY);
    }

    private List<Module> modules(ModuleType type) {
        List<Module> output = new ArrayList<Module>();
        for (Module module : ModuleManager.getModulesInType(type)) {
            if (module != null) {
                output.add(module);
            }
        }
        return output;
    }
}
