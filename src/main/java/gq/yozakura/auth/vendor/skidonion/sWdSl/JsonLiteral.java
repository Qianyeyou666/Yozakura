package gq.yozakura.auth.vendor.skidonion.sWdSl;

import java.io.IOException;

class JsonLiteral extends JsonValue {
    private final String value;
    private final boolean nullLiteral;
    private final boolean trueLiteral;
    private final boolean falseLiteral;

    JsonLiteral(String value) {
        this.value = value;
        this.nullLiteral = "null".equals(value);
        this.trueLiteral = "true".equals(value);
        this.falseLiteral = "false".equals(value);
    }

    void writeJson(JsonWriter writer) throws IOException { writer.writeRaw(value); }
    public String toString() { return value; }
    public int hashCode() { return value.hashCode(); }
    public boolean isNull() { return nullLiteral; }
    public boolean isTrue() { return trueLiteral; }
    public boolean isFalse() { return falseLiteral; }
    public boolean isBoolean() { return trueLiteral || falseLiteral; }
    public boolean asBoolean() {
        if (trueLiteral) return true;
        if (falseLiteral) return false;
        return super.asBoolean();
    }
    public boolean equals(Object obj) {
        return obj instanceof JsonLiteral && value.equals(((JsonLiteral) obj).value);
    }
}

