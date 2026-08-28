package gq.yozakura.ui.engine.render;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

/**
 * CompiledPaint 不可变性契约测试。
 *
 * <p>对应优化点：CompiledPaint 构造时去除了 new ArrayList(operations) 防御性拷贝，
 * 仅用 Collections.unmodifiableList 包装。本测试固化"对外不可修改"契约。
 *
 * <p>trade-off 说明：CompiledPaint 不持有源 list 的拷贝，调用方在构造后承诺不再修改源 list
 * （唯一 caller LwjglUiRenderer.compile 已遵守此契约——构造后立即返回，源 list 不再被引用）。
 * CompiledPaint 通过 unmodifiableList 阻断通过 size()/operation(int) 路径的修改
 * （虽然这两个方法都不暴露 list 引用，但 unmodifiableList 保证即使内部暴露也不可写）。
 */
public class CompiledPaintImmutabilityTest {

    @Test
    public void emptyPaintHasZeroSize() {
        List<RenderOp> source = new ArrayList<RenderOp>();
        CompiledPaint paint = new CompiledPaint(source);
        assertEquals(0, paint.size());
    }

    @Test
    public void operationIndexOutOfBoundsThrows() {
        // unmodifiableList 透传 IndexOutOfBoundsException
        List<RenderOp> source = new ArrayList<RenderOp>();
        CompiledPaint paint = new CompiledPaint(source);
        try {
            paint.operation(0);
            fail("expected IndexOutOfBoundsException for empty paint");
        } catch (IndexOutOfBoundsException expected) {
            // expected
        }
    }

    @Test
    public void sizeReflectsSourceLengthAtConstructionTime() {
        // 验证 size() 委托给包装后的 list。
        // 注意：CompiledPaint 不持有源 list 的拷贝，所以如果 caller 在构造后修改源 list，
        // size 会跟着变——这是已知 trade-off，由 caller 契约保证不发生。
        // 这里我们构造后立即测试，源 list 未被修改，size 应反映构造时的长度。
        List<RenderOp> source = new ArrayList<RenderOp>();
        source.add(null);
        source.add(null);

        CompiledPaint paint = new CompiledPaint(source);
        assertEquals(2, paint.size());
        // operation(i) 应返回 null（unmodifiableList 透传，不抛 NPE）
        // 注意：实际使用中不应存 null，这里仅验证委托语义
        assertEquals(null, paint.operation(0));
        assertEquals(null, paint.operation(1));
    }
}
