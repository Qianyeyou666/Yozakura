package gq.yozakura.module.render;

import gq.yozakura.util.render.HudDockingCoordinator;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Geometry for the soft bridge shown while Night Bloom watermark tiles join or split.
 * The bridge only occupies the gap between two tiles; the facing tile corners flatten
 * with the same progress so translucent surfaces never stack into a dark seam.
 */
final class NightBloomWatermarkLiquid {
    private static final float EPSILON = 0.01F;
    private static final float MIN_NECK_RATIO = 0.22F;
    private static final float NECK_FADE_END = 0.24F;
    private static final float EDGE_EXPANSION_START = 0.16F;
    private static final float ISLAND_EXPANSION_START = 0.32F;

    enum Axis {
        HORIZONTAL,
        VERTICAL
    }

    private NightBloomWatermarkLiquid() {
    }

    static Bridge bridge(NightBloomWatermarkLayout.TileView child,
                         NightBloomWatermarkLayout.TileView parent,
                         NightBloomWatermarkLayout.LinkView link) {
        if (child == null || parent == null || link == null) {
            return Bridge.empty();
        }
        float progress = clamp(link.getProgress());
        Axis axis = link.getPlacement() == NightBloomWatermarkLayout.Placement.LEFT_OF
                || link.getPlacement() == NightBloomWatermarkLayout.Placement.RIGHT_OF
                ? Axis.HORIZONTAL : Axis.VERTICAL;
        if (axis == Axis.HORIZONTAL) {
            NightBloomWatermarkLayout.TileView left = child.getX() <= parent.getX() ? child : parent;
            NightBloomWatermarkLayout.TileView right = left == child ? parent : child;
            float sharedTop = Math.max(left.getY(), right.getY());
            float sharedBottom = Math.min(left.getBottom(), right.getBottom());
            float sharedHeight = Math.max(0.0F, sharedBottom - sharedTop);
            float neckHeight = neckSize(sharedHeight, progress);
            float centerY = (sharedTop + sharedBottom) * 0.5F;
            float start = left.getRight();
            float end = right.getX();
            return new Bridge(axis, left.getTile(), right.getTile(),
                    Math.min(start, end), centerY - neckHeight * 0.5F,
                    Math.abs(end - start), neckHeight, progress, link.isDetaching());
        }

        NightBloomWatermarkLayout.TileView top = child.getY() <= parent.getY() ? child : parent;
        NightBloomWatermarkLayout.TileView bottom = top == child ? parent : child;
        float sharedLeft = Math.max(top.getX(), bottom.getX());
        float sharedRight = Math.min(top.getRight(), bottom.getRight());
        float sharedWidth = Math.max(0.0F, sharedRight - sharedLeft);
        float neckWidth = neckSize(sharedWidth, progress);
        float centerX = (sharedLeft + sharedRight) * 0.5F;
        float start = top.getBottom();
        float end = bottom.getY();
        return new Bridge(axis, top.getTile(), bottom.getTile(),
                centerX - neckWidth * 0.5F, Math.min(start, end),
                neckWidth, Math.abs(end - start), progress, link.isDetaching());
    }

    static Surface surfaceFor(NightBloomWatermarkLayout.TileView tile, List<Bridge> bridges, float radius) {
        Surface surface = Surface.plain(radius, tile == null ? 0.0F : tile.getWidth(),
                tile == null ? 0.0F : tile.getHeight());
        if (tile == null || bridges == null) {
            return surface;
        }
        for (Bridge bridge : bridges) {
            if (bridge == null || !bridge.connects(tile.getTile()) || bridge.getEdgeProgress() <= EPSILON) {
                continue;
            }
            float edgeProgress = bridge.getEdgeProgress();
            if (bridge.getAxis() == Axis.HORIZONTAL) {
                float start = clampRange(bridge.getY() - tile.getY(), tile.getHeight());
                float end = clampRange(bridge.getBottom() - tile.getY(), tile.getHeight());
                surface = bridge.getLeadingTile() == tile.getTile()
                        ? surface.withRightJoin(start, end, edgeProgress)
                        : surface.withLeftJoin(start, end, edgeProgress);
            } else {
                float start = clampRange(bridge.getX() - tile.getX(), tile.getWidth());
                float end = clampRange(bridge.getRight() - tile.getX(), tile.getWidth());
                surface = bridge.getLeadingTile() == tile.getTile()
                        ? surface.withBottomJoin(start, end, edgeProgress)
                        : surface.withTopJoin(start, end, edgeProgress);
            }
        }
        return surface;
    }

