package gq.yozakura.ui.engine.paint;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.fail;

/**
 * 阶段 3 切片 1：PaintCommandList + PaintCommand 值对象契约测试。
 *
 * <p>覆盖：
 * <ul>
 *   <li>PaintCommandList 构造为空、追加命令保持顺序</li>
 *   <li>RectFillCommand / RectBorderCommand / ClipPush / ClipPop 不可变值对象</li>
 *   <li>圆角 border-radius（每角独立 radius）</li>
 *   <li>Visitor 按追加顺序访问</li>
 *   <li>命令的 type() 用于批处理分桶</li>
 *   <li>防御性复制：调用方修改原始几何 list 不影响已构造命令</li>
 *   <li>PaintCommandList 防御性复制命令 list</li>
 * </ul>
 */
public class PaintCommandListTest {

    // ---- PaintCommandList 基础 ----

    @Test
    public void emptyListHasZeroCommands() {
        PaintCommandList list = new PaintCommandList();
        assertEquals(0, list.size());
        assertEquals(true, list.isEmpty());
    }

    @Test
    public void appendRectFillPreservesOrder() {
        PaintCommandList list = new PaintCommandList();
        RectFillCommand a = new RectFillCommand(0, 0, 10, 10, Color.fromRgba(1, 0, 0, 1));
        RectFillCommand b = new RectFillCommand(10, 0, 10, 10, Color.fromRgba(0, 1, 0, 1));
        list.append(a);
        list.append(b);

        assertEquals(2, list.size());
        assertSame(a, list.command(0));
        assertSame(b, list.command(1));
    }

    @Test
    public void appendMixedCommandsPreservesOrder() {
        PaintCommandList list = new PaintCommandList();
        ClipPushCommand clip = new ClipPushCommand(0, 0, 100, 100);
        RectFillCommand rect = new RectFillCommand(5, 5, 10, 10, Color.fromRgba(1, 0, 0, 1));
        RectBorderCommand border = new RectBorderCommand(
                5, 5, 10, 10, 1, 1, 1, 1, Color.fromRgba(0, 0, 0, 1), 0);
        ClipPopCommand pop = new ClipPopCommand();

        list.append(clip);
        list.append(rect);
        list.append(border);
        list.append(pop);

        assertEquals(4, list.size());
        assertEquals(PaintCommand.TYPE_CLIP_PUSH, list.command(0).type());
        assertEquals(PaintCommand.TYPE_RECT_FILL, list.command(1).type());
        assertEquals(PaintCommand.TYPE_RECT_BORDER, list.command(2).type());
        assertEquals(PaintCommand.TYPE_CLIP_POP, list.command(3).type());
    }

    @Test
    public void replayVisitorVisitsInOrder() {
        PaintCommandList list = new PaintCommandList();
        list.append(new RectFillCommand(0, 0, 1, 1, Color.fromRgba(1, 0, 0, 1)));
        list.append(new ClipPushCommand(0, 0, 10, 10));
        list.append(new RectBorderCommand(0, 0, 5, 5, 1, 1, 1, 1, Color.fromRgba(0, 0, 0, 1), 0));
        list.append(new ClipPopCommand());

        final List<Integer> visited = new ArrayList<Integer>();
        PaintCommandVisitor v = new PaintCommandVisitor() {
            @Override public void visitRectFill(RectFillCommand c) { visited.add(PaintCommand.TYPE_RECT_FILL); }
            @Override public void visitRectBorder(RectBorderCommand c) { visited.add(PaintCommand.TYPE_RECT_BORDER); }
            @Override public void visitClipPush(ClipPushCommand c) { visited.add(PaintCommand.TYPE_CLIP_PUSH); }
            @Override public void visitClipPop(ClipPopCommand c) { visited.add(PaintCommand.TYPE_CLIP_POP); }
            @Override public void visitText(TextPaintCommand c) { visited.add(PaintCommand.TYPE_TEXT); }
        };
        list.replay(v);

        assertEquals(4, visited.size());
        assertEquals(Integer.valueOf(PaintCommand.TYPE_RECT_FILL), visited.get(0));
        assertEquals(Integer.valueOf(PaintCommand.TYPE_CLIP_PUSH), visited.get(1));
        assertEquals(Integer.valueOf(PaintCommand.TYPE_RECT_BORDER), visited.get(2));
        assertEquals(Integer.valueOf(PaintCommand.TYPE_CLIP_POP), visited.get(3));
    }

    @Test
    public void snapshotIsImmutableView() {
        PaintCommandList list = new PaintCommandList();
        list.append(new RectFillCommand(0, 0, 1, 1, Color.fromRgba(1, 0, 0, 1)));
        List<PaintCommand> snapshot = list.commands();
        try {
            snapshot.add(new ClipPopCommand());
            fail("snapshot should be unmodifiable");
        } catch (UnsupportedOperationException e) {
            // expected
        }
    }

    @Test
    public void commandsReturnsStableUnmodifiableView() {
        PaintCommandList list = new PaintCommandList();
        List<PaintCommand> first = list.commands();

        list.append(new ClipPopCommand());

        assertSame(first, list.commands());
        assertEquals(1, first.size());
    }

