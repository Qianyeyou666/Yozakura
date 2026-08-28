package gq.yozakura.ui.click.yozakura;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ClickGuiWindowGeometryTest {
    @Test
    public void centersTheDesignAtNativeScaleWhenThereIsEnoughRoom() {
        ClickGuiWindowGeometry geometry = new ClickGuiWindowGeometry();

        geometry.resize(1280f, 720f, -1f, -1f);

        assertEquals(1f, geometry.scale(), 0.001f);
        assertEquals(160f, geometry.x(), 0.001f);
        assertEquals(40f, geometry.y(), 0.001f);
        assertEquals(960f, geometry.width(), 0.001f);
        assertEquals(640f, geometry.height(), 0.001f);
    }

    @Test
    public void scalesTheWholeWindowWithoutChangingItsAspectRatio() {
        ClickGuiWindowGeometry geometry = new ClickGuiWindowGeometry();

        geometry.resize(854f, 480f, -1f, -1f);

        assertEquals(0.70f, geometry.scale(), 0.001f);
        assertEquals(672f, geometry.width(), 0.001f);
        assertEquals(448f, geometry.height(), 0.001f);
        assertEquals(91f, geometry.x(), 0.001f);
        assertEquals(16f, geometry.y(), 0.001f);
    }

    @Test
    public void dragUsesScreenCoordinatesAndClampsTheWindowToTheViewport() {
        ClickGuiWindowGeometry geometry = new ClickGuiWindowGeometry();
        geometry.resize(1280f, 720f, -1f, -1f);

        assertTrue(geometry.beginDrag(200f, 60f));
        geometry.dragTo(-100f, -100f, 1280f, 720f);
        geometry.endDrag();

        assertFalse(geometry.isDragging());
        assertEquals(0f, geometry.x(), 0.001f);
        assertEquals(0f, geometry.y(), 0.001f);
    }

    @Test
    public void convertsInputBackIntoDesignCoordinates() {
        ClickGuiWindowGeometry geometry = new ClickGuiWindowGeometry();
        geometry.resize(854f, 480f, -1f, -1f);

        assertEquals(220f, geometry.localX(geometry.x() + 154f), 0.001f);
        assertEquals(54f, geometry.localY(geometry.y() + 37.8f), 0.001f);
    }

    @Test
    public void minecraftGuiScaleKeepsDesignPixelsAtTheirAuthoredScreenSize() {
        ClickGuiWindowGeometry geometry = new ClickGuiWindowGeometry();

        geometry.resizeForGui(960f, 517f, 2f, -1f, -1f);

        assertEquals(0.5f, geometry.scale(), 0.001f);
        assertEquals(480f, geometry.width(), 0.001f);
        assertEquals(320f, geometry.height(), 0.001f);
        assertEquals(240f, geometry.x(), 0.001f);
        assertEquals(98.5f, geometry.y(), 0.001f);
    }
}
