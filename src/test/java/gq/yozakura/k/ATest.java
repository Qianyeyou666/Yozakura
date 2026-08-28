package gq.yozakura.k;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ATest {
    @Test
    public void standaloneJarCannotCreateAVerifiedSessionWithoutNativeRegistration() {
        assertFalse(A.permitStartup());

        boolean rejected = false;
        try {
            A.login("test-user", "test-password".toCharArray());
        } catch (UnsatisfiedLinkError expected) {
            rejected = true;
        }
        assertTrue("The Java-only distribution must fail closed", rejected);
    }
}
