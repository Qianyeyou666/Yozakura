package gq.yozakura.ui.engine.css;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * 解析 CSS 值中的 {@code var()} 引用，替换为变量映射中的实际值。
 *
 * <p>支持：
 * <ul>
 *   <li>{@code var(--name)}：变量已定义则替换，未定义且无 fallback 返回 {@code null}</li>
 *   <li>{@code var(--name, fallback)}：变量未定义时使用 fallback</li>
 *   <li>嵌套 {@code var(--a, var(--b, #fff))}</li>
 *   <li>变量值引用另一变量（链式解析）</li>
 *   <li>var 嵌入更大值（如 {@code 1px solid var(--c)}）</li>
 *   <li>循环引用检测（{@code --a: var(--b); --b: var(--a)} 返回 {@code null}，不无限递归）</li>
 * </ul>
 *
 * <p>解析失败（未定义变量无 fallback、循环引用、括号不匹配、非法变量名）统一返回 {@code null}，
 * 由调用方决定降级策略（使用初始值或继承值）。不静默使用空字符串。
 *
 * <p>本类不可变、线程不安全（visiting 集合在调用栈上传递）。变量映射在构造时防御性拷贝。
 */
public final class CssVariableResolver {

    private final Map<String, String> variables;

    public CssVariableResolver(Map<String, String> variables) {
        if (variables == null || variables.isEmpty()) {
            this.variables = Collections.emptyMap();
        } else {
            this.variables = new HashMap<String, String>(variables);
        }
    }

    /**
     * 解析 {@link CssValue} 中的所有 {@code var()} 引用。
     *
     * @return 解析后的字符串；若任一 var 无法解析（未定义无 fallback、循环、语法错误）返回 {@code null}
     */
    public String resolve(CssValue value) {
        if (value == null) {
            return null;
        }
        return resolveString(value.raw(), new HashSet<String>());
    }

    /** 解析原始字符串重载；{@code raw} 为 null 返回 null。 */
    public String resolve(String raw) {
        if (raw == null) {
            return null;
        }
        return resolveString(raw.trim(), new HashSet<String>());
    }

    /**
     * 递归解析字符串中的 {@code var(...)}。
     *
     * @param visiting 当前解析路径上正在解析的变量名集合，用于循环检测
     */
    private String resolveString(String s, Set<String> visiting) {
        if (s.isEmpty()) {
            return "";
        }
        // 快速路径：不含 var( 直接返回
        if (s.indexOf("var(") < 0) {
            return s;
        }
        StringBuilder result = new StringBuilder(s.length());
        int i = 0;
        int len = s.length();
        while (i < len) {
            if (startsWith(s, i, "var(")) {
                int open = i + 3; // '(' 的位置
                int close = findMatchingParen(s, open);
                if (close < 0) {
                    return null; // 括号不匹配
                }
                String inner = s.substring(open + 1, close);
                String resolved = resolveVar(inner, visiting);
                if (resolved == null) {
                    return null;
                }
                result.append(resolved);
                i = close + 1;
            } else {
                result.append(s.charAt(i));
                i++;
            }
        }
        return result.toString();
    }

    /**
     * 解析单个 {@code var(...)} 的内部内容（已去除外层 var( 和 )）。
     *
     * <p>内容形如 {@code --name} 或 {@code --name, fallback}。按第一个顶层逗号切分，
     * 以保证 fallback 中的逗号（如 {@code rgba(0,0,0,0.5)}）不被错误切分。
     */
    private String resolveVar(String inner, Set<String> visiting) {
        int comma = findTopLevelComma(inner);
        String name;
        String fallback;
        if (comma < 0) {
            name = inner.trim();
            fallback = null;
        } else {
            name = inner.substring(0, comma).trim();
            fallback = inner.substring(comma + 1).trim();
        }
        // 变量名必须以 -- 开头
        if (name.length() < 3 || !name.startsWith("--")) {
            return null;
        }
        String val = variables.get(name);
        if (val != null) {
            // 循环检测：name 已在当前解析路径上则判定为循环
            if (!visiting.add(name)) {
                // 循环：变量已定义但值无法解析，继续 fallback
                return resolveFallback(fallback, visiting);
            }
            String resolved = resolveString(val, visiting);
            visiting.remove(name);
            if (resolved != null) {
                return resolved;
            }
            // 变量已定义但其值解析失败（嵌套引用未定义、更深层的循环等）：
            // 继续 fallback，不得直接返回 null。
            return resolveFallback(fallback, visiting);
        }
        // 变量未定义：尝试 fallback
        return resolveFallback(fallback, visiting);
    }

    /** 解析 fallback；无 fallback 返回 null。 */
    private String resolveFallback(String fallback, Set<String> visiting) {
        if (fallback == null) {
            return null;
        }
        return resolveString(fallback, visiting);
    }

    /** 从 {@code open}（指向 '('）开始，找到匹配的 ')' 位置；不匹配返回 -1。 */
    private static int findMatchingParen(String s, int open) {
        int depth = 0;
        for (int i = open; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '(') {
                depth++;
            } else if (c == ')') {
                depth--;
                if (depth == 0) {
                    return i;
                }
            }
        }
        return -1;
    }

    /** 找到第一个深度为 0 的逗号位置；没有返回 -1。 */
    private static int findTopLevelComma(String s) {
        int depth = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '(') {
                depth++;
            } else if (c == ')') {
                depth--;
            } else if (c == ',' && depth == 0) {
                return i;
            }
        }
        return -1;
    }

    private static boolean startsWith(String s, int offset, String prefix) {
        if (offset + prefix.length() > s.length()) {
            return false;
        }
        for (int i = 0; i < prefix.length(); i++) {
            if (s.charAt(offset + i) != prefix.charAt(i)) {
                return false;
            }
        }
        return true;
    }
}
