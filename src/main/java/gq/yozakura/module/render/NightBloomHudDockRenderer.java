package gq.yozakura.module.render;

import gq.yozakura.engine.render.ui.RenderServices;
import gq.yozakura.util.render.HudDockingCoordinator;
import gq.yozakura.util.render.HudDrag;

/**
 * Shared Night Bloom background pass for docked HUD widgets.
 *
 * <p>Composites and liquid necks are drawn at the start of the HUD effects frame.  Each widget
 * then renders only its remaining individual surface before its content, preventing a late
 * component from painting over another component's text.</p>
 */
public final class NightBloomHudDockRenderer {
    private static final int SHARED_SURFACE = 0xDC16161A;

    private NightBloomHudDockRenderer() {
    }

    public static void drawSharedSurfaces() {
        if (HUD.getActiveStyle() != HUD.HudStyle.NIGHT_BLOOM) {
            return;
        }
        HudDockingCoordinator.Snapshot snapshot = HudDrag.getDockingSnapshot();
        if (snapshot == null) {
            return;
        }
        for (HudDockingCoordinator.Bridge bridge : snapshot.getBridges()) {
            float individualOpacity = 1.0F - bridge.getCompositeProgress();
            float opacity = bridge.getOpacity() * individualOpacity;
            if (!bridge.isVisible() || opacity <= 0.01F) {
                continue;
            }
            HUD.drawNightBloomShadow(bridge.getX(), bridge.getY(), bridge.getRight(), bridge.getBottom(),
                    bridge.getRadius(), opacity);
            RenderServices.shapes().joinedRounded(bridge.getX(), bridge.getY(), bridge.getRight(), bridge.getBottom(),
                    bridge.getRadius(), bridge.getRadius(), bridge.getRadius(), bridge.getRadius(),
                    multiplyAlpha(SHARED_SURFACE, opacity));
        }
        for (HudDockingCoordinator.Composite composite : snapshot.getComposites()) {
            if (composite.getProgress() <= 0.01F) {
                continue;
            }
            HUD.drawNightBloomShadow(composite.getX(), composite.getY(), composite.getRight(), composite.getBottom(),
                    composite.getRadius(), composite.getProgress());
            RenderServices.shapes().joinedRounded(composite.getX(), composite.getY(),
                    composite.getRight(), composite.getBottom(), composite.getRadius(), composite.getRadius(),
                    composite.getRadius(), composite.getRadius(),
                    multiplyAlpha(SHARED_SURFACE, fusedCompositeSurfaceOpacity(
                            sourceAlpha(SHARED_SURFACE), composite.getProgress())));
        }
    }

    public static void drawPanel(String id, float x, float y, float width, float height, float radius,
                                 float alpha, int fill, int raisedFill) {
        if (width <= 0.0F || height <= 0.0F || alpha <= 0.0F) {
            return;
        }
        HudDockingCoordinator.Surface surface = surface(id, x, y, width, height);
        if (surface == null) {
            HUD.drawNightBloomShadow(x, y, x + width, y + height, radius, alpha);
            RenderServices.shapes().rounded(x, y, x + width, y + height, radius, multiplyAlpha(fill, alpha));
            drawRaisedBand(x, y, width, height, radius, alpha, raisedFill);
            return;
        }
        float individualOpacity = alpha * surface.getIndividualOpacity();
        if (individualOpacity <= 0.01F) {
            return;
        }
        HUD.drawNightBloomShadow(x, y, x + width, y + height, radius, individualOpacity);
        RenderServices.shapes().joinedRounded(x, y, x + width, y + height,
                surface.getTopLeft(), surface.getTopRight(), surface.getBottomRight(), surface.getBottomLeft(),
                surface.getTopJoinStart(), surface.getTopJoinEnd(),
                surface.getBottomJoinStart(), surface.getBottomJoinEnd(),
                surface.getLeftJoinStart(), surface.getLeftJoinEnd(),
                surface.getRightJoinStart(), surface.getRightJoinEnd(),
                multiplyAlpha(fill, individualOpacity));
        drawRaisedBand(x, y, width, height, radius, individualOpacity, raisedFill);
    }

    public static void drawPanel(String id, float x, float y, float width, float height, float radius,
                                 float alpha, int fill) {
        drawPanel(id, x, y, width, height, radius, alpha, fill, 0x00000000);
    }

    public static boolean isDocked(String id) {
        HudDockingCoordinator.Snapshot snapshot = HudDrag.getDockingSnapshot();
        if (snapshot == null || id == null) {
            return false;
        }
        for (HudDockingCoordinator.LinkView link : snapshot.getLinks()) {
            if (!link.isDetaching() && (id.equals(link.getChildId()) || id.equals(link.getParentId()))) {
                return true;
            }
        }
        return false;
    }

    /** True for both connected and liquid-detaching nodes so their outer panel never pops mid-split. */
    public static boolean hasLink(String id) {
        HudDockingCoordinator.Snapshot snapshot = HudDrag.getDockingSnapshot();
        return snapshot != null && snapshot.hasLink(id);
    }

    private static HudDockingCoordinator.Surface surface(String id, float x, float y, float width, float height) {
        HudDockingCoordinator.Snapshot snapshot = HudDrag.getDockingSnapshot();
        if (snapshot == null || id == null) {
            return null;
        }
        HudDockingCoordinator.Surface surface = snapshot.getSurface(id);
        if (surface == null || surface.getNode() == null) {
            return null;
        }
        HudDockingCoordinator.NodeView node = surface.getNode();
        if (Math.abs(node.getX() - x) > 1.0F || Math.abs(node.getY() - y) > 1.0F
                || Math.abs(node.getWidth() - width) > 1.0F || Math.abs(node.getHeight() - height) > 1.0F) {
            return null;
        }
        return surface;
    }

    private static void drawRaisedBand(float x, float y, float width, float height, float radius,
                                       float alpha, int raisedFill) {
        if ((raisedFill >>> 24) == 0 || width <= radius * 2.0F || height <= 1.0F) {
            return;
        }
        RenderServices.shapes().horizontalGradient(x + radius, y + 1.0F, x + width - radius,
                Math.min(y + height - 1.0F, y + 6.0F), multiplyAlpha(raisedFill, alpha), 0x00000000);
    }

    private static int multiplyAlpha(int color, float alpha) {
        int source = color >>> 24 & 255;
        int resolved = Math.round(source * Math.max(0.0F, Math.min(1.0F, alpha)));
        return color & 0x00FFFFFF | resolved << 24;
    }

    /** Replaces fading individual panel alpha without producing a lighter transition frame. */
    static float fusedCompositeSurfaceOpacity(float baseOpacity, float compositeProgress) {
        float base = Math.max(0.0F, Math.min(1.0F, baseOpacity));
        float progress = Math.max(0.0F, Math.min(1.0F, compositeProgress));
        if (base <= 0.0001F || progress <= 0.0F) {
            return 0.0F;
        }
        float individual = base * (1.0F - progress);
        float composite = (base - individual) / Math.max(0.0001F, 1.0F - individual);
        return Math.max(0.0F, Math.min(1.0F, composite / base));
    }

    private static float sourceAlpha(int color) {
        return (color >>> 24 & 255) / 255.0F;
    }
}
