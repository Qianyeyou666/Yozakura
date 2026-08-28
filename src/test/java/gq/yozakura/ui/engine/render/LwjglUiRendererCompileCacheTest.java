package gq.yozakura.ui.engine.render;

import gq.yozakura.ui.engine.paint.Color;
import gq.yozakura.ui.engine.paint.PaintCommandList;
import gq.yozakura.ui.engine.paint.RectFillCommand;
import gq.yozakura.ui.engine.text.FontManager;
import gq.yozakura.ui.engine.text.GlyphAtlas;
import gq.yozakura.ui.engine.text.GlyphRasterizer;
import gq.yozakura.ui.engine.text.GlyphTextureBackend;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

/**
 * LwjglUiRenderer.compile() cache 契约测试。
 *
 * <p>对应优化点：compile() 按 (ref, version) 缓存 CompiledPaint，避免静态帧重复 replay
 * 与 ArrayList 分配。AGENTS.md 性能目标：no HTML/CSS parsing on steady frames；
 * retained paint command list 在静态帧应直接复用编译结果。
 *
 * <p>本测试不需要 OpenGL 上下文：compile() 路径只走 dispatcher.replay，
 * 空 list 不会触发任何 GL 调用；仅验证 cache 命中/失效语义。
 */
public class LwjglUiRendererCompileCacheTest {

    private static final class StubTextureBackend implements GlyphTextureBackend {
        @Override public int createPage(int width, int height) { return 1; }
        @Override public void uploadAlpha(int textureId, int x, int y, int w, int h, byte[] alpha) { }
        @Override public void deletePage(int textureId) { }
    }

    private LwjglUiRenderer newRenderer() {
        // FontManager 可以无 register family 实例化；空 list 不走 text 路径，
        // 不需要真实字体注册。GlyphAtlas 用 stub backend 即可。
        FontManager fonts = new FontManager();
        GlyphAtlas atlas = new GlyphAtlas(64, 64, 1, new StubTextureBackend(), new GlyphRasterizer());
        return new LwjglUiRenderer(fonts, atlas);
    }

    @Test
    public void firstCompileIsMiss() {
        LwjglUiRenderer renderer = newRenderer();
        PaintCommandList list = new PaintCommandList();

        renderer.compile(list);

        assertEquals(1, renderer.stats().compileMisses);
        assertEquals(0, renderer.stats().compileHits);
    }

    @Test
    public void sameRefAndVersionReturnsCached() {
        LwjglUiRenderer renderer = newRenderer();
        PaintCommandList list = new PaintCommandList();

        CompiledPaint first = renderer.compile(list);
        CompiledPaint second = renderer.compile(list);

        assertSame("same ref + same version must return identical CompiledPaint", first, second);
        assertEquals(1, renderer.stats().compileMisses);
        assertEquals(1, renderer.stats().compileHits);
    }

    @Test
    public void versionChangeInvalidatesCache() {
        LwjglUiRenderer renderer = newRenderer();
        PaintCommandList list = new PaintCommandList();

        renderer.compile(list);
        list.append(new RectFillCommand(0, 0, 10, 10, Color.parse("#ffffff")));  // version++

        renderer.compile(list);
        // 第二次 compile 应该重新编译（version 变了）
        assertEquals(2, renderer.stats().compileMisses);
        assertEquals(0, renderer.stats().compileHits);
    }

    @Test
    public void differentRefInvalidatesCache() {
        LwjglUiRenderer renderer = newRenderer();
        PaintCommandList list1 = new PaintCommandList();
        PaintCommandList list2 = new PaintCommandList();

        renderer.compile(list1);
        renderer.compile(list2);

        assertEquals(2, renderer.stats().compileMisses);
        assertEquals(0, renderer.stats().compileHits);
    }

    @Test
    public void appendThenSameRefVersionMatchesStillCaches() {
        // 验证 cache 是 (ref, version) 双 key，不能仅靠 ref 命中
        LwjglUiRenderer renderer = newRenderer();
        PaintCommandList list = new PaintCommandList();

        renderer.compile(list);              // miss (v0)
        list.append(new RectFillCommand(0, 0, 10, 10, Color.parse("#ffffff")));  // v1
        renderer.compile(list);              // miss (v1)
        renderer.compile(list);              // hit (v1)

        assertEquals(2, renderer.stats().compileMisses);
        assertEquals(1, renderer.stats().compileHits);
    }

    @Test
    public void statsAccumulateAcrossCallsUntilReset() {
        // stats 是 LwjglUiRenderer 字段，跨 compile() 调用累加，需调用方显式 reset
        LwjglUiRenderer renderer = newRenderer();
        PaintCommandList list = new PaintCommandList();

        renderer.compile(list);
        renderer.compile(list);
        renderer.compile(list);

        assertEquals(1, renderer.stats().compileMisses);
        assertEquals(2, renderer.stats().compileHits);

        renderer.stats().reset();
        assertEquals(0, renderer.stats().compileMisses);
        assertEquals(0, renderer.stats().compileHits);
    }
}
