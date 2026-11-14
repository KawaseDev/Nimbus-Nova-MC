#version 330
#extension GL_ARB_separate_shader_objects : require

#include <minecraft:fog.glsl>
#include <minecraft:dynamictransforms.glsl>
#include <minecraft:oit.glsl>

uniform sampler2D Sampler0;

layout(location = 0) in float sphericalVertexDistance;
layout(location = 1) in float cylindricalVertexDistance;
layout(location = 2) in vec2 texCoord0;
layout(location = 3) in vec4 vertexColor;

#ifndef OIT_ALPHA_ONLY
layout(location = 0) out vec4 fragColor;
#endif

void main() {
    vec4 dust = texture(Sampler0, texCoord0) * vertexColor * ColorModulator;
    if (dust.a < 0.001) discard;

    #ifdef OIT_ALPHA_ONLY
        executeAlphaOnlyPhase(gl_FragCoord.z, dust.a);
    #else
        vec4 sceneFog = FogColor;
        #ifdef OIT_ACCUMULATE
            dust = sampleColorForAccumulation(dust);
            sceneFog.rgb *= dust.a;
        #endif

        fragColor = apply_fog(dust, sphericalVertexDistance, cylindricalVertexDistance,
                FogEnvironmentalStart, FogEnvironmentalEnd, FogRenderDistanceStart, FogRenderDistanceEnd, sceneFog);
    #endif
}