    /**
     * Keeps the independently draggable watermark tiles while flattening only the outer tile edge
     * touched by a global HUD bridge. This avoids painting a single rectangular watermark backing.
     */
    static Surface mergeDockingSurface(Surface surface, NightBloomWatermarkLayout.TileView tile,
                                       HudDockingCoordinator.Snapshot docking, String nodeId) {
        if (surface == null || tile == null || docking == null || nodeId == null) {
            return surface;
        }
        HudDockingCoordinator.NodeView node = docking.getNode(nodeId);
        if (node == null || !node.isVisible()) {
            return surface;
        }
        Surface merged = surface;
        for (HudDockingCoordinator.Bridge bridge : docking.getBridges()) {
            boolean leading = nodeId.equals(bridge.getLeadingId());
            boolean trailing = nodeId.equals(bridge.getTrailingId());
            if ((!leading && !trailing) || bridge.getEdgeProgress() <= EPSILON) {
                continue;
            }
            float progress = bridge.getEdgeProgress();
            if (bridge.isHorizontal()) {
                float start = clampRange(bridge.getY() - tile.getY(), tile.getHeight());
                float end = clampRange(bridge.getBottom() - tile.getY(), tile.getHeight());
                if (leading && Math.abs(tile.getRight() - node.getRight()) <= 0.75F) {
                    merged = merged.withRightJoin(start, end, progress);
                } else if (trailing && Math.abs(tile.getX() - node.getX()) <= 0.75F) {
                    merged = merged.withLeftJoin(start, end, progress);
                }
            } else {
                float start = clampRange(bridge.getX() - tile.getX(), tile.getWidth());
                float end = clampRange(bridge.getRight() - tile.getX(), tile.getWidth());
                if (leading && Math.abs(tile.getBottom() - node.getBottom()) <= 0.75F) {
                    merged = merged.withBottomJoin(start, end, progress);
                } else if (trailing && Math.abs(tile.getY() - node.getY()) <= 0.75F) {
                    merged = merged.withTopJoin(start, end, progress);
                }
            }
        }
        return merged;
    }

    static List<Composite> composites(List<NightBloomWatermarkLayout.TileView> tileViews,
                                      List<Bridge> bridges) {
        Map<NightBloomWatermarkLayout.Tile, NightBloomWatermarkLayout.TileView> views =
                new EnumMap<NightBloomWatermarkLayout.Tile, NightBloomWatermarkLayout.TileView>(
                        NightBloomWatermarkLayout.Tile.class);
        if (tileViews != null) {
            for (NightBloomWatermarkLayout.TileView view : tileViews) {
                if (view != null) {
                    views.put(view.getTile(), view);
                }
            }
        }
        List<Bridge> activeBridges = new ArrayList<Bridge>();
        if (bridges != null) {
            for (Bridge bridge : bridges) {
                if (bridge != null && bridge.getProgress() > EPSILON
                        && views.containsKey(bridge.getLeadingTile())
                        && views.containsKey(bridge.getTrailingTile())) {
                    activeBridges.add(bridge);
                }
            }
        }

        List<Composite> composites = new ArrayList<Composite>();
        Set<NightBloomWatermarkLayout.Tile> compositeTiles =
                EnumSet.noneOf(NightBloomWatermarkLayout.Tile.class);
        for (Bridge bridge : activeBridges) {
            if (bridge.getAxis() != Axis.VERTICAL || bridge.getCompositeProgress() <= EPSILON) {
                continue;
            }
            Set<NightBloomWatermarkLayout.Tile> activeComponent = connectedComponent(bridge.getLeadingTile(), views,
                    activeBridges, false, 0.0F);
            float leadingVerticalProgress = verticalCompositeProgress(activeComponent, activeBridges);
            Set<NightBloomWatermarkLayout.Tile> component = connectedComponent(bridge.getLeadingTile(), views,
                    activeBridges, true, leadingVerticalProgress);
            if (component.isEmpty() || compositeTiles.containsAll(component)) {
                continue;
            }
            float progress = verticalCompositeProgress(component, activeBridges);
            if (progress > EPSILON) {
                composites.add(composite(component, views, progress));
                compositeTiles.addAll(component);
            }
        }

        Set<NightBloomWatermarkLayout.Tile> inspected = EnumSet.noneOf(NightBloomWatermarkLayout.Tile.class);
        for (NightBloomWatermarkLayout.Tile start : views.keySet()) {
            if (!inspected.add(start)) {
                continue;
            }
            Set<NightBloomWatermarkLayout.Tile> component = connectedComponent(start, views,
                    activeBridges, false, 0.0F);
            inspected.addAll(component);
            if (containsVerticalBridge(component, activeBridges)) {
                continue;
            }
            float progress = alignedHorizontalCompositeProgress(component, views, activeBridges);
            if (progress > EPSILON) {
                composites.add(composite(component, views, progress));
            }
        }
        return composites;
    }

