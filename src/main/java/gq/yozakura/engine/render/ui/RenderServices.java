package gq.yozakura.engine.render.ui;

import gq.yozakura.engine.render.glow.GlowRenderer;
import gq.yozakura.engine.render.glow.GlowProfile;
import gq.yozakura.module.render.NightBloomHudDockRenderer;

public final class RenderServices {
    private static final RenderContext CONTEXT = new RenderContext();
    private static final ShapeRenderer SHAPES = new ShapeRenderer(CONTEXT);
    private static final RoundedRenderer ROUNDED = new RoundedRenderer(SHAPES);
    private static final BlurRenderer BLUR = new BlurRenderer(SHAPES);
    private static final LiquidGlassRenderer LIQUID_GLASS = new LiquidGlassRenderer();
    private static final PanelPainter PANELS = new PanelPainter(SHAPES, BLUR);
    private static final StencilRenderer STENCIL = new StencilRenderer();
    private static final GlowRenderer GLOW = new GlowRenderer();
    private static final GlowRenderer SHADOWS = createShadowRenderer();

    private RenderServices() {
    }

    public static RenderContext context() {
        return CONTEXT;
    }

    public static ShapeRenderer shapes() {
        return SHAPES;
    }

    public static RoundedRenderer rounded() {
        return ROUNDED;
    }

    public static BlurRenderer blur() {
        return BLUR;
    }

    public static LiquidGlassRenderer liquidGlass() {
        return LIQUID_GLASS;
    }

    public static PanelPainter panels() {
        return PANELS;
    }

    public static StencilRenderer stencil() {
        return STENCIL;
    }

    public static GlowRenderer glow() {
        return GLOW;
    }

    public static GlowRenderer shadows() {
        return SHADOWS;
    }

    public static void beginHudEffectsFrame() {
        SHADOWS.beginFrame();
        try {
            GLOW.beginFrame();
            NightBloomHudDockRenderer.drawSharedSurfaces();
        } catch (RuntimeException exception) {
            SHADOWS.flush();
            throw exception;
        }
    }

    public static void flushHudEffectsFrame() {
        try {
            SHADOWS.flush();
        } finally {
            GLOW.flush();
        }
    }

    private static GlowRenderer createShadowRenderer() {
        GlowRenderer renderer = new GlowRenderer();
        renderer.setQuality(GlowProfile.Quality.HIGH);
        renderer.setGlobalStrength(0.55F);
        return renderer;
    }
}
