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
import gq.vapulite.ui.UiPanel;
import gq.vapulite.ui.UiSelect;
import gq.vapulite.ui.UiSlider;
import gq.vapulite.ui.UiTextField;
import gq.vapulite.ui.UiTheme;
import gq.vapulite.ui.UiToggle;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.ScaledResolution;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import java.awt.Color;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class VapeClickGui extends GuiScreen {
    private static final int BACKDROP = new Color(9, 13, 18, 164).getRGB();
    private static final int TOP_BAR = new Color(12, 15, 20, 232).getRGB();
    private static final int CARD = new Color(17, 21, 27, 222).getRGB();
    private static final int CARD_HOVER = new Color(25, 30, 38, 232).getRGB();
    private static final int CARD_OPEN = new Color(34, 35, 77, 238).getRGB();
    private static final int TEXT = new Color(232, 234, 236).getRGB();
    private static final int MUTED = new Color(152, 154, 158).getRGB();
    private static final int FAINT = new Color(83, 86, 92).getRGB();
    private static final int ACCENT = new Color(112, 193, 220).getRGB();
    private static final int RED = new Color(196, 78, 83).getRGB();

    private static final float NAV_H = 28.0f;
    private static final float CARD_W = 172.0f;
    private static final float CARD_H = 34.0f;
    private static final float GAP = 10.0f;
    private static final float SEARCH_H = 24.0f;
    private static final float PANEL_RADIUS = 8.0f;
    private static final float CARD_RADIUS = 7.0f;
    private static final float DETAIL_MIN_W = 330.0f;
    private static final float DETAIL_MAX_W = 430.0f;
    private static final float SIDE_W = 170.0f;
    private static final float DETAIL_HEADER_H = 76.0f;
    private static final float VALUE_ROW_H = 26.0f;
    private static final float CLOSE_END_PROGRESS = 0.22f;
    private static final float CLOSING_TEXT_CUTOFF = 0.36f;

    private GuiTab currentTab = GuiTab.COMBAT;
    private Module selectedModule;
    private Value draggingNumber;
    private Module bindingModule;
    private final Map<Module, Float> expandProgress = new HashMap<Module, Float>();
    private final Map<Module, Float> hoverProgress = new HashMap<Module, Float>();
    private final Map<Module, Float> clickProgress = new HashMap<Module, Float>();
    private final Map<Module, Float> toggleProgress = new HashMap<Module, Float>();
    private final Map<GuiTab, Float> tabHoverProgress = new HashMap<GuiTab, Float>();
    private final Map<Value, Float> valueToggleProgress = new HashMap<Value, Float>();
    private final Map<Value, Float> valueActiveProgress = new HashMap<Value, Float>();
    private float draggingNumberX;
    private float draggingNumberW;
    private float listScroll;
    private float targetListScroll;
    private float settingsScroll;
    private float targetSettingsScroll;
    private float scrollbarAlpha;
    private boolean draggingScrollbar;
    private float scrollbarDragOffset;
    private float openProgress;
    private float navIndicatorX;
    private float contentFade;
    private float searchFocusProgress;
    private float navX;
    private float navY;
    private float navW;
    private float contentX;
    private float contentY;
    private float detailX;
    private float detailW;
    private float sideX;
    private float sideW;
    private float panelH;
    private boolean sidePanelVisible;
    private String searchQuery = "";
    private boolean searchFocused;
    private long searchCursorTime;
    private String toastText;
    private long toastStarted;
    private boolean closing;
    private boolean savedOnClose;
    private long lastFrameNanos;
    private float frameScale = 1.0f;
    private final UiTheme uiTheme = UiTheme.vape();
    private final UiPanel reusablePanel = new UiPanel().setTheme(uiTheme);
    private final UiToggle reusableToggle = new UiToggle().setTheme(uiTheme);
    private final UiSlider reusableSlider = new UiSlider().setTheme(uiTheme);
    private final UiSelect reusableSelect = new UiSelect().setTheme(uiTheme);
    private final UiTextField searchField = new UiTextField().setTheme(uiTheme).placeholder("Search modules...").maxLength(32);

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
        toastText = null;
        closing = false;
        savedOnClose = false;
        draggingScrollbar = false;
        draggingNumber = null;
        lastFrameNanos = System.nanoTime();
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
        if (!closing) {
            updateScrollbarDrag(mouseY);
            updateScroll(mouseX, mouseY);
        }
        if (!closing && draggingNumber instanceof Numbers && Mouse.isButtonDown(0)) {
            updateNumberValue((Numbers) draggingNumber, mouseX, draggingNumberX, draggingNumberW);
        } else {
            draggingNumber = null;
        }

        float introY = (1.0f - easeOut(openProgress)) * (closing ? 18.0f : -10.0f);
        drawBrand(introY);
        drawNavigation(mouseX, mouseY, introY);
        drawSearchBar(mouseX, mouseY, introY);
        drawModuleCards(mouseX, mouseY, introY);
        drawDetailPanel(mouseX, mouseY, introY);
        drawSidePanel(sr, mouseX, mouseY, introY);
        drawBottomBar(sr);
        super.drawScreen(mouseX, mouseY, partialTicks);
        drawKeybindOverlay(sr);
        drawToast(sr);
    }

    private void updateLayout(ScaledResolution sr) {
        float screenW = sr.getScaledWidth();
        float screenH = sr.getScaledHeight();
        sidePanelVisible = screenW >= 760.0f;
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

    private void drawBackdrop(ScaledResolution sr) {
        RenderUtil.drawRect(0, 0, sr.getScaledWidth(), sr.getScaledHeight(), withAlpha(BACKDROP, 94.0f * openProgress));
        RenderUtil.drawGradientRect(0, 0, sr.getScaledWidth(), sr.getScaledHeight(),
                withAlpha(new Color(51, 73, 99, 44).getRGB(), 44.0f * openProgress),
                withAlpha(new Color(6, 8, 10, 92).getRGB(), 92.0f * openProgress));
        RenderUtil.drawGradientRect(0, sr.getScaledHeight() * 0.62f, sr.getScaledWidth(), sr.getScaledHeight(),
                withAlpha(new Color(0, 0, 0, 0).getRGB(), 0.0f),
                withAlpha(new Color(0, 0, 0, 130).getRGB(), 92.0f * openProgress));
    }

    private void drawBrand(float introY) {
        float x = contentX + 4.0f;
        float y = navY + 1.0f + introY * 0.35f;
        FontLoaders.F18.drawString("VAPE", x, y, withAlpha(TEXT, 255.0f * openProgress));
        drawSoftRect(x + 42.0f, y + 1.0f, x + 61.0f, y + 12.0f, 4.0f,
                withAlpha(new Color(42, 45, 86, 190).getRGB(), 180.0f * openProgress));
        drawCenteredText("V4", x + 42.0f, y + 2.0f, x + 61.0f, y + 12.0f,
                withAlpha(new Color(154, 148, 255).getRGB(), 230.0f * openProgress));
        drawFont("Material 3 x VapuLite", x, y + 18.0f, withAlpha(MUTED, 190.0f * openProgress));
    }

    private void drawNavigation(int mouseX, int mouseY, float introY) {
        float y = navY + introY;
        RenderUtil.drawSoftShadow(navX, y, navX + navW, y + NAV_H, 9.0f,
                withAlpha(new Color(0, 0, 0, 210).getRGB(), 70.0f * openProgress), 7, 5.0f);
        RenderUtil.drawRoundedBorderedRect(navX, y, navX + navW, y + NAV_H, 9.0f, 1.0f,
                withAlpha(TOP_BAR, 230.0f * openProgress),
                withAlpha(new Color(83, 94, 118).getRGB(), 45.0f * openProgress));
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
            drawCenteredFont(tab.title, x + tabW / 2.0f, y + 10.0f - hover * 0.4f,
                    withAlpha(textColor, 245.0f * openProgress));
        }
    }

    private void drawSearchBar(int mouseX, int mouseY, float introY) {
        float x = contentX + 8.0f;
        float y = getSearchY() + introY;
        float w = CARD_W - 16.0f;
        boolean hovered = isHovered(x, y, x + w, y + SEARCH_H, mouseX, mouseY);
        searchFocusProgress = animate(searchFocusProgress, searchFocused ? 1.0f : hovered ? 0.45f : 0.0f, 0.20f);
        if (searchFocusProgress > 0.02f) {
            RenderUtil.drawSoftShadow(x, y, x + w, y + SEARCH_H, 6.0f,
                    withAlpha(ACCENT, 70.0f * searchFocusProgress * openProgress), 5, 3.0f);
        }
        searchField.setBounds(x, y, w, SEARCH_H)
                .text(searchQuery)
                .focused(searchFocused)
                .setAlpha(openProgress)
                .render(mouseX, mouseY, 0.0f);
        searchFocused = searchField.focused();
    }

    private void drawModuleCards(int mouseX, int mouseY, float introY) {
        listScroll = animate(listScroll, targetListScroll, 0.12f);
        float listHeight = getListHeight();
        float panelY = contentY + introY;
        reusablePanel.setBounds(contentX, panelY, CARD_W, panelH)
                .radius(PANEL_RADIUS)
                .fill(new Color(12, 16, 22, 218).getRGB())
                .border(new Color(93, 103, 128, 42).getRGB())
                .shadow(new Color(0, 0, 0, 220).getRGB(), 88.0f, 9, 6.0f)
                .setAlpha(openProgress)
                .render(mouseX, mouseY, 0.0f);

        float drawContentY = getModuleListY() + introY;
        beginScissor(contentX + 5.0f, drawContentY, CARD_W - 10.0f, listHeight);
        try {
            float rowY = drawContentY + listScroll;
            List<Module> modules = getVisibleModules();
            for (int i = 0; i < modules.size(); i++) {
                Module module = modules.get(i);
                if (rowY + CARD_H >= drawContentY - 2 && rowY <= drawContentY + listHeight + 2) {
                    float stagger = Math.min(1.0f, Math.max(0.0f, contentFade - i * 0.035f));
                    float eased = easeSmooth(easeOut(stagger));
                    drawModuleCard(module, contentX + 8.0f, rowY + (1.0f - eased) * 8.0f, CARD_H, mouseX, mouseY, eased);
                }
                rowY += CARD_H + 3.0f;
            }
        } finally {
            endScissor();
        }
        drawModuleCount(panelY);
        drawScrollbar(drawContentY, listHeight);
    }

    private void drawModuleCard(Module module, float x, float y, float height, int mouseX, int mouseY, float alpha) {
        boolean selected = selectedModule == module;
        boolean hovered = isHovered(x, y, x + CARD_W - 16.0f, y + height, mouseX, mouseY);
        float hover = animateMap(hoverProgress, module, hovered && !closing ? 1.0f : 0.0f, 0.16f);
        float click = animateMap(clickProgress, module, 0.0f, 0.22f);
        int background = blendColor(CARD, CARD_HOVER, hover);
        background = blendColor(background, CARD_OPEN, selected ? 1.0f : 0.0f);
        if (selected || hover > 0.04f) {
            RenderUtil.drawSoftShadow(x, y, x + CARD_W - 16.0f, y + height, CARD_RADIUS,
                    withAlpha(selected ? ACCENT : new Color(0, 0, 0, 190).getRGB(),
                            (selected ? 34.0f : 22.0f + hover * 10.0f) * alpha * openProgress), 5, 3.0f);
        }
        if (module.getState() || click > 0.02f) {
            RenderUtil.drawSoftShadow(x, y, x + CARD_W - 16.0f, y + height, CARD_RADIUS,
                    withAlpha(ACCENT, (16.0f + click * 45.0f) * alpha * openProgress), 5, 2.8f);
        }
        RenderUtil.drawRoundedBorderedRect(x, y, x + CARD_W - 16.0f, y + height, CARD_RADIUS, 0.8f,
                withAlpha(background, getAlpha(background) * alpha * openProgress),
                withAlpha(selected ? ACCENT : new Color(76, 84, 104).getRGB(),
                        (selected ? 95.0f : 30.0f + hover * 30.0f) * alpha * openProgress));
        if (module.getState()) {
            RenderUtil.drawHorizontalGradientRect(x + 5.0f, y + 1.0f, x + CARD_W - 22.0f, y + 2.0f,
                    withAlpha(ACCENT, 110.0f * alpha * openProgress),
                    withAlpha(ACCENT, 22.0f * alpha * openProgress));
        }
        drawCardHeader(module, x, y, selected, alpha);
    }

    private void drawCardHeader(Module module, float x, float y, boolean selected, float alpha) {
        boolean enabled = module.getState();
        float glyphX = x + 8.0f;
        float glyphY = y + 8.0f;
        drawSoftRect(glyphX, glyphY, glyphX + 14.0f, glyphY + 14.0f, 5.0f,
                withAlpha(new Color(38, 44, 56, 220).getRGB(), 220.0f * alpha * openProgress));
        drawCenteredText(getCategoryMark(module), glyphX, glyphY + 1.0f, glyphX + 14.0f, glyphY + 13.0f,
                withAlpha(enabled || selected ? ACCENT : MUTED, 230.0f * alpha * openProgress));
        String name = trim(module.getName(), FontLoaders.F14, 78.0f);
        drawFont(name, x + 30.0f, y + 8.0f, withAlpha(enabled ? TEXT : new Color(205, 208, 214).getRGB(), 255.0f * alpha * openProgress));
        drawFont(trim(getDescription(module), FontLoaders.F14, 78.0f), x + 30.0f, y + 20.0f, withAlpha(MUTED, 205.0f * alpha * openProgress));
        drawSwitch(x + CARD_W - 45.0f, y + 10.0f, enabled, alpha, module);
    }

    private void drawInlineValues(Module module, float x, float y, float alpha) {
        if (module.getValues().isEmpty()) {
            drawFont("No settings", x + 6, y + 4, withAlpha(FAINT, 255.0f * alpha * openProgress));
            return;
        }

        int index = 0;
        for (Value value : module.getValues()) {
            float rowAlpha = Math.max(0.0f, Math.min(1.0f, alpha - index * 0.04f));
            float rowY = y + (1.0f - easeSmooth(easeOut(rowAlpha))) * 5.0f;
            if (value instanceof Option) {
                drawOption((Option) value, x + 6, rowY, CARD_W - 12, rowAlpha);
            } else if (value instanceof Numbers) {
                drawNumber((Numbers) value, x + 6, rowY, CARD_W - 12, rowAlpha);
            } else if (value instanceof Mode) {
                drawMode((Mode) value, x + 6, rowY, CARD_W - 12, rowAlpha);
            }
            y += getValueHeight(value);
            index++;
        }
    }

    private void drawDetailPanel(int mouseX, int mouseY, float introY) {
        float y = contentY + introY;
        settingsScroll = animate(settingsScroll, targetSettingsScroll, 0.14f);
        reusablePanel.setBounds(detailX, y, detailW, panelH)
                .radius(PANEL_RADIUS)
                .fill(new Color(13, 17, 23, 224).getRGB())
                .border(new Color(88, 98, 122, 44).getRGB())
                .shadow(new Color(0, 0, 0, 230).getRGB(), 92.0f, 10, 7.0f)
                .setAlpha(openProgress)
                .render(mouseX, mouseY, 0.0f);

        if (selectedModule == null) {
            drawCenteredText("No modules", detailX, y + panelH / 2.0f - 8.0f, detailX + detailW, y + panelH / 2.0f + 8.0f,
                    withAlpha(MUTED, 210.0f * openProgress));
            return;
        }

        float headerX = detailX + 18.0f;
        float headerY = y + 17.0f;
        drawSoftRect(headerX, headerY + 3.0f, headerX + 20.0f, headerY + 23.0f, 7.0f,
                withAlpha(new Color(38, 44, 64, 225).getRGB(), 225.0f * openProgress));
        drawCenteredText(getCategoryMark(selectedModule), headerX, headerY + 6.0f, headerX + 20.0f, headerY + 22.0f,
                withAlpha(ACCENT, 235.0f * openProgress));
        FontLoaders.F16.drawString(trim(selectedModule.getName(), FontLoaders.F16, detailW - 110.0f),
                headerX + 30.0f, headerY + 4.0f, withAlpha(TEXT, 255.0f * openProgress));
        drawFont(trim(getDescription(selectedModule), FontLoaders.F14, detailW - 160.0f),
                headerX + 30.0f, headerY + 21.0f, withAlpha(MUTED, 210.0f * openProgress));
        drawSwitch(detailX + detailW - 58.0f, headerY + 8.0f, selectedModule.getState(), 1.0f, selectedModule);
        drawCenteredText("...", detailX + detailW - 28.0f, headerY + 5.0f, detailX + detailW - 12.0f, headerY + 22.0f,
                withAlpha(MUTED, 210.0f * openProgress));

        RenderUtil.drawLine(detailX + 14.0f, y + DETAIL_HEADER_H, detailX + detailW - 14.0f, y + DETAIL_HEADER_H,
                0.6f, withAlpha(new Color(95, 101, 118).getRGB(), 34.0f * openProgress));
        drawSettingSections(y);
        drawDetailValues(y, mouseX, mouseY);
    }

    private void drawSettingSections(float panelY) {
        String[] sections = new String[]{"General", "Settings", "Bind", "Info"};
        float x = detailX + 12.0f;
        float y = panelY + DETAIL_HEADER_H + 10.0f;
        float w = 82.0f;
        for (int i = 0; i < sections.length; i++) {
            float rowY = y + i * 24.0f;
            boolean active = i == 0;
            if (active) {
                RenderUtil.drawSoftShadow(x, rowY, x + w, rowY + 20.0f, 6.0f,
                        withAlpha(ACCENT, 45.0f * openProgress), 5, 3.0f);
                drawSoftRect(x, rowY, x + w, rowY + 20.0f, 6.0f,
                        withAlpha(new Color(54, 50, 121, 224).getRGB(), 224.0f * openProgress));
            }
            drawFont(sections[i], x + 9.0f, rowY + 7.0f,
                    withAlpha(active ? TEXT : MUTED, (active ? 245.0f : 205.0f) * openProgress));
        }
        RenderUtil.drawLine(detailX + 106.0f, panelY + DETAIL_HEADER_H + 7.0f,
                detailX + 106.0f, panelY + panelH - 12.0f, 0.6f,
                withAlpha(new Color(95, 101, 118).getRGB(), 34.0f * openProgress));
    }

    private void drawDetailValues(float panelY, int mouseX, int mouseY) {
        float x = getDetailValuesX();
        float y = getDetailValuesY(panelY);
        float w = getDetailValuesWidth();
        float h = getDetailValuesHeight();
        Module module = selectedModule;
        if (module.getValues().isEmpty()) {
            drawFont("No settings available", x, y + 8.0f, withAlpha(MUTED, 210.0f * openProgress));
            return;
        }

        float contentHeight = getSettingsContentHeight(module);
        targetSettingsScroll = clamp(targetSettingsScroll, -Math.max(0.0f, contentHeight - h), 0.0f);
        settingsScroll = clamp(settingsScroll, -Math.max(0.0f, contentHeight - h), 0.0f);

        beginScissor(x - 2.0f, y, w + 4.0f, h);
        try {
            float valueY = y + settingsScroll;
            int index = 0;
            for (Value value : module.getValues()) {
                float rowAlpha = Math.max(0.0f, Math.min(1.0f, 1.0f - index * 0.015f));
                if (valueY + VALUE_ROW_H >= y - 2.0f && valueY <= y + h + 2.0f) {
                    float active = animateValueMap(valueActiveProgress, value, draggingNumber == value ? 1.0f : 0.0f, 0.18f);
                    if (active > 0.02f) {
                        drawSoftRect(x - 6.0f, valueY + 1.0f, x + w + 2.0f, valueY + VALUE_ROW_H - 2.0f, 6.0f,
                                withAlpha(new Color(36, 41, 55, 160).getRGB(), 120.0f * active * openProgress));
                    }
                    if (value instanceof Option) {
                        drawOption((Option) value, x, valueY, w, rowAlpha);
                    } else if (value instanceof Numbers) {
                        drawNumber((Numbers) value, x, valueY, w, rowAlpha);
                    } else if (value instanceof Mode) {
                        drawMode((Mode) value, x, valueY, w, rowAlpha);
                    }
                }
                valueY += getValueHeight(value);
                index++;
            }
        } finally {
            endScissor();
        }
        drawSettingsScrollbar(panelY, contentHeight, h);
    }

    private void drawSidePanel(ScaledResolution sr, int mouseX, int mouseY, float introY) {
        if (!sidePanelVisible) {
            return;
        }
        float y = contentY + introY;
        drawUserPanel(y);
        drawStatsPanel(y + 56.0f);
        drawModuleSummary(y + 130.0f);
    }

    private void drawUserPanel(float y) {
        RenderUtil.drawSoftShadow(sideX, navY, sideX + sideW, navY + NAV_H, PANEL_RADIUS,
                withAlpha(new Color(0, 0, 0, 220).getRGB(), 70.0f * openProgress), 8, 5.0f);
        RenderUtil.drawRoundedBorderedRect(sideX, navY, sideX + sideW, navY + NAV_H, PANEL_RADIUS, 1.0f,
                withAlpha(new Color(13, 17, 23, 225).getRGB(), 225.0f * openProgress),
                withAlpha(new Color(88, 98, 122).getRGB(), 42.0f * openProgress));
        drawSoftRect(sideX + 10.0f, navY + 6.0f, sideX + 26.0f, navY + 22.0f, 5.0f,
                withAlpha(new Color(81, 87, 103, 220).getRGB(), 220.0f * openProgress));
        drawCenteredText("V", sideX + 10.0f, navY + 8.0f, sideX + 26.0f, navY + 21.0f,
                withAlpha(TEXT, 235.0f * openProgress));
        drawFont("VapuUser", sideX + 34.0f, navY + 6.0f, withAlpha(TEXT, 240.0f * openProgress));
        drawFont("Premium", sideX + 34.0f, navY + 18.0f, withAlpha(ACCENT, 210.0f * openProgress));
        drawCenteredText("v", sideX + sideW - 18.0f, navY + 7.0f, sideX + sideW - 8.0f, navY + 20.0f,
                withAlpha(MUTED, 190.0f * openProgress));
    }

    private void drawStatsPanel(float y) {
        RenderUtil.drawSoftShadow(sideX, y, sideX + sideW, y + 64.0f, PANEL_RADIUS,
                withAlpha(new Color(0, 0, 0, 220).getRGB(), 70.0f * openProgress), 8, 5.0f);
        RenderUtil.drawRoundedBorderedRect(sideX, y, sideX + sideW, y + 64.0f, PANEL_RADIUS, 1.0f,
                withAlpha(new Color(13, 17, 23, 222).getRGB(), 222.0f * openProgress),
                withAlpha(new Color(88, 98, 122).getRGB(), 38.0f * openProgress));
        drawStat("FPS", String.valueOf(Minecraft.getDebugFPS()), sideX + 12.0f, y + 12.0f, TEXT);
        drawStat("Ping", getPingText(), sideX + 68.0f, y + 12.0f, new Color(118, 213, 144).getRGB());
        drawStat("Modules", getEnabledModules() + "/" + ModuleManager.getModules().size(), sideX + 122.0f, y + 12.0f, ACCENT);
        float graphY = y + 48.0f;
        for (int i = 0; i < 10; i++) {
            float px = sideX + 12.0f + i * 14.0f;
            float spike = (i % 3 == 1 ? 6.0f : i % 4 == 0 ? 3.0f : 1.5f);
            RenderUtil.drawLine(px, graphY, px + 8.0f, graphY - spike, 0.7f, withAlpha(ACCENT, 120.0f * openProgress));
            RenderUtil.drawLine(px + 8.0f, graphY - spike, px + 14.0f, graphY - 1.5f, 0.7f, withAlpha(ACCENT, 90.0f * openProgress));
        }
    }

    private void drawModuleSummary(float y) {
        float h = Math.min(180.0f, panelH - 130.0f);
        RenderUtil.drawSoftShadow(sideX, y, sideX + sideW, y + h, PANEL_RADIUS,
                withAlpha(new Color(0, 0, 0, 230).getRGB(), 78.0f * openProgress), 9, 6.0f);
        RenderUtil.drawRoundedBorderedRect(sideX, y, sideX + sideW, y + h, PANEL_RADIUS, 1.0f,
                withAlpha(new Color(13, 17, 23, 224).getRGB(), 224.0f * openProgress),
                withAlpha(new Color(88, 98, 122).getRGB(), 38.0f * openProgress));
        if (selectedModule == null) {
            drawCenteredText("Select a module", sideX, y + h / 2.0f - 8.0f, sideX + sideW, y + h / 2.0f + 8.0f,
                    withAlpha(MUTED, 200.0f * openProgress));
            return;
        }
        drawFont(trim(selectedModule.getName(), FontLoaders.F16, sideW - 42.0f), sideX + 16.0f, y + 18.0f,
                withAlpha(TEXT, 245.0f * openProgress));
        drawCenteredText("*", sideX + sideW - 30.0f, y + 14.0f, sideX + sideW - 18.0f, y + 28.0f,
                withAlpha(MUTED, 190.0f * openProgress));
        drawFont(trim(getDescription(selectedModule), FontLoaders.F14, sideW - 32.0f), sideX + 16.0f, y + 42.0f,
                withAlpha(MUTED, 205.0f * openProgress));
        RenderUtil.drawLine(sideX + 16.0f, y + 74.0f, sideX + sideW - 16.0f, y + 74.0f, 0.6f,
                withAlpha(new Color(95, 101, 118).getRGB(), 36.0f * openProgress));
        drawSummaryRows(y + 86.0f);
        RenderUtil.drawLine(sideX + 16.0f, y + h - 42.0f, sideX + sideW - 16.0f, y + h - 42.0f, 0.6f,
                withAlpha(new Color(95, 101, 118).getRGB(), 36.0f * openProgress));
        drawKeyChip(sideX + 16.0f, y + h - 30.0f, sideW - 32.0f, 18.0f, selectedModule);
    }

    private void drawBottomBar(ScaledResolution sr) {
        float y = sr.getScaledHeight() - 34.0f;
        float profileW = 132.0f;
        RenderUtil.drawRoundedBorderedRect(contentX, y, contentX + profileW, y + 25.0f, 8.0f, 1.0f,
                withAlpha(new Color(13, 17, 23, 218).getRGB(), 218.0f * openProgress),
                withAlpha(new Color(88, 98, 122).getRGB(), 35.0f * openProgress));
        drawSoftRect(contentX + 10.0f, y + 5.0f, contentX + 26.0f, y + 21.0f, 5.0f,
                withAlpha(new Color(81, 87, 103, 220).getRGB(), 220.0f * openProgress));
        drawCenteredText("P", contentX + 10.0f, y + 7.0f, contentX + 26.0f, y + 20.0f,
                withAlpha(TEXT, 235.0f * openProgress));
        drawFont("Default", contentX + 34.0f, y + 5.0f, withAlpha(TEXT, 235.0f * openProgress));
        drawFont("Profile 1", contentX + 34.0f, y + 17.0f, withAlpha(MUTED, 190.0f * openProgress));

        float hintW = 96.0f;
        float hintX = sr.getScaledWidth() - hintW - 16.0f;
        RenderUtil.drawRoundedBorderedRect(hintX, y, hintX + hintW, y + 25.0f, 8.0f, 1.0f,
                withAlpha(new Color(13, 17, 23, 218).getRGB(), 218.0f * openProgress),
                withAlpha(new Color(88, 98, 122).getRGB(), 35.0f * openProgress));
        drawCenteredText("Right Shift", hintX + 8.0f, y + 7.0f, hintX + 64.0f, y + 19.0f,
                withAlpha(TEXT, 220.0f * openProgress));
        drawSoftRect(hintX + 66.0f, y + 5.0f, hintX + hintW - 7.0f, y + 20.0f, 6.0f,
                withAlpha(new Color(69, 62, 154, 232).getRGB(), 232.0f * openProgress));
        drawCenteredText("GUI", hintX + 66.0f, y + 8.0f, hintX + hintW - 7.0f, y + 19.0f,
                withAlpha(TEXT, 245.0f * openProgress));
    }

    private void drawModuleCount(float panelY) {
        String count = getEnabledModules() + " enabled / " + getVisibleModules().size() + " modules";
        drawFont(count, contentX + 12.0f, panelY + panelH - 17.0f, withAlpha(MUTED, 190.0f * openProgress));
    }

    private void drawSettingsScrollbar(float panelY, float contentHeight, float viewHeight) {
        if (contentHeight <= viewHeight + 1.0f) {
            return;
        }
        float trackX = detailX + detailW - 10.0f;
        float trackY = getDetailValuesY(panelY) + 2.0f;
        float trackH = viewHeight - 4.0f;
        float thumbH = Math.max(22.0f, viewHeight / Math.max(1.0f, contentHeight) * trackH);
        float maxScroll = Math.max(1.0f, contentHeight - viewHeight);
        float pct = clamp(-settingsScroll / maxScroll, 0.0f, 1.0f);
        float thumbY = trackY + (trackH - thumbH) * pct;
        drawSoftRect(trackX, trackY, trackX + 2.0f, trackY + trackH, 2.0f,
                withAlpha(new Color(255, 255, 255, 26).getRGB(), 26.0f * openProgress));
        drawSoftRect(trackX, thumbY, trackX + 2.0f, thumbY + thumbH, 2.0f,
                withAlpha(ACCENT, 150.0f * openProgress));
    }

    private void drawStat(String label, String value, float x, float y, int valueColor) {
        drawFont(label, x, y, withAlpha(MUTED, 175.0f * openProgress));
        drawFont(value, x, y + 15.0f, withAlpha(valueColor, 235.0f * openProgress));
    }

    private void drawSummaryRows(float y) {
        List<Value> values = selectedModule.getValues();
        if (values.isEmpty()) {
            drawFont("Settings", sideX + 16.0f, y, withAlpha(MUTED, 185.0f * openProgress));
            drawFont("None", sideX + sideW - 48.0f, y, withAlpha(TEXT, 215.0f * openProgress));
            return;
        }
        int shown = Math.min(4, values.size());
        for (int i = 0; i < shown; i++) {
            Value value = values.get(i);
            float rowY = y + i * 17.0f;
            drawFont(trim(value.getName(), FontLoaders.F14, 78.0f), sideX + 16.0f, rowY,
                    withAlpha(MUTED, 195.0f * openProgress));
            String text = getValueText(value);
            drawFont(trim(text, FontLoaders.F14, 54.0f), sideX + sideW - 16.0f - FontLoaders.F14.getStringWidth(trim(text, FontLoaders.F14, 54.0f)), rowY,
                    withAlpha(TEXT, 220.0f * openProgress));
        }
    }

    private void drawKeyChip(float x, float y, float w, float h, Module module) {
        drawSoftRect(x, y, x + w, y + h, 6.0f, withAlpha(new Color(18, 22, 29, 230).getRGB(), 230.0f * openProgress));
        drawCenteredText(getKeyName(module), x, y + 4.0f, x + w, y + h - 3.0f, withAlpha(TEXT, 225.0f * openProgress));
    }

    private String getCategoryMark(Module module) {
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

    private String getPingText() {
        try {
            if (mc.thePlayer != null && mc.getNetHandler() != null && mc.getNetHandler().getPlayerInfo(mc.thePlayer.getUniqueID()) != null) {
                return mc.getNetHandler().getPlayerInfo(mc.thePlayer.getUniqueID()).getResponseTime() + " ms";
            }
        } catch (Throwable ignored) {
        }
        return "-- ms";
    }

    private int getEnabledModules() {
        int enabled = 0;
        for (Module module : ModuleManager.getModules()) {
            if (module.getState()) {
                enabled++;
            }
        }
        return enabled;
    }

    private float getDetailValuesX() {
        return detailX + 122.0f;
    }

    private float getDetailValuesY(float panelY) {
        return panelY + DETAIL_HEADER_H + 12.0f;
    }

    private float getDetailValuesWidth() {
        return detailW - 144.0f;
    }

    private float getDetailValuesHeight() {
        return panelH - DETAIL_HEADER_H - 24.0f;
    }

    private float getSettingsContentHeight(Module module) {
        if (module == null || module.getValues().isEmpty()) {
            return 0.0f;
        }
        return module.getValues().size() * VALUE_ROW_H + 4.0f;
    }

    private String getValueText(Value value) {
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

    private void drawTag(String text, float x, float y, float alpha) {
        float tagW = Math.max(26.0f, FontLoaders.F14.getStringWidth(text) + 8.0f);
        float tagY = y - 1.0f;
        drawSoftRect(x, tagY, x + tagW, tagY + 10.0f, 5,
                withAlpha(new Color(45, 48, 52, 210).getRGB(), 210.0f * alpha * openProgress));
        drawCenteredText(text, x, tagY, x + tagW, tagY + 10.0f,
                withAlpha(new Color(176, 179, 184).getRGB(), 255.0f * alpha * openProgress));
    }

    private void drawSwitch(float x, float y, boolean enabled, float alpha, Object owner) {
        float progress;
        if (owner instanceof Module) {
            progress = animateMap(toggleProgress, (Module) owner, enabled ? 1.0f : 0.0f, 0.20f);
        } else if (owner instanceof Value) {
            progress = animateValueMap(valueToggleProgress, (Value) owner, enabled ? 1.0f : 0.0f, 0.20f);
        } else {
            progress = enabled ? 1.0f : 0.0f;
        }
        reusableToggle.setBounds(x, y, 18.0f, 9.0f)
                .enabled(enabled)
                .progress(easeSmooth(easeOut(progress)))
                .setAlpha(alpha * openProgress)
                .render(0, 0, 0.0f);
    }

    private void drawOption(Option value, float x, float y, float w, float alpha) {
        boolean enabled = Boolean.TRUE.equals(value.getValue());
        drawFont(trim(value.getName(), FontLoaders.F14, w - 36.0f), x, y + 8.0f,
                withAlpha(enabled ? TEXT : MUTED, 255.0f * alpha * openProgress));
        drawSwitch(x + w - 22.0f, y + 7.0f, enabled, alpha, value);
    }

    private void drawNumber(Numbers value, float x, float y, float w, float alpha) {
        double min = value.getMinimum().doubleValue();
        double max = value.getMaximum().doubleValue();
        double current = ((Number) value.getValue()).doubleValue();
        float pct = (float) ((current - min) / Math.max(0.0001D, max - min));
        pct = Math.max(0.0f, Math.min(1.0f, pct));
        value.animX = animate(value.animX, pct, 0.18f);
        float active = animateValueMap(valueActiveProgress, value, draggingNumber == value ? 1.0f : 0.0f, 0.24f);
        reusableSlider.setBounds(x, y, w, VALUE_ROW_H)
                .data(value.getName(), formatNumber(current), value.animX, active)
                .setAlpha(alpha * openProgress)
                .render(0, 0, 0.0f);
    }

    private void drawMode(Mode value, float x, float y, float w, float alpha) {
        reusableSelect.setBounds(x, y, w, VALUE_ROW_H)
                .data(value.getName(), value.getModeAsString())
                .setAlpha(alpha * openProgress)
                .render(0, 0, 0.0f);
    }

    private void updateScroll(int mouseX, int mouseY) {
        int wheel = Mouse.getDWheel();
        if (draggingScrollbar || wheel == 0) {
            return;
        }
        if (selectedModule != null && isHovered(getDetailValuesX(), getDetailValuesY(contentY),
                getDetailValuesX() + getDetailValuesWidth(), getDetailValuesY(contentY) + getDetailValuesHeight(), mouseX, mouseY)) {
            targetSettingsScroll += wheel > 0 ? 28.0f : -28.0f;
            targetSettingsScroll = clamp(targetSettingsScroll,
                    -Math.max(0.0f, getSettingsContentHeight(selectedModule) - getDetailValuesHeight()), 0.0f);
            return;
        }
        if (!isHovered(contentX, getModuleListY(), contentX + CARD_W, getModuleListY() + getListHeight(), mouseX, mouseY)) {
            return;
        }
        targetListScroll += wheel > 0 ? 34.0f : -34.0f;
        targetListScroll = clamp(targetListScroll, -Math.max(0.0f, getContentHeight() - getListHeight()), 0.0f);
    }

    private boolean handleScrollbarClick(int mouseX, int mouseY, int mouseButton) {
        if (mouseButton != 0) {
            return false;
        }
        ScrollbarMetrics metrics = getScrollbarMetrics(getModuleListY(), getListHeight());
        if (!metrics.visible) {
            return false;
        }
        boolean onTrack = isHovered(metrics.trackX - 4.0f, metrics.trackY, metrics.trackX + 6.0f,
                metrics.trackY + metrics.trackHeight, mouseX, mouseY);
        if (!onTrack) {
            return false;
        }
        boolean onThumb = isHovered(metrics.trackX - 4.0f, metrics.thumbY, metrics.trackX + 6.0f,
                metrics.thumbY + metrics.thumbHeight, mouseX, mouseY);
        draggingScrollbar = true;
        scrollbarDragOffset = onThumb ? mouseY - metrics.thumbY : metrics.thumbHeight / 2.0f;
        updateScrollbarDrag(mouseY);
        return true;
    }

    private void updateScrollbarDrag(int mouseY) {
        if (!draggingScrollbar) {
            return;
        }
        if (!Mouse.isButtonDown(0)) {
            draggingScrollbar = false;
            return;
        }
        ScrollbarMetrics metrics = getScrollbarMetrics(getModuleListY(), getListHeight());
        if (!metrics.visible) {
            draggingScrollbar = false;
            return;
        }
        float thumbTop = clamp(mouseY - scrollbarDragOffset, metrics.trackY, metrics.trackY + metrics.trackHeight - metrics.thumbHeight);
        float pct = (thumbTop - metrics.trackY) / Math.max(1.0f, metrics.trackHeight - metrics.thumbHeight);
        targetListScroll = -metrics.maxScroll * pct;
        listScroll = animate(listScroll, targetListScroll, 0.38f);
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        if (bindingModule != null) {
            return;
        }
        if (closing || handleSearchClick(mouseX, mouseY, mouseButton) || handleNavClick(mouseX, mouseY)
                || handleScrollbarClick(mouseX, mouseY, mouseButton) || handleDetailClick(mouseX, mouseY, mouseButton)) {
            return;
        }
        if (handleModuleClick(mouseX, mouseY, mouseButton)) {
            return;
        }
        searchFocused = false;
        super.mouseClicked(mouseX, mouseY, mouseButton);
    }

    private boolean handleSearchClick(int mouseX, int mouseY, int mouseButton) {
        float x = contentX + 8.0f;
        float y = getSearchY();
        float w = CARD_W - 16.0f;
        searchField.setBounds(x, y, w, SEARCH_H).text(searchQuery).focused(searchFocused);
        if (!searchField.mouseClicked(mouseX, mouseY, mouseButton)) {
            return false;
        }
        String newQuery = searchField.text();
        searchFocused = searchField.focused();
        searchCursorTime = System.currentTimeMillis();
        if (!newQuery.equals(searchQuery)) {
            setSearchQuery(newQuery);
        }
        return true;
    }

    private boolean handleNavClick(int mouseX, int mouseY) {
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

    private boolean handleModuleClick(int mouseX, int mouseY, int mouseButton) {
        float rowY = getModuleListY() + listScroll;
        List<Module> modules = getVisibleModules();
        for (int i = 0; i < modules.size(); i++) {
            Module module = modules.get(i);
            float x = contentX + 8.0f;
            if (isHovered(x, rowY, x + CARD_W - 16.0f, rowY + CARD_H, mouseX, mouseY)) {
                if (mouseButton == 2) {
                    startBinding(module);
                    return true;
                }
                if (mouseButton == 0 && isHovered(x + CARD_W - 45.0f, rowY + 7.0f, x + CARD_W - 22.0f, rowY + 25.0f, mouseX, mouseY)) {
                    module.setState(!module.getState());
                    clickProgress.put(module, 1.0f);
                    return true;
                }
                if (mouseButton == 1) {
                    module.setState(!module.getState());
                    clickProgress.put(module, 1.0f);
                    return true;
                }
                if (mouseButton == 0) {
                    selectedModule = module;
                    settingsScroll = 0.0f;
                    targetSettingsScroll = 0.0f;
                    clickProgress.put(module, 1.0f);
                }
                targetListScroll = clamp(targetListScroll, -Math.max(0.0f, getContentHeight() - getListHeight()), 0.0f);
                return true;
            }
            rowY += CARD_H + 3.0f;
        }
        return false;
    }

    private boolean handleDetailClick(int mouseX, int mouseY, int mouseButton) {
        if (selectedModule == null) {
            return false;
        }
        float x = getDetailValuesX();
        float y = getDetailValuesY(contentY);
        float w = getDetailValuesWidth();
        float h = getDetailValuesHeight();
        if (!isHovered(x - 8.0f, y, x + w + 8.0f, y + h, mouseX, mouseY)) {
            return false;
        }
        return handleInlineValueClick(selectedModule, x, y + settingsScroll, w, mouseX, mouseY, mouseButton);
    }

    private boolean handleInlineValueClick(Module module, float x, float valueY, float width, int mouseX, int mouseY, int mouseButton) {
        for (Value value : module.getValues()) {
            float valueH = getValueHeight(value);
            if (isHovered(x, valueY, x + width, valueY + valueH, mouseX, mouseY)) {
                if (value instanceof Option && mouseButton == 0) {
                    value.setValue(!Boolean.TRUE.equals(value.getValue()));
                    valueActiveProgress.put(value, 1.0f);
                    return true;
                }
                if (value instanceof Mode && mouseButton == 0) {
                    nextMode((Mode) value);
                    valueActiveProgress.put(value, 1.0f);
                    return true;
                }
                if (value instanceof Mode && mouseButton == 1) {
                    previousMode((Mode) value);
                    valueActiveProgress.put(value, 1.0f);
                    return true;
                }
                if (value instanceof Numbers && mouseButton == 0) {
                    draggingNumber = value;
                    draggingNumberX = getSliderBarX(x, width);
                    draggingNumberW = getSliderBarWidth(width);
                    updateNumberValue((Numbers) value, mouseX, draggingNumberX, draggingNumberW);
                    return true;
                }
            }
            valueY += valueH;
        }
        return true;
    }

    @Override
    protected void mouseReleased(int mouseX, int mouseY, int state) {
        draggingNumber = null;
        draggingScrollbar = false;
        super.mouseReleased(mouseX, mouseY, state);
    }

    private void nextMode(Mode value) {
        int index = Arrays.binarySearch(value.getModes(), value.getValue());
        if (index < 0 || index + 1 >= value.getModes().length) {
            value.setValue(value.getModes()[0]);
        } else {
            value.setValue(value.getModes()[index + 1]);
        }
    }

    private void previousMode(Mode value) {
        int index = Arrays.binarySearch(value.getModes(), value.getValue());
        if (index <= 0) {
            value.setValue(value.getModes()[value.getModes().length - 1]);
        } else {
            value.setValue(value.getModes()[index - 1]);
        }
    }

    private void updateNumberValue(Numbers value, int mouseX, float x, float w) {
        double min = value.getMinimum().doubleValue();
        double max = value.getMaximum().doubleValue();
        double inc = value.getIncrement().doubleValue();
        if (inc <= 0.0D) {
            inc = 0.1D;
        }
        double pct = clamp((mouseX - x) / w, 0.0D, 1.0D);
        double result = min + (max - min) * pct;
        result = Math.round(result / inc) * inc;
        result = Math.max(min, Math.min(max, result));
        value.setValue(result);
    }

    private List<Module> getVisibleModules() {
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

    private boolean matchesSearch(Module module, String query) {
        if (module.getName().toLowerCase(Locale.ROOT).contains(query)) {
            return true;
        }
        if (module.Descript != null && module.Descript.toLowerCase(Locale.ROOT).contains(query)) {
            return true;
        }
        return module.getCategory() != null && module.getCategory().name().toLowerCase(Locale.ROOT).contains(query);
    }

    private float getCardHeight(Module module) {
        return CARD_H;
    }

    private float getContentHeight() {
        List<Module> modules = getVisibleModules();
        if (modules.isEmpty()) {
            return 0.0f;
        }
        return modules.size() * (CARD_H + 3.0f) - 3.0f;
    }

    private float getListHeight() {
        return Math.max(120.0f, panelH - SEARCH_H - 43.0f);
    }

    private float getModuleListY() {
        return contentY + SEARCH_H + 12.0f;
    }

    private float getValueHeight(Value value) {
        return VALUE_ROW_H;
    }

    private void ensureSelectedModule() {
        List<Module> modules = getVisibleModules();
        if (selectedModule != null && modules.contains(selectedModule)) {
            return;
        }
        selectedModule = modules.isEmpty() ? null : modules.get(0);
        settingsScroll = 0.0f;
        targetSettingsScroll = 0.0f;
    }

    private float getSliderBarX(float x, float width) {
        float labelW = Math.min(118.0f, Math.max(72.0f, width * 0.34f));
        return x + labelW;
    }

    private float getSliderBarWidth(float width) {
        float labelW = Math.min(118.0f, Math.max(72.0f, width * 0.34f));
        return Math.max(40.0f, width - labelW - 42.0f - 12.0f);
    }

    private String getDescription(Module module) {
        if (module.Descript == null || module.Descript.trim().length() == 0) {
            return "Configure this module.";
        }
        return module.Descript;
    }

    private String getKeyName(Module module) {
        if (module.getKey() == Keyboard.KEY_NONE) {
            return "NONE";
        }
        String keyName = Keyboard.getKeyName(module.getKey());
        return keyName == null ? "NONE" : keyName;
    }

    private float getKeyTagX(Module module, float cardX, String shownName) {
        float x = cardX + 39.0f + FontLoaders.F14.getStringWidth(shownName);
        float maxX = cardX + CARD_W - 60.0f;
        return Math.min(x, maxX);
    }

    private boolean isKeyTagHovered(Module module, float cardX, float cardY, int mouseX, int mouseY) {
        String name = trim(module.getName(), FontLoaders.F14, 58);
        String keyName = getKeyName(module);
        float tagX = getKeyTagX(module, cardX, name);
        float tagY = cardY + 6.0f;
        float tagW = Math.max(26.0f, FontLoaders.F14.getStringWidth(keyName) + 8.0f);
        return isHovered(tagX, tagY, tagX + tagW, tagY + 11.0f, mouseX, mouseY);
    }

    private String formatNumber(double value) {
        if (Math.abs(value - Math.round(value)) < 0.0001D) {
            return String.valueOf((long) Math.round(value));
        }
        return String.format(Locale.ROOT, "%.2f", value);
    }

    private String trim(String text, CFontRenderer font, float maxWidth) {
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

    private void drawFont(String text, float x, float y, int color) {
        if (shouldDrawText(color)) {
            FontLoaders.F14.drawString(text, x, y, color);
        }
    }

    private void drawCenteredFont(String text, float x, float y, int color) {
        if (shouldDrawText(color)) {
            FontLoaders.F14.drawCenteredString(text, x, y, color);
        }
    }

    private boolean shouldDrawText(int color) {
        if (getAlpha(color) < 18) {
            return false;
        }
        return !closing || openProgress > CLOSING_TEXT_CUTOFF;
    }

    private void drawCenteredText(String text, float x, float y, float x2, float y2, int color) {
        float textX = x + (x2 - x - FontLoaders.F14.getStringWidth(text)) / 2.0f;
        float textY = y + (y2 - y - FontLoaders.F14.getStringHeight(text)) / 2.0f + 0.5f;
        drawFont(text, textX, textY, color);
    }

    private float getExpandProgress(Module module) {
        return animateMap(expandProgress, module, selectedModule == module ? 1.0f : 0.0f, 0.15f);
    }

    private float animateMap(Map<Module, Float> map, Module module, float target, float speed) {
        Float current = map.get(module);
        float value = current == null ? target : current.floatValue();
        value = animate(value, target, speed);
        if (Math.abs(value - target) < 0.003f) {
            value = target;
        }
        map.put(module, value);
        return value;
    }

    private float animateValueMap(Map<Value, Float> map, Value valueKey, float target, float speed) {
        Float current = map.get(valueKey);
        float value = current == null ? target : current.floatValue();
        value = animate(value, target, speed);
        if (Math.abs(value - target) < 0.003f) {
            value = target;
        }
        map.put(valueKey, value);
        return value;
    }

    private float animateTabMap(GuiTab tab, float target, float speed) {
        Float current = tabHoverProgress.get(tab);
        float value = current == null ? target : current.floatValue();
        value = animate(value, target, speed);
        if (Math.abs(value - target) < 0.003f) {
            value = target;
        }
        tabHoverProgress.put(tab, value);
        return value;
    }

    private float animate(float current, float target, float speed) {
        float adjustedSpeed = 1.0f - (float) Math.pow(1.0f - clamp(speed, 0.01f, 1.0f), frameScale);
        float value = current + (target - current) * adjustedSpeed;
        if (Math.abs(value - target) < 0.0015f) {
            return target;
        }
        return value;
    }

    private float easeOut(float value) {
        value = clamp(value, 0.0f, 1.0f);
        return 1.0f - (float) Math.pow(1.0f - value, 4.0D);
    }

    private float easeSmooth(float value) {
        value = clamp(value, 0.0f, 1.0f);
        return value * value * (3.0f - 2.0f * value);
    }

    private int blendColor(int from, int to, float progress) {
        progress = clamp(progress, 0.0f, 1.0f);
        int a = (int) (getAlpha(from) + (getAlpha(to) - getAlpha(from)) * progress);
        int r = (int) (getRed(from) + (getRed(to) - getRed(from)) * progress);
        int g = (int) (getGreen(from) + (getGreen(to) - getGreen(from)) * progress);
        int b = (int) (getBlue(from) + (getBlue(to) - getBlue(from)) * progress);
        return ((a & 255) << 24) | ((r & 255) << 16) | ((g & 255) << 8) | (b & 255);
    }

    private int withAlpha(int color, float alpha) {
        int a = (int) clamp(alpha, 0.0f, 255.0f);
        return (color & 0x00FFFFFF) | (a << 24);
    }

    private void drawSoftRect(float x, float y, float x2, float y2, float radius, int color) {
        if (getAlpha(color) <= 0) {
            return;
        }
        RenderUtil.drawRoundedRect(x, y, x2, y2, radius, color);
    }

    private int getAlpha(int color) {
        return color >>> 24 & 255;
    }

    private int getRed(int color) {
        return color >>> 16 & 255;
    }

    private int getGreen(int color) {
        return color >>> 8 & 255;
    }

    private int getBlue(int color) {
        return color & 255;
    }

    private float getSearchY() {
        return navY + NAV_H + 5.0f;
    }

    private void setSearchQuery(String query) {
        searchQuery = query == null ? "" : query;
        searchCursorTime = System.currentTimeMillis();
        selectedModule = null;
        draggingNumber = null;
        listScroll = 0.0f;
        targetListScroll = 0.0f;
        contentFade = 0.0f;
    }

    private void startBinding(Module module) {
        bindingModule = module;
        draggingNumber = null;
        searchFocused = false;
        addToast("Binding " + module.getName());
    }

    private void finishBinding(int keyCode) {
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

    private void drawScrollbar(float drawContentY, float listHeight) {
        ScrollbarMetrics metrics = getScrollbarMetrics(drawContentY, listHeight);
        scrollbarAlpha = animate(scrollbarAlpha, metrics.visible ? 1.0f : 0.0f, 0.18f);
        if (scrollbarAlpha <= 0.01f) {
            return;
        }
        float dragBoost = draggingScrollbar ? 1.0f : 0.0f;
        drawSoftRect(metrics.trackX, metrics.trackY, metrics.trackX + 2.2f, metrics.trackY + metrics.trackHeight, 2.0f,
                withAlpha(new Color(255, 255, 255, 32).getRGB(), 32.0f * scrollbarAlpha * openProgress));
        RenderUtil.drawSoftShadow(metrics.trackX, metrics.thumbY, metrics.trackX + 2.2f, metrics.thumbY + metrics.thumbHeight,
                2.0f, withAlpha(ACCENT, (35.0f + dragBoost * 60.0f) * scrollbarAlpha * openProgress), 4, 2.0f);
        drawSoftRect(metrics.trackX, metrics.thumbY, metrics.trackX + 2.2f, metrics.thumbY + metrics.thumbHeight, 2.0f,
                withAlpha(ACCENT, (150.0f + dragBoost * 70.0f) * scrollbarAlpha * openProgress));
    }

    private ScrollbarMetrics getScrollbarMetrics(float drawContentY, float listHeight) {
        float contentHeight = getContentHeight();
        boolean visible = contentHeight > listHeight + 1.0f;
        float trackX = contentX + CARD_W * 2.0f + GAP + 5.0f;
        float maxScroll = Math.max(1.0f, contentHeight - listHeight);
        float thumbH = visible ? Math.max(22.0f, listHeight / Math.max(1.0f, contentHeight) * listHeight) : listHeight;
        float scrollPct = clamp(-listScroll / maxScroll, 0.0f, 1.0f);
        float thumbY = drawContentY + (listHeight - thumbH) * scrollPct;
        return new ScrollbarMetrics(visible, trackX, drawContentY, listHeight, thumbY, thumbH, maxScroll);
    }

    private void drawKeybindOverlay(ScaledResolution sr) {
        if (bindingModule == null) {
            return;
        }
        RenderUtil.drawRect(0.0f, 0.0f, sr.getScaledWidth(), sr.getScaledHeight(), withAlpha(new Color(0, 0, 0).getRGB(), 92.0f));
        float boxW = 210.0f;
        float boxH = 84.0f;
        float x = sr.getScaledWidth() / 2.0f - boxW / 2.0f;
        float y = sr.getScaledHeight() / 2.0f - boxH / 2.0f;
        RenderUtil.drawRoundedBorderedRect(x, y, x + boxW, y + boxH, 8.0f, 1.0f,
                new Color(15, 17, 20, 238).getRGB(), withAlpha(ACCENT, 130.0f));
        drawCenteredText("KEYBIND", x, y + 14.0f, x + boxW, y + 25.0f, TEXT);
        drawCenteredText(bindingModule.getName(), x, y + 34.0f, x + boxW, y + 45.0f, withAlpha(TEXT, 220.0f));
        drawCenteredText("Current: " + getKeyName(bindingModule), x, y + 49.0f, x + boxW, y + 60.0f, withAlpha(MUTED, 215.0f));
        drawCenteredText("Press key, DEL clears, ESC cancels", x, y + 66.0f, x + boxW, y + 77.0f, withAlpha(MUTED, 185.0f));
    }

    private void addToast(String message) {
        toastText = message;
        toastStarted = System.currentTimeMillis();
    }

    private void drawToast(ScaledResolution sr) {
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
        RenderUtil.drawRoundedBorderedRect(x, y, x + w, y + 17.0f, 6.0f, 0.8f,
                withAlpha(new Color(13, 15, 17, 220).getRGB(), 220.0f * alpha),
                withAlpha(ACCENT, 75.0f * alpha));
        drawCenteredText(toastText, x, y + 4.0f, x + w, y + 14.0f, withAlpha(TEXT, 230.0f * alpha));
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (bindingModule != null) {
            finishBinding(keyCode);
            return;
        }
        if (searchFocused) {
            if (keyCode == Keyboard.KEY_ESCAPE) {
                if (searchQuery.length() > 0) {
                    setSearchQuery("");
                } else {
                    searchFocused = false;
                }
                return;
            }
            if (keyCode == Keyboard.KEY_BACK) {
                if (searchQuery.length() > 0) {
                    setSearchQuery(searchQuery.substring(0, searchQuery.length() - 1));
                }
                return;
            }
            if (keyCode == Keyboard.KEY_DELETE) {
                setSearchQuery("");
                return;
            }
            if (typedChar >= 32 && typedChar != 127 && searchQuery.length() < 32) {
                setSearchQuery(searchQuery + typedChar);
                return;
            }
        }
        if (keyCode == Keyboard.KEY_F && (Keyboard.isKeyDown(Keyboard.KEY_LCONTROL) || Keyboard.isKeyDown(Keyboard.KEY_RCONTROL))) {
            searchFocused = true;
            searchCursorTime = System.currentTimeMillis();
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

    private static boolean isHovered(float x, float y, float x2, float y2, int mouseX, int mouseY) {
        return mouseX >= x && mouseX <= x2 && mouseY >= y && mouseY <= y2;
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private void updateFrameScale() {
        long now = System.nanoTime();
        long elapsed = now - lastFrameNanos;
        lastFrameNanos = now;
        if (elapsed <= 0L) {
            frameScale = 1.0f;
            return;
        }
        frameScale = clamp(elapsed / 16666666.0f, 0.35f, 2.5f);
    }

    private void startClose() {
        saveConfigOnClose();
        closing = true;
        draggingNumber = null;
    }

    private void saveConfigOnClose() {
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

    private void beginScissor(float x, float y, float w, float h) {
        RenderState.pushScissor(x, y, w, h);
    }

    private void endScissor() {
        RenderState.popScissor();
    }

    private enum GuiTab {
        COMBAT("Combat", ModuleType.Combat),
        MOVEMENT("Movement", ModuleType.Movement),
        VISUAL("Visual", ModuleType.Render),
        UTILITY("Utility", ModuleType.Config),
        PLAYER("Player", ModuleType.Player),
        WORLD("World", ModuleType.World),
        MISC("Misc", ModuleType.Other);

        private final String title;
        private final ModuleType[] types;

        GuiTab(String title, ModuleType... types) {
            this.title = title;
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

    private static class ScrollbarMetrics {
        private final boolean visible;
        private final float trackX;
        private final float trackY;
        private final float trackHeight;
        private final float thumbY;
        private final float thumbHeight;
        private final float maxScroll;

        private ScrollbarMetrics(boolean visible, float trackX, float trackY, float trackHeight,
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
