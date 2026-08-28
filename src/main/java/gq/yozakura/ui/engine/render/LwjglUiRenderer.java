package gq.yozakura.ui.engine.render;

import gq.yozakura.engine.render.ShaderRenderer;
import gq.yozakura.ui.engine.paint.Color;
import gq.yozakura.ui.engine.paint.PaintCommandList;
import gq.yozakura.ui.engine.paint.RectBorderCommand;
import gq.yozakura.ui.engine.paint.RectFillCommand;
import gq.yozakura.ui.engine.paint.TextPaintCommand;
import gq.yozakura.ui.engine.text.AtlasGlyph;
import gq.yozakura.ui.engine.text.FontManager;
import gq.yozakura.ui.engine.text.GlyphAtlas;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL20;

import java.awt.Font;
import java.awt.font.FontRenderContext;
import java.awt.font.GlyphVector;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Retained LWJGL2/OpenGL 2.1 renderer for compiled YozakuraUI paint operations. */
public final class LwjglUiRenderer {
    private static final FontRenderContext TEXT_CONTEXT = new FontRenderContext(null, true, false);
    /** Text layout cache soft cap before LRU eviction kicks in. */
    private static final int TEXT_LAYOUT_SOFT_CAP = 2048;
    /** Fraction of entries evicted when soft cap is exceeded. */
    private static final float TEXT_LAYOUT_EVICTION_FRACTION = 0.25F;
    /**
     * Initial capacity for {@link FloatVertexBuilder}. A rounded rect with 12-segment
     * corners emits ~30 triangles (~180 floats); 128 floats = 64 vertices worth,
     * enough for typical rounded rects without a single grow.
     */
    private static final int FLOAT_VERTEX_INITIAL_CAPACITY = 128;

    private final LwjglGlStateAccess stateAccess;
    private final FontManager fonts;
    private final GlyphAtlas atlas;
    private final RenderStats stats = new RenderStats();
    /** Access-order LRU: get/put moves entry to tail; eviction iterates head first. */
    private final Map<TextLayoutKey, List<PositionedGlyph>> textLayouts =
            new LinkedHashMap<TextLayoutKey, List<PositionedGlyph>>(64, 0.75F, true);
    /** Retained-command fast path: steady frames avoid allocating and hashing TextLayoutKey. */
    private final IdentityHashMap<TextPaintCommand, List<PositionedGlyph>> directTextLayouts =
            new IdentityHashMap<TextPaintCommand, List<PositionedGlyph>>();
    private final Map<RectBorderCommand, BorderGeometry.TriangleList> borderGeometry =
            new IdentityHashMap<RectBorderCommand, BorderGeometry.TriangleList>();
    private final Map<RectFillCommand, float[]> rectangleGeometry =
            new IdentityHashMap<RectFillCommand, float[]>();
    /** compile cache: keyed by (source ref, version) to skip replay on static frames. */
    private PaintCommandList cachedSource;
    private int cachedSourceVersion = -1;
    private CompiledPaint cachedCompiled;
    /** incremental clip tracker: skips redundant glEnable/glScissor on unchanged clip. */
    private final ClipStateTracker clipTracker = new ClipStateTracker();
    private boolean disposed;
    private float textPixelScale = -1.0F;

    public LwjglUiRenderer(FontManager fonts, GlyphAtlas atlas) {
        if (fonts == null || atlas == null) {
            throw new IllegalArgumentException("fonts and atlas must not be null");
        }
        this.fonts = fonts;
        this.atlas = atlas;
        this.stateAccess = new LwjglGlStateAccess();
    }

    /** 单帧渲染性能计数器；调用方可在帧末读取字段值用于日志或 on-screen counter。 */
    public RenderStats stats() {
        return stats;
    }

