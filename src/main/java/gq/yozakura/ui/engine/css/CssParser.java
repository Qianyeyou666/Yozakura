package gq.yozakura.ui.engine.css;

import gq.yozakura.ui.engine.dom.SourcePosition;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * CSS 解析器：将 CSS 文本解析为 {@link Stylesheet}。
 *
 * <p>支持规则、多选择器、声明、important、注释、自定义变量声明（--name）、
 * 括号嵌套的值（rgba/var/url/linear-gradient）、at-rule 跳过。
 *
 * <p>错误携带资源路径（可选）、行号与列号。不静默降级。
 */
public final class CssParser {

    private String input;
    private int pos;
    private int line;
    private int col;
    private String resourcePath;

    /**
     * 解析 CSS 文本，资源路径视为未知（null）。
     * 异常的 {@link CssParseException#resourcePath()} 返回 null。
     */
    public Stylesheet parse(String css) {
        return parse(css, null);
    }

    /**
     * 解析 CSS 文本，并携带资源路径用于错误定位。
     * 异常的 {@link CssParseException#resourcePath()} 返回传入的 resourcePath。
     *
     * @param css CSS 文本
     * @param resourcePath 资源路径标识（可为 null，表示未知来源）
     * @return 解析后的 Stylesheet
     * @throws CssParseException 解析失败，异常携带 resourcePath/line/column
     */
    public Stylesheet parse(String css, String resourcePath) {
        if (css == null) {
            return new Stylesheet(new ArrayList<Rule>(), 0);
        }
        this.input = css;
        this.pos = 0;
        this.line = 1;
        this.col = 1;
        this.resourcePath = resourcePath;

        List<Rule> rules = new ArrayList<Rule>();
        int atRuleCount = 0;
        int sourceOrder = 0;

        while (pos < input.length()) {
            skipWhitespaceAndComments();
            if (pos >= input.length()) break;

            char c = input.charAt(pos);
            if (c == '@') {
                atRuleCount += skipAtRule();
                continue;
            }
            if (c == '}') {
                throw error("unexpected '}' without matching rule", line, col);
            }

            int ruleLine = line;
            int ruleCol = col;
            List<Selector> selectors = parseSelectorList();
            skipWhitespaceAndComments();
            expect('{');
            skipWhitespaceAndComments();
            List<Declaration> declarations = parseDeclarations();
            expect('}');

            rules.add(new Rule(selectors, declarations, sourceOrder++,
                    SourcePosition.of(ruleLine, ruleCol)));
        }

        return new Stylesheet(rules, atRuleCount);
    }

    /**
     * 解析 HTML 元素 inline style 属性中的声明列表。
     *
     * <p>输入形如 {@code "color: red; background: blue"}（无选择器、无大括号）。
     * 内联声明参与 {@link StyleResolver} 级联时按 (important, inlineTier, sourceOrderInInline) 排序，
     * 因此这里保留输入顺序，由调用方按 sourceOrder 区分先后。
     *
     * <p>注意：内联声明嵌在 HTML 资源中而非独立 CSS 资源，资源路径上下文重置为 null，
     * 避免上一次 {@link #parse(String, String)} 残留的 resourcePath 误导错误定位。
     *
     * @param inlineStyle 内联 style 文本；null 或空白返回空列表
     * @return 解析后的声明列表（保留输入顺序）；输入为空返回空列表
     */
    public List<Declaration> parseInlineDeclarations(String inlineStyle) {
        if (inlineStyle == null || inlineStyle.trim().isEmpty()) {
            return Collections.emptyList();
        }
        // 复用 parseDeclarations：将 inline 文本包装为 { ... } 形式，
        // 让既有声明解析逻辑（含 !important、括号嵌套值、注释跳过）统一生效。
        // 内联 style 嵌在 HTML 中，资源路径不适用于声明级错误；重置为 null。
        this.input = "{ " + inlineStyle + " }";
        this.pos = 0;
        this.line = 1;
        this.col = 1;
        this.resourcePath = null;
        skipWhitespaceAndComments();
        expect('{');
        skipWhitespaceAndComments();
        List<Declaration> declarations = parseDeclarations();
        // 输入末尾应有 '}'；expect('}') 在 parseDeclarations 看到 '}' 返回后由调用方负责。
        // 这里再消费一个 '}'；若不存在则视作解析错误。
        skipWhitespaceAndComments();
        expect('}');
        return declarations;
    }

