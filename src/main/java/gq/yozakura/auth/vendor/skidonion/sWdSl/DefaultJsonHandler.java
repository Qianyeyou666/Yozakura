package gq.yozakura.auth.vendor.skidonion.sWdSl;

class DefaultJsonHandler extends JsonHandler<JsonArray, JsonObject> {
    protected JsonValue value;

    public JsonArray startArray() { return new JsonArray(); }
    public JsonObject startObject() { return new JsonObject(); }
    public void endNull() { value = JsonValue.NULL; }
    public void endBoolean(boolean input) { value = input ? JsonValue.TRUE : JsonValue.FALSE; }
    public void endString(String input) { value = new JsonString(input); }
    public void endNumber(String input) { value = new JsonNumber(input); }
    public void endArray(JsonArray array) { value = array; }
    public void endObject(JsonObject object) { value = object; }
    public void addArrayValue(JsonArray array) { array.add(value); }
    public void addMember(JsonObject object, String memberName) { object.add(memberName, value); }
    JsonValue parsedValue() { return value; }
    public void endArrayValue(JsonArray array) { endArray(array); }
    public void endObjectValue(JsonObject object) { endObject(object); }
}