    private static Set<NightBloomWatermarkLayout.Tile> connectedComponent(
            NightBloomWatermarkLayout.Tile start,
            Map<NightBloomWatermarkLayout.Tile, NightBloomWatermarkLayout.TileView> views,
            List<Bridge> bridges, boolean islandEdgesOnly, float leadingVerticalProgress) {
        Set<NightBloomWatermarkLayout.Tile> component = EnumSet.noneOf(NightBloomWatermarkLayout.Tile.class);
        if (start == null || !views.containsKey(start)) {
            return component;
        }
        ArrayDeque<NightBloomWatermarkLayout.Tile> queue = new ArrayDeque<NightBloomWatermarkLayout.Tile>();
        component.add(start);
        queue.add(start);
        while (!queue.isEmpty()) {
            NightBloomWatermarkLayout.Tile current = queue.removeFirst();
            for (Bridge bridge : bridges) {
                if (islandEdgesOnly && !contributesToIsland(bridge, leadingVerticalProgress)) {
                    continue;
                }
                NightBloomWatermarkLayout.Tile next = bridge.other(current);
                if (next != null && views.containsKey(next) && component.add(next)) {
                    queue.addLast(next);
                }
            }
        }
        return component;
    }

    private static boolean contributesToIsland(Bridge bridge, float leadingVerticalProgress) {
        if (bridge.getAxis() == Axis.HORIZONTAL) {
            return bridge.getCompositeProgress() >= 1.0F - EPSILON;
        }
        return bridge.getCompositeProgress() > EPSILON
                && bridge.getCompositeProgress() >= leadingVerticalProgress - EPSILON;
    }

    private static boolean containsVerticalBridge(Set<NightBloomWatermarkLayout.Tile> component,
                                                  List<Bridge> bridges) {
        for (Bridge bridge : bridges) {
            if (bridge.getAxis() == Axis.VERTICAL && component.contains(bridge.getLeadingTile())
                    && component.contains(bridge.getTrailingTile())) {
                return true;
            }
        }
        return false;
    }

    private static float verticalCompositeProgress(Set<NightBloomWatermarkLayout.Tile> component,
                                                   List<Bridge> bridges) {
        float progress = 0.0F;
        for (Bridge bridge : bridges) {
            if (bridge.getAxis() == Axis.VERTICAL && component.contains(bridge.getLeadingTile())
                    && component.contains(bridge.getTrailingTile())) {
                progress = Math.max(progress, bridge.getCompositeProgress());
            }
        }
        return progress;
    }

    private static Composite composite(Set<NightBloomWatermarkLayout.Tile> component,
                                       Map<NightBloomWatermarkLayout.Tile,
                                               NightBloomWatermarkLayout.TileView> views, float progress) {
        float left = Float.MAX_VALUE;
        float top = Float.MAX_VALUE;
        float right = -Float.MAX_VALUE;
        float bottom = -Float.MAX_VALUE;
        for (NightBloomWatermarkLayout.Tile tile : component) {
            NightBloomWatermarkLayout.TileView view = views.get(tile);
            left = Math.min(left, view.getX());
            top = Math.min(top, view.getY());
            right = Math.max(right, view.getRight());
            bottom = Math.max(bottom, view.getBottom());
        }
        return new Composite(component, left, top, right, bottom, progress);
    }

