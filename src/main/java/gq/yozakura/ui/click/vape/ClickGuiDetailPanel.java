package gq.yozakura.ui.click.vape;

import gq.yozakura.manager.ModuleManager;
import gq.yozakura.module.Module;
import gq.yozakura.module.render.HUD;
import gq.yozakura.util.render.RenderUtil;
import gq.yozakura.value.Mode;
import gq.yozakura.value.Numbers;
import gq.yozakura.value.Option;
import gq.yozakura.value.Value;
import gq.yozakura.value.properties.ModeProperty;
import gq.yozakura.engine.font.FontLoaders;
import gq.yozakura.engine.render.GLStateManager;
import gq.yozakura.engine.render.ui.RenderServices;
import org.lwjgl.opengl.GL11;
import gq.yozakura.ui.click.ClickGuiIcons;
import gq.yozakura.ui.UiPanel;
import gq.yozakura.ui.UiTheme;
import gq.yozakura.util.animation.AnimUtil;
import net.minecraft.util.ResourceLocation;

import java.awt.Color;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * ClickGUI 详情面板组件，负责选中模块的设置项渲染与交互。
 * <p>
 * 功能包括：
 * <ul>
 *   <li>顶部标签页切换（General / Targets / Extra / Rotation / Visuals）</li>
 *   <li>各类设置值的渲染：开关（Option）、滑块（Numbers）、范围滑块、模式选择（Mode）、颜色选择</li>
 *   <li>颜色面板的色相-亮度拾取和彩虹模式</li>
 *   <li>Mode 下拉栏的展开/收起动画</li>
 *   <li>设置区域的滚动条</li>
 * </ul>
 * 包级私有（package-private），仅供 {@link VapeClickGui} 内部使用。
 */
final class ClickGuiDetailPanel {
    /** 详情标签页名称 */
    private static final String[] DETAIL_TABS = new String[]{"General", "Targets", "Extra", "Rotation", "Visuals"};
    /** 下拉栏每行高度 */
    private static final float DROPDOWN_ROW_H = 18.0f;
    /** 颜色面板饱和度常量 */
    private static final float PALETTE_SATURATION = 0.86f;
    /** 颜色面板最低亮度 */
    private static final float PALETTE_MIN_BRIGHTNESS = 0.42f;
    /** 颜色面板标记半径 */
    private static final float PALETTE_MARKER_RADIUS = 4.8f;
    /** 关联的主 GUI 实例 */
    private final VapeClickGui gui;
    /** 记录每个 Red 值对应的色相（用于颜色面板标记定位） */
    private final Map<Numbers, Float> paletteHueByRed = new HashMap<Numbers, Float>();
    /** 记录每个 Red 值对应的当前颜色值（用于检测颜色是否被外部改变） */
    private final Map<Numbers, Integer> paletteColorByRed = new HashMap<Numbers, Integer>();
    /** 所有展开下拉栏的 Mode（跨开关保持） */
    private static final Set<Mode> expandedModes = new HashSet<>();
    private static final Set<ModeProperty> expandedModeProperties = new HashSet<>();
    /** 下拉栏展开动画进度 0→1 */
    private static final Map<Mode, Float> dropdownAnim = new HashMap<>();
    private static final Map<ModeProperty, Float> dropdownPropertyAnim = new HashMap<>();
    /** 标签页摇晃动画（基于真实时间，约 260ms 衰减完毕） */
    private final AnimUtil tabShake = new AnimUtil(260f);
    /** 标签页弹跳动画（基于真实时间，约 280ms 衰减完毕） */
    private final AnimUtil tabBounce = new AnimUtil(280f);
    /** 标签页底部指示线 X 动画位置（-1 表示未初始化） */
    private float tabIndicatorX = -1f;
    /** 当前帧的 introY，下拉栏需要用它对齐 detail 面板 */
    private float currentIntroY;

    /**
     * 检查某个 Mode 是否处于展开状态。
     */
    boolean hasExpandedMode(Mode value) {
        return expandedModes.contains(value);
    }

    boolean hasExpandedModeProperty(ModeProperty value) {
        return expandedModeProperties.contains(value);
    }

    ClickGuiDetailPanel(VapeClickGui gui) {
        this.gui = gui;
    }

    /** 更新 UI 主题 */
    void updateTheme(UiTheme theme) {
    }

    /**
     * 渲染详情面板。
     * <p>
     * 绘制面板背景 → 模块标题区 → 标签页 → 设置项列表 → Mode 下拉栏。
     * 游戏重启后会从保存的状态恢复展开的 Mode 下拉栏。
     *
     * @param mouseX 鼠标 X 坐标
     * @param mouseY 鼠标 Y 坐标
     * @param introY Y 轴动画偏移量
     */
    void render(int mouseX, int mouseY, float introY) {
        this.currentIntroY = introY;
        // 游戏重启后恢复所有展开的 mode 下拉栏
        String savedKeys = VapeClickGui.savedExpandedModeKeys;
        if (!savedKeys.isEmpty()) {
            VapeClickGui.savedExpandedModeKeys = "";
            for (String entry : savedKeys.split(";")) {
                String trimmed = entry.trim();
                if (trimmed.isEmpty()) continue;
                int sep = trimmed.indexOf(':');
                if (sep <= 0) continue;
                String moduleName = trimmed.substring(0, sep);
                String valueName = trimmed.substring(sep + 1);
                for (Module m : ModuleManager.getModules()) {
                    if (m.getName().equals(moduleName)) {
                        for (Value v : m.getValues()) {
                            if (v instanceof Mode && v.getName().equals(valueName)) {
                                expandedModes.add((Mode) v);
                                dropdownAnim.put((Mode) v, 1f);
                                break;
                            } else if (v instanceof ModeProperty && v.getName().equals(valueName)) {
                                expandedModeProperties.add((ModeProperty) v);
                                dropdownPropertyAnim.put((ModeProperty) v, 1f);
                                break;
                            }
                        }
                        break;
                    }
                }
            }
        }
        // 面板定位
        float y = gui.detailY + introY;
        gui.settingsScroll = gui.animate(gui.settingsScroll, gui.targetSettingsScroll, 0.14f);
        // 绘制面板背
        RenderServices.shapes().shadow(gui.detailX, y, gui.detailX + gui.detailW, y + gui.panelH,
                VapeClickGui.PANEL_RADIUS, gui.withAlpha(gui.shadowColor(230), 92.0f * gui.guiAlpha), 10, 7.0f);
        gui.drawPanelGlass(gui.detailX, y, gui.detailX + gui.detailW, y + gui.panelH,
                VapeClickGui.PANEL_RADIUS, 1.0f,
                gui.withAlpha(detailPanelFill(), gui.getAlpha(detailPanelFill()) * gui.guiAlpha),
                gui.withAlpha(gui.guiColors().glassBorder, gui.getAlpha(gui.guiColors().glassBorder) * gui.guiAlpha));
        drawPanelSurfaces(y);

        // 未选中模块时的空状态提示
        if (gui.selectedModule == null) {
            gui.drawCenteredText("No modules", gui.detailX, y + gui.panelH / 2.0f - 8.0f,
                    gui.detailX + gui.detailW, y + gui.panelH / 2.0f + 8.0f,
                    gui.withAlpha(gui.guiColors().muted, 210.0f * gui.guiAlpha));
            return;
        }

        // 绘制模块标题区域（图标 + 名称 + 描述 + 开关）
        float headerX = gui.detailX + 20.0f;
        float headerY = y + 20.0f;
        drawAnimeGirl(headerX + 200f, headerY - 12f);
        drawModuleIcon(gui.selectedModule, headerX + 13.0f, headerY + 11.0f);
        FontLoaders.F16.drawString(gui.trim(gui.selectedModule.getName(), FontLoaders.F16, gui.detailW - 116.0f),
                headerX + 38.0f, headerY + 1.0f, gui.withAlpha(gui.guiColors().text, 255.0f * gui.guiAlpha));
        gui.drawFont(gui.trim(gui.getDescription(gui.selectedModule), FontLoaders.F14, gui.detailW - 150.0f),
                headerX + 38.0f, headerY + 18.0f, gui.withAlpha(gui.guiColors().muted, 206.0f * gui.guiAlpha));
        gui.drawSwitch(gui.getDetailSwitchX(), gui.getDetailSwitchY(y), gui.selectedModule.getState(), 1.0f, gui.selectedModule);

        drawTabs(y);
        drawDetailValues(y, mouseX, mouseY);

        // 更新下拉栏动画并绘制（展开和收起都有动画）
        for (Mode mode : expandedModes) {
            if (gui.selectedModule != null && gui.selectedModule.getValues().contains(mode) && isModeVisible(mode)) {
                float current = dropdownAnim.containsKey(mode) ? dropdownAnim.get(mode) : 0f;
                current = gui.animate(current, 1f, 0.22f);
                dropdownAnim.put(mode, current);
            }
        }
        for (ModeProperty mode : expandedModeProperties) {
            if (gui.selectedModule != null && gui.selectedModule.getValues().contains(mode) && isModePropertyVisible(mode)) {
                float current = dropdownPropertyAnim.containsKey(mode) ? dropdownPropertyAnim.get(mode) : 0f;
                current = gui.animate(current, 1f, 0.22f);
                dropdownPropertyAnim.put(mode, current);
            }
        }
        // 处理收起动画：不在 expandedModes 中的 mode 动画向 0
        for (Mode mode : new HashSet<>(dropdownAnim.keySet())) {
            if (!expandedModes.contains(mode)) {
                float current = dropdownAnim.get(mode);
                current = gui.animate(current, 0f, 0.24f);
                if (current < 0.005f) {
                    dropdownAnim.remove(mode);
                } else {
                    dropdownAnim.put(mode, current);
                }
            }
        }
        for (ModeProperty mode : new HashSet<>(dropdownPropertyAnim.keySet())) {
            if (!expandedModeProperties.contains(mode)) {
                float current = dropdownPropertyAnim.get(mode);
                current = gui.animate(current, 0f, 0.24f);
                if (current < 0.005f) {
                    dropdownPropertyAnim.remove(mode);
                } else {
                    dropdownPropertyAnim.put(mode, current);
                }
            }
        }
        // 更新标签页动画
        tabShake.tick();
        tabBounce.tick();
        // 绘制所有有动画进度的下拉栏
        for (Mode mode : dropdownAnim.keySet()) {
            if (gui.selectedModule != null && gui.selectedModule.getValues().contains(mode) && isModeVisible(mode)) {
                float progress = dropdownAnim.get(mode);
                if (progress > 0.01f) {
                    drawModeDropdown(mode, mouseX, mouseY, progress);
                }
            }
        }
        for (ModeProperty mode : dropdownPropertyAnim.keySet()) {
            if (gui.selectedModule != null && gui.selectedModule.getValues().contains(mode) && isModePropertyVisible(mode)) {
                float progress = dropdownPropertyAnim.get(mode);
                if (progress > 0.01f) {
                    drawModePropertyDropdown(mode, mouseX, mouseY, progress);
                }
            }
        }
    }

