package gq.yozakura.core.modern;

import gq.yozakura.core.modern.adapter.ModernClientAdapter;

final class ModernClientAdapters {
    private static final ModernClientAdapter ADAPTER = create();

    private ModernClientAdapters() {
    }

    static ModernClientAdapter get() {
        return ADAPTER;
    }

    private static ModernClientAdapter create() {
        try {
            Class<?> type = ModernForgeEventBridge.findClass("gq.yozakura.core.modern.adapter.impl.Modern1201ClientAdapter");
            Object value = type.getDeclaredConstructor().newInstance();
            if (value instanceof ModernClientAdapter) {
                return (ModernClientAdapter) value;
            }
        } catch (Throwable throwable) {
            ModernForgeEventBridge.log("Modern 1.20.1 client adapter unavailable, using reflection bridge", throwable);
        }
        return new ReflectionModernClientAdapter();
    }
}