    public CompiledPaint compile(PaintCommandList commands) {
        ensureOpen();
        if (commands == null) throw new IllegalArgumentException("commands must not be null");
        // compile cache: same ref + unchanged version -> return cached, skip replay + alloc
        if (cachedSource == commands && cachedSourceVersion == commands.version()
                && cachedCompiled != null) {
            stats.compileHits++;
            return cachedCompiled;
        }
        stats.compileMisses++;
        evictTextLayoutsIfOversize();
        directTextLayouts.clear();
        borderGeometry.clear();
        rectangleGeometry.clear();
        final List<RenderOp> operations = new ArrayList<RenderOp>();
        PaintCommandDispatcher dispatcher = new PaintCommandDispatcher(new RenderOpSink() {
            @Override
            public void emit(RenderOp op) {
                operations.add(op);
            }
        });
        commands.replay(dispatcher);
        dispatcher.finish();
        CompiledPaint compiled = new CompiledPaint(operations);
        cachedSource = commands;
        cachedSourceVersion = commands.version();
        cachedCompiled = compiled;
        return compiled;
    }

    public void render(CompiledPaint paint, float logicalWidth, float logicalHeight,
                       float uiScale, float framebufferScale, float originX, float originY,
                       int framebufferHeight) {
        render(paint, logicalWidth, logicalHeight, uiScale, uiScale, framebufferScale,
                originX, originY, framebufferHeight);
    }

    /** Separates visual transform scale from glyph raster scale during window animation. */
    public void render(CompiledPaint paint, float logicalWidth, float logicalHeight,
                       float uiScale, float textUiScale, float framebufferScale,
                       float originX, float originY, int framebufferHeight) {
        ensureOpen();
        if (paint == null || logicalWidth <= 0 || logicalHeight <= 0
                || uiScale <= 0 || textUiScale <= 0 || framebufferScale <= 0) {
            throw new IllegalArgumentException("valid paint and viewport dimensions are required");
        }
        // GlStateGuard.capture/restore already covers every GL state we touch
        // (framebuffer/viewport/matrix/program/texture/blend/alpha/depth/scissor/stencil/color),
        // so the additional glPushAttrib(GL_ALL_ATTRIB_BITS) was a redundant server-side sync
        // point (AGENTS.md performance target: avoid per-frame GL sync).
        clipTracker.reset();
        try (GlStateGuard ignored = new GlStateGuard(stateAccess)) {
            stats.stateSnapshots++;
            float currentTextPixelScale = textUiScale * framebufferScale;
            if (Math.abs(currentTextPixelScale - textPixelScale) > 0.001F) {
                textLayouts.clear();
                directTextLayouts.clear();
                textPixelScale = currentTextPixelScale;
            }
            prepare(logicalWidth, logicalHeight, originX, originY, uiScale);
            for (int i = 0; i < paint.size(); i++) {
                RenderOp operation = paint.operation(i);
                stats.opCount++;
                applyClip(operation.clipRect(), uiScale, framebufferScale,
                        originX, originY, framebufferHeight);
                if (operation.kind() == RenderOp.KIND_RECTANGLE) {
                    stats.rectOps++;
                    drawRectangles(((RectangleRenderOp) operation).batch());
                } else if (operation.kind() == RenderOp.KIND_BORDER) {
                    stats.borderOps++;
                    drawBorders(((BorderRenderOp) operation).batch());
                } else if (operation.kind() == RenderOp.KIND_TEXT) {
                    stats.textOps++;
                    drawText(((TextRenderOp) operation).command(), currentTextPixelScale);
                } else {
                    throw new IllegalStateException("unsupported render op kind: " + operation.kind());
                }
            }
        }
    }

    public void dispose() {
        if (disposed) return;
        disposed = true;
        atlas.dispose();
        textLayouts.clear();
        directTextLayouts.clear();
        borderGeometry.clear();
        rectangleGeometry.clear();
        cachedSource = null;
        cachedCompiled = null;
    }

    private static void prepare(float width, float height, float originX, float originY, float uiScale) {
        GL20.glUseProgram(0);
        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glDisable(GL11.GL_ALPHA_TEST);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glDepthMask(false);
        GL11.glDisable(GL11.GL_STENCIL_TEST);
        GL11.glDisable(GL11.GL_CULL_FACE);
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glDisable(GL11.GL_FOG);
        GL11.glColorMask(true, true, true, true);
        GL11.glPolygonMode(GL11.GL_FRONT_AND_BACK, GL11.GL_FILL);
        GL11.glShadeModel(GL11.GL_SMOOTH);
        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glLoadIdentity();
        GL11.glOrtho(0.0, width, height, 0.0, -1.0, 1.0);
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glLoadIdentity();
        GL11.glTranslatef(originX, originY, 0.0F);
        GL11.glScalef(uiScale, uiScale, 1.0F);
    }

