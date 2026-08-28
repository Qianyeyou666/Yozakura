package gq.yozakura.module.render;

import gq.yozakura.engine.render.glow.GlowProfile;
import gq.yozakura.engine.render.glow.GlowRenderer;
import gq.yozakura.engine.render.ui.RenderServices;
import gq.yozakura.util.color.ColorUtils;
import net.minecraft.client.renderer.GlStateManager;
import org.lwjgl.opengl.GL11;

import java.util.List;

/** Renders every Nymph row background as one connected stencil island. */
final class NymphArrayListRenderer {
    private static final float RADIUS = 4.0F;
    private static final int KAWASE_ITERATIONS = 4;
    private static final float KAWASE_OFFSET = 2.0F;

    private final NymphArrayListEffectsRenderer effects = new NymphArrayListEffectsRenderer();
    private final Bounds union = new Bounds(0.0F, 0.0F, 0.0F, 0.0F);
    private boolean backgroundFailureLogged;

    void drawBackgrounds(HUD.NymphBackground mode, List<NymphArrayListRenderRow> rows,
                         float backgroundAlpha) {
        NymphArrayListBackgroundPlan plan = NymphArrayListBackgroundPlan.forMode(mode);
        if (!plan.hasSurface() || rows.isEmpty()) {
            return;
        }
        try {
            updateUnionBounds(rows);
            float maximumProgress = maximumProgress(rows);
            int alpha = Math.round(ColorUtils.clamp(backgroundAlpha, 0.0F, 255.0F) * maximumProgress);
            int fill = withAlpha(0xFF141414, alpha);
            if (plan.hasBlur()) {
                queueBloomMask(rows);
                boolean blurReady = effects.prepareBlur(KAWASE_ITERATIONS, KAWASE_OFFSET);
                beginUnionMask(rows, 0.0F);
                try {
                    int tint = withAlpha(0xFF101014, Math.min(150, alpha));
                    if (!blurReady || !effects.drawBlurredSurface(
                            union.left, union.top, union.right, union.bottom, 0.0F, tint)) {
                        RenderServices.shapes().rect(union.left, union.top, union.right, union.bottom, fill);
                    }
                } finally {
                    RenderServices.stencil().end();
                }
                return;
            }

            if (plan.hasOutline()) {
                int accent = withAlpha(rows.get(0).color, Math.min(225, alpha + 58));
                drawMaskedFill(rows, union, 0.0F, accent);
                drawMaskedFill(rows, union, 0.7F, fill);
                return;
            }

            drawMaskedFill(rows, union, 0.0F, fill);
            drawBars(plan, rows, maximumProgress);
        } catch (Throwable throwable) {
            logBackgroundFailure(throwable);
        } finally {
            restoreBackgroundState();
        }
    }

    void dispose() {
        effects.dispose();
        backgroundFailureLogged = false;
    }

    private void restoreBackgroundState() {
        GL11.glColorMask(true, true, true, true);
        GL11.glDisable(GL11.GL_STENCIL_TEST);
        RenderServices.stencil().end();
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
    }

    private void logBackgroundFailure(Throwable throwable) {
        if (!backgroundFailureLogged) {
            backgroundFailureLogged = true;
            String message = throwable.getMessage();
            System.err.println("[Yozakura] Nymph ArrayList rendering background failed: "
                    + (message == null ? throwable.toString() : message));
        }
    }

    private void drawMaskedFill(List<NymphArrayListRenderRow> rows, Bounds union,
                                float inset, int color) {
        beginUnionMask(rows, inset);
        try {
            RenderServices.shapes().rect(union.left, union.top, union.right, union.bottom, color);
        } finally {
            RenderServices.stencil().end();
        }
    }

