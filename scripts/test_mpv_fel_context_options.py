#!/usr/bin/env python3
"""Focused regression for FEL context option ownership, using production C."""
import argparse
import os
from pathlib import Path
import re
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parent.parent
parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument("--mpv-source", type=Path, default=ROOT / "build/mpv-native/mpv-android/buildscripts/deps/mpv")
parser.add_argument("--expect-invalid-free", action="store_true")
args = parser.parse_args()
source = args.mpv_source.resolve()


def function(text, name):
    match = re.search(r"^(?:static (?:inline )?)?[^\n]*\b" + re.escape(name)
                      + r"\([^;]*?\n\{.*?^\}", text, re.M | re.S)
    if not match:
        raise ValueError("Missing production function: " + name)
    return match.group(0) + "\n"


with tempfile.TemporaryDirectory(prefix="webhtv-fel-options-") as directory:
    output = Path(directory)
    option_c = (source / "options/m_option.c").read_text()
    option_h = (source / "options/m_option.h").read_text()
    functions = "#define VAL(p) (*(char ***)(p))\n"
    functions += function(option_c, "free_str_list") + function(option_c, "copy_str_list")
    functions += "#undef VAL\n#define VAL(p) (*(m_obj_settings_t **)(p))\n"
    for name in ("obj_setting_free", "free_obj_settings_list", "copy_obj_settings_list"):
        functions += function(option_c, name)
    functions += "#undef VAL\nstatic const m_option_type_t m_option_type_obj_settings_list = { .copy=copy_obj_settings_list, .free=free_obj_settings_list };\n"
    functions += function(option_h, "m_option_copy") + function(option_h, "m_option_free")
    (output / "option_functions.inc").write_text(functions)
    vo = (source / "video/out/vo_gpu_next.c").read_text()
    preinit = function(vo, "preinit")
    start = preinit.index("    struct ra_ctx_opts *ctx_opts =")
    end = preinit.index("    talloc_free(ctx_opts);", start) + len("    talloc_free(ctx_opts);")
    helper = function(vo, "set_android_fel_context_options") if "static void set_android_fel_context_options(" in vo else ""
    (output / "fel_preinit.inc").write_text(helper + "\nstatic int run_preinit(struct vo *vo)\n{\n    struct priv *p = vo->priv;\n"
                                            + preinit[start:end] + "\n    return p->context ? 0 : -1;\n}\n")
    # Use the host's pthread mutexes for ta's debug registry; no allocator stub.
    (output / "osdep").mkdir()
    (output / "osdep/threads.h").write_text("#include <pthread.h>\ntypedef pthread_mutex_t mp_static_mutex;\n#define MP_STATIC_MUTEX_INITIALIZER PTHREAD_MUTEX_INITIALIZER\n#define mp_mutex_lock pthread_mutex_lock\n#define mp_mutex_unlock pthread_mutex_unlock\n")
    (output / "config.h").write_text("#define HAVE_ANDROID 0\n")
    binary = output / "test"
    # Upstream ta's debug leak printer compares int with sizeof; retain its code.
    command = [os.environ.get("CC", "cc"), "-std=gnu11", "-Wall", "-Wextra", "-Werror", "-Wno-unused-parameter", "-Wno-sign-compare",
               "-fsanitize=address,undefined", "-g", "-pthread", "-DTA_MEMORY_DEBUGGING=1",
               "-I" + str(output), "-I" + str(source),
               str(ROOT / "third_party/mpv-player-jni/tests/fel_context_options_test.c"),
               *[str(source / "ta" / name) for name in ("ta.c", "ta_utils.c", "ta_talloc.c")],
               "-o", str(binary)]
    subprocess.run(command, check=True)
    result = subprocess.run([str(binary)], capture_output=True, text=True)
    print(result.stdout, end="")
    print(result.stderr, end="")
    if args.expect_invalid_free:
        if result.returncode == 0 or not re.search("AddressSanitizer|canary.*CANARY", result.stderr):
            raise SystemExit("Expected the production static-option invalid free")
        print("PASS baseline reproduces invalid free with the real mpv allocator")
    elif result.returncode:
        raise SystemExit(result.returncode)
