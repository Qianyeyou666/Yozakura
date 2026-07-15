package gq.yozakura.module.render;

import gq.yozakura.util.animation.MotionValue;
import gq.yozakura.util.animation.UiClock;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Retained layout and input state for Night Bloom's three composable watermark tiles.
 * All geometry is expressed in scaled GUI logical pixels, which keeps drawing, hit testing,
 * snapping, and persisted coordinates in one coordinate space.
 */
final class NightBloomWatermarkLayout {
    static final float SAFE_MARGIN = 6.0F;
    static final float SNAP_DISTANCE = 8.0F;
    static final float SPLIT_GAP = 2.0F;
    static final float POSITION_DURATION_SECONDS = 0.20F;
    static final float LIQUID_DURATION_SECONDS = 0.42F;
    private static final float MAGNETIC_PULL = 0.42F;
    private static final float TOUCH_EPSILON = 0.35F;

    enum Tile {
        BRAND,
        VERSION,
        STATUS
    }

    enum Placement {
        LEFT_OF,
        RIGHT_OF,
        ABOVE,
        BELOW
    }

    enum CrossAlignment {
        START,
        CENTER,
        END
    }

    private enum Side {
        LEFT,
        RIGHT,
        TOP,
        BOTTOM
    }

    private final Map<Tile, TileState> states = new EnumMap<Tile, TileState>(Tile.class);
    private final List<Link> links = new ArrayList<Link>();
    private final Map<Tile, Position> dragStarts = new EnumMap<Tile, Position>(Tile.class);
    private boolean initialized;
    private boolean previousRightDown;
    private Tile activeTile;
    private Tile selectedTile;
    private float pointerStartX;
    private float pointerStartY;
    private float screenWidth;
    private float screenHeight;
    private float uiScale = 1.0F;
    private SnapCandidate preview;
    private boolean dirty;

    NightBloomWatermarkLayout() {
        for (Tile tile : Tile.values()) {
            states.put(tile, new TileState(tile));
        }
    }

    void reset() {
        initialized = false;
        previousRightDown = false;
        activeTile = null;
        selectedTile = null;
        preview = null;
        dirty = false;
        dragStarts.clear();
        links.clear();
        for (TileState state : states.values()) {
            state.reset();
        }
    }

    /** Applies a global dock proxy movement to the complete watermark without breaking tile links. */
    Snapshot translateAll(float deltaX, float deltaY) {
        if (!initialized || Math.abs(deltaX) <= TOUCH_EPSILON && Math.abs(deltaY) <= TOUCH_EPSILON) {
            return snapshot();
        }
        for (TileState state : states.values()) {
            state.translate(deltaX, deltaY);
            state.claimCoordinatePersistence();
        }
        dirty = true;
        return snapshot();
    }

    Snapshot update(Frame frame) {
        if (frame == null) {
            throw new IllegalArgumentException("frame must not be null");
        }
        dirty = false;
        screenWidth = Math.max(0.0F, frame.screenWidth);
        screenHeight = Math.max(0.0F, frame.screenHeight);
        uiScale = Math.max(0.1F, frame.uiScale);
        configure(frame);

        if (!frame.editMode) {
            cancelActiveDrag();
            selectedTile = null;
            previousRightDown = frame.rightDown;
            updateAnimations(frame.deltaSeconds);
            return snapshot();
        }

        if (frame.rightDown && !previousRightDown) {
            splitAt(frame.mouseX, frame.mouseY);
        }

        if (frame.leftDown && !frame.rightDown) {
            if (activeTile == null) {
                beginDrag(frame.mouseX, frame.mouseY);
            }
            if (activeTile != null) {
                updateDrag(frame.mouseX, frame.mouseY);
            }
        } else if (activeTile != null) {
            releaseDrag();
        }

        previousRightDown = frame.rightDown;
        if (activeTile == null) {
            reflowLinks();
        }
        updateAnimations(frame.deltaSeconds);
        return snapshot();
    }

