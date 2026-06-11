package gq.vapulite.Vapu.VapeClickGui;

import gq.vapulite.Manager.ModuleManager;
import gq.vapulite.Vapu.modules.Module;
import gq.vapulite.Vapu.utils.RenderUtil;
import gq.vapulite.font.FontLoaders;
import net.minecraft.client.gui.ScaledResolution;

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
        drawUserPanel(y);
        drawStatsPanel(y + 56.0f);
        drawModuleSummary(y + 130.0f);
    }

    /**
     * 处理鼠标点击事件。
     * <p>
     * 点击按键绑定区域时触发按键绑定流程。
     */
    boolean mouseClicked(int mouseX, int mouseY, int mouseButton) {
        if (mouseButton != 0 || !gui.sidePanelVisible || gui.selectedModule == null) {
            return false;
        }
        float summaryY = gui.contentY + 130.0f;
        float summaryH = Math.min(180.0f, gui.panelH - 130.0f);
        float chipX = gui.sideX + 16.0f;
        float chipY = summaryY + summaryH - 30.0f;
        // 检查是否点击了按键绑定区域
        if (VapeClickGui.isHovered(chipX, chipY, chipX + gui.sideW - 32.0f, chipY + 18.0f, mouseX, mouseY)
                || VapeClickGui.isHovered(gui.sideX + 12.0f, summaryY + 100.0f,
                gui.sideX + gui.sideW - 12.0f, summaryY + 120.0f, mouseX, mouseY)) {
            gui.startBinding(gui.selectedModule);
            return true;
        }
        return false;
    }

    /**
     * 绘制用户面板（头像、用户名、Premium 标签）。
     */
    private void drawUserPanel(float y) {
        // 毛玻璃背景
        RenderUtil.drawSoftShadow(gui.sideX, gui.navY, gui.sideX + gui.sideW, gui.navY + VapeClickGui.NAV_H,
                VapeClickGui.PANEL_RADIUS, gui.withAlpha(gui.shadowColor(220),
                        70.0f * gui.guiAlpha), 8, 5.0f);
        gui.drawThemedGlass(gui.sideX, gui.navY, gui.sideX + gui.sideW, gui.navY + VapeClickGui.NAV_H,
                VapeClickGui.PANEL_RADIUS, 1.0f,
                gui.withAlpha(gui.guiColors().glassFill, 206.0f * gui.guiAlpha),
                gui.withAlpha(gui.guiColors().glassBorder, 50.0f * gui.guiAlpha));
        // 用户头像图标
        gui.drawSoftRect(gui.sideX + 10.0f, gui.navY + 6.0f, gui.sideX + 26.0f, gui.navY + 22.0f, 5.0f,
                gui.withAlpha(new Color(81, 87, 103, 220).getRGB(), 220.0f * gui.guiAlpha));
        gui.drawCenteredIcon(FontLoaders.ICON_USER, FontLoaders.I14, gui.sideX + 18.0f, gui.navY + 14.0f,
                gui.withAlpha(gui.guiColors().text, 235.0f * gui.guiAlpha));
        // 用户名和 Premium 标签
        gui.drawFont("VapuUser", gui.sideX + 34.0f, gui.navY + 6.0f,
                gui.withAlpha(gui.guiColors().text, 240.0f * gui.guiAlpha));
        gui.drawFont("Premium", gui.sideX + 34.0f, gui.navY + 18.0f,
                gui.withAlpha(gui.guiColors().accent, 210.0f * gui.guiAlpha));
        // 下拉箭头
        gui.drawCenteredIcon(FontLoaders.ICON_DROPDOWN_ARROW, FontLoaders.I14,
                gui.sideX + gui.sideW - 13.0f, gui.navY + 14.0f,
                gui.withAlpha(gui.guiColors().muted, 190.0f * gui.guiAlpha));
    }

    /**
     * 绘制状态面板（FPS、Ping、已启用模块数及 FPS 波形图）。
     */
    private void drawStatsPanel(float y) {
        RenderUtil.drawSoftShadow(gui.sideX, y, gui.sideX + gui.sideW, y + 64.0f, VapeClickGui.PANEL_RADIUS,
                gui.withAlpha(gui.shadowColor(220), 70.0f * gui.guiAlpha), 8, 5.0f);
        gui.drawThemedGlass(gui.sideX, y, gui.sideX + gui.sideW, y + 64.0f, VapeClickGui.PANEL_RADIUS, 1.0f,
                gui.withAlpha(gui.guiColors().glassFill, 204.0f * gui.guiAlpha),
                gui.withAlpha(gui.guiColors().glassBorder, 48.0f * gui.guiAlpha));
        // 三列状态数据
        drawStat("FPS", gui.getLiveFpsText(), gui.sideX + 12.0f, y + 12.0f, gui.guiColors().text);
        drawStat("Ping", gui.getPingText(), gui.sideX + 68.0f, y + 12.0f, new Color(118, 213, 144).getRGB());
        drawStat("Modules", gui.getEnabledModules() + "/" + ModuleManager.getModules().size(), gui.sideX + 122.0f, y + 12.0f,
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
        float graphTop = y + 43.0f;
        float graphW = gui.sideW - 24.0f;
        float graphH = 14.0f;
        float graphBottom = graphTop + graphH;
        int count = gui.getFpsGraphSize();

        // 绘制基线
        RenderUtil.drawLine(graphX, graphBottom, graphX + graphW, graphBottom, 0.55f,
                gui.withAlpha(new Color(105, 128, 148).getRGB(), 32.0f * gui.guiAlpha));
        // 采样不足时绘制水平线
        if (count < 2) {
            float yMid = graphBottom - graphH * 0.48f;
            RenderUtil.drawLine(graphX, yMid, graphX + graphW, yMid, 0.7f,
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
            RenderUtil.drawLine(previousX, previousY, px, py, 2.0f,
                    gui.withAlpha(gui.guiColors().accent, 28.0f * gui.guiAlpha));
            RenderUtil.drawLine(previousX, previousY, px, py, 0.85f,
                    gui.withAlpha(new Color(116, 198, 229).getRGB(), 145.0f * gui.guiAlpha));
            previousX = px;
            previousY = py;
        }
        // 最新数据点高亮圆点
        RenderUtil.drawCircle(previousX, previousY, 0, 360, 1.7f,
                gui.withAlpha(new Color(148, 224, 255).getRGB(), 190.0f * gui.guiAlpha));
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
    private void drawModuleSummary(float y) {
        float h = Math.min(180.0f, gui.panelH - 130.0f);
        RenderUtil.drawSoftShadow(gui.sideX, y, gui.sideX + gui.sideW, y + h, VapeClickGui.PANEL_RADIUS,
                gui.withAlpha(gui.shadowColor(230), 78.0f * gui.guiAlpha), 9, 6.0f);
        gui.drawThemedGlass(gui.sideX, y, gui.sideX + gui.sideW, y + h, VapeClickGui.PANEL_RADIUS, 1.0f,
                gui.withAlpha(gui.guiColors().glassFill, 210.0f * gui.guiAlpha),
                gui.withAlpha(gui.guiColors().glassBorder, 48.0f * gui.guiAlpha));
        // 未选中模块时的提示
        if (gui.selectedModule == null) {
            gui.drawCenteredText("Select a module", gui.sideX, y + h / 2.0f - 8.0f,
                    gui.sideX + gui.sideW, y + h / 2.0f + 8.0f,
                    gui.withAlpha(gui.guiColors().muted, 200.0f * gui.guiAlpha));
            return;
        }
        // 模块图标
        gui.drawCenteredIcon(ClickGuiIcons.forModule(gui.selectedModule), FontLoaders.I18,
                gui.sideX + 23.0f, y + 23.0f, gui.withAlpha(gui.guiColors().accent, 220.0f * gui.guiAlpha));
        // 模块名称和收藏星标
        gui.drawFont(gui.trim(gui.selectedModule.getName(), FontLoaders.F16, gui.sideW - 62.0f),
                gui.sideX + 38.0f, y + 18.0f, gui.withAlpha(gui.guiColors().text, 245.0f * gui.guiAlpha));
        gui.drawCenteredIcon(FontLoaders.ICON_STAR_OUTLINE, FontLoaders.I18,
                gui.sideX + gui.sideW - 24.0f, y + 22.0f,
                gui.withAlpha(gui.guiColors().muted, 190.0f * gui.guiAlpha));
        // 模块描述
        gui.drawFont(gui.trim(gui.getDescription(gui.selectedModule), FontLoaders.F14, gui.sideW - 32.0f),
                gui.sideX + 16.0f, y + 42.0f, gui.withAlpha(gui.guiColors().muted, 205.0f * gui.guiAlpha));
        // 分隔线 → 状态信息行
        RenderUtil.drawLine(gui.sideX + 16.0f, y + 74.0f, gui.sideX + gui.sideW - 16.0f, y + 74.0f, 0.6f,
                gui.withAlpha(new Color(95, 101, 118).getRGB(), 36.0f * gui.guiAlpha));
        drawSummaryRows(y + 86.0f);
        // 分隔线 → 按键绑定
        RenderUtil.drawLine(gui.sideX + 16.0f, y + h - 42.0f,
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
        gui.drawThemedGlass(x, y, x + w, y + h, 6.0f, 0.8f,
                gui.withAlpha(gui.guiColors().glassFillSoft, 198.0f * gui.guiAlpha),
                gui.withAlpha(gui.guiColors().glassBorder, 50.0f * gui.guiAlpha));
        gui.drawCenteredText(gui.getKeyName(module), x, y + 4.0f, x + w, y + h - 2.0f,
                gui.withAlpha(gui.guiColors().text, 220.0f * gui.guiAlpha));
    }
}
