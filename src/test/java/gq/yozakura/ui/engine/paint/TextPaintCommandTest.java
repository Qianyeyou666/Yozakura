package gq.yozakura.ui.engine.paint;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class TextPaintCommandTest {
    @Test
    public void commandCarriesRetainedTextStyle() {
        TextPaintCommand command = new TextPaintCommand(
                "Yozakura", 12.0F, 20.0F, 14.0F,
                "Inter", true, Color.parse("#ffffff"));

        assertEquals(PaintCommand.TYPE_TEXT, command.type());
        assertEquals("Yozakura", command.text());
        assertEquals(14.0F, command.fontSize(), 0.001F);
        assertEquals("Inter", command.fontFamily());
    }

    @Test
    public void centeredTextUsesRemainingContentWidth() {
        TextPaintCommand command = new TextPaintCommand(
                "Y", 10.0F, 20.0F, 14.0F,
                "Inter", true, Color.parse("#ffffff"),
                TextPaintCommand.ALIGN_CENTER, 30.0F);

        assertEquals(20.0F, command.alignedX(10.0F), 0.001F);
    }

    @Test
    public void rightAlignedTextUsesRemainingContentWidth() {
        TextPaintCommand command = new TextPaintCommand(
                "EN", 10.0F, 20.0F, 14.0F,
                "Inter", false, Color.parse("#ffffff"),
                TextPaintCommand.ALIGN_RIGHT, 30.0F);

        assertEquals(30.0F, command.alignedX(10.0F), 0.001F);
    }

    @Test
    public void equivalentCommandsShareRetainedTextLayoutKey() {
        TextPaintCommand first = new TextPaintCommand(
                "Combat", 10.0F, 20.0F, 14.0F,
                "Inter", true, Color.parse("#ffffff"),
                TextPaintCommand.ALIGN_LEFT, 100.0F);
        TextPaintCommand second = new TextPaintCommand(
                "Combat", 10.0F, 20.0F, 14.0F,
                "Inter", true, Color.parse("#ffffff"),
                TextPaintCommand.ALIGN_LEFT, 100.0F);

        assertEquals(first, second);
        assertEquals(first.hashCode(), second.hashCode());
    }
}
