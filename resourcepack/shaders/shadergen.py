#!/usr/bin/env python3
"""Generate the standalone Cigaro post-effect resource pack.

Canonical output:
  Cigaro_Shaders_26_3_RP/       expanded pack
  Cigaro_Shaders_26_3_RP.zip    pack archive

The older shader_rp/ directory is kept as a generated mirror so existing
scripts that still point at it do not break.
"""

from pathlib import Path
import json
import zipfile

ROOT = Path(__file__).resolve().parent
PACK_DIR = ROOT / "Cigaro_Shaders_26_3_RP"
MIRROR_DIR = ROOT / "shader_rp"
ZIP_PATH = ROOT / "Cigaro_Shaders_26_3_RP.zip"
PACK_DIRS = (PACK_DIR, MIRROR_DIR)


def write_pack_file(rel_path: str, text: str) -> None:
    for base in PACK_DIRS:
        path = base / rel_path
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(text, encoding="utf-8")


fade_step_fsh = """#version 330
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
"""

write_pack_file("assets/cigaro/shaders/post/fade_step.fsh", fade_step_fsh)


cigaro_fx_fsh = (ROOT / "cigaro_fx.fsh").read_text(encoding="utf-8")

write_pack_file("assets/cigaro/shaders/post/cigaro_fx.fsh", cigaro_fx_fsh)


def make_post_effect_json(mode, direction, max_intensity, speed_mult, tint_rgb, vignette, fade_speed=0.012):
    return {
        "targets": {
            "swap": {},
            "fade_a": {"width": 2, "height": 2, "persistent": True},
            "fade_b": {"width": 2, "height": 2, "persistent": True},
        },
        "passes": [
            {
                "vertex_shader": "minecraft:core/screenquad",
                "fragment_shader": "cigaro:post/fade_step",
                "inputs": [{"sampler_name": "Prev", "target": "fade_b"}],
                "output": "fade_a",
                "uniforms": {
                    "FadeConfig": [
                        {"name": "Speed", "type": "float", "value": float(fade_speed)}
                    ]
                },
            },
            {
                "vertex_shader": "minecraft:core/screenquad",
                "fragment_shader": "minecraft:post/blit",
                "inputs": [{"sampler_name": "In", "target": "fade_a"}],
                "uniforms": {
                    "BlitConfig": [
                        {
                            "name": "ColorModulate",
                            "type": "vec4",
                            "value": [1.0, 1.0, 1.0, 1.0],
                        }
                    ]
                },
                "output": "fade_b",
            },
            {
                "vertex_shader": "minecraft:core/screenquad",
                "fragment_shader": "cigaro:post/cigaro_fx",
                "inputs": [
                    {"sampler_name": "In", "target": "minecraft:main", "bilinear": True},
                    {"sampler_name": "Fade", "target": "fade_a"},
                ],
                "output": "swap",
                "uniforms": {
                    "CigaroConfig": [
                        {
                            "name": "ModeAndDir",
                            "type": "vec4",
                            "value": [float(mode), float(direction), float(max_intensity), float(speed_mult)],
                        },
                        {
                            "name": "TintAndVign",
                            "type": "vec4",
                            "value": [float(tint_rgb[0]), float(tint_rgb[1]), float(tint_rgb[2]), float(vignette)],
                        },
                    ]
                },
            },
            {
                "vertex_shader": "minecraft:core/screenquad",
                "fragment_shader": "minecraft:post/blit",
                "inputs": [{"sampler_name": "In", "target": "swap"}],
                "uniforms": {
                    "BlitConfig": [
                        {
                            "name": "ColorModulate",
                            "type": "vec4",
                            "value": [1.0, 1.0, 1.0, 1.0],
                        }
                    ]
                },
                "output": "minecraft:main",
            },
        ],
    }


effects_spec = {
    "puff":        (0.0, 0.95, 1.0, [1.12, 0.92, 0.68], 0.95, 0.014),
    "mint_chill":  (1.0, 0.95, 1.0, [0.78, 1.06, 1.14], 0.95, 0.015),
    "spice_heat":  (2.0, 1.00, 1.0, [1.14, 0.82, 0.54], 1.15, 0.012),
    "shadow_noir": (3.0, 0.95, 1.0, [1.04, 0.86, 0.94], 0.95, 0.011),
    "withdrawal":  (4.0, 1.05, 1.0, [0.80, 0.86, 0.92], 1.45, 0.008),
    "killer_trip": (5.0, 1.15, 1.0, [1.00, 0.98, 0.72], 1.25, 0.007),
}

for name, (mode, max_int, spd, tint, vig, fspd) in effects_spec.items():
    write_pack_file(
        f"assets/cigaro/post_effect/{name}.json",
        json.dumps(
            make_post_effect_json(mode, 1.0, max_int, spd, tint, vig, fspd),
            indent=2,
            ensure_ascii=False,
        ),
    )
    write_pack_file(
        f"assets/cigaro/post_effect/{name}_out.json",
        json.dumps(
            make_post_effect_json(mode, -1.0, max_int, spd, tint, vig, fspd * 1.2),
            indent=2,
            ensure_ascii=False,
        ),
    )

pack_meta = {
    "pack": {
        "min_format": [97, 1],
        "max_format": [97, 1],
        "description": "Cigaro 26.3 Shaders",
    }
}

write_pack_file(
    "pack.mcmeta",
    json.dumps(pack_meta, indent=2, ensure_ascii=False) + "\n",
)

with zipfile.ZipFile(ZIP_PATH, "w", zipfile.ZIP_DEFLATED) as zf:
    for full in sorted(PACK_DIR.rglob("*")):
        if full.is_file():
            zf.write(full, full.relative_to(PACK_DIR))

print(f"Generated {PACK_DIR}")
print(f"Generated {ZIP_PATH}")