    private void configure(Frame frame) {
        if (!initialized) {
            for (Tile tile : Tile.values()) {
                TileInput input = frame.input(tile);
                states.get(tile).initialize(input.x, input.y, input.width, input.height,
                        input.followsInputPosition);
            }
            initialized = true;
            discoverPersistedLinks();
            return;
        }

        boolean sizeChanged = false;
        for (Tile tile : Tile.values()) {
            TileInput input = frame.input(tile);
            TileState state = states.get(tile);
            sizeChanged |= state.setSize(input.width, input.height);
            if (activeTile == null && input.followsInputPosition && !hasActiveLink(tile)) {
                state.setTarget(input.x, input.y);
            }
        }
        if (sizeChanged && activeTile == null) {
            reflowLinks();
        }
    }

    private void discoverPersistedLinks() {
        for (Tile first : Tile.values()) {
            for (Tile second : Tile.values()) {
                if (first.ordinal() >= second.ordinal()) {
                    continue;
                }
                SnapCandidate connection = touchingConnection(states.get(first), states.get(second));
                if (connection != null && !hasDirectLink(connection.child.tile, connection.parent.tile)) {
                    links.add(new Link(connection.child.tile, connection.parent.tile,
                            connection.placement, connection.alignment, true));
                }
            }
        }
    }

    private void beginDrag(float mouseX, float mouseY) {
        TileState hit = hit(mouseX, mouseY);
        if (hit == null) {
            return;
        }
        activeTile = hit.tile;
        selectedTile = hit.tile;
        pointerStartX = mouseX;
        pointerStartY = mouseY;
        dragStarts.clear();
        for (Tile tile : groupedTiles(activeTile)) {
            TileState state = states.get(tile);
            dragStarts.put(tile, new Position(state.x.get(), state.y.get()));
        }
        preview = null;
    }

    private void updateDrag(float mouseX, float mouseY) {
        if (activeTile == null || dragStarts.isEmpty()) {
            return;
        }
        float deltaX = mouseX - pointerStartX;
        float deltaY = mouseY - pointerStartY;
        if (Math.abs(deltaX) <= TOUCH_EPSILON && Math.abs(deltaY) <= TOUCH_EPSILON) {
            for (Map.Entry<Tile, Position> entry : dragStarts.entrySet()) {
                states.get(entry.getKey()).snapTo(entry.getValue().x, entry.getValue().y);
            }
            preview = null;
            return;
        }
        Delta clamped = clampGroupDelta(deltaX, deltaY);
        for (Map.Entry<Tile, Position> entry : dragStarts.entrySet()) {
            TileState state = states.get(entry.getKey());
            state.snapTo(entry.getValue().x + clamped.x, entry.getValue().y + clamped.y);
        }
        preview = findSnapCandidate();
        if (preview != null) {
            float pull = previewProgress(preview) * MAGNETIC_PULL;
            Delta magnetized = clampGroupDelta(clamped.x + preview.deltaX * pull,
                    clamped.y + preview.deltaY * pull);
            for (Map.Entry<Tile, Position> entry : dragStarts.entrySet()) {
                TileState state = states.get(entry.getKey());
                state.snapTo(entry.getValue().x + magnetized.x, entry.getValue().y + magnetized.y);
            }
        }
    }

    private Delta clampGroupDelta(float deltaX, float deltaY) {
        float minX = Float.MAX_VALUE;
        float minY = Float.MAX_VALUE;
        float maxX = -Float.MAX_VALUE;
        float maxY = -Float.MAX_VALUE;
        for (Map.Entry<Tile, Position> entry : dragStarts.entrySet()) {
            TileState state = states.get(entry.getKey());
            Position position = entry.getValue();
            minX = Math.min(minX, position.x);
            minY = Math.min(minY, position.y);
            maxX = Math.max(maxX, position.x + state.width);
            maxY = Math.max(maxY, position.y + state.height);
        }
        if (minX == Float.MAX_VALUE) {
            return new Delta(0.0F, 0.0F);
        }
        float minimumX = Math.min(SAFE_MARGIN, Math.max(0.0F, (screenWidth - (maxX - minX)) * 0.5F));
        float minimumY = Math.min(SAFE_MARGIN, Math.max(0.0F, (screenHeight - (maxY - minY)) * 0.5F));
        float maximumX = Math.max(minimumX, screenWidth - (maxX - minX) - minimumX);
        float maximumY = Math.max(minimumY, screenHeight - (maxY - minY) - minimumY);
        float nextX = clamp(minX + deltaX, minimumX, maximumX);
        float nextY = clamp(minY + deltaY, minimumY, maximumY);
        return new Delta(nextX - minX, nextY - minY);
    }

