package gq.yozakura.ui.engine.css;

import gq.yozakura.ui.engine.dom.DomNode;
import gq.yozakura.ui.engine.dom.ElementNode;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 样式解析器：对 DOM 树应用 {@link Stylesheet}，产出每个元素的 {@link ComputedStyle}。
 *
 * <p>处理顺序（自顶向下 DFS）：
 * <ol>
 *   <li>收集匹配当前元素的全部声明（含多个规则、多个选择器）</li>
 *   <li>按 (important, specificity, sourceOrder) 升序排序，依次应用，高优先级覆盖低优先级</li>
 *   <li>从父 ComputedStyle 继承：自定义变量（全部）+ 普通继承属性（仅 {@link InheritedProperties}）</li>
 *   <li>用本元素最终自定义变量映射构造 {@link CssVariableResolver}，解析普通属性值中的 var()</li>
 *   <li>构建 ComputedStyle，递归处理子元素</li>
 * </ol>
 *
 * <p>var() 解析失败（未定义无 fallback、循环引用）的属性：
 * <ul>
 *   <li>继承属性：保留父元素的值（与 CSS "invalid at computed-value time" 对继承属性的回退一致）</li>
 *   <li>非继承属性：不写入 ComputedStyle（后续取初始值）</li>
 * </ul>
 *
 * <p>选择器解析结果在单次 {@link #resolve} 调用内缓存，避免重复解析同一选择器文本。
 */
public final class StyleResolver {

    private final SelectorMatcher matcher = new SelectorMatcher();
    private final CssParser inlineStyleParser = new CssParser();
    private Map<String, ParsedSelector> selectorCache;

    /**
     * 解析 DOM 树的 ComputedStyle。
     *
     * @param stylesheet 样式表，不得为 null
     * @param root       根元素，不得为 null；应无父节点（用于 :root 匹配）
     * @return 元素到 ComputedStyle 的映射；包含 root 及其全部后代元素
     */
    public Map<ElementNode, ComputedStyle> resolve(Stylesheet stylesheet, ElementNode root) {
        if (stylesheet == null) {
            throw new IllegalArgumentException("stylesheet must not be null");
        }
        if (root == null) {
            throw new IllegalArgumentException("root must not be null");
        }
        selectorCache = new HashMap<String, ParsedSelector>();
        Map<ElementNode, ComputedStyle> result = new LinkedHashMap<ElementNode, ComputedStyle>();
        resolveElement(stylesheet, root, null, result);
        selectorCache = null; // 释放缓存
        return Collections.unmodifiableMap(result);
    }

    private void resolveElement(Stylesheet ss, ElementNode element,
                                ComputedStyle parentStyle,
                                Map<ElementNode, ComputedStyle> result) {
        ComputedStyle style = computeStyle(ss, element, parentStyle);
        result.put(element, style);
        for (int i = 0; i < element.childCount(); i++) {
            DomNode child = element.child(i);
            if (child instanceof ElementNode) {
                resolveElement(ss, (ElementNode) child, style, result);
            }
        }
    }

    private ComputedStyle computeStyle(Stylesheet ss, ElementNode element,
                                       ComputedStyle parentStyle) {
        // 1. 收集匹配的声明并按优先级升序排序
        List<CandidateDeclaration> candidates = collectCandidates(ss, element);
        // 追加内联 style 声明（参与级联，按 inlineTier + 顺序）
        candidates.addAll(collectInlineCandidates(element));
        // 升序：important(0<1), inlineTier(0<1), specificity, sourceOrder —— 后应用者覆盖先应用者
        Collections.sort(candidates, CANDIDATE_COMPARATOR);

        // 2. 应用 cascade，分别得到普通属性和自定义变量的原始值
        Map<String, String> regularRaw = new LinkedHashMap<String, String>();
        Map<String, String> customRaw = new LinkedHashMap<String, String>();
        for (int i = 0; i < candidates.size(); i++) {
            CandidateDeclaration c = candidates.get(i);
            Declaration d = c.declaration;
            if (d.property().isCustomProperty()) {
                customRaw.put(d.property().name(), d.value().raw());
            } else {
                regularRaw.put(d.property().name(), d.value().raw());
            }
        }

        // 3. 构建最终自定义变量映射：父继承 + 本元素 cascade
        Map<String, String> finalCustoms = new LinkedHashMap<String, String>();
        if (parentStyle != null) {
            finalCustoms.putAll(parentStyle.customProperties());
        }
        finalCustoms.putAll(customRaw); // cascade 覆盖继承

        // 4. 用最终变量映射构造 resolver
        CssVariableResolver resolver = new CssVariableResolver(finalCustoms);

        // 5. 构建 ComputedStyle
        ComputedStyle.Builder builder = ComputedStyle.builder();

        // 5a. 继承父元素的普通继承属性
        if (parentStyle != null) {
            for (String name : parentStyle.propertyNames()) {
                if (InheritedProperties.contains(name)) {
                    builder.set(name, parentStyle.get(name));
                }
            }
        }

        // 5b. 应用 cascade 普通属性（var() 已解析）
        for (Map.Entry<String, String> e : regularRaw.entrySet()) {
            String resolved = resolver.resolve(e.getValue());
            if (resolved != null) {
                builder.set(e.getKey(), resolved);
            }
            // 解析失败时：继承属性保留 5a 的父值；非继承属性保持 absent
        }

        // 5c. 写入最终自定义变量（保留原始值，供后代继承并按需解析）
        for (Map.Entry<String, String> e : finalCustoms.entrySet()) {
            builder.setCustom(e.getKey(), e.getValue());
        }

        return builder.build();
    }

    /**
     * 收集所有匹配元素的规则的声明，附带 specificity 与 sourceOrder。
     * 多选择器规则中任一选择器匹配即应用全部声明；
     * specificity 取所有命中选择器中最高的（不能在首个匹配项 break），
     * 以保证 ".a, #app" 在元素同时命中 .a 与 #app 时按 #app 的 (1,0,0) 参与级联。
     */
    private List<CandidateDeclaration> collectCandidates(Stylesheet ss, ElementNode element) {
        List<CandidateDeclaration> result = new ArrayList<CandidateDeclaration>();
        for (int i = 0; i < ss.ruleCount(); i++) {
            Rule rule = ss.rule(i);
            Specificity matchedSpec = null;
            for (int j = 0; j < rule.selectors().size(); j++) {
                Selector sel = rule.selectors().get(j);
                ParsedSelector parsed = parseSelector(sel.text());
                if (matcher.matches(parsed, element)) {
                    Specificity spec = Specificity.of(parsed);
                    if (matchedSpec == null || spec.compareTo(matchedSpec) > 0) {
                        matchedSpec = spec;
                    }
                    // 不 break：继续检查其余 selector，取最高 specificity
                }
            }
            if (matchedSpec == null) {
                continue;
            }
            List<Declaration> decls = rule.declarations();
            for (int j = 0; j < decls.size(); j++) {
                result.add(new CandidateDeclaration(decls.get(j), matchedSpec, rule.sourceOrder()));
            }
        }
        return result;
    }

    /**
     * 收集元素 inline style 中的声明作为级联候选。
     *
     * <p>inline 声明不带选择器 specificity（参与排序时 inlineTier=1 已保证其位置正确）。
     * sourceOrder 使用声明在 inline 文本中的下标，保证 inline 内同属性后写者胜。
     */
    private List<CandidateDeclaration> collectInlineCandidates(ElementNode element) {
        String inline = element.inlineStyle();
        if (inline == null || inline.isEmpty()) {
            return Collections.emptyList();
        }
        List<Declaration> decls = inlineStyleParser.parseInlineDeclarations(inline);
        List<CandidateDeclaration> result = new ArrayList<CandidateDeclaration>(decls.size());
        for (int i = 0; i < decls.size(); i++) {
            // specificity 取 (0,0,0)：inline tier 已决定排序，无需 specificity 区分
            result.add(new CandidateDeclaration(decls.get(i), Specificity.ofValues(0, 0, 0), i, true));
        }
        return result;
    }

    private ParsedSelector parseSelector(String text) {
        ParsedSelector cached = selectorCache.get(text);
        if (cached == null) {
            cached = SelectorParser.parse(text);
            selectorCache.put(text, cached);
        }
        return cached;
    }

    /** 候选声明：声明 + specificity + sourceOrder + inline 标记，用于 cascade 排序。 */
    private static final class CandidateDeclaration {
        final Declaration declaration;
        final Specificity specificity;
        final int sourceOrder;
        final boolean important;
        final boolean inline;

        CandidateDeclaration(Declaration declaration, Specificity specificity, int sourceOrder) {
            this(declaration, specificity, sourceOrder, false);
        }

        CandidateDeclaration(Declaration declaration, Specificity specificity,
                             int sourceOrder, boolean inline) {
            this.declaration = declaration;
            this.specificity = specificity;
            this.sourceOrder = sourceOrder;
            this.important = declaration.important();
            this.inline = inline;
        }
    }

    /**
     * 升序比较器：important(0<1) → inlineTier(0=stylesheet<1=inline) → specificity → sourceOrder。
     * 升序保证后应用者（更高优先级）覆盖先应用者。
     *
     * <p>排序分层（自低到高）：
     * <ol>
     *   <li>stylesheet 普通声明（按 specificity, sourceOrder）</li>
     *   <li>inline 普通声明（始终高于 stylesheet 普通）</li>
     *   <li>stylesheet !important 声明（按 specificity, sourceOrder）</li>
     *   <li>inline !important 声明（始终高于 stylesheet !important）</li>
     * </ol>
     */
    private static final Comparator<CandidateDeclaration> CANDIDATE_COMPARATOR =
            new Comparator<CandidateDeclaration>() {
                @Override
                public int compare(CandidateDeclaration a, CandidateDeclaration b) {
                    int impA = a.important ? 1 : 0;
                    int impB = b.important ? 1 : 0;
                    if (impA != impB) {
                        return impA - impB;
                    }
                    int inlineA = a.inline ? 1 : 0;
                    int inlineB = b.inline ? 1 : 0;
                    if (inlineA != inlineB) {
                        return inlineA - inlineB;
                    }
                    int c = a.specificity.compareTo(b.specificity);
                    if (c != 0) {
                        return c;
                    }
                    return a.sourceOrder - b.sourceOrder;
                }
            };
}
