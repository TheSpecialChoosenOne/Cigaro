#version 330
#extension GL_ARB_separate_shader_objects : require

#include <minecraft:globals.glsl>

uniform sampler2D InSampler;
uniform sampler2D FadeSampler;

layout(std140) uniform SamplerInfo {
    vec2 OutSize;
    vec2 InSize;
    vec2 FadeSize;
};

layout(std140) uniform CigaroConfig {
    vec4 ModeAndDir;
    vec4 TintAndVign;
};

layout(location = 0) in vec2 texCoord;
layout(location = 0) out vec4 fragColor;

const float PI = 3.14159265359;

float luma(vec3 c) {
    return dot(c, vec3(0.2126, 0.7152, 0.0722));
}

float hash11(float p) {
    p = fract(p * 0.1031);
    p *= p + 33.33;
    p *= p + p;
    return fract(p);
}

float hash21(vec2 p) {
    vec3 p3 = fract(vec3(p.xyx) * 0.1031);
    p3 += dot(p3, p3.yzx + 33.33);
    return fract((p3.x + p3.y) * p3.z);
}

float valueNoise(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    float a = hash21(i);
    float b = hash21(i + vec2(1.0, 0.0));
    float c = hash21(i + vec2(0.0, 1.0));
    float d = hash21(i + vec2(1.0, 1.0));
    return mix(mix(a, b, f.x), mix(c, d, f.x), f.y);
}

float fbm(vec2 p) {
    float v = 0.0;
    float a = 0.5;
    for (int i = 0; i < 3; i++) {
        v += a * valueNoise(p);
        p = p * 2.03 + vec2(17.1, 9.2);
        a *= 0.5;
    }
    return v;
}

vec3 sampleIn(vec2 uv) {
    return texture(InSampler, clamp(uv, vec2(0.001), vec2(0.999))).rgb;
}

float breathCurve(float x) {
    if (x < 0.16) {
        return smoothstep(0.0, 0.16, x);
    }
    if (x < 0.28) {
        return 1.0;
    }
    return exp(-4.2 * (x - 0.28));
}

float heartCurve(float x) {
    float p1 = exp(-pow((x - 0.10) / 0.045, 2.0));
    float p2 = 0.55 * exp(-pow((x - 0.22) / 0.055, 2.0));
    return p1 + p2;
}

