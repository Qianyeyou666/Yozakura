package gq.yozakura.auth.vendor.skidonion.sWdSl;

import java.io.Writer;

final class MinimalJsonWriterConfig extends JsonWriterConfig {
    JsonWriter createWriter(Writer writer) { return new JsonWriter(writer); }
}

