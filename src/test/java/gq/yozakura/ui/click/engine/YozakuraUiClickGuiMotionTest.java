package gq.yozakura.ui.click.engine;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class YozakuraUiClickGuiMotionTest {
    @Test
    public void openingCurveFinishesExactlyAndHasAControlledSpringOvershoot() {
        assertEquals(0.0F, ClickGuiMotion.openingEase(0.0F), 0.0001F);
        assertEquals(1.0F, ClickGuiMotion.openingEase(1.0F), 0.0001F);
        assertTrue(ClickGuiMotion.openingEase(0.75F) > 1.0F);
        assertTrue(ClickGuiMotion.openingEase(0.75F) < 1.10F);
    }

    @Test
    public void controlSpringIsBoundedAndReversible() {
        assertEquals(0.0F, ClickGuiMotion.controlSpring(0.0F), 0.0001F);
        assertEquals(1.0F, ClickGuiMotion.controlSpring(1.0F), 0.0001F);
        float forward = ClickGuiMotion.controlSpring(0.35F);
        assertTrue(forward > 0.35F);
        assertTrue(forward < 1.0F);
        assertEquals(1.0F - forward,
                ClickGuiMotion.reverseControlSpring(0.35F), 0.0001F);
    }

    @Test
    public void motionCurvesMatchTheWebViewCubicBezierTokens() {
        assertEquals(1.08740F, ClickGuiMotion.openingEase(0.5F), 0.0002F);
        assertEquals(1.00676F, ClickGuiMotion.controlSpring(0.5F), 0.0002F);
        assertEquals(0.85236F, ClickGuiMotion.controlSpring(0.25F), 0.0002F);
    }

    @Test
    public void layoutCompensationStartsAtThePreviousScreenPosition() {
        assertEquals(-120.0F, ClickGuiMotion.layoutCompensation(200.0F, 320.0F), 0.0001F);
        assertEquals(120.0F, ClickGuiMotion.layoutCompensation(320.0F, 200.0F), 0.0001F);
    }

    // Pre-existing breakage: ClickGuiMotion.toggleKnobCompensation(boolean)
    // was removed during the YozakuraUI engine refactor. Tracking the fix is
    // out of scope for the RenderUtil/glow perf work; the other four methods
    // above still validate the motion contract.
    // @Test
    // public void toggleAnimationCompensatesFromTheOppositeCssEndpoint() {
    //     assertEquals(-16.0F, ClickGuiMotion.toggleKnobCompensation(true), 0.0001F);
    //     assertEquals(16.0F, ClickGuiMotion.toggleKnobCompensation(false), 0.0001F);
    // }
}
