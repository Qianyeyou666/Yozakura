#version 120

// The source is premultiplied RGBA.  Keeping alpha in the convolution makes
// the soft edge survive the final alpha blend instead of becoming an opaque
// colour field.
uniform sampler2D sourceTexture;
uniform vec2 texelSize;
uniform int radius;
uniform float weights[33];

const int MAX_RADIUS = 32;

void main() {
    vec2 uv = gl_TexCoord[0].st;
    vec4 blurred = texture2D(sourceTexture, uv) * weights[0];

    for (int offset = 1; offset <= MAX_RADIUS; offset++) {
        if (offset > radius) {
            break;
        }

        vec2 sampleOffset = texelSize * float(offset);
        float weight = weights[offset];
        blurred += texture2D(sourceTexture, uv + sampleOffset) * weight;
        blurred += texture2D(sourceTexture, uv - sampleOffset) * weight;
    }

    gl_FragColor = blurred;
}
