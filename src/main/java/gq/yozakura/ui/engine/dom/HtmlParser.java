package gq.yozakura.ui.engine.dom;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * HTML 子集解析器：将 HTML 文本解析为 {@link ElementNode} 树。
 *
 * <p>支持的标签：任意合法标签名（字母开头），含 ui、div、span、p、button、input、img、
 * label、template、aside、main 等语义标签。
 *
 * <p>void 标签（img、br、hr、input、meta、link）无需闭合标签。
 *
 * <p>支持属性：带引号（双引号/单引号）、不带引号、布尔属性；
 * id、class、style、data-*、type、src 等任意属性名。
 *
 * <p>实体解码：命名实体（amp、lt、gt、quot、apos、nbsp）与数字实体（&#65; &#x42;）。
 *
 * <p>错误携带资源路径（可选）、行号与列号。不静默降级。
 */
public final class HtmlParser {

    private static final Set<String> VOID_TAGS = new HashSet<String>(Arrays.asList(
            "img", "br", "hr", "input", "meta", "link", "area", "base", "col", "embed", "source", "track", "wbr"
    ));

    private String input;
    private int pos;
    private int line;
    private int col;
    private String resourcePath;

    /**
     * 解析 HTML 文本，资源路径视为未知（null）。
     * 异常的 {@link HtmlParseException#resourcePath()} 返回 null。
     *
     * @param html HTML 文本
     * @return 根元素节点
     * @throws HtmlParseException 解析失败
     */
    public DomNode parse(String html) {
        return parse(html, null);
    }

    /**
     * 解析 HTML 文本，并携带资源路径用于错误定位。
     * 异常的 {@link HtmlParseException#resourcePath()} 返回传入的 resourcePath。
     *
     * @param html HTML 文本
     * @param resourcePath 资源路径标识（可为 null，表示未知来源）
     * @return 根元素节点
     * @throws HtmlParseException 解析失败，异常携带 resourcePath/line/column
     */
    public DomNode parse(String html, String resourcePath) {
        if (html == null) {
            throw error("input must not be null", 1, 1);
        }
        this.input = html;
        this.pos = 0;
        this.line = 1;
        this.col = 1;
        this.resourcePath = resourcePath;

        skipWhitespace();
        if (pos >= input.length()) {
            throw error("empty input", line, col);
        }

        // 根前只允许空白；遇到非 '<' 视为错误
        if (input.charAt(pos) != '<') {
            throw error("expected '<' at root, text before root element", line, col);
        }

        // 跳过根前可能的注释/doctype
        skipProlog();

        ElementNode root = parseElement();
        skipWhitespaceAndComments();
        if (pos < input.length()) {
            throw error("multiple root elements are not allowed", line, col);
        }
        return root;
    }

    private void skipProlog() {
        while (pos < input.length()) {
            skipWhitespace();
            if (pos >= input.length()) break;
            char c = input.charAt(pos);
            if (c == '<' && startsWith("<!--")) {
                skipComment();
            } else if (c == '<' && startsWith("<!")) {
                // doctype 或其他声明，跳到 '>'
                int startLine = line, startCol = col;
                while (pos < input.length() && input.charAt(pos) != '>') {
                    advance();
                }
                if (pos >= input.length()) {
                    throw error("unterminated declaration", startLine, startCol);
                }
                advance(); // consume '>'
            } else {
                break;
            }
        }
    }

