package gq.yozakura.engine.render.glow;

/**
 * Builds the one-sided weights for a normalized, separable Gaussian kernel.
 */
public final class GaussianKernel {
    public static final int MAX_RADIUS = 32;

    private GaussianKernel() {
    }

    public static float[] create(int radius) {
        if (radius < 0 || radius > MAX_RADIUS) {
            throw new IllegalArgumentException("radius must be between 0 and " + MAX_RADIUS);
        }

        float[] weights = new float[radius + 1];
        if (radius == 0) {
            weights[0] = 1.0f;
            return weights;
        }

        double sigma = radius / 2.0d;
        double denominator = 2.0d * sigma * sigma;
        double total = 0.0d;
        for (int offset = 0; offset <= radius; offset++) {
            double weight = Math.exp(-(offset * offset) / denominator);
            weights[offset] = (float) weight;
            total += offset == 0 ? weight : weight * 2.0d;
        }

        for (int offset = 0; offset <= radius; offset++) {
            weights[offset] = (float) (weights[offset] / total);
        }
        return weights;
    }
}
