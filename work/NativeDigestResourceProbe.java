import gq.yozakura.bridge.IsolatedClientClassLoader;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Paths;

public final class NativeDigestResourceProbe {
    public static void main(String[] args) throws Exception {
        URL jar = Paths.get(args[0]).toUri().toURL();
        try (IsolatedClientClassLoader loader = new IsolatedClientClassLoader(
                new URL[] { jar }, ClassLoader.getSystemClassLoader())) {
            Class<?> bridge = loader.loadClass("gq.yozakura.auth.NativeAuthBridge");
            ClassLoader definingLoader = bridge.getClassLoader();
            String[] resources = {
                    "gq/yozakura/auth/NativeAuthBridge.class",
                    "gq/yozakura/auth/YozakuraAuthGate.class",
                    "gq/yozakura/auth/vendor/tech/skidonion/obfuscator/inline/Wrapper.class",
                    "gq/yozakura/core/Client.class",
                    "gq/yozakura/core/StandaloneClient.class",
                    "gq/yozakura/core/ModernForgeClient.class",
                    "gq/yozakura/module/Module.class",
                    "gq/yozakura/event/bus/EventManager.class",
                    "gq/yozakura/bridge/MovementInputBridge.class",
                    "gq/yozakura/ui/click/yozakura/YozakuraClickGui.class"
            };
            for (String resource : resources) {
                int total = 0;
                try (InputStream input = definingLoader.getResourceAsStream(resource)) {
                    if (input == null) {
                        System.out.println("MISSING " + resource);
                        continue;
                    }
                    byte[] buffer = new byte[4096];
                    int read;
                    while ((read = input.read(buffer)) != -1) {
                        total += read;
                    }
                }
                System.out.println(total + " " + resource);
            }
        }
    }
}
