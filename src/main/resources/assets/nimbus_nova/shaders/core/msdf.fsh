#version 330
#extension GL_ARB_separate_shader_objects : require

uniform sampler2D Sampler0;

layout(location = 0) in vec4 vertexColor;
layout(location = 1) in vec2 texCoord;
layout(location = 2) flat in vec2 unitRange;

layout(location = 0) out vec4 fragColor;

void main() {
    vec3 sampleValue = texture(Sampler0, texCoord).rgb;
    float median = max(min(sampleValue.r, sampleValue.g), min(max(sampleValue.r, sampleValue.g), sampleValue.b));
    vec2 screenTexelSize = 1.0 / max(fwidth(texCoord), vec2(0.000001));
    float screenRange = max(0.5 * dot(unitRange, screenTexelSize), 1.0);
    float coverage = clamp(screenRange * (median - 0.5) + 0.5, 0.0, 1.0);
    float alpha = vertexColor.a * coverage;
    if (alpha < 0.001) discard;
    fragColor = vec4(vertexColor.rgb, alpha);
}
