package gq.yozakura.event.api.events.callables;

import gq.yozakura.event.api.events.Event;
import gq.yozakura.event.api.events.Cancellable;

/**
 * Abstract example implementation of the Cancellable interface.
 *
 * @author DarkMagician6
 * @since August 27, 2013
 */
public abstract class EventCancellable implements Event, Cancellable {
	public static int a = 1;
	public boolean cancelled;

	protected EventCancellable() {
	}

	/**
	 * @see Cancellable.isCancelled
	 */
	@Override
	public boolean isCancelled() {
		return cancelled;
	}

	/**
	 * @see Cancellable.setCancelled
	 */
	@Override
	public void setCancelled(boolean state) {
		cancelled = state;
	}

}
