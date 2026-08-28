package gq.yozakura.util.render;

import gq.yozakura.value.Numbers;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Central registry and gesture owner for HUD dragging. The manager follows the Nymphilila model:
 * named handles are created once, renderers only update their geometry, and one active pointer
 * session is coordinated for the whole HUD editor.
 *
 * <p>Docking remains in {@link HudDrag}; this class owns only ordinary drag identity and input so
 * the existing fusion graph can continue to consume the same positions and snapshots.</p>
 */
public final class HudDragManager {
    private final Map<String, HudDragging> draggables = new LinkedHashMap<String, HudDragging>();
    private final HudDragSession session = new HudDragSession();
    private String selectedId;
    private HudDragging active;

    public HudDragging create(String id, float initialX, float initialY) {
        HudDragging existing = draggables.get(id);
        if (existing != null) {
            return existing;
        }
        HudDragging created = new HudDragging(id, initialX, initialY);
        draggables.put(id, created);
        return created;
    }

    public HudDragging get(String id) {
        return id == null ? null : draggables.get(id);
    }

    public int size() {
        return draggables.size();
    }

    public String getSelectedId() {
        return selectedId;
    }

    public boolean isDragging(String id) {
        return active != null && active.getId().equals(id) && session.isTracking(id);
    }

    public HudDragSession.DragState getState(String id) {
        return id != null && active != null && active.getId().equals(id)
                ? session.getState() : HudDragSession.DragState.IDLE;
    }

    public HudDragSession.Preview getPreview(String id) {
        return id != null && active != null && active.getId().equals(id)
                ? session.getPreview() : null;
    }

    public boolean isSelected(String id) {
        return id != null && id.equals(selectedId);
    }

    public float updateHoverProgress(String id, boolean hovered, long nowNanos) {
        HudDragging dragging = get(id);
        return dragging == null ? 0.0F : dragging.updateHoverProgress(hovered, nowNanos);
    }

    public void observe(String id, Numbers<Double> xValue, Numbers<Double> yValue,
                        float defaultX, float defaultY, float x, float y, float width, float height) {
        if (active != null && active.getId().equals(id) && session.isTracking(id)) {
            cancelActive();
        }
        HudDragging dragging = create(id, defaultX, defaultY);
        dragging.bind(xValue, yValue);
        dragging.setBounds(width, height);
        dragging.setPosition(x, y);
    }

    public HudDragSession.Position update(String id, Numbers<Double> xValue, Numbers<Double> yValue,
                                          float defaultX, float defaultY, HudDragSession.Bounds bounds,
                                          Frame frame) {
        if (bounds == null) {
            throw new IllegalArgumentException("bounds must not be null");
        }
        HudDragging dragging = create(id, defaultX, defaultY);
        dragging.bind(xValue, yValue);
        dragging.setBounds(bounds.elementWidth(), bounds.elementHeight());

        HudDragSession.Position configured = positionOf(xValue, yValue, defaultX, defaultY);
        HudDragSession.Position position = HudDragSession.clampToBounds(configured, bounds);
        dragging.setPosition(position.getX(), position.getY());

        if (frame == null || !frame.editMode) {
            cancelActive();
            session.releasePointerBlock();
            return position;
        }
        if (!frame.leftDown) {
            session.releasePointerBlock();
        }

        if (active != null && session.isTracking(active.getId())) {
            boolean completingCurrent = active.getId().equals(id);
            if (frame.escapeDown) {
                complete(true);
                if (completingCurrent) {
                    position = HudDragSession.clampToBounds(positionOf(xValue, yValue, defaultX, defaultY), bounds);
                    dragging.setPosition(position.getX(), position.getY());
                }
            } else if (!frame.leftDown) {
                complete(false);
                if (completingCurrent) {
                    position = HudDragSession.clampToBounds(positionOf(xValue, yValue, defaultX, defaultY), bounds);
                    dragging.setPosition(position.getX(), position.getY());
                }
            }
        }

        if (active == null && session.getState() == HudDragSession.DragState.IDLE && frame.leftDown
                && !frame.escapeDown && !session.isPointerBlockedUntilRelease()
                && hovered(frame.mouseX, frame.mouseY,
                position.getX(), position.getY(), dragging.getWidth(), dragging.getHeight())) {
            session.arm(id, position, frame.mouseX, frame.mouseY);
            active = dragging;
            selectedId = id;
        }

        if (active != null && active.getId().equals(id) && session.isTracking(id)) {
            HudDragSession.Preview preview = session.drag(frame.mouseX, frame.mouseY, bounds);
            position = preview.getPosition();
            dragging.setPosition(position.getX(), position.getY());
        }
        return position;
    }

    public void clear() {
        cancelActive();
        session.releasePointerBlock();
        draggables.clear();
        selectedId = null;
    }

    private void complete(boolean cancelled) {
        HudDragSession.Completion completion = cancelled ? session.cancel() : session.release();
        if (active != null && completion.getPosition() != null && !cancelled) {
            active.setPosition(completion.getPosition().getX(), completion.getPosition().getY());
            persist(active, completion.getPosition());
        }
        session.acknowledgeRelease();
        active = null;
    }

    private void cancelActive() {
        if (active != null && session.isTracking(active.getId())) {
            complete(true);
        } else if (session.getState() == HudDragSession.DragState.RELEASE) {
            session.acknowledgeRelease();
            active = null;
        }
    }

    private static HudDragSession.Position positionOf(Numbers<Double> xValue, Numbers<Double> yValue,
                                                       float defaultX, float defaultY) {
        float x = xValue == null || xValue.getValue() == null || xValue.getValue() < 0.0D
                ? defaultX : xValue.getValue().floatValue();
        float y = yValue == null || yValue.getValue() == null || yValue.getValue() < 0.0D
                ? defaultY : yValue.getValue().floatValue();
        return new HudDragSession.Position(x, y);
    }

    private static void persist(HudDragging dragging, HudDragSession.Position position) {
        if (dragging.getXValue() != null) {
            dragging.getXValue().setNumberValue(round(position.getX()));
        }
        if (dragging.getYValue() != null) {
            dragging.getYValue().setNumberValue(round(position.getY()));
        }
    }

    private static double round(float value) {
        return Math.round(value * 10.0F) / 10.0D;
    }

    private static boolean hovered(float mouseX, float mouseY, float x, float y, float width, float height) {
        return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
    }

    public static final class Frame {
        private final boolean editMode;
        private final float mouseX;
        private final float mouseY;
        private final boolean leftDown;
        private final boolean escapeDown;

        public Frame(boolean editMode, float mouseX, float mouseY, boolean leftDown, boolean escapeDown) {
            this.editMode = editMode;
            this.mouseX = mouseX;
            this.mouseY = mouseY;
            this.leftDown = leftDown;
            this.escapeDown = escapeDown;
        }
    }
}
