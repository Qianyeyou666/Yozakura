package gq.yozakura.ui.engine.input;

/**
 * 修饰键状态：shift / ctrl / alt（MVP 不含 super/meta）。
 *
 * <p>不可变值对象。{@link #none()} 返回共享空实例，零分配友好。
 *
 * <p>由 {@link Builder} 构造以保证可读性；后续可扩展为 fromGlfwMods(int) 工厂方法。
 */
public final class ModifierKeys {

    private static final ModifierKeys NONE = new ModifierKeys(false, false, false);

    private final boolean shift;
    private final boolean ctrl;
    private final boolean alt;

    private ModifierKeys(boolean shift, boolean ctrl, boolean alt) {
        this.shift = shift;
        this.ctrl = ctrl;
        this.alt = alt;
    }

    /** 共享空实例（所有修饰键 false）。 */
    public static ModifierKeys none() {
        return NONE;
    }

    public static Builder builder() {
        return new Builder();
    }

    public boolean shift() { return shift; }
    public boolean ctrl() { return ctrl; }
    public boolean alt() { return alt; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ModifierKeys)) return false;
        ModifierKeys m = (ModifierKeys) o;
        return shift == m.shift && ctrl == m.ctrl && alt == m.alt;
    }

    @Override
    public int hashCode() {
        int r = (shift ? 1 : 0);
        r = 31 * r + (ctrl ? 1 : 0);
        r = 31 * r + (alt ? 1 : 0);
        return r;
    }

    @Override
    public String toString() {
        return "Modifiers(shift=" + shift + ",ctrl=" + ctrl + ",alt=" + alt + ")";
    }

    /** ModifierKeys 构造器。未设置的修饰键默认 false。 */
    public static final class Builder {
        private boolean shift;
        private boolean ctrl;
        private boolean alt;

        public Builder shift(boolean v) { this.shift = v; return this; }
        public Builder ctrl(boolean v) { this.ctrl = v; return this; }
        public Builder alt(boolean v) { this.alt = v; return this; }

        public ModifierKeys build() {
            if (!shift && !ctrl && !alt) {
                return NONE;  // 共享空实例
            }
            return new ModifierKeys(shift, ctrl, alt);
        }
    }
}
