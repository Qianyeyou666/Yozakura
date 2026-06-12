package gq.vapulite.ui.click;

import gq.vapulite.manager.ModuleManager;
import gq.vapulite.module.Module;
import gq.vapulite.module.render.ClickGUI;
import gq.vapulite.module.render.HUD;
import gq.vapulite.value.Numbers;
import gq.vapulite.engine.font.FontLoaders;
import gq.vapulite.engine.render.ui.RenderServices;
import net.minecraft.client.gui.ScaledResolution;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

import java.awt.Color;

/**
 * ClickGUI 侧边面板组件，显示用户信息、系统状态和选中模块摘要。
 * <p>
 * 仅在窗口宽度 >= 900px 时显示。包含三个子面板：
 * <ul>
 *   <li>用户面板：显示用户名和 Premium 标签</li>
 *   <li>状态面板：显示 FPS、Ping、已启用模块数，以及 FPS 波形图</li>
 *   <li>模块摘要面板：显示选中模块的名称、描述、状态和按键绑定</li>
 * </ul>
 * 包级私有（package-private），仅供 {@link VapeClickGui} 内部使用。
 */
final class ClickGuiSidePanel {
    private static final float STATS_H = 82.0f;
    private static final float SUMMARY_MIN_H = 126.0f;
    private static final float DESIGN_H = 150.0f;
    private static final float DRAG_H = 28.0f;

    /** 关联的主 GUI 实例 */
    private final VapeClickGui gui;

    ClickGuiSidePanel(VapeClickGui gui) {
        this.gui = gui;
    }

    /**
     * 渲染侧边面板。
     *
     * @param sr     屏幕分辨率
     * @param mouseX 鼠标 X 坐标
     * @param mouseY 鼠标 Y 坐标
     * @param introY Y 轴动画偏移量
     */
    void render(ScaledResolution sr, int mouseX, int mouseY, float introY) {
        if (!gui.sidePanelVisible) {
            return;
        }
        float y = gui.contentY + introY;
        drawUserPanel(introY);
        drawOffsetPanel(ClickGUI.sideStatsOffsetX, ClickGUI.sideStatsOffsetY, new PanelDraw() {
            @Override
            public void draw(float offsetX, float offsetY) {
                drawStatsPanel(getStatsY() + introY);
            }
        });
        drawOffsetPanel(ClickGUI.sideSummaryOffsetX, ClickGUI.sideSummaryOffsetY, new PanelDraw() {
            @Override
            public void draw(float offsetX, float offsetY) {
                drawModuleSummary(getSummaryY() + introY, getSummaryH());
            }
        });
        drawOffsetPanel(ClickGUI.sideDesignOffsetX, ClickGUI.sideDesignOffsetY, new PanelDraw() {
            @Override
            public void draw(float offsetX, float offsetY) {
                drawDesignPanel(getDesignY() + introY, Math.round(mouseX - offsetX), Math.round(mouseY - offsetY));
            }
        });
    }

    void updateDrag(int mouseX, int mouseY) {
        if (gui.draggingSidePanel == null) {
            return;
        }
        if (!Mouse.isButtonDown(0)) {
            gui.draggingSidePanel = null;
            return;
        }
        Numbers<Double> offsetX = getOffsetX(gui.draggingSidePanel);
        Numbers<Double> offsetY = getOffsetY(gui.draggingSidePanel);
        if (offsetX == null || offsetY == null) {
            gui.draggingSidePanel = null;
            return;
        }
        offsetX.setValue((double) gui.clamp(gui.draggingSidePanelStartOffsetX + mouseX - gui.draggingSidePanelStartMouseX,
                offsetX.getMinimum().floatValue(), offsetX.getMaximum().floatValue()));
        offsetY.setValue((double) gui.clamp(gui.draggingSidePanelStartOffsetY + mouseY - gui.draggingSidePanelStartMouseY,
                offsetY.getMinimum().floatValue(), offsetY.getMaximum().floatValue()));
    }