    /**
     * 处理鼠标点击事件。
     * <p>
     * 优先级：下拉栏选项 > 模块开关 > 标签页 > 行内设置值。
     */
    boolean mouseClicked(int mouseX, int mouseY, int mouseButton) {
        if (gui.selectedModule == null) {
            return false;
        }
        // 优先处理展开的下拉栏内的选项点击（仅限当前 module 的 mode）
        if (!expandedModes.isEmpty() && mouseButton == 0) {
            for (Mode mode : expandedModes) {
                if (gui.selectedModule != null && gui.selectedModule.getValues().contains(mode)
                        && isModeVisible(mode)
                        && handleDropdownClick(mode, mouseX, mouseY)) {
                    return true;
                }
            }
        }
        if (!expandedModeProperties.isEmpty() && mouseButton == 0) {
            for (ModeProperty mode : expandedModeProperties) {
                if (gui.selectedModule != null && gui.selectedModule.getValues().contains(mode)
                        && isModePropertyVisible(mode)
                        && handleModePropertyDropdownClick(mode, mouseX, mouseY)) {
                    return true;
                }
            }
        }
        // 模块开关
        float headerToggleX = gui.getDetailSwitchX();
        float headerToggleY = gui.getDetailSwitchY(gui.detailY);
        if (mouseButton == 0 && gui.isSwitchHit(headerToggleX, headerToggleY, mouseX, mouseY)) {
            gui.selectedModule.setState(!gui.selectedModule.getState());
            gui.clickProgress.put(gui.selectedModule, 1.0f);
            return true;
        }
        // 标签页点击
        if (mouseButton == 0 && handleTabClick(mouseX, mouseY)) {
            return true;
        }
        // 设置值区域点击
        float x = gui.getDetailValuesX();
        float y = gui.getDetailValuesY(gui.detailY);
        float w = gui.getDetailValuesWidth();
        float h = gui.getDetailValuesHeight();
        if (!VapeClickGui.isHovered(x - 8.0f, y, x + w + 8.0f, y + h, mouseX, mouseY)) {
            return false;
        }
        return handleInlineValueClick(gui.selectedModule, x, y + gui.settingsScroll, w, mouseX, mouseY, mouseButton);
    }

    /**
     * 处理设置区域的滚轮滚动。
     */
    boolean updateScroll(int mouseX, int mouseY, int wheel) {
        if (wheel == 0 || gui.selectedModule == null || !VapeClickGui.isHovered(gui.getDetailValuesX(), gui.getDetailValuesY(gui.detailY),
                gui.getDetailValuesX() + gui.getDetailValuesWidth(), gui.getDetailValuesY(gui.detailY) + gui.getDetailValuesHeight(),
                mouseX, mouseY)) {
            return false;
        }
        gui.targetSettingsScroll += wheel > 0 ? 28.0f : -28.0f;
        gui.targetSettingsScroll = gui.clamp(gui.targetSettingsScroll,
                -Math.max(0.0f, gui.getSettingsContentHeight(gui.selectedModule) - gui.getDetailValuesHeight()), 0.0f);
        return true;
    }

    /**
     * 更新数字滑块值（拖拽过程中每帧调用）。
     * <p>
     * 根据鼠标位置计算滑块百分比并更新值，支持自定义范围的双滑块联动。
     */
    void updateNumberValue(Numbers value, int mouseX, float x, float w) {
        double min = gui.draggingNumberCustomRange ? gui.draggingNumberMin : value.getMinimum().doubleValue();
        double max = gui.draggingNumberCustomRange ? gui.draggingNumberMax : value.getMaximum().doubleValue();
        double inc = value.getIncrement().doubleValue();
        if (inc <= 0.0D) {
            inc = 0.1D;
        }
        double pct = gui.clamp((mouseX - x) / w, 0.0D, 1.0D);
        double result = min + (max - min) * pct;
        result = Math.round(result / inc) * inc;
        result = Math.max(min, Math.min(max, result));
        // 双滑块联动：确保下界不超过上界
        if (gui.draggingNumberCustomRange && gui.draggingNumberPair != null
                && gui.draggingNumberPair.getValue() instanceof Number) {
            double pair = ((Number) gui.draggingNumberPair.getValue()).doubleValue();
            result = gui.draggingNumberLowerBound ? Math.min(result, pair) : Math.max(result, pair);
        }
        value.setNumberValue(result);
    }

    /** 计算滑块百分比 */
    private float pct(Numbers value, double min, double max) {
        double current = ((Number) value.getValue()).doubleValue();
        return (float) gui.clamp((current - min) / Math.max(0.0001D, max - min), 0.0D, 1.0D);
    }

    /**
     * 更新颜色值（在颜色面板拖拽过程中每帧调用）。
     */
    void updateColorValue(int mouseX, int mouseY) {
        if (gui.draggingColorRed == null || gui.draggingColorGreen == null || gui.draggingColorBlue == null) {
            return;
        }
        setColorFromPalette(gui.draggingColorRed, gui.draggingColorGreen, gui.draggingColorBlue,
                mouseX, mouseY, gui.draggingColorX, gui.draggingColorY, gui.draggingColorW, gui.draggingColorH);
    }

