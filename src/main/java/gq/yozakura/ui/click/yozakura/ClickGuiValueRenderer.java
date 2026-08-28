package gq.yozakura.ui.click.yozakura;

import gq.yozakura.engine.font.CFontRenderer;
import gq.yozakura.engine.font.FontLoaders;
import gq.yozakura.engine.render.GLStateManager;
import gq.yozakura.engine.render.ui.RenderServices;
import gq.yozakura.util.animation.AnimationState;
import gq.yozakura.value.Mode;
import gq.yozakura.value.Numbers;
import gq.yozakura.value.Option;
import gq.yozakura.value.Value;
import gq.yozakura.value.properties.ModeProperty;
import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;

import java.util.Locale;

/**
 * Renders and handles interactions for the value controls shown in the
 * module settings expand panel: dropdowns ({@link Mode}), sliders
 * ({@link Numbers}), toggles ({@link Option}), and inline color swatches.
 *
 * <p>State is held per-GUI (one open dropdown, one dragged slider at a time)
 * via a shared {@link InteractionState} instance.
 */
public final class ClickGuiValueRenderer {
    /** Shared per-GUI interaction state for value controls. */
    public static final class InteractionState {
        public String openDropdownKey;
        public String closingDropdownKey;
        /** Internal popup scroll offset, Epsilon EnumSelectPopup semantics. */
        public float dropdownScroll;
        public String draggedSliderKey;
        public String focusedNumberKey;
        public String numberDraft;
        public int numberCursor;
        public String hoveredKey;
        public String keybindTargetKey;
        /** Panel surface used to clamp and vertically flip enum popups. */
        public PanelClickGuiLayout.Rect popupBounds;
        public float popupInset = 3.0f;
    }

    private static final float LABEL_W = 84f;
    private static final float LABEL_MAX_W = 112f;
    private static final float CONTROL_MIN_W = 121f;
    private static final float MIN_LABEL_SCALE = 0.52f;
    // Epsilon Panel setting rhythm: 28px content + the MD3 3px row gap.
    private static final float ROW_H = 28f;
    private static final float ROW_GAP = 3f;
    private static final float DROPDOWN_ROW_H = 24f;

    private final InteractionState state;
    private final AnimationState anim;
    private final EpsilonPanelAnimation.State epsilonAnimations = new EpsilonPanelAnimation.State();
    private long frameTimeMillis;

    public ClickGuiValueRenderer(InteractionState state, AnimationState anim) {
        this.state = state;
        this.anim = anim;
    }

    public void beginFrame(long frameTimeMillis) {
        this.frameTimeMillis = frameTimeMillis;
    }

    /**
     * Renders a single value row. Returns the height consumed (row + gap).
     */
    public float drawValue(Value<?> value, String key, float x, float y, float w,
                           int mouseX, int mouseY, float frameScale, boolean expanded) {
        if (!value.isVisible()) {
            return 0f;
        }
        boolean hovered = mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + ROW_H;
        if (hovered) {
            state.hoveredKey = key;
        }
        String label = value.getDisplayName();
        float labelWidth = labelWidth(w);
        float availableLabelWidth = Math.max(1.0f, labelWidth - 6.0f);
        float labelScale = labelScaleForWidth(label, availableLabelWidth);
        GLStateManager.pushScissor(x, y, availableLabelWidth, ROW_H);
        try {
            EpsilonPanelFonts.text(labelScale).drawString(label, x,
                    EpsilonPanelFonts.centeredY(y, ROW_H, labelScale),
                    PanelClickGuiPalette.textSecondary());
        } finally {
            GLStateManager.popScissor();
        }

        float controlX = x + labelWidth;
        float controlW = w - labelWidth;

        if (value instanceof Mode) {
            drawMode((Mode<?>) value, key, controlX, y, controlW, mouseX, mouseY, frameScale, expanded);
        } else if (value instanceof ModeProperty) {
            drawModeProperty((ModeProperty) value, key, controlX, y, controlW,
                    mouseX, mouseY, frameScale, expanded);
        } else if (value instanceof Numbers) {
            drawNumbers((Numbers<?>) value, key, controlX, y, controlW, mouseX, mouseY, frameScale);
        } else if (value instanceof Option) {
            drawOption((Option<?>) value, key, controlX, y, controlW, mouseX, mouseY, frameScale);
        } else {
            drawUnknown(value, controlX, y, controlW);
        }
        return ROW_H + ROW_GAP;
    }

    public float rowHeight() {
        return ROW_H + ROW_GAP;
    }

    private float labelScaleForWidth(String label, float labelWidth) {
        float preferred = EpsilonPanelMetrics.SETTING_LABEL_SCALE;
        float textWidth = EpsilonPanelFonts.text(preferred).getStringWidth(label);
        if (textWidth <= labelWidth || textWidth <= 0.0f) {
            return preferred;
        }
        return Math.max(MIN_LABEL_SCALE, preferred * labelWidth / textWidth);
    }

    private float labelWidth(float rowWidth) {
        return Math.max(LABEL_W, Math.min(LABEL_MAX_W, rowWidth - CONTROL_MIN_W));
    }

    // ===== Mode (dropdown) =====

