package gq.yozakura.k.vendor.skidonion.sWdSl;

import java.util.Iterator;

class JsonArrayIterator implements Iterator<JsonValue> {
    final Iterator<JsonValue> values;
    final JsonArray array;

    JsonArrayIterator(JsonArray array, Iterator<JsonValue> iterator) {
        this.array = array;
        this.values = iterator;
    }

    public boolean hasNext() { return values.hasNext(); }
    public JsonValue nextValue() { return values.next(); }
    public void remove() { values.remove(); }
    public JsonValue next() { return nextValue(); }
}

