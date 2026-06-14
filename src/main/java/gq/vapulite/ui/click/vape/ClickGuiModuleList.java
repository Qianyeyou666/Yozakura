package gq.vapulite.ui.click.vape;

import gq.vapulite.manager.ModuleManager;
import gq.vapulite.module.Module;
import gq.vapulite.engine.font.FontLoaders;
import gq.vapulite.engine.render.ui.RenderServices;
import gq.vapulite.ui.click.ClickGuiIcons;
import gq.vapulite.ui.UiPanel;
import gq.vapulite.ui.UiTheme;
import org.lwjgl.input.Mouse;

import java.awt.Color;
import java.util.List;

/**
 * ClickGUI 模块列表组件，负责左侧模块卡片列表的渲染与交互。
 * <p>
 * 功能包括：
 * <ul>
 *   <li>渲染模块卡片（图标、名称、描述、开关、收藏星标）</li>
 *   <li>处理鼠标点击（选择模块、切换开关、收藏/取消收藏、中键绑定）</li>
 *   <li>处理滚轮滚动（列表滚动）</li>
 *   <li>处理滚动条拖拽</li>
 * </ul>
 * 包级私有（package-private），仅供 {@link VapeClickGui} 内部使用。
 */
final class ClickGuiModuleList {
    /** 关联的主 GUI 实例 */
    private final VapeClickGui gui;

    ClickGuiModuleList(VapeClickGui gui) {
        this.gui = gui;
    }

    /** 更新 UI 主题 */
    void updateTheme(UiTheme theme) {
    }

    /**
     * 渲染模块列表。
     * <p>
     * 绘制面板背景，然后在裁剪区域内渲染每个可见模块的卡片。
     * 包含入场动画的错位效果（stagger）和滚动条。
     */
    void render(int mouseX, int mouseY, float introY) {
        // 平滑滚动动画
        gui.listScroll = gui.animate(gui.listScroll, gui.targetListScroll, 0.12f);
        float listHeight = gui.getListHeight();
        float modulePanelHeight = gui.getModulePanelHeight();
        float panelY = gui.contentY + introY;
        // 绘制列表面板背景
        RenderServices.shapes().shadow(gui.contentX, panelY, gui.contentX + VapeClickGui.CARD_W,
                panelY + modulePanelHeight, VapeClickGui.PANEL_RADIUS,
                gui.withAlpha(gui.shadowColor(220), 88.0f * gui.guiAlpha), 9, 6.0f);
        gui.drawPanelGlass(gui.contentX, panelY, gui.contentX + VapeClickGui.CARD_W, panelY + modulePanelHeight,
                VapeClickGui.PANEL_RADIUS, 1.0f,
                gui.withAlpha(gui.guiColors().glassFill, gui.getAlpha(gui.guiColors().glassFill) * gui.guiAlpha),
                gui.withAlpha(gui.guiColors().glassBorder, gui.getAlpha(gui.guiColors().glassBorder) * gui.guiAlpha));

        // 在裁剪区域内渲染模块卡片
        float drawContentY = gui.getModuleListY() + introY;
        gui.beginScissor(gui.contentX + 8.0f, drawContentY, VapeClickGui.CARD_W - 16.0f, listHeight);
        try {
            float rowY = drawContentY + gui.listScroll;
            List<Module> modules = gui.getVisibleModules();
            for (int i = 0; i < modules.size(); i++) {
                Module module = modules.get(i);
                // 仅渲染可见范围内的卡片（视口裁剪优化）
                if (rowY + VapeClickGui.CARD_H >= drawContentY - 2 && rowY <= drawContentY + listHeight + 2) {
                    // 错位入场动画：每个后续卡片延迟出现
                    float stagger = Math.min(1.0f, Math.max(0.0f, gui.contentFade - i * 0.035f));
                    float eased = gui.easeSmooth(gui.easeOut(stagger));
                    drawModuleCard(module, gui.contentX + 10.0f, rowY + (1.0f - eased) * 8.0f,
                            VapeClickGui.CARD_H, mouseX, mouseY, eased);
                }
                rowY += VapeClickGui.CARD_H + 6.0f;
            }
        } finally {
            gui.endScissor();
        }
        // 绘制底部模块计数和滚动条
        drawModuleCount(panelY, modulePanelHeight);
        drawScrollbar(drawContentY, listHeight);
    }

