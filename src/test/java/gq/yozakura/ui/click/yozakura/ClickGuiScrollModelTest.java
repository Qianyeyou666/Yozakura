package gq.yozakura.ui.click.yozakura;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class ClickGuiScrollModelTest {
    @Test
    public void wheelMovementUsesTheActualViewportHeight() {
        ClickGuiScrollModel scroll = new ClickGuiScrollModel();
        scroll.updateBounds(900f, 300f);

        scroll.onWheel(-480);

        assertEquals(120f, scroll.target(), 0.001f);
        assertEquals(600f, scroll.maximum(), 0.001f);
    }

    @Test
    public void shrinkingContentClampsCurrentAndTargetOffsets() {
        ClickGuiScrollModel scroll = new ClickGuiScrollModel();
        scroll.updateBounds(900f, 300f);
        scroll.onWheel(-4000);
        scroll.snapToTarget();

        scroll.updateBounds(320f, 300f);

        assertEquals(20f, scroll.maximum(), 0.001f);
        assertEquals(20f, scroll.current(), 0.001f);
        assertEquals(20f, scroll.target(), 0.001f);
    }
}