    /**
     * 处理鼠标点击事件。
     * <p>
     * 点击按键绑定区域时触发按键绑定流程。
     */
    boolean mouseClicked(int mouseX, int mouseY, int mouseButton) {
        if (mouseButton != 0 || !gui.sidePanelVisible) {
            return false;
        }
        if (handleDesignClick(mouseX, mouseY)) {
            return true;
        }
        if (startPanelDrag("stats", getPanelX(ClickGUI.sideStatsOffsetX),
                getPanelY(getStatsY(), ClickGUI.sideStatsOffsetY),
                STATS_H, mouseX, mouseY)) {
            return true;
        }
        if (startPanelDrag("summary", getPanelX(ClickGUI.sideSummaryOffsetX),
                getPanelY(getSummaryY(), ClickGUI.sideSummaryOffsetY),
                getSummaryH(), mouseX, mouseY)) {
            return true;
        }
        if (startPanelDrag("design", getPanelX(ClickGUI.sideDesignOffsetX),
                getPanelY(getDesignY(), ClickGUI.sideDesignOffsetY),
                DESIGN_H, mouseX, mouseY)) {
            return true;
        }
        if (gui.selectedModule == null) {
            return false;
        }
        float summaryX = getPanelX(ClickGUI.sideSummaryOffsetX);
        float summaryY = getPanelY(getSummaryY(), ClickGUI.sideSummaryOffsetY);
        float summaryH = getSummaryH();
        float chipX = summaryX + 16.0f;
        float chipY = summaryY + summaryH - 30.0f;
        // 检查是否点击了按键绑定区域
        if (VapeClickGui.isHovered(chipX, chipY, chipX + gui.sideW - 32.0f, chipY + 18.0f, mouseX, mouseY)
                || VapeClickGui.isHovered(summaryX + 12.0f, summaryY + 100.0f,
                summaryX + gui.sideW - 12.0f, summaryY + 120.0f, mouseX, mouseY)) {
            gui.keyChipClickProgress.put(gui.selectedModule, 1.0f);
            gui.startBinding(gui.selectedModule);
            return true;
        }
        return false;
    }

    /**
     * 绘制用户面板（头像、用户名、Premium 标签）。
     */
    private void drawUserPanel(float introY) {
        float x = gui.contentX;
        float y = gui.navY + introY;
        float w = VapeClickGui.CARD_W;
        RenderServices.shapes().shadow(x, y, x + w, y + VapeClickGui.NAV_H,
                VapeClickGui.PANEL_RADIUS, gui.withAlpha(gui.shadowColor(220),
                        70.0f * gui.guiAlpha), 8, 5.0f);
        gui.drawPanelGlass(x, y, x + w, y + VapeClickGui.NAV_H,
                VapeClickGui.PANEL_RADIUS, 1.0f,
                gui.withAlpha(gui.guiColors().glassFill, 206.0f * gui.guiAlpha),
                gui.withAlpha(gui.guiColors().glassBorder, 50.0f * gui.guiAlpha));
        // 用户头像图标
        gui.drawSoftRect(x + 10.0f, y + 6.0f, x + 26.0f, y + 22.0f, 5.0f,
                gui.withAlpha(new Color(81, 87, 103, 220).getRGB(), 220.0f * gui.guiAlpha));
        gui.drawCenteredIcon(FontLoaders.ICON_USER, FontLoaders.I14, x + 18.0f, y + 14.0f,
                gui.withAlpha(gui.guiColors().text, 235.0f * gui.guiAlpha));
        // 用户名和 Premium 标签
        gui.drawFont("VapuUser", x + 34.0f, y + 6.0f,
                gui.withAlpha(gui.guiColors().text, 240.0f * gui.guiAlpha));
        gui.drawFont("Premium", x + 34.0f, y + 18.0f,
                gui.withAlpha(gui.guiColors().accent, 210.0f * gui.guiAlpha));
    }

