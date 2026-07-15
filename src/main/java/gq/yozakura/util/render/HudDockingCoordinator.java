package gq.yozakura.util.render;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Retained logical-pixel layout for the Night Bloom HUD dock graph.
 *
 * <p>The coordinator deliberately contains no OpenGL or Minecraft calls.  Renderers receive a
 * snapshot containing positions, edge joins, liquid bridges and island composites, so all HUD
 * backgrounds use the exact same connection geometry.</p>
 */
public final class HudDockingCoordinator {
    public static final float SAFE_MARGIN = 6.0F;
    public static final float SNAP_DISTANCE = 8.0F;
    public static final float SPLIT_GAP = 4.0F;
    public static final float POSITION_DURATION_SECONDS = 0.20F;
    public static final float LIQUID_DURATION_SECONDS = 0.42F;

    private static final float EPSILON = 0.01F;
    private static final float TOUCH_EPSILON = 0.35F;
    private static final float MAGNETIC_PULL = 0.42F;
    private static final float EDGE_EXPANSION_START = 0.16F;
    private static final float ISLAND_EXPANSION_START = 0.32F;
    private static final float NECK_FADE_END = 0.24F;
    private static final float MIN_NECK_RATIO = 0.22F;

    public enum Side {
        LEFT,
        RIGHT,
        TOP,
        BOTTOM;

        public static Set<Side> all() {
            return EnumSet.allOf(Side.class);
        }
    }

    public enum Placement {
        LEFT_OF,
        RIGHT_OF,
        ABOVE,
        BELOW
    }

    public enum Alignment {
        START,
        CENTER,
        END
    }

    private final Map<String, NodeState> nodes = new LinkedHashMap<String, NodeState>();
    private final List<Link> links = new ArrayList<Link>();
    private final Map<String, Position> dragStarts = new HashMap<String, Position>();
    private String activeId;
    private String selectedId;
    private float pointerStartX;
    private float pointerStartY;
    private float screenWidth;
    private float screenHeight;
    private boolean previousRightDown;
    private boolean rightPressPending;
    private float rightPressX;
    private float rightPressY;
    private SnapCandidate preview;
    private boolean dirty;

    public void reset() {
        nodes.clear();
        links.clear();
        dragStarts.clear();
        activeId = null;
        selectedId = null;
        preview = null;
        previousRightDown = false;
        rightPressPending = false;
        dirty = false;
    }

    /** Lets an externally owned proxy, such as the independently draggable watermark tiles, snap on release. */
    public Snapshot attachNearest(String id) {
        NodeState child = nodes.get(id);
        if (child == null || !child.visible) {
            return snapshot();
        }
        SnapCandidate best = null;
        for (NodeState parent : nodes.values()) {
            if (parent == child || !parent.visible) {
                continue;
            }
            for (Placement placement : Placement.values()) {
                for (Alignment alignment : Alignment.values()) {
                    best = closer(best, candidate(child, parent, placement, alignment));
                }
            }
        }
        if (best != null) {
            child.setTarget(best.targetX, best.targetY);
            links.add(new Link(best.child.id, best.parent.id, best.placement, best.alignment));
            dirty = true;
        }
        return snapshot();
    }

    /** Starts the same liquid split used by a right-click without requiring an invisible proxy hit box. */
    public Snapshot detach(String id) {
        detachNode(nodes.get(id));
        return snapshot();
    }

    public Snapshot update(Frame frame) {
        if (frame == null) {
            throw new IllegalArgumentException("frame must not be null");
        }
        dirty = false;
        screenWidth = Math.max(0.0F, frame.screenWidth);
        screenHeight = Math.max(0.0F, frame.screenHeight);
        configureNodes(frame.nodes);

        if (!frame.editMode) {
            cancelActiveDrag();
            selectedId = null;
            preview = null;
            rightPressPending = false;
            previousRightDown = frame.rightDown;
            reflowLinks();
            updateAnimations(frame.deltaSeconds);
            return snapshot();
        }

        if (!frame.processInput) {
            if (activeId == null) {
                reflowLinks();
            }
            updateAnimations(frame.deltaSeconds);
            return snapshot();
        }

        if (frame.escapeDown) {
            cancelActiveDrag();
        }
        if (frame.rightDown && !previousRightDown) {
            rightPressPending = true;
            rightPressX = frame.mouseX;
            rightPressY = frame.mouseY;
        }
        if (rightPressPending && frame.rightDown && splitAt(rightPressX, rightPressY)) {
            rightPressPending = false;
        }

        if (frame.leftDown && !frame.rightDown && !frame.escapeDown) {
            if (activeId == null) {
                beginDrag(frame.mouseX, frame.mouseY);
            }
            if (activeId != null) {
                updateDrag(frame.mouseX, frame.mouseY);
            }
        } else if (activeId != null) {
            releaseDrag();
        }

        if (!frame.rightDown) {
            rightPressPending = false;
        }
        previousRightDown = frame.rightDown;
        if (activeId == null) {
            reflowLinks();
        }
        updateAnimations(frame.deltaSeconds);
        return snapshot();
    }

