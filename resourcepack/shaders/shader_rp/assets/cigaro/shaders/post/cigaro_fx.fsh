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

mat2 rot2D(float a) {
    float s = sin(a), c = cos(a);
    return mat2(c, -s, s, c);
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
        p = p * 2.04 + vec2(13.7, 9.2);
        a *= 0.5;
    }
    return v;
}

float crystalNoise(vec2 p) {
    float v = 0.0;
    float a = 0.55;
    for (int i = 0; i < 3; i++) {
        float n = abs(valueNoise(p) - 0.5) * 2.0;
        v += a * (1.0 - n);
        p = rot2D(0.65) * p * 2.15 + vec2(7.3, 19.1);
        a *= 0.5;
    }
    return v;
}

vec3 sampleIn(vec2 uv) {
    return texture(InSampler, clamp(uv, vec2(0.001), vec2(0.999))).rgb;
}

float heartCurve(float x) {
    float p1 = exp(-pow((x - 0.12) / 0.065, 2.0));
    float p2 = 0.65 * exp(-pow((x - 0.28) / 0.080, 2.0));
    return clamp(p1 + p2, 0.0, 1.2);
}

void main() {
    vec3 cleanFrame = texture(InSampler, texCoord).rgb;

    vec4 fadeTex = texture(FadeSampler, vec2(0.5, 0.5));
    float rawProgress = clamp(fadeTex.r + (fadeTex.g / 255.0), 0.0, 1.0);
    float smoothP = smoothstep(0.0, 1.0, rawProgress);
    float dir = ModeAndDir.y;
    float env = (dir > 0.0) ? smoothP : (1.0 - smoothP);

    if (env <= 0.001) {
        fragColor = vec4(cleanFrame, 1.0);
        return;
    }

    float intensity = clamp(env * ModeAndDir.z, 0.0, 1.5);
    float amt = env * clamp(ModeAndDir.z, 0.0, 1.0);

    float mode = ModeAndDir.x;
    float t = GameTime * 1200.0 * max(ModeAndDir.w, 0.001);
    vec2 res = max(OutSize, vec2(1.0));
    float aspect = res.x / res.y;
    vec2 centered = (texCoord - 0.5) * vec2(aspect, 1.0);
    float dist = length(centered);
    float edge = smoothstep(0.12, 0.85, dist);

    vec3 col = vec3(0.0);
    float vignettePulse = 1.0;

    if (mode < 0.5) {
        float breathWave = 0.55 + 0.45 * (0.65 * sin(t * 1.6) + 0.35 * cos(t * 2.7 + 0.8));
        vec2 sway = vec2(
            sin(t * 0.85) * 0.010 + cos(t * 1.9) * 0.004,
            cos(t * 0.65) * 0.008 + sin(t * 1.4) * 0.003
        ) * intensity;

        vec2 smokeUV = centered * vec2(1.6, 1.2) - vec2(sin(t * 0.5) * 0.25, t * 0.55);
        float smoke1 = fbm(smokeUV * 2.2);
        float smoke2 = fbm(smokeUV * 3.8 + vec2(smoke1 * 1.4, -t * 0.35));
        float smokeDensity = smoothstep(0.28, 0.82, 0.55 * smoke1 + 0.45 * smoke2);
        float smokeMask = smokeDensity * smoothstep(0.05, 0.75, dist + (1.0 - texCoord.y) * 0.35);

        vec2 c = texCoord - 0.5 + sway;
        float r2 = dot(centered, centered);
        float zoom = 1.0 + (0.095 * breathWave + 0.04) * intensity;
        vec2 uv = 0.5 + c / zoom * (1.0 + r2 * 0.22 * intensity * breathWave);
        uv += (vec2(smoke1, smoke2) - 0.5) * 0.022 * intensity * (0.4 + 0.6 * smokeMask);

        float blur = (0.006 + 0.014 * edge * breathWave + 0.008 * smokeMask) * intensity;
        float chroma = (0.0025 + 0.0045 * edge) * intensity;
        vec2 viewDir = normalize(centered + vec2(0.0001));
        for (int i = -2; i <= 2; i++) {
            float f = float(i) * 0.5;
            vec2 off = viewDir * f * blur;
            col.r += sampleIn(uv + off + viewDir * chroma).r;
            col.g += sampleIn(uv + off).g;
            col.b += sampleIn(uv + off - viewDir * chroma).b;
        }
        col /= 5.0;

        float L = luma(col);
        vec3 shadowTone = vec3(0.68, 0.56, 0.46);
        vec3 highTone = TintAndVign.rgb * vec3(1.15, 1.02, 0.85);
        vec3 graded = col * mix(shadowTone, highTone, smoothstep(0.05, 0.85, L));
        graded = (graded - 0.5) * 1.24 + 0.5;
        col = mix(col, graded, amt);

        vec3 smokeColor = vec3(0.82, 0.76, 0.68) * (0.45 + 0.55 * smoothstep(0.2, 0.9, L));
        col = mix(col, smokeColor, smokeMask * 0.34 * amt);

        vignettePulse = 0.90 + 0.20 * breathWave;

    } else if (mode < 1.5) {
        float chillPulse = 0.5 + 0.5 * (sin(t * 2.1) * 0.6 + cos(t * 3.7) * 0.4);
        vec2 shiver = vec2(sin(t * 29.0), cos(t * 37.0)) * 0.0012 * intensity * (0.3 + 0.7 * chillPulse);

        vec2 iceCoord = centered * 2.6;
        float ice1 = crystalNoise(iceCoord + vec2(t * 0.04, -t * 0.03));
        float ice2 = crystalNoise(iceCoord * 1.8 - vec2(t * 0.05, t * 0.04));
        float icePattern = 0.6 * ice1 + 0.4 * ice2;

        float frostReach = smoothstep(0.18, 0.78, dist + (icePattern - 0.45) * 0.38);
        float crystals = smoothstep(0.42, 0.88, icePattern) * frostReach;

        vec2 iceGrad = vec2(ice1 - 0.5, ice2 - 0.5);
        float zoom = 1.0 + (0.045 + 0.025 * chillPulse) * intensity;
        vec2 uv = 0.5 + (texCoord - 0.5) / zoom + shiver;
        uv += iceGrad * (0.042 * frostReach + 0.008) * intensity;

        vec2 viewDir = normalize(centered + iceGrad * 0.3 + vec2(0.0001));
        float ca = (0.003 + 0.008 * frostReach) * intensity;
        col.r = sampleIn(uv + viewDir * ca).r;
        col.g = sampleIn(uv).g;
        col.b = sampleIn(uv - viewDir * ca).b;

        vec3 iceBloom = vec3(0.0);
        float bRad = (0.012 + 0.005 * chillPulse) * intensity;
        for (int i = 0; i < 6; i++) {
            float a = float(i) * (PI / 3.0) + t * 0.25;
            vec3 s = sampleIn(uv + vec2(cos(a), sin(a)) * bRad);
            iceBloom += max(s - vec3(0.58), vec3(0.0));
        }
        iceBloom /= 6.0;
        col += iceBloom * vec3(0.45, 1.05, 1.35) * (0.85 * amt);

        float L = luma(col);
        vec3 cryoShadow = vec3(0.45, 0.68, 0.92);
        vec3 cryoHigh = vec3(0.82, 1.15, 1.28);
        vec3 graded = mix(col, vec3(L), 0.35);
        graded *= mix(cryoShadow, cryoHigh, smoothstep(0.1, 0.85, L));
        graded = (graded - 0.5) * 1.28 + 0.5;
        col = mix(col, graded, amt);

        vec3 frostGlaze = vec3(0.78, 0.95, 1.0) * (0.65 + 0.35 * ice2);
        col = mix(col, frostGlaze, crystals * 0.48 * amt);

        vignettePulse = 0.88 + 0.16 * chillPulse;

    } else if (mode < 2.5) {
        float heatPulse = 0.5 + 0.5 * (sin(t * 2.8) * 0.65 + sin(t * 4.9 + 1.2) * 0.35);

        float blastWave = sin(dist * 16.0 - t * 5.5) * exp(-dist * 1.5);
        vec2 turbCoord = centered * vec2(3.2, 2.4) - vec2(0.0, t * 1.6);
        float w1 = valueNoise(turbCoord * 2.5 + vec2(sin(t * 1.7), 0.0)) - 0.5;
        float w2 = valueNoise(turbCoord * 5.2 - vec2(cos(t * 2.3), t * 0.8)) - 0.5;

        float heatProfile = 0.45 + 0.55 * smoothstep(0.0, 0.9, (1.0 - texCoord.y) * 0.6 + dist * 0.6);
        vec2 mirageOffset = vec2(w1 * 0.022 + w2 * 0.010, w2 * 0.018 + w1 * 0.008) * heatProfile;
        mirageOffset += normalize(centered + vec2(0.0001)) * blastWave * 0.007 * edge;

        float zoom = 1.0 + (0.05 + 0.035 * heatPulse) * intensity;
        vec2 uv = 0.5 + (texCoord - 0.5) / zoom + mirageOffset * intensity;

        float chroma = (0.004 + 0.007 * edge * (0.5 + 0.5 * heatPulse)) * intensity;
        vec2 viewDir = normalize(centered + vec2(0.0, -0.35) + vec2(0.0001));
        float smear = (0.005 + 0.008 * heatProfile) * intensity;
        for (int i = -2; i <= 2; i++) {
            float f = float(i) * 0.5;
            vec2 off = vec2(w1 * 0.004 * intensity, f * smear);
            col.r += sampleIn(uv + off + viewDir * chroma).r;
            col.g += sampleIn(uv + off).g;
            col.b += sampleIn(uv + off - viewDir * chroma).b;
        }
        col /= 5.0;

        vec3 fireBloom = vec3(0.0);
        float fRad = (0.015 + 0.008 * heatPulse) * intensity;
        for (int i = 0; i < 6; i++) {
            float a = float(i) * (PI / 3.0) - t * 0.4;
            vec3 s = sampleIn(uv + vec2(cos(a), sin(a)) * fRad);
            fireBloom += max(s - vec3(0.55), vec3(0.0));
        }
        fireBloom /= 6.0;
        col += fireBloom * vec3(1.45, 0.55, 0.12) * (0.95 * amt);

        float L = luma(col);
        vec3 charShadow = vec3(0.55, 0.28, 0.16);
        vec3 flameHigh = TintAndVign.rgb * vec3(1.22, 0.95, 0.68);
        vec3 graded = col * mix(charShadow, flameHigh, smoothstep(0.05, 0.85, L));
        graded = (graded - 0.5) * 1.35 + 0.5;
        col = mix(col, graded, amt);

        float emberRim = smoothstep(0.35, 0.90, dist) * (0.6 + 0.4 * sin(t * 3.5 + w1 * 4.0));
        col += vec3(0.28, 0.07, 0.01) * emberRim * amt;

        vignettePulse = 0.92 + 0.22 * heatPulse;

    } else if (mode < 3.5) {
        vec2 drift = vec2(
            sin(t * 0.75) * 0.014 + cos(t * 1.35) * 0.005,
            cos(t * 0.55) * 0.011 + sin(t * 1.15) * 0.004
        ) * intensity;
        float roll = sin(t * 0.65) * 0.035 * intensity;

        vec2 c = rot2D(roll) * (texCoord - 0.5 + drift);
        float r2 = dot(centered, centered);
        float zoom = 1.0 + (0.06 + 0.025 * sin(t * 0.9)) * intensity;
        vec2 uv = 0.5 + c / zoom * (1.0 + r2 * 0.15 * intensity);

        vec3 base = sampleIn(uv);
        vec3 ghost = sampleIn(uv - drift * 1.6);
        col = mix(base, ghost, 0.28 * amt);

        vec3 anamorphic = vec3(0.0);
        float streakStep = (0.011 + 0.003 * sin(t * 1.5)) * intensity;
        for (int i = -3; i <= 3; i++) {
            float weight = 1.0 - abs(float(i)) * 0.22;
            vec2 sUV = uv + vec2(float(i) * streakStep, 0.0);
            vec3 s = sampleIn(sUV);
            float bright = smoothstep(0.55, 0.95, luma(s));
            anamorphic += s * bright * weight;
        }
        anamorphic /= 3.5;

        vec2 viewDir = normalize(centered + vec2(0.0001));
        float ca = (0.003 + 0.006 * edge) * intensity;
        col.r = mix(col.r, sampleIn(uv + viewDir * ca).r, 0.75 * amt);
        col.b = mix(col.b, sampleIn(uv - viewDir * ca).b, 0.75 * amt);

        float L = luma(col);
        float maxc = max(max(col.r, col.g), col.b);
        float minc = min(min(col.r, col.g), col.b);
        float sat = (maxc - minc) / (maxc + 0.001);

        vec3 noirShadow = vec3(0.42, 0.26, 0.62);
        vec3 neonHigh = vec3(1.18, 0.85, 1.12);
        vec3 graded = mix(vec3(L), col, mix(0.55, 1.45, sat));
        graded *= mix(noirShadow, neonHigh, smoothstep(0.08, 0.85, L));
        graded = (graded - 0.5) * 1.32 + 0.5;
        col = mix(col, graded, amt);

        col += anamorphic * vec3(1.25, 0.42, 1.05) * (0.85 * amt);

        vignettePulse = 0.90 + 0.15 * sin(t * 0.8);

    } else if (mode < 4.5) {
        float cycle = fract(t * 1.10);
        float heart = heartCurve(cycle);
        float tension = 0.30 + 0.70 * heart;

        float twitchSeed = floor(t * 4.5);
        float twitchPhase = fract(t * 4.5);
        float twitchHit = step(0.52, hash11(twitchSeed)) * exp(-twitchPhase * 9.0);
        vec2 jerk = (vec2(hash11(twitchSeed + 2.1), hash11(twitchSeed + 8.3)) - 0.5)
                  * 0.024 * twitchHit * intensity;
        vec2 tremor = vec2(sin(t * 38.0) + cos(t * 51.0), cos(t * 43.0) - sin(t * 29.0))
                    * 0.0018 * tension * intensity;

        float tilt = (sin(t * 1.7) * 0.025 + (hash11(twitchSeed) - 0.5) * 0.05 * heart) * intensity;
        vec2 c = rot2D(tilt) * (texCoord - 0.5) + jerk + tremor;
        float r2 = dot(centered, centered);
        float zoom = 1.0 + (0.04 + 0.095 * heart) * intensity;
        vec2 uv = 0.5 + c / zoom * (1.0 - r2 * 0.28 * tension * intensity);

        vec2 diplopia = vec2(sin(t * 2.3) * 0.006, 0.022 * tension) * intensity;
        vec3 mainView = sampleIn(uv);
        vec3 doubleView = sampleIn(uv + diplopia);
        col = mix(mainView, doubleView, (0.25 + 0.40 * heart) * amt);

        vec2 viewDir = normalize(centered + vec2(0.0001));
        float ca = (0.003 + 0.009 * edge * tension) * intensity;
        col.r = sampleIn(uv + viewDir * ca).r;
        col.b = sampleIn(uv - viewDir * ca).b;

        float L = luma(col);
        vec3 graded = mix(col, vec3(L), 0.72) * vec3(0.84, 0.90, 0.96);
        graded = (graded - 0.5) * (1.28 + 0.15 * heart) + 0.5;
        col = mix(col, graded, amt);

        float tunnelDist = dist * (1.15 + 0.75 * heart * amt);
        float blackout = smoothstep(0.18, 0.78, tunnelDist) * amt;
        float grain = hash21(floor(texCoord * res * 0.5) + vec2(floor(t * 30.0) * 19.3, 7.1)) - 0.5;
        col += vec3(grain) * (0.08 + 0.14 * blackout) * amt;
        col *= 1.0 - 0.78 * blackout;

        vignettePulse = 1.10 + 0.55 * heart;

    } else {
        vec2 sway = vec2(
            sin(t * 1.15) * 0.022 + sin(t * 2.35 + 1.2) * 0.008,
            cos(t * 0.82) * 0.018 + cos(t * 1.75 + 0.4) * 0.006
        );
        vec2 velocity = vec2(
            cos(t * 1.15) * 1.15 * 0.022 + cos(t * 2.35 + 1.2) * 2.35 * 0.008,
            -sin(t * 0.82) * 0.82 * 0.018 - sin(t * 1.75 + 0.4) * 1.75 * 0.006
        );

        float vortexAngle = (sin(t * 0.9) * 0.065 + sin(dist * 6.0 - t * 2.2) * 0.045 * edge) * intensity;
        vec2 c = rot2D(vortexAngle) * (texCoord - 0.5 + sway * intensity);
        float r2 = dot(centered, centered);
        vec2 uv = 0.5 + c * (1.0 + 0.015 * sin(t * 0.65) * intensity + r2 * 0.22 * sin(t * 1.4) * intensity);

        vec2 ghostOffset = -velocity * 1.9 * intensity;
        col = sampleIn(uv);
        vec3 ghost1 = sampleIn(uv + ghostOffset);
        vec3 ghost2 = sampleIn(uv + ghostOffset * 2.1);
        col = mix(col, ghost1, 0.34 * amt);
        col = mix(col, ghost2, 0.18 * amt);

        vec2 viewDir = normalize(centered + vec2(0.0001));
        float ca = (0.0035 + 0.0085 * edge) * intensity;
        col.r = mix(col.r, sampleIn(uv + viewDir * ca).r, 0.88 * amt);
        col.b = mix(col.b, sampleIn(uv - viewDir * ca).b, 0.88 * amt);

        float L = luma(col);
        vec3 oliveShadow = vec3(0.58, 0.84, 0.52);
        vec3 amberHigh = vec3(1.24, 0.94, 0.54);
        vec3 graded = col * mix(oliveShadow, amberHigh, smoothstep(0.05, 0.88, L));
        graded = (graded - 0.5) * 1.20 + 0.5;
        col = mix(col, graded, amt);

        float solar = smoothstep(0.68, 0.98, luma(col)) * amt;
        col = mix(col, 1.0 - col, 0.16 * solar);

        vignettePulse = 0.88 + 0.18 * sin(t * 0.95);
    }

    float shadowLift = 1.0 - smoothstep(0.0, 0.50, luma(col));
    col += TintAndVign.rgb * (0.022 * shadowLift * amt);

    float vig = smoothstep(0.18, 0.88, dist * TintAndVign.a * vignettePulse);
    col = mix(col, col * (1.0 - 0.76 * vig), amt);

    col = mix(cleanFrame, clamp(col, 0.0, 1.0), smoothstep(0.0, 0.04, amt));

    fragColor = vec4(col, 1.0);
}
