package gq.yozakura.module.render;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class InventoryAnimationPointerTest {
    @Test
    public void scaledPointerMapsBackToInventoryCoordinates() {
        assertEquals(150.0F, InventoryAnimationPointer.toLogicalCoordinate(140, 100.0F, 0.8F), 0.001F);
        assertEquals(50.0F, InventoryAnimationPointer.toLogicalCoordinate(60, 100.0F, 0.8F), 0.001F);
    }

    @Test
    public void fullyOpenInventoryKeepsPointerCoordinatesUnchanged() {
        assertEquals(73.0F, InventoryAnimationPointer.toLogicalCoordinate(73, 100.0F, 1.0F), 0.001F);
    }
}
