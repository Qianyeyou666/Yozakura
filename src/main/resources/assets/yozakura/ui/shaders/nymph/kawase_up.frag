#version 120

uniform sampler2D inTexture;
uniform vec2 offset;
uniform vec2 halfpixel;

void main() {
    vec2 uv = gl_TexCoord[0].st;
    vec4 sum = texture2D(inTexture, uv + vec2(-halfpixel.x * 2.0, 0.0) * offset);
    sum += texture2D(inTexture, uv + vec2(-halfpixel.x, halfpixel.y) * offset) * 2.0;
    sum += texture2D(inTexture, uv + vec2(0.0, halfpixel.y * 2.0) * offset);
    sum += texture2D(inTexture, uv + vec2(halfpixel.x, halfpixel.y) * offset) * 2.0;
    sum += texture2D(inTexture, uv + vec2(halfpixel.x * 2.0, 0.0) * offset);
    sum += texture2D(inTexture, uv + vec2(halfpixel.x, -halfpixel.y) * offset) * 2.0;
    sum += texture2D(inTexture, uv + vec2(0.0, -halfpixel.y * 2.0) * offset);
    sum += texture2D(inTexture, uv + vec2(-halfpixel.x, -halfpixel.y) * offset) * 2.0;
    gl_FragColor = sum / 12.0;
}
