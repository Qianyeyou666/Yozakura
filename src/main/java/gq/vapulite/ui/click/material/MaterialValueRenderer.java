package gq.vapulite.ui.click.material;

import gq.vapulite.engine.font.FontLoaders;
import gq.vapulite.engine.render.ui.RenderServices;
import gq.vapulite.module.Module;
import gq.vapulite.util.animation.AnimationUtil;
import gq.vapulite.value.Mode;
import gq.vapulite.value.Numbers;
import gq.vapulite.value.Option;
import gq.vapulite.value.Value;
import org.lwjgl.input.Mouse;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 模块卡片内的设置值控件。
 *
 * <p>支持 Option 开关、Numbers 滑块、Mode 下拉、min/max 范围滑块以及
 * Red/Green/Blue 三元颜色控制，保证新 GUI 不是静态展示。</p>
 */
@SuppressWarnings({"rawtypes", "unchecked"})
final class MaterialValueRenderer {
    private final MaterialClickGui gui;
    private final Set<Value> expandedModes = new HashSet<Value>();

    private Numbers draggingNumber;
    private Numbers draggingPair;
    private boolean draggingLowerBound;
    private float draggingX;
    private float draggingW;
    private double draggingMin;
    private double draggingMax;

    MaterialValueRenderer(MaterialClickGui gui) {
        this.gui = gui;
    }

    float measure(Module module, float width) {
        float total = 0.0f;
        int count = 0;
        List<Value> values = module.getValues();
        for (int i = 0; i < values.size(); i++) {
            Value value = values.get(i);
            if (!isVisible(module, value)) {
                continue;
            }
            if (isColorStart(module, i)) {
                total += 82.0f * gui.layout().scale;
                i += 2;
            } else if (isRangeStart(module, i)) {
                total += 50.0f * gui.layout().scale;
                i += 1;
            } else if (value instanceof Mode) {
                total += modeHeight((Mode) value);
            } else if (value instanceof Numbers) {
                total += 46.0f * gui.layout().scale;
            } else if (value instanceof Option) {
                total += 32.0f * gui.layout().scale;
            } else {
                total += 28.0f * gui.layout().scale;
            }
            count++;
        }
        return count == 0 ? 0.0f : total + Math.max(0, count - 1) * 8.0f * gui.layout().scale;
    }

    void render(Module module, float x, float y, float width, int mouseX, int mouseY) {
        List<Value> values = module.getValues();
        for (int i = 0; i < values.size(); i++) {
            Value value = values.get(i);
            if (!isVisible(module, value)) {
                continue;
            }
            if (isColorStart(module, i)) {
                drawColor(module, i, x, y, width, mouseX, mouseY);
                y += 90.0f * gui.layout().scale;
                i += 2;
            } else if (isRangeStart(module, i)) {
                drawRange((Numbers) values.get(i), (Numbers) values.get(i + 1), x, y, width);
                y += 58.0f * gui.layout().scale;
                i += 1;
            } else if (value instanceof Mode) {
                drawMode((Mode) value, x, y, width, mouseX, mouseY);
                y += modeHeight((Mode) value) + 8.0f * gui.layout().scale;
            } else if (value instanceof Numbers) {
                drawNumber((Numbers) value, x, y, width);
                y += 54.0f * gui.layout().scale;
            } else if (value instanceof Option) {
                drawOption((Option) value, x, y, width, mouseX, mouseY);
                y += 40.0f * gui.layout().scale;
            } else {
                drawTextValue(value, x, y, width);
                y += 36.0f * gui.layout().scale;
            }
        }
    }

