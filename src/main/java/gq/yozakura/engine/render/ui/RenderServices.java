package gq.yozakura.engine.render.ui;

import gq.yozakura.engine.render.glow.GlowRenderer;

public final class RenderServices {
    private static final RenderContext CONTEXT = new RenderContext();
    private static final ShapeRenderer SHAPES = new ShapeRenderer(CONTEXT);
    private static final RoundedRenderer ROUNDED = new RoundedRenderer(SHAPES);
    private static final BlurRenderer BLUR = new BlurRenderer(SHAPES);
    private static final LiquidGlassRenderer LIQUID_GLASS = new LiquidGlassRenderer();
    private static final PanelPainter PANELS = new PanelPainter(SHAPES, BLUR);
    private static final StencilRenderer STENCIL = new StencilRenderer();
    private static final GlowRenderer GLOW = new GlowRenderer();
    private static final GlowRenderer SHADOWS = new GlowRenderer();

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
}
