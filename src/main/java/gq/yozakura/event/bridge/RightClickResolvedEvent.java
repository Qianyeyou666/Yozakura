package gq.yozakura.event.bridge;

import gq.yozakura.event.bus.events.Event;

/** Immutable right-click cancellation result after all right-click listeners run. */
public final class RightClickResolvedEvent implements Event {
    private final boolean cancelled;

    public RightClickResolvedEvent(boolean cancelled) {
        this.cancelled = cancelled;
    }

    public boolean isCancelled() {
        return cancelled;
    }
}
