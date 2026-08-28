#version 120

uniform sampler2D inTexture;
uniform vec2 offset;
uniform vec2 halfpixel;

void main() {
    vec2 uv = gl_TexCoord[0].st;
    vec4 sum = texture2D(inTexture, uv) * 4.0;
    sum += texture2D(inTexture, uv - halfpixel * offset);
    sum += texture2D(inTexture, uv + halfpixel * offset);
    sum += texture2D(inTexture, uv + vec2(halfpixel.x, -halfpixel.y) * offset);
    sum += texture2D(inTexture, uv - vec2(halfpixel.x, -halfpixel.y) * offset);
    gl_FragColor = vec4(sum.rgb / 8.0, sum.a / 8.0);
}