    /**
     * 处理鼠标点击事件。
     * <p>
     * 点击行为优先级：中键绑定 > 收藏星标 > 开关 > 右键切换 > 左键选中。
     */
    boolean mouseClicked(int mouseX, int mouseY, int mouseButton) {
        // 检查鼠标是否在列表区域内
        if (!VapeClickGui.isHovered(gui.contentX, gui.getModuleListY(), gui.contentX + VapeClickGui.CARD_W,
                gui.getModuleListY() + gui.getListHeight(), mouseX, mouseY)) {
            return false;
        }
        float rowY = gui.getModuleListY() + gui.listScroll;
        List<Module> modules = gui.getVisibleModules();
        for (int i = 0; i < modules.size(); i++) {
            Module module = modules.get(i);
            float x = gui.contentX + 10.0f;
            if (VapeClickGui.isHovered(x, rowY, x + getRowWidth(),
                    rowY + VapeClickGui.CARD_H, mouseX, mouseY)) {
                if (mouseButton == 2) {
                    // 中键：开始按键绑定
                    gui.startBinding(module);
                    return true;
                }
                if (mouseButton == 0 && isStarHit(x, rowY, mouseX, mouseY)) {
                    // 点击星标：切换收藏状态
                    if (gui.favoriteModules.contains(module)) {
                        gui.favoriteModules.remove(module);
                    } else {
                        gui.favoriteModules.add(module);
                    }
                    gui.clickProgress.put(module, 1.0f);
                    return true;
                }
                if (mouseButton == 0 && gui.isSwitchHit(gui.getModuleSwitchX(x), gui.getModuleSwitchY(rowY), mouseX, mouseY)) {
                    // 点击开关：切换模块状态
                    module.setState(!module.getState());
                    gui.clickProgress.put(module, 1.0f);
                    return true;
                }
                if (mouseButton == 1) {
                    // 右键：快速切换模块状态
                    module.setState(!module.getState());
                    gui.clickProgress.put(module, 1.0f);
                    return true;
                }
                if (mouseButton == 0) {
                    // 左键：选中模块
                    VapeClickGui.selectModule(module);
                    gui.clickProgress.put(module, 1.0f);
                }
                gui.targetListScroll = gui.clamp(gui.targetListScroll,
                        -Math.max(0.0f, gui.getContentHeight() - gui.getListHeight()), 0.0f);
                return true;
            }
            rowY += VapeClickGui.CARD_H + 6.0f;
        }
        return false;
    }

    /**
     * 处理鼠标滚轮滚动。
     */
    boolean updateScroll(int mouseX, int mouseY, int wheel) {
        if (wheel == 0 || !VapeClickGui.isHovered(gui.contentX, gui.getModuleListY(),
                gui.contentX + VapeClickGui.CARD_W, gui.getModuleListY() + gui.getListHeight(), mouseX, mouseY)) {
            return false;
        }
        gui.targetListScroll += wheel > 0 ? 34.0f : -34.0f;
        gui.targetListScroll = gui.clamp(gui.targetListScroll,
                -Math.max(0.0f, gui.getContentHeight() - gui.getListHeight()), 0.0f);
        return true;
    }

