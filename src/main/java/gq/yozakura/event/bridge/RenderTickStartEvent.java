package gq.yozakura.event.bridge;

import gq.yozakura.event.bus.events.Event;

/** Raised once per rendered world frame before the camera transform is consumed. */
public final class RenderTickStartEvent implements Event {
    private final float partialTicks;
    private final boolean cameraInputComplete;

    public RenderTickStartEvent(float partialTicks) {
        this(partialTicks, false);
    }

    public RenderTickStartEvent(float partialTicks, boolean cameraInputComplete) {
        this.partialTicks = partialTicks;
        this.cameraInputComplete = cameraInputComplete;
    }

    public float getPartialTicks() {
        return partialTicks;
    }

    public boolean isCameraInputComplete() {
        return cameraInputComplete;
    }
}
