package gq.yozakura.auth;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class NativeAuthBridgeTest {
    @Test
    public void standaloneJarCannotCreateAVerifiedSessionWithoutNativeRegistration() {
        assertFalse(NativeAuthBridge.permitStartup());

        boolean rejected = false;
        try {
            NativeAuthBridge.login("test-user", "test-password".toCharArray());
        } catch (UnsatisfiedLinkError expected) {
            rejected = true;
        }
        assertTrue("The Java-only distribution must fail closed", rejected);
    }
}
