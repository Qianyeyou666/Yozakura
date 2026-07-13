#version 120

uniform sampler2D sourceTexture;
uniform vec2 texelSize;
uniform vec2 direction;
uniform int radius;
uniform float weights[17];

void main() {
    vec2 uv = gl_TexCoord[0].st;
    vec4 result = texture2D(sourceTexture, uv) * weights[0];

    for (int offset = 1; offset <= 16; offset++) {
        if (offset > radius) {
            break;
        }
        vec2 stepOffset = direction * texelSize * float(offset);
        result += texture2D(sourceTexture, uv + stepOffset) * weights[offset];
        result += texture2D(sourceTexture, uv - stepOffset) * weights[offset];
    }

    gl_FragColor = result;
}
