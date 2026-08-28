package gq.yozakura.ui.engine.render;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.fail;

/**
 * 阶段 3 切片 3：GL 状态守卫契约测试。
 *
 * <p>验证契约：
 * <ul>
 *   <li>构造时调用 {@link GlStateAccess#capture()} 保存当前状态</li>
 *   <li>{@link GlStateGuard#close()} 调用 {@link GlStateAccess#restore(GlStateSnapshot)}
 *       恢复构造时的快照</li>
 *   <li>close 幂等：重复调用不重复 restore</li>
 *   <li>null access 抛 IllegalArgumentException</li>
 *   <li>snapshot 为值对象：相同字段 equals；防御性拷贝数组</li>
 * </ul>
 *
 * <p>实际 GL 行为（getBoolean/getInteger 等真实查询）需在 Minecraft 实机环境验证，
 * 单测只覆盖契约与编排顺序。
 */
public class GlStateGuardTest {

    private static final class FakeAccess implements GlStateAccess {
        GlStateSnapshot lastCaptured;
        GlStateSnapshot lastRestored;
        int captureCount;
        int restoreCount;

        @Override
        public GlStateSnapshot capture() {
            captureCount++;
            // 返回带标记的 snapshot 以便断言
            GlStateSnapshot s = new GlStateSnapshot();
            // 用 sentinel 验证 round-trip
            s.framebufferBinding = 1001;
            s.viewport = new int[]{0, 0, 960, 540};
            s.blendEnabled = true;
            s.blendSrc = 770; // GL_SRC_ALPHA
            s.blendDst = 771; // GL_ONE_MINUS_SRC_ALPHA
            s.currentColor = new float[]{1f, 1f, 1f, 1f};
            lastCaptured = s;
            return s;
        }

        @Override
        public void restore(GlStateSnapshot snapshot) {
            restoreCount++;
            lastRestored = snapshot;
        }
    }

    @Test
    public void constructorCapturesImmediately() {
        FakeAccess fake = new FakeAccess();
        GlStateGuard guard = new GlStateGuard(fake);
        assertEquals(1, fake.captureCount);
        assertEquals(0, fake.restoreCount);
        guard.close();
    }

    @Test
    public void closeRestoresCapturedSnapshot() {
        FakeAccess fake = new FakeAccess();
        GlStateGuard guard = new GlStateGuard(fake);
        assertSame(fake.lastCaptured, guard.snapshot());
        guard.close();
        assertEquals(1, fake.restoreCount);
        assertSame(fake.lastCaptured, fake.lastRestored);
    }

    @Test
    public void closeIsIdempotent() {
        // 重复 close 不应重复 restore（资源释放幂等，AGENTS.md 要求）
        FakeAccess fake = new FakeAccess();
        GlStateGuard guard = new GlStateGuard(fake);
        guard.close();
        guard.close();
        guard.close();
        assertEquals(1, fake.restoreCount);
    }

    @Test
    public void nullAccessThrows() {
        try {
            new GlStateGuard(null);
            fail("expected IllegalArgumentException for null access");
        } catch (IllegalArgumentException e) {
            // expected
        }
    }

    @Test
    public void snapshotAccessibleForInspection() {
        // snapshot() 返回构造时捕获的快照；调用方可用于调试或断言
        FakeAccess fake = new FakeAccess();
        GlStateGuard guard = new GlStateGuard(fake);
        GlStateSnapshot s = guard.snapshot();
        assertEquals(1001, s.framebufferBinding);
        assertEquals(true, s.blendEnabled);
        guard.close();
    }

    // ---- GlStateSnapshot 值对象 ----

