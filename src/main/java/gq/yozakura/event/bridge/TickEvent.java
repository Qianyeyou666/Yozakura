package gq.vapulite.event.bridge;

import gq.vapulite.event.bus.events.Event;
import gq.vapulite.event.bus.types.EventType;

public class TickEvent implements Event {
    private final EventType type;

    public TickEvent(EventType type) {
        this.type = type;
    }

    public EventType getType() {
        return this.type;
    }
}
