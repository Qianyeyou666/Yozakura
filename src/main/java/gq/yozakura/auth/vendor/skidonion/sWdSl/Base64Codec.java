package gq.yozakura.auth.vendor.skidonion.sWdSl;

import java.nio.charset.StandardCharsets;

public class Base64Codec {
    public static String encodeToString(byte[] data) { return utf8String(encode(data)); }

    public static byte[] decode(String text) { return decodeBytes(utf8Bytes(text)); }

    public static final byte[] encode(byte[] data) {
        if (data == null) throw new IllegalArgumentException("Cannot encode null");
        byte[] out = new byte[((data.length + 2) / 3) * 4];
        int inputIndex = 0;
        int outputIndex = 0;
        while (inputIndex < data.length - 2) {
            int b0 = data[inputIndex++] & 0xff;
            int b1 = data[inputIndex++] & 0xff;
            int b2 = data[inputIndex++] & 0xff;
            out[outputIndex++] = (byte) (b0 >>> 2);
            out[outputIndex++] = (byte) (((b1 >>> 4) & 0x0f) | ((b0 << 4) & 0x3f));
            out[outputIndex++] = (byte) (((b2 >>> 6) & 0x03) | ((b1 << 2) & 0x3f));
            out[outputIndex++] = (byte) (b2 & 0x3f);
        }
        if (inputIndex < data.length) {
            int b0 = data[inputIndex] & 0xff;
            out[outputIndex++] = (byte) (b0 >>> 2);
            if (inputIndex < data.length - 1) {
                int b1 = data[inputIndex + 1] & 0xff;
                out[outputIndex++] = (byte) (((b1 >>> 4) & 0x0f) | ((b0 << 4) & 0x3f));
                out[outputIndex++] = (byte) ((b1 << 2) & 0x3f);
            } else {
                out[outputIndex++] = (byte) ((b0 << 4) & 0x3f);
            }
        }
        for (int i = 0; i < outputIndex; i++) {
            int value = out[i] & 0xff;
            if (value < 26) out[i] = (byte) (value + 'A');
            else if (value < 52) out[i] = (byte) (value - 26 + 'a');
            else if (value < 62) out[i] = (byte) (value - 52 + '0');
            else if (value < 63) out[i] = '+';
            else out[i] = '/';
        }
        while (outputIndex < out.length) out[outputIndex++] = '=';
        return out;
    }

    public static String utf8String(byte[] data) {
        if (data == null) throw new IllegalArgumentException("Cannot create string from null");
        return new String(data, StandardCharsets.UTF_8);
    }

    public static final byte[] utf8Bytes(String text) {
        if (text == null) throw new IllegalArgumentException("Cannot get bytes from null");
        return text.getBytes(StandardCharsets.UTF_8);
    }

    public static final byte[] decodeBytes(byte[] data) throws IllegalArgumentException {
        if (data == null) throw new IllegalArgumentException("Cannot decode null");
        int length = data.length;
        while (length > 0 && data[length - 1] == '=') length--;
        if (length - 1 == 0) return null;

        byte[] values = new byte[length];
        System.arraycopy(data, 0, values, 0, length);
        for (int i = 0; i < length; i++) {
            int value = values[i] & 0xff;
            if (value == '+') values[i] = 62;
            else if (value == '/') values[i] = 63;
            else if (value < ':') values[i] = (byte) (value + 52 - '0');
            else if (value < '[') values[i] = (byte) (value - 'A');
            else if (value < '{') values[i] = (byte) (value + 26 - 'a');
        }

        byte[] out = new byte[(length * 3) / 4];
        int inputIndex = 0;
        int outputIndex = 0;
        int full = (out.length / 3) * 3;
        while (inputIndex < length && outputIndex < full) {
            out[outputIndex++] = (byte) (((values[inputIndex] << 2) & 0xfc) | ((values[inputIndex + 1] >>> 4) & 0x03));
            out[outputIndex++] = (byte) (((values[inputIndex + 1] << 4) & 0xf0) | ((values[inputIndex + 2] >>> 2) & 0x0f));
            out[outputIndex++] = (byte) (((values[inputIndex + 2] << 6) & 0xc0) | (values[inputIndex + 3] & 0x3f));
            inputIndex += 4;
        }
        if (inputIndex < length) {
            if (inputIndex < length - 2) {
                out[outputIndex++] = (byte) (((values[inputIndex] << 2) & 0xfc) | ((values[inputIndex + 1] >>> 4) & 0x03));
                out[outputIndex] = (byte) (((values[inputIndex + 1] << 4) & 0xf0) | ((values[inputIndex + 2] >>> 2) & 0x0f));
            } else if (inputIndex < length - 1) {
                out[outputIndex] = (byte) (((values[inputIndex] << 2) & 0xfc) | ((values[inputIndex + 1] >>> 4) & 0x03));
            } else {
                throw new IllegalArgumentException("Invalid Base64 input length");
            }
        }
        return out;
    }
}

