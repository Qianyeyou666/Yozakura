package gq.vapulite.ui.click.material;

import gq.vapulite.engine.font.FontLoaders;
import gq.vapulite.engine.render.ui.RenderServices;
import gq.vapulite.module.Module;
import gq.vapulite.util.animation.AnimationUtil;
import gq.vapulite.value.Mode;
import gq.vapulite.value.Numbers;
import gq.vapulite.value.Option;
import gq.vapulite.value.Value;
import gq.vapulite.value.properties.ModeProperty;
import org.lwjgl.input.Mouse;

import java.awt.Color;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 模块卡片内的设置值控件。
 *
 * <p>支持 Option 开关、Numbers 滑块、Mode 下拉、min/max 范围滑块以及
 * Red/Green/Blue 三元颜色控制，保证新 GUI 不是静态展示。</p>
 */
@SuppressWarnings({"rawtypes", "unchecked"})
final class MaterialValueRenderer {
    private static final int COLOR_DRAG_NONE = 0;
    private static final int COLOR_DRAG_SATURATION_VALUE = 1;
    private static final int COLOR_DRAG_HUE = 2;
    private static final int COLOR_DRAG_ALPHA = 3;

    private final Map<Value, String> valueTexts = new IdentityHashMap<Value, String>();
    private final Map<Value, String> previousValueTexts = new IdentityHashMap<Value, String>();
    private final Map<Value, Double> valueTextNumbers = new IdentityHashMap<Value, Double>();
    private final Map<Value, Boolean> valueTextIncreasing = new IdentityHashMap<Value, Boolean>();
    private final Map<Value, Float> valueTextProgress = new IdentityHashMap<Value, Float>();
    private final Map<Value, Float> colorHues = new IdentityHashMap<Value, Float>();


    private final MaterialClickGui gui;
    private final Set<Value> expandedModes = new HashSet<Value>();
    private final Set<Value> expandedColors = new HashSet<Value>();
    private final Set<Module> preparedModules = Collections.newSetFromMap(new IdentityHashMap<Module, Boolean>());

