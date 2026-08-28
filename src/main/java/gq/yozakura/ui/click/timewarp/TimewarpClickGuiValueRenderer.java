package gq.yozakura.ui.click.timewarp;

import gq.yozakura.engine.font.CFontRenderer;
import gq.yozakura.engine.render.ui.RenderServices;
import gq.yozakura.ui.click.yozakura.EpsilonPanelFonts;
import gq.yozakura.util.animation.AnimationState;
import gq.yozakura.value.Mode;
import gq.yozakura.value.Numbers;
import gq.yozakura.value.Option;
import gq.yozakura.value.Value;
import gq.yozakura.value.properties.ModeProperty;

import java.util.Locale;

/**
 * Compact Timewarp value sheet. Rows are deliberately independent from the legacy Panel renderer.
 * Popup state is retained while closing so a mode choice never flashes or disappears abruptly.
 */
public final class TimewarpClickGuiValueRenderer {
    public static final float ROW_HEIGHT = 34.0f;
    public static final float ROW_GAP = 2.0f;
    public static final float GROUP_GAP = 8.0f;

    private final AnimationState animations;
    private String draggedSliderKey;
    private Numbers<?> draggedSlider;
    private TimewarpClickGuiGeometry.Rect draggedSliderBounds;
    private String openDropdownKey;
    private Value<?> openDropdownValue;
    private TimewarpClickGuiGeometry.Rect openDropdownAnchor;
    private String closingDropdownKey;
    private Value<?> closingDropdownValue;
    private TimewarpClickGuiGeometry.Rect closingDropdownAnchor;

    public TimewarpClickGuiValueRenderer(AnimationState animations) {
        this.animations = animations;
    }

    public void drawValue(Value<?> value, String key, TimewarpClickGuiGeometry.Rect row,
                          int index, int total, int mouseX, int mouseY, float frameScale) {
        TimewarpClickGuiTheme colors = TimewarpClickGuiTheme.current();
        boolean hovered = row.contains(mouseX, mouseY);
        float enterTarget = 1.0f;
        float enter = animations.animateFrom("value-enter:" + key, enterTarget, 0.30f,
                frameScale, 0.0f);
        float hover = animations.animateFrom("value-hover:" + key, hovered ? 1.0f : 0.0f,
                0.24f, frameScale, 0.0f);
        int alpha = Math.round(255.0f * Math.max(0.0f, Math.min(1.0f, enter)));
        if (hover > 0.01f) {
            RenderServices.shapes().rounded(row.x() - 3.0f, row.y(), row.right() + 3.0f,
                    row.bottom(), 4.0f, TimewarpClickGuiTheme.alpha(colors.cardHover(),
                            Math.round(105.0f * hover * enter)));
        }

        String label = value.getDisplayName();
        CFontRenderer labelFont = EpsilonPanelFonts.text(0.50f);
        labelFont.drawString(label, row.x(), row.y() + 7.0f,
                TimewarpClickGuiTheme.alpha(TimewarpClickGuiTheme.blend(colors.secondary(), colors.text(), hover), alpha));

        if (value instanceof ModeProperty) {
            drawModeProperty((ModeProperty) value, key, row, mouseX, mouseY, frameScale, colors, alpha);
        } else if (value instanceof Mode) {
            drawMode((Mode<?>) value, key, row, mouseX, mouseY, frameScale, colors, alpha);
        } else if (value instanceof Numbers) {
            drawNumber((Numbers<?>) value, key, row, mouseX, frameScale, colors, alpha);
        } else if (value instanceof Option) {
            drawOption((Option<?>) value, key, row, mouseX, mouseY, frameScale, colors, alpha);
        }
    }

