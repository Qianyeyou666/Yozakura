package gq.vapulite.ui.click.vape;

import gq.vapulite.manager.ModuleManager;
import gq.vapulite.core.Client;
import gq.vapulite.module.ModuleType;
import gq.vapulite.module.Module;
import gq.vapulite.module.render.ClickGUI;
import gq.vapulite.value.Mode;
import gq.vapulite.value.Numbers;
import gq.vapulite.value.Option;
import gq.vapulite.value.Value;
import gq.vapulite.engine.font.CFontRenderer;
import gq.vapulite.engine.font.FontLoaders;
import gq.vapulite.engine.render.GLStateManager;
import gq.vapulite.engine.render.ShaderRenderer;
import gq.vapulite.engine.render.ui.RenderServices;
import gq.vapulite.ui.click.ClickGuiIcons;
import gq.vapulite.ui.UiTextField;
import gq.vapulite.ui.UiTheme;
import gq.vapulite.ui.UiToggle;
import gq.vapulite.util.animation.AnimUtil;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.ScaledResolution;
import org.lwjgl.input.Keyboard;
import com.google.gson.JsonObject;
import org.lwjgl.input.Mouse;

import java.awt.Color;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * VapuLite ClickGUI 主界面类，继承 Minecraft 的 {@link GuiScreen}。
 * <p>
 * 实现了一个 Material Design 3 风格的模块配置界面，包含以下主要区域：
 * <ul>
 *   <li><b>导航栏</b>：顶部标签页（Combat/Movement/Visual/Utility/World/Misc/Profiles）</li>
 *   <li><b>搜索栏</b>：支持模块名称和描述的关键词搜索</li>
 *   <li><b>模块列表</b>：左侧可滚动的模块卡片列表</li>
 *   <li><b>详情面板</b>：中央选中模块的设置项编辑区</li>
 *   <li><b>侧边面板</b>：右侧用户信息、系统状态和模块摘要</li>
 *   <li><b>底部栏</b>：用户配置和快捷键提示</li>
 * </ul>
 * <p>
 * 支持三种主题配色：Dark（暗色）、Light（浅色）、Sakura（粉色）。
 * GUI 状态（标签页、选中模块、滚动位置、展开下拉栏等）可通过 JSON 持久化。
 */
public class VapeClickGui extends GuiScreen {
    /**
     * 配色板内部类，封装了 GUI 所有组件的颜色常量。
     * <p>
     * 包含三种预设主题：{@link #DARK}、{@link #LIGHT}、{@link #SAKURA}。
     */
    static final class GuiPalette {
        final int backdrop, topBar, card, cardHover, cardOpen;
        final int text, muted, faint, accent, red;
        final int glassFill, glassFillSoft, glassBorder;
        final int navDefaultHover;
        final int detailSelectedFill, detailSelectedBorder;
        final int switchGlowColor;
        final int valueTrack, valueFill;
        final int modeExpandedFill, modeRowSelected, modeRowHovered;
        final int dropdownBg, dropdownShadow;
        final int shadowDim;

        GuiPalette(int backdrop, int topBar, int card, int cardHover, int cardOpen,
                   int text, int muted, int faint, int accent, int red,
                   int glassFill, int glassFillSoft, int glassBorder,
                   int navDefaultHover, int detailSelectedFill, int detailSelectedBorder,
                   int switchGlowColor, int valueTrack, int valueFill,
                   int modeExpandedFill, int modeRowSelected, int modeRowHovered,
                   int dropdownBg, int dropdownShadow, int shadowDim) {
            this.backdrop = backdrop; this.topBar = topBar; this.card = card; this.cardHover = cardHover;
            this.cardOpen = cardOpen; this.text = text; this.muted = muted; this.faint = faint;
            this.accent = accent; this.red = red; this.glassFill = glassFill; this.glassFillSoft = glassFillSoft;
            this.glassBorder = glassBorder; this.navDefaultHover = navDefaultHover;
            this.detailSelectedFill = detailSelectedFill; this.detailSelectedBorder = detailSelectedBorder;
            this.switchGlowColor = switchGlowColor; this.valueTrack = valueTrack; this.valueFill = valueFill;
            this.modeExpandedFill = modeExpandedFill; this.modeRowSelected = modeRowSelected;
            this.modeRowHovered = modeRowHovered; this.dropdownBg = dropdownBg;
            this.dropdownShadow = dropdownShadow; this.shadowDim = shadowDim;
        }

        /** 暗色主题配色（默认） */
        static final GuiPalette DARK = new GuiPalette(
                new Color(9, 13, 18, 164).getRGB(),
                new Color(12, 15, 20, 232).getRGB(),
                new Color(17, 21, 27, 222).getRGB(),
                new Color(25, 30, 38, 232).getRGB(),
                new Color(34, 35, 77, 238).getRGB(),
                new Color(232, 234, 236).getRGB(),
                new Color(152, 154, 158).getRGB(),
                new Color(83, 86, 92).getRGB(),
                new Color(132, 117, 255).getRGB(),
                new Color(196, 78, 83).getRGB(),
                new Color(7, 9, 13, 154).getRGB(),
                new Color(7, 9, 13, 122).getRGB(),
                new Color(154, 190, 214, 58).getRGB(),
                new Color(37, 43, 54, 190).getRGB(),
                new Color(55, 54, 130, 218).getRGB(),
                new Color(132, 121, 255).getRGB(),
                new Color(132, 117, 255).getRGB(),
                new Color(61, 67, 82, 178).getRGB(),
                new Color(132, 117, 255, 230).getRGB(),
                new Color(35, 38, 62, 200).getRGB(),
                new Color(88, 90, 178, 160).getRGB(),
                new Color(55, 58, 70, 140).getRGB(),
                new Color(18, 22, 30, 240).getRGB(),
                new Color(0, 0, 0, 200).getRGB(),
                new Color(0, 0, 0, 210).getRGB());

        /** 浅色主题配色（天蓝色系，透明度与 Sakura 对齐） */
        static final GuiPalette LIGHT = new GuiPalette(
                new Color(230, 235, 242, 148).getRGB(),
                new Color(220, 225, 234, 220).getRGB(),
                new Color(240, 244, 250, 210).getRGB(),
                new Color(225, 230, 240, 218).getRGB(),
                new Color(190, 218, 238, 225).getRGB(),   // cardOpen: 天蓝
                new Color(28, 30, 36).getRGB(),
                new Color(105, 110, 120).getRGB(),
                new Color(155, 162, 175).getRGB(),
                new Color(24, 142, 198).getRGB(),
                new Color(182, 50, 55).getRGB(),
                new Color(232, 236, 244, 250).getRGB(),   // glassFill
                new Color(225, 230, 240, 250).getRGB(),   // glassFillSoft
                new Color(155, 185, 210, 104).getRGB(),   // glassBorder: 天蓝调
                new Color(195, 218, 235, 170).getRGB(),   // navDefaultHover: 天蓝
                new Color(185, 212, 238, 210).getRGB(),   // detailSelectedFill: 天蓝
                new Color(115, 178, 232).getRGB(),        // detailSelectedBorder: 天蓝
                new Color(24, 142, 198).getRGB(),
                new Color(200, 216, 232, 170).getRGB(),   // valueTrack: 更浅天蓝
                new Color(120, 165, 235, 215).getRGB(),
                new Color(195, 220, 240, 190).getRGB(),   // modeExpandedFill: 天蓝
                new Color(175, 210, 236, 148).getRGB(),   // modeRowSelected: 天蓝
                new Color(195, 218, 235, 132).getRGB(),   // modeRowHovered: 天蓝
                new Color(218, 224, 238, 228).getRGB(),
                new Color(255, 255, 255, 120).getRGB(),
                new Color(255, 255, 255, 140).getRGB());

        /** 樱花/粉色主题配色 */
        static final GuiPalette SAKURA = new GuiPalette(
                new Color(248, 232, 239, 108).getRGB(),
                new Color(255, 248, 252, 244).getRGB(),
                new Color(255, 249, 252, 246).getRGB(),
                new Color(253, 241, 248, 248).getRGB(),
                new Color(255, 220, 237, 242).getRGB(),
                new Color(36, 30, 38).getRGB(),
                new Color(122, 108, 119).getRGB(),
                new Color(174, 154, 168).getRGB(),
                new Color(229, 107, 157).getRGB(),
                new Color(195, 60, 78).getRGB(),
                new Color(255, 249, 252, 250).getRGB(),
                new Color(255, 249, 252, 250).getRGB(),
                new Color(226, 165, 194, 104).getRGB(),
                new Color(255, 228, 241, 214).getRGB(),
                new Color(250, 195, 220, 226).getRGB(),
                new Color(226, 112, 162).getRGB(),
                new Color(229, 107, 157).getRGB(),
                new Color(234, 214, 226, 206).getRGB(),
                new Color(229, 107, 157, 232).getRGB(),
                new Color(250, 232, 242, 224).getRGB(),
                new Color(235, 184, 208, 168).getRGB(),
                new Color(238, 216, 228, 150).getRGB(),
                new Color(255, 246, 251, 238).getRGB(),
                new Color(255, 220, 236, 150).getRGB(),
                new Color(120, 72, 96, 84).getRGB());