    boolean mouseClicked(Module module, float x, float y, float width, int mouseX, int mouseY, int button) {
        if (button != 0) {
            return false;
        }
        List<Value> values = module.getValues();
        for (int i = 0; i < values.size(); i++) {
            Value value = values.get(i);
            if (!isVisible(module, value)) {
                continue;
            }
            if (isColorStart(module, i)) {
                if (MaterialClickLayout.contains(x, y, x + width, y + 82.0f * gui.layout().scale, mouseX, mouseY)) {
                    Numbers red = (Numbers) values.get(i);
                    Numbers green = (Numbers) values.get(i + 1);
                    Numbers blue = (Numbers) values.get(i + 2);
                    Numbers target = colorChannelAt(red, green, blue, y, mouseY);
                    float colorSliderX = x + 18.0f * gui.layout().scale;
                    beginNumberDrag(target, mouseX, colorSliderX, width - 18.0f * gui.layout().scale,
                            target.getMinimum().doubleValue(), target.getMaximum().doubleValue(), null, false);
                    return true;
                }
                y += 90.0f * gui.layout().scale;
                i += 2;
                continue;
            }
            if (isRangeStart(module, i)) {
                Numbers min = (Numbers) values.get(i);
                Numbers max = (Numbers) values.get(i + 1);
                if (MaterialClickLayout.contains(x, y, x + width, y + 50.0f * gui.layout().scale, mouseX, mouseY)) {
                    double rangeMin = Math.min(min.getMinimum().doubleValue(), max.getMinimum().doubleValue());
                    double rangeMax = Math.max(min.getMaximum().doubleValue(), max.getMaximum().doubleValue());
                    float sx = sliderX(x, width);
                    float sw = sliderW(width);
                    float minX = sx + sw * pct(min, rangeMin, rangeMax);
                    float maxX = sx + sw * pct(max, rangeMin, rangeMax);
                    boolean lower = Math.abs(mouseX - minX) <= Math.abs(mouseX - maxX);
                    beginNumberDrag(lower ? min : max, mouseX, sx, sw, rangeMin, rangeMax, lower ? max : min, lower);
                    return true;
                }
                y += 58.0f * gui.layout().scale;
                i += 1;
                continue;
            }
            if (value instanceof Mode) {
                float h = modeHeight((Mode) value);
                if (handleModeClick((Mode) value, x, y, width, mouseX, mouseY)) {
                    return true;
                }
                y += h + 8.0f * gui.layout().scale;
                continue;
            }
            if (value instanceof Numbers) {
                if (MaterialClickLayout.contains(x, y, x + width, y + 46.0f * gui.layout().scale, mouseX, mouseY)) {
                    Numbers number = (Numbers) value;
                    beginNumberDrag(number, mouseX, sliderX(x, width), sliderW(width),
                            number.getMinimum().doubleValue(), number.getMaximum().doubleValue(), null, false);
                    return true;
                }
                y += 54.0f * gui.layout().scale;
                continue;
            }
            if (value instanceof Option) {
                if (MaterialClickLayout.contains(x, y, x + width, y + 32.0f * gui.layout().scale, mouseX, mouseY)) {
                    value.setValue(!Boolean.TRUE.equals(value.getValue()));
                    return true;
                }
                y += 40.0f * gui.layout().scale;
                continue;
            }
            y += 36.0f * gui.layout().scale;
        }
        return false;
    }

    void updateDragging(int mouseX) {
        if (draggingNumber == null) {
            return;
        }
        if (!Mouse.isButtonDown(0)) {
            releaseDrag();
            return;
        }
        updateNumber(draggingNumber, mouseX, draggingX, draggingW, draggingMin, draggingMax, draggingPair, draggingLowerBound);
    }

    void releaseDrag() {
        draggingNumber = null;
        draggingPair = null;
    }

    void closeDropdown() {
        expandedModes.clear();
        releaseDrag();
    }

    private void drawOption(Option value, float x, float y, float width, int mouseX, int mouseY) {
        MaterialClickTheme theme = gui.theme();
        float s = gui.layout().scale;
        boolean active = Boolean.TRUE.equals(value.getValue());
        String key = gui.animationKey(value);
        float activeProgress = gui.easedAnimation("value.option." + key, active ? 1.0f : 0.0f,
                0.26f, active ? 1.0f : 0.0f, AnimationUtil.Ease.OUT_CUBIC);
        float hover = gui.easedAnimation("value.hover." + key,
                MaterialClickLayout.contains(x, y, x + width, y + 32.0f * s, mouseX, mouseY) ? 1.0f : 0.0f,
                0.28f, 0.0f, AnimationUtil.Ease.OUT_CUBIC);
        FontLoaders.C14.drawString(gui.displayName(value), x + 2.0f * hover * s, y + 8.0f * s,
                theme.withAlpha(theme.blend(MaterialClickTheme.MUTED, MaterialClickTheme.TEXT, hover * 0.35f),
                        255.0f * theme.alpha()));
        float swX = x + width - 38.0f * s;
        float swY = y + 7.0f * s;
        int offTrack = theme.softFill(28.0f + 18.0f * hover);
        int onTrack = theme.withAlpha(MaterialClickTheme.PRIMARY, (220.0f + 35.0f * hover) * theme.alpha());
        RenderServices.shapes().rounded(swX, swY, swX + 34.0f * s, swY + 18.0f * s, 9.0f * s,
                theme.blend(offTrack, onTrack, activeProgress));
        float knobX = swX + AnimationUtil.lerp(3.0f, 18.0f, activeProgress) * s;
        int knob = theme.blend(theme.muted(), theme.withAlpha(MaterialClickTheme.ON_PRIMARY, 255.0f * theme.alpha()),
                activeProgress);
        RenderServices.shapes().rounded(knobX, swY + 3.0f * s, knobX + 12.0f * s, swY + 15.0f * s,
                6.0f * s, knob);
    }

