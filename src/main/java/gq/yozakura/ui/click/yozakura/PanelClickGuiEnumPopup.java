package gq.yozakura.ui.click.yozakura;

/**
 * Pure geometry and scrolling model for the Epsilon enum select popup.
 *
 * <p>Mirrors {@code com.github.epsilon.gui.panel.popup.EnumSelectPopup}:
 * the popup shows at most {@link #MAX_VISIBLE_ITEMS} rows of
 * {@link #ITEM_HEIGHT} pixels inside {@link #CONTENT_PADDING} padding.
 * When more options exist the content scrolls internally; the mouse wheel
 * moves {@link #WHEEL_STEP} pixels per notch ({@code scroll - scrollY * 20})
 * clamped to {@code [0, maxScroll]}. Selected rows draw a check icon and
 * indent their label to {@link #TEXT_OFFSET_SELECTED}.
 */
public final class PanelClickGuiEnumPopup {
    public static final int MAX_VISIBLE_ITEMS = 5;
    public static final float ITEM_HEIGHT = 24.0f;
    public static final float ITEM_INNER_HEIGHT = 22.0f;
    public static final float CONTENT_PADDING = 6.0f;
    public static final float WHEEL_STEP = 20.0f;
    public static final float TEXT_OFFSET_UNSELECTED = 10.0f;
    public static final float TEXT_OFFSET_SELECTED = 22.0f;
    public static final float MIN_WIDTH = 108.0f;
    public static final float WIDTH_EXTRA = 24.0f;
    public static final float ANCHOR_GAP = 4.0f;
    public static final float CHIP_HEIGHT = 16.0f;
    public static final float MAX_CHIP_WIDTH = 96.0f;
    public static final float CHIP_HORIZONTAL_PADDING = 8.0f;
    public static final float CHIP_TRAILING_SLOT_WIDTH = 10.0f;
    public static final float ROW_TRAILING_INSET = 5.0f;

    private PanelClickGuiEnumPopup() {
    }

    /** Epsilon assist-chip bounds: text-fit width, 96px cap, row-end aligned. */
    public static PanelClickGuiLayout.Rect chipBounds(PanelClickGuiLayout.Rect rowBounds,
                                                       float textWidth) {
        float desiredWidth = textWidth + CHIP_HORIZONTAL_PADDING * 2.0f
                + CHIP_TRAILING_SLOT_WIDTH;
        float width = Math.min(MAX_CHIP_WIDTH, desiredWidth);
        return new PanelClickGuiLayout.Rect(
                rowBounds.right() - ROW_TRAILING_INSET - width,
                rowBounds.y() + (rowBounds.height() - CHIP_HEIGHT) * 0.5f,
                width,
                CHIP_HEIGHT);
    }

    /** True when the option count exceeds the five-item window. */
    public static boolean isScrollable(int optionCount) {
        return optionCount > MAX_VISIBLE_ITEMS;
    }

    /** Full (unclipped) content height including top and bottom padding. */
    public static float fullContentHeight(int optionCount) {
        return optionCount * ITEM_HEIGHT + CONTENT_PADDING * 2.0f;
    }

    /** Visible popup height, capped at five rows plus padding. */
    public static float viewportHeight(int optionCount) {
        int visible = Math.min(optionCount, MAX_VISIBLE_ITEMS);
        return visible * ITEM_HEIGHT + CONTENT_PADDING * 2.0f;
    }

    /** Maximum scroll offset: the content overflow beyond the viewport. */
    public static float maxScroll(int optionCount) {
        return Math.max(0.0f, fullContentHeight(optionCount) - viewportHeight(optionCount));
    }

    /**
     * Places a popup using Epsilon's minimum width, chip-right alignment and
     * bottom-overflow flip. The inset is supplied by the containing panel.
     */
    public static PanelClickGuiLayout.Rect place(PanelClickGuiLayout.Rect chipBounds,
                                                 PanelClickGuiLayout.Rect popupBounds,
                                                 int optionCount, float panelInset) {
        float popupHeight = viewportHeight(optionCount);
        float popupWidth = Math.max(MIN_WIDTH, chipBounds.width() + WIDTH_EXTRA);
        float popupX = Math.max(popupBounds.x() + panelInset,
                chipBounds.right() - popupWidth);
        float popupY = chipBounds.bottom() + ANCHOR_GAP;
        float maxBottom = popupBounds.bottom() - panelInset;
        if (popupY + popupHeight > maxBottom) {
            popupY = chipBounds.y() - popupHeight - ANCHOR_GAP;
        }
        return new PanelClickGuiLayout.Rect(popupX, popupY, popupWidth, popupHeight);
    }

    /**
     * Applies one wheel event. Epsilon: {@code nextScroll = scroll - scrollY * 20}
     * clamped to {@code [0, maxScroll]}; positive scrollY scrolls up.
     */
    public static float scrollAfterWheel(float scroll, float scrollY, int optionCount) {
        float next = scroll - scrollY * WHEEL_STEP;
        float max = maxScroll(optionCount);
        if (next < 0.0f) {
            return 0.0f;
        }
        return Math.min(next, max);
    }

    /**
     * Hit-tests a local Y coordinate (relative to the popup top) against the
     * scrolled item list. Returns the option index or {@code -1} when the
     * position falls inside the padding or beyond the last option.
     */
    public static int itemIndexAt(float localY, float scroll, int optionCount) {
        float contentY = localY - CONTENT_PADDING + scroll;
        if (contentY < 0.0f) {
            return -1;
        }
        int index = (int) (contentY / ITEM_HEIGHT);
        if (index < 0 || index >= optionCount) {
            return -1;
        }
        float itemY = contentY - index * ITEM_HEIGHT;
        return itemY < ITEM_INNER_HEIGHT ? index : -1;
    }
}
