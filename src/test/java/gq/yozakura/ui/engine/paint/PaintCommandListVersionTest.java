package gq.yozakura.ui.engine.paint;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * PaintCommandList.version() 契约测试：修订号语义。
 *
 * <p>对应优化点：LwjglUiRenderer.compile() 按 (ref, version) 缓存 CompiledPaint。
 * version 必须在每次 append/clear 时自增，让 cache 在源列表内容变化时正确失效。
 */
public class PaintCommandListVersionTest {

    @Test
    public void freshListVersionIsZero() {
        PaintCommandList list = new PaintCommandList();
        assertEquals(0, list.version());
    }

    @Test
    public void appendIncrementsVersion() {
        PaintCommandList list = new PaintCommandList();
        int v0 = list.version();

        list.append(new RectFillCommand(0, 0, 10, 10, Color.parse("#ffffff")));
        assertEquals(v0 + 1, list.version());

        list.append(new RectFillCommand(0, 0, 20, 20, Color.parse("#000000")));
        assertEquals(v0 + 2, list.version());
    }

    @Test
    public void clearIncrementsVersion() {
        PaintCommandList list = new PaintCommandList();
        list.append(new RectFillCommand(0, 0, 10, 10, Color.parse("#ffffff")));
        int v0 = list.version();

        list.clear();
        assertEquals(v0 + 1, list.version());
        assertEquals(0, list.size());
    }

    @Test
    public void emptyClearStillIncrementsVersion() {
        // clear 在空 list 上也应自增（语义一致，避免 caller 误判 cache 仍有效）
        PaintCommandList list = new PaintCommandList();
        int v0 = list.version();
        list.clear();
        assertTrue(v0 != list.version());
    }
}