    private <T extends Enum<T>> void drawMode(Mode<T> mode, String key, float x, float y, float w,
                                              int mouseX, int mouseY, float frameScale, boolean expanded) {
        boolean open = key.equals(state.openDropdownKey) && expanded;
        boolean closing = key.equals(state.closingDropdownKey);
        T current = mode.getValue();
        String display = current == null ? "-" : current.toString();
        PanelClickGuiLayout.Rect chip = modeChipBounds(display, x, y, w);

        // Epsilon EnumSettingRow uses a text-fit assist chip: 16px high,
        // 96px maximum width, 5px trailing inset, and no outline.
        RenderServices.shapes().roundedWH(chip.x(), chip.y(), chip.width(), chip.height(),
                EpsilonPanelMetrics.CONTROL_RADIUS, PanelClickGuiPalette.selected());
        EpsilonPanelFonts.text(EpsilonPanelMetrics.FIELD_TEXT_SCALE).drawString(display,
                chip.x() + PanelClickGuiEnumPopup.CHIP_HORIZONTAL_PADDING,
                EpsilonPanelFonts.centeredY(chip.y(), chip.height(),
                        EpsilonPanelMetrics.FIELD_TEXT_SCALE),
                0xFFE8DEF8);

        long now = frameTimeMillis;
        float chevronProgress = epsilonAnimations.value("enum-chevron:" + key,
                open ? 1.0f : 0.0f, now, EpsilonPanelAnimation.ENUM_CHEVRON_MS,
                gq.yozakura.util.animation.AnimationUtil.Ease.OUT_CUBIC);
        drawChevronTriangle(chip.right() - 7.5f, chip.y() + chip.height() * 0.5f,
                3.0f, chevronProgress, 0xFFE8DEF8);

        if (open || closing) {
            T[] modes = mode.getModes();
            int count = modes.length;
            // Epsilon EnumSelectPopup: at most five 24px rows plus 6px padding;
            // extra options scroll internally instead of growing the popup.
            PanelClickGuiLayout.Rect menu = popupPlacement(chip, count);
            float menuX = menu.x();
            float menuW = menu.width();
            float menuH = menu.height();
            float scroll = PanelClickGuiEnumPopup.isScrollable(count)
                    ? Math.min(state.dropdownScroll, PanelClickGuiEnumPopup.maxScroll(count))
                    : 0f;
            float popupTarget = open ? 1.0f : 0.0f;
            float openProgress = epsilonAnimations.value("popup-open:" + key, popupTarget, now,
                    EpsilonPanelAnimation.POPUP_OPEN_MS,
                    gq.yozakura.util.animation.AnimationUtil.Ease.OUT_CUBIC);
            if (closing && openProgress <= 0.001f) {
                state.closingDropdownKey = null;
                return;
            }
            float animatedMenuY = menu.y() - (1.0f - openProgress) * 6.0f;

            // Epsilon animates the popup as one surface. Individual options do not
            // stagger, pulse, or slide horizontally.
            RenderServices.shapes().shadow(menuX, animatedMenuY, menuX + menuW,
                    animatedMenuY + menuH, 9.0f, 0x70000000, 10, 14f);
            RenderServices.shapes().roundedWH(menuX, animatedMenuY, menuW, menuH,
                    ClickGuiTheme.R_SM, PanelClickGuiPalette.raised());
            RenderServices.shapes().roundedBorderWH(menuX, animatedMenuY, menuW, menuH,
                    ClickGuiTheme.R_SM, 1f, PanelClickGuiPalette.raised(), PanelClickGuiPalette.border());

            float optY = animatedMenuY + PanelClickGuiEnumPopup.CONTENT_PADDING - scroll;
            GLStateManager.pushScissor(menuX, animatedMenuY + 1f, menuW, menuH - 2f);
            try {
                for (int i = 0; i < count; i++) {
                    T opt = modes[i];
                    float oy = optY + i * PanelClickGuiEnumPopup.ITEM_HEIGHT;
                    if (oy + PanelClickGuiEnumPopup.ITEM_HEIGHT < animatedMenuY
                            || oy > animatedMenuY + menuH) {
                        continue;
                    }
                    boolean sel = opt.equals(current);
                    float innerY = oy;
                    boolean optHover = mouseX >= menuX + PanelClickGuiEnumPopup.CONTENT_PADDING
                            && mouseX <= menuX + menuW - PanelClickGuiEnumPopup.CONTENT_PADDING
                            && mouseY >= Math.max(innerY, animatedMenuY)
                            && mouseY <= Math.min(innerY + PanelClickGuiEnumPopup.ITEM_INNER_HEIGHT,
                            animatedMenuY + menuH);
                    float itemX = menuX + PanelClickGuiEnumPopup.CONTENT_PADDING;
                    float itemW = menuW - PanelClickGuiEnumPopup.CONTENT_PADDING * 2.0f;
                    if (sel) {
                        RenderServices.shapes().roundedWH(itemX, innerY, itemW,
                                PanelClickGuiEnumPopup.ITEM_INNER_HEIGHT,
                                ClickGuiTheme.R_XS, ClickGuiTheme.accentDim());
                        EpsilonPanelFonts.icons(EpsilonPanelMetrics.FIELD_TEXT_SCALE).drawString(
                                EpsilonPanelIcons.CHECK,
                                itemX + PanelClickGuiEnumPopup.TEXT_OFFSET_UNSELECTED,
                                EpsilonPanelFonts.centeredY(innerY,
                                        PanelClickGuiEnumPopup.ITEM_INNER_HEIGHT,
                                        EpsilonPanelMetrics.FIELD_TEXT_SCALE),
                                ClickGuiTheme.accentHover());
                    } else if (optHover) {
                        RenderServices.shapes().roundedWH(itemX, innerY, itemW,
                                PanelClickGuiEnumPopup.ITEM_INNER_HEIGHT,
                                ClickGuiTheme.R_XS, PanelClickGuiPalette.overlay());
                    }
                    int color = sel ? ClickGuiTheme.accentHover()
                            : (optHover ? PanelClickGuiPalette.textPrimary() : PanelClickGuiPalette.textSecondary());
                    float textOffset = sel ? PanelClickGuiEnumPopup.TEXT_OFFSET_SELECTED
                            : PanelClickGuiEnumPopup.TEXT_OFFSET_UNSELECTED;
                    EpsilonPanelFonts.text(EpsilonPanelMetrics.FIELD_TEXT_SCALE).drawString(
                            opt.toString(), itemX + textOffset,
                            EpsilonPanelFonts.centeredY(innerY,
                                    PanelClickGuiEnumPopup.ITEM_INNER_HEIGHT,
                                    EpsilonPanelMetrics.FIELD_TEXT_SCALE), color);
                }
            } finally {
                GLStateManager.popScissor();
            }
        }
    }

