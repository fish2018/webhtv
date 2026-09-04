
/*
 * This file is part of libplacebo, which is normally licensed under the terms
 * of the LGPL v2.1+. However, this file (film_grain.h) is also available under
 * the terms of the more permissive MIT license:
 *
 * Copyright (c) 2018-2019 Niklas Haas
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

#ifndef LIBPLACEBO_SHADERS_DEINTERLACING_H_
#define LIBPLACEBO_SHADERS_DEINTERLACING_H_

#include <libplacebo/shaders.h>

PL_API_BEGIN

enum pl_field {
    PL_FIELD_NONE = 0, // no deinterlacing
    PL_FIELD_EVEN,     // "top" fields, with even y coordinates
    PL_FIELD_ODD,      // "bottom" fields, with odd y coordinates

    // Convenience aliases
    PL_FIELD_TOP = PL_FIELD_EVEN,
    PL_FIELD_BOTTOM = PL_FIELD_ODD,
};

static inline enum pl_field pl_field_other(enum pl_field field)
{
    switch (field) {
    case PL_FIELD_EVEN: return PL_FIELD_ODD;
    case PL_FIELD_ODD:  return PL_FIELD_EVEN;
    default: return field;
    }
}

struct pl_field_pair {
    // Top texture. If only this is specified, it's assumed to contain both
    // fields in an interleaved fashion (MBAFF).
    //
    // Note: Support for separate fields (PAFF), is currently pending, so this
    // is the only way to provide interlaced frames at the moment.
    pl_tex top;
};

#define pl_field_pair(...) ((struct pl_field_pair) { __VA_ARGS__ })

struct pl_deinterlace_source {
    // Previous, current and next source (interlaced) frames. `prev` and `next`
    // may be NULL, but `cur` is required. If present, they must all have the
    // exact same texture dimensions.
    //
    // Note: `prev` and `next` are only required for PL_DEINTERLACE_YADIF.
    struct pl_field_pair prev, cur, next;

    // The parity of the current field to output. This field will be unmodified
    // from `cur`, with the corresponding other field interpolated.
    //
    // If this is `PL_FIELD_NONE`, no deinterlacing is performed, and the
    // texture is merely sampled as-is.
    enum pl_field field;

    // The parity of the first frame in a stream. Set this the field that is
    // (conceptually) ordered first in time.
    //
    // If this is `PL_FIELD_NONE`, it will instead default to `PL_FIELD_TOP`.
    enum pl_field first_field;

    // Components to deinterlace. Components not specified will be ignored.
    // Optional, if left as 0, all components will be deinterlaced.
    uint8_t component_mask;
};

#define pl_deinterlace_source(...) (&(struct pl_deinterlace_source) { __VA_ARGS__ })

enum pl_deinterlace_algorithm {
    // No-op deinterlacing, just sample the weaved frame un-touched.
    PL_DEINTERLACE_WEAVE = 0,

    // Naive bob deinterlacing. Doubles the field lines vertically.
    PL_DEINTERLACE_BOB,

    // "Yet another deinterlacing filter". Deinterlacer with temporal and
    // spatial information. Based on FFmpeg's Yadif filter algorithm, but
    // adapted slightly for the GPU.
    PL_DEINTERLACE_YADIF,

    // "Bob weaver" deinterlacer. Motion-adaptive deinterlacer based on yadif,
    // with the use of w3fdif and cubic interpolation algorithms.
    PL_DEINTERLACE_BWDIF,

    PL_DEINTERLACE_ALGORITHM_COUNT,
};

// Returns whether or not an algorithm requires `prev`/`next` refs to be set.
static inline bool pl_deinterlace_needs_refs(enum pl_deinterlace_algorithm algo)
{
    return algo >= PL_DEINTERLACE_YADIF;
}

struct pl_deinterlace_params {
    // Algorithm to use. The recommended default is PL_DEINTERLACE_YADIF, which
    // provides a good trade-off of quality and speed.
    enum pl_deinterlace_algorithm algo;

    // Skip the spatial interlacing check. (PL_DEINTERLACE_YADIF only)
    bool skip_spatial_check;
};

#define PL_DEINTERLACE_DEFAULTS \
    .algo   = PL_DEINTERLACE_BWDIF,

#define pl_deinterlace_params(...) (&(struct pl_deinterlace_params) { PL_DEINTERLACE_DEFAULTS __VA_ARGS__ })
PL_API extern const struct pl_deinterlace_params pl_deinterlace_default_params;

// Deinterlaces a set of interleaved source frames and outputs the result into
// `vec4 color`. If `params` is left as NULL, it defaults to
// `&pl_deinterlace_default_params`.
PL_API void pl_shader_deinterlace(pl_shader sh, const struct pl_deinterlace_source *src,
                                  const struct pl_deinterlace_params *params);

PL_API_END

#endif // LIBPLACEBO_SHADERS_DEINTERLACING_H_
