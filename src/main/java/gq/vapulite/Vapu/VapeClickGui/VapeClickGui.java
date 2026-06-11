package gq.vapulite.Vapu.VapeClickGui;

import gq.vapulite.Manager.ModuleManager;
import gq.vapulite.Vapu.Client;
import gq.vapulite.Vapu.ModuleType;
import gq.vapulite.Vapu.modules.Module;
import gq.vapulite.Vapu.modules.render.ClickGUI;
import gq.vapulite.Vapu.utils.RenderUtil;
import gq.vapulite.Vapu.value.Mode;
import gq.vapulite.Vapu.value.Numbers;
import gq.vapulite.Vapu.value.Option;
import gq.vapulite.Vapu.value.Value;
import gq.vapulite.font.CFontRenderer;
import gq.vapulite.font.FontLoaders;
import gq.vapulite.render.RenderState;
import gq.vapulite.render.ShaderRenderer;
import gq.vapulite.ui.UiTextField;
import gq.vapulite.ui.UiTheme;
import gq.vapulite.ui.UiToggle;
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

public class VapeClickGui extends GuiScreen {
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

        static final GuiPalette DARK = new GuiPalette(
                new Color(9, 13, 18, 164).getRGB(),
                new Color(12, 15, 20, 232).getRGB(),
                new Color(17, 21, 27, 222).getRGB(),
                new Color(25, 30, 38, 232).getRGB(),
                new Color(34, 35, 77, 238).getRGB(),
                new Color(232, 234, 236).getRGB(),
                new Color(152, 154, 158).getRGB(),
                new Color(83, 86, 92).getRGB(),
                new Color(112, 193, 220).getRGB(),
                new Color(196, 78, 83).getRGB(),
                new Color(7, 9, 13, 154).getRGB(),
                new Color(7, 9, 13, 122).getRGB(),
                new Color(154, 190, 214, 58).getRGB(),
                new Color(37, 43, 54, 190).getRGB(),
                new Color(55, 54, 130, 218).getRGB(),
                new Color(132, 121, 255).getRGB(),
                new Color(112, 193, 220).getRGB(),
                new Color(61, 67, 82, 178).getRGB(),
                new Color(132, 117, 255, 230).getRGB(),
                new Color(35, 38, 62, 200).getRGB(),
                new Color(88, 90, 178, 160).getRGB(),
                new Color(55, 58, 70, 140).getRGB(),
                new Color(18, 22, 30, 240).getRGB(),
                new Color(0, 0, 0, 200).getRGB(),
                new Color(0, 0, 0, 210).getRGB());