    /**
     * 处理行内设置值的点击。
     * <p>
     * 根据值的类型分发到不同的处理逻辑：
     * <ul>
     *   <li>颜色：触发颜色面板点击</li>
     *   <li>范围滑块：触发双滑块拖拽</li>
     *   <li>Option：切换布尔值</li>
     *   <li>Mode：展开/收起下拉栏</li>
     *   <li>Numbers：触发单滑块拖拽</li>
     * </ul>
     */
    private boolean handleInlineValueClick(Module module, float x, float valueY, float width,
                                           int mouseX, int mouseY, int mouseButton) {
        List<Value> values = module.getValues();
        for (int i = 0; i < values.size(); i++) {
            if (!gui.isDetailValueVisible(module, i)) {
                continue;
            }
            Value value = values.get(i);
            float valueH = gui.getValueHeight(module, i);
            if (VapeClickGui.isHovered(x, valueY, x + width, valueY + valueH, mouseX, mouseY)) {
                // 颜色面板点击
                if (gui.isColorStart(module, i) && mouseButton == 0) {
                    Numbers red = (Numbers) values.get(i);
                    Numbers green = (Numbers) values.get(i + 1);
                    Numbers blue = (Numbers) values.get(i + 2);
                    return handlePaletteClick(module, red, green, blue, x, valueY, width, mouseX, mouseY);
                }
                // 范围滑块点击
                if (gui.isRangeStart(module, i) && mouseButton == 0) {
                    Numbers min = (Numbers) values.get(i);
                    Numbers max = (Numbers) values.get(i + 1);
                    float barX = gui.getSliderBarX(x, width);
                    float barW = gui.getSliderBarWidth(width);
                    double sliderMin = Math.min(min.getMinimum().doubleValue(), max.getMinimum().doubleValue());
                    double sliderMax = Math.max(min.getMaximum().doubleValue(), max.getMaximum().doubleValue());
                    float minPct = pct(min, sliderMin, sliderMax);
                    float maxPct = pct(max, sliderMin, sliderMax);
                    // 选择更近的滑块端点
                    Numbers target = Math.abs(mouseX - (barX + barW * minPct))
                            <= Math.abs(mouseX - (barX + barW * maxPct)) ? min : max;
                    gui.draggingNumber = target;
                    gui.draggingNumberX = barX;
                    gui.draggingNumberW = barW;
                    gui.draggingNumberCustomRange = true;
                    gui.draggingNumberMin = sliderMin;
                    gui.draggingNumberMax = sliderMax;
                    gui.draggingNumberPair = target == min ? max : min;
                    gui.draggingNumberLowerBound = target == min;
                    updateNumberValue(target, mouseX, barX, barW);
                    gui.valueActiveProgress.put(min, 1.0f);
                    gui.valueActiveProgress.put(max, 1.0f);
                    return true;
                }
                // 布尔选项
                if (value instanceof Option && mouseButton == 0) {
                    value.setValue(!Boolean.TRUE.equals(value.getValue()));
                    gui.valueActiveProgress.put(value, 1.0f);
                    return true;
                }
                // 模式选择
                if (value instanceof Mode && mouseButton == 0) {
                    Mode mode = (Mode) value;
                    if (expandedModes.contains(mode)) {
                        expandedModes.remove(mode);
                    } else {
                        expandedModes.add(mode);
                    }
                    gui.valueActiveProgress.put(value, 1.0f);
                    return true;
                }
                if (value instanceof ModeProperty && mouseButton == 0) {
                    ModeProperty mode = (ModeProperty) value;
                    if (expandedModeProperties.contains(mode)) {
                        expandedModeProperties.remove(mode);
                    } else {
                        expandedModeProperties.add(mode);
                    }
                    gui.valueActiveProgress.put(value, 1.0f);
                    return true;
                }
                // 数字滑块
                if (value instanceof Numbers && mouseButton == 0) {
                    gui.draggingNumber = value;
                    gui.draggingNumberX = gui.getSliderBarX(x, width);
                    gui.draggingNumberW = gui.getSliderBarWidth(width);
                    gui.draggingNumberCustomRange = false;
                    gui.draggingNumberPair = null;
                    updateNumberValue((Numbers) value, mouseX, gui.draggingNumberX, gui.draggingNumberW);
                    return true;
                }
            }
            valueY += valueH;
        }
        return false;
    }

    /**
     * 绘制模块图标（带毛玻璃背景和发光阴影）。
     */
    private void drawModuleIcon(Module module, float centerX, float centerY) {
        gui.drawThemedGlass(centerX - 13.0f, centerY - 13.0f, centerX + 13.0f, centerY + 13.0f,
                8.0f, 0.8f, gui.withAlpha(gui.guiColors().glassFillSoft, 178.0f * gui.guiAlpha),
                gui.withAlpha(gui.guiColors().accent, 74.0f * gui.guiAlpha));
        RenderServices.shapes().shadow(centerX - 13.0f, centerY - 13.0f, centerX + 13.0f, centerY + 13.0f,
                8.0f, gui.withAlpha(gui.guiColors().accent, 34.0f * gui.guiAlpha), 4, 2.0f);
        gui.drawCenteredIcon(ClickGuiIcons.forModule(module), FontLoaders.I20, centerX, centerY,
                gui.withAlpha(gui.guiColors().accent, 236.0f * gui.guiAlpha));
    }

    private void drawPanelSurfaces(float panelY) {
        if (isUnifiedDarkSurface()) {
            return;
        }
        float headerX = gui.detailX + 7.0f;
        float headerY = panelY + 7.0f;
        float headerW = gui.detailW - 14.0f;
        float headerH = 84.0f;
        int headerFill = surfaceColor(true);
        int contentFill = surfaceColor(false);

        RenderServices.shapes().roundedBorder(headerX, headerY, headerX + headerW, headerY + headerH, 7.0f, 0.7f,
                gui.withAlpha(headerFill, 116.0f * gui.guiAlpha),
                gui.withAlpha(gui.guiColors().glassBorder, 28.0f * gui.guiAlpha));
        if (gq.yozakura.module.render.HUD.isSakuraTheme()) {
            RenderServices.shapes().horizontalGradient(headerX + 2.0f, headerY + 2.0f,
                    headerX + headerW - 2.0f, headerY + 30.0f,
                    gui.withAlpha(new Color(255, 232, 244).getRGB(), 82.0f * gui.guiAlpha),
                    gui.withAlpha(new Color(255, 250, 253).getRGB(), 28.0f * gui.guiAlpha));
        }

        float contentX = gui.detailX + 13.0f;
        float contentY = gui.getDetailValuesY(panelY) - 2f;
        float contentW = gui.detailW - 26.0f;
        float contentH = Math.max(24.0f, gui.panelH - (contentY - panelY) - 10.0f);
        RenderServices.shapes().roundedBorder(contentX, contentY, contentX + contentW, contentY + contentH, 7.0f, 0.6f,
                gui.withAlpha(contentFill, 64.0f * gui.guiAlpha),
                gui.withAlpha(gui.guiColors().glassBorder, 22.0f * gui.guiAlpha));
    }

    private int surfaceColor(boolean header) {
        if (gq.yozakura.module.render.HUD.isSakuraTheme()) {
            return header ? new Color(255, 236, 246).getRGB() : new Color(255, 252, 254).getRGB();
        }
        if (gq.yozakura.module.render.HUD.isLightTheme()) {
            return header ? new Color(226, 232, 242).getRGB() : new Color(246, 248, 252).getRGB();
        }
        return new Color(11, 14, 20).getRGB();
    }

    private int detailPanelFill() {
        if (isUnifiedDarkSurface()) {
            return new Color(11, 14, 20, 226).getRGB();
        }
        return gui.guiColors().glassFill;
    }

    private boolean isUnifiedDarkSurface() {
        return !gq.yozakura.module.render.HUD.isSakuraTheme()
                && !gq.yozakura.module.render.HUD.isLightTheme();
    }

    /**
     * 检查指定标签页是否有可见的设置值。
     * <p>
     * 不依赖 {@code isDetailValueVisible}（该方法会与当前 {@code detailTabIndex} 比较），
     * 而是直接检查值本身是否可见且归属于目标标签页。
     */
    private boolean tabHasValues(int tabIndex) {
        if (gui.selectedModule == null) {
            return false;
        }
        for (int i = 0; i < gui.selectedModule.getValues().size(); i++) {
            Value value = gui.selectedModule.getValues().get(i);
            if (!value.isVisible()
                    || gui.isHiddenPaletteValue(gui.selectedModule, value)
                    || gui.isColorContinuation(gui.selectedModule, i)
                    || gui.isRangeContinuation(gui.selectedModule, i)) {
                continue;
            }
            if (gui.getDetailValueTab(gui.selectedModule, i) == tabIndex) {
                return true;
            }
        }
        return false;
    }

    /**
     * 绘制详情标签页（General / Targets / Extra / Rotation / Visuals）。
     * <p>
     * 每个标签页等宽排列，激活标签页底部有高亮指示线和渐变阴影。
     * 没有值的标签页文字显示为浅灰色。
     */
    private void drawTabs(float panelY) {
        float tabX = gui.detailX + 6.0f;
        float tabY = panelY + 58.0f;
        float tabW = gui.detailW - 12.0f;
        float tabH = 31.0f;
        // 标签栏背景
        if (!isUnifiedDarkSurface()) {
            gui.drawThemedGlass(tabX, tabY, tabX + tabW, tabY + tabH, 5.0f, 0.8f,
                    gui.withAlpha(gui.guiColors().detailSelectedFill, 96.0f * gui.guiAlpha),
                    gui.withAlpha(gui.guiColors().glassBorder, 34.0f * gui.guiAlpha));
        }
        float each = tabW / DETAIL_TABS.length;
        // 底部指示线目标位置
        float targetIndicatorX = tabX + each * gui.detailTabIndex + 9.0f;
        if (tabIndicatorX < 0f) tabIndicatorX = targetIndicatorX;
        // 拖拽面板时跳过动画，直接定位，避免指示线滞后漂动
        tabIndicatorX = (gui.draggingDetail || gui.draggingModuleList)
                ? targetIndicatorX
                : gui.animate(tabIndicatorX, targetIndicatorX, 0.14f);
        for (int i = 0; i < DETAIL_TABS.length; i++) {
            float x = tabX + each * i;
            boolean active = i == gui.detailTabIndex;
            boolean hasValues = tabHasValues(i);
            // 没有值的标签页使用浅灰色；有值未选中的标签页颜色更深以作区分
            int textColor = !hasValues ? gui.guiColors().faint
                    : active ? gui.guiColors().text : gui.guiColors().muted;
            float textAlpha = !hasValues ? 120.0f
                    : active ? 238.0f : 224.0f;
            // 计算摇晃和弹跳偏移
            float shakeOffset = AnimUtil.shakeX(tabShake.get(i));
            float bounceOffsetY = AnimUtil.bounceY(tabBounce.get(i));
            float bounceScale = AnimUtil.bounceScale(tabBounce.get(i));
            // 标签文字（应用摇晃和弹跳偏移）
            float textTop = tabY + 10.0f + bounceOffsetY;
            float textBottom = tabY + 22.0f + bounceOffsetY;
            gui.drawCenteredText(DETAIL_TABS[i], x + shakeOffset, textTop, x + each + shakeOffset, textBottom,
                    gui.withAlpha(textColor, textAlpha * gui.guiAlpha));
        }
        // 底部指示线（使用动画位置）
        if (tabHasValues(gui.detailTabIndex)) {
            RenderServices.shapes().shadow(tabIndicatorX, tabY + tabH - 2.0f, tabIndicatorX + each - 18.0f, tabY + tabH,
                    2.0f, gui.withAlpha(gui.guiColors().accent, 100.0f * gui.guiAlpha), 4, 2.0f);
            RenderServices.shapes().horizontalGradient(tabIndicatorX, tabY + tabH - 1.4f, tabIndicatorX + each - 18.0f, tabY + tabH - 0.4f,
                    gui.withAlpha(gui.guiColors().accent, 215.0f * gui.guiAlpha),
                    gui.withAlpha(new Color(152, 135, 255).getRGB(), 215.0f * gui.guiAlpha));
        }
    }