    @Test
    public void snapshotEqualsByFields() {
        GlStateSnapshot a = new GlStateSnapshot();
        GlStateSnapshot b = new GlStateSnapshot();
        a.framebufferBinding = 5;
        a.viewport = new int[]{1, 2, 3, 4};
        a.blendEnabled = true;
        a.blendSrc = 770;
        a.blendDst = 771;
        a.alphaTestEnabled = false;
        a.depthTestEnabled = true;
        a.depthMask = true;
        a.scissorTestEnabled = false;
        a.scissorBox = new int[]{0, 0, 100, 100};
        a.stencilTestEnabled = false;
        a.texture2dEnabled = true;
        a.activeTextureUnit = 33984; // GL_TEXTURE0
        a.textureBinding2D = 42;
        a.currentProgram = 0;
        a.currentColor = new float[]{1f, 0.5f, 0f, 1f};
        a.matrixMode = 5889; // GL_PROJECTION
        a.projectionMatrix = new float[16];
        a.modelviewMatrix = new float[16];
        for (int i = 0; i < 16; i++) {
            a.projectionMatrix[i] = i;
            a.modelviewMatrix[i] = i * 2;
        }

        b.framebufferBinding = 5;
        b.viewport = new int[]{1, 2, 3, 4};
        b.blendEnabled = true;
        b.blendSrc = 770;
        b.blendDst = 771;
        b.alphaTestEnabled = false;
        b.depthTestEnabled = true;
        b.depthMask = true;
        b.scissorTestEnabled = false;
        b.scissorBox = new int[]{0, 0, 100, 100};
        b.stencilTestEnabled = false;
        b.texture2dEnabled = true;
        b.activeTextureUnit = 33984;
        b.textureBinding2D = 42;
        b.currentProgram = 0;
        b.currentColor = new float[]{1f, 0.5f, 0f, 1f};
        b.matrixMode = 5889;
        b.projectionMatrix = new float[16];
        b.modelviewMatrix = new float[16];
        for (int i = 0; i < 16; i++) {
            b.projectionMatrix[i] = i;
            b.modelviewMatrix[i] = i * 2;
        }

        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    public void snapshotNotEqualWhenFieldsDiffer() {
        GlStateSnapshot a = new GlStateSnapshot();
        GlStateSnapshot b = new GlStateSnapshot();
        a.blendEnabled = true;
        b.blendEnabled = false;
        assert !a.equals(b);
    }

    @Test
    public void snapshotDefensiveCopiesArrays() {
        // GlStateSnapshot 构造后修改传入数组不应影响内部状态
        GlStateSnapshot s = new GlStateSnapshot();
        int[] viewport = {1, 2, 3, 4};
        s.viewport = viewport;
        // 直接赋值是字段赋值；snapshot 本身是可变结构（被 Access 填充）。
        // 但 GlStateSnapshot.copy() 必须防御性复制数组字段。
        GlStateSnapshot copy = s.copy();
        viewport[0] = 999;
        assertNotSame("copy must not share viewport array reference", s.viewport, copy.viewport);
        assertEquals(1, copy.viewport[0]);
    }

    @Test
    public void snapshotCopyPreservesAllFields() {
        GlStateSnapshot s = new GlStateSnapshot();
        s.framebufferBinding = 7;
        s.viewport = new int[]{1, 2, 3, 4};
        s.blendEnabled = true;
        s.blendSrc = 770;
        s.blendDst = 771;
        s.scissorBox = new int[]{5, 6, 7, 8};
        s.currentColor = new float[]{0.1f, 0.2f, 0.3f, 0.4f};
        s.projectionMatrix = new float[16];
        s.modelviewMatrix = new float[16];
        s.projectionMatrix[0] = 1.5f;

        GlStateSnapshot copy = s.copy();
        assertEquals(s, copy);
        assertEquals(1.5f, copy.projectionMatrix[0], 0.0001f);
        // 修改 copy 不影响 original
        copy.projectionMatrix[0] = 999f;
        assertEquals(1.5f, s.projectionMatrix[0], 0.0001f);
    }

    // ---- 嵌套 guard ----

    @Test
    public void nestedGuardsRestoreInLifoOrder() {
        // 嵌套 guard 应 LIFO 恢复（内层先 close）
        FakeAccess fake = new FakeAccess();
        GlStateGuard outer = new GlStateGuard(fake);
        GlStateSnapshot outerSnapshot = outer.snapshot();
        // 模拟内层 capture 改变状态
        fake.lastCaptured = null;
        GlStateGuard inner = new GlStateGuard(fake);
        GlStateSnapshot innerSnapshot = inner.snapshot();

        inner.close();
        assertSame(innerSnapshot, fake.lastRestored);
        outer.close();
        assertSame(outerSnapshot, fake.lastRestored);
        assertEquals(2, fake.captureCount);
        assertEquals(2, fake.restoreCount);
    }
}