    private Numbers draggingNumber;
    private Numbers draggingPair;
    private boolean draggingLowerBound;
    private float draggingX;
    private float draggingW;
    private double draggingMin;
    private double draggingMax;
    private Numbers draggingColorRed;
    private Numbers draggingColorGreen;
    private Numbers draggingColorBlue;
    private Numbers draggingColorAlpha;
    private int draggingColorPart;
    private float draggingColorX;
    private float draggingColorY;
    private float draggingColorW;


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
                Numbers alpha = colorAlpha(module, values, i);
                total += colorHeight((Numbers) value, alpha);
                i += colorValueSpan(module, values, i) - 1;
            } else if (isRangeStart(module, i)) {
                total += 50.0f * gui.layout().scale;
                i += 1;
            } else if (value instanceof ModeProperty) {
                total += modeHeight((ModeProperty) value);
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

    void prepare(Module module) {
        if (module == null || !preparedModules.add(module)) {
            return;
        }
        List<Value> values = module.getValues();
        for (int i = 0; i < values.size(); i++) {
            Value value = values.get(i);
            if (!isVisible(module, value)) {
                continue;
            }
            if (isColorStart(module, i)) {
                Numbers red = (Numbers) value;
                Numbers green = (Numbers) values.get(i + 1);
                Numbers blue = (Numbers) values.get(i + 2);
                Numbers alpha = colorAlpha(module, values, i);
                prepareColor(module, red, green, blue, alpha);
                i += colorValueSpan(module, values, i) - 1;
            } else if (isRangeStart(module, i)) {
                prepareRange((Numbers) values.get(i), (Numbers) values.get(i + 1));
                i += 1;
            } else if (value instanceof Mode) {
                prepareMode((Mode) value);
            } else if (value instanceof Numbers) {
                prepareNumber((Numbers) value);
            } else if (value instanceof Option) {
                prepareOption((Option) value);
            } else {
                preparePlainValue(value);
            }
        }
    }

    private void prepareOption(Option value) {
        String key = gui.animationKey(value);
        boolean active = Boolean.TRUE.equals(value.getValue());
        gui.prepareAnimation("value.option." + key, active ? 1.0f : 0.0f);
        gui.prepareAnimation("value.hover." + key, 0.0f);
        preparePlainValue(value);
    }

    private void prepareNumber(Numbers value) {
        String key = gui.animationKey(value);
        double current = numberValue(value);
        String text = formatNumber(current);
        gui.prepareAnimation("value.number." + key, pct(value));
        gui.prepareAnimation("value.slider." + key + ".focus", 0.0f);
        prepareValueText(value, text, current);
        preparePlainValue(value);
    }

    private void prepareRange(Numbers min, Numbers max) {
        double rangeMin = Math.min(min.getMinimum().doubleValue(), max.getMinimum().doubleValue());
        double rangeMax = Math.max(min.getMaximum().doubleValue(), max.getMaximum().doubleValue());
        String minKey = gui.animationKey(min);
        String maxKey = gui.animationKey(max);
        double minValue = numberValue(min);
        double maxValue = numberValue(max);
        String values = formatNumber(minValue) + " - " + formatNumber(maxValue);
        gui.prepareAnimation("value.range.min." + minKey, pct(min, rangeMin, rangeMax));
        gui.prepareAnimation("value.range.max." + maxKey, pct(max, rangeMin, rangeMax));
        gui.prepareAnimation("value.slider." + minKey + ".focus", 0.0f);
        gui.prepareAnimation("value.slider." + maxKey + ".focus", 0.0f);
        prepareValueText(min, values, (minValue + maxValue) * 0.5D);
        FontLoaders.C14.getStringWidth(rangeLabel(min));
        FontLoaders.C14.getStringWidth(values);
    }

    private void prepareMode(Mode mode) {
        String key = gui.animationKey(mode);
        gui.prepareAnimation("value.mode.hover." + key, 0.0f);
        gui.prepareAnimation("value.mode.expand." + key, 0.0f);
        FontLoaders.C14.getStringWidth(gui.displayName(mode));
        FontLoaders.C14.getStringWidth(modeLabel(mode.getModeAsString()));
        Enum[] modes = mode.getModes();
        for (Enum option : modes) {
            if (option != null) {
                FontLoaders.C14.getStringWidth(modeLabel(option.name()));
            }
        }
    }

    private void prepareColor(Module module, Numbers red, Numbers green, Numbers blue, Numbers alpha) {
        String key = gui.animationKey(red);
        gui.prepareAnimation("value.color.expand." + key, 0.0f);
        gui.prepareAnimation("value.color.hover." + key, 0.0f);
        gui.prepareAnimation("value.color.focus." + key, 0.0f);
        gui.prepareAnimation("value.color.hue." + key, colorHue(red, green, blue));
        gui.prepareAnimation("value.color.sat." + key, colorSaturation(red, green, blue));
        gui.prepareAnimation("value.color.bri." + key, colorBrightness(red, green, blue));
        if (alpha != null) {
            double alphaValue = numberValue(alpha);
            gui.prepareAnimation("value.color.alpha." + key, colorAlphaPct(alpha));
            prepareValueText(alpha, formatNumber(alphaValue), alphaValue);
            FontLoaders.C14.getStringWidth(gui.displayName(alpha));
        }
        preparePlainValue(red);
        preparePlainValue(green);
        preparePlainValue(blue);
        if (alpha != null) {
            preparePlainValue(alpha);
        }
        FontLoaders.C14.getStringWidth(colorLabel(red));
    }

    private void preparePlainValue(Value value) {
        gui.animationKey(value);
        FontLoaders.C14.getStringWidth(gui.displayName(value));
        Object raw = value.getValue();
        if (raw != null) {
            FontLoaders.C14.getStringWidth(String.valueOf(raw));
        }
    }

    private void prepareValueText(Value value, String text, double numericValue) {
        if (!valueTexts.containsKey(value)) {
            valueTexts.put(value, text);
            valueTextNumbers.put(value, numericValue);
            valueTextProgress.put(value, 1.0f);
        }
        FontLoaders.C14.getStringWidth(text);
    }

    void render(Module module, float x, float y, float width, int mouseX, int mouseY) {
        List<Value> values = module.getValues();
        for (int i = 0; i < values.size(); i++) {
            Value value = values.get(i);
            if (!isVisible(module, value)) {
                continue;
            }
            if (isColorStart(module, i)) {
                Numbers red = (Numbers) values.get(i);
                Numbers alpha = colorAlpha(module, values, i);
                drawColor(red, (Numbers) values.get(i + 1),
                        (Numbers) values.get(i + 2), alpha, x, y, width, mouseX, mouseY);
                y += colorHeight(red, alpha) + 8.0f * gui.layout().scale;
                i += colorValueSpan(module, values, i) - 1;
            } else if (isRangeStart(module, i)) {
                drawRange((Numbers) values.get(i), (Numbers) values.get(i + 1), x, y, width);
                y += 58.0f * gui.layout().scale;
                i += 1;
            } else if (value instanceof ModeProperty) {
                drawModeProperty((ModeProperty) value, x, y, width, mouseX, mouseY);
                y += modeHeight((ModeProperty) value) + 8.0f * gui.layout().scale;
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
                Numbers red = (Numbers) values.get(i);
                Numbers green = (Numbers) values.get(i + 1);
                Numbers blue = (Numbers) values.get(i + 2);
                Numbers alpha = colorAlpha(module, values, i);
                float h = colorHeight(red, alpha);
                if (MaterialClickLayout.contains(x, y, x + width, y + h, mouseX, mouseY)) {
                    handleColorClick(red, green, blue, alpha, x, y, width, mouseX, mouseY);
                    return true;
                }
                y += h + 8.0f * gui.layout().scale;
                i += colorValueSpan(module, values, i) - 1;
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
            if (value instanceof ModeProperty) {
                float h = modeHeight((ModeProperty) value);
                if (handleModePropertyClick((ModeProperty) value, x, y, width, mouseX, mouseY)) {
                    return true;
                }
                y += h + 8.0f * gui.layout().scale;
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

    void updateDragging(int mouseX, int mouseY) {
        if (draggingColorPart != COLOR_DRAG_NONE) {
            if (!Mouse.isButtonDown(0)) {
                releaseDrag();
                return;
            }
            updateColorDrag(mouseX, mouseY);
            return;
        }
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
        draggingColorRed = null;
        draggingColorGreen = null;
        draggingColorBlue = null;
        draggingColorAlpha = null;
        draggingColorPart = COLOR_DRAG_NONE;
    }

    void closeDropdown() {
        expandedModes.clear();
        expandedColors.clear();
        releaseDrag();
    }

    private void drawOption(Option value, float x, float y, float width, int mouseX, int mouseY) {
        MaterialClickTheme theme = gui.theme();
        float s = gui.layout().scale;
        boolean active = Boolean.TRUE.equals(value.getValue());
        String key = gui.animationKey(value);
        float activeProgress = gui.easedAnimation("value.option." + key, active ? 1.0f : 0.0f,
                0.24f, active ? 1.0f : 0.0f, AnimationUtil.Ease.IN_OUT_CUBIC);
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
        double current = numberValue(value);
        String text = formatNumber(current);
        FontLoaders.C14.drawString(gui.displayName(value), x, y, theme.muted());
        drawAnimatedValueText(value, text, current, x + width, y, theme.muted());
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
        double minValue = numberValue(min);
        double maxValue = numberValue(max);
        String values = formatNumber(minValue) + " - " + formatNumber(maxValue);
        FontLoaders.C14.drawString(label, x, y, theme.muted());
        drawAnimatedValueText(min, values, (minValue + maxValue) * 0.5D, x + width, y, theme.muted());

        float sx = sliderX(x, width);
        float sy = y + 24.0f * s;
        float sw = sliderW(width);
        drawSliderTrack(sx, sy, sw, minPct, maxPct, theme, draggingNumber == min || draggingNumber == max);
        drawKnob(sx + sw * minPct, sy + 2.0f * s, theme, "value.slider." + minKey, draggingNumber == min);
        drawKnob(sx + sw * maxPct, sy + 2.0f * s, theme, "value.slider." + maxKey, draggingNumber == max);
    }

    private void drawColor(Numbers red, Numbers green, Numbers blue, Numbers alpha,
                           float x, float y, float width, int mouseX, int mouseY) {
        MaterialClickTheme theme = gui.theme();
        float s = gui.layout().scale;
        String key = gui.animationKey(red);
        int redValue = colorChannel(red);
        int greenValue = colorChannel(green);
        int blueValue = colorChannel(blue);
        int alphaValue = alpha == null ? 255 : colorAlphaValue(alpha);
        float hue = colorHue(red, green, blue);
        float saturation = colorSaturation(red, green, blue);
        float brightness = colorBrightness(red, green, blue);
        boolean active = draggingColorRed == red;
        float collapsedH = colorCollapsedHeight();
        float expand = colorProgress(red);
        float hover = gui.easedAnimation("value.color.hover." + key,
                MaterialClickLayout.contains(x, y, x + width, y + collapsedH, mouseX, mouseY) ? 1.0f : 0.0f,
                0.28f, 0.0f, AnimationUtil.Ease.OUT_CUBIC);
        float focus = gui.easedAnimation("value.color.focus." + key, active ? 1.0f : 0.0f,
                0.24f, 0.0f, AnimationUtil.Ease.IN_OUT_CUBIC);
        float shownHue = gui.animation("value.color.hue." + key, hue, active ? 0.58f : 0.28f, hue);
        float shownSaturation = gui.animation("value.color.sat." + key, saturation, active ? 0.58f : 0.28f, saturation);
        float shownBrightness = gui.animation("value.color.bri." + key, brightness, active ? 0.58f : 0.28f, brightness);
        float shownAlpha = alpha == null ? 1.0f : gui.animation("value.color.alpha." + key,
                colorAlphaPct(alpha), active ? 0.58f : 0.28f, colorAlphaPct(alpha));

        int rgb = 0xFF000000 | (redValue << 16) | (greenValue << 8) | blueValue;
        int preview = theme.withAlpha(rgb, alphaValue * theme.alpha());
        int hueColor = 0xFF000000 | (Color.HSBtoRGB(shownHue, 1.0f, 1.0f) & 0x00FFFFFF);

        FontLoaders.C14.drawString(colorLabel(red), x, y + 8.0f * s, theme.muted());
        float swatch = 24.0f * s;
        drawColorSwatch(x + width - swatch, y + 4.0f * s, swatch, preview, hover, theme);

        float clipH = colorHeight(red, alpha) - collapsedH;
        if (clipH <= 0.5f) {
            return;
        }

        float controlsAlpha = expand * theme.alpha();
        float controlY = y - 6.0f * s * (1.0f - expand);
        float squareX = x;
        float squareY = colorSquareY(controlY);
        float squareW = width;
        float squareH = colorSquareH();
        float railH = colorRailH();
        float hueY = colorHueY(controlY);
        float radius = 10.0f * s;

        gui.beginScissor(x - 8.0f * s, y + collapsedH, width + 16.0f * s, clipH);
        try {
            RenderServices.shapes().roundedPalette(squareX, squareY, squareX + squareW, squareY + squareH,
                    radius, shownHue, controlsAlpha);
            RenderServices.shapes().roundedBorder(squareX, squareY, squareX + squareW, squareY + squareH,
                    radius, 0.7f * s, 0, theme.withAlpha(0xFFFFFFFF, 34.0f * controlsAlpha));
            drawColorCursor(squareX + squareW * shownSaturation,
                    squareY + squareH * (1.0f - shownBrightness), focus, controlsAlpha, theme);

            RenderServices.shapes().roundedHue(x, hueY, x + width, hueY + railH, railH / 2.0f, controlsAlpha);
            RenderServices.shapes().roundedBorder(x, hueY, x + width, hueY + railH, railH / 2.0f,
                    0.65f * s, 0, theme.withAlpha(0xFFFFFFFF, 28.0f * controlsAlpha));
            drawColorRailThumb(x + width * shownHue, hueY + railH / 2.0f, railH, hueColor, focus, controlsAlpha, theme);

            if (alpha != null) {
                float alphaLabelY = colorAlphaLabelY(controlY);
                float alphaY = colorAlphaY(controlY);
                FontLoaders.C14.drawString(gui.displayName(alpha), x, alphaLabelY,
                        theme.withAlpha(MaterialClickTheme.MUTED, 255.0f * controlsAlpha));
                drawAnimatedValueText(alpha, formatNumber(numberValue(alpha)), numberValue(alpha),
                        x + width, alphaLabelY, theme.withAlpha(MaterialClickTheme.MUTED, 255.0f * controlsAlpha));
                RenderServices.shapes().rounded(x, alphaY, x + width, alphaY + railH, railH / 2.0f,
                        theme.withAlpha(MaterialClickTheme.SURFACE_VARIANT, 76.0f * controlsAlpha));
                RenderServices.shapes().roundedGradient(x, alphaY, x + width, alphaY + railH, railH / 2.0f,
                        theme.withAlpha(theme.blend(MaterialClickTheme.SURFACE_VARIANT, rgb, 0.18f), 108.0f * controlsAlpha),
                        theme.withAlpha(theme.blend(MaterialClickTheme.SURFACE_VARIANT, rgb, 0.18f), 108.0f * controlsAlpha),
                        theme.withAlpha(rgb, 232.0f * controlsAlpha),
                        theme.withAlpha(rgb, 232.0f * controlsAlpha));
                RenderServices.shapes().roundedBorder(x, alphaY, x + width, alphaY + railH, railH / 2.0f,
                        0.65f * s, 0, theme.withAlpha(0xFFFFFFFF, 24.0f * controlsAlpha));
                drawColorRailThumb(x + width * shownAlpha, alphaY + railH / 2.0f, railH, rgb, focus, controlsAlpha, theme);
            }
        } finally {
            gui.endScissor();
        }
    }

    private void drawColorSwatch(float x, float y, float size, int preview, float hover, MaterialClickTheme theme) {
        float s = gui.layout().scale;
        RenderServices.shapes().rounded(x - 3.0f * s, y - 3.0f * s,
                x + size + 3.0f * s, y + size + 3.0f * s,
                10.0f * s, theme.withAlpha(MaterialClickTheme.SURFACE_VARIANT, (42.0f + 18.0f * hover) * theme.alpha()));
        RenderServices.shapes().rounded(x, y, x + size, y + size, 8.0f * s,
                theme.withAlpha(MaterialClickTheme.SURFACE, 112.0f * theme.alpha()));
        RenderServices.shapes().rounded(x, y, x + size, y + size, 8.0f * s, preview);
        RenderServices.shapes().roundedBorder(x, y, x + size, y + size,
                8.0f * s, 0.7f * s, 0, theme.withAlpha(0xFFFFFFFF, 44.0f * theme.alpha()));
    }

    private void drawColorCursor(float centerX, float centerY, float focus, float alpha, MaterialClickTheme theme) {
        float s = gui.layout().scale;
        float radius = (4.6f + 1.2f * focus) * s;
        RenderServices.shapes().circleOutline(centerX, centerY, radius + 1.35f * s,
                1.0f * s, theme.withAlpha(0xFF000000, 130.0f * alpha));
        RenderServices.shapes().circleOutline(centerX, centerY, radius,
                1.35f * s, theme.withAlpha(0xFFFFFFFF, 230.0f * alpha));
    }

    private void drawColorRailThumb(float centerX, float centerY, float railH, int fill,
                                    float focus, float alpha, MaterialClickTheme theme) {
        float s = gui.layout().scale;
        float halfW = (2.4f + 0.8f * focus) * s;
        float halfH = railH / 2.0f + (4.2f + 1.4f * focus) * s;
        RenderServices.shapes().roundedBorder(centerX - halfW, centerY - halfH,
                centerX + halfW, centerY + halfH, halfW,
                0.9f * s, theme.withAlpha(fill, 240.0f * alpha),
                theme.withAlpha(0xFFFFFFFF, 210.0f * alpha));
    }

    private boolean handleColorClick(Numbers red, Numbers green, Numbers blue, Numbers alpha,
                                     float x, float y, float width, int mouseX, int mouseY) {
        float s = gui.layout().scale;
        float collapsedH = colorCollapsedHeight();
        if (MaterialClickLayout.contains(x, y, x + width, y + collapsedH, mouseX, mouseY)) {
            if (expandedColors.contains(red)) {
                expandedColors.remove(red);
                releaseDrag();
            } else {
                expandedColors.add(red);
            }
            return true;
        }
        if (!expandedColors.contains(red)) {
            return false;
        }
        float squareY = colorSquareY(y);
        float squareH = colorSquareH();
        float hueY = colorHueY(y);
        float railH = colorRailH();
        if (MaterialClickLayout.contains(x, squareY, x + width, squareY + squareH, mouseX, mouseY)) {
            beginColorDrag(red, green, blue, alpha, COLOR_DRAG_SATURATION_VALUE, x, y, width, mouseX, mouseY);
            return true;
        }
        if (MaterialClickLayout.contains(x, hueY - 4.0f * s, x + width, hueY + railH + 4.0f * s, mouseX, mouseY)) {
            beginColorDrag(red, green, blue, alpha, COLOR_DRAG_HUE, x, y, width, mouseX, mouseY);
            return true;
        }
        if (alpha != null) {
            float alphaY = colorAlphaY(y);
            if (MaterialClickLayout.contains(x, alphaY - 4.0f * s, x + width, alphaY + railH + 4.0f * s, mouseX, mouseY)) {
                beginColorDrag(red, green, blue, alpha, COLOR_DRAG_ALPHA, x, y, width, mouseX, mouseY);
                return true;
            }
        }
        return false;
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
        float pillY = y + 5.0f * s;
        float pillH = 22.0f * s;
        RenderServices.shapes().rounded(pillX, pillY, pillX + pillW, pillY + pillH,
                8.0f * s, theme.withAlpha(theme.blend(0xFFFFFFFF, MaterialClickTheme.PRIMARY_CONTAINER, expand),
                        (26.0f + 112.0f * expand + 18.0f * hover) * theme.alpha()));
        float currentY = pillY + Math.max(0.0f, pillH - FontLoaders.C14.getStringHeight(current)) / 2.0f + 0.5f * s;
        FontLoaders.C14.drawCenteredString(current, pillX + pillW / 2.0f, currentY,
                theme.withAlpha(theme.blend(MaterialClickTheme.MUTED, MaterialClickTheme.ON_PRIMARY_CONTAINER, expand),
                        255.0f * theme.alpha()));

        Enum[] modes = mode.getModes();
        float collapsedH = 32.0f * s;
        float expandedH = (34.0f + modes.length * 23.0f) * s;
        float optionClipH = AnimationUtil.lerp(collapsedH, expandedH, expand) - collapsedH;
        if (optionClipH <= 0.5f) {
            return;
        }
        gui.beginScissor(x, y + collapsedH, width, optionClipH);
        try {
            float optionY = y + (34.0f - 5.0f * (1.0f - expand)) * s;
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
        } finally {
            gui.endScissor();
        }
    }

    private void drawModeProperty(ModeProperty mode, float x, float y, float width, int mouseX, int mouseY) {
        MaterialClickTheme theme = gui.theme();
        float s = gui.layout().scale;
        float expand = modeProgress(mode);
        float hover = gui.easedAnimation("value.mode.hover." + gui.animationKey(mode),
                MaterialClickLayout.contains(x, y, x + width, y + 32.0f * s, mouseX, mouseY) ? 1.0f : 0.0f,
                0.28f, 0.0f, AnimationUtil.Ease.OUT_CUBIC);
        FontLoaders.C14.drawString(gui.displayName(mode), x, y + 8.0f * s, theme.muted());

        String current = modeLabel(mode.getModeString());
        float pillW = Math.max(68.0f * s, FontLoaders.C14.getStringWidth(current) + 22.0f * s);
        float pillX = x + width - pillW;
        float pillY = y + 5.0f * s;
        float pillH = 22.0f * s;
        RenderServices.shapes().rounded(pillX, pillY, pillX + pillW, pillY + pillH,
                8.0f * s, theme.withAlpha(theme.blend(0xFFFFFFFF, MaterialClickTheme.PRIMARY_CONTAINER, expand),
                        (26.0f + 112.0f * expand + 18.0f * hover) * theme.alpha()));
        float currentY = pillY + Math.max(0.0f, pillH - FontLoaders.C14.getStringHeight(current)) / 2.0f + 0.5f * s;
        FontLoaders.C14.drawCenteredString(current, pillX + pillW / 2.0f, currentY,
                theme.withAlpha(theme.blend(MaterialClickTheme.MUTED, MaterialClickTheme.ON_PRIMARY_CONTAINER, expand),
                        255.0f * theme.alpha()));

        String[] modes = mode.getModes();
        float collapsedH = 32.0f * s;
        float expandedH = (34.0f + modes.length * 23.0f) * s;
        float optionClipH = AnimationUtil.lerp(collapsedH, expandedH, expand) - collapsedH;
        if (optionClipH <= 0.5f) {
            return;
        }
        gui.beginScissor(x, y + collapsedH, width, optionClipH);
        try {
            float optionY = y + (34.0f - 5.0f * (1.0f - expand)) * s;
            for (String option : modes) {
                boolean active = option.equalsIgnoreCase(mode.getModeString());
                RenderServices.shapes().rounded(x, optionY, x + width, optionY + 20.0f * s, 7.0f * s,
                        theme.withAlpha(active ? MaterialClickTheme.PRIMARY_CONTAINER : 0xFFFFFFFF,
                                (active ? 120.0f : 16.0f) * theme.alpha() * expand));
                FontLoaders.C14.drawString(modeLabel(option), x + 9.0f * s, optionY + 5.0f * s,
                        active ? theme.withAlpha(MaterialClickTheme.ON_PRIMARY_CONTAINER, 255.0f * theme.alpha() * expand)
                                : theme.withAlpha(MaterialClickTheme.MUTED, 255.0f * theme.alpha() * expand));
                optionY += 23.0f * s;
            }
        } finally {
            gui.endScissor();
        }
    }

    private void drawTextValue(Value value, float x, float y, float width) {
        MaterialClickTheme theme = gui.theme();
        String text = String.valueOf(value.getValue());
        FontLoaders.C14.drawString(gui.displayName(value), x, y + 7.0f * gui.layout().scale, theme.muted());
        FontLoaders.C14.drawString(text, x + width - FontLoaders.C14.getStringWidth(text), y + 7.0f * gui.layout().scale, theme.muted());
    }

    private void drawSlider(float x, float y, float width, float pct, MaterialClickTheme theme, String key, boolean active) {
        drawSliderTrack(x, y, width, 0.0f, pct, theme, active);
        drawKnob(x + width * MaterialClickTheme.clamp(pct, 0.0f, 1.0f), y + 2.0f * gui.layout().scale, theme, key, active);
    }

    private void drawSliderTrack(float x, float y, float width, float fromPct, float toPct,
                                 MaterialClickTheme theme, boolean active) {
        float s = gui.layout().scale;
        float startPct = MaterialClickTheme.clamp(Math.min(fromPct, toPct), 0.0f, 1.0f);
        float endPct = MaterialClickTheme.clamp(Math.max(fromPct, toPct), 0.0f, 1.0f);
        float trackH = 4.0f * s;
        RenderServices.shapes().rounded(x, y, x + width, y + trackH, trackH / 2.0f, sliderRemaining(theme, active));
        float fillX = x + width * startPct;
        float fillRight = x + width * endPct;
        if (fillRight > fillX + 0.5f * s) {
            RenderServices.shapes().rounded(fillX, y, fillRight, y + trackH, trackH / 2.0f,
                    theme.withAlpha(MaterialClickTheme.PRIMARY, 216.0f * theme.alpha()));
        }
    }

    private int sliderRemaining(MaterialClickTheme theme, boolean active) {
        return theme.withAlpha(MaterialClickTheme.SURFACE_VARIANT, (92.0f + (active ? 30.0f : 0.0f)) * theme.alpha());
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

    private void drawAnimatedValueText(Value value, String text, double numericValue, float rightX, float y, int color) {
        String current = valueTexts.get(value);
        if (current == null) {
            valueTexts.put(value, text);
            valueTextNumbers.put(value, numericValue);
            valueTextProgress.put(value, 1.0f);
            FontLoaders.C14.drawString(text, rightX - FontLoaders.C14.getStringWidth(text), y, color);
            return;
        }

        if (!current.equals(text)) {
            Double previousNumber = valueTextNumbers.get(value);
            previousValueTexts.put(value, current);
            valueTexts.put(value, text);
            valueTextNumbers.put(value, numericValue);
            valueTextIncreasing.put(value, previousNumber == null || numericValue >= previousNumber.doubleValue());
            valueTextProgress.put(value, 0.0f);
            current = text;
        } else {
            valueTextNumbers.put(value, numericValue);
        }

        Float storedProgress = valueTextProgress.get(value);
        float rawProgress = storedProgress == null ? 1.0f : storedProgress.floatValue();
        if (rawProgress < 1.0f) {
            rawProgress = gui.animate(rawProgress, 1.0f, 0.30f);
            if (rawProgress >= 0.94f) {
                rawProgress = 1.0f;
            }
            valueTextProgress.put(value, rawProgress);
        }

        String previous = previousValueTexts.get(value);
        if (previous == null || rawProgress >= 1.0f) {
            previousValueTexts.remove(value);
            FontLoaders.C14.drawString(current, rightX - FontLoaders.C14.getStringWidth(current), y, color);
            return;
        }

        drawDigitFlipText(previous, current, rightX, y, color, rawProgress,
                Boolean.TRUE.equals(valueTextIncreasing.get(value)));
    }

    private void drawDigitFlipText(String previous, String current, float rightX, float y,
                                   int color, float rawProgress, boolean increasing) {
        float s = gui.layout().scale;
        float progress = AnimationUtil.ease(rawProgress, AnimationUtil.Ease.IN_OUT_CUBIC);
        float textH = Math.max(FontLoaders.C14.getStringHeight(previous), FontLoaders.C14.getStringHeight(current));
        float shift = textH + 4.0f * s;
        float direction = increasing ? -1.0f : 1.0f;
        int columns = Math.max(previous.length(), current.length());
        int previousOffset = columns - previous.length();
        int currentOffset = columns - current.length();
        float currentX = rightX - FontLoaders.C14.getStringWidth(current);
        float columnX = currentX;

        for (int column = 0; column < currentOffset; column++) {
            int previousIndex = column - previousOffset;
            if (previousIndex >= 0 && previousIndex < previous.length()) {
                columnX -= charWidth(previous, previousIndex);
            }
        }

        for (int column = 0; column < columns; column++) {
            int previousIndex = column - previousOffset;
            int currentIndex = column - currentOffset;
            boolean previousExists = previousIndex >= 0 && previousIndex < previous.length();
            boolean currentExists = currentIndex >= 0 && currentIndex < current.length();
            String previousChar = previousExists ? charString(previous, previousIndex) : null;
            String currentChar = currentExists ? charString(current, currentIndex) : null;
            float previousW = previousExists ? FontLoaders.C14.getStringWidth(previousChar) : 0.0f;
            float currentW = currentExists ? FontLoaders.C14.getStringWidth(currentChar) : 0.0f;
            float cellW = currentExists ? currentW : previousW;
            float x = currentExists ? currentX : columnX;

            if (previousExists && currentExists && previous.charAt(previousIndex) == current.charAt(currentIndex)) {
                FontLoaders.C14.drawString(currentChar, x, y, color);
            } else {
                drawChangingValueCharacter(previousChar, currentChar, x, cellW, y, textH, color,
                        progress, shift, direction, previousW, currentW);
            }

            if (currentExists) {
                currentX += currentW;
            } else {
                columnX += cellW;
            }
        }
    }

    private void drawChangingValueCharacter(String previousChar, String currentChar, float x, float cellW,
                                            float y, float textH, int color, float progress, float shift,
                                            float direction, float previousW, float currentW) {
        float s = gui.layout().scale;
        float clipW = Math.max(Math.max(previousW, currentW), cellW) + 4.0f * s;
        float clipX = x - Math.max(0.0f, clipW - cellW) / 2.0f;
        gui.beginScissor(clipX, y - 2.0f * s, clipW, textH + 4.0f * s);
        try {
            if (previousChar != null) {
                float previousX = x + (cellW - previousW) / 2.0f;
                FontLoaders.C14.drawString(previousChar, previousX, y + direction * shift * progress,
                        alpha(color, 1.0f - progress));
            }
            if (currentChar != null) {
                float currentX = x + (cellW - currentW) / 2.0f;
                FontLoaders.C14.drawString(currentChar, currentX, y - direction * shift * (1.0f - progress),
                        alpha(color, progress));
            }
        } finally {
            gui.endScissor();
        }
    }

    private String charString(String text, int index) {
        return String.valueOf(text.charAt(index));
    }

    private float charWidth(String text, int index) {
        return FontLoaders.C14.getStringWidth(charString(text, index));
    }

    private int alpha(int color, float alpha) {
        return gui.theme().withAlpha(color, ((color >>> 24) & 255) * MaterialClickTheme.clamp(alpha, 0.0f, 1.0f));
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

    private boolean handleModePropertyClick(ModeProperty mode, float x, float y, float width, int mouseX, int mouseY) {
        float s = gui.layout().scale;
        boolean expanded = expandedModes.contains(mode);
        if (expanded) {
            float optionY = y + 34.0f * s;
            String[] modes = mode.getModes();
            for (String option : modes) {
                if (MaterialClickLayout.contains(x, optionY, x + width, optionY + 20.0f * s, mouseX, mouseY)) {
                    mode.setMode(option);
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

    private void beginColorDrag(Numbers red, Numbers green, Numbers blue, Numbers alpha, int part,
                                float x, float y, float width, int mouseX, int mouseY) {
        draggingNumber = null;
        draggingPair = null;
        draggingColorRed = red;
        draggingColorGreen = green;
        draggingColorBlue = blue;
        draggingColorAlpha = alpha;
        draggingColorPart = part;
        draggingColorX = x;
        draggingColorY = y;
        draggingColorW = width;
        updateColorDrag(mouseX, mouseY);
    }

    private void updateColorDrag(int mouseX, int mouseY) {
        if (draggingColorRed == null || draggingColorGreen == null || draggingColorBlue == null) {
            releaseDrag();
            return;
        }
        if (draggingColorPart == COLOR_DRAG_SATURATION_VALUE) {
            float sat = MaterialClickLayout.clamp((mouseX - draggingColorX) / draggingColorW, 0.0f, 1.0f);
            float bri = 1.0f - MaterialClickLayout.clamp((mouseY - colorSquareY(draggingColorY)) / colorSquareH(), 0.0f, 1.0f);
            setColorFromHsb(draggingColorRed, draggingColorGreen, draggingColorBlue,
                    colorHue(draggingColorRed, draggingColorGreen, draggingColorBlue), sat, bri);
            return;
        }
        if (draggingColorPart == COLOR_DRAG_HUE) {
            float hue = MaterialClickLayout.clamp((mouseX - draggingColorX) / draggingColorW, 0.0f, 1.0f);
            setColorFromHsb(draggingColorRed, draggingColorGreen, draggingColorBlue, hue,
                    colorSaturation(draggingColorRed, draggingColorGreen, draggingColorBlue),
                    colorBrightness(draggingColorRed, draggingColorGreen, draggingColorBlue));
            return;
        }
        if (draggingColorPart == COLOR_DRAG_ALPHA && draggingColorAlpha != null) {
            updateAlpha(draggingColorAlpha,
                    MaterialClickLayout.clamp((mouseX - draggingColorX) / draggingColorW, 0.0f, 1.0f));
        }
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

    private float modeHeight(ModeProperty mode) {
        float s = gui.layout().scale;
        float collapsed = 32.0f * s;
        float expanded = (34.0f + mode.getModes().length * 23.0f) * s;
        return AnimationUtil.lerp(collapsed, expanded, modeProgress(mode));
    }

    private float modeProgress(Value mode) {
        return gui.easedAnimation("value.mode.expand." + gui.animationKey(mode),
                expandedModes.contains(mode) ? 1.0f : 0.0f, 0.24f, 0.0f, AnimationUtil.Ease.IN_OUT_CUBIC);
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
    private float colorHeight(Numbers red, Numbers alpha) {
        return AnimationUtil.lerp(colorCollapsedHeight(), colorExpandedHeight(alpha), colorProgress(red));
    }

    private float colorProgress(Numbers red) {
        return gui.easedAnimation("value.color.expand." + gui.animationKey(red),
                expandedColors.contains(red) ? 1.0f : 0.0f, 0.24f, 0.0f, AnimationUtil.Ease.IN_OUT_CUBIC);
    }

    private float colorCollapsedHeight() {
        return 32.0f * gui.layout().scale;
    }

    private float colorExpandedHeight(Numbers alpha) {
        return (alpha == null ? 132.0f : 166.0f) * gui.layout().scale;
    }

    private float colorSquareY(float y) {
        return y + 38.0f * gui.layout().scale;
    }

    private float colorSquareH() {
        return 66.0f * gui.layout().scale;
    }

    private float colorRailH() {
        return 8.0f * gui.layout().scale;
    }

    private float colorHueY(float y) {
        return colorSquareY(y) + colorSquareH() + 10.0f * gui.layout().scale;
    }

    private float colorAlphaLabelY(float y) {
        return colorHueY(y) + 17.0f * gui.layout().scale;
    }

    private float colorAlphaY(float y) {
        return colorAlphaLabelY(y) + 18.0f * gui.layout().scale;
    }

    private int colorValueSpan(Module module, List<Value> values, int index) {
        return colorAlpha(module, values, index) == null ? 3 : 4;
    }

    private Numbers colorAlpha(Module module, List<Value> values, int index) {
        if (index + 3 >= values.size()) {
            return null;
        }
        Value alpha = values.get(index + 3);
        return isNumberNamed(alpha, "alpha") && isVisible(module, alpha) ? (Numbers) alpha : null;
    }

    private int colorChannel(Numbers value) {
        double max = value.getMaximum().doubleValue();
        double current = numberValue(value);
        return max <= 1.0D ? clampColor(current * 255.0D) : clampColor(current);
    }

    private int colorAlphaValue(Numbers value) {
        return colorChannel(value);
    }

    private float colorAlphaPct(Numbers value) {
        double min = value.getMinimum().doubleValue();
        double max = value.getMaximum().doubleValue();
        if (max <= min) {
            return 1.0f;
        }
        return MaterialClickLayout.clamp((float) ((numberValue(value) - min) / (max - min)), 0.0f, 1.0f);
    }

    private float colorHue(Numbers red, Numbers green, Numbers blue) {
        float r = colorChannel(red) / 255.0f;
        float g = colorChannel(green) / 255.0f;
        float b = colorChannel(blue) / 255.0f;
        float max = Math.max(r, Math.max(g, b));
        float min = Math.min(r, Math.min(g, b));
        float delta = max - min;
        if (delta <= 0.0001f) {
            Float stored = colorHues.get(red);
            return stored == null ? 0.58f : stored.floatValue();
        }
        float hue;
        if (max == r) {
            hue = (g - b) / delta;
            if (hue < 0.0f) {
                hue += 6.0f;
            }
        } else if (max == g) {
            hue = (b - r) / delta + 2.0f;
        } else {
            hue = (r - g) / delta + 4.0f;
        }
        hue /= 6.0f;
        colorHues.put(red, hue);
        return hue;
    }

    private float colorSaturation(Numbers red, Numbers green, Numbers blue) {
        float r = colorChannel(red) / 255.0f;
        float g = colorChannel(green) / 255.0f;
        float b = colorChannel(blue) / 255.0f;
        float max = Math.max(r, Math.max(g, b));
        float min = Math.min(r, Math.min(g, b));
        return max <= 0.0001f ? 0.0f : (max - min) / max;
    }

    private float colorBrightness(Numbers red, Numbers green, Numbers blue) {
        return Math.max(colorChannel(red), Math.max(colorChannel(green), colorChannel(blue))) / 255.0f;
    }

    private void setColorFromHsb(Numbers red, Numbers green, Numbers blue, float hue, float saturation, float brightness) {
        hue = MaterialClickLayout.clamp(hue, 0.0f, 1.0f);
        saturation = MaterialClickLayout.clamp(saturation, 0.0f, 1.0f);
        brightness = MaterialClickLayout.clamp(brightness, 0.0f, 1.0f);
        colorHues.put(red, hue);
        int rgb = Color.HSBtoRGB(hue, saturation, brightness);
        setColorChannel(red, (rgb >>> 16) & 255);
        setColorChannel(green, (rgb >>> 8) & 255);
        setColorChannel(blue, rgb & 255);
    }

    private void setColorChannel(Numbers value, int channel) {
        double max = value.getMaximum().doubleValue();
        double result = max <= 1.0D ? channel / 255.0D : channel;
        setRoundedNumber(value, result);
    }

    private void updateAlpha(Numbers value, float pct) {
        double min = value.getMinimum().doubleValue();
        double max = value.getMaximum().doubleValue();
        setRoundedNumber(value, min + (max - min) * pct);
    }

    private void setRoundedNumber(Numbers value, double result) {
        double min = value.getMinimum().doubleValue();
        double max = value.getMaximum().doubleValue();
        double inc = value.getIncrement().doubleValue();
        result = Math.max(min, Math.min(max, result));
        if (inc > 0.0D) {
            result = Math.round(result / inc) * inc;
        }
        setNumberValue(value, result);
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