    /**
     * 处理标签页点击。
     * <p>
     * 切换标签页时重置滚动位置和拖拽状态。
     * 没有值的标签页不会响应点击。
     */
    private boolean handleTabClick(int mouseX, int mouseY) {
        float tabX = gui.detailX + 6.0f;
        float tabY = gui.detailY + currentIntroY + 58.0f;
        float tabW = gui.detailW - 12.0f;
        float tabH = 31.0f;
        if (!VapeClickGui.isHovered(tabX, tabY, tabX + tabW, tabY + tabH, mouseX, mouseY)) {
            return false;
        }
        int index = (int) ((mouseX - tabX) / (tabW / DETAIL_TABS.length));
        int next = Math.max(0, Math.min(DETAIL_TABS.length - 1, index));
        // 如果目标标签页没有值，触发摇晃动画并阻止切换
        if (!tabHasValues(next)) {
            tabShake.trigger(next);
            return false;
        }
        if (gui.detailTabIndex != next) {
            gui.detailTabIndex = next;
            // 触发选中标签页的弹跳动画
            tabBounce.trigger(next);
            // 切换标签页时重置滚动和拖拽状态
            gui.settingsScroll = 0.0f;
            gui.targetSettingsScroll = 0.0f;
            gui.draggingNumber = null;
            gui.draggingNumberCustomRange = false;
            gui.draggingNumberPair = null;
            gui.clearDraggingColor();
            // 重置可见滑条的 animX，触发入场动画
            if (gui.selectedModule != null) {
                for (int vi = 0; vi < gui.selectedModule.getValues().size(); vi++) {
                    if (gui.isDetailValueVisible(gui.selectedModule, vi)) {
                        gui.selectedModule.getValues().get(vi).animX = 0f;
                    }
                }
            }
        }
        return true;
    }

    /**
     * 处理颜色面板点击。
     * <p>
     * 双击切换彩虹模式，单击开始拖拽颜色选择。
     */
    private boolean handlePaletteClick(Module module, Numbers red, Numbers green, Numbers blue,
                                       float x, float y, float w, int mouseX, int mouseY) {
        float[] bounds = getPaletteBounds(x, y, w);
        if (!VapeClickGui.isHovered(bounds[0], bounds[1], bounds[0] + bounds[2], bounds[1] + bounds[3],
                mouseX, mouseY)) {
            return false;
        }
        // 双击检测（330ms 内两次点击同一颜色面板）
        String key = module.getName() + ":" + red.getName() + ":" + green.getName() + ":" + blue.getName();
        long now = System.currentTimeMillis();
        boolean doubleClick = key.equals(gui.lastPaletteClickKey) && now - gui.lastPaletteClickMS <= 330L;
        gui.lastPaletteClickKey = key;
        gui.lastPaletteClickMS = now;
        gui.valueActiveProgress.put(red, 1.0f);
        if (doubleClick) {
            setRainbowMode(module, true);
            gui.clearDraggingColor();
            return true;
        }
        setRainbowMode(module, false);
        gui.beginDraggingColor(red, green, blue, bounds[0], bounds[1], bounds[2], bounds[3]);
        setColorFromPalette(red, green, blue, mouseX, mouseY, bounds[0], bounds[1], bounds[2], bounds[3]);
        return true;
    }

    /**
     * 绘制颜色选择面板。
     * <p>
     * 包含：标签、色相-亮度拾取区、颜色预览块、彩虹模式指示和双击提示。
     */
    private void drawColorPalette(Module module, Numbers red, Numbers green, Numbers blue,
                                  float x, float y, float w, float alpha, int mouseX, int mouseY) {
        int color = rgb(red, green, blue);
        boolean rainbow = isRainbowMode(module);
        float[] bounds = getPaletteBounds(x, y, w);
        float paletteX = bounds[0];
        float paletteY = bounds[1];
        float paletteW = bounds[2];
        float paletteH = bounds[3];
        float preview = 25.0f;
        float previewX = Math.min(x + w - preview, paletteX + paletteW + 12.0f);
        float active = gui.animateValueMap(gui.valueActiveProgress, red, gui.draggingColorRed == red ? 1.0f : 0.0f, 0.20f);

        // 颜色标签和数值
        gui.drawFont("Color", x, y + 8.0f,
                gui.withAlpha(gui.guiColors().text, 245.0f * alpha * gui.guiAlpha));
        gui.drawFont(rainbow ? "RGB Rainbow" : toHex(color), x, y + 25.0f,
                gui.withAlpha(rainbow ? gui.guiColors().accent : gui.guiColors().muted, 205.0f * alpha * gui.guiAlpha));

        // 色相-亮度面板
        RenderServices.shapes().shadow(paletteX, paletteY, paletteX + paletteW, paletteY + paletteH, 5.0f,
                gui.withAlpha(color, (36.0f + active * 64.0f) * alpha * gui.guiAlpha), 5, 3.0f);
        gui.drawThemedGlass(paletteX - 1.0f, paletteY - 1.0f, paletteX + paletteW + 1.0f,
                paletteY + paletteH + 1.0f, 6.0f, 0.8f,
                gui.withAlpha(gui.guiColors().glassFillSoft, 120.0f * alpha * gui.guiAlpha),
                gui.withAlpha(rainbow ? gui.guiColors().accent : gui.guiColors().glassBorder,
                        (rainbow ? 95.0f : 52.0f) * alpha * gui.guiAlpha));
        RenderServices.shapes().roundedHue(paletteX, paletteY, paletteX + paletteW, paletteY + paletteH,
                5.0f, alpha * gui.guiAlpha);
        RenderServices.shapes().roundedBorder(paletteX, paletteY, paletteX + paletteW, paletteY + paletteH,
                5.0f, 0.8f, 0x00000000,
                gui.withAlpha(new Color(255, 255, 255).getRGB(), 54.0f * alpha * gui.guiAlpha));

        // 当前颜色位置标记（白环 + 内部实心圆）
        float[] marker = getColorMarker(red, green, blue, paletteX, paletteY, paletteW, paletteH);
        RenderServices.shapes().circleOutline(marker[0], marker[1], 4.0f + active, 1.2f,
                gui.withAlpha(new Color(255, 255, 255).getRGB(), 235.0f * alpha * gui.guiAlpha));
        RenderServices.shapes().circle(marker[0], marker[1], 0, 360, 2.1f,
                gui.withAlpha(color, 240.0f * alpha * gui.guiAlpha));

        // 颜色预览块
        gui.drawThemedGlass(previewX, y + 9.0f, previewX + preview, y + 34.0f, 7.0f, 0.8f,
                gui.withAlpha(color, 230.0f * alpha * gui.guiAlpha),
                gui.withAlpha(new Color(255, 255, 255).getRGB(), 70.0f * alpha * gui.guiAlpha));
        if (rainbow) {
            RenderServices.shapes().circle(previewX + preview - 5.5f, y + 13.5f, 0, 360, 2.6f,
                    gui.withAlpha(gui.guiColors().accent, 230.0f * alpha * gui.guiAlpha));
        }

        // 操作提示
        gui.drawFont("Double-click: RGB", paletteX, paletteY + paletteH + 6.0f,
                gui.withAlpha(rainbow ? gui.guiColors().accent : gui.guiColors().faint,
                        160.0f * alpha * gui.guiAlpha));
    }

    /**
     * 计算颜色面板的布局边界。
     *
     * @return float[4] = {paletteX, paletteY, paletteW, paletteH}
     */
    private float[] getPaletteBounds(float x, float y, float w) {
        float labelW = gui.getDetailLabelWidth(w);
        float preview = 25.0f;
        float gap = 12.0f;
        float minPaletteW = 76.0f;
        // 空间不足时压缩标签宽度
        if (w - labelW - preview - gap < minPaletteW) {
            labelW = Math.max(58.0f, w - preview - gap - minPaletteW);
        }
        float paletteX = x + labelW;
        float paletteY = y + 9.0f;
        float paletteW = Math.max(48.0f, w - labelW - preview - gap);
        return new float[]{paletteX, paletteY, paletteW, 25.0f};
    }