    /**
     * 绘制状态面板（FPS、Ping、已启用模块数及 FPS 波形图）。
     */
    private void drawStatsPanel(float y) {
        RenderServices.shapes().shadow(gui.sideX, y, gui.sideX + gui.sideW, y + STATS_H, VapeClickGui.PANEL_RADIUS,
                gui.withAlpha(gui.shadowColor(220), 70.0f * gui.guiAlpha), 8, 5.0f);
        gui.drawPanelGlass(gui.sideX, y, gui.sideX + gui.sideW, y + STATS_H, VapeClickGui.PANEL_RADIUS, 1.0f,
                gui.withAlpha(gui.guiColors().glassFill, 204.0f * gui.guiAlpha),
                gui.withAlpha(gui.guiColors().glassBorder, 48.0f * gui.guiAlpha));
        drawPanelTitle(FontLoaders.ICON_INFO, "Statistics", y + 13.0f);
        // 三列状态数据
        drawStat("FPS", gui.getLiveFpsText(), gui.sideX + 12.0f, y + 32.0f, gui.guiColors().text);
        drawStat("Ping", gui.getPingText(), gui.sideX + 68.0f, y + 32.0f, new Color(93, 180, 115).getRGB());
        drawStat("Modules", gui.getEnabledModules() + "/" + ModuleManager.getModules().size(), gui.sideX + 122.0f, y + 32.0f,
                gui.guiColors().accent);
        // FPS 波形图
        drawFpsGraph(y);
    }

    /**
     * 绘制 FPS 波形折线图。
     * <p>
     * 从 FPS 采样缓冲区中读取历史数据，绘制平滑折线。
     * 当采样数不足时显示一条水平线。
     */
    private void drawFpsGraph(float y) {
        float graphX = gui.sideX + 12.0f;
        float graphTop = y + 64.0f;
        float graphW = gui.sideW - 24.0f;
        float graphH = 14.0f;
        float graphBottom = graphTop + graphH;
        int count = gui.getFpsGraphSize();

        // 绘制基线
        RenderServices.shapes().line(graphX, graphBottom, graphX + graphW, graphBottom, 0.55f,
                gui.withAlpha(new Color(105, 128, 148).getRGB(), 32.0f * gui.guiAlpha));
        // 采样不足时绘制水平线
        if (count < 2) {
            float yMid = graphBottom - graphH * 0.48f;
            RenderServices.shapes().line(graphX, yMid, graphX + graphW, yMid, 0.7f,
                    gui.withAlpha(gui.guiColors().accent, 70.0f * gui.guiAlpha));
            return;
        }

        // 计算 Y 轴范围（最小扩展到 24fps 以避免波动过大）
        float min = Float.MAX_VALUE;
        float max = 0.0f;
        for (int i = 0; i < count; i++) {
            float sample = gui.getFpsGraphSample(i);
            min = Math.min(min, sample);
            max = Math.max(max, sample);
        }
        if (max - min < 24.0f) {
            float center = (min + max) * 0.5f;
            min = Math.max(0.0f, center - 12.0f);
            max = center + 12.0f;
        }

        // 逐段绘制折线（底层粗线 + 上层细高亮线）
        float step = graphW / Math.max(1.0f, count - 1.0f);
        float previousX = graphX;
        float previousY = graphBottom - normalizedFps(gui.getFpsGraphSample(0), min, max) * graphH;
        for (int i = 1; i < count; i++) {
            float px = graphX + step * i;
            float py = graphBottom - normalizedFps(gui.getFpsGraphSample(i), min, max) * graphH;
            RenderServices.shapes().line(previousX, previousY, px, py, 2.0f,
                    gui.withAlpha(gui.guiColors().accent, 28.0f * gui.guiAlpha));
            RenderServices.shapes().line(previousX, previousY, px, py, 0.85f,
                    gui.withAlpha(gui.guiColors().accent, 145.0f * gui.guiAlpha));
            previousX = px;
            previousY = py;
        }
        // 最新数据点高亮圆点
        RenderServices.shapes().circle(previousX, previousY, 0, 360, 1.7f,
                gui.withAlpha(gui.guiColors().accent, 190.0f * gui.guiAlpha));
    }

    /**
     * 将 FPS 值归一化到 0~1 范围。
     */
    private float normalizedFps(float fps, float min, float max) {
        return gui.clamp((fps - min) / Math.max(1.0f, max - min), 0.0f, 1.0f);
    }

