package gq.yozakura.util.notification;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class NightBloomNotificationLayoutTest {
    private static final float EPSILON = 0.0001F;

    @Test
    public void transitionAlphaTracksTheExistingSlideContinuously() {
        assertEquals(0.0F, NightBloomNotificationLayout.alphaForSlide(1.0F), EPSILON);
        assertEquals(0.5F, NightBloomNotificationLayout.alphaForSlide(0.5F), EPSILON);
        assertEquals(1.0F, NightBloomNotificationLayout.alphaForSlide(0.0F), EPSILON);
        assertTrue(NightBloomNotificationLayout.alphaForSlide(0.49F)
                > NightBloomNotificationLayout.alphaForSlide(0.50F));
    }

    @Test
    public void lifetimeProgressIsClampedAtBothEnds() {
        assertEquals(1.0F, NightBloomNotificationLayout.progressForLifetime(0L, 2500L), EPSILON);
        assertEquals(0.5F, NightBloomNotificationLayout.progressForLifetime(1250L, 2500L), EPSILON);
        assertEquals(0.0F, NightBloomNotificationLayout.progressForLifetime(4000L, 2500L), EPSILON);
        assertEquals(0.0F, NightBloomNotificationLayout.progressForLifetime(1L, 0L), EPSILON);
    }

    @Test
    public void layoutKeepsIconTextAndProgressInsideThePanelInsets() {
        NightBloomNotificationLayout.Layout layout = NightBloomNotificationLayout.create(100.0F, 200.0F, 300.0F, 246.0F);

        assertEquals(109.0F, layout.getIconLeft(), EPSILON);
        assertEquals(209.0F, layout.getIconTop(), EPSILON);
        assertEquals(22.0F, layout.getIconSize(), EPSILON);
        assertEquals(139.0F, layout.getTitleX(), EPSILON);
        assertEquals(207.0F, layout.getTitleY(), EPSILON);
        assertEquals(222.0F, layout.getMessageY(), EPSILON);
        assertEquals(109.0F, layout.getProgressLeft(), EPSILON);
        assertEquals(291.0F, layout.getProgressRight(), EPSILON);
        assertTrue(layout.getProgressTop() < layout.getProgressBottom());
        assertTrue(layout.getProgressBottom() <= 246.0F);
    }

    @Test
    public void compactPanelDimensionsStayBelowTheLegacyNotificationSize() {
        assertEquals(38.0F, NightBloomNotificationLayout.panelHeight(false), EPSILON);
        assertEquals(46.0F, NightBloomNotificationLayout.panelHeight(true), EPSILON);
        assertEquals(190.0F, NightBloomNotificationLayout.panelWidth(40.0F, 80.0F), EPSILON);
        assertEquals(248.0F, NightBloomNotificationLayout.panelWidth(260.0F, 280.0F), EPSILON);
    }

    @Test
    public void liquidIconAndContentTilesCloseTheirGapIntoOneLocalSurface() {
        NightBloomNotificationLayout.LiquidPair separated =
                NightBloomNotificationLayout.createLiquidPair(100.0F, 200.0F, 300.0F, 246.0F, 0.0F);
        NightBloomNotificationLayout.LiquidPair joined =
                NightBloomNotificationLayout.createLiquidPair(100.0F, 200.0F, 300.0F, 246.0F, 1.0F);

        assertTrue(separated.getBodyLeft() > separated.getIconRight());
        assertEquals(0.0F, separated.getBridgeOpacity(), EPSILON);
        assertEquals(0.0F, separated.getCompositeProgress(), EPSILON);

        assertEquals(joined.getIconRight(), joined.getBodyLeft(), EPSILON);
        assertEquals(1.0F, joined.getBridgeOpacity(), EPSILON);
        assertEquals(1.0F, joined.getCompositeProgress(), EPSILON);
        assertEquals(100.0F, joined.getCompositeLeft(), EPSILON);
        assertEquals(300.0F, joined.getCompositeRight(), EPSILON);
        assertEquals(200.0F, joined.getCompositeTop(), EPSILON);
        assertEquals(246.0F, joined.getCompositeBottom(), EPSILON);
    }

    @Test
    public void liquidPairKeepsItsBridgeWithinItsOwnNotificationBounds() {
        NightBloomNotificationLayout.LiquidPair first =
                NightBloomNotificationLayout.createLiquidPair(100.0F, 200.0F, 300.0F, 246.0F, 0.45F);
        NightBloomNotificationLayout.LiquidPair second =
                NightBloomNotificationLayout.createLiquidPair(100.0F, 260.0F, 300.0F, 306.0F, 0.45F);

        assertTrue(first.getBridgeTop() >= first.getCompositeTop());
        assertTrue(first.getBridgeBottom() <= first.getCompositeBottom());
        assertTrue(second.getBridgeTop() >= second.getCompositeTop());
        assertTrue(second.getBridgeBottom() <= second.getCompositeBottom());
        assertTrue(first.getCompositeBottom() < second.getCompositeTop());
    }

    @Test
    public void liquidPairKeepsTextAndProgressInsideTheContentTileWhileItCloses() {
        NightBloomNotificationLayout.LiquidPair separated =
                NightBloomNotificationLayout.createLiquidPair(100.0F, 200.0F, 300.0F, 246.0F, 0.0F);
        NightBloomNotificationLayout.LiquidPair joined =
                NightBloomNotificationLayout.createLiquidPair(100.0F, 200.0F, 300.0F, 246.0F, 1.0F);

        assertEquals(139.0F, joined.getTitleX(), EPSILON);
        assertTrue(separated.getTitleX() > joined.getTitleX());
        assertTrue(separated.getProgressLeft() >= separated.getBodyLeft());
        assertTrue(separated.getProgressLeft() > separated.getIconRight());
        assertTrue(separated.getProgressRight() <= separated.getBodyRight());
    }
}
