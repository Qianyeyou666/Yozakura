package gq.yozakura.bridge;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class MoveFixResolverTest {
    @Test
    public void keepsForwardMotionWhenTheServerYawTurnsRight() {
        MoveFixResolver.ResolvedInput resolved = MoveFixResolver.resolve(0.0F, 90.0F, 1.0F, 0.0F);

        assertEquals(0.0F, resolved.getForward(), 0.00001F);
        assertEquals(1.0F, resolved.getStrafe(), 0.00001F);
    }

    @Test
    public void preservesTheSampledSneakMagnitude() {
        MoveFixResolver.ResolvedInput resolved = MoveFixResolver.resolve(0.0F, 90.0F, 0.3F, 0.0F);

        assertEquals(0.0F, resolved.getForward(), 0.00001F);
        assertEquals(0.3F, resolved.getStrafe(), 0.00001F);
    }

    @Test
    public void preservesTheSampledSneakMagnitudeForDiagonalInput() {
        MoveFixResolver.ResolvedInput resolved = MoveFixResolver.resolve(0.0F, 90.0F, 0.3F, 0.3F);

        assertEquals(-0.3F, resolved.getForward(), 0.00001F);
        assertEquals(0.3F, resolved.getStrafe(), 0.00001F);
    }

    @Test
    public void preservesSneakSpeedWhenAForwardInputMapsToADiagonal() {
        MoveFixResolver.ResolvedInput resolved = MoveFixResolver.resolve(0.0F, 45.0F, 0.3F, 0.0F);
        float diagonalAxis = (float) (0.3D / Math.sqrt(2.0D));

        assertEquals(diagonalAxis, resolved.getForward(), 0.00001F);
        assertEquals(diagonalAxis, resolved.getStrafe(), 0.00001F);
    }

    @Test
    public void preservesSneakSpeedWhenADiagonalInputMapsToForward() {
        MoveFixResolver.ResolvedInput resolved = MoveFixResolver.resolve(0.0F, -45.0F, 0.3F, 0.3F);
        float cardinalAxis = (float) (0.3D * Math.sqrt(2.0D));

        assertEquals(cardinalAxis, resolved.getForward(), 0.00001F);
        assertEquals(0.0F, resolved.getStrafe(), 0.00001F);
    }

    @Test
    public void retainsFullSpeedWhenAHighMagnitudeInputMapsToOneAxis() {
        MoveFixResolver.ResolvedInput resolved = MoveFixResolver.resolve(0.0F, 45.0F, 0.8F, 0.8F);

        assertEquals(0.0F, resolved.getForward(), 0.00001F);
        assertEquals(1.0F, resolved.getStrafe(), 0.00001F);
    }

    @Test
    public void handlesYawWrappingWithoutReversingForwardInput() {
        MoveFixResolver.ResolvedInput resolved = MoveFixResolver.resolve(179.0F, -179.0F, 1.0F, 0.0F);

        assertEquals(1.0F, resolved.getForward(), 0.00001F);
        assertEquals(0.0F, resolved.getStrafe(), 0.00001F);
    }

    @Test
    public void leavesStationaryInputUntouched() {
        MoveFixResolver.ResolvedInput resolved = MoveFixResolver.resolve(15.0F, 175.0F, 0.0F, 0.0F);

        assertEquals(0.0F, resolved.getForward(), 0.00001F);
        assertEquals(0.0F, resolved.getStrafe(), 0.00001F);
    }
}
