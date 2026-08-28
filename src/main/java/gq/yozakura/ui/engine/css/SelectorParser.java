package gq.yozakura.ui.engine.css;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * 将 CSS 选择器文本解析为 {@link ParsedSelector}。
 *
 * <p>MVP 支持的语法：
 * <ul>
 *   <li>compound：tag、#id、.class、:pseudo、[attr]、[attr=val] 的任意组合</li>
 *   <li>combinator：空格（后代）与 {@code >}（直接子节点）</li>
 * </ul>
 *
 * <p>解析失败抛出 {@link IllegalArgumentException}，由调用方决定是否包装为
 * 带 source 位置的 {@link CssParseException}。
 *
 * <p>该解析器不处理选择器分组（逗号）；分组在 {@link CssParser} 层拆分后逐个调用本类。
 */
public final class SelectorParser {

    private SelectorParser() {
    }

    /** 解析选择器文本。trim 后不得为空。 */
    public static ParsedSelector parse(String text) {
        if (text == null) {
            throw new IllegalArgumentException("selector text must not be null");
        }
        String trimmed = text.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("selector text must not be empty");
        }
        return new Parser(trimmed).parse();
    }

    private static final class Parser {
        private final String s;
        private int pos;

        Parser(String s) {
            this.s = s;
            this.pos = 0;
        }

        ParsedSelector parse() {
            List<CompoundSelector> compounds = new ArrayList<CompoundSelector>();
            List<Combinator> combinators = new ArrayList<Combinator>();
            compounds.add(parseCompound());
            while (true) {
                // 读取 combinator：跳过空白，识别 '>'
                boolean sawChild = false;
                boolean sawSpace = false;
                while (pos < s.length()) {
                    char c = s.charAt(pos);
                    if (isWhitespace(c)) {
                        sawSpace = true;
                        pos++;
                    } else if (c == '>') {
                        sawChild = true;
                        pos++;
                        skipWhitespace();
                        break;
                    } else {
                        break;
                    }
                }
                if (pos >= s.length()) {
                    break; // 结束，丢弃尾部 combinator
                }
                compounds.add(parseCompound());
                // '>' 优先；否则若存在空白则为后代
                if (sawChild) {
                    combinators.add(Combinator.CHILD);
                } else if (sawSpace) {
                    combinators.add(Combinator.DESCENDANT);
                } else {
                    // 不应发生：parseCompound 已消费完一个 compound，此处必为空白或 '>'
                    throw new IllegalArgumentException("expected combinator at position " + pos);
                }
            }
            return new ParsedSelector(compounds, combinators, s);
        }

        private void skipWhitespace() {
            while (pos < s.length() && isWhitespace(s.charAt(pos))) {
                pos++;
            }
        }

        private CompoundSelector parseCompound() {
            String tag = null;
            String id = null;
            List<String> classes = new ArrayList<String>();
            Set<PseudoClass> pseudos = EnumSet.noneOf(PseudoClass.class);
            List<AttrSelector> attrs = new ArrayList<AttrSelector>();

            // 可选 tag：compound 起始的标识符
            if (pos < s.length() && isIdentStart(s.charAt(pos))) {
                tag = readIdent();
            }

            while (pos < s.length()) {
                char c = s.charAt(pos);
                if (c == '#') {
                    pos++;
                    id = readIdent();
                } else if (c == '.') {
                    pos++;
                    classes.add(readIdent());
                } else if (c == ':') {
                    pos++;
                    String name = readIdent();
                    PseudoClass pc = PseudoClass.byName(name);
                    if (pc == null) {
                        throw new IllegalArgumentException("unknown pseudo-class :" + name);
                    }
                    pseudos.add(pc);
                } else if (c == '[') {
                    pos++;
                    attrs.add(parseAttr());
                } else if (isWhitespace(c) || c == '>') {
                    break;
                } else {
                    throw new IllegalArgumentException(
                            "unexpected character '" + c + "' at position " + pos);
                }
            }

            if (tag == null && id == null && classes.isEmpty()
                    && pseudos.isEmpty() && attrs.isEmpty()) {
                throw new IllegalArgumentException("empty compound selector");
            }
            return new CompoundSelector(tag, id, classes, pseudos, attrs);
        }

        private AttrSelector parseAttr() {
            skipWhitespace();
            String name = readIdent();
            skipWhitespace();
            if (pos < s.length() && s.charAt(pos) == ']') {
                pos++;
                return AttrSelector.presence(name);
            }
            if (pos < s.length() && s.charAt(pos) == '=') {
                pos++;
                skipWhitespace();
                String value = readAttrValue();
                skipWhitespace();
                if (pos >= s.length() || s.charAt(pos) != ']') {
                    throw new IllegalArgumentException("expected ']' at position " + pos);
                }
                pos++;
                return AttrSelector.equals(name, value);
            }
            throw new IllegalArgumentException("invalid attribute selector at position " + pos);
        }

        private String readAttrValue() {
            if (pos < s.length() && (s.charAt(pos) == '"' || s.charAt(pos) == '\'')) {
                char quote = s.charAt(pos);
                pos++;
                StringBuilder sb = new StringBuilder();
                while (pos < s.length() && s.charAt(pos) != quote) {
                    sb.append(s.charAt(pos));
                    pos++;
                }
                if (pos >= s.length()) {
                    throw new IllegalArgumentException("unterminated string in attribute selector");
                }
                pos++; // 消费闭合引号
                return sb.toString();
            }
            return readIdent();
        }

        private String readIdent() {
            int start = pos;
            while (pos < s.length() && isIdentPart(s.charAt(pos))) {
                pos++;
            }
            if (start == pos) {
                throw new IllegalArgumentException("expected identifier at position " + start);
            }
            return s.substring(start, pos);
        }

        private static boolean isIdentStart(char c) {
            return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || c == '_' || c == '-';
        }

        private static boolean isIdentPart(char c) {
            return isIdentStart(c) || (c >= '0' && c <= '9');
        }

        private static boolean isWhitespace(char c) {
            return c == ' ' || c == '\t' || c == '\n' || c == '\r' || c == '\f';
        }
    }
}