    private void drawModeProperty(ModeProperty mode, String key, float x, float y, float w,
                                  int mouseX, int mouseY, float frameScale, boolean expanded) {
        boolean open = key.equals(state.openDropdownKey) && expanded;
        boolean closing = key.equals(state.closingDropdownKey);
        String display = mode.getModeString();
        if (display.isEmpty()) {
            display = "-";
        }
        PanelClickGuiLayout.Rect chip = modeChipBounds(display, x, y, w);

        RenderServices.shapes().roundedWH(chip.x(), chip.y(), chip.width(), chip.height(),
                EpsilonPanelMetrics.CONTROL_RADIUS, PanelClickGuiPalette.selected());
        EpsilonPanelFonts.text(EpsilonPanelMetrics.FIELD_TEXT_SCALE).drawString(display,
                chip.x() + PanelClickGuiEnumPopup.CHIP_HORIZONTAL_PADDING,
                EpsilonPanelFonts.centeredY(chip.y(), chip.height(),
                        EpsilonPanelMetrics.FIELD_TEXT_SCALE),
                0xFFE8DEF8);

        long now = frameTimeMillis;
        float chevronProgress = epsilonAnimations.value("enum-chevron:" + key,
                open ? 1.0f : 0.0f, now, EpsilonPanelAnimation.ENUM_CHEVRON_MS,
                gq.yozakura.util.animation.AnimationUtil.Ease.OUT_CUBIC);
        drawChevronTriangle(chip.right() - 7.5f, chip.y() + chip.height() * 0.5f,
                3.0f, chevronProgress, 0xFFE8DEF8);

        if (open || closing) {
            String[] modes = mode.getModes();
            int count = modes.length;
            PanelClickGuiLayout.Rect menu = popupPlacement(chip, count);
            float menuX = menu.x();
            float menuW = menu.width();
            float menuH = menu.height();
            float scroll = PanelClickGuiEnumPopup.isScrollable(count)
                    ? Math.min(state.dropdownScroll, PanelClickGuiEnumPopup.maxScroll(count))
                    : 0f;
            float popupTarget = open ? 1.0f : 0.0f;
            float openProgress = epsilonAnimations.value("popup-open:" + key, popupTarget, now,
                    EpsilonPanelAnimation.POPUP_OPEN_MS,
                    gq.yozakura.util.animation.AnimationUtil.Ease.OUT_CUBIC);
            if (closing && openProgress <= 0.001f) {
                state.closingDropdownKey = null;
                return;
            }
            float animatedMenuY = menu.y() - (1.0f - openProgress) * 6.0f;

            RenderServices.shapes().shadow(menuX, animatedMenuY, menuX + menuW,
                    animatedMenuY + menuH, 9.0f, 0x70000000, 10, 14f);
            RenderServices.shapes().roundedWH(menuX, animatedMenuY, menuW, menuH,
                    ClickGuiTheme.R_SM, PanelClickGuiPalette.raised());
            RenderServices.shapes().roundedBorderWH(menuX, animatedMenuY, menuW, menuH,
                    ClickGuiTheme.R_SM, 1f, PanelClickGuiPalette.raised(), PanelClickGuiPalette.border());

            float optY = animatedMenuY + PanelClickGuiEnumPopup.CONTENT_PADDING - scroll;
            GLStateManager.pushScissor(menuX, animatedMenuY + 1f, menuW, menuH - 2f);
            try {
                for (int i = 0; i < count; i++) {
                    String option = modes[i];
                    float oy = optY + i * PanelClickGuiEnumPopup.ITEM_HEIGHT;
                    if (oy + PanelClickGuiEnumPopup.ITEM_HEIGHT < animatedMenuY
                            || oy > animatedMenuY + menuH) {
                        continue;
                    }
                    boolean selected = option.equalsIgnoreCase(mode.getModeString());
                    boolean optionHovered = mouseX >= menuX + PanelClickGuiEnumPopup.CONTENT_PADDING
                            && mouseX <= menuX + menuW - PanelClickGuiEnumPopup.CONTENT_PADDING
                            && mouseY >= Math.max(oy, animatedMenuY)
                            && mouseY <= Math.min(oy + PanelClickGuiEnumPopup.ITEM_INNER_HEIGHT,
                            animatedMenuY + menuH);
                    float itemX = menuX + PanelClickGuiEnumPopup.CONTENT_PADDING;
                    float itemW = menuW - PanelClickGuiEnumPopup.CONTENT_PADDING * 2.0f;
                    if (selected) {
                        RenderServices.shapes().roundedWH(itemX, oy, itemW,
                                PanelClickGuiEnumPopup.ITEM_INNER_HEIGHT,
                                ClickGuiTheme.R_XS, ClickGuiTheme.accentDim());
                        EpsilonPanelFonts.icons(EpsilonPanelMetrics.FIELD_TEXT_SCALE).drawString(
                                EpsilonPanelIcons.CHECK,
                                itemX + PanelClickGuiEnumPopup.TEXT_OFFSET_UNSELECTED,
                                EpsilonPanelFonts.centeredY(oy,
                                        PanelClickGuiEnumPopup.ITEM_INNER_HEIGHT,
                                        EpsilonPanelMetrics.FIELD_TEXT_SCALE),
                                ClickGuiTheme.accentHover());
                    } else if (optionHovered) {
                        RenderServices.shapes().roundedWH(itemX, oy, itemW,
                                PanelClickGuiEnumPopup.ITEM_INNER_HEIGHT,
                                ClickGuiTheme.R_XS, PanelClickGuiPalette.overlay());
                    }
                    int color = selected ? ClickGuiTheme.accentHover()
                            : (optionHovered ? PanelClickGuiPalette.textPrimary()
                            : PanelClickGuiPalette.textSecondary());
                    float textOffset = selected ? PanelClickGuiEnumPopup.TEXT_OFFSET_SELECTED
                            : PanelClickGuiEnumPopup.TEXT_OFFSET_UNSELECTED;
                    EpsilonPanelFonts.text(EpsilonPanelMetrics.FIELD_TEXT_SCALE).drawString(
                            option, itemX + textOffset,
                            EpsilonPanelFonts.centeredY(oy,
                                    PanelClickGuiEnumPopup.ITEM_INNER_HEIGHT,
                                    EpsilonPanelMetrics.FIELD_TEXT_SCALE), color);
                }
            } finally {
                GLStateManager.popScissor();
            }
        }
    }

