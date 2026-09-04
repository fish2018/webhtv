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

#ifndef LIBPLACEBO_SHADERS_FILM_GRAIN_H_
#define LIBPLACEBO_SHADERS_FILM_GRAIN_H_

// Film grain synthesis shaders for AV1 / H.274.

#include <stdint.h>
#include <stdbool.h>

#include <libplacebo/colorspace.h>
#include <libplacebo/shaders.h>

PL_API_BEGIN

enum pl_film_grain_type {
    PL_FILM_GRAIN_NONE = 0,
    PL_FILM_GRAIN_AV1,
    PL_FILM_GRAIN_H274,
    PL_FILM_GRAIN_COUNT,
};

// AV1 film grain parameters. For the exact meaning of these, see the AV1
// specification (section 6.8.20).
struct pl_av1_grain_data {
    int num_points_y;
    uint8_t points_y[14][2];     // [n][0] = value, [n][1] = scaling
    bool chroma_scaling_from_luma;
    int num_points_uv[2];        // should be {0} for grayscale images
    uint8_t points_uv[2][10][2]; // like points_y for points_uv[0, 1] = u, v
    int scaling_shift;
    int ar_coeff_lag;
    int8_t ar_coeffs_y[24];
    int8_t ar_coeffs_uv[2][25];
    int ar_coeff_shift;
    int grain_scale_shift;
    int8_t uv_mult[2];
    int8_t uv_mult_luma[2];
    int16_t uv_offset[2];        // 9-bit value, range [-256, 255]
    bool overlap;
};

// H.274 film grain parameters. For the exact meaning of these, see the H.274
// specification (section 8.5).
struct pl_h274_grain_data {
    int model_id;
    int blending_mode_id;
    int log2_scale_factor;
    bool component_model_present[3];
    uint16_t num_intensity_intervals[3];
    uint8_t num_model_values[3];
    const uint8_t *intensity_interval_lower_bound[3];
    const uint8_t *intensity_interval_upper_bound[3];
    const int16_t (*comp_model_value[3])[6];
};

// Tagged union for film grain data
struct pl_film_grain_data {
    enum pl_film_grain_type type;   // film grain type
    uint64_t seed;                  // shared seed value

    union {
        // Warning: These values are not sanity-checked at all, Invalid grain
        // data results in undefined behavior!
        struct pl_av1_grain_data av1;
        struct pl_h274_grain_data h274;
    } params;
};

// Options for the `pl_shader_film_grain` call.
struct pl_film_grain_params {
    // Required for all film grain types:
    struct pl_film_grain_data data; // film grain data
    pl_tex tex;                     // texture to sample from
    struct pl_color_repr *repr;     // underlying color representation (see notes)
    int components;
    int component_mapping[4];       // same as `struct pl_plane`

    // Notes for `repr`:
    //  - repr->bits affects the rounding for grain generation
    //  - repr->levels affects whether or not we clip to full range or not
    //  - repr->sys affects the interpretation of channels
    //  - *repr gets normalized by this shader, which is why it's a pointer

    // Required for PL_FILM_GRAIN_AV1 only:
    pl_tex luma_tex;                // "luma" texture (see notes)
    int luma_comp;                  // index of luma in `luma_tex`

    // Notes for `luma_tex`:
    //  - `luma_tex` must be specified if the `tex` does not itself contain the
    //     "luma-like" component. For XYZ systems, the Y channel is the luma
    //     component. For RGB systems, the G channel is.
};

#define pl_film_grain_params(...) (&(struct pl_film_grain_params) { __VA_ARGS__ })

// Test if film grain needs to be applied. This is a helper function that users
// can use to decide whether or not `pl_shader_film_grain` needs to be called,
// based on the given grain metadata.
PL_API bool pl_needs_film_grain(const struct pl_film_grain_params *params);

// Sample from a texture while applying film grain at the same time.
// `grain_state` must be unique for every plane configuration, as it may
// contain plane-dependent state.
//
// Returns false on any error, or if film grain generation is not supported
// due to GLSL limitations.
PL_API bool pl_shader_film_grain(pl_shader sh, pl_shader_obj *grain_state,
                                 const struct pl_film_grain_params *params);

PL_API_END

#endif // LIBPLACEBO_SHADERS_FILM_GRAIN_H_
