package gq.yozakura.ui.engine.input;

/**
 * 键盘事件类型枚举。
 *
 * <p>MVP 子集：DOWN / UP / REPEAT。
 * REPEAT 对应长按重复键（AGENTS.md "repeat-key" 状态）。
 */
public enum KeyType {
    DOWN,
    UP,
    REPEAT
}