    // ===== Numbers (slider) =====

    private void drawNumbers(Numbers<?> num, String key, float x, float y, float w,
                             int mouseX, int mouseY, float frameScale) {
        double value = num.getValue().doubleValue();
        double minimum = num.getMinimum().doubleValue();
        double maximum = num.getMaximum().doubleValue();
        float targetProgress = (float) ((value - minimum) / Math.max(0.0001, maximum - minimum));
        targetProgress = Math.max(0.0f, Math.min(1.0f, targetProgress));
        long now = frameTimeMillis;
        float progress = epsilonAnimations.value("slider-indicator:" + key, targetProgress,
                now, EpsilonPanelAnimation.SLIDER_INDICATOR_MS,
                gq.yozakura.util.animation.AnimationUtil.Ease.OUT_QUART);
        progress = Math.max(0.0f, Math.min(1.0f, progress));

        PanelClickGuiLayout.Rect row = new PanelClickGuiLayout.Rect(x, y, w, ROW_H);
        PanelClickGuiLayout.Rect track = EpsilonPanelGeometry.numberTrack(row);
        PanelClickGuiLayout.Rect field = EpsilonPanelGeometry.numberField(row);
        PanelClickGuiLayout.Rect interactive = EpsilonPanelGeometry.numberInteractive(row);
        boolean dragging = key.equals(state.draggedSliderKey);
        boolean hovered = interactive.contains(mouseX, mouseY);
        float hoverProgress = epsilonAnimations.value("slider-hover:" + key,
                dragging || hovered ? 1.0f : 0.0f, now,
                EpsilonPanelAnimation.SLIDER_HOVER_MS,
                gq.yozakura.util.animation.AnimationUtil.Ease.OUT_QUART);
        float pressProgress = epsilonAnimations.value("slider-press:" + key,
                dragging ? 1.0f : 0.0f, now,
                EpsilonPanelAnimation.SLIDER_PRESS_MS,
                gq.yozakura.util.animation.AnimationUtil.Ease.OUT_CUBIC);

        int trackColor = ClickGuiTheme.blend(PanelClickGuiPalette.overlay(),
                0xF835333B, hoverProgress);
        RenderServices.shapes().rounded(track.x(), track.y(), track.right(), track.bottom(),
                3.0f, trackColor);
        float indicatorX = track.x() + track.width() * progress;
        if (indicatorX > track.x()) {
            RenderServices.shapes().rounded(track.x(), track.y(), indicatorX, track.bottom(),
                    3.0f, ClickGuiTheme.blend(PanelClickGuiPalette.accent(),
                            ClickGuiTheme.accentHover(), hoverProgress));
        }
        float handleWidth = Math.max(0.0f, 2.0f - pressProgress * 2.0f);
        float handleHeight = 14.0f;
        if (handleWidth > 0.01f) {
            float handleTop = track.y() + (track.height() - handleHeight) * 0.5f;
            RenderServices.shapes().rounded(indicatorX - handleWidth * 0.5f, handleTop,
                    indicatorX + handleWidth * 0.5f, handleTop + handleHeight,
                    handleWidth * 0.5f,
                    ClickGuiTheme.blend(PanelClickGuiPalette.textPrimary(),
                            ClickGuiTheme.accentHover(), hoverProgress));
        }

        boolean editing = key.equals(state.focusedNumberKey);
        String text = editing && state.numberDraft != null
                ? state.numberDraft : formatNumber(value, num);
        if (pressProgress > 0.01f) {
            CFontRenderer bubbleFont = EpsilonPanelFonts.text(EpsilonPanelMetrics.BUBBLE_TEXT_SCALE);
            float bubbleWidth = bubbleFont.getStringWidth(text) + 16.0f;
            float bubbleHeight = 18.0f;
            float bubbleX = indicatorX - bubbleWidth * 0.5f;
            float bubbleY = row.y() - 22.0f;
            RenderServices.shapes().shadow(bubbleX, bubbleY, bubbleX + bubbleWidth,
                    bubbleY + bubbleHeight, EpsilonPanelMetrics.CONTROL_RADIUS,
                    ClickGuiTheme.withAlpha(0xFF000000, (int) (96.0f * pressProgress)), 6, 8.0f);
            RenderServices.shapes().rounded(bubbleX, bubbleY, bubbleX + bubbleWidth,
                    bubbleY + bubbleHeight, EpsilonPanelMetrics.CONTROL_RADIUS,
                    ClickGuiTheme.withAlpha(PanelClickGuiPalette.selected(),
                            (int) (ClickGuiTheme.alphaOf(PanelClickGuiPalette.selected()) * pressProgress)));
            EpsilonPanelFonts.drawCenteredText(text, indicatorX, bubbleY, bubbleHeight,
                    EpsilonPanelMetrics.BUBBLE_TEXT_SCALE,
                    ClickGuiTheme.withAlpha(PanelClickGuiPalette.textPrimary(),
                            (int) (255.0f * pressProgress)));
        }

        float fieldFocus = editing ? 1.0f : pressProgress;
        int fieldBackground = ClickGuiTheme.blend(PanelClickGuiPalette.raised(),
                ClickGuiTheme.accentDim(), fieldFocus);
        int fieldBorder = ClickGuiTheme.blend(PanelClickGuiPalette.border(),
                PanelClickGuiPalette.accent(), Math.max(hoverProgress * 0.65f, fieldFocus));
        RenderServices.shapes().roundedBorderWH(field.x(), field.y(), field.width(), field.height(),
                ClickGuiTheme.R_XS, 1.0f, fieldBackground, fieldBorder);
        GLStateManager.pushScissor(field.x() + 2.0f, field.y(), field.width() - 4.0f, field.height());
        try {
            EpsilonPanelFonts.drawCenteredText(text, field.x() + field.width() * 0.5f,
                    field.y(), field.height(), EpsilonPanelMetrics.FIELD_TEXT_SCALE,
                    ClickGuiTheme.blend(PanelClickGuiPalette.textSecondary(),
                            ClickGuiTheme.accentHover(), fieldFocus));
            if (editing && ((frameTimeMillis / 500L) & 1L) == 0L) {
                CFontRenderer fieldFont = EpsilonPanelFonts.text(EpsilonPanelMetrics.FIELD_TEXT_SCALE);
                String beforeCursor = text.substring(0, Math.min(state.numberCursor, text.length()));
                float textLeft = field.x() + (field.width() - fieldFont.getStringWidth(text)) * 0.5f;
                float caretX = textLeft + fieldFont.getStringWidth(beforeCursor);
                RenderServices.shapes().rect(caretX, field.y() + 4.0f,
                        caretX + 1.0f, field.bottom() - 4.0f, PanelClickGuiPalette.accent());
            }
        } finally {
            GLStateManager.popScissor();
        }
    }

