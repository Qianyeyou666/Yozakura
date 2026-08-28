package gq.yozakura.util.render;

/**
 * Pure logical-pixel state for a single HUD drag gesture. Rendering and value persistence stay in {@link HudDrag}.
 */
final class HudDragSession {
    static final float DRAG_THRESHOLD = 3.0F;
    static final float SAFE_MARGIN = 6.0F;
    static final float SNAP_DISTANCE = 6.0F;

    enum DragState {
        IDLE,
        ARMED,
        DRAGGING,
        SNAP_PREVIEW,
        RELEASE
    }

    enum SnapTarget {
        NONE,
        START,
        CENTER,
        END
    }

    private DragState state = DragState.IDLE;
    private String activeId;
    private Position pointerDownPosition;
    private Position previewPosition;
    private float pointerDownMouseX;
    private float pointerDownMouseY;
    private SnapTarget horizontalSnap = SnapTarget.NONE;
    private SnapTarget verticalSnap = SnapTarget.NONE;
    private boolean pointerBlockedUntilRelease;

    DragState getState() {
        return state;
    }

    String getActiveId() {
        return activeId;
    }

    boolean isTracking(String id) {
        return id != null && id.equals(activeId) && (state == DragState.ARMED
                || state == DragState.DRAGGING || state == DragState.SNAP_PREVIEW);
    }

    boolean isReleaseFor(String id) {
        return id != null && id.equals(activeId) && state == DragState.RELEASE;
    }

    boolean isPointerBlockedUntilRelease() {
        return pointerBlockedUntilRelease;
    }

    void releasePointerBlock() {
        pointerBlockedUntilRelease = false;
    }

    Preview getPreview() {
        Position position = previewPosition == null ? new Position(0.0F, 0.0F) : previewPosition;
        return new Preview(position, horizontalSnap, verticalSnap);
    }

    void arm(String id, Position position, float mouseX, float mouseY) {
        if (id == null || id.isEmpty()) {
            throw new IllegalArgumentException("id must not be empty");
        }
        requireFinite(mouseX, "mouseX");
        requireFinite(mouseY, "mouseY");
        if (position == null) {
            throw new IllegalArgumentException("position must not be null");
        }

        activeId = id;
        pointerDownPosition = position;
        previewPosition = position;
        pointerDownMouseX = mouseX;
        pointerDownMouseY = mouseY;
        horizontalSnap = SnapTarget.NONE;
        verticalSnap = SnapTarget.NONE;
        state = DragState.ARMED;
    }

    Preview drag(float mouseX, float mouseY, Bounds bounds) {
        requireFinite(mouseX, "mouseX");
        requireFinite(mouseY, "mouseY");
        if (bounds == null) {
            throw new IllegalArgumentException("bounds must not be null");
        }
        if (!isTracking(activeId)) {
            return getPreview();
        }

        float deltaX = mouseX - pointerDownMouseX;
        float deltaY = mouseY - pointerDownMouseY;
        if (state == DragState.ARMED && deltaX * deltaX + deltaY * deltaY < DRAG_THRESHOLD * DRAG_THRESHOLD) {
            return getPreview();
        }

        Position candidate = clampToBounds(new Position(pointerDownPosition.x + deltaX,
                pointerDownPosition.y + deltaY), bounds);
        AxisSnap xSnap = snap(candidate.x, bounds.minX(), bounds.centerX(), bounds.maxX());
        AxisSnap ySnap = snap(candidate.y, bounds.minY(), bounds.centerY(), bounds.maxY());
        previewPosition = new Position(xSnap.value, ySnap.value);
        horizontalSnap = xSnap.target;
        verticalSnap = ySnap.target;
        state = horizontalSnap == SnapTarget.NONE && verticalSnap == SnapTarget.NONE
                ? DragState.DRAGGING : DragState.SNAP_PREVIEW;
        return getPreview();
    }

    Completion release() {
        if (!isTracking(activeId)) {
            return Completion.none();
        }
        state = DragState.RELEASE;
        pointerBlockedUntilRelease = true;
        return new Completion(previewPosition, !samePosition(previewPosition, pointerDownPosition), false);
    }

    Completion cancel() {
        if (!isTracking(activeId)) {
            return Completion.none();
        }
        previewPosition = pointerDownPosition;
        horizontalSnap = SnapTarget.NONE;
        verticalSnap = SnapTarget.NONE;
        state = DragState.RELEASE;
        pointerBlockedUntilRelease = true;
        return new Completion(pointerDownPosition, false, true);
    }

    void acknowledgeRelease() {
        if (state != DragState.RELEASE) {
            return;
        }
        state = DragState.IDLE;
        activeId = null;
        pointerDownPosition = null;
        previewPosition = null;
        horizontalSnap = SnapTarget.NONE;
        verticalSnap = SnapTarget.NONE;
    }

    static Position clampToBounds(Position position, Bounds bounds) {
        if (position == null || bounds == null) {
            throw new IllegalArgumentException("position and bounds must not be null");
        }
        return new Position(clamp(position.x, bounds.minX(), bounds.maxX()),
                clamp(position.y, bounds.minY(), bounds.maxY()));
    }

