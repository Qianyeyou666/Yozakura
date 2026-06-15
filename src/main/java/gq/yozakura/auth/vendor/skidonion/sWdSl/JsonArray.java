package gq.yozakura.auth.vendor.skidonion.sWdSl;

import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

public class JsonArray extends JsonValue implements Iterable<JsonValue> {
    private final List<JsonValue> values;

    public JsonArray() { this.values = new ArrayList<JsonValue>(); }
    public JsonArray(JsonArray other) { this(other, false); }
    private JsonArray(JsonArray other, boolean immutable) {
        if (other == null) throw new NullPointerException("array is null");
        this.values = immutable ? Collections.unmodifiableList(other.values) : new ArrayList<JsonValue>(other.values);
    }

    @Deprecated public static JsonArray readFrom(Reader reader) throws IOException { return JsonValue.parse(reader).asArray(); }
    @Deprecated public static JsonArray parse(String text) { return JsonValue.parse(text).asArray(); }
    public static JsonArray unmodifiable(JsonArray array) { return new JsonArray(array, true); }
    public JsonArray addInt(int value) { return add(JsonValue.valueOf(value)); }
    public JsonArray addLong(long value) { return add(JsonValue.valueOf(value)); }
    public JsonArray addFloat(float value) { return add(JsonValue.valueOf(value)); }
    public JsonArray addDouble(double value) { return add(JsonValue.valueOf(value)); }
    public JsonArray addBoolean(boolean value) { return add(JsonValue.valueOf(value)); }
    public JsonArray addString(String value) { return add(JsonValue.valueOf(value)); }
    public JsonArray add(JsonValue value) { values.add(value == null ? JsonValue.NULL : value); return this; }
    public JsonArray setInt(int index, int value) { return set(index, JsonValue.valueOf(value)); }
    public JsonArray setLong(int index, long value) { return set(index, JsonValue.valueOf(value)); }
    public JsonArray setFloat(int index, float value) { return set(index, JsonValue.valueOf(value)); }
    public JsonArray setDouble(int index, double value) { return set(index, JsonValue.valueOf(value)); }
    public JsonArray setBoolean(int index, boolean value) { return set(index, JsonValue.valueOf(value)); }
    public JsonArray setString(int index, String value) { return set(index, JsonValue.valueOf(value)); }
    public JsonArray set(int index, JsonValue value) { values.set(index, value == null ? JsonValue.NULL : value); return this; }
    public JsonArray removeAt(int index) { values.remove(index); return this; }
    public int size() { return values.size(); }
    public boolean isEmpty() { return values.isEmpty(); }
    public JsonValue get(int index) { return values.get(index); }
    public List<JsonValue> values() { return Collections.unmodifiableList(values); }
    public Iterator<JsonValue> iterator() { return new JsonArrayIterator(this, values.iterator()); }
    void writeJson(JsonWriter writer) throws IOException {
        writer.beginArray();
        for (int idx = 0; idx < values.size(); idx++) {
            if (idx > 0) writer.writeSeparator();
            values.get(idx).writeJson(writer);
        }
        writer.endArray();
    }
    public boolean isArray() { return true; }
    public JsonArray asArray() { return this; }
    public int hashCode() { return values.hashCode(); }
    public boolean equals(Object obj) { return obj instanceof JsonArray && values.equals(((JsonArray) obj).values); }
}