        static final GuiPalette GRAY = new GuiPalette(
                new Color(16, 18, 22, 132).getRGB(),
                new Color(24, 27, 32, 232).getRGB(),
                new Color(24, 27, 32, 220).getRGB(),
                new Color(38, 42, 49, 228).getRGB(),
                new Color(58, 61, 68, 236).getRGB(),
                new Color(232, 234, 236).getRGB(),
                new Color(168, 171, 176).getRGB(),
                new Color(106, 110, 118).getRGB(),
                new Color(184, 192, 204).getRGB(),
                new Color(196, 78, 83).getRGB(),
                new Color(18, 21, 26, 168).getRGB(),
                new Color(24, 27, 32, 136).getRGB(),
                new Color(190, 196, 206, 62).getRGB(),
                new Color(54, 59, 68, 180).getRGB(),
                new Color(70, 76, 86, 214).getRGB(),
                new Color(204, 210, 220).getRGB(),
                new Color(184, 192, 204).getRGB(),
                new Color(72, 78, 88, 180).getRGB(),
                new Color(198, 206, 218, 224).getRGB(),
                new Color(48, 52, 60, 196).getRGB(),
                new Color(108, 116, 130, 152).getRGB(),
                new Color(76, 82, 92, 142).getRGB(),
                new Color(24, 28, 34, 238).getRGB(),
                new Color(0, 0, 0, 180).getRGB(),
                new Color(0, 0, 0, 205).getRGB());
    }

    /** 根据 HUD 模块设置获取当前 GUI 配色 */
    GuiPalette guiColors() {
        try {
            if (gq.vapulite.module.render.HUD.getTheme() == gq.vapulite.module.render.HUD.Theme.SAKURA) {
                return GuiPalette.SAKURA;
            }
            if (gq.vapulite.module.render.HUD.getTheme() == gq.vapulite.module.render.HUD.Theme.GRAY) {
                return GuiPalette.GRAY;
            }
            return gq.vapulite.module.render.HUD.isLightTheme() ? GuiPalette.LIGHT : GuiPalette.DARK;
        } catch (Exception e) {
            return GuiPalette.DARK;
        }
    }

    /** 获取阴影颜色（浅色主题用白色半透明阴影，暗色用黑色阴影） */
    int shadowColor(int alpha) {
        try {
            return gq.vapulite.module.render.HUD.isLightTheme() || gq.vapulite.module.render.HUD.isSakuraTheme()
                    ? withAlpha(0xFFFFFFFF, alpha)
                    : new Color(0, 0, 0, alpha).getRGB();
        } catch (Exception e) {
            return new Color(0, 0, 0, alpha).getRGB();
        }
    }

    /**
     * 绘制主题自适应毛玻璃效果。
     * <p>
     * 浅色主题使用简单的圆角边框矩形，暗色主题使用毛玻璃（Frosted Glass）效果。
     */
    void drawThemedGlass(float x, float y, float x2, float y2, float radius, float strength, int fill, int border) {
        themeRenderer.drawThemedGlass(x, y, x2, y2, radius, strength, fill, border);
    }

    void drawPanelGlass(float x, float y, float x2, float y2, float radius, float strength, int fill, int border) {
        themeRenderer.drawPanelGlass(x, y, x2, y2, radius, strength, fill, border);
    }

    // ==================== 布局常量 ====================
    static final float NAV_H = 28.0f;            // 导航栏高度
    static final float CARD_W = 194.0f;          // 模块卡片宽度
    static final float CARD_H = 50.0f;           // 模块卡片高度
    static final float GAP = 10.0f;              // 面板间距
    static final float SEARCH_H = 38.0f;         // 搜索栏高度
    static final float MODULE_PANEL_SHORTEN = 68.0f; // 左侧模块列表比主体面板短出的高度
    static final float PANEL_RADIUS = 8.0f;      // 面板圆角半径
    static final float CARD_RADIUS = 7.0f;       // 卡片圆角半径
    static final float DETAIL_MIN_W = 300.0f;    // 详情面板最小宽度
    static final float DETAIL_MAX_W = 360.0f;    // 详情面板最大宽度
    static final float SIDE_W = 170.0f;          // 侧边面板宽度
    static final float DETAIL_HEADER_H = 98.0f;  // 详情面板头部高度
    static final float VALUE_ROW_H = 30f;      // 普通值行高度
    static final float NUMBER_ROW_H = 30.0f;     // 数字滑块行高度
    static final float RANGE_ROW_H = 30.0f;      // 范围滑块行高度
    static final float MODE_ROW_H = 30.0f;       // 模式选择行高度
    static final float COLOR_ROW_H = 64.0f;      // 颜色选择行高度
    static final float SWITCH_W = 28.0f;         // 开关宽度
    static final float SWITCH_H = 14.0f;         // 开关高度
    static final float SWITCH_HIT_PAD = 5.0f;    // 开关点击区域的额外内边距
    static final float BOTTOM_BAR_H = 86.0f;     // 底部全局控制栏高度
    static final float CLOSE_END_PROGRESS = 0.22f; // 关闭动画结束阈值
    static final float CLOSING_TEXT_CUTOFF = 0.36f; // 关闭动画中文字消失阈值
    static final int FPS_GRAPH_SAMPLES = 44;     // FPS 波形图采样数

    // ==================== 全局状态 ====================
    static GuiTab currentTab = GuiTab.COMBAT;         // 当前导航标签页
    static Module selectedModule;                      // 当前选中的模块
    static final Map<String, Float> detailScrollByModule = new HashMap<>(); // 每个 module 记住各自的 scroll

    /**
     * 切换选中模块时保留/恢复 detail 面板滚动位置。
     * <p>
     * 每个模块独立记忆其在详情面板中的滚动位置，
     * 切换回来时自动恢复。
     */
    static void selectModule(Module m) {
        if (selectedModule != null) {
            detailScrollByModule.put(selectedModule.getName(), settingsScroll);
        }
        selectedModule = m;
        if (m != null) {
            for (Value v : m.getValues()) {
                v.animX = 0f;
            }
        }
        if (m != null && detailScrollByModule.containsKey(m.getName())) {
            settingsScroll = detailScrollByModule.get(m.getName());
            targetSettingsScroll = settingsScroll;
        } else {
            settingsScroll = 0;
            targetSettingsScroll = 0;
        }
    }

