package gq.yozakura.auth.vendor.skidonion.sWdSl;

public class JsonMember {
    private final String name;
    private final JsonValue value;

    JsonMember(String name, JsonValue value) {
        this.name = name;
        this.value = value;
    }

    public String getName() { return name; }
    public JsonValue getValue() { return value; }
    public int hashCode() { return name.hashCode() ^ value.hashCode(); }
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof JsonMember)) return false;
        JsonMember other = (JsonMember) obj;
        return name.equals(other.name) && value.equals(other.value);
    }
    static String getName(JsonMember member) { return member.name; }
    static JsonValue getValue(JsonMember member) { return member.value; }
}

