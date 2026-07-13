package gq.yozakura.engine.render.glow;

import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class GaussianKernelTest {
    private static final float EPSILON = 0.000001F;

    @Test
    public void radiusZeroIsAnImpulse() {
        assertArrayEquals(new float[]{1.0F}, GaussianKernel.create(0), EPSILON);
    }

    @Test
    public void symmetricKernelIsNormalized() {
        float[] weights = GaussianKernel.create(12);
        double total = weights[0];
        for (int offset = 1; offset < weights.length; offset++) {
            total += weights[offset] * 2.0D;
        }

        assertEquals(1.0D, total, 0.000001D);
    }

    @Test
    public void weightsAreNonNegativeAndMonotonicallyDecreasing() {
        float[] weights = GaussianKernel.create(GaussianKernel.MAX_RADIUS);

        for (int offset = 0; offset < weights.length; offset++) {
            assertTrue("weight at offset " + offset + " must be non-negative",
                    weights[offset] >= 0.0F);
            if (offset > 0) {
                assertTrue("weight at offset " + offset + " must not exceed its predecessor",
                        weights[offset] <= weights[offset - 1] + EPSILON);
            }
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsNegativeRadius() {
        GaussianKernel.create(-1);
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsRadiusAboveShaderBudget() {
        GaussianKernel.create(GaussianKernel.MAX_RADIUS + 1);
    }
}