    private void applyClip(ClipRect clip, float uiScale, float framebufferScale, float originX,
                          float originY, int framebufferHeight) {
        if (!clipTracker.update(clip)) {
            // unchanged: skip redundant glEnable/glScissor calls
            return;
        }
        stats.clipChanges++;
        if (clip == null) {
            GL11.glDisable(GL11.GL_SCISSOR_TEST);
            return;
        }
        int x = Math.round((originX + clip.x() * uiScale) * framebufferScale);
        int width = Math.round(clip.width() * uiScale * framebufferScale);
        int height = Math.round(clip.height() * uiScale * framebufferScale);
        int y = framebufferHeight - Math.round(
                (originY + (clip.y() + clip.height()) * uiScale) * framebufferScale);
        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        GL11.glScissor(x, y, width, height);
    }

    private void evictTextLayoutsIfOversize() {
        if (textLayouts.size() <= TEXT_LAYOUT_SOFT_CAP) {
            return;
        }
        int toEvict = (int) (textLayouts.size() * TEXT_LAYOUT_EVICTION_FRACTION);
        if (toEvict <= 0) {
            toEvict = 1;
        }
        Iterator<Map.Entry<TextLayoutKey, List<PositionedGlyph>>> it = textLayouts.entrySet().iterator();
        for (int i = 0; i < toEvict && it.hasNext(); i++) {
            it.next();
            it.remove();
        }
        stats.textLayoutEvictions += toEvict;
    }

    private void drawRectangles(RectangleBatch batch) {
        stats.drawCalls++;
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        for (int i = 0; i < batch.rectCount(); i++) {
            RectFillCommand command = batch.rect(i);
            if (command.effect() == RectFillCommand.EFFECT_HUE) {
                if (!ShaderRenderer.drawRoundedHueRect(command.x(), command.y(),
                        command.x() + command.width(), command.y() + command.height(),
                        command.radius(), command.color().a())) {
                    throw new IllegalStateException("YozakuraUI hue shader is unavailable");
                }
            } else if (command.effect() == RectFillCommand.EFFECT_PALETTE) {
                if (!ShaderRenderer.drawRoundedPaletteRect(command.x(), command.y(),
                        command.x() + command.width(), command.y() + command.height(),
                        command.radius(), command.effectValue(), command.color().a())) {
                    throw new IllegalStateException("YozakuraUI palette shader is unavailable");
                }
            } else if (command.isShadow()) {
                drawShadowShader(command);
            } else if (hasRoundedCorners(command)) {
                drawRoundedShader(command);
            } else {
                drawRectangleGeometry(command);
            }
        }
    }

    private void drawShadowShader(RectFillCommand command) {
        float radius = maximumRadius(command);
        if (!ShaderRenderer.drawRoundedShadow(command.x(), command.y(),
                command.x() + command.width(), command.y() + command.height(),
                radius, command.shadowBlur(), toArgb(command.color()))) {
            throw new IllegalStateException("YozakuraUI rounded shadow shader is unavailable");
        }
    }

    private void drawRoundedShader(RectFillCommand command) {
        boolean rendered;
        if (command.isGradient() && hasUniformRadius(command)) {
            int topLeft = toArgb(gradientColor(command, command.x(), command.y()));
            int bottomLeft = toArgb(gradientColor(command, command.x(), command.y() + command.height()));
            int topRight = toArgb(gradientColor(command, command.x() + command.width(), command.y()));
            int bottomRight = toArgb(gradientColor(command,
                    command.x() + command.width(), command.y() + command.height()));
            rendered = ShaderRenderer.drawRoundedGradientRect(command.x(), command.y(),
                    command.x() + command.width(), command.y() + command.height(),
                    command.topLeftRadius(), topLeft, bottomLeft, topRight, bottomRight);
        } else {
            Color color = command.isGradient()
                    ? gradientColor(command, command.x() + command.width() * 0.5F,
                    command.y() + command.height() * 0.5F)
                    : command.color();
            rendered = ShaderRenderer.drawJoinedRoundedRect(command.x(), command.y(),
                    command.x() + command.width(), command.y() + command.height(),
                    command.topLeftRadius(), command.topRightRadius(),
                    command.bottomRightRadius(), command.bottomLeftRadius(), toArgb(color));
        }
        if (!rendered) {
            throw new IllegalStateException("YozakuraUI rounded rectangle shader is unavailable");
        }
    }

