package gq.yozakura.bridge;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class EventManagerConcurrencyContractTest {
    @Test
    public void nettyDispatchCanReadWhileTheClientThreadRegistersModules() throws IOException {
        String source = source("src/main/java/gq/yozakura/event/bus/EventManager.java");

        assertTrue("The event-class registry must publish updates safely to Netty",
                source.contains("ConcurrentHashMap<Class<?>, List<MethodData>>"));
        assertTrue("Registration must atomically copy, deduplicate, sort, and replace one event snapshot",
                source.contains("REGISTRY.compute(eventClass"));
        assertTrue("Unregistration must atomically replace or remove the same event snapshot",
                source.contains("REGISTRY.computeIfPresent(eventClass"));
        assertFalse("ConcurrentHashMap iteration must not depend on iterator.remove",
                source.contains("iterator.remove();"));
        assertTrue("Hot event dispatch must use allocation-free indexed snapshot traversal",
                source.contains("for (int i = 0; i < list.size(); i++)"));
        assertFalse("Hot event dispatch must not allocate a foreach iterator",
                source.contains("for (MethodData data : list)"));
    }

    private static String source(String path) throws IOException {
        return new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.UTF_8);
    }
}
