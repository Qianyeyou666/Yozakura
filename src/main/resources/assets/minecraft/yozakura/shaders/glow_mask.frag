#version 120

// Builds a straight-alpha source. The mask pass uses straight-alpha blending,
// so its framebuffer receives premultiplied RGBA for the blur pipeline. Text
// commands use the alpha channel of textureIn; mode 1 selects the procedural
// round rect.
uniform sampler2D textureIn;
uniform vec4 maskColor;
uniform float strength;

uniform int mode;
uniform vec2 rectSize;
uniform float radius;
uniform float padding;
uniform float softness;

float roundedRectAlpha(vec2 uv) {
    vec2 halfSize = max(rectSize * 0.5, vec2(0.0001));
    float roundedRadius = clamp(radius, 0.0, min(halfSize.x, halfSize.y));
    vec2 drawSize = rectSize + vec2(max(padding, 0.0) * 2.0);
    vec2 point = uv * drawSize - vec2(max(padding, 0.0)) - halfSize;
    vec2 corner = abs(point) - halfSize + vec2(roundedRadius);
    float signedDistance = length(max(corner, 0.0))
            + min(max(corner.x, corner.y), 0.0) - roundedRadius;
    float feather = max(softness, 0.0001);
    return 1.0 - smoothstep(0.0, feather, signedDistance);
}

void main() {
    vec2 uv = gl_TexCoord[0].st;
    float fontAlpha = texture2D(textureIn, uv).a;
    float sourceAlpha = mode == 1 ? roundedRectAlpha(uv) : fontAlpha;
    float alpha = clamp(sourceAlpha * maskColor.a * max(strength, 0.0), 0.0, 1.0);

    gl_FragColor = vec4(maskColor.rgb, alpha);
}
