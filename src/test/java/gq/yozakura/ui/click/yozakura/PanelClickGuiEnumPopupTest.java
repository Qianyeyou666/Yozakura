package gq.yozakura.ui.click.yozakura;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Locks the Epsilon {@code EnumSelectPopup} geometry and scrolling model:
 * at most five visible items, 24px rows, 6px content padding, and wheel
 * scrolling at 20px per notch clamped to the content overflow.
 */
public class PanelClickGuiEnumPopupTest {
    @Test
    public void epsilonEnumPopupMetricsStayExact() {
        assertEquals(5, PanelClickGuiEnumPopup.MAX_VISIBLE_ITEMS);
        assertEquals(24.0f, PanelClickGuiEnumPopup.ITEM_HEIGHT, 0.001f);
        assertEquals(22.0f, PanelClickGuiEnumPopup.ITEM_INNER_HEIGHT, 0.001f);
        assertEquals(6.0f, PanelClickGuiEnumPopup.CONTENT_PADDING, 0.001f);
        assertEquals(20.0f, PanelClickGuiEnumPopup.WHEEL_STEP, 0.001f);
        assertEquals(10.0f, PanelClickGuiEnumPopup.TEXT_OFFSET_UNSELECTED, 0.001f);
        assertEquals(22.0f, PanelClickGuiEnumPopup.TEXT_OFFSET_SELECTED, 0.001f);
        assertEquals(16.0f, PanelClickGuiEnumPopup.CHIP_HEIGHT, 0.001f);
        assertEquals(96.0f, PanelClickGuiEnumPopup.MAX_CHIP_WIDTH, 0.001f);
    }

    @Test
    public void popupBecomesScrollableOnlyAboveFiveOptions() {
        assertFalse(PanelClickGuiEnumPopup.isScrollable(4));
        assertFalse(PanelClickGuiEnumPopup.isScrollable(5));
        assertTrue(PanelClickGuiEnumPopup.isScrollable(6));
    }

    @Test
    public void viewportHeightIsCappedAtFiveItemsPlusPadding() {
        // 3 items: 3*24 + 12 = 84
        assertEquals(84.0f, PanelClickGuiEnumPopup.viewportHeight(3), 0.001f);
        // 5 items: 5*24 + 12 = 132
        assertEquals(132.0f, PanelClickGuiEnumPopup.viewportHeight(5), 0.001f);
        // 9 items still cap at 132
        assertEquals(132.0f, PanelClickGuiEnumPopup.viewportHeight(9), 0.001f);
    }

    @Test
    public void maxScrollEqualsContentOverflow() {
        assertEquals(0.0f, PanelClickGuiEnumPopup.maxScroll(5), 0.001f);
        // 6 items: full = 6*24+12 = 156, viewport = 132, overflow = 24
        assertEquals(24.0f, PanelClickGuiEnumPopup.maxScroll(6), 0.001f);
        // 9 items: overflow = 4*24 = 96
        assertEquals(96.0f, PanelClickGuiEnumPopup.maxScroll(9), 0.001f);
    }

    @Test
    public void wheelScrollMovesTwentyPixelsPerNotchAndClamps() {
        // scroll down (scrollY = -1) from 0 -> 20
        assertEquals(20.0f, PanelClickGuiEnumPopup.scrollAfterWheel(0.0f, -1.0f, 9), 0.001f);
        // scroll up (scrollY = +1) from 20 -> 0
        assertEquals(0.0f, PanelClickGuiEnumPopup.scrollAfterWheel(20.0f, 1.0f, 9), 0.001f);
        // clamp at maxScroll (96 for 9 items)
        assertEquals(96.0f, PanelClickGuiEnumPopup.scrollAfterWheel(90.0f, -1.0f, 9), 0.001f);
        // clamp at zero
        assertEquals(0.0f, PanelClickGuiEnumPopup.scrollAfterWheel(5.0f, 1.0f, 9), 0.001f);
        // non-scrollable popups never move
        assertEquals(0.0f, PanelClickGuiEnumPopup.scrollAfterWheel(0.0f, -1.0f, 4), 0.001f);
    }