    // ===== Option (toggle) =====

    @SuppressWarnings("unchecked")
    private void drawOption(Option<?> option, String key, float x, float y, float w,
                            int mouseX, int mouseY, float frameScale) {
        Object raw = option.getValue();
        boolean on = raw instanceof Boolean && ((Boolean) raw);
        PanelClickGuiLayout.Rect row = new PanelClickGuiLayout.Rect(x, y, w, ROW_H);
        PanelClickGuiLayout.Rect toggle = EpsilonPanelGeometry.optionSwitch(row);
        float toggleW = toggle.width();
        float toggleH = toggle.height();
        float toggleX = toggle.x();
        float toggleY = toggle.y();
        boolean hovered = toggle.contains(mouseX, mouseY);
        long now = frameTimeMillis;
        float onTarget = on ? 1f : 0f;
        float onNow = epsilonAnimations.value("tg-on:" + key, onTarget,
                now, EpsilonPanelAnimation.TOGGLE_MS,
                gq.yozakura.util.animation.AnimationUtil.Ease.OUT_ELASTIC);
        float hoverNow = epsilonAnimations.value("tg-hover:" + key, hovered ? 1.0f : 0.0f,
                now, EpsilonPanelAnimation.TOGGLE_HOVER_MS,
                gq.yozakura.util.animation.AnimationUtil.Ease.OUT_CUBIC);
        onNow = Math.max(0.0f, Math.min(1.0f, onNow));

        // Background track — accent gradient when on
        int bgBlend = ClickGuiTheme.blend(PanelClickGuiPalette.overlay(), PanelClickGuiPalette.accent(), onNow);
        int border = on ? ClickGuiTheme.withAlpha(PanelClickGuiPalette.accent(), 0x59) : PanelClickGuiPalette.border();
        border = ClickGuiTheme.blend(PanelClickGuiPalette.border(), border, onNow);
        RenderServices.shapes().roundedBorderWH(toggleX, toggleY, toggleW, toggleH, ClickGuiTheme.R_CAPSULE,
                1f, bgBlend, border);
        // Inner top highlight when on (subtle glass effect, matches inset 0 1px 0 rgba(255,255,255,.15))
        if (onNow > 0.05f) {
            RenderServices.shapes().horizontalGradient(toggleX + 4f, toggleY + 1f,
                    toggleX + toggleW - 4f, toggleY + 2f,
                    ClickGuiTheme.withAlpha(0xFFFFFF, 0x00),
                    ClickGuiTheme.withAlpha(0xFFFFFF, (int) (0x26 * onNow)));
            RenderServices.shapes().horizontalGradient(toggleX + toggleW / 2f, toggleY + 1f,
                    toggleX + toggleW - 4f, toggleY + 2f,
                    ClickGuiTheme.withAlpha(0xFFFFFF, (int) (0x26 * onNow)),
                    ClickGuiTheme.withAlpha(0xFFFFFF, 0x00));
        }
        float handleSize = 8f + 4f * onNow;
        float inset = 4f - 2f * onNow;
        float handleX = toggleX + inset + onNow * (toggleW - handleSize - inset * 2f);
        float handleY = toggleY + (toggleH - handleSize) * 0.5f;
        int handleColor = ClickGuiTheme.blend(PanelClickGuiPalette.textMuted(), 0xFFFFFFFF, onNow);
        if (hoverNow > 0.01f) {
            RenderServices.shapes().circle(handleX + handleSize * 0.5f,
                    handleY + handleSize * 0.5f, 0, 360, 10f,
                    ClickGuiTheme.withAlpha(handleColor, (int) (0x1E * hoverNow)));
        }
        RenderServices.shapes().circle(handleX + handleSize * 0.5f,
                handleY + handleSize * 0.5f, 0, 360, handleSize * 0.5f, handleColor);
    }