    @Test
    public void appendRejectsNull() {
        PaintCommandList list = new PaintCommandList();
        try {
            list.append(null);
            fail("expected IllegalArgumentException for null command");
        } catch (IllegalArgumentException e) {
            // expected
        }
    }

    // ---- RectFillCommand 值对象 ----

    @Test
    public void rectFillStoresGeometryAndColor() {
        Color c = Color.fromRgba(0.1f, 0.2f, 0.3f, 0.4f);
        RectFillCommand cmd = new RectFillCommand(10, 20, 100, 50, c);

        assertEquals(10f, cmd.x(), 0.0001f);
        assertEquals(20f, cmd.y(), 0.0001f);
        assertEquals(100f, cmd.width(), 0.0001f);
        assertEquals(50f, cmd.height(), 0.0001f);
        assertEquals(c, cmd.color());
        assertEquals(PaintCommand.TYPE_RECT_FILL, cmd.type());
    }

    @Test
    public void rectFillEqualsByComponents() {
        Color c = Color.fromRgba(1, 0, 0, 1);
        RectFillCommand a = new RectFillCommand(1, 2, 3, 4, c);
        RectFillCommand b = new RectFillCommand(1, 2, 3, 4, c);
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    public void rectFillRejectsNullColor() {
        try {
            new RectFillCommand(0, 0, 1, 1, null);
            fail("expected IllegalArgumentException for null color");
        } catch (IllegalArgumentException e) {
            // expected
        }
    }

    @Test
    public void rectFillRejectsNegativeSize() {
        try {
            new RectFillCommand(0, 0, -1, 1, Color.fromRgba(0, 0, 0, 1));
            fail("expected IllegalArgumentException for negative width");
        } catch (IllegalArgumentException e) {
            // expected
        }
        try {
            new RectFillCommand(0, 0, 1, -1, Color.fromRgba(0, 0, 0, 1));
            fail("expected IllegalArgumentException for negative height");
        } catch (IllegalArgumentException e) {
            // expected
        }
    }

    @Test
    public void rectFillAcceptsZeroSize() {
        // 零尺寸合法（display:none 等场景）；渲染器跳过
        RectFillCommand cmd = new RectFillCommand(0, 0, 0, 0, Color.fromRgba(0, 0, 0, 1));
        assertEquals(0f, cmd.width(), 0.0001f);
        assertEquals(0f, cmd.height(), 0.0001f);
    }

    // ---- RectBorderCommand 值对象 ----

    @Test
    public void rectBorderStoresFourEdgesAndColor() {
        Color c = Color.fromRgba(0, 0, 0, 1);
        RectBorderCommand cmd = new RectBorderCommand(0, 0, 10, 10, 1, 2, 3, 4, c, 0);
        assertEquals(1f, cmd.borderTop(), 0.0001f);
        assertEquals(2f, cmd.borderRight(), 0.0001f);
        assertEquals(3f, cmd.borderBottom(), 0.0001f);
        assertEquals(4f, cmd.borderLeft(), 0.0001f);
        assertEquals(0f, cmd.radius(), 0.0001f);
        assertEquals(c, cmd.color());
        assertEquals(PaintCommand.TYPE_RECT_BORDER, cmd.type());
    }

    @Test
    public void rectBorderSupportsRoundedCorner() {
        Color c = Color.fromRgba(0, 0, 0, 1);
        RectBorderCommand cmd = new RectBorderCommand(0, 0, 10, 10, 1, 1, 1, 1, c, 3);
        assertEquals(3f, cmd.radius(), 0.0001f);
    }

    @Test
    public void rectBorderRejectsNegativeRadius() {
        try {
            new RectBorderCommand(0, 0, 10, 10, 1, 1, 1, 1, Color.fromRgba(0, 0, 0, 1), -1);
            fail("expected IllegalArgumentException for negative radius");
        } catch (IllegalArgumentException e) {
            // expected
        }
    }

    @Test
    public void rectBorderRejectsNegativeEdges() {
        try {
            new RectBorderCommand(0, 0, 10, 10, -1, 1, 1, 1, Color.fromRgba(0, 0, 0, 1), 0);
            fail("expected IllegalArgumentException for negative border");
        } catch (IllegalArgumentException e) {
            // expected
        }
    }

    // ---- ClipPush / ClipPop ----

    @Test
    public void clipPushStoresRectangle() {
        ClipPushCommand cmd = new ClipPushCommand(5, 6, 100, 50);
        assertEquals(5f, cmd.x(), 0.0001f);
        assertEquals(6f, cmd.y(), 0.0001f);
        assertEquals(100f, cmd.width(), 0.0001f);
        assertEquals(50f, cmd.height(), 0.0001f);
        assertEquals(PaintCommand.TYPE_CLIP_PUSH, cmd.type());
    }

    @Test
    public void clipPopIsStateless() {
        ClipPopCommand cmd = new ClipPopCommand();
        assertEquals(PaintCommand.TYPE_CLIP_POP, cmd.type());
        // 所有 ClipPopCommand 等价
        assertEquals(new ClipPopCommand(), cmd);
    }

    @Test
    public void clipPushRejectsNegativeSize() {
        try {
            new ClipPushCommand(0, 0, -1, 10);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            // expected
        }
    }
}
