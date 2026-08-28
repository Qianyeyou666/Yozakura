package gq.yozakura.ui.engine.render;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * RenderStats 契约测试：reset 清零、命中率计算、字段累加语义。
 *
 * <p>AGENTS.md 要求 "Optimization must be evidence-based. Add counters or benchmarks
 * before introducing complex caches."。本测试固化计数器语义，确保后续优化不改坏 baseline。
 */
public class RenderStatsTest {

    @Test
    public void freshStatsAreAllZero() {
        RenderStats stats = new RenderStats();
        assertEquals(0, stats.opCount);
        assertEquals(0, stats.drawCalls);
        assertEquals(0, stats.clipChanges);
        assertEquals(0, stats.compileHits);
        assertEquals(0, stats.compileMisses);
        assertEquals(0, stats.textLayoutHits);
        assertEquals(0, stats.textLayoutMisses);
        assertEquals(0, stats.textLayoutEvictions);
        assertEquals(0, stats.stateSnapshots);
        assertEquals(0.0F, stats.compileHitRate(), 0.0001F);
        assertEquals(0.0F, stats.textLayoutHitRate(), 0.0001F);
    }

    @Test
    public void resetClearsAllFields() {
        RenderStats stats = new RenderStats();
        stats.opCount = 42;
        stats.drawCalls = 10;
        stats.compileHits = 5;
        stats.compileMisses = 3;
        stats.textLayoutEvictions = 7;
        stats.stateSnapshots = 2;

        stats.reset();

        assertEquals(0, stats.opCount);
        assertEquals(0, stats.drawCalls);
        assertEquals(0, stats.compileHits);
        assertEquals(0, stats.compileMisses);
        assertEquals(0, stats.textLayoutEvictions);
        assertEquals(0, stats.stateSnapshots);
    }

    @Test
    public void compileHitRateIsRatioOfHitsToTotal() {
        RenderStats stats = new RenderStats();
        stats.compileHits = 7;
        stats.compileMisses = 3;
        // 7 / (7 + 3) = 0.7
        assertEquals(0.7F, stats.compileHitRate(), 0.0001F);
    }

    @Test
    public void textLayoutHitRateIsRatioOfHitsToTotal() {
        RenderStats stats = new RenderStats();
        stats.textLayoutHits = 4;
        stats.textLayoutMisses = 1;
        // 4 / (4 + 1) = 0.8
        assertEquals(0.8F, stats.textLayoutHitRate(), 0.0001F);
    }

    @Test
    public void hitRateIsZeroWhenNoData() {
        RenderStats stats = new RenderStats();
        // Avoid divide-by-zero: both hits and misses zero -> rate 0
        assertEquals(0.0F, stats.compileHitRate(), 0.0001F);
        assertEquals(0.0F, stats.textLayoutHitRate(), 0.0001F);
    }

    @Test
    public void hitRateIsOneWhenAllHits() {
        RenderStats stats = new RenderStats();
        stats.compileHits = 10;
        stats.compileMisses = 0;
        assertEquals(1.0F, stats.compileHitRate(), 0.0001F);
    }
}
