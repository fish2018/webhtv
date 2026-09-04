/*
 * This file is part of libplacebo.
 *
 * libplacebo is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * libplacebo is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with libplacebo.  If not, see <http://www.gnu.org/licenses/>.
 */

#ifndef LIBPLACEBO_SHADERS_CUSTOM_H_
#define LIBPLACEBO_SHADERS_CUSTOM_H_

#include <stdlib.h>

// Functions for writing custom shaders and hooking them into the `pl_renderer`
// pipeline, as well as compatibility functions for parsing shaders in mpv
// format.

#include <libplacebo/shaders.h>
#include <libplacebo/dispatch.h>
#include <libplacebo/colorspace.h>

PL_API_BEGIN

// Parameters describing custom shader text to be embedded into a `pl_shader`
// object. All of the strings are optional and can be left as NULL, but without
// a `body` in particular, the shader will do nothing useful on its own.
struct pl_custom_shader {
    // The prelude contains text such as extra #defines, #extension pragmas,
    // or other parts of the shader that must be placed at the very
    // beginning (before input layout declarations etc.)
    //
    // Note: #extension pragmas do not need to be emitted to enable support for
    // resource types already attached to the shader (e.g. SSBOs), compute
    // shaders, or GPU capabilities known to libplacebo (e.g. subgroups).
    const char *prelude;

    // The header contains text such as helper function definitions, extra
    // uniforms, shared memory variables or buffer descriptions.
    const char *header;

    // A friendly name for the shader. (Optional)
    const char *description;

    // The "primary" GLSL code. This will be effectively appended to the "main"
    // function. It lives in an environment given by the `input` signature, and
    // is expected to return results in a way given by the `output` signature.
    //
    // Note: In the case of PL_SHADER_SIG_COLOR, the output `vec4 color` is
    // allocated by `pl_shader_custom`, the user merely needs to assign to it.
    //
    // Note: For ease of development it can be useful to have the main logic
    // live inside a helper function defined as part of `header`, and specify
    // the `body` as a single line that simply calls the helper function.
    const char *body;
    enum pl_shader_sig input;
    enum pl_shader_sig output;

    // Extra descriptors, variables and vertex attributes to attach to the
    // resulting `pl_shader_res`.
    //
    // Note: The names inside these will possibly be replaced by fresh
    // identifiers internally, so users should avoid looking for exact string
    // matches for the given names inside the `pl_shader_res`.
    const struct pl_shader_desc *descriptors;
    int num_descriptors;
    const struct pl_shader_var *variables;
    int num_variables;
    const struct pl_shader_va *vertex_attribs;
    int num_vertex_attribs;
    const struct pl_shader_const *constants;
    int num_constants;

    // If true, this shader must be a compute shader. The desired workgroup
    // size and shared memory usage can be optionally specified, or 0 if no
    // specific work group size or shared memory size restrictions apply.
    //
    // See also: `pl_shader_res.compute_group_size`
    bool compute;
    size_t compute_shmem;
    int compute_group_size[2];

    // Fixes the output size requirements of the shader to exact dimensions.
    // Optional, if left as 0, means the shader can be dispatched at any size.
    int output_w;
    int output_h;
};

// Append custom shader code, including extra descriptors and variables, to an
// existing `pl_shader` object. Returns whether successful. This function may
// fail in the event that e.g. the custom shader requires compute shaders on
// an unsupported GPU, or exceeds the GPU's shared memory capabilities.
PL_API bool pl_shader_custom(pl_shader sh, const struct pl_custom_shader *params);

// Which "rendering stages" are available for user shader hooking purposes.
// Except where otherwise noted, all stages are "non-resizable", i.e. the
// shaders already have specific output size requirements.
enum pl_hook_stage {
    // Hook stages for the untouched planes, as made available by the source.
    // These are all resizable, i.e. there are no specific output stage
    // requirements.
    PL_HOOK_RGB_INPUT       = 1 << 0,
    PL_HOOK_LUMA_INPUT      = 1 << 1,
    PL_HOOK_CHROMA_INPUT    = 1 << 2,
    PL_HOOK_ALPHA_INPUT     = 1 << 3,
    PL_HOOK_XYZ_INPUT       = 1 << 4,

    // Hook stages for the scaled/aligned planes
    PL_HOOK_CHROMA_SCALED   = 1 << 5,
    PL_HOOK_ALPHA_SCALED    = 1 << 6,

