package gq.yozakura.event.bridge;

import gq.yozakura.event.bus.events.Event;

public class MouseOverEvent implements Event {
    private final float partialTicks;

    public MouseOverEvent(float partialTicks) {
        this.partialTicks = partialTicks;
    }

    public float getPartialTicks() {
        return partialTicks;
    }
}
