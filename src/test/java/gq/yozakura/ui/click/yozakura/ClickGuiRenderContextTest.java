package gq.yozakura.ui.click.yozakura;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class ClickGuiRenderContextTest {
    @Test
    public void mapsDesignBoundsIntoTheSameScreenSpaceAsTheWindow() {
        ClickGuiRenderContext context = new ClickGuiRenderContext();
        context.configure(29f, 16f, 0.75f);

        assertEquals(194f, context.screenX(220f), 0.001f);
        assertEquals(56.5f, context.screenY(54f), 0.001f);
        assertEquals(180f, context.screenWidth(240f), 0.001f);
    }
}