    private void drawUnknown(Value<?> value, float x, float y, float w) {
        Object v = value.getValue();
        String text = v == null ? "-" : v.toString();
        FontLoaders.epsilonPanel(10).drawString(text, x + 4, y + 8, PanelClickGuiPalette.textSecondary());
    }

    // ===== Interaction =====

    public boolean mouseClicked(Value<?> value, String key, float x, float y, float w,
                                int mouseX, int mouseY, int button) {
        if (!value.isVisible()) {
            return false;
        }
        if (button != 0) {
            return false;
        }
        float labelWidth = labelWidth(w);
        float controlX = x + labelWidth;
        float controlW = w - labelWidth;

        if (value instanceof Mode) {
            return clickMode((Mode<?>) value, key, controlX, y, controlW, mouseX, mouseY, button);
        }
        if (value instanceof ModeProperty) {
            return clickModeProperty((ModeProperty) value, key, controlX, y, controlW,
                    mouseX, mouseY, button);
        }
        if (value instanceof Numbers) {
            return clickNumbers((Numbers<?>) value, key, controlX, y, controlW, mouseX, mouseY, button);
        }
        if (value instanceof Option) {
            return clickOption((Option<?>) value, key, controlX, y, controlW, mouseX, mouseY, button);
        }
        return false;
    }

    private <T extends Enum<T>> boolean clickMode(Mode<T> mode, String key, float x, float y, float w,
                                                  int mouseX, int mouseY, int button) {
        boolean open = key.equals(state.openDropdownKey);
        T current = mode.getValue();
        String display = current == null ? "-" : current.toString();
        PanelClickGuiLayout.Rect chip = modeChipBounds(display, x, y, w);
        // Trigger click uses the exact assist-chip bounds used for drawing.
        if (chip.contains(mouseX, mouseY)) {
            if (open) {
                requestDropdownClose();
            } else {
                state.closingDropdownKey = null;
                state.openDropdownKey = key;
                state.dropdownScroll = 0f;
                epsilonAnimations.snap("popup-open:" + key, 0.0f);
            }
            return true;
        }
        if (!open) {
            return false;
        }
        // Option click — Epsilon EnumSelectPopup hit test: five-item viewport,
        // scrolled content, click selects and closes (shouldCloseAfterClick).
        T[] modes = mode.getModes();
        int count = modes.length;
        PanelClickGuiLayout.Rect menu = popupPlacement(chip, count);
        float openProgress = epsilonAnimations.value("popup-open:" + key, 1.0f,
                frameTimeMillis, EpsilonPanelAnimation.POPUP_OPEN_MS,
                gq.yozakura.util.animation.AnimationUtil.Ease.OUT_CUBIC);
        float animatedMenuY = menu.y() - (1.0f - openProgress) * 6.0f;
        float scroll = PanelClickGuiEnumPopup.isScrollable(count)
                ? Math.min(state.dropdownScroll, PanelClickGuiEnumPopup.maxScroll(count))
                : 0f;
        if (mouseX >= menu.x() + PanelClickGuiEnumPopup.CONTENT_PADDING
                && mouseX <= menu.right() - PanelClickGuiEnumPopup.CONTENT_PADDING
                && mouseY >= animatedMenuY && mouseY <= animatedMenuY + menu.height()) {
            int index = PanelClickGuiEnumPopup.itemIndexAt(mouseY - animatedMenuY, scroll, count);
            if (index >= 0) {
                mode.setValue(modes[index]);
                requestDropdownClose();
                return true;
            }
            // Padding strip inside the popup — swallow without closing.
            return true;
        }
        // Click outside menu — close
        requestDropdownClose();
        return false;
    }

    private boolean clickModeProperty(ModeProperty mode, String key, float x, float y, float w,
                                      int mouseX, int mouseY, int button) {
        boolean open = key.equals(state.openDropdownKey);
        String display = mode.getModeString();
        if (display.isEmpty()) {
            display = "-";
        }
        PanelClickGuiLayout.Rect chip = modeChipBounds(display, x, y, w);
        if (chip.contains(mouseX, mouseY)) {
            if (open) {
                requestDropdownClose();
            } else {
                state.closingDropdownKey = null;
                state.openDropdownKey = key;
                state.dropdownScroll = 0f;
                epsilonAnimations.snap("popup-open:" + key, 0.0f);
            }
            return true;
        }
        if (!open) {
            return false;
        }
        String[] modes = mode.getModes();
        int count = modes.length;
        PanelClickGuiLayout.Rect menu = popupPlacement(chip, count);
        float openProgress = epsilonAnimations.value("popup-open:" + key, 1.0f,
                frameTimeMillis, EpsilonPanelAnimation.POPUP_OPEN_MS,
                gq.yozakura.util.animation.AnimationUtil.Ease.OUT_CUBIC);
        float animatedMenuY = menu.y() - (1.0f - openProgress) * 6.0f;
        float scroll = PanelClickGuiEnumPopup.isScrollable(count)
                ? Math.min(state.dropdownScroll, PanelClickGuiEnumPopup.maxScroll(count))
                : 0f;
        if (mouseX >= menu.x() + PanelClickGuiEnumPopup.CONTENT_PADDING
                && mouseX <= menu.right() - PanelClickGuiEnumPopup.CONTENT_PADDING
                && mouseY >= animatedMenuY && mouseY <= animatedMenuY + menu.height()) {
            int index = PanelClickGuiEnumPopup.itemIndexAt(mouseY - animatedMenuY, scroll, count);
            if (index >= 0) {
                mode.setMode(modes[index]);
                requestDropdownClose();
                return true;
            }
            return true;
        }
        requestDropdownClose();
        return false;
    }