    private void releaseDrag() {
        if (activeTile == null) {
            return;
        }
        boolean moved = hasDraggedSinceStart();
        boolean attached = false;
        if (moved && preview != null && canAttach(preview.child, preview.parent, preview.placement)) {
            float snapDeltaX = preview.targetX - preview.child.x.get();
            float snapDeltaY = preview.targetY - preview.child.y.get();
            for (Tile tile : dragStarts.keySet()) {
                TileState state = states.get(tile);
                state.setTarget(state.x.get() + snapDeltaX, state.y.get() + snapDeltaY);
            }
            if (!hasDirectLink(preview.child.tile, preview.parent.tile)) {
                links.add(new Link(preview.child.tile, preview.parent.tile,
                        preview.placement, preview.alignment, false));
            }
            states.get(preview.child.tile).claimCoordinatePersistence();
            states.get(preview.parent.tile).claimCoordinatePersistence();
            attached = true;
        }
        if (moved) {
            for (Tile tile : dragStarts.keySet()) {
                TileState state = states.get(tile);
                state.claimCoordinatePersistence();
            }
        }
        dirty = attached || moved;
        activeTile = null;
        dragStarts.clear();
        preview = null;
    }

    private boolean hasDraggedSinceStart() {
        for (Map.Entry<Tile, Position> entry : dragStarts.entrySet()) {
            TileState state = states.get(entry.getKey());
            if (Math.abs(state.x.getTarget() - entry.getValue().x) > TOUCH_EPSILON
                    || Math.abs(state.y.getTarget() - entry.getValue().y) > TOUCH_EPSILON) {
                return true;
            }
        }
        return false;
    }

    private void cancelActiveDrag() {
        if (activeTile == null) {
            return;
        }
        for (Map.Entry<Tile, Position> entry : dragStarts.entrySet()) {
            states.get(entry.getKey()).snapTo(entry.getValue().x, entry.getValue().y);
        }
        activeTile = null;
        dragStarts.clear();
        preview = null;
    }

    private void splitAt(float mouseX, float mouseY) {
        TileState hit = hit(mouseX, mouseY);
        if (hit == null) {
            return;
        }
        selectedTile = hit.tile;
        Set<Tile> group = groupedTiles(hit.tile);
        if (group.size() < 2) {
            return;
        }
        Map<Tile, Delta> shifts = new EnumMap<Tile, Delta>(Tile.class);
        for (Tile tile : group) {
            shifts.put(tile, new Delta(0.0F, 0.0F));
        }
        float halfGap = SPLIT_GAP * uiScale * 0.5F;
        for (Link link : links) {
            if (link.detaching || (link.child != hit.tile && link.parent != hit.tile)
                    || !group.contains(link.child) || !group.contains(link.parent)) {
                continue;
            }
            Delta childShift = shifts.get(link.child);
            Delta parentShift = shifts.get(link.parent);
            if (link.placement == Placement.LEFT_OF) {
                shifts.put(link.child, new Delta(childShift.x - halfGap, childShift.y));
                shifts.put(link.parent, new Delta(parentShift.x + halfGap, parentShift.y));
            } else if (link.placement == Placement.RIGHT_OF) {
                shifts.put(link.child, new Delta(childShift.x + halfGap, childShift.y));
                shifts.put(link.parent, new Delta(parentShift.x - halfGap, parentShift.y));
            } else if (link.placement == Placement.ABOVE) {
                shifts.put(link.child, new Delta(childShift.x, childShift.y - halfGap));
                shifts.put(link.parent, new Delta(parentShift.x, parentShift.y + halfGap));
            } else {
                shifts.put(link.child, new Delta(childShift.x, childShift.y + halfGap));
                shifts.put(link.parent, new Delta(parentShift.x, parentShift.y - halfGap));
            }
            states.get(link.child).claimCoordinatePersistence();
            states.get(link.parent).claimCoordinatePersistence();
            link.detach();
        }
        for (Tile tile : group) {
            TileState state = states.get(tile);
            Delta shift = shifts.get(tile);
            state.setTarget(state.x.get() + shift.x, state.y.get() + shift.y);
        }
        activeTile = null;
        dragStarts.clear();
        preview = null;
        dirty = true;
    }

