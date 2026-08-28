package gq.yozakura.k.vendor.skidonion.sWdSl;

import java.io.IOException;
import java.io.Writer;

class BufferedCharWriter extends Writer {
    private final Writer writer;
    private final char[] buffer;
    private int size = 0;

    BufferedCharWriter(Writer writer) { this(writer, 16); }
    BufferedCharWriter(Writer writer, int size) {
        this.writer = writer;
        this.buffer = new char[size];
    }

    public void write(int value) throws IOException {
        if (size == buffer.length) flush();
        buffer[size++] = (char) value;
    }
    public void write(char[] data, int off, int len) throws IOException {
        if (len >= buffer.length) {
            flush();
            writer.write(data, off, len);
            return;
        }
        if (size + len > buffer.length) flush();
        System.arraycopy(data, off, buffer, size, len);
        size += len;
    }
    public void write(String text, int off, int len) throws IOException {
        if (len >= buffer.length) {
            flush();
            writer.write(text, off, len);
            return;
        }
        if (size + len > buffer.length) flush();
        text.getChars(off, off + len, buffer, size);
        size += len;
    }
    public void flush() throws IOException {
        if (size > 0) {
            writer.write(buffer, 0, size);
            size = 0;
        }
        writer.flush();
    }
    public void close() throws IOException {
        flush();
        writer.close();
    }
}

