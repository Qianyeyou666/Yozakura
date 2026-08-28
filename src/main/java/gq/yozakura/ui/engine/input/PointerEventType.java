package gq.yozakura.ui.engine.input;

/**
 * 指针事件类型枚举。
 *
 * <p>MVP 子集：DOWN / UP / MOVE / WHEEL。
 * ENTER/EXIT 由命中测试在 hover 变化时合成（阶段 4 切片 4.4），暂不在事件流中暴露。
 * DRAG 由捕获状态合成（阶段 4 切片 4.5）。
 */
public enum PointerEventType {
    DOWN,
    UP,
    MOVE,
    WHEEL
}