    // ==================== 交互状态 ====================
    Value draggingNumber;               // 当前拖拽的数值
    Numbers draggingColorRed;           // 当前拖拽的颜色-Red
    Numbers draggingColorGreen;         // 当前拖拽的颜色-Green
    Numbers draggingColorBlue;          // 当前拖拽的颜色-Blue
    Module bindingModule;               // 当前正在绑定的模块
    final Map<Module, Float> hoverProgress = new HashMap<Module, Float>();     // 模块悬停动画进度
    final Map<Module, Float> clickProgress = new HashMap<Module, Float>();     // 模块点击动画进度
    final Map<Module, Float> keyChipHoverProgress = new HashMap<Module, Float>(); // 侧栏按键按钮悬停动画进度
    final Map<Module, Float> keyChipClickProgress = new HashMap<Module, Float>(); // 侧栏按键按钮点击动画进度
    final Map<Module, Float> toggleProgress = new HashMap<Module, Float>();    // 模块开关动画进度
    final Map<Module, Float> selectProgress = new HashMap<Module, Float>();    // 模块选中动画进度
    final Map<GuiTab, Float> tabHoverProgress = new HashMap<GuiTab, Float>();  // 标签页悬停动画进度
    final Map<Value, Float> valueToggleProgress = new HashMap<Value, Float>(); // 设置值开关动画进度
    final Map<Value, Float> valueActiveProgress = new HashMap<Value, Float>(); // 设置值激活动画进度
    final Set<Module> favoriteModules = new HashSet<Module>();                 // 收藏的模块集合
    float draggingNumberX;              // 拖拽滑块起始 X
    float draggingNumberW;              // 拖拽滑块宽度
    boolean draggingNumberCustomRange;  // 是否在拖拽自定义范围滑块
    double draggingNumberMin;           // 自定义范围下限
    double draggingNumberMax;           // 自定义范围上限
    Numbers draggingNumberPair;         // 自定义范围滑块的配对值
    boolean draggingNumberLowerBound;   // 是否拖拽的是下界
    float draggingColorX;              // 颜色面板拖拽 X
    float draggingColorY;              // 颜色面板拖拽 Y
    float draggingColorW;              // 颜色面板拖拽宽度
    float draggingColorH;              // 颜色面板拖拽高度
    static float listScroll;           // 模块列表当前滚动偏移
    static float targetListScroll;     // 模块列表目标滚动偏移
    static float settingsScroll;       // 设置面板当前滚动偏移
    static float targetSettingsScroll; // 设置面板目标滚动偏移
    static String savedExpandedModeKeys = ""; // 游戏重启后恢复展开的 mode 下拉栏
    float scrollbarAlpha;             // 滚动条透明度
    boolean draggingScrollbar;        // 是否正在拖拽滚动条
    float scrollbarDragOffset;        // 滚动条拖拽偏移
    String draggingSidePanel;         // 当前正在拖拽的右侧面板子项
    float draggingSidePanelStartMouseX;
    float draggingSidePanelStartMouseY;
    float draggingSidePanelStartOffsetX;
    float draggingSidePanelStartOffsetY;
    boolean draggingModuleList;       // 是否正在拖拽模块列表面板
    float dragModuleListStartMouseX;
    float dragModuleListStartMouseY;
    float dragModuleListStartOffsetX;
    float dragModuleListStartOffsetY;
    boolean draggingDetail;           // 是否正在拖拽详情面板
    float dragDetailStartMouseX;
    float dragDetailStartMouseY;
    float dragDetailStartOffsetX;
    float dragDetailStartOffsetY;
    boolean draggingUserPanel;       // 是否正在拖拽用户面板
    float dragUserPanelStartMouseX;
    float dragUserPanelStartMouseY;
    float dragUserPanelStartOffsetX;
    float dragUserPanelStartOffsetY;
    float openProgress;              // GUI 打开动画进度 0→1
    float guiAlpha;                  // 全局 GUI 透明度
    float navIndicatorX;             // 导航栏指示器 X 坐标
    float contentFade;               // 内容淡入动画进度
    float searchFocusProgress;       // 搜索栏聚焦动画进度
    float navX;                      // 导航栏 X
    float navY;                      // 导航栏 Y
    float navW;                      // 导航栏宽度
    float contentX;                  // 内容区域 X
    float contentY;                  // 内容区域 Y
    float detailX;                   // 详情面板 X
    float detailY;                   // 详情面板 Y（独立于 contentY，支持单独拖动）
    float detailW;                   // 详情面板宽度
    float sideX;                     // 侧边面板 X
    float sideY;                     // 侧边面板 Y（独立于 contentY，支持单独拖动）
    float sideW;                     // 侧边面板宽度
    float windowW;                   // 窗口总宽度
    float panelH;                    // 面板高度
    float userPanelX;                // 用户面板 X（独立于模块列表）
    float userPanelY;                // 用户面板 Y
    int currentMouseX;               // 当前帧鼠标 X
    int currentMouseY;               // 当前帧鼠标 Y
    boolean sidePanelVisible;        // 侧边面板是否可见（屏幕宽度 >= 900px）
    static int detailTabIndex;       // 详情面板当前标签页索引
    String searchQuery = "";         // 搜索查询文本
    boolean searchFocused;           // 搜索栏是否聚焦
    long searchCursorTime;           // 搜索光标最后活跃时间
    String toastText;                // Toast 消息文本
    long toastStarted;               // Toast 开始时间
    boolean closing;                 // 是否正在关闭
    boolean savedOnClose;            // 关闭时是否已保存
    long lastPaletteClickMS;         // 颜色面板上次点击时间（用于双击检测）
    String lastPaletteClickKey;      // 颜色面板上次点击标识
    long lastFrameNanos;             // 上一帧时间（纳秒）
    long fpsSampleStarted;           // FPS 采样开始时间
    int fpsSampleFrames;             // FPS 采样帧数
    int liveFps;                     // 实时 FPS 值
    final float[] fpsGraphSamples = new float[FPS_GRAPH_SAMPLES]; // FPS 波形图采样缓冲区
    int fpsGraphCursor;              // FPS 波形图写入位置
    int fpsGraphSize;                // FPS 波形图当前有效采样数
    long fpsGraphLastSample;         // FPS 波形图上次采样时间
    float fpsGraphSmoothed;          // FPS 平滑值
    float frameScale = 1.0f;         // 帧缩放因子（用于帧率无关动画）
    final AnimUtil navBounce = new AnimUtil(280f);  // 导航栏选中指示器弹跳动画
    gq.vapulite.module.render.HUD.Theme lastTheme = gq.vapulite.module.render.HUD.Theme.DARK;
    float themeFadeProgress = 0.0f;
    final Map<gq.vapulite.module.render.HUD.Theme, Float> themeSwatchProgress = new HashMap<gq.vapulite.module.render.HUD.Theme, Float>();
    final Map<gq.vapulite.module.render.HUD.Theme, Float> themeSwatchHoverProgress = new HashMap<gq.vapulite.module.render.HUD.Theme, Float>();
    final Map<gq.vapulite.module.render.HUD.Theme, Float> designSwatchHoverProgress = new HashMap<gq.vapulite.module.render.HUD.Theme, Float>();
    float designThemeButtonHoverProgress = 0.0f;
    float designResetButtonHoverProgress = 0.0f;
    float designResetButtonPressProgress = 0.0f;
    final UiTheme uiTheme = UiTheme.current();                    // UI 主题
    final UiToggle reusableToggle = new UiToggle().setTheme(uiTheme); // 可复用的开关控件
    final UiTextField searchField = new UiTextField().setTheme(uiTheme).placeholder("Search modules...").maxLength(32); // 搜索文本框
    final ClickGuiThemeRenderer themeRenderer = new ClickGuiThemeRenderer(this);
    final ClickGuiNavigationRenderer navigationRenderer = new ClickGuiNavigationRenderer(this);
    final ClickGuiOverlayRenderer overlayRenderer = new ClickGuiOverlayRenderer(this);
    final ClickGuiSearchBar searchBar = new ClickGuiSearchBar(this);      // 搜索栏组件
    final ClickGuiModuleList moduleList = new ClickGuiModuleList(this);    // 模块列表组件
    final ClickGuiDetailPanel detailPanel = new ClickGuiDetailPanel(this); // 详情面板组件
    final ClickGuiSidePanel sidePanel = new ClickGuiSidePanel(this);      // 侧边面板组件
    final ClickGuiBottomBar bottomBar = new ClickGuiBottomBar(this);      // 底部栏组件

    /**
     * 初始化 GUI 界面。
     * <p>
     * 重置所有动画状态、滚动位置和交互状态到初始值。
     */
    @Override
    public void initGui() {
        super.initGui();
        ScaledResolution sr = new ScaledResolution(mc);
        updateLayout(sr);
        scrollbarAlpha = 0.0f;
        openProgress = 0.0f;
        contentFade = 0.0f;
        navIndicatorX = navX + 2.0f;
        bindingModule = null;
        searchFocused = false;
        searchQuery = "";
        searchFocusProgress = 0.0f;
        toastText = null;
        closing = false;
        savedOnClose = false;
        draggingScrollbar = false;
        draggingSidePanel = null;
        draggingNumber = null;
        draggingNumberCustomRange = false;
        draggingNumberPair = null;
        clearDraggingColor();
        lastPaletteClickMS = 0L;
        lastPaletteClickKey = null;
        lastFrameNanos = System.nanoTime();
        fpsSampleStarted = lastFrameNanos;
        fpsSampleFrames = 0;
        liveFps = 0;
        fpsGraphCursor = 0;
        fpsGraphSize = 0;
        fpsGraphLastSample = 0L;
        fpsGraphSmoothed = 0.0f;
        frameScale = 1.0f;
        lastTheme = gq.vapulite.module.render.HUD.getTheme();
        themeFadeProgress = 0.0f;
        themeRenderer.resetGlassAnimation();
    }

    /**
     * 每帧渲染 GUI。
     * <p>
     * 渲染顺序：背景 → 导航栏 → 模块列表 → 搜索栏 → 详情面板 → 侧边面板 → 底部栏 → 按键绑定覆盖层 → Toast。
     */
    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        currentMouseX = mouseX;
        currentMouseY = mouseY;
        updateFrameScale();
        navBounce.tick();
        ScaledResolution sr = new ScaledResolution(mc);
        updateLayout(sr);
        // 同步 UI 主题
        UiTheme currentTheme = UiTheme.current();
        searchField.setTheme(currentTheme);
        reusableToggle.setTheme(currentTheme);
        detailPanel.updateTheme(currentTheme);
        moduleList.updateTheme(currentTheme);
        ensureSelectedModule();
        // 打开/关闭动画
        openProgress = animate(openProgress, closing ? 0.0f : 1.0f, closing ? 0.20f : 0.16f);
        guiAlpha = openProgress * gq.vapulite.module.render.ClickGUI.clickGuiAlpha.getValue().floatValue();
        contentFade = animate(contentFade, closing ? 0.0f : 1.0f, closing ? 0.18f : 0.14f);
        if (closing && openProgress <= CLOSE_END_PROGRESS) {
            mc.displayGuiScreen(null);
            return;
        }
        updateThemeTransition();
        themeRenderer.updateGlassAnimation();
        drawBackdrop(sr);
        ShaderRenderer.invalidateFrostedGlass();
        // 更新交互状态
        if (!closing) {
            moduleList.updateScrollbarDrag(mouseY);
            sidePanel.updateDrag(mouseX, mouseY);
            updatePanelDrag(mouseX, mouseY);
            updateScroll(mouseX, mouseY);
        }
        // 颜色拖拽持续更新
        if (!closing && draggingColorRed != null && Mouse.isButtonDown(0)) {
            detailPanel.updateColorValue(mouseX, mouseY);
        } else if (!Mouse.isButtonDown(0)) {
            clearDraggingColor();
        }
        // 数字拖拽持续更新
        if (!closing && draggingNumber instanceof Numbers && Mouse.isButtonDown(0)) {
            detailPanel.updateNumberValue((Numbers) draggingNumber, mouseX, draggingNumberX, draggingNumberW);
        } else {
            draggingNumber = null;
            draggingNumberCustomRange = false;
            draggingNumberPair = null;
        }

