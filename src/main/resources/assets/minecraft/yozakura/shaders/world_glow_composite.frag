#version 120

uniform sampler2D maskTexture;
uniform sampler2D blurTexture;
uniform vec4 coreColor;
uniform vec4 outerColor;
uniform float coreLayer;
uniform float strength;

void main() {
    vec2 uv = gl_TexCoord[0].st;
    float maskAlpha = texture2D(maskTexture, uv).a;
    float glowAlpha = texture2D(blurTexture, uv).a * (1.0 - maskAlpha);
    vec4 tint = mix(outerColor, coreColor, clamp(coreLayer, 0.0, 1.0));
    float finalAlpha = tint.a * glowAlpha * strength;

    gl_FragColor = vec4(tint.rgb * finalAlpha, finalAlpha);
}
