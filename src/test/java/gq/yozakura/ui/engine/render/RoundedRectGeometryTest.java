package gq.yozakura.ui.engine.render;

import gq.yozakura.ui.engine.paint.Color;
import gq.yozakura.ui.engine.paint.RectFillCommand;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class RoundedRectGeometryTest {
    @Test
    public void independentRadiiKeepSquareBottomCorners() {
        RectFillCommand command = new RectFillCommand(
                0.0F, 0.0F, 100.0F, 40.0F, Color.parse("#ffffff"),
                12.0F, 12.0F, 0.0F, 0.0F);
        final boolean[] corners = new boolean[2];

        RoundedRectGeometry.emit(command, new RoundedRectGeometry.TriangleSink() {
            @Override
            public void triangle(float x0, float y0, float x1, float y1, float x2, float y2) {
                inspect(x0, y0); inspect(x1, y1); inspect(x2, y2);
            }

            private void inspect(float x, float y) {
                if (Math.abs(x) < 0.001F && Math.abs(y) < 0.001F) corners[0] = true;
                if (Math.abs(x - 100.0F) < 0.001F && Math.abs(y - 40.0F) < 0.001F) corners[1] = true;
            }
        });

        assertFalse("rounded top-left must not cover the square corner", corners[0]);
        assertTrue("zero-radius bottom-right must cover the square corner", corners[1]);
    }
}