    /**
     * A row of fully aligned horizontal tiles should finish as one surface instead of leaving
     * several independently antialiased edges at every hand-off.  The minimum link progress
     * deliberately keeps the individual liquid neck visible until every join in the row is ready.
     */
    private static float alignedHorizontalCompositeProgress(Set<NightBloomWatermarkLayout.Tile> component,
                                                            Map<NightBloomWatermarkLayout.Tile,
                                                                    NightBloomWatermarkLayout.TileView> views,
                                                            List<Bridge> bridges) {
        if (component.size() < 2) {
            return 0.0F;
        }
        float top = Float.NaN;
        float bottom = Float.NaN;
        for (NightBloomWatermarkLayout.Tile tile : component) {
            NightBloomWatermarkLayout.TileView view = views.get(tile);
            if (view == null) {
                return 0.0F;
            }
            if (Float.isNaN(top)) {
                top = view.getY();
                bottom = view.getBottom();
            } else if (Math.abs(top - view.getY()) > EPSILON
                    || Math.abs(bottom - view.getBottom()) > EPSILON) {
                return 0.0F;
            }
        }

        float progress = 1.0F;
        boolean foundBridge = false;
        for (Bridge bridge : bridges) {
            if (!component.contains(bridge.getLeadingTile()) || !component.contains(bridge.getTrailingTile())) {
                continue;
            }
            if (bridge.getAxis() != Axis.HORIZONTAL) {
                return 0.0F;
            }
            foundBridge = true;
            progress = Math.min(progress, bridge.getCompositeProgress());
        }
        return foundBridge ? progress : 0.0F;
    }

    static Corners cornersFor(NightBloomWatermarkLayout.TileView tile, List<Bridge> bridges, float radius) {
        Surface surface = surfaceFor(tile, bridges, radius);
        return new Corners(surface.getTopLeft(), surface.getTopRight(),
                surface.getBottomRight(), surface.getBottomLeft());
    }

    private static float neckSize(float sharedSize, float progress) {
        if (sharedSize <= 0.0F || progress <= EPSILON) {
            return 0.0F;
        }
        float expansion = edgeProgress(progress);
        float presence = ease(progress / NECK_FADE_END);
        return sharedSize * presence * (MIN_NECK_RATIO + (1.0F - MIN_NECK_RATIO) * expansion);
    }

    private static float clampRange(float value, float size) {
        return Math.max(0.0F, Math.min(Math.max(0.0F, size), value));
    }

    private static float edgeProgress(float progress) {
        return ease((clamp(progress) - EDGE_EXPANSION_START) / (1.0F - EDGE_EXPANSION_START));
    }

    private static float compositeProgress(float progress) {
        return ease((clamp(progress) - ISLAND_EXPANSION_START) / (1.0F - ISLAND_EXPANSION_START));
    }

    private static float ease(float value) {
        float clamped = clamp(value);
        return clamped * clamped * (3.0F - 2.0F * clamped);
    }

    private static float clamp(float value) {
        return Math.max(0.0F, Math.min(1.0F, value));
    }

    static final class Bridge {
        private static final Bridge EMPTY = new Bridge(Axis.HORIZONTAL, null, null,
                0.0F, 0.0F, 0.0F, 0.0F, 0.0F, false);

        private final Axis axis;
        private final NightBloomWatermarkLayout.Tile leadingTile;
        private final NightBloomWatermarkLayout.Tile trailingTile;
        private final float x;
        private final float y;
        private final float width;
        private final float height;
        private final float progress;
        private final boolean detaching;

        private Bridge(Axis axis, NightBloomWatermarkLayout.Tile leadingTile,
                       NightBloomWatermarkLayout.Tile trailingTile, float x, float y,
                       float width, float height, float progress, boolean detaching) {
            this.axis = axis;
            this.leadingTile = leadingTile;
            this.trailingTile = trailingTile;
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
            this.progress = progress;
            this.detaching = detaching;
        }