    private List<Selector> parseSelectorList() {
        List<Selector> selectors = new ArrayList<Selector>();
        StringBuilder sb = new StringBuilder();
        while (pos < input.length()) {
            char c = input.charAt(pos);
            if (c == '{') break;
            if (c == ',') {
                String text = sb.toString().trim();
                if (!text.isEmpty()) {
                    selectors.add(new Selector(text));
                }
                sb.setLength(0);
                advance();
                skipInlineWhitespace();
                continue;
            }
            if (c == '/' && pos + 1 < input.length() && input.charAt(pos + 1) == '*') {
                skipComment();
                continue;
            }
            sb.append(c);
            advance();
        }
        String text = sb.toString().trim();
        if (!text.isEmpty()) {
            selectors.add(new Selector(text));
        }
        if (selectors.isEmpty()) {
            throw error("expected selector before '{'", line, col);
        }
        return selectors;
    }

    private List<Declaration> parseDeclarations() {
        List<Declaration> declarations = new ArrayList<Declaration>();
        while (pos < input.length()) {
            skipWhitespaceAndComments();
            if (pos >= input.length()) {
                throw error("unterminated rule, expected '}'", line, col);
            }
            if (input.charAt(pos) == '}') {
                return declarations;
            }
            if (input.charAt(pos) == ';') {
                advance();
                continue;
            }
            declarations.add(parseDeclaration());
            skipInlineWhitespace();
            if (pos < input.length() && input.charAt(pos) == ';') {
                advance();
            }
        }
        throw error("unterminated rule, expected '}'", line, col);
    }

    private Declaration parseDeclaration() {
        String name = parsePropertyName();
        if (name.isEmpty()) {
            throw error("invalid property name", line, col);
        }
        skipInlineWhitespace();
        expect(':');
        skipInlineWhitespace();
        String rawValue = parseValue();
        if (rawValue.trim().isEmpty()) {
            throw error("missing value for property '" + name + "'", line, col);
        }
        // 检查 !important
        skipInlineWhitespace();
        boolean important = false;
        if (pos < input.length() && input.charAt(pos) == '!') {
            String importantKeyword = tryReadImportant();
            if (importantKeyword != null) {
                important = true;
            }
        }
        return new Declaration(new Property(name), new CssValue(rawValue), important);
    }

    private String parsePropertyName() {
        int start = pos;
        if (pos < input.length() && isPropertyStart(input.charAt(pos))) {
            advance();
            while (pos < input.length() && isPropertyPart(input.charAt(pos))) {
                advance();
            }
            return input.substring(start, pos);
        }
        return "";
    }

    private String parseValue() {
        StringBuilder sb = new StringBuilder();
        while (pos < input.length()) {
            char c = input.charAt(pos);
            if (c == ';' || c == '}') break;
            if (c == '!' ) break;
            if (c == '/' && pos + 1 < input.length() && input.charAt(pos + 1) == '*') {
                skipComment();
                continue;
            }
            if (c == '(') {
                sb.append(readBalancedParens());
                continue;
            }
            if (c == '"' || c == '\'') {
                sb.append(readQuotedString(c));
                continue;
            }
            sb.append(c);
            advance();
        }
        return sb.toString().trim();
    }

    private String readBalancedParens() {
        StringBuilder sb = new StringBuilder();
        int depth = 0;
        while (pos < input.length()) {
            char c = input.charAt(pos);
            if (c == '(') depth++;
            else if (c == ')') {
                depth--;
                if (depth == 0) {
                    sb.append(c);
                    advance();
                    return sb.toString();
                }
            }
            sb.append(c);
            advance();
        }
        throw error("unterminated parentheses in value", line, col);
    }

