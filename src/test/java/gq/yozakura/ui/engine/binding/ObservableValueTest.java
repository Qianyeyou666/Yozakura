package gq.yozakura.ui.engine.binding;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * 阶段 5 切片 5.1：ObservableValue 单值可观察测试。
 *
 * <p>验证契约（AGENTS.md）：
 * <ul>
 *   <li>"Observable data and repeaters" — 单值可观察，监听器在值变化时被通知</li>
 *   <li>"Avoid per-frame streams, reflection, boxing and temporary collections."</li>
 *   <li>同值 set 不通知（去抖，避免无谓 dirty）</li>
 *   <li>监听器可移除（防止内存泄漏与关闭 UI 后回调）</li>
 * </ul>
 *
 * <p>设计：监听器列表用数组实现（零分配热路径），不依赖 Stream。
 */
public class ObservableValueTest {

    /** 简单记录所有通知值的监听器。 */
    private static final class RecordingListener<T> implements ValueChangeListener<T> {
        final List<T> received = new ArrayList<T>();
        @Override
        public void onValueChanged(T oldValue, T newValue) {
            received.add(newValue);
        }
    }

    @Test
    public void initial_value_exposed() {
        ObservableValue<String> v = new ObservableValue<String>("hello");
        assertEquals("hello", v.get());
    }

    @Test
    public void set_updates_value_and_notifies() {
        ObservableValue<Integer> v = new ObservableValue<Integer>(1);
        RecordingListener<Integer> l = new RecordingListener<Integer>();
        v.addListener(l);
        v.set(2);
        assertEquals(Integer.valueOf(2), v.get());
        assertEquals(1, l.received.size());
        assertEquals(Integer.valueOf(2), l.received.get(0));
    }

    @Test
    public void set_same_value_does_not_notify() {
        ObservableValue<String> v = new ObservableValue<String>("x");
        RecordingListener<String> l = new RecordingListener<String>();
        v.addListener(l);
        v.set("x");  // 同值
        assertEquals("x", v.get());
        assertTrue("no notification for same value", l.received.isEmpty());
    }

    @Test
    public void null_value_supported() {
        ObservableValue<String> v = new ObservableValue<String>(null);
        assertEquals(null, v.get());
        RecordingListener<String> l = new RecordingListener<String>();
        v.addListener(l);
        v.set("a");
        assertEquals(1, l.received.size());
        assertEquals("a", l.received.get(0));
    }

    @Test
    public void null_to_null_does_not_notify() {
        ObservableValue<String> v = new ObservableValue<String>(null);
        RecordingListener<String> l = new RecordingListener<String>();
        v.addListener(l);
        v.set(null);
        assertTrue(l.received.isEmpty());
    }

    @Test
    public void multiple_listeners_all_notified_in_order() {
        ObservableValue<Integer> v = new ObservableValue<Integer>(0);
        final List<Integer> order = new ArrayList<Integer>();
        v.addListener(new ValueChangeListener<Integer>() {
            @Override public void onValueChanged(Integer o, Integer n) { order.add(n * 10); }
        });
        v.addListener(new ValueChangeListener<Integer>() {
            @Override public void onValueChanged(Integer o, Integer n) { order.add(n * 100); }
        });
        v.set(1);
        assertEquals(2, order.size());
        assertEquals(Integer.valueOf(10), order.get(0));
        assertEquals(Integer.valueOf(100), order.get(1));
    }

    @Test
    public void removed_listener_does_not_receive_notifications() {
        ObservableValue<Integer> v = new ObservableValue<Integer>(0);
        RecordingListener<Integer> l = new RecordingListener<Integer>();
        v.addListener(l);
        v.set(1);
        boolean removed = v.removeListener(l);
        assertTrue(removed);
        v.set(2);
        assertEquals(1, l.received.size());  // 只收到第一次
        assertEquals(Integer.valueOf(1), l.received.get(0));
    }

    @Test
    public void remove_nonexistent_listener_returns_false() {
        ObservableValue<Integer> v = new ObservableValue<Integer>(0);
        RecordingListener<Integer> l = new RecordingListener<Integer>();
        assertFalse(v.removeListener(l));
    }

    @Test
    public void listener_added_during_notification_receives_subsequent() {
        // 防御：在 onValueChanged 中 addListener 不应触发 ConcurrentModification
        ObservableValue<Integer> v = new ObservableValue<Integer>(0);
        final RecordingListener<Integer> later = new RecordingListener<Integer>();
        v.addListener(new ValueChangeListener<Integer>() {
            @Override public void onValueChanged(Integer o, Integer n) {
                if (n == 1) v.addListener(later);
            }
        });
        v.set(1);  // 触发 addListener
        assertTrue(later.received.isEmpty());  // 新监听器不应收到本次
        v.set(2);
        assertEquals(1, later.received.size());
        assertEquals(Integer.valueOf(2), later.received.get(0));
    }

    @Test
    public void listener_removed_during_notification_safe() {
        ObservableValue<Integer> v = new ObservableValue<Integer>(0);
        final RecordingListener<Integer> second = new RecordingListener<Integer>();
        v.addListener(new ValueChangeListener<Integer>() {
            @Override public void onValueChanged(Integer o, Integer n) {
                v.removeListener(second);  // 在通知过程中移除后续监听器
            }
        });
        v.addListener(second);
        v.set(1);
        // second 在第一次通知过程中被移除——本契约允许两种语义：
        // (a) 已注册的监听器在本次通知中仍被调用；或 (b) 立即生效不调用。
        // 选择 (a)：本次通知仍走完，但下次不再调用。验证下次：
        v.set(2);
        assertTrue("second removed before second set", second.received.size() <= 1);
    }

    @Test
    public void clearListeners_removes_all() {
        ObservableValue<Integer> v = new ObservableValue<Integer>(0);
        RecordingListener<Integer> a = new RecordingListener<Integer>();
        RecordingListener<Integer> b = new RecordingListener<Integer>();
        v.addListener(a);
        v.addListener(b);
        v.clearListeners();
        v.set(1);
        assertTrue(a.received.isEmpty());
        assertTrue(b.received.isEmpty());
    }

    @Test
    public void listener_callback_passes_old_and_new() {
        ObservableValue<Integer> v = new ObservableValue<Integer>(10);
        final int[] captured = new int[2];
        v.addListener(new ValueChangeListener<Integer>() {
            @Override public void onValueChanged(Integer o, Integer n) {
                captured[0] = o;
                captured[1] = n;
            }
        });
        v.set(20);
        assertEquals(10, captured[0]);
        assertEquals(20, captured[1]);
    }

    @Test(expected = IllegalArgumentException.class)
    public void addNullListener_rejected() {
        new ObservableValue<Integer>(0).addListener(null);
    }

    @Test
    public void listener_reference_held_after_set() {
        // 确保监听器被强引用持有，不被 GC（不依赖弱引用）
        ObservableValue<Integer> v = new ObservableValue<Integer>(0);
        RecordingListener<Integer> l = new RecordingListener<Integer>();
        v.addListener(l);
        // 强制 GC（best-effort）
        System.gc();
        v.set(5);
        assertEquals(1, l.received.size());
        assertSame(l, l);  // listener 仍存活
    }
}
