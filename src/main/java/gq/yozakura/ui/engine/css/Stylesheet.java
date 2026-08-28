package gq.yozakura.ui.engine.css;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * CSS 样式表：规则列表 + at-rule 计数。不可变值对象（构造后规则列表不可变）。
 *
 * <p>at-rule（如 @media）在 MVP 中作为不透明块跳过，仅计数；
 * 普通规则按 source order 保留。
 */
public final class Stylesheet {
    private final List<Rule> rules;
    private final int atRuleCount;

    public Stylesheet(List<Rule> rules, int atRuleCount) {
        this.rules = rules == null
                ? Collections.<Rule>emptyList()
                : Collections.unmodifiableList(new ArrayList<Rule>(rules));
        this.atRuleCount = atRuleCount;
    }

    public int ruleCount() {
        return rules.size();
    }

    public Rule rule(int index) {
        return rules.get(index);
    }

    public List<Rule> rules() {
        return rules;
    }

    public int atRuleCount() {
        return atRuleCount;
    }

    @Override
    public String toString() {
        return "Stylesheet{rules=" + rules.size() + ", atRules=" + atRuleCount + "}";
    }
}