    /**
     * 绘制模块摘要面板（图标、名称、描述、状态行、按键绑定）。
     */
    private void drawModuleSummary(float y, float h) {
        RenderServices.shapes().shadow(gui.sideX, y, gui.sideX + gui.sideW, y + h, VapeClickGui.PANEL_RADIUS,
                gui.withAlpha(gui.shadowColor(230), 78.0f * gui.guiAlpha), 9, 6.0f);
        gui.drawPanelGlass(gui.sideX, y, gui.sideX + gui.sideW, y + h, VapeClickGui.PANEL_RADIUS, 1.0f,
                gui.withAlpha(gui.guiColors().glassFill, 210.0f * gui.guiAlpha),
                gui.withAlpha(gui.guiColors().glassBorder, 48.0f * gui.guiAlpha));
        drawPanelTitle(FontLoaders.ICON_SETTINGS, "Module Info", y + 17.0f);
        // 未选中模块时的提示
        if (gui.selectedModule == null) {
            gui.drawCenteredText("Select a module", gui.sideX, y + h / 2.0f - 8.0f,
                    gui.sideX + gui.sideW, y + h / 2.0f + 8.0f,
                    gui.withAlpha(gui.guiColors().muted, 200.0f * gui.guiAlpha));
            return;
        }
        // 模块图标
        gui.drawCenteredIcon(ClickGuiIcons.forModule(gui.selectedModule), FontLoaders.I18,
                gui.sideX + 23.0f, y + 42.0f, gui.withAlpha(gui.guiColors().accent, 220.0f * gui.guiAlpha));
        // 模块名称和收藏星标
        gui.drawFont(gui.trim(gui.selectedModule.getName(), FontLoaders.F16, gui.sideW - 62.0f),
                gui.sideX + 38.0f, y + 37.0f, gui.withAlpha(gui.guiColors().text, 245.0f * gui.guiAlpha));
        gui.drawCenteredIcon(FontLoaders.ICON_STAR_OUTLINE, FontLoaders.I18,
                gui.sideX + gui.sideW - 24.0f, y + 41.0f,
                gui.withAlpha(gui.guiColors().muted, 190.0f * gui.guiAlpha));
        // 模块描述
        gui.drawFont(gui.trim(gui.getDescription(gui.selectedModule), FontLoaders.F14, gui.sideW - 32.0f),
                gui.sideX + 16.0f, y + 63.0f, gui.withAlpha(gui.guiColors().muted, 205.0f * gui.guiAlpha));
        // 分隔线 → 状态信息行
        RenderServices.shapes().line(gui.sideX + 16.0f, y + 91.0f, gui.sideX + gui.sideW - 16.0f, y + 91.0f, 0.6f,
                gui.withAlpha(new Color(95, 101, 118).getRGB(), 36.0f * gui.guiAlpha));
        drawSummaryRows(y + 103.0f);
        // 分隔线 → 按键绑定
        RenderServices.shapes().line(gui.sideX + 16.0f, y + h - 42.0f,
                gui.sideX + gui.sideW - 16.0f, y + h - 42.0f, 0.6f,
                gui.withAlpha(new Color(95, 101, 118).getRGB(), 36.0f * gui.guiAlpha));
        drawKeyChip(gui.sideX + 16.0f, y + h - 30.0f, gui.sideW - 32.0f, 18.0f, gui.selectedModule);
    }

    /**
     * 绘制单个状态标签+值（如 "FPS" / "120"）。
     */
    private void drawStat(String label, String value, float x, float y, int valueColor) {
        gui.drawFont(label, x, y, gui.withAlpha(gui.guiColors().muted, 175.0f * gui.guiAlpha));
        gui.drawFont(value, x, y + 15.0f, gui.withAlpha(valueColor, 235.0f * gui.guiAlpha));
    }

    /**
     * 绘制模块状态摘要行（启用状态、绑定按键、选项数量）。
     */
    private void drawSummaryRows(float y) {
        if (gui.selectedModule == null) {
            return;
        }
        String[][] rows = new String[][]{
                new String[]{"State", gui.selectedModule.getState() ? "Enabled" : "Disabled"},
                new String[]{"Key", gui.getKeyName(gui.selectedModule)},
                new String[]{"Options", String.valueOf(gui.getVisibleValueCount(gui.selectedModule))}
        };
        for (int i = 0; i < rows.length; i++) {
            float rowY = y + i * 20.0f;
            gui.drawFont(rows[i][0], gui.sideX + 16.0f, rowY, gui.withAlpha(gui.guiColors().muted, 178.0f * gui.guiAlpha));
            gui.drawFont(rows[i][1], gui.sideX + gui.sideW - 16.0f - FontLoaders.F14.getStringWidth(rows[i][1]), rowY,
                    gui.withAlpha(i == 0 && gui.selectedModule.getState() ? gui.guiColors().accent : gui.guiColors().text,
                            220.0f * gui.guiAlpha));
        }
    }