    private void drawRectangleGeometry(RectFillCommand command) {
        if (!command.isGradient()) setColor(command.color());
        float[] vertices = rectangleGeometry.get(command);
        if (vertices == null) {
            vertices = compileRectangle(command);
            rectangleGeometry.put(command, vertices);
        }
        GL11.glBegin(GL11.GL_TRIANGLES);
        try {
            for (int v = 0; v < vertices.length; v += 2) {
                if (command.isGradient()) {
                    setColor(gradientColor(command, vertices[v], vertices[v + 1]));
                }
                GL11.glVertex2f(vertices[v], vertices[v + 1]);
            }
        } finally {
            GL11.glEnd();
        }
    }

    private void drawBorders(BorderBatch batch) {
        stats.drawCalls++;
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        setColor(batch.color());
        for (int i = 0; i < batch.borderCount(); i++) {
            RectBorderCommand command = batch.border(i);
            if (hasUniformRoundedBorder(command)) {
                if (!ShaderRenderer.drawRoundedBorderedRect(command.x(), command.y(),
                        command.x() + command.width(), command.y() + command.height(),
                        command.radius(), command.borderTop(), 0x00000000,
                        toArgb(command.color()))) {
                    throw new IllegalStateException("YozakuraUI rounded border shader is unavailable");
                }
            } else {
                drawBorderGeometry(command);
            }
        }
    }

    private void drawBorderGeometry(RectBorderCommand command) {
        setColor(command.color());
        BorderGeometry.TriangleList triangles = borderGeometry.get(command);
        if (triangles == null) {
            triangles = BorderGeometry.decompose(command);
            borderGeometry.put(command, triangles);
        }
        GL11.glBegin(GL11.GL_TRIANGLES);
        try {
            for (int t = 0; t < triangles.triangleCount(); t++) {
                float[] v = triangles.triangle(t);
                GL11.glVertex2f(v[0], v[1]);
                GL11.glVertex2f(v[2], v[3]);
                GL11.glVertex2f(v[4], v[5]);
            }
        } finally {
            GL11.glEnd();
        }
    }

    private void drawText(TextPaintCommand command, float pixelScale) {
        stats.drawCalls++;
        List<PositionedGlyph> glyphs = directTextLayouts.get(command);
        if (glyphs != null) {
            stats.textLayoutHits++;
        } else {
            TextLayoutKey layoutKey = new TextLayoutKey(command);
            glyphs = textLayouts.get(layoutKey);
            if (glyphs == null) {
                stats.textLayoutMisses++;
                glyphs = layoutText(command, pixelScale);
                textLayouts.put(layoutKey, glyphs);
            } else {
                stats.textLayoutHits++;
            }
            directTextLayouts.put(command, glyphs);
        }

        GL11.glEnable(GL11.GL_TEXTURE_2D);
        setColor(command.color());
        int activeTexture = -1;
        boolean drawing = false;
        try {
            for (int i = 0; i < glyphs.size(); i++) {
                PositionedGlyph positioned = glyphs.get(i);
                AtlasGlyph glyph = positioned.glyph;
                if (glyph.textureId() > 0) {
                    if (glyph.textureId() != activeTexture) {
                        if (drawing) {
                            GL11.glEnd();
                            drawing = false;
                        }
                        GL11.glBindTexture(GL11.GL_TEXTURE_2D, glyph.textureId());
                        GL11.glBegin(GL11.GL_QUADS);
                        drawing = true;
                        activeTexture = glyph.textureId();
                    }
                    float x0 = snapToPixelGrid(command.x() + positioned.penX
                            + glyph.bearingX() / pixelScale, pixelScale);
                    float y0 = snapToPixelGrid(command.y() - glyph.bearingY() / pixelScale,
                            pixelScale);
                    float x1 = x0 + glyph.width() / pixelScale;
                    float y1 = y0 + glyph.height() / pixelScale;
                    GL11.glTexCoord2f(glyph.u0(), glyph.v0()); GL11.glVertex2f(x0, y0);
                    GL11.glTexCoord2f(glyph.u1(), glyph.v0()); GL11.glVertex2f(x1, y0);
                    GL11.glTexCoord2f(glyph.u1(), glyph.v1()); GL11.glVertex2f(x1, y1);
                    GL11.glTexCoord2f(glyph.u0(), glyph.v1()); GL11.glVertex2f(x0, y1);
                }
            }
        } finally {
            if (drawing) GL11.glEnd();
        }
    }