    /**
     * Routes a wheel event into the open enum popup. Epsilon:
     * {@code scroll - scrollY * 20} clamped to the content overflow.
     * Returns true when the popup consumed the event.
     */
    public boolean mouseScrolledOpenDropdown(Mode<?> mode, String key, float scrollY) {
        return mouseScrolledOpenDropdown(mode.getModes().length, key, scrollY);
    }

    public boolean mouseScrolledOpenDropdown(ModeProperty mode, String key, float scrollY) {
        return mouseScrolledOpenDropdown(mode.getModes().length, key, scrollY);
    }

    private boolean mouseScrolledOpenDropdown(int count, String key, float scrollY) {
        if (!key.equals(state.openDropdownKey)) {
            return false;
        }
        if (!PanelClickGuiEnumPopup.isScrollable(count)) {
            return true;
        }
        state.dropdownScroll = PanelClickGuiEnumPopup.scrollAfterWheel(state.dropdownScroll, scrollY, count);
        return true;
    }

    private boolean clickNumbers(Numbers<?> num, String key, float x, float y, float w,
                                 int mouseX, int mouseY, int button) {
        PanelClickGuiLayout.Rect row = new PanelClickGuiLayout.Rect(x, y, w, ROW_H);
        PanelClickGuiLayout.Rect field = EpsilonPanelGeometry.numberField(row);
        if (field.contains(mouseX, mouseY)) {
            if (!key.equals(state.focusedNumberKey)) {
                beginNumberEditing(num, key);
            }
            return true;
        }
        if (state.focusedNumberKey != null) {
            cancelNumberEditing();
        }
        PanelClickGuiLayout.Rect interactive = EpsilonPanelGeometry.numberInteractive(row);
        if (interactive.contains(mouseX, mouseY)) {
            state.draggedSliderKey = key;
            updateSliderFromMouse(num, EpsilonPanelGeometry.numberTrack(row), mouseX);
            return true;
        }
        return false;
    }

    private void beginNumberEditing(Numbers<?> num, String key) {
        state.draggedSliderKey = null;
        state.focusedNumberKey = key;
        state.numberDraft = formatNumber(num.getValue().doubleValue(), num);
        state.numberCursor = state.numberDraft.length();
    }

    public boolean keyTypedNumber(Numbers<?> num, String key, char typedChar, int keyCode) {
        if (!key.equals(state.focusedNumberKey)) {
            return false;
        }
        if (keyCode == Keyboard.KEY_RETURN || keyCode == Keyboard.KEY_NUMPADENTER) {
            commitNumberEditing(num, key);
            return true;
        }
        if (keyCode == Keyboard.KEY_ESCAPE) {
            cancelNumberEditing();
            return true;
        }
        String draft = state.numberDraft == null ? "" : state.numberDraft;
        int cursor = Math.max(0, Math.min(state.numberCursor, draft.length()));
        if (keyCode == Keyboard.KEY_BACK) {
            if (cursor > 0) {
                state.numberDraft = draft.substring(0, cursor - 1) + draft.substring(cursor);
                state.numberCursor = cursor - 1;
            }
            return true;
        }
        if (keyCode == Keyboard.KEY_DELETE) {
            if (cursor < draft.length()) {
                state.numberDraft = draft.substring(0, cursor) + draft.substring(cursor + 1);
            }
            return true;
        }
        if (keyCode == Keyboard.KEY_LEFT) {
            state.numberCursor = Math.max(0, cursor - 1);
            return true;
        }
        if (keyCode == Keyboard.KEY_RIGHT) {
            state.numberCursor = Math.min(draft.length(), cursor + 1);
            return true;
        }
        if ((typedChar >= '0' && typedChar <= '9') || typedChar == '.' || typedChar == '-') {
            String candidate = draft.substring(0, cursor) + typedChar + draft.substring(cursor);
            if (isValidNumberDraft(candidate)) {
                state.numberDraft = candidate;
                state.numberCursor = cursor + 1;
            }
            return true;
        }
        return true;
    }

    private boolean isValidNumberDraft(String draft) {
        if (draft.length() > 20 || draft.indexOf('-', 1) >= 0) {
            return false;
        }
        int dot = draft.indexOf('.');
        return dot < 0 || dot == draft.lastIndexOf('.');
    }

    private void commitNumberEditing(Numbers<?> num, String key) {
        if (!key.equals(state.focusedNumberKey)) {
            return;
        }
        try {
            double parsed = Double.parseDouble(state.numberDraft);
            if (!Double.isNaN(parsed) && !Double.isInfinite(parsed)) {
                num.setNumberValue(PanelNumberInputPolicy.normalizeTypedValue(num, parsed));
            }
        } catch (NumberFormatException ignored) {
            // Invalid transient drafts such as "-" are cancelled without changing the value.
        }
        cancelNumberEditing();
    }

    public void cancelNumberEditing() {
        state.focusedNumberKey = null;
        state.numberDraft = null;
        state.numberCursor = 0;
    }

    public boolean isEditingNumber() {
        return state.focusedNumberKey != null;
    }

    public String focusedNumberKey() {
        return state.focusedNumberKey;
    }