    /**
     * 绘制按键绑定卡片（毛玻璃圆角矩形 + 按键名居中）。
     */
    private void drawKeyChip(float x, float y, float w, float h, Module module) {
        boolean hovered = VapeClickGui.isHovered(x, y, x + w, y + h, gui.currentMouseX, gui.currentMouseY);
        float hover = gui.animateMap(gui.keyChipHoverProgress, module, hovered && !gui.closing ? 1.0f : 0.0f, 0.18f);
        float click = gui.animateMap(gui.keyChipClickProgress, module, 0.0f, 0.22f);
        float inset = click * 1.2f;
        float lift = hover * 0.8f - click * 0.5f;
        if (hover > 0.02f || click > 0.02f) {
            RenderServices.shapes().shadow(x, y, x + w, y + h, 6.0f,
                    gui.withAlpha(gui.guiColors().accent, (18.0f + hover * 36.0f + click * 54.0f) * gui.guiAlpha),
                    5, 2.4f);
        }
        int fill = gui.blendColor(gui.guiColors().glassFillSoft, gui.guiColors().accent, 0.08f * hover + 0.12f * click);
        gui.drawThemedGlass(x + inset, y + inset, x + w - inset, y + h - inset, 6.0f, 0.8f,
                gui.withAlpha(fill, (198.0f + hover * 24.0f + click * 20.0f) * gui.guiAlpha),
                gui.withAlpha(hover > 0.01f || click > 0.01f ? gui.guiColors().accent : gui.guiColors().glassBorder,
                        (50.0f + hover * 62.0f + click * 78.0f) * gui.guiAlpha));
        gui.drawCenteredText(gui.getKeyName(module), x, y + 4.0f - lift, x + w, y + h - 2.0f - lift,
                gui.withAlpha(hover > 0.2f || click > 0.02f ? gui.guiColors().accent : gui.guiColors().text,
                        (220.0f + hover * 20.0f) * gui.guiAlpha));
    }

