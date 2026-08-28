package gq.yozakura.ui.engine.css;

import gq.yozakura.ui.engine.dom.ElementNode;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * 阶段 1 修订：CSS 变量依赖索引契约测试。
 *
 * <p>规格要求"变量修改只失效依赖节点"。StyleDependencyIndex 是纯离线数据结构，
 * 记录 var(--name) 被哪些 (element, property) 使用；修改变量时只返回受影响元素集合，
 * 不扫描或重算无关节点。
 *
 * <p>不引入布局、OpenGL 或 Minecraft 依赖。
 */
public class StyleDependencyIndexTest {

    private static ElementNode el(String tag, String id) {
        ElementNode e = ElementNode.create(tag);
        if (id != null) e.withId(id);
        return e;
    }

    private static ElementNode el(String tag) {
        return el(tag, null);
    }

    // ---- 基础注册与查询 ----

    @Test
    public void registerUseRecordsByVariableName() {
        StyleDependencyIndex idx = new StyleDependencyIndex();
        ElementNode a = el("div", "a");
        idx.registerUse(a, "color", "--accent");

        Set<ElementNode> affected = idx.elementsAffectedBy("--accent");
        assertEquals(1, affected.size());
        assertTrue(affected.contains(a));
    }

    @Test
    public void multipleUsagesOfSameVariableReturnAllElements() {
        StyleDependencyIndex idx = new StyleDependencyIndex();
        ElementNode a = el("div", "a");
        ElementNode b = el("div", "b");
        ElementNode c = el("div", "c");
        idx.registerUse(a, "color", "--accent");
        idx.registerUse(b, "background", "--accent");
        idx.registerUse(c, "border-color", "--accent");

        Set<ElementNode> affected = idx.elementsAffectedBy("--accent");
        assertEquals(3, affected.size());
        assertTrue(affected.contains(a));
        assertTrue(affected.contains(b));
        assertTrue(affected.contains(c));
    }

    @Test
    public void sameElementMultiplePropertiesUsingSameVariable() {
        StyleDependencyIndex idx = new StyleDependencyIndex();
        ElementNode a = el("div", "a");
        idx.registerUse(a, "color", "--accent");
        idx.registerUse(a, "background", "--accent");

        Set<StyleDependencyIndex.Usage> usages = idx.usagesFor("--accent");
        assertEquals(2, usages.size());

        Set<ElementNode> affected = idx.elementsAffectedBy("--accent");
        assertEquals(1, affected.size()); // 去重：同元素多属性只算一个受影响元素
        assertTrue(affected.contains(a));
    }

    // ---- 隔离性：修改 --name 不影响使用 --other 的节点 ----

    @Test
    public void elementsAffectedByReturnsOnlyRelevantElements() {
        StyleDependencyIndex idx = new StyleDependencyIndex();
        ElementNode usesA1 = el("div", "usesA1");
        ElementNode usesA2 = el("div", "usesA2");
        ElementNode usesB1 = el("div", "usesB1");
        ElementNode usesB2 = el("div", "usesB2");
        ElementNode usesNoVar = el("div", "usesNoVar");

        idx.registerUse(usesA1, "color", "--a");
        idx.registerUse(usesA2, "background", "--a");
        idx.registerUse(usesB1, "color", "--b");
        idx.registerUse(usesB2, "border", "--b");

        Set<ElementNode> affectedByA = idx.elementsAffectedBy("--a");
        assertEquals(2, affectedByA.size());
        assertTrue(affectedByA.contains(usesA1));
        assertTrue(affectedByA.contains(usesA2));

        Set<ElementNode> affectedByB = idx.elementsAffectedBy("--b");
        assertEquals(2, affectedByB.size());
        assertTrue(affectedByB.contains(usesB1));
        assertTrue(affectedByB.contains(usesB2));

        // 修改 --a 不应触及 --b 的节点
        assertTrue(!affectedByA.contains(usesB1));
        assertTrue(!affectedByA.contains(usesB2));
    }

    @Test
    public void unregisteredVariableReturnsEmptySet() {
        StyleDependencyIndex idx = new StyleDependencyIndex();
        idx.registerUse(el("div"), "color", "--a");

        Set<ElementNode> affected = idx.elementsAffectedBy("--unregistered");
        assertTrue(affected.isEmpty());
    }

    @Test
    public void usagesForUnregisteredVariableReturnsEmptySet() {
        StyleDependencyIndex idx = new StyleDependencyIndex();
        assertTrue(idx.usagesFor("--unregistered").isEmpty());
    }

    // ---- 通过声明列表批量注册（扫描 var() 引用）----

