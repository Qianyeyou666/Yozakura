package gq.yozakura.event.bridge;

import gq.yozakura.event.bus.events.Event;
import gq.yozakura.event.bus.types.EventType;

public class TickEvent implements Event {
    private final EventType type;

    public TickEvent(EventType type) {
        this.type = type;
    }

    public EventType getType() {
        return this.type;
    }
}