    private void drawDesignPanel(float y, int mouseX, int mouseY) {
        RenderServices.shapes().shadow(gui.sideX, y, gui.sideX + gui.sideW, y + DESIGN_H, VapeClickGui.PANEL_RADIUS,
                gui.withAlpha(gui.shadowColor(230), 72.0f * gui.guiAlpha), 8, 5.0f);
        gui.drawPanelGlass(gui.sideX, y, gui.sideX + gui.sideW, y + DESIGN_H, VapeClickGui.PANEL_RADIUS, 1.0f,
                gui.withAlpha(gui.guiColors().glassFill, 214.0f * gui.guiAlpha),
                gui.withAlpha(gui.guiColors().glassBorder, 54.0f * gui.guiAlpha));
        drawPanelTitle(FontLoaders.ICON_SUN_ALT, "Design", y + 17.0f);

        float swatchX = gui.sideX + 15.0f;
        float swatchY = y + 37.0f;
        drawThemeSwatch(HUD.Theme.SAKURA, swatchX, swatchY, 0xFFE56B9D, mouseX, mouseY);
        drawThemeSwatch(HUD.Theme.LIGHT, swatchX + 29.0f, swatchY, 0xFFF8F8FA, mouseX, mouseY);
        drawThemeSwatch(HUD.Theme.DARK, swatchX + 58.0f, swatchY, 0xFF282836, mouseX, mouseY);
        drawThemeSwatch(HUD.Theme.GRAY, swatchX + 87.0f, swatchY, 0xFF8E949D, mouseX, mouseY);
        drawDecorSwatch(swatchX + 116.0f, swatchY, 0xFFFFFFFF);

        float previewX = gui.sideX + 15.0f;
        float previewY = y + 64.0f;
        float previewW = gui.sideW - 30.0f;
        RenderServices.shapes().rounded(previewX, previewY, previewX + previewW, previewY + 34.0f, 6.0f,
                gui.withAlpha(new Color(255, 235, 245).getRGB(), 230.0f * gui.guiAlpha));
        RenderServices.shapes().horizontalGradient(previewX + 2.0f, previewY + 2.0f,
                previewX + previewW - 2.0f, previewY + 32.0f,
                gui.withAlpha(new Color(255, 210, 230).getRGB(), 158.0f * gui.guiAlpha),
                gui.withAlpha(new Color(190, 222, 252).getRGB(), 138.0f * gui.guiAlpha));
        RenderServices.shapes().circle(previewX + previewW * 0.72f, previewY + 16.0f, 0, 360, 16.0f,
                gui.withAlpha(new Color(255, 255, 255).getRGB(), 52.0f * gui.guiAlpha));

        float buttonY = y + 105.0f;
        boolean buttonHovered = VapeClickGui.isHovered(gui.sideX + 15.0f, buttonY, gui.sideX + gui.sideW - 15.0f,
                buttonY + 15.0f, mouseX, mouseY);
        gui.designThemeButtonHoverProgress = gui.animate(gui.designThemeButtonHoverProgress,
                buttonHovered ? 1.0f : 0.0f, 0.18f);
        float buttonHover = gui.easeSmooth(gui.designThemeButtonHoverProgress);
        float textShift = gui.easeOut(gui.themeFadeProgress) * 2.0f - buttonHover * 0.6f;
        gui.drawThemedGlass(gui.sideX + 15.0f, buttonY, gui.sideX + gui.sideW - 15.0f, buttonY + 15.0f,
                5.0f, 0.7f, gui.withAlpha(gui.guiColors().glassFillSoft, (222.0f + 22.0f * buttonHover) * gui.guiAlpha),
                gui.withAlpha(buttonHovered ? gui.guiColors().accent : gui.guiColors().glassBorder,
                        (44.0f + 52.0f * buttonHover) * gui.guiAlpha));
        if (buttonHover > 0.02f) {
            RenderServices.shapes().shadow(gui.sideX + 15.0f, buttonY, gui.sideX + gui.sideW - 15.0f, buttonY + 15.0f,
                    5.0f, gui.withAlpha(gui.guiColors().accent, 22.0f * buttonHover * gui.guiAlpha), 4, 2.0f);
        }
        gui.drawCenteredText("Theme  " + formatTheme(HUD.getTheme()), gui.sideX + 18.0f, buttonY + 3.0f + textShift,
                gui.sideX + gui.sideW - 18.0f, buttonY + 14.0f,
                gui.withAlpha(gui.guiColors().text, (218.0f + 24.0f * buttonHover) * gui.guiAlpha));

        float resetY = y + 127.0f;
        boolean resetHovered = VapeClickGui.isHovered(gui.sideX + 15.0f, resetY, gui.sideX + gui.sideW - 15.0f,
                resetY + 16.0f, mouseX, mouseY);
        gui.designResetButtonHoverProgress = gui.animate(gui.designResetButtonHoverProgress,
                resetHovered ? 1.0f : 0.0f, 0.18f);
        gui.designResetButtonPressProgress = gui.animate(gui.designResetButtonPressProgress, 0.0f, 0.25f);
        float resetHover = gui.easeSmooth(gui.designResetButtonHoverProgress);
        float resetPress = gui.easeOut(gui.designResetButtonPressProgress);
        float resetInset = resetPress * 1.0f;
        gui.drawThemedGlass(gui.sideX + 15.0f + resetInset, resetY + resetInset,
                gui.sideX + gui.sideW - 15.0f - resetInset, resetY + 16.0f - resetInset,
                5.0f, 0.7f,
                gui.withAlpha(gui.guiColors().glassFillSoft, (218.0f + 26.0f * resetHover) * gui.guiAlpha),
                gui.withAlpha(resetHovered ? gui.guiColors().accent : gui.guiColors().glassBorder,
                        (42.0f + 56.0f * resetHover) * gui.guiAlpha));
        if (resetHover > 0.02f || resetPress > 0.02f) {
            RenderServices.shapes().shadow(gui.sideX + 15.0f, resetY, gui.sideX + gui.sideW - 15.0f, resetY + 16.0f,
                    5.0f, gui.withAlpha(gui.guiColors().accent,
                            (20.0f * resetHover + 34.0f * resetPress) * gui.guiAlpha), 4, 2.0f);
        }
        gui.drawCenteredText("Reset Layout", gui.sideX + 18.0f,
                resetY + 3.0f - resetHover * 0.5f + resetPress * 0.6f,
                gui.sideX + gui.sideW - 18.0f, resetY + 14.0f,
                gui.withAlpha(gui.guiColors().text, (214.0f + 28.0f * resetHover) * gui.guiAlpha));
    }