    PL_HOOK_NATIVE          = 1 << 7,  // Combined image in its native color space
    PL_HOOK_RGB             = 1 << 8,  // After conversion to RGB (resizable)
    PL_HOOK_LINEAR          = 1 << 9,  // After linearization but before scaling
    PL_HOOK_SIGMOID         = 1 << 10, // After sigmoidization
    PL_HOOK_PRE_KERNEL      = 1 << 11, // Immediately before the main scaler kernel
    PL_HOOK_POST_KERNEL     = 1 << 12, // Immediately after the main scaler kernel
    PL_HOOK_SCALED          = 1 << 13, // After scaling, before color management
    PL_HOOK_PRE_OUTPUT      = 1 << 14, // After color management, before blending/rotation
    PL_HOOK_OUTPUT          = 1 << 15, // After blending/rotation, before dithering
};

// Returns true if a given hook stage is resizable
static inline bool pl_hook_stage_resizable(enum pl_hook_stage stage) {
    switch (stage) {
    case PL_HOOK_RGB_INPUT:
    case PL_HOOK_LUMA_INPUT:
    case PL_HOOK_CHROMA_INPUT:
    case PL_HOOK_ALPHA_INPUT:
    case PL_HOOK_XYZ_INPUT:
    case PL_HOOK_NATIVE:
    case PL_HOOK_RGB:
        return true;

    case PL_HOOK_CHROMA_SCALED:
    case PL_HOOK_ALPHA_SCALED:
    case PL_HOOK_LINEAR:
    case PL_HOOK_SIGMOID:
    case PL_HOOK_PRE_KERNEL:
    case PL_HOOK_POST_KERNEL:
    case PL_HOOK_SCALED:
    case PL_HOOK_PRE_OUTPUT:
    case PL_HOOK_OUTPUT:
        return false;
    }

    abort();
}

// The different forms of communicating image data between the renderer and
// the hooks
enum pl_hook_sig {
    PL_HOOK_SIG_NONE,   // No data is passed, no data is received/returned
    PL_HOOK_SIG_COLOR,  // `vec4 color` already pre-sampled in a `pl_shader`
    PL_HOOK_SIG_TEX,    // `pl_tex` containing the image data
    PL_HOOK_SIG_COUNT,
};

struct pl_hook_params {
    // GPU objects associated with the `pl_renderer`, which the user may
    // use for their own purposes.
    pl_gpu gpu;
    pl_dispatch dispatch;

    // Helper function to fetch a new temporary texture, using renderer-backed
    // storage. This is guaranteed to have sane image usage requirements and a
    // 16-bit or floating point format. The user does not need to free/destroy
    // this texture in any way. May return NULL.
    pl_tex (*get_tex)(void *priv, int width, int height);
    void *priv;

    // Which stage triggered the hook to run.
    enum pl_hook_stage stage;

    // For `PL_HOOK_SIG_COLOR`, this contains the existing shader object with
    // the color already pre-sampled into `vec4 color`. The user may modify
    // this as much as they want, as long as they don't dispatch/finalize/reset
    // it.
    //
    // Note that this shader might have specific output size requirements,
    // depending on the exact shader stage hooked by the user, and may already
    // be a compute shader.
    pl_shader sh;

    // For `PL_HOOK_SIG_TEX`, this contains the texture that the user should
    // sample from.
    //
    // Note: This texture object is owned by the renderer, and users must not
    // modify its contents. It will not be touched for the duration of a frame,
    // but the contents are lost in between frames.
    pl_tex tex;

    // The effective current rectangle of the image we're rendering in this
    // shader, i.e. the effective rect of the content we're interested in,
    // as a crop of either `sh` or `tex` (depending on the signature).
    //
    // Note: This is still set even for `PL_HOOK_SIG_NONE`!
    pl_rect2df rect;

    // The current effective colorspace and representation, of either the
    // pre-sampled color (in `sh`), or the contents of `tex`, respectively.
    //
    // Note: This is still set even for `PL_HOOK_SIG_NONE`!
    struct pl_color_repr repr;
    struct pl_color_space color;
    int components;

    // The representation and colorspace of the original image, for reference.
    const struct pl_color_repr *orig_repr;
    const struct pl_color_space *orig_color;

    // The (cropped) source and destination rectangles of the overall
    // rendering. These are functionallty equivalent to `image.crop` and
    // `target.crop`, respectively, but `src_rect` in particular may change as
    // a result of previous hooks being executed. (e.g. prescalers)
    pl_rect2df src_rect;
    pl_rect2d dst_rect;
};

