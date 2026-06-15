package gq.yozakura.auth.vendor.skidonion.sWdSl;

import java.io.IOException;
import java.io.Reader;
import java.io.Serializable;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.Writer;

public abstract class JsonValue implements Serializable {
    public static final JsonValue NULL = new JsonLiteral("null");
    public static final JsonValue TRUE = new JsonLiteral("true");
    public static final JsonValue FALSE = new JsonLiteral("false");

    JsonValue() {}

    public static JsonValue parse(Reader reader) throws IOException { return Json.parseReader(reader); }
    public static JsonValue parse(String text) { return Json.parse(text); }
    public static JsonValue valueOf(int value) { return new JsonNumber(Integer.toString(value)); }
    public static JsonValue valueOf(long value) { return new JsonNumber(Long.toString(value)); }
    public static JsonValue valueOf(float value) { return new JsonNumber(Float.toString(value)); }
    public static JsonValue valueOf(double value) { return new JsonNumber(Double.toString(value)); }
    public static JsonValue valueOf(String value) { return value == null ? NULL : new JsonString(value); }
    public static JsonValue valueOf(boolean value) { return value ? TRUE : FALSE; }

    public boolean isObject() { return false; }
    public boolean isArray() { return false; }
    public boolean isNumber() { return false; }
    public boolean isString() { return false; }
    public boolean isBoolean() { return false; }
    public boolean isTrue() { return false; }
    public boolean isFalse() { return false; }
    public boolean isNull() { return false; }
    public JsonObject asObject() { throw new UnsupportedOperationException("Not an object: " + toString()); }
    public JsonArray asArray() { throw new UnsupportedOperationException("Not an array: " + toString()); }
    public int asInt() { throw new UnsupportedOperationException("Not a number: " + toString()); }
    public long asLong() { throw new UnsupportedOperationException("Not a number: " + toString()); }
    public float asFloat() { throw new UnsupportedOperationException("Not a number: " + toString()); }
    public double asDouble() { throw new UnsupportedOperationException("Not a number: " + toString()); }
    public String asString() { throw new UnsupportedOperationException("Not a string: " + toString()); }
    public boolean asBoolean() { throw new UnsupportedOperationException("Not a boolean: " + toString()); }

    public void writeTo(Writer writer) throws IOException { writeTo(writer, JsonWriterConfig.MINIMAL); }

    public void writeTo(Writer writer, JsonWriterConfig config) throws IOException {
        if (writer == null) throw new NullPointerException("writer is null");
        if (config == null) throw new NullPointerException("config is null");
        writeJson(config.createWriter(writer));
    }

    public String toString() { return toString(JsonWriterConfig.MINIMAL); }

    public String toString(JsonWriterConfig config) {
        StringWriter writer = new StringWriter();
        try {
            writeTo(writer, config);
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
        return writer.toString();
    }

    abstract void writeJson(JsonWriter writer) throws IOException;
}

