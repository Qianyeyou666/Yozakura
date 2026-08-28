package gq.yozakura.event.bridge;

import gq.yozakura.event.bus.events.Event;

/** Raised after world and first-person item rendering has completed. */
public final class RenderTickEndEvent implements Event {
    private final float partialTicks;

    public RenderTickEndEvent(float partialTicks) {
        this.partialTicks = partialTicks;
    }

    public float getPartialTicks() {
        return partialTicks;
    }
}
