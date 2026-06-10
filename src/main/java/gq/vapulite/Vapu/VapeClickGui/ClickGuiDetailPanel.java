package gq.vapulite.Vapu.VapeClickGui;

import gq.vapulite.Manager.ModuleManager;
import gq.vapulite.Vapu.modules.Module;
import gq.vapulite.Vapu.utils.RenderUtil;
import gq.vapulite.Vapu.value.Mode;
import gq.vapulite.Vapu.value.Numbers;
import gq.vapulite.Vapu.value.Option;
import gq.vapulite.Vapu.value.Value;
import gq.vapulite.font.FontLoaders;
import gq.vapulite.ui.UiPanel;

import java.awt.Color;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class ClickGuiDetailPanel {
    private static final String[] DETAIL_TABS = new String[]{"General", "Targets", "Extra", "Rotation", "Visuals"};
    private static final float DROPDOWN_ROW_H = 18.0f;
    private static final float PALETTE_SATURATION = 0.86f;
    private static final float PALETTE_MIN_BRIGHTNESS = 0.42f;
    private static final float PALETTE_MARKER_RADIUS = 4.8f;
    private final VapeClickGui gui;
    private final UiPanel panel;
    private final Map<Numbers, Float> paletteHueByRed = new HashMap<Numbers, Float>();
    private final Map<Numbers, Integer> paletteColorByRed = new HashMap<Numbers, Integer>();
    private static final Set<Mode> expandedModes = new HashSet<>(); // 所有展开下拉栏的Mode（跨开关保持）
    private static final Map<Mode, Float> dropdownAnim = new HashMap<>(); // 下拉栏展开动画进度 0→1
    private float currentIntroY; // 当前帧的introY，dropdown需要用它对齐detail面板

    boolean hasExpandedMode(Mode value) {
        return expandedModes.contains(value);
    }

    ClickGuiDetailPanel(VapeClickGui gui) {
        this.gui = gui;
        this.panel = new UiPanel().setTheme(gui.uiTheme);
    }

    void render(int mouseX, int mouseY, float introY) {
        this.currentIntroY = introY;
        // 游戏重启后恢复所有展开的mode下拉栏
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
                            }
                        }
                        break;
                    }
                }
            }
        }
        float y = gui.contentY + introY;
        gui.settingsScroll = gui.animate(gui.settingsScroll, gui.targetSettingsScroll, 0.14f);
        panel.setBounds(gui.detailX, y, gui.detailW, gui.panelH)
                .radius(VapeClickGui.PANEL_RADIUS)
                .fill(VapeClickGui.GLASS_FILL)
                .border(VapeClickGui.GLASS_BORDER)
                .shadow(new Color(0, 0, 0, 230).getRGB(), 92.0f, 10, 7.0f)
                .setAlpha(gui.openProgress)
                .render(mouseX, mouseY, 0.0f);

        if (gui.selectedModule == null) {
            gui.drawCenteredText("No modules", gui.detailX, y + gui.panelH / 2.0f - 8.0f,
                    gui.detailX + gui.detailW, y + gui.panelH / 2.0f + 8.0f,
                    gui.withAlpha(VapeClickGui.MUTED, 210.0f * gui.openProgress));
            return;
        }

        float headerX = gui.detailX + 20.0f;
        float headerY = y + 16.0f;
        drawModuleIcon(gui.selectedModule, headerX + 13.0f, headerY + 13.0f);
        FontLoaders.F16.drawString(gui.trim(gui.selectedModule.getName(), FontLoaders.F16, gui.detailW - 116.0f),
                headerX + 38.0f, headerY + 1.0f, gui.withAlpha(VapeClickGui.TEXT, 255.0f * gui.openProgress));
        gui.drawFont(gui.trim(gui.getDescription(gui.selectedModule), FontLoaders.F14, gui.detailW - 150.0f),
                headerX + 38.0f, headerY + 18.0f, gui.withAlpha(VapeClickGui.MUTED, 206.0f * gui.openProgress));
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
        // 处理收起动画：不在expandedModes中的mode动画向0
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
        // 绘制所有有动画进度的下拉栏
        for (Mode mode : dropdownAnim.keySet()) {
            if (gui.selectedModule != null && gui.selectedModule.getValues().contains(mode) && isModeVisible(mode)) {
                float progress = dropdownAnim.get(mode);
                if (progress > 0.01f) {
                    drawModeDropdown(mode, mouseX, mouseY, progress);
                }
            }
        }
    }

    boolean mouseClicked(int mouseX, int mouseY, int mouseButton) {
        if (gui.selectedModule == null) {
            return false;
        }
        // 优先处理展开的下拉栏内的选项点击（仅限当前module的mode）
        if (!expandedModes.isEmpty() && mouseButton == 0) {
            for (Mode mode : expandedModes) {
                if (gui.selectedModule != null && gui.selectedModule.getValues().contains(mode)
                        && isModeVisible(mode)
                        && handleDropdownClick(mode, mouseX, mouseY)) {
                    return true;
                }
            }
        }
        float headerToggleX = gui.getDetailSwitchX();
        float headerToggleY = gui.getDetailSwitchY(gui.contentY);
        if (mouseButton == 0 && gui.isSwitchHit(headerToggleX, headerToggleY, mouseX, mouseY)) {
            gui.selectedModule.setState(!gui.selectedModule.getState());
            gui.clickProgress.put(gui.selectedModule, 1.0f);
            return true;
        }
        if (mouseButton == 0 && handleTabClick(mouseX, mouseY)) {
            return true;
        }
        float x = gui.getDetailValuesX();
        float y = gui.getDetailValuesY(gui.contentY);
        float w = gui.getDetailValuesWidth();
        float h = gui.getDetailValuesHeight();
        if (!VapeClickGui.isHovered(x - 8.0f, y, x + w + 8.0f, y + h, mouseX, mouseY)) {
            return false;
        }
        return handleInlineValueClick(gui.selectedModule, x, y + gui.settingsScroll, w, mouseX, mouseY, mouseButton);
    }

    boolean updateScroll(int mouseX, int mouseY, int wheel) {
        if (wheel == 0 || gui.selectedModule == null || !VapeClickGui.isHovered(gui.getDetailValuesX(), gui.getDetailValuesY(gui.contentY),
                gui.getDetailValuesX() + gui.getDetailValuesWidth(), gui.getDetailValuesY(gui.contentY) + gui.getDetailValuesHeight(),
                mouseX, mouseY)) {
            return false;
        }
        gui.targetSettingsScroll += wheel > 0 ? 28.0f : -28.0f;
        gui.targetSettingsScroll = gui.clamp(gui.targetSettingsScroll,
                -Math.max(0.0f, gui.getSettingsContentHeight(gui.selectedModule) - gui.getDetailValuesHeight()), 0.0f);
        return true;
    }

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
        if (gui.draggingNumberCustomRange && gui.draggingNumberPair != null
                && gui.draggingNumberPair.getValue() instanceof Number) {
            double pair = ((Number) gui.draggingNumberPair.getValue()).doubleValue();
            result = gui.draggingNumberLowerBound ? Math.min(result, pair) : Math.max(result, pair);
        }
        value.setValue(result);
    }

    private float pct(Numbers value, double min, double max) {
        double current = ((Number) value.getValue()).doubleValue();
        return (float) gui.clamp((current - min) / Math.max(0.0001D, max - min), 0.0D, 1.0D);
    }

    void updateColorValue(int mouseX, int mouseY) {
        if (gui.draggingColorRed == null || gui.draggingColorGreen == null || gui.draggingColorBlue == null) {
            return;
        }
        setColorFromPalette(gui.draggingColorRed, gui.draggingColorGreen, gui.draggingColorBlue,
                mouseX, mouseY, gui.draggingColorX, gui.draggingColorY, gui.draggingColorW, gui.draggingColorH);
    }

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
                if (gui.isColorStart(module, i) && mouseButton == 0) {
                    Numbers red = (Numbers) values.get(i);
                    Numbers green = (Numbers) values.get(i + 1);
                    Numbers blue = (Numbers) values.get(i + 2);
                    return handlePaletteClick(module, red, green, blue, x, valueY, width, mouseX, mouseY);
                }
                if (gui.isRangeStart(module, i) && mouseButton == 0) {
                    Numbers min = (Numbers) values.get(i);
                    Numbers max = (Numbers) values.get(i + 1);
                    float barX = gui.getSliderBarX(x, width);
                    float barW = gui.getSliderBarWidth(width);
                    double sliderMin = Math.min(min.getMinimum().doubleValue(), max.getMinimum().doubleValue());
                    double sliderMax = Math.max(min.getMaximum().doubleValue(), max.getMaximum().doubleValue());
                    float minPct = pct(min, sliderMin, sliderMax);
                    float maxPct = pct(max, sliderMin, sliderMax);
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
                if (value instanceof Option && mouseButton == 0) {
                    value.setValue(!Boolean.TRUE.equals(value.getValue()));
                    gui.valueActiveProgress.put(value, 1.0f);
                    return true;
                }
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

    private void drawModuleIcon(Module module, float centerX, float centerY) {
        RenderUtil.drawFrostedGlassRect(centerX - 13.0f, centerY - 13.0f, centerX + 13.0f, centerY + 13.0f,
                8.0f, 0.8f, gui.withAlpha(VapeClickGui.GLASS_FILL_SOFT, 178.0f * gui.openProgress),
                gui.withAlpha(VapeClickGui.ACCENT, 74.0f * gui.openProgress));
        RenderUtil.drawSoftShadow(centerX - 13.0f, centerY - 13.0f, centerX + 13.0f, centerY + 13.0f,
                8.0f, gui.withAlpha(VapeClickGui.ACCENT, 34.0f * gui.openProgress), 4, 2.0f);
        gui.drawCenteredIcon(ClickGuiIcons.forModule(module), FontLoaders.I20, centerX, centerY,
                gui.withAlpha(new Color(226, 232, 248).getRGB(), 236.0f * gui.openProgress));
    }

    private void drawTabs(float panelY) {
        float tabX = gui.detailX + 6.0f;
        float tabY = panelY + 58.0f;
        float tabW = gui.detailW - 12.0f;
        float tabH = 31.0f;
        RenderUtil.drawFrostedGlassRect(tabX, tabY, tabX + tabW, tabY + tabH, 5.0f, 0.8f,
                gui.withAlpha(VapeClickGui.GLASS_FILL_SOFT, 164.0f * gui.openProgress),
                gui.withAlpha(VapeClickGui.GLASS_BORDER, 40.0f * gui.openProgress));
        float each = tabW / DETAIL_TABS.length;
        for (int i = 0; i < DETAIL_TABS.length; i++) {
            float x = tabX + each * i;
            boolean active = i == gui.detailTabIndex;
            gui.drawCenteredText(DETAIL_TABS[i], x, tabY + 10.0f, x + each, tabY + 22.0f,
                    gui.withAlpha(active ? VapeClickGui.TEXT : VapeClickGui.MUTED,
                            (active ? 238.0f : 196.0f) * gui.openProgress));
            if (active) {
                RenderUtil.drawSoftShadow(x + 9.0f, tabY + tabH - 2.0f, x + each - 9.0f, tabY + tabH,
                        2.0f, gui.withAlpha(VapeClickGui.ACCENT, 100.0f * gui.openProgress), 4, 2.0f);
                RenderUtil.drawHorizontalGradientRect(x + 9.0f, tabY + tabH - 1.4f, x + each - 9.0f, tabY + tabH - 0.4f,
                        gui.withAlpha(VapeClickGui.ACCENT, 215.0f * gui.openProgress),
                        gui.withAlpha(new Color(152, 135, 255).getRGB(), 215.0f * gui.openProgress));
            }
        }
    }

    private boolean handleTabClick(int mouseX, int mouseY) {
        float tabX = gui.detailX + 6.0f;
        float tabY = gui.contentY + currentIntroY + 58.0f;
        float tabW = gui.detailW - 12.0f;
        float tabH = 31.0f;
        if (!VapeClickGui.isHovered(tabX, tabY, tabX + tabW, tabY + tabH, mouseX, mouseY)) {
            return false;
        }
        int index = (int) ((mouseX - tabX) / (tabW / DETAIL_TABS.length));
        int next = Math.max(0, Math.min(DETAIL_TABS.length - 1, index));
        if (gui.detailTabIndex != next) {
            gui.detailTabIndex = next;
            gui.settingsScroll = 0.0f;
            gui.targetSettingsScroll = 0.0f;
            gui.draggingNumber = null;
            gui.draggingNumberCustomRange = false;
            gui.draggingNumberPair = null;
            gui.clearDraggingColor();
        }
        return true;
    }

    private boolean handlePaletteClick(Module module, Numbers red, Numbers green, Numbers blue,
                                       float x, float y, float w, int mouseX, int mouseY) {
        float[] bounds = getPaletteBounds(x, y, w);
        if (!VapeClickGui.isHovered(bounds[0], bounds[1], bounds[0] + bounds[2], bounds[1] + bounds[3],
                mouseX, mouseY)) {
            return false;
        }
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

        gui.drawFont("Color", x, y + 8.0f,
                gui.withAlpha(VapeClickGui.TEXT, 245.0f * alpha * gui.openProgress));
        gui.drawFont(rainbow ? "RGB Rainbow" : toHex(color), x, y + 25.0f,
                gui.withAlpha(rainbow ? VapeClickGui.ACCENT : VapeClickGui.MUTED, 205.0f * alpha * gui.openProgress));

        RenderUtil.drawSoftShadow(paletteX, paletteY, paletteX + paletteW, paletteY + paletteH, 5.0f,
                gui.withAlpha(color, (36.0f + active * 64.0f) * alpha * gui.openProgress), 5, 3.0f);
        RenderUtil.drawFrostedGlassRect(paletteX - 1.0f, paletteY - 1.0f, paletteX + paletteW + 1.0f,
                paletteY + paletteH + 1.0f, 6.0f, 0.8f,
                gui.withAlpha(VapeClickGui.GLASS_FILL_SOFT, 120.0f * alpha * gui.openProgress),
                gui.withAlpha(rainbow ? VapeClickGui.ACCENT : VapeClickGui.GLASS_BORDER,
                        (rainbow ? 95.0f : 52.0f) * alpha * gui.openProgress));
        RenderUtil.drawRoundedHueRect(paletteX, paletteY, paletteX + paletteW, paletteY + paletteH,
                5.0f, alpha * gui.openProgress);
        RenderUtil.drawRoundedBorderedRect(paletteX, paletteY, paletteX + paletteW, paletteY + paletteH,
                5.0f, 0.8f, 0x00000000,
                gui.withAlpha(new Color(255, 255, 255).getRGB(), 54.0f * alpha * gui.openProgress));

        float[] marker = getColorMarker(red, green, blue, paletteX, paletteY, paletteW, paletteH);
        RenderUtil.drawCircleOutline(marker[0], marker[1], 4.0f + active, 1.2f,
                gui.withAlpha(new Color(255, 255, 255).getRGB(), 235.0f * alpha * gui.openProgress));
        RenderUtil.drawCircle(marker[0], marker[1], 0, 360, 2.1f,
                gui.withAlpha(color, 240.0f * alpha * gui.openProgress));

        RenderUtil.drawFrostedGlassRect(previewX, y + 9.0f, previewX + preview, y + 34.0f, 7.0f, 0.8f,
                gui.withAlpha(color, 230.0f * alpha * gui.openProgress),
                gui.withAlpha(new Color(255, 255, 255).getRGB(), 70.0f * alpha * gui.openProgress));
        if (rainbow) {
            RenderUtil.drawCircle(previewX + preview - 5.5f, y + 13.5f, 0, 360, 2.6f,
                    gui.withAlpha(VapeClickGui.ACCENT, 230.0f * alpha * gui.openProgress));
        }

        gui.drawFont("Double-click: RGB", paletteX, paletteY + paletteH + 6.0f,
                gui.withAlpha(rainbow ? VapeClickGui.ACCENT : VapeClickGui.FAINT,
                        160.0f * alpha * gui.openProgress));
    }

    private float[] getPaletteBounds(float x, float y, float w) {
        float labelW = gui.getDetailLabelWidth(w);
        float preview = 25.0f;
        float gap = 12.0f;
        float minPaletteW = 76.0f;
        if (w - labelW - preview - gap < minPaletteW) {
            labelW = Math.max(58.0f, w - preview - gap - minPaletteW);
        }
        float paletteX = x + labelW;
        float paletteY = y + 9.0f;
        float paletteW = Math.max(48.0f, w - labelW - preview - gap);
        return new float[]{paletteX, paletteY, paletteW, 25.0f};
    }

    private void setColorFromPalette(Numbers red, Numbers green, Numbers blue,
                                     int mouseX, int mouseY, float x, float y, float w, float h) {
        float hue = gui.clamp((mouseX - x) / Math.max(1.0f, w), 0.0f, 1.0f);
        float vertical = gui.clamp((mouseY - y) / Math.max(1.0f, h), 0.0f, 1.0f);
        float brightness = 1.0f - vertical * (1.0f - PALETTE_MIN_BRIGHTNESS);
        int rgb = Color.HSBtoRGB(hue, PALETTE_SATURATION, brightness);
        setNumber(red, rgb >> 16 & 255);
        setNumber(green, rgb >> 8 & 255);
        setNumber(blue, rgb & 255);
        paletteHueByRed.put(red, hue);
        paletteColorByRed.put(red, rgb & 0xFFFFFF);
    }

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

    private Object findMode(Mode mode, String wanted) {
        Object[] modes = mode.getModes();
        for (Object entry : modes) {
            if (entry instanceof Enum && wanted.equalsIgnoreCase(((Enum) entry).name())) {
                return entry;
            }
        }
        return null;
    }

    private int rgb(Numbers red, Numbers green, Numbers blue) {
        return 0xFF000000 | colorValue(red) << 16 | colorValue(green) << 8 | colorValue(blue);
    }

    private int colorValue(Numbers value) {
        Object raw = value.getValue();
        return Math.max(0, Math.min(255, raw instanceof Number ? ((Number) raw).intValue() : 0));
    }

    private void setNumber(Numbers value, int color) {
        value.setValue((double) Math.max(0, Math.min(255, color)));
    }

    private String toHex(int color) {
        String hex = Integer.toHexString(color & 0xFFFFFF).toUpperCase();
        while (hex.length() < 6) {
            hex = "0" + hex;
        }
        return "#" + hex;
    }

    private void drawDetailValues(float panelY, int mouseX, int mouseY) {
        float x = gui.getDetailValuesX();
        float y = gui.getDetailValuesY(panelY);
        float w = gui.getDetailValuesWidth();
        float h = gui.getDetailValuesHeight();
        Module module = gui.selectedModule;
        float contentHeight = gui.getSettingsContentHeight(module);
        if (contentHeight <= 4.0f) {
            gui.drawFont("No settings in this section", x, y + 8.0f,
                    gui.withAlpha(VapeClickGui.MUTED, 210.0f * gui.openProgress));
            return;
        }
        gui.targetSettingsScroll = gui.clamp(gui.targetSettingsScroll, -Math.max(0.0f, contentHeight - h), 0.0f);
        gui.settingsScroll = gui.clamp(gui.settingsScroll, -Math.max(0.0f, contentHeight - h), 0.0f);

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
                if (valueY + valueH >= y - 2.0f && valueY <= y + h + 2.0f) {
                    float active = gui.animateValueMap(gui.valueActiveProgress, value,
                            gui.draggingNumber == value ? 1.0f : 0.0f, 0.18f);
                    if (active > 0.02f) {
                        gui.drawSoftRect(x - 6.0f, valueY + 1.0f, x + w + 2.0f,
                                valueY + valueH - 2.0f, 6.0f,
                                gui.withAlpha(new Color(36, 41, 55, 160).getRGB(),
                                        120.0f * active * gui.openProgress));
                    }
                    if (gui.isColorStart(module, i)) {
                        drawColorPalette(module, (Numbers) values.get(i), (Numbers) values.get(i + 1),
                                (Numbers) values.get(i + 2), x, valueY, w, rowAlpha, mouseX, mouseY);
                    } else if (gui.isRangeStart(module, i)) {
                        drawRangeNumber((Numbers) values.get(i), (Numbers) values.get(i + 1),
                                x, valueY, w, rowAlpha);
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

    private void drawOption(Option value, float x, float y, float w, float alpha) {
        boolean enabled = Boolean.TRUE.equals(value.getValue());
        gui.drawFont(gui.trim(gui.getDisplayName(value), FontLoaders.F14, w - 68.0f), x, y + 8.0f,
                gui.withAlpha(enabled ? VapeClickGui.TEXT : VapeClickGui.MUTED, 255.0f * alpha * gui.openProgress));
        gui.drawSwitch(gui.getOptionSwitchX(x, w), gui.getOptionSwitchY(y), enabled, alpha, value);
    }

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
        float barY = y + 15.0f;
        float pillW = gui.getDetailValuePillWidth();
        float pillX = x + w - pillW;
        gui.drawFont(gui.trim(gui.getDisplayName(value), FontLoaders.F14, labelW - 8.0f), x, y + 8.0f,
                gui.withAlpha(VapeClickGui.TEXT, 245.0f * alpha * gui.openProgress));
        RenderUtil.drawRoundedRect(barX, barY, barX + barW, barY + 2.0f, 2.0f,
                gui.withAlpha(new Color(61, 67, 82, 180).getRGB(), 178.0f * alpha * gui.openProgress));
        RenderUtil.drawProgressBar(barX, barY, barX + barW, barY + 2.0f, 2.0f, value.animX,
                0x00000000, gui.withAlpha(new Color(132, 117, 255).getRGB(), 230.0f * alpha * gui.openProgress));
        float knob = 3.2f + active * 1.0f;
        RenderUtil.drawSoftShadow(barX + barW * value.animX - knob, barY - 3.0f,
                barX + barW * value.animX + knob, barY + 5.0f, 4.0f,
                gui.withAlpha(new Color(132, 117, 255).getRGB(), 100.0f * alpha * gui.openProgress), 4, 2.0f);
        RenderUtil.drawRoundedRect(barX + barW * value.animX - knob, barY - knob + 1.0f,
                barX + barW * value.animX + knob, barY + knob + 1.0f, knob,
                gui.withAlpha(new Color(145, 128, 255).getRGB(), 255.0f * alpha * gui.openProgress));
        drawValuePill(gui.formatNumber(current), pillX, y + 3.0f, pillW, alpha);
    }

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
        float barY = y + 28.0f;
        float pillW = 76.0f;
        float pillX = x + w - pillW;
        String rangeText = gui.formatNumber(Math.min(first, second)) + " - " + gui.formatNumber(Math.max(first, second));

        gui.drawFont(gui.trim(gui.getRangeDisplayName(minValue), FontLoaders.F14, labelW - 8.0f),
                x, y + 8.0f, gui.withAlpha(VapeClickGui.TEXT, 245.0f * alpha * gui.openProgress));
        drawValuePill(rangeText, pillX, y + 3.0f, pillW, alpha);
        RenderUtil.drawRoundedRect(barX, barY, barX + barW, barY + 2.2f, 2.0f,
                gui.withAlpha(new Color(61, 67, 82, 180).getRGB(), 178.0f * alpha * gui.openProgress));
        RenderUtil.drawRoundedRect(barX + barW * lowPct, barY, barX + barW * highPct, barY + 2.2f, 2.0f,
                gui.withAlpha(new Color(132, 117, 255).getRGB(), 230.0f * alpha * gui.openProgress));
        drawRangeKnob(barX + barW * minValue.animX, barY, 3.1f + activeMin * 1.0f, activeMin, alpha);
        drawRangeKnob(barX + barW * maxValue.animX, barY, 3.1f + activeMax * 1.0f, activeMax, alpha);
        if (active > 0.02f) {
            RenderUtil.drawSoftShadow(barX + barW * lowPct, barY - 2.0f, barX + barW * highPct, barY + 4.0f,
                    3.0f, gui.withAlpha(new Color(132, 117, 255).getRGB(), 62.0f * active * alpha * gui.openProgress),
                    4, 2.0f);
        }
    }

    private void drawRangeKnob(float centerX, float barY, float knob, float active, float alpha) {
        RenderUtil.drawSoftShadow(centerX - knob, barY - 3.0f, centerX + knob, barY + 5.0f, 4.0f,
                gui.withAlpha(new Color(132, 117, 255).getRGB(), 82.0f * (0.35f + active) * alpha * gui.openProgress),
                4, 2.0f);
        RenderUtil.drawRoundedRect(centerX - knob, barY - knob + 1.0f,
                centerX + knob, barY + knob + 1.0f, knob,
                gui.withAlpha(new Color(145, 128, 255).getRGB(), 255.0f * alpha * gui.openProgress));
    }

    private void drawMode(Mode value, float x, float y, float w, float alpha) {
        float labelW = gui.getDetailLabelWidth(w);
        float pillW = Math.min(112.0f, Math.max(72.0f, w - labelW));
        float pillX = x + w - pillW;
        boolean expanded = expandedModes.contains(value);
        gui.drawFont(gui.trim(gui.getDisplayName(value), FontLoaders.F14, labelW - 8.0f), x, y + 8.0f,
                gui.withAlpha(VapeClickGui.TEXT, 245.0f * alpha * gui.openProgress));
        float borderAlpha = expanded ? 110.0f : 48.0f;
        int fillColor = expanded
                ? gui.withAlpha(new Color(35, 38, 62, 200).getRGB(), 200.0f * alpha * gui.openProgress)
                : gui.withAlpha(VapeClickGui.GLASS_FILL_SOFT, 194.0f * alpha * gui.openProgress);
        RenderUtil.drawFrostedGlassRect(pillX, y + 3.0f, pillX + pillW, y + 23.0f, 5.0f, 0.8f,
                fillColor,
                gui.withAlpha(VapeClickGui.GLASS_BORDER, borderAlpha * alpha * gui.openProgress));
        gui.drawFont(gui.trim(gui.formatModeLabel(value.getModeAsString()), FontLoaders.F14, pillW - 28.0f),
                pillX + 12.0f, y + 9.0f,
                gui.withAlpha(expanded ? VapeClickGui.ACCENT : VapeClickGui.TEXT,
                        (expanded ? 240.0f : 230.0f) * alpha * gui.openProgress));
        gui.drawFont(expanded ? "^" : "v", pillX + pillW - 15.0f, y + 8.0f,
                gui.withAlpha(expanded ? VapeClickGui.ACCENT : VapeClickGui.MUTED,
                        (expanded ? 220.0f : 185.0f) * alpha * gui.openProgress));
    }

    private void drawValuePill(String text, float x, float y, float w, float alpha) {
        RenderUtil.drawFrostedGlassRect(x, y, x + w, y + 20.0f, 5.0f, 0.8f,
                gui.withAlpha(VapeClickGui.GLASS_FILL_SOFT, 190.0f * alpha * gui.openProgress),
                gui.withAlpha(VapeClickGui.GLASS_BORDER, 46.0f * alpha * gui.openProgress));
        gui.drawCenteredText(text, x, y + 5.0f, x + w, y + 17.0f,
                gui.withAlpha(VapeClickGui.TEXT, 220.0f * alpha * gui.openProgress));
    }

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
        gui.drawSoftRect(trackX, trackY, trackX + 2.0f, trackY + trackH, 2.0f,
                gui.withAlpha(new Color(255, 255, 255, 26).getRGB(), 26.0f * gui.openProgress));
        gui.drawSoftRect(trackX, thumbY, trackX + 2.0f, thumbY + thumbH, 2.0f,
                gui.withAlpha(VapeClickGui.ACCENT, 150.0f * gui.openProgress));
    }

    // ==================== Mode 下拉栏 ====================

    private float getModeValueY(Mode value) {
        float y = gui.getDetailValuesY(gui.contentY + currentIntroY) + gui.settingsScroll;
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

    private void drawModeDropdown(Mode value, int mouseX, int mouseY, float animProgress) {
        Object[] modes = value.getModes();
        if (modes.length == 0) return;

        float labelW = gui.getDetailLabelWidth(gui.getDetailValuesWidth());
        float pillW = Math.min(112.0f, Math.max(72.0f, gui.getDetailValuesWidth() - labelW));
        float pillX = gui.getDetailValuesX() + gui.getDetailValuesWidth() - pillW;
        float valueY = getModeValueY(value);
        float detailY = gui.getDetailValuesY(gui.contentY + currentIntroY);
        float detailH = gui.getDetailValuesHeight();
        float dropdownY = valueY + 23.0f;
        float fullDropdownH = modes.length * DROPDOWN_ROW_H;

        // pill+下拉栏全部滚出detail区域 → 不渲染
        float pillBottom = valueY + 23.0f;
        float fullBottom = Math.max(pillBottom, dropdownY + fullDropdownH);
        float fullTop = Math.min(valueY, dropdownY);
        if (fullBottom < detailY || fullTop > detailY + detailH) return;

        // 用scissor裁剪：先限制到detail框，再用动画进度限制下拉栏高度
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

            RenderUtil.drawFrostedGlassRect(pillX, dropdownY, pillX + pillW, dropdownY + fullDropdownH,
                    5.0f, 0.9f,
                    gui.withAlpha(new Color(18, 22, 30, 240).getRGB(), 238.0f * gui.openProgress),
                    gui.withAlpha(VapeClickGui.GLASS_BORDER, 62.0f * gui.openProgress));
            RenderUtil.drawSoftShadow(pillX, dropdownY, pillX + pillW, dropdownY + fullDropdownH,
                    5.0f, gui.withAlpha(new Color(0, 0, 0, 200).getRGB(), 82.0f * gui.openProgress), 6, 3.0f);

            for (int i = 0; i < modes.length; i++) {
                float rowY = dropdownY + i * DROPDOWN_ROW_H;
                boolean selected = modes[i] == value.getValue();
                boolean hovered = i == hoveredIndex;

                if (hovered || selected) {
                    gui.drawSoftRect(pillX + 2.0f, rowY + 1.0f, pillX + pillW - 2.0f,
                            rowY + DROPDOWN_ROW_H - 1.0f,
                            4.0f, gui.withAlpha(selected ? new Color(88, 90, 178, 160).getRGB()
                                    : new Color(55, 58, 70, 140).getRGB(),
                            (selected ? 178.0f : 110.0f) * gui.openProgress));
                }
                gui.drawFont(gui.trim(gui.formatModeLabel(modes[i].toString()), FontLoaders.F14, pillW - 20.0f),
                        pillX + 10.0f, rowY + 4.0f,
                        gui.withAlpha(selected ? VapeClickGui.ACCENT : VapeClickGui.TEXT,
                                (selected ? 240.0f : 210.0f) * gui.openProgress));
            }
        } finally {
            gui.endScissor();
        }
    }

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
}
