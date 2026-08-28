package gq.yozakura.module.render;

/** Pure session state for separating camera input from the player's real facing. */
final class FreeLookCameraState {
    private boolean active;
    private float playerYaw;
    private float playerPitch;
    private float cameraYaw;
    private float cameraPitch;

    void begin(float yaw, float pitch) {
        active = true;
        playerYaw = yaw;
        playerPitch = clampPitch(pitch);
        cameraYaw = yaw;
        cameraPitch = clampPitch(pitch);
    }

    Frame captureInput(float inputYaw, float inputPitch) {
        if (!active) {
            begin(inputYaw, inputPitch);
        }
        cameraYaw += wrapDegrees(inputYaw - playerYaw);
        cameraPitch = clampPitch(cameraPitch + inputPitch - playerPitch);
        return currentFrame();
    }

    Frame currentFrame() {
        return new Frame(playerYaw, playerPitch, cameraYaw, cameraPitch);
    }

    Restore restore(int perspective) {
        return new Restore(playerYaw, playerPitch, perspective);
    }

    Restore end(int perspective) {
        active = false;
        return restore(perspective);
    }

    boolean isActive() {
        return active;
    }

    private static float clampPitch(float pitch) {
        return Math.max(-90.0F, Math.min(90.0F, pitch));
    }

    private static float wrapDegrees(float degrees) {
        degrees %= 360.0F;
        if (degrees >= 180.0F) {
            degrees -= 360.0F;
        }
        if (degrees < -180.0F) {
            degrees += 360.0F;
        }
        return degrees;
    }

    static final class Frame {
        private final float playerYaw;
        private final float playerPitch;
        private final float cameraYaw;
        private final float cameraPitch;

        Frame(float playerYaw, float playerPitch, float cameraYaw, float cameraPitch) {
            this.playerYaw = playerYaw;
            this.playerPitch = playerPitch;
            this.cameraYaw = cameraYaw;
            this.cameraPitch = cameraPitch;
        }

        float getPlayerYaw() {
            return playerYaw;
        }

        float getPlayerPitch() {
            return playerPitch;
        }

        float getCameraYaw() {
            return cameraYaw;
        }

        float getCameraPitch() {
            return cameraPitch;
        }
    }

    static final class Restore {
        private final float yaw;
        private final float pitch;
        private final int perspective;

        Restore(float yaw, float pitch, int perspective) {
            this.yaw = yaw;
            this.pitch = pitch;
            this.perspective = perspective;
        }

        float getYaw() {
            return yaw;
        }

        float getPitch() {
            return pitch;
        }

        int getPerspective() {
            return perspective;
        }
    }
}
