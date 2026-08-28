package gq.yozakura.k.vendor.skidonion.sWdSl;

import java.io.IOException;
import java.io.Writer;

class JsonWriter {
    protected final Writer out;

    JsonWriter(Writer writer) {
        if (writer == null) throw new NullPointerException("writer is null");
        this.out = writer;
    }

    protected void writeRaw(String raw) throws IOException { out.write(raw); }
    protected void writeMemberName(String name) throws IOException { writeString(name); out.write(':'); }
    protected void writeString(String value) throws IOException { writeQuotedString(value); }
    protected void beginArray() throws IOException { out.write('['); }
    protected void endArray() throws IOException { out.write(']'); }
    protected void beginObject() throws IOException { out.write('{'); }
    protected void endObject() throws IOException { out.write('}'); }
    protected void writeSeparator() throws IOException { out.write(','); }
    protected void writeFalse() throws IOException { out.write("false"); }
    protected void writeTrue() throws IOException { out.write("true"); }
    protected void writeQuotedString(String value) throws IOException {
        out.write('"');
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            switch (ch) {
                case '"': out.write("\\\""); break;
                case '\\': out.write("\\\\"); break;
                case '\b': out.write("\\b"); break;
                case '\f': out.write("\\f"); break;
                case '\n': out.write("\\n"); break;
                case '\r': out.write("\\r"); break;
                case '\t': out.write("\\t"); break;
                default:
                    char[] escaped = escape(ch);
                    if (escaped != null) {
                        out.write(escaped);
                    } else {
                        out.write(ch);
                    }
            }
        }
        out.write('"');
    }

    private static char[] escape(char ch) {
        if (ch > '\\' && (ch < 0x2028 || ch > 0x2029)) return null;
        if (ch == '\\') return "\\\\".toCharArray();
        if (ch == '"') return "\\\"".toCharArray();
        if (ch == '\n') return "\\n".toCharArray();
        if (ch == '\r') return "\\r".toCharArray();
        if (ch == '\t') return "\\t".toCharArray();
        if (ch == 0x2028) return "\\u2028".toCharArray();
        if (ch == 0x2029) return "\\u2029".toCharArray();
        if (ch > 0x1f) return null;
        char[] result = new char[] { '\\', 'u', '0', '0', '0', '0' };
        char[] hex = "0123456789abcdef".toCharArray();
        result[4] = hex[(ch >> 4) & 0xf];
        result[5] = hex[ch & 0xf];
        return result;
    }
}

