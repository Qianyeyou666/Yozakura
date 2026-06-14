#version 120

// Adapted from OverShifted/LiquidGlass BatchRenderer2D.glsl.
// MIT License, Copyright (c) 2026 Sepehr Kalanaki.

uniform vec2 rectSize;
uniform vec4 fillColor;
uniform vec4 borderColor;
uniform sampler2D screenTex;
uniform vec2 screenSize;
uniform vec2 viewportSize;
uniform float radius;
uniform float borderWidth;
uniform float padding;
uniform float softness;
uniform float refraction;
uniform float highlight;
uniform float u_powerFactor;
uniform float u_fPower;
uniform float u_a;
uniform float u_b;
uniform float u_c;
uniform float u_d;
uniform float u_noise;
uniform float u_glowWeight;
uniform float u_glowBias;
uniform float u_glowEdge0;
uniform float u_glowEdge1;

const float EPSILON1 = 0.0000000000001;
const float EPSILON2 = 0.0001;
const float M_E = 2.718281828459045;

float sdSuperellipse(vec2 p, float n, float r) {
    vec2 pAbs = abs(p);
    float numerator = pow(pAbs.x, n) + pow(pAbs.y, n) - pow(r, n);
    float denX = pow(pAbs.x, 2.0 * n - 2.0);
    float denY = pow(pAbs.y, 2.0 * n - 2.0);
    float denominator = n * sqrt(denX + denY) + 0.00001;
    return numerator / denominator;
}

float roundedRectSDF(vec2 p, vec2 halfSize, float r) {
    vec2 q = abs(p) - halfSize + vec2(r);
    return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - r;
}

float f(float x) {
    return 1.0 - u_b * pow(u_c * M_E, -u_d * x - u_a);
}

float rand(vec2 co) {
    return fract(sin(dot(co, vec2(12.9898, 78.233))) * 43758.5453);
}

vec2 safeUv(vec2 uv) {
    return clamp(uv, vec2(0.0015), vec2(0.9985));
}

float glow(vec2 texCoord) {
    return sin(atan(texCoord.y * 2.0 - 1.0, texCoord.x * 2.0 - 1.0) - 0.5);
}

void main() {
    vec2 size = max(rectSize, vec2(0.001));
    vec2 drawSize = size + vec2(padding * 2.0);
    vec2 coord = gl_TexCoord[0].st * drawSize - vec2(padding);
    vec2 st = clamp(coord / size, vec2(0.0), vec2(1.0));
    vec2 p = st * 2.0 - 1.0;

    vec2 halfSize = size * 0.5;
    float r = min(radius, min(halfSize.x, halfSize.y));
    float maskDistance = roundedRectSDF(coord - halfSize, halfSize, r);
    float mask = 1.0 - smoothstep(0.0, softness, maskDistance);
    if (mask <= 0.0) {
        discard;
    }

    float d = sdSuperellipse(p, u_powerFactor, 1.0);
    float rectDist = clamp(-maskDistance / max(r, 1.0), 0.0, 1.0);
    float dist = max(max(-d, 0.0), rectDist);
    vec2 sampleP = p * pow(max(f(dist), EPSILON2), u_fPower);
    vec2 screenUv = safeUv(gl_FragCoord.xy / max(viewportSize, vec2(1.0)));
    vec2 quadScale = size / max(viewportSize, vec2(1.0)) * 0.5;
    vec2 centerUv = screenUv - p * quadScale;
    vec2 sourceUv = safeUv(centerUv + mix(p, sampleP, refraction) * quadScale);

    vec3 noise = vec3(rand(coord * 0.001) - 0.5);
    vec3 blurred = texture2D(screenTex, sourceUv).rgb + noise * u_noise;
    float tintWeight = clamp(fillColor.a * 0.10, 0.0, 0.10);
    vec3 color = mix(blurred, fillColor.rgb, tintWeight);
    float mul = glow(st) * u_glowWeight * highlight * smoothstep(u_glowEdge0, u_glowEdge1, dist)
            + 1.0 + u_glowBias;

    float visibility = clamp(max(fillColor.a, borderColor.a) / 0.65, 0.0, 1.0);
    float glassAlpha = 0.96 * visibility * mask;
    gl_FragColor = vec4(color * mul, glassAlpha);
}
