package gq.vapulite.ui.click.material;

import gq.vapulite.engine.font.FontLoaders;
import gq.vapulite.engine.render.ShaderRenderer;
import gq.vapulite.engine.render.ui.RenderServices;
import gq.vapulite.manager.ModuleManager;
import gq.vapulite.module.Module;
import gq.vapulite.module.ModuleType;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

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
    private final List<CardLayout> cardLayouts = new ArrayList<CardLayout>();
    private final Map<ModuleType, List<Module>> moduleCache = new EnumMap<ModuleType, List<Module>>(ModuleType.class);
    private float lastMaxScroll;
    private boolean lastMaxScrollValid;

    MaterialModuleGrid(MaterialClickGui gui) {
        this.gui = gui;
        this.valueRenderer = new MaterialValueRenderer(gui);
    }

    void resetScroll() {
        scroll = 0.0f;
        targetScroll = 0.0f;
        lastMaxScroll = 0.0f;
        lastMaxScrollValid = false;
        valueRenderer.closeDropdown();
    }

    void render(int mouseX, int mouseY) {
        valueRenderer.updateDragging(mouseX, mouseY);
        MaterialClickLayout layout = gui.layout();
        MaterialClickTheme theme = gui.theme();
        List<Module> currentModules = modules(gui.currentType());
        gui.prepareModuleAnimations(currentModules);
        float maxScroll = rebuildCardLayouts(layout, currentModules);
        lastMaxScroll = maxScroll;
        lastMaxScrollValid = true;
        targetScroll = MaterialClickLayout.clamp(targetScroll, -maxScroll, 0.0f);
        scroll = gui.animate(scroll, targetScroll, 0.22f);

        float clipTop = clipTop(layout);
        float clipBottom = clipBottom(layout);
        float clipLeft = clipLeft(layout);
        gui.beginScissor(clipLeft, clipTop, layout.gridX + layout.gridW - clipLeft, clipBottom - clipTop);
        try {
            drawCards(mouseX, mouseY, clipTop, clipBottom, scroll);
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

    private void drawCards(int mouseX, int mouseY, float clipTop, float clipBottom,
                           float renderScroll) {
        if (cardLayouts.isEmpty()) {
            return;
        }
        MaterialClickLayout layout = gui.layout();
        float baseY = firstCardY(layout, renderScroll);
        for (int i = 0; i < cardLayouts.size(); i++) {
            CardLayout card = cardLayouts.get(i);
            float y = baseY + card.yOffset;
            if (y + card.h >= clipTop && y <= clipBottom) {
                card.card.setLayout(card.x, y, card.w, card.h, 1.0f, card.valueH);
                card.card.render(mouseX, mouseY);
            }
        }
    }

    boolean mouseClicked(int mouseX, int mouseY, int button) {
        MaterialClickLayout layout = gui.layout();
        if (!inExtendedGrid(layout, mouseX, mouseY)) {
            valueRenderer.closeDropdown();
            return false;
        }
        List<Module> modules = modules(gui.currentType());
        rebuildCardLayouts(layout, modules);
        float baseY = firstCardY(layout);

        for (int i = 0; i < cardLayouts.size(); i++) {
            CardLayout card = cardLayouts.get(i);
            float y = baseY + card.yOffset;
            card.card.setLayout(card.x, y, card.w, card.h, 1.0f, card.valueH);
            if (card.card.mouseClicked(mouseX, mouseY, button)) {
                return true;
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
        if (lastMaxScrollValid) {
            targetScroll = MaterialClickLayout.clamp(targetScroll, -lastMaxScroll, 0.0f);
        } else if (targetScroll > 0.0f) {
            targetScroll = 0.0f;
        }
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

        ShaderRenderer.prepareViewportFeatherBlur();
        if (topStrength > 0.01f) {
            ShaderRenderer.drawViewportFeatherBlur(x1, topStart, x2, topEnd, true, topStrength);
        }
        if (bottomStrength > 0.01f) {
            ShaderRenderer.drawViewportFeatherBlur(x1, bottomStart, x2, bottomEnd, false, bottomStrength);
        }
    }

    private float rebuildCardLayouts(MaterialClickLayout layout, List<Module> modules) {
        float s = layout.scale;
        float gap = 16.0f * s;
        float cardW = (layout.gridW - gap) / 2.0f;
        float leftY = 0.0f;
        float rightY = 0.0f;
        int count = modules == null ? 0 : modules.size();
        for (int i = 0; i < count; i++) {
            Module module = modules.get(i);
            valueRenderer.prepare(module);
            float valueH = valueRenderer.measure(module, cardW - 40.0f * s);
            float h = MaterialModuleCard.measure(gui, module, cardW, valueH);
            boolean useLeft = i % 2 == 0;
            float x = useLeft ? layout.gridX : layout.gridX + cardW + gap;
            float yOffset = useLeft ? leftY : rightY;
            CardLayout card = cardLayoutAt(i, module);
            card.update(x, yOffset, cardW, h, valueH);
            if (useLeft) {
                leftY += h + gap;
            } else {
                rightY += h + gap;
            }
        }
        trimCardLayouts(count);
        float cardsH = Math.max(0.0f, Math.max(leftY, rightY) - gap);
        float contentHeight = CARD_TOP_PADDING * s + cardsH + CARD_BOTTOM_PADDING * s;
        return maxScrollFromContentHeight(layout, contentHeight);
    }

    private CardLayout cardLayoutAt(int index, Module module) {
        if (index < cardLayouts.size()) {
            CardLayout card = cardLayouts.get(index);
            if (card.module == module) {
                return card;
            }
            card = new CardLayout(gui, valueRenderer, module);
            cardLayouts.set(index, card);
            return card;
        }
        CardLayout card = new CardLayout(gui, valueRenderer, module);
        cardLayouts.add(card);
        return card;
    }

    private void trimCardLayouts(int count) {
        for (int i = cardLayouts.size() - 1; i >= count; i--) {
            cardLayouts.remove(i);
        }
    }

    private float maxScrollFromContentHeight(MaterialClickLayout layout, float contentHeight) {
        return Math.max(0.0f, contentHeight - (clipBottom(layout) - layout.gridY));
    }

    private float firstCardY(MaterialClickLayout layout) {
        return firstCardY(layout, scroll);
    }

    private float firstCardY(MaterialClickLayout layout, float renderScroll) {
        return layout.gridY + CARD_TOP_PADDING * layout.scale + renderScroll;
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

    private static final class CardLayout {
        private final Module module;
        private final MaterialModuleCard card;
        private float x;
        private float yOffset;
        private float w;
        private float h;
        private float valueH;

        private CardLayout(MaterialClickGui gui, MaterialValueRenderer valueRenderer, Module module) {
            this.module = module;
            this.card = new MaterialModuleCard(gui, valueRenderer, module, 0.0f, 0.0f, 0.0f, 0.0f, 1.0f, 0.0f);
        }

        private void update(float x, float yOffset, float w, float h, float valueH) {
            this.x = x;
            this.yOffset = yOffset;
            this.w = w;
            this.h = h;
            this.valueH = valueH;
        }
    }

    private List<Module> modules(ModuleType type) {
        List<Module> cached = moduleCache.get(type);
        if (cached != null) {
            return cached;
        }
        List<Module> output = new ArrayList<Module>();
        for (Module module : ModuleManager.getModulesInType(type)) {
            if (module != null) {
                output.add(module);
            }
        }
        moduleCache.put(type, output);
        return output;
    }
}