    static float toLogicalCoordinate(float rawCoordinate, int displaySpan, int logicalSpan) {
        requireFinite(rawCoordinate, "rawCoordinate");
        return rawCoordinate * Math.max(0, logicalSpan) / Math.max(1, displaySpan);
    }

    static float toLogicalYFromBottom(float rawY, int displayHeight, int logicalHeight) {
        return Math.max(0, logicalHeight) - toLogicalCoordinate(rawY, displayHeight, logicalHeight) - 1.0F;
    }

    private static AxisSnap snap(float value, float start, float center, float end) {
        float bestValue = value;
        float bestDistance = SNAP_DISTANCE;
        SnapTarget target = SnapTarget.NONE;

        float startDistance = Math.abs(value - start);
        if (startDistance <= bestDistance) {
            bestValue = start;
            bestDistance = startDistance;
            target = SnapTarget.START;
        }
        float centerDistance = Math.abs(value - center);
        if (centerDistance <= SNAP_DISTANCE && (target == SnapTarget.NONE || centerDistance < bestDistance)) {
            bestValue = center;
            bestDistance = centerDistance;
            target = SnapTarget.CENTER;
        }
        float endDistance = Math.abs(value - end);
        if (endDistance <= SNAP_DISTANCE && (target == SnapTarget.NONE || endDistance < bestDistance)) {
            bestValue = end;
            target = SnapTarget.END;
        }
        return new AxisSnap(bestValue, target);
    }

    private static boolean samePosition(Position first, Position second) {
        return first != null && second != null && Math.abs(first.x - second.x) < 0.0001F
                && Math.abs(first.y - second.y) < 0.0001F;
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private static void requireFinite(float value, String name) {
        if (Float.isNaN(value) || Float.isInfinite(value)) {
            throw new IllegalArgumentException(name + " must be finite");
        }
    }

    static final class Position {
        private final float x;
        private final float y;

        Position(float x, float y) {
            requireFinite(x, "x");
            requireFinite(y, "y");
            this.x = x;
            this.y = y;
        }

        float getX() {
            return x;
        }

        float getY() {
            return y;
        }
    }

    static final class Bounds {
        private final float screenWidth;
        private final float screenHeight;
        private final float elementWidth;
        private final float elementHeight;

        Bounds(float screenWidth, float screenHeight, float elementWidth, float elementHeight) {
            requireFinite(screenWidth, "screenWidth");
            requireFinite(screenHeight, "screenHeight");
            requireFinite(elementWidth, "elementWidth");
            requireFinite(elementHeight, "elementHeight");
            this.screenWidth = Math.max(0.0F, screenWidth);
            this.screenHeight = Math.max(0.0F, screenHeight);
            this.elementWidth = Math.max(0.0F, elementWidth);
            this.elementHeight = Math.max(0.0F, elementHeight);
        }

        float minX() {
            return safeMin(screenWidth, elementWidth);
        }

        float minY() {
            return safeMin(screenHeight, elementHeight);
        }

        float maxX() {
            return safeMax(screenWidth, elementWidth);
        }

        float maxY() {
            return safeMax(screenHeight, elementHeight);
        }

        float centerX() {
            return clamp((screenWidth - elementWidth) * 0.5F, minX(), maxX());
        }

        float centerY() {
            return clamp((screenHeight - elementHeight) * 0.5F, minY(), maxY());
        }

        float elementWidth() {
            return elementWidth;
        }

        float elementHeight() {
            return elementHeight;
        }

        private static float safeMin(float screenSize, float elementSize) {
            return Math.min(SAFE_MARGIN, Math.max(0.0F, (screenSize - elementSize) * 0.5F));
        }

        private static float safeMax(float screenSize, float elementSize) {
            float min = safeMin(screenSize, elementSize);
            return Math.max(min, screenSize - elementSize - min);
        }
    }

    static final class Preview {
        private final Position position;
        private final SnapTarget horizontalSnap;
        private final SnapTarget verticalSnap;

        Preview(Position position, SnapTarget horizontalSnap, SnapTarget verticalSnap) {
            this.position = position;
            this.horizontalSnap = horizontalSnap;
            this.verticalSnap = verticalSnap;
        }

        Position getPosition() {
            return position;
        }

        SnapTarget getHorizontalSnap() {
            return horizontalSnap;
        }

        SnapTarget getVerticalSnap() {
            return verticalSnap;
        }

    }

    static final class Completion {
        private static final Completion NONE = new Completion(null, false, false);

        private final Position position;
        private final boolean shouldPersist;
        private final boolean cancelled;

        Completion(Position position, boolean shouldPersist, boolean cancelled) {
            this.position = position;
            this.shouldPersist = shouldPersist;
            this.cancelled = cancelled;
        }

        static Completion none() {
            return NONE;
        }

        Position getPosition() {
            return position;
        }

        boolean shouldPersist() {
            return shouldPersist;
        }

        boolean isCancelled() {
            return cancelled;
        }
    }

    private static final class AxisSnap {
        private final float value;
        private final SnapTarget target;

        private AxisSnap(float value, SnapTarget target) {
            this.value = value;
            this.target = target;
        }
    }
}
