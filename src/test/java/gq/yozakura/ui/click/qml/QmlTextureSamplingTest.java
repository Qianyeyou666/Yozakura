package gq.yozakura.ui.click.qml;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class QmlTextureSamplingTest {
    @Test
    public void nativePhysicalPixelScaleUsesCrispSampling() {
        assertTrue(QmlTextureSampling.isPixelPerfect(0.5F, 2));
        assertTrue(QmlTextureSampling.isPixelPerfect(1.0F, 1));
    }

    @Test
    public void resizedContentKeepsSmoothSampling() {
        assertFalse(QmlTextureSampling.isPixelPerfect(0.4F, 2));
    }
}