    private void drawOption(Option<?> option, String key, TimewarpClickGuiGeometry.Rect row,
                            int mouseX, int mouseY, float frameScale,
                            TimewarpClickGuiTheme colors, int alpha) {
        boolean on = Boolean.TRUE.equals(option.getValue());
        TimewarpClickGuiGeometry.Rect toggle = toggleBounds(row);
        boolean hovered = toggle.contains(mouseX, mouseY);
        float progress = animations.animateFrom("value-toggle:" + key, on ? 1.0f : 0.0f,
                0.24f, frameScale, on ? 1.0f : 0.0f);
        float optionHover = animations.animateFrom("value-option-hover:" + key,
                hovered ? 1.0f : 0.0f, 0.24f, frameScale, 0.0f);
        float pulse = animations.animateFrom("value-toggle-pulse:" + key,
                on ? 1.0f : 0.0f, 0.30f, frameScale, on ? 1.0f : 0.0f);
        int track = TimewarpClickGuiTheme.blend(colors.control(), colors.accent(), progress);
        track = TimewarpClickGuiTheme.blend(track, colors.cardHover(), optionHover * 0.35f);
        RenderServices.shapes().rounded(toggle.x(), toggle.y(), toggle.right(), toggle.bottom(),
                toggle.height() * 0.5f, TimewarpClickGuiTheme.alpha(track, alpha));
        float knob = 10.0f + pulse * 1.0f;
        float centerX = toggle.x() + 7.0f + (toggle.width() - 14.0f) * progress;
        float stretch = progress * (1.0f - progress) * 4.0f;
        RenderServices.shapes().rounded(centerX - knob * 0.5f - stretch,
                toggle.y() + (toggle.height() - knob) * 0.5f,
                centerX + knob * 0.5f + stretch,
                toggle.y() + (toggle.height() + knob) * 0.5f,
                knob * 0.5f, TimewarpClickGuiTheme.alpha(0xFFF4F4F5, alpha));
    }

    private void drawNumber(Numbers<?> number, String key, TimewarpClickGuiGeometry.Rect row,
                            int mouseX, float frameScale, TimewarpClickGuiTheme colors, int alpha) {
        TimewarpClickGuiGeometry.Rect slider = sliderBounds(row);
        double min = number.getMinimum().doubleValue();
        double max = number.getMaximum().doubleValue();
        double current = number.getValue().doubleValue();
        float target = max <= min ? 0.0f : clamp((float) ((current - min) / (max - min)));
        boolean focused = draggedSliderKey != null && draggedSliderKey.equals(key);
        float progress = animations.animateFrom("value-slider:" + key, target,
                focused ? 0.52f : 0.22f, frameScale, target);
        float focus = animations.animateFrom("value-slider-focus:" + key,
                focused ? 1.0f : 0.0f, 0.20f, frameScale, 0.0f);
        float fillRight = slider.x() + slider.width() * progress;
        RenderServices.shapes().rounded(slider.x(), slider.y() + 5.0f, slider.right(),
                slider.y() + 8.0f, 1.5f, TimewarpClickGuiTheme.alpha(colors.control(), alpha));
        RenderServices.shapes().rounded(slider.x(), slider.y() + 5.0f, fillRight,
                slider.y() + 8.0f, 1.5f, TimewarpClickGuiTheme.alpha(colors.accent(), alpha));
        RenderServices.shapes().circle(fillRight, slider.y() + 6.5f, 0, 360,
                3.0f + focus * 1.1f, TimewarpClickGuiTheme.alpha(colors.accent(), alpha));
        String display = formatNumber(current);
        CFontRenderer font = EpsilonPanelFonts.text(0.46f);
        font.drawString(display, row.right() - font.getStringWidth(display),
                row.y() + 5.0f, TimewarpClickGuiTheme.alpha(colors.secondary(), alpha));
    }

    private void drawMode(Mode<?> mode, String key, TimewarpClickGuiGeometry.Rect row,
                          int mouseX, int mouseY, float frameScale, TimewarpClickGuiTheme colors,
                          int alpha) {
        Object current = mode.getValue();
        drawDropdownChip(current == null ? "-" : enumName(current), key, mode, row,
                mouseX, mouseY, frameScale, colors, alpha);
    }

    private void drawModeProperty(ModeProperty mode, String key, TimewarpClickGuiGeometry.Rect row,
                                  int mouseX, int mouseY, float frameScale,
                                  TimewarpClickGuiTheme colors, int alpha) {
        drawDropdownChip(mode.getModeString(), key, mode, row, mouseX, mouseY,
                frameScale, colors, alpha);
    }