    /**
     * 根据鼠标在颜色面板中的位置设置 RGB 值。
     * <p>
     * X 轴对应色相（Hue），Y 轴对应亮度（Brightness），饱和度固定为 0.86。
     */
    private void setColorFromPalette(Numbers red, Numbers green, Numbers blue,
                                     int mouseX, int mouseY, float x, float y, float w, float h) {
        float hue = gui.clamp((mouseX - x) / Math.max(1.0f, w), 0.0f, 1.0f);
        float vertical = gui.clamp((mouseY - y) / Math.max(1.0f, h), 0.0f, 1.0f);
        float brightness = 1.0f - vertical * (1.0f - PALETTE_MIN_BRIGHTNESS);
        int rgb = Color.HSBtoRGB(hue, PALETTE_SATURATION, brightness);
        setNumber(red, rgb >> 16 & 255);
        setNumber(green, rgb >> 8 & 255);
        setNumber(blue, rgb & 255);
        // 缓存色相和颜色用于标记定位
        paletteHueByRed.put(red, hue);
        paletteColorByRed.put(red, rgb & 0xFFFFFF);
    }

    /**
     * 计算颜色标记在面板中的像素坐标。
     * <p>
     * 优先使用缓存的色相值定位，避免 HSB 转换精度问题。
     *
     * @return float[2] = {markerX, markerY}
     */
    private float[] getColorMarker(Numbers red, Numbers green, Numbers blue, float x, float y, float w, float h) {
        int currentColor = rgb(red, green, blue) & 0xFFFFFF;
        float[] hsb = Color.RGBtoHSB(colorValue(red), colorValue(green), colorValue(blue), null);
        Float storedHue = paletteHueByRed.get(red);
        Integer storedColor = paletteColorByRed.get(red);
        float hue = storedHue != null && storedColor != null && storedColor == currentColor
                ? gui.clamp(storedHue, 0.0f, 1.0f) : hsb[0];
        float innerRadius = Math.min(PALETTE_MARKER_RADIUS, Math.min(w, h) * 0.5f);
        float mx = gui.clamp(x + hue * w, x + innerRadius, x + w - innerRadius);
        float brightnessRange = Math.max(0.0001f, 1.0f - PALETTE_MIN_BRIGHTNESS);
        float my = gui.clamp(y + gui.clamp((1.0f - hsb[2]) / brightnessRange, 0.0f, 1.0f) * h,
                y + innerRadius, y + h - innerRadius);
        return new float[]{mx, my};
    }

    /**
     * 设置模块的彩虹模式。
     * <p>
     * 遍历模块的所有值，查找名为 "rainbow" 的 Option 或包含 "color" 的 Mode，
     * 并设置为对应模式（RAINBOW 或 STATIC）。
     */
    private void setRainbowMode(Module module, boolean enabled) {
        for (Value value : module.getValues()) {
            String name = gui.normalizeValueName(value);
            if (value instanceof Option && name.contains("rainbow")) {
                value.setValue(enabled);
            }
            if (value instanceof Mode && (name.equals("color") || name.contains("color"))) {
                Mode mode = (Mode) value;
                Object match = findMode(mode, enabled ? "RAINBOW" : "STATIC");
                if (match != null) {
                    mode.setValue(match);
                }
            }
        }
    }

    /** 检查模块是否处于彩虹模式 */
    private boolean isRainbowMode(Module module) {
        for (Value value : module.getValues()) {
            String name = gui.normalizeValueName(value);
            if (value instanceof Option && name.contains("rainbow") && Boolean.TRUE.equals(value.getValue())) {
                return true;
            }
            if (value instanceof Mode && (name.equals("color") || name.contains("color"))) {
                Object current = value.getValue();
                if (current instanceof Enum && "RAINBOW".equalsIgnoreCase(((Enum) current).name())) {
                    return true;
                }
            }
        }
        return false;
    }

    /** 在 Mode 的所有可选项中查找指定名称的枚举值 */
    private Object findMode(Mode mode, String wanted) {
        Object[] modes = mode.getModes();
        for (Object entry : modes) {
            if (entry instanceof Enum && wanted.equalsIgnoreCase(((Enum) entry).name())) {
                return entry;
            }
        }
        return null;
    }

    /** 将 RGB 三个 Numbers 值组合为 int 颜色 */
    private int rgb(Numbers red, Numbers green, Numbers blue) {
        return 0xFF000000 | colorValue(red) << 16 | colorValue(green) << 8 | colorValue(blue);
    }

    /** 安全获取 Numbers 值的 int 颜色分量（0-255） */
    private int colorValue(Numbers value) {
        Object raw = value.getValue();
        return Math.max(0, Math.min(255, raw instanceof Number ? ((Number) raw).intValue() : 0));
    }

    /** 设置 Numbers 值（0-255 范围） */
    private void setNumber(Numbers value, int color) {
        value.setNumberValue(Math.max(0, Math.min(255, color)));
    }

    /** 将 int 颜色转为 #RRGGBB 格式 */
    private String toHex(int color) {
        String hex = Integer.toHexString(color & 0xFFFFFF).toUpperCase();
        while (hex.length() < 6) {
            hex = "0" + hex;
        }
        return "#" + hex;
    }

    /**
     * 绘制所有设置值。
     * <p>
     * 在裁剪区域内渲染每个可见的设置项，并根据类型分发到对应的绘制方法。
     * 包含设置区域的滚动条。
     */
    private void drawDetailValues(float panelY, int mouseX, int mouseY) {
        float x = gui.getDetailValuesX();
        float y = gui.getDetailValuesY(panelY);
        float w = gui.getDetailValuesWidth();
        float h = gui.getDetailValuesHeight();
        Module module = gui.selectedModule;
        float contentHeight = gui.getSettingsContentHeight(module);
        RenderServices.shapes().roundedBorder(x - 8.0f, y - 6.0f, x + w + 8.0f, y + h + 4.0f, 7.0f, 0.6f,
                gui.withAlpha(surfaceColor(false), 42.0f * gui.guiAlpha),
                gui.withAlpha(gui.guiColors().glassBorder, 18.0f * gui.guiAlpha));
        // 空设置提示
        if (contentHeight <= 4.0f) {
            gui.drawFont("No settings in this section", x, y + 8.0f,
                    gui.withAlpha(gui.guiColors().muted, 210.0f * gui.guiAlpha));
            return;
        }
        gui.targetSettingsScroll = gui.clamp(gui.targetSettingsScroll, -Math.max(0.0f, contentHeight - h), 0.0f);
        gui.settingsScroll = gui.clamp(gui.settingsScroll, -Math.max(0.0f, contentHeight - h), 0.0f);

        // 裁剪区域绘制
        gui.beginScissor(x - 2.0f, y, w + 4.0f, h);
        try {
            float valueY = y + gui.settingsScroll;
            int index = 0;
            List<Value> values = module.getValues();
            for (int i = 0; i < values.size(); i++) {
                if (!gui.isDetailValueVisible(module, i)) {
                    continue;
                }
                Value value = values.get(i);
                float valueH = gui.getValueHeight(module, i);
                float rowAlpha = Math.max(0.0f, Math.min(1.0f, 1.0f - index * 0.015f));
                // 视口裁剪优化：仅绘制可见行
                if (valueY + valueH >= y - 2.0f && valueY <= y + h + 2.0f) {
                    // 根据值类型分发绘制
                    if (gui.isColorStart(module, i)) {
                        drawColorPalette(module, (Numbers) values.get(i), (Numbers) values.get(i + 1),
                                (Numbers) values.get(i + 2), x, valueY, w, rowAlpha, mouseX, mouseY);
                    } else if (gui.isRangeStart(module, i)) {
                        drawRangeNumber((Numbers) values.get(i), (Numbers) values.get(i + 1),
                                x, valueY, w, rowAlpha);
                    } else if (value instanceof ModeProperty) {
                        drawModeProperty((ModeProperty) value, x, valueY, w, rowAlpha);
                    } else if (value instanceof Option) {
                        drawOption((Option) value, x, valueY, w, rowAlpha);
                    } else if (value instanceof Numbers) {
                        drawNumber((Numbers) value, x, valueY, w, rowAlpha);
                    } else if (value instanceof Mode) {
                        drawMode((Mode) value, x, valueY, w, rowAlpha);
                    }
                }
                valueY += valueH;
                index++;
            }
        } finally {
            gui.endScissor();
        }
        drawSettingsScrollbar(panelY, contentHeight, h);
    }

