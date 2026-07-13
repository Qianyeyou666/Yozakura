#version 120

// Removes the sharp source from the blurred result so this pass can run after
// normal HUD text/geometry without washing out its centre.
uniform sampler2D maskTexture;
uniform sampler2D blurTexture;
uniform float strength;
uniform int shadowMode;

void main() {
    vec2 uv = gl_TexCoord[0].st;
    vec4 mask = texture2D(maskTexture, uv);
    vec4 blur = texture2D(blurTexture, uv);

    if (shadowMode == 1) {
        // The mask has antialiased coverage. Suppress only the covered share
        // so the black blur remains underneath translucent edge pixels rather
        // than leaving a bright one-pixel gap around the source geometry.
        float outsideMask = 1.0 - mask.a;
        float shadowAlpha = blur.r * outsideMask * max(strength, 0.0);
        gl_FragColor = vec4(0.0, 0.0, 0.0, shadowAlpha);
        return;
    }

    float coreSuppression = 1.0 - mask.a;
    vec4 glow = blur * (max(strength, 0.0) * coreSuppression);
    glow.rgb = min(glow.rgb, vec3(glow.a));

    gl_FragColor = clamp(glow, 0.0, 1.0);
}
