package gq.yozakura.ui.engine.css;

import gq.yozakura.ui.engine.dom.ElementNode;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * CSS 变量依赖索引：纯离线数据结构，记录 var(--name) 被哪些 (element, property) 使用。
 *
 * <p>用途：当某个自定义变量（如 {@code --accent}）被修改时，只失效/重算真正引用该变量的元素，
 * 不扫描或重算无关节点。规格阶段 1 验收项要求"变量修改只失效依赖节点"。
 *
 * <p>本类只记录直接 var() 引用，不做变量到变量的传递闭包。
 * 例如 {@code --a: var(--b); color: var(--a)} 中，color 仅登记为依赖 --a；
 * 修改 --b 时不会经由 --a 传递到 color。需要传递闭包的场景可在调用方额外维护
 * 变量间引用关系（本类提供 {@link #registerUse} 原语以支持任意注册）。
 *
 * <p>不引入布局、OpenGL 或 Minecraft 依赖；纯 Java 8 集合实现。
 * 线程不安全：仅在解析/样式失效路径单线程使用。
 */
public final class StyleDependencyIndex {

    /**
     * 单条 var() 使用记录：哪个元素的哪个属性引用了哪个变量。
     * 不可变值对象。
     */
    public static final class Usage {
        private final ElementNode element;
        private final String property;
        private final String variableName;

        public Usage(ElementNode element, String property, String variableName) {
            if (element == null) {
                throw new IllegalArgumentException("element must not be null");
            }
            if (property == null || property.isEmpty()) {
                throw new IllegalArgumentException("property must not be null or empty");
            }
            if (variableName == null || !variableName.startsWith("--")) {
                throw new IllegalArgumentException(
                        "variableName must start with '--': " + variableName);
            }
            this.element = element;
            this.property = property;
            this.variableName = variableName;
        }

        public ElementNode element() {
            return element;
        }

        public String property() {
            return property;
        }

        public String variableName() {
            return variableName;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Usage)) return false;
            Usage u = (Usage) o;
            return element == u.element
                    && property.equals(u.property)
                    && variableName.equals(u.variableName);
        }

        @Override
        public int hashCode() {
            int r = System.identityHashCode(element);
            r = 31 * r + property.hashCode();
            r = 31 * r + variableName.hashCode();
            return r;
        }

        @Override
        public String toString() {
            return "Usage{" + variableName + " used by " + property + "}";
        }
    }

    // 变量名 -> 使用列表（保留注册顺序，便于确定性迭代）
    private final Map<String, List<Usage>> index = new LinkedHashMap<String, List<Usage>>();
    private int totalUsages;

    /**
     * 显式登记一条 var() 使用记录。
     *
     * <p>调用方可手动注册，也可通过 {@link #registerDeclarations} 批量扫描。
     * 同一 (element, property, varName) 多次注册会重复记录（便于统计调用次数）。
     */
    public void registerUse(ElementNode element, String property, String variableName) {
        Usage usage = new Usage(element, property, variableName);
        List<Usage> list = index.get(variableName);
        if (list == null) {
            list = new ArrayList<Usage>();
            index.put(variableName, list);
        }
        list.add(usage);
        totalUsages++;
    }

    /**
     * 扫描一组声明，将其中所有 var() 引用登记到索引。
     *
     * <p>仅扫描声明的值字符串中的 var(--name) 引用（含嵌套 var()）；
     * 不解析值，不评估 fallback。同一 var() 在值中出现 N 次会被登记 N 次
     * （由 {@link #extractVarReferences(String)} 的实现决定）。
     *
     * @param element      声明所属元素
     * @param declarations 待扫描的声明列表；null 或空列表为 no-op
     */
    public void registerDeclarations(ElementNode element, List<Declaration> declarations) {
        if (declarations == null || declarations.isEmpty()) {
            return;
        }
        for (int i = 0; i < declarations.size(); i++) {
            Declaration d = declarations.get(i);
            // 自定义变量声明本身不登记为"使用"——它定义变量，不消费变量
            if (d.property().isCustomProperty()) {
                continue;
            }
            String value = d.value().raw();
            List<String> refs = extractVarReferences(value);
            for (int j = 0; j < refs.size(); j++) {
                registerUse(element, d.property().name(), refs.get(j));
            }
        }
    }

    /**
     * 返回使用指定变量的元素集合（去重）。
     * 修改变量时只需重算此集合中的元素。
     */
    public Set<ElementNode> elementsAffectedBy(String variableName) {
        List<Usage> list = index.get(variableName);
        if (list == null || list.isEmpty()) {
            return Collections.emptySet();
        }
        // 用 identity set 去重（ElementNode 没重写 equals，默认就是 identity）
        Set<ElementNode> elements = new LinkedHashSet<ElementNode>();
        for (int i = 0; i < list.size(); i++) {
            elements.add(list.get(i).element());
        }
        return Collections.unmodifiableSet(elements);
    }

    /**
     * 返回指定变量的全部使用记录（不去重，保留注册顺序）。
     * 用于精确失效：同一元素多个属性引用同一变量时，每个属性都需重算。
     */
    public Set<Usage> usagesFor(String variableName) {
        List<Usage> list = index.get(variableName);
        if (list == null || list.isEmpty()) {
            return Collections.emptySet();
        }
        return Collections.unmodifiableSet(new LinkedHashSet<Usage>(list));
    }

    /** 已登记的全部变量名集合。 */
    public Set<String> registeredVariableNames() {
        return Collections.unmodifiableSet(new LinkedHashSet<String>(index.keySet()));
    }

    /** 累计登记次数（含重复）；用于统计/调试。 */
    public int totalUsages() {
        return totalUsages;
    }

    /** 清空索引。 */
    public void clear() {
        index.clear();
        totalUsages = 0;
    }

    /**
     * 从 CSS 值字符串中提取所有 var() 引用的变量名。
     *
     * <p>处理嵌套 var(--a, var(--b, #fff))：返回 ["--a", "--b"]。
     * 不评估 fallback、不解析值，只提取变量名。
     * 括号不匹配时返回已提取的部分。
     */
    static List<String> extractVarReferences(String value) {
        if (value == null || value.isEmpty()) {
            return Collections.emptyList();
        }
        List<String> refs = null;
        int i = 0;
        int len = value.length();
        while (i < len) {
            // 找 "var("
            if (value.charAt(i) == 'v'
                    && i + 4 <= len
                    && value.charAt(i + 1) == 'a'
                    && value.charAt(i + 2) == 'r'
                    && value.charAt(i + 3) == '(') {
                int open = i + 3; // '(' 位置
                int close = findMatchingParen(value, open);
                if (close < 0) {
                    break; // 括号不匹配，停止
                }
                String inner = value.substring(open + 1, close);
                // inner 形如 "--name" 或 "--name, fallback"
                String name = extractVarName(inner);
                if (name != null) {
                    if (refs == null) {
                        refs = new ArrayList<String>(2);
                    }
                    refs.add(name);
                }
                // 继续扫描 inner（fallback 中可能还有嵌套 var）
                List<String> nested = extractVarReferences(inner);
                if (!nested.isEmpty()) {
                    if (refs == null) {
                        refs = new ArrayList<String>(nested.size());
                    }
                    refs.addAll(nested);
                }
                i = close + 1;
            } else {
                i++;
            }
        }
        return refs == null ? Collections.<String>emptyList() : refs;
    }

    /** 从 var() 内部内容（如 "--name, fallback"）提取顶层变量名。 */
    private static String extractVarName(String inner) {
        int comma = findTopLevelComma(inner);
        String name = (comma < 0 ? inner : inner.substring(0, comma)).trim();
        if (name.length() >= 3 && name.startsWith("--")) {
            return name;
        }
        return null;
    }

    private static int findMatchingParen(String s, int open) {
        int depth = 0;
        for (int i = open; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '(') depth++;
            else if (c == ')') {
                depth--;
                if (depth == 0) return i;
            }
        }
        return -1;
    }

    private static int findTopLevelComma(String s) {
        int depth = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '(') depth++;
            else if (c == ')') depth--;
            else if (c == ',' && depth == 0) return i;
        }
        return -1;
    }
}