    private void drawNumber(Numbers value, float x, float y, float width) {
        MaterialClickTheme theme = gui.theme();
        float s = gui.layout().scale;
        FontLoaders.C14.drawString(gui.displayName(value), x, y, theme.muted());
        FontLoaders.C14.drawString(formatNumber(numberValue(value)), x + width - FontLoaders.C14.getStringWidth(formatNumber(numberValue(value))),
                y, theme.muted());
        String key = gui.animationKey(value);
        float shownPct = gui.animation("value.number." + key, pct(value),
                draggingNumber == value ? 0.62f : 0.26f, pct(value));
        drawSlider(sliderX(x, width), y + 24.0f * s, sliderW(width), shownPct, theme,
                "value.slider." + key, draggingNumber == value);
    }

    private void drawRange(Numbers min, Numbers max, float x, float y, float width) {
        MaterialClickTheme theme = gui.theme();
        float s = gui.layout().scale;
        double rangeMin = Math.min(min.getMinimum().doubleValue(), max.getMinimum().doubleValue());
        double rangeMax = Math.max(min.getMaximum().doubleValue(), max.getMaximum().doubleValue());
        String minKey = gui.animationKey(min);
        String maxKey = gui.animationKey(max);
        float minPct = gui.animation("value.range.min." + minKey, pct(min, rangeMin, rangeMax),
                draggingNumber == min ? 0.62f : 0.26f, pct(min, rangeMin, rangeMax));
        float maxPct = gui.animation("value.range.max." + maxKey, pct(max, rangeMin, rangeMax),
                draggingNumber == max ? 0.62f : 0.26f, pct(max, rangeMin, rangeMax));
        String label = rangeLabel(min);
        String values = formatNumber(numberValue(min)) + " - " + formatNumber(numberValue(max));
        FontLoaders.C14.drawString(label, x, y, theme.muted());
        FontLoaders.C14.drawString(values, x + width - FontLoaders.C14.getStringWidth(values), y, theme.muted());

        float sx = sliderX(x, width);
        float sy = y + 24.0f * s;
        float sw = sliderW(width);
        RenderServices.shapes().rounded(sx, sy, sx + sw, sy + 4.0f * s, 2.0f * s, theme.softFill(24.0f));
        RenderServices.shapes().rounded(sx + sw * minPct, sy, sx + sw * maxPct, sy + 4.0f * s, 2.0f * s,
                theme.withAlpha(MaterialClickTheme.PRIMARY, 210.0f * theme.alpha()));
        drawKnob(sx + sw * minPct, sy + 2.0f * s, theme, "value.slider." + minKey, draggingNumber == min);
        drawKnob(sx + sw * maxPct, sy + 2.0f * s, theme, "value.slider." + maxKey, draggingNumber == max);
    }

    private void drawColor(Module module, int index, float x, float y, float width, int mouseX, int mouseY) {
        List<Value> values = module.getValues();
        Numbers red = (Numbers) values.get(index);
        Numbers green = (Numbers) values.get(index + 1);
        Numbers blue = (Numbers) values.get(index + 2);
        MaterialClickTheme theme = gui.theme();
        float s = gui.layout().scale;
        String label = colorLabel(red);
        int color = 0xFF000000 | (clampColor(numberValue(red)) << 16) | (clampColor(numberValue(green)) << 8) | clampColor(numberValue(blue));

        FontLoaders.C14.drawString(label, x, y, theme.muted());
        RenderServices.shapes().rounded(x + width - 22.0f * s, y - 2.0f * s, x + width, y + 20.0f * s,
                7.0f * s, color);
        drawColorSlider("R", red, x, y + 25.0f * s, width);
        drawColorSlider("G", green, x, y + 45.0f * s, width);
        drawColorSlider("B", blue, x, y + 65.0f * s, width);
    }

