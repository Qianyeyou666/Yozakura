package gq.yozakura.k.vendor.skidonion.sWdSl;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;

public final class Json {
    private Json() {}
    public static JsonValue value(int value) { return JsonValue.valueOf(value); }
    public static JsonValue value(long value) { return JsonValue.valueOf(value); }
    public static JsonValue value(float value) { return JsonValue.valueOf(value); }
    public static JsonValue value(double value) { return JsonValue.valueOf(value); }
    public static JsonValue value(String value) { return JsonValue.valueOf(value); }
    public static JsonValue value(boolean value) { return JsonValue.valueOf(value); }
    public static JsonArray array() { return new JsonArray(); }
    public static JsonArray arrayOfInts(int... values) { JsonArray array = new JsonArray(); for (int value : values) array.addInt(value); return array; }
    public static JsonArray arrayOfLongs(long... values) { JsonArray array = new JsonArray(); for (long value : values) array.addLong(value); return array; }
    public static JsonArray arrayOfFloats(float... values) { JsonArray array = new JsonArray(); for (float value : values) array.addFloat(value); return array; }
    public static JsonArray arrayOfDoubles(double... values) { JsonArray array = new JsonArray(); for (double value : values) array.addDouble(value); return array; }
    public static JsonArray arrayOfBooleans(boolean... values) { JsonArray array = new JsonArray(); for (boolean value : values) array.addBoolean(value); return array; }
    public static JsonArray arrayOfStrings(String... values) { JsonArray array = new JsonArray(); for (String value : values) array.addString(value); return array; }
    public static JsonObject object() { return new JsonObject(); }
    public static JsonValue parse(String text) {
        try {
            return parseReader(new StringReader(text));
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }
    public static JsonValue parseReader(Reader reader) throws IOException {
        J handler = new J();
        new JsonParser(handler).parse(reader);
        return handler.parsedValue();
    }
    private static String identity(String value) { return value; }
}

