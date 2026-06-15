package gq.vapulite.engine.render.ui;

public final class LiquidGlassSettings {
    private static final LiquidGlassSettings DEFAULT = LiquidGlassSettings.of(
            2,      // blurIterations: 横向+纵向 blur 的迭代次数，越高越平滑但越耗性能。
            4.0f,   // blurRadius: blur 半径，越大背景越糊。
            0.85f,  // blurDownscale: blur 工作贴图分辨率比例，越接近 1.0 越不容易出方块。
            0.03f,  // noise: 静态磨砂颗粒强度。
            3.2f,   // powerFactor: 超椭圆折射形状强度，影响边缘/中心过渡。
            1.0f,   // refractionPower: 折射曲线幂次。
            0.7f,   // refractionA: 折射曲线偏移项。
            2.3f,   // refractionB: 折射曲线幅度项。
            5.2f,   // refractionC: 折射曲线指数基数。
            6.9f,   // refractionD: 折射曲线衰减速度。
            1.8f,   // refractionScale: 背景采样偏移强度，越大扭曲越明显。
            0.3f,   // glowWeight: 角向高光/暗角权重。
            0.0f,   // glowBias: 整体亮度偏移。
            0.06f,  // glowEdge0: 高光边缘 smoothstep 起点。
            0.0f,   // glowEdge1: 高光边缘 smoothstep 终点。
            1.2f);  // highlight: 总体高光强度。

    private final int blurIterations;
    private final float blurRadius;
    private final float blurDownscale;
    private final float noise;
    private final float powerFactor;
    private final float refractionPower;
    private final float refractionA;
    private final float refractionB;
    private final float refractionC;
    private final float refractionD;
    private final float refractionScale;
    private final float glowWeight;
    private final float glowBias;
    private final float glowEdge0;
    private final float glowEdge1;
    private final float highlight;

    /**
     * Creates a complete LiquidGlass parameter set.
     *
     * @param blurIterations horizontal+vertical blur iteration count; higher is smoother and slower.
     * @param blurRadius blur radius in source pixels.
     * @param blurDownscale blur framebuffer size multiplier, where 1.0 keeps full resolution.
     * @param noise static frosted-grain strength.
     * @param powerFactor superellipse/refraction shape factor.
     * @param refractionPower refraction curve exponent.
     * @param refractionA refraction curve offset.
     * @param refractionB refraction curve amplitude.
     * @param refractionC refraction curve exponential base.
     * @param refractionD refraction curve falloff speed.
     * @param refractionScale screen texture sample offset strength.
     * @param glowWeight angular highlight and shadow weight.
     * @param glowBias global brightness bias applied after glow.
     * @param glowEdge0 smoothstep lower edge for glow visibility.
     * @param glowEdge1 smoothstep upper edge for glow visibility.
     * @param highlight global highlight multiplier.
     */
    public static LiquidGlassSettings of(int blurIterations, float blurRadius, float blurDownscale, float noise,
                                         float powerFactor, float refractionPower, float refractionA,
                                         float refractionB, float refractionC, float refractionD,
                                         float refractionScale, float glowWeight, float glowBias,
                                         float glowEdge0, float glowEdge1, float highlight) {
        return new LiquidGlassSettings(blurIterations, blurRadius, blurDownscale, noise,
                powerFactor, refractionPower, refractionA, refractionB, refractionC,
                refractionD, refractionScale, glowWeight, glowBias, glowEdge0,
                glowEdge1, highlight);
    }

    private LiquidGlassSettings(int blurIterations, float blurRadius, float blurDownscale, float noise,
                                float powerFactor, float refractionPower, float refractionA,
                                float refractionB, float refractionC, float refractionD,
                                float refractionScale, float glowWeight, float glowBias,
                                float glowEdge0, float glowEdge1, float highlight) {
        this.blurIterations = blurIterations;
        this.blurRadius = blurRadius;
        this.blurDownscale = blurDownscale;
        this.noise = noise;
        this.powerFactor = powerFactor;
        this.refractionPower = refractionPower;
        this.refractionA = refractionA;
        this.refractionB = refractionB;
        this.refractionC = refractionC;
        this.refractionD = refractionD;
        this.refractionScale = refractionScale;
        this.glowWeight = glowWeight;
        this.glowBias = glowBias;
        this.glowEdge0 = glowEdge0;
        this.glowEdge1 = glowEdge1;
        this.highlight = highlight;
    }

    public static LiquidGlassSettings defaults() {
        return DEFAULT;
    }

    public LiquidGlassSettings withBlurIterations(int value) {
        return copy(value, blurRadius, blurDownscale, noise, powerFactor, refractionPower,
                refractionA, refractionB, refractionC, refractionD, refractionScale,
                glowWeight, glowBias, glowEdge0, glowEdge1, highlight);
    }

    public LiquidGlassSettings withBlurRadius(float value) {
        return copy(blurIterations, value, blurDownscale, noise, powerFactor, refractionPower,
                refractionA, refractionB, refractionC, refractionD, refractionScale,
                glowWeight, glowBias, glowEdge0, glowEdge1, highlight);
    }