    private void drawColorSlider(String label, Numbers value, float x, float y, float width) {
        MaterialClickTheme theme = gui.theme();
        float s = gui.layout().scale;
        FontLoaders.C14.drawString(label, x, y - 5.0f * s, theme.faint());
        String key = gui.animationKey(value);
        float shownPct = gui.animation("value.color." + key, pct(value),
                draggingNumber == value ? 0.62f : 0.26f, pct(value));
        drawSlider(x + 18.0f * s, y, width - 18.0f * s, shownPct, theme,
                "value.slider." + key, draggingNumber == value);
    }

    private void drawMode(Mode mode, float x, float y, float width, int mouseX, int mouseY) {
        MaterialClickTheme theme = gui.theme();
        float s = gui.layout().scale;
        float expand = modeProgress(mode);
        float hover = gui.easedAnimation("value.mode.hover." + gui.animationKey(mode),
                MaterialClickLayout.contains(x, y, x + width, y + 32.0f * s, mouseX, mouseY) ? 1.0f : 0.0f,
                0.28f, 0.0f, AnimationUtil.Ease.OUT_CUBIC);
        FontLoaders.C14.drawString(gui.displayName(mode), x, y + 8.0f * s, theme.muted());

        String current = modeLabel(mode.getModeAsString());
        float pillW = Math.max(68.0f * s, FontLoaders.C14.getStringWidth(current) + 22.0f * s);
        float pillX = x + width - pillW;
        RenderServices.shapes().rounded(pillX, y + 5.0f * s, pillX + pillW, y + 27.0f * s,
                8.0f * s, theme.withAlpha(theme.blend(0xFFFFFFFF, MaterialClickTheme.PRIMARY_CONTAINER, expand),
                        (26.0f + 112.0f * expand + 18.0f * hover) * theme.alpha()));
        FontLoaders.C14.drawCenteredString(current, pillX + pillW / 2.0f, y + 10.0f * s,
                theme.withAlpha(theme.blend(MaterialClickTheme.MUTED, MaterialClickTheme.ON_PRIMARY_CONTAINER, expand),
                        255.0f * theme.alpha()));

        if (expand <= 0.01f) {
            return;
        }
        float optionY = y + (34.0f - 5.0f * (1.0f - expand)) * s;
        Enum[] modes = mode.getModes();
        for (Enum option : modes) {
            boolean active = option.name().equalsIgnoreCase(mode.getModeAsString());
            RenderServices.shapes().rounded(x, optionY, x + width, optionY + 20.0f * s, 7.0f * s,
                    theme.withAlpha(active ? MaterialClickTheme.PRIMARY_CONTAINER : 0xFFFFFFFF,
                            (active ? 120.0f : 16.0f) * theme.alpha() * expand));
            FontLoaders.C14.drawString(modeLabel(option.name()), x + 9.0f * s, optionY + 5.0f * s,
                    active ? theme.withAlpha(MaterialClickTheme.ON_PRIMARY_CONTAINER, 255.0f * theme.alpha() * expand)
                            : theme.withAlpha(MaterialClickTheme.MUTED, 255.0f * theme.alpha() * expand));
            optionY += 23.0f * s;
        }
    }

    private void drawTextValue(Value value, float x, float y, float width) {
        MaterialClickTheme theme = gui.theme();
        String text = String.valueOf(value.getValue());
        FontLoaders.C14.drawString(gui.displayName(value), x, y + 7.0f * gui.layout().scale, theme.muted());
        FontLoaders.C14.drawString(text, x + width - FontLoaders.C14.getStringWidth(text), y + 7.0f * gui.layout().scale, theme.muted());
    }