    /** 绘制布尔选项行（标签 + 开关） */
    private void drawOption(Option value, float x, float y, float w, float alpha) {
        boolean enabled = Boolean.TRUE.equals(value.getValue());
        gui.drawFont(gui.trim(gui.getDisplayName(value), FontLoaders.F14, w - 68.0f), x, y + 12.0f,
                gui.withAlpha(enabled ? gui.guiColors().text : gui.guiColors().muted, 255.0f * alpha * gui.guiAlpha));
        gui.drawSwitch(gui.getOptionSwitchX(x, w), gui.getOptionSwitchY(y), enabled, alpha, value);
    }

    /** 绘制数字滑块行（标签 + 滑块 + 数值标签） */
    private void drawNumber(Numbers value, float x, float y, float w, float alpha) {
        double min = value.getMinimum().doubleValue();
        double max = value.getMaximum().doubleValue();
        double current = ((Number) value.getValue()).doubleValue();
        float pct = (float) ((current - min) / Math.max(0.0001D, max - min));
        pct = Math.max(0.0f, Math.min(1.0f, pct));
        value.animX = gui.animate(value.animX, pct, 0.18f);
        float active = gui.animateValueMap(gui.valueActiveProgress, value, gui.draggingNumber == value ? 1.0f : 0.0f, 0.24f);
        float labelW = gui.getDetailLabelWidth(w);
        float barX = gui.getSliderBarX(x, w);
        float barW = gui.getSliderBarWidth(w);
        float barY = y + 13.0f;
        float pillW = gui.getDetailValuePillWidth();
        float pillX = x + w - pillW;
        // 标签
        gui.drawFont(gui.trim(gui.getDisplayName(value), FontLoaders.F14, labelW - 8.0f), x, y + 12.0f,
                gui.withAlpha(gui.guiColors().text, 245.0f * alpha * gui.guiAlpha));
        // 滑块轨道
        RenderServices.shapes().rounded(barX, barY, barX + barW, barY + 2.0f, 2.0f,
                gui.withAlpha(gui.guiColors().valueTrack, 178.0f * alpha * gui.guiAlpha));
        // 滑块填充
        RenderServices.shapes().progressBar(barX, barY, barX + barW, barY + 2.0f, 2.0f, value.animX,
                0x00000000, gui.withAlpha(gui.guiColors().valueFill, 230.0f * alpha * gui.guiAlpha));
        // 滑块拖拽手柄（带阴影）
        float knob = 3.2f + active * 1.0f;
        RenderServices.shapes().shadow(barX + barW * value.animX - knob, barY - 3.0f,
                barX + barW * value.animX + knob, barY + 5.0f, 4.0f,
                gui.withAlpha(gui.guiColors().valueFill, 100.0f * alpha * gui.guiAlpha), 4, 2.0f);
        RenderServices.shapes().rounded(barX + barW * value.animX - knob, barY - knob + 1.0f,
                barX + barW * value.animX + knob, barY + knob + 1.0f, knob,
                gui.withAlpha(gui.guiColors().valueFill, 255.0f * alpha * gui.guiAlpha));
        // 数值标签
        drawValuePill(gui.formatNumber(current), pillX, y + 3.0f, pillW, alpha);
    }

    /** 绘制范围滑块行（两个可拖拽手柄，用于设置最小/最大值） */
    private void drawRangeNumber(Numbers minValue, Numbers maxValue, float x, float y, float w, float alpha) {
        double sliderMin = Math.min(minValue.getMinimum().doubleValue(), maxValue.getMinimum().doubleValue());
        double sliderMax = Math.max(minValue.getMaximum().doubleValue(), maxValue.getMaximum().doubleValue());
        double first = ((Number) minValue.getValue()).doubleValue();
        double second = ((Number) maxValue.getValue()).doubleValue();
        float firstPct = pct(minValue, sliderMin, sliderMax);
        float secondPct = pct(maxValue, sliderMin, sliderMax);
        minValue.animX = gui.animate(minValue.animX, firstPct, 0.18f);
        maxValue.animX = gui.animate(maxValue.animX, secondPct, 0.18f);
        float lowPct = Math.min(minValue.animX, maxValue.animX);
        float highPct = Math.max(minValue.animX, maxValue.animX);
        float activeMin = gui.animateValueMap(gui.valueActiveProgress, minValue,
                gui.draggingNumber == minValue ? 1.0f : 0.0f, 0.24f);
        float activeMax = gui.animateValueMap(gui.valueActiveProgress, maxValue,
                gui.draggingNumber == maxValue ? 1.0f : 0.0f, 0.24f);
        float active = Math.max(activeMin, activeMax);
        float labelW = gui.getDetailLabelWidth(w);
        float barX = gui.getSliderBarX(x, w);
        float barW = gui.getSliderBarWidth(w);
        float barY = y + 13.0f;
        float pillW = gui.getDetailValuePillWidth();
        float pillX = x + w - pillW;
        String rangeText = gui.formatNumber(Math.min(first, second)) + " - " + gui.formatNumber(Math.max(first, second));

        // 标签和数值
        gui.drawFont(gui.trim(gui.getRangeDisplayName(minValue), FontLoaders.F14, labelW - 8.0f),
                x, y + 12.0f, gui.withAlpha(gui.guiColors().text, 245.0f * alpha * gui.guiAlpha));
        drawValuePill(rangeText, pillX, y + 3.0f, pillW, alpha);
        // 轨道
        RenderServices.shapes().rounded(barX, barY, barX + barW, barY + 2.2f, 2.0f,
                gui.withAlpha(gui.guiColors().valueTrack, 178.0f * alpha * gui.guiAlpha));
        // 范围填充
        RenderServices.shapes().rounded(barX + barW * lowPct, barY, barX + barW * highPct, barY + 2.2f, 2.0f,
                gui.withAlpha(gui.guiColors().valueFill, 230.0f * alpha * gui.guiAlpha));
        // 两个拖拽手柄
        drawRangeKnob(barX + barW * minValue.animX, barY, 3.1f + activeMin * 1.0f, activeMin, alpha);
        drawRangeKnob(barX + barW * maxValue.animX, barY, 3.1f + activeMax * 1.0f, activeMax, alpha);
        // 激活时的范围发光阴影
        if (active > 0.02f) {
            RenderServices.shapes().shadow(barX + barW * lowPct, barY - 2.0f, barX + barW * highPct, barY + 4.0f,
                    3.0f, gui.withAlpha(gui.guiColors().valueFill, 62.0f * active * alpha * gui.guiAlpha),
                    4, 2.0f);
        }
    }

    /** 绘制范围滑块的拖拽手柄 */
    private void drawRangeKnob(float centerX, float barY, float knob, float active, float alpha) {
        RenderServices.shapes().shadow(centerX - knob, barY - 3.0f, centerX + knob, barY + 5.0f, 4.0f,
                gui.withAlpha(gui.guiColors().valueFill, 82.0f * (0.35f + active) * alpha * gui.guiAlpha),
                4, 2.0f);
        RenderServices.shapes().rounded(centerX - knob, barY - knob + 1.0f,
                centerX + knob, barY + knob + 1.0f, knob,
                gui.withAlpha(gui.guiColors().valueFill, 255.0f * alpha * gui.guiAlpha));
    }

    /** 绘制模式选择行（标签 + 下拉按钮） */
    private void drawMode(Mode value, float x, float y, float w, float alpha) {
        float labelW = gui.getDetailLabelWidth(w);
        float pillW = Math.min(112.0f, Math.max(72.0f, w - labelW));
        float pillX = x + w - pillW;
        boolean expanded = expandedModes.contains(value);
        gui.drawFont(gui.trim(gui.getDisplayName(value), FontLoaders.F14, labelW - 8.0f), x, y + 12.0f,
                gui.withAlpha(gui.guiColors().text, 245.0f * alpha * gui.guiAlpha));
        // 下拉按钮（展开时样式不同）
        float borderAlpha = expanded ? 110.0f : 48.0f;
        int fillColor = expanded
                ? gui.withAlpha(gui.guiColors().modeExpandedFill, 200.0f * alpha * gui.guiAlpha)
                : gui.withAlpha(gui.guiColors().glassFillSoft, 194.0f * alpha * gui.guiAlpha);
        gui.drawThemedGlass(pillX, y + 3.0f, pillX + pillW, y + 23.0f, 5.0f, 0.8f,
                fillColor,
                gui.withAlpha(gui.guiColors().glassBorder, borderAlpha * alpha * gui.guiAlpha));
        gui.drawFont(gui.trim(gui.formatModeLabel(value.getModeAsString()), FontLoaders.F16, pillW - 28.0f),
                pillX + 10.0f, y + 11.0f,
                gui.withAlpha(expanded ? gui.guiColors().accent : gui.guiColors().text,
                        (expanded ? 240.0f : 230.0f) * alpha * gui.guiAlpha));
        // 展开/收起箭头 — GL_LINE_STRIP 无重叠 V/^
        float arrowCX = pillX + pillW - 12.0f;
        float arrowCY = y + 13.0f;
        float arrowS = 2.8f;
        int arrowColor = gui.withAlpha(expanded ? gui.guiColors().accent : gui.guiColors().muted,
                (expanded ? 220.0f : 185.0f) * alpha * gui.guiAlpha);
        GLStateManager.begin2D();
        try {
            GL11.glLineWidth(1.3f);
            RenderUtil.glColor(arrowColor);
            GL11.glBegin(GL11.GL_LINE_STRIP);
            if (expanded) {
                GL11.glVertex2f(arrowCX - arrowS, arrowCY + 1.8f);
                GL11.glVertex2f(arrowCX, arrowCY - 2.0f);
                GL11.glVertex2f(arrowCX + arrowS, arrowCY + 1.8f);
            } else {
                GL11.glVertex2f(arrowCX - arrowS, arrowCY - 1.8f);
                GL11.glVertex2f(arrowCX, arrowCY + 2.0f);
                GL11.glVertex2f(arrowCX + arrowS, arrowCY - 1.8f);
            }
            GL11.glEnd();
        } finally {
            GLStateManager.end2D();
        }
    }

