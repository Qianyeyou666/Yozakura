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
        valueRenderer.updateDragging(mouseX);
        MaterialClickLayout layout = gui.layout();
        MaterialClickTheme theme = gui.theme();
        float maxScroll = maxScroll(layout);
        targetScroll = MaterialClickLayout.clamp(targetScroll, -maxScroll, 0.0f);
        scroll = gui.animate(scroll, targetScroll, 0.22f);

        float clipTop = clipTop(layout);
        float clipBottom = clipBottom(layout);
        float clipLeft = clipLeft(layout);
        gui.beginScissor(clipLeft, clipTop, layout.gridX + layout.gridW - clipLeft, clipBottom - clipTop);
        try {
            drawCards(mouseX, mouseY, clipTop, clipBottom);
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
        ModuleType type = gui.currentType();
        String title = type.toString();
        float headerTop = layout.y;
        float headerBottom = clipTop(layout);
        float titleHeight = FontLoaders.getFontRender(28).getStringHeight(title);
        float availableGap = Math.max(0.0f, headerBottom - headerTop - titleHeight);
        float titleY = headerTop + availableGap / 1.618f + 2.0f * layout.scale;
        FontLoaders.getFontRender(28).drawString(title, layout.contentX, titleY, theme.text());
    }

    private void drawCards(int mouseX, int mouseY, float clipTop, float clipBottom) {
        MaterialClickLayout layout = gui.layout();
        float s = layout.scale;
        float gap = 16.0f * s;
        float cardW = (layout.gridW - gap) / 2.0f;
        float leftY = firstCardY(layout);
        float rightY = leftY;
        List<Module> modules = modules();

        for (int i = 0; i < modules.size(); i++) {
            Module module = modules.get(i);
            float h = MaterialModuleCard.measure(gui, valueRenderer, module, cardW);
            boolean useLeft = i % 2 == 0;
            float x = useLeft ? layout.gridX : layout.gridX + cardW + gap;
            float y = useLeft ? leftY : rightY;
            if (y + h >= clipTop && y <= clipBottom) {
                new MaterialModuleCard(gui, valueRenderer, module, x, y, cardW, h).render(mouseX, mouseY);
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
        List<Module> modules = modules();

        for (int i = 0; i < modules.size(); i++) {
            Module module = modules.get(i);
            float h = MaterialModuleCard.measure(gui, valueRenderer, module, cardW);
            boolean useLeft = i % 2 == 0;
            float x = useLeft ? layout.gridX : layout.gridX + cardW + gap;
            float y = useLeft ? leftY : rightY;
            MaterialModuleCard card = new MaterialModuleCard(gui, valueRenderer, module, x, y, cardW, h);
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
        float topStart = clipTop;
        float topEnd = Math.min(clipBottom, topStart + featherH);
        float bottomEnd = clipBottom;
        float bottomStart = Math.max(clipTop, bottomEnd - featherH);
        float topStrength = MaterialClickLayout.clamp(-scroll / Math.max(1.0f, featherH), 0.0f, 1.0f);
        float bottomStrength = MaterialClickLayout.clamp((maxScroll + scroll) / Math.max(1.0f, featherH), 0.0f, 1.0f);
        if (cardsIntersect(layout, bottomStart, bottomEnd)) {
            bottomStrength = Math.max(bottomStrength, 1.0f);
        }
        float x1 = layout.gridX - FEATHER_OUTSET_X * s;
        float x2 = layout.gridX + layout.gridW + (FEATHER_OUTSET_X + 12.0f) * s;

        if (topStrength <= 0.01f && bottomStrength <= 0.01f) {
            return;
        }

        ShaderRenderer.invalidateFrostedGlass();
        if (topStrength > 0.01f) {
            ShaderRenderer.drawViewportFeatherBlur(x1, topStart, x2, topEnd, true, topStrength);
        }
        if (bottomStrength > 0.01f) {
            ShaderRenderer.drawViewportFeatherBlur(x1, bottomStart, x2, bottomEnd, false, bottomStrength);
        }
    }

    private float contentHeight() {
        MaterialClickLayout layout = gui.layout();
        float gap = 16.0f * layout.scale;
        float cardW = (layout.gridW - gap) / 2.0f;
        float leftH = 0.0f;
        float rightH = 0.0f;
        List<Module> modules = modules();
        for (int i = 0; i < modules.size(); i++) {
            Module module = modules.get(i);
            float cardH = MaterialModuleCard.measure(gui, valueRenderer, module, cardW);
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
        return layout.gridY + CARD_TOP_PADDING * layout.scale + scroll;
    }

    private float maxScroll(MaterialClickLayout layout) {
        return Math.max(0.0f, contentHeight() - (clipBottom(layout) - layout.gridY));
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

    private boolean cardsIntersect(MaterialClickLayout layout, float regionTop, float regionBottom) {
        if (regionBottom <= regionTop) {
            return false;
        }
        float s = layout.scale;
        float gap = 16.0f * s;
        float cardW = (layout.gridW - gap) / 2.0f;
        float leftY = firstCardY(layout);
        float rightY = leftY;
        List<Module> modules = modules();

        for (int i = 0; i < modules.size(); i++) {
            Module module = modules.get(i);
            float h = MaterialModuleCard.measure(gui, valueRenderer, module, cardW);
            boolean useLeft = i % 2 == 0;
            float y = useLeft ? leftY : rightY;
            if (y + h > regionTop && y < regionBottom) {
                return true;
            }
            if (useLeft) {
                leftY += h + gap;
            } else {
                rightY += h + gap;
            }
        }
        return false;
    }

    private boolean inExtendedGrid(MaterialClickLayout layout, float mouseX, float mouseY) {
        return MaterialClickLayout.contains(clipLeft(layout), clipTop(layout),
                layout.gridX + layout.gridW, clipBottom(layout), mouseX, mouseY);
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