        static final GuiPalette LIGHT = new GuiPalette(
                new Color(230, 235, 242, 148).getRGB(),
                new Color(220, 225, 234, 220).getRGB(),
                new Color(240, 244, 250, 210).getRGB(),
                new Color(225, 230, 240, 218).getRGB(),
                new Color(200, 205, 235, 225).getRGB(),
                new Color(28, 30, 36).getRGB(),
                new Color(105, 110, 120).getRGB(),
                new Color(155, 162, 175).getRGB(),
                new Color(24, 142, 198).getRGB(),
                new Color(182, 50, 55).getRGB(),
                new Color(232, 236, 244, 148).getRGB(),
                new Color(225, 230, 240, 118).getRGB(),
                new Color(160, 175, 198, 54).getRGB(),
                new Color(210, 215, 228, 170).getRGB(),
                new Color(190, 192, 220, 200).getRGB(),
                new Color(130, 160, 240).getRGB(),
                new Color(24, 142, 198).getRGB(),
                new Color(190, 195, 210, 170).getRGB(),
                new Color(120, 165, 235, 215).getRGB(),
                new Color(210, 215, 235, 190).getRGB(),
                new Color(170, 185, 228, 148).getRGB(),
                new Color(200, 205, 220, 132).getRGB(),
                new Color(218, 224, 238, 228).getRGB(),
                new Color(255, 255, 255, 120).getRGB(),
                new Color(255, 255, 255, 140).getRGB());
    }

    GuiPalette guiColors() {
        try {
            return gq.vapulite.Vapu.modules.render.HUD.isLightTheme() ? GuiPalette.LIGHT : GuiPalette.DARK;
        } catch (Exception e) {
            return GuiPalette.DARK;
        }
    }

    int shadowColor(int alpha) {
        try {
            return gq.vapulite.Vapu.modules.render.HUD.isLightTheme()
                    ? withAlpha(0xFFFFFFFF, alpha)
                    : new Color(0, 0, 0, alpha).getRGB();
        } catch (Exception e) {
            return new Color(0, 0, 0, alpha).getRGB();
        }
    }

    void drawThemedGlass(float x, float y, float x2, float y2, float radius, float strength, int fill, int border) {
        if (gq.vapulite.Vapu.modules.render.HUD.isLightTheme()) {
            RenderUtil.drawRoundedBorderedRect(x, y, x2, y2, radius, strength, fill, border);
        } else {
            RenderUtil.drawFrostedGlassRect(x, y, x2, y2, radius, strength, fill, border);
        }
    }

    static final float NAV_H = 28.0f;
    static final float CARD_W = 222.0f;
    static final float CARD_H = 50.0f;
    static final float GAP = 10.0f;
    static final float SEARCH_H = 38.0f;
    static final float PANEL_RADIUS = 8.0f;
    static final float CARD_RADIUS = 7.0f;
    static final float DETAIL_MIN_W = 350.0f;
    static final float DETAIL_MAX_W = 560.0f;
    static final float SIDE_W = 170.0f;
    static final float DETAIL_HEADER_H = 98.0f;
    static final float VALUE_ROW_H = 26.0f;
    static final float NUMBER_ROW_H = 40.0f;
    static final float RANGE_ROW_H = 44.0f;
    static final float MODE_ROW_H = 30.0f;
    static final float COLOR_ROW_H = 64.0f;
    static final float SWITCH_W = 28.0f;
    static final float SWITCH_H = 14.0f;
    static final float SWITCH_HIT_PAD = 5.0f;
    static final float CLOSE_END_PROGRESS = 0.22f;
    static final float CLOSING_TEXT_CUTOFF = 0.36f;
    static final int FPS_GRAPH_SAMPLES = 44;

    static GuiTab currentTab = GuiTab.COMBAT;
    static Module selectedModule;
    static final Map<String, Float> detailScrollByModule = new HashMap<>(); // 每个module记住各自的scroll

    /** 切换选中module时保留/恢复detail panel滚动位置 */
    static void selectModule(Module m) {
        if (selectedModule != null) {
            detailScrollByModule.put(selectedModule.getName(), settingsScroll);
        }
        selectedModule = m;
        if (m != null && detailScrollByModule.containsKey(m.getName())) {
            settingsScroll = detailScrollByModule.get(m.getName());
            targetSettingsScroll = settingsScroll;
        } else {
            settingsScroll = 0;
            targetSettingsScroll = 0;
        }
    }
    Value draggingNumber;
    Numbers draggingColorRed;
    Numbers draggingColorGreen;
    Numbers draggingColorBlue;
    Module bindingModule;
    final Map<Module, Float> hoverProgress = new HashMap<Module, Float>();
    final Map<Module, Float> clickProgress = new HashMap<Module, Float>();
    final Map<Module, Float> toggleProgress = new HashMap<Module, Float>();
    final Map<GuiTab, Float> tabHoverProgress = new HashMap<GuiTab, Float>();
    final Map<Value, Float> valueToggleProgress = new HashMap<Value, Float>();
    final Map<Value, Float> valueActiveProgress = new HashMap<Value, Float>();
    final Set<Module> favoriteModules = new HashSet<Module>();
    float draggingNumberX;
    float draggingNumberW;
    boolean draggingNumberCustomRange;
    double draggingNumberMin;
    double draggingNumberMax;
    Numbers draggingNumberPair;
    boolean draggingNumberLowerBound;
    float draggingColorX;
    float draggingColorY;
    float draggingColorW;
    float draggingColorH;
    static float listScroll;
    static float targetListScroll;
    static float settingsScroll;
    static float targetSettingsScroll;
    static String savedExpandedModeKeys = ""; // 游戏重启后恢复展开的mode下拉栏
    float scrollbarAlpha;
    boolean draggingScrollbar;
    float scrollbarDragOffset;
    float openProgress;
    float guiAlpha;
    float navIndicatorX;
    float contentFade;
    float searchFocusProgress;
    float navX;
    float navY;
    float navW;
    float contentX;
    float contentY;
    float detailX;
    float detailW;
    float sideX;
    float sideW;
    float windowW;
    float panelH;
    boolean sidePanelVisible;
    static int detailTabIndex;
    String searchQuery = "";
    boolean searchFocused;
    long searchCursorTime;
    String toastText;
    long toastStarted;
    boolean closing;
    boolean savedOnClose;
    long lastPaletteClickMS;
    String lastPaletteClickKey;
    long lastFrameNanos;
    long fpsSampleStarted;
    int fpsSampleFrames;
    int liveFps;
    final float[] fpsGraphSamples = new float[FPS_GRAPH_SAMPLES];
    int fpsGraphCursor;
    int fpsGraphSize;
    long fpsGraphLastSample;
    float fpsGraphSmoothed;
    float frameScale = 1.0f;
    final UiTheme uiTheme = UiTheme.current();
    final UiToggle reusableToggle = new UiToggle().setTheme(uiTheme);
    final UiTextField searchField = new UiTextField().setTheme(uiTheme).placeholder("Search modules...").maxLength(32);
    final ClickGuiSearchBar searchBar = new ClickGuiSearchBar(this);
    final ClickGuiModuleList moduleList = new ClickGuiModuleList(this);
    final ClickGuiDetailPanel detailPanel = new ClickGuiDetailPanel(this);
    final ClickGuiSidePanel sidePanel = new ClickGuiSidePanel(this);
    final ClickGuiBottomBar bottomBar = new ClickGuiBottomBar(this);

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
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        updateFrameScale();
        ScaledResolution sr = new ScaledResolution(mc);
        updateLayout(sr);
        ensureSelectedModule();
        openProgress = animate(openProgress, closing ? 0.0f : 1.0f, closing ? 0.20f : 0.16f);
        guiAlpha = openProgress * gq.vapulite.Vapu.modules.render.ClickGUI.clickGuiAlpha.getValue().floatValue();
        contentFade = animate(contentFade, closing ? 0.0f : 1.0f, closing ? 0.18f : 0.14f);
        if (closing && openProgress <= CLOSE_END_PROGRESS) {
            mc.displayGuiScreen(null);
            return;
        }
        drawBackdrop(sr);
        ShaderRenderer.invalidateFrostedGlass();
        if (!closing) {
            moduleList.updateScrollbarDrag(mouseY);
            updateScroll(mouseX, mouseY);
        }
        if (!closing && draggingColorRed != null && Mouse.isButtonDown(0)) {
            detailPanel.updateColorValue(mouseX, mouseY);
        } else if (!Mouse.isButtonDown(0)) {
            clearDraggingColor();
        }
        if (!closing && draggingNumber instanceof Numbers && Mouse.isButtonDown(0)) {
            detailPanel.updateNumberValue((Numbers) draggingNumber, mouseX, draggingNumberX, draggingNumberW);
        } else {
            draggingNumber = null;
            draggingNumberCustomRange = false;
            draggingNumberPair = null;
        }

        float introY = (1.0f - easeOut(openProgress)) * (closing ? 18.0f : -10.0f);
        drawBrand(introY);
        drawNavigation(mouseX, mouseY, introY);
        moduleList.render(mouseX, mouseY, introY);
        searchBar.render(mouseX, mouseY, introY);
        detailPanel.render(mouseX, mouseY, introY);
        sidePanel.render(sr, mouseX, mouseY, introY);
        bottomBar.render(sr);
        super.drawScreen(mouseX, mouseY, partialTicks);
        drawKeybindOverlay(sr);
        drawToast(sr);
    }

    void updateLayout(ScaledResolution sr) {
        float screenW = sr.getScaledWidth();
        float screenH = sr.getScaledHeight();
        sidePanelVisible = screenW >= 900.0f;
        sideW = sidePanelVisible ? SIDE_W : 0.0f;
        float available = Math.max(360.0f, screenW - 24.0f);
        detailW = Math.min(DETAIL_MAX_W, Math.max(DETAIL_MIN_W, available - CARD_W - GAP - (sidePanelVisible ? sideW + GAP : 0.0f)));
        float totalW = CARD_W + GAP + detailW + (sidePanelVisible ? GAP + sideW : 0.0f);
        if (totalW > available) {
            detailW = Math.max(DETAIL_MIN_W, available - CARD_W - GAP - (sidePanelVisible ? sideW + GAP : 0.0f));
            totalW = CARD_W + GAP + detailW + (sidePanelVisible ? GAP + sideW : 0.0f);
        }
        windowW = totalW;
        contentX = Math.max(10.0f, screenW / 2.0f - totalW / 2.0f);
        if (ClickGUI.windowX.getValue() >= 0.0D) {
            contentX = clamp(ClickGUI.windowX.getValue().floatValue(), 10.0f, Math.max(10.0f, screenW - totalW - 10.0f));
        }
        navY = 12.0f;
        if (ClickGUI.windowY.getValue() >= 0.0D) {
            navY = clamp(ClickGUI.windowY.getValue().floatValue(), 6.0f, Math.max(6.0f, screenH - 260.0f));
        }
        detailX = contentX + CARD_W + GAP;
        sideX = detailX + detailW + GAP;
        navX = detailX;
        navW = detailW;
        contentY = navY + NAV_H + 12.0f;
        panelH = Math.max(220.0f, screenH - contentY - 48.0f);
    }

    void drawBackdrop(ScaledResolution sr) {
        RenderUtil.drawRect(0, 0, sr.getScaledWidth(), sr.getScaledHeight(), withAlpha(guiColors().backdrop, 94.0f * guiAlpha));
        RenderUtil.drawGradientRect(0, 0, sr.getScaledWidth(), sr.getScaledHeight(),
                withAlpha(new Color(51, 73, 99, 44).getRGB(), 44.0f * guiAlpha),
                withAlpha(new Color(6, 8, 10, 92).getRGB(), 92.0f * guiAlpha));
        RenderUtil.drawGradientRect(0, sr.getScaledHeight() * 0.62f, sr.getScaledWidth(), sr.getScaledHeight(),
                withAlpha(new Color(0, 0, 0, 0).getRGB(), 0.0f),
                withAlpha(new Color(0, 0, 0, 130).getRGB(), 92.0f * guiAlpha));
    }

    void drawBrand(float introY) {
        float x = contentX + 4.0f;
        float y = navY + 1.0f + introY * 0.35f;
        FontLoaders.F18.drawString("VAPE", x, y, withAlpha(guiColors().text, 255.0f * guiAlpha));
        drawSoftRect(x + 42.0f, y + 1.0f, x + 61.0f, y + 12.0f, 4.0f,
                withAlpha(guiColors().detailSelectedFill, 180.0f * guiAlpha));
        drawCenteredText("V4", x + 42.0f, y + 2.0f, x + 61.0f, y + 12.0f,
                withAlpha(new Color(154, 148, 255).getRGB(), 230.0f * guiAlpha));
        drawFont("Material 3 x VapuLite", x, y + 18.0f, withAlpha(guiColors().muted, 190.0f * guiAlpha));
    }

    void drawNavigation(int mouseX, int mouseY, float introY) {
        float y = navY + introY;
        RenderUtil.drawSoftShadow(navX, y, navX + navW, y + NAV_H, 9.0f,
                withAlpha(shadowColor(210), 70.0f * guiAlpha), 7, 5.0f);
        drawThemedGlass(navX, y, navX + navW, y + NAV_H, 9.0f, 1.0f,
                withAlpha(guiColors().glassFillSoft, 186.0f * guiAlpha),
                withAlpha(guiColors().glassBorder, 52.0f * guiAlpha));
        float tabW = navW / GuiTab.values().length;
        float targetX = navX + currentTab.ordinal() * tabW + 2.0f;
        navIndicatorX = animate(navIndicatorX, targetX, 0.18f);
        RenderUtil.drawSoftShadow(navIndicatorX, y + 4.0f, navIndicatorX + tabW - 4.0f, y + NAV_H - 4.0f, 7.0f,
                withAlpha(guiColors().accent, 85.0f * guiAlpha), 5, 4.0f);
        RenderUtil.drawRoundedBorderedRect(navIndicatorX, y + 4.0f, navIndicatorX + tabW - 4.0f, y + NAV_H - 4.0f, 7.0f, 0.8f,
                withAlpha(guiColors().detailSelectedFill, 232.0f * guiAlpha),
                withAlpha(guiColors().detailSelectedBorder, 80.0f * guiAlpha));
        for (int i = 0; i < GuiTab.values().length; i++) {
            GuiTab tab = GuiTab.values()[i];
            float x = navX + i * tabW;
            boolean hovered = isHovered(x, y, x + tabW, y + NAV_H, mouseX, mouseY);
            float hover = animateTabMap(tab, hovered && tab != currentTab && !closing ? 1.0f : 0.0f, 0.18f);
            if (hover > 0.01f) {
                drawSoftRect(x + 3.0f, y + 4.0f, x + tabW - 3.0f, y + NAV_H - 4.0f, 7.0f,
                        withAlpha(guiColors().navDefaultHover, 190.0f * hover * guiAlpha));
            }
            int textColor = tab == currentTab ? guiColors().text : guiColors().muted;
            int color = withAlpha(textColor, 245.0f * guiAlpha);
            CFontRenderer navIconFont = FontLoaders.I16;
            String title = trim(tab.title, FontLoaders.F14, Math.max(18.0f, tabW - 42.0f));
            float iconW = navIconFont.getStringWidth(tab.icon);
            float titleW = FontLoaders.F14.getStringWidth(title);
            float gap = 10.0f;
            float groupX = x + (tabW - iconW - gap - titleW) / 2.0f;
            float textY = y + 10.0f - hover * 0.4f;
            drawCenteredIcon(tab.icon, navIconFont, groupX + iconW / 2.0f, y + NAV_H / 2.0f - hover * 0.4f, color);
            drawFont(title, groupX + iconW + gap, textY, color);
        }
    }

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

    String getPingText() {
        try {
            if (mc.thePlayer != null && mc.getNetHandler() != null && mc.getNetHandler().getPlayerInfo(mc.thePlayer.getUniqueID()) != null) {
                return mc.getNetHandler().getPlayerInfo(mc.thePlayer.getUniqueID()).getResponseTime() + " ms";
            }
        } catch (Throwable ignored) {
        }
        return "-- ms";
    }

    String getLiveFpsText() {
        if (fpsGraphSmoothed > 0.0f) {
            return String.valueOf(Math.max(1, Math.round(fpsGraphSmoothed)));
        }
        return liveFps <= 0 ? "--" : String.valueOf(liveFps);
    }

    int getFpsGraphSize() {
        return fpsGraphSize;
    }

    float getFpsGraphSample(int index) {
        if (fpsGraphSize <= 0) {
            return fpsGraphSmoothed > 0.0f ? fpsGraphSmoothed : liveFps;
        }
        int clamped = Math.max(0, Math.min(fpsGraphSize - 1, index));
        int start = fpsGraphSize == fpsGraphSamples.length ? fpsGraphCursor : 0;
        return fpsGraphSamples[(start + clamped) % fpsGraphSamples.length];
    }

    int getEnabledModules() {
        int enabled = 0;
        for (Module module : ModuleManager.getModules()) {
            if (module.getState()) {
                enabled++;
            }
        }
        return enabled;
    }

    float getDetailValuesX() {
        return detailX + 20.0f;
    }

    float getDetailValuesY(float panelY) {
        return panelY + DETAIL_HEADER_H + 8.0f;
    }

    float getDetailValuesWidth() {
        return detailW - 40.0f;
    }

    float getDetailValuesHeight() {
        return panelH - DETAIL_HEADER_H - 18.0f;
    }

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

    void updateScroll(int mouseX, int mouseY) {
        int wheel = Mouse.getDWheel();
        if (draggingScrollbar || wheel == 0) {
            return;
        }
        if (detailPanel.updateScroll(mouseX, mouseY, wheel)) {
            return;
        }
        moduleList.updateScroll(mouseX, mouseY, wheel);
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        if (bindingModule != null) {
            return;
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
        currentTab = tab;
        selectModule(null);
        searchFocused = false;
        setSearchQuery("");
        contentFade = 0.0f;
        targetListScroll = 0.0f;
        listScroll = 0.0f;
        return true;
    }

    @Override
    protected void mouseReleased(int mouseX, int mouseY, int state) {
        draggingNumber = null;
        draggingNumberCustomRange = false;
        draggingNumberPair = null;
        clearDraggingColor();
        draggingScrollbar = false;
        super.mouseReleased(mouseX, mouseY, state);
    }

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

    boolean matchesSearch(Module module, String query) {
        if (module.getName().toLowerCase(Locale.ROOT).contains(query)) {
            return true;
        }
        if (module.Descript != null && module.Descript.toLowerCase(Locale.ROOT).contains(query)) {
            return true;
        }
        return module.getCategory() != null && module.getCategory().name().toLowerCase(Locale.ROOT).contains(query);
    }

    float getCardHeight(Module module) {
        return CARD_H;
    }

    float getContentHeight() {
        List<Module> modules = getVisibleModules();
        if (modules.isEmpty()) {
            return 0.0f;
        }
        return modules.size() * (CARD_H + 6.0f) - 6.0f;
    }

    float getListHeight() {
        return Math.max(120.0f, panelH - SEARCH_H - 52.0f);
    }

    float getModuleListY() {
        return contentY + SEARCH_H + 22.0f;
    }

    float getValueHeight(Value value) {
        if (value instanceof Numbers) {
            return NUMBER_ROW_H;
        }
        if (value instanceof Mode) {
            return MODE_ROW_H;
        }
        return VALUE_ROW_H;
    }

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

    boolean isColorStart(Module module, int index) {
        if (module == null || index < 0 || index + 2 >= module.getValues().size()) {
            return false;
        }
        List<Value> values = module.getValues();
        return isNumberNamed(values.get(index), "red")
                && isNumberNamed(values.get(index + 1), "green")
                && isNumberNamed(values.get(index + 2), "blue");
    }

    boolean isColorContinuation(Module module, int index) {
        return isColorStart(module, index - 1) || isColorStart(module, index - 2);
    }

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

    boolean isRangeContinuation(Module module, int index) {
        return isRangeStart(module, index - 1);
    }

    String getRangeDisplayName(Value value) {
        String base = rangeDisplayBase(value, "min");
        return base.length() == 0 ? getDisplayName(value) : base;
    }

    boolean isHiddenPaletteValue(Module module, Value value) {
        if (module == null || value == null || !(value instanceof Option)) {
            return false;
        }
        String moduleName = module.getName() == null ? "" : module.getName().replace(" ", "").toLowerCase(Locale.ROOT);
        String valueName = normalizeValueName(value);
        return moduleName.equals("esp") && (valueName.equals("rainbow") || valueName.equals("paletterainbow"));
    }

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

        if (containsAny(raw, "yaw", "pitch", "rotate", "rotation", "aim", "aimpoint",
                "prediction", "freezone", "reaction", "lock", "randomize")) {
            return 3;
        }
        if (containsAny(raw, "player", "mob", "animal", "invisible", "target", "priority",
                "throughwall", "wallcheck", "range", "reach", "fov", "hurt", "hitbox", "expand")) {
            return 1;
        }
        if (containsAny(raw, "render", "visual", "shader", "trail", "color", "alpha", "radius",
                "height", "line", "pulse", "background", "watermark", "arraylist", "notification",
                "potion", "inventory", "scale", "xposition", "yposition", "xoffset", "yoffset",
                "bottom", "red", "green", "blue")) {
            return 4;
        }
        if (name.equals("x") || name.equals("y")) {
            return 4;
        }
        if (containsAny(raw, "weapon", "sword", "mouse", "moving", "sprint", "rightclick",
                "auto", "swap", "restore", "block", "sneak", "ground", "scope", "release",
                "break", "require", "only", "hold", "key")) {
            return 2;
        }
        return 0;
    }

    private boolean containsAny(String text, String... needles) {
        for (String needle : needles) {
            if (text.contains(needle)) {
                return true;
            }
        }
        return false;
    }

    private String normalizeValueText(Value value) {
        String display = value == null || value.getDisplayName() == null ? "" : value.getDisplayName();
        String name = value == null || value.getName() == null ? "" : value.getName();
        return (display + " " + name).replace(" ", "").replace("_", "").replace("-", "").toLowerCase(Locale.ROOT);
    }

    String getDisplayName(Value value) {
        String display = value == null ? "" : value.getDisplayName();
        if (display == null || display.trim().length() == 0) {
            display = value == null ? "" : value.getName();
        }
        return display == null ? "" : display;
    }

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

    private boolean isNumberNamed(Value value, String name) {
        return value instanceof Numbers && normalizeValueName(value).equals(name);
    }

    String normalizeValueName(Value value) {
        String raw = value == null ? "" : value.getName();
        if (raw == null || raw.length() == 0) {
            raw = value == null ? "" : value.getDisplayName();
        }
        return raw == null ? "" : raw.replace(" ", "").replace("_", "").replace("-", "").toLowerCase(Locale.ROOT);
    }

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

    void clearDraggingColor() {
        draggingColorRed = null;
        draggingColorGreen = null;
        draggingColorBlue = null;
    }

    void ensureSelectedModule() {
        List<Module> modules = getVisibleModules();
        if (selectedModule != null && modules.contains(selectedModule)) {
            return;
        }
        selectModule(modules.isEmpty() ? null : modules.get(0));
    }

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
        return cardX + CARD_W - SWITCH_W - 62.0f;
    }

    float getModuleSwitchY(float cardY) {
        return cardY + 14.0f;
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

    boolean isSwitchHit(float switchX, float switchY, int mouseX, int mouseY) {
        return isHovered(switchX - SWITCH_HIT_PAD, switchY - SWITCH_HIT_PAD,
                switchX + SWITCH_W + SWITCH_HIT_PAD, switchY + SWITCH_H + SWITCH_HIT_PAD, mouseX, mouseY);
    }

    String getDescription(Module module) {
        if (module.Descript == null || module.Descript.trim().length() == 0) {
            return "Configure this module.";
        }
        return module.Descript;
    }

    String getKeyName(Module module) {
        if (module.getKey() == Keyboard.KEY_NONE) {
            return "NONE";
        }
        String keyName = Keyboard.getKeyName(module.getKey());
        return keyName == null ? "NONE" : keyName;
    }

    String formatNumber(double value) {
        if (Math.abs(value - Math.round(value)) < 0.0001D) {
            return String.valueOf((long) Math.round(value));
        }
        return String.format(Locale.ROOT, "%.2f", value);
    }

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

    boolean shouldDrawText(int color) {
        if (getAlpha(color) < 18) {
            return false;
        }
        return !closing || openProgress > CLOSING_TEXT_CUTOFF;
    }

    void drawCenteredText(String text, float x, float y, float x2, float y2, int color) {
        float textX = x + (x2 - x - FontLoaders.F14.getStringWidth(text)) / 2.0f;
        float textY = y + (y2 - y - FontLoaders.F14.getStringHeight(text)) / 2.0f + 0.5f;
        drawFont(text, textX, textY, color);
    }

    void drawCenteredIcon(String icon, CFontRenderer font, float centerX, float centerY, int color) {
        if (shouldDrawText(color)) {
            font.drawString(icon, centerX - font.getStringWidth(icon) / 2.0f + ClickGuiIcons.visualOffsetX(icon),
                    centerY - font.getHeight() / 2.0f + 2.0f + ClickGuiIcons.visualOffsetY(icon), color);
        }
    }

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

    float animate(float current, float target, float speed) {
        float adjustedSpeed = 1.0f - (float) Math.pow(1.0f - clamp(speed, 0.01f, 1.0f), frameScale);
        float value = current + (target - current) * adjustedSpeed;
        if (Math.abs(value - target) < 0.00045f) {
            return target;
        }
        return value;
    }

    float easeOut(float value) {
        value = clamp(value, 0.0f, 1.0f);
        return 1.0f - (float) Math.pow(1.0f - value, 4.0D);
    }

    float easeSmooth(float value) {
        value = clamp(value, 0.0f, 1.0f);
        return value * value * (3.0f - 2.0f * value);
    }

    int blendColor(int from, int to, float progress) {
        progress = clamp(progress, 0.0f, 1.0f);
        int a = (int) (getAlpha(from) + (getAlpha(to) - getAlpha(from)) * progress);
        int r = (int) (getRed(from) + (getRed(to) - getRed(from)) * progress);
        int g = (int) (getGreen(from) + (getGreen(to) - getGreen(from)) * progress);
        int b = (int) (getBlue(from) + (getBlue(to) - getBlue(from)) * progress);
        return ((a & 255) << 24) | ((r & 255) << 16) | ((g & 255) << 8) | (b & 255);
    }

    int withAlpha(int color, float alpha) {
        int a = (int) clamp(alpha, 0.0f, 255.0f);
        return (color & 0x00FFFFFF) | (a << 24);
    }

    void drawSoftRect(float x, float y, float x2, float y2, float radius, int color) {
        if (getAlpha(color) <= 0) {
            return;
        }
        RenderUtil.drawRoundedRect(x, y, x2, y2, radius, color);
    }

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

    float getSearchY() {
        return contentY + 10.0f;
    }

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

    void startBinding(Module module) {
        bindingModule = module;
        draggingNumber = null;
        draggingNumberCustomRange = false;
        draggingNumberPair = null;
        searchFocused = false;
        addToast("Binding " + module.getName());
    }

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

    void drawKeybindOverlay(ScaledResolution sr) {
        if (bindingModule == null) {
            return;
        }
        RenderUtil.drawRect(0.0f, 0.0f, sr.getScaledWidth(), sr.getScaledHeight(), withAlpha(new Color(0, 0, 0).getRGB(), 92.0f));
        float boxW = 210.0f;
        float boxH = 84.0f;
        float x = sr.getScaledWidth() / 2.0f - boxW / 2.0f;
        float y = sr.getScaledHeight() / 2.0f - boxH / 2.0f;
        drawThemedGlass(x, y, x + boxW, y + boxH, 8.0f, 1.0f,
                withAlpha(guiColors().glassFill, 218.0f), withAlpha(guiColors().accent, 130.0f));
        drawCenteredText("KEYBIND", x, y + 14.0f, x + boxW, y + 25.0f, guiColors().text);
        drawCenteredText(bindingModule.getName(), x, y + 34.0f, x + boxW, y + 45.0f, withAlpha(guiColors().text, 220.0f));
        drawCenteredText("Current: " + getKeyName(bindingModule), x, y + 49.0f, x + boxW, y + 60.0f, withAlpha(guiColors().muted, 215.0f));
        drawCenteredText("Press key, DEL clears, ESC cancels", x, y + 66.0f, x + boxW, y + 77.0f, withAlpha(guiColors().muted, 185.0f));
    }

    void addToast(String message) {
        toastText = message;
        toastStarted = System.currentTimeMillis();
    }

    void drawToast(ScaledResolution sr) {
        if (toastText == null) {
            return;
        }
        long elapsed = System.currentTimeMillis() - toastStarted;
        if (elapsed > 2500L) {
            toastText = null;
            return;
        }
        float alpha = elapsed < 1800L ? 1.0f : 1.0f - (elapsed - 1800L) / 700.0f;
        float w = FontLoaders.F14.getStringWidth(toastText) + 20.0f;
        float x = sr.getScaledWidth() / 2.0f - w / 2.0f;
        float y = navY + NAV_H + SEARCH_H + 12.0f;
        drawThemedGlass(x, y, x + w, y + 17.0f, 6.0f, 0.8f,
                withAlpha(guiColors().glassFillSoft, 194.0f * alpha),
                withAlpha(guiColors().accent, 75.0f * alpha));
        drawCenteredText(toastText, x, y + 4.0f, x + w, y + 14.0f, withAlpha(guiColors().text, 230.0f * alpha));
    }

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

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    @Override
    public void onGuiClosed() {
        saveConfigOnClose();
        super.onGuiClosed();
    }

    static boolean isHovered(float x, float y, float x2, float y2, int mouseX, int mouseY) {
        return mouseX >= x && mouseX <= x2 && mouseY >= y && mouseY <= y2;
    }

    static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

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
        fpsSampleFrames++;
        long sampleElapsed = now - fpsSampleStarted;
        if (sampleElapsed >= 250000000L) {
            liveFps = Math.max(1, Math.round(fpsSampleFrames * 1000000000.0f / sampleElapsed));
            fpsSampleFrames = 0;
            fpsSampleStarted = now;
        }
    }

    private void updateFpsGraph(long now, float instantFps) {
        float fps = clamp(instantFps, 1.0f, 999.0f);
        if (fpsGraphSmoothed <= 0.0f) {
            fpsGraphSmoothed = fps;
        } else {
            fpsGraphSmoothed += (fps - fpsGraphSmoothed) * 0.22f;
        }
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

    void startClose() {
        saveConfigOnClose();
        closing = true;
        draggingNumber = null;
        draggingNumberCustomRange = false;
        draggingNumberPair = null;
    }

    void saveConfigOnClose() {
        if (savedOnClose || Client.instance == null) {
            return;
        }
        savedOnClose = true;
        // 保存所有module的展开下拉栏（moduleName:valueName格式，分号分隔）
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
     * 将GUI状态序列化为JsonObject，由FileManager写入config JSON的_gui段
     */
    public static JsonObject saveGuiState() {
        JsonObject obj = new JsonObject();
        obj.addProperty("tab", currentTab.ordinal());
        obj.addProperty("module", selectedModule != null ? selectedModule.getName() : "");
        obj.addProperty("detailTab", detailTabIndex);
        obj.addProperty("listScroll", listScroll);
        obj.addProperty("settingsScroll", settingsScroll);
        // 保存展开的mode下拉栏（由saveConfigOnClose提前写入静态字段）
        obj.addProperty("expandedModes", savedExpandedModeKeys);
        return obj;
    }

    /**
     * 从config JSON的_gui段恢复GUI状态（游戏启动时调用）
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

    void beginScissor(float x, float y, float w, float h) {
        RenderState.pushScissor(x, y, w, h);
    }

    void endScissor() {
        RenderState.popScissor();
    }

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