    private void drawModeProperty(ModeProperty value, float x, float y, float w, float alpha) {
        float labelW = gui.getDetailLabelWidth(w);
        float pillW = Math.min(112.0f, Math.max(72.0f, w - labelW));
        float pillX = x + w - pillW;
        boolean expanded = expandedModeProperties.contains(value);
        gui.drawFont(gui.trim(gui.getDisplayName(value), FontLoaders.F14, labelW - 8.0f), x, y + 12.0f,
                gui.withAlpha(gui.guiColors().text, 245.0f * alpha * gui.guiAlpha));
        float borderAlpha = expanded ? 110.0f : 48.0f;
        int fillColor = expanded
                ? gui.withAlpha(gui.guiColors().modeExpandedFill, 200.0f * alpha * gui.guiAlpha)
                : gui.withAlpha(gui.guiColors().glassFillSoft, 194.0f * alpha * gui.guiAlpha);
        gui.drawThemedGlass(pillX, y + 3.0f, pillX + pillW, y + 23.0f, 5.0f, 0.8f,
                fillColor,
                gui.withAlpha(gui.guiColors().glassBorder, borderAlpha * alpha * gui.guiAlpha));
        gui.drawFont(gui.trim(gui.formatModeLabel(value.getModeString()), FontLoaders.F16, pillW - 28.0f),
                pillX + 10.0f, y + 11.0f,
                gui.withAlpha(expanded ? gui.guiColors().accent : gui.guiColors().text,
                        (expanded ? 240.0f : 230.0f) * alpha * gui.guiAlpha));
        float arrowCX = pillX + pillW - 12.0f;
        float arrowCY = y + 13.0f;
        float arrowS = 2.8f;
        int arrowColor = gui.withAlpha(expanded ? gui.guiColors().accent : gui.guiColors().muted,
                (expanded ? 220.0f : 185.0f) * alpha * gui.guiAlpha);
        GLStateManager.begin2D();
        try {
            GL11.glLineWidth(1.3f);
            RenderUtil.glColor(arrowColor);
            GL11.glBegin(GL11.GL_LINE_STRIP);
            if (expanded) {
                GL11.glVertex2f(arrowCX - arrowS, arrowCY + 1.8f);
                GL11.glVertex2f(arrowCX, arrowCY - 2.0f);
                GL11.glVertex2f(arrowCX + arrowS, arrowCY + 1.8f);
            } else {
                GL11.glVertex2f(arrowCX - arrowS, arrowCY - 1.8f);
                GL11.glVertex2f(arrowCX, arrowCY + 2.0f);
                GL11.glVertex2f(arrowCX + arrowS, arrowCY - 1.8f);
            }
            GL11.glEnd();
        } finally {
            GLStateManager.end2D();
        }
    }

    /** 绘制数值标签（毛玻璃圆角矩形 + 居中文字） */
    private void drawValuePill(String text, float x, float y, float w, float alpha) {
        gui.drawThemedGlass(x, y, x + w, y + 20.0f, 5.0f, 0.8f,
                gui.withAlpha(gui.guiColors().glassFillSoft, 190.0f * alpha * gui.guiAlpha),
                gui.withAlpha(gui.guiColors().glassBorder, 46.0f * alpha * gui.guiAlpha));
        gui.drawCenteredText(text, x, y + 5.0f, x + w, y + 17.0f,
                gui.withAlpha(gui.guiColors().text, 220.0f * alpha * gui.guiAlpha));
    }

    /** 绘制设置区域的滚动条 */
    private void drawSettingsScrollbar(float panelY, float contentHeight, float viewHeight) {
        if (contentHeight <= viewHeight + 1.0f) {
            return;
        }
        float trackX = gui.detailX + gui.detailW - 10.0f;
        float trackY = gui.getDetailValuesY(panelY) + 2.0f;
        float trackH = viewHeight - 4.0f;
        float thumbH = Math.max(22.0f, viewHeight / Math.max(1.0f, contentHeight) * trackH);
        float maxScroll = Math.max(1.0f, contentHeight - viewHeight);
        float pct = gui.clamp(-gui.settingsScroll / maxScroll, 0.0f, 1.0f);
        float thumbY = trackY + (trackH - thumbH) * pct;
        // 轨道
        gui.drawSoftRect(trackX, trackY, trackX + 2.0f, trackY + trackH, 2.0f,
                gui.withAlpha(new Color(255, 255, 255, 26).getRGB(), 26.0f * gui.guiAlpha));
        // 滑块
        gui.drawSoftRect(trackX, thumbY, trackX + 2.0f, thumbY + thumbH, 2.0f,
                gui.withAlpha(gui.guiColors().accent, 150.0f * gui.guiAlpha));
    }

    // ==================== Mode 下拉栏 ====================

    /**
     * 计算给定 Mode 值在当前设置列表中的 Y 坐标。
     */
    private float getModeValueY(Mode value) {
        float y = gui.getDetailValuesY(gui.detailY + currentIntroY) + gui.settingsScroll;
        List<Value> values = gui.selectedModule.getValues();
        for (int i = 0; i < values.size(); i++) {
            if (!gui.isDetailValueVisible(gui.selectedModule, i)) {
                continue;
            }
            Value v = values.get(i);
            if (v == value) return y;
            y += gui.getValueHeight(gui.selectedModule, i);
        }
        return y;
    }

    /**
     * 绘制 Mode 展开后的下拉选项列表。
     * <p>
     * 使用裁剪区域限制下拉栏在详情面板内可见。
     * 高亮悬停行和当前选中行。
     */
    private void drawModeDropdown(Mode value, int mouseX, int mouseY, float animProgress) {
        Object[] modes = value.getModes();
        if (modes.length == 0) return;

        float labelW = gui.getDetailLabelWidth(gui.getDetailValuesWidth());
        float pillW = Math.min(112.0f, Math.max(72.0f, gui.getDetailValuesWidth() - labelW));
        float pillX = gui.getDetailValuesX() + gui.getDetailValuesWidth() - pillW;
        float valueY = getModeValueY(value);
        float detailY = gui.getDetailValuesY(gui.detailY + currentIntroY);
        float detailH = gui.getDetailValuesHeight();
        float dropdownY = valueY + 23.0f;
        float fullDropdownH = modes.length * DROPDOWN_ROW_H;

        // pill+下拉栏全部滚出 detail 区域 → 不渲染
        float pillBottom = valueY + 23.0f;
        float fullBottom = Math.max(pillBottom, dropdownY + fullDropdownH);
        float fullTop = Math.min(valueY, dropdownY);
        if (fullBottom < detailY || fullTop > detailY + detailH) return;

        // 用 scissor 裁剪：先限制到 detail 框，再用动画进度限制下拉栏高度
        float clipX = gui.getDetailValuesX();
        float clipW = gui.getDetailValuesWidth();
        float clipTop = Math.max(detailY, dropdownY);
        float clipH = Math.min(detailY + detailH, dropdownY + fullDropdownH * animProgress) - clipTop;
        if (clipH <= 0) return;
        gui.beginScissor(clipX - 4.0f, clipTop, clipW + 8.0f, clipH);
        try {
            // 检测悬停行
            int hoveredIndex = -1;
            for (int i = 0; i < modes.length; i++) {
                float rowY = dropdownY + i * DROPDOWN_ROW_H;
                if (VapeClickGui.isHovered(pillX, rowY, pillX + pillW, rowY + DROPDOWN_ROW_H, mouseX, mouseY)) {
                    hoveredIndex = i;
                    break;
                }
            }

            // 下拉栏背景 + 阴影
            gui.drawThemedGlass(pillX, dropdownY, pillX + pillW, dropdownY + fullDropdownH,
                    5.0f, 0.9f,
                    gui.withAlpha(gui.guiColors().dropdownBg, 238.0f * gui.guiAlpha),
                    gui.withAlpha(gui.guiColors().glassBorder, 62.0f * gui.guiAlpha));
            RenderServices.shapes().shadow(pillX, dropdownY, pillX + pillW, dropdownY + fullDropdownH,
                    5.0f, gui.withAlpha(gui.shadowColor(200), 82.0f * gui.guiAlpha), 6, 3.0f);

            // 每行选项
            for (int i = 0; i < modes.length; i++) {
                float rowY = dropdownY + i * DROPDOWN_ROW_H;
                boolean selected = modes[i] == value.getValue();
                boolean hovered = i == hoveredIndex;

                // 悬停/选中行高亮背景
                if (hovered || selected) {
                    gui.drawSoftRect(pillX + 2.0f, rowY + 1.0f, pillX + pillW - 2.0f,
                            rowY + DROPDOWN_ROW_H - 1.0f,
                            4.0f, gui.withAlpha(selected ? gui.guiColors().modeRowSelected
                                    : gui.guiColors().modeRowHovered,
                            (selected ? 178.0f : 110.0f) * gui.guiAlpha));
                }
                // 选项文字
                gui.drawFont(gui.trim(gui.formatModeLabel(modes[i].toString()), FontLoaders.F14, pillW - 20.0f),
                        pillX + 10.0f, rowY + 7.0f,
                        gui.withAlpha(selected ? gui.guiColors().accent : gui.guiColors().text,
                                (selected ? 240.0f : 210.0f) * gui.guiAlpha));
            }
        } finally {
            gui.endScissor();
        }
    }

