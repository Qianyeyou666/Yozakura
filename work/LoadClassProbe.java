public final class LoadClassProbe {
    public static void main(String[] args) throws Exception {
        for (String name : args) {
            long started = System.nanoTime();
            Class.forName(name, true, Thread.currentThread().getContextClassLoader());
            long elapsedMillis = (System.nanoTime() - started) / 1_000_000L;
            System.out.println("loaded " + name + " in " + elapsedMillis + "ms");
        }
    }
}
