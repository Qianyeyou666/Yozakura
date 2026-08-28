package gq.yozakura.ui.engine.css;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 解析后的选择器：compound 列表与连接它们的 combinator 列表。
 *
 * <p>例如 "div > .sidebar .category" 解析为 3 个 compound 和 2 个 combinator：
 * [div] CHILD [sidebar] DESCENDANT [category]。
 * combinators[i] 连接 compounds[i] 与 compounds[i+1]。
 * 不可变值对象。
 */
public final class ParsedSelector {
    private final List<CompoundSelector> compounds;
    private final List<Combinator> combinators;
    private final String text;

    public ParsedSelector(List<CompoundSelector> compounds, List<Combinator> combinators, String text) {
        if (compounds == null || compounds.isEmpty()) {
            throw new IllegalArgumentException("compounds must not be null or empty");
        }
        // 防御性复制：调用方修改原始 list 不得影响已构造的 ParsedSelector 状态。
        // unmodifiableList 只是视图，原始 list 仍可被修改。
        this.compounds = Collections.unmodifiableList(new ArrayList<CompoundSelector>(compounds));
        this.combinators = combinators == null
                ? Collections.<Combinator>emptyList()
                : Collections.unmodifiableList(new ArrayList<Combinator>(combinators));
        this.text = text == null ? "" : text;
    }

    public List<CompoundSelector> compounds() { return compounds; }

    /** combinators[i] 连接 compounds[i] 与 compounds[i+1]。 */
    public Combinator combinator(int i) { return combinators.get(i); }

    public int combinatorCount() { return combinators.size(); }

    public String text() { return text; }
}
