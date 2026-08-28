package gq.yozakura.ui.click.qml;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class QmlWindowGeometryTest {
    @Test
    public void initialSizeUsesOneQmlPixelPerPhysicalPixel() {
        QmlWindowGeometry geometry = new QmlWindowGeometry(960, 640);

        geometry.updateViewport(960, 540, 2);

        assertEquals(0.5F, geometry.scale(), 0.001F);
        assertEquals(240.0F, geometry.x(), 0.001F);
        assertEquals(110.0F, geometry.y(), 0.001F);
    }

    @Test
    public void draggingUsesPressTimeCoordinatesAndStaysOnScreen() {
        QmlWindowGeometry geometry = new QmlWindowGeometry(960, 640);
        geometry.updateViewport(960, 540, 2);
        geometry.beginMove(300, 150);

        geometry.updatePointer(-1000, -1000);

        assertEquals(0.0F, geometry.x(), 0.001F);
        assertEquals(0.0F, geometry.y(), 0.001F);
    }

    @Test
    public void resizePreservesAspectRatioAndClampsToCrispMaximum() {
        QmlWindowGeometry geometry = new QmlWindowGeometry(960, 640);
        geometry.updateViewport(960, 540, 2);
        geometry.beginResize(720, 430);

        geometry.updatePointer(2000, 2000);

        assertEquals(0.5F, geometry.scale(), 0.001F);

        geometry.updatePointer(400, 200);

        assertEquals(0.25F, geometry.scale(), 0.001F);
        assertEquals(1.5F, geometry.renderedWidth() / geometry.renderedHeight(), 0.001F);
    }
}
