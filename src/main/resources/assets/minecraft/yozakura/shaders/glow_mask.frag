#version 120

// Builds a straight-alpha source. The mask pass uses straight-alpha blending,
// so its framebuffer receives premultiplied RGBA for the blur pipeline. Text
// commands use the alpha channel of textureIn; mode 1 selects the procedural
// round rect and mode 2 selects a procedural ring/arc.
uniform sampler2D textureIn;
uniform vec4 maskColor;
uniform float strength;
uniform int shadowMode;

uniform int mode;
uniform vec2 rectSize;
uniform float radius;
uniform float padding;
uniform float softness;
uniform vec2 ringCenter;
uniform float ringRadius;
uniform float ringWidth;
uniform float ringProgress;

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

float ringAlpha(vec2 uv) {
    float outerRadius = max(ringRadius, 0.0001);
    float width = clamp(ringWidth, 0.0001, outerRadius);
    float drawRadius = outerRadius + max(padding, 0.0);
    vec2 point = (uv * 2.0 - 1.0) * drawRadius;
    float radialDistance = abs(length(point) - (outerRadius - width * 0.5)) - width * 0.5;
    float radialAlpha = 1.0 - smoothstep(0.0, max(softness, 0.0001), radialDistance);
    if (ringProgress >= 0.9999) {
        return radialAlpha;
    }
    if (ringProgress <= 0.0001) {
        return 0.0;
    }
    float angle = atan(point.y, point.x);
    float clockwise = mod(angle + 1.57079632679 + 6.28318530718, 6.28318530718);
    float sweep = clamp(ringProgress, 0.0, 1.0) * 6.28318530718;
    float angularFeather = max(softness / max(outerRadius, 0.0001), 0.0001);
    float arcAlpha = 1.0 - smoothstep(sweep, sweep + angularFeather, clockwise);
    float bodyAlpha = radialAlpha * arcAlpha;
    float centerRadius = outerRadius - width * 0.5;
    vec2 startPoint = vec2(0.0, -centerRadius);
    vec2 endPoint = vec2(sin(sweep) * centerRadius, -cos(sweep) * centerRadius);
    float capRadius = width * 0.5;
    float startCapDistance = length(point - startPoint) - capRadius;
    float endCapDistance = length(point - endPoint) - capRadius;
    float capFeather = max(softness, 0.0001);
    float startCapAlpha = 1.0 - smoothstep(0.0, capFeather, startCapDistance);
    float endCapAlpha = 1.0 - smoothstep(0.0, capFeather, endCapDistance);
    return max(bodyAlpha, max(startCapAlpha, endCapAlpha));
}

void main() {
    vec2 uv = gl_TexCoord[0].st;
    float sourceAlpha;
    if (mode == 1) {
        sourceAlpha = roundedRectAlpha(uv);
    } else if (mode == 2) {
        sourceAlpha = ringAlpha(uv);
    } else {
        sourceAlpha = texture2D(textureIn, uv).a;
    }
    float alpha = clamp(sourceAlpha * maskColor.a * max(strength, 0.0), 0.0, 1.0);

    if (shadowMode == 1) {
        gl_FragColor = vec4(maskColor.a * sourceAlpha, 0.0, 0.0, sourceAlpha);
        return;
    }

    gl_FragColor = vec4(maskColor.rgb, alpha);
}