void main() {
    vec4 fadeTex = texture(FadeSampler, vec2(0.5, 0.5));
    float rawProgress = clamp(fadeTex.r + (fadeTex.g / 255.0), 0.0, 1.0);
    float smoothP = smoothstep(0.0, 1.0, rawProgress);
    float dir = ModeAndDir.y;
    float env = (dir > 0.0) ? smoothP : (1.0 - smoothP);
    float intensity = clamp(env * ModeAndDir.z, 0.0, 1.5);
    float amt = clamp(intensity, 0.0, 1.0);

    if (intensity <= 0.001) {
        fragColor = texture(InSampler, texCoord);
        return;
    }

    float mode = ModeAndDir.x;
    float t = GameTime * 1200.0 * max(ModeAndDir.w, 0.001);
    vec2 res = max(OutSize, vec2(1.0));
    float aspect = res.x / res.y;
    vec2 centered = (texCoord - 0.5) * vec2(aspect, 1.0);
    float dist = length(centered);
    float edge = smoothstep(0.15, 0.85, dist);

    vec3 col = sampleIn(texCoord);
    float lift = 0.0;
    vec3 liftTint = vec3(1.0);
    float vignettePulse = 1.0;

    if (mode < 0.5) {
        float x = fract(t / 8.0);
        float breath = breathCurve(x);
        float xLag = fract(t / 8.0 - 0.10);
        float breathLag = breathCurve(xLag);
        float haze = 0.62 * breath + 0.38 * breathLag;

        vec2 center = vec2(
            0.5 + 0.012 * sin(t * 0.11) + 0.006 * sin(t * 0.29 + 1.3),
            0.54 + 0.006 * cos(t * 0.17) + 0.003 * sin(t * 0.41)
        );
        vec2 c = (texCoord - center) * vec2(aspect, 1.0);
        float zoom = 1.0 + 0.045 * intensity * breath;
        vec2 uv = center + (texCoord - center) / zoom;
        uv.x += 0.0035 * intensity * (sin(t * 0.37) + 0.45 * sin(t * 0.93 + 2.0));
        uv.y += 0.0020 * intensity * sin(t * 0.23 + 1.1);

        float radial = smoothstep(0.35, 0.95, dist);
        float blur = (0.0040 + 0.0100 * haze) * intensity * radial;
        float chroma = 0.0015 * intensity * radial;
        vec2 viewDir = normalize(c + vec2(0.0001));
        col = vec3(0.0);
        for (int i = -2; i <= 2; i++) {
            float f = float(i) / 2.0;
            vec2 off = viewDir * f * blur;
            col.r += sampleIn(uv + off + viewDir * chroma).r;
            col.g += sampleIn(uv + off).g;
            col.b += sampleIn(uv + off - viewDir * chroma).b;
        }
        col /= 5.0;

        float veil = smoothstep(0.15, 0.9, length(c)) * (0.5 + 0.5 * breath);
        col = mix(col, col * vec3(1.05, 0.92, 0.80) + vec3(0.025, 0.018, 0.012),
                  0.18 * amt * veil);

        float L = luma(col);
        vec3 shadowTone = vec3(0.72, 0.66, 0.60);
        vec3 highTone = TintAndVign.rgb;
        col *= mix(shadowTone, highTone, smoothstep(0.05, 0.90, L));
        col = (col - 0.5) * (1.14 + 0.06 * amt) + 0.5;
        col = mix(col, col * TintAndVign.rgb, 0.16 * amt * smoothstep(0.55, 1.0, L));

        lift = 0.028;
        liftTint = vec3(0.95, 0.90, 0.84);
    } else if (mode < 1.5) {
        float onset = (dir > 0.0) ? (1.0 - smoothstep(0.0, 0.24, rawProgress)) : 0.0;
        float impulse = onset * onset;

        vec2 center = vec2(0.5 + 0.0025 * sin(t * 0.19),
                           0.5 + 0.0020 * cos(t * 0.23));
        float zoom = 1.0 + 0.018 * intensity + 0.022 * impulse;
        vec2 uv = center + (texCoord - center) / zoom;
        uv += vec2(sin(t * 37.0), cos(t * 43.0)) * 0.0005 * impulse;

        vec3 base = sampleIn(uv);
        vec2 px = 1.0 / res;
        vec2 dx = vec2(px.x, 0.0);
        vec2 dy = vec2(0.0, px.y);
        vec3 blur = (sampleIn(uv + dx) + sampleIn(uv - dx)
                   + sampleIn(uv + dy) + sampleIn(uv - dy) + base) * 0.2;
        float sharpen = (0.9 + 1.8 * impulse) * intensity;
        col = clamp(base + (base - blur) * sharpen, 0.0, 1.0);

        float L = luma(col);
        col = mix(col, vec3(L), 0.18 * amt);
        float sh = 1.0 - smoothstep(0.05, 0.55, L);
        float hi = smoothstep(0.50, 0.95, L);
        col = mix(col, col * vec3(0.58, 0.72, 0.86) + vec3(0.010, 0.020, 0.035),
                  0.42 * amt * sh);
        col = mix(col, col * vec3(0.78, 1.06, 1.12), 0.38 * amt * hi);

        float frost = fbm(texCoord * vec2(4.0, 3.2) + vec2(t * 0.015, -t * 0.020));
        float frostMask = smoothstep(0.55, 0.95, dist + (frost - 0.5) * 0.18);
        col = mix(col, mix(col, vec3(0.80, 0.92, 1.0), 0.22),
                  frostMask * amt);

        float ca = (0.0015 + 0.0020 * impulse) * intensity * edge;
        vec2 viewDir = normalize(centered + vec2(0.0001));
        col.r = mix(col.r, sampleIn(uv + viewDir * ca).r, 0.65);
        col.b = mix(col.b, sampleIn(uv - viewDir * ca).b, 0.65);

        lift = 0.018;
        liftTint = vec3(0.65, 0.85, 1.0);
    } else if (mode < 2.5) {
        float heatMask = smoothstep(0.0, 0.85, 1.0 - texCoord.y);
        heatMask = pow(heatMask, 1.35);

        vec2 q = texCoord * vec2(aspect, 1.0);
        float flow = t * 0.32;
        float n1 = sin(q.y * 9.0 - flow * 4.0 + q.x * 4.0
                       + 1.3 * sin(q.x * 3.0 + t * 0.31));
        float n2 = sin(q.y * 15.0 - flow * 6.0 - q.x * 7.0
                       + 1.1 * cos(q.y * 2.0 - t * 0.23));
        float n3 = sin(q.y * 23.0 - flow * 9.0 + q.x * 12.0
                       + 0.7 * sin(q.x * 8.0 + t * 0.40));
        float n4 = cos(q.x * 11.0 + t * 0.70 + q.y * 5.0);

        vec2 uv = texCoord;
        uv += vec2(n1 * 0.0070 + n2 * 0.0035 + n3 * 0.0016,
                   n4 * 0.0040 + n2 * 0.0024)
              * intensity * heatMask;

        float tremble = 0.5 + 0.5 * sin(t * 3.1 + n1 * 1.7 + n2 * 0.8);
        float chroma = 0.0035 * intensity * (0.35 + 0.65 * tremble)
                     * (0.3 + 0.7 * edge);
        vec2 viewDir = normalize(centered + vec2(0.0001));
        col.r = sampleIn(uv + viewDir * chroma).r;
        col.g = sampleIn(uv).g;
        col.b = sampleIn(uv - viewDir * chroma).b;

        vec3 bloom = vec3(0.0);
        float radius = 0.010 + 0.006 * (0.5 + 0.5 * sin(t * 0.70));
        for (int i = 0; i < 8; i++) {
            float a = float(i) * (2.0 * PI / 8.0) + t * 0.07;
            vec3 s = sampleIn(uv + vec2(cos(a), sin(a)) * radius);
            bloom += max(s - vec3(0.65), vec3(0.0));
        }
        bloom /= 8.0;
        col += bloom * vec3(1.15, 0.58, 0.28)
             * (0.65 * amt) * (0.45 + 0.55 * heatMask);

        float L = luma(col);
        vec3 warmShadow = vec3(0.62, 0.44, 0.32);
        vec3 warmHigh = TintAndVign.rgb;
        col *= mix(warmShadow, warmHigh, smoothstep(0.05, 0.90, L));
        col = (col - 0.5) * (1.20 + 0.08 * amt) + 0.5;
        float sh = 1.0 - smoothstep(0.05, 0.50, L);
        col = mix(col, col * vec3(0.64, 0.46, 0.32) + vec3(0.035, 0.015, 0.005),
                  0.18 * amt * sh);

        lift = 0.028;
        liftTint = vec3(1.0, 0.68, 0.42);
    } else if (mode < 3.5) {
        vec2 center = vec2(0.5 + 0.004 * sin(t * 0.07),
                           0.5 + 0.003 * cos(t * 0.11));
        float drift = 0.006 * intensity * (sin(t * 0.07) + 0.5 * sin(t * 0.13 + 1.7));
        vec2 uv = center + (texCoord - center) * (1.0 + drift);

        vec3 base = sampleIn(uv);
        vec3 mist = vec3(0.0);
        float radius = 0.018 + 0.006 * sin(t * 0.17);
        for (int i = 0; i < 10; i++) {
            float a = float(i) * (2.0 * PI / 10.0) + 0.7;
            vec3 s = sampleIn(uv + vec2(cos(a), sin(a)) * radius);
            mist += max(s - vec3(0.62), vec3(0.0));
        }
        mist /= 10.0;

        vec3 mistWide = vec3(0.0);
        for (int i = 0; i < 6; i++) {
            float a = float(i) * (2.0 * PI / 6.0) + 2.1;
            vec3 s = sampleIn(uv + vec2(cos(a), sin(a)) * (radius * 2.2));
            mistWide += max(s - vec3(0.55), vec3(0.0));
        }
        mistWide /= 6.0;
        mist = mist * 0.65 + mistWide * 0.35;

        col = base + mist * vec3(1.05, 0.72, 0.88) * (0.55 * amt);
        col = mix(col, col + mist * 0.35, 0.30 * amt);

        float L = luma(col);
        float maxc = max(max(col.r, col.g), col.b);
        float sat = 1.0 + 0.50 * amt * (1.0 - maxc);
        col = mix(vec3(L), col, sat);

        vec3 berryShadow = vec3(0.64, 0.44, 0.82);
        vec3 berryHigh = vec3(1.04, 0.94, 0.92);
        vec3 berryTone = mix(berryShadow, berryHigh, smoothstep(0.20, 0.90, L));
        col = mix(col, col * berryTone, 0.62 * amt);
        col = (col - 0.5) * 1.06 + 0.5;

        float sh = 1.0 - smoothstep(0.05, 0.55, L);
        col = mix(col, col * vec3(0.80, 0.66, 0.90) + vec3(0.020, 0.0, 0.030),
                  0.12 * amt * sh);
        float hi = smoothstep(0.50, 0.95, L);
        col = mix(col, col * vec3(1.06, 0.94, 0.92), 0.12 * amt * hi);

        lift = 0.024;
        liftTint = vec3(0.90, 0.72, 0.92);
    } else if (mode < 4.5) {
        float x = fract(t / 1.15);
        float heart = heartCurve(x);
        float recovery = exp(-max(0.0, x - 0.24) * 4.0);

        float sacTime = t * 0.63;
        float sacSeed = floor(sacTime);
        float sacPhase = fract(sacTime);
        float sacTrigger = step(0.82, hash11(sacSeed + 4.7));
        float sac = sacTrigger * exp(-sacPhase * 22.0);
        vec2 jerk = (vec2(hash11(sacSeed + 1.3), hash11(sacSeed + 7.9)) - 0.5)
                  * 0.010 * sac * intensity;

        vec2 center = vec2(0.5 + 0.003 * sin(t * 0.13),
                           0.5 + 0.002 * cos(t * 0.17));
        vec2 uv = center + (texCoord - center) * (1.0 - 0.030 * heart * intensity) + jerk;
        uv += vec2(sin(t * 0.31), cos(t * 0.27)) * 0.0015 * intensity;

        col = sampleIn(uv);
        vec2 viewDir = normalize(centered + vec2(0.0001));
        float ca = 0.0015 * intensity * edge;
        col.r = sampleIn(uv + viewDir * ca).r;
        col.b = sampleIn(uv - viewDir * ca).b;

        float L = luma(col);
        col = mix(col, vec3(L), 0.38 * amt);
        col = mix(col, vec3(L) * 0.82 + vec3(0.02), 0.30 * amt);
        col = mix(col, col * vec3(0.92, 0.95, 0.96), 0.50 * amt);

        float blackout = smoothstep(0.15, 0.95, heart) * intensity;
        float edgeMask = smoothstep(0.18, 0.85, dist);
        col *= 1.0 - 0.50 * blackout * edgeMask;
        col = mix(col, col * vec3(0.95, 0.97, 1.0),
                  0.15 * amt * recovery * edgeMask);

        float grain = hash21(floor(uv * res)
                    + vec2(floor(t * 24.0) * 17.0, floor(t * 24.0) * 31.0)) - 0.5;
        float shadowMask = 1.0 - smoothstep(0.0, 0.40, L);
        col += vec3(grain) * 0.055 * amt * shadowMask;

        float beatVig = 0.78 + 0.34 * smoothstep(0.15, 0.95, heart);
        vignettePulse = beatVig;

        lift = 0.018;
        liftTint = vec3(0.80, 0.88, 0.95);
    } else {
        vec2 sway = vec2(
            sin(t * 1.10) * 0.018 + sin(t * 2.30 + 1.2) * 0.0065,
            cos(t * 0.77) * 0.015 + cos(t * 1.70 + 0.4) * 0.0050
        );
        vec2 center = vec2(0.5 + sway.x * 0.35, 0.5 + sway.y * 0.35);
        vec2 uv = texCoord + sway * intensity;
        uv = center + (uv - center) * (1.0 + 0.008 * intensity * sin(t * 0.43));

        vec2 velocity = vec2(
            cos(t * 1.10) * 1.10 * 0.018 + cos(t * 2.30 + 1.2) * 2.30 * 0.0065,
            -sin(t * 0.77) * 0.77 * 0.015 - sin(t * 1.70 + 0.4) * 1.70 * 0.0050
        );
        vec2 ghostOffset = -velocity * 1.8;

        col = sampleIn(uv);
        vec3 ghost1 = sampleIn(uv + ghostOffset);
        vec3 ghost2 = sampleIn(uv + ghostOffset * 2.0);
        col = mix(col, ghost1, 0.30 * amt);
        col = mix(col, ghost2, 0.14 * amt);

        vec2 viewDir = normalize(centered + vec2(0.0001));
        float ca = (0.0025 + 0.006 * edge) * intensity;
        col.r = mix(col.r, sampleIn(uv + viewDir * ca).r, 0.85);
        col.b = mix(col.b, sampleIn(uv - viewDir * ca).b, 0.85);

        float L = luma(col);
        vec3 oliveShadow = vec3(0.62, 0.80, 0.56);
        vec3 amberHigh = vec3(1.20, 0.92, 0.58);
        col *= mix(oliveShadow, amberHigh, smoothstep(0.05, 0.90, L));
        col = (col - 0.5) * (1.08 + 0.04 * amt) + 0.5;

        float sh = 1.0 - smoothstep(0.05, 0.55, L);
        col = mix(col, col * vec3(0.78, 0.92, 0.72) + vec3(0.010, 0.020, 0.0),
                  0.18 * amt * sh);

        float solar = smoothstep(0.72, 1.0, luma(col)) * amt;
        col = mix(col, 1.0 - col, 0.10 * solar);

        vignettePulse = 0.86 + 0.14 * sin(t * 0.31);

        lift = 0.030;
        liftTint = vec3(0.72, 0.88, 0.58);
    }

    float shadowLift = 1.0 - smoothstep(0.0, 0.55, luma(col));
    col += liftTint * (lift * shadowLift * amt);

    float vig = smoothstep(0.20, 0.90, dist * TintAndVign.a * vignettePulse);
    col = mix(col, col * (1.0 - 0.72 * vig), amt);

    fragColor = vec4(clamp(col, 0.0, 1.0), 1.0);
}
