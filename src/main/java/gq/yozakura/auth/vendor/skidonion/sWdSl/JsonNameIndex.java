package gq.yozakura.auth.vendor.skidonion.sWdSl;

class JsonNameIndex {
    private final byte[] slots;

    JsonNameIndex() { this.slots = new byte[32]; }

    JsonNameIndex(JsonNameIndex other) {
        this();
        System.arraycopy(other.slots, 0, this.slots, 0, this.slots.length);
    }

    void put(String name, int index) {
        int slot = slot(name);
        slots[slot] = index < 255 ? (byte) (index + 1) : 0;
    }

    void remove(int index) {
        for (int i = 0; i < slots.length; i++) {
            int value = slots[i] & 0xff;
            if (value == index + 1) {
                slots[i] = 0;
            } else if (value > index + 1) {
                slots[i] = (byte) (value - 1);
            }
        }
    }

    int get(Object value) {
        return (slots[slot(value)] & 0xff) - 1;
    }

    private int slot(Object value) {
        return value.hashCode() & (slots.length - 1);
    }
}

