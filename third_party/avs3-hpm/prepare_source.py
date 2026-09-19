#!/usr/bin/env python3
"""Adapt a fresh, hash-verified HPM 15.0 copy without changing its codec tools."""
from pathlib import Path
import re
import sys

root = Path(sys.argv[1])


def edit(name, before, after, count=1):
    path = root / name
    text = path.read_text(encoding="latin-1")
    if text.count(before) != count:
        raise SystemExit(f"HPM source drift: {name}: {before[:80]!r}")
    path.write_text(text.replace(before, after), encoding="latin-1")


edit("src/com_recon.c", "__inline u8 dec_is_pix_ref_valid", "static __inline u8 dec_is_pix_ref_valid")

# Four-tap chroma filters only use the low four s16 coefficients. The reference
# SSE code read eight, crossing the final row of the coefficient table. Keep
# the exact low lanes and load only the eight bytes the filter owns.
path = root / "src/com_mc.c"
text = path.read_text(encoding="latin-1")
for name in ("mc_filter_c_4pel_horz_sse", "mc_filter_c_4pel_vert_sse"):
    start = text.index("static void " + name)
    end = text.index("\n}", start)
    body = text[start:end]
    before = "coeff0_1_8x16b = _mm_loadu_si128((__m128i*)coeff);"
    if body.count(before) != 1:
        raise SystemExit("HPM chroma coefficient load drift: " + name)
    text = text[:start] + body.replace(before,
        "coeff0_1_8x16b = _mm_loadl_epi64((const __m128i*)coeff);") + text[end:]
path.write_text(text, encoding="latin-1")

# CLI progress output has no place in a library or an Android playback loop.
edit("src/dec_eco.c", '    printf("%d ", patch->idx);',
     "    /* Per-patch CLI progress is intentionally omitted in the library. */")

# Keep the scalar ESAO implementations, removing only x86 dispatch/functions.
path = root / "src/com_esao.c"
text = path.read_text(encoding="latin-1")
text = re.sub(r"^#include <(?:[a-z]*intrin|cpuid)\.h>\n", "", text, flags=re.M)
for name in ("esao_on_block_for_luma_avx", "esao_on_block_for_luma_sse",
             "esao_on_block_for_chroma_avx", "esao_on_block_for_chroma_sse",
             "is_support_sse_avx", "decide_esao_filter_func_pointer"):
    match = re.search(r"^(?:void|int) " + name + r"\(", text, re.M)
    if not match:
        raise SystemExit("Missing HPM ESAO function: " + name)
    start = text.index("{", match.end())
    end, depth = start + 1, 1
    while depth:
        depth += (text[end] == "{") - (text[end] == "}")
        end += 1
    replacement = ""
    if name == "decide_esao_filter_func_pointer":
        replacement = """void decide_esao_filter_func_pointer(ESAO_FUNC_POINTER *f)
{
    f->esao_on_block_for_luma = esao_on_block_for_luma_without_simd;
    f->esao_on_block_for_chroma = esao_on_block_for_chroma_without_simd;
}"""
    text = text[:match.start()] + replacement + text[end:]
path.write_text(text, encoding="latin-1")

# A disabled ESAO LCU carries flag 0. HPM read parameter[-1] before testing
# that flag, even though the result was unused. Preserve the no-filter result
# while avoiding that reference-code out-of-bounds read.
edit("src/com_esao.c", "    int lcu_flag = info->pic_header.esao_lcu_enable[comp_idx];",
     "    int lcu_flag = info->pic_header.esao_lcu_enable[comp_idx];\n    if (lcu_flag && !lcu_open_flag) return;")

# Route all allocations and fatal assertions through an instance-owned arena.
# Only C decoder/common sources are linked; no longjmp crosses C++ destructors.
for path in list((root / "inc").glob("*.h")) + list((root / "src").glob("*.c")):
    text = path.read_text(encoding="latin-1")
    text = text.replace("#ifdef X86_SSE", "#if X86_SSE")
    text = text.replace("#include <malloc.h>", "#include <stdlib.h>")
    for old, new in (("malloc", "webhtv_hpm_alloc"), ("calloc", "webhtv_hpm_calloc"),
                     ("memalign", "webhtv_hpm_aligned_alloc"), ("free", "webhtv_hpm_free"),
                     ("assert", "HPM_REQUIRE"), ("exit", "HPM_EXIT")):
        text = re.sub(r"\b" + old + r"\s*\(", new + "(", text)
    path.write_text(text, encoding="latin-1")