    private void drawThemeSwatch(HUD.Theme theme, float x, float y, int color, int mouseX, int mouseY) {
        boolean selected = HUD.getTheme() == theme;
        boolean hovered = VapeClickGui.isHovered(x - 4.0f, y - 4.0f, x + 18.0f, y + 18.0f, mouseX, mouseY);
        Float current = gui.themeSwatchProgress.get(theme);
        float progress = current == null ? (selected ? 1.0f : 0.0f) : current.floatValue();
        progress = gui.animate(progress, selected ? 1.0f : 0.0f, 0.18f);
        gui.themeSwatchProgress.put(theme, progress);
        Float currentHover = gui.designSwatchHoverProgress.get(theme);
        float hover = currentHover == null ? 0.0f : currentHover.floatValue();
        hover = gui.animate(hover, hovered ? 1.0f : 0.0f, 0.20f);
        gui.designSwatchHoverProgress.put(theme, hover);

        float eased = gui.easeSmooth(progress);
        float hoverEase = gui.easeSmooth(hover);
        float lift = eased * 1.3f + hoverEase * 0.8f;
        float size = 14.0f + eased * 2.0f + hoverEase * 1.0f;
        float px = x - lift;
        float py = y - lift;
        if (progress > 0.02f || hover > 0.02f) {
            RenderServices.shapes().shadow(px - 2.0f, py - 2.0f, px + size + 2.0f, py + size + 2.0f, 5.0f,
                    gui.withAlpha(gui.guiColors().accent, (24.0f + 76.0f * progress + 38.0f * hover) * gui.guiAlpha), 5, 3.0f);
        }
        RenderServices.shapes().roundedBorder(px, py, px + size, py + size, 4.0f, 1.0f,
                gui.withAlpha(color, (210.0f + 35.0f * progress + 18.0f * hover) * gui.guiAlpha),
                gui.withAlpha(selected ? gui.guiColors().accent : gui.guiColors().glassBorder,
                        (70.0f + 120.0f * progress + 48.0f * hover) * gui.guiAlpha));
        if (theme == HUD.Theme.LIGHT || theme == HUD.Theme.GRAY) {
            RenderServices.shapes().roundedBorder(px + 1.0f, py + 1.0f, px + size - 1.0f, py + size - 1.0f, 3.0f, 0.5f,
                    gui.withAlpha(0x00FFFFFF, 0.0f),
                    gui.withAlpha(0xFFB8C0CC, 80.0f * gui.guiAlpha));
        }
    }

    private void drawDecorSwatch(float x, float y, int color) {
        RenderServices.shapes().rounded(x, y, x + 14.0f, y + 14.0f, 4.0f,
                gui.withAlpha(color, 220.0f * gui.guiAlpha));
    }

    private boolean handleDesignClick(int mouseX, int mouseY) {
        float xBase = getPanelX(ClickGUI.sideDesignOffsetX);
        float y = getPanelY(getDesignY(), ClickGUI.sideDesignOffsetY);
        float swatchX = xBase + 15.0f;
        float swatchY = y + 37.0f;
        HUD.Theme[] themes = new HUD.Theme[]{HUD.Theme.SAKURA, HUD.Theme.LIGHT, HUD.Theme.DARK, HUD.Theme.GRAY};
        for (int i = 0; i < themes.length; i++) {
            float x = swatchX + i * 29.0f;
            if (VapeClickGui.isHovered(x - 4.0f, swatchY - 4.0f, x + 18.0f, swatchY + 18.0f, mouseX, mouseY)) {
                HUD.setTheme(themes[i]);
                gui.themeFadeProgress = 1.0f;
                gui.addToast("Theme -> " + formatTheme(themes[i]));
                return true;
            }
        }
        if (isResetButtonHovered(y, mouseX, mouseY)) {
            gui.resetUiLayout();
            return true;
        }
        return false;
    }

