package gq.yozakura.module.render;

final class TargetEspRenderQuality {
    private static final int FULL_DETAIL_FPS = 200;
    private static final int MINIMUM_DETAIL_FPS = 60;

    private TargetEspRenderQuality() {
    }

    static int segments(int full, int minimum, int frameRate) {
        int resolvedMinimum = Math.max(1, Math.min(full, minimum));
        if (frameRate >= FULL_DETAIL_FPS) {
            return full;
        }
        if (frameRate <= MINIMUM_DETAIL_FPS) {
            return resolvedMinimum;
        }

        float progress = (frameRate - MINIMUM_DETAIL_FPS)
                / (float) (FULL_DETAIL_FPS - MINIMUM_DETAIL_FPS);
        int interpolated = Math.round(resolvedMinimum + (full - resolvedMinimum) * progress);
        int quadAligned = Math.round(interpolated / 4.0f) * 4;
        return Math.max(resolvedMinimum, Math.min(full, quadAligned));
    }

    static int riseSigmaRingSegments(int frameRate) {
        return segments(32, 16, frameRate);
    }

    static float riseSigmaRingHeight(float bodyHeight, float time) {
        float normalized = 0.5f + 0.5f * (float) Math.sin(time * 5.0f);
        return Math.max(0.0f, bodyHeight) * normalized;
    }

    static float riseSigmaRingTrailOffset(float bodyHeight, float time) {
        return Math.max(0.0f, bodyHeight) * 0.28f * (float) Math.cos(time * 5.0f);
    }
}
