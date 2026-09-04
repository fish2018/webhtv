#!/usr/bin/env python3

import argparse
from pathlib import Path
import re


ROOT = Path(__file__).resolve().parent.parent
SOURCE = ROOT / "third_party/mpv-native-overrides/aimagereader-stable/video/out/hwdec/hwdec_aimagereader_vk_stable.c"
SHADER = ROOT / "third_party/mpv-native-overrides/aimagereader-stable/video/out/hwdec/hwdec_aimagereader_vk_stable.comp"
STALE_HEADER = ROOT / "third_party/mpv-native-overrides/aimagereader-stable/video/out/hwdec/hwdec_aimagereader_vk_stable_comp.h"
P2_PATCH = ROOT / "third_party/patches/mpv-p2-generic-uv.patch"


def require(pattern: str, text: str, description: str) -> re.Match[str]:
    match = re.search(pattern, text, re.MULTILINE)
    if not match:
        raise SystemExit(f"missing {description}")
    return match


def require_tokens(text: str, tokens: tuple[str, ...], description: str) -> None:
    for token in tokens:
        if token not in text:
            raise SystemExit(f"{description} is missing: {token}")


def verify_stable() -> None:
    source = SOURCE.read_text(encoding="utf-8")
    shader = SHADER.read_text(encoding="utf-8")
    shader_group = require(
        r"layout\s*\(\s*local_size_x\s*=\s*(\d+)\s*,\s*local_size_y\s*=\s*(\d+)\s*\)\s*in\s*;",
        shader,
        "stable Vulkan shader workgroup declaration",
    )
    source_x = int(require(
        r"^#define\s+STABLE_WORKGROUP_X\s+(\d+)\s*$",
        source,
        "STABLE_WORKGROUP_X",
    ).group(1))
    source_y = int(require(
        r"^#define\s+STABLE_WORKGROUP_Y\s+(\d+)\s*$",
        source,
        "STABLE_WORKGROUP_Y",
    ).group(1))
    shader_x, shader_y = map(int, shader_group.groups())
    if (source_x, source_y) != (shader_x, shader_y):
        raise SystemExit(
            "stable Vulkan dispatch/shader mismatch: "
            f"C={source_x}x{source_y}, shader={shader_x}x{shader_y}"
        )
    if source_x * source_y > 128:
        raise SystemExit(
            "stable Vulkan shader exceeds the 128-invocation Vulkan core baseline: "
            f"{source_x}x{source_y}"
        )
    require_tokens(shader, (
        "vec2 uv_offset;",
        "vec2 uv_scale;",
        "ivec2 output_size;",
        "params.uv_offset",
        "params.uv_scale",
    ), "stable Vulkan shader")
    require_tokens(source, (
        "float uv_offset[2];",
        "float uv_scale[2];",
        "int32_t output_size[2];",
        "aimagereader_vk_stable_comp.inc",
        "CPU-precomputed UV transform",
    ), "stable Vulkan source")
    if STALE_HEADER.exists():
        raise SystemExit(f"unused stale shader header must not exist: {STALE_HEADER}")
    print(
        "Verified stable Vulkan shader contract: "
        f"{source_x}x{source_y}, CPU-precomputed UV transform"
    )


def verify_p2_patch_scope() -> None:
    patch = P2_PATCH.read_text(encoding="utf-8")
    paths = tuple(
        match.group(1)
        for match in re.finditer(r"^diff --git a/(\S+) b/(\S+)$", patch, re.MULTILINE)
        if match.group(1) == match.group(2)
    )
    expected = (
        "video/out/hwdec/hwdec_aimagereader.comp",
        "video/out/hwdec/hwdec_aimagereader.frag",
        "video/out/hwdec/hwdec_aimagereader_vk_convert.c",
    )
    if paths != expected:
        raise SystemExit(f"P2 generic UV patch scope changed: {paths!r}")
    require_tokens(patch, (
        "+    vec2 uv_offset;",
        "+    vec2 uv_scale;",
        "+    float uv_offset[2];",
        "+    float uv_scale[2];",
        "+_Static_assert(sizeof(struct conversion_push_constants) == 24,",
        "+               \"Generic Vulkan conversion uses CPU-precomputed UV transform\\n\");",
    ), "P2 generic UV patch")
    print("Verified P2 generic UV patch scope: 3 source files")


def verify_generic(mpv_source: Path) -> None:
    hwdec = mpv_source / "video/out/hwdec"
    compute = (hwdec / "hwdec_aimagereader.comp").read_text(encoding="utf-8")
    fragment = (hwdec / "hwdec_aimagereader.frag").read_text(encoding="utf-8")
    source = (hwdec / "hwdec_aimagereader_vk_convert.c").read_text(encoding="utf-8")
    compute_header = (hwdec / "hwdec_aimagereader_comp.h").read_text(encoding="utf-8")
    fragment_header = (hwdec / "hwdec_aimagereader_frag.h").read_text(encoding="utf-8")

    for name, shader in (("compute", compute), ("fragment", fragment)):
        require_tokens(shader, (
            "vec2 uv_offset;",
            "vec2 uv_scale;",
            "ivec2 output_size;",
            "params.uv_offset",
            "params.uv_scale",
        ), f"generic Vulkan {name} shader")
        for stale in ("ivec2 crop_offset;", "ivec2 crop_size;", "ivec2 source_size;"):
            if stale in shader:
                raise SystemExit(f"generic Vulkan {name} shader retains stale token: {stale}")

    require_tokens(source, (
        "float uv_offset[2];",
        "float uv_scale[2];",
        "int32_t output_size[2];",
        "_Static_assert(sizeof(struct conversion_push_constants) == 24,",
        "(float)((double)crop->left / source.desc.width)",
        "((double)source.output_width * source.desc.width)",
        "log_conversion_geometry(p, &geometry);",
        "Generic Vulkan conversion uses CPU-precomputed UV transform",
        "ANDROID_VULKAN_AIMAGEREADER_BACKEND_LEGACY",
        "p->release_sync_fd = false;",
        "WebHTV Vulkan keeps AImage until the conversion fence completes",
    ), "generic Vulkan conversion source")
    require_tokens(compute_header, (
        "Generated from hwdec_aimagereader.comp",
        "static const uint32_t aimagereader_comp_spv[] = {",
    ), "generic Vulkan compute header")
    require_tokens(fragment_header, (
        "Generated from hwdec_aimagereader.frag",
        "static const uint32_t aimagereader_frag_spv[] = {",
    ), "generic Vulkan fragment header")
    print("Verified generic Vulkan shader contract: CPU-precomputed UV transform")


parser = argparse.ArgumentParser()
parser.add_argument("--mpv-source", type=Path)
args = parser.parse_args()

verify_stable()
verify_p2_patch_scope()
if args.mpv_source:
    verify_generic(args.mpv_source)
