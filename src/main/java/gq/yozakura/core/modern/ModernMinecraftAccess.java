package gq.yozakura.core.modern;

import gq.yozakura.core.modern.adapter.ModernClientAdapter;

import java.util.List;

final class ModernMinecraftAccess {
    private ModernMinecraftAccess() {
    }

    static Object minecraft() {
        return adapter().minecraft();
    }

    static Object player(Object minecraft) {
        return adapter().player(minecraft);
    }

    static Object level(Object minecraft) {
        return adapter().level(minecraft);
    }

    static Object font(Object minecraft) {
        return adapter().font(minecraft);
    }

    static Object gameMode(Object minecraft) {
        return adapter().gameMode(minecraft);
    }

    static Object options(Object minecraft) {
        return adapter().options(minecraft);
    }

    static Object connection(Object minecraft, Object player) {
        return adapter().connection(minecraft, player);
    }

    static Object connectionNetworkManager(Object connection) {
        return adapter().connectionNetworkManager(connection);
    }

    static List<Object> livingEntities(Object minecraft) {
        return adapter().livingEntities(minecraft);
    }

    private static ModernClientAdapter adapter() {
        return ModernClientAdapters.get();
    }
}
