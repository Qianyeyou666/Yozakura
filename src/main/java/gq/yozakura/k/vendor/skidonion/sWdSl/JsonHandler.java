package gq.yozakura.k.vendor.skidonion.sWdSl;

public abstract class JsonHandler<A_, O_> {
    JsonParser parser;

    protected JsonLocation location() { return parser == null ? new JsonLocation(0, 1, 1) : parser.location(); }
    public void startNull() {}
    public void endNull() {}
    public void startBoolean() {}
    public void endBoolean(boolean value) {}
    public void startString() {}
    public void endString(String value) {}
    public void startNumber() {}
    public void endNumber(String value) {}
    public A_ startArray() { return null; }
    public void endArray(A_ array) {}
    public void endArrayValue(A_ array) {}
    public void addArrayValue(A_ array) {}
    public O_ startObject() { return null; }
    public void endObject(O_ object) {}
    public void endObjectValue(O_ object) {}
    public void startMember(O_ object, String name) {}
    public void endMember(O_ object, String name) {}
    public void addMember(O_ object, String name) {}
}