    private SnapCandidate findSnapCandidate() {
        if (activeTile == null) {
            return null;
        }
        Set<Tile> moving = groupedTiles(activeTile);
        SnapCandidate best = null;
        for (Tile movingTile : moving) {
            TileState child = states.get(movingTile);
            for (Tile otherTile : Tile.values()) {
                if (moving.contains(otherTile)) {
                    continue;
                }
                TileState parent = states.get(otherTile);
                best = closer(best, candidate(child, parent, Placement.LEFT_OF, CrossAlignment.START));
                best = closer(best, candidate(child, parent, Placement.RIGHT_OF, CrossAlignment.START));
                best = closer(best, candidate(child, parent, Placement.ABOVE, CrossAlignment.START));
                best = closer(best, candidate(child, parent, Placement.ABOVE, CrossAlignment.CENTER));
                best = closer(best, candidate(child, parent, Placement.ABOVE, CrossAlignment.END));
                best = closer(best, candidate(child, parent, Placement.BELOW, CrossAlignment.START));
                best = closer(best, candidate(child, parent, Placement.BELOW, CrossAlignment.CENTER));
                best = closer(best, candidate(child, parent, Placement.BELOW, CrossAlignment.END));
            }
        }
        return best;
    }

    private SnapCandidate candidate(TileState child, TileState parent, Placement placement,
                                    CrossAlignment alignment) {
        if (!canAttach(child, parent, placement)) {
            return null;
        }
        float x = child.x.get();
        float y = child.y.get();
        float desiredX = x;
        float desiredY = y;
        if (placement == Placement.LEFT_OF) {
            desiredX = parent.x.get() - child.width;
            desiredY = parent.y.get();
        } else if (placement == Placement.RIGHT_OF) {
            desiredX = parent.x.get() + parent.width;
            desiredY = parent.y.get();
        } else if (placement == Placement.ABOVE) {
            desiredX = alignedX(child, parent, alignment);
            desiredY = parent.y.get() - child.height;
        } else {
            desiredX = alignedX(child, parent, alignment);
            desiredY = parent.y.get() + parent.height;
        }
        float deltaX = desiredX - x;
        float deltaY = desiredY - y;
        float distance = (float) Math.sqrt(deltaX * deltaX + deltaY * deltaY);
        if (distance > SNAP_DISTANCE * uiScale) {
            return null;
        }
        return new SnapCandidate(child, parent, placement, alignment,
                desiredX, desiredY, deltaX, deltaY, distance);
    }

    private boolean canAttach(TileState child, TileState parent, Placement placement) {
        if (child == null || parent == null || child == parent || placement == null) {
            return false;
        }
        return isSideAvailable(child.tile, childSide(placement))
                && isSideAvailable(parent.tile, parentSide(placement));
    }

    private boolean isSideAvailable(Tile tile, Side side) {
        for (Link link : links) {
            if (link.uses(tile, side)) {
                return false;
            }
        }
        return true;
    }

    private static Side childSide(Placement placement) {
        if (placement == Placement.LEFT_OF) {
            return Side.RIGHT;
        }
        if (placement == Placement.RIGHT_OF) {
            return Side.LEFT;
        }
        return placement == Placement.ABOVE ? Side.BOTTOM : Side.TOP;
    }

    private static Side parentSide(Placement placement) {
        if (placement == Placement.LEFT_OF) {
            return Side.LEFT;
        }
        if (placement == Placement.RIGHT_OF) {
            return Side.RIGHT;
        }
        return placement == Placement.ABOVE ? Side.TOP : Side.BOTTOM;
    }

    private static float alignedX(TileState child, TileState parent, CrossAlignment alignment) {
        if (alignment == CrossAlignment.END) {
            return parent.x.get() + parent.width - child.width;
        }
        if (alignment == CrossAlignment.CENTER) {
            return parent.x.get() + (parent.width - child.width) * 0.5F;
        }
        return parent.x.get();
    }

    private static SnapCandidate closer(SnapCandidate current, SnapCandidate candidate) {
        if (candidate == null) {
            return current;
        }
        return current == null || candidate.distance < current.distance ? candidate : current;
    }

