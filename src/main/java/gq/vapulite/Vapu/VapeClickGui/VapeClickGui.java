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
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.ScaledResolution;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

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
    private static final int BACKDROP = new Color(18, 23, 27, 134).getRGB();
    private static final int TOP_BAR = new Color(13, 14, 14, 230).getRGB();
    private static final int CARD = new Color(10, 11, 12, 235).getRGB();
    private static final int CARD_HOVER = new Color(19, 20, 22, 242).getRGB();
    private static final int CARD_OPEN = new Color(12, 13, 14, 248).getRGB();
    private static final int TEXT = new Color(232, 234, 236).getRGB();
    private static final int MUTED = new Color(152, 154, 158).getRGB();
    private static final int FAINT = new Color(83, 86, 92).getRGB();
    private static final int ACCENT = new Color(112, 193, 220).getRGB();
    private static final int RED = new Color(196, 78, 83).getRGB();

    private static final float NAV_W = 330.0f;
    private static final float NAV_H = 18.0f;
    private static final float CARD_W = 165.0f;
    private static final float CARD_H = 28.0f;
    private static final float GAP = 4.0f;
    private static final float SEARCH_H = 16.0f;
    private static final float CLOSE_END_PROGRESS = 0.22f;
    private static final float CLOSING_TEXT_CUTOFF = 0.36f;

    private GuiTab currentTab = GuiTab.COMBAT;
    private Module selectedModule;
    private Value draggingNumber;
    private Module bindingModule;
    private final Map<Module, Float> expandProgress = new HashMap<Module, Float>();
    private final Map<Module, Float> hoverProgress = new HashMap<Module, Float>();
    private final Map<Module, Float> toggleProgress = new HashMap<Module, Float>();
    private final Map<Value, Float> valueToggleProgress = new HashMap<Value, Float>();
    private float draggingNumberX;
    private float draggingNumberW;
    private float listScroll;
    private float targetListScroll;
    private float scrollbarAlpha;
    private float openProgress;
    private float navIndicatorX;
    private float contentFade;
    private float navX;
    private float navY;
    private float contentX;
    private float contentY;
    private String searchQuery = "";
    private boolean searchFocused;
    private long searchCursorTime;
    private String toastText;
    private long toastStarted;
    private boolean closing;
    private boolean savedOnClose;
    private long lastFrameNanos;
    private float frameScale = 1.0f;

    @Override
    public void initGui() {
        super.initGui();
        ScaledResolution sr = new ScaledResolution(mc);
        navX = sr.getScaledWidth() / 2.0f - NAV_W / 2.0f;
        navY = 5.0f;
        contentX = navX;
        contentY = navY + NAV_H + SEARCH_H + 12.0f;
        listScroll = 0.0f;
        targetListScroll = 0.0f;
        scrollbarAlpha = 0.0f;
        openProgress = 0.0f;
        contentFade = 0.0f;
        navIndicatorX = navX + 2.0f;
        bindingModule = null;
        searchFocused = false;
        searchQuery = "";
        toastText = null;
        closing = false;
        savedOnClose = false;
        lastFrameNanos = System.nanoTime();
        frameScale = 1.0f;
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        updateFrameScale();
        ScaledResolution sr = new ScaledResolution(mc);
        openProgress = animate(openProgress, closing ? 0.0f : 1.0f, closing ? 0.20f : 0.16f);
        contentFade = animate(contentFade, closing ? 0.0f : 1.0f, closing ? 0.18f : 0.14f);
        if (closing && openProgress <= CLOSE_END_PROGRESS) {
            mc.displayGuiScreen(null);
            return;
        }
        drawBackdrop(sr);
        if (!closing) {
            updateScroll(mouseX, mouseY);
        }
        if (!closing && draggingNumber instanceof Numbers && Mouse.isButtonDown(0)) {
            updateNumberValue((Numbers) draggingNumber, mouseX, draggingNumberX, draggingNumberW);
        } else {
            draggingNumber = null;
        }

        float introY = (1.0f - easeOut(openProgress)) * (closing ? 18.0f : -10.0f);
        drawKeyPreview(introY);
        drawNavigation(mouseX, mouseY, introY);
        drawSearchBar(mouseX, mouseY, introY);
        drawModuleCards(mouseX, mouseY, introY);
        super.drawScreen(mouseX, mouseY, partialTicks);
        drawKeybindOverlay(sr);
        drawToast(sr);
    }

    private void drawBackdrop(ScaledResolution sr) {
        RenderUtil.drawRect(0, 0, sr.getScaledWidth(), sr.getScaledHeight(), withAlpha(BACKDROP, 58.0f * openProgress));
        RenderUtil.drawGradientRect(0, 0, sr.getScaledWidth(), sr.getScaledHeight(),
                withAlpha(new Color(74, 88, 105, 24).getRGB(), 24.0f * openProgress),
                withAlpha(new Color(10, 12, 13, 42).getRGB(), 42.0f * openProgress));
    }

    private void drawKeyPreview(float introY) {
        float x = 0.0f;
        float y = introY;
        RenderUtil.drawRect(x, y, x + 55, y + 62, withAlpha(new Color(23, 30, 38, 126).getRGB(), 126.0f * openProgress));
        drawKeyBox("W", x + 19, y + 1, 17, 17);
        drawKeyBox("A", x + 2, y + 19, 17, 17);
        drawKeyBox("S", x + 19, y + 19, 17, 17);
        drawKeyBox("D", x + 36, y + 19, 17, 17);
        RenderUtil.drawRect(x + 19, y + 39, x + 36, y + 40, withAlpha(new Color(126, 135, 143, 130).getRGB(), 130.0f * openProgress));
        drawKeyBox("LMB", x + 2, y + 45, 25, 17);
        drawKeyBox("RMB", x + 28, y + 45, 25, 17);
    }

    private void drawKeyBox(String text, float x, float y, float w, float h) {
        RenderUtil.drawRect(x, y, x + w, y + h, withAlpha(new Color(35, 43, 52, 106).getRGB(), 106.0f * openProgress));
        drawCenteredFont(text, x + w / 2.0f, y + 6.0f, withAlpha(new Color(154, 162, 170, 138).getRGB(), 138.0f * openProgress));
    }

    private void drawNavigation(int mouseX, int mouseY, float introY) {
        float y = navY + introY;
        drawSoftRect(navX, y, navX + NAV_W, y + NAV_H, 8, withAlpha(TOP_BAR, 230.0f * openProgress));
        float tabW = NAV_W / 6.0f;
        float targetX = navX + currentTab.ordinal() * tabW + 2.0f;
        navIndicatorX = animate(navIndicatorX, targetX, 0.18f);
        drawSoftRect(navIndicatorX, y + 2, navIndicatorX + tabW - 4, y + NAV_H - 2, 7,
                withAlpha(new Color(74, 76, 80, 210).getRGB(), 210.0f * openProgress));
        for (int i = 0; i < GuiTab.values().length; i++) {
            GuiTab tab = GuiTab.values()[i];
            float x = navX + i * tabW;
            boolean hovered = isHovered(x, navY, x + tabW, navY + NAV_H, mouseX, mouseY);
            if (tab != currentTab && hovered) {
                drawSoftRect(x + 2, y + 2, x + tabW - 2, y + NAV_H - 2, 7,
                        withAlpha(new Color(43, 45, 48, 190).getRGB(), 190.0f * openProgress));
            }
            drawCenteredFont(tab.title, x + tabW / 2.0f, y + 6.0f,
                    withAlpha(tab == GuiTab.UNLOAD ? RED : TEXT, 255.0f * openProgress));
        }
    }

    private void drawSearchBar(int mouseX, int mouseY, float introY) {
        float x = contentX;
        float y = getSearchY() + introY;
        float w = CARD_W * 2.0f + GAP;
        boolean hovered = isHovered(x, y, x + w, y + SEARCH_H, mouseX, mouseY);
        int border = searchFocused ? withAlpha(ACCENT, 125.0f * openProgress)
                : hovered ? withAlpha(new Color(130, 134, 140).getRGB(), 62.0f * openProgress)
                : withAlpha(new Color(80, 84, 90).getRGB(), 38.0f * openProgress);
        int fill = withAlpha(new Color(13, 15, 17, 202).getRGB(), 202.0f * openProgress);
        RenderUtil.drawRoundedBorderedRect(x, y, x + w, y + SEARCH_H, 6.0f, 0.8f, fill, border);

        String shown = searchQuery.length() == 0 ? "Search modules" : trim(searchQuery, FontLoaders.F14, w - 86.0f);
        int textColor = searchQuery.length() == 0 ? withAlpha(MUTED, 170.0f * openProgress) : withAlpha(TEXT, 230.0f * openProgress);
        drawFont(shown, x + 8.0f, y + 5.0f, textColor);

        if (searchFocused) {
            long elapsed = System.currentTimeMillis() - searchCursorTime;
            if ((elapsed / 360L) % 2L == 0L) {
                float cursorX = x + 8.0f + (searchQuery.length() == 0 ? 0.0f : FontLoaders.F14.getStringWidth(shown) + 2.0f);
                RenderUtil.drawRect(cursorX, y + 4.0f, cursorX + 0.8f, y + SEARCH_H - 4.0f,
                        withAlpha(TEXT, 190.0f * openProgress));
            }
        }

        if (searchQuery.length() > 0) {
            String count = getVisibleModules().size() + " results";
            float countW = FontLoaders.F14.getStringWidth(count);
            drawFont(count, x + w - countW - 19.0f, y + 5.0f, withAlpha(MUTED, 160.0f * openProgress));
            drawCenteredText("x", x + w - 15.0f, y + 3.0f, x + w - 5.0f, y + SEARCH_H - 3.0f,
                    withAlpha(MUTED, 190.0f * openProgress));
        }
    }

    private void drawModuleCards(int mouseX, int mouseY, float introY) {
        listScroll = animate(listScroll, targetListScroll, 0.12f);
        float listHeight = getListHeight();
        float drawContentY = contentY + introY;
        beginScissor(contentX - 2, drawContentY - 2, CARD_W * 2 + GAP + 4, listHeight + 4);
        try {
            float[] columnY = new float[]{drawContentY + listScroll, drawContentY + listScroll};
            List<Module> modules = getVisibleModules();
            for (int i = 0; i < modules.size(); i++) {
                Module module = modules.get(i);
                int col = i % 2;
                float x = contentX + col * (CARD_W + GAP);
                float y = columnY[col];
                float height = getCardHeight(module);
                if (y + height >= drawContentY - 2 && y <= drawContentY + listHeight + 2) {
                    float stagger = Math.min(1.0f, Math.max(0.0f, contentFade - i * 0.035f));
                    float eased = easeSmooth(easeOut(stagger));
                    drawModuleCard(module, x, y + (1.0f - eased) * 8.0f, height, mouseX, mouseY, eased);
                }
                columnY[col] += height + GAP;
            }
        } finally {
            endScissor();
        }
        drawScrollbar(drawContentY, listHeight);
    }

    private void drawModuleCard(Module module, float x, float y, float height, int mouseX, int mouseY, float alpha) {
        boolean opened = selectedModule == module;
        boolean hovered = isHovered(x, y, x + CARD_W, y + Math.min(CARD_H, height), mouseX, mouseY);
        float hover = animateMap(hoverProgress, module, hovered && !closing ? 1.0f : 0.0f, 0.16f);
        float expand = getExpandProgress(module);
        int background = blendColor(CARD, CARD_HOVER, hover);
        background = blendColor(background, CARD_OPEN, expand);
        drawSoftRect(x + 2.1f, y + 2.4f, x + CARD_W + 2.1f, y + height + 2.4f, 7,
                withAlpha(new Color(0, 0, 0, 120).getRGB(), 38.0f * alpha * openProgress));
        drawSoftRect(x + 0.9f, y + 1.1f, x + CARD_W + 0.9f, y + height + 1.1f, 6,
                withAlpha(new Color(0, 0, 0, 130).getRGB(), 62.0f * alpha * openProgress));
        drawSoftRect(x - 0.4f, y - 0.4f, x + CARD_W + 0.4f, y + height + 0.4f, 6,
                withAlpha(new Color(84, 88, 94, 120).getRGB(), 28.0f * alpha * openProgress));
        drawSoftRect(x, y, x + CARD_W, y + height, 6, withAlpha(background, getAlpha(background) * alpha * openProgress));
        if (module.getState()) {
            RenderUtil.drawHorizontalGradientRect(x + 5.0f, y + 1.0f, x + CARD_W - 5.0f, y + 1.8f,
                    withAlpha(ACCENT, 110.0f * alpha * openProgress),
                    withAlpha(ACCENT, 22.0f * alpha * openProgress));
        }
        RenderUtil.drawRect(x + 6, y + 1, x + CARD_W - 6, y + 1.4f,
                withAlpha(new Color(255, 255, 255, 70).getRGB(), 16.0f * alpha * openProgress));

        drawCardHeader(module, x, y, opened, alpha);
        if (expand > 0.03f) {
            drawInlineValues(module, x, y + 30 + (1.0f - expand) * -6.0f, expand * alpha);
        }
    }

    private void drawCardHeader(Module module, float x, float y, boolean opened, float alpha) {
        boolean enabled = module.getState();
        String name = trim(module.getName(), FontLoaders.F14, 58);
        drawFont(name, x + 6, y + 7, withAlpha(enabled ? TEXT : new Color(202, 204, 207).getRGB(), 255.0f * alpha * openProgress));
        drawTag(getKeyName(module), getKeyTagX(module, x, name), y + 7, alpha);

        drawSoftRect(x + 6, y + 18, x + 14, y + 26, 3, withAlpha(new Color(78, 84, 90, 220).getRGB(), 220.0f * alpha * openProgress));
        drawCenteredText(opened ? "-" : "+", x + 6, y + 18, x + 14, y + 26, withAlpha(TEXT, 255.0f * alpha * openProgress));
        drawFont(trim(getDescription(module), FontLoaders.F14, 116), x + 18, y + 21, withAlpha(MUTED, 255.0f * alpha * openProgress));
        drawSwitch(x + CARD_W - 22, y + 6, enabled, alpha, module);
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

    private void drawTag(String text, float x, float y, float alpha) {
        float tagW = Math.max(26.0f, FontLoaders.F14.getStringWidth(text) + 8.0f);
        float tagY = y - 1.0f;
        drawSoftRect(x, tagY, x + tagW, tagY + 10.0f, 3,
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
        int offTrack = new Color(37, 39, 42, 235).getRGB();
        int onTrack = new Color(Client.ThemeR, Client.ThemeG, Client.ThemeB, 210).getRGB();
        int track = blendColor(offTrack, onTrack, progress);
        drawSoftRect(x, y, x + 18, y + 9, 5, withAlpha(track, getAlpha(track) * alpha * openProgress));
        RenderUtil.drawRect(x + 4, y + 1, x + 14, y + 1.35f,
                withAlpha(new Color(255, 255, 255, 60).getRGB(), 18.0f * alpha * openProgress));
        float smoothProgress = easeSmooth(easeOut(progress));
        float knobX = x + 2 + 8.0f * smoothProgress;
        int knob = blendColor(new Color(112, 118, 123).getRGB(), new Color(226, 241, 246).getRGB(), smoothProgress);
        drawSoftRect(knobX, y + 2, knobX + 6, y + 7, 3, withAlpha(knob, 245.0f * alpha * openProgress));
    }

    private void drawOption(Option value, float x, float y, float w, float alpha) {
        boolean enabled = Boolean.TRUE.equals(value.getValue());
        drawFont(trim(value.getName(), FontLoaders.F14, w - 25), x, y + 4,
                withAlpha(enabled ? TEXT : MUTED, 255.0f * alpha * openProgress));
        drawSwitch(x + w - 20, y + 2, enabled, alpha, value);
    }

    private void drawNumber(Numbers value, float x, float y, float w, float alpha) {
        double min = value.getMinimum().doubleValue();
        double max = value.getMaximum().doubleValue();
        double current = ((Number) value.getValue()).doubleValue();
        float barX = x + 76;
        float barW = w - 76;
        float pct = (float) ((current - min) / Math.max(0.0001D, max - min));
        pct = Math.max(0.0f, Math.min(1.0f, pct));
        value.animX = animate(value.animX, pct, 0.18f);
        String valueText = formatNumber(current);
        drawFont(trim(value.getName(), FontLoaders.F14, 54), x, y + 4, withAlpha(TEXT, 255.0f * alpha * openProgress));
        drawFont(valueText, barX + barW - FontLoaders.F14.getStringWidth(valueText), y + 2,
                withAlpha(MUTED, 210.0f * alpha * openProgress));
        drawSoftRect(barX, y + 12, barX + barW, y + 14, 2, withAlpha(new Color(58, 61, 65, 220).getRGB(), 220.0f * alpha * openProgress));
        drawSoftRect(barX, y + 12, barX + barW * value.animX, y + 14, 2, withAlpha(ACCENT, 255.0f * alpha * openProgress));
        drawSoftRect(barX + barW * value.animX - 2.2f, y + 9, barX + barW * value.animX + 2.2f, y + 17, 3, withAlpha(ACCENT, 255.0f * alpha * openProgress));
    }

    private void drawMode(Mode value, float x, float y, float w, float alpha) {
        drawFont(trim(value.getName(), FontLoaders.F14, 58), x, y + 4, withAlpha(TEXT, 255.0f * alpha * openProgress));
        float pillX = x + 76;
        float pillW = w - 76;
        drawSoftRect(pillX, y + 1, pillX + pillW, y + 15, 7, withAlpha(new Color(29, 31, 34, 230).getRGB(), 230.0f * alpha * openProgress));
        String mode = trim(value.getModeAsString(), FontLoaders.F14, pillW - 10);
        drawCenteredFont(mode, pillX + pillW / 2.0f, y + 6, withAlpha(TEXT, 255.0f * alpha * openProgress));
    }

    private void updateScroll(int mouseX, int mouseY) {
        int wheel = Mouse.getDWheel();
        if (wheel == 0 || !isHovered(contentX, contentY, contentX + CARD_W * 2 + GAP, contentY + getListHeight(), mouseX, mouseY)) {
            return;
        }
        targetListScroll += wheel > 0 ? 20 : -20;
        targetListScroll = clamp(targetListScroll, -Math.max(0, getContentHeight() - getListHeight()), 0);
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        if (bindingModule != null) {
            return;
        }
        if (closing || handleSearchClick(mouseX, mouseY, mouseButton) || handleNavClick(mouseX, mouseY)) {
            return;
        }
        if (handleModuleClick(mouseX, mouseY, mouseButton)) {
            return;
        }
        searchFocused = false;
        super.mouseClicked(mouseX, mouseY, mouseButton);
    }

    private boolean handleSearchClick(int mouseX, int mouseY, int mouseButton) {
        float x = contentX;
        float y = getSearchY();
        float w = CARD_W * 2.0f + GAP;
        if (!isHovered(x, y, x + w, y + SEARCH_H, mouseX, mouseY)) {
            return false;
        }
        searchFocused = true;
        searchCursorTime = System.currentTimeMillis();
        if (mouseButton == 0 && searchQuery.length() > 0 && isHovered(x + w - 17.0f, y, x + w, y + SEARCH_H, mouseX, mouseY)) {
            setSearchQuery("");
        }
        return true;
    }

    private boolean handleNavClick(int mouseX, int mouseY) {
        if (!isHovered(navX, navY, navX + NAV_W, navY + NAV_H, mouseX, mouseY)) {
            return false;
        }
        float tabW = NAV_W / 6.0f;
        int index = (int) ((mouseX - navX) / tabW);
        if (index < 0 || index >= GuiTab.values().length) {
            return true;
        }
        GuiTab tab = GuiTab.values()[index];
        if (tab == GuiTab.UNLOAD) {
            startClose();
            return true;
        }
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
        float[] columnY = new float[]{contentY + listScroll, contentY + listScroll};
        List<Module> modules = getVisibleModules();
        for (int i = 0; i < modules.size(); i++) {
            Module module = modules.get(i);
            int col = i % 2;
            float x = contentX + col * (CARD_W + GAP);
            float y = columnY[col];
            float height = getCardHeight(module);
            if (isHovered(x, y, x + CARD_W, y + height, mouseX, mouseY)) {
                if (selectedModule == module && mouseY > y + CARD_H) {
                    return handleInlineValueClick(module, x, y + 30, mouseX, mouseY, mouseButton);
                }
                if (mouseY <= y + CARD_H && (mouseButton == 2 || isKeyTagHovered(module, x, y, mouseX, mouseY))) {
                    startBinding(module);
                    return true;
                }
                if (mouseButton == 1 || isHovered(x + 6, y + 18, x + 14, y + 26, mouseX, mouseY)) {
                    selectedModule = selectedModule == module ? null : module;
                } else if (mouseButton == 0) {
                    module.setState(!module.getState());
                }
                targetListScroll = clamp(targetListScroll, -Math.max(0, getContentHeight() - getListHeight()), 0);
                return true;
            }
            columnY[col] += height + GAP;
        }
        return false;
    }

    private boolean handleInlineValueClick(Module module, float x, float valueY, int mouseX, int mouseY, int mouseButton) {
        for (Value value : module.getValues()) {
            float valueH = getValueHeight(value);
            if (isHovered(x + 6, valueY, x + CARD_W - 6, valueY + valueH, mouseX, mouseY)) {
                if (value instanceof Option && mouseButton == 0) {
                    value.setValue(!Boolean.TRUE.equals(value.getValue()));
                    return true;
                }
                if (value instanceof Mode && mouseButton == 0) {
                    nextMode((Mode) value);
                    return true;
                }
                if (value instanceof Mode && mouseButton == 1) {
                    previousMode((Mode) value);
                    return true;
                }
                if (value instanceof Numbers && mouseButton == 0) {
                    draggingNumber = value;
                    draggingNumberX = x + 82;
                    draggingNumberW = CARD_W - 88;
                    updateNumberValue((Numbers) value, mouseX, draggingNumberX, draggingNumberW);
                    return true;
                }
            }
            valueY += valueH;
        }
        return true;
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
        float expand = getExpandProgress(module);
        if (expand <= 0.01f) {
            return CARD_H;
        }
        float targetHeight = 32.0f;
        if (module.getValues().isEmpty()) {
            targetHeight += 18.0f;
        } else {
            for (Value value : module.getValues()) {
                targetHeight += getValueHeight(value);
            }
            targetHeight += 4.0f;
        }
        return CARD_H + (targetHeight - CARD_H) * easeSmooth(easeOut(expand));
    }

    private float getContentHeight() {
        float[] columnY = new float[]{0.0f, 0.0f};
        List<Module> modules = getVisibleModules();
        for (int i = 0; i < modules.size(); i++) {
            int col = i % 2;
            columnY[col] += getCardHeight(modules.get(i)) + GAP;
        }
        return Math.max(columnY[0], columnY[1]);
    }

    private float getListHeight() {
        ScaledResolution sr = new ScaledResolution(mc);
        return Math.max(160.0f, sr.getScaledHeight() - contentY - 10.0f);
    }

    private float getValueHeight(Value value) {
        return value instanceof Numbers ? 20.0f : 19.0f;
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
        beginAntialiasing();
        try {
            RenderUtil.drawFastRoundedRect(Math.round(x), y, Math.round(x2), y2, radius, color);
        } catch (Throwable ignored) {
            RenderUtil.drawRoundedRect(x, y, x2, y2, radius, color);
        } finally {
            endAntialiasing();
        }
    }

    private void beginAntialiasing() {
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_HINT_BIT | GL11.GL_COLOR_BUFFER_BIT);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glEnable(0x809D);
        GL11.glEnable(GL11.GL_LINE_SMOOTH);
        GL11.glEnable(GL11.GL_POLYGON_SMOOTH);
        GL11.glHint(GL11.GL_LINE_SMOOTH_HINT, GL11.GL_NICEST);
        GL11.glHint(GL11.GL_POLYGON_SMOOTH_HINT, GL11.GL_NICEST);
    }

    private void endAntialiasing() {
        GL11.glPopAttrib();
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
        float contentHeight = getContentHeight();
        boolean visible = contentHeight > listHeight + 1.0f;
        scrollbarAlpha = animate(scrollbarAlpha, visible ? 1.0f : 0.0f, 0.18f);
        if (scrollbarAlpha <= 0.01f) {
            return;
        }
        float trackX = contentX + CARD_W * 2.0f + GAP + 5.0f;
        float maxScroll = Math.max(1.0f, contentHeight - listHeight);
        float thumbH = Math.max(22.0f, listHeight / contentHeight * listHeight);
        float scrollPct = clamp(-listScroll / maxScroll, 0.0f, 1.0f);
        float thumbY = drawContentY + (listHeight - thumbH) * scrollPct;
        drawSoftRect(trackX, drawContentY, trackX + 2.2f, drawContentY + listHeight, 2.0f,
                withAlpha(new Color(255, 255, 255, 32).getRGB(), 32.0f * scrollbarAlpha * openProgress));
        drawSoftRect(trackX, thumbY, trackX + 2.2f, thumbY + thumbH, 2.0f,
                withAlpha(ACCENT, 150.0f * scrollbarAlpha * openProgress));
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
        MOVE("Move", ModuleType.Movement),
        VISUAL("Visual", ModuleType.Render),
        UTILITY("Utility", ModuleType.Player, ModuleType.World, ModuleType.Other),
        PROFILES("Profiles", ModuleType.Config),
        UNLOAD("Unload");

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
}
