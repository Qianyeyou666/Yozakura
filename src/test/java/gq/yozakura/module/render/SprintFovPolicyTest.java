package gq.yozakura.module.render;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class SprintFovPolicyTest {
    @Test
    public void removesOnlyVanillaSprintContribution() {
        assertEquals(1.0F, SprintFovPolicy.withoutSprint(1.15F, 0.13D, 0.1F, true), 0.0001F);
        assertEquals(0.935F, SprintFovPolicy.withoutSprint(1.088F, 0.156D, 0.1F, true), 0.0001F);
    }

    @Test
    public void leavesNonSprintingFovUntouched() {
        assertEquals(0.82F, SprintFovPolicy.withoutSprint(0.82F, 0.1D, 0.1F, false), 0.0001F);
    }
}