    /**
     * 处理滚动条点击（开始拖拽）。
     */
    boolean handleScrollbarClick(int mouseX, int mouseY, int mouseButton) {
        if (mouseButton != 0) {
            return false;
        }
        VapeClickGui.ScrollbarMetrics metrics = gui.getScrollbarMetrics(gui.getModuleListY(), gui.getListHeight());
        if (!metrics.visible) {
            return false;
        }
        boolean onTrack = VapeClickGui.isHovered(metrics.trackX - 4.0f, metrics.trackY, metrics.trackX + 6.0f,
                metrics.trackY + metrics.trackHeight, mouseX, mouseY);
        if (!onTrack) {
            return false;
        }
        // 判断是点击在滑块上（精确定位）还是轨道上（跳到点击位置）
        boolean onThumb = VapeClickGui.isHovered(metrics.trackX - 4.0f, metrics.thumbY, metrics.trackX + 6.0f,
                metrics.thumbY + metrics.thumbHeight, mouseX, mouseY);
        gui.draggingScrollbar = true;
        gui.scrollbarDragOffset = onThumb ? mouseY - metrics.thumbY : metrics.thumbHeight / 2.0f;
        updateScrollbarDrag(mouseY);
        return true;
    }

    /**
     * 更新滚动条拖拽位置。
     * <p>
     * 根据鼠标 Y 坐标计算滑块位置并更新列表滚动偏移。
     */
    void updateScrollbarDrag(int mouseY) {
        if (!gui.draggingScrollbar) {
            return;
        }
        // 鼠标释放时停止拖拽
        if (!Mouse.isButtonDown(0)) {
            gui.draggingScrollbar = false;
            return;
        }
        VapeClickGui.ScrollbarMetrics metrics = gui.getScrollbarMetrics(gui.getModuleListY(), gui.getListHeight());
        if (!metrics.visible) {
            gui.draggingScrollbar = false;
            return;
        }
        // 计算滑块顶部位置并映射到滚动偏移
        float thumbTop = gui.clamp(mouseY - gui.scrollbarDragOffset, metrics.trackY,
                metrics.trackY + metrics.trackHeight - metrics.thumbHeight);
        float pct = (thumbTop - metrics.trackY) / Math.max(1.0f, metrics.trackHeight - metrics.thumbHeight);
        gui.targetListScroll = -metrics.maxScroll * pct;
        gui.listScroll = gui.animate(gui.listScroll, gui.targetListScroll, 0.38f);
    }

