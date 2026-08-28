package gq.yozakura.ui.engine.paint;

/** Retained text draw command. The y coordinate is the text baseline. */
public final class TextPaintCommand extends PaintCommand {
    public static final int ALIGN_LEFT = 0;
    public static final int ALIGN_CENTER = 1;
    public static final int ALIGN_RIGHT = 2;

    private final String text;
    private final float x;
    private final float y;
    private final float fontSize;
    private final String fontFamily;
    private final boolean bold;
    private final Color color;
    private final int alignment;
    private final float availableWidth;

    public TextPaintCommand(String text, float x, float y, float fontSize,
                            String fontFamily, boolean bold, Color color) {
        this(text, x, y, fontSize, fontFamily, bold, color, ALIGN_LEFT, 0.0F);
    }

    public TextPaintCommand(String text, float x, float y, float fontSize,
                            String fontFamily, boolean bold, Color color,
                            int alignment, float availableWidth) {
        if (text == null || fontFamily == null || fontFamily.trim().isEmpty() || color == null) {
            throw new IllegalArgumentException("text, fontFamily and color must be present");
        }
        if (fontSize <= 0.0F) {
            throw new IllegalArgumentException("fontSize must be positive: " + fontSize);
        }
        if (alignment < ALIGN_LEFT || alignment > ALIGN_RIGHT) {
            throw new IllegalArgumentException("unsupported text alignment: " + alignment);
        }
        if (availableWidth < 0.0F) {
            throw new IllegalArgumentException("availableWidth must not be negative: " + availableWidth);
        }
        this.text = text;
        this.x = x;
        this.y = y;
        this.fontSize = fontSize;
        this.fontFamily = fontFamily;
        this.bold = bold;
        this.color = color;
        this.alignment = alignment;
        this.availableWidth = availableWidth;
    }

    public String text() { return text; }
    public float x() { return x; }
    public float y() { return y; }
    public float fontSize() { return fontSize; }
    public String fontFamily() { return fontFamily; }
    public boolean bold() { return bold; }
    public Color color() { return color; }
    public int alignment() { return alignment; }
    public float availableWidth() { return availableWidth; }

    public float alignedX(float textWidth) {
        float remaining = Math.max(0.0F, availableWidth - Math.max(0.0F, textWidth));
        if (alignment == ALIGN_CENTER) return x + remaining * 0.5F;
        if (alignment == ALIGN_RIGHT) return x + remaining;
        return x;
    }

    @Override
    public boolean equals(Object value) {
        if (this == value) return true;
        if (!(value instanceof TextPaintCommand)) return false;
        TextPaintCommand other = (TextPaintCommand) value;
        return text.equals(other.text)
                && Float.floatToIntBits(x) == Float.floatToIntBits(other.x)
                && Float.floatToIntBits(y) == Float.floatToIntBits(other.y)
                && Float.floatToIntBits(fontSize) == Float.floatToIntBits(other.fontSize)
                && fontFamily.equals(other.fontFamily)
                && bold == other.bold
                && sameColor(color, other.color)
                && alignment == other.alignment
                && Float.floatToIntBits(availableWidth) == Float.floatToIntBits(other.availableWidth);
    }

    @Override
    public int hashCode() {
        int result = text.hashCode();
        result = 31 * result + Float.floatToIntBits(x);
        result = 31 * result + Float.floatToIntBits(y);
        result = 31 * result + Float.floatToIntBits(fontSize);
        result = 31 * result + fontFamily.hashCode();
        result = 31 * result + (bold ? 1 : 0);
        result = 31 * result + Float.floatToIntBits(color.r());
        result = 31 * result + Float.floatToIntBits(color.g());
        result = 31 * result + Float.floatToIntBits(color.b());
        result = 31 * result + Float.floatToIntBits(color.a());
        result = 31 * result + alignment;
        result = 31 * result + Float.floatToIntBits(availableWidth);
        return result;
    }

    private static boolean sameColor(Color left, Color right) {
        return Float.floatToIntBits(left.r()) == Float.floatToIntBits(right.r())
                && Float.floatToIntBits(left.g()) == Float.floatToIntBits(right.g())
                && Float.floatToIntBits(left.b()) == Float.floatToIntBits(right.b())
                && Float.floatToIntBits(left.a()) == Float.floatToIntBits(right.a());
    }

    @Override
    public int type() { return TYPE_TEXT; }

    @Override
    public void accept(PaintCommandVisitor visitor) { visitor.visitText(this); }
}
