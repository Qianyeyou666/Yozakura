package gq.yozakura.ui.engine.render;

import gq.yozakura.ui.engine.paint.Color;
import gq.yozakura.ui.engine.paint.RectBorderCommand;
import gq.yozakura.ui.engine.paint.RectFillCommand;
import gq.yozakura.ui.engine.paint.TextPaintCommand;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class LwjglUiRendererRoundedPathTest {
    @Test
    public void roundedCommandsAreRoutedAwayFromPolygonBatchPath() {
        assertTrue(LwjglUiRenderer.hasRoundedCorners(new RectFillCommand(
                0, 0, 100, 40, Color.parse("#ffffff"), 12.0F)));
        assertTrue(LwjglUiRenderer.hasRoundedCorners(new RectFillCommand(
                0, 0, 100, 40, Color.parse("#ffffff"), 12.0F, 12.0F, 0.0F, 0.0F)));
        assertFalse(LwjglUiRenderer.hasRoundedCorners(new RectFillCommand(
                0, 0, 100, 40, Color.parse("#ffffff"))));
    }

    @Test
    public void uniformRoundedBordersUseTheAntialiasedShaderPath() {
        assertTrue(LwjglUiRenderer.hasUniformRoundedBorder(new RectBorderCommand(
                0, 0, 100, 40, 1, 1, 1, 1, Color.parse("#ffffff"), 12)));
        assertFalse(LwjglUiRenderer.hasUniformRoundedBorder(new RectBorderCommand(
                0, 0, 100, 40, 0, 0, 1, 0, Color.parse("#ffffff"), 12)));
    }

    @Test
    public void glyphOriginsSnapToThePhysicalPixelGrid() {
        assertEquals(10.5F, LwjglUiRenderer.snapToPixelGrid(10.31F, 2.0F), 0.0001F);
        assertEquals(10.0F, LwjglUiRenderer.snapToPixelGrid(10.24F, 2.0F), 0.0001F);
    }

    @Test
    public void pickerEffectsRemainExplicitRetainedCommands() {
        RectFillCommand hue = RectFillCommand.hue(0, 0, 100, 16, 8);
        RectFillCommand palette = RectFillCommand.palette(0, 0, 190, 150, 8, 0.75F);
        assertEquals(RectFillCommand.EFFECT_HUE, hue.effect());
        assertEquals(RectFillCommand.EFFECT_PALETTE, palette.effect());
        assertEquals(0.75F, palette.effectValue(), 0.0001F);
    }

    @Test
    public void paintOnlyMotionAndOpacityReuseTextShaping() {
        TextPaintCommand first = new TextPaintCommand("Yozakura", 10, 20, 14,
                "Inter", true, Color.parse("#ffffff"));
        TextPaintCommand movedAndFaded = new TextPaintCommand("Yozakura", 14, 24, 14,
                "Inter", true, Color.parse("rgba(255,255,255,0.25)"));
        TextPaintCommand different = new TextPaintCommand("Other", 14, 24, 14,
                "Inter", true, Color.parse("#ffffff"));
        assertTrue(LwjglUiRenderer.sharesTextLayout(first, movedAndFaded));
        assertFalse(LwjglUiRenderer.sharesTextLayout(first, different));
    }
}