    private void drawDropdownChip(String text, String key, Value<?> value,
                                  TimewarpClickGuiGeometry.Rect row, int mouseX, int mouseY,
                                  float frameScale, TimewarpClickGuiTheme colors, int alpha) {
        TimewarpClickGuiGeometry.Rect chip = dropdownBounds(row);
        boolean open = key.equals(openDropdownKey);
        boolean hovered = chip.contains(mouseX, mouseY);
        float progress = animations.animateFrom("value-dropdown:" + key,
                open ? 1.0f : 0.0f, 0.28f, frameScale, 0.0f);
        float chipHover = animations.animateFrom("value-dropdown-hover:" + key,
                hovered ? 1.0f : 0.0f, 0.24f, frameScale, 0.0f);
        int chipColor = TimewarpClickGuiTheme.blend(colors.control(), colors.accentSoft(), progress * 0.78f);
        chipColor = TimewarpClickGuiTheme.blend(chipColor, colors.cardHover(), chipHover * 0.35f);
        RenderServices.shapes().rounded(chip.x(), chip.y(), chip.right(), chip.bottom(), 4.0f,
                TimewarpClickGuiTheme.alpha(chipColor, alpha));
        EpsilonPanelFonts.text(0.52f).drawString(text, chip.x() + 9.0f,
                EpsilonPanelFonts.centeredY(chip.y(), chip.height(), 0.52f),
                TimewarpClickGuiTheme.alpha(open ? colors.text() : colors.secondary(), alpha));
        float cx = chip.right() - 11.0f;
        float cy = chip.y() + chip.height() * 0.5f;
        float rotation = progress * 180.0f;
        double radians = Math.toRadians(rotation - 45.0f);
        float dx = (float) Math.cos(radians) * 3.2f;
        float dy = (float) Math.sin(radians) * 3.2f;
        RenderServices.shapes().line(cx - dx, cy - dy, cx, cy, 1.0f,
                TimewarpClickGuiTheme.alpha(colors.muted(), alpha));
        RenderServices.shapes().line(cx, cy, cx + dx, cy + dy, 1.0f,
                TimewarpClickGuiTheme.alpha(colors.muted(), alpha));
        if (open) {
            openDropdownValue = value;
            openDropdownAnchor = chip;
        }
    }

    public void drawOpenDropdown(int mouseX, int mouseY, float frameScale,
                                 TimewarpClickGuiGeometry.Rect clipBounds) {
        TimewarpClickGuiTheme colors = TimewarpClickGuiTheme.current();
        Value<?> value = openDropdownValue != null ? openDropdownValue : closingDropdownValue;
        TimewarpClickGuiGeometry.Rect anchor = openDropdownAnchor != null
                ? openDropdownAnchor : closingDropdownAnchor;
        String key = openDropdownKey != null ? openDropdownKey : closingDropdownKey;
        if (value == null || anchor == null || key == null) {
            return;
        }
        String[] options = dropdownOptions(value);
        if (options.length == 0) {
            closeDropdown();
            return;
        }
        boolean opening = openDropdownKey != null;
        float target = opening ? 1.0f : 0.0f;
        float popupProgress = animations.animateFrom("value-popup:" + key, target,
                0.30f, frameScale, opening ? 0.0f : 1.0f);
        if (!opening && popupProgress <= 0.01f) {
            closingDropdownKey = null;
            closingDropdownValue = null;
            closingDropdownAnchor = null;
            return;
        }
        int visibleOptions = Math.min(options.length, 6);
        float fullHeight = visibleOptions * 24.0f + 8.0f;
        float popupHeight = Math.max(1.0f, fullHeight * popupProgress);
        float popupY = anchor.bottom() + 4.0f;
        if (popupY + fullHeight > clipBounds.bottom() - 4.0f) {
            popupY = anchor.y() - fullHeight - 4.0f;
        }
        float animatedY = popupY + (1.0f - popupProgress) * (opening ? 5.0f : -5.0f);
        TimewarpClickGuiGeometry.Rect popup = new TimewarpClickGuiGeometry.Rect(
                anchor.x(), animatedY, anchor.width(), popupHeight);
        RenderServices.shapes().shadow(popup.x(), popup.y(), popup.right(), popup.bottom(),
                6.0f, colors.shadow(Math.round(110.0f * popupProgress)), 8, 10.0f);
        RenderServices.shapes().rounded(popup.x(), popup.y(), popup.right(), popup.bottom(),
                6.0f, TimewarpClickGuiTheme.alpha(colors.window(), Math.round(245.0f * popupProgress)));
        String selected = dropdownCurrent(value);
        for (int index = 0; index < visibleOptions; index++) {
            float itemProgress = TimewarpClickGuiAnimation.stagger(popupProgress, index, visibleOptions);
            float y = popup.y() + 4.0f + index * 24.0f;
            TimewarpClickGuiGeometry.Rect item = new TimewarpClickGuiGeometry.Rect(
                    popup.x() + 4.0f, y, popup.width() - 8.0f, 22.0f);
            boolean active = options[index].equalsIgnoreCase(selected);
            boolean hovered = item.contains(mouseX, mouseY);
            if (active || hovered) {
                int itemColor = active ? colors.accentSoft() : colors.control();
                RenderServices.shapes().rounded(item.x(), item.y(), item.right(), item.bottom(),
                        4.0f, TimewarpClickGuiTheme.alpha(itemColor,
                                Math.round(220.0f * itemProgress * popupProgress)));
            }
            EpsilonPanelFonts.text(0.50f).drawString(options[index], item.x() + 7.0f,
                    EpsilonPanelFonts.centeredY(item.y(), item.height(), 0.50f),
                    TimewarpClickGuiTheme.alpha(active ? colors.text() : colors.secondary(),
                            Math.round(255.0f * itemProgress * popupProgress)));
        }
    }

