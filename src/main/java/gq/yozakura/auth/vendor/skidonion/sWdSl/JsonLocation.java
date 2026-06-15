package gq.yozakura.auth.vendor.skidonion.sWdSl;

public class JsonLocation {
    final int offset;
    final int line;
    final int column;

    JsonLocation(int offset, int line, int column) {
        this.offset = offset;
        this.line = line;
        this.column = column;
    }

    public int getOffset() { return offset; }
    public int getLine() { return line; }
    public int getColumn() { return column; }

    public String toString() {
        return line + ":" + column;
    }

    public int hashCode() {
        int result = offset;
        result = 31 * result + line;
        result = 31 * result + column;
        return result;
    }

    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof JsonLocation)) return false;
        JsonLocation other = (JsonLocation) obj;
        return offset == other.offset && line == other.line && column == other.column;
    }
}