struct pl_hook_res {
    // If true, the hook is assumed to have "failed" or errored in some way,
    // and all other fields are ignored.
    bool failed;

    // What type of output this hook is returning.
    // Note: If this is `PL_HOOK_SIG_NONE`, all other fields are ignored.
    enum pl_hook_sig output;

    // For `PL_HOOK_SIG_COLOR`, this *must* be set to a valid `pl_shader`
    // object containing the sampled color value (i.e. with an output signature
    // of `PL_SHADER_SIG_COLOR`), and *should* be allocated from the given
    // `pl_dispatch` object. Ignored otherwise.
    pl_shader sh;

    // For `PL_HOOK_SIG_TEX`, this *must* contain the texture object containing
    // the result of rendering the hook. This *should* be a texture allocated
    // using the given `get_tex` callback, to ensure the format and texture
    // usage flags are compatible with what the renderer expects.
    pl_tex tex;

    // For shaders that return some sort of output, this contains the
    // new/altered versions of the existing "current texture" metadata.
    struct pl_color_repr repr;
    struct pl_color_space color;
    int components;

    // This contains the new effective rect of the contents. This may be
    // different from the original `rect` for resizable passes. Ignored for
    // non-resizable passes.
    pl_rect2df rect;
};

enum pl_hook_par_mode {
    PL_HOOK_PAR_VARIABLE,   // normal shader variable
    PL_HOOK_PAR_DYNAMIC,    // dynamic shader variable, e.g. per-frame changing
    PL_HOOK_PAR_CONSTANT,   // fixed at compile time (e.g. for array sizes),
                            // must be scalar (non-vector/matrix)
    PL_HOOK_PAR_DEFINE,     // defined in the preprocessor, must be `int`
    PL_HOOK_PAR_MODE_COUNT,
};

typedef union pl_var_data {
    int i;
    unsigned u;
    float f;
} pl_var_data;

struct pl_hook_par {
    // Name as used in the shader.
    const char *name;

    // Type of this shader parameter, and how it's manifested in the shader.
    enum pl_var_type type;
    enum pl_hook_par_mode mode;

    // Human-readable explanation of this parameter. (Optional)
    const char *description;

    // Mutable data pointer to current value of variable.
    pl_var_data *data;

    // Default/initial value, and lower/upper bounds.
    pl_var_data initial;
    pl_var_data minimum;
    pl_var_data maximum;

    // Human-readable names for the variants of an integer option. This array
    // can be indexed directly by integer values, ranging from `minimum.i` to
    // `maximum.i`. May be NULL, in which case options are unnamed.
    const char * const *names;
};

// Struct describing a hook.
//
// Note: Users may freely create their own instances of this struct, there is
// nothing particularly special about `pl_mpv_user_shader_parse`.
struct pl_hook {
    enum pl_hook_stage stages;  // Which stages to hook on
    enum pl_hook_sig input;     // Which input signature this hook expects
    void *priv;                 // Arbitrary user context

    // Custom tunable shader parameters exported by this hook. These may be
    // updated at any time by the user, to influence the behavior of the hook.
    // Contents are arbitrary and subject to the method of hook construction.
    const struct pl_hook_par *parameters;
    int num_parameters;

    // Called at the beginning of passes, to reset/initialize the hook. (Optional)
    void (*reset)(void *priv);

    // The hook function itself. Called by the renderer at any of the indicated
    // hook stages. See `pl_hook_res` for more info on the return values.
    struct pl_hook_res (*hook)(void *priv, const struct pl_hook_params *params);

    // Unique signature identifying this hook, used to disable misbehaving hooks.
    // All hooks with the same signature will be disabled, should they fail to
    // execute during run-time.
    uint64_t signature;
};

// Compatibility layer with `mpv` user shaders. See the mpv man page for more
// information on the format. Will return `NULL` if the shader fails parsing.
//
// The resulting `pl_hook` objects should be destroyed with the corresponding
// destructor when no longer needed.
PL_API const struct pl_hook *
pl_mpv_user_shader_parse(pl_gpu gpu, const char *shader_text, size_t shader_len);

PL_API void pl_mpv_user_shader_destroy(const struct pl_hook **hook);

PL_API_END

#endif // LIBPLACEBO_SHADERS_CUSTOM_H_