    /** Replays the source DoAFuckingBloomEvent mask pass through the HUD shadow pipeline. */
    private void queueBloomMask(List<NymphArrayListRenderRow> rows) {
        GlowRenderer shadows = RenderServices.shadows();
        boolean isolatedFrame = !shadows.isFrameOpen();
        if (isolatedFrame) {
            shadows.beginFrame();
            shadows.beginCommandSnapshotCache();
        }
        try {
            for (int index = 0; index < rows.size(); index++) {
                NymphArrayListBackgroundPlan.Bounds bounds = rows.get(index).surface;
                if (bounds.right > bounds.left && bounds.bottom > bounds.top) {
                    shadows.queueRoundedRect(bounds.left, bounds.top, bounds.right, bounds.bottom,
                            RADIUS, 0xFF000000, 1.0F, GlowProfile.SHADOW);
                }
                if (index > 0) {
                    NymphArrayListBackgroundPlan.Bounds previous = rows.get(index - 1).surface;
                    NymphArrayListBackgroundPlan.Bounds connector =
                            NymphArrayListBackgroundPlan.connector(previous, bounds, RADIUS);
                    if (connector != null) {
                        shadows.queueRoundedRect(connector.left, connector.top,
                                connector.right, connector.bottom, 0.0F,
                                0xFF000000, 1.0F, GlowProfile.SHADOW);
                    }
                }
            }
        } finally {
            if (isolatedFrame) {
                shadows.flush();
            }
        }
    }

    private void beginUnionMask(List<NymphArrayListRenderRow> rows, float inset) {
        RenderServices.stencil().initWrite();
        drawUnionMask(rows, inset);
        RenderServices.stencil().read(1);
    }

    private void drawUnionMask(List<NymphArrayListRenderRow> rows, float inset) {
        float radius = Math.max(0.0F, RADIUS - inset);
        for (int index = 0; index < rows.size(); index++) {
            NymphArrayListBackgroundPlan.Bounds bounds = rows.get(index).surface;
            float left = bounds.left + inset;
            float top = bounds.top + inset;
            float right = bounds.right - inset;
            float bottom = bounds.bottom - inset;
            if (right > left && bottom > top) {
                RenderServices.shapes().rounded(left, top, right, bottom, radius, 0xFFFFFFFF);
            }
            if (index > 0) {
                NymphArrayListBackgroundPlan.Bounds previous = rows.get(index - 1).surface;
                float connectorLeft = Math.max(previous.left, bounds.left) + inset;
                float connectorRight = Math.min(previous.right, bounds.right) - inset;
                if (connectorRight > connectorLeft && bounds.top - previous.bottom <= 1.0F) {
                    float seam = (previous.bottom + bounds.top) * 0.5F;
                    RenderServices.shapes().rect(connectorLeft, seam - Math.max(0.5F, radius),
                            connectorRight, seam + Math.max(0.5F, radius), 0xFFFFFFFF);
                }
            }
        }
    }

    private void drawBars(NymphArrayListBackgroundPlan plan,
                          List<NymphArrayListRenderRow> rows, float maximumProgress) {
        for (int index = 0; index < rows.size(); index++) {
            NymphArrayListRenderRow row = rows.get(index);
            NymphArrayListBackgroundPlan.Bounds bounds = row.surface;
            float barX = plan.isLeftBar() ? bounds.left : bounds.right - 1.5F;
            float top = index == 0 ? bounds.top + 1.0F : bounds.top;
            float bottom = index == rows.size() - 1 ? bounds.bottom - 1.0F : bounds.bottom;
            RenderServices.shapes().rect(barX, top, barX + 1.5F, bottom,
                    withAlpha(row.color, Math.round(230.0F * maximumProgress)));
        }
    }

    private void updateUnionBounds(List<NymphArrayListRenderRow> rows) {
        NymphArrayListBackgroundPlan.Bounds first = rows.get(0).surface;
        union.left = first.left;
        union.top = first.top;
        union.right = first.right;
        union.bottom = first.bottom;
        for (int index = 1; index < rows.size(); index++) {
            NymphArrayListBackgroundPlan.Bounds bounds = rows.get(index).surface;
            union.left = Math.min(union.left, bounds.left);
            union.top = Math.min(union.top, bounds.top);
            union.right = Math.max(union.right, bounds.right);
            union.bottom = Math.max(union.bottom, bounds.bottom);
        }
    }

    private static float maximumProgress(List<NymphArrayListRenderRow> rows) {
        float progress = 0.0F;
        for (NymphArrayListRenderRow row : rows) {
            progress = Math.max(progress, row.scaleIn ? row.progress : 1.0F);
        }
        return ColorUtils.clamp(progress, 0.0F, 1.0F);
    }

    private static int withAlpha(int color, int alpha) {
        return ColorUtils.clamp(alpha, 0, 255) << 24 | color & 0x00FFFFFF;
    }

    private static final class Bounds {
        private float left;
        private float top;
        private float right;
        private float bottom;

        private Bounds(float left, float top, float right, float bottom) {
            this.left = left;
            this.top = top;
            this.right = right;
            this.bottom = bottom;
        }
    }
}
