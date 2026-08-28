#version 120

uniform sampler2D inTexture;
uniform vec2 framebufferSize;
uniform vec2 rectSize;
uniform float radius;
uniform vec4 tint;

float roundSdf(vec2 point, vec2 halfSize, float cornerRadius) {
    vec2 q = abs(point) - halfSize + vec2(cornerRadius);
    return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - cornerRadius;
}

void main() {
    vec2 halfSize = rectSize * 0.5;
    vec2 local = gl_TexCoord[0].st * rectSize - halfSize;
    float distance = roundSdf(local, halfSize, min(radius, min(halfSize.x, halfSize.y)));
    float mask = 1.0 - smoothstep(0.0, 1.0, distance);
    vec2 screenUv = gl_FragCoord.xy / max(framebufferSize, vec2(1.0));
    vec4 blurred = texture2D(inTexture, screenUv);
    vec3 color = mix(blurred.rgb, tint.rgb, tint.a);
    gl_FragColor = vec4(color, mask);
}
