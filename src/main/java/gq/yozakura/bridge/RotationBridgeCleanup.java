package gq.yozakura.bridge;

public final class RotationBridgeCleanup {
    private RotationBridgeCleanup() {
    }

    public static void clearTransientState() {
        MovementInputBridge.restoreRotation();
        MovementInputBridge.resetMovementInput();
    }
}
