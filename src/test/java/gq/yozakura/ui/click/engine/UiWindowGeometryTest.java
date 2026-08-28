package gq.yozakura.ui.click.engine;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class UiWindowGeometryTest {
    @Test
    public void guiScaleTwoStartsAtAuthoredPhysicalSize() {
        UiWindowGeometry geometry = new UiWindowGeometry(960.0F, 640.0F);

        geometry.updateViewport(960, 540, 2.0F);

        assertEquals(0.5F, geometry.scale(), 0.001F);
        assertEquals(240.0F, geometry.x(), 0.001F);
        assertEquals(110.0F, geometry.y(), 0.001F);
    }
}
