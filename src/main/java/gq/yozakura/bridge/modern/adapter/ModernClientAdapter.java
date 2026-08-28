package gq.yozakura.bridge.modern.adapter;

import java.util.List;

public interface ModernClientAdapter {
    Object minecraft();

    Object player(Object minecraft);

    Object level(Object minecraft);

    Object font(Object minecraft);

    Object gameMode(Object minecraft);

    Object options(Object minecraft);

    Object connection(Object minecraft, Object player);

    Object connectionNetworkManager(Object connection);

    List<Object> entities(Object minecraft);

    List<Object> livingEntities(Object minecraft);
}
