package gq.yozakura.auth;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class NativeAuthBridgeTest {
    @Test
    public void standaloneJarCannotCreateAVerifiedSessionWithoutNativeRegistration() {
        assertFalse(NativeAuthBridge.isVerifiedSession());

        boolean rejected = false;
        try {
            NativeAuthBridge.login("test-user", "test-password".toCharArray());
        } catch (IllegalStateException expected) {
            rejected = expected.getMessage().contains("native authentication runtime");
        }
        assertTrue("The Java-only distribution must fail closed", rejected);
    }
}