    /**
     * 绘制单个模块卡片。
     * <p>
     * 卡片样式根据状态变化：
     * <ul>
     *   <li>选中：高亮填充 + 紫色发光阴影</li>
     *   <li>启用未选中：带主题色的半透明背景</li>
     *   <li>禁用未选中：无特殊背景</li>
     * </ul>
     */
    private void drawModuleCard(Module module, float x, float y, float height, int mouseX, int mouseY, float alpha) {
        boolean selected = gui.selectedModule == module;
        float rowW = getRowWidth();
        boolean hovered = VapeClickGui.isHovered(x, y, x + rowW, y + height, mouseX, mouseY);
        float hover = gui.animateMap(gui.hoverProgress, module, hovered && !gui.closing ? 1.0f : 0.0f, 0.16f);
        float click = gui.animateMap(gui.clickProgress, module, 0.0f, 0.22f);
        // 选中动画：选中时从 0→1，取消选中时从 1→0
        float selectRaw = gui.animateMap(gui.selectProgress, module, selected ? 1.0f : 0.0f, 0.14f);
        float selectAnim = gui.easeSmooth(selectRaw);
        // 选中或悬停时的阴影效果
        if (selectAnim > 0.02f || hover > 0.04f) {
            int shadowColor = gui.blendColor(
                    gui.shadowColor(190),
                    gui.guiColors().accent,
                    selectAnim);
            float shadowStrength = (18.0f + hover * 12.0f) * (1.0f - selectAnim) + 42.0f * selectAnim;
            RenderServices.shapes().shadow(x, y, x + rowW, y + height, 7.0f,
                    gui.withAlpha(shadowColor, shadowStrength * alpha * gui.guiAlpha), 5, 3.0f);
        }
        // 点击时的脉冲阴影
        if (click > 0.02f) {
            RenderServices.shapes().shadow(x, y, x + rowW, y + height, 7.0f,
                    gui.withAlpha(gui.guiColors().accent, (14.0f + click * 36.0f) * alpha * gui.guiAlpha), 5, 2.8f);
        }
        boolean enabled = module.getState();
        // 选中动画：从默认背景渐变到选中背景
        if (selectAnim > 0.005f) {
            float fillAlpha = 232.0f * selectAnim * alpha * gui.guiAlpha;
            float borderAlpha = 78.0f * selectAnim * alpha * gui.guiAlpha;
            gui.drawThemedGlass(x, y, x + rowW, y + height, 7.0f, 1.2f,
                    gui.withAlpha(gui.guiColors().detailSelectedFill, fillAlpha),
                    gui.withAlpha(gui.guiColors().accent, borderAlpha));
        }
        if (selectAnim < 0.995f && enabled) {
            // 启用状态背景：随着选中动画增加而逐渐消失
            float enabledAlpha = 46.0f * (1.0f - selectAnim) * alpha * gui.guiAlpha;
            int accent = gui.guiColors().accent;
            boolean light = gq.vapulite.module.render.HUD.isLightTheme() || gq.vapulite.module.render.HUD.isSakuraTheme();
            int bgColor = light
                    ? gui.blendColor(accent, 0xFFFFFFFF, 0.55f)
                    : gui.blendColor(accent, 0xFF000000, 0.55f);
            RenderServices.shapes().rounded(x, y, x + rowW, y + height, VapeClickGui.CARD_RADIUS,
                    gui.withAlpha(bgColor, enabledAlpha));
        }
        // 悬停高亮（非选中状态）
        if (selectAnim < 0.8f && hover > 0.01f) {
            int hoverFill = gui.blendColor(new Color(0, 0, 0, 0).getRGB(), gui.guiColors().navDefaultHover, hover);
            float hoverAlpha = gui.getAlpha(hoverFill) * (1.0f - selectAnim) * alpha * gui.guiAlpha;
            RenderServices.shapes().rounded(x, y, x + rowW, y + height, VapeClickGui.CARD_RADIUS,
                    gui.withAlpha(hoverFill, hoverAlpha));
        }
        // 卡片底部分隔线（选中动画完成时隐藏）
        if (selectAnim < 0.5f) {
            RenderServices.shapes().line(x + 9.0f, y + height - 0.5f, x + rowW - 9.0f, y + height - 0.5f, 0.6f,
                    gui.withAlpha(new Color(102, 110, 128).getRGB(), 22.0f * (1.0f - selectAnim * 2.0f) * alpha * gui.guiAlpha));
        }
        drawCardHeader(module, x, y, selectAnim, alpha);
    }

    /**
     * 绘制卡片头部（图标、名称、描述、开关、星标）。
     */
    private void drawCardHeader(Module module, float x, float y, float selectAnim, float alpha) {
        boolean enabled = module.getState();
        float centerY = y + 24.0f;
        drawModuleIcon(module, x + 20.0f, centerY, selectAnim, alpha);
        // 模块名称：选中时颜色从默认渐变到强调色
        int nameColor = gui.blendColor(
                enabled ? gui.guiColors().text : gui.guiColors().muted,
                gui.guiColors().accent,
                selectAnim * 0.7f);
        String name = gui.trim(module.getName(), FontLoaders.F14, 66.0f);
        gui.drawFont(name, x + 42.0f, y + 15.0f,
                gui.withAlpha(nameColor, 255.0f * alpha * gui.guiAlpha));
        // 模块描述
        gui.drawFont(gui.trim(gui.getDescription(module), FontLoaders.F14, 70.0f), x + 42.0f, y + 30.0f,
                gui.withAlpha(gui.guiColors().muted, 198.0f * alpha * gui.guiAlpha));
        // 开关和收藏星标
        gui.drawSwitch(gui.getModuleSwitchX(x), gui.getModuleSwitchY(y), enabled, alpha, module);
        drawStarIcon(getStarCenterX(x), centerY, gui.favoriteModules.contains(module), alpha);
    }

