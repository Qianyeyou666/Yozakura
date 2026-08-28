package gq.yozakura.k.vendor.skidonion.sWdSl;

import java.io.IOException;

class JsonNumber extends JsonValue {
    private final String value;

    JsonNumber(String value) {
        if (value == null) throw new NullPointerException("string is null");
        this.value = value;
    }

    public String toString() { return value; }
    void writeJson(JsonWriter writer) throws IOException { writer.writeRaw(value); }
    public boolean isNumber() { return true; }
    public int asInt() { return Integer.parseInt(value); }
    public long asLong() { return Long.parseLong(value); }
    public float asFloat() { return Float.parseFloat(value); }
    public double asDouble() { return Double.parseDouble(value); }
    public int hashCode() { return value.hashCode(); }
    public boolean equals(Object obj) {
        return obj instanceof JsonNumber && value.equals(((JsonNumber) obj).value);
    }
}

