package gq.vapulite.value;

public class Numbers<T extends Number> extends Value<T> {
    public T min;
    public T max;
    public T inc;
    private String name;
    private final boolean integer;

    public Numbers(String displayName, String name, T value, T min, T max, T inc) {
        super(displayName, name);
        setValue(value);
        this.min = min;
        this.max = max;
        this.inc = inc;
        this.integer = false;
    }

    public T getMinimum() {
        return this.min;
    }

    public T getMaximum() {
        return this.max;
    }

    public T getIncrement() {
        return this.inc;
    }

    public void setIncrement(T inc) {
        this.inc = inc;
    }

    @SuppressWarnings("unchecked")
    public void setNumberValue(double value) {
        Number typeHint = getTypeHint();
        if (typeHint instanceof Float) {
            setValue((T) Float.valueOf((float) value));
        } else if (typeHint instanceof Integer) {
            setValue((T) Integer.valueOf((int) Math.round(value)));
        } else if (typeHint instanceof Long) {
            setValue((T) Long.valueOf(Math.round(value)));
        } else if (typeHint instanceof Short) {
            setValue((T) Short.valueOf((short) Math.round(value)));
        } else if (typeHint instanceof Byte) {
            setValue((T) Byte.valueOf((byte) Math.round(value)));
        } else {
            setValue((T) Double.valueOf(value));
        }
    }

    private Number getTypeHint() {
        Number current = getValue();
        if (current != null) {
            return current;
        }
        if (min != null) {
            return min;
        }
        if (max != null) {
            return max;
        }
        return inc;
    }

    public String getId() {
        return this.name;
    }

    public boolean isInteger() {
        return false;
    }
}