    private SnapCandidate touchingConnection(TileState first, TileState second) {
        SnapCandidate best = touchingCandidate(first, second, Placement.LEFT_OF, CrossAlignment.START);
        best = closer(best, touchingCandidate(first, second, Placement.RIGHT_OF, CrossAlignment.START));
        best = closer(best, touchingCandidate(first, second, Placement.ABOVE, CrossAlignment.START));
        best = closer(best, touchingCandidate(first, second, Placement.ABOVE, CrossAlignment.CENTER));
        best = closer(best, touchingCandidate(first, second, Placement.ABOVE, CrossAlignment.END));
        best = closer(best, touchingCandidate(first, second, Placement.BELOW, CrossAlignment.START));
        best = closer(best, touchingCandidate(first, second, Placement.BELOW, CrossAlignment.CENTER));
        return closer(best, touchingCandidate(first, second, Placement.BELOW, CrossAlignment.END));
    }

    private SnapCandidate touchingCandidate(TileState child, TileState parent, Placement placement,
                                            CrossAlignment alignment) {
        SnapCandidate candidate = candidate(child, parent, placement, alignment);
        if (candidate == null || candidate.distance > TOUCH_EPSILON) {
            return null;
        }
        return candidate;
    }

    private void reflowLinks() {
        for (int pass = 0; pass < Tile.values().length; pass++) {
            for (Link link : links) {
                if (link.detaching) {
                    continue;
                }
                TileState child = states.get(link.child);
                TileState parent = states.get(link.parent);
                Position desired = resolvedPosition(child, parent, link.placement, link.alignment);
                float deltaX = desired.x - child.x.getTarget();
                float deltaY = desired.y - child.y.getTarget();
                if (Math.abs(deltaX) <= TOUCH_EPSILON && Math.abs(deltaY) <= TOUCH_EPSILON) {
                    continue;
                }
                for (Tile tile : linkedComponent(link.child, link)) {
                    TileState state = states.get(tile);
                    state.setTarget(state.x.getTarget() + deltaX, state.y.getTarget() + deltaY);
                }
                dirty = true;
            }
        }
    }

    private static Position resolvedPosition(TileState child, TileState parent, Placement placement,
                                             CrossAlignment alignment) {
        if (placement == Placement.LEFT_OF) {
            return new Position(parent.x.getTarget() - child.width, parent.y.getTarget());
        }
        if (placement == Placement.RIGHT_OF) {
            return new Position(parent.x.getTarget() + parent.width, parent.y.getTarget());
        }
        if (placement == Placement.ABOVE) {
            return new Position(alignedXTarget(child, parent, alignment), parent.y.getTarget() - child.height);
        }
        return new Position(alignedXTarget(child, parent, alignment), parent.y.getTarget() + parent.height);
    }

    private static float alignedXTarget(TileState child, TileState parent, CrossAlignment alignment) {
        if (alignment == CrossAlignment.END) {
            return parent.x.getTarget() + parent.width - child.width;
        }
        if (alignment == CrossAlignment.CENTER) {
            return parent.x.getTarget() + (parent.width - child.width) * 0.5F;
        }
        return parent.x.getTarget();
    }

    private void updateAnimations(float deltaSeconds) {
        float delta = UiClock.clampDelta(deltaSeconds);
        for (Link link : links) {
            link.progress.updateTween(delta, LIQUID_DURATION_SECONDS);
        }
        Iterator<Link> iterator = links.iterator();
        while (iterator.hasNext()) {
            Link link = iterator.next();
            if (link.detaching && link.progress.get() <= 0.01F) {
                iterator.remove();
            }
        }
        for (TileState state : states.values()) {
            state.x.updateTween(delta, POSITION_DURATION_SECONDS);
            state.y.updateTween(delta, POSITION_DURATION_SECONDS);
        }
    }

    private TileState hit(float mouseX, float mouseY) {
        Tile[] order = Tile.values();
        for (int index = order.length - 1; index >= 0; index--) {
            TileState state = states.get(order[index]);
            if (state.contains(mouseX, mouseY)) {
                return state;
            }
        }
        return null;
    }

    private Set<Tile> groupedTiles(Tile start) {
        return linkedComponent(start, null);
    }

    private Set<Tile> linkedComponent(Tile start, Link ignored) {
        Set<Tile> visited = EnumSet.noneOf(Tile.class);
        if (start == null) {
            return visited;
        }
        ArrayDeque<Tile> queue = new ArrayDeque<Tile>();
        queue.add(start);
        visited.add(start);
        while (!queue.isEmpty()) {
            Tile current = queue.removeFirst();
            for (Link link : links) {
                if (link == ignored || link.detaching) {
                    continue;
                }
                Tile next = link.other(current);
                if (next != null && visited.add(next)) {
                    queue.addLast(next);
                }
            }
        }
        return visited;
    }

