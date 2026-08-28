package gq.yozakura.ui.engine.css;

/**
 * CSS 属性选择器。MVP 支持 [name]、[name=val]。
 * 不可变值对象。
 */
public final class AttrSelector {
    private final String name;
    private final String value;
    private final boolean presence;

    private AttrSelector(String name, String value, boolean presence) {
        this.name = name;
        this.value = value;
        this.presence = presence;
    }

    /** [name] 存在选择器。 */
    public static AttrSelector presence(String name) {
        return new AttrSelector(name, null, true);
    }

    /** [name=val] 等值选择器。 */
    public static AttrSelector equals(String name, String value) {
        return new AttrSelector(name, value, false);
    }

    public String name() { return name; }
    public String value() { return value; }
    public boolean isPresence() { return presence; }
}
