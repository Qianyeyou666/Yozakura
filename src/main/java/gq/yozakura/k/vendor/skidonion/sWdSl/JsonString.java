package gq.yozakura.k.vendor.skidonion.sWdSl;

import java.io.IOException;

class JsonString extends JsonValue {
    private final String value;

    JsonString(String value) {
        if (value == null) throw new NullPointerException("string is null");
        this.value = value;
    }

    void writeJson(JsonWriter writer) throws IOException { writer.writeString(value); }
    public boolean isString() { return true; }
    public String asString() { return value; }
    public String toString() { return super.toString(); }
    public int hashCode() { return value.hashCode(); }
    public boolean equals(Object obj) {
        return obj instanceof JsonString && value.equals(((JsonString) obj).value);
    }
}

