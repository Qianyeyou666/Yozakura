package gq.yozakura.auth.vendor.skidonion.sWdSl;

import java.io.Writer;

public class PrettyPrintConfig extends JsonWriterConfig {
    private final char[] indentation;

    protected PrettyPrintConfig(char[] chars) { this.indentation = chars; }
    public static PrettyPrintConfig singleLine() { return new PrettyPrintConfig(null); }
    public static PrettyPrintConfig spaces(int spaces) {
        if (spaces < 0) throw new IllegalArgumentException("number is negative");
        char[] chars = new char[spaces];
        java.util.Arrays.fill(chars, ' ');
        return new PrettyPrintConfig(chars);
    }
    public static PrettyPrintConfig twoSpaces() { return spaces(2); }
    protected JsonWriter createWriter(Writer writer) { return new PrettyJsonWriter(writer, indentation, null); }
}