    private ElementNode parseElement() {
        int startLine = line;
        int startCol = col;
        expect('<');
        // 标签名
        String tag = parseTagName();
        if (tag.isEmpty()) {
            throw error("invalid tag name", startLine, startCol);
        }

        AttributeMap.AttributeMapBuilder attrBuilder = AttributeMap.builder();
        String id = null;
        List<String> classes = new ArrayList<String>();
        String inlineStyle = null;

        // 属性
        while (true) {
            skipInlineWhitespace();
            if (pos >= input.length()) {
                throw error("unterminated start tag '" + tag + "'", line, col);
            }
            char c = input.charAt(pos);
            if (c == '>') {
                advance();
                break;
            }
            if (c == '/' && pos + 1 < input.length() && input.charAt(pos + 1) == '>') {
                advance(); advance();
                // 自闭合
                ElementNode selfClosed = ElementNode.create(tag, SourcePosition.of(startLine, startCol));
                applyAttributes(selfClosed, attrBuilder.build(), id, classes, inlineStyle);
                return selfClosed;
            }
            // 属性
            AttrParsed ap = parseAttribute();
            String name = ap.name;
            String value = ap.value;
            attrBuilder.set(name, value);
            if (name.equals("id")) {
                id = value;
            } else if (name.equals("class")) {
                for (String cls : value.split("\\s+")) {
                    if (!cls.isEmpty()) classes.add(cls);
                }
            } else if (name.equals("style")) {
                inlineStyle = value;
            }
        }

        ElementNode element = ElementNode.create(tag, SourcePosition.of(startLine, startCol));
        applyAttributes(element, attrBuilder.build(), id, classes, inlineStyle);

        if (VOID_TAGS.contains(tag)) {
            return element;
        }

        // 子节点
        while (true) {
            if (pos >= input.length()) {
                throw error("unclosed tag '" + tag + "', expected </" + tag + ">", startLine, startCol);
            }
            if (startsWith("</")) {
                String closeTag = parseClosingTag();
                if (!closeTag.equals(tag)) {
                    throw error(
                            "mismatched closing tag: expected </" + tag + "> but found </" + closeTag + ">",
                            line, col);
                }
                return element;
            }
            if (startsWith("<!--")) {
                skipComment();
                continue;
            }
            if (input.charAt(pos) == '<') {
                ElementNode child = parseElement();
                element.appendChild(child);
            } else {
                String text = parseText();
                if (!text.isEmpty()) {
                    element.appendChild(TextNode.of(text, SourcePosition.of(line, col)));
                }
            }
        }
    }

    private void applyAttributes(ElementNode element, AttributeMap attrs,
                                 String id, List<String> classes, String inlineStyle) {
        element.withAttributes(attrs);
        if (id != null) element.withId(id);
        if (!classes.isEmpty()) element.withClasses(classes);
        if (inlineStyle != null) element.withInlineStyle(inlineStyle);
    }

    private String parseClosingTag() {
        expect('<');
        expect('/');
        String tag = parseTagName();
        skipInlineWhitespace();
        expect('>');
        return tag;
    }

    private static class AttrParsed {
        final String name;
        final String value;
        AttrParsed(String name, String value) {
            this.name = name;
            this.value = value;
        }
    }

    private AttrParsed parseAttribute() {
        String name = parseAttrName();
        if (name.isEmpty()) {
            throw error("invalid attribute name", line, col);
        }
        skipInlineWhitespace();
        if (pos >= input.length()) {
            throw error("unterminated attribute", line, col);
        }
        if (input.charAt(pos) != '=') {
            // 布尔属性
            return new AttrParsed(name, "");
        }
        advance(); // consume '='
        skipInlineWhitespace();
        if (pos >= input.length()) {
            throw error("unterminated attribute value", line, col);
        }
        char c = input.charAt(pos);
        if (c == '"' || c == '\'') {
            return new AttrParsed(name, parseQuotedValue(c));
        }
        // 不带引号的值
        return new AttrParsed(name, parseUnquotedValue());
    }

    private String parseQuotedValue(char quote) {
        advance(); // consume quote
        StringBuilder sb = new StringBuilder();
        while (pos < input.length() && input.charAt(pos) != quote) {
            if (input.charAt(pos) == '&') {
                sb.append(parseEntity());
            } else {
                sb.append(input.charAt(pos));
                advance();
            }
        }
        if (pos >= input.length()) {
            throw error("unterminated quoted attribute value", line, col);
        }
        advance(); // consume closing quote
        return sb.toString();
    }

    private String parseUnquotedValue() {
        StringBuilder sb = new StringBuilder();
        while (pos < input.length()) {
            char c = input.charAt(pos);
            if (c == ' ' || c == '\t' || c == '\n' || c == '\r' || c == '>' || c == '/') {
                break;
            }
            if (c == '&') {
                sb.append(parseEntity());
            } else {
                sb.append(c);
                advance();
            }
        }
        return sb.toString();
    }

    private String parseText() {
        StringBuilder sb = new StringBuilder();
        while (pos < input.length()) {
            char c = input.charAt(pos);
            if (c == '<') break;
            if (c == '&') {
                sb.append(parseEntity());
            } else {
                sb.append(c);
                advance();
            }
        }
        return sb.toString();
    }