    public LiquidGlassSettings withBlurDownscale(float value) {
        return copy(blurIterations, blurRadius, value, noise, powerFactor, refractionPower,
                refractionA, refractionB, refractionC, refractionD, refractionScale,
                glowWeight, glowBias, glowEdge0, glowEdge1, highlight);
    }

    public LiquidGlassSettings withNoise(float value) {
        return copy(blurIterations, blurRadius, blurDownscale, value, powerFactor, refractionPower,
                refractionA, refractionB, refractionC, refractionD, refractionScale,
                glowWeight, glowBias, glowEdge0, glowEdge1, highlight);
    }

    public LiquidGlassSettings withPowerFactor(float value) {
        return copy(blurIterations, blurRadius, blurDownscale, noise, value, refractionPower,
                refractionA, refractionB, refractionC, refractionD, refractionScale,
                glowWeight, glowBias, glowEdge0, glowEdge1, highlight);
    }

    public LiquidGlassSettings withRefractionPower(float value) {
        return copy(blurIterations, blurRadius, blurDownscale, noise, powerFactor, value,
                refractionA, refractionB, refractionC, refractionD, refractionScale,
                glowWeight, glowBias, glowEdge0, glowEdge1, highlight);
    }

    public LiquidGlassSettings withRefractionCurve(float a, float b, float c, float d) {
        return copy(blurIterations, blurRadius, blurDownscale, noise, powerFactor, refractionPower,
                a, b, c, d, refractionScale, glowWeight, glowBias, glowEdge0, glowEdge1, highlight);
    }

    public LiquidGlassSettings withRefractionA(float value) {
        return withRefractionCurve(value, refractionB, refractionC, refractionD);
    }

    public LiquidGlassSettings withRefractionB(float value) {
        return withRefractionCurve(refractionA, value, refractionC, refractionD);
    }

    public LiquidGlassSettings withRefractionC(float value) {
        return withRefractionCurve(refractionA, refractionB, value, refractionD);
    }

    public LiquidGlassSettings withRefractionD(float value) {
        return withRefractionCurve(refractionA, refractionB, refractionC, value);
    }

    public LiquidGlassSettings withRefractionScale(float value) {
        return copy(blurIterations, blurRadius, blurDownscale, noise, powerFactor, refractionPower,
                refractionA, refractionB, refractionC, refractionD, value,
                glowWeight, glowBias, glowEdge0, glowEdge1, highlight);
    }

    public LiquidGlassSettings withGlow(float weight, float bias, float edge0, float edge1) {
        return copy(blurIterations, blurRadius, blurDownscale, noise, powerFactor, refractionPower,
                refractionA, refractionB, refractionC, refractionD, refractionScale,
                weight, bias, edge0, edge1, highlight);
    }

    public LiquidGlassSettings withGlowWeight(float value) {
        return withGlow(value, glowBias, glowEdge0, glowEdge1);
    }

    public LiquidGlassSettings withGlowBias(float value) {
        return withGlow(glowWeight, value, glowEdge0, glowEdge1);
    }

    public LiquidGlassSettings withGlowEdge0(float value) {
        return withGlow(glowWeight, glowBias, value, glowEdge1);
    }

    public LiquidGlassSettings withGlowEdge1(float value) {
        return withGlow(glowWeight, glowBias, glowEdge0, value);
    }

    public LiquidGlassSettings withHighlight(float value) {
        return copy(blurIterations, blurRadius, blurDownscale, noise, powerFactor, refractionPower,
                refractionA, refractionB, refractionC, refractionD, refractionScale,
                glowWeight, glowBias, glowEdge0, glowEdge1, value);
    }

    public int blurIterations() {
        return blurIterations;
    }

    public float blurRadius() {
        return blurRadius;
    }

    public float blurDownscale() {
        return blurDownscale;
    }

    public float noise() {
        return noise;
    }

    public float powerFactor() {
        return powerFactor;
    }

    public float refractionPower() {
        return refractionPower;
    }

    public float refractionA() {
        return refractionA;
    }

    public float refractionB() {
        return refractionB;
    }

    public float refractionC() {
        return refractionC;
    }

    public float refractionD() {
        return refractionD;
    }

    public float refractionScale() {
        return refractionScale;
    }

    public float glowWeight() {
        return glowWeight;
    }

    public float glowBias() {
        return glowBias;
    }

    public float glowEdge0() {
        return glowEdge0;
    }

    public float glowEdge1() {
        return glowEdge1;
    }

    public float highlight() {
        return highlight;
    }

    private LiquidGlassSettings copy(int blurIterations, float blurRadius, float blurDownscale, float noise,
                                     float powerFactor, float refractionPower, float refractionA,
                                     float refractionB, float refractionC, float refractionD,
                                     float refractionScale, float glowWeight, float glowBias,
                                     float glowEdge0, float glowEdge1, float highlight) {
        return new LiquidGlassSettings(blurIterations, blurRadius, blurDownscale, noise,
                powerFactor, refractionPower, refractionA, refractionB, refractionC,
                refractionD, refractionScale, glowWeight, glowBias, glowEdge0,
                glowEdge1, highlight);
    }
}