    @SuppressWarnings("unchecked")
    private boolean clickOption(Option<?> option, String key, float x, float y, float w,
                                int mouseX, int mouseY, int button) {
        PanelClickGuiLayout.Rect row = new PanelClickGuiLayout.Rect(x, y, w, ROW_H);
        if (row.contains(mouseX, mouseY)) {
            Object raw = option.getValue();
            boolean on = raw instanceof Boolean && ((Boolean) raw);
            Option<Boolean> bool = (Option<Boolean>) option;
            bool.setValue(!on);
            return true;
        }
        return false;
    }

    public boolean updateDraggedSlider(Numbers<?> num, String key, float x, float y, float w, int mouseX) {
        if (!key.equals(state.draggedSliderKey)) {
            return false;
        }
        PanelClickGuiLayout.Rect row = new PanelClickGuiLayout.Rect(x, y, w, ROW_H);
        updateSliderFromMouse(num, EpsilonPanelGeometry.numberTrack(row), mouseX);
        return true;
    }

    private void updateSliderFromMouse(Numbers<?> num, PanelClickGuiLayout.Rect track, int mouseX) {
        double mn = num.getMinimum().doubleValue();
        double mx = num.getMaximum().doubleValue();
        double inc = num.getIncrement().doubleValue();
        float pct = (mouseX - track.x()) / track.width();
        pct = Math.max(0f, Math.min(1f, pct));
        double v = mn + (mx - mn) * pct;
        if (inc > 0) {
            v = mn + Math.round((v - mn) / inc) * inc;
        }
        if (v < mn) v = mn;
        if (v > mx) v = mx;
        num.setNumberValue(v);
    }

    public void mouseReleased() {
        state.draggedSliderKey = null;
    }

    public void clearNumberEditing() {
        cancelNumberEditing();
    }

    public void requestDropdownClose() {
        if (state.openDropdownKey == null) {
            return;
        }
        state.closingDropdownKey = state.openDropdownKey;
        state.openDropdownKey = null;
    }

    public void clearDropdownImmediately() {
        state.openDropdownKey = null;
        state.closingDropdownKey = null;
    }

    public String dropdownRenderKey() {
        return state.openDropdownKey != null
                ? state.openDropdownKey : state.closingDropdownKey;
    }

    public boolean isDropdownOpen() {
        return state.openDropdownKey != null
                || state.closingDropdownKey != null;
    }

    public boolean isDropdownInteractive() {
        return state.openDropdownKey != null;
    }

    public boolean isDraggingSlider() {
        return state.draggedSliderKey != null;
    }

    public String getDraggedSliderKey() {
        return state.draggedSliderKey;
    }

    // ===== Helpers =====

    private PanelClickGuiLayout.Rect modeChipBounds(String display, float x, float y, float width) {
        PanelClickGuiLayout.Rect row = new PanelClickGuiLayout.Rect(x, y, width, ROW_H);
        float textWidth = EpsilonPanelFonts.text(EpsilonPanelMetrics.FIELD_TEXT_SCALE)
                .getStringWidth(display);
        return PanelClickGuiEnumPopup.chipBounds(row, textWidth);
    }

    private PanelClickGuiLayout.Rect popupPlacement(PanelClickGuiLayout.Rect chip,
                                                     int optionCount) {
        if (state.popupBounds == null) {
            float popupWidth = Math.max(PanelClickGuiEnumPopup.MIN_WIDTH,
                    chip.width() + PanelClickGuiEnumPopup.WIDTH_EXTRA);
            return new PanelClickGuiLayout.Rect(chip.right() - popupWidth,
                    chip.bottom() + PanelClickGuiEnumPopup.ANCHOR_GAP,
                    popupWidth, PanelClickGuiEnumPopup.viewportHeight(optionCount));
        }
        return PanelClickGuiEnumPopup.place(chip, state.popupBounds, optionCount, state.popupInset);
    }

    private static void drawChevronTriangle(float centerX, float centerY, float size,
                                             float progress, int color) {
        float angle = (float) Math.toRadians(90.0f * Math.max(0.0f, Math.min(1.0f, progress)));
        float cos = (float) Math.cos(angle);
        float sin = (float) Math.sin(angle);
        float[][] vertices = {
                {-size, -size},
                {-size, size},
                {size, 0.0f}
        };
        float alpha = ((color >>> 24) & 0xFF) / 255.0f;
        float red = ((color >>> 16) & 0xFF) / 255.0f;
        float green = ((color >>> 8) & 0xFF) / 255.0f;
        float blue = (color & 0xFF) / 255.0f;
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT
                | GL11.GL_TEXTURE_BIT | GL11.GL_CURRENT_BIT);
        try {
            GL11.glDisable(GL11.GL_TEXTURE_2D);
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            GL11.glColor4f(red, green, blue, alpha);
            GL11.glBegin(GL11.GL_TRIANGLES);
            for (float[] vertex : vertices) {
                GL11.glVertex2f(centerX + vertex[0] * cos - vertex[1] * sin,
                        centerY + vertex[0] * sin + vertex[1] * cos);
            }
            GL11.glEnd();
        } finally {
            GL11.glPopAttrib();
        }
    }

    private String formatNumber(double v, Numbers<?> num) {
        Number inc = num.getIncrement();
        boolean integer = inc != null && inc.doubleValue() >= 1.0;
        if (integer) {
            return String.valueOf((int) Math.round(v));
        }
        if (v >= 100) {
            return String.format(Locale.ROOT, "%.0f", v);
        }
        if (v >= 10) {
            return String.format(Locale.ROOT, "%.1f", v);
        }
        return String.format(Locale.ROOT, "%.2f", v);
    }
}
