package gq.yozakura.k.vendor.skidonion.sWdSl;

import java.util.Iterator;

class JsonObjectMemberIterator implements Iterator<JsonMember> {
    final Iterator<String> names;
    final Iterator<JsonValue> values;
    final JsonObject object;

    JsonObjectMemberIterator(JsonObject object, Iterator<String> names, Iterator<JsonValue> values) {
        this.object = object;
        this.names = names;
        this.values = values;
    }

    public boolean hasNext() { return names.hasNext(); }
    public JsonMember nextMember() { return new JsonMember(names.next(), values.next()); }
    public void remove() { names.remove(); values.remove(); object.rebuildIndex(); }
    public JsonMember next() { return nextMember(); }
}