    /**
     * 绘制底部模块计数文字（"X enabled / Y modules" 或 "N results / Y modules"）。
     */
    private void drawModuleCount(float panelY, float modulePanelHeight) {
        int visible = gui.getVisibleModules().size();
        int enabled = gui.getEnabledModules();
        int total = ModuleManager.getModules().size();
        String text = gui.searchQuery.length() == 0
                ? enabled + " enabled / " + total + " modules"
                : visible + " results / " + total + " modules";
        gui.drawFont(text, gui.contentX + 16.0f, panelY + modulePanelHeight - 20.0f,
                gui.withAlpha(gui.guiColors().muted, 205.0f * gui.guiAlpha));
    }

    /** @return 卡片行宽度 */
    private float getRowWidth() {
        return VapeClickGui.CARD_W - 20.0f;
    }

    /** @return 收藏星标的中心 X 坐标 */
    private float getStarCenterX(float rowX) {
        return rowX + getRowWidth() - 18.0f;
    }

    /** 检查鼠标是否点击了星标区域 */
    private boolean isStarHit(float rowX, float rowY, int mouseX, int mouseY) {
        float centerX = getStarCenterX(rowX);
        float centerY = rowY + 24.0f;
        return VapeClickGui.isHovered(centerX - 11.0f, centerY - 11.0f, centerX + 11.0f, centerY + 11.0f, mouseX, mouseY);
    }

    /** 绘制模块图标 */
    private void drawModuleIcon(Module module, float centerX, float centerY, float selectAnim, float alpha) {
        int baseColor = module.getState() ? gui.guiColors().text : gui.guiColors().muted;
        int color = gui.blendColor(baseColor, gui.guiColors().accent, selectAnim * 0.8f);
        gui.drawCenteredIcon(ClickGuiIcons.forModule(module), FontLoaders.I20, centerX, centerY,
                gui.withAlpha(color, 220.0f * alpha * gui.guiAlpha));
    }

    /** 绘制收藏星标图标 */
    private void drawStarIcon(float centerX, float centerY, boolean favorite, float alpha) {
        int color = gui.withAlpha(favorite ? new Color(156, 147, 255).getRGB() : new Color(142, 149, 166).getRGB(),
                (favorite ? 230.0f : 176.0f) * alpha * gui.guiAlpha);
        gui.drawCenteredIcon(favorite ? FontLoaders.ICON_STAR : FontLoaders.ICON_STAR_OUTLINE,
                FontLoaders.I18, centerX, centerY, color);
    }

    /**
     * 绘制列表滚动条。
     * <p>
     * 包含轨道、滑块和拖拽高亮效果。滚动条 alpha 有独立的渐隐动画。
     */
    private void drawScrollbar(float drawContentY, float listHeight) {
        VapeClickGui.ScrollbarMetrics metrics = gui.getScrollbarMetrics(drawContentY, listHeight);
        // 滚动条渐隐动画
        gui.scrollbarAlpha = gui.animate(gui.scrollbarAlpha, metrics.visible ? 1.0f : 0.0f, 0.18f);
        if (gui.scrollbarAlpha <= 0.01f) {
            return;
        }
        float dragBoost = gui.draggingScrollbar ? 1.0f : 0.0f;
        // 轨道
        gui.drawSoftRect(metrics.trackX, metrics.trackY, metrics.trackX + 2.2f,
                metrics.trackY + metrics.trackHeight, 2.0f,
                gui.withAlpha(new Color(128, 128, 128, 32).getRGB(), 32.0f * gui.scrollbarAlpha * gui.guiAlpha));
        // 滑块阴影 + 滑块本体
        RenderServices.shapes().shadow(metrics.trackX, metrics.thumbY, metrics.trackX + 2.2f,
                metrics.thumbY + metrics.thumbHeight, 2.0f,
                gui.withAlpha(gui.guiColors().accent, (35.0f + dragBoost * 60.0f) * gui.scrollbarAlpha * gui.guiAlpha), 4, 2.0f);
        gui.drawSoftRect(metrics.trackX, metrics.thumbY, metrics.trackX + 2.2f,
                metrics.thumbY + metrics.thumbHeight, 2.0f,
                gui.withAlpha(gui.guiColors().accent, (150.0f + dragBoost * 70.0f) * gui.scrollbarAlpha * gui.guiAlpha));
    }
}
