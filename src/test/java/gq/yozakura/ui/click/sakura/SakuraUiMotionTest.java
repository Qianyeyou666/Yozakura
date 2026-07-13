package gq.yozakura.ui.click.sakura;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class SakuraUiMotionTest {
    private static final float EPSILON = 0.0002F;

    @Test
    public void preservesTheExistingSixtyFpsFeelForOpenAnimation() {
        float expected = 1.0F - (float) Math.pow(1.0F - 0.18F, 1.0F);

        float actual = SakuraUiMotion.approach(0.0F, 1.0F, 0.18F, 1.0F / 60.0F);

        assertEquals(expected, actual, EPSILON);
    }

    @Test
    public void keepsWindowOpenAndCloseProgressIndependentOfFrameRate() {
        float openedAtSixty = simulate(0.0F, 1.0F, 0.18F, 0.22F, 60);
        float openedAtTwoForty = simulate(0.0F, 1.0F, 0.18F, 0.22F, 240);

        assertEquals(openedAtSixty, openedAtTwoForty, EPSILON);
        assertEquals(simulate(openedAtSixty, 0.0F, 0.24F, 0.17F, 60),
                simulate(openedAtTwoForty, 0.0F, 0.24F, 0.17F, 240), EPSILON);
    }

    @Test
    public void keepsModuleListTransitionIndependentOfFrameRate() {
        assertEquals(simulate(0.0F, 1.0F, 0.20F, 0.26F, 60),
                simulate(0.0F, 1.0F, 0.20F, 0.26F, 240), EPSILON);
    }

    @Test
    public void keepsBindingOverlayIndependentOfFrameRate() {
        assertEquals(simulate(0.0F, 1.0F, 0.20F, 0.21F, 60),
                simulate(0.0F, 1.0F, 0.20F, 0.21F, 240), EPSILON);
    }

    @Test
    public void keepsScrollDisplayIndependentOfFrameRate() {
        assertEquals(simulate(0.0F, 96.0F, 0.26F, 0.26F, 60),
                simulate(0.0F, 96.0F, 0.26F, 0.26F, 240), EPSILON);
    }

    @Test
    public void limitsAStalledFrameToTheUiClockBudget() {
        float stalled = SakuraUiMotion.approach(0.0F, 1.0F, 0.20F, 1.0F);
        float maximumStep = SakuraUiMotion.approach(0.0F, 1.0F, 0.20F, 0.05F);

        assertEquals(maximumStep, stalled, EPSILON);
        assertTrue(stalled > 0.0F && stalled < 1.0F);
    }

    private static float simulate(float initial, float target, float speed, float seconds, int framesPerSecond) {
        float value = initial;
        float elapsed = 0.0F;
        float frameSeconds = 1.0F / framesPerSecond;
        while (elapsed < seconds) {
            float delta = Math.min(frameSeconds, seconds - elapsed);
            value = SakuraUiMotion.approach(value, target, speed, delta);
            elapsed += delta;
        }
        return value;
    }
}
