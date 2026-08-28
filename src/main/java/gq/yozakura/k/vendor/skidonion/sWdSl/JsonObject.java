package gq.yozakura.k.vendor.skidonion.sWdSl;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

public class JsonObject extends JsonValue implements Iterable<JsonMember> {
    private final List<String> names;
    private final List<JsonValue> values;
    private transient JsonNameIndex index;

    public JsonObject() {
        this.names = new ArrayList<String>();
        this.values = new ArrayList<JsonValue>();
        this.index = new JsonNameIndex();
    }

    public JsonObject(JsonObject other) { this(other, false); }
    private JsonObject(JsonObject other, boolean immutable) {
        if (other == null) throw new NullPointerException("object is null");
        this.names = immutable ? Collections.unmodifiableList(other.names) : new ArrayList<String>(other.names);
        this.values = immutable ? Collections.unmodifiableList(other.values) : new ArrayList<JsonValue>(other.values);
        this.index = new JsonNameIndex();
        rebuildIndex();
    }

    @Deprecated public static JsonObject readFrom(Reader reader) throws IOException { return JsonValue.parse(reader).asObject(); }
    @Deprecated public static JsonObject parse(String text) { return JsonValue.parse(text).asObject(); }
    public static JsonObject unmodifiable(JsonObject object) { return new JsonObject(object, true); }
    public JsonObject addInt(String name, int value) { return add(name, JsonValue.valueOf(value)); }
    public JsonObject addLong(String name, long value) { return add(name, JsonValue.valueOf(value)); }
    public JsonObject addFloat(String name, float value) { return add(name, JsonValue.valueOf(value)); }
    public JsonObject addDouble(String name, double value) { return add(name, JsonValue.valueOf(value)); }
    public JsonObject addBoolean(String name, boolean value) { return add(name, JsonValue.valueOf(value)); }
    public JsonObject addString(String name, String value) { return add(name, JsonValue.valueOf(value)); }
    public JsonObject add(String name, JsonValue value) {
        if (name == null) throw new NullPointerException("name is null");
        index.put(name, names.size());
        names.add(name);
        values.add(value == null ? JsonValue.NULL : value);
        return this;
    }
    public JsonObject setInt(String name, int value) { return set(name, JsonValue.valueOf(value)); }
    public JsonObject setLong(String name, long value) { return set(name, JsonValue.valueOf(value)); }
    public JsonObject setFloat(String name, float value) { return set(name, JsonValue.valueOf(value)); }
    public JsonObject setDouble(String name, double value) { return set(name, JsonValue.valueOf(value)); }
    public JsonObject setBoolean(String name, boolean value) { return set(name, JsonValue.valueOf(value)); }
    public JsonObject setString(String name, String value) { return set(name, JsonValue.valueOf(value)); }
    public JsonObject set(String name, JsonValue value) {
        if (name == null) throw new NullPointerException("name is null");
        int index = indexOfName(name);
        if (index != -1) {
            values.set(index, value == null ? JsonValue.NULL : value);
        } else {
            add(name, value);
        }
        return this;
    }
    public JsonObject removeName(String name) {
        int idx = indexOfName(name);
        if (idx != -1) {
            names.remove(idx);
            values.remove(idx);
            index.remove(idx);
        }
        return this;
    }
    public boolean contains(String name) { return indexOfName(name) != -1; }
    public JsonObject merge(JsonObject other) {
        if (other == null) throw new NullPointerException("object is null");
        for (JsonMember member : other) set(member.getName(), member.getValue());
        return this;
    }
    public JsonValue get(String name) {
        int index = indexOfName(name);
        return index == -1 ? null : values.get(index);
    }
    public int getInt(String name, int defaultValue) { JsonValue value = get(name); return value == null ? defaultValue : value.asInt(); }
    public long getLong(String name, long defaultValue) { JsonValue value = get(name); return value == null ? defaultValue : value.asLong(); }
    public float getFloat(String name, float defaultValue) { JsonValue value = get(name); return value == null ? defaultValue : value.asFloat(); }
    public double getDouble(String name, double defaultValue) { JsonValue value = get(name); return value == null ? defaultValue : value.asDouble(); }
    public boolean getBoolean(String name, boolean defaultValue) { JsonValue value = get(name); return value == null ? defaultValue : value.asBoolean(); }
    public String getString(String name, String defaultValue) { JsonValue value = get(name); return value == null ? defaultValue : value.asString(); }
    public int size() { return names.size(); }
    public boolean isEmpty() { return names.isEmpty(); }
    public List<String> names() { return Collections.unmodifiableList(names); }
    public Iterator<JsonMember> iterator() { return new JsonObjectMemberIterator(this, names.iterator(), values.iterator()); }
    void writeJson(JsonWriter writer) throws IOException {
        writer.beginObject();
        for (int idx = 0; idx < names.size(); idx++) {
            if (idx > 0) writer.writeSeparator();
            writer.writeMemberName(names.get(idx));
            values.get(idx).writeJson(writer);
        }
        writer.endObject();
    }
    public boolean isObject() { return true; }
    public JsonObject asObject() { return this; }
    public int hashCode() {
        int result = 1;
        for (int idx = 0; idx < names.size(); idx++) {
            result += names.get(idx).hashCode() ^ values.get(idx).hashCode();
        }
        return result;
    }
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof JsonObject)) return false;
        JsonObject other = (JsonObject) obj;
        return names.equals(other.names) && values.equals(other.values);
    }
    int indexOfName(String name) {
        int idx = index.get(name);
        if (idx != -1 && idx < names.size() && names.get(idx).equals(name)) return idx;
        return names.indexOf(name);
    }
    private synchronized void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
        in.defaultReadObject();
        this.index = new JsonNameIndex();
        rebuildIndex();
    }
    void rebuildIndex() {
        this.index = new JsonNameIndex();
        if (index != null) {
            for (int i = 0; i < names.size(); i++) index.put(names.get(i), i);
        }
    }
}