    private String parseEntity() {
        // 当前位于 '&'
        int startLine = line, startCol = col;
        expect('&');
        int entityStart = pos;
        boolean numeric = false;
        boolean hex = false;
        if (pos < input.length() && input.charAt(pos) == '#') {
            numeric = true;
            advance();
            if (pos < input.length() && (input.charAt(pos) == 'x' || input.charAt(pos) == 'X')) {
                hex = true;
                advance();
            }
        }
        StringBuilder body = new StringBuilder();
        while (pos < input.length() && input.charAt(pos) != ';') {
            char c = input.charAt(pos);
            if (c == '<' || c == '&' || c == '\n') {
                // 非法实体终止
                return "&";
            }
            body.append(c);
            advance();
        }
        if (pos >= input.length() || input.charAt(pos) != ';') {
            return "&";
        }
        advance(); // consume ';'
        String entity = body.toString();
        if (numeric) {
            try {
                int code = Integer.parseInt(entity, hex ? 16 : 10);
                if (code < 0 || code > 0x10FFFF) {
                    return "&";
                }
                return new String(Character.toChars(code));
            } catch (NumberFormatException e) {
                return "&";
            }
        }
        // 命名实体
        return decodeNamedEntity(entity, startLine, startCol);
    }

    private String decodeNamedEntity(String name, int line, int col) {
        if (name.equals("amp")) return "&";
        if (name.equals("lt")) return "<";
        if (name.equals("gt")) return ">";
        if (name.equals("quot")) return "\"";
        if (name.equals("apos")) return "'";
        if (name.equals("nbsp")) return "\u00a0";
        // 未知实体，原样返回（不静默降级为空）
        return "&" + name + ";";
    }

    private String parseTagName() {
        int start = pos;
        if (pos < input.length() && isTagNameStart(input.charAt(pos))) {
            advance();
            while (pos < input.length() && isTagNamePart(input.charAt(pos))) {
                advance();
            }
            return input.substring(start, pos);
        }
        return "";
    }

    private String parseAttrName() {
        int start = pos;
        if (pos < input.length() && isAttrNameStart(input.charAt(pos))) {
            advance();
            while (pos < input.length() && isAttrNamePart(input.charAt(pos))) {
                advance();
            }
            return input.substring(start, pos);
        }
        return "";
    }

    private static boolean isTagNameStart(char c) {
        return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z');
    }

    private static boolean isTagNamePart(char c) {
        return isTagNameStart(c) || (c >= '0' && c <= '9') || c == '-';
    }

    private static boolean isAttrNameStart(char c) {
        return isTagNameStart(c) || c == '_' || c == ':';
    }

    private static boolean isAttrNamePart(char c) {
        return isAttrNameStart(c) || (c >= '0' && c <= '9') || c == '-' || c == '.';
    }

    private void skipComment() {
        // 当前位于 '<!--'
        for (int i = 0; i < 4; i++) advance();
        while (pos < input.length()) {
            if (startsWith("-->")) {
                advance(); advance(); advance();
                return;
            }
            advance();
        }
    }

    private void skipInlineWhitespace() {
        while (pos < input.length()) {
            char c = input.charAt(pos);
            if (c == ' ' || c == '\t') {
                advance();
            } else {
                break;
            }
        }
    }

    private void skipWhitespace() {
        while (pos < input.length()) {
            char c = input.charAt(pos);
            if (c == ' ' || c == '\t' || c == '\n' || c == '\r') {
                advance();
            } else {
                break;
            }
        }
    }

    private void skipWhitespaceAndComments() {
        while (pos < input.length()) {
            char c = input.charAt(pos);
            if (c == ' ' || c == '\t' || c == '\n' || c == '\r') {
                advance();
            } else if (startsWith("<!--")) {
                skipComment();
            } else {
                break;
            }
        }
    }

    private void expect(char c) {
        if (pos >= input.length() || input.charAt(pos) != c) {
            throw error("expected '" + c + "'", line, col);
        }
        advance();
    }

    private boolean startsWith(String s) {
        if (pos + s.length() > input.length()) return false;
        for (int i = 0; i < s.length(); i++) {
            if (input.charAt(pos + i) != s.charAt(i)) return false;
        }
        return true;
    }

    private void advance() {
        if (pos >= input.length()) return;
        char c = input.charAt(pos);
        pos++;
        if (c == '\n') {
            line++;
            col = 1;
        } else {
            col++;
        }
    }

    /**
     * 构造异常时携带当前 resourcePath。
     * 集中所有错误抛出点，避免每处手工传 resourcePath。
     */
    private HtmlParseException error(String message, int line, int column) {
        return new HtmlParseException(message, resourcePath, line, column);
    }
}