    private void drawSlider(float x, float y, float width, float pct, MaterialClickTheme theme, String key, boolean active) {
        float s = gui.layout().scale;
        RenderServices.shapes().rounded(x, y, x + width, y + 4.0f * s, 2.0f * s, theme.softFill(24.0f));
        RenderServices.shapes().rounded(x, y, x + width * pct, y + 4.0f * s, 2.0f * s,
                theme.withAlpha(MaterialClickTheme.PRIMARY, 210.0f * theme.alpha()));
        drawKnob(x + width * pct, y + 2.0f * s, theme, key, active);
    }

    private void drawKnob(float centerX, float centerY, MaterialClickTheme theme, String key, boolean active) {
        float s = gui.layout().scale;
        float focus = gui.easedAnimation(key + ".focus", active ? 1.0f : 0.0f,
                0.30f, 0.0f, AnimationUtil.Ease.OUT_CUBIC);
        float radius = (6.0f + 2.0f * focus) * s;
        if (focus > 0.01f) {
            RenderServices.shapes().shadow(centerX - radius, centerY - radius, centerX + radius, centerY + radius,
                    radius, theme.withAlpha(MaterialClickTheme.PRIMARY, 70.0f * theme.alpha() * focus), 4, 2.0f * s);
        }
        RenderServices.shapes().rounded(centerX - radius, centerY - radius,
                centerX + radius, centerY + radius, radius,
                theme.withAlpha(MaterialClickTheme.PRIMARY, 255.0f * theme.alpha()));
    }

    private boolean handleModeClick(Mode mode, float x, float y, float width, int mouseX, int mouseY) {
        float s = gui.layout().scale;
        boolean expanded = expandedModes.contains(mode);
        if (expanded) {
            float optionY = y + 34.0f * s;
            Enum[] modes = mode.getModes();
            for (Enum option : modes) {
                if (MaterialClickLayout.contains(x, optionY, x + width, optionY + 20.0f * s, mouseX, mouseY)) {
                    mode.setMode(option.name());
                    expandedModes.remove(mode);
                    return true;
                }
                optionY += 23.0f * s;
            }
        }
        if (MaterialClickLayout.contains(x, y, x + width, y + 32.0f * s, mouseX, mouseY)) {
            if (expanded) {
                expandedModes.remove(mode);
            } else {
                expandedModes.clear();
                expandedModes.add(mode);
            }
            return true;
        }
        return false;
    }

    private void beginNumberDrag(Numbers value, int mouseX, float x, float w, double min, double max,
                                 Numbers pair, boolean lowerBound) {
        draggingNumber = value;
        draggingPair = pair;
        draggingLowerBound = lowerBound;
        draggingX = x;
        draggingW = w;
        draggingMin = min;
        draggingMax = max;
        updateNumber(value, mouseX, x, w, min, max, pair, lowerBound);
    }

    private void updateNumber(Numbers value, int mouseX, float x, float w, double min, double max,
                              Numbers pair, boolean lowerBound) {
        double inc = value.getIncrement().doubleValue();
        if (inc <= 0.0D) {
            inc = 0.1D;
        }
        double pct = MaterialClickLayout.clamp((mouseX - x) / w, 0.0f, 1.0f);
        double result = min + (max - min) * pct;
        result = Math.round(result / inc) * inc;
        result = Math.max(min, Math.min(max, result));
        if (pair != null) {
            double pairValue = numberValue(pair);
            result = lowerBound ? Math.min(result, pairValue) : Math.max(result, pairValue);
        }
        setNumberValue(value, result);
    }

    private void setNumberValue(Numbers value, double result) {
        value.setNumberValue(result);
    }

    private float modeHeight(Mode mode) {
        float s = gui.layout().scale;
        float collapsed = 32.0f * s;
        float expanded = (34.0f + mode.getModes().length * 23.0f) * s;
        return AnimationUtil.lerp(collapsed, expanded, modeProgress(mode));
    }

    private float modeProgress(Mode mode) {
        return gui.easedAnimation("value.mode.expand." + gui.animationKey(mode),
                expandedModes.contains(mode) ? 1.0f : 0.0f, 0.28f, 0.0f, AnimationUtil.Ease.OUT_CUBIC);
    }

    private float sliderX(float x, float width) {
        return x;
    }

    private float sliderW(float width) {
        return width;
    }

