package gq.yozakura.k.vendor.skidonion.sWdSl;

import java.io.IOException;
import java.io.Writer;

class PrettyJsonWriter extends JsonWriter {
    private final char[] indentation;
    private int level;

    PrettyJsonWriter(Writer writer, char[] indentation, Object ignored) {
        super(writer);
        this.indentation = indentation;
    }

    protected void beginArray() throws IOException {
        level++;
        out.write('[');
        writeNewLineAndIndent();
    }

    protected void endArray() throws IOException {
        level--;
        writeNewLineAndIndent();
        out.write(']');
    }

    protected void beginObject() throws IOException {
        level++;
        out.write('{');
        writeNewLineAndIndent();
    }

    protected void endObject() throws IOException {
        level--;
        writeNewLineAndIndent();
        out.write('}');
    }

    protected void writeSeparator() throws IOException {
        out.write(',');
        if (!writeNewLineAndIndent()) out.write(' ');
    }

    protected void writeMemberName(String name) throws IOException {
        writeString(name);
        out.write(':');
        out.write(' ');
    }

    private boolean writeNewLineAndIndent() throws IOException {
        if (indentation == null) return false;
        out.write('\n');
        for (int i = 0; i < level; i++) out.write(indentation);
        return true;
    }
}

