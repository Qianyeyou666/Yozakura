package gq.yozakura.auth.vendor.skidonion.sWdSl;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;

public class JsonParser {
    private final JsonHandler handler;
    private int offset;
    private int line = 1;
    private int column = 1;

    @SuppressWarnings({"unchecked", "rawtypes"})
    public JsonParser(JsonHandler<?, ?> handler) {
        if (handler == null) throw new NullPointerException("handler is null");
        this.handler = (JsonHandler) handler;
        handler.parser = this;
    }

    public void parse(String text) { try { parse(new StringReader(text)); } catch (IOException ex) { throw new RuntimeException(ex); } }

    public void parse(Reader reader) throws IOException { parse(reader, 1024); }

    public void parse(Reader reader, int bufferSize) throws IOException {
        if (reader == null) throw new NullPointerException("reader is null");
        StringBuilder sb = new StringBuilder();
        char[] buf = new char[Math.max(1, bufferSize)];
        int read;
        while ((read = reader.read(buf)) != -1) sb.append(buf, 0, read);
        new Parser(sb.toString(), this, handler).parse();
    }

    JsonLocation location() { return new JsonLocation(offset, line, column); }

    private static final class Parser {
        final String in;
        final JsonParser owner;
        final JsonHandler handler;
        int pos;
        int line = 1;
        int column = 1;

        @SuppressWarnings("rawtypes")
        Parser(String in, JsonParser owner, JsonHandler handler) {
            this.in = in;
            this.owner = owner;
            this.handler = handler;
            sync();
        }

        void parse() {
            skip();
            value();
            skip();
            if (pos != in.length()) error("Unexpected character");
        }
        @SuppressWarnings({"unchecked", "rawtypes"})
        void value() {
            skip();
            if (pos >= in.length()) error("Unexpected end of input");
            char ch = peek();
            if (ch == '"') {
                handler.endString(string());
            } else if (ch == '{') {
                object();
            } else if (ch == '[') {
                array();
            } else if (match("true")) {
                handler.endBoolean(true);
            } else if (match("false")) {
                handler.endBoolean(false);
            } else if (match("null")) {
                handler.endNull();
            } else {
                handler.endNumber(number());
            }
        }
        @SuppressWarnings({"unchecked", "rawtypes"})
        void object() {
            Object object = handler.startObject();
            read('{');
            skip();
            if (!tryRead('}')) {
                do {
                    skip();
                    String name = string();
                    skip();
                    read(':');
                    value();
                    handler.addMember(object, name);
                    skip();
                } while (tryRead(','));
                read('}');
            }
            handler.endObject(object);
            handler.endObjectValue(object);
        }
        @SuppressWarnings({"unchecked", "rawtypes"})
        void array() {
            Object array = handler.startArray();
            read('[');
            skip();
            if (!tryRead(']')) {
                do {
                    value();
                    handler.addArrayValue(array);
                    skip();
                } while (tryRead(','));
                read(']');
            }
            handler.endArray(array);
            handler.endArrayValue(array);
        }
        String string() {
            read('"');
            StringBuilder sb = new StringBuilder();
            while (pos < in.length()) {
                char ch = next();
                if (ch == '"') return sb.toString();
                if (ch == '\\') {
                    if (pos >= in.length()) error("Unexpected end of escape");
                    ch = next();
                    switch (ch) {
                        case '"': sb.append('"'); break;
                        case '\\': sb.append('\\'); break;
                        case '/': sb.append('/'); break;
                        case 'b': sb.append('\b'); break;
                        case 'f': sb.append('\f'); break;
                        case 'n': sb.append('\n'); break;
                        case 'r': sb.append('\r'); break;
                        case 't': sb.append('\t'); break;
                        case 'u':
                            if (pos + 4 > in.length()) error("Invalid unicode escape");
                            int codePoint = 0;
                            for (int i = 0; i < 4; i++) {
                                int digit = Character.digit(in.charAt(pos + i), 16);
                                if (digit == -1) error("Invalid unicode escape");
                                codePoint = (codePoint << 4) + digit;
                            }
                            sb.append((char) codePoint);
                            for (int i = 0; i < 4; i++) advance(in.charAt(pos++));
                            break;
                        default: error("Invalid escape");
                    }
                } else {
                    if (ch < 0x20) error("Invalid character in string");
                    sb.append(ch);
                }
            }
            error("Unterminated string");
            return null;
        }
        String number() {
            int start = pos;
            if (peek() == '-') next();
            if (pos >= in.length()) error("Unexpected end of number");
            if (peek() == '0') {
                next();
            } else if (peek() >= '1' && peek() <= '9') {
                while (pos < in.length() && Character.isDigit(peek())) next();
            } else {
                error("Expected digit");
            }
            if (pos < in.length() && peek() == '.') {
                next();
                if (pos >= in.length() || !Character.isDigit(peek())) error("Expected digit");
                while (pos < in.length() && Character.isDigit(peek())) next();
            }
            if (pos < in.length() && (peek() == 'e' || peek() == 'E')) {
                next();
                if (pos < in.length() && (peek() == '+' || peek() == '-')) next();
                if (pos >= in.length() || !Character.isDigit(peek())) error("Expected digit");
                while (pos < in.length() && Character.isDigit(peek())) next();
            }
            if (start == pos) error("Expected value");
            return in.substring(start, pos);
        }
        void skip() { while (pos < in.length() && Character.isWhitespace(peek())) next(); }
        boolean match(String keyword) {
            if (in.startsWith(keyword, pos)) {
                for (int i = 0; i < keyword.length(); i++) next();
                return true;
            }
            return false;
        }
        char peek() { return in.charAt(pos); }
        char next() { char ch = in.charAt(pos++); advance(ch); return ch; }
        void advance(char ch) {
            if (ch == '\n') {
                line++;
                column = 1;
            } else {
                column++;
            }
            sync();
        }
        void sync() {
            owner.offset = pos;
            owner.line = line;
            owner.column = column;
        }
        void read(char expected) { if (pos >= in.length() || next() != expected) error("Expected '" + expected + "'"); }
        boolean tryRead(char expected) { if (pos < in.length() && peek() == expected) { next(); return true; } return false; }
        void error(String msg) { throw new JsonParseException(msg, new JsonLocation(pos, line, column)); }
    }
}

