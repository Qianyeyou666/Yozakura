package gq.vapulite.Vapu.VapeClickGui;

import gq.vapulite.Manager.ModuleManager;
import gq.vapulite.Vapu.Client;
import gq.vapulite.Vapu.ModuleType;
import gq.vapulite.Vapu.modules.Module;
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
    static final int BACKDROP = new Color(9, 13, 18, 164).getRGB();
    static final int TOP_BAR = new Color(12, 15, 20, 232).getRGB();
    static final int CARD = new Color(17, 21, 27, 222).getRGB();
    static final int CARD_HOVER = new Color(25, 30, 38, 232).getRGB();
    static final int CARD_OPEN = new Color(34, 35, 77, 238).getRGB();
    static final int TEXT = new Color(232, 234, 236).getRGB();
    static final int MUTED = new Color(152, 154, 158).getRGB();
    static final int FAINT = new Color(83, 86, 92).getRGB();
    static final int ACCENT = new Color(112, 193, 220).getRGB();
    static final int RED = new Color(196, 78, 83).getRGB();
    static final int GLASS_FILL = new Color(7, 9, 13, 154).getRGB();
    static final int GLASS_FILL_SOFT = new Color(7, 9, 13, 122).getRGB();
    static final int GLASS_BORDER = new Color(154, 190, 214, 58).getRGB();

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
    static final float SWITCH_W = 28.0f;
    static final float SWITCH_H = 14.0f;
    static final float SWITCH_HIT_PAD = 5.0f;
    static final float CLOSE_END_PROGRESS = 0.22f;
    static final float CLOSING_TEXT_CUTOFF = 0.36f;

    GuiTab currentTab = GuiTab.COMBAT;
    Module selectedModule;
    Value draggingNumber;
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
    float listScroll;
    float targetListScroll;
    float settingsScroll;
    float targetSettingsScroll;
    float scrollbarAlpha;
    boolean draggingScrollbar;
    float scrollbarDragOffset;
    float openProgress;
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
    float panelH;
    boolean sidePanelVisible;
    int detailTabIndex;
    String searchQuery = "";
    boolean searchFocused;
    long searchCursorTime;
    String toastText;
    long toastStarted;
    boolean closing;
    boolean savedOnClose;
    long lastFrameNanos;
    long fpsSampleStarted;
    int fpsSampleFrames;
    int liveFps;
    float frameScale = 1.0f;
    final UiTheme uiTheme = UiTheme.vape();
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
        listScroll = 0.0f;
        targetListScroll = 0.0f;
        settingsScroll = 0.0f;
        targetSettingsScroll = 0.0f;
        scrollbarAlpha = 0.0f;
        openProgress = 0.0f;
        contentFade = 0.0f;
        navIndicatorX = navX + 2.0f;
        bindingModule = null;
        searchFocused = false;
        searchQuery = "";
        searchFocusProgress = 0.0f;
        detailTabIndex = 0;
        toastText = null;
        closing = false;
        savedOnClose = false;
        draggingScrollbar = false;
        draggingNumber = null;
        lastFrameNanos = System.nanoTime();
        fpsSampleStarted = lastFrameNanos;
        fpsSampleFrames = 0;
        liveFps = 0;
        frameScale = 1.0f;
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        updateFrameScale();
        ScaledResolution sr = new ScaledResolution(mc);
        updateLayout(sr);
        ensureSelectedModule();
        openProgress = animate(openProgress, closing ? 0.0f : 1.0f, closing ? 0.20f : 0.16f);
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
        if (!closing && draggingNumber instanceof Numbers && Mouse.isButtonDown(0)) {
            detailPanel.updateNumberValue((Numbers) draggingNumber, mouseX, draggingNumberX, draggingNumberW);
        } else {
            draggingNumber = null;
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
        contentX = Math.max(10.0f, screenW / 2.0f - totalW / 2.0f);
        detailX = contentX + CARD_W + GAP;
        sideX = detailX + detailW + GAP;
        navX = detailX;
        navY = 12.0f;
        navW = detailW;
        contentY = navY + NAV_H + 12.0f;
        panelH = Math.max(220.0f, screenH - contentY - 48.0f);
    }

    void drawBackdrop(ScaledResolution sr) {
        RenderUtil.drawRect(0, 0, sr.getScaledWidth(), sr.getScaledHeight(), withAlpha(BACKDROP, 94.0f * openProgress));
        RenderUtil.drawGradientRect(0, 0, sr.getScaledWidth(), sr.getScaledHeight(),
                withAlpha(new Color(51, 73, 99, 44).getRGB(), 44.0f * openProgress),
                withAlpha(new Color(6, 8, 10, 92).getRGB(), 92.0f * openProgress));
        RenderUtil.drawGradientRect(0, sr.getScaledHeight() * 0.62f, sr.getScaledWidth(), sr.getScaledHeight(),
                withAlpha(new Color(0, 0, 0, 0).getRGB(), 0.0f),
                withAlpha(new Color(0, 0, 0, 130).getRGB(), 92.0f * openProgress));
    }

    void drawBrand(float introY) {
        float x = contentX + 4.0f;
        float y = navY + 1.0f + introY * 0.35f;
        FontLoaders.F18.drawString("VAPE", x, y, withAlpha(TEXT, 255.0f * openProgress));
        drawSoftRect(x + 42.0f, y + 1.0f, x + 61.0f, y + 12.0f, 4.0f,
                withAlpha(new Color(42, 45, 86, 190).getRGB(), 180.0f * openProgress));
        drawCenteredText("V4", x + 42.0f, y + 2.0f, x + 61.0f, y + 12.0f,
                withAlpha(new Color(154, 148, 255).getRGB(), 230.0f * openProgress));
        drawFont("Material 3 x VapuLite", x, y + 18.0f, withAlpha(MUTED, 190.0f * openProgress));
    }

    void drawNavigation(int mouseX, int mouseY, float introY) {
        float y = navY + introY;
        RenderUtil.drawSoftShadow(navX, y, navX + navW, y + NAV_H, 9.0f,
                withAlpha(new Color(0, 0, 0, 210).getRGB(), 70.0f * openProgress), 7, 5.0f);
        RenderUtil.drawFrostedGlassRect(navX, y, navX + navW, y + NAV_H, 9.0f, 1.0f,
                withAlpha(GLASS_FILL_SOFT, 186.0f * openProgress),
                withAlpha(GLASS_BORDER, 52.0f * openProgress));
        float tabW = navW / GuiTab.values().length;
        float targetX = navX + currentTab.ordinal() * tabW + 2.0f;
        navIndicatorX = animate(navIndicatorX, targetX, 0.18f);
        RenderUtil.drawSoftShadow(navIndicatorX, y + 4.0f, navIndicatorX + tabW - 4.0f, y + NAV_H - 4.0f, 7.0f,
                withAlpha(ACCENT, 85.0f * openProgress), 5, 4.0f);
        RenderUtil.drawRoundedBorderedRect(navIndicatorX, y + 4.0f, navIndicatorX + tabW - 4.0f, y + NAV_H - 4.0f, 7.0f, 0.8f,
                withAlpha(new Color(69, 62, 154, 232).getRGB(), 232.0f * openProgress),
                withAlpha(new Color(142, 133, 255).getRGB(), 80.0f * openProgress));
        for (int i = 0; i < GuiTab.values().length; i++) {
            GuiTab tab = GuiTab.values()[i];
            float x = navX + i * tabW;
            boolean hovered = isHovered(x, y, x + tabW, y + NAV_H, mouseX, mouseY);
            float hover = animateTabMap(tab, hovered && tab != currentTab && !closing ? 1.0f : 0.0f, 0.18f);
            if (hover > 0.01f) {
                drawSoftRect(x + 3.0f, y + 4.0f, x + tabW - 3.0f, y + NAV_H - 4.0f, 7.0f,
                        withAlpha(new Color(37, 43, 54, 190).getRGB(), 190.0f * hover * openProgress));
            }
            int textColor = tab == currentTab ? TEXT : new Color(202, 205, 213).getRGB();
            int color = withAlpha(textColor, 245.0f * openProgress);
            String title = trim(tab.title, FontLoaders.F14, Math.max(18.0f, tabW - 25.0f));
            FontLoaders.I14.drawString(tab.icon, x + 9.0f, y + 10.0f - hover * 0.4f, color);
            drawFont(title, x + 23.0f, y + 10.0f - hover * 0.4f, color);
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
        return liveFps <= 0 ? "--" : String.valueOf(liveFps);
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
        return module.getValues().size() * VALUE_ROW_H + 4.0f;
    }

    String getValueText(Value value) {
        if (value instanceof Option) {
            return Boolean.TRUE.equals(value.getValue()) ? "On" : "Off";
        }
        if (value instanceof Numbers) {
            return formatNumber(((Number) value.getValue()).doubleValue());
        }
        if (value instanceof Mode) {
            return ((Mode) value).getModeAsString();
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
                .setAlpha(alpha * openProgress)
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
        selectedModule = null;
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
        return VALUE_ROW_H;
    }

    void ensureSelectedModule() {
        List<Module> modules = getVisibleModules();
        if (selectedModule != null && modules.contains(selectedModule)) {
            return;
        }
        selectedModule = modules.isEmpty() ? null : modules.get(0);
        settingsScroll = 0.0f;
        targetSettingsScroll = 0.0f;
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
        return cardY + 10.0f;
    }

    float getDetailSwitchX() {
        return detailX + detailW - SWITCH_W - 28.0f;
    }

    float getDetailSwitchY(float panelY) {
        return panelY + 20.0f;
    }

    float getOptionSwitchX(float rowX, float rowW) {
        return rowX + rowW - SWITCH_W - 2.0f;
    }

    float getOptionSwitchY(float rowY) {
        return rowY + 6.0f;
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
            font.drawString(icon, centerX - font.getStringWidth(icon) / 2.0f,
                    centerY - font.getHeight() / 2.0f + 2.0f, color);
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
        selectedModule = null;
        draggingNumber = null;
        listScroll = 0.0f;
        targetListScroll = 0.0f;
        contentFade = 0.0f;
    }

    void startBinding(Module module) {
        bindingModule = module;
        draggingNumber = null;
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
        RenderUtil.drawFrostedGlassRect(x, y, x + boxW, y + boxH, 8.0f, 1.0f,
                withAlpha(GLASS_FILL, 218.0f), withAlpha(ACCENT, 130.0f));
        drawCenteredText("KEYBIND", x, y + 14.0f, x + boxW, y + 25.0f, TEXT);
        drawCenteredText(bindingModule.getName(), x, y + 34.0f, x + boxW, y + 45.0f, withAlpha(TEXT, 220.0f));
        drawCenteredText("Current: " + getKeyName(bindingModule), x, y + 49.0f, x + boxW, y + 60.0f, withAlpha(MUTED, 215.0f));
        drawCenteredText("Press key, DEL clears, ESC cancels", x, y + 66.0f, x + boxW, y + 77.0f, withAlpha(MUTED, 185.0f));
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
        RenderUtil.drawFrostedGlassRect(x, y, x + w, y + 17.0f, 6.0f, 0.8f,
                withAlpha(GLASS_FILL_SOFT, 194.0f * alpha),
                withAlpha(ACCENT, 75.0f * alpha));
        drawCenteredText(toastText, x, y + 4.0f, x + w, y + 14.0f, withAlpha(TEXT, 230.0f * alpha));
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
        fpsSampleFrames++;
        long sampleElapsed = now - fpsSampleStarted;
        if (sampleElapsed >= 250000000L) {
            liveFps = Math.max(1, Math.round(fpsSampleFrames * 1000000000.0f / sampleElapsed));
            fpsSampleFrames = 0;
            fpsSampleStarted = now;
        }
    }

    void startClose() {
        saveConfigOnClose();
        closing = true;
        draggingNumber = null;
    }

    void saveConfigOnClose() {
        if (savedOnClose || Client.instance == null) {
            return;
        }
        savedOnClose = true;
        try {
            Client.SaveConfig();
            addToast("Config saved");
        } catch (IOException ignored) {
            addToast("Config save failed");
        }
    }

    void beginScissor(float x, float y, float w, float h) {
        RenderState.pushScissor(x, y, w, h);
    }

    void endScissor() {
        RenderState.popScissor();
    }

    enum GuiTab {
        COMBAT("Combat", FontLoaders.ICON_BOMB, ModuleType.Combat),
        MOVEMENT("Movement", FontLoaders.ICON_MOVEMENT, ModuleType.Movement),
        VISUAL("Visual", FontLoaders.ICON_EYE, ModuleType.Render),
        UTILITY("Utility", FontLoaders.ICON_SETTINGS, ModuleType.Config),
        WORLD("World", FontLoaders.ICON_INFO, ModuleType.World),
        MISC("Misc", FontLoaders.ICON_LIST, ModuleType.Other),
        PLAYER("Profiles", FontLoaders.ICON_PERSON, ModuleType.Player);

        private final String title;
        private final String icon;
        private final ModuleType[] types;

        GuiTab(String title, String icon, ModuleType... types) {
            this.title = title;
            this.icon = icon;
            this.types = types;
        }

        private boolean contains(ModuleType type) {
            for (ModuleType moduleType : types) {
                if (moduleType == type) {
                    return true;
                }
            }
            return false;
        }
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