    private static void setColor(Color color) {
        GL11.glColor4f(color.r(), color.g(), color.b(), color.a());
    }

    static boolean hasRoundedCorners(RectFillCommand command) {
        return maximumRadius(command) > 0.0F;
    }

    static boolean hasUniformRoundedBorder(RectBorderCommand command) {
        return command.radius() > 0.0F
                && Float.floatToIntBits(command.borderTop()) == Float.floatToIntBits(command.borderRight())
                && Float.floatToIntBits(command.borderTop()) == Float.floatToIntBits(command.borderBottom())
                && Float.floatToIntBits(command.borderTop()) == Float.floatToIntBits(command.borderLeft());
    }

    private static boolean hasUniformRadius(RectFillCommand command) {
        return Float.floatToIntBits(command.topLeftRadius()) == Float.floatToIntBits(command.topRightRadius())
                && Float.floatToIntBits(command.topLeftRadius()) == Float.floatToIntBits(command.bottomRightRadius())
                && Float.floatToIntBits(command.topLeftRadius()) == Float.floatToIntBits(command.bottomLeftRadius());
    }

    private static float maximumRadius(RectFillCommand command) {
        return Math.max(Math.max(command.topLeftRadius(), command.topRightRadius()),
                Math.max(command.bottomRightRadius(), command.bottomLeftRadius()));
    }

    private static int toArgb(Color color) {
        int alpha = Math.round(color.a() * 255.0F);
        int red = Math.round(color.r() * 255.0F);
        int green = Math.round(color.g() * 255.0F);
        int blue = Math.round(color.b() * 255.0F);
        return alpha << 24 | red << 16 | green << 8 | blue;
    }

    static float snapToPixelGrid(float logicalCoordinate, float pixelScale) {
        if (pixelScale <= 0.0F) return logicalCoordinate;
        return Math.round(logicalCoordinate * pixelScale) / pixelScale;
    }

    static boolean sharesTextLayout(TextPaintCommand first, TextPaintCommand second) {
        return first != null && second != null
                && new TextLayoutKey(first).equals(new TextLayoutKey(second));
    }

    private static Color gradientColor(RectFillCommand command, float x, float y) {
        double radians = Math.toRadians(command.gradientAngleDegrees());
        float dx = (float) Math.sin(radians);
        float dy = (float) -Math.cos(radians);
        float x0 = command.x();
        float y0 = command.y();
        float x1 = x0 + command.width();
        float y1 = y0 + command.height();
        float p00 = x0 * dx + y0 * dy;
        float p10 = x1 * dx + y0 * dy;
        float p01 = x0 * dx + y1 * dy;
        float p11 = x1 * dx + y1 * dy;
        float minimum = Math.min(Math.min(p00, p10), Math.min(p01, p11));
        float maximum = Math.max(Math.max(p00, p10), Math.max(p01, p11));
        float range = maximum - minimum;
        float t = range <= 0.0001F ? 0.0F : (x * dx + y * dy - minimum) / range;
        t = Math.max(0.0F, Math.min(1.0F, t));
        Color start = command.color();
        Color end = command.endColor();
        return Color.fromRgba(
                start.r() + (end.r() - start.r()) * t,
                start.g() + (end.g() - start.g()) * t,
                start.b() + (end.b() - start.b()) * t,
                start.a() + (end.a() - start.a()) * t);
    }