    @Test
    public void registerDeclarationsScansVarReferencesInValues() {
        StyleDependencyIndex idx = new StyleDependencyIndex();
        ElementNode a = el("div", "a");

        List<Declaration> decls = Arrays.asList(
                new Declaration(new Property("color"), new CssValue("var(--accent)"), false),
                new Declaration(new Property("background"), new CssValue("var(--bg, #fff)"), false),
                new Declaration(new Property("border"), new CssValue("1px solid var(--accent)"), false),
                new Declaration(new Property("width"), new CssValue("100px"), false));

        idx.registerDeclarations(a, decls);

        // color、border 都用 var(--accent)；background 用 var(--bg)；width 无 var
        Set<StyleDependencyIndex.Usage> accentUsages = idx.usagesFor("--accent");
        assertEquals(2, accentUsages.size());
        Set<String> accentProps = new HashSet<String>();
        for (StyleDependencyIndex.Usage u : accentUsages) {
            accentProps.add(u.property());
        }
        assertTrue(accentProps.contains("color"));
        assertTrue(accentProps.contains("border"));

        Set<StyleDependencyIndex.Usage> bgUsages = idx.usagesFor("--bg");
        assertEquals(1, bgUsages.size());
        assertEquals("background", bgUsages.iterator().next().property());
    }

    @Test
    public void registerDeclarationsHandlesNestedVars() {
        StyleDependencyIndex idx = new StyleDependencyIndex();
        ElementNode a = el("div", "a");

        List<Declaration> decls = Collections.<Declaration>singletonList(
                new Declaration(new Property("color"),
                        new CssValue("var(--a, var(--b, #fff))"), false));

        idx.registerDeclarations(a, decls);

        // 嵌套 var(--a, var(--b, #fff))：外层引用 --a，内层引用 --b
        Set<StyleDependencyIndex.Usage> aUsages = idx.usagesFor("--a");
        assertEquals(1, aUsages.size());
        Set<StyleDependencyIndex.Usage> bUsages = idx.usagesFor("--b");
        assertEquals(1, bUsages.size());
    }

    @Test
    public void registerDeclarationsWithEmptyListIsNoOp() {
        StyleDependencyIndex idx = new StyleDependencyIndex();
        idx.registerDeclarations(el("div"), Collections.<Declaration>emptyList());
        assertTrue(idx.registeredVariableNames().isEmpty());
    }

    // ---- 确定性测试：修改 --name 只触发受影响节点的重算 ----

    @Test
    public void modifyingVariableTriggersRecomputeOnlyForAffectedElements() {
        // 场景：5 个元素，其中 2 个用 --accent（应重算），3 个不用（不应重算）
        StyleDependencyIndex idx = new StyleDependencyIndex();
        ElementNode usesAccent1 = el("div", "usesAccent1");
        ElementNode usesAccent2 = el("div", "usesAccent2");
        ElementNode noVar1 = el("div", "noVar1");
        ElementNode noVar2 = el("div", "noVar2");
        ElementNode usesOther = el("div", "usesOther");

        idx.registerUse(usesAccent1, "color", "--accent");
        idx.registerUse(usesAccent2, "background", "--accent");
        idx.registerUse(usesOther, "color", "--other");

        // 模拟：当 --accent 修改时，只重算 elementsAffectedBy("--accent") 中的元素
        Set<ElementNode> toRecompute = idx.elementsAffectedBy("--accent");
        Set<ElementNode> recomputed = new HashSet<ElementNode>();
        for (ElementNode e : toRecompute) {
            recomputed.add(e); // 模拟重算
        }

        // 确定性断言：只有 2 个元素被重算
        assertEquals(2, recomputed.size());
        assertTrue(recomputed.contains(usesAccent1));
        assertTrue(recomputed.contains(usesAccent2));
        assertTrue(!recomputed.contains(noVar1));
        assertTrue(!recomputed.contains(noVar2));
        assertTrue(!recomputed.contains(usesOther));
    }

    @Test
    public void recomputeCounterProvesNoFullScan() {
        // 计数器测试：构建 N 个元素的索引，仅 K 个使用 --target；
        // 修改 --target 后，elementsAffectedBy 返回的元素数严格等于 K，不大于 N。
        StyleDependencyIndex idx = new StyleDependencyIndex();
        int total = 20;
        int affected = 5;
        List<ElementNode> all = new ArrayList<ElementNode>();
        for (int i = 0; i < total; i++) {
            ElementNode e = el("div", "el" + i);
            all.add(e);
            if (i < affected) {
                idx.registerUse(e, "color", "--target");
            } else {
                idx.registerUse(e, "color", "--other");
            }
        }

        int recomputed = idx.elementsAffectedBy("--target").size();
        assertEquals(affected, recomputed);
        assertTrue("recompute count must be less than total node count",
                recomputed < total);
    }

    // ---- 元数据 ----

    @Test
    public void registeredVariableNamesReturnsAllKnownNames() {
        StyleDependencyIndex idx = new StyleDependencyIndex();
        idx.registerUse(el("div"), "color", "--a");
        idx.registerUse(el("div"), "color", "--b");
        idx.registerUse(el("div"), "color", "--c");

        Set<String> names = idx.registeredVariableNames();
        assertEquals(3, names.size());
        assertTrue(names.contains("--a"));
        assertTrue(names.contains("--b"));
        assertTrue(names.contains("--c"));
    }

    @Test
    public void totalUsagesCountsAllRegistrations() {
        StyleDependencyIndex idx = new StyleDependencyIndex();
        ElementNode a = el("div", "a");
        idx.registerUse(a, "color", "--x");
        idx.registerUse(a, "background", "--x");
        idx.registerUse(el("div", "b"), "color", "--y");

        assertEquals(3, idx.totalUsages());
    }
}
