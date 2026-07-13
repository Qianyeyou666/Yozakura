package gq.yozakura.bridge;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class StandaloneRotationPublicationTest {
    @Test
    public void publishesCompleteImmutableSnapshots() {
        StandaloneRotationPublication publication = new StandaloneRotationPublication();

        assertFalse(publication.snapshot().isActive());

        publication.publish(true, 35.5F, -12.25F);
        StandaloneRotationPublication.Snapshot first = publication.snapshot();
        assertTrue(publication.hasUnsentRotation());
        publication.markSent(first);
        assertFalse(publication.hasUnsentRotation());

        publication.publish(true, -80.0F, 44.0F);
        StandaloneRotationPublication.Snapshot second = publication.snapshot();
        assertTrue(publication.hasUnsentRotation());

        assertTrue(first.isActive());
        assertEquals(35.5F, first.getYaw(), 0.0F);
        assertEquals(-12.25F, first.getPitch(), 0.0F);
        assertTrue(second.isActive());
        assertEquals(-80.0F, second.getYaw(), 0.0F);
        assertEquals(44.0F, second.getPitch(), 0.0F);

        publication.markSent(first);
        assertTrue("Acknowledging an older C03 must not consume the newer rotation", publication.hasUnsentRotation());
        publication.markSent(second);
        assertFalse(publication.hasUnsentRotation());

        publication.clear();
        assertFalse(publication.snapshot().isActive());
    }
}
