package gq.yozakura.bridge;

import java.util.ArrayDeque;
import java.util.Queue;

/**
 * Holds outbound actions until the packet bridge reaches their tick boundary.
 *
 * <p>Actions collected during a silent-rotation tick become ready only after
 * that tick's player packet has established the server-side yaw. They are then
 * released immediately before the following player packet.</p>
 */
final class OutboundActionBatchQueue<T> {
    private final Queue<T> readyActions = new ArrayDeque<T>();
    private final Queue<T> currentActions = new ArrayDeque<T>();

    void addCurrent(T action) {
        currentActions.add(action);
    }

    void addReady(T action) {
        readyActions.add(action);
    }

    T pollReady() {
        return readyActions.poll();
    }

    T pollCurrent() {
        return currentActions.poll();
    }

    void promoteCurrent() {
        T action;
        while ((action = currentActions.poll()) != null) {
            readyActions.add(action);
        }
    }

    void clear() {
        readyActions.clear();
        currentActions.clear();
    }
}
