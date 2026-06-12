#version 120

uniform sampler2D screenTex;
uniform vec2 screenSize;
uniform vec2 center;
uniform float radius;
uniform float progress;
uniform float strength;
uniform float seed;
uniform float time;

float rand(vec2 p) {
    return fract(sin(dot(p, vec2(127.1, 311.7)) + seed * 0.017) * 43758.5453123);
}

float hash(float n) {
    return fract(sin(n * 17.13 + seed * 0.071) * 43758.5453);
}

float angularCrack(float angle, float dist, float index) {
    float target = hash(index) * 6.2831853;
    float width = mix(0.010, 0.034, hash(index + 2.0));
    float delta = abs(atan(sin(angle - target), cos(angle - target)));
    float len = mix(0.24, 1.0, hash(index + 4.0));
    float jag = sin(dist * mix(18.0, 42.0, hash(index + 8.0)) + hash(index + 11.0) * 6.2831853) * 0.012;
    return smoothstep(width, 0.0, delta + jag) * smoothstep(len, len - 0.26, dist);
}

float facetEdge(vec2 p, float dist) {
    vec2 cell = floor(p * 7.5 + vec2(seed * 0.011, seed * 0.017));
    vec2 local = fract(p * 7.5 + vec2(seed * 0.011, seed * 0.017));
    float n = rand(cell);
    float diagonal = abs((local.x - local.y) + (n - 0.5) * 0.34);
    float cross = min(abs(local.x - 0.5), abs(local.y - 0.5));
    float line = min(diagonal, cross);
    return smoothstep(0.055, 0.0, line) * smoothstep(0.10, 0.98, dist) * smoothstep(1.10, 0.42, dist);
}

void main() {
    vec2 frag = gl_FragCoord.xy;
    vec2 uv = frag / max(screenSize, vec2(1.0));
    vec2 fromCenter = frag - center;
    float distPx = length(fromCenter);
    float d = distPx / max(radius, 1.0);
    if (d > 1.18) {
        discard;
    }

    vec2 dir = distPx <= 0.001 ? vec2(0.0, 1.0) : fromCenter / distPx;
    float open = 1.0 - pow(1.0 - clamp(progress / 0.42, 0.0, 1.0), 4.0);
    float hold = 1.0 - smoothstep(0.72, 1.0, progress);
    float fade = clamp(min(1.0, progress / 0.10) * hold, 0.0, 1.0);
    float angle = atan(fromCenter.y, fromCenter.x);
    float cracks = 0.0;
    for (int i = 0; i < 18; i++) {
        cracks = max(cracks, angularCrack(angle, d, float(i) + 1.0));
    }
    cracks *= smoothstep(0.06, 0.24, d) * smoothstep(1.08, 0.52, d);

    float shardNoise = rand(floor((uv * screenSize - center) / max(10.0, radius * 0.055)));
    float cell = smoothstep(0.36, 1.0, shardNoise) * smoothstep(0.18, 0.94, d);
    float facets = facetEdge(fromCenter / max(radius, 1.0), d);
    float burst = smoothstep(0.20, 1.0, d) * smoothstep(1.12, 0.58, d) * (0.70 + 0.30 * sin(angle * 11.0 + seed));
    float warpMask = clamp(cracks * 1.45 + facets * 0.82 + cell * 0.22 + burst * 0.12, 0.0, 1.0) * fade;

    float refractAmount = (0.015 + cracks * 0.036 + facets * 0.025 + cell * 0.010) * strength * fade;
    vec2 tangent = vec2(-dir.y, dir.x);
    vec2 wobble = tangent * sin(angle * 9.0 + d * 18.0 - time * 2.8) * 0.0055 * strength * fade;
    vec2 offset = (dir * refractAmount * (1.0 - d * 0.46) + wobble) * max(0.22, warpMask);

    vec2 baseUv = clamp(uv + offset, vec2(0.0015), vec2(0.9985));
    vec2 chroma = dir * (0.0028 + cracks * 0.0035) * strength * fade;
    float r = texture2D(screenTex, clamp(baseUv + chroma, vec2(0.0015), vec2(0.9985))).r;
    float g = texture2D(screenTex, baseUv).g;
    float b = texture2D(screenTex, clamp(baseUv - chroma, vec2(0.0015), vec2(0.9985))).b;
    vec3 distorted = vec3(r, g, b);

    vec3 glassTint = vec3(0.62, 0.96, 1.0);
    float edge = clamp(cracks * 0.96 + facets * 0.74 + cell * 0.16, 0.0, 1.0);
    distorted = mix(distorted, glassTint, edge * 0.34 * fade);
    distorted += vec3(0.38, 0.95, 1.0) * (cracks * 0.48 + facets * 0.34) * fade;
    distorted += vec3(1.0) * (cracks * 0.34 + facets * 0.18) * fade;

    float alpha = clamp((warpMask * 0.84 + cracks * 0.42 + facets * 0.30) * fade, 0.0, 0.94);
    gl_FragColor = vec4(distorted, alpha);
}