    private List<PositionedGlyph> layoutText(TextPaintCommand command, float pixelScale) {
        List<PositionedGlyph> glyphs = new ArrayList<PositionedGlyph>();
        float penX = 0.0F;
        String text = command.text();
        Font previousFont = null;
        int previousCodePoint = -1;
        AtlasGlyph previousGlyph = null;
        for (int offset = 0; offset < text.length(); ) {
            int codePoint = text.codePointAt(offset);
            Font font = fonts.resolve(command.fontFamily(), command.bold(), codePoint,
                    command.fontSize() * pixelScale);
            AtlasGlyph glyph = atlas.glyph(font, codePoint);
            if (previousFont != null && previousFont.equals(font)) {
                String pairText = new String(Character.toChars(previousCodePoint))
                        + new String(Character.toChars(codePoint));
                char[] pairChars = pairText.toCharArray();
                GlyphVector pair = font.layoutGlyphVector(TEXT_CONTEXT, pairChars,
                        0, pairChars.length, Font.LAYOUT_LEFT_TO_RIGHT);
                if (pair.getNumGlyphs() >= 2) {
                    float shapedSecondX = (float) pair.getGlyphPosition(1).getX();
                    penX += (shapedSecondX - previousGlyph.advance()) / pixelScale;
                }
            }
            glyphs.add(new PositionedGlyph(glyph, penX));
            penX += glyph.advance() / pixelScale;
            previousFont = font;
            previousCodePoint = codePoint;
            previousGlyph = glyph;
            offset += Character.charCount(codePoint);
        }
        float alignedX = command.alignedX(penX);
        float shift = alignedX - command.x();
        if (shift != 0.0F) {
            for (int i = 0; i < glyphs.size(); i++) {
                PositionedGlyph positioned = glyphs.get(i);
                glyphs.set(i, new PositionedGlyph(positioned.glyph, positioned.penX + shift));
            }
        }
        return glyphs;
    }

    private static final class TextLayoutKey {
        private final String text;
        private final float fontSize;
        private final String fontFamily;
        private final boolean bold;
        private final int alignment;
        private final float availableWidth;

        private TextLayoutKey(TextPaintCommand command) {
            this.text = command.text();
            this.fontSize = command.fontSize();
            this.fontFamily = command.fontFamily();
            this.bold = command.bold();
            this.alignment = command.alignment();
            this.availableWidth = command.availableWidth();
        }

        @Override
        public boolean equals(Object value) {
            if (this == value) return true;
            if (!(value instanceof TextLayoutKey)) return false;
            TextLayoutKey other = (TextLayoutKey) value;
            return text.equals(other.text)
                    && Float.floatToIntBits(fontSize) == Float.floatToIntBits(other.fontSize)
                    && fontFamily.equals(other.fontFamily)
                    && bold == other.bold
                    && alignment == other.alignment
                    && Float.floatToIntBits(availableWidth)
                    == Float.floatToIntBits(other.availableWidth);
        }

        @Override
        public int hashCode() {
            int result = text.hashCode();
            result = 31 * result + Float.floatToIntBits(fontSize);
            result = 31 * result + fontFamily.hashCode();
            result = 31 * result + (bold ? 1 : 0);
            result = 31 * result + alignment;
            result = 31 * result + Float.floatToIntBits(availableWidth);
            return result;
        }
    }

    private static float[] compileRectangle(RectFillCommand command) {
        final FloatVertexBuilder builder = new FloatVertexBuilder();
        RoundedRectGeometry.emit(command, new RoundedRectGeometry.TriangleSink() {
            @Override
            public void triangle(float x0, float y0, float x1, float y1, float x2, float y2) {
                builder.add(x0, y0);
                builder.add(x1, y1);
                builder.add(x2, y2);
            }
        });
        return builder.toArray();
    }

    private void ensureOpen() {
        if (disposed) throw new IllegalStateException("renderer has been disposed");
    }

    private static final class PositionedGlyph {
        private final AtlasGlyph glyph;
        private final float penX;

        private PositionedGlyph(AtlasGlyph glyph, float penX) {
            this.glyph = glyph;
            this.penX = penX;
        }
    }

    private static final class FloatVertexBuilder {
        private float[] values = new float[FLOAT_VERTEX_INITIAL_CAPACITY];
        private int size;

        private void add(float x, float y) {
            if (size + 2 > values.length) {
                float[] grown = new float[values.length * 2];
                System.arraycopy(values, 0, grown, 0, size);
                values = grown;
            }
            values[size++] = x;
            values[size++] = y;
        }

        private float[] toArray() {
            float[] result = new float[size];
            System.arraycopy(values, 0, result, 0, size);
            return result;
        }
    }
}
