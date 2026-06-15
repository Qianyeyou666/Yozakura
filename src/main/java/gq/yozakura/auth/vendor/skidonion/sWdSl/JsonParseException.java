package gq.yozakura.auth.vendor.skidonion.sWdSl;

public class JsonParseException extends RuntimeException {
    private final JsonLocation location;

    JsonParseException(String message, JsonLocation location) {
        super(message + " at " + location);
        this.location = location;
    }

    public JsonLocation getLocation() { return location; }
    public int getOffset() { return location.getOffset(); }
    public int getLine() { return location.getLine(); }
    public int getColumn() { return location.getColumn(); }
}