        static Bridge empty() {
            return EMPTY;
        }

        Axis getAxis() {
            return axis;
        }

        NightBloomWatermarkLayout.Tile getLeadingTile() {
            return leadingTile;
        }

        NightBloomWatermarkLayout.Tile getTrailingTile() {
            return trailingTile;
        }

        float getX() {
            return x;
        }

        float getY() {
            return y;
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

        float getProgress() {
            return progress;
        }

        float getOpacity() {
            return ease(progress / NECK_FADE_END);
        }

        float getEdgeProgress() {
            return edgeProgress(progress);
        }

        float getCompositeProgress() {
            return compositeProgress(progress);
        }

        boolean isDetaching() {
            return detaching;
        }

        float getRadius() {
            return Math.min(2.0F, Math.min(width, height) * 0.5F) * (1.0F - getEdgeProgress());
        }

        boolean isVisible() {
            return getOpacity() > EPSILON && width > EPSILON && height > EPSILON;
        }

        boolean connects(NightBloomWatermarkLayout.Tile tile) {
            return tile != null && (tile == leadingTile || tile == trailingTile);
        }

        NightBloomWatermarkLayout.Tile other(NightBloomWatermarkLayout.Tile tile) {
            if (tile == leadingTile) {
                return trailingTile;
            }
            return tile == trailingTile ? leadingTile : null;
        }
    }

    static final class Surface {
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
        private final float width;
        private final float height;

        private Surface(float topLeft, float topRight, float bottomRight, float bottomLeft,
                        float topJoinStart, float topJoinEnd, float bottomJoinStart, float bottomJoinEnd,
                        float leftJoinStart, float leftJoinEnd, float rightJoinStart, float rightJoinEnd,
                        float width, float height) {
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
            this.width = width;
            this.height = height;
        }

        private static Surface plain(float radius, float width, float height) {
            float resolvedRadius = Math.max(0.0F, radius);
            return new Surface(resolvedRadius, resolvedRadius, resolvedRadius, resolvedRadius,
                    1.0F, 0.0F, 1.0F, 0.0F, 1.0F, 0.0F, 1.0F, 0.0F, width, height);
        }

        float getTopLeft() {
            return topLeft;
        }

        float getTopRight() {
            return topRight;
        }

        float getBottomRight() {
            return bottomRight;
        }

        float getBottomLeft() {
            return bottomLeft;
        }

        float getTopJoinStart() {
            return topJoinStart;
        }

        float getTopJoinEnd() {
            return topJoinEnd;
        }

        float getBottomJoinStart() {
            return bottomJoinStart;
        }

        float getBottomJoinEnd() {
            return bottomJoinEnd;
        }

        float getLeftJoinStart() {
            return leftJoinStart;
        }

        float getLeftJoinEnd() {
            return leftJoinEnd;
        }

        float getRightJoinStart() {
            return rightJoinStart;
        }

        float getRightJoinEnd() {
            return rightJoinEnd;
        }

        private Surface withTopJoin(float start, float end, float progress) {
            if (end <= start) {
                return this;
            }
            float resolvedStart = joinedStart(topJoinStart, topJoinEnd, start, end);
            float resolvedEnd = joinedEnd(topJoinStart, topJoinEnd, start, end);
            return new Surface(start <= EPSILON ? topLeft * (1.0F - progress) : topLeft,
                    end >= width - EPSILON ? topRight * (1.0F - progress) : topRight,
                    bottomRight, bottomLeft,
                    resolvedStart, resolvedEnd, bottomJoinStart, bottomJoinEnd,
                    leftJoinStart, leftJoinEnd, rightJoinStart, rightJoinEnd, width, height);
        }

        private Surface withBottomJoin(float start, float end, float progress) {
            if (end <= start) {
                return this;
            }
            float resolvedStart = joinedStart(bottomJoinStart, bottomJoinEnd, start, end);
            float resolvedEnd = joinedEnd(bottomJoinStart, bottomJoinEnd, start, end);
            return new Surface(topLeft, topRight,
                    end >= width - EPSILON ? bottomRight * (1.0F - progress) : bottomRight,
                    start <= EPSILON ? bottomLeft * (1.0F - progress) : bottomLeft,
                    topJoinStart, topJoinEnd, resolvedStart, resolvedEnd,
                    leftJoinStart, leftJoinEnd, rightJoinStart, rightJoinEnd, width, height);
        }

