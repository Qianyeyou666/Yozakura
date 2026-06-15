package gq.yozakura.auth.vendor.skidonion.sWdSl;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

public class ChaChaStream {
    private static final byte[] SIGMA = "expand 32-byte k".getBytes(StandardCharsets.US_ASCII);
    private final int[] workingState = new int[16];
    private final byte[] keyStream = new byte[64];
    private final AtomicInteger position;
    private long counter;
    private final byte[] nonce;
    private final int[] state = new int[16];

    public ChaChaStream(byte[] key, byte[] nonce, long counter) {
        if (key.length < 32) throw new IllegalArgumentException("key must be 32 bytes");
        if (nonce.length < 12) throw new IllegalArgumentException("nonce must be 12 bytes");
        state[0] = load32(SIGMA, 0); state[1] = load32(SIGMA, 4); state[2] = load32(SIGMA, 8); state[3] = load32(SIGMA, 12);
        for (int i = 0; i < 8; i++) state[4 + i] = load32(key, i * 4);
        state[12] = (int) (counter & 0xffffffffL);
        state[13] = load32(nonce, 0) + (int) (counter >>> 32);
        state[14] = load32(nonce, 4);
        state[15] = load32(nonce, 8);
        this.counter = counter;
        this.nonce = nonce.clone();
        this.position = new AtomicInteger(64);
    }

    private static int rotateLeft(int value, int bits) { return (value << bits) | (value >>> (32 - bits)); }
    private static int load32(byte[] data, int off) {
        return (data[off] & 0xff) | ((data[off + 1] & 0xff) << 8) | ((data[off + 2] & 0xff) << 16) | ((data[off + 3] & 0xff) << 24);
    }
    private static void store32(int value, byte[] out, int off) {
        out[off] = (byte) value;
        out[off + 1] = (byte) (value >>> 8);
        out[off + 2] = (byte) (value >>> 16);
        out[off + 3] = (byte) (value >>> 24);
    }
    private static void quarterRound(int[] state, int a, int b, int c, int d) {
        state[a] += state[b]; state[d] = rotateLeft(state[d] ^ state[a], 16);
        state[c] += state[d]; state[b] = rotateLeft(state[b] ^ state[c], 12);
        state[a] += state[b]; state[d] = rotateLeft(state[d] ^ state[a], 8);
        state[c] += state[d]; state[b] = rotateLeft(state[b] ^ state[c], 7);
    }
    private void generateBlock() {
        System.arraycopy(state, 0, workingState, 0, 16);
        for (int i = 0; i < 10; i++) {
            quarterRound(workingState, 0, 4, 8, 12); quarterRound(workingState, 1, 5, 9, 13); quarterRound(workingState, 2, 6, 10, 14); quarterRound(workingState, 3, 7, 11, 15);
            quarterRound(workingState, 0, 5, 10, 15); quarterRound(workingState, 1, 6, 11, 12); quarterRound(workingState, 2, 7, 8, 13); quarterRound(workingState, 3, 4, 9, 14);
        }
        for (int i = 0; i < 16; i++) store32(workingState[i] + state[i], keyStream, i * 4);
        state[12]++;
        if (state[12] == 0) state[13]++;
        counter++;
        position.set(0);
    }
    public byte[] apply(byte[] input) {
        synchronized (position) {
            byte[] out = new byte[input.length];
            for (int i = 0; i < input.length; i++) {
                if (position.get() >= 64) generateBlock();
                out[i] = (byte) (input[i] ^ keyStream[position.getAndIncrement()]);
            }
            return out;
        }
    }

    public void skip(long bytes) {
        synchronized (position) {
            for (long i = 0; i < bytes; i++) {
                if (position.get() >= 64) generateBlock();
                position.getAndIncrement();
            }
        }
    }

    public long getCounter() { return counter; }

    public void setCounter(long counter) {
        this.counter = counter;
        state[12] = (int) counter;
        state[13] = load32(nonce, 0) + (int) (counter >>> 32);
        position.set(64);
    }
}