    public boolean clickOpenDropdown(int mouseX, int mouseY, int button,
                                     TimewarpClickGuiGeometry.Rect clipBounds) {
        if (openDropdownKey == null || openDropdownValue == null || openDropdownAnchor == null) {
            return false;
        }
        if (button != 0) {
            return true;
        }
        String[] options = dropdownOptions(openDropdownValue);
        float popupHeight = Math.min(options.length, 6) * 24.0f + 8.0f;
        float popupY = openDropdownAnchor.bottom() + 4.0f;
        if (popupY + popupHeight > clipBounds.bottom() - 4.0f) {
            popupY = openDropdownAnchor.y() - popupHeight - 4.0f;
        }
        TimewarpClickGuiGeometry.Rect popup = new TimewarpClickGuiGeometry.Rect(
                openDropdownAnchor.x(), popupY, openDropdownAnchor.width(), popupHeight);
        if (popup.contains(mouseX, mouseY)) {
            int index = (int) ((mouseY - popup.y() - 4.0f) / 24.0f);
            if (index >= 0 && index < options.length && index < 6) {
                setDropdownValue(openDropdownValue, options[index]);
            }
        }
        closeDropdown();
        return true;
    }

    public boolean mouseClicked(Value<?> value, String key, TimewarpClickGuiGeometry.Rect row,
                                int mouseX, int mouseY, int button) {
        if (button != 0 || !row.contains(mouseX, mouseY)) {
            return false;
        }
        if (value instanceof Option && toggleBounds(row).contains(mouseX, mouseY)) {
            @SuppressWarnings("unchecked")
            Option<Boolean> option = (Option<Boolean>) value;
            option.setValue(!Boolean.TRUE.equals(option.getValue()));
            animations.snap("value-toggle-pulse:" + key, 0.0f);
            return true;
        }
        if (value instanceof Mode || value instanceof ModeProperty) {
            if (dropdownBounds(row).contains(mouseX, mouseY)) {
                if (key.equals(openDropdownKey)) {
                    closeDropdown();
                } else {
                    beginDropdown(key, value, dropdownBounds(row));
                }
                return true;
            }
        }
        if (value instanceof Numbers && !(value instanceof ModeProperty)) {
            TimewarpClickGuiGeometry.Rect slider = sliderBounds(row);
            if (slider.contains(mouseX, mouseY)) {
                draggedSliderKey = key;
                draggedSlider = (Numbers<?>) value;
                draggedSliderBounds = slider;
                updateSlider(mouseX);
                return true;
            }
        }
        return true;
    }