    private void configureNodes(List<NodeInput> inputs) {
        Set<String> currentIds = new HashSet<String>();
        boolean sizeChanged = false;
        List<String> resizedIds = new ArrayList<String>();
        for (NodeInput input : inputs) {
            currentIds.add(input.id);
            NodeState state = nodes.get(input.id);
            if (state == null) {
                state = new NodeState(input);
                nodes.put(input.id, state);
                continue;
            }
            boolean resized = state.update(input, activeId == null && !hasActiveLink(input.id));
            sizeChanged |= resized;
            if (resized) {
                resizedIds.add(input.id);
            }
        }

        Iterator<Map.Entry<String, NodeState>> iterator = nodes.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, NodeState> entry = iterator.next();
            if (!currentIds.contains(entry.getKey())) {
                removeLinksFor(entry.getKey());
                dragStarts.remove(entry.getKey());
                if (entry.getKey().equals(activeId)) {
                    activeId = null;
                }
                iterator.remove();
            }
        }
        if (sizeChanged && activeId == null) {
            for (String id : resizedIds) {
                reflowFromAnchor(id, null, new HashSet<String>());
            }
            reflowLinks();
        }
    }

    private void removeLinksFor(String id) {
        Iterator<Link> iterator = links.iterator();
        while (iterator.hasNext()) {
            if (iterator.next().connects(id)) {
                iterator.remove();
            }
        }
    }

    private void beginDrag(float mouseX, float mouseY) {
        NodeState hit = hit(mouseX, mouseY);
        if (hit == null || !hit.movable) {
            return;
        }
        activeId = hit.id;
        selectedId = hit.id;
        pointerStartX = mouseX;
        pointerStartY = mouseY;
        dragStarts.clear();
        for (String id : linkedComponent(activeId, null)) {
            NodeState state = nodes.get(id);
            if (state != null && state.visible) {
                dragStarts.put(id, new Position(state.x.get(), state.y.get()));
            }
        }
        preview = null;
    }

    private void updateDrag(float mouseX, float mouseY) {
        if (activeId == null || dragStarts.isEmpty()) {
            return;
        }
        float deltaX = mouseX - pointerStartX;
        float deltaY = mouseY - pointerStartY;
        if (Math.abs(deltaX) <= TOUCH_EPSILON && Math.abs(deltaY) <= TOUCH_EPSILON) {
            restoreDragStarts();
            preview = null;
            return;
        }
        Delta clamped = clampGroupDelta(deltaX, deltaY);
        applyDragDelta(clamped);
        preview = findSnapCandidate();
        if (preview != null) {
            float pull = previewProgress(preview) * MAGNETIC_PULL;
            Delta magnetized = clampGroupDelta(clamped.x + preview.deltaX * pull,
                    clamped.y + preview.deltaY * pull);
            applyDragDelta(magnetized);
        } else {
            Delta snapped = screenSnapDelta();
            if (snapped.x != 0.0F || snapped.y != 0.0F) {
                applyDragDelta(new Delta(clamped.x + snapped.x, clamped.y + snapped.y));
            }
        }
    }

    private void restoreDragStarts() {
        for (Map.Entry<String, Position> entry : dragStarts.entrySet()) {
            NodeState state = nodes.get(entry.getKey());
            if (state != null) {
                state.snapTo(entry.getValue().x, entry.getValue().y);
            }
        }
    }

    private void applyDragDelta(Delta delta) {
        for (Map.Entry<String, Position> entry : dragStarts.entrySet()) {
            NodeState state = nodes.get(entry.getKey());
            if (state != null) {
                state.snapTo(entry.getValue().x + delta.x, entry.getValue().y + delta.y);
            }
        }
    }

    private Delta clampGroupDelta(float deltaX, float deltaY) {
        float minX = Float.MAX_VALUE;
        float minY = Float.MAX_VALUE;
        float maxX = -Float.MAX_VALUE;
        float maxY = -Float.MAX_VALUE;
        for (Map.Entry<String, Position> entry : dragStarts.entrySet()) {
            NodeState state = nodes.get(entry.getKey());
            if (state == null) {
                continue;
            }
            Position position = entry.getValue();
            minX = Math.min(minX, position.x);
            minY = Math.min(minY, position.y);
            maxX = Math.max(maxX, position.x + state.width);
            maxY = Math.max(maxY, position.y + state.height);
        }
        if (minX == Float.MAX_VALUE) {
            return new Delta(0.0F, 0.0F);
        }
        float groupWidth = maxX - minX;
        float groupHeight = maxY - minY;
        float left = safeMargin(screenWidth, groupWidth);
        float top = safeMargin(screenHeight, groupHeight);
        float right = Math.max(left, screenWidth - groupWidth - left);
        float bottom = Math.max(top, screenHeight - groupHeight - top);
        float nextX = clamp(minX + deltaX, left, right);
        float nextY = clamp(minY + deltaY, top, bottom);
        return new Delta(nextX - minX, nextY - minY);
    }

    private Delta screenSnapDelta() {
        if (dragStarts.isEmpty()) {
            return new Delta(0.0F, 0.0F);
        }
        float minX = Float.MAX_VALUE;
        float minY = Float.MAX_VALUE;
        float maxX = -Float.MAX_VALUE;
        float maxY = -Float.MAX_VALUE;
        for (String id : dragStarts.keySet()) {
            NodeState state = nodes.get(id);
            if (state == null) {
                continue;
            }
            minX = Math.min(minX, state.x.get());
            minY = Math.min(minY, state.y.get());
            maxX = Math.max(maxX, state.x.get() + state.width);
            maxY = Math.max(maxY, state.y.get() + state.height);
        }
        float dx = nearestSnap(minX, (minX + maxX) * 0.5F, maxX,
                SAFE_MARGIN, screenWidth * 0.5F, screenWidth - SAFE_MARGIN);
        float dy = nearestSnap(minY, (minY + maxY) * 0.5F, maxY,
                SAFE_MARGIN, screenHeight * 0.5F, screenHeight - SAFE_MARGIN);
        return new Delta(dx, dy);
    }

    private static float nearestSnap(float start, float center, float end,
                                     float startTarget, float centerTarget, float endTarget) {
        float best = 0.0F;
        float distance = SNAP_DISTANCE;
        float[] source = new float[]{start, center, end};
        float[] target = new float[]{startTarget, centerTarget, endTarget};
        for (int index = 0; index < source.length; index++) {
            float candidate = target[index] - source[index];
            float candidateDistance = Math.abs(candidate);
            if (candidateDistance <= distance) {
                best = candidate;
                distance = candidateDistance;
            }
        }
        return best;
    }

    private void releaseDrag() {
        if (activeId == null) {
            return;
        }
        boolean moved = hasDraggedSinceStart();
        if (moved && preview != null && canAttach(preview.child, preview.parent, preview.placement)) {
            float snapX = preview.targetX - preview.child.x.get();
            float snapY = preview.targetY - preview.child.y.get();
            for (String id : dragStarts.keySet()) {
                NodeState state = nodes.get(id);
                if (state != null) {
                    state.setTarget(state.x.get() + snapX, state.y.get() + snapY);
                }
            }
            links.add(new Link(preview.child.id, preview.parent.id, preview.placement, preview.alignment));
            dirty = true;
        }
        if (moved) {
            dirty = true;
        }
        activeId = null;
        dragStarts.clear();
        preview = null;
    }

    private boolean hasDraggedSinceStart() {
        for (Map.Entry<String, Position> entry : dragStarts.entrySet()) {
            NodeState state = nodes.get(entry.getKey());
            if (state != null && (Math.abs(state.x.getTarget() - entry.getValue().x) > TOUCH_EPSILON
                    || Math.abs(state.y.getTarget() - entry.getValue().y) > TOUCH_EPSILON)) {
                return true;
            }
        }
        return false;
    }

    private void cancelActiveDrag() {
        if (activeId == null) {
            return;
        }
        restoreDragStarts();
        activeId = null;
        dragStarts.clear();
        preview = null;
    }

    private boolean splitAt(float mouseX, float mouseY) {
        return detachNode(hit(mouseX, mouseY));
    }

    private boolean detachNode(NodeState hit) {
        if (hit == null) {
            return false;
        }
        selectedId = hit.id;
        Set<String> group = linkedComponent(hit.id, null);
        if (group.size() < 2) {
            return true;
        }
        Map<String, Delta> shifts = new HashMap<String, Delta>();
        for (String id : group) {
            shifts.put(id, new Delta(0.0F, 0.0F));
        }
        float halfGap = SPLIT_GAP * 0.5F;
        for (Link link : links) {
            if (link.detaching || !link.connects(hit.id) || !group.contains(link.childId)
                    || !group.contains(link.parentId)) {
                continue;
            }
            moveApart(shifts, link.childId, link.parentId, link.placement, halfGap);
            link.detach();
        }
        for (String id : group) {
            NodeState state = nodes.get(id);
            Delta shift = shifts.get(id);
            if (state != null && shift != null) {
                state.setTarget(state.x.get() + shift.x, state.y.get() + shift.y);
            }
        }
        activeId = null;
        dragStarts.clear();
        preview = null;
        dirty = true;
        return true;
    }

    private static void moveApart(Map<String, Delta> shifts, String childId, String parentId,
                                  Placement placement, float halfGap) {
        Delta child = shifts.get(childId);
        Delta parent = shifts.get(parentId);
        if (placement == Placement.LEFT_OF) {
            shifts.put(childId, child.add(-halfGap, 0.0F));
            shifts.put(parentId, parent.add(halfGap, 0.0F));
        } else if (placement == Placement.RIGHT_OF) {
            shifts.put(childId, child.add(halfGap, 0.0F));
            shifts.put(parentId, parent.add(-halfGap, 0.0F));
        } else if (placement == Placement.ABOVE) {
            shifts.put(childId, child.add(0.0F, -halfGap));
            shifts.put(parentId, parent.add(0.0F, halfGap));
        } else {
            shifts.put(childId, child.add(0.0F, halfGap));
            shifts.put(parentId, parent.add(0.0F, -halfGap));
        }
    }

    private SnapCandidate findSnapCandidate() {
        if (activeId == null) {
            return null;
        }
        Set<String> moving = linkedComponent(activeId, null);
        SnapCandidate best = null;
        for (String movingId : dragStarts.keySet()) {
            NodeState child = nodes.get(movingId);
            if (child == null) {
                continue;
            }
            for (NodeState parent : nodes.values()) {
                if (moving.contains(parent.id)) {
                    continue;
                }
                for (Placement placement : Placement.values()) {
                    for (Alignment alignment : Alignment.values()) {
                        best = closer(best, candidate(child, parent, placement, alignment));
                    }
                }
            }
        }
        return best;
    }

    private SnapCandidate candidate(NodeState child, NodeState parent, Placement placement, Alignment alignment) {
        if (!canAttach(child, parent, placement)) {
            return null;
        }
        Position desired = resolvedPosition(child, parent, placement, alignment, false);
        float deltaX = desired.x - child.x.get();
        float deltaY = desired.y - child.y.get();
        float distance = (float) Math.sqrt(deltaX * deltaX + deltaY * deltaY);
        if (distance > SNAP_DISTANCE) {
            return null;
        }
        return new SnapCandidate(child, parent, placement, alignment,
                desired.x, desired.y, deltaX, deltaY, distance);
    }

    private boolean canAttach(NodeState child, NodeState parent, Placement placement) {
        if (child == null || parent == null || child == parent || placement == null) {
            return false;
        }
        if (!child.visible || !parent.visible) {
            return false;
        }
        if (linkedComponent(child.id, null).contains(parent.id)) {
            return false;
        }
        Side childSide = childSide(placement);
        Side parentSide = parentSide(placement);
        return child.supports(childSide) && parent.supports(parentSide)
                && isSideAvailable(child.id, childSide) && isSideAvailable(parent.id, parentSide);
    }

    private boolean isSideAvailable(String id, Side side) {
        for (Link link : links) {
            if (!link.detaching && link.uses(id, side)) {
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

    private static Position resolvedPosition(NodeState child, NodeState parent, Placement placement,
                                             Alignment alignment, boolean target) {
        float childX = target ? child.x.getTarget() : child.x.get();
        float childY = target ? child.y.getTarget() : child.y.get();
        float parentX = target ? parent.x.getTarget() : parent.x.get();
        float parentY = target ? parent.y.getTarget() : parent.y.get();
        if (placement == Placement.LEFT_OF) {
            return new Position(parentX - child.width, aligned(childY, child.height, parentY, parent.height, alignment));
        }
        if (placement == Placement.RIGHT_OF) {
            return new Position(parentX + parent.width,
                    aligned(childY, child.height, parentY, parent.height, alignment));
        }
        if (placement == Placement.ABOVE) {
            return new Position(aligned(childX, child.width, parentX, parent.width, alignment), parentY - child.height);
        }
        return new Position(aligned(childX, child.width, parentX, parent.width, alignment), parentY + parent.height);
    }

    private static float aligned(float current, float childSize, float parentStart, float parentSize,
                                 Alignment alignment) {
        if (alignment == Alignment.END) {
            return parentStart + parentSize - childSize;
        }
        if (alignment == Alignment.CENTER) {
            return parentStart + (parentSize - childSize) * 0.5F;
        }
        return parentStart;
    }

    private static SnapCandidate closer(SnapCandidate current, SnapCandidate candidate) {
        if (candidate == null) {
            return current;
        }
        return current == null || candidate.distance < current.distance ? candidate : current;
    }

    private void reflowLinks() {
        for (int pass = 0; pass < nodes.size(); pass++) {
            for (Link link : links) {
                if (link.detaching) {
                    continue;
                }
                NodeState child = nodes.get(link.childId);
                NodeState parent = nodes.get(link.parentId);
                if (child == null || parent == null || !child.visible || !parent.visible) {
                    continue;
                }
                Position desired = resolvedPosition(child, parent, link.placement, link.alignment, true);
                float dx = desired.x - child.x.getTarget();
                float dy = desired.y - child.y.getTarget();
                if (Math.abs(dx) <= TOUCH_EPSILON && Math.abs(dy) <= TOUCH_EPSILON) {
                    continue;
                }
                for (String id : linkedComponent(link.childId, link)) {
                    NodeState state = nodes.get(id);
                    if (state != null) {
                        state.setTarget(state.x.getTarget() + dx, state.y.getTarget() + dy);
                    }
                }
                dirty = true;
            }
        }
    }

    /**
     * A widget whose content changes size is the stable visual anchor for that update.  Moving
     * the opposite branch avoids the familiar "panel grows into its neighbour" overlap and keeps
     * vertically fused HUDs reading as one Dynamic-Island-like object.
     */
    private void reflowFromAnchor(String anchorId, Link ignored, Set<String> visited) {
        if (anchorId == null || !visited.add(anchorId)) {
            return;
        }
        NodeState anchor = nodes.get(anchorId);
        if (anchor == null || !anchor.visible) {
            return;
        }
        for (Link link : links) {
            if (link == ignored || link.detaching || !link.connects(anchorId)) {
                continue;
            }
            String otherId = link.other(anchorId);
            NodeState other = nodes.get(otherId);
            if (other == null || !other.visible) {
                continue;
            }
            Position desired = link.childId.equals(anchorId)
                    ? reversedPosition(anchor, other, link.placement, link.alignment)
                    : resolvedPosition(other, anchor, link.placement, link.alignment, true);
            other.setTarget(desired.x, desired.y);
            reflowFromAnchor(otherId, link, visited);
            dirty = true;
        }
    }

    private static Position reversedPosition(NodeState child, NodeState parent, Placement placement,
                                             Alignment alignment) {
        float childX = child.x.getTarget();
        float childY = child.y.getTarget();
        if (placement == Placement.LEFT_OF) {
            return new Position(childX + child.width,
                    reverseAligned(childY, child.height, parent.height, alignment));
        }
        if (placement == Placement.RIGHT_OF) {
            return new Position(childX - parent.width,
                    reverseAligned(childY, child.height, parent.height, alignment));
        }
        if (placement == Placement.ABOVE) {
            return new Position(reverseAligned(childX, child.width, parent.width, alignment),
                    childY + child.height);
        }
        return new Position(reverseAligned(childX, child.width, parent.width, alignment),
                childY - parent.height);
    }

    private static float reverseAligned(float childStart, float childSize, float parentSize,
                                        Alignment alignment) {
        if (alignment == Alignment.END) {
            return childStart - parentSize + childSize;
        }
        if (alignment == Alignment.CENTER) {
            return childStart - (parentSize - childSize) * 0.5F;
        }
        return childStart;
    }

    private void updateAnimations(float deltaSeconds) {
        float delta = Math.max(0.0F, Math.min(0.25F, deltaSeconds));
        for (Link link : links) {
            link.progress.update(delta, LIQUID_DURATION_SECONDS);
        }
        Iterator<Link> iterator = links.iterator();
        while (iterator.hasNext()) {
            Link link = iterator.next();
            if (link.detaching && link.progress.get() <= EPSILON) {
                iterator.remove();
            }
        }
        for (NodeState state : nodes.values()) {
            state.x.update(delta, POSITION_DURATION_SECONDS);
            state.y.update(delta, POSITION_DURATION_SECONDS);
        }
    }

    private NodeState hit(float mouseX, float mouseY) {
        List<NodeState> ordered = new ArrayList<NodeState>(nodes.values());
        for (int index = ordered.size() - 1; index >= 0; index--) {
            NodeState state = ordered.get(index);
            if (state.contains(mouseX, mouseY)) {
                return state;
            }
        }
        return null;
    }

    private Set<String> linkedComponent(String start, Link ignored) {
        Set<String> visited = new HashSet<String>();
        if (start == null || !nodes.containsKey(start)) {
            return visited;
        }
        ArrayDeque<String> queue = new ArrayDeque<String>();
        queue.add(start);
        visited.add(start);
        while (!queue.isEmpty()) {
            String current = queue.removeFirst();
            for (Link link : links) {
                if (link == ignored || link.detaching) {
                    continue;
                }
                String next = link.other(current);
                if (next != null && visited.add(next)) {
                    queue.addLast(next);
                }
            }
        }
        return visited;
    }

    private boolean hasActiveLink(String id) {
        for (Link link : links) {
            if (!link.detaching && link.connects(id)) {
                return true;
            }
        }
        return false;
    }

    private Snapshot snapshot() {
        Map<String, NodeView> nodeViews = new LinkedHashMap<String, NodeView>();
        for (NodeState state : nodes.values()) {
            nodeViews.put(state.id, new NodeView(state.id, state.x.get(), state.y.get(),
                    state.x.getTarget(), state.y.getTarget(), state.width, state.height, state.radius,
                    state.compositeEligible, state.visible));
        }
        List<LinkView> linkViews = new ArrayList<LinkView>();
        List<Bridge> bridges = new ArrayList<Bridge>();
        for (Link link : links) {
            NodeView child = nodeViews.get(link.childId);
            NodeView parent = nodeViews.get(link.parentId);
            if (child == null || parent == null) {
                continue;
            }
            LinkView view = new LinkView(link.childId, link.parentId, link.placement, link.alignment,
                    link.progress.get(), link.detaching);
            linkViews.add(view);
            if (!child.visible || !parent.visible) {
                continue;
            }
            Bridge bridge = Bridge.create(child, parent, view);
            if (bridge.getProgress() > EPSILON) {
                bridges.add(bridge);
            }
        }
        List<Composite> composites = composites(nodeViews, bridges);
        Map<String, Float> compositeProgress = new HashMap<String, Float>();
        for (Composite composite : composites) {
            for (String id : composite.ids) {
                Float existing = compositeProgress.get(id);
                compositeProgress.put(id, existing == null ? composite.progress : Math.max(existing, composite.progress));
            }
        }
        Map<String, Surface> surfaces = new LinkedHashMap<String, Surface>();
        for (NodeView node : nodeViews.values()) {
            SurfaceBuilder builder = new SurfaceBuilder(node);
            for (Bridge bridge : bridges) {
                builder.apply(bridge);
            }
            Float island = compositeProgress.get(node.id);
            surfaces.put(node.id, builder.build(island == null ? 1.0F : 1.0F - island));
        }
        LinkView previewView = preview == null ? null : new LinkView(preview.child.id, preview.parent.id,
                preview.placement, preview.alignment, previewProgress(preview), false);
        return new Snapshot(nodeViews, linkViews, bridges, composites, surfaces,
                previewView, activeId, selectedId, dirty);
    }

    private List<Composite> composites(Map<String, NodeView> nodeViews, List<Bridge> bridges) {
        List<Composite> result = new ArrayList<Composite>();
        Set<String> visited = new HashSet<String>();
        for (NodeView start : nodeViews.values()) {
            if (!visited.add(start.id)) {
                continue;
            }
            Set<String> group = new HashSet<String>();
            ArrayDeque<String> queue = new ArrayDeque<String>();
            group.add(start.id);
            queue.add(start.id);
            float progress = 1.0F;
            boolean hasBridge = false;
            while (!queue.isEmpty()) {
                String id = queue.removeFirst();
                for (Bridge bridge : bridges) {
                    if (!bridge.connects(id)) {
                        continue;
                    }
                    hasBridge = true;
                    progress = Math.min(progress, bridge.getCompositeProgress());
                    String other = bridge.other(id);
                    if (other != null && group.add(other)) {
                        visited.add(other);
                        queue.addLast(other);
                    }
                }
            }
            if (!hasBridge || group.size() < 2 || progress <= EPSILON) {
                continue;
            }
            float left = Float.MAX_VALUE;
            float top = Float.MAX_VALUE;
            float right = -Float.MAX_VALUE;
            float bottom = -Float.MAX_VALUE;
            float radius = 0.0F;
            String owner = null;
            boolean allEligible = true;
            for (String id : group) {
                NodeView node = nodeViews.get(id);
                if (node == null) {
                    continue;
                }
                left = Math.min(left, node.x);
                top = Math.min(top, node.y);
                right = Math.max(right, node.getRight());
                bottom = Math.max(bottom, node.getBottom());
                radius = Math.max(radius, node.radius);
                allEligible &= node.compositeEligible;
                if (owner == null || id.compareTo(owner) < 0) {
                    owner = id;
                }
            }
            if (allEligible) {
                result.add(new Composite(group, owner, left, top, right, bottom, radius, progress));
            }
        }
        return result;
    }

    private float previewProgress(SnapCandidate candidate) {
        float raw = clamp(1.0F - candidate.distance / SNAP_DISTANCE, 0.0F, 1.0F);
        return smoothStep(raw);
    }

    private static float safeMargin(float screen, float size) {
        return Math.min(SAFE_MARGIN, Math.max(0.0F, (screen - size) * 0.5F));
    }

    private static float smoothStep(float value) {
        float clamped = clamp(value, 0.0F, 1.0F);
        return clamped * clamped * (3.0F - 2.0F * clamped);
    }

    private static float clamp(float value, float minimum, float maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private static float finite(float value, String name) {
        if (Float.isNaN(value) || Float.isInfinite(value)) {
            throw new IllegalArgumentException(name + " must be finite");
        }
        return value;
    }

    public static final class Frame {
        private final float screenWidth;
        private final float screenHeight;
        private final float deltaSeconds;
        private final boolean editMode;
        private final float mouseX;
        private final float mouseY;
        private final boolean leftDown;
        private final boolean rightDown;
        private final boolean escapeDown;
        private final boolean processInput;
        private final List<NodeInput> nodes;

        public Frame(float screenWidth, float screenHeight, float deltaSeconds, boolean editMode,
                     float mouseX, float mouseY, boolean leftDown, boolean rightDown, boolean escapeDown,
                     List<NodeInput> nodes) {
            this(screenWidth, screenHeight, deltaSeconds, editMode, mouseX, mouseY, leftDown, rightDown,
                    escapeDown, true, nodes);
        }

        public Frame(float screenWidth, float screenHeight, float deltaSeconds, boolean editMode,
                     float mouseX, float mouseY, boolean leftDown, boolean rightDown, boolean escapeDown,
                     boolean processInput, List<NodeInput> nodes) {
            this.screenWidth = Math.max(0.0F, finite(screenWidth, "screenWidth"));
            this.screenHeight = Math.max(0.0F, finite(screenHeight, "screenHeight"));
            this.deltaSeconds = Math.max(0.0F, finite(deltaSeconds, "deltaSeconds"));
            this.editMode = editMode;
            this.mouseX = finite(mouseX, "mouseX");
            this.mouseY = finite(mouseY, "mouseY");
            this.leftDown = leftDown;
            this.rightDown = rightDown;
            this.escapeDown = escapeDown;
            this.processInput = processInput;
            this.nodes = new ArrayList<NodeInput>();
            if (nodes != null) {
                Set<String> ids = new HashSet<String>();
                for (NodeInput input : nodes) {
                    if (input == null || !ids.add(input.id)) {
                        throw new IllegalArgumentException("node inputs must be non-null and unique");
                    }
                    this.nodes.add(input);
                }
            }
        }
    }

    public static final class NodeInput {
        private final String id;
        private final float x;
        private final float y;
        private final float width;
        private final float height;
        private final float radius;
        private final Set<Side> sides;
        private final boolean movable;
        private final boolean compositeEligible;
        private final boolean visible;

        public NodeInput(String id, float x, float y, float width, float height, Set<Side> sides) {
            this(id, x, y, width, height, 4.0F, sides);
        }

        public NodeInput(String id, float x, float y, float width, float height, float radius, Set<Side> sides) {
            this(id, x, y, width, height, radius, sides, true);
        }

        public NodeInput(String id, float x, float y, float width, float height, float radius, Set<Side> sides,
                         boolean movable) {
            this(id, x, y, width, height, radius, sides, movable, true);
        }

        public NodeInput(String id, float x, float y, float width, float height, float radius, Set<Side> sides,
                         boolean movable, boolean compositeEligible) {
            this(id, x, y, width, height, radius, sides, movable, compositeEligible, true);
        }

        public NodeInput(String id, float x, float y, float width, float height, float radius, Set<Side> sides,
                         boolean movable, boolean compositeEligible, boolean visible) {
            if (id == null || id.trim().isEmpty()) {
                throw new IllegalArgumentException("id must not be empty");
            }
            this.id = id;
            this.x = finite(x, "x");
            this.y = finite(y, "y");
            this.width = Math.max(0.0F, finite(width, "width"));
            this.height = Math.max(0.0F, finite(height, "height"));
            this.radius = Math.max(0.0F, finite(radius, "radius"));
            this.sides = sides == null || sides.isEmpty()
                    ? Collections.<Side>emptySet() : EnumSet.copyOf(sides);
            this.movable = movable;
            this.compositeEligible = compositeEligible;
            this.visible = visible;
        }
    }

    public static final class Snapshot {
        private final Map<String, NodeView> nodes;
        private final List<LinkView> links;
        private final List<Bridge> bridges;
        private final List<Composite> composites;
        private final Map<String, Surface> surfaces;
        private final LinkView preview;
        private final String activeId;
        private final String selectedId;
        private final boolean dirty;

        private Snapshot(Map<String, NodeView> nodes, List<LinkView> links, List<Bridge> bridges,
                         List<Composite> composites, Map<String, Surface> surfaces, LinkView preview,
                         String activeId, String selectedId, boolean dirty) {
            this.nodes = new LinkedHashMap<String, NodeView>(nodes);
            this.links = new ArrayList<LinkView>(links);
            this.bridges = new ArrayList<Bridge>(bridges);
            this.composites = new ArrayList<Composite>(composites);
            this.surfaces = new LinkedHashMap<String, Surface>(surfaces);
            this.preview = preview;
            this.activeId = activeId;
            this.selectedId = selectedId;
            this.dirty = dirty;
        }

        public NodeView getNode(String id) {
            return nodes.get(id);
        }

        public List<NodeView> getNodes() {
            return new ArrayList<NodeView>(nodes.values());
        }

        public List<LinkView> getLinks() {
            return new ArrayList<LinkView>(links);
        }

        public List<Bridge> getBridges() {
            return new ArrayList<Bridge>(bridges);
        }

        public List<Composite> getComposites() {
            return new ArrayList<Composite>(composites);
        }

        public Surface getSurface(String id) {
            return surfaces.get(id);
        }

        public LinkView getPreview() {
            return preview;
        }

        public boolean isDragging(String id) {
            return id != null && id.equals(activeId);
        }

        public boolean isSelected(String id) {
            return id != null && id.equals(selectedId);
        }

        public boolean isDocked(String id) {
            if (id == null) {
                return false;
            }
            for (LinkView link : links) {
                if (!link.detaching && (id.equals(link.childId) || id.equals(link.parentId))) {
                    return true;
                }
            }
            return false;
        }

        public boolean hasLink(String id) {
            if (id == null) {
                return false;
            }
            for (LinkView link : links) {
                if (id.equals(link.childId) || id.equals(link.parentId)) {
                    return true;
                }
            }
            return false;
        }

        public boolean isDirty() {
            return dirty;
        }
    }

    public static final class NodeView {
        private final String id;
        private final float x;
        private final float y;
        private final float targetX;
        private final float targetY;
        private final float width;
        private final float height;
        private final float radius;
        private final boolean compositeEligible;
        private final boolean visible;

        private NodeView(String id, float x, float y, float targetX, float targetY,
                         float width, float height, float radius, boolean compositeEligible, boolean visible) {
            this.id = id;
            this.x = x;
            this.y = y;
            this.targetX = targetX;
            this.targetY = targetY;
            this.width = width;
            this.height = height;
            this.radius = radius;
            this.compositeEligible = compositeEligible;
            this.visible = visible;
        }

        public String getId() { return id; }
        public float getX() { return x; }
        public float getY() { return y; }
        public float getTargetX() { return targetX; }
        public float getTargetY() { return targetY; }
        public float getWidth() { return width; }
        public float getHeight() { return height; }
        public float getRadius() { return radius; }
        public boolean isCompositeEligible() { return compositeEligible; }
        public boolean isVisible() { return visible; }
        public float getRight() { return x + width; }
        public float getBottom() { return y + height; }
    }

    public static final class LinkView {
        private final String childId;
        private final String parentId;
        private final Placement placement;
        private final Alignment alignment;
        private final float progress;
        private final boolean detaching;

        private LinkView(String childId, String parentId, Placement placement, Alignment alignment,
                         float progress, boolean detaching) {
            this.childId = childId;
            this.parentId = parentId;
            this.placement = placement;
            this.alignment = alignment;
            this.progress = progress;
            this.detaching = detaching;
        }

        public String getChildId() { return childId; }
        public String getParentId() { return parentId; }
        public Placement getPlacement() { return placement; }
        public Alignment getAlignment() { return alignment; }
        public float getProgress() { return progress; }
        public boolean isDetaching() { return detaching; }
    }

    public static final class Bridge {
        private final boolean horizontal;
        private final String leadingId;
        private final String trailingId;
        private final float x;
        private final float y;
        private final float width;
        private final float height;
        private final float progress;
        private final boolean detaching;

        private Bridge(boolean horizontal, String leadingId, String trailingId, float x, float y,
                       float width, float height, float progress, boolean detaching) {
            this.horizontal = horizontal;
            this.leadingId = leadingId;
            this.trailingId = trailingId;
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
            this.progress = progress;
            this.detaching = detaching;
        }

        private static Bridge create(NodeView child, NodeView parent, LinkView link) {
            boolean horizontal = link.placement == Placement.LEFT_OF || link.placement == Placement.RIGHT_OF;
            if (horizontal) {
                NodeView left = child.x <= parent.x ? child : parent;
                NodeView right = left == child ? parent : child;
                float sharedTop = Math.max(left.y, right.y);
                float sharedBottom = Math.min(left.getBottom(), right.getBottom());
                float shared = Math.max(0.0F, sharedBottom - sharedTop);
                float neck = neckSize(shared, link.progress);
                float center = (sharedTop + sharedBottom) * 0.5F;
                float start = left.getRight();
                float end = right.x;
                return new Bridge(true, left.id, right.id, Math.min(start, end), center - neck * 0.5F,
                        Math.abs(end - start), neck, link.progress, link.detaching);
            }
            NodeView top = child.y <= parent.y ? child : parent;
            NodeView bottom = top == child ? parent : child;
            float sharedLeft = Math.max(top.x, bottom.x);
            float sharedRight = Math.min(top.getRight(), bottom.getRight());
            float shared = Math.max(0.0F, sharedRight - sharedLeft);
            float neck = neckSize(shared, link.progress);
            float center = (sharedLeft + sharedRight) * 0.5F;
            float start = top.getBottom();
            float end = bottom.y;
            return new Bridge(false, top.id, bottom.id, center - neck * 0.5F, Math.min(start, end),
                    neck, Math.abs(end - start), link.progress, link.detaching);
        }

        public boolean isHorizontal() { return horizontal; }
        public String getLeadingId() { return leadingId; }
        public String getTrailingId() { return trailingId; }
        public float getX() { return x; }
        public float getY() { return y; }
        public float getWidth() { return width; }
        public float getHeight() { return height; }
        public float getRight() { return x + width; }
        public float getBottom() { return y + height; }
        public float getProgress() { return progress; }
        public boolean isDetaching() { return detaching; }
        public float getOpacity() { return smoothStep(progress / NECK_FADE_END); }
        public float getEdgeProgress() { return smoothStep((progress - EDGE_EXPANSION_START) / (1.0F - EDGE_EXPANSION_START)); }
        public float getCompositeProgress() { return smoothStep((progress - ISLAND_EXPANSION_START) / (1.0F - ISLAND_EXPANSION_START)); }
        public float getRadius() { return Math.min(2.0F, Math.min(width, height) * 0.5F) * (1.0F - getEdgeProgress()); }
        public boolean isVisible() { return getOpacity() > EPSILON && width > EPSILON && height > EPSILON; }

        private boolean connects(String id) {
            return id != null && (id.equals(leadingId) || id.equals(trailingId));
        }

        private String other(String id) {
            if (id == null) {
                return null;
            }
            return id.equals(leadingId) ? trailingId : id.equals(trailingId) ? leadingId : null;
        }
    }

    public static final class Composite {
        private final Set<String> ids;
        private final String ownerId;
        private final float x;
        private final float y;
        private final float right;
        private final float bottom;
        private final float radius;
        private final float progress;

        private Composite(Set<String> ids, String ownerId, float x, float y, float right, float bottom,
                          float radius, float progress) {
            this.ids = new HashSet<String>(ids);
            this.ownerId = ownerId;
            this.x = x;
            this.y = y;
            this.right = right;
            this.bottom = bottom;
            this.radius = radius;
            this.progress = progress;
        }

        public boolean contains(String id) { return ids.contains(id); }
        public String getOwnerId() { return ownerId; }
        public float getX() { return x; }
        public float getY() { return y; }
        public float getRight() { return right; }
        public float getBottom() { return bottom; }
        public float getRadius() { return radius; }
        public float getProgress() { return progress; }
    }

    public static final class Surface {
        private final NodeView node;
        private final float topLeft;
        private final float topRight;
        private final float bottomRight;
        private final float bottomLeft;
        private final float topJoinStart;
        private final float topJoinEnd;
        private final float bottomJoinStart;
        private final float bottomJoinEnd;
        private final float leftJoinStart;
        private final float leftJoinEnd;
        private final float rightJoinStart;
        private final float rightJoinEnd;
        private final float individualOpacity;

        private Surface(NodeView node, float topLeft, float topRight, float bottomRight, float bottomLeft,
                        float topJoinStart, float topJoinEnd, float bottomJoinStart, float bottomJoinEnd,
                        float leftJoinStart, float leftJoinEnd, float rightJoinStart, float rightJoinEnd,
                        float individualOpacity) {
            this.node = node;
            this.topLeft = topLeft;
            this.topRight = topRight;
            this.bottomRight = bottomRight;
            this.bottomLeft = bottomLeft;
            this.topJoinStart = topJoinStart;
            this.topJoinEnd = topJoinEnd;
            this.bottomJoinStart = bottomJoinStart;
            this.bottomJoinEnd = bottomJoinEnd;
            this.leftJoinStart = leftJoinStart;
            this.leftJoinEnd = leftJoinEnd;
            this.rightJoinStart = rightJoinStart;
            this.rightJoinEnd = rightJoinEnd;
            this.individualOpacity = individualOpacity;
        }

        public NodeView getNode() { return node; }
        public float getTopLeft() { return topLeft; }
        public float getTopRight() { return topRight; }
        public float getBottomRight() { return bottomRight; }
        public float getBottomLeft() { return bottomLeft; }
        public float getTopJoinStart() { return topJoinStart; }
        public float getTopJoinEnd() { return topJoinEnd; }
        public float getBottomJoinStart() { return bottomJoinStart; }
        public float getBottomJoinEnd() { return bottomJoinEnd; }
        public float getLeftJoinStart() { return leftJoinStart; }
        public float getLeftJoinEnd() { return leftJoinEnd; }
        public float getRightJoinStart() { return rightJoinStart; }
        public float getRightJoinEnd() { return rightJoinEnd; }
        public float getIndividualOpacity() { return individualOpacity; }
    }

    private static final class NodeState {
        private final String id;
        private final Tween x;
        private final Tween y;
        private float width;
        private float height;
        private float radius;
        private Set<Side> sides;
        private boolean movable;
        private boolean compositeEligible;
        private boolean visible;
        private float lastInputX;
        private float lastInputY;

        private NodeState(NodeInput input) {
            id = input.id;
            x = new Tween(input.x);
            y = new Tween(input.y);
            width = input.width;
            height = input.height;
            radius = input.radius;
            sides = input.sides;
            movable = input.movable;
            compositeEligible = input.compositeEligible;
            visible = input.visible;
            lastInputX = input.x;
            lastInputY = input.y;
        }

        private boolean update(NodeInput input, boolean acceptsExternalPosition) {
            boolean sizeChanged = Math.abs(width - input.width) > TOUCH_EPSILON
                    || Math.abs(height - input.height) > TOUCH_EPSILON;
            width = input.width;
            height = input.height;
            radius = input.radius;
            sides = input.sides;
            movable = input.movable;
            compositeEligible = input.compositeEligible;
            visible = input.visible;
            boolean externalMoved = Math.abs(lastInputX - input.x) > TOUCH_EPSILON
                    || Math.abs(lastInputY - input.y) > TOUCH_EPSILON;
            if (acceptsExternalPosition && externalMoved) {
                x.snapTo(input.x);
                y.snapTo(input.y);
            }
            lastInputX = input.x;
            lastInputY = input.y;
            return sizeChanged;
        }

        private void snapTo(float x, float y) { this.x.snapTo(x); this.y.snapTo(y); }
        private void setTarget(float x, float y) { this.x.setTarget(x); this.y.setTarget(y); }
        private boolean supports(Side side) { return sides.contains(side); }
        private boolean contains(float mouseX, float mouseY) {
            return visible && mouseX >= x.get() && mouseX <= x.get() + width
                    && mouseY >= y.get() && mouseY <= y.get() + height;
        }
    }

    private static final class Link {
        private final String childId;
        private final String parentId;
        private final Placement placement;
        private final Alignment alignment;
        private final Progress progress = new Progress(0.0F);
        private boolean detaching;

        private Link(String childId, String parentId, Placement placement, Alignment alignment) {
            this.childId = childId;
            this.parentId = parentId;
            this.placement = placement;
            this.alignment = alignment;
            progress.setTarget(1.0F);
        }

        private void detach() { detaching = true; progress.setTarget(0.0F); }
        private boolean connects(String id) { return childId.equals(id) || parentId.equals(id); }
        private String other(String id) { return childId.equals(id) ? parentId : parentId.equals(id) ? childId : null; }
        private boolean uses(String id, Side side) {
            return childId.equals(id) ? childSide(placement) == side : parentId.equals(id) && parentSide(placement) == side;
        }
    }

    private static final class SurfaceBuilder {
        private final NodeView node;
        private float topLeft;
        private float topRight;
        private float bottomRight;
        private float bottomLeft;
        private float topStart;
        private float topEnd;
        private float bottomStart;
        private float bottomEnd;
        private float leftStart;
        private float leftEnd;
        private float rightStart;
        private float rightEnd;

        private SurfaceBuilder(NodeView node) {
            this.node = node;
            topLeft = node.radius;
            topRight = node.radius;
            bottomRight = node.radius;
            bottomLeft = node.radius;
            topStart = bottomStart = leftStart = rightStart = 1.0F;
            topEnd = bottomEnd = leftEnd = rightEnd = 0.0F;
        }

        private void apply(Bridge bridge) {
            if (!bridge.connects(node.id) || bridge.getEdgeProgress() <= EPSILON) {
                return;
            }
            float progress = bridge.getEdgeProgress();
            if (bridge.horizontal) {
                float start = clamp(bridge.y, node.y, node.getBottom());
                float end = clamp(bridge.getBottom(), node.y, node.getBottom());
                if (bridge.leadingId.equals(node.id)) {
                    rightStart = unionStart(rightStart, rightEnd, start);
                    rightEnd = unionEnd(rightStart, rightEnd, end);
                    if (start <= node.y + EPSILON) topRight *= 1.0F - progress;
                    if (end >= node.getBottom() - EPSILON) bottomRight *= 1.0F - progress;
                } else {
                    leftStart = unionStart(leftStart, leftEnd, start);
                    leftEnd = unionEnd(leftStart, leftEnd, end);
                    if (start <= node.y + EPSILON) topLeft *= 1.0F - progress;
                    if (end >= node.getBottom() - EPSILON) bottomLeft *= 1.0F - progress;
                }
            } else {
                float start = clamp(bridge.x, node.x, node.getRight());
                float end = clamp(bridge.getRight(), node.x, node.getRight());
                if (bridge.leadingId.equals(node.id)) {
                    bottomStart = unionStart(bottomStart, bottomEnd, start);
                    bottomEnd = unionEnd(bottomStart, bottomEnd, end);
                    if (start <= node.x + EPSILON) bottomLeft *= 1.0F - progress;
                    if (end >= node.getRight() - EPSILON) bottomRight *= 1.0F - progress;
                } else {
                    topStart = unionStart(topStart, topEnd, start);
                    topEnd = unionEnd(topStart, topEnd, end);
                    if (start <= node.x + EPSILON) topLeft *= 1.0F - progress;
                    if (end >= node.getRight() - EPSILON) topRight *= 1.0F - progress;
                }
            }
        }

        private Surface build(float individualOpacity) {
            return new Surface(node, topLeft, topRight, bottomRight, bottomLeft,
                    topStart, topEnd, bottomStart, bottomEnd, leftStart, leftEnd, rightStart, rightEnd,
                    individualOpacity);
        }

        private static float unionStart(float currentStart, float currentEnd, float next) {
            return currentEnd <= currentStart ? next : Math.min(currentStart, next);
        }

        private static float unionEnd(float currentStart, float currentEnd, float next) {
            return currentEnd <= currentStart ? next : Math.max(currentEnd, next);
        }
    }

    private static final class Tween {
        private float value;
        private float target;

        private Tween(float value) { this.value = value; this.target = value; }
        private float get() { return value; }
        private float getTarget() { return target; }
        private void setTarget(float value) { target = value; }
        private void snapTo(float value) { this.value = value; target = value; }
        private void update(float delta, float duration) {
            float progress = Math.min(1.0F, Math.max(0.0F, delta) / Math.max(EPSILON, duration));
            value += (target - value) * progress;
        }
    }

    private static final class Progress {
        private float value;
        private float target;

        private Progress(float value) { this.value = value; this.target = value; }
        private float get() { return value; }
        private void setTarget(float value) { target = clamp(value, 0.0F, 1.0F); }
        private void update(float delta, float duration) {
            float step = Math.max(0.0F, delta) / Math.max(EPSILON, duration);
            if (value < target) value = Math.min(target, value + step);
            else if (value > target) value = Math.max(target, value - step);
        }
    }

    private static final class SnapCandidate {
        private final NodeState child;
        private final NodeState parent;
        private final Placement placement;
        private final Alignment alignment;
        private final float targetX;
        private final float targetY;
        private final float deltaX;
        private final float deltaY;
        private final float distance;

        private SnapCandidate(NodeState child, NodeState parent, Placement placement, Alignment alignment,
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
        private Position(float x, float y) { this.x = x; this.y = y; }
    }

    private static final class Delta {
        private final float x;
        private final float y;
        private Delta(float x, float y) { this.x = x; this.y = y; }
        private Delta add(float x, float y) { return new Delta(this.x + x, this.y + y); }
    }

    private static float neckSize(float sharedSize, float progress) {
        if (sharedSize <= EPSILON || progress <= EPSILON) {
            return 0.0F;
        }
        float edge = smoothStep((progress - EDGE_EXPANSION_START) / (1.0F - EDGE_EXPANSION_START));
        float presence = smoothStep(progress / NECK_FADE_END);
        return sharedSize * presence * (MIN_NECK_RATIO + (1.0F - MIN_NECK_RATIO) * edge);
    }
}