    private void drawPanelTitle(String icon, String title, float y) {
        gui.drawCenteredIcon(icon, FontLoaders.I14, gui.sideX + 18.0f, y,
                gui.withAlpha(gui.guiColors().accent, 220.0f * gui.guiAlpha));
        gui.drawFont(title, gui.sideX + 30.0f, y - 5.0f,
                gui.withAlpha(gui.guiColors().text, 236.0f * gui.guiAlpha));
    }

    private float getSummaryY() {
        return gui.contentY + STATS_H + 10.0f;
    }

    private float getStatsY() {
        return gui.contentY;
    }

    private float getSummaryH() {
        return Math.max(SUMMARY_MIN_H, getDesignY() - getSummaryY() - 10.0f);
    }

    private float getDesignY() {
        return gui.contentY + gui.panelH - DESIGN_H;
    }

    private float getPanelX(Numbers<Double> offsetX) {
        return gui.sideX + offsetX.getValue().floatValue();
    }

    private float getPanelY(float baseY, Numbers<Double> offsetY) {
        return baseY + offsetY.getValue().floatValue();
    }

    private void drawOffsetPanel(Numbers<Double> offsetX, Numbers<Double> offsetY, PanelDraw draw) {
        float x = offsetX.getValue().floatValue();
        float y = offsetY.getValue().floatValue();
        GL11.glPushMatrix();
        GL11.glTranslatef(x, y, 0.0f);
        try {
            draw.draw(x, y);
        } finally {
            GL11.glPopMatrix();
        }
    }

    private boolean startPanelDrag(String id, float x, float y, float h, int mouseX, int mouseY) {
        if (!VapeClickGui.isHovered(x, y, x + gui.sideW, y + Math.min(DRAG_H, h), mouseX, mouseY)) {
            return false;
        }
        gui.draggingSidePanel = id;
        gui.draggingSidePanelStartMouseX = mouseX;
        gui.draggingSidePanelStartMouseY = mouseY;
        Numbers<Double> offsetX = getOffsetX(id);
        Numbers<Double> offsetY = getOffsetY(id);
        gui.draggingSidePanelStartOffsetX = offsetX == null ? 0.0f : offsetX.getValue().floatValue();
        gui.draggingSidePanelStartOffsetY = offsetY == null ? 0.0f : offsetY.getValue().floatValue();
        return true;
    }

    private Numbers<Double> getOffsetX(String id) {
        if ("stats".equals(id)) {
            return ClickGUI.sideStatsOffsetX;
        }
        if ("summary".equals(id)) {
            return ClickGUI.sideSummaryOffsetX;
        }
        if ("design".equals(id)) {
            return ClickGUI.sideDesignOffsetX;
        }
        return null;
    }

    private Numbers<Double> getOffsetY(String id) {
        if ("stats".equals(id)) {
            return ClickGUI.sideStatsOffsetY;
        }
        if ("summary".equals(id)) {
            return ClickGUI.sideSummaryOffsetY;
        }
        if ("design".equals(id)) {
            return ClickGUI.sideDesignOffsetY;
        }
        return null;
    }

    private boolean isResetButtonHovered(float panelY, int mouseX, int mouseY) {
        float y = panelY + 127.0f;
        float x = getPanelX(ClickGUI.sideDesignOffsetX);
        return VapeClickGui.isHovered(x + 15.0f, y, x + gui.sideW - 15.0f, y + 16.0f,
                mouseX, mouseY);
    }

    private interface PanelDraw {
        void draw(float offsetX, float offsetY);
    }

    private String formatTheme(HUD.Theme theme) {
        if (theme == HUD.Theme.SAKURA) {
            return "Sakura";
        }
        if (theme == HUD.Theme.LIGHT) {
            return "Light";
        }
        if (theme == HUD.Theme.GRAY) {
            return "Gray";
        }
        return "Dark";
    }
}