    public void updateDrag(int mouseX) {
        if (draggedSlider != null && draggedSliderBounds != null) {
            updateSlider(mouseX);
        }
    }

    public void mouseReleased() {
        draggedSliderKey = null;
        draggedSlider = null;
        draggedSliderBounds = null;
    }

    public boolean isDraggingSlider() { return draggedSlider != null; }
    public boolean isDropdownOpen() { return openDropdownKey != null || closingDropdownKey != null; }

    public void closeDropdown() {
        if (openDropdownKey != null) {
            closingDropdownKey = openDropdownKey;
            closingDropdownValue = openDropdownValue;
            closingDropdownAnchor = openDropdownAnchor;
            animations.snap("value-popup:" + closingDropdownKey, 1.0f);
        }
        openDropdownKey = null;
        openDropdownValue = null;
        openDropdownAnchor = null;
    }

    private void beginDropdown(String key, Value<?> value, TimewarpClickGuiGeometry.Rect anchor) {
        if (openDropdownKey != null && !openDropdownKey.equals(key)) {
            closeDropdown();
        }
        closingDropdownKey = null;
        closingDropdownValue = null;
        closingDropdownAnchor = null;
        openDropdownKey = key;
        openDropdownValue = value;
        openDropdownAnchor = anchor;
        animations.ensure("value-popup:" + key, 0.0f);
    }

    private void updateSlider(int mouseX) {
        double min = draggedSlider.getMinimum().doubleValue();
        double max = draggedSlider.getMaximum().doubleValue();
        double increment = Math.max(0.000001, draggedSlider.getIncrement().doubleValue());
        double ratio = clamp((mouseX - draggedSliderBounds.x()) / draggedSliderBounds.width());
        double value = min + (max - min) * ratio;
        value = Math.round((value - min) / increment) * increment + min;
        draggedSlider.setNumberValue(Math.max(min, Math.min(max, value)));
    }

    private static TimewarpClickGuiGeometry.Rect toggleBounds(TimewarpClickGuiGeometry.Rect row) {
        return new TimewarpClickGuiGeometry.Rect(row.right() - 31.0f,
                row.y() + 7.0f, 31.0f, 17.0f);
    }

    private static TimewarpClickGuiGeometry.Rect dropdownBounds(TimewarpClickGuiGeometry.Rect row) {
        return new TimewarpClickGuiGeometry.Rect(row.x(), row.y() + 3.0f,
                row.width(), 28.0f);
    }

    private static TimewarpClickGuiGeometry.Rect sliderBounds(TimewarpClickGuiGeometry.Rect row) {
        return new TimewarpClickGuiGeometry.Rect(row.x() + 16.0f, row.y() + 20.0f,
                row.width() - 16.0f, 12.0f);
    }

    private static String[] dropdownOptions(Value<?> value) {
        if (value instanceof ModeProperty) {
            return ((ModeProperty) value).getModes();
        }
        if (value instanceof Mode) {
            Object[] modes = ((Mode<?>) value).getModes();
            String[] result = new String[modes.length];
            for (int i = 0; i < modes.length; i++) {
                result[i] = enumName(modes[i]);
            }
            return result;
        }
        return new String[0];
    }

    private static String dropdownCurrent(Value<?> value) {
        if (value instanceof ModeProperty) {
            return ((ModeProperty) value).getModeString();
        }
        return enumName(value.getValue());
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void setDropdownValue(Value<?> value, String selected) {
        if (value instanceof ModeProperty) {
            ((ModeProperty) value).setMode(selected);
        } else if (value instanceof Mode) {
            ((Mode) value).setMode(selected);
        }
    }

    private static String enumName(Object value) {
        if (value instanceof Enum) {
            return ((Enum<?>) value).name();
        }
        return String.valueOf(value);
    }

    private static String formatNumber(double value) {
        if (Math.abs(value - Math.rint(value)) < 0.0001) {
            return Integer.toString((int) Math.rint(value));
        }
        return String.format(Locale.ROOT, "%.2f", value);
    }

    private static float clamp(float value) {
        return Math.max(0.0f, Math.min(1.0f, value));
    }
}
