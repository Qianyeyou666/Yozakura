package gq.yozakura.k.vendor.skidonion.sWdSl;

import java.nio.charset.StandardCharsets;
import java.util.BitSet;

public class FormUrlEncoder {
    static BitSet SAFE_CHARS = new BitSet(256);
    static final int SPACE = 32;
    static {
        for (char c = 'a'; c <= 'z'; c++) SAFE_CHARS.set(c);
        for (char c = 'A'; c <= 'Z'; c++) SAFE_CHARS.set(c);
        for (char c = '0'; c <= '9'; c++) SAFE_CHARS.set(c);
        SAFE_CHARS.set(SPACE);
        SAFE_CHARS.set('-'); SAFE_CHARS.set('_'); SAFE_CHARS.set('.'); SAFE_CHARS.set('*');
    }
    private FormUrlEncoder() {}

    public static String encode(String value) {
        StringBuilder result = new StringBuilder(value.length());
        boolean changed = false;
        for (int i = 0; i < value.length();) {
            char ch = value.charAt(i);
            if (ch < 256 && SAFE_CHARS.get(ch)) {
                if (ch == ' ') {
                    result.append('+');
                    changed = true;
                } else {
                    result.append(ch);
                }
                i++;
            } else {
                int start = i;
                do {
                    i++;
                    if (i >= value.length()) break;
                    ch = value.charAt(i);
                } while (!(ch < 256 && SAFE_CHARS.get(ch)));
                byte[] encoded = value.substring(start, i).getBytes(StandardCharsets.UTF_8);
                for (byte b : encoded) {
                    result.append('%');
                    char high = Character.forDigit((b >> 4) & 0xf, 16);
                    char low = Character.forDigit(b & 0xf, 16);
                    result.append(Character.toUpperCase(high));
                    result.append(Character.toUpperCase(low));
                }
                changed = true;
            }
        }
        return changed ? result.toString() : value;
    }
}

