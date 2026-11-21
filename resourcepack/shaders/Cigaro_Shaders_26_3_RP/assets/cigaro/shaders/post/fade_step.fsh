#version 330
#extension GL_ARB_separate_shader_objects : require

uniform sampler2D PrevSampler;

layout(std140) uniform SamplerInfo {
    vec2 OutSize;
    vec2 InSize;
};

layout(std140) uniform FadeConfig {
    float Speed;
};

layout(location = 0) in vec2 texCoord;
layout(location = 0) out vec4 fragColor;

void main() {
    vec4 prev = texture(PrevSampler, vec2(0.5, 0.5));
    float val = prev.r + (prev.g / 255.0);
    val = clamp(val + Speed, 0.0, 1.0);

    float r = floor(val * 255.0) / 255.0;
    float g = fract(val * 255.0);
    fragColor = vec4(r, g, val, 1.0);
}
