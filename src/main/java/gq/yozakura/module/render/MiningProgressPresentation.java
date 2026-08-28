package gq.yozakura.module.render;

final class MiningProgressPresentation {
    private MiningProgressPresentation() {
    }

    static String formatPercent(float progress) {
        float safe = Math.max(0.0F, Math.min(1.0F, progress));
        return Math.round(safe * 100.0F) + "%";
    }

    static float approach(float current, float target, float amount) {
        float safeAmount = Math.max(0.0F, amount);
        if (current < target) {
            return Math.min(target, current + safeAmount);
        }
        return Math.max(target, current - safeAmount);
    }

    static Anchor anchor(int blockX, int blockY, int blockZ,
                         double viewerX, double viewerY, double viewerZ, double faceOffset) {
        double centerX = blockX + 0.5D;
        double centerY = blockY + 0.5D;
        double centerZ = blockZ + 0.5D;
        double deltaX = viewerX - centerX;
        double deltaY = viewerY - centerY;
        double deltaZ = viewerZ - centerZ;
        double length = Math.sqrt(deltaX * deltaX + deltaY * deltaY + deltaZ * deltaZ);
        if (length <= 0.000001D) {
            return new Anchor(centerX, blockY + 1.0D + Math.max(0.0D, faceOffset), centerZ);
        }

        double normalX = deltaX / length;
        double normalY = deltaY / length;
        double normalZ = deltaZ / length;
        double blockSupport = 0.5D * (Math.abs(normalX) + Math.abs(normalY) + Math.abs(normalZ));
        double offset = blockSupport + Math.max(0.0D, faceOffset);
        return new Anchor(
                centerX + normalX * offset,
                centerY + normalY * offset,
                centerZ + normalZ * offset);
    }

    static float worldScale(double distance, float userScale) {
        float safeDistance = (float) Math.max(0.0D, distance);
        float distanceFactor = 1.0F + Math.max(0.0F, safeDistance - 8.0F) / 48.0F;
        distanceFactor = Math.min(1.55F, distanceFactor);
        float safeUserScale = Math.max(0.6F, Math.min(2.0F, userScale));
        return 0.018F * safeUserScale * distanceFactor;
    }

    static final class Anchor {
        private final double x;
        private final double y;
        private final double z;

        private Anchor(double x, double y, double z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }

        double getX() {
            return x;
        }

        double getY() {
            return y;
        }

        double getZ() {
            return z;
        }
    }
}
