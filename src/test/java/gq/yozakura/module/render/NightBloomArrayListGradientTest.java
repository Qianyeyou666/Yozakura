package gq.yozakura.module.render;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

public class NightBloomArrayListGradientTest {
    @Test
    public void colorFieldIsStableForOneTickAndMovesAcrossTimeAndRows() {
        long tick = 10_000L;
        int current = NightBloomArrayListGradient.colorAt(140.0F, 80.0F, tick);

        assertEquals(current, NightBloomArrayListGradient.colorAt(140.0F, 80.0F, tick));
        assertNotEquals(current, NightBloomArrayListGradient.colorAt(140.0F, 80.0F, tick + 900L));
        assertNotEquals(current, NightBloomArrayListGradient.colorAt(190.0F, 80.0F, tick));
        assertNotEquals(current, NightBloomArrayListGradient.colorAt(140.0F, 120.0F, tick));
    }

    @Test
    public void scrollingFieldUsesAVisiblyWidePinkBrightnessRange() {
        int minimumGreen = 255;
        int maximumGreen = 0;
        for (long tick = 0L; tick <= 2000L; tick += 100L) {
            for (int y = 0; y <= 80; y += 16) {
                for (int x = 0; x <= 120; x += 20) {
                    int green = NightBloomArrayListGradient.colorAt(x, y, tick) >>> 8 & 255;
                    minimumGreen = Math.min(minimumGreen, green);
                    maximumGreen = Math.max(maximumGreen, green);
                }
            }
        }
        assertTrue(maximumGreen - minimumGreen >= 130);
    }
}