    /**
     * 处理下拉栏选项的点击。
     * <p>
     * 点击某个选项后设置 Mode 值并关闭下拉栏。
     */
    private boolean handleDropdownClick(Mode value, int mouseX, int mouseY) {
        if (!isModeVisible(value)) {
            return false;
        }
        Object[] modes = value.getModes();
        float labelW = gui.getDetailLabelWidth(gui.getDetailValuesWidth());
        float pillW = Math.min(112.0f, Math.max(72.0f, gui.getDetailValuesWidth() - labelW));
        float pillX = gui.getDetailValuesX() + gui.getDetailValuesWidth() - pillW;
        float dropdownY = getModeValueY(value) + 23.0f;
        float animProgress = dropdownAnim.containsKey(value) ? dropdownAnim.get(value) : 0f;
        float visibleH = modes.length * DROPDOWN_ROW_H * animProgress;

        for (int i = 0; i < modes.length; i++) {
            float rowY = dropdownY + i * DROPDOWN_ROW_H;
            if (rowY >= dropdownY + visibleH) break;
            if (VapeClickGui.isHovered(pillX, rowY, pillX + pillW, rowY + DROPDOWN_ROW_H,
                    mouseX, mouseY)) {
                value.setValue(modes[i]);
                expandedModes.remove(value);
                gui.valueActiveProgress.put(value, 1.0f);
                return true;
            }
        }
        return false;
    }

    /** 检查 Mode 值是否在当前标签页可见 */
    private boolean isModeVisible(Mode mode) {
        if (gui.selectedModule == null || mode == null) {
            return false;
        }
        List<Value> values = gui.selectedModule.getValues();
        for (int i = 0; i < values.size(); i++) {
            if (values.get(i) == mode) {
                return gui.isDetailValueVisible(gui.selectedModule, i);
            }
        }
        return false;
    }

    private float getModePropertyValueY(ModeProperty value) {
        float y = gui.getDetailValuesY(gui.detailY + currentIntroY) + gui.settingsScroll;
        List<Value> values = gui.selectedModule.getValues();
        for (int i = 0; i < values.size(); i++) {
            if (!gui.isDetailValueVisible(gui.selectedModule, i)) {
                continue;
            }
            Value v = values.get(i);
            if (v == value) return y;
            y += gui.getValueHeight(gui.selectedModule, i);
        }
        return y;
    }

    private void drawModePropertyDropdown(ModeProperty value, int mouseX, int mouseY, float animProgress) {
        String[] modes = value.getModes();
        if (modes.length == 0) return;

        float labelW = gui.getDetailLabelWidth(gui.getDetailValuesWidth());
        float pillW = Math.min(112.0f, Math.max(72.0f, gui.getDetailValuesWidth() - labelW));
        float pillX = gui.getDetailValuesX() + gui.getDetailValuesWidth() - pillW;
        float valueY = getModePropertyValueY(value);
        float detailY = gui.getDetailValuesY(gui.detailY + currentIntroY);
        float detailH = gui.getDetailValuesHeight();
        float dropdownY = valueY + 23.0f;
        float fullDropdownH = modes.length * DROPDOWN_ROW_H;
        float fullBottom = Math.max(valueY + 23.0f, dropdownY + fullDropdownH);
        float fullTop = Math.min(valueY, dropdownY);
        if (fullBottom < detailY || fullTop > detailY + detailH) return;

        float clipX = gui.getDetailValuesX();
        float clipW = gui.getDetailValuesWidth();
        float clipTop = Math.max(detailY, dropdownY);
        float clipH = Math.min(detailY + detailH, dropdownY + fullDropdownH * animProgress) - clipTop;
        if (clipH <= 0) return;
        gui.beginScissor(clipX - 4.0f, clipTop, clipW + 8.0f, clipH);
        try {
            int hoveredIndex = -1;
            for (int i = 0; i < modes.length; i++) {
                float rowY = dropdownY + i * DROPDOWN_ROW_H;
                if (VapeClickGui.isHovered(pillX, rowY, pillX + pillW, rowY + DROPDOWN_ROW_H, mouseX, mouseY)) {
                    hoveredIndex = i;
                    break;
                }
            }

            gui.drawThemedGlass(pillX, dropdownY, pillX + pillW, dropdownY + fullDropdownH,
                    5.0f, 0.9f,
                    gui.withAlpha(gui.guiColors().dropdownBg, 238.0f * gui.guiAlpha),
                    gui.withAlpha(gui.guiColors().glassBorder, 62.0f * gui.guiAlpha));
            RenderServices.shapes().shadow(pillX, dropdownY, pillX + pillW, dropdownY + fullDropdownH,
                    5.0f, gui.withAlpha(gui.shadowColor(200), 82.0f * gui.guiAlpha), 6, 3.0f);

            for (int i = 0; i < modes.length; i++) {
                float rowY = dropdownY + i * DROPDOWN_ROW_H;
                boolean selected = i == value.getValue();
                boolean hovered = i == hoveredIndex;
                if (hovered || selected) {
                    gui.drawSoftRect(pillX + 2.0f, rowY + 1.0f, pillX + pillW - 2.0f,
                            rowY + DROPDOWN_ROW_H - 1.0f,
                            4.0f, gui.withAlpha(selected ? gui.guiColors().modeRowSelected
                                    : gui.guiColors().modeRowHovered,
                            (selected ? 178.0f : 110.0f) * gui.guiAlpha));
                }
                gui.drawFont(gui.trim(gui.formatModeLabel(modes[i]), FontLoaders.F14, pillW - 20.0f),
                        pillX + 10.0f, rowY + 7.0f,
                        gui.withAlpha(selected ? gui.guiColors().accent : gui.guiColors().text,
                                (selected ? 240.0f : 210.0f) * gui.guiAlpha));
            }
        } finally {
            gui.endScissor();
        }
    }

    private boolean handleModePropertyDropdownClick(ModeProperty value, int mouseX, int mouseY) {
        if (!isModePropertyVisible(value)) {
            return false;
        }
        String[] modes = value.getModes();
        float labelW = gui.getDetailLabelWidth(gui.getDetailValuesWidth());
        float pillW = Math.min(112.0f, Math.max(72.0f, gui.getDetailValuesWidth() - labelW));
        float pillX = gui.getDetailValuesX() + gui.getDetailValuesWidth() - pillW;
        float dropdownY = getModePropertyValueY(value) + 23.0f;
        float animProgress = dropdownPropertyAnim.containsKey(value) ? dropdownPropertyAnim.get(value) : 0f;
        float visibleH = modes.length * DROPDOWN_ROW_H * animProgress;

        for (int i = 0; i < modes.length; i++) {
            float rowY = dropdownY + i * DROPDOWN_ROW_H;
            if (rowY >= dropdownY + visibleH) break;
            if (VapeClickGui.isHovered(pillX, rowY, pillX + pillW, rowY + DROPDOWN_ROW_H,
                    mouseX, mouseY)) {
                value.setValue(i);
                expandedModeProperties.remove(value);
                gui.valueActiveProgress.put(value, 1.0f);
                return true;
            }
        }
        return false;
    }

    private boolean isModePropertyVisible(ModeProperty mode) {
        if (gui.selectedModule == null || mode == null) {
            return false;
        }
        List<Value> values = gui.selectedModule.getValues();
        for (int i = 0; i < values.size(); i++) {
            if (values.get(i) == mode) {
                return gui.isDetailValueVisible(gui.selectedModule, i);
            }
        }
        return false;
    }

    private void drawAnimeGirl(float posX, float posY) {
        switch (HUD.getTheme()) {
            case LIGHT:
                RenderUtil.drawTexturedRect(new ResourceLocation("wubolong/light.png"), posX, posY, posX+75f, posY+50f, .5f * gui.guiAlpha);
                break;
            case SAKURA:
                RenderUtil.drawTexturedRect(new ResourceLocation("wubolong/sakura.png"), posX, posY, posX+75f, posY+50f, .5f * gui.guiAlpha);
                break;
        }
    }
}