    private boolean hasDirectLink(Tile first, Tile second) {
        for (Link link : links) {
            if (!link.detaching && link.connects(first, second)) {
                return true;
            }
        }
        return false;
    }

    private boolean hasActiveLink(Tile tile) {
        if (tile == null) {
            return false;
        }
        for (Link link : links) {
            if (!link.detaching && (link.child == tile || link.parent == tile)) {
                return true;
            }
        }
        return false;
    }

    private Snapshot snapshot() {
        Map<Tile, TileView> views = new EnumMap<Tile, TileView>(Tile.class);
        Set<Tile> persistedTiles = EnumSet.noneOf(Tile.class);
        for (Tile tile : Tile.values()) {
            TileState state = states.get(tile);
            views.put(tile, new TileView(tile, state.x.get(), state.y.get(),
                    state.x.getTarget(), state.y.getTarget(), state.width, state.height));
            if (state.shouldPersistCoordinates()) {
                persistedTiles.add(tile);
            }
        }
        List<LinkView> linkViews = new ArrayList<LinkView>();
        for (Link link : links) {
            linkViews.add(new LinkView(link.child, link.parent, link.placement, link.alignment,
                    link.progress.get(), link.detaching));
        }
        LinkView previewView = preview == null ? null : new LinkView(preview.child.tile, preview.parent.tile,
                preview.placement, preview.alignment, previewProgress(preview), false);
        return new Snapshot(views, linkViews, previewView, activeTile, selectedTile, persistedTiles, dirty);
    }

    private float previewProgress(SnapCandidate candidate) {
        float threshold = Math.max(0.0001F, SNAP_DISTANCE * uiScale);
        float raw = clamp(1.0F - candidate.distance / threshold, 0.0F, 1.0F);
        return raw * raw * (3.0F - 2.0F * raw);
    }