        private Surface withLeftJoin(float start, float end, float progress) {
            if (end <= start) {
                return this;
            }
            float resolvedStart = joinedStart(leftJoinStart, leftJoinEnd, start, end);
            float resolvedEnd = joinedEnd(leftJoinStart, leftJoinEnd, start, end);
            return new Surface(start <= EPSILON ? topLeft * (1.0F - progress) : topLeft,
                    topRight, bottomRight,
                    end >= height - EPSILON ? bottomLeft * (1.0F - progress) : bottomLeft,
                    topJoinStart, topJoinEnd, bottomJoinStart, bottomJoinEnd,
                    resolvedStart, resolvedEnd, rightJoinStart, rightJoinEnd, width, height);
        }

        private Surface withRightJoin(float start, float end, float progress) {
            if (end <= start) {
                return this;
            }
            float resolvedStart = joinedStart(rightJoinStart, rightJoinEnd, start, end);
            float resolvedEnd = joinedEnd(rightJoinStart, rightJoinEnd, start, end);
            return new Surface(topLeft,
                    start <= EPSILON ? topRight * (1.0F - progress) : topRight,
                    end >= height - EPSILON ? bottomRight * (1.0F - progress) : bottomRight,
                    bottomLeft,
                    topJoinStart, topJoinEnd, bottomJoinStart, bottomJoinEnd,
                    leftJoinStart, leftJoinEnd, resolvedStart, resolvedEnd, width, height);
        }

        private static float joinedStart(float currentStart, float currentEnd, float start, float end) {
            return currentEnd <= currentStart ? start : Math.min(currentStart, start);
        }

        private static float joinedEnd(float currentStart, float currentEnd, float start, float end) {
            return currentEnd <= currentStart ? end : Math.max(currentEnd, end);
        }
    }

    static final class Composite {
        private final Set<NightBloomWatermarkLayout.Tile> tiles;
        private final float x;
        private final float y;
        private final float right;
        private final float bottom;
        private final float progress;

        private Composite(Set<NightBloomWatermarkLayout.Tile> tiles, float x, float y,
                          float right, float bottom, float progress) {
            this.tiles = EnumSet.copyOf(tiles);
            this.x = x;
            this.y = y;
            this.right = right;
            this.bottom = bottom;
            this.progress = progress;
        }

        boolean contains(NightBloomWatermarkLayout.Tile tile) {
            return tile != null && tiles.contains(tile);
        }

        float getX() {
            return x;
        }

        float getY() {
            return y;
        }

        float getRight() {
            return right;
        }

        float getBottom() {
            return bottom;
        }

        float getProgress() {
            return progress;
        }
    }

    static final class Corners {
        private final float topLeft;
        private final float topRight;
        private final float bottomRight;
        private final float bottomLeft;

        private Corners(float topLeft, float topRight, float bottomRight, float bottomLeft) {
            this.topLeft = topLeft;
            this.topRight = topRight;
            this.bottomRight = bottomRight;
            this.bottomLeft = bottomLeft;
        }

        float getTopLeft() {
            return topLeft;
        }

        float getTopRight() {
            return topRight;
        }

        float getBottomRight() {
            return bottomRight;
        }

        float getBottomLeft() {
            return bottomLeft;
        }

        private Corners withTopLeft(float value) {
            return new Corners(Math.min(topLeft, value), topRight, bottomRight, bottomLeft);
        }

        private Corners withTopRight(float value) {
            return new Corners(topLeft, Math.min(topRight, value), bottomRight, bottomLeft);
        }

        private Corners withBottomRight(float value) {
            return new Corners(topLeft, topRight, Math.min(bottomRight, value), bottomLeft);
        }

        private Corners withBottomLeft(float value) {
            return new Corners(topLeft, topRight, bottomRight, Math.min(bottomLeft, value));
        }
    }
}