    private String readQuotedString(char quote) {
        StringBuilder sb = new StringBuilder();
        sb.append(quote);
        advance();
        while (pos < input.length() && input.charAt(pos) != quote) {
            sb.append(input.charAt(pos));
            advance();
        }
        if (pos >= input.length()) {
            throw error("unterminated string in value", line, col);
        }
        sb.append(quote);
        advance();
        return sb.toString();
    }

    private String tryReadImportant() {
        int savePos = pos;
        int saveLine = line;
        int saveCol = col;
        advance(); // consume '!'
        StringBuilder sb = new StringBuilder();
        while (pos < input.length()) {
            char c = input.charAt(pos);
            if (!Character.isLetter(c)) break;
            sb.append(c);
            advance();
        }
        String keyword = sb.toString().toLowerCase();
        if (keyword.equals("important")) {
            return keyword;
        }
        // 回退
        pos = savePos;
        line = saveLine;
        col = saveCol;
        return null;
    }

    private int skipAtRule() {
        // 当前位于 '@'
        advance();
        // 读 at-keyword
        while (pos < input.length() && isAtKeywordPart(input.charAt(pos))) {
            advance();
        }
        // 跳过 prelude（可能含括号）直到 '{' 或 ';'
        int parenDepth = 0;
        while (pos < input.length()) {
            char c = input.charAt(pos);
            if (c == '/' && pos + 1 < input.length() && input.charAt(pos + 1) == '*') {
                skipComment();
                continue;
            }
            if (c == '(') {
                parenDepth++;
                advance();
                continue;
            }
            if (c == ')') {
                if (parenDepth > 0) parenDepth--;
                advance();
                continue;
            }
            if (parenDepth == 0) {
                if (c == ';') {
                    advance();
                    return 1;
                }
                if (c == '{') {
                    break;
                }
            }
            advance();
        }
        if (pos >= input.length()) return 1;
        // 当前位于 '{'，跳过整个 block
        advance();
        int braceDepth = 1;
        while (pos < input.length() && braceDepth > 0) {
            char c = input.charAt(pos);
            if (c == '/' && pos + 1 < input.length() && input.charAt(pos + 1) == '*') {
                skipComment();
                continue;
            }
            if (c == '{') braceDepth++;
            else if (c == '}') braceDepth--;
            advance();
        }
        return 1;
    }

    private void skipComment() {
        // 当前位于 "/*"
        advance(); advance();
        while (pos < input.length()) {
            if (input.charAt(pos) == '*' && pos + 1 < input.length() && input.charAt(pos + 1) == '/') {
                advance(); advance();
                return;
            }
            advance();
        }
    }

    private void skipWhitespaceAndComments() {
        while (pos < input.length()) {
            char c = input.charAt(pos);
            if (c == ' ' || c == '\t' || c == '\n' || c == '\r') {
                advance();
            } else if (c == '/' && pos + 1 < input.length() && input.charAt(pos + 1) == '*') {
                skipComment();
            } else {
                break;
            }
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

    private void expect(char c) {
        if (pos >= input.length() || input.charAt(pos) != c) {
            throw error("expected '" + c + "'", line, col);
        }
        advance();
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

    private static boolean isPropertyStart(char c) {
        return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || c == '-' || c == '_';
    }

    private static boolean isPropertyPart(char c) {
        return isPropertyStart(c) || (c >= '0' && c <= '9');
    }

    private static boolean isAtKeywordPart(char c) {
        return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || c == '-';
    }

    /**
     * 构造异常时携带当前 resourcePath。
     * 集中所有错误抛出点，避免每处手工传 resourcePath。
     */
    private CssParseException error(String message, int line, int column) {
        return new CssParseException(message, resourcePath, line, column);
    }
}
