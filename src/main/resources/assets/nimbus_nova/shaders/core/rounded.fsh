#version 330
#extension GL_ARB_separate_shader_objects : require

layout(location = 0) in vec4 vertexColor;
layout(location = 1) in vec2 localPosition;
layout(location = 2) flat in vec2 halfSize;
layout(location = 3) flat in vec4 parameters;

layout(location = 0) out vec4 fragColor;

void main() {
    float radius = parameters.x;
    vec2 q = abs(localPosition) - halfSize + vec2(radius);
    float distance = length(max(q, vec2(0.0))) + min(max(q.x, q.y), 0.0) - radius;
    float antialias = max(fwidth(distance), 0.001);
    float coverage = 1.0 - smoothstep(-antialias * 0.5, antialias * 0.5, distance);

    if (parameters.w > 1.5) {
        float outside = max(distance, 0.0);
        float sigma = max(parameters.z, 0.0625);
        coverage = exp(-(outside * outside) / (2.0 * sigma * sigma));
    } else if (parameters.w > 0.5) {
        coverage *= smoothstep(-parameters.y - antialias * 0.5, -parameters.y + antialias * 0.5, distance);
    }

    float alpha = vertexColor.a * coverage;
    if (alpha < 0.001) discard;
    fragColor = vec4(vertexColor.rgb, alpha);
}
