package gq.yozakura.ui.engine.css;

import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/**
 * 阶段 1 切片 5：CSS 变量与 var() 解析契约测试。
 *
 * <p>覆盖：
 * <ul>
 *   <li>简单 var(--name) 解析</li>
 *   <li>var(--name, fallback) 默认值</li>
 *   <li>var 嵌入更大值（如 1px solid var(--c)）</li>
 *   <li>嵌套 var(--a, var(--b, #fff))</li>
 *   <li>变量值引用另一个变量</li>
 *   <li>循环引用检测（不无限递归）</li>
 *   <li>未定义变量无默认值 → null</li>
 *   <li>带逗号的 fallback（rgba(0,0,0,0.5)）</li>
 * </ul>
 */
public class CssVariableResolverTest {

    private static CssVariableResolver resolver(Map<String, String> vars) {
        return new CssVariableResolver(vars);
    }

    private static Map<String, String> map(String... kv) {
        Map<String, String> m = new HashMap<String, String>();
        for (int i = 0; i + 1 < kv.length; i += 2) {
            m.put(kv[i], kv[i + 1]);
        }
        return m;
    }

    private static String resolve(CssVariableResolver r, String raw) {
        return r.resolve(new CssValue(raw));
    }

    // ---- 基础解析 ----

    @Test
    public void resolvesSimpleVar() {
        CssVariableResolver r = resolver(map("--accent", "#8b5cf6"));
        assertEquals("#8b5cf6", resolve(r, "var(--accent)"));
    }

    @Test
    public void valueWithoutVarReturnedAsIs() {
        CssVariableResolver r = resolver(map());
        assertEquals("#fff", resolve(r, "#fff"));
        assertEquals("1px solid #ccc", resolve(r, "1px solid #ccc"));
    }

    @Test
    public void resolvesVarWithFallbackWhenDefined() {
        CssVariableResolver r = resolver(map("--accent", "#8b5cf6"));
        assertEquals("#8b5cf6", resolve(r, "var(--accent, #fff)"));
    }

    @Test
    public void resolvesVarWithFallbackWhenUndefined() {
        CssVariableResolver r = resolver(map());
        assertEquals("#fff", resolve(r, "var(--accent, #fff)"));
    }

    @Test
    public void undefinedVarWithoutFallbackReturnsNull() {
        CssVariableResolver r = resolver(map());
        assertNull(resolve(r, "var(--accent)"));
    }

    // ---- 嵌入更大值 ----

    @Test
    public void resolvesVarEmbeddedInLargerValue() {
        CssVariableResolver r = resolver(map("--c", "#ccc"));
        assertEquals("1px solid #ccc", resolve(r, "1px solid var(--c)"));
    }

    @Test
    public void resolvesMultipleVarsInOneValue() {
        CssVariableResolver r = resolver(map("--w", "1px", "--c", "#ccc"));
        assertEquals("1px solid #ccc", resolve(r, "var(--w) solid var(--c)"));
    }

    @Test
    public void embeddedVarUndefinedWithoutFallbackReturnsNull() {
        CssVariableResolver r = resolver(map());
        assertNull(resolve(r, "1px solid var(--c)"));
    }

    // ---- 嵌套与引用 ----

    @Test
    public void resolvesNestedVarFallback() {
        CssVariableResolver r = resolver(map("--b", "#ccc"));
        assertEquals("#ccc", resolve(r, "var(--a, var(--b, #fff))"));
    }

    @Test
    public void resolvesNestedVarFallbackAllUndefined() {
        CssVariableResolver r = resolver(map());
        assertEquals("#fff", resolve(r, "var(--a, var(--b, #fff))"));
    }

    @Test
    public void resolvesVarReferencingAnotherVar() {
        CssVariableResolver r = resolver(map("--a", "var(--b)", "--b", "#ccc"));
        assertEquals("#ccc", resolve(r, "var(--a)"));
    }

    @Test
    public void resolvesVarChain() {
        CssVariableResolver r = resolver(map("--a", "var(--b)", "--b", "var(--c)", "--c", "#fff"));
        assertEquals("#fff", resolve(r, "var(--a)"));
    }

    // ---- 循环检测 ----

    @Test
    public void cycleReturnsNull() {
        CssVariableResolver r = resolver(map("--a", "var(--b)", "--b", "var(--a)"));
        assertNull(resolve(r, "var(--a)"));
    }

    @Test
    public void selfReferenceReturnsNull() {
        CssVariableResolver r = resolver(map("--a", "var(--a)"));
        assertNull(resolve(r, "var(--a)"));
    }

    // ---- 循环/嵌套解析失败时继续 fallback（不得直接返回 null）----

    @Test
    public void selfReferenceWithFallbackReturnsFallback() {
        // --a 已定义但其值解析失败（自引用循环）；var(--a, red) 必须继续 fallback → red
        CssVariableResolver r = resolver(map("--a", "var(--a)"));
        assertEquals("red", resolve(r, "var(--a, red)"));
    }

    @Test
    public void mutualCycleWithFallbackReturnsFallback() {
        // --a: var(--b); --b: var(--a); var(--a, #fff) → #fff
        CssVariableResolver r = resolver(map("--a", "var(--b)", "--b", "var(--a)"));
        assertEquals("#fff", resolve(r, "var(--a, #fff)"));
    }

    @Test
    public void definedVarReferencingUndefinedWithFallbackReturnsFallback() {
        // --a: var(--missing); --missing 未定义。
        // --a 已定义但其值 var(--missing) 解析为 null；var(--a, #fff) 必须继续 fallback → #fff
        CssVariableResolver r = resolver(map("--a", "var(--missing)"));
        assertEquals("#fff", resolve(r, "var(--a, #fff)"));
    }

    @Test
    public void nestedFallbackWhenDefinedOuterResolvesToNull() {
        // --a: var(--c); --c: var(--c)（自循环 → null）
        // var(--a, var(--b, #fff))：外层 --a 解析失败，应继续外层 fallback；
        // 内层 --b 未定义，继续内层 fallback → #fff
        CssVariableResolver r = resolver(map("--a", "var(--c)", "--c", "var(--c)"));
        assertEquals("#fff", resolve(r, "var(--a, var(--b, #fff))"));
    }

    // ---- 边界 ----

    @Test
    public void varWithWhitespaceAroundName() {
        CssVariableResolver r = resolver(map("--accent", "#8b5cf6"));
        assertEquals("#8b5cf6", resolve(r, "var( --accent )"));
    }

    @Test
    public void fallbackWithCommasPreserved() {
        CssVariableResolver r = resolver(map());
        assertEquals("rgba(0, 0, 0, 0.5)", resolve(r, "var(--c, rgba(0, 0, 0, 0.5))"));
    }

    @Test
    public void fallbackVarWithCommaValue() {
        // fallback 本身是带逗号的值，确保只按第一个顶层逗号切分 name/fallback
        CssVariableResolver r = resolver(map("--defined", "rgba(1,2,3,4)"));
        assertEquals("rgba(1,2,3,4)", resolve(r, "var(--undefined, var(--defined))"));
    }

    @Test
    public void resolvesRawStringOverload() {
        CssVariableResolver r = resolver(map("--accent", "#8b5cf6"));
        assertEquals("#8b5cf6", r.resolve("var(--accent)"));
    }

    @Test
    public void nullInputReturnsNull() {
        CssVariableResolver r = resolver(map());
        assertNull(r.resolve((CssValue) null));
    }

    @Test
    public void emptyValueReturnsEmpty() {
        CssVariableResolver r = resolver(map());
        assertEquals("", resolve(r, ""));
    }
}
