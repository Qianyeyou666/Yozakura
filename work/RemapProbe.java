import gq.yozakura.bridge.VanillaRemapClassLoader;
import java.io.File;
import java.net.URL;

public final class RemapProbe {
    public static void main(String[] args) throws Exception {
        URL jar = new File(args[0]).toURI().toURL();
        ClassLoader loader = new VanillaRemapClassLoader(
                new URL[]{jar}, Thread.currentThread().getContextClassLoader(), true);
        for (int index = 1; index < args.length; index++) {
            long started = System.nanoTime();
            Class<?> type = Class.forName(args[index], true, loader);
            type.getDeclaredConstructor().newInstance();
            long elapsedMillis = (System.nanoTime() - started) / 1_000_000L;
            System.out.println("instantiated " + args[index] + " in " + elapsedMillis + "ms");
        }
    }
}
