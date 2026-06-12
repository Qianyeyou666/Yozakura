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
uniform float blurRadius;
uniform float refraction;
uniform float highlight;
uniform float grainStrength;
uniform float time;

const float EPSILON1 = 0.0000000000001;
const float EPSILON2 = 0.0001;
const float M_E = 2.718281828459045;

const float U_A = 0.7;
const float U_B = 2.3;
const float U_C = 5.2;
const float U_D = 6.9;
const float U_F_POWER = 3.0;
const float U_POWER_FACTOR = 4.0;
const float U_NOISE = 0.1;
const float U_GLOW_WEIGHT = 0.3;
const float U_GLOW_BIAS = 0.0;
const float U_GLOW_EDGE0 = 0.06;
const float U_GLOW_EDGE1 = 0.0;

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
    return 1.0 - U_B * pow(U_C * M_E, -U_D * x - U_A);
}

float rand(vec2 co) {
    return fract(sin(dot(co, vec2(12.9898, 78.233))) * 43758.5453);
}

float glow(vec2 texCoord) {
    return sin(atan(texCoord.y * 2.0 - 1.0, texCoord.x * 2.0 - 1.0) - 0.5);
}

vec2 safeUv(vec2 uv) {
    return clamp(uv, vec2(0.0015), vec2(0.9985));
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

    float d = sdSuperellipse(p, U_POWER_FACTOR, 1.0);
    float rectDist = clamp(-maskDistance / max(r, 1.0), 0.0, 1.0);
    float dist = max(max(-d, 0.0), rectDist);
    vec2 sampleP = p * pow(max(f(dist), EPSILON2), U_F_POWER);
    vec2 screenUv = safeUv(gl_FragCoord.xy / max(viewportSize, vec2(1.0)));
    vec2 quadScale = size / max(viewportSize, vec2(1.0)) * 0.5;
    vec2 centerUv = screenUv - p * quadScale;
    vec2 sourceUv = safeUv(centerUv + mix(p, sampleP, refraction) * quadScale);

    vec4 noise = vec4(vec3(rand(gl_FragCoord.xy * 0.001 + vec2(time * 0.017)) - 0.5), 0.0);
    vec4 color = texture2D(screenTex, sourceUv) + noise * U_NOISE * max(grainStrength, 0.0);
    float mul = glow(st) * U_GLOW_WEIGHT * highlight * smoothstep(U_GLOW_EDGE0, U_GLOW_EDGE1, dist)
            + 1.0 + U_GLOW_BIAS;

    vec4 liquidGlass = color * vec4(vec3(mul), 1.0);
    liquidGlass.a *= max(fillColor.a, borderColor.a) * mask;
    gl_FragColor = liquidGlass;
}