    private float pct(Numbers value) {
        return pct(value, value.getMinimum().doubleValue(), value.getMaximum().doubleValue());
    }

    private float pct(Numbers value, double min, double max) {
        if (max <= min) {
            return 0.0f;
        }
        return MaterialClickLayout.clamp((float) ((numberValue(value) - min) / (max - min)), 0.0f, 1.0f);
    }

    private double numberValue(Numbers value) {
        Object current = value.getValue();
        return current instanceof Number ? ((Number) current).doubleValue() : 0.0D;
    }

    private boolean isVisible(Module module, Value value) {
        return value != null && value.isVisible() && !isHiddenPaletteValue(module, value);
    }

    private boolean isColorStart(Module module, int index) {
        List<Value> values = module.getValues();
        return index + 2 < values.size()
                && isNumberNamed(values.get(index), "red")
                && isNumberNamed(values.get(index + 1), "green")
                && isNumberNamed(values.get(index + 2), "blue");
    }

    private boolean isRangeStart(Module module, int index) {
        List<Value> values = module.getValues();
        if (index + 1 >= values.size() || !(values.get(index) instanceof Numbers) || !(values.get(index + 1) instanceof Numbers)) {
            return false;
        }
        String first = rangeBase(values.get(index), "min");
        String second = rangeBase(values.get(index + 1), "max");
        return first.length() > 0 && first.equals(second);
    }

    private boolean isNumberNamed(Value value, String name) {
        return value instanceof Numbers && normalizeValueName(value).equals(name);
    }

    private boolean isHiddenPaletteValue(Module module, Value value) {
        if (module == null || value == null || !(value instanceof Option)) {
            return false;
        }
        String moduleName = MaterialClickGui.normalize(module.getName());
        String valueName = normalizeValueName(value);
        return moduleName.equals("esp") && (valueName.equals("rainbow") || valueName.equals("paletterainbow"));
    }

    private String rangeBase(Value value, String prefix) {
        return rangeDisplayBase(value, prefix).replace(" ", "").replace("_", "").replace("-", "").toLowerCase(Locale.ROOT);
    }

    private String rangeDisplayBase(Value value, String prefix) {
        String raw = gui.displayName(value).trim();
        String lower = raw.toLowerCase(Locale.ROOT);
        if (lower.startsWith(prefix + " ")) {
            return raw.substring(prefix.length()).trim();
        }
        if (lower.startsWith(prefix) && raw.length() > prefix.length()) {
            return raw.substring(prefix.length()).trim();
        }
        return "";
    }

    private String rangeLabel(Value value) {
        String base = rangeDisplayBase(value, "min");
        return base.length() == 0 ? gui.displayName(value) : base;
    }

    private String colorLabel(Value red) {
        String label = gui.displayName(red);
        String lower = label.toLowerCase(Locale.ROOT);
        if (lower.endsWith(" red")) {
            return label.substring(0, label.length() - 4).trim();
        }
        if (lower.equals("red")) {
            return "Color";
        }
        return label;
    }

    private String normalizeValueName(Value value) {
        String raw = value == null ? "" : value.getName();
        if (raw == null || raw.length() == 0) {
            raw = value == null ? "" : value.getDisplayName();
        }
        return raw == null ? "" : raw.replace(" ", "").replace("_", "").replace("-", "").toLowerCase(Locale.ROOT);
    }

    private Numbers colorChannelAt(Numbers red, Numbers green, Numbers blue, float y, int mouseY) {
        float s = gui.layout().scale;
        if (mouseY >= y + 56.0f * s) {
            return blue;
        }
        if (mouseY >= y + 36.0f * s) {
            return green;
        }
        return red;
    }

    private int clampColor(double value) {
        return Math.max(0, Math.min(255, (int) Math.round(value)));
    }

    private String modeLabel(String label) {
        return label == null ? "" : label.replace("_", " ");
    }

    private String formatNumber(double value) {
        if (Math.abs(value - Math.round(value)) < 0.005D) {
            return String.valueOf((long) Math.round(value));
        }
        String text = String.format(Locale.ROOT, "%.2f", value);
        while (text.endsWith("0")) {
            text = text.substring(0, text.length() - 1);
        }
        if (text.endsWith(".")) {
            text = text.substring(0, text.length() - 1);
        }
        return text;
    }
}