    @Test
    public void itemHitTestingAccountsForScrollAndPadding() {
        // local Y inside padding hits nothing
        assertEquals(-1, PanelClickGuiEnumPopup.itemIndexAt(3.0f, 0.0f, 9));
        // first row starts at padding
        assertEquals(0, PanelClickGuiEnumPopup.itemIndexAt(6.0f, 0.0f, 9));
        assertEquals(0, PanelClickGuiEnumPopup.itemIndexAt(27.9f, 0.0f, 9));
        assertEquals(-1, PanelClickGuiEnumPopup.itemIndexAt(28.1f, 0.0f, 9));
        assertEquals(1, PanelClickGuiEnumPopup.itemIndexAt(30.0f, 0.0f, 9));
        // scrolled by one row: same local Y hits the next item
        assertEquals(2, PanelClickGuiEnumPopup.itemIndexAt(30.0f, 24.0f, 9));
        // below the last item -> nothing
        assertEquals(-1, PanelClickGuiEnumPopup.itemIndexAt(6.0f + 9 * 24.0f, 0.0f, 9));
        // beyond the option count -> nothing (scrolled to the very end)
        assertEquals(8, PanelClickGuiEnumPopup.itemIndexAt(123.9f, 96.0f, 9));
        assertEquals(-1, PanelClickGuiEnumPopup.itemIndexAt(124.0f, 96.0f, 9));
    }

    @Test
    public void chipWidthFitsTextAndCapsAtNinetySixPixels() {
        PanelClickGuiLayout.Rect row = new PanelClickGuiLayout.Rect(100.0f, 50.0f, 200.0f, 28.0f);

        PanelClickGuiLayout.Rect normal = PanelClickGuiEnumPopup.chipBounds(row, 40.0f);
        PanelClickGuiLayout.Rect capped = PanelClickGuiEnumPopup.chipBounds(row, 200.0f);

        assertEquals(66.0f, normal.width(), 0.001f);
        assertEquals(229.0f, normal.x(), 0.001f);
        assertEquals(56.0f, normal.y(), 0.001f);
        assertEquals(96.0f, capped.width(), 0.001f);
        assertEquals(199.0f, capped.x(), 0.001f);
    }

    @Test
    public void placementUsesEpsilonMinimumWidthAndRightAlignment() {
        PanelClickGuiLayout.Rect chip = new PanelClickGuiLayout.Rect(250.0f, 80.0f, 72.0f, 18.0f);
        PanelClickGuiLayout.Rect panel = new PanelClickGuiLayout.Rect(100.0f, 20.0f, 240.0f, 300.0f);

        PanelClickGuiLayout.Rect popup = PanelClickGuiEnumPopup.place(chip, panel, 3, 3.0f);

        assertEquals(108.0f, popup.width(), 0.001f);
        assertEquals(chip.right(), popup.right(), 0.001f);
        assertEquals(chip.bottom() + 4.0f, popup.y(), 0.001f);
        assertEquals(84.0f, popup.height(), 0.001f);
    }

    @Test
    public void placementClampsLeftAndFlipsAboveWhenBottomWouldOverflow() {
        PanelClickGuiLayout.Rect chip = new PanelClickGuiLayout.Rect(104.0f, 276.0f, 40.0f, 18.0f);
        PanelClickGuiLayout.Rect panel = new PanelClickGuiLayout.Rect(100.0f, 20.0f, 240.0f, 300.0f);

        PanelClickGuiLayout.Rect popup = PanelClickGuiEnumPopup.place(chip, panel, 5, 3.0f);

        assertEquals(103.0f, popup.x(), 0.001f);
        assertEquals(chip.y() - popup.height() - 4.0f, popup.y(), 0.001f);
        assertEquals(132.0f, popup.height(), 0.001f);
    }
}