        // 渲染各区域（introY 用于打开/关闭动画的位移效果）
        float introY = (1.0f - easeOut(openProgress)) * (closing ? 18.0f : -10.0f);
        drawNavigation(mouseX, mouseY, introY);
        moduleList.render(mouseX, mouseY, introY);
        searchBar.render(mouseX, mouseY, introY);
        detailPanel.render(mouseX, mouseY, introY);
        sidePanel.render(sr, mouseX, mouseY, introY);
        drawThemeFade(sr);
        super.drawScreen(mouseX, mouseY, partialTicks);
        drawKeybindOverlay(sr);
        drawToast(sr);
    }

    /**
     * 更新布局参数。
     * <p>
     * 根据屏幕分辨率和窗口位置配置计算各面板的坐标和尺寸。
     * 支持通过 ClickGUI 模块的 windowX/windowY 值自定义窗口位置。
     */
    void updateLayout(ScaledResolution sr) {
        ClickGuiLayout layout = ClickGuiLayout.calculate(sr);
        float modOX = ClickGUI.moduleOffsetX.getValue().floatValue();
        float modOY = ClickGUI.moduleOffsetY.getValue().floatValue();
        float detOX = ClickGUI.detailOffsetX.getValue().floatValue();
        float detOY = ClickGUI.detailOffsetY.getValue().floatValue();
        float userOX = ClickGUI.userPanelOffsetX.getValue().floatValue();
        float userOY = ClickGUI.userPanelOffsetY.getValue().floatValue();
        // 各面板有效坐标 = 布局计算值 + 面板独立偏移
        // 模块列表
        contentX = layout.contentX + modOX;
        contentY = layout.contentY + modOY;
        // 详情面板（独立拖动，导航栏不动）
        detailX = layout.detailX + detOX;
        detailY = layout.contentY + detOY;
        // 导航栏固定在布局位置，不随详情面板移动
        navX = layout.navX;
        navY = layout.navY;
        // 用户面板（独立于模块列表，默认位于模块列表左上方）
        userPanelX = layout.contentX + userOX;
        userPanelY = layout.navY + userOY;
        // 侧面板：子面板各自已有独立偏移
        sideX = layout.sideX;
        sideY = layout.contentY;
        navW = layout.navW;
        detailW = layout.detailW;
        sideW = layout.sideW;
        windowW = layout.windowW;
        panelH = layout.panelH;
        sidePanelVisible = layout.sidePanelVisible;
    }

    /**
     * 绘制全屏半透明背景。
     * <p>
     * 包含三层：实色底色 + 顶部到中间渐变 + 中间到底部渐变（底部加深）。
     */
    void drawBackdrop(ScaledResolution sr) {
        themeRenderer.drawBackdrop(sr);
    }

    void updateThemeTransition() {
        gq.vapulite.module.render.HUD.Theme current = gq.vapulite.module.render.HUD.getTheme();
        if (current != lastTheme) {
            lastTheme = current;
            themeFadeProgress = 1.0f;
        }
        themeFadeProgress = animate(themeFadeProgress, 0.0f, 0.13f);
    }

    void drawThemeFade(ScaledResolution sr) {
        themeRenderer.drawThemeFade(sr);
    }

    /**
     * 绘制顶部用户区和分类导航共用的连体背景。
     */
    void drawTopBarBackground(float introY) {
        navigationRenderer.drawTopBarBackground(introY);
    }

    /**
     * 绘制顶部导航栏。
     * <p>
     * 导航栏包含等宽的标签页按钮，激活标签页有高亮指示器和背景。
     * 悬停时显示半透明高亮效果。
     */
    void drawNavigation(int mouseX, int mouseY, float introY) {
        navigationRenderer.drawNavigation(mouseX, mouseY, introY);
    }

    /**
     * 获取模块分类的简称标记。
     * @return C=Combat, M=Movement, V=Visual(render), P=Player, W=World, U=Config, O=Other
     */
    String getCategoryMark(Module module) {
        if (module.getCategory() == ModuleType.Combat) {
            return "C";
        }
        if (module.getCategory() == ModuleType.Movement) {
            return "M";
        }
        if (module.getCategory() == ModuleType.Render) {
            return "V";
        }
        if (module.getCategory() == ModuleType.Player) {
            return "P";
        }
        if (module.getCategory() == ModuleType.World) {
            return "W";
        }
        if (module.getCategory() == ModuleType.Config) {
            return "U";
        }
        return "O";
    }

    /**
     * 获取模块分类的主题色。
     */
    int getCategoryAccent(Module module) {
        if (module.getCategory() == ModuleType.Combat) return 0xFF8B7CFF;
        if (module.getCategory() == ModuleType.Movement) return 0xFF70C1DC;
        if (module.getCategory() == ModuleType.Render) return 0xFFFF8DA8;
        if (module.getCategory() == ModuleType.Player) return 0xFF6FD39A;
        if (module.getCategory() == ModuleType.World) return 0xFFFFC76D;
        if (module.getCategory() == ModuleType.Config) return 0xFFB7A4FF;
        return 0xFFD4DAE3;
    }

    /** @return 当前玩家的 Ping 值显示文本 */
    String getPingText() {
        try {
            if (mc.thePlayer != null && mc.getNetHandler() != null && mc.getNetHandler().getPlayerInfo(mc.thePlayer.getUniqueID()) != null) {
                return mc.getNetHandler().getPlayerInfo(mc.thePlayer.getUniqueID()).getResponseTime() + " ms";
            }
        } catch (Throwable ignored) {
        }
        return "-- ms";
    }

    /** @return 实时 FPS 文本（优先使用平滑值） */
    String getLiveFpsText() {
        if (fpsGraphSmoothed > 0.0f) {
            return String.valueOf(Math.max(1, Math.round(fpsGraphSmoothed)));
        }
        return liveFps <= 0 ? "--" : String.valueOf(liveFps);
    }

    /** @return FPS 波形图有效采样数 */
    int getFpsGraphSize() {
        return fpsGraphSize;
    }

    /**
     * 获取 FPS 波形图中指定索引的采样值。
     * <p>
     * 环形缓冲区索引计算：从最旧的数据开始读取。
     */
    float getFpsGraphSample(int index) {
        if (fpsGraphSize <= 0) {
            return fpsGraphSmoothed > 0.0f ? fpsGraphSmoothed : liveFps;
        }
        int clamped = Math.max(0, Math.min(fpsGraphSize - 1, index));
        int start = fpsGraphSize == fpsGraphSamples.length ? fpsGraphCursor : 0;
        return fpsGraphSamples[(start + clamped) % fpsGraphSamples.length];
    }

    /** @return 已启用模块数量 */
    int getEnabledModules() {
        int enabled = 0;
        for (Module module : ModuleManager.getModules()) {
            if (module.getState()) {
                enabled++;
            }
        }
        return enabled;
    }

    // ==================== 布局计算辅助方法 ====================

    /** @return 设置值区域的 X 坐标 */
    float getDetailValuesX() {
        return detailX + 20.0f;
    }

    /** @return 设置值区域的 Y 坐标 */
    float getDetailValuesY(float panelY) {
        return panelY + DETAIL_HEADER_H +2f;
    }

    /** @return 设置值区域的宽度 */
    float getDetailValuesWidth() {
        return detailW - 40.0f;
    }

    /** @return 设置值区域的高度 */
    float getDetailValuesHeight() {
        return panelH - DETAIL_HEADER_H - 18.0f;
    }

    /** @return 模块设置项的总内容高度（用于计算滚动范围） */
    float getSettingsContentHeight(Module module) {
        if (module == null || module.getValues().isEmpty()) {
            return 0.0f;
        }
        float height = 4.0f;
        for (int i = 0; i < module.getValues().size(); i++) {
            if (isDetailValueVisible(module, i)) {
                height += getValueHeight(module, i);
            }
        }
        return height;
    }

    /** 格式化设置值的显示文本 */
    String getValueText(Value value) {
        if (value instanceof Option) {
            return Boolean.TRUE.equals(value.getValue()) ? "On" : "Off";
        }
        if (value instanceof Numbers) {
            return formatNumber(((Number) value.getValue()).doubleValue());
        }
        if (value instanceof Mode) {
            return formatModeLabel(((Mode) value).getModeAsString());
        }
        return String.valueOf(value.getValue());
    }

    /**
     * 绘制开关控件。
     * <p>
     * 使用可复用的 UiToggle 实例，支持平滑动画过渡。
     *
     * @param owner   动画绑定对象（Module 或 Value），用于独立追踪动画进度
     */
    void drawSwitch(float x, float y, boolean enabled, float alpha, Object owner) {
        float progress;
        if (owner instanceof Module) {
            progress = animateMap(toggleProgress, (Module) owner, enabled ? 1.0f : 0.0f, 0.12f);
        } else if (owner instanceof Value) {
            progress = animateValueMap(valueToggleProgress, (Value) owner, enabled ? 1.0f : 0.0f, 0.12f);
        } else {
            progress = enabled ? 1.0f : 0.0f;
        }
        reusableToggle.setBounds(x, y, SWITCH_W, SWITCH_H)
                .enabled(enabled)
                .progress(easeSmooth(progress))
                .setAlpha(alpha * guiAlpha)
                .render(0, 0, 0.0f);
    }

    /**
     * 更新鼠标滚轮滚动。
     * <p>
     * 优先传递给详情面板（设置区域），如果不在详情区域内则传递给模块列表。
     */
    /**
     * 面板拖拽启动。
     * <p>
     * 检测鼠标是否在某个面板的拖拽句柄上，如果是则开始拖拽该面板。
     * 句柄区域：模块列表顶部 24px、详情面板导航栏、侧面板顶部 24px。
     */
    private void startPanelDrag(int mouseX, int mouseY) {
        float modOX = ClickGUI.moduleOffsetX.getValue().floatValue();
        float modOY = ClickGUI.moduleOffsetY.getValue().floatValue();
        float detOX = ClickGUI.detailOffsetX.getValue().floatValue();
        float detOY = ClickGUI.detailOffsetY.getValue().floatValue();
        // 模块列表拖拽句柄：顶部 24px（contentX/Y 已包含偏移）
        if (isHovered(contentX, contentY, contentX + CARD_W, contentY + 24.0f, mouseX, mouseY)) {
            draggingModuleList = true;
            dragModuleListStartMouseX = mouseX;
            dragModuleListStartMouseY = mouseY;
            dragModuleListStartOffsetX = modOX;
            dragModuleListStartOffsetY = modOY;
        }
        // 详情面板拖拽句柄：面板顶部 36px 区域
        if (isHovered(detailX, detailY, detailX + detailW, detailY + 36.0f, mouseX, mouseY)) {
            draggingDetail = true;
            dragDetailStartMouseX = mouseX;
            dragDetailStartMouseY = mouseY;
            dragDetailStartOffsetX = detOX;
            dragDetailStartOffsetY = detOY;
        }
        // 用户面板拖拽句柄：整个面板（CARD_W x NAV_H）
        if (isHovered(userPanelX, userPanelY, userPanelX + CARD_W, userPanelY + NAV_H, mouseX, mouseY)) {
            draggingUserPanel = true;
            dragUserPanelStartMouseX = mouseX;
            dragUserPanelStartMouseY = mouseY;
            dragUserPanelStartOffsetX = ClickGUI.userPanelOffsetX.getValue().floatValue();
            dragUserPanelStartOffsetY = ClickGUI.userPanelOffsetY.getValue().floatValue();
        }
    }

    /**
     * 更新面板拖拽位置。
     * <p>
     * 每帧调用，将鼠标位移转换为面板偏移并写入 ClickGUI 值。
     */
    private void updatePanelDrag(int mouseX, int mouseY) {
        if (!Mouse.isButtonDown(0)) {
            draggingModuleList = false;
            draggingDetail = false;
            draggingUserPanel = false;
            return;
        }
        float min = -600f, max = 600f;
        if (draggingModuleList) {
            float nx = clamp(dragModuleListStartOffsetX + mouseX - dragModuleListStartMouseX, min, max);
            float ny = clamp(dragModuleListStartOffsetY + mouseY - dragModuleListStartMouseY, min, max);
            ClickGUI.moduleOffsetX.setValue((double) nx);
            ClickGUI.moduleOffsetY.setValue((double) ny);
        }
        if (draggingDetail) {
            float nx = clamp(dragDetailStartOffsetX + mouseX - dragDetailStartMouseX, min, max);
            float ny = clamp(dragDetailStartOffsetY + mouseY - dragDetailStartMouseY, min, max);
            ClickGUI.detailOffsetX.setValue((double) nx);
            ClickGUI.detailOffsetY.setValue((double) ny);
        }
        if (draggingUserPanel) {
            float nx = clamp(dragUserPanelStartOffsetX + mouseX - dragUserPanelStartMouseX, min, max);
            float ny = clamp(dragUserPanelStartOffsetY + mouseY - dragUserPanelStartMouseY, min, max);
            ClickGUI.userPanelOffsetX.setValue((double) nx);
            ClickGUI.userPanelOffsetY.setValue((double) ny);
        }
    }

    void updateScroll(int mouseX, int mouseY) {
        int wheel = Mouse.getDWheel();
        if (draggingScrollbar || draggingSidePanel != null || wheel == 0) {
            return;
        }
        if (detailPanel.updateScroll(mouseX, mouseY, wheel)) {
            return;
        }
        moduleList.updateScroll(mouseX, mouseY, wheel);
    }

    /**
     * 处理鼠标点击事件。
     * <p>
     * 事件分发优先级：搜索栏 > 导航栏 > 滚动条 > 详情面板 > 侧边面板 > 模块列表。
     */
    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        if (bindingModule != null) {
            return;
        }
        ScaledResolution sr = new ScaledResolution(mc);
        // 面板拖拽检测（在正常点击之前，不影响纯点击行为）
        if (!closing && mouseButton == 0) {
            startPanelDrag(mouseX, mouseY);
        }
        if (closing || searchBar.mouseClicked(mouseX, mouseY, mouseButton) || handleNavClick(mouseX, mouseY)
                || moduleList.handleScrollbarClick(mouseX, mouseY, mouseButton) || detailPanel.mouseClicked(mouseX, mouseY, mouseButton)
                || sidePanel.mouseClicked(mouseX, mouseY, mouseButton)) {
            return;
        }
        if (moduleList.mouseClicked(mouseX, mouseY, mouseButton)) {
            return;
        }
        searchFocused = false;
        super.mouseClicked(mouseX, mouseY, mouseButton);
    }

    /**
     * 处理导航栏点击。
     * <p>
     * 点击标签页时切换当前标签、清空搜索、重置滚动。
     */
    boolean handleNavClick(int mouseX, int mouseY) {
        if (!isHovered(navX, navY, navX + navW, navY + NAV_H, mouseX, mouseY)) {
            return false;
        }
        float tabW = navW / GuiTab.values().length;
        int index = (int) ((mouseX - navX) / tabW);
        if (index < 0 || index >= GuiTab.values().length) {
            return true;
        }
        GuiTab tab = GuiTab.values()[index];
        if (tab != currentTab) {
            currentTab = tab;
            navBounce.trigger(tab.ordinal());
        }
        selectModule(null);
        searchFocused = false;
        setSearchQuery("");
        contentFade = 0.0f;
        targetListScroll = 0.0f;
        listScroll = 0.0f;
        return true;
    }

    /** 鼠标释放时清除所有拖拽状态 */
    @Override
    protected void mouseReleased(int mouseX, int mouseY, int state) {
        draggingNumber = null;
        draggingNumberCustomRange = false;
        draggingNumberPair = null;
        clearDraggingColor();
        draggingScrollbar = false;
        draggingSidePanel = null;
        draggingModuleList = false;
        draggingDetail = false;
        draggingUserPanel = false;
        super.mouseReleased(mouseX, mouseY, state);
    }

    void resetUiLayout() {
        ClickGUI.windowX.setValue(-1.0);
        ClickGUI.windowY.setValue(-1.0);
        ClickGUI.sideStatsOffsetX.setValue(0.0);
        ClickGUI.sideStatsOffsetY.setValue(0.0);
        ClickGUI.sideSummaryOffsetX.setValue(0.0);
        ClickGUI.sideSummaryOffsetY.setValue(0.0);
        ClickGUI.sideDesignOffsetX.setValue(0.0);
        ClickGUI.sideDesignOffsetY.setValue(0.0);
        ClickGUI.moduleOffsetX.setValue(0.0);
        ClickGUI.moduleOffsetY.setValue(0.0);
        ClickGUI.detailOffsetX.setValue(0.0);
        ClickGUI.detailOffsetY.setValue(0.0);
        ClickGUI.userPanelOffsetX.setValue(0.0);
        ClickGUI.userPanelOffsetY.setValue(0.0);
        draggingSidePanel = null;
        designResetButtonPressProgress = 1.0f;
        addToast("UI layout reset");
    }

    /**
     * 获取当前可见的模块列表。
     * <p>
     * 如果有搜索查询，按关键词过滤；否则按当前标签页过滤。
     * 结果按名称字母顺序排序。
     */
    List<Module> getVisibleModules() {
        ArrayList<Module> modules = new ArrayList<Module>();
        String query = searchQuery == null ? "" : searchQuery.trim().toLowerCase(Locale.ROOT);
        for (Module module : ModuleManager.getModules()) {
            if (query.length() > 0 ? matchesSearch(module, query) : currentTab.contains(module.getCategory())) {
                modules.add(module);
            }
        }
        Collections.sort(modules, new Comparator<Module>() {
            @Override
            public int compare(Module first, Module second) {
                return first.getName().compareToIgnoreCase(second.getName());
            }
        });
        return modules;
    }

    /** 检查模块是否匹配搜索查询（名称、描述或类型名） */
    boolean matchesSearch(Module module, String query) {
        if (module.getName().toLowerCase(Locale.ROOT).contains(query)) {
            return true;
        }
        if (module.Descript != null && module.Descript.toLowerCase(Locale.ROOT).contains(query)) {
            return true;
        }
        return module.getCategory() != null && module.getCategory().name().toLowerCase(Locale.ROOT).contains(query);
    }

    /** @return 模块卡片高度（固定） */
    float getCardHeight(Module module) {
        return CARD_H;
    }

    /** @return 所有可见模块的总内容高度 */
    float getContentHeight() {
        List<Module> modules = getVisibleModules();
        if (modules.isEmpty()) {
            return 0.0f;
        }
        return modules.size() * (CARD_H + 6.0f) - 6.0f;
    }

    /** @return 列表可视区域高度 */
    float getListHeight() {
        return Math.max(120.0f, getModulePanelHeight() - SEARCH_H - 52.0f);
    }

    /** @return 左侧模块列表面板高度，与详情面板底部对齐 */
    float getModulePanelHeight() {
        return panelH;
    }

    /** @return 模块列表起始 Y 坐标 */
    float getModuleListY() {
        return contentY + SEARCH_H + 22.0f;
    }

    /** @return 单个设置值行的默认高度 */
    float getValueHeight(Value value) {
        if (value instanceof Numbers) {
            return NUMBER_ROW_H;
        }
        if (value instanceof Mode) {
            return MODE_ROW_H;
        }
        return VALUE_ROW_H;
    }

    /**
     * 获取指定索引的设置值行高度。
     * <p>
     * 考虑值可见性、颜色组、范围滑块的合并行高度。
     */
    float getValueHeight(Module module, int index) {
        if (module != null && index >= 0 && index < module.getValues().size()
                && (!module.getValues().get(index).isVisible()
                || isHiddenPaletteValue(module, module.getValues().get(index)))) {
            return 0.0f;
        }
        if (isRangeStart(module, index)) {
            return RANGE_ROW_H;
        }
        if (isRangeContinuation(module, index)) {
            return 0.0f;
        }
        if (isColorStart(module, index)) {
            return COLOR_ROW_H;
        }
        if (isColorContinuation(module, index)) {
            return 0.0f;
        }
        return getValueHeight(module.getValues().get(index));
    }

    /**
     * 检查从指定索引开始是否为一个颜色组（连续三个 Numbers: Red, Green, Blue）。
     */
    boolean isColorStart(Module module, int index) {
        if (module == null || index < 0 || index + 2 >= module.getValues().size()) {
            return false;
        }
        List<Value> values = module.getValues();
        return isNumberNamed(values.get(index), "red")
                && isNumberNamed(values.get(index + 1), "green")
                && isNumberNamed(values.get(index + 2), "blue");
    }

    /** 检查是否为颜色组的延续行（Green/Blue 部分），应合并到颜色行中 */
    boolean isColorContinuation(Module module, int index) {
        return isColorStart(module, index - 1) || isColorStart(module, index - 2);
    }

    /**
     * 检查从指定索引开始是否为一个范围滑块组（两个 Numbers: min 和 max 共享相同基本名称）。
     */
    boolean isRangeStart(Module module, int index) {
        if (module == null || index < 0 || index + 1 >= module.getValues().size()) {
            return false;
        }
        Value first = module.getValues().get(index);
        Value second = module.getValues().get(index + 1);
        if (!(first instanceof Numbers) || !(second instanceof Numbers)) {
            return false;
        }
        String firstBase = rangeBase(first, "min");
        String secondBase = rangeBase(second, "max");
        return firstBase.length() > 0 && firstBase.equals(secondBase);
    }

    /** 检查是否为范围滑块的延续行 */
    boolean isRangeContinuation(Module module, int index) {
        return isRangeStart(module, index - 1);
    }

    /** 获取范围设置项的基础显示名称 */
    String getRangeDisplayName(Value value) {
        String base = rangeDisplayBase(value, "min");
        return base.length() == 0 ? getDisplayName(value) : base;
    }

    /**
     * 检查某个值是否为隐藏的调色板值。
     * <p>
     * 例如 ESP 模块的 "rainbow" 和 "paletteRainbow" 选项不显示为独立行。
     */
    boolean isHiddenPaletteValue(Module module, Value value) {
        if (module == null || value == null || !(value instanceof Option)) {
            return false;
        }
        String moduleName = module.getName() == null ? "" : module.getName().replace(" ", "").toLowerCase(Locale.ROOT);
        String valueName = normalizeValueName(value);
        if (moduleName.equals("clickgui") && valueName.equals("glass")) {
            return true;
        }
        return moduleName.equals("esp") && (valueName.equals("rainbow") || valueName.equals("paletterainbow"));
    }

    /** @return 当前标签页中可见的设置值数量 */
    int getVisibleValueCount(Module module) {
        if (module == null || module.getValues().isEmpty()) {
            return 0;
        }
        int count = 0;
        for (int i = 0; i < module.getValues().size(); i++) {
            if (isDetailValueVisible(module, i)) {
                count++;
            }
        }
        return count;
    }

    /** 检查指定索引的设置值是否在当前标签页中可见 */
    boolean isDetailValueVisible(Module module, int index) {
        if (module == null || index < 0 || index >= module.getValues().size()) {
            return false;
        }
        Value value = module.getValues().get(index);
        if (!value.isVisible() || isHiddenPaletteValue(module, value) || isColorContinuation(module, index)
                || isRangeContinuation(module, index)) {
            return false;
        }
        return getDetailValueTab(module, index) == detailTabIndex;
    }

    /**
     * 根据设置值的名称关键词自动归类到详情标签页。
     * <p>
     * 归类规则：
     * <ul>
     *   <li>0 = General（默认）</li>
     *   <li>1 = Targets（目标选择相关）</li>
     *   <li>2 = Extra（武器/按键条件相关）</li>
     *   <li>3 = Rotation（旋转/瞄准相关）</li>
     *   <li>4 = Visuals（渲染/颜色相关）</li>
     * </ul>
     */
    int getDetailValueTab(Module module, int index) {
        if (module == null || index < 0 || index >= module.getValues().size()) {
            return 0;
        }
        if (isColorStart(module, index)) {
            return 4;
        }

        Value value = module.getValues().get(index);
        String raw = normalizeValueText(value);
        String name = normalizeValueName(value);

        // 旋转/瞄准
        if (containsAny(raw, "yaw", "pitch", "rotate", "rotation", "aim", "aimpoint",
                "prediction", "freezone", "reaction", "lock", "randomize")) {
            return 3;
        }
        // 目标
        if (containsAny(raw, "player", "mob", "animal", "invisible", "target", "priority",
                "throughwall", "wallcheck", "range", "reach", "fov", "hurt", "hitbox", "expand")) {
            return 1;
        }
        // 视觉
        if (containsAny(raw, "render", "visual", "shader", "trail", "color", "alpha", "radius",
                "height", "line", "pulse", "background", "watermark", "arraylist", "notification",
                "potion", "inventory", "scale", "xposition", "yposition", "xoffset", "yoffset",
                "bottom", "red", "green", "blue")) {
            return 4;
        }
        if (name.equals("x") || name.equals("y")) {
            return 4;
        }
        // 额外条件
        if (containsAny(raw, "weapon", "sword", "mouse", "moving", "sprint", "rightclick",
                "auto", "swap", "restore", "block", "sneak", "ground", "scope", "release",
                "break", "require", "only", "hold", "key")) {
            return 2;
        }
        return 0;
    }

    /** 检查文本是否包含任意一个关键词 */
    private boolean containsAny(String text, String... needles) {
        for (String needle : needles) {
            if (text.contains(needle)) {
                return true;
            }
        }
        return false;
    }

    /** 标准化值文本（去除空格、下划线、横线，转小写）用于分类匹配 */
    private String normalizeValueText(Value value) {
        String display = value == null || value.getDisplayName() == null ? "" : value.getDisplayName();
        String name = value == null || value.getName() == null ? "" : value.getName();
        return (display + " " + name).replace(" ", "").replace("_", "").replace("-", "").toLowerCase(Locale.ROOT);
    }

    /** 获取设置值的显示名称 */
    String getDisplayName(Value value) {
        String display = value == null ? "" : value.getDisplayName();
        if (display == null || display.trim().length() == 0) {
            display = value == null ? "" : value.getName();
        }
        return display == null ? "" : display;
    }

    /**
     * 格式化 Mode 的原始字符串为可读标签。
     * <p>
     * 例如 "FAST_PLACE" → "Fast Place"。
     */
    String formatModeLabel(String raw) {
        if (raw == null || raw.length() == 0) {
            return "";
        }
        String text = raw.replace('_', ' ').replace('-', ' ').toLowerCase(Locale.ROOT);
        StringBuilder builder = new StringBuilder(text.length());
        boolean upper = true;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (Character.isWhitespace(c)) {
                builder.append(' ');
                upper = true;
            } else {
                builder.append(upper ? Character.toUpperCase(c) : c);
                upper = false;
            }
        }
        return builder.toString();
    }

    /** 提取范围滑块的基本名称（去除 min/max 前缀） */
    private String rangeBase(Value value, String prefix) {
        return rangeDisplayBase(value, prefix)
                .replace(" ", "")
                .replace("_", "")
                .replace("-", "")
                .toLowerCase(Locale.ROOT);
    }

    private String rangeDisplayBase(Value value, String prefix) {
        String raw = getDisplayName(value).trim();
        String lower = raw.toLowerCase(Locale.ROOT);
        if (lower.startsWith(prefix + " ")) {
            return raw.substring(prefix.length()).trim();
        }
        if (lower.startsWith(prefix) && raw.length() > prefix.length()) {
            return raw.substring(prefix.length()).trim();
        }
        return "";
    }

    /** 检查值是否为指定名称的 Numbers 类型 */
    private boolean isNumberNamed(Value value, String name) {
        return value instanceof Numbers && normalizeValueName(value).equals(name);
    }

    /** 标准化值名称 */
    String normalizeValueName(Value value) {
        String raw = value == null ? "" : value.getName();
        if (raw == null || raw.length() == 0) {
            raw = value == null ? "" : value.getDisplayName();
        }
        return raw == null ? "" : raw.replace(" ", "").replace("_", "").replace("-", "").toLowerCase(Locale.ROOT);
    }

    /** 开始拖拽颜色面板 */
    void beginDraggingColor(Numbers red, Numbers green, Numbers blue, float x, float y, float w, float h) {
        draggingColorRed = red;
        draggingColorGreen = green;
        draggingColorBlue = blue;
        draggingColorX = x;
        draggingColorY = y;
        draggingColorW = w;
        draggingColorH = h;
        draggingNumber = null;
        draggingNumberCustomRange = false;
        draggingNumberPair = null;
    }

    /** 清除颜色拖拽状态 */
    void clearDraggingColor() {
        draggingColorRed = null;
        draggingColorGreen = null;
        draggingColorBlue = null;
    }

    /** 确保始终有一个模块被选中 */
    void ensureSelectedModule() {
        List<Module> modules = getVisibleModules();
        if (selectedModule != null && modules.contains(selectedModule)) {
            return;
        }
        selectModule(modules.isEmpty() ? null : modules.get(0));
    }

    // ==================== 布局位置辅助方法 ====================

    float getSliderBarX(float x, float width) {
        float labelW = getDetailLabelWidth(width);
        return x + labelW;
    }

    float getSliderBarWidth(float width) {
        float labelW = getDetailLabelWidth(width);
        return Math.max(54.0f, width - labelW - 58.0f - 14.0f);
    }

    float getDetailLabelWidth(float width) {
        return Math.min(142.0f, Math.max(92.0f, width * 0.38f));
    }

    float getDetailValuePillWidth() {
        return 48.0f;
    }

    float getModuleSwitchX(float cardX) {
        return cardX + CARD_W - SWITCH_W - 54.0f;
    }

    float getModuleSwitchY(float cardY) {
        return cardY + 17.0f;
    }

    float getDetailSwitchX() {
        return detailX + detailW - SWITCH_W - 28.0f;
    }

    float getDetailSwitchY(float panelY) {
        return panelY + 22.0f;
    }

    float getOptionSwitchX(float rowX, float rowW) {
        return rowX + rowW - SWITCH_W - 2.0f;
    }

    float getOptionSwitchY(float rowY) {
        return rowY + 7.0f;
    }

    /** 检查鼠标是否点击了开关（包含额外的点击内边距） */
    boolean isSwitchHit(float switchX, float switchY, int mouseX, int mouseY) {
        return isHovered(switchX - SWITCH_HIT_PAD, switchY - SWITCH_HIT_PAD,
                switchX + SWITCH_W + SWITCH_HIT_PAD, switchY + SWITCH_H + SWITCH_HIT_PAD, mouseX, mouseY);
    }

    /** 获取模块描述文本，无描述时返回默认文本 */
    String getDescription(Module module) {
        if (module.Descript == null || module.Descript.trim().length() == 0) {
            return "Configure this module.";
        }
        return module.Descript;
    }

    /** 获取模块绑定按键的显示名称 */
    String getKeyName(Module module) {
        if (module.getKey() == Keyboard.KEY_NONE) {
            return "NONE";
        }
        String keyName = Keyboard.getKeyName(module.getKey());
        return keyName == null ? "NONE" : keyName;
    }

    /** 格式化数字（整数不带小数，浮点数保留两位） */
    String formatNumber(double value) {
        if (Math.abs(value - Math.round(value)) < 0.0001D) {
            return String.valueOf((long) Math.round(value));
        }
        return String.format(Locale.ROOT, "%.2f", value);
    }

    /**
     * 截断文本使其适应指定宽度。
     * @return 如果文本过长则添加 "..." 后缀
     */
    String trim(String text, CFontRenderer font, float maxWidth) {
        if (text == null) {
            return "";
        }
        if (font.getStringWidth(text) <= maxWidth) {
            return text;
        }
        String result = text;
        while (result.length() > 1 && font.getStringWidth(result + "...") > maxWidth) {
            result = result.substring(0, result.length() - 1);
        }
        return result + "...";
    }

    // ==================== 基础绘制方法 ====================

    /** 绘制普通文字（检查透明度阈值和关闭动画状态） */
    void drawFont(String text, float x, float y, int color) {
        if (shouldDrawText(color)) {
            FontLoaders.F14.drawString(text, x, y, color);
        }
    }

    void drawCenteredFont(String text, float x, float y, int color) {
        if (shouldDrawText(color)) {
            FontLoaders.F14.drawCenteredString(text, x, y, color);
        }
    }

    /** 检查是否应该绘制文字（透明度 >= 18 且未超过关闭动画文字截断点） */
    boolean shouldDrawText(int color) {
        if (getAlpha(color) < 18) {
            return false;
        }
        return !closing || openProgress > CLOSING_TEXT_CUTOFF;
    }

    /** 在矩形区域内居中绘制文字 */
    void drawCenteredText(String text, float x, float y, float x2, float y2, int color) {
        float textX = x + (x2 - x - FontLoaders.F14.getStringWidth(text)) / 2.0f;
        float textY = y + (y2 - y - FontLoaders.F14.getStringHeight(text)) / 2.0f + 0.5f;
        drawFont(text, textX, textY, color);
    }

    /** 在指定中心点绘制图标（考虑视觉偏移） */
    void drawCenteredIcon(String icon, CFontRenderer font, float centerX, float centerY, int color) {
        if (shouldDrawText(color)) {
            font.drawString(icon, centerX - font.getStringWidth(icon) / 2.0f + ClickGuiIcons.visualOffsetX(icon),
                    centerY - font.getHeight() / 2.0f + 2.0f + ClickGuiIcons.visualOffsetY(icon), color);
        }
    }

    // ==================== 动画辅助方法 ====================

    /**
     * 平滑动画插值（Map 版本，绑定 Module）。
     * <p>
     * 使用帧缩放因子确保动画在不同帧率下保持一致速度。
     */
    float animateMap(Map<Module, Float> map, Module module, float target, float speed) {
        Float current = map.get(module);
        float value = current == null ? target : current.floatValue();
        value = animate(value, target, speed);
        if (Math.abs(value - target) < 0.003f) {
            value = target;
        }
        map.put(module, value);
        return value;
    }

    /** 平滑动画插值（Map 版本，绑定 Value） */
    float animateValueMap(Map<Value, Float> map, Value valueKey, float target, float speed) {
        Float current = map.get(valueKey);
        float value = current == null ? target : current.floatValue();
        value = animate(value, target, speed);
        if (Math.abs(value - target) < 0.003f) {
            value = target;
        }
        map.put(valueKey, value);
        return value;
    }

    /** 平滑动画插值（Map 版本，绑定 GuiTab） */
    float animateTabMap(GuiTab tab, float target, float speed) {
        Float current = tabHoverProgress.get(tab);
        float value = current == null ? target : current.floatValue();
        value = animate(value, target, speed);
        if (Math.abs(value - target) < 0.003f) {
            value = target;
        }
        tabHoverProgress.put(tab, value);
        return value;
    }

    /**
     * 核心平滑动画插值函数。
     * <p>
     * 使用帧缩放因子（frameScale）进行帧率补偿，确保动画在不同帧率下保持一致。
     *
     * @param current 当前值
     * @param target  目标值
     * @param speed   插值速度（0.01~1.0）
     * @return 插值后的新值
     */
    float animate(float current, float target, float speed) {
        float adjustedSpeed = 1.0f - (float) Math.pow(1.0f - clamp(speed, 0.01f, 1.0f), frameScale);
        float value = current + (target - current) * adjustedSpeed;
        if (Math.abs(value - target) < 0.00045f) {
            return target;
        }
        return value;
    }

    /** Ease-out 缓出函数（四次方） */
    float easeOut(float value) {
        value = clamp(value, 0.0f, 1.0f);
        return 1.0f - (float) Math.pow(1.0f - value, 4.0D);
    }

    /** Ease-smooth 平滑缓动函数（Hermite 插值） */
    float easeSmooth(float value) {
        value = clamp(value, 0.0f, 1.0f);
        return value * value * (3.0f - 2.0f * value);
    }

    // ==================== 颜色工具方法 ====================

    /** 在两个颜色之间线性插值 */
    int blendColor(int from, int to, float progress) {
        progress = clamp(progress, 0.0f, 1.0f);
        int a = (int) (getAlpha(from) + (getAlpha(to) - getAlpha(from)) * progress);
        int r = (int) (getRed(from) + (getRed(to) - getRed(from)) * progress);
        int g = (int) (getGreen(from) + (getGreen(to) - getGreen(from)) * progress);
        int b = (int) (getBlue(from) + (getBlue(to) - getBlue(from)) * progress);
        return ((a & 255) << 24) | ((r & 255) << 16) | ((g & 255) << 8) | (b & 255);
    }

    /** 替换颜色的 Alpha 分量 */
    int withAlpha(int color, float alpha) {
        int a = (int) clamp(alpha, 0.0f, 255.0f);
        return (color & 0x00FFFFFF) | (a << 24);
    }

    /** 绘制圆角矩形（透明度为 0 时跳过） */
    void drawSoftRect(float x, float y, float x2, float y2, float radius, int color) {
        if (getAlpha(color) <= 0) {
            return;
        }
        RenderServices.shapes().rounded(x, y, x2, y2, radius, color);
    }

    // ==================== 颜色分量提取 ====================

    int getAlpha(int color) {
        return color >>> 24 & 255;
    }

    int getRed(int color) {
        return color >>> 16 & 255;
    }

    int getGreen(int color) {
        return color >>> 8 & 255;
    }

    int getBlue(int color) {
        return color & 255;
    }

    // ==================== 搜索相关 ====================

    float getSearchY() {
        return contentY + 10.0f;
    }

    /**
     * 设置搜索查询并重置相关状态。
     * <p>
     * 清空选中的模块、重置列表滚动、触发内容淡入动画。
     */
    void setSearchQuery(String query) {
        searchQuery = query == null ? "" : query;
        searchCursorTime = System.currentTimeMillis();
        selectModule(null);
        draggingNumber = null;
        draggingNumberCustomRange = false;
        draggingNumberPair = null;
        listScroll = 0.0f;
        targetListScroll = 0.0f;
        contentFade = 0.0f;
    }

    // ==================== 按键绑定 ====================

    /** 开始为指定模块绑定按键 */
    void startBinding(Module module) {
        bindingModule = module;
        draggingNumber = null;
        draggingNumberCustomRange = false;
        draggingNumberPair = null;
        searchFocused = false;
        addToast("Binding " + module.getName());
    }

    /**
     * 完成按键绑定。
     * <p>
     * 支持：Delete/Backspace 清除绑定，Escape 取消绑定，其他键设置绑定。
     */
    void finishBinding(int keyCode) {
        if (bindingModule == null) {
            return;
        }
        if (keyCode == Keyboard.KEY_DELETE || keyCode == Keyboard.KEY_BACK) {
            bindingModule.setKey(Keyboard.KEY_NONE);
            addToast(bindingModule.getName() + " key cleared");
            bindingModule = null;
            return;
        }
        if (keyCode == Keyboard.KEY_ESCAPE) {
            addToast("Binding cancelled");
            bindingModule = null;
            return;
        }
        if (keyCode != Keyboard.KEY_NONE) {
            bindingModule.setKey(keyCode);
            addToast(bindingModule.getName() + " -> " + getKeyName(bindingModule));
            bindingModule = null;
        }
    }

    /**
     * 计算滚动条的各项指标。
     *
     * @return ScrollbarMetrics 包含可见性、位置、大小等信息
     */
    ScrollbarMetrics getScrollbarMetrics(float drawContentY, float listHeight) {
        float contentHeight = getContentHeight();
        boolean visible = contentHeight > listHeight + 1.0f;
        float trackX = contentX + CARD_W - 7.0f;
        float maxScroll = Math.max(1.0f, contentHeight - listHeight);
        float thumbH = visible ? Math.max(22.0f, listHeight / Math.max(1.0f, contentHeight) * listHeight) : listHeight;
        float scrollPct = clamp(-listScroll / maxScroll, 0.0f, 1.0f);
        float thumbY = drawContentY + (listHeight - thumbH) * scrollPct;
        return new ScrollbarMetrics(visible, trackX, drawContentY, listHeight, thumbY, thumbH, maxScroll);
    }

    /**
     * 绘制按键绑定覆盖层。
     * <p>
     * 在绑定过程中显示半透明背景和提示信息。
     */
    void drawKeybindOverlay(ScaledResolution sr) {
        overlayRenderer.drawKeybindOverlay(sr);
    }

    // ==================== Toast 消息 ====================

    /** 添加 Toast 消息（显示 2.5 秒，最后 0.7 秒渐隐） */
    void addToast(String message) {
        toastText = message;
        toastStarted = System.currentTimeMillis();
    }

    /** 绘制 Toast 消息 */
    void drawToast(ScaledResolution sr) {
        overlayRenderer.drawToast(sr);
    }

    /**
     * 处理键盘输入。
     * <p>
     * 支持：Ctrl+F 聚焦搜索、Escape/RightShift 关闭 GUI。
     */
    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (bindingModule != null) {
            finishBinding(keyCode);
            return;
        }
        if (searchBar.keyTyped(typedChar, keyCode)) {
            return;
        }
        if (keyCode == Keyboard.KEY_F && (Keyboard.isKeyDown(Keyboard.KEY_LCONTROL) || Keyboard.isKeyDown(Keyboard.KEY_RCONTROL))) {
            searchBar.focus();
            return;
        }
        if (keyCode == Keyboard.KEY_ESCAPE || keyCode == Keyboard.KEY_RSHIFT) {
            startClose();
            return;
        }
        super.keyTyped(typedChar, keyCode);
    }

    /** GUI 不暂停游戏 */
    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    /** GUI 关闭时保存配置 */
    @Override
    public void onGuiClosed() {
        saveConfigOnClose();
        super.onGuiClosed();
    }

    /** 检查鼠标是否在矩形区域内 */
    static boolean isHovered(float x, float y, float x2, float y2, int mouseX, int mouseY) {
        return mouseX >= x && mouseX <= x2 && mouseY >= y && mouseY <= y2;
    }

    /** 限制值在 [min, max] 范围内（float 版本） */
    static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    /** 限制值在 [min, max] 范围内（double 版本） */
    static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    /**
     * 更新帧缩放因子和 FPS 统计。
     * <p>
     * 帧缩放因子 = 实际帧间隔 / 目标帧间隔（60fps = 16.67ms），
     * 用于帧率无关的动画速度补偿。
     */
    void updateFrameScale() {
        long now = System.nanoTime();
        long elapsed = now - lastFrameNanos;
        lastFrameNanos = now;
        if (elapsed <= 0L) {
            frameScale = 1.0f;
            return;
        }
        float measuredScale = clamp(elapsed / 16666666.0f, 0.55f, 1.75f);
        frameScale += (measuredScale - frameScale) * 0.18f;
        updateFpsGraph(now, 1000000000.0f / elapsed);
        // 每 250ms 更新一次实时 FPS
        fpsSampleFrames++;
        long sampleElapsed = now - fpsSampleStarted;
        if (sampleElapsed >= 250000000L) {
            liveFps = Math.max(1, Math.round(fpsSampleFrames * 1000000000.0f / sampleElapsed));
            fpsSampleFrames = 0;
            fpsSampleStarted = now;
        }
    }

    /**
     * 更新 FPS 波形图采样数据。
     * <p>
     * 每 ~90ms 采样一次，存入环形缓冲区用于侧边面板的 FPS 折线图。
     */
    private void updateFpsGraph(long now, float instantFps) {
        float fps = clamp(instantFps, 1.0f, 999.0f);
        // 平滑 FPS 值
        if (fpsGraphSmoothed <= 0.0f) {
            fpsGraphSmoothed = fps;
        } else {
            fpsGraphSmoothed += (fps - fpsGraphSmoothed) * 0.22f;
        }
        // 每 90ms 采样
        if (fpsGraphLastSample != 0L && now - fpsGraphLastSample < 90000000L) {
            return;
        }
        fpsGraphLastSample = now;
        fpsGraphSamples[fpsGraphCursor] = fpsGraphSmoothed;
        fpsGraphCursor = (fpsGraphCursor + 1) % fpsGraphSamples.length;
        if (fpsGraphSize < fpsGraphSamples.length) {
            fpsGraphSize++;
        }
    }

    /** 开始关闭 GUI（触发关闭动画并保存配置） */
    void startClose() {
        saveConfigOnClose();
        closing = true;
        draggingNumber = null;
        draggingNumberCustomRange = false;
        draggingNumberPair = null;
    }

    /**
     * 关闭时保存配置。
     * <p>
     * 保存所有已展开的 Mode 下拉栏状态到静态字段，
     * 以便在下次游戏启动时恢复。
     */
    void saveConfigOnClose() {
        if (savedOnClose || Client.instance == null) {
            return;
        }
        savedOnClose = true;
        // 保存所有 module 的展开下拉栏（moduleName:valueName 格式，分号分隔）
        StringBuilder expanded = new StringBuilder();
        for (Module m : ModuleManager.getModules()) {
            for (Value v : m.getValues()) {
                if (v instanceof Mode && detailPanel.hasExpandedMode((Mode) v)) {
                    if (expanded.length() > 0) expanded.append(";");
                    expanded.append(m.getName()).append(":").append(v.getName());
                }
            }
        }
        savedExpandedModeKeys = expanded.toString();
        try {
            Client.SaveConfig();
            addToast("Config saved");
        } catch (IOException ignored) {
            addToast("Config save failed");
        }
    }

    // ==================== GUI 状态持久化 ====================

    /**
     * 将 GUI 状态序列化为 JsonObject，由 FileManager 写入 config JSON 的 _gui 段。
     * <p>
     * 保存内容：当前标签页、选中模块、详情标签页索引、滚动位置、展开的 Mode 下拉栏。
     */
    public static JsonObject saveGuiState() {
        JsonObject obj = new JsonObject();
        obj.addProperty("tab", currentTab.ordinal());
        obj.addProperty("module", selectedModule != null ? selectedModule.getName() : "");
        obj.addProperty("detailTab", detailTabIndex);
        obj.addProperty("listScroll", listScroll);
        obj.addProperty("settingsScroll", settingsScroll);
        // 保存展开的 mode 下拉栏（由 saveConfigOnClose 提前写入静态字段）
        obj.addProperty("expandedModes", savedExpandedModeKeys);
        return obj;
    }

    /**
     * 从 config JSON 的 _gui 段恢复 GUI 状态（游戏启动时调用）。
     * <p>
     * 恢复内容：标签页、选中模块、详情标签页、滚动位置、展开的 Mode 下拉栏。
     * 所有字段均为可选，缺失时保持默认值。
     */
    public static void loadGuiState(JsonObject obj) {
        try {
            int tabOrdinal = obj.get("tab").getAsInt();
            GuiTab[] tabs = GuiTab.values();
            if (tabOrdinal >= 0 && tabOrdinal < tabs.length) {
                currentTab = tabs[tabOrdinal];
            }
        } catch (Exception ignored) {}
        try {
            String moduleName = obj.get("module").getAsString();
            if (moduleName != null && !moduleName.isEmpty()) {
                for (Module m : ModuleManager.getModules()) {
                    if (m.getName().equals(moduleName)) {
                        selectModule(m);
                        break;
                    }
                }
            }
        } catch (Exception ignored) {}
        try {
            detailTabIndex = obj.get("detailTab").getAsInt();
        } catch (Exception ignored) {}
        try {
            listScroll = obj.get("listScroll").getAsFloat();
            targetListScroll = listScroll;
            settingsScroll = obj.get("settingsScroll").getAsFloat();
            targetSettingsScroll = settingsScroll;
        } catch (Exception ignored) {}
        try {
            savedExpandedModeKeys = obj.get("expandedModes").getAsString();
        } catch (Exception ignored) {}
    }

    // ==================== Scissor 裁剪 ====================

    /** 开始 OpenGL 裁剪区域（限制渲染范围） */
    void beginScissor(float x, float y, float w, float h) {
        GLStateManager.pushScissor(x, y, w, h);
    }

    /** 结束 OpenGL 裁剪区域 */
    void endScissor() {
        GLStateManager.popScissor();
    }

    /**
     * 滚动条指标数据类，封装滚动条的所有计算参数。
     */
    static class ScrollbarMetrics {
        final boolean visible;
        final float trackX;
        final float trackY;
        final float trackHeight;
        final float thumbY;
        final float thumbHeight;
        final float maxScroll;

        ScrollbarMetrics(boolean visible, float trackX, float trackY, float trackHeight,
                         float thumbY, float thumbHeight, float maxScroll) {
            this.visible = visible;
            this.trackX = trackX;
            this.trackY = trackY;
            this.trackHeight = trackHeight;
            this.thumbY = thumbY;
            this.thumbHeight = thumbHeight;
            this.maxScroll = maxScroll;
        }
    }
}
