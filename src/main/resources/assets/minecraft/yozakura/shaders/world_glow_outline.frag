#version 120

uniform sampler2D maskTexture;
uniform vec2 texelSize;
uniform vec2 direction;
uniform int radius;

void main() {
    vec2 uv = gl_TexCoord[0].st;
    float alpha = texture2D(maskTexture, uv).a;

    for (int offset = 1; offset <= 6; offset++) {
        if (offset > radius) {
            break;
        }
        vec2 stepOffset = direction * texelSize * float(offset);
        alpha = max(alpha, texture2D(maskTexture, uv + stepOffset).a);
        alpha = max(alpha, texture2D(maskTexture, uv - stepOffset).a);
    }

    gl_FragColor = vec4(alpha, alpha, alpha, alpha);
}
