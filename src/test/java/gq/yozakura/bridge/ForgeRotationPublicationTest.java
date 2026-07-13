package gq.yozakura.bridge;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class ForgeRotationPublicationTest {
    @Test
    public void finalRotationReplacesTheInProgressSnapshotAtomically() {
        ForgeRotationPublication publication = new ForgeRotationPublication();

        ForgeRotationPublication.Snapshot inProgress = publication.beginPre();
        ForgeRotationPublication.Snapshot published = publication.publish(true, 91.25F, -17.5F);

        assertTrue(inProgress.isPreInProgress());
        assertFalse(inProgress.isActive());
        assertFalse(published.isPreInProgress());
        assertTrue(published.isActive());
        assertEquals(91.25F, published.getYaw(), 0.0F);
        assertEquals(-17.5F, published.getPitch(), 0.0F);
        assertEquals(inProgress.getGeneration(), published.getGeneration());
        assertSame(published, publication.snapshot());
    }

    @Test
    public void sendingAnOldSnapshotDoesNotReleaseANewerGeneration() {
        ForgeRotationPublication publication = new ForgeRotationPublication();
        publication.beginPre();
        ForgeRotationPublication.Snapshot first = publication.publish(true, 10.0F, 20.0F);
        publication.beginPre();
        ForgeRotationPublication.Snapshot second = publication.publish(true, 30.0F, 40.0F);

        publication.markSent(first);

        assertTrue(publication.isGenerationSent(first.getGeneration()));
        assertFalse(publication.isGenerationSent(second.getGeneration()));
        assertEquals(first.getGeneration(), publication.getSentGeneration());
    }

    @Test(expected = IllegalStateException.class)
    public void publishRequiresAnExplicitPreGeneration() {
        new ForgeRotationPublication().publish(true, 1.0F, 2.0F);
    }
}
