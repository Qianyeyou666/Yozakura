package gq.yozakura.ui.engine.input;

/**
 * 键盘事件值对象：携带类型、逻辑键名、可打印字符、修饰键与时间戳。
 *
 * <p>AGENTS.md 契约："Focus, text editing and keyboard routing must have one owner.
 * Closing the UI restores cursor, repeat-key and focus state."
 *
 * <p>key 为逻辑键名（如 "A"、"LEFT"、"BACKSPACE"），由 host 层从 LWJGL 2 键码映射。
 * character 为可打印字符（如 'a'），无字符输入时为 0。
 *
 * <p>不可变。线程安全（只读）。
 */
public final class KeyboardEvent {

    private final KeyType type;
    private final String key;
    private final char character;
    private final ModifierKeys modifiers;
    private final long timestamp;

    private KeyboardEvent(KeyType type, String key, char character,
                          ModifierKeys modifiers, long timestamp) {
        if (type == null) {
            throw new IllegalArgumentException("type must not be null");
        }
        if (key == null) {
            throw new IllegalArgumentException("key must not be null");
        }
        if (modifiers == null) {
            throw new IllegalArgumentException("modifiers must not be null");
        }
        if (timestamp < 0L) {
            throw new IllegalArgumentException("timestamp must not be negative: " + timestamp);
        }
        this.type = type;
        this.key = key;
        this.character = character;
        this.modifiers = modifiers;
        this.timestamp = timestamp;
    }

    /** 按键按下事件。 */
    public static KeyboardEvent down(String key, ModifierKeys modifiers, long timestamp) {
        return new KeyboardEvent(KeyType.DOWN, key, (char) 0, modifiers, timestamp);
    }

    /** 按键释放事件。 */
    public static KeyboardEvent up(String key, ModifierKeys modifiers, long timestamp) {
        return new KeyboardEvent(KeyType.UP, key, (char) 0, modifiers, timestamp);
    }

    /** 长按重复键事件（操作系统自动重复）。 */
    public static KeyboardEvent repeat(String key, ModifierKeys modifiers, long timestamp) {
        return new KeyboardEvent(KeyType.REPEAT, key, (char) 0, modifiers, timestamp);
    }

    /**
     * 字符输入事件（由字符输入回调产生，非按键事件）。
     *
     * @param character 可打印字符
     * @param key       逻辑键名（通常与 character 一致，如 "a"）
     */
    public static KeyboardEvent character(char character, String key,
                                           ModifierKeys modifiers, long timestamp) {
        return new KeyboardEvent(KeyType.DOWN, key, character, modifiers, timestamp);
    }

    public KeyType type() { return type; }
    public String key() { return key; }
    public char character() { return character; }
    public ModifierKeys modifiers() { return modifiers; }
    public long timestamp() { return timestamp; }

    /** 是否为字符输入事件（character != 0）。 */
    public boolean isCharacterInput() {
        return character != 0;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof KeyboardEvent)) return false;
        KeyboardEvent e = (KeyboardEvent) o;
        return type == e.type
                && key.equals(e.key)
                && character == e.character
                && modifiers.equals(e.modifiers)
                && timestamp == e.timestamp;
    }

    @Override
    public int hashCode() {
        int r = type.hashCode();
        r = 31 * r + key.hashCode();
        r = 31 * r + (int) character;
        r = 31 * r + modifiers.hashCode();
        r = 31 * r + (int) (timestamp ^ (timestamp >>> 32));
        return r;
    }

    @Override
    public String toString() {
        return "KeyboardEvent{" + type + " key=" + key + " char=" + (int) character
                + " mods=" + modifiers + " t=" + timestamp + "}";
    }
}