edit("inc/com_port.h", "#include <limits.h>", '#include <limits.h>\n#include "hpm_guard.h"')
edit("inc/com_port.h", "#if defined(WIN32) || defined(WIN64)\n#include <emmintrin.h>",
     '#if defined(__arm__) || defined(__aarch64__)\n#include "sse2neon.h"\n#elif defined(WIN32) || defined(WIN64)\n#include <emmintrin.h>')

# Existing com_assert variants evaluate x a second time on failure; a read in x
# must never run twice. The error boundary replaces both abort and goto cleanup.
path = root / "inc/com_port.h"
text = path.read_text(encoding="latin-1")
start = text.index("#define com_assert(x)")
end = text.index("#if X86_SSE", start)
text = text[:start] + """#define com_assert(x) HPM_REQUIRE(x)
#define com_assert_r(x) HPM_REQUIRE(x)
#define com_assert_rv(x,r) HPM_REQUIRE(x)
#define com_assert_g(x,g) HPM_REQUIRE(x)
#define com_assert_gv(x,r,v,g) HPM_REQUIRE(x)

""" + text[end:]
path.write_text(text, encoding="latin-1")

# Reject oversized RPLs before writing arrays (including pointers obtained from
# an array before a compiler bounds check could detect a subsequent overwrite).
edit("src/dec_eco.c", "sqh->rpls_l0_num            = (u32)com_bsr_read_ue(bs);",
     "sqh->rpls_l0_num = com_bsr_read_ue(bs);\n    HPM_REQUIRE(sqh->rpls_l0_num <= MAX_NUM_RPLS);")
edit("src/dec_eco.c", "sqh->rpls_l1_num = (u32)com_bsr_read_ue(bs);",
     "sqh->rpls_l1_num = com_bsr_read_ue(bs);\n        HPM_REQUIRE(sqh->rpls_l1_num <= MAX_NUM_RPLS);")

# Validate dimensions, sampling and external-reference requirements before the
# first allocation or indexing based on a decoded sequence header.
edit("src/dec.c", "ret = dec_eco_sqh(bs, sqh);", """ret = dec_eco_sqh(bs, sqh);
        HPM_REQUIRE(sqh->profile_id == 0x32 && sqh->chroma_format == 1);
        HPM_REQUIRE(!sqh->library_stream_flag && !sqh->library_picture_enable_flag);
        HPM_REQUIRE(sqh->horizontal_size > 0 && sqh->vertical_size > 0);
        HPM_REQUIRE(sqh->horizontal_size <= 8192 && sqh->vertical_size <= 8192);
        HPM_REQUIRE((uint64_t)sqh->horizontal_size * sqh->vertical_size <= 33554432);
        HPM_REQUIRE(sqh->sample_precision == 1 || sqh->sample_precision == 2);
        HPM_REQUIRE(sqh->encoding_precision == 2);""")

# The reader permits exactly three lookahead bytes for the appended next-start
# sentinel. Reaching the end is an error, never an all-ones value or busy loop.
edit("src/dec_bsr.c", "        return -1;", '        webhtv_hpm_fail(-1, "truncated bitstream", __LINE__);', count=2)
edit("src/dec_bsr.c", "            return ((u32)-1);", '            webhtv_hpm_fail(-1, "truncated bitstream", __LINE__);')
edit("src/dec_bsr.c", "com_assert(size > 0);", "com_assert(size > 0 && size <= 32);", count=2)
edit("src/dec_bsr.c", "    clz += len;", "    clz += len;\n    HPM_REQUIRE(clz <= 15 && len + clz + 1 <= 32);")
edit("src/dec_bsr.c", "        cur += byte;", "        HPM_REQUIRE(byte >= 0);\n        cur += byte;")
# A 32-bit shift is undefined on every architecture, not just x86.
edit("inc/dec_bsr.h", "#if defined(X86F)", "#if 1")

# Preserve sequence display metadata that the CLI used to discard.
edit("src/dec_eco.c", "    com_bsr_read1(bs);                                              // sample_range",
     "    int range = com_bsr_read1(bs);\n    webhtv_hpm_set_color(range, -1, -1, -1);                            // sample_range")
edit("src/dec_eco.c", "        com_bsr_read(bs, 8);                                       // matrix_coefficients",
     "        int matrix = com_bsr_read(bs, 8);\n        webhtv_hpm_set_color(range, colour_primaries, transfer_characteristics, matrix); // matrix_coefficients")
