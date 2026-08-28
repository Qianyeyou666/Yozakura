package gq.yozakura.ui.engine.render;

import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;

public class ScissorTransformTest {
    @Test
    public void convertsTopLeftLogicalClipToBottomLeftFramebufferCoordinates() {
        int[] box = ScissorTransform.toFramebuffer(
                new ClipRect(10, 20, 30, 40),
                2.0F, 5.0F, 7.0F, 400);

        assertArrayEquals(new int[]{30, 266, 60, 80}, box);
    }
}
