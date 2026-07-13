package gq.yozakura.bridge;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;

public class LunarRendererInputBridgeContractTest {
    @Test
    public void nameTagSpecialsEventsAreRemappedAndDispatched() throws IOException {
        String remapper = source("src/main/java/gq/yozakura/bridge/VanillaRemapClassLoader.java");
        String renderer = source("src/main/java/gq/yozakura/bridge/StandaloneLivingRendererBridge.java");

        assertTrue("The standalone loader must remap Forge's nested name-tag event family",
                remapper.contains("RenderLivingEvent$Specials")
                        && remapper.contains("RenderLivingEvent$Specials$Pre")
                        && remapper.contains("RenderLivingEvent$Specials$Post"));
        assertTrue("Standalone living renderers must dispatch the cancellable name-tag pre event",
                renderer.contains("dispatchSpecialsPre") && renderer.contains("Specials.Pre"));
        assertTrue("A cancelled name-tag event must suppress the delegate renderer's vanilla label",
                renderer.contains("specialsCancelled") && renderer.contains("suppressNameTag"));
    }

    @Test
    public void reinjectionReplacesRatherThanNestsOldLoaderWrappers() throws IOException {
        String renderer = source("src/main/java/gq/yozakura/bridge/StandaloneLivingRendererBridge.java");
        String movement = source("src/main/java/gq/yozakura/bridge/MovementInputBridge.java");

        assertTrue("Renderer wrappers from an old loader must be recognized by class name",
                renderer.contains("getClass().getName()") && renderer.contains("readDelegate(current)"));
        assertTrue("Movement input wrappers from an old loader must be unwrapped before takeover",
                movement.contains("unwrapMovementInput")
                        && movement.contains("getClass().getName()")
                        && movement.contains("readMovementDelegate"));
        assertTrue("The new movement hook must wrap the unwrapped vanilla delegate",
                movement.contains("new HookedMovementInput(delegate)"));
    }

    @Test
    public void playerRendererScopesVisualRotationToTheDelegateCall() throws IOException {
        String renderer = source("src/main/java/gq/yozakura/bridge/StandaloneLivingRendererBridge.java");

        int apply = renderer.indexOf("VisualRotationSnapshot.apply(entity)");
        int render = renderer.indexOf("delegate.doRender(entity, x, y, z, entityYaw, partialTicks)", apply);
        int restore = renderer.indexOf("rotationSnapshot.restore(entity)", render);
        assertTrue("Lunar's player renderer must see the published visual head/body rotation only while rendering",
                apply >= 0 && render > apply && restore > render);
    }

    @Test
    public void sprintPacketGatePublishesOneAtomicCrossThreadDecision() throws IOException {
        String movement = source("src/main/java/gq/yozakura/bridge/MovementInputBridge.java");

        assertTrue("The client thread must publish one sprint-block decision to Netty",
                movement.contains("private static volatile boolean blockSprintStartThisTick;"));
        assertFalse("Netty must not combine independently published movement flags",
                movement.contains("silentMovementThisTick")
                        || movement.contains("sprintAllowedThisTick"));
    }

    private static String source(String path) throws IOException {
        return new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.UTF_8);
    }
}
