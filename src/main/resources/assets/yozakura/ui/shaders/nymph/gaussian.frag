#version 120

uniform sampler2D textureIn;
uniform vec2 texelSize;
uniform vec2 direction;
uniform float radius;
uniform float weights[256];

#define offset texelSize * direction

void main() {
    vec3 blurred = texture2D(textureIn, gl_TexCoord[0].st).rgb * weights[0];

    for (float sampleOffset = 1.0; sampleOffset <= radius; sampleOffset++) {
        float weight = weights[int(abs(sampleOffset))];
        blurred += texture2D(textureIn,
                gl_TexCoord[0].st + sampleOffset * offset).rgb * weight;
        blurred += texture2D(textureIn,
                gl_TexCoord[0].st - sampleOffset * offset).rgb * weight;
    }

    gl_FragColor = vec4(blurred, 1.0);
}
