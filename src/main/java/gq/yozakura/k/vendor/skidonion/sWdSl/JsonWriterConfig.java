package gq.yozakura.k.vendor.skidonion.sWdSl;

import java.io.Writer;

public abstract class JsonWriterConfig {
    public static final JsonWriterConfig MINIMAL = new MinimalJsonWriterConfig();
    abstract JsonWriter createWriter(Writer writer);
}