    private static float clamp(float value, float minimum, float maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    static final class Frame {
        final float screenWidth;
        final float screenHeight;
        final float uiScale;
        final float deltaSeconds;
        final boolean editMode;
        final float mouseX;
        final float mouseY;
        final boolean leftDown;
        final boolean rightDown;
        private final Map<Tile, TileInput> inputs = new EnumMap<Tile, TileInput>(Tile.class);

        Frame(float screenWidth, float screenHeight, float uiScale, float deltaSeconds,
              boolean editMode, float mouseX, float mouseY, boolean leftDown, boolean rightDown,
              TileInput brand, TileInput version, TileInput status) {
            this.screenWidth = requireFinite(screenWidth, "screenWidth");
            this.screenHeight = requireFinite(screenHeight, "screenHeight");
            this.uiScale = requireFinite(uiScale, "uiScale");
            this.deltaSeconds = requireFinite(deltaSeconds, "deltaSeconds");
            this.editMode = editMode;
            this.mouseX = requireFinite(mouseX, "mouseX");
            this.mouseY = requireFinite(mouseY, "mouseY");
            this.leftDown = leftDown;
            this.rightDown = rightDown;
            add(brand);
            add(version);
            add(status);
            for (Tile tile : Tile.values()) {
                if (!inputs.containsKey(tile)) {
                    throw new IllegalArgumentException("frame must contain " + tile);
                }
            }
        }

        TileInput input(Tile tile) {
            return inputs.get(tile);
        }

        private void add(TileInput input) {
            if (input == null || inputs.put(input.tile, input) != null) {
                throw new IllegalArgumentException("tile inputs must be non-null and unique");
            }
        }
    }

    static final class TileInput {
        final Tile tile;
        final float x;
        final float y;
        final float width;
        final float height;
        final boolean followsInputPosition;

        TileInput(Tile tile, float x, float y, float width, float height) {
            this(tile, x, y, width, height, false);
        }

        TileInput(Tile tile, float x, float y, float width, float height, boolean followsInputPosition) {
            if (tile == null) {
                throw new IllegalArgumentException("tile must not be null");
            }
            this.tile = tile;
            this.x = requireFinite(x, "x");
            this.y = requireFinite(y, "y");
            this.width = Math.max(0.0F, requireFinite(width, "width"));
            this.height = Math.max(0.0F, requireFinite(height, "height"));
            this.followsInputPosition = followsInputPosition;
        }
    }

    static final class Snapshot {
        private final Map<Tile, TileView> tiles;
        private final List<LinkView> links;
        private final LinkView preview;
        private final Tile activeTile;
        private final Tile selectedTile;
        private final Set<Tile> persistedTiles;
        private final boolean dirty;

        Snapshot(Map<Tile, TileView> tiles, List<LinkView> links, LinkView preview,
                 Tile activeTile, Tile selectedTile, Set<Tile> persistedTiles, boolean dirty) {
            this.tiles = tiles;
            this.links = links;
            this.preview = preview;
            this.activeTile = activeTile;
            this.selectedTile = selectedTile;
            this.persistedTiles = EnumSet.noneOf(Tile.class);
            if (persistedTiles != null) {
                this.persistedTiles.addAll(persistedTiles);
            }
            this.dirty = dirty;
        }

        TileView tile(Tile tile) {
            return tiles.get(tile);
        }

        List<TileView> tiles() {
            return new ArrayList<TileView>(tiles.values());
        }

        List<LinkView> links() {
            return new ArrayList<LinkView>(links);
        }

        LinkView preview() {
            return preview;
        }

        boolean isDragging() {
            return activeTile != null;
        }

        boolean isActive(Tile tile) {
            return tile != null && tile == activeTile;
        }

        boolean isSelected(Tile tile) {
            return tile != null && tile == selectedTile;
        }

        Tile selectedTile() {
            return selectedTile;
        }

        boolean isDirty() {
            return dirty;
        }

        boolean shouldPersist(Tile tile) {
            return tile != null && persistedTiles.contains(tile);
        }

        boolean isGrouped(Tile first, Tile second) {
            if (first == null || second == null || first == second) {
                return false;
            }
            Set<Tile> visited = EnumSet.noneOf(Tile.class);
            ArrayDeque<Tile> queue = new ArrayDeque<Tile>();
            visited.add(first);
            queue.add(first);
            while (!queue.isEmpty()) {
                Tile current = queue.removeFirst();
                for (LinkView link : links) {
                    if (link.detaching) {
                        continue;
                    }
                    Tile next = link.other(current);
                    if (next == second) {
                        return true;
                    }
                    if (next != null && visited.add(next)) {
                        queue.addLast(next);
                    }
                }
            }
            return false;
        }

        boolean hasLiquidTransition() {
            if (preview != null && preview.progress > 0.01F) {
                return true;
            }
            for (LinkView link : links) {
                if (link.progress > 0.01F) {
                    return true;
                }
            }
            return false;
        }
    }

    static final class TileView {
        private final Tile tile;
        private final float x;
        private final float y;
        private final float targetX;
        private final float targetY;
        private final float width;
        private final float height;

        TileView(Tile tile, float x, float y, float targetX, float targetY, float width, float height) {
            this.tile = tile;
            this.x = x;
            this.y = y;
            this.targetX = targetX;
            this.targetY = targetY;
            this.width = width;
            this.height = height;
        }

        Tile getTile() {
            return tile;
        }

        float getX() {
            return x;
        }

        float getY() {
            return y;
        }

        float getTargetX() {
            return targetX;
        }

        float getTargetY() {
            return targetY;
        }

        float getWidth() {
            return width;
        }

        float getHeight() {
            return height;
        }

        float getRight() {
            return x + width;
        }

        float getBottom() {
            return y + height;
        }

        boolean contains(float mouseX, float mouseY) {
            return mouseX >= x && mouseX <= getRight() && mouseY >= y && mouseY <= getBottom();
        }
    }

    static final class LinkView {
        private final Tile child;
        private final Tile parent;
        private final Placement placement;
        private final CrossAlignment alignment;
        private final float progress;
        private final boolean detaching;

        LinkView(Tile child, Tile parent, Placement placement, CrossAlignment alignment,
                 float progress, boolean detaching) {
            this.child = child;
            this.parent = parent;
            this.placement = placement;
            this.alignment = alignment;
            this.progress = progress;
            this.detaching = detaching;
        }

        Tile getChild() {
            return child;
        }

        Tile getParent() {
            return parent;
        }

        Placement getPlacement() {
            return placement;
        }

        CrossAlignment getAlignment() {
            return alignment;
        }

        float getProgress() {
            return progress;
        }

        boolean isDetaching() {
            return detaching;
        }

        Tile other(Tile tile) {
            if (tile == child) {
                return parent;
            }
            return tile == parent ? child : null;
        }
    }

    private static final class TileState {
        private final Tile tile;
        private final MotionValue x = new MotionValue(0.0F);
        private final MotionValue y = new MotionValue(0.0F);
        private float width;
        private float height;
        private boolean persistCoordinates;

        private TileState(Tile tile) {
            this.tile = tile;
        }

        private void initialize(float x, float y, float width, float height, boolean followsInputPosition) {
            this.width = width;
            this.height = height;
            this.x.snapTo(x);
            this.y.snapTo(y);
            this.persistCoordinates = !followsInputPosition;
        }

        private boolean setSize(float width, float height) {
            boolean changed = Math.abs(this.width - width) > TOUCH_EPSILON
                    || Math.abs(this.height - height) > TOUCH_EPSILON;
            this.width = width;
            this.height = height;
            return changed;
        }

        private void snapTo(float x, float y) {
            this.x.snapTo(x);
            this.y.snapTo(y);
        }

        private void setTarget(float x, float y) {
            this.x.setTarget(x);
            this.y.setTarget(y);
        }

        private void translate(float deltaX, float deltaY) {
            snapTo(x.get() + deltaX, y.get() + deltaY);
        }

        private void claimCoordinatePersistence() {
            persistCoordinates = true;
        }

        private boolean shouldPersistCoordinates() {
            return persistCoordinates;
        }

        private boolean contains(float mouseX, float mouseY) {
            return mouseX >= x.get() && mouseX <= x.get() + width
                    && mouseY >= y.get() && mouseY <= y.get() + height;
        }

        private void reset() {
            width = 0.0F;
            height = 0.0F;
            x.snapTo(0.0F);
            y.snapTo(0.0F);
            persistCoordinates = false;
        }
    }

    private static final class Link {
        private final Tile child;
        private final Tile parent;
        private final Placement placement;
        private final CrossAlignment alignment;
        private final MotionValue progress;
        private boolean detaching;

        private Link(Tile child, Tile parent, Placement placement, CrossAlignment alignment, boolean alreadyFused) {
            this.child = child;
            this.parent = parent;
            this.placement = placement;
            this.alignment = alignment;
            this.progress = new MotionValue(alreadyFused ? 1.0F : 0.0F);
            if (!alreadyFused) {
                this.progress.setTarget(1.0F);
            }
        }

        private void detach() {
            detaching = true;
            progress.setTarget(0.0F);
        }

        private Tile other(Tile tile) {
            if (tile == child) {
                return parent;
            }
            return tile == parent ? child : null;
        }

        private boolean connects(Tile first, Tile second) {
            return (child == first && parent == second) || (child == second && parent == first);
        }

        private boolean uses(Tile tile, Side side) {
            if (tile == child) {
                return childSide(placement) == side;
            }
            return tile == parent && parentSide(placement) == side;
        }
    }

    private static final class SnapCandidate {
        private final TileState child;
        private final TileState parent;
        private final Placement placement;
        private final CrossAlignment alignment;
        private final float targetX;
        private final float targetY;
        private final float deltaX;
        private final float deltaY;
        private final float distance;

        private SnapCandidate(TileState child, TileState parent, Placement placement, CrossAlignment alignment,
                              float targetX, float targetY, float deltaX, float deltaY, float distance) {
            this.child = child;
            this.parent = parent;
            this.placement = placement;
            this.alignment = alignment;
            this.targetX = targetX;
            this.targetY = targetY;
            this.deltaX = deltaX;
            this.deltaY = deltaY;
            this.distance = distance;
        }
    }

    private static final class Position {
        private final float x;
        private final float y;

        private Position(float x, float y) {
            this.x = x;
            this.y = y;
        }
    }

    private static final class Delta {
        private final float x;
        private final float y;

        private Delta(float x, float y) {
            this.x = x;
            this.y = y;
        }
    }

    private static float requireFinite(float value, String name) {
        if (Float.isNaN(value) || Float.isInfinite(value)) {
            throw new IllegalArgumentException(name + " must be finite");
        }
        return value;
    }
}
